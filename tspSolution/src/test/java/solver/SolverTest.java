package solver;

import benchmark.TestInstanceLibrary;
import data.Matrix;
import domain.AirCity;
import model.Route;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration-level tests for all four solver implementations plus
 * {@link RouteEvaluator} and {@link RouteImprover}.
 *
 * <h3>Strategy</h3>
 * <ul>
 *   <li>Every solver is tested on the named instances from
 *       {@link TestInstanceLibrary}.</li>
 *   <li>{@link BruteForceSolver} acts as an oracle on small instances:
 *       heuristic solutions must not exceed its distance by more than the
 *       allowed tolerance.</li>
 *   <li>The infeasible instance must produce INVALID from every solver.</li>
 *   <li>{@link BruteForceSolver} must refuse the 20-city instance.</li>
 * </ul>
 */
class SolverTest {

    // ── Helpers ───────────────────────────────────────────────────────────────

    private Matrix<AirCity> matrix(List<AirCity> cities) {
        Matrix<AirCity> m = new Matrix<>(AirCity.class);
        for (AirCity c : cities) m.addCity(c);
        m.populateMatrix();
        return m;
    }

    private BruteForceSolver<AirCity>         brute(Matrix<AirCity> m) { return new BruteForceSolver<>(m); }
    private SlackInsertion2OptSolver<AirCity>  opt2 (Matrix<AirCity> m) { return new SlackInsertion2OptSolver<>(m); }
    private SlackInsertionSolver<AirCity>      slack(Matrix<AirCity> m) { return new SlackInsertionSolver<>(m); }
    private NearestNeighborSolver<AirCity>     nn   (Matrix<AirCity> m) { return new NearestNeighborSolver<>(m); }

    // ── Null / bad-argument guards ────────────────────────────────────────────

    @Test
    void all_solvers_throw_on_null_matrix() {
        assertThrows(IllegalArgumentException.class, () -> new BruteForceSolver<>(null));
        assertThrows(IllegalArgumentException.class, () -> new SlackInsertion2OptSolver<>(null));
        assertThrows(IllegalArgumentException.class, () -> new SlackInsertionSolver<>(null));
        assertThrows(IllegalArgumentException.class, () -> new NearestNeighborSolver<>(null));
    }

    @Test
    void all_solvers_throw_on_null_start_city() {
        var inst = TestInstanceLibrary.trivial();
        Matrix<AirCity> m = matrix(inst.cities());
        assertThrows(IllegalArgumentException.class, () -> brute(m).solve(null));
        assertThrows(IllegalArgumentException.class, () -> opt2(m).solve(null));
        assertThrows(IllegalArgumentException.class, () -> slack(m).solve(null));
        assertThrows(IllegalArgumentException.class, () -> nn(m).solve(null));
    }

    @Test
    void all_solvers_return_invalid_when_start_not_in_matrix() {
        var inst = TestInstanceLibrary.trivial();
        Matrix<AirCity> m = matrix(inst.cities());
        AirCity outsider = new AirCity("Outsider", 10.0, 10.0);
        assertFalse(brute(m).solve(outsider).isValid());
        assertFalse(opt2(m) .solve(outsider).isValid());
        assertFalse(slack(m).solve(outsider).isValid());
        assertFalse(nn(m)   .solve(outsider).isValid());
    }

    // ── trivial (2 cities) ────────────────────────────────────────────────────

    @Test
    void all_solvers_find_valid_route_on_trivial() {
        var inst = TestInstanceLibrary.trivial();
        Matrix<AirCity> m = matrix(inst.cities());
        AirCity s = inst.startCity();
        assertTrue(brute(m).solve(s).isValid());
        assertTrue(opt2(m) .solve(s).isValid());
        assertTrue(slack(m).solve(s).isValid());
        assertTrue(nn(m)   .solve(s).isValid());
    }

    @Test
    void trivial_route_has_three_cities_in_path() {
        var inst = TestInstanceLibrary.trivial();
        Route<AirCity> r = brute(matrix(inst.cities())).solve(inst.startCity());
        assertEquals(3, r.size(), "path = [start, other, start]");
    }

    // ── five_city ─────────────────────────────────────────────────────────────

    @Test
    void all_solvers_valid_on_five_city() {
        var inst = TestInstanceLibrary.fiveCity();
        Matrix<AirCity> m = matrix(inst.cities());
        AirCity s = inst.startCity();
        assertTrue(brute(m).solve(s).isValid());
        assertTrue(opt2(m) .solve(s).isValid());
        assertTrue(slack(m).solve(s).isValid());
        assertTrue(nn(m)   .solve(s).isValid());
    }

    @Test
    void five_city_routes_visit_all_cities() {
        var inst = TestInstanceLibrary.fiveCity();
        Route<AirCity> r = brute(matrix(inst.cities())).solve(inst.startCity());
        assertEquals(inst.size() + 1, r.size(),
                "Closed route path must have n+1 entries (start appears twice)");
    }

    // ── deadlines ─────────────────────────────────────────────────────────────

    @Test
    void deadline_aware_solvers_valid_on_deadlines_instance() {
        var inst = TestInstanceLibrary.deadlines();
        Matrix<AirCity> m = matrix(inst.cities());
        AirCity s = inst.startCity();
        assertTrue(brute(m).solve(s).isValid(), "brute");
        assertTrue(opt2(m) .solve(s).isValid(), "2-opt");
        assertTrue(slack(m).solve(s).isValid(), "slack");
    }

    // ── eight_city_deadlines ──────────────────────────────────────────────────

    /**
     * Deadline-aware solvers must find a valid route. Ordering matters here —
     * a naive solver visiting Amsterdam before London will miss London's deadline.
     * NearestNeighborSolver is not required to succeed because it has no
     * deadline-aware ordering strategy.
     */
    @Test
    void deadline_aware_solvers_valid_on_eight_city_deadlines() {
        var inst = TestInstanceLibrary.eightCityDeadlines();
        AirCity s = inst.startCity();
        assertTrue(brute(matrix(inst.cities())).solve(s).isValid(), "brute");
        assertTrue(opt2 (matrix(inst.cities())).solve(s).isValid(), "2-opt");
        assertTrue(slack(matrix(inst.cities())).solve(s).isValid(), "slack");
    }

    @Test
    void nearest_neighbour_invalid_on_eight_city_deadlines() {
        var inst = TestInstanceLibrary.eightCityDeadlines();
        Route<AirCity> r = nn(matrix(inst.cities())).solve(inst.startCity());
        assertFalse(r.isValid(),
                "NearestNeighbor must return INVALID on eight_city_deadlines "
                + "because it lacks deadline-aware ordering");
        assertTrue(r.size() > 1,
                "Invalid route must still contain visited cities for diagnostics");
    }

    @Test
    void eight_city_deadlines_brute_force_is_optimal() {
        var inst = TestInstanceLibrary.eightCityDeadlines();
        Route<AirCity> bruteRoute = brute(matrix(inst.cities())).solve(inst.startCity());
        Route<AirCity> opt2Route  = opt2 (matrix(inst.cities())).solve(inst.startCity());
        assertTrue(bruteRoute.isValid(), "brute force must find valid route");
        assertTrue(opt2Route.isValid(),  "2-opt must find valid route");
        assertTrue(bruteRoute.getTotalDistance() <= opt2Route.getTotalDistance() + 1e-3,
                "brute force must not exceed 2-opt on this instance");
    }

    // ── infeasible ────────────────────────────────────────────────────────────

    @Test
    void all_solvers_return_invalid_on_infeasible_instance() {
        var inst = TestInstanceLibrary.infeasible();
        Matrix<AirCity> m = matrix(inst.cities());
        AirCity s = inst.startCity();
        assertFalse(brute(m).solve(s).isValid(), "brute");
        assertFalse(opt2(m) .solve(s).isValid(), "2-opt");
        assertFalse(slack(m).solve(s).isValid(), "slack");
        assertFalse(nn(m)   .solve(s).isValid(), "nn");
    }

    // ── BruteForceSolver ──────────────────────────────────────────────────────

    @Test
    void brute_force_refuses_instance_larger_than_max() {
        var inst = TestInstanceLibrary.twentyCity();
        assertThrows(IllegalArgumentException.class,
                () -> brute(matrix(inst.cities())).solve(inst.startCity()));
    }

    @Test
    void brute_force_does_not_exceed_heuristics_on_five_city() {
        var inst = TestInstanceLibrary.fiveCity();
        double optimal    = brute(matrix(inst.cities())).solve(inst.startCity()).getTotalDistance();
        double heuristic  = opt2 (matrix(inst.cities())).solve(inst.startCity()).getTotalDistance();
        double fast       = slack(matrix(inst.cities())).solve(inst.startCity()).getTotalDistance();
        assertTrue(optimal <= heuristic + 1e-3, "BruteForce must not exceed opt2 on 5-city");
        assertTrue(optimal <= fast      + 1e-3, "BruteForce must not exceed slack on 5-city");
    }

    @Test
    void brute_force_factorial_correct() {
        assertEquals(1L,       BruteForceSolver.factorial(0));
        assertEquals(1L,       BruteForceSolver.factorial(1));
        assertEquals(2L,       BruteForceSolver.factorial(2));
        assertEquals(6L,       BruteForceSolver.factorial(3));
        assertEquals(24L,      BruteForceSolver.factorial(4));
        assertEquals(3_628_800L, BruteForceSolver.factorial(10));
    }

    @Test
    void brute_force_factorial_throws_for_negative() {
        assertThrows(IllegalArgumentException.class,
                () -> BruteForceSolver.factorial(-1));
    }

    // ── ten_city_deadlines ────────────────────────────────────────────────────

    @Test
    void deadline_aware_solvers_valid_on_ten_city_deadlines() {
        var inst = TestInstanceLibrary.tenCityDeadlines();
        AirCity s = inst.startCity();
        assertTrue(brute(matrix(inst.cities())).solve(s).isValid(), "brute");
        assertTrue(opt2 (matrix(inst.cities())).solve(s).isValid(), "2-opt");
        assertTrue(slack(matrix(inst.cities())).solve(s).isValid(), "slack");
    }

    @Test
    void nearest_neighbour_invalid_on_ten_city_deadlines() {
        var inst = TestInstanceLibrary.tenCityDeadlines();
        Route<AirCity> r = nn(matrix(inst.cities())).solve(inst.startCity());
        assertFalse(r.isValid(),
                "NearestNeighbor must return INVALID on ten_city_deadlines — "
                + "its greedy ordering visits Amsterdam before London, "
                + "missing London's deadline");
        assertTrue(r.size() > 1,
                "Invalid route must still contain visited cities for diagnostics");
    }

    // ── ten_city ──────────────────────────────────────────────────────────────

    @Test
    void ten_city_brute_force_valid() {
        var inst = TestInstanceLibrary.tenCity();
        assertTrue(brute(matrix(inst.cities())).solve(inst.startCity()).isValid());
    }

    @Test
    void ten_city_heuristics_within_20_percent_of_optimal() {
        var inst = TestInstanceLibrary.tenCity();
        double optimal   = brute (matrix(inst.cities())).solve(inst.startCity()).getTotalDistance();
        double twoOpt    = opt2  (matrix(inst.cities())).solve(inst.startCity()).getTotalDistance();
        double fastSlack = slack (matrix(inst.cities())).solve(inst.startCity()).getTotalDistance();
        double nearestN  = nn    (matrix(inst.cities())).solve(inst.startCity()).getTotalDistance();

        assertTrue(twoOpt    <= optimal * 1.20, "2-opt > 20% above optimal on ten_city");
        assertTrue(fastSlack <= optimal * 1.20, "slack > 20% above optimal on ten_city");
        assertTrue(nearestN  <= optimal * 1.35, "nn > 35% above optimal on ten_city");
    }

    // ── twenty_city ───────────────────────────────────────────────────────────

    @Test
    void heuristics_valid_on_twenty_city() {
        var inst = TestInstanceLibrary.twentyCity();
        AirCity s = inst.startCity();
        assertTrue(opt2 (matrix(inst.cities())).solve(s).isValid(), "2-opt");
        assertTrue(slack(matrix(inst.cities())).solve(s).isValid(), "slack");
        assertTrue(nn   (matrix(inst.cities())).solve(s).isValid(), "nn");
    }

    @Test
    void twenty_city_routes_close_back_to_start() {
        var inst = TestInstanceLibrary.twentyCity();
        Route<AirCity> r = opt2(matrix(inst.cities())).solve(inst.startCity());
        List<AirCity> path = r.getPath();
        assertEquals(inst.startCity(), path.get(0),               "must start at depot");
        assertEquals(inst.startCity(), path.get(path.size() - 1), "must close at depot");
    }

    // ── RouteEvaluator ────────────────────────────────────────────────────────

    @Test
    void route_evaluator_throws_for_single_city_list() {
        var inst = TestInstanceLibrary.trivial();
        Matrix<AirCity> m = matrix(inst.cities());
        assertThrows(IllegalArgumentException.class,
                () -> RouteEvaluator.evaluate(List.of(inst.startCity()), m));
    }

    @Test
    void route_evaluator_produces_valid_route_for_two_cities() {
        var inst = TestInstanceLibrary.trivial();
        Matrix<AirCity> m = matrix(inst.cities());
        List<AirCity> order = new ArrayList<>(inst.cities());
        order.add(inst.startCity()); // close the tour
        Route<AirCity> r = RouteEvaluator.evaluate(order, m);
        assertTrue(r.isValid());
    }

    @Test
    void delta_distance_is_nan_for_out_of_bounds_insert() {
        var inst = TestInstanceLibrary.trivial();
        Matrix<AirCity> m = matrix(inst.cities());
        List<AirCity> order = new ArrayList<>(List.of(inst.startCity(), inst.startCity()));
        double delta = RouteEvaluator.deltaDistanceIfInsert(order, m, 0, inst.startCity());
        assertTrue(Double.isNaN(delta),
                "insertIndex=0 is out of bounds and must return NaN");
    }

    @Test
    void delta_distance_finite_for_valid_insert() {
        var inst = TestInstanceLibrary.fiveCity();
        Matrix<AirCity> m = matrix(inst.cities());
        AirCity s = inst.startCity();
        AirCity other = inst.cities().stream()
                .filter(c -> !c.equals(s)).findFirst().orElseThrow();
        List<AirCity> order = new ArrayList<>(List.of(s, s));
        double delta = RouteEvaluator.deltaDistanceIfInsert(order, m, 1, other);
        assertTrue(Double.isFinite(delta));
    }

    // ── RouteImprover ─────────────────────────────────────────────────────────

    @Test
    void route_improver_does_not_worsen_valid_route() {
        var inst = TestInstanceLibrary.fiveCity();
        Matrix<AirCity> m = matrix(inst.cities());
        Route<AirCity> baseline = opt2(m).solve(inst.startCity());
        assertTrue(baseline.isValid());

        // Re-run improvement on the same order and verify distance does not increase.
        List<AirCity> order = new ArrayList<>(baseline.getPath());
        double distBefore = baseline.getTotalDistance();
        RouteImprover.improveClosedValid(order, m, 5);
        double distAfter = RouteEvaluator.evaluate(order, m).getTotalDistance();
        assertTrue(distAfter <= distBefore + 1e-6,
                "Improvement must not increase the total distance");
    }

    @Test
    void route_improver_does_nothing_for_short_route() {
        var inst = TestInstanceLibrary.trivial();
        Matrix<AirCity> m = matrix(inst.cities());
        // trivial: [start, other, start] → 3 elements, below the 4-element minimum
        List<AirCity> order = new ArrayList<>(
                brute(m).solve(inst.startCity()).getPath());
        // Should not throw and should leave the route unchanged.
        assertDoesNotThrow(() -> RouteImprover.improveClosedValid(order, m, 5));
    }
}
