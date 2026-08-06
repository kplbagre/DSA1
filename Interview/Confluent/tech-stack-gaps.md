# Confluent Tech Stack — Gap Tracker

> **Purpose:** Every technology in the Confluent JD that is NOT (or only partially) covered in `SystemDesignConcepts/`. Tracks action, depth, location, and status.
>
> **Rule:** Before the interview, every row in this table should be ✅ Done or ⏭️ Skipped.
>
> **Based on:** JD analysis from `job-description.md` + gap analysis vs `SystemDesignConcepts/INDEX.md`.

---

## Gap Table

| Technology | What it is (one line) | Coverage in Notes | Action | Depth | Where the note lives | Status |
|---|---|---|---|---|---|---|
| **Apache Kafka — Internals** | Distributed append-only log. Core product you're joining. | #19 covers fundamentals (partitions, offsets, consumer groups, acks). **Log compaction, throughput architecture, retention-as-TTL, topic design = NOT covered.** | ✅ Create note | Deep (full concept note) | `SystemDesignConcepts/Core-Architecture/Service-Communication/60-kafka-internals.md` | ✅ Done |
| **Control Plane / Data Plane** | Architectural split: control plane orchestrates (brain), data plane moves data (muscle). | Not in INDEX. | ✅ Create briefing section | Medium (1 page in briefing) | `Interview/Confluent/TechStack/tableflow-tech-briefing.md` | ✅ Done |
| **Apache Iceberg** | Open table format for data lakes — ACID + time travel on top of Parquet files in S3. | Not in INDEX. | ✅ Create briefing section | Light (half page in briefing) | `Interview/Confluent/TechStack/tableflow-tech-briefing.md` | ✅ Done |
| **Apache Flink** | Stateful stream processing framework. Sits on top of Kafka for joins, aggregations, windowed ops. | Not in INDEX. | ✅ Create briefing section | Light (half page in briefing) | `Interview/Confluent/TechStack/tableflow-tech-briefing.md` | ✅ Done |
| **Delta Lake** | Databricks' equivalent of Iceberg. ACID + time travel. Confluent chose Iceberg for openness. | Not in INDEX. | ✅ Create briefing section | Brief (1 paragraph in briefing) | `Interview/Confluent/TechStack/tableflow-tech-briefing.md` | ✅ Done |
| **Data Lakehouse** | Storage paradigm: data lake (cheap S3) + data warehouse (ACID, SQL). Iceberg enables this. | Not in INDEX. | ✅ Create briefing section | Brief (1 paragraph in briefing) | `Interview/Confluent/TechStack/tableflow-tech-briefing.md` | ✅ Done |
| **AWS Glue** | AWS data catalog — metadata registry for Iceberg tables (schema, location, partitioning). | Not in INDEX. | ✅ Create briefing section | One sentence in briefing | `Interview/Confluent/TechStack/tableflow-tech-briefing.md` | ✅ Done |
| **Unity Catalog** | Databricks' unified governance solution. Same role as AWS Glue but Databricks-ecosystem. | Not in INDEX. | ✅ Create briefing section | One sentence in briefing | `Interview/Confluent/TechStack/tableflow-tech-briefing.md` | ✅ Done |
| **Apache Spark** | Batch + stream processing. Data engineers use it to READ Iceberg tables Tableflow writes. | Not in INDEX. | ⏭️ Skip | None — JD says "preferred, not required." You build the writer, not the reader. | — | ⏭️ Skipped |

---

## INDEX Registration Note

`59-nosql-cassandra-mongo-dynamo.md` exists on disk at `SystemDesignConcepts/Core-Architecture/Database-Core/` but is **NOT in INDEX.md**. `60-kafka-internals.md` was registered as the next note.

When registering #60 in `INDEX.md`, also check if #59 needs to be formally registered.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 2026 | Tracker created. 8 gaps identified from JD analysis. 1 skipped (Spark). 1 deep note created (#60 Kafka Internals). 1 briefing file created (tableflow-tech-briefing.md) covering 6 shallow reads. |
