package com.vrp.e2e;

import com.vrp.service.GoogleRoutesService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;

/**
 * End-to-end coverage of Flow B through the real HTTP layer: {@code GET /api/routes/validate}.
 *
 * <p>Two complementary paths, neither mocked:
 * <ul>
 *   <li><b>No key (always-on):</b> when no API key is configured the endpoint returns 503 with a
 *       real Haversine fallback estimate. Skipped (not failed) if a key happens to be present.</li>
 *   <li><b>Live (gated on {@code GOOGLE_ROUTES_LIVE}):</b> with a key, the endpoint makes a real
 *       Google call and returns traffic-aware figures plus the fallback comparison.</li>
 * </ul>
 */
@QuarkusTest
class RouteValidationEndpointE2ETest {

    @Inject
    GoogleRoutesService googleRoutesService;

    @Test
    void withoutKeyReturns503AndHaversineFallback() {
        Assumptions.assumeFalse(googleRoutesService.isApiKeyConfigured(),
            "API key is configured; the no-key path cannot be exercised");

        given()
            .queryParam("originLat", 49.6295).queryParam("originLng", 8.3640)
            .queryParam("destLat", 49.6800).queryParam("destLng", 8.6250)
        .when()
            .get("/api/routes/validate")
        .then()
            .statusCode(503)
            .body("error", notNullValue())
            .body("hint", notNullValue())
            .body("fallbackEstimate.method", containsString("Haversine"))
            .body("fallbackEstimate.distanceMeters", greaterThan(0))
            .body("fallbackEstimate.distanceKm", notNullValue())
            .body("fallbackEstimate.travelTimeSeconds", greaterThan(0))
            .body("fallbackEstimate.travelTimeMinutes", notNullValue());
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "GOOGLE_ROUTES_LIVE", matches = "true")
    void withKeyReturnsRealTrafficAwareRoute() {
        Assumptions.assumeTrue(googleRoutesService.isApiKeyConfigured(),
            "GOOGLE_MAPS_API_KEY must be set for the live validation path");

        given()
            .queryParam("originLat", 49.6295).queryParam("originLng", 8.3640)
            .queryParam("destLat", 49.6800).queryParam("destLng", 8.6250)
            .queryParam("trafficModel", "BEST_GUESS")
        .when()
            .get("/api/routes/validate")
        .then()
            .statusCode(200)
            .body("trafficDurationSeconds", greaterThan(0))
            .body("trafficDurationMinutes", notNullValue())
            .body("staticDurationSeconds", greaterThan(0))
            .body("staticDurationMinutes", notNullValue())
            .body("distanceMeters", greaterThan(0))
            .body("distanceKm", notNullValue())
            .body("trafficModel", equalTo("BEST_GUESS"))
            .body("departureUtc", notNullValue())
            .body("fallbackEstimate.distanceMeters", greaterThan(0));
    }
}
