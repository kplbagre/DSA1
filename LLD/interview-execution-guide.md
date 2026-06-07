# LLD Interview Execution Guide

> **The 60-minute playbook.** Knowing patterns is prep. Executing cleanly in 60 minutes is the interview skill. These are different things.
>
> **Use this file:** Read it the morning of any LLD interview. Internalize the minute-by-minute rhythm.

---

## 🎯 What Interviewers Actually Evaluate

From InMobi's rubric and general SDE-2/3 bar:

| They observe | What it signals |
|---|---|
| You ask clarifying questions before coding | You think before building — senior behaviour |
| You name the patterns you're using | You have vocabulary, not just code |
| Your interfaces come before classes | You think in abstractions, not implementations |
| You handle concurrency without being asked | You think about production, not just happy path |
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
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│  MINUTE 5-10 → DESIGN OUT LOUD (before coding)             │
│                                                             │
│  Speak your design, don't type it yet:                      │
│  - "I see these entities: X, Y, Z"                          │
│  - "The core relationship is: X has many Y"                  │
│  - "I'll use Strategy pattern here because the algorithm    │
│     needs to be swappable"                                  │
│  - "My interfaces will be: IParkingLot, IFeeStrategy"       │
│                                                             │
│  Name the pattern BEFORE coding it.                         │
│  Interviewers reward vocabulary.                            │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│  MINUTE 10-40 → CODE THE CORE                              │
│                                                             │
│  Order:                                                     │
│  1. Enums first (SpotType, VehicleType, SpotStatus)         │
│  2. Interfaces / abstract classes                           │
│  3. Core domain classes (Parking Lot, Spot, Vehicle)        │
│  4. Strategy / behaviour implementations                    │
│  5. Main orchestrator class last                            │
│                                                             │
│  Rules:                                                     │
│  - Working code > perfect code                              │
│  - Happy path first, edge cases noted verbally              │
│  - If you get stuck: "I'll stub this for now and come back" │
│  - Talk while you code — silence feels like confusion       │
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│  MINUTE 40-50 → CONCURRENCY (they WILL ask this)           │
│                                                             │
│  Even if they don't ask — bring it up proactively:         │
│  "If this needs to be thread-safe, here's what I'd change:" │
│                                                             │
│  Decision framework:                                        │
│  - Shared mutable field? → synchronized / ReentrantLock     │
│  - Concurrent map? → ConcurrentHashMap                      │
│  - Atomic counter? → AtomicInteger / AtomicLong             │
│  - Read-heavy, write-rare? → ReadWriteLock                  │
│                                                             │
│  What NOT to say: "I'd just add synchronized everywhere"    │
│  What TO say: "I'd identify the shared state first, then    │
│  lock only at that boundary — over-locking kills throughput"│
└─────────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────────┐
│  MINUTE 50-60 → EXTENSIONS & WRAP-UP                       │
│                                                             │
│  "What if we add X?" → answer with design, not code:        │
│  - "That's why I made IFeeStrategy an interface —           │
│     you just add a new implementation"                      │
│  - "Adding EV charging spots is just a new SpotType enum"   │
│                                                             │
│  Proactively answer "what would you do differently?":       │
│  - Shows senior self-awareness                              │
│  - Prevents the interviewer from catching you off guard     │
│                                                             │
│  End with: "Should I add error handling / validation next?" │
└─────────────────────────────────────────────────────────────┘
```

---

## 🔬 The 5 Lines That Score Maximum Points

| When | What to say |
|---|---|
| Before coding | *"I'm going to start with the interfaces — I find it easier to reason about contracts before implementations."* |
| When choosing a pattern | *"I'm using Strategy here because the fee calculation algorithm needs to vary independently of the parking lot."* |
| On concurrency | *"The shared state here is the `availableSpots` map — I'd make that a ConcurrentHashMap and use AtomicInteger for the count."* |
| When asked "what would you change?" | *"If I had more time I'd extract the search logic into a separate Searcher class — right now it's doing too much."* |
| When stuck | *"I'll stub this method for now and come back — I don't want to lose momentum on the core flow."* |

---

## ❌ Mistakes That Kill You

| Mistake | Why it kills |
|---|---|
| Start coding without asking questions | Signals you build before understanding requirements |
| Write implementation classes first, interfaces later | Shows you think bottom-up, not top-down |
| Silence while coding | Interviewer can't follow your thinking |
| Skip concurrency unless asked | Signals you don't think about production |
| "I'd use synchronized everywhere" | Shows you know the tool but not when/where |
| Overdesign — 15 classes for parking lot | Interview code ≠ production code. Ship the skeleton. |
| Never name the pattern | You used Strategy but said "I'll just make this pluggable" — wasted signal |

---

## 🧠 Mental Model for the Whole Hour

> Think of yourself as a senior engineer doing a **30-minute whiteboard design session** followed by a **30-minute implementation sprint**. You're not building production software — you're proving you could.
>
> The interviewer is watching HOW you think, not just WHAT you produce.

---

## 🧾 The Pattern-Naming Cheatsheet

Have these phrases ready — drop them naturally:

| When you're doing... | Say... |
|---|---|
| Making fee calculation pluggable | *"I'm using Strategy pattern — the algorithm varies independently"* |
| Making spots from a factory | *"Factory method here — the creation logic is encapsulated"* |
| Notifying observers of events | *"Observer pattern — decouples the event source from the handlers"* |
| Building a complex object step by step | *"Builder pattern — separates construction from representation"* |
| An object changes behaviour by state | *"State pattern — avoids the if-else chain on status"* |
| Encapsulating a request as an object | *"Command pattern — makes operations undoable and queueable"* |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | Guide created. Informed by InMobi LLD round gaps (no interfaces, no pattern naming, no concurrency). |
