# InMobi LLD — Battle-Ready Guide (Temp File)

> **Goal:** Walk into LLD round confident. This file has:
> 1. **Pattern Decision Framework** — see a requirement, know the pattern instantly
> 2. **Parking Lot** — full discussion walkthrough (no code, just how to talk)
> 3. **Notification System** — full coded example (different domain, same patterns)
> 4. **The Minimal Moves That Impress**

---

# PART 1 — Pattern Decision Framework

## 🎨 Visual — "See the Requirement, Pick the Pattern"

```
┌─────────────────────────────────────────────────────────┐
│              PATTERN DECISION FLOWCHART                  │
│                                                         │
│  Read the requirement. Ask yourself:                    │
│                                                         │
│  ┌─────────────────────────────────────────┐            │
│  │ "Can this BEHAVIOR vary or be swapped?" │            │
│  │  (allocation, pricing, sorting, routing)│            │
│  └──────────────────┬──────────────────────┘            │
│                     │ YES                               │
│                     ▼                                   │
│              ★ STRATEGY PATTERN ★                       │
│              interface + multiple impls                  │
│              inject via constructor                      │
│                                                         │
│  ┌─────────────────────────────────────────┐            │
│  │ "Do I need to CREATE different objects  │            │
│  │  based on a type/config?"              │            │
│  │  (vehicle types, notification channels) │            │
│  └──────────────────┬──────────────────────┘            │
│                     │ YES                               │
│                     ▼                                   │
│              ★ FACTORY PATTERN ★                        │
│              static create(type) method                  │
│              caller doesn't know concrete class          │
│                                                         │
│  ┌─────────────────────────────────────────┐            │
│  │ "When X happens, should MULTIPLE things │            │
│  │  react independently?"                  │            │
│  │  (on exit: bill + log + notify + stats) │            │
│  └──────────────────┬──────────────────────┘            │
│                     │ YES                               │
│                     ▼                                   │
│              ★ OBSERVER PATTERN ★                       │
│              List<Observer>, loop and notify             │
│              publisher doesn't know subscribers          │
│                                                         │
│  ┌─────────────────────────────────────────┐            │
│  │ "Must a request pass through a CHAIN    │            │
│  │  of independent checks/filters?"        │            │
│  │  (fraud → quality → eligibility)        │            │
│  └──────────────────┬──────────────────────┘            │
│                     │ YES                               │
│                     ▼                                   │
│              ★ CHAIN OF RESPONSIBILITY ★                │
│              each handler has a `next`                   │
│              any handler can short-circuit               │
│                                                         │
│  ┌─────────────────────────────────────────┐            │
│  │ "Is there ONE shared expensive resource │            │
│  │  that everyone reads?"                  │            │
│  │  (config, logger, connection pool)      │            │
│  └──────────────────┬──────────────────────┘            │
│                     │ YES                               │
│                     ▼                                   │
│              ★ SINGLETON ★                              │
│              enum or double-checked locking              │
│              use sparingly                               │
│                                                         │
│  KEY INVARIANT:                                         │
│    Most LLD problems use Strategy + Factory + Observer. │
│    If you nail these 3, you clear the bar.              │
└─────────────────────────────────────────────────────────┘
```

## Quick-Fire: Requirement → Pattern

| When you hear... | Pattern | Why |
|---|---|---|
| "different pricing models" | **Strategy** | behavior varies |
| "different allocation algorithms" | **Strategy** | behavior varies |
| "different notification channels" | **Strategy** | behavior varies |
| "create vehicle by type" | **Factory** | creation varies by type |
| "create notification by channel" | **Factory** | creation varies by type |
| "on event X, do billing + logging + alerting" | **Observer** | multiple reactions to one event |
| "fraud check → quality check → eligibility" | **Chain of Resp** | sequential independent filters |
| "global config / logger" | **Singleton** | shared read-mostly resource |

---

# PART 2 — Parking Lot (Discussion Walkthrough)

> **This is HOW you talk through it. No code needed — they want to see your THINKING.**

## Step 1: Restate + Clarify (Minute 0–2)

Say this:

> *"So we're designing a parking lot management system. Before I start, three quick questions:*
> 1. *Scale — single lot or multi-lot chain?*
> 2. *Priority features — entry/exit, ticketing, pricing? Or also valet, EV charging?*
> 3. *Extensibility — what might change later? New vehicle types? New pricing?"*

## Step 2: List Entities (Minute 2–4)

> *"Let me identify the nouns — these become my classes:"*

```
ENTITIES (draw boxes on excalidraw):

┌──────────────┐     ┌───────────┐     ┌──────────────┐
│  ParkingLot  │────▶│   Floor   │────▶│ ParkingSpot  │
│  - name      │ has │ - number  │ has │ - id         │
│  - floors    │many │ - spots   │many │ - type (enum)│
└──────────────┘     └───────────┘     │ - occupied   │
                                       │ - vehicle    │
                                       └──────────────┘

┌──────────────┐     ┌──────────────┐
│   Vehicle    │     │    Ticket    │
│  - plate     │     │ - id         │
│  - type(enum)│     │ - vehicle    │
│              │     │ - spot       │
└──────────────┘     │ - entryTime  │
  ▲ subtypes:        │ - exitTime?  │
  Car, Bike,         │ - fee?       │
  Truck, EV          └──────────────┘
```

Say: *"Vehicle has subtypes — Car, Bike, Truck, EV. ParkingSpot has a type enum matching vehicle types. Ticket ties a vehicle to a spot with timestamps."*

## Step 3: Identify Behaviors + Pick Patterns (Minute 4–8)

> *"Now the verbs — these become my services. And I'll look for where behavior varies:"*

Say this while drawing:

> *"Spot allocation — there are multiple ways to allocate: nearest available, first-come, random. That behavior varies → I'll use* ***Strategy pattern.***"

> *"Fee calculation — hourly flat, vehicle-type-based, surge pricing. Again, behavior varies →* ***Strategy.***"

> *"Vehicle creation — based on type, I create Car or Bike or Truck. Caller shouldn't know the concrete class →* ***Factory.***"

> *"On vehicle exit — multiple things should happen independently: calculate fee, log event, send notification, update analytics. One event, many reactions →* ***Observer.***"

```
SERVICES + PATTERNS (draw on excalidraw):

┌─────────────────────┐
│   ParkingManager     │ ◄── orchestrator
│  (uses all below)    │
└──┬──────┬──────┬─────┘
   │      │      │
   ▼      ▼      ▼
┌──────┐┌──────┐┌──────────────┐
│Spot  ││Fee   ││ExitObserver  │
│Alloc ││Calc  ││(interface)   │
│Strat ││Strat ││              │
│(intf)││(intf)││ - BillingObs │
└──┬───┘└──┬───┘│ - LoggingObs │
   │       │    │ - NotifyObs  │
   ▼       ▼    └──────────────┘
Nearest  Hourly
First    Surge       ┌──────────────┐
Random   ByType      │VehicleFactory│
                     │ create(type) │
                     └──────────────┘
```

**This is the money slide.** Once you draw this, they see: Strategy, Factory, Observer — all three. That's the SDE2 bar already.

## Step 4: Thread-Safety (30 seconds)

> *"Quick concurrency note: ParkingSpot.occupied flip should be atomic — I'd use AtomicBoolean or synchronized on Floor level. TicketService.issueTicket is the critical section. ConcurrentHashMap for in-memory ticket store."*

## Step 5: Edge Cases (30 seconds)

> *"Edge cases: lot full → Optional.empty from allocator + LotFullException + observer fires alert. Lost ticket → policy hook. Vehicle exits without entering → audit log."*

## Step 6: Extensibility — The Killer Move (1 min)

> *"Adding EV charging? New ElectricSpot extends ParkingSpot + EVChargingObserver. No edits to ParkingManager."*
> *"Surge pricing during peak? New SurgePricingStrategy, inject via config. Zero change to existing strategies."*

**That one sentence — "no edits to ParkingManager" — is OCP in action. Say it explicitly.**

---

# PART 2B — Parking Lot (Fully Coded — Same Patterns, Different Domain)

> Compare this side-by-side with Part 3 (Notification System). The SHAPE is identical.

## 🎨 Visual — Pattern Mapping: Parking Lot vs Notification

```
┌──────────────────────────────────────────────────────────────────┐
│         SAME PATTERN, DIFFERENT DOMAIN                           │
│                                                                  │
│  Pattern          Parking Lot              Notification          │
│  ─────────────    ──────────────────       ─────────────────     │
│  STRATEGY         SpotAllocationStrategy   RoutingStrategy       │
│                   FeeCalculationStrategy                         │
│                   ↳ varies HOW             ↳ varies HOW          │
│                                                                  │
│  FACTORY          VehicleFactory           ChannelFactory         │
│                   ↳ create by type         ↳ create by type      │
│                                                                  │
│  OBSERVER         ExitObserver             SendObserver           │
│                   ↳ bill + log + notify    ↳ log + metrics + aud │
│                                                                  │
│  ORCHESTRATOR     ParkingManager           NotificationService   │
│                   ↳ no if/else             ↳ no if/else          │
│                                                                  │
│  KEY INSIGHT: If you can code ONE, you can code ANY LLD problem. │
│  The skeleton is ALWAYS: interface → concrete impls → inject     │
│  into orchestrator → orchestrator has ZERO if/else.              │
└──────────────────────────────────────────────────────────────────┘
```

## Entities (Enums + Core Classes)

```java
enum VehicleType {
    BIKE, CAR, TRUCK, EV
}

enum SpotType {
    BIKE, CAR, TRUCK, EV
}

// ── Vehicle hierarchy (Factory will create these) ──
abstract class Vehicle {
    private final String plateNumber;
    private final VehicleType type;

    Vehicle(String plateNumber, VehicleType type) {
        this.plateNumber = plateNumber;
        this.type = type;
    }

    String getPlateNumber() { return plateNumber; }
    VehicleType getType() { return type; }
}

class Car extends Vehicle {
    Car(String plate) { super(plate, VehicleType.CAR); }
}

class Bike extends Vehicle {
    Bike(String plate) { super(plate, VehicleType.BIKE); }
}

class Truck extends Vehicle {
    Truck(String plate) { super(plate, VehicleType.TRUCK); }
}

class ElectricVehicle extends Vehicle {
    ElectricVehicle(String plate) { super(plate, VehicleType.EV); }
}
```

```java
// ── ParkingSpot ──
class ParkingSpot {
    private final int id;
    private final SpotType type;
    private final int floor;
    private volatile boolean occupied;
    private Vehicle currentVehicle;

    ParkingSpot(int id, SpotType type, int floor) {
        this.id = id;
        this.type = type;
        this.floor = floor;
        this.occupied = false;
    }

    int getId() { return id; }
    SpotType getType() { return type; }
    int getFloor() { return floor; }
    boolean isOccupied() { return occupied; }
    Vehicle getCurrentVehicle() { return currentVehicle; }

    synchronized void park(Vehicle vehicle) {
        this.currentVehicle = vehicle;
        this.occupied = true;
    }

    synchronized void free() {
        this.currentVehicle = null;
        this.occupied = false;
    }
}
```

```java
// ── Ticket ──
class Ticket {
    private final String id;
    private final Vehicle vehicle;
    private final ParkingSpot spot;
    private final LocalDateTime entryTime;
    private LocalDateTime exitTime;
    private double fee;

    Ticket(String id, Vehicle vehicle, ParkingSpot spot) {
        this.id = id;
        this.vehicle = vehicle;
        this.spot = spot;
        this.entryTime = LocalDateTime.now();
    }

    String getId() { return id; }
    Vehicle getVehicle() { return vehicle; }
    ParkingSpot getSpot() { return spot; }
    LocalDateTime getEntryTime() { return entryTime; }
    LocalDateTime getExitTime() { return exitTime; }
    double getFee() { return fee; }

    void closeTicket(double fee) {
        this.exitTime = LocalDateTime.now();
        this.fee = fee;
    }
}
```

## Pattern 1 — Factory (create vehicle by type)

```java
// Compare with: ChannelFactory.create(ChannelType) in Notification
//
// SAME SHAPE:
//   static method takes a type enum → returns the right subclass
//   caller doesn't know if it's Car or Truck

class VehicleFactory {
    static Vehicle create(VehicleType type, String plate) {
        return switch (type) {
            case CAR   -> new Car(plate);
            case BIKE  -> new Bike(plate);
            case TRUCK -> new Truck(plate);
            case EV    -> new ElectricVehicle(plate);
        };
    }
}
```

## Pattern 2 — Strategy (spot allocation behavior varies)

```java
// Compare with: RoutingStrategy in Notification
//
// SAME SHAPE:
//   interface with one method
//   multiple concrete implementations
//   injected into orchestrator via constructor

// ── Strategy interface ──
interface SpotAllocationStrategy {
    Optional<ParkingSpot> allocate(List<ParkingSpot> spots, Vehicle vehicle);
}

// ── Concrete: First available spot of matching type ──
class FirstAvailableStrategy implements SpotAllocationStrategy {
    @Override
    public Optional<ParkingSpot> allocate(List<ParkingSpot> spots,
                                           Vehicle vehicle) {
        SpotType needed = mapVehicleToSpot(vehicle.getType());
        return spots.stream()
                .filter(s -> !s.isOccupied())
                .filter(s -> s.getType() == needed)
                .findFirst();
    }

    private SpotType mapVehicleToSpot(VehicleType vt) {
        return switch (vt) {
            case BIKE  -> SpotType.BIKE;
            case CAR   -> SpotType.CAR;
            case TRUCK -> SpotType.TRUCK;
            case EV    -> SpotType.EV;
        };
    }
}

// ── Concrete: Nearest to entrance (lowest floor, lowest id) ──
class NearestToEntranceStrategy implements SpotAllocationStrategy {
    @Override
    public Optional<ParkingSpot> allocate(List<ParkingSpot> spots,
                                           Vehicle vehicle) {
        SpotType needed = mapVehicleToSpot(vehicle.getType());
        return spots.stream()
                .filter(s -> !s.isOccupied())
                .filter(s -> s.getType() == needed)
                .min(Comparator.comparingInt(ParkingSpot::getFloor)
                        .thenComparingInt(ParkingSpot::getId));
    }

    private SpotType mapVehicleToSpot(VehicleType vt) {
        return switch (vt) {
            case BIKE  -> SpotType.BIKE;
            case CAR   -> SpotType.CAR;
            case TRUCK -> SpotType.TRUCK;
            case EV    -> SpotType.EV;
        };
    }
}
```

## Pattern 2B — Strategy (fee calculation behavior varies)

```java
// SAME pattern, second usage in SAME system
// This shows the interviewer you can apply Strategy MULTIPLE TIMES

interface FeeCalculationStrategy {
    double calculate(Ticket ticket);
}

// ── Flat hourly rate ──
class HourlyFlatFee implements FeeCalculationStrategy {
    private final double ratePerHour;

    HourlyFlatFee(double ratePerHour) {
        this.ratePerHour = ratePerHour;
    }

    @Override
    public double calculate(Ticket ticket) {
        long hours = ChronoUnit.HOURS.between(
                ticket.getEntryTime(), ticket.getExitTime());
        if (hours == 0) { hours = 1; }  // minimum 1 hour
        return hours * ratePerHour;
    }
}

// ── Rate depends on vehicle type ──
class VehicleTypeFee implements FeeCalculationStrategy {
    private final Map<VehicleType, Double> rates;

    VehicleTypeFee(Map<VehicleType, Double> rates) {
        this.rates = rates;
    }

    @Override
    public double calculate(Ticket ticket) {
        long hours = ChronoUnit.HOURS.between(
                ticket.getEntryTime(), ticket.getExitTime());
        if (hours == 0) { hours = 1; }
        double rate = rates.getOrDefault(
                ticket.getVehicle().getType(), 50.0);
        return hours * rate;
    }
}
```

## Pattern 3 — Observer (on exit: bill + log + notify)

```java
// Compare with: SendObserver in Notification
//
// SAME SHAPE:
//   interface with one method (onExit vs onSend)
//   multiple independent implementations
//   orchestrator fires all, doesn't know who's listening

interface ExitObserver {
    void onExit(Ticket ticket);
}

class BillingObserver implements ExitObserver {
    @Override
    public void onExit(Ticket ticket) {
        System.out.println("BILLING: Charged Rs." + ticket.getFee()
                + " to vehicle " + ticket.getVehicle().getPlateNumber());
    }
}

class LoggingObserver implements ExitObserver {
    @Override
    public void onExit(Ticket ticket) {
        System.out.println("LOG: Vehicle " + ticket.getVehicle().getPlateNumber()
                + " exited spot " + ticket.getSpot().getId()
                + " | Fee: " + ticket.getFee());
    }
}

class NotificationObserver implements ExitObserver {
    @Override
    public void onExit(Ticket ticket) {
        System.out.println("NOTIFY: SMS sent to owner of "
                + ticket.getVehicle().getPlateNumber()
                + " — receipt Rs." + ticket.getFee());
    }
}
```

## The Orchestrator — ParkingManager (brings it all together)

```java
// Compare with: NotificationService in Part 3
//
// SAME SHAPE:
//   Constructor takes Strategy + Observers (Dependency Injection)
//   Main methods have NO if/else — delegates to Strategy + Observer
//   This is the class the interviewer judges hardest

class ParkingManager {
    private final List<ParkingSpot> spots;
    private final SpotAllocationStrategy allocationStrategy;
    private final FeeCalculationStrategy feeStrategy;
    private final List<ExitObserver> exitObservers;
    private final ConcurrentHashMap<String, Ticket> activeTickets;
    private int ticketCounter;

    ParkingManager(SpotAllocationStrategy allocationStrategy,
                   FeeCalculationStrategy feeStrategy,
                   List<ExitObserver> exitObservers,
                   List<ParkingSpot> spots) {
        this.allocationStrategy = allocationStrategy;
        this.feeStrategy = feeStrategy;
        this.exitObservers = exitObservers;
        this.spots = spots;
        this.activeTickets = new ConcurrentHashMap<>();
        this.ticketCounter = 0;
    }

    // ── ENTRY: allocate spot + issue ticket ──
    // NO if/else — Strategy decides which spot
    Optional<Ticket> entry(Vehicle vehicle) {
        Optional<ParkingSpot> spot = allocationStrategy
                .allocate(spots, vehicle);

        if (spot.isEmpty()) {
            System.out.println("LOT FULL — no spot for "
                    + vehicle.getType());
            return Optional.empty();
        }

        spot.get().park(vehicle);
        String ticketId = "TKT-" + (++ticketCounter);
        Ticket ticket = new Ticket(ticketId, vehicle, spot.get());
        activeTickets.put(ticketId, ticket);

        System.out.println("ENTRY: " + vehicle.getPlateNumber()
                + " → Spot " + spot.get().getId()
                + " | Ticket: " + ticketId);
        return Optional.of(ticket);
    }

    // ── EXIT: calculate fee + free spot + notify observers ──
    // NO if/else — Strategy calculates, Observer reacts
    Optional<Ticket> exit(String ticketId) {
        Ticket ticket = activeTickets.remove(ticketId);
        if (ticket == null) {
            System.out.println("ERROR: Ticket " + ticketId + " not found");
            return Optional.empty();
        }

        // Strategy calculates fee
        double fee = feeStrategy.calculate(ticket);
        ticket.closeTicket(fee);

        // Free the spot
        ticket.getSpot().free();

        // Observer: notify all listeners
        for (ExitObserver observer : exitObservers) {
            observer.onExit(ticket);
        }

        return Optional.of(ticket);
    }
}
```

**Say this in the interview:**
> *"Look at entry() and exit() — no if/else anywhere. The allocation strategy decides WHICH spot. The fee strategy decides HOW MUCH. The observers react to the exit event. ParkingManager just orchestrates. If I change the pricing model tomorrow, ParkingManager doesn't change. That's SRP and OCP."*

## Main / Driver

```java
public class Main {
    public static void main(String[] args) {
        // ── Create spots (2 floors, mixed types) ──
        List<ParkingSpot> spots = List.of(
            new ParkingSpot(1, SpotType.BIKE, 1),
            new ParkingSpot(2, SpotType.CAR, 1),
            new ParkingSpot(3, SpotType.CAR, 1),
            new ParkingSpot(4, SpotType.TRUCK, 1),
            new ParkingSpot(5, SpotType.EV, 2),
            new ParkingSpot(6, SpotType.CAR, 2)
        );

        // ── Create observers ──
        List<ExitObserver> observers = List.of(
            new BillingObserver(),
            new LoggingObserver(),
            new NotificationObserver()
        );

        // ── Inject Strategy + Observers into manager ──
        ParkingManager manager = new ParkingManager(
            new NearestToEntranceStrategy(),    // Strategy 1: allocation
            new VehicleTypeFee(Map.of(          // Strategy 2: pricing
                VehicleType.BIKE, 20.0,
                VehicleType.CAR, 50.0,
                VehicleType.TRUCK, 100.0,
                VehicleType.EV, 60.0
            )),
            observers,
            new ArrayList<>(spots)
        );

        // ── Test: Entry ──
        Vehicle car1 = VehicleFactory.create(VehicleType.CAR, "KA-01-1234");
        Vehicle bike1 = VehicleFactory.create(VehicleType.BIKE, "KA-01-5678");
        Vehicle truck1 = VehicleFactory.create(VehicleType.TRUCK, "KA-02-9999");

        Optional<Ticket> t1 = manager.entry(car1);
        Optional<Ticket> t2 = manager.entry(bike1);
        Optional<Ticket> t3 = manager.entry(truck1);

        // ── Test: Exit ──
        // (In real code, time passes. Here fee = 1 hour minimum)
        System.out.println("\n=== EXITS ===");
        t1.ifPresent(t -> manager.exit(t.getId()));
        t2.ifPresent(t -> manager.exit(t.getId()));
    }
}
```

**Expected output:**

```
ENTRY: KA-01-1234 → Spot 2 | Ticket: TKT-1
ENTRY: KA-01-5678 → Spot 1 | Ticket: TKT-2
ENTRY: KA-02-9999 → Spot 4 | Ticket: TKT-3

=== EXITS ===
BILLING: Charged Rs.50.0 to vehicle KA-01-1234
LOG: Vehicle KA-01-1234 exited spot 2 | Fee: 50.0
NOTIFY: SMS sent to owner of KA-01-1234 — receipt Rs.50.0
BILLING: Charged Rs.20.0 to vehicle KA-01-5678
LOG: Vehicle KA-01-5678 exited spot 1 | Fee: 20.0
NOTIFY: SMS sent to owner of KA-01-5678 — receipt Rs.20.0
```

## Side-by-Side Comparison — Orchestrator Methods

```
┌──────────────────────────────────┬──────────────────────────────────┐
│     ParkingManager.exit()        │   NotificationService.send()     │
├──────────────────────────────────┼──────────────────────────────────┤
│ 1. Strategy calculates fee       │ 1. Strategy selects channels     │
│    fee = feeStrategy.calculate() │    selected = routingStrategy    │
│                                  │        .selectChannels()         │
│                                  │                                  │
│ 2. Core action                   │ 2. Core action                   │
│    ticket.closeTicket(fee)       │    channel.send(notification)    │
│    spot.free()                   │                                  │
│                                  │                                  │
│ 3. Observers react               │ 3. Observers react               │
│    for (ExitObserver o :         │    for (SendObserver o :         │
│         exitObservers)           │         observers)               │
│      o.onExit(ticket)            │      o.onSend(notif, ch, ok)    │
│                                  │                                  │
│ NO if/else ✓                     │ NO if/else ✓                     │
│ Strategy decides ✓               │ Strategy decides ✓               │
│ Observer reacts ✓                │ Observer reacts ✓                │
└──────────────────────────────────┴──────────────────────────────────┘

KEY TAKEAWAY:
  Every LLD orchestrator follows this SAME 3-step shape:
  1. Strategy decides → 2. Core action → 3. Observers react
  Learn the SHAPE, not the domain.
```

---

# PART 3 — Notification System (Fully Coded)

> **Entirely different domain, same patterns. This is your "I can code LLD" proof.**
>
> **Problem:** Design a notification system that sends alerts through multiple channels (Email, SMS, Push). Different notification types use different channels. Support priority routing and adding new channels without changing existing code.

## 🎨 Visual — Class Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                  NOTIFICATION SYSTEM                         │
│                                                             │
│                  ┌───────────────────┐                      │
│                  │ NotificationService│ ◄── orchestrator    │
│                  │                   │                      │
│                  │ - factory         │                      │
│                  │ - strategy        │                      │
│                  │ - observers       │                      │
│                  └─────────┬─────────┘                      │
│                            │ uses                           │
│           ┌────────────────┼────────────────┐               │
│           │                │                │               │
│           ▼                ▼                ▼               │
│  ┌─────────────┐  ┌──────────────┐  ┌──────────────┐      │
│  │ Channel     │  │ Routing      │  │ Send         │      │
│  │ Factory     │  │ Strategy     │  │ Observer     │      │
│  │             │  │ (interface)  │  │ (interface)  │      │
│  │ create(type)│  │              │  │              │      │
│  │  → Email    │  │ → AllChannels│  │ → LogObs     │      │
│  │  → SMS      │  │ → PriorityFn│  │ → MetricsObs │      │
│  │  → Push     │  │ → SingleBest│  │ → AuditObs   │      │
│  └─────────────┘  └──────────────┘  └──────────────┘      │
│                                                             │
│  PATTERN MAP:                                               │
│    "Create channel by type"     → Factory                   │
│    "Route to channels varies"   → Strategy                  │
│    "On send: log + metrics"     → Observer                  │
│                                                             │
│  KEY INVARIANT:                                             │
│    Adding a new channel (WhatsApp) = 1 new class +          │
│    1 line in factory. ZERO changes to existing code.        │
└─────────────────────────────────────────────────────────────┘
```

## The Code (complete, runnable)

### Entities

```java
enum ChannelType {
    EMAIL, SMS, PUSH
}

enum Priority {
    LOW, MEDIUM, HIGH, CRITICAL
}

class Notification {
    private final String recipient;
    private final String title;
    private final String body;
    private final Priority priority;
    private final Set<ChannelType> channels;

    Notification(String recipient, String title, String body,
                 Priority priority, Set<ChannelType> channels) {
        this.recipient = recipient;
        this.title = title;
        this.body = body;
        this.priority = priority;
        this.channels = channels;
    }

    // getters
    String getRecipient() { return recipient; }
    String getTitle() { return title; }
    String getBody() { return body; }
    Priority getPriority() { return priority; }
    Set<ChannelType> getChannels() { return channels; }
}
```

### Pattern 1 — Strategy (routing behavior varies)

```java
// Step 1: Define the interface — "what" varies
interface RoutingStrategy {
    List<Channel> selectChannels(Notification notification,
                                 Map<ChannelType, Channel> available);
}

// Step 2: Implement concrete strategies — "how" it varies

// Send to ALL channels the notification requests
class AllChannelsStrategy implements RoutingStrategy {
    @Override
    public List<Channel> selectChannels(Notification notification,
                                         Map<ChannelType, Channel> available) {
        List<Channel> result = new ArrayList<>();
        for (ChannelType type : notification.getChannels()) {
            if (available.containsKey(type)) {
                result.add(available.get(type));
            }
        }
        return result;
    }
}

// CRITICAL priority → send to ALL available channels (not just requested)
class PriorityEscalationStrategy implements RoutingStrategy {
    @Override
    public List<Channel> selectChannels(Notification notification,
                                         Map<ChannelType, Channel> available) {
        if (notification.getPriority() == Priority.CRITICAL) {
            // Critical = blast to every channel
            return new ArrayList<>(available.values());
        }
        // Non-critical = just use requested channels
        return new AllChannelsStrategy()
                   .selectChannels(notification, available);
    }
}
```

**When to explain Strategy in interview:**
> *"Routing logic varies — today it's 'send to all requested channels', tomorrow it's 'escalate critical to all channels', next month it's 'A/B test channels'. I extract the HOW behind an interface. New routing = new class, zero changes to NotificationService. That's OCP."*

### Pattern 2 — Factory (create channel by type)

```java
// Step 1: Define the product interface — what all channels share
interface Channel {
    void send(Notification notification);
    ChannelType getType();
}

// Step 2: Concrete products
class EmailChannel implements Channel {
    @Override
    public void send(Notification notification) {
        System.out.println("EMAIL → " + notification.getRecipient()
                + ": " + notification.getTitle());
        // real impl: SMTP client, template engine, etc.
    }

    @Override
    public ChannelType getType() { return ChannelType.EMAIL; }
}

class SmsChannel implements Channel {
    @Override
    public void send(Notification notification) {
        System.out.println("SMS → " + notification.getRecipient()
                + ": " + notification.getBody());
    }

    @Override
    public ChannelType getType() { return ChannelType.SMS; }
}

class PushChannel implements Channel {
    @Override
    public void send(Notification notification) {
        System.out.println("PUSH → " + notification.getRecipient()
                + ": " + notification.getTitle());
    }

    @Override
    public ChannelType getType() { return ChannelType.PUSH; }
}

// Step 3: Factory — caller says WHAT, factory decides HOW to create
class ChannelFactory {
    static Channel create(ChannelType type) {
        return switch (type) {
            case EMAIL -> new EmailChannel();
            case SMS   -> new SmsChannel();
            case PUSH  -> new PushChannel();
        };
    }
}
```

**When to explain Factory in interview:**
> *"The caller shouldn't know whether it's creating EmailChannel or SmsChannel. Adding WhatsApp tomorrow? I add one class + one case in the factory. No other file changes."*

### Pattern 3 — Observer (on send: log + metrics + audit)

```java
// Step 1: Define observer interface — what "reacting" means
interface SendObserver {
    void onSend(Notification notification, Channel channel, boolean success);
}

// Step 2: Concrete observers — each does ONE independent thing (SRP)
class LoggingObserver implements SendObserver {
    @Override
    public void onSend(Notification notification, Channel channel,
                       boolean success) {
        System.out.println("LOG: " + channel.getType() + " → "
                + notification.getRecipient()
                + " [" + (success ? "OK" : "FAIL") + "]");
    }
}

class MetricsObserver implements SendObserver {
    @Override
    public void onSend(Notification notification, Channel channel,
                       boolean success) {
        // Real impl: increment Prometheus counter
        System.out.println("METRIC: notifications_sent_total{"
                + "channel=" + channel.getType()
                + ",status=" + (success ? "ok" : "fail") + "} +1");
    }
}

class AuditObserver implements SendObserver {
    @Override
    public void onSend(Notification notification, Channel channel,
                       boolean success) {
        // Real impl: write to audit DB
        System.out.println("AUDIT: " + notification.getRecipient()
                + " via " + channel.getType() + " at " + System.currentTimeMillis());
    }
}
```

**When to explain Observer in interview:**
> *"When a notification is sent, multiple things should react independently — logging, metrics, audit trail. I don't want NotificationService to know about any of them. Observer pattern: register listeners, fire on event. Adding a new reaction (e.g., Slack alert) = one new Observer class. NotificationService untouched."*

### The Orchestrator (brings it all together)

```java
class NotificationService {
    private final Map<ChannelType, Channel> channels;
    private final RoutingStrategy routingStrategy;
    private final List<SendObserver> observers;

    // Constructor injection — Dependency Inversion (the D in SOLID)
    NotificationService(RoutingStrategy routingStrategy,
                        List<SendObserver> observers) {
        this.routingStrategy = routingStrategy;
        this.observers = observers;
        this.channels = new ConcurrentHashMap<>();
    }

    // Register channels (created via Factory)
    void registerChannel(Channel channel) {
        channels.put(channel.getType(), channel);
    }

    // The main method — clean, short, no if/else
    void send(Notification notification) {
        // Strategy decides WHICH channels
        List<Channel> selected = routingStrategy
                .selectChannels(notification, channels);

        for (Channel channel : selected) {
            boolean success = false;
            try {
                channel.send(notification);
                success = true;
            } catch (Exception e) {
                System.err.println("Failed: " + channel.getType()
                        + " — " + e.getMessage());
            }

            // Observer: notify all listeners
            for (SendObserver observer : observers) {
                observer.onSend(notification, channel, success);
            }
        }
    }
}
```

**Point this out in interview:**
> *"Look at the send() method — no if/else, no switch, no channel-specific code. Strategy picks channels, each Channel sends, Observers react. That's SRP and OCP working together."*

### Main / Driver (ALWAYS include this — InMobi penalises skipping it)

```java
public class Main {
    public static void main(String[] args) {
        // Create channels via Factory
        Channel email = ChannelFactory.create(ChannelType.EMAIL);
        Channel sms = ChannelFactory.create(ChannelType.SMS);
        Channel push = ChannelFactory.create(ChannelType.PUSH);

        // Create observers
        List<SendObserver> observers = List.of(
            new LoggingObserver(),
            new MetricsObserver(),
            new AuditObserver()
        );

        // Inject Strategy + Observers into service
        NotificationService service = new NotificationService(
            new PriorityEscalationStrategy(),
            observers
        );

        // Register channels
        service.registerChannel(email);
        service.registerChannel(sms);
        service.registerChannel(push);

        // Test 1: Normal notification (only requested channels)
        Notification normal = new Notification(
            "kapil@example.com", "Order Shipped",
            "Your order #123 has shipped",
            Priority.MEDIUM,
            Set.of(ChannelType.EMAIL, ChannelType.SMS)
        );
        System.out.println("=== NORMAL NOTIFICATION ===");
        service.send(normal);

        // Test 2: Critical notification (escalates to ALL channels)
        Notification critical = new Notification(
            "kapil@example.com", "SECURITY ALERT",
            "Unusual login detected",
            Priority.CRITICAL,
            Set.of(ChannelType.EMAIL)  // requested only email...
        );
        System.out.println("\n=== CRITICAL NOTIFICATION ===");
        service.send(critical);
        // ↑ PriorityEscalationStrategy sends to ALL channels, not just email
    }
}
```

---

# PART 4 — The Minimal Moves That Impress

## Your Interview Flow (memorize this order)

```
Minute 0-2:  RESTATE + 3 CLARIFYING QUESTIONS
             (scale, priority features, extensibility axis)

Minute 2-5:  LIST ENTITIES
             (draw boxes on excalidraw — nouns become classes)

Minute 5-8:  IDENTIFY BEHAVIORS + NAME PATTERNS
             (verbs become services, "varies" → Strategy,
              "create by type" → Factory, "react" → Observer)

Minute 8-12: DRAW THE PATTERN MAP
             (which service uses which pattern — THE MONEY SLIDE)

Minute 12-25: CODE
             (interfaces FIRST, then one concrete each,
              then orchestrator, then Main)

Minute 25-28: THREAD-SAFETY + EDGE CASES
             (AtomicBoolean, ConcurrentHashMap, what if X fails?)

Minute 28-30: EXTENSIBILITY MOVE
             ("Adding WhatsApp? One new class. Zero changes.")
```

## The 5 Lines That Score Maximum Points

| When to say it | What to say |
|---|---|
| When you spot varying behavior | *"This behavior varies — I'll put it behind an interface. That's Strategy + OCP."* |
| When you create objects by type | *"Caller shouldn't know the concrete class — Factory handles that."* |
| When multiple things react to one event | *"One event, many reactions — Observer. Publisher doesn't know the subscribers."* |
| When you show the orchestrator has no if/else | *"Look — no if/else in the main method. Strategy picks, Channel does, Observer reacts. Clean SRP."* |
| When they ask "how would you add X?" | *"One new class, zero changes to existing code. That's Open-Closed Principle."* |

## What NOT to Do

| Mistake | Why it kills you |
|---|---|
| Start coding immediately | They want to see you THINK first — entities, patterns, then code |
| Put all logic in one God class | Instant red flag. Separate concerns into services. |
| Hardcode `if (type == EMAIL)` in the service | That's the whole point of Strategy — extract it |
| Skip the Main/driver | InMobi PDF explicitly wants runnable examples |
| No interfaces | Everything concrete = untestable = red flag |
| Forget thread-safety | Even a 10-second mention counts |

## The Extensibility Answer Template

When they ask *"How would you add [X]?"* — and they WILL — use this template:

> *"To add [X], I'd create a new [ConcreteClass] implementing [Interface]. Register it in [Factory/Service]. Zero changes to [Orchestrator]. That's OCP — open for extension, closed for modification."*

**Examples:**
- "Add WhatsApp?" → `class WhatsAppChannel implements Channel` + one case in `ChannelFactory`
- "Add rate limiting?" → `class RateLimitedChannel implements Channel` (Decorator wrapping another Channel)
- "Add retry on failure?" → `class RetryChannel implements Channel` (Decorator)
- "Add priority queue for sending?" → Change `RoutingStrategy` impl, zero changes to `NotificationService`

---

# PART 5 — Quick Pattern Code Templates (Copy-Paste Skeletons)

## Strategy in 10 Lines

```java
// 1. Interface
interface PricingStrategy {
    double calculate(Ticket ticket);
}
// 2. Concrete
class HourlyPricing implements PricingStrategy {
    public double calculate(Ticket t) {
        long hours = ChronoUnit.HOURS.between(t.getEntry(), t.getExit());
        return hours * 50.0;
    }
}
// 3. Inject
class ParkingManager {
    private final PricingStrategy pricing;
    ParkingManager(PricingStrategy p) { this.pricing = p; }
}
```

## Factory in 8 Lines

```java
class ChannelFactory {
    static Channel create(ChannelType type) {
        return switch (type) {
            case EMAIL -> new EmailChannel();
            case SMS   -> new SmsChannel();
            case PUSH  -> new PushChannel();
        };
    }
}
```

## Observer in 10 Lines

```java
// 1. Interface
interface EventObserver {
    void onEvent(Event e);
}
// 2. Register + Fire
class EventManager {
    private final List<EventObserver> observers = new ArrayList<>();
    void register(EventObserver o) { observers.add(o); }
    void fire(Event e) {
        observers.forEach(o -> o.onEvent(e));
    }
}
```

---

> **Bottom line:** You don't need to memorize 500 lines of code. You need to:
> 1. Draw entities as boxes
> 2. Spot "varies" → Strategy, "create by type" → Factory, "react to event" → Observer
> 3. Code interfaces FIRST, one concrete each, then orchestrator
> 4. Say "one new class, zero changes" when they ask about extensibility
>
> That's it. That clears the SDE2 bar.
