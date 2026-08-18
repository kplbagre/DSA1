# LLD Interview Execution Guide

> **The 60-minute playbook.** Knowing patterns is prep. Executing cleanly in 60 minutes is the interview skill. These are different things.
>
> **Use this file:** Read it the morning of any LLD interview. Internalize the minute-by-minute rhythm.
>
> **Sources:** Informed by InMobi LLD round (Kapil's direct feedback), ashishps1/awesome-low-level-design, hellointerview.com delivery framework, Concept and Coding (Shreyansh) live walkthroughs.
> **Part of the LLD Interview Playbook.** Index: **../README.md**

---

## 🎯 What Interviewers Actually Evaluate

From InMobi's rubric, hellointerview.com framework, and general SDE-2/3 bar:

| They observe | What it signals |
|---|---|
| You ask clarifying questions before coding | You think before building — senior behaviour |
| You name the entities and relationships first | You think in domain models, not code |
| You name the SOLID principle you're applying | You have vocabulary, not just patterns |
| Your interfaces come before classes | You think in abstractions, not implementations |
| You handle concurrency without being asked | You think about production, not just happy path |
| You walk through a use case before coding | You validate design before investing code time |
| You know what you'd do differently | You have a senior relationship with your own work |

---

## 🧭 The 60-Minute Breakdown

```
┌─────────────────────────────────────────────────────────────┐
│  MINUTE 0-5 → REQUIREMENTS GATHERING                        │
│                                                             │
│  Do NOT touch the keyboard. Ask first.                      │
│                                                             │
│  Always ask these 5 questions:                              │
│  1. Scale — how many users/requests concurrently?           │
│  2. Scope — what's in vs out? (e.g., payments? reports?)    │
│  3. Persistence — in-memory OK or need DB?                  │
│  4. Concurrency — multi-threaded or single-threaded?        │
│  5. Extensions — "is there a feature you'll want to add?"   │
│     (This tells you where to put the extension points)      │
│                                                             │
│  Lock in 3-5 functional requirements, 1-2 non-functional.   │
│  Say them back to the interviewer: "So I'm building X that  │
│  does Y and Z. Multi-threaded. Swappable fee logic. Right?" │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│  MINUTE 5-10 → CORE ENTITIES  ← NEW STEP                   │
│                                                             │
│  Identify the NOUNS from your requirements.                 │
│  Nouns → classes/interfaces. Verbs → methods.               │
│                                                             │
│  Do this out loud:                                          │
│  "I see these entities: Vehicle, ParkingSpot, ParkingFloor, │
│   ParkingLot, ParkingTicket, FeeStrategy."                  │
│                                                             │
│  Then name the relationships:                               │
│  - IS-A (inheritance): CompactSpot IS-A ParkingSpot         │
│  - HAS-A (composition): ParkingLot HAS-A List<ParkingFloor> │
│  - USES (dependency): ParkingLot USES FeeStrategy           │
│                                                             │
│  Why this step matters:                                     │
│  - Forces you to think in domain, not in implementation     │
│  - Interviewers reward "domain modeling" vocabulary         │
│  - Catches missing entities before you write 30 lines       │
│    and realize you forgot ParkingTicket                     │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│  MINUTE 10-17 → DESIGN OUT LOUD + USE CASE WALKTHROUGH      │
│                 ← ENHANCED STEP                             │
│                                                             │
│  Speak your design — don't type it yet:                     │
│  - "My interfaces will be: ParkingSpot, FeeStrategy"        │
│  - "I'll use Strategy here — OCP: adding a new fee algo     │
│     is one new class, zero changes to ParkingLot"           │
│  - "Factory for spot creation — DIP: ParkingFloor depends   │
│     on the ParkingSpot interface, not concrete classes"     │
│                                                             │
│  Name the PATTERN and the PRINCIPLE it enforces.            │
│  Don't just say "I'll make this pluggable" — say WHY:       │
│  "Strategy here because Open-Closed: closed for             │
│   modification, open for extension."                        │
│                                                             │
│  THEN — walk through one use case BEFORE coding:            │
│  "Let me trace a car parking: client calls parkVehicle() →  │
│   ParkingLot iterates floors → each floor calls             │
│   findAvailableSpot() → spot is assigned → ticket returned. │
│   Does that flow make sense before I start coding?"         │
│                                                             │
│  This pre-coding walkthrough catches design holes in 2 min  │
│  that would cost 15 min to fix mid-coding.                  │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│  MINUTE 17-45 → CODE THE CORE                               │
│                                                             │
│  Order:                                                     │
│  1. Enums first (SpotType, VehicleType, SpotStatus)         │
│  2. Interfaces / abstract classes                           │
│  3. Core domain classes (Spot, Vehicle, Ticket)             │
│  4. Strategy / behaviour implementations                    │
│  5. Factory class                                           │
│  6. Main orchestrator class last                            │
│                                                             │
│  Rules:                                                     │
│  - Working code > perfect code                              │
│  - Happy path first, edge cases noted verbally              │
│  - If you get stuck: "I'll stub this for now and come back" │
│  - Talk while you code — silence feels like confusion       │
│                                                             │
│  SOLID check while coding (say these out loud):             │
│  - SRP: "Does each class have one reason to change?"        │
│  - OCP: "Can I extend this without modifying it?"           │
│  - LSP: "Can I swap any subclass without breaking callers?" │
│  - ISP: "Are my interfaces lean — no methods clients skip?" │
│  - DIP: "Do I depend on interfaces, not concrete classes?"  │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│  MINUTE 45-55 → CONCURRENCY (they WILL ask this)           │
│                                                             │
│  Even if they don't ask — bring it up proactively:         │
│  "If this needs to be thread-safe, here's what I'd change:" │
│                                                             │
│  Decision framework:                                        │
│  - Shared mutable field? → synchronized / ReentrantLock     │
│  - Concurrent map? → ConcurrentHashMap                      │
│  - Atomic counter? → AtomicInteger / AtomicLong             │
│  - Read-heavy, write-rare? → ReadWriteLock                  │
│  - Observer list? → CopyOnWriteArrayList                    │
│                                                             │
│  What NOT to say: "I'd just add synchronized everywhere"    │
│  What TO say: "I'd identify the shared state first, then    │
│  lock only at that boundary — over-locking kills throughput"│
│                                                             │
│  Granularity upgrade if asked to scale:                     │
│  "Right now I have a coarse lock on the whole parkVehicle() │
│   method. For a large lot, I'd move to per-floor locking — │
│   floors process entries in parallel, contention drops by   │
│   the number of floors."                                    │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│  MINUTE 55-60 → EXTENSIONS & WRAP-UP                       │
│                                                             │
│  "What if we add X?" → answer with design, not code:        │
│  - "That's why I made FeeStrategy an interface —            │
│     you just add a new implementation, zero changes here"   │
│  - "Adding EV spots is one new enum value and one factory   │
│     case — the floor and lot are untouched"                 │
│                                                             │
│  Proactively answer "what would you do differently?":       │
│  - Shows senior self-awareness                              │
│  - Prevents the interviewer from catching you off guard     │
│  - Format: "I'd [specific change] because [specific reason] │
│     — right now [specific trade-off]."                      │
│                                                             │
│  End with: "Should I add error handling / validation next?" │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔬 The 6 Lines That Score Maximum Points

| When | What to say |
|---|---|
| Before coding | *"I'm going to start with the interfaces — I find it easier to reason about contracts before implementations."* |
| When choosing a pattern | *"I'm using Strategy here because of Open-Closed — the fee algorithm needs to vary without me modifying ParkingLot."* |
| When naming an entity relationship | *"ParkingLot HAS-A list of ParkingFloors — composition, not inheritance, because a lot isn't a special kind of floor."* |
| On concurrency | *"The shared state here is the `availableSpots` scan — I'd synchronize parkVehicle() end-to-end. The ticket map is ConcurrentHashMap — no full lock needed there."* |
| When asked "what would you change?" | *"If I had more time I'd move to per-floor locking — right now it's one global lock on parkVehicle(), which is a bottleneck if floors could be processed in parallel."* |
| When stuck | *"I'll stub this method for now and come back — I don't want to lose momentum on the core flow."* |

---

## ❌ Mistakes That Kill You

| Mistake | Why it kills |
|---|---|
| Start coding without asking questions | Signals you build before understanding requirements |
| Skip the "core entities" step | Means you'll discover missing classes mid-coding |
| Write implementation classes first, interfaces later | Shows you think bottom-up, not top-down |
| Name a pattern but not the principle | "I'll use Strategy" — but WHY? Name the SOLID principle it serves |
| Silence while coding | Interviewer can't follow your thinking |
| Skip concurrency unless asked | Signals you don't think about production |
| "I'd use synchronized everywhere" | Shows you know the tool but not when/where |
| Overdesign — 15 classes for parking lot | Interview code ≠ production code. Ship the skeleton. |
| Never walk through a use case | You coded for 30 min, then realise the flow breaks for the unpark scenario |

---

## 🧠 Mental Model for the Whole Hour

> Think of yourself as a senior engineer doing a **3-phase design session**:
> 1. **Domain modeling** (requirements + entities + relationships) — 10 minutes
> 2. **Design contract** (interfaces + patterns + use case walkthrough) — 7 minutes
> 3. **Implementation sprint** (code + concurrency + extensions) — 43 minutes
>
> The first 17 minutes are the multiplier. A bad design coded perfectly still fails. A good design coded imperfectly passes — because the interviewer can see the reasoning.

---

## 🧾 The Pattern-Naming Cheatsheet

Have these phrases ready — drop them naturally:

| When you're doing... | Say... |
|---|---|
| Making spot creation pluggable | *"Factory method — I'm centralising the `new` decision so adding a new type touches one place."* |
| Making fee calculation pluggable | *"Strategy — the algorithm varies independently of ParkingLot. Open-Closed principle."* |
| Notifying observers of events | *"Observer — I'm decoupling the event source from handlers so I can add consumers without touching the publisher."* |
| Building a complex object step by step | *"Builder — separates construction from representation. Good when the object has 5+ optional fields."* |
| An object changes behaviour by state | *"State pattern — avoids the if-else chain on status. Each state is its own class."* |
| Encapsulating a request as an object | *"Command — makes operations first-class objects. Supports undo, queue, and replay."* |

---

## 🧾 The SOLID Principle Drop-In Phrases

Name the principle when you apply it — this is the signal that separates SDE-3 from SDE-2:

| When you're doing... | Say... |
|---|---|
| Splitting a class that does too much | *"Single Responsibility — each class has one reason to change."* |
| Adding a new type without editing existing code | *"Open-Closed — open for extension, closed for modification. That's exactly why I made this an interface."* |
| Making a subclass truly substitutable | *"Liskov Substitution — I can swap any ParkingSpot implementation without the caller noticing."* |
| Making a lean interface | *"Interface Segregation — I'd rather have two focused interfaces than one fat one with methods callers don't use."* |
| Depending on interfaces, not classes | *"Dependency Inversion — ParkingLot depends on FeeStrategy, not HourlyFeeStrategy. Easy to test, easy to swap."* |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | Guide created. Informed by InMobi LLD round gaps (no interfaces, no pattern naming, no concurrency). |
| June 2026 | Three additions from multi-source review (hellointerview.com + ashishps1 + Shreyansh): (1) Explicit "Core Entities" step between requirements and design — identify nouns and IS-A/HAS-A relationships. (2) SOLID principle naming as a required callout alongside pattern naming. (3) Pre-coding use-case walkthrough added to design phase — validate flow before investing code time. Pattern-naming cheatsheet expanded to include explicit SOLID principle phrases. |
| Aug 2026 | Moved to InterviewPlaybook/execution-guide.md during LLD folder restructure; cross-reference paths updated. |
