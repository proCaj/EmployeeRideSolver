package com.vrp.constraint;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.score.stream.*;
import com.vrp.domain.Driver;
import com.vrp.domain.Event;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class VrpConstraintProvider implements ConstraintProvider {
    
    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[]{
            timeWindowConstraint(constraintFactory),
            pairingConstraint(constraintFactory),
            driverAssignmentRequired(constraintFactory),
            vehicleCapacityConstraint(constraintFactory),
            returnToHomeDistance(constraintFactory),
            excessiveIdleTime(constraintFactory),
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
    
    Constraint vehicleCapacityConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Event.class)
            .filter(event -> event.getCumulativePassengerCount() != null
                          && event.getDriver() != null
                          && event.getCumulativePassengerCount() > event.getDriver().getMaxCapacity())
            .penalizeLong(HardMediumSoftLongScore.ONE_HARD,
                event -> (long) (event.getCumulativePassengerCount() - event.getDriver().getMaxCapacity()) * 1000L)
            .asConstraint("Vehicle capacity constraint");
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

    Constraint returnToHomeDistance(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Driver.class)
            .join(Event.class,
                  Joiners.equal(driver -> driver, Event::getDriver))
            .groupBy((driver, event) -> driver,
                     ConstraintCollectors.toList((driver, event) -> event))
            .penalizeLong(HardMediumSoftLongScore.ONE_SOFT,
                     (driver, events) -> {
                         Event lastEvent = findLastEvent(events);
                         if (lastEvent == null) return 0L;
                         return lastEvent.getLocation().getHaversineDistance(driver.getLocation());
                     })
            .asConstraint("Return to home distance");
    }

    Constraint excessiveIdleTime(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Event.class)
            .filter(event -> event.getPreviousStandstill() instanceof Event)
            .filter(event -> {
                Event previous = (Event) event.getPreviousStandstill();
                if (previous.getDepartureTime() == null || event.getArrivalTime() == null) {
                    return false;
                }
                Duration idle = Duration.between(previous.getDepartureTime(), event.getArrivalTime());
                return idle.toMinutes() > 240; // More than 4 hours
            })
            .penalizeLong(HardMediumSoftLongScore.ONE_SOFT,
                     event -> {
                         Event previous = (Event) event.getPreviousStandstill();
                         Duration idle = Duration.between(previous.getDepartureTime(), event.getArrivalTime());
                         return idle.toMinutes() - 240; // Penalize minutes over 4 hours
                     })
            .asConstraint("Excessive idle time");
    }

    private Event findLastEvent(List<Event> events) {
        Set<Event> hasSuccessor = events.stream()
            .map(Event::getPreviousStandstill)
            .filter(s -> s instanceof Event)
            .map(s -> (Event) s)
            .collect(Collectors.toSet());

        return events.stream()
            .filter(e -> !hasSuccessor.contains(e))
            .findFirst()
            .orElse(null);
    }
}
