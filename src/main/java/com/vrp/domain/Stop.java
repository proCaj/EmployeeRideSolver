package com.vrp.domain;

import com.vrp.entity.Employee;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A single stop within an Event's route. Each stop represents a location where
 * passengers board or alight.
 *
 * For a morning pickup event: the driver visits locations in order, picking up
 * passengers at each stop, then delivering them to their customer locations.
 *
 * Example (FR-3 cross-customer batch):
 *   Stop 0: Hub         → pickup 4 Chep employees + 1 Sanner employee (5 board)
 *   Stop 1: Chep site   → 4 Chep employees alight (4 leave)
 *   Stop 2: Sanner site → 1 Sanner employee alight (1 leaves)
 *
 * Example (FR-1 same-customer batch):
 *   Stop 0: Hub         → pickup 4 Chep employees (4 board)
 *   Stop 1: Chep site   → 4 Chep employees alight (4 leave)
 *
 * Example (morning pickup with remote pickup point):
 *   Stop 0: Tankstelle  → pickup 1 Chep employee (Person 1, 1 boards)
 *   Stop 1: Hub         → pickup 4 Chep employees (4 board)
 *   Stop 2: Chep site   → 5 Chep employees alight (5 leave)
 */
public class Stop {

    /** Location of this stop. */
    private Location location;

    /** Passengers boarding at this stop (empty for final dropoff-only stops). */
    private List<Employee> boardingPassengers;

    /** Passenger count alighting at this stop. */
    private int alightingCount;

    /** Customer name for passengers alighting here (for display/logging). */
    private String alightingCustomerName;

    /** Boarding time at this stop (2 min * boarding count). */
    private Duration boardingDuration;

    /** Travel time FROM previous stop TO this stop (set during generation). */
    private Duration travelTimeFromPrevious;

    /** Distance in meters FROM previous stop TO this stop. */
    private long distanceFromPrevious;

    /**
     * Latest allowed arrival time at this stop.
     * For pickup stops: the shift start time minus travel buffer.
     * For dropoff (customer) stops: the shift start time at that customer.
     * Used by time window constraints to validate multi-stop events.
     */
    private Instant maxEndTime;

    public Stop() {
        this.boardingPassengers = new ArrayList<>();
    }

    public Stop(Location location, List<Employee> boardingPassengers,
                int alightingCount, String alightingCustomerName) {
        this.location = location;
        this.boardingPassengers = boardingPassengers != null
            ? new ArrayList<>(boardingPassengers) : new ArrayList<>();
        this.alightingCount = alightingCount;
        this.alightingCustomerName = alightingCustomerName;
        // Boarding: 2 minutes per boarding passenger
        int boardingCount = this.boardingPassengers.size();
        this.boardingDuration = Duration.ofMinutes(2L * boardingCount);
    }

    /** Total passengers in vehicle after this stop (computed externally). */
    public int getNetPassengerChange() {
        return boardingPassengers.size() - alightingCount;
    }

    // --- Getters and Setters ---

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public List<Employee> getBoardingPassengers() {
        return boardingPassengers;
    }

    public void setBoardingPassengers(List<Employee> boardingPassengers) {
        this.boardingPassengers = boardingPassengers != null
            ? new ArrayList<>(boardingPassengers) : new ArrayList<>();
        this.boardingDuration = Duration.ofMinutes(2L * this.boardingPassengers.size());
    }

    public int getAlightingCount() {
        return alightingCount;
    }

    public void setAlightingCount(int alightingCount) {
        this.alightingCount = alightingCount;
    }

    public String getAlightingCustomerName() {
        return alightingCustomerName;
    }

    public void setAlightingCustomerName(String alightingCustomerName) {
        this.alightingCustomerName = alightingCustomerName;
    }

    public Duration getBoardingDuration() {
        return boardingDuration;
    }

    public void setBoardingDuration(Duration boardingDuration) {
        this.boardingDuration = boardingDuration;
    }

    public Duration getTravelTimeFromPrevious() {
        return travelTimeFromPrevious;
    }

    public void setTravelTimeFromPrevious(Duration travelTimeFromPrevious) {
        this.travelTimeFromPrevious = travelTimeFromPrevious;
    }

    public long getDistanceFromPrevious() {
        return distanceFromPrevious;
    }

    public void setDistanceFromPrevious(long distanceFromPrevious) {
        this.distanceFromPrevious = distanceFromPrevious;
    }

    public Instant getMaxEndTime() {
        return maxEndTime;
    }

    public void setMaxEndTime(Instant maxEndTime) {
        this.maxEndTime = maxEndTime;
    }

    @Override
    public String toString() {
        return "Stop{" + location.name()
            + ", board=" + boardingPassengers.size()
            + ", alight=" + alightingCount
            + (alightingCustomerName != null ? " → " + alightingCustomerName : "")
            + "}";
    }
}
