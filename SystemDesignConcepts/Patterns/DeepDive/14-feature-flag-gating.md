# Pattern Deep Dive: Feature Flag Gating

> **Read this when:** An interviewer asks how you roll out a risky change safely, add a cross-cutting concern (rate limiting, auth, logging) to many services without touching all of them at once, or deprecate a feature while keeping the old path alive for stragglers.
> **Pre-interview refresh:** Read the KEY INSIGHT + the flag types table (5 min).

---

## 🎯 What Problem Does This Pattern Solve?

You need to release something to production — but you're not confident enough to release it to everyone at once. Or you need to add the same behaviour to 20 services without deploying all 20 simultaneously. Or you need to turn something off quickly if it breaks, without a redeployment.

The naive approach: deploy to all users at once. If it breaks, redeploy the fix. This takes 15–45 minutes of deployment time — during which users are broken.

Feature flags (also called feature toggles — configuration-driven switches that control which code path executes at runtime, without redeployment) solve all three problems:
- **Gradual rollout:** release to 1% → 10% → 50% → 100% of users
- **Kill switch:** if it breaks, flip the flag to OFF — takes effect in seconds, no redeploy
- **Cross-cutting rollout:** the flag evaluator lives in one place (API gateway, service mesh, SDK), not in every service

Real-world examples:
- Facebook uses feature flags to release to internal employees first, then 1% of users, then everyone
- Netflix uses flags to A/B test algorithm changes before full rollout
- Shopify uses flags to enable beta features for merchant cohorts before general availability
- Any "dark launch" or "controlled rollout" is a feature flag in disguise

---

## 💡 Core Insight

**The flag is the rollback mechanism.** You don't need a hotfix deployment to revert — you flip the flag. This decouples the deployment (putting code in production) from the release (turning the code on for users). Code can sit in production "dark" (deployed but flagged off) for days before it's released.

> **KEY INSIGHT:** "Deploy ≠ Release. Feature flags let you separate the two. You deploy code to production dark. You release it by flipping a flag. You roll back by flipping it back — in seconds, not 45 minutes. The flag is your safety net, not your deployment pipeline."

---

## 🎨 Visual — How Feature Flag Evaluation Works

```
WITHOUT FEATURE FLAGS
──────────────────────────────────────────────────────────────
Request → Service → (code always runs) → Response
                         │
                     New code breaks? → Deploy a fix → 45 min downtime


WITH FEATURE FLAGS
──────────────────────────────────────────────────────────────

Request → Service → Flag Evaluator ──── flag=OFF ────▶ OLD code path → Response
                         │
                         └─────────── flag=ON  ────▶ NEW code path → Response

Flag Evaluator checks:
  1. Is this flag enabled globally?       (boolean flag)
  2. Is this user in the rollout %?       (percentage flag: hash(user_id) % 100 < rollout_pct)
  3. Is this user in the beta segment?    (cohort flag: user.isBetaTester == true)
  4. Is this request from internal IPs?   (environment flag: ip in INTERNAL_RANGE)

Flag config lives in:
  - A central config service (LaunchDarkly, Flagsmith, Unleash)
  - Redis (flag name → JSON config, TTL-less)
  - Environment variable (simplest, but requires redeploy to change)

Flag change propagates in:
  - Config service: < 1 second (push-based)
  - Redis poll: 1–5 seconds (poll interval)
  - Env var: requires redeploy (use only for non-urgent flags)


PERCENTAGE ROLLOUT — how it works
──────────────────────────────────────────────────────────────

rollout_percentage = 10

For each request:
  bucket = hash(user_id) % 100   ← deterministic: same user always gets same bucket
  if bucket < rollout_percentage:
      → NEW code path (this user is in the 10%)
  else:
      → OLD code path

Why hash(user_id) not random():
  random() gives a different result on every request
  → user sees new UI on one page load, old UI on next → inconsistent experience
  hash(user_id) is deterministic: same user is always in or always out

KEY INVARIANT:
   A flag evaluator NEVER makes a network call on the hot path.
   Flag config is cached locally. Evaluation is in-memory, sub-millisecond.
   Stale flag config (5-second lag) is acceptable. Latency is not.
```

---

## 🗂️ The 4 Flag Types

---

### Type 1 — Boolean (Kill Switch)

The simplest flag. Either ON or OFF for all users.

**When to use:**
- Emergency kill switch: "turn off the new payment processor if it breaks"
- Temporary workaround: "disable the expensive ML ranking while the model is being retrained"
- Dark launch validation: "code is deployed, turn it on only when we're ready"

```java
if (featureFlags.isEnabled("new_payment_processor")) {
    return newPaymentProcessor.charge(request);
} else {
    return legacyPaymentProcessor.charge(request);
}
```

**Propagation:** Config service pushes new value. Takes effect on next request evaluation. No redeploy.

---

### Type 2 — Percentage Rollout (Canary by User)

Enable the new code path for N% of users. Ramp the percentage as confidence grows.

**When to use:**
- Gradual release: "roll out the new recommendation algorithm to 1% → 10% → 50% → 100%"
- Risk mitigation: "if it breaks, only N% of users are affected"
- Statistical validation: "do users in the new path have better engagement?"

```java
// WRONG — Math.abs(Integer.MIN_VALUE) overflows and stays negative:
// int bucket = Math.abs(userId.hashCode()) % 100;  ← do not use

// CORRECT — mask the sign bit, result is always 0–99:
int bucket = (userId.hashCode() & Integer.MAX_VALUE) % 100;
if (bucket < featureFlags.getRolloutPct("new_recommendations")) {
    return newRecommendationEngine.rank(items);
} else {
    return legacyRankingEngine.rank(items);
}
```

**Ramp schedule:**
```
Day 1:  1%   → watch error rate, latency
Day 3:  5%   → watch for edge case failures
Day 7:  25%  → watch for scale-related issues
Day 14: 50%  → approaching general availability
Day 21: 100% → flag becomes permanent, schedule flag removal
```

---

### Type 3 — Cohort / Segment Flag (Beta Users)

Enable for a specific group: beta users, internal employees, premium subscribers, a specific company.

**When to use:**
- Internal dogfooding: "turn on the new UI for all Walmart employees first"
- Beta program: "enable for users who opted into the beta"
- Enterprise feature: "enable only for tenants on the Enterprise plan"

```java
if (user.isBetaTester() && featureFlags.isEnabled("new_dashboard")) {
    return newDashboard.render();
} else {
    return oldDashboard.render();
}
```

**Why better than a hardcoded list:** The flag evaluator checks the user's attributes dynamically. Adding a user to the beta doesn't require code change — just update the segment definition in the flag config.

---

### Type 4 — Environment / Ops Flag (Cross-Cutting Rollout)

Enable a behaviour across many services at once, controlled from one place.

**The scenario:** You need to add rate limiting to 20 services. You don't want to deploy all 20 simultaneously. You want to test rate limiting on 2 services first, then expand.

```
Without flags:
  20 services → each needs its own rate limiting code + deployment

With flags at the API gateway layer:
  API Gateway → flag evaluator → if flag ON → apply rate limiting → forward to service
  Service code is unchanged.
  Flag is OFF by default. Turn it ON one service at a time.
```

```
Flag config:
{
  "rate_limiting_enabled": {
    "services": ["notification-service", "search-service"],  ← start with 2
    "rollout_pct": 100
  }
}

Week 2: add "payment-service" to the list
Week 3: add remaining 17 services
```

**This is how you add auth, tracing, rate limiting, audit logging to many services without touching all of them** — the concern lives at the gateway or service mesh layer, controlled by a flag.

---

## ⚠️ The 3 Things That Go Wrong

**1. Flag proliferation — flags that never get removed**

Teams add flags but never clean them up. 2 years later, the codebase has 400 flags. Nobody knows which ones are still active. The code is full of dead branches.

Fix: every flag must have:
- An owner (team name)
- An expiry date (when it will be either permanent or removed)
- A removal ticket created at flag creation time

No expiry date = flag is rejected. This is a process rule, not a technical one.

**2. Stale flag config causes inconsistency**

Service A polls flag config every 5 seconds. Service B polls every 30 seconds. During a ramp-up, Service A sees flag=25% but Service B still sees flag=0%. A request that spans both services sees inconsistent behaviour.

Fix: use a push-based config service (LaunchDarkly, Flagsmith) that propagates changes instantly. If using Redis polling, keep poll intervals consistent across all services (5 seconds standard).

**3. Flag evaluation on the hot path causes latency**

Flag evaluator makes a network call to a config service on every request. Config service becomes a dependency. If it's slow, your entire service is slow.

Fix: flag config is ALWAYS cached in local memory. The evaluator reads from cache. A background thread refreshes the cache every 5 seconds. The hot path never makes a network call for flag evaluation.

---

## 🧩 Interview Probe Q&As

**"How do you roll back a bad release instantly without redeploying?"**
> Feature flag. The new code path is gated behind a flag. If it breaks, flip the flag to OFF — the config service propagates the change in under 1 second, and every new request goes back to the old code path. No deployment, no 45-minute pipeline. The flag is the rollback mechanism.

**"How do you add rate limiting to 20 services without touching all of them?"**
> Rate limiting lives at the API gateway layer, not inside each service. A feature flag controls which services have it enabled. Start with 2 services, validate, then add the remaining services to the flag config one by one. No code change in any service — just a config update.

**"How do you ensure a user always gets the same experience and doesn't flip between old and new UI on page refreshes?"**
> Use `hash(user_id) % 100` for bucket assignment, not `random()`. The hash is deterministic — the same user always maps to the same bucket. `random()` would give a different result on every request, causing inconsistent user experience.

**"What happens if your flag service goes down?"**
> The local cache serves stale flag config — typically 5 seconds old at most. Define a safe default for every flag: if config is unavailable, should the flag be ON or OFF? For a kill switch, the safe default is OFF (don't run the risky new code). For a permanently-on feature, the safe default is ON (don't degrade users because config is unavailable).

**"When do you remove a flag?"**
> Once the rollout reaches 100% and has been stable for 2+ weeks, the flag becomes permanent. Remove the flag, make the new code path the only path. Remove the old code path. This is called "baking in" the flag. Do it on a schedule — flags that aren't removed become technical debt that makes the codebase harder to reason about.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 11, 2026 | **Note created.** Batch 1 of Operational-Scenarios gap closure. Feature flag pattern — critical for cross-cutting rollout scenarios and "how do you release safely" interview questions. |
| Jul 11, 2026 | **Bug fix — hashCode overflow in percentage rollout.** `Math.abs(userId.hashCode()) % 100` is wrong: `Math.abs(Integer.MIN_VALUE)` overflows and returns a negative number, producing an invalid bucket. Fixed to `(userId.hashCode() & Integer.MAX_VALUE) % 100` — masks the sign bit, always returns 0–99. |
