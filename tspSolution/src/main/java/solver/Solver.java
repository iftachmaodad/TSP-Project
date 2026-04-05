package solver;

import domain.City;
import model.Route;

/**
 * Contract for TSP solver implementations.
 *
 * <p>A solver takes a pre-populated {@link data.Matrix} and a start city, then
 * returns a {@link Route} visiting all cities in the matrix and closing back at
 * the start. The route is always non-null; callers must check
 * {@link Route#isValid()} to determine whether a feasible solution was found.
 *
 * <p>Implementations must document:
 * <ul>
 *   <li>Time complexity</li>
 *   <li>Optimality guarantee (exact vs. heuristic)</li>
 *   <li>Deadline handling strategy</li>
 * </ul>
 *
 * @param <T> the concrete city subtype
 */
public interface Solver<T extends City> {

    /**
     * Solves the TSP for all cities in the matrix, departing from and
     * returning to {@code startCity}.
     *
     * @param startCity the depot city; must be present in the matrix
     * @return a non-null route; check {@link Route#isValid()} for feasibility
     * @throws IllegalArgumentException if {@code startCity} is {@code null}
     */
    Route<T> solve(T startCity);
}
