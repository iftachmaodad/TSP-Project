package ui;

/**
 * Represents a named place shown as an overlay pin on the map.
 *
 * <p>Used internally between {@link PlaceSearchService} and
 * {@link UiController}. Clicking a pin pre-fills the city name and
 * coordinates in the city panel; the user then confirms by pressing
 * "Add City".
 *
 * @param label      display name of the place
 * @param lon        longitude
 * @param lat        latitude
 * @param importance 0.0–1.0 relevance score used to size and filter pins
 * @param placeType  one of: "capital", "city", "town", "village", "airport", "other"
 */
public record MapMarker(String label, double lon, double lat,
                        double importance, String placeType) {

    /** Convenience constructor with default importance and type. */
    public MapMarker(String label, double lon, double lat) {
        this(label, lon, lat, 0.5, "other");
    }

    public MapMarker(String label, double lon, double lat, double importance) {
        this(label, lon, lat, importance, "other");
    }

    public MapMarker {
        if (label == null || label.isBlank()) label = "?";
        else                                  label = label.trim();
        if (lon < -180 || lon > 180)
            throw new IllegalArgumentException("Longitude out of range: " + lon);
        if (lat < -90 || lat > 90)
            throw new IllegalArgumentException("Latitude out of range: " + lat);
        importance = Math.max(0.0, Math.min(1.0, importance));
        if (placeType == null) placeType = "other";
    }

    public boolean isCapital()  { return "capital".equals(placeType); }
    public boolean isCity()     { return "city".equals(placeType) || "capital".equals(placeType); }
    public boolean isAirport()  { return "airport".equals(placeType); }
}
