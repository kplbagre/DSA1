# 36 — Two-Phase Commit vs Saga Pattern

## 📖 What is Distributed Transaction Management?

**Full form:** 2PC (Two-Phase Commit) vs Saga Pattern — two strategies for coordinating atomic transactions across multiple services or databases.

**Simple analogy:** Imagine a wedding: the couple wants to get married (all-or-nothing transaction). Venue must confirm, catering must confirm, photographer must confirm. Two-Phase Commit is like a strict coordinator: "Venue, can you guarantee Saturday at 2pm? Yes. Catering, can you guarantee Saturday at 2pm? Yes. Photographer? Yes. OKAY, EVERYONE COMMIT — the wedding is Saturday." If anyone says no at any point, the whole thing is cancelled. Saga is like a flexible plan: "Venue booked for Saturday. Catering booked. Photographer booked. If photographer cancels, we call another photographer. If that fails, we refund catering." Each step commits independently; if one fails, we reverse the previous steps (compensation).

**Core principle:** 2PC guarantees "all-or-nothing" across multiple databases/services via locking during a voting phase. Saga guarantees eventual consistency by executing steps sequentially and compensating on failure. 2PC is strong but slow; Saga is fast but requires careful compensation logic.

**Why it matters in system design:** At scale (multiple microservices, multiple databases), ACID transactions don't exist. You must choose: 2PC's guarantees + blocking, or Saga's flexibility + complexity. This choice defines how money is moved, orders are fulfilled, shipments are coordinated.

---

## 🎯 Why This Matters

- **Problem:** Distributed transactions are impossible if you demand traditional ACID guarantees. How do you ship an order without losing money or double-charging?
- **Interview signal:** "Design payment + inventory deduction across two services." Your answer reveals whether you understand 2PC's limitations and when Saga is required.
- **Senior expectation:** You know not just that these patterns exist, but their failure modes, and which to use for which data (payments vs recommendations).

---

## 🧠 The Mental Model

Imagine a bank transfer from Account A (at Bank 1) to Account B (at Bank 2).

**2PC approach (pessimistic — locked until committed):**
- Bank 1 locks Account A, deducts $100. Writes to transaction log: "A: -$100, pending."
- Bank 1 votes: "Ready to commit." Waits for Bank 2.
- Bank 2 locks Account B, adds $100. Writes to transaction log: "B: +$100, pending."
- Bank 2 votes: "Ready to commit." Returns to Bank 1.
- Bank 1 sees both ready. Coordinator says "COMMIT."
- Both banks finalize. A: -$100, B: +$100. Done.

**During this entire time, neither A nor B is accessible to other transactions.** Accounts are locked.

**Saga approach (optimistic — execute and compensate):**
- Step 1: Deduct from A. A: -$100. (committed immediately, not locked)
- Step 2: Add to B. B: +$100. (committed immediately)
- If Step 2 fails: Compensate. Refund A: +$100. (reverses Step 1)

**The difference:** 2PC locks resources during voting. Saga locks nothing; just compensates on failure.

**The key insight:** 2PC = "guarantee before action." Saga = "action first, compensate on failure."

---

## 🎨 Visual — System Topology & Coordination Flow

```
FULL SYSTEM TOPOLOGY:
                    ┌──────────────────────────┐
                    │   Coordinator / TM       │
                    │  (Transaction Manager)   │
                    └──────────────────────────┘
                           ▲          ▲
                    Vote & │          │ Commit/Abort
                    Commit │          │
                           │          │
        ┌────────────────┐  │     ┌────────────────┐
        │  Service A     │◀─┘     └─▶│   Service B    │
        │ (Accounts DB)  │             │ (Shipping DB) │
        │                │             │                │
        │ Table: Ledger  │             │ Table: Orders │
        └────────────────┘             └────────────────┘

TWO-PHASE COMMIT (2PC) FLOW:
Time  │ Service A         │ Coordinator    │ Service B
──────┼──────────────────┼────────────────┼──────────────────
 0ms  │ PREPARE_COMMIT   │                │
      │ (lock A, ready?) │◀───────────────│
      │ Status: LOCKED   │                │
      │                  │───────────────▶│ PREPARE_COMMIT
 50ms │                  │                │ (lock B, ready?)
      │                  │                │ Status: LOCKED
      │                  │                │
100ms │                  │ Global COMMIT? │
      │ Receive: COMMIT  │                │ Receive: COMMIT
      │ Finalize, unlock │                │ Finalize, unlock
150ms │ A: -$100 ✓       │                │ B: +$100 ✓

KEY INVARIANT:
   2PC locks resources during voting phase.
   All-or-nothing semantics guaranteed.
   But: slow (2+ network round trips) + deadlock risk if coordinator fails.

SAGA PATTERN FLOW:
Time  │ Service A         │ Saga Orchestrator │ Service B
──────┼──────────────────┼───────────────────┼──────────────────
 0ms  │ EXECUTE Step 1   │                   │
      │ Deduct A: -$100  │                   │
      │ Committed ✓      │                   │
      │                  │                   │
 50ms │                  │──────────────────▶│ EXECUTE Step 2
      │                  │                   │ Add B: +$100
      │                  │                   │ Committed ✓
100ms │                  │                   │
      │                  │ (Step 2 fails?)   │
      │                  │ → Compensate      │
      │ EXECUTE Compensate                   │
      │ (reverse of Step 1)                  │
      │ Refund A: +$100 ✓                   │
150ms │                  │                   │

KEY INVARIANT:
   Saga executes steps sequentially, each committed independently.
   No locks. On failure, execute compensating transactions in reverse.
   Risk: temporary inconsistency between A and B (B sees +$100 while A is -$100).
```

---

## ⚙️ How It Actually Works

**Two-Phase Commit (2PC):**

1. **Phase 1 — Prepare/Voting:**
   - Coordinator sends PREPARE_COMMIT to all participants
   - Participants lock resources, execute transaction, write to undo log, vote YES or NO
   - If any participant votes NO (e.g., deadlock, constraint violation), abort

2. **Phase 2 — Commit/Abort:**
   - If all participants voted YES: coordinator broadcasts COMMIT
   - If any voted NO: coordinator broadcasts ABORT
   - Participants finalize (release locks, confirm state)

**Saga Pattern (Orchestration variant):**

1. **Step 1 — Service A executes:** Deduct from account. No lock. Committed immediately.

2. **Step 2 — Service B executes:** If succeeds, done. If fails, move to compensation.

3. **Compensation — Reverse order:** Service A compensates (refund). This is a NEW transaction, not a rollback.

**Code example — 2PC (Java with JDBC):**

```java
public class TwoPhaseCommitTransfer {
    private Connection bank1Conn; // Account A
    private Connection bank2Conn; // Account B
    
    public void transfer(String accountA, String accountB, BigDecimal amount) {
        try {
            // Phase 1 — Prepare
            // Set autocommit false (manual transaction control)
            bank1Conn.setAutoCommit(false);
            bank2Conn.setAutoCommit(false);
            
            // Service A: deduct and lock
            bank1Conn.executeUpdate(
                "UPDATE accounts SET balance = balance - ? WHERE id = ?" ,
                amount, accountA
            );
            // At this point, row is locked, but not committed
            
            // Service B: add and lock
            bank2Conn.executeUpdate(
                "UPDATE accounts SET balance = balance + ? WHERE id = ?",
                amount, accountB
            );
            // Both locked, both modified in memory
            
            // Phase 2 — Commit
            // If we reach here, vote YES
            bank1Conn.commit(); // Finalize, unlock
            bank2Conn.commit(); // Finalize, unlock
            
        } catch (SQLException e) {
            // Abort — rollback both
            try {
                bank1Conn.rollback();
                bank2Conn.rollback();
            } catch (SQLException ignored) {}
            throw new TransactionFailedException("Transfer failed", e);
        }
    }
}

// Saga Pattern (Orchestration variant)
public class SagaOrchestrator {
    private AccountService accountService;
    private ShippingService shippingService;
    
    public void shipOrder(String orderId, BigDecimal amount) {
        try {
            // Step 1 — Charge account (independent commit)
            accountService.deduct(amount); // Committed immediately
            
            // Step 2 — Ship order (independent commit)
            shippingService.ship(orderId); // Committed immediately
            
        } catch (Exception e) {
            // Compensation — reverse order
            try {
                shippingService.cancelShipment(orderId); // Reverse of Step 2
                accountService.refund(amount); // Reverse of Step 1
            } catch (Exception compError) {
                // Compensation itself failed — log to dead-letter queue
                deadLetterQueue.push(
                    new CompensationFailure(orderId, compError)
                );
            }
        }
    }
}
```

---

## 🏢 Real World — Where Companies Use This

- **Banking (2PC):** Bank transfers across institutions use 2PC for regulatory compliance. Money cannot be in flight; accounts must be consistent. SWIFT protocol enforces 2PC-like semantics. Cost: transfers take 2-3 business days (slow due to locking and coordination overhead).

- **Uber (Saga pattern):** Uber charges driver → Marks ride complete → Pays driver. Each step commits independently. If payment fails mid-flow, Saga compensates (reverse charges, retry driver payment). Can't use 2PC across geographically distributed services — too slow. Saga trades consistency for availability.

- **Amazon Prime (Saga pattern):** Deduct from wallet → Trigger shipment → Update inventory. Each service commits independently. If shipment fails, compensate (refund customer). 2PC would block checkout while confirming shipment availability.

- **Razorpay (Saga):** Payment received → Create invoice → Send webhook to merchant. Each committed independently. If webhook fails, Saga retries (compensation = re-send webhook). Merchant doesn't wait for webhook during checkout.

- **Paypal (Hybrid):** Internal ledger uses 2PC (consistency critical). Customer-to-merchant transfers use Saga (must be fast, eventual consistency acceptable).

---

## 🧭 When to Use vs When NOT to Use

| Use 2PC when | Use Saga when |
|---|---|
| Strong consistency is non-negotiable (payment ledger, bank transfers) | Speed is critical (checkout, payment processing) |
| Participants are few and nearby (same data center) | Participants are numerous or geographically distributed |
| Transactions are brief (< 100ms) | Transactions are long (multi-second, involve human steps) |
| | Compensations are natural (refund, reverse shipment) |

**The common mistake:** Trying to use 2PC across microservices. 2PC requires a global coordinator and assumes synchronous communication. In microservices at scale (network partitions, service failures), 2PC blocks forever. Use Saga instead.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain (2PC)** | Guaranteed consistency; all-or-nothing semantics; no compensating transactions |
| **You lose (2PC)** | Blocking (locks held during voting); slow (2+ network round-trips); vulnerable to coordinator failure |
| **You gain (Saga)** | Fast (no blocking); resilient (services fail independently); scalable across regions |
| **You lose (Saga)** | Temporary inconsistency between steps; compensating transactions are complex; idempotency and retry logic required |
| **Failure mode (2PC)** | Coordinator crashes mid-commit → transactions hang forever, locks never released, manual recovery needed |
| **Failure mode (Saga)** | Compensation fails → temporary inconsistency becomes permanent; requires manual reconciliation |

---

## 🔬 Interview Q&As

### Q: "Why doesn't everyone just use 2PC? It's simpler and guarantees correctness."

> 2PC requires all participants to be locked and available during the voting phase. At scale, that's intolerable. A 5-service transaction waits for the slowest service (network latency amplifies). In geo-distributed systems, 2PC can block for seconds (network partition, slowdown). Saga trades immediate consistency for availability and speed. At scale, availability + eventual consistency beats guaranteed consistency + blocking.

### Q: "In a Saga, what if compensation fails? Now we're in a bad state."

> Correct — that's a gap in compensation design. Example: You charge customer, ship order, then shipping service crashes before marking shipment as confirmed. You can't refund (payment already committed) and can't re-ship (state unclear). Solution: Idempotency keys + dead-letter queue. Log compensation failures; human team reviews and manually resolves. This is why Saga requires careful design.

### Q: "Can we use 2PC in a microservices world?"

> Not at scale. 2PC assumes synchronous communication and a reachable coordinator. In microservices, services fail, networks partition, latency varies. 2PC will timeout, hang, or cause cascading failures. Use Saga with distributed orchestration (Kafka, choreography) instead. Some teams use 2PC for single-region, low-latency participants (e.g., two co-located databases), but it doesn't scale.

### Q: "How do we know if a Saga step succeeded or failed? What if the response is lost?"

> Idempotency keys. Each Saga step includes a unique ID. If the response is lost and we retry the step, the service recognizes the ID and returns the same result without re-executing. This is critical for Saga correctness. Without idempotency keys, retries cause duplicate charges or duplicate shipments.

### Q: "In a Saga, if step 2 fails and we compensate step 1, what if the compensation also fails?"

> Now you have a stuck transaction. Solution: Log it to a dead-letter queue (Kafka topic, database table). Human team gets alerted. They manually investigate: "Why did compensation fail? What's the actual state?" Then manually resolve (refund, reship, retry compensation). This is operationally expensive, but unavoidable. Saga trades the need for consensus (2PC overhead) for the need for manual recovery (operational burden).

### Q: "For a payment system, should we use 2PC or Saga?"

> Depends on the scope. If you're charging one user's account and crediting another's account in the same database, 2PC is fine (single database, no microservices). If you're charging at PaymentService and updating inventory at InventoryService, use Saga (distributed). Payment systems use both: 2PC internally (ledger consistency), Saga externally (payment processor → merchant account).

### Q: "Can we use Saga for critical transactions like money transfers?"

> Yes, but with care. Saga + idempotency keys + dead-letter queues can achieve payment-grade reliability. The trade-off: brief window of inconsistency (customer sees $100 deducted before transfer completes). If you need zero-moment inconsistency, you need 2PC. But 2PC on distributed systems doesn't scale. So in practice, most payment systems accept Saga's brief inconsistency.

---

## 🧾 TL;DR

> "2PC guarantees all-or-nothing but blocks during voting. Saga executes steps independently and compensates on failure — faster, but requires idempotency keys and dead-letter queue handling for failures. Use 2PC for single-region strong consistency; use Saga for distributed systems."

---

## 🔗 Related Concepts

- **Event Sourcing (22):** Saga executes steps as events; event sourcing provides the audit trail
- **Saga Pattern (23):** This note explains Saga deeply; concept 23 is the introduction
- **Idempotency (04):** Saga retries require idempotency keys to prevent double-charges
- **Distributed Locking (06):** 2PC uses locks; understanding lock contention helps explain why 2PC is slow

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **"Choreography vs Orchestration" — Chris Richardson** (microservices.io) | Two variants of Saga (choreography with events vs orchestration with coordinator). This note covers orchestration; choreography is the event-driven variant. | ~10 min |
| **"Transaction Outbox Pattern" — Event Sourcing** — Arpit Bhayani (arpitbhayani.me) | How to make Saga writes durable using outbox tables. Ensures compensation is guaranteed even if original service crashes. | ~12 min |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 25, 2026 | Initial creation. Added 2PC voting phase vs Saga compensation flow, code example for both patterns, JDBC transaction control, Saga orchestrator with compensation. Real-world examples (Banking, Uber, Amazon Prime, Razorpay, PayPal). Seven Q&As covering blocking behavior, compensation failures, idempotency, Saga vs 2PC trade-off. |
