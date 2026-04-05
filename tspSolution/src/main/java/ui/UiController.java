package ui;

import domain.AirCity;
import domain.City;
import domain.CityFactory;
import domain.GroundCity;
import javafx.animation.PauseTransition;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.util.Duration;
import model.Route;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Top-level UI controller for the Solver tab.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Builds the left control panel (map toggles, city panel, solver panel).</li>
 *   <li>Wires all callbacks between the map, city panel, and solver panel.</li>
 *   <li>Manages the overlay pin lifecycle: bundled pins refresh automatically on
 *       every viewport change; the "Load More Pins" button is the only path to
 *       Overpass, with a 5-second cooldown and a per-session call limit.</li>
 *   <li>Owns the route-report table at the bottom of the screen.</li>
 * </ul>
 *
 * <h3>Pin loading</h3>
 * {@link #refreshBundledPins()} is instant and network-free, called on every
 * viewport, city-list, and mode change. {@link #loadMorePins()} calls Overpass
 * and is triggered only by the manual button.
 *
 * <h3>Start-city deadline stripping</h3>
 * The start city may not carry a deadline. When the start city changes to one
 * that has a deadline, {@link #stripDeadlineFromCity(City)} replaces it in the
 * shared list with a no-deadline copy. {@link #lastKnownStartCity} prevents the
 * replacement from triggering a spurious second strip.
 *
 * <h3>Thread safety</h3>
 * All methods run on the JavaFX application thread. Background work is delegated
 * to {@link PlaceSearchService} and {@link SolverPanel}, which marshal results
 * back via {@link javafx.application.Platform#runLater}.
 */
public final class UiController {

    private final BorderPane root = new BorderPane();

    private final MapViewPane          mapPane     = new MapViewPane();
    private final ObservableList<City> cities      = FXCollections.observableArrayList();
    private final CityPanel            cityPanel   = new CityPanel(cities);
    private final SolverPanel          solverPanel = new SolverPanel(cities);
    private final PlaceSearchService   placeSearch = PlaceSearchService.INSTANCE;

    private VBox fullPanel;
    private VBox collapsedBar;

    // ── Route report ──────────────────────────────────────────────────────────

    /** Status banner on row 0 of the route table; data rows use {@link #NONE}. */
    private enum RouteBanner { NONE, VALID_STATUS, INVALID_STATUS }

    /**
     * @param banner    valid/invalid synthetic row vs normal data
     * @param distanceM segment or total distance (metres), same formatting as steps
     * @param timeS     cumulative time (seconds) or total time
     */
    private record RouteRow(RouteBanner banner, String step, String city,
                            String distanceM, String timeS, String deadline, String note) {}

    private final ObservableList<RouteRow> routeRows  = FXCollections.observableArrayList();
    private final TableView<RouteRow>      routeTable = buildRouteTable();

    // ── Loading state ─────────────────────────────────────────────────────────
    private final Label              statusLabel    = new Label();
    private final ProgressIndicator  loadingSpinner = new ProgressIndicator(-1);
    private int activeQueries = 0;

    // ── Pin-load button (manual Overpass fetch) ───────────────────────────────
    // Bundled pins refresh automatically on viewport change (no network).
    // This button is only for fetching MORE pins from Overpass in sparse areas.
    private final Button loadPinsBtn       = new Button("📍 Load More Pins");
    private final Label  overpassUsageLbl  = new Label();
    private boolean      pinCooldown       = false;
    private int          queryGeneration   = 0;

    // Callback: city list forwarded to TspApp when "Send to Benchmark" is clicked.
    Consumer<List<City>> onSendToBenchmark;

    /**
     * Tracks the previously known start city so {@link #onStartCityChanged}
     * only strips deadlines when the selection genuinely changes, not when
     * {@link CityPanel#refreshStartBox()} fires a spurious ComboBox event
     * while rebuilding the item list after a city is added or removed.
     */
    private City lastKnownStartCity = null;

    // ══════════════════════════════════════════════════════════
    // Constructor
    // ══════════════════════════════════════════════════════════

    public UiController() {
        buildLayout();
        wireCallbacks();
        mapPane.setWrapX(true);
    }

    public Parent getRoot() { return root; }

    /** Returns the currently selected start city (for cross-tab wiring). */
    public City getStartCity() { return cityPanel.startBox.getValue(); }

    // ══════════════════════════════════════════════════════════
    // Layout
    // ══════════════════════════════════════════════════════════

    private void buildLayout() {
        root.setPadding(new Insets(0));

        statusLabel.setStyle(
            "-fx-font-size: 11; -fx-text-fill: #cc4400; -fx-padding: 2 8 2 8;" +
            "-fx-background-color: rgba(255,255,255,0.85);");
        statusLabel.setVisible(false);

        loadingSpinner.setMaxSize(32, 32);
        loadingSpinner.setVisible(false);
        loadingSpinner.setStyle("-fx-progress-color: #2196F3;");

        StackPane mapOverlay = new StackPane(mapPane, statusLabel, loadingSpinner);
        mapOverlay.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        StackPane.setAlignment(statusLabel,    Pos.BOTTOM_CENTER);
        StackPane.setAlignment(loadingSpinner, Pos.TOP_RIGHT);
        StackPane.setMargin(loadingSpinner, new Insets(10));

        routeTable.setMinHeight(80);
        routeTable.setMaxHeight(260);  // prevents table pushing map off screen
        mapOverlay.setMinHeight(150);

        SplitPane splitV = new SplitPane();
        splitV.setOrientation(Orientation.VERTICAL);
        splitV.getItems().addAll(mapOverlay, routeTable);
        splitV.setDividerPositions(0.75);
        VBox.setVgrow(splitV, Priority.ALWAYS);

        // The map takes all extra space when the window is resized;
        // the route table keeps its preferred height rather than expanding.
        SplitPane.setResizableWithParent(routeTable, false);

        root.setCenter(splitV);
        fullPanel    = buildFullPanel();
        collapsedBar = buildCollapsedBar();
        root.setLeft(fullPanel);
    }

    private VBox buildCollapsedBar() {
        VBox bar = new VBox(10);
        bar.setPadding(new Insets(10));
        bar.setPrefWidth(48);
        Button open = new Button("\u2630");
        open.setFocusTraversable(false);
        open.setOnAction(e -> root.setLeft(fullPanel));
        bar.getChildren().add(open);
        return bar;
    }

    private VBox buildFullPanel() {
        VBox panel = new VBox(10);
        panel.setPadding(new Insets(10));
        panel.setPrefWidth(360);

        Label title = new Label("TSP Controls");
        title.setStyle("-fx-font-size: 18; -fx-font-weight: bold;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button hide = new Button("\u29C7");
        hide.setFocusTraversable(false);
        hide.setOnAction(e -> root.setLeft(collapsedBar));
        HBox header = new HBox(10, title, spacer, hide);

        Button controlsBtn = new Button("\u2139 Controls");
        controlsBtn.setOnAction(e -> new Alert(Alert.AlertType.INFORMATION,
            "Left-drag: pan the map\n" +
            "Scroll: zoom in/out\n\n" +
            "Click a red pin \u2192 select a named place\n" +
            "Double-click empty space \u2192 place a raw marker\n" +
            "Single-click empty space \u2192 clear selection\n\n" +
            "Added city markers:\n" +
            "  \u2B25 Gold ring  = start city\n" +
            "  \u23F0 Cyan ring  = city with deadline\n" +
            "  \u25CF  White/grey = regular added city\n\n" +
            "Overlay pins (red teardrop) = available places to add\n\n" +
            "Route drawn as a yellow arrow line after solving."
        ).showAndWait());

        CheckBox labelsToggle = new CheckBox("Show place labels");
        labelsToggle.setSelected(true);
        labelsToggle.setOnAction(e -> mapPane.setShowLabels(labelsToggle.isSelected()));

        CheckBox tilesToggle = new CheckBox("Live map tiles");
        tilesToggle.setSelected(true);
        tilesToggle.setOnAction(e -> {
            boolean on = tilesToggle.isSelected();
            mapPane.setUseTiles(on);
            labelsToggle.setDisable(!on);
            loadPinsBtn.setDisable(!on);
            if (on) {
                mapPane.clearPendingMarker();
                showStatus(null);
                refreshBundledPins(); // show bundled pins immediately when going online
            } else {
                mapPane.setOverlayMarkers(null);
                mapPane.clearPendingMarker();
                showStatus("Offline mode — click anywhere to place a city marker.");
            }
        });

        // "Load More Pins" — manual Overpass fetch for sparse areas.
        // Bundled pins (places.json) load automatically; this button fetches
        // additional pins from Overpass when the current area has few results.
        loadPinsBtn.setMaxWidth(Double.MAX_VALUE);
        loadPinsBtn.setTooltip(new Tooltip(
                "Fetch more place pins for the current area from Overpass.\n" +
                "Only needed in areas not covered by the bundled data.\n" +
                "5-second cooldown — limit: " + PlaceSearchService.OVERPASS_SESSION_LIMIT
                + " API calls per session."));
        loadPinsBtn.setOnAction(e -> {
            if (pinCooldown) return;
            loadMorePins();
            pinCooldown = true;
            loadPinsBtn.setDisable(true);
            PauseTransition cd = new PauseTransition(Duration.seconds(5));
            cd.setOnFinished(ev -> {
                pinCooldown = false;
                // Keep disabled if we've hit the session limit.
                if (placeSearch.getOverpassCallCount()
                        < PlaceSearchService.OVERPASS_SESSION_LIMIT) {
                    loadPinsBtn.setDisable(false);
                } else {
                    loadPinsBtn.setDisable(true);
                    loadPinsBtn.setTooltip(new Tooltip(
                            "Session API limit reached. Restart the app to fetch more."));
                }
            });
            cd.play();
        });

        overpassUsageLbl.setStyle(
                "-fx-font-size: 10; -fx-text-fill: #666;");
        overpassUsageLbl.setWrapText(true);
        updateOverpassLabel();

        panel.getChildren().addAll(
            header, controlsBtn, tilesToggle, labelsToggle,
            loadPinsBtn, overpassUsageLbl,
            new Separator(),
            cityPanel.getRoot(),
            new Separator(),
            solverPanel.getRoot()
        );

        ScrollPane scroll = new ScrollPane(panel);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);

        VBox wrapper = new VBox(scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        wrapper.setPrefWidth(360);
        wrapper.setMaxWidth(360);
        return wrapper;
    }

    // ══════════════════════════════════════════════════════════
    // Callback wiring
    // ══════════════════════════════════════════════════════════

    private void wireCallbacks() {

        mapPane.setOnCityClick(city -> cityPanel.selectCityInList(city));

        mapPane.setOnMarkerClick(marker -> {
            cityPanel.onMarkerSelected(marker);
            mapPane.clearPendingMarker();
            showStatus(null);
        });

        mapPane.setOnDeselect(() -> {
            mapPane.setSelectedOverlayMarker(null);
            mapPane.setSelectedCity(null);
            cityPanel.clearCityListSelection();
            cityPanel.clearPending();
        });

        cityPanel.onCityListSelectionChanged = city -> {
            mapPane.setSelectedCity(city);
            // Deselect overlay pin and clear pending so pin + city selection
            // are never active at the same time.
            if (city != null) {
                mapPane.setSelectedOverlayMarker(null);
                cityPanel.clearPending();
            }
        };

        mapPane.setTileFetchListeners(this::showLoading, this::hideLoading);

        // Viewport change → refresh bundled pins immediately (no network).
        // The "Load More Pins" button handles Overpass fetches for sparse areas.
        mapPane.setOnViewportChanged(this::refreshBundledPins);

        mapPane.setOnMapClick((lon, lat) -> {
            cityPanel.onMapClick(lon, lat);
            mapPane.setPendingMarker(lon, lat);
            showStatus(mapPane.isTilesEnabled()
                ? "Raw coordinates selected — or click a red pin to select a named place."
                : "Offline mode — click places a marker at raw coordinates.");
        });

        cityPanel.onCityListChanged = () -> {
            mapPane.setCities(new ArrayList<>(cities));
            mapPane.setStartCity(cityPanel.startBox.getValue());
            mapPane.clearPendingMarker();
            cityPanel.clearPending();
            solverPanel.clearLastRoute();
            mapPane.setRoute(null);
            clearRouteReport();
            showStatus(null);
            // Re-filter pins so newly-added cities disappear from the overlay.
            refreshBundledPins();
        };

        // When the start city genuinely changes, strip its deadline if it has one.
        // lastKnownStartCity prevents spurious strips: refreshStartBox() fires setValue()
        // even when just rebuilding the list, so we guard by identity comparison.
        cityPanel.onStartCityChanged = () -> {
            City newStart = cityPanel.startBox.getValue();
            mapPane.setStartCity(newStart);
            solverPanel.clearLastRoute();
            mapPane.setRoute(null);
            clearRouteReport();

            if (newStart != null && newStart != lastKnownStartCity
                    && newStart.hasDeadline()) {
                stripDeadlineFromCity(newStart);
            }
            lastKnownStartCity = newStart;
        };

        cityPanel.onModeChanged = () -> {
            lastKnownStartCity = null;
            mapPane.setCities(new ArrayList<>());
            mapPane.setStartCity(null);
            mapPane.setRoute(null);
            mapPane.clearPendingMarker();
            mapPane.setOverlayMarkers(null);
            solverPanel.clearLastRoute();
            clearRouteReport();
            showStatus(null);
            // Reset pin cooldown so new mode's pins can be loaded immediately.
            pinCooldown = false;
            loadPinsBtn.setDisable(
                    !mapPane.isTilesEnabled()
                    || placeSearch.getOverpassCallCount()
                            >= PlaceSearchService.OVERPASS_SESSION_LIMIT);
            // Refresh bundled pins for the new mode automatically.
            refreshBundledPins();
        };

        solverPanel.startCitySupplier = () -> cityPanel.startBox.getValue();
        solverPanel.modeSupplier      = () -> cityPanel.modeBox.getValue();
        solverPanel.setLoadingHooks(this::showLoading, this::hideLoading);

        // Route ready — draw on map and populate report table.
        solverPanel.onRouteReady = route -> {
            mapPane.setCities(new ArrayList<>(cities));
            mapPane.setStartCity(cityPanel.startBox.getValue());
            mapPane.setRoute(new ArrayList<>(route.getPath()));
            showRouteReport(route);
        };

        // "Send to Benchmark": forward the city list to TspApp → BenchmarkPane.
        solverPanel.onSendToBenchmark = cityList -> {
            if (onSendToBenchmark != null) {
                onSendToBenchmark.accept(cityList);
                showStatus("Sent " + cityList.size() + " cities to the Benchmark tab.");
            }
        };

        // Show bundled pins immediately on startup — no network call needed.
        refreshBundledPins();
    }

    // ══════════════════════════════════════════════════════════
    // Strip deadline from the new start city
    // ══════════════════════════════════════════════════════════

    /**
     * Replaces {@code city} in the city list with an identical copy that has
     * no deadline, then updates the start-city selection to the copy.
     *
     * <p>City is immutable so replacement is the only way to remove the deadline.
     * After replacement {@link #lastKnownStartCity} is updated to the new copy
     * so that the ComboBox {@code setValue(copy)} call inside this method does
     * not re-trigger {@link #onStartCityChanged} and loop.
     */
    private void stripDeadlineFromCity(City city) {
        int idx = cities.indexOf(city);
        if (idx < 0) return;

        @SuppressWarnings("unchecked")
        City copy = CityFactory.create(
                (Class<City>) city.getClass(),
                city.getID(), city.getX(), city.getY(),
                null); // null deadline → City.NO_DEADLINE

        if (copy == null) return;

        // Update lastKnownStartCity BEFORE setValue so the resulting
        // onStartCityChanged event sees it as a no-op (same identity).
        lastKnownStartCity = copy;

        cities.set(idx, copy);
        cityPanel.refreshStartBox();
        cityPanel.startBox.setValue(copy);
        mapPane.setCities(new ArrayList<>(cities));
        mapPane.setStartCity(copy);
    }

    // ══════════════════════════════════════════════════════════
    // Pin loading — two separate concerns
    // ══════════════════════════════════════════════════════════

    /**
     * Refreshes overlay pins using only the bundled {@code places.json} and
     * disk cache — no network call, no spinner, always instant.
     *
     * <p>Called automatically whenever the viewport changes (pan / zoom).
     * Already-added cities are filtered out so they don't show as pins.
     */
    private void refreshBundledPins() {
        if (!mapPane.isTilesEnabled()) return;

        Class<? extends City> mode = cityPanel.modeBox.getValue();
        if (mode == null) { mapPane.setOverlayMarkers(null); return; }

        boolean isAir    = AirCity.class.equals(mode);
        boolean isGround = GroundCity.class.equals(mode);
        if (!isAir && !isGround) { mapPane.setOverlayMarkers(null); return; }

        double[] bounds = mapPane.getVisibleBounds();
        double west = bounds[0], south = bounds[1], east = bounds[2], north = bounds[3];
        double spanLon = Math.abs(east - west);
        int    cap     = spanLon > 60 ? 15 : spanLon > 20 ? 25 : 40;

        String type = isAir ? "airport" : "city";

        // searchBundledOnly fires synchronously on the FX thread.
        placeSearch.searchBundledOnly(west, south, east, north, type, markers -> {
            List<MapMarker> result = filterAdded(markers);
            sortPins(result);
            List<MapMarker> capped =
                    result.size() > cap ? new ArrayList<>(result.subList(0, cap)) : result;
            mapPane.setOverlayMarkers(capped);
        });
    }

    /**
     * Fetches additional pins from Overpass for the current viewport.
     * Only called from the "Load More Pins" button — never automatically.
     *
     * <p>Shows a spinner during the request. Updates the rate-limit label
     * after the call completes.
     */
    private void loadMorePins() {
        if (!mapPane.isTilesEnabled()) return;

        Class<? extends City> mode = cityPanel.modeBox.getValue();
        if (mode == null) return;

        boolean isAir    = AirCity.class.equals(mode);
        boolean isGround = GroundCity.class.equals(mode);
        if (!isAir && !isGround) return;

        if (placeSearch.getOverpassCallCount()
                >= PlaceSearchService.OVERPASS_SESSION_LIMIT) {
            showStatus("Overpass API session limit reached ("
                    + PlaceSearchService.OVERPASS_SESSION_LIMIT
                    + " calls). Restart the app to fetch more.");
            return;
        }

        double[] bounds = mapPane.getVisibleBounds();
        double west = bounds[0], south = bounds[1], east = bounds[2], north = bounds[3];
        double spanLon = Math.abs(east - west);
        int    cap     = spanLon > 60 ? 15 : spanLon > 20 ? 25 : 40;
        int    gen     = ++queryGeneration;

        mapPane.setOverlayMarkers(null);
        showLoading();
        long startMs = System.currentTimeMillis();

        String type = isAir ? "airport" : "city";
        placeSearch.searchInBounds(west, south, east, north, type, markers -> {
            updateOverpassLabel();

            List<MapMarker> result = filterAdded(markers);
            sortPins(result);
            List<MapMarker> capped =
                    result.size() > cap ? new ArrayList<>(result.subList(0, cap)) : result;

            long remaining = Math.max(0, 350 - (System.currentTimeMillis() - startMs));
            PauseTransition delay = new PauseTransition(Duration.millis(remaining));
            delay.setOnFinished(ev -> {
                hideLoading();
                if (gen != queryGeneration) return;
                mapPane.setOverlayMarkers(capped);
            });
            delay.play();
        });
    }

    /**
     * Returns a new list with any marker whose coordinates match an
     * already-added city removed.
     */
    private List<MapMarker> filterAdded(List<MapMarker> markers) {
        List<MapMarker> result = new ArrayList<>();
        for (MapMarker m : markers) {
            if (!LocationMatchers.nearExistingCity(m.lon(), m.lat(), cities)) result.add(m);
        }
        return result;
    }

    /** Updates the Overpass usage label below the Load More Pins button. */
    private void updateOverpassLabel() {
        int used  = placeSearch.getOverpassCallCount();
        int limit = PlaceSearchService.OVERPASS_SESSION_LIMIT;
        if (used == 0) {
            overpassUsageLbl.setText("");
        } else if (used >= limit) {
            overpassUsageLbl.setText("⚠ API limit reached (" + limit + "/" + limit + ")");
            overpassUsageLbl.setStyle("-fx-font-size: 10; -fx-text-fill: #cc0000;");
        } else {
            overpassUsageLbl.setText("Overpass calls: " + used + "/" + limit);
            String colour = used >= limit - 2 ? "#cc6600" : "#666";
            overpassUsageLbl.setStyle("-fx-font-size: 10; -fx-text-fill: " + colour + ";");
        }
    }

    // ══════════════════════════════════════════════════════════
    // Route report table
    // ══════════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    private TableView<RouteRow> buildRouteTable() {
        TableView<RouteRow> t = new TableView<>(routeRows);
        t.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        Label ph = new Label(
                "Solve a route to see the step-by-step report here.\n"
                + "Benchmark \"Time (ms)\" is solver runtime; this table's \"Time (s)\" "
                + "is cumulative travel time along the route.");
        ph.setWrapText(true);
        ph.setStyle("-fx-text-alignment: center;");
        t.setPlaceholder(ph);

        TableColumn<RouteRow, String> colStep = new TableColumn<>("#");
        colStep.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().step()));
        colStep.setMaxWidth(36); colStep.setMinWidth(36);

        TableColumn<RouteRow, String> colCity = new TableColumn<>("City");
        colCity.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().city()));
        colCity.setMinWidth(70);

        TableColumn<RouteRow, String> colDist = new TableColumn<>("Distance (m)");
        colDist.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().distanceM()));
        colDist.setPrefWidth(96);

        TableColumn<RouteRow, String> colTime = new TableColumn<>("Time (s)");
        colTime.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().timeS()));
        colTime.setPrefWidth(88);

        TableColumn<RouteRow, String> colDead = new TableColumn<>("Deadline");
        colDead.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().deadline()));
        colDead.setPrefWidth(78);

        TableColumn<RouteRow, String> colNote = new TableColumn<>("Note");
        colNote.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().note()));
        colNote.setMinWidth(36);

        t.getColumns().addAll(colStep, colCity, colDist, colTime, colDead, colNote);

        for (TableColumn<RouteRow, ?> c : t.getColumns()) c.setSortable(false);

        t.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(RouteRow row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setStyle("");
                    return;
                }
                if (row.banner() == RouteBanner.VALID_STATUS) {
                    setStyle("-fx-background-color: #e8f5e9; -fx-font-weight: bold;");
                } else if (row.banner() == RouteBanner.INVALID_STATUS) {
                    setStyle("-fx-background-color: #ffcccc; -fx-font-weight: bold;");
                } else if ("LATE".equals(row.note())) {
                    setStyle("-fx-background-color: #ffe0e0;");
                } else {
                    setStyle("");
                }
            }
        });
        return t;
    }

    private void showRouteReport(Route<? extends City> route) {
        routeRows.clear();
        showRouteReportTyped(route);
    }

    private <T extends City> void showRouteReportTyped(Route<T> route) {
        List<T>      path         = route.getPath();
        List<Double> arrivalTimes = route.getArrivalTimes();
        List<Double> legDistances = route.getLegDistances();

        if (route.isValid()) {
            routeRows.add(new RouteRow(RouteBanner.VALID_STATUS, "\u2713", "VALID ROUTE",
                    "\u2014", "\u2014", "\u2014", ""));
        } else {
            routeRows.add(new RouteRow(RouteBanner.INVALID_STATUS, "\u26A0", "INVALID ROUTE",
                    "\u2014", "\u2014", "\u2014", "Route could not be completed"));
        }

        for (int i = 0; i < path.size(); i++) {
            T      c       = path.get(i);
            double arrival = i < arrivalTimes.size() ? arrivalTimes.get(i) : -1.0;
            double legDist = i < legDistances.size() ? legDistances.get(i) : -1.0;

            String timeStr = arrival >= 0 ? String.format("%.1f", arrival) : "\u2014";
            String distStr = i == 0 ? "start"
                          : legDist >= 0 ? String.format("%.0f", legDist) : "\u2014";
            String dead   = c.hasDeadline()
                          ? String.format("%.0f s", c.getDeadline()) : "\u2014";
            String note   = "";
            if (c.hasDeadline() && arrival >= 0)
                note = arrival > c.getDeadline() ? "LATE" : "ok";

            routeRows.add(new RouteRow(RouteBanner.NONE,
                String.valueOf(i + 1), c.getID(), distStr, timeStr, dead, note));
        }

        routeRows.add(new RouteRow(RouteBanner.NONE,
            "\u2014", "TOTAL",
            String.format("%.0f", route.getTotalDistance()),
            String.format("%.1f", route.getTotalTime()),
            "\u2014", ""));
    }

    private void clearRouteReport() { routeRows.clear(); }

    // ══════════════════════════════════════════════════════════
    // Loading / status helpers
    // ══════════════════════════════════════════════════════════

    private void showLoading() {
        activeQueries++;
        loadingSpinner.setVisible(true);
    }

    private void hideLoading() {
        activeQueries = Math.max(0, activeQueries - 1);
        if (activeQueries == 0) loadingSpinner.setVisible(false);
    }

    private void showStatus(String message) {
        if (message == null || message.isBlank()) {
            statusLabel.setVisible(false);
            statusLabel.setText("");
        } else {
            statusLabel.setText(message);
            statusLabel.setVisible(true);
        }
    }

    // ══════════════════════════════════════════════════════════
    // Helpers
    // ══════════════════════════════════════════════════════════

    /**
     * Sorts overlay pins by importance descending — the most globally significant
     * places always shown first regardless of viewport position.
     *
     * <p>A proximity-weighted sort was previously used when zoomed in, but it caused
     * clumping: many low-importance places clustered near the viewport centre would
     * push high-importance cities off screen. Pure importance sort is consistent
     * whether pins came from the bundled data or from Overpass.
     */
    private static void sortPins(List<MapMarker> list) {
        list.sort((a, b) -> Double.compare(b.importance(), a.importance()));
    }
}
