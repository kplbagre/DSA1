# Parking Lot

> **Standard followed:** `LLD/notes-standards.md`

---

## 🎯 Problem Statement

Design a multi-floor parking lot that parks vehicles of different sizes. The lot has different spot types (compact, large, handicapped, EV). When a vehicle arrives, assign the first available matching spot and issue a ticket. When it exits, calculate the fee and free the spot.

---

## 📖 Requirements

**Functional:**
- Park a vehicle — find first available spot matching the vehicle's required spot type
- Issue a `ParkingTicket` on entry (records spot, floor, entry timestamp)
- Unpark a vehicle — calculate fee via active strategy, vacate the spot
- Check how many spots of a given type are available
- Support multiple floors, each with any mix of spot types
- Spot types: `COMPACT`, `LARGE`, `HANDICAPPED`, `EV`

**Non-functional:**
- Thread-safe — two vehicles entering simultaneously must never get the same spot
- Fee strategy must be swappable without modifying `ParkingLot`
- Adding a new spot type (e.g., `MOTORCYCLE`) = one new class + one factory case, zero changes elsewhere

---

## 🏗️ Class Design

### 🎨 Visual — Class Structure

```
┌──────────────────────────────────────────────────┐
│                   ParkingLot                     │
│  - floors: List<ParkingFloor>                    │
│  - feeStrategy: FeeStrategy                      │
│  - activeTickets: Map<String, ParkingTicket>     │
│  + parkVehicle(Vehicle, SpotType): ParkingTicket │
│  + unparkVehicle(ticketId): ParkingTicket        │
└───────────┬──────────────────────┬───────────────┘
            │ 1..*                 │ uses
            ▼                      ▼
┌─────────────────────┐   ┌──────────────────────┐
│    ParkingFloor     │   │  <<interface>>        │
│  - floorNumber: int │   │  FeeStrategy          │
│  - spots: List<>    │   │  + calculate(v, min)  │
│  + findAvailable()  │   └──────────┬───────────┘
└──────────┬──────────┘              │ implements
           │ 1..*             ┌──────┴──────┐
           ▼                  │             │
┌──────────────────────┐  Hourly      FlatRate
│ <<interface>>        │  FeeStrategy FeeStrategy
│ ParkingSpot          │
│ + getType()          │     ParkingSpotFactory
│ + isAvailable()      │     + create(SpotType)
│ + assignVehicle(v)   │          │ creates
│ + removeVehicle()    │          ▼
└──────────┬───────────┘  CompactSpot  LargeSpot
           │ implements   HandicappedSpot  EVSpot
      ──── each ────

ParkingTicket
  - ticketId: String
  - vehicle: Vehicle          Vehicle
  - spot: ParkingSpot    ←──  - licensePlate: String
  - floorNumber: int          - type: VehicleType
  - entryTime: LocalDateTime

KEY INVARIANT:
   ParkingLot holds the lock — spot assignment is one atomic
   check-then-assign, preventing two threads from getting the same spot.
```

---

## 🔌 Key Interfaces

```java
/**
 * Contract every parking spot must fulfil.
 * Spot-level assign/remove are synchronized to guard the parkedVehicle field.
 */
public interface ParkingSpot {

    SpotType getType();

    boolean isAvailable();

    // Throws IllegalStateException if already occupied
    void assignVehicle(Vehicle vehicle);

    // Returns the vehicle that was parked; nulls out the field
    Vehicle removeVehicle();

    Vehicle getParkedVehicle();
}
```

```java
/**
 * Strategy for fee calculation — swappable without touching ParkingLot.
 * All the algorithm lives here; the caller only calls calculate().
 */
public interface FeeStrategy {

    // durationMinutes is time from entry to exit
    double calculate(Vehicle vehicle, long durationMinutes);
}
```

---

## 🧭 Design Decisions

| Decision | Why |
|---|---|
| **Factory for spot creation** | `ParkingFloor` never calls `new CompactSpot()` — it calls `ParkingSpotFactory.create(type)`. Adding `MOTORCYCLE` = one new class + one factory case. Zero floor changes. |
| **Strategy for fee** | Regular vs event vs first-15-minutes-free are different algorithms, not just parameter differences. Each gets its own class. The parking lot holds a reference and calls `calculate()` without knowing which one runs. |
| **`synchronized` on `parkVehicle()`** | The check (`isAvailable()`) and the assign (`assignVehicle()`) must be atomic. Without the lock, Thread A and B both read `available=true`, both proceed — same spot, two vehicles. See Concurrency section. |
| **`ConcurrentHashMap` for active tickets** | Reads and writes to the ticket map happen from different threads (park vs unpark). `ConcurrentHashMap` gives safe concurrent access without locking the whole map. |
| **`ParkingFloor` owns the spot list** | Searching per-floor keeps the responsibility local. `ParkingLot` iterates floors; each floor searches its own list. Clean delegation. |

---

## 🎨 Visual — Object Interaction

```
Vehicle Enters:

Client           ParkingLot (synchronized)   ParkingFloor      ParkingSpot
  │                      │                        │                  │
  │  parkVehicle(v, t)   │                        │                  │
  │─────────────────────▶│  LOCK acquired         │                  │
  │                      │                        │                  │
  │                      │  findAvailableSpot(t)  │                  │
  │                      │───────────────────────▶│                  │
  │                      │                        │  isAvailable()?  │
  │                      │                        │─────────────────▶│
  │                      │                        │◀── true ─────────│
  │                      │◀────── spot ───────────│                  │
  │                      │                        │                  │
  │                      │  assignVehicle(vehicle)│                  │
  │                      │────────────────────────────────────────▶  │
  │                      │  floor.decrementAvailable()               │
  │                      │  new ParkingTicket(...)                   │
  │                      │  activeTickets.put(id, ticket)            │
  │                      │  LOCK released                            │
  │◀────── ticket ───────│                                           │

Vehicle Exits:

Client           ParkingLot                   FeeStrategy
  │                  │                              │
  │  unparkVehicle   │                              │
  │  (ticketId)      │                              │
  │─────────────────▶│  activeTickets.remove(id)    │
  │                  │  calculate exit time         │
  │                  │  calculate(vehicle, minutes) │
  │                  │─────────────────────────────▶│
  │                  │◀──────── fee ────────────────│
  │                  │  spot.removeVehicle()        │
  │                  │  floor.incrementAvailable()  │
  │◀── ticket+fee ───│

KEY INVARIANT:
   Park is synchronized end-to-end; unpark only needs the ticket map
   (ConcurrentHashMap) — no full lock needed on exit.
```

---

## 🖊️ Coding Skeleton

**Interview coding order — write in this sequence to never get stuck:**

1. **Enums** — `SpotType`, `SpotStatus`, `VehicleType` (zero dependencies, write first)
2. **Interfaces** — `ParkingSpot`, `FeeStrategy` (define the contracts before any class)
3. **Simple domain** — `Vehicle`, `ParkingTicket` (depend only on enums and interfaces)
4. **Concrete spots** — `CompactSpot` in full; stub `LargeSpot`, `HandicappedSpot`, `EVSpot` as "same pattern"
5. **Fee strategies** — `HourlyFeeStrategy` (the math is 3 lines)
6. **Factory** — `ParkingSpotFactory.create(SpotType)` — one switch, done
7. **`ParkingFloor`** — holds spots, provides `findAvailableSpot(type)`
8. **`ParkingLot`** — orchestrator: inject floors + strategy, implement park/unpark

**Why this order?** Enums → interfaces → leaf classes → orchestrator. Nothing you write at step N depends on something you haven't written yet. The orchestrator is always last because it depends on everything.

**Class stubs to sketch quickly (write these structures, fill logic after):**

```java
// Step 7 — ParkingFloor skeleton
public class ParkingFloor {
    private final int floorNumber;
    private final List<ParkingSpot> spots;
    private final AtomicInteger availableCount;

    // synchronized: scan for first available spot of matching type
    public synchronized ParkingSpot findAvailableSpot(SpotType type) { ... }
}
```

```java
// Step 8 — ParkingLot skeleton
// thread-safe: synchronized on parkVehicle; ConcurrentHashMap for tickets
public class ParkingLot {
    private final List<ParkingFloor> floors;
    private final FeeStrategy feeStrategy;
    private final Map<String, ParkingTicket> activeTickets; // ConcurrentHashMap

    // synchronized — check-then-assign must be atomic
    public synchronized ParkingTicket parkVehicle(Vehicle vehicle, SpotType type) { ... }

    // no full lock needed — remove from map, vacate spot
    public ParkingTicket unparkVehicle(String ticketId) { ... }
}
```

---

## 🔁 Concurrency — Making It Thread-Safe

**Shared mutable state — the three things that get corrupted without locks:**

| Field | Problem without lock | Fix |
|---|---|---|
| `ParkingSpot.parkedVehicle` | Thread A and B both read `null`, both call `assignVehicle()` — two cars, one spot | `synchronized` on `assignVehicle()` at the spot level |
| `ParkingFloor.spots` list scan | Check-then-act race: two threads both find the same spot available, both assign it | `synchronized` on `ParkingLot.parkVehicle()` — one lock guards the whole find-and-assign |
| `activeTickets` map | Two threads could write the same key simultaneously | `ConcurrentHashMap` — thread-safe by design |

**Strategy used: coarse lock on entry, lock-free map for tickets**

```java
// thread-safe: synchronized on parkVehicle; ConcurrentHashMap for activeTickets
public class ParkingLot {

    private final Map<String, ParkingTicket> activeTickets = new ConcurrentHashMap<>();

    // synchronized prevents the InMobi scenario:
    // A reads available=true, B reads available=true → both assign → one spot, two cars
    public synchronized ParkingTicket parkVehicle(Vehicle vehicle, SpotType spotType) {
        for (ParkingFloor floor : floors) {
            ParkingSpot spot = floor.findAvailableSpot(spotType);
            if (spot != null) {
                spot.assignVehicle(vehicle);       // spot-level guard too
                floor.decrementAvailable();
                String ticketId = UUID.randomUUID().toString();
                ParkingTicket ticket = new ParkingTicket(ticketId, vehicle, spot, floor.getFloorNumber());
                activeTickets.put(ticketId, ticket);
                return ticket;
            }
        }
        throw new IllegalStateException("No spot available for type: " + spotType);
    }
}
```

**Trade-off:** `synchronized` on the whole `parkVehicle()` is a global lock — only one car parks at a time. For a single-building lot this is fine. For a distributed system with thousands of concurrent entries, move to **per-floor locking** (`synchronized` on each `ParkingFloor` instance) to allow floors to process entries in parallel.

---

## 📐 "What Would You Do Differently?"

> *"If I had more time, I'd add per-floor locking instead of a single lock on `ParkingLot` — it's a contention bottleneck on a busy multi-floor lot. I'd also add a `ParkingDisplayBoard` that uses the **Observer pattern** — it subscribes to spot events and updates the availability count reactively, rather than requiring the caller to poll."*

> *"I'd also add a `Validator` to enforce business rules separately — like handicapped spots require a permit, EV spots require an EV vehicle — instead of baking that into the spot class."*

---

## 🔬 Interview Q&As

### Q: "Walk me through how two cars trying to park at the same time are handled."
> Without a lock, Thread A and B both call `findAvailableSpot()`, both find the same spot available, and both call `assignVehicle()` — classic check-then-act race condition. The fix is synchronizing `parkVehicle()` on the `ParkingLot` instance. Only one thread executes the find-and-assign at a time. The synchronized block is the smallest scope that covers the check-then-act pair.

### Q: "Why Strategy for fee instead of if-else in ParkingLot?"
> Because the fee algorithm varies independently of the parking lot. `HourlyFeeStrategy`, `FlatRateFeeStrategy`, `FreeStrategy` (first 15 minutes) are all valid at different times. With Strategy, I inject the right one at construction time — the lot never knows which runs. Adding a new pricing model = one new class, zero changes to `ParkingLot`. With if-else, every new pricing type means editing the lot — violating open-closed principle.

### Q: "Why Factory for spot creation instead of constructing spots directly?"
> Because `ParkingFloor` shouldn't need to know whether it's creating `new CompactSpot()` or `new EVSpot()`. Factory centralises that decision. When I add a `MotorcycleSpot`, I update the factory once — the floor, the lot, and all callers are untouched.

### Q: "How would you add EV charging to EV spots without breaking existing spots?"
> `EVSpot` implements `ParkingSpot` — it already has `assignVehicle()` and `removeVehicle()`. I'd add a `ChargingCapable` interface with `startCharging(Vehicle)` and `stopCharging()`. `EVSpot` implements both. `ParkingLot` checks `instanceof ChargingCapable` when assigning to an EV spot and starts charging. Existing spots are untouched — they don't implement `ChargingCapable`.

### Q: "How would you make the available-spot count always accurate without scanning every spot?"
> Each `ParkingFloor` holds an `AtomicInteger availableCount`. On assignment, decrement it; on vacate, increment it. `ParkingLot.getAvailableCount(type)` sums across floors in O(floors) — no spot scan. The atomic integer is increment/decrement-safe without extra synchronization.

---

## 🧾 TL;DR — 30-Second Pitch

> *"I've got a `ParkingLot` that delegates to `ParkingFloor`s. Each floor holds a list of `ParkingSpot`s — CompactSpot, LargeSpot, HandicappedSpot, EVSpot — all behind an interface. A `ParkingSpotFactory` creates them. Fee calculation is plugged in as a `FeeStrategy`. `parkVehicle()` is synchronized to prevent the check-then-act race where two threads both see a spot as available. Active tickets live in a `ConcurrentHashMap`. Adding a new spot type or pricing model is one new class — nothing else changes."*

---

## 🔗 Patterns Used

- **Factory** — `ParkingSpotFactory` creates `ParkingSpot` implementations. See **`LLD/DesignPatterns/01-factory-strategy.md`** (Factory section).
- **Strategy** — `FeeStrategy` swaps the fee algorithm at runtime. See **`LLD/DesignPatterns/01-factory-strategy.md`** (Strategy section).
