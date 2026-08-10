# B1 — Design a Subscription Billing API

> **Read `solution-notes-standards.md` first.** This file is a 60-minute interview answer framework, not a concept reference. Concept-level depth lives in `SystemDesignConcepts/`.

---

## 📚 Prerequisites — Study These First

Before reading the solution, make sure you can explain the bolded items below from memory.

| Concept | File in `SystemDesignConcepts/` | Why you need it for this question |
|---|---|---|
| **Idempotency** (core + advanced) | `Foundations/Concurrency-and-Consistency/04-idempotency.md` | Preventing double-charges on payment retry is the #1 correctness problem here — idempotency key pattern is mandatory, not optional |
| **Idempotency (advanced)** | `Foundations/Concurrency-and-Consistency/04-idempotency_advanced.md` | Distributed idempotency across the billing service and the payment gateway |
| **Outbox / CDC pattern** | `Foundations/Data-Fundamentals/07-cdc-outbox.md` | The outbox pattern is the safe way to atomically write the payment record AND publish the `payment_succeeded` event — this is the correct answer to "what if the server crashes after charging but before writing to the DB?" |
| **Saga pattern** | `Core-Architecture/Resilience-and-Fault-Tolerance/23-saga-pattern.md` | Distributed compensating transactions — know when to use Saga vs two-phase commit and what happens when a saga step fails mid-flow |
| **Two-phase commit vs Saga** | `Core-Architecture/Resilience-and-Fault-Tolerance/36-two-phase-commit-vs-saga.md` | Why 2PC is wrong for a billing system that spans a payment gateway (external service) |
| **Retry / exponential backoff** | `Core-Architecture/Resilience-and-Fault-Tolerance/35-retry-exponential-backoff-patterns.md` | Payment retry logic with jitter — the 7-day dunning schedule is an application of this pattern |
| **Multi-step processes** | `Patterns/DeepDive/05-multi-step-processes.md` | The billing flow (charge → entitle → notify → invoice) is a multi-step workflow — know how to model state transitions and recover from partial failure |

---

## 🎯 What Is This System?

**In plain English:** A subscription billing system charges customers on a recurring schedule (monthly or yearly), handles mid-cycle plan changes with prorated credits, retries failed payments with exponential backoff, and fans out billing events to downstream services — entitlement, email, and analytics.

**Real-world examples:**

| System / Company | What they built |
|---|---|
| **Stripe Billing** | Infrastructure powering millions of subscription businesses worldwide |
| **Recurly** | SaaS-first subscription platform with dunning management |
| **Chargebee** | Billing + revenue recognition for B2B SaaS |
| **Zuora** | Enterprise subscription management (used by Salesforce, Box) |
| **Paddle** | Billing + merchant-of-record for software companies |
| **DocuSign eSignature plans** | The product you're helping build — monthly/annual tiers with seat limits |

**Core user journey:** User subscribes to a $29/month plan → credit card charged immediately → access granted → system auto-charges on the same date each month → if the card declines, retries over 7 days before suspending access.

**Why it's hard to build at scale:** If a payment succeeds but the server crashes before writing to the DB, the retry will double-charge the customer — real money is lost. Idempotency, atomic DB + event writes (outbox pattern), and exactly-once downstream delivery are correctness requirements, not nice-to-haves.

---

## 🧠 How to Use This File

**This file is an instantiation of the solution-notes-standards.md framework.** Every section below maps to one phase of the 60-minute delivery rhythm. The sections are ordered exactly as you would deliver them live.

**Before your interview:**
1. Read `solution-notes-standards.md` once to understand the format (15 min)
2. Memorize the 6 Memory Anchors below (2 min)
3. Read Sections 2, 7, 10, 11, 12, 13 in full — these are the high-leverage sections (25 min)
4. The morning of: re-read Section 15 (TL;DR) only

**The time budget (Type B delivery order):**
- Minutes 0–5: Sections 1–2 (Opener + Clarifying questions)
- Minutes 5–8: Section 3 (Requirements — state FR/NFR out loud)
- Minutes 8–13: **Section 8** (API Design — name the contract BEFORE drawing the system; this is the primary deliverable for Type B)
- Minutes 13–18: Section 4 (Scale estimation — use these numbers to justify HLD choices)
- Minutes 18–20: Section 5 (Requirements variation — keep brief; the table is for follow-up readiness, not recitation)
- Minutes 20–38: Section 6 (HLD — 3-stage progression; weave in Section 9 data model during Stage 3 deep dive)
- Minutes 38–48: Section 7 (Deep dives: Dive 1 + Dive 2 are mandatory; Dive 3 only if ahead of schedule)
- Minutes 48–53: Section 10 (Trade-offs: exactly 3, with failure modes)
- Minutes 53–57: Section 11 (DocuSign-specific depth)
- Minutes 57–60: Section 12 (Interviewer probes — buffer)

**Stay on this schedule.** If you're at minute 42 and still deep-diving — stop. Pivot to trade-offs. The rubric values trade-off thinking over implementation depth.

---

## 💾 Memory Anchors (Memorize These 6)

Before every interview, say these 6 sentences to yourself (takes 30 seconds):

1. **"Idempotency first."** — Billing is the one domain where a duplicate write costs real money. Name this in the first 10 minutes.
2. **"State machine owns the lifecycle."** — Every subscription transition is a state-machine event. Never let status be a free-text field.
3. **"Outbox = atomic publish."** — The dual-write problem (write DB + write Kafka) is solved by writing both in one transaction. Name this explicitly.
4. **"Proration is a business rule, not a system design rule."** — Know the formula but don't get pulled into it for 20 minutes. One sentence is enough.
5. **"Downstream consumers, not downstream calls."** — After payment succeeds, notify entitlement/email/analytics via Kafka, not synchronous HTTP.
6. **"SOLID at the API layer."** — DocuSign's PDF asks for this. Be ready to name SRP, OCP, DIP with concrete examples from billing.

---

## Section 0 — Question Identity Card

| | |
|---|---|
| **Question** | Design a Subscription Billing API / Design a service or product API (billing variant) |
| **Interview Type** | Type B — Product Architecture / API Design |
| **Confirmed or Likely** | ⭐ Confirmed asked (DocuSign official prep guide PDF p.3 — "Design a service or product API" with billing/subscription listed as primary example) |
| **Concept notes prerequisite** | `SystemDesignConcepts/Production-Grade/System-Design-Patterns/49-state-machines-workflows.md`, `SystemDesignConcepts/Foundations/Data-Fundamentals/11-api-design.md`, `SystemDesignConcepts/Foundations/Data-Fundamentals/07-cdc-outbox.md`, `SystemDesignConcepts/Foundations/Data-Fundamentals/12-data-modeling.md` |
| **DocuSign-specific angle** | DocuSign's Commerce Backend team owns the billing lifecycle for eSign subscription plans (Individual → Business Pro → Business Premium → Enterprise). This question tests whether you can design a billing API that is simultaneously SOLID-principled, idempotent under payment retries, and extensible to new plan types without changing core billing logic. |
| **Time budget** | 5 min clarify → 5 min estimate → 15 min HLD → 15 min deep dive → 8 min trade-offs → 7 min Q&A buffer |

---

## Section 1 — 🚀 The One-Sentence Opener

> "Before I draw anything, let me ask a few clarifying questions — specifically about the billing model (flat-rate vs usage-based), whether this is multi-tenant, how we handle failed payments, and what downstream systems need to be notified on subscription events — because each of those creates a different architecture."

Then immediately go to Section 2. Do NOT start drawing.

---

## Section 2 — 🔍 Clarifying Questions Script (Minutes 0–5)

**Say this out loud after your opener:**
> "I have a few clarifying questions to make sure I build the right thing..."

---

**Q: "Is this flat-rate subscription (monthly/annual fixed price) or usage-based billing (per API call, per document signed)?"**
- Why ask: flat-rate drives a simple state machine; usage-based requires a metering pipeline (event aggregation, time-window sums, threshold alerts). These are fundamentally different architectures.
- Flat-rate → state machine + scheduled renewal job. Core of today's design.
- Usage-based → event ingestion pipeline + billing run aggregation + invoice generation. Out of scope unless asked to extend.

**Q: "Does a subscription belong to an individual user or to an organization (tenant)?"**
- Why ask: individual billing = simple user_id FK; organization billing = tenant isolation, multiple seats per org, admin-level billing portal. DocuSign is B2B SaaS — the answer is almost certainly organization-level.
- Individual → one subscription per user, simple FK.
- Organization → subscriptions belong to an `organization_id` tenant; per-seat counts; admin user manages billing on behalf of the org.

**Q: "What happens when a payment fails — do we retry immediately, enter a grace period, or suspend immediately?"**
- Why ask: this is the most critical business rule. It determines whether the state machine has a `PAST_DUE` state with retry logic or a simpler `ACTIVE → CANCELLED` transition.
- Grace period → PAST_DUE state, dunning (the process of making repeated payment attempts after initial failure) schedule, retry with exponential backoff.
- No grace → ACTIVE → CANCELLED on first failure. Simpler state machine.

**Q: "What downstream systems need to know about subscription events — entitlement service, notification service, analytics?"**
- Why ask: if the answer is "just update a flag," sync is fine. If multiple services need to react, synchronous calls create tight coupling and cascading failures — this forces the outbox + Kafka decision.
- One downstream → can call synchronously.
- Multiple downstream → Kafka fanout with outbox pattern. This is where the design gets senior-level.

**Q: "Is plan upgrade/downgrade in scope? If so, how is proration handled?"**
- Why ask: proration (the partial-period credit when changing plans mid-billing-cycle) is a billing-specific complexity. If in scope, the data model needs `current_period_start` and `current_period_end`. If out of scope, simplify.
- In scope → UPGRADED/DOWNGRADED state transitions, proration credit on next invoice.
- Out of scope → design stays simpler; note it as a future extension.

**Q: "What consistency guarantee do downstream consumers need — is it OK if entitlement is granted 500ms after payment, or must it be synchronous?"**
- Why ask: strict synchronous consistency forces synchronous HTTP calls (tight coupling, cascading failures); eventual consistency (acceptable 500ms lag) enables the Kafka outbox pattern (resilient, decoupled).
- "Must be instantaneous" → synchronous entitlement call, accept the coupling.
- "500ms lag OK" → outbox + Kafka → recommend this and justify why.

---

## Section 3 — 📋 Requirements

### Functional Requirements (what the system does)

- Organizations can subscribe to a plan (Individual, Business Pro, Business Premium, Enterprise)
- Subscriptions renew automatically at the end of each billing cycle (monthly or annual)
- Users can upgrade or downgrade their plan; proration applied on next invoice
- Failed payments enter a grace period (`PAST_DUE`) with up to 4 retry attempts over 7 days before cancellation
- Organizations can cancel a subscription; access continues until end of current period
- Downstream systems (entitlement, notification, analytics) are notified on every subscription lifecycle event
- Payment API must be idempotent — retrying a failed payment never double-charges

**Out of scope today:**
- Usage-based / metered billing (per-document, per-API-call)
- Invoice PDF generation
- Multi-currency pricing
- Tax calculation (Avalara integration)

### Non-Functional Requirements

- **Scale:** ~1.6M paying organizations; ~53K renewals/day steady state (0.6 writes/sec), ~100K on the 1st-of-month peak day, jitter-spread into a 60-minute window = ~28 charges/sec peak; read-heavy entitlement checks at ~1,850 req/sec average and ~5,500 req/sec peak (these are the Section 4 numbers — state them consistently)
- **Latency:** POST /payments P99 < 2s (payment gateway round-trip dominates); GET /subscriptions P99 < 50ms
- **Availability:** 99.9% SLO — billing must be highly available; a billing outage means no new sign-ups and no renewals
- **Consistency:** Eventual consistency acceptable — entitlement granted within 500ms of payment success (Kafka consumer lag)
- **Durability:** Payment records must be durable (no data loss); at-least-once delivery for downstream events with idempotent consumers
- **Compliance:** SOC 2 Type II — every billing event must have an immutable audit trail with timestamp, actor, and before/after state

---

## Section 8 — 🌐 API Design (Minutes 8–13) ⭐ Type B Primary Deliverable

> **Why here, not later:** For Type B (Product Architecture), the API contract is the thesis of your answer. Define it right after requirements — before scale estimation, before the HLD. The architecture exists to fulfill the contract, not the other way around. An interviewer who sees you draw boxes before defining what the system exposes will wonder whether you're designing or guessing.

### 🧠 How to Derive These Endpoints (Reconstruct, Don't Recall)

Every endpoint starts from a functional requirement. The move is: **FR → operation → resource → HTTP method → contract.**

Read your Section 3 FR list. Each "organizations can X" or "system must Y" is telling you an operation exists. Work through a few:

**"Organizations can subscribe to a plan"** → CREATE operation → resource is `subscription` → `POST /v1/subscriptions`. Who calls it? An org admin, so JWT Bearer with admin role. What's the minimum they send? The three things needed: plan_id, tenant_id, billing_cycle. What do they get back? Proof it was created and what happens next: subscription_id + next_renewal_date.

**"Payment API must be idempotent"** → The FR itself names the constraint. This tells you: `Idempotency-Key` header is mandatory on `POST /v1/payments`, and status codes must include `402` (gateway declined — a valid non-error outcome, not a 500). The requirement shaped the contract, not just the route.

**"Organizations can cancel; access continues until end of current period"** → state change on an existing resource → `DELETE /v1/subscriptions/{id}`. But the second clause — "access continues" — tells you the response body needs `access_until`, not just a `204 No Content`. Read every word of the FR; each clause tells you something about the response.

**Validation check (say this out loud):** After deriving your endpoints, map each one back to a FR. Any endpoint with no matching FR probably shouldn't exist. Any FR with no matching endpoint is a gap — fill it.

---

### Core Endpoints

| Method | Path | Auth | Request Body | Response | Status Codes |
|---|---|---|---|---|---|
| POST | /v1/subscriptions | JWT Bearer (admin role) | `{plan_id, tenant_id, billing_cycle}` | `{subscription_id, status, next_renewal_date}` | 201, 400, 409 |
| GET | /v1/subscriptions/{id} | JWT Bearer | — | `{id, status, plan, tenant_id, current_period_end}` | 200, 404 |
| PUT | /v1/subscriptions/{id}/plan | JWT Bearer (admin) | `{new_plan_id}` | `{subscription_id, old_plan, new_plan, proration_credit_cents}` | 200, 400, 409 |
| DELETE | /v1/subscriptions/{id} | JWT Bearer (admin) | — | `{subscription_id, status: "CANCELLED", access_until}` | 200, 404 |
| POST | /v1/payments | JWT Bearer + `Idempotency-Key` header | `{subscription_id, amount_cents, currency_code}` | `{payment_id, charge_id, status}` | 201, 200, 400, 402, 409 |
| GET | /v1/subscriptions/{id}/payments | JWT Bearer | — | `[{payment_id, amount, status, created_at}]` (paginated) | 200, 404 |
| POST | /v1/webhooks/stripe | `Stripe-Signature` HMAC (no JWT — Stripe is not a logged-in user) | Stripe Event object | `{received: true}` | 200, 400 |

---

### 🔍 Endpoint Stories — Why Each One Exists

**`POST /v1/subscriptions`** — Entry point for a new customer. The `409` matters: if an org already has an active subscription, a second POST must conflict, not silently create a duplicate — this is the `idx_subscriptions_org` partial unique index surfacing as an HTTP code. Without it you'd have two active subscriptions for one org with no way to enforce the one-per-org rule. The `400` fires on exactly two conditions: `plan_id` does not exist in `subscription_plans` (or has `is_active = false`), or `billing_cycle` is not one of `MONTHLY`/`ANNUAL`.

**`GET /v1/subscriptions/{id}`** — This is the entitlement check endpoint. It's called 5,500 times/sec at peak — every DocuSign API request verifies the org has an active plan. Don't let the simplicity fool you. The interviewer will ask "how does this scale?" Answer: Redis cache, TTL, not a DB read. This is the highest-traffic endpoint in the system.

**`PUT /v1/subscriptions/{id}/plan`** — Plan upgrade/downgrade. The response returns `proration_credit_cents` so the UI can show the customer exactly what they'll pay next cycle before they confirm. If you return only a bare `200`, the customer has no visibility into the financial impact — bad UX and a likely follow-up probe. The `409` has two named triggers: the subscription is not in `ACTIVE` state (you cannot change the plan of a `CANCELLED` or `PAST_DUE` subscription), or a concurrent plan change already bumped the row version — two org admins clicking Upgrade at the same time. The `400` fires when `new_plan_id` equals the current `plan_id` (a no-op change is a client bug, not a success).

**`DELETE /v1/subscriptions/{id}`** — Cancel. Returns `access_until` because the subscription isn't destroyed immediately — the org retains access until period end. The interviewer will ask "why not `204`?" Because the caller needs to know when access ends to update the UI and communicate to the customer. The `404` trigger is specifically a subscription_id that does not belong to the caller's tenant — return `404`, not `403`, so you don't leak the existence of another tenant's subscription ID.

**`POST /v1/payments`** — The most critical endpoint. Charges Stripe. The `Idempotency-Key` header is the entire reason this is safe to retry. Four codes, four distinct triggers, and naming them precisely is the point:
- `201` — first successful charge; a new `payments` row was created.
- `200` — idempotent replay: same `Idempotency-Key`, same body, cached response returned, **no** second Stripe call. Distinguishing 201 from 200 lets the client tell "I created this" from "this already existed."
- `402` (Payment Required) — Stripe declined the card (`insufficient_funds`, `card_expired`). This is a *valid business outcome*, not a system error: distinct from `400` and `500`. Most candidates collapse card declines into a 400 and lose a point.
- `400` — the request is malformed: `amount_cents` is absent/non-integer/negative, `currency_code` isn't a valid ISO-4217 code, or the `Idempotency-Key` header is missing entirely.
- `409` — the same `Idempotency-Key` was replayed with a **different** request body (e.g., a different `amount_cents`). That is a client bug, and silently returning the cached response would hide a real defect. Stripe itself behaves this way.

**`GET /v1/subscriptions/{id}/payments`** — Payment history, paginated. Cursor-based, not offset — an org could have years of payment records and `OFFSET 10000` scans 10K rows on every page. The nested path `/subscriptions/{id}/payments` makes ownership clear: these payments belong to this subscription. `404` trigger: same tenant-scoping rule as DELETE.

**`POST /v1/webhooks/stripe`** — The endpoint most candidates forget, and the one a Commerce Backend interviewer will absolutely ask about. Stripe is the *source of truth for money*, and it tells you about money asynchronously: a 3-D Secure authentication completes minutes later, a bank reverses an ACH debit days later, a dispute opens weeks later. None of those events come back on your original `POST /v1/payments` response. Three non-obvious properties: (1) auth is HMAC signature verification over the raw request body — so this route must read the **raw bytes**, not a parsed-and-re-serialized JSON body, or the signature never matches; (2) `400` has exactly one trigger — signature verification failed or the timestamp is outside the 5-minute tolerance window (replay attack); (3) you return `200` **before** doing any work — persist the raw event and process it asynchronously, because Stripe retries any non-2xx for up to 3 days and a slow handler turns into a retry storm. See Deep Dive 3.

---

### Key Design Decisions

**Idempotency:** `Idempotency-Key` header on POST /v1/payments (UUID v4, client-generated). Redis stores `idempotency_key → response_body` with 24h TTL. Second call with same key returns same 200 — no second charge.

**Versioning:** `/v1/` in path (URL versioning). Chosen over `Accept-Version` header because it is visible in logs, easier to route in API Gateway, and explicit in client URLs. Trade-off: version proliferation requires eventually routing v1 → v2 migration.

**Error format (standard across all endpoints):**
```json
{
  "error": {
    "code": "PAYMENT_DECLINED",
    "message": "Payment was declined by the payment processor.",
    "details": {
      "decline_code": "insufficient_funds",
      "subscription_id": "sub_abc123"
    },
    "request_id": "req_xyz789"
  }
}
```

**Proration — upgrade and downgrade are NOT symmetric.** This is the single most probed piece of billing math, so know both directions and the rounding rule.

```
Let  r = days_remaining / days_in_cycle          (the unused fraction of the paid period)
     unused_credit  = round(old_plan_cents × r)  (what the customer already paid for and won't use)
     new_period_fee = round(new_plan_cents × r)  (cost of the new plan for the rest of the period)

UPGRADE (new_plan > old_plan) — takes effect IMMEDIATELY:
     delta_due_now = new_period_fee - unused_credit
     Next invoice = full new_plan_cents.

DOWNGRADE (new_plan < old_plan) — takes effect at PERIOD END:
     Nothing is charged or refunded now. `pending_plan_id` is set; the renewal
     job swaps the plan at current_period_end. delta = 0 for the current period.
```

**Worked example — upgrade, Business Pro ($25/mo) → Business Premium ($50/mo), day 15 of a 30-day cycle:**

```
r              = 15 / 30                     = 0.5
unused_credit  = round(2500 × 0.5)           = 1250 cents  ($12.50)
new_period_fee = round(5000 × 0.5)           = 2500 cents  ($25.00)
delta_due_now  = 2500 - 1250                = 1250 cents  ($12.50)

Sanity check: customer pays 2500 (already) + 1250 (now) = 3750 cents for the
period = 15 days of Pro + 15 days of Premium. The math balances.
Next invoice on day 31 = 5000 cents, full price.
```

**Why downgrades wait for period end:** an immediate downgrade would owe the customer `unused_credit - new_period_fee` — a *negative* invoice. You cannot invoice a negative amount, and refunding to the card reopens PCI/chargeback surface for a customer who is still active. Deferring to `current_period_end` makes the delta exactly zero. This is also Stripe's default (`proration_behavior: none` on downgrade).

**Rounding and currency correctness (say this out loud — it is a free senior signal):**
- Money is **integer minor units** (`amount_cents INT`) everywhere — never `float`/`double`. `0.1 + 0.2 != 0.3` in IEEE-754, and a half-cent error across 1.6M subscriptions is a real reconciliation break.
- Round **once, at the end**, half-up, and always in the invoice's currency. Never round intermediate factors like `r`.
- Currencies have different exponents: USD/EUR = 2 decimals, JPY/KRW = 0, KWD/BHD = 3. Store the exponent on the currency, not in code, or a ¥1,000 plan silently becomes ¥10.00.
- The residual cent from a proration split is assigned to the **invoice line item**, not spread — so the sum of line items always equals the invoice total exactly.

**Pagination on /payments:** Cursor-based (see C3 solution). Cursor encodes `(created_at, payment_id)`. Avoids OFFSET performance degradation on large payment histories.

> 📖 Full: `SystemDesignConcepts/Foundations/Data-Fundamentals/11-api-design.md`

---

## Section 4 — 🔢 Scale Estimation (Minutes 13–18)

```
Assumptions:
  - 1.6M paying organizations globally
  - Average billing cycle: monthly (12 renewals/year per subscription)
  - Renewals peak: first 3 days of month (DocuSign billing cycles cluster on the 1st)

Traffic — write path:
  - Renewals/day (steady state): 1.6M ÷ 30 = ~53K/day → 53,000 ÷ 86,400 = ~0.6/sec
  - Peak day: the 1st of the month is the most popular billing anchor date — assume ~2× the
      uniform average renews that day = ~100K renewals
  - The renewal scheduler jitters those 100K charges across a 60-minute window (Deep Dive 4):
      100,000 ÷ 3,600 = ~28 charges/sec for that hour  ← this is the design peak
  - New subscription creates: assume 5K/day = 0.06/sec (negligible)
  - Payment API DB writes: ~0.6/sec steady, ~28/sec peak; each charge writes 2 rows
      (payments + outbox) plus 1 subscription UPDATE = ~85 row-writes/sec peak

Traffic — read path:
  - Entitlement checks (every API call to DocuSign checks subscription status): 1.6M orgs × 100 API calls/day = 160M reads/day = 1,850/sec
  - Peak (business hours, 3× base): ~5,500 req/sec
  - Caching is mandatory at this read volume — the DB cannot handle 5,500 req/sec raw

Storage:
  - Subscription row: ~500 bytes × 1.6M = 800 MB (trivially fits in Postgres)
  - Payment history: 1.6M × 12 payments/year × 500 bytes = 9.6 GB/year (indexed by subscription_id, no problem)
  - Outbox events: short-lived; processed and deleted within seconds; <100 MB at any time
  - Audit log: 1.6M × 12 events/year × 2 KB = 38 GB/year — archive to S3 after 90 days

Bandwidth:
  - Inbound (payment requests): 28 req/sec × 2 KB = 56 KB/sec — negligible
  - Outbound (entitlement reads): 5,500 req/sec × 1 KB = 5.5 MB/sec — handled by cache hit, not DB

Key conclusions:
  - "At ~28 charges/sec peak (~85 row-writes/sec), a single Postgres primary — good for roughly
     5,000 writes/sec — is at under 2% of write capacity. No sharding needed."
  - "At 5,500 entitlement reads/sec, Redis cache is mandatory. DB cannot serve this volume raw."
  - "Outbox table stays tiny — processed rows deleted, negligible storage impact."
  - "Storage is not a concern at DocuSign's scale — the complexity is in correctness, not capacity."
```

---

## Section 5 — 🔄 Requirements Variation Table ⭐ KEY SECTION

| If the interviewer says... | Your architecture changes to... | The reasoning |
|---|---|---|
| "10K total customers" | Single-service monolith, sync downstream calls, no Kafka | Small scale doesn't justify async complexity; sync is simpler to reason about and debug |
| "100M customers" | Sharded Postgres by `tenant_id`, tiered Redis cache (L1 in-process + L2 Redis cluster), Kafka partitioned by `tenant_id` | Write volume exceeds single Postgres primary; read volume requires hierarchical caching |
| "Usage-based billing (per document signed)" | Add metering pipeline: event ingestion (Kafka) → aggregation (Flink/Spark) → billing run job → invoice generation | Metering requires aggregation over a time window before you can compute a bill — fundamentally different from flat-rate |
| "Strong consistency required — entitlement must be granted before payment returns 200" | Synchronous HTTP call from payment service to entitlement service; accept tight coupling and timeout risk | Can no longer use Kafka fanout; must call entitlement inline and roll back payment if entitlement call fails |
| "Multi-currency (USD, EUR, GBP)" | Add `currency` column to `subscription_plans`; store `amount_cents + currency_code` together; never convert at write time | Storing only cents without currency creates silent bugs; conversion must happen at display time with exchange rate snapshot |
| "SOC 2 Type II compliance required" | Immutable append-only `subscription_events` audit table; every state transition writes an event row; no UPDATE on event rows | Mutable status columns can be overwritten silently; regulators need an immutable, timestamped trail of every status change |
| "Self-service plan downgrade must take effect at period end, not immediately" | Add `pending_plan_id` column; renewal job applies pending plan at `current_period_end` | Immediate downgrade would require proration credit calculation; period-end downgrade is simpler and is Stripe's default behavior |
| "Payment processor is Stripe today but must be swappable" | `IPaymentProcessor` interface with `charge(amount, currency, customerId)` and `refund(chargeId)`; `StripePaymentProcessor` and `BraintreePaymentProcessor` as implementations | DIP: billing service depends on abstraction, not Stripe SDK; swap processor by injecting different bean |

---

## Section 6 — 🏗️ High-Level Architecture (Minutes 8–20)

### 🎨 Visual — Subscription Billing Evolution (3-Stage Progression)

Start with Stage 1. Explain what breaks. Evolve to Stage 2. Explain what breaks. Evolve to Stage 3. This shows architectural thinking, not just the final answer.

---

```
══════════════════════════════════════════════════════════════════════
STAGE 1 — Sync REST, No Idempotency  (MVP, single-service)
══════════════════════════════════════════════════════════════════════

  [Client]
     │
     ▼
  [API Gateway]
     │
     ▼
  [BillingService]
     │  1. Charge payment gateway (Stripe)
     │  2. UPDATE subscriptions SET status='ACTIVE'
     │  3. POST /entitlement-service/grant          ← sync HTTP
     │  4. POST /notification-service/send           ← sync HTTP
     ▼
  [Postgres]


  BREAKING POINT 1: Stage 1 breaks at ~28 charges/sec (the 1st-of-month peak)
    the moment the Stripe round-trip exceeds the client's socket timeout.
    Exhausted resource: the client's HTTP read timeout, not a server resource —
    Stripe P99 is ~1.8s and clients typically time out at 2s. Assume a
    conservative 0.5% timeout rate on 100K peak-day charges → ~500 client
    retries → 500 duplicate Stripe charges in one day.
    Observable symptom: 500 rows in `payments` with the same subscription_id and
    two distinct stripe_charge_id values within 5 minutes; Stripe dashboard shows
    a duplicate-charge dispute rate spike. No idempotency key = real money lost.
    Why Stage 2 is needed: the retry must be made safe, not made rarer.

  BREAKING POINT 2: Entitlement service is down at step 3.
    Exhausted resource: none — this is a correctness failure, not a capacity one.
    At 28 charges/sec, a 60-second entitlement outage strands ~1,700 paid-but-
    unentitled organizations with no compensating action available (the Stripe
    charge cannot be rolled back by a DB rollback).
    Observable symptom: `subscriptions.status='ACTIVE'` while the entitlement
    service returns 403 for the same tenant; support ticket volume spikes.

  BREAKING POINT 3: Adding a fourth downstream (analytics) requires
    touching BillingService code → OCP (Open/Closed Principle) violated.
    Observable symptom: payment API P99 = sum of all downstream latencies —
    at 4 downstreams averaging 80ms each, P99 grows 320ms per added consumer.

══════════════════════════════════════════════════════════════════════
STAGE 2 — Idempotency Key + State Machine + Sync Downstream
══════════════════════════════════════════════════════════════════════

  [Client] ──── Idempotency-Key: <uuid> ────►
     │
     ▼
  [API Gateway]
     │
     ▼
  [BillingService]
     │  0. Check Redis: idempotency_key → if hit, return cached response
     │  1. Charge Stripe
     │  2. BEGIN transaction
     │     UPDATE subscriptions SET status=state_machine.next(event)
     │  3. COMMIT
     │  4. Store result in Redis (TTL 24h)
     │  5. POST /entitlement-service/grant          ← still sync HTTP
     │  6. POST /notification-service/send           ← still sync HTTP
     ▼
  [Postgres]    [Redis]

  State machine transitions:
    PENDING ──[payment_succeeded]──► ACTIVE
    ACTIVE  ──[payment_succeeded]──► ACTIVE    ← renewal self-transition.
                                                 Forgetting this row makes EVERY
                                                 monthly renewal throw.
    ACTIVE  ──[payment_failed]────► PAST_DUE
    PAST_DUE──[payment_failed]────► PAST_DUE   ← retry #2..#4 also fail
    PAST_DUE──[payment_succeeded]──► ACTIVE
    PAST_DUE──[retries_exhausted]──► CANCELLED
    ACTIVE  ──[cancelled]──────────► CANCELLED
    ACTIVE  ──[plan_changed]───────► ACTIVE (new plan_id)

  > 📖 Full: `SystemDesignConcepts/Production-Grade/System-Design-Patterns/49-state-machines-workflows.md`

  FIXED IN STAGE 2: Idempotency is now safe — duplicate retries
    hit Redis and return the same 200. State stays correct.

  BREAKING POINT 2: Entitlement service 503 at step 5:
    Payment succeeded (Stripe charged), DB committed, but HTTP call
    throws → BillingService returns 500 to client. Client retries.
    Redis idempotency key returns the cached 200, but entitlement
    was never granted. Silent inconsistency.
    Quantified: at step 5, entitlement service returns 503 → payment
    committed (Stripe charged customer's card) but DocuSign access never
    granted. Observable: subscription status='ACTIVE' in DB but entitlement
    service has no record. No compensation logic = support escalation.
    Stage 3 needed because sync HTTP call to entitlement cannot be retried
    safely without idempotency at the entitlement layer — outbox + Kafka
    provides at-least-once delivery with consumer-side deduplication.

  BREAKING POINT 3: Notification service GC pause (5s) at step 6:
    In-process GC pause → notification service HTTP call hangs 5s →
    BillingService payment API P99 spikes to 5s → customer checkout
    spinner spins while card is already charged.
    Observable: payment API P99 = 5,000ms; Stripe webhook confirms charge
    but client times out and shows "payment failed" error. Customer sees
    double-charge risk and files dispute. Stage 3 needed because a
    non-critical downstream (notifications) must not be on the critical
    path of the payment API — async Kafka fanout decouples them.

══════════════════════════════════════════════════════════════════════
STAGE 3 — Outbox Pattern + Kafka Fanout  (Production design)
══════════════════════════════════════════════════════════════════════

  [Client] ──── Idempotency-Key: <uuid> ────►
     │
     ▼
  [API Gateway]
     │
     ▼
  [BillingService]
     │  0. Check Redis: idempotency_key → if hit, return cached response
     │  1. Charge Stripe
     │  2. BEGIN transaction ◄─── single atomic unit
     │     UPDATE subscriptions SET status=state_machine.next(event)
     │     INSERT INTO outbox (event_type, payload, aggregate_id)
     │       VALUES ('SUBSCRIPTION_ACTIVATED', {...}, subscription_id)
     │  3. COMMIT  ◄── DB + outbox written atomically; nothing can split them
     │  4. Store result in Redis (TTL 24h)
     │  5. Return 200 to client
     ▼
  [Postgres] ◄── outbox table lives here

  [OutboxProcessor] (background thread, polls outbox every 100ms)
     │  SELECT * FROM outbox WHERE processed_at IS NULL LIMIT 100
     │  FOR EACH event:
     │    publish to Kafka topic: subscription-events
     │    UPDATE outbox SET processed_at=NOW()
     ▼
  [Kafka: subscription-events]
     ├──► [EntitlementConsumer]   grants/revokes feature access
     ├──► [NotificationConsumer]  sends email/SMS to org admin
     └──► [AnalyticsConsumer]     updates revenue metrics (BigQuery)

  > 📖 Full: `SystemDesignConcepts/Foundations/Data-Fundamentals/07-cdc-outbox.md`

  KEY INVARIANTS:
    1. Payment gateway charge and outbox write are in one transaction:
       either BOTH succeed or NEITHER does. No split-brain possible.
    2. Downstream services are decoupled: entitlement slowness does
       NOT affect payment API P99. Each consumer retries independently.
    3. Outbox processor is at-least-once: Kafka consumers MUST be
       idempotent (check subscription_id + event_id before acting).
```

> 📖 Full: `SystemDesignConcepts/Foundations/Data-Fundamentals/11-api-design.md`

---

### Decision Table 1 — Payment Idempotency Mechanism

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| No idempotency | Zero implementation effort | Double-charge on any network retry | ❌ Unacceptable for billing |
| DB unique constraint on `idempotency_key` | Durable, survives service restart | DB round-trip on every payment request | ⚠️ Works but slower than Redis |
| Redis + 24h TTL | Sub-millisecond lookup; TTL auto-expires old keys | If Redis is down, all payments fail (Redis becomes SPOF) | ✅ Choose this — use Redis Sentinel or cluster for HA |

> 📖 Full: `SystemDesignConcepts/Foundations/Data-Fundamentals/11-api-design.md` (idempotency section)

---

### Decision Table 2 — Downstream Propagation After Payment

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| Sync HTTP call from BillingService | Immediate consistency | Entitlement/notification failure fails payment; tight coupling; adds latency | ❌ Stage 1 failure mode — don't use for multiple downstreams |
| Kafka direct publish (no outbox) | Decoupled; fast | Kafka publish and DB commit are separate operations — if Kafka publish fails, DB is committed but event is lost | ❌ Dual-write problem — never acceptable for financial events |
| Outbox pattern + Kafka | Atomic: DB + outbox in one transaction; Kafka publish is best-effort retry; no event loss | Slight delay (outbox poll interval 100ms); consumers must be idempotent | ✅ Choose this — only pattern that is both decoupled AND consistent |

> 📖 Full: `SystemDesignConcepts/Foundations/Data-Fundamentals/07-cdc-outbox.md`

---

### Decision Table 3 — Subscription State Storage

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| Single `status` VARCHAR column | Simple; easy to query | Mutable; no history; violates SOC 2 audit requirement; can be overwritten silently | ❌ Fails compliance requirement |
| Event sourcing (no current state table) | Perfect audit trail; replay any point in time | Complex to query current state; high read latency for entitlement checks | ⚠️ Overkill for billing; only use if financial regulators require full replay |
| State machine table + append-only events table | Current state queryable instantly (O(1)); full audit trail in events table; state machine enforces valid transitions | Two tables to maintain; slightly more write overhead | ✅ Choose this — best of both worlds |

> 📖 Full: `SystemDesignConcepts/Production-Grade/System-Design-Patterns/49-state-machines-workflows.md`

---

### Data Flow Walkthrough (Stage 3 — say this out loud)

1. **Client sends POST /v1/payments with `Idempotency-Key: uuid-123`**
2. **BillingService checks Redis:** key `uuid-123` → miss (first request) → proceed
3. **BillingService charges Stripe** — Stripe returns `charge_id: ch_abc123`
4. **BillingService opens DB transaction:** (a) updates `subscriptions` row via state machine, (b) inserts into `outbox` with event payload — both committed atomically
5. **BillingService caches result in Redis** (`uuid-123` → `{status: 200, response: {...}}`, TTL 24h) and returns 200 to client
6. **OutboxProcessor** polls every 100ms, finds the new row, publishes to `subscription-events` Kafka topic, marks the outbox row as `processed_at = NOW()`
7. **EntitlementConsumer** receives event, grants feature access for the org, marks its own consumed offset committed
8. **NotificationConsumer** receives event, sends billing receipt email to org admin
9. **If client retries with same `Idempotency-Key: uuid-123`:** Redis hits → returns cached 200 → no second Stripe charge, no second DB write

---

## Section 7 — 🔬 Core Component Deep Dives (Minutes 20–38)

Pick Dives 1–3 — they are the riskiest components and the ones DocuSign interviewers push on most. Dive 1 makes the *synchronous* money path safe; Dive 3 makes the *asynchronous* money path safe. Skipping Dive 3 is the most common way to look like you've only ever read about Stripe rather than integrated it.

---

### Deep Dive 1: Idempotency + Payment Safety

**Why this is the most critical component:**
Billing is the one domain where a bug costs real money. A double-charge creates a support escalation, a refund, and potential churn. Idempotency is the minimum viable safety net.

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| DB unique constraint on `(idempotency_key, tenant_id)` | Survives service restart; durable | DB round-trip on hot path; contention under peak |
| Redis key with 24h TTL | Sub-millisecond; horizontally scalable | Redis failure = all payments fail; requires Redis HA |
| Application-level deduplication (check + insert) | Simple | Race condition between check and insert; not safe without DB lock |

**Decision: Redis with 24h TTL + Redis Sentinel for HA**
Because the payment hot path requires sub-millisecond idempotency checks; DB round-trips add P99 tail latency. Redis Sentinel eliminates the single-point-of-failure concern.
The trade-off I'm accepting: if Redis cluster is entirely down (beyond Sentinel failover), payments are blocked. Mitigation: fall back to DB-only path during Redis outage (degrade gracefully, not fail completely).

**Implementation sketch:**

```java
public PaymentResponse charge(PaymentRequest request, String idempotencyKey) {
    // Check Redis cache first — O(1) lookup
    String cacheKey = "idempotency:" + idempotencyKey;
    String cached = redisClient.get(cacheKey);
    if (cached != null) {
        // Replay: return stored result without re-charging
        return objectMapper.readValue(cached, PaymentResponse.class);
    }

    // First-time request: charge payment gateway
    ChargeResult chargeResult = paymentProcessor.charge(
        request.getAmountCents(),
        request.getCurrencyCode(),
        request.getCustomerId()
    );

    // Atomic DB write: update subscription + insert outbox event
    subscriptionRepository.transactionalActivate(
        request.getSubscriptionId(),
        chargeResult.getChargeId(),
        buildOutboxPayload(chargeResult)
    );

    PaymentResponse response = PaymentResponse.success(chargeResult.getChargeId());

    // Cache result with 24h TTL — safe for client retries
    redisClient.setex(cacheKey, 86400, objectMapper.writeValueAsString(response));

    return response;
}
```

```java
// Repository: single transaction — both writes or neither
@Transactional
public void transactionalActivate(UUID subscriptionId, String chargeId, String outboxPayload) {
    // State machine transition: validates the event is legal from current state
    Subscription subscription = subscriptionRepository.findByIdForUpdate(subscriptionId);
    SubscriptionStatus nextStatus = stateMachine.transition(
        subscription.getStatus(),
        SubscriptionEvent.PAYMENT_SUCCEEDED
    );
    subscription.setStatus(nextStatus);
    subscription.setLastChargeId(chargeId);
    subscriptionRepository.save(subscription);

    // Outbox row in same transaction — atomically paired with subscription update
    outboxRepository.insert(
        subscriptionId,
        "SUBSCRIPTION_ACTIVATED",
        outboxPayload
    );
    // Transaction commits: both rows written or neither (DB rollback on any exception)
}
```

---

### Deep Dive 2: Outbox Processor + Kafka Fanout

**Why this is the most critical component:**
The outbox pattern is what makes billing events reliable without tight coupling. Every candidate who defaults to `@KafkaProducer` inside the payment service transaction is exposed to the dual-write problem — the most common senior-level mistake on this question.

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| Direct Kafka publish inside `@Transactional` | Simple | Kafka publish and DB commit are separate ops; timeout between them = event lost |
| Outbox table + background poller | Atomic guarantee; no event loss | 100ms polling delay; outbox table needs cleanup |
| Debezium CDC (Kafka Connector reads DB WAL) | Zero polling code; event exactly when committed | Requires Kafka Connect cluster; operational overhead |

**Decision: Outbox table + background poller**
Because Debezium adds significant operational complexity (Kafka Connect cluster, connector config, connector restarts); the outbox poller is simpler to operate and the 100ms latency is acceptable for entitlement (not human-perceptible). Kafka direct publish risks silent event loss on Kafka broker unavailability — never acceptable for billing.
The trade-off I'm accepting: 100ms delay in downstream event processing. For DocuSign, entitlement granted 100ms after payment is imperceptible to the user. If sub-10ms was required, Debezium CDC would be the answer.

**Implementation sketch:**

```java
// Outbox table schema
// See Section 9 for full DDL

// OutboxProcessor — runs every 100ms as a scheduled Spring task
@Scheduled(fixedDelay = 100)
public void processOutbox() {
    List<OutboxEvent> pending = outboxRepository.findUnprocessed(100);
    for (OutboxEvent event : pending) {
        try {
            // Publish to Kafka — at-least-once (Kafka producer acks=all).
            // Partition key is the aggregate id (subscription_id) so all events for
            // one subscription land on one partition and stay ordered.
            // .get(...) blocks until the broker acks.
            kafkaTemplate.send(
                "subscription-events",
                event.getAggregateId().toString(),
                event.getPayload()
            ).get(5, TimeUnit.SECONDS);

            // Mark processed only after successful Kafka ack
            outboxRepository.markProcessed(event.getId());
        } catch (Exception e) {
            // Log and skip — will retry on next poll cycle
            // Retry count tracked; alert if count > threshold
            log.error("Outbox publish failed for event {}", event.getId(), e);
        }
    }
}
```

```java
// EntitlementConsumer — must be idempotent
@KafkaListener(topics = "subscription-events", groupId = "entitlement-service")
public void onSubscriptionEvent(ConsumerRecord<String, String> record) {
    SubscriptionEvent event = objectMapper.readValue(record.value(), SubscriptionEvent.class);

    // Idempotency guard: check if this event was already processed
    if (entitlementRepository.eventAlreadyProcessed(event.getEventId())) {
        log.info("Skipping duplicate event {}", event.getEventId());
        return;
    }

    // Process based on event type
    switch (event.getEventType()) {
        case "SUBSCRIPTION_ACTIVATED":
            entitlementService.grantAccess(event.getTenantId(), event.getPlanId());
            break;
        case "SUBSCRIPTION_CANCELLED":
            entitlementService.revokeAccess(event.getTenantId());
            break;
    }

    // Record that this event was processed (idempotency table)
    entitlementRepository.markEventProcessed(event.getEventId());
}
```

---

### Deep Dive 3: Stripe Webhook Ingestion — Duplicates and Out-of-Order Events

**Why this is the most critical component:**
`POST /v1/payments` is only *half* of the money path. The other half is Stripe telling you what happened after the fact — and Stripe's delivery contract is **at-least-once, with no ordering guarantee**. Two independent failure modes fall out of that, and both silently corrupt subscription state. Candidates who only handle the synchronous charge path get pushed here and have nothing to say.

**Failure mode A — duplicates.** Stripe retries any webhook that doesn't get a 2xx within 20 seconds, with backoff, for up to 3 days. It also legitimately re-sends after a delivery-endpoint outage. So `invoice.paid` for the same invoice will arrive twice. If your handler is `subscription.retryCount = 0; grantEntitlement()`, running twice is harmless — but `ledger.append(+$50)` running twice books $100 of revenue that does not exist.

**Failure mode B — out-of-order.** This is the subtler one and it is the classic DocuSign Commerce probe. Stripe fans webhooks out from multiple workers; `invoice.paid` and `customer.subscription.updated` for the *same* plan upgrade are independent deliveries and can arrive in either order. The damaging sequence:

### 🎨 Visual — the webhook out-of-order problem and how a version guard fixes it

```
Real order of truth at Stripe (what actually happened):
   t0  customer.subscription.updated   plan: Pro → Premium   (stripe seq 41)
   t1  invoice.paid                    amount: $12.50 delta  (stripe seq 42)

  ❌ NAIVE HANDLER — events arrive reversed over the network
  ┌──────────────────────────────────────────────────────────────────┐
  │  t0+80ms   invoice.paid  arrives FIRST                           │
  │              handler: status = ACTIVE, entitle(plan = Pro)       │
  │                                        └─ reads the OLD plan ─┐  │
  │  t0+310ms  subscription.updated arrives SECOND                │  │
  │              handler: plan_id = Premium                       │  │
  │                                                              ▼  │
  │  FINAL STATE:  subscriptions.plan_id = Premium  ✓               │
  │                entitlement cache      = Pro     ✗  ← DIVERGED   │
  │  Customer paid for Premium, is entitled to Pro. No error logged. │
  └──────────────────────────────────────────────────────────────────┘

  ✅ VERSION-GUARDED HANDLER — apply only if the event is not stale
  ┌──────────────────────────────────────────────────────────────────┐
  │  Every webhook carries Stripe's object version. Guard the write: │
  │                                                                  │
  │    UPDATE subscriptions                                          │
  │       SET plan_id = ?, status = ?, provider_version = ?          │
  │     WHERE id = ? AND provider_version < ?                        │
  │                                                                  │
  │  t0+80ms   invoice.paid (v42)  → 42 > 0   → APPLIED, version=42  │
  │  t0+310ms  sub.updated  (v41)  → 41 < 42  → 0 rows, DROPPED      │
  │                                   (stale — re-read the object    │
  │                                    from Stripe to reconcile)     │
  └──────────────────────────────────────────────────────────────────┘

KEY INVARIANT:
   A webhook handler is a CONDITIONAL write, never an unconditional one.
   Dedup on event.id makes it safe to run twice; the provider_version
   guard makes it safe to run in the WRONG ORDER. You need both —
   idempotency alone does not buy you ordering.
```

**The three-layer defence (say all three out loud):**

| Layer | Mechanism | What it protects against |
|---|---|---|
| Authenticity | HMAC verify `Stripe-Signature` over the **raw** body, reject if timestamp is >5 min old | Forged webhooks minting free Enterprise entitlements |
| Duplicates | `INSERT INTO provider_events(event_id) ` with a UNIQUE constraint; unique violation → return 200 and stop | Double-booked revenue on Stripe retries |
| Ordering | `WHERE provider_version < :incoming_version` on every state write | Silent plan/entitlement divergence |

**Implementation sketch:**

```java
/**
 * Webhook entry point. Returns 200 as fast as possible, then processes async.
 * Stripe retries any non-2xx for up to 3 days — a slow handler becomes a retry storm.
 */
@PostMapping(value = "/v1/webhooks/stripe", consumes = "application/json")
public ResponseEntity<String> handleStripeWebhook(
        @RequestBody byte[] rawBody,
        @RequestHeader("Stripe-Signature") String signature) {

    // Layer 1 — authenticity. MUST use the raw bytes; a re-serialized body breaks the HMAC.
    StripeEvent event;
    try {
        event = signatureVerifier.verifyAndParse(rawBody, signature, webhookSecret);
    } catch (SignatureVerificationException e) {
        // Only trigger for a 400 on this route: bad HMAC or timestamp outside tolerance
        return ResponseEntity.badRequest().body("invalid signature");
    }

    // Layer 2 — dedup. UNIQUE(event_id) makes this the atomic "have I seen you?" check.
    boolean firstTimeSeen = providerEventRepo.insertIfAbsent(event.getId(), rawBody);
    if (!firstTimeSeen) {
        // Stripe retry of an event already durably stored — acknowledge, do nothing
        return ResponseEntity.ok("{\"received\":true}");
    }

    // Durable, so acknowledge now and let a worker apply it
    webhookQueue.enqueue(event.getId());
    return ResponseEntity.ok("{\"received\":true}");
}
```

```java
/**
 * Applied by the async worker. Layer 3 — the version guard.
 * The UPDATE is conditional, so a stale (out-of-order) event affects 0 rows.
 */
@Transactional
public void applySubscriptionUpdated(StripeEvent event) {
    UUID subscriptionId = resolveLocalId(event.getObject().getId());
    long incomingVersion = event.getObject().getVersion();

    int rowsAffected = subscriptionRepo.updateIfNewer(
        subscriptionId,
        event.getObject().getPlanId(),
        event.getObject().getStatus(),
        incomingVersion
    );

    if (rowsAffected == 0) {
        // Stale event: a newer version already landed. Do NOT apply it.
        // Re-read the authoritative object from Stripe and reconcile once.
        reconciler.scheduleReconcile(subscriptionId);
        return;
    }

    // Only a winning write emits a downstream event — otherwise consumers flap
    outboxRepo.insert(subscriptionId, "SUBSCRIPTION_PLAN_CHANGED", buildPayload(event));
}
```

**The reconciliation backstop (name this — it is what separates a designed system from a hopeful one):** webhooks are best-effort even with all three layers, so a nightly job pages through Stripe's `subscriptions.list` and diffs `(status, plan_id, current_period_end)` against the local table. Any drift is logged as a `BILLING_DRIFT` metric and auto-corrected from Stripe, because **Stripe is the source of truth for money and we are the source of truth for entitlement**. At 1.6M subscriptions this is a ~30-minute paged job, run off a read replica.

---

### Deep Dive 4 (if time permits): Renewal Scheduler

**Why this matters:**
The ~100K renewals that land on the 1st-of-month peak day cannot run as a cron job that fires at midnight and tries to charge 100K subscriptions simultaneously. That's a thundering herd — Stripe rate limits, DB lock contention, and memory pressure all spike at once.

**Decision: Distributed scheduler with jitter + queue-based execution**

```
[Scheduler Job — runs every minute]
  SELECT * FROM subscriptions
  WHERE next_renewal_date BETWEEN NOW() AND NOW() + INTERVAL '1 day'
    AND status = 'ACTIVE'
    AND NOT EXISTS (SELECT 1 FROM renewal_jobs WHERE subscription_id = s.id AND date = CURRENT_DATE)
  LIMIT 1000

  FOR EACH subscription:
    INSERT INTO renewal_jobs (subscription_id, scheduled_at = NOW() + random_jitter_0_to_60min)
    Publish to Kafka: renewal-scheduled

[RenewalWorker — Kafka consumer]
  Consumes from renewal-scheduled
  Sleeps until scheduled_at (respects jitter)
  Calls BillingService.charge(subscription_id, idempotency_key = "renewal:" + subscription_id + ":" + today)
```

> 📖 Full: `SystemDesignConcepts/Production-Grade/System-Design-Patterns/47-job-scheduling-at-scale.md`

**Why random jitter:** spreading 100K renewals over 60 minutes = ~28/sec instead of a single-minute spike. Stripe's API rate limit (100 req/sec per account) is not breached, and Postgres sees ~85 row-writes/sec instead of a lock stampede on the `subscriptions` table.

---

## Section 9 — 🗄️ Data Model

### Core Tables

```sql
-- Organizations (tenants) — one billing entity per org
CREATE TABLE organizations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(255) NOT NULL,
    billing_email   VARCHAR(255) NOT NULL,
    stripe_customer_id VARCHAR(100),           -- Stripe customer reference
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Subscription plans (flat-rate tiers)
CREATE TABLE subscription_plans (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(100) NOT NULL,     -- 'Business Pro', 'Enterprise', etc.
    price_cents     INT NOT NULL,              -- 2500 = $25.00 USD
    currency_code   CHAR(3) NOT NULL DEFAULT 'USD',
    billing_interval VARCHAR(20) NOT NULL,     -- 'MONTHLY' or 'ANNUAL'
    features        JSONB NOT NULL,            -- {"max_users": 5, "api_rate_limit": 1000}
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Subscriptions — one per organization (current state)
CREATE TABLE subscriptions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    organization_id     UUID NOT NULL REFERENCES organizations(id),
    plan_id             UUID NOT NULL REFERENCES subscription_plans(id),
    status              VARCHAR(20) NOT NULL
                            CHECK (status IN ('PENDING','ACTIVE','PAST_DUE','CANCELLED','PAUSED')),
    current_period_start TIMESTAMPTZ NOT NULL,
    current_period_end   TIMESTAMPTZ NOT NULL,
    cancelled_at        TIMESTAMPTZ,           -- NULL if not cancelled
    pending_plan_id     UUID REFERENCES subscription_plans(id), -- for period-end plan change
    retry_count         SMALLINT NOT NULL DEFAULT 0,
    provider_version    BIGINT NOT NULL DEFAULT 0, -- Stripe object version; guards out-of-order webhooks
    last_charge_id      VARCHAR(100),          -- Stripe charge_id of last successful payment
    created_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_subscriptions_org
    ON subscriptions(organization_id)
    WHERE status != 'CANCELLED';               -- one active subscription per org

CREATE INDEX idx_subscriptions_renewal
    ON subscriptions(current_period_end, status)
    WHERE status = 'ACTIVE';                  -- renewal scheduler query: O(renewals_due) not O(total)

-- Subscription events — append-only audit trail (SOC 2 compliance)
CREATE TABLE subscription_events (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL REFERENCES subscriptions(id),
    event_type      VARCHAR(50) NOT NULL,      -- 'ACTIVATED', 'CANCELLED', 'PLAN_CHANGED', etc.
    from_status     VARCHAR(20),
    to_status       VARCHAR(20),
    actor_user_id   UUID,                      -- NULL for system-initiated events (renewal)
    metadata        JSONB,                     -- plan_id, charge_id, etc.
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
-- No UPDATE or DELETE on this table — insert only, enforced by application + DB grants

CREATE INDEX idx_subscription_events_sub
    ON subscription_events(subscription_id, created_at DESC);

-- Payments table
CREATE TABLE payments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    subscription_id UUID NOT NULL REFERENCES subscriptions(id),
    organization_id UUID NOT NULL REFERENCES organizations(id),
    amount_cents    INT NOT NULL,
    currency_code   CHAR(3) NOT NULL DEFAULT 'USD',
    status          VARCHAR(20) NOT NULL
                        CHECK (status IN ('PENDING','SUCCEEDED','FAILED','REFUNDED')),
    idempotency_key VARCHAR(64) NOT NULL,
    stripe_charge_id VARCHAR(100),             -- populated after Stripe responds
    failure_reason  VARCHAR(255),              -- decline_code from Stripe
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_payments_idempotency
    ON payments(idempotency_key);              -- DB-level guard (secondary to Redis)

CREATE INDEX idx_payments_subscription
    ON payments(subscription_id, created_at DESC);

-- Outbox table — short-lived; rows deleted after Kafka publish
CREATE TABLE outbox (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_id    UUID NOT NULL,             -- subscription_id
    aggregate_type  VARCHAR(50) NOT NULL,      -- 'SUBSCRIPTION'
    event_type      VARCHAR(50) NOT NULL,      -- 'SUBSCRIPTION_ACTIVATED', etc.
    payload         JSONB NOT NULL,
    processed_at    TIMESTAMPTZ,               -- NULL = pending; set after Kafka publish
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_outbox_unprocessed
    ON outbox(created_at)
    WHERE processed_at IS NULL;               -- OutboxProcessor query: only unprocessed rows

-- Inbound webhook log — the mirror image of `outbox`. Outbox = events we publish;
-- provider_events = events we receive. Both exist for the same reason: exactly-once effect.
CREATE TABLE provider_events (
    event_id        VARCHAR(100) PRIMARY KEY,  -- Stripe's evt_... id; PK IS the dedup guard
    event_type      VARCHAR(50) NOT NULL,      -- 'invoice.paid', 'customer.subscription.updated'
    raw_payload     JSONB NOT NULL,            -- stored verbatim for replay and dispute forensics
    received_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed_at    TIMESTAMPTZ,               -- NULL = queued; set after the handler applies it
    outcome         VARCHAR(20)                -- 'APPLIED', 'DROPPED_STALE', 'FAILED'
);

CREATE INDEX idx_provider_events_unprocessed
    ON provider_events(received_at)
    WHERE processed_at IS NULL;
```

### Key Schema Decisions

- **`subscription_events` is append-only:** No UPDATE or DELETE ever. This is the SOC 2 audit trail. DB grants for the billing service role: INSERT only, never UPDATE/DELETE on this table.
- **`status` CHECK constraint:** The DB enforces the valid set of states. The application state machine enforces transitions. Defense in depth — a bug that writes an invalid status fails at the DB layer.
- **`pending_plan_id`:** Plan downgrade at period end. Renewal job reads `pending_plan_id`, applies the new plan at `current_period_end`, clears the column. Avoids complexity of immediate proration credits.
- **`outbox.processed_at` partial index:** Only unprocessed rows appear in the OutboxProcessor query. As processed rows are cleaned up, the index stays tiny — no performance degradation over time.
- **`provider_events.event_id` as the primary key:** Deduplication of Stripe webhook retries is not application logic — it's a primary-key constraint. A duplicate delivery raises a unique violation, which the handler translates into "already seen, return 200." Free, atomic, and impossible to forget.
- **`subscriptions.provider_version`:** The out-of-order guard. Every webhook-driven write is `... WHERE provider_version < :incoming`, so a late-arriving stale event affects 0 rows instead of overwriting newer truth. See Deep Dive 3.
- **Money is `INT` cents, never `NUMERIC` and never `FLOAT`:** `amount_cents` is integer minor units. Float would introduce IEEE-754 representation error into a financial record; `NUMERIC` is exact but invites accidental fractional-cent arithmetic. Integer cents makes the illegal state unrepresentable. Pair every amount with its `currency_code` in the same row — an amount without a currency is a bug waiting to happen.
- **SQL, not NoSQL:** Subscription data is highly relational (org → subscription → payment → plan). Transactional correctness (ACID) is mandatory for billing. Postgres is the right choice; NoSQL would sacrifice the transaction guarantees we need for the outbox pattern.

> 📖 Full: `SystemDesignConcepts/Foundations/Data-Fundamentals/12-data-modeling.md`

---

## Section 10 — ⚠️ Trade-offs + Failure Modes (Minutes 38–45)

### Trade-off 1: Eventual Consistency (Outbox + Kafka) vs Synchronous Entitlement

- **Chose:** Eventual consistency — outbox + Kafka fanout
- **Gain:** Payment API P99 is not affected by entitlement service slowness; adding new downstream consumers requires zero changes to BillingService (OCP satisfied); any single consumer failure does not fail the payment
- **Lose:** 100ms–500ms lag between payment success and entitlement grant; during this window, a user who just paid cannot yet access their new features
- **Failure mode if wrong (sync):** Entitlement service 5s timeout → payment API P99 spikes → Stripe gateway timeout → double-charge risk; adding 5th downstream consumer requires modifying billing service code. **Business impact:** End-of-quarter enterprise upgrade request (common at DocuSign — contracts auto-renew or get upgraded) triggers entitlement service slowdown → payment API times out → customer sees "payment failed" even though Stripe already charged their card → customer files a chargeback → DocuSign support spike, finance reconciliation effort, and risk of losing the renewal.

### Trade-off 2: Outbox Poller vs Debezium CDC for Event Relay

- **Chose:** Outbox table + Spring `@Scheduled` poller
- **Gain:** No additional infrastructure (no Kafka Connect cluster, no connector config); simpler to test and debug; team can reason about it without Kafka Connect expertise
- **Lose:** 100ms polling granularity (vs near-zero latency with CDC); poller is a single-threaded bottleneck (mitigated by processing 100 rows per cycle at ~1.2 writes/sec, this is more than sufficient); polling adds DB load
- **Failure mode if wrong (direct Kafka publish):** Kafka broker unavailable between DB commit and Kafka publish → subscription activated in DB but no downstream notification → entitlement never granted → silent, hard-to-diagnose inconsistency. **Business impact:** Customer pays for a plan upgrade but cannot access the new DocuSign features (e.g., advanced fields, bulk send) — for DocuSign this means the customer thinks the upgrade failed, calls support, and if the inconsistency persists days, churns or files a chargeback, even though their subscription record shows 'ACTIVE'.

### Trade-off 3: Single Postgres Writer vs Sharded DB

- **Chose:** Single Postgres primary with read replicas
- **Gain:** Simpler operations; no cross-shard transaction complexity; no re-sharding risk; ~28 charges/sec on the 1st-of-month peak (~85 row-writes/sec) is under 2% of a single Postgres primary's ~5,000 writes/sec capacity
- **Lose:** Single point of write failure (mitigated by RDS Multi-AZ automatic failover with ~30s RTO); no horizontal write scaling if DocuSign grows 100×
- **Failure mode if wrong (premature sharding):** Proration transactions span plan table and subscription table — cross-shard transactions require distributed 2PC (two-phase commit), which reintroduces availability vs consistency trade-offs unnecessarily at this scale. **Business impact:** A mid-cycle plan upgrade (plan changes require prorated billing calculation across subscription + plan tables) fails due to 2PC coordinator timeout → customer is charged the wrong amount for the next billing cycle — for DocuSign this means an incorrect invoice goes to an enterprise customer's finance team, triggering a manual dispute process and eroding trust in the billing system.

---

## Section 11 — 🔐 DocuSign-Specific Depth

### Commerce Backend Team Context

DocuSign's Commerce Backend team owns the subscription lifecycle for all paid eSign plans. The billing system described here is the literal architecture behind DocuSign's own revenue engine. This gives you the opportunity to show you understand *their* domain, not just generic billing.

**DocuSign's plan tiers (name these):**
- Individual, Business Pro, Business Premium, Enterprise
- Each tier maps to a `subscription_plans` row with a `features` JSONB column specifying API rate limits, max users, and feature flags

**Multi-tenant billing (B2B SaaS implication):**
- Subscriptions belong to organizations (tenants), not individual users
- One org admin manages billing for all seats in the org
- The `organizations.stripe_customer_id` maps to a Stripe Customer object — all charges for that org go through one Stripe customer, enabling consolidated invoice history

**SOC 2 Type II compliance:**
- Every subscription state change writes to `subscription_events` (append-only, INSERT-only)
- Audit entries include: `actor_user_id` (NULL for system/renewal), `from_status`, `to_status`, `metadata` (charge ID, plan ID)
- Regulators can query the full lifecycle of any subscription without reading from mutable tables

**SOLID applied to billing (the PDF asks for this explicitly):**
- **SRP:** `BillingService` charges payments only; `EntitlementService` grants access only; `RenewalScheduler` determines *when* to bill — each class has one reason to change
- **OCP:** Adding a new downstream consumer (e.g., `CRMConsumer` for Salesforce sync) requires zero changes to `BillingService` — add a new Kafka consumer group
- **LSP:** `StripePaymentProcessor` and `BraintreePaymentProcessor` are fully substitutable via `IPaymentProcessor` interface
- **ISP:** `IPaymentProcessor` is narrow — `charge(amount, currency, customerId)` and `refund(chargeId)` only. The billing service does not depend on Stripe-specific methods like `createPaymentIntent`
- **DIP:** `BillingService` depends on `IPaymentProcessor` (abstraction), not `StripePaymentProcessor` (concrete). Processor swapped by injecting different Spring bean — no billing logic changes

**KYC / Fraud angle:**
- Enterprise plan changes initiated by API are flagged for review if the org has no prior payment history (new org upgrading directly to Enterprise is a fraud signal)
- Idempotency key abuse detection: same idempotency key used across different subscription IDs → alert (client bug or intentional manipulation)

**Dunning management (the retry lifecycle after a failed payment):**

Dunning (the process of systematically retrying failed payments before cancelling a subscription) is a first-class business concern at any SaaS company. Most candidates stop at "retry 3 times" — the full dunning lifecycle is:

```
Day 0:   Payment fails → ACTIVE transitions to PAST_DUE → retry #1 immediately
Day 1:   retry #2   (gap = 1 day)
Day 3:   retry #3   (gap = 2 days)
Day 7:   retry #4, final attempt (gap = 4 days) + warning email to org admin
Day 8:   retries_exhausted → CANCELLED → access revoked → churn event published

Gaps double: 1 → 2 → 4 days. THAT is what makes it exponential backoff.
(A common bad answer is "Day 0/3/5/7" — gaps of 3/2/2 shrink, so calling
 it exponential backoff is wrong and an interviewer who knows the pattern
 will notice. Say the gaps out loud, not just the days.)
```

**Why the ladder grows rather than hammers:** the dominant decline reason is `insufficient_funds`, which resolves on the customer's payroll cycle — days, not minutes. Retrying every hour burns Stripe rate budget and, worse, some issuers treat repeated declines on the same card as a fraud signal and hard-block it. Growing gaps track the real-world event you're waiting for.

**Implementation detail:** `retry_count` in the `subscriptions` table tracks attempts and increments on *every* failed attempt — note that the transition to `PAST_DUE` happens on the **first** failure, not on retry exhaustion; exhaustion is what moves `PAST_DUE → CANCELLED`. Each retry is scheduled as a `renewal_jobs` row with its future `scheduled_at`. If any retry succeeds, `PAST_DUE → ACTIVE` and `retry_count` resets to 0. The schedule is a configuration parameter (not hardcoded) — finance teams adjust it per customer segment (enterprise gets longer grace periods; the FR's "4 attempts over 7 days" is the default, not a law).

**Invoice immutability + the credit-note pattern (a finalized invoice is never mutated):**

An invoice moves `DRAFT → OPEN → PAID | VOID | UNCOLLECTIBLE`. Once it leaves `DRAFT` it is a **legal financial document** and every row on it is frozen. If the amount was wrong, you do *not* run `UPDATE invoices SET amount_cents = ...`. You issue a **credit note** — a separate, signed, negative-amount document that references the original invoice.

```
WRONG (what most candidates say):
    UPDATE invoices SET amount_cents = 3750 WHERE id = 'inv_123';
    → the $5,000 invoice the customer's AP department already filed
      and paid against no longer exists. The audit trail is destroyed.
      Under SOX this is a material weakness, not a bug.

RIGHT:
    invoices        inv_123   OPEN   5000 cents   (frozen forever)
    credit_notes    cn_456    →inv_123   -1250 cents   reason='PRORATION_CREDIT'
    Net owed = 5000 - 1250 = 3750 cents, and BOTH documents are retrievable.
```

Why this matters here specifically: the proration credit from Section 8 **is** a credit note. That is how a mid-cycle downgrade or a service-credit SLA refund is represented without touching a finalized invoice. Say it in one sentence: *"Finalized invoices are immutable; corrections are additive credit notes that reference the original."* That single sentence covers immutability, auditability, and the proration mechanism at once.

**Revenue recognition (ASC 606 / deferred revenue):**

ASC 606 (the US accounting standard that governs when revenue can be recognized — i.e., counted in income — for SaaS companies) requires that revenue is recognized when *performance obligations are fulfilled*, not when cash is received. For annual subscriptions this matters significantly:

- Customer pays $1,200 for a 12-month annual plan on January 1 → you received $1,200 in cash
- Under ASC 606, you can only recognize $100/month (the portion earned by fulfilling the service obligation that month)
- The remaining $1,100 sits in **deferred revenue** (a liability on the balance sheet)

**Why this affects billing system design:**
- The `payments` table records cash received ($1,200)
- A separate `revenue_recognition` table records recognized revenue ($100/month) as a scheduled job runs at period end
- This table is audited for SOX (Sarbanes-Oxley) compliance — tamper-evident, append-only
- Plan upgrades mid-cycle trigger an ASC 606 recalculation: remaining deferred revenue from old plan is partially recognized, new deferred revenue schedule begins

**In an interview:** Name ASC 606 and deferred revenue as a distinct concern from payment processing. Most candidates conflate "payment received" with "revenue earned" — naming this distinction signals real SaaS billing depth.

**PCI-DSS scope minimization:**

PCI-DSS (Payment Card Industry Data Security Standard — a security certification required of any business that stores, transmits, or processes cardholder data) is expensive to maintain scope for. Storing card numbers yourself requires quarterly audits, network segmentation, encryption at rest, strict access controls, and annual certification by a Qualified Security Assessor (QSA).

**The correct architectural decision:** Never store card data in your own systems. Instead:
- At checkout, the client collects card data directly in Stripe's hosted JS widget (Stripe Elements or Stripe.js)
- Stripe tokenizes the card and returns a `payment_method_id` (a string like `pm_abc123`)
- Your billing service stores only the `payment_method_id` (not the card number, not the CVV, not the expiry)
- All future charges reference this token: `paymentProcessor.charge(paymentMethodId, amountCents, ...)`

**What this means for your schema:** `organizations.stripe_customer_id` and `payments.stripe_charge_id` — both are Stripe-opaque tokens. No PAN (primary account number), no CVV, no expiry anywhere in your DB. This shrinks your PCI-DSS scope to nearly zero (you're a "merchant using a third-party processor" not a "merchant storing cardholder data").

**In an interview:** When asked "how do you handle payment security?", don't say "encrypt the card number in the DB." Say "we never store card data — we use Stripe tokenization and our systems only ever see `payment_method_id`. This eliminates PCI-DSS scope for cardholder data storage entirely."

**Double-entry ledger for financial auditability:**

A double-entry ledger (the accounting principle where every financial event generates two equal and opposite entries — one debit, one credit — so that the books always balance) is the foundation of any production-grade financial system.

For a subscription billing system, every financial event creates two ledger entries:

| Event | Debit | Credit |
|---|---|---|
| Customer pays $100 | Cash (asset) +$100 | Deferred Revenue (liability) +$100 |
| End of month: $100 earned | Deferred Revenue (liability) -$100 | Revenue (income) +$100 |
| Refund issued: $100 | Deferred Revenue +$100 / Revenue -$100 | Cash -$100 |

**Implementation:** A `ledger_entries` table (append-only, never UPDATE/DELETE) with columns `(event_id, account_type, direction, amount_cents, currency_code, created_at)`. The billing service writes ledger entries transactionally alongside the subscription state change. Finance teams run `SELECT SUM(CASE direction WHEN 'CREDIT' THEN amount_cents ELSE -amount_cents END)` to verify books balance.

**In an interview:** You don't need to design the full ledger. Name it: "In a production billing system, every financial event generates corresponding ledger entries following double-entry accounting principles. The `payments` table records transactions; a separate `ledger_entries` table records the accounting entries. This is what allows finance to produce a trial balance and close the books each month." That sentence alone separates you from 95% of candidates.

---

## Section 12 — 🔬 Where the Interviewer Will Probe

### Surface Probe (Tier 1 — every candidate gets this)

**Q: "What happens if the client retries the payment request after a network timeout?"**
> The `Idempotency-Key` header is checked against Redis before any Stripe call. If the key exists in Redis, we return the cached response immediately — no second charge. Redis stores the response for 24 hours. The key is the client-generated UUID from the original request. This is why we document in the API contract that clients must generate a fresh UUID per distinct payment intent, not reuse old keys.

**Q: "How does the renewal job avoid charging the same subscription twice in one cycle?"**
> The renewal scheduler inserts a `renewal_jobs` row with a unique constraint on `(subscription_id, billing_date)` before enqueuing the charge. If the scheduler runs twice (pod restart, duplicate trigger), the second insert fails the unique constraint — the charge is never enqueued twice. The payment itself also carries an idempotency key: `"renewal:" + subscriptionId + ":" + billingDate`.

### Deep Probe (Tier 2 — tests real understanding)

**Q: "What if the outbox processor crashes between publishing to Kafka and marking the row as processed? Do we get duplicate events?"**
> Yes — at-least-once delivery is the outbox guarantee. The outbox processor publishes to Kafka, then marks `processed_at`. If it crashes between those two steps, the row is reprocessed on restart and published again. This means every downstream consumer MUST be idempotent. For `EntitlementConsumer`, the idempotency check is: `SELECT 1 FROM processed_events WHERE event_id = ?`. If the event ID exists, skip. This is a standard at-least-once + idempotent consumer pattern — not a flaw in the outbox design.

**Q: "How do you handle a Stripe API rate limit during a renewal burst?"**
> The renewal scheduler uses random jitter (0–60 minutes) to spread the ~100K peak-day renewals over the hour rather than spiking at midnight. This limits peak Stripe throughput to ~28 req/sec (100K ÷ 3,600 seconds). Stripe's rate limit is 100 req/sec per account — we stay comfortably below. Two different retry ladders exist here and you should not conflate them: a Stripe `429` is a *transport* failure, retried in-process with backoff 5s → 10s → 30s and no state change, because the customer's card was never touched. A Stripe `402` card decline is a *business* failure — it moves the subscription to `PAST_DUE` on the **first** decline and hands off to the multi-day dunning ladder (Day 0/1/3/7), incrementing `retry_count` per attempt. `PAST_DUE → CANCELLED` happens only when all 4 dunning attempts are exhausted.

### Cross-Concept Probe (Tier 3 — separates senior candidates)

**Q: "If we needed to support metered billing (charge per document signed), what changes?"**
> Metered billing requires a fundamentally different upstream pipeline: (1) every `document_signed` event is published to a Kafka topic by the signing service, (2) a Flink or Spark Streaming job aggregates events per `(tenant_id, billing_cycle)`, writing running totals to a Redis counter, (3) at billing time, the BillingService reads the Redis counter as the billable quantity, computes the charge, then resets the counter for the next cycle. The subscription state machine and outbox pattern remain unchanged — only the amount calculation changes. The key risk is "at exactly what moment does the billing cycle end?" — we'd need a consistent snapshot of the counter at `current_period_end`, not a live read, to avoid race conditions between ongoing signing events and the billing job.

---

## Section 13 — 🐞 Common Mistakes on This Question

- **Mistake 1: Publishing Kafka directly inside `@Transactional` block** → **Why it's wrong:** The Kafka publish and the DB commit are two separate operations. If Kafka is temporarily unavailable, the DB commits (subscription activated) but the event is never published — entitlement is never granted. Silent inconsistency. **What to say instead:** "I use the outbox pattern — both the subscription update and the outbox row are in the same DB transaction. Kafka publish happens from the outbox processor after the transaction commits. The two operations are decoupled."

- **Mistake 2: No idempotency on payment API** → **Why it's wrong:** Any client retry (timeout, flaky network) re-calls the payment API and charges the Stripe customer again. In a B2B SaaS context, a $2,500/year enterprise charge being doubled is a critical incident. **What to say instead:** "Every POST /payments requires an `Idempotency-Key` header. I check Redis before any Stripe call. This is non-negotiable for any billing API."

- **Mistake 3: Designing status as a free-text or unconstrained field** → **Why it's wrong:** Without a state machine, any code path can write any value to `status`. Invalid transitions (CANCELLED → ACTIVE without a payment) become possible. **What to say instead:** "Status transitions are governed by a state machine. The `stateMachine.transition(currentStatus, event)` method throws `InvalidTransitionException` for illegal transitions. The DB also has a CHECK constraint as a defense-in-depth layer."

- **Mistake 4: Calling entitlement and notification synchronously from billing service** → **Why it's wrong:** You've made payment P99 equal to max(payment_gateway_latency, entitlement_latency, notification_latency). If the notification service has a 5-second spike, your payment API P99 spikes too. Also violates OCP — adding a new downstream requires modifying BillingService. **What to say instead:** "After payment succeeds and the outbox row is committed, I return 200 to the client. Downstream notifications happen asynchronously via Kafka. EntitlementService and NotificationService are independent consumers."

- **Mistake 5: Thundering herd renewal** → **Why it's wrong:** Scheduling all renewals at midnight creates a spike of 100K simultaneous Stripe API calls, DB lock contention, and a single-pod renewal worker being overwhelmed. **What to say instead:** "The renewal scheduler uses random jitter — each subscription's renewal is scheduled with a random offset within a 60-minute window. This spreads 100K renewals evenly over the hour, keeping Stripe throughput at ~28 req/sec, well within rate limits."

- **Mistake 6: Treating the Stripe webhook as an afterthought** → **Why it's wrong:** `POST /v1/payments` is only the synchronous half of the money path. 3-D Secure completions, ACH reversals, and disputes all arrive later as webhooks, at-least-once and **unordered**. A handler that unconditionally writes state will apply a stale `subscription.updated` on top of a newer `invoice.paid` and silently leave `plan_id` and entitlement divergent — no exception, no alert. **What to say instead:** "Webhooks get three layers: HMAC signature verification on the raw body, a UNIQUE constraint on `event_id` for dedup, and a `WHERE provider_version < :incoming` guard so out-of-order events affect zero rows. Plus a nightly reconciliation job that diffs local state against Stripe, because webhooks are best-effort even with all three."

---

## Section 14 — 🧭 DocuSign Dimensions Checklist

| Dimension | Relevant? | How this design addresses it |
|---|---|---|
| Testability | ✅ | `IPaymentProcessor` is the DIP seam: the whole 28-charges/sec peak-day renewal run is replayable in CI against a mock processor with zero real money moved. The state machine is a pure function, so all 9 `(status, event)` transitions — including the `ACTIVE + PAYMENT_SUCCEEDED → ACTIVE` renewal self-transition that a real DocuSign monthly renewal exercises 1.6M times a month — are table-driven unit tests with no DB. Out-of-order webhooks are testable by replaying two stored `provider_events` rows in reverse and asserting the v41 event is dropped |
| Usability | ✅ | `Idempotency-Key` follows Stripe's own convention, so a DocuSign customer's existing Stripe-integration code needs no new mental model. `PUT /plan` returns `proration_credit_cents` — for the Business Pro → Business Premium upgrade in Section 8 that is the literal string "$12.50 due today" the admin sees before confirming, rather than an unexplained charge appearing later. Card declines return `402` with Stripe's `decline_code`, so the org admin is told "your card expired" instead of "an error occurred" — the difference between self-service recovery and a support ticket |

| Extensibility | ✅ | Adding a new downstream consumer (Salesforce CRM sync) = new Kafka consumer group, zero changes to BillingService (OCP); adding a new plan type = new row in `subscription_plans`, zero code changes; swapping Stripe for Braintree = new `IPaymentProcessor` implementation, injected via Spring bean |
| Security | ✅ | Zero cardholder data in any of the 7 tables — only Stripe tokens (`pm_`, `ch_`, `cus_`), which keeps DocuSign's PCI-DSS scope at SAQ-A instead of the quarterly-audit tier. `subscription_events` is INSERT-only across all ~19M events/year (1.6M orgs × 12), satisfying the SOC 2 Type II tamper-evidence control DocuSign certifies to. Idempotency keys are tenant-scoped, so a key harvested from one org's traffic cannot replay a charge against another org. The `/v1/webhooks/stripe` route is HMAC-verified with a 5-minute timestamp tolerance — without it, a forged `invoice.paid` mints free Enterprise entitlement for any account_id an attacker can guess |
| Availability | ✅ | The 99.9% SLO allows ~43 min/month. RDS Multi-AZ failover is ~30s, so a single failover consumes 1.2% of the monthly budget. The riskiest window is the 1st-of-month billing run: a 60-minute outage there strands ~100K renewals, but every renewal carries idempotency key `"renewal:" + subId + ":" + date`, so the run is simply re-executed after recovery with zero double-charges — this is why the availability story for DocuSign billing is *recoverability*, not just uptime. Entitlement reads survive a Postgres outage entirely because they are served from Redis at 5,500 req/sec, so an in-progress signing ceremony is never blocked by a billing-DB failover |
| Scalability | ✅ | Write path: ~28 charges/sec on the 1st-of-month peak (~85 row-writes/sec) against a ~5,000 writes/sec primary — under 2%, so no sharding; Read path: 5,500 req/sec entitlement checks → Redis cache with 30s TTL, since 5,500 req/sec of `SELECT status FROM subscriptions` would saturate the primary's CPU; Renewal: jitter spreads 100K peak-day charges across 60 minutes to stay under Stripe's 100 req/sec account limit; BillingService pods are stateless (all state in Postgres/Redis) so they scale horizontally behind the LB |
| Observability & Traceability | ✅ | `request_id` in every API response; `subscription_events` table is a complete state history for any subscription; Kafka consumer lag metric (alert when EntitlementConsumer lag > 10s); outbox row age metric (alert when unprocessed rows older than 30s); payment failure rate by `decline_code` (Grafana dashboard) |

---

## Section 15 — 🧾 TL;DR Answer Summary

> "I'd design a flat-rate subscription billing API using three key components working together: a state machine that owns all subscription lifecycle transitions (PENDING → ACTIVE → PAST_DUE → CANCELLED), a Redis-backed idempotency layer on the payment endpoint to prevent double-charges on retries, and an outbox pattern that atomically writes both the subscription update and a Kafka event in one DB transaction — so downstream entitlement, notification, and analytics services consume events independently without tight coupling to the payment path. The renewal scheduler uses random jitter to spread 100K renewals over 60 minutes, avoiding a thundering herd against Stripe's rate limits. For DocuSign specifically, I'd apply SOLID principles at the API layer — the billing service depends on an IPaymentProcessor interface (not Stripe directly), downstream consumers are added as new Kafka consumer groups without touching billing code (OCP), and the subscription_events table is INSERT-only for SOC 2 audit compliance."

---

---

## 🔌 LLD Drill-Down — Class Structure for the Billing Service

> **Trigger:** Interviewer says "Walk me through the class design for the billing service" or "Show me how the state machine and outbox work at the code level" — expected follow-up after the outbox + Kafka HLD discussion.
>
> **What they're testing:** Whether you understand that (1) the state machine is a pure function with no side effects, (2) the outbox write and subscription update are in one `@Transactional` method — not two separate calls, and (3) the `IPaymentProcessor` interface is the DIP boundary that makes the whole thing testable and swappable.

---

### 🧠 Mental Model Before You Draw

The billing service has three clearly separated concerns:

- **State machine** — pure function: `(currentStatus, event) → nextStatus`. No I/O. No DB. Just logic.
- **BillingService** — orchestrator: Redis idempotency check → charge processor → `@Transactional` write (subscription + outbox together)
- **OutboxProcessor** — background poller: reads unprocessed outbox rows → publishes to Kafka → marks processed

The critical point to say out loud: **"There's no Java `synchronized` here. The concurrency guarantee is Postgres — I use `SELECT FOR UPDATE` inside the `@Transactional` block to lock the subscription row before reading its state and applying the transition. Two threads trying to cancel the same subscription at the same time — one gets the lock, applies the transition, commits. The other gets the lock after commit, sees `status='CANCELLED'`, applies `CANCELLED + cancel event → InvalidTransitionException`."**

---

### 🏗️ Class Structure

```
┌──────────────────────────────────────────────────────────────┐
│                    BillingController                         │
│  POST /v1/payments   → BillingService.charge()              │
│  DELETE /subscriptions/{id} → BillingService.cancel()       │
└──────────────────────────────────────────────────────────────┘
        │ calls
        ▼
┌──────────────────────────────────────────────────────────────┐
│                    BillingService                            │
│  - paymentProcessor: IPaymentProcessor  (DIP boundary)      │
│  - subscriptionRepo: SubscriptionRepository                  │
│  - outboxRepo:       OutboxRepository                        │
│  - redis:            RedisTemplate                           │
│  - stateMachine:     SubscriptionStateMachine                │
│  + charge(request, idempotencyKey): PaymentResponse          │
│  + cancel(subscriptionId, userId):  void                     │
│  + upgrade(subscriptionId, newPlanId): void                  │
└──────────────────────────────────────────────────────────────┘
        │                            │
   ┌────▼────────────┐    ┌──────────▼──────────┐
   │ IPaymentProcessor│    │ SubscriptionStateMachine│
   │ (interface — DIP)│    │ + transition(status,  │
   │ + charge(...)    │    │   event): SubscriptionStatus │
   │ + refund(...)    │    │   throws InvalidTransition- │
   └────┬────────────┘    │   Exception            │
        │                 └─────────────────────────┘
   ┌────▼──────────────────┐
   │ StripePaymentProcessor│   (also: BraintreePaymentProcessor)
   │ - stripeClient        │
   │ + charge(amount, ...)  │
   │ + refund(chargeId)     │
   └───────────────────────┘

┌──────────────────────────────────────────────────────────────┐
│                    OutboxProcessor                           │
│  @Scheduled(fixedDelay=100ms)                               │
│  → reads unprocessed outbox rows                            │
│  → publishes to Kafka                                        │
│  → marks processed_at                                        │
└──────────────────────────────────────────────────────────────┘
```

---

### 🖊️ Critical Classes — Write These in the Interview

**SubscriptionStatus + SubscriptionEvent (enums — write first, they anchor everything else):**

```java
public enum SubscriptionStatus {
    PENDING, ACTIVE, PAST_DUE, CANCELLED, PAUSED
}

public enum SubscriptionEvent {
    PAYMENT_SUCCEEDED, PAYMENT_FAILED, RETRIES_EXHAUSTED, CANCEL_REQUESTED, REACTIVATED, PLAN_CHANGED
}
```

**SubscriptionStateMachine — pure function, no I/O:**

```java
/**
 * Pure state machine — no DB, no Kafka, no side effects.
 * Given a (currentStatus, event) pair, returns the next valid status.
 * Throws InvalidTransitionException for illegal transitions.
 * This is the single source of truth for subscription lifecycle logic.
 */
public class SubscriptionStateMachine {

    private static final Map<String, SubscriptionStatus> TRANSITIONS = new HashMap<>();

    static {
        // key = "FROM_STATUS:EVENT" → value = TO_STATUS
        TRANSITIONS.put("PENDING:PAYMENT_SUCCEEDED",   SubscriptionStatus.ACTIVE);
        // Self-transition: the monthly renewal charges an ALREADY-ACTIVE subscription.
        // Omitting this row is the #1 bug in hand-written billing state machines —
        // every renewal would throw InvalidTransitionException and roll back.
        TRANSITIONS.put("ACTIVE:PAYMENT_SUCCEEDED",    SubscriptionStatus.ACTIVE);
        TRANSITIONS.put("ACTIVE:PAYMENT_FAILED",       SubscriptionStatus.PAST_DUE);
        TRANSITIONS.put("ACTIVE:CANCEL_REQUESTED",     SubscriptionStatus.CANCELLED);
        // Plan change keeps the subscription ACTIVE; only plan_id moves
        TRANSITIONS.put("ACTIVE:PLAN_CHANGED",         SubscriptionStatus.ACTIVE);
        TRANSITIONS.put("PAST_DUE:PAYMENT_SUCCEEDED",  SubscriptionStatus.ACTIVE);
        TRANSITIONS.put("PAST_DUE:PAYMENT_FAILED",     SubscriptionStatus.PAST_DUE);
        TRANSITIONS.put("PAST_DUE:RETRIES_EXHAUSTED",  SubscriptionStatus.CANCELLED);
        TRANSITIONS.put("CANCELLED:REACTIVATED",       SubscriptionStatus.PENDING);
    }

    public SubscriptionStatus transition(SubscriptionStatus current, SubscriptionEvent event) {
        String key = current.name() + ":" + event.name();
        SubscriptionStatus next = TRANSITIONS.get(key);
        if (next == null) {
            throw new InvalidTransitionException(
                "No valid transition from " + current + " on event " + event
            );
        }
        return next;
    }
}
```

**IPaymentProcessor — the DIP boundary (makes Stripe swappable):**

```java
/**
 * DIP: BillingService depends on this abstraction, not on StripePaymentProcessor.
 * Swap payment processors by injecting a different @Bean — no billing logic changes.
 * In tests: inject MockPaymentProcessor → no real money, no Stripe calls.
 */
public interface IPaymentProcessor {
    ChargeResult charge(int amountCents, String currencyCode, String customerId);
    void refund(String chargeId);
}
```

**BillingService — the orchestrator (the class the interviewer most wants to see):**

```java
public class BillingService {

    private final IPaymentProcessor paymentProcessor;
    private final SubscriptionRepository subscriptionRepo;
    private final OutboxRepository outboxRepo;
    private final RedisTemplate<String, String> redis;
    private final SubscriptionStateMachine stateMachine;

    /**
     * Charge a subscription payment.
     * Order of operations:
     *   1. Check Redis idempotency key — return cached result if seen before
     *   2. Charge payment processor (Stripe) — external, unrecoverable if double-charged
     *   3. @Transactional: apply state machine transition + write outbox row (atomic)
     *   4. Cache result in Redis with 24h TTL
     *
     * Concurrency: @Transactional + SELECT FOR UPDATE locks the subscription row.
     * Two threads racing on the same subscription: one commits, the other reads
     * updated state and applies its transition from the committed state.
     */
    public PaymentResponse charge(PaymentRequest request, String idempotencyKey) {
        // Step 1: idempotency check
        String cacheKey = "idempotency:" + idempotencyKey;
        String cached = redis.opsForValue().get(cacheKey);
        if (cached != null) {
            return deserialize(cached, PaymentResponse.class);
        }

        // Step 2: charge payment processor (Stripe)
        // This happens OUTSIDE the DB transaction — Stripe is external and cannot be rolled back
        ChargeResult chargeResult = paymentProcessor.charge(
            request.getAmountCents(),
            request.getCurrencyCode(),
            request.getCustomerId()
        );

        // Step 3: atomic DB write — subscription update + outbox event in ONE transaction
        persistPaymentAndOutbox(request.getSubscriptionId(), chargeResult);

        PaymentResponse response = PaymentResponse.success(chargeResult.getChargeId());

        // Step 4: cache for future idempotent retries (24h TTL)
        redis.opsForValue().set(cacheKey, serialize(response), Duration.ofHours(24));

        return response;
    }

    /**
     * Single @Transactional method containing BOTH the subscription update
     * and the outbox insert. They commit together or rollback together.
     * This is what prevents the dual-write problem.
     */
    @Transactional
    public void persistPaymentAndOutbox(UUID subscriptionId, ChargeResult chargeResult) {
        // SELECT FOR UPDATE: row-level Postgres lock prevents concurrent updates
        Subscription sub = subscriptionRepo.findByIdForUpdate(subscriptionId);

        // Apply state machine transition — throws InvalidTransitionException for illegal events
        SubscriptionStatus nextStatus = stateMachine.transition(
            sub.getStatus(), SubscriptionEvent.PAYMENT_SUCCEEDED
        );

        sub.setStatus(nextStatus);
        sub.setLastChargeId(chargeResult.getChargeId());
        sub.setRetryCount(0);
        subscriptionRepo.save(sub);

        // Outbox row: same transaction — committed atomically with subscription update
        outboxRepo.insert(
            subscriptionId,
            "SUBSCRIPTION_ACTIVATED",
            buildOutboxPayload(subscriptionId, chargeResult)
        );
        // @Transactional commits here: both rows or neither
    }

    /**
     * Cancel a subscription — applies CANCEL_REQUESTED event via state machine.
     * No payment processor call — purely a state transition.
     */
    @Transactional
    public void cancel(UUID subscriptionId, UUID actorUserId) {
        Subscription sub = subscriptionRepo.findByIdForUpdate(subscriptionId);

        SubscriptionStatus nextStatus = stateMachine.transition(
            sub.getStatus(), SubscriptionEvent.CANCEL_REQUESTED
        );

        sub.setStatus(nextStatus);
        sub.setCancelledAt(Instant.now());
        subscriptionRepo.save(sub);

        outboxRepo.insert(
            subscriptionId,
            "SUBSCRIPTION_CANCELLED",
            buildCancelPayload(subscriptionId, actorUserId)
        );
    }
}
```

**OutboxProcessor — the background poller:**

```java
/**
 * Runs every 100ms as a Spring @Scheduled task.
 * Publishes unprocessed outbox rows to Kafka, then marks them processed.
 * At-least-once delivery: if it crashes between publish and markProcessed,
 * the row is re-published on next cycle — consumers MUST be idempotent.
 */
@Component
public class OutboxProcessor {

    private final OutboxRepository outboxRepo;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 100)
    public void processOutbox() {
        List<OutboxEvent> pending = outboxRepo.findUnprocessed(100);

        for (OutboxEvent event : pending) {
            try {
                // Partition key = aggregate id (subscription_id) → per-subscription ordering.
                // .get(...) blocks waiting for the Kafka ack before we mark the row processed.
                kafkaTemplate.send(
                    "subscription-events",
                    event.getAggregateId().toString(),
                    event.getPayload()
                ).get(5, TimeUnit.SECONDS);

                outboxRepo.markProcessed(event.getId());
            } catch (Exception e) {
                // Log and skip — will retry on next poll cycle
                log.error("Outbox publish failed for event {}, will retry", event.getId(), e);
            }
        }
    }
}
```

---

### 🔁 Concurrency — The Core Point

```
Two users cancel the same subscription at the same time:

Thread A                                Thread B
  findByIdForUpdate(sub_123)           findByIdForUpdate(sub_123)
    → Postgres: acquires row lock →       → Postgres: BLOCKS (waiting for lock)
  stateMachine.transition(ACTIVE, CANCEL_REQUESTED) → CANCELLED
  save(sub_123, status=CANCELLED)
  outboxRepo.insert(SUBSCRIPTION_CANCELLED)
  COMMIT  ← releases lock
                                          ← Postgres: lock acquired, reads committed state
                                          stateMachine.transition(CANCELLED, CANCEL_REQUESTED)
                                            → throws InvalidTransitionException
                                          @Transactional: rollback

Result: exactly one CANCELLED event published. No duplicate events.

KEY INVARIANT:
   The state machine + SELECT FOR UPDATE is the concurrency guard.
   No Java synchronized. The DB is the single source of truth.
```

---

### 🔬 LLD Interview Probes — B1 Specific

**Q: "Walk me through what happens if the server crashes after Stripe charges the card but before the DB transaction commits."**
> Stripe has already charged the card. The outbox row and subscription update were never committed — the DB rolled back. The client's idempotency key was never cached in Redis (that happens after the transaction). When the client retries (or when the renewal scheduler retries), it hits a Redis miss (idempotency key not there) and re-calls `paymentProcessor.charge()`. But Stripe is idempotent — if we pass the same `idempotencyKey` to Stripe in the request, Stripe recognizes it and returns the original `charge_id` without charging again. So: same idempotency key → same Stripe charge_id → same DB write. No double-charge. This is why the idempotency key flows all the way to the Stripe API call, not just the BillingService layer.

**Q: "Why is `persistPaymentAndOutbox()` a separate method from `charge()`? Why not make the entire charge() method @Transactional?"**
> Two reasons. First, Stripe's `charge()` call must be outside the transaction. If we include it inside, the DB transaction holds open while Stripe takes 1-2 seconds to respond. Postgres row lock is held for those 1-2 seconds — every other thread trying to touch this subscription row is blocked. Second, if Stripe times out and throws, a surrounding `@Transactional` would roll back the DB — even though Stripe may have actually charged the card (ambiguous timeout). Keeping Stripe outside the transaction avoids holding DB locks during external I/O.

**Q: "How do you prevent the renewal scheduler from charging the same subscription twice in one billing cycle?"**
> The renewal scheduler uses a unique constraint: `(subscription_id, billing_date)` in a `renewal_jobs` table. Before enqueueing a charge, it inserts a row. If the scheduler runs twice (pod restart, duplicate trigger), the second INSERT fails the unique constraint — the charge is never queued twice. The charge itself also carries an idempotency key: `"renewal:" + subscriptionId + ":" + billingDate` — so even if two charges somehow get enqueued, Stripe deduplicates on the idempotency key.

**Q: "What happens if the OutboxProcessor publishes to Kafka, then crashes before marking `processed_at`?"**
> The row remains `processed_at IS NULL`. On the next poll cycle, it's picked up again and published to Kafka again. This is at-least-once delivery — the downstream consumer (e.g., `EntitlementConsumer`) receives the event twice. This is why every consumer must be idempotent: before processing, check `SELECT 1 FROM processed_events WHERE event_id = ?`. If already processed, skip. The event_id in the Kafka message is the outbox row's UUID — stable across retries.

**Q: "If DocuSign adds a new downstream consumer — say, a Salesforce CRM sync consumer — what code changes are needed?"**
> Zero changes to BillingService. Zero changes to OutboxProcessor. Add one new Kafka consumer group: `SalesforceConsumer` with `@KafkaListener(topics="subscription-events", groupId="salesforce-sync")`. Each consumer group gets all events independently — Kafka offsets are per-group. This is the OCP (Open/Closed Principle) payoff: the billing system is closed to modification but open to extension via new consumer groups. That's the architectural argument for Kafka over sync HTTP: adding a downstream never touches billing code.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 2026 | **Audit pass — accuracy + 3 domain gaps closed.** (1) **Scale math reconciled:** Section 3 claimed "100K renewals/day = 1.2 writes/sec" and "~150 req/sec peak" while Section 4 derived 53K/day and 5,500 req/sec — three mutually contradictory numbers. Now one coherent set: 53K/day steady (0.6/sec), ~100K on the 1st-of-month peak day, jitter-spread to ~28 charges/sec (~85 row-writes/sec); the stale "6 writes/sec" figure removed from Trade-off 3 and Section 14. (2) **Stage 1 breaking points quantified** — previously narrative-only; now name the exhausted resource (client HTTP read timeout vs Stripe's ~1.8s P99), a number (~500 duplicate charges/day at 0.5% timeout on 100K peak-day charges), and an observable symptom. (3) **Proration rewritten** — the old single formula was upgrade/downgrade-agnostic and economically wrong; now shows the upgrade-immediate vs downgrade-at-period-end asymmetry, a worked $25→$50 mid-cycle example that balances, why a negative invoice forces deferral, plus integer-minor-units/rounding/currency-exponent rules. (4) **Stripe webhooks added** (was entirely absent — the biggest gap for a Commerce Backend interview): `POST /v1/webhooks/stripe` endpoint + story, new Deep Dive 3 covering the out-of-order problem with a Picture+Invariant visual, three-layer defence (HMAC on raw body / UNIQUE `event_id` dedup / `provider_version` guard), and the nightly reconciliation backstop. (5) **Invoice immutability + credit-note pattern** added to Section 11 — finalized invoices are never UPDATEd; the proration credit *is* a credit note. (6) **State machine bug fixed** — `TRANSITIONS` had no `ACTIVE:PAYMENT_SUCCEEDED` row, so every monthly renewal would have thrown `InvalidTransitionException`; added that plus `PAST_DUE:PAYMENT_FAILED` and `PLAN_CHANGED`. (7) **Dunning ladder corrected** — Day 0/3/5/7 has *shrinking* gaps and was mislabelled exponential backoff; now Day 0/1/3/7 (gaps 1→2→4), and the FR's "3 retries" reconciled to 4; probe answer no longer conflates the Stripe-429 transport retry with the 402-decline dunning ladder. (8) Every 4xx now has a named trigger in the Endpoint Stories; `POST /v1/payments` distinguishes 201 (first charge) from 200 (idempotent replay) and names the 409 trigger (same key, different body). (9) Section 14 Testability/Usability/Security/Availability cells rewritten to cite Section 4 numbers and specific DocuSign consequences. (10) Schema: added `provider_events` table and `subscriptions.provider_version`; end-of-line comments in the Kafka blocks moved above the statements per AGENTS.md. |
| Jul 5, 2026 | File created. Full 15-section 60-min interview-ready solution. Type B (Product Architecture). PDF-confirmed question. Covers: 3-stage progressive HLD (sync/no-idempotency → state machine + sync downstream → outbox + Kafka fanout), 3 decision tables, full SQL schema (7 tables with indexes), SOLID breakdown for DocuSign, Tier 1/2/3 probe answers, 5 common mistakes. Cross-refs verified against actual SystemDesignConcepts files. |
