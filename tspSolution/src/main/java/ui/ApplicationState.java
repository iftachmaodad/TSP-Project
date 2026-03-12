package ui;

import domain.City;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

public final class ApplicationState {
    private final ObservableList<City> cities = FXCollections.observableArrayList();
    private RouteSnapshot lastRoute;

    public ObservableList<City> getCities() {
        return cities;
    }

    public RouteSnapshot getLastRoute() {
        return lastRoute;
    }

    public void setLastRoute(RouteSnapshot lastRoute) {
        this.lastRoute = lastRoute;
    }

    public void clearRoute() {
        this.lastRoute = null;
    }

    public record RouteSnapshot(boolean valid, String report) {}
}
