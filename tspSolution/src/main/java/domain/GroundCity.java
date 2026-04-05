package domain;

/**
 * A city reachable by ground transport (car / truck).
 *
 * Pure data class — coordinates, ID, optional deadline, optional address.
 * Distance and time calculations are handled by GroundDistanceProvider.
 *
 * Phase 2 note: when OSRM integration is added, GroundDistanceProvider will
 * use the address field to resolve snapped road-network coordinates.
 */
public class GroundCity extends City {

    // --- Fields ---
    private final String address;

    // --- Constructors ---

    public GroundCity(double x, double y)                                            { this(null, x, y, NO_DEADLINE, null); }
    public GroundCity(double x, double y, double deadline)                           { this(null, x, y, deadline, null); }
    public GroundCity(String ID, double x, double y)                                 { this(ID, x, y, NO_DEADLINE, null); }
    public GroundCity(String ID, double x, double y, double deadline)                { this(ID, x, y, deadline, null); }

    public GroundCity(String ID, double x, double y, double deadline, String address) {
        super(ID, x, y, deadline);
        this.address = (address == null || address.isBlank())
                ? defaultAddress()
                : address;
    }

    // --- Methods ---

    private String defaultAddress() {
        // Latitude, Longitude format as a fallback address string
        return String.format("%.6f, %.6f", getY(), getX());
    }

    // --- Getters ---

    public String getAddress() { return address; }
}
