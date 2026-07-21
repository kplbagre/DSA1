# Pattern Deep Dive: Strangler Fig

> **Read this when:** An interviewer asks you to extract a service from a monolith, replace a vendor dependency, migrate from one architecture to another, or deprecate a feature — without taking the system down or rewriting everything at once.
> **Pre-interview refresh:** Read the KEY INSIGHT + the 4-phase diagram (5 min).

---

## 🎯 What Problem Does This Pattern Solve?

You have a working system. It needs to change — a service needs to be extracted, a dependency replaced, or a component decomposed. The wrong answer is: **rewrite it all and cut over on one day.**

The "big bang" rewrite fails because:
- The new system always has bugs the old one doesn't
- There's no way to roll back once you've cut over
- You can't validate the new system under real production traffic without exposing users to its bugs
- You need both systems to be correct simultaneously, and that's hard to guarantee

The Strangler Fig (named after a tree that grows around its host, eventually replacing it without the host ever "dying" suddenly) is the answer: **extract one piece at a time, keep the old system running, route traffic gradually, decommission only after validation.**

Real-world examples:
- Netflix migrating from a monolithic Java app to microservices over 7 years
- Shopify splitting its Rails monolith into pods — one service at a time
- DocuSign extracting document signing logic from a legacy C++ system
- Any "we're going to microservices" initiative that actually succeeds

---

## 💡 Core Insight

**Never rewrite and never stop. Extract and validate incrementally.** The old system is the fallback. The new system earns traffic by proving itself. The moment you decommission the old system, you lose your safety net — so you decommission last, not first.

> **KEY INSIGHT:** "The strangler fig never kills the host directly. It grows alongside it, takes over the sunlight (traffic) gradually, and the host dies naturally once it has no more traffic to serve. You decommission by starvation — not by cutting."

---

## 🎨 Visual — The 4-Phase Migration

```
PHASE 1: Coexistence (build new alongside old)
─────────────────────────────────────────────────────
ALL traffic
     │
     ▼
┌─────────────┐     still handles everything
│   MONOLITH  │ ◄──────────────────────────────
│  (old)      │
│  NotifLogic │
└─────────────┘

    ┌─────────────────┐
    │ Notification     │   ← being built, gets NO traffic yet
    │ Microservice     │     (validated in shadow mode only)
    │ (new)           │
    └─────────────────┘

─────────────────────────────────────────────────────
PHASE 2: Shadow Mode (route a slice, compare — responses NOT shown to user)
─────────────────────────────────────────────────────
ALL traffic
     │
     ├──── 95% ────▶ MONOLITH  (still source of truth)
     │
     └──── 5%  ────▶ NEW SERVICE  (shadow mode — real traffic forked,
                                   responses compared but
                                   NEVER shown to user)

─────────────────────────────────────────────────────
PHASE 3: Traffic Ramp (new earns majority)
─────────────────────────────────────────────────────
     ├──── 50% ────▶ MONOLITH
     └──── 50% ────▶ NEW SERVICE  (response shown to user)

     ├──── 10% ────▶ MONOLITH   (shrinks)
     └──── 90% ────▶ NEW SERVICE (grows)

─────────────────────────────────────────────────────
PHASE 4: Decommission (old is starved, then removed)
─────────────────────────────────────────────────────
     └──── 100% ───▶ NEW SERVICE

     MONOLITH notification code → deleted
     Old DB table → archived → dropped

KEY INVARIANT:
   The old system is never killed. It is starved.
   You move traffic away incrementally; the old code
   is removed only after 100% traffic runs clean on new.
```

---

## 🗂️ The 4 Phases in Detail

---

### Phase 1 — Build New Alongside Old

Build the new service. Wire it to its own DB. Do NOT route any user traffic to it yet.

**What you do:**
- Stand up the new service with all required APIs
- Give it a dedicated DB (not the monolith's shared DB)
- Backfill historical data from old DB to new DB (batch job)
- Start dual-write at the END of Phase 1 (see below)
- Test with synthetic traffic only until Phase 2

**Why no user traffic yet:** The new system is unproven. If you expose users to it now, bugs affect real people and you have no baseline to compare against.

**When does dual-write start — and why this matters:**

Dual-write starts at the end of Phase 1, after the backfill completes. This timing matters because of the backfill race condition:

```
T0: Backfill job starts (copying old DB → new DB)
T1: User creates new notification → goes to old DB only (dual-write not started yet)
T2: Backfill finishes — but T1's write is not in new DB (race!)
```

Fix: start dual-write BEFORE the backfill completes (or re-run the backfill with a narrow time window after dual-write is on). The safest sequence:

```
1. Start backfill job (batch, idempotent)
2. Enable dual-write via new service's write API → both DBs now receive writes
3. Run a catch-up backfill for any rows written between backfill start and dual-write enable
   (narrow time window, fast to process)
4. Reconciliation job validates both DBs match → Phase 1 complete
```

See `12-dual-write-reconciliation.md` for the full dual-write design.

**The output of Phase 1:** A running service with a fully synced DB and dual-write active. New service write logic tested under real production writes — no traffic exposure yet.

---

### Phase 2 — Shadow Mode (5% Canary, Read-Only Compare)

Route a small slice of real traffic to the new service. Do NOT show the new service's response to the user — show the old service's response. But record both and compare.

**What you do:**
- Set the load balancer or API gateway to fork 5% of reads to both services
- Return the old service's response to the user (always)
- Log new service response alongside old service response
- Run an automated comparison job: are the responses the same?

**Why 5% not 100%:** You're looking for divergence, not replacing the user experience. 5% gives you real production data patterns without risking all users.

**The critical addition — inactive user validation:**
5% canary only covers users who happen to log in. Users who haven't logged in for weeks will never appear in the 5% slice. Their data may have drifted. You must proactively validate them.

```
Background Job (read-only, runs daily):
  → fetch list of users inactive > 7 days
  → for each: GET /notifications?userId=X from OLD service
  → GET /notifications?userId=X from NEW service
  → compare responses
  → log mismatch → alert if mismatch rate > threshold
```

**CRITICAL: This job must be read-only.** It cannot trigger writes. If it did, it would corrupt the DB during the dual-write phase.

**The output of Phase 2:** Confidence that new service responses match old for all user segments — active and inactive.

---

### Phase 3 — Traffic Ramp

Now serve users from the new service. Increase gradually.

```
Week 1:  5%  new  → 95% old   (monitoring only)
Week 2: 25%  new  → 75% old
Week 3: 50%  new  → 50% old
Week 4: 90%  new  → 10% old
Week 5: 100% new  → 0%  old
```

**Rollback trigger:** Define before starting. Common: if error rate on new service exceeds 0.5% OR P99 latency increases by more than 20% → immediately route 100% back to old service.

---

### Phase 4 — Decommission

Only after 100% traffic runs clean for at least 1 full traffic cycle (typically 2 weeks):
- Delete the extracted code from the monolith
- Mark old DB table as read-only (archive, don't delete yet)
- After 30 days: drop old table

**Why wait 30 days to drop the table:** Audit trails, legal holds, and "I need to check something in the old data" requests always arrive after you think you're done.

---

## ⚠️ The 4 Things That Go Wrong

**1. The proxy layer becomes a bottleneck**
Adding a routing proxy to split traffic introduces latency. Fix: use your existing API gateway (not a custom proxy); most API gateways support canary routing natively.

**2. Writes diverge during dual-write phase**
One DB gets a write the other misses (network timeout between the two writes). Fix: designate one DB as source of truth; run a daily reconciliation job; alert on divergence before ramping traffic.

> **Important — what "dual write" means during microservice extraction:** The monolith must call the NEW SERVICE's write API, not write directly to the new DB. Direct DB write bypasses the new service's business logic — validation, event publishing, business rules. When traffic eventually shifts, the write path runs in production for the first time. See `12-dual-write-reconciliation.md` for the full distinction.

**3. Someone skips the inactive user validation**
This is the single most common migration failure. Active users look fine. Inactive users have stale or missing data in the new service that nobody catches until a user complains 3 months later. Fix: background comparison job — mandatory, not optional.

**4. Decommission happens too early**
Team is excited to remove the old code. They decommission before 100% traffic is stable. A bug surfaces on the new service. There's no rollback. Fix: define a stability window (2 weeks at 100%) before decommissioning is allowed.

---

## 🧩 Interview Probe Q&As

**"Why not just rewrite and cut over?"**
> Big bang rewrites fail because the new system has bugs the old one doesn't, and there's no rollback once you've cut over. The Strangler Fig keeps the old system as a live fallback until the new system has proven itself under real production traffic. You decommission by starvation, not by cutting.

**"What's your rollback strategy?"**
> Before ramping past 5%, define the rollback trigger — e.g., error rate > 0.5% or latency increase > 20%. The load balancer routes back to old service in under 60 seconds. Old DB is still the source of truth so no data is lost. The rollback is automated, not manual.

**"How do you validate inactive users who never hit the canary?"**
> Canary only covers users who organically log in during the slice window. For inactive users, run a proactive read-only background job daily — fetch their data from both old and new service, compare, alert on mismatch. This must be read-only: it cannot trigger writes or it corrupts the DB state during dual-write.

**"How do you handle the DB during migration?"**
> See `12-dual-write-reconciliation.md` — this is a separate pattern that runs alongside the Strangler Fig. Short answer: dual-write to both DBs, old is source of truth, reconciliation job catches drift.

**"When do you decommission?"**
> 100% traffic on new service for at least 2 weeks with error rate and latency matching or beating the old service. Then archive old table, wait 30 days, drop it. Remove old code from monolith last.

---

## 🧭 When to Use This Pattern

| Situation | Use Strangler Fig? |
|---|---|
| Extract one service from a monolith | ✅ Yes — this is the canonical use case |
| Replace a vendor SDK embedded in 20 services | ✅ Yes — wrap vendor behind adapter, swap internals |
| Migrate from REST to gRPC | ✅ Yes — run both, migrate clients one by one |
| Rewrite from scratch (new language/framework) | ✅ Yes — old system routes until new one is ready |
| Schema change in a single DB table | ❌ No — use Expand-Migrate-Contract (`11-expand-migrate-contract.md`) |
| Add a new feature to an existing service | ❌ No — just add it with a feature flag |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 11, 2026 | **Note created.** Triggered by live interview — notification service migration question exposed gap in migration pattern prep. |
| Jul 11, 2026 | **Bug fix — two gaps found during audit.** (1) Phase 1 added backfill race condition explanation and correct dual-write start sequence. (2) Things That Go Wrong #2 clarified that "dual write" in microservice extraction means calling the new service's write API, not writing directly to the new DB. |
| Jul 20, 2026 | Fixed naming confusion: Phase 2 diagram labeled the forked-traffic slice as "canary" but the behavior (responses NOT shown to user, responses compared/discarded) is shadow mode by definition. Canary is Phase 3 where new service responses ARE shown to users. Updated diagram labels to "shadow mode". |
