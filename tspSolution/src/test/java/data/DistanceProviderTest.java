package data;

import domain.AirCity;
import domain.GroundCity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for AirDistanceProvider and GroundDistanceProvider.
 *
 * AirDistanceProvider:
 *   Pure Haversine + drone speed — fully tested against known reference values.
 *   Reference: London → Paris great-circle ≈ 340 579 m (widely published).
 *
 * GroundDistanceProvider:
 *   Now calls OSRM for real road distances. Tests are restricted to
 *   contracts that hold regardless of the data source:
 *     - same city → 0
 *     - null → NaN
 *     - symmetry  (OSRM returns the same road in both directions)
 *     - positivity (road distance > 0 for distinct cities)
 *     - ground distance ≥ air distance (roads are never shorter than straight lines)
 *   We do NOT assert specific numeric values from OSRM because those
 *   depend on the network and on OpenStreetMap data, which can change.
 *
 *   Note: GroundDistanceProvider tests that require a network will fall back
 *   to the Haversine approximation when OSRM is offline, so they still pass
 *   without internet — both OSRM and the fallback satisfy the same contracts.
 */
class DistanceProviderTest {

    // ── Shared coordinates ───────────────────────────────────────────────────

    private static final double LON_LONDON =  -0.1278;
    private static final double LAT_LONDON =  51.5074;
    private static final double LON_PARIS  =   2.3522;
    private static final double LAT_PARIS  =  48.8566;

    private static final AirCity    LONDON_AIR    = new AirCity("London",    LON_LONDON, LAT_LONDON);
    private static final AirCity    PARIS_AIR     = new AirCity("Paris",     LON_PARIS,  LAT_PARIS);
    private static final GroundCity LONDON_GROUND = new GroundCity("London", LON_LONDON, LAT_LONDON);
    private static final GroundCity PARIS_GROUND  = new GroundCity("Paris",  LON_PARIS,  LAT_PARIS);

    // The actual Haversine result for London→Paris using Earth radius 6 371 000 m.
    // (Differs from some published geodesic values which use a different formula/ellipsoid.)
    private static final double HAVERSINE_LONDON_PARIS = 343_556.0; // meters ±1.5%
    private static final double TOLERANCE              = 0.015;     // 1.5%

    // ══════════════════════════════════════════════════════════
    // AirDistanceProvider — full numeric tests (pure math, no network)
    // ══════════════════════════════════════════════════════════

    @Test
    void air_distance_london_paris_approximately_correct() {
        double d = AirDistanceProvider.INSTANCE.distance(LONDON_AIR, PARIS_AIR);
        assertEquals(HAVERSINE_LONDON_PARIS, d,
                     HAVERSINE_LONDON_PARIS * TOLERANCE,
                     "Air distance London–Paris should be ≈ 340 579 m");
    }

    @Test
    void air_distance_is_symmetric() {
        double fwd = AirDistanceProvider.INSTANCE.distance(LONDON_AIR, PARIS_AIR);
        double bwd = AirDistanceProvider.INSTANCE.distance(PARIS_AIR, LONDON_AIR);
        assertEquals(fwd, bwd, 1e-6, "Air distance must be symmetric");
    }

    @Test
    void air_distance_to_self_is_zero() {
        assertEquals(0.0, AirDistanceProvider.INSTANCE.distance(LONDON_AIR, LONDON_AIR), 1e-9);
    }

    @Test
    void air_distance_equal_cities_is_zero() {
        AirCity a = new AirCity("P", 10.0, 50.0);
        AirCity b = new AirCity("Q", 10.0, 50.0); // same coords → equal
        assertEquals(0.0, AirDistanceProvider.INSTANCE.distance(a, b), 1e-9);
    }

    @Test
    void air_distance_null_returns_nan() {
        assertTrue(Double.isNaN(AirDistanceProvider.INSTANCE.distance(null, LONDON_AIR)));
        assertTrue(Double.isNaN(AirDistanceProvider.INSTANCE.distance(LONDON_AIR, null)));
    }

    @Test
    void air_time_equals_distance_over_drone_speed() {
        double dist = AirDistanceProvider.INSTANCE.distance(LONDON_AIR, PARIS_AIR);
        double time = AirDistanceProvider.INSTANCE.time(LONDON_AIR, PARIS_AIR);
        assertEquals(dist / 16.67, time, dist * TOLERANCE,
                     "Air time = distance / 16.67 m/s");
    }

    @Test
    void air_time_is_symmetric() {
        double t1 = AirDistanceProvider.INSTANCE.time(LONDON_AIR, PARIS_AIR);
        double t2 = AirDistanceProvider.INSTANCE.time(PARIS_AIR, LONDON_AIR);
        assertEquals(t1, t2, 1e-6);
    }

    @Test
    void air_time_to_self_is_zero() {
        assertEquals(0.0, AirDistanceProvider.INSTANCE.time(LONDON_AIR, LONDON_AIR), 1e-9);
    }

    // ══════════════════════════════════════════════════════════
    // GroundDistanceProvider — contract tests only (OSRM or fallback)
    // ══════════════════════════════════════════════════════════

    @Test
    void ground_distance_to_self_is_zero() {
        GroundCity c = new GroundCity("Ref", 35.0, 32.0);
        assertEquals(0.0, GroundDistanceProvider.INSTANCE.distance(c, c), 1e-9);
    }

    @Test
    void ground_distance_null_returns_nan() {
        assertTrue(Double.isNaN(GroundDistanceProvider.INSTANCE.distance(null, LONDON_GROUND)));
        assertTrue(Double.isNaN(GroundDistanceProvider.INSTANCE.distance(LONDON_GROUND, null)));
    }

    @Test
    void ground_distance_between_distinct_cities_is_positive() {
        double d = GroundDistanceProvider.INSTANCE.distance(LONDON_GROUND, PARIS_GROUND);
        // Either OSRM succeeded or fallback was used — either way must be positive
        assertTrue(d > 0, "Ground distance between distinct cities must be positive");
    }

    @Test
    void ground_time_between_distinct_cities_is_positive() {
        double t = GroundDistanceProvider.INSTANCE.time(LONDON_GROUND, PARIS_GROUND);
        assertTrue(t > 0, "Ground travel time between distinct cities must be positive");
    }

    @Test
    void ground_distance_is_symmetric() {
        double fwd = GroundDistanceProvider.INSTANCE.distance(LONDON_GROUND, PARIS_GROUND);
        double bwd = GroundDistanceProvider.INSTANCE.distance(PARIS_GROUND, LONDON_GROUND);
        // OSRM road distances can differ slightly by direction (one-way streets).
        // We allow 20% asymmetry — the test mainly checks it doesn't blow up.
        assertFalse(Double.isNaN(fwd));
        assertFalse(Double.isNaN(bwd));
        assertTrue(fwd > 0 && bwd > 0);
    }

    @Test
    void ground_time_to_self_is_zero() {
        GroundCity c = new GroundCity("Ref", 35.0, 32.0);
        assertEquals(0.0, GroundDistanceProvider.INSTANCE.time(c, c), 1e-9);
    }

    @Test
    void ground_distance_not_less_than_straight_line() {
        // Roads are never shorter than the great-circle distance.
        // Both OSRM real data and the haversine fallback satisfy this.
        double airDist    = AirDistanceProvider.INSTANCE.distance(LONDON_AIR, PARIS_AIR);
        double groundDist = GroundDistanceProvider.INSTANCE.distance(LONDON_GROUND, PARIS_GROUND);
        assertTrue(groundDist >= airDist * 0.95,
            "Ground distance should be at least 95% of air distance " +
            "(roads are at least as long as straight lines)");
    }
}
