# Notes Standards — Pattern Notes

> **Purpose:** Pattern notes are decision playbooks, not concept explanations. They answer one question: "What do I DO when I recognize this pattern in an interview?" Read the concept notes in `Core-Architecture/` and `Foundations/` to understand HOW things work. Read pattern notes to know WHAT to say and in WHAT ORDER.

---

## 🎯 Pattern Notes vs Concept Notes

| Dimension | Concept Note | Pattern Note |
|---|---|---|
| **Question answered** | How does X work? | What do I do when I see this problem? |
| **Tone** | Educational, descriptive | Prescriptive, operational |
| **Target length** | 400–800 lines | ≤ 200 lines |
| **Pre-interview use** | Study session (30–60 min) | 5-minute refresh |
| **Structure** | Mental model → mechanics → Q&A | Trigger → sequence → script |

---

## 🗂️ The 9 Patterns (Locked Classification)

| # | Pattern | Core Question It Answers |
|---|---|---|
| 1 | **Scaling Reads** | How do I serve 100K+ reads/sec from a data store? |
| 2 | **Scaling Writes** | How do I ingest millions of writes/sec without dropping data? |
| 3 | **Feed & Fanout** | How do I assemble a personalized feed and deliver updates to millions of followers? |
| 4 | **Dealing with Contention** | How do I handle many concurrent writers hitting the same resource? |
| 5 | **Multi-step Processes** | How do I coordinate a business workflow that spans multiple services? |
| 6 | **Long-Running Tasks** | How do I handle a single computation that takes seconds-to-hours? |
| 7 | **Real-time Updates** | How do I push updates to clients as events happen, at scale? |
| 8 | **Handling Large Blobs** | How do I store, serve, and process files that are too large for a database? |
| 9 | **Proximity Search** | How do I find things near a location efficiently? |

---

## ⚡ Pattern Boundaries (Read This First)

The patterns overlap. These rules prevent misclassification:

### Scaling Reads vs Feed & Fanout
- **Scaling Reads**: Generic reads — any user reads the SAME data (product page, URL redirect, search result). Bottleneck is database read capacity.
- **Feed & Fanout**: Personalized assembly — each user sees a DIFFERENT result assembled from many sources (Twitter timeline, Instagram feed). Bottleneck is per-user assembly latency + write amplification.
- **Test:** Can you cache one response and serve it to many users? → Scaling Reads. Does each user need a unique assembly? → Feed & Fanout.

### Feed & Fanout vs Real-time Updates
- **Feed & Fanout**: User polls or opens the app and gets a pre-assembled feed. Updates may be minutes stale.
- **Real-time Updates**: Server PUSHES to client within seconds of an event (chat message, stock price, live score). Requires persistent connection (WebSocket, SSE) or near-real-time polling.
- **Test:** Does the user NEED to see the update without refreshing? → Real-time Updates. Is a near-recent feed acceptable? → Feed & Fanout.

### Scaling Writes vs Dealing with Contention
- **Scaling Writes**: Writes are independent — each user writes their own distinct data. Bottleneck is raw throughput.
- **Dealing with Contention**: Writes CONFLICT — many users write to the same row/counter/seat. Bottleneck is coordination, not throughput.
- **Test:** Would a perfect write throughput system still have a problem? If yes (because two users want the same seat) → Contention. If no → Scaling Writes.

### Multi-step Processes vs Long-Running Tasks
- **Multi-step Processes**: A business workflow that coordinates multiple services (payment → inventory → notification). Complexity is in the distributed coordination and rollback on failure.
- **Long-Running Tasks**: A single expensive computation (video transcode, ML inference, report generation). Complexity is in async execution, progress tracking, and result retrieval.
- **Test:** Does the task span multiple domain services that each have their own state? → Multi-step. Is it one big compute job? → Long-Running Tasks.

---

## 📐 Pattern Note Structure (Exact Section Order)

Every pattern note has exactly these 9 sections, in this order:

---

### Section 1 — 🚨 Trigger Signals
**Length:** 6–10 bullet points maximum.

These are the phrases and clues that tell you "I'm in this pattern."

**Two types of triggers:**
1. **Interviewer phrases** — exact words or paraphrases the interviewer uses
2. **System symptoms** — numbers or behaviors you derive during the interview

Format:
```markdown
## 🚨 Trigger Signals — "I'm in this pattern when..."

**Interviewer says:**
- "Design a system that handles X million reads per day"
- "Users are complaining about slow page loads under traffic"

**System characteristics you derive:**
- Read:write ratio > 10:1
- Database CPU is high but write volume is low
```

---

### Section 2 — 💡 Core Insight
**Length:** 3–5 lines. One paragraph.

The single idea that makes this pattern click. Not a summary of the solution — the insight that drives the whole decision tree.

Format:
```markdown
## 💡 Core Insight

[One paragraph. The "aha." Should be something you can say out loud in 3 sentences that makes the interviewer nod.]
```

---

### Section 3 — 🧭 Decision Sequence
**Length:** 4–8 numbered steps.

**This is the most important section.** An ordered sequence — not a menu. Each step is a decision gate: "Start here. If this isn't enough, escalate to the next step."

Format:
```markdown
## 🧭 Decision Sequence

1. **Try X first** — works when [condition]. Cost: [cost]. Add it before step 2.
2. **If X is not enough, add Y** — works when [condition]. Introduces [trade-off].
3. **If Y is not enough, do Z** — this is the nuclear option: [what it unlocks, what it costs].
```

Rules:
- Each step has an explicit "when to escalate" condition
- Later steps introduce more complexity than earlier steps
- Never present two options at the same level without saying "prefer A unless B"

---

### Section 4 — 🎨 ASCII Visual
**One diagram only.** Show the canonical architecture for this pattern at its most complete state (i.e., after all steps in Section 3 are applied).

Include a `KEY INVARIANT` line that names the architectural property this pattern enforces.

See AGENTS.md Rule 6 for ASCII conventions.

---

### Section 5 — 🔬 Interviewer Probes
**Exactly 5 Q&As.** These are the follow-up questions you WILL get after you describe the pattern.

Format:
```markdown
### Q: "Exact probe question"
> 2–3 sentence answer. End with the trade-off.
```

Rules:
- At least 2 probes must be failure/edge-case questions ("what if your cache crashes?")
- At least 1 probe must ask about the downside of your approach
- Answers must be short enough to say out loud in 30 seconds

---

### Section 6 — ⚠️ Anti-patterns
**Exactly 3 bullets.** The 3 most common mistakes candidates make in this pattern.

Format:
```markdown
- **Mistake name** — what they do wrong and what goes wrong as a result.
```

---

### Section 7 — 🗺️ Problems Map
**Table format.** Maps interview problems to this pattern.

Format:
```markdown
| Problem | Why this pattern applies | Variant |
|---|---|---|
| Design Twitter | Each user's timeline is unique | Feed & Fanout overlap |
```

---

### Section 8 — 📢 Interview Script
**Length:** 5–8 sentences. This is what you say out loud in the first 90 seconds after recognizing the pattern.

Format:
```markdown
## 📢 Interview Script — What to say in the first 90 seconds

"[Script. First person. Present tense. Confident. States the pattern name, the core insight, the first decision, and the key trade-off. Does NOT list every option — commits to an approach.]"
```

Rules:
- Must be speakable — read it out loud before writing it into the note
- Must name the first concrete technology decision ("I'll add a Redis cache layer...")
- Must acknowledge the key trade-off ("The cost is eventual consistency on the read path...")
- Must NOT be a menu ("there are several options: caching, replicas, CDN...")

---

### Section 9 — 🔗 Concept Notes (Depth)
**3–5 links maximum.** When the interviewer goes deep, these are the notes with the full mechanics.

Format:
```markdown
## 🔗 Concept Notes — Go here for the "how it works"

- **Caching mechanics** → `Foundations/Performance-and-Scale/03-caching.md`
- **Read replicas + lag** → `Core-Architecture/Database-Core/29-db-replication-failover.md`
```

---

## ✅ Pattern Note Pre-Publish Checklist

- [ ] All 9 sections present in order
- [ ] Section 1 has BOTH trigger types: interviewer phrases AND system symptoms
- [ ] Section 3 is a SEQUENCE (steps have "escalate when") — not a flat options list
- [ ] Section 4 has a KEY INVARIANT line
- [ ] Section 5 has exactly 5 Q&As, at least 2 are failure/edge-case probes
- [ ] Section 6 has exactly 3 anti-patterns
- [ ] Section 8 was read out loud — it's speakable
- [ ] Section 8 names a specific first technology decision
- [ ] Section 9 cross-links to existing concept notes (not placeholders)
- [ ] Total note length ≤ 200 lines
- [ ] No emojis outside the approved AGENTS.md palette

---

## 🔄 Changelog

| Date | Change |
|---|---|
| July 2026 | Standards file created. 9 patterns locked. 8-section structure defined. Pattern boundaries documented to prevent misclassification. Section 9 (Concept Notes cross-links) added per advisor recommendation to keep notes ≤200 lines by delegating mechanics. |
