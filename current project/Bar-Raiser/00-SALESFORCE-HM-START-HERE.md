# 00 — Salesforce Hiring Manager Round · START HERE
### The one file you open first. What to read, why, and how much.

> **Role:** Software Engineer, SMTS · Signup & ISV Platform (India). You've cleared DSA + LLD + HLD.
> **This round:** Hiring Manager — behavioral + **project deep-dive** + leveling. No coding.
> **Everything is now in this repo** — organized into `Craft/`, `Core-Project/`, `AI-Project/`. No external cross-refs needed; safe to push and read anywhere.

---

## 🎯 The Round in One Line

A 45-min conversation where the HM decides **do I want this person on my team, at this level?** — via a project deep-dive, values/behavioral questions, and pushback follow-ups. It slips on the *second layer* (the pushback), not the first answer.

**Two orientation files (read once):**
- [Interview/Salesforce/hiring-manager-round.md](../../Interview/Salesforce/hiring-manager-round.md) — format, leveling risk, failure-rate reality, questions to ask ⤴
- [Interview/Salesforce/job-description.md](../../Interview/Salesforce/job-description.md) — the JD ⤴

---

## ⭐ Priority Sequence at a Glance (flat cross-folder ranking)

The reading plan below is grouped by *area*; this is the single **do-in-this-order** list across all folders.

| # | File | Tier | Why it's here |
| --- | --- | --- | --- |
| 1 | `Craft/SALESFORCE-HM-BEHAVIORAL-DEEPDIVE` | 🔴 MUST | The two patterns + story-fit map — **fixes why last round slipped (pushback)** |
| 2 | `Core-Project/MCSE-PROJECT-DEEPDIVE` | 🔴 MUST | Reason FROM your system + 10 Q&A — **#1 confirmed HM question** |
| 3 | `Core-Project/MCSE-DECISION-LOG` | 🔴 MUST | Trade-off defense — **where the round is won on follow-ups** |
| 4 | `Craft/SITUATIONAL-PUSHBACK-PLAYBOOK` | 🔴 MUST | 6 meta-moves for any pushback |
| 5 | `Core-Project/MCSE-PITCHES-AND-CROSSQS` | 🔴 MUST | The exact 30s/2min words |
| 6 | `Core-Project/MCSE-FEATURES-AND-FAILURE` | 🟡 SHOULD | "Explain something complex" + "how you handle failure" |
| 7 | `AI-Project/AI-PNS-STORIES` | 🟡 SHOULD | AI fluency from real work (JD differentiator) |
| 8 | `Core-Project/MCSE-interview-stories` | 🟡 SHOULD | Raw STAR stories for "tell me about a time" |
| 9 | Values + V2MOM (`Interview/Salesforce/hiring-manager-round.md`) | 🟡 SHOULD | Frame answers with Trust #1 |
| 10 | `KAFKA-VS-MQ-COMPARISON` · `AI-PROJECT-DEEPDIVE` · `Craft/BAR-RAISER-TECHNICAL` | ⚪ SKIM | You're strong here |

- **If only 1 hour:** #1 + #2 + #3.
- **Morning-of:** the two patterns + story-fit map + MCSE one-paragraph ("wrong answer worse than slow") + why-Salesforce line.

---

## 📁 Folder Map

```
Bar-Raiser/
├── 00-SALESFORCE-HM-START-HERE.md   ← this file
├── Craft/            interview craft (project-agnostic — behavioral, pushback, HM patterns, technical)
├── Core-Project/     MCSE (promise & sourcing engine) — deep-dive+Q&A, decision-log, features/failure, stories, pitches, Kafka
└── AI-Project/       the AI PNS agent — deep-dive + stories
```

---

## 📚 The Reading Plan (priority · why · how long)

### 🧠 1. Craft — the fix for why last round slipped  ·  **MUST**

| File | Why | Time |
| --- | --- | --- |
| [Craft/SALESFORCE-HM-BEHAVIORAL-DEEPDIVE.md](Craft/SALESFORCE-HM-BEHAVIORAL-DEEPDIVE.md) | ⭐ The two patterns (prevention / trade-off-defense), the story-fit map, 11 questions with scripted pushbacks. The whole game. | 40 min |
| [Craft/SITUATIONAL-PUSHBACK-PLAYBOOK.md](Craft/SITUATIONAL-PUSHBACK-PLAYBOOK.md) | ⭐ 6 meta-moves for ANY pushback + real high-pushback questions (unresponsive teammate, senior-asks-unviable, credit-stealing, disagree-with-decision) — each with the **instinct to suppress**. | 25 min |
| [Craft/BAR-RAISER-BEHAVIORAL.md](Craft/BAR-RAISER-BEHAVIORAL.md) | Broader behavioral bank + `Q16–Q19` (composure, mentorship, self-perception, missed deadline) — pick your true option. | 20 min |

### 🔬 2. Core-Project (MCSE) — the #1 confirmed HM question  ·  **MUST**

| File | Why | Time |
| --- | --- | --- |
| [Core-Project/MCSE-PROJECT-DEEPDIVE.md](Core-Project/MCSE-PROJECT-DEEPDIVE.md) | ⭐ Understand your own system cold — architecture, pipeline, caches, ingestion, the concurrency mechanics, what you owned, **+ 10 drillable Q&A with pushbacks**. You reason FROM this. | 35 min |
| [Core-Project/MCSE-DECISION-LOG.md](Core-Project/MCSE-DECISION-LOG.md) | ⭐ Every "why X, not Y" (20 decisions) as Problem→Why→Why-not→Trade-off, **each with a scripted pushback + answer**. The trade-off-defense ammunition — where the round is won. | 35 min |
| [Core-Project/MCSE-FEATURES-AND-FAILURE.md](Core-Project/MCSE-FEATURES-AND-FAILURE.md) | ⭐ The "explain something complex" bank (predictive delivery-time, multi-slot, clearance) + the "how do you handle failure" bank (error taxonomy, 5 resilience layers, bad-config, back-pressure) — taught with diagrams + pushbacks. | 30 min |
| [Core-Project/MCSE-PITCHES-AND-CROSSQS.md](Core-Project/MCSE-PITCHES-AND-CROSSQS.md) | ⭐ The exact words — 30s/2min pitches (Stories 0–4) + the cross-question bank + stack-justification (Why-X) + gray-areas. Scrubbed, portable. | 30 min |
| [Core-Project/MCSE-interview-stories.md](Core-Project/MCSE-interview-stories.md) | The raw bug/project stories (with prevention beats) the deep-dive points to. | 25 min |
| [Core-Project/KAFKA-VS-MQ-COMPARISON.md](Core-Project/KAFKA-VS-MQ-COMPARISON.md) | Kafka vs RabbitMQ/SQS/Pulsar/Redis, **where Kafka is NOT preferable**, why Kafka in MCSE + the read-path counter-example. Asked in a real interview. | 15 min |
| [Core-Project/KAFKA-MCSE-INGESTION.md](Core-Project/KAFKA-MCSE-INGESTION.md) | Write-side ingestion internals (consumer V1→V2, retry topics, fault-tolerant contract). ⚠️ contains internal identifiers — study only, don't read aloud. | skim |

### 🤖 3. AI-Project — the JD's core differentiator  ·  **MUST** (short)

| File | Why | Time |
| --- | --- | --- |
| [AI-Project/AI-PNS-STORIES.md](AI-Project/AI-PNS-STORIES.md) | ⭐ Your AI agent work as STAR + pushbacks, honest ownership split. Most candidates can't answer AI-fluency from real work — you can. | 20 min |
| [AI-Project/AI-PROJECT-DEEPDIVE.md](AI-Project/AI-PROJECT-DEEPDIVE.md) | What the app is + concept-level architecture + RAG/agents/tool-design internals (scrubbed) — for a deep AI-technical probe. | 20 min |

### ⚙️ 4. Technical / Kafka concepts  ·  **SKIM** (you're strong)

| File | Why | Time |
| --- | --- | --- |
| [Craft/BAR-RAISER-TECHNICAL.md](Craft/BAR-RAISER-TECHNICAL.md) | Operational-maturity answers (debugging, testing, rollback, observability). Already HM-ready — skim. | 20 min |
| [SystemDesignConcepts #19 message-queues](../../SystemDesignConcepts/Core-Architecture/Service-Communication/19-message-queues-kafka-rabbitmq.md) | Kafka fundamentals refresh (acks, consumer groups, lag, pub/sub). ⤴ | 15 min |
| [SystemDesignConcepts #60 kafka-internals](../../SystemDesignConcepts/Core-Architecture/Service-Communication/60-kafka-internals.md) | Deep Kafka (partition math, throughput, compaction). Ignore Tableflow sections (Confluent-specific). ⤴ | 15 min |

### 🏢 5. Company / values / focus-tech  ·  **SHOULD** (short)

| What | Why | Time |
| --- | --- | --- |
| [Interview/Salesforce/hiring-manager-round.md](../../Interview/Salesforce/hiring-manager-round.md) — values + V2MOM section | 5 core values (**Trust #1**) + V2MOM shape; frame results with Trust/Customer Success. ⤴ | 10 min |
| Focus-tech priorities | Lean into **Kafka/MQ** (strength) + **AI** (edge); shore up **Oracle/Postgres relational** (gap); swap Grafana→**Splunk** vocab. | (in your head) |

---

## ⏱️ Time-Budgeted Plans

**If you have 3 days:**
- **Day 1 — Project:** `Core-Project/MCSE-PROJECT-DEEPDIVE` until you can draw it from memory → `MCSE-PITCHES-AND-CROSSQS`. Say the 2-min pitch out loud.
- **Day 2 — Craft:** `SALESFORCE-HM-BEHAVIORAL-DEEPDIVE` (drill the two patterns) → `SITUATIONAL-PUSHBACK-PLAYBOOK` → pick true options in `BAR-RAISER-BEHAVIORAL Q16–Q19` → `AI-Project/AI-PNS-STORIES`.
- **Day 3 — Fit + polish:** values/V2MOM, why-Salesforce out loud, Kafka skim, gray-areas (in MCSE-PITCHES-AND-CROSSQS §4), questions to ask.

**Night before (~90 min):** `MCSE-PROJECT-DEEPDIVE` (25) · `SALESFORCE-HM-BEHAVIORAL-DEEPDIVE` two patterns + story-fit map (25) · `MCSE-PITCHES-AND-CROSSQS` 30s pitches (20) · why-Salesforce + questions-to-ask (20).

**Morning of (~15 min — this only):**
- The **two patterns** (prevention · trade-off-defense) from the behavioral deep-dive.
- The **story-fit map** (never answer a design question with a code-review story).
- The **why-Salesforce / why-leaving** line, out loud once.
- The MCSE **one-paragraph** + "wrong answer worse than slow answer" framing.

---

## 🚫 Do NOT Re-Read Everything

The failure mode is panic-reading everything at 1am. Don't. The **morning-of set above is the whole safety net.** Everything else is built so you've already internalized it. Trust the work.

---

## 🗂️ Portability & Confidentiality

- **Self-contained:** every file needed for the round is in this repo (`Craft/`, `Core-Project/`, `AI-Project/`). Safe to push to git and read anywhere.
- **Scrubbed:** the consolidated files replace internal codenames/config-keys with concepts. One exception — `Core-Project/KAFKA-MCSE-INGESTION.md` still contains some internal identifiers (class/topic names); **study only, and scrub it before pushing if the repo will be public.**
- **Local-only raw sources (NOT in git, on your machine only):** `~/Documents/Kpl-inv/project-update/` (full MCSE knowledge layer) and `~/aiPnSBackend/prep/` (AI internals). Open these for deep study; never copy the knowledge layer in — it holds confidential identifiers.
- **Recommendation:** keep the git repo **private** regardless.

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| Aug 26, 2026 | Index created — sequenced reading plan, time budgets, do-not-reread rule. |
| Aug 28, 2026 | **Reorganized into folders** (`Craft/`, `Core-Project/`, `AI-Project/`) and made **self-contained**: external `Kpl-inv/project-update` delivery links folded into new in-repo `Core-Project/MCSE-PITCHES-AND-CROSSQS.md`; added `KAFKA-MCSE-INGESTION.md` and `AI-Project/AI-PROJECT-DEEPDIVE.md`. All in-repo links repointed to subfolders. Raw external sources demoted to local-only study; portability + confidentiality note added. |
| Aug 28, 2026 | **Core-Project enriched (no-compression pass).** `MCSE-PROJECT-DEEPDIVE` rebuilt rich (added "How It's Actually Built" mechanics + 10 drillable Q&A with pushbacks); added `MCSE-DECISION-LOG.md` (20 decisions, each with a scripted pushback) and `MCSE-FEATURES-AND-FAILURE.md` (complex-feature + failure-mode banks with diagrams). Full MCSE knowledge layer (project-update 10/11/12 + WHY_THESE_CHOICES) consolidated in-repo and scrubbed to concepts. |
