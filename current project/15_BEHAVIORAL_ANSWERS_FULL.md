# 15 — Behavioral Answers: Full Scripts (Non-AI + AI)
### Ready-to-say answers built from your whole 5-year body of work

> **Companion to file 14.** File 14 is strategy (who the director is, the rubric,
> STAR structure). THIS file is the actual words — full answers you can say almost
> verbatim, drawn from your real MCSE work, not just the AI project.
>
> **Why this file exists:** file 14's stories leaned all-AI. A director wants
> RANGE — feature delivery, production firefighting, scale, security, cross-team
> work. Your MCSE career has all of it. This file uses it.
>
> **Reading time:** 45 minutes. Read the story bank, then the scripted answers out loud.

---

## Index

| # | Section | What you get |
|---|---|---|
| 1 | [The Expanded Story Bank (7 stories)](#1--the-expanded-story-bank-7-stories) | Your real work, STAR-shaped, ready to deploy |
| 2 | [Question → Story Map](#2--question--story-map) | Any question → which story |
| 3 | [Full Scripted Answers (20 questions)](#3--full-scripted-answers-20-questions) | Say these almost verbatim |
| 4 | [The "What Would You Do Differently" Table](#4--the-what-would-you-do-differently-table) | One per story — the senior signal |
| 5 | [Confidentiality Guardrails](#5--confidentiality-guardrails) | What never to say out loud |

---

## 1 — The Expanded Story Bank (7 stories)

Memorize the SHAPE and the NUMBER in each. Say "I," quantify the result, end with a lesson.

> **Translate jargon the first time.** MCSE = "Walmart's promise & sourcing engine
> — decides which store/warehouse ships each cart item and what delivery date the
> customer sees. ~700K requests/min, sub-100ms p95."

---

### STORY A — Multi-Slot Delivery for Canada (HERO FEATURE — cross-team ownership)
**Use for:** proudest work, hardest project, cross-team, ambiguity, technical leadership

```
S: Our promise engine was built on a one-slot-per-item assumption — every data
   structure assumed a single delivery date and node per item. Canada's product
   team wanted to show two options at once: Express (same/next-day) and Standard
   (2–5 days), different prices, both visible to the customer simultaneously.

T: I led the design and delivery. The hard part wasn't the feature — it was
   changing a core response contract that FOUR upstream teams consume
   (Search, Item Page, Cart, Checkout) without breaking any of them.

A: - I redesigned the response: added a slots[] array, each slot carrying its own
     date, price, and inventory hold — kept the old single-slot fields populated
     so existing consumers wouldn't break (additive, backward-compatible).
   - I refactored the reservation generator to emit one inventory hold per slot
     instead of per item, and co-designed partial-confirmation handling with the
     inventory team.
   - I shipped it behind a per-market flag, Canada only, and validated with
     shadow traffic — replayed a slice of production Canada traffic against the
     new path and diffed responses before cutover.

R: Canada shipped dual delivery options with zero breakage to the four upstream
   consumers. The lesson: on a shared contract, backward-compatibility and a
   shadow-diff safety net matter more than the elegance of the design.
```

---

### STORY B — The 100% CPU Production Incident (DEBUGGING HERO + pressure)
**Use for:** hardest production issue, high pressure, a time you were stuck, ownership

```
S: During peak, our Canada promise pods hit 100% CPU. Latency jumped ~5x — from
   ~80ms to ~400ms p95 — and we were on the edge of dropping requests. Live,
   in production, at peak traffic.

T: I led the debug. The pressure was real: no deploy in 24h, no downstream spike,
   normal traffic volume — so the usual suspects were all ruled out.

A: - I worked it methodically: ruled out external slowdown (dashboards), ruled out
     a regression (no deploy), ruled out a traffic surge. That pointed inside the JVM.
   - I took a thread dump on a hot pod — most threads were in GC or waiting on a
     logging lock. Then a heap dump — top retainers were huge strings.
   - I traced it: debug-level log statements were building full-payload strings via
     string concatenation even though logging was at INFO — so we paid to serialize
     a large object on every request, and it lingered until GC. Under peak, GC
     couldn't keep up, which presented as CPU saturation.
   - Fix was two lines — guard the debug call / use parameterized logging. CPU
     dropped from 100% to ~30% within the rolling restart.

R: Resolved a peak-traffic incident in about 3 hours. Then I went further:
   I wrote a static-analysis rule that flags this logging anti-pattern across the
   whole codebase — found 40+ instances, four on hot paths — and added a
   heap-pressure alert so the next person gets signal earlier than CPU. The lesson:
   fix the incident, but also make the whole class of bug impossible to repeat.
```

---

### STORY C — The Kafka Ingestion Tier (SCALE + event-driven)
**Use for:** high-throughput systems, event-driven design, Kafka, scale

```
S: MCSE's caches are hydrated by an upstream ingestion tier that consumes events
   from 18 different domain teams and lands them in Cassandra. Some pipelines
   process millions of records a day on 120–200 pods each.

T: I own parts of this tier — including keeping the hot pipelines within their
   sub-minute freshness SLO while staying resilient to bad data.

A: - I worked with a reactive, batched consumer model: poller threads pull batches,
     dispatch onto a bounded processor pool that gives natural back-pressure —
     when it's full, polling slows, and the system self-throttles instead of falling over.
   - I made the consumers resilient to poison-pill records: they log, emit a metric,
     and commit past the bad record rather than re-throwing — because re-throwing in
     a Kafka consumer blocks the whole partition forever.
   - Failures route to per-topic retry queues with backoff, plus an overnight
     dead-letter drain.

R: Hot pipelines hold a sub-minute end-to-end freshness SLO at millions of records
   a day. The lesson: at scale, resilience is mostly about failing gracefully on
   the one bad record without stalling the good millions behind it.
```

---

### STORY D — App-to-App Authentication Migration (SECURITY + zero-downtime migration)
**Use for:** security, a risky migration, attention to detail, working carefully

```
S: The auth between our engine and the inventory query service was a static
   API key. That's replayable and hard to rotate — a security gap.

T: I replaced it with signed, per-request authentication without any downtime
   for a service on the hot path.

A: - I moved us to signed requests: each request carries a fresh signature with a
     timestamp, validated on the receiver with a key from the service registry —
     which stops replay attacks.
   - The risk was a bad cutover breaking a hot-path dependency, so I ran BOTH auth
     methods accepted in parallel for two weeks, watched metrics, then cut over
     once the new path was proven.

R: Closed a replay-attack vector on a critical dependency with zero downtime.
   The lesson: for security migrations on live systems, the dual-accept window is
   everything — you never flip a hard cutover on the hot path.
```

---

### STORY E — Kafka Cluster Decommissioning (CROSS-TEAM COORDINATION)
**Use for:** cross-team work, coordination, migration, influence without authority

```
S: We had to migrate consumers off a deprecated Kafka cluster onto the new
   platform-managed one — but the producers were owned by five different teams.

T: I coordinated the consumer-side migration across those teams without owning
   any of them — pure influence, no authority.

A: - I rolled it out per-topic rather than big-bang, running parallel consumption
     on old and new so I could validate each topic before cutting it over.
   - I drove the sequencing with the five producer teams — who moves when, how we
     verify — and kept it visible so no team was surprised.

R: Migrated every consumer off the deprecated cluster with no data loss and no
   surprise to the producer teams. The lesson: cross-team migrations succeed on
   sequencing and communication, not on code — the code was the easy part.
```

---

### STORY F — The AI PNS Assistant (RECENT + directly relevant to the role)
**Use for:** recent work, AI/LLM experience, innovation, connecting to Oracle Health

```
S: PNS on-call engineers burned 15–25 minutes per incident manually querying
   4–5 systems just to find why an offer wasn't getting 2-day delivery.

T: I owned the domain layer of a new AI assistant — the part that required
   actually knowing how the domain works, not the AI plumbing.

A: - I built 40+ tools exposing our internal APIs to an LLM agent, and wrote each
     tool's description precisely enough that the model reliably picks the right one.
   - I authored the system prompt encoding the debugging logic — what to check first,
     market-specific defaults — and validated against 20 real past incidents.

R: Investigation dropped from 15–25 minutes to 8–12 seconds, with root cause and a
   recommended fix, not just raw data. The lesson: the AI was the easy part —
   the value was translating years of domain intuition into rules a machine applies.
```

> **This is your bridge to Oracle Health.** Same pattern — AI collapsing manual,
> multi-system work into seconds — applied to a domain that matters more.

---

### STORY G — Mentoring / Raising the Bar (FEEDBACK + teaching)
**Use for:** giving feedback, mentoring, teaching, lifting the team

```
S: As one of the senior engineers, I review a lot of design and code from SDE-1
   and SDE-2 engineers. After the CPU incident, I saw the same logging anti-pattern
   showing up in new pull requests.

T: I needed to stop the pattern from spreading without just being the person who
   blocks PRs and demoralizes people.

A: - Instead of leaving 40 review comments, I turned the lesson into a static-analysis
     rule so the feedback was automatic, consistent, and not personal.
   - For the individuals, I walked one engineer through WHY it caused a production
     incident — showed the heap dump — so it landed as a war story, not a nitpick.

R: The anti-pattern stopped appearing, and the team understood the "why," not just
   the "don't." The lesson: the best feedback scales without you — turn a repeated
   correction into tooling, and teach the one story that makes people remember it.
```

---

## 2 — Question → Story Map

```
┌───────────────────────────────────────────────┬────────────────────┐
│  If they ask about...                         │  Reach for...       │
├───────────────────────────────────────────────┼────────────────────┤
│  Proudest / most impactful / best project      │  A (multi-slot)     │
│  Hardest / most challenging project             │  A, then B          │
│  Hardest production issue / firefight           │  B (100% CPU)       │
│  A time you were stuck / didn't know            │  B                  │
│  High-throughput / scale / event-driven         │  C (ingestion)      │
│  Security / careful / risky change              │  D (auth migration) │
│  Cross-team / coordination / influence          │  E, then A          │
│  Recent work / AI / innovation                  │  F (AI agent)       │
│  Giving feedback / mentoring / teaching         │  G, then B          │
│  Ownership / beyond your scope                   │  B (Sonar rule)     │
│  Conflict / disagreement                         │  A (contract) or B  │
│  Ambiguous requirements                          │  A or F             │
│  Pressure / tight deadline                       │  B (peak incident)  │
│  Difficult stakeholder                           │  E or A             │
│  Failure / mistake                               │  See Q3 script      │
└───────────────────────────────────────────────┴────────────────────┘
```

---

## 3 — Full Scripted Answers (20 questions)

Say these almost verbatim. They already respect the confidentiality rules.

---

**Q1 — "Tell me about yourself."**
> "I'm Kapil — I've spent about 5 years at Walmart on the same backend platform,
> currently at a senior engineer level. The platform is Walmart's promise and
> sourcing engine — every time someone adds an item to a cart, it decides which
> store or warehouse ships it and what delivery date the customer sees. It runs
> around 700,000 requests a minute at sub-100ms p95.
>
> My work spans three things: I've delivered features end-to-end — like Canada's
> multi-slot delivery; I've been the on-call engineer who leads the hard production
> debugs, including a peak-traffic CPU incident; and most recently I built the domain
> layer of an AI assistant that lets engineers debug in plain English instead of
> querying five systems by hand. That last one is why the Oracle Health role caught
> my eye — it's the same thing, AI collapsing manual work into seconds, applied to
> a domain that matters more."

---

**Q2 — "Walk me through a contribution you're especially proud of."**
> Tell **Story A (multi-slot)**. Open with the human framing: "Customers in Canada
> could only see one delivery option; the business wanted to show Express and
> Standard side by side." Then Task → Action → Result. End: "What I'm proud of
> isn't the design — it's that we changed a core contract four teams depend on and
> nobody broke."

---

**Q3 — "Tell me about a time you failed or made a mistake."**
> "On the multi-slot project, my first design handled the happy path — a customer
> picks one of the two slots — but I under-thought the edge case where a customer
> confirms only one option and we've placed inventory holds for both. My initial
> approach would have leaked inventory — held stock that never got released.
>
> I caught it in shadow testing, not design review, which honestly bothered me —
> it should have surfaced earlier. I owned it with the team, then co-designed an
> explicit release path with the inventory team so the unconfirmed hold gets
> released cleanly, and added it to the test suite.
>
> The lesson stuck: on anything involving reservations or holds, I now design the
> failure and partial-success paths FIRST, before the happy path — because those
> are where the real bugs and the real cost live."

> **Why this works:** real, non-fatal, you owned it, you fixed it systemically,
> senior lesson. Do NOT use the CPU incident as your "failure" — that's a WIN.

---

**Q4 — "Tell me about your hardest production issue."**
> Tell **Story B (100% CPU)** in full. Emphasize the methodical triage (rule out
> external, regression, surge → look inside the JVM), the thread-dump/heap-dump work,
> and the two-part result: fast fix + the static-analysis rule and heap alert so it
> can't recur. This is your strongest story — take the full 2 minutes.

---

**Q5 — "Tell me about a conflict with a teammate."**
> "On multi-slot, I disagreed with an engineer on the upstream team about the shape
> of the new contract. They wanted a richer, more complex response so they'd have
> everything in one call; I wanted a leaner, additive change to protect the other
> three teams consuming it and keep the migration safe.
>
> Rather than argue in the abstract, I framed it around the actual risk: I showed
> that the additive approach let all four consumers migrate independently, and the
> richer version would force a coordinated big-bang change across teams on different
> timelines. Once it was about migration risk instead of preference, we aligned on
> the leaner contract — and I took on their real concern separately.
>
> The lesson: turn an opinion clash into a question about risk or data, and it stops
> being personal. The best answer usually wins on its own once you reframe it."

---

**Q6 — "Tell me about giving someone difficult feedback."**
> Tell **Story G**. Emphasize: you made it about the work not the person, you showed
> the heap dump so the "why" landed, and you scaled the feedback into a rule so it
> wasn't 40 personal nitpicks. Close: "Good feedback should teach and then scale
> beyond you."

---

**Q7 — "Tell me about teaching someone something complex."**
> "After the CPU incident, the root cause — that a disabled debug log could still
> tank production — was counterintuitive, and newer engineers kept writing the same
> pattern. I sat one of them down and instead of explaining it abstractly, I walked
> them through the actual heap dump from the incident: 'here's the giant string,
> here's what's holding the reference, here's why GC couldn't keep up.'
>
> Making it concrete — a real war story with real evidence — did what a paragraph in
> a wiki never would. He never wrote that pattern again, and he started catching it
> in others' reviews. Teaching is compression: find the one concrete thing that makes
> the concept click, and skip the lecture."

---

**Q8 — "Tell me about taking ownership beyond your role."**
> "After I fixed the CPU incident, the assigned work was done — the pods were healthy.
> But I knew the same logging pattern almost certainly existed elsewhere in a codebase
> that large. Nobody asked me to, but I wrote a static-analysis rule to flag the
> pattern across the whole codebase, found 40-plus instances, and cleaned up the four
> that were on hot paths. I also added a heap-pressure alert so the next person gets
> signal before CPU saturates.
>
> The lesson: fixing the ticket is table stakes; ownership is fixing the class of
> problem so it can't page someone at 2am again."

---

**Q9 — "Tell me about working with someone very different from you."**
> Use **Story E (Kafka decommissioning)** or the **Q5 conflict**. Frame: "I tend to
> be cautious and incremental; one of the producer-team leads was much more
> move-fast. On the cluster migration, instead of fighting that, I used it — they
> pushed the pace, I brought the per-topic validation and parallel-run safety net.
> The combination was better than either of us alone. Different working styles are
> an asset if you let each person cover the other's blind spot."

---

**Q10 — "Tell me about a high-scale or high-throughput system."**
> Tell **Story C (ingestion tier)**. Lead with the numbers — 18 domains, millions of
> records/day, 120–200 pods, sub-minute SLO. Emphasize the back-pressure design and
> the poison-pill handling. This sells your event-driven/scale depth.

---

**Q11 — "How do you handle ambiguous requirements?"**
> "I start by finding the real user pain rather than the stated feature. On the AI
> assistant, the ask was vague — 'make on-call debugging easier.' I grounded it by
> pulling 20 real past incidents where we already knew the root cause, and made the
> concrete target: could the system solve THESE correctly? That turned an ambiguous
> goal into a testable one, and it drove every design decision after that. Ambiguity
> usually means nobody's translated the business wish into a measurable target yet —
> that translation is the job."

---

**Q12 — "Tell me about a tight deadline or high-pressure moment."**
> Use **Story B** framed on pressure: "The CPU incident was as high-pressure as it
> gets — peak traffic, latency 5x, on the edge of dropping customer requests, and no
> obvious cause. The pressure is exactly why the methodical approach matters: under
> stress the temptation is to guess and restart things. I forced myself to rule out
> causes one at a time instead. Structure beats panic."

---

**Q13 — "How do you deal with a difficult stakeholder or customer?"**
> "My 'customers' were the on-call engineers who'd use the AI assistant — and they
> were skeptical experts, which is the hardest audience. They weren't going to trust
> an AI answer just because it sounded confident. So instead of selling it, I
> validated it against cases they'd personally debugged and knew the answer to, and
> showed them the match. Earning trust from expert users by proving it on their own
> hard cases — that was the real work, more than the code."

---

**Q14 — "Why are you leaving Walmart?"**
> "It's been a great 5 years — I got new, harder problems almost every year because
> the platform kept growing, so I never felt stuck. But I've now seen what AI agents
> can do for a complex domain, and I want to apply that where the stakes are higher.
> Oracle Health is doing exactly that, and I'd rather go deep in a domain worth years
> than be the AI person on a logistics team." (Positive, forward-looking. Never
> negative about Walmart.)

---

**Q15 — "Why Oracle Health?"**
> Use the full answer from **file 14, Section 6**. Short version: "Two reasons — the
> mission of reducing clinician burden so they spend time with patients maps directly
> to what I've done, using AI to collapse manual multi-system work into seconds; and
> the work is squarely my strongest area — LLMs and agents applied to a hard, real
> domain — except here it's the core of the product, not a side project."

---

**Q16 — "What's your greatest strength?"**
> "Two things in combination. One — building services that are both fast and correct
> under high concurrency: thread-pool design, Kafka, low-latency Java. Two — being
> the person who can take an ambiguous production problem and methodically root-cause
> it under pressure. The CPU incident is the story I usually give for the second one."

---

**Q17 — "What's your greatest weakness?"**
> "I've got less hands-on depth with infrastructure-as-code — Terraform, cloud
> provisioning directly. I work on deployments and understand them, but a platform
> team authors the infra, so I haven't built that muscle myself. I've been closing it
> deliberately, and it's an area I'd want to grow into rather than avoid." (Honest,
> scoped, non-fatal for a backend/AI role. Never say "perfectionist.")

---

**Q18 — "What does a senior engineer do on your team?"**
> "Three things. Lead design and delivery of features spanning multiple teams —
> multi-slot is the canonical example. Mentor the more junior engineers through
> design review, code review, on-call shadowing. And own a slice of operational
> excellence — for me that's been performance and the production-debug runbooks."

---

**Q19 — "Have you worked in healthcare before?"**
> "Not directly, and I won't pretend otherwise. What I have is a system with
> healthcare-adjacent properties — high-throughput, low-latency, multi-region,
> audit-heavy, multi-tenant with strict isolation — and recent hands-on AI-agent work
> that's the same pattern this role needs. The engineering transfers directly; the
> domain — EHR workflows, RCM, coding standards — is what I'd ramp on, the same way I
> ramped on Cassandra and Kafka five years ago. I've already been studying the
> workflows: prior auth, clinical documentation, the coding pipeline."

> This is where files 11–13 pay off — you can talk credibly about prior auth and
> medical coding as AI problems.

---

**Q20 — "Do you have questions for me?"**
> Always ask 3. Pull from **file 14, Section 7**. Best two for a director:
> "What separates a good first year from a great one at this level, in your eyes?"
> and "What's the hardest problem the team is wrestling with right now that a new
> senior engineer could actually help move?"

---

## 4 — The "What Would You Do Differently" Table

Directors love this question. Never say "nothing." Have one per story.

```
┌──────────────────────────┬──────────────────────────────────────────────────┐
│  Story                   │  "What would you do differently?"                │
├──────────────────────────┼──────────────────────────────────────────────────┤
│  A — Multi-slot          │  "Versioned the API at the URL level instead of  │
│                          │  overloading the response. We chose pragmatically │
│                          │  under timeline; greenfield I'd version cleanly." │
├──────────────────────────┼──────────────────────────────────────────────────┤
│  B — CPU incident        │  "Caught it earlier — we only had a CPU alert,   │
│                          │  not a heap-pressure alert. I added that after,   │
│                          │  but it should have existed before."              │
├──────────────────────────┼──────────────────────────────────────────────────┤
│  C — Ingestion tier      │  "Rebuild it as small per-pipeline services with │
│                          │  shared libraries instead of one artifact behind  │
│                          │  a startup flag. Right call then, doesn't age well."│
├──────────────────────────┼──────────────────────────────────────────────────┤
│  F — AI assistant        │  "Invest in a per-tool test harness with mocked  │
│                          │  APIs up front — we tested end-to-end first, which│
│                          │  made failures hard to isolate. Cost us weeks."   │
└──────────────────────────┴──────────────────────────────────────────────────┘
```

---

## 5 — Confidentiality Guardrails

Say the concept, never the internal identifier. Applies to every answer above.

```
✓ SAFE                                    ✗ NEVER SAY
────────────────────────────────────────────────────────────────────
"our promise & sourcing engine"           the internal codename + page IDs
"a logging-library misuse pattern"         "junior dev's sloppy code" / names
"a runtime config flag"                    specific CCM key names
"a large enterprise customer"              specific seller / carrier / BU IDs
"we had a peak-traffic incident"           specific outage dates
"~700K rpm, sub-100ms p95"                 confidential GMV / revenue figures
"most services are on Java 17"             "our ingestion is on Java 8" (unprompted)
"we use Resilience4j for new code"         "we use Hystrix everywhere"
```

**Two rules that cover everything:**
1. If it needs Walmart SSO to see, don't say the identifier — describe the concept.
2. Never throw a teammate, team, or "the old code" under the bus. "A pattern in the
   codebase," never "someone's bad code."

---

## THE ONE THING TO REMEMBER

> Every story quietly answers the director's real question:
> *"When this person hits a hard problem, a conflict, or a failure —
> do I want them on my team?"*
> Impact + ownership + humility + range = **yes.**
