# Operational Scenario: Monolith → Microservice Migration

> **When this appears in an interview:** Interviewer says "we have X in a monolith, we want to extract it as a microservice — how do you manage the migration?" The keyword is **migrate**, not design. If you start designing the new service from scratch without a migration plan, you've answered the wrong question.
> **Patterns used:** Strangler Fig (`10-strangler-fig.md`) + Dual Write (`12-dual-write-reconciliation.md`) + Shadow Mode (`13-shadow-mode-dark-launch.md`)

---

## 🎯 The Situation

You have a monolith. One piece of it — a service, a set of tables, a domain — has grown large enough to justify extraction. The business is live. Users are using it right now. You cannot take it down. You cannot rewrite everything.

**Classic triggers:**
- "The notification service is slowing down everything else in the monolith — deploys take 45 minutes because of it"
- "We need the payment service to scale independently — it's getting hammered on Black Friday but nothing else is"
- "Three teams are all changing the user service and stepping on each other"

---

## 🧠 The Decision You Make First

Before drawing anything, ask one clarifying question:

> *"Are you asking me to design the new microservice, or walk through the migration strategy — how we extract it safely from the monolith?"*

If migration → this document applies.
If design → start with requirements and HLD of the new service, but still end with a brief migration section.

The word **migrate** in the question means they want to hear: Strangler Fig, dual write, canary, shadow mode, decommission. Not "here's how I'd build a notification service."

---

## 🎨 Visual — The Full Migration Picture

```
BEFORE: Everything in the monolith
─────────────────────────────────────────────────────────────────
┌──────────────────────────────────────────────────────────────┐
│                        MONOLITH                              │
│                                                              │
│  ┌──────────────┐   in-process   ┌────────────────────────┐ │
│  │ Business     │──────────────▶ │  Target Service Logic   │ │
│  │ Logic        │                │  (e.g. Notification)    │ │
│  │ (sign, pay,  │                │  - create               │ │
│  │  feature)    │                │  - read                 │ │
│  └──────────────┘                │  - mark read/delete     │ │
│                                  └───────────┬─────────────┘ │
│                                              │               │
│                                  ┌───────────▼─────────────┐ │
│                                  │    SHARED MONOLITH DB    │ │
│                                  │   (notifications table)  │ │
│                                  └──────────────────────────┘ │
└──────────────────────────────────────────────────────────────┘


AFTER: Clean extraction
─────────────────────────────────────────────────────────────────
┌──────────────────────┐
│      MONOLITH        │
│  Business Logic      │──── HTTP call ────────────────────────▶
│  (sign, pay, feature)│                                        │
└──────────────────────┘                                        │
                                              ┌─────────────────▼───────────────┐
                                              │      NEW MICROSERVICE            │
                                              │                                  │
                                              │  ┌─────────┐   ┌─────────────┐  │
                                              │  │ REST API │   │  Consumer   │  │
                                              │  │ (client) │   │  (Kafka)    │  │
                                              │  └─────────┘   └─────────────┘  │
                                              │                                  │
                                              │  ┌───────────────────────────┐  │
                                              │  │    DEDICATED DB            │  │
                                              │  │    (not shared with        │  │
                                              │  │     monolith)              │  │
                                              │  └───────────────────────────┘  │
                                              └──────────────────────────────────┘
                                                            ▲
                                              ┌─────────────┘
                                              │
                                    ┌─────────────────┐
                                    │  CLIENT          │
                                    │  (Web / Mobile)  │
                                    │  reads/writes    │
                                    │  directly to new │
                                    │  service API     │
                                    └─────────────────┘
```

---

## 🗂️ The 5-Phase Playbook

State this aloud in the interview, one phase at a time. Do not dump all phases at once — walk the interviewer through each.

---

### Phase 1 — Build the New Service (Zero Risk)

**What you do:**
- Build the new microservice with its own dedicated DB
- Implement all the APIs: create, read, mark-read, delete
- Backfill: copy existing data from monolith DB to new DB (batch job)
- NO user traffic yet

**Say in interview:**
> *"First, I build the new service with a dedicated DB and backfill the existing data. No user traffic touches it yet — this phase carries zero risk."*

**Backfill strategy:**
```
Batch job (runs once):
  SELECT * FROM monolith.notifications
  ORDER BY created_at ASC

  For each batch of 1000 rows:
    INSERT INTO new_db.notifications (...)
    OFFSET checkpoint  ← resumable if job fails mid-run

  Run during off-peak hours
  Idempotent: use notification ID as conflict key (ON CONFLICT DO NOTHING)
```

---

### Phase 2 — Dual Write via New Service API (Write Path Validation)

**What you do:**
- Monolith calls the **new service's write endpoints** for every write — not the new DB directly
- Old DB = source of truth (monolith still writes here first)
- New service handles the write, applies its own business logic, writes to new DB
- Reconciliation job runs every 15 minutes to catch drift

**Why via service API, not direct DB write:**
If the monolith wrote directly to the new DB, the new service's write logic — validation, event publishing, business rules — would never run. By the time you switch traffic over, the write path would be running in production for the first time. Calling the service API exercises the full write path under real load from day one.

**Say in interview:**
> *"I enable dual write — but via the new service's write API, not direct DB writes. Every write goes to old DB first (source of truth), then the monolith calls the new service's write endpoint. The new service processes it through its full business logic and writes to the new DB. If the new service call fails we log drift — we don't fail the user's request because the old DB already committed. A reconciliation job runs every 15 minutes. Phase 2 ends only after the write error rate on the new service is below 0.1% for 7 days — that's write confidence."*

```
Corrected monolith write path:
  1. Write to OLD DB          (if this fails → abort, return error to user)
  2. HTTP call to NEW SERVICE write API:
       POST   /notifications          → new service creates, writes to NEW DB
       PATCH  /notifications/:id/read → new service marks read, writes to NEW DB
       DELETE /notifications/:id      → new service soft-deletes, writes to NEW DB
     If this call fails → log to drift table, alert, do NOT fail the request
     (old DB committed — request succeeded from user's perspective)

Write validation gate (before moving to Phase 3):
  Monitor new service write error rate daily:
    errors / total write calls = error rate
  Target: < 0.1% error rate for 7 consecutive days
  If not met → investigate, fix, reset the 7-day counter
  Only when write path is proven stable → move to shadow read validation

Reconciliation job (every 15 min):
  Compare both DBs for recent changes
  Any gap → alert → on-call re-syncs the drifted row
```

> **See:** `12-dual-write-reconciliation.md` for failure modes and reconciliation job design.

> ⚠️ **Common mistake:** Teams often skip write validation and jump straight to ramping read traffic. When the write path breaks under load — which it will if untested — users are already seeing the new service's reads. Fixing a broken write path while users are live on the new service is far harder than fixing it during dual-write when old DB is still the source of truth.

> ⚠️ **Circuit breaker is mandatory before enabling dual write.** If the new service goes down, the monolith's HTTP calls will block until timeout. At scale, blocked threads fill the monolith's thread pool and cascade into monolith unavailability — a second service's failure takes down the entire monolith. Wrap the new service call in a circuit breaker (Resilience4j in Java): after N consecutive failures, the circuit opens, the monolith stops calling the new service and logs the missed writes to drift_log. The reconciliation job catches up. The monolith's core path is never degraded by the new service.

> ⚠️ **Service-to-service authentication must be in place before dual write.** The monolith calls the new service's write API as a service, not as a user. This call needs its own credential — mTLS, a short-lived internal JWT, or a service-account API key. If you expose the new service's write API without auth "for now, we'll add it later," you have an unauthenticated write endpoint in production. Add auth before Phase 2 starts, not after.

---

### Phase 3 — Shadow Read Validation

**What you do:**
- Route 5% of READ traffic to the new service
- Do NOT show new service response to users — return old service's response
- Compare responses and log mismatches
- Run background job for inactive users

**Say in interview:**
> *"Now I shadow 5% of reads to the new service. Users still see the old service's response. I compare both responses and alert on mismatches. But 5% canary only covers active users — I also run a background job that proactively validates inactive users who haven't logged in recently."*

**Inactive user background job (THE KEY THING TO SAY PROACTIVELY):**
```
Nightly background job (READ-ONLY):

  Fetch: users with last_login > 30 days ago
  For each:
    old_result = GET /notifications?userId=X from old service
    new_result = GET /notifications?userId=X from new service
    compare → log mismatch → re-sync if needed

CRITICAL: This job NEVER writes to either DB.
          It only reads and compares.
          Writing would corrupt the dual-write state.
```

> **See:** `13-shadow-mode-dark-launch.md` for the full shadow mode design including side-effect suppression.

---

### Phase 4 — Traffic Ramp (Earn 100%)

**What you do:**
- Mismatch rate < 0.1% for 14 days → start showing new service responses to users
- Ramp gradually: 5% → 25% → 50% → 100%
- Define rollback trigger BEFORE starting

**Say in interview:**
> *"Once shadow validation passes, I ramp reads to the new service gradually. I define the rollback trigger upfront: if error rate exceeds 0.5% or latency degrades by more than 20%, we route back to old service immediately."*

```
Ramp schedule:
  Week 1: 5%  user traffic → new service  (monitoring)
  Week 2: 25% user traffic → new service
  Week 3: 50% user traffic → new service
  Week 4: 100% user traffic → new service

Rollback trigger (automated):
  If new_service.error_rate > 0.5%
  OR new_service.p99_latency > old_service.p99_latency × 1.2
  → route 100% back to old service
  → alert team
  → investigate before re-attempting ramp
```

---

### Phase 5 — Decommission (Starvation, Not Cutting)

**What you do:**
- After 2 weeks of stable 100% traffic on new service
- Remove notification code from monolith
- Archive old DB table → wait 30 days → drop

**Say in interview:**
> *"The monolith's notification code is removed only after the new service has run at 100% for 2 weeks with no incidents. The old DB table is archived — not dropped immediately. We wait 30 days for any audit or data recovery requests before dropping it."*

```
Decommission order (order matters):
  1. Remove dual-write code from monolith
  2. Remove old service endpoints (return 410 Gone if called)
  3. Mark old DB table as READ-ONLY
  4. Wait 30 days
  5. Archive old table data to S3 cold storage
  6. DROP old table

DO NOT drop the table before archiving.
DO NOT skip the 30-day wait.
```

---

## ⚠️ The 4 Questions the Interviewer Will Probe

**"What is your rollback plan?"**
> Rollback trigger is defined before Phase 4 starts — error rate or latency threshold. If triggered, the load balancer routes 100% back to old service in under 60 seconds. Old DB is still populated (dual write ran through Phase 4), so no data is lost. This is the reason you don't decommission until you're confident.

**"How do you handle the users who haven't logged in for weeks?"**
> Canary alone won't cover them. I run a nightly read-only background job that proactively fetches their data from both old and new service and compares. Any mismatch is re-synced. No traffic ramp past 25% until this job has cleared all inactive user cohorts.

**"What if the new service has a bug that only affects a rare edge case?"**
> That's exactly what shadow mode is for. We run the new service against 100% of traffic in shadow mode — users never see the output — so even rare requests that only occur once a week will hit the shadow. The comparison job surfaces edge case mismatches before any user is affected.

**"How do you handle deletes during dual write?"**
> All deletes are soft deletes (tombstone) during the migration window. The deleted_at timestamp is synced to both DBs by the dual-write path. Hard deletes only happen after migration is complete and old table is decommissioned. This prevents the "deleted in new service, still visible in old" problem.

---

## 🧾 TL;DR — What to Say in the Interview (2 Minutes)

> *"I'd use the Strangler Fig pattern — five phases. First, build the new service and backfill existing data, no user traffic. Second, enable dual write — but via the new service's write API, not direct DB writes. Monolith writes to old DB first, then calls the new service's write endpoint so the full write path gets tested under real load. Old DB is source of truth. Reconciliation job catches drift every 15 minutes. Phase 2 ends only after the new service's write error rate is below 0.1% for 7 days — that's write confidence. Third, shadow 5% of reads to the new service but always return the old service's response to users — compare both and alert on mismatch. Critically, I also run a nightly read-only background job to validate inactive users who never hit the canary. Fourth, once mismatch rate is below 0.1% for 14 days, ramp traffic to the new service — 5% to 25% to 50% to 100% — with a defined rollback trigger on error rate or latency. Fifth, after two weeks at 100%, remove the monolith code and archive the old table. I decommission by starvation — never by cutting."*

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 11, 2026 | **Note created.** First scenario in Operational-Scenarios folder. Directly maps to live interview question (notification service migration). Incorporates inactive user validation insight that interviewer had to prompt — now built into the playbook as a proactive step. |
| Jul 11, 2026 | **Critical bug fix — Phase 2 dual-write framing.** Original heading "Monolith Writes to Both DBs" implied direct DB writes, bypassing the new service's write logic entirely. Corrected to "Dual Write via New Service API" — monolith calls new service's write endpoint, new service writes to its own DB. Added write validation gate (7-day <0.1% error rate target) before moving to Phase 3. Updated TL;DR to reflect the correct model. |
| Jul 11, 2026 | **Senior tech lead audit fixes.** Added Phase 2 mandatory prerequisites: (1) circuit breaker wrapping new-service HTTP calls to prevent monolith thread-pool exhaustion if new service is down; (2) service-to-service authentication (mTLS / internal JWT / service-account key) must be in place before exposing new service's write API. Both are prerequisites before enabling dual write, not afterthoughts. |
