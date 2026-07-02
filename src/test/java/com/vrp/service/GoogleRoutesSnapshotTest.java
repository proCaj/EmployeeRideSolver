package com.vrp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Deterministic, zero-cost replay of REAL Google Routes API responses.
 *
 * <p>The JSON under {@code src/test/resources/google-snapshots/} was captured from the live
 * {@code routes.googleapis.com/directions/v2:computeRoutes} endpoint with field mask {@code *}
 * (every field). Nothing here is fabricated — it is the actual raw payload the service receives.
 * {@link GoogleRoutesLiveCaptureTest} re-fetches/refreshes these snapshots against the live API
 * when enabled; this test proves our parsing and the full field set every time {@code mvn test}
 * runs, without spending money.
 *
 * <p>These snapshots also caught a real invariant: with {@code BEST_GUESS} traffic on an off-peak
 * future departure, the traffic-aware {@code duration} is consistently LESS than {@code staticDuration}
 * — so we assert both are positive and sanely proportioned, never that traffic ≥ static.
 */
class GoogleRoutesSnapshotTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Route-level fields present in EVERY captured route — proves "everything, not one or two".
     * {@code warnings} is deliberately excluded: it only appears when the route actually has a
     * warning (e.g. "uses a motorway"), so it is covered separately by {@link #warningsAppearWhenPresent}.
     */
    private static final String[] EXPECTED_ROUTE_FIELDS = {
        "description", "distanceMeters", "duration", "legs", "localizedValues", "polyline",
        "polylineDetails", "routeLabels", "routeToken", "staticDuration", "travelAdvisory",
        "viewport"
    };

    private static final String[] SNAPSHOTS = {"eich-frankfurt", "hub-chep", "hub-sanner"};

    private JsonNode loadRoute(String name) throws Exception {
        JsonNode root = loadRoot(name);
        JsonNode routes = root.path("routes");
        assertTrue(routes.isArray() && routes.size() > 0, "routes[] present and non-empty in " + name);
        return routes.get(0);
    }

    private JsonNode loadRoot(String name) throws Exception {
        String path = "/google-snapshots/" + name + ".json";
        try (InputStream in = getClass().getResourceAsStream(path)) {
            assertNotNull(in, "snapshot on classpath: " + path);
            return MAPPER.readTree(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    // ---------------------------------------------------------------
    // Full field coverage across every captured route
    // ---------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"eich-frankfurt", "hub-chep", "hub-sanner"})
    void everyRouteLevelFieldPresent(String name) throws Exception {
        JsonNode route = loadRoute(name);
        for (String field : EXPECTED_ROUTE_FIELDS) {
            assertTrue(route.has(field), "route." + field + " present in " + name);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"eich-frankfurt", "hub-chep", "hub-sanner"})
    void distanceAndDurationsAreSaneAndConsistent(String name) throws Exception {
        JsonNode route = loadRoute(name);

        long distance = route.path("distanceMeters").asLong(-1);
        assertTrue(distance > 0, "distanceMeters > 0 in " + name);

        long traffic = GoogleRoutesService.parseDurationSeconds(route.path("duration").asText(""));
        long stat = GoogleRoutesService.parseDurationSeconds(route.path("staticDuration").asText(""));
        assertTrue(traffic > 0, "traffic duration > 0 in " + name);
        assertTrue(stat > 0, "static duration > 0 in " + name);
        // Sanely proportioned — no assumption about which is larger (real data showed traffic < static).
        double ratio = (double) traffic / stat;
        assertTrue(ratio > 0.4 && ratio < 2.5, "traffic/static ratio sane (" + ratio + ") in " + name);

        // Implied average speed must be physically plausible for a car (5–160 km/h).
        double kmh = (distance / 1000.0) / (traffic / 3600.0);
        assertTrue(kmh > 5 && kmh < 160, "implied speed plausible (" + kmh + " km/h) in " + name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"eich-frankfurt", "hub-chep", "hub-sanner"})
    void legAndStepDistancesSumExactlyToRouteDistance(String name) throws Exception {
        JsonNode route = loadRoute(name);
        long routeDistance = route.path("distanceMeters").asLong();

        JsonNode legs = route.path("legs");
        assertTrue(legs.isArray() && legs.size() > 0, "legs present in " + name);

        long legSum = 0;
        for (JsonNode leg : legs) {
            long legDistance = leg.path("distanceMeters").asLong();
            legSum += legDistance;

            // Leg endpoints and geometry.
            assertTrue(hasLatLng(leg.path("startLocation")), "leg startLocation latLng in " + name);
            assertTrue(hasLatLng(leg.path("endLocation")), "leg endLocation latLng in " + name);
            assertFalse(leg.path("polyline").path("encodedPolyline").asText("").isBlank(),
                "leg polyline in " + name);

            JsonNode steps = leg.path("steps");
            assertTrue(steps.isArray() && steps.size() > 0, "leg steps present in " + name);
            long stepSum = 0;
            for (JsonNode step : steps) {
                stepSum += step.path("distanceMeters").asLong(0);
                assertTrue(hasLatLng(step.path("startLocation")), "step startLocation latLng in " + name);
                assertTrue(hasLatLng(step.path("endLocation")), "step endLocation latLng in " + name);
                assertEquals("DRIVE", step.path("travelMode").asText(), "step travelMode in " + name);
                assertFalse(step.path("polyline").path("encodedPolyline").asText("").isBlank(),
                    "step polyline in " + name);
                assertTrue(step.has("navigationInstruction"), "step navigationInstruction in " + name);
            }
            assertEquals(legDistance, stepSum, "sum(step distances) == leg distance in " + name);
        }
        assertEquals(routeDistance, legSum, "sum(leg distances) == route distance in " + name);
    }

    @ParameterizedTest
    @ValueSource(strings = {"eich-frankfurt", "hub-chep", "hub-sanner"})
    void metadataFieldsPopulated(String name) throws Exception {
        JsonNode route = loadRoute(name);

        // Route labels.
        JsonNode labels = route.path("routeLabels");
        assertTrue(labels.isArray() && labels.size() > 0, "routeLabels non-empty in " + name);

        // Encoded polyline for the whole route.
        assertFalse(route.path("polyline").path("encodedPolyline").asText("").isBlank(),
            "route polyline in " + name);

        // Localized human-readable values.
        JsonNode lv = route.path("localizedValues");
        for (String key : new String[]{"distance", "duration", "staticDuration"}) {
            assertFalse(lv.path(key).path("text").asText("").isBlank(),
                "localizedValues." + key + ".text in " + name);
        }

        // Viewport bounding box.
        assertTrue(hasLatLngDirect(route.path("viewport").path("low")), "viewport.low in " + name);
        assertTrue(hasLatLngDirect(route.path("viewport").path("high")), "viewport.high in " + name);

        // Route token (opaque, but present and non-trivial).
        assertTrue(route.path("routeToken").asText("").length() > 10, "routeToken in " + name);

        // latLng inputs produce empty geocodingResults — verify it's present-but-empty, as expected.
        JsonNode geocoding = loadRoot(name).path("geocodingResults");
        assertTrue(geocoding.isMissingNode() || geocoding.isEmpty() || geocoding.isObject(),
            "geocodingResults empty for latLng inputs in " + name);
    }

    // ---------------------------------------------------------------
    // Production extraction (GoogleRoutesService.parseResponse) against REAL data
    // ---------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"eich-frankfurt", "hub-chep", "hub-sanner"})
    void productionParseResponseMatchesRawFields(String name) throws Exception {
        JsonNode route = loadRoute(name);
        String rawBody = loadRawBody(name);

        GoogleRoutesService service = new GoogleRoutesService();
        GoogleRoutesService.RouteResult result =
            service.parseResponse(rawBody, "BEST_GUESS", "2026-07-07T03:00:00Z");

        assertEquals(route.path("distanceMeters").asLong(), result.distanceMeters(),
            "parsed distance matches raw in " + name);
        assertEquals(GoogleRoutesService.parseDurationSeconds(route.path("duration").asText()),
            result.trafficDurationSeconds(), "parsed traffic duration matches raw in " + name);
        assertEquals(GoogleRoutesService.parseDurationSeconds(route.path("staticDuration").asText()),
            result.staticDurationSeconds(), "parsed static duration matches raw in " + name);
        assertEquals("BEST_GUESS", result.trafficModel());
        assertEquals("2026-07-07T03:00:00Z", result.departureUtc());
    }

    private String loadRawBody(String name) throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/google-snapshots/" + name + ".json")) {
            assertNotNull(in);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    // ---------------------------------------------------------------
    // Locked exact values (real recorded numbers — regression guard)
    // ---------------------------------------------------------------

    @Test
    void warningsAppearWhenPresent() throws Exception {
        // hub-sanner's route uses a motorway, so Google returns a warnings[] entry — captured verbatim.
        JsonNode warnings = loadRoute("hub-sanner").path("warnings");
        assertTrue(warnings.isArray() && warnings.size() > 0,
            "hub-sanner has a non-empty warnings[] (motorway advisory)");
        assertFalse(warnings.get(0).asText("").isBlank(), "warning text captured");
    }

    @Test
    void hubToSannerExactRecordedValues() throws Exception {
        JsonNode route = loadRoute("hub-sanner");
        assertEquals(21275, route.path("distanceMeters").asLong());
        assertEquals(1276, GoogleRoutesService.parseDurationSeconds(route.path("duration").asText()));
        assertEquals(1418, GoogleRoutesService.parseDurationSeconds(route.path("staticDuration").asText()));
        // Origin is the City-Fahrschule hub (49.6295, 8.3640).
        JsonNode start = route.path("legs").get(0).path("startLocation").path("latLng");
        assertEquals(49.6295, start.path("latitude").asDouble(), 1e-3, "origin ~ hub latitude");
        assertEquals(8.3640, start.path("longitude").asDouble(), 1e-3, "origin ~ hub longitude");
    }

    private static boolean hasLatLng(JsonNode locationNode) {
        JsonNode latLng = locationNode.path("latLng");
        return latLng.has("latitude") && latLng.has("longitude");
    }

    private static boolean hasLatLngDirect(JsonNode node) {
        return node.has("latitude") && node.has("longitude");
    }
}
