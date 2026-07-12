# Pattern Deep Dive: Expand → Migrate → Contract

> **Read this when:** An interviewer asks how you change a database schema on a live system — rename a column, add a NOT NULL constraint, change a data type, split a table — without taking the system down or running a risky one-shot `ALTER TABLE`.
> **Pre-interview refresh:** Read the KEY INSIGHT + the 3-phase visual (5 min).

---

## 🎯 What Problem Does This Pattern Solve?

Your database is live. Millions of rows. Thousands of requests per second. Your schema needs to change:
- Rename `user_name` column to `full_name`
- Add a `NOT NULL` constraint to `email`
- Change `status` from `VARCHAR` to an `ENUM`
- Split `address` into `address_line1`, `address_line2`, `city`, `zip`

The naive answer: `ALTER TABLE users RENAME COLUMN user_name TO full_name;`

The problem: on a 500M row table, `ALTER TABLE` acquires an **exclusive lock** (a lock that blocks ALL reads and writes on the table for the entire duration of the operation — which can be minutes or hours on a large table). Your system goes down.

Even "fast" DDL operations in Postgres/MySQL can lock tables for seconds — which at 5,000 RPS means thousands of failed requests piling up.

The Expand-Migrate-Contract pattern is how you make schema changes with zero downtime: **add the new thing alongside the old, move data gradually, remove the old only when nothing uses it.**

---

## 💡 Core Insight

**Never remove before you add. Never add a constraint before the data satisfies it. Never rename directly — add a copy, migrate, drop the original.** Each of these "never" rules maps to one phase of the pattern.

> **KEY INSIGHT:** "A schema change on a live system is not one event — it is three deployments separated by a migration job. Expand in deploy 1. Migrate data in a background job. Contract in deploy 2. The system runs normally at every point. The only downtime is the time you forgot to plan for."

---

## 🎨 Visual — The 3 Phases

```
EXAMPLE: Rename column  user_name → full_name  on users table (500M rows)

──────────────────────────────────────────────────────────────────────

PHASE 1: EXPAND  (Deploy 1)
Add the new column alongside the old one.
Start writing to BOTH. Read from OLD only.

  users table:
  ┌────────────────────────────────────────────────┐
  │  id │ user_name (old) │ full_name (new) │ ...  │
  │  1  │  "kapil"        │  "kapil"        │      │  ← new rows: written to both
  │  2  │  "alice"        │  NULL           │      │  ← old rows: full_name is NULL
  │  3  │  "bob"          │  NULL           │      │
  └────────────────────────────────────────────────┘

  App code in Deploy 1:
    writes → BOTH user_name AND full_name
    reads  → user_name (old column only, new one may be NULL)

──────────────────────────────────────────────────────────────────────

PHASE 2: MIGRATE  (Background job — runs while system is live)
Backfill old rows: copy user_name into full_name for rows where full_name IS NULL.

  UPDATE users
  SET full_name = user_name
  WHERE full_name IS NULL
  LIMIT 1000;        ← batched — never lock the whole table
                     ← runs in off-peak hours
                     ← idempotent — safe to restart

  After job completes:
  ┌────────────────────────────────────────────────┐
  │  id │ user_name (old) │ full_name (new) │ ...  │
  │  1  │  "kapil"        │  "kapil"        │      │  ← all rows have full_name
  │  2  │  "alice"        │  "alice"        │      │
  │  3  │  "bob"          │  "bob"          │      │
  └────────────────────────────────────────────────┘

──────────────────────────────────────────────────────────────────────

PHASE 3: CONTRACT  (Deploy 2)
Switch reads to new column. Then drop old column.

  Step A — Deploy 2a:
    writes → BOTH (still safe)
    reads  → full_name (new column)
    Run for 1 week. Monitor for errors.

  Step B — Deploy 2b (after validation):
    writes → full_name ONLY  (stop writing to user_name)
    reads  → full_name ONLY

  Step C — Drop old column (after another week):
    ALTER TABLE users DROP COLUMN user_name;
    ← this ALTER is fast: dropping a column doesn't rewrite the table
    ← no lock contention because nothing reads or writes user_name anymore

KEY INVARIANT:
   At every point in time, the system has at least ONE valid column
   it can read from. There is no moment where data is unavailable.
   The drop only happens after the app has been reading the new
   column cleanly for at least a week.
```

---

## 🗂️ Applying the Pattern — Common Schema Change Types

---

### Type 1 — Rename a Column

| Phase | What happens |
|---|---|
| Expand | Add new column (nullable). Write to both. Read from old. |
| Migrate | Backfill: `UPDATE ... SET new_col = old_col WHERE new_col IS NULL` |
| Contract | Read from new. Stop writing to old. Drop old. |

**Gotcha:** If the column is part of an index, you need a new index on the new column before dropping the old one. Build the index `CONCURRENTLY` (Postgres) — this builds the index without locking the table.

---

### Type 2 — Add a NOT NULL Constraint

Adding `NOT NULL` to an existing column causes Postgres to scan all rows to verify no NULLs exist — full table lock.

> **Postgres-specific:** `NOT VALID` and `VALIDATE CONSTRAINT` are PostgreSQL features. MySQL uses a different strategy: add as nullable, backfill, then run `ALTER TABLE ... MODIFY COLUMN ... NOT NULL` (which in MySQL 5.6+ with Online DDL avoids a full table copy for some storage engines — verify for your version).

| Phase | What happens |
|---|---|
| Expand | Add column as NULLABLE. Write default value for new rows. |
| Migrate | Backfill: `UPDATE ... SET col = 'default' WHERE col IS NULL` in batches |
| Contract | Add NOT NULL constraint with `NOT VALID` first (skips existing rows), then `VALIDATE CONSTRAINT` (validates without full lock), then `SET NOT NULL` |

```sql
-- Expand
ALTER TABLE orders ADD COLUMN status VARCHAR(20);

-- Migrate (batch job)
UPDATE orders SET status = 'PENDING' WHERE status IS NULL LIMIT 1000;
-- sleep 100ms between batches, repeat until 0 rows affected

-- Contract step 1: add constraint that only applies to new rows
-- NOT VALID = skips full table scan → no long lock
ALTER TABLE orders ADD CONSTRAINT status_not_null
  CHECK (status IS NOT NULL) NOT VALID;

-- Contract step 2: validate existing rows
-- Takes SHARE UPDATE EXCLUSIVE lock (allows reads + inserts, blocks DDL)
-- Shorter than a full scan lock, but can still run for minutes on a 500M row table
ALTER TABLE orders VALIDATE CONSTRAINT status_not_null;
```

---

### Type 3 — Split One Column into Two

Example: `address` → `address_line1` + `city` + `zip`

| Phase | What happens |
|---|---|
| Expand | Add `address_line1`, `city`, `zip` as nullable columns |
| Migrate | Parse old `address` string → write into new columns (batch job) |
| Contract | Read from new columns. Update app to write to new columns. Drop `address`. |

**Gotcha:** Parsing an address string is lossy — "123 Main St, Austin TX 78701" might not parse cleanly for all rows. Always audit mismatch rate before contracting.

---

### Type 4 — Change Data Type (VARCHAR → INT)

Example: `user_id` stored as `VARCHAR`, needs to become `BIGINT`.

| Phase | What happens |
|---|---|
| Expand | Add `user_id_int BIGINT`. Write to both columns. |
| Migrate | `UPDATE ... SET user_id_int = CAST(user_id AS BIGINT) WHERE user_id_int IS NULL` |
| Contract | Read from `user_id_int`. Drop `user_id`. Rename `user_id_int` → `user_id`. |

---

## ⚠️ The 4 Things That Go Wrong

**1. Running the backfill as a single query**
```sql
-- WRONG: locks the whole table for hours
UPDATE users SET full_name = user_name WHERE full_name IS NULL;

-- RIGHT: batched, with sleep between batches
UPDATE users SET full_name = user_name
WHERE id IN (SELECT id FROM users WHERE full_name IS NULL LIMIT 1000);
-- sleep 100ms, repeat
```

**2. Contracting before all old code is gone**
A background worker still reads the old column. You drop it. The worker crashes.

Fix: grep your entire codebase for the old column name before dropping. Check query logs for the last 7 days. Verify zero references before running the DROP.

**3. Forgetting index migration**
Old column had an index. New column doesn't. After contracting, queries that used the old index run full table scans.

Fix: create index on new column `CONCURRENTLY` during the Migrate phase, before contracting.

> **`CONCURRENTLY` cannot run inside a transaction block.** If your migration tool (Flyway, Liquibase) wraps migrations in a transaction by default, `CREATE INDEX CONCURRENTLY` will throw: `ERROR: CREATE INDEX CONCURRENTLY cannot run inside a transaction block`. Fix: run the index creation as a separate migration file marked as non-transactional (`transactional: false` in Liquibase, `@NotTransactional` annotation or raw SQL split in Flyway).
>
> **If `CREATE INDEX CONCURRENTLY` fails mid-run**, it leaves an INVALID index behind. Future queries won't use it, but future index builds will conflict. Before retrying: `DROP INDEX CONCURRENTLY idx_name;` to remove the invalid index, then re-run.

**4. Skipping the validation window**
Going straight from Migrate to Drop without running the system on the new column for at least a week.

Fix: mandatory validation window — read from new column for 7+ days before dropping old. Defines the minimum safe Contract timeline.

---

## 🧩 Interview Probe Q&As

**"How do you rename a column on a 500M row table without downtime?"**
> Expand-Migrate-Contract — three steps across two deployments. First deployment: add the new column as nullable, start writing to both old and new. Run a background job to backfill all existing rows in batches of 1000, with a sleep between batches to avoid locking. Second deployment: switch reads to the new column. After 7 days of clean operation, drop the old column. The drop itself is fast because nothing reads it anymore.

**"Why not just do ALTER TABLE RENAME COLUMN?"**
> On a large table, `ALTER TABLE` acquires an exclusive lock — blocks all reads and writes for the duration. On a 500M row table that can be minutes. At 5,000 RPS that's thousands of timeouts. The Expand-Migrate-Contract pattern keeps the system fully operational at every step.

**"How do you add a NOT NULL constraint without locking the table?"**
> Add the constraint with `NOT VALID` first — this applies only to new rows and doesn't scan existing rows, so no lock. Then run a batch backfill to fill NULLs on old rows. Then call `VALIDATE CONSTRAINT` — which takes a shorter lock than a full scan. This is Postgres's built-in support for safe constraint addition.

**"How long does the migration phase take?"**
> Depends on table size and write throughput. Rule: batch 1000 rows, sleep 100ms between batches. For a 500M row table: 500,000 batches. If each batch takes ~30ms to execute + 100ms sleep = 130ms/batch → ~18 hours. The "14 hours" figure you may see cited assumes near-instant batch execution — realistic is 15–20 hours depending on I/O and lock contention. Run it during off-peak hours. It must be idempotent — resumable from the last checkpoint if it crashes.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 11, 2026 | **Note created.** Batch 1 of Operational-Scenarios gap closure. Schema change pattern — one of the most common real-world backend interview questions. |
| Jul 11, 2026 | **Senior tech lead audit fixes.** (1) Added Postgres qualifier on NOT VALID / VALIDATE CONSTRAINT — MySQL uses different approach. (2) Added CONCURRENTLY-in-transaction-block footgun with Flyway/Liquibase fix. (3) Added INVALID index cleanup procedure when CONCURRENTLY fails mid-run. (4) Corrected migration time estimate: ~18 hours realistic (was ~14 hours, ignored per-batch execution time). |
