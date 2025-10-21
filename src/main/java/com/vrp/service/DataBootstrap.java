package com.vrp.service;

import com.vrp.entity.Customer;
import com.vrp.entity.Employee;
import com.vrp.entity.ShiftDemand;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.DayOfWeek;
import java.time.LocalTime;

@ApplicationScoped
public class DataBootstrap {
    
    private static final Logger LOG = Logger.getLogger(DataBootstrap.class);
    
    @Transactional
    public void loadDemoData(@Observes StartupEvent event) {
        if (Customer.count() > 0) {
            LOG.info("Demo data already loaded, skipping bootstrap");
            return;
        }
        
        LOG.info("Loading demo data...");
        
        Customer otto = new Customer(
            "Otto Cosmetic GmbH",
            "Werner-von-Siemens-Straße 3, 68649 Groß-Rohrheim",
            49.58, 8.47
        );
        otto.persist();
        
        Customer deichmann = new Customer(
            "Deichmann Logistiklager",
            "Carl-Benz-Straße 10, 67590 Monsheim",
            49.62, 8.35
        );
        deichmann.persist();
        
        Customer importhaus = new Customer(
            "Importhaus Wilms",
            "Robert-Bosch-Straße 33, 55232 Alzey",
            49.65, 8.36
        );
        importhaus.persist();
        
        Customer chep = new Customer(
            "CHEP Servicecenter Biebesheim",
            "Am Winkelgraben 13, 64584 Biebesheim am Rhein",
            49.78, 8.46
        );
        chep.persist();
        
        Customer agrarhandel = new Customer(
            "Agrarhandel Kunz",
            "Außerhalb 66, 67575 Eich",
            49.72, 8.43
        );
        agrarhandel.persist();
        
        Employee achim = new Employee("Achim Müller", "achim@example.com", "+49 151 1234 5601");
        achim.persist();
        
        Employee bernd = new Employee("Bernd Schmidt", "bernd@example.com", "+49 151 1234 5602");
        bernd.persist();
        
        Employee christian = new Employee("Christian Weber", "christian@example.com", "+49 151 1234 5603");
        christian.persist();
        
        Employee dirk = new Employee("Dirk Fischer", "dirk@example.com", "+49 151 1234 5604");
        dirk.persist();
        
        Employee emre = new Employee("Emre Yilmaz", "emre@example.com", "+49 151 1234 5605");
        emre.persist();
        
        Employee frank = new Employee("Frank Becker", "frank@example.com", "+49 151 1234 5606");
        frank.persist();
        
        for (DayOfWeek day : new DayOfWeek[]{DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY}) {
            ShiftDemand ottoFrueh = new ShiftDemand(otto, "Frühschicht", day, LocalTime.of(6, 0), LocalTime.of(14, 0), 2);
            ottoFrueh.persist();
            achim.assignToShift(ottoFrueh);
            bernd.assignToShift(ottoFrueh);
            
            ShiftDemand deichmannFrueh = new ShiftDemand(deichmann, "Frühschicht", day, LocalTime.of(7, 0), LocalTime.of(15, 0), 2);
            deichmannFrueh.persist();
            christian.assignToShift(deichmannFrueh);
            dirk.assignToShift(deichmannFrueh);
            
            ShiftDemand importhausFrueh = new ShiftDemand(importhaus, "Frühschicht", day, LocalTime.of(7, 0), LocalTime.of(16, 0), 1);
            importhausFrueh.persist();
            emre.assignToShift(importhausFrueh);
            
            ShiftDemand chepFrueh = new ShiftDemand(chep, "Frühschicht", day, LocalTime.of(5, 30), LocalTime.of(14, 0), 2);
            chepFrueh.persist();
            frank.assignToShift(chepFrueh);
            achim.assignToShift(chepFrueh);
        }
        
        LOG.info("Demo data loaded successfully: 5 customers, 6 employees, multiple shifts");
    }
}
