package com.vrp.e2e;

import com.vrp.entity.Customer;
import com.vrp.entity.Employee;
import com.vrp.entity.EmployeeType;
import com.vrp.entity.ShiftDemand;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Proves the FULL raw input dataset — the real starting point of the optimization pipeline —
 * is persisted into a real (in-memory) H2 database by {@code DataBootstrap} at application
 * startup. No mocks, no hand-fed rows: the app boots, seeds, and we read every field back.
 *
 * <p>This is the "soup" end of soup-to-nuts: if the seed data were wrong, every downstream
 * assertion in the pipeline test would be meaningless. So we verify counts AND field-by-field
 * values for every customer, every driver, and representative employees/shifts.
 */
@QuarkusTest
class DataSeedIntegrationTest {

    // ---- Customers: exact coordinates from DataBootstrap ----

    @Test
    @TestTransaction
    void allFiveCustomersPersistedWithEveryField() {
        List<Customer> customers = Customer.listAll();
        assertEquals(5, customers.size(), "DataBootstrap seeds exactly 5 customers");

        // Every customer must have all fields populated (nothing partially seeded).
        for (Customer c : customers) {
            assertNotNull(c.id, "customer id");
            assertNotNull(c.name, "customer name");
            assertFalse(c.name.isBlank(), "customer name non-blank");
            assertNotNull(c.address, "customer address");
            assertFalse(c.address.isBlank(), "customer address non-blank");
            assertTrue(c.latitude > 49.0 && c.latitude < 50.5, "plausible RLP latitude: " + c.name);
            assertTrue(c.longitude > 7.5 && c.longitude < 9.0, "plausible RLP longitude: " + c.name);
        }

        // Exact per-customer coordinates + address (every field, not a sample).
        assertCustomer("Chep Deutschland GmbH", "Am Winkelgraben 13, 64584 Biebesheim", 49.7784, 8.4625);
        assertCustomer("Sanner GmbH", "Bertha-Benz-Straße 5, 64625 Bensheim", 49.6800, 8.6250);
        assertCustomer("Orion Bausysteme GmbH", "Waldstr. 2, 64584 Biebesheim", 49.7750, 8.4580);
        assertCustomer("Hans W. Barbe Chemische Erzeugnisse GmbH", "Justus-von-Liebig-Str. 17, 67549 Worms", 49.6350, 8.3480);
        assertCustomer("Beneo-Palatinit GmbH", "Wormser Straße 11, 67283 Obrigheim", 49.4700, 8.2100);
    }

    private void assertCustomer(String name, String address, double lat, double lon) {
        Customer c = Customer.find("name", name).firstResult();
        assertNotNull(c, "customer present: " + name);
        assertEquals(address, c.address, "address for " + name);
        assertEquals(lat, c.latitude, 1e-9, "latitude for " + name);
        assertEquals(lon, c.longitude, 1e-9, "longitude for " + name);
    }

    // ---- Employees: 3 drivers + 12 site employees, every field ----

    @Test
    @TestTransaction
    void allEmployeesPersistedWithTypeAndCoordinates() {
        List<Employee> all = Employee.listAll();
        assertEquals(15, all.size(), "3 drivers + 12 site employees");

        long drivers = Employee.count("employeeType", EmployeeType.DRIVER);
        long site = Employee.count("employeeType", EmployeeType.SITE_EMPLOYEE);
        assertEquals(3, drivers, "driver count");
        assertEquals(12, site, "site-employee count");

        // Every driver: active, home = City-Fahrschule hub, no pickup coords, name/email/phone set.
        for (Employee d : Employee.<Employee>list("employeeType", EmployeeType.DRIVER)) {
            assertTrue(d.active, "driver active: " + d.name);
            assertNotNull(d.name);
            assertNotNull(d.email);
            assertNotNull(d.phoneNumber);
            assertEquals(49.6295, d.homeLatitude, 1e-9, "driver home lat: " + d.name);
            assertEquals(8.3640, d.homeLongitude, 1e-9, "driver home lon: " + d.name);
        }

        // Every site employee: active, has pickup coordinates, no home coordinates.
        for (Employee p : Employee.<Employee>list("employeeType", EmployeeType.SITE_EMPLOYEE)) {
            assertTrue(p.active, "site employee active: " + p.name);
            assertNotNull(p.pickupLatitude, "pickup lat set: " + p.name);
            assertNotNull(p.pickupLongitude, "pickup lon set: " + p.name);
        }

        // Person 1 uniquely picks up at Tankstelle Pfeddersheim; the rest at the hub.
        Employee person1 = Employee.find("name", "Person 1").firstResult();
        assertNotNull(person1);
        assertEquals(49.6180, person1.pickupLatitude, 1e-9, "Person 1 pickup lat (Tankstelle)");
        assertEquals(8.2970, person1.pickupLongitude, 1e-9, "Person 1 pickup lon (Tankstelle)");

        Employee person2 = Employee.find("name", "Person 2").firstResult();
        assertNotNull(person2);
        assertEquals(49.6295, person2.pickupLatitude, 1e-9, "Person 2 pickup lat (hub)");
        assertEquals(8.3640, person2.pickupLongitude, 1e-9, "Person 2 pickup lon (hub)");
    }

    // ---- Shifts: 31 total, all active, all fields present, spot-checked ----

    @Test
    @TestTransaction
    void allShiftsPersistedWithEveryFieldAndAssignments() {
        List<ShiftDemand> shifts = ShiftDemand.listAll();
        assertEquals(31, shifts.size(), "total seeded shift demands");
        assertEquals(31, ShiftDemand.count("active", true), "all shifts active");

        // Every shift is fully populated.
        for (ShiftDemand s : shifts) {
            assertNotNull(s.customer, "shift has customer");
            assertNotNull(s.shiftType, "shift type");
            assertFalse(s.shiftType.isBlank());
            assertNotNull(s.dayOfWeek, "day of week");
            assertNotNull(s.startTime, "start time");
            assertNotNull(s.endTime, "end time");
            assertTrue(s.requiredEmployees > 0, "required employees > 0");
            assertNotNull(s.assignedEmployees, "assigned employees set");
        }

        // Per-customer shift counts (derived from DataBootstrap):
        // Chep 5 (Mon-Thu early + Fri), Sanner 5 (Mon-Fri), Orion 5 (Mon-Thu + Fri),
        // Barbe 11 (night Mon-Fri + late Mon-Fri + night Sat), Beneo 5 (Wed/Thu/Fri/Sat night + Sun).
        Map<String, Long> expected = Map.of(
            "Chep Deutschland GmbH", 5L,
            "Sanner GmbH", 5L,
            "Orion Bausysteme GmbH", 5L,
            "Hans W. Barbe Chemische Erzeugnisse GmbH", 11L,
            "Beneo-Palatinit GmbH", 5L);
        expected.forEach((customerName, count) -> {
            long actual = ShiftDemand.count("customer.name", customerName);
            assertEquals(count, actual, "shift count for " + customerName);
        });

        // Spot-check a concrete shift end-to-end: Chep Monday Frühschicht 05:30-14:00, 5 required, 5 assigned.
        ShiftDemand chepMon = ShiftDemand.find(
            "customer.name = ?1 and dayOfWeek = ?2 and startTime = ?3",
            "Chep Deutschland GmbH", DayOfWeek.MONDAY, LocalTime.of(5, 30)).firstResult();
        assertNotNull(chepMon, "Chep Monday early shift present");
        assertEquals(LocalTime.of(14, 0), chepMon.endTime);
        assertEquals(5, chepMon.requiredEmployees);
        assertEquals(5, chepMon.assignedEmployees.size(), "5 employees assigned to Chep Monday early");

        // Barbe night shift spans midnight (22:00 -> 06:00) — the real data that exercises shiftDate logic.
        ShiftDemand barbeNight = ShiftDemand.find(
            "customer.name = ?1 and shiftType = ?2 and dayOfWeek = ?3",
            "Hans W. Barbe Chemische Erzeugnisse GmbH", "Nachtschicht", DayOfWeek.MONDAY).firstResult();
        assertNotNull(barbeNight, "Barbe Monday night shift present");
        assertEquals(LocalTime.of(22, 0), barbeNight.startTime);
        assertEquals(LocalTime.of(6, 0), barbeNight.endTime);
        assertTrue(barbeNight.endTime.isBefore(barbeNight.startTime), "night shift wraps past midnight");
    }
}
