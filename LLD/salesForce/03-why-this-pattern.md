# 03 — Why This Pattern?

**Read time: 15 min.** Patterns are not the answer. **The justification is the answer.**
Naming "Strategy" scores nothing; saying what breaks without it scores everything.

---

## The universal answer shape

Whatever pattern they ask about, answer in this order:

```
1. WHAT VARIES     "What changes here is [X]."
2. THE ISOLATION   "So [X] goes behind [pattern]."
3. THE ALTERNATIVE "The alternative would be [strongest option], but that [fails how]."
```

Step 3 is what separates a real answer from a memorised one. **Never compare against a
strawman** — pick the option a smart engineer would actually defend.

---

## The patterns you actually use, with their justification

### Strategy — by far your most-used

**Trigger:** "supports multiple X", "configurable", behaviour differs by type.

> *"The algorithm varies at runtime, so it goes behind an interface with one implementation
> per variant. Adding a variant is one new class and zero edits to existing code."*

Where: `NotificationSender`, `RateLimiter`, `Schedule`, `PricingStrategy`, `AllocationStrategy`,
`PasswordHasher`, `RetryPolicy`, `LockStrategy`, `AuthProvider`.

**"Isn't that over-engineering?"** — the honest answer, using pricing:
> *"Pricing is the most volatile rule in a parking business — weekends, events, EV surcharges,
> validation discounts. It's the one place I'd insist on a strategy even at small scale,
> because the alternative is every rate change touching core code. Contrast the availability
> counters, where I deliberately didn't create a class."*

Pairing a "yes here" with a "no there" proves it's judgement, not reflex.

---

### Registry — how Strategy avoids a switch

**Trigger:** you must pick an implementation by key.

```java
public JobExecutor(List<JobHandler> handlers) {          // DI injects all impls
    this.handlers = handlers.stream()
        .collect(Collectors.toMap(JobHandler::getType, h -> h));
}
```

> *"Handlers self-register via dependency injection, so the scheduler never imports business
> code. Without it, a `switch (jobType)` forces the scheduler module to compile against every
> team's code — the dependency direction is backwards and independent deploys die."*

**Important correction to remember:** the OCP benefit comes from the **injected-list-to-map**
trick, *not* from the registry being a separate class. If asked "why a separate router class
then?" — the honest answer is cohesion (a home for selection logic that grows) and the clean
LLD-class -> HLD-service mapping, **not** extensibility.

---

### Value Object — immutable, owns a rule

**Trigger:** a tuple that travels together; a rule that must exist in exactly one place.

> *"`TimeInterval` owns `overlaps()`. If I used two raw timestamps, the overlap comparison
> gets rewritten at every call site, and the boundary case — does 10-11 conflict with 11-12? —
> gets answered differently in different places. One class, one tested method, half-open
> semantics `[start, end)`."*

Also: `RateLimitDecision` — *"returning a bare boolean throws away the metadata the caller
needs for `X-RateLimit-Remaining` and `Retry-After`. Once you need three facts back, it's an object."*

---

### Repository — isolate persistence

> *"Persistence changes for different reasons than business logic — that's SRP. It's also
> the seam that lets the due-query become a sharded scan later, and the seam for testing
> without a database."*

---

### Factory — rebuild typed objects from stored data

> *"The DB stores `('CRON', '0 */2 * * *')` as data, not a serialized object, so adding a
> schedule type needs no data migration. `ScheduleFactory` converts data back into types.
> This is the one place a `switch` is fine — it's a factory at the persistence boundary."*

---

### Composite — trees treated uniformly

> *"Filters combine: capacity >= 8 AND has projector. Composable predicates beat a method
> with six nullable params, which is unreadable at the call site and untestable in combination."*

Also structural: `ParkingLot -> Level -> ParkingSpot`.

---

### State — but as a guarded enum (know when to upgrade)

Your files use an enum with a transition guard, **not** State classes:

```java
public boolean canTransitionTo(JobStatus next) {
    return ALLOWED.getOrDefault(this, Set.of()).contains(next);
}
```

> *"The behaviour that differs per state lives in the executor, not on the status object.
> State classes here would be eight classes whose only job is validating transitions.
> I'd promote it to a real State machine the moment entering a state carries side effects —
> like emitting a webhook on entry to FAILED."*

**Naming your own upgrade trigger is the scoring move.**

**When you SHOULD use real State:** Elevator, vending machine, order lifecycle — where each
state genuinely answers the same method differently.

---

## Patterns you DON'T use — and when you should

Your six problems never needed these. A different problem will. **Don't force Strategy onto a
problem that wants Observer.**

| If the requirement says | Use | Classic problem |
|---|---|---|
| "notify subscribers when X changes" | **Observer** | Chat, stock ticker, auction |
| "undo/redo", "queue an action", "replay" | **Command** | Text editor, vending machine |
| "add behaviour at runtime, stackable" | **Decorator** | Coffee shop, pizza, I/O streams |
| "many optional construction params" | **Builder** | Complex request objects |
| "wrap an incompatible third-party API" | **Adapter** | Payment gateway integration |
| "lazy-load / cache / access-control in front" | **Proxy** | Image loading, ORM refs |
| "traverse without exposing structure" | **Iterator** | Custom collections |
| "one operation over a varied object tree" | **Visitor** | Expression/AST evaluation |
| "many objects coordinating pairwise" | **Mediator** | Air traffic control, chat room |
| "skeleton fixed, steps vary" | **Template Method** | Job execution, report generation |

**The test before naming any pattern:** *"What varies, and does this pattern isolate exactly
that variation?"* Can't answer in one sentence -> you picked from memory, not from requirements.

---

## "Isn't this just Observer?" — the notification trap

They will ask this on the notification problem. The answer:

> *"There's an Observer flavour — one event fans out to many channels. But the interesting
> variation isn't *who gets notified*, it's *how each channel delivers*, and that's Strategy.
> Pure Observer would have channels subscribing to the service, which means the service holds
> subscriber state and I lose the per-channel retry and rate-limit logic that lives in each sender."*

---

## The three SOLID principles you'll actually cite

| Principle | Say it as | Where |
|---|---|---|
| **OCP** — open/closed | "Adding a channel is one new class, zero edits" | Every Strategy |
| **SRP** — single responsibility | "Persistence changes for different reasons than dispatch" | Repository split |
| **DIP** — dependency inversion | "The orchestrator depends on the interface, not on SendGrid" | Every injected interface |

Two more, if they come up:
- **LSP** — "any sender is substitutable; the router never checks the concrete type"
- **ISP** — "I'd rather two small interfaces than one that forces empty implementations"

---

## Over-engineering defence (you WILL be challenged)

Structure the answer in three beats:

1. **Concede the principle** — *"Fair challenge, and I'd collapse this if [condition]."*
2. **Name the concrete driver** — *"But the stated requirement is [X], and without this, [Y] breaks."*
3. **Show restraint elsewhere** — *"For contrast, I deliberately didn't create [Z] because it'd be a wrapper with no behaviour."*

Beat 3 is what proves it's judgement rather than pattern-spraying.

---

## The 30-second recap

- Never name a pattern without saying **what varies** and **what breaks without it**
- Compare against the **strongest** alternative — beating a strawman proves nothing
- Check the benefit is produced by the thing you're crediting (registry vs injected-list)
- Have one place you **refused** a pattern, ready to cite
- If a problem wants Observer/Command/Decorator, **use it** — don't force Strategy
- Name your **upgrade trigger** for enum -> real State
