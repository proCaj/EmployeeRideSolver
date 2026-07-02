package com.vrp.service;

import com.vrp.entity.Customer;
import com.vrp.entity.Employee;
import com.vrp.entity.EmployeeType;
import com.vrp.entity.ShiftDemand;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.transaction.Transactional;
import org.jboss.logging.Logger;

import java.time.DayOfWeek;
import java.time.LocalTime;

/**
 * Bootstrap data matching the manual KW 51 route plan.
 *
 * Customers: Chep, Sanner, Orion, Barbe, Beneo
 * Pickup locations:
 * - City-Fahrschule (main hub): Siegfriedstraße 25, 67547 Worms
 * - Tankstelle Pfeddersheim: Odenwaldstraße 7, 67551 Worms
 */
@ApplicationScoped
public class DataBootstrap {

    private static final Logger LOG = Logger.getLogger(DataBootstrap.class);

    // Hub coordinates (City-Fahrschule, Siegfriedstraße 25, 67547 Worms)
    private static final double HUB_LAT = 49.6295;
    private static final double HUB_LON = 8.3640;

    // Alternative pickup location (Tankstelle Pfeddersheim, Odenwaldstraße 7, 67551 Worms)
    private static final double TANKSTELLE_LAT = 49.6180;
    private static final double TANKSTELLE_LON = 8.2970;

    @Transactional
    public void loadDemoData(@Observes StartupEvent event) {
        if (Customer.count() > 0) {
            LOG.info("Demo data already loaded, skipping bootstrap");
            return;
        }

        LOG.info("Loading KW 51 demo data...");

        // ==================== CUSTOMERS ====================

        // Chep Deutschland GmbH - Biebesheim
        // Early: 05:30-14:00 (Fri 05:30-13:00), Late: 14:00-22:00 (Fri 13:00-20:30)
        Customer chep = new Customer(
            "Chep Deutschland GmbH",
            "Am Winkelgraben 13, 64584 Biebesheim",
            49.7784, 8.4625
        );
        chep.persist();

        // Sanner GmbH - Bensheim
        // Early: 06:00-14:00, Late: 14:00-22:00, Night: 22:00-06:00
        Customer sanner = new Customer(
            "Sanner GmbH",
            "Bertha-Benz-Straße 5, 64625 Bensheim",
            49.6800, 8.6250
        );
        sanner.persist();

        // Orion Bausysteme GmbH - Biebesheim
        // Day: 06:30-16:00 (Fri 06:30-12:45)
        Customer orion = new Customer(
            "Orion Bausysteme GmbH",
            "Waldstr. 2, 64584 Biebesheim",
            49.7750, 8.4580
        );
        orion.persist();

        // Hans W. Barbe Chemische Erzeugnisse GmbH - Worms area
        // Early: 06:00-14:00, Late: 14:00-22:00, Night: 22:00-06:00
        Customer barbe = new Customer(
            "Hans W. Barbe Chemische Erzeugnisse GmbH",
            "Justus-von-Liebig-Str. 17, 67549 Worms",
            49.6350, 8.3480
        );
        barbe.persist();

        // Beneo-Palatinit GmbH - Obrigheim (Offstein area)
        // Early: 05:35-13:35, Late: 13:35-21:35, Night: 21:35-05:35
        // Sunday: 12h shifts (05:35-17:35 or 17:35-05:35)
        Customer beneo = new Customer(
            "Beneo-Palatinit GmbH",
            "Wormser Straße 11, 67283 Obrigheim",
            49.4700, 8.2100
        );
        beneo.persist();

        // ==================== DRIVERS ====================

        Employee driver1 = new Employee("Driver 1", "driver1@example.com", "+49 151 1234 5607", EmployeeType.DRIVER);
        driver1.homeLatitude = HUB_LAT;
        driver1.homeLongitude = HUB_LON;
        driver1.persist();

        Employee driver2 = new Employee("Driver 2", "driver2@example.com", "+49 151 1234 5608", EmployeeType.DRIVER);
        driver2.homeLatitude = HUB_LAT;
        driver2.homeLongitude = HUB_LON;
        driver2.persist();

        Employee driver3 = new Employee("Driver 3", "driver3@example.com", "+49 151 1234 5609", EmployeeType.DRIVER);
        driver3.homeLatitude = HUB_LAT;
        driver3.homeLongitude = HUB_LON;
        driver3.persist();

        // ==================== SITE EMPLOYEES ====================

        // --- CHEP employees (5 total) ---
        // 1 from Tankstelle Pfeddersheim, 4 from City-Fahrschule

        Employee person1 = new Employee("Person 1", "person1@example.com", "+49 151 1234 5001", EmployeeType.SITE_EMPLOYEE);
        person1.pickupLatitude = TANKSTELLE_LAT;  // Pfeddersheim pickup
        person1.pickupLongitude = TANKSTELLE_LON;
        person1.persist();

        Employee person2 = new Employee("Person 2", "person2@example.com", "+49 151 1234 5002", EmployeeType.SITE_EMPLOYEE);
        person2.pickupLatitude = HUB_LAT;  // City-Fahrschule
        person2.pickupLongitude = HUB_LON;
        person2.persist();

        Employee person3 = new Employee("Person 3", "person3@example.com", "+49 151 1234 5003", EmployeeType.SITE_EMPLOYEE);
        person3.pickupLatitude = HUB_LAT;
        person3.pickupLongitude = HUB_LON;
        person3.persist();

        Employee person4 = new Employee("Person 4", "person4@example.com", "+49 151 1234 5004", EmployeeType.SITE_EMPLOYEE);
        person4.pickupLatitude = HUB_LAT;
        person4.pickupLongitude = HUB_LON;
        person4.persist();

        Employee person5 = new Employee("Person 5", "person5@example.com", "+49 151 1234 5005", EmployeeType.SITE_EMPLOYEE);
        person5.pickupLatitude = HUB_LAT;
        person5.pickupLongitude = HUB_LON;
        person5.persist();

        // --- SANNER employee (1) ---

        Employee person6 = new Employee("Person 6", "person6@example.com", "+49 151 1234 5006", EmployeeType.SITE_EMPLOYEE);
        person6.pickupLatitude = HUB_LAT;
        person6.pickupLongitude = HUB_LON;
        person6.persist();

        // --- ORION employees (2) ---

        Employee person7 = new Employee("Person 7", "person7@example.com", "+49 151 1234 5007", EmployeeType.SITE_EMPLOYEE);
        person7.pickupLatitude = HUB_LAT;
        person7.pickupLongitude = HUB_LON;
        person7.persist();

        Employee person8 = new Employee("Person 8", "person8@example.com", "+49 151 1234 5008", EmployeeType.SITE_EMPLOYEE);
        person8.pickupLatitude = HUB_LAT;
        person8.pickupLongitude = HUB_LON;
        person8.persist();

        // --- BARBE employees (3 for different shifts) ---

        Employee person9 = new Employee("Person 9", "person9@example.com", "+49 151 1234 5009", EmployeeType.SITE_EMPLOYEE);
        person9.pickupLatitude = HUB_LAT;
        person9.pickupLongitude = HUB_LON;
        person9.persist();

        Employee person10 = new Employee("Person 10", "person10@example.com", "+49 151 1234 5010", EmployeeType.SITE_EMPLOYEE);
        person10.pickupLatitude = HUB_LAT;
        person10.pickupLongitude = HUB_LON;
        person10.persist();

        Employee person11 = new Employee("Person 11", "person11@example.com", "+49 151 1234 5011", EmployeeType.SITE_EMPLOYEE);
        person11.pickupLatitude = HUB_LAT;
        person11.pickupLongitude = HUB_LON;
        person11.persist();

        // --- BENEO employee (1) ---

        Employee person12 = new Employee("Person 12", "person12@example.com", "+49 151 1234 5012", EmployeeType.SITE_EMPLOYEE);
        person12.pickupLatitude = HUB_LAT;
        person12.pickupLongitude = HUB_LON;
        person12.persist();

        // ==================== SHIFTS ====================

        // --- CHEP shifts ---
        // Early: Mon-Thu 05:30-14:00, Fri 05:30-13:00
        // 5 employees: Person 1, Person 2, Person 3, Person 4, Person 5

        for (DayOfWeek day : new DayOfWeek[]{DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY}) {
            ShiftDemand chepEarly = new ShiftDemand(chep, "Frühschicht", day, LocalTime.of(5, 30), LocalTime.of(14, 0), 5);
            chepEarly.persist();
            person1.assignToShift(chepEarly);
            person2.assignToShift(chepEarly);
            person3.assignToShift(chepEarly);
            person4.assignToShift(chepEarly);
            person5.assignToShift(chepEarly);
        }

        // Friday early shift ends at 13:00
        ShiftDemand chepEarlyFri = new ShiftDemand(chep, "Frühschicht", DayOfWeek.FRIDAY, LocalTime.of(5, 30), LocalTime.of(13, 0), 5);
        chepEarlyFri.persist();
        person1.assignToShift(chepEarlyFri);
        person2.assignToShift(chepEarlyFri);
        person3.assignToShift(chepEarlyFri);
        person4.assignToShift(chepEarlyFri);
        person5.assignToShift(chepEarlyFri);

        // --- SANNER shifts ---
        // Early: Mon-Fri 06:00-14:00
        // 1 employee: Person 6

        for (DayOfWeek day : new DayOfWeek[]{DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY}) {
            ShiftDemand sannerEarly = new ShiftDemand(sanner, "Frühschicht", day, LocalTime.of(6, 0), LocalTime.of(14, 0), 1);
            sannerEarly.persist();
            person6.assignToShift(sannerEarly);
        }

        // --- ORION shifts ---
        // Day: Mon-Thu 06:30-16:00, Fri 06:30-12:45
        // 2 employees: Person 7, Person 8

        for (DayOfWeek day : new DayOfWeek[]{DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY}) {
            ShiftDemand orionDay = new ShiftDemand(orion, "Tagschicht", day, LocalTime.of(6, 30), LocalTime.of(16, 0), 2);
            orionDay.persist();
            person7.assignToShift(orionDay);
            person8.assignToShift(orionDay);
        }

        // Friday ends at 12:45
        ShiftDemand orionDayFri = new ShiftDemand(orion, "Tagschicht", DayOfWeek.FRIDAY, LocalTime.of(6, 30), LocalTime.of(12, 45), 2);
        orionDayFri.persist();
        person7.assignToShift(orionDayFri);
        person8.assignToShift(orionDayFri);

        // --- BARBE shifts ---
        // Night shift workers (picked up 06:00/06:30 after their night shift): Person 9, Person 10
        // They work night 22:00-06:00, so we model the return trip
        // Late shift (13:00 dropoff, 22:00 pickup): Person 11

        // Person 9 and Person 10 work night shifts (Tue-Sat nights, meaning Mon-Fri evening dropoff, Tue-Sat morning pickup)
        // For simplicity, model as night shift with return trip
        for (DayOfWeek day : new DayOfWeek[]{DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY}) {
            ShiftDemand barbeNight = new ShiftDemand(barbe, "Nachtschicht", day, LocalTime.of(22, 0), LocalTime.of(6, 0), 2);
            barbeNight.persist();
            person9.assignToShift(barbeNight);
            person10.assignToShift(barbeNight);
        }

        // Person 11 works late shift Mon-Fri
        for (DayOfWeek day : new DayOfWeek[]{DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY}) {
            ShiftDemand barbeLate = new ShiftDemand(barbe, "Spätschicht", day, LocalTime.of(14, 0), LocalTime.of(22, 0), 1);
            barbeLate.persist();
            person11.assignToShift(barbeLate);
        }

        // Saturday night shift for Barbe (pickup at 06:00 Saturday)
        ShiftDemand barbeNightSat = new ShiftDemand(barbe, "Nachtschicht", DayOfWeek.SATURDAY, LocalTime.of(22, 0), LocalTime.of(6, 0), 2);
        barbeNightSat.persist();
        person9.assignToShift(barbeNightSat);
        person10.assignToShift(barbeNightSat);

        // --- BENEO shifts ---
        // Night shift worker: Person 12
        // Picked up Wed/Thu and Fri/Sat at 21:40/22:30 (after night shift ends at 05:35)
        // Complex schedule - model key shifts

        // Wednesday night (pickup Thursday morning)
        ShiftDemand beneoNightWed = new ShiftDemand(beneo, "Nachtschicht", DayOfWeek.WEDNESDAY, LocalTime.of(21, 35), LocalTime.of(5, 35), 1);
        beneoNightWed.persist();
        person12.assignToShift(beneoNightWed);

        // Thursday night (pickup Friday morning)
        ShiftDemand beneoNightThu = new ShiftDemand(beneo, "Nachtschicht", DayOfWeek.THURSDAY, LocalTime.of(21, 35), LocalTime.of(5, 35), 1);
        beneoNightThu.persist();
        person12.assignToShift(beneoNightThu);

        // Friday night (pickup Saturday morning)
        ShiftDemand beneoNightFri = new ShiftDemand(beneo, "Nachtschicht", DayOfWeek.FRIDAY, LocalTime.of(21, 35), LocalTime.of(5, 35), 1);
        beneoNightFri.persist();
        person12.assignToShift(beneoNightFri);

        // Saturday - pickup from night shift in morning (05:40), dropoff for late shift (21:00)
        ShiftDemand beneoNightSat = new ShiftDemand(beneo, "Nachtschicht", DayOfWeek.SATURDAY, LocalTime.of(21, 35), LocalTime.of(5, 35), 1);
        beneoNightSat.persist();
        person12.assignToShift(beneoNightSat);

        // Sunday - 12 hour shift (05:35-17:35 or 17:35-05:35)
        // Model as early 12h shift with dropoff at 17:00
        ShiftDemand beneoSunday = new ShiftDemand(beneo, "Sonntagsschicht", DayOfWeek.SUNDAY, LocalTime.of(5, 35), LocalTime.of(17, 35), 1);
        beneoSunday.persist();
        person12.assignToShift(beneoSunday);

        LOG.info("KW 51 demo data loaded successfully:");
        LOG.info("  - 5 customers: Chep, Sanner, Orion, Barbe, Beneo");
        LOG.info("  - 3 drivers: Driver 1, Driver 2, Driver 3");
        LOG.info("  - 12 site employees (generic sample persons)");
        LOG.info("  - Multiple shifts covering Mon-Sun with early, late, and night patterns");
    }
}
