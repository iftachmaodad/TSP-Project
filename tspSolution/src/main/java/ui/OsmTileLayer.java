package ui;

import javafx.application.Platform;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Fetches OpenStreetMap tiles and draws them on a Canvas GraphicsContext.
 *
 * Tile convention (slippy map / XYZ):
 *   URL  : https://tile.openstreetmap.org/{z}/{x}/{y}.png
 *   Zoom : integer 0-19  (we use 0-14 to match our zoom range)
 *   Each tile is 256x256 pixels at the chosen zoom level.
 *
 * Attribution required by OSM tile usage policy:
 *   "© OpenStreetMap contributors"
 *
 * Design:
 *   - Tiles are fetched on a background thread pool.
 *   - A bounded LRU cache keeps the most recently used tiles in memory.
 *   - On fetch completion the provided Runnable is called on the JavaFX thread
 *     so the caller can trigger a redraw.
 *   - All public methods are safe to call from the JavaFX application thread.
 *
 * <h3>Loading indicator</h3>
 * Optional {@link #setTileFetchListeners(Runnable, Runnable)} callbacks fire on the
 * JavaFX thread once per tile request (begin before submit, end after load/error)
 * so the UI can show a spinner while imagery streams in during pan/zoom.
 */
public final class OsmTileLayer {

    // Set User-Agent for all HTTP requests made by this JVM,
    // including JavaFX's Image loader — required by OSM tile policy.
    static {
        System.setProperty("http.agent", "TSP-Solver/1.0 (student project; Java)");
    }

    // --- Constants ---

    private static final int    TILE_SIZE    = 256;
    private static final int    MAX_OSM_ZOOM = 20;
    private static final int    CACHE_SIZE   = 512;

    // ESRI World Imagery — free satellite tiles, no API key, no User-Agent restriction.
    // Note: ESRI uses z/y/x order (not z/x/y like OSM).
    // Attribution: Tiles © Esri — Source: Esri, Maxar, GeoEye, Earthstar Geographics
    private static final String TILE_URL =
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/%d/%d/%d";

    // --- State ---

    /** LRU cache: key = "z/x/y", value = loaded Image (or PENDING sentinel). */
    private final Map<String, Image> cache = new LinkedHashMap<>(CACHE_SIZE, 0.75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String, Image> e) {
            return size() > CACHE_SIZE;
        }
    };

    private final ExecutorService pool = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "osm-tile-loader");
        t.setDaemon(true);
        return t;
    });

    // World size — must match MapViewPane.WORLD_W and WORLD_H exactly
    // so that tile positions agree with pin positions.
    private static final double WORLD_W = 2048.0;

    /** Called on the FX thread after a tile finishes loading. */
    private Runnable onTileLoaded;

    /** One begin/end pair per async tile fetch; invoked on the JavaFX thread. */
    private Runnable onTileFetchBegin = () -> {};
    private Runnable onTileFetchEnd   = () -> {};

    // --- Public API ---

    public void setOnTileLoaded(Runnable callback) {
        this.onTileLoaded = callback;
    }

    /**
     * Runs on the JavaFX thread: {@code onBegin} when a new tile download starts,
     * {@code onEnd} exactly once when that download completes, fails, or errors.
     */
    public void setTileFetchListeners(Runnable onBegin, Runnable onEnd) {
        this.onTileFetchBegin = onBegin != null ? onBegin : () -> {};
        this.onTileFetchEnd   = onEnd != null ? onEnd : () -> {};
    }

    /**
     * Draws tiles onto the GraphicsContext.
     *
     * {@code tileScreen} and {@code osmZ} are computed from {@code WORLD_W}
     * (not {@code vw}) so that tile positions match {@link MapViewPane}'s
     * {@code worldToScreen} formula, which uses the same reference world width.
     */
    public void draw(GraphicsContext g, double vw, double vh,
                     double camLon, double camLat, double zoom) {

        int osmZ = lonLatZoomToOsmZ(zoom); // uses WORLD_W, not vw

        // Number of tiles across the world at this zoom level
        int numTiles = 1 << osmZ;

        // Camera position in tile coordinates
        double camTileX = lonToTileX(camLon, osmZ);
        double camTileY = latToTileY(camLat, osmZ);

        // Screen pixels per tile — uses WORLD_W to match MapViewPane.worldToScreen
        // At osmZ: world = numTiles tiles = WORLD_W * zoom screen pixels
        // So: tileScreen = WORLD_W * zoom / numTiles
        double tileScreen = WORLD_W * zoom / numTiles;

        double tileX0 = camTileX - (vw / 2.0) / tileScreen;
        double tileY0 = camTileY - (vh / 2.0) / tileScreen;

        int tileXStart = (int) Math.floor(tileX0);
        int tileYStart = (int) Math.floor(tileY0);
        int tileXEnd   = (int) Math.ceil(tileX0 + vw / tileScreen) + 1;
        int tileYEnd   = (int) Math.ceil(tileY0 + vh / tileScreen) + 1;

        for (int tx = tileXStart; tx <= tileXEnd; tx++) {
            for (int ty = tileYStart; ty <= tileYEnd; ty++) {
                // Clamp Y (no wrapping vertically)
                if (ty < 0 || ty >= numTiles) continue;

                // Wrap X (world wraps horizontally)
                int wrappedTx = ((tx % numTiles) + numTiles) % numTiles;

                // Screen position of this tile's top-left corner
                double sx = (tx - tileX0) * tileScreen;
                double sy = (ty - tileY0) * tileScreen;

                Image img = getTile(osmZ, wrappedTx, ty);
                if (img != null && img.getWidth() > 0) {
                    g.drawImage(img, sx, sy, tileScreen, tileScreen);
                } else {
                    // Draw placeholder while loading
                    g.setFill(javafx.scene.paint.Color.rgb(200, 200, 200));
                    g.fillRect(sx, sy, tileScreen, tileScreen);
                    g.setStroke(javafx.scene.paint.Color.rgb(180, 180, 180));
                    g.strokeRect(sx, sy, tileScreen, tileScreen);
                }
            }
        }
    }

    /** Shuts down the background thread pool. Call when the application closes. */
    public void shutdown() {
        pool.shutdownNow();
    }

    // --- Tile loading ---

    /**
     * Returns the tile image if cached, or null if not yet loaded.
     * Triggers an async fetch if the tile is not in cache.
     */
    private Image getTile(int z, int x, int y) {
        String key = z + "/" + x + "/" + y;

        synchronized (cache) {
            if (cache.containsKey(key)) return cache.get(key);
            // Mark as in-flight so we don't fire duplicate requests
            cache.put(key, null);
        }

        onTileFetchBegin.run();

        // ESRI tile order is z/y/x — pass y before x
        String url = String.format(TILE_URL, z, y, x);
        pool.submit(() -> {
            AtomicBoolean finished = new AtomicBoolean(false);
            Runnable notifyDone = () -> {
                if (finished.getAndSet(true)) return;
                Platform.runLater(() -> {
                    onTileFetchEnd.run();
                    if (onTileLoaded != null) onTileLoaded.run();
                });
            };
            try {
                Image img = new Image(url, true);
                img.progressProperty().addListener((obs, oldVal, newVal) -> {
                    if (newVal.doubleValue() >= 1.0 && !img.isError()) {
                        synchronized (cache) { cache.put(key, img); }
                        notifyDone.run();
                    }
                });
                img.errorProperty().addListener((obs, oldVal, newVal) -> {
                    if (Boolean.TRUE.equals(newVal)) {
                        synchronized (cache) { cache.remove(key); }
                        notifyDone.run();
                    }
                });
                synchronized (cache) { cache.put(key, img); }
                if (img.getProgress() >= 1.0 && !img.isError()) {
                    synchronized (cache) { cache.put(key, img); }
                    notifyDone.run();
                }
            } catch (Exception e) {
                System.err.println("[OsmTileLayer] Tile load failed: " + e.getMessage());
                synchronized (cache) { cache.remove(key); }
                notifyDone.run();
            }
        });

        return null;
    }

    // --- Coordinate conversions ---

    /**
     * Maps the MapViewPane zoom factor to an OSM tile zoom level.
     * Uses WORLD_W (not viewport width) so osmZ is consistent with
     * the world-space coordinate system used by MapViewPane.
     */
    private static int lonLatZoomToOsmZ(double zoom) {
        double worldScreenW = WORLD_W * zoom;
        int osmZ = (int) (Math.log(worldScreenW / TILE_SIZE) / Math.log(2));
        return Math.max(0, Math.min(osmZ, MAX_OSM_ZOOM));
    }

    /** Longitude to OSM tile X at zoom level z. */
    private static double lonToTileX(double lon, int z) {
        return (lon + 180.0) / 360.0 * (1 << z);
    }

    /** Latitude to OSM tile Y at zoom level z (Web Mercator). */
    private static double latToTileY(double lat, int z) {
        double latRad = Math.toRadians(lat);
        return (1.0 - Math.log(Math.tan(latRad) + 1.0 / Math.cos(latRad)) / Math.PI) / 2.0 * (1 << z);
    }
}
