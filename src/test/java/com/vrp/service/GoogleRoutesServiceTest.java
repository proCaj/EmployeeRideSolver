package com.vrp.service;

import com.vrp.resource.GoogleRouteValidationResource;
import org.junit.jupiter.api.Test;

import java.time.*;

import static org.junit.jupiter.api.Assertions.*;

class GoogleRoutesServiceTest {

    // ----------------------------------------------------------------
    // parseDurationSeconds
    // ----------------------------------------------------------------

    @Test
    void parseDurationSeconds_parsesWholeSeconds() {
        assertEquals(3600L, GoogleRoutesService.parseDurationSeconds("3600s"));
    }

    @Test
    void parseDurationSeconds_truncatesDecimalSeconds() {
        assertEquals(3600L, GoogleRoutesService.parseDurationSeconds("3600.9s"));
    }

    @Test
    void parseDurationSeconds_acceptsNoSuffix() {
        assertEquals(1800L, GoogleRoutesService.parseDurationSeconds("1800"));
    }

    @Test
    void parseDurationSeconds_returnsZeroForNull() {
        assertEquals(0L, GoogleRoutesService.parseDurationSeconds(null));
    }

    @Test
    void parseDurationSeconds_returnsZeroForBlank() {
        assertEquals(0L, GoogleRoutesService.parseDurationSeconds(""));
    }

    // ----------------------------------------------------------------
    // toRfc3339Utc — Berlin offset is UTC+1 in winter, UTC+2 in summer
    // ----------------------------------------------------------------

    @Test
    void toRfc3339Utc_berlinWinterOffsetIsUtcPlusOne() {
        // January = CET = UTC+1 → 05:00 Berlin = 04:00 UTC
        LocalDateTime berlinTime = LocalDateTime.of(2026, 1, 13, 5, 0, 0);
        String utc = GoogleRoutesService.toRfc3339Utc(berlinTime);
        assertTrue(utc.startsWith("2026-01-13T04:00:00"),
            "Expected UTC 04:00:00, got: " + utc);
    }

    @Test
    void toRfc3339Utc_berlinSummerOffsetIsUtcPlusTwo() {
        // June = CEST = UTC+2 → 05:00 Berlin = 03:00 UTC
        LocalDateTime berlinTime = LocalDateTime.of(2026, 6, 30, 5, 0, 0);
        String utc = GoogleRoutesService.toRfc3339Utc(berlinTime);
        assertTrue(utc.startsWith("2026-06-30T03:00:00"),
            "Expected UTC 03:00:00, got: " + utc);
    }

    @Test
    void toRfc3339Utc_containsZoneOffset() {
        LocalDateTime berlinTime = LocalDateTime.of(2026, 6, 30, 5, 0, 0);
        String utc = GoogleRoutesService.toRfc3339Utc(berlinTime);
        // ISO_OFFSET_DATE_TIME includes +HH:MM or Z
        assertTrue(utc.contains("+") || utc.contains("Z"),
            "Expected RFC-3339 offset marker, got: " + utc);
    }

    // ----------------------------------------------------------------
    // parseResponse — exercises JSON parsing without hitting the API
    // ----------------------------------------------------------------

    @Test
    void parseResponse_extractsAllFields() throws Exception {
        GoogleRoutesService service = new GoogleRoutesService();
        String json = """
            {
              "routes": [{
                "distanceMeters": 75000,
                "duration": "3600s",
                "staticDuration": "3000s"
              }]
            }""";
        GoogleRoutesService.RouteResult result =
            service.parseResponse(json, "BEST_GUESS", "2026-06-30T03:00:00Z");
        assertEquals(75000L, result.distanceMeters());
        assertEquals(3600L, result.trafficDurationSeconds());
        assertEquals(3000L, result.staticDurationSeconds());
        assertEquals("BEST_GUESS", result.trafficModel());
        assertEquals("2026-06-30T03:00:00Z", result.departureUtc());
    }

    @Test
    void parseResponse_throwsWhenNoRoutes() {
        GoogleRoutesService service = new GoogleRoutesService();
        String json = "{\"routes\": []}";
        assertThrows(RuntimeException.class,
            () -> service.parseResponse(json, "BEST_GUESS", "2026-06-30T03:00:00Z"));
    }

    // ----------------------------------------------------------------
    // isApiKeyConfigured — no CDI injection means apiKey stays null
    // ----------------------------------------------------------------

    @Test
    void isApiKeyConfigured_returnsFalseWhenNotInjected() {
        // Plain instantiation — no CDI, apiKey field is null
        GoogleRoutesService service = new GoogleRoutesService();
        assertFalse(service.isApiKeyConfigured());
    }

    // ----------------------------------------------------------------
    // nextTuesdayAt05 — always returns a future Tuesday at 05:00
    // ----------------------------------------------------------------

    @Test
    void nextTuesdayAt05_alwaysReturnsTuesdayAtFiveAm() {
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        LocalDateTime result = GoogleRouteValidationResource.nextTuesdayAt05(berlin);
        assertEquals(DayOfWeek.TUESDAY, result.getDayOfWeek());
        assertEquals(5, result.getHour());
        assertEquals(0, result.getMinute());
    }

    @Test
    void nextTuesdayAt05_dateIsStrictlyInFuture() {
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        LocalDate today = LocalDate.now(berlin);
        LocalDateTime result = GoogleRouteValidationResource.nextTuesdayAt05(berlin);
        assertTrue(result.toLocalDate().isAfter(today),
            "nextTuesdayAt05 should always be strictly after today");
    }

    @Test
    void nextTuesdayAt05_skipsTodayIfTuesday() {
        // Simulate: if today were Tuesday, result must be at least 1 day ahead
        // We can't control LocalDate.now(), so verify the "if today == Tuesday" branch
        // by checking the arithmetic directly.
        ZoneId berlin = ZoneId.of("Europe/Berlin");
        LocalDate today = LocalDate.now(berlin);
        // Regardless of what today is, the result must be > today
        LocalDateTime result = GoogleRouteValidationResource.nextTuesdayAt05(berlin);
        assertTrue(result.toLocalDate().isAfter(today));
    }
}
