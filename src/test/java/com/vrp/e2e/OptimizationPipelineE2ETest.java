package com.vrp.e2e;

import com.vrp.domain.Driver;
import com.vrp.domain.Event;
import com.vrp.domain.Location;
import com.vrp.domain.VrpSolution;
import com.vrp.service.SolverService;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The centerpiece: the ENTIRE optimization pipeline, soup to nuts, over real data and the real
 * road network — no mocks anywhere.
 *
 * <p>Real H2 (seeded by DataBootstrap at boot) → real REST {@code POST /api/solver/solve} →
 * EventGenerationService → REAL GraphHopper routing → real Timefold solver → real
 * {@code GET /api/solver/{jobId}/solution}. We drive it exactly as the browser does (HTTP),
 * poll to completion, then assert the solution is genuinely feasible.
 *
 * <p>Gated on {@code GRAPHHOPPER_PBF}: requires the operator-provided OSM extract (the
 * {@code real-routing} Maven profile builds the cache before this JVM boots). Fails loudly rather
 * than silently falling back to Haversine.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "GRAPHHOPPER_PBF", matches = ".+")
class OptimizationPipelineE2ETest extends RealRoutingTestBase {

    @Inject
    SolverService solverService;

    @Test
    void realRoutingIsActuallyUsedNotHaversineFallback() {
        // A physical invariant only real road routing can satisfy: driving distance between two
        // points is strictly greater than the great-circle (straight-line) distance.
        // Use an intra-Worms leg (hub -> Barbe) that is guaranteed to be inside the Rheinland-Pfalz
        // OSM extract, so this proof holds even if some customers sit across the Hessen border.
        Location hub = Location.HUB;
        Location barbe = new Location("Barbe", 49.6350, 8.3480);

        long roadDistance = hub.getDistanceTo(barbe, graphHopperService.getGraphHopper());
        long straightLine = hub.getHaversineDistance(barbe);

        assertTrue(roadDistance > straightLine,
            "road distance (" + roadDistance + "m) must exceed straight-line (" + straightLine
            + "m) — proves GraphHopper real routing, not the Haversine fallback");
    }

    @Test
    void fullPipelineProducesFeasibleAssignedSolution() {
        // 1. Kick off optimization through the real HTTP endpoint (form-encoded, as the UI does).
        given()
            .contentType(ContentType.URLENC)
            .formParam("date", "2026-07-06")   // a Monday
        .when()
            .post("/api/solver/solve")
        .then()
            .statusCode(200);

        // 2. The POST returns HTML that does not render the jobId; /debug exposes it as JSON.
        String jobIdStr = given()
            .when().get("/api/solver/debug")
            .then().statusCode(200)
                .body("currentJobId", notNullValue())
            .extract().jsonPath().getString("currentJobId");
        UUID jobId = UUID.fromString(jobIdStr);

        // 3. Poll the real status endpoint until the solver terminates.
        await("solver to finish")
            .atMost(Duration.ofMinutes(4))
            .pollInterval(Duration.ofSeconds(2))
            .until(() ->
                "NOT_SOLVING".equals(
                    given().pathParam("jobId", jobIdStr)
                        .when().get("/api/solver/{jobId}/status")
                        .then().statusCode(200)
                        .extract().jsonPath().getString("status")));

        // 4. The JSON solution endpoint returns the real serialized solution.
        given()
            .pathParam("jobId", jobIdStr)
        .when()
            .get("/api/solver/{jobId}/solution")
        .then()
            .statusCode(200)
            .contentType(ContentType.JSON);

        // 5. Rigorous assertions on the real solution object (score serializes as a string over JSON,
        //    so we read the object directly for a reliable numeric check).
        VrpSolution solution = solverService.getSolution(jobId);
        assertNotNull(solution, "final solution present");
        assertNotNull(solution.getScore(), "solution scored");
        assertNotNull(solution.getEvents(), "events present");
        assertFalse(solution.getEvents().isEmpty(), "pipeline generated events from the seeded week");

        // Feasibility: hardScore >= 0 means EVERY hard constraint is satisfied — driver assignment,
        // capacity (cumulative + peak), time windows, pickup/dropoff pairing, and all ArbZG limits.
        long hard = solution.getScore().hardScore();
        assertTrue(hard >= 0,
            "solution must be feasible (hardScore >= 0). Actual score: " + solution.getScore()
            + ". If negative on the full week, raise the solver spent-limit or reduce scope.");

        // Every event is assigned to a driver.
        for (Event e : solution.getEvents()) {
            assertNotNull(e.getDriver(), "event " + e.getId() + " assigned to a driver");
            assertNotNull(e.getArrivalTime(), "event " + e.getId() + " has an arrival time");
        }

        // Real distance was accumulated across the routes.
        long totalDistance = 0;
        int assignedDrivers = 0;
        for (Driver d : solution.getDrivers()) {
            long driverDistance = solution.getEvents().stream()
                .filter(e -> d.equals(e.getDriver()))
                .mapToLong(Event::getDistance)
                .sum();
            if (driverDistance > 0) {
                assignedDrivers++;
            }
            totalDistance += driverDistance;
        }
        assertTrue(totalDistance > 0, "total routed distance > 0");
        assertTrue(assignedDrivers > 0, "at least one driver has a non-empty route");

        // 6. HTML results page renders (smoke check of the output leg).
        given()
            .when().get("/api/solver/results")
            .then().statusCode(200);
    }
}
