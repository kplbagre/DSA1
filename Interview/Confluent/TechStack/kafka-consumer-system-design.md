# Kafka Consumer — System Design Deep Dive
### Grounded in mcse_data_ingestion source code. Every class name is real.

> **Purpose:** Confluent system design round prep, Kafka-first. You built a production Kafka consumer platform at scale. This file translates what you built into the language Confluent engineers speak — consumer group internals, offset lifecycle, exactly-once semantics, partition assignment, and system design patterns. The interviewer built Kafka. They will probe deeper than any other company.
>
> **Cross-reference:** Tech stack → `tableflow-tech-briefing.md` | Architecture overview → `../../../Kpl-inv/project-update/new/MCSE_DATA_INGESTION.md`

---

## 1. What You Actually Built — From Source Code

Before going to theory, anchor everything in your real implementation. This is what makes your answers defensible.

```
mcse_data_ingestion/
├── mcsedata-messaging/kafka/consumer/
│   ├── KafkaConsumerThread.java        ← V1: poll loop, ArrayBlockingQueue
│   ├── KafkaAvroConsumerThread.java    ← V1: Avro variant
│   └── KafkaConsumerManager.java       ← V1: lifecycle manager
│
├── mcsedata-listener/listener/common/
│   ├── KafkaListener.java              ← V1: abstract Runnable, calls onMessage(String)
│   ├── KafkaSource.java                ← V1: multi-consumer + mirror topic management
│   └── KafkaSourceV2.java              ← V2: config holder for RKConsumer
│
├── mcsedata-listener/listener/rkConsumer/
│   ├── consumer/KafkaConsumerV2Impl.java   ← V2: core, wraps RKConsumerSerial
│   ├── consumer/LimoConsumerV2.java        ← V2: hardened 4-consumer pattern
│   └── listener/KafkaListenerV2.java       ← V2: onMessage(List, ExecutorService)
│
└── mcsedata-listener/listener/common/consumerStrategy/
    └── KafkaStrategyImpl.java              ← decides V1 vs V2 at startup via CCM
```

---

## 2. V1 Consumer — How It Actually Works

### The Poll Loop (KafkaConsumerThread.java)

```java
// Real code from KafkaConsumerThread.java
@Override
public void run() {
    while (isRunning.get()) {
        while (isPaused.get()) {
            synchronized (isPaused) {
                isPaused.wait();       // blocks here until resumeListener() calls notifyAll()
            }
        }
        final ConsumerRecords<String, String> consumerRecords = consumer.poll(consumerTimeOut);
        consumerRecords.forEach(consumerRecord -> {
            final String value = consumerRecord.value();
            try {
                if (!ignoreKafkakMessage) {
                    messages.put(value);   // BLOCKS if queue is full — this is back-pressure
                }
            } catch (InterruptedException e) {
                LOG.error("Exception while putting the message in blocking queue", e);
            }
        });
    }
}
```

**What this means in Kafka terms:**

| Concept | V1 Implementation |
|---|---|
| **Offset commit** | Auto-commit (NOT explicitly disabled — default behaviour) |
| **Back-pressure** | `ArrayBlockingQueue.put()` blocks poll thread when queue is full |
| **Pause/Resume** | `AtomicBoolean isPaused` + `wait()/notifyAll()` — consumer pauses poll but stays in the group |
| **Shutdown** | `isRunning.set(false)` → poll loop exits → `consumer.close()` via shutdown hook |
| **Parallelism** | N `KafkaConsumerThread` instances sharing one `ArrayBlockingQueue<String>` |

### The Queue Hand-off (KafkaSource.java)

```
KafkaConsumerThread (poll thread)
    │ messages.put(value)           ← blocks if full
    ▼
ArrayBlockingQueue<String>         ← bounded (capacity from CCM)
    │ queue.poll()
    ▼
KafkaListener.run()                ← separate thread, drains queue
    │ onMessage(payload)
    ▼
EventManager → Cassandra write
```

**KafkaSource also manages mirror topics:**
```java
// Mirror source — reads from mirror topic, writes to SAME queue
getMirrorKafkaTopicConfig(primaryTopic).ifPresent(mirrorTopicConfig -> {
    mirrorSource = new MirrorSource(propertyConfig, eventConfig, mirrorTopicConfig,
                                    ccmConfig, queue, isRunning, primaryTopic);
    mirrorSource.init();
});
```
Both primary and mirror consumers drain into the same `ArrayBlockingQueue`. The listener doesn't know which source a message came from — transparent failover.

---

## 3. V2 Consumer — How It Actually Works

### Thread Model (KafkaConsumerV2Impl.java)

```java
// Real code from KafkaConsumerV2Impl.java
private void startPolling(KafkaListenerV2 kafkaListenerV2, Properties propertyConfig,
                          String topic, Boolean ignoreKafkakMessage) {

    Map<String, String> topicTPConfigMap = getTopicStrategy(topic, tenantId);
    // ↑ reads TOPIC_STRATEGY_CONFIG_JSON CCM key → {topic: {noOfConsumers: "4", noOfThreads: "8"}}

    int noOfConsumers = Integer.parseInt(topicTPConfigMap.getOrDefault("noOfConsumers", "1"));
    pool = Executors.newFixedThreadPool(noOfConsumers + 1);   // +1 for coordinator thread

    for (int i = 1; i <= noOfConsumers; i++) {
        int noOfThreads = Integer.parseInt(topicTPConfigMap.getOrDefault("noOfThreads", "1"));
        ExecutorService executorService = Executors.newFixedThreadPool(noOfThreads);
        // ↑ one processor pool PER consumer (not shared across consumers)

        ConsumerRecordListProcessor<String, String> recordListProcessor = (List<ConsumerRecord<String, String>> list) -> {
            if (!ignoreKafkakMessage) {
                kafkaListenerV2.onMessage(list, executorService);
                // ↑ hands batch to listener, which fans records across executorService
            }
        };

        RKConsumer<String, String> consumer = new RKConsumerSerial<>(
                pool, closed, propertyConfig, topic, recordListProcessor,
                Long.parseLong(propertyConfig.getProperty("consumer.timeout.ms")));
        consumers.add(consumer);
        consumer.start();
    }
}
```

**V2 Thread Architecture:**

```
Kafka Broker (topic with N partitions)
         │
         │ partitions assigned across consumer group members
         ▼
┌─────────────────────────────────────────────────────────────────┐
│  KafkaConsumerV2Impl (per pod)                                  │
│                                                                  │
│  pool = FixedThreadPool(noOfConsumers + 1)                      │
│  │                                                               │
│  ├── RKConsumerSerial[1] → polls partitions → batch             │
│  │      │ recordListProcessor.process(batch)                    │
│  │      ▼                                                        │
│  │   ExecutorService[1] (noOfThreads)                           │
│  │      ├── Thread: process record 1 → Cassandra write          │
│  │      ├── Thread: process record 2 → Cassandra write          │
│  │      └── Thread: process record N → Cassandra write          │
│  │                                                               │
│  ├── RKConsumerSerial[2] → polls partitions → batch             │
│  │      ▼ (own ExecutorService[2])                              │
│  │      └── noOfThreads threads                                  │
│  │                                                               │
│  └── ... up to noOfConsumers                                    │
└─────────────────────────────────────────────────────────────────┘
```

**Key Kafka config in V2 (from KafkaSourceV2.java):**
```java
// Explicitly set in V2 — NOT in V1
properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
```
This is the critical difference. V2 manually controls offset commits. V1 relied on auto-commit.

**Shutdown (KafkaConsumerV2Impl.java):**
```java
public void stopConsumer() {
    if (!closed.get()) {
        closed.set(true);
        for (RKConsumer<String, String> c : consumers) {
            c.wakeup();     // ← Kafka's consumer.wakeup() — throws WakeupException on next poll()
        }
        shutdownAndAwaitTermination(pool);
        // → pool.shutdown() → awaitTermination(5s) → shutdownNow() if timeout
    }
}
```

---

## 4. The Hardened Pattern — LimoConsumerV2 (4 Consumers)

This is the most interesting design pattern in the codebase. Read from source:

```java
// Real code from LimoConsumerV2.java — @Builder pattern
@Builder
public class LimoConsumerV2 {
    private KafkaSourceV2 isoSource;       // primary topic source config
    private KafkaSourceV2 isoErrorSource;  // error topic source config
    private KafkaConsumerV2Impl isoConsumer;            // primary consumer
    private KafkaConsumerV2Impl isoErrorConsumer;       // error topic consumer
    private KafkaConsumerV2Impl isoMirrorConsumer;      // primary mirror consumer
    private KafkaConsumerV2Impl isoMirrorErrorConsumer; // error mirror consumer

    public void init() {
        KafkaSourceV2.Source isoPrimary      = isoSource.getPrimarySource();
        KafkaSourceV2.Source isoError        = isoErrorSource.getPrimarySource();
        KafkaSourceV2.Source isoMirror       = isoSource.getMirrorSource();         // from mirror region
        KafkaSourceV2.Source isoMirrorError  = isoErrorSource.getMirrorSource();    // mirror's error topic

        // Each null-checks before starting — graceful degradation if topic not configured
        if (Objects.nonNull(isoPrimary) && StringUtils.isNotBlank(isoPrimary.getTopic())) {
            isoConsumer = new KafkaConsumerV2Impl(ccmConfig, eventConfig);
            isoConsumer.startConsumer(new LimoKafkaListenerV2(...), isoPrimary.getProperties(),
                                      isoPrimary.getTopic(), isoPrimary.getIgnoreKafkakMessage());
        }
        // ... same pattern for error, mirror, mirrorError
    }
}
```

**What this means operationally:**

```
Primary topic (us-east-2)
    │  primary consumer group
    ▼
KafkaConsumerV2Impl[primary]    ← hot path, all production traffic

Error topic (us-east-2)
    │  error consumer group
    ▼
KafkaConsumerV2Impl[error]      ← drains failed records, slower processing

Mirror topic (us-central)        ← cross-region mirror of primary
    │  mirror consumer group
    ▼
KafkaConsumerV2Impl[mirror]     ← cross-region redundancy

Mirror Error topic (us-central)
    │  mirror-error consumer group
    ▼
KafkaConsumerV2Impl[mirrorError] ← cross-region error drain

All 4 run simultaneously in the same pod, from the same JAR.
All write to the same Cassandra cluster.
```

**Why 4 consumers and not one?**
Each has a separate `group.id`. This means:
- Primary failure doesn't block error drain
- Mirror provides geographic redundancy (if us-east-2 Kafka is down, mirror in us-central is still being consumed)
- Error and mirror-error topics are independent retry channels

---

## 5. Consumer Strategy — How V1/V2 Is Decided at Runtime

```java
// KafkaStrategyImpl.java — reads CCM at startup
private String getStrategyType(String tenantId) {
    String strategyType = KafkaConsumerStrategy.KAFKA_CONSUMER_V1.getName(); // default
    String consumerStrategyJson = ccmConfig.getString(tenantId, CcmEnum.KAFKA_CONSUMER_STRATEGY);
    // consumerStrategyJson = {"iso": "V2", "dcc": "V1", "caspr": "V1"}

    SourcingIngestionContext context = SourcingIngestionContext.getCurrentContext();
    if (consumerStrategyMap.containsKey(context.getContextName())) {
        strategyType = consumerStrategyMap.get(context.getContextName());
    }
    return strategyType;
}
```

**Context = runAs flag from KITT YAML (`-DsourcingIngestionMcseRunAs=iso`).**

So at pod boot:
1. Spring reads `runAs` JVM property → `SourcingIngestionContext.getCurrentContext()` = "iso"
2. `KafkaStrategyImpl.init()` reads `KAFKA_CONSUMER_STRATEGY` CCM → finds "iso" → "V2"
3. `kafkaConsumerStrategyFactory.initializeKafkaSource("V2", tenantId)` → wires LimoConsumerV2
4. Pod starts consuming as V2

Change it to V1 without a deploy: update CCM key `KAFKA_CONSUMER_STRATEGY`, pod picks it up at next restart (or if CCM is hot-reloadable, immediately).

---

## 6. Kafka Internals — What Confluent Will Probe

You're interviewing at the company that WROTE Kafka. These are the internals they'll expect you to know. Anchor each with how your system implements it.

### 6.1 Consumer Group Protocol

**How consumer group membership works:**

```
1. Consumer starts → sends FindCoordinator request to any broker
2. Broker replies with the group coordinator (a specific broker responsible for this group.id)
3. Consumer sends JoinGroup request to coordinator
4. Coordinator waits for session.timeout.ms for all members to join
5. Coordinator elects ONE consumer as the Group Leader
6. Group Leader runs partition assignment algorithm
7. Group Leader sends SyncGroup with assignments back to coordinator
8. Coordinator distributes assignments to all consumers
9. Each consumer starts fetching its assigned partitions
```

**What this means for your system:**

When you deploy the ISO pipeline (200 pods, 200 partitions), each pod sends JoinGroup. The coordinator elects one pod as leader. Leader assigns: pod 1 → partition 0, pod 2 → partition 1, ..., pod 200 → partition 199.

**Kafka config you control for group behavior:**
```
session.timeout.ms    → how long before coordinator declares consumer dead (missed heartbeats)
heartbeat.interval.ms → how often consumer sends heartbeat (should be < session.timeout / 3)
max.poll.interval.ms  → how long between poll() calls before consumer is presumed dead
                         (this is the one that bites you with slow processing)
```

**Interview answer on rebalancing:**
> "We run 200 pods on the ISO pipeline. A pod restart triggers a rebalance. We use the default eager rebalance protocol — all 200 consumers stop, coordinator reassigns all 200 partitions, then all 200 resume. On a rolling deploy, this means brief consumption pauses per restart window. The right fix is to switch to CooperativeStickyAssignor — incremental rebalances that only move the orphaned partitions, leaving the other 199 pods consuming continuously. We haven't migrated yet because the ca-kconsumer library (RKConsumer) controls the assignor, and that's a library upgrade, not app code."

### 6.2 Offset Management

**The three commit patterns:**

```
1. Auto-commit (V1 behaviour):
   KafkaConsumer internally commits every auto.commit.interval.ms (default 5s)
   Risk: consumer crashes after auto-commit but before processing → records lost

2. commitSync() after processing:
   process(record) → commitSync()
   Guarantees at-least-once. Never loses records. Can duplicate on crash mid-batch.

3. commitAsync() for throughput:
   process(record) → commitAsync(callback)
   Higher throughput (doesn't block poll). If commit fails, you won't know immediately.
   Callback handles failures.
```

**In V2 (ENABLE_AUTO_COMMIT_CONFIG = false):**
RKConsumerSerial controls commit timing. Since it processes one batch at a time, the effective pattern is: poll batch → process entire batch via executorService → RKConsumerSerial commits → next poll.

This is at-least-once: crash mid-batch → re-consume the whole batch. Since Cassandra writes are upserts, duplicate processing is safe.

**Interview: "How do you guarantee at-least-once?"**
> "V2 explicitly disables auto-commit (`ENABLE_AUTO_COMMIT_CONFIG=false`). RKConsumerSerial commits offsets only after the batch processing completes. If a pod crashes mid-batch, the committed offset is behind the failed batch, so on restart we re-consume and re-process. Since Cassandra writes are upserts keyed by entity ID, processing the same record twice gives the same result — effectively-once semantics without Kafka Transactions."

### 6.3 The max.poll.interval.ms Trap

This is the sneaky failure mode at scale.

**Problem:** You poll 100 records, hand them to a thread pool with `noOfThreads=8`, and wait for all 8 threads to finish before the next poll. If Cassandra is slow and the 8 threads each take 5 seconds, that's 5 seconds between poll() calls.

If `max.poll.interval.ms = 5000` (default 300s, but misconfigured): the coordinator thinks your consumer is dead, triggers a rebalance, revokes your partitions. Your consumer is processing fine — it just wasn't polling fast enough. Now you have a rebalance storm.

**Your mitigation:** `consumer.timeout.ms` in your config controls how long `poll()` waits for records. The `TOPIC_STRATEGY_CONFIG_JSON` controls `noOfThreads`. Together they bound the processing time. But if Cassandra latency spikes, you can hit this.

**The right fix:** Process the batch asynchronously, return from `onMessage()` quickly, and track futures. Poll can continue while processing is in-flight. But this complicates offset commit ordering (you don't know which offsets are safe to commit yet).

**Interview answer:**
> "This is a real tension. We process batches synchronously per RKConsumerSerial — one batch must finish before the next poll. This simplifies offset commits but creates a gap between polls. We tune `max.poll.interval.ms` to be safely above our expected batch processing time with headroom for Cassandra latency spikes. We alert on Cassandra write latency > 50ms p99 to catch this before it triggers rebalances."

### 6.4 Partition Key Design

**From your system: offer-ID as partition key.**

```
Why it matters:
  Producer sends 3 updates for offer-123: price=$10, $12, $15
  If all 3 land on same partition → consumed in order → Cassandra has $15 ✓
  If spread across partitions → consumed out of order → Cassandra might have $10 ✗

Kafka guarantee: within a partition, messages are ordered.
Across partitions: no ordering guarantee.
```

**Partitioning formula:**
```
partition = hash(key) % num_partitions
```

If `num_partitions = 200` and you add a new topic with 400 partitions, the formula changes. Records with the same key go to a different partition. Existing consumers need to handle this (usually transparent — Kafka reassigns).

**What happens with null key:**
```
Kafka uses round-robin assignment across partitions.
Records with null key have NO ordering guarantee across messages.
Safe only for idempotent data where order doesn't matter (e.g., heartbeats, metrics).
```

**Your interview answer:**
> "We partition by entity ID — offer ID for offer events, distributor ID for DCC events. This guarantees all mutations for one entity are ordered within a single partition, consumed by a single pod thread in sequence. Since Cassandra upserts by primary key, out-of-order processing of the same entity would leave stale data. The partition key is the correctness guarantee."

### 6.5 Log Retention and Replay

**Your retention config: 7 days.**

This means:
- `log.retention.hours = 168` on the broker
- A segment is eligible for deletion after 7 days OR when it exceeds `log.retention.bytes`
- Committed offsets stored in `__consumer_offsets` topic (separate retention, default 7 days)

**Replay mechanics:**
```
Scenario: bad deploy corrupted 6 hours of offer writes. Fix is deployed. Need to reprocess.

Step 1: Get the offset at T-6h
        Admin API: listOffsets(topic, partition, OffsetSpec.forTimestamp(sixHoursAgo))
        → returns OffsetAndTimestamp per partition

Step 2: Reset consumer group offset
        Admin API: alterConsumerGroupOffsets(groupId, {topicPartition → offsetAndMetadata})
        → resets committed offset to the timestamp offset

Step 3: Consumer group picks up on next poll
        → processes records from T-6h to now
        → Cassandra upserts are safe — same data written again

Step 4: Verify via reconciliation
        → count rows written in that window, compare to expected offset range
```

**Interview: "How do you replay data?"**
> "We have three replay modes. First, Kafka offset reset — use the admin API to reset the consumer group offset to a specific timestamp or offset range. Kafka's offset-for-timestamp API lets us target exactly T-6h. Second, per-entity replay via our admin REST endpoint — it fetches the specific entity from the upstream system and re-runs the pipeline for that one record. Third, bulk replay via gscope re-upload for datasets like carrier TNT tables. All three are safe because our Cassandra writes are upserts."

---

## 7. System Design Questions Confluent Will Ask

### Design Q1: "Design a Kafka consumer service that fans out to multiple Cassandra tables"

**This is exactly what you built. Describe it systematically.**

```
Requirements (establish these first):
  - Multiple data domains (offers, capacity, carriers, TNT) from different topics
  - Each domain writes to a different Cassandra table
  - At-least-once delivery
  - Operational simplicity (one team owns everything)
  - Throughput: hot domains → millions/day, cold domains → thousands/day

Design:
  1. One JAR, multiple deployments via runAs flag
     Reason: shared DAL, shared domain models, single security scan, single on-call model

  2. Two consumer generations
     V1 (KafkaConsumerThread + ArrayBlockingQueue):
       - Simple, single-threaded per partition
       - Right for low-volume domains (capacity, CASPR)
       - Back-pressure via bounded queue

     V2 (RKConsumerSerial + per-consumer ExecutorService):
       - Batched, fan-out within pod
       - Right for high-volume domains (offers, item-store)
       - noOfConsumers × noOfThreads tunable via CCM without restart

  3. Offset management
     V1: auto-commit (acceptable for low-volume with idempotent writes)
     V2: manual commit after batch completes (ENABLE_AUTO_COMMIT=false)

  4. Error handling
     Primary → retry topic → dead-letter
     For most critical pipelines: 4-consumer pattern (primary + error + mirror + mirror-error)

  5. Back-pressure
     V1: ArrayBlockingQueue.put() blocks poll thread
     V2: RKConsumerSerial processes one batch at a time — slow Cassandra = slower poll rate

  6. Observability
     Kafka consumer lag per topic per group → Grafana, alert at 5 min for hot pipelines
     Cassandra write latency → alert at 50ms p99
     Dead-letter count → alert on growth
```

### Design Q2: "Design a Kafka-to-Iceberg pipeline" (Tableflow's core problem)

**This is what the team you're interviewing for actually built.**

```
Requirements:
  - Read from Kafka topic (any format — JSON, Avro, Protobuf)
  - Convert to Parquet
  - Write to S3 as Iceberg table
  - Guarantee data freshness (latency SLO)
  - Handle schema evolution

Key design decisions:

1. Consumer group strategy
   - One consumer group per Iceberg table
   - Partition count = max parallelism
   - CooperativeStickyAssignor to minimize rebalance disruption

2. Batching for Iceberg commit efficiency
   - Don't commit one Iceberg snapshot per Kafka record — too many small files
   - Buffer records in memory or local disk for T seconds or N records
   - Then: write Parquet file → update Iceberg manifest → atomic snapshot commit
   - Trade-off: T seconds = data freshness SLO. Larger T → fewer files, higher latency.

3. Exactly-once challenge
   - Kafka consumer + Iceberg write is NOT inherently atomic
   - Crash between Kafka commit and Iceberg commit → either message loss or duplicate file
   - Solution: store Kafka offset in Iceberg table metadata
     → on recovery, check last committed Iceberg offset
     → reset Kafka consumer to that offset
     → Parquet files are deduplicated by offset range (idempotent writes)

4. Schema evolution
   - Avro → Parquet via schema registry lookup
   - Iceberg allows additive schema changes (new nullable columns) without rewriting files
   - Non-additive changes (rename, type change) require table migration

5. Control plane responsibility
   - Manage table lifecycle (create, pause, delete)
   - Monitor consumer lag on the data plane topic
   - Provision/deprovision data plane pods
   - Store per-table state (current Kafka offset, last snapshot ID, schema version)

6. Data plane responsibility
   - Stateless as possible — all state in Iceberg metadata or external store
   - Multiple data plane instances can be assigned different partitions
   - Each writes independent Parquet files; Iceberg merge on query (not at write time)
```

### Design Q3: "How would you implement consumer group coordination?"

**This maps to K2 GC directly — the thing you'd work on.**

```
What consumer group coordination does:
  1. Track group membership (which consumers are alive)
  2. Trigger rebalance when membership changes
  3. Elect group leader (for partition assignment)
  4. Distribute partition assignments
  5. Track committed offsets per (group, topic, partition)

K2 GC is Confluent's new architecture for this coordinator:

Current (old) architecture:
  - Group coordinator is a specific Kafka broker responsible for each group
  - Broker handles heartbeats, offset commits, rebalance protocol
  - Scaling is limited by broker capacity
  - Group state is in-memory on the coordinator broker

K2 GC (new architecture):
  - Coordinator is a separate service (not embedded in broker)
  - Can scale independently of Kafka brokers
  - Deep dependencies: Conflux/SMR (replicated state machine), Partition Service

What you'd probe in a design interview:
  1. How do you ensure coordinator doesn't lose group state if it crashes?
     → Replicated state machine (SMR) — writes are committed to a quorum
     → Coordinator restarts and replays from last committed log position

  2. How do you handle split-brain (two coordinators think they're leader)?
     → Leader election via Raft or similar consensus
     → Fencing tokens — coordinator includes epoch in all operations
     → Old epoch operations rejected

  3. How do you handle a consumer that's alive but slow (not crashing)?
     → max.poll.interval.ms — if consumer doesn't poll within this window, coordinator
        presumes it dead and triggers rebalance
     → Consumer sends heartbeat separately from poll — heartbeat proves liveness,
        poll timeout proves processing progress

  4. How do you minimise rebalance disruption at scale?
     → Cooperative/incremental rebalance (KAFKA-8840)
     → Only orphaned partitions are revoked/reassigned
     → 199 of 200 consumers continue uninterrupted

Your bridge: "I've operated consumer groups at 200-pod scale, so I've seen the consumer group coordinator's behavior from the application side — rebalance storms, max.poll.interval.ms misconfiguration, session timeout tuning. Working on K2 GC is the opportunity to understand and improve that coordination protocol from the inside."
```

---

## 8. Kafka Config Reference — Numbers You Should Know Cold

These come from your actual system. Say them confidently.

```
Consumer configuration:
  consumer.timeout.ms          → your poll timeout (how long poll() waits for records)
  enable.auto.commit           → false in V2 (manual commit after processing)
  KAFKA_CONSUMERS (V1)         → default 4 consumer threads per pod
  noOfConsumers (V2)           → from TOPIC_STRATEGY_CONFIG_JSON CCM key
  noOfThreads (V2)             → from TOPIC_STRATEGY_CONFIG_JSON CCM key
  TERMINATION_WAIT_TIME        → 5 seconds (shutdown drain window)

Scale numbers:
  ISO pipeline:        120–200 pods, 200 partitions
  Throughput formula:  200 pods × noOfConsumers × noOfThreads = parallel threads
  Example:             200 × 4 × 8 = 6,400 concurrent processing threads
  Daily volume:        4–5M events/day
  Consumer lag SLO:    < 5 minutes for hot pipelines (ISO, offers)
  Retention:           7 days

Group IDs:
  Each KITT deployment has its own group.id — pipeline-level fault isolation
  ISO:    mcse_lite_ingestion_TeflonNewIso
  Bulk:   mcse_bulk_upload_group
  (and so on per deployment)

Topics per pipeline:
  Primary topic
  Error topic (<topic>-retry)
  Dead letter topic (accumulated failures, drained overnight)
  Mirror topic (cross-region, same queue)
```

---

## 9. SSL/mTLS — How Kafka Security Is Configured

From actual code (`KafkaSource.Source.getKafkaSSLProperties()`):

```java
// SSL config loaded from Akeyless secret file at pod startup
configProperties.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, sslProperties.getSecurityProtocol());
// → "SSL"

configProperties.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, sslProperties.getSslTrustStoreLocation());
// → "/etc/secrets/truststore.jks"

configProperties.put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, sslProperties.getSslKeyStoreLocation());
// → "/etc/secrets/keystore.jks"

// Passwords loaded from same file
configProperties.put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, sslProperties.getSslTrustStorePassword());
configProperties.put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, sslProperties.getSslKeyStorePassword());
configProperties.put(SslConfigs.SSL_KEY_PASSWORD_CONFIG, sslProperties.getSslKeyPassword());
```

**What this means:**
- `security.protocol=SSL` → encrypts Kafka traffic in transit (TLS)
- JKS files mounted by Akeyless at pod startup from `/etc/secrets/`
- mTLS: both client (consumer pod) and server (Kafka broker) present certificates
- Custom SSL path support via `ssl.file.path` in CCM for per-topic cert overrides

**Interview:** "Kafka transport is mTLS. Every pod has a client certificate from Akeyless. The Kafka broker validates the pod cert against its truststore. Cert rotation is automated via Akeyless with a grace period — both old and new certs are valid during rotation, so rolling restarts don't drop connections."

---

## 10. Producer Side — What You've Implemented

From `mcsedata-messaging/kafka/producer/` — multiple Kafka producers exist for downstream publishing.

Relevant producers:
- `McseAuditKafkaProducer` — publishes audit events when Cassandra writes happen
- `FcapFlipTraceKafkaProducer` — publishes capacity flip trace events
- `ItemStoreKafkaProducer` / `MpItemStoreKafkaProducer` — re-publish transformed events
- `EventLoggingKafkaProducer` — event logging for MSSQL pipeline

**Producer config you know:**
```
acks=all            → wait for all in-sync replicas before confirming
retries > 0         → retry on transient failures (network blips, leader election)
enable.idempotence  → exactly-once per producer session (sequence numbers)
```

**Why these settings matter:**
- `acks=all`: guarantees the message is on at least `min.insync.replicas` brokers before you get back an ack. If the leader crashes after ack but before replication, you don't lose data.
- `enable.idempotence=true`: if the producer retries (due to a timeout before receiving ack), the broker deduplicates using sequence numbers. Net result: no duplicate messages even with retries.

---

## 11. Bridging Your Work to K2 Group Coordinator

**What you've operated → what they're building:**

| What you've done | What K2 GC addresses |
|---|---|
| Operated 200-pod consumer groups with rebalance events | Rebalance protocol design and minimizing disruption |
| Seen eager rebalance pause all 200 consumers | Motivation for cooperative/incremental rebalance |
| Tuned `session.timeout.ms`, `max.poll.interval.ms` | These configs feed into coordinator behavior |
| Per-pipeline `group.id` isolation | Group coordinator manages per-group state independently |
| `consumer.wakeup()` for clean shutdown | Wakeup → WakeupException → last poll completes → clean commit |
| Mirror topic redundancy | Multi-region coordinator state replication |
| LimoConsumerV2 — 4 independent consumer groups | Each group is independently tracked by coordinator |

**What to say:**
> "From the application side, I've spent a lot of time understanding consumer group behavior — when rebalances trigger, why they happen, how to minimise their impact. I've tuned session timeouts, handled rebalance storms from max.poll.interval.ms misconfig, and designed multi-group architectures for fault isolation. Working on K2 GC would let me see and improve the coordinator protocol itself. The pain points I've experienced as a consumer author are exactly the pain points K2 GC is designed to solve."

---

## 12. Quick Q&A — Confluent-Level Questions

**"How does Kafka guarantee ordering?"**
> "Within a partition, messages are strictly ordered — an append-only log. Across partitions, there's no ordering guarantee. The producer's partition key determines placement. Same key → same partition → same consumer → ordered processing. Null key → round-robin, no ordering. In our system, offer-ID is the key so all mutations for one offer are ordered."

**"What happens to unacknowledged messages when a consumer crashes?"**
> "If auto-commit is off and the consumer crashes mid-batch, the committed offset is behind. On restart (or rebalance to another consumer), those messages are re-consumed. Since we're at-least-once, we process them again. Our Cassandra upserts make this safe. If auto-commit is on and the consumer commits before finishing processing, then crashes, the messages are skipped — message loss. This is why V2 has ENABLE_AUTO_COMMIT=false."

**"What is a consumer lag and why does it matter?"**
> "Consumer lag = log-end-offset minus committed offset, per partition. It measures how far behind a consumer is. For us, ISO pipeline at sub-5-minute lag SLO means Hollow caches stay fresh — new offer data reaches MCSE sourcing pods within 5 minutes of the offer event hitting Kafka. Lag above 5 minutes means stale Hollow snapshots, which means wrong delivery dates or ERR0077 for new items."

**"What is exactly-once semantics in Kafka?"**
> "Kafka Transactions wrap a read-process-write cycle atomically: the consumer reads, processes, writes to a downstream producer, and commits all three in one transaction. If anything fails, the entire transaction rolls back — the offset is not committed, and the downstream write is not visible. This is more expensive than at-least-once. For our use case — offer data with idempotent Cassandra upserts — at-least-once is sufficient and cheaper. If we were processing financial transactions (increments, not sets), we'd need true exactly-once."

**"How does Kafka handle broker failure?"**
> "Topics have a replication factor (typically 3). One broker is the partition leader; others are in-sync replicas (ISR). All reads and writes go to the leader. If the leader fails, the controller broker elects a new leader from the ISR. Clients discover the new leader from broker metadata and reconnect. Brief unavailability during election (seconds). With `acks=all`, a message is only confirmed after all ISR replicas have it — so leader failure after ack doesn't lose data."

**"What is the __consumer_offsets topic?"**
> "A special internal topic where Kafka stores committed consumer offsets. When your consumer calls commitSync(), Kafka writes (group, topic, partition) → offset to `__consumer_offsets`. Replicated with RF=3, compact log (last value per key is retained). This is also what the consumer group coordinator uses to recover group state after restart."

---

*Last updated: August 11, 2026. All Java code from mcse_data_ingestion source — verified from actual files.*
