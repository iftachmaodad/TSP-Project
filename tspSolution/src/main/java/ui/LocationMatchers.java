package ui;

import domain.City;

/**
 * Shared geographic tolerance constants and helpers for UI deduplication
 * (overlay pins, add-city, search).
 *
 * <h3>Rationale</h3>
 * {@link domain.City#equals(Object)} uses ~1e⁻⁹° (~0.1 mm), which is correct
 * for model identity but too strict for coordinate jitter between different
 * data sources. Two results for the same place can differ by up to ~3 km
 * (~0.03°). This class provides a single ~500 m threshold that catches jitter
 * without merging genuinely distinct cities.
 *
 * <h3>Thread safety</h3>
 * Stateless — safe from any thread.
 */
public final class LocationMatchers {

    /**
     * Euclidean threshold in degrees; ~500 m at mid-latitudes.
     *
     * <p>Chosen to catch coordinate jitter between different place data sources
     * (same city returned with slightly different coordinates) while preserving
     * distinct cities in dense urban areas where 500 m is clearly a different
     * location.
     */
    public static final double SAME_PLACE_EPS_DEG = 0.005;

    private LocationMatchers() {}

    public static boolean samePlaceDegrees(double lon1, double lat1,
                                           double lon2, double lat2) {
        double dx = lon1 - lon2;
        double dy = lat1 - lat2;
        return Math.hypot(dx, dy) < SAME_PLACE_EPS_DEG;
    }

    public static boolean nearExistingCity(double lon, double lat, Iterable<City> cities) {
        for (City c : cities) {
            if (samePlaceDegrees(lon, lat, c.getX(), c.getY())) return true;
        }
        return false;
    }
}
