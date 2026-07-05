# B1 — Design a Subscription Billing API

> **Read `solution-notes-standards.md` first.** This file is a 60-minute interview answer framework, not a concept reference. Concept-level depth lives in `SystemDesignConcepts/`.

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
- Failed payments enter a grace period (`PAST_DUE`) with up to 3 retry attempts over 7 days before cancellation
- Organizations can cancel a subscription; access continues until end of current period
- Downstream systems (entitlement, notification, analytics) are notified on every subscription lifecycle event
- Payment API must be idempotent — retrying a failed payment never double-charges

**Out of scope today:**
- Usage-based / metered billing (per-document, per-API-call)
- Invoice PDF generation
- Multi-currency pricing
- Tax calculation (Avalara integration)

### Non-Functional Requirements

- **Scale:** DocuSign has ~1.6M paying customers; ~100K renewals/day = ~1.2 writes/sec (renewal) + read-heavy entitlement checks (~150 req/sec peak)
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
| POST | /v1/payments | JWT Bearer + `Idempotency-Key` header | `{subscription_id, amount_cents, currency_code}` | `{payment_id, charge_id, status}` | 200, 400, 402, 409 |
| GET | /v1/subscriptions/{id}/payments | JWT Bearer | — | `[{payment_id, amount, status, created_at}]` (paginated) | 200, 404 |

---

### 🔍 Endpoint Stories — Why Each One Exists

**`POST /v1/subscriptions`** — Entry point for a new customer. The `409` matters: if an org already has an active subscription, a second POST must conflict, not silently create a duplicate. Without it you'd have two active subscriptions for one org with no way to enforce the one-per-org rule.

**`GET /v1/subscriptions/{id}`** — This is the entitlement check endpoint. It's called 5,500 times/sec at peak — every DocuSign API request verifies the org has an active plan. Don't let the simplicity fool you. The interviewer will ask "how does this scale?" Answer: Redis cache, TTL, not a DB read. This is the highest-traffic endpoint in the system.

**`PUT /v1/subscriptions/{id}/plan`** — Plan upgrade/downgrade. The response returns `proration_credit_cents` so the UI can show the customer exactly what they'll pay next cycle before they confirm. If you return only a bare `200`, the customer has no visibility into the financial impact — bad UX and a likely follow-up probe. The `409` handles the race: two org admins trying to change the plan simultaneously.

**`DELETE /v1/subscriptions/{id}`** — Cancel. Returns `access_until` because the subscription isn't destroyed immediately — the org retains access until period end. The interviewer will ask "why not `204`?" Because the caller needs to know when access ends to update the UI and communicate to the customer.

**`POST /v1/payments`** — The most critical endpoint. Charges Stripe. The `Idempotency-Key` header is the entire reason this is safe to retry. The `402` status code (Payment Required) is specifically for "gateway declined your card" — distinct from `400` (malformed request) and `500` (system failure). Name this distinction; most candidates collapse card declines into a 400 and lose a point.

**`GET /v1/subscriptions/{id}/payments`** — Payment history, paginated. Cursor-based, not offset — an org could have years of payment records and `OFFSET 10000` scans 10K rows on every page. The nested path `/subscriptions/{id}/payments` makes ownership clear: these payments belong to this subscription.

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

**Proration formula (plan upgrade):**
```
proration_credit = old_plan_price × (days_remaining / days_in_cycle)
new_invoice = new_plan_price - proration_credit
```
This credit is applied on the next invoice, not as an immediate refund. Simpler to implement; matches Stripe's default behavior.

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
  - Renewals/day (steady state): 1.6M ÷ 30 = ~53K/day = ~0.6/sec
  - Renewals peak (3-day cluster): 53K × 3 = 159K in 3 days, spread over 8 working hours = ~5.5/sec peak
  - New subscription creates: assume 5K/day = 0.06/sec (negligible)
  - Payment API writes: ~0.6/sec steady, ~6/sec peak (safe for a single Postgres writer)

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
  - Inbound (payment requests): 6 req/sec × 2 KB = 12 KB/sec — negligible
  - Outbound (entitlement reads): 5,500 req/sec × 1 KB = 5.5 MB/sec — handled by cache hit, not DB

Key conclusions:
  - "At 6 writes/sec peak, a single Postgres primary handles writes comfortably — no sharding needed."
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


  BREAKING POINT 1: Client times out at step 1 → retries POST /payments
    → BillingService charges Stripe AGAIN → double-charge.
    No idempotency key = real money lost.

  BREAKING POINT 2: Entitlement service is down at step 3
    → Payment succeeds, status='ACTIVE', but user can't access DocuSign.
    BillingService has no rollback for a completed Stripe charge.
    Support ticket volume spikes; manual intervention required.

  BREAKING POINT 3: Adding a fourth downstream (analytics) requires
    touching BillingService code → OCP (Open/Closed Principle) violated.

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
    ACTIVE  ──[payment_failed]────► PAST_DUE
    PAST_DUE──[payment_succeeded]──► ACTIVE
    PAST_DUE──[retries_exhausted]──► CANCELLED
    ACTIVE  ──[cancelled]──────────► CANCELLED
    ACTIVE  ──[upgraded]───────────► ACTIVE (new plan_id)

  > 📖 Full: `SystemDesignConcepts/Production-Grade/System-Design-Patterns/49-state-machines-workflows.md`

  BREAKING POINT 1: Idempotency is now safe — duplicate retries
    hit Redis and return the same 200. But state is still correct.

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

Pick these two — they are the riskiest components and the ones DocuSign interviewers push on most.

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
            // Publish to Kafka — at-least-once (Kafka producer acks=all)
            kafkaTemplate.send(
                "subscription-events",
                event.getAggregateId().toString(),    // partition key: subscription_id
                event.getPayload()
            ).get(5, TimeUnit.SECONDS);               // block for ack

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

### Deep Dive 3 (if time permits): Renewal Scheduler

**Why this matters:**
100K renewals/day cannot run as a cron job that fires at midnight and tries to charge 100K subscriptions simultaneously. That's a thundering herd — Stripe rate limits, DB lock contention, and memory pressure all spike at once.

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

**Why random jitter:** spreading 100K renewals over 60 minutes = ~27/sec instead of 100K/second spike. Stripe's API rate limit (100 req/sec per account) is not breached.

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
```

### Key Schema Decisions

- **`subscription_events` is append-only:** No UPDATE or DELETE ever. This is the SOC 2 audit trail. DB grants for the billing service role: INSERT only, never UPDATE/DELETE on this table.
- **`status` CHECK constraint:** The DB enforces the valid set of states. The application state machine enforces transitions. Defense in depth — a bug that writes an invalid status fails at the DB layer.
- **`pending_plan_id`:** Plan downgrade at period end. Renewal job reads `pending_plan_id`, applies the new plan at `current_period_end`, clears the column. Avoids complexity of immediate proration credits.
- **`outbox.processed_at` partial index:** Only unprocessed rows appear in the OutboxProcessor query. As processed rows are cleaned up, the index stays tiny — no performance degradation over time.
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
- **Gain:** Simpler operations; no cross-shard transaction complexity; no re-sharding risk; 6 writes/sec (steady state) is trivially within single Postgres primary capacity (can handle ~5,000 writes/sec)
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
Day 0:   Payment fails → PAST_DUE → retry #1 immediately
Day 3:   retry #2 (exponential backoff)
Day 5:   retry #3
Day 7:   retry #4 (final attempt) + warning email to org admin
Day 8:   retries_exhausted → CANCELLED → access revoked → churn event published
```

**Implementation detail:** `retry_count` in the `subscriptions` table tracks attempts. Each retry is scheduled as a `renewal_jobs` row with its future `scheduled_at`. If a retry succeeds, the subscription reverts to `ACTIVE` and `retry_count` resets to 0. The dunning schedule is a configuration parameter (not hardcoded) — finance teams adjust it based on customer segment (enterprise gets longer grace periods).

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
> The renewal scheduler uses random jitter (0–60 minutes) to spread 100K renewals over the hour rather than spiking at midnight. This limits peak Stripe throughput to ~28 req/sec (100K ÷ 3,600 seconds). Stripe's rate limit is 100 req/sec per account — we stay comfortably below. For failed renewals (Stripe returns 429), the worker retries with exponential backoff: 5s, 10s, 30s. Retries exceeding 3 attempts increment `subscriptions.retry_count` and transition to `PAST_DUE` state.

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

---

## Section 14 — 🧭 DocuSign Dimensions Checklist

| Dimension | Relevant? | How this design addresses it |
|---|---|---|
| Testability | ✅ | `IPaymentProcessor` interface enables unit tests with a mock processor (no Stripe calls in tests); outbox processor is a pure function — given a list of outbox rows, assert Kafka calls and `processed_at` updates; state machine transitions are pure functions — input state + event → output state |
| Usability | ✅ | Standard REST API; `Idempotency-Key` header follows Stripe's widely-adopted convention; error responses include `decline_code` and `request_id` for debugging; proration amount is returned on plan change response so the UI can show the user exactly what they'll pay |
| Extensibility | ✅ | Adding a new downstream consumer (Salesforce CRM sync) = new Kafka consumer group, zero changes to BillingService (OCP); adding a new plan type = new row in `subscription_plans`, zero code changes; swapping Stripe for Braintree = new `IPaymentProcessor` implementation, injected via Spring bean |
| Security | ✅ | JWT Bearer on all endpoints; admin role check on mutation endpoints (plan change, cancel); Stripe customer ID never exposed in API responses; `subscription_events` is INSERT-only (no mutation, no deletion) to prevent audit trail tampering; idempotency keys are tenant-scoped (key from one tenant cannot satisfy request from another) |
| Availability | ✅ | Postgres Multi-AZ (RDS) for automatic failover; Redis Sentinel for idempotency cache HA; Kafka replication factor 3; renewal job is idempotent — can be restarted safely; outbox processor restart re-processes any unpublished events |
| Scalability | ✅ | Write path: 6 req/sec peak → single Postgres primary handles this trivially; Read path: 5,500 req/sec entitlement checks → Redis cache with TTL (not DB reads); Renewal: jitter spreads load over 60 minutes; horizontal scaling by adding BillingService pods (stateless) behind load balancer |
| Observability & Traceability | ✅ | `request_id` in every API response; `subscription_events` table is a complete state history for any subscription; Kafka consumer lag metric (alert when EntitlementConsumer lag > 10s); outbox row age metric (alert when unprocessed rows older than 30s); payment failure rate by `decline_code` (Grafana dashboard) |

---

## Section 15 — 🧾 TL;DR Answer Summary

> "I'd design a flat-rate subscription billing API using three key components working together: a state machine that owns all subscription lifecycle transitions (PENDING → ACTIVE → PAST_DUE → CANCELLED), a Redis-backed idempotency layer on the payment endpoint to prevent double-charges on retries, and an outbox pattern that atomically writes both the subscription update and a Kafka event in one DB transaction — so downstream entitlement, notification, and analytics services consume events independently without tight coupling to the payment path. The renewal scheduler uses random jitter to spread 100K renewals over 60 minutes, avoiding a thundering herd against Stripe's rate limits. For DocuSign specifically, I'd apply SOLID principles at the API layer — the billing service depends on an IPaymentProcessor interface (not Stripe directly), downstream consumers are added as new Kafka consumer groups without touching billing code (OCP), and the subscription_events table is INSERT-only for SOC 2 audit compliance."

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 5, 2026 | File created. Full 15-section 60-min interview-ready solution. Type B (Product Architecture). PDF-confirmed question. Covers: 3-stage progressive HLD (sync/no-idempotency → state machine + sync downstream → outbox + Kafka fanout), 3 decision tables, full SQL schema (7 tables with indexes), SOLID breakdown for DocuSign, Tier 1/2/3 probe answers, 5 common mistakes. Cross-refs verified against actual SystemDesignConcepts files. |
