# Solution Notes Standards — Disney R3 (Onsite System Design)

> **Read this before writing or reading ANY solution file in this folder.**
> This file defines the exact format, reasoning, and quality bar for every solution walkthrough.
> It is NOT a concept reference — it is a 60-minute interview answer framework adapted for Disney's onsite.

---

## 🎯 Why This Format Exists

A concept note (in `SystemDesignConcepts/`) teaches you the mechanic. A solution note teaches you **how to present a complete answer to a specific question in 60 minutes**, with the right structure, the right clarifying questions, and the right trade-off language.

The gap between "I know Redis sorted sets" and "I nailed the leaderboard question" is entirely in this file.

---

## 🏰 Disney R3 — What You Need to Know

Disney's onsite is **materially different from FAANG**:

- **Lower depth bar.** Interviewers are often domain engineers, not dedicated interviewers. One solid deep dive is enough — don't rush to cover 3.
- **No standardized process.** Questions are team-dependent. Research which org is hiring and what systems that team owns.
- **Guest-centric framing wins.** Disney interviewers respond strongly when you connect technical decisions to guest experience. "This means a player sees their rank in under 100ms" beats "this reduces P99 latency."
- **Creativity is valued.** A non-obvious extension (e.g., rank delta animation, park-specific leaderboard for a ride game) shows imagination — Disney's core value.
- **Casual, collaborative tone.** This is not an adversarial interrogation. Treat it as a whiteboarding session with a colleague. Think out loud.

---

## 📐 Solution File Format — Exact Section Order

Every solution file has these sections, in this order. Do not skip any.

---

### Section -1 — 🎯 What Is This System? (Pre-Interview Orientation)

NOT delivered in the interview — this is your reader orientation before your working memory fills up.

```markdown
## 🎯 What Is This System?

**In plain English:** [2-sentence plain description — no jargon, no acronyms]

**Real-world examples:**
| System / Company | What they built |
|---|---|
| **[Company A]** | [one-line description] |

**Core user journey:** [One sentence from the user's perspective]

**Why it's hard to build at scale:** [One sentence naming the specific technical failure mode]
```

Rules: < 20 lines. Plain English understandable by a non-engineer. Include Disney if directly applicable.

---

### Section 0 — Question Identity Card

```markdown
| | |
|---|---|
| **Question** | [exact question text] |
| **Interview Type** | Type A — System Design |
| **Confirmed or Likely** | ⭐ Confirmed / 🔶 Likely |
| **Concept notes prerequisite** | `XX-filename.md` |
| **Disney-specific angle** | [one line: what makes this question Disney-flavored] |
| **Time budget** | 5 min clarify → 5 min estimate → 15 min HLD → 15 min deep dive → 8 min trade-offs → 7 min Q&A buffer |
```

---

### Section 1 — 🚀 The One-Sentence Opener

First words out of your mouth. Scoped, confident, not rushing to design.

```
> "Before I start drawing, let me ask a few clarifying questions to make sure
>  I'm solving the right version of this problem..."
```

---

### Section 2 — 🔍 Clarifying Questions Script (Minutes 0–5)

For each question: **why you're asking** and **how the answer forks your architecture**.

```markdown
**Q: "[exact question]"**
- Why ask: [architectural fork unlocked]
- If answer is A → [implication A]
- If answer is B → [implication B]
```

Target: 4–6 questions. Stop after 5 minutes — assume and proceed.

---

### Section 3 — 📋 Requirements (Functional + Non-Functional)

Verbal contract with the interviewer. State what you're building.

```markdown
**Functional Requirements:**
- Users should be able to [core action]
- Out of scope: [explicit exclusions]

**Non-Functional Requirements:**
- Scale: [X DAU, Y req/sec]
- Latency: [P99 < X ms]
- Availability: [99.9% SLO]
- Consistency: [strong / eventual + why]
- Durability: [data loss tolerance]
```

---

### Section 3.5 — 🗂️ Core Entities (~2 minutes)

Name the key data objects. **No database names.** DB choice belongs in Section 6 after Section 4 scale numbers justify it.

```markdown
| Entity | What it represents |
|---|---|
| **[EntityName]** | [What it is + nature word: ephemeral / append-only / immutable / derived / transactional] |
```

Nature words telegraph the access pattern without naming a technology:
- `ephemeral` → Redis or in-memory
- `append-only` → Cassandra or Postgres insert-only
- `immutable` → S3 or versioned rows
- `derived` → computed from another entity; can be rebuilt
- `transactional` → written in same DB transaction as the triggering event

---

### Section 4 — 🔢 Scale Estimation (Minutes 5–8)

Quick envelope math. State numbers out loud — the interviewer watches how you think.

```markdown
**Traffic:**
- DAU: [X million]
- Writes/sec: [DAU × actions/day ÷ 86,400]
- Reads/sec: [write rate × read:write ratio]
- Peak (assume 3×): [X req/sec]

**Storage:**
- Per record: [X bytes]
- Records/day: [write rate × 86,400]
- 1 year: [GB/TB]

**Key conclusions:**
- "At [X] writes/sec, [component] [handles it / starts to strain]"
```

---

### Section 5 — 🔄 Requirements Variation Table ⭐ KEY SECTION

For each axis of variation, show how your architecture changes. The interviewer WILL vary requirements.

```markdown
| If the interviewer says... | Your architecture changes to... | The reasoning |
|---|---|---|
| "10K users/day" | [simple design] | [why simple works] |
| "100M users/day" | [scaled design] | [why scale forces this] |
```

Axes to always cover: scale, consistency, latency, scope (single-region vs. global), feature scope.

---

### Section 6 — 🏗️ High-Level Architecture (Minutes 8–20)

ASCII diagram first, explanation second.

**⭐ MANDATORY — Quantified Stage Transition Thresholds:**

Every stage transition must name the specific number and resource that breaks:

```markdown
BREAKING POINT: Stage N breaks at [X req/sec / Y GB / Z connections]
  because [specific resource] is exhausted.
  Observable symptom: [P99 > X ms / OOM / connection refused].
  Why Stage N+1 is needed: [one sentence].
```

Format:
```markdown
══════════════════════════════════════════════════
STAGE N — [Name] ([scale this handles])
══════════════════════════════════════════════════
[ASCII diagram]

BREAKING POINT: ...
```

---

### Section 7 — 🔬 Core Component Deep Dives (Minutes 20–35)

**Disney bar: 1–2 deep dives** (not 3 like FAANG). Pick the **riskiest** component — where the design most likely fails.

```markdown
### Deep Dive: [Component Name]

**Why this is the most critical component:**
[One sentence]

**Options considered:**
| Option | Pros | Cons |
|---|---|---|

**Decision: [Option X]**
Because [reason tied to requirements].
The trade-off I'm accepting: [what you lose].

**Implementation sketch:**
[code or pseudo-code]
```

---

### Section 8 — 🌐 API Design

Position: after Core Entities (Section 3.5), before Scale Estimation (Section 4). 3–5 minutes for Type A.

**Three parts (all required):**

**Part 1 — Derivation Framework**
Show FR → operation → resource → HTTP method → contract. Walk 2–3 FRs.

**Part 2 — Core Endpoints Table**
```markdown
| Method | Path | Auth | Request Body | Response | Status Codes |
|---|---|---|---|---|---|
```
4–6 endpoints. Always include at least one error code (4xx).

**Part 3 — Endpoint Stories**
One paragraph per interesting endpoint — what makes it non-obvious.

---

### Section 9 — 🗄️ Data Model

Schema decisions and justification.

```markdown
### Core Tables / Collections
\`\`\`sql
CREATE TABLE [table_name] (...);
\`\`\`

### Key Schema Decisions:
- [Decision]: [why]
- Indexing strategy: [indexes + why]
- SQL vs NoSQL choice: [decision + justification]
```

---

### Section 10 — ⚠️ Trade-offs + Failure Modes (Minutes 35–45)

Three named trade-offs. For each: what you chose, gain, lose, what breaks.

**⭐ MANDATORY — Business Impact in Every Failure Mode:**

Every "Failure mode if wrong" has TWO layers:
1. **Technical breakdown** — what component fails, what resource is exhausted
2. **Business impact** — what the guest/user experiences, what Disney metric takes a hit

```markdown
- **Failure mode if wrong:** [Technical]: [component] fails / [resource] exhausted.
  [Business]: Guests experience [observable UX symptom]. For Disney, this means
  [specific product consequence — leaderboard dark during peak event / park app 500 / score lost].
```

---

### Section 11 — 🏰 Disney-Specific Depth

What makes YOUR answer Disney-flavored vs. a generic textbook answer.

**Per question, cover:**
- The guest experience angle (how does your design affect what the guest sees?)
- Disney platform specifics: My Disney Experience app, Play Disney Parks app, Disney+, ESPN Fantasy
- Time-windowed or event-specific context (seasonal events, park ride games, streaming peaks)
- "UX of Victory" — Disney games emphasize the feeling of winning, not just the number

**Template sentence pattern:**
> "For Disney's [specific app/team/event], [your design decision] is required because [specific constraint]. Without it, [specific Disney failure scenario]."

**⭐ MANDATORY — No Generic Dimension Mapping:**
Each cell in Section 14 must pass the 3-test:
1. Names a specific number from Section 4
2. Names a specific Disney product scenario
3. Could appear verbatim in a post-incident review

---

### Section 12 — 🔬 Where the Interviewer Will Probe

3 tiers of follow-ups with prepared answers.

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

What candidates typically get wrong. Read BEFORE the interview.

```markdown
- **Mistake:** [what] → **Why it's wrong:** [why] → **What to say instead:** [correction]
```

---

### Section 14 — 🧭 Disney Interview Signals Checklist

Disney evaluates across these dimensions. Mark which apply and how your design addresses them.

| Signal | Relevant? | How your design addresses it |
|---|---|---|
| Guest-Centric Thinking | ✅ / — | [how the design improves what the guest sees/feels] |
| Technical Depth | ✅ / — | [specific data structure, algorithm, or protocol choice] |
| Imagination & Creativity | ✅ / — | [non-obvious extension or Disney-specific insight] |
| Trade-off Clarity | ✅ / — | [named trade-off with quantified reasoning] |
| Scalability | ✅ / — | [specific scale number from Section 4 + how design handles it] |
| Reliability | ✅ / — | [failure mode + recovery path] |
| Communication Clarity | ✅ / — | [did you drive the conversation? did you use whiteboard effectively?] |

---

### Section 15 — 🧾 TL;DR Answer Summary

60-second summary. Read the morning of the interview.

```
> "[4-5 sentences: core design decision, key trade-off, scale-handling mechanism, Disney-specific insight]"
```

---

## ⏱️ 60-Minute Time Budget

| Phase | Minutes | What you're doing |
|---|---|---|
| Clarifying questions | 0–5 | Ask 4-6 targeted questions, confirm scope |
| Requirements + estimation | 5–10 | State FR/NFR, do envelope math out loud |
| API design | 10–13 | Define the interface contract |
| High-level architecture | 13–23 | Draw ASCII diagram, walk through data flow with breaking points |
| Core deep dives (1–2) | 23–35 | Go deep on riskiest components only |
| Trade-offs + failure modes | 35–45 | Three named trade-offs with business impact |
| Disney-specific depth | 45–50 | Guest experience, park/streaming context |
| Interviewer Q&A buffer | 50–60 | Answer probes from your prepared list |

**Disney-specific rule:** Keep 15 minutes for Q&A. Disney interviewers are often casually curious — they'll pivot to tangents you didn't plan for. Having slack time lets you engage without panic.

---

## 🧪 Pre-Write Checklist

**Structure:**
- [ ] Section -1 written — plain English, real-world examples, user journey, why it's hard
- [ ] Section 0 filled in
- [ ] Section 2 has WHY for each question and the architectural fork
- [ ] Section 5 covers ≥ 5 axes of variation
- [ ] Section 7 picks RISKIEST, not most interesting — and caps at 1–2 deep dives
- [ ] Section 10 has exactly 3 named trade-offs
- [ ] Section 11 is Disney-specific — not generic SaaS advice
- [ ] Section 12 has all 3 tiers
- [ ] Section 13 has ≥ 2 common mistakes
- [ ] Section 14 filled in with Disney-grounded sentences
- [ ] ASCII diagram readable and every box justified

**Quality gates:**
- [ ] Section 6 breaking points: every stage transition has a quantified threshold (req/sec, GB, connections) + observable symptom
- [ ] Section 10 business impact: every failure mode has technical breakdown + guest/business consequence
- [ ] Section 14 Disney-specific: every cell passes the 3-test (specific number, specific Disney scenario, RCA-ready)
- [ ] Redis commands use `ZREVRANK` / `ZREVRANGE` (descending) for leaderboard rank — NOT `ZRANK` / `ZRANGE`

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 21, 2026 | **File created.** Disney R3 onsite — adapted from DocuSign r2-solutions/solution-notes-standards.md. Key changes: Section 11 → Disney-Specific Depth, Section 14 → Disney Interview Signals Checklist (7 Disney-specific dimensions replacing DocuSign's 7 PDF dimensions), depth bar lowered to 1–2 deep dives, 15 min Q&A buffer added, guest-centric framing rules added. |
