# Java Building Blocks for LLD

> **Read before any Problem note.** Every LLD problem is built from these pieces. This note is a decision framework — for each building block: what it is in one line, and *when to choose it* over the alternatives.
> **Part of LLD Foundations.** Index + reading order: **../README.md**

---

## 🎯 Why This Matters

In an LLD interview, you'll make dozens of micro-decisions:
- "Should `ParkingSpot` be an interface or abstract class?"
- "Should I use `HashMap` or `ConcurrentHashMap`?"
- "When do I need `synchronized` vs `AtomicInteger`?"

These aren't trick questions. They're design questions. Having a mental framework for each saves you from freezing.

---

## 🔌 Interface vs Abstract Class — The #1 LLD Decision

This is the most-asked design question in LLD rounds. Interviewers ask it directly or force it by asking "why did you make ParkingSpot an interface?"

### The Decision Rule

```
  Does it define a CONTRACT           Does it define a PARTIAL IMPLEMENTATION
  (what something CAN DO)?            (what something IS, sharing real code)?
           │                                          │
           ▼                                          ▼
       Interface                              Abstract Class

  - Types share NO code               - Related types share real code
  - Multiple unrelated types          - Single inheritance is fine
  - Pluggability / strategy           - Template method pattern
  - No state to share                 - Common fields or logic exist
```

### Side by Side

| | Interface | Abstract Class |
|---|---|---|
| **Multiple inheritance** | ✅ Many interfaces per class | ❌ Only one abstract parent |
| **State (fields)** | ❌ No instance fields | ✅ Can have fields |
| **Constructor** | ❌ None | ✅ Has one |
| **Default impl** | ✅ Java 8+ `default` methods | ✅ Always |
| **Use for** | Contracts, roles, strategies | Shared code, template method |

---

### ✅ Use Interface — 9 examples

**One code example:**

```java
// ParkingSpot is an interface — CompactSpot, LargeSpot, EVSpot share zero code.
// They just fulfil the same contract. Any new spot type implements without breaking anything.
public interface ParkingSpot {
    SpotType getType();
    boolean isAvailable();
    void assignVehicle(Vehicle vehicle);
    Vehicle removeVehicle();
}
```

**Remaining examples in plain English:**

- **`FeeStrategy` (Parking Lot)** — `HourlyFeeStrategy` and `FlatRateFeeStrategy` have completely different math. No shared fields, no shared logic. One contract, two algorithms → Interface.
- **`SplitStrategy` (Splitwise)** — `EqualSplit`, `ExactSplit`, `PercentageSplit` compute differently. Nothing to share. → Interface.
- **`SchedulerStrategy` (Elevator)** — `FCFSStrategy` and `SCANStrategy` pick the next floor differently. No shared code. → Interface.
- **`RateLimiter` (Rate Limiter LLD)** — `TokenBucketLimiter`, `SlidingWindowLimiter`, `FixedWindowLimiter` are unrelated algorithms. → Interface.
- **`Comparator` (Java stdlib)** — `String`, `Integer`, `Employee`, `Book` are completely unrelated classes but all can implement `Comparator` for sorting. Interface lets unrelated types play the same role. → Interface.
- **`Runnable` (Java threading)** — Any class can become a background task by implementing `Runnable.run()`. Nothing to share. → Interface.
- **`PaymentGateway` (Razorpay/PhonePe)** — `UpiPayment`, `CardPayment`, `WalletPayment` hit different backends, different APIs, different error handling. Same contract: `pay(amount)`. → Interface.
- **`EventListener` (Observer pattern)** — `EmailNotifier`, `SMSNotifier`, `PushNotifier` all respond to the same event with completely different delivery mechanisms. → Interface.
- **`Serializable` (Java)** — A marker interface — no methods at all. Just signals the JVM "this class can be serialized." Any class can implement it. → Interface.

---

### ✅ Use Abstract Class — 9 examples

**One code example:**

```java
// AbstractParkingSpot — if ALL spot types must log every park/unpark to an audit file,
// that logging code is SHARED. Put it in the abstract base. getType() is still abstract.
public abstract class AbstractParkingSpot implements ParkingSpot {

    // Shared field — all spots need this
    protected Vehicle parkedVehicle;

    // Shared behaviour — all spots log access the same way
    @Override
    public void assignVehicle(Vehicle vehicle) {
        AuditLog.record("PARK: " + vehicle + " at " + getType());
        this.parkedVehicle = vehicle;
    }

    // Still abstract — each spot type returns its own type
    @Override
    public abstract SpotType getType();
}
```

**Remaining examples in plain English:**

- **`AbstractList` (Java Collections)** — `get()` and `size()` are abstract; but `iterator()`, `contains()`, `indexOf()`, `toString()` are implemented once in the base and shared by `ArrayList`, `LinkedList`, etc. → Abstract Class.
- **`BaseNotificationSender` (Notification system)** — `send(message)` is abstract (Email, SMS, Push each implement differently); but retry logic, rate-limiting, and error logging are identical for all senders → shared in base. → Abstract Class.
- **`AbstractElevatorScheduler` (Elevator)** — `pickNextFloor()` is abstract; but request queue management, adding/removing requests, and tracking the current floor are shared across FCFS and SCAN. → Abstract Class.
- **`AbstractReport` (Template Method pattern)** — `generateHeader()` and `generateFooter()` are implemented in base (same for all report types); `generateBody()` is abstract (invoice body ≠ summary body). → Abstract Class.
- **`AbstractFeeCalculator` (Fee system variation)** — Tax calculation and currency rounding are shared; `calculateBase(vehicle, minutes)` is abstract because hourly ≠ flat rate. → Abstract Class.
- **`HttpServlet` (Java Servlet API)** — `doGet()`, `doPost()` are abstract (each servlet implements its own); but `service()`, request parsing, and session handling are shared in the base class. → Abstract Class.
- **`Vehicle` base class (Cab booking)** — GPS tracking start/stop, fare meter start/stop are shared across Auto, Mini, Premier rides. `getFarePerKm()` is abstract because rates differ. → Abstract Class.
- **`JUnit TestCase` (testing)** — `setUp()`, `tearDown()` implemented in base (run around every test); test methods are overridden per test class. Framework reuses the lifecycle code. → Abstract Class.
- **`Animal` (game / simulation)** — `breathe()`, `eat()`, `move()` are identical for all animals; `makeSound()` is abstract because Dog says "woof", Cat says "meow". → Abstract Class.

---

**The one-line interview answer:**
> *"I made it an interface because the implementations share no code — they only need to honour the same contract. If they ever started sharing behaviour, I'd introduce an abstract base class that implements the interface."*

---

## 🧠 Enums With Behavior

Enums in Java can do more than hold constants. They can carry methods — and each constant can implement that method differently. This replaces `switch` chains scattered across the codebase.

**One code example:**

```java
// VehicleType knows which spot it needs — no switch needed anywhere else
public enum VehicleType {

    MOTORCYCLE {
        @Override
        public SpotType requiredSpot() {
            return SpotType.COMPACT;
        }
    },
    CAR {
        @Override
        public SpotType requiredSpot() {
            return SpotType.COMPACT;
        }
    },
    TRUCK {
        @Override
        public SpotType requiredSpot() {
            return SpotType.LARGE;
        }
    };

    public abstract SpotType requiredSpot();
}
// Usage: vehicle.getType().requiredSpot() — no if-else anywhere
```

**More examples in plain English — when each constant has a different rule:**

- **`OrderStatus` (BookMyShow)** — `PENDING`, `CONFIRMED`, `CANCELLED`, `REFUNDED`. Add `isTerminal(): boolean` → CANCELLED and REFUNDED return `true`, others return `false`. Replaces `if (status == CANCELLED || status == REFUNDED)` everywhere.
- **`ElevatorDirection` (Elevator)** — `UP`, `DOWN`, `IDLE`. Add `reverse()` → `UP.reverse()` returns `DOWN`, `DOWN.reverse()` returns `UP`. Controller calls `direction.reverse()` instead of an if-else.
- **`LogLevel` (Logger system)** — `DEBUG`, `INFO`, `WARN`, `ERROR`. Add `priority(): int` (DEBUG=0, ERROR=3). Filter: `if (msg.level.priority() >= threshold.priority())`. No switch needed.
- **`PaymentStatus` (Vending Machine)** — `PENDING`, `SUCCESS`, `FAILED`, `REFUNDED`. Add `isRetryable(): boolean` → only `FAILED` returns true. Payment service calls `status.isRetryable()`.
- **`SurgeTier` (Cab booking)** — `NONE(1.0)`, `MODERATE(1.5)`, `HIGH(2.0)`, `PEAK(3.0)`. Each constant holds a multiplier. Fare calculator calls `SurgeTier.forDemand(demand).multiplier()`.
- **`DayOfWeek` (Java stdlib)** — `MONDAY`...`SUNDAY`. `isWeekend()` → Saturday and Sunday return true. A fee strategy can call `DayOfWeek.now().isWeekend()` to decide surge pricing.
- **`SpotType` (Parking Lot)** — Add `allowsVehicle(VehicleType v): boolean`. `HANDICAPPED.allowsVehicle(TRUCK)` → false. Validation logic lives on the enum, not in ParkingLot.

**Rule:** When each enum constant has a different rule or returns a different value — put the rule on the enum. Kills `switch(enumValue)` in every service method.

---

## 🧭 Collections — Which One When

| I need to... | Use | LLD example | Real world |
|---|---|---|---|
| Ordered list, fast by index | `ArrayList` | Floors in ParkingLot, seats in a Screen | Product list on search results page |
| Fast lookup by key | `HashMap` | `ticketId → ParkingTicket` | `userId → User session` in auth service |
| Fast lookup + concurrent access | `ConcurrentHashMap` | `activeTickets` (park/unpark from diff threads) | Request rate counters per IP in API gateway |
| Unique elements, no duplicates | `HashSet` | Booked seat IDs in BookMyShow | Visited URLs in a web crawler |
| Queue, process in arrival order | `ArrayDeque` | External elevator requests | Task queue in thread pool |
| Always pop smallest/largest first | `PriorityQueue` | Elevator: pick closest floor first | Hospital: critical patients treated first |
| Sorted by key, range queries | `TreeMap` | Stock exchange order book (by price) | Leaderboard sorted by score |
| LRU eviction (access-ordered) | `LinkedHashMap(16, 0.75, true)` | LRU Cache implementation | Browser history (most recent, fixed size) |
| Never-changes list | `List.of(...)` | Allowed spot types for a floor | Whitelisted HTTP methods for an endpoint |

**ConcurrentHashMap vs synchronizedMap — the one question interviewers ask:**
- `ConcurrentHashMap` — concurrent reads never block each other; writes lock only the affected bucket (a small partition of the map). Use when multiple threads read AND write.
- `Collections.synchronizedMap(new HashMap<>())` — every read AND write locks the entire map. Use only when you need to iterate the whole map safely. Almost always `ConcurrentHashMap` wins.

---

## 🔁 Concurrency Primitives — Which One When

| Situation | Use | LLD example | Real world |
|---|---|---|---|
| One thread at a time in a block | `synchronized` | `parkVehicle()` in ParkingLot | Bank account debit — one thread at a time |
| Try to lock, don't block if busy | `ReentrantLock + tryLock()` | Elevator: try assigning, skip if elevator busy | DB connection pool: acquire with timeout |
| Fair queuing (first-come-first-served) | `ReentrantLock(true)` | Elevator: serve waiting requests in order | Print spooler: first requested, first printed |
| Many readers, rare writes | `ReadWriteLock` | Cache reads vs occasional invalidation | Config store: thousands of reads, rare update |
| Thread-safe counter | `AtomicInteger` | `availableCount` in ParkingFloor | Page view counter on a news article |
| Shared flag between threads | `volatile boolean` | `isRunning` flag in elevator background thread | Graceful shutdown flag in a server |
| Thread-safe map | `ConcurrentHashMap` | `activeTickets` in ParkingLot | Session store in a web server |
| Limit concurrent access | `Semaphore` | Max 3 EVs charging simultaneously | Max 10 concurrent DB connections |

### 🎨 Visual — Lock Decision Tree

```
Is the shared state just a counter?
  YES → AtomicInteger / AtomicLong
  NO  ↓

Is it a map?
  YES → ConcurrentHashMap
  NO  ↓

Is it read-heavy, write-rare?
  YES → ReadWriteLock
  NO  ↓

Do you need tryLock / timeout / fairness?
  YES → ReentrantLock
  NO  ↓

Simple mutual exclusion → synchronized

KEY INVARIANT:
   Lock the minimum scope that covers the shared mutable state.
   Over-locking (synchronized on everything) kills throughput.
   Under-locking (no sync on shared mutable state) causes race conditions.
```

---

## 🎨 Key Keywords — When to Use Each

| Keyword | What it does | LLD example | Why |
|---|---|---|---|
| `final` field | Reference can't change after construction | `private final FeeStrategy feeStrategy` | Strategy set once, never swapped at runtime |
| `final` class | Can't be subclassed | `ParkingTicket` | Value object — subclasses adding state would be wrong |
| `final` method | Can't be overridden | Template method in abstract base | Subclasses can't break the shared algorithm |
| `static` method | No instance needed | `ParkingSpotFactory.create()` | Factory methods are always static |
| `private` field | Only this class sees it | All fields on domain classes | Encapsulation — expose through methods |
| `protected` field | This class + subclasses | State in abstract base (`parkedVehicle`) | Subclasses need it; external callers don't |
| `volatile` field | Always read from main memory | `boolean isRunning` in background thread | Prevents thread from reading stale cached value |

---

## 🧾 TL;DR — Decision Rules to Memorise

> **Interface or Abstract Class?** → "Share no code → Interface. Share real code → Abstract Class."

> **Which collection?** → "Lookup by key → HashMap/ConcurrentHashMap. Ordered list → ArrayList. Unique elements → HashSet. Priority → PriorityQueue. Sorted → TreeMap. LRU → LinkedHashMap(access-order)."

> **Which concurrency primitive?** → "Counter → AtomicInteger. Map → ConcurrentHashMap. Simple lock → synchronized. Need tryLock → ReentrantLock. Read-heavy → ReadWriteLock. Limit slots → Semaphore."

> **Enums with behavior** → "Each constant has a different rule → put the rule on the enum. Kills switch statements everywhere else."

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | Created — Java coding foundations for LLD. |
| June 2026 | Expanded — 9 Interface examples, 9 Abstract Class examples, Enum/Collection/Concurrency with LLD + real-world columns. |
| Aug 2026 | Moved to Foundations/05-java-building-blocks.md during LLD folder restructure; cross-reference paths updated. |
