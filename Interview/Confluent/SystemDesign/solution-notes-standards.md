# Solution Notes Standards — Confluent System Design

> **Read this before writing or reading ANY solution file in this folder.**
> This file defines the exact format, quality bar, and Confluent-specific rules for every solution walkthrough.
> It is NOT a concept reference — it is a 60-minute interview answer framework calibrated to Confluent's evaluation style.

---

## 🎯 Why This Format Exists

A solution note teaches you **how to present a complete answer to a specific question in 60 minutes**, with the right clarifying questions, the right API contract, the right trade-off language, and depth where Confluent actually probes.

The gap between "I know KV stores" and "I nailed the Distributed KV Store question at Confluent" is entirely in this file.

**The Confluent reality check (from research, Jul 2026):**
> "If you do any mistake in API design they highlight it as if the world has ended."
> "Small question bank — questions repeat. Mastering 4-5 core themes gives very high coverage."
> "Design round killed an otherwise perfect loop — SSE2 got all strong-hires in coding but lean-no-hire because no strong-hire in design."

Design is the **gating round**. A regular "hire" is insufficient — it must be a "strong hire."

---

## 📖 Two Confluent Interview Types — Know Which One You're Answering

Every solution file is tagged with one type. The type drives how you allocate your 60 minutes.

### Type 1 — API + Data Model Round

**What they test:** Can you define a precise REST contract? Can you design the SQL schema that backs it?

**Key signal from research:** "No architecture diagram required." The entire round is the API contract and the data model.

**Time allocation:**
- 0–5 min: Clarifying questions
- 5–10 min: Requirements + core entities
- 10–30 min: **API Design (primary deliverable — 20 minutes)**
- 30–45 min: Data Model / SQL Schema
- 45–52 min: Trade-offs on API and schema decisions
- 52–60 min: Interviewer Q&A

**Questions likely in this type:** Pure API design round (May 2025 LeetCode), DB/SQL/API round (Apr 2026).

---

### Type 2 — Full System Design Round

**What they test:** End-to-end distributed system thinking. API contract → architecture → deep dives → trade-offs.

**API Design still comes first** — even in full design, the API defines the boundary before you draw boxes.

**Time allocation:**
- 0–5 min: Clarifying questions
- 5–10 min: Requirements + entities + scale estimation
- 10–18 min: **API Design (5-8 minutes — boundary statement before HLD)**
- 18–30 min: High-Level Architecture
- 30–45 min: Core component deep dives (2-3 riskiest)
- 45–53 min: Trade-offs + failure modes
- 53–57 min: Confluent/Tableflow angle
- 57–60 min: Q&A buffer

**Questions in this type:** TempMail (Jul 2025), Distributed KV Store (Jul/Oct 2025), Aggregate News Feed (Jul 2025).

---

## 📐 Solution File Format — Exact Section Order

Every solution file has these sections, in this order. Do not skip any.

---

### Section -1 — 🎯 What Is This System? (Pre-Interview Orientation)

**Not delivered in the interview.** Reader-orientation block. Ground yourself in what you're building before working memory fills up with trade-offs.

Every reader should answer three questions after reading this section:

1. What does this system do? (plain English, no jargon)
2. Which real companies have built it?
3. Why is it hard? (the core scaling or correctness challenge in one sentence)

**Format:**

```markdown
## 🎯 What Is This System?

**In plain English:** [2-sentence plain description — no jargon, no acronyms]

**Real-world examples:**

| System / Company | What they built |
|---|---|
| **[Company A]** | [one-line description] |
| **[Company B]** | [one-line description] |

**Core user journey:** [One sentence from the user's perspective]

**Why it's hard to build at scale:** [One sentence naming the specific technical failure mode]

**Tableflow parallel:** [One sentence on how this maps to Kafka/Tableflow domain — signals domain fit]
```

**Rules:**
- Under 25 lines total
- "In plain English" must be understandable by a non-engineer
- 3–5 real-world examples
- "Why it's hard" names a concrete failure mode, not "it's complex"
- "Tableflow parallel" is mandatory — every question maps to Tableflow (see `sd-research.md` §Tableflow Connection)

---

### Section 1 — 🚀 The One-Sentence Opener

The first words out of your mouth when the question appears.

```
> "Before I start, let me ask a few clarifying questions to make sure
>  I'm solving the right version of this problem..."
```

Then immediately pivot to Section 2. **Never start drawing or naming technologies without scope.**

This is the #1 senior-engineer signal. Confluent interviewers are evaluating from the first sentence.

---

### Section 2 — 🔍 Clarifying Questions Script (Minutes 0–5)

NOT just a list of questions. For each question: **why you're asking** and **how the answer changes your architecture**.

**Format for each question:**

```markdown
**Q: "[exact question to ask]"**
- Why ask: [what this unlocks — what architectural fork does the answer create?]
- If answer is A → [architecture implication A]
- If answer is B → [architecture implication B]
```

**Target:** 4–6 questions. Stop at 5 minutes. Don't ask what you don't need.

**Universal questions to always ask (adapt to the question):**
1. Scale: "How many users/requests per day are we designing for?"
2. Consistency: "Does this require strict consistency, or is eventual consistency acceptable?"
3. Latency: "What are the latency requirements? Is there an SLO?"
4. Read/write ratio: "Is this read-heavy, write-heavy, or balanced?"
5. Scope: "Should I focus on [core feature] today, or include [extension]?"

---

### Section 3 — 📋 Requirements (Functional + Non-Functional)

After clarifying questions, state what you'll build. Verbal contract with the interviewer.

**Format:**

```markdown
**Functional Requirements (what the system does):**
- Users should be able to [core action 1]
- Users should be able to [core action 2]
- Out of scope: [what you are explicitly NOT building]

**Non-Functional Requirements (how well it does it):**
- Scale: [X DAU, Y requests/sec]
- Latency: [P99 < X ms]
- Availability: [99.9% SLO = ~9 hours downtime/year]
- Consistency: [strong / eventual — and why]
- Durability: [is data loss acceptable? For what time window?]
```

---

### Section 3.5 — 🗂️ Core Entities (~2 minutes)

Name the key data objects the system manages — entities and access patterns only. **No database names here.** DB choice belongs in Section 9 (Data Model) after scale numbers justify it.

**Format:**

```markdown
| Entity | What it represents |
|---|---|
| **[EntityName]** | [What it is + nature word: ephemeral / append-only / immutable / client-held / transactional] |
```

**Rules:**
- Two columns only: `Entity` and `What it represents` — **no Storage column**
- Nature words telegraph the access pattern without naming technology:
  - `ephemeral` → Redis or TTL-based storage
  - `append-only` → Cassandra or insert-only Postgres
  - `immutable` → S3 or versioned rows
  - `client-held` → not stored server-side
  - `transactional` → written in the same DB transaction as its triggering event
- 3–6 entities typical

---

### Section 4 — 🔢 Scale Estimation (Minutes 5–10, Type 2 only)

**Type 1 (API + Data Model) — skip or very brief.** The interviewer is focused on contract correctness, not capacity math.

**Type 2 (Full Design) — full envelope math.** These numbers justify every architecture choice downstream.

**Format:**

```markdown
**Traffic:**
- DAU: [X million]
- Writes/sec: [X DAU × Y actions/day ÷ 86,400]
- Reads/sec: [write rate × read:write ratio]
- Peak (3×): [X req/sec]

**Storage:**
- Per record: [X KB]
- Records/day: [write rate × 86,400]
- 1 year: [records/day × 365 × KB = GB/TB]

**Key conclusions (connect to architecture):**
- "At [X] writes/sec, single Postgres [handles it / starts to strain] — this is why [Section 7 choice]."
- "At [Y] GB/year, [archiving strategy / fits in one region]."
```

**⭐ MANDATORY RULE — Scale numbers must be referenced downstream.** Section 4 numbers must appear explicitly in Section 7 (HLD) and Section 8 (Deep Dives). If scale estimate isn't referenced, it was pointless to compute.

---

### Section 5 — 🔄 Requirements Variation Table ⭐ KEY SECTION

**What separates a senior candidate from a junior one.** For each major axis of variation, show how your architecture changes. The Confluent interviewer WILL vary the requirements — this section prepares you for every direction.

**Format:**

```markdown
| If the interviewer says... | Your architecture changes to... | The reasoning |
|---|---|---|
| "10K users/day" | [simple design] | [why simple works] |
| "100M users/day" | [scaled design] | [why scale forces this] |
| "Strict consistency required" | [synchronous, locking] | [why you can't be eventual] |
| "Eventual consistency OK" | [async, sharded] | [what you gain] |
| "P99 < 10ms latency" | [in-memory, cache-first] | [why DB can't do this] |
| "Multi-region" | [replication + conflict resolution] | [CAP trade-off made explicit] |
```

**Axes to always cover:**
1. Scale axis (small → large)
2. Consistency axis (strong → eventual)
3. Latency axis (strict SLO → relaxed)
4. Geographic axis (single-region → multi-region / global)
5. Feature axis (MVP → full product)

---

### Section 6 — ⭐ API Design ← CONFLUENT'S PRIMARY EVALUATION AXIS

> **MANDATORY RULE: API Design comes BEFORE HLD. Always. For both Type 1 and Type 2.**
>
> Validated by research: Confluent treats API contract as the primary deliverable. Errors here are "highlighted as if the world has ended." The HLD implements the API — you cannot define an architecture for an interface you haven't named yet.

**Time budget by type:**
- **Type 1 (API + Data Model):** 20 minutes — full derivation, multiple probing rounds
- **Type 2 (Full Design):** 5–8 minutes — boundary statement before entering HLD

**Reference:** API verb/code/header rules in **`api-design-cheatsheet.md`**. Do NOT reproduce those tables here — link them.

---

**Section 6 has three parts — all required:**

**Part 1 — 🧠 Derivation Framework (narrative, not a checklist)**

Show the thought process. Walk through 2-3 FRs, derive endpoints from first principles:

```markdown
### 🧠 How to Derive These Endpoints (Reconstruct, Don't Recall)

Every endpoint starts from a functional requirement: **FR → operation → resource → HTTP method → contract.**

**"[FR text]"** → [operation type] → resource is `[noun]` → `[METHOD /path]`.
Who calls it? [auth]. Minimum payload? [request]. What do they get back?
[response — and WHY that specific field matters to the caller].

**"[FR with constraint]"** → The constraint shapes the contract:
[idempotency key / specific status code / response field / pagination strategy].

**Validation check:** Map each endpoint back to a FR. Orphan endpoints shouldn't exist.
FRs with no endpoint are gaps.
```

**Rules:**
- Walk through 2-4 FRs — enough to teach the pattern
- For each FR: full chain from FR text → non-obvious contract detail
- At least one example where a FR constraint shapes the contract (idempotency, specific status code)
- Under 20 lines

**Part 2 — Core Endpoints table**

```markdown
| Method | Path | Auth | Request Body | Response | Status Codes |
|---|---|---|---|---|---|
| [METHOD] | /v1/... | [auth] | {fields} | {fields} | [codes] |
```

Rules:
- 6 columns max — no Responsibility column
- Status codes: always include at least one 4xx, not just 200/201
- Response: only what the caller needs next
- **Type 1:** 4–8 endpoints. **Type 2:** 2–4 endpoints.

**Part 3 — 🔍 Endpoint Stories (why each endpoint exists)**

One paragraph per endpoint explaining what makes it non-obvious:

```markdown
### 🔍 Endpoint Stories

**`[METHOD /path]`** — [what it does in plain English]. [What makes it non-obvious:
the status code choice, the idempotency requirement, the response field that tells a story,
the auth requirement]. [The FR it directly satisfies].
```

**⭐ MANDATORY RULE — Every status code needs a named trigger.** In Endpoint Stories, every 4xx code must name the exact condition that causes it. "404 if email not found" → what makes it "not found"? Expired? Never existed? Different user's email? Confluent probes exactly here.

---

### Section 7 — 🏗️ High-Level Architecture (Type 2 only, Minutes 18–30)

ASCII diagram first, explanation second. Draw boxes, then walk the data flow.

**Format:**

```markdown
### ASCII Architecture Diagram

\`\`\`
[client] → [API gateway] → [service] → [DB]
                            ↓
                         [cache]
                            ↓
                         [Kafka] → [worker / processor]
\`\`\`

**Data flow walkthrough (say this out loud):**
1. [Request comes in] → [what happens at each box] → [response back]
2. [Async path if applicable]

**Each box: one sentence on what it does and why it's there.**
\`\`\`
```

**⭐ MANDATORY RULE — Every box must be justified.** Don't draw Kafka if you can't explain why you need it. Confluent engineers probe this — they know exactly what Kafka's overhead is and whether it's warranted.

**⭐ MANDATORY RULE — Quantified Stage Transition Thresholds:**

Every Stage N → Stage N+1 transition needs a specific number.

```markdown
══════════════════════════════════════════════════
STAGE N — [Name] ([scale this handles])
══════════════════════════════════════════════════

[ASCII diagram]

BREAKING POINT: Stage N breaks at [X req/sec / Y GB / Z connections]
  because [specific resource: Postgres CPU / Redis memory / thread pool]
  is exhausted at that load.
  Observable symptom: [P99 > X ms / OOM / connection refused].
  Why Stage N+1 is needed: [one sentence].
```

"It breaks under load" scores zero. "Single Postgres primary tops out at ~5K reads/sec; our peak is 3,300 redirect/sec. With 3 query types, CPU saturates at ~2,800/sec, causing P99 > 500ms — that's when we need read replicas" scores full marks.

---

### Section 8 — 🔬 Core Component Deep Dives (Type 2 only, Minutes 30–45)

Pick the 2-3 most critical / riskiest components. "Riskiest" = where the system most likely fails, where scale hits hardest, or what Confluent specifically focuses on given their Kafka/streaming domain.

**Format per component:**

```markdown
### Deep Dive: [Component Name]

**Why this is the most critical component:**
[One sentence on why this is where the design lives or dies]

**Options considered:**
| Option | Pros | Cons |
|---|---|---|
| Option A | ... | ... |
| Option B | ... | ... |

**Decision: [Option X]**
Because [reason tied to the specific requirements from Section 3].
The trade-off I'm accepting is [what you lose].

**Implementation sketch:**
[code, SQL schema, or pseudo-code — actual syntax, not hand-waving]
```

**⭐ Confluent-specific: Bloom Filter awareness.** If the question involves large-scale membership checking (is this email address already taken? is this key alive?), proactively introduce Bloom filters and be prepared for a 10-minute deep dive on false positive rate, sizing, and when to use them. Research shows one round was consumed entirely by Bloom filter discussion. See `sd-research.md` §Bloom Filter depth.

---

### Section 9 — 🗄️ Data Model / SQL Schema

**For Type 1 (API + Data Model):** This is the co-primary deliverable alongside API Design. Spend 12-15 minutes here.

**For Type 2 (Full Design):** Goes inside Section 8 deep dive for the storage component.

**Format:**

```markdown
### Core Tables

\`\`\`sql
CREATE TABLE [table_name] (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    [column]    [type]    NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_[table]_[col] ON [table_name] ([column]);
\`\`\`

### Key Schema Decisions:
- **[Decision 1]:** [why — connect to NFR or access pattern]
- **[Decision 2]:** [why]
- **Indexing strategy:** [which indexes exist + the query they serve]
- **SQL vs NoSQL choice:** [decision + justification connected to access patterns]
```

**⭐ MANDATORY RULE — Actual DDL, not hand-waving.** Research (Apr 2026) shows Confluent explicitly evaluates SQL schema. "Use Postgres with an emails table" is not a schema — `CREATE TABLE` with column types, constraints, and `CREATE INDEX` is. Confluent probes: "What indexes would you add?" Be ready.

**⭐ MANDATORY RULE — Index every query.** For each access pattern defined in FRs, there must be a corresponding index or primary key that serves it. Unindexed queries on large tables are a probe target.

---

### Section 10 — ⚠️ Trade-offs + Failure Modes (Minutes 45–53)

Three must-cover trade-offs. For each: what you chose, what you gain, what you lose, what breaks.

**Format:**

```markdown
### Trade-off 1: [Consistency vs Availability / Sync vs Async / Simple vs Optimal]
- **Chose:** [option]
- **Gain:** [what]
- **Lose:** [what]
- **Failure mode if wrong:** [technical breakdown] + [streaming/Confluent business impact]
```

**⭐ MANDATORY RULE — Two-layer failure modes:**

Every "failure mode if wrong" must have two layers:
1. **Technical breakdown** — what component fails, what resource is exhausted
2. **Confluent/streaming business impact** — what a Kafka/streaming customer or the Tableflow pipeline experiences

```markdown
- **Failure mode if wrong:** [Technical]: [component] fails / [resource] exhausted at [load].
  [Streaming impact]: Kafka consumer lag grows unbounded / Iceberg table reads return stale snapshots /
  Tableflow pipeline falls behind real-time SLA / downstream consumers get duplicate events.
```

**Example (good):**
> "If we chose synchronous fan-out at 100K subscribers × 1M events/day = 100B writes/day, Cassandra write throughput is exceeded. Tableflow's Iceberg table sync falls behind — downstream analytics sees hours-old data instead of minutes-old."

**Example (bad — no streaming layer):**
> "Fan-out write path exceeds Cassandra throughput and writes drop."

**⭐ MANDATORY RULE — Every decision must be defended.** Confluent probes justification of every choice. Trade-off section is preparation for "why X over Y?" at any point in the interview. Don't just present one option — show you considered alternatives.

---

### Section 11 — 🌊 Confluent/Tableflow Angle

What makes YOUR answer Confluent-flavored vs a generic textbook answer. Show you understand their domain.

**Per question, cover the relevant subset of:**
- How this system's design maps to Kafka/Tableflow internals
- Where Kafka would be the ingestion backbone in this design (and why)
- How Apache Iceberg / Delta Lake / table format concepts apply
- Multi-cloud reliability considerations (Confluent operates AWS/Azure/GCP)
- How 99.99% uptime requirements shape the design
- Any Tableflow-specific insight (see `sd-research.md` §Tableflow Connection)

**⭐ MANDATORY RULE — No generic cloud advice.** "Use Kafka for reliability" is as weak as generic boilerplate. Confluent-specific means:
- Name what specific Kafka feature you're using and why (compacted topic = KV store; consumer groups = parallel processing; retention = ephemeral storage)
- Connect to Iceberg/Delta Lake where applicable
- Name the multi-cloud consideration if the system requires global distribution

**Template pattern:**
> "For Confluent's [specific team / product feature], [your design decision] mirrors how [Kafka/Tableflow/Iceberg] handles [equivalent problem]. Specifically: [technical parallel]. This signals you're building for streaming-native consistency."

---

### Section 12 — 🔬 Where the Interviewer Will Probe

3 tiers of follow-ups, with prepared answers.

**Format:**

```markdown
### Surface Probe (Tier 1 — every candidate gets this)
**Q: "[question]"**
> [2-3 sentence answer]

### Deep Probe (Tier 2 — tests real understanding)
**Q: "[question]"**
> [2-4 sentence answer with specific technical detail — numbers, algorithm name, trade-off]

### Cross-Concept Probe (Tier 3 — separates senior candidates)
**Q: "[question connecting this to another concept]"**
> [answer demonstrating cross-concept reasoning]
```

**⭐ Confluent probe patterns from research:**
- API probes: "Why that status code and not 400?" / "What happens if the client retries?" / "How do you handle concurrent writes to the same resource?"
- Bloom filter probes: "What's your false positive rate?" / "How do you size it?" / "What happens on a false positive — does the system break?"
- Distributed systems probes: "What happens during a network partition?" / "How do you handle split-brain?" / "What's your consistency model and which CAP corner are you in?"

---

### Section 13 — 🐞 Common Mistakes on This Question

What candidates typically get wrong. Reading this BEFORE the interview prevents you from making the same mistakes.

**Format:**

```markdown
- **Mistake 1:** [what] → **Why it's wrong:** [why] → **What to say instead:** [correction]
- **Mistake 2:** ...
```

**⭐ Always include (adapt to the question):**
- The mistake of starting with personalization/ranking for feed questions (start with ingestion reliability)
- The mistake of using wrong HTTP verb or status code
- The mistake of skipping index justification in SQL schema
- The mistake of not quantifying why Stage 1 breaks

---

### Section 14 — 🧭 Confluent Evaluation Axes Checklist

From research (`sd-research.md` §Primary evaluation axes) — the 6 axes Confluent evaluates. Mark which apply and how you address them.

| Axis | Relevant? | How your design addresses it |
|---|---|---|
| **API Design Precision** | ✅ / — | [specific verbs, codes, headers you got right] |
| **Trade-off Defense** | ✅ / — | [which decisions you can justify verbally] |
| **SQL / Data Modeling** | ✅ / — | [actual DDL, indexes, SQL vs NoSQL choice] |
| **Distributed Systems** | ✅ / — | [partitioning, replication, fault tolerance, CAP] |
| **Pipeline Resilience** | ✅ / — | [ingestion reliability, retry, ordering, at-least-once] |
| **Concurrency** | ✅ / — | [consistency model, race conditions, thread safety] |

**⭐ MANDATORY RULE — No boilerplate cells.** Each ✅ cell must contain a product-specific sentence tied to this question's design — not template phrases.

**Bad:** "API Design: use REST best practices."
**Good:** "API Design: `POST /v1/emails` returns 201 with `Location` header; `GET /v1/emails/{id}/messages` uses cursor pagination because offset breaks with TTL-based deletions."

The test for each cell: could it appear verbatim in a Confluent architecture review? If no → rewrite.

---

### Section 15 — 🧾 TL;DR Answer Summary

If you had 60 seconds to summarize your entire answer, this is what you'd say. Read this the morning of the interview.

```markdown
> "[One paragraph, 4-5 sentences, covering: the core design decision, the key API contract choice,
>   the scale-handling mechanism, the trade-off you'd defend first, and the Confluent/Tableflow parallel.]"
```

---

## ⏱️ 60-Minute Time Budget

### Type 1 — API + Data Model Round

| Phase | Minutes | What you're doing |
|---|---|---|
| Clarifying questions | 0–5 | 4-6 questions, confirm scope |
| Requirements + entities | 5–10 | State FR/NFR, name entities |
| **API Design (primary)** | 10–30 | Derivation + endpoints + stories — this IS the interview |
| Data Model / SQL Schema | 30–45 | Actual DDL, indexes, SQL vs NoSQL justification |
| Trade-offs | 45–52 | 3 trade-offs: API choices + schema choices |
| Interviewer Q&A | 52–60 | Answer probes from your Tier 1/2/3 list |

### Type 2 — Full System Design Round

| Phase | Minutes | What you're doing |
|---|---|---|
| Clarifying questions | 0–5 | 4-6 targeted questions |
| Requirements + entities + scale | 5–10 | FR/NFR, entities, envelope math |
| **API Design (boundary)** | 10–18 | Endpoints, key contract decisions |
| High-Level Architecture | 18–30 | ASCII diagram, data flow walkthrough, stage transitions |
| Core deep dives (2-3) | 30–45 | Riskiest components with quantified break points |
| Trade-offs + failure modes | 45–53 | 3 trade-offs, two-layer failure modes |
| Confluent angle | 53–57 | Kafka/Tableflow parallel |
| Interviewer Q&A | 57–60 | Tier 1/2/3 probes |

**Critical rule:** Do NOT over-index on deep dives and skip trade-offs. At minute 48 without trade-offs → stop the deep dive and pivot. Trade-off discussion is what distinguishes senior candidates.

---

## 🧪 Pre-Write Checklist

Run before writing each solution file.

### Structure
- [ ] Section -1 (What Is This System?) written — plain English, real-world examples, Tableflow parallel
- [ ] Interview type tagged: Type 1 or Type 2
- [ ] Section 2 (Clarifying questions) has WHY for each question + the architectural fork
- [ ] Section 5 (Requirements variation) covers at least 5 axes
- [ ] Section 6 (API Design) has all 3 parts: derivation narrative + endpoint table + endpoint stories
- [ ] Section 9 (Data Model) has actual `CREATE TABLE` DDL with indexes (not prose)
- [ ] Section 10 (Trade-offs) has exactly 3 with gain/lose/failure-mode
- [ ] Section 11 (Confluent angle) is specific — not generic cloud advice
- [ ] Section 12 (Probes) has all 3 tiers
- [ ] Section 13 (Common mistakes) names at least 2 real mistakes
- [ ] Section 15 (TL;DR) is 4-5 sentences covering core decision + API choice + trade-off + Confluent parallel
- [ ] ASCII diagram (Type 2): every box justified in walkthrough text
- [ ] No concept note content reproduced inline — cross-referenced by link to existing notes

### Quality Gates (run before saving)

- [ ] **API status codes**: Every 4xx code in Endpoint Stories has a named trigger — the exact condition that causes it. Not just "404 if not found" — why would it be not found?
- [ ] **Section 7 breaking points (Type 2)**: Every stage transition has a quantified threshold — specific req/sec, GB, or connection count + observable symptom. No stage says "breaks under load" without a number.
- [ ] **Section 10 two-layer failure modes**: Every "failure mode if wrong" has (1) technical breakdown + (2) Confluent/streaming business impact. No failure mode stops at "returns 500."
- [ ] **Section 14 Confluent axes**: Every ✅ cell is product-specific — names a concrete design decision from this file, not a template phrase.
- [ ] **Section 9 DDL completeness**: Every access pattern in FRs has a corresponding index or PK. Spot the unindexed queries before the interviewer does.
- [ ] **Scale estimates referenced (Type 2)**: Numbers from Section 4 are cited at least once in Section 7 (HLD) and Section 8 (Deep Dives). If not, either the estimate was useless or the architecture wasn't justified.
- [ ] **Ingestion-first framing (feed/pipeline questions)**: Does the HLD start with reliable ingestion, NOT ranking or personalization? (Research: candidates starting with ML/ranking were dinged.)

---

## 🔗 Reference Files in This Folder

| File | Purpose |
|---|---|
| **`sd-research.md`** | Source of truth for which questions are confirmed, frequency, evaluation axes, candidate tips |
| **`api-design-cheatsheet.md`** | HTTP verbs, status codes, headers, pagination, URL rules, idempotency — cross-reference from Section 6, don't reproduce |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 2026 | File created. Adapted from DocuSign `r2-solutions/solution-notes-standards.md` with Confluent-specific calibration. Removed: Question Identity Card (Section 0), "How to use this file" preamble, DocuSign 7-dimension section, DocuSign interview type classification. Added: Confluent Type 1/Type 2 split (API+Data Model vs Full Design), Confluent Evaluation Axes Checklist (6 axes from `sd-research.md`), Confluent/Tableflow Angle section, Bloom filter mandatory awareness, ingestion-first rule for pipeline questions, two-layer failure modes in streaming context, DDL-completeness and index-coverage quality gates. API Design moved to Section 6 with elevated MANDATORY rules reflecting Confluent's gating behavior on API mistakes. |
