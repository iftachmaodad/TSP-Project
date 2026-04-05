package domain;

import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract base class for a location (city / stop) in the TSP problem.
 *
 * <p>City is a pure data class: it stores geographic coordinates, a display ID,
 * and an optional arrival-deadline in seconds. All distance and time calculations
 * are delegated to {@link data.DistanceProvider} implementations registered per
 * concrete subclass in {@link CityRegistry}.
 *
 * <h3>Coordinate convention</h3>
 * <ul>
 *   <li>{@code x} = longitude (−180 to +180)</li>
 *   <li>{@code y} = latitude  (−90  to +90 )</li>
 * </ul>
 * Out-of-range coordinates are clamped and a warning is written to
 * {@code System.err}.
 *
 * <h3>Equality</h3>
 * Two cities are equal if and only if they share the same concrete class and
 * the same coordinates within 1 × 10⁻⁹ degree tolerance (~0.1 mm). The ID and
 * deadline are excluded so that the same physical location cannot appear twice
 * in a route under different names.
 *
 * <h3>Thread safety</h3>
 * The auto-naming counter uses one {@link AtomicInteger} per concrete type,
 * stored in a {@link ConcurrentHashMap}, making concurrent construction safe.
 */
public abstract class City {

    // ── Auto-naming: one AtomicInteger per concrete subclass ─────────────────
    private static final ConcurrentHashMap<Class<? extends City>, AtomicInteger>
            TYPE_COUNTERS = new ConcurrentHashMap<>();

    /** Sentinel meaning "this city has no deadline constraint". */
    public static final double NO_DEADLINE = Double.MAX_VALUE;

    // ── Immutable state ───────────────────────────────────────────────────────
    private final String id;
    private final double x;
    private final double y;
    private final double deadline;

    // ── Constructors ──────────────────────────────────────────────────────────

    /** No-deadline constructor with auto-generated ID. */
    protected City(double x, double y)                       { this(null, x, y, NO_DEADLINE); }

    /** Deadline constructor with auto-generated ID. */
    protected City(double x, double y, double deadline)      { this(null, x, y, deadline);    }

    /** No-deadline constructor with explicit ID. */
    protected City(String id, double x, double y)            { this(id,   x, y, NO_DEADLINE); }

    /**
     * Primary constructor — all others delegate here.
     *
     * @param id       display name; {@code null}, blank, or a registered
     *                 type-prefix value (e.g. {@code "AirCity3"}) triggers
     *                 auto-naming
     * @param x        longitude [−180, 180]; clamped if out of range
     * @param y        latitude  [−90,  90];  clamped if out of range
     * @param deadline arrival deadline in seconds from departure;
     *                 non-positive values are treated as {@link #NO_DEADLINE}
     */
    protected City(String id, double x, double y, double deadline) {

        // ── Clamp coordinates ─────────────────────────────────────────────────
        double cx = x, cy = y;
        if (x < -180 || x > 180 || y < -90 || y > 90) {
            System.err.println("[City] WARNING: coordinates (" + x + ", " + y
                    + ") out of normal range — clamping.");
            cx = ((x + 180) % 360 + 360) % 360 - 180;
            cy = Math.max(-90, Math.min(90, y));
        }

        // ── Resolve ID ────────────────────────────────────────────────────────
        String resolvedId = (id == null || id.isBlank()
                || CityRegistry.startsWithIgnoreCase(id))
                ? generateDefaultId()
                : id.trim();

        // ── Normalize deadline ────────────────────────────────────────────────
        double resolvedDeadline = (deadline <= 0) ? NO_DEADLINE : deadline;

        this.id       = resolvedId;
        this.x        = cx;
        this.y        = cy;
        this.deadline = resolvedDeadline;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns {@code true} if this city carries a deadline constraint. */
    public boolean hasDeadline() { return deadline != NO_DEADLINE; }

    /** Returns the display ID. Never {@code null} or blank. */
    public String getID()       { return id; }

    /** Returns the longitude. */
    public double getX()        { return x; }

    /** Returns the latitude. */
    public double getY()        { return y; }

    /** Returns the deadline in seconds, or {@link #NO_DEADLINE} if unconstrained. */
    public double getDeadline() { return deadline; }

    // ── Object overrides ──────────────────────────────────────────────────────

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || this.getClass() != obj.getClass()) return false;
        City other = (City) obj;
        final double EPS = 1e-9;
        return Math.abs(this.x - other.x) < EPS
            && Math.abs(this.y - other.y) < EPS;
    }

    @Override
    public int hashCode() {
        // Round to ~0.1 mm to match the equals tolerance.
        return Objects.hash(Math.round(x * 1e6), Math.round(y * 1e6));
    }

    @Override
    public String toString() {
        String s = id + " - {" + String.format("%.4f", x)
                + ", " + String.format("%.4f", y) + "}";
        if (hasDeadline()) {
            s += " | Due: " + String.format("%.0f", deadline) + "s";
        }
        return s;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /** Thread-safe auto-name generation, e.g. {@code "AirCity7"}. */
    private String generateDefaultId() {
        Class<? extends City> type = this.getClass();
        AtomicInteger counter =
                TYPE_COUNTERS.computeIfAbsent(type, k -> new AtomicInteger(0));
        return type.getSimpleName() + counter.incrementAndGet();
    }
}
