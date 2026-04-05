package ui;

import data.Matrix;
import domain.City;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import model.Route;
import solver.*;

import java.util.List;
import java.util.function.Consumer;

/**
 * Solver controls shown in the left panel of the Solver tab.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Solver algorithm selection.</li>
 *   <li>"Solve" button — builds the matrix, runs the solver, fires
 *       {@link #onRouteReady}.</li>
 *   <li>"Send to Benchmark" button — enabled as soon as ≥ 2 cities are
 *       present; sends the current city list (and start city) to the
 *       Benchmark tab via {@link #onSendToBenchmark}. Works for all city
 *       types (AirCity and GroundCity). Does not require a solve first.</li>
 *   <li>Status label showing ✅ VALID / ❌ INVALID after each solve.</li>
 *   <li>Matrix build and solve run on a background {@link Task} so the UI stays
 *       responsive; optional {@link #setLoadingHooks} ties into the map spinner.</li>
 * </ul>
 */
public final class SolverPanel {

    // ── Solver display names ──────────────────────────────────────────────────
    private static final String OPT_2OPT    = "Optimised (multi-start + 2-opt)";
    private static final String FAST        = "Fast (slack insertion)";
    private static final String NEAREST     = "Nearest Neighbour (greedy)";
    private static final String BRUTE_FORCE = "Brute Force (exact, \u2264 10 non-start cities)";

    // ── UI ────────────────────────────────────────────────────────────────────
    private final VBox             root          = new VBox(10);
    private final ComboBox<String> solverBox     = new ComboBox<>();
    private final Button           solveBtn      = new Button("\u2705 Solve");
    private final Button           sendBenchBtn  = new Button("\uD83D\uDCCA Send to Benchmark");
    private final Label            statusLbl     = new Label();

    // ── State ─────────────────────────────────────────────────────────────────
    private final ObservableList<City> cities;

    // ── Callbacks set by UiController ─────────────────────────────────────────
    Consumer<Route<? extends City>> onRouteReady;
    Consumer<List<City>>            onSendToBenchmark; // fired by Send to Benchmark button
    java.util.function.Supplier<City>                  startCitySupplier;
    java.util.function.Supplier<Class<? extends City>> modeSupplier;

    private Runnable loadingStart = () -> {};
    private Runnable loadingEnd   = () -> {};

    // ══════════════════════════════════════════════════════════
    // Constructor
    // ══════════════════════════════════════════════════════════

    public SolverPanel(ObservableList<City> sharedCities) {
        this.cities = sharedCities;
        build();

        // Keep "Send to Benchmark" enabled only when ≥ 2 cities are present.
        cities.addListener((javafx.collections.ListChangeListener<City>) c -> {
            sendBenchBtn.setDisable(cities.size() < 2);
        });
        sendBenchBtn.setDisable(true); // initially no cities
    }

    public VBox getRoot() { return root; }

    /**
     * Invoked around background solve (matrix population + solver). Null arguments
     * are treated as no-ops. Typically wired to {@link UiController#showLoading} /
     * {@link UiController#hideLoading}.
     */
    public void setLoadingHooks(Runnable start, Runnable end) {
        this.loadingStart = start != null ? start : () -> {};
        this.loadingEnd   = end != null ? end : () -> {};
    }

    /** Clears the status label (called when the city list or start city changes). */
    public void clearLastRoute() {
        statusLbl.setText("");
        statusLbl.setStyle("");
    }

    // ══════════════════════════════════════════════════════════
    // Building
    // ══════════════════════════════════════════════════════════

    private void build() {
        solverBox.getItems().setAll(OPT_2OPT, FAST, NEAREST, BRUTE_FORCE);
        solverBox.getSelectionModel().select(0);

        solverBox.setOnAction(e -> {
            if (BRUTE_FORCE.equals(solverBox.getValue())) {
                new Alert(Alert.AlertType.INFORMATION,
                    "Brute Force is exact but O(n!).\n" +
                    "Safe only for \u2264 10 non-start cities (11 total).\n" +
                    "It will refuse larger instances.")
                    .showAndWait();
            }
        });

        solveBtn.setOnAction(e -> runSolver());

        sendBenchBtn.setMaxWidth(Double.MAX_VALUE);
        sendBenchBtn.setTooltip(new Tooltip(
                "Send the current city list to the Benchmark tab\n" +
                "so all four solvers can be run against it.\n" +
                "Works for AirCity and GroundCity."));
        sendBenchBtn.setOnAction(e -> sendToBenchmark());

        statusLbl.setWrapText(true);

        root.getChildren().addAll(
                new Label("Solver:"), solverBox, solveBtn,
                sendBenchBtn, statusLbl);
    }

    // ══════════════════════════════════════════════════════════
    // Solving
    // ══════════════════════════════════════════════════════════

    private void runSolver() {
        if (modeSupplier == null || startCitySupplier == null) return;

        Class<? extends City> type  = modeSupplier.get();
        City                  start = startCitySupplier.get();

        if (type == null || start == null || cities.size() < 2) {
            alert("Invalid input",
                  "Choose a mode, a start city, and add at least 2 cities.");
            return;
        }

        solveTyped(type, start);
    }

    private <T extends City> void solveTyped(Class<T> type, City rawStart) {
        if (!type.isInstance(rawStart)) {
            alert("Type mismatch", "Start city is not of the selected type.");
            return;
        }

        for (City c : cities) {
            if (!type.isInstance(c)) {
                alert("Type mismatch", "All cities must be of the same type.");
                return;
            }
        }

        final String solverChoice = solverBox.getValue();
        final T      start        = type.cast(rawStart);

        loadingStart.run();
        solveBtn.setDisable(true);

        Task<Route<T>> task = new Task<>() {
            @Override protected Route<T> call() throws Exception {
                Matrix<T> matrix = new Matrix<>(type);
                for (City c : cities) matrix.addCity(type.cast(c));
                if (!matrix.populateMatrix() || !matrix.checkIntegrity()) {
                    throw new IllegalStateException("Failed to compute the distance matrix.");
                }
                final Solver<T> solver;
                try {
                    solver = createSolver(solverChoice, matrix);
                } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException("FACTORY|" + ex.getMessage(), ex);
                }
                return solver.solve(start);
            }
        };

        task.setOnSucceeded(ev -> {
            loadingEnd.run();
            solveBtn.setDisable(false);
            Route<T> route = task.getValue();
            if (onRouteReady != null) onRouteReady.accept(route);
            boolean valid = route.isValid();
            statusLbl.setText(valid ? "\u2705 VALID" : "\u274C INVALID");
            statusLbl.setStyle("-fx-font-weight: bold; -fx-text-fill: "
                    + (valid ? "green" : "red") + ";");
        });

        task.setOnFailed(ev -> {
            loadingEnd.run();
            solveBtn.setDisable(false);
            Throwable t = task.getException();
            String msg = t != null && t.getMessage() != null ? t.getMessage() : String.valueOf(t);
            if (t instanceof IllegalArgumentException && msg.startsWith("FACTORY|")) {
                alert("Solver error", msg.substring("FACTORY|".length()));
            } else if (t instanceof IllegalArgumentException) {
                alert("Solver refused", msg);
            } else if (t instanceof IllegalStateException) {
                alert("Matrix error", msg);
            } else {
                alert("Solve failed", msg);
            }
        });

        Thread worker = new Thread(task, "tsp-solver");
        worker.setDaemon(true);
        worker.start();
    }

    // ══════════════════════════════════════════════════════════
    // Send to Benchmark
    // ══════════════════════════════════════════════════════════

    /**
     * Sends the current city list to the Benchmark tab.
     * Enabled for both AirCity and GroundCity as long as ≥ 2 cities exist.
     * No solve is required first.
     */
    private void sendToBenchmark() {
        if (cities.size() < 2) return; // button should already be disabled
        if (onSendToBenchmark != null) {
            onSendToBenchmark.accept(List.copyOf(cities));
        }
    }

    // ── Solver factory ────────────────────────────────────────────────────────

    private <T extends City> Solver<T> createSolver(String name, Matrix<T> matrix) {
        if (OPT_2OPT.equals(name))    return new SlackInsertion2OptSolver<>(matrix);
        if (FAST.equals(name))        return new SlackInsertionSolver<>(matrix);
        if (NEAREST.equals(name))     return new NearestNeighborSolver<>(matrix);
        if (BRUTE_FORCE.equals(name)) return new BruteForceSolver<>(matrix);
        return new SlackInsertion2OptSolver<>(matrix);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void alert(String title, String msg) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(null);
        TextArea area = new TextArea(msg);
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefRowCount(Math.min(8, msg.split("\n").length + 1));
        area.setPrefWidth(400);
        area.setStyle("-fx-background-color: transparent; -fx-background-insets: 0;");
        dialog.getDialogPane().setContent(area);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dialog.getDialogPane().setPrefWidth(440);
        dialog.showAndWait();
    }
}
