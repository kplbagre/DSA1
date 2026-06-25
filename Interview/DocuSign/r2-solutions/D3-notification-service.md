# D3 — Design a Real-Time Notification Service

> **Read `solution-notes-standards.md` FIRST.** This file is a 60-minute interview answer framework.
> Its structure mirrors DELIVERY-RECIPE: clarify (5 min) → estimate (5 min) → HLD (15 min) → deep dives (15 min) → trade-offs (8 min) → Q&A (7 min).
> **Say this out loud** before your interview — don't just read it.

---

## Section 0 — Question Identity Card

| | |
|---|---|
| **Question** | **Design a Real-Time Notification Service** (multi-channel: email, SMS, push notifications) |
| **Interview Type** | **Type A — System Design** (Infrastructure: fan-out, delivery guarantees, retry logic, channel coordination) |
| **Confirmed or Likely** | 🔶 Likely (DesignGurus + InterviewQuery both list as standalone; distinct from billing/Kafka fanout) |
| **Concept notes prerequisite** | `07-cdc-outbox.md` (outbox pattern for reliable event publishing), `04-idempotency.md` (exactly-once delivery semantics) |
| **DocuSign-specific angle** | Notifications are critical for e-signature workflows: "Document ready for your signature," "You've been asked to sign," "Document fully signed," "Your signature is contested" — must be reliable (no lost notifications), multi-channel (email for formal, SMS for urgent, push for mobile), and respect user preferences (opt-in/out per channel). |
| **Time budget** | 5 min clarify → 5 min estimate → 15 min HLD → 15 min deep dive → 8 min trade-offs → 7 min Q&A buffer |

---

## Section 1 — 🚀 The One-Sentence Opener

> "Before I design, let me clarify the scope — especially around channels (which ones?), delivery guarantees (at-least-once or exactly-once?), and latency SLOs (how fast do notifications need to arrive?)..."

Then immediately pivot to Section 2 (clarifying questions).

---

## Section 2 — 🔍 Clarifying Questions Script (Minutes 0–5)

**Q: "Which notification channels do we support — email, SMS, push, or all three?"**
- Why ask: Each channel has different infrastructure (email via SendGrid/SES, SMS via Twilio, push via Firebase/Apple APNs). Supporting all three is 3× the complexity.
- If email only → simple service; single provider
- If all three → fan-out service; must coordinate across three parallel paths

**Q: "What's the delivery guarantee — at-least-once (user may see duplicate notifications) or exactly-once (user sees it once)?"**
- Why ask: At-least-once is easier (simple retry logic); exactly-once requires idempotency keys stored in a database.
- If at-least-once → acceptable for most use cases (push, SMS); user can ignore duplicates
- If exactly-once → need idempotency table; adds latency + complexity

**Q: "What's the latency SLO — do users need to see the notification within 1 second, 1 minute, or is background delivery OK?"**
- Why ask: Real-time (< 1s) requires in-memory queues + synchronous delivery; background (minutes) allows batching + cheaper infrastructure.
- If real-time → synchronous delivery path; SQS FIFO or Kafka with low lag
- If background → batch delivery (every 5 minutes); cheaper

**Q: "Do we need to respect user preferences — can users opt out of SMS but stay opted in for email?"**
- Why ask: Per-channel opt-in/out requires a preferences table; adds a lookup on every notification.
- If yes → preferences table required
- If no → simpler (all-or-nothing opt-in)

**Q: "What's the scale — how many users, how many notifications per user per day?"**
- Why ask: Drives throughput, sharding strategy, and channel selection (SMS at 1M notifs/day is cheap; at 1B/day becomes expensive).

**Q: "Are notifications event-driven (triggered by user actions) or batch (scheduled digest emails)?"**
- Why ask: Event-driven = synchronous + Kafka; batch = scheduled jobs + cron.
- If event-driven → Kafka → fan-out service
- If batch → cron job + batch processor

---

## Section 3 — 📋 Requirements (Functional + Non-Functional)

**Functional Requirements:**
- System should send notifications to users via email (primary), SMS (secondary), and push notifications (tertiary)
- Notifications are triggered by events from upstream services (document signed, payment received, etc.)
- System should respect user preferences (opt-in/out per channel, do-not-disturb hours)
- System should provide a notification history (users can view past notifications)
- Out of scope: In-app messaging, webhook delivery, SMS/email template customization (assume templates are pre-defined)

**Non-Functional Requirements:**
- Scale: 10M users, 100 notifications per user per day = 1B notifications/day (~11.6K notifs/sec baseline, ~35K peak)
- Latency: P99 notification delivery < 30 seconds (email can be slower than push)
- Availability: 99.9% SLO (9 hours downtime/year)
- Delivery guarantee: **At-least-once** (acceptable to resend notifications; consumer idempotency handles duplicates)
- Durability: Notifications are queued durably (SQS/Kafka) — no loss even if service restarts
- Multi-channel coordination: Fan-out to all configured channels in parallel; fail one channel without blocking others
- Rate limiting: Max 100 notifications per user per hour (avoid spam)
- Compliance: GDPR (user can request deletion of notification history), CAN-SPAM (unsubscribe links in emails)

---

## Section 4 — 🔢 Scale Estimation (Minutes 5–10)

**Traffic:**
- DAU: 10M users
- Notifications/day: 100 per user = 1B notifications/day
- Notifications/sec: 1B ÷ 86,400 = 11.6K notifs/sec baseline
- Peak (3×): 35K notifs/sec
- Per-channel breakdown (assume): 60% email, 30% SMS, 10% push
  - Email: 21K notifs/sec baseline
  - SMS: 10.5K notifs/sec baseline
  - Push: 3.5K notifs/sec baseline

**Provider limits (typical):**
- SendGrid: up to 100K emails/sec (plenty)
- Twilio: up to 10K SMS/sec (need to shard at 10K+ or use multiple accounts)
- Firebase Cloud Messaging: up to 30K push/sec (plenty)

**Storage:**
- Per notification record: ~500 bytes (user_id, channel, message, status, timestamp)
- Notifications/year: 1B × 365 = 365B notifications = 182 GB/year
- At 5 years: 910 GB (fits in one Postgres instance; archive after 1 year to S3)

**Bandwidth:**
- Inbound (event ingestion): 35K notifs/sec × 2 KB = 70 MB/sec
- Outbound (to email/SMS providers): 35K notifs/sec × 1 KB = 35 MB/sec

**Key conclusions:**
- At 35K notifs/sec, **single Kafka broker is sufficient** (typical throughput ~100K msgs/sec); add 3 brokers for HA
- At 21K emails/sec, **SendGrid is fine** (capacity is 100K/sec)
- At 10.5K SMS/sec, **single Twilio account is at the limit**; might need shard by region
- At 182 GB/year, **single Postgres instance handles easily**; archive after 1 year to cold storage

---

## Section 5 — 🔄 Requirements Variation Table ⭐ Key Differentiator

| Requirement | Small scale (1K notifs/day) | Large scale (1B notifs/day) | Impact on design |
|---|---|---|---|
| **Throughput** | 0.01 notifs/sec | 35K notifs/sec peak | Single-thread processing → Kafka sharding; single DB → read replicas |
| **Channels** | Email only (simplest) | All three (email + SMS + push) | Direct API call per channel → fan-out service; webhook fanout |
| **Delivery guarantee** | At-least-once OK | Exactly-once required (legal/payments) | Simple retry → idempotency table + deduplication |
| **Latency SLO** | 1 hour acceptable | P99 < 30s required (time-sensitive events) | Batch processing → real-time streaming (Kafka) |
| **User preferences** | All-or-nothing opt-in | Per-channel + per-notification-type opt-in | Simple flag → preferences table + lookup on every notification |
| **Rate limiting** | Global limit | Per-user per-hour limit | Static threshold → token bucket + Redis |

---

## Section 6 — 🏗️ High-Level Architecture (Minutes 10–25)

### 🎨 ASCII Architecture Diagram

```
  REAL-TIME NOTIFICATION SERVICE — HIGH-LEVEL ARCHITECTURE
  ───────────────────────────────────────────────────────────────

  UPSTREAM SERVICES (event sources)
  ┌─────────────────────────────────┐
  │ Document Service                │
  │ (document.signed, document.created) │
  │ Payment Service                 │
  │ (payment.success)               │
  │ Order Service (order.placed)    │
  └──────────────┬──────────────────┘
                 │
                 ▼
         ┌────────────────────┐
         │  Event Processor   │
         │ (normalize events) │
         └────────┬───────────┘
                  │
                  ▼
         ┌────────────────────┐
         │ Kafka (with Outbox │
         │ pattern from DB)   │
         │  ┌──────────────┐  │
         │  │ Partitioned  │  │
         │  │ by user_id   │  │
         │  └──────────────┘  │
         └────────┬───────────┘
                  │
    ┌─────────────┼─────────────┐
    │             │             │
    ▼             ▼             ▼
┌─────────┐  ┌─────────┐  ┌─────────┐
│  Email  │  │   SMS   │  │  Push   │
│ Delivery│  │Delivery │  │Delivery │
│ Service │  │ Service │  │ Service │
│ (SQS)   │  │ (SQS)   │  │ (SQS)   │
└────┬────┘  └────┬────┘  └────┬────┘
     │             │            │
     ▼             ▼            ▼
┌─────────────┐ ┌───────┐ ┌──────────┐
│ SendGrid/   │ │Twilio │ │Firebase  │
│ SES         │ │       │ │ Cloud    │
│             │ │       │ │ Messaging│
└──────┬──────┘ └───┬───┘ └────┬─────┘
       │            │          │
       ▼            ▼          ▼
    USERS' INBOXES (email, SMS, push notifications)

SUPPORTING INFRASTRUCTURE
───────────────────────────────────────────────────────────────
┌─────────────────────────────────────────────────────────────┐
│  Redis Cache                                                │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ • Rate limits (per-user/hour quota)                    │ │
│  │ • Notification preferences cache (opt-in/out)         │ │
│  │ • Idempotency key cache (24h retention)              │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  PostgreSQL                                                 │
│  ┌────────────────────────────────────────────────────────┐ │
│  │ • notification_history (user sees past notifs)        │ │
│  │ • user_preferences (opt-in/out per channel)           │ │
│  │ • notification_status (tracking: PENDING, SENT, FAILED)│ │
│  │ • idempotency_keys (for exactly-once semantics)       │ │
│  │ • outbox (for reliable Kafka publish)                 │ │
│  └────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────┐
│  Dead-Letter Queue (DLQ)                                    │
│  Failed notifications → manual inspection → retry queue    │
└─────────────────────────────────────────────────────────────┘

KEY INVARIANT:
   Kafka partitions by user_id to guarantee ordering:
   "All notifications for user_id=john arrive in sequence."
   This prevents race conditions (e.g., "Document rejected"
   before "Document ready" confuses the user).
```

**Data flow walkthrough (say this out loud):**

**Flow 1 — Event ingestion (synchronous outbox pattern):**
1. Document Service finishes signing, calls `POST /v1/events/document.signed`
2. Notification Service receives event, extracts payload (user_id, document_title, etc.)
3. **Transaction begins:**
   - Insert into `notification_history` (what we'll show to user later)
   - Insert into `outbox` table (marker: "notify user X about document signing")
   - **Both committed atomically**
4. **Separately (out of transaction):** Outbox processor polls the `outbox` table, publishes to Kafka topic `notifications.events`, updates `outbox.status = SENT`
5. If Kafka publish fails → outbox processor retries (row stays PENDING)

**Flow 2 — Kafka to fan-out (asynchronous, multi-channel):**
1. Kafka consumer pulls event from `notifications.events` partition (keyed by user_id for ordering)
2. **Fan-out service:**
   - Check `user_preferences`: is user opted in for email? SMS? Push?
   - Check rate limit (Redis): has user exceeded 100 notifs/hour?
   - If rate limit exceeded → silently drop (or queue for later)
   - For each enabled channel: enqueue to the appropriate SQS queue (email-queue, sms-queue, push-queue)
3. Each channel's SQS queue is consumed by the respective delivery service

**Flow 3 — Channel delivery with retry (e.g., email):**
1. Email Delivery Service pops message from `email-queue`
2. Lookup user's email from a profile service
3. Check idempotency_keys table: have we already sent an email with this event_id to this user?
4. If yes → return 200 OK (idempotent replay)
5. If no:
   - Insert into `idempotency_keys` (key = event_id + user_id, status = IN_PROGRESS)
   - Call SendGrid: `POST /api/sendgrid/send`
   - If success → update `idempotency_keys.status = SUCCESS`, update `notification_status.status = SENT`
   - If failure → retry with exponential backoff (1s, 2s, 4s, 8s, 16s, 32s, 64s)
   - After 7 retries (max 127s total) → send to DLQ (dead-letter queue) for manual inspection
6. If user unsubscribed (opt-out) → skip delivery, mark as `SKIPPED`

**Why each component:**
- **Kafka (partitioned by user_id)**: Guarantees ordering per user; prevents race conditions; scales to 35K msgs/sec
- **Outbox pattern**: Atomic DB write + event publish; prevents lost notifications (dual-write problem solved)
- **Fan-out service**: Normalizes one incoming event to N channels; fan-out logic is centralized
- **SQS queues** (per-channel): Decouples notification ingestion from delivery; each channel scales independently
- **Redis cache**: Rate limiting (token bucket), preferences lookup (hot), idempotency keys (fast dedup)
- **Postgres**: Notification history (audit/compliance), preferences storage, idempotency table (exactly-once guarantees)
- **DLQ**: Failed notifications don't get lost; ops can manually retry after investigating root cause

---

## Section 7 — 🔬 Core Component Deep Dives (Minutes 25–40)

### Deep Dive 1: Outbox Pattern for Reliable Event Publishing

**Why this is the most critical component:**
Without the outbox, notifications can be lost silently. The Notification Service inserts into `notification_history` and publishes to Kafka, but one can fail without the other. Result: DB thinks the notification was sent (user sees it in history), but Kafka never got the event (so no email is actually sent). The outbox pattern moves the "publish to Kafka" operation **into the same database transaction** as the insert, guaranteeing atomicity.

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Option A: Direct Kafka publish** (synchronous, no outbox) | Simple; fewer moving parts | Dual-write problem: if Kafka fails, notification is in DB but not published. If DB fails after Kafka commit, Kafka has the event but DB doesn't. |
| **Option B: Outbox pattern** (async processor) | Atomic DB + Kafka publish; retries are automatic (processor retries); at-least-once delivery | Slightly more complex; requires a background processor (one more service to operate); 100ms lag before Kafka publish |
| **Option C: Event sourcing** (all state is events) | True single source of truth; easy audit trail; no dual-write | Overkill for this problem; slow queries (must replay events); large storage overhead |

**Decision: Option B (Outbox pattern).**

Because it balances atomicity (DB write + Kafka publish are transactional) with operational simplicity (no event sourcing complexity). At-least-once delivery is acceptable; the idempotency layer handles duplicates.

**Implementation sketch:**

```java
public class NotificationService {
    private final JdbcTemplate jdbc;
    private final KafkaTemplate kafkaTemplate;

    /**
     * Create a notification and publish event (outbox pattern).
     * Both the notification insert and the outbox insert happen in ONE transaction.
     */
    @Transactional
    public void createNotification(String userId, NotificationEvent event) {
        // Step 1: Insert notification_history (user can query this via API)
        jdbc.update(
            "INSERT INTO notification_history (id, user_id, event_type, message, created_at, status) " +
            "VALUES (?, ?, ?, ?, ?, ?)",
            UUID.randomUUID(), userId, event.getType(), event.getMessage(),
            Instant.now(), "CREATED"
        );

        // Step 2: Insert outbox entry (marker for async processor)
        // This is IN THE SAME TRANSACTION as Step 1.
        // If the transaction commits, BOTH rows are in the database.
        // If it rolls back, NEITHER row exists. No dual-write inconsistency.
        jdbc.update(
            "INSERT INTO outbox (aggregate_id, event_type, payload, status, created_at) " +
            "VALUES (?, ?, ?, ?, ?)",
            userId, event.getType(), event.toJson(), "PENDING", Instant.now()
        );
        // Transaction auto-commits here.

        // Step 3: Return to caller immediately.
        // The outbox processor will publish to Kafka asynchronously.
    }
}

public class OutboxProcessor {
    private final JdbcTemplate jdbc;
    private final KafkaTemplate kafkaTemplate;

    /**
     * Background job: poll outbox table, publish to Kafka, mark as SENT.
     * Runs continuously (e.g., every 100ms or triggered by events).
     * If Kafka is down, processor retries indefinitely.
     * The notification is durably stored (doesn't get lost).
     */
    @Scheduled(fixedRate = 100)  // every 100ms
    public void processPendingEvents() {
        // Poll for unsent outbox entries
        List<OutboxEntry> pending = jdbc.query(
            "SELECT * FROM outbox WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT 100",
            (rs, rowNum) -> new OutboxEntry(
                rs.getLong("id"),
                rs.getString("aggregate_id"),
                rs.getString("event_type"),
                rs.getString("payload")
            )
        );

        for (OutboxEntry entry : pending) {
            try {
                // Publish to Kafka
                kafkaTemplate.send(
                    "notifications.events",
                    entry.getAggregateId(),  // key = user_id (for partitioning)
                    entry.getPayload()       // value = event JSON
                ).get(5, TimeUnit.SECONDS);  // wait for Kafka to confirm

                // Mark as SENT only after Kafka confirms
                jdbc.update(
                    "UPDATE outbox SET status = 'SENT', sent_at = ? WHERE id = ?",
                    Instant.now(), entry.getId()
                );
            } catch (Exception e) {
                // Kafka failed or timed out
                // DO NOT mark as SENT. Retry next cycle.
                // The outbox entry will stay PENDING forever (until Kafka recovers).
                logger.warn("Failed to publish outbox entry {}", entry.getId(), e);
                // Could add a retry counter here; after N retries, move to FAILED.
            }
        }
    }
}
```

**Why this deep dive matters:**
- The outbox pattern is the industry-standard solution to the dual-write problem
- At 35K notifs/sec, you must have a durable mechanism to avoid losing notifications
- The 100ms outbox processing delay is acceptable (notifications still arrive within seconds)

---

### Deep Dive 2: Multi-Channel Fan-Out with Rate Limiting

**Why this is the most critical component:**
Notifications go to multiple channels (email, SMS, push) in parallel. Each channel has different SLOs, limits, and failure modes. Email is cheap but slow (50ms); SMS is expensive and has per-account limits (Twilio: ~10K SMS/sec); push is fast but optional. At 35K notifs/sec, you need to **shard** the fan-out (partition by user_id) and **rate-limit** per user (prevent spam: max 100 notifs/hour) and per **channel** (respect SMS provider limits).

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Option A: Fan-out inline** (synchronous) | Simple; no queue; user gets immediate feedback | Blocks on slowest channel (email); if SMS fails, whole request fails; no retry mechanism |
| **Option B: Fan-out async to SQS** (one queue for all channels) | Reliable; retry built-in; decoupled | All channels share one queue; SMS backlog blocks emails; hard to debug which channel failed |
| **Option C: Fan-out async to SQS (per-channel queues)** | Each channel scales independently; failures are isolated; easy to debug | Slightly more complex (3 SQS queues) |

**Decision: Option C (per-channel SQS queues).**

Because SMS is the constraint (10K SMS/sec limit), and we don't want SMS backlog to affect email delivery. Each channel's SQS queue is consumed by its own consumer fleet, so they scale independently.

**Implementation sketch:**

```java
public class FanOutService {
    private final AmazonSQS sqs;
    private final RedisTemplate redis;
    private final UserPreferencesService preferences;

    /**
     * Fan out one incoming notification to multiple channels.
     * Called by the Kafka consumer listening to notifications.events.
     */
    public void fanOutNotification(String userId, NotificationEvent event) {
        // Step 1: Check user preferences
        UserPreferences prefs = preferences.get(userId);
        boolean emailEnabled = prefs.isEmailEnabled();
        boolean smsEnabled = prefs.isSmsEnabled();
        boolean pushEnabled = prefs.isPushEnabled();

        // Step 2: Rate limiting (per user, per hour)
        String rateLimitKey = "notif:ratelimit:" + userId;
        Long currentCount = redis.opsForValue().increment(rateLimitKey);
        if (currentCount == 1) {
            // First notification of this hour; set TTL
            redis.expire(rateLimitKey, Duration.ofHours(1));
        }
        if (currentCount > 100) {
            // User has exceeded quota; skip all channels
            logger.info("User {} exceeded notification quota. Dropping notification.", userId);
            return;
        }

        // Step 3: Enqueue to per-channel SQS queues
        if (emailEnabled) {
            sendToQueue(
                "notification-service-email-queue",
                new EmailMessage(userId, event)
            );
        }
        if (smsEnabled) {
            sendToQueue(
                "notification-service-sms-queue",
                new SmsMessage(userId, event)
            );
        }
        if (pushEnabled) {
            sendToQueue(
                "notification-service-push-queue",
                new PushMessage(userId, event)
            );
        }
    }

    private void sendToQueue(String queueName, Object message) {
        try {
            sqs.sendMessage(
                queueName,
                ObjectMapper.writeValueAsString(message)
            );
        } catch (Exception e) {
            // Log but don't throw; if SQS is down, other channels still work
            logger.error("Failed to enqueue message to {}", queueName, e);
        }
    }
}

/**
 * Email delivery consumer (runs in a separate fleet).
 */
public class EmailDeliveryConsumer {
    private final SendGrid sendGrid;
    private final JdbcTemplate jdbc;

    @SqsListener("notification-service-email-queue")
    public void deliverEmail(EmailMessage message) throws Exception {
        String userId = message.getUserId();

        // Step 1: Idempotency check
        String idempotencyKey = "email:" + userId + ":" + message.getEventId();
        Optional<String> cached = redis.opsForValue().get(idempotencyKey);
        if (cached.isPresent()) {
            // Already delivered; skip
            logger.info("Email already sent (idempotent). Skipping user {}.", userId);
            return;
        }

        // Step 2: Get user's email
        String email = userService.getEmail(userId);

        // Step 3: Send via SendGrid
        SendGrid.Request request = new SendGrid.Request();
        request.setMethod(SendGrid.Method.POST);
        request.setEndpoint("mail/send");
        request.setBody(buildEmailBody(email, message.getContent()));

        try {
            SendGrid.Response response = sendGrid.api(request);
            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                // Success
                redis.opsForValue().set(idempotencyKey, "sent", Duration.ofHours(24));
                updateNotificationStatus(userId, message.getEventId(), "SENT");
            } else if (response.getStatusCode() >= 500) {
                // Server error; retry
                throw new Exception("SendGrid returned " + response.getStatusCode());
            } else {
                // Client error (4xx); don't retry
                logger.error("SendGrid returned {}. Dropping message.", response.getStatusCode());
                updateNotificationStatus(userId, message.getEventId(), "FAILED");
            }
        } catch (Exception e) {
            // Retry: SQS will re-queue after visibility timeout
            logger.error("Failed to send email to user {}. Will retry.", userId, e);
            throw e;  // rethrow to trigger SQS retry
        }
    }
}
```

**Why this deep dive matters:**
- Fan-out to multiple channels requires handling different SLOs (email slow, SMS fast)
- Per-channel queues allow independent scaling (don't let SMS backlog block emails)
- Rate limiting prevents spam and respects user preferences
- Idempotency ensures duplicates don't result in duplicate emails

---

### Deep Dive 3: Retry Logic with Exponential Backoff + Dead-Letter Queue

**Why this is the most critical component:**
At 35K notifs/sec, you're making 35K API calls per second to SendGrid, Twilio, Firebase. Network failures are inevitable (1-2% of calls may fail due to timeouts, rate limits, or provider transients). Without retry logic, 350-700 notifications are silently lost every second. With smart retry logic (exponential backoff), transient failures recover automatically, and only permanent failures go to the DLQ.

**Options considered:**

| Option | Pros | Cons |
|---|---|---|
| **Option A: No retry** (send once, fail silently) | Simplest | Loses notifications on transient failures (unacceptable) |
| **Option B: Fixed retry** (retry N times at fixed interval) | Simple | Thundering herd: all failed requests retry at same time, overwhelming the provider |
| **Option C: Exponential backoff + jitter** (1s, 2s, 4s, 8s, 16s, 32s, 64s, + random jitter) | Smooth retry pattern; spreads load over time; recovers from transients | Slightly more complex |
| **Option D: Adaptive retry** (watch provider response codes, adjust backoff dynamically) | Optimal for provider health | Overkill; too complex for this problem |

**Decision: Option C (Exponential backoff + jitter).**

Because it handles the vast majority of transient failures without overwhelming the provider. After 7 retries (max 127s + jitter), we give up and move to the DLQ.

**Implementation sketch:**

```java
public class RetryableEmailDelivery {
    private static final int[] BACKOFF_MILLIS = {1000, 2000, 4000, 8000, 16000, 32000, 64000};
    private static final Random random = new Random();

    public void sendWithRetry(String userId, String email, String content) throws Exception {
        int retryCount = 0;
        Exception lastException = null;

        while (retryCount <= BACKOFF_MILLIS.length) {
            try {
                // Attempt to send
                sendViaProvider(email, content);
                return;  // success
            } catch (Exception e) {
                lastException = e;

                if (isTransientError(e)) {
                    // Transient (network error, 5xx, rate limit) → retry
                    if (retryCount < BACKOFF_MILLIS.length) {
                        long backoffMs = BACKOFF_MILLIS[retryCount];
                        long jitterMs = random.nextLong(backoffMs / 2);  // ±50% jitter
                        long sleepMs = backoffMs + jitterMs;

                        logger.info("Transient error for user {}. Retrying in {}ms (attempt {})",
                            userId, sleepMs, retryCount + 1);
                        Thread.sleep(sleepMs);
                        retryCount++;
                    } else {
                        // Max retries exceeded; give up
                        logger.error("Max retries exceeded for user {}. Moving to DLQ.", userId);
                        sendToDLQ(userId, email, content, lastException);
                        return;
                    }
                } else {
                    // Permanent error (4xx, invalid email) → don't retry
                    logger.error("Permanent error for user {}. Not retrying.", userId, e);
                    updateNotificationStatus(userId, "FAILED");
                    return;
                }
            }
        }
    }

    private boolean isTransientError(Exception e) {
        // 5xx = transient
        // Connection timeout = transient
        // Rate limit (429) = transient
        // 4xx (except 429) = permanent
        if (e instanceof HttpClientErrorException) {
            HttpClientErrorException httpError = (HttpClientErrorException) e;
            return httpError.getStatusCode().value() == 429;  // rate limit
        }
        if (e instanceof SocketTimeoutException || e instanceof ConnectTimeoutException) {
            return true;
        }
        if (e instanceof HttpServerErrorException) {
            return true;
        }
        return false;
    }

    private void sendToDLQ(String userId, String email, String content, Exception error) {
        // Enqueue to dead-letter queue for manual inspection
        deadLetterQueue.send(new DLQMessage(userId, email, content, error.toString()));
    }
}
```

**Why this deep dive matters:**
- Retry logic must distinguish transient (retry) from permanent (fail) errors
- Exponential backoff prevents thundering herd (all failed requests retrying simultaneously)
- Jitter spreads retries over time (smooth load on provider)
- DLQ catches permanent failures for manual inspection (ops can investigate + manually retry if appropriate)

---

## Section 8 — 🌐 API Design

### Core Endpoints

| Method | Path | Auth | Request body | Response | Status codes |
|---|---|---|---|---|---|
| POST | `/v1/notifications` | Internal (service-to-service) | `{user_id, event_type, content, channels: ["email", "sms"]}` | `{notification_id, status}` | 201, 400, 429 |
| GET | `/v1/notifications/{user_id}` | JWT Bearer | — | `{notifications: [{id, type, content, sent_at, read_at}], cursor}` | 200, 403, 404 |
| PUT | `/v1/users/{user_id}/preferences` | JWT Bearer | `{email_enabled: true, sms_enabled: false, push_enabled: true, quiet_hours: {start: "22:00", end: "08:00"}}` | `{user_id, preferences}` | 200, 400 |

### Key Design Decisions

- **Service-to-service auth:** POST `/v1/notifications` is internal only (called by Document Service, Order Service, etc.). Protect with API key or mTLS.
- **Pagination:** GET notifications uses cursor-based pagination (keyset pagination by `created_at DESC, id DESC`) to handle millions of notifications per user.
- **Quiet hours:** User can specify "don't send notifications between 10 PM and 8 AM." Fan-out service checks this before enqueuing.
- **Rate limiting:** Global per-tenant, per-user (100 notifs/hour). Excess notifications are dropped silently (no error to caller).
- **Versioning:** `/v1/` in path.

---

## Section 9 — 🗄️ Data Model

### Core Tables

```sql
CREATE TABLE notification_history (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    event_type VARCHAR(50),  -- "document.signed", "payment.received"
    message TEXT,  -- user-facing message
    channels VARCHAR(20)[] DEFAULT ARRAY[]::VARCHAR[],  -- which channels received it
    read_at TIMESTAMP,  -- NULL until user opens notification
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_user_created (user_id, created_at DESC)
);

CREATE TABLE user_preferences (
    user_id UUID PRIMARY KEY,
    email_enabled BOOLEAN DEFAULT TRUE,
    sms_enabled BOOLEAN DEFAULT FALSE,
    push_enabled BOOLEAN DEFAULT TRUE,
    
    quiet_hours_start TIME,  -- e.g., "22:00"
    quiet_hours_end TIME,    -- e.g., "08:00"
    quiet_hours_tz VARCHAR(40),  -- user's timezone (for quiet hour calc)
    
    unsubscribed_at TIMESTAMP,  -- opt-out timestamp (GDPR: can request this)
    
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE notification_status (
    notification_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    event_type VARCHAR(50),
    
    email_status VARCHAR(20),  -- 'PENDING', 'SENT', 'FAILED', 'SKIPPED'
    sms_status VARCHAR(20),
    push_status VARCHAR(20),
    
    email_sent_at TIMESTAMP,
    sms_sent_at TIMESTAMP,
    push_sent_at TIMESTAMP,
    
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_user_status (user_id, created_at DESC)
);

-- Outbox table for reliable Kafka publishing (dual-write prevention)
CREATE TABLE outbox (
    id BIGSERIAL PRIMARY KEY,
    aggregate_id UUID NOT NULL,  -- user_id
    event_type VARCHAR(50),
    payload TEXT,  -- JSON event
    status VARCHAR(20) CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    sent_at TIMESTAMP,
    
    INDEX idx_pending (status, created_at)
);

-- Idempotency keys for exactly-once delivery semantics
CREATE TABLE idempotency_keys (
    key VARCHAR(128) PRIMARY KEY,  -- event_id + user_id
    response TEXT,  -- stored response
    status VARCHAR(20),  -- 'IN_PROGRESS', 'SUCCESS', 'FAILED'
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP WITH TIME ZONE,
    
    INDEX idx_expires (expires_at)
);
```

### Key Schema Decisions

- **notification_history.channels**: Array of channel names (PostgreSQL ARRAY type) for quick filtering ("show me all email notifications")
- **notification_status**: Separate table to track per-channel delivery status (email might FAIL, but SMS succeeds)
- **outbox table**: For the outbox pattern; processor polls this table and publishes to Kafka
- **idempotency_keys**: Fast lookup to avoid duplicate deliveries; expired keys are cleaned up daily
- **quiet_hours_tz**: User's timezone; server converts "quiet_hours = 22:00 to 08:00 in Asia/Kolkata" to UTC for consistent server-side logic

---

## Section 10 — ⚠️ Trade-Offs + Failure Modes (Minutes 45–52)

### Trade-off 1: Delivery Guarantee (At-Least-Once vs Exactly-Once)

**Chose:** At-least-once (with idempotency on the consumer side = effectively exactly-once from the user's perspective).

**Gain:** Simpler to implement (outbox pattern is standard); no coordination between services needed; consumer idempotency is isolated.

**Lose:** Outbox processor may publish the same event twice to Kafka if it crashes after Kafka confirms but before marking the outbox entry as SENT. Consumer gets the event twice, but idempotency deduplication handles it.

**Failure mode if wrong:** If you try to guarantee exactly-once end-to-end (transactional outbox + Kafka + idempotency), you add 40% latency and 2-3× complexity. At-least-once + idempotency is simpler and sufficient.

---

### Trade-off 2: Per-Channel Queues vs Single Fan-Out Queue

**Chose:** Per-channel SQS queues (email-queue, sms-queue, push-queue).

**Gain:** Email consumer can process 21K emails/sec without being blocked by SMS backlog; SMS queue can have its own scaling policy (respect Twilio limits).

**Lose:** Slightly more complexity (manage 3 queues instead of 1); more operational overhead.

**Failure mode if wrong:** If you use a single queue and SMS provider is slow, emails get delayed behind SMS messages. Users see a 5-minute delay in receiving emails, which feels broken.

---

### Trade-off 3: Synchronous Preferences Lookup vs Cached Preferences

**Chose:** Redis cache (5-minute TTL) + fallback to DB.

**Gain:** At 35K notifs/sec, 35K preference lookups/sec would hit the DB hard. Redis cache at 100 microseconds per lookup is feasible.

**Lose:** Stale preferences (up to 5 minutes); if user disables SMS at 12:00 PM, they might get 2-3 SMS messages until cache expires.

**Failure mode if wrong:** If you do synchronous DB lookup per notification, DB becomes the bottleneck (typically 10-20 notifs/sec max for a single DB). You'll need read replicas and caching anyway.

---

## Section 11 — 🔐 DocuSign-Specific Depth (Minutes 52–57)

**Why this question is on the DocuSign R2 interview:**

Notifications are critical to DocuSign's product. When a document is ready for signature, DocuSign must notify the signer — via email (formal), SMS (urgent), or push (on-mobile users). If a notification is lost, the signer never signs, the customer's workflow breaks, and customer support gets a ticket. Non-repudiation also requires notifications: "We notified you on 3 June at 2 PM" must be provable (audit trail).

**DocuSign-specific angles your answer must address:**

1. **Multi-signer coordination**: When a document has 5 signers, notifications must arrive in order. Signer 1 gets notified → signs → Signer 2 gets notified (not before). Kafka partitioning by user_id + ordered processing ensures this.

2. **Audit trail**: Every notification is logged (when sent, to which channel, success/failure). For legal compliance, DocuSign keeps these logs forever. GDPR allows GDPR right-to-deletion, but audit logs of **sent** notifications are exempt (they're evidence of compliance).

3. **Quiet hours + business context**: DocuSign customers are global enterprises. A notification sent at 3 AM to a Tokyo office worker is rude. Quiet hours must respect the user's timezone.

4. **Compliance (CAN-SPAM, GDPR)**: Every email notification must have an unsubscribe link (CAN-SPAM requirement). User must be able to opt out per channel without opting out entirely.

5. **High availability**: Notifications are on the critical path. If the notification service is down, customers can't sign documents. 99.9% SLO is non-negotiable.

**Your answer should include:**

> "Notifications are event-driven via Kafka. When a document is ready for the next signer, the Document Service publishes an event. The Notification Service consumes it, checks the signer's preferences (cached in Redis), respects quiet hours (converted to the user's timezone), and fans out to the user's enabled channels (email, SMS, push) via separate SQS queues. Each channel retries with exponential backoff on transient failures. Idempotency keys prevent duplicate emails (if the Kafka consumer restarts). The entire flow is audited: every notification delivery attempt is logged to notification_status for compliance. If a notification fails permanently, it goes to a dead-letter queue for manual inspection by support."

> "For multi-signer workflows, Kafka partitions notifications by signer_id, ensuring all notifications for signer John are processed in order. If signer #2 rejects the document, a new notification is sent back to signer #1 (in sequence), and the audit trail shows the full signing attempt history."

---

## Section 12 — 🔬 Where the Interviewer Will Probe (Minutes 57–60)

### Tier 1 — Surface Probe (Do you understand async systems?)

**Q: "What happens if a user opts out of SMS after we've already enqueued the notification to the SMS queue but before we've sent it?"**

> The SMS queue consumer checks user preferences again (Redis cache + DB fallback) before actually calling Twilio. If the user unsubscribed, the consumer skips delivery and marks the notification as 'SKIPPED' instead of 'SENT'. This is safe because we don't modify the queue — we just skip processing for that user.

### Tier 2 — Deep Probe (Do you understand failure modes?)

**Q: "What if Twilio's SMS API is down for 1 hour? Will users get spammed with notifications when it comes back online, or do we have backpressure?"**

> Good question. The SMS queue is durable (SQS), so notifications accumulate while Twilio is down. When Twilio comes back online, the SMS consumer starts processing the backlog. With exponential backoff, the first message will retry immediately, the second after 1s, etc. This spreads the load over ~2 minutes instead of a thundering herd. Also, **the SQS queue visibility timeout is set to 30s**, so if Twilio is still slow, messages go back to the queue and other SMS consumer instances can pick them up. This prevents a single slow instance from blocking the entire queue.

### Tier 3 — Cross-Concept Probe (Can you reason across concepts?)

**Q: "How does your notification service interact with the audit system? If a customer claims they never received a signing request, how do you prove them wrong?"**

> The notification_history table is immutable (append-only, indexed by user_id + created_at). When the Document Service triggers a signature request, the Notification Service creates a notification_history entry (proof: "we sent a notification at 2026-06-24 14:30 UTC"). The notification_status table tracks delivery: we have proof that the email was sent successfully (email_status = 'SENT', email_sent_at = '2026-06-24 14:30 UTC'). We also log the provider's response (SendGrid returned 200 OK). If the customer claims they didn't receive the email, we check if the email was marked spam by their mail provider (out of our control), or we resend it. The audit trail proves we did our part.

---

## Section 13 — 🐞 Common Mistakes on This Question

- **Mistake 1:** "I'll have one queue for all notification channels." → **Why it's wrong:** SMS is the bottleneck (Twilio max ~10K SMS/sec). If SMS queue gets congested, emails get delayed. Users see 5-minute email latency, which is unacceptable. → **What to say instead:** "Per-channel SQS queues. Each channel has independent scaling. Email consumer fleet can have 10 instances, SMS can have 5, push can have 3. They don't interfere."

- **Mistake 2:** "I'll call the user preference service for every notification (synchronous lookup)." → **Why it's wrong:** At 35K notifs/sec, you're making 35K preference lookups/sec to the database. Most databases can't sustain more than 1K concurrent queries. You'll need caching anyway. → **What to say instead:** "Redis cache for user preferences, 5-minute TTL. Fallback to database on cache miss. This handles 35K lookups/sec with sub-100ms latency."

- **Mistake 3:** "If the outbox processor crashes, notifications are lost." → **Why it's wrong:** Outbox entries are durable in the DB. The processor is stateless and restartable. When it restarts, it will re-process all PENDING outbox entries. Nothing is lost. → **What to say instead:** "Outbox entries are append-only in the database. The processor is stateless and restartable. If it crashes, the next instance reads the same PENDING entries and processes them. At-least-once delivery is guaranteed."

---

## Section 14 — 🧭 DocuSign Dimensions Checklist

| Dimension | Relevant? | How your design addresses it |
|---|---|---|
| **Testability** | ✅ | Each component is independently testable: verify email delivery with mocked SendGrid; test retry logic with simulated failures; test idempotency with duplicate requests. No end-to-end external dependency required. |
| **Usability** | ✅ | Users can manage preferences (email/SMS/push) via API. Quiet hours respect timezone. Unsubscribe links in emails are clickable (CAN-SPAM). Notification history is queryable. |
| **Extensibility** | ✅ | New channels can be added (Slack, Teams) by creating a new SQS queue + consumer. Existing logic (fan-out, retry, idempotency) doesn't change. |
| **Security** | ✅ | Service-to-service auth (API key) on `/v1/notifications` endpoint. User preferences are tenant-isolated. Notifications contain no sensitive data (metadata only; content is in doc URL). |
| **Availability** | ✅ | 99.9% SLO achieved: Kafka + SQS + multi-instance consumers. If one SMS consumer fails, others process its queue. Outbox processor retries forever (no message lost). |
| **Scalability** | ✅ | Kafka partitions by user_id (50 partitions = 50 parallel consumers). SQS auto-scales based on queue depth. Postgres holds notification_history (indexed); archive to S3 after 1 year. At 1B notifs/day × 5 years = only 5TB (manageable). |
| **Observability & Traceability** | ✅ | Every notification has a unique ID. Trace from event → Kafka → fan-out → SQS → delivery. Log at each step (event received, preferences checked, queued, sent/failed). CloudWatch metrics: notif_count, delivery_latency_p99, retry_rate. |

---

## Section 15 — 🧾 TL;DR Answer Summary

> "A real-time notification service at scale (35K notifs/sec) requires three critical pieces: (1) **reliable event publishing** via the outbox pattern (atomic DB insert + Kafka publish in one transaction); (2) **independent channel delivery** with per-channel SQS queues (email/SMS/push scale independently, no queue bottleneck); (3) **smart retry logic** with exponential backoff + jitter (handles transient failures without thundering herd, moves permanent failures to DLQ). The system respects user preferences (cached in Redis), enforces quiet hours (timezone-aware), and maintains full audit trails (notification_history + notification_status). Trade-offs: at-least-once delivery (acceptable with idempotency), per-channel queues (slightly more ops overhead but required for scale), and cached preferences (5-minute stale window acceptable). At 35K notifs/sec, the Kafka → fan-out → SQS → delivery flow completes in <5 seconds end-to-end, meeting DocuSign's requirement that signers are notified promptly when a document is ready to sign."

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 24, 2026 | **D3-notification-service.md created.** Full 15-section solution framework for Type A System Design. Covers: outbox pattern for reliable event publishing, per-channel fan-out (email/SMS/push), exponential backoff retry logic, idempotency for exactly-once delivery, rate limiting, and DocuSign-specific depth (multi-signer coordination, audit trails, quiet hours). Scale: 1B notifs/day, 35K notifs/sec peak. Prerequisites: `07-cdc-outbox.md`, `04-idempotency.md`. |
