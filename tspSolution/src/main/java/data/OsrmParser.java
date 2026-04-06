package data;

/**
 * Parses the JSON response from the OSRM routing API without an external
 * JSON library.
 *
 * <h3>Target format</h3>
 * <pre>{@code
 * {
 *   "code": "Ok",
 *   "routes": [
 *     {
 *       "legs": [ { "distance": X, "duration": X, … } ],
 *       "distance": 450123.4,
 *       "duration": 32456.7
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <h3>Legs-array disambiguation</h3>
 * The {@code "distance"} and {@code "duration"} keys each appear twice inside
 * {@code routes[0]}: once inside the nested {@code legs} array and once at the
 * route's top level. The parser locates the closing bracket of the {@code legs}
 * array before searching for the top-level fields, ensuring the per-leg values
 * are never returned.
 *
 * <h3>Return value</h3>
 * Both {@link #extractDistance} and {@link #extractDuration} return
 * {@link Double#NaN} on any parse failure or non-{@code "Ok"} response code.
 *
 * <h3>Thread safety</h3>
 * Stateless singleton; safe from any thread.
 */
final class OsrmParser {

    static final OsrmParser INSTANCE = new OsrmParser();

    private OsrmParser() {}

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

    private double extractTopLevelField(String json, String field) {
        if (json == null || json.isBlank()) return Double.NaN;

        if (!json.contains("\"Ok\"")) return Double.NaN;

        int routesIdx = json.indexOf("\"routes\"");
        if (routesIdx < 0) return Double.NaN;

        int objStart = json.indexOf('{', routesIdx);
        if (objStart < 0) return Double.NaN;

        int objEnd = findMatchingBrace(json, objStart);
        if (objEnd < 0) return Double.NaN;

        String routeObj = json.substring(objStart, objEnd + 1);

        // Skip past the legs array so route-level fields are read, not leg fields.
        int searchFrom = skipLegsArray(routeObj);

        return extractNumber(routeObj.substring(searchFrom), field);
    }

    /**
     * Returns the index in {@code routeObj} just past the closing bracket of
     * the {@code "legs"} array, or {@code 0} if no legs array is found.
     */
    private static int skipLegsArray(String routeObj) {
        int legsIdx = routeObj.indexOf("\"legs\"");
        if (legsIdx < 0) return 0;

        int arrStart = routeObj.indexOf('[', legsIdx);
        if (arrStart < 0) return 0;

        int depth = 0;
        for (int i = arrStart; i < routeObj.length(); i++) {
            char c = routeObj.charAt(i);
            if (c == '[') depth++;
            else if (c == ']') {
                depth--;
                if (depth == 0) return i + 1;
            }
        }
        return 0;
    }

    /**
     * Returns the index of the closing {@code '}'} matching the {@code '{'}
     * at {@code openIdx}, or {@code -1} if unmatched.
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
     * Finds the first {@code "key": value} occurrence in {@code obj} and
     * parses {@code value} as a {@code double}. Returns {@link Double#NaN}
     * if the key is absent or the value is not numeric.
     */
    private static double extractNumber(String obj, String key) {
        String search = "\"" + key + "\"";
        int ki = obj.indexOf(search);
        if (ki < 0) return Double.NaN;

        int colon = obj.indexOf(':', ki + search.length());
        if (colon < 0) return Double.NaN;

        int vi = colon + 1;
        while (vi < obj.length() && Character.isWhitespace(obj.charAt(vi))) vi++;
        if (vi >= obj.length()) return Double.NaN;

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
