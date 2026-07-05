# Pattern Deep Dive: Multi-step Processes

> **Read this when:** You need to understand how to coordinate a business workflow that spans multiple services — each with its own state — where partial failure must be handled gracefully without data corruption.
> **Pre-interview refresh:** Use `Reference/05-multi-step-processes.md` (5 min).

---

## 🎯 What Problem Does This Pattern Solve?

A business operation requires multiple steps across multiple services. Each step succeeds or fails independently. If step 3 fails, steps 1 and 2 have already committed — and you need to undo them.

Classic examples:
- **E-commerce order:** charge payment → reserve inventory → notify warehouse → send confirmation email
- **Flight booking:** reserve seat → charge card → issue ticket → notify airline
- **Bank transfer:** debit account A → credit account B → record audit log
- **User signup:** create account → send verification email → provision default settings → notify analytics

The challenge is not speed — it's **correctness under failure**. Any step can fail at any moment (network timeout, service down, DB error). The system must end up in a consistent state: either all steps completed, or all steps rolled back cleanly.

A single DB transaction solves this for one service. Across multiple services, you can't use a single DB transaction — you'd need a distributed transaction (2PC), and those are notoriously slow, complex, and fragile.

---

## 💡 Core Insight

**Distributed transactions don't work at scale.** Two-Phase Commit (2PC — a protocol where all participants must agree before committing, requiring a coordinator to hold locks across all services) blocks all participants for the duration of the protocol, creates a single point of failure (the coordinator), and fails badly when any participant is unavailable.

The production answer is the **Saga pattern**: break the transaction into a sequence of local transactions. Each local transaction commits immediately. If a later step fails, run **compensating transactions** (the undo operations) in reverse order to restore consistency.

There are two ways to coordinate a Saga:
- **Orchestration**: A central coordinator (Saga Orchestrator) directs each step and handles failures
- **Choreography**: Services emit events; downstream services react to events (no central coordinator)

> **KEY INSIGHT:** "Give up ACID across services. Use local ACID + compensating transactions. Choose orchestration (explicit, debuggable) or choreography (decoupled, complex) based on workflow complexity."

---

## 🗂️ The 4 Approaches

---

### Approach 1 — Saga Orchestration

🧠 **Mental model:** Uber ride order — charge card → assign driver → track route → complete payment. A central Orchestrator directs each step and knows the current state. If driver assignment fails, it sends a card refund command. Every step's outcome is visible in one place.

A dedicated Saga Orchestrator service directs the workflow. It sends commands to each participant service and receives success/failure responses. On failure, it sends compensating commands in reverse order.

**When to use:**
- Complex workflows with many steps and branching logic
- Need visibility into workflow state (what step is it on? what failed?)
- Centralized error handling and retry logic
- Teams need a single place to understand the flow

**When NOT to use:**
- Simple two-step workflows (overkill)
- When coupling through a central coordinator is unacceptable
- High throughput workflows where the orchestrator becomes a bottleneck

**How it works:**

**Steps in plain English:**
1. **Initiate** — Client calls Saga Orchestrator to start the workflow.
2. **Step 1 command** — Orchestrator sends "charge payment" command to Payment Service.
3. **Step 1 success** — Payment Service commits locally, replies success. Orchestrator records state.
4. **Step 2 command** — Orchestrator sends "reserve inventory" to Inventory Service.
5. **Step 2 failure** — Inventory Service is out of stock, replies failure.
6. **Compensate** — Orchestrator sends "refund payment" compensating command to Payment Service.
7. **Final state** — Workflow ends in a clean failed state. Payment refunded. No partial commit.

```
                    ┌──────────────────────────────┐
                    │      Saga Orchestrator         │
                    │  (tracks state in its own DB) │
                    └────┬──────────┬───────────────┘
                         │          │
             ┌───────────▼──┐  ┌────▼──────────────┐
             │   Payment     │  │   Inventory        │
             │   Service     │  │   Service          │
             └───────────────┘  └────────────────────┘

Happy path:
Orchestrator ──"charge $50"──▶ Payment   ──"OK"──▶ Orchestrator
Orchestrator ──"reserve item"──▶ Inventory ──"OK"──▶ Orchestrator
Orchestrator ──"notify WH"──▶  Warehouse  ──"OK"──▶ Orchestrator
→ Workflow COMPLETE

Failure path (Inventory out of stock):
Orchestrator ──"charge $50"──────▶ Payment   ──"OK"──▶ Orchestrator
Orchestrator ──"reserve item"──▶   Inventory ──"FAIL (out of stock)"──▶ Orchestrator
Orchestrator ──"refund $50"──────▶ Payment   ──"OK"──▶ Orchestrator
→ Workflow FAILED (cleanly — no dangling charge)
```

**Orchestrator state machine:**

```
States: STARTED → PAYMENT_CHARGED → INVENTORY_RESERVED → NOTIFIED → COMPLETED
                                  ↘ INVENTORY_FAILED → PAYMENT_REFUNDED → FAILED

Persisted in DB after each step → orchestrator can crash and resume from last state.
```

---

### Approach 2 — Saga Choreography

🧠 **Mental model:** GitHub Actions pipeline — push code (event) → tests run automatically (react) → on test success, build triggers (react) → on build success, deploy triggers (react). No central director — each stage reacts to the previous event.

No central coordinator. Each service emits events when it completes its step. Downstream services listen for events and react. Failure triggers a compensating event that propagates in reverse.

**When to use:**
- Simple linear workflows (few steps, no branching)
- Teams want maximum decoupling between services
- Prefer event-driven architecture throughout
- Don't want a central coordinator service to maintain

**When NOT to use:**
- Complex workflows with branching, parallel steps, or many participants — event chains become impossible to trace
- Need visibility into current workflow state (choreography has no central view)
- Debugging distributed failures (following event chains across 7 services is painful)

**How it works:**

**Steps in plain English:**
1. **Order placed** — Order Service publishes `OrderPlaced` event to Kafka.
2. **Payment reacts** — Payment Service consumes `OrderPlaced`, charges card, publishes `PaymentCharged`.
3. **Inventory reacts** — Inventory Service consumes `PaymentCharged`, reserves stock, publishes `InventoryReserved`.
4. **Failure** — Inventory is out of stock, publishes `InventoryFailed`.
5. **Compensate** — Payment Service consumes `InventoryFailed`, issues refund, publishes `PaymentRefunded`.

```
Order Service   Kafka Topic      Payment Svc    Inventory Svc
     │                │               │               │
     │──OrderPlaced──▶│               │               │
     │                │──OrderPlaced─▶│               │
     │                │               │──PaymentCharged──▶│
     │                │               │               │──InventoryReserved (success)
     │                │               │               │
     │         ─ ─ ─ ─Failure path─ ─ ─ ─ ─ ─ ─ ─ ─ ─│
     │                │               │               │
     │                │               │   InventoryFailed
     │                │◀──InventoryFailed─────────────│
     │                │──InventoryFailed──▶│           │
     │                │               │ (refund)       │
     │                │◀──PaymentRefunded─┤            │
```

**Key challenge:** Cyclic event dependencies. If Payment listens to Inventory events and Inventory listens to Payment events for compensations, the event graph becomes hard to reason about. Keep choreography for linear flows only.

---

### Approach 3 — Outbox Pattern (Reliable Event Emission)

🧠 **Mental model:** You write a letter AND immediately log it in your sent-mail ledger — both in one pen stroke. If you crash afterward, the mail is still logged and will be delivered when you recover. Outbox = same DB transaction for business write + event record.

Not a complete saga coordination approach, but an essential building block for both orchestration and choreography: ensuring that a local DB commit and an event emission are **atomic**.

**The problem it solves:** Service A commits to its DB and then publishes to Kafka. But what if the service crashes between the DB commit and the Kafka publish? DB has the change; Kafka doesn't. Downstream services never react. Inconsistency.

**How it works:**

**Steps in plain English:**
1. **Same transaction** — Service A writes business record AND an outbox event record in the same DB transaction. Both commit atomically.
2. **Outbox poller** — A background process (or CDC/Debezium) reads the outbox table and publishes events to Kafka.
3. **Mark sent** — After successful Kafka publish, mark the outbox record as sent (or delete it).
4. **Crash safety** — If service crashes before publishing, the outbox record survives. Poller retries on restart.

```
              ┌──────────────────────────────────────────────┐
              │  Single DB Transaction                        │
              │  INSERT INTO orders (...)                     │
              │  INSERT INTO outbox (event_type, payload, ...) │
              └──────────────────────────────────────────────┘
                                   │
                         ┌─────────▼──────────┐
                         │  Outbox Poller /    │
                         │  CDC (Debezium)     │
                         └─────────┬──────────┘
                                   │ publishes to Kafka
                         ┌─────────▼──────────┐
                         │  Kafka Topic        │
                         └─────────────────────┘

Guarantee: If DB committed, event WILL eventually reach Kafka (at-least-once).
No event is lost due to crash between DB commit and Kafka publish.
```

---

### Approach 4 — Durable Execution Engines (Temporal / AWS Step Functions)

Framework-level saga coordination where the workflow code itself is made durable — if the process crashes mid-execution, it replays from the last checkpoint automatically. You write workflow logic as ordinary procedural code; the engine handles state persistence, retries, timeouts, and crash recovery.

**When to use:**
- Long-running workflows (minutes to days) with human-in-the-loop steps (approval gates, bank confirmations)
- Complex retry logic and timeouts that would require enormous boilerplate in manual orchestration
- You need workflow versioning — deploy new logic without breaking in-flight instances

**When NOT to use:**
- Simple 2–3 step saga (orchestration or choreography is lighter)
- Team doesn't want to operate another infrastructure dependency (Temporal requires a cluster)

**Key concepts:**
- **Activities** — individual units of work (charge payment, reserve inventory). Auto-retried on failure with configurable backoff.
- **Signals** — external events that wake a waiting workflow (user approves, webhook arrives, bank confirms). The human-in-the-loop primitive.
- **Continue-as-New** — for indefinitely running workflows (subscription billing, recurring reminders), periodically snapshot and restart event history to prevent unbounded growth.

```
Temporal workflow (conceptual):

workflow OrderFlow(order):
    result = await charge_payment(order)       # retried automatically on failure
    if result == DECLINED:
        return FAILED
    inv = await reserve_inventory(order)       # retried automatically on failure
    if inv == OUT_OF_STOCK:
        await refund_payment(order)            # compensating activity
        return FAILED
    await send_confirmation(order)
    return COMPLETED

Durable: crash between any two lines → replay from last checkpoint.
No manual state machine, no outbox table, no retry loop — engine handles all of it.
```

🧠 **Mental model:** Stripe payment reconciliation — a workflow that runs for days, waiting for bank confirmations, retrying failed webhooks, escalating to human review if needed. Manual saga orchestration code for this would be hundreds of lines of state management boilerplate. Temporal makes it look like a 20-line function that just doesn't crash.

---

## 🧭 Decision Sequence

```
START: Business operation spans multiple services

Step 1 ── How many steps and services?
          2 steps, 2 services, linear?
                → Choreography (simple event chain). Low overhead.
          3+ steps, or branching, or parallel steps?
                → Orchestration. Complexity needs a coordinator.
          Long-running (hours/days) or needs human approval gates?
                → Durable Execution Engine (Temporal / Step Functions).

Step 2 ── Do you need visibility into workflow state?
          Yes (ops team needs to see "order 123 is at step 3 of 5, failed at inventory")
                → Orchestration (orchestrator persists state in its DB).
          No (fire-and-forget, eventual consistency OK)
                → Choreography (simpler, no state store needed).

Step 3 ── How do you guarantee event emission after DB commit?
          Always → Outbox pattern. Every service in the saga must use it.
          Skipping this = "dual write" problem = events lost on crash.

Step 4 ── How do you handle partial failures?
          Orchestration: orchestrator detects failure, sends compensating commands in reverse.
          Choreography: services publish failure events; upstream services consume and compensate.
          Both: compensating transactions must be idempotent (safe to run twice).
```

---

## 🎨 Visual — Orchestrated Saga (Full Architecture)

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

## ⚠️ Anti-patterns

- **Using choreography for complex workflows.** Choreography works for 2–3 services in a linear chain. For 5+ services with branching (payment declined → notify customer vs retry vs escalate), the event graph becomes a distributed spaghetti that's impossible to trace in production. When a saga fails at step 4 of 7 in a choreographed flow, you're reading 7 different Kafka topics and 5 different service logs to understand what happened. The debugging cost is enormous. Use orchestration for anything non-trivial.

- **Not making compensating transactions idempotent.** The orchestrator will retry failed compensating commands. If "refund $50" is not idempotent and runs twice, the customer gets $100 refunded. Use idempotency keys on every command — both forward transactions and compensating transactions. This is non-negotiable.

- **Putting too much logic in the orchestrator.** The orchestrator should be a thin coordinator: it knows the sequence of steps and how to compensate, but contains no business logic. Business logic (how to calculate a refund, how to reserve inventory) belongs in the participant services. An orchestrator that grows to contain business rules becomes a God service — the single point of failure you were trying to avoid by distributing in the first place.

---

## 🗺️ Problems Map

| Interview Problem | Why Multi-step Processes Applies | Approach |
|---|---|---|
| Design E-commerce Order Flow | Payment + Inventory + Notification must all succeed or all rollback | Orchestrated Saga |
| Design Flight Booking | Seat reservation + payment + ticket issuance | Orchestrated Saga |
| Design Bank Transfer | Debit + Credit + audit log (two accounts, one bank) | Local ACID or orchestrated saga if cross-service |
| Design DocuSign (document signing) | Signature → record → notify → archive | Orchestrated Saga or state machine |
| Design Expense Report Approval | Submit → approve → pay → notify | Orchestrated Saga with human steps |
| Design User Signup | Create account + send email + provision settings | Choreography (simple linear, OK to retry) |

---

## 🔗 Concept Notes — Go Here for "How It Works"

- **Saga pattern** (2PC vs Saga trade-offs, full mechanics) → `../../Core-Architecture/Resilience-and-Fault-Tolerance/36-two-phase-commit-vs-saga.md`
- **Saga / compensating transactions** → `../../Core-Architecture/Resilience-and-Fault-Tolerance/23-saga-pattern.md`
- **Outbox pattern** (transactional event emission, CDC) → `../../Foundations/Data-Fundamentals/07-cdc-outbox.md`
- **Idempotency** (safe retries) → `../../Foundations/Concurrency-and-Consistency/04-idempotency.md`
- **State machines** → `../../Production-Grade/System-Design-Patterns/49-state-machines-workflows.md`

---

## 🔄 Changelog

| Date | Change |
|---|---|
| July 2026 | Note created. Batch 2 of 8 remaining patterns. |
| July 2026 | Added Approach 4 (Durable Execution Engines — Temporal/Step Functions) with signals, versioning, Continue-as-New. Updated decision sequence to include durable engines. |
