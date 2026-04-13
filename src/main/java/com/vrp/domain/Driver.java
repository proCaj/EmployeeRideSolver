package com.vrp.domain;

import com.vrp.entity.Employee;

import java.time.Duration;
import java.util.Objects;

public class Driver implements Standstill {
    
    private String id;
    private Location homeLocation;
    private int maxCapacity = 6;
    private Duration maxConsecutiveHours = Duration.ofHours(4);
    private Duration minBreak = Duration.ofMinutes(30);
    private Duration maxBreak = Duration.ofHours(4);
    private Duration maxDailyHours = Duration.ofHours(10);
    private Duration maxWeeklyHours = Duration.ofHours(40);
    private Employee employee;
    
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
    
    public int getMaxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(int maxCapacity) {
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
        return "Driver{" + id + ", home=" + homeLocation.name() + "}";
    }
}
