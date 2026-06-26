# EmployeeRideSolver

Employee Transportation VRP Solver — optimizes driver routes for transporting employees between a central hub (City-Fahrschule, Worms) and customer locations using Timefold Solver and GraphHopper.

---

## Table of Contents

- [Project Overview](#project-overview)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
- [Architecture](#architecture)
  - [Event Generation Pipeline](#event-generation-pipeline)
  - [Domain Model](#domain-model)
  - [Timefold Chained Pattern](#timefold-chained-pattern)
  - [Constraints](#constraints)
  - [Key Services](#key-services)
- [REST API](#rest-api)
- [Web Pages](#web-pages)
- [Configuration](#configuration)
- [Testing](#testing)
- [Reference Data](#reference-data)
- [Project Structure](#project-structure)

---

## Project Overview

EmployeeRideSolver solves a Vehicle Routing Problem (VRP) for employee transportation in the Worms, Germany area. Drivers operating from a central hub pick up site employees and deliver them to customer locations (Chep, Sanner, Orion, Barbe, Beneo) according to shift schedules, then return them at shift end. The optimizer uses real-world road distances from OpenStreetMap via GraphHopper and enforces German labor law (ArbZG) working-hour constraints.

The system batches employees traveling to the same customer/shift into shared rides (FR-1) and further merges pickup events at the same location within a configurable time window into multi-stop routes that visit multiple customer sites in geographic order (FR-3).

---

## Tech Stack

| Component         | Technology                                          |
|-------------------|-----------------------------------------------------|
| Runtime           | Quarkus 3.17.3, Java 17                             |
| Optimization      | Timefold Solver 1.17.0                              |
| Routing           | GraphHopper 8.0 with OSM data (car profile)         |
| Database          | H2 file-based (`./db/vrp`)                          |
| ORM               | Hibernate / Panache                                  |
| Frontend          | Qute templates + HTMX + custom dark theme CSS       |
| Build             | Maven 3.x                                           |

---

## Getting Started

### Prerequisites

- Java 17+
- Maven 3.x
- OSM data file: `rheinland-pfalz-latest.osm.pbf` in the project root (for GraphHopper routing)

### Run in Dev Mode

```bash
mvn quarkus:dev
```

The application starts on `http://localhost:5000` with live reload enabled.

### Google Routes API (optional)

To enable traffic-aware ETA validation at `/routes/validate`, set your Google Maps API key before starting the app:

```bash
# Option A: export directly
export GOOGLE_MAPS_API_KEY=your_key_here
mvn quarkus:dev

# Option B: source from .env.local (git-ignored)
# .env.local contains: export GOOGLE_MAPS_API_KEY=your_key_here
source .env.local
mvn quarkus:dev
```

The validation page works without a key — it shows the Haversine fallback estimate and explains how to configure the key.

**Test URLs (after starting the app):**
- UI page: `http://localhost:5000/routes/validate`
- JSON API: `http://localhost:5000/api/routes/validate` (demo route, default traffic model)
- JSON API with params: `http://localhost:5000/api/routes/validate?originLat=49.6441&originLng=8.3253&destLat=50.1073&destLng=8.6637&trafficModel=BEST_GUESS`

The API requires a Google Maps API key with **Routes API** enabled. Create one at [Google Cloud Console](https://console.cloud.google.com/).

### Build

```bash
mvn clean package
```

### Run Tests

```bash
mvn test                        # All tests
mvn test -Dtest=ClassName        # Specific test class
```

---

## Architecture

### Event Generation Pipeline (2-pass)

The `EventGenerationService` generates transport events in two passes:

**Pass 1 (FR-1): Same-customer batching.** Employees are grouped by `(shift, pickup location)`. Each group produces a single batched pickup event (and a paired dropoff if the shift requires a return trip). This dramatically reduces the event count — e.g., 5 Chep employees on the same shift become one event with 5 passengers instead of 5 individual events.

**Pass 2 (FR-3): Cross-customer merging.** Pickup events at the same location whose start times fall within a 30-minute window are merged into multi-stop events. Customer sites are visited in geographic order (nearest-first from pickup). Example: a 04:30 Chep pickup (4 passengers at Hub) and a 04:30 Sanner pickup (1 passenger at Hub) become one event: `Hub(5 board) → Chep(4 alight) → Sanner(1 alight)`.

### Domain Model

**VrpSolution** (`@PlanningSolution`) — Top-level solution container holding drivers, events, locations, customers, employees, the GraphHopper instance, and the planning score (`HardMediumSoftLongScore`).

**Event** (`@PlanningEntity`) — Represents a pickup or dropoff run. Contains:
- `previousStandstill` — chained planning variable linking into the driver's route chain
- `stops` — ordered list of `Stop` objects defining the multi-stop route (FR-3)
- `pairedEvent` — links pickup/dropoff pairs for same-driver enforcement
- `shiftDate` — calendar date for ArbZG daily working-hour tracking
- Shadow variables: `driver` (anchor), `arrivalTime`, `cumulativePassengerCount`

**Stop** — A single stop within an Event's route. Each stop tracks location, boarding/alighting passengers, travel time from previous stop, boarding duration, distance, and per-stop deadline (`maxEndTime`).

**Driver** (problem fact, NOT a planning entity) — Anchor for route chains. Holds capacity (default 6), working-hour limits, break rules, and home location.

**Location** (record) — Geographic coordinates with `name`, `latitude`, `longitude`. Provides distance and travel-time calculation via GraphHopper with Haversine fallback.

**Standstill** (interface) — Implemented by both `Driver` and `Event` to participate in chained planning variable graphs.

### Timefold Chained Pattern

The solver uses Timefold's chained planning variable pattern for vehicle routing:

```
Driver (anchor) → Event₁ → Event₂ → ... → Eventₙ → null
                    ↑          ↑                ↑
             previousStandstill (chained planning variable)
```

- `Event.previousStandstill` — `@PlanningVariable(graphType = CHAINED)` referencing the `standstillRange` value range (all drivers + all events)
- `Event.driver` — `@AnchorShadowVariable` automatically resolves which `Driver` anchors the chain
- `Event.arrivalTime` — `@ShadowVariable` updated by `ArrivalTimeUpdatingVariableListener`, cascades through the chain
- `Event.cumulativePassengerCount` — `@ShadowVariable` updated by `PassengerCountUpdatingVariableListener`, tracks running passenger total for capacity checks

### Constraints

#### Hard Constraints (7)

| # | Constraint                   | Description                                                                                   |
|---|------------------------------|-----------------------------------------------------------------------------------------------|
| 1 | `driverAssignmentRequired`   | Unassigned event incurs 1000 hard penalty per event                                            |
| 2 | `vehicleCapacityConstraint` | Checks cumulative AND peak concurrent passenger count (FR-3 multi-stop aware)                 |
| 3 | `timeWindowConstraint`      | Validates event-level AND per-stop deadlines; penalizes seconds past deadline                 |
| 4 | `pairingConstraint`          | Pickup/dropoff must use same driver; pickup must complete before dropoff arrival             |
| 5 | `maxDailyWorkingHours`      | 10h/day per driver, grouped by `shiftDate` (ArbZG night-shift compliance)                    |
| 6 | `maxWeeklyWorkingHours`     | 40h/week per driver                                                                            |
| 7 | `maxConsecutiveDrivingHours`| 4h max continuous driving without 30-min break; resets across different work days             |

#### Soft Constraints (4)

| # | Constraint             | Description                                          |
|---|------------------------|------------------------------------------------------|
| 1 | `minimizeTotalDistance`| Primary optimization goal — minimize km driven         |
| 2 | `minimizeWaitingTime`  | Reduce driver idle time before event start             |
| 3 | `returnToHomeDistance` | Penalize distance from last event back to driver home  |
| 4 | `excessiveIdleTime`    | Gaps > 4h between consecutive events are penalized     |

### Key Services

| Service                   | Responsibility                                                                 |
|---------------------------|-------------------------------------------------------------------------------|
| `EventGenerationService`  | 2-pass event generation: FR-1 same-customer batching + FR-3 cross-customer merging |
| `SolverService`           | Builds `VrpSolution`, runs Timefold solver asynchronously, manages solver jobs |
| `GraphHopperService`      | Loads OSM data, provides real routing distances/times (Haversine fallback)     |
| `SolutionDiagnosticService` | Analyzes unassigned events, diagnoses reasons (capacity, time, pairing), suggests fixes |
| `DataBootstrap`           | Seeds KW 51 demo data on startup (5 customers, 3 drivers, 12 employees)       |

---

## REST API

### Solver

| Method | Endpoint                    | Description                    | Response   |
|--------|-----------------------------|--------------------------------|------------|
| POST   | `/api/solver/solve`         | Start optimization             | HTML (HTMX)|
| GET    | `/api/solver/status`       | Current solver status          | HTML (HTMX)|
| POST   | `/api/solver/stop`         | Stop active solver             | HTML (HTMX)|
| GET    | `/api/solver/results`      | Results page                   | HTML (HTMX)|
| GET    | `/api/solver/{jobId}/status`  | JSON solver status          | JSON       |
| GET    | `/api/solver/{jobId}/solution`| Full VrpSolution JSON       | JSON       |
| DELETE | `/api/solver/{jobId}`      | Terminate solver job           | JSON       |

### Employees

| Method | Endpoint                         | Description              |
|--------|----------------------------------|--------------------------|
| GET    | `/api/employees`                | List all employees        |
| POST   | `/api/employees`                | Create employee (JSON/form)|
| GET    | `/api/employees/{id}`           | Get employee by ID        |
| PUT    | `/api/employees/{id}`           | Update employee (JSON/form)|
| DELETE | `/api/employees/{id}`           | Delete employee           |
| PATCH  | `/api/employees/{id}/toggle`    | Toggle active status      |

### Customers

| Method | Endpoint                         | Description              |
|--------|----------------------------------|--------------------------|
| GET    | `/api/customers`                | List all customers        |
| POST   | `/api/customers`                | Create customer (JSON/form)|
| GET    | `/api/customers/{id}`           | Get customer by ID        |
| PUT    | `/api/customers/{id}`           | Update customer (JSON/form)|
| DELETE | `/api/customers/{id}`           | Delete customer           |

### Shifts

| Method | Endpoint                                    | Description                     |
|--------|---------------------------------------------|---------------------------------|
| GET    | `/api/shifts`                               | List all shifts                  |
| POST   | `/api/shifts`                               | Create shift (JSON/form)         |
| GET    | `/api/shifts/{id}`                          | Get shift by ID                  |
| PUT    | `/api/shifts/{id}`                          | Update shift (JSON/form)         |
| DELETE | `/api/shifts/{id}`                          | Delete shift                     |
| PATCH  | `/api/shifts/{id}/toggle`                   | Toggle shift active              |
| POST   | `/api/shifts/{id}/assign-employee`          | Assign employee to shift (HTMX)  |
| DELETE | `/api/shifts/{id}/unassign/{employeeId}`     | Unassign employee (HTMX)        |

All CRUD endpoints accept both `application/json` and `application/x-www-form-urlencoded`.

---

## Web Pages

| Path                      | Description                                      |
|---------------------------|--------------------------------------------------|
| `/`                       | Dashboard                                         |
| `/employees`              | Employee list                                     |
| `/employees/new`          | Create employee form                              |
| `/employees/{id}/edit`    | Edit employee form                                |
| `/customers`              | Customer list                                     |
| `/customers/new`          | Create customer form                              |
| `/customers/{id}/edit`    | Edit customer form                                |
| `/shifts`                 | Shift list                                        |
| `/shifts/new`             | Create shift form                                 |
| `/shifts/{id}/edit`       | Edit shift form                                   |
| `/shifts/{id}`            | Shift detail with employee assignment             |
| `/optimize`               | Start optimization (date/runtime parameters)      |
| `/routes`                 | Optimization results with driver timelines        |
| `/routes/validate`        | Compare Google Routes ETA vs Haversine fallback   |

---

## Configuration

`src/main/resources/application.properties`:

| Property                                       | Value                              | Description                        |
|------------------------------------------------|------------------------------------|------------------------------------|
| `quarkus.http.port`                             | `5000`                             | HTTP port                          |
| `quarkus.http.host`                             | `0.0.0.0`                          | Listen on all interfaces           |
| `quarkus.datasource.jdbc.url`                   | `jdbc:h2:file:./db/vrp;AUTO_SERVER=TRUE` | H2 file database            |
| `quarkus.hibernate-orm.database.generation`     | `update`                           | Auto-update schema                 |
| `graphhopper.osm.file`                          | `rheinland-pfalz-latest.osm.pbf`   | OSM data file                      |
| `graphhopper.cache.dir`                         | `./graphhopper-cache`              | Routing graph cache                |
| `graphhopper.profiles`                          | `car`                              | Routing profile                    |
| `quarkus.timefold.solver.termination.spent-limit`| `120s`                            | Solver timeout                     |
| `quarkus.timefold.solver.environment-mode`      | `REPRODUCIBLE`                     | Deterministic solving              |
| `quarkus.log.category."com.vrp".level`          | `DEBUG`                            | Debug logging for app code         |
| `google.maps.api.key`                           | `${GOOGLE_MAPS_API_KEY:}` (empty default) | Google Routes API key — set via env var |

---

## Testing

### RouteOptimizationTest

Constraint verifier tests and solver integration tests validating all 7 hard constraints and 4 soft constraints.

### GoogleRoutesServiceTest

11 tests covering (no real API calls, no API key required):
- `parseDurationSeconds` — Google duration string parsing (`"3600s"`, decimals, no-suffix)
- `toRfc3339Utc` — Berlin→UTC conversion for both CET (winter) and CEST (summer) offsets
- `parseResponse` — JSON response parsing with mock fixture
- `isApiKeyConfigured` — returns false when CDI key injection is absent
- `nextTuesdayAt05` — always returns a future Tuesday at 05:00

Run targeted:
```bash
mvn test -Dtest=GoogleRoutesServiceTest
```

### EventGenerationServiceTest

13 tests covering:
- FR-1 same-customer batching (employees at same pickup location grouped into single events)
- FR-3 cross-customer merging (pickup events at same location within 30-min window merged into multi-stop events)
- Edge cases (single employee, mixed pickup locations, capacity checks)

---

## Reference Data

### Hub Location

| Name              | Address                          | Latitude  | Longitude |
|-------------------|----------------------------------|-----------|-----------|
| City-Fahrschule   | Siegfriedstraße 25, 67547 Worms  | 49.6295   | 8.3640    |

### Alternative Pickup

| Name                  | Address                          | Latitude  | Longitude |
|-----------------------|----------------------------------|-----------|-----------|
| Tankstelle Pfeddersheim | Odenwaldstraße 7, 67551 Worms  | 49.6180   | 8.2970    |

### Customer Locations

| Customer                    | Address                                       | Latitude  | Longitude |
|-----------------------------|-----------------------------------------------|-----------|-----------|
| Chep Deutschland GmbH       | Am Winkelgraben 13, 64584 Biebesheim          | 49.7784   | 8.4625    |
| Sanner GmbH                 | Bertha-Benz-Straße 5, 64625 Bensheim          | 49.6800   | 8.6250    |
| Orion Bausysteme GmbH       | Waldstr. 2, 64584 Biebesheim                  | 49.7750   | 8.4580    |
| Hans W. Barbe               | Justus-von-Liebig-Str. 17, 67549 Worms        | 49.6350   | 8.3480    |
| Beneo-Palatinit GmbH        | Wormser Straße 11, 67283 Obrigheim            | 49.4700   | 8.2100    |

### Demo Data (KW 51)

The `DataBootstrap` service seeds 5 customers, 3 drivers, and 12 employees on startup for development and testing.

---

## Project Structure

```
src/main/java/com/vrp/
├── constraint/
│   └── VrpConstraintProvider.java          # 7 hard + 4 soft constraints
├── domain/
│   ├── VrpSolution.java                    # @PlanningSolution
│   ├── Event.java                          # @PlanningEntity (chained)
│   ├── Stop.java                           # Multi-stop route segment
│   ├── Driver.java                         # Problem fact / chain anchor
│   ├── Location.java                       # Record: name, lat, lon
│   ├── Route.java                          # Route display model
│   └── Standstill.java                     # Interface for chain participants
├── entity/
│   ├── Employee.java                       # JPA entity (DRIVER / SITE_EMPLOYEE)
│   ├── EmployeeType.java                   # Enum
│   ├── Customer.java                       # JPA entity
│   └── ShiftDemand.java                    # JPA entity (shift + assignments)
├── listener/
│   ├── ArrivalTimeUpdatingVariableListener.java
│   └── PassengerCountUpdatingVariableListener.java
├── resource/
│   ├── SolverResource.java                # Solver REST API
│   ├── EmployeeResource.java              # Employee CRUD
│   ├── CustomerResource.java              # Customer CRUD
│   ├── ShiftResource.java                 # Shift CRUD + assignment
│   └── WebResource.java                   # HTML page rendering
└── service/
    ├── SolverService.java                  # Solver job management
    ├── EventGenerationService.java         # 2-pass event generation
    ├── GraphHopperService.java             # OSM routing
    ├── SolutionDiagnosticService.java      # Unassigned event analysis
    └── DataBootstrap.java                  # Seed data

src/main/resources/
├── application.properties
└── templates/
    ├── base.html               # Layout template
    ├── dashboard.html          # Dashboard page
    ├── employees.html          # Employee list
    ├── employee-form.html      # Create/edit employee
    ├── customers.html          # Customer list
    ├── customer-form.html      # Create/edit customer
    ├── shifts.html             # Shift list
    ├── shift-form.html         # Create/edit shift
    ├── optimize.html           # Start optimization
    ├── routes.html             # Results with driver timelines
    ├── solverStatus.html       # Solver status (HTMX partial)
    └── solverResults.html      # Solver results (HTMX partial)

src/test/java/com/vrp/solver/
├── RouteOptimizationTest.java            # Constraint + integration tests
└── EventGenerationServiceTest.java        # FR-1/FR-3 generation tests (13)
```
