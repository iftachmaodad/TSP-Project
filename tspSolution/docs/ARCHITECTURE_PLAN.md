# Architecture & Responsibilities Plan

## Current gaps to fix

1. `UiController` currently mixes UI behavior, application state, matrix setup, API wiring, and solver orchestration.
2. `Matrix` is coupled to a concrete `GoogleMapsService` implementation.
3. The map view is image-based (`world.jpg`) rather than a real tile-backed map.
4. Unit test coverage is missing for core behavior.

## Target layer split

- **Frontend/UI layer**
  - `UiController`: view events only.
  - `MapViewPane`: rendering and map interactions only.
- **Application layer**
  - `TspCoordinator`: orchestrates solve flow and dependencies.
  - `ApplicationState`: holds UI-facing state (`cities`, `lastRoute`).
- **Domain layer**
  - `City`, `AirCity`, `GroundCity`, `CityRegistry`.
- **Data/integration layer**
  - `Matrix`, `MatrixDataProvider`, `GoogleMapsService`.
- **Solver layer**
  - `Solver`, `SlackInsertionSolver`, `SlackInsertion2OptSolver`, helpers.

## API alternatives to Google Maps (free)

### 1) OpenRouteService (ORS)
- Free tier and route matrix APIs.
- Good replacement for driving distance/time.

### 2) OSRM public/demo or self-hosted
- Fully open-source, very fast routing and matrix support.
- Best if you can self-host for reliability.

### 3) GraphHopper (open + hosted options)
- Matrix and route APIs with free plans depending on usage.

## Real-time map view options

### Recommended: JavaFX WebView + Leaflet + OpenStreetMap tiles
- Embed a real interactive map in a `WebView`.
- Let users click city coordinates directly on real cities/roads.
- Keep solver/backend logic unchanged.

### Alternative: JavaFXMap library wrappers
- Faster to start but less flexible than Leaflet bridge.

## Suggested next algorithm additions (after refactor/tests)

1. Nearest Neighbor baseline.
2. Simulated Annealing improvement pass.
3. Genetic Algorithm variant for larger city sets.
4. Held-Karp exact solver (small N only) for benchmark truth.

## Testing plan

- Domain tests: `AirCity` distance/time behavior.
- Solver tests: `RouteEvaluator` valid path evaluation.
- Application tests: `TspCoordinator` with a stub `MatrixDataProvider`.

