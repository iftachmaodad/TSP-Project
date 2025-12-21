package tspSolution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class SlackInsertionSolver<T extends City> implements Solver<T> {

    private final Matrix<T> matrix;

    public SlackInsertionSolver(Matrix<T> matrix) {
        if (matrix == null) throw new IllegalArgumentException("Matrix cannot be null");
        this.matrix = matrix;
    }

    @Override
    public Route<T> solve(T startCity) {
        if (startCity == null) throw new IllegalArgumentException("Start city cannot be null");
        if (!matrix.getCities().contains(startCity)) return fail("Start city is not inside the matrix cities set.");

        if (!matrix.checkIntegrity()) {
            boolean ok = matrix.populateMatrix();
            if (!ok || !matrix.checkIntegrity()) return fail("Matrix could not be populated.");
        }

        List<T> urgent = new ArrayList<>();
        List<T> flexible = new ArrayList<>();

        for (T c : matrix.getCities()) {
            if (c.equals(startCity)) continue;
            if (c.hasDeadline()) urgent.add(c);
            else flexible.add(c);
        }

        // If direct travel from start -> urgent city already misses deadline, instance is infeasible.
        for (T u : urgent) {
            int i = matrix.getIndexOf(startCity);
            int j = matrix.getIndexOf(u);
            double direct = matrix.getTime(i, j);

            if (!Double.isNaN(direct) && direct > u.getDeadline()) {
                Route<T> r = new Route<>(startCity);
                r.invalidate("No valid route: direct travel to urgent city " + u.getID() + " already misses its deadline.");
                return r;
            }
        }

        urgent.sort(Comparator.comparingDouble(u -> slackFromStart(startCity, u)));

        // Closed route: [start, ..., start]
        List<T> routeOrder = new ArrayList<>();
        routeOrder.add(startCity);
        routeOrder.add(startCity);

        // Insert urgent cities first
        for (T u : urgent) {
            routeOrder.add(routeOrder.size() - 1, u);

            Route<T> test = RouteEvaluator.evaluate(routeOrder, matrix);
            if (!test.isValid()) {
                test.setDebugLog("Missed a deadline while inserting urgent city: " + u.getID());
                return test;
            }
        }

        // Insert flexible cities greedily
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

        RouteImprover.improveClosedValid(routeOrder, matrix, 2);

        Route<T> result = RouteEvaluator.evaluate(routeOrder, matrix);
        if (result.isValid()) {
            if (!remaining.isEmpty()) {
                result.setDebugLog("Valid route found (regular), but could not insert " + remaining.size() + " flexible city/cities without breaking deadlines.");
            } else {
                result.setDebugLog("Valid route completed successfully (regular).");
            }
        } else {
            if (!remaining.isEmpty()) {
                result.setDebugLog("No valid route found that includes all cities. Remaining flexible cities: " + remaining.size());
            } else {
                result.setDebugLog("Route ended up invalid (regular).");
            }
        }

        return result;
    }

    private double slackFromStart(T start, T city) {
        int i = matrix.getIndexOf(start);
        int j = matrix.getIndexOf(city);
        if (i < 0 || j < 0) return Double.POSITIVE_INFINITY;

        double travel = matrix.getTime(i, j);
        if (Double.isNaN(travel)) travel = Double.POSITIVE_INFINITY;
        return city.getDeadline() - travel;
    }

    private Insertion<T> bestFeasibleInsertion(List<T> routeOrder, T city) {
        Insertion<T> best = null;

        for (int idx = 1; idx < routeOrder.size(); idx++) {
            double delta = RouteEvaluator.deltaDistanceIfInsert(routeOrder, matrix, idx, city);
            if (Double.isNaN(delta)) continue;

            List<T> temp = new ArrayList<>(routeOrder);
            temp.add(idx, city);

            Route<T> evaluated = RouteEvaluator.evaluate(temp, matrix);
            if (!evaluated.isValid()) continue;

            if (best == null || delta < best.deltaDistance()) best = new Insertion<>(idx, city, delta);
        }

        return best;
    }

    private Route<T> fail(String msg) {
        List<T> cities = matrix.getCities();
        if (cities.isEmpty()) throw new IllegalStateException("Matrix has no cities.");
        T start = cities.get(0);
        Route<T> r = new Route<>(start);
        r.invalidate("SOLVER ERROR: " + msg);
        return r;
    }
}
