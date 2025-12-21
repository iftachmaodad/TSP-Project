package tspSolution;

import java.util.ArrayList;
import java.util.List;

public final class RouteImprover {
    private RouteImprover() {}

    public static <T extends City> void improveClosedValid(List<T> routeOrder, Matrix<T> matrix, int maxPasses) {
        if (routeOrder == null || routeOrder.size() < 4) return;
        if (!routeOrder.get(0).equals(routeOrder.get(routeOrder.size() - 1))) return;

        for (int pass = 0; pass < maxPasses; pass++) {
            boolean improved = false;

            if (relocatePass(routeOrder, matrix)) improved = true;
            if (twoOptPass(routeOrder, matrix)) improved = true;

            if (!improved) return;
        }
    }

    private static <T extends City> boolean relocatePass(List<T> routeOrder, Matrix<T> matrix) {
        Route<T> base = RouteEvaluator.evaluate(routeOrder, matrix);
        if (!base.isValid()) return false;

        double bestDist = base.getTotalDistance();
        int n = routeOrder.size();

        // Candidate "from" positions: 1..n-2 (exclude start endpoints)
        for (int from = 1; from <= n - 2; from++) {
            T node = routeOrder.get(from);
            if (node.equals(routeOrder.get(0))) continue;

            // Remove it
            List<T> removed = new ArrayList<>(routeOrder);
            removed.remove(from);

            // Insert positions: 1..removed.size()-1 (before closing start)
            for (int to = 1; to <= removed.size() - 1; to++) {
                // Approximate no-op skip
                if (to == from) continue;

                List<T> trial = new ArrayList<>(removed);
                trial.add(to, node);

                Route<T> r = RouteEvaluator.evaluate(trial, matrix);
                if (!r.isValid()) continue;

                double d = r.getTotalDistance();
                if (!Double.isNaN(d) && d + 1e-6 < bestDist) {
                    routeOrder.clear();
                    routeOrder.addAll(trial);
                    return true; // first improvement
                }
            }
        }

        return false;
    }

    private static <T extends City> boolean twoOptPass(List<T> routeOrder, Matrix<T> matrix) {
        Route<T> base = RouteEvaluator.evaluate(routeOrder, matrix);
        if (!base.isValid()) return false;

        double bestDist = base.getTotalDistance();
        int n = routeOrder.size();

        // i: 1..n-3, k: i+1..n-2
        for (int i = 1; i <= n - 3; i++) {
            for (int k = i + 1; k <= n - 2; k++) {
                List<T> trial = new ArrayList<>(routeOrder);
                reverse(trial, i, k);

                Route<T> r = RouteEvaluator.evaluate(trial, matrix);
                if (!r.isValid()) continue;

                double d = r.getTotalDistance();
                if (!Double.isNaN(d) && d + 1e-6 < bestDist) {
                    routeOrder.clear();
                    routeOrder.addAll(trial);
                    return true; // first improvement
                }
            }
        }

        return false;
    }

    private static <T> void reverse(List<T> list, int i, int k) {
        while (i < k) {
            T tmp = list.get(i);
            list.set(i, list.get(k));
            list.set(k, tmp);
            i++;
            k--;
        }
    }
}
