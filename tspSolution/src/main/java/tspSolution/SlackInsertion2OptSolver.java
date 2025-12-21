package tspSolution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

public final class SlackInsertion2OptSolver<T extends City> implements Solver<T> {

    private final Matrix<T> matrix;
    private static final int RANDOM_TRIES = 25;

    public SlackInsertion2OptSolver(Matrix<T> matrix) {
        if (matrix == null) throw new IllegalArgumentException("Matrix cannot be null");
        this.matrix = matrix;
    }

    @Override
    public Route<T> solve(T startCity) {
        if (startCity == null) throw new IllegalArgumentException("Start city cannot be null");
        if (!matrix.getCities().contains(startCity)) return fail("Start city is not inside matrix.");

        if (!matrix.checkIntegrity()) {
            if (!matrix.populateMatrix() || !matrix.checkIntegrity())
                return fail("Matrix could not be populated.");
        }

        List<T> urgent = new ArrayList<>();
        List<T> flexible = new ArrayList<>();

        for (T c : matrix.getCities()) {
            if (c.equals(startCity)) continue;
            if (c.hasDeadline()) urgent.add(c);
            else flexible.add(c);
        }

        // Infeasible instance check (must mark INVALID, not VALID).
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

        List<List<T>> urgentOrderings = new ArrayList<>();

        urgentOrderings.add(sortedCopy(urgent, Comparator.comparingDouble(City::getDeadline)));
        urgentOrderings.add(sortedCopy(urgent, Comparator.comparingDouble(u -> slackFromStart(startCity, u))));
        urgentOrderings.add(sortedCopy(urgent, Comparator.comparingDouble(u -> safeTime(startCity, u))));

        Random rnd = new Random();
        for (int i = 0; i < RANDOM_TRIES; i++) {
            List<T> shuf = new ArrayList<>(urgent);
            Collections.shuffle(shuf, rnd);
            urgentOrderings.add(shuf);
        }

        Route<T> bestValid = null;
        Route<T> bestInvalid = null;

        for (List<T> urgentOrder : urgentOrderings) {
            List<T> routeOrder = new ArrayList<>();
            routeOrder.add(startCity);
            routeOrder.addAll(urgentOrder);
            routeOrder.add(startCity);

            Route<T> urgentOnly = RouteEvaluator.evaluate(routeOrder, matrix);
            if (!urgentOnly.isValid()) {
                bestInvalid = chooseBetterInvalid(bestInvalid, urgentOnly);
                continue;
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

            RouteImprover.improveClosedValid(routeOrder, matrix, 8);

            Route<T> result = RouteEvaluator.evaluate(routeOrder, matrix);

            if (result.isValid()) {
                if (bestValid == null || result.getTotalDistance() < bestValid.getTotalDistance()) {
                    bestValid = result;
                }
            } else {
                bestInvalid = chooseBetterInvalid(bestInvalid, result);
            }
        }

        if (bestValid != null) {
            bestValid.setDebugLog("Valid route found successfully (optimized: relocate + global 2-opt).");
            return bestValid;
        }

        if (bestInvalid != null) {
            bestInvalid.invalidate("No valid route found. Returning best invalid route found by the heuristic.");
            return bestInvalid;
        }

        return fail("Solver failed unexpectedly.");
    }

    private double safeTime(T a, T b) {
        int i = matrix.getIndexOf(a);
        int j = matrix.getIndexOf(b);
        double t = matrix.getTime(i, j);
        return Double.isNaN(t) ? Double.POSITIVE_INFINITY : t;
    }

    private double slackFromStart(T start, T city) {
        double t = safeTime(start, city);
        return city.getDeadline() - t;
    }

    private Insertion<T> bestFeasibleInsertion(List<T> routeOrder, T city) {
        Insertion<T> best = null;

        for (int idx = 1; idx < routeOrder.size(); idx++) {
            double delta = RouteEvaluator.deltaDistanceIfInsert(routeOrder, matrix, idx, city);
            if (Double.isNaN(delta)) continue;

            List<T> tmp = new ArrayList<>(routeOrder);
            tmp.add(idx, city);

            Route<T> evaluated = RouteEvaluator.evaluate(tmp, matrix);
            if (!evaluated.isValid()) continue;

            if (best == null || delta < best.deltaDistance()) {
                best = new Insertion<>(idx, city, delta);
            }
        }

        return best;
    }

    private Route<T> chooseBetterInvalid(Route<T> a, Route<T> b) {
        if (a == null) return b;
        if (b == null) return a;
        return b.getTotalTime() < a.getTotalTime() ? b : a;
    }

    private List<T> sortedCopy(List<T> src, Comparator<T> cmp) {
        List<T> c = new ArrayList<>(src);
        c.sort(cmp);
        return c;
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
