# Operational Scenario: Production Data Fixes

> **When this appears in an interview:** Interviewer says "a bug ran and wrote bad data to production — what do you do?" or "a job deleted more rows than it should have — how do you recover?" The keyword is **production data** combined with **bad state**, **corruption**, **wrong values**, **missing rows**, or **accidental delete**.
> **Patterns used:** Feature Flag Gating (`14-feature-flag-gating.md`) — kill the bug-triggering code path before fixing data. Dual Write (`12-dual-write-reconciliation.md`) — reconciliation pattern applies to verification after fix.

---

## 🎯 The Situation

A bug ran in production. The wrong values were written. Or rows were deleted that shouldn't have been. Or a migration backfilled incorrect data. Users are seeing wrong state. You need to fix it — safely, without making it worse.

**Classic triggers in interviews:**
- "An UPDATE query ran without a WHERE clause and updated all 2M rows"
- "A job charged customers the wrong price — how do you fix the data?"
- "A migration script ran on the wrong environment"
- "Soft-delete logic had a bug and hard-deleted 50,000 user records"

---

## 🧠 The Decision You Make First

Before anything else, ask one clarifying question:

> *"What type of data corruption are we dealing with — wrong values written, rows deleted, rows not created, or a cascading downstream effect?"*

The answer determines what can be recovered and how:

| Type | Recovery options | Complexity |
|---|---|---|
| Wrong values written | SELECT the correct values, UPDATE to correct state | Moderate |
| Rows deleted (soft delete or backup available) | Restore from backup or un-delete soft-delete flag | Moderate |
| Rows hard-deleted (no backup) | Reconstruct from audit log, event log, or downstream records | High |
| Missing rows (should have been created, weren't) | Backfill from a source of truth (partner API, event log) | High |
| Cascading downstream effects | Fix DB row + remediate downstream (email correction, refund, etc.) | Very high |

A cascading downstream effect (wrong email sent, wrong charge processed) requires a separate remediation plan beyond the DB fix — the DB fix alone does not undo what already happened downstream.

---

## 🎨 Visual — The Audit → Backup → Fix → Verify Loop

> **Before:** bad data in production, bug still running, scope unknown, users seeing wrong state.
> **After:** source killed, scope audited, fix applied in batches, verified at zero, downstream effects remediated.

```
INCIDENT: bad data in production
         │
         ▼
┌──────────────────────────────────────────────────┐
│  STEP 0: KILL THE SOURCE                          │
│                                                   │
│  The bug that wrote bad data may still be running │
│  → Kill the feature flag / deploy the fix FIRST  │
│  → Verify no more bad writes are happening       │
│  → Only then fix existing bad data               │
└──────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────────┐
│  STEP 1: AUDIT — scope the damage (READ ONLY)    │
│                                                   │
│  Write a SELECT version of your fix.              │
│  COUNT(*) the affected rows.                      │
│  Understand the EXACT bad condition.              │
│  Run on READ REPLICA to avoid lock on primary.   │
└──────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────────┐
│  STEP 2: BACKUP — snapshot before touching        │
│                                                   │
│  Export the affected rows to a backup table.     │
│  Or: take a DB snapshot / PITR checkpoint.       │
│  Verify the backup is queryable before fixing.   │
│  Without backup → if your fix is wrong, no undo. │
└──────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────────┐
│  STEP 3: DRY RUN — BEGIN + ROLLBACK in console   │
│                                                   │
│  BEGIN;                                           │
│  [your fix DML]                                   │
│  SELECT COUNT(*), sample rows;   -- inspect!     │
│  ROLLBACK;           -- no commit, just verify   │
│                                                   │
│  Only if dry-run row count matches audit → commit │
└──────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────────┐
│  STEP 4: FIX — small batches, not one giant DML  │
│                                                   │
│  Wrap in a transaction (all-or-nothing).         │
│  Fix in batches of 1000 rows — monitor per batch.│
│  Large single DML holds locks for minutes.       │
│  Batches allow rollback per chunk if needed.     │
└──────────────────────────────────────────────────┘
         │
         ▼
┌──────────────────────────────────────────────────┐
│  STEP 5: VERIFY — re-run your audit query        │
│                                                   │
│  SELECT COUNT(*) WHERE <bad-condition>           │
│  Expected result: 0 rows.                        │
│  Spot-check 5–10 rows that were fixed.           │
│  Re-run all business logic checks that failed.  │
└──────────────────────────────────────────────────┘

KEY INVARIANT:
   Never fix data without first auditing the scope (Step 1),
   and never commit the fix without first dry-running it (Step 3).
   A second bad data fix on top of the first doubles the incident.
```

---

## ⚠️ PREREQUISITES — Must Exist Before You Can Fix Production Data

> **Class 4 (missing prerequisites):** The ability to safely fix production data depends on infrastructure that must exist before the incident. If it doesn't, you're attempting surgery without instruments.

**Required:**

| Prerequisite | Why it matters | What happens without it |
|---|---|---|
| **Point-in-time recovery (PITR)** | Roll back the DB to the moment before the bug ran | Without PITR, hard-deleted rows are gone permanently |
| **Read replica** | Run your audit SELECT without locking the primary | SELECT on primary during a fix may block user queries |
| **Audit / event log** | Reconstruct what values were correct before the bug | Without event log, you cannot know the "before" state for all rows |
| **DB change history** | Which migration ran and when | Without this, you don't know where the bad data starts |
| **Query access to prod (controlled)** | Authorized access to run fixes | Without a defined access process, the fix itself is unauthorized |

If PITR is not enabled on your production DB, that is itself a critical gap — add it as a post-incident action item.

---

## 🗂️ The 4-Phase Playbook

---

### Phase 0 — Kill the Source First

**Say in interview:**
> *"My first step is not to fix data — it's to kill the source of the bad writes. If the bug is still running, any fix I apply will be re-corrupted. Kill the feature flag or roll back the deploy, confirm the bad writes have stopped, then audit scope. Starting with data fixes while the bug is live is like bailing water from a boat with a hole still in it."*

> **This is the step most people skip.** If the bug is still running, fixing the data is pointless — the bad writes will re-corrupt the rows you just fixed.

**Do before anything else:**

```
1. Identify: is the bug still writing bad data RIGHT NOW?
   → Check the write rate on the affected table
   → Check application logs for the bug-triggering code path

2. Stop the bug:
   → Kill switch the feature flag if the bug is in a flagged feature
   → Roll back the bad deploy
   → Disable the cron job / batch job if it's the source
   → Increase DB query access rate limits if it's a runaway query

3. Verify the bad writes have stopped:
   → Watch the affected table's write rate drop to baseline
   → Only proceed when you're confident no more bad data is being written

DO NOT start fixing data if the bug is still writing.
```

---

### Phase 1 — Audit (Read Only — Scope the Damage)

**Say in interview:**
> *"Before touching anything, I audit scope. I write a SELECT version of my fix — same WHERE clause I plan to use in the UPDATE — and run it on a read replica to avoid locking the primary. I COUNT(*) the affected rows and sample 10 of them to confirm they all match the bad condition. If the sample has healthy rows, my WHERE clause is too broad and I narrow it before proceeding."*

**The rule: write a SELECT version of your fix first. Always.**

```sql
-- Wrong: jump straight to the fix
-- UPDATE orders SET status = 'pending' WHERE ...

-- Right: SELECT first to understand scope
SELECT COUNT(*)
FROM orders
WHERE status = 'shipped'
  AND shipped_at IS NULL   -- the bad condition: shipped but no timestamp
  AND created_at > '2026-07-10 14:00:00'  -- narrow to the bug window
;

-- Result: 2,847 rows affected
-- You now know exactly what you're fixing and how many rows
```

**Run on a read replica, not the primary:**

```
Why:
  Your SELECT may take 5–30 seconds on a large table.
  SELECT on the primary holds shared locks.
  Shared locks block concurrent writes during that window.
  Run on replica → zero impact on production write path.

How:
  Most cloud DBs provide replica endpoints.
  Your DB client tool should have a "connect to replica" option.
  If you have no replica access → be extra careful with lock-holding SELECTs.
```

**Audit checklist:**
- Count: how many rows are affected?
- Sample: fetch 10 rows — do they all match the bad condition?
- Boundary: what was the first bad row created? (timestamp — define the bug window)
- Downstream: which downstream systems read from these rows? (services, BI tools, ETL jobs)

---

### Phase 2 — Backup (Before Touching Anything)

**Say in interview:**
> *"Before any fix, I create a backup table with a SELECT INTO — the exact rows I'm about to change. If my fix turns out to be wrong, I can restore from the backup with a simple UPDATE...FROM. Without a backup, a bad fix is unrecoverable. I verify the backup row count matches my audit before proceeding."*

**The rule: export the rows you're about to change, before you change them.**

```sql
-- Option A: copy to a backup table in the same DB
CREATE TABLE orders_backup_20260711 AS
SELECT *
FROM orders
WHERE status = 'shipped'
  AND shipped_at IS NULL
  AND created_at > '2026-07-10 14:00:00'
;

-- Verify it: ensure row count matches your audit
SELECT COUNT(*) FROM orders_backup_20260711;
-- Should match: 2,847

-- Option B: export to S3/GCS as a CSV (if you need it outside the DB)
-- Use your DB's export tooling (pg_dump, BigQuery export, RDS snapshot)
```

**If you have PITR enabled:**

```
Before any fix, note the exact timestamp: 2026-07-11 09:42:00 UTC.
If your fix goes wrong, you can restore to this point.
But: PITR restores the ENTIRE DB — it will undo all writes in the
restore window, not just your fix. Use PITR as a last resort,
not as a replacement for the backup table approach.
```

---

### Phase 3 — Fix (Dry Run First, Then Batch Commit)

**Say in interview:**
> *"I never commit a fix without dry-running it first: BEGIN, run the DML, inspect the row count and sample the updated values, ROLLBACK. Only if the dry-run count matches my audit do I commit. Then I apply in batches of 500 rows — not one giant UPDATE — so locks are held for milliseconds per batch, not minutes. Each batch is idempotent: the loop re-runs only on rows still matching the bad condition, so a crash halfway through is safe to resume."*

**Step 1: Dry run with BEGIN + ROLLBACK — mandatory.**

```sql
-- Dry run: test the fix without committing
BEGIN;

UPDATE orders
SET
    status = 'pending',
    shipped_at = NULL
WHERE status = 'shipped'
  AND shipped_at IS NULL
  AND created_at > '2026-07-10 14:00:00'
;

-- Inspect before committing:
-- How many rows did it affect?
-- Do the updated values look correct?
SELECT COUNT(*), status, shipped_at
FROM orders
WHERE created_at > '2026-07-10 14:00:00'
  AND status = 'pending'
  AND shipped_at IS NULL
LIMIT 10;

-- If the row count matches the audit and the sample looks correct:
-- COMMIT;

-- If anything looks wrong:
ROLLBACK;
-- Fix the query and repeat the dry run.
```

**Step 2: Apply in batches — not one giant DML.**

```sql
-- BAD: single UPDATE on 2,847 rows holds an exclusive lock the entire time
UPDATE orders
SET status = 'pending', shipped_at = NULL
WHERE status = 'shipped' AND shipped_at IS NULL
  AND created_at > '2026-07-10 14:00:00';

-- GOOD: batch by 500 rows at a time
DO $$
DECLARE
    updated_count INT;
BEGIN
    LOOP
        UPDATE orders
        SET status = 'pending', shipped_at = NULL
        WHERE id IN (
            SELECT id FROM orders
            WHERE status = 'shipped'
              AND shipped_at IS NULL
              AND created_at > '2026-07-10 14:00:00'
            LIMIT 500
        );

        GET DIAGNOSTICS updated_count = ROW_COUNT;
        EXIT WHEN updated_count = 0;

        -- Small sleep between batches: reduces lock contention
        PERFORM pg_sleep(0.1);
    END LOOP;
END $$;
```

**Why batches:**
- Each batch holds locks only for the duration of that 500-row update (milliseconds)
- If the fix crashes halfway, rows already fixed stay fixed (the loop is resumable)
- You can monitor progress between batches — if something looks wrong, stop

**Make the fix idempotent:**

```
Idempotent (safe to run twice — gives same result):
  UPDATE orders SET status = 'pending'
  WHERE status = 'shipped' AND shipped_at IS NULL;
  → If you run this twice: second run updates 0 rows (none match anymore). Safe.

NOT idempotent (dangerous to run twice):
  INSERT INTO orders SELECT * FROM orders_backup_20260711;
  → First run: 2,847 rows inserted. Second run: 2,847 duplicate rows inserted.
  → Fix: add ON CONFLICT DO NOTHING using the primary key.
```

---

### Phase 4 — Verify and Monitor

**Say in interview:**
> *"After the fix, I re-run the original audit query — expected result: zero rows. I spot-check 10 rows that were fixed to confirm the values look correct. Then I watch for 30 minutes for any downstream re-corruption. The fix is only complete when the metric is zero and it stays zero."*

**Re-run the audit query — expected result: 0.**

```sql
-- Your original audit query
SELECT COUNT(*)
FROM orders
WHERE status = 'shipped'
  AND shipped_at IS NULL
  AND created_at > '2026-07-10 14:00:00'
;
-- Expected: 0
-- If non-zero: some rows were missed — check why
```

**Spot-check a sample of fixed rows:**

```sql
SELECT id, status, shipped_at, updated_at
FROM orders
WHERE id IN (SELECT id FROM orders_backup_20260711 LIMIT 10);
-- Verify: status is 'pending', shipped_at is NULL, updated_at is current
```

**Watch for downstream re-corruption:**
- Check if any downstream job is re-writing the bad values (the bug may have been in a background job that's still scheduled)
- Watch the error rate on user-facing reads of the affected rows

---

## ⚠️ The Hard Cases: Cascading Downstream Effects

> **This is the part most notes skip — and it's what a senior engineer thinks about immediately.**

A DB fix restores the data. It does NOT undo what the bad data triggered:

```
Scenario: a pricing bug charged customers $0.00 instead of $49.99

DB fix:
  UPDATE orders SET price = 49.99 WHERE price = 0.00 AND ...
  → Order table now shows correct price. ✅

What the DB fix does NOT fix:
  ✗ Payment gateway already processed the $0.00 charge
  ✗ Customers already received a "$0.00 charge" email confirmation
  ✗ Finance systems already reconciled the $0.00 revenue
  ✗ Inventory was decremented based on the bad order
```

**How to handle cascading effects:**

| Downstream effect | Remediation |
|---|---|
| Email sent with wrong data | Send a correction email (or a "we're sorry" email with the correct information) |
| Wrong charge processed | Issue refunds and re-charge, OR absorb the loss — requires a business decision, not a DB fix |
| Finance/reporting shows wrong numbers | Re-run reconciliation job after DB fix; flag affected period for manual review |
| Inventory decremented incorrectly | Run a separate inventory reconciliation job |
| Downstream service cached the bad data | Invalidate the relevant cache keys after the DB fix |

**The rule:** for every affected row, ask "what events did this row trigger?" Each triggered event that already fired needs its own remediation plan — the DB fix is the beginning, not the end.

---

## 🧩 Interview Probe Q&As

**"What's your first action when you discover bad data in production?"**
> Kill the source of the bad data before touching the data itself. If the bug is still running, any fix I apply will be re-corrupted. Kill the feature flag or roll back the deploy first, confirm the bad writes have stopped, then move to auditing scope. Starting with data fixes while the bug is live is like bailing water from a boat with a hole still in it.

**"How do you figure out how many rows are affected?"**
> Write a SELECT version of the fix first — with the same WHERE clause I plan to use in the UPDATE. Run it on a read replica (not primary) to avoid lock contention. COUNT(*) gives me the scope. I also pull a sample of 10 rows to confirm they all match the expected bad condition — if the sample has healthy rows in it, my WHERE clause is too broad and I need to narrow it.

**"What if the fix script crashes halfway through?"**
> Two answers: (1) I wrap the fix in a transaction — if it crashes, the transaction rolls back and we're at the state before the fix started. (2) I run in batches using a loop, where each batch commits independently. If the loop crashes on batch 15, batches 1–14 are already committed, and I resume from batch 15 — the loop re-runs only on rows that still match the bad condition, making it naturally idempotent. Which approach I use depends on whether I can afford a single large transaction lock or need smaller batches.

**"What if you accidentally fix the wrong rows — how do you recover?"**
> That's why I created the backup table in Step 2 before touching anything. I restore from the backup: `UPDATE orders SET status = original_status FROM orders_backup_20260711 WHERE orders.id = orders_backup_20260711.id`. If the damage is broader and the backup table doesn't cover all the wrong rows, I use PITR to restore the DB to the timestamp I noted before the fix. PITR is a last resort because it rolls back ALL writes in the restore window — not just my fix.

**"How do you fix data without triggering downstream business logic a second time?"**
> Apply the fix directly at the DB level, not through the application layer. The application layer has event listeners, notification triggers, and validation that may re-fire on a write. A direct DB UPDATE bypasses all of that — it changes only the stored value without triggering any business logic. The tradeoff is that ORM-layer hooks (Hibernate @PreUpdate listeners, Postgres triggers) may still fire — check for them before running the fix. If a Postgres trigger is attached to the table, the fix must either suppress the trigger temporarily or be designed to be a no-op when the trigger fires on the corrected data.

**"What's your post-fix process?"**
> After verifying the fix: (1) re-run all the business checks that should now pass (e.g., order status reports, counts by status), (2) watch for 30 minutes for any downstream re-corruption, (3) notify customer support of which user IDs were affected so they can proactively handle inbound queries, (4) write a post-mortem: what was the root cause, what allowed the bug to write to production, what's being added to prevent recurrence (input validation, shadow mode, staging environment parity).

---

## 🧾 TL;DR — What to Say in the Interview (2 Minutes)

> *"My first step isn't to fix data — it's to kill the source of the bad writes. If the bug is still running, my fix will be re-corrupted. Kill the feature flag or roll back the deploy first. Once writes stop, I audit: SELECT version of my fix on a read replica, COUNT(*) the affected rows. Then I create a backup table with the exact rows I'm about to change — without it, a bad fix is unrecoverable. Then a dry run: BEGIN, run the DML, inspect the row count, ROLLBACK. Only if the dry run matches my audit do I commit. I apply in batches of 500 rows, not one giant UPDATE, so locks are held milliseconds per batch. After fixing, I re-run the audit query and expect zero rows. The DB fix is the easy part — the hard part is cascading downstream effects: emails sent with bad data, payments charged at wrong amounts, finance reports showing wrong numbers. Each requires its own remediation plan — not just a DB UPDATE."*

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 11, 2026 | **Note created.** Batch 3 of Operational-Scenarios gap closure. Production data fixes — a high-signal interview question for senior engineers. Written with 8 known mistake classes applied: Step 0 (kill the source) is a Class 4 prerequisite that most notes miss; backup table is a Class 4 prerequisite (without it, a bad fix is unrecoverable); batch approach over single DML is a Class 5 consideration (what state is left if the fix crashes halfway); cascading downstream effects section covers what the DB fix cannot undo — the differentiating part of a senior answer. |
