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

        Employee thomas = new Employee("Thomas Koch", "thomas@example.com", "+49 151 1234 5607", EmployeeType.DRIVER);
        thomas.homeLatitude = HUB_LAT;
        thomas.homeLongitude = HUB_LON;
        thomas.persist();

        Employee sarah = new Employee("Sarah Meyer", "sarah@example.com", "+49 151 1234 5608", EmployeeType.DRIVER);
        sarah.homeLatitude = HUB_LAT;
        sarah.homeLongitude = HUB_LON;
        sarah.persist();

        Employee michael = new Employee("Michael Wagner", "michael@example.com", "+49 151 1234 5609", EmployeeType.DRIVER);
        michael.homeLatitude = HUB_LAT;
        michael.homeLongitude = HUB_LON;
        michael.persist();

        // ==================== SITE EMPLOYEES ====================

        // --- CHEP employees (5 total) ---
        // 1 from Tankstelle Pfeddersheim, 4 from City-Fahrschule

        Employee naruto = new Employee("Naruto Uzumaki", "naruto@example.com", "+49 151 1234 5001", EmployeeType.SITE_EMPLOYEE);
        naruto.pickupLatitude = TANKSTELLE_LAT;  // Pfeddersheim pickup
        naruto.pickupLongitude = TANKSTELLE_LON;
        naruto.persist();

        Employee sasuke = new Employee("Sasuke Uchiha", "sasuke@example.com", "+49 151 1234 5002", EmployeeType.SITE_EMPLOYEE);
        sasuke.pickupLatitude = HUB_LAT;  // City-Fahrschule
        sasuke.pickupLongitude = HUB_LON;
        sasuke.persist();

        Employee sakura = new Employee("Sakura Haruno", "sakura@example.com", "+49 151 1234 5003", EmployeeType.SITE_EMPLOYEE);
        sakura.pickupLatitude = HUB_LAT;
        sakura.pickupLongitude = HUB_LON;
        sakura.persist();

        Employee hinata = new Employee("Hinata Hyuga", "hinata@example.com", "+49 151 1234 5004", EmployeeType.SITE_EMPLOYEE);
        hinata.pickupLatitude = HUB_LAT;
        hinata.pickupLongitude = HUB_LON;
        hinata.persist();

        Employee shikamaru = new Employee("Shikamaru Nara", "shikamaru@example.com", "+49 151 1234 5005", EmployeeType.SITE_EMPLOYEE);
        shikamaru.pickupLatitude = HUB_LAT;
        shikamaru.pickupLongitude = HUB_LON;
        shikamaru.persist();

        // --- SANNER employee (1) ---

        Employee kakashi = new Employee("Kakashi Hatake", "kakashi@example.com", "+49 151 1234 5006", EmployeeType.SITE_EMPLOYEE);
        kakashi.pickupLatitude = HUB_LAT;
        kakashi.pickupLongitude = HUB_LON;
        kakashi.persist();

        // --- ORION employees (2) ---

        Employee ino = new Employee("Ino Yamanaka", "ino@example.com", "+49 151 1234 5007", EmployeeType.SITE_EMPLOYEE);
        ino.pickupLatitude = HUB_LAT;
        ino.pickupLongitude = HUB_LON;
        ino.persist();

        Employee choji = new Employee("Choji Akimichi", "choji@example.com", "+49 151 1234 5008", EmployeeType.SITE_EMPLOYEE);
        choji.pickupLatitude = HUB_LAT;
        choji.pickupLongitude = HUB_LON;
        choji.persist();

        // --- BARBE employees (3 for different shifts) ---

        Employee rockLee = new Employee("Rock Lee", "rocklee@example.com", "+49 151 1234 5009", EmployeeType.SITE_EMPLOYEE);
        rockLee.pickupLatitude = HUB_LAT;
        rockLee.pickupLongitude = HUB_LON;
        rockLee.persist();

        Employee neji = new Employee("Neji Hyuga", "neji@example.com", "+49 151 1234 5010", EmployeeType.SITE_EMPLOYEE);
        neji.pickupLatitude = HUB_LAT;
        neji.pickupLongitude = HUB_LON;
        neji.persist();

        Employee gaara = new Employee("Gaara Sabaku", "gaara@example.com", "+49 151 1234 5011", EmployeeType.SITE_EMPLOYEE);
        gaara.pickupLatitude = HUB_LAT;
        gaara.pickupLongitude = HUB_LON;
        gaara.persist();

        // --- BENEO employee (1) ---

        Employee temari = new Employee("Temari Sabaku", "temari@example.com", "+49 151 1234 5012", EmployeeType.SITE_EMPLOYEE);
        temari.pickupLatitude = HUB_LAT;
        temari.pickupLongitude = HUB_LON;
        temari.persist();

        // ==================== SHIFTS ====================

        // --- CHEP shifts ---
        // Early: Mon-Thu 05:30-14:00, Fri 05:30-13:00
        // 5 employees: Naruto, Sasuke, Sakura, Hinata, Shikamaru

        for (DayOfWeek day : new DayOfWeek[]{DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY}) {
            ShiftDemand chepEarly = new ShiftDemand(chep, "Frühschicht", day, LocalTime.of(5, 30), LocalTime.of(14, 0), 5);
            chepEarly.persist();
            naruto.assignToShift(chepEarly);
            sasuke.assignToShift(chepEarly);
            sakura.assignToShift(chepEarly);
            hinata.assignToShift(chepEarly);
            shikamaru.assignToShift(chepEarly);
        }

        // Friday early shift ends at 13:00
        ShiftDemand chepEarlyFri = new ShiftDemand(chep, "Frühschicht", DayOfWeek.FRIDAY, LocalTime.of(5, 30), LocalTime.of(13, 0), 5);
        chepEarlyFri.persist();
        naruto.assignToShift(chepEarlyFri);
        sasuke.assignToShift(chepEarlyFri);
        sakura.assignToShift(chepEarlyFri);
        hinata.assignToShift(chepEarlyFri);
        shikamaru.assignToShift(chepEarlyFri);

        // --- SANNER shifts ---
        // Early: Mon-Fri 06:00-14:00
        // 1 employee: Kakashi

        for (DayOfWeek day : new DayOfWeek[]{DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY}) {
            ShiftDemand sannerEarly = new ShiftDemand(sanner, "Frühschicht", day, LocalTime.of(6, 0), LocalTime.of(14, 0), 1);
            sannerEarly.persist();
            kakashi.assignToShift(sannerEarly);
        }

        // --- ORION shifts ---
        // Day: Mon-Thu 06:30-16:00, Fri 06:30-12:45
        // 2 employees: Ino, Choji

        for (DayOfWeek day : new DayOfWeek[]{DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY}) {
            ShiftDemand orionDay = new ShiftDemand(orion, "Tagschicht", day, LocalTime.of(6, 30), LocalTime.of(16, 0), 2);
            orionDay.persist();
            ino.assignToShift(orionDay);
            choji.assignToShift(orionDay);
        }

        // Friday ends at 12:45
        ShiftDemand orionDayFri = new ShiftDemand(orion, "Tagschicht", DayOfWeek.FRIDAY, LocalTime.of(6, 30), LocalTime.of(12, 45), 2);
        orionDayFri.persist();
        ino.assignToShift(orionDayFri);
        choji.assignToShift(orionDayFri);

        // --- BARBE shifts ---
        // Night shift workers (picked up 06:00/06:30 after their night shift): Rock Lee, Neji
        // They work night 22:00-06:00, so we model the return trip
        // Late shift (13:00 dropoff, 22:00 pickup): Gaara

        // Rock Lee and Neji work night shifts (Tue-Sat nights, meaning Mon-Fri evening dropoff, Tue-Sat morning pickup)
        // For simplicity, model as night shift with return trip
        for (DayOfWeek day : new DayOfWeek[]{DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY}) {
            ShiftDemand barbeNight = new ShiftDemand(barbe, "Nachtschicht", day, LocalTime.of(22, 0), LocalTime.of(6, 0), 2);
            barbeNight.persist();
            rockLee.assignToShift(barbeNight);
            neji.assignToShift(barbeNight);
        }

        // Gaara works late shift Mon-Fri
        for (DayOfWeek day : new DayOfWeek[]{DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY}) {
            ShiftDemand barbeLate = new ShiftDemand(barbe, "Spätschicht", day, LocalTime.of(14, 0), LocalTime.of(22, 0), 1);
            barbeLate.persist();
            gaara.assignToShift(barbeLate);
        }

        // Saturday night shift for Barbe (pickup at 06:00 Saturday)
        ShiftDemand barbeNightSat = new ShiftDemand(barbe, "Nachtschicht", DayOfWeek.SATURDAY, LocalTime.of(22, 0), LocalTime.of(6, 0), 2);
        barbeNightSat.persist();
        rockLee.assignToShift(barbeNightSat);
        neji.assignToShift(barbeNightSat);

        // --- BENEO shifts ---
        // Night shift worker: Temari
        // Picked up Wed/Thu and Fri/Sat at 21:40/22:30 (after night shift ends at 05:35)
        // Complex schedule - model key shifts

        // Wednesday night (pickup Thursday morning)
        ShiftDemand beneoNightWed = new ShiftDemand(beneo, "Nachtschicht", DayOfWeek.WEDNESDAY, LocalTime.of(21, 35), LocalTime.of(5, 35), 1);
        beneoNightWed.persist();
        temari.assignToShift(beneoNightWed);

        // Thursday night (pickup Friday morning)
        ShiftDemand beneoNightThu = new ShiftDemand(beneo, "Nachtschicht", DayOfWeek.THURSDAY, LocalTime.of(21, 35), LocalTime.of(5, 35), 1);
        beneoNightThu.persist();
        temari.assignToShift(beneoNightThu);

        // Friday night (pickup Saturday morning)
        ShiftDemand beneoNightFri = new ShiftDemand(beneo, "Nachtschicht", DayOfWeek.FRIDAY, LocalTime.of(21, 35), LocalTime.of(5, 35), 1);
        beneoNightFri.persist();
        temari.assignToShift(beneoNightFri);

        // Saturday - pickup from night shift in morning (05:40), dropoff for late shift (21:00)
        ShiftDemand beneoNightSat = new ShiftDemand(beneo, "Nachtschicht", DayOfWeek.SATURDAY, LocalTime.of(21, 35), LocalTime.of(5, 35), 1);
        beneoNightSat.persist();
        temari.assignToShift(beneoNightSat);

        // Sunday - 12 hour shift (05:35-17:35 or 17:35-05:35)
        // Model as early 12h shift with dropoff at 17:00
        ShiftDemand beneoSunday = new ShiftDemand(beneo, "Sonntagsschicht", DayOfWeek.SUNDAY, LocalTime.of(5, 35), LocalTime.of(17, 35), 1);
        beneoSunday.persist();
        temari.assignToShift(beneoSunday);

        LOG.info("KW 51 demo data loaded successfully:");
        LOG.info("  - 5 customers: Chep, Sanner, Orion, Barbe, Beneo");
        LOG.info("  - 3 drivers: Thomas, Sarah, Michael");
        LOG.info("  - 12 site employees (Naruto characters)");
        LOG.info("  - Multiple shifts covering Mon-Sun with early, late, and night patterns");
    }
}
