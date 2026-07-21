# Operational Scenario: Pre-Event Capacity Planning

> **When this appears in an interview:** Interviewer says "Black Friday is in 6 weeks — how do you prepare?" or "a major marketing campaign drops next month — what do you do?" The keywords are **capacity planning**, **peak event**, or **traffic forecast**.
> **Patterns used:** Feature Flag Gating (`../../Patterns/DeepDive/14-feature-flag-gating.md`) for graceful degradation kill switches during the event. Scaling Reads (`../../Patterns/DeepDive/01-scaling-reads.md`).

---

## 🎯 The Situation

You have advance warning of a known traffic spike. It could be Black Friday, a Super Bowl ad, a product launch, or a government deadline (everyone files taxes on April 15). You have weeks — not minutes — to prepare.

**Classic triggers in interviews:**
- "Black Friday is in 6 weeks — walk me through your preparation"
- "We're running a marketing campaign that will 5× our usual traffic — how do you handle it?"
- "Our service has never been load tested — a big event is coming — what do you do?"
- "How do you capacity plan for a traffic spike you've never seen before?"

---

## 🧠 The Decision You Make First

Ask one clarifying question before planning anything:

> *"Do we have historical data for this type of event, or are we forecasting for the first time?"*

| Answer | Approach | Safety buffer |
|---|---|---|
| Historical data exists (previous Black Fridays) | Baseline × multiplier from history | +50% above historical peak |
| No history, but industry benchmarks available | Industry benchmark × your traffic profile | +100% (2× forecast) |
| Completely novel event (first-ever launch) | Load test to find the ceiling, then provision at 150% of ceiling | +200% (3× forecast) |

**The answer to this question determines how much buffer to add.** Known events with data: 50% buffer. Unknown events: 2–3× buffer. Forecasts are always wrong — the buffer is not optional.

---

## 🎨 Visual — 6-Week Preparation Timeline

> **Before:** service at current baseline, never load tested, no runbooks, no kill switches, no pre-provisioned capacity.
> **After:** service load-tested to 150% of forecast peak, infrastructure pre-provisioned, graceful degradation kill switches configured, on-call war room planned.

```
T-6 weeks      T-5 weeks     T-4 to T-3 weeks    T-2 weeks     T-1 week     T-0 (Event)
───────────────────────────────────────────────────────────────────────────────────────────
│ BASELINE     │ FORECAST     │ LOAD TEST          │ PROVISION   │ RUNBOOKS   │ WAR ROOM
│              │              │                    │             │            │
│ Measure:     │ Define:      │ Run 3 rounds:      │ Pre-scale:  │ Write:     │ Dashboard
│ P50/P95/P99, │ expected     │ 1×  → confirm      │ min-replicas│ runbooks   │ live.
│ throughput,  │ peak multiple│ stable baseline    │ set to      │ per alert  │ On-call
│ saturation   │              │                    │ forecast    │ type.      │ briefed.
│ metrics for  │ Set target   │ Forecast× → check  │ minimum.    │            │ Kill
│ service AND  │ SLA: P99 <   │ SLA holds          │ Pre-warm    │ Configure: │ switches
│ every        │ Xms at       │                    │ caches.     │ kill       │ tested.
│ downstream   │ forecast     │ 150% forecast→     │ Verify DB   │ switches   │ Rollback
│ dependency   │ peak         │ test graceful      │ connection  │ for non-   │ plan
│              │              │ degradation        │ budget.     │ core       │ ready.
│              │              │ Fix gaps found     │             │ features.  │
│              │              │ before event       │             │            │

KEY INVARIANT:
   Every performance gap discovered during load test MUST be fixed
   before the event. If it can't be fixed in time, it becomes a
   kill switch (disable the feature for the event window).
```

---

## ⚠️ PREREQUISITE — Load Test Environment Must Match Production

> **Class 4 (Missing prerequisites):** Load testing gives wrong results if the environment doesn't match production. A clean load test against the wrong environment is worse than no test — it gives false confidence.

| Required | Why | Without it |
|---|---|---|
| **Same instance types as prod** | Smaller instances saturate at different thresholds | Numbers are wrong by 2–10× |
| **Same DB size and data volume** | A DB with 1,000 rows performs differently than 100M rows | Query plans differ; indexes don't behave the same |
| **Same number of replicas as prod** | Zero replicas in test means you never test read routing | Replica performance is untested |
| **Mocked / sandboxed external APIs** | Real payment gateway will trigger fraud alerts at load test volumes | Stripe will block your account; test hits error path, not happy path |
| **Realistic traffic patterns** | Flat uniform load underestimates burst impact | Burst handling is untested |
| **Load test data isolated from production** | Test orders/users contaminate operational data | Support tickets from "test orders" appearing for real users |

---

## 🗂️ The 6-Phase Capacity Planning Playbook

---

### Phase 1 — Baseline (T-6 Weeks)

Establish where you are before deciding where you need to be.

```
Metrics to capture at current traffic:
  P50, P95, P99 latency — per critical endpoint
  Error rate                — baseline should be < 0.1%
  Throughput (RPS)          — measured at current peak hour, not average hour
  DB: CPU %, active connections, slow query count (> 100ms)
  Cache: hit rate           — if < 80%, caching isn't helping much
  Downstream APIs: P99 latency per dependency call
  Saturation: thread pool %, connection pool % — how much headroom exists?

For each external dependency, document:
  → What is its rate limit?
  → What is its SLA latency at your current call volume?
  → Does it have a sandbox / staging environment for load testing?
```

> ⚠️ **Class 8 (Incomplete change surface):** Don't measure only your service. Map every dependency and its limits. Your service can scale 10×, but if the payment gateway rate-limits at 1,000 req/s and you'll hit 2,000 req/s at peak, the gateway is your actual ceiling — and you didn't see it until the event.

**Say in interview:**
> *"Six weeks out, I establish a baseline — P99, throughput, and saturation metrics for my service AND every downstream dependency. Dependency rate limits are often the hidden ceiling: your service scales 10×, but if downstream can't, you hit their limit at the worst moment."*

---

### Phase 2 — Forecast (T-5 Weeks)

Define the expected peak traffic multiple.

```
Data sources:
  1. Historical data (best source):
     → Compare last year's Black Friday peak hour vs an ordinary peak hour
     → Typical e-commerce: 5–10× peak hour multiple on the peak day
     → Use PEAK HOUR vs PEAK HOUR — not average vs average

  2. Marketing projection (if no history):
     → Expected impressions → conversion rate → sessions → requests/sec
     → Work with marketing team on the funnel; don't guess conversion rate

  3. Industry benchmarks (no history, no projection):
     → B2C e-commerce: plan for 10× ordinary peak hour
     → B2B SaaS: 3–5× (fewer users, more predictable)
     → Add 2× buffer on top of any benchmark

Define the target SLA BEFORE load testing:
  "P99 < 500ms, error rate < 0.1%, at 5× current peak throughput"
  Without a defined target, load test results have no pass/fail criterion.
  You cannot declare success or failure without a pre-agreed threshold.
```

> ⚠️ **Class 3 (Math without reality check):** Don't forecast based on average traffic. Black Friday peak hour may be 20× your average hour, but only 5× your previous peak hour. The relevant multiplier is **peak vs peak**, not peak vs average. Using the wrong denominator makes your forecast look much smaller than it is.

**Say in interview:**
> *"I forecast by asking: what multiple of our current PEAK HOUR will we see at event peak? Not average — peak. A 5× spike on average traffic is a very different number from a 5× spike on your busiest previous hour. I define the target SLA before load testing, because without a pass/fail threshold, the test results mean nothing."*

---

### Phase 3 — Load Test (T-4 to T-3 Weeks)

Validate the system holds at forecast peak. Fix every gap found here — before the event.

**Tools:** k6, Gatling, Locust, JMeter. For spike-shape tests, k6 has built-in ramping stages.

```
Three-round progression:

  Round 1 — Baseline validation (1× current peak):
    → Confirm the system is stable at today's load.
    → If it's not stable at 1×, you have a pre-existing problem.
      Fix it before continuing.

  Round 2 — Forecast peak (e.g., 5× current):
    → Does P99 stay within SLA? Does error rate stay < 0.1%?
    → Does HPA scale up correctly and in time?
    → If not: you've found the bottleneck. Fix it. Re-run Round 2.

  Round 3 — 150% of forecast peak (stress / degradation test):
    → What happens when traffic exceeds forecast?
    → System MUST degrade gracefully — rate-limit excess requests, not crash.
    → Test kill switches: flip a feature flag off → confirm traffic drops cleanly.
    → Find the breaking point so you know the margin you're operating with.
```

**k6 ramping example (Round 2 — 5× peak):**

```javascript
export const options = {
    stages: [
        // Ramp up over 5 minutes (simulates a real traffic surge)
        { duration: '5m',  target: 500  },
        // Hold at peak for 20 minutes (sustain — not just a spike)
        { duration: '20m', target: 500  },
        // Ramp down — confirm recovery
        { duration: '5m',  target: 0    },
    ],
    thresholds: {
        // Failure criteria — test fails if these are breached
        http_req_duration: ['p(99)<500'],
        http_req_failed:   ['rate<0.001'],
    },
};
```

> ⚠️ **Class 4 (Missing prerequisite — environment match):** Load test environment must match production topology: same instance types, same DB size, realistic data volume, mocked external APIs. A mismatch invalidates the results.

> ⚠️ **Class 5 (Failure residue — load test data contamination):** If the load test writes to a production-adjacent DB, test orders/transactions will pollute operational data. Use a dedicated test environment, or use a test account ID range explicitly excluded from downstream systems (reporting, fulfillment, finance).

**Say in interview:**
> *"I run the load test in three rounds: 1× to validate baseline stability, forecast peak to validate the SLA holds, and 150% of forecast to confirm graceful degradation and test kill switches. Every gap found gets fixed before the event. The load test environment must match prod topology — same DB size, same instance types — otherwise the numbers are fiction."*

---

### Phase 4 — Provision (T-2 Weeks)

Pre-scale infrastructure. Do NOT rely on auto-scaling alone for a known spike.

> **Why pre-provision?** HPA evaluates every 15–30 seconds, and new pods take 30–120 seconds to start and become healthy. During a sudden spike, you have a 2–3 minute window of under-provisioning while auto-scaling catches up. For an ordinary traffic day, this lag is acceptable. For a known spike event that hits 5× traffic in 60 seconds, this lag means thousands of failed requests at the worst moment.

```
Pre-provisioning steps:

  1. Set HPA min-replicas to the forecast minimum:
       kubectl patch hpa <name> -p '{"spec":{"minReplicas": <N>}}'
     This ensures pods are already running when traffic arrives.

  2. Pre-warm caches before the event window opens:
       Run a batch job that reads hot data from DB → writes to Redis
       Do this 30–60 min before traffic spike (not during it)
     Cold Redis during a spike = thundering herd on the DB at the worst moment.

  3. Verify DB connection budget:
       pod_count × hikari_pool_size < DB max_connections (or pooler limit)
     Run this check explicitly — don't assume it's fine.

  4. Pre-scale read replicas:
       Add replicas 24 hours before the event (replica warmup takes time)
       Verify replication lag is < 1s before routing production reads to new replicas

  5. Reserve cloud capacity (if on AWS / GCP / Azure):
       On-demand instances may not be available during large events
       (other companies are also scaling for the same holiday)
       Use reserved capacity or spot instances reserved in advance
```

> ⚠️ **Class 3 (Math check — cache warm-up spike):** When 20 new pods spin up simultaneously, each warms its local in-process cache from Redis or DB. This causes a brief DB spike even if Redis is pre-warmed. Stagger pod scale-out (not all at once) or ensure Redis has the data so pods hydrate from cache, not DB.

**Say in interview:**
> *"I don't rely on auto-scaling alone for a known spike. HPA has a 2–3 minute lag — during a sudden spike, that's exactly the moment you can't afford to be under-provisioned. I pre-scale: set HPA min-replicas to the forecast minimum, pre-warm the Redis cache from a batch job, and add read replicas 24 hours before the event so they have time to warm up and catch replication lag."*

---

### Phase 5 — Kill Switches and Runbooks (T-1 Week)

Decide what degrades gracefully. Write the runbooks before you need them.

```
Graceful degradation plan:

  Feature                        | Can disable under load? | Mechanism
  ───────────────────────────────────────────────────────────────────────
  Product recommendations        | YES                     | Feature flag → return empty list
  Recently viewed items          | YES                     | Feature flag → return empty
  Real-time inventory count      | YES                     | Serve cached count (accept stale)
  Review / rating display        | YES                     | Feature flag → hide section
  Non-critical search filters    | YES                     | Feature flag → simplified results
  Checkout                       | NO — core path          | Must always work
  Payment processing             | NO — core path          | Must always work
  User login / auth              | NO — core path          | Must always work

Pre-written runbooks (one per expected alert type):
  "DB connections exhausted" → steps to scale pooler, check slow queries
  "Cache miss rate > 50%"   → steps to verify Redis health, check eviction
  "P99 > SLA threshold"     → steps to kill non-core feature flags, scale pods
  "Payment API rate-limited" → steps to queue requests, contact vendor
  "Pod auto-scaling stuck"  → steps to manual scale, check node capacity
```

> ⚠️ **Class 1 (Mechanism under-specification):** "Write a runbook" is not enough. Each runbook must specify the exact command to run, the exact metric to watch afterward, and the exact definition of "fixed." Without these, an on-call engineer at 2am will improvise — and improvisation under pressure is how incidents become worse.

**Say in interview:**
> *"A week before the event, I define what degrades gracefully and pre-configure the feature flags for those features. If recommendations go down, I flip a flag and serve an empty list — checkout is never touched. The runbooks are written in calm so the on-call engineer has a script, not a blank page. Each runbook specifies exact commands and the exact metric that confirms the fix worked."*

---

### Phase 6 — Game Day (T-0)

The event itself. Preparation is done — this phase is observation and response.

```
Before traffic starts:
  War room channel open (Slack / Teams) with all stakeholders
  Dashboard link pinned in the channel
  On-call rotation briefed: who owns which service, who is incident commander
  Escalation chain documented: if P1 hits, who calls whom at 2am?
  Kill switch commands saved and ready to paste (don't type them during an incident)

First 15 minutes after traffic spike starts:
  Watch error rate, P99, throughput on the dashboard
  Compare against load test baselines — does real traffic match the forecast?
  Check HPA: are pods scaling as expected? Is the scale-up completing in time?
  If traffic exceeds forecast: manually scale up immediately — do not wait for HPA

During the event:
  Status update every 30 minutes even if all green:
    "All systems nominal. Current traffic: 4.2× baseline. P99: 320ms. No alerts."
  Stakeholders need to know you're watching. Silence is alarming.

Post-event (next day):
  Scale down gradually — long-tail traffic often persists after peak
  Write event debrief: forecast vs actual, what matched, what didn't
  Update capacity model with real data for next event
  Convert any improvised fixes into permanent infrastructure changes
```

**Say in interview:**
> *"On game day, I have the war room channel open and dashboards live before traffic starts. In the first 15 minutes I confirm that real traffic matches the load test forecast. If it exceeds forecast, I manually scale — I don't wait for HPA. After the event, I write a debrief: what matched the forecast, what didn't, and what I'd do differently. Real event data is the most accurate input for next year's capacity model."*

---

## 🧩 Interview Probe Q&As

**"What if traffic far exceeds your forecast during the event?"**
> Four moves, in order. First: manually scale immediately — `kubectl scale deployment/<name> --replicas=N` — don't wait for HPA's 2–3 minute lag. Second: activate kill switches for non-core features — shed load from recommendations, reviews, non-critical filters. Third: if core path is at risk, activate rate limiting at the API gateway — shed traffic rather than crash. A rate-limited user gets a 429 and can retry; a crashed service gives a 500 and loses the transaction. Fourth: update the war room channel every 5 minutes, not 30. Stakeholders need to know you're managing an over-forecast event actively.

**"How do you load test a service that depends on external APIs you don't control?"**
> Mock or sandbox all external dependencies. Most payment gateways have sandbox environments with the same API contract as production. For dependencies without sandboxes, build an internal mock service that returns realistic responses with realistic latency (matching the external API's documented P99 SLA). Without mocking: your load test will exhaust the external API's rate limit, get throttled or blocked, and you'll be testing the error path instead of the happy path. After load testing, explicitly document the assumption: "these results assume downstream P99 of 200ms; if actual downstream degrades, our system will degrade proportionally."

**"What do you do if you discover a critical performance problem during load testing with 2 weeks left?"**
> Triage by path first: is it on the core path (checkout, login, payment) or a non-core feature? If non-core: kill it with a feature flag for the event window. Fix it after. If core: focus exclusively on the specific bottleneck found — missing index, missing cache, connection pool limit — rather than refactoring. Most load test failures have a targeted fix. If the core path cannot be fixed in time, escalate to leadership immediately with a clear risk statement and options: delay the event, reduce the marketing spend, or accept the risk with a mitigation plan. Do not discover this on the day of the event.

**"How do you handle scale-down after the event?"**
> Gradually, not immediately. Long-tail traffic often persists 6–12 hours after a peak event — users came back to browse, orders are still being processed. Hard-scaling from 20 pods to 2 pods before traffic stabilizes causes a mini-incident. Let HPA handle scale-down over 30–60 minutes by resetting max-replicas to normal and letting it wind down naturally. Monitor as it scales down. Also: the post-event steady state may be permanently higher than pre-event — a successful campaign acquires new users. Don't blindly restore pre-event baselines. Update your HPA min-replicas to reflect the new normal.

---

## 🧾 TL;DR — What to Say in the Interview (2 Minutes)

> *"I use a 6-phase playbook. At T-6 weeks I baseline: P99, throughput, and saturation — for my service AND every downstream dependency, because dependency rate limits are often the ceiling you don't see until the event. At T-5 weeks I forecast: I compare peak vs peak, not peak vs average, and add a 50–100% safety buffer because forecasts are always wrong. At T-4 to T-3 weeks I load test in three rounds — 1× to validate baseline stability, forecast peak to validate the SLA holds, 150% to test graceful degradation. Every gap gets fixed before the event; if it can't be fixed, it becomes a kill switch. At T-2 weeks I provision: I pre-scale HPA min-replicas, pre-warm the cache from a batch job, and add read replicas 24 hours in advance — I don't rely on auto-scaling alone because HPA has a 2–3 minute lag during a sudden spike. At T-1 week I configure kill switches and write runbooks — exact commands, not bullet points. On game day I open a war room channel before traffic starts, confirm real traffic matches the forecast in the first 15 minutes, and manually scale if it exceeds forecast. After the event, I write a debrief and update the capacity model with real numbers."*

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 21, 2026 | **Note created.** Batch 4 of Operational-Scenarios gap closure. Pre-event capacity planning — the "Black Friday in 6 weeks" interview question. Class 3 applied: forecast must use peak-vs-peak not peak-vs-average; cache warm-up spike math. Class 4: load test environment must match prod topology. Class 5: load test data contamination pattern. Class 8: dependency rate limits as the hidden ceiling. Class 1: runbooks must specify exact commands and exact success metrics, not just "write a runbook." |
