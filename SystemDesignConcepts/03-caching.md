# Caching

> **Standard followed:** `SystemDesignConcepts/notes-standards.md`

---

## 🎯 Why This Matters

Caching is the answer to "how do you scale reads?" — the question that follows every system design answer the moment you say "database." A single Postgres node handles roughly 10,000 read queries per second at moderate complexity. Redis handles 100,000+ per second. Every design interview that involves a read-heavy system (user profiles, product catalog, feed, search results) will surface caching. Senior engineers are expected to name the caching strategy (not just "use Redis"), the eviction policy, and what breaks when the cache is stale.

**Which round:** R2 System Design — "how do you scale your reads?" follow-up on almost every answer.
**Why senior engineers own this:** Junior engineers add Redis and call it done. Senior engineers know: (1) which strategy fills the cache (cache-aside? write-through?), (2) what happens when a cache entry expires under high traffic (stampede), and (3) which data must never be cached (frequently-changing, financially sensitive).

---

## 🧠 The Mental Model

Think of caching as a **personal assistant with a notepad**.

You're a busy executive. Every time someone asks you a question, you have to look it up in a huge filing cabinet (the database) in another room — takes 30 seconds each time. So you hire an assistant who sits right next to you with a notepad (the cache).

**How it works:**
- First time someone asks "What's Kapil's phone number?" — you don't know, the assistant runs to the filing cabinet, finds it, brings it back, AND writes it on the notepad.
- Next time someone asks the same question — the assistant checks the notepad first. Found it in 1 second. You never went to the filing cabinet.
- That's a **cache hit**. The first trip was a **cache miss**.

**The three problems every caching system must solve:**

**Problem 1 — The notepad runs out of space (Eviction):**
The assistant can only hold 100 entries on the notepad. When entry 101 arrives, which old entry do you erase? The most common rule: erase the one that was used least recently — **LRU (Least Recently Used)**. The assumption: if you haven't looked something up in a while, you probably won't need it soon. Alternatives: LFU (Least Frequently Used — erase the thing asked for fewest times), or simple TTL expiry (erase after N minutes regardless of usage).

**Problem 2 — The notepad becomes stale (TTL and Invalidation):**
Kapil's phone number changes. The notepad still has the old one. Until the notepad entry expires (TTL — time to live), every request gets the wrong answer. You control how stale is "acceptable" by setting the TTL. Short TTL = fresh data, but more cache misses. Long TTL = fewer misses, but stale data risk.

**Problem 3 — The filing cabinet gets rushed when the notepad expires (Stampede):**
At 3 PM, 1,000 users simultaneously ask for the same thing. The notepad entry expired at 2:59 PM. All 1,000 requests miss the cache simultaneously and all run to the filing cabinet at once. The filing cabinet collapses under the load. This is the **cache stampede** (also called thundering herd). Fix: only let ONE request go to the DB and refill the cache — the other 999 wait. This is a **mutex lock on cache refill**.

**The key insight is:** A cache is a bet that reads are more frequent than changes. The moment that bet is wrong — frequently-updated data — a cache becomes a liability, not an asset.

---

## 🎨 Visual — Cache-Aside vs Write-Through + Stampede Problem

```
  CACHE-ASIDE (most common pattern)
  ─────────────────────────────────────────────────────────────────

  READ PATH:
  App ──▶ Cache ──▶ HIT? ──yes──▶ return to App  ✅

                       │
                       no (MISS)
                       ▼
                    Database ──▶ App stores result in Cache ──▶ return to App

  WRITE PATH:
  App ──▶ Database (write)
       └─▶ Cache.delete(key)   ← invalidate, NOT update
                                  (let next READ refill it from DB)

  KEY INVARIANT:
     App manages the cache explicitly. Cache is "lazy" — only filled on a miss.
     On write, DELETE the cache key (don't update it — avoids write-write races).


  WRITE-THROUGH (keeps cache warm)
  ─────────────────────────────────────────────────────────────────

  WRITE PATH:
  App ──▶ Cache.write(key, value)
       └─▶ Database (write)    ← BOTH writes happen synchronously

  READ PATH:
  App ──▶ Cache ──▶ always a HIT (cache is kept current)

  KEY INVARIANT:
     Every write goes to cache AND DB. Cache is always warm — no cold start problem.
     Cost: every write is slower (two writes instead of one).
     Risk: if DB write fails after cache write, cache has stale data.


  STAMPEDE PROBLEM + FIX
  ─────────────────────────────────────────────────────────────────

  WITHOUT protection:                  WITH mutex lock:
  ─────────────────────                ──────────────────
  t=0: Cache key expires               t=0: Cache key expires
  t=1: 1000 requests arrive            t=1: 1000 requests arrive
  t=1: All 1000 → DB miss              t=1: Request #1 gets lock → goes to DB
  t=1: DB gets 1000 simultaneous       t=1: Requests #2-1000 wait (or return stale)
       queries ──▶ DB crashes    ❌    t=2: DB returns → Request #1 fills cache
                                       t=2: Lock released, remaining get cache HIT ✅

  KEY INVARIANT:
     Cache stampede only happens when MANY requests miss the SAME key at the SAME time.
     Prevention: distributed lock (Redis SETNX) on the "refill" operation.
     Alternative: probabilistic early expiry — refresh BEFORE the TTL expires.
```

---

## ⚙️ How It Actually Works

### Strategy 1 — Cache-Aside (Lazy Loading)

**Steps:**
1. **On READ:** check cache for the key. If HIT, return immediately.
2. **If MISS:** query the DB, store the result in cache with a TTL, return the result.
3. **On WRITE:** write to DB, then DELETE the cache key (not update — delete forces refill on next read).

```java
public User getUser(long userId) {
    String cacheKey = "user:" + userId;

    // Step 1: check cache
    User cached = (User) redisTemplate.opsForValue().get(cacheKey);
    if (cached != null) {
        return cached;
    }

    // Step 2: cache miss — go to DB
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException("User not found: " + userId));

    // Step 2 cont: store in cache with TTL
    redisTemplate.opsForValue().set(cacheKey, user, Duration.ofMinutes(30));
    return user;
}

public void updateUser(long userId, UpdateUserRequest req) {
    userRepository.save(req.toEntity(userId));

    // Step 3: DELETE on write — not update
    redisTemplate.delete("user:" + userId);
}
```

### What is Redis, and why does it fit here?

**Redis** (Remote Dictionary Server) is an in-memory key-value store — it keeps all its data in RAM, making reads and writes microseconds fast (vs milliseconds for a disk-based DB).

**In an interview, if asked:** "Redis is an in-memory data store — all data lives in RAM, so reads are ~100 microseconds vs ~1-10 milliseconds for a relational DB. For caching, I use Redis because it's single-threaded (no lock contention on reads), has built-in TTL expiry per key, supports multiple data structures (strings, sorted sets, hashes), and has Lua scripting for atomic multi-step operations."

---

### Strategy 2 — Write-Through (Keep Cache Always Warm)

**When to use:** Read-heavy dashboards, leaderboards, or any data where a cache miss is visibly painful to the user. You want zero cold-start misses — cache is always current.

**Steps:**
1. **On WRITE:** write to cache AND database synchronously — both in the same request.
2. **On READ:** always a cache hit (cache was updated on the last write). No miss logic needed.
3. **Risk:** if the DB write fails after the cache write, they diverge. Mitigate by writing to DB first, then cache — a cache-write failure is acceptable (next read refills), a DB-write failure must roll back.

```java
@Transactional
public User updateUserWriteThrough(long userId, UpdateUserRequest req) {
    // Step 1a: write to DB first
    User updated = userRepository.save(req.toEntity(userId));

    // Step 1b: write to cache — keep it warm
    String cacheKey = "user:" + userId;
    redisTemplate.opsForValue().set(cacheKey, updated, Duration.ofMinutes(30));

    // If cache write fails here, that is acceptable — DB is the source of truth.
    // Next read will miss and refill from DB.
    return updated;
}

// READ — always a hit because write-through keeps it warm
public User getUser(long userId) {
    String cacheKey = "user:" + userId;
    User cached = (User) redisTemplate.opsForValue().get(cacheKey);
    if (cached != null) {
        return cached;
    }
    // Cold start only — first request before any write has populated the cache
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException("User not found: " + userId));
    redisTemplate.opsForValue().set(cacheKey, user, Duration.ofMinutes(30));
    return user;
}
```

---

### Strategy 3 — Write-Behind / Write-Back (Async DB Write)

**When to use:** Write-heavy systems where DB write latency is the bottleneck — e.g., Twitter/X fan-out (one tweet → write to 50 followers' feed caches), analytics counters, feed generation. The client gets a fast response; the DB is updated asynchronously in the background.

**Steps:**
1. **On WRITE:** write to cache immediately, return success to client.
2. **Background job:** periodically flushes "dirty" cache entries to the DB.
3. **Risk:** if the cache crashes before the background flush, data is LOST. Only use when some data loss is acceptable (analytics counters, recommendation feeds) — NEVER for payments or orders.

```java
// Write-behind — return immediately, DB updated by background worker
public void incrementViewCount(long articleId) {
    String cacheKey = "views:" + articleId;

    // Step 1: update cache synchronously — client gets fast response
    redisTemplate.opsForValue().increment(cacheKey);

    // Mark as dirty for background flush (add to a "dirty keys" set)
    redisTemplate.opsForSet().add("dirty:views", String.valueOf(articleId));
}

// Background job — runs every 10 seconds, flushes dirty keys to DB
@Scheduled(fixedDelay = 10_000)
public void flushDirtyCountsToDb() {
    Set<String> dirtyIds = redisTemplate.opsForSet().members("dirty:views");
    if (dirtyIds == null || dirtyIds.isEmpty()) {
        return;
    }
    for (String articleId : dirtyIds) {
        Long count = redisTemplate.opsForValue().get("views:" + articleId) != null
            ? Long.parseLong(redisTemplate.opsForValue().get("views:" + articleId).toString())
            : 0L;
        articleRepository.updateViewCount(Long.parseLong(articleId), count);
    }
    redisTemplate.delete("dirty:views");
}
```

---

### Strategy 4 — Read-Through (Cache as Proxy)

**When to use:** You want the application code to stay simple — it only ever talks to the cache, never to the DB directly. The cache itself handles the miss and DB fetch transparently. Common in frameworks like Spring Cache with `@Cacheable`.

**How it differs from Cache-Aside:** In cache-aside, the APPLICATION decides to check cache, then query DB on miss. In read-through, the APPLICATION only queries the cache — the cache layer itself fetches from DB on a miss.

```java
// Read-through via Spring @Cacheable annotation — Spring manages the miss logic
@Service
public class UserService {

    // Spring checks the "users" cache for key=userId.
    // On HIT: returns cached value without calling the method body.
    // On MISS: calls the method body, caches the result, returns it.
    @Cacheable(value = "users", key = "#userId")
    public User getUser(long userId) {
        // This line only executes on a cache MISS
        return userRepository.findById(userId)
            .orElseThrow(() -> new NotFoundException("User not found: " + userId));
    }

    // @CacheEvict — remove from cache on update (same as cache-aside delete)
    @CacheEvict(value = "users", key = "#userId")
    public User updateUser(long userId, UpdateUserRequest req) {
        return userRepository.save(req.toEntity(userId));
    }
}
```

---

### Strategy 5 — Refresh-Ahead (Proactive Refill)

**When to use:** You know a cache entry will expire soon AND a miss would be expensive (stampede risk). Instead of waiting for the TTL to hit zero and then getting a miss, a background thread pre-fetches the value slightly before expiry. Common for session tokens, frequently-read configs, and home page data.

**How it works:**
- When a cache hit occurs and the TTL is below a threshold (e.g., below 20% of original TTL), trigger an async background refresh — the current request still gets the cached value.
- By the time the TTL expires, the cache is already warm with fresh data. Zero missed requests.

```java
public User getUserWithRefreshAhead(long userId) {
    String cacheKey = "user:" + userId;

    // Check cache — also check remaining TTL
    User cached = (User) redisTemplate.opsForValue().get(cacheKey);
    Long ttlSeconds = redisTemplate.getExpire(cacheKey, TimeUnit.SECONDS);

    if (cached != null) {
        // Trigger async refresh if TTL is below 20% of the 30-minute window (< 6 min)
        if (ttlSeconds != null && ttlSeconds < 360) {
            CompletableFuture.runAsync(() -> {
                User fresh = userRepository.findById(userId).orElse(null);
                if (fresh != null) {
                    redisTemplate.opsForValue().set(cacheKey, fresh, Duration.ofMinutes(30));
                }
            });
        }
        return cached;
    }

    // Full miss — fetch synchronously
    User user = userRepository.findById(userId)
        .orElseThrow(() -> new NotFoundException("User not found: " + userId));
    redisTemplate.opsForValue().set(cacheKey, user, Duration.ofMinutes(30));
    return user;
}
```

---

### All 5 Strategies — Quick Decision Table

| Strategy | Who fetches on miss? | Write path | When to use |
|---|---|---|---|
| **Cache-Aside** | Application | DB write + cache DELETE | Default choice. Simple, predictable. |
| **Write-Through** | Application (cold start only) | DB write + cache write | Read-heavy, cache miss is visibly painful. |
| **Write-Behind** | Application (async flush) | Cache write, DB async | Write-heavy, minor data loss acceptable. |
| **Read-Through** | Cache layer (transparent) | DB write + cache evict | Simpler app code, framework-managed. |
| **Refresh-Ahead** | Background thread (proactive) | DB write + cache evict | High-traffic keys, TTL expiry stampede risk. |

---

### Strategy 6 — Stampede Protection with Redis Lock

```java
public User getUserWithStampedeProtection(long userId) {
    String cacheKey = "user:" + userId;
    String lockKey = "lock:user:" + userId;

    // Check cache first (no lock needed for reads)
    User cached = (User) redisTemplate.opsForValue().get(cacheKey);
    if (cached != null) {
        return cached;
    }

    // Cache miss — try to acquire lock for DB fetch
    Boolean lockAcquired = redisTemplate.opsForValue()
        .setIfAbsent(lockKey, "1", Duration.ofSeconds(10));

    if (Boolean.TRUE.equals(lockAcquired)) {
        try {
            // This thread won the lock — go to DB and refill
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found: " + userId));
            redisTemplate.opsForValue().set(cacheKey, user, Duration.ofMinutes(30));
            return user;
        } finally {
            redisTemplate.delete(lockKey);
        }
    } else {
        // Another thread is already fetching — wait briefly and retry from cache
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return getUserWithStampedeProtection(userId);
    }
}
```

### What is `setIfAbsent` (SETNX), and why does it prevent the stampede?

**`setIfAbsent`** is Redis's atomic "set only if key does not exist" operation — known in Redis as `SETNX` (SET if Not eXists). It returns `true` if the key was set (you got the lock), `false` if the key already existed (someone else holds the lock).

**In an interview, if asked:** "SETNX is Redis's built-in compare-and-set — it atomically checks if a key exists and sets it in one operation. Because Redis is single-threaded, there is no race condition: exactly one caller gets `true`, all others get `false`. This is how we implement a distributed lock to ensure only one thread runs the expensive DB query on a cache miss."

---

### Eviction Policies — Which One to Pick

```
LRU  (Least Recently Used)  → Evict the key that was NOT accessed for the longest time
                               Best for: general-purpose caches (user sessions, product pages)

LFU  (Least Frequently Used) → Evict the key accessed fewest times
                               Best for: content where popularity matters (top-10 products
                               should stay, niche items can go)

TTL  (Time to Live)          → Evict after N seconds regardless of usage
                               Best for: data with a natural freshness window (stock price:
                               TTL 5 seconds, session token: TTL 30 minutes)

RANDOM                       → Evict a random key
                               Use only when access patterns are genuinely unpredictable
```

---

## 🏢 Real World — Where Companies Use This

- **DocuSign** — User session data (auth token, account metadata) cached in Redis per `session_id`. TTL matches token expiry (1 hour). On logout: delete the cache key immediately — no waiting for TTL to expire.
- **Swiggy** — Restaurant menu cached for 5 minutes. Cache-aside strategy: first request after restaurant updates their menu misses the cache, refills it, all subsequent requests hit cache. The 5-minute TTL is acceptable stale window because menu updates aren't real-time.
- **BookMyShow** — Movie show availability cached with a very short TTL (10 seconds) during flash sales. The business accepted that availability might be slightly stale — the final inventory check happens at booking time (in the DB, with locking), not at the "is this show available?" page load.
- **Flipkart** — Product detail pages (name, description, images) cached for 30 minutes. Write-through on product update — both cache and DB updated together. Images cached separately in CDN with 24-hour TTL.
- **Razorpay** — Payment method details (card metadata, UPI handles) cached per user. LFU eviction — actively used payment methods stay in cache; rarely-used ones evicted first.
- **Twitter / X** — Home feed cached per user (write-behind strategy for fan-out). When a user with 50 followers tweets, the tweet is written to cache of all 50 followers immediately — the DB write happens asynchronously in the background.

---

## 🧭 When to Use vs When NOT to Use

| Cache this data | Do NOT cache this data |
|---|---|
| Data that is read far more than it is written | Financial balances — must always be current |
| Data that is expensive to compute | Data that changes every second (live stock prices with sub-second precision) |
| Data that is the same for many users (product catalog) | Unique per-request data that will never be requested again |
| Session data (read on every authenticated request) | Data where stale = legal/compliance problem |

**The common mistake:** Caching data and then writing to cache AND database separately (not in a transaction). If the DB write fails, the cache now has data the DB doesn't. Cache and DB diverge. Always: write to DB first, then update/invalidate cache. If the cache update fails — that's fine, the next read refills it from the correct DB state.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Read latency drops from milliseconds to microseconds for hot keys. DB load drops dramatically — the same 10K req/sec to the API becomes 200 req/sec to the DB (for high cache-hit-rate data). |
| **You lose** | Stale data window (TTL-based staleness). Operational complexity — another service to operate, monitor, and fail-over. Cache invalidation bugs are notoriously hard to track down. |
| **Failure mode** | Cache cold start — after a Redis restart, all cache is empty. Every request misses and goes to the DB simultaneously. If the DB can't handle that sudden load, it falls over. Mitigation: cache warm-up on startup, or a circuit breaker that limits request rate to the DB. |

---

## 🔬 Interview Q&As

### Tier 1 — Surface Questions

### Q: "How do you scale reads in a system with a relational database?"
> First, add a read replica — the primary DB handles writes, read replicas handle reads, replication lag is typically <100ms. If that's still not enough, introduce a cache layer (Redis) in front of the DB for frequently-read, slowly-changing data. Cache-aside strategy: check Redis first, on miss query the read replica, store result in Redis with a TTL. For a product catalog read 50K times/day but updated once/hour — a 30-minute TTL means 99% of reads hit Redis, 1% hit the DB. The DB handles only cache misses and writes.

### Q: "What is LRU eviction and when would you use it?"
> LRU — Least Recently Used — evicts the key that was last accessed the longest time ago. The intuition: if you haven't used it in a while, you probably won't need it soon. Right for general-purpose caches where recent popularity predicts future demand — user sessions, recently-viewed product pages. Wrong for content where some items are always popular regardless of when last accessed (top-10 products should stay even if not accessed for an hour) — there, LFU is better.

### Q: "Walk me through cache-aside vs write-through vs write-behind."
> **Cache-aside** is lazy: on miss the app fetches from DB and populates cache; on write the app deletes the cache key. Simple, default choice. **Write-through** keeps cache always warm: every write goes to DB and cache synchronously. No cold start, but every write is slightly slower. **Write-behind** returns success to the client after writing to cache only — DB is updated asynchronously by a background job. Extremely fast writes, but data loss risk if the cache crashes before the flush. Write-behind is only acceptable for non-critical data like view counters or analytics — never for payments or orders. **Read-through** is cache-aside managed by the framework (e.g., `@Cacheable`) rather than by explicit app code — same semantics, less boilerplate. **Refresh-ahead** proactively refills cache before TTL expires to eliminate stampede risk on high-traffic keys.

---

### Tier 2 — Cross / Probe Questions

### Q: "Your cache just got evicted (Redis restarted). 50,000 concurrent users hit your homepage. What happens and how do you prevent it from taking down your DB?"
> This is cache stampede at scale — all 50K requests miss the cache simultaneously and hit the DB. The DB, which normally handles 200 req/sec with the cache warm, now gets 50K req/sec and falls over. Prevention: (1) Mutex lock on cache refill — only one request goes to the DB per cache key, others wait or serve stale. (2) Rate limit DB traffic — circuit breaker that allows only N req/sec to reach the DB. (3) Cache warm-up on startup — a background job pre-loads hot keys from DB before traffic is routed to the new cache. In production, you avoid the cold start entirely by using Redis persistence (AOF/RDB snapshots) — Redis reloads its data from disk on restart.

### Q: "You cached a user's data with a 30-minute TTL. The user deletes their account. Their cached data is still served for up to 30 minutes. How do you handle this?"
> On account deletion, explicitly delete the cache key immediately — don't wait for TTL expiry. `redisTemplate.delete("user:" + userId)` runs as part of the delete transaction. For data with stricter requirements (e.g., access control decisions — a revoked role should take effect immediately), use active invalidation: on every permission change, delete or update the relevant cache keys. TTL is a safety net, not the primary invalidation mechanism for security-sensitive data. Also: if the cache key holds composite data that other records depend on, invalidate those too — use a tag-based invalidation strategy or key prefix deletion.

### Q: "How does caching interact with horizontal scaling (10 API servers)?"
> Each API server needs to talk to the SAME cache, not a local in-process cache — otherwise server 1 might have a stale entry that server 2 has already invalidated. Use a shared Redis instance (or Redis Cluster for scale). The trade-off with in-process (JVM heap) caching is that you can't invalidate across nodes — server 1 writes, server 2 still has the old value for up to TTL duration. For data where cross-node stale reads are acceptable (CDN-style, public content), in-process L1 cache + shared Redis L2 cache is the fastest pattern. For user-specific or frequently-mutated data, shared Redis only.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> *"I use cache-aside with Redis for read-heavy data — check cache first, on miss load from DB and store with a TTL, on write delete the cache key so the next read refills from a clean DB state. The three things I explicitly call out: TTL length (how much staleness is acceptable), eviction policy (LRU for general, LFU for popularity-driven), and stampede protection on high-traffic keys."*

---

## 🔗 Related Concepts

- **`02-rate-limiting.md`** — The KYC mapping table (API key → business entity) mentioned there should be cached in Redis. Cache invalidation on key rotation is a real operational concern.
- **`06-distributed-locking.md`** — The stampede protection mutex uses Redis SETNX — the same primitive as distributed locking. The two concepts share the same Redis infrastructure.
- **`07-cdc-outbox.md`** — Write-behind caching (cache writes, DB writes asynchronously) is related to the outbox pattern — both decouple the write path from the DB commit.

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **ByteByteGo — "Top Caching Strategies"** (YouTube) | Visual walkthrough of cache-aside, write-through, write-behind, refresh-ahead with diagrams. Search: "ByteByteGo caching strategies" | ~10 min |
| **hellointerview.com — Caching** | Interview walkthrough with Redis specifics. URL: https://www.hellointerview.com/learn/system-design/deep-dives/redis | ~15 min |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. DocuSign R2 prep — "how do you scale reads?" follow-up. Covers cache-aside, write-through, LRU/LFU/TTL eviction, stampede protection, Redis. |
| June 2026 | Gap patch: added write-behind, read-through, refresh-ahead strategies with code. Added 5-strategy comparison decision table. Updated Q&A to cover all 5 strategies. |
