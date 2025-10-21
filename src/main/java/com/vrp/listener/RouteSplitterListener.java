package com.vrp.listener;

import ai.timefold.solver.core.api.domain.variable.VariableListener;
import ai.timefold.solver.core.api.score.director.ScoreDirector;
import com.vrp.domain.Driver;
import com.vrp.domain.Event;
import com.vrp.domain.Route;
import com.vrp.domain.VrpSolution;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

public class RouteSplitterListener implements VariableListener<VrpSolution, Driver> {
    
    @Override
    public void beforeEntityAdded(ScoreDirector<VrpSolution> scoreDirector, Driver driver) {
    }
    
    @Override
    public void afterEntityAdded(ScoreDirector<VrpSolution> scoreDirector, Driver driver) {
        updateRoutes(scoreDirector, driver);
    }
    
    @Override
    public void beforeVariableChanged(ScoreDirector<VrpSolution> scoreDirector, Driver driver) {
    }
    
    @Override
    public void afterVariableChanged(ScoreDirector<VrpSolution> scoreDirector, Driver driver) {
        updateRoutes(scoreDirector, driver);
    }
    
    @Override
    public void beforeEntityRemoved(ScoreDirector<VrpSolution> scoreDirector, Driver driver) {
    }
    
    @Override
    public void afterEntityRemoved(ScoreDirector<VrpSolution> scoreDirector, Driver driver) {
    }
    
    protected void updateRoutes(ScoreDirector<VrpSolution> scoreDirector, Driver driver) {
        List<Event> events = driver.getEvents();
        List<Route> routes = new ArrayList<>();
        
        if (events == null || events.isEmpty()) {
            scoreDirector.beforeVariableChanged(driver, "routes");
            driver.setRoutes(routes);
            scoreDirector.afterVariableChanged(driver, "routes");
            return;
        }
        
        List<Event> currentRouteEvents = new ArrayList<>();
        long currentRouteDuration = 0L;
        Duration maxConsecutiveHours = driver.getMaxConsecutiveHours();
        
        for (Event event : events) {
            Duration eventDuration = event.getDuration();
            long eventDurationSeconds = eventDuration != null ? eventDuration.getSeconds() : 0L;
            
            if (currentRouteDuration + eventDurationSeconds > maxConsecutiveHours.getSeconds() && !currentRouteEvents.isEmpty()) {
                routes.add(new Route(currentRouteEvents, currentRouteDuration));
                currentRouteEvents = new ArrayList<>();
                currentRouteDuration = 0L;
            }
            
            currentRouteEvents.add(event);
            currentRouteDuration += eventDurationSeconds;
        }
        
        if (!currentRouteEvents.isEmpty()) {
            routes.add(new Route(currentRouteEvents, currentRouteDuration));
        }
        
        scoreDirector.beforeVariableChanged(driver, "routes");
        driver.setRoutes(routes);
        scoreDirector.afterVariableChanged(driver, "routes");
    }
}
