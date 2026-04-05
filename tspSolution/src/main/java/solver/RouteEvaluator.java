package solver;

import data.Matrix;
import domain.City;
import model.Route;

import java.util.List;

/**
 * Stateless utility for evaluating a city sequence against a distance matrix.
 *
 * <p>All methods are pure functions — they do not modify the matrix or the
 * route order list passed in.
 */
public final class RouteEvaluator {

    private RouteEvaluator() {}

    /**
     * Builds and evaluates a {@link Route} from an ordered city list.
     *
     * <p>Evaluation stops early as soon as the route becomes invalid (deadline
     * violation or a city not found in the matrix). The returned route always
     * reflects the metrics at the point of failure, not any phantom work done
     * after it.
     *
     * @param routeOrder ordered sequence of cities; must contain at least two
     *                   elements (start city and at least one destination)
     * @param matrix     pre-populated distance/time matrix
     * @return evaluated route; check {@link Route#isValid()}
     * @throws IllegalArgumentException if {@code routeOrder} is {@code null}
     *                                  or has fewer than two elements
     */
    public static <T extends City> Route<T> evaluate(
            List<T> routeOrder, Matrix<T> matrix) {

        if (routeOrder == null || routeOrder.size() < 2) {
            throw new IllegalArgumentException(
                    "Route order must contain at least 2 cities.");
        }

        Route<T> route = new Route<>(routeOrder.get(0));

        for (int k = 1; k < routeOrder.size(); k++) {
            T prev = routeOrder.get(k - 1);
            T next = routeOrder.get(k);

            int i = matrix.getIndexOf(prev);
            int j = matrix.getIndexOf(next);

            // getDistance/-Time return NaN for bad indices; Route.addStep
            // will detect this and invalidate the route with a clear message.
            double dist = matrix.getDistance(i, j);
            double time = matrix.getTime(i, j);

            route.addStep(next, dist, time);

            // Stop processing as soon as the route is invalid.
            if (!route.isValid()) return route;
        }

        return route;
    }

    /**
     * Computes the change in total route distance if {@code city} were
     * inserted at position {@code insertIndex}.
     *
     * <p>Insertion between positions {@code insertIndex−1} and
     * {@code insertIndex} replaces edge {@code (a→b)} with edges
     * {@code (a→city)} and {@code (city→b)}.
     *
     * @param routeOrder current route list (unmodified)
     * @param matrix     pre-populated distance/time matrix
     * @param insertIndex position before which to insert (must be in [1, size−1])
     * @param city       city to insert
     * @return additional distance in metres, or {@link Double#NaN} if the
     *         insertion is invalid (index out of bounds or city not in matrix)
     */
    public static <T extends City> double deltaDistanceIfInsert(
            List<T> routeOrder, Matrix<T> matrix, int insertIndex, T city) {

        if (insertIndex <= 0 || insertIndex >= routeOrder.size()) return Double.NaN;

        T a = routeOrder.get(insertIndex - 1);
        T b = routeOrder.get(insertIndex);

        int ia = matrix.getIndexOf(a);
        int ib = matrix.getIndexOf(b);
        int ic = matrix.getIndexOf(city);

        double oldEdge  = matrix.getDistance(ia, ib);
        double newEdge1 = matrix.getDistance(ia, ic);
        double newEdge2 = matrix.getDistance(ic, ib);

        if (!Double.isFinite(oldEdge) || !Double.isFinite(newEdge1)
                || !Double.isFinite(newEdge2)) {
            return Double.NaN;
        }

        return (newEdge1 + newEdge2) - oldEdge;
    }
}
