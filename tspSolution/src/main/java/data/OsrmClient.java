package data;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/**
 * Minimal HTTP client for the OSRM public routing API.
 *
 * Endpoint:
 *   http://router.project-osrm.org/route/v1/driving/{lon1},{lat1};{lon2},{lat2}?overview=false
 *
 * Returns the raw JSON response string, or null if the request fails for any
 * reason (network unavailable, timeout, non-200 status, etc.).
 *
 * The caller (GroundDistanceProvider) handles null by falling back to the
 * Haversine approximation — the app never crashes without internet.
 *
 * OSRM usage policy:
 *   The public demo server is for development/testing only.
 *   Do not use it for production traffic or automated bulk requests.
 *   See: http://project-osrm.org/docs/v5.22.0/api/
 *
 * This class is package-private — only GroundDistanceProvider uses it.
 */
final class OsrmClient {

    private static final String BASE_URL =
        "http://router.project-osrm.org/route/v1/driving/%f,%f;%f,%f?overview=false";

    private static final String USER_AGENT  = "TSP-Solver/1.0 (student project)";
    private static final int    TIMEOUT_MS  = 6_000;

    // Package-private singleton — stateless
    static final OsrmClient INSTANCE = new OsrmClient();

    private OsrmClient() {}

    /**
     * Requests the driving route between two lon/lat points from OSRM.
     *
     * @param lon1 longitude of the first point
     * @param lat1 latitude  of the first point
     * @param lon2 longitude of the second point
     * @param lat2 latitude  of the second point
     * @return raw JSON response string, or null on any error
     */
    String fetch(double lon1, double lat1, double lon2, double lat2) {
        try {
            String url = String.format(BASE_URL, lon1, lat1, lon2, lat2);
            URI uri = URI.create(url);

            HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);

            if (conn.getResponseCode() != 200) return null;

            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line);
            }
            return sb.toString();

        } catch (Exception e) {
            return null; // network unavailable, timeout, etc.
        }
    }
}
