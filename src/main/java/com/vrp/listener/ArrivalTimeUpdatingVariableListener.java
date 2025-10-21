package com.vrp.listener;

import ai.timefold.solver.core.api.domain.variable.VariableListener;
import ai.timefold.solver.core.api.score.director.ScoreDirector;
import com.vrp.domain.Driver;
import com.vrp.domain.Event;
import com.vrp.domain.Standstill;
import com.vrp.domain.VrpSolution;

import java.time.Instant;

public class ArrivalTimeUpdatingVariableListener implements VariableListener<VrpSolution, Event> {
    
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
        if (previousStandstill instanceof Driver) {
            arrivalTime = sourceEvent.getMinStartTime();
        } else if (previousStandstill instanceof Event) {
            Event previousEvent = (Event) previousStandstill;
            if (previousEvent.getArrivalTime() == null) {
                arrivalTime = null;
            } else {
                Instant departureTime = previousEvent.getDepartureTime();
                arrivalTime = departureTime.plus(previousEvent.getLocation()
                    .getTravelTime(sourceEvent.getFromLocation(), null));
            }
        } else {
            arrivalTime = null;
        }
        
        scoreDirector.beforeVariableChanged(sourceEvent, "arrivalTime");
        sourceEvent.setArrivalTime(arrivalTime);
        scoreDirector.afterVariableChanged(sourceEvent, "arrivalTime");
        
        Event shadowEvent = findNextEvent(sourceEvent);
        while (shadowEvent != null) {
            updateArrivalTime(scoreDirector, shadowEvent);
            shadowEvent = findNextEvent(shadowEvent);
        }
    }
    
    private Event findNextEvent(Event currentEvent) {
        Driver driver = currentEvent.getDriver();
        if (driver == null || driver.getAssignedEvents() == null) {
            return null;
        }
        for (Event event : driver.getAssignedEvents()) {
            if (event.getPreviousStandstill() == currentEvent) {
                return event;
            }
        }
        return null;
    }
}
