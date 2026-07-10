# DocuSign Interview — LLD Concept Reading Guide

> **Purpose:** Ordered reading list for the 8 LLD concept files you need before any LLD follow-up question.
> These are the vocabulary files — they let you handle NEW problems, not just the 5 you've prepared.
> **Read Tier 1 first.** These apply to every LLD problem regardless of domain.

---

## How to Read Each File

For every file, answer these 3 questions before moving on:
1. **What is the pattern / mechanism?** (name it, draw the class structure in your head)
2. **What problem does it solve?** (what breaks without it?)
3. **Which probe question does this answer?** (the interviewer sentence that triggers this knowledge)

---

## The Universal LLD Pipeline (Run this for every problem)

```
New LLD problem arrives
        │
        ▼
1. Does it have lifecycle? (PENDING → ACTIVE → CANCELLED)  → State pattern
2. Do multiple things react to one event?                  → Observer pattern
3. Is an algorithm swappable?                              → Strategy pattern
4. Are multiple object types created?                      → Factory pattern
        │
        ▼
5. Where is the shared mutable state?
   - Single JVM, compound action    → synchronized
   - Single JVM, single counter     → AtomicInteger
   - State lives in DB              → SELECT FOR UPDATE + @Transactional
   - Distributed (multi-pod)        → Redis Lua script or SET NX
        │
        ▼
6. Draw class diagram → 5-7 boxes, relationships, 2-3 methods each
7. Write the ONE critical method (the concurrency-sensitive one)
8. Name the pattern + explain the concurrency fix
```

Memorise this pipeline. It handles ~90% of LLD problems.

---

## Tier 1 — Apply to EVERY LLD Problem

---

### 1. Concurrency Deep Dive ⭐ Most Important
**File:** [concurrency-deep-dive.md](concurrency-deep-dive.md)

**Probe it answers:** "Why synchronized here?" / "Why not just use AtomicInteger?" / "What's the race condition?" / "How do two threads interact with this?"

**Focus on:**
- [ ] Race condition — what it is, how to reproduce it in a sentence ("two threads read X simultaneously, both see stale value, both write, one write is lost")
- [ ] `synchronized` — when to use: compound actions (check-then-act, read-modify-write)
- [ ] `AtomicInteger` — when to use: single counter increment, NO compound action
- [ ] `ReentrantReadWriteLock` — when to use: many readers, few writers (LRU cache `get()` mutates order → write lock)
- [ ] `ConcurrentHashMap.computeIfAbsent()` — atomic single-entry creation (RateLimiterService per clientId)
- [ ] Deadlock — what causes it, how to prevent (consistent lock ordering)
- [ ] `wait()` / `notify()` — producer-consumer pattern (BlockingQueue wraps this)

**The 5 concurrency weapons — know when to reach for each:**

| Weapon | Use when |
|---|---|
| `synchronized` | Single JVM, compound action (check-then-act) |
| `AtomicInteger` | Single JVM, single counter, no compound action |
| `ReentrantReadWriteLock` | Single JVM, high read / low write ratio |
| `SELECT FOR UPDATE` + `@Transactional` | State lives in DB, multi-pod safe |
| Redis Lua script | Distributed, atomic check+decrement across pods |
| Redis `SET NX` | Distributed, one-time atomic claim (lock, idempotency) |

---

### 2. SOLID Principles
**File:** [DesignPatterns/00-solid-principles.md](DesignPatterns/00-solid-principles.md)

**Probe it answers:** "Why did you use an interface here?" / "How would you add a new channel without changing this class?" / "Is this following single responsibility?"

**Focus on:**
- [ ] **SRP** — one class, one reason to change. `BillingService` charges only; `NotificationService` notifies only. If your class name has "And" in it, it's an SRP violation.
- [ ] **OCP** — open for extension, closed for modification. Adding a new Kafka consumer = zero changes to billing service. Adding a new `EmailDeliveryHandler` = zero changes to `NotificationFanoutService`.
- [ ] **LSP** — subtypes fully substitutable. `StripePaymentProcessor` and `BraintreePaymentProcessor` both honour `IPaymentProcessor` contract without surprises.
- [ ] **ISP** — narrow interfaces. `IPaymentProcessor` has `charge()` + `refund()` only — not Stripe-specific `createPaymentIntent()`.
- [ ] **DIP** — depend on abstraction. `BillingService` → `IPaymentProcessor` (not `StripePaymentProcessor`). Injected via Spring bean — swap by config, not code change.

**The one sentence per principle (say these out loud):**
> SRP: "Each class has one reason to change."
> OCP: "I extend by adding new classes, not editing existing ones."
> LSP: "Any subclass can replace its parent without breaking callers."
> ISP: "Interfaces are narrow — callers only see methods they use."
> DIP: "High-level modules depend on abstractions, not implementations."

---

### 3. Factory + Strategy Patterns
**File:** [DesignPatterns/01-factory-strategy.md](DesignPatterns/01-factory-strategy.md)

**Probe it answers:** "How do you swap the rate limiting algorithm at runtime?" / "How do you create different seat types without if-else chains?" / "How is this extensible to new algorithms?"

**Focus on:**
- [ ] **Strategy** — defines a family of algorithms, encapsulates each, makes them interchangeable. `RateLimiter` interface → `TokenBucketRateLimiter` + `SlidingWindowRateLimiter`. The caller never sees the concrete class.
- [ ] **Factory** — separates object creation from usage. `SeatFactory.create(SeatTier)` → returns `GoldSeat` / `SilverSeat`. Caller doesn't know the concrete type.
- [ ] Why Factory + Strategy appear together: Factory creates the Strategy. `RateLimiterFactory.create(type)` → returns the right `RateLimiter` implementation.
- [ ] How to extend: new algorithm = new class implementing the interface. Zero changes to existing code (OCP).
- [ ] The interview draw: `<<interface>> RateLimiter` ← `TokenBucketRateLimiter`, `SlidingWindowRateLimiter` + `RateLimiterFactory` as a separate box.

---

### 4. Observer Pattern
**File:** [DesignPatterns/02-observer.md](DesignPatterns/02-observer.md)

**Probe it answers:** "How do you notify email and analytics when a booking is made without coupling BookingService to both?" / "How do you add a new notification channel without changing the core service?"

**Focus on:**
- [ ] Subject (Observable) maintains a list of observers. On event: iterate and call `update()` on each.
- [ ] Observer interface: `void onEvent(Event e)` — all observers implement this single method.
- [ ] Decoupling: `Show` (subject) doesn't know whether `EmailObserver` or `AnalyticsObserver` exists. It just calls `notifyObservers()`.
- [ ] Adding new channel: new class implementing `BookingObserver` + register it. Zero changes to `Show`. This IS the OCP payoff.
- [ ] Synchronous vs async: synchronous observer (inline `notify()`) blocks the caller thread. Async (Kafka consumer) decouples timing. Know when to say which.
- [ ] Interview draw: `Show` → `List<BookingObserver>` → `EmailObserver`, `AnalyticsObserver`, `SmsObserver`

---

### 5. State Pattern
**File:** [DesignPatterns/05-state.md](DesignPatterns/05-state.md)

**Probe it answers:** "How do you prevent invalid status transitions?" / "How does the order lifecycle evolve?" / "What happens if someone tries to cancel an already-cancelled subscription?"

**Focus on:**
- [ ] State machine core: `(currentState, event) → nextState`. Pure function. No I/O.
- [ ] Enum-based transition table: `Map<String, Status> TRANSITIONS`. Key = `"FROM_STATUS:EVENT"`. Clean, readable, no if-else chains.
- [ ] `InvalidTransitionException` — thrown when no entry found in the map. Defense-in-depth against invalid writes.
- [ ] Two representations: (1) pure transition map (what we use in B1/CF1), (2) full GoF State pattern with a class per state (overkill for most interview problems — mention it but don't implement it).
- [ ] Append-only event log alongside current state: current state for O(1) reads, event log for SOC 2 audit trail. Name both in any commerce design.

---

## Tier 2 — How You Execute (Draw + Code)

---

### 6. Java Building Blocks for LLD
**File:** [java-building-blocks-for-lld.md](java-building-blocks-for-lld.md)

**Probe it answers:** "Why interface not abstract class?" / "Which collection would you use here?" / "How would you make this thread-safe in Java?"

**Focus on:**
- [ ] **Interface vs Abstract Class** — Interface: pure contract, no state, multiple inheritance. Abstract: shared state + partial implementation, single inheritance. Rule of thumb: "can be" → interface; "is a" with shared code → abstract.
- [ ] **Which collection** — `HashMap` (O(1) get/put, unordered) vs `LinkedHashMap` (insertion/access order, O(1) — LRU uses this) vs `TreeMap` (sorted, O(log N)) vs `PriorityQueue` (min/max heap)
- [ ] **ConcurrentHashMap** — thread-safe map; `computeIfAbsent(key, fn)` is atomic — use for "create once per key" (one RateLimiter per clientId)
- [ ] **ReentrantReadWriteLock** — `readLock()` for reads (multiple threads), `writeLock()` for writes (exclusive). LRU `get()` needs write lock because it mutates DLL order.
- [ ] **BlockingQueue** — producer puts, consumer takes, blocks if empty/full. Used in thread pool, task queue, rate-limited workers.

---

### 7. OOP Concepts
**File:** [oop-concepts.md](oop-concepts.md)

**Probe it answers:** "Why composition over inheritance here?" / "What's the benefit of encapsulation in your design?" / "How does polymorphism apply here?"

**Focus on:**
- [ ] **Encapsulation** — fields private, access via methods. Lets you change internals without breaking callers. `Show.seats` is private — callers use `bookSeat()`, not `seats.get(id)`.
- [ ] **Polymorphism** — code to the interface. `List<BookingObserver> observers` — caller doesn't know if it's email or SMS. Swap at runtime by changing what's in the list.
- [ ] **Composition over Inheritance** — prefer `has-a` over `is-a`. `BillingService` HAS an `IPaymentProcessor`, not IS A `StripePaymentProcessor`. Inheritance creates tight coupling; composition allows swap.
- [ ] **Abstraction** — hide complexity behind an interface. `SeatCounter.decrement(classId)` hides whether it's Redis DECR or optimistic locking.

---

### 8. UML for Interviews
**File:** [uml-for-interviews.md](uml-for-interviews.md)

**Probe it answers:** (Not a probe — this is the DRAWING TOOL. If you draw it wrong, you lose credibility early.)

**Focus on:**
- [ ] **Class box** — 3 rows: ClassName / fields / methods. `+` = public, `-` = private, `#` = protected.
- [ ] **Association** (plain arrow `→`) — A uses B. `BillingService → IPaymentProcessor`
- [ ] **Implementation** (dashed arrow `-->`) — class implements interface. `TokenBucketRateLimiter --|> RateLimiter`
- [ ] **Composition** (filled diamond `◆→`) — A owns B, B can't exist without A. `Show ◆→ Seat`
- [ ] **Aggregation** (hollow diamond `◇→`) — A has B, B can exist independently. `BookingService ◇→ SeatCounter`
- [ ] **Multiplicity** — `1`, `0..*`, `1..*` on association lines. `Show 1 ◆→ * Seat`
- [ ] Interview shortcut: interviewers don't penalise imperfect UML — they care that you HAVE a diagram. Boxes + arrows + interface notation is enough.

---

## Reading Checklist

| # | File | Today | Tomorrow |
|---|---|---|---|
| 1 | [concurrency-deep-dive.md](concurrency-deep-dive.md) ⭐ | [ ] | [ ] |
| 2 | [DesignPatterns/00-solid-principles.md](DesignPatterns/00-solid-principles.md) | [ ] | [ ] |
| 3 | [DesignPatterns/01-factory-strategy.md](DesignPatterns/01-factory-strategy.md) | [ ] | [ ] |
| 4 | [DesignPatterns/02-observer.md](DesignPatterns/02-observer.md) | [ ] | [ ] |
| 5 | [DesignPatterns/05-state.md](DesignPatterns/05-state.md) | [ ] | [ ] |
| 6 | [java-building-blocks-for-lld.md](java-building-blocks-for-lld.md) | [ ] | [ ] |
| 7 | [oop-concepts.md](oop-concepts.md) | [ ] | [ ] |
| 8 | [uml-for-interviews.md](uml-for-interviews.md) | [ ] | [ ] |

---

## Pattern → Problem Map (Memorise This)

| If the problem has... | Reach for... |
|---|---|
| Entity with lifecycle (PENDING → ACTIVE → CANCELLED) | **State machine** (enum transition map) |
| Multiple listeners reacting to one event | **Observer** (Subject + Observer interface) |
| Swappable algorithm (fee calc, rate limit algo, delivery channel) | **Strategy** (interface + implementations) |
| Multiple object types to create | **Factory** (creates the Strategy/object) |
| Shared counter, single JVM | `synchronized` or `AtomicInteger` |
| High read / low write, single JVM | `ReentrantReadWriteLock` |
| State in DB, multi-pod safe | `SELECT FOR UPDATE` + `@Transactional` |
| Distributed atomic decrement | Redis Lua script |
| Distributed one-time claim | Redis `SET NX` |
| One class doing too much | Apply **SRP** — split it |
| Adding feature requires editing class | Apply **OCP** — new class instead |
| Tight coupling to concrete impl | Apply **DIP** — introduce interface |

---

## Interview Execution (Read morning of interview)

**File:** [interview-execution-guide.md](interview-execution-guide.md)

The 60-minute playbook. Read this last, the morning of. Not a concept file — it's the delivery script.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 9, 2026 | File created. 8-file LLD reading guide for DocuSign Commerce Backend interview. Tier 1: concurrency-deep-dive (most important), SOLID, Factory+Strategy, Observer, State. Tier 2: Java building blocks, OOP concepts, UML. Includes universal LLD pipeline (pattern recognition → concurrency fix → class diagram), 5 concurrency weapons table, pattern→problem map. |
