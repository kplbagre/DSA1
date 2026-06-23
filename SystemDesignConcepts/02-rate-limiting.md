# Rate Limiting

> **Standard followed:** `SystemDesignConcepts/notes-standards.md`

---

## 🎯 Why This Matters

Rate limiting is the first line of defence for any public API. Without it, one bad client (or one bug in a client) can saturate your entire service — legitimate users get timeouts, your database gets hammered, your bill spikes. It appears in every system design interview that involves an API, and DocuSign interviewers have confirmed asking for it with algorithm-level depth (token bucket internals + how you identify clients in a microservices environment).

**Which round:** R2 System Design (Variant A — infrastructure, and Variant B — API design).
**Why senior engineers own this:** Junior engineers implement rate limiting. Senior engineers choose the right algorithm for the access pattern, handle the distributed case, and identify clients correctly without breaking behind a load balancer.

---

## 🧠 The Mental Model

Imagine a **nightclub with a strict bouncer**. The rule: no more than 10 people allowed in per hour. The bouncer enforces this rule — but he has 4 different methods, each with different trade-offs.

---

**Method 1 — Fixed Window (The Reset Clock):**
The bouncer has a counter and a clock. He resets the counter to 0 every hour on the dot — 9 PM, 10 PM, 11 PM. Simple rule: once the counter hits 10, no one else enters until the clock resets.

The problem: 9 people enter at 10:59 PM. The clock resets at 11:00 PM. 9 more enter at 11:01 PM. In 2 minutes, 18 people are inside — nearly double the limit. Anyone who knows the reset time can game it by rushing in just before and just after. This is called the **boundary spike**.

---

**Method 2 — Token Bucket (The Jar of Tokens):**
The bouncer has a jar that holds 10 tokens. Every 6 minutes, he drops 1 token in (10 tokens/hour = 1 every 6 min), but the jar never holds more than 10. Every person entering takes 1 token. No token → no entry.

The key behaviour: a tour bus of 8 tourists arrives after an idle hour — the jar is full, all 8 get in instantly. That's intentional **burst allowance**. If a second bus arrives right after, only 2 tokens remain, so only 2 enter. The jar refills slowly over time. This is right for APIs where honest burst is normal — e.g., a client that was silent for 10 minutes and then sends 10 requests at once.

---

**Method 3 — Sliding Window Log (The Timestamped Logbook):**
The bouncer writes the exact clock time of every person who entered into a logbook. When someone new arrives at 10:45 PM, he flips through and crosses out every entry older than 60 minutes (older than 9:45 PM), then counts what's left. If fewer than 10 entries remain, he allows entry and writes the new timestamp.

This is perfectly accurate — no boundary spike, no approximation. The window truly slides with every request. The cost: the logbook can get large under heavy traffic, because every request in the last 60 minutes needs a stored timestamp.

---

**Method 4 — Sliding Window Counter (Two-Bucket Estimate):**
Instead of the full logbook, the bouncer remembers only two numbers: how many people entered in the previous hour window (say, 7) and how many in the current window so far (say, 3). He calculates an estimate: "We're 45 minutes into the current hour, so 75% of the previous hour's count still 'overlaps' this window."

Estimate = 7 × 0.25 (old window's remaining fraction) + 3 (current window) = 4.75. Well under 10 — allow entry.

This is a pragmatic approximation. Not perfectly accurate (the estimate assumes the previous window's requests were spread evenly), but memory footprint is just 2 numbers per client. Good enough for most APIs.

---

**The key insight is:** Fixed window is the simplest but gets gamed at boundaries. Token bucket is the default choice — it handles burst gracefully and is easy to reason about. Sliding window log is the strictest and most accurate — right when one extra allowed call has a real cost (billing API). Sliding window counter is the memory-efficient compromise between token bucket and the logbook.

---

## 🎨 Visual — Token Bucket vs Sliding Window Log

```
TOKEN BUCKET
─────────────────────────────────────────────────────────────────

  Capacity = 5 tokens   Refill rate = 1 token / sec

  t=0s  ┌──────────────┐   Bucket starts full
        │ ● ● ● ● ●    │   5 tokens available
        └──────────────┘

  t=1s  3 requests arrive simultaneously
        │ ● ●          │   3 tokens consumed → 2 remain  ✅ allowed
        └──────────────┘

  t=2s  Refill fires → +1 token
        │ ● ● ●        │   3 tokens now
        └──────────────┘

  t=2s  4 more requests arrive
        │              │   only 3 tokens → 3 allowed, 1 rejected  ❌
        └──────────────┘

KEY INVARIANT:
   Burst is allowed up to bucket capacity.
   Steady-state throughput is capped at refill rate.
   A client that was quiet for N seconds has "saved up" N tokens.


SLIDING WINDOW LOG
─────────────────────────────────────────────────────────────────

  Limit = 3 requests per 60 seconds

  Sorted set (timestamps of recent requests):
  [10:00:05, 10:00:30, 10:00:55]   ← 3 entries, window not full

  New request arrives at 10:01:10:
  Step 1: Remove entries older than (10:01:10 - 60s) = 10:00:10
          → remove 10:00:05
          Remaining: [10:00:30, 10:00:55]  ← 2 entries

  Step 2: Count remaining = 2. Limit = 3. 2 < 3 → allow ✅
          Add 10:01:10 to set: [10:00:30, 10:00:55, 10:01:10]

  New request arrives at 10:01:15 (5 seconds later):
  Step 1: Remove older than 10:00:15 → remove 10:00:30
          Remaining: [10:00:55, 10:01:10]  ← 2 entries
  Step 2: Count = 2 < 3 → allow ✅

KEY INVARIANT:
   The window slides with every request — it is always "the last 60 seconds."
   No boundary spike. Accurate to the millisecond.
   Memory cost = number of requests in the window (can be large under traffic).
```

---

## ⚙️ How It Actually Works

### Algorithm 1 — Token Bucket (use for most APIs)

**Steps:**
1. **Store two values per client in Redis:** `tokens` (current count) and `last_refill_time`.
2. **On each request:** calculate how much time has passed since last refill. Add `elapsed × refill_rate` tokens (capped at `max_capacity`).
3. **If tokens ≥ 1:** decrement by 1, allow the request. Update state in Redis.
4. **If tokens < 1:** reject with HTTP 429. Optionally return `Retry-After` header.
5. **Atomic write:** steps 2-4 must be one atomic operation. Use a Lua script in Redis — never two separate commands.

```java
// Redis Lua script — runs atomically on the Redis server
// Called via: jedis.eval(SCRIPT, keys, args)
private static final String TOKEN_BUCKET_SCRIPT = """
    local key = KEYS[1]
    local capacity = tonumber(ARGV[1])
    local refillRate = tonumber(ARGV[2])
    local now = tonumber(ARGV[3])

    local bucket = redis.call('HMGET', key, 'tokens', 'lastRefill')
    local tokens = tonumber(bucket[1]) or capacity
    local lastRefill = tonumber(bucket[2]) or now

    -- Step 2: refill based on elapsed time
    local elapsed = now - lastRefill
    local refilled = math.min(capacity, tokens + elapsed * refillRate)

    -- Step 3: check and consume
    if refilled >= 1 then
        redis.call('HMSET', key, 'tokens', refilled - 1, 'lastRefill', now)
        redis.call('EXPIRE', key, 3600)
        return 1
    else
        return 0
    end
    """;

public boolean isAllowed(String clientId) {
    String key = "rate_limit:" + clientId;
    long now = System.currentTimeMillis() / 1000;
    Long result = (Long) jedis.eval(
        TOKEN_BUCKET_SCRIPT,
        List.of(key),
        List.of(
            String.valueOf(CAPACITY),
            String.valueOf(REFILL_RATE),
            String.valueOf(now)
        )
    );
    return result == 1L;
}
```

### Algorithm 2 — Sliding Window Log (use for billing/strict APIs)

**Steps:**
1. **Store a sorted set in Redis** keyed by `client_id`. Score = request timestamp.
2. **On each request:** remove all entries with score older than `now - windowSeconds`.
3. **Count remaining entries.** If count < limit, allow and add current timestamp to the set.
4. **If count ≥ limit:** reject with HTTP 429.

```java
public boolean isAllowed(String clientId) {
    String key = "sliding:" + clientId;
    long now = System.currentTimeMillis();
    long windowStart = now - WINDOW_MS;

    // Steps 2-4 in one Lua script for atomicity
    String script = """
        local key = KEYS[1]
        local now = tonumber(ARGV[1])
        local windowStart = tonumber(ARGV[2])
        local limit = tonumber(ARGV[3])

        -- Step 2: evict old entries
        redis.call('ZREMRANGEBYSCORE', key, '-inf', windowStart)

        -- Step 3: count
        local count = redis.call('ZCARD', key)

        -- Step 4: allow or reject
        if count < limit then
            redis.call('ZADD', key, now, now)
            redis.call('EXPIRE', key, 3600)
            return 1
        end
        return 0
        """;

    Long result = (Long) jedis.eval(
        script,
        List.of(key),
        List.of(String.valueOf(now), String.valueOf(windowStart), String.valueOf(LIMIT))
    );
    return result == 1L;
}
```

### What is Lua, and why does Redis use it?

**Lua** (pronounced "LOO-ah") is a lightweight scripting language. Redis has a built-in Lua interpreter — you send a Lua script to Redis and it runs entirely inside Redis, as one atomic unit. Think of it like a stored procedure in a database: instead of sending 3 separate SQL statements and hoping nothing interrupts them, you send the whole operation as one script that the DB runs without anyone else cutting in.

**In an interview, if asked:** "Lua is Redis's built-in scripting language. When you need multiple Redis operations to execute atomically — read, compute, write — you wrap them in a Lua script and call `EVAL`. Redis runs the entire script as a single command, so no other client can interleave. It's the standard pattern for rate limiting, distributed locking, and any multi-step Redis operation that must be race-condition-free."

### Why Lua? Why not just INCR + EXPIRE?

Two separate Redis commands are not atomic. Between the `INCR` and the `EXPIRE`, another server can also run `INCR`. Result: two concurrent requests each think they're the first — both get through, even if the limit is 1. The Lua script runs as a single atomic unit on the Redis server — no interleaving possible.

### Client Identification — Don't Use IP

In a microservices environment, IP-based rate limiting breaks immediately:
- All requests from a company's office network share one NAT IP
- Behind a load balancer, all internal services share the same outbound IP
- Your IP identifies a network, not a user

**What to use instead:**

| Context | Identifier | How to extract |
|---|---|---|
| Public REST API | API key | `Authorization: ApiKey abc123` header |
| OAuth2 / JWT API | `client_id` or `sub` claim | Decode JWT, extract claim |
| Internal microservices | Service identity from mTLS or JWT `iss` | mTLS certificate CN or JWT issuer |
| KYC-verified B2B (DocuSign case) | Verified business entity ID | Map API key → business entity in a lookup table |

**The KYC (Know Your Customer) layer:** A large DocuSign enterprise customer might have 50 developers, each with their own API key. Rate limiting per API key means the enterprise gets 50× the quota — not the intent. KYC maps all 50 keys to one verified business entity. The rate limit is applied at the entity level. This requires a lookup step: `api_key → entity_id → rate_limit_bucket`.

### Distributed Rate Limiting (Multiple API Servers)

```
               ┌─────────────┐
 Client ───▶   │ API Server 1 │──────┐
               └─────────────┘      │
                                     ▼
 Client ───▶   ┌─────────────┐   ┌──────────┐
               │ API Server 2 │──▶│  Redis   │  ← shared rate limit state
               └─────────────┘   └──────────┘
                                     ▲
 Client ───▶   ┌─────────────┐      │
               │ API Server 3 │──────┘
               └─────────────┘

KEY INVARIANT:
   All servers share one Redis — one counter per client.
   No server makes a rate limiting decision alone.
```

**The Redis SPOF problem:** If Redis goes down, you have two choices:
- **Fail open** (allow all traffic) — your API gets flooded, but it stays up
- **Fail closed** (reject all traffic) — your API is unreachable, but it's protected

For most APIs: fail open. For payment APIs: fail closed.

---

## 🏢 Real World — Where Companies Use This

- **DocuSign** — Rate limit per verified business entity (KYC), not per API key. 3,000 requests/hour per account with a burst limit of 500 per 30 seconds. Exceeding returns HTTP 429. JWT `sub` identifies the caller, not IP.
- **Stripe** — Token bucket per API key, with different limits per endpoint. `POST /charges` (write) has a lower limit than `GET /charges` (read). Returns `Retry-After` header so SDKs can back off correctly.
- **Razorpay / PhonePe** — Sliding window for payment APIs. Exact accuracy matters — an extra `POST /payment` call costs money. Token bucket would be too lenient.
- **Twitter / X API** — Fixed window with 15-minute intervals for the free tier. Developers know the reset time and batch calls accordingly. Simple to implement and communicate.
- **AWS API Gateway** — Token bucket at the account level and per-route. The burst limit is the bucket capacity; the rate limit is the refill rate. Same exact model as described above.
- **Swiggy order API** — Rate limit per restaurant partner dashboard session. Sliding window per `partner_id` extracted from OAuth token, not IP — same restaurant can have multiple browser tabs open.

---

## 🧭 When to Use vs When NOT to Use

| Use rate limiting when | Do NOT apply it when |
|---|---|
| API is public or exposed to third-party clients | It's an internal service called only by trusted services with circuit breakers already in place |
| You need to protect downstream services (DB, payment gateway) from overload | The bottleneck is not request volume — it's slow computation or external latency |
| Billing is usage-based and abuse would cost money | A single heavyweight request is the problem — rate limiting doesn't help, request timeouts do |
| You want to give paying tiers more quota (Bronze/Silver/Gold limits) | You want to limit total system load — use a queue + backpressure instead |

**The common mistake:** Rate limiting by IP address in a microservices system. Every internal service shares the same egress IP. Result: one service's traffic counts against another's quota. Always extract a logical client identity from the JWT or API key.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Protection from abuse, fair resource allocation across clients, cost control for usage-based billing |
| **You lose** | Extra Redis round-trip on every request (~1-2ms latency), Redis becomes a shared dependency — failure affects rate limiting decisions, operational complexity of managing quotas per tier |
| **Failure mode** | Redis goes down → either all requests are blocked (fail closed) or rate limiting silently stops working (fail open). Neither is good. Plan the failure mode explicitly before deploying. |

---

## 🔬 Interview Q&As

### Tier 1 — Surface Questions

### Q: "What is rate limiting and why do you need it?"
> Rate limiting controls how many requests a client can make in a time window. Without it, a single misbehaving client — whether a bug in an SDK, a DDoS, or a scraper — can exhaust your API's capacity and deny service to legitimate users. It also protects downstream services: if every inbound request fans out to 5 database queries, an unrestricted 10K req/sec client creates 50K queries/sec your DB didn't sign up for.

### Q: "What's the difference between token bucket and sliding window?"
> Token bucket allows burst — a client that was quiet for 10 seconds has saved up tokens and can send 10 requests at once. It's right for general-purpose APIs where honest burst is normal. Sliding window log is precise — it counts exactly how many requests happened in the last N seconds with no approximation. It's right for billing APIs or any context where one extra allowed call has a real cost. Token bucket is more forgiving; sliding window is more accurate but memory-intensive.

### Q: "How do you implement rate limiting across 10 API servers?"
> All 10 servers share a single Redis instance. Each request hits Redis to check and update the rate limit counter. The check-and-update must be atomic — a Lua script ensures no two concurrent requests race on the counter. The trade-off is that Redis becomes a shared dependency: if it goes down, you need a pre-decided policy — fail open (allow traffic, no rate limiting) or fail closed (reject all traffic). For most APIs, fail open is safer; for payment APIs, fail closed is safer.

### Q: "Walk me through designing a rate limiter for an API like DocuSign's."
> First, clarify: per API key, per user, or per business entity? For DocuSign, it's per verified business account (KYC layer) — a company with 10 developers shouldn't get 10× the quota. I'd use a token bucket with burst capacity matching their tier (Bronze: 100/hr, Gold: 10,000/hr). On every request, extract the JWT `client_id` claim, look it up in a mapping table to get the business entity ID, then run the Redis Lua script against that entity's bucket key. Respond with HTTP 429 + `Retry-After` header on rejection so SDKs back off cleanly.

---

### Tier 2 — Cross / Probe Questions

### Q: "Why not just use INCR + EXPIRE in Redis? Why do you need a Lua script?"
> `INCR` and `EXPIRE` are two separate commands — they're not atomic. If two API servers send `INCR` to Redis at the same moment, both read the count before either has written back. Both see count = 0, both increment to 1, both think they're the first request. You've now allowed 2 requests when your limit was 1. A Lua script executes atomically on the Redis server — it's a single indivisible operation, no interleaving possible. This isn't just a theory — at high concurrency it happens constantly.

### Q: "Your Redis node storing rate limit state goes down. What happens and what do you do?"
> Two choices, both bad in different ways. Fail open: all rate limiting silently stops — legitimate users get through but so does abuse. Fail closed: all requests are rejected — your API is effectively down. For most APIs, fail open is right — availability beats protection. For billing or payment APIs, fail closed. The decision must be made before deployment, not during the incident. You can also reduce blast radius by using Redis Cluster — partition clients across multiple Redis shards, so one node failure only disables rate limiting for a subset of clients.

### Q: "A client says your rate limiter is blocking them even though they're within quota. How do you debug?"
> Three likely causes. First: clock skew between API servers — if servers have different system times, the sliding window calculation is off. Fix: use Redis server time (`TIME` command in Lua) rather than application server time. Second: they share an IP with another client who is over quota — fix: ensure you're identifying by JWT claim, not IP. Third: the token bucket hasn't refilled yet — they burned their burst capacity and haven't waited for refill. The response headers (`X-RateLimit-Remaining`, `X-RateLimit-Reset`) should tell the client exactly what their state is.

### Q: "How do you handle rate limiting fairly when one client has 50 API keys (KYC case)?"
> Rate limiting per API key gives that client 50× the quota — not the intent. The fix is a KYC (Know Your Customer) identity layer: maintain a mapping table from `api_key → verified_business_entity_id`. On every request, look up the entity ID (cache this mapping in Redis or local memory — it's read-heavy, write-rare). Apply the rate limit against the entity ID bucket, not the API key. All 50 keys share one bucket. This requires an onboarding step where keys are registered to a verified entity — but it's the only way to enforce quotas per business contract rather than per credential.

### Q: "How is rate limiting different from circuit breaking? When do you need both?"
> Rate limiting protects your service FROM clients — it's inbound traffic control. Circuit breaking protects your service FROM downstream dependencies — it's outbound call control. When a downstream service (payment gateway, database) is slow or failing, the circuit breaker opens and stops sending calls, giving the downstream time to recover. Rate limiting doesn't help here — you could be within quota and still hammer a struggling DB. You typically need both: rate limiting at the API gateway (inbound) and circuit breakers at service-to-service calls (outbound).

---

## 🧾 TL;DR — One Interviewer-Ready Line

> *"I'd use a token bucket backed by a Redis Lua script for atomicity — token bucket for the burst tolerance, Lua for race-condition safety. The key decision before the algorithm is client identification: in a microservices environment, you identify by JWT claim or API key, never IP — and if it's a B2B API, you add a KYC layer to map keys to business entities so a company with 50 keys doesn't get 50× the quota."*

---

## 🔗 Related Concepts

- **`04-idempotency.md`** — Rate limiting rejects retried requests; idempotency makes retries safe. The two concepts are complementary: rate limiting without idempotency means clients retry rejected requests and storm the API further.
- **`06-distributed-locking.md`** — Both use Redis for distributed coordination. Rate limiter uses atomic Lua scripts; distributed lock uses SETNX + EXPIRE. Same Redis, different patterns.
- **`03-caching.md`** — The KYC mapping table (api_key → entity_id) should be cached in Redis. Cache invalidation on key rotation is a real operational concern.

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **"Rate Limiting Algorithms Explained"** — ByteByteGo (YouTube) | Visual animations of all 4 algorithms. Good for reinforcing what you just read. Search: "ByteByteGo rate limiting" | ~8 min |
| **hellointerview.com — Rate Limiter** | Full interview walkthrough with scoring rubric. URL: https://www.hellointerview.com/learn/system-design/answer-keys/rate-limiter | ~15 min |
| **"Rate Limiting"** — Arpit Bhayani (YouTube) | Redis atomicity and distributed sharding depth. Search: "Arpit Bhayani rate limiting" | ~20 min |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. DocuSign R2 prep — confirmed asked with JWT + KYC depth. Sources: ByteByteGo, hellointerview.com, Arpit Bhayani, DocuSign API docs. |
