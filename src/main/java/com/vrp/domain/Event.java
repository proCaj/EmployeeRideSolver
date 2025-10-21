package com.vrp.domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.*;
import com.vrp.entity.Employee;
import com.vrp.listener.ArrivalTimeUpdatingVariableListener;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

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
    private Employee assignedEmployee;
    private String dayType;
    private Duration earlyArrivalMin;
    private Duration earlyArrivalMax;
    
    @PlanningVariable(valueRangeProviderRefs = "driverRange")
    private Driver driver;
    
    @PlanningVariable(valueRangeProviderRefs = "standstillRange")
    private Standstill previousStandstill;
    
    @PreviousElementShadowVariable(sourceVariableName = "previousStandstill")
    private Event previousEvent;
    
    @NextElementShadowVariable(sourceVariableName = "previousStandstill")
    private Event nextEvent;
    
    @ShadowVariable(variableListenerClass = ArrivalTimeUpdatingVariableListener.class, 
                     sourceVariableName = "previousStandstill")
    private Instant arrivalTime;
    
    public Event() {
    }
    
    public Event(String id, Location fromLocation, Location toLocation,
                 Instant minStartTime, Instant maxEndTime, Duration duration, long distance,
                 boolean isPickup, Employee assignedEmployee, String dayType,
                 Duration earlyArrivalMin, Duration earlyArrivalMax) {
        this.id = id;
        this.fromLocation = fromLocation;
        this.toLocation = toLocation;
        this.minStartTime = minStartTime;
        this.maxEndTime = maxEndTime;
        this.duration = duration;
        this.distance = distance;
        this.isPickup = isPickup;
        this.assignedEmployee = assignedEmployee;
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
    
    public Employee getAssignedEmployee() {
        return assignedEmployee;
    }
    
    public void setAssignedEmployee(Employee assignedEmployee) {
        this.assignedEmployee = assignedEmployee;
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
    
    public void setDriver(Driver driver) {
        this.driver = driver;
    }
    
    public Standstill getPreviousStandstill() {
        return previousStandstill;
    }
    
    public void setPreviousStandstill(Standstill previousStandstill) {
        this.previousStandstill = previousStandstill;
    }
    
    public Event getPreviousEvent() {
        return previousEvent;
    }
    
    public void setPreviousEvent(Event previousEvent) {
        this.previousEvent = previousEvent;
    }
    
    public Event getNextEvent() {
        return nextEvent;
    }
    
    public void setNextEvent(Event nextEvent) {
        this.nextEvent = nextEvent;
    }
    
    public Instant getArrivalTime() {
        return arrivalTime;
    }
    
    public void setArrivalTime(Instant arrivalTime) {
        this.arrivalTime = arrivalTime;
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
               ", from=" + fromLocation.name() + ", to=" + toLocation.name() + "}";
    }
}
