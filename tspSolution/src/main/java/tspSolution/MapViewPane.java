package tspSolution;

import javafx.beans.value.ChangeListener;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

public final class MapViewPane extends StackPane {
    // --- Properties ---
    private final Canvas canvas = new Canvas(800, 600);
    private Image worldImage = null;

    private List<City> cities = new ArrayList<>();
    private List<City> route = null;

    private BiConsumer<Double, Double> onMapClick; // lon, lat

    // --- Constructors ---
    public MapViewPane() {
        getChildren().add(canvas);

        // ✅ Maven-friendly resource path:
        // Put the file at: src/main/resources/images/world.png
        try {
            worldImage = new Image(getClass().getResourceAsStream("/images/world.png"));
        } catch (Exception ignored) {
            worldImage = null;
        }

        ChangeListener<Number> resize = (obs, oldV, newV) -> redraw();
        widthProperty().addListener(resize);
        heightProperty().addListener(resize);

        setOnMouseClicked(e -> {
            double w = getWidth();
            double h = getHeight();
            if (w <= 0 || h <= 0) return;

            double x = e.getX();
            double y = e.getY();

            double lon = (x / w) * 360.0 - 180.0;
            double lat = 90.0 - (y / h) * 180.0;

            if (onMapClick != null) onMapClick.accept(lon, lat);
        });

        redraw();
    }

    // --- Methods ---
    public void setOnMapClick(BiConsumer<Double, Double> handler) {
        this.onMapClick = handler;
    }

    public void setCities(List<City> cities) {
        this.cities = (cities == null) ? new ArrayList<>() : new ArrayList<>(cities);
        redraw();
    }

    public void setRoute(List<City> routePath) {
        this.route = routePath;
        redraw();
    }

    private void redraw() {
        double w = getWidth();
        double h = getHeight();
        if (w <= 0 || h <= 0) return;

        canvas.setWidth(w);
        canvas.setHeight(h);

        GraphicsContext g = canvas.getGraphicsContext2D();

        g.clearRect(0, 0, w, h);
        if (worldImage != null && worldImage.getWidth() > 0) {
            g.drawImage(worldImage, 0, 0, w, h);
        } else {
            g.strokeRect(0, 0, w, h);
        }

        if (route != null && route.size() >= 2) {
            for (int i = 1; i < route.size(); i++) {
                City a = route.get(i - 1);
                City b = route.get(i);
                double ax = lonToX(a.getX(), w);
                double ay = latToY(a.getY(), h);
                double bx = lonToX(b.getX(), w);
                double by = latToY(b.getY(), h);
                g.strokeLine(ax, ay, bx, by);
            }
        }

        for (City c : cities) {
            double cx = lonToX(c.getX(), w);
            double cy = latToY(c.getY(), h);
            double r = 4;
            g.fillOval(cx - r, cy - r, r * 2, r * 2);
        }
    }

    private double lonToX(double lon, double width) {
        return (lon + 180.0) / 360.0 * width;
    }

    private double latToY(double lat, double height) {
        return (90.0 - lat) / 180.0 * height;
    }
}
