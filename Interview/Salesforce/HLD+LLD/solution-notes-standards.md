# Salesforce SMTS — HLD + LLD Combined Round: Notes Standard

> **What this file is:** The notes standard for combined-round problem files. Cross-references
> `LLD/notes-standards.md` and `Interview/DocuSign/r2-solutions/solution-notes-standards.md`
> for the mechanics of each half. The new sections here are the ones that don't exist in
> either: the dual-lens mental model, the navigation framework, and Salesforce-specific scoring.
>
> **When you use this:** Writing any problem notes file for the Salesforce SMTS combined round.
> **File location for each problem:** `Interview/Salesforce/HLD+LLD/<problem-name>.md`

---

## 🧠 Section 1: The Core Mental Model — Two Zoom Levels, Same System

The combined round tests one skill: holding both views simultaneously and switching between them
without losing the thread. LLD and HLD are not two separate designs — they are two zoom levels
of the SAME system.

### 🎨 Visual — Dual Zoom Levels: Notification Service

```
═══════════════════════════════════════════════════════════════════════
  HLD VIEW  (Zoom Level = System, Scope = 5+ services)
═══════════════════════════════════════════════════════════════════════

  ┌─────────────┐     ┌──────────────────┐     ┌──────────────────┐
  │  API Layer  │────▶│  Notification    │────▶│  Channel Router  │
  │ (REST/gRPC) │     │  Service         │     │ (routes by type) │
  └─────────────┘     └──────────────────┘     └──────────────────┘
                              │                         │
                              ▼                         ▼
                     ┌─────────────────┐     ┌──────────────────┐
                     │  Notification   │     │  Dispatch Queue  │
                     │  Store (DB)     │     │  → Workers (×N)  │
                     └─────────────────┘     └──────────────────┘

                            ZOOM IN ↓         ZOOM OUT ↑

═══════════════════════════════════════════════════════════════════════
  LLD VIEW  (Zoom Level = Class, Scope = interfaces + implementations)
═══════════════════════════════════════════════════════════════════════

  <<interface>>                        <<interface>>
  NotificationSender                   ChannelRouter
  + send(NotificationRequest): void    + route(ChannelType): NotificationSender
        ▲                                        ▲
        │ implements                              │ implements
  ┌─────┴────────────────────┐         ┌─────────┴───────────────────┐
  │ EmailSender              │         │ DefaultChannelRouter         │
  │ PushSender               │         │ - Map<ChannelType, Sender>   │
  │ SMSSender                │         └─────────────────────────────┘
  └──────────────────────────┘

KEY INVARIANT:
   Each HLD box owns one interface + N strategy implementations.
   The zoom direction is always: HLD box → <<interface>> → implementations.
   The public methods of the interface are the API contract between HLD boxes.
```

### The Mapping Rule (memorise this)

| HLD Box | LLD Class(es) | Connection |
|---|---|---|
| Notification Service (orchestrator) | `NotificationService` class | The orchestrator that coordinates all below |
| Channel Router | `ChannelRouter` interface + `DefaultChannelRouter` | Strategy pattern — routes to correct Sender |
| Dispatch Workers | `NotificationSender` interface + `EmailSender`, `PushSender`, `SMSSender` | Strategy pattern — each channel = one impl |
| Notification Store | `NotificationRepository` interface + `NotificationStatus` enum | Repository pattern |
| API Layer | `NotificationController` + `NotificationRequest` value object | Entry point + input validation |

The mapping table is the first thing you build in prep notes for any problem. It forces you
to understand both views before the interview.

---

## 🧭 Section 2: Navigation Framework — The Skill That Wins the Round

### Opening Protocol (first 2 minutes — mandatory)

> "Before I start — should I do LLD first or HLD first, or do you have a preference?"
> *(Wait for answer. If no preference:)*
> "I'll start with LLD — class design, interfaces, concurrency. Then zoom out to the
> distributed system. I'll flag the transition explicitly so you can redirect me if you
> want more depth anywhere."

This two-sentence opener does three things: sets shared expectations, signals you know both
halves exist, and gives the interviewer a control point. Skipping this opener is a common
failure mode — candidates assume an order and the interviewer assumes a different one.

### HLD → LLD Zoom-In

**Trigger phrases from interviewer:**
- "How would you implement the Channel Router?"
- "Show me the class design for the dispatcher"
- "What does the Sender interface look like?"

**What to say:**
> "Good — I'll zoom into [box name] now. At the class level, the key abstraction is..."

**What to draw:** Class diagram for that ONE HLD box only. Show the interface before any
implementation. Don't redraw the whole class diagram — zoom into the requested box.

**Key rule:** One box at a time. If they ask about Channel Router, draw `ChannelRouter`
interface + implementations. Don't drag in `NotificationService` unless they ask.

### LLD → HLD Zoom-Out

**Trigger phrases from interviewer:**
- "How does this scale to 10M users?"
- "How would this work in production?"
- "What happens when there are 1000 requests per second?"

**What to say:**
> "Let me zoom out to the system level. The classes I just showed map to these
> services: [draw boxes around class groups, label them as services]."

**What to draw:** Take your LLD classes and draw service boundaries around logical groups.
The classes BECOME the HLD boxes — draw the promotion explicitly. Then add the infrastructure
layer (load balancer, message queue, DB replicas).

**Key rule:** The transition is the mapping. "NotificationSender and its impls → Dispatch
Workers service" is the sentence that connects both views. Say it explicitly.

### Pivot Signal Lookup Table

| What Interviewer Says | What They Want | Your Move |
|---|---|---|
| "How would you implement X?" | LLD zoom-in on one HLD component | Zoom in, show interface first then impl |
| "How does this scale?" | HLD zoom-out | Draw service boundaries + infra layer |
| "What if we add a new channel type?" | Extensibility — OCP + Strategy | "At LLD: add one class implementing Sender, zero existing code changes. At HLD: add one routing rule and a new worker pool." |
| "Walk me through a notification being sent" | Dual-level trace | Trace at HLD level first, then say "at code level, that's a call to `channelRouter.route()` → `sender.send()`" |
| "What breaks first?" | HLD scale + failure thinking | Name the bottleneck + QPS threshold |
| "What about reliability?" | HLD resilience angle | Retries + idempotency-key + dead-letter queue |
| "What if two threads send the same notification?" | LLD concurrency | Named fields + lock strategy |

---

## 📐 Section 3: Per-Level Structure

### LLD Half (target: 35 min)

**Cross-reference:** Follow `LLD/notes-standards.md` — Type 2 Problem Notes, all 12 sections.
**Relative path:** `../../../LLD/notes-standards.md`

**Time allocation for 35-min live delivery:**

```
 0– 2 min   Problem Statement + Requirements (verbal, no writing)
 2–12 min   Class Design — ASCII diagram + entity walkthrough
12–18 min   Key Interfaces — write the Java interface contracts live
18–24 min   Design Decisions — name patterns and justify each ("Strategy because...")
24–30 min   Coding Skeleton — write the orchestrator method live
30–35 min   Concurrency + "What Would You Do Differently?" (1 thing each)
```

**Adjustments specific to the combined round:**
- Q&As and Patterns sections are prep-notes only. Don't recite them live.
- Name every pattern out loud the moment you draw it: *"This is Strategy pattern."*
- Thread-safety must be said explicitly: *"This field is shared — I'll use `synchronized`
  on this method / `ConcurrentHashMap` here."* Salesforce always asks about it.
- Write real Java in the coding skeleton — interfaces, `@Override`, generics, no pseudocode.

**What Salesforce scores in the LLD half:**

| Dimension | The Signal They're Looking For |
|---|---|
| SOLID principles | You name them by name: OCP, SRP, DIP — not just follow them silently |
| Design patterns | Named + justified: *"Strategy because behavior is runtime-swappable"* |
| Extensibility | *"To add SMS, I add one class. No existing code changes."* — say this explicitly |
| Concurrency | Named shared fields + named lock strategy. Not implied — stated. |
| Code quality | Real interfaces, `@Override`, enums for state, no magic strings |

---

### HLD Half (target: 45 min)

**Cross-reference:** Follow `Interview/DocuSign/r2-solutions/solution-notes-standards.md`
for the full section list.
**Relative path:** `../../DocuSign/r2-solutions/solution-notes-standards.md`

**Compressed section list for 45 min (not the full 60-min DocuSign format):**

```
 0– 3 min   Clarifying questions — 4 questions, note the architectural fork each forces
 3– 6 min   Requirements — 5 FR + 4 NFR, stated quickly
 6– 8 min   Core entities — 4-6 entities, nature words only (no storage column)
 8–12 min   Scale estimation — 3 numbers with units. No more.
12–25 min   Architecture diagram — ASCII or whiteboard, 5+ labeled boxes, data flow arrows
25–30 min   Breaking points — what fails first + at what threshold
30–40 min   ONE deep dive component (the riskiest one)
40–44 min   3 trade-offs with one-sentence business impact each
44–45 min   API design — 3-4 endpoints, method + path + key fields
```

**What to omit (no time in 45 min):**
- Type A / Type B distinction — DocuSign-specific, not applicable here
- Section 14 TUEASAS checklist — raise 1-2 dimensions verbally in the deep dive
- Full failure mode catalogue — fold into trade-offs instead
- RESHADED deep math — 3 scale numbers, then move on

**Core Entities rule (from DocuSign standard, applies here):**
No "storage" column in the entities table. Nature words only:
`transactional` / `ephemeral` / `immutable` / `append-only` / `client-held`

**Breaking points must be quantified.** Not: *"the DB will be a bottleneck."*
Yes: *"Single DB bottlenecks above ~2,000 write QPS — that's when we add a message queue
to buffer writes and let workers drain asynchronously."*

**What Salesforce scores in the HLD half:**

| Dimension | The Signal They're Looking For |
|---|---|
| Requirements clarity | Each clarifying Q reveals a fork: *"if push only → single dispatcher; if multi-channel → router + strategy"* |
| Architecture breadth | The diagram works end-to-end without gaps |
| Deep dive ability | ONE component at real depth: retry logic, deduplication, schema decisions |
| Trade-off thinking | Business impact layer: not just "Kafka adds latency" but "50ms latency vs zero dropped notifications at 10M/day" |
| Salesforce awareness | Multi-tenancy raised without prompting |

---

## 🏢 Section 4: Salesforce-Specific Evaluation Layer

### Multi-Tenancy — Raise This Proactively

Salesforce serves 150,000 orgs (organizations — a Salesforce term for a single customer
tenant — "org" is short for "organization") on shared infrastructure. For any design, add
the multi-tenancy angle without waiting to be asked:

> *"Since this would run on Salesforce's shared infrastructure with 150K orgs, I'd add
> per-org rate limiting on the queue and a tenant ID as a partition key on storage — so
> one org's notification spike doesn't degrade other orgs."*

**Per-problem multi-tenancy angle:**

| Problem | What to Say |
|---|---|
| Notification Service | Per-org rate limits on dispatch, `orgId` partition key on notification log |
| Rate Limiter | Counters keyed by `orgId:clientId`, not just `clientId` |
| Job Scheduler | Per-org job quotas, `orgId` isolation in job queue, per-org execution logs |
| Rules / Workflow Engine | Rule evaluation scoped per org — no cross-org rule data leakage |

### Platform Events Angle (bonus — raise when relevant)

If designing a notification or event-driven system, mention Platform Events
(Salesforce's internal event bus built on Kafka with at-least-once delivery guarantees):

> *"This maps directly to how Salesforce Platform Events works — events on a Kafka-backed bus,
> workers consuming asynchronously, guaranteed at-least-once delivery. I'd design the same way."*

Saying this signals you understand their product domain, not just generic distributed systems.

---

## ⏱️ Section 5: Time Management Protocol

```
[  0– 2 min ]  Opening — confirm order, set expectations (Section 2 opener)
[  2–37 min ]  Half 1 (LLD or HLD — whichever was confirmed)
   [ 30 min mark ]  → WRAP SIGNAL: "I want to be mindful of time — should I
                        go deeper here or move to [the other half]?"
[ 37–87 min ]  Half 2
   [ 75 min mark ]  → WRAP SIGNAL: "We have about 10 minutes — deeper on X
                        or should I cover the last trade-off?"
[ 87–90 min ]  Buffer, questions, handoff
```

**The 30-min rule:** At the 30-minute mark of whichever half you're in, proactively surface
the wrap signal. Don't wait for the interviewer to cut you off. Offering the control point
reads as a senior engineer managing scope — not running out of time.

**Overage recovery:** If LLD takes 42 min instead of 35:
- In HLD, skip scale estimation math. Jump straight to the architecture diagram.
- Say: *"I'll skip the math and go straight to the design since we have limited time."*
- Prioritise: architecture diagram + ONE deep dive + trade-offs. API design is last.

---

##  Section 6: Per-Problem Notes File Format

Each problem gets one `.md` file: `Interview/Salesforce/HLD+LLD/<problem-name>.md`.

**Note:** The order below is LLD-first. If your round is HLD-first (the Roundz SMTS report
documents HLD→LLD on Notification Service), swap sections 2 and 3. Both halves are designed
to stand alone. The dual-layer map (section 1) applies regardless of order.

> ###  READ SECTION 6.5 BEFORE WRITING
> The section list below is the **skeleton**. Section 6.5 contains the **seven authoring rules**
> that determine whether the file is interview-deliverable or just a nicely formatted answer
> key. A file that follows the skeleton but skips 6.5 will look complete and score badly.
> Reference implementations: `notification-service.md`, `job-scheduler.md`, `rate-limiter.md`,
> `booking-system.md`, `parking-lot.md`, `signup-login-system.md`.

### Required Sections (exact order)

```
0.  Identity
   — problem name, format (HLD+LLD combined), time budget per half,
     frequency rank (from questions-by-frequency.md), Salesforce-specific angle
   — QUOTE THE VERBATIM SOURCE PROMPT if one exists in the research files.
     The real wording tells you what's actually graded (e.g. the Meeting Room
     prompt names "race conditions" explicitly → it's a concurrency problem).
   — If the Salesforce fit is weak (e.g. Parking Lot), SAY SO honestly.
     A forced product tie-in reads worse than an acknowledged gap.

1.  Dual-Layer Map
   — the HLD-box → LLD-class(es) mapping table for THIS problem
   — every row names the interface that makes that box swappable
   — close with a "zoom sentence": one line showing the SAME component at both
     levels ("X is a lock in LLD; in HLD it's what stops two AZs double-booking")

2.  LLD Half  (all 12 sections, compressed for 35-min delivery)
   2.1  Problem Statement
   2.2  Requirements (functional + non-functional + explicit OUT OF SCOPE)
   2.3  Class Design — SPLIT INTO FIVE SUBSECTIONS (see Rule 1 + Rule 2):
        2.3.1  Deriving the classes (the noun-extraction table)
        2.3.2  Entity fields
        2.3.3  Relationships (with composition-vs-aggregation calls)
        2.3.4  ASCII class diagram
        2.3.5  Follow-ups they will ask — and your answers
   2.4  Key Interfaces (Java, interface before any impl)
   2.5  Design Decisions (with the alternatives table — see Rule 3)
   2.6  Visual — Object Interaction (the call trace, with narration lines)
   2.7  Coding Skeleton (enum → interface → impl → registry → orchestrator)
   2.8  Concurrency (every race named, each with a fix and WHY that fix)
   2.9  "What Would You Do Differently?" (≥ 2 concrete answers with trade-offs)
   2.10 Interview Q&As (prep-only, don't recite)
   2.11 TL;DR — 30-Second Pitch (LLD)
   2.12 Patterns Used (name + where + why, one line each)

3.  HLD Half (target: 45 min)
   3.1  Clarifying Questions — each with the ARCHITECTURAL FORK it forces
   3.2  Requirements (5 FR + 4 NFR, NFRs carry the numbers)
   3.3  Core Entities (nature words only, no storage column)
   3.4  Scale Estimation (≥ 3 numbers with units; show the arithmetic)
   3.5  Architecture Diagram — STAGED, NOT FINAL-STATE (see Rule 4)
   3.6  Deep Dive on the riskiest component (layered-defense or options table,
        plus an honest limitation stated out loud)
   3.7  Trade-offs (3, each with a "failure mode if wrong" — see Rule 5)
   3.8  API Design (table + derivation note explaining a non-obvious choice)
   3.9  Data Model — FULL SQL + justified decisions table (see Rule 6)
   3.10 Salesforce Multi-Tenancy Angle (as a quotable spoken paragraph)

4.  Navigation Pivots — THIS Problem
   — opening protocol script (verbatim, from Section 2 of this standard)
   — 5–7 pivot signals specific to this problem
   — for each: what the interviewer says → what they want → your scripted response
   — must be bidirectional: ≥ 1 HLD→LLD and ≥ 1 LLD→HLD

5.  TL;DR — Dual-Level Pitch
   — ≤ 5 sentences covering both LLD design and HLD architecture
   — must name: one pattern (LLD), one quantified breaking point (HLD),
     one trade-off, and the Salesforce angle

6. Changelog
   — date, what was created/changed, which source prompt it's grounded in
```

**Target length: 700–850 lines.** Below ~600 means a section got hand-waved. Above ~900
means prose that won't survive a 90-minute round — tighten rather than split, since
splitting one problem across files destroys the dual-zoom cohesion.

---

##  Section 6.5: The Seven Authoring Rules (THIS IS THE QUALITY BAR)

These seven rules are the difference between notes that *present answers* and notes that
*prepare you to defend answers*. Everything here was learned by getting it wrong first.

### Rule 1 — Derive classes from requirement nouns; never present a class list

A class list is unfalsifiable — the interviewer can't tell whether you reasoned or memorised.
Build a table that walks requirement → extracted noun/verb → class → **justification**.

**The justification column is the scored part.** It must answer: *why does this deserve its
own type instead of being a field on an existing one, and what specifically breaks if you
inline it?* Vague benefits ("cleaner", "more modular") are worthless. Name the concrete failure.

| Column | Content |
|---|---|
| Requirement | Quote it verbatim from section 2.2 |
| Noun / verb extracted | The actual word, plus whether it's a variation point |
| Becomes | Class/interface/enum + which kind and why that kind |
| Why it earns its own type | The concrete failure if inlined |

**Also required: at least one row where you DECLINE to create a class.** Knowing when *not*
to add a class is a stronger signal than adding ten. Examples from existing files: availability
counters in `parking-lot.md` ("a wrapper around a map with no behavior"), burst config in
`rate-limiter.md` ("two numbers that only mean something together with the rule").

Close the table with a one-line summary naming the variation points:
> *"So the design has three variation points — X, Y, Z — and everything else is plumbing."*

### Rule 2 — Every HAS-A must resolve to composition or aggregation, with reasoning

Saying "HAS-A" invites the follow-up *"composition or aggregation?"* and a file that doesn't
answer it has set a trap for you. Include this rule of thumb verbatim in every file:

> **Composition** (filled diamond) — the whole creates and owns the part; part dies with the
> whole; never shared.
> **Aggregation** (hollow diamond) — the whole holds a reference to a part built elsewhere;
> part outlives the whole and may be shared.
> **Heuristic to say out loud:** *"If I `new` it inside the constructor, it's composition.
> If it arrives through the constructor, it's aggregation."*

**The trap to avoid (a real error caught in v1 of `notification-service.md`):** "the service
can't function without it" describes a **required dependency**, NOT ownership. A
constructor-injected collaborator is *aggregation* even when mandatory. Getting this backwards
is worse than omitting it.

Strong relationship tables also include:
- at least one **USES** (collaborator, not a structural part) distinguished from HAS-A
- at least one **deliberate non-relationship** ("no back-reference, because that would create a cycle")
- where possible, one field that is **both** (e.g. composition of the map, aggregation of its values)

### Rule 3 — Compare against the STRONGEST alternative, never a strawman

An "alternatives considered" table is worthless if the alternative is one nobody would propose.
The original `notification-service.md` compared its router against a `switch` statement, but
the real competitor was *the same injected map inlined into the orchestrator*, which is equally
OCP-clean. That made the whole justification collapse under one follow-up.

**Test before writing the row:** *"Would a competent engineer actually argue for this
alternative?"* If no, you picked a strawman. Find the option a smart person would defend.

Table shape: `| Decision | Pattern Chosen | Strongest Alternative Considered | Why the alternative loses |`

**Corollary — attribute benefits to the right cause.** Before claiming a class provides a
benefit, check whether the benefit actually comes from something else (a DI pattern, a
constraint, a data structure). Claiming a benefit the component doesn't provide is the
fastest way to lose credibility mid-answer.

### Rule 4 — HLD architecture must be STAGED, never final-state

Drawing the finished architecture proves nothing — it's recall. The reasoning *is* the answer.

```
Stage 1 — the naive design a reasonable person would start with
   ↓  ≥ 3 BREAKING POINTS, each QUANTIFIED with arithmetic
   ↓  a DECISION table (options / strength / weakness / verdict)
Stage 2 — the fixed design, with "what each hop buys us"
   ↓  ≥ 1 remaining BREAKING POINT (the one that bites at real scale)
[Stage 3 — only if there's a genuine third step, e.g. rate-limiter's local pre-filter]
```

**Quantified means arithmetic, not adjectives.** Not "the DB will bottleneck" but *"280ms
blocked per notification ÷ 200 threads = ~714/sec, and we need 35,000/sec — short by 49x."*
The number is what makes it a senior answer instead of a generic observation.

**Always end Stage 2 with a "remaining known gap" you name before the interviewer finds it.**
Self-identified weaknesses read as maturity; discovered ones read as oversights.

### Rule 5 — Trade-offs need a "failure mode if wrong", not just pros and cons

Four parts, every time:
1. **Chose:** the decision
2. **Gain:** what it buys
3. **Lose:** the real cost (never "none")
4. **Failure mode if wrong:** the *concrete business consequence* of the other choice

Part 4 is the differentiator. Not "it would be slower" but *"fail-closed globally means a
30-second Redis failover returns 429 for 100% of API traffic across all 150K orgs — the
limiter causes a bigger incident than any abuse it prevents."*

### Rule 6 — Every file needs a full SQL data model with justified decisions

The schema is where design claims get tested. Requirements:
- **Real DDL** — types, constraints, indexes, partitioning. Not a field list.
- **Partial indexes** where the hot query only touches a subset (`WHERE status = 'FREE'`).
- **Multi-tenancy:** `org_id`/tenant key on every table and leading the indexes.
- **A justified-decisions table:** `| Decision | Why | What breaks otherwise |`
- **Explain deliberate ABSENCES too** — e.g. rate-limiter documents why counters are *not*
  in Postgres. Saying what you chose not to store is as informative as what you did.
- **Consistency with the LLD:** if a class holds an object but the schema stores strings,
  explain the round-trip (this gap was caught in `job-scheduler.md` review → `ScheduleFactory`).

### Rule 7 - Let the problem pick the pattern; never reuse a favourite

The six existing files lean on a recurring set - Strategy (12x), Repository, Registry, Value
Object, Factory, Composite, Chain/Specification, Aggregate separation, State-as-guarded-enum,
Template Method, Lease-with-TTL, Outbox. That is an artefact of **those six problems**, not a
house style. Several classic patterns appear nowhere: **Observer, Command, Decorator, Builder,
Adapter, Proxy, Iterator, Visitor, Mediator, Facade, Flyweight, Bridge, Prototype.**

**If a new problem's natural answer is one of the unused patterns, use it.** Reaching for
Strategy because the previous file used Strategy is cargo-culting, and an interviewer will
find the seam immediately.

**Pick by trigger, not by habit:**

| If the requirement says... | The natural pattern is | Classic problem |
|---|---|---|
| "notify/subscribe when X changes", fan-out to listeners | **Observer / pub-sub** | Chat, stock ticker, auction |
| "undo/redo", "queue an action", "replay operations" | **Command** | Text editor, vending machine, remote |
| "add behaviour at runtime without subclassing" | **Decorator** | Coffee shop, pizza builder, I/O streams |
| "many optional params", complex validated construction | **Builder** | Complex request objects |
| "wrap an incompatible third-party API" | **Adapter** | Payment gateway integration |
| "lazy-load / access-control / cache in front of an object" | **Proxy** | Image loading, ORM lazy refs |
| "traverse a collection without exposing its structure" | **Iterator** | Custom collections, paginated feeds |
| "tree of objects treated uniformly" | **Composite** | File system, org chart, UI trees |
| "one operation across a varied object tree" | **Visitor** | AST/expression evaluation |
| "many objects coordinating pairwise" | **Mediator** | Air-traffic control, chat rooms |
| "algorithm skeleton fixed, steps vary" | **Template Method** | Job execution, report generation |
| "behaviour genuinely changes per state, with side effects" | **State** (real classes) | Elevator, vending machine, order lifecycle |

**Two judgement calls this standard insists on:**

1. **State pattern vs guarded enum.** Existing files use an enum with a `canTransitionTo()`
   guard, and that is correct when transitions carry no per-state behaviour. Use the **real
   State pattern** when entering a state has side effects or each state answers the same
   method differently - Elevator is the textbook case. Name your upgrade trigger either way.
2. **Composite is under-used, not over-used.** Any "tree treated uniformly" requirement
   (file system, org hierarchy, nested UI) wants Composite. Do not flatten it into a list
   plus recursion just because the previous problems did not need it.

**The test before naming any pattern:** *"What varies, and does this pattern isolate exactly
that variation?"* If you cannot answer in one sentence, you picked from memory, not from the
requirements - and Rule 3 (strongest alternative) will expose it.

### Cross-cutting: write for the SPOKEN round

- Add **narration lines** at the moments that score: *"Say this when you draw it: ..."*
- Anticipate follow-ups **inline**, phrased as one-breath spoken answers, not essays.
- State honest limits out loud: *"a lease narrows the double-execution window, it doesn't
  close it — idempotent handlers are the only complete answer."* Overclaiming is a red flag;
  calibrated confidence is a senior signal.
- **Differentiate from sibling files.** If a problem shares a mechanism with an existing file,
  lead with a *different* hard idea (parking-lot deliberately avoids re-running booking's
  interval-locking narrative and does partition tolerance instead).

---

## Section 7: Quality Checklist

Run before declaring any combined-round notes file ready.

> **Gate A is the one that matters.** The previous version of this checklist could be passed
> in full by a file that presented a bare class list, said "HAS-A" with no ownership call,
> justified patterns against strawmen, and drew a single final-state diagram - i.e. exactly
> the draft that had to be sent back twice. **If any Gate A box fails, the file is NOT ready,
> regardless of how many Gate B boxes pass.**

### GATE A - The Seven Authoring Rules (fail any one = not deliverable)

Each maps to a rule in Section 6.5. Verify by reading the file, not by remembering writing it.

**R1 - Derivation, not recall**
- [ ] Section 2.3.1 is a table: requirement -> noun/verb -> class -> why it earns its own type
- [ ] Every "why" names a **concrete failure** if inlined (not "cleaner", not "more modular")
- [ ] Requirements are quoted verbatim from 2.2, not paraphrased
- [ ] At least one row where you **decline** to create a class, with the reason
- [ ] Closing one-liner names the variation points

**R2 - Ownership resolved**
- [ ] The composition/aggregation definitions + `new`-vs-injected heuristic appear verbatim
- [ ] **Every** HAS-A resolves to composition or aggregation with lifecycle reasoning
- [ ] No instance of "can't function without it" used as an argument for composition
      (that is a required dependency, not ownership - the v1 error)
- [ ] At least one **USES** distinguished from HAS-A
- [ ] At least one deliberate **non-relationship** explained

**R3 - Strongest alternative, correct attribution**
- [ ] Table shape: `| Decision | Pattern Chosen | Strongest Alternative Considered | Why it loses |`
- [ ] Ask of each alternative: *would a competent engineer actually argue for this?*
      If no, it is a strawman - replace it
- [ ] Each claimed benefit is produced by the thing credited with it, not by a DI pattern,
      constraint, or data structure elsewhere in the design

**R4 - Staged HLD**
- [ ] Section 3.5 has `#### Stage 1` (naive) and `#### Stage 2` (fixed), not one final diagram
- [ ] >= 3 quantified breaking points in Stage 1, each showing arithmetic (`X / Y = Z, need W`)
- [ ] A DECISION table between stages (options / strength / weakness / verdict)
- [ ] Stage 2 explains "what each hop buys us"
- [ ] >= 1 remaining breaking point after Stage 2
- [ ] A self-identified "remaining known gap" named before the interviewer could find it

**R5 - Trade-offs with failure modes**
- [ ] Exactly 3 trade-offs, each with Chose / Gain / Lose / **Failure mode if wrong**
- [ ] Every "Lose" is real (never "none")
- [ ] Every failure mode is a concrete business consequence, not "it would be slower"

**R6 - Data model**
- [ ] Real SQL DDL: types, constraints, indexes, partitioning - not a field list
- [ ] Partial indexes where the hot query touches a subset
- [ ] Tenant key (`org_id`) on every table and leading the indexes
- [ ] `| Decision | Why | What breaks otherwise |` table present
- [ ] Deliberate **absences** explained (what you chose NOT to store, and why)
- [ ] Any class-field vs stored-column mismatch has an explained round-trip

**R7 - Pattern fits the problem**
- [ ] Every pattern answers *"what varies, and does this isolate exactly that variation?"*
      in one sentence
- [ ] No pattern reused merely because a sibling file used it
- [ ] Unused-but-applicable patterns considered (Observer, Command, Decorator, Builder,
      Adapter, Proxy, Iterator, Visitor, Mediator) - if the problem calls for one, it is used
- [ ] State-vs-guarded-enum decided deliberately, with the upgrade trigger named

**Cross-cutting**
- [ ] Section 2.3.5 exists with >= 6 follow-ups answered in one-breath spoken form
- [ ] Narration lines present at scoring moments ("Say this when you draw it: ...")
- [ ] At least one place states what the design does **not** guarantee
- [ ] If a mechanism overlaps a sibling file, this file leads with a different hard idea

### GATE B - Structure and mechanics

**Identity + Dual-Layer Map:**
- [ ] Verbatim source prompt quoted if one exists in the research files
- [ ] Salesforce angle stated - or its weakness acknowledged honestly
- [ ] HLD-box -> LLD-class(es) table exists for this specific problem
- [ ] Every HLD box has at least one class mapping; the interface per box is named
- [ ] A "zoom sentence" shows one component at both levels

**LLD Half:**
- [ ] All 12 sections present (2.1 - 2.12), with 2.3 split into 2.3.1 - 2.3.5
- [ ] Interface shown before any implementation (always)
- [ ] All design patterns named AND justified (not used silently)
- [ ] Concurrency: every race named + fix + **lock scope** + why that scope
- [ ] Coding skeleton ordered: enum -> interface -> impl -> registry -> orchestrator
- [ ] "What would you do differently?" has >= 2 concrete answers with trade-offs

**HLD Half:**
- [ ] Each clarifying question notes the architectural fork it forces
- [ ] Scale estimation has >= 3 numbers with units, and shows the arithmetic
- [ ] Architecture diagrams have >= 5 labeled boxes with data-flow arrows
- [ ] Deep dive covers the **riskiest** component, with an honest limitation
- [ ] API design includes a derivation note for a non-obvious choice
- [ ] Salesforce multi-tenancy angle written as a quotable spoken paragraph

**Navigation:**
- [ ] Opening protocol script present verbatim
- [ ] 5-7 pivot signals with scripted responses
- [ ] Bidirectional: >= 1 HLD->LLD and >= 1 LLD->HLD

**Overall:**
- [ ] Identity card has the time budget split (X min LLD, Y min HLD)
- [ ] TL;DR names a pattern, a quantified breaking point, a trade-off, and the Salesforce angle
- [ ] Changelog row present with the grounding source
- [ ] Length 700-850 lines (under ~600 = something was hand-waved)
- [ ] All code blocks are Java, mentally compilable, no pseudocode
- [ ] No literal escape artifacts (`\u2014` etc.) - grep before declaring done

### Fast verification commands

```bash
F=<problem>.md
grep -c '^### 2\.' $F        # expect 12
grep -c '^### 3\.' $F        # expect 10
grep -c '^#### Stage' $F     # expect >= 2   (R4)
grep -c 'BREAKING POINT' $F  # expect >= 4   (R4)
grep -ci 'composition or aggregation' $F   # expect >= 1 (R2)
grep -ci 'Alternative Considered' $F       # expect >= 1 (R3)
grep -c 'Follow-ups they will ask' $F      # expect 1     (cross-cutting)
grep -c 'Data Model' $F                    # expect >= 1  (R6)
grep -c 'Opening Protocol' $F              # expect 1
grep -c '\\u' $F             # expect 0     (escape artifacts)
wc -l $F                     # expect 700-850
```

---

## 🧾 TL;DR — What This Round Is Testing

Not two separate skills. **One skill: holding both zoom levels simultaneously.**

The interviewer will pivot between levels mid-conversation. They're watching to see if you
lose the thread — if switching from "what class handles routing" to "how does this scale to
10M users" makes you restart from scratch, or if you can answer both and show how they connect.

The winning move:
1. Open by confirming order and setting expectations
2. Build the dual-layer map in your head before starting
3. Name patterns in LLD as you draw them — don't make the interviewer extract the names
4. Raise multi-tenancy in HLD without being asked
5. At every pivot, connect the two views: *"at code level this is X; at system level this is Y"*

A candidate who can only do LLD OR HLD gets an offer at a lower band. A candidate who
navigates between them fluidly — naming the zoom transitions explicitly — reads as SMTS.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 2026 | Standard created for Salesforce SMTS HLD+LLD combined round. Synthesised from `LLD/notes-standards.md`, `Interview/DocuSign/r2-solutions/solution-notes-standards.md`, Salesforce-specific research (Roundz SMTS report, 8+ interview sources). |
