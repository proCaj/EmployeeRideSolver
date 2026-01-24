# Requirements Document: VRP Solver Optimization Issues

## 1. Problem Statement

The current VRP solver produces inefficient routes that do not match the quality of manually planned routes. The primary issue is that **employee batching is not implemented**, resulting in individual trips for each employee instead of grouped transportation.

### Current Behavior (Broken)

- **No batching**: Each employee receives individual pickup/dropoff events
- **Excessive events**: 5 Chep employees × 5 days × 2 trips = 50 events (just for one customer)
- **Inefficient driver utilization**: 3 drivers producing 1,735 km total weekly distance
- **Split assignments**: Employees going to the same location at the same time are transported separately

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

**FR-3**: The system SHOULD further optimize by combining pickups for different customers when:
- Pickup times are within a configurable window (e.g., 15 minutes)
- Pickup locations are the same
- Example: 04:30 Chep (4) + Sanner (1) combined at City-Fahrschule

### 3.2 Pickup Location Grouping

**FR-4**: Employees with different pickup locations MUST be in separate events
- Naruto (Tankstelle Pfeddersheim) → separate event at 04:20
- Sasuke, Sakura, Hinata, Shikamaru (City-Fahrschule) → batched event at 04:30

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

| Metric | Current (Broken) | Target |
|--------|------------------|--------|
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

The batching logic should be implemented in:
```
src/main/java/com/vrp/service/EventGenerationService.java
```

Current code (line 57-58) explicitly disables batching:
```java
// Generate individual events per employee (no batching)
for (Employee employee : shift.assignedEmployees) {
```

### 6.2 Required Changes

1. **Group employees** by (customer, shift, day, pickup location)
2. **Create single Event** with multiple passengers for each group
3. **Adjust travel time** calculation for boarding multiple passengers
4. **Update event ID** scheme to reflect grouped nature

### 6.3 Batching Algorithm Pseudocode

```
for each shift in shiftDemands:
    group employees by pickup_location
    for each (location, employees) group:
        create ONE pickup event with all employees as passengers
        create ONE dropoff event with all employees as passengers
        link pickup and dropoff as paired events
```

### 6.4 Edge Cases

- Single employee at unique pickup location → still create event (no batching possible)
- Mixed pickup locations for same shift → separate batched events per location
- Night shifts spanning midnight → handle date boundary correctly

---

## 7. Test Data (KW 51)

### Employees by Customer

| Customer | Employees | Pickup Location |
|----------|-----------|-----------------|
| Chep | Naruto Uzumaki | Tankstelle Pfeddersheim |
| Chep | Sasuke Uchiha, Sakura Haruno, Hinata Hyuga, Shikamaru Nara | City-Fahrschule |
| Sanner | Kakashi Hatake | City-Fahrschule |
| Orion | Ino Yamanaka, Choji Akimichi | City-Fahrschule |
| Barbe | Rock Lee, Neji Hyuga, Gaara Sabaku | City-Fahrschule |
| Beneo | Temari Sabaku | City-Fahrschule |

### Expected Batched Events (Monday Example)

| Event | Type | Customer | Passengers | Location |
|-------|------|----------|------------|----------|
| 1 | Pickup | Chep | Naruto (1) | Pfeddersheim |
| 2 | Pickup | Chep | Sasuke, Sakura, Hinata, Shikamaru (4) | City-Fahrschule |
| 3 | Pickup | Sanner | Kakashi (1) | City-Fahrschule |
| 4 | Pickup | Orion | Ino, Choji (2) | City-Fahrschule |
| 5 | Pickup | Barbe Night End | Rock Lee, Neji (2) | Barbe |
| 6 | Pickup | Barbe Late | Gaara (1) | City-Fahrschule |
| 7 | Dropoff | Chep | Naruto (1) | Pfeddersheim |
| 8 | Dropoff | Chep | Sasuke, Sakura, Hinata, Shikamaru (4) | City-Fahrschule |
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
