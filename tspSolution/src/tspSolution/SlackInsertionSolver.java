package tspSolution;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class SlackInsertionSolver<T extends City> implements Solver<T> {
    // --- Properties ---
    private final Matrix<T> matrix;

    // --- Constructors ---
    public SlackInsertionSolver(Matrix<T> matrix) {
        if (matrix == null) throw new IllegalArgumentException("Matrix cannot be null");
        this.matrix = matrix;
    }

    // --- Solver Logic ---
    @Override
    public Route<T> solve(T startCity) {
        if (startCity == null) throw new IllegalArgumentException("Start city cannot be null");
        if (!matrix.getCities().contains(startCity)) return fail("Start city is not inside the matrix cities set.");

        if (!matrix.checkIntegrity()) {
            boolean ok = matrix.populateMatrix();
            if (!ok || !matrix.checkIntegrity()) return fail("Matrix could not be populated.");
        }

        List<T> all = new ArrayList<>(matrix.getCities());
        all.remove(startCity);

        // Separate urgent / non-urgent
        List<T> urgent = all.stream()
                .filter(City::hasDeadline)
                .sorted(Comparator.comparingDouble(c -> slackFromStart(startCity, c)))
                .toList();

        List<T> nonUrgent = all.stream()
                .filter(c -> !c.hasDeadline())
                .toList();

        // Build route order list (we keep an explicit order list like the 2-opt solver)
        List<T> routeOrder = new ArrayList<>();
        routeOrder.add(startCity);

        Set<T> visited = new HashSet<>();
        visited.add(startCity);

        // --- Visit urgent cities in chosen order ---
        for (T u : urgent) {
            if (visited.contains(u)) continue;

            routeOrder.add(u);
            Route<T> test = RouteEvaluator.evaluate(routeOrder, matrix);
            if (!test.isValid()) {
                test.setDebugLog("Missed deadline while going to urgent city: " + u.getID());
                return test;
            }

            visited.add(u);

            // After reaching this urgent city, try to insert some non-urgent cities (safely)
            insertNonUrgentSafely(routeOrder, nonUrgent, visited);
        }

        // --- Add remaining non-urgent cities (safely) ---
        insertNonUrgentSafely(routeOrder, nonUrgent, visited);

        // --- Return to start (always closed loop) ---
        routeOrder.add(startCity);

        return RouteEvaluator.evaluate(routeOrder, matrix);
    }

    // --- Helpers ---
    private double slackFromStart(T start, T city) {
        int i = matrix.getIndexOf(start);
        int j = matrix.getIndexOf(city);
        double travel = matrix.getTime(i, j);
        if (Double.isNaN(travel)) travel = Double.POSITIVE_INFINITY;
        return city.getDeadline() - travel;
    }

    private void insertNonUrgentSafely(List<T> routeOrder, List<T> nonUrgent, Set<T> visited) {
        boolean inserted;

        do {
            inserted = false;
            Insertion<T> best = null;

            // Try to insert ANY remaining non-urgent city at the best place (min delta distance)
            for (T candidate : nonUrgent) {
                if (visited.contains(candidate)) continue;

                Insertion<T> ins = bestFeasibleInsertion(routeOrder, candidate);
                if (ins != null && (best == null || ins.deltaDistance() < best.deltaDistance())) {
                    best = ins;
                }
            }

            if (best != null) {
                routeOrder.add(best.index(), best.city());
                visited.add(best.city());
                inserted = true;
            }

        } while (inserted);
    }

    private Insertion<T> bestFeasibleInsertion(List<T> routeOrder, T city) {
        Insertion<T> best = null;

        // We insert at positions 1..routeOrder.size() (end insertion allowed)
        // (But not before index 0 because start must stay first)
        for (int idx = 1; idx <= routeOrder.size(); idx++) {
            double delta = deltaDistanceIfInsertOpen(routeOrder, idx, city);
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

    // Open-route insert delta:
    // - if inserting in the middle: replace edge A->B with A->C + C->B
    // - if inserting at end: add edge last->C
    private double deltaDistanceIfInsertOpen(List<T> routeOrder, int insertIndex, T city) {
        if (insertIndex < 1 || insertIndex > routeOrder.size()) return Double.NaN;

        int ic = matrix.getIndexOf(city);
        if (ic < 0) return Double.NaN;

        // inserting at end
        if (insertIndex == routeOrder.size()) {
            T last = routeOrder.get(routeOrder.size() - 1);
            int il = matrix.getIndexOf(last);
            double add = matrix.getDistance(il, ic);
            return Double.isNaN(add) ? Double.NaN : add;
        }

        // inserting in middle
        T a = routeOrder.get(insertIndex - 1);
        T b = routeOrder.get(insertIndex);

        int ia = matrix.getIndexOf(a);
        int ib = matrix.getIndexOf(b);

        double oldEdge = matrix.getDistance(ia, ib);
        double new1 = matrix.getDistance(ia, ic);
        double new2 = matrix.getDistance(ic, ib);

        if (Double.isNaN(oldEdge) || Double.isNaN(new1) || Double.isNaN(new2)) return Double.NaN;
        return (new1 + new2) - oldEdge;
    }

    private Route<T> fail(String msg) {
        List<T> cities = matrix.getCities();
        if (cities.isEmpty()) throw new IllegalStateException("Matrix has no cities.");
        T start = cities.get(0);
        Route<T> r = new Route<>(start);
        r.setDebugLog("SOLVER ERROR: " + msg);
        return r;
    }
}
