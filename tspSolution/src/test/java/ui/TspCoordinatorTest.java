package ui;

import data.Matrix;
import data.MatrixDataProvider;
import domain.City;
import domain.GroundCity;
import model.Route;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TspCoordinatorTest {

    @AfterEach
    void reset() {
        Matrix.reset();
        Matrix.setDataProvider(null);
    }

    @Test
    void solveGroundModeUsesInjectedProvider() {
        Matrix.setDataProvider(new StubProvider());

        TspCoordinator coordinator = new TspCoordinator();
        GroundCity a = new GroundCity("A", 10, 10, City.NO_DEADLINE);
        GroundCity b = new GroundCity("B", 11, 11, City.NO_DEADLINE);

        Route<GroundCity> route = coordinator.solve(
                List.of(a, b),
                GroundCity.class,
                a,
                "Fast (no 2-Opt)",
                null
        );

        assertNotNull(route);
        assertEquals(3, route.size());
        assertTrue(route.getTotalDistance() >= 0);
    }

    private static final class StubProvider implements MatrixDataProvider {
        @Override
        public boolean fillMatrix(Matrix<?> matrix) {
            double[][] dist = matrix.getDistanceMatrix();
            double[][] time = matrix.getTimeMatrix();
            for (int i = 0; i < dist.length; i++) {
                for (int j = 0; j < dist.length; j++) {
                    dist[i][j] = (i == j) ? 0 : 1000;
                    time[i][j] = (i == j) ? 0 : 100;
                }
            }
            return true;
        }
    }
}
