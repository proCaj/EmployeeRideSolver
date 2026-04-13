package com.vrp.domain;

import com.graphhopper.GHRequest;
import com.graphhopper.GHResponse;
import com.graphhopper.ResponsePath;
import com.graphhopper.GraphHopper;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Duration;
import java.util.Objects;

public record Location(
    @JsonProperty("name") String name,
    @JsonProperty("latitude") double latitude,
    @JsonProperty("longitude") double longitude
) {
    
    public static final Location HUB = new Location("City-Fahrschule", 49.6295, 8.3640);
    
    @JsonCreator
    public Location {
        Objects.requireNonNull(name, "Location name cannot be null");
    }
    
    public long getDistanceTo(Location other, GraphHopper graphHopper) {
        if (this.equals(other)) {
            return 0L;
        }
        
        if (graphHopper == null) {
            return getHaversineDistance(other);
        }
        
        try {
            GHRequest request = new GHRequest(this.latitude, this.longitude, other.latitude, other.longitude)
                .setProfile("car");
            GHResponse response = graphHopper.route(request);
            
            if (response.hasErrors()) {
                return getHaversineDistance(other);
            }
            
            ResponsePath path = response.getBest();
            return Math.round(path.getDistance());
        } catch (Exception e) {
            return getHaversineDistance(other);
        }
    }
    
    public Duration getTravelTime(Location other, GraphHopper graphHopper) {
        if (this.equals(other)) {
            return Duration.ZERO;
        }
        
        if (graphHopper == null) {
            long distance = getHaversineDistance(other);
            return Duration.ofSeconds(distance / 15);
        }
        
        try {
            GHRequest request = new GHRequest(this.latitude, this.longitude, other.latitude, other.longitude)
                .setProfile("car");
            GHResponse response = graphHopper.route(request);
            
            if (response.hasErrors()) {
                long distance = getHaversineDistance(other);
                return Duration.ofSeconds(distance / 15);
            }
            
            ResponsePath path = response.getBest();
            return Duration.ofMillis(path.getTime());
        } catch (Exception e) {
            long distance = getHaversineDistance(other);
            return Duration.ofSeconds(distance / 15);
        }
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
