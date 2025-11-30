Problem description:
Route Structure Flexibility
Routes don't need to follow strict sequential phases (pickup → dropoff → pickup → return)
It's acceptable to have interleaved pickups and dropoffs if it optimizes the route
Example: Home → Pickup1 → Pickup2 → Customer1 → Pickup3 → Customer2 → etc.
Driver Working Patterns
A single route: Home → various pickups/dropoffs → Home
After completing a route, driver takes a break (30 min minimum, 4 hours maximum)
Drivers can then start another route
Ideal pattern would be 4 hours driving → 30 min break → 4 hours driving
However, customer demands rarely allow this perfect pattern
Shifts and Weekend Handling
Customers can have up to 3 shifts per day
Weekends may have different patterns (e.g., dropoffs but no pickups)
We can model all transportation needs as pickup and dropoff events, regardless of shift start/end
Planning Flexibility
Weekly planning is standard, but need ability to update mid-week
New customer demand might require adding employees and adjusting transportation
Optimization Priorities
Minimize total distance driven (highest priority - gas and car wear)
Maximize consecutive working hours for drivers (soft constraint)
Minimize employee travel time (lower priority)