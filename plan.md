# Implementation Status

All phases from the original implementation plan are complete.

## Completed Phases

### Phase 1: Data Model Extensions
- Employee entity: `homeLatitude`, `homeLongitude`, `pickupLatitude`, `pickupLongitude` fields
- `Employee.getHomeLocation()` / `Employee.getPickupLocation()` helper methods
- Driver domain object: populated from `Employee.getHomeLocation()`

### Phase 2: Cumulative Capacity Shadow Variable
- `Event.cumulativePassengerCount` - `@ShadowVariable` via `PassengerCountUpdatingVariableListener`
- Propagates through chain: newCount = previous cumulative + event.passengerDelta
- Used by `vehicleCapacityConstraint` (checks both cumulative and peak concurrent load)

### Phase 3: Constraint Updates
- `vehicleCapacityConstraint` - validates cumulative chain load AND peak concurrent within multi-stop events
- `timeWindowConstraint` - validates event-level AND per-stop deadlines
- `returnToHomeDistance` - soft penalty for last event's distance to driver home
- `excessiveIdleTime` - soft penalty for gaps > 4h between consecutive events
- `maxDailyWorkingHours` - hard: 10h/day per driver, grouped by shiftDate
- `maxWeeklyWorkingHours` - hard: 40h/week per driver
- `maxConsecutiveDrivingHours` - hard: 4h max without 30min break, resets on day boundaries

### Phase 4: Event Generation
- Two-pass pipeline in `EventGenerationService`:
  - Pass 1 (FR-1): Group employees by pickup location within same shift -> batched events
  - Pass 2 (FR-3): Merge pickup events at same location within 30-min window -> multi-stop events
- `Stop` domain class for individual stops within a multi-stop route
- FR-3 constructor on `Event` that derives fromLocation/toLocation from stops
- `shiftDate` field on Event for ArbZG day grouping

### Phase 5: Unassigned Event Diagnostics
- `SolutionDiagnosticService` - analyzes unassigned events with reasons and suggestions
- Diagnoses: no drivers, capacity exceeded, time window conflict, pairing issue

### Phase 6: Database / Bootstrap
- H2 auto-DDL (`quarkus.hibernate-orm.database.generation=update`)
- `DataBootstrap` seeds KW 51 demo data (5 customers, 3 drivers, 12 employees, 27+ shifts)
- Hub location: City-Fahrschule (49.6295, 8.3640)

### Phase 7: UI
- Dark theme across all pages with custom CSS variables
- Sidebar navigation with active-state highlighting
- Employee management: list, create/edit forms, type badges, active toggle
- Customer management: list with cards, create/edit forms
- Shift management: day-grouped list, create/edit, employee assignment (modal + drag-and-drop)
- Optimization page with solver status panel
- Route results with driver timelines and Leaflet.js map
- HTMX-powered interactions with proper error handling (HTTP 409 conflict preservation)

## GraphHopper Integration
- `GraphHopperService` loads OSM data (`rheinland-pfalz-latest.osm.pbf`) at startup
- `Location` record: `getDistanceTo()` and `getTravelTime()` use GraphHopper, fall back to Haversine
- `VrpSolution` stores `GraphHopper` instance for VariableListener access during solving
- `ArrivalTimeUpdatingVariableListener` uses real routing when available, Haversine/15 m/s otherwise
