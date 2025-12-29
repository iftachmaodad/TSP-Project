package ui;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import domain.City;

public final class MapViewPane extends StackPane {

    private final Canvas canvas = new Canvas(900, 650);
    private Image worldImage;

    private List<City> cities = new ArrayList<>();
    private List<City> route = null;
    private City startCity = null;

    private boolean showLabels = true;

    // pending click marker
    private Double pendingLon = null;
    private Double pendingLat = null;

    // ---- World size in pixels (fixed) ----
    private double worldW = 2048;
    private double worldH = 1024;

    // ---- Camera (world pixels) ----
    // camX/camY = center of view in world pixels
    private double camX = worldW / 2.0;
    private double camY = worldH / 2.0;

    // zoom: screenPixels = worldPixels * zoom
    private double zoom = 1.0;
    private static final double MIN_ZOOM = 1.0;
    private static final double MAX_ZOOM = 10.0;

    // wrap horizontally
    private boolean wrapX = true;

    // drag state
    private boolean panning = false;
    private double pressX, pressY;
    private double dragStartCamX, dragStartCamY;
    private static final double CLICK_DRAG_THRESHOLD_PX = 6.0;
    private boolean movedWhilePress = false;

    private BiConsumer<Double, Double> onMapClick;

    // tooltip
    private final Tooltip hoverTip = new Tooltip();
    private boolean tipShowing = false;

    public MapViewPane() {
        getChildren().add(canvas);

        try {
            worldImage = new Image(getClass().getResourceAsStream("/images/world.jpg"));
            if (worldImage != null && worldImage.getWidth() > 0 && worldImage.getHeight() > 0) {
                worldW = worldImage.getWidth();
                worldH = worldImage.getHeight();
                camX = worldW / 2.0;
                camY = worldH / 2.0;
            }
        } catch (Exception ignored) {
            worldImage = null;
        }

        widthProperty().addListener((obs, o, n) -> redraw());
        heightProperty().addListener((obs, o, n) -> redraw());

        // Zoom with scroll, centered on mouse position
        setOnScroll(e -> {
            double vw = getWidth();
            double vh = getHeight();
            if (vw <= 0 || vh <= 0) return;

            double factor = (e.getDeltaY() > 0) ? 1.12 : (1.0 / 1.12);

            zoom *= factor;
            if (zoom < MIN_ZOOM) zoom = MIN_ZOOM;
            if (zoom > MAX_ZOOM) zoom = MAX_ZOOM;

            // Keep world point under cursor fixed
            double worldBeforeX = screenToWorldX(e.getX(), vw);
            double worldBeforeY = screenToWorldY(e.getY(), vh);

            double worldAfterX = screenToWorldX(e.getX(), vw);
            double worldAfterY = screenToWorldY(e.getY(), vh);

            camX += (worldBeforeX - worldAfterX);
            camY += (worldBeforeY - worldAfterY);

            normalizeCamera();
            redraw();
        });

        // Shift + Drag pan
        setOnMousePressed(e -> {
            pressX = e.getX();
            pressY = e.getY();
            movedWhilePress = false;

            if (e.getButton() == MouseButton.PRIMARY && e.isShiftDown()) {
                panning = true;
                dragStartCamX = camX;
                dragStartCamY = camY;
            } else {
                panning = false;
            }
        });

        setOnMouseDragged(e -> {
            double dx = e.getX() - pressX;
            double dy = e.getY() - pressY;

            if (dx * dx + dy * dy > CLICK_DRAG_THRESHOLD_PX * CLICK_DRAG_THRESHOLD_PX) {
                movedWhilePress = true;
            }

            if (!panning) return;

            camX = dragStartCamX - (dx / zoom);
            camY = dragStartCamY - (dy / zoom);

            normalizeCamera();
            redraw();
        });

        setOnMouseReleased(e -> panning = false);

        // Click to set pending marker (left click, NOT shift, NOT dragged)
        setOnMouseClicked(e -> {
            if (e.getButton() != MouseButton.PRIMARY) return;
            if (e.isShiftDown()) return;
            if (movedWhilePress) return;

            double vw = getWidth();
            double vh = getHeight();
            if (vw <= 0 || vh <= 0) return;

            double wx = screenToWorldX(e.getX(), vw);
            double wy = screenToWorldY(e.getY(), vh);

            if (wrapX) wx = mod(wx, worldW);
            wy = clamp(wy, 0, worldH);

            double lon = worldXToLon(wx);
            double lat = worldYToLat(wy);

            setPendingMarker(lon, lat);

            if (onMapClick != null) onMapClick.accept(lon, lat);
        });

        // Hover tooltip
        setOnMouseMoved(e -> {
            City nearest = findNearestCity(e.getX(), e.getY(), 10);
            if (nearest == null) {
                if (tipShowing) { hoverTip.hide(); tipShowing = false; }
                return;
            }

            StringBuilder sb = new StringBuilder();
            sb.append(nearest.getID());
            sb.append("\nlon=").append(String.format("%.5f", nearest.getX()));
            sb.append("  lat=").append(String.format("%.5f", nearest.getY()));
            if (nearest.hasDeadline()) sb.append("\ndeadline=").append(String.format("%.0f", nearest.getDeadline())).append("s");
            else sb.append("\n(no deadline)");

            hoverTip.setText(sb.toString());
            if (!tipShowing) {
                hoverTip.show(this, e.getScreenX() + 12, e.getScreenY() + 12);
                tipShowing = true;
            } else {
                hoverTip.setAnchorX(e.getScreenX() + 12);
                hoverTip.setAnchorY(e.getScreenY() + 12);
            }
        });

        setOnMouseExited(e -> {
            if (tipShowing) { hoverTip.hide(); tipShowing = false; }
        });

        normalizeCamera();
        redraw();
    }

    // ---------- Public API ----------
    public void setOnMapClick(BiConsumer<Double, Double> handler) { this.onMapClick = handler; }

    public void setShowLabels(boolean show) {
        this.showLabels = show;
        redraw();
    }

    public void setCities(List<City> cities) {
        this.cities = (cities == null) ? new ArrayList<>() : new ArrayList<>(cities);
        redraw();
    }

    public void setRoute(List<City> routePath) {
        this.route = routePath;
        redraw();
    }

    public void setStartCity(City start) {
        this.startCity = start;
        redraw();
    }

    public void setPendingMarker(double lon, double lat) {
        this.pendingLon = lon;
        this.pendingLat = lat;
        redraw();
    }

    public void clearPendingMarker() {
        this.pendingLon = null;
        this.pendingLat = null;
        redraw();
    }

    public void setWrapX(boolean wrapX) {
        this.wrapX = wrapX;
        normalizeCamera();
        redraw();
    }

    // ---------- Drawing ----------
    private void redraw() {
        double vw = getWidth();
        double vh = getHeight();
        if (vw <= 0 || vh <= 0) return;

        canvas.setWidth(vw);
        canvas.setHeight(vh);

        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, vw, vh);

        // draw background (tile horizontally if wrapX)
        drawBackground(g, vw, vh);

        // route lines
        drawRoute(g, vw, vh);

        // pending click marker
        drawPending(g, vw, vh);

        // cities
        for (City c : cities) {
            drawCity(g, c, vw, vh);
        }
    }

    private void drawBackground(GraphicsContext g, double vw, double vh) {
        if (worldImage == null || worldImage.getWidth() <= 0) {
            g.setStroke(Color.GRAY);
            g.strokeRect(0, 0, vw, vh);
            return;
        }

        // world top-left in world coords
        double viewWorldW = vw / zoom;
        double viewWorldH = vh / zoom;
        double topLeftX = camX - viewWorldW / 2.0;
        double topLeftY = camY - viewWorldH / 2.0;

        double drawY = -topLeftY * zoom;

        double tileW = worldW * zoom;

        if (!wrapX) {
            double drawX = -topLeftX * zoom;
            g.drawImage(worldImage, drawX, drawY, worldW * zoom, worldH * zoom);
            return;
        }

        // normalize start tile so it covers screen
        double startX = -mod(topLeftX, worldW) * zoom;

        // draw enough tiles to cover screen width
        for (double x = startX - tileW; x < vw + tileW; x += tileW) {
            g.drawImage(worldImage, x, drawY, worldW * zoom, worldH * zoom);
        }
    }

    private void drawRoute(GraphicsContext g, double vw, double vh) {
        if (route == null || route.size() < 2) return;

        double prevX = Double.NaN, prevY = Double.NaN;

        // thick outline first
        g.setLineWidth(5.0);
        g.setStroke(Color.rgb(0, 0, 0, 0.90));
        for (int i = 0; i < route.size(); i++) {
            City c = route.get(i);
            double[] p = cityToScreen(c, vw, vh, prevX);
            double x = p[0], y = p[1];

            if (i > 0) g.strokeLine(prevX, prevY, x, y);

            prevX = x; prevY = y;
        }

        // inner bright line
        prevX = Double.NaN; prevY = Double.NaN;
        g.setLineWidth(2.8);
        g.setStroke(Color.rgb(255, 235, 59, 0.98));
        for (int i = 0; i < route.size(); i++) {
            City c = route.get(i);
            double[] p = cityToScreen(c, vw, vh, prevX);
            double x = p[0], y = p[1];

            if (i > 0) {
                g.strokeLine(prevX, prevY, x, y);
                drawArrowHead(g, prevX, prevY, x, y);
            }

            prevX = x; prevY = y;
        }

        g.setLineWidth(1.0);
    }

    private void drawPending(GraphicsContext g, double vw, double vh) {
        if (pendingLon == null || pendingLat == null) return;

        double wx = lonToWorldX(pendingLon);
        double wy = latToWorldY(pendingLat);

        double[] p = worldToScreen(wx, wy, vw, vh, Double.NaN);
        double x = p[0], y = p[1];

        double r = 10;

        g.setLineWidth(5.0);
        g.setStroke(Color.rgb(0, 0, 0, 0.95));
        g.strokeOval(x - r, y - r, r * 2, r * 2);

        g.setLineWidth(2.2);
        g.setStroke(Color.rgb(255, 255, 255, 0.98));
        g.strokeOval(x - r, y - r, r * 2, r * 2);

        g.strokeLine(x - r - 7, y, x + r + 7, y);
        g.strokeLine(x, y - r - 7, x, y + r + 7);

        g.setLineWidth(1.0);
    }

    private void drawCity(GraphicsContext g, City c, double vw, double vh) {
        boolean isStart = (startCity != null && startCity.equals(c));
        boolean isDeadline = c.hasDeadline();

        double wx = lonToWorldX(c.getX());
        double wy = latToWorldY(c.getY());

        double[] p = worldToScreen(wx, wy, vw, vh, Double.NaN);
        double x = p[0], y = p[1];

        double r = isStart ? 8 : (isDeadline ? 7 : 5);

        // main dot
        g.setFill(Color.BLACK);
        g.fillOval(x - r, y - r, r * 2, r * 2);

        g.setStroke(Color.WHITE);
        g.setLineWidth(2.0);
        g.strokeOval(x - r, y - r, r * 2, r * 2);

        // start ring
        if (isStart) {
            g.setStroke(Color.rgb(255, 215, 0, 0.98));
            g.setLineWidth(3.0);
            g.strokeOval(x - (r + 6), y - (r + 6), (r + 6) * 2, (r + 6) * 2);
        }

        // deadline ring
        if (isDeadline && !isStart) {
            g.setStroke(Color.rgb(0, 230, 255, 0.95));
            g.setLineWidth(3.0);
            g.strokeOval(x - (r + 6), y - (r + 6), (r + 6) * 2, (r + 6) * 2);
        }

        // label (no emoji dependency)
        if (showLabels) {
            String label = c.getID();
            if (isStart) label = "S " + label;
            else if (isDeadline) label = "D " + label;

            // outline text
            g.setFill(Color.rgb(0, 0, 0, 0.85));
            g.fillText(label, x + r + 6 + 1, y - r - 2 + 1);
            g.setFill(Color.WHITE);
            g.fillText(label, x + r + 6, y - r - 2);
        }

        g.setLineWidth(1.0);
    }

    // ---------- Coordinate conversions ----------
    // Screen -> World
    private double screenToWorldX(double sx, double viewW) {
        double viewWorldW = viewW / zoom;
        double topLeftX = camX - viewWorldW / 2.0;
        return topLeftX + sx / zoom;
    }

    private double screenToWorldY(double sy, double viewH) {
        double viewWorldH = viewH / zoom;
        double topLeftY = camY - viewWorldH / 2.0;
        return topLeftY + sy / zoom;
    }

    // World -> Screen (with wrap closest-to-prevX for nice route lines)
    private double[] worldToScreen(double wx, double wy, double viewW, double viewH, double prevScreenX) {
        double viewWorldW = viewW / zoom;
        double viewWorldH = viewH / zoom;

        double topLeftX = camX - viewWorldW / 2.0;
        double topLeftY = camY - viewWorldH / 2.0;

        double x = (wx - topLeftX) * zoom;
        double y = (wy - topLeftY) * zoom;

        if (wrapX && !Double.isNaN(prevScreenX)) {
            double tileW = worldW * zoom;
            double best = x;
            double bestDist = Math.abs(best - prevScreenX);

            double xL = x - tileW;
            double dL = Math.abs(xL - prevScreenX);
            if (dL < bestDist) { bestDist = dL; best = xL; }

            double xR = x + tileW;
            double dR = Math.abs(xR - prevScreenX);
            if (dR < bestDist) best = xR;

            x = best;
        }

        return new double[]{x, y};
    }

    private double[] cityToScreen(City c, double viewW, double viewH, double prevScreenX) {
        double wx = lonToWorldX(c.getX());
        double wy = latToWorldY(c.getY());
        return worldToScreen(wx, wy, viewW, viewH, prevScreenX);
    }

    // lon/lat <-> world pixels
    private double lonToWorldX(double lon) {
        double n = normalizeLon(lon);
        return ((n + 180.0) / 360.0) * worldW;
    }

    private double latToWorldY(double lat) {
        double cl = clamp(lat, -90.0, 90.0);
        return ((90.0 - cl) / 180.0) * worldH;
    }

    private double worldXToLon(double wx) {
        double lon = (wx / worldW) * 360.0 - 180.0;
        return normalizeLon(lon);
    }

    private double worldYToLat(double wy) {
        double lat = 90.0 - (wy / worldH) * 180.0;
        return clamp(lat, -90.0, 90.0);
    }

    // ---------- Camera normalization ----------
    private void normalizeCamera() {
        if (wrapX) camX = mod(camX, worldW);
        camY = clamp(camY, viewHalfWorldH(), worldH - viewHalfWorldH());
    }

    private double viewHalfWorldH() {
        double vh = getHeight();
        if (vh <= 0) vh = 650;
        return (vh / zoom) / 2.0;
    }

    // ---------- Hover helper ----------
    private City findNearestCity(double sx, double sy, double maxDistPx) {
        double vw = getWidth();
        double vh = getHeight();
        if (vw <= 0 || vh <= 0) return null;

        City best = null;
        double bestD2 = maxDistPx * maxDistPx;

        for (City c : cities) {
            double[] p = cityToScreen(c, vw, vh, Double.NaN);
            double dx = sx - p[0];
            double dy = sy - p[1];
            double d2 = dx * dx + dy * dy;
            if (d2 <= bestD2) {
                bestD2 = d2;
                best = c;
            }
        }
        return best;
    }

    // ---------- Arrow heads ----------
    private void drawArrowHead(GraphicsContext g, double x1, double y1, double x2, double y2) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 25) return;

        double ux = dx / len;
        double uy = dy / len;

        double arrowLen = 13;
        double arrowWidth = 8;

        double bx = x2 - ux * arrowLen;
        double by = y2 - uy * arrowLen;

        double px = -uy;
        double py = ux;

        double lx = bx + px * arrowWidth;
        double ly = by + py * arrowWidth;

        double rx = bx - px * arrowWidth;
        double ry = by - py * arrowWidth;

        g.strokeLine(x2, y2, lx, ly);
        g.strokeLine(x2, y2, rx, ry);
    }

    // ---------- Small math helpers ----------
    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private static double mod(double a, double m) {
        double r = a % m;
        return (r < 0) ? (r + m) : r;
    }

    private static double normalizeLon(double lon) {
        return ((lon + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
    }
}
