package ui;

import benchmark.TestInstance;
import benchmark.TestInstanceLibrary;
import data.Matrix;
import domain.AirCity;
import domain.City;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import model.Route;
import solver.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The Benchmark tab.
 *
 * <h3>Layout — left panel</h3>
 * <ul>
 *   <li><b>Built-in instances</b> — ComboBox with the 8 named library instances.</li>
 *   <li><b>Session instances</b> — a {@link ListView} of card-style rows (name,
 *       city count, preview, Remove) for city-sets sent from the Solver tab.</li>
 *   <li>Solver checkboxes, Run button, status label, tile toggle.</li>
 * </ul>
 *
 * <h3>Active instance</h3>
 * Either the built-in ComboBox selection or a clicked session row drives
 * {@link #currentInstance}. Selecting one clears the other's highlight so
 * only one source is active at a time.
 *
 * <h3>Thread safety</h3>
 * Solvers run on daemon threads; results are marshalled back to the FX thread
 * via {@link Platform#runLater}.
 */
public final class BenchmarkPane {
    private static final double DIVIDER_MIN = 0.30;
    private static final double DIVIDER_MAX = 0.93;

    // ── Root ─────────────────────────────────────────────────────────────────
    private final BorderPane root = new BorderPane();

    // ── Left panel controls ───────────────────────────────────────────────────
    private final ComboBox<TestInstance<AirCity>> instanceBox = new ComboBox<>();
    private final Map<String, CheckBox>           solverBoxes = new LinkedHashMap<>();
    private final Button                          runBtn      = new Button("\u25B6 Run");
    private final Label                           statusLabel = new Label(
            "Choose an instance and solvers, then press Run.");
    private final CheckBox tilesToggle = new CheckBox("Live map tiles");

    // ── Session instance table ────────────────────────────────────────────────
    /** One saved session sent from the Solver tab. */
    private record SessionRow(TestInstance<AirCity> instance) {
        String name()      { return instance.name(); }
        /** Comma-separated list of city IDs, capped for card preview. */
        String cityList() {
            String raw = instance.cities().stream()
                    .map(AirCity::getID)
                    .collect(Collectors.joining(", "));
            return raw.length() > 72 ? raw.substring(0, 69) + "…" : raw;
        }
        String fullCityList() {
            return instance.cities().stream()
                    .map(AirCity::getID)
                    .collect(Collectors.joining(", "));
        }
    }

    private final ObservableList<SessionRow> sessionRows = FXCollections.observableArrayList();
    private final ListView<SessionRow>      sessionList  = new ListView<>(sessionRows);
    private int sessionCounter = 0;

    // ── Right: map + results table ────────────────────────────────────────────
    private final MapViewPane              map   = new MapViewPane();
    private final TableView<BenchRow>      table = new TableView<>();
    private final ObservableList<BenchRow> rows  = FXCollections.observableArrayList();

    private final Map<String, Route<AirCity>> latestRoutes = new LinkedHashMap<>();
    private TestInstance<AirCity>             currentInstance = null;
    private double                            optimalDist     = Double.NaN;

    // ══════════════════════════════════════════════════════════
    // Constructor
    // ══════════════════════════════════════════════════════════

    public BenchmarkPane() {
        buildLayout();
        buildResultsTable();
        buildSessionList();
        wireEvents();
        map.setWrapX(true);
        map.setShowLabels(true);
        tilesToggle.setSelected(true);
        map.setUseTiles(true);
    }

    public BorderPane getRoot() { return root; }

    // ══════════════════════════════════════════════════════════
    // Public API — receive cities from the Solver tab
    // ══════════════════════════════════════════════════════════

    /**
     * Adds a city list sent from the Solver tab as a benchmarkable session
     * instance.
     *
     * <p>Cities are converted to {@link AirCity} (preserving coordinates, ID,
     * and deadline) so the Haversine benchmark engine works uniformly across
     * city types. GroundCity sets are therefore also accepted.
     *
     * <p>The new instance appears at the top of the session table and is
     * automatically selected as the active instance.
     *
     * @param cities    the current city list from the Solver tab
     * @param startCity the depot; must be an element of {@code cities}
     */
    public void addSessionInstance(List<City> cities, City startCity) {
        if (cities == null || cities.size() < 2 || startCity == null) return;
        if (!cities.contains(startCity)) return;

        // Convert to AirCity, preserving coordinates, ID, and deadline.
        List<AirCity> airCities = new ArrayList<>();
        for (City c : cities) {
            double dl = c.hasDeadline() ? c.getDeadline() : City.NO_DEADLINE;
            airCities.add(new AirCity(c.getID(), c.getX(), c.getY(), dl));
        }

        // Locate the converted start city by coordinate.
        AirCity airStart = airCities.get(0);
        for (AirCity ac : airCities) {
            if (Math.abs(ac.getX() - startCity.getX()) < 1e-9
                    && Math.abs(ac.getY() - startCity.getY()) < 1e-9) {
                airStart = ac;
                break;
            }
        }

        if (sessionRows.isEmpty()) sessionCounter = 0;
        String name = "Session " + (++sessionCounter);
        TestInstance<AirCity> inst =
                new TestInstance<>(name, List.copyOf(airCities), airStart);

        // Prepend so the newest instance is at the top.
        sessionRows.add(0, new SessionRow(inst));

        // Auto-select the new session instance.
        selectSessionRow(0);
    }

    // ══════════════════════════════════════════════════════════
    // Layout
    // ══════════════════════════════════════════════════════════

    private void buildLayout() {
        // ── Left panel ────────────────────────────────────────
        VBox left = new VBox(10);
        left.setPadding(new Insets(14));
        left.setPrefWidth(260);

        // Built-in instances
        Label builtInLabel = new Label("Built-in instance:");
        builtInLabel.setStyle("-fx-font-weight: bold;");
        instanceBox.setMaxWidth(Double.MAX_VALUE);
        instanceBox.setPromptText("Select instance\u2026");
        instanceBox.getItems().setAll(TestInstanceLibrary.all());
        instanceBox.setCellFactory(lv -> instanceCell());
        instanceBox.setButtonCell(instanceCell());

        // Session instances (from Solver tab)
        Label sessionLabel = new Label("Your sessions (from Solver tab)");
        sessionLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
        Label sessionHint = new Label("Send a city list with \uD83D\uDCCA Send to Benchmark. "
                + "Click a card to load it; Remove deletes only that saved set.");
        sessionHint.setWrapText(true);
        sessionHint.setStyle("-fx-font-size: 10; -fx-text-fill: #64748b;");

        // Solver checkboxes
        Label solverLabel = new Label("Solvers to run:");
        solverLabel.setStyle("-fx-font-weight: bold;");

        String[] names = {
            "BruteForce",
            "2-Opt (multi-start)",
            "Slack Insertion",
            "Nearest Neighbour"
        };
        for (String name : names) {
            CheckBox cb = new CheckBox(name);
            cb.setSelected(true);
            solverBoxes.put(name, cb);
        }

        Label bruteNote = new Label("* BruteForce refuses n > 10");
        bruteNote.setStyle("-fx-font-size: 10; -fx-text-fill: #888;");
        bruteNote.setWrapText(true);

        runBtn.setMaxWidth(Double.MAX_VALUE);
        runBtn.setStyle("-fx-font-size: 14; -fx-font-weight: bold;");

        statusLabel.setWrapText(true);
        statusLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #555;");

        Button selectAll  = new Button("All");
        Button selectNone = new Button("None");
        selectAll .setOnAction(e -> solverBoxes.values().forEach(c -> c.setSelected(true)));
        selectNone.setOnAction(e -> solverBoxes.values().forEach(c -> c.setSelected(false)));
        HBox selBtns = new HBox(6, selectAll, selectNone);

        tilesToggle.setOnAction(e -> map.setUseTiles(tilesToggle.isSelected()));

        ScrollPane leftScroll = new ScrollPane();
        VBox leftContent = new VBox(10,
                builtInLabel, instanceBox,
                new Separator(),
                sessionLabel, sessionHint, sessionList,
                new Separator(),
                solverLabel, selBtns);
        leftContent.getChildren().addAll(solverBoxes.values());
        leftContent.getChildren().addAll(
                bruteNote, new Separator(), runBtn, statusLabel, tilesToggle);
        leftContent.setPadding(new Insets(0));
        leftScroll.setContent(leftContent);
        leftScroll.setFitToWidth(true);
        leftScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        left.getChildren().add(leftScroll);
        VBox.setVgrow(leftScroll, Priority.ALWAYS);

        // ── Right panel ────────────────────────────────────────
        SplitPane splitV = new SplitPane();
        splitV.setOrientation(Orientation.VERTICAL);
        splitV.getItems().addAll(map, table);
        splitV.setDividerPositions(0.65);
        map.setMinHeight(150);
        table.setMinHeight(80);
        SplitPane.setResizableWithParent(map, true);
        SplitPane.setResizableWithParent(table, true);
        splitV.getDividers().get(0).positionProperty().addListener((obs, oldV, newV) -> {
            double p = newV.doubleValue();
            if (p < DIVIDER_MIN) splitV.setDividerPositions(DIVIDER_MIN);
            else if (p > DIVIDER_MAX) splitV.setDividerPositions(DIVIDER_MAX);
        });

        root.setLeft(left);
        root.setCenter(splitV);
        BorderPane.setMargin(left, new Insets(0));
    }

    // ══════════════════════════════════════════════════════════
    // Session instance list (card-style)
    // ══════════════════════════════════════════════════════════

    private void buildSessionList() {
        Label ph = new Label("Nothing saved yet.\n"
                + "In the Solver tab, add cities and press \uD83D\uDCCA Send to Benchmark.");
        ph.setWrapText(true);
        ph.setStyle("-fx-text-fill: #64748b; -fx-padding: 12; -fx-text-alignment: center;");
        sessionList.setPlaceholder(ph);
        sessionList.setPrefHeight(220);
        sessionList.setMinHeight(100);
        sessionList.setFocusTraversable(true);

        sessionList.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(SessionRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                Label title = new Label(item.name());
                title.setStyle("-fx-font-weight: bold; -fx-font-size: 13px;");
                Label countLbl = new Label("·  " + item.instance().size() + " cities");
                countLbl.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px;");
                Region spacer = new Region();
                HBox.setHgrow(spacer, Priority.ALWAYS);
                Button removeBtn = new Button("Remove");
                removeBtn.setFocusTraversable(false);
                removeBtn.setStyle(
                        "-fx-font-size: 11px; -fx-padding: 5 12 5 12; -fx-background-radius: 8; "
                        + "-fx-background-color: #fecaca; -fx-text-fill: #991b1b; "
                        + "-fx-cursor: hand;");
                removeBtn.setOnAction(e -> {
                    e.consume();
                    removeSessionRow(item);
                });

                HBox top = new HBox(8, title, countLbl, spacer, removeBtn);
                top.setAlignment(Pos.CENTER_LEFT);

                Label preview = new Label(item.cityList());
                preview.setWrapText(true);
                preview.setMaxWidth(248);
                preview.setStyle("-fx-text-fill: #475569; -fx-font-size: 11px;");

                VBox card = new VBox(8, top, preview);
                card.setPadding(new Insets(10, 12, 12, 12));
                boolean active = item.instance().equals(currentInstance);
                card.setStyle(active
                        ? "-fx-background-color: #e3f2fd; -fx-background-radius: 10; "
                        + "-fx-border-color: #1e88e5; -fx-border-radius: 10; -fx-border-width: 2;"
                        : "-fx-background-color: #f8fafc; -fx-background-radius: 10; "
                        + "-fx-border-color: #e2e8f0; -fx-border-radius: 10; -fx-border-width: 1;");

                Tooltip tip = new Tooltip(item.name() + "\n\n" + item.fullCityList());
                tip.setWrapText(true);
                tip.setMaxWidth(400);
                Tooltip.install(card, tip);

                setGraphic(card);
                setText(null);
            }
        });

        sessionList.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> {
                    if (sel == null) return;
                    instanceBox.getSelectionModel().clearSelection();
                    activateInstance(sel.instance());
                    sessionList.refresh();
                });
    }

    private void removeSessionRow(SessionRow row) {
        sessionRows.remove(row);
        if (currentInstance != null && currentInstance.equals(row.instance())) {
            currentInstance = null;
            rows.clear();
            latestRoutes.clear();
            optimalDist = Double.NaN;
            map.setCities(new ArrayList<>());
            map.setRoute(null);
            statusLabel.setText("Choose an instance and solvers, then press Run.");
        }
        sessionList.refresh();
    }

    // ══════════════════════════════════════════════════════════
    // Results table
    // ══════════════════════════════════════════════════════════

    private void buildResultsTable() {
        table.setItems(rows);
        table.setPlaceholder(new Label("Run a benchmark to see results."));
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);

        table.getColumns().add(col("Solver",       r -> r.solver));
        table.getColumns().add(col("Distance (m)", r -> r.distance));
        table.getColumns().add(col("Time (ms)",    r -> r.timeMs));
        table.getColumns().add(col("Valid",        r -> r.valid));
        table.getColumns().add(col("Gap vs opt",   r -> r.gap));

        table.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(BenchRow item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null)         setStyle("");
                else if (item.refused)             setStyle("-fx-background-color: #f0f0f0;");
                else if ("YES".equals(item.valid)) setStyle("-fx-background-color: #e8f5e9;");
                else                               setStyle("-fx-background-color: #ffebee;");
            }
        });

        table.getSelectionModel().selectedItemProperty().addListener((obs, old, sel) -> {
            if (sel == null || currentInstance == null) return;
            Route<AirCity> route = latestRoutes.get(sel.solver);
            if (route == null) return;
            loadRouteOnMap(route, currentInstance);
        });
    }

    private TableColumn<BenchRow, String> col(String header,
            java.util.function.Function<BenchRow, String> getter) {
        TableColumn<BenchRow, String> c = new TableColumn<>(header);
        c.setCellValueFactory(cd -> new SimpleStringProperty(getter.apply(cd.getValue())));
        return c;
    }

    // ══════════════════════════════════════════════════════════
    // Events
    // ══════════════════════════════════════════════════════════

    private void wireEvents() {
        instanceBox.setOnAction(e -> {
            TestInstance<AirCity> inst = instanceBox.getValue();
            if (inst == null) return;
            // Deselect session list so only one source is active.
            sessionList.getSelectionModel().clearSelection();
            activateInstance(inst);
            sessionList.refresh();
        });

        runBtn.setOnAction(e -> runBenchmark());
    }

    // ══════════════════════════════════════════════════════════
    // Instance activation (shared by both sources)
    // ══════════════════════════════════════════════════════════

    /**
     * Sets {@code inst} as the active instance: updates {@link #currentInstance},
     * clears previous results, loads the cities onto the map, and resets status.
     */
    private void activateInstance(TestInstance<AirCity> inst) {
        currentInstance = inst;
        rows.clear();
        latestRoutes.clear();
        optimalDist = Double.NaN;
        loadCitiesOnMap(inst);
        statusLabel.setText("Ready. Press Run to benchmark.");
        sessionList.refresh();
    }

    /** Selects and activates the session row at position {@code index}. */
    private void selectSessionRow(int index) {
        if (index < 0 || index >= sessionRows.size()) return;
        instanceBox.getSelectionModel().clearSelection();
        sessionList.getSelectionModel().select(index);
        // activateInstance is called by the selection listener.
    }

    // ══════════════════════════════════════════════════════════
    // Running the benchmark
    // ══════════════════════════════════════════════════════════

    private void runBenchmark() {
        if (currentInstance == null) {
            statusLabel.setText("Please select an instance first.");
            return;
        }

        List<String> selected = new ArrayList<>();
        for (var entry : solverBoxes.entrySet()) {
            if (entry.getValue().isSelected()) selected.add(entry.getKey());
        }
        if (selected.isEmpty()) {
            statusLabel.setText("Please select at least one solver.");
            return;
        }

        rows.clear();
        latestRoutes.clear();
        optimalDist = Double.NaN;

        setRunning(true);
        statusLabel.setText("Running\u2026 (0 / " + selected.size() + " done)");

        int[] done = {0};
        int   total = selected.size();

        for (String solverName : selected) {
            Task<BenchRow> task = buildTask(solverName, currentInstance);

            task.setOnSucceeded(ev -> {
                BenchRow row = task.getValue();
                Platform.runLater(() -> {
                    if (row.route != null) latestRoutes.put(row.solver, row.route);

                    if ("BruteForce".equals(row.solver) && "YES".equals(row.valid)
                            && row.rawDist >= 0) {
                        optimalDist = row.rawDist;
                        recomputeGaps();
                    }
                    rows.add(row.withGap(
                            formatGap(row.rawDist, optimalDist, "YES".equals(row.valid))));
                    done[0]++;
                    statusLabel.setText(
                            "Running\u2026 (" + done[0] + " / " + total + " done)");
                    if (done[0] == total) {
                        setRunning(false);
                        statusLabel.setText("Done. Click a row to see its route on the map.");
                        for (BenchRow r : rows) {
                            if ("YES".equals(r.valid)) {
                                table.getSelectionModel().select(r);
                                break;
                            }
                        }
                    }
                });
            });

            task.setOnFailed(ev -> Platform.runLater(() -> {
                done[0]++;
                rows.add(BenchRow.error(solverName));
                if (done[0] == total) {
                    setRunning(false);
                    statusLabel.setText("Done (some solvers failed \u2014 see table).");
                }
            }));

            Thread t = new Thread(task, "benchmark-" + solverName);
            t.setDaemon(true);
            t.start();
        }
    }

    private Task<BenchRow> buildTask(String solverName, TestInstance<AirCity> inst) {
        return new Task<>() {
            @Override protected BenchRow call() {
                Matrix<AirCity> matrix = new Matrix<>(AirCity.class);
                for (AirCity c : inst.cities()) matrix.addCity(c);
                matrix.populateMatrix();

                Solver<AirCity> solver;
                try {
                    solver = createSolver(solverName, matrix);
                } catch (Exception ex) {
                    return BenchRow.error(solverName);
                }

                long t0 = System.nanoTime();
                Route<AirCity> route;
                try {
                    route = solver.solve(inst.startCity());
                } catch (IllegalArgumentException ex) {
                    long ms = (System.nanoTime() - t0) / 1_000_000;
                    return BenchRow.refused(solverName, ms);
                }
                long ms = (System.nanoTime() - t0) / 1_000_000;
                return BenchRow.of(solverName, route, ms);
            }
        };
    }

    private Solver<AirCity> createSolver(String name, Matrix<AirCity> matrix) {
        return switch (name) {
            case "BruteForce"          -> new BruteForceSolver<>(matrix);
            case "2-Opt (multi-start)" -> new SlackInsertion2OptSolver<>(matrix);
            case "Slack Insertion"     -> new SlackInsertionSolver<>(matrix);
            case "Nearest Neighbour"   -> new NearestNeighborSolver<>(matrix);
            default -> throw new IllegalArgumentException("Unknown solver: " + name);
        };
    }

    // ══════════════════════════════════════════════════════════
    // Map helpers
    // ══════════════════════════════════════════════════════════

    private void loadCitiesOnMap(TestInstance<AirCity> inst) {
        map.setCities(new ArrayList<>(inst.cities()));
        map.setStartCity(inst.startCity());
        map.setRoute(null);
        map.fitBounds(inst.cities(), 0.15);
    }

    private void loadRouteOnMap(Route<AirCity> route, TestInstance<AirCity> inst) {
        map.setCities(new ArrayList<>(inst.cities()));
        map.setStartCity(inst.startCity());
        map.setRoute(new ArrayList<>(route.getPath()));
    }

    // ══════════════════════════════════════════════════════════
    // UI state helpers
    // ══════════════════════════════════════════════════════════

    private void setRunning(boolean running) {
        runBtn.setDisable(running);
        runBtn.setText(running ? "\u23F3 Running\u2026" : "\u25B6 Run");
        instanceBox.setDisable(running);
        sessionList.setDisable(running);
        solverBoxes.values().forEach(cb -> cb.setDisable(running));
    }

    private void recomputeGaps() {
        ObservableList<BenchRow> updated = FXCollections.observableArrayList();
        for (BenchRow r : rows)
            updated.add(r.withGap(formatGap(r.rawDist, optimalDist, "YES".equals(r.valid))));
        rows.setAll(updated);
    }

    private ListCell<TestInstance<AirCity>> instanceCell() {
        return new ListCell<>() {
            @Override protected void updateItem(TestInstance<AirCity> item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                setText(item.name() + "  (" + item.size() + " cities)");
            }
        };
    }

    // ══════════════════════════════════════════════════════════
    // Gap formatting
    // ══════════════════════════════════════════════════════════

    static String formatGap(double heuristicDist, double optimalDist, boolean valid) {
        if (!valid)                      return "INVALID";
        if (Double.isNaN(optimalDist))   return "N/A";
        if (Double.isNaN(heuristicDist)) return "INVALID";
        double pct = (heuristicDist - optimalDist) / optimalDist * 100.0;
        return pct < 0.005 ? "optimal" : String.format("+%.2f%%", pct);
    }

    // ══════════════════════════════════════════════════════════
    // Result row model
    // ══════════════════════════════════════════════════════════

    /**
     * One row in the solver results table. All display fields are pre-formatted
     * strings; {@link #rawDist} is kept as a double for gap recalculation when
     * the BruteForce optimal becomes available.
     */
    static final class BenchRow {
        final String         solver;
        final String         distance;
        final String         timeMs;
        final String         valid;
        String               gap;
        final double         rawDist;
        final boolean        refused;
        final Route<AirCity> route;

        private BenchRow(String solver, String distance, String timeMs,
                         String valid, String gap, double rawDist,
                         boolean refused, Route<AirCity> route) {
            this.solver   = solver;
            this.distance = distance;
            this.timeMs   = timeMs;
            this.valid    = valid;
            this.gap      = gap;
            this.rawDist  = rawDist;
            this.refused  = refused;
            this.route    = route;
        }

        static BenchRow of(String solver, Route<AirCity> route, long ms) {
            boolean v    = route.isValid();
            double  dist = v ? route.getTotalDistance() : Double.NaN;
            return new BenchRow(solver,
                    v ? String.format("%,.0f m", dist) : "INVALID",
                    ms + " ms", v ? "YES" : "NO", "\u2026", dist, false, route);
        }

        static BenchRow refused(String solver, long ms) {
            return new BenchRow(solver, "\u2014", ms + " ms", "\u2014",
                    "N/A (n > 10)", Double.NaN, true, null);
        }

        static BenchRow error(String solver) {
            return new BenchRow(solver, "ERROR", "\u2014", "NO",
                    "\u2014", Double.NaN, true, null);
        }

        BenchRow withGap(String newGap) {
            return new BenchRow(solver, distance, timeMs, valid,
                    newGap, rawDist, refused, route);
        }
    }
}
