# Notes Standards — SystemDesignConcepts

> **Purpose:** Every concept note in this folder follows this exact structure. Define the standard once, write consistently forever. Review this before writing any new note.

---

## 🎯 Design Principles Behind This Format

1. **Mental model before mechanics.** The concept sticks in memory through analogy and intuition, not through algorithm steps.
2. **Real company names, not "imagine a system."** Swiggy, Razorpay, BookMyShow — not "Company X."
3. **Interview Q&A is the payoff.** The rest of the note builds toward this. These are the actual words you say in the room.
4. **ASCII visuals are mandatory** for any concept with state, flow, or sequence (AGENTS.md Rule 6).
5. **Lightweight, not academic.** Each note should be revisable in 20 minutes before an interview.

---

## 📐 Note Structure (Exact Section Order)

Every note has these sections, in this order:

---

### Section 0 — 📚 Further Reading (After the Note — Optional)
**Optional. 2-3 resources maximum.**

This section sits at the **BOTTOM** of every note, just before the Changelog. The note is the entry point — fully self-contained. These resources are for deeper exploration AFTER the reader has gone through the note.

**Design principle:** The note does not assume any external resource has been read. Everything needed to understand the concept and answer interview questions is inside the note itself.

**Format:**
```markdown
## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **[Video title]** — Channel (YouTube) | One-line on what this adds beyond the note | ~X min |
| **[Page title]** — hellointerview.com / arpitbhayani.me (URL) | One-line on what it adds | ~X min read |
```

**Rules:**
- Max 3 resources. Pick only if they add something beyond what's in the note.
- Always include the YouTube search term or direct URL.
- Time estimate is mandatory.
- Frame as "what it adds" not "what it covers" — reinforce that the note already covered the basics.

---

### Section 0.5 — 📖 What is X? (Explicit Definition)
**Mandatory. Length:** 5-8 lines maximum.

Appears IMMEDIATELY after the title/header, BEFORE "🎯 Why This Matters." This section explicitly answers the question a reader asks when they first see the title.

**Format:**

```markdown
## 📖 What is [Concept Name]?

**Full form:** [Acronym expansion or full technical name]

**Simple analogy:** [One everyday comparison that encapsulates the core idea]

**Core principle:** [1-2 sentences on what this concept does and why it matters]

**Why it matters in system design:** [One sentence connecting to scale/interviews]
```

**Requirements:**
- **Full form:** Expand the acronym or state the full technical name (e.g., "JWT = JSON Web Token", "Circuit Breaker Pattern")
- **Simple analogy:** One real-world comparison (theme park ticket, restaurant menu, bank bathroom key, library catalog, mailbox, etc.). Must be graspable to a non-engineer.
- **Core principle:** Explain the mechanism in plain English — what problem it solves, what guarantees it provides
- **Why it matters:** Connect to system design scale (performance, reliability, cost, distributed systems, etc.)

**Depth test:** A first-time reader should understand what the concept IS and why they should care, before diving into the mental model.

---

### Section 1 — 🎯 Why This Matters
**Length:** 3-5 lines maximum.

- One line: what problem this concept solves
- One line: which interview round it shows up in
- One line: why a senior engineer is expected to know this

**Do NOT:** Explain the concept here. Just establish why the reader should care.

---

### Section 2 — 🧠 The Mental Model
**Length:** 8-15 lines. This is the most important section.

- One everyday analogy that creates a **complete mental movie** — not just a name match. The reader should be able to retell the concept using only the analogy, without touching technical vocabulary.
- The analogy must cover: (1) the normal flow, (2) what goes wrong without this concept, (3) how this concept fixes it.
- Reference style: see how Kafka is explained as a "newspaper that never throws away old editions" in `Kpl-inv/project-update/new/MCSE_DATA_INGESTION.md` — the analogy is deep enough that partitions, offsets, and rebalancing all map naturally to parts of the story.
- End with one sentence: "The key insight is: ___."

**Depth test:** If you removed all technical words from this section and showed it to a non-engineer, they should still understand what the system does and why it's needed.

**Do NOT:** Dive into technical mechanics here. Save that for Section 4.
**Do NOT:** Use a shallow comparison ("It's like a queue"). The analogy must carry the full mechanic.

---

### Section 3 — 🎨 Visual — System Topology & Component Flow
**Mandatory for:** All architectural/system-design concepts. Every concept must show WHERE it fits in the overall system design architecture.

**Two-diagram requirement for architectural concepts:**

1. **Full System Topology diagram** — REQUIRED. Shows the complete stack from client to database, with the concept properly positioned within this hierarchy:
   - Client/Internet → CDN → Load Balancer → Service Pods → Cache Layer → Database
   - Labels each layer clearly
   - Shows data flow direction (↓ for requests, ↑ for responses)
   - Highlights where THIS specific concept lives and operates
   - Example: Load Balancer sits BETWEEN Internet/CDN and Service Pods; Circuit Breaker wraps calls FROM Service Pods TO other services; Cache sits BETWEEN Pods and Database

2. **Component Detail diagram** — REQUIRED. Shows internal mechanics/state transitions of the concept itself:
   - If concept has state (e.g., Circuit Breaker: closed → open → half-open)
   - If concept has message flow (e.g., Kafka producer → partitions → consumer groups)
   - If concept has algorithm steps (e.g., rate limiter bucket refill timeline)

**Rule:** Don't use "flat" request-response diagrams (Request → Component → Response). Every architectural concept must answer: "Where does this live in a real system? What sits before it? What sits after it?"

**Format for both diagrams:**
````markdown
### 🎨 Visual — <Topology: placement in system> + <Component: internal mechanic>

```
FULL SYSTEM TOPOLOGY:
┌─────────┐     ┌──────────┐     ┌────────────┐     ┌──────────┐     ┌──────────┐
│ Client  │────▶│   LB     │────▶│  Service   │────▶│  Cache   │────▶│   DB     │
│         │     │(This is │     │   Pods     │     │          │     │          │
└─────────┘     │ where    └────────────────┘     └──────────┘     └──────────┘
                │ LB sits)
                └──────────┘

COMPONENT DETAIL (e.g., Circuit Breaker States):
┌─────────┐
│ CLOSED  │──(failure threshold)──▶ ┌──────┐
│         │                         │ OPEN │
└─────────┘◀─(timeout expires)──────┴──────┘
    ▲                                   │
    │                                   │
    └──────(success)─────────┌──────────┘
                             │
                          ┌──────────┐
                          │HALF-OPEN │
                          └──────────┘

KEY INVARIANT:
   Component sits at layer X. Topology shows requests flow in direction Y. State diagram shows fault-handling guarantees.
```
````

**Use in ASCII:**
- `→ ← ↑ ↓` for data flow direction
- `┌ ┐ └ ┘ ├ ┤ ─ │` for boxes (keep width ≤ 80 columns)
- `✅ ❌` for success/failure markers
- Clear layer labels: "Client Tier", "Service Tier", "Data Tier"

---

### Section 4 — ⚙️ How It Actually Works
**Length:** 20-40 lines including code.

**Steps in plain English FIRST** (AGENTS.md Rule 2), then code.

Format:
````markdown
**Steps:**
1. **Step one** — what happens and why.
2. **Step two** — what happens and why.
3. **Step three** — what happens and why.

```java
// code with comments matching the numbered steps
```
````

Code rules (AGENTS.md Rule 1):
- Language tag: always ` ```java `
- One statement per line
- Always braced (`if`, `for`, `while`)
- Spaces around operators
- Working code only — no `...` placeholders

---

### Section 5 — 🏢 Real World — Where Companies Use This
**Length:** 4-6 bullet points.

Format per bullet:
```
- **CompanyName** (product/feature): why they use THIS concept specifically.
```

Requirements:
- Real company names — Swiggy, Razorpay, Amazon, BookMyShow, Flipkart, PhonePe, Uber, Zomato, etc.
- Real product context — not "they have high traffic" but "during Big Billion Day flash sales where 50K users hit the same last item"
- Show WHY this concept fits their specific constraint

---

### Section 6 — 🧭 When to Use vs When NOT to Use
**Format:** Decision table.

| Use this when | Do NOT use when |
|---|---|
| condition A | condition X |
| condition B | condition Y |

Then: **"The common mistake"** — one line on what engineers get wrong.

---

### Section 7 — ⚠️ Trade-offs
**Three fixed rows:**

| | |
|---|---|
| **You gain** | what this concept gives you |
| **You lose** | what this concept costs you |
| **Failure mode** | what breaks if you apply this in the wrong situation |

---

### Section 8 — 🔬 Interview Q&As
**Mandatory. 5-7 questions minimum.**

Format:
```
### Q: "Exact question the interviewer will ask"
> 2-4 sentence crisp answer. Senior signal in every sentence.
```

**Two tiers of questions — both are required:**

**Tier 1 — Surface questions** (what every candidate prepares):
- The basic "what is X?" question
- The "when would you use X?" question
- The "what's the trade-off?" question
- The worked example question (using a real problem — bus booking, flash sale, payment retry, etc.)

**Tier 2 — Cross/probe questions** (what separates senior candidates):
These are the questions an interviewer asks when they want to test whether you actually understand it or just memorized it. Every note MUST include at least 2 of these.

Examples of cross-question patterns:
- "What happens if [edge case]?" — e.g., "What if the Redis node holding your rate limiter state crashes?"
- "How does X interact with Y?" — e.g., "How does rate limiting interact with idempotency keys on retries?"
- "Why not just use [simpler alternative]?" — e.g., "Why not just use a database counter instead of Redis for rate limiting?"
- "How would you handle [scale change]?" — e.g., "Your API now has 10 regions. How does your rate limiter change?"
- "What breaks if you're wrong about [assumption]?" — e.g., "What if your clock skew between nodes is 2 seconds?"

**The test:** Read each Q&A answer. If a smart interviewer could immediately ask "but what about X?" and you have no answer in the note — that X needs to be a question in this section.

---

### Section 9 — 🧾 TL;DR — One Interviewer-Ready Line
**One sentence only.** This is what you say when you drop this concept naturally in an interview answer.

Format:
```
> "One sentence that demonstrates you know this concept and its trade-off, suitable for dropping mid-conversation."
```

---

### Section 10 — 🔗 Related Concepts (optional, 3-5 links)
Cross-links to other notes in this folder using relative paths.

---

## ✅ Pre-Publish Checklist

Before finalizing any note, verify:

- [ ] Section 0.5 (What is X?) present immediately after title, before "🎯 Why This Matters" — includes full form, analogy, core principle, and scale relevance
- [ ] Section 0 (Further Reading) present at BOTTOM of note — not the top. Note is self-contained; Section 0 is optional deeper-dive after reading.
- [ ] All 10 required sections present (Section 11 is optional)
- [ ] Section 2 has a concrete everyday analogy
- [ ] Section 3 has TWO ASCII visuals: (1) Full System Topology showing where concept sits in complete stack, (2) Component Detail showing internal mechanics/state. Both have KEY INVARIANT.
- [ ] Section 4 has English steps BEFORE code
- [ ] Section 4 code is valid Java (language-tagged, braced, one statement per line)
- [ ] Section 5 has ≥ 3 real company names with real context
- [ ] Section 8 has ≥ 5 Q&As — minimum 2 are Tier 2 cross/probe questions
- [ ] Section 2 analogy is deep enough to retell the concept without technical vocabulary (newspaper test)
- [ ] Section 3 diagram is only present if it genuinely speeds up understanding (not decorative)
- [ ] Every potentially-unfamiliar term is glossed at first use (AGENTS.md Rule 8)
- [ ] Any specific technology named in Section 4 (Lua, Redis sorted set, Kafka, etc.) has a `### What is X, and why does it fit here?` sub-section with a one-sentence plain-English definition AND an explicit "in an interview, if asked" answer sentence
- [ ] **Coverage completeness check:** Every strategy, algorithm, or pattern NAMED anywhere in the note has full coverage (steps + code or equivalent). A mention in a visual or real-world example without a corresponding implementation section is a gap — fix it before closing the file.
- [ ] No emojis outside the approved AGENTS.md palette
- [ ] Note is readable end-to-end in under 20 minutes

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | Standards file created. Format defined before writing any notes. |
| June 2026 | Three additions: (1) Section 2 analogy must be deep enough to retell without tech vocabulary — "newspaper test". (2) Section 3 diagrams only where they genuinely accelerate understanding, not for simple comparisons. (3) Section 8 now requires Tier 2 cross/probe questions — at least 2 per note, covering edge cases, interactions, and failure modes interviewers drill into. |
| June 2026 | Section 0 design reversal: moved from TOP to BOTTOM. Notes are the entry point — fully self-contained. Section 0 reframed as "Further Reading (Optional — after the note)". External resources are for deeper exploration, not prerequisites. |
| June 2026 | New checklist rule: any named technology in Section 4 must have a "What is X, and why does it fit here?" sub-section — plain-English definition + explicit interview answer. Codified from Lua explanation added to 02-rate-limiting.md. |
| June 2026 | **Section 3 overhaul — System Topology requirement (June 25).** All architectural concepts must now include TWO diagrams: (1) Full System Topology showing complete stack (Client → Internet → CDN → LB → Service Pods → Cache → DB) with concept properly positioned in this hierarchy, not flat "request-response" diagrams. (2) Component Detail showing internal mechanics/state transitions. Eliminates vague positioning; forces specificity about WHERE concept sits and WHAT layer precedes/follows it. |
| June 2026 | **Section 0.5 addition — Explicit "What is X?" definitions (June 25).** Added mandatory Section 0.5 between title and "Why This Matters". Every concept must now explicitly state: (1) Full form/acronym expansion, (2) Simple everyday analogy, (3) Core principle, (4) Why it matters in system design. This creates a "definition checkpoint" BEFORE diving into mental models, ensuring readers understand foundational concepts and terminology. Applies to all 33 concepts (concepts 01-23 retrofitted; 24-33 already compliant). |
| June 2026 | **Real World section audit — All 33 concepts verified (June 25).** Confirmed that all concepts 01-33 include Section 5 (🏢 Real World — Where Companies Use This) with ≥3 real company examples and specific context. Concepts 06 (Databases) and 27 (JWT Token Storage) were missing this section and have been updated with authoritative company examples (Amazon, Stripe, Netflix, Razorpay, Auth0, Google, LinkedIn, Uber, Shopify, Twitter/X, DocuSign). All 33 concepts now fully compliant with real-world context requirement. |
