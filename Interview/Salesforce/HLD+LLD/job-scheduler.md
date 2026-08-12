# Job Scheduler — HLD + LLD Combined Round

---

## 0.  Identity

| | |
|---|---|
| **Problem** | Distributed Job Scheduler — schedule jobs (one-shot + recurring/cron), dispatch them to workers at the right time, exactly-once-ish execution |
| **Format** | HLD+LLD combined (Salesforce SMTS), 90 min confirmed |
| **Time budget** | 35 min LLD -> 45 min HLD -> 10 min buffer |
| **Frequency rank** | **#2 pick** in `questions-by-frequency.md` for a combined round. Strongest cross-file agreement: #2 HLD in the archived file, and independently confirmed in fresh research with an explicit quote (CodingKaro, Apr 2025, LMTS): *"Topic: Job Scheduler System. Covered both LLD + HLD aspects. Focus was on write LLD code for job creation, persistence, dispatching, and execution flow."* Two adjacent Jun 2025 variants exist: "Cron-like Job Scheduling System" and "Cron Job Parser." |
| **Salesforce-specific angle** | Maps to **Scheduled Apex / Apex Flex Queue** — Salesforce runs tenant-scheduled jobs on shared infrastructure with per-org governor limits (a real Salesforce org is capped at 100 scheduled Apex jobs). Multi-tenant fairness is the natural extension. |

**The exact ask, per the sourced prompt:** job **creation**, **persistence**, **dispatching**, **execution flow**. Structure the LLD half around those four verbs — they are the grading rubric.

---

## 1.  Dual-Layer Map

> Build this before writing either half. Every HLD box owns exactly one interface + N implementations.

| HLD Box (system view) | LLD Class(es) (code view) | The interface that makes it swappable |
|---|---|---|
| Scheduler API / job intake | `SchedulerService`, `Job`, `JobBuilder` | `JobRepository` (persist anywhere) |
| Schedule evaluation ("when does this run next?") | `Schedule` + `OneShotSchedule` / `CronSchedule` / `FixedRateSchedule` | **`Schedule`** — the core Strategy |
| Due-job poller / time wheel | `JobPoller`, `DueJobQuery` | `JobRepository.findDue(now, limit)` |
| Dispatch / queueing | `JobDispatcher` | `JobQueue` (in-memory today, SQS/Kafka in prod) |
| Worker execution | `WorkerNode`, `JobExecutor`, `JobHandler` | **`JobHandler`** — per-job-type business logic |
| Lease / ownership (no double-run) | `JobLease`, `LeaseManager` | `LeaseStore` (Redis or DB row lock) |
| Retry + failure policy | `RetryPolicy`, `ExponentialBackoff` | **`RetryPolicy`** — Strategy |
| Job state tracking | `JobStatus`, `JobRun` | — (state machine, see 2.5) |

**The zoom sentence to say out loud:** *"`JobHandler` implementations are classes on one node in LLD; in HLD they're a fleet of stateless worker pods pulling from a queue. `LeaseManager` is a Redis `SETNX` call in LLD; in HLD it's the thing that prevents two schedulers in different AZs from firing the same job twice."*

---

## 2.  LLD Half (target: 35 min)

### 2.1  Problem Statement

Design a job scheduler that lets callers register jobs to run at a future time or on a recurring schedule (cron), persists them durably, dispatches them to workers when due, executes them exactly once despite crashes and multiple scheduler instances, and retries failures with backoff.

### 2.2  Requirements

**Functional:**
- Schedule a **one-shot** job ("run at 3:00 PM today")
- Schedule a **recurring** job via cron expression ("0 */2 * * *")
- Cancel / pause / resume a scheduled job
- Execute the job's business logic via a pluggable handler
- Retry failed jobs with backoff; give up after N attempts (-> dead letter)
- Query job status and execution history

**Non-Functional:**
- **No double execution** — two scheduler instances must not both fire the same job
- **Durable** — a scheduler crash must not lose scheduled jobs
- **Extensible** — new schedule type or new job type = one new class, no edits elsewhere
- **Thread-safe** — a worker pool executes many jobs concurrently
- Late execution is acceptable (seconds); **duplicate execution is not**

**Explicitly out of scope (say this):** distributed workflow DAGs / job dependencies (that's Airflow, different problem), and sub-second real-time scheduling.

### 2.3  Class Design

#### 2.3.1  Deriving the classes (say this out loud, minutes 2-6)

Derive from the four verbs in the prompt — creation, persistence, dispatching, execution — plus the nouns each one drags in. **The justification is the scored part, not the class name.**

| # | Requirement / verb | Noun or variation point extracted | Becomes | Why it earns its own type (and what breaks if you inline it) |
|---|---|---|---|---|
| 1 | "Schedule a job" (**creation**) | noun: *job* | **`Job`** (entity) | Needs identity so it can be cancelled, leased, retried, and audited. Without an ID-bearing entity there's nothing to attach a lease or a retry count to. |
| 2 | "at a future time" **vs** "on a cron schedule" | the *timing rule* varies | **`Schedule`** (interface) + `OneShotSchedule`, `CronSchedule`, `FixedRateSchedule` | **This is the primary variation point of the whole problem.** Two very different rules answer one question: `nextRunAfter(Instant)`. Inlining as `if (job.isCron()) {...} else {...}` puts a growing conditional in the hot path and means adding "run on the last business day of the month" edits the scheduler core. Interface first. |
| 3 | "persists them durably" (**persistence**) | verb: *store / find due* | **`JobRepository`** (interface) | Persistence is a different reason to change than scheduling logic (SRP). Also the seam that lets the due-query become a DB index scan now and a partitioned scan later without touching the poller. |
| 4 | "dispatches them when due" (**dispatching**) | verb: *find due*, *hand off* | **`JobPoller`** + **`JobDispatcher`** | Two distinct jobs, deliberately not one class: the poller answers *"what's due?"* (a query concern, tuned by batch size and interval), the dispatcher answers *"who runs it and how does it get there?"* (a transport concern, in-memory vs SQS). Merging them means swapping the queue forces you to retest the polling logic. |
| 5 | "execute the job's business logic" (**execution**) | the *work itself* varies per job type | **`JobHandler`** (interface) | Second major variation point. `SendEmailHandler`, `GenerateReportHandler` — the scheduler must never know what a job *does*. Without this interface the scheduler imports every business module in the company: a dependency-direction disaster. |
| 6 | "no double execution" (NFR) | noun: *ownership claim* | **`JobLease`** + **`LeaseManager`** | The claim needs to be a first-class thing with an owner and an expiry — a boolean `isRunning` flag can't survive the owner crashing (it stays `true` forever). Modeling it as a lease with a TTL makes crash-recovery automatic. |
| 7 | "retry with backoff, give up after N" | the *backoff rule* varies | **`RetryPolicy`** (interface) + `ExponentialBackoff`, `FixedDelay`, `NoRetry` | Third variation point. Different job classes want different policies — a payment retry is not an email retry. Hardcoding `sleep(2^n)` means one global policy for the whole system. |
| 8 | "query job status / history" | *lifecycle* of one execution | **`JobStatus`** (enum) + **`JobRun`** (entity) | `JobRun` is separate from `Job` because a recurring job has **one definition and many executions** — collapsing them means you can't answer "did last night's run fail?" without destroying the schedule record. This is the single most common modeling mistake on this problem. |

**The one-liner after the table:** *"So the design has three variation points — when it runs (`Schedule`), what it does (`JobHandler`), and how it retries (`RetryPolicy`) — and everything else is plumbing around those three interfaces."*

#### 2.3.2  Entity fields

```
Job                                  <- the DEFINITION (one per schedule)
  - jobId:        String
  - jobType:      String             <- key into the JobHandler registry
  - payload:      Map<String,Object>
  - schedule:     Schedule           <- Strategy
  - retryPolicy:  RetryPolicy        <- Strategy
  - nextRunAt:    Instant            <- indexed; the poller queries on this
  - status:       JobStatus
  - maxAttempts:  int
  - createdAt:    Instant

JobRun                               <- ONE EXECUTION (many per Job)
  - runId:        String
  - jobId:        String
  - attempt:      int
  - startedAt:    Instant
  - finishedAt:   Instant
  - status:       JobStatus
  - errorMessage: String
  - workerId:     String

JobLease
  - jobId:      String
  - ownerId:    String               <- which scheduler/worker holds it
  - expiresAt:  Instant              <- TTL: crash recovery without a human

JobStatus (enum): SCHEDULED, QUEUED, RUNNING, SUCCEEDED, FAILED, RETRYING, CANCELLED, DEAD
```

#### 2.3.3  Relationships — with the composition-vs-aggregation call made explicit

Same rule as always — the distinction is **lifecycle ownership**, not syntax. *"If I `new` it inside the constructor, it's composition. If it arrives through the constructor, it's aggregation."*

| Relationship | Type | Composition or aggregation — and why that one |
|---|---|---|
| `CronSchedule` — `Schedule` | **IS-A** (implements) | Realization, not ownership. Liskov: `JobPoller` calls `nextRunAfter()` without knowing which schedule kind it holds. |
| `Job` — `Schedule` | **HAS-A** -> **composition** | The schedule is created with the job, dies with it, and is shared with nothing else. Delete the job, the schedule is meaningless. Filled diamond. |
| `Job` — `RetryPolicy` | **HAS-A** -> **aggregation** | Deliberately different from `Schedule`, and worth saying so: retry policies are typically **shared singletons** (`ExponentialBackoff(base=2s)` is reused by thousands of jobs). Same-shaped field, different ownership answer, because one is per-job data and the other is shared behavior. |
| `Job` — `JobRun` | **HAS-MANY** -> **aggregation** (1:N) | Runs are queried and archived independently of the job definition; a `JobRun` row outlives a deleted `Job` for audit. If it were composition, deleting the job would cascade away your execution history — exactly what you don't want. |
| `JobExecutor` — `JobHandler` | **USES** (dependency, resolved per job) | Not HAS-A: the executor looks the handler up by `jobType` per execution and forgets it. It holds no state on any handler. |
| `WorkerNode` — `JobExecutor` | **HAS-A** -> **composition** | The executor is created by and lives inside the worker process; nothing else references that instance. |
| `LeaseManager` — `LeaseStore` | **USES** (injected) | Collaborator. Swapping Redis for a DB row-lock changes only DI wiring. |

#### 2.3.4  ASCII class diagram — interfaces before implementations, always

```
                        SchedulerService
                        - repository:  JobRepository
                        - poller:      JobPoller
                        - dispatcher:  JobDispatcher
                        + schedule(Job): String
                        + cancel(jobId): void
                                |  uses
        +-----------------------+------------------------+
        v                       v                        v
  <<interface>>          <<interface>>            <<interface>>
  JobRepository          JobQueue                 LeaseStore
  + save(Job)            + push(Job)              + tryAcquire(jobId,
  + findDue(now,limit)   + poll(): Job                owner, ttl): bool
  + update(Job)                                   + release(jobId)
        ^                       ^                        ^
        | implements            | implements             | implements
  PostgresJobRepo         SqsJobQueue              RedisLeaseStore
                          InMemoryJobQueue         DbRowLockLeaseStore

                              Job
                              - schedule:    Schedule      <>--- composition
                              - retryPolicy: RetryPolicy   <>--- aggregation
                              - nextRunAt:   Instant
                                   |
                 +-----------------+------------------+
                 v                                    v
          <<interface>>                        <<interface>>
          Schedule                             RetryPolicy
          + nextRunAfter(Instant): Instant     + nextDelay(attempt): Duration
          + isRecurring(): boolean             + shouldRetry(attempt): boolean
                 ^                                    ^
                 | implements                         | implements
      +----------+-----------+              +---------+---------+
      |          |           |              |         |         |
 OneShot     Cron        FixedRate     Exponential  Fixed     NoRetry
 Schedule    Schedule    Schedule      Backoff      Delay

                          WorkerNode
                          - executor: JobExecutor
                                |
                                v
                          JobExecutor
                          - handlers: Map<String, JobHandler>
                          - leaseManager: LeaseManager
                          + execute(Job): JobRun
                                | resolves by jobType
                                v
                          <<interface>>
                          JobHandler
                          + getType(): String
                          + handle(payload): void
                                ^
                                | implements
                 +--------------+---------------+
                 |              |               |
          SendEmailHandler  ReportHandler   CleanupHandler
```

**Key invariant:** every box that fans out to multiple behaviors is an interface first — `Schedule`, `RetryPolicy`, `JobHandler`, `JobQueue`, `LeaseStore` are all `<<interface>>` before any concrete class exists.

#### 2.3.5  Follow-ups they will ask after this section — and your answers

| Their question | Your answer (one breath) |
|---|---|
| "Your `Job` holds a `Schedule` object, but the DB stores a string. How does that round-trip?" | "A `ScheduleFactory` reconstitutes it on load — `(schedule_type, schedule_expr, timezone)` maps to the right `Schedule` implementation. That's deliberate: I persist the *data* (`CRON`, `0 */2 * * *`) rather than a serialized object, so I can add a new schedule type without a data migration and without Java-serialization coupling in the database. Same pattern for `RetryPolicy`, which is looked up by name from a registry of shared singletons." |
| "Why is `JobRun` separate from `Job`?" | "A recurring job is one definition with many executions. If I collapse them, the third run overwrites the second run's outcome and I lose execution history — and I can't answer 'did last night's run fail?' without destroying the schedule. One-to-many, and the runs outlive the definition for audit." |
| "Composition or aggregation between `Job` and `Schedule`?" | "Composition — created with the job, dies with it, shared with nothing. Note `RetryPolicy` on the same class is the opposite: aggregation, because backoff policies are shared singletons across thousands of jobs. Same field shape, different ownership." |
| "Why not just a `boolean isRunning` flag instead of a lease?" | "Because it can't survive the owner crashing — the flag stays `true` forever and the job is wedged until a human clears it. A lease has an owner *and* a TTL, so crash recovery is automatic: the lease expires and another node picks it up." |
| "Why does `Schedule` return `nextRunAfter(now)` instead of storing a list of run times?" | "Cron schedules are effectively infinite — I can't materialize every future run. Computing the next one lazily keeps storage O(1) per job instead of unbounded, and rescheduling after each run is a single field update." |
| "Do you need both `JobPoller` and `JobDispatcher`?" | "They answer different questions — 'what's due?' is a query concern tuned by batch size and poll interval; 'how does it reach a worker?' is a transport concern that swaps between in-memory and SQS. Keeping them separate means changing the queue doesn't force retesting the polling logic. Small classes, and the split maps 1:1 to two different HLD boxes." |
| "How does a worker know how to run an arbitrary job?" | "It doesn't, by design. It looks up a `JobHandler` by `jobType` in a registry populated via DI. The scheduler never imports business logic — otherwise the scheduler depends on every team's code and can't be deployed independently." |
| "What if the payload schema changes?" | "Payload is an opaque map to the scheduler and the handler owns its schema. I'd version it with a `payloadVersion` field so a handler can support old in-flight jobs during a rollout — jobs scheduled a week ago still execute against today's code." |

### 2.4  Key Interfaces

```java
/**
 * THE core abstraction. Answers exactly one question: given a point in time,
 * when does this job next run? Adding "last business day of month" = one new class.
 */
public interface Schedule {
    Instant nextRunAfter(Instant from);
    boolean isRecurring();
}
```

```java
/**
 * Business logic plug-in point. The scheduler NEVER imports these implementations —
 * they register themselves into the executor's map via DI. Dependency Inversion.
 */
public interface JobHandler {
    String getType();
    void handle(Map<String, Object> payload) throws Exception;
}
```

```java
/** Failure policy as a strategy — a payment retry is not an email retry. */
public interface RetryPolicy {
    boolean shouldRetry(int attempt);
    Duration nextDelay(int attempt);
}
```

```java
/**
 * findDue() is the hot path — this is the method that gets an index in HLD
 * and becomes a partitioned/sharded query at scale.
 */
public interface JobRepository {
    void save(Job job);
    List<Job> findDue(Instant now, int limit);
    void update(Job job);
    Optional<Job> findById(String jobId);
}
```

```java
/** Atomic claim with a TTL. The TTL is what makes crash recovery automatic. */
public interface LeaseStore {
    boolean tryAcquire(String jobId, String ownerId, Duration ttl);
    void release(String jobId, String ownerId);
    boolean renew(String jobId, String ownerId, Duration ttl);
}
```

```java
/**
 * Persistence <-> object round-trip. The DB stores DATA (type + expression),
 * not a serialized object - so adding a schedule type needs no data migration
 * and the schema never couples to Java serialization.
 */
public class ScheduleFactory {
    public static Schedule from(String type, String expr, String timezone) {
        return switch (type) {
            case "ONESHOT"    -> new OneShotSchedule(Instant.parse(expr));
            case "CRON"       -> new CronSchedule(expr, ZoneId.of(timezone));
            case "FIXED_RATE" -> new FixedRateSchedule(Duration.parse(expr));
            default -> throw new IllegalArgumentException("Unknown schedule type: " + type);
        };
    }
}
```

**Say this when you write it:** *"This is the one place a `switch` is acceptable — it's a factory at the persistence boundary, turning data into types. The alternative, serializing the `Schedule` object into the row, welds my database to my Java class layout and turns a refactor into a migration."*

### 2.5  Design Decisions

**The question you must be ready for: "Isn't `JobStatus` a State pattern?"**

Here — unlike Notification Service, where I argued a plain enum was right — the answer is **genuinely closer to yes, and I'd still say no for this scope**. The lifecycle is real (`SCHEDULED -> QUEUED -> RUNNING -> SUCCEEDED | FAILED -> RETRYING -> DEAD`) and transitions have rules (you cannot go `SUCCEEDED -> RUNNING`; `CANCELLED` is terminal). That's the State pattern's trigger condition. Why I'd still use an enum plus a guarded transition method: the behavior that differs per state lives in the **executor**, not on the status object, and a State class per status would be 8 classes whose only job is to validate transitions. **The honest framing to say out loud:** *"I'd model it as an enum with a `canTransitionTo()` guard now, and I'd promote it to a proper State machine the moment transitions start carrying side effects — like emitting a webhook on entry to FAILED."* Naming your own upgrade trigger is what scores here.

**Why not a `Timer` / `ScheduledExecutorService` and be done?** Because `ScheduledExecutorService` holds schedules **in JVM heap**. Process restart = every scheduled job silently gone, violating the durability NFR. It also can't coordinate across instances — run three replicas and every job fires three times. It's the right answer only for a single-node, non-durable scheduler, and saying *why* it fails here beats never mentioning it.

**Why polling a `nextRunAt` index rather than a `DelayQueue` / timer wheel?** A timer wheel is O(1) and elegant, but it lives in memory and re-populating it after restart means scanning the DB anyway. Polling a `WHERE next_run_at <= now()` index is boring, survives restarts for free, and is trivially horizontally scalable with `SKIP LOCKED`. **Trade-off:** poll interval sets the floor on scheduling latency (a 1s poll = up to 1s late), which the NFR explicitly allows.

| Decision | Pattern Chosen | Strongest Alternative Considered | Why the alternative loses |
|---|---|---|---|
| `Schedule` interface with per-type impls | **Strategy** | `if (isCron) ... else ...` inside the poller | The conditional sits in the hot path and grows with every new schedule type; adding "last business day of month" edits the scheduler core rather than adding a class |
| `JobHandler` registry resolved by `jobType` | **Strategy + Registry** | `switch (jobType)` inside the executor | Forces the scheduler module to import and compile against every business module in the company — the dependency direction is backwards and independent deploys die |
| Lease with owner + TTL | **Lease / lock with expiry** | `boolean isRunning` column | Cannot survive owner crash — flag stays `true` forever, job wedged until manual intervention. TTL gives automatic recovery |
| `JobRun` separate from `Job` | **Entity split (definition vs execution)** | One table, overwrite status per run | Destroys execution history for recurring jobs; can't audit or debug "which run failed"; makes retry counting ambiguous across runs |
| DB-index polling with `SKIP LOCKED` | **Pull-based dispatch** | In-memory timer wheel / `DelayQueue` | Timer wheel is faster but non-durable and single-node; restart loses state and multiple replicas double-fire. Polling trades a little latency (allowed by NFR) for durability and trivial scale-out |
| `RetryPolicy` as an injected strategy | **Strategy** | Hardcoded `sleep(2^n)` in the executor | One global policy for every job type; a payment retry and a cleanup retry get the same backoff, and testing requires real sleeping |

### 2.6  Visual — Object Interaction (one job, one execution)

```
SchedulerService.schedule(job)
      |
      +--> job.schedule.nextRunAfter(now())  ---> sets job.nextRunAt
      +--> JobRepository.save(job)                [status = SCHEDULED]

... time passes ...

JobPoller (every 1s, on each scheduler replica)
      |
      +--> JobRepository.findDue(now, limit=100)
      |        SELECT ... WHERE next_run_at <= now() AND status='SCHEDULED'
      |        FOR UPDATE SKIP LOCKED            <-- two pollers never grab the same row
      |
      +--> for each due job:
               JobDispatcher.dispatch(job)
                     |
                     +--> JobQueue.push(job)     [status = QUEUED]

WorkerNode (polls the queue)
      |
      +--> JobQueue.poll() -> job
      +--> LeaseManager.tryAcquire(jobId, workerId, ttl=5m)
      |        |
      |        +-- false --> another worker owns it, drop silently (not an error)
      |        +-- true  --> continue
      |
      +--> JobExecutor.execute(job)
                |
                +--> create JobRun(attempt=n)    [status = RUNNING]
                +--> handler = handlers.get(job.jobType)
                +--> handler.handle(job.payload)
                |        |
                |        +-- success --> JobRun.status = SUCCEEDED
                |        |               if recurring: job.nextRunAt =
                |        |                   schedule.nextRunAfter(now()); status=SCHEDULED
                |        |               else: job.status = SUCCEEDED
                |        |
                |        +-- exception --> retryPolicy.shouldRetry(attempt)?
                |                          yes: nextRunAt = now + nextDelay(attempt)
                |                               status = RETRYING
                |                          no:  status = DEAD  (-> dead letter)
                |
                +--> LeaseManager.release(jobId, workerId)   [finally block]
```

**Narrate this line when you draw it:** *"The lease release is in a `finally` — but even if the process is killed before `finally` runs, the TTL expires and the job becomes claimable again. That's the whole point of a lease over a flag."*

### 2.7  Coding Skeleton

**Write in this order:** enum -> interface -> impl -> registry -> orchestrator.

```java
// 1. Enum first — no magic strings
public enum JobStatus {
    SCHEDULED, QUEUED, RUNNING, SUCCEEDED, FAILED, RETRYING, CANCELLED, DEAD;

    // Guarded transitions — the "enum + guard" middle ground from 2.5
    private static final Map<JobStatus, Set<JobStatus>> ALLOWED = Map.of(
        SCHEDULED, EnumSet.of(QUEUED, CANCELLED),
        QUEUED,    EnumSet.of(RUNNING, CANCELLED),
        RUNNING,   EnumSet.of(SUCCEEDED, FAILED, RETRYING),
        RETRYING,  EnumSet.of(SCHEDULED, DEAD),
        FAILED,    EnumSet.of(RETRYING, DEAD)
    );

    public boolean canTransitionTo(JobStatus next) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(next);
    }
}

// 2. The core Strategy — interface before implementations
public interface Schedule {
    Instant nextRunAfter(Instant from);
    boolean isRecurring();
}

// 3. Two implementations (write these two live; mention the rest follow the shape)
public class OneShotSchedule implements Schedule {
    private final Instant runAt;

    public OneShotSchedule(Instant runAt) { this.runAt = runAt; }

    @Override
    public Instant nextRunAfter(Instant from) {
        return runAt.isAfter(from) ? runAt : null;   // null == never again
    }

    @Override
    public boolean isRecurring() { return false; }
}

public class CronSchedule implements Schedule {
    private final CronExpression cron;   // parsing is a separate concern — see 2.9

    public CronSchedule(String expression) {
        this.cron = CronExpression.parse(expression);
    }

    @Override
    public Instant nextRunAfter(Instant from) { return cron.next(from); }

    @Override
    public boolean isRecurring() { return true; }
}

// 4. Registry — handlers self-register via DI, scheduler imports no business code
public class JobExecutor {
    private final Map<String, JobHandler> handlers;
    private final LeaseManager leaseManager;
    private final JobRepository repository;

    public JobExecutor(List<JobHandler> handlerList,
                       LeaseManager leaseManager,
                       JobRepository repository) {
        this.handlers = handlerList.stream()
            .collect(Collectors.toMap(JobHandler::getType, h -> h));
        this.leaseManager = leaseManager;
        this.repository = repository;
    }

    // 5. The orchestrator method — narrate this one live
    public void execute(Job job, String workerId) {
        // Atomic claim. If another worker owns it, this is a no-op, not an error.
        if (!leaseManager.tryAcquire(job.getJobId(), workerId, Duration.ofMinutes(5))) {
            return;
        }
        JobRun run = JobRun.start(job, workerId);
        try {
            JobHandler handler = handlers.get(job.getJobType());
            if (handler == null) {
                throw new IllegalStateException("No handler for type: " + job.getJobType());
            }
            handler.handle(job.getPayload());
            run.markSucceeded();
            rescheduleOrComplete(job);
        } catch (Exception e) {
            run.markFailed(e.getMessage());
            applyRetryPolicy(job, run.getAttempt());
        } finally {
            repository.saveRun(run);
            repository.update(job);
            leaseManager.release(job.getJobId(), workerId);   // TTL covers us if we die first
        }
    }

    private void rescheduleOrComplete(Job job) {
        if (job.getSchedule().isRecurring()) {
            job.setNextRunAt(job.getSchedule().nextRunAfter(Instant.now()));
            job.setStatus(JobStatus.SCHEDULED);          // back in the pool
        } else {
            job.setStatus(JobStatus.SUCCEEDED);
        }
    }

    private void applyRetryPolicy(Job job, int attempt) {
        RetryPolicy policy = job.getRetryPolicy();
        if (policy.shouldRetry(attempt)) {
            job.setNextRunAt(Instant.now().plus(policy.nextDelay(attempt)));
            job.setStatus(JobStatus.RETRYING);
        } else {
            job.setStatus(JobStatus.DEAD);               // dead-letter for inspection
        }
    }
}
```

### 2.8  Concurrency — Making It Thread-Safe

Three distinct races here. Name all three — most candidates only find the first.

| Race | Where | Fix | Why this fix |
|---|---|---|---|
| **Two pollers grab the same due job** | `findDue()` on N scheduler replicas | `SELECT ... FOR UPDATE SKIP LOCKED LIMIT 100` | The DB does the mutual exclusion; `SKIP LOCKED` means replica B takes the *next* 100 rows instead of blocking behind replica A. Scales linearly with replicas — no external coordinator needed. |
| **Two workers execute the same queued job** | after `JobQueue.poll()` (SQS is at-least-once) | `LeaseManager.tryAcquire()` = Redis `SET key owner NX PX ttl` | Atomic compare-and-set in one round trip. The loser drops the message silently — that's correct behavior, not an error. |
| **Handler map mutated during execution** | `JobExecutor.handlers` | Built once in the constructor, never mutated -> effectively immutable | Immutability beats locking. If hot-registration were required, `ConcurrentHashMap` — but then say *why* you'd need it. |

**The lease-expiry edge case to raise unprompted (this is the senior signal):** if a worker stalls (long GC pause) past its 5-minute TTL, the lease expires, a second worker picks the job up, and now **both** are running it. Mitigations to name: (a) a heartbeat that calls `renew()` at TTL/3, and (b) make handlers idempotent — pass `runId` so the handler can dedupe. Say the honest part out loud: *"With at-least-once dispatch, exactly-once execution isn't achievable end-to-end without idempotent handlers — the lease shrinks the window, it doesn't close it."*

### 2.9  "What Would You Do Differently?"

**Cron parsing is a separate class, and I'd not hand-roll it.** `CronExpression.parse()` is a whole sub-problem — field ranges, steps (`0/5`), day-of-week vs day-of-month conflicts, DST. In production I'd use a library (Quartz's `CronExpression`, `cron-utils`). **But flag this:** the Jun 2025 Salesforce variant *"Cron Job Parser"* asks you to build exactly that (`"0/5 8,12 1 * 1-5"` -> Minute/Hour/Day/Month/DayOfWeek). If they pivot there, it's a tokenize-then-expand-each-field problem: split on whitespace into 5 fields, and per field handle `*`, `a-b` ranges, `a,b,c` lists, and `a/n` steps, each producing a `Set<Integer>` of valid values.

**Second thing:** I'd add a **jitter** to retry backoff. Pure exponential backoff synchronizes retries — 10,000 jobs failing during a downstream outage all retry at exactly `t+2s`, then `t+4s`, hammering the recovering service. `delay * (0.5 + random())` spreads them.

### 2.10  Interview Q&As (prep-only, don't recite)

| Q | A |
|---|---|
| "How do you guarantee exactly-once execution?" | "You can't, end to end, with at-least-once dispatch. I get *effectively* once via a lease (narrow the window) + idempotent handlers keyed on `runId` (make duplicates harmless). Claiming true exactly-once here is a red flag." |
| "What if a job takes longer than its recurrence interval?" (every 5 min, runs 7 min) | "Overlap policy — and it should be per-job config: SKIP (default, don't start if one is running), QUEUE (run back-to-back), or ALLOW (concurrent). The lease naturally implements SKIP for free." |
| "How do you cancel a job that's already running?" | "Cancellation is cooperative. Set `status=CANCELLED` so it won't be re-dispatched, and pass a cancellation token the handler checks between steps. You can't safely kill a thread mid-write." |
| "Clock skew across scheduler nodes?" | "Never trust node wall-clocks for correctness — use the DB's `now()` as the single time authority in the due-query, so all replicas agree by construction. NTP drift then only affects logging." |
| "How do you handle a job scheduled while the system was down?" | "It just runs late — the due-query is `next_run_at <= now()`, not `== now()`, so a backlog drains naturally. For cron jobs I'd add a `misfirePolicy`: fire-once-immediately vs skip-to-next-occurrence. Quartz calls this misfire handling." |
| "Why not Quartz / Airflow / Temporal?" | "For real, I'd evaluate them first. Quartz is close to this design (it's DB-backed with row locks). I'd build custom when I need multi-tenant governor limits or tighter control over dispatch — which is exactly Salesforce's situation." |

### 2.11  TL;DR — 30-Second Pitch (LLD)

Three interfaces carry the design: `Schedule` (when it runs — one-shot vs cron as Strategy), `JobHandler` (what it does — resolved from a DI registry so the scheduler imports zero business code), and `RetryPolicy` (how it recovers). `Job` is the definition and `JobRun` is one execution — separated because a recurring job has many runs and collapsing them destroys history. Double-execution is prevented in two layers: `SELECT FOR UPDATE SKIP LOCKED` so two pollers never claim the same row, and a TTL'd lease so two workers never run the same job — with the honest caveat that exactly-once needs idempotent handlers, since a lease narrows the window rather than closing it.

### 2.12  Patterns Used

| Pattern | Where | Why (one line) |
|---|---|---|
| **Strategy** | `Schedule`, `RetryPolicy`, `JobHandler` | Three independent variation points, each runtime-swappable |
| **Registry** | `JobExecutor.handlers` map | Resolve behavior by key without a `switch` and without importing implementations |
| **Repository** | `JobRepository` | Isolates the due-query so it can become a sharded scan later |
| **Factory** | `ScheduleFactory` | Rebuilds the right `Schedule` type from persisted `(type, expr, tz)` data — keeps the schema decoupled from Java classes |
| **Lease / lock with expiry** | `LeaseManager` | Mutual exclusion that self-heals when the owner dies |
| **State machine (as guarded enum)** | `JobStatus.canTransitionTo()` | Illegal transitions rejected in one place; upgrade path to full State named |
| **Template Method** (light) | `JobExecutor.execute()` | Fixed skeleton — lease, run, reschedule-or-retry, release — with the variable step delegated to the handler |

---

## 3.  HLD Half (target: 45 min)

### 3.1 Clarifying Questions (0-3 min)

| Question | Architectural Fork |
|---|---|
| "Scale — how many scheduled jobs, and what's the peak fire rate?" | 10K jobs -> one Postgres table and a single poller is genuinely fine. 100M jobs / 500K fires-per-minute -> time-based sharding, partitioned tables, and a dispatch tier become mandatory. |
| "Exactly-once or at-least-once execution?" | At-least-once + idempotent handlers -> lease + retry (this design). True exactly-once -> distributed transactions or a transactional outbox per job, much heavier. |
| "Job duration — milliseconds or hours?" | Sub-second -> simple request/response workers. Hour-long jobs -> heartbeats, lease renewal, and cancellation tokens become first-class. |
| "Multi-tenant? Can one tenant's jobs starve another's?" | Single tenant -> one global queue. Multi-tenant (Salesforce: 150K orgs) -> per-org quotas + fair scheduling, or one noisy org monopolizes every worker. |

### 3.2 Requirements

**Functional (5):**
- Register one-shot and recurring (cron) jobs
- Dispatch each due job to exactly one worker
- Execute via pluggable per-type handlers
- Retry with backoff; dead-letter after N attempts
- Query job status + execution history; cancel/pause/resume

**Non-Functional (4):**
- Scale: **100M scheduled jobs**, peak **500K fires/min (~8.3K/sec)**
- Durability: a scheduler crash loses nothing — all state in durable storage
- Scheduling accuracy: fire within **~1s** of due time (P99)
- No double execution under normal operation (lease + idempotent handlers)

### 3.3 Core Entities

| Entity | Nature |
|---|---|
| **Job** (definition) | transactional — mutable `next_run_at`, read on the hot path |
| **JobRun** (execution) | append-only — permanent audit record, never updated after terminal |
| **JobLease** | ephemeral — TTL'd, self-expiring, never needs manual cleanup |
| **DeadLetterJob** | append-only — requires human/ops inspection |
| **TenantQuota** | transactional — small, cached, read per dispatch decision |

### 3.4 Scale Estimation

- **Fire rate:** 100M jobs, average one fire/day = ~1,160/sec baseline; peak **8.3K/sec** (jobs cluster hard at `:00` — see the thundering-herd breaking point below)
- **Due-query load:** at a 1s poll interval, that's **1 index scan/sec per replica**, each returning up to a few thousand rows at peak — the index on `(status, next_run_at)` is the single most important thing in the schema
- **Storage:** `JobRun` at ~300 bytes x 100M runs/day = **30 GB/day -> ~11 TB/year** -> 30-day hot partitions in Postgres, older to S3/Parquet. `Job` definitions are only 100M x 1KB = **100 GB total**, which fits comfortably

### 3.5 Architecture Diagram

Draw the naive version, break it with a number, then fix it.

#### Stage 1 — Naive: one scheduler process with an in-memory timer

```
  +-------------------+        +--------------------------------+
  |   Client / API    |------->|      Scheduler Process         |
  +-------------------+        |  ScheduledExecutorService      |
                               |  (jobs held in JVM heap)       |
                               |    - runs job inline on the    |
                               |      timer thread              |
                               +----------------+---------------+
                                                |
                                                v
                                        business logic
```

**BREAKING POINT 1 — total loss on restart.** Schedules live in heap. A deploy, OOM, or crash silently drops **every** scheduled job. There is no recovery because there is no record.

**BREAKING POINT 2 — cannot scale past one process.** Run 2 replicas for availability and every job fires **twice** — there's no shared claim. So availability and correctness are in direct conflict: you're forced to run exactly one instance, which is also a single point of failure.

**BREAKING POINT 3 — one slow job blocks the timer.** `ScheduledExecutorService` with a small pool means a 10-minute job occupies a timer thread; jobs due behind it fire late. At 8.3K/sec this collapses immediately.

**DECISION — where does schedule state live?**

| Option | Strength | Weakness | Verdict |
|---|---|---|---|
| In-memory timer wheel / `DelayQueue` | O(1) next-job lookup; no DB load | Lost on restart; can't coordinate replicas; rebuild requires a DB scan anyway | Rejected — fails the durability NFR outright |
| Redis sorted set (score = `next_run_at`) | Very fast `ZRANGEBYSCORE`; naturally ordered | Durability depends on AOF/RDB config; 100M members is a large memory footprint; still need a DB for history | Good as a **cache/index**, not as the source of truth |
| **Durable DB row with an indexed `next_run_at`, polled with `SKIP LOCKED`** | Survives restarts; multi-replica safe with zero extra infra; history and definitions live together | Poll interval sets a latency floor (~1s); index write amplification on every reschedule | **Chosen** — NFR allows ~1s accuracy |

#### Stage 2 — Sharded pollers, decoupled dispatch, stateless workers

```
   +--------------+
   |  Client API  |  POST /v1/jobs
   +------+-------+
          v
   +------------------+
   | Scheduler API    |  validates, computes first next_run_at, persists
   +--------+---------+
            v
   +------------------------------------------+
   |     Postgres: jobs (partitioned)         |
   |     INDEX (status, next_run_at)          |<--------+
   |     partitioned by hash(job_id) -> 16    |         |
   +--------------------+---------------------+         | status update
                        ^                               | + reschedule
     poll every 1s      |  SELECT ... WHERE next_run_at <= now()
     (each replica owns |  FOR UPDATE SKIP LOCKED LIMIT 100
      a shard range)    |                               |
          +-------------+-------------+                 |
          |             |             |                 |
   +------+-----+ +-----+------+ +----+-------+         |
   | Poller A   | | Poller B   | | Poller C   |         |
   | shards 0-5 | | shards 6-10| | shards11-15|         |
   +------+-----+ +-----+------+ +----+-------+         |
          |             |             |                 |
          +-------------+-------------+                 |
                        v                               |
              +---------------------+                   |
              | Dispatcher          |                   |
              | - per-tenant quota  |<--+ Redis: tenant |
              |   check (fairness)  |   | token buckets |
              +----------+----------+   +---------------+
                         v                               |
              +-------------------------+                |
              |   SQS / Kafka: job-queue|                |
              |   (at-least-once)       |                |
              +-----------+-------------+                |
                          v                              |
        +-----------------+------------------+           |
        |                 |                  |           |
  +-----+------+   +------+-----+    +-------+----+      |
  | Worker Pod |   | Worker Pod |    | Worker Pod |      |
  | + lease    |   | + lease    |    | + lease    |------+
  | + handler  |   | + handler  |    | + handler  |
  +-----+------+   +------+-----+    +-------+----+
        |                 |                  |
        +--------+--------+------------------+
                 v                    v
        +------------------+   +------------------+
        | Redis: leases    |   | Postgres:        |
        | SET NX PX 5m     |   | job_runs         |
        +------------------+   | (30d hot -> S3)  |
                               +------------------+
                                        |
                                   failures exhausted
                                        v
                               +------------------+
                               | dead_letter      |
                               +------------------+
```

**What each piece buys us:**
- **Shard-partitioned polling** — each poller replica owns a disjoint hash range, so replicas never contend on the same rows; `SKIP LOCKED` is the second line of defense during rebalances.
- **Dispatcher as a separate tier** — this is where per-tenant fairness is enforced *before* work reaches the shared queue. Doing it in the worker is too late; the queue is already poisoned.
- **Queue between dispatch and execution** — decouples job duration from polling cadence, so a 10-minute job can't delay the poller.
- **Stateless workers + Redis lease** — scale horizontally; a dead worker's lease expires and the job is retried automatically.

**BREAKING POINT (Stage 2) — the thundering herd at `:00`.** Humans schedule jobs at round times. Empirically most cron expressions land on the hour, so a large share of daily fires bunch into the first seconds of each hour. If even 5% of 100M jobs (5M) are due at `00:00:00`, a 1s poll with `LIMIT 100` per replica drains 100 x replicas per second — at 16 replicas that's 1,600/sec, meaning the herd takes **~52 minutes to clear**, and jobs fire catastrophically late. **Mitigations to name:** (a) raise batch size and replica count for the top of the hour, (b) **add deterministic jitter** at schedule creation — spread "hourly" jobs across the hour by hashing `job_id` into an offset, (c) priority lanes so latency-sensitive jobs bypass the herd. Option (b) is the cheap, high-leverage one and it's the answer they're looking for.

**Remaining known gap (say it before they find it):** the dispatcher writes to the queue *after* the poller marks the row — a crash between them means a job marked `QUEUED` that never reached the queue. Fix: a **reaper** that finds rows stuck in `QUEUED` past a threshold and returns them to `SCHEDULED`. That's cheaper than an outbox here because the reaper is needed for crashed-worker recovery anyway.

### 3.6 Deep Dive: Preventing Double Execution (the riskiest component)

**Why this one:** it's the only requirement where being wrong is silent and expensive — a duplicated "charge the customer" job is a real incident, and the failure is invisible in normal monitoring.

**The defense is layered, and each layer catches a different failure:**

| Layer | Mechanism | Catches | Doesn't catch |
|---|---|---|---|
| 1. Row claim | `SELECT ... FOR UPDATE SKIP LOCKED` | Two pollers reading the same due row | Anything after the row is claimed |
| 2. Queue dedup | SQS message dedup ID / Kafka key | Duplicate enqueue from a poller retry | SQS at-least-once redelivery to two consumers |
| 3. Execution lease | Redis `SET jobId owner NX PX 5m` | Two workers pulling the same message | A stalled owner whose TTL expires mid-run |
| 4. Handler idempotency | Business-level dedup keyed on `runId` | Everything above, as the final backstop | Nothing — this is the only complete answer |

**Options considered for layer 3:**

| Option | Pros | Cons |
|---|---|---|
| DB row lock held for job duration | Strong; no extra infra | A 10-minute job holds a Postgres transaction for 10 minutes — bloats WAL, blocks vacuum, exhausts connections |
| **Redis lease with TTL + heartbeat renewal** | Cheap (~0.2ms), self-expiring, survives worker death, no long transactions | Redis becomes a correctness dependency; a Redis failover can drop leases |
| Fencing token (monotonic counter) + storage-side check | Strictly correct even under stalls — stale owner's writes are rejected | Requires the *downstream* store to honor the token, which you often don't control |

**Decision:** Redis lease with heartbeat, **plus** `runId` passed to handlers for idempotency. Say the honest limitation out loud: *"A lease is a performance optimization for correctness, not a correctness guarantee. Under a GC pause longer than the TTL, two workers genuinely can run. The only complete answer is idempotent handlers — fencing tokens if the downstream store supports them."*

### 3.7 Trade-offs

**Trade-off 1: Poll-based dispatch vs push/timer-based**
- **Chose:** poll a `next_run_at` index every ~1s
- **Gain:** durable by construction, trivially multi-replica, no state to rebuild after deploy
- **Lose:** up to ~1s scheduling latency, and constant baseline DB query load even when idle
- **Failure mode if wrong:** if the product later needs sub-100ms scheduling (e.g. rate-limit-driven retries), polling can't get there without hammering the DB — you'd bolt on a Redis sorted-set hot tier for near-term jobs and keep the DB for everything beyond a few seconds out.

**Trade-off 2: At-least-once + idempotent handlers vs true exactly-once**
- **Chose:** at-least-once with lease narrowing plus handler idempotency
- **Gain:** vastly simpler; no distributed transactions; workers stay stateless and cheap to scale
- **Lose:** the correctness burden shifts onto every handler author — an org-wide discipline problem, not a technical one
- **Failure mode if wrong:** one team writes a non-idempotent payment handler, a worker GC-pauses past its lease, and a customer is charged twice — a financial incident with no automated detection. Mitigation: make `runId`-based dedup a helper in the shared job framework so idempotency is the default, not a per-team decision.

**Trade-off 3: Per-tenant fairness at the dispatcher vs a single FIFO queue**
- **Chose:** per-tenant token buckets checked at dispatch
- **Gain:** one org scheduling 1M jobs can't monopolize the worker fleet; every other org keeps its latency SLO
- **Lose:** an extra Redis lookup on the dispatch hot path (~0.2ms) and quota config to manage per tenant
- **Failure mode if wrong:** with a single FIFO queue, one tenant's bulk import puts 1M jobs ahead of everyone else's — all 150K other orgs see their scheduled jobs fire hours late, from a single tenant's ordinary usage. This is the noisy-neighbor problem, and it's the reason Salesforce has governor limits at all.

### 3.8 API Design

| Method | Path | Auth | Request | Response | Status Codes |
|---|---|---|---|---|---|
| POST | `/v1/jobs` | JWT (org-scoped) | `{jobType, payload, schedule: {type, expr}, retryPolicy, maxAttempts}` | `{jobId, nextRunAt}` | 201, 400 (bad cron), 429 (org quota) |
| GET | `/v1/jobs/{jobId}` | JWT | — | `{jobId, status, nextRunAt, lastRun}` | 200, 403, 404 |
| GET | `/v1/jobs/{jobId}/runs` | JWT | `?cursor=&limit=20` | `{runs[], nextCursor}` | 200, 403 |
| DELETE | `/v1/jobs/{jobId}` | JWT | — | `204` (cancel; cooperative if running) | 204, 404, 409 |
| POST | `/v1/jobs/{jobId}/pause` \| `/resume` | JWT | — | `{jobId, status}` | 200, 409 |

**Derivation note (say this):** `POST /v1/jobs` returns `nextRunAt`, not just an ID — it's the cheapest way for a caller to verify their cron expression means what they think it means. Returning the computed next fire time catches "0 0 * * *" vs "0 0 * * 0" mistakes at write time instead of a week later.

### 3.9 Data Model

```sql
-- The DEFINITION. Hot path: the partial index on (status, next_run_at).
CREATE TABLE jobs (
    job_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    org_id        UUID NOT NULL,               -- multi-tenancy + fairness key
    job_type      VARCHAR(64) NOT NULL,        -- resolves to a JobHandler
    payload       JSONB,
    payload_ver   SMALLINT DEFAULT 1,          -- lets handlers evolve safely

    schedule_type VARCHAR(16) NOT NULL,        -- ONESHOT | CRON | FIXED_RATE
    schedule_expr VARCHAR(128),                -- "0 */2 * * *" for CRON
    timezone      VARCHAR(40) DEFAULT 'UTC',   -- cron is wall-clock, so DST matters

    next_run_at   TIMESTAMPTZ,                 -- NULL = terminal, never runs again
    status        VARCHAR(16) NOT NULL,
    attempt       SMALLINT DEFAULT 0,
    max_attempts  SMALLINT DEFAULT 3,
    retry_policy  VARCHAR(32) DEFAULT 'EXPONENTIAL',

    created_at    TIMESTAMPTZ DEFAULT now(),
    updated_at    TIMESTAMPTZ DEFAULT now()
) PARTITION BY HASH (job_id);                  -- 16 partitions -> parallel pollers

-- THE index that makes the system work. Partial: only rows that can actually fire.
CREATE INDEX idx_due ON jobs (next_run_at)
    WHERE status IN ('SCHEDULED', 'RETRYING');

-- Fairness + per-org listing
CREATE INDEX idx_org ON jobs (org_id, status);

-- ONE EXECUTION. Append-only; never updated after reaching a terminal state.
CREATE TABLE job_runs (
    run_id        UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_id        UUID NOT NULL,
    org_id        UUID NOT NULL,
    attempt       SMALLINT NOT NULL,
    status        VARCHAR(16) NOT NULL,        -- RUNNING | SUCCEEDED | FAILED
    worker_id     VARCHAR(64),
    started_at    TIMESTAMPTZ DEFAULT now(),
    finished_at   TIMESTAMPTZ,
    duration_ms   INTEGER,
    error_message TEXT,

    UNIQUE (job_id, attempt),                  -- dedup backstop for retried dispatch
    INDEX idx_job_history (job_id, started_at DESC)
) PARTITION BY RANGE (started_at);             -- 30-day hot, older -> S3/Parquet

-- Terminal failures needing human attention.
CREATE TABLE dead_letter_jobs (
    job_id        UUID PRIMARY KEY,
    org_id        UUID NOT NULL,
    last_error    TEXT,
    total_attempts SMALLINT,
    dead_at       TIMESTAMPTZ DEFAULT now()
);
```

**Schema decisions worth saying out loud:**

| Decision | Why | What breaks otherwise |
|---|---|---|
| Partial index `WHERE status IN ('SCHEDULED','RETRYING')` | The due-query only ever scans fireable rows. Terminal jobs (the vast majority over time) are not in the index at all | A full index on `next_run_at` grows with all 100M rows forever, slowing every insert and bloating cache with rows that can never fire |
| `PARTITION BY HASH (job_id)`, 16 ways | Lets N pollers own disjoint shard ranges and scan in parallel without contending | One table means every poller competes for the same hot index pages; `SKIP LOCKED` alone still serializes on page latches at 8K/sec |
| `job_runs` split from `jobs` | One definition, many executions — the LLD `Job`/`JobRun` split, mirrored in storage | Overwriting status on the job row destroys history and makes "which attempt failed?" unanswerable |
| `UNIQUE (job_id, attempt)` | Idempotency backstop — a re-dispatched attempt can't create a second run row | Retry storms silently inflate the run history and corrupt success-rate metrics |
| `timezone` stored per job | Cron is **wall-clock** semantics: "2 AM daily" means 2 AM local, and DST makes that 23 or 25 hours apart | Storing UTC only means every DST transition shifts every user's job by an hour, twice a year |
| `next_run_at NULL` for terminal | NULL is naturally excluded from the index; no sentinel dates | A sentinel like `9999-12-31` keeps dead rows in the hot index forever |
| `payload_ver` | Jobs scheduled last week execute against this week's handler code | A handler refactor breaks every in-flight scheduled job with no migration path |

### 3.10 Salesforce Multi-Tenancy Angle

> *"On Salesforce's shared infrastructure this is Scheduled Apex — 150K orgs scheduling jobs against a shared worker fleet. I'd put `org_id` on every table and leading every index, enforce a **per-org token bucket at the dispatcher** so one org's 1M-job bulk import can't monopolize workers, and cap concurrent runs per org — which is exactly what Salesforce's real governor limits do (a production org is capped at 100 scheduled Apex jobs). I'd also make the dead-letter queue per-org, so one tenant's failing job doesn't bury another tenant's alerts."*

The fairness argument is the part that scores: **per-org quotas belong at the dispatcher, before the shared queue** — once a noisy tenant's jobs are in the queue, every other tenant is already behind them.

---

## 4.  Navigation Pivots — THIS Problem

**Opening Protocol (first 2 minutes — verbatim, per `format.md` Section 2):**

> "Before I start — should I do LLD first or HLD first, or do you have a preference?"
> *(If no preference:)* "I'll start with LLD — class design, the schedule and handler abstractions, concurrency. Then zoom out to the distributed system. I'll flag the transition explicitly so you can redirect me."

| Interviewer Says | What They Want | Your Move |
|---|---|---|
| "How do you make sure a job runs only once?" | The layered answer, both zoom levels | LLD: `SKIP LOCKED` + TTL'd lease. HLD: the 4-layer table in 3.6 — and end on "idempotent handlers are the only complete answer" |
| "How would you implement the cron parsing?" | LLD zoom-in (this is the Jun 2025 variant) | Tokenize 5 fields; per field expand `*`, `a-b`, `a,b,c`, `a/n` into a `Set<Integer>`; then `nextRunAfter` walks forward field by field. Don't redraw the system |
| "What happens at 100M jobs?" | HLD scale-out + bottleneck | Partial index + hash partitioning + shard-owning pollers; then name the thundering herd at `:00` and jitter as the fix |
| "A worker dies mid-job — then what?" | Failure recovery at both levels | LLD: lease TTL expires, job reclaimed, `attempt` increments. HLD: the reaper sweeps rows stuck in `QUEUED`/`RUNNING` |
| "Add a new job type tomorrow" | Extensibility (OCP) | LLD: one new `JobHandler`, registered via DI, zero edits. HLD: no infra change at all — workers are generic; that's the payoff of the handler registry |
| "How is this different from a message queue?" | Conceptual clarity | "A queue delivers *now*; a scheduler decides *when*. The scheduler sits in front of a queue — they compose, they don't compete" |
| "How does Salesforce's version differ?" | Domain awareness | Scheduled Apex + Flex Queue, per-org governor limits, fairness at the dispatcher |

---

## 5.  TL;DR — Dual-Level Pitch

At the class level this is three Strategy interfaces — `Schedule` (one-shot vs cron), `JobHandler` (per-type business logic resolved from a DI registry so the scheduler imports no business code), and `RetryPolicy` — with `Job` (the definition) deliberately split from `JobRun` (one execution) so recurring jobs keep full history. At the system level those classes become tiers: shard-owning pollers run `SELECT ... FOR UPDATE SKIP LOCKED` against a partial index on `next_run_at`, a dispatcher enforces per-tenant token buckets before work reaches the shared queue, and stateless workers claim a TTL'd Redis lease before executing. The defining trade-off is at-least-once with idempotent handlers rather than true exactly-once — a lease narrows the double-execution window but a GC pause past the TTL can still open it, so `runId`-keyed idempotency is the real guarantee. The bottleneck to name is the thundering herd at the top of each hour, fixed with deterministic jitter at schedule-creation time. On Salesforce's infrastructure this is Scheduled Apex, and per-org quotas at the dispatcher are what stop one of 150K tenants from starving the rest.

---

##  Changelog

| Date | Change |
|---|---|
| Aug 2026 | File created — second problem in `Interview/Salesforce/HLD+LLD/`. Grounded in the verbatim CodingKaro Apr 2025 LMTS prompt (job creation / persistence / dispatching / execution flow). Follows `solution-notes-standards.md`; matches the derivation-first bar set by `notification-service.md` (noun-from-requirement table, composition-vs-aggregation calls, alternatives-considered against the *strongest* alternative, per-section follow-ups, staged HLD evolution with quantified breaking points, full data model). |
