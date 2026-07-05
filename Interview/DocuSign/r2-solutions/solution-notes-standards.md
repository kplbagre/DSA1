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

### Section -1 — 🎯 What Is This System? (Pre-Interview Orientation)

**Placed before the interview delivery framework.** This is NOT a section you deliver in the interview — it is a reader-orientation block for you, the person studying. Its purpose is to ground you in what you're actually building before your working memory fills up with trade-offs.

Every reader should be able to answer three questions after reading this section:

1. What does this system do? (plain English, no jargon)
2. Which real companies have built it? (names + one-line description)
3. Why is it hard? (one sentence on the core scaling or correctness challenge)

**Format:**

```markdown
## 🎯 What Is This System?

**In plain English:** [2-sentence plain description — no jargon, no acronyms]

**Real-world examples:**

| System / Company | What they built |
|---|---|
| **[Company A]** | [one-line description] |
| **[Company B]** | [one-line description] |
| **[Company C]** | [one-line description] |

**Core user journey:** [One sentence from the user's perspective — what they do, what they get]

**Why it's hard to build at scale:** [One sentence naming the specific technical failure mode or correctness requirement]
```

**Rules:**
- Keep the whole section under 20 lines
- "In plain English" must be understandable by a non-engineer
- Real-world examples table: 4–6 rows; include the company that is most domain-relevant to the interview (for DocuSign questions: include DocuSign if applicable)
- "Why it's hard" names a concrete failure mode — not "it's complex" or "it requires scale"

**Placement:** After the opening `---` divider, before `## 🧠 How to Use This File` (or before `## Section 0` in files that don't have a How-to-Use block).

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

**⭐ NEW MANDATORY RULE — Quantified Stage Transition Thresholds:**

Every Stage N → Stage N+1 transition must include a specific number that triggers the evolution. Do not say "Stage 1 breaks" — say *why it breaks at what load*.

```markdown
BREAKING POINT: Stage 1 breaks at [X req/sec / Y GB / Z concurrent connections]
  because [specific resource — Postgres CPU / Redis memory / TCP connections / thread pool]
  is exhausted at that load. At this threshold, [observable symptom: P99 > X ms / OOM /
  connection refused]. This is why Stage 2 is needed.
```

**Format for each stage:**
```markdown
══════════════════════════════════════════════════
STAGE N — [Name] ([scale this handles])
══════════════════════════════════════════════════

[ASCII diagram]

BREAKING POINT: [specific metric] hits [specific number] at [specific load].
  Observable symptom: [what the user or engineer sees].
  Why Stage N+1 is needed: [one sentence].
```

**Why this matters:** An interviewer asking "at what point does Stage 1 fail?" expects a number. "It breaks under load" scores zero. "Single Postgres primary tops out at ~5K reads/sec; our peak is 3,300 redirect/sec. With 3 other query types running, CPU saturates at ~2,800 redirect/sec, causing P99 > 500ms — that's when we need Stage 2" scores full marks.

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

### Section 8 — 🌐 API Design

**Placement — universal rule: API design BEFORE HLD, always.**

> Validated by Hello Interview, interviewing.io, DesignGurus/Grokking (2024-2025 frameworks). Logic: API defines *what* the system does → HLD shows *how* it does it. You can't draw an HLD in the air without first naming what it needs to implement.

| Type | When to deliver | Time budget | Role |
|---|---|---|---|
| **Type B (Product Architecture)** | Minutes 8–13 — after entities (Section 3.5), before scale (Section 4) | 8–13 minutes, full derivation + stories | Primary deliverable. The API contract IS the answer. |
| **Type A (System Design)** | After scale/variation (Sections 4–5), before HLD (Section 6) | 3–5 minutes, concise | Supporting. Names the interface; HLD implements it. |
| **Mixed (A+B)** | After entities (Section 3.5), before scale — closer to Type B | 5–8 minutes | Both matter; API gives the vocabulary for the HLD discussion. |

**The one difference between Type A and Type B is depth, not position.** Both put API before HLD. Type B spends 8–13 minutes on it (it's the primary deliverable). Type A spends 3–5 minutes (quick boundary statement before the architecture conversation).

---

**Section 8 has three parts — all three are required:**

**Part 1 — 🧠 Derivation Framework (narrative, not a checklist)**

Do NOT write "Question 1: Who calls this? Question 2: What do they send?" — that's mechanical and doesn't survive stress. Instead, write a narrative derivation that shows the thought process applied to 2-3 actual FRs from Section 3:

```markdown
### 🧠 How to Derive These Endpoints (Reconstruct, Don't Recall)

Every endpoint starts from a functional requirement. The move is: **FR → operation → resource → HTTP method → contract.**

**"[FR text]"** → [operation type] → resource is `[noun]` → `[METHOD /path]`. Who calls it? [auth]. What's the minimum they send? [request]. What do they get back? [response — and WHY that specific field matters].

**"[FR with a constraint in it]"** → The FR itself names the constraint. This tells you: [specific API design implication — header, status code, response field].

**Validation check:** After deriving all endpoints, map each back to a FR. Orphan endpoints shouldn't exist. FRs with no endpoint are gaps.
```

**Rules for the derivation narrative:**
- Walk through 2-4 FRs — enough to teach the pattern, not all of them
- For each FR, show the full chain: FR text → operation → resource → method → at least one non-obvious contract detail
- At least one of the examples should show a FR constraint shaping the contract (idempotency, a specific status code, a specific response field)
- Keep the whole section under 20 lines

**Part 2 — Core Endpoints table (compact quick reference)**

```markdown
### Core Endpoints

| Method | Path | Auth | Request Body | Response | Status Codes |
|---|---|---|---|---|---|
| [METHOD] | /v1/... | [auth] | {fields} | {fields} | [codes] |
```

Rules:
- Keep the table to 6 columns maximum — do not add a Responsibility column (too wide)
- Status codes: always include at least one error code (4xx), not just 200/201
- Response: include only what the caller needs next — not the entire object
- 4–8 endpoints typical for Type B; 2–4 for Type A

**Part 3 — 🔍 Endpoint Stories (why each endpoint exists)**

After the table, one paragraph per endpoint:

```markdown
### 🔍 Endpoint Stories — Why Each One Exists

**`[METHOD /path]`** — [one sentence: what this endpoint does in plain English]. [One sentence: what makes it non-obvious or interesting — the status code choice, the response field that tells a story, the auth requirement, the probe the interviewer will ask]. [Optional: the FR it directly satisfies].
```

Rules:
- Not every endpoint needs the same depth — focus paragraph length on the interesting ones
- Each story must contain at least one thing the interviewer will probe on
- For Type A files where API is supporting, endpoint stories can be shorter — 1-2 sentences each
- Think as an interviewer: what would make me ask a follow-up about this endpoint?

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
- **Failure mode if wrong:** [what breaks technically — AND what the user/business experiences]

### Trade-off 2: ...
### Trade-off 3: ...
```

**The DocuSign PDF says:** "Focus on Trade-offs: We are more interested in seeing how you think through the pros and cons of different approaches."

**⭐ NEW MANDATORY RULE — Business Impact in Every Failure Mode:**

"Failure mode if wrong" must have TWO layers:
1. **Technical breakdown** — what component fails, what resource is exhausted
2. **Business impact** — what the user perceives, what the business metric takes a hit

```markdown
- **Failure mode if wrong:** [Technical]: [component] fails / [resource] exhausted.
  [Business]: Users experience [observable UX symptom]. For DocuSign, this means
  [specific product consequence — signing flow blocked / envelope delivery delayed /
  audit trail unavailable / customer support ticket spike].
```

**Example (good):**
> "Failure mode if wrong: if we chose sync entitlement calls, entitlement service 5s timeout → payment API P99 spikes. For DocuSign's Commerce Backend: customers who just paid see a spinning button for 5+ seconds. ~15% abandon and call support thinking the charge was taken. SLA breach = escalation to VP of Engineering."

**Example (bad — no business layer):**
> "Failure mode if wrong: entitlement service timeout causes payment API to return 500."

The bad example describes what breaks technically. The good example describes what the interviewer's manager would see in a post-incident review.

**Why this rule exists:** Senior engineers are trusted with on-call because they can answer: "What is the customer impact?" If your failure modes stop at the technical layer, you sound like a junior engineer who's never been paged at 3 AM.

---

### Section 11 — 🔐 DocuSign-Specific Depth

What makes YOUR answer DocuSign-flavored vs a generic answer from a textbook. This is where you show you understand their domain.

**Per question, this covers:**
- The B2B SaaS / multi-tenant implications
- The e-signature / legal compliance angle
- The specific features DocuSign's product has that your system must support
- KYC (Know Your Customer) / audit trail / non-repudiation where relevant

**⭐ NEW MANDATORY RULE — No Generic Dimension Mapping:**

Section 14 (DocuSign Dimensions Checklist) must NOT be filled with boilerplate. Each row must contain a product-specific, DocuSign-grounded sentence — not a template phrase.

**Bad (boilerplate, scores zero):**
> "Scalability: horizontal sharding handles growth."

**Good (product-grounded, scores full):**
> "Scalability: at DocuSign's 1.6M paying orgs, the entitlement read path sees 5,500 req/sec peak — DB reads are impossible; Redis cache with 30-second TTL is the only way to stay within the payment P99 SLO."

**The test for each dimension cell:**
- Does it name a specific number from Section 4 (scale estimation)?
- Does it name a specific DocuSign product scenario (not "a SaaS product")?
- Could this sentence appear verbatim in a post-incident RCA or architecture review?

If all three answers are NO → rewrite the cell.

**For Section 11 narrative content**, each point must connect to DocuSign's **actual business reality**:
- Name a DocuSign product tier, team, or customer segment
- Reference a compliance standard they actually certify to (SOC 2, GDPR, ESIGN Act, PCI-DSS)
- Describe a failure scenario that would generate a real customer support ticket at DocuSign

**Template sentence pattern for Section 11 depth points:**
> "For DocuSign's [specific team/product/customer type], [your design decision] is required because [specific compliance/business/product constraint]. Without it, [what specific DocuSign failure scenario occurs]."

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

**Structure:**
- [ ] **Section -1 (What Is This System?)** written — plain English description, 4–6 real-world examples, one-line user journey, one-line "why it's hard"
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

**Quality gates (NEW — added Jul 5, 2026):**
- [ ] **Section 6 breaking points**: Every stage transition has a quantified threshold — *"Stage N breaks at X req/sec because Y resource is exhausted at that load. Observable symptom: Z."* No stage transition says just "breaks" without a number.
- [ ] **Section 10 business impact**: Every "Failure mode if wrong" has two layers — (1) technical breakdown + (2) user/business consequence. *"P99 spikes → users experience → DocuSign metric hits."* No failure mode stops at the technical layer.
- [ ] **Section 14 DocuSign-specific**: Every dimension cell passes the 3-test: names a specific number from Section 4, names a specific DocuSign product scenario, could appear in a real RCA. No boilerplate "scalability: horizontal sharding" cells.
- [ ] **Status codes**: Every API endpoint's status codes are logically consistent with the assumed design (no 409 if custom codes are out of scope, no 200 where 201 is correct, etc.). Each 4xx code has a named trigger in the Endpoint Stories.

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
| Jul 5, 2026 | **3 new quality gates added to Pre-Write Checklist.** (1) Section 6 quantified breaking points: every stage transition requires a specific threshold (req/sec, GB, connections) and observable symptom — "breaks under load" is no longer acceptable. (2) Section 10 business impact: every failure mode requires two layers — technical breakdown + user/business consequence (what DocuSign customer sees, what metric takes a hit). (3) Section 14 DocuSign-specific dimensions: every checklist cell must pass a 3-point test (specific number from Section 4, specific DocuSign product scenario, RCA-ready prose). Template sentence pattern added. Pre-Write Checklist split into Structure and Quality Gates sections. |
| Jul 5, 2026 | **Section -1 "What Is This System?" added as a mandatory pre-section.** Added to all 14 existing solution files (A1, A2, A3, B1, C1, C2, CF1, D1, D2, D3, E1, E2) and codified in the Pre-Write Checklist. Purpose: ground the reader in what system they're solving before working memory fills up with trade-offs. Format: plain-English description + real-world examples table + one-line user journey + one-line "why it's hard." |
| June 2026 | File created. Defines 15-section solution note format, two interview types (System Design vs Product Architecture), requirements variation table as key differentiator, 60-minute time budget, DocuSign 7-dimension checklist. Based on research: RESHADED framework, hellointerview.com delivery framework, DocuSign PDF interview guide. |
