package domain;

/**
 * A city reachable by air (drone / fixed-wing).
 *
 * <p>Pure value type — coordinates, ID, optional deadline.
 * All distance and time calculations are delegated to
 * {@link data.AirDistanceProvider} via {@link CityRegistry}.
 */
public final class AirCity extends City {

    public AirCity(double x, double y)                             { super(x, y); }
    public AirCity(double x, double y, double deadline)            { super(x, y, deadline); }
    public AirCity(String id, double x, double y)                  { super(id, x, y); }
    public AirCity(String id, double x, double y, double deadline) { super(id, x, y, deadline); }
}
