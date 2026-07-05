# Long-Running Tasks — Quick Reference

> **Read this:** 30 min before an interview involving video processing, report generation, or async job systems.
> **Deep study:** `DeepDive/06-long-running-tasks.md`

---

## 🎯 What kind of problem is this?

Use this pattern when: **a user request triggers work that takes longer than an HTTP timeout** — you can't block the caller inline for seconds or minutes.

Trigger words: "video transcode", "report generation", "batch processing", "ML inference", "bulk email", "image processing pipeline", "data export", "job takes minutes", "async processing".

---

## 🧭 Decision Sequence

```
START: Request requires > 5 seconds of processing

Step 1 → Return 202 Accepted + job_id immediately (always)
         Never make the caller wait inline for long jobs.
         Caller gets job_id in < 100ms regardless of job duration.

Step 2 → Choose the queue
         Low throughput, simple: Redis List (LPUSH/BRPOP) or Bull/Celery
         High throughput, fan-out, replay needed: Kafka
         AWS ecosystem: SQS

Step 3 → How does the caller get results?
         Browser / human user waiting → polling (GET /jobs/{id} every 5s)
                                     + SSE for progress if job > 1 min
         Backend service caller → webhook callback
         Both → support both (polling as fallback for failed webhooks)

Step 4 → How many workers?
         Auto-scale workers based on queue depth.
         Queue depth > N → spin up more workers.
         Queue depth = 0 → scale down to minimum.
         Use spot/preemptible instances for cost efficiency.

Step 5 → What if a worker crashes mid-job?
         Job must be re-queued and retried.
         Worker must be idempotent: re-running a partial job produces the same result.
         Use job_id as idempotency key on all side effects.

Step 6 → Job keeps failing after N retries?
         Dead Letter Queue (DLQ) — move failed job to DLQ for human inspection.
         Never silently discard. Alert ops on every DLQ arrival.

Step 7 → Mixed job types (fast thumbnails + 2-hour ML training)?
         Separate queues: HIGH (seconds) / NORMAL (minutes) / LOW (hours).
         Workers check HIGH first → NORMAL → LOW. Urgent jobs never wait
         behind a batch job.
```

---

## ⚡ Strategies at a Glance

| Strategy | Use when | Don't use when |
|---|---|---|
| **Async Job Queue** | Job > 5 seconds, multiple concurrent users, retriable | Result needed within the same HTTP request |
| **Progress Tracking** | Job > 1 min, user watching progress bar, has sub-steps | Single atomic op — no sub-steps to report |
| **Webhook Callback** | Caller is a backend service, fire-and-forget, polling impractical | Caller is a browser; caller is behind NAT/firewall |

**Key numbers to remember:**
- Always return 202 Accepted + job_id in < 100ms — never block inline
- SQS visibility timeout: job becomes visible again if not ACKed within N seconds
- Worker crash recovery: unACKed jobs re-appear in queue after visibility timeout
- Progress in Redis with 24h TTL: auto-expires after job completes
- Auto-scale trigger: queue depth (SQS `ApproximateNumberOfMessages`, Kafka consumer lag)
- Spot/preemptible instances: 70–90% cheaper; natural fit for fault-tolerant workers

---

## 🎨 Key Architecture Diagram

```
                         ┌─────────────────────────────────────┐
    Browser/Client ─────▶│      Job Submission API             │
    POST /jobs            │  1. Write job record (PENDING)      │
                          │  2. Enqueue to job queue            │
                          │  3. Return 202 + job_id             │
                          └──────────────┬──────────────────────┘
                                         │
                          ┌──────────────▼──────────────────────┐
                          │          Job Queue                   │
                          │   (Kafka / SQS / Redis)              │
                          │   Durable. Survives worker crashes.  │
                          └──────────────┬──────────────────────┘
                                         │ workers pull jobs
                    ┌────────────────────▼─────────────────────┐
                    │             Worker Pool                    │
                    │  Worker 1 [job:abc → transcoding 45%]     │
                    │  Worker 2 [job:xyz → report generating]   │
                    │  Worker 3 [idle]                          │
                    │                                           │
                    │  Auto-scales: queue depth ↑ → workers ↑  │
                    └─────────┬──────────────────┬─────────────┘
                              │                  │
               ┌──────────────▼──┐    ┌──────────▼────────────┐
               │  Status / Progress│   │  Result Storage        │
               │  (Redis)          │   │  (S3 for large output) │
               │  job:abc123       │   │  s3://bucket/abc123/   │
               │  {status:RUNNING, │   │  output.mp4            │
               │   progress:45}    │   └───────────────────────┘
               └──────────────────┘
                              ▲
    Client polls ─────────────┘  GET /jobs/abc123
    or receives webhook ◀──── POST /webhook (on completion)

KEY INVARIANT:
   Job submission path: fast (< 100ms). Always returns job_id.
   Job execution path: slow (seconds to hours). Fully async.
   These two paths never block each other.
   Worker crash = job re-queued. No work is permanently lost.
```

---

## 🔬 Interview Q&A

### Q: "User submits a video transcode. How do you ensure it completes even if the worker crashes?"

> The job must be re-queued and retried. Two requirements: (1) The queue must be durable — Kafka with replication, SQS with visibility timeout, or Redis with persistence. Worker reads the job but doesn't ACK/commit offset until processing is complete. If worker crashes, the job becomes visible again in the queue (after visibility timeout) and another worker picks it up. (2) The worker must be idempotent — re-running a partially completed transcode produces the same final output. Use job_id as a key on all outputs (S3 object key = job_id/output.mp4). If the output already exists in S3, skip re-upload. Two transcodes of the same job = same result, not a duplicate file.

---

### Q: "How do you scale the worker pool to handle traffic spikes?"

> Auto-scale workers based on queue depth — the canonical approach. Metric: approximate number of messages in the queue (SQS: `ApproximateNumberOfMessages`; Kafka: consumer group lag). Scaling rule: if queue depth > N × average_job_duration × target_workers, spin up more workers. If queue depth = 0 for M minutes, scale down to minimum. Use spot/preemptible instances for worker nodes — they're 70–90% cheaper and workers are naturally fault-tolerant (job re-queues on crash). For predictable traffic patterns (end-of-month reports), pre-scale before the spike rather than reacting to it.

---

### Q: "How do you tell the user their 10-minute video transcode is 45% done?"

> Three options: (1) Polling — client hits `GET /jobs/{id}/progress` every 5 seconds. Worker writes progress to Redis (`HSET job:abc123 progress 45`). Simple, works everywhere. (2) SSE — client opens `EventSource` to `/jobs/{id}/stream`. Server streams progress events from Redis as worker updates them. User sees live progress bar without polling overhead. (3) WebSocket — bidirectional, overkill for one-way progress updates; use SSE instead. Production recommendation: SSE for web, polling for mobile/API clients. Store progress in Redis with a 24h TTL — auto-expires after job completes or fails.

---

### Q: "What happens if your job queue is full? (backpressure)"

> Queue full = producer is faster than consumers. Options: (1) Reject new job submissions with 503 Service Unavailable + Retry-After header. Caller should back off and retry. (2) Shed low-priority jobs — if the queue has both "batch report" and "real-time thumbnail" jobs, deprioritize batch. (3) Scale out workers (if compute resources are available). (4) Persist overflow to a secondary slower queue (disk-backed). Key insight: a bounded queue is a feature, not a bug — it provides backpressure that prevents the whole system from falling over. An unbounded queue that grows forever will eventually OOM the queue server.

---

### Q: "How do you implement job prioritization? (urgent jobs skip the queue)"

> Separate queues per priority, not a single shared queue. Pattern: HIGH queue, NORMAL queue, LOW queue. Workers check HIGH first; if empty, check NORMAL; if empty, check LOW. Simple and effective. For more nuanced priority: (1) Redis ZADD with priority score — worker uses ZPOPMAX to always get the highest-priority job. (2) Multiple Kafka topics: `jobs.high`, `jobs.normal`, `jobs.low` — consumers subscribe to all three with different weights. Real-world example: YouTube's transcoding pipeline prioritizes new uploads (users waiting) over re-transcoding old videos at new resolutions (background maintenance).

---

### Q: "User submits the same expensive report twice. How do you avoid running it twice?"

> Deduplication via idempotency key. When user submits the job: (1) Compute a deterministic job fingerprint from the inputs (hash of report_type + date_range + user_id). (2) Check if a job with this fingerprint already exists in a PENDING or RUNNING state. If yes, return the existing job_id — don't enqueue again. (3) If the existing job is COMPLETED, return its result directly. This is the "exactly-once submission" guarantee. The deduplication window should match your business requirements — typically 5–60 minutes. After that window, a second submission with the same inputs creates a fresh job.

---

### Q: "How do you handle a job that's been running for 3 hours but should only take 10 minutes?"

> Job timeout with dead-letter queue. Configure a maximum job duration (e.g., 20 minutes). Worker sets a timeout on itself or the orchestration layer detects a job that's been RUNNING longer than max_duration. Actions: (1) Kill the stuck worker. (2) Move the job to a dead-letter queue (DLQ) — a separate queue for jobs that failed or timed out, for human inspection. (3) Update job status to TIMEOUT. (4) Notify the caller. The DLQ allows ops team to investigate root cause (infinite loop? external API hung?) and manually replay if appropriate. Never silently discard timed-out jobs.

---

### Q: "Design the job system for YouTube's video upload and transcode pipeline."

> (1) User uploads video to S3 directly via presigned URL — no server in the upload path. (2) S3 triggers an event to Kafka on upload complete. (3) Transcode job created in job DB (status: PENDING); Kafka message enqueued. (4) Transcode workers (GPU instances) pull from Kafka, transcode into 5 resolutions (360p, 480p, 720p, 1080p, 4K) in parallel. (5) Each resolution is an independent sub-job — partial availability is fine (video is watchable at 360p while 4K is still processing). (6) Progress tracked in Redis per resolution. (7) When any resolution completes, update video availability in DB. (8) When all resolutions complete, send notification via WebSocket to YouTube Studio tab. Worker auto-scaling: queue depth triggers GPU instance provisioning via cloud API.

---

## ⚠️ Anti-patterns (don't say these)

- **Polling the DB from workers to find new jobs** — 50 workers × 1 poll/sec = 50 DB queries/sec with lock contention on jobs table; use a real queue (Kafka, SQS, Redis BRPOP)
- **Storing large job results in the database** — transcoded videos, 100MB PDFs, 50MB CSVs don't belong in Postgres; store in S3, put only the URL in DB
- **Not handling duplicate job execution** — workers crash and re-queue; without idempotency keys, job runs twice: user charged twice, email sent twice

---

## 🧩 Common Interview Problems

| Problem | Key Design Choice | Notes |
|---|---|---|
| Design YouTube / Video Platform | Job queue + GPU worker pool + S3 | 5 resolution sub-jobs in parallel |
| Design Google Docs Export | Async job + polling + S3 download URL | "Export as PDF" for large docs |
| Design Data Export System (GDPR) | Job queue + large output to S3 + email link | At-least-once + idempotency |
| Design ML Training Platform | Job queue + GPU cluster + progress tracking | Hours-long jobs, priority queue |
| Design Bulk Email Sender | Job queue + worker pool + rate limiting | 10M emails, rate-limit per ESP |
| Design Report Generator | Async job + S3 result + webhook callback | Monthly financial report |

---

## 🔗 Full notes

`DeepDive/06-long-running-tasks.md` — queue selection, worker crash recovery, backpressure, full failure mode Q&A
