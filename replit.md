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
- Initial project setup (October 21, 2025)
- Maven structure created with Quarkus, Timefold, GraphHopper dependencies
- Application properties configured for H2 database and GraphHopper
