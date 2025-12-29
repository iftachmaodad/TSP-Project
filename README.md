# TSP Solution — Traveling Salesman Problem with Time Constraints

## Project Title

**Traveling Salesman Problem (TSP) with Time Constraints**

---

## Project Overview

This project implements a modular and extensible solution for the **Traveling Salesman Problem (TSP)** with **optional time (deadline) constraints**.

Unlike the classic TSP, where the goal is only to minimize total distance, this system supports **realistic routing constraints**, including:

* Cities that must be visited before a deadline
* Different distance calculation strategies
* Multiple solver heuristics (fast vs optimized)
* Optional integration with real-world road data (Google Maps)

The project is written in **Java 21**, uses **JavaFX** for the UI, follows a **top-down architecture**, and is built using **Maven**.

---

## Key Features

* Generic city model with optional deadlines
* Two city types:

  * **AirCity** — distance calculated locally using the Haversine formula
  * **GroundCity** — distance and travel time retrieved from Google Maps API
* Central distance & time matrix shared across solvers
* Automatic strategy selection (local math vs API)
* Route validation (arrival times + deadline checks)
* Pluggable solver architecture using generics
* JavaFX-based interactive UI

---

## Project Structure

```
src/main/java
├── data
│   ├── GoogleMapsService.java
│   └── Matrix.java
│
├── domain
│   ├── City.java
│   ├── AirCity.java
│   ├── GroundCity.java
│   └── CityRegistry.java
│
├── model
│   └── Route.java
│
├── solver
│   ├── Solver.java
│   ├── SlackInsertionSolver.java
│   ├── SlackInsertion2OptSolver.java
│   ├── RouteEvaluator.java
│   ├── RouteImprover.java
│   └── Insertion.java
│
├── ui
│   ├── TspApp.java
│   ├── UiController.java
│   └── MapViewPane.java
│
src/main/resources
├── images
└── app.css
```

---

## Architecture Overview

The system is designed using a **top-down approach**, divided into logical layers:

1. **UI Layer (JavaFX)**
   Handles user interaction, city creation, solver selection, and result visualization.

2. **Domain Layer**
   Defines the city hierarchy, deadlines, and strategy selection.

3. **Data Layer**
   Builds and stores distance/time matrices and communicates with external APIs when needed.

4. **Solver Layer**
   Contains interchangeable solving algorithms using a generic solver interface.

5. **Output Model**
   Represents the final route, arrival times, and validity status.

Architecture and execution flow are documented using **PlantUML diagrams**:

* `tsp_architecture.puml`
* `tsp_solver_flow.puml`

---

## City Model & Deadlines

Each city may optionally have a **deadline**.

* A route is considered **invalid** if any city is visited after its deadline.
* Deadline checks are performed during route evaluation.

### City Types

* **AirCity**

  * Uses local mathematical distance (Haversine)
  * Travel time = distance / constant speed

* **GroundCity**

  * Uses Google Distance Matrix API
  * Distance & time retrieved dynamically

---

## Distance & Time Matrix

The `Matrix` class:

* Stores all cities
* Maintains distance and time matrices
* Automatically populates values based on city type
* Uses Google Maps API **only when required**

Calling:

```java
Matrix.getInstance(type).populateMatrix();
```

Always results in valid distance and time values.

---

## Solver Architecture

The solver layer is **generic and extensible**.

### Solver Interface

```java
public interface Solver<T extends City> {
    Route<T> solve(T startCity);
}
```

### Implemented Solvers

* **SlackInsertionSolver**

  * Fast heuristic
  * Prioritizes urgent cities (small slack)

* **SlackInsertion2OptSolver**

  * Optimized version
  * Applies route improvement (2-opt, relocation)

---

## Intended Solving Logic (High Level)

The solver mimics human decision-making:

1. Identify urgent cities (with deadlines)
2. Compute slack:

   ```
   slack = deadline − estimated arrival time
   ```
3. Visit cities with the smallest slack first
4. Insert non-urgent cities when possible
5. Validate route constraints continuously

---

## Google Maps API Integration

Used **only for GroundCity** instances.

### Requirements

* Google Cloud account
* Distance Matrix API enabled
* Billing enabled
* API key (not hardcoded)

Recommended usage:

```bash
set GOOGLE_MAPS_API_KEY=YOUR_API_KEY
```

---

## Build & Run

From the project root:

```bash
mvn clean javafx:run
```

Make sure:

* Java 21 is installed
* API key is set if using GroundCity

---

## Git & Project Hygiene

* `target/`, IDE files, and API keys are excluded via `.gitignore`
* UML diagrams (`.puml`) are committed
* Project is structured for future extension

---

## Author

Student project
Traveling Salesman Problem with Time Constraints
Built as part of an academic programming & research assignment

---