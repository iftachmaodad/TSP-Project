package ui;

import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.util.Comparator;

import domain.AirCity;
import domain.City;
import domain.CityFactory;
import domain.CityRegistry;

/**
 * Left sidebar panel for city and route management.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 *   <li>Mode (city type) selection — AirCity or GroundCity.</li>
 *   <li>City name and optional deadline input (deadline stored in seconds
 *       internally; the UI presents Minutes / Hours / Days for convenience).</li>
 *   <li>Adding cities from a pending map double-click or a clicked overlay pin.</li>
 *   <li>Displaying the city list and managing removal.</li>
 *   <li>Start city selection — the start city is enforced to have no deadline.</li>
 *   <li>Notifying {@link UiController} of list, start-city, and mode changes
 *       via the callback fields.</li>
 * </ul>
 *
 * <h3>City creation</h3>
 * Delegated to {@link domain.CityFactory}; this class never constructs
 * {@link domain.City} subclasses directly.
 *
 * <h3>List ↔ map highlight</h3>
 * {@link #onCityListSelectionChanged} fires when the list selection changes so
 * {@link MapViewPane} can highlight the corresponding map marker.
 *
 * <h3>Thread safety</h3>
 * Must be used on the JavaFX application thread only.
 */
public final class CityPanel {

    // --- UI ---
    private final VBox root = new VBox(10);

    final ComboBox<Class<? extends City>> modeBox       = new ComboBox<>();
    final ComboBox<City>                  startBox      = new ComboBox<>();
    final TextField                       nameField     = new TextField();
    final Label                           coordLabel    =
        new Label("Left-click = select. Drag = pan. Scroll = zoom.");
    private final ListView<City>          cityListView  = new ListView<>();

    // Deadline: number + unit — converts to seconds internally
    private final TextField              deadlineValue = new TextField();
    private final ComboBox<DeadlineUnit> deadlineUnit  = new ComboBox<>();
    private final HBox                   deadlineRow   =
        new HBox(6, deadlineValue, deadlineUnit);

    // Shared city list owned by UiController
    private final ObservableList<City> cities;

    // --- Callbacks to parent ---
    Runnable onCityListChanged;
    Runnable onStartCityChanged;
    Runnable onModeChanged;
    /** Fired when the city {@link ListView} selection changes (including clear). FX thread. */
    java.util.function.Consumer<City> onCityListSelectionChanged;

    // --- Pending click state ---
    private double pendingLon = Double.NaN;
    private double pendingLat = Double.NaN;

    // ══════════════════════════════════════════════════════════
    // Deadline unit enum
    // ══════════════════════════════════════════════════════════

    /**
     * Human-readable time units for the deadline input.
     * Each unit knows how many seconds it equals so the conversion
     * is a single multiply — nothing else in the codebase changes.
     */
    private enum DeadlineUnit {
        MINUTES("Minutes",   60.0),
        HOURS  ("Hours",  3_600.0),
        DAYS   ("Days",  86_400.0);

        final String label;
        final double toSeconds;

        DeadlineUnit(String label, double toSeconds) {
            this.label     = label;
            this.toSeconds = toSeconds;
        }

        @Override public String toString() { return label; }
    }

    // ══════════════════════════════════════════════════════════
    // Constructor
    // ══════════════════════════════════════════════════════════

    public CityPanel(ObservableList<City> sharedCities) {
        this.cities = sharedCities;
        build();
        loadModes();
    }

    public VBox getRoot() { return root; }

    // ══════════════════════════════════════════════════════════
    // Public API
    // ══════════════════════════════════════════════════════════

    /** Called by MapViewPane when the user clicks the map (raw coordinates). */
    public void onMapClick(double lon, double lat) {
        pendingLon = lon;
        pendingLat = lat;
        nameField.clear();
        coordLabel.setText(String.format("Selected: lon=%.6f  lat=%.6f", lon, lat));
    }

    /**
     * Called when the user clicks an overlay marker on the map.
     * Pre-fills name and coordinates; the user still clicks "Add City" to confirm.
     */
    public void onMarkerSelected(MapMarker marker) {
        pendingLon = marker.lon();
        pendingLat = marker.lat();
        nameField.setText(marker.label());
        coordLabel.setText(String.format("Selected: %s  (%.5f, %.5f)",
                           marker.label(), marker.lon(), marker.lat()));
    }

    /** Clears the pending coordinates and resets the label. */
    public void clearPending() {
        pendingLon = Double.NaN;
        pendingLat = Double.NaN;
        coordLabel.setText("Left-click = select. Drag = pan. Scroll = zoom.");
    }

    /** Refreshes the start box after the city list changes. */
    public void refreshStartBox() {
        City current = startBox.getValue();
        startBox.getItems().setAll(cities);

        if (cities.isEmpty()) {
            startBox.setValue(null);
            return;
        }

        if (current != null && cities.contains(current)) {
            startBox.setValue(current);
        } else {
            startBox.getSelectionModel().select(0);
        }
    }

    /** Selects the given city in the list view (called when user clicks it on the map). */
    public void selectCityInList(City city) {
        cityListView.getSelectionModel().select(city);
        cityListView.scrollTo(city);
    }

    /** Clears list selection only (e.g. map empty-click deselect). */
    public void clearCityListSelection() {
        cityListView.getSelectionModel().clearSelection();
    }

    /** Clears all cities and resets all state. */
    public void clearAll() {
        cities.clear();
        startBox.getItems().clear();
        startBox.setValue(null);
        clearPending();
    }

    // ══════════════════════════════════════════════════════════
    // Building
    // ══════════════════════════════════════════════════════════

    private void build() {
        root.setPadding(new Insets(10));
        root.setPrefWidth(360);

        nameField.setPromptText("City name (optional)");
        modeBox.setPromptText("Choose City Type");
        startBox.setPromptText("Choose Start City");

        // Show only the city ID in the start-city combo box, not the full City.toString().
        startBox.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(City item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : item.getID());
            }
        });
        startBox.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(City item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : item.getID());
            }
        });

        // Deadline row: number field + unit dropdown
        deadlineValue.setPromptText("No deadline");
        deadlineValue.setPrefWidth(120);
        deadlineUnit.getItems().setAll(DeadlineUnit.values());
        deadlineUnit.getSelectionModel().select(DeadlineUnit.HOURS); // default: hours
        HBox.setHgrow(deadlineValue, Priority.ALWAYS);

        Button addBtn    = new Button("\uD83D\uDCCD Add City");
        Button removeBtn = new Button("\uD83D\uDDD1 Remove Selected");
        Button clearBtn  = new Button("\uD83E\uDDF9 Clear All");

        cityListView.setItems(cities);

        // Show deadline cities as "⏰ CityName [7200 s]" and plain "CityName" otherwise.
        cityListView.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(City item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); return; }
                if (item.hasDeadline()) {
                    setText("⏰ " + item.getID()
                            + "  [" + String.format("%.0f", item.getDeadline()) + " s]");
                } else {
                    setText(item.getID());
                }
            }
        });

        cityListView.getSelectionModel().selectedItemProperty().addListener((obs, prev, sel) -> {
            if (onCityListSelectionChanged != null) onCityListSelectionChanged.accept(sel);
        });

        addBtn.setOnAction(e -> addCityFromPending());

        removeBtn.setOnAction(e -> {
            City sel = cityListView.getSelectionModel().getSelectedItem();
            if (sel == null) return;
            cities.remove(sel);
            refreshStartBox();
            if (onCityListChanged != null) onCityListChanged.run();
        });

        clearBtn.setOnAction(e -> {
            clearAll();
            if (onCityListChanged != null) onCityListChanged.run();
        });

        startBox.setOnAction(e -> {
            if (onStartCityChanged != null) onStartCityChanged.run();
        });

        modeBox.setOnAction(e -> {
            clearAll();
            if (onModeChanged != null) onModeChanged.run();
        });

        root.getChildren().addAll(
            new Label("Mode:"),       modeBox,
            new Label("Start City:"), startBox,
            new Separator(),
            coordLabel,
            nameField,
            new Label("Deadline (optional):"), deadlineRow,    // human-readable deadline
            addBtn,
            new Separator(),
            new Label("Cities (\u23F1 = has deadline):"),
            cityListView, removeBtn, clearBtn
        );

        // Let the ListView grow to fill whatever vertical space is available
        VBox.setVgrow(cityListView, Priority.ALWAYS);
        cityListView.setMinHeight(100);
        cityListView.setPrefHeight(javafx.scene.layout.Region.USE_COMPUTED_SIZE);
    }

    private void loadModes() {
        // Sort alphabetically so order is deterministic across JVM runs
        // (CityRegistry returns a HashSet which has no guaranteed iteration order).
        var sorted = CityRegistry.getRegisteredTypes().stream()
                .sorted(Comparator.comparing(Class::getSimpleName))
                .toList();
        modeBox.getItems().setAll(sorted);

        modeBox.setCellFactory(lv -> new ListCell<>() {
            @Override protected void updateItem(Class<? extends City> item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : item.getSimpleName());
            }
        });
        modeBox.setButtonCell(new ListCell<>() {
            @Override protected void updateItem(Class<? extends City> item, boolean empty) {
                super.updateItem(item, empty);
                setText((empty || item == null) ? null : item.getSimpleName());
            }
        });

        if (!modeBox.getItems().isEmpty()) {
            modeBox.getSelectionModel().select(0);
        }
    }


    // ══════════════════════════════════════════════════════════
    // City creation — delegates to CityFactory
    // ══════════════════════════════════════════════════════════

    private void addCityFromPending() {
        if (Double.isNaN(pendingLon) || Double.isNaN(pendingLat)) {
            String hint = isAirMode()
                ? "Click an airport pin on the map, or double-click to place a raw marker."
                : "Double-click the map to place a marker at any location.";
            alert("No coordinates", hint);
            return;
        }

        Class<? extends City> type = modeBox.getValue();
        if (type == null) {
            alert("No mode selected", "Choose a city type first.");
            return;
        }

        Double deadlineSecs = parseDeadlineSeconds();

        // The first city becomes the start city. Start cities cannot have deadlines.
        if (deadlineSecs != null && cities.isEmpty()) {
            alert("Deadline on start city",
                  "The first city you add becomes the start city.\n" +
                  "Start cities cannot have deadlines — the deadline will be ignored.");
            deadlineSecs = null;
            deadlineValue.clear();
        }

        // Also block deadline if this city matches the current start city.
        City currentStart = startBox.getValue();
        if (deadlineSecs != null && currentStart != null) {
            double dx = pendingLon - currentStart.getX();
            double dy = pendingLat - currentStart.getY();
            if (dx * dx + dy * dy < 1e-8) {
                alert("Deadline on start city",
                      "The start city cannot have a deadline — it will be ignored.");
                deadlineSecs = null;
                deadlineValue.clear();
            }
        }

        if (LocationMatchers.nearExistingCity(pendingLon, pendingLat, cities)) {
            alert("Duplicate",
                  "A city at or very near these coordinates already exists in the list.");
            return;
        }

        City city = CityFactory.create(type, nameField.getText(),
                                       pendingLon, pendingLat, deadlineSecs);

        if (city == null) {
            alert("Create failed",
                  "Could not create a city of type " + type.getSimpleName() + ".\n" +
                  "Expected constructor: (String id, double x, double y, double deadline)");
            return;
        }

        if (cities.contains(city)) {
            alert("Duplicate", "A city at these coordinates already exists.");
            return;
        }

        cities.add(city);
        refreshStartBox();
        clearPending();
        nameField.clear();
        deadlineValue.clear();

        if (onCityListChanged != null) onCityListChanged.run();
    }

    /**
     * Reads the deadline value field and unit dropdown and returns
     * the deadline in seconds, or null if the field is blank or invalid.
     *
     * Examples:
     *   "2"  + Hours   → 7 200.0 s
     *   "30" + Minutes → 1 800.0 s
     *   "1"  + Days    → 86 400.0 s
     *   ""             → null (no deadline)
     */
    private Double parseDeadlineSeconds() {
        String text = deadlineValue.getText();
        if (text == null || text.isBlank()) return null;

        try {
            double amount = Double.parseDouble(text.trim());
            if (amount <= 0) return null;

            DeadlineUnit unit = deadlineUnit.getValue();
            if (unit == null) unit = DeadlineUnit.HOURS;

            return amount * unit.toSeconds;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean isAirMode() { return AirCity.class.equals(modeBox.getValue()); }

    private void alert(String title, String msg) {
        // Use a TextArea inside a Dialog so long messages are never cut off.
        // A plain Alert sizes itself to its content label, which JavaFX can
        // truncate when the text is multi-line and the stage is too narrow.
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle(title);
        dialog.setHeaderText(null);

        TextArea area = new TextArea(msg);
        area.setEditable(false);
        area.setWrapText(true);
        area.setPrefRowCount(Math.min(10, msg.split("\n").length + 1));
        area.setPrefWidth(420);
        area.setStyle("-fx-background-color: transparent; -fx-background-insets: 0;");

        dialog.getDialogPane().setContent(area);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dialog.getDialogPane().setPrefWidth(460);
        dialog.showAndWait();
    }
}
