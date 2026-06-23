# SOLID Principles for LLD Interviews

> **SOLID is the vocabulary of design justification.** You won't be asked to recite definitions — you'll be asked "why did you design it this way?" SOLID is how you answer that. Every pattern in this folder exists to satisfy one or more SOLID principles.

---

## 🎯 Why This Matters

SOLID is a set of 5 principles for writing code that is easy to extend and hard to break. In LLD interviews, SOLID shows up in two ways:

1. **You apply it without naming it** — and the interviewer thinks "they designed it cleanly"
2. **You name it while applying it** — and the interviewer thinks "they have vocabulary AND they design cleanly"

Name it. Vocabulary matters at SDE-3.

---

## 📖 The 5 Principles

---

### S — Single Responsibility Principle (SRP)

> *"A class should have only one reason to change."*

**Plain English:** Each class does exactly one thing. If fee rules change, only the fee class changes. If spot assignment logic changes, only the assignment class changes. Changes are isolated.

**One code example:**

```
✅ SOLID — Parking Lot:
   ParkingLot      → owns spot assignment (one reason to change)
   FeeStrategy     → owns fee calculation (one reason to change)
   ParkingTicket   → owns entry/exit data (one reason to change)
   ParkingFloor    → owns per-floor spot search (one reason to change)

❌ Violation:
   ParkingLot.calculateFee(vehicle, duration) { ... }      ← billing reason
   ParkingLot.findAvailableSpot(type) { ... }              ← assignment reason
   ParkingLot.sendEmailReceipt(ticket) { ... }             ← notification reason
   // ParkingLot now has 3 reasons to change — every one of these teams edits the same class
```

**More examples in plain English:**

- **Logger system** — `LogHandler` writes to the destination; `LogFormatter` formats the message; `LogRouter` decides which handlers to invoke. Each has one reason to change: new destination, new format, new routing rule.
- **BookMyShow** — `SeatSelector` finds available seats; `PricingEngine` calculates price; `BookingService` orchestrates; `NotificationService` sends confirmation. Pricing changes never touch booking; delivery changes never touch pricing.
- **Spring MVC** — `Controller` handles HTTP concerns; `Service` owns business logic; `Repository` owns DB access. Three layers, three reasons to change, three separate classes.
- **Splitwise** — `SplitCalculator` computes who owes what; `DebtSimplifier` simplifies the graph; `ReminderScheduler` sends due reminders. A new split algorithm doesn't touch reminders.
- **Real world analogy** — A restaurant: chef cooks, waiter serves, cashier bills. If the menu changes, only the chef is affected. If payment system changes, only the cashier is affected.

**Red flag in your own design:** A class named `XxxManager`, `XxxHelper`, or `XxxUtils` with 10+ methods usually violates SRP. If the name contains "And" — `ParkingAndBillingManager` — it's definitely two classes.

**Interview phrase:** *"I extracted fee logic into FeeStrategy so ParkingLot has a single reason to change — spot assignment. Fee rules change independently of how spots are assigned."*

---

### O — Open/Closed Principle (OCP)

> *"Open for extension, closed for modification."*

**Plain English:** Add new behaviour by adding a new class — not by editing existing code. Existing, tested code stays untouched.

**One code example:**

```
✅ SOLID — adding WeekendSurgeFeeStrategy to Parking Lot:
   1. Create WeekendSurgeFeeStrategy implements FeeStrategy   ← new file
   2. Inject it: new ParkingLot(floors, new WeekendSurgeFeeStrategy())  ← config
   3. ParkingLot.java untouched. HourlyFeeStrategy.java untouched.

❌ Violation — same goal without OCP:
   public double calculateFee(Vehicle v, long minutes) {
       if (isWeekend()) { return weekendRate * minutes; }
       else if (isEventDay()) { return flatRate; }
       else { return hourlyRate * Math.ceil(minutes / 60.0); }
       // Every new fee type means opening this method and editing it
   }
```

**More examples in plain English:**

- **Rate Limiter** — Adding `SlidingWindowLimiter` = one new class implementing `RateLimiter`. `RateLimiterService` never changes. `TokenBucketLimiter` never changes.
- **Logger system** — Adding `CloudWatchHandler` = one new class implementing `LogHandler`. `FileHandler` and `ConsoleHandler` are untouched. No existing code re-tested.
- **Splitwise** — Adding `PercentageSplitStrategy` = one new class. `Expense.split()` calls the interface — it never changes.
- **Factory pattern** — Adding `EVSpot` = one new class + one line in `ParkingSpotFactory`. `ParkingLot`, `ParkingFloor` unchanged.
- **Payment gateways** — Razorpay adds UPI support: one new `UpiPaymentStrategy`. All existing `CardPaymentStrategy`, `WalletPaymentStrategy` untouched.
- **Real world analogy** — A power strip has open slots for new plugs. Adding a new device doesn't require rewiring the strip — it's open for extension (new devices), closed for modification (the strip itself).

**The pattern connection:** Strategy IS OCP for algorithms. Factory IS OCP for object creation. Observer IS OCP for event handling (add a new listener without touching the event source).

**Interview phrase:** *"I used Strategy here so adding a new pricing model doesn't touch ParkingLot. Open for extension — new strategy class. Closed for modification — ParkingLot never changes."*

---

### L — Liskov Substitution Principle (LSP)

> *"Subtypes must be substitutable for their base type without breaking the program."*

**Plain English:** Everywhere you use `ParkingSpot`, you must be able to swap in `CompactSpot`, `EVSpot`, or any other implementation without the caller breaking or changing behaviour.

**One code example:**

```java
// LSP holds — ParkingLot calls the same method on any ParkingSpot
ParkingSpot spot = ParkingSpotFactory.create(spotType); // could be CompactSpot or EVSpot
spot.assignVehicle(vehicle);   // always works — no surprises, no extra exceptions
boolean free = spot.isAvailable(); // always returns a boolean — no IllegalStateException
```

```
❌ LSP Violation:
   HandicappedSpot.assignVehicle(vehicle) {
       if (!vehicle.hasPermit()) {
           throw new PermitRequiredException(); // ← not in the ParkingSpot contract
       }
   }
   // ParkingLot calls assignVehicle() assuming it just assigns.
   // Now HandicappedSpot crashes the caller — LSP broken.
```

**More examples in plain English:**

- **`FeeStrategy` implementations** — `HourlyFeeStrategy` and `FlatRateFeeStrategy` both return a valid positive double from `calculate()`. Neither throws. Neither returns null. Either can substitute the other wherever `FeeStrategy` is used.
- **BookMyShow seats** — `GoldSeat`, `SilverSeat`, `PlatinumSeat` all implement `Seat.book(userId)`. All accept a valid user, mark the seat booked, return confirmation. No unexpected exceptions. Any seat type can be used wherever `Seat` is expected.
- **`SplitStrategy` implementations** — `EqualSplit`, `ExactSplit`, `PercentageSplit` all return a valid `List<ExpenseSplit>` from `split(amount, participants)`. None throws for valid input.
- **Classic violation** — `Square extends Rectangle`. Setting `rectangle.setWidth(5)` on a Square also sets height to 5 — breaks the caller's expectation that width and height are independent. A Square is NOT substitutable for a Rectangle.
- **Real world analogy** — Any USB device (keyboard, mouse, webcam) plugs into any USB port and works. Liskov at hardware level. If a webcam required a different physical port — LSP violated.

**The fix for the permit scenario:** Put permit validation in a `SpotValidator` before reaching the spot. The spot's `assignVehicle()` remains a simple assignment — no surprises.

**Interview phrase:** *"All my spot implementations honour the ParkingSpot contract without adding surprise behaviour. CompactSpot, EVSpot — any of them can be swapped in wherever ParkingSpot is expected."*

---

### I — Interface Segregation Principle (ISP)

> *"Don't force a class to implement methods it doesn't need."*

**Plain English:** Keep interfaces small and focused. A fat interface with 10 methods forces every implementor to stub out the ones it doesn't use — dead code that lies about what the class does.

**One code example:**

```
❌ Fat interface violation:
   public interface ParkingSpot {
       boolean isAvailable();
       void assignVehicle(Vehicle v);
       void startCharging();        // only EVSpot needs this
       void stopCharging();         // only EVSpot needs this
       boolean hasPermit(Vehicle v);// only HandicappedSpot needs this
   }
   // CompactSpot must implement startCharging() { throw new UnsupportedOperationException(); }
   // That stub is a lie — it pretends CompactSpot can charge

✅ Segregated:
   interface ParkingSpot     { isAvailable(); assignVehicle(); removeVehicle(); }
   interface ChargingCapable { startCharging(); stopCharging(); }
   interface PermitRequired  { boolean hasPermit(Vehicle v); }

   EVSpot          implements ParkingSpot, ChargingCapable
   HandicappedSpot implements ParkingSpot, PermitRequired
   CompactSpot     implements ParkingSpot  ← only what it actually does
```

**More examples in plain English:**

- **Splitwise** — `SplitStrategy` only has `split(amount, participants)`. If you added `sendReminder()` to the same interface, `EqualSplitStrategy` would have to implement a notification method it has nothing to do with.
- **Rate Limiter** — `RateLimiter` only has `isAllowed(clientId): boolean`. Admin operations (`resetQuota()`, `getRemainingQuota()`) live on a separate `RateLimiterAdmin` interface. A `TokenBucketLimiter` implements only `RateLimiter` unless it also supports admin ops.
- **Logger system** — `LogHandler` only has `handle(LogEvent)`. Filtering logic lives on `LogFilter`. Formatting logic lives on `LogFormatter`. Each implementor only implements what it does.
- **Elevator** — `SchedulerStrategy` only has `assignElevator(request, elevators)`. Diagnostics and metrics go on a separate interface so `FCFSStrategy` doesn't stub out methods it doesn't need.
- **Java stdlib** — `Closeable` has one method: `close()`. Not `flush()`, `reset()`, `read()` — those are on separate interfaces. You only implement the interface whose contract you can actually fulfil.
- **Real world analogy** — A TV remote and a cable remote are separate. You don't want your cable remote to also control volume on the TV — ISP at product design level. Fat universal remotes are confusing precisely because they violate ISP.

**Interview phrase:** *"I kept ParkingSpot lean — just the core contract. EV charging is a separate ChargingCapable interface. CompactSpot doesn't implement methods it doesn't need."*

---

### D — Dependency Inversion Principle (DIP)

> *"Depend on abstractions, not concretions."*

**Plain English:** High-level classes declare interfaces. The concrete implementation is injected in from outside — not hardcoded inside.

**One code example:**

```java
// ❌ DIP violation — ParkingLot hard-codes the concrete fee class
public class ParkingLot {
    private HourlyFeeStrategy feeStrategy = new HourlyFeeStrategy(50.0);
    // Switching to FlatRateFeeStrategy requires editing ParkingLot
}

// ✅ DIP — ParkingLot depends on the interface; caller injects the concrete type
public class ParkingLot {
    private final FeeStrategy feeStrategy; // interface, not class

    public ParkingLot(List<ParkingFloor> floors, FeeStrategy feeStrategy) {
        this.feeStrategy = feeStrategy; // injected — ParkingLot doesn't choose
    }
}

// Caller decides which algorithm to inject:
ParkingLot eventLot  = new ParkingLot(floors, new FlatRateFeeStrategy(200.0));
ParkingLot normalLot = new ParkingLot(floors, new HourlyFeeStrategy(50.0));
```

**More examples in plain English:**

- **Elevator** — `ElevatorController` depends on `SchedulerStrategy` (interface), not `FCFSStrategy` (concrete). The scheduling algorithm is injected at construction. Swapping FCFS for SCAN = zero changes to ElevatorController.
- **Notification system** — `NotificationService` depends on `NotificationChannel` (interface), not `EmailService`. If SMS is added, inject `SMSChannel` — NotificationService unchanged.
- **Spring `@Autowired`** — You declare `private UserRepository userRepository;` (interface). Spring injects the JPA concrete bean at runtime. Your service never calls `new JpaUserRepository()`.
- **Splitwise** — `Expense` depends on `SplitStrategy` (interface) injected at construction. Add `PercentageSplitStrategy` → inject it. `Expense` never changes.
- **Rate Limiter** — `RateLimiterService` holds `Map<clientId, RateLimiter>` (interface). Each client's limiter is created by `RateLimiterFactory` and injected — the service doesn't know which algorithm runs.
- **Real world analogy** — A car depends on "fuel" (gasoline, diesel, electric, hydrogen) — not specifically "Shell petrol". The car has a fuel port; what goes in can change. DIP at engineering level.
- **JDBC** — Your DAO depends on `Connection` (interface). `DriverManager.getConnection()` returns the MySQL or PostgreSQL concrete implementation. Your code never imports a MySQL-specific class.

**Interview phrase:** *"ParkingLot depends on the FeeStrategy interface, not any concrete class. The concrete strategy is injected — this is dependency inversion. It's what makes the algorithm swappable."*

---

## 🧭 SOLID Quick-Fire — Name the Principle While Designing

| When you're doing this... | Say this... |
|---|---|
| Splitting fee logic out of ParkingLot | *"Single Responsibility — ParkingLot owns assignment, FeeStrategy owns billing"* |
| Adding a new class instead of editing existing | *"Open/Closed — new behaviour via new class, existing code untouched"* |
| All implementations safely substitutable | *"Liskov Substitution — any ParkingSpot implementation is a valid drop-in"* |
| Keeping ParkingSpot interface lean | *"Interface Segregation — CompactSpot shouldn't implement charging methods"* |
| Injecting via constructor | *"Dependency Inversion — ParkingLot depends on the interface, not the concretion"* |

---

## 🎨 Visual — How SOLID Connects to Patterns

```
SOLID Principle          Pattern it enables
──────────────────────────────────────────────────────────
Single Responsibility  → Strategy (fee logic in its own class)
                       → Observer (notification in its own class)
                       → Command (each operation is its own class)

Open / Closed          → Strategy (add algorithm via new class)
                       → Factory (add type via new class + one case)
                       → Observer (add listener, never touch event source)

Liskov Substitution    → Any interface-based design
                       → All spot implementations honour ParkingSpot contract

Interface Segregation  → Lean strategy interfaces (one method each)
                       → ParkingSpot vs ChargingCapable vs PermitRequired

Dependency Inversion   → Constructor injection everywhere
                       → Spring @Autowired automates this

KEY INVARIANT:
   Patterns are implementations of SOLID.
   Name SOLID when asked "why". Name the Pattern when asked "how".
```

---

## ⚠️ Common SOLID Mistakes in Interviews

| Mistake | Principle violated | Fix |
|---|---|---|
| Fee calculation inside `ParkingLot` | SRP | Extract to `FeeStrategy` |
| `if-else` for every new spot/fee type | OCP | Add a class, not an `if` branch |
| Spot implementation throws unexpected exception | LSP | Validation in a Validator, not the spot |
| Giant `ParkingSystemManager` interface | ISP | Split by role |
| `ParkingLot` calls `new HourlyFeeStrategy()` | DIP | Inject via constructor |
| `ElevatorController` imports `FCFSStrategy` directly | DIP | Depend on `SchedulerStrategy` interface |

---

## 🧾 TL;DR

> *S: One class, one job. O: Add code, don't edit it. L: Subtypes behave like their parent. I: Keep interfaces small. D: Depend on interfaces, inject the concrete type.*
>
> *In an LLD interview: name the principle when you apply it. It turns a design decision into a signal.*

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | Created — SOLID with LLD examples. |
| June 2026 | Expanded — 4-6 English examples per principle, mixing Parking Lot, BookMyShow, Elevator, Splitwise, Rate Limiter, real-world analogies. |
