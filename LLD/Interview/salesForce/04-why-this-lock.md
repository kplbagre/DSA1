# 04 — Why This Lock?

**Read time: 12 min.** Salesforce always asks about concurrency. The question is never
"do you know what `synchronized` means" — it's **"did you pick the right mechanism, at the
right scope, and do you know what it still doesn't guarantee?"**

---

## The one race behind almost every question

**Check-then-act.** You read state, decide, then write — and something changed in between.

```java
if (spotIsFree(spot)) {      // <-- thread B also reads "free" right here
    assign(spot, vehicle);   // <-- both threads assign the same spot
}
```

**The sentence that answers most concurrency questions:**

> *"The read is never the guarantee. Both requests can pass the check. The guarantee has to
> be at the write — so I re-check **inside** the lock, or let the database arbitrate."*

Say this on booking (two users, same room), parking (two gates, same spot), scheduling
(two pollers, same job), and rate limiting (two pods, same counter). Same shape every time.

---

## Choosing the mechanism — the decision table

| Situation | Use | Why this one |
|---|---|---|
| Two app nodes grab the same DB row | `SELECT ... FOR UPDATE SKIP LOCKED` | DB arbitrates; `SKIP LOCKED` means node B takes the *next* rows instead of blocking behind A. Scales with replicas |
| Two workers might run the same job | Redis lease: `SET key owner NX PX ttl` | Atomic claim; **the TTL means crash recovery is automatic** — no human clears a stuck flag |
| Read-modify-write across the network | **One atomic Lua script** | Two round trips leave a race window between them; Lua runs inside Redis in one hop |
| In-process counter, high contention | CAS retry loop / `AtomicInteger` | Lock-free; `synchronized` would serialize every key through one monitor |
| Single-row edit conflict (two people edit one booking) | **version column** (optimistic) | Correct tool for *updates* — retry on conflict is cheap when conflicts are rare |
| Insert-conflict (overlapping ranges) | **DB constraint** (`EXCLUDE USING gist`) | Optimistic locking can't guard rows that **don't exist yet** — there's no version to compare |
| Two structures must agree (list + counter) | Both inside the **same** critical section | Otherwise the displayed count drifts from reality |

---

## Lock SCOPE — the senior signal

Almost everyone locks *something*. Fewer people justify **how much** they locked.

> *"Lock exactly the resource whose invariant you're protecting."*

| Problem | Scope chosen | Why not global |
|---|---|---|
| Booking | per **room** (`room:{id}`) | Two people booking *different* rooms must never block each other |
| Parking | per **level** | A 6-gate lot would behave like a 1-gate lot at rush hour |
| Rate limiter | per **key** (CAS, no lock) | A monitor on the limiter serializes every request in the process |
| Job scheduler | per **row** (`SKIP LOCKED`) | Pollers should take different work, not queue for the same work |

**If asked "why not `synchronized` on the method?"**
> *"That serializes every request in the process through one monitor — the component becomes
> the bottleneck it exists to prevent. It's correct but unusable. Per-key locking keeps
> contention scoped to actual conflicts."*

---

## Two-layer correctness (the strongest answer you have)

Booking is the best example. **Don't pick one mechanism — layer two, with different jobs.**

```sql
ALTER TABLE bookings ADD CONSTRAINT no_overlapping_bookings
EXCLUDE USING gist (
    room_id WITH =,
    tstzrange(start_time, end_time, '[)') WITH &&
) WHERE (status = 'CONFIRMED');
```

> *"The **lock** makes conflicts rare and gives a clean error path with details — 'room busy,
> here are three alternatives.' The **constraint** makes double-booking physically impossible
> even if the lock is bypassed by a bug, a deploy, or someone running direct SQL.
> If I had to delete one, I'd keep the constraint: the lock protects the user experience,
> the constraint protects the invariant. Only one of those is allowed to fail."*

That last sentence is the whole answer in one line.

---

## Say what it does NOT guarantee (calibrated confidence)

The single biggest scoring opportunity in concurrency. **Never claim exactly-once.**

> *"A lease narrows the double-execution window; it doesn't close it. If a worker
> GC-pauses past its TTL, the lease expires, another worker picks the job up, and both
> run. The only complete answer is **idempotent handlers** keyed on `runId` — the lease is
> a performance optimisation for correctness, not a correctness guarantee."*

Same shape elsewhere:
- Rate limiter: *"local token leases make enforcement approximate — bounded over-admission of `pods x leaseSize`. Fine for quotas, not for login attempts."*
- Offline parking gate: *"during a partition I may over-allocate. I'd rather over-allocate than trap a driver at a barrier."*

---

## Optimistic vs pessimistic — pick correctly

| | Optimistic (version column, retry) | Pessimistic (`FOR UPDATE`, lease) |
|---|---|---|
| Best when | conflicts are **rare** | conflicts are **likely** |
| Cost of conflict | wasted work + retry | waiting |
| Your usage | editing one booking's attendees | booking a popular room at 9am |

**The trap:** optimistic retry under heavy contention.
> *"With 50 people racing for the same room, optimistic means ~50 attempts and ~49 failures —
> a retry storm that amplifies load exactly at the spike. That's the classic
> optimistic-locking-under-high-contention failure."*

**The other trap:** optimistic locking guards **updates**. For overlapping-interval inserts,
there's no existing row and no version to compare — that's why booking needs a constraint.

---

## Why lease + TTL beats a boolean flag

> *"A `boolean isRunning` can't survive the owner crashing — it stays `true` forever and the
> job is wedged until a human clears it. A lease has an **owner and an expiry**, so recovery
> is automatic."*

Add the refinement if pushed:
> *"I'd add a heartbeat renewing at TTL/3, so long-running jobs don't lose a lease they're
> still legitimately holding."*

---

## Why one Lua script instead of GET then SET

> *"`GET` then `SET` is two round trips. Between them, another pod can write — both read
> `tokens = 1`, both allow. The Lua script executes the whole refill-check-decrement
> atomically inside Redis: one hop, no window."*

Bonus detail that shows depth:
> *"I pass `now` into the script rather than reading the clock inside it, so the script stays
> deterministic — that matters for replication safety."*

---

## Thread-safety patterns in your Java

| Technique | Where | Why |
|---|---|---|
| **Immutability** | `TimeInterval`, `RateLimitDecision`, `RateLimitRule` | Thread-safe by construction — no locking needed at all |
| **Effectively immutable** | registry maps built in constructor, never mutated | Beats locking; no synchronization required |
| `ConcurrentHashMap` | only when hot-registration is genuinely required | Say *why* you need it, or prefer immutable |
| `AtomicInteger` | availability counters | Lock-free increment |
| `ReentrantLock` | per-level in parking | Needed because two structures must move together |
| CAS retry loop | token bucket | Optimistic, lock-free, degrades gracefully |

> *"Immutability is the cheapest concurrency strategy — if it can't change, it can't race."*

---

## The four-part answer template

```
1. NAME THE RACE       "Check-then-act between the availability read and the insert."
2. THE MECHANISM       "Per-room SELECT ... FOR UPDATE, re-checking inside the lock."
3. THE SCOPE + WHY     "Per room, not global — different rooms must not block each other."
4. WHAT REMAINS        "And a DB exclusion constraint underneath, because app locks fail
                        during deploys. Even then, handlers must be idempotent."
```

---

## The 30-second recap

- The universal race is **check-then-act**; the read is never the guarantee
- **Scope the lock to the invariant's resource** — per-room, per-level, per-key, never global
- **Layer two mechanisms**: a lock for clean UX, a DB constraint for undefeatable correctness
- Optimistic for rare-conflict **updates**; pessimistic for likely conflicts; **constraints for insert-conflicts**
- Lease + TTL beats a boolean because crash recovery becomes automatic
- Two network round trips = a race; **one atomic script** = no window
- **Always state what it still doesn't guarantee** — that's the senior signal
