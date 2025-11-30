# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Employee Transportation VRP Solver - A Java-based optimization system for routing drivers to transport employees between a central hub (Hauptbahnhof Worms) and customer locations. Uses Timefold Solver for VRP optimization with real-world routing via GraphHopper and OSM data.

**Technology Stack:**
- Quarkus 3.17.3 (Java 17)
- Timefold Solver 1.17.0 (VRP optimization)
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
   - Used as vehicles/anchors in the Timefold chained planning graph
   - Default capacity: 6 passengers

2. **SITE_EMPLOYEE** - Employees who need transportation
   - Generate pickup/dropoff `Event` objects based on shift assignments
   - Batched together when going to same customer/shift/day
   - Assigned to driver routes by the solver

### Timefold Chained Planning Pattern

The VRP uses Timefold's chained planning variable pattern (NOT vehicle routing):

**Key architectural rule:** `Driver` is a **problem fact**, NOT a planning entity. It must never have `@PlanningEntity` annotation.

**Chain structure:**
```
Driver (anchor) → Event₁ → Event₂ → ... → Eventₙ → null
                    ↑          ↑                ↑
             previousStandstill (chained planning variable)
```

**Domain objects:**
- `Standstill` interface - Implemented by both `Driver` and `Event` to participate in chains
- `Driver` - Anchor point, provides home location, implements `Standstill`
- `Event` - Planning entity with `@PlanningVariable(graphType = CHAINED)` on `previousStandstill`
- `Event.driver` - `@AnchorShadowVariable` automatically tracks which Driver owns this event
- `Event.arrivalTime` - `@ShadowVariable` updated by `ArrivalTimeUpdatingVariableListener`

**Variable listener chain propagation:**
The `ArrivalTimeUpdatingVariableListener` recursively updates arrival times:
- When an Event's `previousStandstill` changes, its `arrivalTime` is recalculated
- The listener then finds the next Event in the chain and updates it
- This cascades through the entire route chain

### Event Generation and Batching

`EventGenerationService.generateEventsForWeek()`:
- Groups employees by (customer, shift, day) for batching
- Creates paired pickup/dropoff Events with `List<Employee> passengers`
- Calculates distances using GraphHopper
- Sets time windows from `ShiftDemand` configuration

**Event batching logic:**
- Multiple employees going to same customer/shift/day → single Event with multiple passengers
- Reduces events dramatically (e.g., 68 unbatched → 40 batched)
- `Event.getPassengerCount()` returns size of passengers list
- Vehicle capacity constraint checks `passengerCount <= driver.maxCapacity`

### Constraint Provider

`VrpConstraintProvider` defines hard and soft constraints:

**Hard constraints:**
- `driverAssignmentRequired` - Every event must have a driver
- `vehicleCapacityConstraint` - Passenger count cannot exceed driver capacity
- `timeWindowConstraint` - Events must complete before `maxEndTime`
- `pairingConstraint` - Pickup/dropoff pairs must use same driver, pickup before dropoff

**Soft constraints (optimization goals):**
- `minimizeTotalDistance` - Primary optimization goal
- `minimizeWaitingTime` - Reduce driver idle time

### Solver Flow

1. User navigates to `/optimize` page and clicks "Start Optimization"
2. `POST /api/solve` → `SolverService.solve()`
3. Filters active DRIVER employees → creates `Driver` domain objects
4. `EventGenerationService` generates batched pickup/dropoff Events
5. Creates `VrpSolution` with drivers, events, locations
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
- Used in event generation for accurate distance metrics

## File Structure

**Domain (Timefold planning entities):**
- `src/main/java/com/vrp/domain/` - Planning entities and problem facts
  - `VrpSolution.java` - `@PlanningSolution` with drivers, events, locations
  - `Event.java` - `@PlanningEntity` with chained `previousStandstill` variable
  - `Driver.java` - Problem fact (NOT planning entity), implements `Standstill`
  - `Standstill.java` - Interface for chain participants
  - `Location.java` - Geographic coordinates with distance calculations

**Persistence (JPA entities):**
- `src/main/java/com/vrp/entity/` - Database entities
  - `Employee.java` - With `EmployeeType` enum
  - `Customer.java` - Customer locations
  - `ShiftDemand.java` - Shift definitions and assignments

**Business Logic:**
- `src/main/java/com/vrp/service/SolverService.java` - Optimization job management
- `src/main/java/com/vrp/service/EventGenerationService.java` - Event creation and batching
- `src/main/java/com/vrp/service/GraphHopperService.java` - Real-world routing
- `src/main/java/com/vrp/service/DataBootstrap.java` - Seed data for development

**Constraints:**
- `src/main/java/com/vrp/constraint/VrpConstraintProvider.java` - Hard/soft constraints

**Variable Listeners:**
- `src/main/java/com/vrp/listener/ArrivalTimeUpdatingVariableListener.java` - Shadow variable updates

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
- Timefold: 120s termination limit, REPRODUCIBLE mode
- GraphHopper: OSM file path, cache directory, car profile
- Logging: INFO level, DEBUG for `com.vrp` package

## Important Technical Notes

### Timefold Planning Entity Rules
- Driver must NEVER be annotated with `@PlanningEntity`
- Quarkus Timefold processor validates this at build time
- Only Event should be a planning entity
- Driver is marked with `@ProblemFactCollectionProperty` in VrpSolution

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
1. Minimize total distance driven (highest priority - fuel and vehicle wear)
2. Maximize consecutive working hours for drivers (soft constraint)
3. Minimize employee travel time (lower priority)

**Route flexibility:**
- Routes don't require strict pickup → dropoff → pickup → return sequences
- Interleaved pickups/dropoffs allowed if it optimizes the route
- Example: Home → Pickup1 → Pickup2 → Customer1 → Pickup3 → Customer2

**Driver patterns:**
- Single route: Home → pickups/dropoffs → Home
- Break between routes: 30 min minimum, 4 hours maximum
- Ideal: 4 hours driving → 30 min break → 4 hours driving
- Customer demand rarely allows perfect patterns

**Shift handling:**
- Customers can have up to 3 shifts per day
- Weekends may have different patterns
- All modeled as pickup/dropoff events
- Weekly planning with mid-week update capability
