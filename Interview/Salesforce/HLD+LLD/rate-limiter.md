# Rate Limiter — HLD + LLD Combined Round

---

## 0.  Identity

| | |
|---|---|
| **Problem** | Distributed API Rate Limiter — throttle requests per client/tenant across a fleet of servers |
| **Format** | HLD+LLD combined (Salesforce SMTS), 90 min confirmed |
| **Time budget** | 35 min LLD -> 45 min HLD -> 10 min buffer |
| **Frequency rank** | **#3 pick** in `questions-by-frequency.md`. Appears in *both* the archived file (#3 HLD, #3 LLD) and fresh research (#3 HLD). Verbatim confirmations: CodingKaro Dec 2025 *"Design API Rate Limiter"*; Blind Aug 2025 (Backend SMTS, Bay Area) *"System design was to design a rate limiter and then a query executor."* |
| **Salesforce-specific angle** | This IS a Salesforce product concern — **API governor limits**. A real org gets a 24-hour rolling API call quota by edition, and Salesforce enforces per-org concurrent-request caps. Multi-tenant fairness isn't a bonus here; it's the actual problem. |

**Note on the Blind data point:** it was asked alongside a second design prompt in the same round, so scope tightly and be ready to hand back time.

---

## 1.  Dual-Layer Map

| HLD Box (system view) | LLD Class(es) (code view) | The interface that makes it swappable |
|---|---|---|
| API Gateway / filter tier | `RateLimitFilter`, `RateLimiterService` | `RateLimiter` |
| Algorithm choice (bucket vs window) | `TokenBucketLimiter`, `SlidingWindowLogLimiter`, `SlidingWindowCounterLimiter` | **`RateLimiter`** — the core Strategy |
| Rule lookup ("what limit applies?") | `RuleResolver`, `RateLimitRule` | `RuleRepository` |
| Counter storage (Redis) | `RedisCounterStore`, `InMemoryCounterStore` | **`CounterStore`** |
| Identity extraction (who is being limited?) | `ClientKeyResolver` | **`KeyResolver`** — by API key, user, IP, org |
| Response / headers | `RateLimitDecision` | — (value object) |
| Overflow behavior | `RejectStrategy`, `QueueStrategy`, `ShadowStrategy` | **`OverflowPolicy`** |

**The zoom sentence:** *"`TokenBucketLimiter` is a class doing arithmetic on two longs in LLD. In HLD it's a Lua script executing atomically inside Redis, because the moment you have 50 gateway pods, the bucket can't live in any one pod's heap."*

---

## 2.  LLD Half (target: 35 min)

### 2.1  Problem Statement

Design a rate limiter that decides, for each incoming request, whether to allow or reject it based on configurable per-client limits (e.g. 100 requests/minute), works correctly when many threads hit it concurrently, and supports multiple limiting algorithms.

### 2.2  Requirements

**Functional:**
- Decide allow/reject for a request given a client identity
- Support limits at different granularities: per API key, per user, per org, per endpoint
- Support multiple algorithms (token bucket, sliding window) — configurable per rule
- Return metadata: remaining quota, retry-after
- Support burst allowance distinct from sustained rate

**Non-Functional:**
- **Thread-safe** — one bucket is read/modified by many request threads at once
- **Low latency** — the limiter is on every request's hot path; must add < 1ms
- **Extensible** — new algorithm = one new class, no edits elsewhere
- **Fail-open by default** — the limiter must not become a single point of failure for the whole API

**Out of scope (say it):** DDoS/L3 protection (that's a CDN/WAF concern) and billing/quota accounting (different lifecycle, monthly not per-minute).

### 2.3  Class Design

#### 2.3.1  Deriving the classes (say this out loud, minutes 2-6)

| # | Requirement | Noun / variation point | Becomes | Why it earns its own type (and what breaks if you inline it) |
|---|---|---|---|---|
| 1 | "Decide allow/reject" | verb: *decide*, and the rule varies | **`RateLimiter`** (interface) | The **primary variation point**. Token bucket and sliding window answer the same question with completely different math and state. `if (algo == TOKEN_BUCKET)` in the hot path is both a perf smell and an OCP violation. |
| 2 | "allow or reject **plus** remaining/retry-after" | noun: *the decision* | **`RateLimitDecision`** (value object) | Returning a bare `boolean` throws away the metadata the caller needs for `X-RateLimit-Remaining` and `Retry-After` headers. Once you need three facts back, that's an object. Immutable — no reason to ever mutate a past decision. |
| 3 | "per API key, per user, per org, per endpoint" | verb: *identify who* | **`KeyResolver`** (interface) | Who gets limited is policy, not mechanism. Hardcoding `request.getApiKey()` means switching to per-org limiting edits the limiter itself. Also this is the seam where composite keys (`org:123:endpoint:/query`) get built. |
| 4 | "configurable per-client limits" | noun: *the limit config* | **`RateLimitRule`** (value object) | The `(limit, window, algorithm, burst)` tuple travels together and is looked up as a unit. Passing four loose params everywhere invites argument-order bugs. |
| 5 | same — "configurable" implies lookup | verb: *resolve which rule applies* | **`RuleResolver`** + `RuleRepository` | Rule matching has real logic (most-specific-wins: endpoint rule beats org rule beats global default). That precedence logic needs a home that isn't the limiter. |
| 6 | "works across many threads" + counters must live somewhere | noun: *the counter state* | **`CounterStore`** (interface) | **The most important abstraction for the HLD pivot.** In-memory today, Redis tomorrow — and that single swap is what takes this from single-node to distributed. If counter access is inlined as a `HashMap` field, the distributed version is a rewrite, not a config change. |
| 7 | "burst allowance distinct from sustained rate" | — | (fields on `RateLimitRule`) | Deliberately **not** a new class: burst is two numbers (`capacity`, `refillRate`) that only mean something together with the rule. A `BurstPolicy` class here would be ceremony with no behavior. **Say this out loud** — knowing when *not* to add a class is a signal. |
| 8 | "fail-open by default" | the *behavior on overflow/error* | **`OverflowPolicy`** (interface) | Reject vs queue vs shadow-mode (log only) are genuinely different behaviors, and shadow mode is how you safely roll out a new limit in production. Worth an interface. |

**One-liner after the table:** *"Two variation points drive everything: the algorithm (`RateLimiter`) and where counters live (`CounterStore`). Everything else is configuration and plumbing."*

#### 2.3.2  Entity fields

```
RateLimitRule                    <- immutable config
  - ruleId:      String
  - keyPattern:  String          <- "org:*", "apikey:*:endpoint:/query"
  - limit:       long            <- 100
  - window:      Duration        <- 1 minute
  - burstCapacity: long          <- token bucket only; >= limit
  - algorithm:   AlgorithmType

RateLimitDecision                <- immutable result
  - allowed:     boolean
  - remaining:   long
  - retryAfter:  Duration        <- null when allowed
  - ruleId:      String          <- which rule fired (debuggability)

TokenBucketState                 <- the mutable state, per key
  - tokens:         double       <- fractional: refill is continuous, not stepwise
  - lastRefillNanos: long

AlgorithmType (enum): TOKEN_BUCKET, SLIDING_WINDOW_LOG, SLIDING_WINDOW_COUNTER, FIXED_WINDOW
```

**Why `tokens` is a `double`:** refill is rate x elapsed-time, which is fractional. Truncating to `long` on every check silently loses tokens and under-delivers the configured rate — a subtle bug worth naming.

#### 2.3.3  Relationships — with the composition-vs-aggregation call made explicit

Rule of thumb, said out loud: *"If I `new` it inside the constructor, it's composition. If it arrives through the constructor, it's aggregation."*

| Relationship | Type | Composition or aggregation — and why that one |
|---|---|---|
| `TokenBucketLimiter` — `RateLimiter` | **IS-A** (implements) | Realization. Liskov: the service calls `tryAcquire()` never knowing which algorithm backs it. |
| `RateLimiterService` — `RateLimiter` | **HAS-A** -> **aggregation** | Injected, not constructed internally — and it's actually a `Map<AlgorithmType, RateLimiter>` of shared, stateless strategy singletons. Not composition: one `TokenBucketLimiter` instance serves every rule using that algorithm. |
| `TokenBucketLimiter` — `CounterStore` | **HAS-A** -> **aggregation** | Injected. The Redis-backed store is a shared connection-pooled singleton used by all limiters; the limiter must not own its lifecycle or it'd open a connection pool per algorithm. |
| `RateLimitRule` — `Duration` / `AlgorithmType` | **HAS-A** -> **composition** | Plain immutable value fields created with the rule and meaningless outside it. |
| `RateLimiterService` — `RateLimitDecision` | **CREATES** (factory-ish, not ownership) | The service produces decisions and hands them off; it holds no reference afterward. Worth distinguishing from HAS-A: a created-and-returned object is not a structural part. |
| `RuleResolver` — `RuleRepository` | **USES** (injected) | Collaborator; swapping a DB-backed rule store for a config-file one changes only wiring. |
| `TokenBucketState` — `TokenBucketLimiter` | **no reference either way** (deliberate) | State lives in the `CounterStore` keyed by client, **not** as a field on the limiter. This is the single most important structural decision here: a stateless limiter + externalized state is exactly what allows the HLD version to run on 50 pods. If state were a field, the class would be un-distributable. |

#### 2.3.4  ASCII class diagram — interfaces before implementations, always

```
                        RateLimiterService
                        - limiters:    Map<AlgorithmType, RateLimiter>
                        - ruleResolver: RuleResolver
                        - keyResolver:  KeyResolver
                        + check(Request): RateLimitDecision
                                 |  uses
        +------------------------+------------------------+
        v                        v                        v
  <<interface>>           <<interface>>            <<interface>>
  RateLimiter             KeyResolver              RuleResolver
  + tryAcquire(key,       + resolve(Request):      + resolve(key):
      rule): Decision         String                   RateLimitRule
        ^                        ^                        ^
        | implements             | implements             | implements
   +----+--------+-----------+   ApiKeyResolver      DefaultRuleResolver
   |             |           |   OrgKeyResolver      (most-specific-wins)
TokenBucket  SlidingWindow  SlidingWindow            CompositeKeyResolver
Limiter      LogLimiter     CounterLimiter
   |             |           |
   +------+------+-----------+
          | all depend on
          v
    <<interface>>
    CounterStore                       <-- THE seam that makes it distributed
    + increment(key, ttl): long
    + getState(key): BucketState
    + compareAndSet(key, old, new): boolean
    + evalAtomic(script, keys, args): long
          ^
          | implements
    +-----+---------------+
    |                     |
InMemoryCounterStore   RedisCounterStore
(ConcurrentHashMap)    (Lua script = atomic)
```

#### 2.3.5  Follow-ups they will ask after this section — and your answers

| Their question | Your answer (one breath) |
|---|---|
| "Why is bucket state in a `CounterStore` and not a field on the limiter?" | "Because that one decision is what lets the same class run distributed. A field means state is per-JVM, so 50 gateway pods enforce 50 independent limits. Externalizing state keeps the limiter stateless and swaps single-node for distributed by changing one injected implementation." |
| "Composition or aggregation between the service and its limiters?" | "Aggregation — they're injected, stateless, shared singletons. One `TokenBucketLimiter` serves every rule using that algorithm; the service doesn't own their lifecycle." |
| "Why return an object instead of a boolean?" | "Callers need `X-RateLimit-Remaining` and `Retry-After`. Once you need three facts back, a boolean forces either out-params or a second call to recompute state — both worse." |
| "Why is `tokens` a double?" | "Refill is rate x elapsed time, which is fractional. Truncating to long on each check leaks tokens and quietly enforces a lower rate than configured." |
| "Isn't `OverflowPolicy` over-engineering for allow/reject?" | "It earns its place through shadow mode — logging what *would* have been rejected without rejecting it. That's how you safely roll out a new limit against real traffic. Without it, every limit change is a guess in production. If we never needed shadow mode I'd collapse it to a boolean." |
| "Where do you enforce burst vs sustained rate?" | "Both live in the token bucket: `burstCapacity` is the bucket size, `limit/window` is the refill rate. That's the algorithm's main advantage over fixed window — burst is native, not bolted on." |

### 2.4  Key Interfaces

```java
/**
 * THE core abstraction. Note it takes the key AND the rule — the limiter is
 * stateless and per-call; all mutable state lives in the CounterStore.
 */
public interface RateLimiter {
    RateLimitDecision tryAcquire(String key, RateLimitRule rule);
    AlgorithmType getType();
}
```

```java
/**
 * The seam that makes this distributed. Swap InMemory -> Redis and the same
 * limiter classes now coordinate across the whole fleet.
 */
public interface CounterStore {
    long increment(String key, Duration ttl);
    Optional<TokenBucketState> getState(String key);
    boolean compareAndSet(String key, TokenBucketState expected, TokenBucketState updated);
}
```

```java
/** Who is being limited — API key, user, org, or a composite of them. */
public interface KeyResolver {
    String resolve(Request request);
}
```

```java
/** Most-specific-wins rule matching lives here, not in the limiter. */
public interface RuleResolver {
    RateLimitRule resolve(String key, String endpoint);
}
```

### 2.5  Design Decisions

**The question you must be ready for: "Which algorithm, and why?"** This is the whole problem. Have the comparison memorized cold:

| Algorithm | How it works | Memory/key | Burst? | The flaw that matters |
|---|---|---|---|---|
| **Fixed window** | counter per wall-clock window | O(1), tiny | no | **2x burst at the boundary** — 100 requests at 11:59:59 plus 100 at 12:00:00 = 200 in one second while "obeying" 100/min |
| **Sliding window log** | store timestamp of every request | **O(n) per key** — 100 limit = 100 timestamps | exact | Precise but memory scales with the limit; at 1M keys x 100 entries this is the expensive option |
| **Sliding window counter** | weighted blend of current + previous window | O(1), two counters | approximate | Slight inaccuracy at boundaries (assumes even distribution within the previous window) |
| **Token bucket** | tokens refill at a constant rate; each request takes one | O(1), two fields | **yes, natively** | Allows a full-capacity burst instantly after idle — usually a feature, occasionally a surprise |

**Decision:** **token bucket as the default**, sliding-window-counter available per rule. Reasoning to say aloud: *"Token bucket is O(1) memory, natively supports burst — which real APIs want — and the refill math is two fields. Sliding window log is the most accurate but its memory scales with the limit value, which at millions of keys is the wrong trade. Fixed window I'd reject outright because of the 2x boundary burst."*

**Why not just `@RateLimiter` from a library (Resilience4j/Guava)?** Say this proactively: *"Guava's `RateLimiter` is single-JVM and blocking by default — it makes callers wait rather than rejecting, which is wrong for an API tier. Resilience4j is also per-node. Any in-process library gives you N independent limits for N pods. The moment the requirement is 'per client across the fleet,' you need shared state, which is this design."*

| Decision | Pattern Chosen | Strongest Alternative Considered | Why the alternative loses |
|---|---|---|---|
| `RateLimiter` interface per algorithm | **Strategy** | One class with an `AlgorithmType` switch | The switch sits on the hottest path in the system and grows per algorithm; per-rule algorithm selection becomes a nested conditional |
| State in `CounterStore`, limiter stateless | **Externalized state / DIP** | Bucket state as a field (e.g. `Map<String,Bucket>` inside the limiter) | Works perfectly on one node and fails silently on N — each pod enforces its own limit, so actual throughput is N x configured. This is the #1 wrong answer for this problem |
| Immutable `RateLimitDecision` returned | **Value object** | `boolean` + out-params, or a mutable result | Loses the metadata needed for response headers; mutable results are a data race when a decision object is logged asynchronously |
| Fail-open on store outage | **Explicit failure policy** | Fail-closed (reject when Redis is down) | Fail-closed turns a Redis blip into a full API outage — the limiter becomes the SPOF it was meant to protect against. Fail-open risks brief over-admission, which is the cheaper failure. **Caveat to state:** for abuse-prevention limits (login attempts) I'd fail *closed*; the policy should be per-rule |
| `OverflowPolicy` interface | **Strategy** | `boolean rejectOnOverflow` flag | A flag can't express shadow mode (log-only), which is the safe way to roll out any new limit |

### 2.6  Visual — Object Interaction (one request)

```
Request arrives at RateLimitFilter
      |
      +--> KeyResolver.resolve(request)
      |        returns "org:00D5f:apikey:AK-91:endpoint:/query"
      |
      +--> RuleResolver.resolve(key, endpoint)
      |        most-specific-wins:
      |          1. endpoint-specific rule?   -> use it
      |          2. org-level rule?           -> use it
      |          3. global default            -> fallback
      |        returns RateLimitRule(limit=100, window=1m, TOKEN_BUCKET, burst=150)
      |
      +--> limiters.get(rule.algorithm)              [Strategy lookup, no switch]
      |        returns TokenBucketLimiter
      |
      +--> TokenBucketLimiter.tryAcquire(key, rule)
               |
               +--> CounterStore.getState(key)       [Redis GET / Lua]
               |        state = {tokens: 42.7, lastRefillNanos: T0}
               |
               +--> refill: elapsed = now - T0
               |            tokens = min(burst, tokens + elapsed * refillRate)
               |
               +--> if tokens >= 1:
               |        tokens -= 1
               |        CAS/Lua write back  --> ALLOWED  (remaining = floor(tokens))
               |    else:
               |        retryAfter = (1 - tokens) / refillRate
               |                                    --> REJECTED (429)
               |
               v
      RateLimitDecision
      |
      +-- allowed  --> forward to handler
      |               + headers: X-RateLimit-Remaining, X-RateLimit-Limit
      |
      +-- rejected --> 429 Too Many Requests
                      + headers: Retry-After
```

**Narrate this:** *"Refill is lazy — computed from elapsed time on read, not by a background thread ticking every bucket. With millions of keys, a refill thread would be the bottleneck; lazy refill means a key costs nothing until it's touched."*

### 2.7  Coding Skeleton

**Order:** enum -> value objects -> interface -> impl -> registry -> orchestrator.

```java
// 1. Enum first
public enum AlgorithmType { TOKEN_BUCKET, SLIDING_WINDOW_LOG, SLIDING_WINDOW_COUNTER, FIXED_WINDOW }

// 2. Immutable value objects
public final class RateLimitDecision {
    private final boolean allowed;
    private final long remaining;
    private final Duration retryAfter;

    private RateLimitDecision(boolean allowed, long remaining, Duration retryAfter) {
        this.allowed = allowed;
        this.remaining = remaining;
        this.retryAfter = retryAfter;
    }

    public static RateLimitDecision allow(long remaining) {
        return new RateLimitDecision(true, remaining, null);
    }

    public static RateLimitDecision reject(Duration retryAfter) {
        return new RateLimitDecision(false, 0, retryAfter);
    }

    public boolean isAllowed() { return allowed; }
    public long getRemaining() { return remaining; }
    public Duration getRetryAfter() { return retryAfter; }
}

// 3. Interface before implementation
public interface RateLimiter {
    RateLimitDecision tryAcquire(String key, RateLimitRule rule);
    AlgorithmType getType();
}

// 4. The implementation you write live — single-node version first, then
//    say "and here's the one line that makes it distributed"
public class TokenBucketLimiter implements RateLimiter {

    private final CounterStore store;

    public TokenBucketLimiter(CounterStore store) {
        this.store = store;   // injected: swap InMemory -> Redis, nothing else changes
    }

    @Override
    public AlgorithmType getType() { return AlgorithmType.TOKEN_BUCKET; }

    @Override
    public RateLimitDecision tryAcquire(String key, RateLimitRule rule) {
        double refillPerNano = (double) rule.getLimit() / rule.getWindow().toNanos();
        long now = System.nanoTime();

        // CAS retry loop — optimistic concurrency, no lock held across the store call
        for (int attempt = 0; attempt < 3; attempt++) {
            TokenBucketState current = store.getState(key)
                .orElseGet(() -> new TokenBucketState(rule.getBurstCapacity(), now));

            // Lazy refill: tokens earned since last touch
            long elapsed = now - current.getLastRefillNanos();
            double refilled = Math.min(
                rule.getBurstCapacity(),
                current.getTokens() + elapsed * refillPerNano
            );

            if (refilled < 1.0) {
                long nanosUntilOneToken = (long) ((1.0 - refilled) / refillPerNano);
                return RateLimitDecision.reject(Duration.ofNanos(nanosUntilOneToken));
            }

            TokenBucketState updated = new TokenBucketState(refilled - 1.0, now);
            if (store.compareAndSet(key, current, updated)) {
                return RateLimitDecision.allow((long) Math.floor(updated.getTokens()));
            }
            // CAS lost -> another thread moved the bucket; re-read and retry
        }
        // Contention beyond retry budget: fail open (see 2.5 for why)
        return RateLimitDecision.allow(0);
    }
}

// 5. Orchestrator — Strategy lookup, no switch
public class RateLimiterService {
    private final Map<AlgorithmType, RateLimiter> limiters;
    private final RuleResolver ruleResolver;
    private final KeyResolver keyResolver;

    public RateLimiterService(List<RateLimiter> limiterList,
                              RuleResolver ruleResolver,
                              KeyResolver keyResolver) {
        this.limiters = limiterList.stream()
            .collect(Collectors.toMap(RateLimiter::getType, l -> l));
        this.ruleResolver = ruleResolver;
        this.keyResolver = keyResolver;
    }

    public RateLimitDecision check(Request request) {
        String key = keyResolver.resolve(request);
        RateLimitRule rule = ruleResolver.resolve(key, request.getEndpoint());
        return limiters.get(rule.getAlgorithm()).tryAcquire(key, rule);
    }
}
```

**The line to say after writing the CAS loop:** *"This is correct on one node. On fifty nodes, `getState` + `compareAndSet` is two round trips with a race between them — which is exactly why the distributed version collapses this into one atomic Redis Lua script. Same class, different `CounterStore`."*

### 2.8  Concurrency — Making It Thread-Safe

| Race | Where | Fix | Why this fix |
|---|---|---|---|
| **Two threads refill/decrement the same bucket** | `TokenBucketState` for one key | **CAS retry loop** (optimistic) with `compareAndSet` | Lock-free: buckets are hot but contention per *individual key* is usually low. A `synchronized` block on the limiter would serialize **all** keys through one monitor — catastrophic on the request hot path |
| **Read-modify-write across a network** | `getState` then `compareAndSet` against Redis | **Single atomic Lua script** in the distributed version | Two round trips leave a window where another pod writes between them. Lua executes atomically inside Redis — one round trip, no window |
| **Rule config changed while requests in flight** | `RuleResolver` cache | Immutable `RateLimitRule` + atomic reference swap on reload | Requests in flight keep using the old rule object safely; no torn reads of a half-updated rule |
| **Registry mutated at runtime** | `RateLimiterService.limiters` | Built once in constructor, never mutated | Effectively immutable beats locking |

**Why not `synchronized` on the whole `tryAcquire`?** Say it explicitly: *"That would serialize every request in the process through a single monitor — the limiter becomes the bottleneck it exists to prevent. Per-key striped locks would be the middle ground, but CAS avoids locks entirely and degrades gracefully."*

### 2.9  "What Would You Do Differently?"

**I'd add a local pre-filter in front of Redis.** At high QPS, every request making a Redis round trip means the limiter adds ~0.5ms and Redis becomes a hard dependency on the hot path. A small per-pod in-memory cache of "this key is currently way under its limit" can skip the network call for the overwhelming majority of requests, syncing to Redis periodically. **The trade-off to state honestly:** it makes enforcement approximate (slight over-admission at boundaries) in exchange for large latency and load savings — a good trade for API quotas, a bad one for abuse prevention.

**Second:** I'd separate the limit *decision* from the limit *observation*. Emitting "how close is each tenant to their limit" as metrics lets you alert before customers get 429s, rather than finding out from a support ticket.

### 2.10  Interview Q&As (prep-only)

| Q | A |
|---|---|
| "How do you handle a client with no rule configured?" | "Global default rule as the last resort in the resolver chain — never unlimited. An unmatched key defaulting to unlimited is how one misconfigured client takes down the tier." |
| "Rate limit by IP or by API key?" | "API key when authenticated — IP punishes everyone behind a NAT/corporate proxy. IP only for unauthenticated endpoints like login, where it's the only identity available. Usually both, as separate rules." |
| "What if Redis goes down?" | "Fail open for quota-style limits — the limiter must not be a bigger outage than the thing it protects. But per-rule: abuse-prevention rules (login attempts) fail closed. I'd also degrade to per-pod local limiting as a middle ground rather than no limiting at all." |
| "Distributed clock skew?" | "Token bucket uses *elapsed* time from a stored timestamp, not absolute wall-clock agreement, so mild skew is harmless. For safety, do the time arithmetic inside the Redis Lua script using Redis's own `TIME` — single clock authority." |
| "How would you test this?" | "Fake the clock — inject a `Clock` rather than calling `System.nanoTime()` directly, so you can advance time deterministically instead of `Thread.sleep`-ing in tests. Concurrency-wise, hammer one key from N threads and assert the total allowed count never exceeds capacity." |
| "Client wants to know their limit before sending?" | "Return `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` on every response — that's why the decision is an object. A dedicated quota endpoint too, but headers are self-service and free." |

### 2.11  TL;DR — 30-Second Pitch (LLD)

`RateLimiter` is a Strategy interface with one implementation per algorithm — token bucket as default because it's O(1) memory and supports burst natively, while fixed window is rejected outright for its 2x boundary burst. The critical structural decision is that limiters are **stateless**: all bucket state lives behind a `CounterStore` interface, which is what allows the identical class to run single-node with a `ConcurrentHashMap` or fleet-wide with Redis. Thread safety uses a CAS retry loop rather than `synchronized`, because locking the limiter would serialize every request in the process through one monitor.

### 2.12  Patterns Used

| Pattern | Where | Why |
|---|---|---|
| **Strategy** | `RateLimiter`, `KeyResolver`, `OverflowPolicy` | Multiple interchangeable algorithms/policies selected at runtime |
| **Registry** | `RateLimiterService.limiters` map | Algorithm lookup without a switch on the hot path |
| **Value Object** | `RateLimitDecision`, `RateLimitRule` | Immutable, thread-safe by construction, safe to log/share |
| **Chain of Responsibility** (light) | `RuleResolver` most-specific-wins | Ordered fallback: endpoint -> org -> global default |
| **Dependency Inversion** | `CounterStore` | The single seam that turns single-node into distributed |

---

## 3.  HLD Half (target: 45 min)

### 3.1 Clarifying Questions (0-3 min)

| Question | Architectural Fork |
|---|---|
| "Scale — requests/sec across how many gateway nodes?" | 1K/sec on 3 nodes -> Redis handles it trivially, single instance. 1M/sec on 500 nodes -> Redis becomes the bottleneck; need sharding + local pre-filtering. |
| "Is approximate limiting acceptable, or must it be exact?" | Approximate -> local counters with async sync, sub-ms and cheap. Exact -> every request touches shared state, ~0.5ms and a hard Redis dependency. **This single question changes the entire architecture.** |
| "What's limited — per user, per org, per endpoint, or all three?" | One dimension -> one counter per key. Multi-dimensional -> N counter checks per request, and you must decide whether *all* must pass (they must) and what to do about partial consumption. |
| "What should happen when the limiter's own store is down?" | Fail open -> availability preserved, limits temporarily unenforced. Fail closed -> correctness preserved, but a Redis blip becomes a full API outage. |

### 3.2 Requirements

**Functional (5):**
- Allow/reject every inbound API request against configured limits
- Support per-org, per-user, per-API-key, per-endpoint granularity
- Return standard rate-limit headers and `429` with `Retry-After`
- Rules configurable at runtime without redeploying the gateway
- Shadow mode: evaluate and log without enforcing (safe rollout)

**Non-Functional (4):**
- Scale: **1M requests/sec** peak across ~500 gateway pods
- Added latency: **P99 < 1ms** (it's on every request)
- Availability: must never be a bigger outage source than the API it protects -> fail-open default
- Accuracy: within ~1% of configured limit is acceptable for quota rules

### 3.3 Core Entities

| Entity | Nature |
|---|---|
| **RateLimitRule** | transactional — small, rarely written, heavily cached |
| **CounterState** | ephemeral — TTL'd, reconstructible, never backed up |
| **RateLimitDecision** | ephemeral — computed per request, never stored |
| **ViolationEvent** | append-only — emitted on 429 for analytics/alerting |
| **TenantQuota** | transactional — per-org limits, the multi-tenant fairness input |

### 3.4 Scale Estimation

- **Throughput:** 1M req/sec peak. If every request does one Redis round trip, that's **1M Redis ops/sec** — a single Redis instance tops out around 100K ops/sec for Lua scripts, so this needs **~10-16 shards minimum**, or local pre-filtering to cut the volume.
- **Key cardinality:** 150K orgs x ~10 API keys x ~20 endpoints = **~30M distinct keys** worst case. At ~100 bytes/key (token bucket = 2 fields + overhead) that's **~3 GB** — comfortably in-memory, and TTLs evict idle keys so the working set is far smaller.
- **Latency budget:** Redis round trip ~0.3-0.5ms within an AZ. That is the entire P99 < 1ms budget, leaving no room for a second call — **which is why the read-modify-write must be one atomic Lua script, not GET-then-SET.**

### 3.5 Architecture Diagram

#### Stage 1 — Naive: in-process counters per gateway pod

```
   Client
     |
     v
  +--------------------+   +--------------------+   +--------------------+
  |  Gateway Pod 1     |   |  Gateway Pod 2     |   |  Gateway Pod 50    |
  |  ConcurrentHashMap |   |  ConcurrentHashMap |   |  ConcurrentHashMap |
  |  <key, Bucket>     |   |  <key, Bucket>     |   |  <key, Bucket>     |
  |  limit = 100/min   |   |  limit = 100/min   |   |  limit = 100/min   |
  +--------------------+   +--------------------+   +--------------------+
```

**BREAKING POINT 1 — the limit is silently multiplied by the pod count (the quantified one).** Each pod enforces 100/min independently. With 50 pods behind a round-robin load balancer, a client actually gets **5,000 requests/min — 50x the configured limit**. The system reports full compliance while being wrong by a factor of 50. This is the defining failure of this problem and the reason the whole design exists.

**BREAKING POINT 2 — autoscaling silently changes the limit.** Scale from 50 to 80 pods under load and the effective limit jumps to 8,000/min. The limit is now a function of your capacity, not your policy — exactly backwards, since traffic spikes are when limits matter most.

**BREAKING POINT 3 — state lost on deploy.** Rolling restart wipes every bucket; every client gets a fresh full allowance mid-window.

**DECISION — where does counter state live?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| In-process per pod | Zero latency; no dependency | Limit x N pods; breaks on autoscale; lost on deploy | Rejected — wrong by 50x |
| Sticky sessions (hash client -> pod) | Keeps state local and correct per client | Breaks on pod loss/rebalance; hot clients create hot pods; fights normal LB behavior | Rejected — fragile |
| **Central Redis with atomic Lua** | One source of truth; correct regardless of pod count; survives deploys | ~0.5ms per request; Redis is now on the hot path | **Chosen** |
| Redis + local pre-filter | Cuts Redis load by an order of magnitude | Approximate at boundaries | **Chosen as the scale-out step** (see Stage 3) |

#### Stage 2 — Central Redis, atomic Lua, fail-open

```
   Client
     |
     v
  +---------------------------------------------------------------+
  |                    Gateway Pods (x50)                         |
  |  +---------------------------------------------------------+  |
  |  | RateLimitFilter                                         |  |
  |  |   1. KeyResolver  -> "org:00D5f:ak:AK-91:/query"        |  |
  |  |   2. RuleResolver -> rule (from local cache, 30s TTL)   |  |
  |  |   3. one Lua call -> allow/reject                       |  |
  |  +---------------------------+-----------------------------+  |
  +------------------------------|--------------------------------+
                                 |  EVALSHA (single round trip,
                                 |  atomic refill+decrement)
                                 v
              +--------------------------------------+
              |     Redis Cluster (16 shards)        |
              |  key -> {tokens, lastRefillNanos}    |
              |  TTL = 2 x window (idle keys evict)  |
              |  hash-slot by client key             |
              +------------------+-------------------+
                                 |
                    +------------+-------------+
                    v                          v
        +----------------------+   +------------------------+
        | Rule Config Service  |   | Violation events ->    |
        | (Postgres + pub/sub  |   | Kafka -> analytics,    |
        |  invalidation)       |   | alerting, abuse detect |
        +----------------------+   +------------------------+
```

**Why Lua and not GET+SET:** the refill-check-decrement sequence must be atomic. Two round trips leave a race where two pods both read `tokens=1` and both allow. `EVALSHA` runs the whole sequence inside Redis in one shot — one network hop, no window.

**Why rules are cached locally with a 30s TTL:** rule lookups massively outnumber rule changes. Fetching rules from Postgres per request would add a second hot-path dependency; a local cache with pub/sub invalidation gives near-instant updates without per-request cost.

**BREAKING POINT (Stage 2) — Redis throughput and the hot-key problem.** A single Redis instance sustains roughly 100K Lua evals/sec; at 1M req/sec we're **10x over one instance**, hence 16 shards. But sharding by client key doesn't fix the **hot key**: one org sending 200K req/sec maps to exactly one hash slot on one shard, so that shard saturates while the other 15 idle. Symptoms: rising `EVALSHA` latency on one node, 429s arriving late, and eventually the fail-open path tripping for everyone on that shard. **Mitigations:** (a) shard-local sub-counters for known hot tenants (split one logical key into K physical keys, each with limit/K), and (b) the local pre-filter in Stage 3, which keeps most hot-tenant traffic off Redis entirely.

#### Stage 3 — Local pre-filter for hot keys (the scale-out step)

```
  Gateway Pod
  +-------------------------------------------------------+
  |  L1: local token allotment (per pod, per key)         |
  |      pod holds a lease on N tokens from the global    |
  |      bucket; serves them locally with zero network    |
  |      calls; refreshes asynchronously when depleted    |
  |                                                        |
  |      hit  -> decide locally, 0 network, ~0.01ms       |
  |      miss -> fall through to Redis Lua (Stage 2)      |
  +-----------------------------+-------------------------+
                                | async batch sync (every 100ms
                                |  or on depletion)
                                v
                        Redis Cluster (global truth)
```

**What this buys:** if a pod leases 20 tokens at a time, Redis traffic drops ~20x — from 1M/sec to ~50K/sec, back inside a single shard's comfort zone. **What it costs:** enforcement becomes approximate — in the worst case, 50 pods each holding an unused lease means up to `50 x leaseSize` tokens outstanding beyond the true limit. **State the trade honestly:** *"That's acceptable for quota limits where ~1% overshoot is fine; it's not acceptable for abuse prevention, so login-attempt rules should skip L1 and always hit Redis."*

### 3.6 Deep Dive: Atomicity Across the Fleet (the riskiest component)

**Why this one:** every other part degrades gracefully; this one fails *silently and incorrectly*. A non-atomic limiter reports success while admitting multiples of the configured rate.

**The Lua script (the heart of the whole design):**

```lua
-- KEYS[1] = bucket key,  ARGV = capacity, refillRate/sec, now_ms, requested
local capacity    = tonumber(ARGV[1])
local refillRate  = tonumber(ARGV[2])
local now         = tonumber(ARGV[3])
local requested   = tonumber(ARGV[4])

local bucket   = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
local tokens   = tonumber(bucket[1]) or capacity
local lastTs   = tonumber(bucket[2]) or now

-- lazy refill from elapsed time
local elapsed  = math.max(0, now - lastTs) / 1000
tokens = math.min(capacity, tokens + elapsed * refillRate)

local allowed = 0
if tokens >= requested then
    tokens = tokens - requested
    allowed = 1
end

redis.call('HMSET', KEYS[1], 'tokens', tokens, 'ts', now)
redis.call('PEXPIRE', KEYS[1], math.ceil((capacity / refillRate) * 2000))

return { allowed, math.floor(tokens) }
```

**Three details worth pointing at while you show it:**
1. **`now` is passed in, not read inside** — deliberately. Redis replicas would otherwise compute different times; passing the caller's timestamp keeps the script deterministic, which matters for replication safety.
2. **`PEXPIRE` on every write** — idle keys self-evict, so 30M possible keys never all exist at once. Without a TTL, memory grows unbounded with key cardinality.
3. **Returns remaining tokens** — so the gateway can emit `X-RateLimit-Remaining` without a second call.

**Options considered for cross-fleet atomicity:**

| Option | Pros | Cons |
|---|---|---|
| `INCR` + `EXPIRE` (fixed window) | Simplest possible; two commands | Not atomic together (crash between them = key with no TTL, leaks forever); and inherits the fixed-window 2x boundary burst |
| `WATCH`/`MULTI` optimistic transaction | No scripting | Round trips multiply under contention; hot keys retry-storm exactly when you need throughput most |
| **Lua `EVALSHA`** | Atomic, single round trip, cacheable by SHA | Script logic lives outside the app codebase; needs care to stay deterministic |
| Redis modules (e.g. throttle) | Purpose-built, well-tested | Requires installing modules — often unavailable on managed Redis |

**Decision:** Lua `EVALSHA`. Then add the honest caveat: *"Redis Cluster requires all keys in one script to hash to the same slot, so this works because each invocation touches exactly one key. Multi-dimensional limits — per-user AND per-org in one call — would need hash tags to co-locate them, or separate sequential calls."*

### 3.7 Trade-offs

**Trade-off 1: Exact (always Redis) vs approximate (local pre-filter)**
- **Chose:** approximate via L1 leases for quota rules; exact for abuse rules
- **Gain:** ~20x reduction in Redis ops and ~0.5ms off P99 for the common path
- **Lose:** transient over-admission bounded by `pods x leaseSize`
- **Failure mode if wrong:** applying L1 to a login-attempt limit means a credential-stuffing attacker gets `50 x leaseSize` attempts instead of 5 — turning a security control into a security hole. Rule-level opt-in, never global.

**Trade-off 2: Fail-open vs fail-closed on Redis outage**
- **Chose:** fail-open by default, fail-closed per-rule for abuse prevention
- **Gain:** a Redis incident degrades enforcement rather than taking down the API
- **Lose:** during the outage, clients can exceed limits freely
- **Failure mode if wrong:** fail-closed globally means a 30-second Redis failover returns 429 for **100% of API traffic across all 150K orgs** — the limiter causes a far larger incident than any abuse it would have prevented.

**Trade-off 3: Token bucket vs sliding window log**
- **Chose:** token bucket
- **Gain:** O(1) memory per key (2 fields vs N timestamps), native burst support, trivial Lua implementation
- **Lose:** slightly less precise than an exact log; allows a full burst immediately after idle
- **Failure mode if wrong:** sliding window log at 30M keys x 100-entry limits means storing up to 3B timestamps — Redis memory blows past hundreds of GB and the cost of exactness exceeds the value of the API being protected.

### 3.8 API Design

| Method | Path | Auth | Request | Response | Status Codes |
|---|---|---|---|---|---|
| — | *(any protected endpoint)* | JWT/API key | — | adds `X-RateLimit-Limit`, `X-RateLimit-Remaining`, `X-RateLimit-Reset` | 200, **429 + `Retry-After`** |
| GET | `/v1/rate-limits/me` | JWT | — | `{rules[]: {scope, limit, window, remaining, resetAt}}` | 200 |
| POST | `/v1/admin/rate-limit-rules` | Admin | `{keyPattern, limit, window, algorithm, burstCapacity, mode}` | `{ruleId}` | 201, 400 |
| PATCH | `/v1/admin/rate-limit-rules/{id}` | Admin | `{limit?, mode?}` (`mode`: ENFORCE \| SHADOW) | `{ruleId, ...}` | 200, 404 |
| GET | `/v1/admin/violations` | Admin | `?orgId=&since=` | `{violations[], topOffenders[]}` | 200 |

**Derivation note:** `mode: SHADOW` on the rule is what makes limit changes safe — you deploy a new limit in shadow, watch `violations` for a day to see who *would* have been throttled, then flip to `ENFORCE`. Without it, every limit change is a production experiment on real customers.

### 3.9 Data Model

```sql
-- Rules: small, read-heavy, cached in every pod with pub/sub invalidation.
CREATE TABLE rate_limit_rules (
    rule_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id         UUID,                         -- NULL = global/default rule
    key_pattern    VARCHAR(256) NOT NULL,        -- "org:*", "org:00D5f:endpoint:/query"
    endpoint       VARCHAR(128),                 -- NULL = all endpoints
    algorithm      VARCHAR(32) NOT NULL DEFAULT 'TOKEN_BUCKET',
    limit_count    INTEGER NOT NULL,
    window_seconds INTEGER NOT NULL,
    burst_capacity INTEGER,                      -- token bucket only; >= limit_count
    priority       SMALLINT NOT NULL DEFAULT 100,-- lower wins: most-specific-first
    mode           VARCHAR(16) NOT NULL DEFAULT 'ENFORCE'  -- ENFORCE | SHADOW | DISABLED
        CHECK (mode IN ('ENFORCE','SHADOW','DISABLED')),
    created_at     TIMESTAMPTZ DEFAULT now(),
    updated_at     TIMESTAMPTZ DEFAULT now(),

    UNIQUE (key_pattern, endpoint)
);

CREATE INDEX idx_rule_lookup ON rate_limit_rules (org_id, priority)
    WHERE mode <> 'DISABLED';

-- Violations: append-only stream for alerting and abuse detection.
-- NOTE: written async off the hot path (Kafka -> batch insert), never inline.
CREATE TABLE rate_limit_violations (
    id            BIGSERIAL PRIMARY KEY,
    org_id        UUID NOT NULL,
    client_key    VARCHAR(256) NOT NULL,
    endpoint      VARCHAR(128),
    rule_id       UUID,
    was_shadow    BOOLEAN DEFAULT FALSE,         -- would-have-blocked vs did-block
    occurred_at   TIMESTAMPTZ DEFAULT now(),

    INDEX idx_org_recent (org_id, occurred_at DESC)
) PARTITION BY RANGE (occurred_at);              -- 7-day hot window; older -> S3

-- Per-org quota overrides (the multi-tenant fairness input).
CREATE TABLE tenant_quotas (
    org_id            UUID PRIMARY KEY,
    edition           VARCHAR(32),               -- limits are edition-derived
    daily_api_calls   BIGINT,
    max_concurrent    INTEGER,
    updated_at        TIMESTAMPTZ DEFAULT now()
);
```

**Counter state is deliberately NOT in this schema** — it lives only in Redis. Say this out loud: *"Counters are ephemeral and reconstructible; persisting them to Postgres would put a write on every request, which is exactly the load the limiter exists to prevent. Losing counters on a Redis failure means clients briefly get a fresh allowance — acceptable, and far cheaper than durably writing 1M counters/sec."*

| Decision | Why | What breaks otherwise |
|---|---|---|
| `priority` column, lower-wins | Rule precedence (endpoint > org > global) must be explicit data, not code | Precedence buried in Java means changing it needs a deploy, and two rules can silently both match with undefined winner |
| `mode` as an enum incl. `SHADOW` | Safe rollout of limit changes against real traffic | Every limit change is a live experiment; you discover the limit was too tight from customer escalations |
| Violations partitioned + written async | 429s can spike to millions/min during an attack — inline writes would amplify the incident | Synchronous violation logging turns a traffic spike into a database outage |
| `was_shadow` flag | Distinguishes "would have blocked" from "did block" in the same table | Can't measure the impact of a proposed limit without polluting real violation metrics |
| No counter table | Counters are ephemeral, high-write, reconstructible | Persisting them adds a durable write to every request — the limiter becomes the bottleneck |

### 3.10 Salesforce Multi-Tenancy Angle

> *"This is essentially Salesforce's API governor limits. A real org has a 24-hour rolling API call quota tied to its edition, plus a concurrent-request cap. I'd make `org_id` the primary rate-limit dimension with the quota derived from `tenant_quotas.edition`, and enforce a **two-tier limit**: a per-org quota so one tenant can't exhaust shared capacity, plus a per-API-key limit inside the org so one runaway integration can't consume the whole org's quota and break every other integration that org depends on."*

Two Salesforce-specific points worth adding:
- **Concurrent-request limiting is a different mechanism than rate limiting** — it's a semaphore on in-flight requests, not a bucket over time. Salesforce enforces both, and conflating them is a common miss.
- **The 429 must be actionable per-tenant:** returning `Retry-After` plus which limit was hit (org quota vs key limit) is what lets a customer self-diagnose instead of filing a support case.

---

## 4.  Navigation Pivots — THIS Problem

**Opening Protocol (first 2 minutes — verbatim, per `format.md` Section 2):**

> "Before I start — should I do LLD first or HLD first, or do you have a preference?"
> *(If no preference:)* "I'll start with LLD — the algorithm choice and class structure — then zoom out to how this works across a fleet, which is where the interesting problem is. I'll flag the transition."

| Interviewer Says | What They Want | Your Move |
|---|---|---|
| "Which algorithm would you choose?" | LLD depth on the core trade-off | The 4-row comparison table from 2.5. Lead with token bucket + why; reject fixed window on the 2x boundary burst |
| "Now you have 50 servers" | **The pivot this problem exists for** | Name Breaking Point 1 immediately: "the limit silently becomes 50x." Then: state moves behind `CounterStore` -> Redis + atomic Lua |
| "Why is one Redis call not enough?" | Atomicity understanding | GET-then-SET has a race between the two hops; show the Lua script and say "one round trip, atomic inside Redis" |
| "What if Redis is down?" | Failure policy maturity | Fail open for quotas, fail closed for abuse rules, degrade to local limiting as a middle tier — and say *why* fail-closed globally is worse than the disease |
| "One customer sends 200K req/sec" | HLD hot-key problem | Sharding by key doesn't help a single hot key; sub-counter splitting + L1 leases do |
| "Add a new algorithm tomorrow" | Extensibility (OCP) | LLD: one new `RateLimiter` impl, registered via DI. HLD: one new Lua script; no topology change |
| "How does Salesforce do this?" | Domain awareness | Governor limits: per-org 24h rolling quota by edition + concurrent-request caps; two-tier org/key limiting |

---

## 5.  TL;DR — Dual-Level Pitch

At the class level this is a Strategy: `RateLimiter` with one implementation per algorithm, defaulting to token bucket because it's O(1) memory and handles burst natively, while fixed window is rejected for its 2x boundary burst. The structural decision that matters is that limiters are stateless — all counter state sits behind a `CounterStore` interface, so the same class runs single-node on a `ConcurrentHashMap` or fleet-wide on Redis. That matters because the defining failure of this problem is in-process counters: 50 pods each enforcing 100/min means the client actually gets 5,000/min, and the system reports full compliance while being wrong by 50x. The distributed version collapses refill-check-decrement into a single atomic Redis Lua script — two round trips would leave a race — with per-pod token leases in front to cut Redis load ~20x, trading bounded over-admission for latency. The key trade-off is failing open by default so the limiter never becomes a bigger outage than the API it protects, with abuse-prevention rules failing closed instead; on Salesforce this maps directly to per-org governor limits with a second per-API-key tier inside each org.

---

##  Changelog

| Date | Change |
|---|---|
| Aug 2026 | File created — third problem in `Interview/Salesforce/HLD+LLD/`. Grounded in CodingKaro Dec 2025 ("Design API Rate Limiter") and Blind Aug 2025 (Backend SMTS Bay Area). Follows `solution-notes-standards.md`; matches the derivation-first bar from `notification-service.md` and `job-scheduler.md`: noun-from-requirement derivation, composition-vs-aggregation calls, alternatives-considered against the strongest alternative, per-section follow-ups, staged HLD evolution with quantified breaking points, full data model. |
