# TSP Solution – Traveling Salesman Problem with Time Constraints

## Project Title

**Traveling Salesman Problem with Time Constraints**

---

## Project Overview

This project implements a solution framework for the **Traveling Salesman Problem (TSP)** with an optional **deadline (time constraint)** for visiting cities.

Unlike the classic TSP, where the goal is only to minimize total distance, this project also supports **real-world constraints**, such as:

* Cities that must be visited before a certain time
* Different distance calculation strategies (mathematical vs real road data)
* Human-like decision logic (prioritizing urgent cities)

The project is written in **Java**, structured as a **Maven project**, and optionally integrates the **Google Distance Matrix API** for realistic travel times.

---

## Main Features

* Generic city model with deadlines
* Support for multiple city types:

  * **AirCity** – distance calculated mathematically (Haversine formula)
  * **GroundCity** – distance and time retrieved via Google Maps API
* Central **Matrix** class that stores distances and travel times
* Automatic strategy selection (math vs API) using a registry
* Route representation with arrival times and deadline validation
* Extensible solver interface for different TSP algorithms

---

## Project Structure

```
tspSolution/
├── City.java
├── AirCity.java
├── GroundCity.java
├── CityRegistry.java
├── Matrix.java
├── GoogleMapsService.java
├── Route.java
├── Solver.java
├── Main.java
├── pom.xml
└── README.md
```

---

## Core Design Decisions

### City & Deadlines

Each city may optionally have a **deadline**.
A route becomes **invalid** if a city is visited after its deadline.

### Matrix

The `Matrix` class:

* Stores all cities
* Builds distance and time matrices
* Automatically decides how to populate itself:

  * Mathematical calculation for `AirCity`
  * Google Maps API for `GroundCity`

Calling:

```java
matrix.populateMatrix();
```

will **always populate real values**, regardless of the strategy used.

---

## Google Maps API Integration

For `GroundCity`, distances and travel times are retrieved using the **Google Distance Matrix API**.

### Requirements

* Google Cloud account
* Distance Matrix API enabled
* Billing enabled
* API key

The API key is **not hardcoded** and should be provided at runtime (recommended via environment variable).

Example:

```bash
set GOOGLE_MAPS_API_KEY=YOUR_KEY
```

---

## Solver Architecture

The project defines a generic solver interface:

```java
public interface Solver<T extends City> {
    Route<T> solve(T startCity);
}
```

This allows multiple solving strategies to be implemented later, such as:

* Greedy nearest-neighbor
* Deadline-first (urgent cities)
* Human-like heuristic routing
* Hybrid approaches

---

## Intended Solving Logic (High Level)

The planned solving approach mimics how a **human driver** thinks:

1. Identify cities with deadlines (urgent cities)
2. Prioritize the city with the **smallest slack**

   * Slack = deadline − estimated arrival time
3. Between urgent stops, insert non-urgent cities if possible
4. Continue until all cities are visited or no valid route exists

---

## Technologies Used

* Java 21
* Maven
* Google Maps Distance Matrix API
* Eclipse IDE
* Git / GitHub

---

## Build & Run

From the project root:

```bash
mvn package
mvn exec:java
```

Make sure your API key is set if using `GroundCity`.

---

## Notes

* IDE files (`.metadata`, `.settings`, `bin`, `target`) are excluded via `.gitignore`
* API keys are never committed
* The project is designed to be **extensible**, not a single hard-coded solution

---

## Author

Student project – Traveling Salesman Problem with deadlines
Built as part of a guided research / programming assignment

---