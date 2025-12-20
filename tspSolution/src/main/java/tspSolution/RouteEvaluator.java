package tspSolution;

import java.util.List;

public final class RouteEvaluator {
    private RouteEvaluator() {}

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

            route.addStep(next, dist, time);

            // if it became invalid due to NaN or deadline, stop early
            if (!route.isValid()) return route;
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
