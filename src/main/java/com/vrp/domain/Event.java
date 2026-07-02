package com.vrp.domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.*;
import com.vrp.entity.Employee;
import com.vrp.entity.ShiftDemand;
import com.vrp.listener.ArrivalTimeUpdatingVariableListener;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * A planning entity representing a transport event (pickup or dropoff run).
 *
 * FR-3: Events support multi-stop routes. A pickup event visits locations in
 * order, picking up passengers and delivering them to customer sites.
 * The route is defined by the ordered list of {@link Stop}s.
 *
 * Example (FR-3, morning pickup):
 *   Stop 0: Hub         → board 4 Chep + 1 Sanner = 5 passengers
 *   Stop 1: Chep site   → 4 alight (for Chep)
 *   Stop 2: Sanner site → 1 alight (for Sanner)
 *
 * Legacy fields fromLocation/toLocation are retained for backward compatibility
 * with tests and the constraint provider. They map to:
 *   fromLocation = first stop's location
 *   toLocation   = last stop's location
 */
@PlanningEntity(difficultyComparatorClass = EventDifficultyComparator.class)
public class Event implements Standstill {
    
    private String id;

    // --- Legacy single-location fields (derived from stops for compatibility) ---
    private Location fromLocation;
    private Location toLocation;

    // --- FR-3: Multi-stop route ---
    private List<Stop> stops;

    private Instant minStartTime;
    private Instant maxEndTime;
    private Duration duration;     // total duration across all stops
    private long distance;         // total distance across all stops
    private boolean isPickup;
    private Event pairedEvent;

    /** All passengers across all stops (flat list for backward compat). */
    private List<Employee> passengers;

    private ShiftDemand shiftDemand;
    private String dayType;
    private Duration earlyArrivalMin;
    private Duration earlyArrivalMax;
    
    /**
     * The calendar date this event belongs to for daily working hour tracking.
     * For night shifts spanning midnight, shiftDate is the day the shift STARTED per ArbZG.
     */
    private LocalDate shiftDate;
    
    @PlanningVariable(graphType = PlanningVariableGraphType.CHAINED, 
                      valueRangeProviderRefs = "standstillRange")
    private Standstill previousStandstill;
    
    @AnchorShadowVariable(sourceVariableName = "previousStandstill")
    private Driver driver;
    
    @ShadowVariable(variableListenerClass = ArrivalTimeUpdatingVariableListener.class,
                     sourceVariableName = "previousStandstill")
    private Instant arrivalTime;

    @ShadowVariable(variableListenerClass = com.vrp.listener.PassengerCountUpdatingVariableListener.class,
                     sourceVariableName = "previousStandstill")
    private Integer cumulativePassengerCount;

    private int cachedPassengerDelta;
    private int cachedPeakPassengerCount;
    private Instant departureTime;

    public Event() {
        this.passengers = new ArrayList<>();
        this.stops = new ArrayList<>();
        this.cachedPassengerDelta = 0;
        this.cachedPeakPassengerCount = 0;
        this.departureTime = null;
    }
    
    /**
     * Legacy constructor for single-stop events (backward compat with tests).
     */
    public Event(String id, Location fromLocation, Location toLocation,
                 Instant minStartTime, Instant maxEndTime, Duration duration, long distance,
                 boolean isPickup, List<Employee> passengers, ShiftDemand shiftDemand, String dayType,
                 Duration earlyArrivalMin, Duration earlyArrivalMax) {
        this.id = id;
        this.fromLocation = fromLocation;
        this.toLocation = toLocation;
        this.minStartTime = minStartTime;
        this.maxEndTime = maxEndTime;
        this.duration = duration;
        this.distance = distance;
        this.isPickup = isPickup;
        this.passengers = passengers != null ? new ArrayList<>(passengers) : new ArrayList<>();
        this.shiftDemand = shiftDemand;
        this.dayType = dayType;
        this.earlyArrivalMin = earlyArrivalMin;
        this.earlyArrivalMax = earlyArrivalMax;
        this.stops = new ArrayList<>();
        this.cachedPassengerDelta = computePassengerDelta();
        this.cachedPeakPassengerCount = computePeakPassengerCount();
    }

    /**
     * FR-3 constructor for multi-stop events.
     * Sets fromLocation/toLocation from first/last stop.
     * Computes total distance and duration from stops.
     */
    public Event(String id, List<Stop> stops,
                 Instant minStartTime, Instant maxEndTime,
                 boolean isPickup, ShiftDemand shiftDemand, String dayType,
                 Duration earlyArrivalMin, Duration earlyArrivalMax) {
        this.id = id;
        this.stops = stops != null ? new ArrayList<>(stops) : new ArrayList<>();
        this.minStartTime = minStartTime;
        this.maxEndTime = maxEndTime;
        this.isPickup = isPickup;
        this.shiftDemand = shiftDemand;
        this.dayType = dayType;
        this.earlyArrivalMin = earlyArrivalMin;
        this.earlyArrivalMax = earlyArrivalMax;

        // Derive legacy fields from stops
        if (!this.stops.isEmpty()) {
            this.fromLocation = this.stops.get(0).getLocation();
            this.toLocation = this.stops.get(this.stops.size() - 1).getLocation();
        }

        // Compute total distance and duration from stops
        this.distance = computeTotalDistance();
        this.duration = computeTotalDuration();

        // Flatten all boarding passengers into the legacy passengers list
        this.passengers = this.stops.stream()
            .flatMap(s -> s.getBoardingPassengers().stream())
            .collect(Collectors.toCollection(ArrayList::new));

        this.cachedPassengerDelta = computePassengerDelta();
        this.cachedPeakPassengerCount = computePeakPassengerCount();
    }
    
    @Override
    public Location getLocation() {
        // The driver ends up at the LAST stop's location after completing this event
        return toLocation;
    }
    
    @Override
    public Driver getDriver() {
        return driver;
    }
    
    public Instant getDepartureTime() {
        return departureTime;
    }
    
    public Duration getWaitingTime() {
        if (arrivalTime == null || arrivalTime.isAfter(minStartTime)) {
            return Duration.ZERO;
        }
        return Duration.between(arrivalTime, minStartTime);
    }
    
    public int getPassengerCount() {
        return passengers != null ? passengers.size() : 0;
    }
    
    /**
     * Net passenger change across ALL stops in this event.
     * For FR-3 multi-stop events where all passengers are delivered within
     * the event, netDelta is 0 (they board and alight within the same event).
     * The peak concurrent load is tracked separately via getPeakPassengerCount().
     */
    public int getPassengerDelta() { return cachedPassengerDelta; }

    private int computePassengerDelta() {
        if (stops != null && !stops.isEmpty()) {
            return stops.stream()
                .mapToInt(Stop::getNetPassengerChange)
                .sum();
        }
        // Legacy fallback for single-from/to events
        return isPickup ? getPassengerCount() : -getPassengerCount();
    }
    
    public String getPassengerNames() {
        if (passengers == null || passengers.isEmpty()) {
            return "";
        }
        return passengers.stream()
            .map(e -> e.name)
            .collect(Collectors.joining(", "));
    }
    
    public Employee getAssignedEmployee() {
        return passengers != null && !passengers.isEmpty() ? passengers.get(0) : null;
    }
    
    public void setAssignedEmployee(Employee employee) {
        if (this.passengers == null) {
            this.passengers = new ArrayList<>();
        }
        this.passengers.clear();
        if (employee != null) {
            this.passengers.add(employee);
        }
    }

    /**
     * Returns the maximum concurrent passenger count during this event.
     * For multi-stop events, this is the peak load on the vehicle.
     * Used by the capacity constraint.
     */
    public int getPeakPassengerCount() { return cachedPeakPassengerCount; }

    private int computePeakPassengerCount() {
        if (stops == null || stops.isEmpty()) {
            return getPassengerCount();
        }
        int current = 0;
        int peak = 0;
        for (Stop stop : stops) {
            current += stop.getNetPassengerChange();
            if (current > peak) {
                peak = current;
            }
        }
        return peak;
    }

    // --- Stop-based route computations ---

    private long computeTotalDistance() {
        if (stops == null || stops.isEmpty()) return distance;
        return stops.stream()
            .mapToLong(Stop::getDistanceFromPrevious)
            .sum();
    }

    private Duration computeTotalDuration() {
        if (stops == null || stops.isEmpty()) return duration;
        Duration total = Duration.ZERO;
        for (Stop stop : stops) {
            if (stop.getTravelTimeFromPrevious() != null) {
                total = total.plus(stop.getTravelTimeFromPrevious());
            }
            if (stop.getBoardingDuration() != null) {
                total = total.plus(stop.getBoardingDuration());
            }
            // Alighting time: 1 min per alighting passenger
            total = total.plus(Duration.ofMinutes(stop.getAlightingCount()));
        }
        return total;
    }

    // --- Getters and Setters ---

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Location getFromLocation() { return fromLocation; }
    public void setFromLocation(Location fromLocation) { this.fromLocation = fromLocation; }

    public Location getToLocation() { return toLocation; }
    public void setToLocation(Location toLocation) { this.toLocation = toLocation; }

    public List<Stop> getStops() { return stops; }
    public void setStops(List<Stop> stops) {
        this.stops = stops != null ? new ArrayList<>(stops) : new ArrayList<>();
        if (!this.stops.isEmpty()) {
            this.fromLocation = this.stops.get(0).getLocation();
            this.toLocation = this.stops.get(this.stops.size() - 1).getLocation();
        }
        this.cachedPassengerDelta = computePassengerDelta();
        this.cachedPeakPassengerCount = computePeakPassengerCount();
    }

    public Instant getMinStartTime() { return minStartTime; }
    public void setMinStartTime(Instant minStartTime) { this.minStartTime = minStartTime; }

    public Instant getMaxEndTime() { return maxEndTime; }
    public void setMaxEndTime(Instant maxEndTime) { this.maxEndTime = maxEndTime; }

    public Duration getDuration() { return duration; }
    public void setDuration(Duration duration) { this.duration = duration; }

    public long getDistance() { return distance; }
    public void setDistance(long distance) { this.distance = distance; }

    public boolean isPickup() { return isPickup; }
    public void setPickup(boolean pickup) { isPickup = pickup; }

    public Event getPairedEvent() { return pairedEvent; }
    public void setPairedEvent(Event pairedEvent) { this.pairedEvent = pairedEvent; }

    public List<Employee> getPassengers() { return passengers; }
    public void setPassengers(List<Employee> passengers) {
        this.passengers = passengers != null ? new ArrayList<>(passengers) : new ArrayList<>();
    }

    public ShiftDemand getShiftDemand() { return shiftDemand; }
    public void setShiftDemand(ShiftDemand shiftDemand) { this.shiftDemand = shiftDemand; }

    public String getDayType() { return dayType; }
    public void setDayType(String dayType) { this.dayType = dayType; }

    public Duration getEarlyArrivalMin() { return earlyArrivalMin; }
    public void setEarlyArrivalMin(Duration earlyArrivalMin) { this.earlyArrivalMin = earlyArrivalMin; }

    public Duration getEarlyArrivalMax() { return earlyArrivalMax; }
    public void setEarlyArrivalMax(Duration earlyArrivalMax) { this.earlyArrivalMax = earlyArrivalMax; }

    public LocalDate getShiftDate() { return shiftDate; }
    public void setShiftDate(LocalDate shiftDate) { this.shiftDate = shiftDate; }

    public Standstill getPreviousStandstill() { return previousStandstill; }
    public void setPreviousStandstill(Standstill previousStandstill) { this.previousStandstill = previousStandstill; }

    public void setDriver(Driver driver) { this.driver = driver; }

    public Instant getArrivalTime() { return arrivalTime; }
    public void setArrivalTime(Instant arrivalTime) {
        this.arrivalTime = arrivalTime;
        if (arrivalTime == null || duration == null) {
            this.departureTime = null;
        } else if (minStartTime != null) {
            Instant effectiveStart = arrivalTime.isBefore(minStartTime) ? minStartTime : arrivalTime;
            this.departureTime = effectiveStart.plus(duration);
        } else {
            // No minStartTime constraint — depart immediately after arrival + duration
            this.departureTime = arrivalTime.plus(duration);
        }
    }

    public Integer getCumulativePassengerCount() { return cumulativePassengerCount; }
    public void setCumulativePassengerCount(Integer cumulativePassengerCount) { this.cumulativePassengerCount = cumulativePassengerCount; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Event event = (Event) o;
        return Objects.equals(id, event.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "Event{" + id + ", " + (isPickup ? "Pickup" : "Dropoff") + 
               ", stops=" + (stops != null ? stops.size() : 0) +
               ", passengers=" + getPassengerCount() + "}";
    }
}
