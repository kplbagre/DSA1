# Operational Scenario: Zero-Downtime Schema Changes

> **When this appears in an interview:** "How do you add a NOT NULL column to a table with 500 million rows?" or "How do you rename a column in production?" or "How do you change a data type on a heavily-used table without downtime?" The keyword is **live system** — they want to hear that you know a naive `ALTER TABLE` kills production.
> **Pattern used:** Expand-Migrate-Contract (`11-expand-migrate-contract.md`)

---

## 🎯 The Situation

Your database is live. Real traffic hitting it — 5,000 requests per second. The schema needs to change. A developer opens a migration file and writes:

```sql
ALTER TABLE users RENAME COLUMN user_name TO full_name;
```

On a 500M row table this acquires an **exclusive lock** — all reads and writes blocked — for minutes or hours. At 5,000 RPS that means hundreds of thousands of failed requests. The on-call phone rings within 30 seconds.

**The correct answer is never a one-shot ALTER TABLE on a large, live table.** It is always Expand → Migrate → Contract across two deployments and a background job.

---

## 🧠 The Decision You Make First

Before answering, ask:

> *"What is the table size, and what's the approximate write throughput? That determines whether we need the full Expand-Migrate-Contract approach or if a maintenance window is acceptable."*

| Answer | What to do |
|---|---|
| Table < 1M rows, low traffic | Maintenance window is acceptable — brief ALTER TABLE with app paused |
| Table > 10M rows OR > 1,000 RPS | Full Expand-Migrate-Contract — zero downtime mandatory |
| Table > 500M rows | Expand-Migrate-Contract + batched migration with rate limiting |

---

## 🎨 Visual — What Happens Without This Pattern

```
NAIVE APPROACH (what NOT to do)
──────────────────────────────────────────────────────────────

  ALTER TABLE users RENAME COLUMN user_name TO full_name;

       ┌────────────────────────────────┐
       │   EXCLUSIVE TABLE LOCK         │
       │                                │
       │   All reads  ─── BLOCKED ───▶ │ (connection queue builds up)
       │   All writes ─── BLOCKED ───▶ │
       │                                │
       │   Duration: minutes to hours   │
       │   on 500M row table            │
       └────────────────────────────────┘

  Result: On-call alert fires in 30 seconds.
          Connection pool exhausted in 60 seconds.
          App restarts cascade. Full outage.


CORRECT APPROACH (Expand-Migrate-Contract)
──────────────────────────────────────────────────────────────

Deploy 1                   Background Job              Deploy 2
──────────                 ──────────────              ────────
Add full_name              Backfill:                   Switch reads
column (nullable)          UPDATE in batches            to full_name
                           of 1,000 rows               Drop user_name
Write to BOTH              No table lock               (fast — nothing
Read from user_name        Runs off-peak               uses it now)

   │                            │                          │
   ▼                            ▼                          ▼
Zero user impact         Gradual, low risk           Zero user impact

KEY INVARIANT:
   At no point is the app without a readable column.
   user_name exists until full_name is proven safe.
   The DROP only happens after full_name has served reads for 7+ days.
```

---

## 🗂️ The Playbook — By Schema Change Type

---

### Case 1: Rename a Column

**Say in interview:**
> *"I never rename directly on a live table. I add the new column, dual-write during a transition period, backfill old rows in batches, switch reads to the new column, then drop the old one. Three steps, two deployments, one background job."*

```
Phase 1 — Deploy 1 (Expand):
  ALTER TABLE users ADD COLUMN full_name VARCHAR(255);
  App: writes to BOTH user_name AND full_name
  App: reads from user_name only (full_name may be NULL)

Phase 2 — Background job (Migrate):
  -- Run in batches, off-peak, with sleep between batches
  UPDATE users
  SET full_name = user_name
  WHERE full_name IS NULL
  AND id BETWEEN :batch_start AND :batch_end;

  -- Idempotent: safe to restart from checkpoint
  -- Monitor: rows remaining, drift between batches

Phase 3 — Deploy 2 (Contract):
  Step A: App reads from full_name, writes to both → run 7 days
  Step B: App writes to full_name only → run 7 days
  Step C: ALTER TABLE users DROP COLUMN user_name;
          (fast — no data to move, no lock contention)
```

---

### Case 2: Add a NOT NULL Column

**Say in interview:**
> *"Adding NOT NULL directly causes Postgres to scan all 500M rows to verify no NULLs exist — that's an exclusive lock for hours. The safe way is: add as NULLABLE, backfill a default in batches, then add the constraint using NOT VALID first, then VALIDATE CONSTRAINT. This splits the lock into two short operations."*

```
Phase 1 — Expand:
  ALTER TABLE orders ADD COLUMN status VARCHAR(20);
  -- nullable, no lock

  App starts writing status = 'PENDING' for new rows

Phase 2 — Migrate:
  UPDATE orders
  SET status = 'PENDING'
  WHERE status IS NULL
  LIMIT 1000;
  -- repeat until 0 rows remain

Phase 3 — Contract:
  -- Step A: add constraint that only checks new rows
  ALTER TABLE orders
  ADD CONSTRAINT status_not_null
  CHECK (status IS NOT NULL) NOT VALID;
  -- no table scan → near-instant

  -- Step B: validate existing rows (shorter lock than full scan)
  ALTER TABLE orders VALIDATE CONSTRAINT status_not_null;

  -- Step C: promote to true NOT NULL
  ALTER TABLE orders ALTER COLUMN status SET NOT NULL;
  -- fast — constraint already enforced
```

---

### Case 3: Split One Column Into Two

Example: `address` → `address_line1` + `city` + `zip`

**Say in interview:**
> *"Split is the hardest case because parsing an existing column is lossy — not all address strings parse cleanly. I add the three new columns, write to all four in the app, run a batch parse job for existing rows, audit the mismatch rate, then switch reads to the new columns and drop the old one."*

```
Phase 1 — Expand:
  ALTER TABLE users ADD COLUMN address_line1 VARCHAR(255);
  ALTER TABLE users ADD COLUMN city VARCHAR(100);
  ALTER TABLE users ADD COLUMN zip VARCHAR(20);

  App: writes parsed values to all 4 columns for new records
  App: reads from address (old) for now

Phase 2 — Migrate:
  Batch job: parse old address into new fields
  Audit: how many rows parsed cleanly vs. failed?
  Target: < 0.1% parse failure rate before proceeding
  Failed rows: flag for manual review, do not block migration

Phase 3 — Contract:
  Switch reads to new columns.
  Run 7 days → monitor for nulls or parse errors in reads.
  Drop address column.
```

---

## ⚠️ The 4 Things That Go Wrong

**1. Running the backfill as a single query**
A single `UPDATE WHERE status IS NULL` on 500M rows locks the table.
Fix: batch 1,000 rows per query, sleep 50–100ms between batches, checkpoint progress.

**2. Forgetting to check for the old column in background jobs**
After Deploy 2 drops the column, a nightly report job that reads `user_name` crashes.
Fix: before dropping, grep codebase AND query `pg_stat_statements` for the old column name. Check last 30 days of query logs.

**3. Missing the index on the new column**
Old column had an index. New column doesn't. After contracting, all queries that used the old index become full table scans.
Fix: `CREATE INDEX CONCURRENTLY idx_users_full_name ON users(full_name);` during the Migrate phase. `CONCURRENTLY` builds without locking the table.

**4. Skipping the dual-write period**
Team rushes to Deploy 2 immediately after backfill. A replication lag means some replicas still have NULL in the new column.
Fix: mandatory 7-day dual-write validation window after backfill completes. Check replica lag before switching reads.

---

## 🧩 Interview Probe Q&As

**"Why not just do `ALTER TABLE` directly?"**
> On a 500M row table, `ALTER TABLE` acquires an exclusive lock — blocks all reads and writes. Duration scales with table size. At 5,000 RPS that's thousands of failed requests per minute. The Expand-Migrate-Contract approach keeps the system fully operational at every step by spreading the change across two deployments and a background job.

**"How do you handle the backfill on 500M rows without impacting production?"**
> Batch in groups of 1,000 rows with a 100ms sleep between batches. Run during off-peak hours. The job is idempotent — it checkpoints its progress and can resume after a crash. At 1,000 rows per batch × 100ms sleep that's ~14 hours for 500M rows — acceptable for a background migration job.

**"What if the backfill job fails halfway through?"**
> The job is idempotent. It uses `WHERE new_col IS NULL` as its resume condition — rows already migrated are skipped on restart. No re-work, no data duplication. Restart from the last checkpoint.

**"When is it safe to drop the old column?"**
> After: (1) backfill is 100% complete, (2) all app code reads from the new column, (3) zero writes go to the old column, (4) 7 days of production traffic have passed cleanly, (5) grep and query log audit show zero references to the old column. Then the DROP is fast — Postgres dropping a column doesn't rewrite the table, it just marks the column as invisible.

---

## 🧾 TL;DR — What to Say in the Interview (2 Minutes)

> *"I use Expand-Migrate-Contract — never a one-shot ALTER TABLE on a live table. Three phases: in the first deployment, I add the new column as nullable and start writing to both old and new. Then I run a background job to backfill existing rows in batches of 1,000 with a sleep between batches — never a full table update, always idempotent. In the second deployment, I switch reads to the new column and monitor for 7 days. Then I drop the old column — the DROP itself is fast because nothing reads it anymore. The key is that at every point in time the app has a valid column it can read from. There's no moment of unavailability."*

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 11, 2026 | **Note created.** Batch 2 of Operational-Scenarios. Schema changes are one of the most common real-world backend interview questions at SDE-3 level. |
