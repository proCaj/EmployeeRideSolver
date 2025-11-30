package com.vrp.service;

import com.vrp.domain.Driver;
import com.vrp.domain.Event;
import com.vrp.domain.Location;
import com.vrp.domain.VrpSolution;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@ApplicationScoped
public class SolutionDiagnosticService {

    public DiagnosticReport analyze(VrpSolution solution) {
        List<Event> unassigned = solution.getEvents().stream()
                .filter(e -> e.getDriver() == null)
                .collect(Collectors.toList());

        if (unassigned.isEmpty()) {
            return new DiagnosticReport(Collections.emptyList(), Collections.emptyList());
        }

        List<UnassignedEventReason> reasons = new ArrayList<>();

        for (Event event : unassigned) {
            UnassignedEventReason reason = diagnoseEvent(event, solution);
            reasons.add(reason);
        }

        List<String> suggestions = generateSuggestions(reasons, solution);

        return new DiagnosticReport(reasons, suggestions);
    }

    private UnassignedEventReason diagnoseEvent(Event event, VrpSolution solution) {
        // Check each potential reason

        // 1. No drivers available at all
        if (solution.getDrivers().isEmpty()) {
            return new UnassignedEventReason(
                event,
                "No drivers available",
                "Add at least one employee with DRIVER type to enable route planning"
            );
        }

        // 2. Capacity would be exceeded on all possible routes
        boolean capacityIssue = checkCapacityIssue(event, solution);
        if (capacityIssue) {
            return new UnassignedEventReason(
                event,
                "Capacity exceeded - passenger count exceeds all driver capacities",
                String.format("This event requires %d passenger slots but all drivers have maximum capacity of %d",
                    event.getPassengerCount(),
                    solution.getDrivers().stream().mapToLong(Driver::getMaxCapacity).max().orElse(0))
            );
        }

        // 3. Time window conflict with all drivers
        boolean timeConflict = checkTimeWindowConflict(event, solution);
        if (timeConflict) {
            return new UnassignedEventReason(
                event,
                "Time window conflict - no driver can reach this event in time",
                String.format("Event requires arrival between %s and %s, but no driver can reach it within this window",
                    event.getMinStartTime(),
                    event.getMaxEndTime())
            );
        }

        // 4. Pairing constraint issue
        if (event.getPairedEvent() != null && !event.isPickup()) {
            Event pickup = event.getPairedEvent();
            if (pickup.getDriver() == null) {
                return new UnassignedEventReason(
                    event,
                    "Paired pickup event is unassigned",
                    "The corresponding pickup event must be assigned to a driver before this dropoff can be assigned"
                );
            }
        }

        // 5. Unknown reason
        return new UnassignedEventReason(
            event,
            "Unknown - solver could not find feasible assignment",
            "Try increasing solver runtime, adding more drivers, or relaxing time window constraints"
        );
    }

    private boolean checkTimeWindowConflict(Event event, VrpSolution solution) {
        // Check if any driver could theoretically reach this event from their home location
        for (Driver driver : solution.getDrivers()) {
            // Calculate minimum travel time from driver home to event start
            long distance = driver.getLocation().getHaversineDistance(event.getFromLocation());
            long travelTimeSeconds = distance / 15; // Average speed 15 m/s
            Duration minTravelTime = Duration.ofSeconds(travelTimeSeconds);

            // Assume driver could start at earliest event time minus travel time
            Instant earliestPossibleArrival = event.getMinStartTime().plus(minTravelTime);

            // If the driver could theoretically arrive before max end time, no conflict
            if (earliestPossibleArrival.isBefore(event.getMaxEndTime())) {
                return false;
            }
        }
        return true;
    }

    private boolean checkCapacityIssue(Event event, VrpSolution solution) {
        // Check if this is a pickup that would exceed capacity everywhere
        if (!event.isPickup()) return false;

        int passengerCount = event.getPassengerCount();
        for (Driver driver : solution.getDrivers()) {
            if (passengerCount <= driver.getMaxCapacity()) {
                return false;
            }
        }
        return true;
    }

    private List<String> generateSuggestions(List<UnassignedEventReason> reasons,
                                              VrpSolution solution) {
        List<String> suggestions = new ArrayList<>();

        long capacityIssues = reasons.stream()
                .filter(r -> r.reason().contains("Capacity"))
                .count();

        long timeIssues = reasons.stream()
                .filter(r -> r.reason().contains("Time window"))
                .count();

        long noDriverIssues = reasons.stream()
                .filter(r -> r.reason().contains("No drivers"))
                .count();

        long pairingIssues = reasons.stream()
                .filter(r -> r.reason().contains("Paired"))
                .count();

        if (noDriverIssues > 0) {
            suggestions.add("Add drivers: Mark employees as DRIVER type to create available vehicles for route planning");
        }

        if (capacityIssues > 0) {
            int totalUnassignedPassengers = reasons.stream()
                    .filter(r -> r.reason().contains("Capacity"))
                    .mapToInt(r -> r.event().getPassengerCount())
                    .sum();

            long maxCapacity = solution.getDrivers().stream()
                    .mapToLong(Driver::getMaxCapacity)
                    .max()
                    .orElse(6);

            int additionalDrivers = (int) Math.ceil((double) totalUnassignedPassengers / maxCapacity);

            suggestions.add(String.format(
                "Capacity shortage: %d events unassigned due to capacity constraints. Adding %d driver(s) would likely resolve this",
                capacityIssues, additionalDrivers));
        }

        if (timeIssues > 0) {
            suggestions.add(String.format(
                "Time window conflicts: %d events have time windows that cannot be met. Consider adjusting shift times, adding drivers in different locations, or increasing early arrival buffers",
                timeIssues));
        }

        if (pairingIssues > 0) {
            suggestions.add(String.format(
                "Pairing issues: %d dropoff events cannot be assigned because their pickup events are unassigned. Resolve pickup assignment issues first",
                pairingIssues));
        }

        if (reasons.size() > 0 && suggestions.isEmpty()) {
            suggestions.add(String.format(
                "General: %d events could not be assigned. Try increasing solver runtime (currently limited), adding more drivers, or reviewing constraint configurations",
                reasons.size()));
        }

        return suggestions;
    }

    public record UnassignedEventReason(Event event, String reason, String detail) {
        public String getEventId() {
            return event.getId();
        }

        public String getEventType() {
            return event.isPickup() ? "Pickup" : "Dropoff";
        }

        public String getEmployeeNames() {
            return event.getPassengerNames();
        }

        public String getLocation() {
            return event.getToLocation().name();
        }
    }

    public record DiagnosticReport(
        List<UnassignedEventReason> unassignedReasons,
        List<String> suggestions
    ) {
        public int getTotalUnassigned() {
            return unassignedReasons.size();
        }

        public boolean hasIssues() {
            return !unassignedReasons.isEmpty();
        }
    }
}
