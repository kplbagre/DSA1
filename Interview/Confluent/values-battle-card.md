# Confluent Values Round — Battle Card
### Senior Software Engineer | Kora K2 Group Coordinator (Job ID 127248)

> **This is the fast-recall artifact.** Read it the morning of the interview (~10 min).
> For full story detail, the source files stay as deep reference:
> - Behavioral stories → [BAR-RAISER-BEHAVIORAL.md](../../current%20project/Bar-Raiser/BAR-RAISER-BEHAVIORAL.md) (by Q-number)
> - Bug/project detail → [MCSE-interview-stories.md](../../current%20project/Bar-Raiser/MCSE-interview-stories.md)
> - Values research + IBM/Kora context → [value-fit-round.md](value-fit-round.md)
>
> **The round has TWO components (HR-confirmed):** values assessment + role fit conversation.
> Section 1 below = values. Section 2 = role fit (the part not covered anywhere else).
>
> **Delivery rules:** STAR, 60–90 sec per story, first-person ("I", not "we"), one metric per story, always close the loop (what changed).

---

## 🧾 THE ONE-PAGE MAP — 5 Values → Best Story

| Value | Primary story | One-line hook | Metric / outcome |
| --- | --- | --- | --- |
| **Earn Our Customers' Love** | Mexico DST bug (`BEHAVIORAL Q6`, `STORIES Bug 3`) | Customers got wrong delivery dates 1hr off every summer — silent, twice-a-year | Fixed-offset fix; wrong-promise class eliminated for MX (buId=2) |
| **Smart, Humble & Empathetic** | `.toList()` prod bug I caused (`BEHAVIORAL Q5`, `STORIES Bug 5`) | My own code shipped an immutable-list bug; owned it, audited the whole class | Audited every `.toList()`; wrote Java-upgrade note for the team |
| **Be Fired Up & Get Stuff Done** | CA V5 slot onboarding (`STORIES Project 1`) | Owned the slot data-access layer end-to-end under a hard launch date | Shipped behind CCM flag, zero US risk, hit CA launch |
| **Tasteful, Not Wasteful** | CA serialization perf fix (`BEHAVIORAL Q1/Q15`, `STORIES Bug 7`) | 3-line fix found by systematic profiling, not a rewrite | p95 dropped immediately; per-market CCM control |
| **One Team** | Trace V2 cross-team migration (`BEHAVIORAL Q2/Q10`) | Led schema review with analytics *before* writing code | Log-grep → SQL query; dual-write, clean V1 deprecation |

**Backup stories (if they push for a second in any theme):**
- Principled dissent → multihop double-bug PR (`BEHAVIORAL Q3`) — pushed back on "ship half," proved partial fix was worse
- Pushing back on product → 17-store DFS diagnostic (`BEHAVIORAL Q4`) — 16 of 17 stores would've failed silently
- Ownership of a gap → Cassandra TTL nobody owned (`BEHAVIORAL Q1`)
- Non-determinism deep-dive → HashMap iteration bug (`STORIES Bug 1`)

---

## 🧠 Value-by-Value — How to Frame Each (Confluent-specific angle)

**Earn Our Customers' Love** — Confluent frames customers as developers who bet their pipelines on Kafka. My angle: "wrong output is worse than no output." Mexico DST + multihop EDD both show I treat a silently-wrong result as a customer-trust failure, not a minor bug.

**Smart, Humble & Empathetic** — Lead with a failure I owned (the `.toList()` bug from *my own* code). Humble = I don't hide it; smart = I fixed the whole class not just the instance; empathetic = I wrote the note so nobody else trips on it.

**Be Fired Up & Get Stuff Done** — CA V5: hard external deadline, ambiguous spec (Cassandra key format not final). I didn't block — I abstracted the key behind a CCM-configurable delimiter and kept moving. Bias for action *with* a safety lever.

**Tasteful, Not Wasteful** — This is the "elegant, not over-engineered" value. Best proof: the 3-line serialization fix. I didn't rewrite the tracing layer — I profiled, found the exact waste (a 400-entry map serialized 50×/call), and cleared it behind a flag. Also the CA-specific-vs-generic trade-off (`BEHAVIORAL Q19`) — chose honest, documented debt over speculative architecture.

**One Team** — Trace V2. I could've shipped the schema and made analytics adapt. Instead I ran the schema review first and built their fields in from day one, so their migration was "update a query," not "rebuild logic."

---

## 🎯 SECTION 2 — ROLE FIT NARRATIVE (the gap — new content)

> Role fit is the second HR-confirmed component. These are practiced answers, not scripts — internalize the beats, say them conversationally.

### "Why Confluent / why K2 Group Coordinator?" (~90 sec)

**The bridge — lead with what I already do:**
> "I've spent the last few years on a 700K request/minute JVM platform where Kafka isn't a side component — it's the backbone. Sixteen in-memory caches hydrated by Kafka consumers, and I've owned the failure modes personally: consumer lag paging, non-idempotent reprocessing corrupting state, and blast-radius isolation — I split a shared consumer per market after one market's bad message corrupted another's cache. So I've lived the exact class of problem the Group Coordinator solves: coordinating consumers, group membership, rebalancing, offset commits — reliably, at scale."

**The pull — why *this* team specifically:**
> "K2 GC is Confluent taking consumer-group coordination out of the broker and making it a standalone cloud-native service. That's interesting to me for a concrete reason: I've been on the *consumer* side of stop-the-world rebalances and offset-commit races. Working on the coordination layer itself — the thing that decides membership and drives rebalance — is going a layer deeper on a problem I already understand from the outside. It's the same instinct that made me chase the HashMap non-determinism bug: I want to own the layer that has to be correct, not just consume it."

**The company — why Confluent over staying:**
> "Kora is a genuine re-architecture — 5 million engineering hours, cloud-native from the storage layer up. I want to work where the hard distributed-systems problem *is* the product, not a supporting cast member. And Confluent's radical transparency and small-team ownership model fit how I already work — I bring answers, not tickets."

### "The IBM acquisition just closed — does that concern you?" (~45 sec)

> "It's the opposite of a concern, honestly. Kreps framed it as the Red Hat precedent — Confluent keeps operating independently, and IBM gives it reach into hybrid-cloud and agentic-AI workloads where streaming data is the foundation. For an engineer that means the core problem — real-time data infrastructure — just got a bigger runway, not a smaller one. The work on Kora and the Group Coordinator doesn't change; the distribution behind it grows. I'd rather join at the moment the bet is scaling up."

> ⚠️ Only volunteer the IBM detail if asked. Don't lead with acquisition talk — it can read as focusing on the deal over the work.

### "Anything you're worried about / a weakness here?" (honest, self-aware — hits Smart/Humble/Empathetic)

> "The obvious one: I've operated Kafka deeply as a *consumer* and cache-hydration owner, but I haven't built broker-internal coordination logic. I'm not going to pretend the protocol internals are second nature yet. What I'd lean on is the pattern I already have — I front-load the highest-risk unknowns first (I did exactly that on CA V5), so I'd expect to be deep in the rebalance and membership internals in the first few weeks rather than easing in from the edges."

---

## 🔬 SIX PROBE THEMES — one story slotted to each

Confluent probes these six (from `value-fit-round.md §4`). Have the named story ready:

| Theme | Story | File ref |
| --- | --- | --- |
| Emotional regulation under pressure | lost composure — pick true option | `BEHAVIORAL Q16` (draft — select) |
| Principled dissent | multihop double-bug — "ship half is worse" | `BEHAVIORAL Q3` |
| Proactive communication | estimate 2x off, brought options early | `BEHAVIORAL S6` |
| Ownership / accountability | Cassandra TTL nobody owned | `BEHAVIORAL Q1` |
| Self-awareness (wrong perception) | pick true option | `BEHAVIORAL Q18` (draft — select) |
| Mentorship / amplifying others | pick true option | `BEHAVIORAL Q17` (draft — select) |

> **Also for emotional regulation**, the 100% CPU incident (Story B) lives in `15_BEHAVIORAL_ANSWERS_FULL.md` — **not in the current prep folder. Locate and re-read before the round.**

---

## ⚠️ PRE-INTERVIEW VERIFY CHECKLIST — fill from memory before the round

These are `[VERIFY]` gaps in `BAR-RAISER-BEHAVIORAL.md` that a single follow-up will expose. Fill or soften each:

- [ ] **Q2 (Trace V2):** exact fields analytics asked for? real timeline (~6 wks?)
- [ ] **Q9 (cross-team DST note):** did other services actually respond / find the bug? If not → reframe as platform-internal (still strong)
- [ ] **Q10 (analytics buy-in):** how many dashboards migrated? real win they got?
- [ ] **Q12 (quality-bar checklist):** did it catch anything in a real PR? If not → "became a reference artifact, cited in PR comments"
- [ ] **Q15 (serialization fix):** actual map-size distribution numbers?
- [ ] **Locate Story B & Story G** in `15_BEHAVIORAL_ANSWERS_FULL.md` and re-read
- [ ] Rehearse the two role-fit answers out loud, timed

> **Rule:** if you can't verify a number, go qualitative ("most requests were small, the slow ones all had large maps") — never state a fabricated stat. A collapsed stat under probing costs more than a vague-but-honest one.

---

## 🪜 GAPS TO FILL — moved to the BR file

> The four hard-gap questions with no existing story — **emotional regulation, mentorship, self-perception, an actual missed deadline** — now live as **`BEHAVIORAL Q16–Q19`** in [BAR-RAISER-BEHAVIORAL.md](../../current%20project/Bar-Raiser/BAR-RAISER-BEHAVIORAL.md), each with 2–3 candidate options.
>
> They're company-agnostic (every behavioral round probes them), so the durable BR file is their home — not this Confluent-specific card.
>
> **Action:** open Q16–Q19, pick the ONE true option per question, delete the rest, and replace invented specifics with real detail. Then this card's probe-theme table already points at them.

| Gap | Theme | Where |
| --- | --- | --- |
| Lost composure | Emotional regulation | `BEHAVIORAL Q16` |
| Mentored/unblocked someone | Mentorship (primary SSE signal) | `BEHAVIORAL Q17` |
| Wrong perception of you | Self-awareness | `BEHAVIORAL Q18` |
| Actual missed deadline | Accountability | `BEHAVIORAL Q19` |

---

## 🧩 MEDIUM GAPS — Reframe Cues (story exists, needs adjustment)

> These aren't missing stories — they're existing stories the interviewer will push on in a direction you haven't rehearsed. For each: which story to use, the beats to add, and the trap.

---

### MED 1 — "Deliver a project under a tight deadline **with a team**." *(§5 Q3)*
**Use:** CA V5 (`STORIES Project 1`).
**The trap:** Your stories are heavily solo. They'll probe *"what did the rest of the team do?"* and a solo answer reads as "doesn't collaborate."
**Beats to add:**
- Name who else was involved and their piece — the slot-publishing team (data schema), analytics/consumers, your reviewer.
- Show one coordination act you drove: the schema alignment, the CCM-delimiter decision that unblocked *both* sides, the dual-write ramp.
- Close with the shared outcome ("we hit the CA launch"), not just your component.
> One line to have ready: *"My piece was the slot data-access layer, but the deadline was only hittable because I got the key format locked with the publishing team early and made the delimiter config-driven so neither side blocked the other."*

---

### MED 2 — "A conflict **within a team** — how did you handle it?" *(§5 Q4)*
**The trap:** This is the thinnest spot. BR Q3/Q4 are *technical* dissent (disagreeing on an approach), not *interpersonal* friction. If the interviewer means people-conflict, "I disagreed on a tech decision" doesn't land. `S9/S10` are hypotheticals, not "a real time."
**Two ways to answer — pick your reality:**

**Angle A — reframe a technical disagreement as relationship-managed:** *(safe, uses BR Q3)*
> "The multihop double-bug fix — my tech lead wanted to split it into two PRs, I believed shipping half was worse than shipping neither. The disagreement got a little tense in review comments. What resolved it wasn't me being right on paper — it was me switching to a live call, walking through the concrete overshoot example, and framing it as 'here's the customer-facing risk of the partial fix' rather than 'you're wrong.' We aligned in five minutes. The relationship mattered more than winning the thread."

**Angle B — a genuine interpersonal conflict** *(stronger IF you have a real one — fill from memory):*
> Two teammates / you-and-a-peer disagreed on ownership or approach in a way that created friction (not just a technical delta). What you did to de-escalate, understand their side, and find the path. → **You must supply the real event.** If Angle A is all you have, use it and be honest that your conflicts tend to be technical, resolved by moving to data + live conversation.

> **Decide now which angle is true for you** so you're not choosing in the room.

---

### MED 3 — "A leadership experience guiding a team through significant change." *(§5 Q7)*
**Use:** Trace V2 (`BEHAVIORAL Q2/Q10`).
**The trap:** Over-claiming. Trace V2 is *cross-team persuasion*, not *managing a team through change*. Don't imply you led people you didn't manage — the BR files flag over-claimed narratives.
**How to frame honestly:**
- Position it as *technical leadership without authority*, which is the right SSE altitude anyway.
- The "change" = migrating analytics off the V1 single-blob model to V2 typed events — a change in how another team *worked*, which they resisted ("V1 works, why break it?").
- Your leadership acts: ran the schema review first, reframed from "you should migrate" to "tell me what you need and I'll build it in," de-risked with dual-write.
> One line: *"I didn't have authority over the analytics team, so leading the change meant making migration cheaper than staying — I built their required fields into V2 from day one so their move was a query change, not a rebuild."*

---

### MED 4 — "Why Confluent / how you'll contribute to the mission?" *(§5 Q2 / Slot H)*
**Status:** Draft exists — see **Section 2 (Role Fit Narrative)** above. Not a content gap, a **rehearsal gap.**
**The trap:** It comes out as a data dump (Kora facts, 5M hours, IBM) instead of a personal answer.
**Fix:** Say it out loud 3× until the *bridge* (your Kafka-consumer/cache-hydration work → the coordination problem) is the spine and the facts are seasoning. If you can't say it in ~90 sec without reading, it's not ready.

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| Aug 18, 2026 | **File created.** Lean battle card built from the 3 Bar-Raiser files + `value-fit-round.md`. Maps 5 Confluent values → best existing stories (no story duplication), adds the missing Role Fit narrative (Why K2 GC, IBM acquisition, honest weakness), slots the six probe themes, and carries forward the open `[VERIFY]` gaps. |
| Aug 18, 2026 | **Added "Gaps to fill" section**, then **relocated the four hard-gap stories** (lost composure, mentorship, wrong self-perception, actual missed deadline) with all candidate options into `BAR-RAISER-BEHAVIORAL.md` as company-agnostic `Q16–Q19`. This card now keeps only a pointer. User to select the true option per question. |
| Aug 18, 2026 | **Added "Medium gaps" reframe cues.** Four existing-story questions that need adjustment (tight deadline *with a team*, interpersonal conflict, leading a team through change, why-Confluent rehearsal) — beats to add + trap to avoid for each; MED 2 given two angles since interpersonal conflict is genuinely thin. |
