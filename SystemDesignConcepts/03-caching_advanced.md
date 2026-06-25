# Caching — Advanced Patterns

> **Standard followed:** `SystemDesignConcepts/notes-standards.md`
> **Companion to:** `03-caching.md` — Advanced variants of the 5 core caching strategies (cache-aside, write-through, write-behind, read-through, refresh-ahead)

---

## 🎯 Why This Matters

The core caching note covers the foundational patterns and stampede protection. But production systems encounter edge cases: How do you warm the cache on startup so the first request isn't a miss? How do you invalidate cache entries when a dependency updates (a user profile changes, a permission is revoked)? What happens when a query has no result — do you cache the emptiness? When you have 10 levels of caching (L1 CPU cache, L2, L3, RAM, local Redis, Redis cluster, CDN, browser cache), how do they coordinate? This advanced note covers patterns that senior engineers deploy when the basic approach breaks at scale.

---

## 🧠 The Mental Model

Extend the "assistant with a notepad" analogy from the core note:

You started with one assistant (one Redis node). Now you've scaled to 100 offices across the country (100 API servers), each with a local assistant. **Problem 1:** Do they all have the same notepad (a shared Redis cluster), or separate notepads (local Redis per server)? If separate, they disagree — one office has "Kapil's number is 555-1234" while another has "555-1235." **Problem 2:** When Kapil changes his number, how do you tell all 100 assistants to erase the old entry? You can't send a message to each one — that's 100 messages. **Problem 3:** When you ask for someone's phone number and they don't exist, does the assistant write "doesn't exist" on the notepad so future requests also get that answer fast, or does the assistant keep asking the filing cabinet every time?

These are the problems advanced caching solves.

---

## 🎨 Visual — Multi-Level Cache Hierarchy and Invalidation Flows

```
MULTI-LEVEL CACHE HIERARCHY
════════════════════════════

┌─────────────────────────────────────────────┐
│ L0: Browser Cache (HTTP headers)            │
│     e.g. Cache-Control: max-age=3600        │
│     User's browser ────▶ 1 second latency   │
└─────────────────────────────────────────────┘
                    ▲
                    │ miss
                    ▼
┌─────────────────────────────────────────────┐
│ L1: CDN (CloudFront, Akamai)                │
│     Cached at edge locations                │
│     Geographically distributed ────▶ 10ms   │
└─────────────────────────────────────────────┘
                    ▲
                    │ miss
                    ▼
┌─────────────────────────────────────────────┐
│ L2: Local Redis (per API server)            │
│     In-process cache or local TCP socket    │
│     Replicated from shared cluster ─▶ 1ms   │
└─────────────────────────────────────────────┘
                    ▲
                    │ miss
                    ▼
┌─────────────────────────────────────────────┐
│ L3: Shared Redis Cluster                    │
│     Global cache, all servers read/write    │
│     Multi-zone replication ────────▶ 10ms   │
└─────────────────────────────────────────────┘
                    ▲
                    │ miss
                    ▼
┌─────────────────────────────────────────────┐
│ L4: Database (Postgres/MySQL)               │
│     Source of truth                         │
│     Disk I/O ────────────────────▶ 10ms     │
└─────────────────────────────────────────────┘

KEY INVARIANT:
   Each level is a fallback for the level above.
   Invalidation must cascade top-down (browser ← CDN ← L2 ← L3).
   Write happens at the DB, then propagates back up.


CACHE INVALIDATION PATTERNS
═══════════════════════════════════════════════

PATTERN A: TTL-ONLY (simplest)
──────────────────────────────
Write to DB → let TTL expire on cache → next read refills
Result: data may be stale for up to TTL duration
Use: low-sensitivity data (product catalog)

PATTERN B: EXPLICIT DELETE ON WRITE (fast invalidation)
──────────────────────────────────────────────────────
Write to DB → DELETE cache key → next read refills
Result: always fresh after write
Cost: all 100 servers must know which keys to delete
Use: user profiles, permissions

PATTERN C: EVENT-DRIVEN INVALIDATION
────────────────────────────────────
Write to DB → emit event (Kafka, SNS) → all servers DELETE key
Result: eventual consistency (all servers eventually agree)
Cost: event infrastructure required
Benefit: decoupled invalidation (writer doesn't know about caches)
Use: complex systems where many services read/write the same data

PATTERN D: TAG-BASED INVALIDATION
────────────────────────────────
Cache keys: "user:123", "user:123:posts", "user:123:friends"
Tag: {userId: 123}
Write to DB (user 123 changes) → DELETE all keys with tag 123
Result: invalidate related keys atomically
Tool: Redis SCAN with pattern, or tagging library
Use: when one object affects multiple cache entries

PATTERN E: CONDITIONAL DELETE (smart invalidation)
────────────────────────────────────────────────
Cache query result with hash of inputs
Write to DB → check if ANY input changed → conditionally delete
Result: avoid deleting unrelated cache entries
Use: complex queries with multiple filter dimensions
```

---

## ⚙️ How It Actually Works

### Cache Warming — Preloading Before the First Miss

**Problem:** A system restarts or Redis cluster is recreated. The first 1,000 requests all miss the cache and hammer the database. This is the "cold start" problem.

**Solution:** Cache warming — preload critical data into the cache before serving traffic.

**Steps in plain English:**

1. **Identify critical data:** Which keys are queried first (user profiles, popular products, system config)?
2. **Trigger warming:** On startup, run a background job that loads and caches this data.
3. **Gradual or parallel:** Load in batches to avoid DB spike; use thread pools for parallelism.
4. **Health check before traffic:** Only begin serving requests after warming completes.

```java
@Service
@Slf4j
public class CacheWarmupService {

    private final UserRepository userRepository;
    private final RedisTemplate<String, User> redisTemplate;
    private final ExecutorService executorService = Executors.newFixedThreadPool(10);

    @EventListener(ApplicationReadyEvent.class)
    public void warmupCache() {
        log.info("Starting cache warmup...");
        
        // Load all active users (assumes manageable count for warmup)
        List<User> activeUsers = userRepository.findByStatusActive();
        
        // Batch warmup with parallelism to reduce time
        for (User user : activeUsers) {
            executorService.submit(() -> {
                String cacheKey = "user:" + user.getId();
                redisTemplate.opsForValue().set(cacheKey, user, Duration.ofHours(1));
            });
        }
        
        // Wait for all tasks to complete before returning
        // (Alternatively: continue in background and monitor)
        executorService.shutdown();
        try {
            executorService.awaitTermination(5, TimeUnit.MINUTES);
            log.info("Cache warmup completed");
        } catch (InterruptedException e) {
            log.warn("Cache warmup interrupted", e);
        }
    }
}
```

**Interview answer:** "I warm the cache on startup by running a background job that preloads critical data (user profiles, system config, popular items) before the service begins accepting traffic. This eliminates the cold-start spike where the first batch of requests miss the cache simultaneously. For large datasets, I load in parallel batches and only mark the service as 'ready' after warmup completes."

---

### Tag-Based (Dependency-Based) Cache Invalidation

**Problem:** User Alice's profile changes. This invalidates multiple cache keys: `user:alice`, `user:alice:posts`, `user:alice:followers`, `team:acme:member:alice`. If you manually delete each key, you'll miss some. If you delete too broadly, you invalidate unrelated data.

**Solution:** Tag cache entries — group related keys by a tag (e.g., `userId:alice`) — then invalidate all keys with that tag in one operation.

```java
@Service
public class UserService {

    private final UserRepository userRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    // Store user profile with a tag
    public void cacheUserProfile(Long userId) {
        User user = userRepository.findById(userId).orElseThrow();
        String cacheKey = "user:" + userId;
        
        redisTemplate.opsForValue().set(cacheKey, user);
        
        // Add this key to a tag set
        // Tag format: "tag:userId:123" → set of cache keys
        String tagKey = "tag:userId:" + userId;
        redisTemplate.opsForSet().add(tagKey, cacheKey);
    }

    @Transactional
    public void updateUser(Long userId, UpdateUserRequest req) {
        userRepository.save(req.toEntity(userId));
        
        // Invalidate ALL cache entries tagged with this user
        String tagKey = "tag:userId:" + userId;
        Set<Object> keysToInvalidate = redisTemplate.opsForSet().members(tagKey);
        
        if (!keysToInvalidate.isEmpty()) {
            redisTemplate.delete(keysToInvalidate);  // Delete all tagged keys
        }
        
        // Clean up the tag itself
        redisTemplate.delete(tagKey);
    }
}
```

**In an interview, if asked:** "I use tag-based invalidation for complex objects with multiple cache entries. A user profile update invalidates `user:id`, `user:id:posts`, `user:id:permissions` — all tagged under `userId:X`. In Redis, I store a set of cache keys per tag, then delete the entire set in one operation. This is more reliable than trying to remember all related keys manually."

---

### Negative Caching — Caching the Absence of Data

**Problem:** A request queries for a non-existent resource — user ID 99999 (doesn't exist). Without caching, every request for user 99999 hits the database, gets 0 rows, and returns 404. With negative caching, the first 404 is cached: "user 99999 does not exist" for 5 minutes. Subsequent requests get the cached 404 instantly.

**Steps in plain English:**

1. **Cache the 404 result** with a special marker (e.g., `{NOT_FOUND}` or a short TTL like 5 minutes).
2. **On write (if the missing resource is created)**, invalidate the negative cache entry.
3. **Balance TTL:** Negative cache TTL should be shorter than positive cache TTL — you want the "not found" to be forgotten faster.

```java
@Service
public class ProductService {

    private final ProductRepository productRepository;
    private final RedisTemplate<String, Object> redisTemplate;
    private static final String NOT_FOUND = "$$NOT_FOUND$$";
    private static final Duration POSITIVE_TTL = Duration.ofMinutes(30);
    private static final Duration NEGATIVE_TTL = Duration.ofMinutes(5);  // Shorter

    public Product getProduct(Long productId) {
        String cacheKey = "product:" + productId;
        
        // Check cache (may be a cached hit, miss, or cached 404)
        Object cached = redisTemplate.opsForValue().get(cacheKey);
        
        if (NOT_FOUND.equals(cached)) {
            // Negative cache hit — product doesn't exist
            throw new ProductNotFoundException("Product not found: " + productId);
        }
        if (cached instanceof Product) {
            // Positive cache hit
            return (Product) cached;
        }
        
        // Cache miss — query DB
        Product product = productRepository.findById(productId)
            .orElse(null);
        
        if (product == null) {
            // Cache the 404 — shorter TTL than positive cache
            redisTemplate.opsForValue().set(cacheKey, NOT_FOUND, NEGATIVE_TTL);
            throw new ProductNotFoundException("Product not found: " + productId);
        }
        
        // Positive cache
        redisTemplate.opsForValue().set(cacheKey, product, POSITIVE_TTL);
        return product;
    }

    @Transactional
    public void createProduct(CreateProductRequest req) {
        Product created = productRepository.save(req.toEntity());
        
        // Invalidate negative cache for this product ID (if it was cached as "not found")
        String cacheKey = "product:" + created.getId();
        redisTemplate.delete(cacheKey);
    }
}
```

**In an interview, if asked:** "I use negative caching for queries that often return 404 — caching the absence prevents repeated database hits. A product ID that doesn't exist is cached with a 5-minute TTL (shorter than the 30-minute TTL for found products). When the product is created later, I delete the negative cache entry so the new product is immediately visible. This is especially valuable for high-miss-rate queries like user ID lookups with random/sequential IDs."

---

### Multi-Level Cache Coherence

**Problem:** You have a shared Redis cluster (L3) AND local Redis instances on 10 API servers (L2). Server A updates the cache, Server B still has stale data. How do they stay in sync?

**Solution:** One-way or two-way coherence protocols:

- **Write-through to shared cache:** Every write goes to the shared cluster first. Local caches asynchronously sync from the shared cache via Pub/Sub.
- **Broadcast invalidation:** When a key changes on the shared cluster, publish an invalidation event. All local caches subscribe and delete the key.

```java
@Configuration
public class CacheCoherenceConfig {

    // When a key is written/invalidated on shared Redis,
    // publish a message so all servers sync
    @Bean
    public MessageListenerAdapter messageListenerAdapter(CacheCoherenceListener listener) {
        return new MessageListenerAdapter(listener, "invalidateLocalCache");
    }

    @Bean
    public RedisMessageListenerContainer redisContainer(
            RedisConnectionFactory connectionFactory,
            MessageListenerAdapter listenerAdapter) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listenerAdapter, new PatternTopic("cache:invalidate:*"));
        return container;
    }
}

@Component
@Slf4j
public class CacheCoherenceListener {

    private final RedisTemplate<String, Object> localCache;

    public void invalidateLocalCache(Message message, byte[] pattern) {
        String key = new String(message.getBody());
        log.debug("Received invalidation event for key: {}", key);
        
        // Delete from local cache
        localCache.delete(key);
    }
}

@Service
public class CoherentCacheService {

    private final RedisTemplate<String, Object> sharedRedis;
    private final RedisTemplate<String, Object> localRedis;

    public void updateProductInSharedCache(Long productId, Product product) {
        String cacheKey = "product:" + productId;
        
        // Write to shared Redis
        sharedRedis.opsForValue().set(cacheKey, product, Duration.ofMinutes(30));
        
        // Publish invalidation event to all subscribers
        sharedRedis.convertAndSend("cache:invalidate:" + productId, cacheKey);
    }
}
```

**In an interview, if asked:** "For multi-level caches, I use broadcast invalidation: when a key changes on the shared cluster, I publish an event that all local caches subscribe to. Each local cache receives the message and deletes the key. This keeps L2 (local) in sync with L3 (shared). Trade-off: Pub/Sub is reliable for recent changes but not durable — if a server restarts during an invalidation broadcast, it might miss the message. To handle this, I also use TTLs — entries eventually expire — combined with periodic full reconciliation for critical data."

---

## 🏢 Real World — Where Companies Use This

- **Shopify** (e-commerce): Multi-level caching with cache warming on startup — product catalog loaded into local Redis on each server, CDN layer for images. Negative caching for out-of-stock items (reduces DB hits when inventory is zero).
- **Netflix** (video platform): Tag-based invalidation for user preferences — when a user's watch history or ratings change, all dependent caches (recommendations, UI state) are invalidated via a single tag. Event-driven via Kafka.
- **Stripe** (payments): Hierarchical caches for API keys and permissions. When permissions change, broadcast invalidation to all regions. Negative caching for revoked keys (cached "key is invalid" to prevent re-querying HSM).
- **Uber** (ride-sharing): Local cache warming on driver app startup — nearby drivers, surge pricing. Broadcast invalidation when surge prices change (Redis Pub/Sub). Short negative TTL for "no drivers available in area" queries.
- **AWS DynamoDB**: DAX (DynamoDB Accelerator) is a managed multi-level cache — application talks to DAX (local), DAX talks to DynamoDB. Invalidation automatic on writes.

---

## 🧭 When to Use vs When NOT to Use

| Use advanced caching when | Do NOT use when |
|---|---|
| You have multi-level caches (local + shared + CDN) and need coherence | A single Redis instance is sufficient for your load — the complexity isn't worth it |
| Frequently-accessed data with complex dependency chains (e.g., user → posts → comments) | All queries are unique (uncacheable) or data changes constantly |
| You need to warm the cache on startup to prevent cold-start DB spike | Cold start happens rarely or your DB can handle the spike |
| False-negative queries are expensive (e.g., checking if an API key is valid) | The cost of a false miss is low (user can just retry) |
| You have different cache lifetimes for related data (positive TTL >> negative TTL) | One global TTL works fine for all your data |

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Cache warming eliminates cold start. Tag invalidation ensures consistency without manual key tracking. Negative caching reduces DB load for non-existent queries. Multi-level caching optimizes latency at every tier. |
| **You lose** | Complexity — cache coherence requires event infrastructure (Pub/Sub, Kafka). Memory overhead from multiple cache layers. Debugging cache inconsistency issues is harder (which layer is stale?). |
| **Failure mode** | Invalidation broadcast fails (Redis cluster restarts) — local caches stay stale until TTL expires. Negative cache prevents a newly-created resource from being visible until TTL expires. Tag-based invalidation requires careful tag design — a poorly-chosen tag invalidates too much. |

---

## 🔬 Interview Q&As

### Q: "You have 100 API servers, each with a local Redis cache, and a shared Redis cluster. User data changes on Server A. How do all 100 servers know to invalidate their local cache?"

> I use Redis Pub/Sub or Kafka: when Server A updates the shared cluster, it publishes an invalidation event ("invalidate:user:123"). All 100 servers subscribe to the invalidation channel and delete their local copy. This achieves eventual consistency with minimal latency. Trade-off: Pub/Sub is not durable — if a server restarts during the broadcast, it might miss the message. To handle this, I combine Pub/Sub with short TTLs, so even if a server misses the event, the stale entry expires in a few minutes.

---

### Q: "A user's request for a non-existent product (ID 99999) always returns 404. Without caching, this query hits the database every time. How do you optimize this?"

> Negative caching: I cache the 404 result with a shorter TTL than positive cache hits (e.g., 5 minutes vs 30 minutes). The first query for product 99999 hits the DB, gets 0 rows, and I store a marker (e.g., `{NOT_FOUND}`) in Redis with a 5-minute TTL. Subsequent requests return the cached 404 instantly. When product 99999 is actually created, I delete the negative cache entry. The shorter negative TTL ensures that newly-created resources become visible reasonably quickly without waiting for the full TTL to expire.

---

### Q (Tier 2): "At startup, your Redis cluster is empty and 50,000 users connect simultaneously. They all make the same query (e.g., 'fetch homepage feed'). What happens, and how do you prevent the stampede?"

> Two strategies: (1) **Cache warming** — before the service becomes healthy, preload the most common queries (popular feeds, system config, top products) into the cache. This eliminates the cold start. (2) **Stampede protection** — use a distributed lock (Redis SETNX) so only the first request refills the cache, and the others wait (or return stale). Combined: warm critical data on startup, use stampede protection as a fallback for non-warmed keys. The warm-up job itself needs to be fast — I load in parallel batches and only mark the service ready after critical data is cached.

---

### Q (Tier 2): "Your tag-based invalidation strategy is: when a user changes, delete all keys tagged with `userId:X`. What breaks if the tagging logic is wrong?"

> If I forget to tag a cache entry, it won't be invalidated when the user changes — stale data persists until TTL expires. If I tag too broadly (e.g., all keys with `company:Y` when only user X changed), I invalidate unrelated cached data and cause unnecessary cache misses. To prevent this: (1) centralize tag logic in one place (not scattered across services), (2) test tag coverage — ensure every cache write includes appropriate tags, (3) monitor tag cardinality — if a tag has 100,000 keys, invalidating it causes 100,000 deletes and temporary latency spike. For large cardinality tags, use time-based TTL instead of explicit invalidation.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> "For advanced caching: I warm the cache on startup to eliminate cold starts. I use tag-based invalidation for related cache entries (tag all entries for a user, delete the tag on update). Negative caching reduces DB hits for non-existent queries. Multi-level caches (local per-server + shared cluster + CDN) use Pub/Sub to broadcast invalidation and stay coherent. Shorter TTLs for negative cache entries ensure newly-created resources become visible quickly."

---

## 🔗 Related Concepts

- **`03-caching.md`** — the core caching note covers the 5 foundational strategies (cache-aside, write-through, write-behind, read-through, refresh-ahead) and stampede protection via SETNX lock. This companion extends with warming, invalidation, and multi-level coherence.
- **`07-cdc-outbox.md`** — event-driven cache invalidation via CDC (Change Data Capture) — an alternative to Pub/Sub for ensuring all cache layers eventually see updates.
- **`06-distributed-locking.md`** — when invalidating a tag with 100,000 keys, a distributed lock prevents concurrent invalidation operations from piling up on Redis.

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **"Advanced Caching Patterns"** — Arpit Bhayani (YouTube: search "Arpit Bhayani cache invalidation") | Deep dive on tag-based invalidation and event-driven cache strategies with real examples. | ~25 min |
| **"Caching Strategies"** — System Design Primer (https://github.com/donnemartin/system-design-primer#cache) | Structured comparison of warming, coherence, and multi-level caching with diagrams. | ~15 min read |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 23, 2026 | Companion file created. Covers: cache warming on startup (cold start prevention), tag-based (dependency-based) invalidation for atomic multi-key deletes, negative caching (caching 404s with short TTL), multi-level cache coherence (Pub/Sub broadcast invalidation), real-world patterns from Shopify/Netflix/Stripe. 3 Q&As (all advanced/Tier 2 scenarios). Pairs with core `03-caching.md`. |
