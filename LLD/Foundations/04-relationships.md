# Class Relationships — IS-A, HAS-A, USES

> **Part of LLD Foundations.** Index + reading order: **../README.md**
>
> **This is the follow-up you are most likely to fumble.** Most people stop at *"it HAS-A
> spot"* — and the interviewer's very next word is *"composition or aggregation?"* This note
> is the canonical treatment. `07-uml-for-interviews.md` shows how to *draw* these; this file
> explains what they *mean* and how to *justify* your choice.

---

## 🎯 Why This Matters

Naming entities is easy. The score comes from naming the **wiring between them** correctly and defending it. There are only three relationships you ever need — and the entire skill is knowing that **one of them is only half an answer**.

```
IS-A    → substitutable for        → implements / extends
HAS-A   → structurally made of     → a field (part of the object)   ← half an answer!
USES    → calls, holds no state    → a collaborator, often injected
```

**HAS-A always needs a second word: composition or aggregation.** That distinction is the heart of this note.

---

## 📖 Terminology

| Term | Plain English |
|---|---|
| **Composition** | The whole *owns* the part — creates it, and it dies with the whole. (Filled diamond ◆ in UML.) |
| **Aggregation** | The whole *references* the part — the part is built elsewhere, handed in, and outlives the whole. (Hollow diamond ◇ in UML.) |
| **Association** | A generic "these two are connected" link; composition and aggregation are its two specific flavours. |
| **Liskov substitution** | A subtype must be usable anywhere its base type is, without surprising the caller (the "L" in SOLID). |
| **Aggregate boundary** | The edge of one self-contained cluster of objects; across it, you reference by ID, not by object. |

---

## 🧠 The Core Idea — HAS-A Splits by *Lifecycle*, Not Syntax

Here is the trap that catches everyone: **composition and aggregation look identical in Java.** Both are just a field. The difference is invisible in the type system — it lives in *who owns the lifetime.*

```
              ┌───────────────────────────────────────────────┐
              │  Both are "a field of type X". Same syntax.    │
              │  The question is: WHO CONTROLS X's LIFETIME?   │
              └───────────────────────────────────────────────┘
                         │                          │
            created INSIDE the whole      handed IN from outside
                         │                          │
                         ▼                          ▼
                 ◆ COMPOSITION                ◇ AGGREGATION
             dies with the whole         outlives the whole
             never shared                often shared
```

### The one mnemonic to remember (say it verbatim)

> **"If I `new` it inside the constructor, it's composition. If it arrives *through* the
> constructor, it's aggregation."**

```java
public class Booking {

    private final TimeInterval interval;

    public Booking(Instant start, Instant end) {
        // COMPOSITION — created here, owned here, dies with the Booking
        this.interval = new TimeInterval(start, end);
    }
}

public class NotificationService {

    private final ChannelRouter router;

    public NotificationService(ChannelRouter router) {
        // AGGREGATION — injected, shared, outlives this service
        this.router = router;
    }
}
```

**The confirming check (the older "delete" mnemonic, kept as a cross-check, not the primary rule):**
*If I delete the whole, should the part die too?* Yes → composition. No → aggregation.
Deleting a `Booking` should delete its `TimeInterval` (meaningless alone) → composition.
Deleting a `NotificationService` must **not** delete the shared `ChannelRouter` → aggregation.

> **Why one primary mnemonic?** Older notes used *"delete parent → child dies"* while others
> used *"new inside vs injected."* They agree in every case — the `new`-vs-injected test is
> just easier to apply while you're actually writing the constructor, so make it primary and
> use the delete test to confirm.

### 🎨 Visual — the three relationships in one diagram

```
   ┌──────────────┐         ◆ composition          ┌──────────────┐
   │  ParkingLot  │◆────────────────────────────────│ ParkingFloor │
   └──────────────┘   floors created by the lot,    └──────────────┘
                       die when the lot is torn down

   ┌──────────────┐         ◇ aggregation           ┌──────────────┐
   │ ParkingLot   │◇────────────────────────────────│  FeeStrategy │
   └──────────────┘   injected, shared, swappable    └──────────────┘
                       — outlives any single lot

   ┌──────────────┐         ──▷ IS-A (inherits)      ┌──────────────┐
   │  CompactSpot │─────────────────────────────────▷│  ParkingSpot │
   └──────────────┘   substitutable for the base     └──────────────┘  (interface)

   ┌──────────────┐         ┄┄> USES (calls)         ┌──────────────┐
   │ BookingSvc   │┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄>│  Repository  │
   └──────────────┘   calls it, holds no state on it └──────────────┘

KEY INVARIANT:
   ◆ filled diamond = owned + dies together (composition)
   ◇ hollow diamond = referenced + independent lifetime (aggregation)
   ──▷ = IS-A (Liskov: subtype swappable for base)
   ┄┄> = USES (transient collaborator, no ownership)
```

---

## ⚠️ THE TRAP — "It Can't Work Without It" ≠ Ownership

This is the single most common mistake, and it *feels* right, which is why it's dangerous.

> **"It can't function without it"** describes a **required dependency**, NOT ownership.

`NotificationService` is useless without a `ChannelRouter`. Tempting to call that composition. **It's aggregation** — the router is injected, shared, and has its own lifecycle. Necessity and ownership are two *different* questions:

- *Do I need it to work?* → a **required dependency** (could be aggregation OR composition)
- *Did I create it, and does it die with me?* → **composition**

**If the interviewer pushes:**
> *"It would only be composition if the service did `this.router = new DefaultChannelRouter()`
> internally — which would also destroy testability, because I could never inject a mock."*

That last clause is gold: it shows the ownership choice has a **practical consequence** (testability), not just a UML label.

> **Lesson learned the hard way (Aug 2026):** the "can't work without it" reasoning was the
> exact mistake that produced a wrong composition/aggregation answer in a real drill. Anchor
> on *lifecycle ownership*, never on *necessity*.

---

## 📖 Worked Examples (general LLD problems)

| Relationship | Answer | The reason (say this) |
|---|---|---|
| `ParkingLot` — `ParkingFloor` | **composition** | "Floors are created with the lot and are meaningless without it; delete the lot, the floors go too." |
| `ParkingLot` — `FeeStrategy` | **aggregation** | "Injected and swappable; one strategy instance can be shared, and it outlives any single lot object." |
| `Booking` — `TimeInterval` | **composition** | "Created with the booking, dies with it. When persisted it's literally two columns on the booking row — that's what composition looks like in SQL." |
| `Job` — `Schedule` | **composition** | "Created with the job, meaningless without it." |
| `Job` — `RetryPolicy` | **aggregation** | "Deliberately different from `Schedule` on the *same* class: retry policies are shared singletons — one `ExponentialBackoff` serves thousands of jobs." |
| `User` — `Session` | **aggregation** | "Sessions expire and get revoked independently; composition would imply deleting a session means something about the user." |
| `Job` — `JobRun` (1:N) | **aggregation** | "Runs outlive the job definition for audit. Composition would cascade-delete your execution history when the job is removed." |
| `Service` — `Repository` | **USES** | "It calls it and forgets it — holds no meaningful state on it. Swapping Postgres for Cassandra changes only the DI wiring." |

**The strongest answer in the set — one field that is *both*:**
> *"`DefaultChannelRouter` holds a `Map<ChannelType, NotificationSender>`. The **map object**
> is composition — the router creates it, nobody else references that instance. The
> **senders inside it** are aggregation — they're injected singletons that survive the router
> being replaced."*

---

## 🔗 Why Reference by ID Instead of Holding the Object?

A very common follow-up: *"Why does `Booking` store a `roomId` and not a `MeetingRoom` object?"* or *"Why does `Ticket` store `spotId`, not a `ParkingSpot`?"*

Three reasons — pick whichever fits:

1. **Different lifecycles** — "Rooms exist without bookings, get renovated, get retired. Holding the object couples two independent lifetimes."
2. **Query cost** — "Embedding the object means every booking list drags full room graphs along."
3. **Aggregate boundary** — "The booking's job is to *record a reservation*, not to *own room inventory*. Holding the live object pins live inventory in memory from an archived record."

> **Rule:** across an aggregate boundary (two clusters with independent lifetimes), reference by **ID**. Within one aggregate (parts owned by the whole), hold the object.

---

## 🧬 IS-A: Say "Liskov" and Mean It — and Know When Inheritance Is *Wrong*

**When IS-A is right:**
> *"`EmailSender` IS-A `NotificationSender` because the router calls `.send()` through the
> interface and must never need to know the concrete type. That's Liskov substitution — any
> implementation is swappable without the caller changing."*

**When IS-A is wrong (a classic trap — have this ready):**
> *"I deliberately did **not** create `Car`/`Truck`/`Motorcycle` subclasses. No behaviour
> varies by vehicle — a truck doesn't *park* differently, it just needs a bigger spot.
> Subclasses with nothing overridden are ceremony. What actually varies is the sizing rule,
> so that's **data** in a compatibility table (or an enum), not a class hierarchy."*

> **The rule:** inheritance is for **behaviour** that differs. If only **data** differs, use a
> field or an enum. A subclass that overrides nothing is a smell.

This is also why **composition is usually preferred over inheritance**: inheritance is a rigid, compile-time "is-a" bond that exposes the parent's internals to the child and locks the hierarchy; composition is a flexible "has-a"/"uses" bond you can rewire (and inject/mock) at runtime. Reach for inheritance only when there is genuine substitutable, overriding behaviour.

---

## 🚫 The Deliberate NON-Relationship (bonus points)

Have one ready — it signals you thought about **coupling**, not just connections:

> *"`UserPreferences` has no back-reference to the service that loaded it. Adding one creates a
> reference cycle and drags persistence concerns into a value object."*

> *"A `Session` holds no reference to the access token. Access tokens are stateless JWTs that
> aren't stored at all — modelling them as entities would defeat their purpose."*

Knowing which links **not** to draw is as senior as knowing which to draw.

---

## 🧭 Answer Template for ANY Relationship Question

```
1. NAME IT       "That's HAS-A."
2. RESOLVE IT    "Aggregation, specifically."
3. LIFECYCLE     "Because it's injected and shared — it outlives this object."
4. CONSEQUENCE   "If it were composition I couldn't inject a mock / it'd cascade-delete
                  my audit history / each instance would open its own connection pool."
```

Steps 3 and 4 are what separate a senior answer from a textbook one: you tie the label to **lifecycle** and then to a **practical consequence**.

---

## 🧾 TL;DR

- **HAS-A is half an answer** — always resolve to composition or aggregation.
- **The test is lifecycle ownership:** `new` inside the constructor = composition; injected = aggregation. (Confirm with: delete the whole — should the part die?)
- **"Can't work without it" is a required dependency, not ownership** — that's the trap.
- **Reference by ID** across aggregate boundaries (independent lifetimes); hold the object within one aggregate.
- **Inheritance is for varying behaviour**; if only data varies, use an enum or a field. Prefer composition over inheritance.
- Have one **deliberate non-relationship** ready.

**Related:** OOP pillars and composition-over-inheritance depth → **01-oop-concepts.md**. Liskov in full → **02-solid-principles.md**. How to draw these on a whiteboard → **07-uml-for-interviews.md**.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 2026 | **Created as the canonical relationships note** during the LLD restructure. Merges the composition-vs-aggregation material that was previously fragmented across `oop-concepts.md`, `uml-for-interviews.md`, `salesForce/02-why-this-relationship.md`, and the salesForce cheatsheet. **Reconciled the two conflicting mnemonics** — `new`-inside-vs-injected is now primary; delete-parent-dies is the confirming cross-check. Kept the justification-first framing (name → resolve → lifecycle → consequence) and the "necessity ≠ ownership" trap. |
