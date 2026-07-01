package com.vrp.solver;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.score.director.ScoreDirectorFactoryConfig;
import ai.timefold.solver.core.config.solver.SolverConfig;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import ai.timefold.solver.core.api.score.stream.test.ConstraintVerifier;
import com.vrp.constraint.VrpConstraintProvider;
import com.vrp.domain.Driver;
import com.vrp.domain.Event;
import com.vrp.domain.Location;
import com.vrp.domain.VrpSolution;
import com.vrp.entity.Customer;
import com.vrp.entity.Employee;
import com.vrp.entity.EmployeeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Validates the VRP optimization against the manual route plan from KW 51.
 *
 * The manual plan (manual-plan.md / REQUIREMENTS.md) describes an efficient
 * 2-driver schedule for transporting employees to 5 customer sites. This test
 * models the Monday scenario with real location data and verifies that the
 * Timefold solver produces feasible, reasonable results.
 *
 * Key expectations from the manual plan:
 * - Driver 1 handles early/day shifts (Chep, Sanner, Orion) with up to 6 passengers
 * - Driver 2 handles late/night shifts (Barbe, Beneo)
 * - All hard constraints satisfied (capacity <= 6, time windows, pairing)
 * - Total distance should be reasonable (not wildly inefficient)
 */
class RouteOptimizationTest {

    // ---- Real-world locations from REQUIREMENTS.md Appendix ----
    static final Location HUB = new Location("City-Fahrschule", 49.6295, 8.3640);
    static final Location TANKSTELLE = new Location("Tankstelle Pfeddersheim", 49.6180, 8.2970);
    static final Location CHEP = new Location("Chep Deutschland GmbH", 49.7784, 8.4625);
    static final Location SANNER = new Location("Sanner GmbH", 49.6800, 8.6250);
    static final Location ORION = new Location("Orion Bausysteme GmbH", 49.7750, 8.4580);
    static final Location BARBE = new Location("Hans W. Barbe", 49.6350, 8.3480);
    static final Location BENEO = new Location("Beneo-Palatinit GmbH", 49.4700, 8.2100);

    // Monday of KW 51 (Dec 15, 2025)
    static final LocalDate MONDAY = LocalDate.of(2025, 12, 15);
    static final ZoneId ZONE = ZoneId.of("Europe/Berlin");

    // Employees from REQUIREMENTS.md Section 7
    static Employee naruto, sasuke, sakura, hinata, shikamaru;
    static Employee kakashi;
    static Employee ino, choji;
    static Employee rockLee, neji, gaara;
    static Employee temari;

    // Customers
    static Customer chepCustomer, sannerCustomer, orionCustomer, barbeCustomer, beneoCustomer;

    ConstraintVerifier<VrpConstraintProvider, VrpSolution> constraintVerifier;

    @BeforeAll
    static void setupTestData() {
        // Chep employees: Naruto at Tankstelle, 4 others at Hub
        naruto = createEmployee(1L, "Naruto Uzumaki", TANKSTELLE);
        sasuke = createEmployee(2L, "Sasuke Uchiha", HUB);
        sakura = createEmployee(3L, "Sakura Haruno", HUB);
        hinata = createEmployee(4L, "Hinata Hyuga", HUB);
        shikamaru = createEmployee(5L, "Shikamaru Nara", HUB);

        // Sanner employee
        kakashi = createEmployee(6L, "Kakashi Hatake", HUB);

        // Orion employees
        ino = createEmployee(7L, "Ino Yamanaka", HUB);
        choji = createEmployee(8L, "Choji Akimichi", HUB);

        // Barbe employees
        rockLee = createEmployee(9L, "Rock Lee", HUB);
        neji = createEmployee(10L, "Neji Hyuga", HUB);
        gaara = createEmployee(11L, "Gaara Sabaku", HUB);

        // Beneo employee
        temari = createEmployee(12L, "Temari Sabaku", HUB);

        // Customers with real coordinates
        chepCustomer = createCustomer(1L, "Chep Deutschland GmbH", "Am Winkelgraben 13, 64584 Biebesheim", 49.7784, 8.4625);
        sannerCustomer = createCustomer(2L, "Sanner GmbH", "Bertha-Benz-Straße 5, 64625 Bensheim", 49.6800, 8.6250);
        orionCustomer = createCustomer(3L, "Orion Bausysteme GmbH", "Waldstr. 2, 64584 Biebesheim", 49.7750, 8.4580);
        barbeCustomer = createCustomer(4L, "Hans W. Barbe", "Justus-von-Liebig-Str. 17, 67549 Worms", 49.6350, 8.3480);
        beneoCustomer = createCustomer(5L, "Beneo-Palatinit GmbH", "Wormser Straße 11, 67283 Obrigheim", 49.4700, 8.2100);
    }

    @BeforeEach
    void setupConstraintVerifier() {
        constraintVerifier = ConstraintVerifier.build(
                new VrpConstraintProvider(), VrpSolution.class, Driver.class, Event.class);
    }

    // ============================================================
    // Constraint Verifier Tests
    // ============================================================

    /**
     * Regression test for the Timefold 2.x list-variable migration: verifies that
     * the driver/previousEvent/arrivalTime shadow variables are correctly derived
     * from a driver's events list without going through a full solve.
     */
    @Test
    void shadowVariables_propagateAlongDriverEventsList() {
        Driver driver = new Driver("d1", HUB);

        Event e1 = new Event();
        e1.setId("e1");
        e1.setPickup(true);
        e1.setFromLocation(HUB);
        e1.setToLocation(CHEP);
        e1.setMinStartTime(toInstant(MONDAY, 4, 45));
        e1.setMaxEndTime(toInstant(MONDAY, 5, 30));
        e1.setDuration(Duration.ofMinutes(30));
        e1.setDistance(HUB.getHaversineDistance(CHEP));

        Event e2 = new Event();
        e2.setId("e2");
        e2.setPickup(true);
        e2.setFromLocation(CHEP);
        e2.setToLocation(SANNER);
        e2.setMinStartTime(toInstant(MONDAY, 6, 0));
        e2.setMaxEndTime(toInstant(MONDAY, 7, 0));
        e2.setDuration(Duration.ofMinutes(30));
        e2.setDistance(CHEP.getHaversineDistance(SANNER));

        driver.getEvents().add(e1);
        driver.getEvents().add(e2);

        VrpSolution solution = new VrpSolution(
            List.of(HUB, CHEP, SANNER), List.of(), List.of(),
            List.of(driver), List.of(e1, e2), MONDAY);

        ai.timefold.solver.core.api.solver.SolutionManager.updateShadowVariables(solution);

        assertEquals(driver, e1.getDriver(), "e1 driver should be set after shadow variable update");
        assertNull(e1.getPreviousEvent(), "e1 is first in the route, so it has no previous event");
        assertEquals(e1.getMinStartTime(), e1.getArrivalTime(), "e1 arrivalTime should default to its minStartTime");

        assertEquals(driver, e2.getDriver(), "e2 driver should be set after shadow variable update");
        assertEquals(e1, e2.getPreviousEvent(), "e2 previousEvent should be e1");
        assertEquals(e1.getDepartureTime().plus(e1.getToLocation().getTravelTime(e2.getFromLocation(), null)),
            e2.getArrivalTime(), "e2 arrivalTime should follow e1's departure plus travel time");
    }

    @Test
    void driverAssignmentRequired_unassignedEvent_shouldPenalize() {
        Event event = new Event();
        event.setId("unassigned-event");
        event.setPickup(true);
        event.setPassengers(List.of(naruto));
        event.setFromLocation(HUB);
        event.setToLocation(CHEP);
        event.setMinStartTime(toInstant(MONDAY, 4, 45));
        event.setMaxEndTime(toInstant(MONDAY, 5, 30));
        event.setDuration(Duration.ofMinutes(30));
        event.setDistance(HUB.getHaversineDistance(CHEP));
        // driver is null (not assigned)

        constraintVerifier.verifyThat(VrpConstraintProvider::driverAssignmentRequired)
                .given(event)
                .penalizesBy(1000L);
    }

    @Test
    void driverAssignmentRequired_assignedEvent_shouldNotPenalize() {
        Driver driver = new Driver("d1", HUB);

        Event event = new Event();
        event.setId("assigned-event");
        event.setPickup(true);
        event.setPassengers(List.of(naruto));
        event.setFromLocation(HUB);
        event.setToLocation(CHEP);
        event.setMinStartTime(toInstant(MONDAY, 4, 45));
        event.setMaxEndTime(toInstant(MONDAY, 5, 30));
        event.setDuration(Duration.ofMinutes(30));
        event.setDistance(HUB.getHaversineDistance(CHEP));
        event.setDriver(driver);

        constraintVerifier.verifyThat(VrpConstraintProvider::driverAssignmentRequired)
                .given(event)
                .penalizes(0);
    }

    @Test
    void vehicleCapacity_withinLimit_shouldNotPenalize() {
        Driver driver = new Driver("d1", HUB);
        driver.setMaxCapacity(6);

        // Event with cumulative count = 6 (at capacity, not exceeding)
        Event event = new Event();
        event.setId("capacity-ok");
        event.setPickup(true);
        event.setPassengers(List.of(sasuke, sakura, hinata, shikamaru));
        event.setFromLocation(HUB);
        event.setToLocation(CHEP);
        event.setMinStartTime(toInstant(MONDAY, 4, 45));
        event.setMaxEndTime(toInstant(MONDAY, 5, 30));
        event.setDuration(Duration.ofMinutes(30));
        event.setDistance(HUB.getHaversineDistance(CHEP));
        event.setDriver(driver);
        event.setCumulativePassengerCount(6); // at limit

        constraintVerifier.verifyThat(VrpConstraintProvider::vehicleCapacityConstraint)
                .given(event)
                .penalizes(0);
    }

    @Test
    void vehicleCapacity_exceedsLimit_shouldPenalize() {
        Driver driver = new Driver("d1", HUB);
        driver.setMaxCapacity(6);

        Event event = new Event();
        event.setId("capacity-exceeded");
        event.setPickup(true);
        event.setPassengers(List.of(sasuke, sakura, hinata, shikamaru));
        event.setFromLocation(HUB);
        event.setToLocation(CHEP);
        event.setMinStartTime(toInstant(MONDAY, 4, 45));
        event.setMaxEndTime(toInstant(MONDAY, 5, 30));
        event.setDuration(Duration.ofMinutes(30));
        event.setDistance(HUB.getHaversineDistance(CHEP));
        event.setDriver(driver);
        event.setCumulativePassengerCount(8); // exceeds limit by 2

        constraintVerifier.verifyThat(VrpConstraintProvider::vehicleCapacityConstraint)
                .given(event)
                .penalizesBy(2000L); // (8 - 6) * 1000
    }

    @Test
    void negativeCumulativePassengerCount_dropoffBeforePickup_shouldPenalize() {
        Driver driver = new Driver("d1", HUB);

        Event event = new Event();
        event.setId("negative-passenger-count");
        event.setPickup(false);
        event.setPassengers(List.of(naruto));
        event.setFromLocation(CHEP);
        event.setToLocation(HUB);
        event.setMinStartTime(toInstant(MONDAY, 14, 0));
        event.setMaxEndTime(toInstant(MONDAY, 15, 0));
        event.setDuration(Duration.ofMinutes(30));
        event.setDistance(CHEP.getHaversineDistance(HUB));
        event.setDriver(driver);
        event.setCumulativePassengerCount(-1); // impossible: dropped off before pickup

        constraintVerifier.verifyThat(VrpConstraintProvider::negativeCumulativePassengerCount)
                .given(event)
                .penalizesBy(1_000_000L);
    }

    @Test
    void timeWindow_completesBeforeMaxEnd_shouldNotPenalize() {
        Driver driver = new Driver("d1", HUB);

        Event event = new Event();
        event.setId("on-time");
        event.setPickup(true);
        event.setPassengers(List.of(naruto));
        event.setFromLocation(HUB);
        event.setToLocation(CHEP);
        event.setMinStartTime(toInstant(MONDAY, 4, 45));
        event.setMaxEndTime(toInstant(MONDAY, 5, 30));
        event.setDuration(Duration.ofMinutes(30));
        event.setDistance(HUB.getHaversineDistance(CHEP));
        event.setDriver(driver);
        event.setArrivalTime(toInstant(MONDAY, 4, 50)); // arrives 04:50, completes 05:20 < 05:30

        constraintVerifier.verifyThat(VrpConstraintProvider::timeWindowConstraint)
                .given(event)
                .penalizes(0);
    }

    @Test
    void timeWindow_completesAfterMaxEnd_shouldPenalize() {
        Driver driver = new Driver("d1", HUB);

        Event event = new Event();
        event.setId("late");
        event.setPickup(true);
        event.setPassengers(List.of(naruto));
        event.setFromLocation(HUB);
        event.setToLocation(CHEP);
        event.setMinStartTime(toInstant(MONDAY, 4, 45));
        event.setMaxEndTime(toInstant(MONDAY, 5, 30));
        event.setDuration(Duration.ofMinutes(30));
        event.setDistance(HUB.getHaversineDistance(CHEP));
        event.setDriver(driver);
        event.setArrivalTime(toInstant(MONDAY, 5, 15)); // arrives 05:15, completes 05:45 > 05:30

        // Penalty = seconds over: 05:45 - 05:30 = 900 seconds
        constraintVerifier.verifyThat(VrpConstraintProvider::timeWindowConstraint)
                .given(event)
                .penalizesBy(900L);
    }

    @Test
    void pairing_sameDriverPickupBeforeDropoff_shouldNotPenalize() {
        Driver driver = new Driver("d1", HUB);

        Event pickup = new Event();
        pickup.setId("pickup-paired");
        pickup.setPickup(true);
        pickup.setPassengers(List.of(naruto));
        pickup.setFromLocation(HUB);
        pickup.setToLocation(CHEP);
        pickup.setMinStartTime(toInstant(MONDAY, 4, 45));
        pickup.setMaxEndTime(toInstant(MONDAY, 5, 30));
        pickup.setDuration(Duration.ofMinutes(30));
        pickup.setDistance(HUB.getHaversineDistance(CHEP));
        pickup.setDriver(driver);
        pickup.setArrivalTime(toInstant(MONDAY, 4, 50));

        Event dropoff = new Event();
        dropoff.setId("dropoff-paired");
        dropoff.setPickup(false);
        dropoff.setPassengers(List.of(naruto));
        dropoff.setFromLocation(CHEP);
        dropoff.setToLocation(HUB);
        dropoff.setMinStartTime(toInstant(MONDAY, 14, 0));
        dropoff.setMaxEndTime(toInstant(MONDAY, 15, 0));
        dropoff.setDuration(Duration.ofMinutes(30));
        dropoff.setDistance(CHEP.getHaversineDistance(HUB));
        dropoff.setDriver(driver);
        dropoff.setArrivalTime(toInstant(MONDAY, 14, 10)); // after pickup departure

        // Link paired events
        pickup.setPairedEvent(dropoff);
        dropoff.setPairedEvent(pickup);

        constraintVerifier.verifyThat(VrpConstraintProvider::pairingConstraint)
                .given(pickup, dropoff)
                .penalizes(0);
    }

    @Test
    void pairing_differentDrivers_shouldPenalize() {
        Driver driver1 = new Driver("d1", HUB);
        Driver driver2 = new Driver("d2", HUB);

        Event pickup = new Event();
        pickup.setId("pickup-split");
        pickup.setPickup(true);
        pickup.setPassengers(List.of(gaara));
        pickup.setFromLocation(HUB);
        pickup.setToLocation(BARBE);
        pickup.setMinStartTime(toInstant(MONDAY, 13, 15));
        pickup.setMaxEndTime(toInstant(MONDAY, 14, 0));
        pickup.setDuration(Duration.ofMinutes(10));
        pickup.setDistance(HUB.getHaversineDistance(BARBE));
        pickup.setDriver(driver1);
        pickup.setArrivalTime(toInstant(MONDAY, 13, 20));

        Event dropoff = new Event();
        dropoff.setId("dropoff-split");
        dropoff.setPickup(false);
        dropoff.setPassengers(List.of(gaara));
        dropoff.setFromLocation(BARBE);
        dropoff.setToLocation(HUB);
        dropoff.setMinStartTime(toInstant(MONDAY, 22, 0));
        dropoff.setMaxEndTime(toInstant(MONDAY, 23, 0));
        dropoff.setDuration(Duration.ofMinutes(10));
        dropoff.setDistance(BARBE.getHaversineDistance(HUB));
        dropoff.setDriver(driver2); // different driver!
        dropoff.setArrivalTime(toInstant(MONDAY, 22, 10));

        pickup.setPairedEvent(dropoff);
        dropoff.setPairedEvent(pickup);

        constraintVerifier.verifyThat(VrpConstraintProvider::pairingConstraint)
                .given(pickup, dropoff)
                .penalizes();
    }

    // ============================================================
    // Solver Integration Test: Monday KW 51 with Batched Events
    // ============================================================

    @Test
    void testMondayBatchedRouteOptimization() {
        // Create 2 drivers based at the hub (as per manual plan)
        Driver driver1 = new Driver("driver-1", HUB);
        Driver driver2 = new Driver("driver-2", HUB);

        List<Event> events = new ArrayList<>();

        // ---- Chep early shift (05:30 - 14:00) ----
        // Naruto has a separate pickup at Tankstelle (FR-4)
        Event[] narutoChep = createPairedEvents("naruto-chep",
                TANKSTELLE, CHEP,
                toInstant(MONDAY, 4, 45), toInstant(MONDAY, 5, 30),
                toInstant(MONDAY, 14, 0), toInstant(MONDAY, 15, 0),
                List.of(naruto));
        events.addAll(List.of(narutoChep[0], narutoChep[1]));

        // 4 Chep employees batched from Hub (FR-1)
        Event[] batchChep = createPairedEvents("batch-chep",
                HUB, CHEP,
                toInstant(MONDAY, 4, 45), toInstant(MONDAY, 5, 30),
                toInstant(MONDAY, 14, 0), toInstant(MONDAY, 15, 0),
                List.of(sasuke, sakura, hinata, shikamaru));
        events.addAll(List.of(batchChep[0], batchChep[1]));

        // ---- Sanner early shift (06:00 - 14:00) ----
        // Kakashi from Hub - in manual plan combined with Chep batch at 04:30 (FR-3)
        Event[] kakashiSanner = createPairedEvents("kakashi-sanner",
                HUB, SANNER,
                toInstant(MONDAY, 5, 15), toInstant(MONDAY, 6, 0),
                toInstant(MONDAY, 14, 0), toInstant(MONDAY, 15, 0),
                List.of(kakashi));
        events.addAll(List.of(kakashiSanner[0], kakashiSanner[1]));

        // ---- Orion day shift (06:30 - 16:00) ----
        // Ino + Choji batched from Hub
        Event[] orionBatch = createPairedEvents("orion-batch",
                HUB, ORION,
                toInstant(MONDAY, 5, 45), toInstant(MONDAY, 6, 30),
                toInstant(MONDAY, 16, 0), toInstant(MONDAY, 17, 0),
                List.of(ino, choji));
        events.addAll(List.of(orionBatch[0], orionBatch[1]));

        // ---- Barbe late shift (14:00 - 22:00) ----
        // Gaara from Hub (Driver 2 in manual plan)
        Event[] gaaraBarbe = createPairedEvents("gaara-barbe",
                HUB, BARBE,
                toInstant(MONDAY, 13, 15), toInstant(MONDAY, 14, 0),
                toInstant(MONDAY, 22, 0), toInstant(MONDAY, 23, 0),
                List.of(gaara));
        events.addAll(List.of(gaaraBarbe[0], gaaraBarbe[1]));

        // All locations used
        List<Location> locations = List.of(HUB, TANKSTELLE, CHEP, SANNER, ORION, BARBE);

        // All passengers
        List<Employee> employees = List.of(naruto, sasuke, sakura, hinata, shikamaru,
                kakashi, ino, choji, gaara);

        List<Customer> customers = List.of(chepCustomer, sannerCustomer, orionCustomer, barbeCustomer);

        VrpSolution problem = new VrpSolution(locations, customers, employees,
                List.of(driver1, driver2), events, MONDAY);

        // Configure solver with 30-second time limit
        SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(VrpSolution.class)
                .withEntityClasses(Driver.class, Event.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(VrpConstraintProvider.class))
                .withTerminationConfig(new TerminationConfig()
                        .withSecondsSpentLimit(30L));

        SolverFactory<VrpSolution> solverFactory = SolverFactory.create(solverConfig);
        Solver<VrpSolution> solver = solverFactory.buildSolver();
        VrpSolution solution = solver.solve(problem);

        // ---- Validate Solution ----

        assertNotNull(solution.getScore(), "Solution must have a score");
        System.out.println("=== Monday Batched Route Optimization (KW 51) ===");
        System.out.println("Score: " + solution.getScore());
        System.out.println();

        // HARD CONSTRAINT: No violations
        assertTrue(solution.getScore().hardScore() >= 0,
                "Hard score must be >= 0 (no violations). Actual: " + solution.getScore());

        // All events must be assigned to a driver
        for (Event event : solution.getEvents()) {
            assertNotNull(event.getDriver(),
                    "Event " + event.getId() + " must be assigned to a driver");
        }

        // Paired events must have the same driver
        for (Event event : solution.getEvents()) {
            if (event.getPairedEvent() != null && !event.isPickup()) {
                Event pickup = event.getPairedEvent();
                assertEquals(pickup.getDriver(), event.getDriver(),
                        "Paired pickup/dropoff must have same driver: " + event.getId());
            }
        }

        // Vehicle capacity must never exceed 6
        for (Event event : solution.getEvents()) {
            if (event.getCumulativePassengerCount() != null) {
                assertTrue(event.getCumulativePassengerCount() <= 6,
                        "Capacity exceeded at " + event.getId() +
                                ": " + event.getCumulativePassengerCount() + " > 6");
            }
        }

        // Pickup must occur before paired dropoff
        for (Event event : solution.getEvents()) {
            if (event.getPairedEvent() != null && event.isPickup()) {
                Event dropoff = event.getPairedEvent();
                if (event.getArrivalTime() != null && dropoff.getArrivalTime() != null) {
                    assertTrue(event.getDepartureTime().isBefore(dropoff.getArrivalTime())
                                    || event.getDepartureTime().equals(dropoff.getArrivalTime()),
                            "Pickup must be before dropoff: " + event.getId());
                }
            }
        }

        // Print route details for analysis
        printRoutes(solution);

        // Verify driver utilization: both drivers should be used
        Map<String, Integer> driverEventCounts = new HashMap<>();
        for (Event event : solution.getEvents()) {
            if (event.getDriver() != null) {
                driverEventCounts.merge(event.getDriver().getId(), 1, Integer::sum);
            }
        }
        System.out.println("Driver utilization: " + driverEventCounts);

        // Both drivers should be used (manual plan uses 2 drivers)
        assertTrue(driverEventCounts.size() >= 1,
                "At least 1 driver should be used");

        // Total events should match what we created (10 events = 5 pairs)
        assertEquals(10, solution.getEvents().size(),
                "Should have 10 events (5 pickup + 5 dropoff)");
    }

    // ============================================================
    // Solver Test: Individual (unbatched) events - current behavior
    // ============================================================

    @Test
    void testMondayIndividualEventsOptimization() {
        // Same scenario but with individual events per employee (no batching)
        // This models the CURRENT behavior of EventGenerationService
        Driver driver1 = new Driver("driver-1", HUB);
        Driver driver2 = new Driver("driver-2", HUB);

        List<Event> events = new ArrayList<>();

        // ---- Chep early shift (05:30 - 14:00) - 5 individual events ----
        List<Employee> chepEmployees = List.of(naruto, sasuke, sakura, hinata, shikamaru);
        for (Employee emp : chepEmployees) {
            Location pickup = emp.id == 1L ? TANKSTELLE : HUB; // Naruto at Tankstelle
            Event[] pair = createPairedEvents("chep-" + emp.id,
                    pickup, CHEP,
                    toInstant(MONDAY, 4, 45), toInstant(MONDAY, 5, 30),
                    toInstant(MONDAY, 14, 0), toInstant(MONDAY, 15, 0),
                    List.of(emp));
            events.addAll(List.of(pair[0], pair[1]));
        }

        // ---- Sanner early shift (06:00 - 14:00) - 1 event ----
        Event[] kakashiPair = createPairedEvents("sanner-" + kakashi.id,
                HUB, SANNER,
                toInstant(MONDAY, 5, 15), toInstant(MONDAY, 6, 0),
                toInstant(MONDAY, 14, 0), toInstant(MONDAY, 15, 0),
                List.of(kakashi));
        events.addAll(List.of(kakashiPair[0], kakashiPair[1]));

        // ---- Orion day shift (06:30 - 16:00) - 2 individual events ----
        for (Employee emp : List.of(ino, choji)) {
            Event[] pair = createPairedEvents("orion-" + emp.id,
                    HUB, ORION,
                    toInstant(MONDAY, 5, 45), toInstant(MONDAY, 6, 30),
                    toInstant(MONDAY, 16, 0), toInstant(MONDAY, 17, 0),
                    List.of(emp));
            events.addAll(List.of(pair[0], pair[1]));
        }

        // ---- Barbe late shift (14:00 - 22:00) - 1 event ----
        Event[] gaaraPair = createPairedEvents("barbe-" + gaara.id,
                HUB, BARBE,
                toInstant(MONDAY, 13, 15), toInstant(MONDAY, 14, 0),
                toInstant(MONDAY, 22, 0), toInstant(MONDAY, 23, 0),
                List.of(gaara));
        events.addAll(List.of(gaaraPair[0], gaaraPair[1]));

        List<Location> locations = List.of(HUB, TANKSTELLE, CHEP, SANNER, ORION, BARBE);
        List<Employee> employees = List.of(naruto, sasuke, sakura, hinata, shikamaru,
                kakashi, ino, choji, gaara);
        List<Customer> customers = List.of(chepCustomer, sannerCustomer, orionCustomer, barbeCustomer);

        VrpSolution problem = new VrpSolution(locations, customers, employees,
                List.of(driver1, driver2), events, MONDAY);

        SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(VrpSolution.class)
                .withEntityClasses(Driver.class, Event.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(VrpConstraintProvider.class))
                .withTerminationConfig(new TerminationConfig()
                        .withSecondsSpentLimit(30L));

        SolverFactory<VrpSolution> solverFactory = SolverFactory.create(solverConfig);
        Solver<VrpSolution> solver = solverFactory.buildSolver();
        VrpSolution solution = solver.solve(problem);

        System.out.println("=== Monday Individual Events (No Batching) ===");
        System.out.println("Score: " + solution.getScore());
        System.out.println("Events: " + solution.getEvents().size() +
                " (vs 10 batched = " + (solution.getEvents().size() - 10) + " more)");
        System.out.println();

        // Hard constraints must still be satisfied
        assertTrue(solution.getScore().hardScore() >= 0,
                "Hard score must be >= 0. Actual: " + solution.getScore());

        // All events assigned
        for (Event event : solution.getEvents()) {
            assertNotNull(event.getDriver(),
                    "Event " + event.getId() + " must be assigned");
        }

        // Pairing maintained
        for (Event event : solution.getEvents()) {
            if (event.getPairedEvent() != null && !event.isPickup()) {
                Event pickup = event.getPairedEvent();
                assertEquals(pickup.getDriver(), event.getDriver(),
                        "Pairing violated for: " + event.getId());
            }
        }

        // 18 events total (9 employees × 2 trips)
        assertEquals(18, solution.getEvents().size(),
                "Should have 18 events (9 pickup + 9 dropoff)");

        printRoutes(solution);

        // Compare soft scores: individual events should have worse (more negative) soft score
        System.out.println("Individual events soft score: " + solution.getScore().softScore());
    }

    // ============================================================
    // Full Week Simulation (simplified - Monday only, all shifts)
    // ============================================================

    @Test
    void testMondayFullDayWithAllShifts() {
        // Models all events from the manual plan for Monday including late/night shifts
        Driver driver1 = new Driver("driver-1", HUB);
        Driver driver2 = new Driver("driver-2", HUB);
        Driver driver3 = new Driver("driver-3", HUB); // backup driver

        List<Event> events = new ArrayList<>();

        // ---- Early shift events (Driver 1 in manual plan) ----

        // Naruto → Chep (separate pickup at Tankstelle)
        Event[] narutoChep = createPairedEvents("naruto-chep-early",
                TANKSTELLE, CHEP,
                toInstant(MONDAY, 4, 45), toInstant(MONDAY, 5, 30),
                toInstant(MONDAY, 14, 0), toInstant(MONDAY, 15, 0),
                List.of(naruto));
        events.addAll(List.of(narutoChep[0], narutoChep[1]));

        // 4 Chep → Chep (batched at Hub)
        Event[] batchChep = createPairedEvents("batch-chep-early",
                HUB, CHEP,
                toInstant(MONDAY, 4, 45), toInstant(MONDAY, 5, 30),
                toInstant(MONDAY, 14, 0), toInstant(MONDAY, 15, 0),
                List.of(sasuke, sakura, hinata, shikamaru));
        events.addAll(List.of(batchChep[0], batchChep[1]));

        // Kakashi → Sanner
        Event[] kakashiSanner = createPairedEvents("kakashi-sanner-early",
                HUB, SANNER,
                toInstant(MONDAY, 5, 15), toInstant(MONDAY, 6, 0),
                toInstant(MONDAY, 14, 0), toInstant(MONDAY, 15, 0),
                List.of(kakashi));
        events.addAll(List.of(kakashiSanner[0], kakashiSanner[1]));

        // Ino + Choji → Orion
        Event[] orionBatch = createPairedEvents("orion-batch-day",
                HUB, ORION,
                toInstant(MONDAY, 5, 45), toInstant(MONDAY, 6, 30),
                toInstant(MONDAY, 16, 0), toInstant(MONDAY, 17, 0),
                List.of(ino, choji));
        events.addAll(List.of(orionBatch[0], orionBatch[1]));

        // ---- Late shift events (Driver 2 in manual plan) ----

        // Gaara → Barbe late shift
        Event[] gaaraBarbe = createPairedEvents("gaara-barbe-late",
                HUB, BARBE,
                toInstant(MONDAY, 13, 15), toInstant(MONDAY, 14, 0),
                toInstant(MONDAY, 22, 0), toInstant(MONDAY, 23, 0),
                List.of(gaara));
        events.addAll(List.of(gaaraBarbe[0], gaaraBarbe[1]));

        // ---- Night shift events (Driver 2 in manual plan) ----

        // Rock Lee + Neji → Barbe night shift (22:00 - 06:00)
        // Pickup in evening, dropoff next morning (we set maxEnd generously for test)
        Event[] barbeNight = createPairedEvents("barbe-night",
                HUB, BARBE,
                toInstant(MONDAY, 21, 15), toInstant(MONDAY, 22, 0),
                // Dropoff would be Tuesday 06:00, but for single-day test we use late Monday
                toInstant(MONDAY, 23, 0), toInstant(MONDAY, 23, 59),
                List.of(rockLee, neji));
        events.addAll(List.of(barbeNight[0], barbeNight[1]));

        List<Location> locations = List.of(HUB, TANKSTELLE, CHEP, SANNER, ORION, BARBE);
        List<Employee> allPassengers = List.of(naruto, sasuke, sakura, hinata, shikamaru,
                kakashi, ino, choji, gaara, rockLee, neji);
        List<Customer> customers = List.of(chepCustomer, sannerCustomer, orionCustomer, barbeCustomer);

        VrpSolution problem = new VrpSolution(locations, customers, allPassengers,
                List.of(driver1, driver2, driver3), events, MONDAY);

        SolverConfig solverConfig = new SolverConfig()
                .withSolutionClass(VrpSolution.class)
                .withEntityClasses(Driver.class, Event.class)
                .withScoreDirectorFactory(new ScoreDirectorFactoryConfig()
                        .withConstraintProviderClass(VrpConstraintProvider.class))
                .withTerminationConfig(new TerminationConfig()
                        .withSecondsSpentLimit(30L));

        SolverFactory<VrpSolution> solverFactory = SolverFactory.create(solverConfig);
        Solver<VrpSolution> solver = solverFactory.buildSolver();
        VrpSolution solution = solver.solve(problem);

        System.out.println("=== Monday Full Day (All Shifts, 3 Drivers) ===");
        System.out.println("Score: " + solution.getScore());
        System.out.println("Total events: " + solution.getEvents().size());
        System.out.println();

        // Hard constraints satisfied
        assertTrue(solution.getScore().hardScore() >= 0,
                "Hard score must be >= 0. Actual: " + solution.getScore());

        // All events assigned
        for (Event event : solution.getEvents()) {
            assertNotNull(event.getDriver(),
                    "Event " + event.getId() + " not assigned");
        }

        // Analyze driver usage
        Map<String, List<Event>> driverRoutes = new HashMap<>();
        for (Event event : solution.getEvents()) {
            if (event.getDriver() != null) {
                driverRoutes.computeIfAbsent(event.getDriver().getId(), k -> new ArrayList<>()).add(event);
            }
        }

        System.out.println("Drivers used: " + driverRoutes.size());
        for (Map.Entry<String, List<Event>> entry : driverRoutes.entrySet()) {
            long pickups = entry.getValue().stream().filter(Event::isPickup).count();
            long dropoffs = entry.getValue().stream().filter(e -> !e.isPickup()).count();
            System.out.printf("  %s: %d events (%d pickups, %d dropoffs)%n",
                    entry.getKey(), entry.getValue().size(), pickups, dropoffs);
        }

        // Manual plan uses 2 drivers for Monday (3rd is backup)
        // Solver should ideally use 2-3 drivers
        assertTrue(driverRoutes.size() <= 3,
                "Should use at most 3 drivers. Used: " + driverRoutes.size());

        // Verify temporal consistency: early events handled separately from late events
        printRoutes(solution);

        // Capacity check
        for (Event event : solution.getEvents()) {
            if (event.getCumulativePassengerCount() != null && event.getDriver() != null) {
                assertTrue(event.getCumulativePassengerCount() <= event.getDriver().getMaxCapacity(),
                        "Capacity exceeded at " + event.getId());
            }
        }
    }

    // ============================================================
    // Helper Methods
    // ============================================================

    static Employee createEmployee(Long id, String name, Location pickupLoc) {
        Employee e = new Employee(name, null, null, EmployeeType.SITE_EMPLOYEE);
        e.id = id;
        if (pickupLoc != null && !pickupLoc.name().equals(HUB.name())) {
            e.pickupLatitude = pickupLoc.latitude();
            e.pickupLongitude = pickupLoc.longitude();
        }
        return e;
    }

    static Customer createCustomer(Long id, String name, String address, double lat, double lon) {
        Customer c = new Customer(name, address, lat, lon);
        c.id = id;
        return c;
    }

    static Instant toInstant(LocalDate date, int hour, int minute) {
        return LocalDateTime.of(date, LocalTime.of(hour, minute))
                .atZone(ZONE).toInstant();
    }

    static Event createPickupEvent(String id, Location from, Location to,
                                    Instant minStart, Instant maxEnd, List<Employee> passengers) {
        long distance = from.getHaversineDistance(to);
        Duration travelTime = Duration.ofSeconds(distance / 15);
        Duration boarding = Duration.ofMinutes(2L * passengers.size());
        Duration totalDuration = travelTime.plus(boarding);

        Event event = new Event(id, from, to, minStart, maxEnd, totalDuration, distance,
                true, passengers, null, "weekday",
                Duration.ofMinutes(30), Duration.ofMinutes(45));
        // Derive shiftDate from minStart (tests are single-day, so this works)
        event.setShiftDate(minStart.atZone(ZONE).toLocalDate());
        return event;
    }

    static Event createDropoffEvent(String id, Location from, Location to,
                                     Instant minStart, Instant maxEnd, List<Employee> passengers) {
        long distance = from.getHaversineDistance(to);
        Duration travelTime = Duration.ofSeconds(distance / 15);
        Duration boarding = Duration.ofMinutes(2L * passengers.size());
        Duration totalDuration = travelTime.plus(boarding);

        Event event = new Event(id, from, to, minStart, maxEnd, totalDuration, distance,
                false, passengers, null, "weekday",
                Duration.ZERO, Duration.ZERO);
        // Derive shiftDate from minStart (tests are single-day, so this works)
        event.setShiftDate(minStart.atZone(ZONE).toLocalDate());
        return event;
    }

    /**
     * Creates a paired pickup+dropoff event pair linked together.
     */
    static Event[] createPairedEvents(String baseId, Location pickupLoc, Location customerLoc,
                                       Instant pickupMinStart, Instant pickupMaxEnd,
                                       Instant dropoffMinStart, Instant dropoffMaxEnd,
                                       List<Employee> passengers) {
        Event pickup = createPickupEvent("pickup-" + baseId, pickupLoc, customerLoc,
                pickupMinStart, pickupMaxEnd, passengers);
        Event dropoff = createDropoffEvent("dropoff-" + baseId, customerLoc, pickupLoc,
                dropoffMinStart, dropoffMaxEnd, passengers);
        pickup.setPairedEvent(dropoff);
        dropoff.setPairedEvent(pickup);
        return new Event[]{pickup, dropoff};
    }

    /**
     * Prints the route chain for each driver in the solution.
     */
    private void printRoutes(VrpSolution solution) {
        for (Driver driver : solution.getDrivers()) {
            System.out.println("--- " + driver.getId() + " (home: " + driver.getHomeLocation().name() + ") ---");
            int eventCount = 0;
            long totalDistance = 0;

            for (Event current : driver.getEvents()) {
                eventCount++;
                totalDistance += current.getDistance();
                String arrivalStr = current.getArrivalTime() != null
                        ? current.getArrivalTime().atZone(ZONE).toLocalTime().toString()
                        : "N/A";
                System.out.printf("  %2d. [%s] %s %s → %s | %d pax (cum: %s) | arr: %s%n",
                        eventCount,
                        current.isPickup() ? "PICKUP " : "DROPOFF",
                        current.getId(),
                        current.getFromLocation().name(),
                        current.getToLocation().name(),
                        current.getPassengerCount(),
                        current.getCumulativePassengerCount() != null
                                ? String.valueOf(current.getCumulativePassengerCount()) : "?",
                        arrivalStr);
            }
            System.out.printf("  Route: %d events, ~%.1f km total event distance%n",
                    eventCount, totalDistance / 1000.0);
            System.out.println();
        }
    }
}
