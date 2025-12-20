package tspSolution;

import java.util.List;

/**
 * Central place to evaluate a route order using the matrix:
 * - Computes total distance/time
 * - Checks deadlines exactly by arrival time
 *
 * Assumes routeOrder is an explicit order including the closing start city at the end.
 */
public final class RouteEvaluator {
    // --- Constructor ---
    private RouteEvaluator() {}

    // --- Methods ---
    public static <T extends City> Route<T> evaluate(List<T> routeOrder, Matrix<T> matrix) {
        if (routeOrder == null || routeOrder.size() < 2) {
            throw new IllegalArgumentException("Route order must contain at least 2 cities (start and end).");
        }

        Route<T> route = new Route<>(routeOrder.get(0));

        for (int k = 1; k < routeOrder.size(); k++) {
            T prev = routeOrder.get(k - 1);
            T next = routeOrder.get(k);

            int i = matrix.getIndexOf(prev);
            int j = matrix.getIndexOf(next);

            double dist = matrix.getDistance(i, j);
            double time = matrix.getTime(i, j);

            if (Double.isNaN(dist) || Double.isNaN(time)) {
                route.setDebugLog("Matrix has N/A edge: " + prev.getID() + " -> " + next.getID());
                // still add step but will likely become invalid / meaningless
                route.addStep(next, Double.NaN, Double.NaN);
                return route;
            }

            route.addStep(next, dist, time);
        }

        return route;
    }

    public static <T extends City> double deltaDistanceIfInsert(List<T> routeOrder, Matrix<T> matrix, int insertIndex, T city) {
        if (insertIndex <= 0 || insertIndex >= routeOrder.size()) return Double.NaN;

        T a = routeOrder.get(insertIndex - 1);
        T b = routeOrder.get(insertIndex);

        int ia = matrix.getIndexOf(a);
        int ib = matrix.getIndexOf(b);
        int ic = matrix.getIndexOf(city);

        double oldEdge = matrix.getDistance(ia, ib);
        double newEdge1 = matrix.getDistance(ia, ic);
        double newEdge2 = matrix.getDistance(ic, ib);

        if (Double.isNaN(oldEdge) || Double.isNaN(newEdge1) || Double.isNaN(newEdge2)) return Double.NaN;

        return (newEdge1 + newEdge2) - oldEdge;
    }
}
