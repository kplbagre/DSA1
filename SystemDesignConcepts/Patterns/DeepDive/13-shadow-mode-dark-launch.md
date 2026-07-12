# Pattern Deep Dive: Shadow Mode / Dark Launch

> **Read this when:** You need to validate a new system under real production traffic — without exposing users to its bugs, and without relying on synthetic test data. Especially critical when migrating services where inactive users may have data that active-user canary testing misses.
> **Pre-interview refresh:** Read the KEY INSIGHT + the inactive user section (5 min).

---

## 🎯 What Problem Does This Pattern Solve?

You have built a new service. You tested it in staging. Staging is not production.

The gap between staging and production:
- Staging has clean, uniform test data. Production has 5 years of messy edge cases.
- Staging traffic is synthetic. Production traffic has patterns you didn't anticipate.
- Staging doesn't have users who signed up 3 years ago and haven't logged in since. Production does.

You need to expose the new service to **real production traffic** before users depend on it. Shadow mode (also called dark launch — a launch where the new system is "live" but invisible to users) is the answer: run the new service in parallel with the old one, fire the same requests at both, but always return the old service's response to the user.

Real-world examples:
- GitHub ran its new search infrastructure in shadow mode for months before switching
- Uber ran its new dispatch algorithm in shadow mode to compare ETAs before user impact
- Any migration where "it worked in staging" is not enough confidence

---

## 💡 Core Insight

**The user never sees the shadow response. The engineer always does.** Shadow mode is an observation tool — you learn what the new system does under real conditions without risking user experience. The user is the control group; the shadow is the experiment.

> **KEY INSIGHT:** "Shadow mode separates risk from learning. You get the learning (does the new system produce correct results?) without the risk (user sees a wrong answer). You earn the right to serve users only after shadow validation passes."

---

## 🎨 Visual — Shadow Mode Traffic Flow

```
NORMAL REQUEST (without shadow mode)
──────────────────────────────────────────────────────
    User request
         │
         ▼
    OLD SERVICE  ──── response ────▶ User sees this ✅
         │
    OLD DB


SHADOW MODE REQUEST (running both in parallel)
──────────────────────────────────────────────────────
    User request
         │
         ├──── primary ────▶ OLD SERVICE ──── response ────▶ User sees this ✅
         │                        │
         │                    OLD DB
         │
         └──── shadow  ────▶ NEW SERVICE ──── response ────▶ User NEVER sees this
                                  │                                │
                              NEW DB                         Comparison
                                                             Service logs:
                                                             match? mismatch?
                                                             latency diff?

COMPARISON RESULT
──────────────────────────────────────────────────────
  ┌─────────────────────────────────────────────────┐
  │  Match    → ✅ new service correct for this case │
  │  Mismatch → ❌ log payload diff, alert team      │
  │  New svc timeout → ⚠️ log, don't fail request   │
  └─────────────────────────────────────────────────┘

KEY INVARIANT:
   A shadow response timeout or error NEVER fails the user's request.
   The old service's response is always the user's answer.
   Shadow mode is observation only — never participates in the user's experience.
```

---

## 🗂️ The 3 Modes of Shadow Validation

---

### Mode 1 — Full Shadow (0% user exposure)

100% of requests are forked to the shadow. Users always see the old service's response. Shadow responses are logged and compared.

**When to use:** First 1–2 weeks after the new service is built. You're learning, not serving.

**What you're looking for:**
- Functional correctness: do responses match?
- Data completeness: does the new service have all the data the old one has?
- Edge cases: what request shapes produce mismatches?
- Latency: is the new service faster or slower than the old one?

---

### Mode 2 — Canary (5% user exposure, rest stays shadow)

5% of users see the new service's response. 95% still see the old one. The 5% is the canary — if the new service has a critical bug, only 5% of users are affected.

**When to move from full shadow to canary:** Mismatch rate has been below 0.1% for 7 consecutive days in full shadow mode.

**Rollback trigger:** Error rate on the 5% canary slice > 0.5%, or latency > 20% worse. Instantly route the 5% back to old service.

---

### Mode 3 — Inactive User Validation (shadow for dormant accounts)

This is the one most teams skip — and it's the most dangerous gap.

**The problem:** A 5% canary only covers users who organically generate traffic. If your system has 10M users but only 500K are active monthly, your canary validates 500K users' data. The other 9.5M users have data in your old DB that has NEVER been verified in the new DB.

```
Active users (500K):
  → Hit the canary naturally
  → Shadow comparison runs
  → Data validated ✅

Inactive users (9.5M):
  → Never hit the canary
  → Shadow comparison never runs
  → Data NOT validated ❌
  → When they log in after you've switched to new DB → potential corruption
```

**Fix — proactive background validation job:**

```
Background Shadow Job (runs nightly, READ-ONLY):

Step 1: Identify inactive users
  SELECT user_id FROM users
  WHERE last_login < NOW() - INTERVAL '30 days'
  LIMIT 10000  ← batch size, don't do all at once

Step 2: For each user, shadow-call both services
  old_response = GET /notifications?userId=X from OLD service
  new_response = GET /notifications?userId=X from NEW service

Step 3: Compare
  If match    → mark user_id as validated in shadow_validation table
  If mismatch → log full diff, alert, add to re-sync queue

Step 4: Re-sync queue (separate job)
  For mismatched users:
    canonical_state = fetch from OLD DB (source of truth)
    call NEW SERVICE's idempotent write/backfill API with canonical_state
    do NOT write directly to new DB — that bypasses the service's write logic
    mark as re-synced, re-run shadow comparison to verify

CRITICAL CONSTRAINT:
  This job is READ-ONLY.
  It MUST NOT create, update, or delete any user-facing data.
  If it did, it would pollute the new DB with synthetic writes
  during the dual-write phase.

  When a mismatch IS found and a re-sync is needed:
  → Call the NEW SERVICE's write/backfill API with canonical data from old DB
  → Do NOT write directly to the new DB
  → Direct DB write bypasses the new service's validation and business logic
     — the DB would have data that the service itself never processed
```

**Do not ramp canary traffic past 25% until the background validation job has passed for all inactive users.**

---

## ⚠️ The 3 Things That Go Wrong

**1. Shadow mode has side effects**

The new service being shadow-called performs writes: sends emails, charges cards, creates records.

This is catastrophic. A user's request hits the old service (their actual response), AND the shadow triggers a charge, email, or DB write from the new service.

Fix: Shadow mode MUST be read-only at the new service level OR the shadow infrastructure must intercept and suppress write side effects. Before running shadow mode, audit every write path in the new service. Mark them as no-ops in shadow mode via a context flag.

```java
if (ShadowContext.isShadow()) {
    // log what would have been written, but don't write
    log.info("Shadow suppressed write: {}", payload);
    return;
}
// actual write
repository.save(entity);
```

**2. Comparison is too strict**

Timestamps, UUIDs, and ordering differ between old and new service responses. Comparison job reports 100% mismatch because the new service uses UTC timestamps and the old one uses epoch milliseconds.

Fix: Normalise before comparing. Write a comparison function that ignores non-semantic differences (timestamp format, ordering of equal-priority items, field naming). Focus on semantic equality: same data, not same bytes.

**3. Shadow latency impacts primary path**

Shadow requests run in parallel with primary requests — but if the shadow's infrastructure shares connection pools, thread pools, or CPU with the primary service, it will slow down user responses.

Fix: Run shadow in a completely separate process. Use async fire-and-forget for shadow calls. Set a timeout on shadow calls: the right value is the **old service's P99 latency + a small buffer** (e.g., if old service P99 is 180ms, shadow timeout = 200ms). Using an arbitrary absolute value (e.g., 200ms against an old service with P99 of 300ms) means you always time out and never capture shadow responses. Never block the primary request waiting for the shadow.

**4. Shadow context lost in async / reactive code**

`ShadowContext.isShadow()` via a thread-local flag works on the primary thread, but silently drops context on thread pool handoff — `CompletableFuture`, Spring `@Async`, and reactive frameworks (WebFlux, Project Reactor) all execute continuations on different threads.

Fix: if using Java thread pools, use `InheritableThreadLocal` so child threads inherit the parent's context. If using reactive code, pass the shadow flag through the reactive context (`Mono.contextWrite`) rather than a thread-local. Verify this propagation explicitly — a unit test that runs shadow through a `CompletableFuture.supplyAsync()` will fail silently without it.

---

## 🧩 Interview Probe Q&As

**"How do you handle inactive users that your 5% canary never hits?"**
> Run a proactive background validation job nightly. It fetches data for inactive users from both old and new service, compares the responses, and logs mismatches. It is strictly read-only — it cannot trigger writes or it corrupts the DB state during dual-write. You don't ramp canary past 25% until this job has validated all inactive user cohorts.

**"What if the shadow service is slow? Does it affect the user?"**
> No. Shadow calls are fire-and-forget with a timeout set to the old service's P99 latency + buffer. If the shadow doesn't respond in time, the timeout is logged and the primary response is returned to the user immediately. The primary path is never blocked on the shadow. Important: the timeout should NOT be an arbitrary constant (e.g., "always 200ms") — it should be calibrated to the old service's actual P99 so you capture real shadow responses rather than timing out everything.

**"What if the shadow service causes side effects — emails, charges?"**
> This is the hardest part of shadow mode. You must audit every write path in the new service and suppress them when running in shadow context. The service reads a `X-Shadow-Request: true` header (or a thread-local flag) and no-ops all writes, logging what it would have done instead.

**"How is shadow mode different from A/B testing?"**
> A/B testing splits users and measures business metrics — conversion, engagement. Both A and B responses are shown to real users. Shadow mode never shows the new system's response to users — it's purely an engineering validation tool. The user is not a subject; the shadow is invisible to them.

**"How long do you run shadow mode before switching?"**
> Until mismatch rate is below 0.1% for 14 consecutive days AND the inactive user background job has passed all cohorts. There is no fixed time — it's metric-gated, not calendar-gated.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 11, 2026 | **Note created.** Triggered by live interview — inactive user validation gap exposed during notification service migration question. Interviewer had to prompt this insight; it now has a dedicated note so it's proactively raised next time. |
| Jul 11, 2026 | **Bug fix — re-sync queue implementation.** Original re-sync step said "re-write to new DB directly" — same class of bug as dual-write direct DB write. Fixed to: call new service's idempotent write/backfill API with canonical data from old DB. Added explicit note in CRITICAL CONSTRAINT block explaining why direct DB write is wrong. |
| Jul 11, 2026 | **Senior tech lead audit fixes.** (1) Shadow timeout fixed: was absolute 200ms, now stated as old-service P99 + buffer — arbitrary constant will time out valid responses if old service P99 > timeout. (2) Added "Things That Go Wrong #4" on ThreadLocal context loss in async/reactive code (CompletableFuture, WebFlux) — fix: InheritableThreadLocal for thread pools, reactive context for Reactor/WebFlux. (3) Updated probe Q&A to reflect calibrated timeout guidance. |
