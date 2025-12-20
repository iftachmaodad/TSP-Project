package tspSolution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SlackInsertion2OptSolver<T extends City> implements Solver<T> {
    // --- Properties ---
    private final Matrix<T> matrix;

    // --- Constructors ---
    public SlackInsertion2OptSolver(Matrix<T> matrix) {
        if (matrix == null) throw new IllegalArgumentException("Matrix cannot be null");
        this.matrix = matrix;
    }

    // --- Methods ---
    @Override
    public Route<T> solve(T startCity) {
        if (startCity == null) throw new IllegalArgumentException("Start city cannot be null");
        if (!matrix.getCities().contains(startCity)) return fail("Start city is not inside the matrix cities set.");

        if (!matrix.checkIntegrity()) {
            boolean ok = matrix.populateMatrix();
            if (!ok || !matrix.checkIntegrity()) return fail("Matrix could not be populated.");
        }

        List<T> all = matrix.getCities();
        List<T> urgent = new ArrayList<>();
        List<T> flexible = new ArrayList<>();

        for (T c : all) {
            if (c.equals(startCity)) continue;
            if (c.hasDeadline()) urgent.add(c);
            else flexible.add(c);
        }

        urgent.sort(Comparator.comparingDouble(c -> slackFromStart(startCity, c)));

        List<T> routeOrder = new ArrayList<>();
        routeOrder.add(startCity);
        routeOrder.addAll(urgent);
        routeOrder.add(startCity); // closed loop

        Route<T> urgentOnly = RouteEvaluator.evaluate(routeOrder, matrix);
        if (!urgentOnly.isValid()) {
            urgentOnly.setDebugLog("Urgent-only route already misses a deadline.");
            return urgentOnly;
        }

        List<T> remaining = new ArrayList<>(flexible);
        while (!remaining.isEmpty()) {
            Insertion<T> best = null;

            for (T candidate : remaining) {
                Insertion<T> ins = bestFeasibleInsertion(routeOrder, candidate);
                if (ins != null && (best == null || ins.deltaDistance() < best.deltaDistance())) best = ins;
            }

            if (best == null) break;

            routeOrder.add(best.index(), best.city());
            remaining.remove(best.city());
        }

        // SAFETY: only run 2-opt if it's a real closed loop
        if (routeOrder.size() >= 4 && routeOrder.get(0).equals(routeOrder.get(routeOrder.size() - 1))) {
            Segment2Opt.optimizeByUrgentSegments(routeOrder, matrix);
        }

        Route<T> finalRoute = RouteEvaluator.evaluate(routeOrder, matrix);
        if (!remaining.isEmpty()) {
            finalRoute.setDebugLog("Could not insert " + remaining.size() + " non-urgent city/cities without missing deadlines.");
        } else {
            finalRoute.setDebugLog(finalRoute.isValid() ? "Route complete." : "Route invalid after optimization.");
        }
        return finalRoute;
    }

    private double slackFromStart(T start, T city) {
        int i = matrix.getIndexOf(start);
        int j = matrix.getIndexOf(city);
        if (i < 0 || j < 0) return Double.POSITIVE_INFINITY;

        double travel = matrix.getTime(i, j);
        if (Double.isNaN(travel)) travel = Double.POSITIVE_INFINITY;
        return city.getDeadline() - travel;
    }

    private Route<T> fail(String msg) {
        List<T> cities = matrix.getCities();
        if (cities.isEmpty()) throw new IllegalStateException("Matrix has no cities.");
        T start = cities.get(0);
        Route<T> r = new Route<>(start);
        r.setDebugLog("SOLVER ERROR: " + msg);
        return r;
    }

    private Insertion<T> bestFeasibleInsertion(List<T> routeOrder, T city) {
        Insertion<T> best = null;

        // Don't insert at 0. Also don't insert after the last node (which is the closing start).
        for (int idx = 1; idx < routeOrder.size(); idx++) {
            // Extra safety: don't insert after last element; idx < size ensures that
            double delta = RouteEvaluator.deltaDistanceIfInsert(routeOrder, matrix, idx, city);
            if (Double.isNaN(delta)) continue;

            List<T> temp = new ArrayList<>(routeOrder);
            temp.add(idx, city);

            Route<T> evaluated = RouteEvaluator.evaluate(temp, matrix);
            if (!evaluated.isValid()) continue;

            if (best == null || delta < best.deltaDistance()) {
                best = new Insertion<>(idx, city, delta);
            }
        }
        return best;
    }
}
