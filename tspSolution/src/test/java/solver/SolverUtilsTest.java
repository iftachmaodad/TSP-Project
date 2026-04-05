package solver;

import data.Matrix;
import domain.AirCity;
import domain.City;
import model.Route;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link SolverUtils} static helpers.
 */
class SolverUtilsTest {

    private AirCity start, near, far;
    private Matrix<AirCity> matrix;

    @BeforeEach
    void setUp() {
        start = new AirCity("Start", 35.0, 32.0);
        near  = new AirCity("Near",  35.1, 32.0); // ~11 km, ~660 s at drone speed
        far   = new AirCity("Far",   35.5, 32.0); // ~55 km, ~3 300 s

        matrix = new Matrix<>(AirCity.class);
        matrix.addCity(start);
        matrix.addCity(near);
        matrix.addCity(far);
        matrix.populateMatrix();
    }

    // ── safeTime ──────────────────────────────────────────────────────────────

    @Test
    void safeTime_positive_for_distinct_cities() {
        assertTrue(SolverUtils.safeTime(matrix, start, near) > 0);
    }

    @Test
    void safeTime_zero_for_same_city() {
        assertEquals(0.0, SolverUtils.safeTime(matrix, start, start), 1e-9);
    }

    @Test
    void safeTime_infinity_for_city_not_in_matrix() {
        AirCity unknown = new AirCity("X", 10.0, 10.0);
        assertEquals(Double.POSITIVE_INFINITY,
                SolverUtils.safeTime(matrix, start, unknown));
    }

    // ── safeDistance ──────────────────────────────────────────────────────────

    @Test
    void safeDistance_positive_for_distinct_cities() {
        assertTrue(SolverUtils.safeDistance(matrix, start, near) > 0);
    }

    @Test
    void safeDistance_infinity_for_unknown_city() {
        AirCity unknown = new AirCity("X", 10.0, 10.0);
        assertEquals(Double.POSITIVE_INFINITY,
                SolverUtils.safeDistance(matrix, start, unknown));
    }

    // ── slackFromStart ────────────────────────────────────────────────────────

    @Test
    void slackFromStart_positive_when_deadline_generous() {
        AirCity generous = new AirCity("G", 35.1, 32.0, 100_000.0);
        Matrix<AirCity> m = new Matrix<>(AirCity.class);
        m.addCity(start); m.addCity(generous); m.populateMatrix();
        assertTrue(SolverUtils.slackFromStart(m, start, generous) > 0);
    }

    @Test
    void slackFromStart_negative_when_deadline_impossible() {
        AirCity impossible = new AirCity("I", 35.1, 32.0, 1.0);
        Matrix<AirCity> m = new Matrix<>(AirCity.class);
        m.addCity(start); m.addCity(impossible); m.populateMatrix();
        assertTrue(SolverUtils.slackFromStart(m, start, impossible) < 0);
    }

    // ── findInfeasibleUrgent ──────────────────────────────────────────────────

    @Test
    void findInfeasibleUrgent_null_when_all_feasible() {
        AirCity feasible = new AirCity("F", 35.1, 32.0, 100_000.0);
        Matrix<AirCity> m = new Matrix<>(AirCity.class);
        m.addCity(start); m.addCity(feasible); m.populateMatrix();
        assertNull(SolverUtils.findInfeasibleUrgent(m, start, List.of(feasible)));
    }

    @Test
    void findInfeasibleUrgent_returns_city_when_deadline_missed() {
        AirCity impossible = new AirCity("I", 35.1, 32.0, 1.0);
        Matrix<AirCity> m = new Matrix<>(AirCity.class);
        m.addCity(start); m.addCity(impossible); m.populateMatrix();
        City result = SolverUtils.findInfeasibleUrgent(m, start, List.of(impossible));
        assertEquals(impossible, result);
    }

    @Test
    void findInfeasibleUrgent_empty_list_returns_null() {
        assertNull(SolverUtils.findInfeasibleUrgent(matrix, start, List.of()));
    }

    // ── chooseBetterInvalid ───────────────────────────────────────────────────

    @Test
    void chooseBetterInvalid_returns_non_null_when_one_is_null() {
        Route<AirCity> r = new Route<>(start);
        r.invalidate("test");
        assertSame(r, SolverUtils.chooseBetterInvalid(null, r));
        assertSame(r, SolverUtils.chooseBetterInvalid(r, null));
    }

    @Test
    void chooseBetterInvalid_both_null_returns_null() {
        assertNull(SolverUtils.chooseBetterInvalid(null, null));
    }

    @Test
    void chooseBetterInvalid_prefers_more_cities_visited() {
        Route<AirCity> small = new Route<>(start);
        small.invalidate("small");

        Route<AirCity> larger = new Route<>(start);
        larger.addStep(near, 9430.0, 566.0);
        larger.invalidate("larger");

        assertSame(larger, SolverUtils.chooseBetterInvalid(small, larger));
    }

    @Test
    void chooseBetterInvalid_prefers_shorter_distance_on_tie() {
        Route<AirCity> shortR = new Route<>(start);
        shortR.addStep(near, 1_000.0, 60.0);
        shortR.invalidate("short");

        Route<AirCity> longR = new Route<>(start);
        longR.addStep(near, 50_000.0, 3_000.0);
        longR.invalidate("long");

        assertSame(shortR, SolverUtils.chooseBetterInvalid(longR, shortR));
    }

    // ── bestFeasibleInsertion ─────────────────────────────────────────────────

    @Test
    void bestFeasibleInsertion_finds_position_for_simple_route() {
        List<AirCity> route = new ArrayList<>(List.of(start, start));
        Insertion<AirCity> ins =
                SolverUtils.bestFeasibleInsertion(route, matrix, near);
        assertNotNull(ins);
        assertEquals(near, ins.city());
        assertTrue(ins.index() >= 1 && ins.index() < route.size());
    }

    @Test
    void bestFeasibleInsertion_null_for_impossible_deadline() {
        AirCity impossible = new AirCity("I", 35.1, 32.0, 1.0);
        Matrix<AirCity> m = new Matrix<>(AirCity.class);
        m.addCity(start); m.addCity(impossible); m.populateMatrix();

        List<AirCity> route = new ArrayList<>(List.of(start, start));
        assertNull(SolverUtils.bestFeasibleInsertion(route, m, impossible));
    }

    @Test
    void bestFeasibleInsertion_delta_is_finite() {
        List<AirCity> route = new ArrayList<>(List.of(start, start));
        Insertion<AirCity> ins =
                SolverUtils.bestFeasibleInsertion(route, matrix, near);
        assertNotNull(ins);
        assertTrue(Double.isFinite(ins.deltaDistance()));
    }

    // ── greedyInsertAll ───────────────────────────────────────────────────────

    @Test
    void greedyInsertAll_inserts_all_when_no_deadlines() {
        List<AirCity> route = new ArrayList<>(List.of(start, start));
        List<AirCity> remaining =
                SolverUtils.greedyInsertAll(route, matrix, List.of(near, far));
        assertTrue(remaining.isEmpty(),
                "All no-deadline cities must be insertable");
        assertEquals(4, route.size()); // start + near + far + start
    }

    @Test
    void greedyInsertAll_returns_uninserted_when_deadline_impossible() {
        AirCity impossible = new AirCity("I", 35.1, 32.0, 1.0);
        Matrix<AirCity> m = new Matrix<>(AirCity.class);
        m.addCity(start); m.addCity(impossible); m.populateMatrix();

        List<AirCity> route = new ArrayList<>(List.of(start, start));
        List<AirCity> remaining =
                SolverUtils.greedyInsertAll(route, m, List.of(impossible));
        assertEquals(1, remaining.size(),
                "City with impossible deadline must remain uninserted");
    }

    @Test
    void greedyInsertAll_does_not_modify_candidates_list() {
        List<AirCity> route      = new ArrayList<>(List.of(start, start));
        List<AirCity> candidates = new ArrayList<>(List.of(near, far));
        SolverUtils.greedyInsertAll(route, matrix, candidates);
        assertEquals(2, candidates.size(),
                "greedyInsertAll must not modify the original candidates list");
    }

    // ── sortedCopy ────────────────────────────────────────────────────────────

    @Test
    void sortedCopy_does_not_modify_source() {
        List<AirCity> src = new ArrayList<>(List.of(far, near, start));
        SolverUtils.sortedCopy(src, java.util.Comparator.comparing(City::getID));
        assertEquals(far, src.get(0), "Source list must not be modified by sortedCopy");
    }

    @Test
    void sortedCopy_returns_sorted_result() {
        List<AirCity> src    = new ArrayList<>(List.of(far, near, start));
        List<AirCity> sorted = SolverUtils.sortedCopy(
                src, java.util.Comparator.comparing(City::getID));
        for (int i = 0; i < sorted.size() - 1; i++) {
            assertTrue(sorted.get(i).getID()
                    .compareTo(sorted.get(i + 1).getID()) <= 0);
        }
    }

    // ── fail ──────────────────────────────────────────────────────────────────

    @Test
    void fail_returns_invalid_route() {
        Route<AirCity> r = SolverUtils.fail(matrix, "test error");
        assertFalse(r.isValid());
    }

    @Test
    void fail_throws_when_matrix_empty() {
        Matrix<AirCity> empty = new Matrix<>(AirCity.class);
        assertThrows(IllegalStateException.class,
                () -> SolverUtils.fail(empty, "msg"));
    }
}
