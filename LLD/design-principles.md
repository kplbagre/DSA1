# Design Principles

> **Read before any problem note.** These 5 principles — KISS, DRY, YAGNI, Separation of Concerns, and Law of Demeter — are the vocabulary interviewers expect you to drop naturally when explaining design decisions. You already know SOLID (see `DesignPatterns/00-solid-principles.md`). These are the non-SOLID principles that complete the picture.
>
> **Cross-reference:** SOLID principles → `DesignPatterns/00-solid-principles.md`

---

## 🎯 Why These Principles Matter in an Interview

Naming a design pattern is half the answer. The other half is the WHY — naming the principle the pattern enforces. "I used Strategy here because Open-Closed — but also because adding a new algorithm doesn't clutter ParkingLot with if-else, which would violate KISS." Principles give you language to justify decisions under drill-down.

---

## 🔹 1. KISS — Keep It Simple, Stupid

### 🎯 What Problem Does It Solve?

Over-engineering. Adding abstractions "for future flexibility" that nobody needs today. Every extra layer of indirection is complexity the next reader must navigate.

### 🧠 Mental Model

A camping stove has two settings: On and Off. A restaurant oven has 12 burners, 3 ovens, a salamander grill, and a sous vide unit. If you're cooking scrambled eggs at home, the camping stove is better. KISS says: build the camping stove, not the restaurant kitchen, until you actually need the restaurant kitchen.

### ⚙️ Violation vs Fix

```java
// ❌ KISS violation — over-engineered for a 3-person parking lot prototype
public interface ParkingSpotSelectionStrategy {
    ParkingSpot select(List<ParkingSpot> spots, Vehicle vehicle, SelectionContext context);
}

public class SelectionContext {
    private final ZonedDateTime arrivalTime;
    private final PricePreference pricePreference;
    private final AccessibilityRequirement accessibility;
    // ... 5 more fields
}
// Nobody asked for PricePreference. The interviewer said "basic parking lot."
```

```java
// ✅ KISS fix — build exactly what was asked
public ParkingSpot findAvailableSpot(VehicleType vehicleType) {
    for (ParkingFloor floor : floors) {
        ParkingSpot spot = floor.findAvailableSpot(vehicleType);
        if (spot != null) {
            return spot;
        }
    }
    return null;
}
// Strategy can be introduced later if "different selection algorithms" become a requirement.
```

### 🔌 LLD Interview Application

> *"I'm keeping the spot-selection logic inline for now — KISS. If the interviewer asks 'can we add a priority-based selection?', that's the moment I extract it to a Strategy interface."*

---

## 🔹 2. DRY — Don't Repeat Yourself

### 🎯 What Problem Does It Solve?

Duplicated logic in multiple places means a bug fixed in one place silently remains in the others. When you change the fee calculation formula and it exists in 3 files, you need to find all 3. Miss one, and you have inconsistent behaviour.

### 🧠 Mental Model

Imagine a law firm where every lawyer keeps a personal copy of the tax code. When the government updates the law, each lawyer independently (and inconsistently) updates their copy. DRY says: one source of truth. Every lawyer reads from the same updated copy.

### ⚙️ Violation vs Fix

```java
// ❌ DRY violation — fee calculation logic duplicated in two places
public class ParkingLot {

    public long calculateFeeForCar(long minutes) {
        // hourly rate hardcoded here
        return (minutes / 60) * 20L + (minutes % 60 > 0 ? 20L : 0L);
    }
}

public class ParkingReport {

    public long calculateFeeForReport(long minutes) {
        // same formula duplicated — if rate changes, this is missed
        return (minutes / 60) * 20L + (minutes % 60 > 0 ? 20L : 0L);
    }
}
```

```java
// ✅ DRY fix — single source of truth
public class HourlyFeeStrategy implements FeeStrategy {

    private static final long RATE_PER_HOUR = 20L;

    @Override
    public long calculate(long minutes) {
        long hours = (minutes / 60) + (minutes % 60 > 0 ? 1 : 0);
        return hours * RATE_PER_HOUR;
    }
}
// ParkingLot and ParkingReport both use HourlyFeeStrategy.calculate()
// Change the rate in one place → both updated automatically
```

### 🔌 LLD Interview Application

> *"I extracted fee calculation to `FeeStrategy` — DRY. The report generator and the ticket printer both call the same strategy. One change propagates everywhere."*

---

## 🔹 3. YAGNI — You Aren't Gonna Need It

### 🎯 What Problem Does It Solve?

Building features that might be useful someday but aren't in the current requirements. Every line of code you write is code you must maintain, test, and explain. Speculative features increase surface area without delivering value today.

### 🧠 Mental Model

A contractor building your kitchen asks: "Should I pre-wire for a second dishwasher — just in case you want one in 5 years?" Unless you specifically asked for it, the answer is no. That pre-wiring adds cost now for a benefit that may never materialise. Build what was asked. Extend later if needed.

### ⚙️ Violation vs Fix

```java
// ❌ YAGNI violation — adding multi-currency support "for the future"
// when the interviewer said "assume payments are in INR"
public class Ticket {

    private final long amount;
    private final Currency currency;       // ← not asked
    private final ExchangeRate exchangeRate; // ← not asked
    private final String internationalFormat; // ← not asked

    // Nobody asked for this. Every future reader must understand why it's here.
}
```

```java
// ✅ YAGNI fix — build what the problem requires
public class Ticket {

    private final String ticketId;
    private final long amountInPaise;   // INR, as specified
    private final Instant issuedAt;
    private final String vehicleNumber;
}
// If multi-currency becomes a requirement, add it then with a real use case.
```

### 🔌 LLD Interview Application

> *"I haven't added EV spot logic — YAGNI. The requirements don't mention it. If the interviewer asks 'can we extend this?', I'll show the Factory extension point — but I won't build it until asked."*

---

## 🔹 4. Separation of Concerns (SoC)

### 🎯 What Problem Does It Solve?

When one class mixes unrelated responsibilities — business logic, email sending, database access, logging — changing any one concern forces you to understand and retest all the others. SoC says: each class/module handles one topic. Changes to email never affect fee calculation.

### 🧠 Mental Model

A hospital has separate departments: Emergency, Radiology, Pharmacy, Billing. Each department focuses on one concern. The billing department doesn't need to know how an X-ray is read. If billing changes its software, Radiology is unaffected. Separation of Concerns is the organisational principle that keeps departments autonomous.

In code: `ParkingLot` handles parking logic. `EmailService` handles notifications. `FeeCalculator` handles money. `TicketRepository` handles persistence. They're separate classes with separate test files and separate reasons to change.

### ⚙️ Violation vs Fix

```java
// ❌ SoC violation — one class does parking, fees, email, AND persistence
public class ParkingLot {

    public Ticket parkVehicle(Vehicle vehicle) {
        // concern 1: find a spot
        ParkingSpot spot = findAvailableSpot(vehicle.getType());

        // concern 2: generate ticket
        Ticket ticket = new Ticket(vehicle, spot, Instant.now());

        // concern 3: persist to DB (mixes data access with business logic)
        database.save(ticket);

        // concern 4: send email (mixes notification with parking logic)
        emailService.send(vehicle.getOwnerEmail(), "You are parked at " + spot);

        return ticket;
    }
}
```

```java
// ✅ SoC fix — each class owns one concern
public class ParkingLot {
    // Only concern: find a spot and issue a ticket
    public Ticket parkVehicle(Vehicle vehicle) {
        ParkingSpot spot = findAvailableSpot(vehicle.getType());
        return new Ticket(vehicle, spot, Instant.now());
    }
}

public class BookingOrchestrator {
    // Orchestrates across concerns — but delegates each to a specialist
    public void handlePark(Vehicle vehicle) {
        Ticket ticket = parkingLot.parkVehicle(vehicle);
        ticketRepository.save(ticket);
        notificationService.sendParkConfirmation(vehicle.getOwnerEmail(), ticket);
    }
}
```

### 🔌 LLD Interview Application

> *"I'm not putting email logic in ParkingLot — Separation of Concerns. ParkingLot handles parking. If notification logic changes — SMS instead of email — ParkingLot doesn't need to be touched."*

---

## 🔹 5. Law of Demeter — "Talk Only to Direct Friends"

### 🎯 What Problem Does It Solve?

When code chains method calls across multiple objects (`ticket.getSpot().getFloor().getBuilding().getName()`), it tightly couples to the entire chain's internal structure. Change any middle object's API, and you break every caller that traversed through it. The Law of Demeter (also called the "principle of least knowledge") says: a method should only call methods on objects it directly knows about.

### 🧠 Mental Model

You need the postcode for a delivery. You could: ask your manager → who asks HR → who asks IT → who queries the database. That's a 4-hop chain. Or: you call the delivery system directly and ask for the postcode. Talk to your direct contact, not to their contacts' contacts. Each extra hop is a dependency you didn't need.

### ⚙️ Violation vs Fix

```java
// ❌ Law of Demeter violation — chaining through 4 objects
public String getLocationName(Ticket ticket) {
    // ParkingService knows about Ticket, but it shouldn't need to know
    // about Spot → Floor → Building → getName()
    return ticket.getSpot().getFloor().getBuilding().getName();
}
// If Floor adds a wrapper class between Floor and Building, this breaks.
```

```java
// ✅ Law of Demeter fix — ask the direct friend for what you need
public class Ticket {

    private final ParkingSpot spot;

    // Ticket exposes what callers need — delegates internally
    public String getLocationName() {
        return spot.getLocationName();
    }
}

public class ParkingSpot {

    private final ParkingFloor floor;

    public String getLocationName() {
        return floor.getLocationName();
    }
}
// ParkingService calls ticket.getLocationName() — one hop, one dependency
```

### 🔌 LLD Interview Application

> *"I'm adding `getLocationName()` on Ticket rather than chaining through Spot → Floor → Building. Law of Demeter — the caller only knows about Ticket, and Ticket knows about Spot. No caller should traverse three levels of object graph."*

---

## 🧾 TL;DR — Drop-In Phrases for Interviews

| When you're doing... | Say... |
|---|---|
| Keeping the design simple, not adding unused abstractions | *"KISS — I'll introduce the abstraction when the requirement arrives, not before."* |
| Extracting shared logic to avoid duplication | *"DRY — one source of truth. Change the rate in one place, every caller picks it up."* |
| Not building a feature that wasn't asked for | *"YAGNI — I'll stub this method for now. Adding it without a requirement costs maintenance with no current value."* |
| Splitting a bloated class into specialists | *"Separation of Concerns — ParkingLot handles parking; I'll push email to NotificationService so neither is coupled to the other."* |
| Replacing a chain of `.get()` calls | *"Law of Demeter — I'd rather ask Ticket for the location name than chain through Spot, Floor, Building."* |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. 5 non-SOLID principles with LLD-specific code examples and interview drop-in phrases. |
