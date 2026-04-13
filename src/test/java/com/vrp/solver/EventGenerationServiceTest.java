package com.vrp.solver;

import com.vrp.domain.Event;
import com.vrp.domain.Location;
import com.vrp.domain.Stop;
import com.vrp.entity.Customer;
import com.vrp.entity.Employee;
import com.vrp.entity.EmployeeType;
import com.vrp.entity.ShiftDemand;
import com.vrp.service.EventGenerationService;
import com.vrp.service.GraphHopperService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class EventGenerationServiceTest {

    private static final Location HUB = Location.HUB;
    private static final Location TANKSTELLE = new Location("Tankstelle Pfeddersheim", 49.6180, 8.2970);
    private static final Location CHEP = new Location("Chep", 49.7784, 8.4625);
    private static final Location SANNER = new Location("Sanner", 49.6800, 8.6250);
    private static final Location ORION = new Location("Orion", 49.7750, 8.4580);
    private static final Location BARBE = new Location("Barbe", 49.6350, 8.3480);

    private EventGenerationService service;
    private LocalDate weekStart;

    @BeforeEach
    void setUp() {
        service = new EventGenerationService();
        service.setGraphHopperService(null);
        weekStart = LocalDate.of(2024, 12, 16); // Monday KW51
    }

    // ================================================================
    // FR-1: Same-customer batching tests
    // ================================================================

    @Test
    void sameCustomer_sameLocation_shouldBeBatched() {
        Customer chep = createCustomer("Chep", CHEP);
        List<Employee> employees = List.of(
            createEmployee(1, "Sasuke", null, null),
            createEmployee(2, "Sakura", null, null),
            createEmployee(3, "Hinata", null, null),
            createEmployee(4, "Shikamaru", null, null)
        );
        ShiftDemand shift = createShift(chep, DayOfWeek.MONDAY, LocalTime.of(5, 30), LocalTime.of(14, 0), employees);

        List<Event> events = service.generateEventsForWeek(List.of(shift), weekStart, HUB);

        assertEquals(2, events.size());
        Event pickup = events.stream().filter(Event::isPickup).findFirst().orElseThrow();
        assertEquals(4, pickup.getPassengerCount());
    }

    @Test
    void differentPickupLocations_shouldCreateSeparateEvents() {
        Customer chep = createCustomer("Chep", CHEP);
        Employee atHub = createEmployee(1, "Sasuke", null, null);
        Employee atTankstelle = createEmployee(2, "Naruto", TANKSTELLE.latitude(), TANKSTELLE.longitude());
        ShiftDemand shift = createShift(chep, DayOfWeek.MONDAY, LocalTime.of(5, 30), LocalTime.of(14, 0),
            List.of(atHub, atTankstelle));

        List<Event> events = service.generateEventsForWeek(List.of(shift), weekStart, HUB);

        long pickups = events.stream().filter(Event::isPickup).count();
        assertEquals(2, pickups);
    }

    @Test
    void boardingTime_scalesWithPassengerCount() {
        Customer chep = createCustomer("Chep", CHEP);
        List<Employee> employees4 = List.of(
            createEmployee(1, "A", null, null),
            createEmployee(2, "B", null, null),
            createEmployee(3, "C", null, null),
            createEmployee(4, "D", null, null)
        );
        ShiftDemand shift4 = createShift(chep, DayOfWeek.MONDAY, LocalTime.of(5, 30), LocalTime.of(14, 0), employees4);

        List<Employee> employees1 = List.of(createEmployee(5, "E", null, null));
        ShiftDemand shift1 = createShift(chep, DayOfWeek.TUESDAY, LocalTime.of(5, 30), LocalTime.of(14, 0), employees1);

        List<Event> events = service.generateEventsForWeek(List.of(shift4, shift1), weekStart, HUB);

        Event pickup4 = events.stream()
            .filter(Event::isPickup)
            .filter(e -> e.getShiftDate().equals(weekStart))
            .findFirst().orElseThrow();
        Event pickup1 = events.stream()
            .filter(Event::isPickup)
            .filter(e -> e.getShiftDate().equals(weekStart.plusDays(1)))
            .findFirst().orElseThrow();

        Duration diff = pickup4.getDuration().minus(pickup1.getDuration());
        assertTrue(diff.toMinutes() >= 5,
            "4-passenger event should have more boarding time than 1-passenger event, got diff=" + diff.toMinutes());
    }

    @Test
    void inactiveShift_shouldProduceNoEvents() {
        Customer chep = createCustomer("Chep", CHEP);
        List<Employee> employees = List.of(createEmployee(1, "A", null, null));
        ShiftDemand shift = createShift(chep, DayOfWeek.MONDAY, LocalTime.of(5, 30), LocalTime.of(14, 0), employees);
        shift.active = false;

        List<Event> events = service.generateEventsForWeek(List.of(shift), weekStart, HUB);
        assertTrue(events.isEmpty());
    }

    @Test
    void noReturnTrip_shouldProduceOnlyPickup() {
        Customer chep = createCustomer("Chep", CHEP);
        List<Employee> employees = List.of(createEmployee(1, "A", null, null));
        ShiftDemand shift = createShift(chep, DayOfWeek.MONDAY, LocalTime.of(5, 30), LocalTime.of(14, 0), employees);
        shift.requiresReturnTrip = false;

        List<Event> events = service.generateEventsForWeek(List.of(shift), weekStart, HUB);
        assertEquals(1, events.size());
        assertTrue(events.get(0).isPickup());
    }

    @Test
    void pairedEvents_shouldBeLinked() {
        Customer chep = createCustomer("Chep", CHEP);
        List<Employee> employees = List.of(createEmployee(1, "A", null, null));
        ShiftDemand shift = createShift(chep, DayOfWeek.MONDAY, LocalTime.of(5, 30), LocalTime.of(14, 0), employees);

        List<Event> events = service.generateEventsForWeek(List.of(shift), weekStart, HUB);
        Event pickup = events.stream().filter(Event::isPickup).findFirst().orElseThrow();
        Event dropoff = events.stream().filter(e -> !e.isPickup()).findFirst().orElseThrow();

        assertEquals(dropoff, pickup.getPairedEvent());
        assertEquals(pickup, dropoff.getPairedEvent());
    }

    @Test
    void shiftDate_shouldBeSetOnAllEvents() {
        Customer chep = createCustomer("Chep", CHEP);
        List<Employee> employees = List.of(createEmployee(1, "A", null, null));
        ShiftDemand shift = createShift(chep, DayOfWeek.MONDAY, LocalTime.of(5, 30), LocalTime.of(14, 0), employees);

        List<Event> events = service.generateEventsForWeek(List.of(shift), weekStart, HUB);
        for (Event e : events) {
            assertEquals(weekStart, e.getShiftDate());
        }
    }

    // ================================================================
    // FR-3: Cross-customer batching tests
    // ================================================================

    @Test
    void samePickupLocation_differentCustomers_shouldMergeToMultiStop() {
        Customer chep = createCustomer("Chep", CHEP);
        Customer sanner = createCustomer("Sanner", SANNER);

        List<Employee> chepEmployees = List.of(
            createEmployee(1, "Sasuke", null, null),
            createEmployee(2, "Sakura", null, null),
            createEmployee(3, "Hinata", null, null),
            createEmployee(4, "Shikamaru", null, null)
        );
        List<Employee> sannerEmployees = List.of(
            createEmployee(5, "Kakashi", null, null)
        );

        ShiftDemand chepShift = createShift(chep, DayOfWeek.MONDAY, LocalTime.of(5, 30), LocalTime.of(14, 0), chepEmployees);
        ShiftDemand sannerShift = createShift(sanner, DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(14, 0), sannerEmployees);

        List<Event> events = service.generateEventsForWeek(List.of(chepShift, sannerShift), weekStart, HUB);

        long pickups = events.stream().filter(Event::isPickup).count();
        long dropoffs = events.stream().filter(e -> !e.isPickup()).count();

        // FR-3 merges same-location pickups: 1 multi-stop pickup + 2 dropoffs = 3 events
        assertEquals(1, pickups, "FR-3 should merge same-location pickups into 1 multi-stop event");
        assertEquals(2, dropoffs, "Each customer should still have its own return trip dropoff");

        Event multiStopPickup = events.stream().filter(Event::isPickup).findFirst().orElseThrow();
        assertNotNull(multiStopPickup.getStops());
        assertTrue(multiStopPickup.getStops().size() >= 2);
        assertEquals(5, multiStopPickup.getPassengerCount());
        assertEquals(5, multiStopPickup.getPeakPassengerCount());
    }

    @Test
    void multiStopEvent_shouldHaveNetZeroDelta() {
        Customer chep = createCustomer("Chep", CHEP);
        Customer sanner = createCustomer("Sanner", SANNER);

        List<Employee> chepEmployees = List.of(
            createEmployee(1, "A", null, null),
            createEmployee(2, "B", null, null),
            createEmployee(3, "C", null, null),
            createEmployee(4, "D", null, null)
        );
        List<Employee> sannerEmployees = List.of(
            createEmployee(5, "E", null, null)
        );

        ShiftDemand chepShift = createShift(chep, DayOfWeek.MONDAY, LocalTime.of(5, 30), LocalTime.of(14, 0), chepEmployees);
        ShiftDemand sannerShift = createShift(sanner, DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(14, 0), sannerEmployees);

        List<Event> events = service.generateEventsForWeek(List.of(chepShift, sannerShift), weekStart, HUB);

        Event multiStopPickup = events.stream().filter(Event::isPickup).findFirst().orElseThrow();

        // FR-3 multi-stop event: all passengers board at stop 0 and alight at subsequent stops
        assertEquals(0, multiStopPickup.getPassengerDelta());
        assertEquals(5, multiStopPickup.getPeakPassengerCount());
    }

    @Test
    void differentPickupLocations_differentCustomers_shouldNotMerge() {
        Customer chep = createCustomer("Chep", CHEP);
        Customer sanner = createCustomer("Sanner", SANNER);

        Employee atTankstelle = createEmployee(1, "Naruto", TANKSTELLE.latitude(), TANKSTELLE.longitude());
        Employee atHub = createEmployee(2, "Kakashi", null, null);

        ShiftDemand chepShift = createShift(chep, DayOfWeek.MONDAY, LocalTime.of(5, 30), LocalTime.of(14, 0), List.of(atTankstelle));
        ShiftDemand sannerShift = createShift(sanner, DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(14, 0), List.of(atHub));

        List<Event> events = service.generateEventsForWeek(List.of(chepShift, sannerShift), weekStart, HUB);

        long pickups = events.stream().filter(Event::isPickup).count();
        assertEquals(2, pickups, "Different pickup locations should produce separate pickup events");
    }

    // ================================================================
    // Full Monday scenario
    // ================================================================

    @Test
    void fullMondayScenario_matchesRequirements() {
        Customer chep = createCustomer("Chep", CHEP);
        Customer sanner = createCustomer("Sanner", SANNER);
        Customer orion = createCustomer("Orion", ORION);
        Customer barbe = createCustomer("Barbe", BARBE);

        Employee naruto = createEmployee(1, "Naruto", TANKSTELLE.latitude(), TANKSTELLE.longitude());
        Employee sasuke = createEmployee(2, "Sasuke", null, null);
        Employee sakura = createEmployee(3, "Sakura", null, null);
        Employee hinata = createEmployee(4, "Hinata", null, null);
        Employee shikamaru = createEmployee(5, "Shikamaru", null, null);
        Employee kakashi = createEmployee(6, "Kakashi", null, null);
        Employee ino = createEmployee(7, "Ino", null, null);
        Employee choji = createEmployee(8, "Choji", null, null);
        Employee rockLee = createEmployee(9, "Rock Lee", null, null);
        Employee neji = createEmployee(10, "Neji", null, null);
        Employee gaara = createEmployee(11, "Gaara", null, null);

        ShiftDemand chepEarly = createShift(chep, DayOfWeek.MONDAY, LocalTime.of(5, 30), LocalTime.of(14, 0),
            List.of(naruto, sasuke, sakura, hinata, shikamaru));
        ShiftDemand sannerEarly = createShift(sanner, DayOfWeek.MONDAY, LocalTime.of(6, 0), LocalTime.of(14, 0),
            List.of(kakashi));
        ShiftDemand orionDay = createShift(orion, DayOfWeek.MONDAY, LocalTime.of(6, 30), LocalTime.of(16, 0),
            List.of(ino, choji));
        ShiftDemand barbeNight = createShift(barbe, DayOfWeek.MONDAY, LocalTime.of(22, 0), LocalTime.of(6, 0),
            List.of(rockLee, neji));
        ShiftDemand barbeLate = createShift(barbe, DayOfWeek.MONDAY, LocalTime.of(14, 0), LocalTime.of(22, 0),
            List.of(gaara));

        List<Event> events = service.generateEventsForWeek(
            List.of(chepEarly, sannerEarly, orionDay, barbeNight, barbeLate),
            weekStart, HUB);

        long pickups = events.stream().filter(Event::isPickup).count();
        long dropoffs = events.stream().filter(e -> !e.isPickup()).count();

        // With FR-3: Chep4+Sanner1 merge at Hub. Others separate.
        // Pickups: Naruto(Tankstelle) + Chep4+Sanner1(Hub FR3) + Orion2(Hub) + BarbeNight2(Hub) + BarbeLate1(Hub) = 5
        assertEquals(5, pickups, "Should have 5 pickup events with FR-3 merging");
        assertEquals(5, dropoffs, "Should have 5 dropoff events");

        // Verify FR-3 merged event exists
        Event fr3Event = events.stream()
            .filter(Event::isPickup)
            .filter(e -> e.getStops() != null && e.getStops().size() > 1)
            .findFirst().orElse(null);
        assertNotNull(fr3Event, "Should have an FR-3 multi-stop event");
        assertEquals(5, fr3Event.getPassengerCount());
        assertEquals(5, fr3Event.getPeakPassengerCount());
        assertEquals(0, fr3Event.getPassengerDelta());
    }

    // ================================================================
    // Helpers
    // ================================================================

    private Customer createCustomer(String name, Location loc) {
        Customer c = new Customer();
        c.id = name.hashCode();
        c.name = name;
        c.latitude = loc.latitude();
        c.longitude = loc.longitude();
        return c;
    }

    private Employee createEmployee(int id, String name, Double pickupLat, Double pickupLon) {
        Employee e = new Employee();
        e.id = (long) id;
        e.name = name;
        e.employeeType = EmployeeType.SITE_EMPLOYEE;
        if (pickupLat != null && pickupLon != null) {
            e.pickupLatitude = pickupLat;
            e.pickupLongitude = pickupLon;
        }
        return e;
    }

    private ShiftDemand createShift(Customer customer, DayOfWeek day, LocalTime start, LocalTime end, List<Employee> employees) {
        ShiftDemand s = new ShiftDemand();
        s.customer = customer;
        s.dayOfWeek = day;
        s.startTime = start;
        s.endTime = end;
        s.assignedEmployees = new HashSet<>(employees);
        s.active = true;
        s.requiresReturnTrip = true;
        s.id = (customer.name + "-" + day).hashCode();
        return s;
    }
}
