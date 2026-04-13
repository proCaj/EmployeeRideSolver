# Employee Transportation VRP Solver

## Overview
A Java-based employee transportation routing optimization system using Quarkus 3.17.3, Timefold Solver 1.17.0, and GraphHopper for route planning. The system optimizes driver routes for transporting employees between a central hub (City-Fahrschule, Worms) and multiple customer locations, with same-customer batching (FR-1) and cross-customer merging (FR-3) into multi-stop routes.

## Technology Stack
- **Backend:** Quarkus 3.17.3 with Java 17
- **Optimization:** Timefold Solver 1.17.0
- **Routing:** GraphHopper 8.0 with OSM data
- **Database:** H2 (file-based persistence)
- **ORM:** Hibernate with Panache
- **Frontend:** Qute templates + HTMX + custom dark theme CSS
- **Build:** Maven 3.x

## Project Structure
- `src/main/java/com/vrp/domain/` - Timefold planning entities (Event, Stop, Driver, VrpSolution, Location, Standstill)
- `src/main/java/com/vrp/entity/` - JPA entities (Customer, Employee, ShiftDemand)
- `src/main/java/com/vrp/service/` - Business logic and solver services
- `src/main/java/com/vrp/resource/` - REST API endpoints
- `src/main/java/com/vrp/listener/` - Timefold variable listeners
- `src/main/java/com/vrp/constraint/` - Constraint provider
- `src/main/resources/templates/` - Qute HTML templates

## Key Features
1. Employee and customer management with CRUD operations
2. Shift demand configuration with time windows
3. Two-pass event generation: FR-1 same-customer batching + FR-3 cross-customer merging
4. Multi-stop routes with per-stop time windows and capacity tracking
5. ArbZG-compliant working hours constraints (daily, weekly, consecutive driving)
6. Real-world routing using GraphHopper (OSM data) with Haversine fallback
7. Route visualization with timeline view and Leaflet.js map
8. Live optimization progress tracking
9. Unassigned event diagnostics with suggestions

## Event Generation Pipeline

### Pass 1: FR-1 Same-Customer Batching
Employees going to the same customer on the same shift and day, departing from the same pickup location, are grouped into a single batched event. Boarding time scales with passenger count (2 min/passenger).

### Pass 2: FR-3 Cross-Customer Merging
Pickup events at the same location within a 30-minute window are merged into multi-stop events. Customer sites are visited in geographic order (nearest-first from pickup). Each stop tracks its own boarding/alighting passengers and deadline.

**Example (FR-3, Monday morning):**
```
Stop 0: City-Fahrschule → board 4 Chep + 1 Sanner = 5 passengers
Stop 1: Chep site       → 4 alight
Stop 2: Sanner site     → 1 alight
```

## Constraints

### Hard Constraints
| Constraint | Description |
|------------|-------------|
| driverAssignmentRequired | Every event must have a driver assigned |
| vehicleCapacityConstraint | Cumulative + peak concurrent passengers ≤ driver capacity (6) |
| timeWindowConstraint | Event-level and per-stop deadlines must be met |
| pairingConstraint | Pickup/dropoff pairs: same driver, pickup before dropoff |
| maxDailyWorkingHours | 10h/day per driver, grouped by shiftDate |
| maxWeeklyWorkingHours | 40h/week per driver |
| maxConsecutiveDrivingHours | 4h max without 30min break |

### Soft Constraints
| Constraint | Description |
|------------|-------------|
| minimizeTotalDistance | Primary optimization goal |
| minimizeWaitingTime | Reduce driver idle time |
| returnToHomeDistance | Distance from last event back to driver home |
| excessiveIdleTime | Penalize gaps > 4h between consecutive events |

## System Architecture

### Employee Type System
The system distinguishes between two employee types:
1. **DRIVER** - Employees who operate vehicles and transport site employees
   - Converted to Driver domain objects with ArbZG working-hours parameters
   - Used as anchors in the Timefold solver
   - Must be marked as active to participate in route planning

2. **SITE_EMPLOYEE** - Employees who need transportation to customer sites
   - Generate pickup and dropoff events in the VRP solution
   - Batched and merged by the event generation pipeline
   - Linked to shifts via ShiftDemand assignments

### VRP Optimization Flow
1. User triggers optimization via `/optimize` page (POST /api/solver/solve)
2. SolverService filters active DRIVER employees and creates Driver domain objects
3. EventGenerationService runs the 2-pass pipeline (FR-1 batching + FR-3 merging)
4. Timefold solver assigns events to drivers while respecting all constraints
5. Solution accessible via GET /api/solver/{jobId}/solution
6. Routes displayed on `/routes` page with driver timelines and map

### Timefold Chained VRP Domain Model

1. **Driver (Problem Fact)** - The anchor point for each route chain
   - Implements `Standstill` interface to participate in the chain
   - NOT a planning entity - marked with `@ProblemFactCollectionProperty` in VrpSolution
   - Provides starting location (home location) for route planning
   - Contains ArbZG parameters: maxCapacity(6), maxDailyHours(10h), maxWeeklyHours(40h), maxConsecutiveHours(4h), minBreak(30min)

2. **Event (Planning Entity)** - Pickup/dropoff events that form chains
   - Only planning entity with `@PlanningVariable(graphType = CHAINED)`
   - `previousStandstill` points to either Driver (anchor) or previous Event
   - `@AnchorShadowVariable` tracks which Driver owns this event
   - `stops` list supports multi-stop routes (FR-3)
   - `shiftDate` groups events by work day for ArbZG compliance
   - `arrivalTime` calculated by `ArrivalTimeUpdatingVariableListener` (uses GraphHopper)
   - `cumulativePassengerCount` calculated by `PassengerCountUpdatingVariableListener`

3. **Chain Structure**
   - Driver → Event₁ → Event₂ → ... → Eventₙ → null
   - Each Event's `previousStandstill` creates the linked chain
   - Shadow variable `driver` allows constraints to access route owner
