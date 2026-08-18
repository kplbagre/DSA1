# Notes Standard — JPMC Round 3 Interview Problems

> **What this covers:** The 5 problem notes for the JPMC post-DSA design round.
> Each note covers **one full 1-hour arc** — LLD first, then HLD — matching how JPMC drives it.
>
> **File location for each problem:** `Interview/JPMorgan/Round3/Problems/<problem-name>.md`
>
> **What's NOT in these notes** (stripped by design):
> Prerequisites, "how to use this file", memory anchors, concept analogies,
> "Real World companies" lists, "When to Use vs Not" tables, Further Reading.
> Those belong in concept notes. These are execution notes.
>
> **Emoji palette:** This standard uses emojis beyond the universal AGENTS.md set.
> They are declared in `Interview/JPMorgan/AGENTS.md`.

---

## 📐 Section Order (Exact — every problem note follows this)

```
1.  🎯  Problem Statement             (2-3 lines)
2.  ❓  Clarifying Questions          (10-15, categorized)
3.  🏗️  LLD — Class Diagram           (ASCII class structure)
4.  🧭  LLD — Design Decisions        (table: decision / why / rejected alternative)
5.  🔌  LLD — Key Interfaces          (contract only — no implementation)
6.  ⚙️  LLD — Code to Write           (ONLY the 1-3 methods JPMC will ask)
7.  🔁  LLD — Concurrency             (what's shared, which strategy, 1 code block)
8.  🧨  Java Depth Probes             (your phrase → their question → your answer)
9.  🌐  HLD — Component Diagram       (ASCII full stack: client → LB → services → DB)
10. 🏛️  HLD — Decisions               (table: component / why / alternative considered)
11. 📡  HLD — API Design              (2-3 key endpoints with idempotency / contracts)
12. 🛤️  HLD — Happy + Unhappy Paths  (end-to-end flow for both)
13. 🔧  HLD — Fault Tolerance         (per external call: what breaks, what you add)
14. 🔬  Interview Q&As                (5-7, mix of surface + JPMC probes)
15. 🧾  TL;DR — 30-Second Pitch       (one paragraph — how you open this problem)
16. 🔄  Changelog
```

---

## 📏 Section-by-Section Rules

### §1 — 🎯 Problem Statement
**Length:** 2-3 lines.
State what you are building, not what you'll design. Name the real-world product.

```markdown
Design a movie ticket booking system (think BookMyShow). Users browse shows,
pick seats, pay, and get a booking confirmation. Multiple users can attempt
to book the same seat simultaneously.
```

---

### §2 — ❓ Clarifying Questions
**Length:** 10–15 questions, grouped into categories.
Write these as if you are asking the interviewer. Do NOT skip this section —
JPMC accounts explicitly say 10–15 questions signals SDE-3 maturity.

**Required categories (always present):**
```
Scale          — DAU, peak QPS, data size, read:write ratio
Functional     — what's in scope, what's out (what NOT to build)
Consistency    — strong vs eventual, what's acceptable to be stale
Latency SLA    — what response time is acceptable?
Third-party    — any external calls? their SLA/timeout?
Failure model  — retry acceptable? can the user re-initiate?
Compliance     — any data residency, audit, or security constraint?
```

**Format:**
```markdown
## ❓ Clarifying Questions

**Scale:**
1. How many users are booking concurrently at peak? (10K? 100K?)
2. Is this a single city or national? How many shows/day?
3. Read-heavy (browsing) vs write-heavy (booking)?

**Functional scope:**
4. Does "booking" include payment, or is that a separate service?
5. Do we handle cancellations and refunds in this design?

**Consistency:**
6. Is it acceptable to show a seat as available and then fail the booking?
   (i.e., optimistic display + locking at payment time)

**Third-party:**
7. Does payment go through an external gateway? What's its SLA?
   (The answer drives whether we need async + tracking or sync)

...and so on to 10-15.
```

---

### §3 — 🏗️ LLD — Class Diagram

**§3 has two sub-sections, in this order:**

```
§3a — 📐 Construction Guide  (HOW to build the diagram in an interview)
§3b — ✅ Complete Diagram    (the answer key — full ASCII class diagram)
```

**The guide comes first. The diagram comes last.** The reader uses §3a as a thinking
scaffold during real preparation; §3b is the reference to check against.

---

#### §3a — 📐 Construction Guide

**Mandatory. 7 moves, each with a "board snapshot" showing what you've drawn so far.**

**The 7 moves (apply to every problem, adapted to the archetype):**

| Move | What you do | What you say out loud |
|---|---|---|
| 1 | List domain nouns — don't draw yet | "Let me identify the core entities first." Read the problem statement; extract nouns. |
| 2 | Classify each noun: entity / enum / service | "I'll make X an enum — finite values, compile-time contract." |
| 3 | Draw enums first. Explain non-obvious states. | Walk the state path. Defend every non-obvious state (e.g., HELD, SEAT_LOCKED). |
| 4 | Draw entities smallest → largest. Name what each knows + can do. | "The non-obvious field is X. The key method is Y — it guards Z." |
| 5 | Identify variable behavior. Extract interfaces. | "I'm extracting X as an interface because the implementation varies — LLD vs HLD, prod vs test." |
| 6 | Add the orchestrating service last. Its constructor deps = your design. | "I'm injecting all strategies — not instantiating them. That's DIP." |
| 7 | Name the hot resource. One sentence tying all locks to it. | "The hot resource is X.field. My entire locking strategy guards that check-then-assign." |

**Board snapshot rule:** After each move, show a small ASCII sketch of what
you'd have drawn on the board at that point — NOT the full final diagram.
The snapshot should be ≤ 15 lines. It shows the interviewer's view of your
board as you think out loud.

**Format for each move:**

````markdown
**Move N — [verb phrase describing the move]**

[1-2 sentences: what to do + what to say]

[Explanation of the key decision at this move — the one JPMC will probe]

> **Interviewer:** "..."
> **Answer:** ...

**Your board at the end of Move N:**

```
[Small ASCII snapshot — what you've drawn so far, not the full diagram]
```
````

**After all 7 moves:** include the 75% rule — a priority list of what to draw
first if time is short. Format:

```
Priority 1 — must reach (10 min):  [list the 4-6 elements that carry SDE-3 signal]
Priority 2 — draw if time allows:  [2-3 secondary elements]
Priority 3 — verbally mention, never draw:  [what to skip]
```

---

#### §3b — ✅ Complete Diagram

**Mandatory ASCII.** Show every class with its key fields and methods.
Show relationships: `1..*`, `uses`, `implements`, inheritance.

**Label it:** `### ✅ Complete Diagram — What You're Building Toward`

**Rule:** Box width ≤ 80 chars. Use `┌ ┐ └ ┘ ─ │ ├ ┤` for boxes, `→ ←` for
references, `▼` for inheritance/implements. Close with KEY INVARIANT.

**What to include:** Every entity class, every interface, every key collaborator.
**What to skip:** Getters, setters, constructors, `toString()`.

---

### §4 — 🧭 LLD — Design Decisions
**This is the centerpiece of the LLD section.**
One row per decision. Every row must have all three columns.

**Format:**
```markdown
| Decision | Why this | What I rejected and why |
|---|---|---|
| **Strategy for fee** | Fee algorithm varies by time/event — each gets its own class | if-else in ParkingLot — violates OCP; every new pricing = modify lot |
| **`synchronized` on `parkVehicle()`** | check-then-assign must be atomic | per-spot lock — deadlock risk if two threads hold different spots |
```

**Rule:** Do NOT just say "I used Factory." Say why Factory and what alternative you rejected.
The WHY is what separates SDE-3 from SDE-2.

---

### §5 — 🔌 LLD — Key Interfaces
Only interfaces that define a contract the interviewer would probe.
Show the method signatures + a one-line javadoc explaining the contract.
**No implementations in this section.**

```markdown
| Interface | Contract |
|---|---|
| `FeeStrategy` | Plug-in fee algorithm; `calculate(Vehicle, durationMinutes)` |
| `ParkingSpot` | Atomic assign/remove; `isAvailable()`, `assignVehicle(v)`, `removeVehicle()` |
```

Then write the Java interface — signature only, no body, one-line javadoc.

---

### §6 — ⚙️ LLD — Code to Write
**This is the most restricted section. JPMC will ask you to code ~1-3 things.**

**Problem archetype drives what you write here:**

| Archetype | Write this | Examples |
|---|---|---|
| **Hot-resource race** (shared seat/spot/inventory) | The synchronized assign method; the check-then-act guard | Movie Ticket Booking, Parking Lot |
| **State machine** (status that transitions through stages) | The `transition(event)` method; guard against invalid transitions | Delivery Partner (ORDER_PLACED → ASSIGNED → PICKED → DELIVERED), Booking states |
| **Matching / search** (query + filter + rank) | The core filter loop or score comparator | No Broker (property search), Delivery Partner (partner assignment) |
| **Async ingestion** (upload + validate + store) | The async submit method with tracking ID; the callback/poll handler | Payment System (async confirm), Document Upload |

**Write code for ONLY:**
1. The concurrency-critical method (if the problem has shared mutable state)
2. A state machine transition (if the problem has one — booking states, delivery status)
3. The one algorithm that's non-obvious (fee calculation, matching logic, async flow)

**Do NOT write:**
- Full class bodies (constructors, getters, all fields)
- Factory switch statements (just say "factory creates based on type")
- Observer notification loops (just say "publish to observers")
- Bean/entity classes with no logic

**If none of the three above apply** (e.g., No Broker is primarily search + property upload with no hot-resource race), skip §6 and note: *"No synchronization-critical method — concurrency is handled at the DB layer (optimistic lock) / Redis (distributed lock at HLD level)."*

**Format:** English steps first, then code. Max 1 code block per method.
Each code block has a `// header comment` explaining what it guards against.

---

### §7 — 🔁 LLD — Concurrency
**If applicable.** Skip this section with a one-line note if the problem has no shared mutable state at the LLD level (e.g., No Broker — search is read-only; concurrency concern is at HLD layer, handled by distributed lock or DB isolation level).

**When present:** One table + one code block. Nothing more.

**Table format:**
```markdown
| Shared field | What breaks without lock | Fix |
|---|---|---|
| `spot.parkedVehicle` | Two threads both see null, both assign | `synchronized` on `assignVehicle()` |
| `activeTickets` map | Concurrent put/remove | `ConcurrentHashMap` |
```

**Code block:** The critical section only. The synchronized method that covers the
check-then-act pair. End with a **Trade-off:** one sentence on the bottleneck this creates
and what you'd do at scale.

**For state-machine problems:** The concurrency concern is invalid state transitions — two threads calling `transition()` simultaneously. Show how you guard the transition method, not a parking/booking assignment.

---

### §8 — 🧨 Java Depth Probes
**Format:** three-column table. Read left to right — what you say → what they ask → answer.

```markdown
| What you say in design | What they immediately ask | Your answer |
|---|---|---|
| "use ConcurrentHashMap here" | "Why not a regular HashMap?" | HashMap is not thread-safe — concurrent puts can corrupt the internal array. CHM uses CAS for empty buckets and bucket-level locks for collision chains. |
| "async via CompletableFuture" | "What thread pool runs it?" | `ForkJoinPool.commonPool()` by default. For I/O-bound tasks I'd pass a custom `ExecutorService` to avoid starving compute tasks. |
| "distributed lock via Redis" | "What if the Redis node crashes mid-lock?" | SET NX has a TTL — lock auto-expires. I'd also use Redlock (3 nodes, majority quorum) for HA. |
```

**Rule:** Only add a row for a phrase YOU will actually say in your design.
If you don't say "CompletableFuture", don't add the row. These are probes triggered by your vocabulary.

---

### §9 — 🌐 HLD — Component Diagram

**Always use the 4-phase construction guide.** Never open with the complete diagram. Show your reasoning — the interviewer is watching how you arrive at the design, not just what you produce.

#### §9 Structure (mandatory for every problem note)

```
§9  🌐 HLD — How to Build This Diagram in the Interview — 3 Phases

  Phase 1 — Numbers First (≈2 min)
  Phase 2 — Skeleton: The Simplest System That Could Work + BREAKING POINT (≈3 min)
  Phase 3 — Upgrade It: One Fix per Pain Point → Production Diagram (≈5 min)

  ✅ Production Diagram — Confluent Single-Column Vertical Flow
```

---

#### Phase 1 — Numbers First

**Purpose:** derive the quantities that will force your architecture decisions. Every component added in Phase 4 must trace back to a number from Phase 1.

**What to compute (always include these rows):**
```
DAU → active session rate
High-traffic window → peak reads/sec with formula shown
Write rate → peak writes/sec with formula shown
Storage: row size × rows/day × 365 → GB/year (do you need sharding?)
Hot data size → does it fit in Redis / memory?
```

**Golden rule for numbers:** If your Phase 1 numbers don't force a component, don't add that component in Phase 4. If MySQL can serve your read load comfortably, don't add Redis just because it looks good. The numbers are the argument — they must genuinely support each upgrade.

**Key question to drive Phase 1:** "What is the hot path, and how often does it get hit?"

---

#### Phase 2 — Skeleton: The Simplest System That Could Work

**Rules:**
- Only boxes that are logically unavoidable at day-one scale
- No Redis, no Kafka, no CDN — those are optimizations; save them for Phase 4
- Every service connects directly to MySQL; no caching layer
- All calls are synchronous — no async

**Diagram style: Confluent single-column vertical flow.** Each stage is a self-contained vertical diagram with labeled arrows and detail inside boxes. This is the required style for all problem notes.

**Template (Confluent style — same for skeleton and production diagram):**
```
── [Stage Name] ──────────────────────────────────────────────────

   ┌──────────────────────────────────────────────────┐
   │  Client   Mobile App · Web Browser               │
   └──────────────────────┬───────────────────────────┘
                          │ HTTPS
   ┌──────────────────────▼───────────────────────────┐
   │  API Gateway  (auth · routing)                   │
   └──────┬───────────────────────────────┬───────────┘
          │                               │
   ┌──────▼────────────────┐   ┌──────────▼─────────────────────────┐
   │  [Read Service(s)]    │   │  [Write Service]                   │
   │                       │   │       └──▶ [External if needed]    │
   └──────┬────────────────┘   └──────────────────────┬─────────────┘
          │ [operation label]                          │ [operation label]
          ▼                                            ▼
   ┌─────────────────────────────────────────────────────────────────┐
   │  [Cache — only if this stage adds it]                          │
   │  key:pattern  → what it stores  · TTL  ← [ServiceName]        │
   └──────────────────────────┬──────────────────────────────────────┘
                              │ [cache miss / write]
   ┌──────────────────────────▼──────────────────────────────────────┐
   │  [DB — always present]                                         │
   │  table · table           ← [ServiceName]                       │
   └──────────────────────────┬──────────────────────────────────────┘
                              │ [event name — only if Kafka added]
   ┌──────────────────────────▼──────────────────────────────────────┐
   │  Kafka  (topic: [name])  — only if async fan-out justified     │
   │  ├──▶ [ConsumerA]                                              │
   │  └──▶ [ConsumerB]                                              │
   └─────────────────────────────────────────────────────────────────┘

BREAKING POINT / KEY INVARIANT: ...
══════════════════════════════════════════════════════════════════
```

**Rules for the Confluent-style diagram:**
- Skeleton uses `BREAKING POINT:` — list (a)(b)(c) pain points inline after the diagram
- Production uses `KEY INVARIANT:` — state what the topology guarantees
- End every diagram block with `══════════════════════════════════════════════════════════════════`
- Data store boxes show: `key:pattern → what stored · TTL ← ServiceName` — the `← ServiceName` tag shows which service owns each entry without a separate diagram
- Arrow labels between service boxes and data stores show the Redis command or SQL operation (not just "reads" or "writes")
- Phase 4 upgrades appear by ADDING boxes that weren't in the skeleton — the diff between skeleton and production IS the upgrade story

**Close Phase 2 by embedding BREAKING POINT inside the skeleton diagram** — list (a)(b)(c) pain points right below the diagram, before the `══════` separator. This is the SDE-3 signal: you proactively name the breakdowns before the interviewer asks.

---

#### Phase 3 — Upgrade It: One Fix per Pain Point

**Rules:**
- Add exactly one component per pain point from Phase 3
- State the specific mechanism (not just "add Redis" — state "Redis SET NX + PX TTL = atomic, auto-expiring distributed lock")
- Tie back to the pain point number: "Pain point 1 → Redis Cache…"
- Never add a component without a pain point that demanded it

**Format for each upgrade:**
```
PAIN POINT N → Add [Component] ([key config])
  Why this works: [1-2 sentences — the specific property that solves the problem]
```

---

#### ✅ Production Diagram — What You're Building Toward

**This is Phase 2 skeleton + all Phase 3 upgrades drawn together as one Confluent single-column diagram.** The diff between the skeleton and the production diagram IS the upgrade story.

**Mandatory rules for the production diagram:**
- Use **Confluent single-column vertical flow** (same style as the skeleton)
- Added boxes must trace back to a named upgrade in Phase 3 — no orphan components
- Every arrow must have a label (HTTPS, Redis command, Kafka event name, SQL operation)
- Data store boxes show `key:pattern → what stored · TTL ← ServiceName` tags
- Close with `KEY INVARIANT:` (1-3 lines stating what this topology guarantees)
- End with `══════════════════════════════════════════════════════════════════`
- Width ≤ 80 columns

**Template (fill in for each problem):**

```
── Production: All Upgrades Applied ──────────────────────────────

   ┌────────────────────────────────────────────────┐
   │  Client   Mobile App · Web Browser             │
   └─────────────────────┬──────────────────────────┘
                         │ HTTPS
   ┌─────────────────────▼────────────────────────────────────────┐
   │  API Gateway  (JWT · rate-limit · TLS · routing)            │
   └──────┬────────────────────────────────────────┬─────────────┘
          │                                        │
   ┌──────▼────────────────┐   ┌────────────────────▼──────────────────┐
   │  [ReadService(s)]     │   │  [WriteService]                       │
   │  (read-heavy)         │   │  state machine · ACID writes          │
   │  [QueryService]       │   │  └──▶ [External]  (timeout)          │
   └──────┬────────────────┘   └────────────────────┬─────────────────┘
          │ GET (cache-aside)                        │ SET key NX PX ttl
          │ GET (other reads)                        │   ACID write · idempotency
          ▼                                          ▼
   ┌──────────────────────────────────────────────────────────────────┐
   │  Redis                                                           │
   │  [key:pattern]  → [what stored]  · [TTL]  ← [ReadService]      │
   │  [key:pattern]  → [what stored]  · [TTL]  ← [QueryService]     │
   │  [key:pattern]  → [what stored]  · [TTL]  ← [WriteService]     │
   └──────────────────────────┬───────────────────────────────────────┘
                              │ cache miss / ACID write
   ┌──────────────────────────▼───────────────────────────────────────┐
   │  MySQL  (ACID)                                                   │
   │  [table] · [table]         ← [ReadService]                      │
   │  [table] · [table]         ← [WriteService]  (single ACID tx)   │
   └──────────────────────────┬───────────────────────────────────────┘
                              │ emit [event-name]
   ┌──────────────────────────▼───────────────────────────────────────┐
   │  Kafka  (topic: [name], key = [partition-key])                   │
   │  ├──▶ [ConsumerA]   [what it does] · retry via DLQ              │
   │  └──▶ [ConsumerB]   [what it does]                              │
   └──────────────────────────────────────────────────────────────────┘

KEY INVARIANT: [what this topology guarantees — 1-3 lines]
══════════════════════════════════════════════════════════════════
```

**The "which path hits which data store" problem is solved by:**
1. **Labeled arrows** between service boxes and data stores showing the actual Redis command or SQL operation
2. **`← ServiceName` tags** inside each data store box for every key/table — ownership visible without a separate diagram
3. **Vertical adjacency** — services and their data stores are next to each other in the flow

**Anti-rule:** No box without a sentence in §10 defending why it's there.

---

### §10 — 🏛️ HLD — Decisions
Same format as §4 but for infrastructure choices.

```markdown
| Component | Why chosen | What I rejected and why |
|---|---|---|
| **Redis for seat lock** | SET NX + TTL = atomic lock + auto-expiry. No lock cleanup needed on crash. | DB row lock — holds a DB connection open for the lock duration; expensive at scale |
| **Kafka for confirmation** | Email/analytics don't need to block the booking response. Async fan-out. | Synchronous call chain — if email service is slow, booking API is slow |
| **MySQL for bookings** | Strong consistency required — can't double-book; ACID transactions | NoSQL — eventual consistency is not acceptable for seat assignment |
```

---

### §11 — 📡 HLD — API Design
**Max 3 endpoints.** Focus on the two endpoints the interviewer will probe:
- The write endpoint (with idempotency key)
- The read endpoint (with pagination if relevant)

**Format:**
```markdown
POST /v1/bookings
X-Idempotency-Key: <uuid>          ← client generates; server deduplicates
Authorization: Bearer <token>

Request:  { showId, seatIds[], paymentMethodId }
Response: 202 Accepted { bookingId, status: "PENDING" }

// Idempotency: server stores key in DB.
// Second call with same key → return same response, no second charge.
```

---

### §12 — 🛤️ HLD — Happy + Unhappy Paths
**Both are required — JPMC accounts confirm this explicitly.**

**Happy path:** Numbered steps end-to-end. Keep it to 6-8 steps.

**Unhappy paths — pick the ones relevant to the problem's archetype:**

| Archetype | Must-cover unhappy paths |
|---|---|
| **Hot-resource race** | Race condition (two users, same resource); payment timeout; crash mid-transaction; retry (idempotency) |
| **State machine** | Invalid transition attempt; event arrives out of order; service crash mid-transition (what state is the entity left in?) |
| **Search / browse** | Stale data served from cache; DB replica lag causing old results; search timeout under load |
| **Async ingestion** | Third-party validation timeout (→ return tracking ID, poll later); validation fails after accepted; duplicate upload |

**Format:**
```markdown
**Happy path:**
1. User selects seats → GET /shows/{id}/seats (cache-first, 200ms SLA)
2. User initiates booking → POST /bookings (idempotency key generated client-side)
3. BookingService acquires Redis lock: SET seat:123:lock userId NX PX 10000
4. BookingService creates booking row: status = PENDING
5. PaymentService called synchronously (10s timeout)
6. On success: booking → CONFIRMED; lock released; Kafka event → email + analytics
7. Response: 200 bookingId + confirmation number

**Unhappy path — seat race:**
→ Two users click same seat at same moment
→ Both hit BookingService simultaneously
→ Redis SET NX: only ONE gets the lock (atomic CAS)
→ Loser gets 409 Conflict → UI shows "seat taken, pick another"

**Unhappy path — payment timeout:**
→ PaymentService call exceeds 10s
→ BookingService: booking → FAILED; Redis lock released; seat available again
→ Idempotency key NOT committed → user can retry safely

**Unhappy path — service crash mid-booking:**
→ BookingService crashes after Redis lock SET but before DB write
→ Redis TTL expires (10s) → seat auto-released
→ User retries → idempotency key not found → new booking initiated safely
```

---

### §13 — 🔧 HLD — Fault Tolerance
**One row per external call.** JPMC probes this explicitly — don't design around it.

```markdown
| External call | What breaks | What you add |
|---|---|---|
| Payment gateway | Timeout → booking stuck in PENDING | 10s timeout → booking → FAILED; idempotency key on retry |
| Email service | Down → user never gets confirmation | Kafka DLQ — failed messages retried async; email is not booking-critical |
| Seat lock (Redis) | Node crashes → lock never released | TTL on every lock (PX 10000); Redlock for HA if needed |
| DB write | Partial write → inconsistent state | DB transaction wraps booking row + audit log; rollback on failure |
```

**After the table:** one sentence on the "happy → unhappy" pivot JPMC uses:
> *"Once the happy path works, I'd ask myself: what happens if THIS call takes 10× longer or fails permanently? That question drives every row in this table."*

---

### §14 — 🔬 Interview Q&As
**5-7 questions. Two tiers required:**

**Tier 1 — Surface (what every candidate knows):**
- Walk me through the happy path end-to-end.
- How do you prevent double-booking?

**Tier 2 — JPMC probes (what they ask when you claim SDE-3):**
- "What happens if the Redis node crashes mid-lock?"
- "How does your idempotency key survive a network retry where the server succeeded but the client never got the response?"
- "You said eventual consistency for the email — what's the worst case delay and does the user care?"
- "Your synchronized block on parkVehicle() — what's the bottleneck at 1000 concurrent bookings?"

**Format:**
```markdown
### Q: "How do you prevent two users from booking the same seat?"
> Redis SET NX is atomic — only one thread gets the lock. The key is seat:seatId:lock,
> value is the userId, TTL is 10s. If Redis crashes between SET and the DB write,
> the TTL auto-expires and the seat is available again. No manual cleanup needed.
```

---

### §15 — 🧾 TL;DR — 30-Second Pitch
One paragraph. This is what you say in the FIRST 60 seconds when the interviewer gives you the problem. It covers: entities, patterns used, concurrency model, scale strategy.

```markdown
> "I'll start with the core entities — Booking, Seat, Show, Theatre, Payment — and
> their relationships. For concurrency, the critical decision is seat locking: I'll use
> Redis SET NX with a TTL so the lock auto-expires on crash. The booking flow is
> synchronous to payment but async for everything downstream (email, analytics, inventory
> update) via Kafka. The design question I want to confirm first: does payment go through
> an external gateway, and what's its timeout SLA? That drives whether we need a
> synchronous call or an async polling model."
```

---

### §16 — 🔄 Changelog
Standard table. Date + what changed.

---

## ✅ Pre-Write Checklist (Run Before Starting Each Note)

- [ ] Clarifying questions: ≥ 10, all 7 categories covered
- [ ] LLD class diagram: every entity has fields + key methods; relationships labeled
- [ ] Design decisions: every choice has WHY + rejected alternative
- [ ] Code section: max 3 methods; only concurrency, state machine, or non-obvious algo
- [ ] Java depth probes: only phrases you'll actually say in your design
- [ ] HLD diagram: Confluent single-column vertical flow; skeleton has `BREAKING POINT:` embedded, production has `KEY INVARIANT:`, both end with `══════`
- [ ] Unhappy paths: ≥ 3 scenarios (race, timeout, crash, retry)
- [ ] Fault tolerance table: one row per external call
- [ ] Q&A: ≥ 2 Tier-2 JPMC probes
- [ ] TL;DR: you can read it out loud in 30 seconds and it covers entities + concurrency + HLD shape
- [ ] All code: language-tagged, braced, one statement per line, no `...` placeholders
- [ ] All unfamiliar terms glossed on first use
- [ ] No sections from the stripped list (prerequisites, how-to-use, memory anchors, mental-model analogy, real-world companies, further reading)

---

## 🔄 Changelog

| Date | Change |
|------|--------|
| Aug 2026 | Standard created for JPMC Round 3 problem notes. Designed for 1-hour LLD+HLD arc. Stripped from parent HLD/LLD standards: prerequisites, how-to-use, memory anchors, concept analogies, real-world company lists, when-to-use-vs-not tables, further reading. LLD code rule: 3 methods max, only concurrency/state-machine/non-obvious algo. JPMC-specific signals baked in: 10-15 clarifying questions, fault-tolerance table per external call, happy+unhappy paths mandatory, Java-depth-probe table. |
| Aug 2026 | **§3 expanded** into two sub-sections: §3a (Construction Guide — 7 moves with board snapshots + 75% rule) and §3b (Complete Diagram — the answer key). Construction guide must come first; complete diagram comes last. Each move requires a ≤15-line ASCII board snapshot showing the diagram at that point in time. The 7-move framework is problem-agnostic — apply to every archetype, adapt the entities and interfaces to the specific problem. |
| Aug 2026 | **§9 overhauled** into a mandatory 4-phase HLD construction guide: Phase 1 Numbers → Phase 2 Skeleton → Phase 3 Break It → Phase 4 Upgrade It. The complete component diagram becomes "✅ Enhanced Diagram — What You're Building Toward" and appears only at the end of the 4 phases. Numbers in Phase 1 must genuinely force each component added in Phase 4 — if the load is fine without an upgrade, don't add it. Each Phase 3 pain point must name the exact number from Phase 1 that causes the break, not generic "won't scale" language. |
| Aug 2026 | **HLD diagram style locked to tiered (Option D).** Both skeleton and enhanced diagrams use horizontal tier bands: CLIENT → GATEWAY → READ PATH / WRITE PATH → DATA → ASYNC. This replaces the previous vertical-pipe style. The READ PATH / WRITE PATH split is a deliberate SDE-3 signal — it forces separation of concerns before any component is added. Quality checklist updated to match. |
| Aug 2026 | **Enhanced diagram redesigned to single annotated diagram (interview-drawable).** Replaced tiered structure + two sequence diagrams with one diagram. Path-to-datastore ambiguity resolved by: (1) arrow labels from READ/WRITE path boxes showing access pattern (GET cache-aside vs SET NX PX), (2) each Redis key tagged ← READ PATH or ← WRITE PATH inside the box, (3) each MySQL table labeled with which path owns it. One diagram, ~5 min to draw on a whiteboard. |
| Aug 2026 | **Diagram style migrated to Confluent single-column vertical flow.** Skeleton and production diagrams both use `── Stage Name ──` header, vertical box-and-arrow flow, `← ServiceName` tags inside data store boxes, labeled arrows showing the actual Redis command or SQL operation, and `══════` separator at the end. BREAKING POINT embedded inside the skeleton diagram block (replaces standalone Phase 3 text). Phase 3 collapsed — now just the upgrade justification text before the production diagram. Standards template updated to match. |
| Aug 2026 | **§9 structure corrected from 4-phase to 3-phase** to match actual approved movie-ticket note structure: Phase 1 Numbers → Phase 2 Skeleton+BREAKING POINT → Phase 3 Upgrade+Production Diagram. "Break It" is no longer a standalone phase; BREAKING POINT is embedded inside the skeleton diagram. "Enhanced Diagram" section renamed to "Production Diagram" and its template updated from tiered style to Confluent single-column. Pre-write checklist updated accordingly. |
