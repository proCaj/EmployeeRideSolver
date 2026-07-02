# Requirements Document: VRP Solver Optimization Issues

## 1. Problem Statement

The VRP solver produces routes for employee transportation using a 2-pass pipeline that batches employees and optimizes route assignments. The system groups employees traveling to the same destination at the same time into shared events, then uses Timefold Solver to assign events to drivers with minimal total distance.

### Current Behavior

- **2-pass pipeline**: Pass 1 groups employees by (customer, shift, day, pickup location); Pass 2 merges events across customers when pickup times fall within the configurable time window
- **Batched events**: Employees sharing the same customer/shift/day/pickup are combined into single events with multiple passengers
- **Cross-customer merging**: Events for different customers at the same pickup location within the time window are merged (e.g., Chep + Sanner at City-Fahrschule at 04:30)
- **Solver-assigned routes**: Timefold Solver assigns batched events to drivers, optimizing for total distance while respecting hard constraints (capacity, pairing, time windows)

### Expected Behavior (Manual Plan KW 51)

- **Batched trips**: Employees going to same customer/shift/day grouped into single events
- **Combined pickups**: 04:30 pickup combines Chep (4 people) + Sanner (1 person) in one trip
- **Efficient routes**: 2 drivers covering all transportation needs
- **Sequential route optimization**: Driver does Chep → Sanner → Orion → Barbe in logical sequence

---

## 2. Reference: Manual Route Plan (KW 51)

### Driver 1 Daily Pattern (Early Shift Coverage)
| Time  | Action | Customer | Passengers | Pickup Location |
|-------|--------|----------|------------|-----------------|
| 04:20 | Pickup | Chep | 1 | Tankstelle Pfeddersheim |
| 04:30 | Pickup | Chep + Sanner | 4 + 1 = 5 | City-Fahrschule |
| 05:30 | Pickup | Orion | 2 | City-Fahrschule |
| 06:00 | Pickup | Barbe (night shift end) | 2 | Barbe location |
| 14:00 | Dropoff | Chep | 5 | City-Fahrschule |
| 14:00 | Dropoff | Sanner | 1 | City-Fahrschule |
| 16:00 | Dropoff | Orion | 2 | City-Fahrschule |

### Driver 2 Daily Pattern (Late/Night Shift Coverage)
| Time  | Action | Customer | Passengers | Pickup Location |
|-------|--------|----------|------------|-----------------|
| 13:00 | Pickup | Barbe (late shift) | 1 | City-Fahrschule |
| 21:30 | Pickup | Barbe (night shift) | 2 | City-Fahrschule |
| 22:00 | Dropoff | Barbe (late shift end) | 1 | City-Fahrschule |
| 22:30 | Dropoff | Beneo (night shift end) | 1 | City-Fahrschule |

### Weekend Pattern (Sat 20.12 + Sun 21.12)
| Time  | Day | Action | Customer | Passengers |
|-------|-----|--------|----------|------------|
| 05:40 | Sat+Sun | Dropoff | Beneo (night end) | 1 |
| 06:00 | Sat only | Dropoff | Barbe (night end) | 2 |
| 17:00 | Sun | Pickup | Beneo (12h shift) | 1 |
| 21:00 | Sat | Pickup | Beneo (night shift) | 1 |

---

## 3. Functional Requirements

### 3.1 Event Batching (CRITICAL)

**FR-1**: The system MUST batch employees into single events when they share:
- Same customer destination
- Same shift (start time)
- Same day
- Same pickup location (or within grouping threshold)

**FR-2**: Batched events MUST have:
- `passengers` list containing all grouped employees
- `passengerCount` reflecting total passengers
- Adjusted boarding time (2 min per passenger)

**FR-3**: The system MUST combine pickups for different customers when:
- Pickup times are within a configurable time window (`FR3_TIME_WINDOW` constant, default 30 minutes)
- Pickup locations are the same
- Example: 04:30 Chep (4) + Sanner (1) combined at City-Fahrschule

### 3.2 Pickup Location Grouping

**FR-4**: Employees with different pickup locations MUST be in separate events
- Person 1 (Tankstelle Pfeddersheim) → separate event at 04:20
- Person 2, Person 3, Person 4, Person 5 (City-Fahrschule) → batched event at 04:30

**FR-5**: The hub location (City-Fahrschule) coordinates:
- Latitude: 49.6295
- Longitude: 8.3640

**FR-6**: Alternative pickup (Tankstelle Pfeddersheim) coordinates:
- Latitude: 49.6180
- Longitude: 8.2970

### 3.3 Route Optimization

**FR-7**: Routes SHOULD minimize total distance driven (primary optimization goal)

**FR-8**: Routes SHOULD allow interleaved pickups/dropoffs when it reduces distance
- Example: Home → Pickup1 → Pickup2 → Customer1 → Pickup3 → Customer2

**FR-9**: Paired pickup/dropoff events MUST use the same driver

**FR-10**: Pickup events MUST occur before their paired dropoff events

### 3.4 ArbZG Working Hours Constraints

**FR-11**: Max 10h daily per driver — driving time is grouped by `shiftDate`. Night shifts that span midnight count toward the shift start day (i.e., a shift starting 22:00 on Monday and ending 06:00 on Tuesday is attributed to Monday).

**FR-12**: Max 40h weekly per driver — total driving time across all days in a calendar week (Monday–Sunday) must not exceed 40 hours.

**FR-13**: Max 4h consecutive driving without 30min break — if a driver's continuous driving block exceeds 4 hours, a 30-minute break must be inserted before additional driving can be assigned.

---

## 4. Constraints

### 4.1 Driver Working Hours

| Constraint | Value |
|------------|-------|
| Maximum daily hours | 10 hours |
| Maximum weekly hours | 40 hours |
| Minimum break between routes | 30 minutes |
| Maximum break between routes | 4 hours |

### 4.2 Vehicle Capacity

| Constraint | Value |
|------------|-------|
| Maximum passengers per vehicle | 6 |
| Available vehicles | 2 |
| Available drivers | 3 (rotating) |

### 4.3 Time Windows

| Constraint | Description |
|------------|-------------|
| Early arrival buffer (min) | 30 minutes before shift start |
| Early arrival buffer (max) | 45 minutes before shift start |
| Late pickup buffer | 1 hour after shift end |

### 4.4 Shift Patterns

| Customer | Shifts | Days |
|----------|--------|------|
| Chep | Early 05:30-14:00 (Fri -13:00), Late 14:00-22:00 | Mon-Fri |
| Sanner | Early 06:00-14:00, Late 14:00-22:00, Night 22:00-06:00 | Mon-Sat |
| Orion | Day 06:30-16:00 (Fri -12:45) | Mon-Fri |
| Barbe | Early 06:00-14:00, Late 14:00-22:00, Night 22:00-06:00 | Mon-Sat |
| Beneo | Early 05:35-13:35, Late 13:35-21:35, Night 21:35-05:35 | Mon-Sun |

---

## 5. Success Criteria

### 5.1 Quantitative Metrics

| Metric | Baseline (Unbatched) | Target |
|--------|----------------------|--------|
| Total weekly distance | ~1,735 km | < 800 km |
| Number of drivers needed | 3 | 2 (with 3rd as backup) |
| Events per week | ~170 (unbatched) | ~50 (batched) |
| Trips per Chep early shift | 5 individual | 1-2 batched |

### 5.2 Qualitative Criteria

- Routes should match logical patterns similar to manual plan
- No employee should be transported alone if others share same destination/time
- Driver schedules should be balanced (not one overloaded, others idle)
- Weekend coverage should be handled with minimal driver count

---

## 6. Technical Implementation Notes

### 6.1 Code Location

The batching logic is implemented in:
```
src/main/java/com/vrp/service/EventGenerationService.java
```

### 6.2 Implementation Details

**FR-1 (Event Batching)**: Employees are grouped by the composite key (customer, shift, day, pickup location). Each group produces a single pickup Event and a single dropoff Event with all grouped employees in the `passengers` list. Travel time is adjusted with 2 minutes boarding per passenger.

**FR-3 (Cross-Customer Merging)**: After initial grouping, a second pass merges events for different customers when their pickup times fall within the `FR3_TIME_WINDOW` constant (default 30 minutes) and they share the same pickup location. Merged events combine their passenger lists and take the earlier pickup time. This enables the Chep + Sanner combination at City-Fahrschule.

### 6.3 Two-Pass Batching Algorithm

```
# Pass 1: Group employees by shared trip attributes
for each shiftDemand:
    group employees by (customer, shift, day, pickup_location)
    for each group:
        create pickup Event with all employees as passengers
        create dropoff Event with all employees as passengers
        link pickup and dropoff as paired events

# Pass 2: Merge events across customers within time window
sort all pickup events by pickup_location, then by pickup_time
for each pickup_location:
    events_at_location = events filtered by location, sorted by time
    for each event in events_at_location:
        find subsequent events within FR3_TIME_WINDOW at same location
        if found and combined passengerCount <= vehicle capacity:
            merge events: combine passengers, keep earlier time
            update paired dropoff events accordingly
```

### 6.4 Edge Cases

- Single employee at unique pickup location → still create event (no batching possible)
- Mixed pickup locations for same shift → separate batched events per location
- Night shifts spanning midnight → attributed to `shiftDate` (shift start day) for daily hour tracking (FR-11, FR-12); a shift starting 22:00 Monday and ending 06:00 Tuesday counts toward Monday's 10h daily limit
- Merged events exceeding vehicle capacity → do not merge; keep as separate events assigned to different drivers

---

## 7. Test Data (KW 51)

### Employees by Customer

| Customer | Employees | Pickup Location |
|----------|-----------|-----------------|
| Chep | Person 1 | Tankstelle Pfeddersheim |
| Chep | Person 2, Person 3, Person 4, Person 5 | City-Fahrschule |
| Sanner | Person 6 | City-Fahrschule |
| Orion | Person 7, Person 8 | City-Fahrschule |
| Barbe | Person 9, Person 10, Person 11 | City-Fahrschule |
| Beneo | Person 12 | City-Fahrschule |

### Expected Batched Events (Monday Example)

| Event | Type | Customer | Passengers | Location |
|-------|------|----------|------------|----------|
| 1 | Pickup | Chep | Person 1 (1) | Pfeddersheim |
| 2 | Pickup | Chep + Sanner | Person 2, Person 3, Person 4, Person 5, Person 6 (5) | City-Fahrschule |
| 3 | Pickup | Orion | Person 7, Person 8 (2) | City-Fahrschule |
| 4 | Pickup | Barbe Night End | Person 9, Person 10 (2) | Barbe |
| 5 | Pickup | Barbe Late | Person 11 (1) | City-Fahrschule |
| 6 | Dropoff | Chep | Person 1 (1) | Pfeddersheim |
| 7 | Dropoff | Chep + Sanner | Person 2, Person 3, Person 4, Person 5, Person 6 (5) | City-Fahrschule |
| ... | ... | ... | ... | ... |

---

## 8. Appendix: Customer Locations

| Customer | Address | Latitude | Longitude |
|----------|---------|----------|-----------|
| Chep Deutschland GmbH | Am Winkelgraben 13, 64584 Biebesheim | 49.7784 | 8.4625 |
| Sanner GmbH | Bertha-Benz-Straße 5, 64625 Bensheim | 49.6800 | 8.6250 |
| Orion Bausysteme GmbH | Waldstr. 2, 64584 Biebesheim | 49.7750 | 8.4580 |
| Hans W. Barbe | Justus-von-Liebig-Str. 17, 67549 Worms | 49.6350 | 8.3480 |
| Beneo-Palatinit GmbH | Wormser Straße 11, 67283 Obrigheim | 49.4700 | 8.2100 |

| Pickup Location | Address | Latitude | Longitude |
|-----------------|---------|----------|-----------|
| City-Fahrschule (Hub) | Siegfriedstraße 25, 67547 Worms | 49.6295 | 8.3640 |
| Tankstelle Pfeddersheim | Odenwaldstraße 7, 67551 Worms | 49.6180 | 8.2970 |
