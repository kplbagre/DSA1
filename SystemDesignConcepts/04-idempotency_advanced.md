# Idempotency — Advanced Patterns

> **Standard followed:** `SystemDesignConcepts/notes-standards.md`
> **Companion to:** `04-idempotency.md` — Advanced patterns for distributed transactions, batch idempotency, and eventual consistency

---

## 🎯 Why This Matters

The core idempotency note covers HTTP-level idempotency (Idempotency-Key header) and Kafka consumer deduplication. But production systems go deeper: How do you make a **multi-step operation** (1. payment, 2. inventory update, 3. shipment notification) idempotent as a unit? What if the payment succeeds but inventory fails — how do you rollback? When you're processing **batches** of 10,000 records and the job fails halfway, how do you retry without re-processing the first 5,000? How do you generate **deterministic request IDs** so retries always produce the same ID? Senior engineers deploy these patterns when idempotency must extend beyond a single endpoint to entire workflows.

---

## 🧠 The Mental Model

Extend the "doorman checking the guest list" analogy from the core note:

You had one doorman with a clipboard (one idempotency check per API call). Now you have a series of doorways — the payment doorway, inventory doorway, shipping doorway. The guest (request ID) passes through all three. **Problem 1:** If the guest gets past the payment doorway but collapses at the inventory doorway, how do you clean up the payment? That's the **saga pattern** (multi-step idempotency with compensation). **Problem 2:** You're processing a list of 1,000 invoices. The job crashes after 600. On retry, you only process the remaining 400, not all 1,000 again. That's **batch idempotency** (partition the work and track progress). **Problem 3:** Every time a user retries a payment, the request ID should be the same so you don't create duplicate payments. That's **deterministic request ID generation** (derived from inputs, not random).

These are the problems advanced idempotency solves.

---

## 🎨 Visual — Saga Pattern and Batch Idempotency

```
SAGA PATTERN — Multi-Step Idempotency with Compensation
═════════════════════════════════════════════════════════════

HAPPY PATH:
──────────
Request ID: payment-001

Step 1: Payment                   Request ID: payment-001 → DB stores idempotency record
        Charge card $100          ✅ Success, record status: PAYMENT_DONE
        ↓
Step 2: Inventory Update          Request ID: payment-001 → DB checks: already done?
        Reserve 5 seats           No, new step. Execute. ✅ Success, status: INVENTORY_DONE
        ↓
Step 3: Notification             Request ID: payment-001 → DB checks: already done?
        Send confirmation email   No, new step. Execute. ✅ Success, status: COMPLETE
        ↓
RESULT: All 3 steps done once


FAILURE & COMPENSATION:
──────────────────────
Request ID: payment-001

Step 1: Payment ✅ Charge card $100
Step 2: Inventory ❌ No seats available (failure)

Compensation:
  Reverse Step 1: Refund $100

Why compensation, not retry?
- Retry would re-charge the card (duplicate charge)
- Compensation is: "undo what succeeded, declare failure"

Result: User is charged $0, invoice shows "FAILED - compensation executed"


BATCH IDEMPOTENCY — Retry Partitions
═════════════════════════════════════════

Process 1000 invoices, job crashes at #600:

First run:
  Process [1...100]       ✅ Committed (stored in DB with status PROCESSED)
  Process [101...200]     ✅ Committed
  Process [201...300]     ✅ Committed
  Process [301...400]     ✅ Committed
  Process [401...500]     ✅ Committed
  Process [501...600]     ✅ Committed
  Process [601...700]     ❌ Crash (partial — maybe [601...650] done)

Retry (same batch ID, e.g., batch-2026-06-23):
  Process [1...100]       🔄 Check: idempotency key batch-2026-06-23:1-100 → exists → skip
  Process [101...200]     🔄 Check: exists → skip
  ...
  Process [501...600]     🔄 Check: exists → skip
  Process [601...700]     🔄 Check: partial (maybe 601-650 exist, 651-700 don't)
                          Process only [651...700] ✅
  Process [701...800]     🔄 Check: doesn't exist → Process
  ...
  Process [901...1000]    🔄 Check: doesn't exist → Process

RESULT: After retry, all 1000 invoices processed exactly once

KEY INVARIANT:
   Each partition is idempotent (batch ID + partition range = unique key).
   Partial failures in a partition are detected and only missing work is redone.
```

---

## ⚙️ How It Actually Works

### Saga Pattern — Compensating Transactions

**Problem:** A three-step booking: (1) reserve hotel, (2) reserve flight, (3) charge credit card. Step 1 succeeds, step 2 fails. Now what? You can't just "reject" — you've already reserved the hotel. You need to **compensate** (cancel the reservation).

**Solution:** Each step has an associated compensation. If any step fails, execute compensations in reverse order.

```java
@Service
@Slf4j
public class BookingSagaService {

    private final HotelService hotelService;
    private final FlightService flightService;
    private final PaymentService paymentService;
    private final BookingRepository bookingRepository;

    // Idempotency storage: one record per saga step
    @Entity
    @Table(name = "saga_steps")
    public static class SagaStep {
        @Id private UUID stepId = UUID.randomUUID();
        private String sagaId;               // Unique saga instance
        private String stepName;             // e.g., "hotel_reservation"
        private String status;               // "PENDING", "DONE", "COMPENSATED"
        private LocalDateTime createdAt;
    }

    @Transactional
    public void bookingFlow(UUID bookingId, BookingRequest req) {
        String sagaId = "saga-" + bookingId;

        try {
            // Step 1: Hotel
            executeStep(sagaId, "hotel_reserve", () -> {
                return hotelService.reserve(req.getHotelId());
            });

            // Step 2: Flight
            executeStep(sagaId, "flight_reserve", () -> {
                return flightService.reserve(req.getFlightId());
            });

            // Step 3: Payment
            executeStep(sagaId, "payment_charge", () -> {
                return paymentService.charge(req.getCardToken(), req.getAmount());
            });

            // Mark saga as complete
            updateSagaStatus(sagaId, "COMPLETE");
            log.info("Saga {} completed successfully", sagaId);

        } catch (Exception e) {
            // If any step fails, compensate in reverse order
            log.error("Saga {} failed at step: {}", sagaId, e.getMessage());
            compensateSaga(sagaId);
            updateSagaStatus(sagaId, "COMPENSATED");
            throw new SagaCompensatedException("Booking failed and was rolled back");
        }
    }

    private void executeStep(String sagaId, String stepName, Callable<?> action)
            throws Exception {
        // Check if this step already executed (idempotency)
        SagaStep existing = sagaStepRepository.findBySagaIdAndStepName(sagaId, stepName);
        if (existing != null && "DONE".equals(existing.status)) {
            log.debug("Step {} already executed for saga {}", stepName, sagaId);
            return;  // Skip — idempotent
        }

        // Execute the step
        Object result = action.call();

        // Record that this step is done
        SagaStep step = new SagaStep();
        step.sagaId = sagaId;
        step.stepName = stepName;
        step.status = "DONE";
        sagaStepRepository.save(step);
    }

    private void compensateSaga(String sagaId) {
        // Fetch all "DONE" steps in REVERSE order
        List<SagaStep> completedSteps = sagaStepRepository.findBySagaIdAndStatusOrderByCreatedAtDesc(
            sagaId, "DONE"
        );

        for (SagaStep step : completedSteps) {
            try {
                switch (step.stepName) {
                    case "hotel_reserve" -> hotelService.cancel(/* reservation ID */);
                    case "flight_reserve" -> flightService.cancel(/* reservation ID */);
                    case "payment_charge" -> paymentService.refund(/* charge ID */);
                }
                step.status = "COMPENSATED";
                sagaStepRepository.save(step);
            } catch (Exception e) {
                log.error("Compensation for {} failed: {}", step.stepName, e.getMessage());
                // Log but continue compensating other steps
            }
        }
    }
}
```

**In an interview, if asked:** "I use the saga pattern for multi-step operations: each step is recorded with its idempotency key (sagaId + stepName). If a step fails, I compensate by executing reverse operations (cancel hotel, refund payment) in reverse order. Retries skip already-completed steps (idempotent). This ensures that either all steps succeed or all are compensated — no partial bookings."

---

### Batch Idempotency — Processing with Automatic Retry of Partial Failures

**Problem:** A daily job processes 1,000 invoices. It crashes after 600. On retry, you don't want to re-process invoices 1-600 (they already succeeded). You only want to process 601-1,000.

**Solution:** Partition the batch and assign an idempotency key to each partition. On retry, check which partitions succeeded and only reprocess failed ones.

```java
@Service
@Slf4j
public class BatchInvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentService paymentService;
    private final IdempotencyRepository idempotencyRepository;

    public static class BatchProcessing {
        public String batchId;       // e.g., "batch-2026-06-23"
        public String batchName;     // e.g., "daily-invoices"
        public int totalRecords;
        public int processedCount;
        public LocalDateTime createdAt;
    }

    public void processBatch(String batchName) {
        String batchId = batchName + "-" + LocalDate.now();
        int batchSize = 100;  // Process 100 invoices per partition

        List<Invoice> allInvoices = invoiceRepository.findByStatus("PENDING");
        int totalRecords = allInvoices.size();

        for (int i = 0; i < allInvoices.size(); i += batchSize) {
            int endIndex = Math.min(i + batchSize, allInvoices.size());
            List<Invoice> partition = allInvoices.subList(i, endIndex);

            // Idempotency key: batch ID + partition range
            String partitionKey = batchId + ":" + i + "-" + endIndex;

            // Check if this partition was already processed
            var existing = idempotencyRepository.findById(partitionKey);
            if (existing.isPresent() && "SUCCESS".equals(existing.get().status)) {
                log.debug("Partition {} already processed", partitionKey);
                continue;  // Skip
            }

            try {
                // Process this partition
                for (Invoice invoice : partition) {
                    paymentService.charge(invoice);
                    invoice.setStatus("PROCESSED");
                    invoiceRepository.save(invoice);
                }

                // Mark partition as done
                recordPartitionSuccess(partitionKey, endIndex - i);
                log.info("Processed partition {} ({} invoices)", partitionKey, partition.size());

            } catch (Exception e) {
                log.error("Partition {} failed: {}", partitionKey, e.getMessage());
                recordPartitionFailure(partitionKey);
                // Continue with next partition (or break to halt batch)
                // Depending on policy: continue or fail-fast
            }
        }

        log.info("Batch {} completed", batchId);
    }

    private void recordPartitionSuccess(String partitionKey, int recordCount) {
        var record = new IdempotencyRecord();
        record.key = partitionKey;
        record.status = "SUCCESS";
        record.recordCount = recordCount;
        idempotencyRepository.save(record);
    }

    private void recordPartitionFailure(String partitionKey) {
        var record = new IdempotencyRecord();
        record.key = partitionKey;
        record.status = "FAILED";
        idempotencyRepository.save(record);
    }
}
```

**In an interview, if asked:** "For batch jobs, I partition the work and assign each partition an idempotency key (batch ID + partition range). On retry, I check which partitions already succeeded and skip them. If a partition partially fails, I mark it as failed and retry it entirely (assuming partition size is small enough to be idempotent). This allows batch jobs to recover from mid-run crashes without reprocessing already-completed work."

---

### Deterministic Request IDs — From Input, Not Random

**Problem:** A user retries a payment. The client generates a new random request ID each time. The server sees three different request IDs and creates three payment charges.

**Solution:** Generate the request ID deterministically from inputs (user ID, timestamp, merchant ID) so retries always produce the same ID.

```java
@Service
public class DeterministicIdempotencyService {

    /**
     * Generate a deterministic idempotency key from inputs.
     * Same inputs → same key, always.
     */
    public String generateIdempotencyKey(String userId, LocalDateTime timestamp, String action) {
        // Derive key from inputs (not random)
        String combined = userId + "|" + timestamp.format(DateTimeFormatter.ISO_DATE_TIME) + "|" + action;
        
        // Hash to get a short, stable key
        String hash = DigestUtils.sha256Hex(combined);
        return hash.substring(0, 16);  // Use first 16 chars
    }

    /**
     * Alternative: use Stripe-style approach — derive from user + action + timestamp round
     */
    public String generateStripeStyleKey(String userId, String action) {
        // Round timestamp to minute (so retries within same minute get same key)
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime rounded = now.withSecond(0).withNano(0);

        String combined = userId + ":" + action + ":" + rounded.format(DateTimeFormatter.ISO_DATE_TIME);
        return DigestUtils.sha256Hex(combined).substring(0, 20);
    }
}

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;
    private final DeterministicIdempotencyService idempotencyService;

    @PostMapping
    public ResponseEntity<?> createPayment(
            @RequestBody PaymentRequest req,
            @AuthenticationPrincipal User user) {

        // Generate deterministic key from user + action + timestamp
        String idempotencyKey = idempotencyService.generateStripeStyleKey(
            user.getId().toString(),
            "charge"
        );

        // Retry with same key = same payment (idempotent)
        PaymentResult result = paymentService.charge(
            user.getId(),
            req.getAmount(),
            idempotencyKey
        );

        return ResponseEntity.ok(result);
    }
}
```

**In an interview, if asked:** "Instead of relying on clients to send Idempotency-Key headers, I generate the key deterministically from the user ID, action, and timestamp rounded to the minute. Same inputs always produce the same key. If a client retries within the same minute with identical amounts, they get the same idempotency key, so the server treats it as a duplicate and returns the cached result. This is more robust than asking clients to implement retry logic correctly."

---

## 🏢 Real World — Where Companies Use This

- **Stripe**: Saga patterns for complex payment flows (authorize → capture → invoice → settlement). Deterministic request ID generation to prevent duplicate charges on retry. Batch idempotency for monthly billing runs.
- **Uber**: Saga pattern for ride bookings (match driver → confirm location → charge card → notify both parties). Compensation if driver cancels after match.
- **Amazon**: Batch idempotency for order fulfillment (pick → pack → ship). Millions of orders daily; partial batch failures are common and must not duplicate.
- **Netflix**: Batch idempotency for content delivery (transcode → upload → catalog index). Failures mid-run; retrying only failed transcodes saves hours.

---

## 🧭 When to Use vs When NOT to Use

| Use saga/batch idempotency when | Do NOT use when |
|---|---|
| Multi-step operations where any step can fail (bookings, payments, orders) | Single, atomic operation — basic idempotency key is enough |
| Batch jobs that process thousands of items and can fail mid-run | Batch is small and always completes or always fails entirely |
| Compensation logic is well-defined (cancel, refund, delete) | No clear rollback strategy — system can't afford to compensate |

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Multi-step operations are atomic (all succeed or all compensate). Batch retries don't re-do work. Deterministic IDs prevent duplicate charges. Saga pattern handles complex workflows. |
| **You lose** | Complexity — sagas require compensations for each step (more code, more testing). Batch idempotency requires state tracking per partition (DB writes). Deterministic ID generation must be stable (changing algorithm = new keys). |
| **Failure mode** | Compensation fails (e.g., refund API is down) — transaction is stuck in limbo. Batch partition fails repeatedly — stuck indefinitely or skipped if tolerance is too high. Deterministic ID generation changes — retries treated as new requests. |

---

## 🔬 Interview Q&As

### Q: "You have a payment → shipment workflow. Payment succeeds but shipment fails. How do you handle this?"

> Saga pattern with compensations: when the shipment step fails, I execute the compensation for the payment step (refund the charge). Each step is idempotent — if the client retries the entire workflow, the payment step checks "have I already charged for this order?" If yes, it skips. The shipment step then retries. This ensures the order isn't charged twice even if the client retries multiple times.

---

### Q: "Your batch job processes 10,000 records and crashes at record #7,500. On retry, you don't want to reprocess the first 7,500. How?"

> Batch idempotency with partition keys: I divide the 10,000 records into partitions of 100 each (100 partitions). Each partition gets an idempotency key: "batch-2026-06-23:0-100", "batch-2026-06-23:100-200", etc. After processing each partition, I record success in a database. On retry, I check which partitions succeeded and skip them, only reprocessing the failed partitions. This way, if the crash happened at partition 75, I only retry partitions 75-100 (2,500 records) instead of all 10,000.

---

### Q (Tier 2): "How do you prevent duplicate payments if a user retries their payment request?"

> Deterministic idempotency key generation: instead of asking the client to send a random Idempotency-Key header (which they might forget or change), I generate the key server-side from the user ID, action name, and timestamp rounded to the minute. Same user, same action, same minute = same key. If the user retries within the minute, the server sees the same key and returns the cached payment result instead of charging again. This is more robust than relying on correct client implementation."

---

## 🧾 TL;DR — One Interviewer-Ready Line

> "For multi-step workflows: I use sagas with compensations (if step fails, undo prior steps). For batch jobs: I partition work and track which partitions succeeded, retrying only failed ones. For payment retries: I generate idempotency keys deterministically from user + action + time, so retries within the same minute are always deduplicated."

---

## 🔗 Related Concepts

- **`04-idempotency.md`** — core patterns (HTTP Idempotency-Key, Kafka deduplication). This companion extends with distributed transactions (sagas) and batch processing.
- **`07-cdc-outbox.md`** — outbox pattern works well with sagas — each saga step writes to the outbox, guaranteeing at-least-once delivery to downstream systems.
- **`01-optimistic-pessimistic-locking.md`** — locking interacts with sagas — saga steps may need pessimistic locks to prevent concurrent modifications during the multi-step flow.

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **"Saga Pattern"** — Chris Richardson (https://microservices.io/patterns/data/saga.html) | Authoritative guide on choreography vs orchestration sagas, compensation patterns. | ~10 min read |
| **"Batch Processing Patterns"** — Arpit Bhayani (YouTube: search "Arpit Bhayani batch processing") | Idempotent batch design for fault tolerance. | ~20 min |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 23, 2026 | Companion file created. Covers: saga pattern (multi-step idempotency with compensations), batch idempotency (partition tracking + retry of failed partitions), deterministic request ID generation, real-world patterns from Stripe/Uber/Amazon. 3 Q&As (all advanced scenarios). Pairs with core `04-idempotency.md`. |
