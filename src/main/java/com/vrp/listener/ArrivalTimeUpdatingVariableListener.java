package com.vrp.listener;

import ai.timefold.solver.core.api.domain.variable.VariableListener;
import ai.timefold.solver.core.api.score.director.ScoreDirector;
import com.vrp.domain.Driver;
import com.vrp.domain.Event;
import com.vrp.domain.Standstill;
import com.vrp.domain.VrpSolution;

import java.time.Duration;
import java.time.Instant;

public class ArrivalTimeUpdatingVariableListener implements VariableListener<VrpSolution, Event> {
    
    private static final int AVERAGE_SPEED_MPS = 15;
    
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
                Duration travelTime = calculateTravelTime(previousEvent, sourceEvent);
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
    
    private Event findNextEvent(VrpSolution solution, Event currentEvent) {
        for (Event event : solution.getEvents()) {
            if (event.getPreviousStandstill() == currentEvent) {
                return event;
            }
        }
        return null;
    }
    
    private Duration calculateTravelTime(Event from, Event to) {
        long distance = from.getLocation().getHaversineDistance(to.getFromLocation());
        long travelTimeSeconds = distance / AVERAGE_SPEED_MPS;
        return Duration.ofSeconds(travelTimeSeconds);
    }
}
