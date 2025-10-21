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
            capacityConstraint(constraintFactory),
            timeWindowConstraint(constraintFactory),
            pairingConstraint(constraintFactory),
            dailyHoursConstraint(constraintFactory),
            weeklyHoursConstraint(constraintFactory),
            minimizeTotalDistance(constraintFactory),
            maximizeConsecutiveHours(constraintFactory),
            minimizeTotalTravelTime(constraintFactory),
            minimizeWaitingTime(constraintFactory)
        };
    }
    
    Constraint capacityConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Driver.class)
            .filter(driver -> driver.getTotalLoad() > driver.getMaxCapacity())
            .penalizeLong(HardMediumSoftLongScore.ONE_HARD,
                driver -> (driver.getTotalLoad() - driver.getMaxCapacity()))
            .asConstraint("Capacity constraint");
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
                if (pickup.getArrivalTime() == null || dropoff.getArrivalTime() == null) {
                    return false;
                }
                return dropoff.getArrivalTime().isBefore(pickup.getDepartureTime());
            })
            .penalizeLong(HardMediumSoftLongScore.ONE_HARD,
                dropoff -> {
                    Event pickup = dropoff.getPairedEvent();
                    return Duration.between(dropoff.getArrivalTime(), pickup.getDepartureTime()).getSeconds();
                })
            .asConstraint("Pairing constraint");
    }
    
    Constraint dailyHoursConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Driver.class)
            .filter(driver -> driver.getTotalDailyHours().compareTo(driver.getMaxDailyHours()) > 0)
            .penalizeLong(HardMediumSoftLongScore.ONE_HARD,
                driver -> driver.getTotalDailyHours().minus(driver.getMaxDailyHours()).getSeconds())
            .asConstraint("Daily hours constraint");
    }
    
    Constraint weeklyHoursConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Driver.class)
            .filter(driver -> driver.getTotalWeeklyHours().compareTo(driver.getMaxWeeklyHours()) > 0)
            .penalizeLong(HardMediumSoftLongScore.ONE_HARD,
                driver -> driver.getTotalWeeklyHours().minus(driver.getMaxWeeklyHours()).getSeconds())
            .asConstraint("Weekly hours constraint");
    }
    
    Constraint minimizeTotalDistance(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Driver.class)
            .penalizeLong(HardMediumSoftLongScore.ONE_SOFT,
                Driver::getTotalDistanceMeters)
            .asConstraint("Minimize total distance");
    }
    
    Constraint maximizeConsecutiveHours(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Driver.class)
            .rewardLong(HardMediumSoftLongScore.ONE_MEDIUM,
                driver -> driver.getConsecutiveWorkingHours().getSeconds())
            .asConstraint("Maximize consecutive hours");
    }
    
    Constraint minimizeTotalTravelTime(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Driver.class)
            .penalizeLong(HardMediumSoftLongScore.ONE_SOFT,
                driver -> driver.getTotalTravelTime().getSeconds() / 100)
            .asConstraint("Minimize travel time");
    }
    
    Constraint minimizeWaitingTime(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Driver.class)
            .penalizeLong(HardMediumSoftLongScore.ONE_SOFT,
                driver -> driver.getTotalWaitingTime().toMinutes() / 60)
            .asConstraint("Minimize waiting time");
    }
}
