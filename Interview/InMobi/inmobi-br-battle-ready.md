# InMobi Bar Raiser — Battle-Ready Guide (Temp File)

> **What BR evaluates (from their PDF, page 9):**
> 1. **Depth in past work** — technical depth, ownership, trade-offs, challenges, lessons learned
> 2. **Long-term thinking** — scalability, maintainability, technical debt, system evolution, strategic planning
> 3. **Learn and be curious** — growth mindset, intellectual curiosity, continuous learning
>
> **Their mindset (from PDF):** *"Our interviews are conversations with a potential future colleague."*
>
> **Your advantage:** You have 5 years on the SAME platform with clear ownership stories. The risk is sounding like you're describing someone else's system. Every answer must end with **"I did X"**, not **"we have X"**.

---

# PART 1 — The 3-Layer Answer Template (Use for EVERY project question)

```
┌─────────────────────────────────────────────────────────────┐
│        HOW TO ANSWER ANY PROJECT QUESTION                    │
│                                                             │
│  LAYER 1: CONTEXT (15 seconds)                              │
│  "The platform does X at Y scale."                          │
│  → One sentence. No jargon. No acronyms.                    │
│                                                             │
│  LAYER 2: WHAT I DID (30-60 seconds)                        │
│  "Within that, I specifically owned..."                     │
│  → YOUR contribution. Not "we". "I".                        │
│  → Name the technical decision YOU made.                    │
│                                                             │
│  LAYER 3: TRADE-OFFS (15-30 seconds)                        │
│  "The trade-off was... If I were doing it greenfield..."    │
│  → Name what you'd do differently.                          │
│  → This is the SENIOR SIGNAL they're listening for.         │
│                                                             │
│  THEN OFFER TWO DOORS:                                      │
│  "Happy to go deeper into [X] or [Y] — your call."         │
│  → YOU control the conversation. They pick the path.        │
│                                                             │
│  KEY INVARIANT:                                             │
│    Layer 1 = anyone can say. Layer 2 = SDE2 bar.            │
│    Layer 3 = SDE3+ signal. If you skip Layer 3, you lose.   │
│    The doors keep YOU driving the round.                     │
└─────────────────────────────────────────────────────────────┘
```

---

# PART 2 — Your 5 Stories (Translated for InMobi/iDSP)

> **Rule:** Never say Walmart acronyms without translation. Never say MCSE, CCAP, Wakanda, Hollow, CCM, Kitt without explaining what they do in one phrase. The interviewer's mental tax is YOUR problem to manage.

## Story Menu — Which One When

| Interviewer asks... | Lead with | Bridge to |
|---|---|---|
| "Tell me about yourself" | **Story 0** (elevator) | Offer 2 doors into Story 2 or Story 4 |
| "Most challenging project" | **Story 2** (multi-slot) | "Or if you prefer a debugging story..." → Story 3 |
| "Hard production issue" | **Story 3** (100% CPU) | Story 1 architecture if they probe the system |
| "High-throughput system" | **Story 4** (Kafka ingestion) | Story 1 architecture |
| "Cross-team delivery" | **Story 2** (multi-slot) or **Story 5** (Mexico migration) | |
| "What do you do day-to-day?" | **Story 0** then offer doors | |
| "Tell me about a platform you built" | **Story 1** (MCSE architecture) | |

---

## STORY 0 — Elevator Pitch (memorize verbatim)

> *"I'm Kapil, I've been at Walmart for 5 years on the same backend platform — currently SDE-3. The platform is our promise and sourcing engine. Every time someone adds an item to a Walmart cart, it decides which warehouse ships it and what delivery date the customer sees. It runs at about 700K requests per minute, sub-100ms p95.*
>
> *My work spans three areas: I've built features end-to-end like a multi-slot delivery redesign for Canada; I've operated the system as on-call and led hard production debugs including a 100% CPU incident; and I've owned the upstream Kafka ingestion tier that hydrates the platform's caches — 18 deployments processing 4-5 million events per day.*
>
> *Tech stack is Java 17, Spring Boot, Kafka, Cassandra, and Kubernetes on Azure. I'm here because iDSP's bidding engine has the same engineering shape — millions QPS, sub-100ms decisions, event-driven data, in-memory state — and I want to apply these patterns in ad-tech."*

**iDSP bridge (say this last sentence):**

> *"The way I think about it: my current system decides 'which warehouse, what date' in 100ms. iDSP decides 'which advertiser, what bid' in 100ms. The shape is identical — fan-out, score, pick the best, return fast."*

---

## STORY 1 — Platform Architecture (when asked "tell me about your system")

### 30-Second Version

> *"The platform is a modular monolith — about 30 Maven modules in one deployable. The reason is latency: each request fans out to 50-100 internal evaluations, and network hops between microservices would blow the 100ms budget.*
>
> *The request flow has three phases. Pre-Scatter — fetch all input data in parallel. Orchestrator — generate every possible fulfillment option and evaluate them in parallel. Gather — reduce to the single best option per item. The whole thing runs in under 100ms because everything in the middle is in-memory."*

### iDSP Bridge

> *"This maps directly to iDSP's bidder architecture. Your Pre-Scatter fetches user data, campaign data, supply quality signals. Your Orchestrator evaluates all eligible campaigns per request. Your Gather picks the best bid. Same shape, different domain. You use Aerospike where we use an in-memory cache called Hollow — both serve the same purpose: keeping the hot path off the database."*

### Follow-Up Answers (have ready)

**"Why modular monolith, not microservices?"**
> "Per-request fan-out of 50-100 evaluations under 100ms. A 5ms network round-trip × 50 hops = 250ms — unacceptable. Microservices are correct for system boundaries — the upstream and downstream ARE microservices. But internally, in-process calls are the only way to make the math work."

**"How do you handle a slow dependency?"**
> "Three layers. Thread-pool isolation per dependency so one slow service can't exhaust pod threads. Per-call timeout shorter than the request budget. Circuit breaker that opens after a failure-rate threshold and falls back — typically a stale cache or a pessimistic delivery date. Better to promise late than to fail the request."

**"How do you scale horizontally?"**
> "Application pods are stateless — every cache is rebuildable from Kafka or a snapshot store. So we scale by adding pods. Kafka consumer groups divide partitions automatically. The hard ceiling is downstream — the inventory service and the database — not our tier."

**"What's the hardest thing about this system?"**
> "Three things. First, latency floor — 50-100 evaluations must complete in 100ms, so every component is either in-memory or thread-isolated. Second, multi-market multi-tenant — same codebase serves 4 countries and multiple seller types, all config-driven. Third, eventually-consistent reference data — offer data flows through Kafka into in-memory caches, so we trade strong consistency for read speed. The design constraint is: stale data must always be safe."

---

## STORY 2 — Canada Multi-Slot Delivery (your hero FEATURE story)

### 30-Second Pitch

> *"I led the design and implementation of multi-slot delivery for Canada. The platform was built to return one delivery slot per item; the business wanted Express — same-day or next-day — and Standard — 2 to 5 days — in the same response with separate prices. I redesigned the reservation generator to emit multiple inventory holds per item, co-designed a new API contract with the upstream team, and shipped it behind a feature flag. The hard part wasn't the design — it was migrating without breaking four consuming teams."*

### The Hard Part (this is what they dig into)

> *"Three edge cases consumed most of the engineering time:*
>
> *Partial confirmation — customer picks Express only, we have to release the Standard hold without leaking inventory. Re-reservation on cart edit — slots change between promise and checkout, we needed diff semantics, not full re-holds. Slot price drift — price can change between promise and checkout from demand-based pricing, the contract had to surface the delta so checkout could re-prompt."*

### Trade-Off (ALWAYS say this)

> *"The response has both old single-slot fields and a new slots array. It's ugly — I'd rather have versioned the API at the URL level. But we couldn't coordinate a v2 endpoint with four upstream teams on the timeline we had. So we chose backward compatibility over API cleanliness."*

### iDSP Bridge

> *"At iDSP this maps to returning multiple bid options per ad request — different creatives, different price points, different campaign objectives — in the same response. The same tension exists: extend the existing contract or version it? And the same edge cases: what if the creative changes between bid and impression?"*

---

## STORY 3 — 100% CPU Production Debug (your hero DEBUGGING story)

### 30-Second Pitch

> *"Last summer our Canada pods hit 100% CPU during peak. Latency went from p95 80ms to 400ms. I led the debug. Root cause: a logging library misuse — debug-level log statements were constructing full payload strings via string concatenation even with debug disabled. The strings retained references to large response objects, driving GC into a death spiral, which presented as CPU saturation. Fix was a one-line guard, but diagnosis took three hours of thread-dump and heap-dump analysis. I wrote a Sonar rule to catch the pattern across the codebase — found and fixed 40 more instances."*

### The Senior Signal in This Story

- **Methodical triage** — ruled out external, ruled out regression, then looked inside the JVM
- **Tooling fluency** — thread dumps, heap dumps, retention analysis
- **Root cause depth** — didn't stop at "fix the log line", explained WHY the symptom was CPU
- **Long-term thinking** — Sonar rule, heap-pressure alert, runbook for the next on-call

### Follow-Up: "What's the scariest CLASS of issue in your system?"

> *"Not infrastructure outages — those are visible through dashboards and runbooks handle them. The dangerous ones are bad config pushes. Our runtime config system can change business logic across every pod simultaneously, instantly, bypassing every circuit breaker — because the service is technically healthy, it's just doing the wrong thing. A wrong carrier cutoff can make items non-purchasable across an entire market. The fix is config governance — staged rollouts, per-key rollback, and alerting on BUSINESS metrics like promise-date distribution, not just system metrics like latency."*

### iDSP Bridge

> *"At iDSP's scale — millions QPS — this exact pattern applies. A bad targeting config could suppress ads for an entire advertiser segment while all bidders look healthy. You'd need the same separation: system health metrics AND business KPI alerts. The one I'd add is bid-rate monitoring per campaign — if it drops to zero and the campaign is active, that's a config-level signal."*

---

## STORY 4 — Kafka Data Ingestion Platform (your SCALE story)

### 30-Second Pitch

> *"I own the upstream Kafka ingestion tier — the service that consumes events from 18 different domain teams and lands them in Cassandra. It's one Maven WAR deployed 18 times, each configured with a different startup flag that activates different Kafka listeners. Hot pipelines run on 120-200 pods, processing 4-5 million records per day. The V2 consumer uses reactive batching with a bounded processor pool for natural backpressure — when the pool is full, the poll loop blocks, which slows Kafka consumption."*

### Follow-Up Answers

**"Why one JAR for 18 deployments?"**
> "Three reasons. Single artifact = single security scan and Sonar build. Shared domain modules so cross-domain bug fixes deploy together. One mental model for on-call. The trade-off: if I were rebuilding, I'd split into per-pipeline services with shared libraries — the unified deploy was right for the team size at the time but doesn't age well."

**"How do you handle a poison pill?"**
> "Never re-throw — that blocks the partition forever on one bad record. Catch, log with offset and key, increment a metric, publish to a retry topic, commit past. A separate retry consumer drains with backoff. After N retries, dead-letter store for human review. Critical rule: the processing operation must be idempotent, because any record might be processed more than once."

**"What if the retry topic also fails?"**
> "If the retry consumer is down, records sit in the retry topic durably — Kafka holds them, we page on retry-consumer health. If the retry topic itself is unwritable — broker outage — the primary consumer stops committing offsets so records replay when Kafka recovers. Never acknowledge an unprocessed record just because the retry path failed."

### iDSP Bridge

> *"iDSP's data tier has the same shape — campaign updates, targeting data, user signals, fraud rules — all flowing through event streams into the bidder's in-memory state. The patterns are identical: idempotent consumers, poison-pill handling, bounded processor pools, lag-based SLOs. At iDSP's scale, you'd probably use Flink instead of raw Kafka consumers for the stream processing — but the failure-handling principles are the same."*

---

## STORY 5 — Mexico Commerce Platform Migration (your LEADERSHIP story)

### 30-Second Pitch

> *"I led the backend migration of Walmart Mexico's checkout from a legacy fulfillment path to the unified Commerce Platform — same engine powering the US. The hard part wasn't code — it was zero-downtime against live Mexico traffic. We ran old and new paths in parallel, used per-cohort feature flags to ramp from 1% upward, compared business KPIs — order completion rate, promise accuracy — on each cohort before going wider, and kept a one-minute rollback ready. Migration completed without a customer-visible disruption."*

### Why This Is a Senior Story

- **Cross-team coordination** — multiple producers and consumers had to align contracts
- **Zero rollback drama** — we tested the rollback, didn't just hope
- **Decision criterion was business KPIs**, not technical metrics — that's the senior signal

### iDSP Bridge

> *"This maps to iDSP's model rollout strategy. When you deploy a new bidding model, you don't flip 100% at once — you canary at 5%, compare CTR/CVR/ROI on the canary cohort vs control, and ramp if metrics hold. The shape is identical: parallel old/new, feature-flagged rollout, business KPIs as the gate, instant rollback. The difference is iDSP's feedback loop is faster — ad impressions give you signal in minutes, not days."*

---

# PART 3 — The 3 BR Competencies (Scripted Answers)

## Competency 1: Depth in Past Work

**They'll test this by asking follow-ups that go deeper than your pitch. Be ready for:**

| Follow-up | Ready answer |
|---|---|
| "How do you size your thread pools?" | "I/O-bound = 2-4x CPU count, tuned empirically. Always `ThreadPoolExecutor` with bounded queues — unbounded masks backpressure. Different pools per dependency for isolation." |
| "Callable+Future vs CompletableFuture?" | "Callable+Future when I need ALL results before continuing (Pre-Scatter — 5 parallel calls). CompletableFuture when I want to combine results as they arrive without blocking on the slowest (Orchestrator — 50-100 evaluations)." |
| "How do you handle eventual consistency?" | "Design every consumer to be safe in the presence of stale data. For us, stale = slightly later delivery date — safe. Stale inventory = over-promise — unsafe. So we do a final inventory check at checkout time against a strongly-consistent path." |
| "Walk me through how a write becomes a read." | "Domain team publishes Kafka event → ingestion service consumes, validates, writes to Cassandra → Spark precompute reads Cassandra, builds new in-memory cache snapshot → pods detect new version, atomic swap. Hot read path never touches Cassandra." |
| "CAP for your system?" | "AP on the read path — availability + partition tolerance over consistency. Pod that loses DB connectivity falls back to in-memory snapshot. Exception: reservation path is CP — can't double-allocate inventory." |

**The depth signal:** If they keep asking and you keep having crisp answers, you're winning. If you run out after 2 questions, you're losing. The cross-questions above cover the 10 most likely deep dives.

---

## Competency 2: Long-Term Thinking

**They'll test this by asking "what would you do differently?" or "how do you think about technical debt?"**

### Pre-built "What I'd Do Differently" per Story

| Story | What you'd change |
|---|---|
| **Architecture** | "Push observability-as-code from day one — dashboards were bolted on later and are inconsistent. Also standardize on Resilience4j from the start — Hystrix migration is pure tech debt." |
| **Multi-slot** | "Version the API at the URL level instead of overloading the response. We chose pragmatically for the timeline; greenfield I'd choose differently." |
| **CPU debug** | "I'd have caught it earlier if we'd had a heap-pressure alert instead of just CPU alerts. I added that after the incident." |
| **Ingestion** | "Split the 18 deployments into separate small services with shared libraries. The one-JAR pattern was right for the team size; it doesn't age well." |
| **Mexico migration** | "Invest more in automated reconciliation between old and new paths. We relied on manual KPI comparison; automated drift detection would've been safer." |

### "How do you think about technical debt?"

> *"I classify tech debt into three buckets. Deliberate debt — we chose a shortcut knowingly and it has a repayment plan (like the dual-shape API). Accidental debt — it crept in without a decision (like Hystrix still being in production). And bit-rot debt — something that was the right choice at the time but the ecosystem moved on (like Spring 4 on the ingestion tier). For each, I want a different response: deliberate debt gets a tracking ticket; accidental debt gets a Sonar rule or lint check; bit-rot gets a modernization roadmap with a business case."*

### "How do you balance feature work with tech debt?"

> *"I don't treat them as opposites. The best features I've shipped — like multi-slot — included tech-debt repayment as part of the design. Refactoring the reservation generator wasn't tech-debt work, it was the feature. The separate category is pure modernization — like the Hystrix-to-Resilience4j migration — and for that, I pitch it as risk reduction with a concrete number: 'this library is EOL, the last security patch was 18 months ago, here's the blast radius if a CVE hits.'"*

---

## Competency 3: Learn and Be Curious

**They'll test this with "how do you stay current?" or "what have you learned recently?"**

### "What have you learned recently?"

> *"Two things. First, I've been exploring LLM-based tooling for operational workflows — I designed the tool specification layer for an internal AI agent that helps our on-call team. That's 40+ tools backed by Azure OpenAI, covering inventory lookups, carrier tracing, and Cassandra queries. It taught me that prompt engineering is less about cleverness and more about schema design — the tool specification IS the interface.*
>
> *Second, I've been studying iDSP's architecture because of this interview. The ML-driven bidding pipeline — Triton for inference, feature stores for real-time features, A/B frameworks at millions QPS — is genuinely new territory for me. I haven't done online ML inference at that scale, and that's exactly the kind of problem I want to grow into."*

### "Why InMobi specifically?"

> *"Three reasons. First, iDSP's engineering challenge is the same shape as what I've spent 5 years on — high-throughput, low-latency, event-driven decision engines — but in a domain I haven't touched. That's the ideal growth vector. Second, the ML-first approach to bidding decisions is something I want to be closer to. I've consumed ML models; I want to build systems that serve them at scale. Third, your scale is real — millions QPS with <100ms budget. I've lived in that world at Walmart, and I know the engineering discipline it demands."*

### "What do you do outside of work to grow?"

> *"I'm building a structured knowledge base for interview prep — DSA patterns, system design, LLD — in Git, which forces me to write clearly enough that I can re-read it under pressure. And I've been exploring Spring AI and LangChain4j for agent-based workflows — the internal AI agent project came out of that curiosity."*

---

# PART 4 — Gray Areas (InMobi-Specific)

## Things to NEVER mention voluntarily

| # | Don't Say | Why |
|---|---|---|
| 1 | "It was sloppy code from another team" | Signals you'd throw teammates under the bus |
| 2 | "We use Hystrix everywhere" | It's deprecated. Lead with Resilience4j, mention Hystrix as "legacy, migrating" |
| 3 | "We use Java 8" | Ingestion is still Java 8. Say: "Most services are on Java 17; the ingestion tier is older — known modernization item" |
| 4 | "I built an ML pipeline" | You consumed Predictive TNT through a cache. Say: "I consume ML model outputs with guardrails — I didn't train the model" |
| 5 | Walmart-specific incidents | Don't name customer-impacting outages. Pivot to YOUR debug story |
| 6 | Why you're leaving Walmart negatively | Say: "I want to apply the same patterns in a new domain — iDSP's engineering challenge excites me" |
| 7 | Walmart internal acronyms without translation | MCSE = "our promise and sourcing engine". Wakanda = "our inventory service". CCM = "our runtime config system" |

## Things to call OUT proactively

| # | Proactive Move | Why It Works |
|---|---|---|
| 1 | "I've been at Walmart 5 years, which is longer than usual" | Pre-empts "low ambition" read. Follow with: "The platform kept growing — I got new problems every year without changing employers" |
| 2 | "I'm coming from e-commerce, not ad-tech" | Disarming. Follow with: "The patterns transfer — high-throughput Java, event-driven, in-memory state, sub-100ms decisions. The domain is the growth" |
| 3 | "I consume ML, I don't train it" | Prevents overclaiming. Respected as honest self-awareness |

---

# PART 5 — The 5 Phrases That Make You Sound Senior

Drop these naturally — 1-2 per major answer, not more:

| # | Phrase | When to Use |
|---|---|---|
| 1 | *"The trade-off there was..."* | After describing any design decision |
| 2 | *"In our case the binding constraint was..."* | When explaining why you chose approach X |
| 3 | *"The thing that would actually break this is..."* | When asked "what could go wrong?" |
| 4 | *"We chose A over B because at our scale..."* | When comparing approaches |
| 5 | *"I don't remember the exact value, but the design principle was..."* | When asked a specific number you don't recall |

---

# PART 6 — Questions to Ask THEM (Brownie Points)

From their PDF: *"Ask questions. Gain brownie points."*

Pick 2 — at least 1 technical:

> 1. *"iDSP is moving all decisioning to ML — how does the A/B framework work for model rollouts at your QPS? Shadow traffic or canary percentage?"*
> 2. *"With Aerospike as the primary KV store, how do you handle schema evolution for targeting data?"*
> 3. *"What's the biggest engineering challenge the iDSP team is solving right now?"*
> 4. *"How does the feature store serve real-time features to the bidder within the 100ms window?"*
> 5. *"What does ownership look like for an SDE-2 here — do you own a full engine end-to-end or contribute across multiple?"*

---

# PART 7 — Minute-by-Minute BR Round Plan

```
Minute 0-2:    INTRODUCTION
               Say Story 0 elevator pitch (memorized).
               End with: "Happy to go deep on any of these."

Minute 2-10:   DEEP DIVE #1 (they pick)
               Use the 3-layer template.
               End EVERY answer with two doors.

Minute 10-20:  DEEP DIVE #2 (they pivot or dig deeper)
               If debugging → Story 3 (CPU)
               If scale → Story 4 (Kafka ingestion)
               If cross-team → Story 2 (multi-slot) or Story 5 (Mexico)

Minute 20-30:  CROSS-QUESTIONS
               They'll probe trade-offs and "what would you change?"
               Use Part 3 answers.

Minute 30-35:  LONG-TERM / CULTURE FIT
               "Why InMobi?" → Part 3, Competency 3
               "How do you handle on-call?" →
               "I treat on-call as ownership. Three things matter:
                runbook for every alert, clear escalation, post-incident
                review that produces an action item."

Minute 35-40:  YOUR QUESTIONS
               Ask 2 from Part 6. At least 1 technical.
               End on: "What's the biggest engineering challenge
               the team is solving right now?"
```

---

# PART 8 — iDSP Domain Bridges (Drop ONE per story)

| Your System | iDSP Equivalent | One Sentence to Say |
|---|---|---|
| MCSE decides "which warehouse, what date" | iDSP decides "which advertiser, what bid" | *"Same shape: fan-out, score candidates, pick the best, return in 100ms."* |
| Pre-Scatter → Orchestrator → Gather | Targeting → Bid Evaluation → Creative Selection | *"Both are scatter-gather pipelines under a latency ceiling."* |
| Hollow in-memory caches (16 of them) | Aerospike + local LRU caches | *"Both serve the same purpose: keep the hot path off the database."* |
| Kafka ingestion into Cassandra | Event streams updating targeting/campaign data | *"Same patterns: idempotent consumers, poison-pill handling, lag SLOs."* |
| Multi-market (US, MX, CA, CL) | Multi-exchange (different SSPs, different ad formats) | *"Config-driven behavior per market/exchange, same codebase."* |
| Bad config = scariest failure | Bad targeting config = suppressed ads | *"Both look healthy to infra monitoring. Only business-KPI alerts catch them."* |
| Modular monolith (can't afford network hops) | Bidder is a monolith (can't afford network hops at 100ms) | *"Same architectural constraint: latency budget kills microservices internally."* |

---

> **Bottom line for BR:** You're not telling them about Walmart. You're proving 3 things:
> 1. **Depth** — "I didn't just use the system, I understand WHY it's designed this way"
> 2. **Ownership** — "I built X, I debugged Y, I made the trade-off on Z"
> 3. **Growth** — "I want to apply these patterns in ad-tech, and I'm already thinking about iDSP's specific challenges"
>
> The 3-layer template + two-doors pattern keeps you in control. Trust it.
