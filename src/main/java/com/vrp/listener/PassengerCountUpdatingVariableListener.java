package com.vrp.listener;

import ai.timefold.solver.core.api.domain.variable.VariableListener;
import ai.timefold.solver.core.api.score.director.ScoreDirector;
import com.vrp.domain.Event;
import com.vrp.domain.Standstill;
import com.vrp.domain.VrpSolution;

public class PassengerCountUpdatingVariableListener
        implements VariableListener<VrpSolution, Event> {

    @Override
    public void beforeVariableChanged(ScoreDirector<VrpSolution> scoreDirector, Event event) {
        // No action needed
    }

    @Override
    public void afterVariableChanged(ScoreDirector<VrpSolution> scoreDirector, Event event) {
        updatePassengerCount(scoreDirector, event);
    }

    @Override
    public void beforeEntityAdded(ScoreDirector<VrpSolution> scoreDirector, Event event) {
        // No action needed
    }

    @Override
    public void afterEntityAdded(ScoreDirector<VrpSolution> scoreDirector, Event event) {
        updatePassengerCount(scoreDirector, event);
    }

    @Override
    public void beforeEntityRemoved(ScoreDirector<VrpSolution> scoreDirector, Event event) {
        // No action needed
    }

    @Override
    public void afterEntityRemoved(ScoreDirector<VrpSolution> scoreDirector, Event event) {
        // No action needed
    }

    private void updatePassengerCount(ScoreDirector<VrpSolution> scoreDirector, Event event) {
        Standstill previous = event.getPreviousStandstill();

        int previousCount = 0;
        if (previous instanceof Event) {
            Integer prevCumulative = ((Event) previous).getCumulativePassengerCount();
            previousCount = prevCumulative != null ? prevCumulative : 0;
        }
        // If previous is Driver, count starts at 0

        int newCount = previousCount + event.getPassengerDelta();

        Integer currentCount = event.getCumulativePassengerCount();
        if (currentCount == null || currentCount.intValue() != newCount) {
            scoreDirector.beforeVariableChanged(event, "cumulativePassengerCount");
            event.setCumulativePassengerCount(newCount);
            scoreDirector.afterVariableChanged(event, "cumulativePassengerCount");
        }

        // Propagate to next event in chain
        Event nextEvent = event.getNextEvent();
        if (nextEvent != null) {
            updatePassengerCount(scoreDirector, nextEvent);
        }
    }
}
