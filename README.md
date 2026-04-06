# TSP Solver

Interactive JavaFX desktop app for solving the **Travelling Salesman Problem (TSP)** with optional **hard deadlines**.

The project lives in the `tspSolution/` Maven module.

---

## Project structure

```
tspSolution/src/
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
│   │                      PlaceSearchService, MapMarker
│   └── resources/
│       ├── places.json              Bundled ~200 world cities + airports
│       ├── cache/
│       │   ├── places_cache.json    Overpass-fetched pins (grows over time)
│       │   └── osrm_cache.json      OSRM road distance cache (append-only JSONL)
│       ├── images/world.jpg         Offline fallback map (equirectangular)
│       └── styles/app.css           Application stylesheet
└── test/
    └── java/
        ├── data/          DistanceProviderTest, MatrixTest, OsrmParserTest
        ├── domain/        CityTest
        ├── misc/          MiscTest
        ├── model/         RouteTest
        └── solver/        SolverTest, SolverUtilsTest
```

---

## Requirements

- **Java 21** or later
- **Maven 3.8+**
- Internet connection for live map tiles (ESRI), place pins (Overpass), road distances (OSRM), and place search (Nominatim)
- Offline mode is available: disabling "Live map tiles" uses a bundled world map image

---

From repository root:

```bash
cd tspSolution
mvn javafx:run
```

Run tests:

```bash
cd tspSolution
mvn test
```

Run CLI benchmark:

```bash
cd tspSolution
mvn exec:java -Dexec.mainClass="benchmark.SolverBenchmark"
```

Refresh bundled place data:

```bash
cd tspSolution
mvn exec:java -Dexec.mainClass="tools.FetchPlacesData"
```

---

## What the app does

### Solver tab
- Interactive world map (live satellite tiles, or offline fallback image).
- Add cities by:
  - clicking an overlay place pin, or
  - double-clicking raw coordinates.
- Optional per-city deadline input (minutes / hours / days).
- Select start city (start city is enforced without deadline).
- Solve and visualize the route on-map.
- Bottom route table shows step index, city, leg distance, cumulative travel time, deadline, and late/on-time note.
- "Send to Benchmark" exports current city set to the Benchmark tab.

### Benchmark tab
- Run selected solvers against:
  - built-in test instances, or
  - session instances sent from Solver tab.
- Compare distance, runtime, validity, and optimality gap (when brute-force baseline is available).
- Click a benchmark result row to display its route on the map.

---

## Transport modes

| Mode | City type | Distance model |
|------|-----------|----------------|
| Air | `AirCity` | Haversine great-circle distance |
| Ground | `GroundCity` | OSRM road distance + duration |

Distance providers are selected by city type (`AirDistanceProvider` / `GroundDistanceProvider`) through `CityRegistry`.

---

## Solvers

| Solver class | Type | Notes |
|--------------|------|-------|
| `BruteForceSolver` | exact | Refuses large instances (UI warns: practical limit around 10 non-start cities). |
| `SlackInsertion2OptSolver` | heuristic | Multi-start + insertion + local improvements (`RouteImprover`). |
| `SlackInsertionSolver` | heuristic | Faster slack-based insertion baseline. |
| `NearestNeighborSolver` | heuristic | Greedy nearest-neighbour baseline. |

Core solver interfaces/helpers are in `solver/` (`Solver`, `RouteEvaluator`, `SolverUtils`, `Insertion`).

---

## Project layout

```text
tspSolution/
├── pom.xml
├── src/
│   ├── main/
│   │   ├── java/
│   │   │   ├── benchmark/  SolverBenchmark, TestInstance, TestInstanceLibrary
│   │   │   ├── data/       Matrix, DistanceProvider, Air/GroundDistanceProvider,
│   │   │   │               OsrmClient, OsrmParser
│   │   │   ├── domain/     City, AirCity, GroundCity, CityFactory, CityRegistry
│   │   │   ├── model/      Route
│   │   │   ├── solver/     all solver implementations + utilities
│   │   │   ├── tools/      FetchPlacesData
│   │   │   └── ui/         TspApp, UiController, SolverPanel, CityPanel,
│   │   │                   BenchmarkPane, MapViewPane, PlaceSearchService,
│   │   │                   OsmTileLayer, MapMarker, LocationMatchers
│   │   └── resources/
│   │       ├── places.json
│   │       ├── cache/
│   │       │   ├── places_cache.json
│   │       │   └── osrm_cache.json
│   │       ├── images/world.jpg
│   │       └── styles/app.css
│   └── test/java/
│       ├── data/
│       ├── domain/
│       ├── misc/
│       ├── model/
│       └── solver/
```

---

## External services used at runtime

- **ESRI World Imagery**: live map tiles.
- **Overpass API**: additional place pins for visible area.
- **OSRM public router**: road distance/duration for `GroundCity`.
- **Nominatim**: reverse geocoding/search context.

The UI includes rate/call protections for Overpass usage and caches fetched data locally.

---

## Caching

### `src/main/resources/cache/places_cache.json`
- Stores fetched place pins from Overpass.
- Loaded on startup and merged with bundled places.

### `src/main/resources/cache/osrm_cache.json`
- Stores OSRM road distance/duration lookups.
- Reused across solves/sessions to reduce repeated network calls.

---

## Requirements

- Java 21+
- Maven 3.8+
- Internet connection for live tiles and external APIs (optional if running only offline map / pre-cached paths)

---

## Notes for contributors

- Keep UI interactions on the JavaFX application thread.
- Avoid blocking the FX thread with network or matrix-building work; existing code uses background tasks/services.
- If you update behavior, update this README and avoid stale comments/docs.

---

## License

Student project; not production-deployed service software.
