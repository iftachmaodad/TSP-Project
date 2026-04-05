package benchmark;

import domain.City;

import java.util.List;

/**
 * A named benchmark instance: a fixed set of cities and a designated start city.
 *
 * Immutable by construction — the list is copied defensively in TestInstanceLibrary
 * before being passed here, so callers receive a stable snapshot.
 *
 * @param <T>       the city type (AirCity, GroundCity, …)
 * @param name      human-readable instance name used in benchmark output
 * @param cities    all cities in the instance, including the start city
 * @param startCity the depot — must be an element of {@code cities}
 */
public record TestInstance<T extends City>(
        String name,
        List<T> cities,
        T startCity
) {
    /**
     * Compact constructor — validates that startCity is inside the list.
     */
    public TestInstance {
        if (name == null || name.isBlank())
            throw new IllegalArgumentException("Instance name must not be blank.");
        if (cities == null || cities.isEmpty())
            throw new IllegalArgumentException("City list must not be empty.");
        if (startCity == null)
            throw new IllegalArgumentException("Start city must not be null.");
        if (!cities.contains(startCity))
            throw new IllegalArgumentException(
                "Start city '" + startCity.getID() + "' is not in the city list.");
        // Defensive copy already done by TestInstanceLibrary; record the reference as-is.
    }

    /** Convenience: number of cities in the instance (including the start city). */
    public int size() { return cities.size(); }

    /** Number of non-start cities (the ones the solver must visit). */
    public int nonStartCount() { return cities.size() - 1; }
}
