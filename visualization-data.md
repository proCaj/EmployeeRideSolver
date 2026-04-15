# EmployeeRideSolver - KW 51 Visualization Data

## 1. Location Coordinates (from DataBootstrap.java)

### Hub / Pickup Points

| ID | Name | Address | Latitude | Longitude |
|----|------|---------|----------|-----------|
| HUB | City-Fahrschule | Siegfriedstraße 25, 67547 Worms | 49.6295 | 8.3640 |
| TANK | Tankstelle Pfeddersheim | Odenwaldstraße 7, 67551 Worms | 49.6180 | 8.2970 |

### Customer Locations

| ID | Name | Address | Latitude | Longitude |
|----|------|---------|----------|-----------|
| CHEP | Chep Deutschland GmbH | Am Winkelgraben 13, 64584 Biebesheim | 49.7784 | 8.4625 |
| SANNER | Sanner GmbH | Bertha-Benz-Straße 5, 64625 Bensheim | 49.6800 | 8.6250 |
| ORION | Orion Bausysteme GmbH | Waldstr. 2, 64584 Biebesheim | 49.7750 | 8.4580 |
| BARBE | Hans W. Barbe Chemische Erzeugnisse GmbH | Justus-von-Liebig-Str. 17, 67549 Worms | 49.6350 | 8.3480 |
| BENEO | Beneo-Palatinit GmbH | Wormser Straße 11, 67283 Obrigheim | 49.4700 | 8.2100 |

### Employee Pickup Assignments

| Employee | Type | Pickup Location |
|----------|------|-----------------|
| Naruto Uzumaki | Site (Chep) | TANK (Pfeddersheim) |
| Sasuke Uchiha | Site (Chep) | HUB |
| Sakura Haruno | Site (Chep) | HUB |
| Hinata Hyuga | Site (Chep) | HUB |
| Shikamaru Nara | Site (Chep) | HUB |
| Kakashi Hatake | Site (Sanner) | HUB |
| Ino Yamanaka | Site (Orion) | HUB |
| Choji Akimichi | Site (Orion) | HUB |
| Rock Lee | Site (Barbe) | HUB |
| Neji Hyuga | Site (Barbe) | HUB |
| Gaara Sabaku | Site (Barbe) | HUB |
| Temari Sabaku | Site (Beneo) | HUB |

### Drivers (home = HUB)

| Name | Email | Phone |
|------|-------|-------|
| Thomas Koch | thomas@example.com | +49 151 1234 5607 |
| Sarah Meyer | sarah@example.com | +49 151 1234 5608 |
| Michael Wagner | michael@example.com | +49 151 1234 5609 |

---

## 2. Haversine Distance Matrix (meters)

| From\To | HUB | TANK | CHEP | SANNER | ORION | BARBE | BENEO |
|---------|-----|------|------|--------|-------|-------|-------|
| HUB | 0 | 4,993 | 18,009 | 19,610 | 17,534 | 1,305 | 20,928 |
| TANK | 4,993 | 0 | 21,443 | 24,600 | 20,949 | 4,131 | 17,613 |
| CHEP | 18,009 | 21,443 | 0 | 16,004 | 497 | 17,946 | 38,817 |
| SANNER | 19,610 | 24,600 | 16,004 | 0 | 15,990 | 20,557 | 37,956 |
| ORION | 17,534 | 20,949 | 497 | 15,990 | 0 | 17,462 | 38,332 |
| BARBE | 1,305 | 4,131 | 17,946 | 20,557 | 17,462 | 0 | 20,874 |
| BENEO | 20,928 | 17,613 | 38,817 | 37,956 | 38,332 | 20,874 | 0 |

### Key Distances Summary (Haversine / Road Estimate at 1.35x / Drive Time at 50km/h avg)

| Pair | Haversine | Road Est | Drive Time |
|------|-----------|----------|------------|
| HUB - BARBE | 1.3 km | 1.8 km | ~2 min |
| HUB - TANK | 5.0 km | 6.7 km | ~8 min |
| HUB - ORION | 17.5 km | 23.7 km | ~28 min |
| HUB - CHEP | 18.0 km | 24.3 km | ~29 min |
| HUB - SANNER | 19.6 km | 26.5 km | ~32 min |
| HUB - BENEO | 20.9 km | 28.2 km | ~34 min |
| CHEP - ORION | 0.5 km | 0.7 km | ~1 min |
| CHEP - SANNER | 16.0 km | 21.6 km | ~26 min |
| BARBE - TANK | 4.1 km | 5.6 km | ~7 min |
| BARBE - BENEO | 20.9 km | 28.2 km | ~34 min |

---

## 3. Manual Plan KW 51 - Driver Schedules (from manual-plan.md)

### Driver 1 (Thomas Koch) - Weekday Schedule

| Time | Action | From | To | Passengers | Notes |
|------|--------|------|----|------------|-------|
| 04:20 | Pickup -> Drop | Tankstelle Pfeddersheim | Chep | 1 (Naruto) | Frühschicht 05:30 |
| 04:30 | Pickup -> Drop | City-Fahrschule | Chep | 4 (Sasuke, Sakura, Hinata, Shikamaru) | Frühschicht 05:30 |
| 04:30 | Pickup -> Drop | City-Fahrschule | Sanner | 1 (Kakashi) | Frühschicht 06:00 |
| 05:30 | Pickup -> Drop | City-Fahrschule | Orion | 2 (Ino, Choji) | Tagschicht 06:30 |
| 06:00/06:30 | Pickup -> Drop | Barbe | City-Fahrschule | 2 (Rock Lee, Neji) | Night shift return, Tue-Sat mornings |
| 14:00 | Pickup -> Drop | Chep | City-Fahrschule | 5 (all Chep) | Fri: 13:00 |
| 14:00 | Pickup -> Drop | Sanner | City-Fahrschule | 1 (Kakashi) | |
| 16:00 | Pickup -> Drop | Orion | City-Fahrschule | 2 (Ino, Choji) | Fri: 12:45 combined with Chep |

### Driver 2 (Sarah Meyer) - Weekday Schedule

| Time | Action | From | To | Passengers | Notes |
|------|--------|------|----|------------|-------|
| 13:00 | Pickup -> Drop | City-Fahrschule | Barbe | 1 (Gaara) | Spätschicht 14:00 |
| 21:30 | Pickup -> Drop | City-Fahrschule | Barbe | 2 (Rock Lee, Neji) | Nachtschicht 22:00 |
| 22:00 | Pickup -> Drop | Barbe | City-Fahrschule | 1 | Return from late shift |
| 21:40/22:30 | Pickup -> Drop | Beneo | City-Fahrschule | 1 (Temari) | Night shift return; Wed+Thu / Fri+Sat |

### Weekend Schedule (Sat 20.12 + Sun 21.12.2025)

**Driver 1 (Early shift):**

| Time | Day | Action | From | To | Passengers | Notes |
|------|-----|--------|------|----|------------|-------|
| 05:40 | Sat+Sun | Pickup -> Drop | Beneo | City-Fahrschule | 1 (Temari) | Night shift return |
| 06:00 | Sat only | Pickup -> Drop | Barbe | City-Fahrschule | 2 (Rock Lee, Neji) | Night shift return |

**Driver 2 (Late shift):**

| Time | Day | Action | From | To | Passengers | Notes |
|------|-----|--------|------|----|------------|-------|
| 21:00 | Sat | Pickup -> Drop | City-Fahrschule | Beneo | 1 (Temari) | Nachtschicht 21:35 |
| 17:00 | Sun | Pickup -> Drop | City-Fahrschule | Beneo | 1 (Temari) | Sonntagsschicht 17:35 |

---

## 4. PlantUML Diagram Data

### 4a. Location Map Diagram

```plantuml
@startuml EmployeeRideSolver_KW51_Map
!theme plain
skinparam backgroundColor #FEFEFE
skinparam defaultFontName Helvetica

title EmployeeRideSolver - KW 51 Location Map & Schedules

package "Pickup Points" {
  rectangle "HUB\nCity-Fahrschule\nSiegfriedstraße 25\n67547 Worms\n(49.6295, 8.3640)" as HUB #LightBlue
  rectangle "TANK\nTankstelle Pfeddersheim\nOdenwaldstraße 7\n67551 Worms\n(49.6180, 8.2970)" as TANK #LightGray
}

package "Customers" {
  rectangle "CHEP\nChep Deutschland GmbH\n64584 Biebesheim\n(49.7784, 8.4625)" as CHEP #LightGreen
  rectangle "SANNER\nSanner GmbH\n64625 Bensheim\n(49.6800, 8.6250)" as SANNER #LightGreen
  rectangle "ORION\nOrion Bausysteme GmbH\n64584 Biebesheim\n(49.7750, 8.4580)" as ORION #LightGreen
  rectangle "BARBE\nHans W. Barbe\n67549 Worms\n(49.6350, 8.3480)" as BARBE #LightSalmon
  rectangle "BENEO\nBeneo-Palatinit GmbH\n67283 Obrigheim\n(49.4700, 8.2100)" as BENEO #LightSalmon
}

HUB   -- TANK    : **5.0 km**
HUB   -- CHEP    : **18.0 km**
HUB   -- SANNER  : **19.6 km**
HUB   -- ORION   : **17.5 km**
HUB   -- BARBE   : **1.3 km**
HUB   -- BENEO   : **20.9 km**
CHEP  -- ORION   : **0.5 km**
CHEP  -- SANNER  : **16.0 km**
BARBE -- BENEO   : **20.9 km**

@enduml
```

### 4b. Driver 1 Weekday Sequence

```plantuml
@startuml Driver1_KW51_Weekday
title Driver 1 (Thomas Koch) - KW 51 Weekday

actor "Thomas" as D
participant "Tankstelle" as T
participant "City-Fahrschule" as H
participant "Chep" as C
participant "Sanner" as S
participant "Orion" as O
participant "Barbe" as B

== Early Morning Run ==
D -> T : 04:20\nPickup 1 person (Naruto)\nDrive ~21 km (~25 min)
T -> C : Drop at Chep\nFrühschicht 05:30
D -> H : 04:30\nPickup 4 persons (Sasuke,Sakura,Hinata,Shikamaru)\nDrive ~18 km (~22 min)
H -> C : Drop at Chep\nFrühschicht 05:30
D -> H : 04:30\nPickup 1 person (Kakashi)\nDrive ~20 km (~24 min)
H -> S : Drop at Sanner\nFrühschicht 06:00
D -> H : 05:30\nPickup 2 persons (Ino, Choji)\nDrive ~18 km (~21 min)
H -> O : Drop at Orion\nTagschicht 06:30

== Morning Return (Tue-Sat) ==
D -> B : 06:00/06:30\nPickup 2 persons (Rock Lee, Neji)\nNight shift return
B -> H : Drive ~1.3 km (~2 min)

== Afternoon Return Run ==
D -> C : 14:00 (Fri: 13:00)\nReturn 5 persons from Chep\nDrive ~18 km (~22 min)
C -> H : Drop at City-Fahrschule
D -> S : 14:00\nReturn 1 person (Kakashi) from Sanner\nDrive ~20 km (~24 min)
S -> H : Drop at City-Fahrschule
D -> O : 16:00 (Fri: 12:45)\nReturn 2 persons from Orion\nDrive ~18 km (~21 min)
O -> H : Drop at City-Fahrschule

@enduml
```

### 4c. Driver 2 Weekday Sequence

```plantuml
@startuml Driver2_KW51_Weekday
title Driver 2 (Sarah Meyer) - KW 51 Weekday

actor "Sarah" as D
participant "City-Fahrschule" as H
participant "Barbe" as B
participant "Beneo" as BN

== Afternoon Drop ==
D -> H : 13:00\nPickup 1 person (Gaara)\nDrive ~1.3 km (~2 min)
H -> B : Drop at Barbe\nSpätschicht 14:00

== Evening Run ==
D -> H : 21:30\nPickup 2 persons (Rock Lee, Neji)\nDrive ~1.3 km (~2 min)
H -> B : Drop at Barbe\nNachtschicht 22:00

D -> B : 22:00\nReturn 1 person from Barbe\nDrive ~1.3 km (~2 min)
B -> H : Drop at City-Fahrschule

D -> BN : 21:40/22:30\nReturn 1 person (Temari) from Beneo\nDrive ~21 km (~25 min)
BN -> H : Drop at City-Fahrschule\n(Wed+Thu / Fri+Sat)

@enduml
```

### 4d. Weekend Schedule Sequence

```plantuml
@startuml Weekend_KW51
title Weekend KW 51 (20.12 + 21.12.2025)

actor "Driver1\n(Early)" as D1
actor "Driver2\n(Late)" as D2
participant "City-Fahrschule" as H
participant "Barbe" as B
participant "Beneo" as BN

== Saturday 20.12.2025 ==
D1 -> BN : 05:40\nReturn 1 person (Temari)\nDrive ~21 km (~25 min)
BN -> H : Drop at City-Fahrschule

D1 -> B : 06:00\nReturn 2 persons (Rock Lee, Neji)\nDrive ~1.3 km (~2 min)
B -> H : Drop at City-Fahrschule

D2 -> H : 21:00\nPickup 1 person (Temari)\nDrive ~21 km (~25 min)
H -> BN : Drop at Beneo\nNachtschicht 21:35

== Sunday 21.12.2025 ==
D1 -> BN : 05:40\nReturn 1 person\nDrive ~21 km (~25 min)
BN -> H : Drop at City-Fahrschule

D2 -> H : 17:00\nPickup 1 person (Temari)\nDrive ~21 km (~25 min)
H -> BN : Drop at Beneo\nSonntagsschicht 17:35

@enduml
```

### 4e. Full Route Network Activity Diagram

```plantuml
@startuml KW51_Route_Network
!theme plain
title KW 51 Route Network - All Location Connections

rectangle "City-Fahrschule\n(HUB)\n49.6295, 8.3640\nWorms" as HUB #LightBlue
rectangle "Tankstelle\nPfeddersheim\n(TANK)\n49.6180, 8.2970\nWorms" as TANK #Gray
rectangle "Chep\n(CHEP)\n49.7784, 8.4625\nBiebesheim" as CHEP #LightGreen
rectangle "Sanner\n(SANNER)\n49.6800, 8.6250\nBensheim" as SANNER #LightGreen
rectangle "Orion\n(ORION)\n49.7750, 8.4580\nBiebesheim" as ORION #LightGreen
rectangle "Barbe\n(BARBE)\n49.6350, 8.3480\nWorms" as BARBE #LightSalmon
rectangle "Beneo\n(BENEO)\n49.4700, 8.2100\nObrigheim" as BENEO #LightSalmon

HUB   --> TANK    : 5.0 km
HUB   --> CHEP    : 18.0 km
HUB   --> SANNER  : 19.6 km
HUB   --> ORION   : 17.5 km
HUB   --> BARBE   : 1.3 km
HUB   --> BENEO   : 20.9 km
TANK  --> CHEP    : 21.4 km
TANK  --> BARBE   : 4.1 km
CHEP  --> ORION   : 0.5 km
CHEP  --> SANNER  : 16.0 km
BARBE --> BENEO   : 20.9 km

note right of HUB
  **Driver 1 Morning Route:**
  TANK->CHEP->HUB->SANNER->HUB->ORION
  **Driver 1 Evening Route:**
  CHEP->HUB->SANNER->HUB->ORION->HUB
end note

note right of BENEO
  **Driver 2 Route:**
  HUB->BARBE->HUB->BARBE->HUB
  BENEO->HUB (evening)
end note

@enduml
```