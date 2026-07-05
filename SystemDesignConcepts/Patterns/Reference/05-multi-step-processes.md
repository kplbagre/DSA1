# Multi-step Processes — Quick Reference

> **Read this:** 30 min before an interview involving distributed workflows, order flows, or multi-service transactions.
> **Deep study:** `DeepDive/05-multi-step-processes.md`

---

## 🎯 What kind of problem is this?

Use this pattern when: **a business operation spans multiple services, each with its own DB, and partial failure must be handled without data corruption** — you can't use a single DB transaction.

Trigger words: "e-commerce order flow", "payment + inventory + notification", "flight booking", "bank transfer", "distributed transaction", "what if payment succeeds but inventory fails", "saga", "compensating transaction".

---

## 🧭 Decision Sequence

```
START: Business operation spans multiple services

Step 1 → How many steps and services?
         2 steps, 2 services, linear?
               → Choreography (simple event chain). Low overhead.
         3+ steps, or branching, or parallel steps?
               → Orchestration. Complexity needs a coordinator.
         Long-running (hours/days) or needs human approval gates?
               → Durable Execution Engine (Temporal / AWS Step Functions).

Step 2 → Do you need visibility into workflow state?
         Yes (ops team needs to see "order 123 is at step 3 of 5, failed at inventory")
               → Orchestration (orchestrator persists state in its DB).
         No (fire-and-forget, eventual consistency OK)
               → Choreography (simpler, no state store needed).

Step 3 → How do you guarantee event emission after DB commit?
         Always → Outbox pattern. Every service in the saga must use it.
         Skipping this = "dual write" problem = events lost on crash.

Step 4 → How do you handle partial failures?
         Orchestration: orchestrator detects failure, sends compensating commands in reverse.
         Choreography: services publish failure events; upstream services compensate.
         Both: compensating transactions must be idempotent (safe to run twice).
```

---

## ⚡ Strategies at a Glance

| Strategy | Use when | Don't use when |
|---|---|---|
| **Saga Orchestration** | 3+ steps, branching, auditability required, payment flows | Simple 2-step linear flows |
| **Saga Choreography** | Simple linear flows, team wants decoupling | Complex branching — event chains become untraceable |
| **Outbox Pattern** | Anytime you write to DB and publish to Kafka | Never skip this — dual-write problem is silent data loss |
| **Durable Execution (Temporal)** | Hours/days-long workflows, human-in-the-loop, complex retry logic | Simple sagas; team unwilling to run Temporal cluster |

**Key numbers to remember:**
- 2PC (distributed transactions) is a non-starter: all participants block for coordinator duration
- Compensating transactions must be idempotent — orchestrator will retry them on crash
- Outbox pattern: same DB transaction writes business record + outbox event → at-least-once to Kafka
- Orchestrator persists state after every step → crash-safe resume from last completed step

---

## 🎨 Key Architecture Diagram

```
         Client
           │  POST /orders
           ▼
    ┌─────────────────┐    persists saga state
    │  Order Service  │──────────────────────────▶ ┌────────────────┐
    │  (triggers saga)│                             │  Saga State DB │
    └────────┬────────┘                             │  (Postgres)    │
             │                                      └────────────────┘
             ▼                                           ▲ (read/write after each step)
    ┌─────────────────────────────────────────────────────────────────┐
    │                    Saga Orchestrator                             │
    │  Current step: PAYMENT_CHARGING                                 │
    │  Completed: []   Pending: [payment, inventory, notification]    │
    └──────────┬──────────────────────┬──────────────────────────────┘
               │ command              │ command (after step 1 OK)
    ┌──────────▼──────┐    ┌──────────▼──────────┐    ┌──────────────────┐
    │ Payment Service  │    │ Inventory Service    │    │ Notif. Service   │
    │ charge $50       │    │ reserve 1x item      │    │ send email       │
    │ compensate:      │    │ compensate:          │    │ compensate:      │
    │   refund $50     │    │   release reservation│    │   (none needed)  │
    └──────────────────┘    └─────────────────────┘    └──────────────────┘

Failure at Inventory:
    Orchestrator ──▶ Payment: "refund $50" (compensating command)
    Orchestrator records state: FAILED
    Client receives: 409 Conflict (out of stock)

KEY INVARIANT:
   Each local service is ACID within itself.
   The saga is eventually consistent across services.
   Compensating transactions restore consistency on failure.
   Orchestrator state persists after each step → crash-safe resume.
```

---

## 🔬 Interview Q&A

### Q: "Why not use distributed transactions (2PC) across services?"

> Two-Phase Commit requires a coordinator to hold locks on all participants until all agree. In a distributed system: (1) Lock duration = slowest participant's response time — if Payment Service takes 500ms, all participants block for 500ms. (2) If the coordinator crashes after sending "prepare" but before "commit," all participants hold locks indefinitely — the system is stuck. (3) 2PC doesn't work across heterogeneous systems (different DBs, external APIs). Saga trades ACID guarantees for availability: each local transaction commits immediately, compensating transactions handle rollback. The business process is eventually consistent, not atomically consistent.

---

### Q: "What's a compensating transaction? Give an example."

> A compensating transaction is a business operation that semantically undoes a previous step. It's not a DB rollback — the original transaction already committed. Example: "charge payment" is the forward transaction. "Refund payment" is the compensating transaction. Key property: compensating transactions must be idempotent — if the orchestrator crashes and retries the compensating command twice, running "refund $50" twice should not refund $100. Fix: use idempotency keys. Idempotency key = saga_id + step_id. Payment Service checks: if refund with this key already processed, return success without doing it again.

---

### Q: "Orchestration vs choreography — which do you recommend for a payment flow?"

> Orchestration for payment flows, definitively. Payment flows have branching logic (handle payment declined differently from network timeout), strict ordering requirements (never reserve inventory before charging), and require auditability (ops team needs to see exactly which step failed and why). Choreography's event chains across payment, inventory, warehouse, and notification become extremely hard to trace when something goes wrong. The debugging cost of choreography at that complexity pays for the overhead of an orchestrator in the first two production incidents. Use choreography only for simple linear flows where decoupling is the primary concern.

---

### Q: "What is the dual write problem and how does the Outbox pattern solve it?"

> Dual write: service writes to its DB, then separately writes to Kafka (or any external system). These are two separate I/O operations — not atomic. If the service crashes between the DB write and the Kafka publish, the DB has the record but the event is never emitted. Downstream services never react. Business state is inconsistent. Outbox solution: write the business record AND the event to be published into the SAME local DB transaction. Both commit or both rollback. A separate process reads the outbox table and publishes to Kafka. Crash safety: if the service crashes, the outbox record survived the DB commit and will be published on next run. At-least-once delivery — make consumers idempotent.

---

### Q: "How do you handle a compensating transaction failing? (The refund itself fails)"

> This is the hardest problem in distributed sagas. Options: (1) Retry the compensating transaction with exponential backoff — most transient failures resolve. (2) Mark the saga as `COMPENSATION_FAILED` and alert a human operator (SRE or finance team). Some failures require manual intervention (bank rejects refund; customer must be contacted). (3) Dead letter queue — failed compensating commands go to a DLQ; human processes them. The key point: you cannot have a compensating transaction that has no escape hatch. Every saga must define what happens when compensation itself fails. This is a business decision as much as a technical one.

---

### Q: "How do you ensure idempotency in a saga where steps may be retried?"

> Every step in the saga must be idempotent: running it twice has the same effect as running it once. Implementation: (1) Pass an idempotency key with every command (saga_id + step_number). (2) Each service checks: has this key been processed before? If yes, return the previous result. If no, process and store the key + result. (3) Store processed keys in a DB table with a unique constraint on the key. The `INSERT ... ON CONFLICT DO NOTHING` pattern works well. (4) Idempotency keys expire after a reasonable window (e.g., 24 hours) — sagas don't run for days.

---

### Q: "How does a saga differ from a state machine? Are they the same?"

> A saga is often implemented as a state machine, but they're different concepts. A state machine defines states and transitions. A saga is a specific pattern for managing distributed transactions with compensations. The saga orchestrator is typically implemented as a state machine: `STARTED → PAYMENT_CHARGED → INVENTORY_RESERVED → COMPLETED` with failure states and compensation states. But a state machine is also used for simpler things (order status: pending → confirmed → shipped → delivered) that don't involve distributed transactions. Every saga can be implemented as a state machine; not every state machine is a saga.

---

### Q: "Design the order flow for an e-commerce system with payment, inventory, and notification."

> Use orchestrated saga: (1) Order Service receives order request, writes to orders table + outbox event, starts saga. (2) Saga Orchestrator persists state, sends "charge payment" to Payment Service. (3) Payment success → Orchestrator sends "reserve inventory" to Inventory Service. (4a) Inventory success → Orchestrator sends "send confirmation" to Notification Service → saga COMPLETE. (4b) Inventory failure → Orchestrator sends "refund payment" to Payment Service → saga FAILED with clean rollback. (5) Each service uses Outbox pattern to reliably emit completion events. (6) All steps use idempotency keys (orderId + stepName). Client gets 202 Accepted immediately; order status polled or pushed via WebSocket.

---

### Q: "What happens to an in-flight saga when the orchestrator restarts?"

> This is why orchestrators persist saga state to a DB after every step. On restart, orchestrator reads all sagas in non-terminal states (`STARTED`, `PAYMENT_CHARGED`, `INVENTORY_RESERVED`, etc.) and resumes from the last completed step. This requires that all steps are idempotent (the orchestrator might re-send a command that was already processed if it crashed after sending but before recording the response). Combined with idempotency keys on each participant, restart recovery is seamless — the saga picks up exactly where it left off.

---

## ⚠️ Anti-patterns (don't say these)

- **Choreography for complex workflows** — 5+ services with branching = distributed spaghetti; debugging step 4 of 7 across 7 Kafka topics is a production nightmare
- **Non-idempotent compensating transactions** — orchestrator retries; "refund $50" running twice = $100 refunded; use idempotency keys on every command
- **Business logic in the orchestrator** — orchestrator is a thin coordinator; business logic belongs in participant services; God-orchestrator = the single point of failure you avoided by distributing

---

## 🧩 Common Interview Problems

| Problem | Approach | Key decision |
|---|---|---|
| Design E-commerce Order Flow | Orchestrated Saga | Payment + Inventory + Notification must all succeed or all rollback |
| Design Flight Booking | Orchestrated Saga | Seat reservation + payment + ticket issuance |
| Design Bank Transfer | Local ACID or orchestrated saga | Single DB = ACID transaction; cross-service = saga |
| Design Expense Report Approval | Orchestrated Saga with human steps | Async approval steps with timeout handling |
| Design User Signup | Choreography | Simple linear, OK to retry each step independently |

---

## 🔗 Full notes

`DeepDive/05-multi-step-processes.md` — 2PC vs Saga trade-offs, outbox pattern mechanics, full failure mode Q&A
