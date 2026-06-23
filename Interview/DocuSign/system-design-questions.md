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
| A1 | **Design a URL Shortener** | DocuSign official prep guide (PDF p.3) | ✅ Yes — explicitly listed | PDF dated 2024 | ⭐ Confirmed | `Interview/DocuSign/DOCUSIGN_PREP.md` — full design |
| A2 | **Build a Facebook Chat / Messenger** | DocuSign PDF p.3 + multiple Blind/Glassdoor reports 2024-25 | ✅ Yes — "Build a Facebook chat" | Glassdoor Nov-Dec 2025, Blind threads | ⭐ Confirmed | `Interview/DocuSign/DOCUSIGN_PREP.md` — full design |
| A3 | **Architect a Worldwide Video Distribution System** | DocuSign official prep guide (PDF p.3) | ✅ Yes — "Architect a worldwide video distribution system" | PDF dated 2024 | ⭐ Confirmed | `Interview/DocuSign/DOCUSIGN_PREP.md` — full design |

---

## Variant B — Product Architecture / API Design

> These come up when the interviewer is from a product engineering team (Commerce, Identity, Workflow). Key areas from PDF: SOLID principles, Design patterns, Protocols, Data formats, Storage data models, Scalability.

| # | Question | Source | In PDF? | When | Tier | Coverage |
|---|---|---|---|---|---|---|
| B1 | **Design a Subscription Billing API** | DocuSign PDF p.3 — "Design a service or product API" | ✅ Yes — example listed | PDF dated 2024 | ⭐ Confirmed | `Interview/DocuSign/DOCUSIGN_PREP.md` — full design with SOLID, state machine, Kafka fanout |
| B2 | **Design a Chat Service or Feed API** | DocuSign PDF p.3 — explicitly listed | ✅ Yes | PDF dated 2024 | ⭐ Confirmed | `Interview/DocuSign/DOCUSIGN_PREP.md` — Feed API section |
| B3 | **Design an Email Server** | DocuSign PDF p.3 — explicitly listed | ✅ Yes | PDF dated 2024 | ⭐ Confirmed | `Interview/DocuSign/DOCUSIGN_PREP.md` — Email Server section (thin) |

---

## Confirmed from Actual Candidates (NOT in company PDF)

> Questions reported by real candidates in interview experiences published on Exponent, InterviewQuery, LinkedIn, 1Point3Acres.

| # | Question | Source | In PDF? | When | Tier | Coverage |
|---|---|---|---|---|---|---|
| C1 | **Design a Rate Limiter for a Microservices API** | Exponent — 1 verified answer. Interviewer pushed beyond IP-based limiting. Discussion included KYC practices, JWT token identification, deque-of-timestamps with threshold mechanism. | ❌ No | Exponent listing (2024-25) | ⭐ Confirmed | **GAP** → planned `SystemDesignConcepts/02-rate-limiting.md` |
| C2 | **Expense Report System — Data Model Design** | InterviewQuery actual interview report. Format: given a UI mockup, design the DB schema (expense date, type, amount, purpose, location, report period, line items grid). Follow-up: add validation rules — certain employee types can't exceed limits on certain expense categories; some categories unavailable to certain roles. | ❌ No | InterviewQuery report (2024-25) | ⭐ Confirmed | **GAP** → no note exists |
| C3 | **Pagination API + Data Model Design** | 1Point3Acres — thread titled "Tech Phone Screen: Pagination API and Data Model Design". Confirmed by DocuSign's own engineering blog on API pagination. Topics: cursor vs offset pagination, consistency guarantees, performance at scale. | ❌ No | 1Point3Acres (2024-25) | ⭐ Confirmed | **GAP** → no note exists |

### ⚠️ Format insight (confirmed from LinkedIn 2024 candidate report):
> Candidates are **sent 2 questions beforehand** to choose from — one API design question, one traditional server-side application question. The candidate picks one. The interviewer then deep-dives specifically on **the part that's core to their team**. Go in knowing both options are available.

---

## Likely — Domain Fit + Multiple Prep Sites (NOT in company PDF)

> Not from a single confirmed candidate report, but consistent across DesignGurus, Blind discussions, and DocuSign's own product domain.

| # | Question | Source | In PDF? | When | Tier | Coverage |
|---|---|---|---|---|---|---|
| D1 | **Design a Digital Signature System** (scale to millions of users) | DesignGurus DocuSign guide + multiple Blind threads. Key angles: PKI, public/private key pairs, audit trail, non-repudiation, webhook on signing completion, multi-party signing order. | ❌ No | Multiple 2024-25 reports | 🔶 Likely | **GAP** → no note exists |
| D2 | **Design a Document Storage & Retrieval Service** (high availability) | Glassdoor 2025 interview reports, DesignGurus DocuSign guide. DocuSign-specific angles: versioning, audit log, compliance (SOC 2, GDPR), encrypted at rest. | ❌ No | Glassdoor 2025 | 🔶 Likely | **GAP** → no note exists |
| D3 | **Design a Real-Time Notification Service** (standalone, multi-channel) | DesignGurus + InterviewQuery both list this as standalone. Distinct from the billing Kafka fanout in DOCUSIGN_PREP.md. Angles: email + SMS + push, fan-out, delivery guarantees, idempotency, retry with backoff. | ❌ No | Multiple prep sites (2025) | 🔶 Likely | Partially covered in Observer pattern (Kafka fanout). **Full standalone GAP** |

---

## Speculative — Drop from prep (prep sites only, no candidate corroboration)

> These appeared on single prep sites with no real candidate validation. Low ROI given limited prep time. Skip unless B1-D3 are fully covered.

| # | Question | Source | Decision |
|---|---|---|---|
| S1 | Design a Distributed Logging System | DesignGurus only | ⛔ Skip |
| S2 | Design an Online Collaboration Tool | DesignGurus only | ⛔ Skip |
| S3 | Design a Real-Time Analytics Dashboard | Exponent aggregator only | ⛔ Skip |

---

## Coverage Summary

| Status | Questions |
|---|---|
| ✅ Fully covered in DOCUSIGN_PREP.md | A1, A2, A3, B1, B2, B3 |
| ✅ Partially covered (LLD pattern notes exist) | D3 (Observer/Kafka fanout written, standalone not) |
| ❌ GAP — confirmed asked, no note | C1 (Rate Limiter), C2 (Expense Report Data Model), C3 (Pagination API) |
| ❌ GAP — likely asked, no note | D1 (Digital Signature), D2 (Document Storage), D3 (Notification Service standalone) |
| ⛔ Skipped intentionally | S1, S2, S3 |

**Total questions to prep: 12 (6 covered + 6 gaps)**
**Immediate priority gaps: C1, C2, C3, D1**

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. Research basis: DocuSign official PDF (4 pages) + 16 web searches + 10 page fetches across Glassdoor, Blind, Exponent, InterviewQuery, 1Point3Acres, LinkedIn, Design Gurus. |
