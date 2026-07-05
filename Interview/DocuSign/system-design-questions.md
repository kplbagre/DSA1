# DocuSign R2 — System Design Questions Master List

> **Purpose:** Finalise the problem space before writing solutions. Every question to prepare for, with source and confidence level. Do NOT add solutions here — this file is the index. Solutions go in `SystemDesignConcepts/` or `LLD/`.
>
> **Last researched:** June 2026 (PDF + 16 web searches + 10 page fetches across Glassdoor, Blind, Exponent, InterviewQuery, 1Point3Acres, LeetCode Discuss, LinkedIn, Design Gurus)

---

## How to read this file

| Column | Meaning |
|---|---|
| **Source** | Where this question came from |
| **In company PDF?** | Whether DocuSign's official prep guide lists it |
| **When** | Date or period of the candidate report |
| **Tier** | ⭐ Confirmed (actual candidate) / 🔶 Likely (domain fit, multiple prep sites) / ⚪ Speculative |
| **Coverage** | Where the solution notes live (or GAP if not written yet) |

---

## Variant A — Infrastructure / Distributed System Design

> These come up when the interviewer is from a platform/infra team. Key dimensions: Testability, Usability, Extensibility, Security, Availability, Scalability, Observability.

| # | Question | Source | In PDF? | When | Tier | Coverage |
|---|---|---|---|---|---|---|
| A1 | **Design a URL Shortener** | DocuSign official prep guide (PDF p.3) | ✅ Yes — explicitly listed | PDF dated 2024 | ⭐ Confirmed | ✅ `r2-solutions/A1-url-shortener.md` (full 60-min framework, Jun 23, 2026) |
| A2 | **Build a Facebook Chat / Messenger** | DocuSign PDF p.3 + multiple Blind/Glassdoor reports 2024-25 | ✅ Yes — "Build a Facebook chat" | Glassdoor Nov-Dec 2025, Blind threads | ⭐ Confirmed | ✅ `r2-solutions/A2-chat-messenger.md` + DELIVERY-RECIPE integration (Jun 23, 2026) |
| A3 | **Architect a Worldwide Video Distribution System** | DocuSign official prep guide (PDF p.3) | ✅ Yes — "Architect a worldwide video distribution system" | PDF dated 2024 | ⭐ Confirmed | ✅ `r2-solutions/A3-video-distribution.md` (full 60-min framework, 3-stage HLD, DocuSign PDF pivot, Jul 5, 2026) |

---

## Variant B — Product Architecture / API Design

> These come up when the interviewer is from a product engineering team (Commerce, Identity, Workflow). Key areas from PDF: SOLID principles, Design patterns, Protocols, Data formats, Storage data models, Scalability.

| # | Question | Source | In PDF? | When | Tier | Coverage |
|---|---|---|---|---|---|---|
| B1 | **Design a Subscription Billing API** | DocuSign PDF p.3 — "Design a service or product API" | ✅ Yes — example listed | PDF dated 2024 | ⭐ Confirmed | ✅ `r2-solutions/B1-subscription-billing.md` (full 60-min framework, 3-stage HLD, outbox+Kafka, SOLID, Jul 5, 2026) |
| B2 | **Design a Chat Service or Feed API** | DocuSign PDF p.3 — explicitly listed | ✅ Yes | PDF dated 2024 | ⭐ Confirmed | `Interview/DocuSign/DOCUSIGN_PREP.md` — Feed API section |
| B3 | **Design an Email Server** | DocuSign PDF p.3 — explicitly listed | ✅ Yes | PDF dated 2024 | ⭐ Confirmed | `Interview/DocuSign/DOCUSIGN_PREP.md` — Email Server section (thin) |

---

## Confirmed from Actual Candidates (NOT in company PDF)

> Questions reported by real candidates in interview experiences published on Exponent, InterviewQuery, LinkedIn, 1Point3Acres.

| # | Question | Source | In PDF? | When | Tier | Coverage |
|---|---|---|---|---|---|---|
| C1 | **Design a Rate Limiter for a Microservices API** | Exponent — 1 verified answer. Interviewer pushed beyond IP-based limiting. Discussion included KYC practices, JWT token identification, deque-of-timestamps with threshold mechanism. | ❌ No | Exponent listing (2024-25) | ⭐ Confirmed | ✅ `r2-solutions/C1-rate-limiter.md` (full 60-min framework, Jun 23, 2026) |
| C2 | **Expense Report System — Data Model Design** | InterviewQuery actual interview report. Format: given a UI mockup, design the DB schema (expense date, type, amount, purpose, location, report period, line items grid). Follow-up: add validation rules — certain employee types can't exceed limits on certain expense categories; some categories unavailable to certain roles. | ❌ No | InterviewQuery report (2024-25) | ⭐ Confirmed | ✅ `r2-solutions/C2-expense-report.md` (full 60-min framework, Jun 23, 2026) |
| C3 | **Pagination API + Data Model Design** | 1Point3Acres — thread titled "Tech Phone Screen: Pagination API and Data Model Design". Confirmed by DocuSign's own engineering blog on API pagination. Topics: cursor vs offset pagination, consistency guarantees, performance at scale. | ❌ No | 1Point3Acres (2024-25) | ⭐ Confirmed | ✅ `r2-solutions/C3-pagination-api.md` (full 60-min framework, Jun 24, 2026) |

### ⚠️ Format insight (confirmed from LinkedIn 2024 candidate report):
> Candidates are **sent 2 questions beforehand** to choose from — one API design question, one traditional server-side application question. The candidate picks one. The interviewer then deep-dives specifically on **the part that's core to their team**. Go in knowing both options are available.

---

## Likely — Domain Fit + Multiple Prep Sites (NOT in company PDF)

> Not from a single confirmed candidate report, but consistent across DesignGurus, Blind discussions, and DocuSign's own product domain.

| # | Question | Source | In PDF? | When | Tier | Coverage |
|---|---|---|---|---|---|---|
| D1 | **Design a Digital Signature System** (scale to millions of users) | DesignGurus DocuSign guide + multiple Blind threads. Key angles: PKI, public/private key pairs, audit trail, non-repudiation, webhook on signing completion, multi-party signing order. | ❌ No | Multiple 2024-25 reports | 🔶 Likely | ✅ `r2-solutions/D1-digital-signature.md` (full 60-min framework, Mixed A+B, Jun 24, 2026) |
| D2 | **Design a Document Storage & Retrieval Service** (high availability) | Glassdoor 2025 interview reports, DesignGurus DocuSign guide. DocuSign-specific angles: versioning, audit log, compliance (SOC 2, GDPR), encrypted at rest. | ❌ No | Glassdoor 2025 | 🔶 Likely | ✅ `r2-solutions/D2-document-storage.md` (full 60-min framework, Type B, Jun 24, 2026) |
| D3 | **Design a Real-Time Notification Service** (standalone, multi-channel) | DesignGurus + InterviewQuery both list this as standalone. Distinct from the billing Kafka fanout in DOCUSIGN_PREP.md. Angles: email + SMS + push, fan-out, delivery guarantees, idempotency, retry with backoff. | ❌ No | Multiple prep sites (2025) | 🔶 Likely | ✅ `r2-solutions/D3-notification-service.md` (full 60-min framework, Type A, Jun 24, 2026) |

---

## Speculative — Drop from prep (prep sites only, no candidate corroboration)

> These appeared on single prep sites with no real candidate validation. Low ROI given limited prep time. Skip unless B1-D3 are fully covered.

| # | Question | Source | Decision |
|---|---|---|---|
| S1 | Design a Distributed Logging System | DesignGurus only | ⛔ Skip |
| S2 | Design an Online Collaboration Tool | DesignGurus only | ⛔ Skip |
| S3 | Design a Real-Time Analytics Dashboard | Exponent aggregator only | ⛔ Skip |

---

## July 2026 Research Pass — Newly Surfaced Questions

> **Research basis:** 12 web searches + 6 page fetches across Glassdoor, 1Point3Acres, LeetCode Discuss, Exponent, DesignGurus, Blind, Prepfully, AlgoDaily, MockQuestions (Jul 4, 2026).
>
> **Confidence notation:** ⭐ = dated candidate report / 🔶 = aggregator-inferred (no single dated source). Source quality was mixed — most primary pages (LeetCode, hw.glich, prachub) returned 403/503. Don't weight 🔶 questions equally to confirmed ⭐.

| # | Question | Source | When | Tier | Level | Coverage |
|---|---|---|---|---|---|---|
| E1 | **OOD: Object-Oriented Design of a Chess Game** | Glassdoor report, Chicago candidate (Sep 2025) | Sep 2025 | ⭐ Confirmed (single report) | SWE (unspecified level) | ✅ `r2-solutions/E1-search-system.md` exists — **NOT this question. GAP.** |
| E2 | **Design a Multi-Level Parking System** | Multiple aggregator sites (Dataford, DesignGurus) | 2025 (aggregator) | 🔶 Likely | Senior SWE (P4) | ❌ GAP — not written |
| E3 | **Design a Multi-Region, Low-Latency System** | Dataford 2026 guide (aggregator) | 2025-26 (aggregator) | 🔶 Speculative | Senior SWE (P4) | ❌ GAP — but partially covered by CDN design (A3) and messaging (A2) |
| E4 | **Design a WhatsApp-Like End-to-End Communication App** | LeetCode Discuss P4 candidate experience (specific round detail) | 2025 | ⭐ Confirmed (1 report) | P4 Senior | Covered by `r2-solutions/A2-chat-messenger.md` — add E2E encryption angle |
| E5 | **Design a Document Signing Service** | DesignGurus DocuSign guide | 2025 (aggregator) | 🔶 Likely | Senior SWE | Covered by `r2-solutions/D1-digital-signature.md` |

### ⚠️ Format conflict — not resolved

Two different P4 interview structures reported in the 2025–26 research pass:

| Source | Structure |
|---|---|
| Blind thread (2025) | 2 DSA rounds → 1 HLD round → 1 HM round (4 total) |
| InterviewQuery guide (aggregated) | 2 technical rounds → 1 system design → 3 behavioral rounds (6 total) |

**Action:** Confirm with recruiter which format applies to your loop. The 4-round P4 structure (2 DSA + 1 HLD + 1 HM) is the most commonly reported. The 3-behavioral variant may apply to senior loops with more stakeholders.

### SWE (P3) vs Senior SWE (P4) — what the data shows

| Dimension | SWE / P3 | Senior SWE / P4 |
|---|---|---|
| **Format** | 1-2 DSA + 1 system design | 2 DSA + 1 HLD + 1 HM (sometimes more behavioral rounds) |
| **System design depth** | Clean API + basic scale | Trade-offs without prompting; "simple, testable, auditable" expected |
| **VP in loop** | Not confirmed | Confirmed — Blind: "VP level won't let you through if basic coding weak" |
| **Behavioral rounds** | 1 (blended in HM) | 1 HM + sometimes 3 separate behavioral rounds |
| **Data confidence** | Weak — few P3-specific reports | Solid — multiple P4 candidate reports in 2025 |

> **Honest gap:** Most 2025-26 reports are from P4 candidates. P3 question patterns are inferred, not confirmed.

---

## Coverage Summary

| Status | Questions |
|---|---|
| ✅ Fully covered in r2-solutions/ (60-min interview format) | A1, A2, A3, B1, C1, C2, C3, CF1, D1, D2, D3, E1-search-system, E2-authentication-system |
| ✅ Covered in DOCUSIGN_PREP.md (high-level, not 60-min format) | B2, B3 |
| 🔶 Newly surfaced — chess OOD is a real gap | E1-questions-file (OOD chess game) — not written; `E1-search-system.md` is a different problem |
| 🔶 Low priority — covered adjacent or speculative | E2-questions-file (parking system — not written; `E2-authentication-system.md` is a different problem), E3, E5 |
| ✅ Already covered (different framing) | E4 → A2 (add E2E encryption angle) |
| ⛔ Skipped intentionally (low ROI, no candidate corroboration) | S1, S2, S3 |

> ⚠️ **Naming mismatch:** The `E1-` and `E2-` files in r2-solutions/ were written independently — they cover a Search System and an Authentication System respectively. They do NOT correspond to this file's E1 (OOD chess) or E2 (parking lot). The questions master list and r2-solutions/ use the same prefixes for different problems. Treat them as standalone bonus solutions.

**Total confirmed+likely questions in this file: 16**
**Interview-ready solutions in r2-solutions/: A1, A2, A3, B1, C1, C2, C3, CF1, D1, D2, D3, E1-search-system, E2-authentication-system (13 files)**
**Still only in DOCUSIGN_PREP.md (high-level): B2, B3**
**OOD chess game (questions-file E1): not written — single Glassdoor report Sep 2025**

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 5, 2026 | **B1 and A3 promoted to r2-solutions/.** Full 15-section 60-min solutions written for Subscription Billing API (`B1-subscription-billing.md`) and Worldwide Video Distribution (`A3-video-distribution.md`). Both use progressive 3-stage HLD. Coverage count updated from 11 to 13. B2 and B3 remain in DOCUSIGN_PREP.md only. Coverage Summary updated to reflect actual r2-solutions/ state; CF1, E1-search-system, E2-authentication-system coverage confirmed; naming mismatch between questions-file E1/E2 and r2-solutions E1/E2 documented. |
| June 2026 | File created. Research basis: DocuSign official PDF (4 pages) + 16 web searches + 10 page fetches across Glassdoor, Blind, Exponent, InterviewQuery, 1Point3Acres, LinkedIn, Design Gurus. |
| July 2026 | Second research pass added (Jul 2026 section above). Newly surfaced: E1 OOD chess (⭐ Sep 2025 Glassdoor), E4 WhatsApp E2E (⭐ 2025 LC Discuss), E2/E3/E5 (🔶 aggregator). Format conflict documented. P3 vs P4 split noted. |
