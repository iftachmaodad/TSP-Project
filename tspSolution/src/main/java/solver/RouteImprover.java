package solver;

import data.Matrix;
import domain.City;
import model.Route;

import java.util.ArrayList;
import java.util.List;

/**
 * Local-search improvement operators applied to a closed, valid route.
 *
 * <h3>Operators</h3>
 * <dl>
 *   <dt>Relocate</dt>
 *   <dd>Removes one city from its current position and re-inserts it at the
 *       cheapest other position. O(n²) per pass.</dd>
 *   <dt>2-opt</dt>
 *   <dd>Reverses a sub-sequence of the route if doing so reduces the total
 *       distance. O(n²) per pass.</dd>
 * </dl>
 *
 * <h3>Usage</h3>
 * Both operators use <em>first-improvement</em> strategy: as soon as a
 * beneficial move is found the route is updated and the pass restarts. This
 * is simpler than best-improvement and avoids re-evaluating all pairs after
 * each small improvement.
 *
 * <h3>Deadlines</h3>
 * Every trial permutation is evaluated with {@link RouteEvaluator#evaluate}
 * before being accepted, so deadline constraints are always respected.
 *
 * <h3>Precondition</h3>
 * The input {@code routeOrder} must be a <em>closed</em> route:
 * {@code routeOrder.get(0).equals(routeOrder.get(size−1))}.
 * The method silently returns if this is not satisfied or if the route is
 * already invalid.
 */
public final class RouteImprover {

    private RouteImprover() {}

    /**
     * Applies interleaved relocate + 2-opt passes until no further
     * improvement is found or {@code maxPasses} is reached.
     *
     * <p>Modifies {@code routeOrder} in place. Does nothing if the route has
     * fewer than 4 elements or is not closed.
     *
     * @param routeOrder mutable closed route list (first == last == depot)
     * @param matrix     pre-populated distance/time matrix
     * @param maxPasses  upper bound on improvement iterations
     */
    public static <T extends City> void improveClosedValid(
            List<T> routeOrder, Matrix<T> matrix, int maxPasses) {

        if (routeOrder == null || routeOrder.size() < 4) return;
        if (!routeOrder.get(0).equals(routeOrder.get(routeOrder.size() - 1))) return;

        for (int pass = 0; pass < maxPasses; pass++) {
            boolean improved = relocatePass(routeOrder, matrix)
                            | twoOptPass(routeOrder, matrix);
            if (!improved) return;
        }
    }

    // ── Relocate ──────────────────────────────────────────────────────────────

    /**
     * One relocate pass: tries moving each non-depot city to every other
     * position. Accepts and returns {@code true} on the first improvement.
     *
     * <p>Complexity: O(n²) evaluations, each O(n) → O(n³) worst case, but
     * in practice the first improvement is found quickly.
     */
    private static <T extends City> boolean relocatePass(
            List<T> routeOrder, Matrix<T> matrix) {

        Route<T> base = RouteEvaluator.evaluate(routeOrder, matrix);
        if (!base.isValid()) return false;

        double bestDist = base.getTotalDistance();
        int    n        = routeOrder.size();
        T      depot    = routeOrder.get(0);

        for (int from = 1; from <= n - 2; from++) {
            T node = routeOrder.get(from);
            if (node.equals(depot)) continue; // skip duplicate depot entries

            // Build a route with `node` removed.
            List<T> withoutNode = new ArrayList<>(routeOrder);
            withoutNode.remove(from);

            for (int to = 1; to <= withoutNode.size() - 1; to++) {
                if (to == from) continue; // same logical position — skip

                List<T> trial = new ArrayList<>(withoutNode);
                trial.add(to, node);

                Route<T> r = RouteEvaluator.evaluate(trial, matrix);
                if (!r.isValid()) continue;

                if (r.getTotalDistance() + 1e-6 < bestDist) {
                    routeOrder.clear();
                    routeOrder.addAll(trial);
                    return true; // first-improvement: restart outer loop
                }
            }
        }
        return false;
    }

    // ── 2-opt ─────────────────────────────────────────────────────────────────

    /**
     * One 2-opt pass: tries reversing every sub-sequence [i, k] inside the
     * route. Accepts and returns {@code true} on the first improvement.
     *
     * <p>Complexity: O(n²) pairs evaluated, each requiring an O(n) route
     * evaluation.
     */
    private static <T extends City> boolean twoOptPass(
            List<T> routeOrder, Matrix<T> matrix) {

        Route<T> base = RouteEvaluator.evaluate(routeOrder, matrix);
        if (!base.isValid()) return false;

        double bestDist = base.getTotalDistance();
        int    n        = routeOrder.size();

        for (int i = 1; i <= n - 3; i++) {
            for (int k = i + 1; k <= n - 2; k++) {
                List<T> trial = new ArrayList<>(routeOrder);
                reverse(trial, i, k);

                Route<T> r = RouteEvaluator.evaluate(trial, matrix);
                if (!r.isValid()) continue;

                if (r.getTotalDistance() + 1e-6 < bestDist) {
                    routeOrder.clear();
                    routeOrder.addAll(trial);
                    return true;
                }
            }
        }
        return false;
    }

    // ── Utility ───────────────────────────────────────────────────────────────

    /** Reverses the sub-list {@code list[i..k]} in place. */
    private static <T> void reverse(List<T> list, int i, int k) {
        while (i < k) {
            T tmp = list.get(i);
            list.set(i, list.get(k));
            list.set(k, tmp);
            i++;
            k--;
        }
    }
}
