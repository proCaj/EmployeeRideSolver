package com.vrp.constraint;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.score.stream.*;
import com.vrp.domain.Driver;
import com.vrp.domain.Event;

import java.time.Duration;
import java.time.Instant;

public class VrpConstraintProvider implements ConstraintProvider {
    
    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[]{
            timeWindowConstraint(constraintFactory),
            pairingConstraint(constraintFactory),
            driverAssignmentRequired(constraintFactory),
            minimizeTotalDistance(constraintFactory),
            minimizeWaitingTime(constraintFactory)
        };
    }
    
    Constraint driverAssignmentRequired(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Event.class)
            .filter(event -> event.getDriver() == null)
            .penalizeLong(HardMediumSoftLongScore.ONE_HARD, event -> 1000L)
            .asConstraint("Driver assignment required");
    }
    
    Constraint timeWindowConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Event.class)
            .filter(event -> {
                if (event.getArrivalTime() == null) return false;
                Instant effectiveCompletion = event.getArrivalTime().plus(
                    event.getDuration() != null ? event.getDuration() : Duration.ZERO
                );
                return effectiveCompletion.isAfter(event.getMaxEndTime());
            })
            .penalizeLong(HardMediumSoftLongScore.ONE_HARD,
                event -> {
                    Instant effectiveCompletion = event.getArrivalTime().plus(
                        event.getDuration() != null ? event.getDuration() : Duration.ZERO
                    );
                    return Duration.between(event.getMaxEndTime(), effectiveCompletion).getSeconds();
                })
            .asConstraint("Time window constraint");
    }
    
    Constraint pairingConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Event.class)
            .filter(event -> event.getPairedEvent() != null && !event.isPickup())
            .filter(dropoff -> {
                Event pickup = dropoff.getPairedEvent();
                if (dropoff.getDriver() == null || pickup.getDriver() == null) {
                    return false;
                }
                if (!dropoff.getDriver().equals(pickup.getDriver())) {
                    return true;
                }
                if (pickup.getArrivalTime() == null || dropoff.getArrivalTime() == null) {
                    return false;
                }
                return dropoff.getArrivalTime().isBefore(pickup.getDepartureTime());
            })
            .penalizeLong(HardMediumSoftLongScore.ONE_HARD,
                dropoff -> {
                    Event pickup = dropoff.getPairedEvent();
                    if (dropoff.getDriver() == null || pickup.getDriver() == null ||
                        !dropoff.getDriver().equals(pickup.getDriver())) {
                        return 10000L;
                    }
                    if (pickup.getDepartureTime() == null || dropoff.getArrivalTime() == null) {
                        return 1000L;
                    }
                    return Duration.between(dropoff.getArrivalTime(), pickup.getDepartureTime()).getSeconds();
                })
            .asConstraint("Pairing constraint");
    }
    
    Constraint minimizeTotalDistance(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Event.class)
            .filter(event -> event.getDriver() != null)
            .penalizeLong(HardMediumSoftLongScore.ONE_SOFT,
                Event::getDistance)
            .asConstraint("Minimize total distance");
    }
    
    Constraint minimizeWaitingTime(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Event.class)
            .filter(event -> event.getDriver() != null && event.getWaitingTime() != null)
            .penalizeLong(HardMediumSoftLongScore.ONE_SOFT,
                event -> event.getWaitingTime().toMinutes())
            .asConstraint("Minimize waiting time");
    }
}
