# 05 — The Drill

**Read time: 9 min.** Cover the right column. Say your answer **out loud**. Uncover. Compare.

Not about matching word-for-word — about whether you named **what breaks otherwise**.

---

## Round 1 — Interfaces and classes

| They ask | You say |
|---|---|
| "Why an interface here?" | "That's the variation point — the behaviour changes. Adding a new type is one new class and zero edits to existing code." |
| "Why not just an if-else?" | "To add a case I'd have to open and edit a method that already works and is already tested. That's the OCP violation — regression risk plus merge conflicts on the same lines." |
| "Why write the interface before the implementation?" | "If I write `EmailSender` first and extract later, the interface ends up shaped like email — SMS has to stub out methods it doesn't need." |
| "You only have one implementation. Why abstract it?" | "Two reasons: it's the testing seam so I can inject a fake, and the roadmap already has the second one. Otherwise I'd agree it's speculative." |
| "Isn't this over-engineering?" | "Fair challenge — I'd collapse it if [condition]. But the requirement says [X], and without it [specific thing] breaks. For contrast, I deliberately *didn't* create [Z] because it'd be a wrapper with no behaviour." |
| "Why is `ChannelType` an enum, not a class with `send()`?" | "Each channel needs its own dependencies — SendGrid client, Twilio client. Injecting those into an enum constant is awkward in Java and untestable. Separate classes keep DI clean." |
| "Why isn't there a `Car`/`Truck` hierarchy?" | "No behaviour varies by vehicle — a truck doesn't park differently, it needs a bigger spot. Subclasses with nothing overridden are ceremony. The sizing rule is data, not a hierarchy." |

---

## Round 2 — Relationships

| They ask | You say |
|---|---|
| "Composition or aggregation?" | "If I `new` it inside the constructor it's composition; if it arrives through the constructor it's aggregation." |
| "The service can't work without the router — isn't that composition?" | "No — that's a required *dependency*, not ownership. It's injected and shared, so aggregation. It'd only be composition if the service did `new DefaultChannelRouter(...)` internally, which would also kill mock injection in tests." |
| "Why does `Booking` store `roomId` instead of the room object?" | "Different lifecycles — rooms exist without bookings and get renovated independently. Embedding the object also drags room graphs into every booking query." |
| "Why is `JobRun` separate from `Job`?" | "A recurring job is one definition with many executions. Collapsed, the third run overwrites the second's outcome and I lose history — I can't answer 'did last night's run fail?'" |
| "Why is `Credential` separate from `User`?" | "Blast radius. Profile data is read on every request; the hash should only be read during authentication. Together, the hash rides along in every query, cache entry, and log line." |
| "`Job` has both `Schedule` and `RetryPolicy` — same relationship?" | "No, and that's deliberate. `Schedule` is composition — created with the job, dies with it. `RetryPolicy` is aggregation — backoff policies are shared singletons across thousands of jobs." |

---

## Round 3 — Patterns

| They ask | You say |
|---|---|
| "Why Strategy?" | "The algorithm varies at runtime. One implementation per variant, selected by key — no switch on the hot path." |
| "Isn't this just Observer?" | "There's an Observer flavour — one event fans out. But the interesting variation is *how* each channel delivers, which is Strategy. Pure Observer means the service holds subscriber state and I lose per-channel retry logic." |
| "Why a Repository if you have one database?" | "Persistence changes for different reasons than business logic — SRP. It's also the testing seam, and the schema already plans a hot/cold split that lands behind the same interface." |
| "Is `JobStatus` a State pattern?" | "It's an enum with a transition guard. The per-state behaviour lives in the executor, so State classes would be eight classes that only validate transitions. I'd promote it to real State the moment entering a state has side effects — like a webhook on FAILED." |
| "Why is pricing a strategy but availability isn't a class?" | "Pricing is the most volatile rule in the business — weekends, events, surcharges. Availability counters are a map with no behaviour; wrapping them would be ceremony." |
| "What if I asked for undo/redo?" | "That's Command — each action becomes an object with `execute()`/`undo()`, and you keep a stack. Strategy would be the wrong reach there." |

---

## Round 4 — Concurrency

| They ask | You say |
|---|---|
| "Two users book the same room at the same instant." | "Both pass the availability read — that read is never the guarantee. The write is guarded: per-room lock, re-check overlap **inside** the lock, insert. Plus a DB exclusion constraint underneath as the backstop." |
| "Why not `synchronized` on the method?" | "It serializes every request in the process through one monitor — the component becomes the bottleneck it exists to prevent. Per-key locking scopes contention to actual conflicts." |
| "Why per-room and not one lock?" | "Lock exactly the resource whose invariant you're protecting. Two people booking different rooms must never block each other." |
| "How do you guarantee exactly-once execution?" | "You can't, end to end, with at-least-once dispatch. A lease narrows the window; a GC pause past the TTL reopens it. Idempotent handlers keyed on `runId` are the only complete answer." |
| "Why a lease instead of an `isRunning` flag?" | "A flag can't survive the owner crashing — it stays true forever and the job is wedged until a human clears it. A lease has an owner *and* a TTL, so recovery is automatic." |
| "Why one Lua script instead of GET then SET?" | "Two round trips leave a race between them — both pods read `tokens=1`, both allow. Lua runs the whole refill-check-decrement atomically inside Redis in one hop." |
| "Optimistic or pessimistic locking?" | "Depends on conflict likelihood. Optimistic for rare-conflict single-row edits. Pessimistic for a popular room at 9am — optimistic there means 50 attempts, 49 failures, a retry storm at the spike." |
| "50 servers now — does your rate limiter still work?" | "No, and that's the point. In-process counters mean each pod enforces 100/min independently — the client gets 5,000/min. The system reports compliance while being wrong by 50x. State has to move behind `CounterStore` into Redis." |

---

## Round 5 — The traps

| They ask | You say |
|---|---|
| "Does a 10-11 booking conflict with 11-12?" | "No — half-open intervals `[start, end)`. Strict inequalities both sides. Closed intervals would reject every back-to-back meeting." |
| "How do you store passwords?" | "bcrypt or argon2, per-user salt, cost tuned to ~100ms, plus a pepper in a KMS. Never SHA — SHA is built to be *fast*, which is exactly the wrong property. Slowness is the feature." |
| "How do you log out a JWT?" | "You can't invalidate a stateless JWT — that's why access tokens are short-lived and the revocable state is the refresh token plus the session row. Worst case is one 15-minute window." |
| "What if Redis goes down?" | "Fail open for quota limits — the limiter must not be a bigger outage than the API it protects. But per-rule: login-attempt limits fail closed. Auth is where you don't relax under failure." |
| "What's your bottleneck?" | *(never say "the database")* — "At 35K/sec with 280ms blocked per notification, a 200-thread pool sustains ~714/sec. We're short by 49x." |
| "Any downsides to your design?" | *(never say "none")* — name the trade honestly, then the mitigation. |

---

## Final self-test — can you do this cold?

Pick any one of your six problems and say, in 60 seconds, without notes:

```
1. The three interfaces and what varies behind each
2. One composition and one aggregation, with the lifecycle reason
3. The main race and the lock scope you chose
4. One thing your design does NOT guarantee
5. One place you deliberately did NOT add an abstraction
```

If you can do that for **Notification, Job Scheduler, and Booking**, you're ready. Those three
cover Strategy, entity splitting, and concurrency — the three things most likely to be probed.

---

## Last thing before you walk in

> **When you don't know:** say *"I'm not certain — let me reason about it"* and think out loud.
> That scores better than inventing a benefit, because an invented benefit collapses on the
> very next follow-up. Calibrated confidence is a senior signal; overclaiming is a red flag.
