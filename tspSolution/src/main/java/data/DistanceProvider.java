package data;

import domain.City;

/**
 * Computes travel distance (meters) and travel time (seconds) between two cities.
 *
 * Each city type registers one implementation in CityRegistry.
 * Implementations must be stateless and thread-safe.
 */
public interface DistanceProvider {

    /**
     * Returns the travel distance in meters between two cities.
     * Returns 0 if a == b, NaN only if computation is fundamentally impossible.
     */
    double distance(City a, City b);

    /**
     * Returns the travel time in seconds between two cities.
     * Returns 0 if a == b, NaN only if computation is fundamentally impossible.
     */
    double time(City a, City b);
}
