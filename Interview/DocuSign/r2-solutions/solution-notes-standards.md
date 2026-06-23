# Solution Notes Standards — DocuSign R2

> **Read this before writing or reading ANY solution file in this folder.**
> This file defines the exact format, reasoning, and quality bar for every solution walkthrough.
> It is NOT a concept reference — it is a 60-minute interview answer framework.

---

## 🎯 Why This Format Exists

A concept note (in `SystemDesignConcepts/`) teaches you the mechanic — what consistent hashing is, how a Bloom filter works. A solution note teaches you **how to present a complete answer to a specific question in 60 minutes**, with the right structure, the right clarifying questions, and the right trade-off language.

The gap between "I know rate limiting" and "I nailed the rate limiting question" is entirely in this file.

---

## 📖 Two Interview Types — Know Which One You're Answering

The DocuSign PDF explicitly defines two different interview formats. Every solution file is tagged with one.

### Type A — System Design Interview
**What they test:** How low-level constraints affect high-level goals. Deeply distributed systems thinking.

**Key dimensions DocuSign evaluates (from PDF):**
- **Testability** — can the system be tested in isolation?
- **Usability** — is the API/interface intuitive?
- **Extensibility** — can new requirements be added cheaply?
- **Security** — is data protected at rest, in transit, at the boundary?
- **Availability** — what is the uptime SLO and how is it achieved?
- **Scalability** — how does the system behave at 10×, 100× load?
- **Observability & Traceability** — can you see what the system is doing? Can you trace a request?

**Example questions (from PDF):** Design a URL shortener, Build a Facebook chat, Architect a worldwide video distribution system.

**Questions in this folder that are Type A:** C1 (Rate Limiter), D3 (Notification Service)

---

### Type B — Product Architecture / Design Interview
**What they test:** Building a product or API that operates at large scale.

**Key areas DocuSign evaluates (from PDF):**
- **Storage data models** — schema design, SQL vs NoSQL choice
- **SOLID principles** — clean code, extensible design
- **Scalability** — same as Type A but at the product layer
- **Design patterns** — Factory, Observer, Strategy, etc.
- **Protocols** — REST vs gRPC vs WebSocket vs SSE
- **Data formats** — JSON vs Protobuf vs Avro

**Example questions (from PDF):** Design a service or product API, Design a chat service or feed API, Design an email server.

**Questions in this folder that are Type B:** C2 (Expense Report), C3 (Pagination API), D2 (Document Storage)

**Mixed (Type A + B):** D1 (Digital Signature) — requires both PKI system design AND API/data model design.

---

## 📐 Solution File Format — Exact Section Order

Every solution file has these sections, in this order. Do not skip any.

---

### Section 0 — Question Identity Card

```markdown
| | |
|---|---|
| **Question** | [exact question text] |
| **Interview Type** | Type A — System Design / Type B — Product Architecture / Mixed |
| **Confirmed or Likely** | ⭐ Confirmed asked / 🔶 Likely |
| **Concept notes prerequisite** | `XX-filename.md`, `YY-filename.md` |
| **DocuSign-specific angle** | [one line: what makes this question DocuSign-flavored] |
| **Time budget** | 5 min clarify → 5 min estimate → 15 min HLD → 15 min deep dive → 8 min trade-offs → 7 min Q&A buffer |
```

---

### Section 1 — 🚀 The One-Sentence Opener

The first words out of your mouth when the question appears. Confident, scoped, showing you know the space.

**Format:**
```
> "Before I start drawing, let me ask a few clarifying questions to make sure
>  I'm solving the right version of this problem..."
```

Then immediately pivot to Section 2 (clarifying questions). Never start drawing without scope.

**Why this matters:** Starting with clarifying questions is the #1 signal that distinguishes a senior engineer from someone who memorized an answer. The DocuSign PDF says: "Start with Requirements — when presented with a broad question, start by asking clarifying questions."

---

### Section 2 — 🔍 Clarifying Questions Script (Minutes 0–5)

NOT just a list of questions. For each question: **why you're asking** and **how the answer changes your architecture**.

This is the most important section. It shows the interviewer you think before you design.

**Format for each question:**

```markdown
**Q: "[exact question to ask]"**
- Why ask: [what this unlocks — what architectural fork does the answer create?]
- If answer is A → [architecture implication A]
- If answer is B → [architecture implication B]
```

**Target:** 4–6 questions. Stop asking and start designing after 5 minutes. Don't ask things you don't need.

**Universal questions to always ask (adapt to the question):**
1. Scale: "How many users/requests per day are we designing for?"
2. Consistency: "Does this system require strict consistency, or is eventual consistency acceptable?"
3. Latency: "What are the latency requirements? Is there an SLO?"
4. Read/write ratio: "Is this read-heavy, write-heavy, or balanced?"
5. Scope: "Is [feature X] in scope for today, or should I focus on [core feature]?"

---

### Section 3 — 📋 Requirements (Functional + Non-Functional)

After clarifying questions, state what you'll build. This is a verbal contract with the interviewer.

**Format:**

```markdown
**Functional Requirements (what the system does):**
- Users should be able to [core action 1]
- Users should be able to [core action 2]
- Out of scope: [what you're explicitly NOT building]

**Non-Functional Requirements (how well it does it):**
- Scale: [X DAU, Y requests/sec]
- Latency: [P99 < X ms]
- Availability: [99.9% SLO = ~9 hours downtime/year]
- Consistency: [strong / eventual — and why]
- Durability: [is data loss acceptable? For what time window?]
```

---

### Section 4 — 🔢 Scale Estimation (Minutes 5–8)

Quick envelope math. These numbers justify every architecture choice you make later. State them out loud — the interviewer is watching how you think about numbers.

**Format:**

```markdown
**Traffic:**
- DAU: [X million]
- Requests/sec (write): [X DAU × Y actions/day ÷ 86,400]
- Requests/sec (read): [write rate × read:write ratio]
- Peak (assume 3×): [X req/sec]

**Storage:**
- Per record: [X KB]
- Records/day: [write rate × 86,400]
- 1 year: [records/day × 365 × KB = GB/TB]

**Bandwidth:**
- Inbound: [write rate × payload size]
- Outbound: [read rate × response size]

**Key conclusions:**
- "At [X] writes/sec, a single Postgres instance [handles it fine / starts to strain]"
- "At [Y] GB/year, [we need an archiving strategy / fits comfortably in one region]"
```

---

### Section 5 — 🔄 Requirements Variation Table ⭐ KEY SECTION

**This is what separates a senior candidate from a junior one.**

For each major axis of variation, show how your architecture changes. The interviewer WILL vary the requirements — this section prepares you for every direction they can take it.

**Format:**

```markdown
| If the interviewer says... | Your architecture changes to... | The reasoning |
|---|---|---|
| "10K users/day" | [simple design] | [why simple works] |
| "100M users/day" | [scaled design] | [why scale forces this] |
| "Strict consistency required" | [synchronous, locking] | [why you can't be eventual] |
| "Eventual consistency OK" | [async, sharded] | [what you gain] |
| "P99 < 10ms latency" | [in-memory, cache-first] | [why DB can't do this] |
| "Multi-tenant B2B SaaS" | [tenant isolation added] | [DocuSign context] |
```

**Axes to always cover:**
1. Scale axis (small → large)
2. Consistency axis (strong → eventual)
3. Latency axis (strict SLO → relaxed)
4. Scope axis (single-tenant → multi-tenant / single-region → global)
5. Feature axis (MVP → full features)

---

### Section 6 — 🏗️ High-Level Architecture (Minutes 8–20)

ASCII diagram first, explanation second. Draw the boxes, then walk through the data flow.

**Format:**

```markdown
### ASCII Architecture Diagram

\`\`\`
[client] → [load balancer] → [API gateway] → [service] → [DB]
                                              ↓
                                           [cache]
                                              ↓
                                           [message queue] → [worker]
\`\`\`

**Data flow walkthrough (say this out loud):**
1. [Request comes in] → [what happens at each box] → [response goes back]
2. [Async path if applicable]

**Each box: one sentence on what it does and why it's there.**
```

**Rule:** Every box in the diagram must be justified. Don't draw a Kafka if you don't explain why you need it.

---

### Section 7 — 🔬 Core Component Deep Dives (Minutes 20–38)

Pick the 2-3 most critical / riskiest components and go deep. "Riskiest" = where the system most likely fails, where scale hits hardest, or what DocuSign specifically cares about.

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
[code, SQL schema, or pseudo-code as appropriate]
```

---

### Section 8 — 🌐 API Design (Type B questions + Type A where relevant)

Define the exact API contract. For DocuSign's product architecture questions, this is often the main deliverable.

**Format:**

```markdown
### Core Endpoints

| Method | Path | Auth | Request body | Response | Status codes |
|---|---|---|---|---|---|
| POST | /v1/... | JWT Bearer | {fields} | {fields} | 201, 400, 409 |

### Key Design Decisions:
- Idempotency: [how handled — Idempotency-Key header, PUT semantics]
- Versioning: [/v1/ in path vs Accept-Version header — and why]
- Pagination: [cursor vs offset — and why]
- Error format: [standard error body]
```

---

### Section 9 — 🗄️ Data Model

The database schema or data structure decisions. For Type B questions, this is often the second main deliverable.

**Format:**

```markdown
### Core Tables / Collections

\`\`\`sql
CREATE TABLE [table_name] (
    ...
);
\`\`\`

### Key Schema Decisions:
- [Decision 1]: [why]
- [Decision 2]: [why]
- Indexing strategy: [which indexes, why]
- SQL vs NoSQL choice: [decision + justification]
```

---

### Section 10 — ⚠️ Trade-offs + Failure Modes (Minutes 38–45)

Three must-cover trade-offs. For each: what you chose, what you gain, what you lose, what breaks.

**Format:**

```markdown
### Trade-off 1: [Consistency vs Availability / Sync vs Async / Simple vs Optimal]
- **Chose:** [option]
- **Gain:** [what]
- **Lose:** [what]
- **Failure mode if wrong:** [what breaks]

### Trade-off 2: ...
### Trade-off 3: ...
```

**The DocuSign PDF says:** "Focus on Trade-offs: We are more interested in seeing how you think through the pros and cons of different approaches."

---

### Section 11 — 🔐 DocuSign-Specific Depth

What makes YOUR answer DocuSign-flavored vs a generic answer from a textbook. This is where you show you understand their domain.

**Per question, this covers:**
- The B2B SaaS / multi-tenant implications
- The e-signature / legal compliance angle
- The specific features DocuSign's product has that your system must support
- KYC (Know Your Customer) / audit trail / non-repudiation where relevant

---

### Section 12 — 🔬 Where the Interviewer Will Probe

3 levels of follow-ups the DocuSign interviewer is likely to ask. With your prepared answer for each.

**Format:**

```markdown
### Surface Probe (Tier 1 — every candidate gets this)
**Q: "[question]"**
> [2-3 sentence answer]

### Deep Probe (Tier 2 — tests real understanding)
**Q: "[question]"**
> [2-4 sentence answer with specific technical detail]

### Cross-Concept Probe (Tier 3 — separates senior candidates)
**Q: "[question about interaction with another concept]"**
> [answer demonstrating cross-concept reasoning]
```

---

### Section 13 — 🐞 Common Mistakes on This Question

What candidates typically get wrong. Reading this BEFORE the interview prevents you from making the same mistakes.

**Format:**

```markdown
- **Mistake 1:** [what] → **Why it's wrong:** [why] → **What to say instead:** [correction]
- **Mistake 2:** ...
```

---

### Section 14 — 🧭 DocuSign Dimensions Checklist

From the DocuSign PDF — the 7 dimensions of System Design they evaluate. Mark which apply to your question and how you address them.

| Dimension | Relevant? | How your design addresses it |
|---|---|---|
| Testability | ✅ / — | [how] |
| Usability | ✅ / — | [how] |
| Extensibility | ✅ / — | [how] |
| Security | ✅ / — | [how] |
| Availability | ✅ / — | [how] |
| Scalability | ✅ / — | [how] |
| Observability & Traceability | ✅ / — | [how] |

---

### Section 15 — 🧾 TL;DR Answer Summary

If you had 60 seconds to summarize your entire answer, this is what you'd say. Read this the morning of the interview.

```
> "[One paragraph, 4-5 sentences, covering: the core design decision, the
>   key trade-off, the scale-handling mechanism, and the DocuSign-specific insight.]"
```

---

## ⏱️ 60-Minute Time Budget

| Phase | Minutes | What you're doing |
|---|---|---|
| Clarifying questions | 0–5 | Ask 4-6 targeted questions, confirm scope |
| Requirements + estimation | 5–10 | State FR/NFR, do envelope math out loud |
| High-level architecture | 10–20 | Draw ASCII/whiteboard diagram, walk through data flow |
| Core deep dives (2-3) | 20–38 | Go deep on the riskiest components |
| Trade-offs + failure modes | 38–45 | Three named trade-offs with reasoning |
| DocuSign-specific depth | 45–50 | KYC, compliance, audit trail, whatever applies |
| Interviewer Q&A buffer | 50–60 | Answer follow-ups from your probes list |

**Critical rule:** Do NOT over-index on deep dives and skip trade-offs. Interviewers consistently say the trade-off discussion is what distinguishes senior candidates. If you're at minute 42 and haven't covered trade-offs yet — stop the deep dive and pivot.

---

## 🧪 Pre-Write Checklist (Run Before Writing Each Solution File)

- [ ] Section 0 (Identity card) filled in — interview type confirmed
- [ ] Section 2 (Clarifying questions) has WHY for each question and the architectural fork
- [ ] Section 5 (Requirements variation) covers at least 5 axes of variation
- [ ] Section 7 (Deep dives) picks the RISKIEST components, not the most interesting ones
- [ ] Section 10 (Trade-offs) has exactly 3 named trade-offs with gain/lose/failure-mode
- [ ] Section 11 (DocuSign depth) is specific — not generic SaaS advice
- [ ] Section 12 (Probe questions) has all 3 tiers — surface, deep, cross-concept
- [ ] Section 13 (Common mistakes) names at least 2 real mistakes candidates make
- [ ] Section 14 (Dimensions checklist) filled in — each dimension either addressed or explicitly out of scope
- [ ] ASCII diagram is readable and every box is justified
- [ ] No concept note content reproduced here — cross-reference by link, don't copy

---

## 🔗 Resources Used in These Solution Files

All resources were pre-vetted in `SystemDesignConcepts/resources.md`. Key sources for solution-level content:

| Resource | What it adds to solutions | Primary for |
|---|---|---|
| **hellointerview.com — Delivery Framework** | The exact interview delivery structure (clarify → estimate → HLD → deep dive → trade-offs) used as the backbone of Section 6-10 | All questions |
| **ByteByteGo — System Design Interview book (ch. 4)** | RESHADED framework timing: 5 min requirements, 3 min estimation, 15 min HLD, 15 min deep dive | All questions |
| **Arpit Bhayani** | Concept-level depth for each question — feeds Section 7 (deep dives) | C1, D1, D3 |
| **DocuSign PDF** | Interview type classification, 7 evaluation dimensions, trade-off emphasis | Section 0, 14 |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. Defines 15-section solution note format, two interview types (System Design vs Product Architecture), requirements variation table as key differentiator, 60-minute time budget, DocuSign 7-dimension checklist. Based on research: RESHADED framework, hellointerview.com delivery framework, DocuSign PDF interview guide. |
