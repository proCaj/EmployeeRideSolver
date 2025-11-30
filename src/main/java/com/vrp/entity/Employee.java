package com.vrp.entity;

import com.vrp.domain.Location;
import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import org.hibernate.proxy.HibernateProxy;
import java.util.HashSet;
import java.util.Objects;
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
    
    @Enumerated(EnumType.STRING)
    @Column(name = "employee_type", nullable = false)
    public EmployeeType employeeType = EmployeeType.SITE_EMPLOYEE;

    @Column(name = "home_latitude")
    public Double homeLatitude;

    @Column(name = "home_longitude")
    public Double homeLongitude;

    @Column(name = "pickup_latitude")
    public Double pickupLatitude;

    @Column(name = "pickup_longitude")
    public Double pickupLongitude;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
        name = "employee_shift_assignments",
        joinColumns = @JoinColumn(name = "employee_id"),
        inverseJoinColumns = @JoinColumn(name = "shift_demand_id")
    )
    public Set<ShiftDemand> assignments = new HashSet<>();
    
    public Employee() {
    }
    
    public Employee(String name, String email, String phoneNumber, EmployeeType employeeType) {
        this.name = name;
        this.email = email;
        this.phoneNumber = phoneNumber;
        this.employeeType = employeeType;
    }
    
    public void assignToShift(ShiftDemand shift) {
        String conflictMessage = checkShiftConflict(shift);
        if (conflictMessage != null) {
            throw new IllegalStateException(conflictMessage);
        }
        assignments.add(shift);
        shift.assignedEmployees.add(this);
    }
    
    public String checkShiftConflict(ShiftDemand newShift) {
        for (ShiftDemand existingShift : assignments) {
            if (existingShift.dayOfWeek == newShift.dayOfWeek) {
                if (shiftsOverlap(existingShift, newShift)) {
                    return "Employee " + name + " is already assigned to a shift at " + 
                           existingShift.customer.name + " (" + existingShift.startTime + 
                           " - " + existingShift.endTime + ") on " + existingShift.dayOfWeek + 
                           " which overlaps with this shift";
                }
            }
        }
        return null;
    }
    
    private boolean shiftsOverlap(ShiftDemand shift1, ShiftDemand shift2) {
        return shift1.startTime.isBefore(shift2.endTime) && 
               shift2.startTime.isBefore(shift1.endTime);
    }
    
    public void unassignFromShift(ShiftDemand shift) {
        assignments.remove(shift);
        shift.assignedEmployees.remove(this);
    }

    public Location getHomeLocation(Location defaultHub) {
        if (homeLatitude != null && homeLongitude != null) {
            return new Location("Home-" + id, homeLatitude, homeLongitude);
        }
        return defaultHub;
    }

    public Location getPickupLocation(Location defaultHub) {
        if (pickupLatitude != null && pickupLongitude != null) {
            return new Location("Pickup-" + id, pickupLatitude, pickupLongitude);
        }
        return defaultHub;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        Class<?> oEffectiveClass = o instanceof HibernateProxy 
            ? ((HibernateProxy) o).getHibernateLazyInitializer().getPersistentClass() 
            : o.getClass();
        Class<?> thisEffectiveClass = this instanceof HibernateProxy 
            ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass() 
            : this.getClass();
        if (thisEffectiveClass != oEffectiveClass) return false;
        Employee employee = (Employee) o;
        return id != null && Objects.equals(id, employee.id);
    }
    
    @Override
    public int hashCode() {
        return this instanceof HibernateProxy 
            ? ((HibernateProxy) this).getHibernateLazyInitializer().getPersistentClass().hashCode() 
            : getClass().hashCode();
    }
}
