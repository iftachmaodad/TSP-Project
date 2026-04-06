package data;

import domain.City;
import domain.CityRegistry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Pre-computed distance and travel-time matrix for a fixed set of cities.
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * Matrix<AirCity> m = new Matrix<>(AirCity.class);
 * m.addCity(city1);
 * m.addCity(city2);
 * boolean ok = m.populateMatrix();   // must return true before querying
 * double d = m.getDistance(0, 1);
 * }</pre>
 *
 * <h3>Design notes</h3>
 * <ul>
 *   <li>The matrix is populated lazily. Adding or removing a city after
 *       population invalidates it; call {@link #populateMatrix()} again.</li>
 *   <li>Symmetric filling: {@code d(i,j) == d(j,i)}, so only the upper
 *       triangle is computed, halving the number of provider calls.</li>
 *   <li>{@link #populateMatrix()} returns {@code false} and leaves the matrix
 *       invalid if the city set is empty or any computed value is non-finite.</li>
 *   <li>{@link #getCities()} returns the snapshot list used when the matrix
 *       was last populated (stable, index-consistent). If the matrix has never
 *       been populated it returns a copy of the current insertion-order set.</li>
 *   <li>Generic type parameter prevents mixing city types at compile time.</li>
 * </ul>
 *
 * @param <T> the concrete city subtype
 */
public final class Matrix<T extends City> {

    private final Class<T>         type;
    private final DistanceProvider provider;

    private final Set<T> cities = new LinkedHashSet<>();

    /**
     * Stable snapshot of {@link #cities} taken at the last successful
     * {@link #populateMatrix()} call. Index lookups are based on this list.
     */
    private List<T> snapshot = List.of();

    private double[][] distanceMatrix;
    private double[][] timeMatrix;

    // ── Constructor ───────────────────────────────────────────────────────────

    /**
     * Creates a new matrix for the given city type.
     *
     * @param type registered City subclass
     * @throws IllegalArgumentException if {@code type} is not registered in
     *                                  {@link domain.CityRegistry}
     */
    public Matrix(Class<T> type) {
        if (!CityRegistry.exists(type)) {
            throw new IllegalArgumentException(
                    "City type '" + type.getSimpleName()
                    + "' is not registered in CityRegistry.");
        }
        this.type     = type;
        this.provider = CityRegistry.getProvider(type);
    }

    // ── City management ───────────────────────────────────────────────────────

    /**
     * Adds a city. Invalidates the matrix if the city was not already present.
     * Silently ignores {@code null}.
     */
    public void addCity(T city) {
        if (city != null && cities.add(city)) {
            invalidate();
        }
    }

    /**
     * Removes a city. Invalidates the matrix if the city was present.
     */
    public void removeCity(T city) {
        if (cities.remove(city)) {
            invalidate();
        }
    }

    /** Removes all cities and invalidates the matrix. */
    public void clearCities() {
        cities.clear();
        invalidate();
    }

    // ── Population ────────────────────────────────────────────────────────────

    /**
     * Computes the distance and time matrices using the registered
     * {@link DistanceProvider}.
     *
     * <p>Returns {@code false} (and leaves the matrix invalid) if the city
     * set is empty or any provider call returns a non-finite value.
     *
     * @return {@code true} on success, {@code false} if population failed
     */
    public boolean populateMatrix() {
        if (cities.isEmpty()) return false;

        List<T> snap = new ArrayList<>(cities);
        int n = snap.size();

        double[][] dist = new double[n][n];
        double[][] time = new double[n][n];

        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                T a = snap.get(i);
                T b = snap.get(j);

                double d = provider.distance(a, b);
                double t = provider.time(a, b);

                if (!Double.isFinite(d) || !Double.isFinite(t)) {
                    System.err.println("[Matrix] WARNING: non-finite value between "
                            + a.getID() + " and " + b.getID()
                            + " (dist=" + d + ", time=" + t + "). Population failed.");
                    return false;
                }

                dist[i][j] = dist[j][i] = d;
                time[i][j] = time[j][i] = t;
            }
            dist[i][i] = 0;
            time[i][i] = 0;
        }

        this.snapshot       = Collections.unmodifiableList(snap);
        this.distanceMatrix = dist;
        this.timeMatrix     = time;
        return true;
    }

    /**
     * Clears computed matrices. {@link #populateMatrix()} must be called again
     * before any lookups.
     */
    public void invalidate() {
        distanceMatrix = null;
        timeMatrix     = null;
        snapshot       = List.of();
    }

    // ── Integrity ─────────────────────────────────────────────────────────────

    /**
     * Returns {@code true} if the matrix has been successfully populated and
     * its dimensions are consistent with the current city snapshot.
     */
    public boolean checkIntegrity() {
        if (distanceMatrix == null || timeMatrix == null) return false;
        if (snapshot.isEmpty()) return false;
        int n = snapshot.size();
        if (distanceMatrix.length != n || timeMatrix.length != n) return false;
        for (int i = 0; i < n; i++) {
            if (distanceMatrix[i] == null || distanceMatrix[i].length != n) return false;
            if (timeMatrix[i]     == null || timeMatrix[i].length     != n) return false;
        }
        return true;
    }

    // ── Lookups ───────────────────────────────────────────────────────────────

    /**
     * Returns the snapshot index of {@code city}, or {@code -1} if the city
     * is not found or the matrix has not been populated.
     */
    public int getIndexOf(City city) {
        if (city == null || snapshot.isEmpty()) return -1;
        return snapshot.indexOf(city);
    }

    /**
     * Returns the pre-computed distance in metres between cities at indices
     * {@code i} and {@code j}, or {@link Double#NaN} if the index is out of
     * bounds or the matrix has not been populated.
     */
    public double getDistance(int i, int j) {
        if (distanceMatrix == null) return Double.NaN;
        if (i < 0 || j < 0 || i >= distanceMatrix.length || j >= distanceMatrix.length)
            return Double.NaN;
        return distanceMatrix[i][j];
    }

    /**
     * Returns the pre-computed travel time in seconds between cities at
     * indices {@code i} and {@code j}, or {@link Double#NaN} on error.
     */
    public double getTime(int i, int j) {
        if (timeMatrix == null) return Double.NaN;
        if (i < 0 || j < 0 || i >= timeMatrix.length || j >= timeMatrix.length)
            return Double.NaN;
        return timeMatrix[i][j];
    }

    // ── Accessors ─────────────────────────────────────────────────────────────

    /** Number of cities currently tracked (whether or not the matrix is populated). */
    public int size() { return cities.size(); }

    public Class<T>         getType()          { return type; }
    public DistanceProvider getProvider()       { return provider; }
    public double[][]       getDistanceMatrix() { return distanceMatrix; }
    public double[][]       getTimeMatrix()     { return timeMatrix; }

    /**
     * Returns the city list in snapshot order (the order used for index
     * lookups). If the matrix has never been populated, returns a copy of the
     * current insertion-order set so callers always receive a non-null list.
     */
    public List<T> getCities() {
        return snapshot.isEmpty() ? new ArrayList<>(cities) : snapshot;
    }

    // ── toString ──────────────────────────────────────────────────────────────

    @Override
    public String toString() {
        if (cities.isEmpty()) {
            return "Matrix[" + type.getSimpleName() + "] : empty";
        }
        if (distanceMatrix == null) {
            return "Matrix[" + type.getSimpleName() + "] : not populated ("
                    + cities.size() + " cities)";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("=== Matrix (").append(type.getSimpleName()).append(") ===\n\n");
        sb.append("--- Distance (m) ---\n");
        appendTable(sb, distanceMatrix);
        sb.append("\n--- Time (s) ---\n");
        appendTable(sb, timeMatrix);
        return sb.toString();
    }

    private void appendTable(StringBuilder sb, double[][] data) {
        sb.append(String.format("%-15s", "[To ->]"));
        for (T c : snapshot) {
            String label = c.getID().length() > 10
                    ? c.getID().substring(0, 9) + "." : c.getID();
            sb.append(String.format("%-12s", label));
        }
        sb.append('\n');

        for (int i = 0; i < snapshot.size(); i++) {
            String rowId = snapshot.get(i).getID();
            if (rowId.length() > 14) rowId = rowId.substring(0, 13) + ".";
            sb.append(String.format("%-15s", rowId));
            for (int j = 0; j < snapshot.size(); j++) {
                double val = data[i][j];
                sb.append(Double.isNaN(val)
                        ? String.format("%-12s", "N/A")
                        : String.format("%-12.2f", val));
            }
            sb.append('\n');
        }
    }
}
