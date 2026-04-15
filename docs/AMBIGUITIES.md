# Manual Plan KW 51 - Ambiguity Analysis

## Identified Ambiguities

### A1: 04:20 Tankstelle + 04:30 Hub -- Same Driver or Multi-Stop?

**The problem:** The manual plan says "Fahrplan von einem Fahrer" (one driver's schedule) and lists:
- 04:20 Chep hin, 1 Person, Tankstelle Pfeddersheim
- 04:30 Chep hin, 4 Personen, City-Fahrschule
- 04:30 Sanner hin, 1 Person, City-Fahrschule

These overlap in time. Tankstelle->Hub is 5km (~8 min). If one driver does both:
- Scenario A (Multi-Stop FR-3): Driver leaves Hub ~04:12, drives to Tankstelle (8 min), picks up 1 person at 04:20, drives back via Hub (8 min), arrives Hub ~04:28, picks up 5 more people (4 Chep + 1 Sanner), departs ~04:30. Total passengers: 6 (at capacity). Route: TANK->HUB->CHEP->SANNER. **SELECTED SCENARIO** -- this is what FR-3 merging is designed to produce.
- Scenario B (Separate Trips): Two drivers needed in the early morning. Driver 1 does Tankstelle->Chep at 04:20, Driver 2 does Hub->Chep+Sanner at 04:30.
- Scenario C (Sequential): Driver does Tankstelle->Chep (arrive ~04:47), then returns to Hub and does the 05:30 Orion run. The 04:30 Hub pickup is done by a different driver.

### A2: 06:00/06:30 Barbe Return -- Which Days at Which Time?

"06:00/06:30 Uhr Barbe holen 2 Personen ab Dienstag bis Samstag (nach der Orion Fahrt)"

**Interpretation:** Pickup at Barbe (night shift return), after completing the Orion dropoff. The "/06:30" likely means some days at 06:00, others at 06:30. Since Orion dropoff is at 06:30 for the 06:30 shift, the Barbe pickup is likely ~06:45-07:00 after completing Orion. **Selected: 06:45 as compromise** -- after Orion dropoff, 1.3km drive to Barbe (~2 min).

### A3: Driver 2 Beneo Return Time

"21:40/22:30 Uhr Beneo holen (nach Barbe)" with "Mittwoche und Donnerstag 1 Person" and "Freitag und Samstag 1 Person"

**Interpretation:** Different times on different days. Wednesday/Thursday at ~21:40, Friday/Saturday at ~22:30 (after completing Barbe dropoff at 22:00). **Selected: 22:00 as single time** for simplicity in the weekday visualization (the later time after Barbe duty).

### A4: "22:00 Uhr holen 1 Personen" -- Which Location?

Listed after "21:30 Uhr Barbe hin 2 Personen". Context: after dropping 2 people at Barbe for night shift, pick up 1 person FROM Barbe (late shift return). **Selected: Barbe return, 1 person.**

### A5: Michael Wagner (3rd Driver) -- No Fixed Schedule

"Michael muss dann noch mitfahren" only when scheduling conflicts require 2 early + 2 late drivers. **Selected: Michael is reserve only, not scheduled in base scenario.**

### A6: Friday Differences

Chep returns at 13:00 (not 14:00), Orion at 12:45 (not 16:00). The Friday schedule is compressed. **Selected: Monday-Thursday as base scenario.** Friday noted separately.

### A7: Beneo Bus vs. Driver

Plan lists Bus 451 times for Beneo weekdays but says "Wochenende müssen wir in der Regel immer fahren." **Selected: Weekday Beneo uses bus 451 (driver not needed), weekend driver required.** However, the driver 2 schedule shows Beneo returns on weekdays -- meaning the bus handles the outbound trip but the driver handles the return trip. **Selected interpretation: Bus handles inbound (Worms->Beneo), driver handles return (Beneo->Worms) on weekdays; driver handles both on weekends.**

---

## SELECTED SCENARIO: Monday-Thursday (Base Weekday)

**Driver 1 (Thomas Koch):**
1. 04:15 Leave Hub -> 04:20 TANK pickup (1 pax: Naruto) -> 04:28 HUB pickup (5 pax: Sasuke,Sakura,Hinata,Shikamaru for Chep + Kakashi for Sanner) -> 05:00 CHEP drop (5 pax) -> 05:30 SANNER drop (1 pax) -> 06:00 HUB
2. 05:30 HUB pickup (2 pax: Ino,Choji for Orion) -> 06:00 ORION drop (2 pax) -> 06:30 HUB
3. 06:45 BARBE pickup (2 pax: Rock Lee,Neji night return) -> 06:50 HUB drop (2 pax)
4. 14:00 CHEP pickup (5 pax return) -> 14:30 HUB drop (5 pax)
5. 14:00 SANNER pickup (1 pax return) -> 14:30 HUB drop (1 pax)  [parallel with #4 - separate car or sequential]
6. 16:00 ORION pickup (2 pax return) -> 16:30 HUB drop (2 pax)

**Driver 2 (Sarah Meyer):**
1. 13:00 HUB pickup (1 pax: Gaara for Barbe) -> 13:05 BARBE drop (1 pax)
2. 21:30 HUB pickup (2 pax: Rock Lee,Neji for Barbe night) -> 21:35 BARBE drop (2 pax)
3. 22:00 BARBE pickup (1 pax return) -> 22:05 HUB drop (1 pax)
4. 22:30 BENEO pickup (1 pax: Temari return) -> 23:00 HUB drop (1 pax)

**Driver 3 (Michael Wagner):** Not scheduled (reserve only)
