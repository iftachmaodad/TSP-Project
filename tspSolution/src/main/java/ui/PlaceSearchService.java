package ui;

import javafx.application.Platform;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * Provides place-pin data for the map overlay.
 *
 * <h3>Data sources (in priority order)</h3>
 * <ol>
 *   <li><b>Bundled data</b> — {@code /places.json} loaded at startup (~200 world
 *       cities and airports). Always available, instant, no network.</li>
 *   <li><b>Disk cache</b> — {@code cache/places_cache.json}, written by this
 *       class and read at startup. Grows over time as new areas are explored.
 *       Survives app restarts.</li>
 *   <li><b>Overpass API</b> — queried when the combined local+cache data has
 *       fewer than {@value #MIN_PINS} pins in the visible area. Results are
 *       saved to the disk cache so the same area is never fetched twice.</li>
 * </ol>
 *
 * <h3>Singleton</h3>
 * Use {@link #INSTANCE}. The bundled and disk-cached place lists are static
 * (shared across the JVM), so instantiating multiple copies would create
 * redundant background threads without benefit.
 *
 *
 * <h3>Thread safety</h3>
 * All Overpass calls run on a single background thread.
 * Disk writes also happen on that thread so they never block the FX thread.
 */
public final class PlaceSearchService {

    private static final String USER_AGENT     = "TSP-Solver/1.0 (student project)";
    private static final int    OVERPASS_TO_MS = 20_000;
    private static final int    MIN_PINS       = 5;
    private static final String OVERPASS       = "https://overpass-api.de/api/interpreter";
    // ── Grid cell size for Overpass queries ───────────────────────────────────
    private static final double CELL = 5.0;

    // ── In-memory Overpass cell cache (LRU, 30 cells) ─────────────────────────
    private static final int CACHE_MAX = 30;

    // ── Bundled + disk-cached place data ─────────────────────────────────────
    private static final List<MapMarker> ALL_PLACES = new ArrayList<>();
    private static final List<MapMarker> AIR_PLACES = new ArrayList<>();

    /** Path to the disk cache file; null if it cannot be resolved. */
    private static final Path DISK_CACHE_PATH = resolveCachePath();

    // Load bundled places.json and disk cache BEFORE exposing INSTANCE.
    // Static fields and static blocks run in declaration order in Java — INSTANCE
    // must be last so the lists are fully populated when it is first accessed.
    static {
        loadBundled();
        loadDiskCache();
    }

    /**
     * Singleton instance — use this instead of {@code new PlaceSearchService()}.
     * Declared after the static block so that {@link #ALL_PLACES} and
     * {@link #AIR_PLACES} are fully populated before any caller can use it.
     */
    public static final PlaceSearchService INSTANCE = new PlaceSearchService();

    // ── Instance fields ───────────────────────────────────────────────────────
    private PlaceSearchService() {} // singleton — use INSTANCE

    private final Map<String, List<MapMarker>> overpassCache =
            new LinkedHashMap<>(CACHE_MAX, 0.75f, true) {
                @Override protected boolean removeEldestEntry(
                        Map.Entry<String, List<MapMarker>> e) {
                    return size() > CACHE_MAX;
                }
            };
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "place-search");
        t.setDaemon(true);
        return t;
    });

    // ══════════════════════════════════════════════════════════
    // Public API
    // ══════════════════════════════════════════════════════════

    /** Maximum Overpass calls we allow per session before warning the user. */
    public static final int OVERPASS_SESSION_LIMIT = 10;

    /** How many Overpass calls have been made this session. FX-thread only. */
    private int overpassCallCount = 0;

    /** Returns how many Overpass API calls have been made this session. */
    public int getOverpassCallCount() { return overpassCallCount; }


    /**
     * Returns pins from the bundled {@code places.json} and disk cache only —
     * never touches the network. Safe to call on every viewport change.
     *
     * <p>The callback always fires synchronously on the calling thread
     * (which must be the FX thread).
     */
    public void searchBundledOnly(double west, double south, double east, double north,
                                  String placeType, Consumer<List<MapMarker>> onResult) {
        boolean isAir = "airport".equals(placeType);
        List<MapMarker> local = bboxFilter(isAir ? AIR_PLACES : ALL_PLACES,
                west, south, east, north);
        onResult.accept(local);
    }

    /**
     * Returns pins for the visible bounding box.
     *
     * <p>If bundled + cached data is sufficient (≥ {@value #MIN_PINS} pins)
     * the callback fires immediately on the FX thread with no network call.
     * Otherwise Overpass is queried in the background and the callback fires
     * once with the merged result.
     */
    public void searchInBounds(double west, double south, double east, double north,
                               String placeType, Consumer<List<MapMarker>> onResult) {
        boolean isAir = "airport".equals(placeType);
        List<MapMarker> source = isAir ? AIR_PLACES : ALL_PLACES;

        List<MapMarker> local = bboxFilter(source, west, south, east, north);
        if (local.size() >= MIN_PINS) {
            Platform.runLater(() -> onResult.accept(local));
            return;
        }

        String cellKey = cellKey(isAir, west, south);

        synchronized (overpassCache) {
            if (overpassCache.containsKey(cellKey)) {
                List<MapMarker> merged =
                        merge(local, overpassCache.get(cellKey), west, south, east, north);
                Platform.runLater(() -> onResult.accept(merged));
                return;
            }
        }

        // Fetch from Overpass — single delivery, no double-fire.
        // Increment call counter on the FX thread before submitting so the UI
        // can update the rate-limit indicator immediately.
        Platform.runLater(() -> overpassCallCount++);
        worker.submit(() -> {
            double cW = Math.floor(west  / CELL) * CELL;
            double cS = Math.floor(south / CELL) * CELL;
            double cE = cW + CELL, cN = cS + CELL;

            List<MapMarker> fetched = isAir
                    ? fetchOverpassAirports(cS, cW, cN, cE)
                    : fetchOverpassCities(cS, cW, cN, cE);

            synchronized (overpassCache) {
                overpassCache.put(cellKey, fetched);
            }

            // Persist new markers to disk so they survive app restarts.
            appendToDiskCache(fetched, isAir);

            List<MapMarker> merged = merge(local, fetched, west, south, east, north);
            Platform.runLater(() -> onResult.accept(merged));
        });
    }

    public void shutdown() { worker.shutdownNow(); }

    // ══════════════════════════════════════════════════════════
    // Startup loading
    // ══════════════════════════════════════════════════════════

    /** Loads the bundled {@code /places.json} from the classpath. */
    private static void loadBundled() {
        try (InputStream in =
                PlaceSearchService.class.getResourceAsStream("/places.json")) {
            if (in == null) {
                System.err.println("[PlaceSearch] places.json not found.");
                return;
            }
            parsePlacesJson(readStream(in), false /* not overwriting — additive */);
            System.out.println("[PlaceSearch] Bundled: " + ALL_PLACES.size()
                    + " places, " + AIR_PLACES.size() + " airports.");
        } catch (Exception e) {
            System.err.println("[PlaceSearch] Failed to load places.json: " + e.getMessage());
        }
    }

    /**
     * Loads {@code cache/places_cache.json} from disk (if it exists) and
     * merges its entries into {@link #ALL_PLACES} / {@link #AIR_PLACES},
     * deduplicating against what was already loaded from the bundle.
     */
    private static void loadDiskCache() {
        if (DISK_CACHE_PATH == null || !Files.exists(DISK_CACHE_PATH)) return;
        try {
            String json = Files.readString(DISK_CACHE_PATH, StandardCharsets.UTF_8);
            int before = ALL_PLACES.size();
            parsePlacesJson(json, true /* deduplicate */);
            int added = ALL_PLACES.size() - before;
            if (added > 0) {
                System.out.println("[PlaceSearch] Disk cache: +" + added + " places.");
            }
        } catch (Exception e) {
            System.err.println("[PlaceSearch] Failed to load places cache: " + e.getMessage());
        }
    }

    /**
     * Parses a {@code {"places":[…]}} JSON string and adds entries to the
     * static lists. When {@code deduplicate} is true, entries whose coordinates
     * are within ~200 m of an existing entry are skipped.
     */
    private static void parsePlacesJson(String json, boolean deduplicate) {
        if (json == null || json.isBlank()) return;
        int arrIdx = json.indexOf("\"places\"");
        if (arrIdx < 0) return;
        int arrStart = json.indexOf('[', arrIdx);
        if (arrStart < 0) return;

        eachObject(json.substring(arrStart), obj -> {
            String name     = extractField(obj, "name");
            String lonS     = extractField(obj, "lon");
            String latS     = extractField(obj, "lat");
            String impS     = extractField(obj, "imp");
            String type     = extractField(obj, "type");
            String airportS = extractField(obj, "airport");
            if (name == null || lonS == null || latS == null) return;
            try {
                double  lon       = Double.parseDouble(lonS);
                double  lat       = Double.parseDouble(latS);
                double  imp       = impS != null ? Double.parseDouble(impS) : 0.5;
                boolean isAirport = "true".equals(airportS);

                if (deduplicate) {
                    for (MapMarker existing : ALL_PLACES) {
                        double dx = existing.lon() - lon, dy = existing.lat() - lat;
                        if (dx * dx + dy * dy < 0.0004) return; // ~200 m
                    }
                }

                MapMarker m = new MapMarker(name, lon, lat, imp,
                        type != null ? type : "city");
                ALL_PLACES.add(m);
                if (isAirport) AIR_PLACES.add(m);
            } catch (NumberFormatException ignored) {}
        });
    }

    // ══════════════════════════════════════════════════════════
    // Disk cache writes
    // ══════════════════════════════════════════════════════════

    /**
     * Appends newly fetched Overpass markers to the disk cache and to the
     * in-memory lists. Only markers not already present (within ~200 m of any
     * known entry) are written.
     *
     * <p>Strategy: append only the novel entries to the existing cache JSON
     * array rather than rewriting the whole file. This is O(novel) not O(all)
     * and avoids re-serialising the bundled data on every fetch.
     *
     * <p>Called from the background worker thread — never blocks the FX thread.
     */
    private static void appendToDiskCache(List<MapMarker> markers, boolean isAirport) {
        if (DISK_CACHE_PATH == null || markers.isEmpty()) return;

        // Identify markers not already in ALL_PLACES.
        List<MapMarker> novel = new ArrayList<>();
        synchronized (ALL_PLACES) {
            for (MapMarker m : markers) {
                boolean dup = false;
                for (MapMarker existing : ALL_PLACES) {
                    double dx = existing.lon() - m.lon(), dy = existing.lat() - m.lat();
                    if (dx * dx + dy * dy < 0.0004) { dup = true; break; }
                }
                if (!dup) novel.add(m);
            }
            if (novel.isEmpty()) return;

            // Merge into in-memory lists under the same lock.
            ALL_PLACES.addAll(novel);
            if (isAirport) AIR_PLACES.addAll(novel);
        }

        // Append only the novel entries to the cache file.
        // The file format is {"places":[…]} — we rewrite it fully but only
        // with entries that came from Overpass (novel), not the bundled data.
        // The bundled data is already in places.json and doesn't need duplicating.
        // On load, parsePlacesJson() deduplicates both sources anyway.
        try {
            Files.createDirectories(DISK_CACHE_PATH.getParent());

            // Read whatever was already cached on disk.
            List<MapMarker> existing = new ArrayList<>();
            if (Files.exists(DISK_CACHE_PATH)) {
                String raw = Files.readString(DISK_CACHE_PATH, StandardCharsets.UTF_8);
                // Parse existing cache entries.
                int arrIdx = raw.indexOf("\"places\"");
                if (arrIdx >= 0) {
                    int arrStart = raw.indexOf('[', arrIdx);
                    if (arrStart >= 0) {
                        eachObject(raw.substring(arrStart), obj -> {
                            String name  = extractField(obj, "name");
                            String lonS  = extractField(obj, "lon");
                            String latS  = extractField(obj, "lat");
                            String impS  = extractField(obj, "imp");
                            String type  = extractField(obj, "type");
                            if (name == null || lonS == null || latS == null) return;
                            try {
                                existing.add(new MapMarker(name,
                                        Double.parseDouble(lonS),
                                        Double.parseDouble(latS),
                                        impS != null ? Double.parseDouble(impS) : 0.5,
                                        type != null ? type : "city"));
                            } catch (NumberFormatException ignored) {}
                        });
                    }
                }
            }

            // Combine existing cached entries + novel entries (no bundled data).
            List<MapMarker> toWrite = new ArrayList<>(existing);
            for (MapMarker m : novel) {
                boolean dup = false;
                for (MapMarker e : toWrite) {
                    double dx = m.lon() - e.lon(), dy = m.lat() - e.lat();
                    if (dx * dx + dy * dy < 0.0004) { dup = true; break; }
                }
                if (!dup) toWrite.add(m);
            }

            StringBuilder sb = new StringBuilder("{\"places\":[");
            boolean first = true;
            for (MapMarker m : toWrite) {
                if (!first) sb.append(',');
                first = false;
                // airport flag: true if the marker came from an airport Overpass fetch
                // or if its placeType is "airport".
                boolean air = "airport".equals(m.placeType());
                sb.append(String.format(
                        "{\"name\":\"%s\",\"lon\":%.6f,\"lat\":%.6f," +
                        "\"imp\":%.4f,\"type\":\"%s\",\"airport\":%b}",
                        m.label().replace("\\", "\\\\").replace("\"", "\\\""),
                        m.lon(), m.lat(), m.importance(), m.placeType(), air));
            }
            sb.append("]}");
            Files.writeString(DISK_CACHE_PATH, sb, StandardCharsets.UTF_8);
        } catch (Exception e) {
            System.err.println("[PlaceSearch] Failed to write places cache: " + e.getMessage());
        }
    }

    // ══════════════════════════════════════════════════════════
    // Overpass fetchers
    // ══════════════════════════════════════════════════════════

    private List<MapMarker> fetchOverpassAirports(double s, double w, double n, double e) {
        String bbox = s + "," + w + "," + n + "," + e;
        String query = "[out:json][timeout:20];" +
                "(node[\"aeroway\"=\"aerodrome\"](" + bbox + ");" +
                "way[\"aeroway\"=\"aerodrome\"](" + bbox + "););" +
                "out center tags 60;";
        String json = doPost(OVERPASS,
                "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
        if (json == null) return List.of();

        List<MapMarker> out = new ArrayList<>();
        eachElement(json, el -> {
            double[] coords = coords(el);
            if (coords == null) return;
            String name = tagVal(el, "name:en");
            if (name == null) name = tagVal(el, "name");
            if (name == null) return;
            String iata = tagVal(el, "iata");
            out.add(new MapMarker(name, coords[0], coords[1],
                    iata != null ? 0.75 : 0.45, "airport"));
        });
        return out;
    }

    private List<MapMarker> fetchOverpassCities(double s, double w, double n, double e) {
        String bbox = s + "," + w + "," + n + "," + e;
        String query = "[out:json][timeout:20];" +
                "(node[\"place\"~\"city|town\"](" + bbox + "););" +
                "out body 80;";
        String json = doPost(OVERPASS,
                "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8));
        if (json == null) return List.of();

        List<MapMarker> out = new ArrayList<>();
        eachElement(json, el -> {
            double[] coords = coords(el);
            if (coords == null) return;
            String name = tagVal(el, "name:en");
            if (name == null) name = tagVal(el, "name");
            if (name == null) return;
            String place = tagVal(el, "place");
            String popS  = tagVal(el, "population");
            double pop = 0;
            if (popS != null) {
                try { pop = Double.parseDouble(popS.replaceAll("[^0-9]", "")); }
                catch (NumberFormatException ignored) {}
            }
            double imp = pop > 0
                    ? Math.min(0.9, 0.3 + Math.log10(pop + 1) / 7.5)
                    : "city".equals(place) ? 0.6 : 0.4;
            out.add(new MapMarker(name, coords[0], coords[1], imp,
                    "city".equals(place) ? "city" : "town"));
        });
        return out;
    }

    // ══════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════

    private static String cellKey(boolean isAir, double west, double south) {
        return (isAir ? "air" : "gnd") + ":"
                + (int)(Math.floor(west  / CELL) * CELL) + ","
                + (int)(Math.floor(south / CELL) * CELL);
    }

    private static List<MapMarker> bboxFilter(List<MapMarker> src,
            double west, double south, double east, double north) {
        List<MapMarker> out = new ArrayList<>();
        for (MapMarker m : src) {
            if (m.lat() < south || m.lat() > north) continue;
            double lon = m.lon();
            boolean inLon = west <= east
                    ? lon >= west && lon <= east
                    : lon >= west || lon <= east;
            if (inLon) out.add(m);
        }
        return out;
    }

    private static List<MapMarker> merge(List<MapMarker> local, List<MapMarker> fetched,
            double west, double south, double east, double north) {
        List<MapMarker> result = new ArrayList<>(local);
        for (MapMarker m : fetched) {
            if (m.lat() < south || m.lat() > north) continue;
            double lon = m.lon();
            boolean inLon = west <= east
                    ? lon >= west && lon <= east
                    : lon >= west || lon <= east;
            if (!inLon) continue;
            boolean dup = false;
            for (MapMarker r : result) {
                double dx = m.lon() - r.lon(), dy = m.lat() - r.lat();
                if (dx * dx + dy * dy < 0.0004) { dup = true; break; }
            }
            if (!dup) result.add(m);
        }
        result.sort((a, b) -> Double.compare(b.importance(), a.importance()));
        return result;
    }

    /**
     * Resolves the path to {@code cache/places_cache.json} by finding where
     * {@code /places.json} lives on disk and taking its parent directory.
     * Returns null if the resource cannot be resolved to a real file (e.g.
     * inside a sealed JAR).
     */
    private static Path resolveCachePath() {
        try {
            URL url = PlaceSearchService.class.getResource("/places.json");
            if (url == null) return null;
            Path resourcesDir = Path.of(url.toURI()).getParent();
            return resourcesDir.resolve("cache/places_cache.json");
        } catch (Exception e) {
            return null;
        }
    }

    // ── Overpass JSON parsing ─────────────────────────────────────────────────

    private static void eachElement(String json, Consumer<String> h) {
        if (json == null) return;
        int idx = json.indexOf("\"elements\"");
        if (idx < 0) return;
        int arr = json.indexOf('[', idx);
        if (arr < 0) return;
        int depth = 0, start = -1;
        for (int i = arr; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') { if (depth++ == 0) start = i; }
            else if (c == '}' && --depth == 0 && start >= 0) {
                h.accept(json.substring(start, i + 1)); start = -1;
            }
        }
    }

    private static double[] coords(String el) {
        try {
            int ci = el.indexOf("\"center\"");
            if (ci >= 0) {
                String center = el.substring(ci);
                String la = extractField(center, "lat"), lo = extractField(center, "lon");
                if (la != null && lo != null)
                    return new double[]{Double.parseDouble(lo), Double.parseDouble(la)};
            }
            String la = extractField(el, "lat"), lo = extractField(el, "lon");
            if (la != null && lo != null)
                return new double[]{Double.parseDouble(lo), Double.parseDouble(la)};
        } catch (NumberFormatException ignored) {}
        return null;
    }

    private static String tagVal(String el, String key) {
        int ti = el.indexOf("\"tags\"");
        if (ti < 0) return null;
        return extractField(el.substring(ti), key);
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private static void eachObject(String json, Consumer<String> h) {
        if (json == null) return;
        int depth = 0, start = -1;
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '{') { if (depth++ == 0) start = i; }
            else if (c == '}' && --depth == 0 && start >= 0) {
                h.accept(json.substring(start, i + 1)); start = -1;
            }
        }
    }

    private static String extractField(String obj, String key) {
        String search = "\"" + key + "\"";
        int ki = obj.indexOf(search);
        if (ki < 0) return null;
        int colon = obj.indexOf(':', ki + search.length());
        if (colon < 0) return null;
        int vi = colon + 1;
        while (vi < obj.length() && obj.charAt(vi) == ' ') vi++;
        if (vi >= obj.length()) return null;
        if (obj.charAt(vi) == '"') {
            int end = vi + 1;
            while (end < obj.length()) {
                if (obj.charAt(end) == '"' && obj.charAt(end - 1) != '\\') break;
                end++;
            }
            return obj.substring(vi + 1, end);
        } else {
            int end = vi;
            while (end < obj.length()) {
                char c = obj.charAt(end);
                if (c == ',' || c == '}' || c == ']' || c == ' ' || c == '\n') break;
                end++;
            }
            String val = obj.substring(vi, end).trim();
            return val.isEmpty() ? null : val;
        }
    }

    private static String readStream(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
        }
        return sb.toString();
    }

    // ── Network ───────────────────────────────────────────────────────────────

    private String doPost(String urlStr, String body) {
        try {
            HttpURLConnection conn =
                    (HttpURLConnection) URI.create(urlStr).toURL().openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            conn.setConnectTimeout(OVERPASS_TO_MS);
            conn.setReadTimeout(OVERPASS_TO_MS);
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            if (conn.getResponseCode() != 200) return null;
            return readStream(conn.getInputStream());
        } catch (Exception e) { return null; }
    }
}
