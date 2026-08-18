# SOLID Principles

> **Part of LLD Foundations.** Index + reading order: **../README.md**
>
> **SOLID is the vocabulary of design justification.** You are rarely asked to recite the
> definitions — you are asked *"why did you design it this way?"* SOLID is how you answer.
> Every design pattern in `../DesignPatterns/` exists to satisfy one or more SOLID principles.

---

## 🎯 Why This Matters

SOLID is five rules for code that is **easy to extend and hard to break**. In an LLD interview it shows up two ways:

1. **You apply it without naming it** → the interviewer thinks *"clean design."*
2. **You name it while applying it** → the interviewer thinks *"vocabulary AND clean design."*

Name it. At senior level, vocabulary is a scored signal.

**The mental hook for each letter:**

```
S — one class, one job
O — add code, don't edit code
L — a subtype must not surprise the caller
I — keep interfaces small
D — depend on interfaces; get your dependencies handed to you
```

---

## 📖 The 5 Principles

Each principle below has: the definition, a plain-English gloss, an everyday mental model, a **violation → fix** code pair, the smell that tells you it's being broken, and the exact sentence to say in the room.

---

### S — Single Responsibility Principle (SRP)

> *"A class should have only one reason to change."*

**Plain English:** each class does exactly one thing. If fee rules change, only the fee class changes. If spot-assignment logic changes, only the assignment class changes. Changes stay isolated.

**Mental model — a restaurant.** The chef cooks, the waiter serves, the cashier bills. If the menu changes, only the chef is affected; if the card terminal changes, only the cashier is. Separate jobs = separate people. In code: separate concerns = separate classes.

**Violation → Fix:**

```java
// ❌ Violation — one class with three reasons to change
public class ParkingLot {

    public void processExit(Vehicle vehicle, long minutes) {
        // 1. spot assignment concern
        // 2. fee calculation concern    ← billing team edits this class
        // 3. email receipt concern      ← notifications team edits this class
    }
}
```

```java
// ✅ Fix — one class, one job
public class ParkingLot {
    // owns spot assignment ONLY
}

public class FeeStrategy {
    // owns fee calculation ONLY
}

public class NotificationService {
    // owns the receipt ONLY
}
```

**More one-line examples:**

- **BookMyShow** — `SeatSelector` finds seats, `PricingEngine` prices, `BookingService` orchestrates, `NotificationService` confirms. Pricing changes never touch booking.
- **Spring MVC** — `Controller` (HTTP), `Service` (business logic), `Repository` (DB). Three layers, three reasons to change.
- **Splitwise** — `SplitCalculator` computes shares, `DebtSimplifier` simplifies the graph, `ReminderScheduler` nags. A new split algorithm never touches reminders.

**The smell:** a class named `XxxManager` / `XxxHelper` / `XxxUtils` with 10+ methods, or any name with **"And"** in it (`ParkingAndBillingManager` is two classes).

**Say this:** *"I extracted fee logic into `FeeStrategy` so `ParkingLot` has a single reason to change — spot assignment. Fee rules change independently of how spots are assigned."*

---

### O — Open/Closed Principle (OCP)

> *"Software entities should be open for extension, but closed for modification."*

**Plain English:** add new behaviour by adding a new class — not by editing existing, tested code. The moment you re-open working code to add a feature, you risk breaking what already works.

**Mental model — a power strip.** To add a device you plug into an open socket; you don't rewire the strip. The strip (your class) is unchanged; the new device (new implementation) is the extension.

**Violation → Fix:**

```java
// ❌ Violation — every new fee type re-opens this method
public double calculateFee(Vehicle v, long minutes) {
    if (isWeekend()) {
        return weekendRate * minutes;
    } else if (isEventDay()) {
        return flatRate;
    } else {
        return hourlyRate * Math.ceil(minutes / 60.0);
    }
}
```

```java
// ✅ Fix — new fee type = new class; existing code untouched
public interface FeeStrategy {
    double calculate(Vehicle vehicle, long minutes);
}

public class HourlyFeeStrategy implements FeeStrategy {
    @Override
    public double calculate(Vehicle vehicle, long minutes) {
        // hourly logic
        return 0.0;
    }
}

// Adding weekend pricing:
//   1. new file WeekendSurgeFeeStrategy implements FeeStrategy
//   2. inject it: new ParkingLot(floors, new WeekendSurgeFeeStrategy())
//   3. ParkingLot.java and HourlyFeeStrategy.java are NEVER touched
```

**More one-line examples:**

- **Rate Limiter** — adding `SlidingWindowLimiter` is one new class implementing `RateLimiter`; the service never changes.
- **Notification** — adding `SlackChannel` is one new class implementing `NotificationChannel`; existing channels are not re-tested.
- **Payments** — adding UPI is one new `UpiPaymentStrategy`; `CardPaymentStrategy` and `WalletPaymentStrategy` are untouched.

**The pattern connection:** **Strategy** is OCP for algorithms. **Factory** is OCP for object creation. **Observer** is OCP for event handling (add a listener without touching the event source).

**The smell:** adding an `else if` to an existing method to handle a new "type."

**Say this:** *"I used Strategy so adding a pricing model doesn't touch `ParkingLot`. Open for extension — new strategy class. Closed for modification — `ParkingLot` never changes."*

---

### L — Liskov Substitution Principle (LSP)

> *"Subtypes must be substitutable for their base type without breaking the caller."*

**Plain English:** everywhere you use a `ParkingSpot`, you must be able to drop in a `CompactSpot` or `EVSpot` without the caller changing behaviour or crashing. A subtype must not *surprise* the caller.

**Mental model — the USB standard.** Any USB device works in any USB port. If a device needed you to rewire the port, it isn't really USB — it broke the contract.

**Violation → Fix:**

```java
// ❌ Violation — subtype throws where the base type promised it wouldn't
public class HandicappedSpot extends ParkingSpot {
    @Override
    public void assignVehicle(Vehicle vehicle) {
        if (!vehicle.hasPermit()) {
            // ParkingLot calls assignVehicle() expecting a plain assignment.
            // This surprise exception crashes the caller — LSP broken.
            throw new PermitRequiredException();
        }
        super.assignVehicle(vehicle);
    }
}
```

```java
// ✅ Fix — move the surprising rule OUT of the substitutable method.
// A SpotValidator checks the permit BEFORE assignment; assignVehicle() stays
// a plain, surprise-free assignment that every subtype honours identically.
public class SpotValidator {
    public boolean canPark(Vehicle vehicle, ParkingSpot spot) {
        // permit / size / EV-charging checks live here, not inside the spot
        return true;
    }
}
```

**More one-line examples:**

- **`FeeStrategy` impls** — `HourlyFeeStrategy` and `FlatRateFeeStrategy` both return a valid non-negative amount; neither throws, neither returns null. Either substitutes the other.
- **Classic trap** — `Square extends Rectangle`: `setWidth(5)` on a Square also forces height to 5, breaking the caller's assumption that width and height are independent. A Square is **not** a substitutable Rectangle.

**The smell:** a subtype overriding a method just to `throw new UnsupportedOperationException()`. The fix is almost always to split the base into a leaner interface that only promises what all subtypes can deliver (this is why LSP and ISP are cousins).

**Say this:** *"All my spot implementations honour the `ParkingSpot` contract without adding surprise behaviour — any of them can be swapped in wherever `ParkingSpot` is expected."*

---

### I — Interface Segregation Principle (ISP)

> *"No client should be forced to depend on methods it does not use."*

**Plain English:** keep interfaces small and focused. A fat 10-method interface forces every implementor to stub out the ones it doesn't need — dead code that lies about what the class does.

**Mental model — separate remotes.** A TV remote and a cable remote are separate on purpose. You don't want the cable remote sprouting TV-volume buttons it can't really use. Fat "universal" remotes are confusing precisely because they violate ISP.

**Violation → Fix:**

```java
// ❌ Violation — a fat interface forces stubs that lie
public interface ParkingSpot {
    boolean isAvailable();
    void assignVehicle(Vehicle v);
    void startCharging();          // only EVSpot needs this
    void stopCharging();           // only EVSpot needs this
    boolean hasPermit(Vehicle v);  // only HandicappedSpot needs this
}

public class CompactSpot implements ParkingSpot {
    @Override
    public void startCharging() {
        // a stub that pretends a compact spot can charge — a lie
        throw new UnsupportedOperationException();
    }
    // ... other methods
}
```

```java
// ✅ Fix — segregate by capability
public interface ParkingSpot {
    boolean isAvailable();
    void assignVehicle(Vehicle v);
    void removeVehicle();
}

public interface ChargingCapable {
    void startCharging();
    void stopCharging();
}

public interface PermitRequired {
    boolean hasPermit(Vehicle v);
}

// EVSpot          implements ParkingSpot, ChargingCapable
// HandicappedSpot implements ParkingSpot, PermitRequired
// CompactSpot     implements ParkingSpot          ← only what it actually does
```

**More one-line examples:**

- **Rate Limiter** — `RateLimiter` has only `isAllowed(clientId)`. Admin ops (`resetQuota()`) live on a separate `RateLimiterAdmin`.
- **Java stdlib** — `Closeable` has exactly one method: `close()`. Not `flush()`/`reset()`/`read()` — those are separate interfaces.

**The smell:** empty method bodies or `UnsupportedOperationException` in implementations. The interface is demanding something the implementor can't honour — split it.

**Say this:** *"I kept `ParkingSpot` lean — just the core contract. EV charging is a separate `ChargingCapable` interface, so `CompactSpot` never implements methods it doesn't need."*

---

### D — Dependency Inversion Principle (DIP)

> *"Depend on abstractions, not on concretions. Details depend on abstractions, not the reverse."*

**Plain English:** a high-level class declares an interface it needs; the concrete implementation is *injected in from outside*, not hardcoded inside. This is the difference between *what* a class needs and *who* supplies it.

**Mental model — a wall socket.** Your appliance plugs into a standard 220V interface, not directly into the power plant. Coal, solar, or nuclear behind the socket — the appliance neither knows nor cares. Swap the plant without rewiring the appliance.

**Violation → Fix:**

```java
// ❌ Violation — ParkingLot hardwires the concrete fee class
public class ParkingLot {
    // constructs its own dependency → can't swap, can't test in isolation
    private final HourlyFeeStrategy feeStrategy = new HourlyFeeStrategy(50.0);
}
```

```java
// ✅ Fix — depend on the interface; the caller injects the concrete type
public class ParkingLot {

    private final FeeStrategy feeStrategy;   // interface, not class

    public ParkingLot(List<ParkingFloor> floors, FeeStrategy feeStrategy) {
        this.feeStrategy = feeStrategy;      // injected — ParkingLot doesn't choose
    }
}

// The caller decides which algorithm to inject:
// ParkingLot eventLot  = new ParkingLot(floors, new FlatRateFeeStrategy(200.0));
// ParkingLot normalLot = new ParkingLot(floors, new HourlyFeeStrategy(50.0));
```

**More one-line examples:**

- **Spring `@Autowired`** — you declare `private UserRepository repo;` (interface); Spring injects the JPA concrete bean at runtime. You never call `new JpaUserRepository()`. Spring's whole IoC container exists to enforce DIP.
- **JDBC** — your DAO depends on `Connection` (interface); `DriverManager` returns the MySQL/Postgres concrete implementation. Your code imports no vendor class.

**The smell:** `new ConcreteClass()` inside a service class. If a high-level class constructs its own low-level dependencies, DIP is broken. Fix: inject via constructor.

**Say this:** *"`ParkingLot` depends on the `FeeStrategy` interface, not a concrete class. The concrete strategy is injected — that's dependency inversion, and it's what makes the algorithm swappable and the class testable."*

---

## 🧭 SOLID Quick-Fire — Name the Principle While Designing

| When you're doing this… | Say this… |
|---|---|
| Splitting fee logic out of `ParkingLot` | *"Single Responsibility — assignment vs billing are separate reasons to change"* |
| Adding a new class instead of editing an existing one | *"Open/Closed — new behaviour via new class, existing code untouched"* |
| Ensuring all implementations are safely swappable | *"Liskov — any `ParkingSpot` implementation is a valid drop-in"* |
| Keeping an interface lean | *"Interface Segregation — `CompactSpot` shouldn't implement charging methods"* |
| Injecting via the constructor | *"Dependency Inversion — depend on the interface, inject the concretion"* |

---

## 🎨 Visual — How SOLID Powers the Patterns

```
SOLID Principle          Pattern it enables
──────────────────────────────────────────────────────────
Single Responsibility  → Strategy   (fee logic in its own class)
                       → Observer   (each listener in its own class)
                       → Command    (each operation is its own class)

Open / Closed          → Strategy   (add algorithm via new class)
                       → Factory    (add type via new class + one case)
                       → Observer   (add listener, never touch the source)

Liskov Substitution    → any interface-based design
                       → all spot impls honour the ParkingSpot contract

Interface Segregation  → lean, single-purpose interfaces
                       → ParkingSpot vs ChargingCapable vs PermitRequired

Dependency Inversion   → constructor injection everywhere
                       → Spring @Autowired automates it

KEY INVARIANT:
   Patterns are concrete implementations of SOLID.
   Name the PRINCIPLE when asked "why". Name the PATTERN when asked "how".
```

---

## ⚠️ Common SOLID Mistakes in Interviews

| Mistake | Principle violated | Fix |
|---|---|---|
| Fee calculation inside `ParkingLot` | SRP | Extract to `FeeStrategy` |
| `if-else` for every new spot/fee type | OCP | Add a class, not a branch |
| A spot implementation throws an unexpected exception | LSP | Validation in a `Validator`, not the spot |
| Giant `ParkingSystemManager` interface | ISP | Split by role/capability |
| `ParkingLot` calls `new HourlyFeeStrategy()` | DIP | Inject via constructor |

---

## 🔬 Interview Q&As

**Q: Walk me through all 5 SOLID principles.**
> **S** — one class, one reason to change (`FeeStrategy` bills, `ParkingLot` assigns).
> **O** — new fee types are new classes, not edits to `calculateFee`.
> **L** — every `ParkingSpot` subtype is a surprise-free drop-in.
> **I** — `ParkingSpot` stays lean; charging is a separate `ChargingCapable` interface.
> **D** — `ParkingLot` depends on the `FeeStrategy` interface; the concrete type is injected.

**Q: Which principle is most violated in large codebases?**
> SRP — the "God class." `OrderService` slowly grows to 50 methods spanning inventory, pricing, fraud, and fulfilment. Every sprint edits it; every PR conflicts. The fix is extracting focused services, but by then the debt is deep.

**Q: OCP vs DIP — aren't they the same?**
> OCP is about **evolution** — don't modify working code to add behaviour; extend it. DIP is about **wiring** — a class shouldn't grab its own dependencies; they're handed to it. DIP usually *enables* OCP by making the concrete type injectable and therefore swappable.

**Q: How does SOLID relate to testability?**
> DIP is the direct enabler. When `ParkingLot` depends on the `FeeStrategy` interface injected via constructor, a test injects a stub — no real fee engine needed. Without DIP, every test drags in the concrete dependency. ISP helps too: small interfaces mean small mocks.

---

## 🧾 TL;DR

> *S: one class, one job. O: add code, don't edit it. L: subtypes don't surprise the caller. I: keep interfaces small. D: depend on interfaces, inject the concrete type.*
>
> In the room: **name the principle when you apply it.** It turns a silent design decision into a scored signal.

**Related:** design patterns that implement these → **../DesignPatterns/**. The non-SOLID principles (KISS, DRY, YAGNI) → **03-design-principles.md**.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 2026 | **Created by merging `solid-principles.md` (root, general/parking-lot examples) and `DesignPatterns/00-solid-principles.md` (DocuSign billing examples).** De-branded to a general Foundations note; kept the strongest violation→fix code pairs, the quick-fire table, the pattern-connection visual, and the common-mistakes table. Both source files deleted; this is now the single canonical SOLID note. |
