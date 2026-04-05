package domain;

import data.AirDistanceProvider;
import data.DistanceProvider;
import data.GroundDistanceProvider;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Central registry that maps each City subclass to its DistanceProvider.
 *
 * To add a new city type:
 *   1. Create your City subclass.
 *   2. Create a DistanceProvider implementation for it.
 *   3. Call CityRegistry.register(YourCity.class, YourProvider.INSTANCE)
 *      in a static initializer or before the app starts.
 *
 * Nothing else in the codebase needs to change.
 */
public final class CityRegistry {

    // --- Registry ---
    private static final Map<Class<? extends City>, DistanceProvider> REGISTRY = new HashMap<>();

    static {
        REGISTRY.put(AirCity.class,    AirDistanceProvider.INSTANCE);
        REGISTRY.put(GroundCity.class, GroundDistanceProvider.INSTANCE);
    }

    // --- Constructor (utility class, not instantiable) ---
    private CityRegistry() {
        throw new UnsupportedOperationException("CityRegistry is a utility class.");
    }

    // --- Methods ---

    /** Registers a city type with its distance provider. Overwrites any existing entry. */
    public static void register(Class<? extends City> type, DistanceProvider provider) {
        if (type == null || provider == null) throw new IllegalArgumentException("Type and provider must not be null.");
        REGISTRY.put(type, provider);
    }

    /** Returns true if the given city type is registered. */
    public static boolean exists(Class<? extends City> type) {
        return REGISTRY.containsKey(type);
    }

    /**
     * Returns the DistanceProvider for the given city type.
     * Returns null if the type is not registered.
     */
    public static DistanceProvider getProvider(Class<? extends City> type) {
        return REGISTRY.get(type);
    }

    /**
     * Returns true if the given string starts with any registered city type name (case-insensitive).
     * Used to prevent user-supplied city IDs from clashing with auto-generated names.
     */
    public static boolean startsWithIgnoreCase(String input) {
        if (input == null) return false;
        String lower = input.toLowerCase();
        for (Class<? extends City> type : REGISTRY.keySet()) {
            if (lower.startsWith(type.getSimpleName().toLowerCase())) return true;
        }
        return false;
    }

    /** Returns an unmodifiable view of all registered city types. */
    public static Set<Class<? extends City>> getRegisteredTypes() {
        return Collections.unmodifiableSet(REGISTRY.keySet());
    }
}
