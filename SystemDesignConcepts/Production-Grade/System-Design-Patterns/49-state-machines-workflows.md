# 49 — State Machines in Workflows

## 📖 What is a State Machine in Workflows?

**Full form:** Finite State Machine (FSM — a computational model that defines a fixed set of states an entity can occupy, plus the explicit allowed transitions between those states) in Workflows — an explicit model of all valid states a business entity (order, document, booking, expense report) can occupy, plus the valid transitions between those states, enforced at the application and database layer.

**Simple analogy:** A traffic light controller. It can only be in RED, GREEN, or YELLOW. It transitions RED → GREEN → YELLOW → RED. It does NOT jump from RED directly to YELLOW. It does NOT stay GREEN forever. If any controller software tries an invalid transition, it's an error — the light doesn't obey. The valid transitions are the contract.

**Core principle:** Without an explicit state machine, business logic is scattered: `if (status == 'PROCESSING') { do X }` in 10 different service methods. Any service can set any status at any time, creating inconsistent states that break downstream systems. With a state machine: one transition table defines all valid moves; the engine enforces it before any DB write; invalid transitions throw exceptions rather than silently corrupting data.

**Why it matters in system design:** Complex multi-step workflows — DocuSign signing sessions, Flipkart order lifecycle, CultFit class booking, PhonePe payment flow — all require that entities move through states in a defined order. Without FSM, concurrent events or service bugs cause impossible states — a cancelled order that is also marked delivered.

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|---|---|---|
| FSM | Finite State Machine — a model with a fixed set of states and explicit allowed transitions between them | Order FSM: PENDING → PROCESSING → SHIPPED → DELIVERED |
| State | One of the defined conditions an entity can be in at any moment | PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED |
| Valid Transition | A state change that is explicitly listed in the transition map — anything not listed is rejected | PROCESSING → SHIPPED is valid; DELIVERED → SHIPPED is invalid |
| Terminal State | A state with no outgoing transitions — the entity's lifecycle is complete | DELIVERED, CANCELLED, REFUNDED — no further transitions allowed |
| CAS Enforcement (DB) | `UPDATE orders SET status='SHIPPED' WHERE id=? AND status='PROCESSING'` — the WHERE clause atomically guards against invalid transitions | 0 rows affected = concurrent event already changed state; throw ConcurrentTransitionException |
| Invalid Transition | An attempted state change not in the FSM's transition map — rejected before any DB write | Trying to SHIP a CANCELLED order → IllegalStateTransitionException |
| Compensating Transition | A state specifically modelling rollback intent (not a generic ERROR) that triggers reversal of prior steps | `FINANCE_REJECTED` state triggers budget release + submitter notification |
| Orphaned State | An entity stuck in a non-terminal state because the worker processing it died — FSM state drives watchdog recovery | Order stuck in PROCESSING for 30 minutes → background job transitions to FAILED |

---

## 🎯 Why This Matters

**The problem:** `UPDATE orders SET status = ? WHERE id = ?` run without checking current state leads to invalid transitions. An order CANCELLED but then SHIPPED. A document SIGNED before all signers have signed. Without enforcement, any service can set any status at any time.

**Interview relevance:** This topic comes up in every workflow design question — expense report approval (D2-level), signing sessions (D1-level), class bookings (CF1-level), order management (Flipkart/Amazon), ride lifecycle (Uber/Ola). State machines are the senior answer to "how do you prevent invalid state transitions in a distributed system?"

**Senior expectation:** Name the state/transition table model, DB-level CAS (compare-and-swap — an atomic operation that updates a value only if the current value matches an expected value) enforcement (`UPDATE ... WHERE status = 'current'`), idempotent transition handling, compensation (rollback on failure), and how event sourcing relates to FSM.

---

## 🧠 The Mental Model

Think of a boarding gate at an airport. A passenger's boarding pass (the entity) goes through states: CHECK_IN → SECURITY_CLEARED → BOARDED → DEPARTED. The gate agent (the system) checks the current state before allowing any transition. You cannot BOARD without being SECURITY_CLEARED first. You cannot un-DEPART — DEPARTED is a terminal state (a state with no valid outgoing transitions; the entity's lifecycle is complete and cannot be reversed). The gate agent doesn't ask "what state do you want to be in?" — they check "what state ARE you in, and is the requested transition valid from here?"

Now scale to software: replace the gate agent with your WorkflowEngine class. Replace the boarding pass with an Order or Document row in PostgreSQL with a `status` column. The transition table is a `Map<OrderStatus, Set<OrderStatus>>` in your engine — it says "from PROCESSING, you can go to SHIPPED or CANCELLED, but not back to PENDING."

The key insight is: **The DB column is the source of truth, not application variables. The transition enforcement must happen AS A PART OF the DB update** — specifically, `UPDATE orders SET status = 'SHIPPED' WHERE id = ? AND status = 'PROCESSING'`. If this UPDATE returns 0 rows affected, the transition was invalid (concurrent update changed state first) — throw an exception and let the caller retry. Zero rows affected is not a silent failure; it is the machine saying "this transition was not allowed."

Three classes of state machine bugs that explicit FSM prevents:

1. **Race condition on concurrent events:** An order receives a SHIP event and a CANCEL event simultaneously. Without FSM, last-writer-wins corrupts the final state. CAS UPDATE ensures only one transition wins atomically.
2. **Orphaned partial workflows:** A DocuSign signing session where Signer A signed but Signer B's token expired — the session is stuck and never completes. FSM tracks the IN_PROGRESS state and drives timeout logic via a background job that reads the current state and transitions to EXPIRED if too much time has elapsed.
3. **Missing compensation:** In a saga-style multi-step workflow (saga — a pattern for managing distributed transactions by breaking them into a sequence of local transactions with compensating steps for failure), if step 3 fails, FSM knows the current state (STEP_2_DONE) and can trigger the compensation path (undo step 2, undo step 1) rather than leaving the system in an unknown partial state.

---

## 🎨 Visual — System Topology & Component Flow

### Diagram 1 — Full System Topology

```
┌────────────────────────────────────────────────────────────────────────┐
│                         Client Tier                                    │
│             (Mobile App / Web UI / Internal Admin Tool)                │
│                                                                        │
│              POST /orders/{id}/cancel                                  │
└────────────────────────────┬───────────────────────────────────────────┘
                             │ HTTP request: "cancel this order"
                             ▼
┌────────────────────────────────────────────────────────────────────────┐
│                       API Service Layer                                │
│                   (OrderController.cancelOrder)                        │
│                                                                        │
│   Receives request → delegates to WorkflowEngine.transition(           │
│       orderId, CANCEL_EVENT)                                           │
└────────────────────────────┬───────────────────────────────────────────┘
                             │ calls transitionOrder(orderId, CANCELLED)
                             ▼
┌────────────────────────────────────────────────────────────────────────┐
│                  WorkflowEngine (in-process)                           │
│                                                                        │
│   Step 1 — Load current state from DB:   status = PROCESSING           │
│   Step 2 — In-memory guard: is PROCESSING → CANCELLED valid? YES ✓     │
│   Step 3 — CAS UPDATE:                                                 │
│     UPDATE orders SET status='CANCELLED'                               │
│     WHERE id=? AND status='PROCESSING'                                 │
│                                                                        │
│   ┌───────────────────┐         ┌──────────────────────────────────┐   │
│   │ 1 row affected ✓  │         │ 0 rows affected ✗                │   │
│   │ Transition won    │         │ Concurrent SHIP event won first  │   │
│   │ Publish event     │         │ Throw ConcurrentTransitionException│  │
│   └─────────┬─────────┘         └──────────────────┬───────────────┘   │
└─────────────┼────────────────────────────────────── ┼───────────────────┘
              │                                        │
              ▼                                        ▼
┌──────────────────────────────┐       ┌───────────────────────────────────┐
│   Order DB (PostgreSQL)      │       │   API Service Layer               │
│                              │       │   Returns 409 Conflict to client  │
│   orders.status = CANCELLED  │       │   (client reloads current state   │
│   (single source of truth)   │       │    and decides: retry or abort)   │
└──────────────────────────────┘       └───────────────────────────────────┘

NOTE: WorkflowEngine sits BETWEEN the API layer and the DB.
      It is NOT optional middleware — skipping it means skipping enforcement.
      All status-changing operations must route through WorkflowEngine.
      Direct DB writes that bypass the engine are the #1 FSM violation pattern.
```

### Diagram 2 — Component Detail: Order Status State Machine

```
┌──────────────────────────────────────────────────────────────────────────┐
│                     ORDER LIFECYCLE STATE MACHINE                        │
└──────────────────────────────────────────────────────────────────────────┘

PENDING ──(confirm)──▶ PROCESSING ──(ship)──▶ SHIPPED ──(deliver)──▶ DELIVERED
   │                       │                                           (terminal)
   │(cancel)               │(cancel)
   ▼                       ▼
CANCELLED              CANCELLED
(terminal)             (terminal)

PROCESSING ──(payment_fail)──▶ PAYMENT_FAILED
                                    │
                                    │(retry)
                                    ▼
                               PROCESSING  ← re-enters (loop back)

SHIPPED ──(return_requested)──▶ RETURN_IN_TRANSIT ──(returned)──▶ REFUNDED
                                                                   (terminal)

FULL VALID TRANSITION MAP:
  PENDING           → {PROCESSING, CANCELLED}
  PROCESSING        → {SHIPPED, CANCELLED, PAYMENT_FAILED}
  SHIPPED           → {DELIVERED, RETURN_IN_TRANSIT}
  PAYMENT_FAILED    → {PROCESSING}
  RETURN_IN_TRANSIT → {REFUNDED}
  DELIVERED         → {}   ← terminal: no outgoing transitions
  CANCELLED         → {}   ← terminal: no outgoing transitions
  REFUNDED          → {}   ← terminal: no outgoing transitions

KEY INVARIANT:
  Every arrow above is a valid transition.
  Everything NOT shown is invalid.
  Invalid transition → exception at WorkflowEngine layer
                     → 0 DB rows affected
                     → client gets 409 Conflict.
  Terminal states (DELIVERED, CANCELLED, REFUNDED) have no outgoing
  transitions. Any attempt to transition FROM a terminal state is
  always an error, regardless of the target state requested.
```

---

## ⚙️ How It Actually Works

### 4a — State and Transition Table Definition

**Steps:**
1. Define an enum for all valid states — every possible status the entity can be in. This forces a complete enumeration upfront and prevents typo-driven states.
2. Define a `Map` from each state to the set of states it can transition to — this single data structure IS the state machine specification.
3. Any requested transition not present in the map throws an `IllegalStateTransitionException` — fail fast before touching the DB.

```java
// All valid states for an order entity — exhaustive enum, no free-form strings
public enum OrderStatus {
    PENDING,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    PAYMENT_FAILED,
    RETURN_IN_TRANSIT,
    REFUNDED
}

// OrderStateMachine — the single source of truth for all valid transitions
// Instantiated once (stateless singleton) and injected wherever transitions occur
public class OrderStateMachine {

    // VALID_TRANSITIONS is the complete FSM specification
    // Any state not listed as a key has no outgoing transitions (implicit terminal state)
    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = Map.of(
        OrderStatus.PENDING, Set.of(OrderStatus.PROCESSING, OrderStatus.CANCELLED),
        OrderStatus.PROCESSING, Set.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED, OrderStatus.PAYMENT_FAILED),
        OrderStatus.SHIPPED, Set.of(OrderStatus.DELIVERED, OrderStatus.RETURN_IN_TRANSIT),
        OrderStatus.PAYMENT_FAILED, Set.of(OrderStatus.PROCESSING),
        OrderStatus.RETURN_IN_TRANSIT, Set.of(OrderStatus.REFUNDED),
        // Terminal states explicitly mapped to empty sets — makes them visible in the spec
        OrderStatus.DELIVERED, Set.of(),
        OrderStatus.CANCELLED, Set.of(),
        OrderStatus.REFUNDED, Set.of()
    );

    // validateTransition — guard called BEFORE any DB operation
    // Throws on invalid transition so no DB roundtrip is wasted on an impossible move
    public void validateTransition(OrderStatus from, OrderStatus to) {
        Set<OrderStatus> allowed = VALID_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            // IllegalStateTransitionException is a domain exception — not a generic 500
            // The caller (API layer) maps this to HTTP 409 Conflict
            throw new IllegalStateTransitionException(
                "Invalid transition: " + from + " → " + to
            );
        }
    }
}
```

### 4b — DB-Level CAS Enforcement

**Steps:**
1. Application validates the transition in-memory (fail fast before hitting the DB — avoid a wasted network round-trip on a clearly invalid move).
2. Execute a DB update with the current state in the WHERE clause — this is the CAS (compare-and-swap) check. The DB atomically reads the current state and writes the new one in a single operation.
3. If 0 rows affected: a concurrent update already changed the state — throw a `ConcurrentTransitionException` with a retry hint. The caller can reload current state and decide whether to retry.
4. If 1 row affected: the transition succeeded atomically. Publish a domain event for downstream consumers.

```java
// transitionOrder — the ONLY permitted path to change an order's status
// All services, background jobs, and admin tools must call this method
@Transactional
public void transitionOrder(Long orderId, OrderStatus targetStatus) {

    // Step 1 — load current state to validate transition in-memory (fail fast)
    // Read from the primary DB — NEVER from a read replica when making transition decisions
    OrderStatus currentStatus = orderRepository.findStatusById(orderId);

    // Step 2 — in-memory guard: reject clearly invalid transitions before touching DB
    stateMachine.validateTransition(currentStatus, targetStatus);

    // Step 3 — CAS UPDATE: the DB-level atomic guard against concurrent transitions
    // The WHERE clause checks current state — if another thread already changed it,
    // this UPDATE returns 0 rows and we detect the race
    int rowsUpdated = jdbcTemplate.update(
        "UPDATE orders SET status = ?, updated_at = NOW() WHERE id = ? AND status = ?",
        targetStatus.name(),
        orderId,
        currentStatus.name()
    );

    if (rowsUpdated == 0) {
        // A concurrent event changed the order's state between our read and our write
        // This is not a bug — it is expected behavior in concurrent systems
        // The caller receives a 409 and can reload + retry or present the current state to the user
        throw new ConcurrentTransitionException(
            "Order " + orderId + " was concurrently modified. Reload and retry."
        );
    }

    // Step 4 — publish domain event for downstream consumers (notifications, audit log, analytics)
    // This fires AFTER the DB commit succeeds — the domain event reflects confirmed state
    eventPublisher.publish(new OrderStatusChangedEvent(orderId, currentStatus, targetStatus));
}
```

### 4c — Multi-Step Saga with FSM Compensation

**Steps:**
1. Complex workflows have sub-steps — track step progress as part of the FSM state (each approval layer gets its own state, not a generic "IN_PROGRESS").
2. If a sub-step fails, the FSM transitions to a named compensation state (a state specifically modelling the rollback intent, not just a generic ERROR state).
3. The compensation handler undoes previous steps in reverse order, explicitly using the FSM's current state to know what has been done and what still needs undoing.

```java
// Expense report approval workflow state machine — multi-step saga with compensation states
public enum ExpenseReportStatus {
    DRAFT,
    SUBMITTED,
    MANAGER_APPROVED,
    FINANCE_APPROVED,
    // Terminal success state — payment has been initiated
    PAID,
    // Compensation states — named explicitly so compensation logic is state-driven
    MANAGER_REJECTED,
    FINANCE_REJECTED,
    // Terminal failure state — human review required
    REJECTED
}

// handleFinanceRejection — triggered when finance team rejects an approved expense report
// The FSM state drives what compensation actions are needed (budget release, notification)
public void handleFinanceRejection(Long reportId) {
    // Transition to the compensation state — signals that finance step is being rolled back
    transitionExpenseReport(reportId, ExpenseReportStatus.FINANCE_REJECTED);

    // Compensation action 1: notify the submitter that their report was rejected
    notificationService.sendRejectionNotice(reportId);

    // Compensation action 2: release the budget that was held when the report was SUBMITTED
    // This is safe to call here because FSM guarantees we are in FINANCE_REJECTED state
    budgetService.releaseHeldBudget(reportId);

    // Final transition to terminal REJECTED state — no further transitions possible
    transitionExpenseReport(reportId, ExpenseReportStatus.REJECTED);
}
```

### What is CAS (compare-and-swap), and why does it fit here?

CAS (compare-and-swap) is an atomic operation that updates a value only if the current value matches an expected value. In SQL: `UPDATE orders SET status = 'SHIPPED' WHERE id = ? AND status = 'PROCESSING'`. The database executes the check (does `status = 'PROCESSING'` hold?) and the write (`SET status = 'SHIPPED'`) as a single atomic operation. No other transaction can observe an intermediate state. If the current state is different (another thread changed it), the UPDATE returns 0 rows affected — the check failed, the write did not happen. CAS is the fundamental mechanism for optimistic concurrency control (a concurrency strategy that assumes conflicts are rare and detects them at write time rather than acquiring locks at read time) without row-level locks.

In an interview: "The `WHERE status = 'PROCESSING'` clause in my UPDATE is a CAS check — the DB atomically checks and updates in one operation, preventing any other transaction from changing the state between my read and my write. This is optimistic concurrency without any external lock server."

---

## 🏢 Real World — Where Companies Use This

- **DocuSign** (signing session lifecycle): States: CREATED → PENDING_SIGNERS → IN_PROGRESS → COMPLETED / VOIDED / EXPIRED. A signing session transitions from PENDING_SIGNERS to IN_PROGRESS only after all signers are added. Transitioning to COMPLETED requires ALL signers' signature records present — the FSM transition guard queries `signature_records` to verify this precondition. FSM prevents a signing session from appearing COMPLETED with missing signatures, which would be a legally invalid document.

- **Flipkart** (order management): Order states mirror the physical world — PLACED, PACKED, IN_TRANSIT, OUT_FOR_DELIVERY, DELIVERED, RETURN_INITIATED. Invalid transition protection: a DELIVERED order cannot transition to IN_TRANSIT (returns use a separate RETURN_INITIATED state with its own sub-machine). This prevents customer service agents using admin tools from accidentally pushing orders into contradictory states that confuse the warehouse management system.

- **CultFit** (class booking): Class booking states: WAITLISTED → CONFIRMED → CHECKED_IN → COMPLETED / NO_SHOW / CANCELLED. Cancellation is only permitted before CHECKED_IN — once a member scans in at the gym, they cannot cancel for a refund. FSM enforces this cancellation window automatically without scattered `if (status != 'CHECKED_IN')` guards in 5 different service methods. The transition guard IS the business rule.

- **PhonePe** (payment flow): Payment states: INITIATED → PROCESSING → DEDUCTED → SETTLED / FAILED / REFUND_INITIATED → REFUNDED. The DEDUCTED → SETTLED transition requires successful settlement confirmation from the bank. If confirmation doesn't arrive within 5 minutes, an async job reads the current FSM state (DEDUCTED) and transitions to FAILED, triggering reconciliation. Without FSM, the payment might stay in DEDUCTED forever — an impossible real-world state that confuses both the customer and the ledger.

- **Uber** (ride lifecycle): States: REQUESTED → DRIVER_ASSIGNED → DRIVER_ARRIVING → TRIP_IN_PROGRESS → COMPLETED / CANCELLED. If DRIVER_ASSIGNED times out (driver doesn't respond in 30 seconds), a background job reads the current state and, because DRIVER_ASSIGNED → REQUESTED is a valid back-transition, transitions back to REQUESTED for re-dispatch. This timeout-driven auto-transition is a key FSM pattern for SLA enforcement — the FSM state is the trigger that the background job reads to decide whether the timeout action is appropriate.

- **GitHub** (pull request workflow): Pull request states: OPEN → CHANGES_REQUESTED / APPROVED / MERGED / CLOSED. A MERGED PR cannot be re-opened (terminal state). A CLOSED PR can be re-opened (non-terminal, because closing without merging is a deliberate reversible action). The FSM models the developer collaboration contract — the distinction between MERGED (permanent, code is in main) and CLOSED (reversible, code was abandoned) is captured in the terminal vs. non-terminal distinction.

---

## 🧭 When to Use vs When NOT to Use

| Use this when | Do NOT use when |
|---|---|
| Entity goes through defined sequential stages with business rules on each transition | Entity has a simple boolean flag (active/inactive) — FSM is overkill for two-state entities |
| Concurrent events can race to transition the same entity | Workflow is purely linear with no branching or concurrent events |
| Invalid transitions must be prevented and reported clearly (not silently swallowed) | Single-user, single-threaded system with no concurrency |
| You need an audit trail of "who moved this to what state when" | State is purely UI/presentational with no business logic attached to transitions |

**The common mistake:** Using a plain `status` VARCHAR column with `UPDATE orders SET status = ?` without a WHERE clause on current state. Any service can set any status at any time — no transition enforcement. This is "status as a tag" (a label that can be applied freely with no rules) rather than "status as a state machine" (a contract that constrains what is allowed). Fix: add `AND status = ?` to the WHERE clause. That single addition converts a tag into a CAS-enforced state machine.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Impossible for code to produce invalid state transitions; audit trail of all transitions is free (log every state change with actor + timestamp, and the `updated_at` column tells the story); testing is exhaustive and enumerable (test every valid transition and every invalid transition — the test matrix is finite and readable); concurrent events handled safely via CAS with no external lock server |
| **You lose** | Upfront design cost — you must enumerate ALL states and transitions before writing any code; adding a new state requires updating the transition map AND all consumers that process that state (e.g., notification logic, audit dashboards, analytics queries); long-running FSM workflows with many intermediate states are harder to test end-to-end in integration tests |
| **Failure mode** | FSM state stored only in the DB — if the DB has replication lag and a service reads from a replica, it may see a stale state, validate the transition in-memory, but then the CAS UPDATE against the primary still fails (0 rows affected) because the primary has newer state. Solution: always read current state from the PRIMARY DB when making transition decisions. Read replicas are only safe for display queries (showing the user the current status); never use them as the basis for a transition decision. |

---

## 🔬 Interview Q&As

**Q1 (Tier 1):** "How do you design the state machine for a document signing workflow where 3 signers must all sign before the document is marked COMPLETED?"

> Track signing state at two levels: (1) `signing_sessions.status` for the overall session (PENDING_SIGNERS, IN_PROGRESS, COMPLETED, VOIDED); (2) `signature_records.status` per signer (PENDING, SIGNED, DECLINED). On each SIGNED event for a signer, check if all signers have signed: `SELECT COUNT(*) FROM signature_records WHERE session_id = ? AND status != 'SIGNED'`. If count = 0, transition the session to COMPLETED via CAS: `UPDATE signing_sessions SET status = 'COMPLETED' WHERE id = ? AND status = 'IN_PROGRESS'`. The CAS prevents duplicate COMPLETED transitions from concurrent signature events — if two signers submit at the same millisecond, exactly one UPDATE returns 1 row affected and the other sees 0. The session completes exactly once.

---

**Q2 (Tier 1):** "Two concurrent API calls try to CANCEL and SHIP the same order simultaneously. What happens?"

> Both calls read the order in PROCESSING state. Both pass the in-memory validation (PROCESSING → CANCELLED and PROCESSING → SHIPPED are both valid transitions). Both attempt their CAS UPDATEs against PostgreSQL. PostgreSQL serializes these at the storage engine level: one UPDATE's WHERE clause (`AND status = 'PROCESSING'`) matches and commits — say, the CANCEL call wins and sets status to CANCELLED. The other UPDATE's WHERE clause no longer matches (status is now CANCELLED, not PROCESSING) and returns 0 rows affected. The losing thread throws a `ConcurrentTransitionException`. The API returns 409 Conflict. The client reloads the current state (now CANCELLED) and retries the SHIP — which fails in-memory validation because CANCELLED → SHIPPED is not in the valid transition map. The order ends up in exactly one final state, no matter the race outcome.

---

**Q3 (Tier 1):** "Why not just use an enum field with no transition enforcement? Let services write whatever status they want."

> Without enforcement, any service can write any status at any time. An order fulfillment service writes SHIPPED while a cancellation service simultaneously writes CANCELLED — last-writer-wins. No one knows the actual state. An even worse case: the order is DELIVERED but the payment service checks `status != 'CANCELLED'` (a common pattern) and charges the customer again. With explicit FSM enforcement: wrong state → DB UPDATE returns 0 rows → exception → 409 Conflict → the calling service MUST handle it. Bugs surface immediately at the transition point, not days later in a reconciliation report when a finance analyst notices the ledger doesn't balance.

---

**Q4 (Tier 2 — cross/probe):** "Your signing session FSM has a state EXPIRED — triggered by a background job 24 hours after creation if not all signers have signed. How do you prevent a race where a signer signs at the exact same moment the expiry job transitions to EXPIRED?"

> Both the signing action and the expiry job use CAS updates with the expected current state in the WHERE clause. The signer's action: `UPDATE signing_sessions SET status = 'COMPLETED' WHERE id = ? AND status = 'IN_PROGRESS'`. The expiry job: `UPDATE signing_sessions SET status = 'EXPIRED' WHERE id = ? AND status = 'IN_PROGRESS'`. PostgreSQL serializes both updates at the row level — exactly one UPDATE sees 1 row affected. If the signer wins (COMPLETED), the expiry job gets 0 rows affected and exits without changing state. If the expiry job wins (EXPIRED), the signer's action gets 0 rows affected and throws a `ConcurrentTransitionException`, which the API maps to an appropriate user-facing message ("this session has expired"). No intermediate state where the document is simultaneously signed and expired. This is precisely why ALL transitions — including background job transitions — must go through the same CAS mechanism. A background job that bypasses the WorkflowEngine and does a direct UPDATE without a state condition is an FSM violation.

---

**Q5 (Tier 2 — cross/probe):** "How does event sourcing relate to a state machine? Could you implement one using the other?"

> They are complementary, not alternatives. Event sourcing (a persistence pattern that stores the full sequence of domain events — OrderPlaced, OrderShipped, OrderCancelled — as the immutable source of truth, deriving current state by replaying all events) keeps every transition event forever. A state machine stores the current state directly in a `status` column and enforces valid transitions on each event. You can combine them: event sourcing records every transition event immutably (full history); the state machine enforces valid transitions before writing the event (invariant protection). The current state derived from replaying all events in the event log must equal the `status` column value — they are redundant representations of the same truth. When to use each: add event sourcing when you need temporal queries ("what was this order's state 3 hours ago?" or "show me every state this document passed through") or a complete audit trail for compliance. Use state machine `status` column when you need fast current-state reads without event replay. In high-compliance systems like payments and document signing, you often want both.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> "State machines enforce valid entity transitions via CAS: `UPDATE orders SET status = 'SHIPPED' WHERE id = ? AND status = 'PROCESSING'` — 0 rows affected means a concurrent event already changed state, preventing impossible transitions like a cancelled order getting shipped."

---

## 🔗 Related Concepts

- `Foundations/Concurrency-and-Consistency/01-optimistic-pessimistic-locking.md` — CAS is optimistic locking applied to state transitions; the `AND status = ?` WHERE clause IS the optimistic lock
- `Core-Architecture/Resilience-and-Fault-Tolerance/23-saga-pattern.md` — saga compensation states are FSM states with reverse transitions; the FSM drives which compensation steps are needed based on current state
- `Core-Architecture/Database-Core/22-event-sourcing.md` — event sourcing as an alternative or complement to FSM current-state storage; use both for full audit trails in compliance-sensitive systems
- `Production-Grade/System-Design-Patterns/47-job-scheduling-at-scale.md` — the job lifecycle (PENDING → CLAIMED → RUNNING → DONE) is itself a state machine; timeout recovery is FSM-driven (watchdog reads current state and transitions orphaned jobs back to PENDING)

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **"Statecharts: A Visual Formalism for Complex Systems" — David Harel (1987)** | Original academic paper that formalized hierarchical state machines; foundational for understanding XState and complex FSM composition where states contain nested sub-machines | ~30 min read |
| **Spring State Machine — Reference Documentation** | Production-grade FSM implementation in Java/Spring with guards, actions, and persistence; adds implementation depth beyond this note — specifically how to persist FSM state to a DB and resume on restart | ~20 min read |
| **"Using finite state machines for order management" — Shopify Engineering Blog** | Real-world FSM application at Shopify's commerce platform scale — 10M orders/day with complex state requirements; covers how they handle state explosion as order types multiply | ~15 min read |
