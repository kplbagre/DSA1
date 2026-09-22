# 🧵 Kafka for Java Backend — Deep Dive

> After this note you can explain consumer groups and partition assignment, implement `@KafkaListener` with manual offset commit, design a dead letter queue, achieve exactly-once with Kafka transactions, and diagnose consumer lag.

---

## 🎯 The Problem This Solves

Your service needs to process 100,000 events/second from an upstream system. HTTP calls would overwhelm the service and couple it to the upstream's availability. Kafka decouples producers and consumers — the producer writes events to a topic, the consumer reads at its own pace. But the Java-level details — Spring Kafka's `@KafkaListener`, consumer group rebalancing, offset commit strategies, error handling, and exactly-once semantics — determine whether your consumer is reliable or loses/duplicates messages in production.

---

## 📖 Terminology

| Term | Definition |
|---|---|
| **Topic** | A named log of records. Producers write to topics. Consumers read from topics. A topic is divided into partitions. |
| **Partition** | An ordered, immutable log within a topic. Each message has an offset (sequential ID) within its partition. Partitions enable parallelism — multiple consumers can read different partitions simultaneously. |
| **Consumer group** | A named group of consumers that share the work of reading a topic. Each partition is assigned to EXACTLY ONE consumer in the group. If a consumer dies, its partitions are reassigned (rebalance). |
| **Offset** | The position of a message within a partition (0, 1, 2, ...). Consumers track their offset — "I've processed up to offset 42." Committed offset = checkpoint. On restart, consumer resumes from last committed offset. |
| **Rebalance** | When a consumer joins/leaves a group, Kafka reassigns partitions among the remaining consumers. During rebalance, consumption pauses — all partitions are revoked and reassigned. |
| **Dead Letter Queue (DLQ)** | A separate topic where messages that fail processing after N retries are sent. Prevents a poison message from blocking the entire partition. |
| **Exactly-once semantics (EOS)** | The guarantee that a message is processed exactly once — not lost (at-least-once) and not duplicated. Achieved with Kafka transactions + idempotent producer. |
| **Consumer lag** | The difference between the latest offset in a partition and the consumer group's committed offset. Lag > 0 means the consumer is behind. Growing lag = consumer can't keep up. |

---

## 🧠 Mental Model

Think of a Kafka topic as a **multi-lane highway**. Each lane is a partition. Cars (messages) enter lanes based on their key (cars with the same key always use the same lane — ordering within a key). A consumer group is a **team of toll booth operators** — each operator handles one or more lanes, but no two operators handle the same lane. If an operator goes on break (consumer dies), their lanes are reassigned to remaining operators (rebalance). The speedometer showing how far behind you are from the latest car = consumer lag.

> If you can say "partitions enable parallelism; one partition = one consumer in the group; consumer commits offset as a checkpoint; rebalance redistributes partitions; DLQ handles poison messages; exactly-once requires Kafka transactions + idempotent producer" without notes, you have Kafka for Java.

---

## 🎨 Visual — Consumer Group Assignment

```
  Topic: orders (6 partitions)

  Consumer Group: order-processor (3 consumers)

  ┌──────────────────────────────────────────────────────┐
  │  Partition 0 ───► Consumer A                         │
  │  Partition 1 ───► Consumer A                         │
  │  Partition 2 ───► Consumer B                         │
  │  Partition 3 ───► Consumer B                         │
  │  Partition 4 ───► Consumer C                         │
  │  Partition 5 ───► Consumer C                         │
  └──────────────────────────────────────────────────────┘

  Consumer C dies → REBALANCE:
  ┌──────────────────────────────────────────────────────┐
  │  Partition 0 ───► Consumer A                         │
  │  Partition 1 ───► Consumer A                         │
  │  Partition 2 ───► Consumer A   ← was C's             │
  │  Partition 3 ───► Consumer B                         │
  │  Partition 4 ───► Consumer B   ← was C's             │
  │  Partition 5 ───► Consumer B   ← was C's             │
  └──────────────────────────────────────────────────────┘

  RULE: # consumers > # partitions → some consumers are IDLE.
  6 partitions + 8 consumers → 2 consumers do nothing.
  Scale consumers up to partition count, not beyond.

KEY INVARIANT:
   One partition = one consumer per group.
   Ordering is guaranteed WITHIN a partition, not across partitions.
   Messages with the same key go to the same partition → ordered per key.
```

---

## 🪜 Build-up

---

### Level 1 — The naive approach (and why it fails)

```java
// ❌ Auto-commit + no error handling
@KafkaListener(topics = "orders", groupId = "order-processor")
public void process(String message) {
    Order order = objectMapper.readValue(message, Order.class);
    orderService.process(order);
    // Auto-commit: offset committed BEFORE processing completes.
    // If process() fails AFTER commit → message is lost (not re-delivered).
    // If process() succeeds but commit fails → message is re-delivered (duplicate).
    // No DLQ: a poison message (bad JSON) crashes this listener on every retry → partition blocked.
}
```

---

### Level 2 — The real mechanism

#### 2.1 — Spring Kafka producer

```java
// Tier 2 — Production: Kafka producer with Spring
@Configuration
public class KafkaProducerConfig {
    @Bean
    public ProducerFactory<String, OrderEvent> producerFactory() {
        Map<String, Object> props = Map.of(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092",
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class,
            ProducerConfig.ACKS_CONFIG, "all",           // wait for ALL replicas → durability
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,  // prevent duplicate sends on retry
            ProducerConfig.RETRIES_CONFIG, 3              // retry on transient failures
        );
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, OrderEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}

@Service
public class OrderEventPublisher {
    private final KafkaTemplate<String, OrderEvent> kafkaTemplate;

    public void publishOrderCreated(Order order) {
        OrderEvent event = new OrderEvent(order.getId(), "CREATED", Instant.now());
        // Key = orderId → all events for the same order go to the same partition → ordered
        kafkaTemplate.send("order-events", String.valueOf(order.getId()), event)
            .whenComplete((result, ex) -> {
                if (ex != null) {
                    log.error("Failed to publish order event: {}", order.getId(), ex);
                } else {
                    log.info("Published to partition {} offset {}",
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
                }
            });
    }
}
```

#### 2.2 — Spring Kafka consumer with manual commit

```java
// Tier 2 — Production: manual offset commit + error handling
@Configuration
public class KafkaConsumerConfig {
    @Bean
    public ConsumerFactory<String, OrderEvent> consumerFactory() {
        Map<String, Object> props = Map.of(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092",
            ConsumerConfig.GROUP_ID_CONFIG, "order-processor",
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class,
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false,   // ⭐ manual commit
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"  // start from beginning if no committed offset
        );
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderEvent> kafkaListenerContainerFactory() {
        var factory = new ConcurrentKafkaListenerContainerFactory<String, OrderEvent>();
        factory.setConsumerFactory(consumerFactory());
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        factory.setConcurrency(3);   // 3 consumer threads (match partition count / 2)
        return factory;
    }
}

@Component
public class OrderEventConsumer {

    @KafkaListener(topics = "order-events", groupId = "order-processor")
    public void consume(ConsumerRecord<String, OrderEvent> record, Acknowledgment ack) {
        try {
            log.info("Received: partition={} offset={} key={}", 
                record.partition(), record.offset(), record.key());

            orderService.process(record.value());

            ack.acknowledge();   // ⭐ commit offset AFTER successful processing
            // If process() throws → offset NOT committed → message re-delivered on restart

        } catch (Exception e) {
            log.error("Failed to process order event: key={}", record.key(), e);
            // Options:
            // 1. Don't ack → message re-delivered (retry)
            // 2. Send to DLQ → ack → move on
            // 3. Throw → container error handler decides (see DLQ section)
            throw e;   // let the error handler deal with it
        }
    }
}
```

#### 2.3 — Dead Letter Queue (DLQ)

```java
// Tier 2 — Production: DLQ with retry
@Bean
public ConcurrentKafkaListenerContainerFactory<String, OrderEvent> kafkaListenerContainerFactory() {
    var factory = new ConcurrentKafkaListenerContainerFactory<String, OrderEvent>();
    factory.setConsumerFactory(consumerFactory());

    // Retry 3 times with backoff, then send to DLQ
    factory.setCommonErrorHandler(new DefaultErrorHandler(
        new DeadLetterPublishingRecoverer(kafkaTemplate,
            (record, ex) -> new TopicPartition("order-events.DLT", record.partition())),
        new FixedBackOff(1000L, 3)   // 1s between retries, max 3 attempts
    ));

    return factory;
}

// Flow:
// 1. Message arrives → process() fails
// 2. Retry 1 (after 1s) → process() fails
// 3. Retry 2 (after 1s) → process() fails
// 4. Retry 3 (after 1s) → process() fails
// 5. Send to "order-events.DLT" (Dead Letter Topic) → original message preserved
// 6. Acknowledge original offset → consumer moves on
// 7. DLT can be monitored, manually reprocessed, or analyzed for root cause
```

#### 2.4 — Offset commit strategies

```
  AUTO-COMMIT (default — dangerous):
  Consumer commits offset periodically (every 5s by default).
  If consumer crashes between commit and processing → message LOST.
  If consumer crashes after processing but before commit → message DUPLICATED.

  MANUAL COMMIT (recommended):
  Consumer commits AFTER successful processing.
  If consumer crashes before commit → message re-delivered → AT-LEAST-ONCE.
  Make your processing IDEMPOTENT to handle re-delivery safely.

  AT-LEAST-ONCE + IDEMPOTENT PROCESSING = EFFECTIVELY EXACTLY-ONCE:
  - Consumer commits after processing
  - Processing uses idempotent key (orderId) to detect duplicates
  - Duplicate processing is a no-op (INSERT ... ON CONFLICT DO NOTHING)
```

#### 2.5 — Exactly-once with Kafka transactions

```java
// Tier 2 — Production: exactly-once (read → process → produce + commit in one atomic transaction)
@Bean
public ProducerFactory<String, OrderEvent> producerFactory() {
    Map<String, Object> props = Map.of(
        ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092",
        ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true,
        ProducerConfig.TRANSACTIONAL_ID_CONFIG, "order-processor-tx"   // enables transactions
    );
    return new DefaultKafkaProducerFactory<>(props);
}

// Transactional flow:
kafkaTemplate.executeInTransaction(ops -> {
    // 1. Read from input topic (offset tracked)
    // 2. Process the message
    OrderResult result = process(inputMessage);
    // 3. Write to output topic
    ops.send("order-results", result.orderId(), result);
    // 4. Commit consumer offset
    // ALL THREE (read offset + process + write) are committed atomically.
    // If any fails → entire transaction rolls back → no partial state.
    return result;
});
```

---

### Level 3 — The subtleties

#### 3.1 — Consumer lag monitoring

```bash
# Check consumer lag:
kafka-consumer-groups.sh --bootstrap-server kafka:9092 \
    --describe --group order-processor

# Output:
# TOPIC          PARTITION  CURRENT-OFFSET  LOG-END-OFFSET  LAG
# order-events   0          42000           42500           500
# order-events   1          38000           42300           4300
# order-events   2          41000           42100           1100

# Partition 1 has lag 4300 — consumer can't keep up.
# Possible causes:
# 1. Processing too slow → optimize or add consumers (up to partition count)
# 2. Consumer stuck on a poison message → check DLQ / error logs
# 3. Rebalance storm → consumers keep joining/leaving → no progress
```

#### 3.2 — Rebalance impact and mitigation

```java
// Rebalance pauses ALL consumption in the group while partitions are reassigned.
// Frequent rebalances (consumer scaling, health check failures) = consumption stalls.

// Mitigation: Cooperative Sticky Assignor (minimizes partition movement)
ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, 
    CooperativeStickyAssignor.class.getName()
// Only MOVED partitions are revoked — others continue consuming during rebalance.
// Default (RangeAssignor) revokes ALL partitions, then reassigns → longer pause.

// Static group membership (avoid rebalance on restart):
ConsumerConfig.GROUP_INSTANCE_ID_CONFIG, "consumer-1"
// Consumer keeps its assignment across restarts (within session.timeout).
// Rebalance only if the consumer is gone longer than session.timeout.
```

#### 3.3 — Key selection determines partition and ordering

```java
// Messages with the SAME KEY go to the SAME PARTITION → ordered relative to each other.
// Messages with DIFFERENT KEYS may go to DIFFERENT PARTITIONS → no ordering guarantee.

// ✅ Good key: orderId — all events for the same order are ordered
kafkaTemplate.send("order-events", order.getId().toString(), event);

// ❌ Bad key: null — round-robin across partitions → no ordering at all
kafkaTemplate.send("order-events", null, event);

// ❌ Bad key: userId with very popular users → hot partition (one partition gets 80% of traffic)
// Fix: composite key (userId + orderId) or custom partitioner
```

---

## ⚠️ Common Misconceptions

| You might think... | But actually... |
|---|---|
| "Kafka guarantees exactly-once out of the box" | Default is at-most-once (auto-commit). At-least-once requires manual commit. Exactly-once requires Kafka transactions + idempotent producer — explicit configuration. |
| "More consumers = more throughput" | Consumers beyond the partition count are IDLE. 6 partitions + 8 consumers = 2 idle consumers. Scale partitions first, then consumers. |
| "Messages are ordered across the entire topic" | Ordering is guaranteed WITHIN a partition only. Messages in different partitions have no ordering guarantee. Use the same key for messages that must be ordered. |
| "Auto-commit is safe" | Auto-commit commits offsets periodically (every 5s). If the consumer crashes between commit and processing, the message is lost. If it crashes after processing but before commit, the message is duplicated. Manual commit after processing is the safe default. |
| "DLQ is optional" | Without DLQ, a poison message (bad format, schema mismatch) blocks the entire partition forever — the consumer retries infinitely, making no progress on other messages. DLQ is mandatory for production consumers. |

---

## 🐞 Production Footguns

---

> **Footgun: Auto-commit loses messages on crash**
> **Cost:** Data loss — messages processed but "forgotten" on restart
>
> A payment processing consumer used auto-commit (default). The consumer processed a payment, then crashed before the next auto-commit interval (5s). On restart, the committed offset was BEFORE the processed payment — the payment was re-processed, charging the customer twice.

```java
// ❌ The trap: auto-commit
// enable.auto.commit = true (default)
// Consumer processes message → crashes → offset not committed → re-delivery → duplicate charge

// ✅ The fix: manual commit + idempotent processing
// enable.auto.commit = false
// ack.acknowledge() AFTER processing
// Processing uses idempotent DB upsert:
// INSERT INTO payments (order_id, amount) VALUES (?, ?)
// ON CONFLICT (order_id) DO NOTHING;
// Re-delivery of same order_id → no duplicate charge
```

---

> **Footgun: Consumer lag growing silently**
> **Cost:** Stale data — consumers hours behind, processing outdated events
>
> A real-time inventory consumer fell behind during a traffic spike. No lag monitoring was configured. The consumer processed events from 6 hours ago — showing stale inventory counts. Customers ordered out-of-stock items. The lag was discovered only when customer complaints arrived.

```java
// ❌ The trap: no lag monitoring
// Consumer falls behind → no alert → stale data served to users

// ✅ The fix: Micrometer + Prometheus + alerting
// Spring Kafka auto-exposes consumer lag metrics via Micrometer:
// kafka_consumer_fetch_manager_records_lag (per partition)
// Alert: if lag > 10000 for > 5 minutes → PagerDuty
```

---

## 🔗 Concept Web

| Connects to | How |
|---|---|
| `../Concurrency/thread-pool-executor.md` | Spring Kafka's `ConcurrentKafkaListenerContainerFactory.setConcurrency(N)` creates N consumer threads — each is a KafkaConsumer processing assigned partitions. Understanding thread pools explains the concurrency model. |
| `../Concurrency/concurrent-collections.md` | Consumer state (offsets, assignment) is managed with concurrent data structures internally. Understanding CHM and CopyOnWriteArrayList explains Spring Kafka's internal thread-safety. |
| `completable-future.md` | `KafkaTemplate.send()` returns `CompletableFuture` (Spring Kafka 3.x). Error handling with `whenComplete()` / `exceptionally()` follows the CompletableFuture patterns from Note #11. |
| `../../Spring/DeepDive/04-jpa-transactions.md` | Kafka consume → DB write pattern: the consumer processes a message and writes to DB within `@Transactional`. If DB write fails → don't commit Kafka offset → message re-delivered. Transaction management from Spring chapter 04 applies here. |

---

## 🎙️ Interview Deep Questions

**Q1. What is a consumer group and how does partition assignment work?**

> A consumer group is a set of consumers that share the work of reading a topic. Kafka assigns each partition to exactly ONE consumer in the group — this is the unit of parallelism. If a topic has 6 partitions and the group has 3 consumers, each consumer gets 2 partitions. If a consumer dies, Kafka triggers a rebalance — its partitions are reassigned to the remaining consumers. If you add consumers beyond the partition count, the extras are idle. Ordering is guaranteed within a partition (one consumer processes in offset order), not across partitions.

**Q2. Explain at-least-once vs exactly-once. How do you achieve each?**

> At-least-once: disable auto-commit, commit offset AFTER processing. If the consumer crashes before committing, the message is re-delivered on restart — potentially processed twice. Make processing idempotent (e.g., `INSERT ON CONFLICT DO NOTHING`) to handle duplicates safely. Exactly-once: use Kafka transactions — the consumer reads a message, processes it, writes to an output topic, and commits the consumer offset ALL in one atomic transaction. If any step fails, the entire transaction rolls back — no partial state. Requires `transactional.id` on the producer and `isolation.level=read_committed` on downstream consumers.

**Q3. What is a dead letter queue and why is it critical?**

> A DLQ is a separate topic where messages that fail processing after N retries are sent. Without a DLQ, a poison message (malformed JSON, schema incompatibility, business rule violation) blocks the entire partition — the consumer retries indefinitely, making no progress on subsequent messages. With a DLQ: retry 3 times with backoff → if all retries fail → send to the DLT (dead letter topic) → acknowledge the original offset → consumer moves on. The DLT can be monitored (alerts on DLT message count), manually inspected, and reprocessed after the root cause is fixed.

**Q4. How do you diagnose and fix growing consumer lag?**

> Check lag with `kafka-consumer-groups.sh --describe --group <name>` or Micrometer's `kafka_consumer_fetch_manager_records_lag` metric. Growing lag means the consumer is slower than the producer. Causes: (1) Slow processing — optimize the processing logic, batch writes, or use async processing. (2) Too few consumers — add consumers up to the partition count (beyond that = idle). (3) Rebalance storms — consumers constantly joining/leaving → use CooperativeStickyAssignor and static group membership to minimize rebalances. (4) Poison message — one message fails repeatedly → all subsequent messages in that partition are blocked → DLQ fixes this. Alert: lag > threshold for > N minutes → PagerDuty.

---

## 🎙️ Say It in 60 Seconds

> **Part 1 — What (10s):** Kafka decouples producers and consumers via topics (partitioned logs). Consumer groups share the work — each partition is read by exactly one consumer in the group. Offset commit = checkpoint. Consumer lag = how far behind you are.
>
> **Part 2 — How/Why (30s):** Spring Kafka: `@KafkaListener` with manual offset commit (`AckMode.MANUAL_IMMEDIATE`) ensures you acknowledge AFTER processing — preventing message loss. `ConcurrentKafkaListenerContainerFactory.setConcurrency(N)` runs N consumer threads. DLQ via `DeadLetterPublishingRecoverer` + `FixedBackOff(1000, 3)`: retry 3 times, then send to dead letter topic, acknowledge, move on. Exactly-once: Kafka transactions — read + process + produce + commit offset in one atomic transaction. Message key determines partition → same key = same partition = ordered.
>
> **Part 3 — Gotcha (20s):** Two traps: auto-commit (default) loses messages on crash — disable it, commit manually after processing. And no DLQ means a poison message blocks the entire partition forever — configure `DefaultErrorHandler` with `DeadLetterPublishingRecoverer`. Monitor consumer lag with Micrometer → alert if lag grows → means consumer can't keep up or is stuck on a bad message.

---

## 🧾 TL;DR

- **Partition** = unit of parallelism. One partition = one consumer per group. More consumers than partitions = idle consumers.
- **Ordering** guaranteed WITHIN a partition (same key → same partition). NOT across partitions.
- **Auto-commit is dangerous** — commit before processing = message loss. Use manual commit AFTER processing.
- **At-least-once** + idempotent processing = effectively exactly-once for most use cases.
- **Exactly-once** requires Kafka transactions (`transactional.id` + `read_committed`).
- **DLQ is mandatory** — without it, poison messages block the partition forever.
- **Consumer lag** = how far behind. Monitor with Micrometer. Alert if growing.
- **Rebalance** pauses consumption. Use CooperativeStickyAssignor + static group membership to minimize.
- **`acks=all`** + `enable.idempotence=true` on the producer for durability.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note created as Note #39 (Phase 6, Tier 1) of the JavaBackend KB completion roadmap. Staff-level depth: Spring Kafka producer (acks=all, idempotent), consumer with manual commit (AckMode.MANUAL_IMMEDIATE), DLQ (DefaultErrorHandler + DeadLetterPublishingRecoverer), exactly-once (Kafka transactions), consumer group partition assignment (ASCII visual), offset commit strategies (auto vs manual vs transactional), rebalance mitigation (CooperativeStickyAssignor, static membership), consumer lag monitoring (Micrometer), key selection and ordering. Two production footguns: auto-commit data loss, growing lag without monitoring. |
