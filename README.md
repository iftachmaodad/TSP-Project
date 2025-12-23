# TSP Solution – Traveling Salesman Problem with Time Constraints

## Project Title

**Traveling Salesman Problem (TSP) with Time Constraints**

---

## Project Overview

This project implements a **flexible and extensible framework** for solving the **Traveling Salesman Problem (TSP)** with optional **time (deadline) constraints**.

Unlike the classical TSP, which minimizes only total distance, this solution models **real-world routing considerations**, including:

* Cities that must be visited before a given deadline
* Different distance/time calculation strategies
* Realistic travel times using external map services
* Solver logic that mimics **human decision-making** under urgency

The project is written in **Java (Java 21)**, structured as a **Maven project**, and optionally integrates the **Google Distance Matrix API**.

---

## Main Features

* **Generic city model** with optional deadlines
* Support for multiple city types:

  * **AirCity** – distance computed locally using the Haversine formula
  * **GroundCity** – distance and travel time retrieved from Google Maps API
* Central **Matrix<T>** component that stores distances and travel times
* Automatic **strategy selection** (local math vs API) via a registry
* **Route<T>** representation with arrival times and validity checking
* **Generic solver interface** allowing multiple TSP algorithms

---

## Project Structure

```
tspSolution/
├── ui/
│   ├── TspApp.java
│   ├── UiController.java
│   └── MapViewPane.java
├── domain/
│   ├── City.java
│   ├── AirCity.java
│   ├── GroundCity.java
│   ├── CityRegistry.java
│   └── CalculationStrategy.java
├── data/
│   ├── Matrix.java
│   └── GoogleMapsService.java
├── solver/
│   ├── Solver.java
│   ├── SlackInsertionSolver.java
│   ├── SlackInsertion2OptSolver.java
│   ├── RouteEvaluator.java
│   └── RouteImprover.java
├── output/
│   └── Route.java
├── docs/
│   ├── uml/
│   │   ├── tsp_architecture.puml
│   │   └── tsp_solver_flow.puml
│   └── report/
│       └── פרק_7_ארכיטקטורה.docx
├── pom.xml
└── README.md
```

---

## Core Design Decisions

### City Model & Deadlines

Each city may optionally define a **deadline** (latest allowed arrival time).

* If a city has no deadline → it can be visited at any time
* If a city is visited after its deadline → the route becomes **invalid**

This allows the solver to model **urgent vs non-urgent cities**.

---

### Distance & Time Matrix (`Matrix<T>`)

The `Matrix<T>` class is responsible for:

* Storing all cities participating in the route
* Holding distance and travel-time matrices
* Managing data consistency and integrity

Calling:

```java
matrix.populateMatrix();
```

guarantees that **all distances and times are available**, regardless of how they were calculated.

#### Strategy Handling

The matrix does **not** decide how distances are calculated by itself.

Instead:

* `CityRegistry` maps each city type to a `CalculationStrategy`
* `LOCAL_MATH` → distances computed locally
* `API_REQUIRED` → distances fetched via Google Maps API

---

## Google Maps API Integration

For `GroundCity`, travel distance and duration are retrieved using the
**Google Distance Matrix API**.

### Requirements

* Google Cloud account
* Distance Matrix API enabled
* Billing enabled
* API key

The API key is **never hardcoded**.

Recommended usage via environment variable:

```bash
set GOOGLE_MAPS_API_KEY=YOUR_KEY
```

---

## Solver Architecture

The project defines a **generic solver interface**:

```java
public interface Solver<T extends City> {
    Route<T> solve(T startCity);
}
```

### Why Generics?

* Allows the solver to work with **any City subtype**
* Keeps the design flexible and type-safe
* Matches the generic design of `Route<T>` and `Matrix<T>`

---

## Implemented Solving Approaches

### Fast Solver (Slack Insertion)

* Greedy heuristic
* Prioritizes urgent cities
* Builds a valid route quickly
* Suitable for larger inputs

### Optimized Solver (Slack + 2-Opt)

* Starts with a feasible solution
* Applies local improvements (2-opt, relocation)
* Re-evaluates deadlines after each improvement
* Slower but produces higher-quality routes

---

## High-Level Solving Logic (Human-Oriented)

The solver follows a **human-like decision process**:

1. Identify cities with deadlines
2. Compute **slack time** for each city

   ```
   slack = deadline − estimated arrival time
   ```
3. Prioritize cities with the smallest slack
4. Insert non-urgent cities when safe
5. Validate route feasibility continuously

If no valid route exists → the solver reports failure.

---

## UML Documentation

The project includes **two UML diagrams** (written in PlantUML):

1. **Top-Down Architecture Diagram**

   * UI → Domain → Data → Solver → Output
2. **Execution & Algorithm Flow Diagram**

   * Fast vs Optimized solver execution paths

Source files are located under:

```
docs/uml/
```

They can be regenerated using PlantUML.

---

## Build & Run

From the project root:

```bash
mvn clean package
mvn exec:java
```

If using `GroundCity`, ensure the API key is configured.

---

## Version Control Notes

* UML (`.puml`) files and report (`.docx`) **are committed**
* API keys and IDE artifacts are excluded via `.gitignore`
* The repository contains both **source code and documentation**

---

## Technologies Used

* Java 21
* Maven
* JavaFX
* Google Maps Distance Matrix API
* PlantUML
* Eclipse IDE
* Git / GitHub

---

## Author

Student project – Traveling Salesman Problem with time constraints
Developed as part of a guided research and software engineering assignment