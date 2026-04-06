package data;

import domain.City;

/**
 * {@link DistanceProvider} for {@link domain.AirCity}.
 *
 * <h3>Distance</h3>
 * Haversine great-circle formula; Earth radius 6 371 000 m.
 *
 * <h3>Time</h3>
 * {@code distance / DRONE_SPEED_MS}, where the drone cruise speed is
 * 16.67 m/s (~60 km/h).
 *
 * <p>All computation is local — no external API is involved.
 *
 * <h3>Thread safety</h3>
 * Stateless singleton; safe from any thread.
 */
public final class AirDistanceProvider implements DistanceProvider {

    private static final double DRONE_SPEED_MS = 16.67;   // m/s (~60 km/h)
    private static final double EARTH_RADIUS_M = 6_371_000.0;

    /** Stateless singleton; safe from any thread. */
    public static final AirDistanceProvider INSTANCE = new AirDistanceProvider();

    private AirDistanceProvider() {}

    @Override
    public double distance(City a, City b) {
        if (a == null || b == null) return Double.NaN;
        if (a.equals(b)) return 0.0;

        double lat1   = Math.toRadians(a.getY());
        double lat2   = Math.toRadians(b.getY());
        double dLat   = Math.toRadians(b.getY() - a.getY());
        double dLon   = Math.toRadians(b.getX() - a.getX());

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
