# System Design Patterns — Study Plan

> **When you have weeks:** Use the weekly plan. Read one pattern, practice 1-2 problems, then move on.
> **When you have 2 days:** Use the 2-day sprint below. Skip reading everything — only what's listed.

---

## 🗺️ Weekly Plan (Ideal — 3 Weeks)

Interleave reading and practice. Each pattern sticks better when you've felt the pain it solves.

| Week | Pattern | DeepDive File | Practice Problems |
|---|---|---|---|
| 1 | Scaling Reads | `DeepDive/01-scaling-reads.md` | Design URL shortener, Design Yelp |
| 1 | Scaling Writes | `DeepDive/02-scaling-writes.md` | Design a metrics/analytics system |
| 1 | Feed & Fanout | `DeepDive/03-feed-and-fanout.md` | Design Twitter / Instagram feed |
| 2 | Dealing with Contention | `DeepDive/04-dealing-with-contention.md` | Design ticket booking, flash sale |
| 2 | Multi-step Processes | `DeepDive/05-multi-step-processes.md` | Design e-commerce order flow |
| 2 | Long-Running Tasks | `DeepDive/06-long-running-tasks.md` | Design YouTube upload + transcode |
| 3 | Real-time Updates | `DeepDive/07-real-time-updates.md` | Design chat system, live scores |
| 3 | Handling Large Blobs | `DeepDive/08-handling-large-blobs.md` | Design Google Drive / Dropbox |
| 3 | Proximity Search | `DeepDive/09-proximity-search.md` | Design Uber / Yelp |

**How to practice each problem:**
1. **Clarify** (5 min) — users, scale, constraints
2. **Pick pattern** (2 min) — "this is a Feed & Fanout problem"
3. **Name the strategies** (5 min) — 3 options, choose one with reason
4. **Draw the architecture** (20 min) — boxes, arrows, data flows
5. **Address 2-3 failure modes** (10 min) — crashes, scale spikes, consistency
6. **Name what you'd NOT do and why** (3 min) — anti-pattern callout

---

## ⚡ 2-Day Sprint (Interview in 2 days)

Don't try to cover everything. Cover 5-6 patterns deeply and practice answering.

### Day 1 — Build Mental Frameworks

| Time | Task |
|---|---|
| 2h | Read `01-scaling-reads.md`, `02-scaling-writes.md`, `03-feed-and-fanout.md` |
| 1h | Attempt **Design Twitter feed** — out loud, no notes, paper/whiteboard |
| 1h | Read `06-long-running-tasks.md`, `07-real-time-updates.md` |
| 1h | Attempt **Design YouTube upload** OR **Design a chat system** |
| 30m | Read `08-handling-large-blobs.md`, `04-dealing-with-contention.md` |

> **Add `09-proximity-search.md`** only if interviewing for Uber / DoorDash / Lyft / Maps-type company.

### Day 2 — Practice Answering, Not Reading

| Time | Task |
|---|---|
| 30m | Skim the **Decision Sequences only** in all notes you read yesterday |
| 1.5h | Attempt **Design Google Drive / Dropbox** (Large Blobs + Long-Running Tasks combined) |
| 1.5h | Attempt **Design a notification system** (Scaling Writes + Real-time Updates combined) |
| 1h | Attempt **Design ticket booking / flash sale** (Dealing with Contention) |
| 30m | Read **Anti-patterns sections only** across all notes — this is what interviewers listen for |

### Day of Interview

> 30 minutes before: read only the **Decision Sequences** from the 5 patterns you covered.
> That's the mental checklist your brain runs during the answer. Do not re-read full notes.

---

## 🧭 Pattern → Problem Quick Map

Use this when you see a problem and need to know which pattern applies.

| Interview Problem | Primary Pattern | Secondary Pattern |
|---|---|---|
| Design Twitter / Instagram feed | Feed & Fanout | Scaling Reads |
| Design YouTube | Long-Running Tasks | Handling Large Blobs |
| Design Uber / Lyft | Proximity Search | Real-time Updates |
| Design Google Drive / Dropbox | Handling Large Blobs | Long-Running Tasks |
| Design a chat system (WhatsApp) | Real-time Updates | Scaling Writes |
| Design a notification system | Scaling Writes | Real-time Updates |
| Design ticket booking / flash sale | Dealing with Contention | Multi-step Processes |
| Design an e-commerce order flow | Multi-step Processes | Dealing with Contention |
| Design a URL shortener | Scaling Reads | Scaling Writes |
| Design a metrics / analytics system | Scaling Writes | — |
| Design DoorDash | Proximity Search | Feed & Fanout |
| Design a rate limiter | Dealing with Contention | — |

---

## 🧾 TL;DR

- **Weeks available:** Read each DeepDive → practice 2 problems → next pattern. Interleaved.
- **2 days:** Day 1 = read 5-6 patterns. Day 2 = attempt 3 questions. Day of = Decision Sequences only.
- **During any interview:** Name the pattern first. State 2-3 strategies. Choose one with a reason. Draw. Address failures. Call out one anti-pattern.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| July 2026 | Created after all 9 DeepDive notes were complete. |
