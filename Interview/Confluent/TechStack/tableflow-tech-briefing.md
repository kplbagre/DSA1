# Tableflow Tech Stack — Interview Briefing

> **Purpose:** Concise, interview-ready briefing on every JD technology you need to know beyond Apache Kafka. None of these require deep mastery — Kafka internals are the depth probe. These are the "connecting tissue" that shows you understand the ecosystem your team operates in.
>
> **Rule:** Know enough to answer "what is X and why does Confluent use it?" for each. You don't need to code these — you build the Kafka side. But the interviewer WILL check whether you understand where your output goes.
>
> **Cross-reference:** Gap tracker → `../tech-stack-gaps.md` | Kafka internals → `../../../SystemDesignConcepts/Core-Architecture/Service-Communication/60-kafka-internals.md`

---

## Section 1 — Control Plane vs Data Plane

### What is it?

This is the most important architectural concept from the JD. Tableflow has two explicit halves that you may be asked to design or choose between.

| | Control Plane | Data Plane |
|---|---|---|
| **Role** | The brain — orchestrates, manages, decides | The muscle — moves data, does the actual work |
| **What it does** | Table lifecycle management: create, update, pause, delete Iceberg tables. Reconciliation loops. Health checks. Configuration management. | Reads from Kafka, converts to Parquet, writes to S3, updates Iceberg metadata. The hot path. |
| **Traffic volume** | Low — lifecycle events are infrequent | High — every Kafka record flows through this path |
| **Latency tolerance** | Can be slow (seconds to minutes for table creation) | Must be fast (milliseconds-to-seconds for data freshness) |
| **Failure consequence** | Table creation paused — no data loss | Data pipeline stalls — consumer lag grows |
| **Technology pattern** | Leader election, distributed state, reconciliation loop (Kubernetes operator pattern) | Consumer group, throughput optimized, exactly-once semantics |

### 🎨 Visual — Control vs Data Plane Boundary

```
USER / CLOUD CONSOLE
       │
       │ "Create Iceberg table from topic X"
       ▼
┌─────────────────────────────────────────────────────┐
│  CONTROL PLANE                                      │
│  ─────────────────────────────────────────────────  │
│  • Table lifecycle API (create/pause/delete)        │
│  • Resource provisioner (allocates data plane pods) │
│  • Metadata store (table config, offsets, state)    │
│  • Health monitor (is the data plane making progress│
│    on its consumer offset? escalate if not)         │
│                                                     │
│  "I decided a data plane instance should now exist  │
│   for topic X → Iceberg table Y"                   │
└────────────────────┬────────────────────────────────┘
                     │ provision + configure
                     ▼
┌─────────────────────────────────────────────────────┐
│  DATA PLANE                                         │
│  ─────────────────────────────────────────────────  │
│  • Kafka consumer (polls topic X)                   │
│  • Parquet serializer (convert records)             │
│  • S3 writer (write data files)                     │
│  • Iceberg metadata updater (snapshot commits)      │
│                                                     │
│  "I am continuously writing records from topic X    │
│   into Iceberg table Y at S3://bucket/..."          │
└────────────────────────────────────────────────────┘
```

### Interview Q&A

**Q: "The JD mentions both control plane and data plane. Walk me through how they interact in Tableflow."**

> When a user creates a Tableflow connector in the Confluent Cloud console, the request hits the **control plane** — a Kubernetes-operator-style service that manages table lifecycle. The control plane validates the request, registers the table metadata in its store, and provisions a **data plane** instance (a Kafka consumer process) configured to poll the source topic.
>
> The data plane is the hot path: it runs continuously, reading records, converting to Parquet, writing to S3, and committing Iceberg snapshots. It's throughput-critical.
>
> The control plane monitors the data plane's health — specifically, is the consumer offset advancing? If the data plane falls behind or crashes, the control plane detects it (via heartbeat or offset check), alerts, and triggers remediation (restart or failover).
>
> This separation matters for 99.99% availability: a control plane issue (e.g., you can't create new tables for 10 minutes) doesn't affect in-flight data plane pipelines. The data plane serves data independently.

---

## Section 2 — Apache Iceberg

### What is it?

**Apache Iceberg** (an open-source table format — a specification for how to organize data files on object storage and track their metadata — that gives you ACID transactions, schema evolution, and time travel on top of cheap cloud storage like S3) is the output target of Tableflow.

Think of it as: "What if you could do SQL database things on S3 files?"

Regular S3 is just folders and files. No transactions, no schema, no "list me all records where user_id=123" efficiently. Iceberg adds a metadata layer on top of S3 that turns it into something query engines (Spark, Flink, Trino, DuckDB) can treat like a table.

### The Three Things Iceberg Adds

**1. ACID transactions** (Atomicity, Consistency, Isolation, Durability — database properties guaranteeing that writes either fully succeed or fully fail) — multiple writers can safely update the same table. Tableflow commits new Parquet files atomically via snapshot commits (pointer-swap, not overwrite).

**2. Time travel** — every snapshot of the table is preserved in the metadata chain. You can query "what did this table look like 3 days ago?" without keeping a separate backup. Useful for auditing and debugging.

**3. Schema evolution** — you can add, rename, or drop columns without rewriting all the existing Parquet files. The metadata layer handles the mapping.

### How Iceberg Stores Data

```
S3 bucket/
  my-table/
    metadata/
      v1.metadata.json   ← table schema + list of snapshot IDs
      v2.metadata.json   ← latest snapshot after Tableflow write
      snap-001.avro      ← manifest list: which manifest files exist
      manifest-001.avro  ← manifest: which Parquet files are in this snapshot
    data/
      part-000.parquet   ← actual records, columnar format
      part-001.parquet   ← next batch from Tableflow
```

Tableflow's write = add `part-00N.parquet` → update `manifest.avro` → write new `v{N+1}.metadata.json` → atomic pointer swap. If anything fails mid-write, the old `v{N}.metadata.json` pointer still points to the last good snapshot.

### Interview Q&A

**Q: "What is Apache Iceberg and why did Confluent choose it over other table formats?"**

> Apache Iceberg is an open table format — a specification for how to organize Parquet data files on object storage (S3, GCS) and track their metadata with ACID semantics. It gives you SQL-database capabilities (transactions, schema evolution, time travel) on top of cheap cloud object storage.
>
> Confluent chose Iceberg over Delta Lake because Iceberg is storage-system-agnostic and truly open. Delta Lake, while technically open-source, is primarily optimized for the Databricks ecosystem. Iceberg is supported natively by AWS (Glue, Athena), Google Cloud (BigQuery), Azure (ADLS), and every major open-source query engine. For Confluent's multi-cloud strategy (Tableflow must work on AWS, Azure, and GCP), Iceberg's openness and broad ecosystem support are decisive advantages.

---

## Section 3 — Delta Lake vs Iceberg

### Quick Comparison

| | Apache Iceberg | Delta Lake |
|---|---|---|
| **Created by** | Netflix (open-sourced at Apache) | Databricks |
| **Primary ecosystem** | Cloud-agnostic: AWS, GCP, Azure, open source | Databricks-optimized (works outside, but optimized within) |
| **ACID** | Yes | Yes |
| **Time travel** | Yes | Yes |
| **Schema evolution** | Yes | Yes |
| **Query engines** | Spark, Flink, Trino, DuckDB, Athena, BigQuery | Spark, Databricks, limited outside |
| **Why Confluent chose Iceberg** | Multi-cloud: no vendor lock-in, works everywhere | Would tie Confluent to Databricks ecosystem |

### What to say if asked

> Delta Lake and Iceberg solve the same problem — ACID transactions and time travel on open file formats. The difference is ecosystem allegiance. Iceberg is truly cloud and engine agnostic; Delta Lake is technically open-source but most optimized for Databricks. For Confluent's multi-cloud Tableflow, Iceberg was the natural choice: a Tableflow customer on AWS uses Glue catalog + Athena; a customer on GCP uses BigQuery + Iceberg connector; a customer on Azure uses ADLS + Iceberg. One format, all clouds.

---

## Section 4 — Apache Flink

### What is it?

**Apache Flink** (a stateful stream processing framework — a system that processes event streams with operator state, time windows, and exactly-once semantics, often used for joins, aggregations, and transformations on Kafka data) is listed as "preferred" in the JD.

The key distinction from what you already know:

| | Kafka Streams | Apache Flink |
|---|---|---|
| **Runs as** | Library inside your JVM process | Separate cluster (Flink JobManager + TaskManagers) |
| **State storage** | RocksDB embedded in the consumer process | Distributed state backends (RocksDB, memory, S3 checkpoints) |
| **Scaling** | Scale by adding partitions and consumer instances | Scale by adding TaskManager slots (independent of Kafka partitions) |
| **SQL support** | No | Yes — Flink SQL is a full streaming SQL dialect |
| **Windows** | Supported but limited | First-class: tumbling, sliding, session windows |
| **Joins** | Stream-stream join (limited) | Stream-stream, stream-table, temporal joins |
| **Best for** | Lightweight stream enrichment inside a microservice | Complex streaming analytics: joins, windowed aggregations, multi-stream correlations |

### The Critical Use Case

Flink reads from Kafka topics, does stateful computation (e.g., "join the order event stream with the user profile stream to enrich each order with the user's city"), and writes results to another Kafka topic OR directly to an Iceberg table.

In the Tableflow context: Flink sits BETWEEN Kafka and Iceberg when the data needs transformation before landing. Tableflow (direct materialization) is the zero-transformation path; Flink is the transform-then-materialize path.

### Interview Q&A

**Q: "Would you use Kafka Streams or Apache Flink for real-time aggregations over Kafka data?"**

> For lightweight, per-record enrichment or stateless transformations, Kafka Streams is operationally simpler — it's a library, no cluster to manage, scales with Kafka partitions. For anything involving windowed aggregations, multi-stream joins, or complex stateful processing, Flink is the right choice. Flink's state backend (RocksDB with S3 checkpoints) handles terabytes of state and recovers exactly to the last checkpoint after failure. Kafka Streams state is local RocksDB only — recovery from failure means replaying the full topic, which is slow for large state. For the Tableflow team, Flink is the natural fit for pre-materialization transformations before data lands in Iceberg.

---

## Section 5 — Data Lakehouse

### What is it?

**Data Lakehouse** (a storage architecture that combines the low cost and scale of a data lake — raw files in S3 — with the ACID transactions, schema enforcement, and SQL performance of a data warehouse — structured, queryable, reliable) is the paradigm that Tableflow enables.

The traditional trade-off:
- **Data warehouse** (e.g., Snowflake, Redshift) — expensive, structured, fast queries, ACID, limited scale
- **Data lake** (e.g., S3 with raw Parquet) — cheap, unstructured, slow queries, no ACID, unlimited scale

Lakehouse = the best of both: data stored in cheap S3, but accessed via Iceberg's metadata layer that gives it warehouse properties (ACID, schema, time travel). Query engines (Spark, Flink, Athena) read Iceberg tables directly from S3 at warehouse-grade reliability.

**Tableflow's role:** Tableflow materializes Kafka topics → Iceberg tables → turns a streaming platform into a data lakehouse feeder. Engineers on the data team can run Spark SQL on Iceberg tables that Tableflow is continuously writing to in real time.

---

## Section 6 — Data Catalogs: AWS Glue and Unity Catalog

These are one-paragraph reads — enough to know what they are and answer one line in an interview.

**AWS Glue** (AWS's managed metadata catalog — a central registry that stores table schemas, locations, partitioning info, and format details for Iceberg tables in S3, so query engines like Athena and Spark know where to find data without hardcoding paths) is the catalog integration layer for Tableflow on AWS. When Tableflow creates an Iceberg table on S3, it registers the table's metadata (schema, S3 path, partition spec) in Glue so Athena and Spark can discover and query it without additional configuration.

**Unity Catalog** (Databricks' unified governance and catalog solution — same role as AWS Glue, but integrated into the Databricks ecosystem with added governance features like column-level security and lineage tracking) is the equivalent catalog for Tableflow on Databricks/Azure. If a customer is running Databricks, their Iceberg tables from Tableflow get registered here.

**What to say if asked:** "AWS Glue and Unity Catalog are metadata catalogs — they're the registry that tells query engines where Iceberg tables live in object storage. Tableflow registers newly created Iceberg tables in the customer's chosen catalog so their analytics tooling (Athena, Spark, Databricks) can discover and query the tables immediately."

---

## Section 7 — Quick Interview Reference Table

| Technology | One-sentence definition | Confluent/Tableflow connection | Skip depth? |
|---|---|---|---|
| Control Plane | Orchestrates table lifecycle, provisions data plane, monitors health | Half of your team's work | ❌ Know well |
| Data Plane | Continuously reads Kafka, writes Parquet to S3, commits Iceberg snapshots | The other half | ❌ Know well |
| Apache Iceberg | Open table format giving ACID + time travel + schema evolution on S3 | Tableflow's output format | Know conceptually |
| Apache Flink | Stateful stream processing cluster for windowed aggregations and stream joins on Kafka | Pre-materialization transforms before Iceberg | Know it exists and why |
| Delta Lake | Databricks-optimized table format; same features as Iceberg | Confluent chose Iceberg for multi-cloud openness | One paragraph |
| Data Lakehouse | Architecture combining cheap S3 storage with ACID warehouse properties via Iceberg | Tableflow enables the lakehouse pattern | One paragraph |
| AWS Glue | AWS metadata catalog — where Iceberg table schemas and S3 paths are registered | Tableflow registers Iceberg tables here on AWS | One sentence |
| Unity Catalog | Databricks metadata catalog — same as Glue but in Databricks ecosystem | Same as Glue, for Databricks/Azure customers | One sentence |
| Apache Spark | Batch + streaming analytics that READS Iceberg tables Tableflow writes | You're on the write side; Spark is on the read side | ⏭️ Skip |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 2026 | Briefing created. Covers Control/Data Plane, Apache Iceberg (with storage structure diagram), Delta Lake vs Iceberg, Apache Flink vs Kafka Streams, Data Lakehouse, AWS Glue, Unity Catalog. Spark explicitly skipped. |
