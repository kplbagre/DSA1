# LLD Reading Guide — The Concept Files, In Order

> **Part of the LLD Interview Playbook.** Index: **../README.md**
>
> **Purpose:** the ordered reading list for the Foundation concept files you need before *any*
> LLD follow-up question. These are the **vocabulary files** — they let you handle NEW
> problems, not just the ones you've pre-practised. **Read Tier 1 first;** it applies to every
> LLD problem regardless of domain.

---

## How to Read Each File

For every file, answer these 3 questions before moving on:

1. **What is the pattern / mechanism?** (name it; draw the class structure in your head)
2. **What problem does it solve?** (what breaks without it?)
3. **Which probe does it answer?** (the interviewer sentence that triggers this knowledge)

---

## 🧭 The Universal LLD Pipeline (run this for every problem)

```
New LLD problem arrives
        │
        ▼
1. Does it have a lifecycle? (PENDING → ACTIVE → CANCELLED)  → State pattern
2. Do multiple things react to one event?                    → Observer pattern
3. Is an algorithm swappable? (fee, ranking, limiter)        → Strategy pattern
4. Are multiple object types created?                        → Factory pattern
5. Is it a tree / part-whole hierarchy?                       → Composite pattern
        │
        ▼
6. Where is the shared mutable state?
   - Single JVM, compound action     → synchronized
   - Single JVM, single counter      → AtomicInteger
   - State lives in a DB             → SELECT FOR UPDATE + @Transactional
   - Distributed (multi-pod)         → Redis SET NX (claim) / Lua (atomic RMW)
        │
        ▼
7. Draw the class diagram → 5-7 boxes, relationships, 2-3 methods each
8. Write the ONE critical method (the concurrency-sensitive one)
9. Name the pattern + explain the concurrency fix
```

Memorise this pipeline. It handles ~90% of LLD problems.

---

## Tier 1 — Apply to EVERY LLD Problem

### 1. Concurrency ⭐ Most Important
**File:** [../Foundations/06-concurrency.md](../Foundations/06-concurrency.md)

**Probes it answers:** *"Why `synchronized` here?"* / *"Why not just `AtomicInteger`?"* / *"What's the race condition?"* / *"Now make it multi-node."*

**Focus on:**
- [ ] Race condition — reproduce it in one sentence: "two threads read X, both see stale, both write, one write is lost"
- [ ] `synchronized` — compound actions (check-then-act, read-modify-write)
- [ ] `AtomicInteger` — single-counter increment, no compound action
- [ ] `ReentrantReadWriteLock` — many readers, few writers (LRU `get()` mutates order → write lock)
- [ ] `ConcurrentHashMap.computeIfAbsent()` — atomic create-once-per-key
- [ ] Deadlock — cause + prevention (consistent lock ordering)
- [ ] **Part 3 (distributed):** DB `SELECT FOR UPDATE`, Redis `SET NX`, lease+TTL, two-layer correctness, and *"what it still doesn't guarantee"* (never claim exactly-once)

**The concurrency weapons — know when to reach for each:**

| Weapon | Use when |
|---|---|
| `synchronized` | Single JVM, compound action (check-then-act) |
| `AtomicInteger` | Single JVM, single counter, no compound action |
| `ReentrantReadWriteLock` | Single JVM, high read / low write |
| `SELECT FOR UPDATE` + `@Transactional` | State in DB, multi-pod safe |
| Redis Lua script | Distributed, atomic check+decrement across pods |
| Redis `SET NX` | Distributed, one-time atomic claim (lock, idempotency) |

---

### 2. SOLID Principles
**File:** [../Foundations/02-solid-principles.md](../Foundations/02-solid-principles.md)

**Probes it answers:** *"Why an interface here?"* / *"How would you add a new channel without changing this class?"* / *"Is this single responsibility?"*

**One sentence per principle (say these out loud):**
> **SRP:** "Each class has one reason to change."
> **OCP:** "I extend by adding new classes, not editing existing ones."
> **LSP:** "Any subtype replaces its parent without surprising the caller."
> **ISP:** "Interfaces are narrow — callers only see methods they use."
> **DIP:** "High-level modules depend on abstractions, injected in — not on concretions."

---

### 3. Factory + Strategy Patterns
**File:** [../DesignPatterns/01-factory-strategy.md](../DesignPatterns/01-factory-strategy.md)

**Probes it answers:** *"How do you swap the algorithm at runtime?"* / *"How do you create different types without an if-else chain?"* / *"How is this extensible to new algorithms?"*

**Focus on:**
- [ ] **Strategy** — a family of interchangeable algorithms behind one interface (`FeeStrategy` → `HourlyFeeStrategy` / `FlatRateFeeStrategy`); the caller never sees the concrete class
- [ ] **Factory** — separates object creation from use (`SpotFactory.create(type)` → `CompactSpot` / `LargeSpot`)
- [ ] Why they pair: the Factory often *creates* the Strategy
- [ ] Extend by adding a class implementing the interface — zero changes to existing code (OCP)

---

### 4. Observer Pattern
**File:** [../DesignPatterns/02-observer.md](../DesignPatterns/02-observer.md)

**Probes it answers:** *"How do you notify email and analytics on a booking without coupling the core service to both?"* / *"How do you add a new notification channel without changing the core?"*

**Focus on:**
- [ ] Subject holds a list of observers; on an event it iterates and calls `update()` on each
- [ ] The subject doesn't know which concrete observers exist — it just calls `notifyObservers()`
- [ ] Adding a channel = new class implementing the observer interface + register it. Zero changes to the subject — the OCP payoff
- [ ] Synchronous (inline, blocks caller) vs async (Kafka consumer, decouples timing) — know when to say which

---

### 5. State Pattern
**File:** [../DesignPatterns/05-state.md](../DesignPatterns/05-state.md)

**Probes it answers:** *"How do you prevent invalid status transitions?"* / *"What happens if someone cancels an already-cancelled order?"*

**Focus on:**
- [ ] State machine core: `(currentState, event) → nextState`; pure, no I/O
- [ ] Enum transition table (`Map<"FROM:EVENT", Status>`) — clean, no if-else chains
- [ ] Throw `InvalidTransitionException` when no entry exists — defence against illegal writes
- [ ] Two representations: the pure transition map (use this) vs full GoF class-per-state (mention as overkill)

---

### 6. Composite Pattern
**File:** [../DesignPatterns/07-composite.md](../DesignPatterns/07-composite.md)

**Probes it answers:** *"Design a file system — `ls -r`, `du`."* / *"How do you treat a file and a directory uniformly?"*

**Focus on:**
- [ ] Leaf and container implement the *same* interface; the client never type-checks
- [ ] Leaf = base case; container = recursive case (delegates to children and combines)
- [ ] Transparent vs safe child-management (`add`/`remove` on the interface vs only on the container)

---

## Tier 2 — How You Execute (Draw + Code)

### 7. Java Building Blocks
**File:** [../Foundations/05-java-building-blocks.md](../Foundations/05-java-building-blocks.md)

**Probes it answers:** *"Why interface not abstract class?"* / *"Which collection here?"* / *"How would you make this thread-safe in Java?"*

**Focus on:** Interface vs Abstract Class · which collection (`HashMap` / `LinkedHashMap` / `TreeMap` / `PriorityQueue`) · `ConcurrentHashMap.computeIfAbsent` · `ReentrantReadWriteLock` · `BlockingQueue` · enums-with-behaviour.

---

### 8. OOP Concepts
**File:** [../Foundations/01-oop-concepts.md](../Foundations/01-oop-concepts.md)

**Probes it answers:** *"Why composition over inheritance here?"* / *"What's the benefit of encapsulation?"* / *"How does polymorphism apply?"*

**Focus on:** the 4 pillars + composition-over-inheritance. (For the composition-vs-aggregation *distinction*, go to `../Foundations/04-relationships.md`.)

---

### 9. Class Relationships
**File:** [../Foundations/04-relationships.md](../Foundations/04-relationships.md)

**Probes it answers:** *"Is that composition or aggregation?"* / *"Why reference by ID instead of holding the object?"* / *"Why did you NOT subclass here?"*

**Focus on:** IS-A / HAS-A / USES · the `new`-inside-vs-injected mnemonic · the "necessity ≠ ownership" trap · reference-by-ID across aggregate boundaries.

---

### 10. UML for Interviews
**File:** [../Foundations/07-uml-for-interviews.md](../Foundations/07-uml-for-interviews.md)

**Probe:** (not a probe — this is the DRAWING TOOL; draw it wrong and you lose credibility early.)

**Focus on:** class box (3 rows, `+`/`-`/`#`) · association `→` · implementation `--▷` · composition `◆` · aggregation `◇` · multiplicity (`1`, `0..*`, `1..*`). Interviewers don't penalise imperfect UML — they care that you HAVE a diagram.

---

## Reading Checklist

| # | File | Pass 1 | Pass 2 |
|---|---|---|---|
| 1 | [../Foundations/06-concurrency.md](../Foundations/06-concurrency.md) ⭐ | [ ] | [ ] |
| 2 | [../Foundations/02-solid-principles.md](../Foundations/02-solid-principles.md) | [ ] | [ ] |
| 3 | [../DesignPatterns/01-factory-strategy.md](../DesignPatterns/01-factory-strategy.md) | [ ] | [ ] |
| 4 | [../DesignPatterns/02-observer.md](../DesignPatterns/02-observer.md) | [ ] | [ ] |
| 5 | [../DesignPatterns/05-state.md](../DesignPatterns/05-state.md) | [ ] | [ ] |
| 6 | [../DesignPatterns/07-composite.md](../DesignPatterns/07-composite.md) | [ ] | [ ] |
| 7 | [../Foundations/05-java-building-blocks.md](../Foundations/05-java-building-blocks.md) | [ ] | [ ] |
| 8 | [../Foundations/01-oop-concepts.md](../Foundations/01-oop-concepts.md) | [ ] | [ ] |
| 9 | [../Foundations/04-relationships.md](../Foundations/04-relationships.md) | [ ] | [ ] |
| 10 | [../Foundations/07-uml-for-interviews.md](../Foundations/07-uml-for-interviews.md) | [ ] | [ ] |

---

## Pattern → Problem Map (memorise this)

| If the problem has… | Reach for… |
|---|---|
| Entity with a lifecycle (PENDING → ACTIVE → CANCELLED) | **State machine** (enum transition map) |
| Multiple listeners reacting to one event | **Observer** |
| Swappable algorithm (fee, ranking, limiter, delivery) | **Strategy** |
| Multiple object types to create | **Factory** |
| A tree / part-whole hierarchy (files, menus, org chart) | **Composite** |
| Shared counter, single JVM | `synchronized` or `AtomicInteger` |
| High read / low write, single JVM | `ReentrantReadWriteLock` |
| State in a DB, multi-pod safe | `SELECT FOR UPDATE` + `@Transactional` |
| Distributed atomic decrement | Redis Lua script |
| Distributed one-time claim | Redis `SET NX` |
| One class doing too much | Apply **SRP** — split it |
| Adding a feature requires editing a class | Apply **OCP** — new class instead |
| Tight coupling to a concrete impl | Apply **DIP** — introduce an interface |

---

## The Morning-Of Script

**File:** [execution-guide.md](execution-guide.md) — the 60-minute playbook. Read this last, the morning of the interview. Not a concept file — it's the delivery script.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 9, 2026 | Original created as a DocuSign-branded 8-file reading guide (universal LLD pipeline, concurrency weapons, pattern→problem map). |
| Aug 2026 | **De-branded and moved to `InterviewPlaybook/reading-guide.md`** during the LLD restructure. Removed DocuSign/billing-specific examples; generalised to any LLD problem. Updated all paths to the new `Foundations/` + `DesignPatterns/` structure. Added the Composite pattern (#6) and a dedicated Class Relationships entry (#9). |
