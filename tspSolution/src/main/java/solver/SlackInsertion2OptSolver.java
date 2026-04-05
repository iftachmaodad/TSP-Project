package solver;

import data.Matrix;
import domain.City;
import model.Route;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Slack-insertion heuristic with multi-start and local search — optimised
 * variant.
 *
 * <h3>Algorithm</h3>
 * <ol>
 *   <li><strong>Partition</strong> cities into <em>urgent</em> (deadline) and
 *       <em>flexible</em> (no deadline).</li>
 *   <li><strong>Fast-fail</strong> if any urgent city is unreachable directly
 *       from the start.</li>
 *   <li><strong>Generate orderings</strong> of the urgent cities:
 *       <ul>
 *         <li>Sorted by earliest deadline.</li>
 *         <li>Sorted by tightest slack from start.</li>
 *         <li>Sorted by nearest travel time from start.</li>
 *         <li>{@value #RANDOM_TRIES} random shuffles.</li>
 *       </ul>
 *   </li>
 *   <li>For each ordering:
 *       <ol type="a">
 *         <li>Build {@code [start, urgent…, start]} and evaluate.</li>
 *         <li>If valid, greedily insert flexible cities.</li>
 *         <li>Apply {@value #IMPROVEMENT_PASSES} passes of relocate + 2-opt.</li>
 *         <li>Track the best valid route across all orderings.</li>
 *       </ol>
 *   </li>
 *   <li>Return the best valid route, or the best invalid route if none exists.</li>
 * </ol>
 *
 * <p>For a single-pass, faster variant see {@link SlackInsertionSolver}.
 *
 * @param <T> the concrete city subtype
 */
public final class SlackInsertion2OptSolver<T extends City> implements Solver<T> {

    private static final int RANDOM_TRIES       = 25;
    private static final int IMPROVEMENT_PASSES = 8;

    private final Matrix<T> matrix;

    /**
     * @param matrix pre-populated distance/time matrix
     * @throws IllegalArgumentException if {@code matrix} is {@code null}
     */
    public SlackInsertion2OptSolver(Matrix<T> matrix) {
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

        // ── 3. Build orderings ────────────────────────────────────────────────
        List<List<T>> orderings = new ArrayList<>(3 + RANDOM_TRIES);

        orderings.add(SolverUtils.sortedCopy(urgent,
                Comparator.comparingDouble(City::getDeadline)));
        orderings.add(SolverUtils.sortedCopy(urgent,
                Comparator.comparingDouble(
                        u -> SolverUtils.slackFromStart(matrix, startCity, u))));
        orderings.add(SolverUtils.sortedCopy(urgent,
                Comparator.comparingDouble(
                        u -> SolverUtils.safeTime(matrix, startCity, u))));

        Random rng = new Random();
        for (int i = 0; i < RANDOM_TRIES; i++) {
            List<T> shuffled = new ArrayList<>(urgent);
            Collections.shuffle(shuffled, rng);
            orderings.add(shuffled);
        }

        // ── 4. Multi-start search ─────────────────────────────────────────────
        Route<T> bestValid   = null;
        Route<T> bestInvalid = null;

        for (List<T> urgentOrder : orderings) {
            List<T> routeOrder = new ArrayList<>();
            routeOrder.add(startCity);
            routeOrder.addAll(urgentOrder);
            routeOrder.add(startCity);

            // Check that the urgent-cities-only sub-route is valid.
            if (!RouteEvaluator.evaluate(routeOrder, matrix).isValid()) {
                bestInvalid = SolverUtils.chooseBetterInvalid(
                        bestInvalid,
                        RouteEvaluator.evaluate(routeOrder, matrix));
                continue;
            }

            // Insert flexible cities and improve.
            SolverUtils.greedyInsertAll(routeOrder, matrix, new ArrayList<>(flexible));
            RouteImprover.improveClosedValid(routeOrder, matrix, IMPROVEMENT_PASSES);

            Route<T> result = RouteEvaluator.evaluate(routeOrder, matrix);
            if (result.isValid()) {
                if (bestValid == null
                        || result.getTotalDistance() < bestValid.getTotalDistance()) {
                    bestValid = result;
                }
            } else {
                bestInvalid = SolverUtils.chooseBetterInvalid(bestInvalid, result);
            }
        }

        // ── 5. Return result ──────────────────────────────────────────────────
        if (bestValid != null) {
            bestValid.setDebugLog(
                    "Valid route found (multi-start slack-insertion + 2-opt, "
                    + (3 + RANDOM_TRIES) + " orderings tried).");
            return bestValid;
        }
        if (bestInvalid != null) {
            bestInvalid.invalidate(
                    "No valid route found after " + (3 + RANDOM_TRIES)
                    + " orderings. Returning best partial result.");
            return bestInvalid;
        }
        return SolverUtils.fail(matrix, "Solver failed unexpectedly.");
    }
}
