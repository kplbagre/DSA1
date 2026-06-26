# C2 — Expense Report System — Data Model Design

> **Read `solution-notes-standards.md` first.** This file is a 60-minute interview answer framework, not a concept reference. Concept-level depth lives in `SystemDesignConcepts/`.

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
- Scale: 10K employees, 1M expense reports/year = ~2,700 reports/day = ~0.03 per second (not latency-critical)
- Latency: P99 < 500ms (not performance-critical)
- Availability: 99.9% (standard enterprise SaaS)
- Consistency: strong (expense amounts must be accurate; approvals must be durable)
- Compliance: audit trail, immutable approval history

---

## Section 3.5 — 🗂️ Core Entities (~2 minutes)

> **Say this out loud:** "Before I sketch the architecture, let me name the key data objects the system manages."

| Entity | What it represents | Storage |
|---|---|---|
| **Employee** | The person submitting expenses — department, role, manager relationship | PostgreSQL |
| **ExpenseReport** | A grouped submission of multiple expenses for one period/trip — the main workflow unit | PostgreSQL |
| **LineItem** | One individual expense within a report — amount, category, receipt attachment | PostgreSQL |
| **Approval** | Decision record — who approved/rejected, when, and with what comment | PostgreSQL |
| **ExpensePolicy** | Business rule — spending limits per category per employee tier | PostgreSQL |
| **AuditLog** | Immutable record of every state change for compliance — never updated, only appended | PostgreSQL (append-only) |

**Key relationships:**
- An `Employee` submits many `ExpenseReports` (one-to-many)
- An `ExpenseReport` contains many `LineItems` (one-to-many)
- An `ExpenseReport` has a chain of `Approvals` as it moves through the workflow (one-to-many)
- `ExpensePolicy` is looked up by `(department, category)` at submission time to validate amounts

---

## Section 4 — 🔢 Scale Estimation (Minutes 5–10)

**What to do:** Do envelope math out loud. These numbers justify every architecture choice you make in Section 6+. The interviewer wants to see your *thinking*, not just your conclusion.

**Say this out loud (as you write the math on the whiteboard):**
> "Let me do some envelope math to justify the schema. Starting with scale..."

---

**Scale:**
- DAU: 10K employees submitting expenses
- Active reports/day: 10K employees × (1 report/week average) ÷ 5 = 2K reports/day
- Requests/sec: not performance-critical, ~0.02 req/sec average (database scale, not web scale)
- Storage: 2K reports/day × 365 days × 5 line items/report × (1 KB line item) = ~3.65 GB/year

**Key conclusions:**
- "At 0.02 req/sec, this is a database workload, not a web-scale problem. PostgreSQL is appropriate; we don't need sharding."
- "At 3.65 GB/year, data fits on a single DB node for 10 years. Schema simplicity is a priority over distributed scaling."
- "Multi-user concurrency is low (not many users editing same report simultaneously), so pessimistic locking (row locks) is acceptable."

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

---

## Section 6 — 🏗️ High-Level Architecture (Minutes 10–25)

**What to do:** Draw the boxes (ASCII or whiteboard). Walk through the data flow *as if telling a story*. The interviewer is checking: "Does this person understand flow?"

**Note:** Type B questions often skip the HLD diagram and jump straight to API + schema. Adapt this section accordingly.

**Say this out loud (as you transition to API/schema):**
> "For a data model design question, the architecture is straightforward: web API → PostgreSQL. Let me focus on the API contract and the schema design..."

---

### Logical Architecture

```
[Web Client]
    ↓
[API Gateway / Web Server]
    ↓
[Expense Report Service]
    ↓
[PostgreSQL Database]
    ├── employees table
    ├── expense_reports table
    ├── expense_line_items table
    ├── approvals table
    ├── audit_log table
    └── categories table (optional)
```

**Data flow (say this out loud):**

1. **Create report:** Employee creates report (POST /reports). System creates record with state=DRAFT.
2. **Add expenses:** Employee adds line items (POST /reports/{id}/expenses). Each item stored as separate row.
3. **Submit:** Employee submits report (PATCH /reports/{id}, state=SUBMITTED). System validates expenses against limits.
4. **Manager approval:** Manager views report (GET /reports?filter=pending_approval). Approves or rejects (PATCH /reports/{id}/approve).
5. **Audit:** All state changes logged to audit_log (who, what, when, before, after).

---

## Section 7 — 🔬 Core Component Deep Dives (Minutes 25–40)

**What to do:** Pick 2–3 *riskiest* components. "Riskiest" = where the system breaks, or what's unique to this problem.

**Why these 3 for expense reports?**
1. **State machine — approval workflow** — Wrong design = approvals get stuck in invalid states; audit trail breaks.
2. **Business rule validation — expense limits** — Wrong design = employees can submit expenses above limits; policy is unenforced.
3. **Audit trail — immutable history** — Wrong design = audit trail is incomplete; compliance fails.

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
Map<String, List<String>> validTransitions = Map.of(
    "draft", List.of("submitted"),
    "submitted", List.of("manager_approved", "rejected"),
    "manager_approved", List.of("finance_approved", "rejected"),
    "finance_approved", List.of("reimbursed", "rejected"),
    "rejected", List.of()  // terminal state
);

public void transitionState(String reportId, String newState) {
    String currentState = getState(reportId);
    if (!validTransitions.get(currentState).contains(newState)) {
        throw new InvalidStateTransition(currentState + " → " + newState);
    }
    updateState(reportId, newState);
    logToAuditTrail(reportId, currentState, newState);
}
```

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
    limit_per_transaction DECIMAL(10, 2),
    limit_per_report DECIMAL(10, 2),
    PRIMARY KEY (employee_type, category)
);

-- Example data
INSERT INTO expense_limits VALUES ('employee', 'meals', 50.00, 500.00);
INSERT INTO expense_limits VALUES ('director', 'meals', 150.00, 2000.00);
INSERT INTO expense_limits VALUES ('employee', 'flights', 500.00, 5000.00);
```

**Validation pseudocode:**

```java
public void validateExpense(Expense expense, EmployeeType empType) {
    ExpenseLimit limit = limitsCache.get(empType, expense.category);
    
    if (expense.amount > limit.perTransaction) {
        throw new ValidationError("Expense exceeds per-transaction limit: $" + limit.perTransaction);
    }
    
    // Also check report-level limit
    List<Expense> reportExpenses = getExpensesByReport(expense.reportId);
    double reportTotal = reportExpenses.stream()
        .filter(e -> e.category.equals(expense.category))
        .mapToDouble(e -> e.amount)
        .sum() + expense.amount;
    
    if (reportTotal > limit.perReport) {
        throw new ValidationError("Report exceeds per-category limit: $" + limit.perReport);
    }
}
```

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

-- Example audit entry for approval
INSERT INTO audit_log VALUES (
    resource_type='expense_report',
    resource_id='report-123',
    action='approved',
    user_id='manager-456',
    before_value={"state": "submitted"},
    after_value={"state": "manager_approved"},
    reason='Approved. All expenses within policy.'
);
```

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

## Section 8 — 🌐 API Design

### Core Endpoints

| Method | Path | Auth | Request body | Response | Status codes |
|---|---|---|---|---|---|
| POST | `/v1/reports` | JWT (employee) | `{ "title": "...", "period": "2026-06" }` | `{ "id": "...", "state": "draft" }` | 201, 400 |
| GET | `/v1/reports` | JWT (any) | ?filter=draft/submitted/pending_approval | `[{ "id", "employee", "state", "total_amount" }]` | 200, 401 |
| PATCH | `/v1/reports/{id}` | JWT (owner or manager) | `{ "state": "submitted" }` | `{ "id", "state" }` | 200, 400, 409 |
| POST | `/v1/reports/{id}/expenses` | JWT (owner) | `{ "date", "category", "amount", "purpose" }` | `{ "id", "lineItemId" }` | 201, 400, 422 |
| GET | `/v1/reports/{id}/expenses` | JWT (owner or manager) | — | `[{ "id", "category", "amount", "receipt_url" }]` | 200, 404 |
| PATCH | `/v1/reports/{id}/approve` | JWT (manager) | `{ "decision": "approved"/"rejected", "reason": "..." }` | `{ "id", "state" }` | 200, 400, 403 |

### Key Design Decisions:
- **Verb semantics:** PATCH for state changes (approve, reject); POST for creation.
- **Filter parameter:** GET /reports?filter=pending_approval allows filtering by approval state. Cleaner than separate endpoints.
- **Role-based access:** Manager can only approve reports from their team. Enforced in API layer.
- **Idempotency:** POST /reports is idempotent if client sends idempotencyKey header. Prevents duplicate submissions on retry.
- **Error handling:** 409 Conflict if state transition is invalid; 422 Unprocessable Entity if business rule violated (e.g., expense over limit).

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
    total_amount    DECIMAL(10, 2) GENERATED AS (SELECT SUM(amount) FROM expense_line_items WHERE report_id = id),  -- computed column: DB calculates this value automatically from the expression; you never insert or update it manually — the DB derives it fresh on every read
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
    amount          DECIMAL(10, 2) NOT NULL,
    purpose         TEXT,
    location        VARCHAR(255),
    receipt_url     VARCHAR(512),
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
    limit_per_transaction DECIMAL(10, 2),
    limit_per_report DECIMAL(10, 2),
    PRIMARY KEY (employee_type, category)
);
```

### Key Schema Decisions:
- **approval_state enum:** Enforces valid states at DB level (CHECK constraint).
- **total_amount computed column:** SUM of line items. Denormalized for query efficiency; recomputed on each query.
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
- **Failure mode if wrong:** If we chose a full state machine table (separate workflow_state table), every query to get current state requires a JOIN. Schema is overly complex for this use case. At 10K employees, simplicity is more valuable than extensibility.

### Trade-off 2: Hardcoded Limits vs Rules Table

- **Chose:** Rules table (expense_limits) with app-level caching
- **Gain:** Business rules can change without code redeployment. Finance can adjust meal limits on the fly.
- **Lose:** DB lookup latency on every expense validation. Must implement cache invalidation (when rules change, invalidate cache).
- **Failure mode if wrong:** If we chose hardcoded limits in code (constant MEAL_LIMIT = 50), every rule change requires a code redeployment and app restart. Business can't be agile. Rules table is the right choice here.

### Trade-off 3: Audit Log Table vs Event Sourcing

- **Chose:** Audit log table (append-only) + soft deletes for current state
- **Gain:** Simple to query ("who approved this report?"). Current state is in the latest row; no need to replay events.
- **Lose:** Two sources of truth (current state in main tables + audit log). Audit log only captures that changes happened, not *how* to reconstruct state.
- **Failure mode if wrong:** If we chose full event sourcing (state = replay all events), every query would need to replay events from the beginning. At 1M reports/year, replaying becomes slow. Hybrid approach (current state + audit log) is a good balance.

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

## Section 13 — 🐞 Common Mistakes on This Question

**Note:** Reading these mistakes BEFORE the interview prevents you from making them under stress.

---

- **Mistake 1:** No audit trail or soft deletes → **Why wrong:** Compliance requires immutable history. Hard deletes lose forensic evidence. **Say instead:** "I'd add an audit_log table and use soft deletes (deleted_at column) so that all changes are auditable and recoverable."

- **Mistake 2:** Ignoring concurrency (no optimistic/pessimistic locking) → **Why wrong:** Multi-user edits lead to lost updates (two employees editing same expense, one write is lost). **Say instead:** "I'd add version_number for optimistic locking. On update, verify the version hasn't changed since the user read it."

- **Mistake 3:** State machine logic in database vs. app → **Why wrong:** If you hardcode all states in the database, you can't easily add new workflows (e.g., contractor workflow without manager approval). **Say instead:** "State is stored as an enum column; transitions are validated in the app. This allows flexibility without schema changes."

- **Mistake 4:** No separation of concerns (business logic mixed with SQL) → **Why wrong:** Hard to test, hard to change rules. **Say instead:** "Validation logic (ExpenseValidator, ApprovalService) is separate from schema. Each class has one responsibility (SOLID)."

- **Mistake 5:** Forgetting about cascading deletes and FK constraints → **Why wrong:** Deleting an employee leaves orphaned reports. Deleting a report leaves orphaned line items. **Say instead:** "FK constraints with ON DELETE CASCADE ensure data integrity. Deleting a report cascades to line items and approvals."

---

## Section 14 — 🧭 DocuSign Dimensions Checklist

| Dimension | Relevant? | How your design addresses it |
|---|---|---|
| Testability | ✅ | Business logic (validation, approval) is testable in isolation. Mock database; test state transitions independently. |
| Usability | ✅ | API is RESTful with clear resource hierarchy (/reports/{id}/expenses). State filtering (?filter=pending_approval) makes filtering easy. |
| Extensibility | ✅ | New workflows parameterized by employee_type. New categories = new row in expense_limits. New validation rules = new Strategy implementation. |
| Security | ✅ | Role-based access control (manager can only approve their team's reports). Audit trail captures who did what. |
| Availability | ✅ | Single PostgreSQL with replication. No distributed consensus needed. Downgrades gracefully if replicas lag. |
| Scalability | ✅ | Schema handles 10K+ employees, millions of reports. Indexes on key columns (employee_id, approval_state). No sharding needed at this scale. |
| Observability & Traceability | ✅ | Audit log captures all state changes with timestamps and user IDs. Can reconstruct workflow history of any report. |

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
