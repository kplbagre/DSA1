# 47 — Job Scheduling at Scale

## 📖 What is Job Scheduling at Scale?

**Full form:** Distributed Job Scheduling — executing pre-defined tasks (jobs) at scheduled times or intervals, across a cluster of worker nodes, with exactly-once execution guarantees even when nodes fail.

**Simple analogy:** A hospital's operating room scheduler. Surgeries are booked in advance. If a surgeon calls in sick (worker dies), the hospital doesn't cancel — another surgeon takes over. The same surgery is never double-booked (exactly-once). And if the scheduler itself goes down on Saturday night, every pending surgery is still executed when it comes back up.

**Core principle:** Single-machine cron fails when the machine dies or restarts. Distributed scheduling separates the clock/trigger layer from the execution layer — a persistent job store holds all scheduled jobs with their next-run time, and workers compete (via DB-level locking or leader election) to claim and execute each job exactly once.

**Why it matters:** Every production backend has scheduled tasks: payment settlement at EOD, invoice generation on the 1st of the month, cache warming, data reconciliation. When these fail silently (missed job due to server restart during deploy), business impact is severe.

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|---|---|---|
| Job Store | The persistent database table (e.g., PostgreSQL `scheduled_jobs`) that holds all jobs with their status, next run time, and claim state | `scheduled_jobs (id, job_type, status, next_run_at, claimed_by, claim_expires_at)` |
| CAS Claim | Compare-and-swap claim: `UPDATE jobs SET status='CLAIMED', claimed_by=? WHERE id=? AND status='PENDING'` — atomically "wins" a job for exactly one worker | 100 workers race; only the one that gets 1 row affected owns the job |
| Heartbeat | A periodic update (`claim_expires_at = NOW() + 5 min` every 60s) that proves a worker is still alive and extends its claim TTL | Without heartbeat renewal, the watchdog reclaims the job after TTL expiry |
| Claim Expiry (TTL) | The `claim_expires_at` timestamp — if a worker dies, its claim expires and the watchdog resets the job to PENDING | `claim_expires_at = NOW() + INTERVAL '5 minutes'` set at claim time |
| Exactly-Once Semantics | The guarantee that each job runs exactly one time — achieved via CAS claim + idempotency key in the handler | A payment settlement job that runs twice would double-settle; CAS + idempotency prevents this |
| SKIP LOCKED | PostgreSQL/MySQL query hint that returns only unlocked rows and skips rows already locked — gives each worker a non-overlapping batch | `SELECT id FROM jobs WHERE status='PENDING' LIMIT 10 FOR UPDATE SKIP LOCKED` |
| Clock Skew | Different machines having slightly different system clocks — critical to use DB server's `NOW()` for all time comparisons, never the worker's local clock | Worker A's clock is 2s ahead of Worker B's; use `NOW()` from DB to avoid false expiry |
| Quartz/Temporal | Battle-tested job scheduler libraries: Quartz (Java, DB-backed, clustered) and Temporal (durable workflow engine for complex long-running jobs) | Use Quartz for up to ~10K jobs/min; use Temporal when jobs are multi-step sagas |

---

## 🎯 Why This Matters

**The problem:** OS-level cron (`@Scheduled` in Spring or Unix crontab) runs on one machine. That machine restarts during deployment → the 23:59 payment settlement job never runs. No one notices until 3 AM when the finance team calls.

**Interview relevance:** This topic comes up whenever a design has scheduled work — expense report reminders, coupon expiry, notification digests, retry queues with delay, fraud detection batch jobs. Any time you say "and a nightly job will…" in an interview, the follow-up is "what happens if the server restarts at 11:58 PM?"

**Senior expectation:** Name the distributed scheduler pattern (DB-backed job store with CAS claim), exactly-once semantics (idempotency key + claim ownership), heartbeat-based dead job detection, delayed jobs via sorted queue, and the Quartz / Temporal alternatives with their trade-offs.

**Business stakes:** A missed payment settlement job at Razorpay can mean millions of dollars in unreconciled transactions. A missed coupon expiry job means customers redeem coupons past their validity window. These are not theoretical — they happen whenever engineers use single-machine scheduling in a multi-pod deployment.

---

## 🧠 The Mental Model

Think of a post office with 10 delivery trucks. Letters arrive with a "deliver by" date. Each morning, the dispatch manager looks at all undispatched letters whose "deliver by" date is today, and assigns each to a truck driver. If a driver doesn't check in after 1 hour (heartbeat timeout), their letters are reassigned to another driver. No letter gets delivered twice because the dispatch log is updated atomically — once a letter is marked "assigned to Driver 7", Driver 8 cannot claim it.

Now translate each part to a distributed job scheduler:

- The dispatch manager = the job scheduler (cron trigger / timer thread that scans for due jobs)
- The dispatch log = the `jobs` table in PostgreSQL (the single source of truth for all job states)
- The "deliver by" date = the `next_run_at` column (epoch timestamp when the job is due to execute)
- "Assigned to Driver 7" = the `claimed_by` column + `claim_expires_at` (the heartbeat expiry timestamp)
- No double-delivery = optimistic locking on the claim step (`UPDATE ... WHERE claimed_by IS NULL AND next_run_at <= NOW()`)

The key insight is: **The database row is the distributed lock.** CAS (compare-and-swap — an atomic operation that reads a value and writes a new one only if the value hasn't changed) on `claimed_by IS NULL` means only one worker can claim each job, regardless of how many workers are running simultaneously. No external lock server, no ZooKeeper, no Redis lock — just a well-placed `WHERE` clause in an `UPDATE` statement.

Three core problems arise in distributed scheduling, and each has a specific solution:

1. **Double execution:** Two workers start the same job simultaneously → CAS-based claim prevents this. The `UPDATE` is atomic at the DB level; only one transaction sees `1 row affected`.
2. **Missed jobs:** Worker claims a job but dies mid-execution → `claim_expires_at` acts as a TTL (time-to-live); another worker reclaims the job after the TTL expires, ensuring the job eventually runs.
3. **Clock skew:** Workers run on different machines with different system times → always use the DB server's `NOW()` for all time comparisons, never the worker's local `System.currentTimeMillis()`. The DB is the single clock.

---

## 🎨 Visual — System Topology & Component Flow

### Diagram 1 — Full System Topology

```
┌─────────────────────────────────────────────────────────────────────┐
│                     Client Tier                                     │
│          (API Server / Admin Dashboard / Application Code)          │
│                                                                     │
│   POST /jobs  { jobType: "INVOICE_GENERATE", runAt: "2026-07-01" }  │
└──────────────────────────────┬──────────────────────────────────────┘
                               │ INSERT INTO scheduled_jobs
                               ▼
┌─────────────────────────────────────────────────────────────────────┐
│                  Scheduler DB (PostgreSQL)                          │
│                                                                     │
│  scheduled_jobs table:                                              │
│  id | job_type | status  | next_run_at | claimed_by | claim_exp_at │
│  1  | INVOICE  | PENDING | 2026-07-01  | NULL       | NULL         │
│  2  | SETTLE   | CLAIMED | 2026-06-26  | worker-3   | 23:59+5min   │
│  3  | RECONCIL | DONE    | 2026-06-25  | worker-1   | completed    │
└──────────────┬───────────────────────────────────────────────────────┘
               │
               │  SELECT id FROM scheduled_jobs
               │  WHERE status='PENDING' AND next_run_at <= NOW()
               │  FOR UPDATE SKIP LOCKED   ← each worker gets a unique batch
               │
       ┌───────┴────────────────────────────────────┐
       │          Worker Pool (N pods)               │
       │                                             │
       │  ┌──────────┐  ┌──────────┐  ┌──────────┐  │
       │  │ Worker-1 │  │ Worker-2 │  │ Worker-3 │  │
       │  │ polls DB │  │ polls DB │  │ polls DB │  │
       │  │ every 5s │  │ every 5s │  │ every 5s │  │
       │  └────┬─────┘  └────┬─────┘  └────┬─────┘  │
       │       │             │             │        │
       │       │ CAS claim   │ CAS claim   │ CAS claim
       │       │ (only 1     │ fails →     │ succeeds
       │       │ succeeds)   │ skip        │        │
       └───────┼─────────────┼─────────────┼────────┘
               │             │             │
               ▼             ▼             ▼
       ┌────────────────────────────────────────────┐
       │         External Services / Business DB    │
       │  (send email, charge card, write invoice)  │
       └──────────────────────┬─────────────────────┘
                              │ UPDATE scheduled_jobs
                              │ SET status='DONE'
                              ▼
                    Scheduler DB (PostgreSQL)

NOTE: Multiple workers poll simultaneously.
      SELECT...FOR UPDATE SKIP LOCKED (a PostgreSQL primitive that
      acquires row-level locks and skips already-locked rows)
      ensures each row goes to exactly one worker batch.
      The subsequent CAS UPDATE is the final atomic guard.
```

### Diagram 2 — Job Lifecycle State Machine

```
                        ┌─────────────────────────────┐
                        │         JOB LIFECYCLE        │
                        └─────────────────────────────┘

                              ┌─────────┐
                              │ PENDING │  ← Initial state on INSERT
                              └────┬────┘     or after watchdog recovery
                                   │
                    Worker executes claim query:
                    UPDATE ... WHERE status='PENDING'   ← CAS operation
                    (atomic — only ONE worker gets 1 row affected)
                                   │
                        ┌──────────▼──────────┐
                        │       CLAIMED       │  claimed_by=worker-id
                        │                     │  claim_expires_at=NOW()+5m
                        └──────────┬──────────┘
                                   │
                  ┌────────────────┼─────────────────────┐
                  │                │                     │
                  │       Worker begins execution        │
                  │                │                     │
                  │       ┌────────▼────────┐            │
                  │       │     RUNNING     │            │
                  │       │  (heartbeat     │            │
                  │       │   renews every  │            │
                  │       │   60 seconds)   │            │
                  │       └────────┬────────┘            │
                  │                │                     │
                  │     ┌──────────┼───────────┐         │
                  │     │          │           │         │
              heartbeat │    success      exception      │
              expired   │          │           │         │
              (worker   │   ┌──────▼──┐   retry count   │
              died)     │   │  DONE   │   < max?        │
                  │     │   └─────────┘        │         │
                  │     │               ┌──────▼──────┐  │
                  │     │               │RETRY_PENDING│  │
                  │     │               │ next_run_at │  │
                  │     │               │ = NOW()+exp │  │
                  │     │               │  backoff    │  │
                  │     │               └──────┬──────┘  │
                  │     │                      │         │
                  │     │               retry count      │
                  │     │               >= max           │
                  │     │                      │         │
                  │     │               ┌──────▼──────┐  │
                  │     │               │   FAILED    │  │
                  │     │               │ (dead-letter│  │
                  │     │               │  / alert)   │  │
                  │     │               └─────────────┘  │
                  │     │                                │
         Watchdog resets│              claim_expires_at  │
         to PENDING ◄───┘              < NOW() detected  │
                  │                                     │
                  └─────────────────────────────────────┘
                  Watchdog resets CLAIMED → PENDING
                  (orphaned job recovery)

KEY INVARIANT:
  PENDING → CLAIMED is a single atomic CAS UPDATE.
  This is the distributed mutex — only one worker succeeds.
  All others see 0 rows affected and skip the job.
  No external lock manager needed.

RETRY_PENDING → PENDING transition:
  When retry backoff expires, next_run_at becomes eligible again
  and the job re-enters the polling window as PENDING.
```

---

## ⚙️ How It Actually Works

### 4a — Job Table Schema and Claim Logic

The entire distributed locking mechanism lives in the claim query. There is no external coordination service — just the atomicity guarantee of a single SQL `UPDATE` statement.

**Step-by-step claim flow:**

1. Workers poll for eligible jobs using `SELECT...FOR UPDATE SKIP LOCKED` (a PostgreSQL/MySQL query that acquires row-level locks on selected rows AND skips any rows already locked by other transactions — purpose-built for work queues):
   `SELECT id FROM jobs WHERE status = 'PENDING' AND next_run_at <= NOW() LIMIT 10 FOR UPDATE SKIP LOCKED`
2. For each candidate job ID, attempt the CAS claim:
   `UPDATE jobs SET status = 'CLAIMED', claimed_by = ?, claim_expires_at = NOW() + INTERVAL '5 minutes' WHERE id = ? AND status = 'PENDING'`
3. If `UPDATE` returns 1 row affected: this worker owns the job. Begin execution.
4. If `UPDATE` returns 0 rows affected: another worker claimed it first. Skip and move to the next candidate.

```java
// Jobs table schema — logical representation of the scheduled_jobs DB table
public class ScheduledJob {
    private Long id;
    private String jobType;
    private String payload;
    // Possible values: PENDING, CLAIMED, RUNNING, DONE, FAILED, RETRY_PENDING
    private JobStatus status;
    // Epoch timestamp — when this job should next execute (DB server time)
    private Instant nextRunAt;
    // Worker pod ID (hostname + UUID) that currently holds the claim
    private String claimedBy;
    // Heartbeat expiry — if NOW() > claimExpiresAt, this job is orphaned and can be reclaimed
    private Instant claimExpiresAt;
    private int retryCount;
    private int maxRetries;
    private Instant lastExecutedAt;
    private String lastError;
}

// Worker claim logic — this UPDATE is the distributed mutex
// CAS (compare-and-swap): atomically reads claimed_by=NULL and writes the new owner
public boolean claimJob(Long jobId, String workerId) {
    int rowsUpdated = jdbcTemplate.update(
        // The WHERE clause is the CAS condition: status must still be PENDING
        // and next_run_at must be in the past (due time reached)
        "UPDATE scheduled_jobs " +
        "SET status = 'CLAIMED', claimed_by = ?, claim_expires_at = NOW() + INTERVAL '5 minutes' " +
        "WHERE id = ? AND status = 'PENDING' AND next_run_at <= NOW()",
        workerId,
        jobId
    );
    // Exactly one worker across the entire cluster gets rowsUpdated = 1
    // All other workers racing for this job get rowsUpdated = 0 and skip it
    return rowsUpdated == 1;
}

// Worker poll loop — runs on a fixed schedule in every worker pod
public void pollAndExecuteJobs() {
    // SELECT...FOR UPDATE SKIP LOCKED: workers get non-overlapping batches
    // Each worker sees only rows NOT currently locked by another worker's transaction
    List<Long> candidateIds = jdbcTemplate.queryForList(
        "SELECT id FROM scheduled_jobs " +
        "WHERE status = 'PENDING' AND next_run_at <= NOW() " +
        "LIMIT 10 FOR UPDATE SKIP LOCKED",
        Long.class
    );

    for (Long jobId : candidateIds) {
        boolean claimed = claimJob(jobId, this.workerId);
        if (claimed) {
            // This worker owns the job — proceed with execution
            executeJob(jobId);
        }
        // If not claimed, another worker beat us to it — silently skip
    }
}

// Execute a claimed job and update status based on outcome
private void executeJob(Long jobId) {
    ScheduledJob job = loadJob(jobId);
    try {
        // Mark as RUNNING to differentiate from merely claimed
        updateJobStatus(jobId, JobStatus.RUNNING);
        // Dispatch to the appropriate handler based on job type
        JobHandler handler = handlerRegistry.getHandler(job.getJobType());
        handler.execute(job.getPayload());
        // Job completed successfully — record completion time
        updateJobStatusDone(jobId);
    } catch (Exception e) {
        handleJobFailure(jobId, job, e);
    }
}

// Handle failure — route to RETRY_PENDING or FAILED based on retry budget
private void handleJobFailure(Long jobId, ScheduledJob job, Exception e) {
    if (job.getRetryCount() < job.getMaxRetries()) {
        // Exponential backoff: 2^retryCount minutes before next attempt
        long backoffMinutes = (long) Math.pow(2, job.getRetryCount());
        jdbcTemplate.update(
            "UPDATE scheduled_jobs " +
            "SET status = 'RETRY_PENDING', retry_count = retry_count + 1, " +
            "next_run_at = NOW() + (? || ' minutes')::INTERVAL, last_error = ? " +
            "WHERE id = ?",
            backoffMinutes,
            e.getMessage(),
            jobId
        );
    } else {
        // Retry budget exhausted — move to terminal FAILED state and alert
        jdbcTemplate.update(
            "UPDATE scheduled_jobs SET status = 'FAILED', last_error = ? WHERE id = ?",
            e.getMessage(),
            jobId
        );
    }
}
```

### 4b — Heartbeat and Dead Job Recovery

A claimed job that never updates its `claim_expires_at` is treated as orphaned after the TTL expires. The heartbeat (a periodic ping that extends the claim TTL, proving the worker is still alive) is the mechanism that keeps long-running jobs alive and exposes dead workers.

**Heartbeat flow:**
1. Every 60 seconds, each running worker extends `claim_expires_at = NOW() + 5 minutes` for all jobs it currently owns.
2. A watchdog thread (running on every worker pod) scans for orphaned jobs: `SELECT id FROM jobs WHERE status = 'CLAIMED' AND claim_expires_at < NOW()`.
3. For each orphaned job: reset to PENDING so another worker can claim it on the next poll cycle.

```java
// Heartbeat — runs every 60 seconds in each worker pod
// Extends claim_expires_at to signal "I am still alive and processing"
@Scheduled(fixedDelay = 60_000)
public void renewHeartbeat() {
    Set<Long> runningJobIds = getLocallyRunningJobIds();
    if (runningJobIds.isEmpty()) {
        return;
    }
    int renewedCount = jdbcTemplate.update(
        // Only renew heartbeat for jobs this specific worker owns
        // claimed_by = ? prevents worker-A from accidentally renewing worker-B's jobs
        "UPDATE scheduled_jobs " +
        "SET claim_expires_at = NOW() + INTERVAL '5 minutes' " +
        "WHERE id IN (?) AND claimed_by = ?",
        runningJobIds,
        workerId
    );
    log.debug("Renewed heartbeat for {} jobs on worker {}", renewedCount, workerId);
}

// Watchdog — runs every 120 seconds on every worker pod
// Detects jobs whose claim_expires_at passed without a heartbeat renewal
@Scheduled(fixedDelay = 120_000)
public void recoverOrphanedJobs() {
    // claim_expires_at < NOW() means the heartbeat stopped — worker is presumed dead
    // Reset to PENDING so the polling loop can reclaim them
    int recovered = jdbcTemplate.update(
        "UPDATE scheduled_jobs " +
        "SET status = 'PENDING', claimed_by = NULL, claim_expires_at = NULL " +
        "WHERE status = 'CLAIMED' AND claim_expires_at < NOW()"
    );
    if (recovered > 0) {
        // Alert: this is abnormal — a worker died mid-job
        log.warn("Recovered {} orphaned jobs from dead workers", recovered);
        metricsRegistry.counter("scheduler.orphaned_jobs_recovered").increment(recovered);
    }
}
```

**Why the heartbeat interval must be well below the claim TTL:**
If `claim_expires_at` TTL is 5 minutes and heartbeat fires every 60 seconds, a worker has 5 missed heartbeat cycles before the watchdog reclaims the job. If the TTL were only 90 seconds with a 60-second heartbeat, a single GC pause could cause a false expiry and double execution. Rule of thumb: TTL should be at least 5× the heartbeat interval.

### 4c — Delayed Jobs with Redis Sorted Set

For one-time jobs triggered by events ("send this invoice 24 hours from now", "expire this session token in 30 minutes"), a Redis sorted set (a data structure where each member has a numeric score, automatically sorted by score) provides an efficient delay queue without polling the main job table.

**Delayed job flow:**
1. On event (e.g., order placed): `ZADD delayed_jobs {epoch_of_execution} {job_id}` — stores the job with its due time as the sort score.
2. A scheduler process polls every second: `ZRANGEBYSCORE delayed_jobs 0 {now_epoch} LIMIT 10` — returns jobs whose due time has passed.
3. For each due job: attempt `ZREM delayed_jobs {job_id}` — the atomic remove returns 1 to exactly one pod, which then executes. All other pods that also polled the same job get 0 and skip.

```java
// Schedule a delayed one-time job — called at event time (e.g., order placement)
public void scheduleDelayedJob(String jobId, String payload, Duration delay) {
    // Score = epoch milliseconds of when this job should execute
    double executionScore = Instant.now().plus(delay).toEpochMilli();
    // ZADD delayed_jobs {score} {jobId:payload}
    // If a job with this key already exists, ZADD updates its score (idempotent reschedule)
    redisTemplate.opsForZSet().add("delayed_jobs", jobId + ":" + payload, executionScore);
}

// Poll the delay queue — runs every 1 second on each scheduler pod
@Scheduled(fixedDelay = 1_000)
public void pollDelayedJobs() {
    double nowEpochMs = Instant.now().toEpochMilli();
    // Fetch all jobs with score <= now (due time reached or passed)
    Set<ZSetOperations.TypedTuple<String>> dueJobs =
        redisTemplate.opsForZSet().rangeByScoreWithScores("delayed_jobs", 0, nowEpochMs);

    for (ZSetOperations.TypedTuple<String> job : dueJobs) {
        // ZREM is atomic — returns 1 if this pod removed it, 0 if another pod already did
        // This is the distributed claim for delayed jobs — mirrors the DB CAS for recurring jobs
        Long removed = redisTemplate.opsForZSet().remove("delayed_jobs", job.getValue());
        if (removed != null && removed == 1) {
            // Only the pod that successfully removed the entry executes the job
            // All other pods racing for this job see removed=0 and skip it
            executionQueue.add(job.getValue());
        }
    }
}

// Consume from the local execution queue and run the job logic
public void executeDelayedJob(String jobEntry) {
    // Parse jobId and payload from the entry string
    String[] parts = jobEntry.split(":", 2);
    String jobId = parts[0];
    String payload = parts[1];
    JobHandler handler = handlerRegistry.getHandlerForPayload(payload);
    handler.execute(payload);
}
```

**What is `SELECT ... FOR UPDATE SKIP LOCKED`, and why does it fit here?**

`SELECT ... FOR UPDATE SKIP LOCKED` is a PostgreSQL/MySQL query hint that acquires row-level locks on selected rows AND skips any rows already locked by other transactions. This is purpose-built for work queues: each worker gets a unique batch of rows to process, with no row going to two workers simultaneously. Without `SKIP LOCKED`, workers would block waiting for locks, creating contention. With it, each worker instantly receives a non-overlapping batch.

In an interview: "SKIP LOCKED is the PostgreSQL primitive that implements a distributed work queue inside the database — you don't need a separate queue system like RabbitMQ for moderate job volumes (up to ~10K jobs/minute). Above that threshold, purpose-built systems like Temporal or Kafka become necessary."

---

## 🏢 Real World — Where Companies Use This

- **Razorpay (payment settlements):** EOD settlement jobs must run exactly once per merchant per day. A distributed scheduler ensures one job fires at midnight IST even during rolling deployments. Dead worker recovery ensures no settlement is missed — a missed settlement means money stuck in limbo, which triggers regulatory scrutiny. The CAS claim also provides an audit trail: every settlement job row shows which worker ran it, when it started, and when it completed.

- **Netflix (video encoding):** After a video is uploaded, encoding jobs are scheduled: transcode to 1080p, 720p, 480p in parallel. Jobs are distributed across encoding worker pools running on thousands of nodes. Failure recovery retries with the same encoding parameters (idempotent — re-encoding the same source file produces the same output). Netflix uses a custom priority queue on top of the scheduler so 4K HDR encoding (higher priority) preempts SD encodes when workers are constrained.

- **Swiggy (coupon expiry):** Every active coupon has an expiry timestamp. A scheduled job at 23:59 scans expiring coupons and marks them invalid. Job idempotency is trivial here: the same coupon marked invalid twice is a no-op (`UPDATE WHERE status='ACTIVE'` returns 0 rows on the second run). The distributed scheduler ensures this job fires exactly once even if Swiggy is doing a deployment at 23:55.

- **Amazon (email notifications):** Order confirmation emails, delivery reminders, review requests — all scheduled events triggered N hours after a customer action. Amazon SES + delayed job queue (backed by SQS with visibility timeout — SQS's version of `claim_expires_at`) ensures delivery exactly once. A dead-letter queue catches failed email jobs for retry, preventing silent drops. At Amazon scale, the scheduler routes to regional worker pools to respect SES per-region send limits.

- **Flipkart (inventory reconciliation):** Nightly job reconciles warehouse inventory counts against transaction logs from the day. Job ownership via DB CAS prevents two warehouses' reconciliation jobs from running simultaneously against shared inventory tables, which would cause phantom read conflicts. The job result (reconciliation delta report) is idempotent — running it twice with the same day's transactions produces the same delta.

---

## 🧭 When to Use vs When NOT to Use

| Use this when | Do NOT use when |
|---|---|
| Jobs must survive server restarts and deployments | Job is a simple `@Scheduled` on one machine that is always up and a restart is acceptable |
| Exactly-once execution is a business requirement (payments, settlements, invoicing) | Job side effects are naturally idempotent anyway — running twice is harmless (e.g., recomputing a cached value) |
| Job failure must be detected and retried automatically | Volume is so high (>1M jobs/min) that DB polling is a bottleneck — use Kafka or Temporal instead |
| You have delayed jobs ("execute N hours from now") | Trigger is user-driven / real-time (use a request queue or direct API call, not a scheduler) |
| You need an audit trail of every job execution with status and duration | You only have one pod deployed and zero HA requirements |

**The common mistake:** Using Spring `@Scheduled` with `fixedDelay` in a multi-pod deployment. All pods fire the job simultaneously → every pod executes the same job → duplicate processing. A payment settled 3 times, a coupon expired 3 times, 3 copies of the same email sent. Always add DB-level claim ownership before using `@Scheduled` across multiple pods.

**The second common mistake:** Setting `claim_expires_at` too short. A batch job that processes 100K rows might take 8 minutes. If `claim_expires_at = NOW() + 5 minutes`, the watchdog reclaims it mid-execution → double execution. Either set TTL to `NOW() + 30 minutes` for known long-running jobs, or make the heartbeat aggressive (every 30 seconds) with a longer TTL.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Exactly-once execution across a cluster; automatic dead-worker recovery via heartbeat (a periodic ping that extends the claim TTL); full audit trail (job history table shows every execution with worker ID, start time, end time, status, and error message); delayed job support without a separate external queue system |
| **You lose** | DB polling adds load — 100 workers polling every 5 seconds = 20 queries/second on the jobs table (mitigate with a compound index on `(status, next_run_at)` and increasing the poll interval); job store is now a critical dependency — if the DB is down, no jobs fire at all; leader election for a centralized scheduler component adds operational complexity; SKIP LOCKED behavior varies between DB engines (PostgreSQL vs MySQL have subtle differences) |
| **Failure mode** | Long-running jobs that exceed `claim_expires_at` get reclaimed by the watchdog and executed a second time by another worker — causing double execution. Mitigation: set heartbeat interval to 20% of claim TTL; make all jobs idempotent by design; use per-job-type TTL configuration so a 30-minute batch job gets a 45-minute TTL while a 30-second notification job gets a 5-minute TTL |
| **Scaling ceiling** | DB-based polling scales to ~10K workers with proper indexing. Beyond that, the `FOR UPDATE` contention on the jobs table becomes a bottleneck. At that scale, transition to a purpose-built system: Temporal (durable workflow engine), Apache Airflow (DAG-based batch scheduler), or a Kafka-based delayed message queue |

---

## 🔬 Interview Q&As

**Q1 (Tier 1):** "How do you prevent two workers from executing the same scheduled job simultaneously?"

> Use a DB-level CAS (compare-and-swap — an atomic operation that succeeds only if a precondition holds) claim step. Workers first use `SELECT...FOR UPDATE SKIP LOCKED` to obtain non-overlapping candidate batches. Then, for each candidate, the worker immediately attempts: `UPDATE jobs SET status='CLAIMED', claimed_by=? WHERE id=? AND status='PENDING'`. PostgreSQL executes this atomically — only one transaction's `UPDATE` returns 1 row affected. The worker that gets 0 rows affected moves on. The `SKIP LOCKED` hint prevents workers from blocking on each other's locks, giving each worker a unique batch to process. No external coordination service is needed — the DB row is the lock.

---

**Q2 (Tier 1):** "What happens if a worker claims a job but dies mid-execution?"

> The `claim_expires_at` column acts as a heartbeat TTL (time-to-live for the claim). While the job is running, the worker periodically updates `claim_expires_at = NOW() + 5 minutes` every 60 seconds — this is the heartbeat. A watchdog thread (running on every worker pod) scans for jobs where `claim_expires_at < NOW()` and resets them to `PENDING`. When the dead worker's heartbeat stops, the watchdog recovers the job within at most one heartbeat cycle (5 minutes in this example). For correctness, all jobs must be idempotent — the recovery worker re-runs from scratch, not from mid-execution state. The combination of heartbeat + watchdog + idempotency ensures the job eventually completes exactly once from the business logic perspective.

---

**Q3 (Tier 1):** "How do you implement a job that should run '2 hours after the user places an order'?"

> Use a Redis sorted set as a delay queue. On order placement, call `ZADD delayed_jobs {now + 2 hours epoch} {job_id}`. A scheduler pod polls `ZRANGEBYSCORE delayed_jobs 0 {now_epoch}` every second. For each due job, attempt `ZREM delayed_jobs {job_id}` — `ZREM` is atomic in Redis, and only the pod that gets a return value of `1` executes the job. All other pods racing for the same job get `0` and skip it. This gives exactly-once delayed execution via Redis atomic remove. For durability, if Redis is unavailable, fall back to a `next_run_at`-based DB poll with the scheduled time pre-written to the jobs table at order creation.

---

**Q4 (Tier 2 — cross/probe):** "Your job scheduler system has 100 workers polling the jobs table every 1 second. How does this affect database performance at scale?"

> At 100 workers polling every second, that is 100 queries/second on the jobs table — manageable if the table is properly indexed. A compound index on `(status, next_run_at)` means the DB scans only eligible PENDING rows rather than the full table. `FOR UPDATE SKIP LOCKED` ensures workers don't block each other, converting lock waits to instant skips. Further mitigations: increase the poll interval to 5-10 seconds for non-time-critical jobs (most job types tolerate a 10-second delay); partition the jobs table by `job_type` so different job categories use different storage segments; or add a push layer — when a new job is scheduled, publish to a Redis pub-sub channel, waking idle workers immediately instead of waiting for their next poll cycle. At very high scale (>10K workers), the `FOR UPDATE` lock acquisition on the shared jobs table becomes a serialization bottleneck even with SKIP LOCKED. At that point, transition to Temporal (which uses its own durable execution engine) or Apache Kafka with delayed message delivery. Pure DB-based polling is sufficient for most companies up to the scale of tens of millions of jobs per day.

---

**Q5 (Tier 2 — cross/probe):** "How does job scheduling interact with database transactions? If a job does a DB write and then crashes, does the scheduler retry it? Won't that cause a duplicate write?"

> Yes — this is precisely the idempotency requirement for all scheduled jobs. A job that is retried must produce the same observable outcome as if it ran exactly once. The standard pattern uses an idempotency record to enforce this. At the very start of the job handler, before any business logic, write: `INSERT INTO job_executions (job_id, execution_id) VALUES (?, ?) ON CONFLICT DO NOTHING` — with a unique constraint on `(job_id, execution_id)`. The `execution_id` is a stable deterministic ID derived from the job ID and retry count. If this INSERT returns 0 rows affected (conflict), the handler detects a duplicate execution and returns early without running the business logic. If it returns 1, proceed normally. The actual business logic DB write is wrapped in the same transaction as the idempotency record insert — both commit together or both rollback together. This two-phase approach (idempotency check + business write in one transaction) guarantees that a retried job either completes its business logic or detects it already ran, never executing the write twice. For external side effects (HTTP calls, email sends), wrap them in a check-before-act pattern: `SELECT status FROM invoices WHERE order_id=?` before issuing the external call.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> "Distributed job scheduling prevents missed and double-executed cron jobs by persisting all jobs in a DB table and using CAS (`UPDATE...WHERE status='PENDING'`) as the distributed mutex — every job is claimed by exactly one worker, and heartbeat expiry auto-recovers jobs from dead workers."

---

## 🔗 Related Concepts

- `Foundations/Concurrency-and-Consistency/04-idempotency.md` — all retried jobs must be idempotent; the idempotency record pattern is the standard solution
- `Core-Architecture/Distributed-Systems/21-leader-election-consensus.md` — alternative pattern: elect one leader node as the sole scheduler, eliminating the need for CAS claims (but adding leader election complexity)
- `Core-Architecture/Resilience-and-Fault-Tolerance/35-retry-exponential-backoff-patterns.md` — retry logic for failed job executions; the RETRY_PENDING state uses exponential backoff
- `Production-Grade/System-Design-Patterns/49-state-machines-workflows.md` — the job lifecycle (PENDING → CLAIMED → RUNNING → DONE) is itself a state machine; understanding state machines clarifies why each transition must be atomic

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **Quartz Scheduler Documentation** — Quartz Project | Production-grade Java scheduler; shows how JDBC job store implements exactly-once execution with DB locking — adds implementation depth on clustered Quartz setup, misfires (missed fires), and calendar-based schedules | ~20 min read |
| **"Reliable task scheduling at scale" — Netflix TechBlog** | Netflix's approach to encoding job scheduling, failure detection, and priority queues across thousands of worker pods; covers how they handle partial failures in encoding pipelines and job preemption | ~15 min read |
| **Temporal.io — "Why use Temporal"** | When DB-based scheduling is insufficient — durable workflows, long-running sagas, retry semantics at the platform level; explains the difference between a job scheduler and a workflow orchestrator | ~10 min read |
