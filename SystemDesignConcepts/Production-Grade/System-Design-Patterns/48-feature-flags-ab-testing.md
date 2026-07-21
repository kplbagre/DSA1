# 48 — Feature Flags / A/B Testing Infrastructure

## 📖 What is Feature Flags / A/B Testing Infrastructure?

**Full form:** Feature Flags (also called feature toggles or feature switches) + A/B Testing Infrastructure — a runtime mechanism to enable/disable code paths or route users to different variants without deploying new code.

**Simple analogy:** A railway switching yard. The trains (code) are identical — same route, same schedule. But the track switches (feature flags) decide which tracks each train takes. In an A/B test, switch A sends 50% of trains to Platform 1 (variant A); switch B sends 50% to Platform 2 (variant B). You measure which platform clears trains faster. No new trains needed — just the switches change.

**Core principle:** Feature flags decouple code deployment from feature release. Code is deployed to all servers but only activated for a percentage of users or specific user segments. A/B testing extends this — two variants run simultaneously, and metrics determine which wins. The infrastructure must guarantee sticky assignment (same user always sees the same variant) for valid experiment results.

**Why it matters:** Every modern tech company deploys dark launches (code deployed to production but not yet activated for users), gradual rollouts, and continuous experiments. Without feature flags, every release is all-or-nothing. With them, you roll out to 1% → 10% → 100% while monitoring error rates.

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|------|----------------------|---------|
| **Feature Flag (Toggle)** | runtime on/off switch for a code path; no redeployment needed to enable/disable | `if (flagService.isEnabled("new-checkout", userId)) { ... }` |
| **A/B Test (Experiment Flag)** | two variants run simultaneously for different user buckets; metrics decide the winner | 50% users see green CTA button; 50% see blue; measure which has higher conversion |
| **Sticky Assignment** | same user always sees the same variant for the duration of an experiment; no mid-experiment switching | user_id=12345 assigned to variant A on day 1 → always gets variant A until experiment ends |
| **Deterministic Bucketing** | variant assignment computed by hashing user_id + flag_key → percentage; same input → same output | `hash("user123" + "checkout-test") % 100 < 50` → variant A; no stored state needed |
| **Dark Launch** | code deployed to production but flag off for all users; test infrastructure before enabling anyone | new payment processor code deployed; flag=off; QA team manually turns on for test users |
| **Gradual Rollout (Canary)** | enable flag for 1% → 5% → 25% → 100% of users; monitor error rate at each step | 1% rollout of new search algorithm; error rate 0.1% at 1% → expand to 10% |
| **Kill Switch (Ops Flag)** | permanent flag that can disable an expensive feature under load; never removed | `enable-real-time-recommendations=false` during Black Friday traffic spike → serve cached |
| **Zombie Flag** | feature flag whose feature shipped to 100% but the flag code was never cleaned up | flag `new-checkout-2022` still in codebase 2 years later; no one knows if safe to remove |
| **LaunchDarkly / GrowthBook** | managed feature flag services; provide SDKs, dashboards, and flag evaluation APIs | Walmart: GrowthBook for internal A/B tests; LaunchDarkly in many enterprise SaaS shops |

---

## 🎯 Why This Matters

**The problem:**
- Deploying a risky feature to 100% of production users simultaneously causes outages when it goes wrong.
- Requiring a full redeployment to roll back wastes hours — the flag flip is instantaneous.
- Without A/B testing infrastructure, product decisions are made on gut feel, not data. You ship a new checkout flow and never know if it actually improved conversion.

**When this comes up in interviews:**
- Product/growth feature questions: new checkout flow, recommendation algorithm, pricing page redesign, onboarding funnel.
- Infrastructure changes needing gradual rollout: new DB query strategy, new caching layer, new ML model.
- Any question involving "how would you safely deploy X to 500 million users without risking all of them."

**Senior engineer expectation:**
Name the flag evaluation model (in-process cache vs remote eval per request), consistent bucketing (hash-based, not random), variant stickiness (same user always in same bucket), metric consistency (don't mix users between variants mid-experiment), flag lifecycle management (zombie flags — flags whose feature has shipped but the flag code was never cleaned up), and the tradeoff between LaunchDarkly (managed SaaS) vs internal SDK approaches.

---

## 🧠 The Mental Model

Think of a pharmacist running a clinical drug trial. The trial has Treatment A (new drug) and Treatment B (placebo). 1,000 patients are enrolled. Each patient is assigned to a group once at enrollment and stays there for the duration — a patient assigned to group A on day 1 doesn't switch to group B on day 5. This is sticky assignment.

The pharmacist doesn't flip a coin each time a patient shows up. They look up the patient ID in the enrollment registry and return the same assignment every time. The coin was flipped once — at enrollment. The result was written down. Every subsequent lookup reads that written result.

Translate this to feature flags:
- The drug trial = the A/B experiment
- Enrollment registry = the bucketing table (or deterministic hash function)
- Patient ID = user_id
- Treatment assignment = variant ("new-checkout" vs "old-checkout")
- Trial outcome = conversion rate, P95 latency, error rate

For feature flags without A/B testing (simple on/off or percentage rollout), the same principle applies with even less infrastructure:
- A 5% rollout means: `hash(userId + flagKey) % 100 < 5` → show new feature to this user.
- No randomness at call time — same user always gets the same result because the hash is deterministic.
- This means user_id 12345 either always sees the feature or never does (within the experiment window). They don't see it on mobile and not on desktop. Consistency across sessions and devices comes for free from the hash, not from any stored state.

**The key insight:** Flag evaluation must be deterministic given the same (user_id, flag_key) input. Randomness happens once, baked structurally into the hash function — never re-randomized per request.

**Three types of flags by use case:**

1. **Release flags** (boolean on/off): hide unfinished features from production traffic. Remove within one sprint after launch. Example: `new-search-ui-v3`.
2. **Experiment flags** (percentage split with named variants): A/B test UX, pricing, algorithm changes. Remove after winner is declared and deployed to 100%. Example: `checkout-cta-button-color-test`.
3. **Ops flags** (kill switches): disable expensive features under load (e.g., "disable real-time recommendations and serve cached results instead"). Long-lived, never removed — they are permanent operational controls.

---

## 🎨 Visual — System Topology & Component Flow

### Diagram 1 — Full System Topology

```
┌───────────────────────────────────────────────────────────────────┐
│                  Client Tier                                      │
│          (browsers, mobile apps, third-party SDKs)                │
└────────────────────────┬──────────────────────────────────────────┘
                         │  HTTP request (user_id in header/token)
                         ▼
┌───────────────────────────────────────────────────────────────────┐
│                  API / Edge Service (Pod)                         │
│  ┌─────────────────────────────────────────────────────────────┐  │
│  │           Flag Evaluation Engine (in-process library)       │  │
│  │                                                             │  │
│  │  evaluate(flagKey, userId, userAttributes)                  │  │
│  │     1. Check global kill switch                             │  │
│  │     2. Check user-specific override                         │  │
│  │     3. Check segment targeting rules                        │  │
│  │     4. hash(userId + flagKey) % 100 < rolloutPct ?          │  │
│  │     5. Return default                                       │  │
│  │                                                             │  │
│  │  [ConcurrentHashMap<String, FlagRule>]  ← in-memory cache   │  │
│  └──────────────────────────┬──────────────────────────────────┘  │
└─────────────────────────────│─────────────────────────────────────┘
                              │  Background thread syncs every 30s
                              │  (NOT per-request — zero network latency)
                              ▼
┌───────────────────────────────────────────────────────────────────┐
│              Flag Config Service (centralized)                    │
│      LaunchDarkly  OR  internal gRPC config service               │
│                                                                   │
│  Stores: FlagRule objects                                         │
│    - flagKey, enabled, defaultVariant                             │
│    - userOverrides: Map<userId, variant>                          │
│    - segmentRules: List<SegmentRule>                              │
│    - variantAllocations: List<{variant, upperBound}>              │
│                                                                   │
│  Streaming (SSE/WebSocket) for instant kill-switch propagation    │
└──────────────────────┬────────────────────────────────────────────┘
                       │  Admin writes / updates flag rules
                       ▼
┌───────────────────────────────────────────────────────────────────┐
│              Admin Dashboard / Feature Flag API                   │
│   - Create/modify flags and variant allocations                   │
│   - Set rollout percentages (1% → 10% → 50% → 100%)              │
│   - Flip kill switches (ops flags) instantly                      │
│   - View live metric dashboards per variant                       │
└──────────────────────┬────────────────────────────────────────────┘
                       │
                       ▼
┌───────────────────────────────────────────────────────────────────┐
│              User Attribute DB / Session Token Store              │
│   user_id → { country, accountAge, deviceType, betaEnrolled }    │
│   (used to resolve segment targeting rules at evaluation time)    │
└───────────────────────────────────────────────────────────────────┘

KEY: Flag evaluation is IN-PROCESS within the API pod.
     It reads from a local in-memory cache — ZERO network calls per request.
     The cache syncs in the background every 30 seconds.
     Config service downtime does NOT break flag evaluation — stale cache serves.
```

### Diagram 2 — Component Detail: Flag Evaluation Decision Tree

```
evaluate(flagKey, userId, userAttributes)
              │
              ▼
┌─────────────────────────────────────┐
│  1. Lookup flagKey in in-memory     │
│     cache (ConcurrentHashMap)       │
│                                     │
│  cache miss OR rule.enabled = false │──► return defaultVariant ("off")
└──────────────────┬──────────────────┘
                   │ rule found AND enabled = true
                   ▼
┌─────────────────────────────────────┐
│  2. Check user-specific overrides   │
│     rule.userOverrides.get(userId)  │
│                                     │
│  override found                     │──► return override variant
│  (e.g., userId=123 → "variant-B"   │    (used for QA / debugging)
│   to force QA engineer into a       │
│   specific variant)                 │
└──────────────────┬──────────────────┘
                   │ no override
                   ▼
┌─────────────────────────────────────┐
│  3. Check segment targeting rules   │
│     for each SegmentRule in order:  │
│       segmentRule.matches(          │
│         userAttributes)             │
│                                     │
│  segment match                      │──► return segmentRule.variant
│  (e.g., country=IN → "variant-B")  │    (targeted rollout by attribute)
└──────────────────┬──────────────────┘
                   │ no segment match
                   ▼
┌─────────────────────────────────────┐
│  4. Percentage rollout bucketing    │
│     (bucketing = deterministically  │
│      assigning users to a numbered  │
│      range 0-99 via a hash)         │
│                                     │
│  bucket = abs(hash(userId+flagKey)) │
│           % 100                     │
│                                     │
│  Walk variantAllocations (sorted    │
│  by upperBound ascending):          │
│    [{variant="control", ub=50},     │
│     {variant="new-ui",  ub=100}]    │
│                                     │
│  bucket=34 → "control"  (34 < 50)  │──► return "control"
│  bucket=72 → "new-ui"   (72 < 100) │──► return "new-ui"
└──────────────────┬──────────────────┘
                   │ no allocation matched (should not happen
                   │ if allocations sum to 100)
                   ▼
┌─────────────────────────────────────┐
│  5. Return defaultVariant           │
│     (always the safe/off state)     │
└─────────────────────────────────────┘

KEY INVARIANT: hash(userId + flagKey) is deterministic.
Same user_id + same flag_key → same bucket (0–99) → same variant.
Every time. Across all pods. Across all sessions. No DB write needed.
Stickiness is structural (baked into math), not stored.
Scales to billions of users with zero per-user storage.
```

---

## ⚙️ How It Actually Works

### 4a — Flag Evaluation with Local Cache

**How it works, step by step:**

1. On service startup: the SDK fetches all flag rules from the config service into an in-memory `ConcurrentHashMap<String, FlagRule>`. This is the only synchronous network call to the config service.
2. A background thread refreshes the entire map every 30 seconds. Flag rules change rarely (a human writes them), so 30-second staleness is acceptable.
3. Per request: `evaluate(userId, flagKey, userAttributes)` runs entirely against in-memory data — zero network calls, sub-millisecond evaluation.
4. Bucketing (deterministic percentage assignment): compute `Math.abs((userId + flagKey).hashCode()) % 100` → integer 0–99. Compare against variant allocation ranges to determine the variant.

```java
public class FlagEvaluator {
    // In-memory cache: flagKey → FlagRule (refreshed every 30s from config service)
    private final Map<String, FlagRule> flagCache;
    private final FlagConfigClient configClient;

    public FlagEvaluator(FlagConfigClient configClient) {
        this.configClient = configClient;
        this.flagCache = new ConcurrentHashMap<>();
        // Eagerly populate cache at startup to avoid cold-start latency on first request
        refreshFlags();
    }

    @Scheduled(fixedDelay = 30_000)
    public void refreshFlags() {
        // Fetch complete list of flag rules from config service
        List<FlagRule> rules = configClient.fetchAllRules();
        // Overwrite existing entries atomically; new flags appear, removed flags persist until next gc pass
        for (FlagRule rule : rules) {
            flagCache.put(rule.getFlagKey(), rule);
        }
    }

    public String evaluate(String flagKey, String userId, Map<String, String> userAttributes) {
        FlagRule rule = flagCache.get(flagKey);
        // Flag not found in cache OR globally disabled → return default (safe/off state)
        if (rule == null || !rule.isEnabled()) {
            return rule != null ? rule.getDefaultVariant() : "off";
        }
        // Step 1 — check user-specific overrides first (for QA engineers forcing themselves into a variant)
        String override = rule.getUserOverrides().get(userId);
        if (override != null) {
            return override;
        }
        // Step 2 — check segment targeting rules in priority order
        for (SegmentRule segmentRule : rule.getSegmentRules()) {
            if (segmentRule.matches(userAttributes)) {
                return segmentRule.getVariant();
            }
        }
        // Step 3 — percentage rollout via deterministic hash (bucketing)
        // IMPORTANT: Math.abs(Integer.MIN_VALUE) == Integer.MIN_VALUE (still negative in Java)
        // Use bitwise mask instead: (hashCode & 0x7FFFFFFF) guarantees non-negative
        int bucket = ((userId + flagKey).hashCode() & 0x7FFFFFFF) % 100;
        // Walk sorted allocations: first allocation whose upperBound exceeds bucket wins
        for (VariantAllocation allocation : rule.getVariantAllocations()) {
            if (bucket < allocation.getUpperBound()) {
                return allocation.getVariant();
            }
        }
        // Fallthrough: no allocation matched (allocations don't sum to 100 — config error)
        return rule.getDefaultVariant();
    }
}
```

### 4b — Variant Allocation and A/B Test Configuration

**How it works, step by step:**

1. Each flag has a list of `VariantAllocation` objects sorted by `upperBound` ascending.
2. Allocations are expressed as cumulative upper bounds: control = 0–50 (upperBound=50), new-checkout = 50–100 (upperBound=100). A user with bucket=34 lands in control; bucket=72 lands in new-checkout.
3. The user's bucket (0–99) falls into exactly one range → deterministic, unique variant assignment with no overlap.

```java
public class FlagRule {
    // Unique identifier for the flag (e.g., "new-checkout-v3-test")
    private String flagKey;
    // Master on/off switch — if false, all evaluations return defaultVariant immediately
    private boolean enabled;
    // Default returned when flag is disabled or user doesn't match any rule
    private String defaultVariant;
    // Per-user overrides for QA/debugging — checked before bucketing
    private Map<String, String> userOverrides;
    // Targeting rules by user attributes (e.g., country=IN → variant B)
    private List<SegmentRule> segmentRules;
    // Percentage allocations sorted by upperBound ascending
    // Example: [{variant="control", upperBound=50}, {variant="new-checkout", upperBound=100}]
    // This means: bucket 0–49 → "control" (50%), bucket 50–99 → "new-checkout" (50%)
    private List<VariantAllocation> variantAllocations;

    public String getFlagKey() { return flagKey; }
    public boolean isEnabled() { return enabled; }
    public String getDefaultVariant() { return defaultVariant; }
    public Map<String, String> getUserOverrides() { return userOverrides; }
    public List<SegmentRule> getSegmentRules() { return segmentRules; }
    public List<VariantAllocation> getVariantAllocations() { return variantAllocations; }
}

public class VariantAllocation {
    // Variant name (e.g., "new-checkout", "control", "variant-b")
    private String variant;
    // Bucket upper bound (exclusive). Range for this variant: [previousUpperBound, upperBound)
    // Allocations must be sorted ascending and the last entry's upperBound must equal 100
    private int upperBound;

    public String getVariant() { return variant; }
    public int getUpperBound() { return upperBound; }
}
```

**Flag config JSON schema — what a `FlagRule` looks like at rest in the config service:**

```json
{
  "flagKey": "new-checkout-v3-test",
  "enabled": true,
  "defaultVariant": "control",
  "userOverrides": {
    "userId-qa-engineer-1": "new-checkout"
  },
  "segmentRules": [
    {
      "attribute": "country",
      "operator": "EQ",
      "value": "IN",
      "variant": "new-checkout"
    }
  ],
  "variantAllocations": [
    { "variant": "control",      "upperBound": 50  },
    { "variant": "new-checkout", "upperBound": 100 }
  ],
  "owner": "checkout-team",
  "expiresAt": "2026-09-01"
}
```

The `expiresAt` field drives zombie flag detection — see the lifecycle section below.

### 4c — Metric Consistency: Ensuring Users Don't Switch Variants Mid-Experiment

**How it works, step by step:**

1. Once an experiment starts, never change variant allocation percentages mid-flight. If variant B was 10% and you grow it to 20%, some users previously in control are now in variant B — their earlier behavior is mixed into variant B's metric window, corrupting the data.
2. If an experiment must be adjusted (e.g., you realize you need more traffic), end the current experiment and start a new one. Use a new `experimentId` as part of the hash key so all users get fresh, clean bucket assignments.
3. Adding `experimentId` to the hash input: `hash(userId + experimentId + flagKey) % 100` — the same user gets a statistically independent bucket assignment for the new experiment.

```java
// Experiment-aware bucketing — prevents variant contamination across experiment iterations
public String evaluateExperiment(String experimentId, String flagKey, String userId) {
    // Include experimentId in hash input so user-to-variant mapping resets cleanly per experiment
    // Without experimentId: changing allocations mid-experiment silently corrupts historical data
    String hashInput = userId + experimentId + flagKey;
    int bucket = (hashInput.hashCode() & 0x7FFFFFFF) % 100;
    FlagRule rule = flagCache.get(flagKey);
    if (rule == null || !rule.isEnabled()) {
        return rule != null ? rule.getDefaultVariant() : "off";
    }
    // Same waterfall: walk allocations sorted by upperBound to find the matching variant
    for (VariantAllocation allocation : rule.getVariantAllocations()) {
        if (bucket < allocation.getUpperBound()) {
            return allocation.getVariant();
        }
    }
    return rule.getDefaultVariant();
}
```

### What is a "zombie flag," and why does it matter?

A zombie flag is a feature flag whose controlling feature was fully launched months ago, but the flag evaluation code was never removed. The service still calls `evaluate("old-checkout-v2-test", userId)`. The flag config is still in the system. But the variant never changes from "enabled" for 100% of users — there's no experiment any more, just a permanent branch that always evaluates to "on."

Zombie flags accumulate technical debt in several ways: the code has permanent if/else branches that can never execute the "off" path; engineers reading the code don't know if the feature is experimental or permanent; the flag config service carries hundreds of dead rules; and new engineers fear removing the flag code because they can't be sure it's truly dead.

In an interview, if asked about flag lifecycle: "Flag lifecycle management means every flag has a planned removal date — typically within one sprint after the experiment concludes or after the feature ships to 100% of users. The flag config should track owner and expiry date; a lint rule or CI check can warn when a flag's expiry date has passed and the flag code still exists."

**Zombie flag CI check — annotation-based expiry enforcement:**

```java
// Tag every flag evaluation call site with the expected removal date
@FeatureFlag(key = "new-checkout-v3-test", expiresBy = "2026-09-01")
public boolean isNewCheckoutEnabled(String userId) {
    return flagEvaluator.evaluate("new-checkout-v3-test", userId, Map.of()).equals("new-checkout");
}
```

```java
// Annotation processor runs at compile time — CI fails the build if expiry date has passed
public class FeatureFlagExpiryChecker extends AbstractProcessor {

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment env) {
        LocalDate today = LocalDate.now();
        for (Element el : env.getElementsAnnotatedWith(FeatureFlag.class)) {
            FeatureFlag ff = el.getAnnotation(FeatureFlag.class);
            LocalDate expiry = LocalDate.parse(ff.expiresBy());
            if (today.isAfter(expiry)) {
                processingEnv.getMessager().printMessage(
                    Diagnostic.Kind.ERROR,
                    "Flag '" + ff.key() + "' expired on " + ff.expiresBy() + " — remove this flag code."
                );
            }
        }
        return true;
    }
}
```

The build fails until the flag code (and the `if/else` branch it controls) is deleted. Forces cleanup before technical debt accumulates.

---

## 🏢 Real World — Where Companies Use This

- **Netflix (dark launches):** Every new feature in Netflix's recommendation system is dark-launched — code deployed globally but only evaluated for 0.1% of users initially. Engineers monitor error rate and P99 latency on that 0.1% slice before expanding rollout. Feature flags are the safety valve: rollback equals setting the flag back to 0% rollout, which takes seconds and requires no redeployment.

- **Flipkart (Big Billion Day checkout):** A new checkout flow was A/B tested for six weeks before Big Billion Day. 50% of users saw the new UI (variant B); 50% saw the existing flow (control). The primary metric was conversion rate; the guardrail metric was cart abandonment rate. The winner was deployed to 100% one day before the sale — with enough time to catch regressions but close enough to the event that any issues would be caught in production conditions.

- **Swiggy (restaurant ranking algorithm):** A new ML-based ranking was A/B tested against rule-based ranking. Sticky assignment was critical here — the same user must see the same restaurant ordering on every app open, or the experiment captures random noise instead of a genuine preference signal. Users weren't randomly re-bucketed on each session start; they were consistently bucketed by user_id for the duration of the experiment.

- **PhonePe (ops kill switch):** Payment processing uses feature flags for expensive real-time fraud scoring. Under peak load (New Year's Eve, IPL final), the real-time fraud scoring flag is turned off for low-risk transaction categories, falling back to cached risk scores from the previous 10 minutes. This is a classic ops flag (kill switch) — it is never removed and is exercised deliberately by SRE during high-load periods. When the peak subsides, the flag is re-enabled.

- **Airbnb (GrowthBook — open source):** Airbnb's pricing display experiments used GrowthBook, their open-source A/B testing platform. Critically, they bucketed by `listing_id` rather than `user_id` when testing pricing presentation — the same listing must show the same price to all viewers for any given moment, because multiple users browsing the same listing simultaneously must see consistent information.

---

## 🧭 When to Use vs When NOT to Use

| Use this when | Do NOT use when |
|---|---|
| Gradual rollout of risky features to 1% → 10% → 100% of users with real-time monitoring | Feature is foundational infrastructure with no user-visible variant (e.g., switching between two internal DB connection pools with identical behavior) |
| A/B testing UX or algorithm changes with statistical measurement of conversion, latency, or revenue | Flags are used as permanent configuration storage — use environment variables or a database config table instead |
| Kill switch needed for expensive operations under peak load (ops flag / circuit breaker pattern) | Security-critical paths (authentication, encryption, authorization) — a misconfigured flag could disable security checks for a percentage of users |
| Dark launching (code deployed to production and instrumented, but not yet activated for users) | Short-lived hotfixes that need instant rollback — use deploy-with-immediate-revert workflow instead; flag infrastructure adds latency to the rollback decision loop |

**The common mistake:** Using feature flags as permanent feature-access controls — "premium users see this dashboard, free users don't." This is RBAC (role-based access control) and entitlement logic, not feature flag logic. Feature flags should be temporary instruments for gradual rollout and experimentation; they are not a subscription management system. Conflating the two leads to zombie flags that live forever because they have permanent business logic baked into them.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Decouple deploy from release — code can sit in production dormant for weeks before activation. Instant rollback — flip flag to 0%, no redeploy. A/B test any code path without a separate deploy. Gradual rollout with real-time monitoring of error rate and latency. Dark launch for risky features — validate in production at low traffic before committing. |
| **You lose** | Flag proliferation — zombie flags accumulate and the codebase becomes a maze of conditional branches. Every flagged code path must be maintained for the life of the flag (two code paths running simultaneously). Flag evaluation adds ~1–5ms overhead if not properly cached in-process. Distributed config means stale flag state is possible during config service downtime (mitigated by local cache). |
| **Failure mode** | Config service goes down → pods continue serving from stale in-memory cache (30-second lag). If a pod restarts during the config outage, it cold-starts with an empty cache and falls back to hardcoded defaults. **This is why defaults must always be the safe/conservative value (feature off), never the dangerous value (security disabled, expensive feature on, auth bypassed).** Design: `default = feature off`. Never design: `default = feature on` for a feature that hasn't been validated. |

**Failure mode 2 — Redis secondary cache:** To survive config service restarts with a warm cache, back the in-memory cache with a Redis layer. On cold start, the SDK reads from Redis before hitting the config service. Redis TTL = 5 minutes. This means a freshly started pod during a config outage still gets recently-valid flag rules, not hardcoded defaults.

---

## 🔬 Interview Q&As

**Q1 (Tier 1):** "How do you ensure a user always sees the same A/B test variant, even across multiple sessions and devices?"

> Use deterministic hashing: `hash(userId + experimentId) % 100`. Given the same `userId` and `experimentId`, this expression always returns the same integer in [0, 99], which maps to the same variant allocation bucket. The hash is deterministic — same inputs, same output, always. No database storage is needed for variant assignment; the assignment is implicit in the math. Cross-device consistency is guaranteed because both devices evaluate the same hash function with the same `userId`. The only way a user can switch variants is if the variant allocation configuration changes mid-experiment — which is why mid-experiment allocation changes are explicitly forbidden. If more traffic is needed, end experiment v1 and start experiment v2 with a new `experimentId`.

---

**Q2 (Tier 1):** "Your flag config service goes down. What happens to feature flag evaluations in your services?"

> Nothing changes immediately for running pods. All service pods hold a populated in-memory cache of flag rules, refreshed every 30 seconds in a background thread. During the config service outage, every pod continues evaluating flags from its local cache. Evaluations are sub-millisecond, and the config service is not in the critical path per request. The risk window is: (a) a pod restarts during the outage and its cache is cold — it reads hardcoded defaults (all flags off), which is the safe failure mode; (b) a flag update pushed just before the outage may or may not have propagated to all pods yet (30-second window). To reduce blast radius: build the SDK to read from a Redis secondary cache as a warm fallback before falling through to hardcoded defaults. Redis TTL = 5 minutes.

---

**Q3 (Tier 1):** "How do you measure the impact of an A/B test?"

> Both variants instrument metric events: a conversion event (purchase completed, signup confirmed, button clicked) tagged with `userId`, `variantName`, and `experimentId`. These events stream to your analytics pipeline (Kafka → BigQuery or Redshift). Post-aggregation, compare conversion rates between variant A and B using a statistical significance test (two-proportion z-test or chi-squared, targeting p-value < 0.05). Calculate minimum sample size before the experiment starts to avoid underpowered tests. Also track guardrail metrics: P99 latency (is the new feature slower?), error rate, and revenue per user. Crucially: don't stop the experiment early because one variant looks better at day 3 — this is the "peeking problem" (repeatedly checking interim results inflates false-positive rate). Run to the predetermined sample size.

---

**Q4 (Tier 2 — cross/probe):** "You're running an A/B test on checkout UX. A user starts checkout on mobile (assigned to variant A) and then completes the purchase on desktop. Which variant gets credit for the conversion?"

> Attribution must go to the variant at the time of the first meaningful interaction — when the user entered the checkout funnel (added item to cart, hit "proceed to checkout"). At that moment, we persist the variant assignment to a durable store: `INSERT INTO experiment_assignments (user_id, experiment_id, variant, assigned_at) ON CONFLICT (user_id, experiment_id) DO NOTHING`. The `ON CONFLICT DO NOTHING` clause ensures the first recorded assignment is immutable — no update possible. When the conversion event fires (purchase completed on desktop), the analytics pipeline joins it against `experiment_assignments` by `(user_id, experiment_id)` to retrieve the variant that was active at funnel entry. This prevents cross-device contamination (mobile assigns variant A, desktop assigns variant B at different hash evaluation times if the session context differs) and ensures attribution accuracy.

---

**Q5 (Tier 2 — cross/probe):** "If flag evaluation uses a local in-process cache with a 30-second refresh, how do you propagate an emergency flag flip (ops kill switch) instantly to all pods during a production incident?"

> Two mechanisms, in increasing responsiveness order. Option 1: Tag ops flags (kill switches) separately from experiment flags and set their refresh interval to 5 seconds — acceptable because kill switches change infrequently but must propagate fast when they do. Option 2 (preferred for sub-second propagation): implement a pub-sub invalidation channel. When a flag rule is updated in the config service, it publishes a `flag_changed:{flagKey}` event to a Redis pub-sub channel or an internal event bus. Each service pod subscribes to this channel on startup. On receiving the event, the pod immediately fetches only the changed flag rule from the config service and updates its local cache entry. This gives near-instant propagation (< 1 second) without polling every second for the entire flag set. LaunchDarkly's SDK uses this exact pattern with Server-Sent Events (SSE) — each SDK instance opens a persistent SSE connection to the LaunchDarkly streaming endpoint and receives flag change events in real time without polling.

```java
// SSE-based instant flag invalidation — SDK opens one persistent connection per pod
// Flag change event arrives within milliseconds of admin flipping the kill switch

EventSource eventSource = new EventSource.Builder(
    new EventHandler() {
        @Override
        public void onMessage(String event, MessageEvent messageEvent) {
            // Payload: {"flagKey": "ops-kill-realtime-recs", "action": "updated"}
            FlagChangedEvent change = parse(messageEvent.getData());
            // Fetch only the changed rule — not the entire flag set
            FlagRule updatedRule = configClient.fetchRule(change.getFlagKey());
            flagCache.put(change.getFlagKey(), updatedRule);
            log.info("Flag {} updated via SSE — cache refreshed in < 1s", change.getFlagKey());
        }

        @Override
        public void onError(Throwable t) {
            // SSE connection dropped — fall back to 5-second polling until reconnect
            log.warn("SSE stream lost, falling back to polling: {}", t.getMessage());
        }
    },
    URI.create("https://flag-config-service/flags/stream")
).build();

eventSource.start();
```

**Why SSE over WebSocket for this use case:** Flag updates are server-push only — no client sends. SSE is unidirectional, simpler to implement and load-balance than WebSocket, and reconnects automatically on drop. For a read-only broadcast channel like flag config updates, SSE is the right tool.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> "Feature flags decouple code deploy from feature release by evaluating `hash(userId + flagKey) % 100 < rolloutPercent` in-process from a locally-cached config — no network call per request — giving instant rollback and A/B testing with guaranteed sticky user-to-variant assignment."

---

## 🔗 Related Concepts

- `Production-Grade/Auth-and-Security/27-auth-authz-fundamentals.md` — RBAC for permanent feature entitlements vs feature flags for temporary experiments; don't conflate the two
- `Foundations/Performance-and-Scale/03-caching.md` — local in-process cache pattern used for flag config storage; same TTL and invalidation trade-offs apply
- `Core-Architecture/Service-Communication/19-message-queues-kafka-rabbitmq.md` — Kafka for streaming metric events from A/B test variant conversions into the analytics pipeline
- `Production-Grade/Observability/25-monitoring-observability-fundamentals.md` — metric events, dashboards, and alerting needed to measure A/B test outcome and monitor gradual rollout health

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **"Feature Toggles (aka Feature Flags)" — Martin Fowler** | Taxonomy of flag types (release, ops, experiment, permission), lifecycle management, zombie flag risk and remediation strategies | ~15 min read |
| **GrowthBook Documentation — Sticky Bucketing** | Implementation details for sticky assignment with cross-device support; covers hash-based vs stored bucketing trade-offs | ~10 min read |
| **"Overlapping Experiments Infrastructure" — Google KDD 2010** | Google's original paper on running thousands of simultaneous A/B tests without interference — foundational reading for large-scale experimentation infrastructure | ~20 min read |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 1, 2026 | Added FlagRule JSON schema (with `owner` and `expiresAt` fields); added zombie flag CI check with `@FeatureFlag` annotation processor that fails the build when expiry date has passed; added SSE-based kill-switch propagation code to Q5 (EventSource client with onMessage/onError handlers, plus SSE-vs-WebSocket rationale). |
| Jul 20, 2026 | Fixed latent bucketing bug: `Math.abs(hashCode) % 100` is incorrect because `Math.abs(Integer.MIN_VALUE) == Integer.MIN_VALUE` (still negative). Changed to `(hashCode & 0x7FFFFFFF) % 100` in both bucketing sites. |
