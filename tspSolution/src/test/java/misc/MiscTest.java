package misc;

import benchmark.TestInstanceLibrary;
import domain.AirCity;
import domain.City;
import domain.CityFactory;
import domain.CityRegistry;
import domain.GroundCity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for smaller utility classes that don't warrant their own file:
 *   - CityFactory (create, parseDeadline)
 *   - CityRegistry (register, exists, getProvider)
 *   - TestInstanceLibrary (self-consistency of all 8 instances)
 */
class MiscTest {

    // ══════════════════════════════════════════════════════════
    // CityFactory
    // ══════════════════════════════════════════════════════════

    @Test
    void cityFactory_creates_aircity_with_correct_coords() {
        AirCity c = CityFactory.create(AirCity.class, "Test", 35.0, 32.0);
        assertNotNull(c);
        assertEquals(35.0, c.getX(), 1e-9);
        assertEquals(32.0, c.getY(), 1e-9);
    }

    @Test
    void cityFactory_creates_groundcity_correctly() {
        GroundCity c = CityFactory.create(GroundCity.class, "Test", 35.0, 32.0);
        assertNotNull(c);
    }

    @Test
    void cityFactory_preserves_explicit_id() {
        AirCity c = CityFactory.create(AirCity.class, "MyTown", 35.0, 32.0);
        assertNotNull(c);
        assertEquals("MyTown", c.getID());
    }

    @Test
    void cityFactory_with_deadline_sets_deadline() {
        AirCity c = CityFactory.create(AirCity.class, "X", 35.0, 32.0, 500.0);
        assertNotNull(c);
        assertTrue(c.hasDeadline());
        assertEquals(500.0, c.getDeadline(), 1e-9);
    }

    @Test
    void cityFactory_with_null_deadline_means_no_deadline() {
        AirCity c = CityFactory.create(AirCity.class, "X", 35.0, 32.0, null);
        assertNotNull(c);
        assertFalse(c.hasDeadline());
    }

    @Test
    void cityFactory_with_negative_deadline_means_no_deadline() {
        AirCity c = CityFactory.create(AirCity.class, "X", 35.0, 32.0, -1.0);
        assertNotNull(c);
        assertFalse(c.hasDeadline());
    }

    // ── parseDeadline ────────────────────────────────────────────────────────

    @Test
    void parseDeadline_valid_positive_string_returns_value() {
        Double d = CityFactory.parseDeadline("1500");
        assertNotNull(d);
        assertEquals(1500.0, d, 1e-9);
    }

    @Test
    void parseDeadline_blank_string_returns_null() {
        assertNull(CityFactory.parseDeadline(""));
        assertNull(CityFactory.parseDeadline("   "));
    }

    @Test
    void parseDeadline_null_returns_null() {
        assertNull(CityFactory.parseDeadline(null));
    }

    @Test
    void parseDeadline_negative_number_returns_null() {
        assertNull(CityFactory.parseDeadline("-100"));
    }

    @Test
    void parseDeadline_zero_returns_null() {
        assertNull(CityFactory.parseDeadline("0"));
    }

    @Test
    void parseDeadline_non_numeric_returns_null() {
        assertNull(CityFactory.parseDeadline("abc"));
        assertNull(CityFactory.parseDeadline("12.3.4"));
    }

    // ══════════════════════════════════════════════════════════
    // CityRegistry
    // ══════════════════════════════════════════════════════════

    @Test
    void aircity_is_registered() {
        assertTrue(CityRegistry.exists(AirCity.class));
    }

    @Test
    void groundcity_is_registered() {
        assertTrue(CityRegistry.exists(GroundCity.class));
    }

    @Test
    void getProvider_returns_non_null_for_registered_type() {
        assertNotNull(CityRegistry.getProvider(AirCity.class));
        assertNotNull(CityRegistry.getProvider(GroundCity.class));
    }

    @Test
    void getProvider_returns_null_for_unknown_type() {
        // City itself is not registered (it's abstract — only subclasses are)
        // We verify a non-registered type returns null
        assertNull(CityRegistry.getProvider(City.class));
    }

    @Test
    void getRegisteredTypes_contains_both_types() {
        var types = CityRegistry.getRegisteredTypes();
        assertTrue(types.contains(AirCity.class));
        assertTrue(types.contains(GroundCity.class));
    }

    @Test
    void startsWithIgnoreCase_detects_aircity_prefix() {
        assertTrue(CityRegistry.startsWithIgnoreCase("aircity1"));
        assertTrue(CityRegistry.startsWithIgnoreCase("AIRCITY_whatever"));
    }

    @Test
    void startsWithIgnoreCase_returns_false_for_custom_name() {
        assertFalse(CityRegistry.startsWithIgnoreCase("MyCity"));
        assertFalse(CityRegistry.startsWithIgnoreCase("Berlin"));
    }

    @Test
    void startsWithIgnoreCase_returns_false_for_null() {
        assertFalse(CityRegistry.startsWithIgnoreCase(null));
    }

    // ══════════════════════════════════════════════════════════
    // TestInstanceLibrary — self-consistency
    // ══════════════════════════════════════════════════════════

    @Test
    void all_instances_have_start_city_in_city_list() {
        for (var inst : TestInstanceLibrary.all()) {
            assertTrue(inst.cities().contains(inst.startCity()),
                "Instance '" + inst.name() + "': startCity must be in the city list");
        }
    }

    @Test
    void all_instances_have_at_least_two_cities() {
        for (var inst : TestInstanceLibrary.all()) {
            assertTrue(inst.size() >= 2,
                "Instance '" + inst.name() + "': must have at least 2 cities");
        }
    }

    @Test
    void instance_sizes_match_documentation() {
        assertEquals(2,  TestInstanceLibrary.trivial()            .size());
        assertEquals(3,  TestInstanceLibrary.triangle()           .size());
        assertEquals(5,  TestInstanceLibrary.fiveCity()           .size());
        assertEquals(5,  TestInstanceLibrary.deadlines()          .size());
        assertEquals(8,  TestInstanceLibrary.eightCity()          .size());
        assertEquals(8,  TestInstanceLibrary.eightCityDeadlines() .size());
        assertEquals(10, TestInstanceLibrary.tenCity()            .size());
        assertEquals(10, TestInstanceLibrary.tenCityDeadlines()   .size());
        assertEquals(20, TestInstanceLibrary.twentyCity()         .size());
        assertEquals(3,  TestInstanceLibrary.infeasible()         .size());
    }

    @Test
    void all_instance_names_are_unique() {
        var all = TestInstanceLibrary.all();
        long distinct = all.stream().map(i -> i.name()).distinct().count();
        assertEquals(all.size(), distinct,
            "Every instance must have a unique name");
    }

    @Test
    void all_returns_ten_instances() {
        assertEquals(10, TestInstanceLibrary.all().size());
    }

    @Test
    void infeasible_instance_has_a_city_with_short_deadline() {
        var inst = TestInstanceLibrary.infeasible();
        boolean found = inst.cities().stream()
            .filter(c -> !c.equals(inst.startCity()))
            .anyMatch(c -> c.hasDeadline() && c.getDeadline() < 200.0);
        assertTrue(found,
            "infeasible instance must contain at least one city with a very short deadline");
    }

    @Test
    void deadlines_instance_has_two_cities_with_deadlines() {
        long count = TestInstanceLibrary.deadlines().cities().stream()
            .filter(City::hasDeadline)
            .count();
        assertEquals(2, count,
            "deadlines instance must have exactly 2 cities with deadlines");
    }
}
