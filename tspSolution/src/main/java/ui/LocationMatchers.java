package ui;

import domain.City;

/**
 * Shared geographic tolerance for UI deduplication (search, overlay pins, add-city).
 *
 * <h3>Rationale</h3>
 * {@link domain.City#equals(Object)} uses ~1e⁻⁹° (~0.1 mm), which is correct for
 * model identity but too strict for Nominatim coordinate jitter. Nominatim can return
 * two results for the same administrative area (e.g. city boundary centroid vs city
 * hall) that differ by up to ~3 km (~0.03°). This class provides a single ~500 m
 * scale threshold that catches jitter without merging genuinely distinct cities.
 *
 * <h3>Thread safety</h3>
 * Stateless — safe from any thread.
 */
public final class LocationMatchers {

    /**
     * Euclidean threshold in degrees; ~500 m at mid-latitudes.
     *
     * <p>Chosen to catch Nominatim coordinate jitter (same city, different admin
     * boundary points) while preserving distinct cities in dense urban areas where
     * 500 m apart is already clearly a different location.
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
