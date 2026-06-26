package com.vrp.resource;

import com.vrp.domain.Location;
import com.vrp.service.GoogleRoutesService;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.time.*;
import java.util.LinkedHashMap;
import java.util.Map;

@Path("/api/routes/validate")
public class GoogleRouteValidationResource {

    // Demo: Burgheimer Str. 6, 67575 Eich → Frankfurt am Main Hauptbahnhof
    static final double DEMO_ORIGIN_LAT = 49.6441;
    static final double DEMO_ORIGIN_LNG = 8.3253;
    static final double DEMO_DEST_LAT = 50.1073;
    static final double DEMO_DEST_LNG = 8.6637;

    @Inject
    GoogleRoutesService googleRoutesService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response validate(
        @QueryParam("originLat") Double originLat,
        @QueryParam("originLng") Double originLng,
        @QueryParam("destLat") Double destLat,
        @QueryParam("destLng") Double destLng,
        @QueryParam("departure") String departure,
        @QueryParam("trafficModel") @DefaultValue("BEST_GUESS") String trafficModel
    ) {
        double oLat = originLat != null ? originLat : DEMO_ORIGIN_LAT;
        double oLng = originLng != null ? originLng : DEMO_ORIGIN_LNG;
        double dLat = destLat != null ? destLat : DEMO_DEST_LAT;
        double dLng = destLng != null ? destLng : DEMO_DEST_LNG;

        LocalDateTime departureDt = parseDeparture(departure);
        Map<String, Object> fallback = buildFallback(oLat, oLng, dLat, dLng);

        if (!googleRoutesService.isApiKeyConfigured()) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("error", "Google Maps API key not configured");
            body.put("hint", "Set GOOGLE_MAPS_API_KEY environment variable or google.maps.api.key property");
            body.put("fallbackEstimate", fallback);
            return Response.status(Response.Status.SERVICE_UNAVAILABLE).entity(body).build();
        }

        try {
            GoogleRoutesService.RouteResult result = googleRoutesService.computeRoute(
                oLat, oLng, dLat, dLng, departureDt, trafficModel);
            return Response.ok(buildResponse(result, fallback)).build();
        } catch (Exception e) {
            return Response.serverError()
                .entity(Map.of("error", e.getMessage()))
                .build();
        }
    }

    private LocalDateTime parseDeparture(String departure) {
        if (departure == null || departure.isBlank()) {
            return nextTuesdayAt05(ZoneId.of("Europe/Berlin"));
        }
        return LocalDateTime.parse(departure);
    }

    /**
     * Returns the next Tuesday at 05:00 in the given zone.
     * If today is Tuesday, returns the following Tuesday (not today).
     */
    public static LocalDateTime nextTuesdayAt05(ZoneId zone) {
        LocalDate today = LocalDate.now(zone);
        int daysUntilTuesday = (DayOfWeek.TUESDAY.getValue() - today.getDayOfWeek().getValue() + 7) % 7;
        if (daysUntilTuesday == 0) daysUntilTuesday = 7;
        return LocalDateTime.of(today.plusDays(daysUntilTuesday), LocalTime.of(5, 0));
    }

    private Map<String, Object> buildResponse(GoogleRoutesService.RouteResult result, Map<String, Object> fallback) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("trafficDurationSeconds", result.trafficDurationSeconds());
        map.put("trafficDurationMinutes", result.trafficDurationSeconds() / 60.0);
        map.put("staticDurationSeconds", result.staticDurationSeconds());
        map.put("staticDurationMinutes", result.staticDurationSeconds() / 60.0);
        map.put("distanceMeters", result.distanceMeters());
        map.put("distanceKm", result.distanceMeters() / 1000.0);
        map.put("trafficModel", result.trafficModel());
        map.put("departureUtc", result.departureUtc());
        map.put("fallbackEstimate", fallback);
        return map;
    }

    private Map<String, Object> buildFallback(double oLat, double oLng, double dLat, double dLng) {
        Location origin = new Location("origin", oLat, oLng);
        Location dest = new Location("dest", dLat, dLng);
        long distanceMeters = origin.getHaversineDistance(dest);
        long travelSeconds = distanceMeters / 15; // 15 m/s ≈ 54 km/h average speed
        Map<String, Object> fb = new LinkedHashMap<>();
        fb.put("method", "Haversine straight-line / 15 m/s average speed (54 km/h)");
        fb.put("distanceMeters", distanceMeters);
        fb.put("distanceKm", distanceMeters / 1000.0);
        fb.put("travelTimeSeconds", travelSeconds);
        fb.put("travelTimeMinutes", travelSeconds / 60.0);
        return fb;
    }
}
