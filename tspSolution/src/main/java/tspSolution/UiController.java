package tspSolution;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.lang.reflect.Constructor;
import java.util.ArrayList;

public final class UiController {
    // --- UI State ---
    private final BorderPane root = new BorderPane();
    private final MapViewPane mapPane = new MapViewPane();

    private final ObservableList<City> cities = FXCollections.observableArrayList();
    private final ObservableList<City> routePreview = FXCollections.observableArrayList();

    // Controls
    private final ComboBox<Class<? extends City>> modeBox = new ComboBox<>();
    private final ComboBox<City> startBox = new ComboBox<>();
    private final TextField nameField = new TextField();
    private final TextField deadlineField = new TextField();
    private final Label coordLabel = new Label("Click on the map to choose coordinates");

    private double pendingLon = Double.NaN;
    private double pendingLat = Double.NaN;

    // --- Constructor ---
    public UiController() {
        buildLayout();
        hookEvents();
        loadModes();
    }

    // --- Public ---
    public Parent getRoot() {
        return root;
    }

    // --- UI Build ---
    private void buildLayout() {
        root.setPadding(new Insets(10));
        root.setCenter(mapPane);

        VBox left = new VBox(10);
        left.setPadding(new Insets(10));
        left.setPrefWidth(360);

        Label title = new Label("TSP Controls");
        title.setStyle("-fx-font-size: 18; -fx-font-weight: bold;");

        modeBox.setPromptText("Choose City Type (Mode)");
        startBox.setPromptText("Choose Start City");

        nameField.setPromptText("City name (optional)");
        deadlineField.setPromptText("Deadline seconds (optional)");

        Button addBtn = new Button("Add City (from last click)");
        Button removeBtn = new Button("Remove Selected City");
        Button clearBtn = new Button("Clear All");
        Button solveBtn = new Button("Solve");

        ListView<City> cityListView = new ListView<>(cities);
        cityListView.setPrefHeight(250);

        removeBtn.setOnAction(e -> {
            City sel = cityListView.getSelectionModel().getSelectedItem();
            if (sel != null) {
                cities.remove(sel);
                refreshStartBox();
                mapPane.setCities(new ArrayList<>(cities));
            }
        });

        clearBtn.setOnAction(e -> {
            cities.clear();
            routePreview.clear();
            refreshStartBox();
            mapPane.setCities(new ArrayList<>(cities));
            mapPane.setRoute(null);
        });

        addBtn.setOnAction(e -> addCityFromPendingClick());
        solveBtn.setOnAction(e -> solveNow());

        left.getChildren().addAll(
                title,
                new Separator(),
                new Label("Mode (City Type):"),
                modeBox,
                new Label("Start City:"),
                startBox,
                new Separator(),
                coordLabel,
                nameField,
                deadlineField,
                addBtn,
                new Separator(),
                new Label("Cities:"),
                cityListView,
                removeBtn,
                clearBtn,
                new Separator(),
                solveBtn
        );

        root.setLeft(left);
    }

    private void hookEvents() {
        mapPane.setOnMapClick((lon, lat) -> {
            pendingLon = lon;
            pendingLat = lat;
            coordLabel.setText(String.format("Selected: lon=%.6f  lat=%.6f", lon, lat));
        });

        modeBox.setOnAction(e -> {
            Matrix.reset();
            routePreview.clear();
            mapPane.setRoute(null);

            cities.clear();
            refreshStartBox();
            mapPane.setCities(new ArrayList<>(cities));
        });
    }

    private void loadModes() {
        modeBox.getItems().setAll(CityRegistry.getRegisteredTypes());
        if (!modeBox.getItems().isEmpty()) modeBox.getSelectionModel().select(0);
    }

    // --- Actions ---
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

        String name = nameField.getText();
        Double deadline = parseDeadline(deadlineField.getText());

        City c = createCity(type, name, pendingLon, pendingLat, deadline);
        if (c == null) {
            alert("Create failed", "Could not create city. Make sure your city class has a constructor:\n(String id, double x, double y, double deadline)");
            return;
        }

        if (!cities.contains(c)) {
            cities.add(c);
            refreshStartBox();
            mapPane.setCities(new ArrayList<>(cities));
        } else {
            alert("Duplicate", "A city with the same coordinates already exists.");
        }
    }

    // ✅ UPDATED: solveNow() (this is the important change)
    private void solveNow() {
        Class<? extends City> type = modeBox.getValue();
        if (type == null) {
            alert("No mode", "Choose a city type first.");
            return;
        }
        if (cities.size() < 2) {
            alert("Not enough cities", "Add at least 2 cities.");
            return;
        }

        City start = startBox.getValue();
        if (start == null) {
            alert("No start city", "Choose a start city.");
            return;
        }

        // Build matrix for this type (avoid raw types)
        @SuppressWarnings("unchecked")
        Class<City> castType = (Class<City>) type;

        Matrix<City> matrix = Matrix.getInstance(castType);
        matrix.invalidate();

        for (City c : cities) {
            if (!type.equals(c.getClass())) {
                alert("Type mismatch", "All cities must be of the selected mode type.");
                return;
            }
            matrix.addCity(c);
        }

        // If API type: ask for key if missing
        if (matrix.requiresApi() && !Matrix.hasGoogleService()) {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Google API Key Needed");
            dialog.setHeaderText("This city type requires Google Distance Matrix API");
            dialog.setContentText("Enter API key:");

            dialog.showAndWait().ifPresentOrElse(key -> {
                if (key.isBlank()) {
                    alert("Missing key", "You entered an empty key.");
                } else {
                    Matrix.setGoogleMapsService(new GoogleMapsService(key.trim()));
                }
            }, () -> {
                alert("Cancelled", "No key provided, cannot populate API matrix.");
            });
        }

        boolean ok = matrix.populateMatrix();
        if (!ok || !matrix.checkIntegrity()) {
            alert("Matrix failed", "Matrix could not be populated (API key missing / API error / invalid setup).");
            return;
        }

        Solver<City> solver = new SlackInsertion2OptSolver<>(matrix);
        Route<City> route = solver.solve(start);

        if (route == null) {
            alert("Solve failed", "Solver returned null (should not happen).");
            return;
        }

        // draw
        mapPane.setRoute(route.getPath());

        alert("Solved", route.toString());
    }

    // --- Helpers ---
    private void refreshStartBox() {
        startBox.getItems().setAll(cities);
        if (!cities.isEmpty() && startBox.getValue() == null) {
            startBox.getSelectionModel().select(0);
        } else if (startBox.getValue() != null && !cities.contains(startBox.getValue())) {
            startBox.getSelectionModel().clearSelection();
        }
    }

    private Double parseDeadline(String s) {
        if (s == null) return null;
        s = s.trim();
        if (s.isEmpty()) return null;
        try {
            double d = Double.parseDouble(s);
            if (d <= 0) return null;
            return d;
        } catch (Exception e) {
            return null;
        }
    }

    private City createCity(Class<? extends City> type, String name, double lon, double lat, Double deadline) {
        try {
            Constructor<? extends City> ctor = type.getConstructor(String.class, double.class, double.class, double.class);
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