# Parking Lot — JPMC Round 3 (LLD + HLD)

> **JPMC context:** Round 3, in-office. **AI plate-capture service is given upfront**
> — your job is to design the entry, spot-assignment, payment, and notification
> system around it. HackerRank drawing tool available for the HLD diagram.
> Most frequently reported JPMC Round 3 problem across 4+ SuperDay threads (2024–2026).

---

## Index

| § | Section |
|---|---------|
| §1 | 🎯 Problem Statement |
| §2 | ❓ Clarifying Questions |
| §3a | 🏗️ LLD — Construction Guide (7 Moves) |
| §3b | 🏗️ LLD — Complete Class Diagram |
| §4 | 🧭 Design Decisions |
| §5 | 🔌 Key Interfaces |
| §6 | ⚙️ Code — Three Methods |
| §7 | 🔁 Concurrency |
| §8 | 🧨 Java Depth Probes |
| §9 | 🌐 HLD — 3-Phase Construction Guide |
| §10 | 🏛️ HLD Decisions |
| §11 | 📡 API Design |
| §12 | 🛤️ Happy + Unhappy Paths |
| §13 | ⚠️ Fault Tolerance |
| §14 | 📐 Q&A — Tier-2 JPMC Probes |
| §15 | 🧾 TL;DR |
| §16 | 🔄 Changelog |

---

## §1 — 🎯 Problem Statement

Design a city-scale parking lot management system that:

- Manages **multiple lots** across a city, each with multiple floors and typed spots
- **Captures vehicle plates at entry** via an AI service *(pre-built — integrate it, don't design it)*
- **Assigns best-fit spots** by vehicle type (TWO_WHEELER prefers COMPACT; TRUCK requires LARGE)
- **Issues a ticket** on entry; calculates fee on exit via a pluggable strategy
- **Processes payment** on exit; emits a notification (email/SMS receipt)
- **Scales to city-wide deployment** — hundreds of lots, thousands of concurrent vehicles

---

## §2 — ❓ Clarifying Questions

**Scope / MVP**

1. Are we designing for one lot or a city-wide network? *(drives whether we need geospatial search)*
2. What vehicle types are supported — two-wheelers, cars, trucks? Any EVs needing charging spots?
3. Do users book spots in advance (reservation), or is it always walk-in?
   *(reservation adds a pre-hold state to the ticket)*

**Actors / Users**

4. Who are the actors — vehicle owners, lot attendants, lot admins, finance team?
   *(scopes which APIs and roles are needed)*

**Scale**

5. How many lots and total spots city-wide? What is peak entry rate per lot per hour?
6. How many users are actively searching for nearby parking at peak?

**Consistency / Correctness**

7. If two cars arrive simultaneously for the last available spot — who gets it?
   Is any level of double-booking tolerable? *(determines the locking model)*

**External Dependencies**

8. The AI plate-capture service is given — does it run synchronously (blocking the barrier)
   or can it run ahead of the vehicle reaching the barrier?
9. Which payment gateway? Is there a timeout SLA on payment before the barrier opens anyway?

**Edge Cases**

10. Can the same vehicle re-enter before the previous ticket is closed (plate re-entry)?
11. What happens if payment fails after the barrier has already lifted?

**Non-Functional**

12. What is the acceptable latency for "find parking near me" — real-time (<1s)?
13. Can users see a slightly stale available-spot count, or must it be exact?

---

## §3a — 🏗️ LLD — Construction Guide (7 Moves)

> Rebuild this class diagram on a whiteboard in ~10 minutes.
> Each move adds a layer; each board snapshot shows the state after that move.
> Stop at move 7 — ≈75% of the diagram is visible; add detail only if asked.

---

### Move 1 — List Every Domain Noun (don't classify yet)

Before the board, say: *"Let me separate the nouns the problem gives me directly from the ones that constraints force me to invent."*

**From the statement directly:** Vehicle, ParkingSpot, ParkingFloor, ParkingLot, Payment, PlateCapture (given — integrate it, don't design it)

**Derived from constraints:**
- *"entry and exit must be tracked with timing to compute the fee"* → **Ticket** entity (session record linking a vehicle to a spot with an entry timestamp and an exit timestamp — without this there is no time range to bill against)
- *"lot operators use different fee structures — hourly, flat rate, event pricing"* → **FeeStrategy** interface (the fee algorithm is the one thing that changes independently; swap `HourlyFeeStrategy` for `EventFeeStrategy` without modifying `ParkingLotService`)
- *"a two-wheeler can't park in a truck bay and a truck can't fit in a compact spot"* → **VehicleType** and **SpotType** enums + a compatibility rule (enforced by the service, not by the enum itself)

*Filter rule:* Does it carry data or behavior that belongs in the model?
`Notification` → service behavior, not a data entity. Keep the service, drop the noun.
`Fee` → a field on `Ticket`, not a standalone entity.

**Your board at the end of Move 1:**

```
From statement:  Vehicle · ParkingSpot · ParkingFloor · ParkingLot · Payment
                 PlateCapture (given — integrate, don't design)
Derived:         Ticket (session record: vehicle + spot + entry/exit time + fee),
                 FeeStrategy (interface — pluggable fee algorithm),
                 VehicleType / SpotType enums (compatibility rule)
```

---

### Move 2 — Classify: Enums → Entities → Interfaces → Services

```
Board after Move 2:

  ENUMS:      VehicleType   SpotType   TicketStatus
  ENTITIES:   Vehicle   ParkingSpot   ParkingFloor   ParkingLot   Ticket
  INTERFACES: FeeStrategy
  SERVICES:   ParkingLotService   PlateCaptureService (given)
```

Draw enums first — they have no fields to get wrong, and every entity references them.

---

### Move 3 — Draw the Three Enums

```
Board after Move 3:

  ┌──────────────────┐  ┌─────────────────┐  ┌──────────────────────┐
  │  VehicleType     │  │  SpotType       │  │  TicketStatus        │
  │  ─────────────── │  │  ────────────── │  │  ─────────────────── │
  │  TWO_WHEELER     │  │  COMPACT        │  │  ACTIVE              │
  │  CAR             │  │  MEDIUM         │  │  COMPLETED           │
  │  TRUCK           │  │  LARGE          │  │  CANCELLED           │
  └──────────────────┘  └─────────────────┘  └──────────────────────┘
```

*Say aloud:* TWO_WHEELER fits any spot; CAR fits MEDIUM or LARGE; TRUCK requires LARGE only.
This compatibility rule lives in `ParkingLotService.isCompatible()` — not in the enum itself.

---

### Move 4 — Draw the Two Smallest Entities

```
Board after Move 4:

  ┌────────────────────────┐   ┌──────────────────────────────────────────┐
  │  Vehicle               │   │  ParkingSpot                             │
  │  ────────────────────  │   │  ──────────────────────────────────────  │
  │  plateNumber: String   │   │  spotId: String                          │
  │  type: VehicleType     │   │  floor: int                              │
  └────────────────────────┘   │  spotType: SpotType                      │
                               │  parkedVehicle: Vehicle  // null = free  │
                               │  + assignVehicle(v: Vehicle): boolean    │
                               │  + releaseVehicle(): void                │
                               └──────────────────────────────────────────┘
```

---

### Move 5 — Name the Hot Resource and Explain the Guard

```
Board after Move 5 (annotation added to ParkingSpot):

  ┌──────────────────────────────────────────────────────────┐
  │  ParkingSpot                                             │
  │  ──────────────────────────────────────────────────────  │
  │  parkedVehicle: Vehicle  // null = available  ← HOT     │
  │  + assignVehicle(v): boolean  // synchronized           │
  │  + releaseVehicle(): void     // synchronized           │
  └──────────────────────────────────────────────────────────┘

  Guard: synchronized on the ParkingSpot INSTANCE (not the service).
    Two threads racing for the same spot → one gets true, one gets false.
    Two threads racing for DIFFERENT spots → no contention at all.
    Cross-JVM: add Redis SET spot:{spotId}:lock {plate} NX PX 30000.
```

*This is the SDE-3 signal* — proactively naming the hot resource before the
interviewer asks forces the concurrency conversation at the right layer.

---

### Move 6 — Draw ParkingFloor, ParkingLot, and Ticket

```
Board after Move 6:

  ┌──────────────────────────────┐  ┌────────────────────────────────────┐
  │  ParkingFloor                │  │  ParkingLot                        │
  │  ────────────────────────    │  │  ──────────────────────────────    │
  │  floorId: int                │  │  lotId: String                     │
  │  spots: List<ParkingSpot>    │  │  name: String                      │
  └──────────────────────────────┘  │  lat: double                       │
                                    │  lng: double                       │
                                    │  floors: List<ParkingFloor>        │
                                    └────────────────────────────────────┘

  ┌──────────────────────────────────────────────────┐
  │  Ticket                                          │
  │  ──────────────────────────────────────────────  │
  │  ticketId: String                                │
  │  spot: ParkingSpot                               │
  │  vehicle: Vehicle                                │
  │  entryTime: LocalDateTime                        │
  │  exitTime: LocalDateTime                         │
  │  status: TicketStatus                            │
  │  fee: BigDecimal                                 │
  │  + transition(newStatus: TicketStatus): void     │
  └──────────────────────────────────────────────────┘
```

---

### Move 7 — Add FeeStrategy Interface and ParkingLotService (≈75% — stop here)

```
Board after Move 7:

  «interface»
  FeeStrategy
  ─────────────────────────────────────────────
  + calculateFee(ticket: Ticket): BigDecimal
        △                △               △
  HourlyFeeStrategy  FlatFeeStrategy  EventFeeStrategy
  (hours × rate)     (fixed/day)      (premium flat)

  PlateCaptureService  «given — do not design»
  ─────────────────────────────────────────────
  + scanPlate(image: byte[]): String

  ParkingLotService
  ─────────────────────────────────────────────
  lot: ParkingLot
  feeStrategy: FeeStrategy           // injected — OCP
  activeTickets: Map<String, Ticket>
  + enter(plateNumber: String, type: VehicleType): Ticket
  + exit(ticketId: String): BigDecimal
```

*Explain:* `FeeStrategy` is Strategy pattern — `ParkingLotService` is closed to modification
when a new fee tier is added; a new implementation is plugged in. `PlateCaptureService`
is injected as a dependency — seam for mocking in tests; the lot doesn't care how plates
are captured.

---

## §3b — 🏗️ LLD — Complete Class Diagram

```
  ┌──────────────────┐  ┌─────────────────┐  ┌──────────────────────┐
  │  VehicleType     │  │  SpotType       │  │  TicketStatus        │
  │  ─────────────── │  │  ────────────── │  │  ─────────────────── │
  │  TWO_WHEELER     │  │  COMPACT        │  │  ACTIVE              │
  │  CAR             │  │  MEDIUM         │  │  COMPLETED           │
  │  TRUCK           │  │  LARGE          │  │  CANCELLED           │
  └───────┬──────────┘  └───────┬─────────┘  └──────────┬───────────┘
          │ type                │ spotType               │ status
          ▼                     ▼                        ▼
  ┌──────────────────────┐  ┌───────────────────────────────────────────────┐
  │  Vehicle             │  │  ParkingSpot                                  │
  │  ────────────────── │  │  ─────────────────────────────────────────── │
  │  plateNumber: String │  │  spotId: String                               │
  │  type: VehicleType   │  │  floor: int                                   │
  └──────────────────────┘  │  spotType: SpotType                           │
                             │  parkedVehicle: Vehicle // null=free ← HOT   │
                             │  + assignVehicle(v: Vehicle): boolean        │
                             │  + releaseVehicle(): void                    │
                             └────────────────────┬──────────────────────────┘
                                       0..*       │
  ┌──────────────────────────────────────────┐    │ contains
  │  ParkingFloor                            │◀───┘
  │  ──────────────────────────────────────  │
  │  floorId: int                            │
  │  spots: List<ParkingSpot>                │
  └──────────────┬───────────────────────────┘
        0..*     │ contains
  ┌──────────────▼───────────────────────────┐
  │  ParkingLot                              │
  │  ──────────────────────────────────────  │
  │  lotId: String                           │
  │  name: String                            │
  │  lat: double                             │
  │  lng: double                             │
  │  floors: List<ParkingFloor>              │
  └──────────────────────────────────────────┘

  ┌──────────────────────────────────────────────────────┐
  │  Ticket                                              │
  │  ────────────────────────────────────────────────── │
  │  ticketId: String                                    │
  │  spot: ParkingSpot                                   │
  │  vehicle: Vehicle                                    │
  │  entryTime: LocalDateTime                            │
  │  exitTime: LocalDateTime                             │
  │  status: TicketStatus                                │
  │  fee: BigDecimal                                     │
  │  + transition(newStatus: TicketStatus): void         │
  └──────────────────────────────────────────────────────┘

  «interface»
  FeeStrategy
  ──────────────────────────────────────────────────────
  + calculateFee(ticket: Ticket): BigDecimal
        △                  △                  △
  HourlyFeeStrategy  FlatFeeStrategy    EventFeeStrategy

  PlateCaptureService  «given — provided upfront by interviewer»
  ──────────────────────────────────────────────────────
  + scanPlate(image: byte[]): String

  ParkingLotService
  ──────────────────────────────────────────────────────
  lot: ParkingLot
  feeStrategy: FeeStrategy
  plateCapture: PlateCaptureService
  activeTickets: Map<String, Ticket>
  + enter(plateNumber: String, type: VehicleType): Ticket
  + exit(ticketId: String): BigDecimal
  – findAndAssign(type: VehicleType, v: Vehicle): ParkingSpot
  – isCompatible(vehicleType: VehicleType, spotType: SpotType): boolean
```

---

## §4 — 🧭 Design Decisions

| Decision | Why | Rejected alternative |
|---|---|---|
| **Strategy pattern for fee (`FeeStrategy`)** | OCP — new fee tiers (event pricing, monthly pass) plug in without touching `ParkingLotService`. Each strategy is independently testable. | `switch` on fee type inside `exit()` — breaks OCP on every new tier; no isolation between strategies |
| **`synchronized` on `ParkingSpot.assignVehicle()`** | Spot-level granularity — two different spots are assigned concurrently with zero contention; only same-spot assignments are serialized. | `synchronized` on `ParkingLotService.enter()` — every entry waits behind every other entry regardless of which spot; throughput bottleneck |
| **`parkedVehicle == null` as availability sentinel** | One field, one check, one write — all under the same `synchronized` block. No separate boolean to keep in sync. | Separate `boolean isAvailable` field — two fields that must stay in sync; race between reading `isAvailable` and reading `parkedVehicle` |
| **State machine on `Ticket.transition()`** | Guards illegal transitions early — COMPLETED → ACTIVE throws immediately. Transition logic is co-located with the state, not scattered across callers. | Free-form status update from service layer — any code can set any status; violations surface late as data corruption |
| **`PlateCaptureService` injected as interface** | Seam for testing — mock the AI service in unit tests without any network calls. Decouples `ParkingLotService` from the AI implementation. | Direct instantiation inside `enter()` — untestable; changing AI vendor requires modifying `ParkingLotService` |

---

## §5 — 🔌 Key Interfaces

```java
public interface FeeStrategy {
    BigDecimal calculateFee(Ticket ticket);
}
```

```java
public class HourlyFeeStrategy implements FeeStrategy {

    private final Map<VehicleType, BigDecimal> ratePerHour;

    public HourlyFeeStrategy(Map<VehicleType, BigDecimal> ratePerHour) {
        this.ratePerHour = ratePerHour;
    }

    @Override
    public BigDecimal calculateFee(Ticket ticket) {
        long hours = ChronoUnit.HOURS.between(
            ticket.getEntryTime(),
            ticket.getExitTime()
        );
        // any partial hour is billed as a full hour
        long billableHours = Math.max(1, hours);
        BigDecimal rate = ratePerHour.get(ticket.getVehicle().getType());
        return rate.multiply(BigDecimal.valueOf(billableHours));
    }
}
```

---

## §6 — ⚙️ Code — Three Methods

### Method 1 — `ParkingSpot.assignVehicle()` — the hot-resource guard

**Steps in plain English:**

1. **Acquire the spot-level lock** — `synchronized` on `this` (the spot instance, not the service).
2. **Check availability** — if `parkedVehicle != null`, spot is taken; return false immediately.
3. **Assign atomically** — set `parkedVehicle = vehicle` inside the same synchronized block; return true.

```java
public class ParkingSpot {

    private Vehicle parkedVehicle;

    // Step 1 — lock on THIS spot instance; other spots lock independently
    public synchronized boolean assignVehicle(Vehicle vehicle) {
        // Step 2 — check if taken (read + decision inside same lock)
        if (this.parkedVehicle != null) {
            return false;
        }
        // Step 3 — assign atomically; no window between check and set
        this.parkedVehicle = vehicle;
        return true;
    }

    public synchronized void releaseVehicle() {
        this.parkedVehicle = null;
    }

    public synchronized boolean isAvailable() {
        return this.parkedVehicle == null;
    }
}
```

> **Why spot-level, not service-level?** Two cars entering to different spots
> have zero contention — no reason to serialize them. `synchronized` on the
> service would make every entry wait behind every other entry regardless
> of which spot each car wants.

---

### Method 2 — `Ticket.transition()` — state machine guard

**Steps in plain English:**

1. **Look up allowed next states** for the current status from a static map.
2. **Reject the transition** if the requested status is not in the allowed set.
3. **Advance the state** if the transition is valid.

```java
public class Ticket {

    private TicketStatus status = TicketStatus.ACTIVE;

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED =
        Map.of(
            TicketStatus.ACTIVE,
            Set.of(TicketStatus.COMPLETED, TicketStatus.CANCELLED)
        );

    // Step 1 — look up what transitions are legal from current status
    public void transition(TicketStatus newStatus) {
        Set<TicketStatus> allowed = ALLOWED.getOrDefault(this.status, Set.of());

        // Step 2 — reject illegal transition
        if (!allowed.contains(newStatus)) {
            throw new IllegalStateException(
                "Invalid transition: " + this.status + " → " + newStatus
            );
        }

        // Step 3 — advance state
        this.status = newStatus;
    }
}
```

> **Why a static map over a switch?** The allowed-transitions map is immutable
> and declared once at class load. Adding a new state (e.g., DISPUTED) means
> adding one entry to the map — no new `case` arm buried inside a method body.

---

### Method 3 — `ParkingLotService.exit()` — orchestrates fee + state + spot release

**Steps in plain English:**

1. **Look up the active ticket** — throw if not found.
2. **Record exit time** — required for duration calculation.
3. **Calculate fee** via the injected `FeeStrategy`.
4. **Advance the ticket state** to COMPLETED via the state machine guard.
5. **Release the spot** so the next vehicle can claim it.

```java
public class ParkingLotService {

    private final ParkingLot lot;
    private final FeeStrategy feeStrategy;
    private final Map<String, Ticket> activeTickets = new ConcurrentHashMap<>();

    // Step 1 — look up the in-flight ticket
    public BigDecimal exit(String ticketId) {
        Ticket ticket = activeTickets.get(ticketId);
        if (ticket == null) {
            throw new TicketNotFoundException("Ticket not found: " + ticketId);
        }

        // Step 2 — record exit time before fee calculation
        ticket.setExitTime(LocalDateTime.now());

        // Step 3 — delegate fee calculation to the injected strategy
        BigDecimal fee = feeStrategy.calculateFee(ticket);
        ticket.setFee(fee);

        // Step 4 — advance state machine (throws if already COMPLETED/CANCELLED)
        ticket.transition(TicketStatus.COMPLETED);

        // Step 5 — release the spot so the next vehicle can be assigned
        ticket.getSpot().releaseVehicle();
        activeTickets.remove(ticketId);

        return fee;
    }
}
```

> **Payment intentionally absent here:** In production, `PaymentService.charge()`
> sits between steps 3 and 4 — fee calculated → payment attempted → state advances
> to COMPLETED only on success. Failure keeps ticket ACTIVE; vehicle retries at kiosk.
> In HLD, payment row + COMPLETED update happen in a single ACID transaction in MySQL.

---

## §7 — 🔁 Concurrency

### The race: two cars, one spot

```
Thread 1 (Pod A)                 Thread 2 (Pod B)
────────────────────────         ────────────────────────
enter("KA01AB1234", CAR)         enter("MH02CD5678", CAR)
  → iterate floors + spots         → iterate floors + spots
  → spot-42 parkedVehicle == null  → spot-42 parkedVehicle == null
  → call spot42.assignVehicle()    → call spot42.assignVehicle()

    synchronized block opens ─────────────────────────────────────┐
    Thread 1 owns the monitor                                      │
    parkedVehicle == null → assign → return true                   │
                                   Thread 2 waits...              │
                                   Thread 2 acquires monitor ──────┘
                                   parkedVehicle != null → return false
                                   → iterate to next available spot
```

### Single-JVM fix — `synchronized` on the spot instance

`ParkingSpot.assignVehicle()` is `synchronized` — one thread owns the monitor
at a time. Thread 2 gets `false` and moves immediately to the next spot.
No starvation: Thread 2 never blocks indefinitely — it moves on.

### Cross-JVM fix — Redis distributed lock

`synchronized` does not span JVM processes. Two pods each hold their own copy
of `ParkingSpot` in memory — each `synchronized` block runs independently.
Both pods can read `parkedVehicle == null` and both proceed.

**Production solution: Redis SET NX**

```
Before calling assignVehicle():

  SET spot:{spotId}:lock {plateNumber} NX PX 30000
       │                               │      │
       │                               │      └── auto-expire in 30s
       │                               └── only set if key does not exist
       └── uniquely identifies the physical spot
```

Only the pod receiving `OK` proceeds. The other gets `nil` and skips to the next spot.

**Why PX 30000?**
If the winning pod crashes after acquiring the Redis lock but before completing
the MySQL insert, the lock expires in 30s automatically. The spot auto-releases —
no attendant intervention, no orphaned locks.

### `AtomicReference` alternative — for the depth-probe answer

```java
private final AtomicReference<Vehicle> parkedVehicle = new AtomicReference<>(null);

public boolean assignVehicle(Vehicle vehicle) {
    // CAS: atomically sets to vehicle only if current value is null
    return parkedVehicle.compareAndSet(null, vehicle);
}
```

Lock-free CAS — no thread ever blocks. `synchronized` is simpler and more readable;
`AtomicReference` is non-blocking, which matters when many threads race for the same
spot and you want each loser to move on immediately without queuing on a monitor.

---

## §8 — 🧨 Java Depth Probes

| Question | Answer |
|---|---|
| "Why `synchronized` on the spot, not the service?" | Spot-level granularity — different spots have zero contention. Service-level lock serializes all concurrent entries through one monitor regardless of which spots they want. |
| "Would `@Version` (optimistic locking) work here?" | Yes for DB-backed spots. JPA throws `OptimisticLockException` if two transactions update the same row. Caller catches and retries another spot. Works at low contention; retry storm at high contention (peak entry rush). |
| "`ReentrantLock` vs `synchronized` here?" | `ReentrantLock.tryLock()` is actually better — you skip the spot immediately without blocking. `synchronized` blocks until the lock is free, wasting time when you'd rather check the next spot. In the code shown `synchronized` works because `assignVehicle()` is instant; in a real system with network calls inside, prefer `tryLock(0, MILLISECONDS)`. |
| "`AtomicReference<Vehicle>` vs `synchronized`?" | Same correctness guarantee. `compareAndSet(null, vehicle)` is lock-free CAS. Prefer it when many threads race for the same spot and you want zero blocking. `synchronized` is simpler when contention is rare. |
| "Would virtual threads help here?" | The `PlateCaptureService` AI call is I/O-bound (~500ms). A virtual thread releases its carrier thread during the await — useful when many vehicles arrive simultaneously. Fee calculation (CPU-bound arithmetic) gets zero benefit from virtual threads. |
| "What does `ConcurrentHashMap` buy for `activeTickets`?" | Safe concurrent `get()` + `put()` without a global lock — bucket-level CAS for inserts, volatile reads for gets. It does NOT solve the spot-assignment race; that race is at the `ParkingSpot` level. The map just holds in-flight tickets safely across threads. |

---

## §9 — 🌐 HLD — How to Build This Diagram in the Interview — 3 Phases

### Phase 1 — Numbers First (≈2 min)

```
Scale assumption: city-wide parking network

  Lots            500 lots × 200 spots = 100,000 spots total
  Daily entries   500 lots × 200 entries/lot/day = 100,000 entries/day
  Peak window     9–10am, 6–7pm → 100K / (2hr × 3600s) ≈ 14 entries/sec
  Search reads    10M city users, 1% searching at peak = 100K users
                  × 5 searches per 30-min session / 1800s ≈ 278 searches/sec
  Payments        1 per exit ≈ 14 TPS  (trivial for MySQL)
  Storage         1 ticket ≈ 1 KB × 100K/day × 365 ≈ 36 GB/year (no sharding)

HOT PATH:     search reads (278/sec) — QUERY SHAPE problem, not throughput
HOT RESOURCE: ParkingSpot.parkedVehicle — CORRECTNESS problem, not throughput

Key insight: 278 searches/sec is manageable throughput-wise, but each query
computes distance on every lot row then joins spots for availability —
O(lots × spots) scan per request with no geospatial index.

The architecture is forced by two separate concerns:
  (1) Query shape  → nearest-lot search forces Redis GEOSEARCH
  (2) Correctness  → cross-JVM spot contention forces Redis SET NX
```

---

### Phase 2 — Skeleton: Simplest System That Could Work (≈3 min)

```
── Skeleton: Simplest System That Could Work ──────────────────────

   ┌────────────────────────────────────────────────┐
   │  Client   Mobile App · Barrier Terminal        │
   └──────────────┬───────────────┬─────────────────┘
                  │ HTTPS         │ HTTPS
   ┌──────────────▼───────────────▼─────────────────┐
   │  API Gateway  (auth · routing)                 │
   └──────┬──────────────────────────────┬──────────┘
          │                              │
   ┌──────▼────────────────┐   ┌─────────▼────────────────────────────┐
   │  SearchService        │   │  EntryService                        │
   │  (find nearby lots)   │   │       └──▶ PlateCaptureService (AI) │
   └──────┬────────────────┘   │  ExitService                        │
          │                    │       └──▶ PaymentService → Stripe  │
          │                    │  NotificationService → Email / SMS  │
          │                    └─────────────────────┬────────────────┘
          │                                          │
   ┌──────▼──────────────────────────────────────────▼──────────────┐
   │  MySQL  (lots · floors · spots · tickets · payments)           │
   └────────────────────────────────────────────────────────────────┘

BREAKING POINT — walk this skeleton against the Phase 1 numbers:
  (a) SearchService → MySQL at 278 searches/sec: each query computes distance
      on every lot row then joins spots for availability count — O(lots × spots)
      full scan; no geospatial index; latency ~50ms, not the <1s target.
  (b) Two EntryService pods both read spot-42 as parkedVehicle == null and both
      proceed — synchronized is per-JVM; cross-pod oversell is possible.
  (c) PlateCaptureService AI call is synchronous on the barrier critical path
      — ~500ms average; AI service timeout = barrier cannot open at all.
  (d) PaymentService success + ticket.status = COMPLETED are two separate
      operations — pod crash between them leaves ticket ACTIVE (ghost billing).

══════════════════════════════════════════════════════════════════
```

---

### Phase 3 — Upgrade It: One Fix per Pain Point (≈5 min)

*"This works in dev. Now let me address each breaking point."*

**BREAKING POINT (a) → Redis GEO index for nearest-lot search**

`GEOADD lots:{city} {lng} {lat} {lotId}` on lot registration.
`GEOSEARCH lots:{city} FROMLONLAT {lng} {lat} BYRADIUS 2 km ASC COUNT 10`
on each search — O(log N + M), result in <1ms.
Store available spot count as `lot:{lotId}:avail` Redis integer; `DECR` on entry,
`INCR` on exit — always current, single-command read, no spots-table join.

**BREAKING POINT (b) → Redis SET NX for cross-pod spot claim**

Before MySQL INSERT, each pod executes:
`SET spot:{spotId}:lock {plateNumber} NX PX 30000`
Only one pod gets `OK`; the other gets `nil` and iterates to the next spot.
PX 30000 auto-releases if the pod crashes before completing the MySQL write.

**BREAKING POINT (c) → Pre-scan the plate before the barrier**

Vehicle enters detection zone 30m before the barrier.
`PlateCaptureService` scans asynchronously → writes `plate:{plate}:scan` to Redis
with `PX 120000` (2-min TTL). EntryService at the barrier reads from cache (~1ms) —
no synchronous AI call on the physical gate critical path.
If cache miss → synchronous call with 2s timeout → attendant manual override.

**BREAKING POINT (d) → Single ACID transaction on exit**

ExitService writes the payment row + `ticket.status = COMPLETED` in ONE MySQL
transaction. Either both commit or neither does — no ghost billing window.
After commit, emit `parking.exited` to Kafka for async notification.
Kafka is outside the transaction (at-least-once); NotificationService deduplicates
on `ticketId` before sending.

---

```
── Production: All 4 Upgrades Applied ────────────────────────────

   ┌────────────────────────────────────────────────┐
   │  Client   Mobile App · Barrier Terminal        │
   └──────────────┬───────────────┬─────────────────┘
                  │ HTTPS         │ HTTPS
   ┌──────────────▼───────────────▼──────────────────────────────┐
   │  API Gateway  (JWT · rate-limit · TLS · routing)            │
   └──────┬───────────────────────────────────────────┬──────────┘
          │                                           │
   ┌──────▼────────────────────┐   ┌──────────────────▼──────────────────────┐
   │  SearchService            │   │  EntryService                           │
   │  GEOSEARCH 2km → lots     │   │  1. GET plate:{p}:scan  (~1ms)         │
   │  GET lot:{id}:avail       │   │  2. SET spot:{id}:lock NX PX 30000     │
   └──────┬────────────────────┘   │  3. INSERT ticket  (ACID)              │
          │                        │  ExitService                           │
          │                        │  1. calculateFee  (FeeStrategy)         │
          │                        │  2. PaymentService ──▶ Stripe (30s TO) │
          │                        │  3. ACID: payment + COMPLETED           │
          │                        └─────────────────────┬───────────────────┘
          │ GEOSEARCH + GET lot:avail                    │ SET spot:lock NX PX
          ▼                                              ▼ DECR lot:avail
   ┌──────────────────────────────────────────────────────────────────────┐
   │  Redis                                                               │
   │  lots:{city}     → GEO index (lat,lng,lotId)     ← SearchSvc       │
   │  lot:{id}:avail  → available spot count · EX 60  ← EntrySvc       │
   │  spot:{id}:lock  → plateNumber · PX 30000        ← EntrySvc       │
   │  plate:{p}:scan  → AI result   · PX 120000       ← PlateCaptureSvc │
   └──────────────────────────┬───────────────────────────────────────────┘
                              │ cache miss / ACID write
   ┌──────────────────────────▼───────────────────────────────────────────┐
   │  MySQL  (ACID)                                                       │
   │  parking_lots · parking_floors · parking_spots  ← SearchSvc        │
   │  tickets · payments  (single ACID tx on exit)   ← ExitSvc          │
   └──────────────────────────┬───────────────────────────────────────────┘
                              │ emit parking.exited
   ┌──────────────────────────▼───────────────────────────────────────────┐
   │  Kafka  (topic: parking-events, key = ticketId)                     │
   │  ├──▶ NotificationService  email receipt · SMS  · retry via DLQ    │
   │  └──▶ AnalyticsService     occupancy tracking · revenue metrics    │
   └──────────────────────────────────────────────────────────────────────┘

KEY INVARIANT: Redis SET NX is the cross-JVM gate — no two pods can claim
  the same spot; PX 30000 auto-releases if the pod crashes before MySQL
  commit. The exit ACID transaction closes the payment↔status split-brain:
  committed = COMPLETED, rolled back = ACTIVE (safe retry at kiosk).
  PlateCapture result is pre-cached before the vehicle reaches the barrier —
  the physical gate never blocks on AI latency.
══════════════════════════════════════════════════════════════════
```

---

## §10 — 🏛️ HLD Decisions

| Component | Why chosen | Rejected + why |
|---|---|---|
| **Redis GEO for lot search** | `GEOSEARCH` is O(log N + M); nearest lots in <1ms. Available-spot count as a Redis integer (DECR/INCR) — sub-ms read, no join needed. | MySQL `ST_Distance` — requires spatial index tuning; still needs a join on spots table for availability; ~10-50ms vs <1ms for Redis |
| **Redis SET NX for spot lock** | Atomic distributed lock — one command, one winner across all pods; PX TTL auto-releases on pod crash. No manual lock cleanup. | `SELECT FOR UPDATE` on spot row — holds a DB connection open for the lock duration; connection-pool pressure at peak entry rush |
| **Async plate pre-scan** | Eliminates AI latency from the barrier critical path. Scan happens before the vehicle arrives; barrier reads from Redis in ~1ms. | Synchronous AI call at barrier — 500ms average; AI timeout = lot unusable; a physical gate cannot wait on a network call |
| **Single ACID tx on exit** | Payment row + ticket COMPLETED in one transaction — committed = COMPLETED, rolled back = ACTIVE. No ghost billing. | Two separate operations — pod crash between charge and status update bills the customer but never closes the ticket |
| **Kafka for notifications** | Email/SMS does not need to block the exit response or the barrier lifting. At-least-once + DLQ for retries. NotificationService deduplicates on ticketId. | Synchronous email call on exit path — provider latency (~200-500ms) delays barrier opening for every vehicle |

---

## §11 — 📡 API Design

### GET /v1/parking/search — find nearby lots (read, cacheable)

```
GET /v1/parking/search?lat=12.97&lng=77.59&radius=2&radiusUnit=km
Authorization: Bearer {jwt}

200 OK
{
  "lots": [
    {
      "lotId": "lot-cbdBengaluru-01",
      "name": "MG Road Parking",
      "distanceMeters": 320,
      "availableSpots": { "COMPACT": 12, "MEDIUM": 3, "LARGE": 0 },
      "pricingPerHour": { "TWO_WHEELER": 20, "CAR": 60, "TRUCK": 120 }
    }
  ]
}
```

---

### POST /v1/parking/entry — barrier terminal calls on vehicle arrival (write)

```
POST /v1/parking/entry
X-Idempotency-Key: {uuid}            ← terminal generates on first scan
Authorization: Bearer {barrier-jwt}
Content-Type: application/json

{
  "plateNumber": "KA01AB1234",
  "vehicleType": "CAR",
  "lotId": "lot-cbdBengaluru-01"
}

201 Created
{
  "ticketId": "tkt-7f3a9b",
  "spotId": "spot-F2-042",
  "floor": 2,
  "entryTime": "2026-08-17T09:15:00Z"
}

409 Conflict            ← vehicle already has ACTIVE ticket (re-entry before exit)
503 Service Unavailable ← no compatible spot can be claimed (lot full)
```

---

### POST /v1/parking/exit — barrier terminal calls on vehicle departure (write)

```
POST /v1/parking/exit
X-Idempotency-Key: {uuid}
Authorization: Bearer {barrier-jwt}
Content-Type: application/json

{
  "ticketId": "tkt-7f3a9b"
}

200 OK
{
  "ticketId": "tkt-7f3a9b",
  "fee": 150.00,
  "currency": "INR",
  "duration": "2h30m",
  "status": "COMPLETED"
}

402 Payment Required    ← payment failed; barrier stays closed; redirect to kiosk
```

---

## §12 — 🛤️ Happy + Unhappy Paths

### Happy path — full entry-to-exit arc

```
1. User opens app → GET /search?lat=...&lng=... → SearchService →
   Redis GEOSEARCH → returns nearby lots with available spot counts.

2. User drives toward chosen lot → vehicle enters detection zone (30m before
   barrier) → PlateCaptureService scans async → writes plate:{p}:scan to Redis
   (PX 120000).

3. Vehicle reaches barrier → EntryService:
   a. GET plate:{p}:scan from Redis  (~1ms — plate verified).
   b. SET spot:F2-042:lock KA01AB1234 NX PX 30000  (spot claimed).
   c. INSERT ticket row in MySQL  (ACID).
   d. DECR lot:{id}:avail in Redis.
   Barrier lifts. Ticket ID displayed on terminal.

4. Vehicle exits → ExitService:
   a. Fetch ticket by ticketId from MySQL.
   b. Set exitTime = now(); calculateFee via HourlyFeeStrategy.
   c. PaymentService → Stripe  (charge linked card).
   d. Single MySQL ACID tx: INSERT payment row + UPDATE ticket → COMPLETED.
   e. INCR lot:{id}:avail in Redis.
   f. Emit parking.exited to Kafka.
   Barrier lifts.

5. Kafka → NotificationService → email receipt within ~10s.
```

---

### Unhappy path 1 — last-spot race (correctness)

```
Two cars arrive simultaneously for the last COMPACT spot (spot-F1-099).

Pod A: SET spot:F1-099:lock KA01AB1234 NX PX 30000 → OK   (wins)
Pod B: SET spot:F1-099:lock MH02CD5678 NX PX 30000 → nil  (loses)

Pod A: INSERT ticket, DECR lot:avail → COMPACT count = 0.
Pod B: iterates to next compatible spot.
  → MEDIUM available and CAR fits MEDIUM: assigns there.
  → No compatible spot left: returns 503 LOT_FULL.
```

---

### Unhappy path 2 — PlateCapture timeout at barrier

```
Vehicle reaches barrier → GET plate:{p}:scan → Redis miss (skipped detection zone).
EntryService → synchronous PlateCaptureService → 2s timeout exceeded.
Fallback: attendant presses manual-override → manual plate entry → standard flow.

If AI service is consistently down: circuit breaker trips (Resilience4j);
all barrier flows fall back to full manual entry; alert fires to ops.
```

---

### Unhappy path 3 — payment failure on exit

```
ExitService → PaymentService → Stripe → 402 (card declined).
Ticket stays ACTIVE. Barrier does not lift.
Retry: 3× exponential backoff (1s, 2s, 4s) inside PaymentService.

All retries fail:
  → Driver directed to payment kiosk (cash or alternate card).
  → Staff calls POST /v1/parking/exit with manual payment ref.
  → ACID tx commits → barrier lifts.
```

---

### Unhappy path 4 — pod crash mid-entry (split-brain)

```
Pod A:
  SET spot:F2-042:lock KA01AB1234 NX PX 30000 → OK
  [POD CRASHES — before INSERT ticket]

Outcome:
  Redis lock expires in 30s → spot auto-released.
  MySQL:   no ticket row → no ghost active ticket.
  Barrier: 201 never returned → barrier never lifted.

After 30s: spot is available again.
Vehicle retries → next pod succeeds → clean entry.
No human intervention needed.
```

---

## §13 — ⚠️ Fault Tolerance

| External call | Timeout | Retry policy | Fallback |
|---|---|---|---|
| **PlateCaptureService (AI)** | 2s | 0 retries (gate cannot wait) | Manual plate entry; attendant terminal override |
| **Stripe (payment on exit)** | 30s | 3× exponential backoff (1s, 2s, 4s) | Redirect to cash kiosk; manual-override API with payment ref |
| **Redis** | 50ms | 1 immediate retry | Skip GEO → MySQL `ST_Distance` fallback (slower); skip SET NX → DB `SELECT FOR UPDATE` |
| **MySQL** | 5s | 1 retry with new connection | Fail-fast 503; circuit breaker after 5 consecutive failures |
| **Kafka (emit event)** | 5s | 3× producer retry; DLQ on all failures | Notification lost; ticket already COMPLETED in MySQL — no rollback needed |

---

## §14 — 📐 Q&A — Tier-2 JPMC Probes

**Q: How do you prevent double-billing if the exit terminal times out and retries POST /exit?**

> The exit endpoint requires `X-Idempotency-Key` (UUID generated by the terminal on first
> attempt). Server checks Redis `idem:{key}:exit` — if present, returns the original `200`
> response without re-processing. Key expires after 24h. Even without the key, the state
> machine guard catches it: `Ticket.transition(COMPLETED)` on an already-COMPLETED ticket
> throws `IllegalStateException` before any payment or MySQL write happens.

**Q: Your `lot:{id}:avail` counter — how do you keep it consistent with MySQL after a Redis restart?**

> Redis is the fast-path read; MySQL is the source of truth. On every ACID entry/exit commit,
> `DECR`/`INCR` `lot:{id}:avail` in the same request thread immediately after the MySQL commit.
> If Redis restarts: a reconciliation job queries `SELECT COUNT(*) FROM parking_spots WHERE
> lot_id = ? AND parked_vehicle_id IS NULL` per lot and re-seeds the Redis key. A stale count
> of ±2 spots is tolerable — worst case: user drives to a "1 spot" lot and finds 0. They get
> a graceful `503 LOT_FULL`, not an oversell.

**Q: AI plate-capture is a black box — what if it starts returning wrong plates?**

> `PlateCaptureService` is an interface — mocked in tests, swappable in production.
> In production: (1) log every scan result with a confidence score. (2) Monitor
> `plate_scan_error_rate` — circuit breaker trips above 5%, falling back to manual entry.
> (3) On a billing dispute, the stored `plate:{p}:scan` result (confidence score + timestamp)
> is the audit trail for resolution.

---

## §15 — 🧾 TL;DR

**Entities:** `ParkingLot → ParkingFloor → ParkingSpot (hot resource) → Ticket`

**Patterns:**
- **Strategy** — `FeeStrategy` (hourly / flat / event): new tier = new class, zero changes to `ParkingLotService`
- **State machine** — `Ticket.transition()` guards ACTIVE → COMPLETED / CANCELLED
- **Synchronized** on `ParkingSpot.assignVehicle()` (spot-level, not service-level) + Redis SET NX for cross-JVM

**HLD shape:**
- `SearchService` → Redis GEOSEARCH (nearest lots in <1ms) + `lot:{id}:avail` integer
- `EntryService` → GET plate cache (pre-scanned async, ~1ms) → SET NX spot lock → ACID insert
- `ExitService` → FeeStrategy + Stripe + single ACID tx (payment row + ticket COMPLETED)
- Kafka fan-out → `NotificationService` (idempotent on ticketId) + `AnalyticsService`

**SDE-3 signals to surface proactively:**
- `synchronized` is per-JVM → Redis SET NX required for multi-pod deployment
- PlateCapture must be pre-cached so the physical gate never blocks on AI latency
- Payment + status update must be one ACID transaction (no ghost-billing window)
- Architecture is driven by query shape (GEOSEARCH) and correctness (NX lock) — not raw throughput

---

## §16 — 🔄 Changelog

| Date | Change |
|------|--------|
| Aug 2026 | File created. Full 16-section solution for Parking Lot — most reported JPMC Round 3 problem (⭐⭐⭐). LLD: VehicleType/SpotType/TicketStatus enums; Vehicle/ParkingSpot/ParkingFloor/ParkingLot/Ticket entities; FeeStrategy (Strategy pattern); ParkingLotService orchestrator. Concurrency: synchronized on assignVehicle() spot-level + Redis SET NX for cross-JVM. HLD: 3-phase Confluent construction guide, Confluent single-column diagrams. JPMC-specific: AI plate-capture pre-given; pre-scan cache eliminates barrier latency; exit ACID tx closes payment↔status split-brain. |
