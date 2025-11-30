package com.vrp.domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.*;
import com.vrp.entity.Employee;
import com.vrp.entity.ShiftDemand;
import com.vrp.listener.ArrivalTimeUpdatingVariableListener;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@PlanningEntity
public class Event implements Standstill {
    
    private String id;
    private Location fromLocation;
    private Location toLocation;
    private Instant minStartTime;
    private Instant maxEndTime;
    private Duration duration;
    private long distance;
    private boolean isPickup;
    private Event pairedEvent;
    private List<Employee> passengers;
    private ShiftDemand shiftDemand;
    private String dayType;
    private Duration earlyArrivalMin;
    private Duration earlyArrivalMax;
    
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

    public Event() {
        this.passengers = new ArrayList<>();
    }
    
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
    }
    
    @Override
    public Location getLocation() {
        return toLocation;
    }
    
    @Override
    public Driver getDriver() {
        return driver;
    }
    
    public Instant getDepartureTime() {
        if (arrivalTime == null) {
            return null;
        }
        Instant effectiveStart = arrivalTime.isBefore(minStartTime) ? minStartTime : arrivalTime;
        return effectiveStart.plus(duration);
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
    
    public int getPassengerDelta() {
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
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public Location getFromLocation() {
        return fromLocation;
    }
    
    public void setFromLocation(Location fromLocation) {
        this.fromLocation = fromLocation;
    }
    
    public Location getToLocation() {
        return toLocation;
    }
    
    public void setToLocation(Location toLocation) {
        this.toLocation = toLocation;
    }
    
    public Instant getMinStartTime() {
        return minStartTime;
    }
    
    public void setMinStartTime(Instant minStartTime) {
        this.minStartTime = minStartTime;
    }
    
    public Instant getMaxEndTime() {
        return maxEndTime;
    }
    
    public void setMaxEndTime(Instant maxEndTime) {
        this.maxEndTime = maxEndTime;
    }
    
    public Duration getDuration() {
        return duration;
    }
    
    public void setDuration(Duration duration) {
        this.duration = duration;
    }
    
    public long getDistance() {
        return distance;
    }
    
    public void setDistance(long distance) {
        this.distance = distance;
    }
    
    public boolean isPickup() {
        return isPickup;
    }
    
    public void setPickup(boolean pickup) {
        isPickup = pickup;
    }
    
    public Event getPairedEvent() {
        return pairedEvent;
    }
    
    public void setPairedEvent(Event pairedEvent) {
        this.pairedEvent = pairedEvent;
    }
    
    public List<Employee> getPassengers() {
        return passengers;
    }
    
    public void setPassengers(List<Employee> passengers) {
        this.passengers = passengers != null ? new ArrayList<>(passengers) : new ArrayList<>();
    }
    
    public ShiftDemand getShiftDemand() {
        return shiftDemand;
    }
    
    public void setShiftDemand(ShiftDemand shiftDemand) {
        this.shiftDemand = shiftDemand;
    }
    
    public String getDayType() {
        return dayType;
    }
    
    public void setDayType(String dayType) {
        this.dayType = dayType;
    }
    
    public Duration getEarlyArrivalMin() {
        return earlyArrivalMin;
    }
    
    public void setEarlyArrivalMin(Duration earlyArrivalMin) {
        this.earlyArrivalMin = earlyArrivalMin;
    }
    
    public Duration getEarlyArrivalMax() {
        return earlyArrivalMax;
    }
    
    public void setEarlyArrivalMax(Duration earlyArrivalMax) {
        this.earlyArrivalMax = earlyArrivalMax;
    }
    
    public Standstill getPreviousStandstill() {
        return previousStandstill;
    }
    
    public void setPreviousStandstill(Standstill previousStandstill) {
        this.previousStandstill = previousStandstill;
    }
    
    public void setDriver(Driver driver) {
        this.driver = driver;
    }
    
    public Instant getArrivalTime() {
        return arrivalTime;
    }
    
    public void setArrivalTime(Instant arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public Integer getCumulativePassengerCount() {
        return cumulativePassengerCount;
    }

    public void setCumulativePassengerCount(Integer cumulativePassengerCount) {
        this.cumulativePassengerCount = cumulativePassengerCount;
    }

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
               ", from=" + fromLocation.name() + ", to=" + toLocation.name() + 
               ", passengers=" + getPassengerCount() + "}";
    }
}
