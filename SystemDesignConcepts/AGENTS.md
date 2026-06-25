# SystemDesignConcepts — AGENTS.md

> **For any AI assistant writing or editing notes in this folder:** Read this file AND `notes-standards.md` before touching any file here. Both are mandatory. The root `AGENTS.md` sets universal rules; this file sets SystemDesignConcepts-specific rules.

---

## Mandatory Pre-Work (Do This Before Writing Any Note)

1. **Read `notes-standards.md` in this folder** — the complete note format, section order, and pre-publish checklist. Every note must follow it.
2. **Read `resources.md` in this folder** — the curated resource list per concept. Before writing the `📚 Further Reading` section of any note, check `resources.md` for already-vetted resources for that concept. Do NOT invent resource recommendations — use the ones already researched and listed here. If the concept has no entry in `resources.md`, add one.
3. **Read `GAP-CLOSURE-PLAN.md` in this folder** — systematic plan to close coverage gaps (Jun 2026 onwards). Before modifying core notes or creating new files, understand: which gaps are being closed inline (01, 12, 14), which are getting companion advanced files (02.1, 03.1, 04.1, 09.1), and which are being deferred. This prevents duplicate work.
4. **Read the root `AGENTS.md`** — universal formatting rules (code style, ASCII visuals, emoji palette, first-use term gloss).
5. **Check which notes already exist** (Glob on this folder) before writing — avoid duplicating content that belongs in a cross-reference link instead.

> **Rule: When a new reference file is added to this folder that AI should consult, AGENTS.md Mandatory Pre-Work must be updated in the same step — never separately.**

---

## What This Folder Is

Medium-depth concept notes on core backend/system design topics that appear in SDE-2/SDE-3 interviews. These notes are **self-contained entry points** — a reader should be able to pick up any note cold and be interview-ready on that concept. No external resource is required before reading.

**Current notes:**

| File | Concept | Status |
|---|---|---|
| `01-optimistic-pessimistic-locking.md` | Optimistic + Pessimistic Locking | ✅ Done |
| `02-rate-limiting.md` | Rate Limiting (token bucket, sliding window) | ✅ Done |
| `02-rate-limiting_advanced.md` | Rate Limiting — Advanced (adaptive, multi-dimensional, distributed) | ✅ Done |
| `03-caching.md` | Caching (5 strategies, eviction, stampede) | ✅ Done |
| `03-caching_advanced.md` | Caching — Advanced (warming, invalidation, multi-level, coherence) | ✅ Done |
| `04-idempotency.md` | Idempotency (HTTP + Kafka consumer) | ✅ Done |
| `04-idempotency_advanced.md` | Idempotency — Advanced (sagas, batch, deterministic IDs) | ✅ Done |
| `05-consistent-hashing.md` | Consistent Hashing | ✅ Done |
| `06-distributed-locking.md` | Distributed Locking (Redis SETNX, Redlock) | ✅ Done |
| `07-cdc-outbox.md` | CDC + Outbox Pattern | ✅ Done |
| `08-bloom-filter.md` | Bloom Filter | ✅ Done |
| `09-sharded-counters.md` | Sharded Counters | ✅ Done |
| `09-sharded-counters_advanced.md` | Sharded Counters — Advanced (CRDT, time-series, adaptive sharding) | ✅ Done |
| `10-backpressure.md` | Backpressure | ✅ Done |
| `11-api-design.md` | API Design (REST, pagination, versioning) | ✅ Done |
| `12-data-modeling.md` | Relational Data Modeling | ✅ Done |
| `13-security-pki.md` | Security + PKI Fundamentals | ✅ Done |
| `14-document-blob-storage.md` | Document & Blob Storage (S3, metadata DB, versioning) | ✅ Done |
| `15-system-qualities.md` | System Qualities — The 7 DocuSign Evaluation Dimensions | ✅ Done |

> **Advanced Companion Files (Optional Deepeners):**
> Companion advanced files (named `NN-concept_advanced.md`) cover variant-heavy topics. These are optional deepeners — NOT required for interview prep, but useful for readers wanting algorithmic variants and advanced patterns beyond core material. Currently available: `02-rate-limiting_advanced.md`, `03-caching_advanced.md`, `04-idempotency_advanced.md`, `09-counters_advanced.md`. See `GAP-CLOSURE-PLAN.md` for closure strategy.

---

## Rules Specific to This Folder

### 1. Self-Contained — No External Prerequisites

Every note must teach the concept from scratch. Do NOT write "watch the ByteByteGo video first" and then build on it. The note IS the resource. External references go in the `📚 Further Reading` section at the BOTTOM — they are optional deeper dives after reading, not prerequisites before.

### 2. Coverage Completeness (Critical — Most Common Quality Failure)

**Before closing any note:** Count every strategy, algorithm, and pattern named anywhere in the file — visual, real-world examples, trade-offs, Q&As — and verify each one has COMPLETE coverage in the implementation section (steps + code, or equivalent explanation).

**Common trap:** Write-through in the visual, cache-aside in the code → write-through is NOT covered. Named ≠ covered.

Run this check explicitly:
- List every strategy/algorithm named in the note
- Confirm each has its own implementation section or code block
- If any is only mentioned but not implemented — add it before closing

### 3. Named Technology Must Be Explained

Any specific technology named in Section 4 (Lua, Redis SETNX, Kafka sorted set, B-tree, UUID v7, etc.) must have a `### What is X, and why does it fit here?` sub-section with:
- One sentence plain-English definition
- Explicit "In an interview, if asked:" answer sentence

### 4. Section Order Is Fixed

See `notes-standards.md` Section 2 for the exact 10-section order. Do not reorder. Do not skip required sections.

### 5. Interview Depth Calibration

These are SDE-3 level notes — not textbook depth, not introductory depth. The test: after reading the note, can the reader confidently answer both Tier 1 (surface) and Tier 2 (cross/probe) questions in an interview? If not, the note is not deep enough.

Tier 2 questions must cover at least:
- One "what breaks if..." failure mode
- One "how does X interact with Y" cross-concept question
- One "why not just use simpler alternative" question

---

## Pre-Publish Checklist (Run This Before Every File Is Saved)

Copy of the full checklist from `notes-standards.md` — abbreviated for fast review:

- [ ] All 9 required sections present (Section 10 optional)
- [ ] Mental model: everyday analogy, complete enough to retell without technical vocabulary
- [ ] Visual: ASCII diagram present where concept has state/flow/sequence. KEY INVARIANT stated.
- [ ] Steps in plain English BEFORE every code block
- [ ] Code: language-tagged, one statement per line, always braced, spaces around operators
- [ ] Every named technology has a "What is X" sub-section with interview answer
- [ ] **COVERAGE CHECK: every named strategy/algorithm has full implementation coverage**
- [ ] ≥ 5 Q&As, minimum 2 are Tier 2 cross/probe
- [ ] Further Reading at BOTTOM (not top)
- [ ] First-use term gloss for unfamiliar terms
- [ ] No emojis outside approved palette

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | AGENTS.md created for SystemDesignConcepts folder. Enforces: read notes-standards.md first, coverage completeness check, self-contained entry point design, named technology explanation rule. |
| June 2026 | Added `resources.md` to Mandatory Pre-Work (step 2). Further Reading sections must use curated resources from resources.md, not invented recommendations. Added enforcement rule: new reference files must be added to AGENTS.md in the same step they are created. |
| June 23, 2026 | **Gap Closure Plan Created.** Comprehensive audit identified 40+ coverage gaps (70% current coverage). Strategy: inline HIGH-impact gaps (01, 12, 14) + create companion advanced files (02.1, 03.1, 04.1, 09.1) + skip LOW-impact gaps. Naming convention: `NN.1-concept-advanced.md` for companions. Est. effort: 16h. See `GAP-CLOSURE-PLAN.md` for details. |
| June 23, 2026 | **Phase 1 Complete — Inline HIGH-impact gaps.** Modified 3 core notes: 01-optimistic-pessimistic-locking (+isolation levels, +1 Q&A), 12-data-modeling (+schema evolution patterns with batch backfill), 14-document-blob-storage (+multipart upload, +lifecycle policies). Total: +275 lines across 3 files. |
| June 23, 2026 | **Phase 2 Complete — Companion Advanced Files.** Created 4 companion files: 02.1-rate-limiting-advanced (447 lines, adaptive+multi-dim+distributed), 03.1-caching-advanced (439 lines, warming+invalidation+coherence), 04.1-idempotency-advanced (441 lines, sagas+batch+deterministic IDs), 09.1-counters-advanced (456 lines, CRDT+time-series+adaptive). Total: ~1,783 lines. Updated cross-references in AGENTS.md and core notes' Related Concepts sections. |
