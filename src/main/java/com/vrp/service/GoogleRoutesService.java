package com.vrp.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;

@ApplicationScoped
public class GoogleRoutesService {

    private static final Logger LOG = Logger.getLogger(GoogleRoutesService.class);
    static final String ROUTES_API_URL = "https://routes.googleapis.com/directions/v2:computeRoutes";
    static final ZoneId BERLIN_ZONE = ZoneId.of("Europe/Berlin");

    // Optional so an unset OR empty key leaves the app bootable (SmallRye converts "" to null and
    // would otherwise fail deployment). The /routes/validate resource then degrades gracefully to a
    // 503 + Haversine fallback instead of the app failing to start.
    @ConfigProperty(name = "google.maps.api.key")
    Optional<String> apiKey;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public boolean isApiKeyConfigured() {
        return apiKey != null && apiKey.isPresent() && !apiKey.get().isBlank();
    }

    public record RouteResult(
        long trafficDurationSeconds,
        long staticDurationSeconds,
        long distanceMeters,
        String trafficModel,
        String departureUtc
    ) {}

    public RouteResult computeRoute(
        double originLat, double originLng,
        double destLat, double destLng,
        LocalDateTime departureBerlin,
        String trafficModel
    ) {
        if (!isApiKeyConfigured()) {
            throw new IllegalStateException("Google Maps API key not configured. Set GOOGLE_MAPS_API_KEY or google.maps.api.key.");
        }

        String departureUtcStr = toRfc3339Utc(departureBerlin);
        String requestBody = buildRequestBody(originLat, originLng, destLat, destLng, departureUtcStr, trafficModel);

        LOG.debugf("Calling Google Routes API: departure=%s trafficModel=%s", departureUtcStr, trafficModel);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ROUTES_API_URL))
                .header("Content-Type", "application/json")
                .header("X-Goog-Api-Key", apiKey.orElseThrow())
                .header("X-Goog-FieldMask", "routes.duration,routes.staticDuration,routes.distanceMeters")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Google Routes API error " + response.statusCode() + ": " + response.body());
            }

            return parseResponse(response.body(), trafficModel, departureUtcStr);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to call Google Routes API: " + e.getMessage(), e);
        }
    }

    /**
     * Issues a computeRoutes request with a caller-supplied {@code X-Goog-FieldMask} and returns
     * the complete raw JSON response, parsed but otherwise untouched. Unlike {@link #computeRoute},
     * which extracts a narrow {@link RouteResult}, this exposes the entire payload so callers can
     * capture and assert on every field. The production request path ({@link #computeRoute} and the
     * {@code /routes/validate} resource) is deliberately left unchanged.
     *
     * <p>Pass {@code fieldMask = "*"} to retrieve the full response (all routes.* fields). Note that
     * broad field masks and {@code TRAFFIC_AWARE_OPTIMAL} routing map to Google's higher-cost SKUs —
     * cost scales with the number of calls, so keep live usage to a handful.
     *
     * @return the parsed root {@link JsonNode} of the API response
     * @throws IllegalStateException if no API key is configured
     * @throws RuntimeException      on non-200 responses or transport failures
     */
    public JsonNode computeRouteRaw(
        double originLat, double originLng,
        double destLat, double destLng,
        LocalDateTime departureBerlin,
        String trafficModel,
        String fieldMask
    ) {
        if (!isApiKeyConfigured()) {
            throw new IllegalStateException("Google Maps API key not configured. Set GOOGLE_MAPS_API_KEY or google.maps.api.key.");
        }

        String departureUtcStr = toRfc3339Utc(departureBerlin);
        String requestBody = buildRequestBody(originLat, originLng, destLat, destLng, departureUtcStr, trafficModel);

        LOG.debugf("Calling Google Routes API (raw): departure=%s trafficModel=%s fieldMask=%s",
            departureUtcStr, trafficModel, fieldMask);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ROUTES_API_URL))
                .header("Content-Type", "application/json")
                .header("X-Goog-Api-Key", apiKey.orElseThrow())
                .header("X-Goog-FieldMask", fieldMask)
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("Google Routes API error " + response.statusCode() + ": " + response.body());
            }

            return objectMapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Request interrupted", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to call Google Routes API: " + e.getMessage(), e);
        }
    }

    private String buildRequestBody(double originLat, double originLng,
                                    double destLat, double destLng,
                                    String departureUtcStr, String trafficModel) {
        return String.format(Locale.ROOT,
            "{\"origin\":{\"location\":{\"latLng\":{\"latitude\":%f,\"longitude\":%f}}}," +
            "\"destination\":{\"location\":{\"latLng\":{\"latitude\":%f,\"longitude\":%f}}}," +
            "\"travelMode\":\"DRIVE\"," +
            "\"routingPreference\":\"TRAFFIC_AWARE_OPTIMAL\"," +
            "\"departureTime\":\"%s\"," +
            "\"trafficModel\":\"%s\"}",
            originLat, originLng, destLat, destLng, departureUtcStr, trafficModel);
    }

    public RouteResult parseResponse(String responseBody, String trafficModel, String departureUtcStr) throws Exception {
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode routes = root.path("routes");
        if (!routes.isArray() || routes.size() == 0) {
            throw new RuntimeException("No routes in response: " + responseBody);
        }
        JsonNode route = routes.get(0);
        long trafficSeconds = parseDurationSeconds(route.path("duration").asText(""));
        long staticSeconds = parseDurationSeconds(route.path("staticDuration").asText(""));
        long distanceMeters = route.path("distanceMeters").asLong(0);
        return new RouteResult(trafficSeconds, staticSeconds, distanceMeters, trafficModel, departureUtcStr);
    }

    /**
     * Parses Google Routes API duration strings like "3600s" or "3600.5s" into whole seconds.
     */
    static long parseDurationSeconds(String durationStr) {
        if (durationStr == null || durationStr.isBlank()) return 0;
        String s = durationStr.endsWith("s") ? durationStr.substring(0, durationStr.length() - 1) : durationStr;
        return (long) Double.parseDouble(s);
    }

    /**
     * Converts a Europe/Berlin local departure time to RFC-3339 UTC string for Google Routes API.
     */
    static String toRfc3339Utc(LocalDateTime berlinTime) {
        ZonedDateTime utc = berlinTime.atZone(BERLIN_ZONE).withZoneSameInstant(ZoneOffset.UTC);
        return utc.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }
}
