package com.vrp.domain;

import java.util.Comparator;

/**
 * Orders construction-heuristic insertion chronologically: FIRST_FIT_DECREASING assigns the
 * "most difficult" entity first, so an earlier minStartTime must rank as MORE difficult.
 *
 * <p>Why chronological: the pairing constraint hard-couples every return dropoff to its
 * outbound pickup's driver. When the CH inserts a dropoff before its pickup exists in any
 * chain, it locks in a 1M-scale hard violation that local search can only repair by passing
 * through other 1M-scale states — a wall late acceptance never crosses. Inserting events in
 * time order guarantees each pickup is already placed when its dropoff arrives, so the greedy
 * CH lands it on the matching driver instead.
 */
public class EventDifficultyComparator implements Comparator<Event> {

    @Override
    public int compare(Event a, Event b) {
        // Earlier minStartTime = more difficult (inserted first). Tie-break on id for
        // deterministic REPRODUCIBLE-mode runs.
        int byTime = b.getMinStartTime().compareTo(a.getMinStartTime());
        if (byTime != 0) {
            return byTime;
        }
        return b.getId().compareTo(a.getId());
    }
}
