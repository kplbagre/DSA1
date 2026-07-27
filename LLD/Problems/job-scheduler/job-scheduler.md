# Job Scheduler

## 🎯 Problem Statement

Design an in-memory Job Scheduler that:
- Accepts jobs with configurable priorities from multiple concurrent producers
- Executes submitted jobs on a fixed worker thread pool in priority order
- Allows cancellation of queued (not yet running) jobs
- Notifies registered observers whenever a job's status changes

**TrueFoundry context:** TrueFoundry schedules ML training, fine-tuning, and inference jobs on Kubernetes. The core scheduling challenge is the same — priority ordering, concurrent execution, and lifecycle tracking.

---

## 📖 Requirements

**Functional:**
- `submit(job)` — enqueue a job for execution
- `cancel(jobId)` — cancel a queued job (no-op if already running)
- `getStatus(jobId)` — query current job state
- `addListener(listener)` — register an Observer for status-change callbacks

**Non-functional:**
- Thread-safe — N producers submitting simultaneously must not corrupt queue ordering
- Configurable worker pool size (set at construction)
- Priority ordering: lower integer = higher priority; equal priorities are FIFO

**Out of scope (this design session):**
- Time-delayed / cron scheduling — see "What Would You Do Differently?"
- Job dependencies (DAG) — see "What Would You Do Differently?"
- Persistence — all state is in-memory

---

## 🏗️ Class Design

### 🎨 Visual — Class Relationships

```
                ┌───────────────────────────────────────────┐
                │              JobScheduler                 │
                │                                           │
                │  queue:    PriorityBlockingQueue<AbstractJob>│
                │  pool:     ExecutorService (fixed N)      │
                │  registry: ConcurrentHashMap<id, Job>     │
                │  listeners:CopyOnWriteArrayList<JobListener>│
                │                                           │
                │  + submit(job)                            │
                │  + cancel(jobId)                          │
                │  + getStatus(jobId): JobStatus            │
                │  + addListener(listener)                  │
                │  + start(workerCount) / shutdown()        │
                └──────────────────┬────────────────────────┘
                                   │ manages lifecycle of
           ┌───────────────────────┼────────────────────────┐
           ▼                       ▼                        ▼
   <<interface>>            <<enum>>                <<interface>>
   Job                      JobStatus               JobListener
   + getId(): String        PENDING                 + onStatusChange(
   + getPriority(): int     RUNNING                     jobId,
   + execute(): void        COMPLETED                   newStatus)
           │                FAILED
           │ extends        CANCELLED
   AbstractJob
   - id: String
   - priority: int
   - status: AtomicReference<JobStatus>
   + execute(): abstract
   + compareTo(Job): int
```

**KEY INVARIANT:**
```
   One dispatcher thread serializes the PriorityBlockingQueue — always submitting
   the highest-priority queued job to the pool next. N worker threads execute
   concurrently but never reorder jobs themselves.
```

---

## 🔑 Key Interfaces

```java
// Job.java — Command pattern: encapsulates the unit of work
public interface Job extends Comparable<Job> {
    String getId();
    // 1 = highest priority, 10 = lowest
    int getPriority();
    // the actual work — JobScheduler knows nothing about what the job does
    void execute();
}

// JobListener.java — Observer pattern: decouples status-change notification
@FunctionalInterface
public interface JobListener {
    void onStatusChange(String jobId, JobStatus newStatus);
}
```

---

## 🧭 Design Decisions

| Decision | Choice | Why |
|---|---|---|
| Queue type | `PriorityBlockingQueue` | Thread-safe; `take()` blocks without busy-wait; ordering via `Comparable<Job>` |
| Two-layer execution | Dispatcher thread + worker pool | Dispatcher guarantees priority ordering before handing to pool; workers never pull from queue directly |
| Cancel race safety | `AtomicReference<JobStatus>` + CAS | cancel() and dispatcher both attempt `compareAndSet(PENDING, X)` — exactly one wins, preventing double execution |
| Listener storage | `CopyOnWriteArrayList` | Listeners are added rarely but iterated on every status change; iteration is lock-free |
| Job registry | `ConcurrentHashMap` | Status lookups and cancellation are concurrent — needs thread-safe map |

> **Term gloss — CAS** (Compare-And-Swap): a single atomic CPU-level operation that reads a memory location, compares it to an expected value, and writes a new value only if the comparison matches — like a conditional assignment that cannot be interrupted between the read and write steps. Used in Java via `AtomicReference.compareAndSet(expected, next)`.

**Why two-layer (dispatcher + pool) instead of N workers polling directly?**
If N workers each called `queue.take()`, all would grab from the same priority queue — that's fine for thread-safety, but means a lower-priority job could start executing before a higher-priority job that arrives milliseconds later and hasn't entered the queue yet. The single dispatcher creates a sequential chokepoint: it takes one job at a time and submits to the pool, ensuring the pool always receives jobs in priority order.

---

## 🎨 Visual — Job Lifecycle

```
                  submit(job)
                      │
           ┌──────────▼──────────┐
           │  PriorityBlockingQueue  │   ← [PENDING] — sorted by priority
           └──────────┬──────────┘
                      │ dispatcher thread: take()
                      │ CAS: PENDING → RUNNING (or CANCELLED if cancel() won)
           ┌──────────▼──────────┐
           │   ExecutorService   │   ← worker pool (N threads)
           │   Worker 1          │──────────────────────────┐
           │   Worker 2          │                          │
           └─────────────────────┘                         │
                      │ executeJob()                        │
               ┌──────┴──────┐                             │
               │             │                             │
             success      exception                   cancel() called
               │             │                             │
           [COMPLETED]   [FAILED]                    [CANCELLED]
               │             │                             │
               └──────┬──────┘─────────────────────────────┘
                      │ notifyListeners()
               ┌──────▼──────┐
               │ JobListener │   ← Observer callbacks
               └─────────────┘

KEY INVARIANT:
   status transitions are irreversible and protected by CAS.
   Only ONE transition can win the race; the loser is a no-op.
```

---

## 🖊️ Coding Skeleton

**Interview writing order** (write these first, in 15–20 minutes):

**Steps in plain English:**

1. **Define job lifecycle** — enum for all states a job can be in.
2. **Define the command** — Job interface with `execute()` and priority comparison.
3. **Define the base** — AbstractJob holds identity, priority, and thread-safe status.
4. **Define the observer** — JobListener functional interface.
5. **Wire the scheduler** — one dispatcher thread + ExecutorService worker pool.

```java
// Step 1 — define the states a job moves through
enum JobStatus { PENDING, RUNNING, COMPLETED, FAILED, CANCELLED }

// Step 2 — the Command interface
public interface Job extends Comparable<Job> {
    String getId();
    // 1 = highest priority, 10 = lowest
    int getPriority();
    // Command — encapsulates the actual work
    void execute();
}

// Step 3 — base class holds identity and thread-safe status
public abstract class AbstractJob implements Job {
    private final String id;
    private final int priority;
    // AtomicReference instead of volatile — enables CAS for cancel() vs dispatch() race
    private final AtomicReference<JobStatus> status =
        new AtomicReference<>(JobStatus.PENDING);

    protected AbstractJob(String id, int priority) {
        this.id = id;
        this.priority = priority;
    }

    @Override
    public String getId() { return id; }

    @Override
    public int getPriority() { return priority; }

    public JobStatus getStatus() { return status.get(); }

    // package-private — only JobScheduler transitions status via CAS
    boolean casStatus(JobStatus expected, JobStatus next) {
        return status.compareAndSet(expected, next);
    }

    @Override
    public int compareTo(Job other) {
        // Lower number = higher priority = polled first from PriorityBlockingQueue
        return Integer.compare(this.priority, other.getPriority());
    }
}

// Step 4 — the Observer interface
@FunctionalInterface
public interface JobListener {
    void onStatusChange(String jobId, JobStatus newStatus);
}

// Step 5 — the scheduler: one dispatcher + N workers
public class JobScheduler {
    private final PriorityBlockingQueue<AbstractJob> queue =
        new PriorityBlockingQueue<>();
    private final ExecutorService workerPool;
    private final ConcurrentHashMap<String, AbstractJob> registry =
        new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<JobListener> listeners =
        new CopyOnWriteArrayList<>();
    private volatile boolean running = false;
    private Thread dispatcherThread;

    public JobScheduler(int workerCount) {
        this.workerPool = Executors.newFixedThreadPool(workerCount);
    }

    public void submit(AbstractJob job) {
        registry.put(job.getId(), job);
        // PriorityBlockingQueue.put() never blocks — unbounded queue
        queue.put(job);
    }

    public boolean cancel(String jobId) {
        AbstractJob job = registry.get(jobId);
        if (job == null) {
            return false;
        }
        // CAS: only succeeds if job is still PENDING
        // If dispatcher already won the PENDING→RUNNING race, this is a no-op
        boolean cancelled = job.casStatus(JobStatus.PENDING, JobStatus.CANCELLED);
        if (cancelled) {
            notifyListeners(job.getId(), JobStatus.CANCELLED);
        }
        return cancelled;
    }

    public JobStatus getStatus(String jobId) {
        AbstractJob job = registry.get(jobId);
        return job == null ? null : job.getStatus();
    }

    public void addListener(JobListener listener) {
        listeners.add(listener);
    }

    public void start() {
        running = true;
        dispatcherThread = new Thread(this::dispatchLoop, "job-dispatcher");
        dispatcherThread.setDaemon(true);
        dispatcherThread.start();
    }

    public void shutdown() {
        running = false;
        dispatcherThread.interrupt();
        workerPool.shutdown();
    }
}
```

---

## ⚡ Concurrency

**Shared mutable state — one row per shared object:**

| Shared State | Location | Writers | Readers | Protection |
|---|---|---|---|---|
| Queue contents | `PriorityBlockingQueue` | N producers (submit) | 1 dispatcher (take) | `PriorityBlockingQueue` is internally thread-safe |
| Job status | `AtomicReference<JobStatus>` | cancel() + dispatchLoop | getStatus() + workers | CAS — only one wins the PENDING→X transition |
| Job registry | `ConcurrentHashMap` | submit() | cancel(), getStatus() | `ConcurrentHashMap` — no external sync needed |
| Listeners list | `CopyOnWriteArrayList` | addListener() | notifyListeners() iterates | `CopyOnWriteArrayList` — iteration never throws CME |
| `running` flag | `volatile boolean` | shutdown() | dispatchLoop | `volatile` — single write, multiple reads |

**The cancel-vs-dispatch race in detail:**

```
Thread A (cancel):      job.casStatus(PENDING, CANCELLED)  ← wins   → notifyListeners CANCELLED
Thread B (dispatcher):  job.casStatus(PENDING, RUNNING)    ← loses  → sees false, skips executeJob
                                              OR
Thread A (cancel):      job.casStatus(PENDING, CANCELLED)  ← loses
Thread B (dispatcher):  job.casStatus(PENDING, RUNNING)    ← wins   → executeJob() runs normally
```

Only one CAS succeeds; the other thread sees `false` and takes no action. No locks needed.

**Why AtomicReference over volatile here:**
- `volatile` guarantees visibility but not atomicity of a compound read-then-write
- `AtomicReference.compareAndSet()` makes read + conditional-write a single atomic operation
- Two threads calling `casStatus(PENDING, X)` simultaneously: one wins, one gets `false` — no additional coordination required

---

## 🔧 What Would You Do Differently?

1. **Time-delayed scheduling** — Replace `PriorityBlockingQueue` with `DelayQueue<DelayedJob>` where `DelayedJob implements Delayed`. `getDelay(TimeUnit)` returns remaining wait time; `take()` blocks until the earliest job's delay expires. For simpler use cases, `ScheduledThreadPoolExecutor.schedule(job, delay, unit)` works out of the box.

2. **Callable + Future for result access** — Change `Job.execute()` to `Callable<T>`. Store `Future<T>` from `workerPool.submit(callable)` in the registry. Callers that need the job's output call `future.get()`.

3. **Retry with exponential backoff** — Add `maxAttempts` + `attemptCount` to `AbstractJob`. In `executeJob()`, on failure: if `attemptCount < maxAttempts`, reset status to PENDING and re-enqueue after a calculated `delay = baseDelay * 2^attemptCount`.

4. **DAG dependencies** — Track `Map<String, Set<String>> blockedBy` (jobId → set of dependency jobIds). When a job completes, scan for dependent jobs that now have zero outstanding dependencies and submit them. The Observer on job completion is the natural hook.

5. **Persistence** — Serialize job state to a database. On restart, reload all `PENDING` jobs. Critical for TrueFoundry's ML training jobs that may run for hours across pod restarts.

---

## 🧩 Interview Q&As

**Q1: Why a single dispatcher thread instead of N workers polling the PriorityBlockingQueue directly?**

If N workers each called `queue.take()`, they'd compete for jobs in no defined order. A high-priority job arriving at T=0ms could lose to a lower-priority job already polled at T=-1ms. The single dispatcher creates a serialization point: it always submits the globally highest-priority available job to the pool, maintaining strict priority ordering regardless of how many workers are running.

**Q2: What happens if a subscriber (JobListener) throws an exception during `notifyListeners()`?**

In the current design, an unchecked exception from any listener would propagate up and prevent remaining listeners from being notified. Fix: wrap each listener call in a `try-catch`, log the exception, and continue iterating. This is the same defensive pattern used in `EventBus.publish()`.

**Q3: Why `CopyOnWriteArrayList` for listeners? Why not `ArrayList + synchronized`?**

`notifyListeners()` is called on every job status change — potentially hundreds of times per second. `CopyOnWriteArrayList` makes iteration completely lock-free (reads see a stable snapshot of the array). The tradeoff: `addListener()` copies the entire array, so it's expensive on write. Since listeners are added once at startup and read constantly, this is the right tradeoff.

**Q4: How would you implement priority with equal-priority tie-breaking by submission time?**

Add a `submittedAt = System.nanoTime()` field to `AbstractJob`. Update `compareTo()`:

```java
@Override
public int compareTo(Job other) {
    int cmp = Integer.compare(this.priority, other.getPriority());
    if (cmp != 0) {
        return cmp;
    }
    // Same priority — earlier submission time wins (FIFO tie-break)
    return Long.compare(this.submittedAt, ((AbstractJob) other).submittedAt);
}
```

**Q5: What's the risk if `executeJob()` never catches exceptions?**

An unchecked exception inside `job.execute()` would propagate out of the `Runnable` submitted to the pool. The worker thread dies (or is replaced by the pool, depending on the `ThreadFactory`). The job status stays `RUNNING` forever — `getStatus()` returns `RUNNING` even though nothing is executing. Always wrap `job.execute()` in a try-catch and transition to `FAILED` on any exception.

---

## 🧾 TL;DR

One `PriorityBlockingQueue` + one dispatcher thread + N-worker `ExecutorService`. The dispatcher serializes priority ordering; workers execute concurrently. `AtomicReference<JobStatus>` CAS makes `cancel()` vs dispatch race-safe — exactly one wins. `JobListener` Observer decouples notification from execution. Retry, DAG deps, and time-delay are extensions, not core.

---

## 🗺️ Patterns Used

- **Command** — `Job.execute()` encapsulates the unit of work; `JobScheduler` knows nothing about what the job does. See `LLD/DesignPatterns/03-command.md`
- **Observer** — `JobListener.onStatusChange()` decouples status-change notification from job execution. See `LLD/DesignPatterns/02-observer.md`

---

## 🖊️ Full Implementation

### JobStatus.java

```java
public enum JobStatus {
    PENDING,     // submitted, waiting in PriorityBlockingQueue
    RUNNING,     // picked by dispatcher, executing on worker thread
    COMPLETED,   // execute() returned normally
    FAILED,      // execute() threw an exception
    CANCELLED    // cancel() succeeded before dispatcher picked this job
}
```

### Job.java

```java
public interface Job extends Comparable<Job> {
    String getId();
    // 1 = highest priority, 10 = lowest
    int getPriority();
    // Command — encapsulates the actual work
    void execute();
}
```

### JobListener.java

```java
@FunctionalInterface
public interface JobListener {
    // Observer — called whenever a job transitions to a new status
    void onStatusChange(String jobId, JobStatus newStatus);
}
```

### AbstractJob.java

```java
import java.util.concurrent.atomic.AtomicReference;

public abstract class AbstractJob implements Job {

    private final String id;
    private final int priority;
    private final long submittedAt;
    // AtomicReference for CAS — volatile alone can't protect compound read-then-write
    private final AtomicReference<JobStatus> status =
        new AtomicReference<>(JobStatus.PENDING);

    protected AbstractJob(String id, int priority) {
        this.id = id;
        this.priority = priority;
        this.submittedAt = System.nanoTime();
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public int getPriority() {
        return priority;
    }

    public JobStatus getStatus() {
        return status.get();
    }

    // Package-private — only JobScheduler should drive status transitions
    boolean casStatus(JobStatus expected, JobStatus next) {
        return status.compareAndSet(expected, next);
    }

    @Override
    public int compareTo(Job other) {
        int cmp = Integer.compare(this.priority, other.getPriority());
        if (cmp != 0) {
            return cmp;
        }
        // Equal priority — earlier submission wins (FIFO tie-break)
        return Long.compare(this.submittedAt, ((AbstractJob) other).submittedAt);
    }

    @Override
    public abstract void execute();
}
```

### JobScheduler.java

```java
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.TimeUnit;

public class JobScheduler {

    private final PriorityBlockingQueue<AbstractJob> queue =
        new PriorityBlockingQueue<>();
    private final ExecutorService workerPool;
    private final ConcurrentHashMap<String, AbstractJob> registry =
        new ConcurrentHashMap<>();
    // CopyOnWriteArrayList — listeners added rarely, iterated on every status change
    private final CopyOnWriteArrayList<JobListener> listeners =
        new CopyOnWriteArrayList<>();
    // volatile — single writer (shutdown()), multiple readers (dispatchLoop)
    private volatile boolean running = false;
    private Thread dispatcherThread;

    public JobScheduler(int workerCount) {
        this.workerPool = Executors.newFixedThreadPool(workerCount);
    }

    public void submit(AbstractJob job) {
        registry.put(job.getId(), job);
        // PriorityBlockingQueue.put() never blocks — unbounded queue
        queue.put(job);
    }

    public boolean cancel(String jobId) {
        AbstractJob job = registry.get(jobId);
        if (job == null) {
            return false;
        }
        // CAS: wins only if job is still PENDING
        // If dispatcher already transitioned to RUNNING, this returns false — no-op
        boolean cancelled = job.casStatus(JobStatus.PENDING, JobStatus.CANCELLED);
        if (cancelled) {
            notifyListeners(job.getId(), JobStatus.CANCELLED);
        }
        return cancelled;
    }

    public JobStatus getStatus(String jobId) {
        AbstractJob job = registry.get(jobId);
        return job == null ? null : job.getStatus();
    }

    public void addListener(JobListener listener) {
        listeners.add(listener);
    }

    public void start() {
        running = true;
        dispatcherThread = new Thread(this::dispatchLoop, "job-dispatcher");
        // Daemon thread — JVM exit doesn't wait for it
        dispatcherThread.setDaemon(true);
        dispatcherThread.start();
    }

    public void shutdown() {
        running = false;
        dispatcherThread.interrupt();
        workerPool.shutdown();
    }

    private void dispatchLoop() {
        while (running || !queue.isEmpty()) {
            try {
                // poll with timeout — lets the loop check 'running' flag periodically
                AbstractJob job = queue.poll(500, TimeUnit.MILLISECONDS);
                if (job == null) {
                    continue;
                }
                // CAS: PENDING → RUNNING; if cancel() already won the race, skip this job
                if (!job.casStatus(JobStatus.PENDING, JobStatus.RUNNING)) {
                    // job was CANCELLED before we got here — discard silently
                    continue;
                }
                notifyListeners(job.getId(), JobStatus.RUNNING);
                // Submit to worker pool — non-blocking; pool queues if all workers busy
                workerPool.submit(() -> executeJob(job));
            } catch (InterruptedException e) {
                // shutdown() interrupted us — honour it
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private void executeJob(AbstractJob job) {
        try {
            job.execute();
            // Status is already RUNNING — transition to COMPLETED
            job.casStatus(JobStatus.RUNNING, JobStatus.COMPLETED);
            notifyListeners(job.getId(), JobStatus.COMPLETED);
        } catch (Exception e) {
            job.casStatus(JobStatus.RUNNING, JobStatus.FAILED);
            notifyListeners(job.getId(), JobStatus.FAILED);
        }
    }

    private void notifyListeners(String jobId, JobStatus newStatus) {
        for (JobListener listener : listeners) {
            // Defensive catch — one bad listener must not block remaining listeners
            try {
                listener.onStatusChange(jobId, newStatus);
            } catch (Exception ignored) {
                // Production: log the exception here
            }
        }
    }
}
```

### Usage Example

```java
public class TrainingJobExample {

    public static void main(String[] args) throws InterruptedException {
        JobScheduler scheduler = new JobScheduler(4);

        // Observer — log every status change (production: use SLF4J log.info)
        scheduler.addListener((jobId, newStatus) ->
            log.info("Job {} transitioned to {}", jobId, newStatus)
        );

        scheduler.start();

        // Concrete job — extends AbstractJob and provides execute()
        AbstractJob highPriority = new AbstractJob("model-ft-001", 1) {
            @Override
            public void execute() {
                // production: invoke ML training SDK / Kubernetes job API here
                log.info("Fine-tune job started: {}", getId());
            }
        };

        AbstractJob lowPriority = new AbstractJob("data-export-002", 5) {
            @Override
            public void execute() {
                // production: run data export pipeline here
                log.info("Data export started: {}", getId());
            }
        };

        scheduler.submit(lowPriority);
        // higher priority number is lower — model-ft-001 (priority=1) executes before data-export-002 (priority=5)
        scheduler.submit(highPriority);

        Thread.sleep(1000);
        scheduler.shutdown();
    }
}
```

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 2026 | Note created — TrueFoundry LLD prep. One-file format. All Java inline. |
