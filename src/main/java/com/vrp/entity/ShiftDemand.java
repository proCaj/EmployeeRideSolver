package com.vrp.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "shift_demands")
public class ShiftDemand extends PanacheEntity {
    
    @ManyToOne(optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    public Customer customer;
    
    @Column(name = "shift_type", nullable = false)
    public String shiftType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "day_of_week", nullable = false)
    public DayOfWeek dayOfWeek;
    
    @Column(name = "start_time", nullable = false)
    public LocalTime startTime;
    
    @Column(name = "end_time", nullable = false)
    public LocalTime endTime;
    
    @Column(name = "required_employees", nullable = false)
    public int requiredEmployees;
    
    @Column(name = "early_arrival_buffer_min", nullable = false)
    public long earlyArrivalBufferMinMinutes = 30;
    
    @Column(name = "early_arrival_buffer_max", nullable = false)
    public long earlyArrivalBufferMaxMinutes = 45;
    
    @Column(name = "requires_return_trip", nullable = false)
    public boolean requiresReturnTrip = true;
    
    @Column(nullable = false)
    public boolean active = true;
    
    @ManyToMany(mappedBy = "assignments", fetch = FetchType.EAGER)
    public Set<Employee> assignedEmployees = new HashSet<>();
    
    public ShiftDemand() {
    }
    
    public ShiftDemand(Customer customer, String shiftType, DayOfWeek dayOfWeek, 
                       LocalTime startTime, LocalTime endTime, int requiredEmployees) {
        this.customer = customer;
        this.shiftType = shiftType;
        this.dayOfWeek = dayOfWeek;
        this.startTime = startTime;
        this.endTime = endTime;
        this.requiredEmployees = requiredEmployees;
    }
    
    public Duration getEarlyArrivalBufferMin() {
        return Duration.ofMinutes(earlyArrivalBufferMinMinutes);
    }
    
    public Duration getEarlyArrivalBufferMax() {
        return Duration.ofMinutes(earlyArrivalBufferMaxMinutes);
    }
    
    public void setEarlyArrivalBufferMin(Duration duration) {
        this.earlyArrivalBufferMinMinutes = duration.toMinutes();
    }
    
    public void setEarlyArrivalBufferMax(Duration duration) {
        this.earlyArrivalBufferMaxMinutes = duration.toMinutes();
    }
}
