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

public record Location(
    @JsonProperty("name") String name,
    @JsonProperty("latitude") double latitude,
    @JsonProperty("longitude") double longitude
) {

    public static final Location HUB = new Location("City-Fahrschule", 49.6295, 8.3640);

    public record LocationPair(Location from, Location to) {}
    public record TravelData(long distanceMeters, Duration travelTime) {}

    private static final ConcurrentHashMap<LocationPair, TravelData> routingCache = new ConcurrentHashMap<>();

    @JsonCreator
    public Location {
        Objects.requireNonNull(name, "Location name cannot be null");
    }

    public TravelData getRouting(Location other, GraphHopper graphHopper) {
        if (this.equals(other)) return new TravelData(0L, Duration.ZERO);
        LocationPair key = new LocationPair(this, other);
        return routingCache.computeIfAbsent(key, k -> computeRouting(other, graphHopper));
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
    
    @Override
    public String toString() {
        return name + " (" + String.format("%.4f", latitude) + ", " + String.format("%.4f", longitude) + ")";
    }
}
