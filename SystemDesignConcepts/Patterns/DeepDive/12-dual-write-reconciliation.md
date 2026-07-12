# Pattern Deep Dive: Dual Write + Reconciliation

> **Read this when:** You are migrating data from one DB to another, extracting a service, or changing data stores — and you need both the old and new system to stay in sync during the transition without losing data.
> **Pre-interview refresh:** Read the KEY INSIGHT + the failure mode table (5 min).

---

## 🎯 What Problem Does This Pattern Solve?

You are migrating a service from one DB to another (or from a monolith to a microservice). You cannot stop the world and do a one-shot migration — the system must stay live. But you also cannot flip to the new DB instantly — the new one is unproven.

You need a **transition period** where both DBs receive the same writes, so that:
- If the new DB has a bug, you can fall back to the old DB without data loss
- You can validate the new DB has correct data before cutting reads over
- You can ramp traffic gradually without gaps in the new DB's history

This is the **dual-write pattern**: during migration, every write goes to both the old and the new data store simultaneously.

> **Critical distinction — where do you write?**
>
> | Migration type | Write target |
> |---|---|
> | DB migration within same service (e.g., Postgres → Cassandra) | Service writes directly to both DBs |
> | Microservice extraction (monolith → new service) | Monolith calls NEW SERVICE's write API → new service writes to its own DB |
>
> The second case is not optional — if the monolith bypasses the new service and writes directly to the new DB, the new service's write logic (validation, event publishing, business rules) never runs under production load. When traffic finally shifts to the new service, it processes real writes for the first time. That is a blind cutover masquerading as a migration.

---

## 💡 Core Insight

**One DB must be the source of truth. Both cannot be authoritative simultaneously.** Dual write does not mean equal authority — it means the old DB is still the answer if the two disagree. The new DB is being populated in parallel and validated, not yet trusted.

> **KEY INSIGHT:** "Dual write is not synchronization — it is shadow population. The old DB is source of truth. The new DB is the candidate. Reconciliation is the judge. You promote the new DB to source of truth only after the reconciliation job gives the all-clear."

---

## 🎨 Visual — Dual Write Data Flow

```
WRITE PATH — same-service DB migration (both DBs receive every write)
──────────────────────────────────────────────────────────────

    Client
          │
          │ Write request: "create notification for user 42"
          ▼
    ┌─────────────────────────────────┐
    │   Service Layer                  │
    │                                  │
    │  1. Write to OLD DB  ──────────▶ ┌──────────────────┐
    │     (source of truth)            │   OLD Notif DB    │
    │     if this fails → abort both   │   (authoritative) │
    │                                  └──────────────────┘
    │  2. Write to NEW DB  ──────────▶ ┌──────────────────┐
    │     if this fails:               │   NEW Notif DB    │
    │     - log the failure            │   (candidate)     │
    │     - alert (drift starts here)  └──────────────────┘
    │     - do NOT abort the request
    │       (old DB write already committed)
    └─────────────────────────────────┘

WRITE PATH — microservice extraction (monolith calls new service's API)
──────────────────────────────────────────────────────────────

    Client
          │
          │ Write request: "create notification for user 42"
          ▼
    ┌──────────────────────────────────┐
    │   MONOLITH                        │
    │                                   │
    │  1. Write to OLD DB  ───────────▶ ┌──────────────────┐
    │     (source of truth)             │   OLD Notif DB    │
    │     if this fails → abort         │   (authoritative) │
    │                                   └──────────────────┘
    │  2. HTTP POST to NEW SERVICE ───▶ ┌──────────────────────────────┐
    │     write endpoint                │   NEW Notification Service    │
    │     if this fails:                │   (validates + applies        │
    │     - log drift, do NOT abort     │    business logic)            │
    │     (old DB committed)            └──────────────┬───────────────┘
    └──────────────────────────────────┘               │
                                                        ▼ writes to
                                               ┌──────────────────┐
                                               │   NEW Notif DB    │
                                               │   (candidate)     │
                                               └──────────────────┘


READ PATH (depends on migration phase)
──────────────────────────────────────────────────────────────

Phase 1-2:  ALL reads  ──▶ OLD DB
Phase 3:    5% reads   ──▶ NEW DB  (compare, don't show)
Phase 4:    50% reads  ──▶ NEW DB  (show to user)
Phase 5:    100% reads ──▶ NEW DB  (old DB decommissioned)


RECONCILIATION JOB (runs independently, always)
──────────────────────────────────────────────────────────────

Every N minutes (e.g., every 15 min):

  SELECT id, user_id, status, updated_at
  FROM old_db.notifications
  WHERE updated_at > last_check_time
    AND updated_at < NOW() - INTERVAL '5 seconds'   ← grace window

  For each row:
    → fetch same id from new_db.notifications
    → compare: id, user_id, status, message, deleted_at
    → if mismatch → log + alert + write to drift_log table
    → if missing in new DB → re-sync that row

  WHY THE 5-SECOND GRACE WINDOW:
  If a soft-delete lands in old DB at T+0 and new DB at T+0.5s,
  the reconciliation job running at T+0.3s sees a mismatch that isn't real.
  Excluding rows updated in the last N seconds eliminates false alerts from
  the propagation lag between old-DB-write and new-service-API-write.
  The next reconciliation cycle (15 min later) will see the resolved state.

KEY INVARIANT:
   If old DB write fails → abort the entire request.
   If new DB write fails → complete the request, log the drift.
   The old DB is NEVER the one that fails silently.
```

---

## 🗂️ The 3 States of a Dual-Write System

---

### State 1 — Dual Write Active (old DB = source of truth)

Both DBs receive every write. All reads come from old DB. New DB is being populated but not yet trusted.

**What runs:**
- Every write path: write old DB first, then write new DB
- Reconciliation job: every 15 minutes, compare both DBs
- Alert: if drift rate > threshold, page the on-call team

**Duration:** Until shadow read validation passes (typically 1–2 weeks)

---

### State 2 — Read Traffic Shifting (new DB earns reads)

Reads start shifting to the new DB. Dual write continues. New DB is now partially trusted for reads.

```
Reads:   5% → new DB  (shadow, compare only)
        25% → new DB  (serve to user)
        50% → new DB  (serve to user)
Writes:  still going to BOTH DBs
```

**What changes:** The reconciliation job now also validates read responses — are users seeing the same data from both DBs?

---

### State 3 — Single Write (new DB = source of truth)

100% reads on new DB. Dual write phase ends. Old DB becomes read-only archive.

```
Writes: → NEW DB only
Reads:  → NEW DB only
OLD DB: → read-only (archived, not yet dropped)
```

**When to move here:** After 2 weeks of 100% reads on new DB with zero mismatch alerts from reconciliation job.

---

## ⚠️ The 3 Failure Modes That Will Definitely Happen

**1. Partial write — old DB succeeds, new DB fails**

```
Service writes to old DB ✅
Service writes to new DB ✗ (timeout)
Result: old DB has the row, new DB doesn't
        → drift starts accumulating silently
```

Fix: Reconciliation job catches this within 15 minutes. Alert fires. On-call re-syncs the drifted rows. Do NOT retry the new DB write inline — that adds latency to the user's request for a non-authoritative DB.

**2. Delete in one DB, not the other**

User deletes a notification. Service deletes from old DB. New DB write times out.
Result: notification is "deleted" from user's perspective (old DB is truth) but still exists in new DB.
When reads switch to new DB, the "deleted" notification reappears.

Fix: Use soft delete (tombstone) in both DBs. Reconciliation catches the gap. Hard delete only after migration is complete and reads have been on new DB for 30 days.

**3. Schema divergence discovered mid-migration**

Old DB has a column (e.g., `legacy_priority_flag`) that new DB schema doesn't include.
Reconciliation job starts failing because it can't compare like-for-like.

Fix: Before starting dual write, audit both schemas. Every column that needs to carry over must be explicitly mapped. Columns you're intentionally dropping must be excluded from reconciliation comparison.

---

## 🧭 Source of Truth Decision — Always Explicit

Before starting dual write, write this down and share with the team:

```
Source of truth during dual-write phase: OLD DB
Promotion trigger: reconciliation job shows 0 mismatches for 14 days
Rollback trigger: any mismatch rate > 0.1% in a 15-min window
Rollback action: stop reads from new DB, alert team, investigate drift
```

If this isn't written down, someone will change it during an incident. Write it down.

---

## 🧩 Interview Probe Q&As

**"What if the new DB write fails? Do you fail the whole request?"**
> No. The old DB write committed successfully — that's the source of truth. Failing the whole request would mean telling the user their action failed when it actually succeeded. Instead: log the failure to a drift table, alert the on-call team, and let the reconciliation job re-sync the row within 15 minutes.

**"How long do you run dual write?"**
> Until the reconciliation job shows zero mismatches for 14 consecutive days AND 100% read traffic has been on the new DB for at least 2 weeks. Rushing this phase is the most common cause of data loss in migrations.

**"How does the reconciliation job work without impacting production?"**
> It runs as a separate background process with its own read replica connections — it never touches the primary DB writers. It batches comparisons in small windows (e.g., rows updated in the last 15 minutes) so it never does a full table scan in production hours.

**"What's the difference between dual write and the outbox pattern?"**
> Dual write is for migrating data between two DBs during a service extraction — you control both DBs. The outbox pattern (see `07-cdc-outbox.md`) is for reliable event publishing to Kafka — you write a DB row and a Kafka event in the same local transaction so they're atomic. Different problem: dual write handles DB-to-DB migration; outbox handles DB-to-message-queue reliability.

**"What if the new service goes down during dual write — does it take the monolith down with it?"**
> Yes, if unprotected — the monolith's HTTP call to the new service will block until timeout, and at scale, blocked threads fill the thread pool and cascade into monolith unavailability. The fix is a circuit breaker (Resilience4j in Java) wrapping the new-service call: after N consecutive failures, the circuit opens and the monolith stops calling the new service for a cooldown window, logs the skipped writes to drift_log, and lets the reconciliation job catch up. The circuit breaker is mandatory before enabling dual write in Phase 2.

**"How does the monolith authenticate to the new service's write API?"**
> Service-to-service auth, not user auth. Options: (a) mTLS — both services present certificates, infrastructure handles it; (b) short-lived JWT issued by your internal auth service, verified by the new service; (c) API key scoped to the monolith's service account. The choice depends on what your infrastructure supports. The key constraint: the credential must be rotatable without code deployment.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 11, 2026 | **Note created.** Triggered by live interview — dual write came up directly during notification service migration question. Partial write failure mode and soft-delete gap both appeared in the real interview. |
| Jul 11, 2026 | **Clarification — microservice extraction vs DB migration write targets.** Added critical distinction callout and second diagram showing that microservice extraction dual-write must route through the new service's write API, not write directly to the new DB. Direct DB write bypasses the service's business logic and gives false write confidence. |
| Jul 11, 2026 | **Senior tech lead audit fixes.** (1) Added 5-second grace window to reconciliation job query — prevents false mismatch alerts when soft-delete propagation lag between old DB write and new service write falls within the reconciliation window. (2) Added probe Q&As: circuit breaker for new-service downtime protection, and service-to-service authentication for the write API call. |
