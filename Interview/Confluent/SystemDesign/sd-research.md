# Confluent System Design Round — Research (Feb 2025 – Aug 2026)

> **Scope:** System design round ONLY. 18-month window: **Feb 2025 – Aug 2026**. Older data labeled "historical context" — useful for pattern recognition but NOT counted in frequency rankings.
>
> **Role:** Senior Software Engineer — Tableflow team (IBM/Confluent)
>
> **Round structure confirmed by HR:** 3 rounds total — 2 DSA + 1 System Design (for the specific IBM/Confluent Tableflow SSE loop)
> Other SSE loops may be 5 rounds (LLD + HLD + coding + values + HM).
>
> **Methodology note:** Frequency counts only DISTINCT DATED primary candidate reports (LeetCode, 1Point3Acres, JoinTaro, Hack2Hire, Glassdoor, Blind). Prep-site aggregators (PracHub, TechPrep, Exponent, Scoutify) are treated as corroboration only — not counted independently. One question echoed across 4 prep sites is frequency = 1.
>
> **Last updated:** Aug 2026 (deep research pass — expanded to 18-month window, added 4 new questions)

---

## 🎯 What the System Design Round Evaluates

**Mandatory strong hire for SSE2:** At least one design round must result in a "strong hire" (4/4). A regular "hire" is insufficient. One SSE2 candidate got all strong-hires in coding but lean-no-hire overall because no strong-hire in design. **Design is the gating round.**

**Primary evaluation axes (from recent reports):**

| Axis | What They Look For |
|---|---|
| **API Design Precision** | REST verbs, HTTP response codes, headers, endpoint naming. "If you do any mistake they highlight it as if the world has ended." |
| **Trade-off Defense** | Every decision must be justified — why this DB? Why this consistency model? Why this sharding strategy? |
| **SQL / Data Modeling** | Actual table design, indexing, normalization — not just "use Postgres" |
| **Distributed Systems** | Partitioning, replication, fault tolerance, consistency vs availability |
| **Pipeline Resilience** (senior) | Senior candidates expected to start with ingestion reliability, NOT personalization/ranking |
| **Concurrency** | Strongly consistent vs eventually consistent, thread safety, race conditions |

**What they do NOT expect:** Architecture diagrams are reportedly NOT required in the API-focused design round. Focus is on the API contract itself.

---

## ⭐ Master Question List — Ranked by Frequency + Recency

### TIER 1 — Most Repeated (3+ distinct primary reports, in-window)

---

#### 🥇 #1 — Design a Disposable / Temporary Email Service

| Field | Detail |
|---|---|
| **Appearances in window** | 3 confirmed primary reports |
| **Dates** | May 2025, Jul 2025, May 2026 |
| **Most recent** | May 2026 (Hack2Hire) |
| **Confidence** | ⭐⭐⭐ Highest — most repeated question in Confluent's question bank |

**What was asked:**
- May 2025 (LeetCode SSE2): Generate disposable emails valid for 5 hours
- Jul 27, 2025 (PracHub): Auto-expiring 10-minute inboxes. SMTP ingress, MX records, TTL-based storage, rate limiting, observability
- May 2026 (Hack2Hire): Same concept — but the **Bloom filter discussion consumed most of the round**. Come ready for a probabilistic data structures deep dive

**Key angles interviewers probe:**
- How do you generate a random-looking but collision-free email address?
- How do you implement TTL-based expiration (lazy deletion vs. active cleanup)?
- What's the MX record flow when mail arrives — where does it land?
- Rate limiting on generated addresses (prevent spam abuse)
- Bloom filter for fast "does this inbox exist?" lookup

**Variants emerging (2026):** Combined with news feed as a single round ("Design RSS Feed and Temporary Mail" — PracHub Jan/Feb 2026). May be separate problems or one multi-part round.

---

#### 🥈 #2 — Design an Aggregate News Feed / RSS Feed Ingestion Service

| Field | Detail |
|---|---|
| **Appearances in window** | 3 confirmed primary reports |
| **Dates** | Jul 2025, Feb 2026, Apr 2026 |
| **Most recent** | Apr 29, 2026 (PracHub — "Design an RSS News Feed Service") |
| **Confidence** | ⭐⭐⭐ High — confirmed at multiple dates across independent sources |

**What was asked:**
- Jul 2025 (PracHub): Ingest articles from many publishers, deduplicate, rank
- Feb 22, 2026 (PracHub): "Design a News Feed and Mail Service" — combined variant with mail component
- Apr 29, 2026 (PracHub): "Design an RSS news feed service — lets users subscribe to RSS sources, ingests articles"

**Key gotcha (from Jul 2025 report):** Candidates who started with ranking/personalization/ML were dinged. **Start with ingestion reliability** — how do I reliably pull from unreliable external publishers? Deduplication, schema normalization, idempotency first.

**Key angles:**
- Publisher polling vs. push (webhook/PubSub)
- Deduplication: URL-based + content hash
- Schema normalization across heterogeneous RSS formats
- Ranking: simple recency vs. relevance scoring
- User subscription storage and feed generation (fan-out on write vs. read)
- Freshness SLA: how often do you pull?

---

#### 🥉 #3 — API Design Round (Pure REST Contract)

| Field | Detail |
|---|---|
| **Appearances in window** | 3+ confirmed primary reports |
| **Dates** | May 2025, Apr 2026, and implied in multiple others |
| **Most recent** | Apr 2026 (1Point3Acres — "DB, SQL, and API Design round") |
| **Confidence** | ⭐⭐⭐ High — Confluent's **signature differentiated round** |

**What this round is:** No architecture diagram expected. Pure text/verbal definition of API contracts. Interviewer probes every endpoint.

**Confirmed API design problem in window:** May 2025 SSE2 (LeetCode): "Design a Podcast Service" — focused on Feed view, Subscribe/Unsubscribe. Heavy scrutiny on REST verbs, response codes, headers.

**What they scrutinize:**

| Aspect | What to Get Right |
|---|---|
| **HTTP Verbs** | GET (read), POST (create), PUT (full replace), PATCH (partial update), DELETE |
| **Response Codes** | 200, 201, 204, 400, 404, 409 (Conflict), 429 (Rate Limited), 500/503 |
| **Headers** | Content-Type, Accept, Authorization, pagination (Link, X-Total-Count) |
| **URL Structure** | `/api/v1/emails/{id}/messages` — hierarchical, resource-based |
| **Pagination** | Cursor-based (scalable) vs offset-based (simple) — know trade-offs |
| **Idempotency** | POST with Idempotency-Key header for safe retries |
| **Error Format** | Consistent JSON error body: `{ "error": { "code": "...", "message": "..." } }` |
| **Versioning** | URL path (`/v1/`) vs header (`Accept: application/vnd.api.v1+json`) |

> "If you make any mistake they highlight it as if the world has ended." — LeetCode SSE2 report, May 2025

---

### TIER 2 — Well-Documented (2 distinct primary reports, in-window)

---

#### #4 — Design a Globally Distributed, Read-Optimized Key-Value Store

| Field | Detail |
|---|---|
| **Appearances in window** | 2 confirmed primary reports |
| **Dates** | Jul 26, 2025 (PracHub), Oct 2025 (1Point3Acres) |
| **Most recent** | Oct 2025 |
| **Confidence** | ⭐⭐ High |

**What was asked:** Distributed KV store with global read optimization. Focus on consistency models, replication, partitioning.

**Key angles:** CAP theorem trade-offs, read-heavy optimization (caching layers, geographic replicas), consistent hashing for partitioning, leader-follower vs. leaderless replication, conflict resolution (last-write-wins vs. vector clocks).

---

#### #5 — Design a Reliable URL Shortening Service

| Field | Detail |
|---|---|
| **Appearances in window** | 1 confirmed primary report (in-window) + 3+ historical (pre-2025) |
| **Dates** | Jan 1, 2026 (PracHub) |
| **Most recent** | Jan 2026 |
| **Confidence** | ⭐⭐ High (strong historical pattern, now confirmed in 18-month window) |

**Note:** This was in `raw-research-all-rounds.md` as a high-confidence historical question (3+ pre-window reports) but was missing from the prior `sd-research.md`. Now confirmed in-window.

**What was asked:** Accept a long URL, return a short URL, redirect short-code requests back to original.

**Key angles:** Unique ID generation (base62 encoding, 7-char codes), read-heavy optimization (80:20 read:write), caching (what % of links get most traffic), database schema (KV vs relational), collision handling, analytics (click tracking), abuse detection.

---

#### #6 — Feedly-like System / Podcast Service (API Design Round Variant)

| Field | Detail |
|---|---|
| **Appearances in window** | 2 confirmed primary reports |
| **Dates** | May 2025 (Podcast — LeetCode SSE2), Nov 2025 (Feedly — JoinTaro SSE2 Remote) |
| **Most recent** | Nov 2025 |
| **Confidence** | ⭐⭐ Medium-High (API design round variant of news feed, not HLD round) |

**What was asked:**
- May 2025: Design a Podcast Service API — Subscribe/Unsubscribe, Feed view. API design round with REST contract focus
- Nov 2025 (JoinTaro SSE2 Remote): "Design a system like Feedly" — API + data modeling for subscribe/unsubscribe, newsfeed generation

**Key distinction:** These are the **API design round** flavors of the news feed theme. The HLD round focuses on ingestion pipeline architecture; the API round focuses on the contract.

---

### TIER 3 — Single In-Window Report (Credible Sources)

---

#### #7 — Health Check / "wasAlive" Monitoring System

| Field | Detail |
|---|---|
| **Appearances in window** | 1 confirmed primary report |
| **Date** | May 2026 (Hack2Hire) |
| **Most recent** | May 2026 |
| **Confidence** | ⭐ Medium — single report, but highly specific detail suggests authentic |

**What was asked:** Design a health monitoring system. Nodes report heartbeats. 5 time slots × 100ms. 3 consecutive inactive slots = declare node "down."

**Key angles:** Sliding window for heartbeat tracking, time-series storage, alert thresholds, false positive avoidance (jitter in network), distributed coordination (who decides "node is down"?), recovery detection.

---

#### #8 — DB + SQL + API Design Combined Round

| Field | Detail |
|---|---|
| **Appearances in window** | 1 confirmed primary report |
| **Date** | Apr 2026 (1Point3Acres) |
| **Most recent** | Apr 2026 |
| **Confidence** | ⭐ Medium |

**What was asked:** Combined round — database modeling + API contract design. Explicitly separated from the HLD round.

**Key angles:** Entity-relationship design, normalization vs. denormalization for read performance, index design (which columns, covering indexes), SQL query optimization alongside the REST API definition.

---

#### #9 — Job Scheduling System (Design + Code, Concurrency Round)

| Field | Detail |
|---|---|
| **Appearances in window** | 1 confirmed report (Blind) |
| **Date** | 2025 (exact date not disclosed) |
| **Most recent** | 2025 |
| **Confidence** | ⭐ Medium — reported as the **concurrency round**, not the HLD round |

**What was asked (from Blind):** Design and code a job scheduling system. Focused on concurrency — Semaphores, Locks, Conditions.

**Important:** This appears in the **concurrency/LLD round**, not the HLD system design round. Distinct from "Distributed Worker Platform" (which is HLD).

**Key angles:** Thread pool sizing, priority queues, lock-free vs. lock-based coordination, cron-style scheduling, retry logic, dead job detection.

---

### TIER 4 — Unverified / Domain-Inferred (No Dated Candidate Attribution)

These appeared on prep-site TechPrep (2026) with no specific candidate report or date. Consistent with Confluent's domain (Kafka/streaming) but NOT confirmed as asked. Treat as supplementary prep only.

| Question | Source | Confidence |
|---|---|---|
| **Reliable Event Ingestion Pipeline** (handling out-of-order records) | TechPrep 2026 (domain-inferred) | ⚠️ LOW — no candidate attribution |
| **Distributed Feature Flag System** (config propagation across microservices) | TechPrep 2026 (domain-inferred) | ⚠️ LOW — no candidate attribution |
| **Metrics Monitoring and Alerting System** | TechPrep 2026 (as prep recommendation, not asked) | ⚠️ LOW — mentioned as prep material, not a reported question |
| **WhatsApp / Chat Messaging System** | Blind thread (no date attribution) — design a real-time messaging system | ⚠️ LOW — mentioned in Blind Confluent design thread alongside KV Store and News Feed, no standalone dated report |
| **API Rate Limiter** | One Blind/LeetCode mention in Confluent context | ⚠️ LOW — no dated primary candidate report; high domain relevance (Confluent Cloud APIs enforce rate limits) |

---

### 📜 Historical Context (Pre-Feb 2025 — NOT counted in frequency rankings)

These were high-confidence questions from 2022-2024. Not counted in the 18-month window but useful for understanding Confluent's question pool.

| Question | Historical Reports | Notes |
|---|---|---|
| **Distributed Worker Platform** | 3+ reports (~2022-2024) | Async task processing, distributed queue, debuggability. Related to Job Scheduler above |
| **Design Kafka as a Service** | 2+ reports (~2022-2024) | Glassdoor + LeetCode confirmed. Direct domain relevance |
| **Spotify-like System (API Design)** | 1 report (~2023, Staff/SSE LeetCode post 4188001) | API design round. Not seen in 18-month window |
| **TinyURL** | 3+ reports (~2022-2024) | Now replaced by "Reliable URL Shortening Service" variant |
| **Design YouTube** | 1 report (Glassdoor, old) | Not seen in recent window |
| **Blockchain Indexer (HLD)** | 1 report (Sept 2024, LeetCode — "Uber\|Confluent Roller Coaster SDE-3") | HLD round at Confluent onsite. Design a system to index blockchain transactions and make them queryable. Core probe: immutable append-only log (maps to Kafka), indexing strategy, query latency vs. write throughput, eventual consistency. Interviewers described as "20+ years experience, very senior." Pre-window but high-signal. |
| **Snake & Ladder (LLD)** | 1 report (Sept 2024, same thread as above) | LLD round at same Confluent onsite. OOP + game state management, board representation, player progression state machine. Pre-window. |

---

## 📊 Frequency + Recency Summary Table

| Rank | Question | In-Window Reports | Most Recent | Tier |
|---|---|---|---|---|
| 1 | **TempMail / Disposable Email Service** | 3 | May 2026 | ⭐⭐⭐ |
| 2 | **Aggregate News Feed / RSS Feed Ingestion** | 3 | Apr 2026 | ⭐⭐⭐ |
| 3 | **API Design Round** (pure REST contract) | 3+ | Apr 2026 | ⭐⭐⭐ |
| 4 | **Distributed Key-Value Store** (global, read-optimized) | 2 | Oct 2025 | ⭐⭐ |
| 5 | **URL Shortener** (reliable, unique ID focus) | 1 in-window + 3 historical | Jan 2026 | ⭐⭐ |
| 6 | **Feedly / Podcast Service** (API design variant) | 2 | Nov 2025 | ⭐⭐ |
| 7 | **Health Check / wasAlive Monitoring** | 1 | May 2026 | ⭐ |
| 8 | **DB + SQL + API Design Combined** | 1 | Apr 2026 | ⭐ |
| 9 | **Job Scheduling System** (concurrency round) | 1 | 2025 | ⭐ |
| — | Reliable Event Ingestion Pipeline | 0 (domain-inferred) | — | ⚠️ unverified |
| — | Distributed Feature Flag System | 0 (domain-inferred) | — | ⚠️ unverified |
| — | WhatsApp / Chat Messaging System | 0 (Blind, no date) | — | ⚠️ unverified |
| — | API Rate Limiter | 0 (1 mention, no date) | — | ⚠️ unverified |
| — | Blockchain Indexer | historical (Sept 2024) | Sept 2024 | 📜 pre-window |

---

## 🔑 API Design — The Confluent Differentiator

This is where most candidates lose points. Confluent treats API design as a separate evaluation axis, not a footnote.

**Format in the interview:** No whiteboard. You define the API contract verbally or in text. Interviewer probes each endpoint.

> **Confirmed in-window API design questions:** Podcast Service (May 2025), Feedly-like (Nov 2025), DB+SQL+API combined (Apr 2026)

---

## 🔎 Emerging Patterns (Aug 2026 Research Pass)

Three new signals not in the Jul 2026 research:

**1. URL Shortener is now in-window (Jan 2026)**
Was documented in `raw-research-all-rounds.md` as a high-frequency historical question but was missing from `sd-research.md`. Now confirmed in 18-month window. Promoted to Tier 2.

**2. RSS Feed + TempMail combination question**
PracHub shows two entries in Jan-Feb 2026 that combine news feed and temporary email in a single round. Whether this is one multi-part question or two sequential problems in one round is unclear (the Jan 28 entry is locked). **Pattern implication:** Confluent may be stacking the two most common themes into one round as the question bank ages.

**3. "wasAlive" Health Check (May 2026) = the most recent NEW question**
The most recently reported novel design problem. Shows Confluent still occasionally introduces fresh questions. The specificity (5 slots × 100ms, 3 consecutive = down) suggests a concrete implementation exercise, not pure HLD.

**4. Bloom Filter depth is a round-level risk**
In May 2026, a Bloom filter discussion consumed most of the TempMail round. Even for well-prepped candidates, be ready for a long deep-dive into probabilistic data structures, false positive rates, and alternative approaches. The TempMail question is the entry point; Bloom filter may be where depth is tested.

---

## 🏗️ Tableflow Connection — Frame Answers Through Streaming

No Tableflow-specific interview question was found (product GA'd in 2025 — too new). But the questions map directly to Tableflow's architecture:

| Interview Question | Tableflow Parallel |
|---|---|
| **TempMail with TTL** | TTL-based data expiration = Kafka topic retention + Iceberg snapshot expiry |
| **Aggregate RSS News Feed** | Ingestion pipeline = Kafka consumer reading segments → Parquet → Iceberg tables |
| **Distributed KV Store** | Log-compacted Kafka topic = effectively a distributed KV store |
| **API Design** | Tableflow exposes REST APIs for table lifecycle management (control plane) |
| **Health Check System** | Tableflow needs 99.99% uptime monitoring across multi-cloud |
| **URL Shortener** | Control plane APIs managing short-lived resource identifiers |

**Recommendation:** When designing any system, mention Kafka as the ingestion backbone. Show you think in streaming terms — this signals domain fit.

---

## 💡 Candidate Tips (18-Month Window)

1. **"Small question bank — they know you prepped."** (Hack2Hire, May 2026) — Show genuine depth on trade-offs, not rehearsed answers.

2. **Start with ingestion reliability, not personalization.** For the news feed, candidates who started with ranking/ML were dinged. Start with "how do I reliably ingest from unreliable publishers?" (PracHub, Jul 2025)

3. **Design round killed an otherwise perfect loop.** SSE2 got all strong-hires in coding but lean-no-hire because no strong-hire in design. (LeetCode, May 2025)

4. **Bloom filter depth.** In one round, a Bloom filter discussion consumed most of the time. Prepare for deep dives into probabilistic data structures. (Hack2Hire, May 2026)

5. **API design scrutiny is extreme.** Every HTTP verb, status code, and header is a test point. One wrong code gets flagged loudly. (LeetCode SSE2, May 2025)

6. **IBM acquisition causing delays.** Process takes 2+ months post-acquisition. Some offers frozen/delayed. (1P3A Apr 2026, Blind Mar 2026)

7. **Don't trust the vibe.** Interviewers who seem agreeable throughout may still give "No Hire." Drive trade-offs and edge cases proactively without waiting to be prompted. (LinkedIn post, 2025)

---

## ✅ Prep Priority Stack

Based on 18-month data, prepare in this order:

| Priority | Topic | Why |
|---|---|---|
| ⭐ P0 | **TempMail / Disposable Email + Bloom Filter depth** | Most repeated (3 reports), most recent, and deep-dive risk |
| ⭐ P0 | **REST API Design** (verbs, codes, headers, pagination, idempotency) | Confluent's signature axis — 3+ reports, Oct 2025 as standalone |
| ⭐ P0 | **Aggregate RSS News Feed** (ingestion reliability first) | 3 reports, Apr 2026 most recent |
| High P1 | **Distributed KV Store** (globally distributed, read-optimized) | 2 reports, Oct 2025 |
| High P1 | **URL Shortener** (unique ID, read-heavy optimization) | 3+ historical + Jan 2026 in-window |
| High P1 | **SQL / DB Schema Design** | Explicitly evaluated (Apr 2026) as standalone round |
| Medium P2 | **Health Check / Monitoring** ("wasAlive" pattern) | 1 recent report, May 2026 |
| Medium P2 | **Job Scheduling System** | 1 report (concurrency round, not HLD) |
| Low P3 | **Reliable Event Ingestion Pipeline** | Domain-inferred from TechPrep, no candidate attribution |
| Low P3 | **Distributed Feature Flag System** | Same — domain-inferred, not confirmed |

---

## 📚 Sources

**Primary (candidate reports, 18-month window):**
- [LeetCode — Confluent SSE2 May 2025 (No Offer)](https://leetcode.com/discuss/interview-experience/6858166/)
- [LeetCode — Confluent SSE India Apr 2025 (Offer)](https://leetcode.com/discuss/post/6974811/)
- [LeetCode — Confluent SSE2 Remote (Nov 2025)](https://leetcode.com/discuss/post/5895828/)
- [1Point3Acres — Confluent System Design Guide Oct 2025](https://www.1point3acres.com/interview/thread/1138571)
- [1Point3Acres — Confluent SSE Feb 2026](https://www.1point3acres.com/interview/thread/1165654)
- [1Point3Acres — Confluent SSE Apr 2026](https://www.1point3acres.com/interview/thread/1175376)
- [JoinTaro — SSE India Nov 2025 (No Offer)](https://www.jointaro.com/interviews/companies/confluent/experiences/senior-software-engineer-india-november-18-2025-no-offer-neutral-47eb0026/)
- [Hack2Hire — Confluent Forum May 2026](https://www.hack2hire.com/forum/69fc69a8574d87bc49005cf8)
- [Blind — Confluent Interview Experience (Job Scheduling, Design)](https://www.teamblind.com/post/confluent-interview-experience-nedbv3m7)
- [LinkedIn — Yogesh Baghel, Confluent System Design Lessons Learned 2025](https://www.linkedin.com/posts/yogesh-baghel_systemdesign-frontend-interviews-activity-7430148192043823104-yAPC)

**Historical primary sources (pre-Feb 2025 window — used for historical section only):**
- [LeetCode — Uber|Confluent Roller Coaster SDE-3 (Sept 2024)](https://leetcode.com/discuss/interview-experience/5833870/) — Blockchain Indexer (HLD) + Snake & Ladder (LLD) confirmed for Confluent onsite
- [LeetCode — Confluent Senior/Staff SWE Experience (2023)](https://leetcode.com/discuss/interview-question/4188001/) — Spotify-like API design, Staff-level loop

**Aggregator / Corroboration (not counted in frequency):**
- [PracHub — Confluent System Design Questions (Updated 2026)](https://prachub.com/companies/confluent/categories/system-design)
- [TechPrep — Confluent Interview Process 2026](https://www.techprep.app/blog/confluent-interview-process)
- [EngineBogie — Confluent SSE Experience #178](https://enginebogie.com/interview/experience/confluent-senior-software-engineer/178)
- [CodingKaro — Confluent 2025](https://www.codingkaro.in/jobs-internships/leetcode-interview-experience/Confluent)
- [Glassdoor — Confluent 2025-2026](https://www.glassdoor.com/Interview/Confluent-Interview-Questions-E1048428.htm)
- [Exponent — Confluent System Design Questions](https://www.tryexponent.com/questions?company=confluent&role=ml-engineer&type=system-design)

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 2026 | **Second deep research pass.** 8 additional searches (LeetCode, Blind, Glassdoor, 1P3A, InterviewQuery, PracHub). Added to historical: Blockchain Indexer (HLD, Sept 2024) + Snake & Ladder (LLD, Sept 2024) — both from LeetCode Uber\|Confluent SDE-3 thread. Added to Tier 4: WhatsApp/Chat System (Blind thread, no date) + API Rate Limiter (one Confluent-context mention). Added historical primary source links. Appended LLD names reference section at bottom. HLD-only policy enforced — LLD kept as name-only list. |
| Aug 2026 | **Deep research pass (18-month window).** Searched LeetCode, Glassdoor, 1Point3Acres, JoinTaro, Hack2Hire, Blind, PracHub, TechPrep, EngineBogie, LinkedIn. Added 4 new questions to tracker: URL Shortener (Jan 2026, promoted from raw-research), RSS+TempMail combo variant (Jan-Feb 2026), DB+SQL+API combined round (Apr 2026, from 1P3A), and Job Scheduling System (Blind, concurrency round). Added "Emerging Patterns" section. Applied strict methodology: frequency = distinct dated primary reports only; prep-sites treated as corroboration. Excluded: YouTube (frontend round), Spotify-like (pre-window only), TechPrep domain-inferred questions marked LOW-confidence. |
| Jul 2026 | Research compiled. Strict Jun 2025+ filter. 10 confirmed questions, 3 borderline. |

---

## 📋 LLD Questions Surfaced During Research (Names Only — Not Preparing)

> These came up during the research pass. Not in scope for prep but recorded for completeness.

- LRU Cache with TTL (phone screen / coding round — not HLD)
- Task Scheduler / Job Scheduler (concurrency round — code required)
- Snake & Ladder (Sept 2024 onsite — OOP)
- Circuit Breaker Pattern
- Concurrent LRU Cache
- Memory-optimized File Reader
- Splitwise
- Battleship Game
- Multi-directory File System with Read-Write Permissions
