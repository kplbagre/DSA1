# JPMorgan SDE-3 — Post-DSA Rounds: Deep Research (Aug 2026)

> **Context:** You have cleared R1 and R2 (DSA-heavy). The next stage is design + behavioral.
> **Sibling file:** `01-round3-prep-guide.md` (same folder) has the problem practice list,
> Java code templates, and HLD diagrams. This file is the independent research pass — what
> first-hand accounts from the last 12–15 months specifically say about this stage.
>
> **Source window:** Aug 2025 – Aug 2026 (primary). Jun 2024 entries are labeled
> ⏪ **out-of-window** — included only when the data is unique and unavailable elsewhere.
>
> **Honest caveat:** JPMC's format is team-dependent and NOT standardized. "Round 3" for one
> candidate is another's "Round 2." This doc uses the label **post-DSA stage** to reflect
> that reality. Every account is tagged by source confidence.
>
> **Source confidence labels:**
> - ✅ **First-hand** = directly attributed account (LeetCode Discuss, Medium, Fishbowl, Glassdoor)
> - 🔶 **Inferred** = consistent with multiple first-hand accounts but not directly quoted
> - ❌ **Generic** = prep-blog content (Educative, devcom, etc.) — excluded from problem tables

---

## 🧠 §1 — The Format Reality (Read First)

**There is no single "JPMC Round 3."** The SuperDay structure varies significantly by team,
hiring cohort, and location. Here is what first-hand accounts show:

```
FORMAT A — 3-round SuperDay (most common, backend Java roles)
─────────────────────────────────────────────────────────────
Round 1 │ DSA / Coding  (LC Easy-Medium, HackerRank)
Round 2 │ System Design (HLD or LLD or both sequentially)
Round 3 │ Behavioral / HM

Example: SE3 Bengaluru (LC post 5438765), Jun 2025 AVP accounts
```

```
FORMAT B — 3-round SuperDay with embedded PR Review (Bengaluru Cohort)
───────────────────────────────────────────────────────────────────────
Round 1 │ PR Review (~10 min) + DSA (matrix problem)
Round 2 │ HLD design on HackerRank whiteboard
Round 3 │ Behavioral

Example: JPMC SDE3 Bengaluru SuperDay (LC post 7124593 — News Aggregator)
         JPMC Jan 2026 cohort SuperDay (Fishbowl — No Broker app)
```

```
FORMAT C — 4-round format (some teams)
───────────────────────────────────────
Round 1 │ OA / HackerRank DSA
Round 2 │ System Design (e.g., Monolith → Microservices)
Round 3 │ Code Review + Spring Boot questions
Round 4 │ HM / Leadership

Example: Scribd-documented JPMC SWE3 experience
```

```
FORMAT D — 3 rounds, Design FIRST (some India teams)
─────────────────────────────────────────────────────
Round 1 │ System Design (HLD improvement / scale to 50x traffic)
Round 2 │ Code Review + DSA problem
Round 3 │ Behavioral

Example: LC post 7421234 (SDE-2 applied, offered SDE-3, 2025)
```

**Bottom line:** After your DSA rounds, the next round is most likely one of:
- System Design (HLD and/or LLD) — **highest probability**
- Code Review / PR Review — **dedicated round at some teams**
- Behavioral / HM — **least likely if you haven't done it yet**

**The safest assumption:** Prepare system design as your primary and code review as secondary.

---

## 🔬 §2 — Confirmed Problems in the Design Stage

**Only first-hand accounts appear here. Generic prep-blog content is excluded.**

| Date | Source | Format | Problem Asked | Depth | Outcome |
|------|--------|--------|---------------|-------|---------|
| Jan 2026 | ✅ Fishbowl + Glassdoor Community (Jan 22, 2026 cohort SuperDay) | HLD | **No Broker app** — property search + upload property features | HLD only | Not disclosed |
| Nov 2025 | ✅ LeetCode post 7359766 (SDE-3 Java Full Stack, Bangalore) | HLD | **Document Upload & Validation** — accept Aadhaar/files, validate via third-party (2–3s delay), store, return tracking link | Java depth + HLD | Not disclosed |
| 2025 | ✅ LeetCode post 7421234 (SDE-2 applied, offered SDE-3) | HLD | **Flash Sale / Traffic Spike** — design a system for a sale going live, handle millions of users; happy AND unhappy paths required | HLD | ✅ Offered SDE-3 |
| 2025 | ✅ LeetCode post 7124593 (SDE3 Bengaluru SuperDay) | HLD | **News Aggregator** — real-time updates from multiple sources to a UI; users log in and set preferences | HLD on HackerRank whiteboard | Not disclosed |
| 2025 | ✅ LeetCode post 6766946 (SDE-3 Mumbai) | LLD → HLD | **Delivery Partner App** — design LLD; interviewer then gives a basic HLD skeleton and asks you to improve it; walkthrough each component end-to-end | LLD + HLD improvement | Round ended in 25 min |
| 2024–2025 | ✅ LeetCode post 5438765 (SE3 Bengaluru) | HLD | **Parking Lot System** — done on HackerRank drawing/shapes whiteboard | HLD | Not disclosed |
| Jun 2024 ⏪ | ✅ Medium (Iqbalkhaniq, AVP/SDE3, selected) | HLD | **Payment System** — requirements → DB schema → estimations → HLD → optimizations | Full arc | ✅ Selected (declined) |
| 2025 | 🔶 Multiple sources | HLD | **Monolith → Microservices** — convert existing monolith, explain decomposition, boundaries, API gateway | HLD | — |
| HM round | 🔶 LC post 6633523 | HLD | **Virus Scanning Service** — design + make fault tolerant + idempotent; follow-up: orchestrator-based idempotency across services | HLD | — |

> **New problems vs. sibling file (`01-round3-prep-guide.md`):**
> **No Broker**, **Flash Sale/Traffic Spike**, **News Aggregator**, and **Document Upload & Validation**
> were not in the prior research pass. Add these to your practice rotation.

### ⭐ The "Improve the Given HLD Skeleton" Pattern

Multiple independent accounts describe a specific JPMC interviewer move:

> Interviewer gives you a pre-drawn partial HLD diagram and asks you to:
> 1. Identify what's missing or wrong
> 2. Add components to make it scalable / fault tolerant
> 3. Walk through end-to-end flow

This appeared in: Delivery Partner App (Mumbai SDE-3), Flash Sale design, Document Validation
design. **Do not wait to be given a blank slate** — some interviewers hand you something broken
and watch what you do with it.

---

## 🔬 §3 — The PR / Code Review Round (Dedicated Round)

**This is a separate dedicated round at many JPMC teams, not just a warm-up question.**
Multiple first-hand accounts confirm it: the Bengaluru SuperDay account, the Jan 2026 cohort,
and the Format C/D accounts all show a code review as a standalone 10–20 min block.

**Format:** You are given 120–150 lines of Java/Spring Boot code with a PR description.
You review it and verbalize what you find.

### What they check — confirmed from JPMC first-hand accounts:

| Category | What to look for | JPMC-specific signal |
|----------|-----------------|---------------------|
| **SRP / SOLID** | Classes doing too many things; methods that mix business logic with I/O | Interviewers say "SRP violations" explicitly |
| **Security — SQL injection** | Dynamic query construction with user input; `ORDER BY` or `WHERE` with concatenation | JPMC financial context makes this a high-priority catch |
| **Security — MITM** | Hardcoded secrets, API keys, DB credentials in code | Flagged explicitly in Bengaluru SuperDay account |
| **Security — XSS** | User input rendered without sanitization | Less frequent but mentioned |
| **Concurrency** | Thread safety: unsynchronized shared state, wrong lock usage | "Concurrency and multithreading issues" — verbatim from LC post 7124593 |
| **Exception handling** | Swallowed exceptions (`catch (Exception e) {}`); no logging in catch blocks | Confirmed as primary target |
| **Logging** | `System.out.println()` instead of proper logger; missing log levels | "Clean code, logging" — verbatim from account |
| **Spring annotations** | Missing `@Transactional` on DB-modifying methods; prototype bean injected into singleton; wrong `@Autowired` usage | Java backend SDE-3 specific |
| **Input validation** | No validation on REST request body; missing null checks | |

### How to approach it in the round:

**Steps in plain English:**
1. Read the PR description first — understand what it's supposed to do.
2. Scan structure: class responsibilities, method sizes.
3. Flag what's wrong from top to bottom, narrating out loud as you go.
4. Prioritize by severity: security issues first (SQL injection, hardcoded secrets),
   then correctness (thread safety, exception handling), then style (logging, naming).
5. Suggest fixes — don't just list problems.

> **JPMC-specific expectation:** They want you to mention **Man-in-the-Middle attacks**
> and **SQL injection** by name. These are verbatim phrases from the Bengaluru SuperDay account.
> Financial-context reviewers care about these more than e-commerce companies would.

---

## 🔬 §4 — Fault Tolerance Probing (Specific JPMC Pattern)

**This is the most confirmed JPMC-specific design probe and it is NOT in the sibling file.**

From LC post 6633523 (VP-level round):

> Interviewer gave a design problem and asked the candidate to "address fault tolerance at
> each step and justify decisions." Follow-up: the entire process was synchronous — make it
> fault tolerant. The candidate admitted they could not. This was noted as a weakness.

From the Virus Scanning Service (HM round):

> Candidate designed the system, then was asked to make it **idempotent**. Follow-up:
> make ALL services idempotent via a **common orchestrator**.

**Fault tolerance vocabulary JPMC expects you to produce unprompted:**

| Concept | What it means in practice | Where it shows up |
|---------|--------------------------|-------------------|
| **Retry with backoff** | Exponential backoff on transient failures; max retries cap | Any service calling third-party API |
| **Circuit breaker** | After N failures, open the circuit; stop calling the failing service | Resilience4j — CLOSED → OPEN → HALF-OPEN states |
| **Idempotency key** | Same request twice = same result, no side effects | POST /bookings, POST /payments — financial critical |
| **Dead Letter Queue (DLQ)** | Failed Kafka messages go to DLQ for retry/investigation | Async processing (notifications, validations) |
| **Async + tracking link** | For slow third-party calls (2–3s), return a tracking ID immediately, process async | Document Upload & Validation — exact JPMC problem |
| **Common orchestrator** | One service that coordinates calls and ensures each is idempotent | Advanced follow-up in Virus Scanning account |

**The pattern:** JPMC starts with a happy-path design, then pushes:
> *"What happens if this service is down?"*
> *"What happens if the user retries the same request?"*
> *"What if the third-party takes 10 seconds instead of 3?"*

Prepare at least one fault-tolerance answer for every external call in your design.

---

## 🧠 §5 — Java Depth Questions in the Design Context

JPMC embeds Java questions mid-design, especially when you mention threads, locks, or async.
These are not separate from the design round — they emerge from what you say.

**Confirmed topics from first-hand SDE-3 accounts:**

| What triggers it | What they ask | Expected answer |
|------------------|--------------|----------------|
| You say "two users can book the same seat simultaneously" | "How does your Java code handle that?" | Show `synchronized`, `ReentrantLock`, or optimistic locking (`@Version`) |
| You say "async processing" | "What Java API would you use?" | `CompletableFuture`, `ExecutorService`, mention thread pool sizing |
| You mention Java 21 | "What's your favorite new feature and why?" | Virtual threads (Project Loom) — I/O-bound tasks; say when NOT to use (CPU-bound) |
| You use a `HashMap` in your design | "Is your HashMap thread-safe?" | `ConcurrentHashMap` — explain CAS for empty buckets, segment-level locking |
| You use Singleton pattern | "Make your Singleton thread-safe in Java" | Double-checked locking with `volatile`, or enum Singleton |
| You mention distributed lock | "How would you implement that?" | Redis `SET key value NX PX 30000` — NX = only if not exists, auto-expires |

**From Nov 2025 Java Full Stack SDE-3 Bangalore account (confirmed verbatim topics):**
- Advantages of Java 21 new features
- Java vs Scala comparison
- Concurrency control in Java extended to distributed systems
- *"Grilled pretty hard on every point they bring up"*

> **Rule:** If you put it in your design, be ready to code it or explain it at the internals level.
> JPMC uses your own design vocabulary against you.

---

## 🧭 §6 — What Sets SDE-3 Apart in This Round (Level-Setting Signal)

**This is the most actionable section.** Multiple accounts confirm the design stage is the
primary lever for leveling between SDE-2 and SDE-3.

One account (LeetCode 2025): *"The System Design round and the Behavioral round played a major
role in leveling the offer from SDE-2 to SDE-3."* — candidate applied for SDE-2 and was
offered SDE-3 based on design round strength.

One account (older, Blind): candidate downleveled from SDE-3 → SDE-2 because design round
performance did not meet SDE-3 bar.

### The SDE-3 behavioral differences in design (confirmed from accounts):

| What SDE-2 does | What SDE-3 does |
|----------------|----------------|
| Asks 2–3 clarifying questions | Asks **10–15 clarifying questions** — explicitly noted in JPMC accounts |
| Designs happy path cleanly | Proactively covers **happy AND unhappy paths** — verbatim from Flash Sale account |
| Mentions scalability if asked | **Raises scalability and bottlenecks unprompted** |
| States the design | **Defends trade-offs**: "I chose Redis over in-memory here because..." |
| Identifies security if asked | **Mentions compliance and security first** — JPMC financial context |
| Accepts the given HLD | **Identifies what's wrong with the given skeleton** and why |

### The 10–15 clarifying questions rule:

JPMC accounts specifically mention this. Asking 10–15 clarifying questions before designing
is described as "showing maturity" and "reducing systemic risk." This is not a number to hit
mechanically — it's about demonstrating you don't assume.

**Categories to cover with clarifying questions:**
1. Scale: DAU, peak QPS, data size
2. Consistency: strong vs. eventual — acceptable trade-off?
3. Read-heavy vs. write-heavy ratio
4. Latency SLA: what's acceptable?
5. Geographic distribution (JPMC is global — relevant)
6. Third-party dependencies (timeout assumptions, SLA)
7. Retry policy for failures
8. Compliance / data residency (JPMC-specific — always ask)

---

## ⚠️ §7 — Gotchas Specific to JPMC's Design Stage

**The round can end in 25 minutes.** The Mumbai SDE-3 Delivery Partner App round was scheduled
for 45 min and ended in 25 because the interviewer was satisfied. Don't panic if it ends early —
it's not always a bad sign.

**The round can also be 50 minutes of grilling.** The Nov 2025 Bangalore account: *"grilled
pretty hard on any point you bring up."* The interviewer probes every decision. Don't make
claims you can't defend.

**The "behavioral round" may include a design question.** The Virus Scanning Service problem
came up in what candidates labeled as the HM/behavioral round. Don't turn off your design
brain just because the round is labeled "behavioral."

**SOLID principles are expected to be known.** From multiple accounts: interviewers were
"surprised" or flagged it when SDE-3 candidates didn't know SOLID principles by name. Know
them with examples, not just acronyms.

**Security is a first-class citizen at JPMC.** Every design round account mentions security
or compliance as a driver. JPMC handles $6T/day in transactions. If your design doesn't
address data security, encryption at rest/in-transit, or API authentication upfront, you're
already behind.

---

## 🧾 TL;DR — What's New vs. the Existing Guide

This file is the **second research pass**. What's new compared to `01-round3-prep-guide.md`:

1. **Format variability confirmed** — not a fixed "Round 3"; post-DSA stage can be design, code review, or behavioral depending on team
2. **New confirmed problems:** No Broker app (Jan 2026), Document Upload & Validation (Nov 2025), Flash Sale/Traffic Spike (2025), News Aggregator (2025 SuperDay)
3. **PR/Code Review round** — dedicated round at many teams, 120–150 lines Java/Spring, SRP + SQL injection + MITM + concurrency
4. **"Improve the given HLD skeleton" pattern** — JPMC-specific move; don't assume blank-slate design
5. **Fault tolerance is actively probed** — retry, circuit breaker, idempotency, DLQ — not optional at SDE-3
6. **10–15 clarifying questions** — explicitly flagged in JPMC accounts as SDE-3 differentiator
7. **Happy AND unhappy paths** — required, not optional
8. **Design round = level-setter** — SDE-2 ↔ SDE-3 swing confirmed by direct accounts

---

## 🔄 Changelog

| Date | Change |
|------|--------|
| Aug 2026 | File created — independent deep research pass (Aug 2025–Aug 2026 window). 8 search rounds across LeetCode Discuss, Glassdoor, Fishbowl, Medium, Blind, 1Point3Acres. Sibling to `01-round3-prep-guide.md`. Only first-hand accounts appear in problem tables. |
