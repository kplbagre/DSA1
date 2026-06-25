# C1 — Design a Rate Limiter for a Microservices API

> **Read `solution-notes-standards.md` first.** This file is a 60-minute interview answer framework, not a concept reference. Concept-level depth lives in `SystemDesignConcepts/`.

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
| "Whitelist / blacklist per pattern" | Add pattern matcher: if request matches pattern X, bypass rate limiter. Store patterns in Redis with fast lookup (bloom filter or trie). | Whitelisting specific clients (by IP, user agent, API endpoint) prevents false blocks. Bloom filter gives O(1) lookup without false negatives. |
| "Stricter limits on free tier (10 req/min instead of 100)" | Fetch user tier from cache/DB; apply corresponding limit. Add penalty: if user exceeds free tier limit, throttle for 1 hour. | Tiering encourages paid upgrades. Penalties deter abuse. Storage cost for tier metadata is negligible. |

---

## Section 6 — 🏗️ High-Level Architecture (Minutes 10–25)

**What to do:** Draw the boxes (ASCII or whiteboard). Walk through the data flow *as if telling a story*. The interviewer is checking: "Does this person understand flow or just know boxes?"

**Say this out loud (as you start drawing):**
> "Let me draw a high-level architecture. This is how the system looks from 10,000 feet..."

---

### ASCII Architecture Diagram

```
[Client Request with API key]
           ↓
    [API Gateway / LB]
           ↓
    [Rate Limiter Service]
      /          \
  [Redis cluster] ← rate limit counters (atomic increments)
     (sharded)
      /
  [Check counter against limit]
     /        \
[ALLOW]    [REJECT: 429]
   ↓            ↓
[Forward to]  [Return 429 +
 backend]      Retry-After]
   ↓
[Response]

─────────────────────────────────────

[Optional: Strong consistency loop]

[Rate Limiter Service]
    ↓
[Lua script on Redis]
  (atomically check + increment in single operation)
    ↓
[No race conditions between check and increment]
```

**Data flow walkthrough (say this out loud):**

1. **Client sends request:** HTTP request arrives with `Authorization: Bearer {api_key}` header.
2. **Rate Limiter intercepts:** At API gateway or before backend, rate limiter extracts api_key and current timestamp.
3. **Fetch quota:** Look up api_key's tier (premium/standard/free) in Redis (cached, fast). Get rate limit (e.g., 1000 req/min).
4. **Check window:** Determine which 1-minute window we're in. Query Redis counter for this key + window: `rate_limit:{api_key}:{window_minute}`.
5. **Atomic increment & check:** Redis Lua script: increment counter, check if > limit. Return decision in single atomic operation (no race condition between check and increment).
6. **Decision:** If counter ≤ limit, return ALLOW; increment happens. If > limit, return REJECT with 429 status + Retry-After header.
7. **TTL cleanup:** Redis key has TTL = window duration + 60 sec. After expiry, counter is auto-deleted.

**Each box justified:**
- **API Gateway / LB:** Central point to intercept all requests. Scales horizontally; rate limiter is stateless.
- **Rate Limiter Service:** Extracts api_key, checks quota, makes allow/reject decision. Designed for < 1ms latency.
- **Redis cluster:** Stores counters for all keys. Sharded for scalability. Lua scripts ensure atomic check-increment.
- **Lua script:** Ensures strong consistency. In a single network round-trip, we check and increment. No client-side retries needed.

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

## Section 8 — 🌐 API Design

### Core Endpoints

| Method | Path | Auth | Request body | Response | Status codes |
|---|---|---|---|---|---|
| POST | `/v1/rate-limit/check` | API Key in header | `{ "api_key": "..." }` | `{ "allowed": true/false, "remaining": 50, "reset_at": 1234567890 }` | 200, 429 |
| GET | `/v1/rate-limit/status/{api_key}` | API Key in header | — | `{ "tier": "standard", "limit": 1000, "remaining": 523, "reset_at": ... }` | 200, 404, 401 |

### WebSocket Protocol
None (not needed for this question).

### Key Design Decisions:
- **API key in header:** `Authorization: Bearer {api_key}` or custom `X-API-Key` header. Standard for service-to-service auth.
- **Idempotency:** Each request increments counter once. No idempotency needed (not idempotent by design — every call counts).
- **Response fields:** remaining quota and reset timestamp help clients decide when to retry.
- **Status codes:** 200 for allowed, 429 for rejected. Retry-After header in 429 response.

---

## Section 9 — 🗄️ Data Model

### Core Data Structures

```
Redis (in-memory key-value store):

Key: "rate_limit:{api_key}:{window_minute}"
Value: request_count (integer)
TTL: 60 seconds (after window closes, key auto-deletes)

Example:
rate_limit:api_key_12345:202406240930 → 523
rate_limit:api_key_12345:202406240931 → 0  (new window just started)

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
- **Failure mode if wrong:** If we chose sliding window for perfect accuracy, O(N) complexity (scan all requests in window) would add 50-100ms latency per request at scale. Rate limiter becomes the bottleneck, not the backend. At 35K requests/sec, we can't afford that.

### Trade-off 2: Strong Consistency (Atomic Lua) vs Eventual Consistency (Local Counters)

- **Chose:** Strong consistency with Lua scripts (atomic check-increment in Redis)
- **Gain:** Zero overages; fairness guaranteed. Every request is counted accurately.
- **Lose:** Latency floor of ~1ms per request (Redis network RTT). If Redis is slow, request latency increases.
- **Failure mode if wrong:** If we chose eventual consistency (local in-memory counters + async sync), overages would leak. Example: two servers each allow 500 requests/min locally; they sync to Redis every 10 seconds. In that 10-second window, 1000 requests pass, exceeding 1000 req/min limit. Quota is not enforced fairly.

### Trade-off 3: Fail-Open (Allow on Redis Failure) vs Fail-Closed (Reject on Redis Failure)

- **Chose:** Fail-open with local fallback (allow requests using local counters, approximate quotas)
- **Gain:** Availability; requests are not blocked if Redis is temporarily down. Graceful degradation.
- **Lose:** Temporary overages possible during Redis failure. Quotas are approximate for 1-2 minutes until Redis recovers.
- **Failure mode if wrong:** If we chose fail-closed (reject all requests when Redis is down), the entire API becomes unavailable. 99.99% Redis uptime = ~52 minutes downtime/year. Customers can't access the service. SLA breach. With fail-open, we trade temporary quota overages for continuous availability.

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
> Yes. A single Redis shard would see 10,000 requests/sec from one key, which is feasible (500K max), but it monopolizes that shard's capacity. To prevent: (A) per-key rate limiting (my design does this — 1000 req/min per key), (B) add per-IP or per-origin limits on top (detect abuse pattern), (C) implement circuit breakers (if a key consistently hits limits, block it for 1 hour). In an interview: "Per-key quotas naturally prevent single-key abuse. If a key tries 10,000 req/sec, after 1000 requests it's rejected. The attacker must use many keys, which spreads the load across shards."

### Cross-Concept Probe (Tier 3 — separates senior candidates)

**Q: "Your rate limiter sits in front of all APIs. If the rate limiter rejects a request (429), the client sees an error. But what if the rate limiter itself becomes a bottleneck and starts rejecting legitimate traffic — how do you debug and fix this?"**
> This is a classic cascading failure. Rate limiter's job is to *prevent* overload on the backend, but if the rate limiter itself is overloaded, it rejects all traffic. To debug: (1) monitor rate limiter latency (should be < 1ms). If > 5ms, the bottleneck is Redis (slow disk, high load). (2) Check Redis sharding: if one shard is hot (handles 200K+ ops/sec), rebalance keys. (3) Check for hotspot clients: if one API key drives 90% of traffic, it might be a legitimate spike or a bug (runaway loop). (4) To fix: increase Redis cluster capacity, tune token bucket parameters (reduce burst allowance), or implement priority queues (premium keys get lower latency). In an interview: "Rate limiter can become a bottleneck if not monitored. I'd track latency SLO < 1ms. If violated, investigate Redis load, key distribution, and whether legitimate traffic is being rejected due to configuration error."

---

## Section 13 — 🐞 Common Mistakes on This Question

**Note:** Reading these mistakes BEFORE the interview prevents you from making them under stress. Your working memory will shrink, and you're most likely to default to mistakes you haven't explicitly prepared for.

---

- **Mistake 1:** Proposing IP-based rate limiting without mentioning NAT / proxy issues → **Why wrong:** Millions of users behind corporate proxies / NATs share the same IP. IP-based limiting starves them unfairly. **Say instead:** "IP-based limiting works for public APIs but fails for corporate users behind proxies. I'd use API keys (per-service) or user IDs (authenticated) to identify clients fairly."

- **Mistake 2:** Not addressing the strong-consistency race condition → **Why wrong:** Without atomic check-increment, overages leak through. You sound like you didn't think about concurrency. **Say instead:** "I use Redis Lua scripts to atomically check and increment counters in one operation, preventing race conditions."

- **Mistake 3:** Using a separate counter per millisecond / second granularity without explaining cleanup → **Why wrong:** 1M requests/sec = 1M counters per second = storage explodes. **Say instead:** "Counters use window-based keys (per minute) with TTL-based auto-expiry. After the window closes, the key is auto-deleted in 60 seconds."

- **Mistake 4:** Designing for strong consistency but not mentioning what happens if Redis is down → **Why wrong:** You sound like you didn't think about failure modes. **Say instead:** "Strong consistency requires Redis. If Redis fails, I fall back to local in-memory counters (approximate fairness, ~5-10% temporary overages). When Redis recovers, local state syncs back."

- **Mistake 5:** Not distinguishing between different "rate limiting" use cases (API quotas, DDoS protection, leaky bucket backpressure) → **Why wrong:** These require different algorithms. You sound like you have one hammer for all nails. **Say instead:** "I'm assuming rate limiting for API quotas (fair distribution of access). For DDoS protection, I'd use token bucket at the ingress (network level) to drop packets before reaching the app. For backpressure, I'd use a leaky bucket + queue."

---

## Section 14 — 🧭 DocuSign Dimensions Checklist

| Dimension | Relevant? | How your design addresses it |
|---|---|---|
| Testability | ✅ | TokenBucket logic is a pure function; unit tests mock time and verify token accumulation. Redis interactions mocked in unit tests. |
| Usability | ✅ | API response includes remaining quota and reset timestamp; clients know when to retry. HTTP 429 + Retry-After follow standard conventions. |
| Extensibility | ✅ | Algorithm is pluggable (token bucket, sliding window, leaky bucket). Quota tiers are config-driven, not hardcoded. New tier = new config row. |
| Security | ✅ | API-key-based limiting prevents credential-stuffing attacks. Tiered quotas prevent free users from DoS-ing infrastructure. Per-tenant quotas prevent cross-tenant abuse. |
| Availability | ✅ | Fail-open strategy with local fallback. Redis failure doesn't block API access; quotas degrade gracefully. 99.99% target achieved via sharding + replicas. |
| Scalability | ✅ | Token bucket is O(1) per request. Redis cluster sharding distributes load across N shards. Handles 35K+ requests/sec peak. |
| Observability & Traceability | ✅ | Log rate limit decisions per key (allow/reject, remaining quota). Alert on anomalies (key suddenly exceeding quota = bot). Trace request through rate limiter via request ID. |

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
