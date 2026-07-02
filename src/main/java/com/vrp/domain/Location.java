package com.vrp.domain;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.ResponsePath;
import com.graphhopper.GraphHopper;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Represents a geographic location.
 * Equality is based on coordinates only (latitude, longitude), NOT the name.
 * This ensures that locations like "City-Fahrschule" and "Pickup-1" at the same
 * coordinates are treated as the same location for solver chaining, routing cache,
 * and constraint calculations. The name is purely a display label.
 */
public class Location {

    private final String name;
    private final double latitude;
    private final double longitude;

    public static final Location HUB = new Location("City-Fahrschule", 49.6295, 8.3640);

    public record TravelData(long distanceMeters, Duration travelTime) {}

    private static final ConcurrentHashMap<Location, ConcurrentHashMap<Location, TravelData>> routingCache = new ConcurrentHashMap<>();

    @JsonCreator
    public Location(
            @JsonProperty("name") String name,
            @JsonProperty("latitude") double latitude,
            @JsonProperty("longitude") double longitude) {
        this.name = Objects.requireNonNull(name, "Location name cannot be null");
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String name() { return name; }
    public double latitude() { return latitude; }
    public double longitude() { return longitude; }

    /**
     * Clears the process-wide routing cache. The cache is keyed purely by coordinates and is
     * never invalidated, so values computed with one routing backend (e.g. the Haversine fallback)
     * would otherwise leak into later callers using a different backend (e.g. real GraphHopper)
     * within the same JVM. Tests that switch backends must call this between runs so the
     * "real routing was actually used" guarantee holds. Package-private: a test seam, not API.
     */
    static void clearRoutingCache() {
        routingCache.clear();
    }

    public TravelData getRouting(Location other, GraphHopper graphHopper) {
        if (this.equals(other)) return new TravelData(0L, Duration.ZERO);
        return routingCache
            .computeIfAbsent(this, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(other, k -> computeRouting(other, graphHopper));
    }

    private TravelData computeRouting(Location other, GraphHopper graphHopper) {
        if (graphHopper == null) {
            long distance = getHaversineDistance(other);
            return new TravelData(distance, Duration.ofSeconds(distance / 15));
        }
        try {
            GHRequest request = new GHRequest(this.latitude, this.longitude, other.latitude, other.longitude)
                .setProfile("car");
            GHResponse response = graphHopper.route(request);
            if (response.hasErrors()) {
                long distance = getHaversineDistance(other);
                return new TravelData(distance, Duration.ofSeconds(distance / 15));
            }
            ResponsePath path = response.getBest();
            return new TravelData(Math.round(path.getDistance()), Duration.ofMillis(path.getTime()));
        } catch (Exception e) {
            long distance = getHaversineDistance(other);
            return new TravelData(distance, Duration.ofSeconds(distance / 15));
        }
    }

    public long getDistanceTo(Location other, GraphHopper graphHopper) {
        return getRouting(other, graphHopper).distanceMeters();
    }

    public Duration getTravelTime(Location other, GraphHopper graphHopper) {
        return getRouting(other, graphHopper).travelTime();
    }
    
    public long getHaversineDistance(Location other) {
        final int EARTH_RADIUS_METERS = 6371000;
        
        double lat1Rad = Math.toRadians(this.latitude);
        double lat2Rad = Math.toRadians(other.latitude);
        double deltaLat = Math.toRadians(other.latitude - this.latitude);
        double deltaLon = Math.toRadians(other.longitude - this.longitude);
        
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2) +
                   Math.cos(lat1Rad) * Math.cos(lat2Rad) *
                   Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        
        return Math.round(EARTH_RADIUS_METERS * c);
    }

    /**
     * Equality based on coordinates only. Two locations at the same
     * lat/lon are considered equal regardless of their display name.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Location location = (Location) o;
        // Use epsilon comparison for doubles to handle floating point rounding
        return Math.abs(latitude - location.latitude) < 1e-9 &&
               Math.abs(longitude - location.longitude) < 1e-9;
    }

    /**
     * Hash code based on coordinates only (consistent with equals).
     */
    @Override
    public int hashCode() {
        // Round to 6 decimal places (~0.1m precision) for stable hashing
        long latBits = Double.doubleToLongBits(Math.round(latitude * 1e6) / 1e6);
        long lonBits = Double.doubleToLongBits(Math.round(longitude * 1e6) / 1e6);
        return (int) (31 * latBits + lonBits);
    }

    @Override
    public String toString() {
        return name + " (" + String.format("%.4f", latitude) + ", " + String.format("%.4f", longitude) + ")";
    }
}
