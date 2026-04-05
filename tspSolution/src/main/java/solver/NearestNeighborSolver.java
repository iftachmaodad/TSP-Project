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
 * whose deadline (if any) can still be met given the elapsed time. When no
 * feasible move remains the route closes back to the depot.
 *
 * <h3>Properties</h3>
 * <ul>
 *   <li>Deterministic — no randomness.</li>
 *   <li>O(n²) time, O(n) space.</li>
 *   <li>Does not guarantee optimality; typically 20–25 % above optimal on
 *       random Euclidean instances.</li>
 *   <li>May skip cities with tight deadlines when they are not nearest at the
 *       time of selection, but the returned route is always marked accordingly.</li>
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

        T      current = startCity;
        double elapsed = 0.0;

        while (!unvisited.isEmpty()) {
            T      nearest     = null;
            double nearestDist = Double.POSITIVE_INFINITY;

            for (T candidate : unvisited) {
                double arrivalTime =
                        elapsed + SolverUtils.safeTime(matrix, current, candidate);
                if (candidate.hasDeadline()
                        && arrivalTime > candidate.getDeadline()) {
                    continue; // would miss deadline — skip
                }
                double dist = SolverUtils.safeDistance(matrix, current, candidate);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest     = candidate;
                }
            }

            if (nearest == null) break; // no feasible neighbour — close early

            elapsed += SolverUtils.safeTime(matrix, current, nearest);
            routeOrder.add(nearest);
            unvisited.remove(nearest);
            current = nearest;
        }

        routeOrder.add(startCity); // close the tour

        Route<T> result = RouteEvaluator.evaluate(routeOrder, matrix);

        int skipped = unvisited.size();
        if (result.isValid()) {
            result.setDebugLog(skipped == 0
                    ? "Valid route completed (nearest-neighbour)."
                    : "Valid route found; " + skipped
                    + " city/cities skipped due to tight deadlines.");
        } else {
            result.setDebugLog("Invalid route (nearest-neighbour)."
                    + (skipped > 0 ? " " + skipped + " city/cities were skipped." : ""));
        }

        return result;
    }
}
