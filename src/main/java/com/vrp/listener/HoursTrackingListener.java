package com.vrp.listener;

import ai.timefold.solver.core.api.domain.variable.VariableListener;
import ai.timefold.solver.core.api.score.director.ScoreDirector;
import com.vrp.domain.Driver;
import com.vrp.domain.Route;
import com.vrp.domain.VrpSolution;

import java.time.Duration;
import java.util.List;

public class HoursTrackingListener implements VariableListener<VrpSolution, Driver> {
    
    @Override
    public void beforeEntityAdded(ScoreDirector<VrpSolution> scoreDirector, Driver driver) {
    }
    
    @Override
    public void afterEntityAdded(ScoreDirector<VrpSolution> scoreDirector, Driver driver) {
        updateHours(scoreDirector, driver);
    }
    
    @Override
    public void beforeVariableChanged(ScoreDirector<VrpSolution> scoreDirector, Driver driver) {
    }
    
    @Override
    public void afterVariableChanged(ScoreDirector<VrpSolution> scoreDirector, Driver driver) {
        updateHours(scoreDirector, driver);
    }
    
    @Override
    public void beforeEntityRemoved(ScoreDirector<VrpSolution> scoreDirector, Driver driver) {
    }
    
    @Override
    public void afterEntityRemoved(ScoreDirector<VrpSolution> scoreDirector, Driver driver) {
    }
    
    protected void updateHours(ScoreDirector<VrpSolution> scoreDirector, Driver driver) {
        List<Route> routes = driver.getRoutes();
        
        if (routes == null || routes.isEmpty()) {
            scoreDirector.beforeVariableChanged(driver, "totalDailyHours");
            driver.setTotalDailyHours(Duration.ZERO);
            scoreDirector.afterVariableChanged(driver, "totalDailyHours");
            
            scoreDirector.beforeVariableChanged(driver, "totalWeeklyHours");
            driver.setTotalWeeklyHours(Duration.ZERO);
            scoreDirector.afterVariableChanged(driver, "totalWeeklyHours");
            return;
        }
        
        long totalSeconds = 0L;
        for (Route route : routes) {
            totalSeconds += route.getDurationSeconds();
        }
        
        Duration totalDuration = Duration.ofSeconds(totalSeconds);
        
        scoreDirector.beforeVariableChanged(driver, "totalDailyHours");
        driver.setTotalDailyHours(totalDuration);
        scoreDirector.afterVariableChanged(driver, "totalDailyHours");
        
        scoreDirector.beforeVariableChanged(driver, "totalWeeklyHours");
        driver.setTotalWeeklyHours(totalDuration);
        scoreDirector.afterVariableChanged(driver, "totalWeeklyHours");
    }
}
