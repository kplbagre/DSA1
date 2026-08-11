# Salesforce SMTS — HLD + LLD Round Format

**Round:** Technical Design (combined)
**Duration:** 90 minutes
**Scheduled:** Aug 2026

---

## Time Split

```
~35 min  — LLD (OOP class design + Java code)
~45 min  — HLD (system design, architecture)
~10 min  — clarifying questions, buffer, wrap
```

Order is usually LLD first, HLD second — but ask the interviewer at the start.

---

## LLD Half (~35 min)

**Format:** Class design on shared doc. Not boxes — actual `interface`, `class`, method signatures, core Java logic.

**Most likely problems:**

| Problem | Why Salesforce picks it |
|---|---|
| Pub/Sub event system | Maps to Salesforce Platform Events |
| Rate limiter (code level) | Token bucket / sliding window — tests real implementation |
| Workflow / rules engine | "If record changes and condition X, trigger action Y" — their core product |
| Parking lot / Library system | Classic OOP — inheritance, polymorphism |

**What they evaluate:** SOLID principles (say the names), design patterns (Strategy, Observer, Factory), extensibility ("what if we add a new type?"), clean Java — not pseudocode.

---

## HLD Half (~45 min)

**Format:** Distributed system design — whiteboard style, requirements → components → deep dive.

**Most likely problems:**

| Problem | Notes |
|---|---|
| Notification / messaging system | 100M users, push + email |
| Search over CRM records | Full-text, filters, ranking |
| Rate limiter (system level) | Complements LLD rate limiter at code level |
| Workflow automation engine | Event-driven, rule evaluation |
| Bulk data import pipeline | 10M records from CSV — batch processing |

**Salesforce-specific angle to raise proactively:**
> Multi-tenancy — Salesforce serves 150,000 orgs on shared infrastructure. For any design: how does it isolate per-org load, per-org data, per-org rate limits? Raising this before they ask signals you understand their world.

---

## Watch-out: Clock Management

90 min feels long but 35 + 45 is tight. Common failure mode: spending 50 min on LLD, leaving 25 for HLD.

At the 30-minute mark of whichever half you're in — start wrapping, even if incomplete:

> "I could go deeper on X, but I want to make sure we have time for the [HLD/LLD] — should I continue or move on?"

That signal reads as senior.

---

## Strengths Carried Over from Confluent Prep

- API design, HTTP verbs, status codes → HLD API layer
- Kafka / event-driven architecture → Salesforce Platform Events
- DB modeling, cursor pagination, pessimistic locking → HLD data layer
- Idempotency reasoning → HLD reliability layer

## Gap to Fill Before Interview

- LLD OOP problems — need Java class design practice (deep dive TBD)
- Multi-tenancy patterns — shared DB shared schema vs shared DB separate schema vs separate DB (deep dive TBD)
