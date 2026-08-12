# 00 — Cheatsheet (the 10-minutes-before read)

---

## The method, in six moves

```
1. CLARIFY    2 min   4 questions, each one forking the design
2. DERIVE     4 min   requirement -> noun/verb -> class -> why it earns its own type
3. INTERFACE  4 min   every variation point becomes an interface, before any impl
4. RELATE     3 min   IS-A / HAS-A / USES; every HAS-A -> composition or aggregation
5. JUSTIFY    6 min   each choice vs its STRONGEST alternative
6. CONCURRENCY 4 min  name each race, the fix, the lock SCOPE, and what's still not guaranteed
```

---

## Ownership heuristic (the most-asked follow-up)

> **"If I `new` it inside the constructor, it's composition. If it arrives through the
> constructor, it's aggregation."**

| | Composition (filled diamond) | Aggregation (hollow diamond) |
|---|---|---|
| Who creates it | The whole | Someone else |
| Lifetime | Dies with the whole | Outlives the whole |
| Shared? | Never | Often (singletons) |
| Example | `Booking` HAS-A `TimeInterval` | `Job` HAS-A `RetryPolicy` |

**The trap:** *"it can't function without it"* = **required dependency**, NOT ownership.
A mandatory constructor-injected collaborator is still **aggregation**.

**USES** = calls methods on it, holds no meaningful state (a collaborator, not a part).

---

## Pattern → trigger (what makes you reach for it)

| Pattern | Trigger phrase in the requirements | Example |
|---|---|---|
| **Strategy** | "supports multiple X" / "configurable" / behavior varies | `NotificationSender`, `RateLimiter`, `Schedule`, `PricingStrategy` |
| **Registry** | need to pick an impl by key, without a `switch` | `Map<Type, Handler>` built from an injected `List` |
| **Repository** | "persist" / "query by" | `JobRepository.findDue()` |
| **Value Object** | a rule or tuple that travels together, immutable | `TimeInterval`, `RateLimitDecision` |
| **Composite** | criteria/filters combine | `RoomFilter` + `AndFilter` |
| **Factory** | rebuild a typed object from stored data | `ScheduleFactory.from(type, expr, tz)` |
| **Chain / Specification** | ordered fallback, or per-tenant rule sets | `PasswordRule`, most-specific-wins resolver |
| **Aggregate separation** | different read frequency or blast radius | `User` vs `Credential` |
| **Lease (lock + TTL)** | "only one owner at a time" + crash recovery | `LeaseManager` |
| **State (as guarded enum)** | lifecycle with illegal transitions | `JobStatus.canTransitionTo()` |

**Enum, not a class, when:** the set is closed and carries no behavior.
**Interface, not a field, when:** the behavior varies — that's the definition of a variation point.

---

## Concurrency: pick the right tool

| Situation | Tool | Why |
|---|---|---|
| Two pollers grab the same DB row | `SELECT ... FOR UPDATE SKIP LOCKED` | DB arbitrates; B takes the next rows instead of blocking |
| Two nodes claim the same job | Redis lease `SET k owner NX PX ttl` | Atomic; the TTL self-heals when the owner dies |
| Read-modify-write over the network | **One atomic Lua script** | Two round trips leave a race window |
| In-process counter under contention | CAS retry loop / `AtomicInteger` | Lock-free; `synchronized` serializes everything |
| Single-row edit conflict | version column (optimistic) | Right tool for updates |
| Insert-conflict (overlap ranges) | **DB constraint** (`EXCLUDE USING gist`) | Optimistic locking can't guard rows that don't exist yet |
| Structure + counter must agree | both inside the **same** critical section | Otherwise the displayed count drifts from reality |

**Always state the lock SCOPE and why:** per-room, per-level, per-key — never global.
> *"Lock exactly the resource whose invariant you're protecting."*

**The universal race:** *check-then-act*. Reading availability is never the guarantee —
re-check **inside** the lock.

---

## Numbers worth remembering

| Thing | Number | Why it matters |
|---|---|---|
| bcrypt (cost 12) | ~100 ms CPU **per login** | 100K logins/sec = 10,000 cores |
| Redis simple op | ~0.2-0.5 ms | Blows a sub-1ms budget in one round trip |
| Redis Lua evals | ~100K/sec | Above that, shard |
| Postgres single-node writes | ~2-5K/sec | Beyond it, queue or shard |
| In-process limiter on N pods | limit **× N** | The classic silent 50x breach |

---

## Ten sentences that score

1. *"The justification is the scored part, not the class name."*
2. *"If I `new` it inside, composition; if injected, aggregation."*
3. *"That's a required dependency, not ownership — so aggregation."*
4. *"This is the variation point, so it becomes an interface."*
5. *"I'm deliberately NOT creating a class here — it'd be a wrapper with no behavior."*
6. *"The read is never the guarantee; the re-check inside the lock is."*
7. *"Lock scope is per-X, not global — lock exactly the invariant's resource."*
8. *"Two round trips leave a race; the Lua script makes it one atomic hop."*
9. *"A lease narrows the window, it doesn't close it — idempotent handlers are the only complete answer."*
10. *"To add a new type: one new class, zero edits elsewhere."*

---

## Opening protocol (say this first, always)

> "Before I start — should I do LLD first or HLD first, or do you have a preference?"
>
> *(no preference:)* "I'll start with LLD — class design, interfaces, concurrency. Then zoom
> out to the distributed system. I'll flag the transition explicitly so you can redirect me."

---

## Red flags — never say these

| Don't say | Say instead |
|---|---|
| "I'd use exactly-once delivery" | "At-least-once + idempotent handlers = effectively once" |
| "SHA-256 the password" | "bcrypt/argon2 — slowness is the feature" |
| "JWTs can be revoked" | "Short-lived JWT + revocable refresh token" |
| "`synchronized` the method" | "Lock per-key; a global monitor serializes everything" |
| "It's more modular/cleaner" | Name the concrete failure if inlined |
| "The DB will be a bottleneck" | "~2K writes/sec, we need 8K — short by 4x" |
| "No downsides" | Every trade-off has a cost; name it |

---

## The 60-second close

> *"Three interfaces carry the variation — [X], [Y], [Z]. [Entity A] is separate from
> [Entity B] because [lifecycle/blast-radius reason]. Concurrency is handled with [mechanism]
> scoped per-[resource], with [backstop] underneath, and the honest limit is [what it doesn't
> guarantee]. At system level those classes become [services], and the bottleneck is
> [quantified breaking point], fixed with [mitigation]."*
