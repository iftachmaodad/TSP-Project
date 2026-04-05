package data;

/**
 * Parses the JSON response from the OSRM routing API without an external
 * JSON library, keeping the dependency footprint minimal.
 *
 * <h3>Target format</h3>
 * <pre>{@code
 * {
 *   "code": "Ok",
 *   "routes": [
 *     {
 *       "legs": [ { "distance": X, "duration": X, ... } ],
 *       "distance": 450123.4,   ← top-level route distance (metres)
 *       "duration": 32456.7     ← top-level route duration (seconds)
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <h3>Key implementation note</h3>
 * The {@code "distance"} key appears twice inside {@code routes[0]}: once
 * inside the nested {@code legs} array and once at the route's top level.
 * For a single-pair request the values are identical, but to be correct and
 * future-proof the parser finds the <em>closing</em> brace of the
 * {@code legs} array before searching for the top-level field. This ensures
 * the legs values are never mistakenly returned.
 *
 * <h3>Return value</h3>
 * Both {@link #extractDistance} and {@link #extractDuration} return
 * {@link Double#NaN} on any parse failure or non-{@code "Ok"} response code.
 *
 * <p>Package-private — only {@link GroundDistanceProvider} and the test suite
 * should use this class directly.
 */
final class OsrmParser {

    /** Package-private singleton — stateless. */
    static final OsrmParser INSTANCE = new OsrmParser();

    private OsrmParser() {}

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Extracts the top-level route distance in metres from an OSRM response.
     *
     * @param json raw OSRM JSON string
     * @return distance in metres, or {@link Double#NaN} on failure
     */
    double extractDistance(String json) {
        return extractTopLevelField(json, "distance");
    }

    /**
     * Extracts the top-level route duration in seconds from an OSRM response.
     *
     * @param json raw OSRM JSON string
     * @return duration in seconds, or {@link Double#NaN} on failure
     */
    double extractDuration(String json) {
        return extractTopLevelField(json, "duration");
    }

    // ── Parsing ───────────────────────────────────────────────────────────────

    /**
     * Core extraction logic.
     *
     * <ol>
     *   <li>Verify {@code "code":"Ok"}.</li>
     *   <li>Locate the {@code routes} array.</li>
     *   <li>Find the first route object {@code { … }}.</li>
     *   <li>Skip past the {@code legs} array so its nested keys are not
     *       mistakenly matched.</li>
     *   <li>Read the first occurrence of {@code field} after the legs.</li>
     * </ol>
     */
    private double extractTopLevelField(String json, String field) {
        if (json == null || json.isBlank()) return Double.NaN;

        // 1. Require "Ok" response code.
        if (!json.contains("\"Ok\"")) return Double.NaN;

        // 2. Find the routes array.
        int routesIdx = json.indexOf("\"routes\"");
        if (routesIdx < 0) return Double.NaN;

        // 3. Find the first route object.
        int objStart = json.indexOf('{', routesIdx);
        if (objStart < 0) return Double.NaN;

        int objEnd = findMatchingBrace(json, objStart);
        if (objEnd < 0) return Double.NaN;

        String routeObj = json.substring(objStart, objEnd + 1);

        // 4. Skip past the "legs" array so we read the route-level field,
        //    not the per-leg field of the same name.
        int searchFrom = skipLegsArray(routeObj);

        // 5. Extract the numeric value from the remaining string.
        String tail = routeObj.substring(searchFrom);
        return extractNumber(tail, field);
    }

    /**
     * Returns the index in {@code routeObj} just past the closing bracket of
     * the {@code "legs"} array, or {@code 0} if no legs array is found.
     *
     * <p>This prevents the parser from picking up distance/duration values
     * that live inside the legs sub-objects rather than at the route level.
     */
    private static int skipLegsArray(String routeObj) {
        int legsIdx = routeObj.indexOf("\"legs\"");
        if (legsIdx < 0) return 0;

        // Find the '[' that opens the legs array.
        int arrStart = routeObj.indexOf('[', legsIdx);
        if (arrStart < 0) return 0;

        // Walk forward counting brackets to find the matching ']'.
        int depth = 0;
        for (int i = arrStart; i < routeObj.length(); i++) {
            char c = routeObj.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return i + 1; // first position after legs array
            }
        }
        return 0;
    }

    /**
     * Returns the index of the closing {@code '}'} that matches the
     * {@code '{'} at {@code openIdx}, or {@code -1} if unmatched.
     */
    private static int findMatchingBrace(String s, int openIdx) {
        int depth = 0;
        for (int i = openIdx; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '{')      depth++;
            else if (c == '}') { if (--depth == 0) return i; }
        }
        return -1;
    }

    /**
     * Finds the first occurrence of {@code "key": value} in {@code obj} and
     * parses {@code value} as a double. Returns {@link Double#NaN} if the key
     * is absent or the value is not numeric.
     */
    private static double extractNumber(String obj, String key) {
        String search = "\"" + key + "\"";
        int ki = obj.indexOf(search);
        if (ki < 0) return Double.NaN;

        int colon = obj.indexOf(':', ki + search.length());
        if (colon < 0) return Double.NaN;

        // Skip whitespace after the colon.
        int vi = colon + 1;
        while (vi < obj.length() && Character.isWhitespace(obj.charAt(vi))) vi++;
        if (vi >= obj.length()) return Double.NaN;

        // Read until a value-terminating character.
        int end = vi;
        while (end < obj.length()) {
            char c = obj.charAt(end);
            if (c == ',' || c == '}' || c == ']' || Character.isWhitespace(c)) break;
            end++;
        }

        try {
            return Double.parseDouble(obj.substring(vi, end).trim());
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
