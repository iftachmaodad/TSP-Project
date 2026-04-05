package data;

import domain.City;

/**
 * Distance/time provider for AirCity.
 *
 * Distance : Haversine great-circle formula (meters).
 * Time     : distance / drone cruise speed.
 *
 * All math is local — no external API needed.
 */
public final class AirDistanceProvider implements DistanceProvider {

    // Average drone cruise speed (~60 km/h)
    private static final double DRONE_SPEED_MS = 16.67; // m/s
    private static final double EARTH_RADIUS_M = 6_371_000.0;

    // Singleton — stateless, safe to share
    public static final AirDistanceProvider INSTANCE = new AirDistanceProvider();

    private AirDistanceProvider() {}

    // --- DistanceProvider ---

    @Override
    public double distance(City a, City b) {
        if (a == null || b == null) return Double.NaN;
        if (a.equals(b)) return 0.0;

        double lat1 = Math.toRadians(a.getY());
        double lat2 = Math.toRadians(b.getY());
        double dLat = Math.toRadians(b.getY() - a.getY());
        double dLon = Math.toRadians(b.getX() - a.getX());

        double sinDLat = Math.sin(dLat / 2);
        double sinDLon = Math.sin(dLon / 2);

        double h = sinDLat * sinDLat
                 + Math.cos(lat1) * Math.cos(lat2) * sinDLon * sinDLon;

        return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h));
    }

    @Override
    public double time(City a, City b) {
        if (a == null || b == null) return Double.NaN;
        if (a.equals(b)) return 0.0;

        double dist = distance(a, b);
        return Double.isNaN(dist) ? Double.NaN : dist / DRONE_SPEED_MS;
    }
}
