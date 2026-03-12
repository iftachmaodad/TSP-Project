package solver;

import data.Matrix;
import domain.AirCity;
import model.Route;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteEvaluatorTest {

    @AfterEach
    void reset() {
        Matrix.reset();
    }

    @Test
    void evaluateReturnsValidRouteForSimpleAirCities() {
        Matrix<AirCity> matrix = Matrix.getInstance(AirCity.class);
        AirCity a = new AirCity("A", 0, 0, AirCity.NO_DEADLINE);
        AirCity b = new AirCity("B", 0.5, 0.5, AirCity.NO_DEADLINE);

        matrix.addCity(a);
        matrix.addCity(b);
        assertTrue(matrix.populateMatrix());

        Route<AirCity> route = RouteEvaluator.evaluate(List.of(a, b), matrix);

        assertTrue(route.isValid());
        assertEquals(2, route.size());
        assertTrue(route.getTotalDistance() > 0);
    }
}
