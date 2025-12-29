package ui;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

public final class TspApp extends Application {
    @Override
    public void start(Stage stage) {
        UiController ui = new UiController();

        Scene scene = new Scene(ui.getRoot(), 1200, 750);

        var css = getClass().getResource("/styles/app.css");
        if (css != null) scene.getStylesheets().add(css.toExternalForm());

        stage.setTitle("TSP Solver (Map UI)");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}