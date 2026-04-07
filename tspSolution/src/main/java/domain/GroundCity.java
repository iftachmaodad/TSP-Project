package domain;

/**
 * A city reachable by ground transport (car / truck).
 *
 * <p>Pure value type — coordinates, ID, optional deadline, and an optional
 * address string. All distance and time calculations are delegated to
 * {@link data.GroundDistanceProvider} via {@link CityRegistry}, which uses
 * the OSRM routing API.
 *
 * <p>When no address is supplied, a fallback {@code "lat, lon"} string is
 * generated automatically using the stored (post-clamping) coordinates so
 * that display code always has a non-null, consistent value.
 */
public final class GroundCity extends City {

    private final String address;

    public GroundCity(double x, double y)                                             { this(null, x, y, NO_DEADLINE, null); }
    public GroundCity(double x, double y, double deadline)                            { this(null, x, y, deadline, null);    }
    public GroundCity(String id, double x, double y)                                  { this(id,   x, y, NO_DEADLINE, null); }
    public GroundCity(String id, double x, double y, double deadline)                 { this(id,   x, y, deadline,    null); }

    public GroundCity(String id, double x, double y, double deadline, String address) {
        super(id, x, y, deadline);
        this.address = (address == null || address.isBlank())
                ? String.format("%.6f, %.6f", getY(), getX())
                : address;
    }

    /** Returns the address string, or a {@code "lat, lon"} fallback if none was supplied. */
    public String getAddress() { return address; }
}
