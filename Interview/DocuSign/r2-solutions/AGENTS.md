# r2-solutions — AGENTS.md

> **For any AI assistant writing or editing files in this folder:** Read this file AND `solution-notes-standards.md` before touching any file here. Both are mandatory. The root `AGENTS.md` and `SystemDesignConcepts/AGENTS.md` set universal rules; this file sets r2-solutions-specific rules.

---

## Mandatory Pre-Work (Do This Before Writing Any Solution File)

1. **Read `DELIVERY-RECIPE.md`** in the parent folder (`Interview/DocuSign/`) — the universal 6-step interview delivery framework that backs all solution files. Every solution file IS an instantiation of this framework. Understand: why the 6 steps exist (cognitive psychology), how to allocate 60 minutes, memory anchors for stress management, and how to explicitly map to DocuSign's 7 evaluation dimensions. This is non-negotiable.
2. **Read `solution-notes-standards.md` in this folder** — the complete 15-section format, two interview types (Type A / Type B), requirements variation table principle, and pre-write checklist. Every solution file must follow it exactly. Note: Sections 0–15 are *organized* to match DELIVERY-RECIPE's 6 steps, not replace them.
3. **Read `INDEX.md` in this folder** — the current list of planned and written files, their priority order, and the quick-reference interview format. Check this before creating a new file to avoid duplicating or misordering.
4. **Read the root `AGENTS.md`** (`/Users/k0b077v/Documents/kapil-kb/AGENTS.md`) — universal formatting rules: code style, ASCII visuals, emoji palette, first-use term gloss.
5. **Read `SystemDesignConcepts/AGENTS.md`** — for the rules governing concept notes (coverage completeness, named-technology explanation, section order). Solution files cross-reference concept notes — knowing those rules prevents you from accidentally duplicating concept-note content here.
6. **Read the prerequisite concept note(s)** listed in the target file's Section 0 Identity Card before writing. The solution file is the application of those concepts — you cannot write it without understanding what those notes already cover.
7. **Read `system-design-questions.md`** in `Interview/DocuSign/` — the master question list with source, tier, and coverage tracking. Update the Coverage column after writing a new solution file.

> **Rule: When a new reference file is added to this folder that AI should consult, this AGENTS.md Mandatory Pre-Work must be updated in the same step — never separately.**

---

## 🧠 How DELIVERY-RECIPE Maps to Solution Files

**The relationship:** DELIVERY-RECIPE defines the *sequence and psychology* of answering a system design question in 60 minutes. Solution files are the *instantiation* of that recipe for specific questions. Every solution file is structured so that if you follow it section-by-section, you're executing DELIVERY-RECIPE.

**Mapping:**

| DELIVERY-RECIPE Step | Solution File Section(s) | Time | What you're doing |
|---|---|---|---|
| **Step 1: Requirements** | Sections 1–3 (Opener + Clarifying Qs + Requirements) | 5 min | Ask 4–6 questions to clarify scope; state FR/NFR |
| **Step 2: Core Entities** | Embedded in Section 3 (Requirements) + Section 4 (Scale) | — | Core entities emerge from scale estimation |
| **Step 3: API/Interface** | Section 8 (API Design) | — | Boundary contract (for Type B questions; Type A references this) |
| **Step 4: Data Flow** | Section 6 (HLD walkthrough) + Section 7 (Deep dives) | 5 min | "Say this out loud" narrative — trace request end-to-end |
| **Step 5: High-Level Architecture** | Section 5 (Requirements Variation) + Section 6 (HLD + ASCII) | 15 min | Draw boxes, justify each, walk the flow |
| **Step 6: Deep Dives** | Section 7 (2–3 riskiest components) | 15 min | Schema, algorithm, trade-off comparison per component |
| **Trade-offs** | Section 10 (exactly 3, with gain/lose/failure-mode) | 8 min | Name what breaks if you chose wrong |
| **DocuSign Dimensions** | Section 11 (explicit mapping) | — | Post-HLD: "Let me map this to DocuSign's 7 dimensions" |
| **Interviewer Probes** | Section 12 (Tier 1 / 2 / 3) | 7 min | Answer 3-tier follow-ups from your preparation |

**Key insight:** You don't *memorize* solution files. You memorize DELIVERY-RECIPE's 6 memory anchors, then *apply* them. Solution files are studied beforehand to pre-load the deep dives and trade-offs — so that under stress, you execute the familiar 6-step rhythm and call up the pre-prepared details.

---

## What This Folder Is

DocuSign-specific 60-minute answer frameworks for each confirmed/likely R2 interview question. These are NOT concept notes — concept notes live in `SystemDesignConcepts/`. These are "how to walk through THIS question in THIS interview" guides.

**The distinction:**
- Concept note (`SystemDesignConcepts/02-rate-limiting.md`) — teaches what token bucket is, how it works, all algorithms
- Solution file (`r2-solutions/C1-rate-limiter.md`) — teaches how to present a 60-minute rate limiter answer to a DocuSign interviewer, with clarifying questions, scale estimates, DocuSign-specific depth, and prepared probe answers

Do NOT put concept-level teaching in solution files. Cross-reference concept notes by link instead.

---

## Current Solution Files

| Priority | File | Question | Type | Status |
|---|---|---|---|---|
| A1 | `A1-url-shortener.md` | Design a URL Shortener | Type A — System Design | ✅ Written (Jun 23, 2026) |
| A2 | `A2-chat-messenger.md` | Build a Facebook Chat / Messenger Application | Type A — System Design | ✅ Written + DELIVERY-RECIPE integrated (Jun 23, 2026) |
| 1 | `C1-rate-limiter.md` | Design a Rate Limiter for a Microservices API | Type A — System Design | ✅ Written (Jun 23, 2026) |
| 2 | `C2-expense-report.md` | Expense Report System — Data Model Design | Type B — Product Arch | ✅ Written (Jun 23, 2026) |
| 3 | `C3-pagination-api.md` | Pagination API + Data Model Design | Type B — Product Arch | ✅ Written (Jun 24, 2026) |
| 4 | `D1-digital-signature.md` | Design a Digital Signature System | Mixed A+B | ✅ Written (Jun 24, 2026) |
| 5 | `D3-notification-service.md` | Design a Real-Time Notification Service | Type A — System Design | ❌ Not written |
| 6 | `D2-document-storage.md` | Design a Document Storage & Retrieval Service | Type B — Product Arch | ❌ Not written |

> **Note on A1/A2:** These are PDF example questions confirmed asked by real candidates (per web research, June 2026). They are written in addition to the 6 gap questions. A1 and A2 establish the DELIVERY-RECIPE-integrated template; remaining 6 files (C1–D2) follow the same pattern.

---

## Using DELIVERY-RECIPE During Interviews — Memory Anchors

Before every interview, memorize **these 6 memory anchors** from DELIVERY-RECIPE.md:

1. **"Ask before you design."** → Requirements first (Section 2).
2. **"Name the nouns."** → Entities (embedded in Section 3–4).
3. **"Define the boundary."** → API/Interface (Section 8).
4. **"Trace a request."** → Data flow (Section 6 walkthrough).
5. **"Draw the boxes."** → HLD (Section 6 diagram).
6. **"Dig where it's risky."** → Deep dives (Section 7, pick 2–3 riskiest, not most interesting).

**Additional anchors (if you have space):**
- "Everything is a trade-off." (Section 10 — always prepared)
- "Why, not what." (Always explain reasoning, not just technology)
- "Conversational, not presentation." (Answer sounds like thinking aloud, not recitation)

**Stress management:** Under pressure, your working memory shrinks 40–50%. The 6 anchors let your brain execute the framework *without thinking* — like a pianist playing a familiar song. Solution files provide the "sheet music" (the pre-prepared details); the anchors provide the rhythm.

---

## Rules Specific to This Folder

### 1. Never Reproduce Concept Note Content — Link Instead

If a solution file needs to explain what consistent hashing is, do NOT write the explanation inline. Write:

```markdown
Full explanation: **`SystemDesignConcepts/05-consistent-hashing.md`**
```

Then state the decision: "I'll use consistent hashing here because..."

The solution file assumes the reader has read the concept note. Its job is application, not re-teaching.

---

### 2. Section 0 Identity Card Is Mandatory and Must Be Accurate

The Interview Type (Type A / Type B / Mixed) drives the entire structure of the answer. Getting it wrong misdirects interview prep. Before writing:
- Confirm whether DocuSign evaluates this question using the 7 dimensions (Type A) or the 6 product areas (Type B)
- A question can be Mixed — mark it explicitly and cover both sets of evaluation criteria

Reference: `solution-notes-standards.md` Section "Two Interview Types"

---

### 3. Section 5 (Requirements Variation Table) Is Non-Negotiable

This is the most differentiating section per the standards file. It must cover at least 5 axes of variation:
1. Scale (small → large)
2. Consistency (strong → eventual)
3. Latency (strict SLO → relaxed)
4. Scope (single-tenant → multi-tenant / single-region → global)
5. Feature (MVP → full product)

If you skip or thin this section, the solution file fails its purpose.

---

### 4. Section 11 (DocuSign-Specific Depth) Must Be Genuinely Specific

Generic SaaS advice ("add multi-tenancy", "use JWT") does not count as DocuSign depth. DocuSign-specific means one or more of:
- KYC (Know Your Customer) identity layer at DocuSign's B2B scale
- E-signature / legal compliance angle (non-repudiation, audit trail, PKI)
- Multi-party signing order (sequential vs parallel)
- DocuSign's actual product context — how the system would behave inside the DocuSign platform
- The 7 evaluation dimensions explicitly named and mapped to your design

If this is a PDF example question (A1, A2, A3) with no DocuSign domain tie-in, state that explicitly and pivot to: "The DocuSign move here is to name which of the 7 evaluation dimensions your design addresses."

---

### 5. Section 12 (Probe Questions) Must Have All 3 Tiers

| Tier | What it tests | Must include |
|---|---|---|
| Tier 1 — Surface | Does the candidate know the topic? | ≥1 question with 2-3 sentence answer |
| Tier 2 — Deep | Does the candidate understand it, not just know it? | ≥1 question requiring specific technical detail |
| Tier 3 — Cross-concept | Can the candidate reason across system boundaries? | ≥1 question connecting this to another design concept |

Tier 3 is the differentiator. Examples: "How does your rate limiter interact with your authentication system?" / "If your message delivery fails, how does that affect your idempotency guarantees?"

---

### 6. ASCII Diagrams Must Precede Section 6 Explanation

Every Section 6 (HLD) must have the ASCII architecture diagram BEFORE the data flow walkthrough. The diagram is drawn first in the actual interview — the note should mirror that order.

Every box in the diagram must be justified in the walkthrough text below it. Unjustified boxes are a red flag — interviewers ask "why is that there?" and candidates who drew it reflexively can't answer.

---

### 7. Scale Estimates Drive Architecture Choices — Connect Them

Section 4 (Scale Estimation) numbers must be explicitly referenced in Sections 6 and 7. Example:

> "At 231K writes/sec (from our estimate in Section 4), a single Postgres instance tops out around 10K/sec — so we need horizontal write scaling. This is why I chose Cassandra in Section 6."

If the scale estimate isn't referenced downstream, it was pointless to compute it.

---

### 8. Trade-offs Must Name What Breaks (Not Just What You Lose)

Section 10 trade-offs must include a **failure mode**: what actually breaks in production if you made the wrong choice.

❌ Weak: "Fan-out on write is expensive for large groups."
✅ Strong: "Fan-out on write at 100K members × 1M messages/day = 100B writes/day. At that scale, your write throughput exceeds Cassandra's capacity per cluster. The system starts dropping writes under peak load."

The failure mode forces you to actually understand the trade-off, not just recite it.

---

### 9. Time Budget Awareness — 60-Minute Constraint Is Real

The 60-minute time budget in `solution-notes-standards.md` is not decorative. When writing the solution file, mentally allocate each section to a time window. If a deep dive section would take 25+ minutes to explain, it's too deep — trim it or split across two deep dives.

The pre-write checklist item: "If I talked through this section out loud, would I finish in the allotted time?"

---

## Pre-Publish Checklist (Run This Before Every File Is Saved)

Abbreviated from `solution-notes-standards.md` — run both:

- [ ] Section 0 Identity Card filled — interview type is correct (A / B / Mixed)
- [ ] Section 1 opener is a question pivot, not a drawing start
- [ ] Section 2 clarifying questions: each has WHY + architectural fork (not just the question text)
- [ ] Section 3 requirements: FR and NFR both covered, out-of-scope stated
- [ ] Section 4 scale: numbers computed, key conclusions drawn
- [ ] Section 5 variation table: ≥5 axes covered
- [ ] Section 6 HLD: ASCII diagram present, every box justified in walkthrough
- [ ] Section 7 deep dives: 2-3 RISKIEST components picked (not most interesting)
- [ ] Section 8 API: endpoint table + key design decisions (if applicable to question type)
- [ ] Section 9 data model: SQL schema + key decisions (if applicable)
- [ ] Section 10 trade-offs: exactly 3, each with gain / lose / failure mode
- [ ] Section 11 DocuSign depth: genuinely specific, not generic SaaS advice
- [ ] Section 12 probes: all 3 tiers present
- [ ] Section 13 mistakes: ≥2 real mistakes named
- [ ] Section 14 dimensions checklist: all 7 filled (✅ or — with reason)
- [ ] Section 15 TL;DR: 4-5 sentences covering core decision + trade-off + scale + DocuSign insight
- [ ] No concept note content reproduced inline — cross-referenced by link
- [ ] Scale estimate numbers referenced in HLD and deep dive sections
- [ ] `INDEX.md` updated with new file status
- [ ] `system-design-questions.md` Coverage column updated

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | AGENTS.md created for r2-solutions folder. Modelled on SystemDesignConcepts/AGENTS.md. Covers: mandatory pre-work (6 steps), folder purpose distinction (solution vs concept), 9 folder-specific rules, full pre-publish checklist. Triggered by the first solution file (A2-chat-messenger.md) being written without a governing standards file. |
| June 23, 2026 | **DELIVERY-RECIPE integration.** Added: (1) DELIVERY-RECIPE as step 1 in Mandatory Pre-Work, (2) mapping table showing how 6-step recipe translates to 15-section solution format, (3) Memory Anchors section with 6 core anchors + 3 bonus anchors + stress management rationale. Clarified: solution files ARE instantiations of DELIVERY-RECIPE, not separate frameworks. All subsequent solution files (C1, D1, etc.) will inherit this integrated approach. |
