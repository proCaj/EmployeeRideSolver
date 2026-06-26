package com.vrp.listener;

import ai.timefold.solver.core.api.domain.variable.VariableListener;
import ai.timefold.solver.core.api.score.director.ScoreDirector;
import com.graphhopper.GraphHopper;
import com.vrp.domain.Driver;
import com.vrp.domain.Event;
import com.vrp.domain.Location;
import com.vrp.domain.Standstill;
import com.vrp.domain.VrpSolution;

import java.time.Duration;
import java.time.Instant;

/**
 * Updates the arrivalTime shadow variable when previousStandstill changes.
 * Uses GraphHopper for real routing distances when available (stored in VrpSolution).
 * Falls back to Haversine distance / 15 m/s average speed when GraphHopper is not available.
 */
public class ArrivalTimeUpdatingVariableListener implements VariableListener<VrpSolution, Event> {
    
    private static final int AVERAGE_SPEED_MPS = 15; // ~54 km/h fallback
    
    @Override
    public void beforeEntityAdded(ScoreDirector<VrpSolution> scoreDirector, Event event) {
    }
    
    @Override
    public void afterEntityAdded(ScoreDirector<VrpSolution> scoreDirector, Event event) {
        updateArrivalTime(scoreDirector, event);
    }
    
    @Override
    public void beforeVariableChanged(ScoreDirector<VrpSolution> scoreDirector, Event event) {
    }
    
    @Override
    public void afterVariableChanged(ScoreDirector<VrpSolution> scoreDirector, Event event) {
        updateArrivalTime(scoreDirector, event);
    }
    
    @Override
    public void beforeEntityRemoved(ScoreDirector<VrpSolution> scoreDirector, Event event) {
    }
    
    @Override
    public void afterEntityRemoved(ScoreDirector<VrpSolution> scoreDirector, Event event) {
    }
    
    protected void updateArrivalTime(ScoreDirector<VrpSolution> scoreDirector, Event sourceEvent) {
        Standstill previousStandstill = sourceEvent.getPreviousStandstill();
        
        Instant arrivalTime;
        if (previousStandstill == null) {
            arrivalTime = null;
        } else if (previousStandstill instanceof Driver) {
            arrivalTime = sourceEvent.getMinStartTime();
        } else if (previousStandstill instanceof Event) {
            Event previousEvent = (Event) previousStandstill;
            if (previousEvent.getArrivalTime() == null) {
                arrivalTime = null;
            } else {
                Instant departureTime = previousEvent.getDepartureTime();
                Duration travelTime = calculateTravelTime(
                    previousEvent.getLocation(), sourceEvent.getFromLocation(),
                    scoreDirector.getWorkingSolution().getGraphHopper()
                );
                arrivalTime = departureTime.plus(travelTime);
            }
        } else {
            arrivalTime = null;
        }
        
        scoreDirector.beforeVariableChanged(sourceEvent, "arrivalTime");
        sourceEvent.setArrivalTime(arrivalTime);
        scoreDirector.afterVariableChanged(sourceEvent, "arrivalTime");
        
        Event nextEvent = findNextEvent(scoreDirector.getWorkingSolution(), sourceEvent);
        if (nextEvent != null) {
            updateArrivalTime(scoreDirector, nextEvent);
        }
    }

    /**
     * Find the successor in the chained route. This intentionally uses a small
     * linear scan instead of @InverseRelationShadowVariable because Timefold
     * Quarkus rejects inverse shadow annotations on non-planning anchor facts
     * (Driver), while the inverse relation for a chained variable must also
     * work when the previous standstill is a Driver anchor.
     */
    private Event findNextEvent(VrpSolution solution, Standstill standstill) {
        if (solution == null || solution.getEvents() == null) {
            return null;
        }
        for (Event candidate : solution.getEvents()) {
            if (candidate.getPreviousStandstill() == standstill) {
                return candidate;
            }
        }
        return null;
    }
    
    /**
     * Calculates travel time between two locations.
     * Uses GraphHopper for real routing when available, falls back to
     * Haversine distance / average speed otherwise.
     */
    private Duration calculateTravelTime(Location from, Location to, GraphHopper graphHopper) {
        if (from.equals(to)) {
            return Duration.ZERO;
        }
        
        // Try GraphHopper first for real routing
        if (graphHopper != null) {
            try {
                Duration ghTime = from.getTravelTime(to, graphHopper);
                if (ghTime != null && !ghTime.isZero()) {
                    return ghTime;
                }
            } catch (Exception e) {
                // Fall through to Haversine fallback
            }
        }
        
        // Haversine fallback: distance / average speed
        long distance = from.getHaversineDistance(to);
        long travelTimeSeconds = distance / AVERAGE_SPEED_MPS;
        return Duration.ofSeconds(travelTimeSeconds);
    }
}
