package domain;

import java.lang.reflect.Constructor;

/**
 * Creates City instances from a known subclass type, coordinates, and optional metadata.
 *
 * This factory exists so that UI classes (CityPanel) and test/benchmark code can
 * instantiate cities without duplicating reflection logic or depending on each other.
 *
 * All city subclasses must expose the canonical constructor:
 *   (String id, double x, double y, double deadline)
 *
 * If the id is blank or null, the City base class will auto-generate one.
 * If the deadline is null or non-positive, City.NO_DEADLINE is used.
 */
public final class CityFactory {

    private CityFactory() {
        throw new UnsupportedOperationException("CityFactory is a utility class.");
    }

    /**
     * Creates a city of the given type.
     *
     * @param type     registered City subclass (must be in CityRegistry)
     * @param id       optional name — null or blank triggers auto-naming
     * @param lon      longitude (x)
     * @param lat      latitude (y)
     * @param deadline optional deadline in seconds — null means no deadline
     * @return the new city instance, or null if construction fails
     */
    public static <T extends City> T create(Class<T> type,
                                            String id,
                                            double lon,
                                            double lat,
                                            Double deadline) {
        try {
            Constructor<T> ctor =
                type.getConstructor(String.class, double.class, double.class, double.class);
            double d = (deadline == null || deadline <= 0) ? City.NO_DEADLINE : deadline;
            return ctor.newInstance(id, lon, lat, d);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Convenience overload — no deadline.
     */
    public static <T extends City> T create(Class<T> type, String id, double lon, double lat) {
        return create(type, id, lon, lat, null);
    }

    /**
     * Parses a deadline string entered by the user.
     * Returns null if the string is blank or not a positive number.
     */
    public static Double parseDeadline(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            double d = Double.parseDouble(s.trim());
            return d > 0 ? d : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
