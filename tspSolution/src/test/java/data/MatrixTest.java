package data;

import domain.AirCity;
import domain.City;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Matrix}.
 *
 * <p>Each test gets a fresh {@link Matrix} via {@link #setUp()} to avoid
 * shared mutable state between tests.
 */
class MatrixTest {

    private AirCity a, b, c;
    private Matrix<AirCity> matrix;

    @BeforeEach
    void setUp() {
        a = new AirCity("A", 35.0, 32.0);
        b = new AirCity("B", 35.1, 32.0);
        c = new AirCity("C", 35.0, 32.1);

        matrix = new Matrix<>(AirCity.class);
        matrix.addCity(a);
        matrix.addCity(b);
        matrix.addCity(c);
        matrix.populateMatrix();
    }

    // ── Construction ──────────────────────────────────────────────────────────

    @Test
    void unregistered_type_throws_on_construction() {
        assertThrows(IllegalArgumentException.class,
                () -> new Matrix<>(City.class));
    }

    @Test
    void matrix_size_reflects_added_cities() {
        assertEquals(3, matrix.size());
    }

    // ── Integrity ─────────────────────────────────────────────────────────────

    @Test
    void integrity_passes_after_populate() {
        assertTrue(matrix.checkIntegrity());
    }

    @Test
    void integrity_fails_before_populate() {
        Matrix<AirCity> fresh = new Matrix<>(AirCity.class);
        fresh.addCity(a);
        assertFalse(fresh.checkIntegrity());
    }

    @Test
    void integrity_fails_after_adding_city_post_populate() {
        matrix.addCity(new AirCity("D", 35.2, 32.2));
        assertFalse(matrix.checkIntegrity(),
                "Adding a city after populate must invalidate the matrix");
    }

    @Test
    void repopulate_restores_integrity() {
        matrix.addCity(new AirCity("D", 35.2, 32.2));
        matrix.populateMatrix();
        assertTrue(matrix.checkIntegrity());
    }

    // ── populateMatrix return value ───────────────────────────────────────────

    @Test
    void populate_empty_matrix_returns_false() {
        assertFalse(new Matrix<>(AirCity.class).populateMatrix());
    }

    @Test
    void populate_returns_true_for_valid_cities() {
        Matrix<AirCity> m = new Matrix<>(AirCity.class);
        m.addCity(new AirCity("X", 10.0, 10.0));
        m.addCity(new AirCity("Y", 11.0, 11.0));
        assertTrue(m.populateMatrix());
    }

    // ── Diagonal ──────────────────────────────────────────────────────────────

    @Test
    void diagonal_distances_are_zero() {
        for (int i = 0; i < 3; i++) {
            assertEquals(0.0, matrix.getDistance(i, i), 1e-9,
                    "d(i,i) must be 0");
        }
    }

    @Test
    void diagonal_times_are_zero() {
        for (int i = 0; i < 3; i++) {
            assertEquals(0.0, matrix.getTime(i, i), 1e-9,
                    "t(i,i) must be 0");
        }
    }

    // ── Symmetry ──────────────────────────────────────────────────────────────

    @Test
    void distance_matrix_is_symmetric() {
        int n = matrix.size();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                assertEquals(matrix.getDistance(i, j), matrix.getDistance(j, i),
                        1e-6, "d(" + i + "," + j + ") must equal d(" + j + "," + i + ")");
            }
        }
    }

    @Test
    void time_matrix_is_symmetric() {
        int n = matrix.size();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                assertEquals(matrix.getTime(i, j), matrix.getTime(j, i),
                        1e-6, "t(" + i + "," + j + ") must equal t(" + j + "," + i + ")");
            }
        }
    }

    // ── Positive off-diagonal ─────────────────────────────────────────────────

    @Test
    void off_diagonal_distances_are_positive() {
        int n = matrix.size();
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (i != j) {
                    assertTrue(matrix.getDistance(i, j) > 0,
                            "d(" + i + "," + j + ") must be positive");
                }
            }
        }
    }

    // ── Index lookup ──────────────────────────────────────────────────────────

    @Test
    void getIndexOf_returns_distinct_indices_in_range() {
        int ia = matrix.getIndexOf(a);
        int ib = matrix.getIndexOf(b);
        int ic = matrix.getIndexOf(c);

        assertTrue(ia >= 0 && ia < 3);
        assertTrue(ib >= 0 && ib < 3);
        assertTrue(ic >= 0 && ic < 3);
        assertNotEquals(ia, ib);
        assertNotEquals(ib, ic);
        assertNotEquals(ia, ic);
    }

    @Test
    void getIndexOf_returns_minus1_for_unknown_city() {
        assertEquals(-1, matrix.getIndexOf(new AirCity("Unknown", 99.0, 0.0)));
    }

    @Test
    void getDistance_returns_nan_for_negative_index() {
        assertTrue(Double.isNaN(matrix.getDistance(-1, 0)));
    }

    @Test
    void getDistance_returns_nan_for_out_of_bounds_index() {
        assertTrue(Double.isNaN(matrix.getDistance(0, 100)));
    }

    // ── getCities consistency ─────────────────────────────────────────────────

    @Test
    void getCities_returns_all_cities_after_populate() {
        assertEquals(3, matrix.getCities().size());
        assertTrue(matrix.getCities().contains(a));
        assertTrue(matrix.getCities().contains(b));
        assertTrue(matrix.getCities().contains(c));
    }

    @Test
    void getCities_is_stable_before_and_after_populate() {
        // Before populate
        Matrix<AirCity> m = new Matrix<>(AirCity.class);
        m.addCity(a);
        m.addCity(b);
        assertEquals(2, m.getCities().size());

        m.populateMatrix();
        assertEquals(2, m.getCities().size(),
                "getCities must return the same size before and after populate");
    }

    @Test
    void getCities_is_unmodifiable_after_populate() {
        assertThrows(UnsupportedOperationException.class,
                () -> matrix.getCities().add(new AirCity("Z", 1.0, 1.0)));
    }

    // ── Invalidation ─────────────────────────────────────────────────────────

    @Test
    void invalidate_clears_the_matrix() {
        matrix.invalidate();
        assertFalse(matrix.checkIntegrity());
        assertTrue(Double.isNaN(matrix.getDistance(0, 1)));
    }

    @Test
    void remove_city_invalidates_matrix() {
        matrix.removeCity(b);
        assertFalse(matrix.checkIntegrity());
    }

    @Test
    void clear_cities_invalidates_and_resets_size() {
        matrix.clearCities();
        assertFalse(matrix.checkIntegrity());
        assertEquals(0, matrix.size());
    }

    // ── Duplicate city ────────────────────────────────────────────────────────

    @Test
    void adding_duplicate_city_does_not_change_size() {
        int before = matrix.size();
        matrix.addCity(a); // already present
        assertEquals(before, matrix.size(),
                "Adding a duplicate must not change the city count");
    }

    // ── Null city ─────────────────────────────────────────────────────────────

    @Test
    void adding_null_is_silently_ignored() {
        int before = matrix.size();
        assertDoesNotThrow(() -> matrix.addCity(null));
        assertEquals(before, matrix.size());
    }
}
