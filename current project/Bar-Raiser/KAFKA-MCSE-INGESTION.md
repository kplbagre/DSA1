# Kafka — MCSE Data Ingestion (Technical Deep-Dive + Mental Models)

### Senior Software Engineer | Based on real production work in `mcse_data_ingestion`

> **What this file is:** 5 high-impact, Kafka-centric pieces of work from the MCSE (Multi-Channel Sourcing Engine) data-ingestion service. Each item is **technical** (real class names, real code, real offset/commit semantics) **and** built around a **mental model** so you actually understand *why* it works, not just *what* the code says. Code is included where it's load-bearing, but every snippet is wrapped in plain-English explanation.
>
> **How this maps to the sourcing-engine doc (`BAR-RAISER-TECHNICAL.md`):** That doc is the *read* side — the 700K req/min engine that reads in-memory caches. **This service is the *write* side** — the ingestion tier that keeps Cassandra / Azure SQL / caches fresh by consuming ~20 Kafka streams. When an interviewer asks "how does your data get there and stay fresh?", this is the answer.
>
> **⚠️ Sourcing note:** No local git history was available, so these are reconstructed from the *current* code state. Class names, topics, flags, and code are quoted from the source. Anything I couldn't confirm from code (exact prod scale, dates, thresholds) is marked `[VERIFY]`.

---

## 📋 CONTENTS

1. [Kafka Consumer V1 → V2 Re-architecture](#1--kafka-consumer-v1--v2-re-architecture) — *batch processing, per-topic concurrency, manual commit, config-gated rollout*
2. [Item-Store Re-ingestion: retry-topic + success/failure feedback loop](#2--item-store-re-ingestion-retrytopic--successfailure-feedback-loop)
3. [UFN Cross-Tenant Bulk Ingestion (SAMS)](#3--ufn-cross-tenant-bulk-ingestion-sams)
4. [Multi-Sink Bulk Ingestion — Cassandra ↔ Azure SQL routing](#4--multi-sink-bulk-ingestion--cassandra--azure-sql-routing)
5. [The fault-tolerant consumer contract across all pipelines](#5--the-faulttolerant-consumer-contract-across-all-pipelines)

Plus: [Architecture at a glance](#-service-architecture-at-a-glance) · [Kafka vocabulary you must be fluent in](#-kafka-vocabulary-you-must-be-fluent-in) · [Follow-up questions](#-likely-followup-questions)

---

## 🗺️ SERVICE ARCHITECTURE AT A GLANCE

**Mental model:** the sourcing engine is a customer-facing calculator that must answer "when will this arrive?" in milliseconds. It can only be that fast because it reads from **pre-filled in-memory caches / databases**, never from the slow upstream systems directly. **This service is the crew that keeps those caches filled and current** by continuously consuming Kafka. If we fall behind or drop data, the engine keeps answering — but with *stale* data, and nobody gets an error. So our real product is **freshness and reliability at scale.**

**What it is technically:** a multi-module Maven service (Java 17, Spring, `2.10.1-SNAPSHOT`) with ~20 Kafka consumer pipelines.

**The ~20 pipelines (each a domain):** Capacity (CCAP) & FC-Capacity, DCC (distribution-center config), CASPR (slot events), Item-Store / MP Item-Store (LIMO, Avro), Seller Rules, Bulk carrier-TNT / rate-card / shipping-zone, Predictive TNT, ISO Offer Template, Mercurio & Hyperloop offer-leadtime, GScope, Deliverr, Store/GSF, DC-Square, PNO, MP shipping templates, Partner store template, Golden Dimensions, Site-to-Store lane, Uber logistics/price.

**Data sinks (the "reservoirs"):**
- **Cassandra** (keyspace `MCSE`, LOCAL_QUORUM) — primary store: `mcse_offer`, `mcse_tnt_info`, `mcse_item_store`, `mcse_distributor`, lane/zone lookups.
- **Azure SQL** (`mssql-jdbc:11.2.0.jre17`, HikariCP pool) — bulk carrier/zone/rate data.
- **In-memory / Hollow-style caches** — for domains the engine reads on its hot path.

**Stack:** `kafka-clients 2.8.1`, Avro `1.9.2` + Confluent `kafka-avro-serializer 3.2.1` (schema registry), DataStax driver `4.14.1`.

**The unifying idea to remember:** almost everything about topology — topics, consumer counts, worker-thread counts, V1-vs-V2, sink choice, retry on/off, pause flags — is driven by **CCM (Walmart's live config)**, not hardcoded. That's what makes the service tunable in production without a deploy. Every story below leans on this.

---

## 🔤 KAFKA VOCABULARY YOU MUST BE FLUENT IN

Say these correctly and the technical depth reads as real.

- **Topic** — a named stream of records (e.g. `mcse_offer_ingestion`). Think of it as one river.
- **Partition** — a topic is split into N partitions so multiple consumers can read in parallel. **Ordering is guaranteed only *within* a partition**, never across the whole topic. This constraint drives a lot of design.
- **Offset** — a per-partition sequence number (0,1,2,…). A record's address.
- **Commit / committed offset** — the "bookmark." It records *how far this consumer group has processed*. On restart/rebalance, the consumer resumes from the committed offset. **When you commit relative to when you actually finish the work is the single most important reliability decision in a consumer** — it's the crux of Story 1.
- **Consumer group** — a set of consumer instances sharing a `group.id`. Kafka distributes a topic's partitions across the group. Two *different* group ids reading the same topic each get the *full* stream independently (this is the trick behind Stories 2 and 3).
- **Consumer lag** — `latest offset − committed offset` = how many records you're behind. **The #1 health metric** for this service: rising lag = reservoirs going stale.
- **At-least-once vs exactly-once** — with commit-after-processing you may reprocess on crash (**at-least-once**); you avoid duplicates only if you also make processing **idempotent**. We choose at-least-once + idempotent writes on purpose (Story 5).

---

## 1. 🚀 Kafka Consumer V1 → V2 Re-architecture

> **This is the headline story.** A real re-architecture of the consumption layer: it changes throughput, concurrency, *and* offset-commit semantics, and it shipped via a config-gated, zero-downtime, per-pipeline rollout. It touches every topic an interviewer probes.

### The V1 model (what we had)

**Mental model:** a **two-stage assembly line with a bin in the middle.** Stage 1 (reader threads) scoop records off Kafka and drop them into a shared in-memory bin. Stage 2 (a single listener thread) pulls records out of the bin and processes them one at a time.

Technically, V1 is `KafkaSource` + `KafkaConsumerThread` (in `mcsedata-messaging`) + `KafkaListener` / `KafkaAvroListener`:

- **Stage 1 — reader threads.** `KafkaSource.Source.init()` starts a fixed pool of `KafkaConsumerThread`s. Each polls the topic and does `messages.put(value)` — it **only buffers**, it doesn't process:
  ```java
  final ConsumerRecords<String, String> records = consumer.poll(consumerTimeOut);
  records.forEach(r -> messages.put(r.value()));   // dump into a shared ArrayBlockingQueue
  ```
- **Stage 2 — one processing loop.** `KafkaListener.run()` drains that queue **one record at a time**:
  ```java
  while (kafkaSource.hasNext()) {
      String msg = kafkaSource.get();      // pull ONE off the shared queue
      try { onMessage(msg); }              // process it, THEN loop to the next
      catch (Exception e) { LOG.error(...); }
  }
  ```

**Three structural problems — and why each hurts:**

1. **One global concurrency dial.** The pool size comes from a single property (`kafka.consumers`, default 4) that applies to *every* pipeline:
   ```java
   int numOfConsumers = StringUtils.isBlank(consumers) ? 4 : Integer.parseInt(consumers);
   executor = Executors.newFixedThreadPool(numOfConsumers);
   ```
   A firehose stream (offers) and a trickle stream get identical staffing. To speed up the busy one you'd inflate them all — wasteful and coarse.

2. **Processing is single-threaded per listener.** Stage 2 is `onMessage` one record at a time. Even if 4 reader threads fill the bin fast, one processor drains it slowly → bin fills → readers block. **The processor is the bottleneck**, and V1 has no way to parallelize it.

3. **Auto-commit is a silent-data-loss hazard.** `enable.auto.commit` is on, so Kafka moves the bookmark forward **on a timer** — independent of whether a record is still sitting in the in-memory bin, unwritten. If the pod dies, everything buffered but not yet persisted is **gone**, because the committed offset already jumped past it. **Mental model:** a courier who marks parcels "delivered" the moment they leave the van. Usually fine; catastrophic if the van crashes. For a freshness service, silent loss is the worst failure mode.

### The V2 model (what I moved us to)

**Mental model:** collapse the two stages into one and **replace the single cashier with a small team.** Poll a *batch* of records, hand the batch to a pool of workers who process them **in parallel**, wait for the whole batch to finish, and only *then* tell Kafka "done."

Technically, V2 (`KafkaConsumerV2Impl`, package `...listener.rkConsumer.consumer`) is built on **RKConsumer** (`com.walmart.ca.kp.RKConsumerSerial`) — an internal Walmart wrapper that does the poll loop **and manages offset commit for us**, handing our code a whole `List<ConsumerRecord>` (a batch) per poll.

**Change 1 — per-topic concurrency.** Instead of one global number, each topic reads its own `{noOfConsumers, noOfThreads}` from CCM (`TOPIC_STRATEGY_CONFIG_JSON`):
```java
Map<String,String> cfg = getTopicStrategy(topic, tenantId);       // e.g. iso -> {noOfConsumers:2, noOfThreads:5}
int noOfConsumers = Integer.parseInt(cfg.getOrDefault(NO_OF_CONSUMERS, "1"));
for (int i = 1; i <= noOfConsumers; i++) {
    int noOfThreads = Integer.parseInt(cfg.getOrDefault(NO_OF_THREADS, "1"));
    ExecutorService workerPool = Executors.newFixedThreadPool(noOfThreads);   // this topic's own team
    ...
}
```
So the firehose can be `2 consumers × 5 threads` while a quiet topic is `1 × 1`, **at the same time**, tuned live without a deploy.

**Change 2 — batch processing in parallel.** Each listener (e.g. `LimoKafkaListenerV2`) receives the batch and fans it across the worker pool:
```java
public void onMessage(List<ConsumerRecord<String,String>> batch, ExecutorService workerPool) {
    CompletableFuture.allOf(
        batch.stream()
             .map(record -> processOne(record, workerPool))   // kick each record off ASYNC
             .toArray(CompletableFuture[]::new)
    ).join();                                                  // WAIT until the whole batch is done
}
```

**Why `CompletableFuture` here — the intuition:** a `CompletableFuture` is a *receipt for work that hasn't finished yet*. `supplyAsync(work, workerPool)` starts one record's processing on a worker thread and hands back a receipt **immediately** — it doesn't wait. So `batch.stream().map(...)` fires all (say) 50 records at once across the pool → **this is the parallelism V1 lacked.** `allOf(...)` bundles the 50 receipts into one "all done" receipt, and `.join()` **blocks until every record is actually finished.**

That `.join()` is not cosmetic — it's the **safety linchpin**. RKConsumer commits the offset the instant `onMessage` returns. If we didn't `.join()`, `onMessage` would return while 50 records were still in flight, RKConsumer would commit "done," and a crash a moment later would lose them — reintroducing V1's bug. `.join()` is what makes **"commit only after the work is truly persisted"** true.

**Change 3 — manual, commit-after-processing semantics.** Because RKConsumer commits after our callback returns (which only returns after `.join()`), the guarantee flips from V1's "commit on a timer, maybe before persisting" to V2's **"commit only after the batch is persisted."**

### The trade-off (say this explicitly — it's the mature part)

V2 is **at-least-once**: on a crash between "persisted" and "committed," we reprocess a batch — a few records handled twice. I accepted that deliberately, because **our sinks are idempotent** — writes are upserts keyed by id, so reprocessing lands in the identical state (see Story 5). **I traded "occasionally do work twice" for "never silently lose data,"** which is the correct side of the trade for a freshness pipeline. The old model's failure (lost updates) is invisible and unrecoverable; the new model's failure (a duplicate upsert) is harmless.

### The part that shows judgment: the rollout was config-gated per pipeline

I didn't flip everything at once. A strategy layer picks V1 or V2 **per application context**, read from CCM at startup (`KafkaStrategyImpl.getStrategyType`):
```java
String strategyType = KafkaConsumerStrategy.KAFKA_CONSUMER_V1.getName();     // default = proven/legacy
String json = ccmConfig.getString(tenantId, CcmEnum.KAFKA_CONSUMER_STRATEGY).trim();
// json is a map like { "iso": "KAFKA_CONSUMER_V2", "capacity": "KAFKA_CONSUMER_V1", ... }
if (context != null && map.get(context.getContextName()) != null) {
    strategyType = map.get(context.getContextName());
}
```
Then `AbstractConsumerStrategyFactory.initializeKafkaSource(strategyType, tenantId)` builds either the V1 `KafkaSource` chain or the V2 `KafkaSourceV2` chain (one factory per domain: `IsoConsumerStrategyFactory`, `BulkConsumerStrategyFactory`, …).

**Why this is interview-gold:**
- **Default is V1** — missing/unparseable config falls back to the proven path. Fail *safe*, not *new*.
- **One pipeline at a time** — migrate offers first, watch lag + error metrics, then move the next. Blast radius is exactly one stream.
- **~30-second rollback** — a misbehaving pipeline flips back to `KAFKA_CONSUMER_V1` in config, no deploy, no revert.

### Impact / how to close
"V1 committed offsets on a timer, so a crash could silently skip records, and it had one global concurrency dial with single-threaded processing. V2 processes batches in parallel with per-topic staffing, and commits only after `.join()` confirms the batch is persisted — so a crash means safe reprocessing, not silent loss. And I shipped it behind a per-pipeline CCM flag so a risky change to a live, high-volume system had its blast radius bounded to one stream at a time." `[VERIFY: throughput/lag improvement for the hot stream.]`

---

## 2. 🔁 Item-Store Re-ingestion: retry-topic + success/failure feedback loop

> **This is the "reliable Kafka consumer at scale" story** — directly answers "have you owned a consumer that broke, and do you know what goes wrong?"

### The problem

**Mental model:** a **jammed conveyor**. The Item-Store (LIMO) pipeline ingests Avro item↔store eligibility events. Occasionally one is bad — malformed payload, or Cassandra hiccups mid-write. Two naive reactions are both wrong: **re-throw** (one bad record freezes the partition — Kafka can't advance past it, so *all* records behind it stall → outage), or **swallow silently** (that item's eligibility is now permanently stale and nobody knows).

### What I built

**Mental model:** a **"returns lane" beside the main belt**, plus a **triage rule** for what's worth retrying.

Technically:
- **Main chain:** `ItemStoreConsumer` → `ItemStoreKafkaListener` (Avro) → Cassandra. Topic `limo_item_store_eligibility_status`.
- **Separate retry chain:** `ItemStoreRetryConsumer` → `ItemStoreRetryKafkaListener`, reading a **dedicated retry topic** `mcse_item_store_event_retry`. Separate lane = retrying old failures never slows fresh traffic, and they have independent offsets/lag.
- **A feedback loop back to upstream.** After processing, `ItemStoreResponseHelper.sendResponse(...)` publishes an outcome, gated by CCM (`ITEM_STORE_REINGESTION_ENABLE`, default `false`):
  ```java
  if (ccmConfig.getBoolean(businessId, CcmEnum.ITEM_STORE_REINGESTION_ENABLE)) {
      if (itemStoreError != null) {                                   // failure
          itemStoreKafkaProducer.send(ITEM_STORE_FAILURE_EVENT_TOPIC, event, header);   // mcse_item_store_event_failure
      } else {                                                        // success ack
          itemStoreKafkaProducer.send(ITEM_STORE_SUCCESS_EVENT_TOPIC, null, header);    // mcse_item_store_event_success
      }
  }
  ```
  So the pipeline isn't a black hole — the sender learns whether each event actually landed.

**The triage that makes retries smart:** errors are a **typed enum** (`ItemStoreError`), not strings, and each carries a **DATA vs SYSTEM** classification:
- `ERR0101 PAYLOAD_INVALID`, `ERR0102 IO_EXCEPTION_IN_PARSING` → **DATA** (the message itself is wrong — retrying will *never* help).
- `ERR0104 BUSINESS_EXCEPTION`, `ERR0106 ERROR_WHILE_PERSIST_IN_CASSANDRA` → **SYSTEM** (transient — exactly what retry is for).

This is the key insight to state out loud: **blindly retrying everything wastes capacity on poison messages and can amplify an incident.** Encoding *why* it failed lets the retry lane skip the hopeless ones and only re-attempt the recoverable ones.

**Retries are bounded and persisted.** An `EventError` row tracks `numberOfRetries` and `maxRetries`; the work-selection query is bounded so nothing loops forever:
```sql
SELECT error FROM EventError error
WHERE error.eventSource = :source AND error.status = :status
  AND error.numberOfRetries < error.maxRetries
ORDER BY error.numberOfRetries
```
Exhausted records stay in the error table with full context (payload, stack trace, DC) for a human — this is the **DLQ-equivalent**: a durable error table rather than a dead-letter *topic*.

### The trade-off
Durable **error table** vs. a dead-letter **topic + automated replay**. Pro: queryable ("show everything that failed for this seller today"), survives restarts, and the whole loop is CCM-gated per business unit. Con: recovery is more manual than one-click replay. For our volume and failure mix, queryability + control won; I'd revisit if failure volume grew.

### Impact / how to close
"Three principles, in order of damage prevented: **never re-throw on a bad record** (catch, log with partition+offset, commit past it — a stale-but-moving cache beats a frozen one); **give retries their own lane** so healing the past never starves the present; and **classify + bound retries** so we don't burn cycles on poison messages or loop forever. Every failure stays visible and recoverable, and upstream gets told what happened."

---

## 3. 🏢 UFN Cross-Tenant Bulk Ingestion (SAMS)

> **This is the "multi-tenant blast-radius isolation" story** — small, sharp, recent; great for "tricky isolation problem" or "design for failure."

### The problem

**Mental model:** a **shared mailroom serving two companies**, where one company's mail arrives wearing the *other's* return address. We run shared infrastructure for multiple business units. A slice of data for **UFN (Unified Fulfillment Network)** nodes — operated on the SAMS side — arrives on the **shared bulk-upload topic** but **stamped with the Walmart tenant id** in the header. The SAMS instance must recognize just that slice, relabel it to the correct owner, and process it — without disturbing the normal high-volume bulk flow, and without one side's failures spilling onto the other.

The tempting shortcut: add `if (special case) …` branches into the shared `BulkKafkaListenerV2`. That couples the rare path to the common path — a bug in the rare case can now stall the common case, because they share code *and* a partition position.

### What I built

**Mental model:** a **separate sorting desk with its own clerk**, not more sticky notes on the shared desk.

Technically, on the SAMS instance I stood up a **second, independent consumer** — `BulkUfnKafkaListenerV2` — reading the *same* topic but under its **own consumer group** (group id + `_ufn` suffix, `EventConstants.UFN_SUFFIX`):
- Normal bulk → `bulk-upload-group`
- UFN bulk → `bulk-upload-group_ufn`

Because a **different consumer group gets its own copy of the full stream with its own committed offsets**, the two lanes have **independent progress and independent failure**. A poison UFN record or a slow UFN batch **cannot** stall normal bulk ingestion, and vice versa. *That's the whole point — this isn't duplication, it's blast-radius containment.*

The eligibility + relabel logic is deliberately narrow and defensive:
```java
if (runAsTenant.equals(SAMS_TENANT_ID)                       // only on SAMS
        && StringUtils.equals(tenantId, WM_TENANT_ID)         // header says Walmart
        && ufnNodesMap.containsKey(distributorId)) {          // and it's a known UFN node
    return Optional.of(rewritePayloadForUfnCrossTenant(payload, distributorId, ufnNodesMap.get(distributorId)));
}
return Optional.empty();   // not a UFN message -> this consumer simply skips it (no side effect)
```
- **`ufnNodesMap` is loaded from Cassandra config**, not hardcoded → ops can add UFN nodes without a deploy.
- The rewrite swaps **exactly one field** (the distributor id) and **falls back to the original payload** if anything looks off, rather than guessing.
- `Optional.empty()` means non-UFN records are cleanly ignored by *this* consumer (the normal consumer handles them) — no exceptions, no double-processing.

### The trade-off
Running a second consumer costs a bit more infrastructure than a few `if`s. I paid it on purpose: **the value of "a bad SAMS message can never hurt Walmart's ingestion" is worth one extra lane.** Isolation over convenience.

### Impact / how to close
"The judgment call was *separate consumer, not more branches in the shared listener.* It looks like duplication but it's containment — the rare path and the common path have independent offsets and can't take each other down. Plus defensive touches: config-driven node map, single-field rewrite, `Optional`-based skip, graceful fallback."

---

## 4. 🔀 Multi-Sink Bulk Ingestion — Cassandra ↔ Azure SQL routing

> **This is the "abstraction + safe migration" story** — for "how do you make a risky/foundational change?"

### The problem

**Mental model:** **swapping a plane's engine while it flies.** The bulk pipeline (carrier TNT, rate-card, shipping-zone) needed to be able to write to **Azure SQL** *or* **Cassandra**. A big-bang cutover — flip everyone one night and pray — is exactly what causes outages you can't quickly undo.

### What I built

**Mental model:** the sink is a **pluggable destination chosen by config, per business unit** — one dial per tenant, each with a return ticket.

Technically, `BulkConsumerFactory` is a per-BU factory returning the right sink for each of the three domains:
- Cassandra: `BulkCarrierTntCassandraConsumer`, `CarrierZoneChargeCassandraConsumer`, `BulkShippingZoneCassandraConsumer`
- Azure SQL: `BulkCarrierTntAzureConsumer`, `CarrierZoneChargeAzureConsumer`, `BulkShippingZoneAzureConsumer`
- Legacy/default: `BulkCarrierTntConsumer`, `CarrierZoneChargeMessageConsumer`, `BulkShippingZoneConsumer`

Selection is a config lookup on `CcmEnum.BULK_INGESTION_WRITES_TO` (`AZURE_FLOW` / `CASSANDRA_FLOW` / else legacy). Because it's **per-BU**, Mexico can be on Azure SQL while US is on Cassandra **simultaneously** — so a backend migration becomes: move one BU, watch it under real traffic, confirm data landed, move the next; roll any one back by flipping its dial. **Incremental, observable, reversible** — the three properties that make a scary migration boring.

**Partial-failure isolation in a batch.** Same batch pattern as Story 1: each record runs on its own `CompletableFuture` with its own try/catch, so one poison row is logged (with partition/offset) and the batch still commits the good rows. Failures are captured durably by `BulkEventManager` into a failure record (Cassandra `BulkUploadError` table or the legacy SQL failure table — itself a config choice), with payload, line number, and a message truncated to 400 chars. **For bulk loads, getting 99% in and knowing precisely which 1% failed beats rejecting the whole file.**

### The trade-off
Supporting two sinks at once means maintaining two write paths for a while — more code and test surface than forcing everyone onto one. I accepted that **temporary** complexity as the price of a reversible migration; a hard cutover is cheaper to build but risks an un-undoable outage. For a foundational data store, safety wins.

### Impact / how to close
"I turned a backend migration into a per-BU, config-driven rollout with an instant rollback dial instead of a big-bang cutover — and within a batch, per-record `CompletableFuture` isolation means a single bad row is recorded and skipped while the rest lands. Small, observable, reversible steps."

---

## 5. 🛡️ The fault-tolerant consumer contract across all pipelines

> **This is the "philosophy / systems maturity" story** — for "how do you design for failure?" It shows the reliability is *consistent*, not one lucky fix.

Across all ~20 consumers the same invariants hold. State them as a contract — the consistency is the point.

**1. Catch, log with coordinates, commit — never re-throw.**
Every listener wraps `onMessage` in try/catch and logs `partition`+`offset` on failure (V1's `KafkaAvroListener`, every V2 `*ListenerV2`). A malformed record is dropped-with-a-trail, not allowed to freeze the partition. **Rationale (say it):** this is a *cache-hydration* tier; a stale-but-moving cache beats a frozen one. One bad message must never take down a stream thousands of sourcing decisions depend on.

**2. Idempotent by design, because V2 is at-least-once.**
Manual commit (Story 1) means reprocessing on crash/rebalance is *expected*. Sinks tolerate it: Cassandra writes are **upserts** (last-write-wins per key), so reprocessing the same event converges to the same state. **Mental model:** saving a document twice leaves you with one document. This isn't luck — it's a property enforced in code review *because* the commit model guarantees we will reprocess.

**3. Isolate by concern to shrink blast radius.**
Separate lanes for separate concerns — a retry consumer (Story 2), a per-tenant consumer (Story 3) — so a failure in one can't cascade. Different `group.id` = independent offsets = independent failure.

**4. If it moves, it's measured.**
`TransactionLogger` emits **batch-level** (`BATCH_*`) and **event-level** (`EVENT_*`) spans plus typed failure metrics (`logNonResponseTimeTransactionMetric`). With per-record partition/offset logging, on-call answers "which records failed, on which partition, since when" from a dashboard — not by grepping raw logs.

**5. Topology is config, not code.**
Topics, consumer counts, worker-thread counts, V1/V2 choice, sink choice, retry on/off, pause flags (`PAUSE_LIMO_LISTENER`, `GSCOPE_EVENTS_UPLOAD_LISTENER`) — all CCM. Pause a misbehaving consumer, resize a hot one, or roll back a strategy **in seconds without a deploy.** This is the operational lever that makes everything above safe to change live.

### How to close
"The unifying idea is that ingestion must **degrade gracefully and stay observable**, and the important levers must be adjustable without a deploy. Every consumer catches-logs-commits so no single record freezes a partition; every sink is idempotent so at-least-once reprocessing is safe; every knob is config so a 2am response is 'flip a flag, watch the dashboard,' not a scramble. The consistency is deliberate — enforced in review, not rediscovered per incident."

---

## ❓ LIKELY FOLLOW-UP QUESTIONS

- **"Why RKConsumer instead of Spring Kafka's `@KafkaListener` + `ConcurrentMessageListenerContainer`?"** → It's the internal Walmart standard; it gave us batch + manual-commit semantics, and the strategy-factory abstraction let V1 and V2 coexist. Be ready to compare: Spring Kafka would give `DefaultErrorHandler` / `DeadLetterPublishingRecoverer` out of the box — which we instead built as the error-table + retry-topic mechanism (Story 2).
- **"At-least-once — how do you prevent duplicate side effects?"** → Idempotent upserts keyed by id (Story 5). If a sink weren't idempotent we'd need a dedup key or a processed-offset table.
- **"How do you know a consumer is falling behind?"** → Consumer **lag** per pipeline is the headline metric; rising lag = the engine's caches going stale. `[VERIFY: your lag alert thresholds per pipeline.]`
- **"Batch size / poll tuning?"** → `[VERIFY: default ~20 records/poll in config; confirm the tuned prod values in TOPIC_STRATEGY_CONFIG_JSON for hot topics.]`
- **"Ordering?"** → Preserved per partition. Within a batch we process concurrently, so correctness relies on sinks being **commutative/idempotent per key**, not on intra-batch order. If strict per-key order mattered, I'd key-partition and process per-key serially.
- **"Schema evolution?"** → Avro + Confluent schema registry for LIMO/ISO; new fields optional (backward compatible) so old producers don't break new consumers.
- **"What would you improve?"** → Automated replay from the error table (Story 2's trade-off); and collapse the isolated-tenant duplication (Story 3) once proven. Shows I track my own debt.

---

### 🎯 Which story to lead with
- **Biggest project / proudest work →** #1 (V1→V2): scope + trade-off + safe rollout.
- **Reliability / owned an incident →** #2 (retry), backed by #5.
- **Multi-tenancy / isolation →** #3 (UFN): short, sharp.
- **Risky change / migration →** #4 (multi-sink).
- **"How do you design for failure?" →** #5 (the contract) ties the other four together.
