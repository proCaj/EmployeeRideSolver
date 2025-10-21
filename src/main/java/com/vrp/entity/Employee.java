package com.vrp.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "employees")
public class Employee extends PanacheEntity {
    
    @Column(nullable = false)
    public String name;
    
    @Column
    public String email;
    
    @Column(name = "phone_number")
    public String phoneNumber;
    
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "employee_skills", joinColumns = @JoinColumn(name = "employee_id"))
    @Column(name = "skill")
    public Set<String> skills = new HashSet<>();
    
    @Column(nullable = false)
    public boolean active = true;
    
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "employee_shift_assignments",
        joinColumns = @JoinColumn(name = "employee_id"),
        inverseJoinColumns = @JoinColumn(name = "shift_demand_id")
    )
    public Set<ShiftDemand> assignments = new HashSet<>();
    
    public Employee() {
    }
    
    public Employee(String name, String email, String phoneNumber) {
        this.name = name;
        this.email = email;
        this.phoneNumber = phoneNumber;
    }
    
    public void assignToShift(ShiftDemand shift) {
        assignments.add(shift);
        shift.assignedEmployees.add(this);
    }
    
    public void unassignFromShift(ShiftDemand shift) {
        assignments.remove(shift);
        shift.assignedEmployees.remove(this);
    }
}
