# #61 — Stream Processing: Apache Flink, Kafka Streams & Spark Streaming

> **Companion to:** [`#19 Message Queues`](./19-message-queues-kafka-rabbitmq.md) · [`#60 Kafka Internals`](./60-kafka-internals.md)
>
> **What #19 already covers:** Kafka as a transport/broker — partitions, offsets, consumer groups, delivery guarantees.
>
> **What this note adds:** The *processing layer* downstream of Kafka. How to compute aggregations, joins, and transformations over a stream of events. When to pick Flink vs Kafka Streams vs Spark Streaming.

---

## 📖 What is Stream Processing? (Section 0.5)

**Full form:** Stream Processing — continuous, real-time computation over data as it arrives event-by-event (or in micro-batches), rather than collecting a full dataset first.

**Simple analogy:** Batch processing is a bakery that bakes all the bread at 4am in one giant batch — efficient, but customers wait until morning. Stream processing is a conveyor-belt bakery that bakes each loaf the moment it's assembled. Higher per-loaf overhead, but nothing waits.

**Core principle:** Separate where data is stored/transported (Kafka, the pipe) from where data is computed on (Flink / Kafka Streams, the processor). Data flows through Kafka continuously; the processor reacts to each event as it arrives.

**Why it matters in system design:** Any design question involving real-time aggregations (fraud detection, leaderboards, alerting, ETL into data lakes) requires naming the processing layer. "Consume from Kafka" is table stakes. Naming the right tool and explaining stateful windowing, exactly-once guarantees, and watermarks is the senior signal.

---

## 🎯 Why This Matters (Section 1)

Kafka solves *delivery*. Stream processing solves *computation*. They're two different layers — but interviews that involve Kafka almost always reach for "what computes on this stream?" as a follow-up. Not having an answer for that is a gap that's easy to avoid.

This is the note that connects `#19 Message Queues` to real-time pipeline design — fraud detection, live analytics, enrichment, ETL — the patterns that show up in every system that ingests events.

---

## 📖 Terminology

| Term | Plain-English meaning |
|---|---|
| **Stream processing** | Computing over data as it arrives, continuously, without collecting it all first |
| **Event time** | The timestamp embedded IN the event — when it actually happened on the client |
| **Processing time** | When the processor RECEIVED the event — may be later due to network delay |
| **Watermark** | A progress marker: "I'm confident all events up to timestamp T have arrived — safe to close windows ending at T" |
| **Late event** | An event that arrives after its window's watermark has passed — processor must drop, sideload, or recompute |
| **Window** | A bounded time range for aggregation — "count events in the last 5 minutes" |
| **State** | Data the processor remembers across events — a running count, a join buffer, a session |
| **Checkpoint** | A consistent snapshot of all operator state, written to durable storage — enables crash recovery |
| **Exactly-once** | Each input event affects the output exactly once — no duplicates, no drops — even after failure |
| **Backpressure** | When processor is slower than producer, upstream slows down rather than dropping events |
| **Topology** | The directed graph of transformations in a Kafka Streams app — source → operations → sink |

---

## 🧠 The Mental Model (Section 2)

**The river, the waterwheel, and the mill:**

Kafka is a river. Water flows continuously — you can't pause it, and it doesn't wait for you. Events are the water molecules. The river doesn't compute anything; it just carries water from source to sea.

**Kafka Streams** is a small waterwheel you bolt onto the side of YOUR building, right where the river passes. It turns with the river's current and powers a simple machine inside your building (filtering, counting per key). The wheel runs as part of your building — no separate mill required. It can only output back into the river (another Kafka topic).

**Apache Flink** is a full industrial mill a short distance downstream. The mill can run complex machines (joins across multiple rivers, sliding time windows, stateful pattern detection). It takes in water from multiple sources, processes it, and can pump the output ANYWHERE — a reservoir (Postgres), a frozen lake (Iceberg), or back into the river. But it requires its own building, its own staff (a Flink cluster), and its own storage for work-in-progress (checkpoints).

**Spark Streaming** is a mill that runs in micro-bursts — it collects water into a holding tank for 1 second, processes the tankful, then repeats. Not truly continuous, but the same mill also handles historical water analysis (batch), so it's one unified machine.

**The failure analogy:** The mill (Flink) periodically takes a photograph of all the water in its pipes (checkpoint). If the mill catches fire and is rebuilt from the photograph, no water is processed twice and none is lost. That's exactly-once recovery.

**The key insight:** Kafka is the pipe; stream processors are the machines. You need both. The right machine depends on: complexity of computation, whether the output is another Kafka topic or an external system, and how much latency you can accept.

---

## 🎨 Visual — Full System Topology + Component Detail (Section 3)

### System Topology — Where Stream Processing Lives

```
CLIENT TIER                  SERVICE TIER             STREAM TIER            DATA TIER
───────────                  ────────────             ───────────            ─────────
                             ┌───────────┐
Mobile/Web    ──HTTP──▶      │  API      │
                             │  Service  │
IoT Sensors   ──────────────▶│  (writes  │
                             │  events)  │
                             └─────┬─────┘
                                   │ publish
                                   ▼
                             ┌───────────┐
                             │  KAFKA    │   ◀── THIS IS THE TRANSPORT
                             │  Topics   │        (stores + delivers events)
                             └─────┬─────┘
                                   │ consumed by
                         ┌─────────┴──────────┐
                         │                    │
                ┌────────▼──────┐   ┌─────────▼───────┐
                │ KAFKA STREAMS │   │  APACHE FLINK   │  ◀── PROCESSING LAYER
                │ (in-process   │   │  (separate      │       (computes on events)
                │  library)     │   │   cluster)      │
                └────────┬──────┘   └─────────┬───────┘
                         │                    │
                         ▼                    ▼
                   Kafka topic          Postgres / Iceberg
                   (output)             Redis / Kafka
                                        (any sink)

KEY INVARIANT:
  Kafka = transport only. Stream Processor = computation only.
  They are separate concerns and separate deployment units (except Kafka Streams,
  which is a library embedded in your service).
```

### Component Detail — Windowing Types

```
INPUT STREAM:  │e│e│e│  │e│e│  │e│  │e│e│e│e│  │e│
Timeline:      0   1   2   3   4   5   6   7   8   9   10 (minutes)

─────────────────────────────────────────────────────────────────────
TUMBLING WINDOW (size=3min) — non-overlapping, fixed boundaries:
                ├──── W1 ────┤├──── W2 ────┤├──── W3 ──────
                0           3             6              9

─────────────────────────────────────────────────────────────────────
SLIDING WINDOW (size=4min, slide=2min) — overlapping, fires every 2min:
                ├──────── W1 ────────┤
                        ├──────── W2 ────────┤
                                ├──────── W3 ────────┤
                0       2       4       6       8      10

─────────────────────────────────────────────────────────────────────
SESSION WINDOW (idle gap=2min) — closes after 2min silence:
                │e│e│   2min gap   │e│e│e│  2min gap   │e│
                ├─ S1 ─┤           ├──── S2 ────┤       ├─ S3

KEY INVARIANT:
  Tumbling = billing/reporting cycles (no overlap).
  Sliding  = "in the last N minutes" fraud/anomaly detection (overlapping).
  Session  = user activity grouping (variable length, gap-driven).
```

### Component Detail — Watermarks and Late Events

```
Events arrive at Flink (processing time shown):

Processing time: ─────────────────────────────────────────▶
Event arrives:    [e=8:00] [e=8:01] [e=8:03] [e=8:05]  [e=7:59 ← LATE]

Event time:       8:00     8:01     8:03     8:05        7:59

Watermark strategy: "allowed lateness = 5 seconds"
  → Watermark at processing time 8:05 = 8:00 (5sec behind current)

Window [8:00 - 8:05) fires when watermark reaches 8:05
Late event e=7:59 arrives AFTER the window fired:
  → Option 1: DROP (default after allowed lateness expires)
  → Option 2: SIDE OUTPUT → separate stream for late processing
  → Option 3: RECOMPUTE (if trigger configured to update)

KEY INVARIANT:
  Watermark = "safe to close windows up to this event time."
  Larger gap between watermark and current time = lower latency but
  more tolerance for late events. Smaller gap = lower latency, more drops.
```

---

## ⚙️ How It Actually Works (Section 4)

### Kafka Streams — A Simple Per-Key Aggregation

**Steps in plain English:**

1. **Define a topology** — declare what Kafka topics to read from, what operations to apply, and where to write output. This is a directed graph, not imperative code.
2. **Create a KStream** from the input topic — every message becomes an event in the stream.
3. **Apply stateless or stateful transformations** — filter, map, groupBy, count, aggregate.
4. **Write to output topic** — Kafka Streams can only sink to another Kafka topic.
5. **Start the KafkaStreams instance** — it manages consumer group assignment, threading, and local state stores automatically.

```java
Properties props = new Properties();
props.put(StreamsConfig.APPLICATION_ID_CONFIG, "order-counter");
props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "kafka:9092");
props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass());

StreamsBuilder builder = new StreamsBuilder();

// Step 2: read from input topic — each record is (userId, orderJson)
KStream<String, String> orders = builder.stream("raw-orders");

// Step 3: filter and count per user in a 5-minute tumbling window
KTable<Windowed<String>, Long> orderCounts = orders
    .filter((userId, orderJson) -> orderJson != null)
    .groupByKey()
    .windowedBy(TimeWindows.ofSizeWithNoGrace(Duration.ofMinutes(5)))
    .count();

// Step 4: write windowed counts to output topic
orderCounts
    .toStream()
    .map((windowedKey, count) -> KeyValue.pair(windowedKey.key(), count.toString()))
    .to("order-counts-per-user");

// Step 5: start the processor
KafkaStreams streams = new KafkaStreams(builder.build(), props);
streams.start();
Runtime.getRuntime().addShutdownHook(new Thread(streams::close));
```

### Apache Flink — Stateful Sliding Window (Fraud Detection Pattern)

**Steps in plain English:**

1. **Create a StreamExecutionEnvironment** — the entry point; configures parallelism and checkpointing.
2. **Add a Kafka source** — Flink reads from a Kafka topic using the FlinkKafkaConsumer.
3. **Assign timestamps and watermarks** — tell Flink to use event time with a 5-second allowed lateness.
4. **Key the stream** — group events by a key (e.g., `card_id`) so state is partitioned.
5. **Apply a sliding window** — define the window size and slide interval.
6. **Aggregate within the window** — count events, sum values, apply a condition.
7. **Write to sink** — Flink can write to Postgres, Iceberg, Kafka, Redis, or any configured connector.

```java
StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

// Step 1: enable checkpointing every 60 seconds to S3
env.enableCheckpointing(60_000);
env.getCheckpointConfig().setCheckpointStorage("s3://my-bucket/flink-checkpoints");

// Step 2: Kafka source
KafkaSource<Transaction> source = KafkaSource.<Transaction>builder()
    .setBootstrapServers("kafka:9092")
    .setTopics("card-transactions")
    .setValueOnlyDeserializer(new TransactionDeserializer())
    .build();

DataStream<Transaction> transactions = env.fromSource(
    source,
    // Step 3: event-time watermarks with 5-second allowed lateness
    WatermarkStrategy.<Transaction>forBoundedOutOfOrderness(Duration.ofSeconds(5))
        .withTimestampAssigner((tx, ts) -> tx.getEventTimeMs()),
    "Kafka Source"
);

DataStream<Alert> alerts = transactions
    // Step 4: key by card_id so state is per-card
    .keyBy(Transaction::getCardId)
    // Step 5: sliding window — last 10 minutes, evaluated every 1 minute
    .window(SlidingEventTimeWindows.of(Time.minutes(10), Time.minutes(1)))
    // Step 6: count transactions in window; flag if count >= 5
    .process(new ProcessWindowFunction<Transaction, Alert, String, TimeWindow>() {
        @Override
        public void process(String cardId, Context ctx,
                            Iterable<Transaction> txns, Collector<Alert> out) {
            long count = StreamSupport.stream(txns.spliterator(), false).count();
            if (count >= 5) {
                out.collect(new Alert(cardId, count, ctx.window().getEnd()));
            }
        }
    });

// Step 7: write to Kafka alert topic
alerts.sinkTo(KafkaSink.<Alert>builder()
    .setBootstrapServers("kafka:9092")
    .setRecordSerializer(KafkaRecordSerializationSchema.builder()
        .setTopic("fraud-alerts")
        .setValueSerializationSchema(new AlertSerializer())
        .build())
    .build());

env.execute("Fraud Detection Job");
```

---

## 🏢 Real World — Where Companies Use This (Section 5)

- **Lyft** (surge pricing): Apache Flink computes real-time supply/demand ratio per geo-cell using a sliding 5-minute window joining ride-request events with driver-available events. Output feeds the pricing engine. Flink chosen because the join spans two Kafka topics and requires exactly-once delivery to the pricing DB.

- **Swiggy** (order enrichment): Kafka Streams enriches raw order events with restaurant metadata (cuisine, rating, distance) before downstream consumers see the order. Kafka Streams chosen because input and output are both Kafka topics, logic is simple per-key lookup — no external sink needed.

- **Netflix** (viewing metrics): Spark Structured Streaming computes watch-time and completion-rate per title with ~30-second latency, feeding into Iceberg for downstream analytics. Spark chosen because the same codebase handles historical backfill (batch) and live metrics — unifying two pipelines into one.

- **Amazon** (payment fraud): Apache Flink detects patterns like "5 failed payment attempts within 2 minutes for the same card." Session windows (gap = 2 min) group attempts into a session; Flink flags sessions with failure count ≥ 5. State TTL = 10 minutes to bound state size.

- **Confluent / Tableflow** (pipeline health): Kafka Streams monitors consumer group lag per partition and publishes lag metrics back to a Kafka topic. Downstream alerting consumers trigger if lag exceeds threshold. Kafka Streams is the natural fit — monitoring Kafka by consuming Kafka.

---

## 🧭 When to Use vs When NOT to Use (Section 6)

| Use this when | Do NOT use when |
|---|---|
| Data must be acted on in real time (fraud, alerting, live dashboards) | Latency of minutes or hours is acceptable → batch job is simpler |
| Multiple services need the same event stream independently | Only one consumer → direct DB write is simpler |
| Fan-out: one event triggers writes to multiple sinks | Single sink, simple transform → direct consumer or lambda |
| Stateful aggregations over time (sliding window counts, session detection) | Stateless transforms only → a simple Kafka consumer suffices |
| Exactly-once delivery to external sinks is required | At-least-once with idempotent sink is acceptable → simpler setup |
| Event-time correctness with late-arriving events | Events always arrive in order, no late events → processing time is fine |

**The common mistake:** Reaching for Flink when Kafka Streams would do. Flink requires a cluster, checkpoint storage, and operational overhead. If your input and output are both Kafka topics and the logic is per-key, Kafka Streams runs in-process with zero extra infrastructure.

---

## ⚠️ Trade-offs (Section 7)

| | |
|---|---|
| **You gain** | Real-time computation without batch latency. Exactly-once guarantees with Flink checkpointing + idempotent sinks. Event-time correctness via watermarks for out-of-order events. Horizontal scalability: add Kafka partitions and Flink parallelism increases automatically. Fan-out: one Kafka topic feeds multiple independent processors. |
| **You lose** | Operational complexity: a Flink cluster (JobManager + TaskManagers) requires deployment, monitoring, checkpoint storage (S3/HDFS), and upgrade coordination. State management is your responsibility — unbounded state grows to OOM if TTL is not set. Debugging stateful stream jobs is harder than debugging batch jobs (no "re-run on a snapshot"). |
| **Failure mode** | If state TTL is not configured for a stateful Flink job, state accumulates indefinitely — the job OOMs at 3am with no warning. If processing-time windows are used instead of event-time windows for mobile or IoT events (which batch and send with delay), aggregations silently produce wrong results — events arrive late and fall into a future window rather than the correct one. |

---

## 🔬 Interview Q&As — Probes (Section 8)

**Q: "What's the difference between Kafka and Flink?"**
> "Kafka is a message broker — it stores and delivers events durably across services. Flink is a stream processor — it reads those events from Kafka and computes aggregations, joins, and transformations over them. Kafka answers 'how do I move data reliably?'; Flink answers 'how do I compute something on that data in real time?' You almost always use them together: Kafka as source and sink, Flink as the computation engine in between."

**Q: "Kafka Streams vs Flink — which for fraud detection?"**
> "Flink. Fraud detection requires: sliding time windows to detect patterns like '5 failed logins in 10 minutes' — Flink's windowing API handles this natively; event-time semantics with watermarks — a mobile event with a 3-second network delay shouldn't fall in the wrong window; writes to an alert store (Postgres or Kafka) with exactly-once guarantees — Kafka Streams can only output to Kafka. Kafka Streams is right only when input and output are both Kafka topics and logic is simple per-key transforms."

**Q: "What is a watermark and why do you need it?"**
> "A watermark is Flink's way of knowing that event time has progressed far enough to safely close a time window. Without watermarks, Flink would wait indefinitely for late events. A watermark at timestamp T means: 'I'm confident all events with event time ≤ T have arrived.' Events that arrive after their window's watermark are late events — you configure allowed lateness to keep the window open a bit longer, or route them to a side output stream."

**Q: "How does Flink handle failure and recovery?"**
> "Flink checkpoints every N seconds — a consistent snapshot of all operators' state written to S3 or HDFS. If a TaskManager crashes, the job restarts from the last checkpoint. With Kafka as source, Flink rewinds the consumer offset to the one stored in the checkpoint and reprocesses from there. Combined with an idempotent sink (INSERT ON CONFLICT DO NOTHING, or Iceberg's transactional commit), this achieves exactly-once end-to-end."

**Q: "Your Flink job's memory keeps growing until it crashes every 2 days. What's wrong?"**
> "State TTL is not configured. Flink's stateful operators — aggregations, joins, keyed process functions — accumulate state per key. If keys are unbounded (user IDs, card IDs, session IDs) and TTL is not set, the state backend (RocksDB) fills up and the job OOMs. Fix: `StateTtlConfig.newBuilder(Duration.ofHours(24)).build()` on every stateful operator — set TTL to the window size plus allowed lateness, so state is purged after it can no longer be reached by any in-flight window."

---

## 🧾 TL;DR — One Interviewer-Ready Line (Section 9)

> "Stream processing is computation over data as it arrives: Kafka Streams for in-process Kafka-to-Kafka transforms with zero cluster overhead, Apache Flink for stateful aggregations over event-time windows or exactly-once delivery to external sinks, and Spark Structured Streaming when batch + streaming unification matters more than sub-second latency."

---

## 🗺️ Related Concepts

| Concept | Relevance |
|---|---|
| [`#19 Message Queues — Kafka & RabbitMQ`](./19-message-queues-kafka-rabbitmq.md) | The transport layer that feeds stream processors |
| [`#60 Kafka Internals`](./60-kafka-internals.md) | Kafka's throughput architecture — partitions and consumer groups are prerequisite |
| [`#10 Backpressure`](../Resilience-and-Fault-Tolerance/10-backpressure.md) | When the stream processor can't keep up with Kafka — producer slows down |
| [`#04 Idempotency`](../../Foundations/Concurrency-and-Consistency/04-idempotency.md) | Exactly-once in Flink requires idempotent sinks — this is the enabling mechanism |
| [`#31 CQRS`](../../Production-Grade/System-Design-Patterns/31-cqrs-read-write-separation.md) | Stream processors implement the write-side of CQRS — compute projections from the event stream |

---

## 📚 Further Reading (Section 0 — Optional)

| Resource | What it adds | Time |
|---|---|---|
| **Flink Architecture** — flink.apache.org/docs | Official JobManager/TaskManager diagrams; checkpointing internals beyond what's in this note | ~20 min |
| **Kafka Streams vs Flink** — Confluent blog (search "Kafka Streams vs Flink Confluent") | Feature-by-feature comparison from the team that maintains Kafka Streams | ~10 min |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 2026 | File created (first draft — missing standards sections). |
| Aug 2026 | Full rewrite to comply with notes-standards.md: added Section 2 (Mental Model — river/waterwheel/mill analogy), Section 3 (full system topology diagram + 2 component detail diagrams with KEY INVARIANT), Section 4 (How It Works — Kafka Streams topology + Flink fraud-detection code, English steps before code), Section 5 (Real World — 5 companies with specific context), Section 6 (When to Use table), Section 7 (Trade-offs 3-row table), Section 9 (TL;DR). All 10 required sections now present. |
