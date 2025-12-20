package tspSolution;

import java.util.ArrayList;
import java.util.List;

public final class Segment2Opt {
    // --- Constructor ---
    private Segment2Opt() {}

    // --- Methods ---
    public static <T extends City> void optimizeByUrgentSegments(List<T> routeOrder, Matrix<T> matrix) {
        if (routeOrder == null || routeOrder.size() < 4) return;

        // SAFETY: must be closed loop [start, ..., start]
        if (!routeOrder.get(0).equals(routeOrder.get(routeOrder.size() - 1))) return;

        List<Integer> fixed = new ArrayList<>();
        fixed.add(0);

        for (int i = 1; i < routeOrder.size() - 1; i++) {
            if (routeOrder.get(i).hasDeadline()) fixed.add(i);
        }

        fixed.add(routeOrder.size() - 1);

        for (int f = 0; f < fixed.size() - 1; f++) {
            int segStart = fixed.get(f);
            int segEnd = fixed.get(f + 1);
            if (segEnd - segStart < 3) continue;

            twoOptInsideSegment(routeOrder, matrix, segStart, segEnd);
        }
    }

    private static <T extends City> void twoOptInsideSegment(List<T> routeOrder, Matrix<T> matrix, int segStart, int segEnd) {
        boolean improved = true;

        while (improved) {
            improved = false;

            for (int i = segStart + 1; i < segEnd - 1; i++) {
                for (int k = i + 1; k < segEnd; k++) {
                    // k MUST be < lastIndex because gainIf2Opt uses k+1
                    if (k + 1 >= routeOrder.size()) continue;

                    double gain = gainIf2Opt(routeOrder, matrix, i, k);
                    if (Double.isNaN(gain)) continue;

                    if (gain < -1e-9) {
                        reverse(routeOrder, i, k);

                        Route<T> test = RouteEvaluator.evaluate(routeOrder, matrix);
                        if (!test.isValid()) {
                            reverse(routeOrder, i, k);
                        } else {
                            improved = true;
                        }
                    }
                }
            }
        }
    }

    private static <T extends City> double gainIf2Opt(List<T> routeOrder, Matrix<T> matrix, int i, int k) {
        T a = routeOrder.get(i - 1);
        T b = routeOrder.get(i);
        T c = routeOrder.get(k);
        T d = routeOrder.get(k + 1);

        int ia = matrix.getIndexOf(a);
        int ib = matrix.getIndexOf(b);
        int ic = matrix.getIndexOf(c);
        int id = matrix.getIndexOf(d);

        double ab = matrix.getDistance(ia, ib);
        double cd = matrix.getDistance(ic, id);
        double ac = matrix.getDistance(ia, ic);
        double bd = matrix.getDistance(ib, id);

        if (Double.isNaN(ab) || Double.isNaN(cd) || Double.isNaN(ac) || Double.isNaN(bd)) return Double.NaN;
        return (ac + bd) - (ab + cd);
    }

    private static <T extends City> void reverse(List<T> routeOrder, int i, int k) {
        while (i < k) {
            T tmp = routeOrder.get(i);
            routeOrder.set(i, routeOrder.get(k));
            routeOrder.set(k, tmp);
            i++;
            k--;
        }
    }
}