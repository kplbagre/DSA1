# D3 — Design an API Rate Limiter

> **Prerequisites:**
> | Concept file | What to load before reading |
> |---|---|
> | `DSA/SystemDesignConcepts/Foundations/Performance-and-Scale/02-rate-limiting.md` | Token bucket, sliding window, fixed window, leaky bucket algorithms |
> | `DSA/SystemDesignConcepts/Foundations/Performance-and-Scale/02-rate-limiting_advanced.md` | Distributed coordination, adaptive limiting, multi-dimensional limiting |
> | `DSA/SystemDesignConcepts/Foundations/Performance-and-Scale/03-caching.md` | Redis INCR + EXPIRE pattern, pipelining |
> | `DSA/SystemDesignConcepts/Foundations/Performance-and-Scale/09-sharded-counters.md` | Hot DSP key problem, Redis sharding |
> | `Interview/Disney/LLD-rate-limiter-java.md` | Java implementation depth — computeIfAbsent trap, per-bucket sync, LongAdder |
> | `Interview/DocuSign/r2-solutions/C1-rate-limiter.md` | System-level HLD, Redis Cluster, Lua script, probe Q&As |

---

## 🎯 What Is This System? (Section -1 — Pre-Interview Orientation)

**In plain English:** A rate limiter enforces quotas on how many requests a client can make in a sliding time window. It sits in front of every API endpoint and returns HTTP 429 (Too Many Requests) once the quota is exhausted — protecting downstream services from overload, abuse, and runaway automation.

**Disney / Ad Platforms context:** The Disney Ad Exchange receives RTB (Real-Time Bidding — an auction mechanism where ad buyers and sellers negotiate the price of one impression in <100ms, entirely automated) bid requests from DSPs (Demand Side Platforms — ad-buying platforms used by advertisers like The Trade Desk, Google DV360, and Amazon DSP). A single large DSP like The Trade Desk can send 300K bid requests per second during ESPN's Monday Night Football. Without a rate limiter, one DSP's surge can exhaust Disney's auction infrastructure and block bids from all other DSPs — a direct revenue loss.

**Real-world examples:**
| System / Company | What they built |
|---|---|
| **Disney Ad Exchange** | Per-DSP throttling at 300K QPS; protects auction latency during live sports |
| **GitHub API** | 5,000 requests/hour per authenticated token; `X-RateLimit-*` response headers |
| **Stripe API** | 100 requests/second per key; burst allowed, sustained excess rejected |
| **Cloudflare Rate Limiting** | Edge-level limiting — rules fire before traffic reaches origin |
| **AWS API Gateway** | Per-stage throttling: burst limit + steady-state requests/second |
| **OpenAI API** | Tokens/minute + requests/minute limits per pricing tier |

**Core user journey:** The Trade Desk sends its 100,001st bid request in 60 seconds (Disney's DSP quota: 100K/min) → the rate limiter intercepts before the auction engine → returns `HTTP 429 Too Many Requests` with `Retry-After: 12` → The Trade Desk backs off and resumes at the next window.

**Why it's hard to build at scale:** In a distributed system with 50 ad server instances, a per-server counter lets a DSP bypass the limit by distributing requests across all 50 servers — no single server sees the full rate. You need a shared, atomic counter in a sub-millisecond store that absorbs 300K increments per second without becoming a bottleneck inside the 100ms RTB window.

---

## Section 0 — Question Identity Card

| | |
|---|---|
| **Question** | Design an API Rate Limiter |
| **Interview Type** | Type A — System Design (with Java LLD follow-up expected) |
| **Confirmed or Likely** | ⭐ Confirmed — JioHotstar Staff SWE (offer received May 2026); high probability for Disney Ad Platforms |
| **Concept notes prerequisite** | Token bucket algorithm, Redis Lua atomicity, CROSSSLOT bug in Redis Cluster |
| **Disney-specific angle** | Per-DSP throttling protects the ad auction engine during live sports spikes at 300K QPS; fail-open is correct (blocking a DSP during NBA Finals = lost revenue on Disney's highest-CPM inventory) |
| **Time budget** | 5 min clarify → 5 min estimate → 15 min HLD → 15 min deep dive (Java LLD + Redis Lua) → 8 min trade-offs → 7 min Q&A buffer |

---

## Section 1 — 🚀 The One-Sentence Opener

> "Before I draw anything, let me ask a few clarifying questions — specifically about what we're rate limiting, whether this is single-server or distributed, and what happens when the limit is hit, because those three answers drive the entire architecture."

---

## Section 2 — 🔍 Clarifying Questions Script (Minutes 0–5)

**Q: "Are we rate limiting by IP address, by API key, by user, or by a service identity like DSP account ID?"**
- Why ask: The identification method determines where the counter key lives and what tier config to look up.
- IP-based → simple, fails for corporate NAT (millions of users behind one IP), fails for CDN-proxied traffic.
- API-key-based → per-service, identifies the integration; good for external partners (DSPs).
- DSP account ID from JWT → correct for ad exchange (The Trade Desk has one account, many API keys).
- Assume: **DSP account ID** extracted from the JWT on every bid request — this is the billing and quota unit.

**Q: "What's the rate limit — requests per second, per minute, per hour? And is it a hard reject or a soft queue?"**
- Why ask: Determines algorithm choice. Token bucket handles variable rates with burst; leaky bucket smooths traffic but has no burst allowance.
- Per-minute, hard reject → token bucket with per-minute window. Simple, O(1). Pick this.
- Per-second, strict → fixed window with per-second key. Simpler but allows 2× burst at window boundary.
- Soft queue → leaky bucket + worker pool. More complex, not appropriate for RTB (<100ms deadline).
- Assume: **per-minute, hard reject** (HTTP 429 + Retry-After header).

**Q: "Single-server or distributed — do multiple ad server instances share a rate limit counter?"**
- Why ask: This is the decisive architecture fork. Single-server allows in-memory; distributed requires Redis.
- Single-server → token bucket in JVM memory; no network overhead; breaks at the first horizontal scale.
- Distributed (50 servers) → Redis shared counter; atomic Lua script; adds ~0.5ms per request.
- Assume: **distributed, 50 ad server instances** — the interesting case; in-memory is a solved problem.

**Q: "What should happen if the rate limiter's Redis cluster goes down?"**
- Why ask: Forces an AP vs CP decision. Fail-open vs fail-closed has opposite consequences for availability vs security.
- Fail-open (allow all) → temporary overages; no ad break blocked; DSP quota lightly violated.
- Fail-closed (block all) → full ad serving stops; live sports inventory is entirely missed; catastrophic revenue loss.
- Assume: **fail-open** with local in-memory fallback — same reasoning as D2 pacing gate.

**Q: "Do different DSPs have different quotas — e.g., premium DSPs get higher limits than standard?"**
- Why ask: Tiered quotas require per-DSP config lookup on every request. If flat, one global limit, simpler.
- Tiered → fetch DSP tier from a config cache (Redis or in-process cache) before the gate check.
- Flat → one threshold, no lookup.
- Assume: **tiered** — Disney Premier DSP (1M req/min), Standard (100K req/min), Test (10K req/min).

---

## Section 3 — 📋 Requirements

**Functional Requirements:**
- Rate limiter accepts a bid request with a DSP account ID and returns allow/reject within 5ms
- Different DSP tiers have different per-minute quotas (Premier, Standard, Test)
- Rejected requests return HTTP 429 with `Retry-After`, `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` headers
- Rate limit decision is enforced consistently across all 50 ad server instances
- Out of scope: IP-based blocking, DDoS protection at network layer, content-based filtering

**Non-Functional Requirements:**
- Scale: 300K bid requests/sec peak (ESPN live sports); 10K DSP accounts active at peak
- Latency: gate check P99 < 5ms (fits in 100ms RTB window; leaves 95ms for auction logic)
- Availability: 99.99% — ad serving must not be blocked by rate limiter infrastructure failures
- Consistency: strong during normal operation (no quota overages); approximate during Redis outage (fail-open, ~5-10% temporary overages)
- Fairness: one DSP's quota exhaustion must not affect another DSP's bid capacity

---

## Section 3.5 — 🗂️ Core Entities

| Entity | What it represents |
|---|---|
| **DSPClient** | The DSP being rate limited — identified by account ID from JWT; maps to a quota tier |
| **RateLimitPolicy** | The rule applied to a tier — e.g., "Premier = 1,000,000 req/min, Standard = 100,000 req/min" |
| **RateLimitCounter** | Ephemeral rolling count of bid requests for a given DSP in the current minute window; stored in Redis; never persisted to disk |
| **ViolationLog** | Append-only record of 429 events per DSP for abuse detection and SLA reporting; written to Kafka → Cassandra |

**Key relationships:**
- A `DSPClient` has one `RateLimitPolicy` (tier-assigned at contract signing)
- A `RateLimitCounter` is scoped to `(dsp_account_id, window_minute)` — auto-deleted by Redis TTL when window expires
- `ViolationLog` is write-only; never read on the hot path; queried by ops dashboards

---

## Section 4 — 🔢 Scale Estimation (Minutes 5–8)

**Traffic:**
- Peak bid requests/sec: **300,000** (ESPN live sports — Monday Night Football, NBA Finals)
- Average bid requests/sec: 300,000 / 3 = **100,000** (3× peak factor for live sports)
- Active DSP accounts at peak: **10,000**
- Requests per DSP per second (average): 100K / 10K = **10 requests/sec/DSP**

**Redis counter load:**
- Stage 2 (per-request INCR): 300K Redis ops/sec → Redis single-node throughput ~500K ops/sec → 60% utilization at peak; acceptable but leaves no headroom
- Stage 3 (LongAdder 10s batch): 300K impressions spread across 10s = 30K → 10K DSPs → 1 INCRBY per DSP per 10s = **1,000 Redis ops/sec** — 300× reduction

**Redis memory for counter keys:**
- Per DSP per minute: 2 keys (counter + tier config) × 100 bytes = 200 bytes
- Total: 10,000 DSPs × 200 bytes = **2 MB** — trivially small; the hot key problem is CPU, not memory

**Rate limit decision latency:**
- Redis RTT within same AZ: 0.1–0.3ms; Lua script execution: ~0.2ms → total: **~0.5ms per gate check**
- 0.5ms ≪ 5ms budget → well within RTB window

**Key conclusions:**
- "At 300K req/sec, per-request Redis INCR is feasible but runs at 60% of a single Redis node's capacity — no room for traffic spikes. Stage 3 (LongAdder batching) reduces Redis load to 1K ops/sec — 300× headroom."
- "The entire counter state for 10K DSPs fits in 2 MB — Redis memory is never the concern; CPU and network RTT are."
- "50 in-memory token buckets per JVM × 10K DSPs × 100 bytes = 50 MB heap — trivial. The JVM cache is the fast path; Redis is the shared truth."

---

## Section 5 — 🔄 Requirements Variation Table ⭐

| If the interviewer says... | Your architecture changes to... | The reasoning |
|---|---|---|
| "Per-second limits (not per-minute)" | Token bucket with per-second window: key = `rate_limit:{dspId}:{epochSecond}`; TTL = 2 seconds. Smaller key granularity, more responsive to spikes. | Second-level windows catch burst spikes within a minute that per-minute windows miss. Two-second TTL covers race at window boundary. |
| "100K DSP accounts instead of 10K" | Redis keys still trivially small (20 MB). Bottleneck shifts to JVM `ConcurrentHashMap` size (100K entries × 100 bytes = 10 MB — still fine). Counter lookup stays O(1). | DSP count scales comfortably in Redis and JVM. The hot key problem is per-DSP traffic, not total DSP count. |
| "0% overages — exact quota enforcement required" | Redis Lua script with SETNX initialization (no INCR race); OR Redlock distributed lock. Gate check latency doubles to ~1ms. Fail-closed on Redis outage. | CP over AP for the pacing gate — reverse of the D2 ad pacing decision. Reserved for DSPs with contractual zero-overage SLAs. |
| "IP-based rate limiting for DDoS protection at the edge" | Move to CDN/network layer (Cloudflare, AWS WAF) — not application-level. IP rate limiting in Redis: `INCR rate:{ipHash}:{minute}`, TTL 120s. Add NAT detection: if a single IP exceeds 10× average per-IP traffic, mark as suspected NAT and apply relaxed threshold. | IP-based limiting lives at the network layer for DDoS; application-level Redis rate limiting is for quota fairness. Two separate systems with different goals. |
| "Sliding window (not fixed window) for accuracy at boundaries" | Sliding window counter: `current_requests = prev_window_count × (1 - elapsed%) + current_window_count`. Requires two Redis GETs per request instead of one INCR. Accuracy within ~0.1% for most traffic patterns. | Fixed window allows 2× burst at minute boundary (last second of minute N + first second of minute N+1). Sliding window eliminates this — worth the extra GET for billing-critical DSP contracts. |
| "Multi-region — US and EU DSPs must share a global quota" | Home-region assignment (same as D2 global extension): each DSP account is assigned a home region at contract time; GeoDNS routes all that DSP's requests to its home region's rate limiter. Cross-region Redis sync is never on the hot path. | Cross-region Redis RTT is 80–150ms — blows the 5ms gate budget. Home-region stickiness keeps rate check local and sub-ms. Bounded drift: 1-second cross-region sync → at most 5K extra requests (at 300K/sec) before sync catches up. |
| "Frequency capping (max 3 ads per user per hour)" | Per-user Redis keys: `INCR freq:{adId}:{userId}:{hourBucket}` TTL 3,600s. Separate concern from DSP rate limiting (per-DSP quota vs per-user ad exposure). Add Bloom filter front-door: probabilistic check for users who are definitely under cap (0% false negatives) before going to Redis. | Per-user frequency capping is a different dimension — user count (150M DAU) × ads (10K) = 1.5T possible key pairs if per-user/per-ad; Bloom filter prunes Redis calls for the common "not yet capped" case. |

---

## Section 6 — 🏗️ High-Level Architecture (Minutes 8–20)

### 🎨 Visual — Three-Stage Architecture

```
══════════════════════════════════════════════════════════════════════
STAGE 1 — Naive: In-Memory Counter Per Server (breaks at ≥2 servers)
══════════════════════════════════════════════════════════════════════

DSP Bid Request
    │
    ▼
┌──────────────────────────────────────────────────────────────────┐
│  Ad Server #1                                                    │
│  Map<dspId, TokenBucket> buckets  ← in-JVM, zero network        │
│  bucket.tryConsume() → ALLOW / 429                               │
└──────────────────────────────────────────────────────────────────┘

KEY INVARIANT (Stage 1):
   Counter is local: fast, zero dependencies, zero network.
   Works perfectly for exactly ONE server.

BREAKING POINT: Stage 1 breaks the moment a second ad server is added.
  Each of the 50 ad server instances has its own in-memory bucket.
  A DSP hitting all 50 round-robin is seen as 10K req/min per server,
  so 50 × 10K = 500K req/min passes unthrottled (quota: 100K req/min).
  Observable: DSP exceeds quota 5× with zero 429 responses issued.
  Why Stage 2 is needed: all servers must share ONE counter.


══════════════════════════════════════════════════════════════════════
STAGE 2 — Shared Redis Counter (breaks at hot DSP: >100K INCR ops/sec)
══════════════════════════════════════════════════════════════════════

DSP Bid Request
    │
    ▼
┌──────────────────────────────────────┐
│  API Gateway                         │
│  extract dsp_account_id from JWT     │
│  route to Rate Limiter Service       │
└──────────────────────────────────────┘
    │
    ▼
┌──────────────────────────────────────┐     ┌─────────────────────────┐
│  Rate Limiter Service (stateless)    │────▶│     Redis Cluster        │
│                                      │◀────│                         │
│  1. hash(dspId) % N → shard routing │     │  key:                   │
│  2. Lua script (atomic):             │     │   rate:{dspId}:{minute} │
│     GET current count                │     │  value: 42,317          │
│     if count < limit:                │     │  TTL: 120s              │
│       INCR count                     │     │                         │
│       return ALLOW                   │     │  key:                   │
│     else:                            │     │   tier:{dspId}          │
│       return REJECT                  │     │  value: standard        │
│  3. On ALLOW: forward to ad auction  │     │                         │
│  4. On REJECT: return 429 + headers  │     └─────────────────────────┘
└──────────────────────────────────────┘

                                                 ┌──────────────────────┐
                    ALLOW │                       │     Backend           │
                          └──────────────────────▶   Ad Auction Engine  │
                                                  │   (RTB, <100ms)     │
                    REJECT │                       └──────────────────────┘
                           ▼
                    HTTP 429 Too Many Requests
                    Retry-After: {secs_until_window_reset}
                    X-RateLimit-Limit: 100000
                    X-RateLimit-Remaining: 0
                    X-RateLimit-Reset: {epoch_of_next_minute}

── Lua Script: Why Atomicity Matters ─────────────────────────────────

  ❌ WRONG — Two separate Redis ops (race condition):
  ┌──────────────────────────────────────────────────────────────┐
  │ Server A: GET count → 99,999 (under limit)                   │
  │ Server B: GET count → 99,999 (under limit — same read)       │
  │ Server A: INCR → 100,000 (ALLOW)                             │
  │ Server B: INCR → 100,001 (ALLOW — quota exceeded, BUG)       │
  └──────────────────────────────────────────────────────────────┘

  ✅ CORRECT — Lua script (single atomic operation in Redis):
  ┌──────────────────────────────────────────────────────────────┐
  │ local count = redis.call('GET', key)                         │
  │ count = count and tonumber(count) or 0                       │
  │ if count < limit then                                        │  ← all steps
  │     redis.call('INCR', key)                                  │     are one
  │     redis.call('EXPIRE', key, 120)                           │     atomic
  │     return 1  -- allow                                       │     Redis op
  │ else                                                         │
  │     return 0  -- reject                                      │
  │ end                                                          │
  └──────────────────────────────────────────────────────────────┘

KEY INVARIANT (Stage 2):
   Rate Limiter Service is stateless — any instance can serve any DSP.
   All shared state lives in Redis Cluster.
   Lua script makes check + increment ONE atomic operation:
   no server can interleave between the read and the write.

BREAKING POINT: Stage 2 breaks at a viral DSP spike (>100K INCR ops/sec).
  During ESPN Super Bowl, one DSP (The Trade Desk) fires 200K bid requests/sec.
  One Redis keyslot receives 200K INCR ops/sec.
  Redis single-key throughput: ~100K ops/sec before keyslot CPU saturates.
  Observable: Redis CPU alarm fires; INCR P99 climbs from 0.1ms to 5ms+;
  gate check bleeds into the RTB window for all DSPs on that keyslot.
  Why Stage 3 is needed: per-DSP Redis key must be sharded + batched.


══════════════════════════════════════════════════════════════════════
STAGE 3 — LongAdder In-JVM Batching (any DSP scale)
══════════════════════════════════════════════════════════════════════

DSP Bid Request
    │
    ▼
┌──────────────────────────────────────────────────────────────────┐
│  Rate Limiter Service JVM                                        │
│                                                                  │
│  Step 1 — Check local gate (read-only, zero Redis):             │
│    boolean underQuota = localAllowedFlags.get(dspId);           │
│    if (!underQuota) return REJECT;  // fast path: known blocked  │
│                                                                  │
│  Step 2 — Increment in-JVM LongAdder (zero network, zero CAS):  │
│    LongAdder counter = counters.computeIfAbsent(                 │
│        dspId, id -> new LongAdder());                            │
│    counter.increment();                                          │
│                                                                  │
│  Step 3 — Background flush every 10 seconds:                    │
│    long batch = counter.sumThenReset();  // atomic reset         │
│    INCRBY rate:{dspId}:{minute} {batch}                         │
│    long total = GET rate:{dspId}:{minute};                       │
│    if (total >= tier_limit) localAllowedFlags.set(dspId, false);│
└──────────────────────────────────────────────────────────────────┘

Redis load reduction:
  Stage 2: 300K INCR ops/sec (one per bid request)
  Stage 3: 1 INCRBY per DSP per 10s × 10K DSPs = 1,000 INCRBY ops/sec
         = 300× less Redis traffic

Trade-off: up to 10s lag before Redis reflects full count per DSP.
  During that window, per-JVM LongAdder holds the in-flight count.
  Worst case: 50 servers each accumulate 10s × (quota/50) extra requests
  before the flush reveals the DSP is over quota.
  Acceptable: we prefer <1% overages to blocking live sports ad auctions.
```

---

## Section 7 — 🔬 Core Component Deep Dives (Minutes 20–35)

### Deep Dive 1: Token Bucket — Java Implementation and Concurrency Traps

**Why this is the most critical component:**
The rate limiting algorithm determines whether the gate is accurate, memory-efficient, and safe under concurrent access. At 300K QPS on 50 servers, concurrency bugs are not edge cases — they are the steady state.

**Algorithm selection:**

| Algorithm | Accuracy | Memory per DSP | Burst? | Verdict for Disney |
|---|---|---|---|---|
| **Fixed Window Counter** | ❌ 2× burst at minute boundary | Very low (1 int) | ❌ Boundary spike | Acceptable if boundary abuse is not a concern |
| **Sliding Window Log** | ✅ Exact | ❌ 1 entry per request | ✅ Smooth | 300K entries/sec/DSP → OOM. Never. |
| **Sliding Window Counter** | ✅ ~99.9% accurate | Low (2 ints) | ✅ Approximate | Best accuracy without burst control |
| **Token Bucket** | ✅ Smooth | Low (1 counter + timestamp) | ✅ **Native burst** | ✅ Pick this — DSP traffic is bursty |
| **Leaky Bucket** | ✅ Smooth | Low (queue) | ❌ None — queues excess | Upstream traffic shaping, not RTB |

**Decision: Token Bucket** — live sports events cause DSP bid-request bursts (ad break starts: all DSPs fire simultaneously). Token bucket allows accumulated tokens to drain in a burst while still enforcing average rate. Sliding window log stores 300K timestamps/sec/DSP in memory — unacceptable.

**The correct Java implementation (Disney LLD-grade):**

```java
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

/**
 * One token bucket per DSP. Thread-safe via per-instance synchronized.
 *
 * WHY refillRatePerMs (not refillRatePerSecond):
 *   At 300K QPS, requests arrive every ~3 microseconds.
 *   Using elapsedSeconds = (now - last) / 1000 truncates to 0 for any
 *   gap under 1 second — tokens NEVER refill. Bucket drains to 0 and
 *   stays there permanently (starvation bug).
 *   refillRatePerMs uses millisecond granularity: gaps of 1ms+ add tokens.
 *   Still not perfect for microsecond gaps, but Stage 3 (LongAdder batching)
 *   means the bucket is only consulted every 10 seconds, not per request.
 */
public class TokenBucket {
    private final int capacity;
    private final double refillRatePerMs;
    private final AtomicLong availableTokens;
    // volatile: visible across threads without lock (JMM guarantee)
    private volatile long lastRefillTimeMs;

    public TokenBucket(int capacity, Duration windowSize) {
        this.capacity = capacity;
        // refill completely once per window
        this.refillRatePerMs = (double) capacity / windowSize.toMillis();
        this.availableTokens = new AtomicLong(capacity);
        this.lastRefillTimeMs = System.currentTimeMillis();
    }

    /**
     * Atomically refill and consume one token.
     *
     * WHY synchronized here (not AtomicLong.compareAndSet loop):
     *   refill + consume is a compound two-step operation.
     *   A single CAS cannot make two reads + two writes atomic.
     *   Per-bucket synchronized is correct: contention is per-DSP,
     *   not global. A single DSP's concurrent requests serialize here,
     *   which is acceptable — we're rate-limiting THAT DSP anyway.
     *
     * WHY NOT synchronized on the whole RateLimiterService:
     *   That would serialize ALL DSPs on one lock — 300K QPS x 50 servers
     *   all blocking on one mutex. Catastrophic.
     */
    public synchronized boolean tryConsume() {
        refill();
        long tokens = availableTokens.get();
        if (tokens > 0) {
            availableTokens.decrementAndGet();
            return true;
        }
        return false;
    }

    /**
     * Compute tokens earned since last refill.
     * Only advances lastRefillTimeMs when tokens are actually added —
     * prevents clock drift eating future tokens on low-traffic DSPs.
     */
    private void refill() {
        long nowMs = System.currentTimeMillis();
        long elapsedMs = nowMs - lastRefillTimeMs;
        if (elapsedMs > 0) {
            long tokensToAdd = (long) (elapsedMs * refillRatePerMs);
            if (tokensToAdd > 0) {
                long newTokens = Math.min(capacity,
                    availableTokens.get() + tokensToAdd);
                availableTokens.set(newTokens);
                // Only advance the clock when tokens were added.
                // If elapsedMs is too small to produce a token, leave
                // lastRefillTimeMs unchanged so the fractional time
                // accumulates correctly on the next call.
                lastRefillTimeMs = nowMs;
            }
        }
    }

    /**
     * Seconds until the next token is available.
     * Used to populate the Retry-After response header.
     *
     * WHY Math.max(1, ...): the minimum useful Retry-After is 1 second.
     *   Math.ceil(60ms / 1000) = 1 second for a 1000-req/min bucket.
     *   Integer truncation of sub-second values returns 0 — telling the
     *   client "retry immediately", which sends another rejected request
     *   and amplifies the load spike. Never return 0.
     */
    public int retryAfterSeconds() {
        double msUntilNextToken = 1.0 / refillRatePerMs;
        return (int) Math.max(1, Math.ceil(msUntilNextToken / 1000.0));
    }
}
```

**The Registry — per-DSP bucket lookup (the computeIfAbsent trap):**

```java
import java.util.concurrent.ConcurrentHashMap;

public class TokenBucketLimiter {

    // ConcurrentHashMap: per-segment locking on writes, lock-free reads.
    // Each DSP has its own bucket — different DSPs never contend.
    private final ConcurrentHashMap<String, TokenBucket> buckets =
        new ConcurrentHashMap<>();

    private final RateLimiterConfig config;

    public boolean allowRequest(String dspId) {
        // computeIfAbsent is ATOMIC for a given key:
        // if two threads arrive simultaneously for the same NEW dspId,
        // exactly ONE lambda executes and one bucket is created.
        //
        // containsKey() + put() is NOT atomic:
        // Thread A: containsKey returns false
        // Thread B: containsKey returns false
        // Thread A: put(new bucket) — tokens start at capacity
        // Thread B: put(new bucket) — OVERWRITES, resets tokens to capacity
        // Silent bug: DSP's token count resets mid-stream on first request.
        TokenBucket bucket = buckets.computeIfAbsent(
            dspId,
            id -> new TokenBucket(
                config.getLimitForDsp(id),
                config.getWindowSize()
            )
        );
        return bucket.tryConsume();
    }
}
```

**Memory growth — the unbounded ConcurrentHashMap problem:**

```
Scenario: 100K unique DSPs over a week
ConcurrentHashMap: 100K entries x ~200 bytes = 20 MB heap
But: DSPs that stopped sending bids 5 days ago still hold a bucket.
JVM never reclaims it — GC doesn't shrink a referenced map.

Fix: ScheduledExecutorService evicts DSPs inactive for 2x windowSize.
Production fix: replace with Caffeine cache:
  Caffeine.newBuilder()
      .expireAfterAccess(config.getWindowSize().multipliedBy(2))
      .build();
Caffeine uses a time-wheel internally — O(1) eviction, no map iteration.
```

---

### Deep Dive 2: Distributed Enforcement — Redis Cluster, Lua Scripts, and the CROSSSLOT Bug

**Why this is the second most critical:**
The Lua script is the correct solution for atomic check-increment. But in Redis Cluster (sharded across multiple nodes), a Lua script that touches two keys will fail with a `CROSSSLOT` error unless both keys hash to the same slot. This bug is invisible in development (single-node Redis has no slots) and catastrophic in production.

**The correct INCR-based Lua script (not DECR):**

```lua
-- In Redis, executed as a single atomic operation.
-- WHY INCR (not DECR-from-capacity):
--   DECR approach initializes key to capacity and decrements.
--   SETNX in DECR approach gives the first request a free pass
--   (returns capacity without decrementing on first-ever call).
--   INCR approach starts at 0 and counts up — no free-pass bug.

local key   = KEYS[1]              -- rate:{dspId}:{windowMinute}
local limit = tonumber(ARGV[1])    -- DSP quota for this tier
local ttl   = tonumber(ARGV[2])    -- 120 seconds (2-minute TTL)

local count = redis.call('GET', key)
count = count and tonumber(count) or 0

if count < limit then
    redis.call('INCR', key)
    redis.call('EXPIRE', key, ttl)
    return 1  -- allow
else
    return 0  -- reject
end
```

**The CROSSSLOT bug — and the hash tag fix:**

```java
// WRONG — in Redis Cluster, counter key and tier config key
// may hash to different shards. Lua script fails with:
// CROSSSLOT Keys in request don't hash to the same slot
//
String counterKey = "rate:" + dspId + ":" + windowMinute;
String tierKey    = "tier:" + dspId;

// CORRECT — hash tags force both keys to the same slot.
// Redis Cluster uses ONLY the portion inside {...} to compute the slot.
// Both keys hash on dspId alone — guaranteed same shard.
//
String counterKey = "rate:{" + dspId + "}:" + windowMinute;
String tierKey    = "tier:{" + dspId + "}";

// Now the Lua script can read tier config + INCR counter
// in one atomic call with zero CROSSSLOT risk.
```

**Why this bug is production-only:**
- Dev / CI environment: single Redis node, no clustering, no slots → Lua script runs fine.
- Production: Redis Cluster has 16,384 slots. Two keys with different hash tags can land on different nodes. The Lua script cannot execute across nodes. Error thrown at runtime, during a live sports event.
- **Rule:** Any Lua script that touches more than one key must use hash tags from day one. Retrofitting in production requires migrating all existing keys while keeping the service live.

**Redis Cluster shard routing:**

```java
public class RedisBackedRateLimiter {

    private final RedisClusterClient clusterClient;

    public boolean allowRequest(String dspId, int limit) {
        String windowMinute = String.valueOf(
            System.currentTimeMillis() / 60_000L);

        // Hash tags ensure both keys land on the same shard
        String counterKey = "rate:{" + dspId + "}:" + windowMinute;

        // evalSha executes the pre-loaded Lua script atomically.
        // No Java synchronization needed — Redis serializes all commands.
        Long result = clusterClient.evalSha(
            luaScriptSha,
            ScriptOutputType.INTEGER,
            new String[]{ counterKey },
            String.valueOf(limit),
            "120"
        );

        return result != null && result == 1L;
    }
}
```

**Fail-open with local fallback:**

```java
public class RateLimiterService {

    private final RedisBackedRateLimiter redisLimiter;
    private final ConcurrentHashMap<String, TokenBucket> localFallbacks =
        new ConcurrentHashMap<>();

    public boolean allowRequest(String dspId) {
        int limit = tierConfig.getLimitForDsp(dspId);
        try {
            return redisLimiter.allowRequest(dspId, limit);
        } catch (RedisException e) {
            // Fail-open: use local per-server bucket.
            // At 50 servers: effective limit = ~50x tier limit during outage.
            // Acceptable — blocking all DSPs during NBA Finals is worse.
            return localFallbacks
                .computeIfAbsent(dspId,
                    id -> new TokenBucket(limit, config.getWindowSize()))
                .tryConsume();
        }
    }
}
```

---

## Section 8 — 🌐 API Design

### 🧠 How to Derive These Endpoints

**"Rate limiter must check DSP quota before forwarding bid request"** → inline gateway middleware → `POST /v1/rate-limit/check`. The 429 response IS the primary contract — the body of the check endpoint is secondary.

**"DSP must know how much quota remains"** → read-only observability → `GET /v1/rate-limit/status/{dspId}`. Does NOT consume quota. Used by DSPs to implement client-side adaptive bidding (slow down before hitting 429).

**"The 429 response headers are the real API surface"** — most candidates say "return 429 if rate limited" and stop. The signal question: what does the DSP's client need to NOT retry immediately? → `Retry-After` (RFC 7231), `X-RateLimit-Remaining`, `X-RateLimit-Reset`.

---

### Core Endpoints

| Method | Path | Auth | Request | Response | Status Codes |
|---|---|---|---|---|---|
| `POST` | `/v1/rate-limit/check` | Bearer (DSP JWT) | `{ "dspId": "ttd-001", "bidRequestId": "uuid" }` | `{ "allowed": true/false, "remaining": 57402, "resetAt": 1753200060 }` | 200, 429 |
| `GET` | `/v1/rate-limit/status/{dspId}` | Bearer (DSP JWT) | — | `{ "tier": "standard", "limit": 100000, "remaining": 57402, "resetAt": ... }` | 200, 404, 401 |

**429 Response Contract (the real design deliverable):**

```
HTTP/1.1 429 Too Many Requests
Content-Type: application/json
Retry-After: 12
X-RateLimit-Limit: 100000
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1753200060

{
  "error": "RATE_LIMIT_EXCEEDED",
  "message": "DSP quota of 100,000 requests per minute exceeded.",
  "retryAfterSeconds": 12,
  "tier": "standard"
}
```

**Why `Retry-After` is non-negotiable:** Without it, a DSP's retry logic hammers the endpoint immediately after a 429 — each retry generates another 429, amplifying the spike that caused the throttle. A well-behaved DSP SDK parses `Retry-After: 12` and schedules its next batch 12 seconds out, breaking the retry storm.

---

### 🔍 Endpoint Stories

**`POST /v1/rate-limit/check`** — inline gateway middleware. Called before every bid request reaches the auction engine. Returns `allowed: true` or `allowed: false` with `remaining` and `resetAt`. Every call to this endpoint DOES consume quota. Idempotency does not apply — each call represents a distinct bid request.

**`GET /v1/rate-limit/status/{dspId}`** — quota observability, zero quota consumption. DSPs poll this before launching a burst bidding campaign. Returns the same fields as the 429 response body. Reads from Redis — never Cassandra (too slow for this read pattern).

---

## Section 9 — 🗄️ Data Model

### Redis Key Design (Gate State)

```
Per DSP per minute window:

  rate:{dspId}:{windowMinute}
    Type: String (integer — INCR counter)
    Written by: Rate Limiter Service (INCR via Lua on each allowed bid)
    TTL: 120 seconds (2x window — survives one missed minute boundary)
    Value: cumulative bid requests this minute
    Hash tag: {dspId} — forces co-location with tier key on same Redis shard

  tier:{dspId}
    Type: String (tier name: "premier" | "standard" | "test")
    Written by: Admin console at DSP contract signing
    TTL: none (permanent config)
    Hash tag: {dspId} — same shard as counter key (CROSSSLOT safety)

  windowMinute = epoch_ms / 60_000 (integer — unique per minute, no timezone)

Tier limits:
  premier:  1,000,000 req/min  (The Trade Desk, Google DV360)
  standard:   100,000 req/min  (mid-tier DSPs)
  test:        10,000 req/min  (integration testing, sandbox DSPs)
```

### Violation Log — Kafka to Cassandra

```
Kafka Topic: rate-limit-violations
  Partitioned by: dspId
  Retention: 30 days (contract dispute evidence)

Cassandra: dsp_violations
  CREATE TABLE dsp_violations (
      dsp_id        TEXT,
      date          DATE,
      minute_bucket BIGINT,
      violation_count INT,
      PRIMARY KEY ((dsp_id, date), minute_bucket)
  ) WITH CLUSTERING ORDER BY (minute_bucket ASC);
```

### Key Schema Decisions

- **Redis TTL = 120s (not 60s):** A 60s TTL risks the key expiring mid-minute if written at second 59. 120s is a 2x safety margin — the key always covers the full window.
- **windowMinute = epoch_ms / 60_000:** Integer division floors to minute boundary automatically. No timezone handling, no date formatting, no edge cases.
- **Hash tags `{dspId}`:** All keys for one DSP land on the same Redis Cluster slot, enabling Lua scripts to atomically read tier config and update counter in one round-trip.

---

## Section 10 — ⚠️ Trade-offs + Failure Modes (Minutes 35–45)

### Trade-off 1: Token Bucket (Fast, Burst-Friendly) vs Sliding Window Log (Accurate, Memory-Expensive)

- **Chose:** Token bucket with per-minute window.
- **Gain:** O(1) per request (one Redis Lua call). Burst-friendly: DSPs can fire bids in a burst at live sports event start and consume accumulated tokens, as long as average stays under quota. Memory: one counter per DSP per minute (100 bytes).
- **Lose:** Not perfectly accurate at window boundaries. A DSP at 99,999 req/min can fire 200K in a 2-minute span straddling the minute boundary.
- **Failure mode if wrong:** [Technical]: Sliding window log stores one timestamp per request. At 300K bid requests/sec/DSP (peak), that's 300K entries/sec in memory. 300K × 8 bytes = 2.4 MB per DSP per second. At 10K DSPs: 24 GB/sec memory churn → OOM within minutes. [Business]: Rate limiter crashes during Monday Night Football, disabling quota enforcement — contractual SLA violation with every DSP partner simultaneously.

### Trade-off 2: Strong Consistency (Redis Lua Atomic) vs Eventual Consistency (Local Counters + Async Sync)

- **Chose:** Strong consistency via Redis Lua script for normal operation; eventual consistency (local JVM buckets) only as Redis-failure fallback.
- **Gain:** Zero quota overages during normal operation. DSP contracts are honored precisely.
- **Lose:** Every bid check requires a Redis round-trip (~0.5ms). At 300K QPS this is 300K Redis operations per second — manageable with a sharded Redis Cluster, but Redis is on the critical path of every RTB decision.
- **Failure mode if wrong:** [Technical]: Local counters + 10-second async sync → 50 servers each allow N requests locally. Combined: 50×N requests before Redis sees the total. For a 100K req/min DSP: 50 servers × 100K/min × 10s / 60s = 833K requests in 10 seconds → 8× quota overage. [Business]: Auction fairness violated — smaller DSPs are starved of inventory. Disney receives complaints from multiple DSP partners simultaneously, triggering contract review and revenue loss.

### Trade-off 3: Fail-Open (Allow on Redis Down) vs Fail-Closed (Block on Redis Down)

- **Chose:** Fail-open — local token bucket per JVM, approximate quotas during Redis outage.
- **Gain:** Ad auctions continue uninterrupted during Redis outage. 99.99% Redis uptime = ~52 minutes downtime/year; fail-open means ~52 minutes of approximate (not absent) rate limiting per year.
- **Lose:** During Redis outage, effective limit is approximately 50× per-server limit. A Standard DSP (100K req/min tier) could temporarily bid at 5M req/min.
- **Failure mode if wrong:** [Technical]: Fail-closed + Redis outage → all 300K bid requests/sec receive 429 responses. Rate Limiter Service itself becomes the outage. [Business]: During a 5-minute Redis outage during NBA Finals Game 7: $14,400/min × 5 min = $72,000 in lost ad revenue, plus breach of all DSP availability SLAs. This is orders of magnitude worse than 5 minutes of approximate quota enforcement.

---

## Section 11 — 🏰 Disney-Specific Depth

### Live Sports Ad Break — The Thundering DSP Herd

When an ESPN NBA Finals timeout is called, Disney's ad insertion system signals "ad break" to all DSPs simultaneously. Every DSP fires bid requests in the same 2-second window:
- Before timeout: 10K bid requests/sec across all DSPs
- At timeout: 300K bid requests/sec spike within 5 seconds (30× spike)

The rate limiter must absorb this spike without crashing Redis or false-blocking legitimate DSP bids. The LongAdder in-JVM buffer is the shield: during the 5-second ramp, bid requests increment local LongAdders at zero Redis cost. The 10-second batch flush syncs to Redis after the spike subsides. DSPs bid freely during the ramp; the flush reveals which DSPs exceeded quota and sets their local gate to false for the remainder of the minute.

### Disney DSP Tier Design — Contract Value to Quota

```
Premier tier  ($500K+/year DSP contracts):  1,000,000 req/min
  The Trade Desk, Google DV360, Amazon DSP
  Bid on every ESPN live sports impression at 300K QPS

Standard tier ($50K-$500K/year):            100,000 req/min
  Mid-market DSPs bidding selectively

Test tier     (sandbox / integration):       10,000 req/min
  New DSP integrations, QA environments
  Rate-limited aggressively to prevent test traffic polluting live auctions
```

For the Disney-specific depth prompt:
> "The rate limiter tiers map directly to Disney's DSP contract SLAs. Premier DSPs have contracted minimum bid opportunity guarantees — a rate limit that's too low would mean Disney is in breach of its own contract. The Test tier is aggressively low to prevent CI/CD environments from polluting live auction data. I'd surface `X-RateLimit-Remaining` in every response so DSP clients can implement adaptive bidding — slowing their bid rate as they approach quota rather than hitting the wall at 0."

---

## Section 12 — 🔬 Where the Interviewer Will Probe

### Surface Probe (Tier 1 — every candidate gets this)

**Q: "Your 50 ad servers each have an in-memory token bucket. How does a DSP hit the global limit?"**
> Stage 1 in-memory design doesn't work — each server sees only its share of traffic. If 50 servers split 300K QPS evenly, each sees 6K/sec. A DSP limited to 100K req/min passes 6K/sec × 60s = 360K/min per server → global = 18M req/min, 180× the quota. That's why Stage 2 moves all counter state to Redis. Every server's Lua script increments the same Redis key — one shared counter, globally consistent.

### Surface Probe (Tier 1)

**Q: "What happens if Redis goes down?"**
> Fail-open. Rate Limiter Service catches the RedisException and falls back to a local in-memory TokenBucket per JVM. During Redis outage, each server enforces its own per-server limit — approximate, not global. At 50 servers the effective global limit is roughly 50× the tier limit. This is acceptable: Redis at 99.99% uptime = ~52 minutes downtime per year. During those 52 minutes, Disney prefers approximate quota enforcement over blocking all bid requests (100% revenue loss on affected ad breaks).

### Deep Probe (Tier 2 — tests real understanding)

**Q: "Your Lua script reads tier config and increments the counter. In Redis Cluster, those two keys might be on different shards. What happens?"**
> Redis Cluster returns `CROSSSLOT Keys in request don't hash to the same slot` — the Lua script fails. The fix is hash tags: `rate:{dspId}:{minute}` and `tier:{dspId}` — the `{dspId}` portion forces both keys to hash to the same slot, so the Lua script runs atomically. This bug is invisible in development (single-node Redis has no concept of slots) and only explodes in production Redis Cluster. I'd enforce hash tags in the key naming convention from day one — retrofitting in production requires migrating all existing keys while keeping the service live.

### Deep Probe (Tier 2)

**Q: "You use `elapsedMs × refillRatePerMs` for refill. At 300K QPS, requests arrive every 3 microseconds — that's 0.003ms. What does `(long)(0.003 × 0.01667)` equal?"**
> Zero — integer truncation. At 300K QPS the inter-request gap is sub-millisecond, so `elapsedMs = 0` for most calls and `tokensToAdd = 0`. The bucket never refills on the per-request path — permanent starvation. This is exactly why the implementation uses `refillRatePerMs` (fractional) AND only updates `lastRefillTimeMs` when `tokensToAdd > 0` — so fractional milliseconds accumulate correctly. More importantly, Stage 3's LongAdder batching means the token bucket is only consulted every 10 seconds — by then elapsed = 10,000ms, which generates real tokens with no truncation risk.

### Cross-Concept Probe (Tier 3 — separates senior candidates)

**Q: "Disney sells Premier DSPs a guaranteed 1M req/min quota. If one Redis Cluster node fails, that DSP's counter key might be unavailable. How do you handle this without blocking the DSP?"**
> Two approaches: (1) **Redis Cluster automatic failover** — promotes a replica to master in ~10–30 seconds. During that window, detect the missing shard via `JedisConnectionException` and fall back to local JVM TokenBucket for that specific DSP only. Other DSPs on healthy shards continue with strong consistency. (2) **Counter sharding across nodes** — split the Premier DSP's quota across all N shards at contract-setup time (e.g., 250K req/min per shard on a 4-shard cluster). If one shard fails, the DSP loses 25% of quota (750K effective) rather than 100%. Trade-off: each shard only knows its 250K slice, not the full 1M — global undercounting during partial failure. Approach (1) is simpler and correct for most cases; approach (2) is the high-availability pattern for contractually critical Premier DSPs.

---

## Section 13 — 🐞 Common Mistakes on This Question

- **Mistake: Proposing IP-based rate limiting without mentioning NAT** → **Why wrong:** Millions of users behind corporate proxies share one IP. Rate limiting by IP starves legitimate users. → **Say:** "IP-based limiting fails for CDN-proxied traffic and corporate NAT. I use DSP account ID from JWT — the billing unit and quota boundary."

- **Mistake: Using a global `synchronized` on the rate limiter class** → **Why wrong:** Serializes ALL DSPs on one lock. At 300K QPS, thread contention turns a 0.5ms Redis round-trip into a 100ms queue wait — destroys RTB deadlines. → **Say:** "`synchronized` is per-bucket (per DSP), not global. Different DSPs never contend."

- **Mistake: `elapsedSeconds = (now - last) / 1000` integer division** → **Why wrong:** At sub-second inter-request gaps, `elapsedSeconds = 0`, `tokensToAdd = 0`, clock never advances. Bucket drains to 0 and stays there — permanent starvation at high QPS. → **Say:** "I use `refillRatePerMs` (fractional double) and only advance `lastRefillTimeMs` when `tokensToAdd > 0`, so fractional milliseconds accumulate correctly."

- **Mistake: Using a DECR-based Lua script (init bucket to capacity, DECR per request)** → **Why wrong:** The SETNX initialization gives the very first request a free pass — returns capacity without decrementing. One request slips through uncounted. → **Say:** "INCR-based Lua: start at 0, count up. `if count < limit: INCR, ALLOW`. No free-pass on first request."

- **Mistake: Not using hash tags in Redis Cluster key design** → **Why wrong:** Lua script touching two keys on different shards returns `CROSSSLOT`. Invisible in dev (single-node), catastrophic in production Redis Cluster during a live event. → **Say:** "I use `{dspId}` hash tags on all related keys — `rate:{dspId}:{minute}` and `tier:{dspId}` — to guarantee they land on the same Redis Cluster slot."

- **Mistake: `retryAfterSeconds = 1000 / refillRatePerSecond / 1000` integer division** → **Why wrong:** For any rate > 1 req/sec, this returns 0 — telling DSPs to retry immediately. Amplifies the spike. → **Say:** "Minimum `Retry-After` is 1 second: `(int) Math.max(1, Math.ceil(1.0 / refillRatePerMs / 1000.0))`. Never return 0."

---

## Section 14 — 🧭 Disney Interview Signals Checklist

| Signal | Relevant? | How your design addresses it |
|---|---|---|
| **Guest-Centric Thinking** | ✅ | Fail-open ensures Disney viewers watching ESPN live sports never see an interruption caused by rate limiter infrastructure. A DSP being temporarily over-quota by 5% for 52 minutes per year is invisible to the viewer; blocking all ads during NBA Finals Game 7 causes $72K/5-min revenue loss and broadcast operations scramble. The business impact framing ("blocking is catastrophic; overage is contractually manageable") is the guest-centric argument. |
| **Technical Depth** | ✅ | Four specific bugs identified and fixed: (1) `elapsedSeconds` integer truncation → starvation at high QPS, fixed with `refillRatePerMs`; (2) DECR-based Lua gives first request free pass, fixed with INCR-from-0; (3) `retryAfterSeconds = 0` from integer division, fixed with `Math.max(1, ceil(...))`; (4) CROSSSLOT Redis Cluster bug, fixed with `{dspId}` hash tags. `computeIfAbsent` atomicity explained vs `containsKey + put` race. |
| **Imagination & Creativity** | ✅ | LongAdder-based adaptive gate: local JVM accumulates counts between 10s Redis flushes, reducing Redis ops from 300K/sec to 1K/sec (300× reduction). Local `localAllowedFlags` short-circuits the counter increment for known-blocked DSPs (zero-cost fast path). Tier design tied to DSP contract value. |
| **Trade-off Clarity** | ✅ | Three named trade-offs with specific numbers: (1) Token bucket O(1) vs sliding log OOM at 300K QPS. (2) Redis Lua strong consistency vs 8× overage with 10s local sync. (3) Fail-open 50× local limit vs $72K/5-min revenue loss during Redis outage. Each names the exact Disney business outcome. |
| **Scalability** | ✅ | Three-stage evolution with quantified breaking points: Stage 1 → Stage 2 at >1 server (per-server counter allows 50× global quota); Stage 2 → Stage 3 at >100K INCR ops/sec (Redis keyslot CPU saturation). Stage 3 scales to any DSP volume: 1K Redis ops/sec regardless of bid request rate. Redis Cluster adds nodes linearly. |
| **Reliability** | ✅ | Fail-open with local fallback: Redis outage → 50× temporary overages (acceptable) vs 100% rejection (catastrophic). Redis TTL 120s: stale keys auto-expire after 2 windows, no cleanup job. `computeIfAbsent` prevents duplicate bucket creation race. Kafka violation log retained 30 days for contract dispute evidence. |
| **Communication Clarity** | ✅ | Three-stage evolution (in-memory → Redis → LongAdder) tells a clear story. Each breaking point is a concrete observable event ("Redis CPU alarm fires; INCR P99 climbs from 0.1ms to 5ms+"). `Retry-After` explained as the mechanism preventing the retry storm — cause-and-effect visible to any engineer. |

---

## Section 15 — 🧾 TL;DR Answer Summary

> "Rate limiting for Disney's Ad Exchange throttles DSP bid requests by account ID (from JWT) using token bucket (O(1), burst-friendly for live sports spikes). For a single server, in-memory per-DSP buckets work. For 50 servers, state moves to Redis Cluster — a Lua script atomically checks and increments the counter in one round trip (~0.5ms). All related keys use `{dspId}` hash tags so the Lua script never hits a CROSSSLOT error in Redis Cluster. At hot-DSP scale (The Trade Desk at 300K QPS during Super Bowl), a LongAdder in-JVM accumulates counts and INCRBY-flushes every 10s, reducing Redis ops from 300K/sec to 1K/sec. Fail-open on Redis failure: local buckets enforce approximate quotas rather than blocking all ads. Four bugs to flag: `elapsedSeconds / 1000` integer truncation (starvation), DECR-based Lua free-pass on first request, `Retry-After: 0` from integer division, missing hash tags in Redis Cluster key design."

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 21, 2026 | **File created.** Disney R3 onsite — API Rate Limiter (HLD + Java LLD). Full 15-section solution. Disney context: per-DSP throttling for Ad Exchange at 300K QPS. Key technical decisions: token bucket (burst-friendly for live sports), Redis Cluster + Lua atomic INCR (strong consistency), LongAdder 10s batch flush (300× Redis load reduction), fail-open (blocking live sports > temporary overages). Bugs fixed vs source files: (1) C1 `elapsedSeconds` integer truncation → starvation at high QPS → fixed to `refillRatePerMs` fractional math; (2) C1 `retryAfterSeconds = 0` from integer division → thundering herd → fixed to `Math.max(1, ceil(...))`; (3) C1 DECR-based Lua free-pass → fixed to INCR-from-0; (4) CROSSSLOT Redis Cluster bug → fixed with `{dspId}` hash tags. Cross-reference: LLD-rate-limiter-java.md (concurrency depth), C1-rate-limiter.md (DocuSign HLD baseline), D2-ad-budget-pacing.md (LongAdder pattern). |

