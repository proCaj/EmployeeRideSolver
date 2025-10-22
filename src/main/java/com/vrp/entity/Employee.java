package com.vrp.entity;

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
