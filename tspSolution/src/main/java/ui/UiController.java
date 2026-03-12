package ui;

import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.List;

import domain.City;
import domain.CityRegistry;

import data.Matrix;
import model.Route;

public final class UiController {

    private final BorderPane root = new BorderPane();
    private final MapViewPane mapPane = new MapViewPane();

    private final ApplicationState state = new ApplicationState();
    private final TspCoordinator coordinator = new TspCoordinator();

    private final ComboBox<Class<? extends City>> modeBox = new ComboBox<>();
    private final ComboBox<String> solverBox = new ComboBox<>();
    private final ComboBox<City> startBox = new ComboBox<>();
    private final TextField nameField = new TextField();
    private final TextField deadlineField = new TextField();
    private final Label coordLabel = new Label("Left-click = select. Shift+Drag = pan. Scroll = zoom.");

    private final Button showReportBtn = new Button("📄 Show Last Report");

    private double pendingLon = Double.NaN;
    private double pendingLat = Double.NaN;

    private VBox fullPanel;
    private VBox collapsedBar;

    public UiController() {
        buildLayout();
        hookEvents();
        loadModes();

        mapPane.setWrapX(true);
    }

    public Parent getRoot() {
        return root;
    }

    private void buildLayout() {
        root.setPadding(new Insets(0));
        root.setCenter(mapPane);

        fullPanel = buildFullPanel();
        collapsedBar = buildCollapsedBar();

        root.setLeft(fullPanel);
    }

    private VBox buildCollapsedBar() {
        VBox bar = new VBox(10);
        bar.setPadding(new Insets(10));
        bar.setPrefWidth(48);

        Button open = new Button("☰");
        open.setFocusTraversable(false);
        open.setOnAction(e -> root.setLeft(fullPanel));

        bar.getChildren().add(open);
        return bar;
    }

    private VBox buildFullPanel() {
        VBox left = new VBox(10);
        left.setPadding(new Insets(10));
        left.setPrefWidth(360);

        HBox header = new HBox(10);
        Label title = new Label("TSP Controls");
        title.setStyle("-fx-font-size: 18; -fx-font-weight: bold;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button hide = new Button("⯇");
        hide.setFocusTraversable(false);
        hide.setOnAction(e -> root.setLeft(collapsedBar));

        header.getChildren().addAll(title, spacer, hide);

        Button controlsBtn = new Button("ℹ Controls");
        controlsBtn.setOnAction(e -> alert("Controls",
                "• Left-click: select coordinate (shows target marker)\n" +
                "• Shift + Drag: pan\n" +
                "• Scroll: zoom\n" +
                "• Start city marker: gold ring + 'S'\n" +
                "• Deadline city marker: cyan ring + 'D'\n"
        ));

        solverBox.getItems().setAll("Optimized (2-Opt)", "Fast (no 2-Opt)");
        solverBox.getSelectionModel().select(0);

        modeBox.setPromptText("Choose City Type (Mode)");
        startBox.setPromptText("Choose Start City");

        nameField.setPromptText("City name (optional)");
        deadlineField.setPromptText("Deadline seconds (optional)");

        Button addBtn = new Button("📍 Add City");
        Button removeBtn = new Button("🗑 Remove Selected");
        Button clearBtn = new Button("🧹 Clear All");
        Button solveBtn = new Button("✅ Solve");

        CheckBox showLabels = new CheckBox("Show labels on map");
        showLabels.setSelected(true);
        showLabels.setOnAction(e -> mapPane.setShowLabels(showLabels.isSelected()));

        showReportBtn.setDisable(true);
        showReportBtn.setOnAction(e -> {
            if (state.getLastRoute() != null) alert("Last Report", state.getLastRoute().report());
        });

        ListView<City> cityListView = new ListView<>(state.getCities());
        cityListView.setPrefHeight(240);

        addBtn.setOnAction(e -> addCityFromPendingClick());

        removeBtn.setOnAction(e -> {
            City sel = cityListView.getSelectionModel().getSelectedItem();
            if (sel == null) return;

            state.getCities().remove(sel);
            clearSolutionOnly();

            refreshStartBox();
            mapPane.setCities(new ArrayList<>(state.getCities()));
            mapPane.setStartCity(startBox.getValue());
        });

        clearBtn.setOnAction(e -> clearAll());

        solveBtn.setOnAction(e -> solveNow());

        startBox.setOnAction(e -> {
            clearSolutionOnly();
            mapPane.setStartCity(startBox.getValue());
        });

        left.getChildren().addAll(
                header,
                controlsBtn,
                new Separator(),
                new Label("Mode:"),
                modeBox,
                new Label("Solver:"),
                solverBox,
                new Label("Start City:"),
                startBox,
                showLabels,
                new Separator(),
                coordLabel,
                nameField,
                deadlineField,
                addBtn,
                new Separator(),
                new Label("Cities (⏱ = has deadline):"),
                cityListView,
                removeBtn,
                clearBtn,
                new Separator(),
                solveBtn,
                showReportBtn
        );

        return left;
    }

    private void hookEvents() {
        mapPane.setOnMapClick((lon, lat) -> {
            pendingLon = lon;
            pendingLat = lat;

            coordLabel.setText(String.format("Selected: lon=%.6f  lat=%.6f", lon, lat));

            mapPane.setPendingMarker(lon, lat);
        });

        modeBox.setOnAction(e -> {
            Matrix.reset();
            clearAll();
        });
    }

    private void loadModes() {
        modeBox.getItems().setAll(CityRegistry.getRegisteredTypes());
        if (!modeBox.getItems().isEmpty()) modeBox.getSelectionModel().select(0);
    }

    private void addCityFromPendingClick() {
        if (Double.isNaN(pendingLon) || Double.isNaN(pendingLat)) {
            alert("No coordinates", "Click on the map first.");
            return;
        }

        Class<? extends City> type = modeBox.getValue();
        if (type == null) {
            alert("No mode", "Choose a city type first.");
            return;
        }

        City city = createCity(
                type,
                nameField.getText(),
                pendingLon,
                pendingLat,
                parseDeadline(deadlineField.getText())
        );

        if (city == null) {
            alert("Create failed", "Expected constructor:\n(String id, double x, double y, double deadline)");
            return;
        }

        if (state.getCities().contains(city)) {
            alert("Duplicate", "City with same coordinates already exists.");
            return;
        }

        state.getCities().add(city);

        clearSolutionOnly();
        refreshStartBox();

        mapPane.setCities(new ArrayList<>(state.getCities()));
        mapPane.setStartCity(startBox.getValue());

        pendingLon = Double.NaN;
        pendingLat = Double.NaN;
        mapPane.clearPendingMarker();
        coordLabel.setText("Left-click = select. Shift+Drag = pan. Scroll = zoom.");
    }

    private void solveNow() {
        Class<? extends City> type = modeBox.getValue();
        City start = startBox.getValue();

        if (type == null || start == null || state.getCities().size() < 2) {
            alert("Invalid input", "Choose mode, start city, and add at least 2 cities.");
            return;
        }

        Matrix.reset();
        solveNowTyped(type, start);
    }

    private <T extends City> void solveNowTyped(Class<T> type, City rawStart) {
        String apiKey = null;
        Matrix<T> matrix = Matrix.getInstance(type);

        if (matrix.requiresApi() && !Matrix.hasDataProvider()) {
            TextInputDialog d = new TextInputDialog();
            d.setTitle("Google API Key");
            d.setHeaderText("API required for this city type");
            d.setContentText("Enter API key:");

            var res = d.showAndWait();
            if (res.isEmpty() || res.get().isBlank()) return;
            apiKey = res.get().trim();
        }

        try {
            Route<T> route = coordinator.solve(
                    new ArrayList<>(state.getCities()),
                    type,
                    rawStart,
                    solverBox.getValue(),
                    apiKey
            );

            state.setLastRoute(new ApplicationState.RouteSnapshot(route.isValid(), route.toString()));
            showReportBtn.setDisable(false);

            mapPane.setStartCity(type.cast(rawStart));

            List<City> draw = new ArrayList<>();
            for (T c : route.getPath()) draw.add(c);
            mapPane.setRoute(draw);

            alert(route.isValid() ? "Solved (VALID)" : "Solved (INVALID)", route.toString());
        } catch (IllegalArgumentException e) {
            alert("Invalid input", e.getMessage());
        } catch (IllegalStateException e) {
            alert("Matrix Error", e.getMessage());
        }
    }

    private void refreshStartBox() {
        City current = startBox.getValue();
        startBox.getItems().setAll(state.getCities());

        if (state.getCities().isEmpty()) {
            startBox.setValue(null);
            mapPane.setStartCity(null);
            return;
        }

        if (current != null && state.getCities().contains(current)) {
            startBox.setValue(current);
            return;
        }

        startBox.getSelectionModel().select(0);
    }

    private void clearSolutionOnly() {
        state.clearRoute();
        showReportBtn.setDisable(true);
        mapPane.setRoute(null);
    }

    private void clearAll() {
        state.getCities().clear();
        state.clearRoute();
        showReportBtn.setDisable(true);

        startBox.getItems().clear();
        startBox.setValue(null);

        mapPane.setCities(new ArrayList<>());
        mapPane.setRoute(null);
        mapPane.setStartCity(null);
        mapPane.clearPendingMarker();

        coordLabel.setText("Left-click = select. Shift+Drag = pan. Scroll = zoom.");
        pendingLon = Double.NaN;
        pendingLat = Double.NaN;
    }

    private Double parseDeadline(String s) {
        try {
            if (s == null || s.isBlank()) return null;
            double d = Double.parseDouble(s.trim());
            return d > 0 ? d : null;
        } catch (Exception e) {
            return null;
        }
    }

    private City createCity(Class<? extends City> type,
                            String name,
                            double lon,
                            double lat,
                            Double deadline) {
        try {
            Constructor<? extends City> ctor =
                    type.getConstructor(String.class, double.class, double.class, double.class);

            String id = (name == null || name.isBlank()) ? null : name.trim();
            double d = (deadline == null) ? City.NO_DEADLINE : deadline;

            return ctor.newInstance(id, lon, lat, d);
        } catch (Exception e) {
            return null;
        }
    }

    private void alert(String title, String msg) {
        Alert a = new Alert(Alert.AlertType.INFORMATION);
        a.setTitle(title);
        a.setHeaderText(null);
        a.setContentText(msg);
        a.showAndWait();
    }
}
