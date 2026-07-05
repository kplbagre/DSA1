# DocuSign R2 — Prep Strategy

> **Context:** Written during DocuSign prep (June 2026). The CATEGORIES and CONCEPTS here are universal — they apply to any senior backend interview. After DocuSign is done, generalise this file and move to `SystemDesignConcepts/`.
>
> **Purpose:** Lock the problem space and concept-to-question mapping BEFORE writing any notes. Avoids writing the wrong things.

---

## The Problem Space (6 confirmed gaps)

From `system-design-questions.md` — questions NOT covered in DOCUSIGN_PREP.md:

| ID | Question | Tier |
|---|---|---|
| C1 | Design a Rate Limiter for a Microservices API | ⭐ Confirmed asked |
| C2 | Expense Report System — Data Model Design | ⭐ Confirmed asked |
| C3 | Pagination API + Data Model Design | ⭐ Confirmed asked |
| D1 | Design a Digital Signature System | 🔶 Likely (DocuSign's core product) |
| D2 | Design a Document Storage & Retrieval Service | 🔶 Likely |
| D3 | Design a Real-Time Notification Service | 🔶 Likely |

---

## 4 Knowledge Categories

Each gap question maps to an underlying knowledge category. Learn the category → answer any question in it.

---

### Category 1 — API Design + Data Modeling
**Questions it unlocks:** C2 (Expense Report), C3 (Pagination API)

**What the interviewer is really testing:**
Can you design a clean API contract AND the schema that backs it? These two always come together at DocuSign — the confirmed format is "choose between API design OR server-side application."

**Concepts to learn:**
- REST API design: HTTP verbs, status codes, request/response contracts, versioning (`/v1/`, headers vs URL)
- Pagination patterns: cursor (opaque token) vs offset (`?page=2&size=20`) — trade-offs, consistency guarantees, performance at scale
- Relational schema design: normalisation (1NF/2NF/3NF), foreign keys, indexing strategy
- Validation rules at DB + application layer: CHECK constraints, NOT NULL, application-level guard rails
- API idempotency: `Idempotency-Key` header, PUT vs POST semantics

**Concept note files:**
- `SystemDesignConcepts/11-api-design.md` ← **GAP, write this**
- `SystemDesignConcepts/12-data-modeling.md` ← **GAP, write this**
- `SystemDesignConcepts/04-idempotency.md` ← planned, write this too

**Resources:**
- hellointerview.com — "API Design" section (free): https://www.hellointerview.com/learn/system-design/core-concepts/api-design
- Arpit Bhayani — API design videos (search "Arpit Bhayani REST API design")
- DocuSign's own engineering blog on pagination: https://www.docusign.com/blog/developers/the-trenches-api-pagination

---

### Category 2 — Distributed Systems + Rate Limiting
**Questions it unlocks:** C1 (Rate Limiter)

**What the interviewer is really testing:**
Algorithm depth on rate limiting strategies + how you handle distributed state. The confirmed DocuSign candidate detail: interviewer pushed beyond basic IP-based limiting to KYC identity, JWT token identification, and a deque-of-timestamps threshold mechanism.

**Concepts to learn:**
- Token bucket algorithm: fixed capacity bucket, tokens refill at rate R, burst allowed
- Sliding window log: store timestamps of requests in a deque, evict old ones, count remaining
- Fixed window counter: simple but has edge-case spikes at window boundaries
- Redis INCR + Lua script atomicity: why you need atomic increment + expire in one operation
- Distributed rate limiting across nodes: central Redis vs local + gossip, race condition risks
- JWT-based client identification: why IP is wrong for microservices (proxies, NAT), use `sub` claim from JWT
- KYC (Know Your Customer) identity layer: rate limit per verified identity, not per IP

**Concept note files:**
- `SystemDesignConcepts/02-rate-limiting.md` ← planned, **write this first** (highest DocuSign ROI)

**Resources:**
- hellointerview.com — Rate Limiter walkthrough (free)
- Arpit Bhayani — "Rate Limiting" (search "Arpit Bhayani rate limiting system design")
- ByteByteGo YouTube — "Rate Limiting Algorithms Explained" (visual, 8 min)

---

### Category 3 — Security + Cryptography Fundamentals
**Questions it unlocks:** D1 (Digital Signature System), partially D2 (Document Storage compliance)

**What the interviewer is really testing:**
Do you understand how DocuSign's own product works under the hood? PKI, signing flow, audit trail, non-repudiation. If they ask D1, shallow answers will get drilled immediately — this is their domain.

**Concepts to learn:**
- PKI (Public Key Infrastructure): what it is, why it exists, certificate authorities
- Asymmetric encryption: public key encrypts / private key decrypts (or vice versa for signing)
- Digital signature flow: hash the document → encrypt hash with signer's private key → verify with public key
- Hash functions: SHA-256, why we hash before signing (not sign the whole doc)
- Non-repudiation: what it means — signer cannot deny having signed
- Certificate chains: how trust propagates from Root CA → Intermediate CA → End Entity
- Audit trail design: append-only log, timestamp + actor + action + doc hash
- Multi-party signing order: sequential (A must sign before B) vs parallel (A and B can sign independently)
- Webhook on signing completion: event-driven notification, retry with exponential backoff

**Concept note files:**
- `SystemDesignConcepts/13-security-pki.md` ← **GAP, write this**

**Resources:**
- ByteByteGo YouTube — "How does HTTPS work? SSL/TLS explained" (closest free visual)
- Arpit Bhayani — JWT deep dive (covers asymmetric signing, same concepts)
- DocuSign developer docs — "Understanding digital signatures": https://www.docusign.com/products/electronic-signature/learn/digital-signature-faq

---

### Category 4 — Async + Event-Driven + Storage
**Questions it unlocks:** D3 (Notification Service), D2 (Document Storage)

**What the interviewer is really testing:**
Can you design reliable async systems? Delivery guarantees, idempotency on consumers, fan-out at scale. D2 adds blob storage and compliance layering on top.

**Concepts to learn:**
- Kafka fan-out: topic → multiple consumer groups, each with independent offset
- Delivery guarantees: at-most-once vs at-least-once vs exactly-once — when to use each
- Idempotency on consumers: deduplication key, idempotency table in DB
- Retry with exponential backoff: base delay × 2^n + jitter, dead letter queue
- Push vs pull notification: WebSocket, SSE, polling trade-offs
- Multi-channel fan-out: email (SES/SendGrid), SMS (Twilio), push (FCM/APNs) — different reliability contracts
- Blob storage (S3-style): object storage vs block storage vs file storage — when to use which
- Metadata DB alongside blob: store S3 key + doc ID + version + owner + tags in Postgres
- Document versioning: immutable object per version vs mutable with version pointer
- Compliance tagging: SOC 2, GDPR data residency — what it means at storage layer

**Concept note files:**
- `SystemDesignConcepts/07-cdc-outbox.md` ← planned, covers dual-write + reliability pattern
- (D3 fan-out covered by Observer pattern notes in `LLD/DesignPatterns/02-observer.md` for in-process; Kafka scale in `07-cdc-outbox.md`)
- (D2 document storage — no dedicated note needed; concepts covered across 02, 07, 13)

**Resources:**
- ByteByteGo YouTube — "Notification System Design" (covers fan-out, push/pull, multi-channel)
- hellointerview.com — "Design a Notification System" walkthrough
- Arpit Bhayani — CDC and Outbox pattern deep dive

---

## Concept → Note File Mapping (complete)

| Concept | File | Status | Priority for DocuSign |
|---|---|---|---|
| Rate Limiting | `SystemDesignConcepts/02-rate-limiting.md` | ❌ Not written | ⭐ Write first |
| API Design | `SystemDesignConcepts/11-api-design.md` | ❌ Not written | ⭐ Write second |
| Data Modeling | `SystemDesignConcepts/12-data-modeling.md` | ❌ Not written | ⭐ Write third |
| Security + PKI | `SystemDesignConcepts/13-security-pki.md` | ❌ Not written | High |
| CDC + Outbox | `SystemDesignConcepts/07-cdc-outbox.md` | ❌ Not written | High |
| Idempotency | `SystemDesignConcepts/04-idempotency.md` | ❌ Not written | High |

---

## All 13 Concept Notes — Full Priority Order

> This is the complete writing order for ALL `SystemDesignConcepts/` notes, prioritised for DocuSign R2. The first 5 are critical — cover these before anything else. The full list ensures no concept comes up in the interview that you haven't seen.

| Priority | # | File | DocuSign relevance | General relevance |
|---|---|---|---|---|
| ⭐ Critical | 1 | `02-rate-limiting.md` | Confirmed asked — JWT + KYC depth expected | Asked at every company |
| ⭐ Critical | 2 | `11-api-design.md` | Confirmed format — API design OR server-side choice | Every design question involves an API |
| ⭐ Critical | 3 | `12-data-modeling.md` | Confirmed asked — expense report schema | Every design question needs a schema |
| ⭐ Critical | 4 | `03-caching.md` | "How do you scale reads?" comes up in every R2 design | Every design question |
| ⭐ Critical | 5 | `04-idempotency.md` | DocuSign PDF explicitly covers billing idempotency key | Payments, retries, Kafka everywhere |
| High | 6 | `13-security-pki.md` | DocuSign's own product — D1 likely asked | Secure API design |
| High | 7 | `07-cdc-outbox.md` | Notification service D3, event-driven reliability | Migration, dual-write, Kafka |
| High | 8 | `06-distributed-locking.md` | Concurrent writes, subscription state machine | Any high-concurrency design |
| Medium | 9 | `01-optimistic-pessimistic-locking.md` | Inventory-style concurrent reservation | Booking systems, e-commerce |
| Medium | 10 | `05-consistent-hashing.md` | Sharding the DB — "how would you scale to 100M users?" | Sharding, distributed caches |
| Lower | 11 | `08-bloom-filter.md` | Deduplication of signed docs | Deduplication at scale |
| Lower | 12 | `09-sharded-counters.md` | High-write analytics counters | Analytics, leaderboards |
| Lower | 13 | `10-backpressure.md` | Stream processing reliability | Kafka consumer lag |

**Status as of June 2026:**
- ✅ `02-rate-limiting.md` — written
- ❌ All others — not yet written

---

## Phase 2 — Solution Walkthroughs (after concept notes)

> **When to do this:** After the concept notes are written and reviewed. Reading about token bucket is Step 1. Walking through "Design a rate limiter for DocuSign's API" end-to-end is Step 2. These are different skills — this phase bridges them.

### Two-layer structure

| Layer | Location | Purpose |
|---|---|---|
| **Universal** | `SystemDesignQuestions/` (top-level, outside Interview/) | Company-agnostic walkthroughs — reusable for any future interview |
| **DocuSign-specific** | `Interview/DocuSign/r2-solutions/` | DocuSign-flavored versions — KYC, JWT, B2B SaaS context baked in |

**How it works:** Write the universal version first (covers the concept end-to-end). The DocuSign version inherits everything from the universal version and adds: DocuSign-specific depth (KYC identity layer, e-signature context, their confirmed interview probes).

### DocuSign solution files (write in this order)

| Priority | File | Question | Concept notes prerequisite |
|---|---|---|---|
| 1 | `r2-solutions/C1-rate-limiter.md` | Design a Rate Limiter for a Microservices API | `02-rate-limiting.md` ✅ done |
| 2 | `r2-solutions/C3-pagination-api.md` | Pagination API + Data Model Design | `11-api-design.md`, `12-data-modeling.md` |
| 3 | `r2-solutions/C2-expense-report.md` | Expense Report System — Data Model Design | `12-data-modeling.md` |
| 4 | `r2-solutions/D1-digital-signature.md` | Design a Digital Signature System | `13-security-pki.md` |
| 5 | `r2-solutions/D3-notification-service.md` | Design a Real-Time Notification Service | `07-cdc-outbox.md`, `04-idempotency.md` |
| 6 | `r2-solutions/D2-document-storage.md` | Design a Document Storage & Retrieval Service | `07-cdc-outbox.md`, `03-caching.md` |

### What each solution file contains

1. **Clarifying questions to ask** — what to nail down in the first 2-3 minutes before drawing anything
2. **Which concepts to reach for** — which concept note(s) power this answer
3. **The 45-minute walkthrough** — section-by-section structure of the verbal answer
4. **Where the interviewer will probe** — expected follow-up questions + your prepared answers
5. **The one-sentence opener** — how to start confidently when the question is shown

---

## Interview Format Insight (confirmed, June 2024 candidate — LinkedIn)

> Candidates are sent **2 questions beforehand** to choose from:
> - Option 1: API design question
> - Option 2: Traditional server-side application question
>
> You pick one. The interviewer then deep-dives on the part that is **core to their team's domain**.
>
> **Implication:** Prepare both Category 1 (API Design) AND Category 2 (Rate Limiting / distributed systems). You don't know which option you'll feel more confident with until you see both.

---

## July 2026 Research Validation — All 8 Questions Cross-Checked

> **Research basis:** 20 web searches + 8 page fetches (Jul 4, 2026) across Glassdoor, Blind, 1Point3Acres, LeetCode Discuss, Exponent, DesignGurus, InterviewQuery.
> **Verdict: Zero questions dropped. All 8 confirmed or still plausible.**

| Saved question | Confirmed again? | New evidence found |
|---|---|---|
| A1 — URL Shortener | ✅ | DocuSign PDF + multiple aggregators |
| A2 — Facebook Chat / Messenger | ✅ ⭐ **Strongest signal** | Blind P4 candidate (NetApp→DocuSign, 2025): *"design a Facebook messenger type App"* — confirmed the exact HLD approach interviewer used |
| C1 — Rate Limiter (JWT + KYC) | ✅ ⭐ | Exponent candidate report — same deep probe on KYC + JWT reported again |
| C2 — Expense Report Data Model | ✅ ⭐ | InterviewQuery confirmed again |
| C3 — Pagination API + Data Model | ✅ ⭐ | 1Point3Acres Dec 2025 confirmed again |
| D1 — Digital Signature | ✅ 🔶 | Aggregators only — no new candidate report |
| D2 — Document Storage | ✅ 🔶 | Aggregators only |
| D3 — Notification Service | ✅ 🔶 | Aggregators only |

**One new question surfaced:** Design a Calendly-like scheduling app — single unverified Glassdoor snippet, possibly Bengaluru-specific, contradicted by DocuSign's own prep PDF. Not a prep priority.

**Confirmed HLD interviewer approach** (Blind P4 candidate, 2025):
```
1. Functional requirements
2. Non-functional requirements
3. Tech estimation (back-of-envelope)
4. Database schema
5. API design
6. High-level architecture (boxes + arrows)
```

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created during DocuSign R2 prep. Based on 16 searches + PDF analysis. To be generalised post-interview. |
| June 2026 | Phase 2 added — solution walkthroughs. Two-layer structure: universal (`SystemDesignQuestions/`) + DocuSign-specific (`r2-solutions/`). `r2-solutions/` folder created with INDEX.md and 6 planned files. |
| July 2026 | Research validation pass added. All 8 questions cross-checked against 20 searches. Zero dropped. Calendly question surfaced but low confidence. HLD interviewer approach step sequence confirmed. |
