
# Implementation Plan: Employee Transportation VRP Enhancements

## Overview

This plan addresses the gaps between the problem description and current implementation, organized into phases that can be implemented incrementally.

---

## Phase 1: Data Model Extensions

### 1.1 Add Location Fields to Employee Entity

**File:** `src/main/java/com/vrp/entity/Employee.java`

```java
// Add fields for driver home location
@Column(name = "home_latitude")
public Double homeLatitude;

@Column(name = "home_longitude")
public Double homeLongitude;

// Add fields for site employee pickup preference
@Column(name = "pickup_latitude")
public Double pickupLatitude;

@Column(name = "pickup_longitude")
public Double pickupLongitude;
```

**Migration/Bootstrap:** Update `DataBootstrap.java` to set default values:
- Drivers without home coords → use hub coordinates (Hauptbahnhof Worms)
- Site employees without pickup coords → use hub coordinates

### 1.2 Add Helper Methods to Employee

```java
public Location getHomeLocation(Location defaultHub) {
    if (homeLatitude != null && homeLongitude != null) {
        return new Location("Home-" + id, homeLatitude, homeLongitude);
    }
    return defaultHub;
}

public Location getPickupLocation(Location defaultHub) {
    if (pickupLatitude != null && pickupLongitude != null) {
        return new Location("Pickup-" + id, pickupLatitude, pickupLongitude);
    }
    return defaultHub;
}
```

### 1.3 Update Driver Domain Object

**File:** `src/main/java/com/vrp/domain/Driver.java`

Add field to store home location (should already have `location`, but verify it's populated from `Employee.getHomeLocation()`).

---

## Phase 2: Cumulative Capacity Shadow Variable

### 2.1 Add Shadow Variable to Event

**File:** `src/main/java/com/vrp/domain/Event.java`

```java
@ShadowVariable(variableListenerClass = PassengerCountUpdatingVariableListener.class,
                sourceVariableName = "previousStandstill")
private Integer cumulativePassengerCount;

public Integer getCumulativePassengerCount() {
    return cumulativePassengerCount;
}

public void setCumulativePassengerCount(Integer count) {
    this.cumulativePassengerCount = count;
}
```

### 2.2 Create Variable Listener

**New file:** `src/main/java/com/vrp/listener/PassengerCountUpdatingVariableListener.java`

```java
package com.vrp.listener;

import ai.timefold.solver.core.api.domain.variable.VariableListener;
import ai.timefold.solver.core.api.score.director.ScoreDirector;
import com.vrp.domain.Driver;
import com.vrp.domain.Event;
import com.vrp.domain.Standstill;
import com.vrp.domain.VrpSolution;

public class PassengerCountUpdatingVariableListener 
        implements VariableListener<VrpSolution, Event> {

    @Override
    public void beforeVariableChanged(ScoreDirector<VrpSolution> scoreDirector, Event event) {
        // No action needed
    }

    @Override
    public void afterVariableChanged(ScoreDirector<VrpSolution> scoreDirector, Event event) {
        updatePassengerCount(scoreDirector, event);
    }

    @Override
    public void beforeEntityAdded(ScoreDirector<VrpSolution> scoreDirector, Event event) {
        // No action needed
    }

    @Override
    public void afterEntityAdded(ScoreDirector<VrpSolution> scoreDirector, Event event) {
        updatePassengerCount(scoreDirector, event);
    }

    @Override
    public void beforeEntityRemoved(ScoreDirector<VrpSolution> scoreDirector, Event event) {
        // No action needed
    }

    @Override
    public void afterEntityRemoved(ScoreDirector<VrpSolution> scoreDirector, Event event) {
        // No action needed
    }

    private void updatePassengerCount(ScoreDirector<VrpSolution> scoreDirector, Event event) {
        Standstill previous = event.getPreviousStandstill();
        
        int previousCount = 0;
        if (previous instanceof Event) {
            Integer prevCumulative = ((Event) previous).getCumulativePassengerCount();
            previousCount = prevCumulative != null ? prevCumulative : 0;
        }
        // If previous is Driver, count starts at 0
        
        int newCount = previousCount + event.getPassengerDelta();
        
        if (!Integer.valueOf(newCount).equals(event.getCumulativePassengerCount())) {
            scoreDirector.beforeVariableChanged(event, "cumulativePassengerCount");
            event.setCumulativePassengerCount(newCount);
            scoreDirector.afterVariableChanged(event, "cumulativePassengerCount");
        }
        
        // Propagate to next event in chain
        Event nextEvent = findNextEvent(scoreDirector.getWorkingSolution(), event);
        if (nextEvent != null) {
            updatePassengerCount(scoreDirector, nextEvent);
        }
    }

    private Event findNextEvent(VrpSolution solution, Event current) {
        for (Event event : solution.getEvents()) {
            if (event.getPreviousStandstill() == current) {
                return event;
            }
        }
        return null;
    }
}
```

---

## Phase 3: Update Constraints

### 3.1 Modify Vehicle Capacity Constraint

**File:** `src/main/java/com/vrp/constraint/VrpConstraintProvider.java`

**Replace** the existing `vehicleCapacityConstraint`:

```java
// OLD: checks individual event passenger count
// NEW: checks cumulative passenger count at each point in route

Constraint vehicleCapacityConstraint(ConstraintFactory factory) {
    return factory.forEach(Event.class)
            .filter(event -> event.getCumulativePassengerCount() != null 
                          && event.getDriver() != null
                          && event.getCumulativePassengerCount() > event.getDriver().getMaxCapacity())
            .penalize(HardSoftScore.ONE_HARD,
                     event -> event.getCumulativePassengerCount() - event.getDriver().getMaxCapacity())
            .asConstraint("vehicleCapacity");
}
```

### 3.2 Add Return-to-Home Distance Constraint

```java
Constraint returnToHomeDistance(ConstraintFactory factory) {
    return factory.forEach(Event.class)
            .filter(event -> event.getDriver() != null)
            .filter(event -> isLastEventForDriver(event))
            .penalize(HardSoftScore.ONE_SOFT,
                     event -> (int) event.getLocation().distanceTo(event.getDriver().getLocation()))
            .asConstraint("returnToHomeDistance");
}

// Helper method (or use a more efficient approach with shadow variable for "nextEvent")
private boolean isLastEventForDriver(Event event) {
    // This is inefficient - see Phase 5 for optimization
    // For now: an event is "last" if no other event has it as previousStandstill
    // This check needs access to all events - may need to be done differently
}
```

**Better approach for last-event detection:** Add a `@ShadowVariable` for `nextEvent` or compute in the constraint using groupBy:

```java
Constraint returnToHomeDistance(ConstraintFactory factory) {
    return factory.forEach(Driver.class)
            .join(Event.class,
                  Joiners.equal(driver -> driver, Event::getDriver))
            .groupBy((driver, event) -> driver,
                     ConstraintCollectors.toList((driver, event) -> event))
            .penalize(HardSoftScore.ONE_SOFT,
                     (driver, events) -> {
                         Event lastEvent = findLastEvent(events);
                         if (lastEvent == null) return 0;
                         return (int) lastEvent.getLocation().distanceTo(driver.getLocation());
                     })
            .asConstraint("returnToHomeDistance");
}

private Event findLastEvent(List<Event> events) {
    Set<Event> hasSuccessor = events.stream()
            .map(Event::getPreviousStandstill)
            .filter(s -> s instanceof Event)
            .map(s -> (Event) s)
            .collect(Collectors.toSet());
    
    return events.stream()
            .filter(e -> !hasSuccessor.contains(e))
            .findFirst()
            .orElse(null);
}
```

### 3.3 Add Idle Time Penalty Constraint

```java
Constraint excessiveIdleTime(ConstraintFactory factory) {
    return factory.forEach(Event.class)
            .filter(event -> event.getPreviousStandstill() instanceof Event)
            .filter(event -> {
                Event previous = (Event) event.getPreviousStandstill();
                if (previous.getDepartureTime() == null || event.getArrivalTime() == null) {
                    return false;
                }
                Duration idle = Duration.between(previous.getDepartureTime(), event.getArrivalTime());
                return idle.toMinutes() > 240; // More than 4 hours
            })
            .penalize(HardSoftScore.ONE_SOFT,
                     event -> {
                         Event previous = (Event) event.getPreviousStandstill();
                         Duration idle = Duration.between(previous.getDepartureTime(), event.getArrivalTime());
                         return (int) (idle.toMinutes() - 240); // Penalize minutes over 4 hours
                     })
            .asConstraint("excessiveIdleTime");
}
```

### 3.4 Update Time Window Constraints

Add soft constraint for "ideal" arrival time (20 min early):

```java
Constraint idealArrivalTime(ConstraintFactory factory) {
    return factory.forEach(Event.class)
            .filter(event -> event.getArrivalTime() != null && event.isPickup())
            .filter(event -> {
                Instant idealArrival = event.getMinStartTime().minus(event.getEarlyArrivalMax());
                return event.getArrivalTime().isAfter(idealArrival);
            })
            .penalize(HardSoftScore.ONE_SOFT,
                     event -> {
                         Instant idealArrival = event.getMinStartTime().minus(event.getEarlyArrivalMax());
                         return (int) Duration.between(idealArrival, event.getArrivalTime()).toMinutes();
                     })
            .asConstraint("idealArrivalTime");
}
```

---

## Phase 4: Event Generation Changes

### 4.1 Remove Location-Based Batching

**File:** `src/main/java/com/vrp/service/EventGenerationService.java`

**Current behavior:** Groups employees by (customer, shift, day) → single event with multiple passengers.

**New behavior:** Generate individual events per employee. The solver optimizes routing.

```java
public List<Event> generateEventsForWeek(Location hubLocation) {
    List<Event> events = new ArrayList<>();
    List<ShiftDemand> shifts = ShiftDemand.listAll();
    
    for (ShiftDemand shift : shifts) {
        for (Employee employee : shift.assignedEmployees) {
            if (employee.employeeType == EmployeeType.SITE_EMPLOYEE) {
                // Get employee's pickup location (or default to hub)
                Location pickupLocation = employee.getPickupLocation(hubLocation);
                Location customerLocation = new Location(
                    shift.customer.name,
                    shift.customer.latitude,
                    shift.customer.longitude
                );
                
                // Create pickup event
                Event pickup = createPickupEvent(employee, shift, pickupLocation, customerLocation);
                
                // Create dropoff event
                Event dropoff = createDropoffEvent(employee, shift, customerLocation, pickupLocation);
                
                // Link paired events
                pickup.setPairedEvent(dropoff);
                dropoff.setPairedEvent(pickup);
                
                events.add(pickup);
                events.add(dropoff);
            }
        }
    }
    
    return events;
}

private Event createPickupEvent(Employee employee, ShiftDemand shift, 
                                 Location from, Location to) {
    String id = String.format("pickup-%d-%d-%s", 
                              employee.id, shift.id, shift.dayOfWeek);
    
    Instant shiftStart = calculateShiftStartInstant(shift);
    Instant minStart = shiftStart.minus(shift.getEarlyArrivalMin()); // 10 min before
    Instant maxEnd = shiftStart; // Must arrive by shift start
    
    Duration duration = Duration.ofMinutes(2); // Boarding time per passenger
    long distance = graphHopperService.getDistance(from, to);
    
    Event event = new Event();
    event.setId(id);
    event.setFromLocation(from);
    event.setToLocation(to);
    event.setMinStartTime(minStart);
    event.setMaxEndTime(maxEnd);
    event.setDuration(duration);
    event.setDistance(distance);
    event.setPickup(true);
    event.setPassengers(List.of(employee));
    event.setShiftDemand(shift);
    event.setEarlyArrivalMin(Duration.ofMinutes(10));
    event.setEarlyArrivalMax(Duration.ofMinutes(20));
    
    return event;
}
```

### 4.2 Handle Capacity Overflow (Pre-splitting)

Not needed anymore since we generate individual events. The solver handles assignment to drivers respecting capacity via the cumulative constraint.

---

## Phase 5: Unassigned Event Diagnostics

### 5.1 Create Diagnostic Service

**New file:** `src/main/java/com/vrp/service/SolutionDiagnosticService.java`

```java
package com.vrp.service;

import com.vrp.domain.Driver;
import com.vrp.domain.Event;
import com.vrp.domain.VrpSolution;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.*;

@ApplicationScoped
public class SolutionDiagnosticService {

    public DiagnosticReport analyze(VrpSolution solution) {
        List<Event> unassigned = solution.getEvents().stream()
                .filter(e -> e.getDriver() == null)
                .toList();
        
        if (unassigned.isEmpty()) {
            return new DiagnosticReport(Collections.emptyList(), Collections.emptyList());
        }
        
        List<UnassignedEventReason> reasons = new ArrayList<>();
        
        for (Event event : unassigned) {
            UnassignedEventReason reason = diagnoseEvent(event, solution);
            reasons.add(reason);
        }
        
        List<String> suggestions = generateSuggestions(reasons, solution);
        
        return new DiagnosticReport(reasons, suggestions);
    }
    
    private UnassignedEventReason diagnoseEvent(Event event, VrpSolution solution) {
        // Check each potential reason
        
        // 1. Time window conflict with all drivers
        boolean timeConflict = checkTimeWindowConflict(event, solution);
        
        // 2. Capacity would be exceeded on all possible routes
        boolean capacityIssue = checkCapacityIssue(event, solution);
        
        // 3. No drivers available at all
        boolean noDrivers = solution.getDrivers().isEmpty();
        
        String reason;
        if (noDrivers) {
            reason = "No drivers available";
        } else if (timeConflict) {
            reason = "Time window conflict - no driver can reach this event in time";
        } else if (capacityIssue) {
            reason = "Capacity exceeded - all feasible routes are at capacity";
        } else {
            reason = "Unknown - solver could not find feasible assignment";
        }
        
        return new UnassignedEventReason(event, reason);
    }
    
    private boolean checkTimeWindowConflict(Event event, VrpSolution solution) {
        // Check if any driver could theoretically reach this event
        for (Driver driver : solution.getDrivers()) {
            long travelTime = driver.getLocation().timeTo(event.getFromLocation());
            // Simplified check - in reality would need to consider existing assignments
            if (travelTime < event.getMaxEndTime().toEpochMilli()) {
                return false;
            }
        }
        return true;
    }
    
    private boolean checkCapacityIssue(Event event, VrpSolution solution) {
        // Check if this is a pickup that would exceed capacity everywhere
        if (!event.isPickup()) return false;
        
        int passengerCount = event.getPassengerCount();
        for (Driver driver : solution.getDrivers()) {
            if (passengerCount <= driver.getMaxCapacity()) {
                return false;
            }
        }
        return true;
    }
    
    private List<String> generateSuggestions(List<UnassignedEventReason> reasons, 
                                              VrpSolution solution) {
        List<String> suggestions = new ArrayList<>();
        
        long capacityIssues = reasons.stream()
                .filter(r -> r.reason().contains("Capacity"))
                .count();
        
        long timeIssues = reasons.stream()
                .filter(r -> r.reason().contains("Time window"))
                .count();
        
        if (capacityIssues > 0) {
            int additionalDrivers = (int) Math.ceil(capacityIssues / 6.0);
            suggestions.add(String.format(
                "%d events unassigned due to capacity. Adding %d driver(s) would likely resolve this.",
                capacityIssues, additionalDrivers));
        }
        
        if (timeIssues > 0) {
            suggestions.add(String.format(
                "%d events have time window conflicts. Consider adjusting shift times or adding drivers in different locations.",
                timeIssues));
        }
        
        if (reasons.size() > 0 && suggestions.isEmpty()) {
            suggestions.add(String.format(
                "%d events could not be assigned. Consider adding more drivers.",
                reasons.size()));
        }
        
        return suggestions;
    }
    
    public record UnassignedEventReason(Event event, String reason) {}
    
    public record DiagnosticReport(
        List<UnassignedEventReason> unassignedReasons,
        List<String> suggestions
    ) {}
}
```

### 5.2 Add Diagnostic Endpoint

**File:** `src/main/java/com/vrp/resource/SolverResource.java`

```java
@GET
@Path("/{jobId}/diagnostics")
@Produces(MediaType.APPLICATION_JSON)
public Response getDiagnostics(@PathParam("jobId") String jobId) {
    VrpSolution solution = solverService.getSolution(jobId);
    if (solution == null) {
        return Response.status(404).entity("Solution not found").build();
    }
    
    DiagnosticReport report = diagnosticService.analyze(solution);
    return Response.ok(report).build();
}
```

---

## Phase 6: Database Migration

### 6.1 Add Flyway Migration (or update schema)

**New file:** `src/main/resources/db/migration/V2__add_employee_locations.sql`

```sql
ALTER TABLE employees ADD COLUMN home_latitude DOUBLE;
ALTER TABLE employees ADD COLUMN home_longitude DOUBLE;
ALTER TABLE employees ADD COLUMN pickup_latitude DOUBLE;
ALTER TABLE employees ADD COLUMN pickup_longitude DOUBLE;
```

### 6.2 Update DataBootstrap

Set default coordinates for existing data:

```java
// In DataBootstrap.java
private static final double HUB_LAT = 49.6341; // Hauptbahnhof Worms
private static final double HUB_LON = 8.3507;

// When creating test drivers
driver.homeLatitude = HUB_LAT;
driver.homeLongitude = HUB_LON;

// Site employees default to hub pickup
employee.pickupLatitude = null; // Will use hub as default
employee.pickupLongitude = null;
```

---

## Phase 7: UI Updates

### 7.1 Employee Form Updates

Add fields to employee creation/edit forms for:
- Drivers: Home address (with geocoding or manual lat/long)
- Site employees: Pickup location preference (hub vs custom address)

### 7.2 Diagnostics Display

Add section to solution results page showing:
- Count of unassigned events
- Reasons for each
- Suggestions for resolution

---

## Implementation Order

| Phase | Effort | Dependencies | Priority |
|-------|--------|--------------|----------|
| 1. Data Model | Small | None | High |
| 2. Cumulative Capacity | Medium | Phase 1 | High |
| 3. Constraints | Medium | Phase 2 | High |
| 4. Event Generation | Medium | Phase 1 | High |
| 5. Diagnostics | Medium | Phase 3 | Medium |
| 6. DB Migration | Small | Phase 1 | High |
| 7. UI Updates | Medium | All above | Low |

**Recommended approach:** Implement Phases 1, 2, 3, 4, 6 together as they're tightly coupled. Then add Phase 5. UI updates (Phase 7) can come later.

---

## Testing Strategy

### Unit Tests

```java
// Test cumulative capacity calculation
@Test
void cumulativePassengerCount_tracksCorrectly() {
    Driver driver = new Driver("D1", hubLocation, 6);
    Event pickup1 = createPickupEvent(3); // +3
    Event pickup2 = createPickupEvent(2); // +2
    Event dropoff1 = createDropoffEvent(3); // -3
    
    // Chain: driver -> pickup1 -> pickup2 -> dropoff1
    pickup1.setPreviousStandstill(driver);
    pickup2.setPreviousStandstill(pickup1);
    dropoff1.setPreviousStandstill(pickup2);
    
    // After listener propagation:
    assertThat(pickup1.getCumulativePassengerCount()).isEqualTo(3);
    assertThat(pickup2.getCumulativePassengerCount()).isEqualTo(5);
    assertThat(dropoff1.getCumulativePassengerCount()).isEqualTo(2);
}

// Test capacity constraint violation
@Test
void vehicleCapacity_penalizesOverload() {
    // Setup: driver with capacity 6, route with cumulative 7
    // Assert: hard constraint violated
}
```

### Integration Tests

```java
@Test
void solver_handlesMultiplePickupsBeforeDropoffs() {
    // Setup: 3 employees going to same customer, driver capacity 6
    // Run solver
    // Assert: all assigned, cumulative never exceeds 6
}

@Test
void solver_returnsPartialSolution_whenInfeasible() {
    // Setup: 10 employees, 1 driver with capacity 6
    // Run solver
    // Assert: 6 assigned, 4 unassigned, diagnostic report generated
}
```

---

## Questions Resolved

| Question | Answer | Impact |
|----------|--------|--------|
| Multi-route days | Not explicitly modeled; idle time penalties instead | Simplified model |
| Break constraints | Soft penalty for >4hr idle | Single constraint |
| Return to home | Distance added via constraint | No virtual events |
| Batching | Removed; individual events | More events, better optimization |
| Capacity | Cumulative shadow variable | Accurate interleaved tracking |
