package data;

import domain.City;

import java.util.concurrent.ConcurrentHashMap;

/**
 * {@link DistanceProvider} for {@link domain.GroundCity}.
 *
 * <h3>Data source</h3>
 * Uses the OSRM public routing API. Each city pair requires one HTTP call that
 * returns both distance and duration.
 *
 * <h3>Session cache</h3>
 * Results are stored in a {@link ConcurrentHashMap} for the lifetime of the
 * application session. The same city pair is never fetched more than once per
 * session — each solve reads from the cache in O(1). The cache is cleared
 * automatically when the process exits (no disk I/O, no file to manage).
 *
 * <h3>Cache key</h3>
 * {@code "lon1,lat1→lon2,lat2"} with coordinates rounded to 5 decimal places
 * (~1 m precision). Both A→B and B→A are stored after a single fetch.
 *
 * <h3>Fallback</h3>
 * When OSRM is unavailable the implementation falls back to
 * Haversine × 1.3 for distance and distance / 13.89 m/s for time. Fallback
 * results are not cached so they are retried on the next call when the
 * network may have recovered.
 *
 * <h3>Thread safety</h3>
 * The in-memory cache is a {@link ConcurrentHashMap} — safe for concurrent
 * matrix population from multiple solver threads.
 */
public final class GroundDistanceProvider implements DistanceProvider {

    private static final double ROAD_CORRECTION = 1.3;
    private static final double CAR_SPEED_MS    = 13.89; // m/s (~50 km/h)
    private static final double EARTH_RADIUS_M  = 6_371_000.0;

    /** Stateless singleton; safe from any thread. */
    public static final GroundDistanceProvider INSTANCE = new GroundDistanceProvider();

    private final OsrmClient client = OsrmClient.INSTANCE;
    private final OsrmParser parser = OsrmParser.INSTANCE;

    /** Session-scoped in-memory cache. Cleared on process exit. */
    private final ConcurrentHashMap<String, OsrmResult> cache =
            new ConcurrentHashMap<>();

    private GroundDistanceProvider() {}

    // ── DistanceProvider ──────────────────────────────────────────────────────

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

    // ── Cache ─────────────────────────────────────────────────────────────────

    private OsrmResult cachedFetch(City a, City b) {
        String key = cacheKey(a, b);
        OsrmResult cached = cache.get(key);
        if (cached != null) return cached;

        String json = client.fetch(a.getX(), a.getY(), b.getX(), b.getY());
        OsrmResult result = new OsrmResult(
                parser.extractDistance(json), parser.extractDuration(json));

        // Only cache successful OSRM results — fallback values are not stored
        // so they are retried when the network recovers.
        if (Double.isFinite(result.distance) && Double.isFinite(result.duration)) {
            cache.put(key, result);
            cache.putIfAbsent(cacheKey(b, a), result);
        }
        return result;
    }

    private static String cacheKey(City a, City b) {
        return String.format("%.5f,%.5f\u2192%.5f,%.5f",
                a.getX(), a.getY(), b.getX(), b.getY());
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

    /** Bundles OSRM distance and duration returned by a single HTTP call. */
    private record OsrmResult(double distance, double duration) {}
}
