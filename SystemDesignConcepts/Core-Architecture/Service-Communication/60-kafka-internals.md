# #60 — Kafka Internals: Log Compaction, Throughput Architecture & Topic Design

> **Companion to:** [`#19 Message Queues`](./19-message-queues-kafka-rabbitmq.md) | **Confluent Priority:** MUST DO
>
> **What #19 already covers:** partitions, offsets, consumer groups, rebalancing, replication factor, acks, min.insync.replicas, at-least-once vs exactly-once, idempotent producer, Pub/Sub pattern.
>
> **What this note adds:** log compaction (Kafka as a KV store), throughput architecture (why Kafka is fast), retention-as-TTL, topic/partition design, and how the Kafka log connects to Apache Iceberg via Tableflow.

---

## 📖 What is This Note? (Section 0.5)

**Full scope:** Apache Kafka is a distributed, append-only (meaning records are only ever added to the end — never inserted in the middle or overwritten) log that every service in a distributed system can write to or read from. #19 explains what Kafka IS and how its fundamentals work. This note explains how Kafka achieves its extraordinary throughput and how one configuration flag (`cleanup.policy`) transforms it from a time-bounded event queue into a persistent, distributed key-value store.

**One-line version:** Kafka is a city's postal system. The post office stores every letter in arrival order. Time-based retention shreds mail older than 30 days. Log compaction keeps only the LATEST package per recipient and discards older deliveries. Zero-copy is the delivery truck picking up sealed packages directly from the depot shelf without anyone opening them — maximum throughput, zero handling overhead.

**Core principle this note unlocks:** Kafka's speed isn't magic — it comes from three deliberate design choices: sequential disk writes, OS page cache reliance, and zero-copy network transfer. Together they make Kafka capable of millions of writes per second on commodity hardware. Understanding this is what separates a "use Kafka for async messaging" answer from a senior answer.

**Interview stakes at Confluent:**

| Confirmed Question | Concept from this note |
|---|---|
| "Design a distributed KV store" | Log compaction = Kafka IS the KV store |
| "Design TempMail (disposable email)" | `retention.ms` = mailbox expiry TTL |
| "Design News Feed ingestion" | Throughput architecture + partition key strategy |
| "What does Tableflow do internally?" | Kafka log → Parquet → Iceberg snapshot |
| "Why does Kafka achieve higher throughput than RabbitMQ?" | Sequential I/O + page cache + zero-copy |

---

## 🎯 Why This Matters

Saying "use Kafka" is table stakes. The following is a **senior signal at Confluent:**

> "I'd use a log-compacted Kafka topic — set `cleanup.policy=compact` so the broker retains the latest value per key, effectively making the topic a durable, fault-tolerant distributed KV store. For low-latency point reads, layer Redis as a read-through cache that invalidates on incoming Kafka events. For the KV Store interview question, this is the answer that maps directly to what Tableflow does: the compacted topic is the source of truth, and Iceberg is the materialized projection of it."

What mid-level candidates say: "Use Redis for a KV store." ← correct, but misses that Kafka's log-compacted topic gives you durability + replication + ordering that Redis doesn't guarantee.

What senior candidates say: the paragraph above.

---

## 🧠 The Mental Model

### Mental Model 1 — Why Kafka Is Staggeringly Fast

Imagine two libraries.

**Library A** (a traditional relational database): Books are shelved by topic and alphabetically. Adding a new book means finding the right section, shifting books to make room, and inserting it. Finding a book means traversing multiple aisles. Every operation involves seeking — the disk read-head jumps to a specific physical location. This is **random I/O**. A spinning disk handles about 100-200 random seeks per second. At 10ms per seek, 1,000 random writes takes 10 seconds.

**Library B** (Kafka): There is one shelf, infinitely long. Every new book is placed at the far right end. No finding a gap, no shifting, no alphabetical sorting. Finding a specific book means following its offset number stamped on the spine. Every write is at the end. This is **sequential I/O**. The same spinning disk that struggles with 100 random seeks per second handles 50,000+ sequential writes per second. SSDs handle over 1 million.

Three multipliers on top of sequential I/O:

**1. OS Page Cache** (a region of RAM the OS uses to buffer recently accessed disk data). Kafka doesn't manage its own memory heap. Instead, it writes to the OS page cache — the OS keeps the latest log data in RAM and flushes to disk asynchronously. When a consumer reads recent messages, they come from page cache (RAM speeds, ~10GB/s) — never touching disk. Kafka essentially uses the entire machine's free RAM as a buffer, managed by a component (the OS) that is extremely good at it.

**2. Zero-Copy Transfer** — a system call (`sendfile()`) that moves data from the page cache directly to the network socket without passing through user space. The naive path for serving consumer reads: disk → kernel buffer → user-space buffer → socket buffer → network (4 copies). Zero-copy: disk → kernel buffer → network (2 copies, bypassing user space entirely). Result: 60-70% CPU reduction for the same consumer throughput.

**3. Batching and Compression** — producers accumulate records in a buffer (`batch.size`, up to 1MB) and send the entire batch in one network call. Compression (Snappy, LZ4, ZSTD) is applied per batch. Consumers fetch in large batches (`fetch.min.bytes`, `fetch.max.bytes`). The per-record overhead amortizes across the batch.

**Newspaper test:** Kafka writes fast because it ONLY appends to the end of a file (sequential I/O), ONLY reads from RAM when data is recent (page cache), and ONLY uses the kernel to move data to the network (zero-copy). No random seeks, no memory copies, no CPU-heavy serialization per record. The disk becomes nearly as fast as RAM for sequential patterns.

---

### Mental Model 2 — Log Compaction: Kafka as a Distributed KV Store

Standard Kafka (`cleanup.policy=delete`) is a time-series log: "give me all events in the last 7 days." But sometimes you need "give me the CURRENT value for user_id=123." That is a key-value store question, not a time-series question.

**Log compaction** (Kafka's mechanism for retaining only the latest record per key, discarding older values for the same key) answers it.

Think of Wikipedia's edit history. Wikipedia saves every revision ever made to every article. But if you only care about "what does the article say right now?" — you don't need 5,000 historical revisions. You need one: the latest version per article title.

Log compaction does this for Kafka: for every key in a partition, retain at least the latest record. All older records for the same key are eligible for removal (compaction).

After compaction, a consumer reading from offset 0 gets a complete snapshot of the current state of all keys. That IS a distributed KV store — durable (replicated across brokers), fault-tolerant (survives broker failure), and ordered within each partition.

**Three facts you MUST get right in the interview:**

1. **At least one, not exactly one** — compaction retains AT LEAST the latest record per key. The active (currently-being-written) segment is NEVER compacted. So you may see multiple records for the same key in the active segment. Consumers must implement an upsert pattern, not "read once per key."

2. **Tombstones** (records with `value=null` that signal "delete this key") — to delete a key from the compacted log, produce a record with that key and `value=null`. The compactor keeps the tombstone in the log for `delete.retention.ms` (default 24 hours), then removes both the key and the tombstone. The 24-hour window exists so slow consumers can observe the deletion before it disappears. If a consumer has lag > `delete.retention.ms`, it will miss the deletion entirely.

3. **Both policies together** — `cleanup.policy=compact,delete` is valid and commonly used. Kafka deletes old segments by time/size AND compacts duplicate keys within surviving segments. This bounds storage by both time and key cardinality — the safe default for most KV store use cases.

**Newspaper test:** Log compaction is Wikipedia's "current article" feature. Kafka doesn't just store every edit — it can keep only the latest edit per article title, making the log work like a distributed dictionary. A null value deletes the entry. A consumer reading from the beginning always sees the current state of the world.

---

## 🎨 Visual

### 🎨 Visual 1 — Full System Topology

```
              KAFKA IN THE FULL SYSTEM STACK
══════════════════════════════════════════════════════════
  PRODUCERS (write data into Kafka)

  ┌────────────────┐  ┌─────────────────┐  ┌──────────┐
  │  TempMail API  │  │  News Feed API  │  │  Status  │
  │  (produces     │  │  (produces      │  │  Service │
  │   email msgs)  │  │   feed events)  │  │          │
  └───────┬────────┘  └────────┬────────┘  └────┬─────┘
          │ produce             │ produce        │ produce
          └─────────────────────┼────────────────┘
                                │
                                ▼
══════════════════════════════════════════════════════════
  KAFKA CLUSTER (3 brokers, each holds partition replicas)

  ┌────────────────────────────────────────────────────┐
  │                                                    │
  │  ┌──────────────────────┐  ┌────────────────────┐  │
  │  │ topic: user-inbox    │  │ topic: user-status │  │
  │  │ cleanup.policy=delete│  │ cleanup.policy=    │  │
  │  │ retention.ms=600000  │  │   compact          │  │
  │  │ (TempMail TTL = 10m) │  │ (distributed KV)  │  │
  │  └──────────────────────┘  └────────────────────┘  │
  │                                                    │
  └─────────────────────┬──────────────────────────────┘
                        │ subscribe
          ┌─────────────┼──────────┐
          │             │          │
          ▼             ▼          ▼
  ┌──────────┐  ┌────────────┐  ┌───────────────────┐
  │  Email   │  │  Status    │  │  Tableflow        │
  │  Service │  │  Reader    │  │  Consumer         │
  └──────────┘  └────────────┘  └─────────┬─────────┘
                                           │ write Parquet
                                           ▼
                                  ┌─────────────────┐
                                  │  Apache Iceberg  │
                                  │  (S3 data lake) │
                                  └─────────────────┘
══════════════════════════════════════════════════════════

KEY INVARIANT:
  The cleanup.policy flag is the single config that changes
  Kafka's behavior from time-bounded queue (delete) to
  persistent KV store (compact). Replication, fault
  tolerance, and throughput are identical in both modes.
```

---

### 🎨 Visual 2 — Component Detail: Log Compaction Before and After

```
LOG COMPACTION — BEFORE AND AFTER
══════════════════════════════════════════════════════════

PARTITION 0 — BEFORE COMPACTION

  Segment 0 (closed)        Segment 1 (active — NEVER compacted)
  ┌─────────────────────┐   ┌──────────────────────────────────┐
  │ off │ key    │ value│   │ off │ key    │ value             │
  │  0  │ user:1 │ ON   │   │  5  │ user:2 │ OFFLINE           │
  │  1  │ user:2 │ ON   │   │  6  │ user:3 │ AWAY              │
  │  2  │ user:1 │ AWAY │   │  7  │ user:1 │ null (TOMBSTONE)  │
  │  3  │ user:1 │ OFF  │   │                                  │
  │  4  │ user:3 │ ON   │   │                                  │
  └─────────────────────┘   └──────────────────────────────────┘
         ▲
         compactor runs on Segment 0 in the background

──────────────────────────────────────────────────────────

PARTITION 0 — AFTER COMPACTION (Segment 0 compacted)

  Segment 0 (compacted)     Segment 1 (active — unchanged)
  ┌─────────────────────┐   ┌──────────────────────────────────┐
  │ off │ key    │ value│   │ off │ key    │ value             │
  │  3  │ user:1 │ OFF  │   │  5  │ user:2 │ OFFLINE           │
  │  4  │ user:3 │ ON   │   │  6  │ user:3 │ AWAY              │
  │                     │   │  7  │ user:1 │ null (TOMBSTONE)  │
  │ (offsets 0,1,2      │   │                                  │
  │  discarded — older  │   │                                  │
  │  values for same    │   │                                  │
  │  keys)              │   │                                  │
  └─────────────────────┘   └──────────────────────────────────┘

After delete.retention.ms (24h by default) passes:
  Tombstone at offset 7 is purged from BOTH segments.
  user:1 no longer exists anywhere in the log.
  A consumer reading from offset 0 will NOT see user:1.

══════════════════════════════════════════════════════════

KEY INVARIANT:
  Reading from offset 0 on a compacted log gives the current
  state of every key. Offsets are NOT renumbered — gaps are
  normal. The active segment is never compacted. Consumers
  must implement an upsert pattern (last-value-wins).
```

---

## 🔬 How It Actually Works

### Part 1 — Kafka's Throughput Architecture (Why It's Fast)

**Steps in plain English:**

1. **Producer batches records in memory** — records accumulate until `batch.size` is reached or `linger.ms` timer fires.
2. **Broker appends batch to the log file** — a `write()` call appends to the current active segment file. The OS kernel places this in page cache; disk flush is asynchronous.
3. **Replication** — follower brokers fetch the same batch via the same sequential-read path, storing it in their own page cache and flushing asynchronously.
4. **Consumer reads from page cache** — if the consumer is keeping up, the data it requests is still in page cache (in RAM). Kafka's `sendfile()` syscall moves data from page cache to the socket, bypassing user space.

```java
// Producer config: tuning the batch and throughput knobs
Properties props = new Properties();
props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
// batch.size: accumulate up to 64KB before sending (default 16KB)
props.put(ProducerConfig.BATCH_SIZE_CONFIG, 65536);
// linger.ms: wait up to 5ms for batch to fill (default 0 = no wait)
props.put(ProducerConfig.LINGER_MS_CONFIG, 5);
// compression: ZSTD gives best ratio; LZ4 gives best speed
props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
// idempotent: exactly-once producer-side deduplication
props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
```

**Key numbers to quote in the interview:**

| I/O type | Throughput | Use in Kafka |
|---|---|---|
| Random disk write (HDD) | ~0.1 MB/s | Never — Kafka avoids this |
| Sequential disk write (HDD) | ~500 MB/s | Kafka's log appends |
| Sequential disk write (SSD) | ~3 GB/s | Modern Kafka deployments |
| OS page cache read | ~10 GB/s | Hot consumer reads |
| Zero-copy CPU savings | ~60-70% | All consumer reads |

---

### Part 2 — Retention Policies

**Steps in plain English:**

1. **Choose the policy** — `delete` for time-bounded data (TempMail, session events); `compact` for KV store data (user status, entity state); `compact,delete` for bounded KV stores.
2. **Configure the thresholds** — `retention.ms` for time, `retention.bytes` for size, `min.cleanable.dirty.ratio` for compact trigger.
3. **Kafka manages the cleanup** — a background thread deletes old segments or triggers compaction; the active segment is never touched.

```java
// --- OPTION A: Time-based delete (TempMail inbox TTL) ---
Properties deleteConfig = new Properties();
// delete all segments older than 10 minutes
deleteConfig.put("cleanup.policy", "delete");
deleteConfig.put("retention.ms", "600000");
// no size cap
deleteConfig.put("retention.bytes", "-1");

// --- OPTION B: Log compaction (distributed KV store) ---
Properties compactConfig = new Properties();
// retain latest value per key indefinitely
compactConfig.put("cleanup.policy", "compact");
// keep tombstones for 24h so slow consumers see deletions
compactConfig.put("delete.retention.ms", "86400000");
// trigger compaction when 10% of the log has overwritten keys
compactConfig.put("min.cleanable.dirty.ratio", "0.1");

// --- OPTION C: Both (bounded KV store — most common in practice) ---
Properties bothConfig = new Properties();
// 7 days max age
bothConfig.put("cleanup.policy", "compact,delete");
bothConfig.put("retention.ms", "604800000");
bothConfig.put("delete.retention.ms", "86400000");
bothConfig.put("min.cleanable.dirty.ratio", "0.1");
```

---

### Part 3 — Log Compaction Mechanics (The KV Store Pattern)

**Steps in plain English:**

1. **Create the topic** with `cleanup.policy=compact` via AdminClient.
2. **Producers write KV records** — key = entity identifier (user_id, order_id), value = current state as JSON or Avro.
3. **Delete a key** — produce a record with the same key and `value=null` (tombstone).
4. **Consumers read from `earliest`** — process all records, keeping the latest value per key. This is the full KV snapshot.
5. **Point-lookup pattern** — wrap the consumer in a read-through Redis cache: Redis serves point reads; Kafka consumer group invalidates the cache on updates.

```java
// Step 1 — Create the log-compacted topic
Properties adminProps = new Properties();
adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");

try (AdminClient admin = AdminClient.create(adminProps)) {
    Map<String, String> topicConfig = new HashMap<>();
    topicConfig.put("cleanup.policy", "compact");
    topicConfig.put("delete.retention.ms", "86400000");
    topicConfig.put("min.cleanable.dirty.ratio", "0.1");

    NewTopic topic = new NewTopic("user-status", 3, (short) 3);
    topic.configs(topicConfig);
    admin.createTopics(Collections.singleton(topic)).all().get();
}

// Step 2 — Producer: write and update KV entries
Properties producerProps = new Properties();
producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
producerProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);

try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProps)) {
    // KV write: user:1's current status is ONLINE
    producer.send(new ProducerRecord<>("user-status", "user:1", "ONLINE"));
    // KV update: user:1 moved to OFFLINE — old value eligible for compaction
    producer.send(new ProducerRecord<>("user-status", "user:1", "OFFLINE"));
    // TOMBSTONE: null value = delete user:1 from the KV store
    producer.send(new ProducerRecord<>("user-status", "user:1", null));
}

// Step 3 — Consumer: rebuild current KV state from offset 0
Properties consumerProps = new Properties();
consumerProps.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
consumerProps.put(ConsumerConfig.GROUP_ID_CONFIG, "kv-snapshot-builder");
// start from the beginning to get the full current state
consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

// last-value-wins map: the KV snapshot
Map<String, String> kvSnapshot = new HashMap<>();

try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProps)) {
    consumer.subscribe(Collections.singleton("user-status"));
    ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));
    for (ConsumerRecord<String, String> record : records) {
        if (record.value() == null) {
            // tombstone: remove this key from the local snapshot
            kvSnapshot.remove(record.key());
        } else {
            // upsert: latest value for this key always wins
            kvSnapshot.put(record.key(), record.value());
        }
    }
    // kvSnapshot now reflects the current state of all active keys
}
```

> **⚠️ Critical:** Never assume you'll see exactly one record per key. The active segment is uncompacted — always implement the last-value-wins upsert pattern above.

---

### Part 4 — Topic and Partition Design

**How many partitions?**

Rule of thumb:

```
partitions = max(
    ceil(target_write_throughput_MB_per_s / 10),   // one partition ≈ 10 MB/s write
    target_consumer_parallelism                     // one partition = one consumer max
)
```

A consumer group cannot have more active consumers than partitions. 6 partitions = max 6 consumers in parallel.

**Choosing the partition key:**

```java
// Option A — partition by userId: ordering guaranteed per user
// all events for user:123 go to the same partition, in arrival order
producer.send(new ProducerRecord<>("events", userId, eventJson));

// Option B — null key: round-robin, maximum throughput, zero ordering
producer.send(new ProducerRecord<>("events", null, eventJson));

// Option C — composite key: partition by tenantId + userId
// ordering per user within a tenant, tenant isolation
String partitionKey = tenantId + "#" + userId;
producer.send(new ProducerRecord<>("events", partitionKey, eventJson));
```

**Partition key trade-offs:**

| Key | Ordering | Throughput | Hot Partition Risk |
|---|---|---|---|
| `user_id` | Per-user guaranteed | Even (if users are even) | Yes — viral users |
| `event_type` | Per-type guaranteed | Uneven (CLICK >> PURCHASE) | Yes — dominant types |
| `null` (round-robin) | None | Maximum, perfectly even | None |
| `hash(user_id) % N` | None | Even | Low |

**Hot partitions** (when one key generates far more traffic than others, overloading its partition's broker) are covered in depth in `#45-hot-partition-problem.md`. Mitigation strategies: write salting (append a random suffix to the key), hot-key caching (serve reads from Redis for viral keys).

**Increasing partitions post-creation:** You CAN increase partition count, but existing keys will reroute (`hash(key) % new_count` changes). For compacted topics this is dangerous — the latest value for a key may now be in the old partition number, and compaction will no longer clean it up correctly. Always plan partition count upfront.

---

### Part 5 — Tableflow: Kafka → Iceberg Materialization

Tableflow is a Confluent Cloud product (managed Kafka consumer) that materializes (converts and writes) Kafka topic records into Apache Iceberg tables in real time.

**Steps in plain English:**

1. **Tableflow subscribes** to the source Kafka topic and tracks its consumer offset (position in the log).
2. **Micro-batch reads** — Tableflow polls a configurable batch of records (e.g., every 60 seconds or every 10,000 records, whichever comes first).
3. **Parquet conversion** — records are serialized into Parquet format (a columnar binary format optimized for analytical queries — reads columns, not rows).
4. **S3 write** — Parquet files land in object storage (S3/GCS/Azure Blob) under the Iceberg table's data directory.
5. **Iceberg manifest update** — Tableflow updates the Iceberg table's metadata (the manifest and snapshot chain) to point to the new Parquet files.
6. **Atomic offset commit** — the Kafka consumer offset is committed atomically with the Iceberg snapshot write. If the S3 write fails, the offset does NOT advance. If the offset commit fails after a successful S3 write, the Parquet files are re-written on retry and Iceberg's snapshot deduplication handles the duplicate files.

```
Kafka Topic                 Tableflow Consumer           Iceberg Table
──────────                  ──────────────────           ─────────────
[offset 0–99]  ──poll──►   serialize to Parquet  ──►   data/part-0001.parquet
[offset 100+]              write to S3                  manifest.avro (updated)
                           commit offset 100            snapshot-2 → snapshot-1 chain
                            (atomic with snapshot)
```

**Why log compaction matters for Tableflow:** If the source Kafka topic uses `cleanup.policy=compact`, each key in the topic represents the current state of an entity. Tableflow produces an UPSERT Iceberg table — each row reflects the latest value per key. This is the event sourcing pattern (covered in `#22-event-sourcing.md`) materialized into a queryable data lake.

---

## 🏭 Real World

- **LinkedIn** — Created Kafka in 2011 for activity stream tracking. The sequential-write + page cache design lets LinkedIn handle over 1 trillion messages per day on commodity hardware. The original "The Log" blog post by Jay Kreps is still the best explanation of why this works.
- **Confluent / Tableflow** — Your team's product: micro-batch Kafka consumer → Parquet serializer → Iceberg metadata writer, with atomic offset commit. Every engineering decision in Part 5 reflects what Tableflow's data plane does.
- **Uber** — Uses log-compacted topics for the real-time city map: each driver's location is a key-value record (`driver_id` → `{lat, lng, status}`). The compacted topic is the source of truth for the dispatch system. Redis mirrors the latest value for sub-millisecond lookups.
- **Swiggy** — Order state machine updates go to a compacted topic. The latest state per `order_id` is always queryable by reading from the compacted topic. Eliminates the need to scan the orders database for dashboard reads.
- **Razorpay** — Uses Kafka with idempotent producers for payment event processing. If a producer retries due to a network timeout, the broker deduplicates by `producer_id + sequence_number`, preventing duplicate payment records from entering the processing pipeline.

---

## ⚠️ When to Use vs When NOT to Use

**Use `cleanup.policy=compact` (log compaction) when:**
- The topic represents current state per entity (user profile, order status, device config)
- You want to rebuild downstream state by replaying from offset 0
- Downstream consumers are event-sourced systems that reconstruct state from the log
- You need Kafka's replication guarantees on KV data without a separate KV store

**Do NOT use log compaction when:**
- You need time-series analytics ("how many events happened in the last 24h?") — compaction destroys historical intermediate values
- You need point-in-time snapshots to a specific past state — compaction is irreversible
- Events are ephemeral and keys are rarely updated — compaction overhead without benefit; use `delete` instead
- You need sub-millisecond point lookups — Kafka is a scan store, not a B-tree index; pair it with Redis for low-latency reads

**Use `retention.ms` (time-based delete) when:**
- Data has a natural TTL: TempMail inboxes (10 min), session events (24h), clickstream (30 days)
- Consumers are always near-current and don't need to replay historical data
- Storage budget is tight and old data has no value

**Warning — short retention + slow consumer:** If consumer lag exceeds `retention.ms`, lagging consumers permanently miss records. Always monitor consumer lag against the retention window. Alert when lag > 50% of retention.

---

## ⚖️ Trade-offs

| Dimension | `cleanup.policy=compact` | `cleanup.policy=delete` |
|---|---|---|
| **Storage cost** | Grows with unique key count (not time) | Bounded by retention window |
| **History** | Lost after compaction — cannot audit past values | Preserved up to retention window |
| **Read pattern** | Scan from 0 = full KV snapshot | Read recent events only |
| **Delete semantics** | Tombstone → wait `delete.retention.ms` → purge | Automatic at retention boundary |
| **Slow consumer risk** | Misses tombstones if lag > `delete.retention.ms` | Misses events if lag > `retention.ms` |
| **Partition scaling** | Risky — key routing changes, compaction breaks | Safe — no key-based routing |
| **Active segment** | Not compacted — may have multiple records per key | Not deleted — current segment always retained |

**Zero-copy trade-offs:**

| Benefit | Constraint |
|---|---|
| 60-70% CPU reduction for consumer reads | Per-record encryption not possible in kernel path — encrypt at producer |
| 2x read throughput vs user-space copy | Requires DMA-capable NICs (standard on all major cloud providers) |
| No JVM heap pressure for consumer reads | Consumers reading very old data (cold) still hit disk |

---

## 🧩 Interview Q&As

### Q1 — "How would you design a distributed KV store? We need low-latency reads and durable writes."

> Use a log-compacted Kafka topic as the source of truth. Set `cleanup.policy=compact` — the broker retains the latest value per key, giving you a fault-tolerant, replicated distributed KV store with Kafka's delivery guarantees.
>
> For low-latency reads: put Redis in front as a read-through cache. When the Kafka consumer processes an update, it invalidates (or updates) the Redis entry for that key. Reads hit Redis first; if the key isn't in Redis, the consumer has fallen behind — serve from the Kafka scan as a fallback.
>
> For deletes: produce a tombstone (`value=null`). The key disappears from the compacted log after `delete.retention.ms`. Invalidate the Redis key immediately on tombstone receipt.
>
> **Confluent follow-up:** This is exactly the pattern that Tableflow builds on — compacted Kafka topic as the source of truth, Iceberg as the materialized analytical projection.

---

### Q2 — "Why does Kafka achieve higher throughput than traditional message brokers like RabbitMQ?"

> Three reasons:
>
> **Sequential I/O** — Kafka only ever appends to the end of the log. Disks handle sequential writes 100-1000x faster than random writes (no seek overhead). RabbitMQ maintains complex data structures requiring random disk access.
>
> **Page cache** — Kafka delegates memory management to the OS. Recent messages stay in RAM (page cache). Consumers reading recent data never touch disk — they read at memory speeds. On a 64GB machine, Kafka effectively has 50GB+ of read buffer for free.
>
> **Zero-copy** — When consumers fetch, Kafka uses the `sendfile()` syscall: data moves from page cache to the network socket without entering user space. A naive implementation copies data 4 times; zero-copy uses 2 kernel copies, cutting CPU ~60%.
>
> Combined with batching and compression, a 3-node Kafka cluster on commodity hardware handles 1+ million messages per second.

---

### Q3 — "How does TempMail TTL work in your Kafka-based design?"

> Each disposable email inbox maps to a partition key (the `inbox_id`). The Kafka topic uses `cleanup.policy=delete` with `retention.ms` set to the TTL (e.g., 600,000ms = 10 minutes).
>
> When a user creates an inbox, we generate an `inbox_id`, record it in the consumer group, and start polling for records with that key. When a message arrives for the inbox, it's produced to the Kafka topic with `inbox_id` as the partition key.
>
> After 10 minutes, Kafka deletes the segments older than the retention window. The messages are gone — no explicit deletion required. The consumer group offset for that inbox becomes stale.
>
> Compared to a database TTL (`DELETE WHERE created_at < NOW() - INTERVAL`): Kafka's segment deletion is a batch operation that doesn't impact write throughput and doesn't create table fragmentation. The TTL is a native broker-level feature, not a maintenance job.

---

### Q4 — "What's the difference between `retention.ms` and log compaction? When would you use both?"

> `retention.ms` is time-based: Kafka deletes log segments older than the retention window. It's a FIFO (first-in-first-out) time-bounded queue — old data expires regardless of the keys involved.
>
> Log compaction is key-based: Kafka retains at least the latest record per key, discarding older records for the same key. It's a KV-store model — storage grows with unique key count, not with time.
>
> Use both (`cleanup.policy=compact,delete`) when you need bounded storage AND current-value queryability. Example: a user-settings topic where you need the current settings per user (compact) but can safely discard users who haven't been active in 90 days (delete). This bounds storage while maintaining the KV-store read pattern.

---

### Q5 — "Walk me through how Tableflow materializes a Kafka topic into an Apache Iceberg table."

> Tableflow is a managed Kafka consumer inside Confluent Cloud. The flow:
>
> 1. It polls the source topic in micro-batches, tracking the consumer offset.
> 2. Each batch is serialized to Parquet format — a columnar binary format that compresses well and is efficient for analytical reads (Spark, Flink, Trino read columns, not rows).
> 3. Parquet files land in cloud object storage (S3, GCS, Azure Blob) under the table's data directory.
> 4. Tableflow updates the Iceberg table metadata — specifically the "manifest list" that tells query engines where the current data files are and which snapshot is the latest.
> 5. **Critical:** the Kafka consumer offset commit and the Iceberg snapshot write are atomic. If the Parquet write fails, the offset doesn't advance — no records are lost. If the offset commit fails after a successful write, the Parquet is rewritten on retry and Iceberg's snapshot deduplication handles it.
>
> For compacted source topics, Tableflow produces an UPSERT Iceberg table — each row reflects the latest value per key, not a history of all values. This is the event-sourcing pattern materialized into a queryable lake table.

---

### Q6 — ⭐ Tier 2 Probe: "What happens when a producer sends a null value to a log-compacted topic? How long before the key completely disappears?"

> A null value creates a **tombstone** — a special record signaling "delete this key from the compacted log."
>
> Timeline:
>
> **Immediately:** The tombstone is written to the active segment. Consumers reading the topic see it.
>
> **After segment closes and compaction triggers:** The compactor processes closed segments. For the tombstoned key, it discards all previous records (the historical values) AND retains the tombstone itself. The key still exists in the log as a tombstone.
>
> **After `delete.retention.ms` (default 24 hours):** A subsequent compaction pass removes the tombstone. The key no longer exists anywhere in the log.
>
> **Why the 24-hour window?** To give slow consumers time to observe the deletion. If a consumer has lag > `delete.retention.ms`, it will miss the tombstone entirely — it will never know the key was deleted. This is a real operational risk: monitor consumer lag against `delete.retention.ms` on all compacted topics.
>
> **Critical implication for Tableflow:** When Tableflow processes a tombstone from a compacted source topic, it must DELETE the corresponding row in the Iceberg table, not just skip the record. Missed tombstones = deleted Kafka keys surviving forever in the Iceberg table.

---

### Q7 — ⭐ Tier 2 Probe: "Can a consumer reading from offset 0 on a compacted topic guarantee seeing exactly one record per key?"

> No — and this is the most common misconception about log compaction.
>
> The guarantee is: at least the latest record per key is present. Not exactly one.
>
> Two reasons you may see multiple records per key:
>
> **Active segment** — the newest, currently-being-written segment is NEVER compacted. If a key was updated multiple times in the active segment (which can be quite large), a consumer reading from offset 0 will see all those updates in the active segment.
>
> **Compaction lag** — compaction is a background process with configurable triggers (`min.cleanable.dirty.ratio`, `min.compaction.lag.ms`). Between compaction runs, duplicate keys accumulate in closed segments that haven't been compacted yet.
>
> **The correct consumer implementation:** always use an upsert / last-value-wins map:
>
> ```java
> if (record.value() == null) {
>     kvSnapshot.remove(record.key());
> } else {
>     kvSnapshot.put(record.key(), record.value());
> }
> ```
>
> Never assume you'll read each key exactly once. Never hold state that depends on processing each key exactly one time.

---

## 🧾 TL;DR

- **Kafka is fast** because of three invariants: sequential appends only (no seeks), OS page cache as the primary read buffer (recent data = memory speed), zero-copy transfer (`sendfile()` skips user space). Batching + compression amplify all three.
- **`cleanup.policy=delete`** = time-based TTL. Set `retention.ms` for inbox expiry (TempMail: 10 minutes). Kafka deletes whole segments; deletion is eventual, not instant per-record.
- **`cleanup.policy=compact`** = key-based KV store. Latest value per key is retained. `value=null` = tombstone (delete). Active segment is NEVER compacted. Consumers MUST implement last-value-wins upsert.
- **`compact,delete`** = both policies. Bounded by time AND compacted by key. The safe production default for KV store use cases.
- **Partition key** = ordering scope. Same key → same partition → ordered delivery within that partition. Null key = round-robin = max throughput, zero ordering.
- **Tableflow** = micro-batch Kafka consumer → Parquet serializer → S3 → Iceberg metadata update, with atomic offset-plus-snapshot commit.
- **At Confluent interviews:** "log-compacted Kafka topic" is the senior signal for KV Store questions. Know the tombstone lifecycle and the active-segment exception cold.

---

## 🔗 Related Concepts

| Concept | Why it relates |
|---|---|
| [`#19 Message Queues`](./19-message-queues-kafka-rabbitmq.md) | Foundation — partitions, offsets, consumer groups, replication, acks, at-least-once vs exactly-once. Read #19 before this note. |
| [`#22 Event Sourcing`](../Database-Core/22-event-sourcing.md) | Tableflow IS event sourcing: the Kafka log is the immutable event store; Iceberg is the materialized projection. Conceptually identical. |
| [`#07 CDC + Outbox Pattern`](../../Foundations/Data-Fundamentals/07-cdc-outbox.md) | When writing to a DB and a compacted Kafka topic in the same transaction — CDC/Outbox ensures atomicity without dual-write risk. |
| [`#45 Hot Partition Problem`](../Database-Core/45-hot-partition-problem.md) | When partition keys are skewed (viral users, dominant event types), one partition becomes a bottleneck. Mitigation: write salting, hot-key caching. |
| [`#04 Idempotency`](../../Foundations/Concurrency-and-Consistency/04-idempotency.md) | Enable `idempotence=true` on producers writing to compacted topics to prevent duplicate key entries on network retry. |
| [`tableflow-tech-briefing.md`](../../../Interview/Confluent/TechStack/tableflow-tech-briefing.md) | Apache Iceberg, Delta Lake, Flink vs Kafka Streams, Control/Data Plane — the ecosystem this note connects to. |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 2026 | Note created. Covers log compaction mechanics (at-least-one guarantee, tombstone lifecycle, active segment exception), throughput architecture (sequential I/O, page cache, zero-copy, key numbers), retention-as-TTL, topic/partition design (partition count formula, key strategy, hot partition risk), and Tableflow → Iceberg materialization (5-step flow, atomic commit). Companion to #19. 7 Q&As including 2 Tier-2 probes. |

---

## 📖 Further Reading

- [The Log: What every software engineer should know about real-time data's unifying abstraction](https://engineering.linkedin.com/distributed-systems/log-what-every-software-engineer-should-know-about-real-time-datas-unifying) — Jay Kreps (LinkedIn, Kafka's co-creator). The original essay explaining why the append-only log is the right abstraction for distributed systems. Required reading for anyone joining Confluent.
- [Kafka Documentation: Log Compaction](https://kafka.apache.org/documentation/#compaction) — official specification for compaction mechanics, tombstone lifecycle, and configuration knobs.
- [Confluent Tableflow Documentation](https://docs.confluent.io/cloud/current/topics/tableflow/overview.html) — official Tableflow architecture, supported formats, and configuration.
- [`#22-event-sourcing.md`](../Database-Core/22-event-sourcing.md) — deeper dive into the event sourcing pattern that Tableflow implements.
