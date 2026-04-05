package benchmark;

import domain.AirCity;

import java.util.List;

/**
 * A catalogue of named benchmark instances using real European city coordinates.
 *
 * All instances use AirCity (Haversine distance, drone speed 16.67 m/s = ~60 km/h).
 * Cities are real European capitals/major cities so benchmark routes are
 * geographically meaningful and visually clear on the map.
 *
 * City coordinates (lon, lat):
 *   Paris     (2.3522,  48.8566)   Brussels  (4.3517,  50.8503)
 *   London   (-0.1278,  51.5074)   Vienna   (16.3738,  48.2082)
 *   Berlin   (13.4050,  52.5200)   Zurich    (8.5417,  47.3769)
 *   Madrid   (-3.7038,  40.4168)   Prague   (14.4378,  50.0755)
 *   Rome     (12.4964,  41.9028)   Warsaw   (21.0122,  52.2297)
 *   Amsterdam (4.9041,  52.3676)   Budapest (19.0402,  47.4979)
 *   Barcelona (2.1734,  41.3851)   Munich   (11.5820,  48.1351)
 *   Stockholm(18.0686,  59.3293)   Oslo     (10.7522,  59.9139)
 *   Helsinki (24.9384,  60.1699)   Lisbon   (-9.1399,  38.7169)
 *   Athens   (23.7275,  37.9838)   Copenhagen(12.5683, 55.6761)
 *
 * Travel times at drone speed (16.67 m/s ≈ 60 km/h):
 *   Paris–Brussels: ~4.4h   Paris–London: ~5.7h   Paris–Berlin: ~14.6h
 *
 * Instance list:
 *   1. trivial      — 2 cities (Paris + London)
 *   2. triangle     — 3 cities (Paris, London, Brussels)
 *   3. five_city    — 5 cities (Paris + 4 neighbours)
 *   4. deadlines    — 5 cities, 2 with tight deadlines
 *   5. eight_city   — 8 Western European cities
 *   6. ten_city     — 10 cities (brute force still feasible: 9! = 362 880)
 *   7. twenty_city  — 20 cities (brute force refuses)
 *   8. infeasible   — 3 cities, one with an impossible deadline
 */
public final class TestInstanceLibrary {

    private TestInstanceLibrary() {
        throw new UnsupportedOperationException("TestInstanceLibrary is a utility class.");
    }

    // ── Shared city definitions ──────────────────────────────────────────────

    private static AirCity paris()      { return new AirCity("Paris",      2.3522,  48.8566); }
    private static AirCity london()     { return new AirCity("London",    -0.1278,  51.5074); }
    private static AirCity brussels()   { return new AirCity("Brussels",   4.3517,  50.8503); }
    private static AirCity berlin()     { return new AirCity("Berlin",    13.4050,  52.5200); }
    private static AirCity madrid()     { return new AirCity("Madrid",    -3.7038,  40.4168); }
    private static AirCity rome()       { return new AirCity("Rome",      12.4964,  41.9028); }
    private static AirCity amsterdam()  { return new AirCity("Amsterdam",  4.9041,  52.3676); }
    private static AirCity vienna()     { return new AirCity("Vienna",    16.3738,  48.2082); }
    private static AirCity zurich()     { return new AirCity("Zurich",     8.5417,  47.3769); }
    private static AirCity prague()     { return new AirCity("Prague",    14.4378,  50.0755); }
    private static AirCity warsaw()     { return new AirCity("Warsaw",    21.0122,  52.2297); }
    private static AirCity budapest()   { return new AirCity("Budapest",  19.0402,  47.4979); }
    private static AirCity barcelona()  { return new AirCity("Barcelona",  2.1734,  41.3851); }
    private static AirCity munich()     { return new AirCity("Munich",    11.5820,  48.1351); }
    private static AirCity stockholm()  { return new AirCity("Stockholm", 18.0686,  59.3293); }
    private static AirCity oslo()       { return new AirCity("Oslo",      10.7522,  59.9139); }
    private static AirCity helsinki()   { return new AirCity("Helsinki",  24.9384,  60.1699); }
    private static AirCity lisbon()     { return new AirCity("Lisbon",    -9.1399,  38.7169); }
    private static AirCity athens()     { return new AirCity("Athens",    23.7275,  37.9838); }
    private static AirCity copenhagen() { return new AirCity("Copenhagen",12.5683,  55.6761); }

    // ══════════════════════════════════════════════════════════
    // Public API
    // ══════════════════════════════════════════════════════════

    public static List<TestInstance<AirCity>> all() {
        return List.of(
            trivial(), triangle(), fiveCity(), deadlines(),
            eightCity(), tenCity(), twentyCity(), infeasible()
        );
    }

    // ══════════════════════════════════════════════════════════
    // Instance definitions
    // ══════════════════════════════════════════════════════════

    /**
     * trivial — 2 cities.
     * Only one route: Paris → London → Paris.
     */
    public static TestInstance<AirCity> trivial() {
        AirCity start = paris();
        return instance("trivial", List.of(start, london()), start);
    }

    /**
     * triangle — 3 cities, no deadlines.
     * Paris–London–Brussels triangle. 2 permutations for brute force.
     */
    public static TestInstance<AirCity> triangle() {
        AirCity start = paris();
        return instance("triangle", List.of(start, london(), brussels()), start);
    }

    /**
     * five_city — 5 cities, no deadlines.
     * Paris + 4 nearby Western European cities. 4! = 24 permutations.
     */
    public static TestInstance<AirCity> fiveCity() {
        AirCity start = paris();
        return instance("five_city",
            List.of(start, london(), brussels(), amsterdam(), zurich()), start);
    }

    /**
     * deadlines — 5 cities, 2 with tight deadlines.
     *
     * From Paris:
     *   Brussels: ~15 835s direct. Deadline = 21 000s (feasible if visited early).
     *   London:   ~20 609s direct. Deadline = 75 000s (feasible after Brussels).
     *   Berlin, Zurich: no deadline.
     */
    public static TestInstance<AirCity> deadlines() {
        AirCity start    = paris();
        AirCity bxl      = new AirCity("Brussels",  4.3517,  50.8503, 21_000.0);
        AirCity lon      = new AirCity("London",   -0.1278,  51.5074, 75_000.0);
        AirCity berlin_  = berlin();
        AirCity zurich_  = zurich();
        return instance("deadlines", List.of(start, bxl, lon, berlin_, zurich_), start);
    }

    /**
     * eight_city — 8 Western European cities, no deadlines.
     * 7! = 5 040 permutations. Good for measuring heuristic gap.
     */
    public static TestInstance<AirCity> eightCity() {
        AirCity start = paris();
        return instance("eight_city",
            List.of(start, london(), brussels(), amsterdam(),
                    berlin(), zurich(), rome(), madrid()), start);
    }

    /**
     * ten_city — 10 cities, no deadlines.
     * 9! = 362 880 permutations — brute force still runs.
     * Largest instance where we can compute the exact optimality gap.
     */
    public static TestInstance<AirCity> tenCity() {
        AirCity start = paris();
        return instance("ten_city",
            List.of(start, london(), brussels(), amsterdam(), berlin(),
                    zurich(), rome(), madrid(), vienna(), prague()), start);
    }

    /**
     * twenty_city — 20 European cities, no deadlines.
     * 19! ≈ 1.2 × 10¹⁷ — brute force refuses. Only heuristics run.
     */
    public static TestInstance<AirCity> twentyCity() {
        AirCity start = paris();
        return instance("twenty_city",
            List.of(start, london(), brussels(), amsterdam(), berlin(),
                    zurich(), rome(), madrid(), vienna(), prague(),
                    warsaw(), budapest(), barcelona(), munich(), stockholm(),
                    oslo(), helsinki(), lisbon(), athens(), copenhagen()), start);
    }

    /**
     * infeasible — 3 cities, one with an impossible deadline.
     *
     * Rome is ~66 304s from Paris by drone.
     * Deadline = 100s — impossible even by direct flight (misses by 660×).
     * All solvers must return INVALID.
     */
    public static TestInstance<AirCity> infeasible() {
        AirCity start    = paris();
        AirCity brussels_ = brussels();
        AirCity rome_    = new AirCity("Rome", 12.4964, 41.9028, 100.0);
        return instance("infeasible", List.of(start, brussels_, rome_), start);
    }

    // ══════════════════════════════════════════════════════════
    // Helper
    // ══════════════════════════════════════════════════════════

    private static TestInstance<AirCity> instance(
            String name, List<AirCity> cities, AirCity start) {
        return new TestInstance<>(name, List.copyOf(cities), start);
    }
}
