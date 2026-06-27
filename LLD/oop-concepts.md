# OOP Concepts

> **Read before any pattern note.** The 4 pillars of OOP — Encapsulation, Abstraction, Polymorphism, Inheritance — are the foundation everything else is built on. Interviewers ask "explain polymorphism with an example from your design" — this file gives you the prepared answer. It also covers the most important OOP decision in LLD interviews: **Composition vs Inheritance**.
>
> **Cross-reference:** Interface vs Abstract Class decision → `java-building-blocks-for-lld.md`

---

## 🎯 Why OOP Matters in LLD Interviews

Interviewers don't just want you to use OOP — they want you to name what you're doing and why. "I made `ParkingSpot` an interface" is half an answer. "I made it an interface because these implementations share no code — that's Abstraction: I'm hiding HOW each spot works behind a WHAT contract" is a complete answer.

---

## 🔹 Pillar 1 — Encapsulation

### 🎯 What It Is

Hide the internal state of an object. Expose only what callers need to know. Fields are `private`; callers interact through `public` methods. The class controls what can be read and what can be changed, enforcing its own invariants.

### 🧠 Mental Model

A vending machine. You press a button and get a snack. You can't reach inside and take snacks directly, and you can't force the machine to give you a snack without paying. The internal inventory count, the cash drawer, the dispense mechanism — all hidden. The buttons (public methods) are the only interface. This protects the machine's invariants: you can't create a negative inventory.

### ⚙️ Code Example

```java
// ❌ No encapsulation — anyone can corrupt the spot's state
public class ParkingSpot {

    public SeatStatus status = SeatStatus.AVAILABLE;  // public field
    public Vehicle parkedVehicle = null;               // public field
}
// Anywhere in the codebase: spot.status = SeatStatus.BOOKED without assigning a vehicle
// Now the spot is booked but has no vehicle — broken invariant
```

```java
// ✅ Encapsulated — invariant is enforced by the class
public class ParkingSpot {

    private SeatStatus status = SeatStatus.AVAILABLE;
    private Vehicle parkedVehicle = null;

    // The only way to change state — validates invariant
    public boolean assign(Vehicle vehicle) {
        if (this.status != SeatStatus.AVAILABLE) {
            return false;
        }
        this.parkedVehicle = vehicle;
        this.status = SeatStatus.OCCUPIED;
        return true;
    }

    public Vehicle release() {
        Vehicle released = this.parkedVehicle;
        this.parkedVehicle = null;
        this.status = SeatStatus.AVAILABLE;
        return released;
    }

    public boolean isAvailable() {
        return this.status == SeatStatus.AVAILABLE;
    }
}
// Impossible to have status = OCCUPIED with parkedVehicle = null — invariant guaranteed
```

### 🔌 Interview Drop-In

> *"All fields on ParkingSpot are private — Encapsulation. The only way to change status is through `assign()` and `release()`, which enforce the invariant that you can't have a booked spot with no vehicle."*

---

## 🔹 Pillar 2 — Abstraction

### 🎯 What It Is

Hide HOW something works. Expose only WHAT it does. Callers depend on a contract (interface), not on the implementation details. This means implementations can change without callers knowing.

### 🧠 Mental Model

A TV remote. You press "Volume Up" — you don't know if the TV uses infrared, Bluetooth, or HDMI-CEC internally. You don't care. The button (the interface) says "Volume Up"; the TV does whatever it needs to do. If Samsung changes the internal sound chip, your remote still works because the contract (the button) didn't change.

In code: `FeeStrategy.calculate(vehicle, minutes)` is the button. `HourlyFeeStrategy`, `FlatRateFeeStrategy`, `WeekendSurgeStrategy` are the TVs. ParkingLot only knows the button — it calls `calculate()`. Swap the implementation, ParkingLot doesn't change.

### ⚙️ Code Example

```java
// Abstraction: FeeStrategy hides HOW fees are calculated
// ParkingLot knows WHAT it does (calculate), not HOW
public interface FeeStrategy {

    long calculate(VehicleType vehicleType, long parkingMinutes);
}
```

```java
// Two very different implementations — ParkingLot can't tell which it has
public class HourlyFeeStrategy implements FeeStrategy {

    @Override
    public long calculate(VehicleType vehicleType, long parkingMinutes) {
        long hours = (parkingMinutes / 60) + (parkingMinutes % 60 > 0 ? 1 : 0);
        return hours * 20L;
    }
}

public class FlatRateFeeStrategy implements FeeStrategy {

    @Override
    public long calculate(VehicleType vehicleType, long parkingMinutes) {
        // same fee regardless of time
        return 50L;
    }
}
```

```java
// ParkingLot uses the abstraction — no import of HourlyFeeStrategy
public class ParkingLot {

    private final FeeStrategy feeStrategy;  // ← the abstract type, not concrete

    public ParkingLot(FeeStrategy feeStrategy) {
        this.feeStrategy = feeStrategy;
    }

    public long calculateFee(Vehicle vehicle, long minutes) {
        return feeStrategy.calculate(vehicle.getType(), minutes);
    }
}
```

### 🔌 Interview Drop-In

> *"ParkingLot depends on `FeeStrategy`, not `HourlyFeeStrategy` — Abstraction. ParkingLot knows the contract (calculate a fee), not the implementation. Dependency Inversion principle is the SOLID name for the same idea."*

---

## 🔹 Pillar 3 — Polymorphism

### 🎯 What It Is

One interface, many implementations. The same method call behaves differently depending on the actual type at runtime. Polymorphism eliminates `if-else` / `switch` on type and replaces it with "let each object handle itself."

Two flavours:
- **Runtime polymorphism** (the one that matters in LLD): method overriding via inheritance/interfaces. The actual method called is determined at runtime.
- **Compile-time polymorphism**: method overloading (same name, different parameter types). Less interesting in design discussions.

### 🧠 Mental Model

A company has a "submit report" policy that applies to every department. Marketing submits a slide deck. Engineering submits a JIRA update. Finance submits a spreadsheet. The same instruction ("submit report") produces different behaviour for each department. The manager doesn't need a switch statement — each department knows how to submit.

### ⚙️ Code Example

```java
// Without polymorphism — switch on type, grows with every new type
public long calculateFee(Vehicle vehicle, long minutes) {
    if (vehicle.getType() == VehicleType.MOTORCYCLE) {
        return minutes / 60 * 10L;
    } else if (vehicle.getType() == VehicleType.CAR) {
        return minutes / 60 * 20L;
    } else if (vehicle.getType() == VehicleType.TRUCK) {
        return minutes / 60 * 40L;
    }
    throw new IllegalArgumentException("Unknown vehicle type");
}
// Add a new vehicle type → modify this method → violates OCP
```

```java
// ✅ With polymorphism — each implementation handles itself
public interface FeeStrategy {
    long calculate(VehicleType vehicleType, long minutes);
}

// Caller doesn't care which implementation it has
public long calculateFee(Vehicle vehicle, long minutes) {
    return feeStrategy.calculate(vehicle.getType(), minutes);
}
// Add a new vehicle type → add a new FeeStrategy implementation → nothing else changes
```

### 🔌 Interview Drop-In

> *"By making `ParkingSpot` an interface, I get polymorphism — `floor.findAvailableSpot()` iterates `List<ParkingSpot>` and calls `isAvailable()` on each. Whether it's a CompactSpot, LargeSpot, or EVSpot doesn't matter — each answers the same question differently. No switch statements on spot type."*

---

## 🔹 Pillar 4 — Inheritance

### 🎯 What It Is

A class inherits fields and methods from a parent class (IS-A relationship). Subclasses reuse code from the parent and can override behaviour. Reduces duplication when multiple classes genuinely share an implementation.

### 🧠 Mental Model

An `Animal` base class has `breathe()`, `eat()`, `sleep()` — shared by all animals. `Dog` and `Cat` inherit these for free and only override `makeSound()`. You write breathe/eat/sleep once, not once per animal.

### ⚙️ Code Example

```java
// ✅ Legitimate inheritance — shared behaviour + overriding specific behaviour
public abstract class AbstractParkingSpot implements ParkingSpot {

    // Shared state and shared behaviour — both CompactSpot and EVSpot need this
    protected Vehicle parkedVehicle;
    protected SeatStatus status = SeatStatus.AVAILABLE;

    // Shared implementation: all spots log their assignment
    @Override
    public boolean assign(Vehicle vehicle) {
        if (this.status != SeatStatus.AVAILABLE) {
            return false;
        }
        this.parkedVehicle = vehicle;
        this.status = SeatStatus.OCCUPIED;
        return true;
    }

    // Abstract: each subclass returns its own type
    @Override
    public abstract SpotType getType();
}

public class EVSpot extends AbstractParkingSpot {

    @Override
    public SpotType getType() {
        return SpotType.EV;
    }

    // EVSpot-specific: override to also start charging
    @Override
    public boolean assign(Vehicle vehicle) {
        boolean assigned = super.assign(vehicle);
        if (assigned) {
            startCharging(vehicle);
        }
        return assigned;
    }

    private void startCharging(Vehicle vehicle) {
        // connect charger
    }
}
```

---

## 🧭 The Most Important OOP Decision — Composition vs Inheritance

This comes up in every LLD interview, explicitly or implicitly. The rule:

```
  IS-A relationship (type hierarchy)?    HAS-A relationship (using a feature)?
           │                                          │
           ▼                                          ▼
       Inheritance                              Composition
  "EVSpot IS-A ParkingSpot"              "ParkingLot HAS-A FeeStrategy"

  Shared implementation is the same.     Behaviour can vary at runtime.
  Can't swap the parent at runtime.      Can swap the strategy/collaborator.
```

### Why Composition Usually Wins

```java
// ❌ Inheritance for pluggable behaviour — breaks at runtime
public class ParkingLot extends HourlyFeeCalculator {
    // Now ParkingLot IS an HourlyFeeCalculator
    // You can never switch to FlatRateFeeCalculator without changing the class
    // ParkingLot exposes all fee-related methods — violates SRP
}
```

```java
// ✅ Composition — ParkingLot HAS-A FeeStrategy
public class ParkingLot {

    private final FeeStrategy feeStrategy;  // injected, swappable

    // In tests: inject MockFeeStrategy
    // At runtime: inject HourlyFeeStrategy or FlatRateFeeStrategy
    public ParkingLot(FeeStrategy feeStrategy) {
        this.feeStrategy = feeStrategy;
    }
}
```

### The Liskov Check — When Inheritance Is Wrong

If a subclass has to throw `UnsupportedOperationException` to override an inherited method, **inheritance is wrong**. That's a Liskov Substitution violation — the subclass can't be substituted for the parent.

```java
// ❌ Classic LSP violation — inheritance used for code reuse, not type hierarchy
public class ReadOnlyList<T> extends ArrayList<T> {

    @Override
    public boolean add(T element) {
        throw new UnsupportedOperationException("Read-only list");
    }
}
// ReadOnlyList IS NOT an ArrayList — you can't substitute it everywhere ArrayList is used
// Fix: implement List<T> directly and delegate read ops to an internal ArrayList
```

### 🔌 Interview Drop-In

> *"I used composition over inheritance here — ParkingLot HAS-A FeeStrategy rather than ParkingLot extending HourlyFeeCalculator. This keeps ParkingLot's scope narrow and lets the fee algorithm be swapped without changing the class. Inheritance is only justified when the subclass IS genuinely a specialization of the parent and can substitute it everywhere."*

---

## 🎨 Visual — The Four Pillars in One System

```
  ENCAPSULATION            ABSTRACTION              POLYMORPHISM
  ─────────────            ───────────              ────────────
  ParkingSpot              FeeStrategy              List<ParkingSpot>
  ┌──────────────┐         ┌──────────────┐         each.isAvailable()
  │ -status      │         │ +calculate() │              │
  │ -vehicle     │         └──────┬───────┘              │
  │ +assign()    │                │implements            ▼
  │ +release()   │    ┌───────────┴──────────┐    each object handles
  │ +isAvailable │    │                      │    itself — no switch
  └──────────────┘    ▼                      ▼
  Only methods       HourlyFee          FlatRateFee
  touch internals    Strategy           Strategy

  INHERITANCE / COMPOSITION
  ─────────────────────────
  AbstractParkingSpot           ParkingLot
  (shared assign/release code)  HAS-A FeeStrategy  ← composition
       ▲        ▲               (not extends HourlyFee)
  CompactSpot  EVSpot
  (IS-A relationship, real)

KEY INVARIANT:
   Use Inheritance for IS-A (real type hierarchy, shared implementation).
   Use Composition for HAS-A (pluggable behaviour, runtime swap, testing).
```

---

## 🧾 TL;DR — Interview Answers in One Line Each

| Concept | One-line answer |
|---|---|
| **Encapsulation** | *"Private fields + public methods — the class enforces its own invariants; callers can't corrupt state."* |
| **Abstraction** | *"Hiding HOW behind WHAT — callers depend on the interface contract, not the implementation details."* |
| **Polymorphism** | *"Same method call, different behaviour by type — eliminates switch-on-type, enables extension without modification."* |
| **Inheritance** | *"IS-A hierarchy for shared implementation — but composition wins when the behaviour needs to vary at runtime or the IS-A relationship is forced."* |
| **Composition vs Inheritance** | *"Composition when the behaviour is pluggable or testable in isolation. Inheritance when the subclass truly IS the parent and can substitute it everywhere."* |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. 4 OOP pillars with LLD-anchored examples. Composition vs Inheritance decision included as the key interview decision. |
