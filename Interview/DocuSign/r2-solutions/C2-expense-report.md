# C2 — Expense Report System — Data Model Design

> **Read `solution-notes-standards.md` first.** This file is a 60-minute interview answer framework, not a concept reference. Concept-level depth lives in `SystemDesignConcepts/`.

---

## 📚 Prerequisites — Study These First

Before reading the solution, make sure you can explain the bolded items below from memory.

| Concept | File in `SystemDesignConcepts/` | Why you need it for this question |
|---|---|---|
| **Data modeling** | `Foundations/Data-Fundamentals/12-data-modeling.md` | The flexible approval-rule schema (role + category → spend limit) is a data modeling problem — know how to represent variable rule structures without hardcoding |
| **State machines / workflows** | `Production-Grade/System-Design-Patterns/49-state-machines-workflows.md` | The claim lifecycle (Draft → Submitted → Manager Approved → Finance Approved → Paid) is a state machine — transitions must be validated and logged |
| **Multi-step processes** | `Patterns/DeepDive/05-multi-step-processes.md` | Multi-step approval chains with partial failure recovery — what happens if finance-step fails after manager already approved |
| **Optimistic / pessimistic locking** | `Foundations/Concurrency-and-Consistency/01-optimistic-pessimistic-locking.md` | Two managers approving the same expense concurrently — optimistic locking with version column is the right answer |
| **Blob / document storage** | `Foundations/Data-Fundamentals/14-document-blob-storage.md` | Receipt image upload — pre-signed S3 URLs, direct upload from client, how to link the stored file to the expense record |
| **Idempotency** | `Foundations/Concurrency-and-Consistency/04-idempotency.md` | Manager double-clicking Approve — the idempotency key on the approval action prevents duplicate state transitions |

---

## 🎯 What Is This System?

**In plain English:** An expense reporting system lets employees submit reimbursement claims (hotel, meals, travel, software) by entering details and uploading receipts. Claims flow through a multi-step approval workflow — manager, then finance — with validation rules where different employee roles have different spending limits per expense category.

**Real-world examples:**

| System / Company | What they built |
|---|---|
| **SAP Concur** | Used by 95% of Fortune 500; processes 63M expense reports/year |
| **Expensify** | SmartScan (OCR receipts) with real-time reimbursement |
| **Coupa** | Spend management with complex approval policy engines |
| **Workday Expenses** | Integrated with HR so roles and spend limits update automatically |
| **Brex / Ramp** | Corporate card + expense tracking with per-category spending controls |

**Core user journey:** Employee snaps a photo of a $450 hotel receipt → submits the claim → manager gets an approval notification → approves it → finance processes the reimbursement → employee sees "Approved — payment in 3 business days."

**Why it's hard to build at scale:** Validation rules are role-specific and category-specific (a director can claim $500/night hotels; a junior employee cannot; entertainment has different approval chains than travel) — the rule engine must be flexible and enforceable at both API and DB layers, not hardcoded per role.

---

## 🧠 How to Use This File

**This file is an instantiation of DELIVERY-RECIPE** (`Interview/DocuSign/DELIVERY-RECIPE.md`). Every section below maps to one step of the 6-step interview delivery framework. The framework is backed by cognitive psychology — under stress, your working memory shrinks 40–50%, so you need ONE rhythm you can execute automatically.

**Before your interview:**
1. Read DELIVERY-RECIPE.md once to understand the psychology (30 min)
2. Skim the 6 **Memory Anchors** below (2 min)
3. Read this entire file and the 3 **Common Mistakes** (Section 13) so you know what to avoid (20 min)
4. During the interview, follow the 6-step rhythm: Ask → Clarify → Requirements → Estimate → HLD → Deep Dives → Trade-offs → Dimensions → Probes

**The time budget:**
- Minutes 0–5: Sections 1–2 (Opener + Clarifying questions)
- Minutes 5–10: Sections 3–4 (Requirements + Scale estimation)
- Minutes 10–25: Sections 5–6 (Requirements variation + API/schema + Data flow)
- Minutes 25–40: Section 7 (Deep dives: 2–3 riskiest components)
- Minutes 40–48: Section 10 (Trade-offs: exactly 3, with failure modes)
- Minutes 48–52: Section 11 (DocuSign dimensions — map explicitly)
- Minutes 52–60: Section 12 (Interviewer probes — prepared Tier 1/2/3 answers)

**Note:** Type B questions emphasize API design and data model more than infrastructure. Sections 8 (API) and 9 (Data Model) are primary deliverables.

**Stay on this schedule.** If you're at minute 45 and still deep-diving, pause and move to trade-offs — the rubric values trade-off thinking over technical depth.

---

## 💾 Memory Anchors (Memorize These 6)

Before every interview, say these 6 sentences to yourself (takes 30 seconds):

1. **"Ask before you design."** — Don't assume. Use Section 2 to ask clarifying questions and confirm scope.
2. **"Name the nouns."** — Entities are your mental hooks. When stressed, you can remember categories even if you forget details.
3. **"Define the boundary."** — The API/interface is the contract. Lock it down before you argue about implementation.
4. **"Trace a request."** — Section 6's data flow narrative shows you understand movement through the system, not just boxes.
5. **"Draw the boxes."** — ASCII HLD is your mental model made visible. The interviewer can probe specific boxes without restarting.
6. **"Dig where it's risky."** — Section 7: pick 2–3 *riskiest* components (where the system breaks, where scale hits hardest), not the most *interesting* ones.

**Bonus anchors (if you have memory space):**
- "Everything is a trade-off." → Section 10
- "Why, not what." → Explain reasoning, not just technology
- "Conversational, not presentation." → Think aloud; don't recite

---

## Section 0 — Question Identity Card

| | |
|---|---|
| **Question** | Expense Report System — Data Model Design |
| **Interview Type** | Type B — Product Architecture |
| **Confirmed or Likely** | ⭐ Confirmed asked (InterviewQuery actual interview report. Candidate was given UI mockup showing expense form with fields: expense date, type, amount, purpose, location, report period. Follow-up: add validation rules — certain employee types can't exceed limits on certain categories; some categories unavailable to certain roles.) |
| **Concept notes prerequisite** | `12-data-modeling.md` (relational modeling, 3NF, schema evolution, migrations), `01-optimistic-pessimistic-locking.md` (concurrency control for multi-user edits) |
| **DocuSign-specific angle** | Expense management is a workflow product. DocuSign's focus: state machines (draft → submitted → approved → rejected → reimbursed), audit trails (who changed what, when, why), role-based access control (manager approvals, compliance), policy enforcement (expense limits, category restrictions). This is enterprise B2B SaaS design. |
| **Time budget** | 5 min clarify → 5 min estimate → 15 min HLD → 15 min deep dive → 8 min trade-offs → 7 min Q&A buffer |

---

## Section 1 — 🚀 The One-Sentence Opener

> "Before I draw anything, let me ask a few clarifying questions — specifically about the scope (single user vs multi-user approval), the complexity of business rules (expense categories, approval workflows), and whether we need strict audit trails, because those drive the schema."

Then immediately go to Section 2. Do NOT start drawing.

---

## Section 2 — 🔍 Clarifying Questions Script (Minutes 0–5)

**What to do:** Ask 4–6 questions that clarify scope. Don't assume. The interviewer is watching how you *think*, not how fast you talk.

**Say this out loud (after your opener):**
> "I have a few clarifying questions so I make sure I'm building the right thing..."

---

**Q: "Is this a single-user expense tracker, or a multi-user enterprise system with approval workflows?"**
- Why ask: single-user is simple (just create/edit/delete); multi-user requires state machines, approval rules, notifications, audit trails.
- Single-user → simple CRUD, minimal validation
- Multi-user → state machine (draft/submitted/approved/rejected), approval queues, manager access control

---

**Q: "What are the approval workflows — who approves expenses (manager, finance, both)? Is approval sequential (step 1 → step 2) or parallel (multiple approvers at once)?"**
- Why ask: sequential approval needs state tracking; parallel requires consensus logic.
- Sequential → state machine with step numbers
- Parallel → approval checklist (all must sign off)

---

**Q: "Are there business rules on expense limits — e.g., only directors can claim flights, employees capped at $100/meal? And are limits per report or per transaction?"**
- Why ask: business rules require a rules engine or validation table. Limits per report need aggregation logic.
- Per-transaction → validate against category table
- Per-report → sum all expenses in report, validate total

---

**Q: "Do we need audit trails — track who submitted/approved/edited each expense and when?"**
- Why ask: audit trails require change history (created_at, updated_at, changed_by, old_value, new_value). More storage, more complexity.
- Yes → add audit log table, soft deletes instead of hard deletes
- No → simpler schema, just current state

---

**Q: "What are the expense categories — are they fixed (meals, flights, hotels) or customizable per company?"**
- Why ask: fixed categories are simple (enum); customizable requires a category table and per-company configuration.
- Fixed → enum in application code
- Customizable → categories table with per-company filtering

---

**Q: "Should employees be able to edit submitted expenses, or are they locked once submitted?"**
- Why ask: locked submissions are simpler (no concurrent edit conflicts); editable submissions need optimistic/pessimistic locking.
- Locked → state prevents edits after submission
- Editable → need conflict resolution (last-write-wins or merge)

---

**Assumed answers (state these at the start of Section 3):**
- Type B focus — API design + data model
- Multi-user enterprise system with approval workflow (manager approval required)
- Sequential approval (employee submits → manager approves → finance reviews)
- Business rules: employee expense category limits (meals $50/transaction, flights $500/transaction); directors get higher limits
- Audit trails required (who changed what, when)
- Fixed expense categories (meals, flights, hotels, transport, other)
- Submitted expenses are locked (cannot edit after submission, must reject + resubmit to change)

---

## Section 3 — 📋 Requirements

**Functional Requirements (what the system does):**
- Employees can create expense reports and add line items (individual expenses)
- Each expense has: date, category (meals/flights/hotels/transport), amount, receipt (attachment), purpose, location
- Employees can save drafts, edit line items, and submit report for approval
- Managers can view submitted reports, add comments, approve or reject
- Finance can audit approved reports before payment (final review)
- System enforces business rules: expense limits per category per employee type (employee vs director)
- Audit trail: track who submitted/approved/edited each expense and when

**Out of scope (say these explicitly):**
- Payment processing / reimbursement (assume payment handled by separate system)
- Receipt OCR / image processing
- Multi-currency support
- Manager dashboard / analytics
- Mobile app (assume web API only)
- Real-time collaboration (assume sequential edits, not concurrent)

**Non-Functional Requirements:**
- Scale: 10K employees and ~1,200 managers; ~500K expense reports/year = ~2,000 per working day = ~5 row-writes/sec peak, plus a ~40 req/sec approval-queue read path during month-end close (see Section 4 — use these numbers consistently)
- Latency: P99 < 500ms (not performance-critical)
- Availability: 99.9% (standard enterprise SaaS)
- Consistency: strong (expense amounts must be accurate; approvals must be durable)
- Compliance: audit trail, immutable approval history

---

## Section 3.5 — 🗂️ Core Entities (~2 minutes)

> **Say this out loud:** "Before I sketch the architecture, let me name the key data objects the system manages."

| Entity | What it represents |
|---|---|
| **Employee** | The person submitting expenses — department, role, manager relationship |
| **ExpenseReport** | A grouped submission of multiple expenses for one period/trip — the main workflow unit |
| **LineItem** | One individual expense within a report — amount, category, receipt attachment |
| **Approval** | Decision record — who approved/rejected, when, and with what comment |
| **ExpensePolicy** | Business rule — spending limits per category per employee tier |
| **AuditLog** | Immutable record of every state change for compliance — never updated, only appended |

**Key relationships:**
- An `Employee` submits many `ExpenseReports` (one-to-many)
- An `ExpenseReport` contains many `LineItems` (one-to-many)
- An `ExpenseReport` has a chain of `Approvals` as it moves through the workflow (one-to-many)
- `ExpensePolicy` is looked up by `(department, category)` at submission time to validate amounts

---

## Section 8 — 🌐 API Design (Minutes 8–13) ⭐ Type B Primary Deliverable

> **Why here:** The Core Entities table above just named your nouns. Each noun becomes a URL path. Now derive the operations on those nouns from your FRs — before drawing any boxes.

### 🧠 How to Derive These Endpoints (Reconstruct, Don't Recall)

The move is: **FR → operation → resource (from your entities table) → HTTP method → contract.**

**"Employees can create expense reports and add line items"** → Two separate CREATE operations, not one. First: POST /v1/reports — creates the container (report) with just title + period, returns a draft. Then: POST /v1/reports/{id}/expenses — adds a line item to a specific report. Why nested? Because a line item has no identity outside its parent report. The parent path makes ownership explicit and lets you enforce "only the owner can add items to this report" at the routing layer.

**"System enforces business rules: expense limits per category per employee type"** → This FR tells you something about a specific endpoint's error contract. When an employee posts a line item that exceeds their category limit, what do you return? `422 Unprocessable Entity` — not `400 Bad Request`. `400` means the JSON is malformed. `422` means the JSON is valid but the business won't accept it. Name this distinction in the interview; most candidates collapse both into 400.

**"Managers can approve or reject"** → action on existing resource → two design choices: (A) `PATCH /v1/reports/{id}` with `{"state": "approved"}`, or (B) `PATCH /v1/reports/{id}/approve` with `{"decision": "approved", "reason": "..."}`. Choose B — the `/approve` sub-resource makes the action explicit, adds the `reason` field naturally, and is easier to restrict to manager-only at the permission layer. The `403` status code means "you are authenticated but not the right manager for this employee's report."

**Validation check:** Map each FR back to an endpoint. "Finance can audit" → handled by GET /v1/reports with role-based filtering, no separate endpoint needed. "Audit trail" → not an API endpoint — it's a DB-layer concern (AuditLog entity), not a caller-facing route.

---

### Core Endpoints

| Method | Path | Auth | Request Body | Response | Status Codes |
|---|---|---|---|---|---|
| POST | `/v1/reports` | JWT (employee) | `{title, period}` | `{id, state: "draft"}` | 201, 400 |
| GET | `/v1/reports` | JWT (any) | `?state=submitted&page=...` | `[{id, employee, state, total_amount_cents}]` (paginated) | 200, 401 |
| POST | `/v1/reports/{id}/submit` | JWT (owner) | — | `{id, state: "submitted"}` | 200, 400, 409 |
| POST | `/v1/reports/{id}/expenses` | JWT (owner) | `{date, category, amount_cents, purpose, location}` | `{id, line_item_id}` | 201, 400, 422 |
| GET | `/v1/reports/{id}/expenses` | JWT (owner or manager) | — | `[{id, category, amount_cents, receipt_status}]` | 200, 403, 404 |
| POST | `/v1/reports/{id}/expenses/{itemId}/receipt-upload-url` | JWT (owner) | — | `{upload_url, s3_key, expires_at}` | 201, 403, 409 |
| PATCH | `/v1/reports/{id}/approve` | JWT (manager) | `{decision: "approved"/"rejected", reason}` | `{id, state}` | 200, 400, 403, 409 |

---

### 🔍 Endpoint Stories — Why Each One Exists

**`POST /v1/reports`** — Creates an empty draft container. Employees don't add line items at creation — they create the report first, then attach expenses. This two-step flow maps directly to the UI mockup. The response returns `state: "draft"` which signals to the client that the report is still editable. The `400` trigger: `period` is not a valid `YYYY-MM` string, or it names a period more than one month in the future (you cannot claim expenses for a period that hasn't happened).

**`GET /v1/reports`** — The `401` trigger is a missing, malformed, or expired JWT — nothing to do with permissions. Contrast with the `403` on the endpoints below, which means the token is fine but the *identity* isn't allowed. Getting 401-vs-403 right on every row of the table is a cheap, visible precision win.

**`POST /v1/reports/{id}/submit`** — A dedicated submission action, not a generic PATCH. This is a deliberate choice: a dedicated `/submit` endpoint is explicit about the business operation, easy to validate, and unambiguous in permission checks. Two distinct 4xx triggers and you should name both: `409` when the report is not in `draft` state — an already-submitted report resubmitted (the double-click guard); `400` when the report *is* in draft but fails the submission preconditions — zero line items, or a line item over the receipt threshold whose `receipt_status` is not `UPLOADED`. The distinction matters because `409` means "your request was fine, the resource moved on" (don't retry) while `400` means "fix your data and resubmit" (do retry).

**`POST /v1/reports/{id}/expenses`** — Where business rule validation fires. At POST time, the system looks up the `ExpensePolicy` for `(employee.tier, line_item.category)` and rejects if the amount exceeds the limit. Three codes, three triggers: `422` = the request is well-formed but the amount exceeds `limit_per_transaction_cents`, or the category isn't available to this employee_type — "syntactically valid request, semantically rejected," and the interview probe (most candidates return `400` here). `400` = malformed: `amount_cents` is not a non-negative integer, `date` isn't ISO-8601, or `category` isn't in the enum. `403` = the report belongs to another employee.

**`POST /v1/reports/{id}/expenses/{itemId}/receipt-upload-url`** — Mints a 15-minute pre-signed S3 PUT URL so the 3 MB phone photo never crosses the app tier (Deep Dive 4). `201` rather than `200` because a new, uniquely-keyed upload capability is created by the call. `409` trigger: the parent report is no longer in `draft` — you cannot attach or replace a receipt on a report that is already in the approval chain, because the receipt is the substantiation the approver relied on. `403` trigger: the caller is not the report's owner. Note there is no `DELETE` for receipts by design — corrections create a new key and the old object is retained under S3 Object Lock.

**`PATCH /v1/reports/{id}/approve`** — Handles both approve and reject in one endpoint via `decision` field. One approval resource, two values. Four codes:
- `403` — the token is valid but this identity may not perform *this* approval. Three sub-cases, and naming them is the whole point of the endpoint: the actor is the **submitter** (self-approval, forbidden absolutely regardless of role), the actor is a manager but not *this* employee's assigned approver, or the actor already approved an earlier step of this chain (separation of duties). See Deep Dive 1.
- `409` — the report is not in a state that admits this transition: approving a `draft`, or approving a report someone else already approved a moment ago. This code was easy to miss: the Key Design Decisions below say invalid transitions return `409`, so it must appear on the one endpoint that drives transitions.
- `400` — `decision` is not one of `approved`/`rejected`, or `decision = "rejected"` with no `reason` (a rejection without a reason is useless to the employee and to the audit trail, so it's a contract violation, not a business-rule one).
- `401` — no valid JWT.

**`GET /v1/reports?state=submitted`** — The manager's approval queue. Without the `state` filter, a manager at DocuSign would receive every expense report in the system on every call. The filter by state is what makes the approval workflow navigable. Cursor-based pagination is needed if an approver has hundreds of pending reports.

---

### Key Design Decisions

**State machine for report lifecycle:** DRAFT → SUBMITTED → PENDING_APPROVAL → APPROVED / REJECTED. The `submit` and `approve` endpoints drive state transitions. Invalid transitions (e.g., approving a DRAFT) return `409`.

**PATCH /approve vs PATCH /v1/reports/{id} with state field:** `/approve` sub-resource wins — it's explicit, easier to permission-check (manager-only route), and makes the audit trail cleaner (the action is named, not inferred from a state diff).

**422 vs 400:** Business rule violations (limit exceeded, missing receipt for amounts > $X) → `422`. Malformed JSON, missing required fields → `400`. Never conflate them.

> 📖 Full: `SystemDesignConcepts/Foundations/Data-Fundamentals/11-api-design.md`

---

## Section 4 — 🔢 Scale Estimation (Minutes 5–10)

**What to do:** Do envelope math out loud. These numbers justify every architecture choice you make in Section 6+. The interviewer wants to see your *thinking*, not just your conclusion.

**Say this out loud (as you write the math on the whiteboard):**
> "Let me do some envelope math to justify the schema. Starting with scale..."

---

**Scale:**
- 10K employees, ~1,200 managers (typical 8:1 span of control) — the manager count matters, because managers are the ones who hammer the read path
- Reports/year: 10K employees × 1 report/week × 50 weeks = **500K reports/year**
- Reports/working-day: 500K ÷ 250 working days = **2,000/day**, and they are not uniform — ~40% land in the 3 days after month-end, so the peak day is ~3,200 reports
- **Write rate:** each submitted report writes ~1 report row + 5 line items + 1 approval + ~8 audit rows ≈ **15 rows**. Peak day: 3,200 × 15 = 48K row-writes concentrated in an 8-hour window = 48,000 ÷ 28,800 = **~1.7 row-writes/sec**, call it **~5/sec** allowing 3× intra-day burstiness
- **Read rate (this dominates, and it's the number people forget):** 1,200 managers refreshing an approval queue every 30s during a month-end close = 1,200 ÷ 30 = **40 req/sec**, each a `WHERE approval_state = 'submitted' AND manager_id = ?` query
- Receipt bytes: 2,000 reports/day × 5 receipts × 3 MB = **30 GB/day** — an order of magnitude more traffic than everything else combined, which is exactly why receipts must not traverse the app tier (Deep Dive 4)
- Storage (relational only): 500K reports/year × 5 line items × 1 KB = **~2.5 GB/year**; audit log ~1 GB/year

**Key conclusions:**
- "At ~5 row-writes/sec peak against a single Postgres primary's ~5,000 writes/sec, we're at 0.1% of write capacity. This is a database workload, not a web-scale problem — no sharding, no caching layer."
- "The 40 req/sec approval-queue read path is what needs an index, not a shard: a composite index on `(manager_id, approval_state)` turns each queue fetch into an index range scan instead of a 500K-row filter."
- "At ~3.5 GB/year total, data fits on a single node for a decade. Schema *correctness* is the priority, not distributed scaling."
- "The real capacity number in this system is 30 GB/day of receipt images, and the design decision it forces is pre-signed S3 uploads so those bytes never touch our servers."
- "Concurrency is low, but 'low' is not 'zero' — two people editing one line item is rare enough that optimistic locking (a version column, no held locks) is right; pessimistic row locks would cost read throughput to solve a problem that happens a few times a week."

---

## Section 5 — 🔄 Requirements Variation Table ⭐ KEY SECTION

| If the interviewer says... | Your schema changes to... | The reasoning |
|---|---|---|
| "100K employees instead of 10K" | Same schema; PostgreSQL handles it fine. Might add sharding by employee_id if transaction throughput hits limits, but unlikely at this scale. | At 100K employees, we're at ~20K reports/day, ~0.23 req/sec. Still well within single-database capacity (5K-10K req/sec typical). |
| "Real-time collaboration (multiple users editing same report)" | Add optimistic locking: add version_number column to expenses. On update, check version hasn't changed since read. Reject if changed, client retries with fresh data. | Concurrent edits require conflict detection. Optimistic locking is simpler than pessimistic (no locks that slow down reads). See `01-optimistic-pessimistic-locking.md`. |
| "Customizable categories per company" | Add categories table: category_id, company_id, name, default_limit. Expenses reference category_id, not hardcoded enum. Add category validation by company. | Multi-tenancy requires category isolation. Lookup time is negligible (few hundred categories per company). |
| "Manager can override limits (e.g., approve $5000 flight)" | Add override_reason to expense. Add system rule: if manager approves, bypass limit checks. Audit log the override. | Policy flexibility requires explicit override tracking. Audit trail shows who bypassed what rule and why. |
| "Expense submission to finance only after manager approves" | Change approval workflow: add approval_state column (submitted → manager_approved → finance_approved → reimbursed). Move manager approval as prerequisite. | Sequential approval states control the workflow. Finance approval can only happen after manager sign-off. |
| "Self-employed contractors with no manager (submit directly to finance)" | Add employee_type enum: employee, contractor, director. Contractor reports skip manager approval (state: submitted → finance_approved). | Different employee types have different workflows. State machine is parameterized by employee type. |
| "Employees submit expenses in multiple currencies (USD, EUR, GBP, JPY)" | Add `currency_code CHAR(3)` + `amount_local NUMERIC` to line_items; add `fx_rate_at_submission NUMERIC` + `amount_usd NUMERIC` (the converted base currency amount, locked at submission time); fetch current rate from an FX provider (ECB, OpenExchangeRates) at `POST /expenses/line-items` time; store locked rate immutably — never recalculate at approval time. | FX rates change hourly; if you recalculate at approval time, the approved amount differs from what the employee submitted — this causes reimbursement disputes. Rate-locking at submission time makes the amount deterministic throughout the workflow. |

---

## Section 6 — 🏗️ High-Level Architecture (Minutes 10–25)

**What to do:** Draw the boxes (ASCII or whiteboard). Walk through the data flow *as if telling a story*. The interviewer is checking: "Does this person understand flow?"

**Note:** Type B questions often skip the HLD diagram and jump straight to API + schema. Adapt this section accordingly.

**Data Store Selection (10 seconds):** PostgreSQL only — 0.02 req/sec, relational joins across 6 entities, ACID required for approval state machine, 3.65 GB/year fits in a single instance with no sharding or caching needed.

**Say this out loud (as you transition to API/schema):**
> "For a data model design question, the architecture is straightforward: web API → PostgreSQL. Let me focus on the API contract and the schema design..."

---

### 🎨 Visual — Schema Evolution (3-Stage Progression)

> **Note:** This is a Type B (data model design) question. The "architecture" is always web API → PostgreSQL — that's not the insight. The insight is *how the schema evolves* as requirements get richer.

```
── Stage 1: Minimal CRUD Schema ──────────────────────────────────

Web API → PostgreSQL. Two tables. No workflow. No policy enforcement.

PostgreSQL
├── employees          (id, name, email, employee_type, manager_id)
└── expense_reports    (id, employee_id, title, period, created_at,
                        updated_at)
    └── expense_line_items  (id, report_id, date, category, amount,
                              purpose, location, receipt_url)

Flow: employee creates report → adds line items → submits.
Manager receives nothing. Finance receives nothing. No approval
state tracks where the report is in the process.

BREAKING POINT 1: No approval state, so a manager's queue query has no
   predicate to filter on. Exhausted resource: not CPU — the *result set*.
   Each of the ~1,200 managers' queue fetches must return and then
   client-side filter ALL 500K reports/year instead of the ~10 awaiting
   that manager. At the ~40 req/sec month-end close read rate that is
   40 × 500K = 20M rows/sec streamed out of Postgres — the query planner
   has no choice but a full sequential scan.
   Observable symptom: approval queue page load goes from <100ms to
   >4s; Postgres `seq_scan` count on expense_reports climbs linearly
   with traffic; managers start emailing receipts instead.
   Why Stage 2 is needed: an indexable state column.

BREAKING POINT 2: No policy enforcement. Exhausted resource: none — this
   is a pure correctness hole, and those are worse than capacity ones
   because no dashboard shows them. At 2,000 reports/day × 5 line items
   = 10,000 line items/day accepted with zero validation, a single
   mistyped $10,000 meal (a fat-fingered $100.00 → $10000) reaches
   reimbursement.
   Observable symptom: nothing at all until finance reconciliation
   catches it weeks later, by which point the money has moved.
```

**DECISION — WHICH state tracking approach?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| No state (only created_at/updated_at timestamps) | Simplest schema | No workflow visibility; managers can't filter reports awaiting approval; compliance fails | ❌ Not enterprise-ready |
| Enum column (approval_state CHECK constraint; app validates transitions) | Simple; current state in one column; fast filter by state | DB does not enforce valid transitions — app must validate (draft→submitted, not draft→approved) | ✅ Right for 10K-employee scale |
| Separate state machine table (workflow_transitions rows per state change) | DB-enforced transitions; transition history built in | Complex JOINs to get current state; overkill for sequential single-path approval | ❌ Overkill at this scale |

> 📖 Full: **`SystemDesignConcepts/Foundations/Data-Fundamentals/12-data-modeling.md`**

```
── Stage 2: Add Approval Workflow + Policy Engine ────────────────

Add approval_state to expense_reports. Add approvals table for
approval decisions. Add expense_limits table for category rules.

PostgreSQL
├── employees          (id, name, email, employee_type, manager_id)
├── expense_reports    (id, employee_id, title, period,
│                       approval_state CHECK IN ('draft','submitted',
│                       'manager_approved','finance_approved',
│                       'reimbursed','rejected'),
│                       submitted_at, approved_at)
├── expense_line_items (id, report_id, date, category, amount,
│                       purpose, location, receipt_url)
├── approvals          (id, report_id, approver_id, approval_type,
│                       decision, reason, created_at)
└── expense_limits     (employee_type, category,
                        limit_per_transaction, limit_per_report,
                        PK: (employee_type, category))

Valid state transitions (enforced in application, CHECK at DB level):
  draft → submitted
  submitted → manager_approved | rejected
  manager_approved → finance_approved | rejected
  finance_approved → reimbursed | rejected

When employee submits → validate each line item against expense_limits
for their employee_type × category combination.

BREAKING POINT 1: No audit trail. Quantified: 10,000 line-item
   mutations/day (2,000 reports × 5 items) are UPDATEs in place, so
   100% of pre-edit values are unrecoverable. A SOX sample asks for
   30 reports and we can answer "who approved it" (from the approvals
   table) but not "what was the amount when they approved it."
   Observable symptom: the first disputed expense is unresolvable —
   there is no query that returns the answer, at any latency.
   Why Stage 3 is needed: before/after capture on every write.

BREAKING POINT 2: No duplicate detection. At 10,000 line items/day and
   an industry-typical ~1% duplicate-submission rate, that is ~100
   duplicate claims/day — ~25,000/year — each averaging $50, so roughly
   $1.2M/year of double-reimbursement exposure that no approver can
   catch by eye.
   Observable symptom: none in the system; it surfaces as an
   unexplained gap in the finance variance report.

BREAKING POINT 3: No concurrency control. Two people editing the same
   line item overwrite each other (lost update). Rare per-item, but the
   arithmetic still bites: with an employee and a finance analyst both
   touching month-end reports, a few collisions per week × 50 weeks is
   dozens of silently discarded edits/year — and the discarded one is
   usually the *correction*.
   Observable symptom: an employee swears they fixed an amount and the
   old value is back, with nothing in any log to prove either version.
```

**DECISION — WHICH business rule enforcement approach?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| Hardcoded limits in application code (`MEAL_LIMIT = 50`) | Fast; zero DB overhead; simple | Rule change = code redeploy + restart; finance cannot adjust limits without engineering | ❌ Not flexible |
| Rules table (`expense_limits`) with app-level cache (invalidate on rule change) | Change rules with a DB UPDATE; no code deploy; cache keeps validation fast | Cache invalidation when rules change | ✅ Best |
| Drools / external rules engine | Most flexible; non-technical users can define rules | Overkill for straightforward category-based limits; adds a new system to operate | ❌ Overkill |

> 📖 Full: **`SystemDesignConcepts/Foundations/Data-Fundamentals/12-data-modeling.md`**

```
── Stage 3: Add Audit Trail + Optimistic Locking + Dedup ─────────

Production schema. Adds: append-only audit_log for compliance,
version_number for concurrent edit protection, SHA256 fingerprint
for duplicate expense detection, sla_deadline for escalation.

PostgreSQL
├── employees          (id, name, email, employee_type, manager_id)
├── expense_reports    (id, employee_id, title, period,
│                       approval_state, submitted_at, approved_at,
│                       sla_deadline)  ← SLA escalation deadline
├── expense_line_items (id, report_id, date, category, amount,
│                       purpose, location, receipt_url,
│                       version_number,    ← optimistic locking
│                       fingerprint)       ← dedup: SHA256(employee+
│                                             date+merchant+amount)
├── approvals          (id, report_id, approver_id, approval_type,
│                       decision, reason, created_at)
├── expense_limits     (employee_type, category, limit_per_transaction,
│                       limit_per_report)
└── audit_log          (id SERIAL, resource_type, resource_id,
                        action, user_id, before_value JSONB,
                        after_value JSONB, reason, created_at)
                        ← append-only; NEVER updated or deleted

Every state transition → INSERT into audit_log(before, after).
On line item update → check version_number unchanged (optimistic lock).
On line item insert → fingerprint check for 90-day duplicate window.
On report submit → query expense_limits for validation.
On approval → INSERT approvals row + INSERT audit_log row.
```

**DECISION — WHICH audit trail approach?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| No audit trail (only current state in main tables) | Simple; no extra storage | Cannot prove who approved/changed what; compliance fails; disputes unresolvable | ❌ Compliance violation |
| Soft deletes only (deleted_at column, no change history) | Simple; preserves rows from deletion | Captures *that* a row was deleted but not *what* changed between edits | ⚠️ Insufficient alone |
| Append-only audit_log (before/after JSONB) + soft deletes | Full change history; queryable; compliance proof; JSONB flexible for any resource type | Audit table grows unbounded (manageable: ~1M rows/year at 1GB; archive to S3 after 1 year) | ✅ Best |

> 📖 Full: **`SystemDesignConcepts/Foundations/Data-Fundamentals/12-data-modeling.md`**

**Data flow walkthrough (say this out loud):**

1. **Create report:** Employee calls `POST /v1/reports`. System inserts expense_report with `approval_state = 'draft'`, logs to audit_log.
2. **Add expenses:** Employee calls `POST /v1/reports/{id}/expenses`. Policy validation fires (`422` if over limit); on insert, compute SHA256 fingerprint and check the 90-day duplicate window — warn if match; system inserts with `version_number = 1` and `receipt_status = 'PENDING'`.
3. **Attach receipt:** Employee calls `POST /v1/reports/{id}/expenses/{itemId}/receipt-upload-url`, `PUT`s the image straight to S3, and the S3 event notification flips `receipt_status = 'UPLOADED'`. The bytes never touch the app tier.
4. **Submit:** Employee calls `POST /v1/reports/{id}/submit` (the dedicated action endpoint from Section 8 — not a generic `PATCH` with a state field). System re-validates every line item against `expense_limits[employee_type][category]` and requires `receipt_status = 'UPLOADED'` on any item over the receipt threshold. On pass → `approval_state = 'submitted'`, `sla_deadline = now() + 5 days`, log to audit_log.
5. **Manager approval:** Manager calls `PATCH /v1/reports/{id}/approve { decision: approved, reason: ... }`. App checks the transition is *legal* (submitted → manager_approved) **and** that the actor is *authorized* — not the submitter, is the assigned approver, and hasn't approved an earlier step (Deep Dive 1). Then inserts into `approvals` (unique on `(report_id, approval_type)`, so a double-click yields `409`), updates `approval_state`, logs to audit_log.
6. **Concurrent edit protection:** If two users both read `version_number = 3` and try to write back, first write succeeds with `version_number = 4`. Second write does `UPDATE ... WHERE version = 3` — finds 0 rows → returns 409 Conflict → client retries with fresh data.

---

## Section 7 — 🔬 Core Component Deep Dives (Minutes 25–40)

**What to do:** Pick 2–3 *riskiest* components. "Riskiest" = where the system breaks, or what's unique to this problem.

**Why these for expense reports?**
1. **State machine + approval authorization** — Wrong design = approvals get stuck in invalid states, or worse, a manager approves their own $4,000 dinner because the state graph was modelled without an actor.
2. **Business rule validation — expense limits** — Wrong design = employees can submit expenses above limits; policy is unenforced.
3. **Audit trail — immutable history** — Wrong design = audit trail is incomplete; compliance fails.
4. **Receipt upload (do this one if you have time)** — Wrong design = 30 GB/day of phone photos through your app tier, which is what actually takes the system down.

**Say this out loud:**
> "Let me go deep on the three riskiest components — the ones where the system most likely breaks..."

---

### Deep Dive 1: State Machine — Expense Report Approval Workflow

**Why this is the most critical component:**
Approvals must follow a strict sequence (draft → submitted → approved → reimbursed). Wrong states or state transitions break compliance. An expense approved before submitted is a data integrity violation.

**State machine design (options):**

| Option | Approach | Pros | Cons |
|---|---|---|---|
| **Enum column** | approval_state VARCHAR enum (draft/submitted/manager_approved/finance_approved/reimbursed) | Simple, enforced at app level | No transition validation at DB; app must check valid transitions |
| **Check constraint** | Add CHECK constraint: valid transitions only (draft→submitted, submitted→manager_approved, etc.) | Validated at DB level | Complex CHECK logic; hard to modify rules |
| **State machine table** | Separate workflow_state table with rows for each state transition. Validate transitions before INSERT. | Extensible (easy to add new states/transitions) | More complex queries; extra joins |

**Decision: Enum column with app-level validation**
Because at this scale (10K employees), simplicity matters. The app layer validates transitions before updating the state column. The database can have a CHECK constraint as a safety net.

**Schema:**

```sql
CREATE TABLE expense_reports (
    id              UUID PRIMARY KEY,
    employee_id     UUID NOT NULL,
    submitted_date  TIMESTAMP,
    approval_state  VARCHAR(20) CHECK (approval_state IN ('draft', 'submitted', 'manager_approved', 'finance_approved', 'reimbursed')),
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

-- Only allow these state transitions:
-- draft → submitted
-- submitted → manager_approved OR submitted → rejected
-- manager_approved → finance_approved OR finance_approved → rejected
-- finance_approved → reimbursed
```

**Valid transitions pseudocode:**

```java
// "rejected" maps to an empty list — it is a terminal state
Map<String, List<String>> validTransitions = Map.of(
    "draft", List.of("submitted"),
    "submitted", List.of("manager_approved", "rejected"),
    "manager_approved", List.of("finance_approved", "rejected"),
    "finance_approved", List.of("reimbursed", "rejected"),
    "rejected", List.of()
);

public void transitionState(String reportId, String newState, UUID actorId) {
    String currentState = getState(reportId);
    if (!validTransitions.get(currentState).contains(newState)) {
        throw new InvalidStateTransition(currentState + " → " + newState);
    }
    // A legal transition is not automatically an AUTHORIZED one — see below
    authorizeTransition(reportId, currentState, newState, actorId);
    updateState(reportId, newState);
    logToAuditTrail(reportId, currentState, newState, actorId);
}
```

**⭐ A valid transition is not the same thing as an authorized one — and this is where the interviewer goes next.** The state machine above answers *"can the report move from submitted to manager_approved?"* It does not answer *"may **this person** move it?"* Two distinct controls are required, and candidates who only model the state graph get caught:

**Control 1 — no self-approval.** The single most exploited hole in every homegrown expense system: a manager submits their own $4,000 dinner and, because they hold the `manager` role, approves it. Or subtler — an employee's `manager_id` is stale after a reorg and now points at themselves.

**Control 2 — separation of duties across steps.** The *same* person must not satisfy both the manager and the finance approval, even if they legitimately hold both roles (common in a small finance org). Otherwise a two-step chain provides one-step assurance.

```java
/**
 * Authorization for an approval transition. Three checks, in this order —
 * the cheapest and most damning first.
 */
private void authorizeTransition(String reportId, String from, String to, UUID actorId) {
    ExpenseReport report = reportRepo.findById(reportId);

    // Check 1 — NO SELF-APPROVAL. Absolute, role-independent, no override.
    // Applies even if the actor is a director, the CFO, or the submitter's own manager.
    if (report.getEmployeeId().equals(actorId)) {
        throw new SelfApprovalForbiddenException(
            "The submitter of a report can never approve it, regardless of role"
        );
    }

    // Check 2 — the actor must be the CORRECT approver for this specific step,
    // not merely someone holding the role. 403, not 401.
    UUID expectedApprover = approverResolver.resolveFor(report, to);
    if (!expectedApprover.equals(actorId) && !delegations.isActiveDelegate(expectedApprover, actorId)) {
        throw new NotYourApprovalException("You are not the assigned approver for this step");
    }

    // Check 3 — SEPARATION OF DUTIES. Whoever approved an earlier step in this
    // report's chain cannot also approve a later one.
    if (approvalRepo.hasAlreadyApproved(reportId, actorId)) {
        throw new SeparationOfDutiesException(
            "You already approved an earlier step of this report"
        );
    }
}
```

**The edge cases you should volunteer before being asked** (this is the part that reads as experience rather than theory):

| Edge case | Correct behaviour |
|---|---|
| The CEO submits an expense — they have no manager | Route to a designated **alternate approver** (board audit committee / a second C-level). Never auto-approve. `manager_id IS NULL` must be a routing rule, not a bypass |
| An employee's `manager_id` points to themselves after a reorg | A DB `CHECK (manager_id IS NULL OR manager_id <> id)` constraint plus the runtime Check 1 above. Defence in depth, because bad HR-feed data is a *when*, not an *if* |
| A manager is on leave and delegates approval | An explicit `approval_delegations(from_user, to_user, valid_from, valid_to)` table, checked in Check 2. The delegate is recorded in `approvals.approver_id` **and** the original assignee in `approvals.on_behalf_of` — so the audit trail shows both. Delegation is never implicit |
| A manager approves their own direct report's report, and that report reimburses the manager | Out of scope for the data model, but name it: this is the *collusion* case that policy sampling and analytics catch, not a schema constraint |

**Enforce Check 1 at the DB layer too**, because an authorization bug in one code path shouldn't be able to write an illegal row:

```sql
-- The submitter can never appear as an approver of their own report
ALTER TABLE approvals ADD CONSTRAINT no_self_approval CHECK (
    approver_id <> submitter_id
);
-- Requires denormalising submitter_id onto approvals; that redundancy is the
-- price of having the constraint be checkable by the DB at all.

-- One approval per (report, approval_type) — a manager double-clicking Approve
-- cannot create two manager_approval rows
CREATE UNIQUE INDEX uq_approval_step ON approvals (report_id, approval_type);

-- Separation of duties: one person appears at most once per report's chain
CREATE UNIQUE INDEX uq_approver_once ON approvals (report_id, approver_id);
```

That `uq_approval_step` index is also the answer to the idempotency prerequisite in this file's Prereqs table: a manager double-clicking **Approve** produces a unique-violation on the second insert, which the API translates to a `409` — not a second state transition and not a second audit entry.

---

### Deep Dive 2: Business Rule Validation — Expense Limits

**Why this is the riskiest component:**
Business rules (employee can spend max $50 on meals) are the core of the system. Wrong validation = policy is unenforceable; company loses money.

**Validation options:**

| Option | Approach | Pros | Cons |
|---|---|---|---|
| **Hardcoded in app** | Limits are constants in code (MEAL_LIMIT = 50) | Fast (no DB lookup) | Hard to modify; requires code redeploy |
| **Rules table** | limits table: employee_type, category, limit_amount. App queries before accepting expense. | Flexible (change limits without redeploy) | DB lookup latency; need caching |
| **Validation engine** | Separate service that evaluates rules (e.g., Drools — a Java-based rules engine that lets you define business rules in declarative rule files, evaluated at runtime without code redeployment; powerful but complex) | Highly extensible | Overkill for this scale; adds complexity |

**Decision: Rules table with caching**
Because business rules change frequently (company adjusts meal limits) and shouldn't require code redeployment. Cache in application memory (invalidate cache on rule changes).

**Schema:**

```sql
CREATE TABLE expense_limits (
    employee_type   VARCHAR(20),  -- employee, director, contractor
    category        VARCHAR(20),  -- meals, flights, hotels, transport
    limit_per_transaction_cents BIGINT,
    limit_per_report_cents      BIGINT,
    PRIMARY KEY (employee_type, category)
);

-- Example data — integer cents, so $50.00 is 5000 and there is no rounding at all
INSERT INTO expense_limits (employee_type, category, limit_per_transaction_cents, limit_per_report_cents)
VALUES ('employee', 'meals',   5000,  50000),
       ('director', 'meals',  15000, 200000),
       ('employee', 'flights', 50000, 500000);
```

**Validation logic — note that every amount is a `long` of cents, never a `double`:**

```java
public void validateExpense(Expense expense, EmployeeType empType) {
    ExpenseLimit limit = limitsCache.get(empType, expense.getCategory());

    // Strictly greater-than: an expense of exactly the limit is allowed.
    // This is why cents matter — with double arithmetic a $50.00 expense can
    // compare as 50.000000000000007 > 50.0 and get rejected at its own limit.
    if (expense.getAmountCents() > limit.getPerTransactionCents()) {
        throw new PolicyViolationException(
            "Expense exceeds per-transaction limit of " + format(limit.getPerTransactionCents())
        );
    }

    // Report-level limit for this category, including the item being added
    long categoryTotalCents = getExpensesByReport(expense.getReportId()).stream()
        .filter(e -> e.getCategory().equals(expense.getCategory()))
        .mapToLong(Expense::getAmountCents)
        .sum() + expense.getAmountCents();

    if (categoryTotalCents > limit.getPerReportCents()) {
        throw new PolicyViolationException(
            "Report exceeds per-category limit of " + format(limit.getPerReportCents())
        );
    }
}
```

> **Lesson learned the hard way (Aug 2026):** every money field in this design is `BIGINT` cents end to end — API contract, DB column, and Java type. The moment one layer uses `DECIMAL`/`double` you get boundary-comparison bugs that only appear for specific amounts, which is the worst possible failure shape in a policy engine because "it works for $49.99 and $50.01 but rejects $50.00" is unreproducible until you look at the float bits.

---

### Deep Dive 3: Audit Trail — Immutable History

**Why this is the riskiest component:**
Audit trails are compliance (legal requirement). Incomplete or lossy audit = regulatory violation. Every state change must be logged durably.

**Audit trail design (options):**

| Option | Approach | Pros | Cons |
|---|---|---|---|
| **Log table** | audit_log table: timestamp, user_id, action, resource_id, before, after | Complete history, queryable | Append-only table grows unbounded |
| **Event sourcing** | Every change is an immutable event. State is reconstructed from events. | Audit trail is the source of truth | Complex to query current state (need to replay events) |
| **Soft deletes** | deleted_at column instead of hard delete. Historize changes via updated_at timestamps. | Simple; current state is latest row | Doesn't capture *what* changed; only *when* |

**Decision: Audit log table + soft deletes**
Because we need to answer "who approved this report and when?" and "what was the expense amount before the employee edited it?" The audit log answers these questions durably.

**Schema:**

```sql
CREATE TABLE audit_log (
    id              SERIAL PRIMARY KEY,
    resource_type   VARCHAR(50),  -- expense_report, expense_line_item
    resource_id     UUID,
    action          VARCHAR(20),  -- created, updated, approved, rejected
    user_id         UUID,
    timestamp       TIMESTAMP DEFAULT NOW(),
    before_value    JSONB,        -- previous state (for updates)
    after_value     JSONB,        -- new state
    reason          TEXT          -- optional: why the change
);

-- Example audit entry for approval.
-- Note the explicit column list — `INSERT ... VALUES (col='x', ...)` is not
-- valid SQL in any dialect; named assignment only exists in UPDATE ... SET.
INSERT INTO audit_log (resource_type, resource_id, action, user_id, before_value, after_value, reason)
VALUES (
    'expense_report',
    'report-123',
    'approved',
    'manager-456',
    '{"approval_state": "submitted"}'::jsonb,
    '{"approval_state": "manager_approved"}'::jsonb,
    'Approved. All expenses within policy.'
);
```

**"Append-only" has to be *enforced*, not just intended.** Saying the audit log is immutable while the app role holds `UPDATE`/`DELETE` on it is exactly the gap a SOX auditor is paid to find. Two layers:

```sql
-- Layer 1 — privilege: the application role can only ever append
REVOKE UPDATE, DELETE, TRUNCATE ON audit_log FROM expense_app_role;
GRANT  INSERT, SELECT                ON audit_log TO   expense_app_role;

-- Layer 2 — a rule that blocks mutation even for a role that somehow has it
CREATE FUNCTION reject_audit_mutation() RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION 'audit_log is append-only: % is not permitted', TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_log_immutable
    BEFORE UPDATE OR DELETE ON audit_log
    FOR EACH ROW EXECUTE FUNCTION reject_audit_mutation();
```

**In an interview:** "Immutability is a grant plus a trigger, not a comment in the schema. And I'd note the honest limit: a DB superuser or anyone with filesystem access can still rewrite history, so genuinely tamper-*evident* audit needs the log shipped to append-only external storage — S3 Object Lock in compliance mode, or a periodic hash-chain over the rows. For an internal expense system the grant plus trigger is proportionate; for DocuSign's *signing* audit trail, where the record is legal evidence under the ESIGN Act, you'd want the hash chain."

**Write-once, audit-everywhere pattern:**

Every API that modifies data calls this function:

```java
public void logAudit(String resourceType, UUID resourceId, String action,
                     UUID userId, Object before, Object after, String reason) {
    auditLog.insert(
        resourceType, resourceId, action, userId,
        objectToJson(before), objectToJson(after), reason
    );
}
```

---

### Deep Dive 4: Receipt Upload — Pre-Signed S3 URLs

**Why this matters:**
Every line item carries a receipt image — a 2–5 MB phone photo. There are exactly two designs, and picking the wrong one is the most common junior tell on this question.

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| Client `POST`s the image to the API, API forwards to S3 | One endpoint; server controls everything | Every byte crosses your app tier twice. At 2K reports/day × 5 receipts × 3 MB = **30 GB/day** flowing through app memory and thread pools; a `multipart` upload holds a request thread for the duration of a phone's upload on hotel wifi, so a handful of slow clients exhaust the connection pool and the *approval* endpoints time out |
| **Pre-signed S3 PUT URL** ✅ | Bytes go browser → S3 directly, never touching the app tier. App handles only ~200-byte JSON. Independently scalable, and S3 does the durability | Two round trips; needs an orphan-object story and an upload-confirmation story |

**Decision: pre-signed PUT URL, 15-minute expiry.** The app's job is authorization and metadata, not byte-shovelling.

### 🎨 Visual — pre-signed upload: three calls, and the two gaps between them

```
 EMPLOYEE (browser/phone)          EXPENSE API              S3 (private bucket)
        │                               │                          │
   (1)  │ POST /line-items/{id}/        │                          │
        │      receipt-upload-url       │                          │
        ├──────────────────────────────▶│                          │
        │                               │ authorize: is this MY    │
        │                               │ line item? is the report │
        │                               │ still in 'draft'?        │
        │                               │ generate pre-signed PUT  │
        │◀──────────────────────────────┤ expires in 900s          │
        │  { url, s3_key }              │ receipt_status = PENDING │
        │                               │                          │
   (2)  │  PUT <presigned url>  ── 3 MB image ────────────────────▶│
        │  (does NOT touch the API at all — this is the whole point)│
        │◀───────────────────────── 200 ───────────────────────────┤
        │                               │                          │
   (3)  │                               │◀── S3 event notification │
        │                               │    ObjectCreated:Put     │
        │                               │  receipt_status=UPLOADED │
        │                               │  verify size & MIME type │

  ▲ GAP A — between (1) and (2): the client never uploads (app crash, user
    abandons). Result: a PENDING row with no object. Handled by treating
    receipt_status='PENDING' as MISSING at submit time — the submit
    validation requires UPLOADED for any line item over the receipt
    threshold, so an abandoned upload blocks submission rather than
    silently producing an unsubstantiated claim.

  ▲ GAP B — between (2) and (3): the object exists but we haven't recorded
    it (S3 events are async, typically <1s but not guaranteed). Handled by
    a HEAD probe fallback: if a submit arrives while status is PENDING,
    do one synchronous S3 HEAD before rejecting. Belt and braces.

KEY INVARIANT:
   The DB row and the S3 object are written by DIFFERENT actors, so they are
   never atomic. The design therefore makes the DB the authority on
   *intent* (a key was reserved) and S3 the authority on *existence*, and
   reconciles them at the one moment it matters: report submission.
```

**Why the bucket is private and `receipt_s3_key` is a key, not a URL:** a receipt is a financial document showing an employee's name, location, and spending. A public-read URL is a permanent unauthenticated leak, and receipt URLs get pasted into Slack and email. Reads go through a short-lived pre-signed **GET** generated per request after the same authorization check as the metadata — so a manager who is no longer the approver loses access to the image immediately, rather than holding a URL forever.

**Server-side validation you cannot skip:** the pre-signed URL is a capability, so constrain it at generation time — pin `Content-Type` to `image/jpeg|image/png|application/pdf` and `content-length-range` to ≤ 10 MB in the policy. Without the length constraint an employee (or a stolen token) can push arbitrary data into your bucket at your cost. And re-verify the actual MIME type from the object's magic bytes on the S3 event, because `Content-Type` is client-asserted.

**Immutability, to match the audit-trail requirement:** once `receipt_status = UPLOADED`, the object is never overwritten. A corrected receipt gets a **new** key and the old key stays. Enable S3 versioning plus Object Lock (governance mode) on the bucket so the evidence for an approved claim cannot be swapped after the fact — the receipt is the substantiation for the approval, so mutable receipts make the immutable audit log meaningless.

---

## Section 9 — 🗄️ Data Model

### Core Tables

```sql
-- Employees
CREATE TABLE employees (
    id              UUID PRIMARY KEY,
    name            VARCHAR(255) NOT NULL,
    email           VARCHAR(255) UNIQUE NOT NULL,
    employee_type   VARCHAR(20),  -- employee, director, contractor
    manager_id      UUID,         -- who approves their reports
    created_at      TIMESTAMP DEFAULT NOW()
);

-- Expense Reports
CREATE TABLE expense_reports (
    id              UUID PRIMARY KEY,
    employee_id     UUID NOT NULL REFERENCES employees(id),
    title           VARCHAR(255),
    period          VARCHAR(7),   -- YYYY-MM
    approval_state  VARCHAR(20) CHECK (approval_state IN ('draft', 'submitted', 'manager_approved', 'finance_approved', 'reimbursed', 'rejected')),
    -- NO total_amount column here. See "the generated-column trap" below —
    -- a Postgres generated column may not contain a subquery, so the tempting
    -- `GENERATED ALWAYS AS (SELECT SUM(...) FROM expense_line_items)` will not
    -- even create. The report total is derived by a view (see reporting_totals).
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW(),
    submitted_at    TIMESTAMP,
    approved_at     TIMESTAMP
);

-- Expense Line Items
CREATE TABLE expense_line_items (
    id              UUID PRIMARY KEY,
    report_id       UUID NOT NULL REFERENCES expense_reports(id) ON DELETE CASCADE,  -- ON DELETE CASCADE: if the parent expense_report row is deleted, all its child line_item rows are auto-deleted; without this, deleting a report would leave orphaned line items with no parent
    date            DATE NOT NULL,
    category        VARCHAR(20),  -- meals, flights, hotels, transport
    amount_cents    BIGINT NOT NULL,  -- integer minor units; matches the API contract, never float
    currency_code   CHAR(3) NOT NULL DEFAULT 'USD',
    purpose         TEXT,
    location        VARCHAR(255),
    receipt_s3_key  VARCHAR(512),  -- S3 object key, NOT a public URL — see Deep Dive 4
    receipt_status  VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                        CHECK (receipt_status IN ('PENDING','UPLOADED','MISSING')),
    version_number  INT DEFAULT 1,  -- for optimistic locking
    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);

-- Approvals (track who approved what, when)
CREATE TABLE approvals (
    id              UUID PRIMARY KEY,
    report_id       UUID NOT NULL REFERENCES expense_reports(id),
    approver_id     UUID NOT NULL REFERENCES employees(id),
    approval_type   VARCHAR(20),  -- manager_approval, finance_approval
    decision        VARCHAR(20),  -- approved, rejected
    reason          TEXT,
    created_at      TIMESTAMP DEFAULT NOW()
);

-- Audit Log
CREATE TABLE audit_log (
    id              SERIAL PRIMARY KEY,  -- SERIAL: PostgreSQL auto-increment; equivalent to INTEGER NOT NULL DEFAULT nextval(); each new row gets the next sequential integer automatically
    resource_type   VARCHAR(50),
    resource_id     UUID,
    action          VARCHAR(20),
    user_id         UUID,
    before_value    JSONB,
    after_value     JSONB,
    reason          TEXT,
    created_at      TIMESTAMP DEFAULT NOW()
);

-- Expense Limits (business rules)
CREATE TABLE expense_limits (
    employee_type   VARCHAR(20),
    category        VARCHAR(20),
    limit_per_transaction_cents BIGINT,
    limit_per_report_cents      BIGINT,
    PRIMARY KEY (employee_type, category)
);

-- Report totals: a view, not a column. The DB recomputes on read, there is
-- exactly one definition of "total", and it can never drift from the line items.
CREATE VIEW report_totals AS
SELECT r.id AS report_id,
       COALESCE(SUM(li.amount_cents), 0) AS total_amount_cents
FROM expense_reports r
LEFT JOIN expense_line_items li ON li.report_id = r.id
GROUP BY r.id;
```

### ⚠️ The generated-column trap (know this — it's a live SQL error, not a style nit)

The natural instinct is to put the report total on the report row as a computed column:

```sql
-- ❌ THIS DOES NOT CREATE. Postgres rejects it.
total_amount DECIMAL(10,2) GENERATED ALWAYS AS
    (SELECT SUM(amount) FROM expense_line_items WHERE report_id = id) STORED;
```

Two independent reasons it's invalid, and naming either one is the senior signal:
1. A generated column's expression must be **IMMUTABLE and reference only columns of the same row**. Subqueries and other tables are forbidden outright.
2. Postgres only implements **STORED** generated columns, not `VIRTUAL`. So even if cross-table references were allowed, the value would be computed **on write**, not "fresh on every read" — and there is no write to the parent report row when a child line item changes, so the stored total would go stale immediately.

Three legitimate options:

| Option | How | When to pick it |
|---|---|---|
| **View / on-the-fly `SUM`** ✅ | `report_totals` view above | Default. At 5 line items per report the aggregate is free, and the total is structurally incapable of drifting |
| Maintained denormalized column | `expense_reports.total_amount_cents`, updated in the **same transaction** as every line-item insert/update/delete | Only when the approval-queue list view is measurably slow — you're buying read speed with a write-time invariant you must never break |
| Trigger-maintained column | `AFTER INSERT/UPDATE/DELETE` trigger on line items recomputes the parent | Avoid. Hides the write amplification and makes bulk imports mysteriously slow |

**Say this out loud:** "I'd expose the total as a view, not a stored column, because a stored total is a cache and caches drift. If the approval queue needs it inline, I'd maintain it in the same transaction as the line-item write — never as a generated column, because Postgres generated columns can't contain a subquery."

### Key Schema Decisions:
- **approval_state enum:** Enforces valid states at DB level (CHECK constraint).
- **No `total_amount` column; a `report_totals` view instead:** see the generated-column trap above. One definition of "total," zero drift risk, and at ~5 line items per report the aggregate costs nothing.
- **Money is `amount_cents BIGINT`, never `DECIMAL` and never `double`:** integer minor units match the `amount_cents` field already used in the Section 8 API contract, so there is no lossy conversion at the boundary. `DECIMAL` is exact in the DB but invites `double` arithmetic once the value is loaded into Java — and summing `double` amounts to compare against a policy limit is how an expense of exactly $50.00 gets rejected for exceeding a $50.00 limit.
- **version_number on line items:** For optimistic locking (detect concurrent edits).
- **approvals table:** Separate table to track approval history (who, what, when). Useful for audit.
- **audit_log JSONB:** Stores before/after as JSON; flexible schema for any resource type.
- **expense_limits table:** Centralized business rules, easy to modify without code changes.

---

## Section 10 — ⚠️ Trade-offs + Failure Modes (Minutes 40–48)

**What to do:** Name exactly 3 major trade-offs. For each: what you chose, what you gain, what you lose, what breaks if you chose wrong.

**Say this out loud:**
> "Let me step back and name the three major trade-offs in this design..."

---

### Trade-off 1: Enum State vs State Machine Table

- **Chose:** Enum column (approval_state VARCHAR) with app-level validation
- **Gain:** Simple schema, fast queries (no joins). Easy to understand current state.
- **Lose:** State transitions not validated at DB layer; app must check validity. Hard to add complex rules (e.g., "can only reject if finance hasn't approved yet").
- **Failure mode if wrong:** If we chose a full state machine table (separate workflow_state table), every query to get current state requires a JOIN to find the latest transition row per report — and "latest per group" is the expensive shape (a window function or correlated subquery, not a simple join). At 10K employees, simplicity is more valuable than extensibility. **Business impact:** The approval queue is the hot path — ~1,200 managers polling every 30s = ~40 req/sec during the 3-day month-end close (Section 4). Replacing an indexed `approval_state = 'submitted'` predicate with a latest-per-report subquery over 500K reports turns a sub-10ms index scan into a sort-and-aggregate; the queue page degrades from instant to seconds precisely when every manager is using it. For DocuSign's internal expense system that means approvals slip past the 5-day SLA, the escalation sweep fires on reports that were merely slow to load, and finance closes late.

### Trade-off 2: Hardcoded Limits vs Rules Table

- **Chose:** Rules table (expense_limits) with app-level caching
- **Gain:** Business rules can change without code redeployment. Finance can adjust meal limits on the fly.
- **Lose:** DB lookup latency on every expense validation. Must implement cache invalidation (when rules change, invalidate cache).
- **Failure mode if wrong:** If we chose hardcoded limits in code (constant MEAL_LIMIT = 50), every rule change requires a code redeployment and app restart. Business can't be agile. Rules table is the right choice here. **Business impact:** Finance raises the meal limit from $50 to $75 for a conference week — without the rules table, compliant expenses are auto-rejected for the 2-3 days it takes to ship a code change — for DocuSign this means employees submit valid receipts that bounce, creating a manual reconciliation backlog that finance must clear during an already-busy period.

### Trade-off 3: Audit Log Table vs Event Sourcing

- **Chose:** Audit log table (append-only) + soft deletes for current state
- **Gain:** Simple to query ("who approved this report?"). Current state is in the latest row; no need to replay events.
- **Lose:** Two sources of truth (current state in main tables + audit log). Audit log only captures that changes happened, not *how* to reconstruct state.
- **Failure mode if wrong:** If we chose full event sourcing (state = replay all events), every read of current state would replay that report's event stream. At 500K reports/year × ~15 events each = 7.5M events/year, the approval queue — which reads *current state for many reports at once* — becomes a replay of hundreds of thousands of events per page load, which no index can rescue. Hybrid (current state + audit log) is the right balance. **Business impact:** the queue read path is already the binding constraint at ~40 req/sec during month-end close, so this converts the system's hottest query into its slowest one. And in the other direction, an auditor asking "show me every approver and timestamp for report #12345" gets a single indexed `SELECT ... WHERE resource_id = ?` in ~200ms instead of a stream replay — for DocuSign's legal team responding to an HR investigation or SOX sample, that's the difference between answering during the call and opening a ticket.

---

## Section 11 — 🔐 DocuSign-Specific Depth (Minutes 48–52)

**What to do:** For Type B Product Architecture questions, DocuSign's evaluation focuses on SOLID principles, design patterns, scalability, and data model correctness. The DocuSign angle: how does this design support enterprise workflows (approval chains, audit trails, policy enforcement)?

**After the trade-offs, say this out loud:**

> "Let me map this to DocuSign's evaluation dimensions and their product context:
> - **Scalability:** Schema supports 10K+ employees, millions of reports. Single PostgreSQL instance handles load; no sharding needed. Indexes on employee_id and approval_state enable fast filtering.
> - **Testability:** Business logic (approval transitions, expense validation) is testable in isolation. Mock the database; test state machine logic independently.
> - **Extensibility:** New approval workflows = new states in enum + new transitions. New expense categories = new row in limits table. New business rules = new validation in app logic (Strategy pattern).
> - **SOLID principles:** Single Responsibility — ExpenseValidator handles validation; ApprovalService handles approvals. Open/Closed — add new validation rules without modifying existing rules.
> - **Design patterns:** State Machine (approval workflow), Strategy (pluggable validation rules), Audit Trail (immutable history).
> - **Data model correctness:** Referential integrity via foreign keys; audit trail is immutable (append-only). Consistency via constraints (approval_state enum, CHECK clauses).
> - **Compliance:** Audit log provides proof of who approved/rejected, when, and why. Immutable history required for enterprise/regulatory compliance."

---

## Section 12 — 🔬 Where the Interviewer Will Probe (Minutes 52–60)

**What to do:** Prepare for 3 tiers of follow-ups. Tier 1 (surface), Tier 2 (deep), Tier 3 (cross-concept).

---

### Surface Probe (Tier 1 — every candidate gets this)

**Q: "What prevents an employee from editing an expense after the manager has approved it?"**
> The approval_state is submitted → manager_approved. Once in manager_approved state, the API blocks PATCH /expenses/{id} unless the approver explicitly rejects the report (moving it back to submitted). The database has no FK dependency stopping edits, but the API layer enforces the rule: if state != "draft", reject the edit request. In an interview: "The approval state controls what operations are allowed. Draft allows edits; submitted and beyond lock the report."

**Q: "How do you handle the case where a report has multiple expense categories (5 meals, 2 flights)? Do you check limits per category or total report?"**
> Limits are per-category (meals have a $50 limit, flights have a $500 limit). The validation logic sums all expenses in the category and checks against category-specific limits. So a report with $45 of meals + $450 of flights is valid (each category under its limit). If I extended this to per-report budgets, that would require a different table structure (per-report quota).

**Q: "Can a manager edit the employee's expenses before approving?"**
> No. The design assumes managers only approve/reject; they don't edit. If managers need to edit (e.g., correct an amount), that requires a different workflow. Current design: employee submits → manager approves/rejects. If rejected, employee re-submits.

### Deep Probe (Tier 2 — tests real understanding)

**Q: "You're using approval_state as an enum. But what if the company later decides that contractors skip manager approval (go straight to finance)? How do you extend the state machine?"**
> Good question. Current design has one state machine for all employee types. To support contractor workflow (submitted → finance_approved directly), I'd either (1) add conditional logic in the state machine ("if contractor, skip manager approval"), or (2) parameterize the state machine by employee_type. Option 2 is cleaner: create a workflow_configuration table: employee_type → valid_states → valid_transitions. Then the approval service looks up the workflow for the employee type and applies the appropriate state machine. This is extensible without modifying the core schema. See `01-optimistic-pessimistic-locking.md` for concurrency patterns if workflows need to run in parallel.

**Q: "The audit_log stores before/after as JSONB. But if an expense line item has 100 fields, storing all 100 fields on every change is wasteful. How do you optimize?"**
> Two approaches: (1) Store only the fields that changed (delta encoding): before={amount: 50}, after={amount: 75}. Saves space but reconstructing full state requires merging all deltas. (2) Store full before/after (current design) but compress using PostgreSQL COMPRESSION. Trade-off: space vs query complexity. At 1M reports/year with ~5 line items each = 5M line items. If each audit entry is 1 KB, that's 5 GB/year. Compression likely keeps it under 2 GB/year. Worth the storage cost for correctness.

### Cross-Concept Probe (Tier 3 — separates senior candidates)

**Q: "Two employees are both editing the same expense line item concurrently (race condition). Employee A changes amount from 50 to 60; Employee B changes purpose from 'lunch' to 'team lunch'. Both see version 1, both write version 1. Who wins?"**
> This is the classic lost update problem. Optimistic locking solves it: add version_number to line items. Employee A reads version=1, edits, writes back version=2 if version is still 1. Employee B reads version=1, tries to write version=2, fails because version is now 2 (changed by A). B's write is rejected; B retries with fresh data. This prevents lost updates. See `01-optimistic-pessimistic-locking.md` for detailed implementation. In an interview: "I'd use optimistic locking with a version column. On update, check that version hasn't changed; if it has, the concurrent writer gets an error and retries."

---

### New Deep Probes (Tier 2 — added Jul 4, 2026)

**Q: "Your approval state machine has one fixed chain: manager → finance. But what if expenses over $5,000 need VP approval, and expenses from contractors skip manager approval entirely? How do you model threshold-based, role-based routing?"**
> The state machine needs to be data-driven (policy-as-data), not hard-coded in the application.
>
> **Add a `workflow_rules` table:**
> ```sql
> CREATE TABLE workflow_rules (
>     id UUID PRIMARY KEY,
>     employee_type VARCHAR(50),    -- 'EMPLOYEE', 'CONTRACTOR', 'ANY'
>     expense_category VARCHAR(50), -- 'MEALS', 'TRAVEL', 'ANY'
>     min_amount NUMERIC,           -- NULL = no lower bound
>     max_amount NUMERIC,           -- NULL = no upper bound
>     required_approvers JSONB,     -- e.g. ["manager", "vp_finance"]
>     priority INT NOT NULL         -- which rule wins if multiple match
> );
>
> -- Example rows:
> -- (CONTRACTOR, ANY, NULL, NULL, ["finance"]) → contractors skip manager
> -- (EMPLOYEE, ANY, 5000, NULL, ["manager", "vp_finance"]) → high-value needs VP
> -- (EMPLOYEE, ANY, NULL, 5000, ["manager", "finance"]) → standard
> ```
>
> When a report is submitted, the Approval Engine queries all matching rules (employee_type + category + amount), collects the union of required approvers, and creates one `approval_tasks` row per required approver. The report only advances to `FULLY_APPROVED` when all tasks are `APPROVED`.
>
> **In an interview:** "I'd use a workflow_rules table — policy as data. Adding a new threshold or role exemption is a new DB row, not a code change. The Approval Engine looks up rules at submit time and generates the correct tasks. This is how enterprise workflow systems like ServiceNow and Workday work."

---

**Q: "An employee submits the same $50 lunch receipt twice — same date, same merchant, same amount. How does your system detect and handle duplicate expense line items?"**
> **Probabilistic fingerprint-based deduplication.** For each new line item, compute a fingerprint at insert time:
>
> ```java
> String fingerprint = sha256(
>     employeeId + "|" +
>     expenseDate.toString() + "|" +
>     merchantName.toLowerCase().trim() + "|" +
>     amount.toPlainString()
> );
> ```
>
> Store `fingerprint VARCHAR(64)` on `expense_line_items`. Before accepting a new line item, check:
> ```sql
> SELECT id FROM expense_line_items
> WHERE employee_id = ? AND fingerprint = ?
>   AND state NOT IN ('REJECTED', 'CANCELLED')
>   AND expense_date > NOW() - INTERVAL '90 days';
> ```
>
> If a match exists → return HTTP 409 with message: "This expense looks like a duplicate of item #{existing_id} submitted on {date}. Is this intentional?"
>
> The system warns but does NOT auto-reject — legitimate duplicates exist (two meals at the same restaurant same day). The employee must explicitly confirm: "Yes, this is a separate expense" → system adds a `duplicate_confirmed_by` flag and accepts it.
>
> **In an interview:** "I'd use a fingerprint composite key: employee + date + merchant + amount. 90-day lookback prevents false positives for recurring expenses. A warning-not-rejection UX preserves employee agency while surfacing genuine mistakes."

---

**Q: "Your design stores amounts in USD only. An employee in Germany submits a €150 dinner receipt. How do you handle multi-currency?"**

> **Rate-locking at submission time** is the critical design decision. Two wrong approaches and why:
>
> - **Wrong 1 — convert at approval time:** The rate on submission day vs. approval day differs. The manager approves "€150 = $162", but by approval the rate moved and the reimbursement is "$159". The employee is reimbursed less than what the manager approved. Support ticket guaranteed.
> - **Wrong 2 — store only the local amount, convert at display time:** Every UI query hits the FX API. Rate changes hourly; the same approved amount shows different USD values to different viewers. Finance can't close their books on a moving number.
>
> **Correct schema:**
> ```sql
> -- amounts stay in integer minor units; only the FX RATE is fractional
> ALTER TABLE expense_line_items ADD COLUMN amount_local_cents BIGINT NOT NULL;
> ALTER TABLE expense_line_items ADD COLUMN fx_rate_at_submission NUMERIC(18,8);
> ALTER TABLE expense_line_items ADD COLUMN fx_rate_date DATE;
> ALTER TABLE expense_line_items ADD COLUMN amount_usd_cents BIGINT NOT NULL;
> ```
>
> **On `POST /v1/reports/{id}/expenses`:**
> 1. Receive `{amount_cents: 15000, currency_code: "EUR"}`
> 2. Call FX provider (ECB / OpenExchangeRates) for the EUR→USD rate (e.g., 1.083)
> 3. Store `amount_local_cents=15000, currency_code='EUR', fx_rate_at_submission=1.08300000, fx_rate_date='2026-08-09', amount_usd_cents=round(15000 × 1.083)=16245`
> 4. `amount_usd_cents` is immutable from this point forward — the rate is locked at submission
>
> **Which rate date, exactly?** Say this precisely, because "current rate" is not a specification. Use the FX provider's **published daily reference rate for the expense date**, not the live intraday spot rate at the moment of the API call — the ECB publishes one rate per currency per business day at 16:00 CET. Two reasons: (1) two employees submitting the same €150 dinner on the same day get identical USD amounts, so the policy limit applies to both identically; (2) finance can reproduce and defend every conversion from a published, auditable source, which an intraday spot quote from a rate API cannot do. Store `fx_rate_date` so the conversion is reproducible years later during an audit. If the expense date is a weekend or holiday with no published rate, use the last preceding business day's rate and record that date — never interpolate.
>
> **Policy limit enforcement:** always compare `amount_usd_cents` against the limit (e.g., meal limit 7500 cents). The employee sees their local-currency amount; the system enforces limits in the base currency. Deterministic regardless of when the manager approves.
>
> **The rounding trap:** round *once*, when computing `amount_usd_cents`, and never re-derive it. If a downstream report recomputes `amount_local_cents × fx_rate` independently it can land a cent away from the stored value, and then the sum of line items won't equal the report total — which finance will notice before you do.
>
> **In an interview:** "Multi-currency requires rate-locking at submission time. I store both the original local amount and the USD equivalent computed at submission. All downstream logic — limit enforcement, approval, reimbursement — uses the locked USD amount. This eliminates FX drift between submission and reimbursement."

---

### New Cross-Concept Probe (Tier 3 — added Jul 4, 2026)

**Q: "A manager goes on vacation. The employee's expense report has been PENDING_MANAGER_APPROVAL for 8 days. The employee is waiting for reimbursement. What does your system do?"**
> **SLA enforcement with escalation chain.** When a report transitions to `PENDING_MANAGER_APPROVAL`, record the SLA deadline: `sla_deadline = submitted_at + 5 days` (configurable per company policy).
>
> **Scheduled job runs daily:**
> ```sql
> SELECT r.id, r.manager_id, r.employee_id
> FROM expense_reports r
> WHERE r.state = 'PENDING_MANAGER_APPROVAL'
>   AND r.sla_deadline < NOW()
>   AND r.escalation_level = 0;
> ```
>
> For each SLA breach:
> - **Day 5 (escalation level 1):** Email reminder to manager + employee ("Your expense report is overdue")
> - **Day 7 (escalation level 2):** Auto-escalate to manager's manager; record in `audit_log` with action = `SLA_ESCALATED`; update `approval_tasks.assigned_to = manager_of_manager_id`
> - **Day 10 (escalation level 3):** Business policy decision — options: (a) auto-approve with compliance flag, (b) route to a dedicated expense ops team
>
> The `audit_log` records every escalation step: timestamp, trigger, assigned_to, reason. The employee can query their report history and see "Manager auto-escalated after 7 days."
>
> **In an interview:** "SLA enforcement is a scheduled job + escalation chain. The state machine's `PENDING_MANAGER_APPROVAL` state has a TTL. Escalation is a state machine extension — not a separate feature but another valid transition path triggered by time, not user action. The audit log makes every escalation visible for compliance."

---

## Section 13 — 🐞 Common Mistakes on This Question

**Note:** Reading these mistakes BEFORE the interview prevents you from making them under stress.

---

- **Mistake 1:** No audit trail or soft deletes → **Why wrong:** Compliance requires immutable history. Hard deletes lose forensic evidence. **Say instead:** "I'd add an audit_log table and use soft deletes (deleted_at column) so that all changes are auditable and recoverable."

- **Mistake 2:** Ignoring concurrency (no optimistic/pessimistic locking) → **Why wrong:** Multi-user edits lead to lost updates (two employees editing same expense, one write is lost). **Say instead:** "I'd add version_number for optimistic locking. On update, verify the version hasn't changed since the user read it."

- **Mistake 3:** State machine logic in database vs. app → **Why wrong:** If you hardcode all states in the database, you can't easily add new workflows (e.g., contractor workflow without manager approval). **Say instead:** "State is stored as an enum column; transitions are validated in the app. This allows flexibility without schema changes."

- **Mistake 4:** No separation of concerns (business logic mixed with SQL) → **Why wrong:** Hard to test, hard to change rules. **Say instead:** "Validation logic (ExpenseValidator, ApprovalService) is separate from schema. Each class has one responsibility (SOLID)."

- **Mistake 5:** Forgetting about cascading deletes and FK constraints → **Why wrong:** Deleting an employee leaves orphaned reports. Deleting a report leaves orphaned line items. **Say instead:** "FK constraints with ON DELETE CASCADE ensure data integrity. Deleting a report cascades to line items and approvals." *(Caveat worth voicing: `ON DELETE CASCADE` and an immutable audit trail are in tension — for an approved report you should soft-delete, because cascading away the line items destroys the evidence for an approval that already happened.)*

- **Mistake 6:** Modelling the approval state machine without an actor → **Why wrong:** A transition table answers "can this report move from submitted to manager_approved?" but never "may *this person* move it?" That omission is a real exploit, not a theoretical one: a manager submits their own $4,000 dinner and approves it because they hold the `manager` role, or a post-reorg HR feed leaves an employee's `manager_id` pointing at themselves. It also lets one person satisfy both the manager and finance steps, which reduces a two-step control to a one-step one. **Say instead:** "Every transition takes an `actorId` and passes three checks: the submitter can never approve their own report — absolute, no role override; the actor must be the assigned approver for *this* step (or an explicitly recorded delegate); and nobody may approve two steps of the same chain. I'd back the first and third with DB constraints — `CHECK (approver_id <> submitter_id)` and `UNIQUE (report_id, approver_id)` — so an authorization bug in one code path still can't persist an illegal approval."

- **Mistake 7:** Uploading receipt images through the API → **Why wrong:** 2,000 reports/day × 5 receipts × 3 MB = 30 GB/day through your app tier, and a `multipart` handler holds a request thread for the whole duration of a phone upload on hotel wifi — a few dozen slow clients exhaust the connection pool and the *approval* endpoints start timing out. The image path takes down the workflow path. **Say instead:** "The API mints a 15-minute pre-signed S3 PUT URL and the client uploads directly to a private bucket; the app only ever handles ~200 bytes of JSON. An S3 event notification flips `receipt_status` to `UPLOADED`, and submit-time validation treats `PENDING` as missing so an abandoned upload blocks submission rather than producing an unsubstantiated claim."

---

## Section 14 — 🧭 DocuSign Dimensions Checklist

| Dimension | Relevant? | How your design addresses it |
|---|---|---|
| Testability | ✅ | The transition table is a pure function, so all 9 legal `(state, target)` pairs plus the illegal ones are exhaustive table-driven tests with no DB. The *authorization* rules are the ones that actually need testing and they're separable too: self-approval, wrong-approver, and separation-of-duties each get a unit test against a fake `ApproverResolver`. Policy validation is tested at the boundary in integer cents — `4999 / 5000 / 5001` against the $50.00 meal limit — which is the exact case a `double` implementation gets wrong. The 30 GB/day receipt path is testable without S3 by asserting the pre-signed URL's policy (Content-Type pinned, 10 MB length cap) rather than performing an upload. |
| Usability | ✅ | RESTful hierarchy with named actions rather than state-field PATCHes: `POST /reports/{id}/submit`, `PATCH /reports/{id}/approve`. The manager queue is `GET /v1/reports?state=submitted` — for the ~1,200 managers at ~40 req/sec during month-end close, that one filter is the difference between a 10-row page and all 500K reports. Every response includes `approval_state` so the UI knows which button to render without duplicating the state machine client-side. Error codes are actionable rather than generic: a `422` tells the employee *which* limit they exceeded and by how much, so they fix it themselves instead of filing a ticket; a `409` on submit says "already submitted" rather than a bare failure. |
| Extensibility | ✅ | New expense categories = new row in expense_limits (no code deploy). New approval tier (VP threshold for > $5K) = new enum value + new routing row. For DocuSign's global team: different countries have different reimbursement tax rules — all configurable as expense_limits rows parameterized by `(employee_type, category, country_code)` without re-deployment. |
| Security | ✅ | RBAC: managers approve only their direct reports' submissions (enforced by employee_id → manager hierarchy query on every approval). Audit log is append-only (DB trigger blocks UPDATE/DELETE) — for DocuSign's HR/legal team this is the tamper-proof record of "who approved Kapil's $800 client dinner on June 15?" required during internal investigations and SOX audits. |
| Availability | ✅ | At ~5 row-writes/sec peak (Section 4: 2,000 reports/working-day × ~15 rows each, 3× burst) a single Postgres primary at ~5,000 writes/sec is at 0.1% of capacity — no sharding, and the 99.9% SLO is met by Multi-AZ failover alone (~30s RTO). The failure that actually hurts is *receipt* availability during a month-end close: because uploads go browser → S3 directly, an app-tier deploy or restart cannot interrupt an employee's in-flight 3 MB receipt upload — the 30 GB/day receipt path has no dependency on our uptime at all. |
| Scalability | ✅ | The binding constraint is the read path, not writes: ~1,200 managers refreshing their approval queue every 30s during the 3-day month-end close = ~40 req/sec of `WHERE manager_id = ? AND approval_state = 'submitted'`. A composite index on `(manager_id, approval_state)` plus `(approval_state, sla_deadline)` for the escalation sweep turns each into an index range scan over ~10 rows instead of a filter over 500K — sub-10ms, so 40 req/sec uses a fraction of one core. The number that would force a real architecture change is the 30 GB/day of receipts, and that is offloaded to S3 rather than scaled. |
| Observability & Traceability | ✅ | Audit log (append-only, JSONB before/after state) reconstructs the full approval history of any report — for DocuSign's compliance team, "show me every approver and timestamp for report #12345" is a 200ms query returning the exact chain-of-custody. SLA deadline alerts fire when reports sit > 5 days unapproved (finance close escalation). |

---

## Section 15 — 🧾 TL;DR Answer Summary (Review Morning-of-Interview)

**If you had 60 seconds to summarize the entire answer, say this:**

> "I'd design a PostgreSQL schema with expense_reports, expense_line_items, approvals, and audit_log tables. The approval workflow is a state machine (draft → submitted → manager_approved → finance_approved → reimbursed) stored as an enum column, with app-level validation of transitions. Business rule limits (expense category caps, employee type restrictions) are stored in an expense_limits table for flexibility — no hardcoded rules. Audit trail is append-only with before/after JSONB; soft deletes preserve history for compliance. Concurrent edits are handled via optimistic locking (version_number column). The design is simple (PostgreSQL scales to millions of reports), testable (business logic is isolated from SQL), and extensible (new workflows/limits don't require schema changes). For a DocuSign enterprise workflow context, this provides the audit trail, state machine, and role-based approval structure that compliance requires."

**Why read this before your interview?**
The TL;DR fixes the core idea in your head. Under stress, you'll default to this mental model. When the interviewer asks unexpected questions about concurrency or new workflows, you'll reason from this schema design, not from memorized details.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 23, 2026 | **File created.** Type B — Product Architecture. Based on: InterviewQuery actual interview report (given UI mockup, candidate designed schema with validation rules). Concept notes: `12-data-modeling.md`, `01-optimistic-pessimistic-locking.md`. Fully integrated with DELIVERY-RECIPE framework: 🧠 preamble + 60-minute time budget, 💾 Memory Anchors (6 core + 3 bonus), explicit timing callouts in sections 2/4/6/7/10/11/12, "say this out loud" dialogue framing, interview psychology context. Deep dives: state machine (enum vs table), business rule validation (hardcoded vs rules table), audit trail (log table vs event sourcing). Section 5 variation table covers 6 axes (single vs multi-user, sequential vs parallel approval, customizable categories, manager overrides, different approval workflows, contractor vs employee workflows). Section 8 (API) and Section 9 (Data Model) are primary deliverables (Type B emphasis). Pre-write checklist enforced: Identity Card, clarifying questions with WHY, API endpoints + schema with justifications, 3 deep dives on riskiest components, trade-offs with failure modes, 3-tier probes (surface/deep/cross-concept). Common Mistakes (5 entries) emphasize audit trails, concurrency, state machine flexibility, SOLID principles, FK constraints. Result: Interview delivery-ready, zero refinement needed. |
| Jul 5, 2026 | **Section 6 restructured: flat logical architecture → 3-stage schema evolution.** This is a Type B (data model design) question — the evolution is in the schema, not infrastructure. Stage 1 (Minimal CRUD): employees + expense_reports + expense_line_items — BREAKING POINTs: no approval_state (workflow invisible); no expense_limits (policy unenforced). Stage 2 (Approval Workflow + Policy Engine): add approval_state enum column, approvals table, expense_limits (employee_type × category); state transitions validated at app level with CHECK constraint — BREAKING POINTs: no audit trail (can't reconstruct who changed what); no duplicate detection; no optimistic locking (lost update on concurrent edits). Stage 3 (Production): add audit_log (JSONB before/after, append-only), version_number on line_items (optimistic locking), SHA256 fingerprint (90-day duplicate window), sla_deadline (escalation). Three inline decision tables: (1) state tracking — no state ❌ / enum column ✅ / state machine table ❌; (2) business rules — hardcoded ❌ / rules table ✅ / Drools ❌; (3) audit trail — none ❌ / soft deletes only ⚠️ / audit_log JSONB ✅. All Section 6 verdicts verified against Section 7 deep dive choices — no contradictions. |
| Jul 4, 2026 | **4 new Q&As added to Section 12.** (1) **Threshold-based approval routing** — `workflow_rules` table with `(employee_type, expense_category, min_amount, max_amount, required_approvers JSONB)` enables policy-as-data; Approval Engine reads rules at submit time, generates tasks for each required approver; adding VP-threshold = new DB row, no code change; (2) **Duplicate expense detection** — SHA256 fingerprint of (employee_id + date + merchant + amount) stored on line item; 90-day lookback check before insert; warning-not-rejection UX (employee confirms intentional duplicates); legitimate duplicates marked with `duplicate_confirmed_by` flag; (3) **SLA enforcement with escalation chain** — `sla_deadline = submitted_at + N days`; daily scheduler queries overdue reports; escalation levels: Day 5 = email reminder, Day 7 = auto-escalate to manager's manager (audit_log entry), Day 10 = route to ops team; every escalation visible in audit trail. |
| Aug 2026 | **Audit pass — one invalid-SQL fix, two closed gaps, scale math reconciled.** (1) **`total_amount GENERATED AS (SELECT SUM(...))` removed — that DDL does not create in PostgreSQL.** A generated column's expression must be IMMUTABLE and reference only same-row columns (no subqueries, no other tables), and Postgres implements only `STORED`, so the inline gloss claiming "the DB derives it fresh on every read" was also wrong — a stored total would go stale the instant a child line item changed. Replaced with a `report_totals` view plus a decision table covering view / same-transaction denormalised column / trigger, and a "say this out loud" line. (2) **`INSERT INTO audit_log VALUES (resource_type='...', ...)` fixed** — named assignment isn't valid in `INSERT`, only in `UPDATE ... SET`; now an explicit column list with `::jsonb` casts. (3) **Approval authorization added** (the biggest gap — the state machine had no actor at all, so it could answer "is this transition legal?" but never "may *this person* make it?"): no-self-approval as an absolute role-independent rule, assigned-approver check with an explicit delegation table, separation of duties across steps, plus DB-level `CHECK (approver_id <> submitter_id)`, `UNIQUE (report_id, approval_type)` (which is also the manager-double-click idempotency guard → `409`) and `UNIQUE (report_id, approver_id)`. Edge-case table for the CEO-has-no-manager, self-referencing-`manager_id`-after-reorg, and vacation-delegation cases. (4) **Receipt upload added as Deep Dive 4** — it was a listed prerequisite in the Prereqs table but appeared nowhere in the solution: pre-signed S3 PUT vs proxying-through-the-API with the 30 GB/day arithmetic, a Picture+Invariant visual naming the two non-atomic gaps (URL minted but never uploaded / uploaded but event not yet processed) and how submit-time validation reconciles them, private bucket + per-request pre-signed GET, Content-Type and 10 MB length-range pinning, and Object Lock so receipts backing an approval can't be swapped. (5) **Money unified on `BIGINT` cents** end to end — Section 8's API already used `amount_cents` while Section 9's schema used `DECIMAL(10,2)` and the validation code summed `double`, which is how a $50.00 expense gets rejected against a $50.00 limit; limits, line items, and the FX probe all converted, with only the FX *rate* left fractional. (6) **Scale math reconciled** — Section 3 said 1M reports/year, Section 4 derived 2K/day (= 500K/year), and Section 14 cited "6 writes/sec" and "50K concurrent approval-queue refreshes" for a 10K-employee company (impossible). Now one coherent set: 500K reports/year, 2,000/working-day, ~5 row-writes/sec peak, and a ~40 req/sec approval-queue read path from ~1,200 managers during month-end close — with the read path correctly identified as the binding constraint and 30 GB/day of receipts as the real capacity number. (7) **All six Section 6 breaking points quantified** (they were purely narrative) — full-scan result-set size for the missing state column, 10K unvalidated line items/day, 100% unrecoverable pre-edit values, ~$1.2M/year duplicate exposure. (8) Audit-log immutability now *enforced* (REVOKE + `BEFORE UPDATE OR DELETE` trigger) rather than asserted, with the honest limit named (superuser can still rewrite; hash-chain or S3 Object Lock for tamper-evidence). (9) Status codes: `409` added to `/approve` (Key Design Decisions promised it but the table omitted it), every 4xx now has a named trigger, and the Section 6 data flow no longer calls a `PATCH /v1/reports/{id}` submit endpoint that Section 8 doesn't define. (10) FX probe now specifies *which* rate date (published daily reference rate for the expense date, weekend fallback rule, stored `fx_rate_date`) rather than "current rate". |
| Jul 5, 2026 | **Section 10 business impact pass.** Added **Business impact:** sentence to all 3 trade-offs — quarterly-close join latency blocking manager approvals of legitimate expenses (normalization denormalization cost), conference meal category hardcoded requiring 2-3 day code deployment to fix (hardcoded business rules), auditor replay of 1M expense events with no upper-bound pagination causing TimeoutException during SOX audit (event sourcing recovery cost). |
