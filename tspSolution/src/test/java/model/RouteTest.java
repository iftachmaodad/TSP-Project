package model;

import domain.AirCity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Route}.
 *
 * <p>Covers all invariants stated in the class Javadoc:
 * <ul>
 *   <li>path / arrivalTimes / legDistances are always the same length.</li>
 *   <li>Once invalid, always invalid.</li>
 *   <li>Steps added after invalidation are recorded in path but do NOT
 *       update distance/time accumulators.</li>
 *   <li>NaN or infinite dist/time values immediately invalidate the route.</li>
 *   <li>Deadline-at-exactly-arrival is valid (≤, not &lt;).</li>
 * </ul>
 */
class RouteTest {

    private static final AirCity START = new AirCity("Start", 35.0, 32.0);
    private static final AirCity A     = new AirCity("A",     35.1, 32.0);
    private static final AirCity B     = new AirCity("B",     35.2, 32.0);

    // ── Construction ──────────────────────────────────────────────────────────

    @Test
    void new_route_is_valid() {
        assertTrue(new Route<>(START).isValid());
    }

    @Test
    void new_route_has_size_one() {
        assertEquals(1, new Route<>(START).size());
    }

    @Test
    void new_route_has_zero_distance_and_time() {
        Route<AirCity> r = new Route<>(START);
        assertEquals(0.0, r.getTotalDistance(), 1e-9);
        assertEquals(0.0, r.getTotalTime(),     1e-9);
    }

    @Test
    void null_start_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> new Route<AirCity>(null));
    }

    @Test
    void start_city_is_last_city_initially() {
        assertEquals(START, new Route<>(START).getLastCity());
    }

    // ── List length invariant ─────────────────────────────────────────────────

    @Test
    void path_arrival_times_leg_distances_always_same_length() {
        Route<AirCity> r = new Route<>(START);
        assertSizes(r, 1);

        r.addStep(A, 1000.0, 60.0);
        assertSizes(r, 2);

        r.addStep(B, 500.0, 30.0);
        assertSizes(r, 3);
    }

    /** Helper: asserts all three lists have the expected length. */
    private static void assertSizes(Route<AirCity> r, int expected) {
        assertEquals(expected, r.size(),                "path size");
        assertEquals(expected, r.getArrivalTimes().size(), "arrivalTimes size");
        assertEquals(expected, r.getLegDistances().size(), "legDistances size");
    }

    // ── addStep accumulation ──────────────────────────────────────────────────

    @Test
    void addStep_increases_size() {
        Route<AirCity> r = new Route<>(START);
        r.addStep(A, 1000.0, 60.0);
        assertEquals(2, r.size());
    }

    @Test
    void addStep_accumulates_distance() {
        Route<AirCity> r = new Route<>(START);
        r.addStep(A, 1000.0, 60.0);
        r.addStep(B, 500.0,  30.0);
        assertEquals(1500.0, r.getTotalDistance(), 1e-9);
    }

    @Test
    void addStep_accumulates_time() {
        Route<AirCity> r = new Route<>(START);
        r.addStep(A, 1000.0, 60.0);
        r.addStep(B, 500.0,  30.0);
        assertEquals(90.0, r.getTotalTime(), 1e-9);
    }

    @Test
    void addStep_updates_last_city() {
        Route<AirCity> r = new Route<>(START);
        r.addStep(A, 1000.0, 60.0);
        assertEquals(A, r.getLastCity());
    }

    @Test
    void addStep_null_city_invalidates_route() {
        Route<AirCity> r = new Route<>(START);
        r.addStep(null, 0.0, 0.0);
        assertFalse(r.isValid());
    }

    // ── NaN / Infinite guard ──────────────────────────────────────────────────

    @Test
    void nan_distance_immediately_invalidates_route() {
        Route<AirCity> r = new Route<>(START);
        r.addStep(A, Double.NaN, 60.0);
        assertFalse(r.isValid(), "NaN distance must invalidate the route");
    }

    @Test
    void infinite_distance_immediately_invalidates_route() {
        Route<AirCity> r = new Route<>(START);
        r.addStep(A, Double.POSITIVE_INFINITY, 60.0);
        assertFalse(r.isValid());
    }

    @Test
    void nan_time_immediately_invalidates_route() {
        Route<AirCity> r = new Route<>(START);
        r.addStep(A, 1000.0, Double.NaN);
        assertFalse(r.isValid());
    }

    @Test
    void nan_does_not_poison_total_distance_accumulator() {
        // After NaN invalidation the accumulator must not become NaN.
        Route<AirCity> r = new Route<>(START);
        r.addStep(A, Double.NaN, 60.0);
        assertFalse(Double.isNaN(r.getTotalDistance()),
                "totalDistance must remain 0.0 after NaN step, not NaN itself");
        assertEquals(0.0, r.getTotalDistance(), 1e-9);
    }

    // ── Frozen metrics after invalidation ────────────────────────────────────

    @Test
    void steps_after_invalidation_do_not_change_distance() {
        Route<AirCity> r = new Route<>(START);
        r.addStep(A, 1000.0, 60.0);
        r.invalidate("test");
        double distBefore = r.getTotalDistance();

        r.addStep(B, 999_999.0, 9999.0); // should be ignored for metrics
        assertEquals(distBefore, r.getTotalDistance(), 1e-9,
                "Distance must not accumulate after invalidation");
    }

    @Test
    void steps_after_invalidation_do_not_change_time() {
        Route<AirCity> r = new Route<>(START);
        r.addStep(A, 1000.0, 60.0);
        r.invalidate("test");
        double timeBefore = r.getTotalTime();

        r.addStep(B, 999_999.0, 9999.0);
        assertEquals(timeBefore, r.getTotalTime(), 1e-9,
                "Time must not accumulate after invalidation");
    }

    @Test
    void city_added_after_invalidation_still_recorded_in_path() {
        // Path records cities for diagnostics even after invalidation.
        Route<AirCity> r = new Route<>(START);
        r.addStep(A, 1000.0, 60.0);
        r.invalidate("test");
        r.addStep(B, 500.0, 30.0);
        assertTrue(r.getPath().contains(B),
                "City added after invalidation must appear in path for diagnostics");
    }

    @Test
    void once_invalid_stays_invalid() {
        AirCity tight = new AirCity("Tight", 35.1, 32.0, 50.0);
        Route<AirCity> r = new Route<>(START);
        r.addStep(tight, 1000.0, 60.0); // misses deadline → invalid
        r.addStep(B, 500.0, 30.0);
        assertFalse(r.isValid());
    }

    // ── Deadline handling ─────────────────────────────────────────────────────

    @Test
    void deadline_met_keeps_route_valid() {
        AirCity generous = new AirCity("D", 35.1, 32.0, 200.0);
        Route<AirCity> r = new Route<>(START);
        r.addStep(generous, 1000.0, 60.0);
        assertTrue(r.isValid());
    }

    @Test
    void deadline_missed_invalidates_route() {
        AirCity tight = new AirCity("Tight", 35.1, 32.0, 50.0);
        Route<AirCity> r = new Route<>(START);
        r.addStep(tight, 1000.0, 60.0);
        assertFalse(r.isValid());
    }

    @Test
    void arriving_exactly_at_deadline_is_valid() {
        AirCity exact = new AirCity("Exact", 35.1, 32.0, 60.0);
        Route<AirCity> r = new Route<>(START);
        r.addStep(exact, 1000.0, 60.0);
        assertTrue(r.isValid(),
                "Arrival == deadline must be valid (condition is time > deadline)");
    }

    // ── Explicit invalidate ───────────────────────────────────────────────────

    @Test
    void explicit_invalidate_marks_route_invalid() {
        Route<AirCity> r = new Route<>(START);
        r.invalidate("reason");
        assertFalse(r.isValid());
    }

    @Test
    void invalidate_with_null_message_still_invalidates() {
        Route<AirCity> r = new Route<>(START);
        r.invalidate(null);
        assertFalse(r.isValid());
    }

    // ── Path immutability ─────────────────────────────────────────────────────

    @Test
    void getPath_is_unmodifiable() {
        Route<AirCity> r = new Route<>(START);
        r.addStep(A, 1000.0, 60.0);
        assertThrows(UnsupportedOperationException.class,
                () -> r.getPath().add(B));
    }

    @Test
    void getArrivalTimes_is_unmodifiable() {
        Route<AirCity> r = new Route<>(START);
        assertThrows(UnsupportedOperationException.class,
                () -> r.getArrivalTimes().add(99.0));
    }

    @Test
    void getLegDistances_is_unmodifiable() {
        Route<AirCity> r = new Route<>(START);
        assertThrows(UnsupportedOperationException.class,
                () -> r.getLegDistances().add(99.0));
    }

    // ── Path contents ─────────────────────────────────────────────────────────

    @Test
    void path_starts_with_start_city() {
        Route<AirCity> r = new Route<>(START);
        r.addStep(A, 1000.0, 60.0);
        assertEquals(START, r.getPath().get(0));
    }

    @Test
    void path_contains_steps_in_order() {
        Route<AirCity> r = new Route<>(START);
        r.addStep(A, 1000.0, 60.0);
        r.addStep(B,  500.0, 30.0);
        List<AirCity> path = r.getPath();
        assertEquals(START, path.get(0));
        assertEquals(A,     path.get(1));
        assertEquals(B,     path.get(2));
    }

    @Test
    void first_leg_distance_is_zero() {
        Route<AirCity> r = new Route<>(START);
        r.addStep(A, 1000.0, 60.0);
        assertEquals(0.0, r.getLegDistances().get(0), 1e-9,
                "Entry 0 of legDistances is always 0 (start has no incoming leg)");
    }

    @Test
    void first_arrival_time_is_zero() {
        Route<AirCity> r = new Route<>(START);
        r.addStep(A, 1000.0, 60.0);
        assertEquals(0.0, r.getArrivalTimes().get(0), 1e-9);
    }
}
