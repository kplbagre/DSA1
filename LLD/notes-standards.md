# Notes Standards — LLD

> Two note types in this folder. Each has its own structure. Read this before writing any note.

---

## Type 1 — Design Pattern Notes (`DesignPatterns/`)

**Purpose:** Build mental model for a pattern — what it is, when to reach for it, how to code it.
**Length target:** 80-120 lines. Concise. Not a textbook.

### Structure (exact order)

```
1. 🎯 What Problem Does This Pattern Solve?   (3-5 lines)
2. 🧠 Mental Model                            (one everyday analogy, 4-6 lines)
3. 🔌 The Interface Contract                  (interface code first, always)
4. ⚙️ Implementation                          (English steps → Java code)
5. 🏢 Real World Usage                        (3+ real companies/products)
6. 🧭 When to Use vs When NOT to Use          (decision table)
7. 🧩 LLD Problems That Use This Pattern      (one bullet per problem — HOW the pattern is used)
8. 🔬 Interview Q&As                          (3-4 questions)
9. 🧾 TL;DR                                   (one interviewer-ready line)
```

**Section 7 format:**
```
## 🧩 LLD Problems That Use This Pattern

- **Parking Lot** — Factory creates `CompactSpot`, `LargeSpot`, `HandicappedSpot` based on `SpotType` enum. Callers never call `new` directly.
- **BookMyShow** — Factory creates seat objects (`GoldSeat`, `SilverSeat`) based on tier. Pricing and capacity differ per type.
- **Rate Limiter** — Factory creates the right limiter (`TokenBucketLimiter`, `SlidingWindowLimiter`) based on config.
```

One bullet = one problem = one sentence on the EXACT role this pattern plays. Not a generic "this problem uses factory" — but "Factory creates X based on Y in this specific problem."

**Key rules:**
- Interface BEFORE implementation — always
- Mental model uses a physical/everyday analogy
- Code is Java 17+, working, not pseudo-code

---

## Type 2 — Problem Notes (`Problems/<name>/`)

### 2a. The Markdown File (`<problem-name>.md`)

**Purpose:** The approach, design decisions, concurrency handling, and interview execution tips.
**Length target:** 120-180 lines.

### Structure (exact order)

```
1.  🎯 Problem Statement                       (2-3 lines — what you're building)
2.  📖 Requirements                            (functional + non-functional, bullet list)
3.  🏗️ Class Design                            (entities, relationships, ASCII class diagram)
4.  🔌 Key Interfaces                          (the interfaces that matter, with javadoc intent)
5.  🧭 Design Decisions                        (why THIS structure — pattern names, trade-offs)
6.  🎨 Visual — Object Interaction             (ASCII sequence/flow diagram)
7.  🖊️ Coding Skeleton                         (interview coding order + class stubs to memorise)
8.  🔁 Concurrency — Making It Thread-Safe     (which fields are shared, which strategy)
9.  📐 "What Would You Do Differently?"        (mandatory — shows senior thinking)
10. 🔬 Interview Q&As                          (4-5 questions specific to this problem)
11. 🧾 TL;DR — 30-Second Pitch                (how you'd introduce this design in an interview)
12. 🔗 Patterns Used                           (links to DesignPatterns/ notes)
```

### 2b. The Java Files

**Purpose:** Runnable implementation. What you'd actually write in an interview.

**Rules:**
- `interfaces/` or `contracts/` conceptually first — define before implementing
- One class per file (exactly as Java convention demands)
- `@Override` always
- No `System.out.println` — use a simple `Logger` or omit output
- Enums for state (e.g., `SpotStatus.AVAILABLE`, `SpotStatus.OCCUPIED`)
- Thread-safety: `synchronized`, `ReentrantLock`, or `ConcurrentHashMap` as needed

**File naming:** `ParkingLot.java`, `ParkingSpot.java`, `Vehicle.java` — PascalCase, one concept per file.

---

## ✅ Pre-Publish Checklist

### For Pattern notes:
- [ ] Mental model has a concrete everyday analogy
- [ ] Interface shown BEFORE any implementation
- [ ] Java code compiles mentally (no syntax errors)
- [ ] "When NOT to use" row exists in trade-offs table
- [ ] ≥ 2 real company examples

### For Problem notes (markdown):
- [ ] Requirements split into functional + non-functional
- [ ] Design decisions explain the WHY (not just the what)
- [ ] Concurrency section present — named strategy used
- [ ] "What would you do differently?" present
- [ ] ≥ 4 interview Q&As

### For Problem notes (Java files):
- [ ] All files would compile in a blank Java project
- [ ] `@Override` on all overridden methods
- [ ] No magic numbers — enums or constants
- [ ] Thread-safe where the markdown says it is

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | Standards file created for LLD folder. |
| June 2026 | Added section 7 🖊️ Coding Skeleton to Problem note structure (post-InMobi gap: coding skeleton was missing). |
