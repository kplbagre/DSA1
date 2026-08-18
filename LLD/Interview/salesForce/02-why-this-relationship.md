# 02 — Why This Relationship?

**Read time: 12 min.** This is the follow-up you are most likely to get wrong, because most
people stop at "HAS-A" and the interviewer's next word is *"composition or aggregation?"*

---

## The three relationships, in the words you say

| Say this | Means | In code |
|---|---|---|
| **IS-A** | substitutable for | `implements` / `extends` |
| **HAS-A** | structurally made of | a field that is *part of* the object |
| **USES** | calls methods on, holds no state | a collaborator, often injected |

**HAS-A always needs a second word: composition or aggregation.** That's the whole file.

---

## Composition vs aggregation: it's about LIFECYCLE, not syntax

Both look identical in Java — a field. The difference is **who owns the lifetime.**

| | **Composition** (filled diamond) | **Aggregation** (hollow diamond) |
|---|---|---|
| Who creates it | The whole creates it | Built elsewhere, handed in |
| When it dies | Dies with the whole | Outlives the whole |
| Shared? | Never — exclusive | Often — shared singletons |
| Analogy | House HAS-A Room | Team HAS-A Player |

### The one-line heuristic (say this verbatim)

> **"If I `new` it inside the constructor, it's composition. If it arrives through the
> constructor, it's aggregation."**

```java
class Booking {
    private final TimeInterval interval;
    Booking(Instant s, Instant e) {
        this.interval = new TimeInterval(s, e);   // COMPOSITION - created here, dies here
    }
}

class NotificationService {
    private final ChannelRouter router;
    NotificationService(ChannelRouter router) {
        this.router = router;                      // AGGREGATION - injected, shared, outlives
    }
}
```

---

## THE TRAP — and it's the one I originally got wrong

> **"It can't function without it"** describes a **required dependency**, NOT ownership.

`NotificationService` is useless without a `ChannelRouter`. Tempting to call that composition.
**It's aggregation** — the router is injected, shared, and has its own lifecycle.

**Necessity ≠ ownership.** Two different questions:
- *Do I need it to work?* -> required dependency
- *Did I create it and does it die with me?* -> composition

**If they push:**
> *"It'd only be composition if the service did `this.router = new DefaultChannelRouter(...)`
> internally — which would also destroy testability, because I could never inject a mock."*

That last clause is gold: it shows the ownership choice has a **practical consequence**, not
just a UML label.

---

## Worked examples from your six problems

| Relationship | Answer | The reason (say this) |
|---|---|---|
| `Booking` — `TimeInterval` | **composition** | "Created with the booking, dies with it, shared with nothing. In SQL it's literally two columns on the booking row — that's what composition looks like when persisted." |
| `Job` — `Schedule` | **composition** | "Created with the job, meaningless without it." |
| `Job` — `RetryPolicy` | **aggregation** | "Deliberately different from `Schedule` on the same class: retry policies are shared singletons — one `ExponentialBackoff` serves thousands of jobs." |
| `Level` — `ParkingSpot` | **composition** | "A spot belongs to exactly one level for its whole existence." |
| `User` — `Session` | **aggregation** | "Sessions expire and get revoked independently. Composition would imply deleting a session means something about the user." |
| `Job` — `JobRun` | **aggregation (1:N)** | "Runs outlive the job definition for audit. If it were composition, deleting the job would cascade away your execution history." |
| Service — `Repository` | **USES** | "It calls it and forgets it. Holds no meaningful state on it. Swapping Postgres for Cassandra changes only DI wiring." |

**The strongest answer in the set** — one field that is *both*:

> *"`DefaultChannelRouter` holds a `Map<ChannelType, NotificationSender>`. The **map object**
> is composition — the router creates it, nobody else references that instance. The
> **senders inside it** are aggregation — they're injected singletons that survive the
> router being replaced."*

---

## Why reference by ID instead of holding the object?

Very common follow-up: *"Why does `Booking` store `roomId` and not a `MeetingRoom`?"*

Three reasons, pick whichever fits:

1. **Different lifecycles** — "Rooms exist without bookings, get renovated, get retired."
2. **Query cost** — "Embedding the object means every booking list drags room graphs along."
3. **Aggregate boundary** — "The booking's job is to record a reservation, not to own room inventory."

Same answer shape for `Ticket` -> `spotId`, and `Booking` -> `organizerId`:
> *"The ticket is a financial record kept for years; the spot is live inventory. Holding the
> object pins live inventory in memory from an archived record."*

---

## IS-A: say "Liskov" and mean it

> *"`EmailSender` IS-A `NotificationSender` because the router calls `.send()` through the
> interface and must never need to know the concrete type. That's Liskov substitution —
> any implementation must be swappable without the caller changing."*

**When IS-A is wrong** — have this ready, it's a classic trap:

> *"I deliberately didn't create `Car`/`Truck`/`Motorcycle` subclasses. No behaviour varies
> by vehicle — a truck doesn't park differently, it just needs a bigger spot. Subclasses
> with nothing overridden are ceremony. What actually varies is the sizing rule, so that's
> data in a compatibility table, not a class hierarchy."*

**Rule:** inheritance is for **behaviour** that differs. If only *data* differs, use a field
or an enum.

---

## The deliberate NON-relationship (bonus points)

Have one ready — it shows you thought about coupling:

> *"`UserPreferences` has no back-reference to the service that loaded it. Adding one creates
> a cycle and drags persistence concerns into a value object."*

> *"`Session` holds no reference to the access token. Access tokens are stateless JWTs that
> aren't stored at all — modelling them as entities would defeat their purpose."*

---

## Answer template for ANY relationship question

```
1. NAME IT        "That's HAS-A."
2. RESOLVE IT     "Aggregation, specifically."
3. LIFECYCLE      "Because it's injected and shared — it outlives this object."
4. CONSEQUENCE    "If it were composition I couldn't inject a mock / it'd cascade-delete
                   my audit history / each instance would open its own connection pool."
```

---

## The 30-second recap

- HAS-A is half an answer — **always** resolve to composition or aggregation
- The test is **lifecycle ownership**: `new` inside = composition, injected = aggregation
- **"Can't work without it" is a required dependency, not ownership** — that's the trap
- Reference by **ID** across aggregate boundaries; different lifetimes mean no ownership
- Inheritance is for varying **behaviour**; if only data varies, use an enum or a field
- Have one **deliberate non-relationship** ready
