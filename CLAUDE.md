# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Employee Transportation VRP Solver - A Java-based optimization system for routing drivers to transport employees between a central hub (City-Fahrschule) and customer locations. Uses Timefold Solver for VRP optimization with real-world routing via GraphHopper and OSM data.

**Technology Stack:**
- Quarkus 3.33.1 (Java 21)
- Timefold Solver 2.2.0 (VRP optimization)
- GraphHopper 8.0 (real-world routing with OSM data)
- H2 database (file-based persistence at `./db/vrp`)
- Qute templates + HTMX + TailwindCSS (frontend)
- Maven 3.x

## Development Commands

**Run in dev mode (hot reload):**
```bash
mvn quarkus:dev
```
Application runs on http://localhost:5000

**Build the application:**
```bash
mvn clean package
```

**Run tests:**
```bash
mvn test
```

**Run specific test:**
```bash
mvn test -Dtest=YourTestClass
```

**Clean build artifacts:**
```bash
mvn clean
```

## Architecture Overview

### Employee Type System

The system uses `EmployeeType` enum to distinguish roles:

1. **DRIVER** - Employees who operate vehicles
   - Filtered from active employees with `employeeType == DRIVER`
   - Converted to `Driver` domain objects in `SolverService.createDriversFromEmployees()`
   - Each `Driver` is a planning entity that owns an ordered list of `Event`s (the vehicle's route)
   - Default capacity: 6 passengers
   - ArbZG working hour limits configured on Driver: maxDailyHours (10h), maxWeeklyHours (40h), maxConsecutiveHours (4h), minBreak (30min), maxBreak (4h)

2. **SITE_EMPLOYEE** - Employees who need transportation
   - Generate pickup/dropoff `Event` objects based on shift assignments
   - Batched together when going to same customer/shift/day
   - Assigned to driver routes by the solver

### Timefold List Variable Planning Pattern

The VRP uses Timefold's planning list variable pattern (the standard VRP model since Timefold 2.0; the older chained planning variable pattern was removed in 2.0 and is no longer used here):

**Key architectural rule:** `Driver` **is** a planning entity — it owns the `@PlanningListVariable` that represents its route. This reverses the pre-2.0 rule (kept only in git history) that Driver must never be `@PlanningEntity`.

**Route structure:**
```
Driver.events = [Event₁, Event₂, ..., Eventₙ]   (@PlanningListVariable<Event>)
```

**Domain objects:**
- `Driver` - `@PlanningEntity` with `@PlanningListVariable(valueRangeProviderRefs = "eventRange", allowsUnassignedValues = true) private List<Event> events`
- `Event` - `@PlanningEntity` (value class of the list variable) with shadow variables:
  - `Event.driver` - `@InverseRelationShadowVariable(sourceVariableName = "events")` — which Driver owns this event
  - `Event.previousEvent` - `@PreviousElementShadowVariable(sourceVariableName = "events")` — the prior event in the same driver's route (null if first, or if unassigned)
  - `Event.arrivalTime` - Custom shadow variable (`@ShadowVariable(supplierName = "arrivalTimeSupplier")`) — a pure function of `driver` and `previousEvent.arrivalTime`, with the `VrpSolution` injected for GraphHopper access
  - `Event.cumulativePassengerCount` - Custom shadow variable (`@ShadowVariable(supplierName = "cumulativePassengerCountSupplier")`) — a pure function of `driver` and `previousEvent.cumulativePassengerCount`

**Custom shadow variable suppliers (replace the old VariableListener classes):**
`Event.arrivalTimeSupplier(VrpSolution solution)`:
- Declared with `@ShadowSources({"driver", "previousEvent.arrivalTime"})` so Timefold automatically recalculates it whenever the event's position in a route changes
- Returns `null` if unassigned, `minStartTime` if first in the route, otherwise `previousEvent.getDepartureTime() + travelTime`
- Uses GraphHopper (injected via the `VrpSolution` parameter) for real routing travel times; falls back to Haversine distance / 15 m/s average speed when GraphHopper is unavailable
- `Event.getDepartureTime()` is a plain derived getter (arrivalTime + duration), not its own shadow variable

`Event.cumulativePassengerCountSupplier()`:
- Declared with `@ShadowSources({"driver", "previousEvent.cumulativePassengerCount"})`
- Computes `previousEvent.cumulativePassengerCount + thisEvent.passengerDelta` (0 if first in route)

Unlike the old `VariableListener`s, these suppliers are pure functions with no `scoreDirector.beforeVariableChanged`/`afterVariableChanged` bookkeeping and no manual "find the next event and recurse" traversal — Timefold's dependency graph handles cascading automatically.

### Event Generation (2-Pass Pipeline)

`EventGenerationService.generateEventsForWeek()` uses a two-pass pipeline:

**Pass 1 (FR-1): Same-shift batching by pickup location**
- Groups employees by pickup location coordinates within the same shift
- Each group becomes a single batched Event with `List<Employee> passengers`
- Creates paired pickup/dropoff events for shifts requiring return trips
- All events receive `shiftDate` (LocalDate) for ArbZG compliance — night shifts spanning midnight count toward the day the shift started
- Boarding time: 2 min per passenger; alighting time: 1 min per passenger

**Pass 2 (FR-3): Cross-customer merging within 30-min window**
- Merges pickup events at the same pickup location whose `minStartTime` differs by <= 30 minutes (`FR3_TIME_WINDOW`)
- Creates multi-stop Events with ordered `List<Stop>` (pickup → customer sites sorted by distance from pickup, nearest first)
- Re-links paired dropoff events to the merged pickup
- The merged event's `maxEndTime` is the tightest deadline across all customers

**Multi-stop Event model:**
- An Event contains `List<Stop>`, each Stop has a `Location`, boarding passengers, alighting count, and per-stop `maxEndTime`
- `fromLocation` / `toLocation` are derived from first/last stop for backward compatibility
- `getPeakPassengerCount()` returns the maximum concurrent load across all stops (used by capacity constraint)
- `getPassengerDelta()` returns net passenger change (0 for self-contained multi-stop events where all passengers board and alight within the event)

### Constraint Provider

`VrpConstraintProvider` defines hard and soft constraints:

**Hard constraints:**
- `driverAssignmentRequired` - Every event must have a driver
- `vehicleCapacityConstraint` - Checks both cumulative passenger count across the driver's route AND peak concurrent load within multi-stop events; penalizes whichever exceeds driver `maxCapacity` more
- `negativeCumulativePassengerCount` - Cumulative passenger count must never go negative (a dropoff scheduled before its paired pickup in the driver's route)
- `timeWindowConstraint` - Event-level time window (completion before `maxEndTime`) AND per-stop time windows for multi-stop events (each stop checked against its `maxEndTime`)
- `pairingConstraint` - Pickup/dropoff pairs must use same driver, pickup before dropoff
- `maxDailyWorkingHours` - Per driver per shiftDate, maximum 10 hours of working time per ArbZG; night shifts count toward the day the shift started
- `maxWeeklyWorkingHours` - Per driver across all shiftDates in the week, maximum 40 hours
- `maxConsecutiveDrivingHours` - Per driver per shiftDate, maximum 4 hours continuous driving without a break >= 30 min; gaps < 30 min count as continuous driving; gaps between different shiftDates always reset the counter

**Soft constraints (optimization goals):**
- `minimizeTotalDistance` - Primary optimization goal
- `minimizeWaitingTime` - Reduce driver idle time before minStartTime
- `returnToHomeDistance` - Penalize distance from last event's location back to driver's home
- `excessiveIdleTime` - Penalize idle time > 4 hours between consecutive events

### Solver Flow

1. User navigates to `/optimize` page and clicks "Start Optimization"
2. `POST /api/solve` → `SolverService.solve()`
3. Filters active DRIVER employees → creates `Driver` domain objects
4. `EventGenerationService` generates batched pickup/dropoff Events (2-pass pipeline)
5. Creates `VrpSolution` with drivers, events, locations, and GraphHopper instance
6. `SolverManager.solveBuilder()` runs async optimization (default 120s timeout)
7. Best solutions stored in `bestSolutionMap` via consumer callbacks
8. `GET /api/solve/{jobId}/solution` retrieves results
9. Routes displayed on `/routes` page with Leaflet.js map

### Shift Assignment Validation

`ShiftResource.assignEmployee()` validates concurrent shift conflicts:
- Checks if employee already assigned to overlapping shift on same day
- Returns HTTP 409 with error details if conflict detected
- HTMX handles reload with explicit status code check to preserve error messages

### Database Entities (JPA)

Located in `com.vrp.entity`:
- `Employee` - Both drivers and site employees, has `employeeType` field
- `Customer` - Customer locations with lat/long coordinates
- `ShiftDemand` - Shift definitions with time windows and employee assignments
- All use Hibernate Panache for simplified data access

### REST API Dual Input Support

Resources support both JSON and form-encoded data:
- JSON: `Content-Type: application/json`
- Forms: `Content-Type: application/x-www-form-urlencoded`
- Employee unassignment endpoints use `@Consumes(MediaType.WILDCARD)` to accept POST without body

### GraphHopper Integration

`GraphHopperService`:
- Loads OSM data from `rheinland-pfalz-latest.osm.pbf` (245MB file in repo root)
- Caches routing graph in `./graphhopper-cache` directory
- Provides real-world distance/time calculations between coordinates
- Used in event generation for distance/duration calculations and in `Event.arrivalTimeSupplier` for real routing during solving
- GraphHopper instance stored in `VrpSolution` (shallow-copied by Timefold's SolutionCloner, NOT a @ProblemFactProperty)

## File Structure

**Domain (Timefold planning entities):**
- `src/main/java/com/vrp/domain/` - Planning entities and problem facts
  - `VrpSolution.java` - `@PlanningSolution` with drivers, events, locations, customers, employees, GraphHopper instance
  - `Event.java` - `@PlanningEntity` (list variable value class), multi-stop `List<Stop>`, `shiftDate`, `previousEvent`/`driver`/`arrivalTime`/`cumulativePassengerCount` shadow variables
  - `Stop.java` - Single stop within an Event's route: location, boarding passengers, alighting count, boarding duration (2 min/passenger), travel time from previous stop, per-stop `maxEndTime`
  - `Driver.java` - `@PlanningEntity` owning `@PlanningListVariable<Event> events`; ArbZG fields: maxDailyHours, maxWeeklyHours, maxConsecutiveHours, minBreak, maxBreak
  - `Location.java` - Geographic coordinates with Haversine distance and GraphHopper routing
  - `Route.java` - Simple container for a list of Events with duration

**Persistence (JPA entities):**
- `src/main/java/com/vrp/entity/` - Database entities
  - `Employee.java` - With `EmployeeType` enum
  - `Customer.java` - Customer locations
  - `ShiftDemand.java` - Shift definitions and assignments

**Business Logic:**
- `src/main/java/com/vrp/service/SolverService.java` - Optimization job management
- `src/main/java/com/vrp/service/EventGenerationService.java` - 2-pass event generation pipeline (FR-1 same-shift batching + FR-3 cross-customer 30-min window merging into multi-stop events)
- `src/main/java/com/vrp/service/GraphHopperService.java` - Real-world routing
- `src/main/java/com/vrp/service/DataBootstrap.java` - Seed data for development
- `src/main/java/com/vrp/service/SolutionDiagnosticService.java` - Diagnoses why events remain unassigned

**Constraints:**
- `src/main/java/com/vrp/constraint/VrpConstraintProvider.java` - 8 hard + 4 soft constraints including ArbZG working hour limits

**REST Endpoints:**
- `src/main/java/com/vrp/resource/` - REST API and web page controllers
  - `WebResource.java` - HTML page rendering (Qute templates)
  - `SolverResource.java` - Optimization API endpoints
  - `EmployeeResource.java` - Employee CRUD
  - `CustomerResource.java` - Customer CRUD
  - `ShiftResource.java` - Shift CRUD and assignment

**Frontend:**
- `src/main/resources/templates/` - Qute HTML templates with HTMX
- Dark theme with custom CSS variables, DM Sans font
- Leaflet.js for interactive maps on routes page

## Configuration

`src/main/resources/application.properties`:
- Database: H2 file-based at `./db/vrp`
- HTTP: Port 5000, host 0.0.0.0
- Timefold: 120s termination limit, `no-assert` environment mode (reproducible, minimal assertion overhead)
- GraphHopper: OSM file path, cache directory, car profile
- Logging: INFO level, DEBUG for `com.vrp` package

## Important Technical Notes

### Timefold Planning Entity Rules
- Both `Driver` and `Event` are `@PlanningEntity` (Driver owns the `@PlanningListVariable<Event>`, Event carries the shadow variables)
- Both are marked with `@PlanningEntityCollectionProperty` in `VrpSolution`; there are no `@ProblemFactCollectionProperty`-only entities left in the planning graph
- `Event.arrivalTimeSupplier` / `Event.cumulativePassengerCountSupplier` must remain pure functions (no side effects) — Timefold recomputes them automatically based on their declared `@ShadowSources`

### Qute Template Escaping
- JavaScript curly braces in templates need escaping: `{ldelim}` for `{`, `{rdelim}` for `}`
- Or use `{#verbatim}...{/verbatim}` blocks for raw JavaScript

### HTMX Reload Logic
- Use explicit status check: `event.detail.xhr.status >= 200 && event.detail.xhr.status < 300`
- Prevents reload on HTTP 409 conflict responses, preserving error messages

### Hibernate Proxy Compatibility
- Employee entity implements `equals()` and `hashCode()` using ID field
- Required for proper comparison when Hibernate uses proxies

## Optimization Problem Structure

**Optimization goals (priority order):**
1. Satisfy all hard constraints (driver assignment, capacity, time windows, pairing, ArbZG limits)
2. Minimize total distance driven (highest soft priority - fuel and vehicle wear)
3. Minimize driver waiting time
4. Return drivers close to home at end of route
5. Avoid excessive idle time (> 4 hours between events)

**ArbZG (Arbeitszeitgesetz) Compliance:**
- `maxDailyWorkingHours` (10h/day): Sum of event durations (arrivalTime → departureTime) per driver per shiftDate. Night shifts spanning midnight count toward the day the shift started per shiftDate.
- `maxWeeklyWorkingHours` (40h/week): Sum of all event durations per driver across the entire planning week.
- `maxConsecutiveDrivingHours` (4h continuous): Consecutive events with gaps < 30 min count as continuous driving. A gap >= 30 min resets the counter. Only events on the same shiftDate are considered consecutive.
- These are hard constraints — violations produce hard score penalties proportional to the excess.

**Route flexibility:**
- Routes don't require strict pickup → dropoff → pickup → return sequences
- Interleaved pickups/dropoffs allowed if it optimizes the route
- Example: Home → Pickup1 → Pickup2 → Customer1 → Pickup3 → Customer2

**Driver patterns:**
- Single route: Home → pickups/dropoffs → Home
- Break between routes: 30 min minimum (resets consecutive driving), 4 hours maximum
- Ideal: 4 hours driving → 30 min break → 4 hours driving
- Customer demand rarely allows perfect patterns

**Multi-stop events (FR-3):**
- Multiple customer deliveries in one trip: PickupLocation → CustomerA (nearest) → CustomerB → ...
- All passengers board at the pickup stop, alight at their respective customer stops
- Capacity checked at peak concurrent load (not just per-event count)
- Per-stop time windows ensure each customer's shift start deadline is met

**Shift handling:**
- Customers can have up to 3 shifts per day
- Weekends may have different patterns
- All modeled as pickup/dropoff events
- Weekly planning with mid-week update capability
