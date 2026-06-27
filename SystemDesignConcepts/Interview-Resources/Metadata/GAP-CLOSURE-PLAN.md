# Gap Closure Plan — SystemDesignConcepts

> **Purpose:** Systematic plan to close identified coverage gaps in the 15 core concept notes (01-15). Bridges 70% current coverage to 85%+ coverage with minimal file bloat.
>
> **Status:** Planning phase (Jun 23, 2026)
>
> **Audit Source:** Comprehensive coverage audit comparing each note against industry taxonomies (Arpit Bhayani, System Design Primer, hellointerview.com, NIST standards).

---

## Executive Summary

**Current State:**
- 15 concept notes cover core topics with depth
- Coverage: ~70% of complete taxonomy per topic
- Gap Analysis: 40+ subtopic gaps identified across files

**Target State:**
- 15 core notes: 80%+ coverage, no bloat (≤20-25 min revision each)
- 3-4 advanced companion notes: deep-dive variants
- Total effort: ~10-12 hours
- Interview signal: "Core + Advanced concepts" → senior-ready

**Approach:**
1. **Inline HIGH-impact gaps** into existing files (5-10% size increase)
2. **Create companion advanced files** for variant-heavy topics (03.1-caching-advanced.md, etc.)
3. **Skip LOW-impact gaps** (edge cases, rarely asked)

---

## Gap Classification & Action Plan

### **TIER 1 — HIGH IMPACT (Add Inline, No New File)**

These are production-reality concepts; adding a few paragraphs won't bloat files.

| File | Gap | Size Impact | Action | Effort |
|------|-----|-------------|--------|--------|
| `12-data-modeling.md` | Schema Evolution / Large Table Migrations | +150-200 lines | Add subsection in Section 4 (How It Works) showing `ALTER TABLE` patterns, backfill batches | 1.5h |
| `01-optimistic-pessimistic-locking.md` | Isolation Levels (READ_COMMITTED, REPEATABLE_READ, SERIALIZABLE) | +100-150 lines | Add 1 paragraph in Section 2 (Mental Model) explaining isolation levels + footnote on phantom reads | 1h |
| `14-document-blob-storage.md` | Multipart Upload & Lifecycle Policies | +150-200 lines | Add subsection "S3 Operational Patterns" in Section 4 with code example (upload in chunks, lifecycle rules) | 1.5h |

**Total inline effort: 4 hours**

**File size impact:**
- 12-data-modeling: 750 → 900 lines (+20%)
- 01-optimistic: 650 → 750 lines (+15%)
- 14-blob-storage: 700 → 850 lines (+21%)
- All stay within 20-25 min revision window ✅

---

### **TIER 2 — MEDIUM IMPACT (Create Companion Advanced Files)**

Variants and extensions that add breadth but would bloat core notes if inline.

**File: `03.1-caching-advanced.md`**
- Topics: Cache warming, tag-based invalidation, negative caching, multi-level cache hierarchies, cache coherence protocols
- Size: ~400-500 lines (follow same 10-section format as core notes)
- Purpose: Readers who want to deepen caching beyond 5 core strategies
- Effort: 2.5h

**File: `02.1-rate-limiting-advanced.md`**
- Topics: Adaptive rate limiting, multi-dimensional limits, weighted buckets, token bucket variants, distributed coordination
- Size: ~400-500 lines
- Purpose: Scaling rate limiting beyond fixed algorithms to production constraints
- Effort: 2.5h

**File: `04.1-idempotency-advanced.md`**
- Topics: Batch idempotency, saga patterns, deterministic request IDs, idempotency key composition, Kafka transactions
- Size: ~350-450 lines
- Purpose: Distributed transaction patterns beyond basic HTTP + Kafka
- Effort: 2h

**File: `09.1-counters-advanced.md`**
- Topics: Time-series counters, CRDT counters (G-Counter, PN-Counter), adaptive sharding, approximate counting beyond HyperLogLog
- Size: ~350-450 lines
- Purpose: Counter patterns for modern distributed systems
- Effort: 2h

**Total companion files: 4 new files, ~1700-1900 lines total**

**Total companion effort: 9 hours**

---

### **TIER 3 — LOW IMPACT (Skip)**

Edge cases, rarely-asked variants, or overly specialized. Skip to avoid diminishing returns.

| Gap | File | Reason to Skip |
|-----|------|----------------|
| Jump Consistent Hash | 05-consistent-hashing | Niche use case (load balancing power-of-2); not foundational |
| CRDTs (full taxonomy) | 09-sharded-counters | Mentioned in advanced file; detailed coverage overkill |
| Column-Oriented Storage | 12-data-modeling | Analytics use case; separate from OLTP focus |
| Rendezvous Hashing | 05-consistent-hashing | Alternative algorithm; mentioned in advanced file as "see" |
| MVCC Deep Dive | 01-optimistic-pessimistic-locking | Covered by isolation levels inline; full deep-dive saves for DB internals course |
| Window Boundary Alignment | 02-rate-limiting | Edge case; one sentence in inline section |

---

## Naming Convention

**Core notes:** `NN-concept-name.md`
- Example: `03-caching.md`, `12-data-modeling.md`

**Advanced companion notes:** `NN.1-concept-name-advanced.md`
- Example: `03.1-caching-advanced.md`, `02.1-rate-limiting-advanced.md`
- **Benefit:** Files appear adjacent in directory listing; clear progression (core → advanced)
- **Ordering:** Glob of `03*.md` returns both `03-caching.md` and `03.1-caching-advanced.md` in sequence

**Why this notation:**
- Familiar from software versioning (v2.1 = minor release of v2)
- Preserves numerical order while adding logical grouping
- IDE file explorer shows them together under the base number

---

## Execution Strategy

### **Phase 1: Update AGENTS.md (This File)**
- Add reference to GAP-CLOSURE-PLAN.md in Mandatory Pre-Work
- Clarify that advanced files are OPTIONAL companions, not required

### **Phase 2: Add Inline HIGH-Impact Gaps (4h)**
1. **01-optimistic-pessimistic-locking.md:** Add isolation levels subsection
   - Read current file
   - Insert after Section 2 (Mental Model): 1-paragraph explanation + footnote on phantom reads + connection to pessimistic locking
   - Update Q&A section to add 1 new question on isolation levels
   - Re-check pre-publish checklist

2. **12-data-modeling.md:** Add schema evolution subsection
   - Read current file
   - Insert in Section 4: subsection "Schema Evolution Patterns" with examples: `ALTER TABLE ADD COLUMN`, backfill batches, zero-downtime migration
   - Code example showing batch-based backfill
   - Update Q&A or Further Reading to reference this

3. **14-document-blob-storage.md:** Add multipart upload + lifecycle policies
   - Read current file
   - Insert in Section 4: subsection "S3 Operational Patterns" covering multipart uploads, lifecycle rules
   - Code example: `initMultipartUpload`, chunk upload loop, `completeMultipartUpload`
   - Lifecycle policy example (YAML or S3 config)

### **Phase 3: Create Companion Advanced Files (9h)**
1. Create `03.1-caching-advanced.md`
2. Create `02.1-rate-limiting-advanced.md`
3. Create `04.1-idempotency-advanced.md`
4. Create `09.1-counters-advanced.md`

Each follows 10-section format:
- Section 0: Identity Card (Advanced/Companion to `0N-...`)
- Sections 1-9: Same structure as core notes
- Section 10: Cross-reference back to core note + related advanced concepts

### **Phase 4: Update AGENTS.md & Cross-References**
- Add companion files to the "Current notes" table
- Add footnote: "Advanced companion files (*.1-*.md) are optional deepeners for readers wanting variant coverage"
- In each core note's "Related Concepts" section, add: "For advanced variants, see `0N.1-...advanced.md`"

### **Phase 5: Quality Assurance**
- Run pre-publish checklist on each modified core note
- Run pre-publish checklist on each new companion file
- Verify: no bloat in core files, companions follow same quality standards
- Spot-check cross-references work

---

## Timeline

| Phase | Task | Est. Time | Deadline |
|-------|------|-----------|----------|
| 1 | Update AGENTS.md reference | 0.5h | Before Phase 2 |
| 2 | Add 3 inline HIGH gaps | 4h | Sequential: 01, 12, 14 |
| 3 | Create 4 companion files | 9h | Sequential: 03.1, 02.1, 04.1, 09.1 |
| 4 | Cross-reference updates | 1h | After Phase 3 |
| 5 | QA & validation | 1.5h | Final pass |
| **TOTAL** | | **~16 hours** | |

---

## Deliverables

### **Modified Core Notes (3 files)**
- `01-optimistic-pessimistic-locking.md` (isolation levels)
- `12-data-modeling.md` (schema evolution)
- `14-document-blob-storage.md` (multipart upload, lifecycle)

### **New Companion Files (4 files)**
- `02.1-rate-limiting-advanced.md`
- `03.1-caching-advanced.md`
- `04.1-idempotency-advanced.md`
- `09.1-counters-advanced.md`

### **Updated Reference Files (2 files)**
- `SystemDesignConcepts/AGENTS.md` (cross-reference companion files)
- Each core note's "Related Concepts" section (link to companion)

---

## Success Criteria

✅ **Core notes:**
- Remain ≤25 min revision time (no bloat)
- HIGH gaps integrated naturally (no jarring section additions)
- Pre-publish checklist passes

✅ **Companion files:**
- Follow same quality standards as core notes
- Cover all variants (adaptive, multi-dimensional, CRDT, etc.)
- Named correctly (XX.1-name-advanced.md)
- Cross-referenced from core notes

✅ **Interview Signal:**
- Candidate reads core note → ready for typical 60-min system design
- Candidate reads companion → ready for deep-dive follow-up on variants
- No loss of signal if companion isn't read

---

## Risk Mitigation

| Risk | Mitigation |
|------|-----------|
| Inline gaps cause core files to bloat beyond 20 min | Pre-write each gap inline, count lines before committing |
| Companion files duplicate core content | Companion references core note; adds variants only |
| Cross-references break or get lost | Update AGENTS.md + each core file's "Related Concepts" in same pass |
| Quality drops on companions | Enforce same pre-publish checklist as core notes |
| Naming convention confuses readers | Add footnote in AGENTS.md: "02.1-* = advanced variant of 02-*" |

---

## Dependencies & Blockers

✅ **No blockers.** All work is additive; no destructive changes to existing content.

---

## 🔄 Changelog

| Date | Change |
|------|--------|
| Jun 23, 2026 | Plan created. Audit findings: 40+ gaps, 70% coverage. Strategy: inline HIGH + companion MEDIUM. Effort estimate: 16h. |
| Jun 26, 2026 | **5 High-Priority Production-Critical Concepts Added (45-49).** Gap audit on full 44-file knowledge base identified 5 missing concepts frequently tested at SDE-3 level: Hot Partition Problem (45), Push Notifications/Fanout at Scale (46), Job Scheduling at Scale (47), Feature Flags/A/B Testing (48), State Machines in Workflows (49). All 5 written following full notes-standards format. Coverage estimate raised from ~70% to ~80%+ for production-grade system design topics. |

