package solver;

import data.Matrix;
import domain.City;
import model.Route;

import java.util.ArrayList;
import java.util.List;

/**
 * Nearest-neighbour greedy heuristic — O(n²) baseline solver.
 *
 * <h3>Algorithm</h3>
 * Starting from the depot, repeatedly travel to the nearest unvisited city
 * by distance, without any deadline-aware ordering. All cities are visited
 * regardless of deadlines; {@link model.Route#addStep} detects any deadline
 * violation and marks the route INVALID at that point, preserving the full
 * path for diagnostic display.
 *
 * <h3>Properties</h3>
 * <ul>
 *   <li>Deterministic — no randomness.</li>
 *   <li>O(n²) time, O(n) space.</li>
 *   <li>Does not guarantee optimality; typically 20–25 % above optimal on
 *       random Euclidean instances without deadlines.</li>
 *   <li>Has no deadline-aware ordering strategy — selects the nearest city
 *       regardless of urgency. On instances where deadline ordering matters,
 *       it frequently produces INVALID routes even when a valid ordering
 *       exists. Use {@link SlackInsertion2OptSolver} or
 *       {@link SlackInsertionSolver} for deadline-constrained instances.</li>
 * </ul>
 *
 * @param <T> the concrete city subtype
 */
public final class NearestNeighborSolver<T extends City> implements Solver<T> {

    private final Matrix<T> matrix;

    /**
     * @param matrix pre-populated distance/time matrix
     * @throws IllegalArgumentException if {@code matrix} is {@code null}
     */
    public NearestNeighborSolver(Matrix<T> matrix) {
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

        // ── Infeasibility fast-fail ───────────────────────────────────────────
        List<T> urgent = new ArrayList<>();
        for (T c : matrix.getCities()) {
            if (!c.equals(startCity) && c.hasDeadline()) urgent.add(c);
        }
        T infeasible = SolverUtils.findInfeasibleUrgent(matrix, startCity, urgent);
        if (infeasible != null) {
            Route<T> r = new Route<>(startCity);
            r.invalidate("Infeasible: direct travel to '"
                    + infeasible.getID() + "' already exceeds its deadline.");
            return r;
        }

        // ── Greedy nearest-neighbour ──────────────────────────────────────────
        List<T> unvisited = new ArrayList<>(matrix.getCities());
        unvisited.remove(startCity);

        List<T> routeOrder = new ArrayList<>();
        routeOrder.add(startCity);

        T current = startCity;

        while (!unvisited.isEmpty()) {
            T      nearest     = null;
            double nearestDist = Double.POSITIVE_INFINITY;

            for (T candidate : unvisited) {
                double dist = SolverUtils.safeDistance(matrix, current, candidate);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest     = candidate;
                }
            }

            routeOrder.add(nearest);
            unvisited.remove(nearest);
            current = nearest;
        }

        routeOrder.add(startCity); // close the tour

        Route<T> result = RouteEvaluator.evaluate(routeOrder, matrix);
        result.setDebugLog(result.isValid()
                ? "Valid route completed (nearest-neighbour)."
                : "Invalid route — deadline(s) missed (nearest-neighbour).");
        return result;
    }
}
