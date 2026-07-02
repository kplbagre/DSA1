# 46 — Push Notifications / Fanout at Scale

---

## 📖 What is Push Notifications / Fanout at Scale?

**Full form:** Push Notifications + Fanout at Scale — mobile/web push delivery where one event (a tweet, an order update) is "fanned out" (copied and delivered) to potentially millions of subscriber devices.

**Simple analogy:** A newspaper printing plant — one journalist writes a story; the printing press runs 10 million copies; delivery trucks fan out to every doorstep. No journalist calls each reader directly.

**Core principle:** Fanout (the process of copying and delivering one event to many recipients) = decouple the event producer from individual delivery. Producer writes once to a queue; consumer workers read and deliver to each subscriber device token (a unique per-device identifier issued by the OS for push delivery) via APNs (Apple Push Notification Service — Apple's cloud relay that delivers pushes to iOS devices) or FCM (Firebase Cloud Messaging — Google's equivalent for Android). The queue absorbs the spike; workers scale horizontally.

**Why it matters in system design:** Any design involving real-time notifications (order tracking, social alerts, chat messages) needs you to explain device token management, fanout topology, delivery guarantees, and dead token cleanup. Without this, your design bottlenecks at the producer.

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|---|---|---|
| Device Token | A unique opaque string issued by the OS for each device + app installation; used to address pushes | An iOS APNs token: `abc123...` (changes on app reinstall) |
| APNs | Apple Push Notification Service — Apple's cloud relay that delivers pushes to iOS devices | Server calls APNs HTTP/2 API with device token + payload → iOS device wakes up |
| FCM | Firebase Cloud Messaging — Google's push relay for Android devices | Server calls FCM with registration token + payload → Android device wakes up |
| Fanout | Copying and delivering one event to many recipients — decoupling the single write from millions of individual deliveries | Celebrity posts → 1 Kafka event → 50M fan-out worker deliveries |
| Dead Token | A device token that APNs/FCM marks invalid because the user uninstalled the app or the token expired | APNs returns `BadDeviceToken`; worker must immediately mark token inactive in DB |
| Thundering Herd (push) | Millions of devices wake up simultaneously and hammer your API for content after receiving a notification | "You have a new post" notification → 50M devices all call `/api/feed` at once; fix by including full payload in push |
| Batch Push | Sending a single API call with up to 1,000 device tokens instead of one call per token to improve throughput | APNs HTTP/2 supports 1K concurrent streams per connection |
| Fanout Worker | A Kafka consumer pod that owns a partition and delivers notifications to its slice of subscribers | Worker pod 1 owns partition 0 and delivers to users 0–500K |

---

## 🎯 Why This Matters

Mobile clients don't poll servers 24/7 — they sleep. Push notifications wake them. But when a Bollywood celebrity posts, 50M followers need a notification within seconds. Naive direct push from the app server to all devices is a blocking bottleneck: the producer thread blocks on every APNs/FCM HTTP call.

Comes up in chat systems, social feeds, e-commerce order tracking, and ride-sharing status updates — any domain where a server-side event must reach a user's mobile device.

Senior expectation: name device token management, fanout queue topology (Kafka fan-out topic vs pre-computed subscriber lists), APNs/FCM delivery mechanics, retry/backoff for transient errors, and dead token detection and cleanup.

---

## 🧠 The Mental Model

Think of a WhatsApp group broadcast. When you send a message to a group of 256 people, WhatsApp does NOT send 256 separate HTTP requests from your phone. Instead: (1) your phone sends ONE message to WhatsApp's server; (2) the server stores it and puts a delivery task on a queue; (3) worker processes read from the queue and individually push to each member's device token via APNs (for iOS members) or FCM (for Android members); (4) if a device is offline, APNs/FCM queues the message internally and retries delivery when the device comes back online.

Now scale this to Instagram with a celebrity who has 50M followers. The celebrity's phone posts → ONE write to Instagram's database and ONE event published to a Kafka (distributed event streaming platform, used here as the fanout decoupling layer) "new-post" topic. N worker pods (fan-out workers) each own a Kafka partition and a slice of the subscriber ID space. Each worker reads its assigned slice, looks up device tokens in the DeviceToken DB, and calls APNs/FCM in batches of 1000.

The key insight is: the publishing bottleneck and the delivery bottleneck are separated. Publishers write once; horizontal worker scale handles delivery. The queue is the decoupling layer.

Three failure modes every candidate must name:

1. **Dead tokens:** Device tokens expire when the user uninstalls the app. APNs/FCM return a "BadDeviceToken" response on the next push attempt → the fan-out worker must delete or mark the token inactive in the DB immediately.
2. **Thundering herd:** A celebrity event triggers 50M notifications → all 50M devices wake up and ping your server for content simultaneously. Mitigation: include the full content payload in the notification body, not just a "you have an update" signal, so devices do not need to make a follow-up API call.
3. **APNs/FCM rate limits:** APNs enforces per-bundle-id QPS (queries per second) limits; FCM has per-sender-id limits. Mitigation: use batching (send 1000 tokens per API call) combined with exponential backoff on 429 / rate-limit responses.

---

## 🎨 Visual — System Topology & Component Flow

**Diagram 1 — Full System Topology**

```
┌──────────────────────────────────────────────────────────────────────┐
│                         CLIENT TIER                                  │
│                                                                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐      ┌──────────┐        │
│  │ iPhone 1 │  │ iPhone 2 │  │Android 1 │ ...  │Android N │        │
│  │ (~25M    │  │          │  │ (~25M    │      │          │        │
│  │  devices)│  │          │  │  devices)│      │          │        │
│  └────┬─────┘  └────┬─────┘  └────┬─────┘      └────┬─────┘        │
│       │              │              │                  │             │
└───────┼──────────────┼──────────────┼──────────────────┼─────────────┘
        │  APNs/FCM push delivery     │                  │
        ▲              ▲              ▲                  ▲
┌───────┼──────────────┼──────────────┼──────────────────┼─────────────┐
│       │   APPLE / GOOGLE PUSH INFRA │                  │             │
│  ┌────┴──────────────┴──┐   ┌───────┴──────────────────┴───┐        │
│  │  APNs                │   │  FCM                          │        │
│  │  (Apple Push         │   │  (Firebase Cloud Messaging    │        │
│  │   Notification Svc)  │   │   — Google's Android relay)  │        │
│  └──────────┬───────────┘   └──────────────┬────────────────┘        │
└─────────────┼──────────────────────────────┼─────────────────────────┘
              ▲                              ▲
┌─────────────┼──────────────────────────────┼─────────────────────────┐
│             │       WORKER TIER            │                         │
│  ┌──────────┴──────┐  ┌────────────────┐  ┌──────────────────────┐  │
│  │ Fan-out Worker 1 │  │Fan-out Worker 2│  │ Fan-out Worker N     │  │
│  │ (owns partition 0│  │(owns partition │  │ (owns partition N-1) │  │
│  │  of Kafka topic) │  │ 1 of topic)    │  │                      │  │
│  └──────────┬───────┘  └───────┬────────┘  └──────────┬───────────┘  │
│             │                  │                       │             │
└─────────────┼──────────────────┼───────────────────────┼─────────────┘
              │   consume from Kafka partitions          │
              ▲                  ▲                       ▲
┌─────────────┼──────────────────┼───────────────────────┼─────────────┐
│             │  MESSAGE BROKER  │                       │             │
│  ┌──────────┴──────────────────┴───────────────────────┴──────────┐  │
│  │                     Kafka — "notification-events" topic         │  │
│  │   partition 0           partition 1         partition N-1       │  │
│  │   [event A, event B]    [event C, D]        [event ...]         │  │
│  └──────────────────────────────┬───────────────────────────────────┘  │
└─────────────────────────────────┼───────────────────────────────────────┘
                                  ▲
                          publish event
┌─────────────────────────────────┼───────────────────────────────────────┐
│                         EVENT SOURCE                                    │
│  ┌──────────────────────────────┴──────────────────────────────────┐   │
│  │               App Server / Producer Service                      │   │
│  │  (e.g., Post Service, Order Service, Chat Service)               │   │
│  └──────────────────────────────┬───────────────────────────────────┘   │
│                                 │ write triggers                        │
│  ┌──────────────────────────────▼───────────────────────────────────┐   │
│  │               Event DB / Producer Datastore                       │   │
│  │  (Cassandra for follower lists, MySQL/PostgreSQL for order data)  │   │
│  └──────────────────────────────────────────────────────────────────┘   │
└────────────────────────────────────────────────────────────────────────┘
```

---

**Diagram 2 — Component Detail: Fanout Flow**

```
Celebrity posts
      │
      ▼
┌─────────────────────────────────────────┐
│  Post Service (Producer)                │
│  1. Writes post to Cassandra            │
│  2. Publishes NotificationEvent to      │
│     Kafka topic "notification-events"   │
│     partition key = celebrity_user_id   │
└─────────────────┬───────────────────────┘
                  │
                  ▼ Kafka topic partition 0
┌─────────────────────────────────────────┐
│  Fan-out Worker 1 (owns partition 0)    │
│                                         │
│  Step 1: Read NotificationEvent         │
│  Step 2: Paginate DeviceToken DB for    │
│          all follower user IDs          │
│          (page size = 1000 tokens)      │
└─────────────────┬───────────────────────┘
                  │
         ┌────────┴────────┐
         │                 │
         ▼                 ▼
┌────────────────┐  ┌──────────────────┐
│  iOS Tokens    │  │  Android Tokens  │
│  (batch 1000)  │  │  (batch 1000)    │
└───────┬────────┘  └────────┬─────────┘
        │                    │
        ▼                    ▼
┌───────────────┐  ┌─────────────────────┐
│  APNs API     │  │  FCM API            │
│  HTTP/2 call  │  │  HTTP/1.1 or gRPC   │
└───────┬───────┘  └──────────┬──────────┘
        │                     │
        ▼                     ▼
┌───────────────────────────────────────────────────────────────┐
│                 Handle API Response                           │
│                                                               │
│  SUCCESS          → mark delivered (optional audit log)       │
│                                                               │
│  BAD_DEVICE_TOKEN → tokenRepository.markInactive(token.id)   │
│  (token is dead)    dead token removed reactively             │
│                                                               │
│  RATE_LIMIT (429) → exponential backoff                       │
│  (too many calls)   scheduleRetry(event, token, retryAfterMs) │
│                                                               │
│  TRANSIENT ERROR  → retry up to 3 times with backoff          │
│  (5xx, timeout)                                               │
└───────────────────────────────────────────────────────────────┘

KEY INVARIANT:
  The producer writes once.
  The queue absorbs the spike.
  Workers fan out horizontally.
  Dead token cleanup happens reactively on APNs/FCM error response.
```

---

## ⚙️ How It Actually Works

### 4a — Device Token Registration

Steps:
1. App starts → the OS (iOS or Android) generates a device token (a unique opaque string per device + app bundle + environment; changes on app reinstall).
2. App sends the device token to your backend via `POST /devices`.
3. Backend stores: `user_id`, `device_token`, `platform` (IOS/ANDROID), `created_at`, `last_seen_at`.

```java
// Step 1 — DeviceToken entity stored in the device_token table
public class DeviceToken {
    private Long id;
    private Long userId;
    // APNs token (iOS) or FCM registration token (Android) — unique per device + app install
    private String token;
    private DevicePlatform platform;
    private Instant lastSeenAt;
    private boolean active;
}

// Step 2 — Registration endpoint: upsert on re-register since token can be refreshed by OS
public void registerDevice(Long userId, String token, DevicePlatform platform) {
    DeviceToken existing = tokenRepository.findByToken(token);
    if (existing != null) {
        // Token already known — refresh last_seen_at so cleanup jobs don't expire it
        existing.setLastSeenAt(Instant.now());
        existing.setActive(true);
        tokenRepository.save(existing);
        return;
    }
    DeviceToken deviceToken = new DeviceToken();
    deviceToken.setUserId(userId);
    deviceToken.setToken(token);
    deviceToken.setPlatform(platform);
    deviceToken.setLastSeenAt(Instant.now());
    deviceToken.setActive(true);
    tokenRepository.save(deviceToken);
}
```

---

### 4b — Publish Notification Event to Kafka

Steps:
1. Business event occurs (new order placed, new social post, new chat message).
2. Producer service constructs a `NotificationEvent` and publishes it to the `notification-events` Kafka topic.
3. Kafka partitions by `sourceUserId` so all events for the same celebrity land on the same partition — preserving ordering guarantees within one producer's event stream.

```java
// NotificationEvent is the Kafka message payload — includes all info workers need
public class NotificationEvent {
    private String eventId;
    // sourceUserId: the user who triggered the event (e.g., celebrity posting)
    private Long sourceUserId;
    private NotificationTargetType targetType;
    // TARGETED: push to specific userIds only; FANOUT_SUBSCRIBERS: push to all followers of sourceUserId
    private List<Long> targetUserIds;
    private String title;
    private String body;
    // data: key-value pairs for deep link, image URL, or content payload
    private Map<String, String> data;
    private Instant createdAt;
}

// publishNotificationEvent: called by Order Service, Post Service, Chat Service after each state change
public void publishNotificationEvent(NotificationEvent event) {
    // Partition key ensures events for the same source user go to the same partition
    String partitionKey = event.getSourceUserId().toString();
    kafkaTemplate.send("notification-events", partitionKey, event);
}
```

---

### 4c — Fan-out Worker with Dead Token Cleanup

Steps:
1. Worker reads `NotificationEvent` from its assigned Kafka partition.
2. For `FANOUT_SUBSCRIBERS` type: paginate through all subscriber device tokens for `sourceUserId` — do NOT load 50M rows into memory at once.
3. Batch tokens by platform (iOS vs Android) since APNs and FCM have separate APIs.
4. Call the APNs batch send API (for iOS tokens) and the FCM batch send API (for Android tokens).
5. On `BAD_DEVICE_TOKEN` response: mark token inactive in the DB immediately.
6. On `RATE_LIMIT` (HTTP 429): exponential backoff — schedule a retry with the vendor-provided `Retry-After` duration.

```java
// handleNotificationEvent: Kafka consumer entry point — one invocation per Kafka message
@KafkaListener(topics = "notification-events")
public void handleNotificationEvent(NotificationEvent event) {
    List<Long> targetUserIds = resolveTargetUsers(event);
    // Paginate to avoid OOM — process 1000 tokens at a time
    int page = 0;
    int pageSize = 1000;
    List<DeviceToken> tokens;
    do {
        // Fetch only active tokens to skip known-dead entries from previous runs
        tokens = tokenRepository.findActiveTokensForUsers(targetUserIds, page, pageSize);
        // Split by platform because APNs and FCM have different API clients and response formats
        Map<DevicePlatform, List<DeviceToken>> byPlatform = groupByPlatform(tokens);
        List<DeviceToken> iosTokens = byPlatform.getOrDefault(DevicePlatform.IOS, List.of());
        List<DeviceToken> androidTokens = byPlatform.getOrDefault(DevicePlatform.ANDROID, List.of());
        if (!iosTokens.isEmpty()) {
            sendViaApns(event, iosTokens);
        }
        if (!androidTokens.isEmpty()) {
            sendViaFcm(event, androidTokens);
        }
        page++;
    } while (tokens.size() == pageSize);
}

// sendViaApns: calls Apple Push Notification Service (APNs) HTTP/2 API per iOS token
private void sendViaApns(NotificationEvent event, List<DeviceToken> tokens) {
    for (DeviceToken token : tokens) {
        try {
            ApnsResponse response = apnsClient.sendNotification(
                token.getToken(),
                event.getTitle(),
                event.getBody()
            );
            if (response.isRejected() && "BadDeviceToken".equals(response.getRejectionReason())) {
                // APNs confirmed this token is permanently invalid — deactivate immediately
                tokenRepository.markInactive(token.getId());
            }
        } catch (ApnsRateLimitException e) {
            // APNs returned 429 — back off and re-enqueue with vendor-provided retry delay
            scheduleRetry(event, token, e.getRetryAfterMs());
        }
    }
}

// sendViaFcm: calls Firebase Cloud Messaging (FCM) API per Android token
private void sendViaFcm(NotificationEvent event, List<DeviceToken> tokens) {
    for (DeviceToken token : tokens) {
        try {
            FcmResponse response = fcmClient.sendNotification(
                token.getToken(),
                event.getTitle(),
                event.getBody()
            );
            if (response.isError() && "registration/invalid".equals(response.getErrorCode())) {
                // FCM confirmed this registration token is no longer valid — deactivate immediately
                tokenRepository.markInactive(token.getId());
            }
        } catch (FcmRateLimitException e) {
            // FCM returned 429 — back off and re-enqueue with vendor-provided retry delay
            scheduleRetry(event, token, e.getRetryAfterMs());
        }
    }
}

// resolveTargetUsers: for TARGETED events returns the explicit list; for FANOUT reads from follower store
private List<Long> resolveTargetUsers(NotificationEvent event) {
    if (event.getTargetType() == NotificationTargetType.TARGETED) {
        return event.getTargetUserIds();
    }
    // FANOUT_SUBSCRIBERS: load all follower user IDs for sourceUserId from Cassandra
    return followerRepository.findAllFollowerIds(event.getSourceUserId());
}

// groupByPlatform: partition a mixed list of tokens into iOS vs Android buckets
private Map<DevicePlatform, List<DeviceToken>> groupByPlatform(List<DeviceToken> tokens) {
    return tokens.stream().collect(Collectors.groupingBy(DeviceToken::getPlatform));
}

// scheduleRetry: requeues the delivery attempt after retryDelayMs using a delayed Kafka message or Redis sorted set
private void scheduleRetry(NotificationEvent event, DeviceToken token, long retryDelayMs) {
    RetryTask retryTask = new RetryTask();
    retryTask.setEvent(event);
    retryTask.setToken(token);
    retryTask.setScheduledAt(Instant.now().plusMillis(retryDelayMs));
    retryQueue.enqueue(retryTask);
}
```

**What is APNs, and why does it fit here?**

APNs (Apple Push Notification Service) is Apple's cloud service that maintains persistent TCP connections to every iOS device globally. Your server sends a push payload over HTTP/2 to Apple's APNs servers, and APNs relays it to the target device — even if the device is asleep or on a cellular network. FCM (Firebase Cloud Messaging) is Google's equivalent for Android. Both services eliminate the need for your backend to maintain persistent connections to millions of devices, which would be impossible at scale. In an interview, if asked: "APNs and FCM are the only reliable way to push to mobile devices — your server talks to Apple's or Google's infrastructure, and they relay it to the device using the OS-level push channel."

---

### 4d — Deduplication: Preventing Duplicate Pushes

Kafka's at-least-once delivery guarantee means a fan-out worker can receive the same `NotificationEvent` more than once — when a worker crashes and restarts, Kafka redelivers all uncommitted offsets from the last checkpoint. Without deduplication, the same user receives the same push twice.

**Two-layer defence:**

**Layer 1 — idempotency table** (catches duplicates at the worker level before any APNs/FCM call):

```java
// Attempt to claim this event — only one worker wins the INSERT for a given event_id
// ON CONFLICT DO NOTHING: if another worker already inserted this event_id, this insert silently no-ops
int rowsInserted = jdbcTemplate.update(
    "INSERT INTO processed_notifications (event_id, processed_at) VALUES (?, NOW()) ON CONFLICT (event_id) DO NOTHING",
    event.getEventId()
);

if (rowsInserted == 0) {
    // Already processed — skip to avoid duplicate push
    log.info("Duplicate event {} — skipping", event.getEventId());
    return;
}
// This worker owns the event — safe to call APNs/FCM
deliverToDevices(event);
```

**Layer 2 — APNs/FCM collapse key** (catches duplicates that escape the DB check, e.g., during DB network partition):

```java
// APNs: apns-collapse-id header — if two pushes with the same collapse key arrive
// at Apple before the device comes online, APNs coalesces them into one delivery
apnsRequest.setCollapseId(event.getEventId());

// FCM equivalent
fcmMessage.setCollapseKey(event.getEventId());
```

**Batch sizing — align to APNs/FCM API limits:**

| Recipient count | Page size | Rationale |
|---|---|---|
| < 10K (targeted push) | 100–500 | Low overhead; small memory footprint |
| 10K – 1M (segment push) | 1,000 | APNs HTTP/2 allows 1K concurrent streams per connection — batch at this boundary |
| > 1M (celebrity fanout) | 1,000 per worker + horizontal scale | Keep page size at API max; add workers to increase aggregate throughput |

APNs HTTP/2 multiplexing supports up to 1,000 concurrent push streams per connection. Batching at 1,000 tokens maximises throughput per APNs connection.

---

## 🏢 Real World — Where Companies Use This

- **Instagram** (celebrity fanout): When Virat Kohli posts, ~200M followers may have push notifications enabled. Instagram uses pre-computed "fan-out on write" — follower lists are stored in Cassandra, paginated by user_id range, and workers process in parallel at approximately 10K tokens/sec each. Write amplification is accepted because read latency matters more.
- **Swiggy** (order status push): Every order state change (PLACED → CONFIRMED → OUT_FOR_DELIVERY → DELIVERED) triggers a targeted push to exactly one user. No fanout complexity. Dead token cleanup runs as a nightly batch job since stale tokens in an order system are lower urgency than in social.
- **WhatsApp** (group messages): A group message to 256 members results in 256 individual push deliveries. APNs/FCM handle offline queuing internally. WhatsApp stores up to 30 days of offline messages server-side for users who are persistently offline.
- **Netflix** (content recommendations): "New episodes of Your Favourite Show" goes to millions of users simultaneously. Netflix uses scheduled fanout workers — not real-time — since content availability is known in advance. This allows off-peak batch processing to reduce infrastructure cost.
- **PhonePe** (payment success): High-value, time-sensitive single-user push. PhonePe uses a dedicated fast path that bypasses the batch Kafka queue for payment events, targeting sub-500ms end-to-end push delivery. Reliability and latency matter more than throughput here.

---

## 🧭 When to Use vs When NOT to Use

| Use this when | Do NOT use when |
|---|---|
| Mobile app users need real-time alerts without polling | All users are on desktop/web (use WebSocket or SSE instead) |
| Event affects 1 to millions of users (order update to chat broadcast) | Notification is non-urgent (batch email digest is cheaper and more reliable) |
| You need device-specific delivery guarantees (APNs handles offline queuing internally) | User base is entirely server-to-server (no mobile/browser push needed) |
| Separating event production from fan-out delivery is important for scale | Volume is low (<1K notifications/day) — simpler direct HTTP to APNs/FCM is fine |

**The common mistake:** Sending push directly from the business logic layer (blocking HTTP call to APNs per request in the same thread that handles the user's write). Under load — flash sale, celebrity event — this becomes a bottleneck: the producer thread blocks, connection pools exhaust, and the user's write request times out. Always decouple via Kafka or a dedicated notification queue.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Real-time delivery to offline devices via APNs/FCM offline queuing; horizontal fan-out scale via independent Kafka consumer workers; dead token detection keeps your DeviceToken DB clean and query times fast; APNs/FCM handle battery-optimized delivery (the OS batches low-priority notifications to reduce wake cycles) |
| **You lose** | Delivery is best-effort — APNs drops notifications after a device is offline for a vendor-defined period (typically 4 weeks for iOS); you cannot guarantee read receipts without explicit device-side acknowledgement sent back to your server |
| **Failure mode** | If fan-out workers fall behind (Kafka consumer lag spikes due to a slow APNs response), notifications arrive late — monitor consumer group lag as a key SLO metric. Thundering herd on content: millions of devices wake up and hammer your API for content simultaneously — always include the full payload in the push body, not just a "you have a notification" signal, to avoid this secondary spike |

---

## 🔬 Interview Q&As

**Q1 (Tier 1):** "How do you design push notifications for a system with 100M users where a celebrity posts and all followers must be notified within 30 seconds?"

> Publish one "new-post" `NotificationEvent` to a Kafka topic partitioned by `celebrity_user_id`. N fan-out workers each own a Kafka partition and a slice of the follower list (stored in Cassandra, paginated by user_id range). Each worker fetches device tokens in pages of 1000 and calls APNs/FCM batch API. Horizontal worker scale controls throughput. With 100 workers each processing 1K tokens/sec = 100K tokens/sec → 100M deliveries in ~1000 seconds. To hit 30 seconds, you need approximately 3300 workers or larger batch sizes. Identify the throughput constraint explicitly and state that you'd scale workers or increase batch size. In practice, Instagram achieves this with a combination of pre-sharded follower lists and prioritized delivery for highly-engaged users.

---

**Q2 (Tier 1):** "What happens when a device token expires or the user uninstalls the app?"

> APNs returns "BadDeviceToken" and FCM returns "registration/invalid" in their API response on the next push attempt after the token becomes invalid. The fan-out worker checks every API response and, on receiving this error code, immediately calls `tokenRepository.markInactive(token.getId())`. This prevents future push attempts to that token. Additionally, run a scheduled cleanup job (nightly or weekly) to hard-delete tokens that have been inactive for more than 30 days. Without this cleanup, your `device_token` table accumulates millions of dead tokens, making follower-to-token lookups progressively slower and bloating storage.

---

**Q3 (Tier 1):** "What is the difference between fan-out on write vs fan-out on read for notifications?"

> Fan-out on write: when a post is created, immediately update every follower's notification inbox or device token delivery queue. Pros: notifications can be pushed instantly; fan-out happens at write time so read is O(1). Cons: write amplification — 1 celebrity post → 50M writes across the DB and 50M APNs calls. Fan-out on read: do not write to followers at post time — when a follower opens the app, pull the celebrity's latest posts on demand. Pros: no write amplification; simpler producer path. Cons: impossible to push — you don't know when the follower will open the app, so you cannot trigger a notification. Hybrid strategy (used by Instagram): fan-out on write for users with fewer than 10K followers; fan-out on read for mega-celebrities with over 10M followers, delivering to a sampled or segmented subset of followers asynchronously rather than all 50M simultaneously.

---

**Q4 (Tier 2 — cross/probe):** "Your fan-out worker is processing a push for a Kafka event that has already been processed (duplicate due to consumer restart after a crash). How do you avoid sending the same push notification twice?"

> Two layers of defence. First, each `NotificationEvent` carries a unique `eventId` (UUID generated at publish time). Before processing, the worker executes an idempotency check: `INSERT INTO processed_notifications (event_id, processed_at) VALUES (?, NOW()) ON CONFLICT (event_id) DO NOTHING`. If the insert returns 0 rows affected, the event was already processed — skip it entirely. This is idempotency via a deduplication table. Second, APNs supports a collapse key (`apns-collapse-id` header): if two pushes with the same collapse key arrive at APNs before the device comes online, APNs coalesces them and delivers only the latest one. FCM has an equivalent `collapse_key` field. Set the collapse key to the `eventId` so even if a duplicate push escapes the DB check, the device receives only one notification.

---

**Q5 (Tier 2 — cross/probe):** "APNs has a QPS limit per Apple Developer account. If you have 1000 fan-out workers all calling APNs simultaneously, how do you prevent hitting the rate limit?"

> Two approaches, name both and state the trade-off. Approach 1: distributed rate limiter. Implement a token bucket (allows bursts up to a cap) or sliding window rate limiter in Redis, shared across all workers. Each worker acquires a permit from the Redis rate limiter before each APNs call. If no permit is available, the worker backs off with exponential delay and retries. This keeps APNs calls under the QPS budget without a central bottleneck service. Approach 2: centralize APNs calls in a dedicated gateway microservice. All fan-out workers enqueue push requests to this gateway service. The gateway owns the APNs client, enforces rate limits internally, and serializes APNs calls. Advantage: simplifies credential rotation (APNs requires a valid `.p8` key with a 1-year TTL — rotating it in one place is easier than across 1000 workers). Disadvantage: the gateway becomes a single point of failure and a throughput bottleneck. For most systems, the distributed Redis rate limiter approach is preferred because it keeps each worker self-sufficient and avoids a single-point-of-failure gateway.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> "Push notification fanout decouples the event producer (writes once to Kafka) from device delivery (horizontal fan-out workers call APNs/FCM in batches), but delivery is best-effort — always include the payload in the push to avoid a thundering herd when devices wake up."

---

## 🔗 Related Concepts

- `Core-Architecture/Service-Communication/19-message-queues-kafka-rabbitmq.md` — Kafka as the fanout decoupling layer; partitioning strategy applies directly to notification topics
- `Production-Grade/System-Design-Patterns/26-websocket-real-time-communication.md` — alternative real-time channel for desktop/web users (bidirectional, no APNs/FCM dependency)
- `Foundations/Performance-and-Scale/09-sharded-counters.md` — unread notification counts require sharded counters or Redis atomic increments to avoid hot-key contention
- `Core-Architecture/Resilience-and-Fault-Tolerance/35-retry-exponential-backoff-patterns.md` — retry logic for APNs/FCM transient errors (5xx, timeouts, 429 rate limits)

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **"Scaling Push Notifications at Facebook"** — Meta Engineering Blog | Real architecture decisions for billions of pushes daily — adds production scale perspective and infrastructure choices beyond what this note covers | ~15 min read |
| **APNs Provider API — Apple Developer Docs** | Exact HTTP/2 API spec for APNs, collapse keys, priority levels (immediate vs conserve-power), and token-based authentication with `.p8` keys | ~20 min read |
| **"Fan-out on write vs read"** — High Scalability Blog | Deep dive on the write amplification trade-off; Instagram's hybrid strategy for handling celebrities vs ordinary users | ~10 min read |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 1, 2026 | Added Section 4d — Deduplication as a first-class section (idempotency table with `ON CONFLICT DO NOTHING` + APNs/FCM collapse key as second layer); added batch sizing table aligned to APNs HTTP/2 1K stream limit. |
