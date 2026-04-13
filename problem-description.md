# Problem Description: Employee Transportation VRP

## Route Structure Flexibility
Routes don't need to follow strict sequential phases (pickup -> dropoff -> pickup -> return).
Interleaved pickups and dropoffs are allowed when they optimize the route.
Example: Home -> Pickup1 -> Pickup2 -> Customer1 -> Pickup3 -> Customer2

Multi-stop routes (FR-3) enable a single pickup event to serve multiple customers:
Example: Hub -> Chep site (4 alight) -> Sanner site (1 alight)

## Driver Working Patterns
A single route: Home -> various pickups/dropoffs -> Home
After completing a route, driver takes a break (30 min minimum, 4 hours maximum)
Drivers can then start another route
Ideal pattern: 4 hours driving -> 30 min break -> 4 hours driving
Customer demands rarely allow this perfect pattern

## ArbZG Working Hours Compliance
Maximum 10 hours per work day, grouped by shiftDate
  - Night shifts spanning midnight count toward the day the shift started
Maximum 40 hours per week
Maximum 4 hours consecutive driving without a 30-minute break
  - Breaks between events >= 30 minutes reset the consecutive counter
  - Only events on the same shiftDate count as consecutive

## Shifts and Weekend Handling
Customers can have up to 3 shifts per day
Weekends may have different patterns (e.g., dropoffs but no pickups)
All transportation needs are modeled as pickup and dropoff events
Weekly planning is standard, with mid-week update capability

## Event Generation
FR-1: Employees going to the same customer/shift/day/pickup-location are batched into single events
FR-3: Pickup events at the same location within a 30-minute window are merged into multi-stop events
Different pickup locations produce separate events (e.g., Tankstelle vs City-Fahrschule)

## Optimization Priorities
1. Minimize total distance driven (highest priority - fuel and vehicle wear)
2. Respect all hard constraints (capacity, time windows, pairing, ArbZG working hours)
3. Minimize driver idle/waiting time (soft constraint)
4. Minimize return-to-home distance (soft constraint)
