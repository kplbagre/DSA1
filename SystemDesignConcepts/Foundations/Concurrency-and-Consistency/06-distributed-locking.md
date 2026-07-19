# Distributed Locking

---

## 🎯 Why This Matters

When multiple servers race to perform the same critical operation (booking the last seat, charging a payment once), a single mutex is not enough — each server has its own memory. Distributed locking is the mechanism that gives a cluster of servers the same "only one at a time" guarantee that a local mutex gives a single process. It appears in senior-level system design rounds whenever you design anything with inventory, payments, or exactly-once execution. A senior engineer is expected to know the mechanics — not just "use Redis" — because the failure modes (deadlock, stale lock, split-brain) are the probe questions.

---

## 📖 What is Distributed Locking?

**Full form:** Distributed Lock / Mutex across Multiple Servers

**Simple analogy:** A hotel with 10 receptionists (servers). To prevent double-booking the same room, they check a **master booking board** (Redis). Only one receptionist can mark a room as "OCCUPIED" at a time. The booking has a time limit (TTL); if the receptionist never checks out, the room auto-releases after 30 seconds.

**Core principle:** In a cluster of servers, a single mutex per server is not enough — different servers can't enforce each other's locks. A distributed lock uses a shared, centralized service (Redis, Zookeeper, etcd) to ensure only ONE server executes critical code at a time, even though multiple servers might try simultaneously.

**Why it matters in system design:** Prevents double-booking inventory, duplicate charges, race conditions on inventory updates, and ensures exactly-once execution in distributed systems.

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|------|----------------------|---------|
| **Distributed Lock** | a lock enforced by a shared external service (not in-process); all servers respect the same lock | Redis lock `seat:45A` — only one of 10 booking servers can hold it at a time |
| **SETNX** | Redis "SET if Not eXists" — atomic command that creates a key only if it doesn't already exist; the primitive behind Redis locking | `SETNX lock:seat45A serverA` — returns 1 (acquired) or 0 (already held) |
| **TTL (Lock Expiry)** | time-to-live on the lock key; auto-releases the lock if the holder crashes before unlocking | `SET lock:seat45A serverA EX 30` — lock expires in 30s even if server A dies |
| **Fencing Token** | monotonically increasing number issued with each lock acquisition; used to reject stale lock holders | lock #5 acquired by A → A crashes → lock #6 acquired by B → A wakes up, sends token #5 → DB rejects (stale) |
| **Redlock** | multi-node Redis locking algorithm; acquires lock on majority of N Redis nodes to tolerate single-node failure | acquire on 3 of 5 Redis nodes; lock valid only if majority responds before timeout |
| **Lease** | time-bounded lock; holder must renew it before expiry or it's automatically released | lock lease = 30s; holder pings every 10s to renew; stops pinging (crash) → auto-release |
| **Lock Contention** | multiple servers competing for the same lock; can cause retry storms if not managed | 1000 servers retry immediately on lock failure → thundering herd → Redis overloaded |
| **Split-Brain** | network partition causes two servers to each believe they hold the lock simultaneously | avoided by Redlock (majority quorum) and fencing tokens (stale lock rejected by DB) |
| **Stale Lock** | lock not released because holder crashed before unlocking; TTL + fencing tokens are the defense | server A holds lock, JVM pauses 60s (GC), TTL expires, server B takes lock, A resumes — stale |

---

## 🎨 Visual — System Topology: Distributed Locking in Architecture

```
MULTIPLE SERVERS / Clients (booking requests)
    │
    ├─ Server A: "Book room 201"
    ├─ Server B: "Book room 201"
    ├─ Server C: "Cancel room 201"
    └─ Server D: "Check availability"
             │
             ▼
┌──────────────────────────────────────┐
│ Distributed Lock Service             │
│ (Redis / Zookeeper / etcd)           │
│                                      │
│ ┌──────────────────────────────────┐ │
│ │ Lock: room:201                   │ │
│ │ owner: Server A (fence token=5)  │ │
│ │ TTL: 30 seconds                  │ │
│ │ (auto-expires if holder crashes) │ │
│ └──────────────────────────────────┘ │
│                                      │
│ Decision:                            │
│ - Server A: ✅ Acquire lock          │
│ - Server B: ⏳ Wait (retry)          │
│ - Server C: ⏳ Wait (retry)          │
│ - Server D: ✅ Read (no lock needed) │
└──────────────┬───────────────────────┘
               │
    ┌──────────┴──────────────┐
    │                         │
    ▼                         ▼
Shared Database        Booking Log
(Protected resource)   (audit trail)

KEY INVARIANT:
   Only ONE server can hold lock at a time
   Lock has TTL (auto-release on crash)
   Lock owner identified by token (prevents stale releases)
```

---

## 🎨 Visual — Distributed Lock Flow with Fencing Token (Component Detail)

Imagine a hotel with 500 rooms managed by 10 receptionists at separate desks. A guest (request) walks up to **any** receptionist and asks for Room 201. The receptionist checks the master booking board, marks Room 201 **OCCUPIED**, and hands the guest a **key card** with a unique booking code stamped on it (e.g., "Booking #8843").

The key card has an **auto-expiry time** (TTL — time-to-live): if the guest doesn't check out by 11 AM, the room is automatically released, even if the guest vanished overnight. This prevents the hotel from running out of rooms because one guest forgot to leave — the equivalent of a server crash that holds a lock forever.

Now, a sneaky situation: a slow housekeeper (Housekeeper A) was sent to clean Room 201 with a key card from yesterday's booking (Booking #8840). While she was stuck in traffic, Room 201 was released, re-booked (Booking #8843), and a new guest has already moved in. When Housekeeper A arrives, the door system checks: "your booking number is 8840, the room currently holds booking 8843 — you are **older**, you don't have authority". She is turned away. That booking number is a **fencing token** — a monotonically increasing counter that prevents a stale lock holder from wrecking current work.

What if the hotel's master booking board is destroyed in a fire (Redis node crash)? You have two choices: (1) accept that Room 201 might get double-booked until the board is restored (single-node Redis risk), or (2) require **3 out of 5** independent booking boards to agree before marking the room occupied (Redlock — multi-node quorum). Choice (2) is slower but survives the crash of any two boards.

**The key insight is:** a distributed lock must expire automatically (TTL), be identifiable (fencing token), and be released only by its owner — otherwise a crashed server creates a deadlock that outlives its TTL, or a slow server revokes someone else's lock.

---

## 🎨 Visual — SETNX lock flow with fencing token

```
CLIENT A                     REDIS                         DATABASE
   │                            │                               │
   │── SETNX lock:room:201 ───► │                               │
   │   value="A:fence=1"        │ Key doesn't exist             │
   │   EX 30 (TTL=30s)          │ → SET succeeds                │
   │◄── "1" (lock acquired) ─── │                               │
   │                            │                               │
   │  [does work: reserve seat] │                               │
   │────────────────────────────────────────────────────────────►
   │                            │                 write(fence=1) │
   │◄─────────────────────────────────────────── ack ────────── │
   │                            │                               │
   │── DEL lock:room:201 ──────►│  [ONLY if value="A:fence=1"]  │
   │   (safe release with GET)  │  → key deleted                │
   │◄── ok ──────────────────── │                               │


CLIENT B (concurrent attempt)
   │                            │
   │── SETNX lock:room:201 ───► │ Key EXISTS (A holds it)
   │◄── "0" (lock not acquired) │
   │  [retry after backoff]     │


WHAT HAPPENS IF CLIENT A CRASHES (TTL saves us):
   │  ← A crashes here →       │
   │                            │ ... 30 seconds pass ...
   │                            │ Redis auto-expires the key
   │                            │ Room 201 is now lockable again
CLIENT B (retry):
   │── SETNX lock:room:201 ───► │ Key gone → SET succeeds
   │◄── "1" (lock acquired) ─── │


KEY INVARIANT:
   Lock token must be unique per holder (owner=A, fence=N).
   Release must be conditional — only the exact owner can delete.
   TTL is the safety net for owner crashes.
```

---

## ⚙️ How It Actually Works

### Strategy 1 — Single-Node Redis Lock (SETNX + TTL)

**Steps in plain English:**

1. **Acquire** — issue a single atomic `SET key value NX EX seconds` command. `NX` means "only set if not exists." If it returns OK, you hold the lock. If it returns nil, someone else holds it — retry with exponential backoff.
2. **Do work** — perform the critical section while holding the lock. Keep track of your own token (owner ID + fencing counter).
3. **Release safely** — use a Lua script to check that the stored value matches your token *before* deleting. This prevents releasing another thread's lock.

```java
import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SetParams;

public class RedisDistributedLock {

    private final Jedis jedis;
    // TTL — time-to-live in seconds; prevents deadlock if owner crashes
    private static final int TTL_SECONDS = 30;

    // Lua script: check owner token, then delete atomically
    // GET + DEL as two commands would have a race window — Lua makes it atomic
    private static final String RELEASE_SCRIPT =
        "if redis.call('get', KEYS[1]) == ARGV[1] then " +
        "    return redis.call('del', KEYS[1]) " +
        "else " +
        "    return 0 " +
        "end";

    public RedisDistributedLock(Jedis jedis) {
        this.jedis = jedis;
    }

    // Step 1 — acquire: SET NX EX atomically
    public boolean acquire(String lockKey, String ownerToken) {
        SetParams params = SetParams.setParams().nx().ex(TTL_SECONDS);
        String result = jedis.set(lockKey, ownerToken, params);
        return "OK".equals(result);
    }

    // Step 3 — release: Lua script ensures only owner can release
    public boolean release(String lockKey, String ownerToken) {
        Object result = jedis.eval(
            RELEASE_SCRIPT,
            1,
            lockKey,
            ownerToken
        );
        return Long.valueOf(1L).equals(result);
    }
}
```

```java
// Caller — acquire with retry, do work, release
public class SeatReservationService {

    private final RedisDistributedLock lock;
    private final SeatRepository seatRepo;

    public SeatReservationService(RedisDistributedLock lock, SeatRepository seatRepo) {
        this.lock = lock;
        this.seatRepo = seatRepo;
    }

    public boolean reserveSeat(String showId, String seatId, String userId) throws InterruptedException {
        String lockKey = "lock:seat:" + showId + ":" + seatId;
        // ownerToken is unique per caller — UUID ensures no two holders share a token
        String ownerToken = java.util.UUID.randomUUID().toString();
        int maxRetries = 5;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            if (lock.acquire(lockKey, ownerToken)) {
                try {
                    // Step 2 — critical section: safe because we hold the lock
                    if (seatRepo.isAvailable(showId, seatId)) {
                        seatRepo.reserve(showId, seatId, userId);
                        return true;
                    }
                    return false;
                } finally {
                    // Step 3 — always release in finally block
                    lock.release(lockKey, ownerToken);
                }
            }
            // Exponential backoff — avoid thundering herd on lock contention
            Thread.sleep((long) Math.pow(2, attempt) * 50);
        }
        throw new RuntimeException("Could not acquire lock after " + maxRetries + " attempts");
    }
}
```

---

### What is SETNX, and why does it fit here?

**SETNX** stands for "SET if Not eXists." It is a Redis command (now unified into `SET key value NX EX seconds`) that writes a key only when the key is absent — if the key already exists, it does nothing and returns nil.

**Why it fits distributed locking:** A lock is precisely "this slot should only be held by one holder at a time." SETNX's conditional write is an atomic check-and-set in a single round-trip to Redis — no two callers can both succeed simultaneously because Redis is single-threaded per command.

**In an interview, if asked:** "SETNX is Redis's atomic 'write only if empty' command — because Redis executes each command single-threaded, two clients racing on the same key will serialize; exactly one gets OK and the other gets nil, giving us mutual exclusion without any extra locking inside Redis itself."

---

### What is a Fencing Token, and why does it fit here?

A **fencing token** (coined by Martin Kleppmann) is a monotonically increasing number issued with each lock acquisition. The holder passes this number to downstream systems (e.g., the database) with every write. Downstream systems reject any write carrying a token **lower than the highest token they have seen**.

**The problem it solves:** A lock holder (Client A) pauses for 35 seconds (GC pause, page fault, slow network). Its TTL expires, the lock is re-issued to Client B (token = 2). Client A resumes and tries to write — but it's now using a stale lock. Without a fencing token, Client A's write succeeds and corrupts Client B's work. With a fencing token, the database rejects Client A's write (token 1 < current 2).

**In an interview, if asked:** "A fencing token is a monotonically increasing counter attached to each lock grant — the downstream resource rejects any operation carrying a token older than what it last processed, so a GC-paused or network-delayed former lock holder can't corrupt work done by the new lock holder."

---

### Strategy 2 — Redlock (Multi-Node Redis)

**The problem with single-node:** the canonical failure is **master–replica async-replication failover**. Redis replication is asynchronous, so this sequence loses mutual exclusion: (1) Client A acquires the lock on the master; (2) the master crashes *before* replicating that key to its replica; (3) the replica is promoted to master — and it never received the lock key; (4) Client B acquires the "same" lock on the new master. Now A and B both believe they hold it → two holders, data corruption. (A single Redis node with no replica avoids this specific race but is itself a SPOF.)

**Redlock** solves this by requiring a **quorum of independent Redis nodes** (N=5, quorum=3) to all agree on the lock before it is considered held.

**Steps in plain English:**

1. **Try to acquire** on all N Redis nodes in parallel, with a short per-node timeout.
2. **Count successes.** If you acquired the lock on at least ⌊N/2⌋+1 nodes (quorum), you hold the lock.
3. **Verify remaining TTL.** The effective lock validity = original TTL − time spent acquiring. If it's ≤ 0, release everything and retry — you were too slow.
4. **Do work** within the remaining validity window.
5. **Release** on ALL N nodes (even ones that rejected you — cleans up partial state).

```java
// Simplified Redlock concept — production use: Redisson library
public class Redlock {

    private final List<RedisDistributedLock> nodes;
    private static final int LOCK_TTL_MS = 10_000;
    // quorum: majority of nodes must agree
    private final int quorum;

    public Redlock(List<RedisDistributedLock> nodes) {
        this.nodes = nodes;
        this.quorum = nodes.size() / 2 + 1;
    }

    public boolean acquire(String lockKey, String ownerToken) {
        long startMs = System.currentTimeMillis();
        int acquired = 0;

        // Step 1 — try all nodes in parallel (simplified to sequential here)
        for (RedisDistributedLock node : nodes) {
            if (node.acquire(lockKey, ownerToken)) {
                acquired++;
            }
        }

        long elapsed = System.currentTimeMillis() - startMs;
        long remaining = LOCK_TTL_MS - elapsed;

        // Step 2+3 — quorum reached AND TTL still has time left?
        if (acquired >= quorum && remaining > 0) {
            return true;
        }

        // Step 5 — release everywhere (cleanup failed acquisition)
        release(lockKey, ownerToken);
        return false;
    }

    public void release(String lockKey, String ownerToken) {
        // Step 5 — release on ALL nodes, not just the ones that succeeded
        for (RedisDistributedLock node : nodes) {
            node.release(lockKey, ownerToken);
        }
    }
}
```

### What is Redlock, and why does it fit here?

**Redlock** is a distributed lock algorithm designed by Salvatore Sanfilippo (creator of Redis) that runs across multiple **independent** Redis nodes. "Independent" is the key word — they share no replication, so a crash on one node does not propagate.

**Why quorum matters:** If 3 of 5 nodes independently agree the lock is yours, even if 2 nodes crash simultaneously, the lock is still held safely by the surviving majority.

**In an interview, if asked:** "Redlock acquires a lock on a majority of independent Redis nodes — the logic is that if any single node crashes, the others still represent a quorum. The effective TTL shrinks by the time spent on acquisition, so if acquiring takes too long, you release and retry rather than claiming a lock that's almost expired."

**Important caveat (Tier 2 depth):** Martin Kleppmann wrote a critique arguing Redlock is still unsafe under certain clock-skew and GC-pause scenarios. His deeper point: if you add **fencing tokens at the storage layer** (the thing that actually guarantees correctness), then Redlock's 5-node quorum buys you little — a single lock service + fencing tokens is already safe, so Redlock occupies an awkward middle ground. He also notes Redis can't produce reliably-monotonic fencing tokens (non-durable, wall-clock dependent), whereas ZooKeeper's `zxid` is a natural one. Practical rule: use a single Redis node for *efficiency* locks (a rare double-execution is merely wasteful), and fencing tokens (or ZooKeeper) for *correctness* locks (double-execution corrupts data).

---

## 🏢 Real World — Where Companies Use This

- **BookMyShow** (seat reservation): When 10,000 users hit "Book Now" for the last 5 seats of a sold-out concert, a distributed lock on each seat ID ensures exactly one user per seat gets confirmed — no double booking.
- **PhonePe / Razorpay** (payment deduplication): Distributed lock on `payment:{transactionId}` prevents two retry-triggered payment requests from both succeeding — the second acquire fails and returns the cached result from the first.
- **Amazon** (inventory reservation): During flash sales, locks on inventory slots prevent overselling while the DB write completes. Combined with fencing tokens at the DB layer to survive GC pauses in JVM-based services.
- **Uber** (driver assignment): Assigning a driver to a ride is a critical section — two dispatch servers must not assign the same driver to two rides simultaneously. A Redis lock on `driver:{driverId}` serializes assignments.
- **Zomato** (order assignment to delivery partner): Same pattern — `partner:{partnerId}` locked for the duration of order confirmation to prevent double-assignment.

---

## 🧭 When to Use vs When NOT to Use

| Use this when | Do NOT use when |
|---|---|
| Multiple servers must serialize access to a shared external resource (DB row, file, inventory slot) | Only one server runs the critical section — use a local synchronized block instead |
| An operation must complete **before** another begins on the same resource (strict ordering) | Conflict is rare and rows are short-lived — optimistic locking (compare-and-swap) is cheaper |
| You need cross-process mutual exclusion (not just cross-thread) | The resource itself supports atomic operations (e.g., Redis INCR for counters) — use the resource's own atomicity |
| The TTL of the lock is predictable and significantly shorter than the operation time | Operations can take unbounded time (file processing, ML inference) — TTL will expire mid-work |

**The common mistake:** Setting TTL too short. If the critical section takes 5 seconds and TTL is 3 seconds, the lock expires mid-work, a second server acquires it, and you have two concurrent writers. TTL must be at least 2–3× the expected operation time with headroom for GC pauses.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Cross-server mutual exclusion — exactly one process at a time performs the critical section, regardless of how many servers are in the cluster |
| **You lose** | Latency (one extra Redis round-trip per operation), operational complexity (Redis becomes a dependency for correctness, not just performance), and risk of deadlock if TTL is miscalibrated |
| **Failure mode** | Clock skew or GC pause causes the lock holder to believe it still holds the lock after TTL has expired, allowing a second holder to acquire it — both run the critical section simultaneously. Fix: fencing tokens at the resource layer to reject stale writers |

---

## 🔬 Interview Q&As

### Q: "What is a distributed lock and why can't you just use a database row for this?"

> A distributed lock is a mutual exclusion primitive that works across multiple processes/servers — it serializes access to a shared resource in a cluster. A DB row can work as a lock (advisory lock or a `locked_by` column with a unique constraint), but it's much slower than Redis (milliseconds vs microseconds per acquire/release), adds load to the primary database during contention storms, and doesn't offer TTL-based auto-expiry out of the box. For high-throughput locking (BookMyShow selling 50K seats in seconds), Redis is the right tool.

---

### Q: "Why do you need TTL on a distributed lock?"

> If the process holding a lock crashes after acquiring it — before it can release — the lock is permanently held. No other process can ever acquire it. TTL (time-to-live) ensures the lock auto-expires even if the holder vanishes, turning a permanent deadlock into a temporary unavailability. The TTL must be longer than the expected critical-section duration, with headroom for GC pauses.

---

### Q: "What is the difference between SETNX and SET NX EX? Why does it matter?"

> The original `SETNX` command set the key but did not support expiry in a single atomic command — you had to call `SETNX` then `EXPIRE` as two separate commands. The race window between them (server crashes after SETNX but before EXPIRE) leaves a lock with no TTL — a permanent deadlock. `SET key value NX EX seconds` is a single atomic command that sets the key **and** the expiry together — no race window. Always use the unified `SET NX EX` form.

---

### Q: "What is a fencing token and when do you need it?"

> A fencing token is a monotonically increasing number attached to each lock grant. The resource (DB, storage) rejects any write carrying a token lower than the highest token it has processed. It's needed when a lock holder can be paused (JVM GC, slow network) past its TTL — the lock expires, a new holder gets a higher-numbered token, but the old holder wakes up and tries to write with a stale token. Without fencing, the stale write corrupts work done by the new holder. With fencing, the resource itself detects and rejects the stale write.

---

### Q (Tier 2): "Your lock TTL is 30 seconds. Your operation takes 25 seconds. A GC pause of 35 seconds hits the JVM mid-operation. What happens and how do you fix it?"

> The JVM stops for 35 seconds, the lock's 30s TTL expires, a second server acquires the lock (now fencing token = 2), completes its work, and releases. The first server resumes, still believes it holds the lock (it doesn't), and tries to write with fencing token = 1. **Without fencing tokens:** the write succeeds, corrupting the second server's work. **With fencing tokens at the DB layer** (the DB rejects writes with token ≤ 1 since it already processed token = 2): the stale write is rejected, and the first server gets an error it can handle (log and abort). The fix has two parts: (1) add fencing tokens, and (2) consider reducing GC stop-the-world pauses (G1GC tuning, ZGC in Java 15+).

---

### Q (Tier 2): "Why not just use optimistic locking (database version column) instead of a distributed lock?"

> Optimistic locking works well when conflicts are rare — you read a row (version=3), do work, then UPDATE where version=3. If two threads raced, one fails and retries. But optimistic locking breaks down when: (a) the critical section involves **multiple tables or external systems** (not just one row), (b) **conflict probability is high** (flash sales — 10K users for 5 seats means 99.95% of attempts fail a version check, causing a retry storm), or (c) the work **cannot be rolled back cheaply** (e.g., an external payment API call). Distributed locks serialize upfront so you only do work once; optimistic locking does work speculatively and discards it on conflict. For seat booking and payment processing, the cost of speculative work that gets thrown away is too high.

---

### Q (Tier 2): "How does Redlock handle the case where one Redis node is down during acquisition?"

> Redlock requires ⌊N/2⌋+1 nodes to agree (quorum). With N=5 and one node down, you need 3 of 4 remaining nodes to agree — that still works. With two nodes down, you have 3 remaining and need 3 — quorum is exactly met, so acquisition succeeds but has zero fault tolerance. With three nodes down (minority surviving), acquisition fails — the lock is unavailable rather than incorrectly granted. This is the **CP trade-off**: availability degrades gracefully as nodes fail, but correctness is never sacrificed. If availability is critical, fall back to single-node Redis with fencing tokens at the storage layer (simpler but less durable).

---

### Q (Tier 2): "How does ZooKeeper implement a distributed lock, and why is it considered safer than Redis for correctness locks?"

> Each client creates an **ephemeral sequential znode** under a lock path, e.g. `/lock/req-`, and ZooKeeper appends a monotonic counter → `/lock/req-0000000017`. The client lists the children; **the lowest sequence number holds the lock.** A waiter doesn't poll — it sets a **watch on the single znode immediately before it** in the ordering, so it's notified exactly when its predecessor releases (this avoids the "herd effect" of everyone watching the lock). Why it's safer than Redis for correctness: (1) the znode is **ephemeral** — tied to the client's session, so if the client crashes or its heartbeat lapses, ZooKeeper deletes the node and the lock releases automatically (liveness via session, not a guessed TTL); (2) the **sequence number is a natural monotonic fencing token** (like `zxid`) you can pass to the resource layer to reject stale writers; (3) acquisition is decided by consensus (ZAB) across a quorum, not by async replication that can lose the key on failover. The trade-off: ZooKeeper has higher operational overhead and lower throughput than a single Redis node. etcd offers a similar model via leases + compare-and-swap on the key's mod-revision.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> "Distributed locking uses Redis SETNX with TTL (auto-expiry on crash) and a Lua-script conditional release (only the owner can unlock) — for multi-node safety, Redlock requires a quorum of independent Redis nodes, and fencing tokens at the resource layer protect against stale writers who wake up after their TTL has expired."

---

## 🔗 Related Concepts

- **`01-optimistic-pessimistic-locking.md`** — DB-layer locking strategies; optimistic locking is often the right alternative when conflicts are rare
- **`04-idempotency.md`** — idempotent consumers are how you handle at-least-once delivery when a lock expires mid-operation
- **`03-caching.md`** — Redis is both the lock store and the cache layer; understanding Redis internals connects these two concepts
- **`07-cdc-outbox.md`** — outbox pattern combined with distributed locking ensures exactly-once message publication in concurrent environments

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **"Distributed Locks with Redis"** — Arpit Bhayani (YouTube: "Arpit Bhayani distributed locking") | Deep dive on Redis-based locking internals — deadlock prevention mechanics, implementation patterns beyond this note | ~30 min |
| **"Distributed Locking"** — hellointerview.com (https://www.hellointerview.com/learn/system-design/deep-dives/redis) | Redis SETNX, Redlock algorithm, fencing tokens in interview-aligned format with trade-off analysis | ~15 min read |
| **"How to do distributed locking"** — Martin Kleppmann's blog | The critique of Redlock — essential Tier 2 depth on why clock skew breaks safety guarantees | ~20 min read |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. Covers: SETNX + TTL, safe release with Lua script, fencing tokens, Redlock quorum algorithm, when to use vs optimistic locking. 7 Q&As (4 Tier 1 + 3 Tier 2). |
| Jul 19, 2026 | **Clarity fix + gap.** (1) Rewrote the "problem with single-node" motivation for Redlock — the original conflated a single-node crash with a replica serving a new owner; corrected to the canonical master–replica async-failover race. (2) Strengthened the Kleppmann caveat (fencing tokens make Redlock's quorum largely redundant for correctness; Redis can't produce monotonic fencing tokens). (3) Added a ZooKeeper ephemeral-sequential-znode lock Q&A (predecessor-watch, session-based liveness, zxid as natural fencing token) — previously ZooKeeper was name-dropped but never explained. |
