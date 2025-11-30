package com.vrp.service;

import com.graphhopper.GraphHopper;
import com.vrp.domain.Event;
import com.vrp.domain.Location;
import com.vrp.entity.Employee;
import com.vrp.entity.EmployeeType;
import com.vrp.entity.ShiftDemand;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class EventGenerationService {
    
    private static final Logger LOG = Logger.getLogger(EventGenerationService.class);
    
    @Inject
    GraphHopperService graphHopperService;
    
    public List<Event> generateEventsForWeek(List<ShiftDemand> shiftDemands, LocalDate weekStart, Location hubLocation) {
        List<Event> events = new ArrayList<>();

        GraphHopper hopper = null;
        try {
            if (graphHopperService.isInitialized()) {
                hopper = graphHopperService.getGraphHopper();
            }
        } catch (Exception e) {
            LOG.warn("GraphHopper not available, using fallback distances");
        }

        for (ShiftDemand shift : shiftDemands) {
            if (!shift.active) continue;
            if (shift.assignedEmployees == null || shift.assignedEmployees.isEmpty()) continue;

            LocalDate shiftDate = weekStart.with(shift.dayOfWeek);
            if (shiftDate.isBefore(weekStart)) {
                shiftDate = shiftDate.plusWeeks(1);
            }

            Location customerLocation = new Location(
                shift.customer.name,
                shift.customer.latitude,
                shift.customer.longitude
            );

            // Generate individual events per employee (no batching)
            for (Employee employee : shift.assignedEmployees) {
                if (employee.employeeType == EmployeeType.SITE_EMPLOYEE) {
                    // Get employee's pickup location (or default to hub)
                    Location pickupLocation = employee.getPickupLocation(hubLocation);

                    // Create pickup event
                    Event pickupEvent = createPickupEvent(employee, shift, shiftDate, pickupLocation, customerLocation, hopper);
                    events.add(pickupEvent);

                    // Create dropoff event if required
                    if (shift.requiresReturnTrip) {
                        Event dropoffEvent = createDropoffEvent(employee, shift, shiftDate, customerLocation, pickupLocation, hopper);

                        // Link paired events
                        pickupEvent.setPairedEvent(dropoffEvent);
                        dropoffEvent.setPairedEvent(pickupEvent);

                        events.add(dropoffEvent);
                    }
                }
            }
        }

        LOG.info("Generated " + events.size() + " individual events for week starting " + weekStart);
        return events;
    }
    
    private Event createPickupEvent(Employee employee, ShiftDemand shift, LocalDate shiftDate,
                                     Location fromLocation, Location toLocation, GraphHopper hopper) {
        String eventId = String.format("pickup-%d-%d-%s", employee.id, shift.id, shiftDate);

        LocalDateTime shiftStartDateTime = LocalDateTime.of(shiftDate, shift.startTime);
        Instant shiftStartInstant = shiftStartDateTime.atZone(ZoneId.systemDefault()).toInstant();

        Instant minStartTime = shiftStartInstant.minus(shift.getEarlyArrivalBufferMax());
        Instant maxEndTime = shiftStartInstant; // Must arrive by shift start

        Duration travelTime = fromLocation.getTravelTime(toLocation, hopper);
        long distance = fromLocation.getDistanceTo(toLocation, hopper);

        Duration boardingTime = Duration.ofMinutes(2); // Boarding time per passenger
        Duration totalDuration = travelTime.plus(boardingTime);

        Event event = new Event(
            eventId,
            fromLocation,
            toLocation,
            minStartTime,
            maxEndTime,
            totalDuration,
            distance,
            true, // isPickup
            List.of(employee),
            shift,
            shiftDate.getDayOfWeek().getValue() <= 5 ? "weekday" : "weekend",
            shift.getEarlyArrivalBufferMin(),
            shift.getEarlyArrivalBufferMax()
        );

        return event;
    }

    private Event createDropoffEvent(Employee employee, ShiftDemand shift, LocalDate shiftDate,
                                      Location fromLocation, Location toLocation, GraphHopper hopper) {
        String eventId = String.format("dropoff-%d-%d-%s", employee.id, shift.id, shiftDate);

        LocalDateTime shiftEndDateTime = LocalDateTime.of(shiftDate, shift.endTime);
        Instant shiftEndInstant = shiftEndDateTime.atZone(ZoneId.systemDefault()).toInstant();

        Instant minStartTime = shiftEndInstant;
        Instant maxEndTime = shiftEndInstant.plus(Duration.ofHours(1));

        Duration travelTime = fromLocation.getTravelTime(toLocation, hopper);
        long distance = fromLocation.getDistanceTo(toLocation, hopper);

        Duration boardingTime = Duration.ofMinutes(2); // Boarding time per passenger
        Duration totalDuration = travelTime.plus(boardingTime);

        Event event = new Event(
            eventId,
            fromLocation,
            toLocation,
            minStartTime,
            maxEndTime,
            totalDuration,
            distance,
            false, // isPickup (this is dropoff)
            List.of(employee),
            shift,
            shiftDate.getDayOfWeek().getValue() <= 5 ? "weekday" : "weekend",
            Duration.ZERO,
            Duration.ZERO
        );

        return event;
    }
}
