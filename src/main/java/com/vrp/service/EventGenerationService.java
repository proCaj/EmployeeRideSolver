package com.vrp.service;

import com.graphhopper.GraphHopper;
import com.vrp.domain.Event;
import com.vrp.domain.Location;
import com.vrp.entity.Employee;
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
    
    public List<Event> generateEventsForWeek(List<ShiftDemand> shiftDemands, LocalDate weekStart) {
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
            
            LocalDate shiftDate = weekStart.with(shift.dayOfWeek);
            if (shiftDate.isBefore(weekStart)) {
                shiftDate = shiftDate.plusWeeks(1);
            }
            
            Location customerLocation = new Location(
                shift.customer.name,
                shift.customer.latitude,
                shift.customer.longitude
            );
            
            for (Employee employee : shift.assignedEmployees) {
                Event pickupEvent = createPickupEvent(shift, employee, shiftDate, customerLocation, hopper);
                events.add(pickupEvent);
                
                if (shift.requiresReturnTrip) {
                    Event dropoffEvent = createDropoffEvent(shift, employee, shiftDate, customerLocation, hopper);
                    pickupEvent.setPairedEvent(dropoffEvent);
                    dropoffEvent.setPairedEvent(pickupEvent);
                    events.add(dropoffEvent);
                }
            }
        }
        
        LOG.info("Generated " + events.size() + " events for week starting " + weekStart);
        return events;
    }
    
    private Event createPickupEvent(ShiftDemand shift, Employee employee, LocalDate shiftDate,
                                     Location customerLocation, GraphHopper hopper) {
        LocalDateTime shiftStartDateTime = LocalDateTime.of(shiftDate, shift.startTime);
        Instant shiftStartInstant = shiftStartDateTime.atZone(ZoneId.systemDefault()).toInstant();
        
        Instant minStartTime = shiftStartInstant.minus(shift.getEarlyArrivalBufferMax());
        Instant maxEndTime = shiftStartInstant.minus(shift.getEarlyArrivalBufferMin());
        
        Duration travelTime = Location.HUB.getTravelTime(customerLocation, hopper);
        long distance = Location.HUB.getDistanceTo(customerLocation, hopper);
        
        Duration serviceTime = Duration.ofMinutes(2);
        Duration totalDuration = travelTime.plus(serviceTime);
        
        String eventId = "pickup-" + shift.id + "-" + employee.id + "-" + shiftDate + "-" + UUID.randomUUID().toString().substring(0, 8);
        
        return new Event(
            eventId,
            Location.HUB,
            customerLocation,
            minStartTime,
            maxEndTime,
            totalDuration,
            distance,
            true,
            employee,
            shiftDate.getDayOfWeek().getValue() <= 5 ? "weekday" : "weekend",
            shift.getEarlyArrivalBufferMin(),
            shift.getEarlyArrivalBufferMax()
        );
    }
    
    private Event createDropoffEvent(ShiftDemand shift, Employee employee, LocalDate shiftDate,
                                      Location customerLocation, GraphHopper hopper) {
        LocalDateTime shiftEndDateTime = LocalDateTime.of(shiftDate, shift.endTime);
        Instant shiftEndInstant = shiftEndDateTime.atZone(ZoneId.systemDefault()).toInstant();
        
        Instant minStartTime = shiftEndInstant;
        Instant maxEndTime = shiftEndInstant.plus(Duration.ofHours(1));
        
        Duration travelTime = customerLocation.getTravelTime(Location.HUB, hopper);
        long distance = customerLocation.getDistanceTo(Location.HUB, hopper);
        
        Duration serviceTime = Duration.ofMinutes(2);
        Duration totalDuration = travelTime.plus(serviceTime);
        
        String eventId = "dropoff-" + shift.id + "-" + employee.id + "-" + shiftDate + "-" + UUID.randomUUID().toString().substring(0, 8);
        
        return new Event(
            eventId,
            customerLocation,
            Location.HUB,
            minStartTime,
            maxEndTime,
            totalDuration,
            distance,
            false,
            employee,
            shiftDate.getDayOfWeek().getValue() <= 5 ? "weekday" : "weekend",
            Duration.ZERO,
            Duration.ZERO
        );
    }
}
