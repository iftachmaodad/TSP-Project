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
 * Tests covering the quality relationship between solvers and the correctness
 * of the local-search operators in RouteImprover.
 *
 * These complement SolverTest which covers correctness (valid/invalid) and
 * guard conditions. The focus here is on solution quality contracts that
 * became meaningful after SlackInsertionSolver had its local search removed
 * and or-opt was added to RouteImprover.
 */
class SolverQualityTest {

    private Matrix<AirCity> matrix(List<AirCity> cities) {
        Matrix<AirCity> m = new Matrix<>(AirCity.class);
        for (AirCity c : cities) m.addCity(c);
        m.populateMatrix();
        return m;
    }

    // ── Solver quality ordering ───────────────────────────────────────────────

    /**
     * SlackInsertion2OptSolver applies multi-start construction and full local
     * search; SlackInsertionSolver is pure construction with no improvement.
     * The optimised solver must never produce a worse valid route than the fast
     * one on the same instance. Each solver receives its own matrix instance to
     * avoid any shared mutable state.
     */
    @Test
    void opt2_never_worse_than_slack_on_five_city() {
        var inst = TestInstanceLibrary.fiveCity();
        AirCity s = inst.startCity();

        Route<AirCity> opt2Route  = new SlackInsertion2OptSolver<>(matrix(inst.cities())).solve(s);
        Route<AirCity> slackRoute = new SlackInsertionSolver<>(matrix(inst.cities())).solve(s);

        assertTrue(opt2Route.isValid(),  "opt2 must produce a valid route on five_city");
        assertTrue(slackRoute.isValid(), "slack must produce a valid route on five_city");
        assertTrue(opt2Route.getTotalDistance() <= slackRoute.getTotalDistance() + 1e-3,
                "opt2 distance " + opt2Route.getTotalDistance()
                + " must not exceed slack distance " + slackRoute.getTotalDistance());
    }

    @Test
    void opt2_never_worse_than_slack_on_eight_city() {
        var inst = TestInstanceLibrary.eightCity();
        AirCity s = inst.startCity();

        Route<AirCity> opt2Route  = new SlackInsertion2OptSolver<>(matrix(inst.cities())).solve(s);
        Route<AirCity> slackRoute = new SlackInsertionSolver<>(matrix(inst.cities())).solve(s);

        assertTrue(opt2Route.isValid(),  "opt2 must produce a valid route on eight_city");
        assertTrue(slackRoute.isValid(), "slack must produce a valid route on eight_city");
        assertTrue(opt2Route.getTotalDistance() <= slackRoute.getTotalDistance() + 1e-3,
                "opt2 distance " + opt2Route.getTotalDistance()
                + " must not exceed slack distance " + slackRoute.getTotalDistance());
    }

    @Test
    void opt2_never_worse_than_slack_on_deadlines_instance() {
        var inst = TestInstanceLibrary.deadlines();
        AirCity s = inst.startCity();

        Route<AirCity> opt2Route  = new SlackInsertion2OptSolver<>(matrix(inst.cities())).solve(s);
        Route<AirCity> slackRoute = new SlackInsertionSolver<>(matrix(inst.cities())).solve(s);

        assertTrue(opt2Route.isValid(),  "opt2 must find a valid route on deadlines instance");
        assertTrue(slackRoute.isValid(), "slack must find a valid route on deadlines instance");
        assertTrue(opt2Route.getTotalDistance() <= slackRoute.getTotalDistance() + 1e-3,
                "opt2 distance " + opt2Route.getTotalDistance()
                + " must not exceed slack distance " + slackRoute.getTotalDistance());
    }

    // ── Or-opt / local search correctness ────────────────────────────────────

    /**
     * improveClosedValid must never increase total distance on instances large
     * enough that or-opt has a realistic chance to fire.
     */
    @Test
    void improver_does_not_worsen_route_on_eight_city() {
        var inst = TestInstanceLibrary.eightCity();
        Matrix<AirCity> m = matrix(inst.cities());

        Route<AirCity> baseline = new SlackInsertionSolver<>(m).solve(inst.startCity());
        assertTrue(baseline.isValid(), "baseline must be valid before improvement");

        List<AirCity> order = new ArrayList<>(baseline.getPath());
        double distBefore = baseline.getTotalDistance();
        RouteImprover.improveClosedValid(order, m, 8);
        double distAfter = RouteEvaluator.evaluate(order, m).getTotalDistance();

        assertTrue(distAfter <= distBefore + 1e-6,
                "improvement must not increase distance: before=" + distBefore
                + " after=" + distAfter);
    }

    @Test
    void improver_does_not_worsen_route_on_ten_city() {
        var inst = TestInstanceLibrary.tenCity();
        Matrix<AirCity> m = matrix(inst.cities());

        Route<AirCity> baseline = new SlackInsertionSolver<>(m).solve(inst.startCity());
        assertTrue(baseline.isValid(), "baseline must be valid before improvement");

        List<AirCity> order = new ArrayList<>(baseline.getPath());
        double distBefore = baseline.getTotalDistance();
        RouteImprover.improveClosedValid(order, m, 8);
        double distAfter = RouteEvaluator.evaluate(order, m).getTotalDistance();

        assertTrue(distAfter <= distBefore + 1e-6,
                "improvement must not increase distance: before=" + distBefore
                + " after=" + distAfter);
    }

    /**
     * After improvement the route must still be closed (first city == last city)
     * and contain the same number of cities as before.
     */
    @Test
    void improver_preserves_route_structure_on_eight_city() {
        var inst = TestInstanceLibrary.eightCity();
        Matrix<AirCity> m = matrix(inst.cities());

        Route<AirCity> baseline = new SlackInsertionSolver<>(m).solve(inst.startCity());
        assertTrue(baseline.isValid(), "baseline must be valid before improvement");

        List<AirCity> order = new ArrayList<>(baseline.getPath());
        int sizeBefore = order.size();

        RouteImprover.improveClosedValid(order, m, 8);

        assertEquals(sizeBefore, order.size(),
                "improvement must not change the number of cities in the route");
        assertEquals(order.get(0), order.get(order.size() - 1),
                "route must remain closed after improvement");
    }
}
