package ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.Image;
import javafx.stage.Stage;

/**
 * JavaFX entry point.
 *
 * <h3>Tabs</h3>
 * <ul>
 *   <li><b>Solver</b> — interactive map, city management, solver execution.</li>
 *   <li><b>Benchmark</b> — runs all four solvers against named instances or
 *       city sets sent from the Solver tab.</li>
 * </ul>
 *
 * <h3>Cross-tab wiring</h3>
 * When the user clicks "Send to Benchmark" in the Solver tab, the current city
 * list is forwarded to {@link BenchmarkPane#addSessionInstance} so the user can
 * benchmark it without manually re-entering cities. Session instances persist
 * for the lifetime of the application and survive tab switching.
 */
public final class TspApp extends Application {

    @Override
    public void start(Stage stage) {

        // ── Solver tab ────────────────────────────────────────
        UiController ui       = new UiController();
        Tab          solverTab = new Tab("\uD83D\uDDFA Solver", ui.getRoot());
        solverTab.setClosable(false);

        // ── Benchmark tab ─────────────────────────────────────
        BenchmarkPane benchmark = new BenchmarkPane();
        Tab           benchTab  = new Tab("\uD83D\uDCCA Benchmark", benchmark.getRoot());
        benchTab.setClosable(false);

        // Forward city list from Solver tab to Benchmark tab when the button is clicked.
        ui.onSendToBenchmark = cities ->
                benchmark.addSessionInstance(cities, ui.getStartCity());

        // ── Tab pane ──────────────────────────────────────────
        TabPane tabs = new TabPane(solverTab, benchTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // ── Scene ─────────────────────────────────────────────
        Scene scene = new Scene(tabs, 1280, 800);

        var css = getClass().getResource("/styles/app.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

        // ── Window icon ───────────────────────────────────────
        // Place tsp-icon.png in src/main/resources/images/
        // JavaFX will use the largest available size for the titlebar and taskbar.
        try {
            var icon512 = getClass().getResourceAsStream("/images/tsp-icon-600.png");
            var icon256 = getClass().getResourceAsStream("/images/tsp-icon-300.png");
            if (icon512 != null) stage.getIcons().add(new Image(icon512));
            if (icon256 != null) stage.getIcons().add(new Image(icon256));
        } catch (Exception ignored) {
            // Icon is decorative — a missing file must never crash the app.
        }

        stage.setTitle("TSP Solver");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
