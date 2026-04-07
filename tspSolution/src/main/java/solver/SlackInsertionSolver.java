package solver;

import data.Matrix;
import domain.City;
import model.Route;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Slack-insertion heuristic — fast, single-pass variant.
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li><strong>Partition</strong> cities into <em>urgent</em> (those with
 *       deadlines) and <em>flexible</em> (no deadline).</li>
 *   <li><strong>Fast-fail</strong> if any urgent city is unreachable directly
 *       from the start within its deadline.</li>
 *   <li><strong>Insert urgent cities</strong> in tightest-slack order
 *       (smallest {@code deadline − directTravelTime}), each at the cheapest
 *       feasible position. Returns INVALID immediately if any urgent city has
 *       no feasible insertion.</li>
 *   <li><strong>Insert flexible cities</strong> greedily at the cheapest
 *       feasible position.</li>
 * </ol>
 *
 * <p>No local search is applied. This solver exists as a fast construction
 * baseline; for a stronger variant with multi-start and local search see
 * {@link SlackInsertion2OptSolver}.
 *
 * @param <T> the concrete city subtype
 */
public final class SlackInsertionSolver<T extends City> implements Solver<T> {

    private final Matrix<T> matrix;

    /**
     * @param matrix pre-populated distance/time matrix
     * @throws IllegalArgumentException if {@code matrix} is {@code null}
     */
    public SlackInsertionSolver(Matrix<T> matrix) {
        if (matrix == null) throw new IllegalArgumentException("Matrix cannot be null.");
        this.matrix = matrix;
    }

    @Override
    public Route<T> solve(T startCity) {
        if (startCity == null) {
            throw new IllegalArgumentException("Start city cannot be null.");
        }
        if (!matrix.getCities().contains(startCity)) {
            return SolverUtils.fail(matrix,
                    "Start city '" + startCity.getID() + "' is not in the matrix.");
        }
        if (!matrix.checkIntegrity()) {
            if (!matrix.populateMatrix() || !matrix.checkIntegrity()) {
                return SolverUtils.fail(matrix, "Matrix could not be populated.");
            }
        }

        // ── 1. Partition ──────────────────────────────────────────────────────
        List<T> urgent   = new ArrayList<>();
        List<T> flexible = new ArrayList<>();
        for (T c : matrix.getCities()) {
            if (c.equals(startCity)) continue;
            (c.hasDeadline() ? urgent : flexible).add(c);
        }

        // ── 2. Fast-fail ──────────────────────────────────────────────────────
        T infeasible = SolverUtils.findInfeasibleUrgent(matrix, startCity, urgent);
        if (infeasible != null) {
            Route<T> r = new Route<>(startCity);
            r.invalidate("Infeasible: direct travel to '"
                    + infeasible.getID() + "' already exceeds its deadline.");
            return r;
        }

        // ── 3. Insert urgent cities (tightest slack first) ────────────────────
        urgent.sort(Comparator.comparingDouble(
                u -> SolverUtils.slackFromStart(matrix, startCity, u)));

        List<T> routeOrder = new ArrayList<>();
        routeOrder.add(startCity);
        routeOrder.add(startCity); // closed route: [depot, depot]

        for (T u : urgent) {
            Insertion<T> ins =
                    SolverUtils.bestFeasibleInsertion(routeOrder, matrix, u);
            if (ins == null) {
                Route<T> r = new Route<>(startCity);
                r.invalidate("No feasible insertion for urgent city '"
                        + u.getID() + "'.");
                return r;
            }
            routeOrder.add(ins.index(), ins.city());
        }

        // ── 4. Insert flexible cities ─────────────────────────────────────────
        List<T> uninserted =
                SolverUtils.greedyInsertAll(routeOrder, matrix, flexible);

        // ── 5. Evaluate and annotate ──────────────────────────────────────────
        Route<T> result = RouteEvaluator.evaluate(routeOrder, matrix);

        if (result.isValid()) {
            result.setDebugLog(uninserted.isEmpty()
                    ? "Valid route completed (slack insertion, fast)."
                    : "Valid route found; " + uninserted.size()
                    + " flexible city/cities excluded to preserve deadlines.");
        } else {
            result.setDebugLog("Route invalid after construction (slack insertion, fast).");
        }

        return result;
    }
}
