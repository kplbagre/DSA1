# DocuSign R2 — Solution Walkthroughs

> **Read `solution-notes-standards.md` FIRST before reading or writing any solution file.**
> It defines the 15-section format, the two interview types, the requirements variation table, and the 60-minute time budget.

---

## What This Folder Is

DocuSign-specific 60-minute answer frameworks for each confirmed/likely R2 question.
These are NOT concept notes — concept notes live in `SystemDesignConcepts/`.
These are "how to walk through THIS question in THIS interview" guides.

**How to use on the day:**
1. Read the concept note(s) listed below (30 min each)
2. Read the solution file (15 min)
3. Practice talking it out loud — no notes, just talking (30 min)
4. Re-read Section 12 (probe questions) before sleep

---

## 6 Solution Files

| Priority | File | Question | Type | Source | Concept notes |
|---|---|---|---|---|---|
| 1 | `C1-rate-limiter.md` | Design a Rate Limiter for a Microservices API | Type A — System Design | ⭐ Confirmed | `02-rate-limiting.md` |
| 2 | `D1-digital-signature.md` | Design a Digital Signature System | Mixed A+B | 🔶 Likely (DocuSign's own product) | `13-security-pki.md` |
| 3 | `C3-pagination-api.md` | Pagination API + Data Model Design | Type B — Product Arch | ⭐ Confirmed | `11-api-design.md`, `12-data-modeling.md` |
| 4 | `C2-expense-report.md` | Expense Report System — Data Model Design | Type B — Product Arch | ⭐ Confirmed | `12-data-modeling.md` |
| 5 | `D3-notification-service.md` | Design a Real-Time Notification Service | Type A — System Design | 🔶 Likely | `07-cdc-outbox.md`, `04-idempotency.md` |
| 6 | `D2-document-storage.md` | Design a Document Storage & Retrieval Service | Type B — Product Arch | 🔶 Likely | `14-document-blob-storage.md`, `03-caching.md` |

**Writing priority logic:** C1 first (confirmed + most likely asked), D1 second (DocuSign's core product — highest probe risk), C3 third (confirmed + cursor pagination is a known DocuSign deep-dive), then C2, D3, D2.

---

## Quick Reference — Interview Format (from DocuSign PDF)

**System Design (Type A) evaluates:**
Testability · Usability · Extensibility · Security · Availability · Scalability · Observability & Traceability

**Product Architecture (Type B) evaluates:**
Storage data models · SOLID principles · Scalability · Design patterns · Protocols · Data formats

**Both types:**
- 60 minutes total
- Start with clarifying questions (first 5 minutes)
- Focus on trade-offs — "we are more interested in seeing how you think through pros and cons"
- Tool: HackerRank (for coding) / shared whiteboard (for design)

---

## The Requirements Variation Principle

Every solution file has a **Requirements Variation Table** (Section 5). This is the most important section.

Why: The interviewer WILL vary the requirements mid-interview to test if you're pattern-matching or actually reasoning. "What if this needed to support 100M users?" "What if strict consistency was required?" "What if this is a multi-tenant B2B product?"

A senior candidate answers these in real time. These tables prepare you for every direction.

---

## Standards File

`solution-notes-standards.md` — the complete format definition, quality bar, and pre-write checklist for every file in this folder. Read it before writing or reading any solution file.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | Folder created. Solution files to be written after concept notes are complete. |
| June 2026 | INDEX.md updated. `solution-notes-standards.md` created with 15-section format, two interview types, requirements variation principle, 60-minute time budget. 6 solution files prioritised: C1 → D1 → C3 → C2 → D3 → D2. |
