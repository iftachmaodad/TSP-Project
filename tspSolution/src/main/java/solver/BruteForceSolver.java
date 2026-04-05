package solver;

import data.Matrix;
import domain.City;
import model.Route;

import java.util.ArrayList;
import java.util.List;

/**
 * Exact brute-force TSP solver — guaranteed optimal for small instances.
 *
 * <h3>Algorithm</h3>
 * Enumerates every permutation of the non-start cities using Heap's algorithm
 * (in-place, O(n!) permutations, O(1) extra space per swap). Each permutation
 * forms a closed route {@code [start, perm…, start]} that is evaluated against
 * the matrix. The shortest valid route is returned.
 *
 * <h3>Complexity</h3>
 * O(n! × n) time, O(n) space. Practical only for n ≤ 10 non-start cities
 * (10! = 3 628 800 permutations).
 *
 * <h3>Size limit</h3>
 * Throws {@link IllegalArgumentException} from {@link #solve} if the matrix
 * has more than {@link #MAX_CITIES} cities (including the start), so it can
 * never accidentally run on a large instance.
 *
 * <h3>Deadline handling</h3>
 * {@link RouteEvaluator#evaluate} stops as soon as a deadline is missed,
 * pruning further evaluation of that permutation.
 *
 * <h3>Infeasibility fast-fail</h3>
 * Before enumeration, any city whose <em>direct</em> travel time from the
 * start already exceeds its deadline is reported immediately. This is a
 * necessary condition — such a city cannot appear in any valid route.
 *
 * @param <T> the concrete city subtype
 */
public final class BruteForceSolver<T extends City> implements Solver<T> {

    /** Maximum matrix size (including start city) before {@link #solve} refuses. */
    public static final int MAX_CITIES = 11; // start + 10 others

    private final Matrix<T> matrix;

    /**
     * @param matrix pre-populated distance/time matrix
     * @throws IllegalArgumentException if {@code matrix} is {@code null}
     */
    public BruteForceSolver(Matrix<T> matrix) {
        if (matrix == null) throw new IllegalArgumentException("Matrix cannot be null.");
        this.matrix = matrix;
    }

    // ── Solver ────────────────────────────────────────────────────────────────

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException if {@code startCity} is {@code null} or
     *         if the matrix has more than {@link #MAX_CITIES} cities
     */
    @Override
    public Route<T> solve(T startCity) {
        if (startCity == null) {
            throw new IllegalArgumentException("Start city cannot be null.");
        }
        if (!matrix.getCities().contains(startCity)) {
            return SolverUtils.fail(matrix,
                    "Start city '" + startCity.getID() + "' is not in the matrix.");
        }
        if (matrix.size() > MAX_CITIES) {
            throw new IllegalArgumentException(
                    "BruteForceSolver supports at most " + (MAX_CITIES - 1)
                    + " non-start cities (" + MAX_CITIES + " total). "
                    + "This instance has " + matrix.size() + " cities.");
        }
        if (!matrix.checkIntegrity()) {
            if (!matrix.populateMatrix() || !matrix.checkIntegrity()) {
                return SolverUtils.fail(matrix, "Matrix could not be populated.");
            }
        }

        // ── Partition non-start cities ────────────────────────────────────────
        List<T> others = new ArrayList<>(matrix.getCities());
        others.remove(startCity);

        // ── Infeasibility fast-fail ───────────────────────────────────────────
        List<T> urgent = new ArrayList<>();
        for (T c : others) {
            if (c.hasDeadline()) urgent.add(c);
        }
        T directlyInfeasible =
                SolverUtils.findInfeasibleUrgent(matrix, startCity, urgent);
        if (directlyInfeasible != null) {
            Route<T> r = new Route<>(startCity);
            r.invalidate("Infeasible: direct travel to '"
                    + directlyInfeasible.getID()
                    + "' already exceeds its deadline.");
            return r;
        }

        // ── Heap's algorithm ──────────────────────────────────────────────────
        Route<T> bestValid   = null;
        Route<T> bestInvalid = null;

        List<T> perm = new ArrayList<>(others);
        int     n    = perm.size();
        int[]   c    = new int[n]; // Heap's control array; initialised to 0

        // Evaluate the initial permutation.
        Route<T> r = evaluateClosed(startCity, perm);
        if (r.isValid()) bestValid = r;
        else             bestInvalid = SolverUtils.chooseBetterInvalid(bestInvalid, r);

        for (int i = 0; i < n; ) {
            if (c[i] < i) {
                swap(perm, (i % 2 == 0) ? 0 : c[i], i);

                r = evaluateClosed(startCity, perm);
                if (r.isValid()) {
                    if (bestValid == null
                            || r.getTotalDistance() < bestValid.getTotalDistance()) {
                        bestValid = r;
                    }
                } else {
                    bestInvalid = SolverUtils.chooseBetterInvalid(bestInvalid, r);
                }

                c[i]++;
                i = 0;
            } else {
                c[i] = 0;
                i++;
            }
        }

        // ── Return result ─────────────────────────────────────────────────────
        if (bestValid != null) {
            bestValid.setDebugLog("Optimal route found by brute force ("
                    + factorial(others.size()) + " permutations evaluated).");
            return bestValid;
        }
        if (bestInvalid != null) {
            bestInvalid.invalidate(
                    "No valid route exists (confirmed by full enumeration).");
            return bestInvalid;
        }
        return SolverUtils.fail(matrix, "Brute force failed unexpectedly.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Route<T> evaluateClosed(T start, List<T> perm) {
        List<T> order = new ArrayList<>(perm.size() + 2);
        order.add(start);
        order.addAll(perm);
        order.add(start);
        return RouteEvaluator.evaluate(order, matrix);
    }

    private static <T> void swap(List<T> list, int i, int j) {
        T tmp = list.get(i);
        list.set(i, list.get(j));
        list.set(j, tmp);
    }

    /**
     * Returns n! as a long. Safe for n in [0, 20]; overflows for n ≥ 21
     * (well beyond {@link #MAX_CITIES}).
     */
    static long factorial(int n) {
        if (n < 0) throw new IllegalArgumentException("n must be ≥ 0, got " + n);
        long result = 1;
        for (int i = 2; i <= n; i++) result *= i;
        return result;
    }
}
