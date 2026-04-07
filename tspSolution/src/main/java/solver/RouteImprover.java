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
 *   <dd>Reverses a sub-sequence [i, k] of the route when doing so reduces
 *       total distance. A distance-delta filter skips pairs where the reversal
 *       cannot improve the route without evaluating the full sequence, reducing
 *       the number of O(n) evaluations in practice. O(n²) pairs per pass.</dd>
 *   <dt>Or-opt</dt>
 *   <dd>Relocates chains of 2 or 3 consecutive non-depot cities to the
 *       cheapest feasible position elsewhere in the route. Finds improvements
 *       that single-city relocate and 2-opt both miss. O(n²) per pass.</dd>
 * </dl>
 *
 * <h3>Strategy</h3>
 * All operators use first-improvement: the route is updated on the first
 * beneficial move found and the pass restarts immediately. Operators run in
 * the order relocate → 2-opt → or-opt; any improvement in any operator counts
 * as a productive pass.
 *
 * <h3>Deadlines</h3>
 * Every candidate route is evaluated with {@link RouteEvaluator#evaluate}
 * before being accepted, so deadline constraints are always respected. The
 * 2-opt delta filter is applied before the full evaluation; a move is only
 * evaluated when the distance delta is strictly negative.
 *
 * <h3>Precondition</h3>
 * {@code routeOrder} must be a closed route:
 * {@code routeOrder.get(0).equals(routeOrder.get(size - 1))}.
 * The method silently returns when this is not satisfied.
 */
public final class RouteImprover {

    private RouteImprover() {}

    /**
     * Applies interleaved relocate, 2-opt, and or-opt passes until no further
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
                             | twoOptPass(routeOrder, matrix)
                             | orOptPass(routeOrder, matrix);
            if (!improved) return;
        }
    }

    // ── Relocate ──────────────────────────────────────────────────────────────

    private static <T extends City> boolean relocatePass(
            List<T> routeOrder, Matrix<T> matrix) {

        Route<T> base = RouteEvaluator.evaluate(routeOrder, matrix);
        if (!base.isValid()) return false;

        double bestDist = base.getTotalDistance();
        int    n        = routeOrder.size();
        T      depot    = routeOrder.get(0);

        for (int from = 1; from <= n - 2; from++) {
            T node = routeOrder.get(from);
            if (node.equals(depot)) continue;

            List<T> withoutNode = new ArrayList<>(routeOrder);
            withoutNode.remove(from);

            for (int to = 1; to <= withoutNode.size() - 1; to++) {
                if (to == from) continue;

                List<T> trial = new ArrayList<>(withoutNode);
                trial.add(to, node);

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

    // ── 2-opt ─────────────────────────────────────────────────────────────────

    private static <T extends City> boolean twoOptPass(
            List<T> routeOrder, Matrix<T> matrix) {

        Route<T> base = RouteEvaluator.evaluate(routeOrder, matrix);
        if (!base.isValid()) return false;

        double bestDist = base.getTotalDistance();
        int    n        = routeOrder.size();

        for (int i = 1; i <= n - 3; i++) {
            int idxI  = matrix.getIndexOf(routeOrder.get(i - 1));
            int idxI1 = matrix.getIndexOf(routeOrder.get(i));

            for (int k = i + 1; k <= n - 2; k++) {
                int idxK  = matrix.getIndexOf(routeOrder.get(k));
                int idxK1 = matrix.getIndexOf(routeOrder.get(k + 1));

                double removed = matrix.getDistance(idxI,  idxI1)
                               + matrix.getDistance(idxK,  idxK1);
                double added   = matrix.getDistance(idxI,  idxK)
                               + matrix.getDistance(idxI1, idxK1);

                if (added >= removed - 1e-6) continue;

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

    // ── Or-opt ────────────────────────────────────────────────────────────────

    /**
     * One or-opt pass: tries relocating chains of 2 and 3 consecutive
     * non-depot cities to every other position in the route. Accepts and
     * returns {@code true} on the first improvement found.
     *
     * <p>Or-opt generalises single-city relocate to contiguous segments,
     * finding improvements that relocate and 2-opt both miss. Chain lengths
     * 2 and 3 are tried; length 1 is already covered by relocate.
     *
     * <p>Complexity: O(n²) evaluations per chain length, each O(n).
     */
    private static <T extends City> boolean orOptPass(
            List<T> routeOrder, Matrix<T> matrix) {

        Route<T> base = RouteEvaluator.evaluate(routeOrder, matrix);
        if (!base.isValid()) return false;

        double bestDist = base.getTotalDistance();
        int    n        = routeOrder.size();
        T      depot    = routeOrder.get(0);

        for (int chainLen = 2; chainLen <= 3; chainLen++) {
            for (int from = 1; from <= n - 1 - chainLen; from++) {

                boolean containsDepot = false;
                for (int c = from; c < from + chainLen; c++) {
                    if (routeOrder.get(c).equals(depot)) {
                        containsDepot = true;
                        break;
                    }
                }
                if (containsDepot) continue;

                List<T> chain       = routeOrder.subList(from, from + chainLen);
                List<T> withoutChain = new ArrayList<>(routeOrder.subList(0, from));
                withoutChain.addAll(routeOrder.subList(from + chainLen, n));

                for (int to = 1; to <= withoutChain.size() - 1; to++) {
                    List<T> trial = new ArrayList<>(withoutChain);
                    trial.addAll(to, chain);

                    Route<T> r = RouteEvaluator.evaluate(trial, matrix);
                    if (!r.isValid()) continue;

                    if (r.getTotalDistance() + 1e-6 < bestDist) {
                        routeOrder.clear();
                        routeOrder.addAll(trial);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // ── Utility ───────────────────────────────────────────────────────────────

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
