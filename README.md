# TSP Solver

An interactive desktop application for solving the **Travelling Salesman Problem with hard deadline constraints**, built with Java 21 and JavaFX 21.

---

## What it does

The user places cities on a live satellite map, optionally assigns arrival deadlines to each city, then runs one of four solver algorithms to find the shortest valid closed route (depot → all cities → depot) that visits every city before its deadline.

Two transport modes are supported:

| Mode | Distance | Speed |
|------|----------|-------|
| **AirCity** | Haversine great-circle | 16.67 m/s (~60 km/h drone) |
| **GroundCity** | OSRM real road distances | actual driving duration from OSRM |

---

## Algorithms

| Solver | Complexity | Guarantee |
|--------|-----------|-----------|
| `BruteForceSolver` | O(n!) | **Exact optimal** — refuses n > 10 non-start cities |
| `SlackInsertion2OptSolver` | O(n² × passes) | Best heuristic — multi-start construction + full local search |
| `SlackInsertionSolver` | O(n²) | Fast single-pass construction baseline, no local search |
| `NearestNeighborSolver` | O(n²) | Greedy baseline |

**Slack-based insertion:** deadline cities are sorted by `slack = deadline − directTravelTime(start → city)` and inserted tightest-first before flexible cities, preserving feasibility throughout construction.

**Local search operators** (applied by `SlackInsertion2OptSolver`):
- **Relocate** — moves one city to its cheapest feasible position. O(n²) per pass.
- **2-opt** — reverses a sub-sequence when doing so reduces distance. A distance-delta filter skips pairs that cannot improve before doing the full O(n) evaluation.
- **Or-opt** — relocates chains of 2 or 3 consecutive cities. Finds improvements that relocate and 2-opt both miss. O(n²) per pass.

All three operators use first-improvement strategy and run interleaved until no pass yields further improvement or the pass limit is reached.

---

## Project structure

```
src/
├── main/
│   ├── java/
│   │   ├── benchmark/     TestInstance, TestInstanceLibrary, SolverBenchmark
│   │   ├── data/          Matrix, AirDistanceProvider, GroundDistanceProvider,
│   │   │                  OsrmClient, OsrmParser, DistanceProvider
│   │   ├── domain/        City (abstract), AirCity, GroundCity,
│   │   │                  CityFactory, CityRegistry
│   │   ├── model/         Route
│   │   ├── solver/        Solver, BruteForceSolver, SlackInsertion2OptSolver,
│   │   │                  SlackInsertionSolver, NearestNeighborSolver,
│   │   │                  RouteEvaluator, RouteImprover, SolverUtils, Insertion
│   │   ├── tools/         FetchPlacesData  (one-time Overpass data fetcher)
│   │   └── ui/            TspApp, UiController, CityPanel, SolverPanel,
│   │                      BenchmarkPane, MapViewPane, OsmTileLayer,
│   │                      PlaceSearchService, MapMarker, LocationMatchers
│   └── resources/
│       ├── places.json              Bundled ~200 world cities + airports
│       ├── cache/
│       │   ├── places_cache.json    Overpass-fetched pins (grows over time)
│       │   └── osrm_cache.json      OSRM road distance cache (append-only JSONL)
│       ├── images/
│       │   ├── world.jpg            Offline fallback map (equirectangular)
│       │   ├── tsp-icon-600.png     Application icon (600×600)
│       │   └── tsp-icon-300.png     Application icon (300×300)
│       └── styles/app.css           Application stylesheet
└── test/
    └── java/
        ├── data/          DistanceProviderTest, MatrixTest, OsrmParserTest
        ├── domain/        CityTest
        ├── misc/          MiscTest
        ├── model/         RouteTest
        └── solver/        SolverTest, SolverQualityTest, SolverUtilsTest
```

---

## Requirements

- **Java 21** or later
- **Maven 3.8+**
- Internet connection for live map tiles (ESRI), place pins (Overpass), and road distances (OSRM)
- Offline mode is available: disabling "Live map tiles" uses a bundled world map image

---

## Running the application

A `tspApp.bat` launcher is included in the project root for Windows. Double-click it for an interactive menu:

```
[1]  Run Application
[2]  Run Tests
[3]  Run Benchmark (console output)
[4]  Run Application + Tests
[5]  Clean and Rebuild
[0]  Exit
```

Alternatively, use Maven directly:

```bash
mvn javafx:run                                              # launch the app
mvn test                                                    # run all tests
mvn exec:java -Dexec.mainClass="benchmark.SolverBenchmark" # CLI benchmark
mvn exec:java -Dexec.mainClass="tools.FetchPlacesData"     # refresh places.json
```

---

## Map interaction

| Action | Result |
|--------|--------|
| Left-drag | Pan the map |
| Scroll | Zoom in / out |
| Click a red pin | Select a named place — pre-fills the city panel |
| Double-click empty space | Place a raw coordinate marker |
| Single-click empty space | Clear selection |

**Added city markers:**

| Symbol | Meaning |
|--------|---------|
| ⬥ Gold ring | Start city (depot) |
| ⏰ Cyan ring | City with a deadline |
| ● White/grey | Regular city |

The solved route is drawn as a yellow arrow line after solving.

---

## Pin loading

Overlay pins use a two-tier approach:

**Bundled pins** (`places.json`) — ~200 world cities and airports loaded at startup. These refresh automatically on every viewport change (pan / zoom) with no network call.

**Load More Pins button** — manually fetches additional pins from the Overpass API for the current viewport. Subject to a 5-second cooldown and a 10-call per-session limit. Results are saved to `cache/places_cache.json` so the same area is never fetched twice across sessions.

---

## Caching

### Pin cache (`cache/places_cache.json`)
Overpass-fetched place pins are written to this file after each "Load More Pins" request. On startup the file is merged with the bundled `places.json`, deduplicated by proximity (~200 m). Grows over time as new regions are explored.

### OSRM distance cache (`cache/osrm_cache.json`)
Road distance and travel time results from the OSRM API are stored as one JSON object per line:
```json
{"key":"2.35220,48.85660→-0.12780,51.50740","dist":453621.4,"dur":18340.2}
```
Both A→B and B→A keys are stored after a single fetch. Loaded into a `ConcurrentHashMap` at startup. Survives app restarts so the same city pair is never fetched twice across sessions.

---

## External services

| Service | Used for | Rate limit |
|---------|----------|-----------|
| [ESRI World Imagery](https://www.arcgis.com) | Satellite map tiles | None (free) |
| [Overpass API](https://overpass-api.de) | Place pins for the map overlay | ~10 req/session enforced in-app |
| [OSRM](http://router.project-osrm.org) | Road distances for GroundCity | Fair use — cached aggressively |

---

## Key design decisions

**City is immutable and final.** `AirCity` and `GroundCity` are `final` value types. Removing a deadline works by replacing the city in the shared list with a no-deadline copy created via `CityFactory`. This keeps `City` a pure value type and makes `Route`'s deadline tracking correct by construction.

**Matrix pre-computes all pairwise distances.** Before each solve, `Matrix.populateMatrix()` fetches all n(n−1)/2 distances from the registered `DistanceProvider`. Solvers then read from the 2D array in O(1) — no repeated API calls during path evaluation.

**Two solvers, clearly separated roles.** `SlackInsertionSolver` is a pure construction heuristic with no local search — it exists as a fast baseline that shows what insertion alone achieves. `SlackInsertion2OptSolver` adds multi-start search (3 deterministic orderings + 25 random shuffles) and full local search (relocate + 2-opt + or-opt), making the quality difference between the two meaningful and measurable in the benchmark.

**Singleton `PlaceSearchService`.** The bundled and cached place lists are static. The `INSTANCE` field is declared after the `static {}` initializer block so `places.json` is fully loaded before the singleton is exposed.

**Two-phase pin loading.** Bundled pins refresh automatically on every viewport change (instant, no network). The "Load More Pins" button is the only path to Overpass — manual, with a 5-second cooldown and a 10-call session limit shown as a usage label.

---

## Benchmark tab

The Benchmark tab runs all four solvers against any of the 8 named test instances (trivial through twenty-city) or against city sets sent from the Solver tab via the "Send to Benchmark" button.

Session instances appear in a dedicated card list in the left panel (not the instance dropdown) showing name, city count, and a preview of city IDs. Clicking a card selects it; the Remove button deletes only that instance.

Results include an **optimality gap** column: the percentage above the brute-force optimal distance. This is only available when BruteForce also ran on the same instance (n ≤ 10).

---

## License

Student project — not for production use. OSRM and Overpass usage must comply with their respective usage policies.
