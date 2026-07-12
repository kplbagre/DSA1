# Interview Debrief — Notification Service Migration

> **Date:** Jul 11, 2026
> **Round:** System Design (R2 equivalent)
> **Question type:** Migration / Microservice extraction — NOT a greenfield design

---

## 🎯 The Actual Problem (Read This First)

> User "events" are being stored in a monolith. Examples: "Bob has signed your document", "Check out our new AI feature", "Your subscription renewal failed."
> Events start off "unread" and are marked as "read" after being viewed on the client. They can also be deleted by the user.
> **We want to migrate this data to a new micro-service. How do you manage this migration?**

**Key word the interviewer cared about:** *migrate* — not *redesign*, not *build from scratch*.

---

## 🧭 What Actually Happened (Honest Timeline)

### Minutes 0–15: Project discussion
Normal project deep-dive. Not wasted time — this is standard for the format.

---

### Minutes 15–25: Ambiguous start — partially interviewer-induced

The interviewer verbally opened with *"how will you design this"* — which is a different framing from the written "how do you manage this migration." Responding to "design" with a design answer is not unreasonable.

**Interviewer pushback:** "I don't want you to rewrite the monolith — just extract the notification service."

**What to do differently:** One clarifying question at the start would have eliminated this entirely:
> *"Are you asking me to design the new service from scratch, or walk through the migration strategy for extracting it from the monolith?"*

That 10-second question is the only real miss here. The ambiguity was real but it was resolvable before starting.

---

### Minutes 25–30: API design focus

Once understood it was extraction, shifted to API design — what endpoints the new notification service would have.

**Interviewer pushback:** "I want the migration flow — how do you move data and traffic safely."

**Root cause:** API design is relevant but secondary. The question was testing migration strategy first. The order should have been: migration plan → then API design falls out naturally.

---

### What Went Right — Minutes 30–60

Once on the right track, the following was laid out correctly:

**1. New dedicated DB for notification service**
Don't share the monolith DB. The new service gets its own Postgres instance.

**2. Data copy from old DB to new DB**
Before going live, copy existing notification data from monolith DB to new DB. This is the backfill job.

**3. Kafka for event publishing**
Monolith publishes events to Kafka (topic: `user.events`) instead of calling the notification logic in-process. New notification service consumes from Kafka.

> Why Kafka and not direct HTTP: Monolith doesn't block if notification service is down. Events are durable — notification service can replay. Fully decoupled deployments.

**4. Dual write during transition**
During migration, write to BOTH old DB and new DB. This keeps new DB in sync even while monolith is still live. Old DB stays source of truth.

**5. Canary traffic — 5% to start**
Don't flip 100% of traffic to the new service immediately. Route 5% of reads to the new service, compare responses with the monolith, validate correctness.

**6. The inactive user problem (interviewer prompted)**

Interviewer pushback: *"How do you validate users who haven't logged in for weeks? 5% traffic won't cover them."*

Correct answer (arrived at with some help):
- You cannot wait for inactive users to organically hit the 5% canary
- Run a **background job** that proactively fetches data for inactive users from both old and new service and compares
- Critical constraint: this job must be **read-only** — it must NOT trigger writes or it corrupts the DB state during dual-write phase
- The new service calls the monolith for comparison, NOT the monolith calling the new service (new service owns the comparison logic)

```
Background Job (read-only)
  → call monolith GET /notifications?userId=X
  → call new service GET /notifications?userId=X
  → compare response
  → log any mismatch
  → alert if mismatch rate exceeds threshold
```

**7. Periodic DB consistency check**
After traffic is shifted, run a reconciliation job periodically to compare both DBs row by row. Any gap = bug, investigate and fix before decommissioning old DB.

---

## 🎨 Visual — Final Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                        MONOLITH                                 │
│  Business Logic (sign, payment, feature)                        │
└──────────────────────────┬──────────────────────────────────────┘
                           │ publish event
                           ▼
                 ┌─────────────────┐
                 │     KAFKA       │
                 │ topic:          │
                 │ user.events     │
                 │ partition:      │
                 │ user_id         │
                 └────────┬────────┘
                          │ consume
                          ▼
          ┌───────────────────────────────┐
          │    NOTIFICATION SERVICE        │
          │                               │
          │  create / read / mark / delete │
          │  SSE publication to client     │
          │                               │
          │  ┌─────────────────────────┐  │
          │  │    New Notification DB  │  │
          │  └─────────────────────────┘  │
          └──────────────┬────────────────┘
                         │
              ┌──────────┴───────────┐
              │                      │
     ┌────────▼──────┐    ┌──────────▼──────────┐
     │  Browser /    │    │  Background Job      │
     │  Client       │    │  (read-only compare) │
     │  (reads notif)│    │  inactive users      │
     └───────────────┘    │  → call monolith     │
                          │  → call new service  │
                          │  → compare & alert   │
                          └─────────────────────-┘

During migration:
Monolith DB ◄──── dual write ────► New Notification DB
                    (both receive writes until cutover)
```

---

## 🧾 Migration Phases Summary

| Phase | What happens | Risk |
|---|---|---|
| **1. Build new service** | New notification service + new DB created | No risk — not live yet |
| **2. Backfill DB** | Copy existing notification rows from monolith DB to new DB | Medium — large data job |
| **3. Dual write** | All writes go to both DBs; monolith DB is source of truth | Low — old path unchanged |
| **4. Shadow read (5%)** | 5% of read traffic goes to new service; compare with monolith | Low — users see monolith response |
| **5. Background job** | Proactively validate inactive users (read-only compare) | None — read-only |
| **6. Ramp traffic** | Gradually increase to 10% → 25% → 50% → 100% | Increasing — monitor error rate |
| **7. Decommission** | Remove notification code from monolith; archive old DB table | Medium — no rollback after this |

---

## ⚠️ Key Constraints Stated Correctly

- Background comparison job must be **read-only** — never triggers writes
- New service calls monolith for comparison — NOT the other way around
- Traffic ramp only after background validation passes for inactive users
- Periodic DB consistency check continues until old table is decommissioned

---

---

## 🧾 Honest Review

### Context (important — initial review was wrong)

Corrected timeline:
- Minutes 0–15: project discussion — normal, not wasted
- Minutes 15–25: design direction — **partially interviewer-induced** (interviewer said "how will you design" on a written problem that said "how do you manage this migration" — that's a genuine contradiction)
- Minutes 25–30: API focus — minor course correction needed
- Minutes 30–60: correct migration strategy, executed well

This is a much better performance than the initial read suggested.

---

### What went wrong — specific

**1. No clarifying question at the start.**
When the interviewer says "design" but the problem says "migrate" — that contradiction is resolvable in 10 seconds:
> *"Are you asking me to design the new service from scratch, or walk through the migration strategy for extracting it?"*
That question was not asked. It would have saved one pushback and ~10 minutes. This is the primary miss.

**2. The inactive user insight required the interviewer's hint (the diamond).**
This is the main technical miss. A senior engineer should proactively say:
> *"Before I ramp traffic, I need to validate users who haven't logged in recently — canary alone won't cover them. I'd run a background read-only comparison job for those users."*
The insight arrived but only after the interviewer drew the shape. At SDE-3, you want to be the one raising this proactively.

---

### What went right

- Kafka + dual write + canary = correct architecture
- "Read-only background job" constraint = correct and important
- "New service calls monolith, not monolith calling new service" = correct ownership model
- Periodic consistency check = correct production thinking
- First 15 min project deep-dive = positive signal on real-world experience

**The technical depth was solid. The framing gap was small and situational.**

---

## 📊 Revised Chances of Selection

**40–50%** (revised up from initial 25–35%)

The early misdirection was ~10 minutes on an ambiguously framed question — not 30 minutes of wrong thinking. The migration strategy itself was technically sound. The one real gap — proactively raising the inactive user problem — is a miss but not a disqualifier.

What tips the range:
- If interviewer weighs the strong second half heavily → higher end (50%)
- If the inactive user hint is noted negatively → lower end (40%)
- If other candidates had cleaner starts → competitive pressure applies

---

## 📚 Was This Covered in Prep?

| Topic | Covered? | Where |
|---|---|---|
| Notification service design | ✅ Yes | `D3-notification-service.md` |
| Kafka fan-out pattern | ✅ Yes | `D3` Section 7 deep dives |
| Dual write / canary traffic | ⚠️ Mentioned but not drilled | Scattered across solution files |
| Strangler Fig migration pattern | ❌ No | Not in any solution file |
| Inactive user / shadow testing validation | ❌ No | Not covered at all |

**Gap to fix:** Prep files cover designing services well. They do not cover migrating existing services. Strangler Fig + shadow mode + canary + reconciliation job is a standalone pattern that needs its own concept note in `SystemDesignConcepts/`.

---

## 🔄 Changelog

| Date | Entry |
|---|---|
| Jul 11, 2026 | **Debrief created.** Live interview experience — notification service migration from monolith to microservice. |
| Jul 11, 2026 | **Review revised.** Initial assessment of "30 min wasted / 25–35% chance" corrected after candidate clarified: 15 min was project discussion (not wasted), and early misdirection was partly due to interviewer saying "design" on a migration question. Revised to 40–50%. One confirmed technical miss: inactive user validation required interviewer hint. |
