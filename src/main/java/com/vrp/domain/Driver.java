package com.vrp.domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.*;
import com.vrp.entity.Employee;
import com.vrp.listener.*;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@PlanningEntity
public class Driver implements Standstill {
    
    private String id;
    private Location homeLocation;
    private long maxCapacity = 6;
    private Duration maxConsecutiveHours = Duration.ofHours(4);
    private Duration minBreak = Duration.ofMinutes(30);
    private Duration maxBreak = Duration.ofHours(4);
    private Duration maxDailyHours = Duration.ofHours(10);
    private Duration maxWeeklyHours = Duration.ofHours(40);
    private Employee employee;
    
    @InverseRelationShadowVariable(sourceVariableName = "previousStandstill")
    private List<Event> assignedEvents = new ArrayList<>();
    
    @ShadowVariable(variableListenerClass = RouteSplitterListener.class, 
                     sourceVariableName = "assignedEvents")
    private List<Route> routes = new ArrayList<>();
    
    @ShadowVariable(variableListenerClass = HoursTrackingListener.class, 
                     sourceVariableName = "routes")
    private Duration totalDailyHours = Duration.ZERO;
    
    @ShadowVariable(variableListenerClass = HoursTrackingListener.class, 
                     sourceVariableName = "routes")
    private Duration totalWeeklyHours = Duration.ZERO;
    
    @ShadowVariable(variableListenerClass = TotalMetricsListener.class, 
                     sourceVariableName = "routes")
    private Long totalDistanceMeters;
    
    @ShadowVariable(variableListenerClass = TotalMetricsListener.class, 
                     sourceVariableName = "routes")
    private Duration totalTravelTime = Duration.ZERO;
    
    @ShadowVariable(variableListenerClass = TotalMetricsListener.class, 
                     sourceVariableName = "routes")
    private Duration consecutiveWorkingHours = Duration.ZERO;
    
    @ShadowVariable(variableListenerClass = TotalMetricsListener.class, 
                     sourceVariableName = "routes")
    private Duration totalWaitingTime = Duration.ZERO;
    
    public Driver() {
    }
    
    public Driver(String id, Location homeLocation) {
        this.id = id;
        this.homeLocation = homeLocation;
    }
    
    public Driver(String id, Location homeLocation, Employee employee) {
        this.id = id;
        this.homeLocation = homeLocation;
        this.employee = employee;
    }
    
    @Override
    public Location getLocation() {
        return homeLocation;
    }
    
    @Override
    public Driver getDriver() {
        return this;
    }
    
    public int getTotalLoad() {
        if (assignedEvents == null || assignedEvents.isEmpty()) {
            return 0;
        }
        int maxLoad = 0;
        int currentLoad = 0;
        for (Event event : assignedEvents) {
            if (event.isPickup()) {
                currentLoad++;
            } else {
                currentLoad--;
            }
            maxLoad = Math.max(maxLoad, currentLoad);
        }
        return maxLoad;
    }
    
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public Location getHomeLocation() {
        return homeLocation;
    }
    
    public void setHomeLocation(Location homeLocation) {
        this.homeLocation = homeLocation;
    }
    
    public long getMaxCapacity() {
        return maxCapacity;
    }
    
    public void setMaxCapacity(long maxCapacity) {
        this.maxCapacity = maxCapacity;
    }
    
    public Duration getMaxConsecutiveHours() {
        return maxConsecutiveHours;
    }
    
    public void setMaxConsecutiveHours(Duration maxConsecutiveHours) {
        this.maxConsecutiveHours = maxConsecutiveHours;
    }
    
    public Duration getMinBreak() {
        return minBreak;
    }
    
    public void setMinBreak(Duration minBreak) {
        this.minBreak = minBreak;
    }
    
    public Duration getMaxBreak() {
        return maxBreak;
    }
    
    public void setMaxBreak(Duration maxBreak) {
        this.maxBreak = maxBreak;
    }
    
    public Duration getMaxDailyHours() {
        return maxDailyHours;
    }
    
    public void setMaxDailyHours(Duration maxDailyHours) {
        this.maxDailyHours = maxDailyHours;
    }
    
    public Duration getMaxWeeklyHours() {
        return maxWeeklyHours;
    }
    
    public void setMaxWeeklyHours(Duration maxWeeklyHours) {
        this.maxWeeklyHours = maxWeeklyHours;
    }
    
    public Employee getEmployee() {
        return employee;
    }
    
    public void setEmployee(Employee employee) {
        this.employee = employee;
    }
    
    public List<Event> getEvents() {
        return assignedEvents;
    }
    
    public List<Event> getAssignedEvents() {
        return assignedEvents;
    }
    
    public void setAssignedEvents(List<Event> assignedEvents) {
        this.assignedEvents = assignedEvents;
    }
    
    public List<Route> getRoutes() {
        return routes;
    }
    
    public void setRoutes(List<Route> routes) {
        this.routes = routes;
    }
    
    public Duration getTotalDailyHours() {
        return totalDailyHours;
    }
    
    public void setTotalDailyHours(Duration totalDailyHours) {
        this.totalDailyHours = totalDailyHours;
    }
    
    public Duration getTotalWeeklyHours() {
        return totalWeeklyHours;
    }
    
    public void setTotalWeeklyHours(Duration totalWeeklyHours) {
        this.totalWeeklyHours = totalWeeklyHours;
    }
    
    public Long getTotalDistanceMeters() {
        return totalDistanceMeters != null ? totalDistanceMeters : 0L;
    }
    
    public void setTotalDistanceMeters(Long totalDistanceMeters) {
        this.totalDistanceMeters = totalDistanceMeters;
    }
    
    public Duration getTotalTravelTime() {
        return totalTravelTime;
    }
    
    public void setTotalTravelTime(Duration totalTravelTime) {
        this.totalTravelTime = totalTravelTime;
    }
    
    public Duration getConsecutiveWorkingHours() {
        return consecutiveWorkingHours;
    }
    
    public void setConsecutiveWorkingHours(Duration consecutiveWorkingHours) {
        this.consecutiveWorkingHours = consecutiveWorkingHours;
    }
    
    public Duration getTotalWaitingTime() {
        return totalWaitingTime;
    }
    
    public void setTotalWaitingTime(Duration totalWaitingTime) {
        this.totalWaitingTime = totalWaitingTime;
    }
    
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Driver driver = (Driver) o;
        return Objects.equals(id, driver.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return "Driver{" + id + ", home=" + homeLocation.name() + 
               ", events=" + (assignedEvents != null ? assignedEvents.size() : 0) + "}";
    }
}
