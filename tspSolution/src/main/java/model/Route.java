package model;

import domain.City;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * An ordered sequence of cities representing one solution to the TSP, together
 * with cumulative travel metrics and deadline-validity tracking.
 *
 * <h3>Lifecycle</h3>
 * <ol>
 *   <li>Create with a start city: {@code new Route<>(start)}</li>
 *   <li>Extend step-by-step via {@link #addStep}</li>
 *   <li>Check {@link #isValid()} and read {@link #getTotalDistance()}</li>
 * </ol>
 *
 * <h3>Invariants</h3>
 * <ul>
 *   <li>{@code path.size() == arrivalTimes.size() == legDistances.size()} always.</li>
 *   <li>Once {@link #isValid()} returns {@code false} it never returns {@code true} again.</li>
 *   <li>Steps added after invalidation are <em>not</em> accumulated: distance and
 *       time stop increasing so the metrics reflect the point of failure, not
 *       phantom work done after it.</li>
 *   <li>A NaN or infinite distance/time value immediately invalidates the route
 *       rather than silently poisoning the accumulators.</li>
 * </ul>
 *
 * @param <T> the concrete city subtype (AirCity, GroundCity, …)
 */
public final class Route<T extends City> {

    // ── State ─────────────────────────────────────────────────────────────────
    private final List<T>      path;
    private final List<Double> arrivalTimes; // cumulative seconds from departure
    private final List<Double> legDistances; // meters per leg; entry 0 is always 0.0

    private double  totalDistance;
    private double  totalTime;
    private boolean valid;
    private String  debugLog = "";

    // ── Constructors ──────────────────────────────────────────────────────────

    /**
     * Creates a new route starting at {@code start}.
     *
     * @param start the depot / origin city; must not be {@code null}
     * @throws IllegalArgumentException if {@code start} is {@code null}
     */
    public Route(T start) {
        if (start == null) {
            throw new IllegalArgumentException("Start city cannot be null.");
        }

        path         = new ArrayList<>();
        arrivalTimes = new ArrayList<>();
        legDistances = new ArrayList<>();

        path.add(start);
        arrivalTimes.add(0.0);
        legDistances.add(0.0); // no incoming leg for the start city

        totalDistance = 0.0;
        totalTime     = 0.0;
        valid         = true;

        // A start city can technically have a deadline — check it immediately.
        if (start.hasDeadline() && totalTime > start.getDeadline()) {
            invalidate("Start city deadline already missed at t=0.");
        }
    }

    // ── Building ──────────────────────────────────────────────────────────────

    /**
     * Appends one leg to the route.
     *
     * <p>If the route is already invalid, the city is still recorded in
     * {@link #getPath()} (for diagnostics) but the distance and time
     * accumulators are <em>not</em> updated.
     *
     * <p>A NaN or infinite {@code distFromLast} / {@code timeFromLast} value
     * immediately invalidates the route with an explanatory message.
     *
     * @param nextCity     city to travel to; {@code null} invalidates the route
     * @param distFromLast distance in metres from the previous city
     * @param timeFromLast travel time in seconds from the previous city
     */
    public void addStep(T nextCity, double distFromLast, double timeFromLast) {
        if (nextCity == null) {
            invalidate("Attempted to add a null city to the route.");
            return;
        }

        // Guard against NaN/Infinity poisoning the accumulators.
        if (!Double.isFinite(distFromLast) || !Double.isFinite(timeFromLast)) {
            path.add(nextCity);
            arrivalTimes.add(totalTime);     // arrival time unchanged
            legDistances.add(Double.NaN);
            invalidate("Non-finite distance or time to city " + nextCity.getID()
                    + " (dist=" + distFromLast + ", time=" + timeFromLast + ").");
            return;
        }

        // Only accumulate when the route is still valid; once broken we record
        // the city for diagnostic purposes but freeze the metrics.
        if (valid) {
            totalDistance += distFromLast;
            totalTime     += timeFromLast;
        }

        path.add(nextCity);
        arrivalTimes.add(valid ? totalTime : Double.NaN);
        legDistances.add(distFromLast);

        if (valid && nextCity.hasDeadline() && totalTime > nextCity.getDeadline()) {
            invalidate("Missed deadline at " + nextCity.getID()
                    + " (arrived " + String.format("%.1f", totalTime)
                    + "s, deadline " + String.format("%.1f", nextCity.getDeadline()) + "s).");
        }
    }

    /**
     * Marks this route as invalid with an optional diagnostic message.
     * Subsequent calls are safe but the first non-blank message is kept.
     *
     * @param msg diagnostic text; ignored if {@code null} or blank
     */
    public void invalidate(String msg) {
        valid = false;
        if (msg != null && !msg.isBlank() && debugLog.isBlank()) {
            debugLog = msg;
        }
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    /** Number of cities in the path (including the start city and the closing return). */
    public int    size()             { return path.size(); }

    /** Total route distance in metres. Meaningful only when {@link #isValid()} is true. */
    public double getTotalDistance() { return totalDistance; }

    /** Total travel time in seconds. Meaningful only when {@link #isValid()} is true. */
    public double getTotalTime()     { return totalTime; }

    /** Returns {@code true} if no deadline has been violated and all data are finite. */
    public boolean isValid()         { return valid; }

    /** Returns the last city in the path, or {@code null} if the path is empty. */
    public T getLastCity() {
        return path.isEmpty() ? null : path.get(path.size() - 1);
    }

    /** Returns the diagnostic log entry, or an empty string if none was set. */
    public String getDebugLog()           { return debugLog; }

    /**
     * Overwrites the diagnostic log entry (used by solvers to annotate routes).
     * Prefer {@link #invalidate(String)} for error messages.
     */
    public void setDebugLog(String log) { this.debugLog = (log == null) ? "" : log; }

    /**
     * Returns an unmodifiable view of the city sequence.
     * Entry 0 is the start city; the last entry is also the start city for
     * closed (round-trip) routes.
     */
    public List<T> getPath() {
        return Collections.unmodifiableList(path);
    }

    /**
     * Returns an unmodifiable view of cumulative arrival times in seconds.
     * {@code arrivalTimes.get(0)} is always 0.0 (the depot).
     * Entries after the route became invalid are {@link Double#NaN}.
     */
    public List<Double> getArrivalTimes() {
        return Collections.unmodifiableList(arrivalTimes);
    }

    /**
     * Returns an unmodifiable view of per-leg distances in metres.
     * {@code legDistances.get(0)} is always 0.0 (no incoming leg for the start).
     * Entry {@code i} is the distance from city {@code i−1} to city {@code i}.
     */
    public List<Double> getLegDistances() {
        return Collections.unmodifiableList(legDistances);
    }

    // ── toString ──────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== ROUTE REPORT ===\n");
        sb.append("Status   : ").append(valid ? "VALID" : "INVALID").append('\n');
        sb.append("Distance : ").append(String.format("%.2f", totalDistance)).append(" m\n");
        sb.append("Time     : ").append(String.format("%.2f", totalTime)).append(" s\n");
        if (!debugLog.isBlank()) {
            sb.append("Log      : ").append(debugLog).append('\n');
        }
        sb.append("\n--- Itinerary ---\n");

        for (int i = 0; i < path.size(); i++) {
            T      city    = path.get(i);
            double arrival = (i < arrivalTimes.size()) ? arrivalTimes.get(i) : Double.NaN;
            String arrStr  = Double.isNaN(arrival) ? "N/A" : String.format("%.1f", arrival);
            String note    = "";
            if (city.hasDeadline() && !Double.isNaN(arrival)) {
                double due = city.getDeadline();
                note = (arrival > due)
                        ? " [LATE! due=" + String.format("%.0f", due) + "s]"
                        : " [due="       + String.format("%.0f", due) + "s]";
            }
            sb.append(String.format("%-3d | %-9s | %s%s\n",
                    i + 1, arrStr + "s", city.getID(), note));
        }
        return sb.toString();
    }
}
