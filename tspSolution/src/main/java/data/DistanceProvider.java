package data;

import domain.City;

/**
 * Computes travel distance (metres) and travel time (seconds) between two
 * cities.
 *
 * <p>Each city type registers exactly one implementation in
 * {@link domain.CityRegistry}. Implementations must be stateless and
 * thread-safe.
 */
public interface DistanceProvider {

    /**
     * Returns the travel distance in metres between two cities.
     * Returns {@code 0} if {@code a == b}; returns {@link Double#NaN} only
     * if the computation is fundamentally impossible (e.g. both arguments are
     * {@code null}).
     */
    double distance(City a, City b);

    /**
     * Returns the travel time in seconds between two cities.
     * Returns {@code 0} if {@code a == b}; returns {@link Double#NaN} only
     * if the computation is fundamentally impossible.
     */
    double time(City a, City b);
}
