package domain;

/**
 * A city reachable by air (drone / plane).
 *
 * Pure data class — coordinates, ID, optional deadline.
 * Distance and time calculations are handled by AirDistanceProvider.
 */
public class AirCity extends City {

    // --- Constructors ---

    public AirCity(double x, double y)                              { super(x, y); }
    public AirCity(double x, double y, double deadline)             { super(x, y, deadline); }
    public AirCity(String ID, double x, double y)                   { super(ID, x, y); }
    public AirCity(String ID, double x, double y, double deadline)  { super(ID, x, y, deadline); }
}
