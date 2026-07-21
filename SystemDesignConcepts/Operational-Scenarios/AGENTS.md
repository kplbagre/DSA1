# Operational-Scenarios — Folder Standards

> **What this folder is:** Real-world engineering scenario playbooks. These are NOT greenfield designs. They are "your system is live and something needs to change — walk me through it."
> **What this folder is NOT:** Concept explanations (those live in `Foundations/`, `Core-Architecture/`) or pattern references (those live in `Patterns/DeepDive/`).

---

## Note Format (Different from Concept Notes)

Scenario notes are shorter and more playbook-oriented than DeepDive concept notes. Every scenario note has exactly these sections, in this order:

```
## 🎯 The Situation
  When does this scenario appear in an interview?
  What is the trigger?
  What keyword in the question tells you this is the right playbook?

## 🧠 The Decision You Make First
  The clarifying question to ask before drawing anything.
  The fork: 2-3 choices with which playbook each leads to.

## 🎨 Visual — Before and After diagram

## 🗂️ The N-Phase Playbook
  Ordered phases. Each phase:
    - What you do
    - What to say aloud in the interview (quoted)
    - The key risk or failure mode for this phase

## ⚠️ The N Questions the Interviewer Will Probe
  With full answers — not bullet points.

## 🧾 TL;DR — What to Say in the Interview (2 Minutes)
  The spoken summary. Memorize this. Everything else is backup.

## 🔄 Changelog
```

---

## Folder Structure

```
Operational-Scenarios/
├── AGENTS.md                                ← this file
├── Migration/
│   ├── monolith-to-microservice.md          ← ✅ written
│   ├── schema-changes-zero-downtime.md      ← ✅ written
│   └── api-versioning-and-sunset.md         ← ✅ written
├── Scaling/
│   ├── scaling-an-existing-system.md        ← ✅ written
│   └── pre-event-capacity-planning.md       ← ✅ written
├── Production-Ops/
│   ├── production-data-fixes.md             ← ✅ written
│   ├── incident-diagnosis-framework.md      ← ✅ written
│   └── security-hardening-live-systems.md   ← ✅ written
└── Cross-Cutting/
    └── adding-concern-to-existing-systems.md ← ✅ written
```

---

## Pattern Cross-Reference

Every scenario uses one or more of the 5 core operational patterns. Cross-reference these in every scenario note.

| Pattern | File | Used in |
|---|---|---|
| Strangler Fig | `Patterns/DeepDive/10-strangler-fig.md` | All migration scenarios |
| Expand-Migrate-Contract | `Patterns/DeepDive/11-expand-migrate-contract.md` | Schema change scenarios |
| Dual Write + Reconciliation | `Patterns/DeepDive/12-dual-write-reconciliation.md` | Any DB migration |
| Shadow Mode / Dark Launch | `Patterns/DeepDive/13-shadow-mode-dark-launch.md` | Any service validation |
| Feature Flag Gating | `Patterns/DeepDive/14-feature-flag-gating.md` | Cross-cutting rollouts |

---

## Quality Checklist

Before committing any scenario note:

- [ ] "The Situation" names the exact interview keyword that triggers this playbook
- [ ] "Decision First" has a clarifying question the candidate can ask verbatim
- [ ] Diagram shows before and after state (not just after)
- [ ] Each phase has a "say in interview" quoted sentence
- [ ] Probe Q&As are full answers, not bullet fragments
- [ ] TL;DR is ≤ 200 words and speakable in 2 minutes
- [ ] Pattern cross-references use relative paths

### 📋 Known Mistake Classes — Pre-Writing Checklist (Jul 11, 2026 Audit)

Every note written after Jul 11, 2026 must be checked against these 8 mistake classes before committing. These were all discovered in the same session — they are recurring, not one-off.

---

#### Class 1 — "What" Without "How" (Mechanism Under-Specification)

**The bug:** You say *what* to do but not *precisely how*. The reader follows the note and implements something that's subtly wrong.

**Examples caught:**
- `shadow timeout: 200ms` → wrong if old service P99 > 200ms. Correct: `old service P99 + buffer`. Named the mechanism, not the calibration.
- `"dual write"` → ambiguous: direct DB write or API call? Two engineers implement differently. Correct: `monolith calls new service's write API`.
- `"use a circuit breaker"` → says what, not *when* it must be in place. Correct: circuit breaker is a prerequisite before enabling dual write, not an afterthought.

**The check:** Read each step and ask — if a second engineer implements this from your description alone, is there only one way to do it?

---

#### Class 2 — False Universality (Platform-Specific Presented as General)

**The bug:** You describe a Postgres (or MySQL, or Java) feature as if it's the general answer. Reader on a different stack follows the note and hits errors.

**Examples caught:**
- `NOT VALID` / `VALIDATE CONSTRAINT` → PostgreSQL-specific. MySQL handles safe NOT NULL addition differently.
- `CREATE INDEX CONCURRENTLY` → Postgres-specific. MySQL has its own Online DDL syntax.
- Lock behavior of `ALTER TABLE` → differs significantly between Postgres, MySQL, MariaDB.

**The check:** For every SQL snippet or DB operation, ask: which DB is this for? If it's Postgres-specific, say "Postgres:" at the top of the block. If it applies to all, say why.

---

#### Class 3 — Math Without Reality Check

**The bug:** An estimate looks right on the surface but ignores a real-world factor.

**Examples caught:**
- Migration time: `500,000 batches × 100ms sleep = 14 hours` → ignored per-batch execution time (~30ms). Realistic: ~18 hours.

**The check:** For any time or throughput estimate, trace through the full cycle — not just the sleep/wait, but the work time inside the cycle. Then add a 20% buffer for production variability.

---

#### Class 4 — Missing Prerequisites ("You Must Do X Before Y, Not After")

**The bug:** You describe a step that is unsafe without something else in place first, but you don't say that. The reader enables the step and something catastrophic happens.

**Examples caught:**
- Enabling dual write without a **circuit breaker** → new service downtime fills monolith thread pool → monolith goes down.
- Exposing new service's write API without **service-to-service authentication** → unauthenticated write endpoint in production.
- Starting Phase 1 without **dashboards and alerts** set up → you are flying blind if something goes wrong.

**The check:** For every phase gate ("move to Phase N"), list what MUST be true before this phase is safe to start — not what you'll do during the phase. If the prerequisite isn't in the note, add it as a `⚠️` block before the phase.

---

#### Class 5 — Failure Residue (What State Is Left Behind When a Tool Fails Mid-Run)

**The bug:** You describe what a tool/command does when it works. You don't say what broken state it leaves behind if it fails partway through.

**Examples caught:**
- `CREATE INDEX CONCURRENTLY` fails mid-run → leaves an **INVALID index** in the catalog. Future builds conflict with it. Fix: `DROP INDEX CONCURRENTLY` the invalid index before retrying.
- Thread pool exhaustion doesn't clean itself up — threads stay blocked until timeout.

**The check:** For every background job, DDL command, or long-running operation: what does `ps` or the DB catalog look like if this crashes halfway? Does cleanup happen automatically? If not, say what to clean up and how.

---

#### Class 6 — Threading/Async Edge Cases (Works Single-Threaded, Breaks in Production)

**The bug:** Code works in a unit test or single-threaded environment. In production with thread pools, reactive frameworks, or async execution, it silently drops context or produces wrong results.

**Examples caught:**
- `ThreadLocal` doesn't propagate to child threads in `CompletableFuture`, Spring `@Async`, or WebFlux. Shadow context is silently dropped in any async call.
- `Math.abs(Integer.MIN_VALUE) % 100` → `Integer.MIN_VALUE` overflows `Math.abs`, stays negative, produces an invalid bucket. Works for 99.9999% of user IDs, fails for one specific value.

**The check:** For any code block involving threads, pools, or concurrency — ask: does this assume single-threaded execution? If yes, does that assumption hold in production? For Java specifically: `ThreadLocal` → use `InheritableThreadLocal` or reactive context. Hash operations → test with `Integer.MIN_VALUE`.

---

#### Class 7 — Comparison/Reconciliation Window Errors

**The bug:** You compare state across two systems without accounting for propagation lag between them. The comparison fires during the lag window and produces a false alarm (or false OK).

**Examples caught:**
- Reconciliation job compares old DB and new DB immediately after a write. If the write went to old DB at T+0 and the new service API hasn't processed it yet (T+0.5s), reconciliation at T+0.3s sees a mismatch that isn't real. Fix: exclude rows updated in last N seconds from the reconciliation window.
- Clock skew between services: `updated_at` from old service and new service are compared, but services run on machines with different clocks.

**The check:** For any comparison job or validation that reads from two systems: how long is the maximum propagation lag? Is that lag excluded from the comparison window? Are timestamps from the same clock source?

---

#### Class 8 — Incomplete Change Surface ("Grep the Codebase" Is Not Enough)

**The bug:** You say "grep your codebase before dropping the old column" as if that covers all consumers. It doesn't. The column may be read by things that aren't in your codebase.

**Examples caught:**
- BI tools (Metabase, Looker) querying the column directly via SQL
- Stored procedures and database functions referencing the column
- Database views that select the column → `DROP COLUMN` will fail if views reference it
- ORM entity classes (Hibernate, JPA) that map the column by name → app crashes on startup after column is renamed/dropped
- ETL jobs and data warehouse extractors running on a schedule
- Foreign keys from other tables → `DROP COLUMN` fails if FK exists

**The check:** Before any Contract phase that removes a column, index, or table: (1) grep codebase ✅, (2) check `pg_stat_statements` / query logs for the last 30 days, (3) check `pg_views` for views referencing the column, (4) check `pg_constraint` for FK constraints, (5) search ORM entity classes for the column name, (6) check BI tool saved queries.

---

### ⚠️ System Boundary Test (Mandatory — Prevents the "Direct DB Write" Class of Bug)

For **every step that crosses a system boundary** (writes to a DB, makes an API call, sends a message) run this 3-question check:

1. **"What is the naive alternative?"** — e.g., "why not write directly to the DB?" Write the answer into the note if it isn't obvious.
2. **"What would break if someone followed this step naively?"** — e.g., "new service write logic never tested; blind cutover." If something would break, the note must make that path impossible, not just warn about it.
3. **"Is the mechanism described precisely enough that two engineers would implement it the same way?"** — "writes to both DBs" = ambiguous (direct write vs. API call). "monolith calls new service's write API" = unambiguous.

**This is the class of bug that destroyed confidence in the notes on Jul 11, 2026** — Phase 2 of `monolith-to-microservice.md` said "writes to both DBs" which implies direct DB write, bypassing the new service's write logic entirely. Same bug appeared in `13-shadow-mode-dark-launch.md` (re-sync step) and `10-strangler-fig.md` (Things That Go Wrong #2). All three were fixed.

---

## Changelog

| Date | Change |
|---|---|
| Jul 11, 2026 | **Folder created.** First note: monolith-to-microservice migration. Triggered by live interview gap in Infosys/DocuSign round — migration question type completely absent from prior prep. |
| Jul 11, 2026 | **Quality checklist updated.** Added System Boundary Test — 3-question mandatory check for every step that crosses a system boundary (DB write, API call, message). Triggered by discovery that "writes to both DBs" language in multiple notes enabled a direct-DB-write misinterpretation that bypasses the new service's logic entirely. |
| Jul 11, 2026 | **Known Mistake Classes section added.** Full audit of all Batch 1+2 notes produced 8 recurring mistake classes: mechanism under-specification, false universality, math without reality check, missing prerequisites, failure residue, threading/async edge cases, comparison window errors, incomplete change surface. All future notes must be checked against these before committing. |
| Jul 11, 2026 | **Batch 3 complete — Production-Ops notes written.** Three notes: `incident-diagnosis-framework.md` (5-phase incident response: stabilize before diagnosing, 4 signals MELT), `production-data-fixes.md` (kill source → audit → backup → dry-run → batch fix → verify, cascading downstream effects), `security-hardening-live-systems.md` (expand-enforce pattern for auth/secrets/rate-limiting/audit logging). All 8 mistake classes applied. Folder structure updated. |
| Jul 21, 2026 | **Batch 4 complete — Scaling + Cross-Cutting notes written.** Three notes: `scaling-an-existing-system.md` (measure-first, connection pooler prerequisite, thundering herd, replication lag, async offload), `pre-event-capacity-planning.md` (6-phase Black Friday playbook: baseline → forecast → load test → provision → kill switches → game day), `adding-concern-to-existing-systems.md` (expand→observe→enforce pattern, injection point selection, async consumer DLQ). All 8 mistake classes applied. All notes use correct relative paths to `../../Patterns/DeepDive/`. Folder structure updated. |
