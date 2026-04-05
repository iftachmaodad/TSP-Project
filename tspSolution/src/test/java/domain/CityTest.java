package domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link City} base-class behaviour via the {@link AirCity} subclass.
 *
 * <p>Auto-generated IDs accumulate across test runs because the counter is a
 * static field. Tests therefore never assert specific counter values — they
 * only verify structural properties (non-null, non-blank, not a type prefix).
 */
class CityTest {

    // ── Coordinate storage ────────────────────────────────────────────────────

    @Test
    void longitude_in_range_stored_unchanged() {
        AirCity c = new AirCity("X", 35.0, 32.0);
        assertEquals(35.0, c.getX(), 1e-9);
    }

    @Test
    void latitude_in_range_stored_unchanged() {
        AirCity c = new AirCity("X", 35.0, 32.0);
        assertEquals(32.0, c.getY(), 1e-9);
    }

    // ── Coordinate clamping ───────────────────────────────────────────────────

    @Test
    void longitude_beyond_180_is_clamped_into_range() {
        AirCity c = new AirCity("X", 200.0, 0.0);
        assertTrue(c.getX() >= -180 && c.getX() <= 180,
                "Clamped longitude must be in [-180, 180], got " + c.getX());
    }

    @Test
    void latitude_above_90_clamped_to_90() {
        assertEquals(90.0, new AirCity("X", 0.0, 120.0).getY(), 1e-9);
    }

    @Test
    void latitude_below_minus90_clamped_to_minus90() {
        assertEquals(-90.0, new AirCity("X", 0.0, -120.0).getY(), 1e-9);
    }

    // ── Deadline ──────────────────────────────────────────────────────────────

    @Test
    void positive_deadline_is_stored() {
        AirCity c = new AirCity("X", 35.0, 32.0, 1000.0);
        assertTrue(c.hasDeadline());
        assertEquals(1000.0, c.getDeadline(), 1e-9);
    }

    @Test
    void zero_deadline_treated_as_no_deadline() {
        AirCity c = new AirCity("X", 35.0, 32.0, 0.0);
        assertFalse(c.hasDeadline());
        assertEquals(City.NO_DEADLINE, c.getDeadline());
    }

    @Test
    void negative_deadline_treated_as_no_deadline() {
        assertFalse(new AirCity("X", 35.0, 32.0, -1.0).hasDeadline());
    }

    @Test
    void no_deadline_when_not_specified() {
        AirCity c = new AirCity("X", 35.0, 32.0);
        assertFalse(c.hasDeadline());
        assertEquals(City.NO_DEADLINE, c.getDeadline());
    }

    // ── ID / auto-naming ──────────────────────────────────────────────────────

    @Test
    void explicit_id_is_preserved() {
        assertEquals("MyCity", new AirCity("MyCity", 35.0, 32.0).getID());
    }

    @Test
    void null_id_triggers_auto_naming() {
        AirCity c = new AirCity(null, 1.0, 1.0);
        assertNotNull(c.getID());
        assertFalse(c.getID().isBlank());
    }

    @Test
    void blank_id_triggers_auto_naming() {
        AirCity c = new AirCity("   ", 2.0, 2.0);
        assertNotNull(c.getID());
        assertFalse(c.getID().isBlank());
    }

    @Test
    void id_starting_with_type_prefix_triggers_auto_naming() {
        // "AirCity" is a registered prefix — must not be stored as-is.
        AirCity c = new AirCity("AirCity99", 3.0, 3.0);
        assertFalse("AirCity99".equals(c.getID()),
                "ID matching a type prefix must be replaced by an auto-generated name");
    }

    @Test
    void auto_generated_ids_are_unique_per_instance() {
        AirCity c1 = new AirCity(null, 10.0, 10.0);
        AirCity c2 = new AirCity(null, 11.0, 11.0);
        assertNotEquals(c1.getID(), c2.getID(),
                "Two auto-named cities must get distinct IDs");
    }

    // ── equals and hashCode ───────────────────────────────────────────────────

    @Test
    void same_type_same_coords_are_equal() {
        AirCity a = new AirCity("Alpha", 35.0, 32.0);
        AirCity b = new AirCity("Beta",  35.0, 32.0);
        assertEquals(a, b);
    }

    @Test
    void same_type_same_coords_same_hashcode() {
        AirCity a = new AirCity("Alpha", 35.0, 32.0);
        AirCity b = new AirCity("Beta",  35.0, 32.0);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void different_coords_not_equal() {
        assertNotEquals(
                new AirCity("A", 35.0, 32.0),
                new AirCity("B", 35.1, 32.0));
    }

    @Test
    void different_subclass_same_coords_not_equal() {
        assertNotEquals(
                new AirCity("X",    35.0, 32.0),
                new GroundCity("X", 35.0, 32.0),
                "AirCity and GroundCity at same coords must not be equal");
    }

    @Test
    void city_equals_itself() {
        AirCity c = new AirCity("X", 35.0, 32.0);
        assertEquals(c, c);
    }

    @Test
    void city_not_equal_to_null() {
        assertNotEquals(null, new AirCity("X", 35.0, 32.0));
    }

    @Test
    void coords_within_epsilon_are_equal() {
        AirCity a = new AirCity("A", 35.0,        32.0);
        AirCity b = new AirCity("B", 35.0 + 1e-10, 32.0);
        assertEquals(a, b, "Difference well below 1e-9 must be treated as equal");
    }

    @Test
    void coords_outside_epsilon_are_not_equal() {
        AirCity a = new AirCity("A", 35.0,        32.0);
        AirCity b = new AirCity("B", 35.0 + 1e-8,  32.0);
        assertNotEquals(a, b, "Difference above 1e-9 must be treated as distinct");
    }

    // ── toString ──────────────────────────────────────────────────────────────

    @Test
    void toString_contains_id_and_coords() {
        AirCity c = new AirCity("Paris", 2.3522, 48.8566);
        String s = c.toString();
        assertTrue(s.contains("Paris"),  "toString must contain the ID");
        assertTrue(s.contains("2.3522") || s.contains("2.35"),
                "toString must contain the longitude");
    }

    @Test
    void toString_contains_deadline_when_set() {
        AirCity c = new AirCity("X", 0.0, 0.0, 3600.0);
        assertTrue(c.toString().contains("3600"),
                "toString must mention the deadline value");
    }

    @Test
    void toString_does_not_contain_deadline_when_absent() {
        AirCity c = new AirCity("X", 0.0, 0.0);
        assertFalse(c.toString().toLowerCase().contains("due"),
                "toString must not mention deadline when city has none");
    }
}
