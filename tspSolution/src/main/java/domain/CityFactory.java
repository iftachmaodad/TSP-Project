package domain;

import java.lang.reflect.Constructor;

/**
 * Creates {@link City} instances from a known subclass type, coordinates,
 * and optional metadata.
 *
 * <p>This factory exists so that UI classes and benchmark code can instantiate
 * cities without duplicating reflection logic or depending on each other.
 *
 * <p>All city subclasses must expose the canonical constructor:
 * {@code (String id, double x, double y, double deadline)}.
 */
public final class CityFactory {

    private CityFactory() {
        throw new UnsupportedOperationException("CityFactory is a utility class.");
    }

    /**
     * Creates a city of the given type.
     *
     * @param type     registered City subclass (must be in CityRegistry)
     * @param id       optional display name — {@code null} or blank triggers auto-naming
     * @param lon      longitude (x)
     * @param lat      latitude (y)
     * @param deadline arrival deadline in seconds; {@code null} or non-positive
     *                 means no deadline
     * @return the new city instance, or {@code null} if construction fails
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
     *
     * @param s the raw input string
     * @return the deadline value in seconds, or {@code null} if the string is
     *         blank, non-numeric, zero, or negative
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
