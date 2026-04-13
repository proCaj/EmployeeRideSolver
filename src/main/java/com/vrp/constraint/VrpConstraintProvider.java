package com.vrp.constraint;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoftlong.HardMediumSoftLongScore;
import ai.timefold.solver.core.api.score.stream.*;
import com.vrp.domain.Driver;
import com.vrp.domain.Event;
import com.vrp.domain.Stop;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

public class VrpConstraintProvider implements ConstraintProvider {
    
    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[]{
            // Hard constraints
            driverAssignmentRequired(constraintFactory),
            vehicleCapacityConstraint(constraintFactory),
            timeWindowConstraint(constraintFactory),
            pairingConstraint(constraintFactory),
            maxDailyWorkingHours(constraintFactory),
            maxWeeklyWorkingHours(constraintFactory),
            maxConsecutiveDrivingHours(constraintFactory),
            // Soft constraints (optimization goals)
            minimizeTotalDistance(constraintFactory),
            minimizeWaitingTime(constraintFactory),
            returnToHomeDistance(constraintFactory),
            excessiveIdleTime(constraintFactory)
        };
    }
    
    // ============================================================
    // Hard Constraints
    // ============================================================
    
    Constraint driverAssignmentRequired(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Event.class)
            .filter(event -> event.getDriver() == null)
            .penalizeLong(HardMediumSoftLongScore.ONE_HARD, event -> 1000L)
            .asConstraint("Driver assignment required");
    }
    
    Constraint vehicleCapacityConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Event.class)
            .filter(event -> event.getDriver() != null)
            .penalizeLong(HardMediumSoftLongScore.ONE_HARD,
                event -> {
                    int maxCapacity = event.getDriver().getMaxCapacity();

                    // Check cumulative passenger count across the driver's chain
                    Integer cumCount = event.getCumulativePassengerCount();
                    long chainPenalty = 0;
                    if (cumCount != null && cumCount > maxCapacity) {
                        chainPenalty = (long) (cumCount - maxCapacity) * 1000L;
                    }

                    // FR-3: Check peak concurrent load within this event's multi-stop route.
                    // The vehicle must have enough spare capacity at the point of peak load.
                    // Peak load = previousEvent's cumulative + this event's peak concurrent.
                    int previousCumulative = 0;
                    if (event.getPreviousStandstill() instanceof Event) {
                        Integer prevCum = ((Event) event.getPreviousStandstill()).getCumulativePassengerCount();
                        previousCumulative = prevCum != null ? prevCum : 0;
                    }
                    int peakDuringEvent = previousCumulative + event.getPeakPassengerCount();
                    long peakPenalty = 0;
                    if (peakDuringEvent > maxCapacity) {
                        peakPenalty = (long) (peakDuringEvent - maxCapacity) * 1000L;
                    }

                    return Math.max(chainPenalty, peakPenalty);
                })
            .asConstraint("Vehicle capacity constraint");
    }
    
    Constraint timeWindowConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Event.class)
            .filter(event -> event.getDriver() != null)
            .penalizeLong(HardMediumSoftLongScore.ONE_HARD,
                event -> {
                    long totalPenalty = 0;

                    // Check event-level time window (legacy single-stop)
                    if (event.getArrivalTime() != null) {
                        Instant effectiveCompletion = event.getArrivalTime().plus(
                            event.getDuration() != null ? event.getDuration() : Duration.ZERO
                        );
                        if (effectiveCompletion.isAfter(event.getMaxEndTime())) {
                            totalPenalty += Duration.between(event.getMaxEndTime(), effectiveCompletion).getSeconds();
                        }
                    }

                    // FR-3: Check per-stop time windows for multi-stop events
                    if (event.getStops() != null && event.getStops().size() > 1 && event.getArrivalTime() != null) {
                        Instant currentTime = event.getArrivalTime().isBefore(event.getMinStartTime())
                            ? event.getMinStartTime() : event.getArrivalTime();
                        for (Stop stop : event.getStops()) {
                            // Add travel time to this stop
                            if (stop.getTravelTimeFromPrevious() != null) {
                                currentTime = currentTime.plus(stop.getTravelTimeFromPrevious());
                            }
                            // Add boarding/alighting time
                            if (stop.getBoardingDuration() != null) {
                                currentTime = currentTime.plus(stop.getBoardingDuration());
                            }
                            currentTime = currentTime.plusSeconds(stop.getAlightingCount() * 60L);

                            // Check deadline
                            if (stop.getMaxEndTime() != null && currentTime.isAfter(stop.getMaxEndTime())) {
                                totalPenalty += Duration.between(stop.getMaxEndTime(), currentTime).getSeconds();
                            }
                        }
                    }

                    return totalPenalty;
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

    /**
     * Hard constraint: Maximum daily working hours per driver.
     * 
     * "Working hours" = sum of actual event durations for a driver on a given work day.
     * Each event's duration = departureTime - arrivalTime (includes travel, boarding, waiting).
     * Events are grouped by (driver, shiftDate) for per-day accounting.
     * 
     * Night shifts spanning midnight: both pickup and dropoff count toward the day
     * the shift started (shiftDate), per German ArbZG interpretation.
     * 
     * Maximum: 10 hours per day.
     */
    Constraint maxDailyWorkingHours(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Driver.class)
            .join(Event.class,
                  Joiners.equal(driver -> driver, Event::getDriver))
            .groupBy((driver, event) -> driver,
                     (driver, event) -> event.getShiftDate(),
                     ConstraintCollectors.toList((driver, event) -> event))
            .penalizeLong(HardMediumSoftLongScore.ONE_HARD,
                (driver, date, events) -> {
                    if (date == null || events.isEmpty()) return 0L;
                    long totalWorkingMinutes = 0;
                    for (Event e : events) {
                        Instant arrival = e.getArrivalTime();
                        Instant departure = e.getDepartureTime();
                        if (arrival == null || departure == null) continue;
                        totalWorkingMinutes += (departure.getEpochSecond() - arrival.getEpochSecond()) / 60;
                    }
                    long overMinutes = totalWorkingMinutes - driver.getMaxDailyHours().toMinutes();
                    return overMinutes > 0 ? overMinutes * 100L : 0L;
                })
            .asConstraint("Max daily working hours");
    }

    /**
     * Hard constraint: Maximum weekly working hours per driver.
     * 
     * Sums actual event durations across the entire week for each driver.
     * Maximum: 40 hours per week.
     */
    Constraint maxWeeklyWorkingHours(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Driver.class)
            .join(Event.class,
                  Joiners.equal(driver -> driver, Event::getDriver))
            .groupBy((driver, event) -> driver,
                     ConstraintCollectors.toList((driver, event) -> event))
            .penalizeLong(HardMediumSoftLongScore.ONE_HARD,
                (driver, events) -> {
                    if (events.isEmpty()) return 0L;
                    long totalWorkingMinutes = 0;
                    for (Event e : events) {
                        Instant arrival = e.getArrivalTime();
                        Instant departure = e.getDepartureTime();
                        if (arrival == null || departure == null) continue;
                        totalWorkingMinutes += (departure.getEpochSecond() - arrival.getEpochSecond()) / 60;
                    }
                    long overMinutes = totalWorkingMinutes - driver.getMaxWeeklyHours().toMinutes();
                    return overMinutes > 0 ? overMinutes * 100L : 0L;
                })
            .asConstraint("Max weekly working hours");
    }

    /**
     * Hard constraint: Maximum consecutive driving hours without a break.
     * 
     * Walks through a driver's events chronologically. If consecutive events
     * have a gap < minBreak (30 min), they count as continuous driving.
     * If the continuous span exceeds maxConsecutiveHours (4h), it's penalized.
     * A gap >= minBreak resets the consecutive driving counter.
     * 
     * Consecutive driving only applies within the same shift date.
     * Events on different work days (per shiftDate) never count as consecutive.
     */
    Constraint maxConsecutiveDrivingHours(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Driver.class)
            .join(Event.class,
                  Joiners.equal(driver -> driver, Event::getDriver))
            .groupBy((driver, event) -> driver,
                     ConstraintCollectors.toList((driver, event) -> event))
            .penalizeLong(HardMediumSoftLongScore.ONE_HARD,
                (driver, events) -> calculateConsecutiveDrivingPenalty(driver, events))
            .asConstraint("Max consecutive driving hours");
    }
    
    // ============================================================
    // Soft Constraints (Optimization Goals)
    // ============================================================
    
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

    // ============================================================
    // Helper Methods
    // ============================================================

    /**
     * Extracts the date from an event for per-day grouping.
     * Uses shiftDate (the canonical work-day) for daily constraint accounting.
     * Falls back to deriving from minStartTime if shiftDate is null (legacy compatibility).
     */
    private static LocalDate getEventDate(Event event) {
        return event.getShiftDate(); // always set by EventGenerationService
    }

    private Event findLastEvent(List<Event> events) {
        Event latest = null;
        Instant latestDeparture = null;
        for (Event e : events) {
            Instant dep = e.getDepartureTime();
            if (dep != null && (latestDeparture == null || dep.isAfter(latestDeparture))) {
                latestDeparture = dep;
                latest = e;
            }
        }
        return latest;
    }

    /**
     * Calculates the penalty for consecutive driving hours violations.
     */
    private long calculateConsecutiveDrivingPenalty(Driver driver, List<Event> events) {
        int count = 0;
        Event[] arr = new Event[events.size()];
        for (Event e : events) {
            if (e.getArrivalTime() != null && e.getDepartureTime() != null) {
                arr[count++] = e;
            }
        }
        if (count < 2) return 0L;
        Arrays.sort(arr, 0, count, Comparator.comparing(Event::getArrivalTime));

        Instant spanStart = arr[0].getArrivalTime();
        long maxOverMinutes = 0;
        for (int i = 1; i < count; i++) {
            Event prev = arr[i - 1];
            Event curr = arr[i];
            Duration gap = Duration.between(prev.getDepartureTime(), curr.getArrivalTime());
            if (!getEventDate(prev).equals(getEventDate(curr))) {
                spanStart = curr.getArrivalTime();
                continue;
            }
            if (gap.compareTo(driver.getMinBreak()) >= 0) {
                spanStart = curr.getArrivalTime();
            } else {
                Duration consecutiveSpan = Duration.between(spanStart, curr.getDepartureTime());
                long over = consecutiveSpan.toMinutes() - driver.getMaxConsecutiveHours().toMinutes();
                if (over > maxOverMinutes) {
                    maxOverMinutes = over;
                }
            }
        }
        return maxOverMinutes * 100L;
    }
}
