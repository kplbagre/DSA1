# Pattern Deep Dive: Long-Running Tasks

> **Read this when:** You need to understand how to handle single expensive computations — video transcoding, report generation, ML inference, PDF rendering — that take seconds to hours, without blocking the caller or losing progress.
> **Pre-interview refresh:** Use `Reference/06-long-running-tasks.md` (5 min).

---

## 🎯 What Problem Does This Pattern Solve?

A user requests an operation that takes too long to complete synchronously. If you run it inline in the HTTP request, the caller times out (most HTTP clients timeout at 30–60 seconds). If the job takes 10 minutes, the user is stuck watching a spinner.

Classic long-running tasks:
- **Video transcoding:** User uploads a 2GB video → convert to 5 resolutions. Takes 5–20 minutes.
- **Report generation:** "Generate monthly financial report" → query 500M rows, aggregate, export PDF. Takes 2–10 minutes.
- **ML inference:** Batch scoring 1M items. Takes hours.
- **Email bulk send:** Send 10M emails. Takes hours.
- **Image processing:** Resize, watermark, thumbnail 10,000 images from a zip upload.
- **Data export:** "Export my last 5 years of data" — large DB scan + file generation.

The fundamental problem: **HTTP is synchronous by design**. The request-response model has no built-in way to say "I'll get back to you in 20 minutes."

---

## 💡 Core Insight

**Decouple job submission from job execution.** The caller submits a job and gets a job ID immediately (202 Accepted). The job runs asynchronously in the background. The caller uses the job ID to check status or receive a push notification when done.

The architecture has three distinct pieces:
1. **Job queue** — durable buffer between submission and execution (Kafka, SQS, Redis queue)
2. **Worker pool** — processes jobs from the queue (auto-scalable, replaceable)
3. **Status store** — tracks job state and progress (DB or Redis)

> **KEY INSIGHT:** "Return a job ID immediately. Do the work asynchronously. Let the caller poll or receive a callback when done. Never make them wait inline."

---

## 🗂️ The 3 Strategies

---

### Strategy 1 — Async Job Queue (Standard Pattern)

🧠 **Mental model:** GitHub Actions — you push code, get a run ID immediately. Job queues behind other builds. A runner picks it up, executes. You poll the status page (or receive a webhook) when done. You never waited inline.

Submit job → queue → worker picks up → processes → result stored → caller retrieves.

**When to use:**
- Job takes > 5 seconds (longer than a reasonable HTTP timeout)
- Multiple concurrent jobs from many users
- Job can fail and be retried without business impact
- Exact completion time is unpredictable

**When NOT to use:**
- Result needed within the same HTTP request (< 2 seconds) — just do it synchronously
- Job must run exactly once with no retry (use idempotency keys if retrying is a concern)

**How it works:**

**Steps in plain English:**
1. **Submit** — Caller POSTs job request. Server creates a job record (status: PENDING) and enqueues job_id to the queue. Returns 202 Accepted with job_id.
2. **Queue** — Job message sits in durable queue (Kafka/SQS). Survives server crashes.
3. **Pick up** — Worker pulls job from queue, updates status to RUNNING.
4. **Process** — Worker performs the expensive computation. Writes progress updates periodically.
5. **Complete** — Worker stores result (in S3, DB, or cache), updates status to COMPLETED.
6. **Retrieve** — Caller polls `GET /jobs/{job_id}` or receives webhook callback. Fetches result.

```
                    ┌─────────────────┐
     Caller ───────▶│  Job API        │──▶ job_id=abc123, status=PENDING
     POST /transcode │  (sync return)  │
                    └────────┬────────┘
                             │ enqueue
                    ┌────────▼────────┐
                    │  Job Queue      │  (Kafka / SQS / Redis)
                    │  [job:abc123]   │  durable, survives crashes
                    └────────┬────────┘
                             │ dequeue
                    ┌────────▼────────┐
                    │  Worker Pool    │  (auto-scales with queue depth)
                    │  Worker 1  ●   │
                    │  Worker 2  ●   │
                    │  Worker 3      │
                    └────────┬────────┘
                             │ write result
                    ┌────────▼────────┐
                    │  Storage        │  S3 (large output) or DB (metadata)
                    │  + Status DB    │  status: COMPLETED, result_url: s3://...
                    └─────────────────┘
                             ▲
     Caller ────────────────┘  GET /jobs/abc123 → {status: COMPLETED, url: ...}
     (polls or receives webhook)
```

---

### Strategy 2 — Progress Tracking

🧠 **Mental model:** YouTube "Processing: 45% — transcoding 1080p" — the upload is done; the transcode worker is running. Redis holds the current percentage; UI polls every 3 seconds. Worker and UI are completely decoupled.

For long jobs where users need visibility into progress (not just "done/not done"), stream incremental progress updates.

**When to use:**
- Job duration > 1 minute (users need reassurance the job is running)
- Job has identifiable sub-steps (video: downloading → transcoding → uploading → done)
- User is watching a progress bar in the UI

**When NOT to use:**
- Job is a single atomic operation — no meaningful sub-steps to report (just PENDING → DONE)
- Caller only needs the final result, not intermediate state (adds complexity with no UX benefit)
- Job runs fully inside one DB transaction — commit = done, there's no intermediate state to surface

**How it works:**

**Steps in plain English:**
1. **Worker updates** — As worker completes each sub-step, it writes progress to Redis: `HSET job:abc123 progress 45 step "transcoding"`.
2. **Caller polls or streams** — Caller hits `GET /jobs/abc123/progress` periodically (polling) or connects via SSE for pushed updates.
3. **TTL cleanup** — Progress keys have TTL (e.g., 24h). Auto-expire after job completes.

```
           Worker                Redis                    Client
              │                    │                        │
              │ HSET job:abc       │                        │
              │ {progress:10,      │                        │
              │  step:"download"}─▶│                        │
              │                    │                        │
              │ HSET job:abc       │   GET /jobs/abc/progress
              │ {progress:45,      │◀───────────────────────│
              │  step:"transcode"}▶│                        │
              │                    │──{progress:45,         │
              │                    │   step:"transcoding",  │
              │                    │   eta_seconds:180}────▶│
              │ HSET job:abc       │                        │
              │ {progress:100,     │   GET /jobs/abc/progress
              │  step:"done"}─────▶│◀───────────────────────│
              │                    │──{progress:100,        │
              │                    │   done, result_url}───▶│

KEY INVARIANT:
   Worker writes to Redis independently of client polling frequency.
   Client polls at any cadence without slowing or blocking the worker.
```

---

### Strategy 3 — Webhook Callback (Push on Completion)

🧠 **Mental model:** Stripe payment confirmation — you submit a charge, get a charge ID. When the bank confirms (seconds to minutes later), Stripe POSTs to your webhook URL. You never poll Stripe; Stripe calls you.

Instead of the caller polling, register a callback URL. Server calls the callback when the job completes.

**When to use:**
- Caller is a backend service (not a browser) — it can receive HTTP calls
- Polling is expensive or impractical (caller would poll for hours)
- Fire-and-forget from caller's perspective (submit and move on)
- Third-party integrations (Stripe webhooks, GitHub webhooks — this is the same pattern)

**When NOT to use:**
- Caller is a browser (can't receive incoming HTTP requests)
- Caller is behind NAT/firewall (server can't reach callback URL)
- Reliability of webhook delivery matters a lot (webhooks can fail — need retry + signature verification)

**How it works:**

**Steps in plain English:**
1. **Register** — Caller submits job with `callback_url: "https://my-service.com/webhook/job-done"`.
2. **Job runs** — Worker processes job asynchronously.
3. **Callback** — On completion, server POSTs result to callback URL.
4. **Retry** — If callback fails (caller is down), server retries with exponential backoff (3 retries over 24h).
5. **Signature** — Server signs the webhook payload (HMAC-SHA256) so caller can verify it's legitimate.

```
Caller Service                    Job Service              Worker
     │                                │                       │
     │──POST /transcode               │                       │
     │  {callback_url: "https://..."}─▶│                       │
     │◀──202 Accepted, job_id=abc123──│                       │
     │                                │──enqueue──────────────▶│
     │  (caller moves on)             │                        │ (processing)
     │                                │                        │ (5 minutes later)
     │◀──POST /webhook/job-done───────│◀───────────────────────│
     │  {job_id: abc123,              │  (server calls back)   │
     │   status: COMPLETED,           │                        │
     │   result_url: "s3://..."}      │                        │
     │──200 OK────────────────────────▶│                       │
```

---

## 🧭 Decision Sequence

```
START: Request requires > 5 seconds of processing

Step 1 ── Return 202 Accepted + job_id immediately (always)
          Never make the caller wait inline for long jobs.
          Caller gets job_id in < 100ms regardless of job duration.

Step 2 ── Choose the queue
          Low throughput, simple: Redis List (LPUSH/BRPOP) or Bull/Celery
          High throughput, fan-out, replay needed: Kafka
          AWS ecosystem: SQS

Step 3 ── How does the caller get results?
          Browser / human user waiting → polling (GET /jobs/{id} every 5s)
                                      + SSE for progress if job > 1 min
          Backend service caller → webhook callback
          Both → support both (polling as fallback for failed webhooks)

Step 4 ── How many workers?
          Auto-scale workers based on queue depth.
          Queue depth > N → spin up more workers.
          Queue depth = 0 → scale down to minimum.
          Use spot/preemptible instances for cost efficiency.

Step 5 ── What if a worker crashes mid-job?
          Job must be re-queued and retried.
          Worker must be idempotent: re-running a partial job produces the same result.
          Use job_id as idempotency key on all side effects.

Step 6 ── What if a job keeps failing after N retries?
          Dead Letter Queue (DLQ) — after max_retries (e.g., 5), move job to DLQ.
          DLQ = separate queue for human inspection and manual replay.
          Alert ops on every DLQ arrival. Never silently discard failed jobs.

Step 7 ── Mixed job types in the same queue?
          Separate queues by priority and duration:
          HIGH:   user-facing, fast (thumbnail gen, ~2 sec)
          NORMAL: background, medium (report gen, ~2 min)
          LOW:    batch, long (ML training, ~2 hours)
          Workers check HIGH → NORMAL → LOW. Urgent jobs never wait behind
          a 2-hour batch job.
```

---

## 🎨 Visual — Full Long-Running Task Architecture

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

## ⚠️ Anti-patterns

- **Polling the DB from workers to find new jobs.** If 50 workers all `SELECT ... WHERE status='PENDING' LIMIT 1 FOR UPDATE` every second, that's 50 DB queries/sec generating lock contention on the jobs table. The DB becomes the bottleneck. Use a proper queue (Kafka, SQS, Redis) where workers receive work via push (Kafka consumer group) or efficient blocking pop (Redis BRPOP) — no polling needed.

- **Storing large job results in the database.** A transcoded video, a 100MB PDF report, or a 50MB CSV export should never be stored as a BLOB in Postgres. The DB is not a file store. Store large outputs in S3 (or equivalent object storage). Store only the S3 URL + metadata in the DB. Retrieval: return a presigned S3 URL to the caller — they download directly from S3, not through your API server.

- **Not handling duplicate job execution.** Workers crash and jobs get re-queued. Without idempotency, the job runs twice: the user gets charged twice, the email sends twice, the report is generated twice and confuses the user. Every worker action must be idempotent. Use the job_id as the idempotency key for all side effects. Check before acting: "has this job already produced this output?" If yes, skip the action and return success.

---

## 🗺️ Problems Map

| Interview Problem | Why Long-Running Tasks Applies | Key Design Choice |
|---|---|---|
| Design YouTube / Video Platform | Upload → transcode → multiple resolutions | Job queue + GPU worker pool + S3 |
| Design Google Docs Export | "Export as PDF" for large documents | Async job + polling + S3 download URL |
| Design Data Export System | "Export all my data" (GDPR) | Job queue + large output to S3 + email link |
| Design ML Training Platform | Train model on dataset (hours) | Job queue + GPU cluster + progress tracking |
| Design Bulk Email Sender | Send 10M emails for a campaign | Job queue + worker pool + rate limiting |
| Design Report Generator | Monthly financial report (DB scan + PDF) | Async job + S3 result + webhook callback |
| Design Image Processing Service | Resize/watermark batch of 10K images | Parallel job queue + fan-out workers |

---

## 🔗 Concept Notes — Go Here for "How It Works"

- **Message queues** (Kafka, SQS, Redis queues — choosing and configuring) → `../../Core-Architecture/Service-Communication/19-message-queues-kafka-rabbitmq.md`
- **Job scheduling at scale** (cron jobs, distributed schedulers) → `../../Production-Grade/System-Design-Patterns/47-job-scheduling-at-scale.md`
- **Blob / object storage** (S3 presigned URLs, multipart upload) → `../../Foundations/Data-Fundamentals/14-document-blob-storage.md`
- **Real-time progress updates** (SSE for progress streaming) → `07-real-time-updates.md`
- **Idempotency** (safe retries on re-queued jobs) → `../../Foundations/Concurrency-and-Consistency/04-idempotency.md`

---

## 🔄 Changelog

| Date | Change |
|---|---|
| July 2026 | Note created. Batch 3 of 8 remaining patterns. |
| July 2026 | Added 🧠 mental model anchors per strategy. Added DLQ (Step 6) and mixed workload queues (Step 7) to decision sequence. |
