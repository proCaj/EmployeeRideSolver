package com.vrp.listener;

import ai.timefold.solver.core.api.domain.variable.VariableListener;
import ai.timefold.solver.core.api.score.director.ScoreDirector;
import com.vrp.domain.Event;
import com.vrp.domain.Standstill;
import com.vrp.domain.VrpSolution;

import java.util.Objects;

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

        // Must stay a pure function of the source variable, mirroring
        // ArrivalTimeUpdatingVariableListener: unassigned (or downstream of
        // unassigned) events keep null, otherwise FULL_ASSERT flags the stored
        // null vs recomputed value as shadow-variable corruption.
        Integer newCount;
        if (previous == null) {
            newCount = null;
        } else if (previous instanceof Event) {
            Integer prevCumulative = ((Event) previous).getCumulativePassengerCount();
            newCount = prevCumulative == null ? null : prevCumulative + event.getPassengerDelta();
        } else {
            // Previous is the Driver anchor: chain starts at this event's delta
            newCount = event.getPassengerDelta();
        }

        Integer currentCount = event.getCumulativePassengerCount();
        if (!Objects.equals(currentCount, newCount)) {
            scoreDirector.beforeVariableChanged(event, "cumulativePassengerCount");
            event.setCumulativePassengerCount(newCount);
            scoreDirector.afterVariableChanged(event, "cumulativePassengerCount");
        }

        // Propagate to next event in chain
        Event nextEvent = findNextEvent(scoreDirector.getWorkingSolution(), event);
        if (nextEvent != null) {
            updatePassengerCount(scoreDirector, nextEvent);
        }
    }

    /** See ArrivalTimeUpdatingVariableListener for why this avoids inverse shadows. */
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
}
