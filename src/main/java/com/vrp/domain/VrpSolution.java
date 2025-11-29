package com.vrp.domain;

import ai.timefold.solver.core.api.domain.solution.*;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import com.vrp.entity.Customer;
import com.vrp.entity.Employee;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@PlanningSolution
public class VrpSolution {
    
    @ProblemFactCollectionProperty
    private List<Location> locations;
    
    @ProblemFactCollectionProperty
    private List<Customer> customers;
    
    @ProblemFactCollectionProperty
    private List<Employee> employees;
    
    @ProblemFactCollectionProperty
    @ValueRangeProvider(id = "driverRange")
    private List<Driver> drivers;
    
    @PlanningEntityCollectionProperty
    private List<Event> events;
    
    @ProblemFactProperty
    private LocalDate planningStartDate;
    
    @PlanningScore
    private HardMediumSoftLongScore score;
    
    public VrpSolution() {
        this.locations = new ArrayList<>();
        this.customers = new ArrayList<>();
        this.employees = new ArrayList<>();
        this.drivers = new ArrayList<>();
        this.events = new ArrayList<>();
    }
    
    public VrpSolution(List<Location> locations, List<Customer> customers, List<Employee> employees,
                       List<Driver> drivers, List<Event> events, LocalDate planningStartDate) {
        this.locations = locations;
        this.customers = customers;
        this.employees = employees;
        this.drivers = drivers;
        this.events = events;
        this.planningStartDate = planningStartDate;
    }
    
    @ValueRangeProvider(id = "eventRange")
    public List<Event> getEventRange() {
        return events;
    }
    
    @ValueRangeProvider(id = "standstillRange")
    public List<Standstill> getStandstillRange() {
        return Stream.concat(
            drivers.stream(),
            events.stream()
        ).collect(Collectors.toList());
    }
    
    public List<Location> getLocations() {
        return locations;
    }
    
    public void setLocations(List<Location> locations) {
        this.locations = locations;
    }
    
    public List<Customer> getCustomers() {
        return customers;
    }
    
    public void setCustomers(List<Customer> customers) {
        this.customers = customers;
    }
    
    public List<Employee> getEmployees() {
        return employees;
    }
    
    public void setEmployees(List<Employee> employees) {
        this.employees = employees;
    }
    
    public List<Driver> getDrivers() {
        return drivers;
    }
    
    public void setDrivers(List<Driver> drivers) {
        this.drivers = drivers;
    }
    
    public List<Event> getEvents() {
        return events;
    }
    
    public void setEvents(List<Event> events) {
        this.events = events;
    }
    
    public LocalDate getPlanningStartDate() {
        return planningStartDate;
    }
    
    public void setPlanningStartDate(LocalDate planningStartDate) {
        this.planningStartDate = planningStartDate;
    }
    
    public HardMediumSoftLongScore getScore() {
        return score;
    }
    
    public void setScore(HardMediumSoftLongScore score) {
        this.score = score;
    }
}
