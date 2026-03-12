package domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AirCityTest {

    @Test
    void distanceIsZeroForSameCoordinates() {
        AirCity a = new AirCity("A", 10, 20, City.NO_DEADLINE);
        AirCity b = new AirCity("B", 10, 20, City.NO_DEADLINE);

        assertEquals(0.0, a.distance(b), 1e-9);
        assertEquals(0.0, a.time(b), 1e-9);
    }

    @Test
    void distanceAndTimeArePositiveForDifferentCoordinates() {
        AirCity a = new AirCity("A", 0, 0, City.NO_DEADLINE);
        AirCity b = new AirCity("B", 1, 1, City.NO_DEADLINE);

        assertTrue(a.distance(b) > 0);
        assertTrue(a.time(b) > 0);
    }
}
