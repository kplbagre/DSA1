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

## 📄 Section 6: Per-Problem Notes File Format

Each problem gets one `.md` file: `Interview/Salesforce/HLD+LLD/<problem-name>.md`.

**Note:** The order below is LLD-first. If your round is HLD-first (the Roundz SMTS report
documents HLD→LLD on Notification Service), swap sections 2 and 3. Both halves are designed
to stand alone. The dual-layer map (section 1) applies regardless of order.

### Required Sections (exact order)

```
0. 🎯 Identity
   — problem name, format (HLD+LLD combined), time budget per half,
     frequency rank (from questions-by-frequency.md), Salesforce-specific angle

1. 🗺️ Dual-Layer Map
   — the HLD-box → LLD-class(es) mapping table for THIS problem
   — this is the first section because it forces you to understand
     both views before you write either half

2. 🔧 LLD Half
   — all 12 sections from LLD/notes-standards.md, compressed for 35-min delivery
   — see Section 3 of this standard for time allocation and adjustments

3. 🏗️ HLD Half
   — compressed DocuSign format: clarifying Qs, requirements, entities, scale,
     architecture diagram, breaking points, ONE deep dive, 3 trade-offs, API design
   — see Section 3 of this standard for what to omit

4. 🧭 Navigation Pivots — THIS Problem
   — 4–6 pivot signals specific to this problem
   — for each: what the interviewer says → what they want → your scripted response
   — separate from the generic pivot table in Section 2 — these are problem-specific

5. 🧾 TL;DR — Dual-Level Pitch
   — ≤ 5 sentences covering both LLD design and HLD architecture
   — the pitch you'd give if asked "walk me through your design in 60 seconds"
   — should name at least one pattern (LLD) and one trade-off (HLD)
```

---

## ✅ Section 7: Quality Checklist

Run before declaring any combined-round notes file ready.

**Dual-Layer Map:**
- [ ] HLD-box → LLD-class(es) table exists for this specific problem
- [ ] Every HLD box has at least one class mapping
- [ ] The interface contract per box is named

**LLD Half:**
- [ ] All 12 sections from `LLD/notes-standards.md` are present
- [ ] Interface shown before any implementation (always)
- [ ] All design patterns are named AND justified (not just used silently)
- [ ] Concurrency section: shared fields named + lock strategy named
- [ ] Coding skeleton has explicit ordering (enum → interface → impl → factory → orchestrator)
- [ ] "What would you do differently?" has ≥ 1 concrete answer

**HLD Half:**
- [ ] Each clarifying question has an architectural fork consequence noted
- [ ] Scale estimation has ≥ 3 numbers with units (QPS, storage/day, peak multiplier)
- [ ] Architecture diagram has ≥ 5 labeled boxes with data flow arrows
- [ ] Breaking points are quantified (name the bottleneck + the QPS/volume threshold)
- [ ] Each trade-off has a one-sentence business impact layer
- [ ] Salesforce multi-tenancy angle is explicitly documented

**Navigation:**
- [ ] ≥ 4 pivot signals documented with scripted responses
- [ ] Pivots are bidirectional (at least one HLD→LLD and one LLD→HLD)
- [ ] Opening protocol script is present (or cross-referenced from Section 2 here)

**Overall:**
- [ ] Identity card has time budget split (X min LLD, Y min HLD)
- [ ] TL;DR covers both LLD and HLD in ≤ 5 sentences
- [ ] All code blocks are Java, compiles mentally, no pseudocode

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
