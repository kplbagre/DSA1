# Confluent System Design Round — Research (Jun 2025 – Jul 2026)

> **Scope:** System design round ONLY. Last 12-15 months data only. Older data saved separately in `../raw-research-all-rounds.md`.
>
> **Role:** Senior Software Engineer — Tableflow team (IBM/Confluent)
>
> **Round structure confirmed by HR:** 3 rounds total — 2 DSA + 1 System Design
>
> **Data quality:** 8 strict data points (Jun 2025+), 3 borderline (Apr-May 2025, labeled). Sources: LeetCode, PracHub, 1Point3Acres, JoinTaro, Hack2Hire, Glassdoor, CodingKaro.

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

## ⭐ Confirmed System Design Questions (Jun 2025 – Jul 2026)

### Strict Window (Jun 2025+)

| # | Question | Date | Level | Source |
|---|---|---|---|---|
| 1 | **Design a Disposable/Temporary Email Service** — auto-expiring addresses (10-min inboxes). SMTP ingress, MX records, TTL, storage schema, rate limiting, observability. | Jul 2025 | SWE | PracHub |
| 2 | **Design an Aggregate News Feed** — ingest articles from publishers, deduplicate, rank. Senior: start with ingestion reliability, not personalization. | Jul 2025 | SWE | PracHub |
| 3 | **Design a Globally Distributed, Read-Optimized KV Store** | Jul 2025 | SWE | PracHub |
| 4 | **Distributed Key-Value Store** — focus on distributed architecture | Oct 2025 | SSE | 1Point3Acres |
| 5 | **System Design round** — specific question behind paywall; SSE onsite | Nov 2025 | SSE (India) | JoinTaro |
| 6 | **System Design round** — described as "fair"; combined with LRU Cache variant | Sep 2025 | SWE-2 (Edinburgh) | JoinTaro |
| 7 | **System Design round** — specific question behind paywall | Feb 2026 | SSE | 1Point3Acres |
| 8 | **DB, SQL, and API Design round** — database modeling + API contract design | Apr 2026 | SSE | 1Point3Acres |
| 9 | **"Temp email system"** — Bloom filter discussion consumed most of the round | May 2026 | SDE | Hack2Hire |
| 10 | **"wasAlive" health check system** — 5 slots × 100ms, 3 consecutive inactive = down | May 2026 | SDE | Hack2Hire |

### Borderline (Apr-May 2025 — richest data, process unchanged)

| # | Question | Date | Level | Source |
|---|---|---|---|---|
| 11 | **TempMail service** — generated emails valid for 5 hours | May 2025 | SSE2 | LeetCode |
| 12 | **API Design round** — no architecture diagram; pure REST verbs, codes, headers | May 2025 | SSE2 | LeetCode |
| 13 | **LRU Cache with TTL** + get_average() (design-coding hybrid) | Apr 2025 | SSE (India) | LeetCode |

---

## 📊 Question Frequency Pattern

| Question Theme | Appearances (2025-2026) | Confidence |
|---|---|---|
| **TempMail / Disposable Email with TTL** | 3+ reports | ⭐ Very High — most repeated |
| **Distributed Key-Value Store** | 2+ reports | ⭐ High |
| **Aggregate News Feed / RSS Feed** | 2+ reports | High |
| **API Design (pure contract, no diagrams)** | 2+ reports | ⭐ High — Confluent signature |
| **DB/SQL Modeling** | 1+ reports | Medium |
| **Health Check / Monitoring System** | 1 report | Medium |
| **Bloom Filter deep-dive** | 1 report | Medium — but took entire round |

**Key insight from Hack2Hire (May 2026):** "Confluent has a small question bank." Questions repeat. Mastering the 4-5 core themes above gives very high coverage.

---

## 🔑 API Design — The Confluent Differentiator

This is where most candidates lose points. Confluent treats API design as a separate evaluation axis, not a footnote.

**What they scrutinize:**

| Aspect | What to Get Right |
|---|---|
| **HTTP Verbs** | GET (read), POST (create), PUT (full replace), PATCH (partial update), DELETE |
| **Response Codes** | 200 (OK), 201 (Created), 204 (No Content), 400 (Bad Request), 404 (Not Found), 409 (Conflict), 429 (Rate Limited), 500/503 |
| **Headers** | Content-Type, Accept, Authorization, pagination (Link, X-Total-Count) |
| **URL Structure** | `/api/v1/emails/{id}/messages` — hierarchical, resource-based |
| **Pagination** | Cursor-based (scalable) vs offset-based (simple). Know trade-offs. |
| **Idempotency** | POST with idempotency key for safe retries |
| **Error Format** | Consistent JSON error body: `{ "error": { "code": "...", "message": "..." } }` |
| **Versioning** | URL path (`/v1/`) vs header (`Accept: application/vnd.api.v1+json`) |

**Format in the interview:** No whiteboard. You define the API contract verbally or in text. Interviewer probes each endpoint.

---

## 🏗️ Tableflow Connection — Frame Your Answers Through Streaming

No Tableflow-specific interview question was found (product GA'd in 2025 — too new). But the questions map directly to Tableflow's architecture:

| Interview Question | Tableflow Parallel |
|---|---|
| **TempMail with TTL** | TTL-based data expiration = Kafka topic retention + Iceberg snapshot expiry |
| **Aggregate News Feed** | Ingestion pipeline = Kafka consumer reading segments → Parquet → Iceberg tables |
| **Distributed KV Store** | Log-compacted Kafka topic = effectively a distributed KV store |
| **API Design** | Tableflow exposes REST APIs for table lifecycle management (control plane) |
| **Health Check System** | Tableflow needs 99.99% uptime monitoring across multi-cloud |

**Recommendation:** When designing any system, mention Kafka as the ingestion backbone. Show you think in streaming terms — this signals domain fit.

---

## 💡 Recent Tips from Candidates (2025-2026 only)

1. **"Small question bank cuts both ways — easy to prep but they know you prepped."** (Hack2Hire, May 2026) — Show genuine depth, not rehearsed answers.

2. **Start with ingestion reliability, not personalization** — For the news feed question, candidates who started with ranking/ML were dinged. Start with "how do I reliably ingest from unreliable publishers?" (PracHub, Jul 2025)

3. **Design round killed an otherwise perfect loop** — SSE2 got all strong-hires in coding but lean-no-hire because no strong-hire in design. (LeetCode, May 2025)

4. **Bloom filter depth** — In one round, a Bloom filter discussion consumed most of the time. Prepare for deep dives into probabilistic data structures. (Hack2Hire, May 2026)

5. **IBM acquisition causing delays** — Process takes 2+ months post-acquisition. Some offers were frozen. (1P3A Apr 2026, Blind Mar 2026)

6. **Be patient with the process** — Multiple candidates confirm long timelines. (1P3A, JoinTaro 2025-2026)

---

## ✅ Prep Checklist — What to Study for the System Design Round

Based on recent data, prepare these in priority order:

| Priority | Topic | Why |
|---|---|---|
| ⭐ | **Design TempMail / Disposable Email** | Most repeated question (3+ reports) |
| ⭐ | **REST API Design** (verbs, codes, headers, pagination) | Confluent's signature evaluation axis |
| ⭐ | **Design Distributed KV Store** | 2+ reports, maps to Kafka/Tableflow domain |
| High | **Design Aggregate News Feed** | Tests ingestion pipeline design = Tableflow core |
| High | **SQL/DB Schema Design** | Explicitly evaluated (Apr 2026 report) |
| High | **Bloom Filters** | Deep dive consumed an entire round |
| Medium | **Health Check / Monitoring System** | 1 report, but unique Confluent question |
| Medium | **Kafka internals** | Not directly asked but signals domain fit when referenced |
| Medium | **Trade-off articulation** | Practice defending every decision: "I chose X over Y because..." |

---

## 📚 Sources

- [PracHub — Confluent System Design Questions](https://prachub.com/companies/confluent/categories/system-design)
- [LeetCode — SSE2 May 2025](https://leetcode.com/discuss/interview-experience/6858166/)
- [LeetCode — SSE India Apr 2025](https://leetcode.com/discuss/interview-experience/6974811/)
- [1Point3Acres — Confluent Oct 2025](https://www.1point3acres.com/interview/thread/1138571)
- [1Point3Acres — Confluent Feb 2026](https://www.1point3acres.com/interview/thread/1165654)
- [1Point3Acres — Confluent Apr 2026](https://www.1point3acres.com/interview/thread/1175376)
- [JoinTaro — SSE India Nov 2025](https://www.jointaro.com/interviews/companies/confluent/experiences/senior-software-engineer-india-november-18-2025-no-offer-neutral-47eb0026/)
- [JoinTaro — SWE-2 Edinburgh Sep 2025](https://www.jointaro.com/interviews/companies/confluent/experiences/software-engineer-2-edinburgh-scotland-september-2-2025-accepted-offer-positive-45960adc/)
- [Hack2Hire — Confluent Forum May 2026](https://www.hack2hire.com/forum/69fc69a8574d87bc49005cf8)
- [CodingKaro — Confluent 2025](https://www.codingkaro.in/jobs-internships/leetcode-interview-experience/Confluent)
- [Glassdoor — Confluent 2025-2026](https://www.glassdoor.com/Interview/Confluent-Interview-Questions-E1048428.htm)

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 2026 | Research compiled. Strict Jun 2025+ filter. 10 confirmed questions, 3 borderline. |
