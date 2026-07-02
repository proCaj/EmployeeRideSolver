package com.vrp.e2e;

import com.graphhopper.GraphHopper;
import com.vrp.domain.Location;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Exercises the REAL embedded GraphHopper engine over the actual OSM road network for many
 * real domain coordinate pairs (the hub, the five customer sites, and the alternate pickup).
 * Proves that routing is genuine — every leg's road distance meets or exceeds the great-circle
 * lower bound, durations are positive, and the routing cache is self-consistent.
 *
 * <p>Gated on {@code GRAPHHOPPER_PBF}; fails loudly if the engine never initializes.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "GRAPHHOPPER_PBF", matches = ".+")
class GraphHopperRealRoutingE2ETest extends RealRoutingTestBase {

    // Real seeded locations (from DataBootstrap).
    private static final Location HUB = Location.HUB;                                   // City-Fahrschule, Worms
    private static final Location TANKSTELLE = new Location("Tankstelle", 49.6180, 8.2970);
    private static final Location CHEP = new Location("Chep", 49.7784, 8.4625);
    private static final Location SANNER = new Location("Sanner", 49.6800, 8.6250);
    private static final Location ORION = new Location("Orion", 49.7750, 8.4580);
    private static final Location BARBE = new Location("Barbe", 49.6350, 8.3480);
    private static final Location BENEO = new Location("Beneo", 49.4700, 8.2100);

    private record Leg(Location from, Location to) {}

    private static final List<Leg> LEGS = List.of(
        new Leg(HUB, CHEP), new Leg(HUB, SANNER), new Leg(HUB, ORION),
        new Leg(HUB, BARBE), new Leg(HUB, BENEO), new Leg(HUB, TANKSTELLE),
        new Leg(TANKSTELLE, CHEP), new Leg(CHEP, ORION), new Leg(SANNER, BENEO),
        new Leg(BARBE, BENEO), new Leg(CHEP, HUB), new Leg(BENEO, HUB));

    @Test
    void everyLegRoutesOverRealRoadNetwork() {
        GraphHopper hopper = graphHopperService.getGraphHopper();

        for (Leg leg : LEGS) {
            String label = leg.from().name() + " -> " + leg.to().name();

            long road = leg.from().getDistanceTo(leg.to(), hopper);
            long straightLine = leg.from().getHaversineDistance(leg.to());
            Duration time = leg.from().getTravelTime(leg.to(), hopper);

            assertTrue(road > 0, "road distance > 0 for " + label);
            assertTrue(time.getSeconds() > 0, "travel time > 0 for " + label);
            assertTrue(road >= straightLine,
                "road distance (" + road + "m) >= straight-line (" + straightLine + "m) for " + label);

            // Implied average speed must be physically plausible for a car.
            double kmh = (road / 1000.0) / (time.getSeconds() / 3600.0);
            assertTrue(kmh > 3 && kmh < 160, "plausible average speed (" + kmh + " km/h) for " + label);
        }
    }

    @Test
    void routingCacheIsConsistentAndSymmetricScale() {
        GraphHopper hopper = graphHopperService.getGraphHopper();

        // Repeated queries return identical cached results.
        long first = HUB.getDistanceTo(SANNER, hopper);
        long second = HUB.getDistanceTo(SANNER, hopper);
        assertEquals(first, second, "cache returns a stable distance");

        // Forward and reverse road distances should be the same order of magnitude (road networks
        // are rarely perfectly symmetric, but must not differ wildly).
        long forward = HUB.getDistanceTo(CHEP, hopper);
        long reverse = CHEP.getDistanceTo(HUB, hopper);
        double ratio = (double) forward / reverse;
        assertTrue(ratio > 0.7 && ratio < 1.4,
            "forward/reverse distance ratio sane (" + ratio + ")");
    }

    @Test
    void identicalLocationHasZeroDistance() {
        GraphHopper hopper = graphHopperService.getGraphHopper();
        assertEquals(0L, HUB.getDistanceTo(new Location("hub-copy", 49.6295, 8.3640), hopper),
            "same coordinates route to zero distance");
    }
}
