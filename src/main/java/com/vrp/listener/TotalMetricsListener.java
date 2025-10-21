package com.vrp.listener;

import ai.timefold.solver.core.api.domain.variable.VariableListener;
import ai.timefold.solver.core.api.score.director.ScoreDirector;
import com.vrp.domain.Driver;
import com.vrp.domain.Event;
import com.vrp.domain.Route;
import com.vrp.domain.VrpSolution;

import java.time.Duration;
import java.util.List;

public class TotalMetricsListener implements VariableListener<VrpSolution, Driver> {
    
    @Override
    public void beforeEntityAdded(ScoreDirector<VrpSolution> scoreDirector, Driver driver) {
    }
    
    @Override
    public void afterEntityAdded(ScoreDirector<VrpSolution> scoreDirector, Driver driver) {
        updateMetrics(scoreDirector, driver);
    }
    
    @Override
    public void beforeVariableChanged(ScoreDirector<VrpSolution> scoreDirector, Driver driver) {
    }
    
    @Override
    public void afterVariableChanged(ScoreDirector<VrpSolution> scoreDirector, Driver driver) {
        updateMetrics(scoreDirector, driver);
    }
    
    @Override
    public void beforeEntityRemoved(ScoreDirector<VrpSolution> scoreDirector, Driver driver) {
    }
    
    @Override
    public void afterEntityRemoved(ScoreDirector<VrpSolution> scoreDirector, Driver driver) {
    }
    
    protected void updateMetrics(ScoreDirector<VrpSolution> scoreDirector, Driver driver) {
        List<Route> routes = driver.getRoutes();
        
        if (routes == null || routes.isEmpty()) {
            scoreDirector.beforeVariableChanged(driver, "totalDistanceMeters");
            driver.setTotalDistanceMeters(0L);
            scoreDirector.afterVariableChanged(driver, "totalDistanceMeters");
            
            scoreDirector.beforeVariableChanged(driver, "totalTravelTime");
            driver.setTotalTravelTime(Duration.ZERO);
            scoreDirector.afterVariableChanged(driver, "totalTravelTime");
            
            scoreDirector.beforeVariableChanged(driver, "consecutiveWorkingHours");
            driver.setConsecutiveWorkingHours(Duration.ZERO);
            scoreDirector.afterVariableChanged(driver, "consecutiveWorkingHours");
            
            scoreDirector.beforeVariableChanged(driver, "totalWaitingTime");
            driver.setTotalWaitingTime(Duration.ZERO);
            scoreDirector.afterVariableChanged(driver, "totalWaitingTime");
            return;
        }
        
        long totalDistance = 0L;
        long totalTravelSeconds = 0L;
        long maxConsecutiveSeconds = 0L;
        long totalWaitingSeconds = 0L;
        
        for (Route route : routes) {
            List<Event> events = route.getEvents();
            if (events == null || events.isEmpty()) continue;
            
            long routeDistance = 0L;
            long routeDuration = 0L;
            
            for (Event event : events) {
                routeDistance += event.getDistance();
                if (event.getDuration() != null) {
                    routeDuration += event.getDuration().getSeconds();
                }
                Duration waitingTime = event.getWaitingTime();
                if (waitingTime != null) {
                    totalWaitingSeconds += waitingTime.getSeconds();
                }
            }
            
            totalDistance += routeDistance;
            totalTravelSeconds += routeDuration;
            maxConsecutiveSeconds = Math.max(maxConsecutiveSeconds, routeDuration);
        }
        
        scoreDirector.beforeVariableChanged(driver, "totalDistanceMeters");
        driver.setTotalDistanceMeters(totalDistance);
        scoreDirector.afterVariableChanged(driver, "totalDistanceMeters");
        
        scoreDirector.beforeVariableChanged(driver, "totalTravelTime");
        driver.setTotalTravelTime(Duration.ofSeconds(totalTravelSeconds));
        scoreDirector.afterVariableChanged(driver, "totalTravelTime");
        
        scoreDirector.beforeVariableChanged(driver, "consecutiveWorkingHours");
        driver.setConsecutiveWorkingHours(Duration.ofSeconds(maxConsecutiveSeconds));
        scoreDirector.afterVariableChanged(driver, "consecutiveWorkingHours");
        
        scoreDirector.beforeVariableChanged(driver, "totalWaitingTime");
        driver.setTotalWaitingTime(Duration.ofSeconds(totalWaitingSeconds));
        scoreDirector.afterVariableChanged(driver, "totalWaitingTime");
    }
}
