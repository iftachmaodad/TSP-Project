package ui;

import javafx.animation.PauseTransition;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import domain.City;

/**
 * Interactive map canvas.
 *
 * <p>The user interacts directly with the tile map. Clicking an overlay pin
 * pre-fills the city panel with the pin's name and coordinates. Double-clicking
 * empty space places a raw coordinate marker.
 *
 * <p>Online mode:  click overlay pin → pre-fill city panel with named place
 * <br>Offline mode: double-click → raw coordinate fallback
 *
 * Added city markers are drawn with importance-based sizing:
 *   capital   → gold star shape
 *   city      → large filled circle
 *   town      → medium filled circle
 *   village   → small filled circle
 *   other     → plain dot
 *
 * Coordinate system: Web Mercator (matches OSM/ESRI tiles exactly).
 *
 * <h3>Tile loading UI</h3>
 * {@link #setTileFetchListeners(Runnable, Runnable)} forwards to {@link OsmTileLayer}
 * so {@link UiController} can drive the map loading spinner while tiles fetch during pan/zoom.
 *
 * <h3>Offline mode</h3>
 * {@code world.jpg} is equirectangular; when tiles are off, cities, routes, overlay pins,
 * and the pending crosshair use the same equirectangular screen mapping as
 * {@link #drawBackground} so markers stay aligned while panning.
 */
public final class MapViewPane extends StackPane {

    // --- Canvas ---
    private final Canvas canvas = new Canvas();

    // --- Tile layer ---
    private final OsmTileLayer tileLayer = new OsmTileLayer();
    private boolean useTiles = true;

    // --- Data ---
    private List<City>  cities    = new ArrayList<>();
    private List<City>  route      = null;
    private boolean     routeValid = true;
    private City        startCity  = null;
    private boolean     showLabels = true;

    // --- Overlay markers ---
    private List<MapMarker> overlayMarkers  = new ArrayList<>();
    private MapMarker       selectedMarker  = null;
    private MapMarker       hoveredMarker   = null;

    // Selected added city (highlight synced with crosshair/pin deselect)
    private City            selectedCity    = null;

    // Pending marker
    private Double pendingLon = null;
    private Double pendingLat = null;

    // --- World / camera (Web Mercator, square world) ---
    private static final double WORLD_W = 2048.0;
    private static final double WORLD_H = 2048.0;

    private double camX = WORLD_W / 2.0;
    private double camY = WORLD_H / 2.0;
    private double zoom = 1.0;
    private static final double MIN_ZOOM =  1.0;
    private static final double MAX_ZOOM = 50.0;
    private boolean wrapX = true;

    // --- Drag ---
    private boolean panning         = false;
    private double  pressX, pressY;
    private double  dragStartCamX, dragStartCamY;
    private boolean movedWhilePress = false;
    private static final double CLICK_THRESHOLD_PX = 8.0;

    // --- Callbacks ---
    private BiConsumer<Double, Double> onMapClick;
    private Consumer<MapMarker>        onMarkerClick;
    private Consumer<City>             onCityClick;
    private Runnable                   onViewportChanged;
    private Runnable                   onDeselect; // fired on drag, scroll, single-click-empty

    // Debounce: fires onViewportChanged 400ms after panning/zooming stops
    private final PauseTransition viewportDebounce =
        new PauseTransition(Duration.millis(400));

    // --- Fonts ---
    private static final Font MARKER_FONT = Font.font("SansSerif", 11);
    private static final Font LABEL_FONT  = Font.font("SansSerif", 11);
    private static final Font ATTR_FONT   = Font.font("SansSerif", 10);

    // --- Tooltip ---
    private final Tooltip hoverTip   = new Tooltip();
    private boolean       tipShowing = false;

    // ══════════════════════════════════════════════════════════
    // Constructor
    // ══════════════════════════════════════════════════════════

    public MapViewPane() {
        getChildren().add(canvas);

        // Allow this pane to grow to fill whatever container it's placed in
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        widthProperty() .addListener((o, ov, nv) -> { canvas.setWidth(nv.doubleValue());  redraw(); });
        heightProperty().addListener((o, ov, nv) -> { canvas.setHeight(nv.doubleValue()); redraw(); });

        tileLayer.setOnTileLoaded(this::redraw);

        viewportDebounce.setOnFinished(e -> {
            if (onViewportChanged != null) onViewportChanged.run();
        });

        hookScrollZoom();
        hookMouseEvents();
        normalizeCamera();
        redraw();
    }

    // ══════════════════════════════════════════════════════════
    // Public API
    // ══════════════════════════════════════════════════════════

    public void setOnMapClick(BiConsumer<Double, Double> h)  { this.onMapClick       = h; }
    public void setOnMarkerClick(Consumer<MapMarker> h)      { this.onMarkerClick    = h; }
    public void setOnCityClick(Consumer<City> h)             { this.onCityClick      = h; }
    public void setOnViewportChanged(Runnable h)             { this.onViewportChanged = h; }
    public void setOnDeselect(Runnable h)                    { this.onDeselect       = h; }

    /**
     * Invoked on the JavaFX thread when satellite tile fetches start / finish
     * (only while {@link #isTilesEnabled()} is true).
     */
    public void setTileFetchListeners(Runnable onBegin, Runnable onEnd) {
        tileLayer.setTileFetchListeners(onBegin, onEnd);
    }
    public void setShowLabels(boolean show)                  { this.showLabels = show; redraw(); }
    public void setWrapX(boolean wrap)                       { this.wrapX = wrap; normalizeCamera(); redraw(); }
    public void setUseTiles(boolean use) {
        this.useTiles = use;
        normalizeCamera();
        redraw();
    }
    public boolean isTilesEnabled() { return useTiles; }

    public void setOverlayMarkers(List<MapMarker> markers) {
        this.overlayMarkers = (markers == null) ? new ArrayList<>() : new ArrayList<>(markers);
        // Preserve the selection if the same place is still in the new list
        if (selectedMarker != null) {
            boolean stillPresent = false;
            for (MapMarker m : this.overlayMarkers) {
                double dx = m.lon() - selectedMarker.lon();
                double dy = m.lat() - selectedMarker.lat();
                if (dx * dx + dy * dy < 1e-6) { stillPresent = true; break; }
            }
            if (!stillPresent) selectedMarker = null;
        }
        redraw();
    }

    public List<MapMarker> getOverlayMarkers() {
        return new ArrayList<>(overlayMarkers);
    }

    public void selectOverlayMarkerAt(double lon, double lat) {
        for (MapMarker m : overlayMarkers) {
            double dx = m.lon() - lon, dy = m.lat() - lat;
            if (dx*dx + dy*dy < 1e-6) {
                selectedMarker = m;
                redraw();
                return;
            }
        }
    }

    /** Programmatically deselects the selected overlay marker (pass null to clear). */
    public void setSelectedOverlayMarker(MapMarker m) {
        selectedMarker = m;
        redraw();
    }

    /** Highlights an added city marker. Pass null to clear. Cleared by drag/scroll/click-empty. */
    public void setSelectedCity(City c) {
        selectedCity = c;
        redraw();
    }

    public void setCities(List<City> list) {
        this.cities = (list == null) ? new ArrayList<>() : new ArrayList<>(list);
        // Clear city selection if the selected city is no longer in the list
        if (selectedCity != null && !this.cities.contains(selectedCity)) selectedCity = null;
        redraw();
    }

    public void setRoute(List<City> path, boolean valid) {
        this.route      = path;
        this.routeValid = valid;
        redraw();
    }

    /** Convenience overload — assumes valid route (used when clearing with null). */
    public void setRoute(List<City> path) { setRoute(path, true); }

    public void setStartCity(City s)       { this.startCity = s; redraw(); }

    public void setPendingMarker(double lon, double lat) {
        this.pendingLon = lon; this.pendingLat = lat; redraw();
    }

    public void clearPendingMarker() {
        this.pendingLon = null; this.pendingLat = null; redraw();
    }

    public void centreOn(double lon, double lat) {
        camX = lonToWorldX(lon);
        camY = latToWorldY(lat);
        normalizeCamera();
        redraw();
    }

    public void fitBounds(List<? extends City> cityList, double paddingFraction) {
        if (cityList == null || cityList.isEmpty()) return;

        double minLon = cityList.stream().mapToDouble(City::getX).min().orElse(-180);
        double maxLon = cityList.stream().mapToDouble(City::getX).max().orElse(180);
        double minLat = cityList.stream().mapToDouble(City::getY).min().orElse(-85);
        double maxLat = cityList.stream().mapToDouble(City::getY).max().orElse(85);

        camX = lonToWorldX((minLon + maxLon) / 2.0);
        camY = latToWorldY((minLat + maxLat) / 2.0);

        double spanWorldX = lonToWorldX(maxLon) - lonToWorldX(minLon);
        double spanWorldY = latToWorldY(minLat)  - latToWorldY(maxLat);

        double vw = getWidth();  if (vw <= 0) vw = 1280;
        double vh = getHeight(); if (vh <= 0) vh = 800;

        double zoomX = (vw  / Math.max(spanWorldX, 1)) / (1.0 + paddingFraction);
        double zoomY = (vh / Math.max(spanWorldY, 1)) / (1.0 + paddingFraction);
        zoom = clamp(Math.min(zoomX, zoomY), MIN_ZOOM, MAX_ZOOM);

        normalizeCamera();
        redraw();
    }

    public double[] getVisibleBounds() {
        double vw = getWidth(), vh = getHeight();
        if (vw <= 0) vw = 1280;
        if (vh <= 0) vh = 800;

        double west  = worldXToLon(mod(screenToWorldX(0,  vw), WORLD_W));
        double east  = worldXToLon(mod(screenToWorldX(vw, vw), WORLD_W));
        double north = worldYToLat(clamp(screenToWorldY(0,  vh), 0, WORLD_H));
        double south = worldYToLat(clamp(screenToWorldY(vh, vh), 0, WORLD_H));

        return new double[]{ west, south, east, north };
    }

    // ══════════════════════════════════════════════════════════
    // Input
    // ══════════════════════════════════════════════════════════

    private void hookScrollZoom() {
        setOnScroll(e -> {
            double vw = getWidth(), vh = getHeight();
            if (vw <= 0 || vh <= 0) return;

            double wbx = screenToWorldX(e.getX(), vw);
            double wby = screenToWorldY(e.getY(), vh);

            zoom = clamp(zoom * (e.getDeltaY() > 0 ? 1.25 : 1.0 / 1.25), MIN_ZOOM, MAX_ZOOM);

            camX += wbx - screenToWorldX(e.getX(), vw);
            camY += wby - screenToWorldY(e.getY(), vh);

            // Clear crosshair on zoom
            pendingLon = null; pendingLat = null;
            selectedCity = null;
            if (onDeselect != null) onDeselect.run();

            normalizeCamera();
            redraw();
            viewportDebounce.playFromStart();
        });
    }

    private void hookMouseEvents() {
        setOnMousePressed(e -> {
            pressX = e.getX(); pressY = e.getY();
            movedWhilePress = false;
            if (e.getButton() == MouseButton.PRIMARY) {
                panning = true;
                dragStartCamX = camX;
                dragStartCamY = camY;
            }
        });

        setOnMouseDragged(e -> {
            double dx = e.getX() - pressX, dy = e.getY() - pressY;
            if (dx * dx + dy * dy > CLICK_THRESHOLD_PX * CLICK_THRESHOLD_PX)
                movedWhilePress = true;
            if (!panning) return;
            // Clear crosshair when user starts panning
            if (pendingLon != null) { pendingLon = null; pendingLat = null; }
            if (movedWhilePress) { selectedCity = null; if (onDeselect != null) onDeselect.run(); }
            camX = dragStartCamX - dx / zoom;
            camY = dragStartCamY - dy / zoom;
            normalizeCamera();
            redraw();
        });

        setOnMouseReleased(e -> {
            panning = false;
            // Only refresh overlay pins when the viewport actually moved.
            // Firing the debounce on a plain pin click would clear and reload
            // pins 350 ms later even though the camera had not changed.
            if (movedWhilePress) viewportDebounce.playFromStart();
        });

        // JavaFX fires clickCount=1 before clickCount=2 on a double-click,
            // which means the single-click handler (deselect) runs immediately before
            // the double-click handler (place crosshair). We suppress the deselect
            // when clickCount==2 so the two handlers do not conflict.
        setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY || movedWhilePress) return;
            double vw = getWidth(), vh = getHeight();
            if (vw <= 0 || vh <= 0) return;

            // Check overlay marker pins first
            MapMarker hit = findNearestMarker(e.getX(), e.getY(), 25.0);
            if (hit != null) {
                selectedMarker = hit;
                pendingLon = null; pendingLat = null;
                redraw();
                if (onMarkerClick != null) onMarkerClick.accept(hit);
                return;
            }

            // Check added cities
            City nearCity = findNearestCity(e.getX(), e.getY(), 20.0);
            if (nearCity != null) {
                selectedCity = nearCity;
                redraw();
                if (onCityClick != null) onCityClick.accept(nearCity);
                return;
            }

            if (e.getClickCount() == 2) {
                // Double-click on empty space → place crosshair.
                // In tile mode: use Mercator world ↔ lon/lat.
                // In offline mode: the world.jpg is equirectangular (linear lat),
                // so we must invert that formula instead of the Mercator one.
                double lon, lat;
                if (useTiles) {
                    double wx = screenToWorldX(e.getX(), vw);
                    double wy = screenToWorldY(e.getY(), vh);
                    if (wrapX) wx = mod(wx, WORLD_W);
                    wy = clamp(wy, 0, WORLD_H);
                    lon = worldXToLon(wx);
                    lat = worldYToLat(wy);
                } else {
                    lon = screenToLonEquirectangular(e.getX(), vw);
                    lat = screenToLatEquirectangular(e.getY(), vh);
                }
                if (onMapClick != null) onMapClick.accept(lon, lat);
            } else if (e.getClickCount() == 1) {
                // Single click on empty space — deselect everything.
                // Skip if this is the first of a double-click (JavaFX delivers
                // count=1 before count=2; we'll handle it on the count=2 event).
                pendingLon = null; pendingLat = null;
                selectedCity = null;
                if (onDeselect != null) onDeselect.run();
                redraw();
            }
        });

        setOnMouseMoved(e -> {
            // Track hovered overlay marker for highlight effect
            MapMarker nearPin = findNearestMarker(e.getX(), e.getY(), 25.0);
            if (nearPin != hoveredMarker) {
                hoveredMarker = nearPin;
                redraw();
            }

            // Tooltip for added cities
            City nearest = findNearestCity(e.getX(), e.getY(), 14.0);
            if (nearest == null) {
                // Show tooltip for hovered pin instead
                if (nearPin != null) {
                    hoverTip.setText(nearPin.label() +
                        String.format("%n%.4f°, %.4f°", nearPin.lon(), nearPin.lat()));
                    if (!tipShowing) { hoverTip.show(this, e.getScreenX() + 14, e.getScreenY() + 14); tipShowing = true; }
                    else { hoverTip.setAnchorX(e.getScreenX() + 14); hoverTip.setAnchorY(e.getScreenY() + 14); }
                } else {
                    if (tipShowing) { hoverTip.hide(); tipShowing = false; }
                }
                return;
            }
            StringBuilder sb = new StringBuilder(nearest.toString());
            if (nearest.hasDeadline())
                sb.append(String.format("%nDeadline: %.0f s", nearest.getDeadline()));
            hoverTip.setText(sb.toString());
            if (!tipShowing) { hoverTip.show(this, e.getScreenX() + 14, e.getScreenY() + 14); tipShowing = true; }
            else { hoverTip.setAnchorX(e.getScreenX() + 14); hoverTip.setAnchorY(e.getScreenY() + 14); }
        });

        setOnMouseExited(e -> {
            if (tipShowing) { hoverTip.hide(); tipShowing = false; }
            if (hoveredMarker != null) { hoveredMarker = null; redraw(); }
        });
    }

    // ══════════════════════════════════════════════════════════
    // Drawing
    // ══════════════════════════════════════════════════════════

    private void redraw() {
        double vw = canvas.getWidth(), vh = canvas.getHeight();
        if (vw <= 0 || vh <= 0) return;

        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, vw, vh);

        drawBackground(g, vw, vh);
        drawRoute(g, vw, vh);
        drawOverlayMarkers(g, vw, vh);
        drawPendingMarker(g, vw, vh);
        drawCities(g, vw, vh);
        if (useTiles) drawAttribution(g, vw, vh);
    }

    // --- Static fallback map image (used when tiles are disabled) ---
    private static final javafx.scene.image.Image WORLD_IMG = loadWorldMap();

    private static javafx.scene.image.Image loadWorldMap() {
        try {
            var url = MapViewPane.class.getResource("/images/world.jpg");
            return url != null ? new javafx.scene.image.Image(url.toExternalForm()) : null;
        } catch (Exception e) { return null; }
    }

    private void drawBackground(GraphicsContext g, double vw, double vh) {
        if (useTiles) {
            tileLayer.draw(g, vw, vh, worldXToLon(camX), worldYToLat(camY), zoom);
        } else {
            // Offline fallback: draw world.jpg static map
            g.setFill(Color.rgb(30, 60, 100));
            g.fillRect(0, 0, vw, vh);

            if (WORLD_IMG != null && !WORLD_IMG.isError()) {
                // world.jpg is equirectangular (2:1): lon -180→+180 linear X, lat +90→-90 linear Y.
                // The image spans the FULL world width in screen pixels = WORLD_W * zoom.
                double worldScreenW = WORLD_W * zoom;
                double imgW = worldScreenW;
                double imgH = worldScreenW / 2.0; // 2:1 aspect ratio

                // X: left edge of the world image (lon = -180) in screen coords.
                // camX is in Mercator world units (0..WORLD_W).
                // screenX = (worldX - camX) * zoom + vw/2
                // Left edge: worldX = 0  →  screenX = (0 - camX)*zoom + vw/2
                double worldLeft = (0 - camX) * zoom + vw / 2.0;

                // Y: The image maps lat linearly: top=+90, bottom=-90.
                // Convert camY (Mercator) → camLat → linear image Y.
                // Linear image Y: top=0, bottom=imgH.  camLat maps to imgH*(90-camLat)/180.
                // screen center = camLat position in image → imgTop = vh/2 - camLat_imgY
                double camLat = worldYToLat(camY);
                double camLatImgY = imgH * (90.0 - camLat) / 180.0;
                double imgTop = vh / 2.0 - camLatImgY;

                g.drawImage(WORLD_IMG, worldLeft,        imgTop, imgW, imgH);
                g.drawImage(WORLD_IMG, worldLeft - imgW, imgTop, imgW, imgH); // wrap left
                g.drawImage(WORLD_IMG, worldLeft + imgW, imgTop, imgW, imgH); // wrap right
            }
        }
    }

    private void drawRoute(GraphicsContext g, double vw, double vh) {
        if (route == null || route.size() < 2) return;

        Color routeColor = routeValid
                ? Color.rgb(255, 235, 59, 0.98)
                : Color.rgb(220, 50,  50, 0.98);

        double px = Double.NaN, py = Double.NaN;
        g.setLineWidth(5.0);
        g.setStroke(Color.rgb(0, 0, 0, 0.85));
        for (City c : route) {
            double[] p = cityToScreen(c, vw, vh, px);
            if (!Double.isNaN(px)) g.strokeLine(px, py, p[0], p[1]);
            px = p[0]; py = p[1];
        }

        px = Double.NaN; py = Double.NaN;
        g.setLineWidth(2.8);
        g.setStroke(routeColor);
        for (City c : route) {
            double[] p = cityToScreen(c, vw, vh, px);
            if (!Double.isNaN(px)) {
                g.strokeLine(px, py, p[0], p[1]);
                drawArrowHead(g, px, py, p[0], p[1]);
            }
            px = p[0]; py = p[1];
        }
        g.setLineWidth(1.0);
    }

    private void drawOverlayMarkers(GraphicsContext g, double vw, double vh) {
        if (overlayMarkers.isEmpty()) return;
        for (MapMarker m : overlayMarkers) {
            if (m == selectedMarker) continue;
            double[] p = markerScreenPos(m, vw, vh);
            double r = 5.0 + m.importance() * 5.0;
            // Skip if pin tip is completely off screen (with generous margin)
            if (p[0] < -50 || p[0] > vw + 50 || p[1] < -100 || p[1] > vh + 50) continue;
            boolean hov = hoveredMarker != null
                && Math.abs(m.lon() - hoveredMarker.lon()) < 1e-5
                && Math.abs(m.lat() - hoveredMarker.lat()) < 1e-5;
            drawPin(g, p[0], p[1], m.label(), r, false, hov, m.importance(), showLabels);
        }
        if (selectedMarker != null) {
            for (MapMarker m : overlayMarkers) {
                double dx = m.lon() - selectedMarker.lon();
                double dy = m.lat() - selectedMarker.lat();
                if (dx * dx + dy * dy < 1e-6) {
                    double[] p = markerScreenPos(m, vw, vh);
                    double r = 5.0 + m.importance() * 5.0;
                    drawPin(g, p[0], p[1], m.label(), r, true, false, m.importance(), showLabels);
                    break;
                }
            }
        }
    }

    /**
     * Returns the on-screen position for a marker, correctly handling horizontal
     * world wrap at any zoom level. Tries the raw world X and both wrapped copies
     * (±WORLD_W in world units), picks whichever screen X is closest to screen centre.
     */
    private double[] markerScreenPos(MapMarker m, double vw, double vh) {
        if (!useTiles) {
            return lonLatToScreenEquirectangular(m.lon(), m.lat(), vw, vh, Double.NaN);
        }
        double wy = latToWorldY(m.lat());
        double wx = lonToWorldX(m.lon());
        double cx = vw / 2.0;

        // Try raw + left wrap + right wrap in world-unit space
        double[] raw  = worldToScreen(wx,          wy, vw, vh, Double.NaN);
        double[] wL   = worldToScreen(wx - WORLD_W, wy, vw, vh, Double.NaN);
        double[] wR   = worldToScreen(wx + WORLD_W, wy, vw, vh, Double.NaN);

        double[] best = raw;
        double bestD  = Math.abs(raw[0] - cx);
        if (Math.abs(wL[0] - cx) < bestD) { best = wL; bestD = Math.abs(wL[0] - cx); }
        if (Math.abs(wR[0] - cx) < bestD)   best = wR;
        return best;
    }

    private void drawPin(GraphicsContext g, double cx, double cy,
                         String label, double r, boolean selected,
                         boolean hovered, double importance, boolean showLabels) {
        double stem    = r * 1.4;
        double circleY = cy - r - stem;

        // Safety check — if the circle would be off screen, just draw a simple dot
        // at the exact coordinate point so it's always visible
        double vw = canvas.getWidth(), vh = canvas.getHeight();
        boolean circleOnScreen = circleY + r > 0 && circleY - r < vh
                              && cx + r > 0 && cx - r < vw;

        if (!circleOnScreen) {
            // Fallback: simple bright dot at the coordinate
            g.setFill(Color.rgb(200, 30, 30, 0.95));
            g.fillOval(cx - 6, cy - 6, 12, 12);
            g.setStroke(Color.WHITE);
            g.setLineWidth(1.5);
            g.strokeOval(cx - 6, cy - 6, 12, 12);
            g.setLineWidth(1.0);
            return;
        }

        // Selection glow
        if (selected) {
            g.setFill(Color.rgb(255, 230, 0, 0.3));
            g.fillOval(cx - r - 6, circleY - r - 6, (r + 6) * 2, (r + 6) * 2);
            g.setStroke(Color.rgb(255, 220, 0, 0.9));
            g.setLineWidth(2.5);
            g.strokeOval(cx - r - 4, circleY - r - 4, (r + 4) * 2, (r + 4) * 2);
        }

        // Hover glow
        if (hovered && !selected) {
            g.setFill(Color.rgb(255, 255, 255, 0.18));
            g.fillOval(cx - r - 4, circleY - r - 4, (r + 4) * 2, (r + 4) * 2);
            g.setStroke(Color.rgb(255, 255, 255, 0.55));
            g.setLineWidth(1.5);
            g.strokeOval(cx - r - 3, circleY - r - 3, (r + 3) * 2, (r + 3) * 2);
        }

        // ── STEM — grey ──
        g.setFill(Color.rgb(0, 0, 0, 0.2));
        double[] sxs = { cx - r * 0.45 + 1.5, cx + r * 0.45 + 1.5, cx + 1.5 };
        double[] sys = { circleY + r * 0.4, circleY + r * 0.4, cy + 2.5 };
        g.fillPolygon(sxs, sys, 3);

        double[] xs = { cx - r * 0.45, cx + r * 0.45, cx };
        double[] ys = { circleY + r * 0.4, circleY + r * 0.4, cy };
        g.setFill(hovered ? Color.rgb(145, 145, 150) : Color.rgb(120, 120, 125, 0.92));
        g.fillPolygon(xs, ys, 3);

        // ── CIRCLE — red ──
        g.setFill(Color.rgb(0, 0, 0, 0.28));
        g.fillOval(cx - r + 1.5, circleY - r + 1.5, r * 2, r * 2);

        Color fill = selected ? Color.rgb(235, 40,  0)
                   : hovered  ? Color.rgb(225, 65, 35)
                   :            Color.rgb(200, 30, 30, 0.95);
        g.setFill(fill);
        g.fillOval(cx - r, circleY - r, r * 2, r * 2);

        // Shine
        g.setFill(Color.rgb(255, 120, 100, 0.4));
        g.fillOval(cx - r * 0.65, circleY - r * 0.72, r * 0.85, r * 0.7);

        // Border
        g.setStroke(selected ? Color.WHITE : Color.rgb(255, 255, 255, 0.85));
        g.setLineWidth(selected ? 2.0 : 1.5);
        g.strokeOval(cx - r, circleY - r, r * 2, r * 2);

        // Centre dot
        double dot = r * 0.27;
        g.setFill(Color.rgb(255, 255, 255, 0.82));
        g.fillOval(cx - dot, circleY - dot, dot * 2, dot * 2);

        // Label — always show when selected/hovered, otherwise respect showLabels toggle
        boolean showLabel = showLabels && (selected || hovered || importance > 0.7 || (zoom >= 2.5 && r >= 6.0));
        // Selected pin always shows label regardless of toggle
        if (selected) showLabel = true;
        if (showLabel && label != null) {
            g.setFont(MARKER_FONT);
            double lx = cx + r + 5, ly = circleY + 4;
            g.setFill(Color.rgb(0, 0, 0, 0.9));
            g.fillText(label, lx - 1, ly - 1);
            g.fillText(label, lx + 1, ly - 1);
            g.fillText(label, lx - 1, ly + 1);
            g.fillText(label, lx + 1, ly + 1);
            g.setFill(selected ? Color.rgb(255, 255, 200) : Color.WHITE);
            g.fillText(label, lx, ly);
        }
        g.setLineWidth(1.0);
    }

    private MapMarker findNearestMarker(double sx, double sy, double maxPx) {
        double vw = getWidth(), vh = getHeight();
        if (vw <= 0 || vh <= 0 || overlayMarkers.isEmpty()) return null;
        MapMarker best = null;
        double bestD = maxPx * maxPx;
        for (MapMarker m : overlayMarkers) {
            double[] p = markerScreenPos(m, vw, vh);
            double cx = p[0], cy = p[1];
            double r = 5.0 + m.importance() * 5.0;
            double stem = r * 1.4;
            double circleY = cy - r - stem;
            double dxC = sx - cx, dyC = sy - circleY, d2Circle = dxC*dxC + dyC*dyC;
            double dxT = sx - cx, dyT = sy - cy,      d2Tip    = dxT*dxT + dyT*dyT;
            double d2 = Math.min(d2Circle, d2Tip);
            if (d2 <= bestD) { bestD = d2; best = m; }
        }
        return best;
    }

    private void drawPendingMarker(GraphicsContext g, double vw, double vh) {
        if (pendingLon == null || pendingLat == null) return;
        double[] p = useTiles
                ? worldToScreen(lonToWorldX(pendingLon), latToWorldY(pendingLat), vw, vh, Double.NaN)
                : lonLatToScreenEquirectangular(pendingLon, pendingLat, vw, vh, Double.NaN);
        double x = p[0], y = p[1], r = 10;
        g.setLineWidth(4.0); g.setStroke(Color.rgb(0, 0, 0, 0.8));
        g.strokeOval(x - r, y - r, r * 2, r * 2);
        g.setLineWidth(2.0); g.setStroke(Color.WHITE);
        g.strokeOval(x - r, y - r, r * 2, r * 2);
        g.strokeLine(x - r - 6, y, x + r + 6, y);
        g.strokeLine(x, y - r - 6, x, y + r + 6);
        g.setLineWidth(1.0);
    }

    private void drawCities(GraphicsContext g, double vw, double vh) {
        for (City c : cities) drawCity(g, c, vw, vh);
    }

    /**
     * Draws an added city with importance-based marker shape:
     *
     *   capital  → gold star (always visible, large)
     *   city     → large filled circle with white border
     *   town     → medium filled circle
     *   village  → small circle
     *   other    → plain dot
     *
     * Start city gets a gold outer ring.
     * Deadline city gets a cyan outer ring.
     *
     * Fixed screen size — never disappears when zooming out.
     */
    private void drawCity(GraphicsContext g, City c, double vw, double vh) {
        boolean isStart     = startCity != null && startCity.equals(c);
        boolean hasDeadline = c.hasDeadline();
        boolean isSelected  = selectedCity != null && selectedCity.equals(c);

        double[] p = cityToScreen(c, vw, vh, Double.NaN);
        double x = p[0], y = p[1];
        double r = isStart ? 10 : 8;

        // Selection glow — white halo behind everything
        if (isSelected) {
            g.setFill(Color.rgb(255, 255, 255, 0.25));
            g.fillOval(x - r - 7, y - r - 7, (r + 7) * 2, (r + 7) * 2);
            g.setStroke(Color.rgb(255, 255, 255, 0.85));
            g.setLineWidth(2.5);
            g.strokeOval(x - r - 5, y - r - 5, (r + 5) * 2, (r + 5) * 2);
        }

        // Outer ring for start/deadline
        if (isStart) {
            g.setStroke(Color.rgb(255, 215, 0, 0.95));
            g.setLineWidth(3.0);
            g.strokeOval(x - r - 5, y - r - 5, (r + 5) * 2, (r + 5) * 2);
        } else if (hasDeadline) {
            g.setStroke(Color.rgb(0, 230, 255, 0.95));
            g.setLineWidth(2.5);
            g.strokeOval(x - r - 4, y - r - 4, (r + 4) * 2, (r + 4) * 2);
        }

        // Drop shadow
        g.setFill(Color.rgb(0, 0, 0, 0.4));
        g.fillOval(x - r + 1, y - r + 1.5, r * 2, r * 2);

        // White outer circle
        g.setFill(Color.rgb(255, 255, 255, 0.95));
        g.fillOval(x - r, y - r, r * 2, r * 2);

        // Coloured inner dot
        double inner = r * 0.55;
        Color innerColor = isStart     ? Color.rgb(255, 200, 0)   // gold
                        : hasDeadline  ? Color.rgb(0, 200, 255)   // cyan
                        : Color.rgb(60, 60, 60);                   // dark grey
        g.setFill(innerColor);
        g.fillOval(x - inner, y - inner, inner * 2, inner * 2);

        // Thin dark border
        g.setStroke(Color.rgb(60, 60, 60, 0.7));
        g.setLineWidth(1.2);
        g.strokeOval(x - r, y - r, r * 2, r * 2);

        // Label
        if (showLabels) {
            String label = isStart ? "⬥ " + c.getID()
                         : hasDeadline ? "⏰ " + c.getID()
                         : c.getID();
            g.setFont(LABEL_FONT);
            double lx = x + r + 5, ly = y + 4;
            g.setFill(Color.rgb(0, 0, 0, 0.9));
            g.fillText(label, lx - 1, ly - 1);
            g.fillText(label, lx + 1, ly - 1);
            g.fillText(label, lx - 1, ly + 1);
            g.fillText(label, lx + 1, ly + 1);
            g.setFill(Color.WHITE);
            g.fillText(label, lx, ly);
        }
        g.setLineWidth(1.0);
    }

    private void drawAttribution(GraphicsContext g, double vw, double vh) {
        String text = "Imagery \u00A9 Esri, Maxar, Earthstar Geographics  |  Places \u00A9 OpenStreetMap contributors";
        g.setFont(ATTR_FONT);
        g.setFill(Color.rgb(0, 0, 0, 0.6));
        g.fillText(text, vw - 520, vh - 7);
        g.setFill(Color.rgb(255, 255, 255, 0.80));
        g.fillText(text, vw - 521, vh - 8);
    }

    private void drawArrowHead(GraphicsContext g, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1, dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 25) return;
        double ux = dx / len, uy = dy / len;
        double bx = x2 - ux * 13, by = y2 - uy * 13;
        g.strokeLine(x2, y2, bx - uy * 8, by + ux * 8);
        g.strokeLine(x2, y2, bx + uy * 8, by - ux * 8);
    }

    // ══════════════════════════════════════════════════════════
    // Hit testing
    // ══════════════════════════════════════════════════════════

    private City findNearestCity(double sx, double sy, double maxPx) {
        double vw = getWidth(), vh = getHeight();
        if (vw <= 0 || vh <= 0) return null;
        City best = null; double bestD2 = maxPx * maxPx;
        for (City c : cities) {
            double[] p = cityToScreen(c, vw, vh, Double.NaN);
            double dx = sx - p[0], dy = sy - p[1], d2 = dx * dx + dy * dy;
            if (d2 <= bestD2) { bestD2 = d2; best = c; }
        }
        return best;
    }

    // ══════════════════════════════════════════════════════════
    // Coordinate conversions — Web Mercator
    // ══════════════════════════════════════════════════════════

    private double screenToWorldX(double sx, double vw) { return camX - vw / zoom / 2.0 + sx / zoom; }
    private double screenToWorldY(double sy, double vh) { return camY - vh / zoom / 2.0 + sy / zoom; }

    /**
     * Inverts the equirectangular image rendering formula from {@link #lonLatToScreenEquirectangular}
     * to convert a screen X pixel → longitude when offline mode is active.
     *
     * <p>The world.jpg image occupies screen pixels [worldLeft, worldLeft+imgW] where
     * {@code worldLeft = (0 - camX)*zoom + vw/2} and {@code imgW = WORLD_W*zoom}.
     * So: {@code frac = (sx - worldLeft) / imgW} → {@code lon = frac*360 - 180}.
     */
    private double screenToLonEquirectangular(double sx, double vw) {
        double imgW     = WORLD_W * zoom;
        double worldLeft = (0 - camX) * zoom + vw / 2.0;
        double frac     = (sx - worldLeft) / imgW;
        return normalizeLon(frac * 360.0 - 180.0);
    }

    /**
     * Inverts the equirectangular image rendering formula from {@link #lonLatToScreenEquirectangular}
     * to convert a screen Y pixel → latitude when offline mode is active.
     *
     * <p>The image top is at {@code imgTop = vh/2 - camLatImgY} where
     * {@code camLatImgY = imgH*(90 - camLat)/180}. So:
     * {@code latFrac = (sy - imgTop) / imgH} → {@code lat = 90 - latFrac*180}.
     */
    private double screenToLatEquirectangular(double sy, double vh) {
        double imgW      = WORLD_W * zoom;
        double imgH      = imgW / 2.0;
        double camLat    = worldYToLat(camY);
        double camLatImgY = imgH * (90.0 - camLat) / 180.0;
        double imgTop    = vh / 2.0 - camLatImgY;
        double latFrac   = (sy - imgTop) / imgH;
        return clamp(90.0 - latFrac * 180.0, -90.0, 90.0);
    }

    private double[] worldToScreen(double wx, double wy, double vw, double vh, double prevSX) {
        double x = (wx - (camX - vw / zoom / 2.0)) * zoom;
        double y = (wy - (camY - vh / zoom / 2.0)) * zoom;
        if (wrapX && !Double.isNaN(prevSX)) {
            double tileW = WORLD_W * zoom;
            double best = x, bestD = Math.abs(x - prevSX);
            double xL = x - tileW; if (Math.abs(xL - prevSX) < bestD) { bestD = Math.abs(xL - prevSX); best = xL; }
            double xR = x + tileW; if (Math.abs(xR - prevSX) < bestD) best = xR;
            x = best;
        }
        return new double[]{ x, y };
    }

    private double[] cityToScreen(City c, double vw, double vh, double prevSX) {
        if (useTiles) {
            return worldToScreen(lonToWorldX(c.getX()), latToWorldY(c.getY()), vw, vh, prevSX);
        }
        return lonLatToScreenEquirectangular(c.getX(), c.getY(), vw, vh, prevSX);
    }

    /**
     * Screen position for lon/lat when the offline equirectangular map is shown —
     * must match {@link #drawBackground} for {@code world.jpg}.
     */
    private double[] lonLatToScreenEquirectangular(double lon, double lat,
            double vw, double vh, double prevSX) {
        lat = clamp(lat, -90.0, 90.0);
        double worldScreenW = WORLD_W * zoom;
        double imgW = worldScreenW;
        double imgH = worldScreenW / 2.0;
        double worldLeft = (0 - camX) * zoom + vw / 2.0;
        double camLat = worldYToLat(camY);
        double camLatImgY = imgH * (90.0 - camLat) / 180.0;
        double imgTop = vh / 2.0 - camLatImgY;

        double lonN = normalizeLon(lon);
        double frac = (lonN + 180.0) / 360.0;
        double raw = worldLeft + frac * imgW;

        double cx = vw / 2.0;
        double best = raw;
        if (!Double.isNaN(prevSX)) {
            double bestD = Math.abs(raw - prevSX);
            double rawL = raw - imgW;
            double rawR = raw + imgW;
            if (Math.abs(rawL - prevSX) < bestD) {
                bestD = Math.abs(rawL - prevSX);
                best = rawL;
            }
            if (Math.abs(rawR - prevSX) < bestD) best = rawR;
        } else {
            double bestD = Math.abs(raw - cx);
            double rawL = raw - imgW;
            double rawR = raw + imgW;
            if (Math.abs(rawL - cx) < bestD) {
                bestD = Math.abs(rawL - cx);
                best = rawL;
            }
            if (Math.abs(rawR - cx) < bestD) best = rawR;
        }

        double y = imgTop + imgH * (90.0 - lat) / 180.0;
        return new double[]{ best, y };
    }

    private double lonToWorldX(double lon) {
        return ((normalizeLon(lon) + 180.0) / 360.0) * WORLD_W;
    }

    private double latToWorldY(double lat) {
        lat = clamp(lat, -85.051129, 85.051129);
        double latRad = Math.toRadians(lat);
        double merc = (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0;
        return merc * WORLD_H;
    }

    private double worldXToLon(double wx) {
        return normalizeLon((wx / WORLD_W) * 360.0 - 180.0);
    }

    private double worldYToLat(double wy) {
        double n = Math.PI - 2.0 * Math.PI * wy / WORLD_H;
        return Math.toDegrees(Math.atan(Math.sinh(n)));
    }

    // ══════════════════════════════════════════════════════════
    // Camera
    // ══════════════════════════════════════════════════════════

    private void normalizeCamera() {
        double vh = getHeight(); if (vh <= 0) vh = 650;
        double vw = getWidth();  if (vw <= 0) vw = 1280;

        if (useTiles) {
            // Tiles mode: camY is Mercator world Y, clamp so world never shows empty above/below
            double halfH = vh / zoom / 2.0;
            halfH = Math.min(halfH, WORLD_H / 2.0 - 1);
            camY = clamp(camY, halfH, WORLD_H - halfH);
            // Wrap X horizontally
            camX = mod(camX, WORLD_W);
        } else {
            // Offline/static image mode — equirectangular image, linear lat mapping.
            // imgH (screen pixels) = WORLD_W * zoom / 2
            // camLat_imgY = imgH * (90 - camLat) / 180   (pixels from top of image)
            // imgTop = vh/2 - camLat_imgY
            // Clamp so: imgTop <= 0  (top edge on or above screen top)
            //           imgTop + imgH >= vh  (bottom edge on or below screen bottom)
            // i.e.  camLat_imgY >= vh/2            → camLat_imgY in [vh/2, imgH - vh/2]
            double imgH = WORLD_W * zoom / 2.0;
            double halfVh = vh / 2.0;

            if (imgH <= vh) {
                // Image is smaller than viewport — centre it vertically, no panning
                double camLat_imgY = imgH / 2.0;
                double camLat = 90.0 - camLat_imgY / imgH * 180.0;
                camY = latToWorldY(clamp(camLat, -85.051129, 85.051129));
            } else {
                // Clamp camLat_imgY so the image fills the viewport
                double camLat = worldYToLat(camY);
                double camLat_imgY = imgH * (90.0 - camLat) / 180.0;
                double clamped = clamp(camLat_imgY, halfVh, imgH - halfVh);
                double clampedLat = 90.0 - clamped / imgH * 180.0;
                camY = latToWorldY(clamp(clampedLat, -85.051129, 85.051129));
            }

            // Wrap X horizontally in offline mode too (image repeats left/right)
            camX = mod(camX, WORLD_W);
        }
    }

    // ══════════════════════════════════════════════════════════
    // Math helpers
    // ══════════════════════════════════════════════════════════

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }
    private static double mod(double a, double m) { double r = a % m; return r < 0 ? r + m : r; }
    private static double normalizeLon(double lon) { return ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0; }
}
