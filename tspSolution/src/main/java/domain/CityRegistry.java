package domain;

import data.AirDistanceProvider;
import data.DistanceProvider;
import data.GroundDistanceProvider;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Central registry that maps each {@link City} subclass to its
 * {@link DistanceProvider}.
 *
 * <h3>Adding a new city type</h3>
 * <ol>
 *   <li>Create a {@link City} subclass.</li>
 *   <li>Create a {@link DistanceProvider} for it.</li>
 *   <li>Call {@link #register(Class, DistanceProvider)} before the application
 *       starts.</li>
 * </ol>
 * Nothing else in the codebase needs to change.
 *
 * <h3>Thread safety</h3>
 * The registry map is populated in a {@code static} initializer and then only
 * read, except via the explicit {@link #register} call. Callers that invoke
 * {@link #register} concurrently with reads are responsible for external
 * synchronisation.
 */
public final class CityRegistry {

    private static final Map<Class<? extends City>, DistanceProvider> REGISTRY =
            new HashMap<>();

    static {
        REGISTRY.put(AirCity.class,    AirDistanceProvider.INSTANCE);
        REGISTRY.put(GroundCity.class, GroundDistanceProvider.INSTANCE);
    }

    private CityRegistry() {
        throw new UnsupportedOperationException("CityRegistry is a utility class.");
    }

    /**
     * Registers a city type with its distance provider.
     * Overwrites any existing entry for the same type.
     *
     * @param type     the City subclass to register; must not be {@code null}
     * @param provider its distance/time provider; must not be {@code null}
     * @throws IllegalArgumentException if either argument is {@code null}
     */
    public static void register(Class<? extends City> type, DistanceProvider provider) {
        if (type == null || provider == null)
            throw new IllegalArgumentException("Type and provider must not be null.");
        REGISTRY.put(type, provider);
    }

    /**
     * Returns {@code true} if the given city type is registered.
     */
    public static boolean exists(Class<? extends City> type) {
        return REGISTRY.containsKey(type);
    }

    /**
     * Returns the {@link DistanceProvider} for the given city type, or
     * {@code null} if the type is not registered.
     */
    public static DistanceProvider getProvider(Class<? extends City> type) {
        return REGISTRY.get(type);
    }

    /**
     * Returns {@code true} if the given string starts with any registered city
     * type name (case-insensitive). Used to prevent user-supplied city IDs from
     * clashing with auto-generated names.
     */
    public static boolean startsWithIgnoreCase(String input) {
        if (input == null) return false;
        String lower = input.toLowerCase();
        for (Class<? extends City> type : REGISTRY.keySet()) {
            if (lower.startsWith(type.getSimpleName().toLowerCase())) return true;
        }
        return false;
    }

    /**
     * Returns an unmodifiable view of all registered city types.
     */
    public static Set<Class<? extends City>> getRegisteredTypes() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }
}
