package solver;

import domain.City;
import model.Route;

public interface Solver<T extends City> {
    /**
     * Solves the TSP problem starting from the given city.
     * @param startCity The starting point (Depot).
     * @return A calculated Route.
     */
    Route<T> solve(T startCity);
}
