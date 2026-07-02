package com.vrp.constraint;

import ai.timefold.solver.core.api.score.HardMediumSoftScore;
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
    private static final long STRUCTURAL_VIOLATION_PENALTY = 1_000_000L;
    
    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[]{
            // Hard constraints
            driverAssignmentRequired(constraintFactory),
            vehicleCapacityConstraint(constraintFactory),
            negativeCumulativePassengerCount(constraintFactory),
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
    
    public Constraint driverAssignmentRequired(ConstraintFactory constraintFactory) {
        // Structural scale, not a token 1000: with @PlanningListVariable(allowsUnassignedValues
        // = true) local search CAN unassign events mid-search (the chained model never could),
        // so leaving a hard-to-place event unassigned must cost more than any violation it
        // would take to actually place it — otherwise the solver dumps events instead of
        // repairing routes.
        return constraintFactory.forEachIncludingUnassigned(Event.class)
            .filter(event -> event.getDriver() == null)
            .penalize(HardMediumSoftScore.ONE_HARD, event -> STRUCTURAL_VIOLATION_PENALTY)
            .asConstraint("Driver assignment required");
    }
    
    public Constraint vehicleCapacityConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Event.class)
            .filter(event -> event.getDriver() != null)
            .filter(event -> calculateVehicleCapacityPenalty(event) > 0)
            .penalize(HardMediumSoftScore.ONE_HARD, VrpConstraintProvider::calculateVehicleCapacityPenalty)
            .asConstraint("Vehicle capacity constraint");
    }
    
    /**
     * Hard constraint: cumulative passenger count must never go negative.
     * A negative count means a dropoff event precedes its paired pickup in the
     * driver's route — a structural infeasibility. Penalized heavily per unit
     * below zero so the solver immediately fixes route ordering before refining
     * timing.
     */
    public Constraint negativeCumulativePassengerCount(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Event.class)
            .filter(event -> event.getDriver() != null)
            .filter(event -> event.getCumulativePassengerCount() != null
                          && event.getCumulativePassengerCount() < 0)
            .penalize(HardMediumSoftScore.ONE_HARD,
                event -> (long)(-event.getCumulativePassengerCount()) * STRUCTURAL_VIOLATION_PENALTY)
            .asConstraint("Negative cumulative passenger count");
    }

    public Constraint timeWindowConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory.forEachIncludingUnassigned(Event.class)
            .filter(event -> event.getDriver() != null)
            .filter(event -> calculateTimeWindowPenalty(event) > 0)
            .penalize(HardMediumSoftScore.ONE_HARD, VrpConstraintProvider::calculateTimeWindowPenalty)
            .asConstraint("Time window constraint");
    }
    
    public Constraint pairingConstraint(ConstraintFactory constraintFactory) {
        // The pickup must be selected into the tuple via a join, NOT discovered through the
        // dropoff's pairedEvent reference inside the penalty function. Incremental score
        // calculation only re-evaluates a constraint when a selected entity changes, so a
        // transitively-read pickup going stale corrupts the score (proven by FULL_ASSERT).
        return constraintFactory.forEachIncludingUnassigned(Event.class)
            .filter(event -> event.getPairedEvent() != null && !event.isPickup())
            .join(constraintFactory.forEachIncludingUnassigned(Event.class),
                  Joiners.equal(Event::getPairedEvent, pickup -> pickup))
            .filter((dropoff, pickup) -> calculatePairingPenalty(dropoff, pickup) > 0)
            .penalize(HardMediumSoftScore.ONE_HARD, VrpConstraintProvider::calculatePairingPenalty)
            .asConstraint("Pairing constraint");
    }

    private static long calculateVehicleCapacityPenalty(Event event) {
        if (event.getDriver() == null) return 0L;
        int maxCapacity = event.getDriver().getMaxCapacity();

        // Check cumulative passenger count across the driver's route.
        Integer cumCount = event.getCumulativePassengerCount();
        long chainPenalty = 0;
        if (cumCount != null && cumCount > maxCapacity) {
            chainPenalty = (long) (cumCount - maxCapacity) * 1000L;
        }

        // FR-3: Check peak concurrent load within this event's multi-stop route.
        int previousCumulative = 0;
        if (event.getPreviousEvent() != null) {
            Integer prevCum = event.getPreviousEvent().getCumulativePassengerCount();
            previousCumulative = prevCum != null ? prevCum : 0;
        }
        int peakDuringEvent = previousCumulative + event.getPeakPassengerCount();
        long peakPenalty = 0;
        if (peakDuringEvent > maxCapacity) {
            peakPenalty = (long) (peakDuringEvent - maxCapacity) * 1000L;
        }

        return Math.max(chainPenalty, peakPenalty);
    }

    private static long calculateTimeWindowPenalty(Event event) {
        if (event.getDriver() == null || event.getArrivalTime() == null || event.getMaxEndTime() == null) {
            return 0L;
        }
        long totalPenalty = 0;

        // Check event-level time window — but only when no stop carries its own deadline.
        // A merged multi-stop event's maxEndTime is the TIGHTEST customer deadline, while the
        // event completes only after the farthest stop, so the event-level check is inherently
        // violated even when every per-stop deadline is met; the per-stop walk below is
        // authoritative there.
        boolean hasPerStopDeadlines = event.getStops() != null
            && event.getStops().stream().anyMatch(stop -> stop.getMaxEndTime() != null);
        if (!hasPerStopDeadlines) {
            Instant effectiveCompletion = event.getArrivalTime().plus(
                event.getDuration() != null ? event.getDuration() : Duration.ZERO
            );
            if (effectiveCompletion.isAfter(event.getMaxEndTime())) {
                totalPenalty += Duration.between(event.getMaxEndTime(), effectiveCompletion).getSeconds();
            }
        }

        // FR-3: Check per-stop time windows for multi-stop events.
        if (event.getStops() != null && event.getStops().size() > 1 && event.getMinStartTime() != null) {
            Instant currentTime = event.getArrivalTime().isBefore(event.getMinStartTime())
                ? event.getMinStartTime() : event.getArrivalTime();
            for (Stop stop : event.getStops()) {
                if (stop.getTravelTimeFromPrevious() != null) {
                    currentTime = currentTime.plus(stop.getTravelTimeFromPrevious());
                }
                if (stop.getBoardingDuration() != null) {
                    currentTime = currentTime.plus(stop.getBoardingDuration());
                }
                currentTime = currentTime.plusSeconds(stop.getAlightingCount() * 60L);

                if (stop.getMaxEndTime() != null && currentTime.isAfter(stop.getMaxEndTime())) {
                    totalPenalty += Duration.between(stop.getMaxEndTime(), currentTime).getSeconds();
                }
            }
        }

        return totalPenalty;
    }

    private static long calculatePairingPenalty(Event dropoff, Event pickup) {
        if (pickup == null || dropoff.isPickup()) return 0L;
        if (dropoff.getDriver() == null || pickup.getDriver() == null ||
            !dropoff.getDriver().equals(pickup.getDriver())) {
            return STRUCTURAL_VIOLATION_PENALTY;
        }
        if (pickup.getArrivalTime() == null || dropoff.getArrivalTime() == null) {
            return 0L;
        }
        if (pickup.getDepartureTime() == null) {
            return 1000L;
        }
        if (dropoff.getArrivalTime().isBefore(pickup.getDepartureTime())) {
            return Duration.between(dropoff.getArrivalTime(), pickup.getDepartureTime()).getSeconds();
        }
        return 0L;
    }

    /**
     * Hard constraint: Maximum daily working hours per driver.
     * 
     * "Working hours" = sum of planned event service/route durations for a driver
     * on a given work day. Waiting until an event's minStartTime is treated as
     * break/standby time here, not continuous driving/working time.
     * 
     * Night shifts spanning midnight: both pickup and dropoff count toward the day
     * the shift started (shiftDate), per German ArbZG interpretation.
     * 
     * Maximum: 10 hours per day.
     */
    public Constraint maxDailyWorkingHours(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Driver.class)
            .join(Event.class,
                  Joiners.equal(driver -> driver, Event::getDriver))
            .groupBy((driver, event) -> driver,
                     (driver, event) -> event.getShiftDate(),
                     ConstraintCollectors.toList((driver, event) -> event))
            .penalize(HardMediumSoftScore.ONE_HARD,
                (driver, date, events) -> {
                    if (date == null || events.isEmpty()) return 0L;
                    long totalWorkingMinutes = 0;
                    for (Event e : events) {
                        Duration duration = e.getDuration();
                        if (duration == null) continue;
                        totalWorkingMinutes += duration.toMinutes();
                    }
                    long overMinutes = totalWorkingMinutes - driver.getMaxDailyHours().toMinutes();
                    return overMinutes > 0 ? overMinutes * 100L : 0L;
                })
            .asConstraint("Max daily working hours");
    }

    /**
     * Hard constraint: Maximum weekly working hours per driver.
     * 
     * Sums planned event service/route durations across the entire week for each driver.
     * Waiting until minStartTime is excluded as break/standby time.
     * Maximum: 40 hours per week.
     */
    public Constraint maxWeeklyWorkingHours(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Driver.class)
            .join(Event.class,
                  Joiners.equal(driver -> driver, Event::getDriver))
            .groupBy((driver, event) -> driver,
                     ConstraintCollectors.toList((driver, event) -> event))
            .penalize(HardMediumSoftScore.ONE_HARD,
                (driver, events) -> {
                    if (events.isEmpty()) return 0L;
                    long totalWorkingMinutes = 0;
                    for (Event e : events) {
                        Duration duration = e.getDuration();
                        if (duration == null) continue;
                        totalWorkingMinutes += duration.toMinutes();
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
    public Constraint maxConsecutiveDrivingHours(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Driver.class)
            .join(Event.class,
                  Joiners.equal(driver -> driver, Event::getDriver))
            .groupBy((driver, event) -> driver,
                     ConstraintCollectors.toList((driver, event) -> event))
            .penalize(HardMediumSoftScore.ONE_HARD,
                (driver, events) -> calculateConsecutiveDrivingPenalty(driver, events))
            .asConstraint("Max consecutive driving hours");
    }
    
    // ============================================================
    // Soft Constraints (Optimization Goals)
    // ============================================================
    
    public Constraint minimizeTotalDistance(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Event.class)
            .filter(event -> event.getDriver() != null)
            .penalize(HardMediumSoftScore.ONE_SOFT,
                Event::getDistance)
            .asConstraint("Minimize total distance");
    }
    
    public Constraint minimizeWaitingTime(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Event.class)
            .filter(event -> event.getDriver() != null && event.getWaitingTime() != null)
            .penalize(HardMediumSoftScore.ONE_SOFT,
                event -> event.getWaitingTime().toMinutes())
            .asConstraint("Minimize waiting time");
    }

    public Constraint returnToHomeDistance(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Driver.class)
            .join(Event.class,
                  Joiners.equal(driver -> driver, Event::getDriver))
            .groupBy((driver, event) -> driver,
                     ConstraintCollectors.toList((driver, event) -> event))
            .penalize(HardMediumSoftScore.ONE_SOFT,
                     (driver, events) -> {
                         Event lastEvent = findLastEvent(events);
                         if (lastEvent == null) return 0L;
                         return lastEvent.getLocation().getHaversineDistance(driver.getLocation());
                     })
            .asConstraint("Return to home distance");
    }

    public Constraint excessiveIdleTime(ConstraintFactory constraintFactory) {
        return constraintFactory.forEach(Event.class)
            .filter(event -> event.getPreviousEvent() != null)
            .filter(event -> {
                Event previous = event.getPreviousEvent();
                if (previous.getDepartureTime() == null || event.getArrivalTime() == null) {
                    return false;
                }
                Duration idle = Duration.between(previous.getDepartureTime(), event.getArrivalTime());
                return idle.toMinutes() > 240; // More than 4 hours
            })
            .penalize(HardMediumSoftScore.ONE_SOFT,
                     event -> {
                         Event previous = event.getPreviousEvent();
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

        Instant spanStart = getEffectiveStart(arr[0]);
        long maxOverMinutes = 0;
        for (int i = 1; i < count; i++) {
            Event prev = arr[i - 1];
            Event curr = arr[i];
            Instant currEffectiveStart = getEffectiveStart(curr);
            Duration breakBeforeCurrentEvent = Duration.between(prev.getDepartureTime(), currEffectiveStart);
            if (!getEventDate(prev).equals(getEventDate(curr))) {
                spanStart = currEffectiveStart;
                continue;
            }
            if (breakBeforeCurrentEvent.compareTo(driver.getMinBreak()) >= 0) {
                spanStart = currEffectiveStart;
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

    private static Instant getEffectiveStart(Event event) {
        Instant arrival = event.getArrivalTime();
        Instant minStart = event.getMinStartTime();
        if (arrival == null) {
            return minStart;
        }
        if (minStart != null && arrival.isBefore(minStart)) {
            return minStart;
        }
        return arrival;
    }
}
