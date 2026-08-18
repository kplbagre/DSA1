# 01 — Why an Interface Here?

**Read time: 12 min.** This answers the single most common LLD follow-up.

---

## The core idea: find what varies

An interface is not decoration. It exists at exactly one place: **where behaviour changes.**
That place has a name — the **variation point**.

> **The test:** *"Could there be more than one way to do this, now or soon?"*
> Yes -> interface. No -> plain class or a field.

Two things go behind interfaces:
1. **Behaviour that differs by type** (email vs SMS vs push -> `NotificationSender`)
2. **Behaviour that differs by environment** (in-memory vs Redis -> `CounterStore`)

Everything else is a plain class, a value object, or an enum.

---

## Why not just an if-else? (the question behind the question)

They aren't asking about syntax. They're testing whether you know the **Open/Closed
Principle** — *open to extension, closed to modification.*

```java
// The if-else version
public void send(Notification n) {
    if (n.type == EMAIL)      { /* email logic */ }
    else if (n.type == SMS)   { /* sms logic   */ }
    else if (n.type == PUSH)  { /* push logic  */ }
}
```

**Say this out loud:**
> *"To add Slack, I have to open this method and edit it. Every new channel touches code
> that already works and is already tested — that's the OCP violation. With an interface,
> Slack is one new class and this method never changes."*

Three concrete costs to name if pushed:
- **Regression risk** — you edit a tested method to add an untested branch
- **Merge conflicts** — every team adding a channel edits the same lines
- **Testing** — you can't test Slack without instantiating the whole orchestrator

---

## Why the interface comes BEFORE the implementation

Always draw/write the interface first. It's a signal, and here's the reason to give:

> *"The interface is the contract other components depend on. If I write `EmailSender`
> first and extract an interface later, the interface ends up shaped like email —
> it'll have email-specific methods that SMS has to stub out."*

That's **Dependency Inversion**: high-level policy (the orchestrator) shouldn't depend on
low-level detail (SendGrid). Both depend on the abstraction.

---

## The four interfaces in your six problems, and why each exists

| Interface | What varies | What breaks without it |
|---|---|---|
| `NotificationSender` | how a message is delivered per channel | adding Slack edits the orchestrator |
| `RateLimiter` | the throttling algorithm | a `switch` on the hottest path in the system; per-rule algorithm choice becomes nested ifs |
| `Schedule` | when a job next runs (one-shot vs cron) | "last business day of month" edits the scheduler core |
| `PricingStrategy` | how a fee is computed | pricing is the **most volatile rule in the business** — every rate change is a core-logic deploy |
| `CounterStore` | where state lives (memory vs Redis) | **the class can't be distributed at all** — see below |
| `PasswordHasher` | the hashing algorithm | algorithms age; rotation forces a password reset on every user |

---

## The two best "why" answers you have (memorise these)

### 1. `CounterStore` — the interface that enables distribution

> *"State lives behind `CounterStore`, not as a field on the limiter. That one decision is
> what lets the same class run distributed. If bucket state were a field, each of 50 pods
> would hold its own counter and each would enforce 100/min independently — the client
> actually gets 5,000/min. The system reports full compliance while being wrong by 50x."*

This is strong because the failure is **silent and quantified**.

### 2. `PasswordHasher` — the interface that isn't speculative

> *"Hashing algorithms age and cost factors must rise with hardware. The stored `algorithm`
> field lets old and new hashes coexist: on login I verify with the recorded algorithm and
> transparently re-hash with the current one. Hardcode bcrypt and the eventual migration
> means forcing a password reset on a billion users."*

Strong because it pre-empts *"YAGNI — you don't need that flexibility yet."*

---

## When NOT to create an interface (this scores too)

Restraint is a signal. Have one example ready:

> *"I deliberately didn't create an `AvailabilityTracker` class. It'd be a wrapper around a
> `Map<SpotType, AtomicInteger>` with no behaviour of its own. A class that only holds a
> field and delegates isn't an abstraction, it's ceremony."*

**The rule:** an interface with exactly one implementation and no plausible second one is
speculative generality. Two legitimate exceptions to cite:
- **Testing seam** — you need to inject a fake (`JobRepository`)
- **Known future need** — the roadmap already contains the second impl

---

## Interface vs abstract class

Short answer, keep it short:

> *"Interface when I only need the contract. Abstract class when implementations share
> real code I don't want duplicated — that's Template Method: the skeleton is fixed,
> specific steps vary."*

---

## Enum vs class vs interface — the decision

| Use | When | Example |
|---|---|---|
| **Enum** | closed set, no behaviour of its own | `ChannelType`, `SpotType`, `JobStatus` |
| **Class** | has identity or state that changes | `Booking`, `Ticket`, `User` |
| **Value object** | immutable, defined by its values, owns a rule | `TimeInterval`, `RateLimitDecision` |
| **Interface** | the behaviour varies | `RateLimiter`, `Schedule` |

**The trap they'll set:** *"Why isn't `ChannelType` a class with a `send()` method?"*

> *"Enum-with-behaviour works for three fixed channels, but each channel needs its own
> dependencies — a SendGrid client, a Twilio client. Injecting those into an enum constant
> is awkward in Java and untestable. Separate sender classes keep DI clean."*

---

## The 30-second recap

- An interface marks a **variation point** — the thing that changes
- Justify it with **what breaks otherwise**, never with "cleaner" or "more modular"
- Write the **interface before the implementation**, or it gets shaped by the first impl
- The strongest interfaces enable something structural: `CounterStore` enables distribution
- **Know one place you refused to add an abstraction** — restraint is scored too
