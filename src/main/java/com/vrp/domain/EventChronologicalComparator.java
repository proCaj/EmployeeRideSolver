package com.vrp.domain;

import java.util.Comparator;

/**
 * Natural chronological order (earliest minStartTime first). Used by solverConfig.xml's
 * construction-heuristic value selector (SORTED + ASCENDING) so events are inserted into
 * the drivers' route lists in time order.
 *
 * <p>Why chronological: the pairing constraint hard-couples every return dropoff to its
 * outbound pickup's driver. When the CH inserts a dropoff before its pickup exists in any
 * route, it locks in a 1M-scale hard violation that local search can only repair by passing
 * through other 1M-scale states — a wall late acceptance never crosses. Inserting events in
 * time order guarantees each pickup is already placed when its dropoff arrives, so the greedy
 * CH lands it on the matching driver instead.
 */
public class EventChronologicalComparator implements Comparator<Event> {

    @Override
    public int compare(Event a, Event b) {
        int byTime = a.getMinStartTime().compareTo(b.getMinStartTime());
        if (byTime != 0) {
            return byTime;
        }
        // Tie-break on id for deterministic runs.
        return a.getId().compareTo(b.getId());
    }
}
