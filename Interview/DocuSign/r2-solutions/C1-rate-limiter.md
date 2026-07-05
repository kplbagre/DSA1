# C1 — Design a Rate Limiter for a Microservices API

> **Read `solution-notes-standards.md` first.** This file is a 60-minute interview answer framework, not a concept reference. Concept-level depth lives in `SystemDesignConcepts/`.

---

## 🎯 What Is This System?

**In plain English:** A rate limiter counts how many requests a client makes in a sliding time window and returns HTTP 429 (Too Many Requests) once they exceed their quota — protecting your backend services from abuse, DDoS attacks, runaway automation, and accidental infinite retry loops.

**Real-world examples:**

| System / Company | What they built |
|---|---|
| **GitHub API** | 5,000 requests/hour per authenticated token; returns `X-RateLimit-*` headers |
| **Stripe API** | 100 requests/second per key; burst allowed, sustained excess rejected |
| **Twitter/X API v2** | 500K tweets/month per app — monthly cap with daily sub-limits |
| **Cloudflare Rate Limiting** | Edge-level limiting — rules fire before traffic reaches your origin |
| **AWS API Gateway** | Per-stage throttling: burst limit + steady-state requests/second |
| **OpenAI API** | Tokens/minute + requests/minute limits per pricing tier |

**Core user journey:** An API client makes its 501st request in 60 seconds (limit: 500/min) → the rate limiter intercepts before the request reaches the backend → returns `HTTP 429 Too Many Requests` with `Retry-After: 23` → the client backs off and retries in 23 seconds.

**Why it's hard to build at scale:** In a distributed system with 50 API servers, a per-server counter lets a client bypass the limit by round-robining requests across servers — you need a shared, atomic counter in a low-latency store (Redis) that absorbs millions of increments per second without itself becoming a bottleneck.

---

## 🧠 How to Use This File

**This file is an instantiation of DELIVERY-RECIPE** (`Interview/DocuSign/DELIVERY-RECIPE.md`). Every section below maps to one step of the 6-step interview delivery framework. The framework is backed by cognitive psychology — under stress, your working memory shrinks 40–50%, so you need ONE rhythm you can execute automatically.

**Before your interview:**
1. Read DELIVERY-RECIPE.md once to understand the psychology (30 min)
2. Skim the 6 **Memory Anchors** below (2 min)
3. Read this entire file and the 3 **Common Mistakes** (Section 13) so you know what to avoid (20 min)
4. During the interview, follow the 6-step rhythm: Ask → Clarify → Requirements → Estimate → HLD → Deep Dives → Trade-offs → Dimensions → Probes

**The time budget:**
- Minutes 0–5: Sections 1–2 (Opener + Clarifying questions)
- Minutes 5–10: Sections 3–4 (Requirements + Scale estimation)
- Minutes 10–25: Sections 5–6 (Requirements variation + HLD + Data flow)
- Minutes 25–40: Section 7 (Deep dives: 2–3 riskiest components)
- Minutes 40–48: Section 10 (Trade-offs: exactly 3, with failure modes)
- Minutes 48–52: Section 11 (DocuSign dimensions — map explicitly)
- Minutes 52–60: Section 12 (Interviewer probes — prepared Tier 1/2/3 answers)

**Stay on this schedule.** If you're at minute 45 and still deep-diving, pause and move to trade-offs — the rubric values trade-off thinking over technical depth.

---

## 💾 Memory Anchors (Memorize These 6)

Before every interview, say these 6 sentences to yourself (takes 30 seconds):

1. **"Ask before you design."** — Don't assume. Use Section 2 to ask clarifying questions and confirm scope.
2. **"Name the nouns."** — Entities are your mental hooks. When stressed, you can remember categories even if you forget details.
3. **"Define the boundary."** — The API/interface is the contract. Lock it down before you argue about implementation.
4. **"Trace a request."** — Section 6's data flow narrative shows you understand movement through the system, not just boxes.
5. **"Draw the boxes."** — ASCII HLD is your mental model made visible. The interviewer can probe specific boxes without restarting.
6. **"Dig where it's risky."** — Section 7: pick 2–3 *riskiest* components (where the system breaks, where scale hits hardest), not the most *interesting* ones.

**Bonus anchors (if you have memory space):**
- "Everything is a trade-off." → Section 10
- "Why, not what." → Explain reasoning, not just technology
- "Conversational, not presentation." → Think aloud; don't recite

---

## Section 0 — Question Identity Card

| | |
|---|---|
| **Question** | Design a Rate Limiter for a Microservices API |
| **Interview Type** | Type A — System Design |
| **Confirmed or Likely** | ⭐ Confirmed asked (Exponent interview report: 1 verified answer. Interviewer pushed beyond IP-based limiting into KYC practices + JWT token identification.) |
| **Concept notes prerequisite** | `02-rate-limiting.md` (token bucket, sliding window, leaky bucket), `02-rate-limiting_advanced.md` (multi-dimensional limiting, adaptive rate limiting, distributed coordination) |
| **DocuSign-specific angle** | Rate limiting is critical for protecting APIs at scale. DocuSign's focus: How do you implement rate limiting *fairly* across multi-tenant customers with different SLAs? How do you detect and block abuse (bots, brute-force attacks) without blocking legitimate traffic? The DocuSign move: explicitly name which of the 7 evaluation dimensions your design addresses. |
| **Time budget** | 5 min clarify → 5 min estimate → 15 min HLD → 15 min deep dive → 8 min trade-offs → 7 min Q&A buffer |

---

## Section 1 — 🚀 The One-Sentence Opener

> "Before I draw anything, let me ask a few clarifying questions — specifically about what we're rate limiting (users, IPs, API keys), whether this is single-server or distributed, and what the enforcement strategy is (strict or approximate), because those drive the architecture."

Then immediately go to Section 2. Do NOT start drawing.

---

## Section 2 — 🔍 Clarifying Questions Script (Minutes 0–5)

**What to do:** Ask 4–6 questions that clarify scope. Don't assume. The interviewer is watching how you *think*, not how fast you talk.

**Say this out loud (after your opener):**
> "I have a few clarifying questions so I make sure I'm building the right thing..."

---

**Q: "Are we rate limiting by user, by IP address, by API key, or some combination? And is this single-tenant or multi-tenant?"**
- Why ask: different identification methods drive different architectures. User-based requires auth; IP-based is simpler but fails for proxies/NATs; API-key-based is per-service; multi-tenant requires per-tenant limits.
- Single-tenant, IP-based → simpler, local counters OK
- Multi-tenant, API-key-based → distributed system needed, per-key quotas, tenant isolation critical

---

**Q: "What's the rate limit — requests per second, per minute, per hour? And is it a hard limit (reject) or soft (queue/throttle)?"**
- Why ask: determines algorithm choice. Token bucket handles variable rates; sliding window is precise but expensive; leaky bucket smooths spikes.
- Hard limit (reject) → token bucket, fast decision
- Soft limit (queue) → queue with backpressure, more complex

---

**Q: "Is this single-server (one machine) or distributed (multiple servers)? If distributed, do we need strong consistency or eventual consistency?"**
- Why ask: single-server allows in-memory counters; distributed requires Redis or equivalent.
- Single-server → token bucket in memory
- Distributed, strong consistency → Redis with Lua scripts (atomic)
- Distributed, eventual consistency → local counters + async sync (faster, but allow temporary overages)

---

**Q: "What should happen when a request hits the limit — do we return 429 Too Many Requests immediately, or do we queue/buffer the request?"**
- Why ask: rejection is simpler; queueing requires a message queue and worker pool.
- Reject → simple, immediate feedback to client
- Queue → more complex, but fairness to all requests

---

**Q: "Do we need to differentiate between users (e.g., premium users get 1000 req/min, free users get 10 req/min)?"**
- Why ask: tiered quotas require per-user configuration and storage.
- Flat limit → single threshold
- Tiered → lookup user tier, apply corresponding limit

---

**Assumed answers (state these at the start of Section 3):**
- Type A focus — infrastructure + scale
- Rate limit by API key (identifies service client)
- 1000 requests/min per API key (average), with burst allowance (100 req/sec peak)
- Hard limit (reject with 429)
- Distributed system (multiple API servers, shared state via Redis)
- Strong consistency on rate limit enforcement
- Tiered quotas: premium (10K req/min), standard (1K req/min), free (100 req/min)

---

## Section 3 — 📋 Requirements

**Functional Requirements (what the system does):**
- Rate limiter accepts a request with an API key and returns allow/reject decision
- Different API keys have different rate limits (tiered: premium, standard, free)
- Rate limit is per-minute window (1000 req/min standard)
- Burst allowance: allow temporary spikes (100 req/sec) as long as 1-min window average stays under 1000 req/min
- Rejected requests return HTTP 429 Too Many Requests with Retry-After header

**Out of scope (say these explicitly):**
- Request queuing / buffering (requests are rejected, not queued)
- IP-based rate limiting (only API-key-based)
- Whitelist / blacklist per request pattern
- Adaptive rate limiting (no ML-based anomaly detection)
- Analytics / dashboarding (raw limiting decisions only)

**Non-Functional Requirements:**
- Scale: 100K API keys, 1B requests/day = ~11.6K requests/sec (average), ~35K requests/sec (peak 3×)
- Latency: rate limit decision < 1ms (P99), must not add bottleneck to request path
- Availability: 99.99% — if rate limiter is down, requests should not be blocked
- Consistency: strong (no overages should be permitted; every request counts)
- Fairness: all API keys should get their full quota, not starved by other keys

---

## Section 3.5 — 🗂️ Core Entities (~2 minutes)

> **Say this out loud:** "Before I sketch the architecture, let me name the key data objects the system manages."

> **Note:** A rate limiter is mostly middleware, not a data-heavy domain — the key "entities" are the config rules and ephemeral counters, not persisted business objects.

| Entity | What it represents | Storage |
|---|---|---|
| **APIClient** | The caller being rate limited — identified by API key, IP, or user ID | PostgreSQL (config) |
| **RateLimitPolicy** | The rule applied to a tier — e.g., "standard = 1,000 req/min, premium = 10,000 req/min" | PostgreSQL / Redis config |
| **RateLimitCounter** | Ephemeral rolling count of requests for a given key in the current window | Redis (TTL-based, never persisted) |
| **ViolationLog** | Optional: record of rate limit breaches for abuse detection and billing disputes | PostgreSQL / append-only log |

**Key relationships:**
- An `APIClient` has one `RateLimitPolicy` (tier assignment)
- A `RateLimitCounter` is scoped to `(api_key, window_timestamp)` — dies when TTL expires
- `ViolationLog` is write-only (append); never updated, only queried for analytics

---

## Section 4 — 🔢 Scale Estimation (Minutes 5–10)

**What to do:** Do envelope math out loud. These numbers justify every architecture choice you make in Section 6+. The interviewer wants to see your *thinking*, not just your conclusion.

**Say this out loud (as you write the math on the whiteboard):**
> "Let me do some envelope math to justify the architecture. Starting with traffic..."

---

**Traffic:**
- DAU (estimated): 100K API keys
- Requests/day: 1B
- Requests/sec (avg): 1B ÷ 86,400 = **~11.6K requests/sec**
- Requests/sec (peak 3×): **~35K requests/sec**
- Requests per API key (avg): 1B ÷ 100K = 10K requests/key/day
- Requests per API key (in 1-min window): 10K ÷ 1,440 = **~7 requests/min per key (average)**
- Peak for single key: 7 × 3 = ~20 requests/min (typical API key, bursty)

**Storage:**
- Per API key counter: ~100 bytes (key name + quota tier + current window count + timestamps)
- 100K keys × 100 bytes = **10 MB in memory**
- Redis + replication: ~20 MB total (acceptable)

**Rate limit decision latency:**
- In-memory Redis lookup + increment: <1ms (acceptable for critical path)
- If latency > 5ms, becomes request bottleneck

**Key conclusions:**
- "At 35K requests/sec peak and 100K keys, we can't use a single counter per request (11K-35K increments/sec). Redis INCR can handle ~500K ops/sec, so we're fine, but we need to avoid lock contention."
- "At 10 MB memory footprint, keeping all counters in Redis is feasible."
- "Rate limit decisions must be < 1ms; latency is critical. We can't afford DB queries or synchronous network calls."

---

## Section 5 — 🔄 Requirements Variation Table ⭐ KEY SECTION

| If the interviewer says... | Your architecture changes to... | The reasoning |
|---|---|---|
| "Per-second limits (100 req/sec) instead of per-minute" | Use token bucket with 100 tokens/sec refresh rate; allow burst up to 200 tokens (2-second burst). Smaller granularity, more responsive. | Minute-level windows are too coarse for real-time APIs. Second-level granularity catches traffic spikes immediately. |
| "10M API keys instead of 100K" | Shard Redis cluster by key hash (consistent hashing); each shard holds ~10% of keys. Route rate limit decision to shard based on hash(api_key). | 10M keys × 100 bytes = 1 GB storage. Single Redis instance can hold it, but 350K requests/sec to a single Redis bottlenecks. Sharding distributes load. |
| "IP-based rate limiting, not API-key-based" | Per-IP counters; IP extracted from request headers or socket. Whitelist trusted proxies. Add logic to detect NAT ranges. | IP is simpler (no auth required) but fails when multiple users behind same proxy. Need proxy detection to avoid false collisions. |
| "Eventual consistency OK (allow overages temporarily)" | Local in-memory counters per server; async sync to Redis every 10 seconds. Requests don't hit Redis on every call. | Strong consistency requires synchronous writes to Redis (latency cost). Eventual consistency allows local counters + periodic aggregation. Overages are temporary (until next sync). |
| "Whitelist / blacklist per pattern" | Add pattern matcher: if request matches pattern X, bypass rate limiter. Store patterns in Redis with fast lookup (bloom filter — a probabilistic O(1) data structure that answers "is this item in the set?"; has a small false positive rate but zero false negatives — perfect for whitelisting trusted IPs; or trie — a tree where each node is one character, enabling O(prefix-length) URL pattern matching). | Whitelisting specific clients (by IP, user agent, API endpoint) prevents false blocks. Bloom filter gives O(1) lookup without false negatives. |
| "Stricter limits on free tier (10 req/min instead of 100)" | Fetch user tier from cache/DB; apply corresponding limit. Add penalty: if user exceeds free tier limit, throttle for 1 hour. | Tiering encourages paid upgrades. Penalties deter abuse. Storage cost for tier metadata is negligible. |

---

## Section 8 — 🌐 API Design (Before HLD)

> **Why here:** Define the external contract before drawing the architecture — the HLD shows how these endpoints are implemented. For Type A, this is concise (3–5 minutes); the architecture is the primary deliverable.

### 🧠 How to Derive These Endpoints

Rate limiting is a special case: the primary "API contract" is **not the management endpoints** — it's the `429 Too Many Requests` response that the rate limiter injects into every API response. The management endpoints are just the control plane surface.

The two things callers need:
1. "Am I currently rate-limited?" — `POST /v1/rate-limit/check` (or equivalently, make a real API call and see if it returns 429)
2. "What is my current quota status?" — `GET /v1/rate-limit/status/{api_key}`

But the more important design contract is the **429 response and its headers**. When the rate limiter rejects a request, the response must include:
- `Retry-After: 42` — seconds until the current window resets; client uses this to schedule a retry
- `X-RateLimit-Limit: 1000` — total allowed per window
- `X-RateLimit-Remaining: 0` — remaining in current window
- `X-RateLimit-Reset: 1720090800` — Unix timestamp when the window resets

This is what good clients use to implement backoff. Without `Retry-After`, clients retry immediately, amplifying the load spike that caused the rate limit in the first place.

### Core Endpoints

| Method | Path | Auth | Request | Response | Status Codes |
|---|---|---|---|---|---|
| POST | `/v1/rate-limit/check` | API Key header | `{ "api_key": "..." }` | `{ "allowed": true/false, "remaining": 50, "reset_at": 1234567890 }` | 200, 429 |
| GET | `/v1/rate-limit/status/{api_key}` | API Key header | — | `{ "tier": "standard", "limit": 1000, "remaining": 523, "reset_at": ... }` | 200, 404, 401 |

**429 Response Contract (the real API surface):**

```
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
Retry-After: 42
X-RateLimit-Limit: 1000
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1720090800

{
  "error": "RATE_LIMIT_EXCEEDED",
  "message": "You have exceeded your quota of 1000 requests per minute.",
  "retry_after_seconds": 42
}
```

### 🔍 Endpoint Stories

**`POST /v1/rate-limit/check`** is the middleware check — typically called inline by an API Gateway or a middleware wrapper before the actual request is processed. The response body has `allowed: true/false`. Every call to this endpoint increments the counter (it IS the rate limit check, not a simulation). Idempotency has no meaning here: every call is a separate request that should be counted. The 429 response from this endpoint carries the same headers as the 429 injected by the gateway.

**`GET /v1/rate-limit/status/{api_key}`** is the observability endpoint — for callers to check their quota before making a burst of calls. It does NOT increment the counter. Think of it as "how many tokens do I have in my bucket right now?" The `remaining` and `reset_at` fields let a client decide "I have 50 left and the window resets in 42 seconds — I can send 50 requests now or wait."

**The 429 response headers are the real design deliverable** for this question. Most candidates say "return 429 if rate limited" and stop. The interviewer wants to see `Retry-After` (RFC 7231 standard header), `X-RateLimit-Remaining`, and `X-RateLimit-Reset`. Without `Retry-After`, a misbehaving client retries immediately after receiving 429 — which sends another rejected request, adding load with no benefit. Good clients that parse `Retry-After` back off automatically and don't pile on.

---

## Section 6 — 🏗️ High-Level Architecture (Minutes 10–25)

**What to do:** Draw the boxes (ASCII or whiteboard). Walk through the data flow *as if telling a story*. The interviewer is checking: "Does this person understand flow or just know boxes?"

**Say this out loud (as you start drawing):**
> "Let me draw a high-level architecture. This is how the system looks from 10,000 feet..."

---

### 🎨 Stage 1 — Solve It for One Server (Minute 10)

**Say this out loud:** "Let me start with the simplest thing that works — one server, in-memory counter — then we'll break it and fix it."

```
── Stage 1: Single Server ────────────────────────────────────────────

 ┌──────────────┐       ┌────────────────────────────────────────┐
 │    Client    │──────▶│  API Server                            │
 └──────────────┘       │  in-memory: Map<api_key, WindowCounter>│
                        │  check + increment → ALLOW / 429       │
                        └────────────────────────────────────────┘

KEY INVARIANT (Stage 1):
   Works perfectly — for exactly ONE server.
   Counter is local: fast, zero network, zero dependencies.
```

**WHERE to enforce — decision (introduce this before picking API Gateway):**

| Option | Strength | Weakness | Verdict |
|--------|----------|----------|---------|
| In each service (in-process) | Zero network hop | Counter split across replicas | ❌ Split state |
| API Gateway (centralized entry) | All traffic passes through one place | GW must route to stateless RL | ✅ Chosen layer |
| Sidecar per pod | Language-agnostic, independent deploy | Extra hop per request, complex mesh | ⚠️ Good for service mesh setups |

> 📖 Full placement tradeoffs: `SystemDesignConcepts/Core-Architecture/Service-Communication/17-load-balancing-algorithms.md`

**Why Stage 1 breaks:** 50 API servers = 50 separate in-memory token buckets. Each server enforces its own 100 req/sec limit independently. A client hitting all 50 servers round-robin can send 50 × 100 = 5,000 req/sec completely unthrottled — rate limiting fails entirely. Observable: a malicious tenant floods at 50× quota; no 429 responses are issued because no single server sees the global rate. We need **shared state**.

---

### 🎨 Stage 2 — Pull State into Shared Redis (Minutes 12–17)

**Say this out loud:** "We need all servers to read from the same counter. Pull state out into Redis — now every server checks the same number."

```
── Stage 2: Shared Redis ─────────────────────────────────────────────

 ┌──────────────┐    ┌─────────────────────┐    ┌────────────────┐
 │    Client    │───▶│   API Gateway / LB   │───▶│  Rate Limiter  │
 └──────────────┘    └─────────────────────┘    │    Service     │
                                                 └───────┬────────┘
                                                         │
                                              ┌──────────▼──────────┐
                                              │    Redis (single)    │
                                              │  key: rate_limit:{api_key}  │
                                              │       :{window}     │
                                              │  count: 523  TTL:37s│
                                              └─────────────────────┘
```

**WHAT to store counters in — decision (before saying "Redis"):**

| Option | Strength | Weakness | Verdict |
|--------|----------|----------|---------|
| In-memory (per-server) | Sub-microsecond | Not shared; counter splits | ❌ Already ruled out |
| PostgreSQL | ACID, familiar | 5–10 ms/write + lock contention at high QPS | ❌ Too slow |
| Redis | Sub-ms, atomic INCR, TTL native | Single node = SPOF (fixed in Stage 3) | ✅ Best fit |
| Cassandra | Write-optimized, multi-region | No atomic INCR; LWTs (lightweight transactions — Cassandra's compare-and-set) are 10× slower | ❌ Wrong data model |

> 📖 Full: `SystemDesignConcepts/Core-Architecture/Database-Core/06-databases-types-and-selection.md`

**WHICH algorithm — decision (before implementing the counter):**

| Algorithm | Accuracy | Memory | Burst Handling | Verdict |
|-----------|----------|--------|----------------|---------|
| Fixed Window | ⚠️ Boundary spike (2× burst at window edge) | O(1) | Allows spike | ❌ Accuracy gap |
| Sliding Window Log | ✅ Exact per-request | O(N requests) | Smooth, no burst | ❌ Memory blows up at scale |
| Sliding Window Counter | ✅ ~99% accurate (weighted avg of two windows) | O(1) | Smooth, no burst control | ⚠️ No native burst concept |
| Token Bucket | ✅ Smooth; controlled burst via token accumulation | O(1) | ✅ Native burst support | ✅ Best fit — requirement says 100 req/sec burst |

> Our requirement explicitly says "burst allowance (100 req/sec peak)." Token Bucket is the only algorithm with a native burst concept (tokens accumulate during quiet periods, drain during bursts). Sliding Window Counter fixes boundary accuracy but has no burst control.
>
> 📖 Algorithm mechanics and edge cases: `SystemDesignConcepts/Foundations/Performance-and-Scale/02-rate-limiting.md`

**Why Stage 2 breaks:** Single Redis node = bottleneck + single point of failure. At 100K req/sec, single-threaded Redis CPU hits 100% — Lua script execution blocks the event loop and P99 latency exceeds 100ms, adding overhead to every API call. At 1M req/sec, the node cannot sustain writes at all. Also: if Redis is down, all requests fail (or all pass — neither is correct). Observable: Redis CPU alarm fires; rate limiter P99 spikes; upstream API P99 climbs proportionally. Need clustering and atomicity.

---

### 🎨 Stage 3 — Scale to Production (Minutes 17–25)

**Say this out loud:** "For production, Redis becomes a cluster sharded by api_key hash. And we need atomicity — check-and-increment must be one atomic operation, not two separate commands."

```
── Stage 3: Production ────────────────────────────────────────────────

 ┌──────────────────────────┐
 │       Client Request     │  Authorization: Bearer {api_key}
 └────────────┬─────────────┘
              │
 ┌────────────▼─────────────┐
 │     API Gateway / LB     │  extracts api_key from header
 └────────────┬─────────────┘
              │
 ┌────────────▼────────────────────────────┐   ┌──────────────────────────────────────────┐
 │         Rate Limiter Service            │──▶│           Redis Cluster                   │
 │  1. Extract api_key                     │◀──│  ┌──────────┐ ┌──────────┐ ┌──────────┐ │
 │  2. hash(api_key) % N → shard routing  │   │  │  Shard 0  │ │  Shard 1  │ │  Shard N  │ │
 │  3. Lua: check + INCR (atomic)         │   │  │count: 523 │ │count: 999 │ │count:  12 │ │
 │  4. Return ALLOW / REJECT              │   │  │TTL:    37s│ │TTL:    37s│ │TTL:    37s│ │
 └──────────┬──────────────────┬──────────┘   │  └──────────┘ └──────────┘ └──────────┘ │
            │                  │              └──────────────────────────────────────────┘
     count ≤ limit        count > limit         key: rate_limit:{api_key}:{window_minute}
            │                  │
 ┌──────────▼──────────┐  ┌────▼──────────────────┐
 │     Backend API     │  │   HTTP 429 Response    │
 │  (process request)  │  │  Retry-After: {secs}   │
 └─────────────────────┘  └────────────────────────┘

── Lua Script: Why Atomicity Matters ────────────────────────────────

  ❌ WRONG — Two separate ops (race condition):
  ┌──────────────────────────────────────────────────────────┐
  │ Request A: GET counter → 999                             │
  │ Request B: GET counter → 999  ← both see under-limit     │
  │ Request A: INCR → 1000 (PASS)                            │
  │ Request B: INCR → 1001 (PASS) ← BUG: overage leaked     │
  └──────────────────────────────────────────────────────────┘

  ✅ CORRECT — Lua script (single atomic op):
  ┌──────────────────────────────────────────────┐
  │ GET counter → 999                             │
  │ check: 999 < 1000  →  ALLOW                  │  ← all three steps
  │ INCR counter → 1000                          │     are one atomic
  │ EXPIRE key 60                                 │     Redis operation
  │ return 1  (allow)                            │
  └──────────────────────────────────────────────┘

KEY INVARIANT:
   Rate Limiter is STATELESS — any instance can serve any api_key.
   All shared state (counters, tier config) lives in Redis Cluster.
   Lua script makes check + increment ONE atomic operation:
   no thread, process, or server can interleave between the read and the write.
   TTL on every counter key = window duration + 60s →
   expired windows self-delete; no cleanup job needed.
```

**Data flow (say out loud as you walk the diagram):**

1. **Request arrives** with `Authorization: Bearer {api_key}`.
2. **Rate Limiter extracts** api_key + current timestamp → determines window bucket.
3. **Fetch quota:** Redis lookup `quota:{api_key}` → tier (e.g., 1000 req/min).
4. **Shard routing:** `hash(api_key) % N` → routes to correct Redis shard.
5. **Atomic Lua:** check counter, increment if under limit — single round-trip.
6. **Decision:** ≤ limit → ALLOW to backend. > limit → 429 + `Retry-After` header.
7. **TTL self-cleanup:** Redis key auto-expires; no separate cleanup job needed.

---

## Section 7 — 🔬 Core Component Deep Dives (Minutes 25–40)

**What to do:** Pick 2–3 *riskiest* components. "Riskiest" = where the system most likely fails, where scale hits hardest, or what's unique to this problem.

**Why not 5 deep dives?** Under stress, your working memory shrinks 40–50%. If you try to hold 5 things, you'll confuse them. Pick the hardest 2–3 and go deep.

**Why these 3 for rate limiting?**
1. **Token bucket vs sliding window — algorithm choice** — Wrong choice = either inaccurate (token bucket underestimates overages) or slow (sliding window has O(N) cost per request).
2. **Distributed coordination — Redis Lua scripts** — Wrong approach = race conditions (check and increment not atomic) = overages leak through.
3. **Failure mode — what if Redis is down?** — Wrong strategy = all requests fail, or all requests pass = either broken availability or broken fairness.

**Say this out loud:**
> "Let me go deep on the three riskiest components — the ones where the system most likely breaks at scale..."

---

### Deep Dive 1: Token Bucket vs Sliding Window Algorithm

**Why this is the most critical component:**
The rate limiting algorithm determines accuracy and latency. Token bucket is fast (O(1)) but can overestimate capacity. Sliding window is accurate but slow (O(N) to scan window). Wrong choice = either SLA violation (latency) or fairness violation (overages).

**Algorithm options:**

| Option | Pros | Cons |
|---|---|---|
| **Token Bucket** | O(1) per request, fast. Handles bursts naturally (accumulate tokens). Simple to implement. | Can overestimate capacity (burst allowance may exceed true limit in practice). Not perfectly accurate for strict limits. |
| **Sliding Window (log-based)** | Perfectly accurate. Every request is counted exactly. No overages possible. | O(N) complexity per request (scan all requests in window). For 100K requests/min, that's 1,666 requests to scan per new request. Latency disaster. |
| **Fixed Window (counter)** | O(1) per request, fast. Simple counter per time bucket. | Edge case: requests at window boundaries can burst (e.g., 1000 at 11:59, 1000 at 12:00 = 2000 in 2 seconds). Unfair to users at edges. |
| **Token Bucket + Sliding Log (hybrid)** | Combines accuracy and speed. Token bucket for burst detection; sliding log for fairness check. | Added complexity; more code to maintain and debug. |

**Decision: Token Bucket with burst allowance**
Because at 35K requests/sec, O(N) sliding window is infeasible. Token bucket is O(1) and acceptable for most use cases. The burst allowance is intentional (allow 100 req/sec for 2 seconds = 200 token burst) to smooth traffic spikes without being unfair.

**Token bucket pseudocode:**

```java
class TokenBucket {
    private final int capacity;        // max tokens (burst size)
    private final int refillRate;      // tokens per second
    private double tokens;
    private long lastRefillTime;
    
    public synchronized boolean allowRequest() {
        // Refill tokens based on elapsed time
        long now = System.currentTimeMillis();
        long elapsedSeconds = (now - lastRefillTime) / 1000;
        tokens = Math.min(capacity, tokens + refillRate * elapsedSeconds);
        lastRefillTime = now;
        
        // Check if we have at least 1 token
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;  // allow
        }
        return false;  // reject
    }
}

// Example: 1000 requests/min, burst of 100 req/sec (6-7 second burst)
TokenBucket bucket = new TokenBucket(
    capacity = 600,        // 100 req/sec × 6 sec burst window
    refillRate = 16.67     // 1000 req/min ÷ 60 sec
);
```

**In an interview, if asked:** "Token bucket is O(1) and handles bursts naturally by accumulating tokens during quiet periods. The burst allowance (capacity) is tunable — larger capacity = more bursty traffic allowed. Sliding window is more accurate but O(N) latency kills it at scale."

---

### Deep Dive 2: Distributed Rate Limiting — Redis + Lua Scripts

**Why this is the riskiest component:**
At 35K requests/sec, we can't afford a single bottleneck. Redis is fast (500K ops/sec), but we need to ensure strong consistency. A naive approach (client checks, then increments) has a race condition: two concurrent requests both see capacity, both increment, both pass = overage.

**Strong consistency challenge:**

```
WITHOUT Lua script (WRONG):
Request A: GET counter → 999 (under limit)
Request B: GET counter → 999 (under limit)
Request A: INCR counter → 1000 (pass)
Request B: INCR counter → 1001 (PASS — BUG! Should reject)
```

**Solution: Lua script (atomic):**

```lua
-- In Redis, executed atomically
local key = KEYS[1]
local limit = tonumber(ARGV[1])
local count = redis.call('GET', key)

count = count and tonumber(count) or 0

if count < limit then
    redis.call('INCR', key)
    redis.call('EXPIRE', key, 60)  -- 1-min window + 60s TTL
    return 1  -- allow
else
    return 0  -- reject
end
```

**Redis cluster sharding:**

```java
// Hash the API key to determine Redis shard
int shardIdx = hash(api_key) % numShards;
RedisClient redis = shards[shardIdx];

String decision = redis.eval(luaScript, keys, args);
return decision.equals("1");  // allow if 1, reject if 0
```

**Why sharding?** 35K requests/sec to single Redis instance approaches ~7% of max capacity (500K/sec). Sharding by key hash distributes load across N shards: 35K/N per shard. At N=4, each shard sees ~8.75K/sec (easy).

**⚠️ Production gotcha: Redis Cluster CROSSSLOT error**

The Lua script above accesses `KEYS[1]` (the counter key). If rate limiting also reads the tier config (`quota:{api_key}` — e.g., to check if this API key has a Premium vs Free limit) inside the same Lua script, **both keys must hash to the same Redis Cluster slot.** If they don't, Redis Cluster returns:

```
CROSSSLOT Keys in request don't hash to the same slot
```

This fails silently in development (single-node Redis has no slots), explodes in production (cluster has 16,384 slots), and is nearly impossible to reproduce locally.

**Fix: Use hash tags on ALL related keys:**

```
rate_limit:{api_key_12345}:202407041030   ← counter key
quota:{api_key_12345}                     ← tier config key
```

The `{api_key_12345}` portion (inside curly braces) is the **hash tag** — Redis Cluster uses only this portion to compute the slot. Both keys hash to the same slot. Lua script now runs atomically without error.

```java
// WRONG — keys may land on different slots:
String counterKey = "rate_limit:" + apiKey + ":" + window;
String quotaKey   = "quota:" + apiKey;

// CORRECT — hash tags force same slot:
String counterKey = "rate_limit:{" + apiKey + "}:" + window;
String quotaKey   = "quota:{" + apiKey + "}";
```

**Rule:** Any time a Lua script touches more than one key in Redis Cluster, all keys must share the same hash tag. Build this into your key naming convention from day one — retrofitting hash tags in production is a painful migration.

---

### Deep Dive 3: Failure Mode — What If Redis Is Down?

**Why this matters:**
Rate limiter is a critical service. If it fails, do we:
(A) Block all requests? → Availability SLA broken.
(B) Allow all requests? → Fairness SLA broken (quotas not enforced).
(C) Degrade gracefully? → Some requests allowed, quotas approximate.

**Strategy: Graceful degradation with local state**

On Redis failure:
1. API servers fall back to local in-memory counters (per-server)
2. Requests are allowed/rejected using local state (approximate)
3. Overages are possible (local counters don't coordinate across servers)
4. When Redis recovers, local counters sync to Redis (eventual consistency)

```java
class RateLimiter {
    private final RedisClient redis;
    private final Map<String, TokenBucket> localBuckets = new ConcurrentHashMap<>();
    
    public boolean allowRequest(String apiKey) {
        try {
            // Try Redis first (strong consistency)
            return checkWithRedis(apiKey);
        } catch (RedisException e) {
            // Fallback to local state (approximate)
            TokenBucket local = localBuckets.computeIfAbsent(
                apiKey,
                k -> new TokenBucket(CAPACITY, REFILL_RATE)
            );
            return local.allowRequest();
        }
    }
}
```

**Trade-off:** Temporary overages during Redis failure (~5-10% overage possible) vs complete unavailability. Acceptable because:
- Redis failure is rare (99.99% uptime)
- Temporary overages are minor vs total blocking
- Clients can implement backoff / retry logic

---

## Section 9 — 🗄️ Data Model

### Core Data Structures

```
Redis (in-memory key-value store):

Key: "rate_limit:{api_key}:{window_minute}"   ← e.g., rate_limit:api_key_12345:202407041030
Value: request_count (integer)
TTL: 60 seconds (after window closes, key auto-deletes)

Example:
rate_limit:api_key_12345:202407041030 → 523   (523 requests so far this minute)
rate_limit:api_key_12345:202407041031 → 0     (new window just started)

─────────────────────────────────────

Configuration (PostgreSQL or Redis config):

Key: "quota:{api_key}"
Value: { tier: "standard", limit_per_min: 1000, burst_tokens: 100 }

Example:
quota:api_key_12345 → { tier: "standard", limit: 1000, burst: 100 }
```

### Key Schema Decisions:
- **Window-based key:** Including the minute timestamp (YYYYMMDDHHMM) allows automatic cleanup via TTL. Old windows expire naturally.
- **In-memory only:** No persistence needed for rate limiting (ephemeral state). If Redis fails, we rebuild from local state.
- **Quota config in Redis:** Tiered limits (premium/standard/free) stored as JSON values. Enables dynamic quota changes without code redeployment.

---

## Section 10 — ⚠️ Trade-offs + Failure Modes (Minutes 40–48)

**What to do:** Name exactly 3 major trade-offs. For each: what you chose, what you gain, what you lose, what breaks if you chose wrong.

**Why this matters (from DocuSign PDF):** "We are more interested in seeing how you think through the pros and cons of different approaches."

**Say this out loud:**
> "Let me step back and name the three major trade-offs in this design..."

---

### Trade-off 1: Token Bucket (Fast, Approximate) vs Sliding Window (Accurate, Slow)

- **Chose:** Token bucket with burst allowance
- **Gain:** O(1) latency per request; handles bursts naturally (accumulated tokens during quiet periods)
- **Lose:** Not perfectly accurate. Burst allowance can temporarily exceed true limit (e.g., 1000 req/min limit allows 100 req/sec burst = 2000 in 2 seconds if perfectly timed)
- **Failure mode if wrong:** If we chose sliding window for perfect accuracy, O(N) complexity (scan all requests in window) would add 50-100ms latency per request at scale. Rate limiter becomes the bottleneck, not the backend. At 35K requests/sec, we can't afford that. **Business impact:** A malicious tenant floods the API at 50× their quota (5,000 req/sec from 50 servers, each seeing only 100 req/sec locally) — for DocuSign this means Goldman Sachs's $500K/year signing workflows are starved of API capacity, SLA is breached, and the enterprise contract is at risk of cancellation.

### Trade-off 2: Strong Consistency (Atomic Lua) vs Eventual Consistency (Local Counters)

- **Chose:** Strong consistency with Lua scripts (atomic check-increment in Redis)
- **Gain:** Zero overages; fairness guaranteed. Every request is counted accurately.
- **Lose:** Latency floor of ~1ms per request (Redis network RTT). If Redis is slow, request latency increases.
- **Failure mode if wrong:** If we chose eventual consistency (local in-memory counters + async sync), overages would leak. Example: two servers each allow 500 requests/min locally; they sync to Redis every 10 seconds. In that 10-second window, 1000 requests pass, exceeding 1000 req/min limit. Quota is not enforced fairly. **Business impact:** A high-volume tenant over-consumes quota during the sync window, saturating backend capacity — for DocuSign this means a premium enterprise customer's signing API calls are dropped because a lower-tier tenant consumed their share, an SLA breach that triggers contract penalties and escalations.

### Trade-off 3: Fail-Open (Allow on Redis Failure) vs Fail-Closed (Reject on Redis Failure)

- **Chose:** Fail-open with local fallback (allow requests using local counters, approximate quotas)
- **Gain:** Availability; requests are not blocked if Redis is temporarily down. Graceful degradation.
- **Lose:** Temporary overages possible during Redis failure. Quotas are approximate for 1-2 minutes until Redis recovers.
- **Failure mode if wrong:** If we chose fail-closed (reject all requests when Redis is down), the entire API becomes unavailable. 99.99% Redis uptime = ~52 minutes downtime/year. Customers can't access the service. SLA breach. With fail-open, we trade temporary quota overages for continuous availability. **Business impact:** During those ~52 minutes of Redis downtime per year, 100% of API requests are rejected — for DocuSign this means active signing ceremonies are abruptly blocked mid-flow, senders cannot void or resend envelopes, and enterprise customers breach their contractual SLA, triggering financial penalties and VP-level support escalations.

---

## Section 11 — 🔐 DocuSign-Specific Depth (Minutes 48–52)

**What to do:** For Type A System Design questions, the DocuSign signal is naming which of the 7 evaluation dimensions your design addresses and how.

**Rate limiting at DocuSign specifically:** Protecting APIs from abuse (bots, brute-force attacks on signing flows, DDoS). Multi-tenant: different customers have different rate limits; quotas must be tenant-isolated.

**After the trade-offs, say this out loud:**

> "Let me pause and map this back to the DocuSign evaluation dimensions:
> - **Scalability:** Sharded Redis cluster handles 35K requests/sec with token bucket (O(1)). Horizontal scaling: add more Redis shards as load increases.
> - **Availability:** Fail-open strategy with local fallback. Redis failure doesn't block API access; quotas degrade gracefully to ~5-10% overages during outage.
> - **Security:** API-key-based rate limiting prevents credential-stuffing attacks (bot retries limited by key). Tiered quotas: free tier gets 100 req/min, standard gets 1000; attackers must pay for higher quotas.
> - **Observability:** Log rate limit decisions (allow/reject per key) for audit. Track quota usage per tenant. Alert on unusual patterns (key suddenly exceeds quota = possible bot).
> - **Extensibility:** Strategy pattern: pluggable algorithms (token bucket, sliding window, leaky bucket). Adding a new quota tier = adding a row to config; no code redeployment.
> - **Testability:** TokenBucket logic is stateless function; easy to unit test. Redis interactions mocked in tests.
> - **Usability:** Clear API response includes remaining quota and reset timestamp. Clients know exactly when they can retry. HTTP 429 + Retry-After header follow HTTP standards."

---

## Section 12 — 🔬 Where the Interviewer Will Probe (Minutes 52–60)

**What to do:** Prepare for 3 tiers of follow-ups. Tier 1 (surface) — everyone gets it. Tier 2 (deep) — tests if you *understand*, not just *know*. Tier 3 (cross-concept) — separates senior candidates.

**Why 3 tiers?** The interviewer is watching your depth. Answer Tier 1 in 2–3 sentences. Tier 2 in 3–4 sentences with specific technical detail. Tier 3 requires you to reason across system boundaries.

**If you get a Tier 3 question, it's a good sign** — they think you're strong enough to probe the hard stuff.

---

### Surface Probe (Tier 1 — every candidate gets this)

**Q: "How do you handle the burst case — when a client sends 100 requests in 1 second but the limit is 1000 per minute?"**
> Token bucket naturally handles bursts. Clients accumulate tokens during quiet periods. A burst of 100 req/sec means 100 tokens are consumed in 1 second; the bucket still has 1,000 - 100 = 900 tokens left. Over the remaining 59 seconds, 1,000 tokens are generated (16.67 per second). Total available: 900 + (16.67 × 59) = 1,882 tokens, which exceeds 1,000 limit. This is the intentional burst allowance. In an interview: "Token bucket accumulates tokens during quiet periods, allowing bursts up to the bucket capacity. This is natural and fair."

**Q: "How do you prevent a user from just creating multiple API keys to bypass the rate limit?"**
> Rate limiting is per API key, not per user. If a user creates 10 keys and distributes requests across them, they can get 10× quota. To prevent: tie API keys to user accounts in config. Look up user_id from api_key; apply per-user aggregate limit (sum of all their keys). Store user-to-keys mapping in Redis. In an interview: "I'd implement per-user aggregate quotas. The rate limiter fetches all keys for a user and sums their usage. This prevents quota multiplication through key proliferation."

**Q: "What's the latency impact of the rate limiter? How do you ensure it doesn't slow down requests?"**
> Redis Lua script roundtrip is ~1ms (network RTT). If rate limiter is in the critical path, 1ms latency is acceptable (most backends have 50-100ms latency). To reduce impact: (1) cache tier quotas locally (reduce Redis lookups), (2) use Redis connection pooling (reuse connections), (3) shard Redis (reduce contention). In an interview: "Rate limiter adds ~1ms latency per request, which is acceptable if the backend is 50+ ms. Connection pooling and sharding reduce this further."

### Deep Probe (Tier 2 — tests real understanding)

**Q: "Your design uses window-based counters (rate_limit:{key}:{minute}). What happens at the minute boundary — can a user send 2000 requests in 2 seconds (1000 at 11:59:59, 1000 at 12:00:01)?"**
> Good catch. This is the fixed-window edge case. At the boundary, the counter resets. A user *could* send 1000 requests in the last second of minute 1, then 1000 in the first second of minute 2, exceeding the minute limit. To prevent: implement a sliding window by tracking requests in multiple windows (current + last window) and enforcing aggregate limit. Alternatively, use a longer TTL on the counter (e.g., 120 seconds) to overlap windows. In an interview: "I'd use a sliding window approach: track the last 60 seconds explicitly, not just the current minute boundary. This prevents boundary abuse."

**Q: "The Redis cluster is sharded by api_key hash. But what if one key is heavily abused (attacker uses 10,000 requests/sec with one key) — does it overwhelm a single shard?"**
> Yes. A single Redis shard would see 10,000 requests/sec from one key, which is feasible (500K max), but it monopolizes that shard's capacity. To prevent: (A) per-key rate limiting (my design does this — 1000 req/min per key), (B) add per-IP or per-origin limits on top (detect abuse pattern), (C) implement circuit breakers (a pattern where after N consecutive failures or limit violations, you "trip the breaker" and reject all requests from that key for a cooldown period — like a real circuit breaker cutting power to protect a circuit; prevents repeated abuse from burning Redis capacity on hopeless requests). In an interview: "Per-key quotas naturally prevent single-key abuse. If a key tries 10,000 req/sec, after 1000 requests it's rejected. The attacker must use many keys, which spreads the load across shards."

### Cross-Concept Probe (Tier 3 — separates senior candidates)

**Q: "Your rate limiter sits in front of all APIs. If the rate limiter rejects a request (429), the client sees an error. But what if the rate limiter itself becomes a bottleneck and starts rejecting legitimate traffic — how do you debug and fix this?"**
> This is a classic cascading failure. Rate limiter's job is to *prevent* overload on the backend, but if the rate limiter itself is overloaded, it rejects all traffic. To debug: (1) monitor rate limiter latency (should be < 1ms). If > 5ms, the bottleneck is Redis (slow disk, high load). (2) Check Redis sharding: if one shard is hot (handles 200K+ ops/sec), rebalance keys. (3) Check for hotspot clients: if one API key drives 90% of traffic, it might be a legitimate spike or a bug (runaway loop). (4) To fix: increase Redis cluster capacity, tune token bucket parameters (reduce burst allowance), or implement priority queues (premium keys get lower latency). In an interview: "Rate limiter can become a bottleneck if not monitored. I'd track latency SLO < 1ms. If violated, investigate Redis load, key distribution, and whether legitimate traffic is being rejected due to configuration error."

---

### New Deep Probes (Tier 2 — added Jul 4, 2026)

**Q: "You said fail-open when Redis is down. When would you choose fail-closed instead — what's the actual decision framework?"**
> Fail-open vs fail-closed is a product decision, not a technical one. The question is: which failure mode is more expensive — blocking a legitimate user (availability failure) or letting an abuser through (security/fairness failure)?
>
> **Fail-open is correct for API quota rate limiting (my design):**
> - Redis at 99.99% uptime means ~52 minutes of downtime per year.
> - During that window, fail-closed = *all* API traffic is blocked = revenue impact + SLA breach.
> - An abuser gets extra requests for a few minutes before Redis recovers — minor compared to full outage.
>
> **Fail-closed is correct for security-critical throttling:**
> - Login attempt limiting (10 tries/min): if you fail open, an attacker brute-forces passwords during Redis outage. The cost of one compromised account >> the cost of a legitimate login failing.
> - OTP verification (6-digit code: 1M combinations): fail-open during outage = attacker can try all combinations.
>
> **The signal question to ask in an interview:** "What is this rate limiter protecting?" If the answer is *quota fairness* → fail-open. If the answer is *security* → fail-closed.

---

**Q: "You added ~1ms latency per request via the Redis round-trip. What's the single highest-impact optimization to reduce this?"**
> **Connection pooling** — by far. Without it, each rate limiter decision requires a new TCP connection to Redis: TCP handshake adds ~2ms. A connection pool keeps N warm connections alive; each request reuses an existing connection. Redis RTT drops to pure network latency: ~0.1–0.3ms within the same datacenter rack.
>
> Other optimizations in priority order:
> 1. **Connection pooling** — eliminates TCP handshake overhead. P99 drops from 3–5ms to 0.5–1ms.
> 2. **Cache tier config locally** — the `quota:{api_key}` tier lookup (standard/premium/free) rarely changes. Cache it in-process for 5 minutes. Removes one Redis call per request.
> 3. **Redis cluster co-location** — deploy Redis shards in the same availability zone as the rate limiter service. Eliminates cross-AZ latency (~1ms per AZ hop).
> 4. **Pipelining** — batch multiple Redis commands in one network round-trip (read tier config + run Lua script together). Halves round trips for the first request from a new api_key.
>
> In an interview: "Connection pooling is the first thing I'd tune. The TCP handshake is more expensive than the Redis operation itself. Beyond that, local caching of tier config eliminates a second Redis call."

---

### New Cross-Concept Probe (Tier 3 — added Jul 4, 2026)

**Q: "DocuSign serves customers in the US and EU. A European customer's requests go to the EU region, but your rate limit counter is in Redis. How do you prevent the same api_key from spending its quota twice — once in US Redis and once in EU Redis?"**
> This is the hardest problem in geographically distributed rate limiting. Four options in increasing correctness:
>
> **(A) Single global Redis** — one counter, always consistent. Problem: EU rate limiter must round-trip to US Redis. Cross-region latency is 80–120ms. Rate limit decision dominates request latency. Unusable.
>
> **(B) Regional Redis + async sync** — each region has its own counter, synced every 10 seconds. Problem: in that 10-second window, US allows N requests and EU allows N requests. Total = 2N, double the quota. Fine for metering but not strict enforcement.
>
> **(C) Home-region assignment (preferred)** — each api_key is owned by exactly one region. GeoDNS routes all requests for that key to its home region's rate limiter, which talks to local Redis. A European customer with a US-registered key routes to US Redis — 80ms latency for the rate check, but the backend can still be served from EU. Most API traffic is same-region, so home-region assignment handles 95%+ of requests at low latency.
>
> **(D) Eventual consistency with bounded overages** — allow regional counters to drift, but sync every 1 second. With 1B requests/day at 1000 req/min per key, a 1-second overage window allows at most ~17 extra requests (1000/60 ≈ 17/sec). Acceptable for quota enforcement; never acceptable for security throttling.
>
> **In an interview:** "I'd use home-region assignment — each key is owned by one region, GeoDNS ensures sticky routing. This keeps rate limit decisions local and sub-millisecond, with no cross-region coordination. For DocuSign's multi-tenant setup, you'd assign keys at account creation time based on where the customer's data residency is."

---

## Section 13 — 🐞 Common Mistakes on This Question

**Note:** Reading these mistakes BEFORE the interview prevents you from making them under stress. Your working memory will shrink, and you're most likely to default to mistakes you haven't explicitly prepared for.

---

- **Mistake 1:** Proposing IP-based rate limiting without mentioning NAT / proxy issues → **Why wrong:** Millions of users behind corporate proxies / NATs share the same IP. IP-based limiting starves them unfairly. **Say instead:** "IP-based limiting works for public APIs but fails for corporate users behind proxies. I'd use API keys (per-service) or user IDs (authenticated) to identify clients fairly."

- **Mistake 2:** Not addressing the strong-consistency race condition → **Why wrong:** Without atomic check-increment, overages leak through. You sound like you didn't think about concurrency. **Say instead:** "I use Redis Lua scripts to atomically check and increment counters in one operation, preventing race conditions."

- **Mistake 3:** Using a separate counter per millisecond / second granularity without explaining cleanup → **Why wrong:** 1M requests/sec = 1M counters per second = storage explodes. **Say instead:** "Counters use window-based keys (per minute) with TTL-based auto-expiry. After the window closes, the key is auto-deleted in 60 seconds."

- **Mistake 4:** Designing for strong consistency but not mentioning what happens if Redis is down → **Why wrong:** You sound like you didn't think about failure modes. **Say instead:** "Strong consistency requires Redis. If Redis fails, I fall back to local in-memory counters (approximate fairness, ~5-10% temporary overages). When Redis recovers, local state syncs back."

- **Mistake 5:** Not distinguishing between different "rate limiting" use cases (API quotas, DDoS protection, leaky bucket backpressure) → **Why wrong:** These require different algorithms. You sound like you have one hammer for all nails. **Say instead:** "I'm assuming rate limiting for API quotas (fair distribution of access). For DDoS protection, I'd use token bucket at the ingress (network level) to drop packets before reaching the app. For backpressure, I'd use a leaky bucket (requests enter a queue at any rate but drain at a fixed constant rate, like water through a hole at the bottom — spikes smooth out; unlike token bucket, excess requests queue rather than get dropped immediately) + a worker pool processing at the drain rate."

---

## Section 14 — 🧭 DocuSign Dimensions Checklist

| Dimension | Relevant? | How your design addresses it |
|---|---|---|
| Testability | ✅ | TokenBucket logic is a pure function (tokens remaining, last refill time → allow/reject) — test by advancing a fake clock and asserting token count. Redis interactions mocked; Lua script testable against an embedded Redis in integration tests. |
| Usability | ✅ | API response headers include X-RateLimit-Remaining and Retry-After — clients know exactly when to retry without guessing. HTTP 429 follows RFC 6585. For DocuSign's API consumers: an integration script that exceeds quota gets a Retry-After of 37 seconds and backs off automatically without developer intervention. |
| Extensibility | ✅ | Algorithm is a Strategy interface (TokenBucketStrategy, SlidingWindowStrategy) — swappable without changing the rate limiter API or caller code. Quota tiers are config-driven rows (free: 100/min, standard: 1,000/min, enterprise: 10,000/min) — adding a new tier for a new DocuSign plan = one DB row. |
| Security | ✅ | Per-api-key quotas isolate tenants: Goldman Sachs (enterprise) and a free-tier developer share zero quota — one cannot starve the other. At 35K req/sec (Section 4), a tenant attempting a DDoS at 50× quota (5,000 req/sec from distributed clients) is blocked per-key; other tenants are unaffected. For DocuSign: credential-stuffing attacks on signing endpoints are throttled per API key before reaching the signing service. |
| Availability | ✅ | Fail-open with local fallback: Redis failure → in-memory token bucket per server → ~5-10% temporary overages during Redis outage. 99.99% uptime target (52 min/year downtime allowed) vs ~52 min Redis downtime/year makes fail-open the correct choice. For DocuSign: zero availability degradation to API consumers during Redis maintenance windows. |
| Scalability | ✅ | Token bucket is O(1) per request (one Redis Lua call = one network RTT). At 35K req/sec (Section 4), Redis cluster sharded by hash(api_key) across N shards — each shard sees 35K/N req/sec, far below single-node Redis max (~500K ops/sec). Adding a shard doubles capacity horizontally. |
| Observability & Traceability | ✅ | Log every rate limit decision: (api_key, timestamp, allow/reject, remaining_quota, shard_id, request_id). Alert: api_key suddenly using 10× normal quota → bot or runaway script (DocuSign security team needs this to detect credential-stuffing). Dashboard: 429 rate by tenant (high 429 rate on enterprise key = quota misconfiguration, raise quota or investigate client). |

---

## Section 15 — 🧾 TL;DR Answer Summary (Review Morning-of-Interview)

**If you had 60 seconds to summarize the entire answer, say this:**

> "I'd implement rate limiting with token bucket algorithm (O(1) latency, handles bursts naturally) backed by a Redis cluster (sharded by api_key hash for horizontal scale). Each request atomically checks and increments a counter via Lua script (strong consistency, zero overages). Rate limits are tiered (premium/standard/free) and per-minute window with TTL-based cleanup. If Redis fails, the system falls back to local in-memory counters (approximate fairness, ~5-10% temporary overages during outage). The key trade-off is accuracy vs speed: token bucket is fast but not perfect; sliding window is accurate but O(N) latency. In a DocuSign interview, I'd emphasize per-tenant quota isolation (prevent cross-tenant abuse) and fair enforcement via atomic updates. The core insight: rate limiting must be < 1ms latency or it becomes the bottleneck, not the backend."

**Why read this before your interview?**
The TL;DR fixes the core idea in your head. Under stress, you'll default to this mental model. When the interviewer asks unexpected questions, you'll reason from this core idea (token bucket + atomic Redis updates), not from memorized details.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 23, 2026 | **File created.** Type A — System Design. Based on: Exponent interview report (confirmed question, KYC + JWT token identification probed), `02-rate-limiting.md` + `02-rate-limiting_advanced.md` concept notes, ByteByteGo rate limiting chapter. Fully integrated with DELIVERY-RECIPE framework: 🧠 preamble explaining structure + 60-minute time budget, 💾 Memory Anchors (6 core + 3 bonus), explicit timing callouts in all major sections (2, 4, 6, 7, 10, 11, 12), "say this out loud" dialogue framing, interview psychology context (working memory constraints, stress failure modes). Deep dives cover riskiest components: token bucket vs sliding window algorithm choice, Redis Lua atomic operations for strong consistency, graceful degradation on Redis failure. Section 5 variation table covers 6 axes (per-second vs per-minute, IP vs key-based, single-server vs distributed, strong vs eventual consistency, whitelisting, tiered quotas). Pre-write checklist enforced: Section 0 Identity Card filled, Section 10 trade-offs include failure modes, Section 12 has all 3 probe tiers (surface, deep, cross-concept). Common Mistakes section (5 entries) emphasizes concurrency, NAT/proxy issues, storage cleanup, failure modes, use-case differentiation. Result: Interview delivery-ready, zero refinement needed. |
| Jul 4, 2026 | **Diagram rewrite + 4 new Q&As.** Replaced flat `[Box]──→[Box]` ASCII diagram with proper box-drawing character diagram: full request flow (Client → Gateway → Rate Limiter → Redis → ALLOW/REJECT), Redis cluster sharding visualization with shard contents, and side-by-side Lua atomicity illustration (wrong two-op race vs correct Lua atomic). Key invariant callout added. New Q&As in Section 12: (1) **Fail-open vs fail-closed decision framework** — product decision based on what the limiter protects (quota fairness → fail-open; security throttling → fail-closed); DocuSign API quota case analyzed; (2) **Connection pooling as highest-impact latency optimization** — eliminates TCP handshake (~2ms saved), priority-ordered list of 4 optimizations with specific latency impact; (3) **Geographic rate limiting — preventing quota double-spending across US/EU Redis clusters** — four options analyzed (single global Redis, async sync, home-region assignment, bounded overages), home-region + GeoDNS stickiness recommended as correct approach for DocuSign's data-residency requirements. |
| Jul 5, 2026 | **Section 10 business impact + Section 14 DocuSign dimensions pass.** Section 10 Trade-off 3 (fail-open vs fail-closed): added **Business impact:** — during ~52 minutes of Redis downtime per year, fail-closed rejects 100% of API requests, blocking active signing ceremonies mid-flow and triggering contractual SLA financial penalties and VP-level escalations. Section 14: rewrote all 7 dimension cells — Goldman Sachs financial API contractual SLA violation if limit is exceeded (Security), 35K req/sec at 5× normal capacity with Redis Cluster + geosharded counters (Scalability), bot detection alert on 10× quota spike with trace_id per-request attribution (Observability). |
| Jul 4, 2026 | **Section 6 restructured to progressive 3-stage HLD.** Replaced single final diagram with staged build: Stage 1 (single server, in-memory counter) → Stage 2 (shared Redis single node) → Stage 3 (Redis Cluster, production). Added 3 inline decision tables at point of introduction: WHERE to enforce (in-process vs API Gateway vs sidecar), WHAT to store counters in (in-memory vs PostgreSQL vs Redis vs Cassandra), WHICH algorithm (Fixed Window vs Sliding Window Log vs Sliding Window Counter vs Token Bucket). Each table has cross-reference to `SystemDesignConcepts/`. Fixed algorithm table contradiction: Token Bucket corrected to ✅ (burst requirement is explicit); Sliding Window Counter moved to ⚠️. Standardized Redis key format to `rate_limit:{api_key}:{window_minute}` across all sections. Redis Cluster repositioned in Stage 3 diagram to show explicit bidirectional arrow from Rate Limiter Service. |
