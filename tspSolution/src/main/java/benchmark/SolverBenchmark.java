package benchmark;

import data.Matrix;
import domain.AirCity;
import model.Route;
import solver.BruteForceSolver;
import solver.NearestNeighborSolver;
import solver.SlackInsertion2OptSolver;
import solver.SlackInsertionSolver;
import solver.Solver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs all four solvers against all eight benchmark instances and prints a
 * formatted comparison table.
 *
 * Table columns:
 *   Solver       — algorithm name
 *   Instance     — instance name and city count
 *   Distance (m) — total route distance if valid, "INVALID" otherwise
 *   Time (ms)    — wall-clock solve time in milliseconds
 *   Valid        — YES / NO
 *   Gap vs opt   — percentage above brute-force optimal (where available)
 *
 * Gap is only computed when:
 *   - The instance has ≤ BruteForceSolver.MAX_CITIES cities (brute force ran).
 *   - Both the heuristic and the brute-force route are VALID.
 *   - Gap = (heuristic − optimal) / optimal × 100 %
 *
 * Run with:
 *   mvn exec:java -Dexec.mainClass="benchmark.SolverBenchmark"
 */
public final class SolverBenchmark {

    // ── Solver descriptors ──────────────────────────────────────────────────

    @FunctionalInterface
    private interface SolverFactory {
        Solver<AirCity> create(Matrix<AirCity> matrix);
    }

    private record SolverEntry(String name, SolverFactory factory) {}

    private static final List<SolverEntry> SOLVERS = List.of(
        new SolverEntry("BruteForce",      BruteForceSolver::new),
        new SolverEntry("2-Opt(multi)",    SlackInsertion2OptSolver::new),
        new SolverEntry("SlackInsert",     SlackInsertionSolver::new),
        new SolverEntry("NearestNeighbor", NearestNeighborSolver::new)
    );

    // ── One result cell ─────────────────────────────────────────────────────

    private record BenchResult(
            String  solverName,
            String  instanceName,
            int     cityCount,
            boolean valid,
            double  distanceM,
            long    timeMs,
            String  gap
    ) {}

    // ══════════════════════════════════════════════════════════
    // Entry point
    // ══════════════════════════════════════════════════════════

    public static void main(String[] args) {
        System.out.println("=== TSP Solver Benchmark ===\n");

        List<TestInstance<AirCity>> instances = TestInstanceLibrary.all();
        List<BenchResult> results = new ArrayList<>();

        for (TestInstance<AirCity> inst : instances) {
            // Compute brute-force optimal once per instance for gap calculation
            double optimalDist = computeOptimal(inst);

            for (SolverEntry entry : SOLVERS) {
                results.add(runOne(entry, inst, optimalDist));
            }
        }

        printTable(results);
    }

    // ══════════════════════════════════════════════════════════
    // Running one solver on one instance
    // ══════════════════════════════════════════════════════════

    private static BenchResult runOne(SolverEntry entry,
                                      TestInstance<AirCity> inst,
                                      double optimalDist) {
        // Fresh matrix per run — no shared state between solvers
        Matrix<AirCity> matrix = new Matrix<>(AirCity.class);
        for (AirCity c : inst.cities()) matrix.addCity(c);
        matrix.populateMatrix();

        Solver<AirCity> solver;
        try {
            solver = entry.factory().create(matrix);
        } catch (Exception e) {
            return error(entry.name(), inst, "FACTORY_ERR");
        }

        long t0 = System.nanoTime();
        Route<AirCity> route;
        try {
            route = solver.solve(inst.startCity());
        } catch (IllegalArgumentException e) {
            // BruteForceSolver refuses instances with n > MAX_CITIES
            long ms = nanoToMs(System.nanoTime() - t0);
            return new BenchResult(entry.name(), inst.name(), inst.size(),
                                   false, Double.NaN, ms,
                                   "N/A (n>" + BruteForceSolver.MAX_CITIES + ")");
        } catch (Exception e) {
            long ms = nanoToMs(System.nanoTime() - t0);
            return new BenchResult(entry.name(), inst.name(), inst.size(),
                                   false, Double.NaN, ms,
                                   "ERR:" + e.getClass().getSimpleName());
        }
        long ms = nanoToMs(System.nanoTime() - t0);

        double dist = route.isValid() ? route.getTotalDistance() : Double.NaN;
        String gap  = formatGap(dist, optimalDist, route.isValid());

        return new BenchResult(entry.name(), inst.name(), inst.size(),
                               route.isValid(), dist, ms, gap);
    }

    // ══════════════════════════════════════════════════════════
    // Brute-force optimal (for gap)
    // ══════════════════════════════════════════════════════════

    /**
     * Returns the optimal valid route distance found by BruteForceSolver,
     * or NaN if the instance is too large or infeasible.
     */
    private static double computeOptimal(TestInstance<AirCity> inst) {
        if (inst.size() > BruteForceSolver.MAX_CITIES) return Double.NaN;

        Matrix<AirCity> matrix = new Matrix<>(AirCity.class);
        for (AirCity c : inst.cities()) matrix.addCity(c);
        matrix.populateMatrix();

        try {
            Route<AirCity> r = new BruteForceSolver<>(matrix).solve(inst.startCity());
            return r.isValid() ? r.getTotalDistance() : Double.NaN;
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    // ══════════════════════════════════════════════════════════
    // Gap formatting
    // ══════════════════════════════════════════════════════════

    /**
     * Formats the optimality gap.
     *
     *   "  0.00%"   — heuristic matched optimal exactly (or IS brute force)
     *   "+X.XX%"    — heuristic is X% above optimal
     *   "N/A"       — no optimal available (instance too large or infeasible)
     *   "INVALID"   — this solver returned an invalid route
     */
    private static String formatGap(double heuristicDist, double optimalDist, boolean valid) {
        if (!valid)                      return "INVALID";
        if (Double.isNaN(optimalDist))   return "N/A";
        if (Double.isNaN(heuristicDist)) return "INVALID";

        double pct = (heuristicDist - optimalDist) / optimalDist * 100.0;
        if (pct < 0.005) return "  0.00%";
        return String.format("+%.2f%%", pct);
    }

    // ══════════════════════════════════════════════════════════
    // Table printing
    // ══════════════════════════════════════════════════════════

    private static void printTable(List<BenchResult> results) {
        final int WS = 16;  // solver name
        final int WI = 22;  // instance name
        final int WD = 15;  // distance
        final int WT = 10;  // time
        final int WV = 7;   // valid
        final int WG = 14;  // gap

        String fmt    = "%-" + WS + "s | %-" + WI + "s | %-" + WD + "s | %-"
                      + WT + "s | %-" + WV + "s | %-" + WG + "s";
        String header = String.format(fmt,
                            "Solver", "Instance (n)", "Distance (m)",
                            "Time (ms)", "Valid", "Gap vs opt");
        String sep    = "─".repeat(header.length());

        System.out.println(sep);
        System.out.println(header);
        System.out.println(sep);

        // Group by instance so we can draw a separator between groups
        Map<String, List<BenchResult>> byInstance = new LinkedHashMap<>();
        for (BenchResult r : results) {
            byInstance.computeIfAbsent(r.instanceName(), k -> new ArrayList<>()).add(r);
        }

        boolean first = true;
        for (Map.Entry<String, List<BenchResult>> entry : byInstance.entrySet()) {
            if (!first) System.out.println(sep);
            first = false;

            for (BenchResult r : entry.getValue()) {
                String label   = r.instanceName() + " (n=" + r.cityCount() + ")";
                String distStr = r.valid()
                    ? String.format("%,.0f m", r.distanceM())
                    : "INVALID";
                String timeStr = r.timeMs() >= 0 ? r.timeMs() + " ms" : "—";

                System.out.println(String.format(fmt,
                    r.solverName(), label, distStr, timeStr,
                    r.valid() ? "YES" : "NO", r.gap()));
            }
        }

        System.out.println(sep);
        System.out.println();
        System.out.println("Gap: % above brute-force optimal."
            + "  N/A = brute force not run (n > " + BruteForceSolver.MAX_CITIES + ")"
            + " or infeasible.  INVALID = no valid route found.");
    }

    // ══════════════════════════════════════════════════════════
    // Small helpers
    // ══════════════════════════════════════════════════════════

    private static long nanoToMs(long nanos) { return nanos / 1_000_000; }

    private static BenchResult error(String solver, TestInstance<AirCity> inst, String gap) {
        return new BenchResult(solver, inst.name(), inst.size(), false, Double.NaN, -1, gap);
    }
}
