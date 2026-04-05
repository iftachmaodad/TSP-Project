package ui;

/**
 * Represents a place returned by Nominatim reverse geocoding.
 *
 * Used only internally between PlaceSearchService and UiController —
 * no separate pin overlay is drawn on the map. The user clicks directly
 * on the tile's city dot; we reverse geocode to find out what they clicked.
 *
 * @param label      Display name (shortened from Nominatim display_name)
 * @param lon        Longitude
 * @param lat        Latitude
 * @param importance 0.0–1.0 from Nominatim — used to size added-city markers
 * @param placeType  "capital", "city", "town", "village", "airport", "other"
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
