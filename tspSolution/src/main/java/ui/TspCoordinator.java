package ui;

import data.GoogleMapsService;
import data.Matrix;
import domain.City;
import model.Route;
import solver.SlackInsertion2OptSolver;
import solver.SlackInsertionSolver;
import solver.Solver;

import java.util.List;

public final class TspCoordinator {

    public <T extends City> Route<T> solve(List<City> sourceCities,
                                           Class<T> type,
                                           City rawStart,
                                           String solverMode,
                                           String apiKey) {
        if (!type.isInstance(rawStart)) {
            throw new IllegalArgumentException("Start city is not of selected type.");
        }

        Matrix<T> matrix = Matrix.getInstance(type);

        for (City city : sourceCities) {
            if (!type.isInstance(city)) {
                throw new IllegalArgumentException("All cities must match selected mode.");
            }
            matrix.addCity(type.cast(city));
        }

        if (matrix.requiresApi() && !Matrix.hasDataProvider()) {
            if (apiKey == null || apiKey.isBlank()) {
                throw new IllegalArgumentException("API key is required for this city type.");
            }
            Matrix.setDataProvider(new GoogleMapsService(apiKey.trim()));
        }

        if (!matrix.populateMatrix() || !matrix.checkIntegrity()) {
            throw new IllegalStateException("Failed to populate matrix.");
        }

        Solver<T> solver =
                (solverMode != null && solverMode.startsWith("Fast"))
                        ? new SlackInsertionSolver<>(matrix)
                        : new SlackInsertion2OptSolver<>(matrix);

        return solver.solve(type.cast(rawStart));
    }
}
