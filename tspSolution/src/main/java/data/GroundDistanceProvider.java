package data;

import domain.City;

import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Distance and travel-time provider for {@link domain.GroundCity}.
 *
 * <h3>Data source</h3>
 * Uses the OSRM public routing API (single HTTP call per city pair — one
 * request returns both distance and duration).
 *
 * <h3>Two-level cache</h3>
 * <ol>
 *   <li><b>Disk cache</b> — {@code cache/osrm_cache.json} inside the
 *       resources directory. Loaded at startup; results appended after each
 *       new OSRM fetch. Survives app restarts so the same city pair is never
 *       fetched twice across sessions.</li>
 *   <li><b>In-memory cache</b> — {@link ConcurrentHashMap} populated from
 *       the disk cache at startup and updated on every new fetch. Provides
 *       O(1) lookup within a session.</li>
 * </ol>
 *
 * Cache key: {@code "lon1,lat1→lon2,lat2"} rounded to 5 decimal places
 * (~1 m precision). Coordinates alone — no city names — to avoid
 * false misses when the same location is added under different names.
 * A→B and B→A share the same cache entry (symmetric roads).
 *
 * <h3>Fallback</h3>
 * When OSRM is unavailable the implementation falls back to
 * Haversine × 1.3 for distance and distance / 13.89 m/s for time.
 * Fallback results are <em>not</em> cached to disk so they are retried
 * when the network is available again.
 *
 * <h3>Thread safety</h3>
 * The in-memory cache is a {@link ConcurrentHashMap}. Disk writes are
 * synchronised on {@code DISK_LOCK} to prevent interleaved writes.
 */
public final class GroundDistanceProvider implements DistanceProvider {

    private static final double ROAD_CORRECTION = 1.3;
    private static final double CAR_SPEED_MS    = 13.89;
    private static final double EARTH_RADIUS_M  = 6_371_000.0;

    public static final GroundDistanceProvider INSTANCE = new GroundDistanceProvider();

    private final OsrmClient client = OsrmClient.INSTANCE;
    private final OsrmParser parser = OsrmParser.INSTANCE;

    // ── Cache ─────────────────────────────────────────────────────────────────

    /** In-memory cache: key → OsrmResult. */
    private final ConcurrentHashMap<String, OsrmResult> memCache =
            new ConcurrentHashMap<>();

    /** Path to the disk cache file; null if it cannot be resolved. */
    private static final Path DISK_CACHE_PATH = resolveDiskCachePath();

    /** Guards disk writes so concurrent Matrix populations don't corrupt the file. */
    private static final Object DISK_LOCK = new Object();

    // ── Constructor ───────────────────────────────────────────────────────────

    private GroundDistanceProvider() {
        loadDiskCache();
    }

    // ══════════════════════════════════════════════════════════
    // DistanceProvider
    // ══════════════════════════════════════════════════════════

    @Override
    public double distance(City a, City b) {
        if (a == null || b == null) return Double.NaN;
        if (a.equals(b)) return 0.0;
        OsrmResult r = cachedFetch(a, b);
        return Double.isNaN(r.distance)
                ? haversine(a, b) * ROAD_CORRECTION : r.distance;
    }

    @Override
    public double time(City a, City b) {
        if (a == null || b == null) return Double.NaN;
        if (a.equals(b)) return 0.0;
        OsrmResult r = cachedFetch(a, b);
        if (!Double.isNaN(r.duration)) return r.duration;
        double dist = Double.isNaN(r.distance)
                ? haversine(a, b) * ROAD_CORRECTION : r.distance;
        return Double.isNaN(dist) ? Double.NaN : dist / CAR_SPEED_MS;
    }

    // ══════════════════════════════════════════════════════════
    // Cache logic
    // ══════════════════════════════════════════════════════════

    /**
     * Returns a cached result for this city pair, fetching from OSRM only if
     * neither the in-memory nor the disk cache has an entry.
     */
    private OsrmResult cachedFetch(City a, City b) {
        String key = cacheKey(a, b);
        OsrmResult cached = memCache.get(key);
        if (cached != null) return cached;

        // Fetch from OSRM.
        String json = client.fetch(a.getX(), a.getY(), b.getX(), b.getY());
        OsrmResult result = new OsrmResult(
                parser.extractDistance(json), parser.extractDuration(json));

        // Only persist real OSRM results; don't cache fallback NaN values.
        if (Double.isFinite(result.distance) && Double.isFinite(result.duration)) {
            memCache.put(key, result);
            // Symmetric: B→A gives the same road (store both to avoid a future fetch).
            memCache.putIfAbsent(cacheKey(b, a), result);
            appendToDisk(key, result);
            appendToDisk(cacheKey(b, a), result);
        }

        return result;
    }

    /**
     * Cache key: {@code "lon1,lat1→lon2,lat2"} rounded to 5 decimal places.
     */
    private static String cacheKey(City a, City b) {
        return String.format("%.5f,%.5f\u2192%.5f,%.5f",
                a.getX(), a.getY(), b.getX(), b.getY());
    }

    // ── Disk persistence ──────────────────────────────────────────────────────

    /**
     * Loads the disk cache into {@link #memCache} at startup.
     * Format: one JSON object per line, e.g.
     * {@code {"key":"…","dist":1234.5,"dur":56.7}}
     */
    private void loadDiskCache() {
        if (DISK_CACHE_PATH == null || !Files.exists(DISK_CACHE_PATH)) return;
        try {
            int loaded = 0;
            for (String line : Files.readAllLines(DISK_CACHE_PATH, StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String key  = extractField(line, "key");
                String dist = extractField(line, "dist");
                String dur  = extractField(line, "dur");
                if (key == null || dist == null || dur == null) continue;
                try {
                    memCache.putIfAbsent(key,
                            new OsrmResult(Double.parseDouble(dist),
                                           Double.parseDouble(dur)));
                    loaded++;
                } catch (NumberFormatException ignored) {}
            }
            if (loaded > 0) {
                System.out.println("[OSRM] Loaded " + loaded
                        + " cached city pairs from disk.");
            }
        } catch (Exception e) {
            System.err.println("[OSRM] Failed to read disk cache: " + e.getMessage());
        }
    }

    /**
     * Appends a single cache entry to the disk file.
     * Each entry is one JSON object on its own line for easy streaming reads.
     */
    private static void appendToDisk(String key, OsrmResult r) {
        if (DISK_CACHE_PATH == null) return;
        String line = String.format("{\"key\":\"%s\",\"dist\":%.4f,\"dur\":%.4f}%n",
                key, r.distance, r.duration);
        synchronized (DISK_LOCK) {
            try {
                Files.createDirectories(DISK_CACHE_PATH.getParent());
                Files.writeString(DISK_CACHE_PATH, line,
                        StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE,
                        StandardOpenOption.APPEND);
            } catch (Exception e) {
                System.err.println("[OSRM] Failed to write disk cache: " + e.getMessage());
            }
        }
    }

    /**
     * Resolves the path to {@code cache/osrm_cache.json} relative to where
     * {@code places.json} lives on disk. Returns null if resolution fails
     * (e.g. running from inside a sealed JAR).
     */
    private static Path resolveDiskCachePath() {
        try {
            URL url = GroundDistanceProvider.class.getResource("/places.json");
            if (url == null) return null;
            Path resourcesDir = Path.of(url.toURI()).getParent();
            return resourcesDir.resolve("cache/osrm_cache.json");
        } catch (Exception e) {
            return null;
        }
    }

    // ── Haversine fallback ────────────────────────────────────────────────────

    private static double haversine(City a, City b) {
        double lat1  = Math.toRadians(a.getY());
        double lat2  = Math.toRadians(b.getY());
        double dLat  = Math.toRadians(b.getY() - a.getY());
        double dLon  = Math.toRadians(b.getX() - a.getX());
        double sinDL = Math.sin(dLat / 2), sinDn = Math.sin(dLon / 2);
        double h = sinDL * sinDL
                + Math.cos(lat1) * Math.cos(lat2) * sinDn * sinDn;
        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    /** Minimal JSON field extractor for the cache file format. */
    private static String extractField(String obj, String key) {
        String search = "\"" + key + "\":";
        int ki = obj.indexOf(search);
        if (ki < 0) return null;
        int vi = ki + search.length();
        // Skip optional whitespace and opening quote.
        while (vi < obj.length() && obj.charAt(vi) == ' ') vi++;
        if (vi >= obj.length()) return null;
        if (obj.charAt(vi) == '"') {
            int end = vi + 1;
            while (end < obj.length() && obj.charAt(end) != '"') end++;
            return obj.substring(vi + 1, end);
        }
        int end = vi;
        while (end < obj.length()) {
            char c = obj.charAt(end);
            if (c == ',' || c == '}') break;
            end++;
        }
        return obj.substring(vi, end).trim();
    }

    /** Bundles OSRM distance + duration from a single HTTP call. */
    private record OsrmResult(double distance, double duration) {}
}
