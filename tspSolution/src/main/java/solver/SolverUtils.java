package solver;

import data.Matrix;
import domain.City;
import model.Route;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Stateless utility methods shared by all solver implementations.
 *
 * <p>Every method is a pure function: it does not retain state between calls
 * and does not modify the matrix. Callers may safely invoke these from
 * multiple threads.
 */
public final class SolverUtils {

    private SolverUtils() {
        throw new UnsupportedOperationException("SolverUtils is a utility class.");
    }

    // ── Travel helpers ────────────────────────────────────────────────────────

    /**
     * Returns the travel time between two cities from the pre-computed matrix.
     * Returns {@link Double#POSITIVE_INFINITY} instead of NaN when a city is
     * not found or the matrix has not been populated, so callers can use simple
     * {@code <} comparisons without special-casing NaN.
     */
    public static <T extends City> double safeTime(Matrix<T> matrix, T a, T b) {
        int i = matrix.getIndexOf(a), j = matrix.getIndexOf(b);
        if (i < 0 || j < 0) return Double.POSITIVE_INFINITY;
        double t = matrix.getTime(i, j);
        return Double.isFinite(t) ? t : Double.POSITIVE_INFINITY;
    }

    /**
     * Returns the travel distance between two cities from the pre-computed
     * matrix, or {@link Double#POSITIVE_INFINITY} on any error.
     */
    public static <T extends City> double safeDistance(Matrix<T> matrix, T a, T b) {
        int i = matrix.getIndexOf(a), j = matrix.getIndexOf(b);
        if (i < 0 || j < 0) return Double.POSITIVE_INFINITY;
        double d = matrix.getDistance(i, j);
        return Double.isFinite(d) ? d : Double.POSITIVE_INFINITY;
    }

    /**
     * Returns {@code deadline − directTravelTime(start → city)}.
     * A negative result means direct travel from the start already exceeds
     * the deadline (infeasible even as the first stop).
     * Only meaningful for cities that have a deadline.
     */
    public static <T extends City> double slackFromStart(
            Matrix<T> matrix, T start, T city) {
        return city.getDeadline() - safeTime(matrix, start, city);
    }

    // ── Infeasibility detection ───────────────────────────────────────────────

    /**
     * Returns the first city in {@code urgent} for which direct travel from
     * {@code start} already exceeds its deadline, making any route that
     * includes it infeasible.
     *
     * <p>This is a necessary — not sufficient — condition for infeasibility.
     * A city whose direct travel time exceeds its deadline is unreachable
     * regardless of ordering, so any route containing it is guaranteed
     * invalid.
     *
     * @return the first unreachable urgent city, or {@code null} if all are
     *         reachable via direct travel
     */
    public static <T extends City> T findInfeasibleUrgent(
            Matrix<T> matrix, T start, List<T> urgent) {
        for (T u : urgent) {
            if (safeTime(matrix, start, u) > u.getDeadline()) return u;
        }
        return null;
    }

    // ── Insertion helpers ─────────────────────────────────────────────────────

    /**
     * Finds the cheapest feasible position to insert {@code city} into a
     * closed route.
     *
     * <p>Scans positions 1 to {@code routeOrder.size() − 1} inclusive,
     * evaluating the full route after each candidate insertion to verify
     * deadline compliance. The position with the minimum delta distance is
     * returned.
     *
     * @param routeOrder current route (first == last == depot, mutable)
     * @param matrix     pre-populated matrix
     * @param city       city to insert
     * @return best feasible insertion, or {@code null} if none exists
     */
    public static <T extends City> Insertion<T> bestFeasibleInsertion(
            List<T> routeOrder, Matrix<T> matrix, T city) {

        Insertion<T> best = null;

        for (int idx = 1; idx < routeOrder.size(); idx++) {
            double delta = RouteEvaluator.deltaDistanceIfInsert(
                    routeOrder, matrix, idx, city);
            if (!Double.isFinite(delta)) continue;

            // Only perform the full O(n) evaluation when delta is promising.
            List<T> candidate = new ArrayList<>(routeOrder);
            candidate.add(idx, city);
            if (!RouteEvaluator.evaluate(candidate, matrix).isValid()) continue;

            if (best == null || delta < best.deltaDistance()) {
                best = new Insertion<>(idx, city, delta);
            }
        }

        return best;
    }

    /**
     * Greedily inserts all cities in {@code candidates} into {@code routeOrder},
     * each time choosing the city–position pair with the smallest delta
     * distance that keeps the route feasible.
     *
     * <p>Modifies {@code routeOrder} in place. Cities that cannot be inserted
     * without violating a deadline are left in the returned list.
     *
     * @param routeOrder mutable closed route (first == last == depot)
     * @param matrix     pre-populated matrix
     * @param candidates cities to insert (copied; original list is unchanged)
     * @return list of cities that could not be inserted (empty on full success)
     */
    public static <T extends City> List<T> greedyInsertAll(
            List<T> routeOrder, Matrix<T> matrix, List<T> candidates) {

        List<T> remaining = new ArrayList<>(candidates);

        while (!remaining.isEmpty()) {
            Insertion<T> best = null;

            for (T candidate : remaining) {
                Insertion<T> ins =
                        bestFeasibleInsertion(routeOrder, matrix, candidate);
                if (ins != null
                        && (best == null
                            || ins.deltaDistance() < best.deltaDistance())) {
                    best = ins;
                }
            }

            if (best == null) break; // no candidate can be inserted

            routeOrder.add(best.index(), best.city());
            remaining.remove(best.city());
        }

        return remaining;
    }

    // ── Invalid-route tie-breaking ────────────────────────────────────────────

    /**
     * Returns the "less bad" of two invalid routes using the following
     * priority:
     * <ol>
     *   <li>More cities visited (larger path size).</li>
     *   <li>Shorter total distance as a tie-breaker.</li>
     * </ol>
     * Null-safe: if one argument is {@code null} the other is returned.
     */
    public static <T extends City> Route<T> chooseBetterInvalid(
            Route<T> a, Route<T> b) {
        if (a == null) return b;
        if (b == null) return a;
        if (b.size() != a.size()) return b.size() > a.size() ? b : a;
        return b.getTotalDistance() < a.getTotalDistance() ? b : a;
    }

    // ── Error helper ──────────────────────────────────────────────────────────

    /**
     * Creates an invalid route to return when a solver encounters an
     * unrecoverable error (bad matrix, missing start city, etc.).
     *
     * @throws IllegalStateException if the matrix has no cities at all
     */
    public static <T extends City> Route<T> fail(Matrix<T> matrix, String msg) {
        List<T> cities = matrix.getCities();
        if (cities.isEmpty()) {
            throw new IllegalStateException("Matrix has no cities.");
        }
        Route<T> r = new Route<>(cities.get(0));
        r.invalidate("SOLVER ERROR: " + msg);
        return r;
    }

    // ── Sorting helper ────────────────────────────────────────────────────────

    /**
     * Returns a new list containing the elements of {@code src} sorted by
     * {@code comparator}. The source list is not modified.
     */
    public static <T extends City> List<T> sortedCopy(
            List<T> src, Comparator<T> comparator) {
        List<T> copy = new ArrayList<>(src);
        copy.sort(comparator);
        return copy;
    }
}
