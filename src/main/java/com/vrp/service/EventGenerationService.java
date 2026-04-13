package com.vrp.service;

import com.graphhopper.GraphHopper;
import com.vrp.domain.Event;
import com.vrp.domain.Location;
import com.vrp.domain.Stop;
import com.vrp.entity.Customer;
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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class EventGenerationService {
    
    private static final Logger LOG = Logger.getLogger(EventGenerationService.class);
    private static final Duration BOARDING_TIME_PER_PASSENGER = Duration.ofMinutes(2);
    private static final Duration ALIGHTING_TIME_PER_PASSENGER = Duration.ofMinutes(1);
    
    /**
     * FR-3: Maximum time window for combining pickups at the same location.
     * Events whose pickup times differ by less than this window can be merged
     * into a multi-stop event.
     */
    private static final Duration FR3_TIME_WINDOW = Duration.ofMinutes(30);

    @Inject
    GraphHopperService graphHopperService;

    public void setGraphHopperService(GraphHopperService graphHopperService) {
        this.graphHopperService = graphHopperService;
    }
    
    public List<Event> generateEventsForWeek(List<ShiftDemand> shiftDemands, LocalDate weekStart, Location hubLocation) {
        GraphHopper hopper = null;
        try {
            if (graphHopperService != null && graphHopperService.isInitialized()) {
                hopper = graphHopperService.getGraphHopper();
            }
        } catch (Exception e) {
            LOG.warn("GraphHopper not available, using fallback distances");
        }

        // PASS 1: FR-1 -- Generate per-customer batched events.
        // Within each shift, employees at the same pickup location are batched
        // into one event. All employees in a shift go to the same customer.
        List<Event> events = new ArrayList<>();
        for (ShiftDemand shift : shiftDemands) {
            if (!shift.active) continue;
            if (shift.assignedEmployees == null || shift.assignedEmployees.isEmpty()) continue;

            LocalDate shiftDate = weekStart.with(shift.dayOfWeek);
            if (shiftDate.isBefore(weekStart)) shiftDate = shiftDate.plusWeeks(1);

            Location customerLocation = new Location(
                shift.customer.name, shift.customer.latitude, shift.customer.longitude);

            List<Employee> siteEmployees = shift.assignedEmployees.stream()
                .filter(e -> e.employeeType == EmployeeType.SITE_EMPLOYEE)
                .collect(Collectors.toList());
            if (siteEmployees.isEmpty()) continue;

            // FR-1: Group employees by pickup location coordinates
            Map<String, List<Employee>> byPickupCoords = siteEmployees.stream()
                .collect(Collectors.groupingBy(
                    e -> formatCoordKey(e.getPickupLocation(hubLocation)),
                    Collectors.toList()));

            for (Map.Entry<String, List<Employee>> entry : byPickupCoords.entrySet()) {
                List<Employee> passengers = entry.getValue();
                Location pickupLocation = passengers.get(0).getPickupLocation(hubLocation);

                // FR-1: Create single-customer batched event
                Event pickupEvent = createPickupEvent(passengers, shift, shiftDate,
                    pickupLocation, customerLocation, hopper);
                events.add(pickupEvent);

                if (shift.requiresReturnTrip) {
                    Event dropoffEvent = createDropoffEvent(passengers, shift, shiftDate,
                        customerLocation, pickupLocation, hopper);
                    pickupEvent.setPairedEvent(dropoffEvent);
                    dropoffEvent.setPairedEvent(pickupEvent);
                    events.add(dropoffEvent);
                }
            }
        }

        // PASS 2: FR-3 -- Merge pickup events at same location within time window.
        // Different shifts (different customers) that share the same pickup location
        // and time window get merged into multi-stop events.
        events = applyFR3Merging(events, hubLocation, hopper);

        LOG.info("Generated " + events.size() + " events for week starting " + weekStart);
        return events;
    }
    
    private String formatCoordKey(Location loc) {
        return String.format("%.6f,%.6f", loc.latitude(), loc.longitude());
    }

    /**
     * FR-3: Merge pickup events at the same pickup location whose time windows
     * overlap. Creates multi-stop events that visit multiple customer sites
     * in geographic order (nearest-first from pickup).
     *
     * Example: 04:30 Chep pickup (4 pax at Hub) + 04:30 Sanner pickup (1 pax at Hub)
     * → single event: Hub(5 board) → Chep(4 alight) → Sanner(1 alight)
     *
     * TODO: Extend to merge events whose pickup locations are on the way
     * (e.g., Naruto at Tankstelle → Hub on the way to Chep). This requires
     * geographic awareness of route overlap, which is best handled by the
     * solver's chaining. Current limitation: different pickup locations
     * (Tankstelle vs Hub) are not merged, causing slight backtracking.
     */
    private List<Event> applyFR3Merging(List<Event> events, Location hubLocation, GraphHopper hopper) {
        List<Event> pickupEvents = events.stream()
            .filter(Event::isPickup)
            .collect(Collectors.toList());

        List<Event> dropoffEvents = events.stream()
            .filter(e -> !e.isPickup())
            .collect(Collectors.toList());

        // Group pickups by (shiftDate, pickup location coords)
        Map<String, List<Event>> groups = pickupEvents.stream()
            .collect(Collectors.groupingBy(
                e -> e.getShiftDate() + "@" + formatCoordKey(e.getFromLocation()),
                Collectors.toList()));

        List<Event> mergedPickups = new ArrayList<>();
        List<Event> allDropoffs = new ArrayList<>(dropoffEvents);

        for (Map.Entry<String, List<Event>> group : groups.entrySet()) {
            List<Event> sameTimePickups = group.getValue();

            if (sameTimePickups.size() <= 1) {
                mergedPickups.addAll(sameTimePickups);
                continue;
            }

            // Sort by minStartTime for clustering
            sameTimePickups.sort(Comparator.comparing(Event::getMinStartTime));

            // Cluster events within FR3_TIME_WINDOW of each other
            List<List<Event>> clusters = new ArrayList<>();
            List<Event> currentCluster = new ArrayList<>();
            currentCluster.add(sameTimePickups.get(0));

            for (int i = 1; i < sameTimePickups.size(); i++) {
                Event prev = currentCluster.get(currentCluster.size() - 1);
                Event curr = sameTimePickups.get(i);
                Duration gap = Duration.between(prev.getMinStartTime(), curr.getMinStartTime());

                if (gap.compareTo(FR3_TIME_WINDOW) <= 0) {
                    currentCluster.add(curr);
                } else {
                    clusters.add(currentCluster);
                    currentCluster = new ArrayList<>();
                    currentCluster.add(curr);
                }
            }
            clusters.add(currentCluster);

            for (List<Event> cluster : clusters) {
                if (cluster.size() == 1) {
                    mergedPickups.add(cluster.get(0));
                } else {
                    // Merge cluster into a multi-stop event
                    Event merged = mergePickupEvents(cluster, hubLocation, hopper);
                    mergedPickups.add(merged);

                    // Re-link all paired dropoffs to the merged event
                    for (Event original : cluster) {
                        Event originalDropoff = original.getPairedEvent();
                        if (originalDropoff != null) {
                            originalDropoff.setPairedEvent(merged);
                        }
                    }
                    // Link merged event to first dropoff
                    Event firstDropoff = cluster.get(0).getPairedEvent();
                    if (firstDropoff != null) {
                        merged.setPairedEvent(firstDropoff);
                    }
                }
            }
        }

        List<Event> result = new ArrayList<>(mergedPickups);
        result.addAll(allDropoffs);
        return result;
    }

    /**
     * Merges multiple pickup events into a single multi-stop event (FR-3).
     * Route: PickupLocation → CustomerA (nearest) → CustomerB → ...
     * All passengers board at the pickup location, then alight at their
     * respective customer sites in geographic order.
     */
    private Event mergePickupEvents(List<Event> events, Location hubLocation, GraphHopper hopper) {
        Location pickupLocation = events.get(0).getFromLocation();
        LocalDate shiftDate = events.get(0).getShiftDate();

        // Collect all customer destinations with their passengers and deadlines
        List<CustomerStop> customerStops = new ArrayList<>();
        for (Event e : events) {
            customerStops.add(new CustomerStop(
                e.getToLocation(),
                e.getPassengers(),
                e.getMaxEndTime(),
                e.getShiftDemand()
            ));
        }

        // Sort customer sites by distance from pickup (nearest first)
        customerStops.sort(Comparator.comparingLong(cs ->
            pickupLocation.getHaversineDistance(cs.location)));

        // Build stops list
        List<Stop> stops = new ArrayList<>();

        // Stop 0: Pickup -- all passengers board
        List<Employee> allPassengers = customerStops.stream()
            .flatMap(cs -> cs.passengers.stream())
            .collect(Collectors.toList());
        Stop boardingStop = new Stop(pickupLocation, allPassengers, 0, null);
        boardingStop.setBoardingDuration(Duration.ofMinutes(2L * allPassengers.size()));
        boardingStop.setTravelTimeFromPrevious(Duration.ZERO);
        boardingStop.setDistanceFromPrevious(0L);
        stops.add(boardingStop);

        // Subsequent stops: customer sites in geographic order
        Location previousLocation = pickupLocation;
        for (CustomerStop cs : customerStops) {
            Stop dropoffStop = new Stop(cs.location, List.of(), cs.passengers.size(), cs.location.name());
            dropoffStop.setTravelTimeFromPrevious(previousLocation.getTravelTime(cs.location, hopper));
            dropoffStop.setDistanceFromPrevious(previousLocation.getDistanceTo(cs.location, hopper));
            dropoffStop.setMaxEndTime(cs.maxEndTime);
            stops.add(dropoffStop);
            previousLocation = cs.location;
        }

        // Compute timing
        Instant minStartTime = events.stream()
            .map(Event::getMinStartTime)
            .min(Comparator.naturalOrder())
            .orElse(events.get(0).getMinStartTime());
        // MaxEndTime = tightest deadline across all customers
        Instant maxEndTime = customerStops.stream()
            .map(cs -> cs.maxEndTime)
            .min(Comparator.naturalOrder())
            .orElse(events.get(0).getMaxEndTime());

        // Build event ID reflecting multi-customer nature
        String customerNames = customerStops.stream()
            .map(cs -> cs.location.name())
            .collect(Collectors.joining("+"));
        String eventId = String.format("pickup-%s-%s-p%d",
            customerNames, shiftDate, allPassengers.size());

        Event merged = new Event(eventId, stops, minStartTime, maxEndTime,
            true, events.get(0).getShiftDemand(),
            shiftDate.getDayOfWeek().getValue() <= 5 ? "weekday" : "weekend",
            events.get(0).getEarlyArrivalMin(),
            events.get(0).getEarlyArrivalMax());
        merged.setShiftDate(shiftDate);
        return merged;
    }

    /**
     * Creates a single-customer pickup event (FR-1).
     */
    private Event createPickupEvent(List<Employee> passengers, ShiftDemand shift,
                                     LocalDate shiftDate, Location fromLocation,
                                     Location toLocation, GraphHopper hopper) {
        String eventId = String.format("pickup-%d-%d-%s-p%d",
            shift.customer.id, shift.id, shiftDate, passengers.size());

        LocalDateTime shiftStartDateTime = LocalDateTime.of(shiftDate, shift.startTime);
        Instant shiftStartInstant = shiftStartDateTime.atZone(ZoneId.systemDefault()).toInstant();

        Instant minStartTime = shiftStartInstant.minus(shift.getEarlyArrivalBufferMax());
        Instant maxEndTime = shiftStartInstant;

        Duration travelTime = fromLocation.getTravelTime(toLocation, hopper);
        long distance = fromLocation.getDistanceTo(toLocation, hopper);
        Duration boardingTime = BOARDING_TIME_PER_PASSENGER.multipliedBy(passengers.size());
        Duration totalDuration = travelTime.plus(boardingTime);

        Event event = new Event(eventId, fromLocation, toLocation,
            minStartTime, maxEndTime, totalDuration, distance,
            true, passengers, shift,
            shiftDate.getDayOfWeek().getValue() <= 5 ? "weekday" : "weekend",
            shift.getEarlyArrivalBufferMin(),
            shift.getEarlyArrivalBufferMax());
        event.setShiftDate(shiftDate);
        return event;
    }
    
    /**
     * Creates a single-customer dropoff/return event.
     */
    private Event createDropoffEvent(List<Employee> passengers, ShiftDemand shift,
                                      LocalDate shiftDate, Location fromLocation,
                                      Location toLocation, GraphHopper hopper) {
        String eventId = String.format("dropoff-%d-%d-%s-p%d",
            shift.customer.id, shift.id, shiftDate, passengers.size());

        LocalDateTime shiftEndDateTime = LocalDateTime.of(shiftDate, shift.endTime);
        Instant shiftEndInstant = shiftEndDateTime.atZone(ZoneId.systemDefault()).toInstant();

        Instant minStartTime = shiftEndInstant;
        Instant maxEndTime = shiftEndInstant.plus(Duration.ofHours(1));

        Duration travelTime = fromLocation.getTravelTime(toLocation, hopper);
        long distance = fromLocation.getDistanceTo(toLocation, hopper);
        Duration boardingTime = BOARDING_TIME_PER_PASSENGER.multipliedBy(passengers.size());
        Duration totalDuration = travelTime.plus(boardingTime);

        Event event = new Event(eventId, fromLocation, toLocation,
            minStartTime, maxEndTime, totalDuration, distance,
            false, passengers, shift,
            shiftDate.getDayOfWeek().getValue() <= 5 ? "weekday" : "weekend",
            Duration.ZERO, Duration.ZERO);
        event.setShiftDate(shiftDate);
        return event;
    }

    /** Helper for FR-3 merging. */
    private static class CustomerStop {
        final Location location;
        final List<Employee> passengers;
        final Instant maxEndTime;
        final ShiftDemand shiftDemand;

        CustomerStop(Location location, List<Employee> passengers,
                     Instant maxEndTime, ShiftDemand shiftDemand) {
            this.location = location;
            this.passengers = passengers;
            this.maxEndTime = maxEndTime;
            this.shiftDemand = shiftDemand;
        }
    }
}
