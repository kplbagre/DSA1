# Operational Scenario: Incident Diagnosis Framework

> **When this appears in an interview:** Interviewer says "your API error rate just spiked to 30% — what do you do?" or "production is on fire, walk me through your response." The keyword is **incident** or any variant of "something broke in production — how do you respond?"
> **Patterns used:** Feature Flag Gating (`14-feature-flag-gating.md`) for rollback kill switch. Strangler Fig (`10-strangler-fig.md`) rollback if a migration caused the incident.

---

## 🎯 The Situation

Your pager fires at 2am. Error rate spiked. Or latency tripled. Or the service is completely down. You have no idea why yet.

**Classic triggers in interviews:**
- "Your monitoring shows P99 latency went from 80ms to 8 seconds — what do you do?"
- "30% of API calls are returning 500 — walk me through your response"
- "A customer called saying their data looks wrong — how do you investigate?"
- "Your DB CPU is at 100% — what's your plan?"

---

## 🧠 The Decision You Make First

Before anything else, ask one clarifying question:

> *"What is the signal that triggered this — error rate, latency, full outage, or data quality?"*

The answer determines your diagnosis path:

| Signal | Likely cause | First place to look |
|---|---|---|
| Error rate spike (4xx / 5xx ↑) | Bad deploy, dependency failure, data validation error | Recent deployments, dependency health |
| Latency spike (P99 ↑ but low error rate) | Resource saturation, lock contention, slow dependency | CPU/memory/thread pool/connection pool saturation |
| Full outage (service unreachable) | Infrastructure failure, DNS, load balancer | Infra layer BEFORE application layer |
| Resource exhaustion (CPU/mem at 100%) | Memory leak, unbounded query, traffic spike | Profiling, heap dump, query explain plan |
| Data quality (wrong values in DB) | Bug in write path, failed migration | Recent writes, migration history, dual-write drift |

**Do not assume root cause before looking at the signal type.** Every incident looks like "something broke" — the type tells you where to start.

---

## 🎨 Visual — The 5-Phase Incident Response Loop

> **Before:** pager fires, system state unknown, users impacted.
> **After:** impact mitigated, root cause confirmed, metrics back to baseline, post-mortem written.

```
SIGNAL FIRES (alert, customer report, automated check)
         │
         ▼
┌─────────────────────────────────────────────────┐
│  PHASE 1: STABILIZE                              │
│                                                  │
│  Stop the bleeding first.                        │
│  Mitigate user impact BEFORE diagnosing.         │
│  → Rollback last deploy?                         │
│  → Kill switch the new feature flag?             │
│  → Scale up (if resource exhaustion)?            │
│  → Shift traffic to healthy region?              │
└─────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────┐
│  PHASE 2: SCOPE                                  │
│                                                  │
│  How wide is the blast radius?                   │
│  → Which users? Which regions? Which endpoints?  │
│  → What percentage of requests are affected?     │
│  → Is it getting worse, stable, or recovering?  │
└─────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────┐
│  PHASE 3: DIAGNOSE                               │
│                                                  │
│  Use the 4 signals: Metrics → Events →           │
│  Logs → Traces (in that order)                   │
│  → What changed recently?                        │
│  → Which service in the call chain is failing?   │
│  → What does the error message say?              │
└─────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────┐
│  PHASE 4: FIX + VALIDATE                         │
│                                                  │
│  Apply targeted fix. Confirm metrics recovered.  │
│  → Deploy hotfix → watch error rate              │
│  → Config change → watch latency                 │
│  → Scale resource → watch saturation             │
└─────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────┐
│  PHASE 5: POST-MORTEM                            │
│                                                  │
│  Blameless. Written within 48 hours.             │
│  → Timeline of events                            │
│  → Root cause (not "human error" — the system    │
│    that allowed the error to reach production)   │
│  → 3–5 action items with owners + due dates      │
└─────────────────────────────────────────────────┘

KEY INVARIANT:
   Phase 1 (stabilize) ALWAYS comes before Phase 3 (diagnose).
   Mitigating user impact is more urgent than finding root cause.
   A senior engineer on-call asks "how do I stop this?" before
   asking "why did this happen?" — those are different questions.
```

---

## ⚠️ PREREQUISITE — Observability Must Exist Before the Incident

> **This is a Class 4 mistake (missing prerequisites):** You can only run this framework if the following infrastructure exists BEFORE the incident occurs. If you're setting up dashboards while the incident is in progress, you're already in a worse situation than you need to be.

**Required before your first pager fire:**

| Tool | What it gives you | Minimum viable |
|---|---|---|
| **Metrics** (Prometheus, DataDog, CloudWatch) | Error rate, P99 latency, throughput, saturation | Yes — no incident response without this |
| **Centralized logs** (ELK, Splunk, Loki, CloudWatch Logs) | Error messages, stack traces, request correlation | Yes |
| **Distributed tracing** (Jaeger, Zipkin, OpenTelemetry) | Which service in the call chain is slow/failing | Strongly recommended |
| **Alerting** | Pagerduty/OpsGenie rules on error rate + P99 | Yes |
| **Runbooks** | Pre-written diagnosis steps for known alert types | Yes — written during calm, not during incident |
| **Deployment history** | What was deployed and when | Yes |

If any of these are missing, that absence is itself a post-mortem action item.

---

## 🗂️ The 5-Phase Playbook

---

### Phase 1 — Stabilize (Mitigate Before You Diagnose)

**The single most important phase.** Junior engineers want to find root cause. Senior engineers want to stop users from being harmed — FIRST.

**Stabilization options (fastest to slowest):**

```
Option A — Feature flag kill switch       (< 1 second to propagate)
  If the incident is caused by a new feature:
  → Flip the flag to OFF in the flag service
  → Propagates to all services within poll interval (~5 seconds)
  → No deployment needed

Option B — Traffic rollback               (< 60 seconds)
  If a new deployment caused the incident:
  → Revert the load balancer to the previous version
  → On Kubernetes: kubectl rollout undo deployment/<name>
  → Verify rollback: watch error rate drop within 1 traffic cycle

Option C — Scale up                       (1–5 minutes)
  If resource exhaustion is causing the incident:
  → Scale up pods: kubectl scale deployment/<name> --replicas=N
  → Scale up DB: increase read replica count
  → This is a band-aid, not a fix — find root cause after

Option D — Traffic shedding               (immediate)
  If the system is overloaded:
  → Enable rate limiting at the API gateway (if not already on)
  → Drop or queue low-priority request types
  → Protect core user journeys first (checkout > recommendations)

Option E — Region failover                (5–15 minutes)
  If a specific region is failing:
  → Shift traffic to a healthy region via DNS or load balancer
  → Only if your system is multi-region and configured for failover
```

**Say in interview:**
> *"First I stabilize — before I know root cause, I look at whether there's a fast mitigation. If the incident started after a deploy, I roll back the deploy immediately. If there's a feature flag, I kill it. I don't wait to understand why before stopping the user impact."*

---

### Phase 2 — Scope the Blast Radius

Before deep diagnosis, understand the size and shape of the problem. This determines how urgently to escalate and which teams to wake up.

**Scoping questions:**

```
1. How many users are affected?
   → Error rate × DAU = rough user count
   → Is it all users or a subset? (all requests? specific user IDs? specific regions?)

2. Which services are failing?
   → Check the dependency graph: is this our service or a downstream?
   → Distributed trace: which span is the bottleneck?

3. Is it getting worse, stable, or recovering?
   → Plot the error rate over time on the dashboard
   → If recovering: something upstream changed (auto-scaling kicked in,
     a bad request stopped, a batch job finished)
   → If worsening: you have less time — escalate sooner

4. What is the user-facing impact?
   → 500 errors on checkout page → critical, wake everyone up
   → 500 errors on a rarely-used admin endpoint → less urgent
```

**Say in interview:**
> *"Once I've stabilized or confirmed there's no fast mitigation, I scope: which users, which endpoints, which region, and is it getting worse? That tells me how hard to escalate and how much time I have before more users are affected."*

---

### Phase 3 — Diagnose Root Cause (The 4 Signals)

Use the 4 signals in order — metrics first (fastest), traces last (most detailed).

```
SIGNAL 1 — METRICS (start here, takes < 1 min)
─────────────────────────────────────────────
  Error rate:    Which endpoint? Which error code?
  Latency P99:   Which service is slow?
  Throughput:    Traffic spike? Or same traffic, worse performance?
  Saturation:    CPU, memory, thread pool, connection pool
                 → saturation metric = "how full is the bucket?"

  Saturation is the most overlooked signal. Latency rises when
  threads are queueing, not when a single request is slow.

SIGNAL 2 — EVENTS (check this second, takes < 2 min)
─────────────────────────────────────────────────────
  What changed in the last 30 minutes?
  → Deployment? (check deploy log for all services, not just yours)
  → Config change or feature flag change?
  → Cron job that fires at this time?
  → External event: traffic spike, partner API outage, upstream change?

  "What changed?" answers 80% of incidents. Most production
  incidents are not mysterious — they are caused by a change.

SIGNAL 3 — LOGS (when metrics + events don't give root cause)
──────────────────────────────────────────────────────────────
  Search for ERROR and WARN in the timeframe of the incident.
  Filter by service + error rate spike window.
  Look for:
    → Stack traces: what is the exception type?
    → Correlation IDs: does a single request ID appear in errors?
      (may indicate a specific bad payload or user triggering the error)
    → Volume: is one error type dominating? Or many different ones?

  If your logs don't have correlation IDs (request ID propagated
  through all log lines for a single request), that is a gap.
  You cannot trace a request through multiple services without it.

SIGNAL 4 — TRACES (when logs don't pinpoint the service)
──────────────────────────────────────────────────────────
  Open a distributed trace for a failed request.
  Spans show each service in the call chain with its latency.
  The span that is red / slow / missing = the failing service.

  → Latency in service A but not B → A has a problem
  → Latency in span "DB query" → database is slow
  → Span missing entirely → service crashed before responding
```

**Say in interview:**
> *"I use the 4 signals in order: metrics to confirm which service and error type, events to check what changed recently, logs to read the actual error message, traces to pinpoint which service in the call chain is failing. 'Check the logs' without the other three gives you a needle and no haystack size."*

---

### Phase 4 — Fix and Validate

**Say in interview:**
> *"I apply the fix and watch the metrics confirm recovery — not just 'lower,' back to pre-incident baseline. I watch for at least 10 minutes — some fixes cause a brief improvement then recur. If the fix doesn't resolve the incident, I revert it immediately and don't stack fixes."*

Apply the fix. Watch the metrics confirm recovery.

```
Fix → Metric confirmation:
  Deploy hotfix       → watch error rate drop within 1 traffic cycle
  Config change       → watch latency recover within 30 seconds
  Feature flag off    → watch error rate drop within 5 seconds
  Scale up replicas   → watch saturation metric drop

IMPORTANT: a metric confirmation is not just "errors went down."
  → Confirm: error rate below pre-incident baseline (not just "lower")
  → Confirm: P99 latency back to baseline (not just "improved")
  → Confirm: no new error types appeared (fix didn't shift the problem)
  → Watch for 10+ minutes — some fixes cause a brief improvement
    followed by recurrence (the root cause is still present)
```

---

### Phase 5 — Post-Mortem (Blameless, Within 48 Hours)

**Say in interview:**
> *"Within 48 hours I write a blameless post-mortem. The goal is to find the system failure that allowed the human mistake to reach production — not to blame the person who made the change. Root cause is never 'engineer made a mistake' — it's 'our system allowed this mistake to cause a production incident.' Action items have owners and due dates."*

**Post-mortem structure:**

```
1. Incident summary (2 sentences)
2. Timeline (UTC timestamps, what happened and when)
3. Root cause (the system condition, not the human action)
4. Contributing factors (what made it worse or harder to detect)
5. What went well (what helped us resolve faster)
6. Action items:
   → Each item: what, who, by when
   → Typically 3–5 items: monitoring gaps, process gaps, code gaps
   → No more than 5 — more than 5 means none get done
```

---

## 🧩 Interview Probe Q&As

**"Why do you stabilize before diagnosing?"**
> Because the cost of user impact compounds every minute. If I spend 20 minutes finding root cause while users get 500 errors, that's 20 minutes of customer trust eroded. A rollback takes 60 seconds and removes the user impact immediately. I can then diagnose at my own pace against a stable system — with full logs, full metrics, no pressure to cut corners. The root cause investigation is always better after the bleeding stops.

**"What if rolling back the deploy doesn't fix the incident?"**
> Then the incident is not caused by the most recent change — it's either (a) a pre-existing issue that reached a threshold (memory leak finally OOMed), (b) caused by an external dependency (a partner API started returning errors), or (c) caused by a change made more than one deploy ago. After ruling out the last deploy, I move to events: check for config changes, cron jobs, traffic spikes, or upstream changes in the last 24 hours.

**"How do you handle an incident if your distributed tracing isn't set up?"**
> You lose the ability to pinpoint which service in the call chain is failing without manual log grepping across multiple services — which is slow and error-prone. This is a gap. After the incident is resolved, this becomes a post-mortem action item: instrument all services with distributed tracing before the next incident. The answer in the interview is: set it up as a prerequisite, not reactively.

**"What's the difference between a P2 and a P1 incident?"**
> P1: user-facing core path is broken for a significant % of users (checkout, login, payment) — wake up the on-call chain immediately. P2: degraded experience for a subset of users or non-critical path is down — fix within business hours with monitoring. The classification determines the escalation cadence and communication frequency, not the diagnosis approach.

**"How do you communicate during an incident?"**
> Immediately create an incident channel (Slack, Teams). Assign an incident commander (one person owns the communication, others own the diagnosis). Send status updates every 15 minutes even if the update is "still investigating" — silence during an incident is worse than "we don't know yet." Stakeholders and support teams need to know you're aware. After resolution: send an all-clear with a brief summary and a link to the post-mortem.

**"What do you do if the fix you applied didn't resolve the incident?"**
> Revert the fix immediately — don't stack fixes. Each failed fix makes the system state harder to reason about. Document what you tried, what you observed, and what you rolled back. Then re-scope: are the metrics the same as before, or did they change? If they changed, the fix had a partial effect — the root cause may be layered. If unchanged, you eliminated a hypothesis — you now know what DIDN'T cause it, which is progress.

---

## 🧾 TL;DR — What to Say in the Interview (2 Minutes)

> *"I use a 5-phase framework. First, I stabilize — mitigate user impact before diagnosing root cause. If the incident started after a deploy, I roll back immediately. If there's a feature flag, I kill it. I don't wait to understand why before stopping the harm. Second, I scope — which users, which endpoints, which region, is it getting worse? That determines how hard to escalate. Third, I diagnose using the 4 signals in order: metrics to see which service and error type, events to check what changed recently, logs to read the actual error message, traces to pinpoint which service in the call chain is failing. Fourth, I apply the fix and watch the metrics confirm recovery — not just 'lower,' back to pre-incident baseline. Fifth, within 48 hours I write a blameless post-mortem: timeline, root cause in the system (not the person), and 3–5 action items with owners and due dates. The prerequisite for all of this is that metrics, centralized logs, alerting, and distributed tracing exist before the incident — if they don't, the absence is itself the first action item."*

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 11, 2026 | **Note created.** Batch 3 of Operational-Scenarios gap closure. Incident diagnosis framework — one of the most common senior engineering interview questions. Written with 8 known mistake classes applied: prerequisite observability infrastructure stated explicitly (Class 4), all 4 signals described with precise toolchain steps not just "check the logs" (Class 1), communication cadence and stabilize-first ordering are the key differentiators from a junior answer. |
