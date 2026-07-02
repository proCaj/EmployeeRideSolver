package com.vrp.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.vrp.service.GoogleRoutesService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

/**
 * LIVE calls to the real Google Routes API — the un-mocked source of the snapshot fixtures that
 * {@link com.vrp.service.GoogleRoutesSnapshotTest} replays.
 *
 * <p>Gated on {@code GOOGLE_ROUTES_LIVE=true} so ordinary {@code mvn test} / CI never spends money;
 * enable it (with {@code GOOGLE_MAPS_API_KEY} set) to prove the real endpoint and to refresh
 * snapshots. It requests field mask {@code *} — the entire response — and asserts the full payload
 * is well-formed, using the same invariants the snapshot test enforces offline.
 */
@QuarkusTest
@EnabledIfEnvironmentVariable(named = "GOOGLE_ROUTES_LIVE", matches = "true")
class GoogleRoutesLiveCaptureTest {

    @Inject
    GoogleRoutesService googleRoutesService;

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    @Test
    void liveComputeRouteReturnsFullWellFormedPayload() {
        assertTrue(googleRoutesService.isApiKeyConfigured(),
            "GOOGLE_MAPS_API_KEY must be set to run the live capture test");

        // Future departure (Routes API requires departureTime in the future for traffic-aware routing).
        LocalDateTime departure = LocalDateTime.now(BERLIN)
            .plusDays(2).withHour(5).withMinute(0).withSecond(0).withNano(0);

        // City-Fahrschule hub -> Sanner (Bensheim): a real seeded leg.
        JsonNode root = googleRoutesService.computeRouteRaw(
            49.6295, 8.3640, 49.6800, 8.6250, departure, "BEST_GUESS", "*");

        JsonNode routes = root.path("routes");
        assertTrue(routes.isArray() && routes.size() > 0, "live response has routes[]");
        JsonNode route = routes.get(0);

        // Full field set (not just the three production fields).
        for (String field : new String[]{
                "distanceMeters", "duration", "staticDuration", "polyline", "legs",
                "routeLabels", "localizedValues", "viewport"}) {
            assertTrue(route.has(field), "live route." + field + " present");
        }

        long distance = route.path("distanceMeters").asLong();
        long traffic = durationSeconds(route.path("duration").asText());
        long stat = durationSeconds(route.path("staticDuration").asText());
        assertTrue(distance > 0, "live distanceMeters > 0");
        assertTrue(traffic > 0 && stat > 0, "live durations > 0");

        // Legs and steps sum exactly to the route distance.
        long legSum = 0;
        for (JsonNode leg : route.path("legs")) {
            legSum += leg.path("distanceMeters").asLong();
            assertFalse(leg.path("polyline").path("encodedPolyline").asText("").isBlank(),
                "live leg polyline present");
            assertTrue(leg.path("steps").isArray() && leg.path("steps").size() > 0, "live leg has steps");
        }
        assertEquals(distance, legSum, "live sum(leg distances) == route distance");

        assertFalse(route.path("polyline").path("encodedPolyline").asText("").isBlank(),
            "live route polyline present");
    }

    /** Parses Google's "3600s" / "3600.5s" duration strings to whole seconds. */
    private static long durationSeconds(String s) {
        if (s == null || s.isBlank()) return 0;
        String trimmed = s.endsWith("s") ? s.substring(0, s.length() - 1) : s;
        return (long) Double.parseDouble(trimmed);
    }
}
