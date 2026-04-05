package ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
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
        UiController   ui        = new UiController();
        Tab            solverTab = new Tab("\uD83D\uDDFA Solver", ui.getRoot());
        solverTab.setClosable(false);

        // ── Benchmark tab ─────────────────────────────────────
        BenchmarkPane benchmark = new BenchmarkPane();
        Tab           benchTab  = new Tab("\uD83D\uDCCA Benchmark", benchmark.getRoot());
        benchTab.setClosable(false);

        // Forward city list from Solver tab to Benchmark tab when the button is clicked.
        ui.onSendToBenchmark = cities -> {
            benchmark.addSessionInstance(cities, ui.getStartCity());
        };

        // ── Tab pane ──────────────────────────────────────────
        TabPane tabs = new TabPane(solverTab, benchTab);
        tabs.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);

        // ── Scene ─────────────────────────────────────────────
        Scene scene = new Scene(tabs, 1280, 800);

        var css = getClass().getResource("/styles/app.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

        stage.setTitle("TSP Solver");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
