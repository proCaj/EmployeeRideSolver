# Employee Transportation VRP Solver

## Overview
A Java-based employee transportation routing optimization system using Quarkus 3.17.3, Timefold Solver 1.17.0, and GraphHopper for route planning. The system optimizes driver routes for transporting employees between a central hub (Hauptbahnhof Worms) and multiple customer locations.

## Technology Stack
- **Backend:** Quarkus 3.17.3 with Java 17
- **Optimization:** Timefold Solver 1.17.0
- **Routing:** GraphHopper 8.0 with OSM data
- **Database:** H2 (file-based persistence)
- **ORM:** Hibernate with Panache
- **Frontend:** Qute templates + HTMX + TailwindCSS
- **Build:** Maven 3.x

## Project Structure
- `src/main/java/com/vrp/domain/` - Timefold planning entities (Event, Driver, VrpSolution)
- `src/main/java/com/vrp/entity/` - JPA entities (Customer, Employee, ShiftDemand)
- `src/main/java/com/vrp/service/` - Business logic and solver services
- `src/main/java/com/vrp/resource/` - REST API endpoints
- `src/main/java/com/vrp/listener/` - Timefold variable listeners
- `src/main/java/com/vrp/constraint/` - Constraint provider
- `src/main/resources/templates/` - Qute HTML templates

## Key Features
1. Employee and customer management with CRUD operations
2. Shift demand configuration with time windows
3. Real-world routing using GraphHopper (OSM data)
4. VRP optimization with hard/soft constraints
5. Route visualization with timeline view
6. Live optimization progress tracking

## Recent Changes
- **November 29, 2025 - Event Batching & Map Integration:**
  - Implemented passenger batching: Events now group employees going to same shift/customer/day
  - Reduced events from 68 to 40 (batched) and total distance from 614 km to 334 km (45% improvement)
  - Refactored Event domain: replaced single assignedEmployee with List<Employee> passengers
  - Added getPassengerCount() and getPassengerNames() methods for display
  - Added Leaflet.js interactive map to routes page with OpenStreetMap tiles
  - Map displays hub (green) and customer (blue) location markers for the Worms area
  - Connected routes page to solver results via WebResource.routes() method
  - Templates show passenger counts (+N/-N badges) and batched employee names
  - Added vehicle capacity constraint checking passengerCount vs driver.maxCapacity

- **November 29, 2025 - Bug Fixes & Validation:**
  - Fixed HTTP 415 Unsupported Media Type error on employee unassignment endpoints by adding @Consumes(MediaType.WILDCARD)
  - Implemented concurrent shift assignment validation: checkShiftConflict() prevents employees from being assigned to overlapping shifts on the same day
  - Returns HTTP 409 with styled error message showing conflicting shift details
  - Fixed HTMX reload logic using explicit status code check (event.detail.xhr.status >= 200 && < 300) to preserve conflict messages
  - Fixed location display in optimization results using event.toLocation property
  - Solver successfully optimizes: 2 drivers, 40 batched events, ~334 km total distance

- **November 29, 2025 - Dark Theme UI Overhaul:**
  - Implemented complete dark theme redesign across all pages
  - Created custom CSS color scheme with modern styling (DM Sans font family)
  - Updated base template with sidebar navigation and proper navigation highlighting
  - Redesigned Shifts page with collapsible day cards and employee assignment panel
  - Implemented drag-and-drop employee assignment functionality on shifts page
  - Added modal-based employee assignment as alternative to drag-and-drop
  - Updated all form templates (employee, customer, shift) with consistent dark theme styling
  - Fixed Qute template issues with JavaScript curly braces escaping
  - Added responsive table styling for employee list
  - Enhanced card-based layout for customer locations
  - Improved optimization page with solver status panel
  - All pages now use consistent CSS variables for theming

- **October 22, 2025 - Driver/Employee Type Distinction & VRP Integration:**
  - Implemented EmployeeType enum (DRIVER vs SITE_EMPLOYEE) for proper role distinction
  - Updated Employee entity with employeeType field (EnumType.STRING, default SITE_EMPLOYEE)
  - Modified SolverService to filter active DRIVER employees as vehicles for VRP optimization
  - Created createDriversFromEmployees method to convert DRIVER employees to Driver domain objects
  - Removed hardcoded dummy drivers - system now uses real employees marked as DRIVER type
  - Updated employee forms and REST endpoints to support employee type selection
  - Enhanced employee list page to display employee type badges and shift assignments
  - DataBootstrap updated to seed 2 drivers and 6 site employees with proper type assignments
  - Database schema recreated to accommodate employeeType column
  - Architect-reviewed and approved: drivers properly filtered and used as optimization vehicles
  
- **October 22, 2025 - Employee-Shift Assignment Feature:**
  - Added employee management UI with list view and create/edit forms
  - Implemented employee-shift assignment UI on shift edit page
  - Added form-encoded support to EmployeeResource with active parameter
  - Fixed employee form to use PUT method for updates via HTMX
  - Implemented equals/hashCode in Employee entity for Hibernate proxy compatibility
  - Assignment buttons correctly show assign/unassign state based on actual assignments
  - Dual input support for all CRUD endpoints (JSON + form-encoded data)
  
- **October 22, 2025 - UI Implementation:**
  - Added complete customer management UI (list view with cards, create/edit forms)
  - Added complete shift management UI (day-grouped list view, create/edit forms)
  - Implemented dual input support for REST endpoints (JSON + form-encoded data)
  - Fixed Qute template enum/string handling for proper form preselection
  - Added HTMX-powered delete operations for customers and shifts
  
- **October 21, 2025 - Initial Setup:**
  - Maven structure created with Quarkus, Timefold, GraphHopper dependencies
  - Application properties configured for H2 database and GraphHopper
  - Core domain entities and constraint provider implemented

## System Architecture

### Employee Type System
The system distinguishes between two employee types:
1. **DRIVER** - Employees who operate vehicles and transport site employees
   - Converted to Driver domain objects for VRP optimization
   - Used as vehicles in the Timefold solver
   - Must be marked as active to participate in route planning

2. **SITE_EMPLOYEE** - Employees who need transportation to customer sites
   - Generate pickup and dropoff events in the VRP solution
   - Assigned to drivers based on optimization results
   - Linked to shifts via ShiftDemand assignments

### VRP Optimization Flow
1. User triggers optimization via `/optimize` page (POST /api/solve)
2. SolverService filters active DRIVER employees and creates Driver domain objects
3. Site employees and shift demands generate Event objects (pickups/dropoffs)
4. Timefold solver assigns events to drivers while respecting constraints
5. Solution accessible via GET /api/solve/{jobId}/solution
6. Routes displayable on `/routes` page with driver timelines

### Timefold Chained VRP Domain Model
The system uses Timefold's chained planning variable pattern:

1. **Driver (Problem Fact)** - The anchor point for each route chain
   - Implements `Standstill` interface to participate in the chain
   - NOT a planning entity - marked with `@ProblemFactCollectionProperty` in VrpSolution
   - Provides starting location (home location) for route planning

2. **Event (Planning Entity)** - The pickup/dropoff events that form chains
   - Only planning entity with `@PlanningVariable(graphType = CHAINED)`
   - `previousStandstill` points to either Driver (anchor) or previous Event
   - `@AnchorShadowVariable` tracks which Driver owns this event
   - Arrival times calculated by `ArrivalTimeUpdatingVariableListener`

3. **Chain Structure**
   - Driver → Event₁ → Event₂ → ... → Eventₙ → null
   - Each Event's `previousStandstill` creates the linked chain
   - Shadow variable `driver` allows constraints to access route owner

**Key Technical Note:** Driver must NOT be a @PlanningEntity since it has no planning variables. The Timefold Quarkus processor validates this at build time.
