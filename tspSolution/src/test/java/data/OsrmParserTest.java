package data;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link OsrmParser}.
 *
 * <p>All tests are offline and deterministic — no network calls are made.
 * Fake JSON strings are taken from real OSRM responses, trimmed to the
 * fields the parser cares about.
 *
 * <p>A critical regression test verifies that when the {@code legs} array
 * contains its own {@code distance}/{@code duration} fields (which appear
 * before the top-level route fields in the JSON string), the parser correctly
 * returns the top-level values and not the leg values.
 */
class OsrmParserTest {

    private final OsrmParser parser = OsrmParser.INSTANCE;

    // ── Happy path ────────────────────────────────────────────────────────────

    private static final String VALID_RESPONSE =
            "{\"code\":\"Ok\",\"routes\":[{"
            + "\"distance\":450123.4,"
            + "\"duration\":32456.7,"
            + "\"weight\":32456.7,"
            + "\"weight_name\":\"routability\""
            + "}],\"waypoints\":[]}";

    @Test
    void extracts_distance_from_valid_response() {
        assertEquals(450123.4, parser.extractDistance(VALID_RESPONSE), 1e-6);
    }

    @Test
    void extracts_duration_from_valid_response() {
        assertEquals(32456.7, parser.extractDuration(VALID_RESPONSE), 1e-6);
    }

    // ── Integer values ────────────────────────────────────────────────────────

    @Test
    void handles_integer_distance() {
        String json = "{\"code\":\"Ok\",\"routes\":[{\"distance\":1000,\"duration\":60}]}";
        assertEquals(1000.0, parser.extractDistance(json), 1e-9);
    }

    @Test
    void handles_integer_duration() {
        String json = "{\"code\":\"Ok\",\"routes\":[{\"distance\":1000,\"duration\":60}]}";
        assertEquals(60.0, parser.extractDuration(json), 1e-9);
    }

    // ── CRITICAL regression: legs array appears before top-level fields ───────

    /**
     * In a real OSRM response the {@code legs} array is nested inside the
     * route object and its {@code distance}/{@code duration} appear before the
     * route-level values in the JSON string.
     *
     * <p>The parser must skip the legs array and return the route-level values.
     * Previously, {@code indexOf("\"distance\"")} would find the leg value first,
     * causing this test to fail.
     */
    @Test
    void returns_route_level_distance_not_leg_distance() {
        // Leg distance (63412.5) appears BEFORE route-level distance (63412.5)
        // in the string. In this single-pair request they happen to be equal,
        // but the test uses different values to make the distinction explicit.
        String json =
                "{\"code\":\"Ok\","
                + "\"routes\":[{"
                + "\"legs\":[{\"steps\":[],\"summary\":\"\","
                + "\"weight\":100.0,\"duration\":100.0,\"distance\":99999.0}],"
                + "\"weight_name\":\"routability\","
                + "\"weight\":3821.8,"
                + "\"duration\":3821.8,"
                + "\"distance\":63412.5"
                + "}],"
                + "\"waypoints\":[]}";

        assertEquals(63412.5, parser.extractDistance(json), 1e-6,
                "Parser must return the route-level 'distance', not the legs distance");
    }

    @Test
    void returns_route_level_duration_not_leg_duration() {
        String json =
                "{\"code\":\"Ok\","
                + "\"routes\":[{"
                + "\"legs\":[{\"steps\":[],\"summary\":\"\","
                + "\"weight\":100.0,\"duration\":99999.0,\"distance\":63412.5}],"
                + "\"weight_name\":\"routability\","
                + "\"weight\":3821.8,"
                + "\"duration\":3821.8,"
                + "\"distance\":63412.5"
                + "}],"
                + "\"waypoints\":[]}";

        assertEquals(3821.8, parser.extractDuration(json), 1e-6,
                "Parser must return the route-level 'duration', not the legs duration");
    }

    // ── Real-world shaped response ────────────────────────────────────────────

    @Test
    void handles_realistic_osrm_response_with_extra_fields() {
        String json =
                "{\"code\":\"Ok\","
                + "\"routes\":[{"
                + "\"geometry\":{\"coordinates\":[[34.78,32.08],[35.20,31.76]],"
                + "\"type\":\"LineString\"},"
                + "\"legs\":[{\"steps\":[],\"summary\":\"\",\"weight\":3821.8,"
                + "\"duration\":3821.8,\"distance\":63412.5}],"
                + "\"weight_name\":\"routability\","
                + "\"weight\":3821.8,"
                + "\"duration\":3821.8,"
                + "\"distance\":63412.5"
                + "}],"
                + "\"waypoints\":["
                + "{\"hint\":\"abc\",\"distance\":0.1,\"name\":\"Derech Begin\","
                + "\"location\":[34.78,32.08]},"
                + "{\"hint\":\"def\",\"distance\":0.2,\"name\":\"Jaffa Road\","
                + "\"location\":[35.20,31.76]}"
                + "]}";

        assertEquals(63412.5, parser.extractDistance(json), 1e-6);
        assertEquals(3821.8,  parser.extractDuration(json), 1e-6);
    }

    // ── Error cases ───────────────────────────────────────────────────────────

    @Test
    void returns_nan_when_code_is_not_ok() {
        String json = "{\"code\":\"NoRoute\",\"routes\":[],\"waypoints\":[]}";
        assertTrue(Double.isNaN(parser.extractDistance(json)));
        assertTrue(Double.isNaN(parser.extractDuration(json)));
    }

    @Test
    void returns_nan_for_null_input() {
        assertTrue(Double.isNaN(parser.extractDistance(null)));
        assertTrue(Double.isNaN(parser.extractDuration(null)));
    }

    @Test
    void returns_nan_for_blank_input() {
        assertTrue(Double.isNaN(parser.extractDistance("")));
        assertTrue(Double.isNaN(parser.extractDistance("   ")));
    }

    @Test
    void returns_nan_when_routes_array_is_empty() {
        String json = "{\"code\":\"Ok\",\"routes\":[],\"waypoints\":[]}";
        assertTrue(Double.isNaN(parser.extractDistance(json)));
    }

    @Test
    void returns_nan_when_distance_field_missing() {
        String json = "{\"code\":\"Ok\",\"routes\":[{\"duration\":32456.7}]}";
        assertTrue(Double.isNaN(parser.extractDistance(json)));
    }

    @Test
    void returns_nan_when_duration_field_missing() {
        String json = "{\"code\":\"Ok\",\"routes\":[{\"distance\":450123.4}]}";
        assertTrue(Double.isNaN(parser.extractDuration(json)));
    }

    @Test
    void returns_nan_for_completely_invalid_json() {
        assertTrue(Double.isNaN(parser.extractDistance("not json at all")));
        assertTrue(Double.isNaN(parser.extractDuration("{broken")));
    }

    // ── Zero values ───────────────────────────────────────────────────────────

    @Test
    void handles_zero_distance_and_duration() {
        String json =
                "{\"code\":\"Ok\",\"routes\":[{\"distance\":0.0,\"duration\":0.0}]}";
        assertEquals(0.0, parser.extractDistance(json), 1e-9);
        assertEquals(0.0, parser.extractDuration(json), 1e-9);
    }
}
