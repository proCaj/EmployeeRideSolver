package com.vrp.entity;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "customers")
public class Customer extends PanacheEntity {
    
    @Column(nullable = false)
    public String name;
    
    @Column(nullable = false)
    public String address;
    
    @Column(nullable = false)
    public double latitude;
    
    @Column(nullable = false)
    public double longitude;
    
    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    public List<ShiftDemand> shiftDemands = new ArrayList<>();
    
    public Customer() {
    }
    
    public Customer(String name, String address, double latitude, double longitude) {
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
    }
    
    public void addShiftDemand(ShiftDemand shift) {
        shiftDemands.add(shift);
        shift.customer = this;
    }
    
    public void removeShiftDemand(ShiftDemand shift) {
        shiftDemands.remove(shift);
        shift.customer = null;
    }
}
