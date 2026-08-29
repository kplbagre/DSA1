# MCSE — Features & Failure Modes (the "explain something complex" + "how do you handle failure" bank)
### Deep, teachable walkthroughs of the hard features you built, and the failure taxonomy you operate

> **What this is:** Two things the HM/technical round always probes — (1) *"Walk me through a genuinely complex thing you designed"* and (2) *"How does your system fail, and how do you handle it?"* Each feature and each failure mode is taught **concept-first with a diagram**, then given a **say-this → pushback → answer**.
>
> **Why these features:** They're the ones with a non-obvious design insight — the kind that proves you *designed* the thing, not just coded a ticket. Predictive delivery-time (§1) is your strongest "complex feature" story; multi-slot (§2) is your strongest "backward-compatible contract change" story.
>
> ⚠️ **Confidentiality:** internal codenames/error-codes/config-keys are replaced with plain-English concepts. Describe the *taxonomy and mechanism* in the room, never the internal identifier.
>
> 🌉 **Companion files:** architecture → [MCSE-PROJECT-DEEPDIVE.md](MCSE-PROJECT-DEEPDIVE.md) · decisions → [MCSE-DECISION-LOG.md](MCSE-DECISION-LOG.md) · behavioral stories → [MCSE-interview-stories.md](MCSE-interview-stories.md).

---

## 🧾 Table of Contents

**Part A — Advanced Features (the "explain something complex" bank)**
1. [Predictive Delivery-Time — the ML-fed EDD, and the double-count gotcha](#1--predictive-delivery-time)
2. [Canada Multi-Slot — Express + Standard in one backward-compatible response](#2--canada-multi-slot)
3. [Order Regrouping (batching) — the peer service that calls back into MCSE](#3--order-regrouping)
4. [Clearance-Aware Sourcing — the anchor-node correctness problem](#4--clearance-aware-sourcing)
5. [Owning node-classification (removing an upstream dependency)](#5--owning-node-classification)

**Part B — Failure Modes & Resilience (the "how do you handle failure" bank)**
6. [The error taxonomy — 4 failure classes and how to triage each](#6--the-error-taxonomy)
7. [The 5 resilience layers (defense in depth)](#7--the-5-resilience-layers)
8. [Graceful degradation — "stale data over no data," "degraded but on-time"](#8--graceful-degradation)
9. [Bad config — the scariest failure mode (invisible to infra dashboards)](#9--bad-config-the-scariest-failure-mode)
10. [Back-pressure on both sides (read fail-fast vs write slow-down)](#10--back-pressure-on-both-sides)
11. [Failure severity ranking (what pages you at 2am, in order)](#11--failure-severity-ranking)

---

# Part A — Advanced Features

## 1. — Predictive Delivery-Time

**One-line pitch:** We replaced a hand-maintained, carrier-*promised* transit time with a **machine-learned prediction of actual delivery performance** — and the subtle part isn't the model, it's that *the date formula itself changes* when you use it.

### 1.1 The problem with the old way

The delivery date (EDD — Estimated Delivery Date) was computed from the carrier's **promised** transit time: a static number a team maintained by hand, padded with **performance buffers** when a lane underperformed. Problems:

- Buffers were added **manually** — slow, error-prone, reactive.
- It couldn't capture **day-of-week effects** (Monday shipments run slower than Wednesday on some lanes).
- One number per lane didn't adapt to **seasonality**.

### 1.2 The insight — the model *absorbs* the buffers

The model learns from the **last ~4 weeks of actual deliveries** and predicts a transit time per `DC × carrier-method × zip × day-of-week`. Here's the part interviewers latch onto:

```
🎨 Visual — why you must CHANGE the formula, not just swap the input

  CARRIER-PROMISED path:            PREDICTED path:
  base transit time                 predicted transit time
    + performance buffer            (   ✗ NO performance buffer  )  ← model already learned it
    + weather buffer                  + weather buffer            ← still exogenous, still add
    + transit/delivery calendars      (   ✗ skip calendars,       )  ← model already absorbed them
                                          holiday overrides only  )
    ─────────────────────             ─────────────────────
    = EDD                             = EDD

  KEY INVARIANT:
     The two transit-time sources are NOT interchangeable inputs to one formula.
     The prediction has ALREADY absorbed the buffers/calendars from training on actuals.
     Re-applying them = DOUBLE-COUNTING = an over-conservative (too-late) date.
```

**The one-liner that lands:** *"The model already knows Mondays are slow on that lane — so if I also add a Monday buffer on top, I've counted it twice and promised a date later than reality. Using the prediction means turning the buffers off."*

### 1.3 The design that keeps the hot path safe

Two design decisions matter:

1. **No inline model inference.** The model runs **offline** on the ML team's infra; its output lands in an in-memory snapshot (Hollow) that the request path **looks up** sub-microsecond. The hot path stays deterministic — no model latency, no model outage on the 700K-rpm path. (See [MCSE-DECISION-LOG.md #13](MCSE-DECISION-LOG.md#13--offline-ml-batch--hollow).)
2. **Guardrails decide *which* transit time to trust, per request**, applied in order:

```
1. Feature enabled for this market/lane?            no → carrier-promised
2. Does a prediction exist for this DC×method×zip×day? no → carrier-promised
3. Is |predicted − planned| > 10 days?              yes → carrier-promised (deviation too large, distrust it)
4. Was the carrier number just recalibrated (<14d)?  yes → carrier-promised (fresh human input wins)
5. Is a known-bad-lane buffer present?              yes → carrier-promised (lane flagged problematic)
6. all clear                                        →   USE PREDICTION (and turn off buffers/calendars)
```

Every request logs **which source was used + the confidence + a reason code**, streamed to a trace store and a BI dashboard, so we can *prove* adoption and root-cause a wrong date.

### 1.4 Business framing (have the "why it mattered" ready)

Goal: lift on-time delivery past ~95% and **tighten** the average promise by ~0.25 days (a more accurate, often *sooner* date → more conversions), worth meaningful incremental GMV. The senior point: **a tighter date is only valuable if it's still accurate** — hence the guardrails and the deviation kill-switch.

> **Pushback: "Why not just do real-time inference for the freshest prediction?"**
> "Delivery-lane behavior moves on the order of hours to days, not seconds, so a daily/near-daily batch captures essentially all the signal. Real-time inference would put a variable-latency, separately-failing model call directly on a sub-100ms, 700K-rpm path — one model hiccup becomes a customer-facing latency spike. Pre-computing offline and serving from cache keeps the hot path deterministic. It's the accuracy-vs-reliability trade, and at this scale a fraction-of-a-percent accuracy gain isn't worth a new hot-path failure mode."

> **Pushback: "What if the model is confidently wrong for a lane?"**
> "That's what the guardrails are for. The deviation rule (step 3) says if the prediction disagrees with the planned number by more than a threshold, I distrust the model and fall back to the carrier number — a confidently-wrong outlier gets caught there. There's also a per-lane kill-switch and a market-wide one, both runtime-config so I can cut over to carrier-promised in seconds without a deploy. And because every request logs which source it used, a bad lane shows up in the dashboard as an adoption or accuracy anomaly, not as silent customer harm."

> **Pushback: "How do you know the prediction is actually better in production?"**
> "The trace logs the source, the predicted value, the carrier value, and later we join against actual delivery outcomes — so the model's accuracy is measured against reality on a BI dashboard (on-time %, promise-date distribution), per market and lane. Adoption and accuracy are monitored continuously; if predicted-source on-time drops below carrier-source, the guardrails and kill-switch let me pull back. It's not 'ship and hope' — the feedback loop is built into the trace contract."

---

## 2. — Canada Multi-Slot

**One-line pitch:** The engine assumed **one delivery option per item**; Canada needed to show **Express *and* Standard simultaneously** — which meant changing a **core response contract that four upstream teams consume** (Search, Item Page, Cart, Checkout) without breaking any of them.

### 2.1 Why it was hard

It wasn't the sourcing logic — it was the **blast radius of a contract change**:

```
🎨 Visual — one contract, four consumers, zero allowed breakage

              MCSE response contract
                      │
     ┌────────┬───────┴───────┬──────────┐
   Search  Item Page        Cart      Checkout
   (each on its own release train, can't be forced to migrate together)

  A breaking change here cascades across the ENTIRE checkout funnel at once.
```

### 2.2 The design — additive, not a clean v2

```
OLD response:  { price, deliveryDate, node, ... }              ← single implicit slot
NEW response:  { price, deliveryDate, node, ...,               ← OLD fields STILL populated
                 slots: [ {type:Express, price, date, isDefault:true},
                          {type:Standard, price, date} ] }     ← NEW array added alongside
```

- **Old single-slot fields stay populated** → consumers who haven't migrated keep working unchanged.
- **New `slots[]` array added alongside** → consumers opt in on their own timeline, reading `isDefault` to know which slot to show first.
- **Per-market feature flag** → shipped dark, enabled for Canada only, percentage-ramped.
- **Reservation path refactored to one inventory hold per slot**, with **diff-based re-hold** on re-source (don't release-and-reacquire everything when a cart edits — compute the delta).
- **Validated with shadow traffic** — replayed production Canada traffic through the new path and **diffed** responses against the old before cutover.

### 2.3 The edge cases (have these — they prove you thought it through)

| Edge case | Problem | Solution |
| --- | --- | --- |
| **Partial confirmation** | Customer picks Express; Standard slot was also held | Release the unused Standard hold **without leaking inventory** |
| **Re-reservation** | Cart edit re-sources; slots may change | Diff the holds, don't full-release-and-reacquire |
| **Slot-price drift** | Slot price changed between promise and checkout | Surface the delta; let the checkout layer decide whether to re-prompt |

> **Pushback: "Carrying old + new fields is tech debt — why not a clean v2 contract?"**
> "It's deliberate, documented debt with a payoff: four consumers on four different release trains could migrate **independently** instead of coordinating a synchronized flag-day across the whole checkout funnel — which at this scale is a scheduling nightmare and a single huge risk. Additive means each team adopts `slots[]` when ready while the old fields keep them running. I logged it as debt with a cleanup path — retire the single-slot fields once all four consumers are on the array. I'd rather carry two shapes for a while than blockade four teams behind one cutover."

> **Pushback: "How did you validate you didn't break the four consumers?"**
> "Shadow traffic. I replayed real production Canada requests through the new path and **diffed** the responses field-by-field against the existing path — so I could see any unintended change to the old fields *before* a single customer hit the new code. Then a per-market flag let me ramp Canada from a small percentage up, watching each consumer's error and latency metrics. The additive design plus shadow-diff plus staged ramp meant the risk was measured at every step, not discovered in prod."

> **Pushback: "Why one hold per slot instead of one hold you split later?"**
> "Because the two slots can source from different nodes with different inventory — they're genuinely separate reservations, and treating them as one hold you split later creates a moment where the accounting is ambiguous and inventory can leak on partial confirmation. One hold per slot makes release explicit: confirm Express, release Standard's hold cleanly. The diff-based re-hold then optimizes the *common* case (cart edit) so I'm not releasing and reacquiring holds that didn't change."

---

## 3. — Order Regrouping

**One-line pitch:** A **peer service** regroups already-sourced orders into shared shipments (two orders to the same address 4 hours apart → ship together in one box). The interesting design point: it operates on *completed* sourcing decisions, so it must **call back into MCSE** — which is why it's a peer, not a sub-service.

### 3.1 The architecture insight

```
🎨 Visual — why the regrouper is a PEER, not below MCSE

   MCSE sources orders  ──►  order is "done"
                                  │
                     Regrouper sees two orders to same dest
                                  │
                     wants to combine → must RE-SOURCE the combined basket
                                  │
                                  └──► calls BACK into MCSE  ◄── this is the key point

  KEY INVARIANT:
     A service that calls back into its "parent" cannot be the parent's sub-service.
     Operating on completed decisions ⇒ it sits at the same level and re-enters sourcing.
```

### 3.2 The 6-step batch flow (with the safety net)

```
1. Get latest order status (from the order-management system)
2. Get latest sourcing status (from MCSE) for those orders
3. Swap the inventory reservations (move the hold to the shared node)
4. Update the order-management system with the new sourcing
5. IF step 4 fails → ROLL BACK the reservation swap (atomic — hold returns to original)  ← the safety net
6. Asynchronously update capacity with the new picture
```

**Step 5 is the whole point:** without an atomic rollback, a mid-way failure **double-holds or leaks inventory**. Clearance items are **excluded** from regrouping entirely (see §4 — swapping a clearance item to another node would charge the customer the wrong price).

> **Pushback: "Why not make regrouping part of MCSE to avoid the round-trip?"**
> "Because it operates on a fundamentally different lifecycle stage. MCSE answers 'where and when *now*' synchronously on the hot path; regrouping is an **asynchronous, after-the-fact optimization** across *already-placed* orders, and to regroup it must re-source the combined basket — i.e., call back into MCSE. Folding it in would couple a batch optimizer to a sub-100ms request engine and blur two very different SLOs. Keeping it a peer means each has one job, and the round-trip is cheap relative to the async batch cadence."

---

## 4. — Clearance-Aware Sourcing

**One-line pitch:** Clearance items are marked down **at one specific store** and **cannot be re-shopped from another node** without charging the customer the wrong (national) price — so sourcing has to pin them to an "anchor node" and defend against every code path that might move them.

### 4.1 The correctness model

```
🎨 Visual — anchor node vs national fallback

  Clearance offer
     ├── Anchor node       = the discounting store (clearance price)   ← must source here if possible
     └── National fallback = a fallback node at the national (non-clearance) price

  IF anchor node can fulfill  → serve anchor price, filter out all alternatives
  IF anchor node cannot       → fall back to national price from an alternate node

  KEY INVARIANT:
     A clearance line sourced from the wrong node = customer charged the wrong price
     = a customer-overcharge incident. Correctness dominates optimization here.
```

### 4.2 Why it touches so many features (exclusions)

Because moving a clearance item is a *correctness* bug, it's **excluded** from every optimization that might relocate or defer it: basket consolidation, pre-purchase consolidation, preferred-day, pickup-from-arbitrary-store, directed sourcing, order-regrouping, scheduled sourcing.

### 4.3 The double safety net (this is the senior detail)

The order-management system normally triggers a **re-shop** when an item goes out of stock — which for a clearance item would re-source it at the wrong price. Two independent nets:

1. **Primary:** clearance responses set an indicator the order-management system checks before re-shopping.
2. **Backup:** because the upstream "is re-shop eligible" flag isn't always accurate, **MCSE itself independently blocks re-shop** when the item is clearance.

**Two nets because the failure is a customer-overcharge — you don't rely on a single upstream flag for a money-correctness invariant.**

> **Pushback: "Isn't a second safety net redundant if the first works?"**
> "For most features, yes — but here the failure mode is charging a customer the wrong price, which is a trust-and-compliance incident, not a degraded experience. The primary net depends on an **upstream** flag I don't fully control and that's known to be occasionally inaccurate. So MCSE enforces the invariant **itself** as a backup — defense in depth for a money-correctness rule. The cost is a little duplicated logic; the benefit is that no single upstream inaccuracy can cause an overcharge. I reserve this level of paranoia for correctness-of-money, not for everything."

---

## 5. — Owning node-classification

**One-line pitch:** MCSE used to ask an **upstream service** to classify which nodes were eligible for an offer; we migrated that classification **into MCSE** (always fetch inventory ourselves, run the classification rules locally) so we could eventually **remove the upstream call entirely** — fewer hops, fewer failure modes, more control.

**The migration was incremental** (strangler-fig again): inherit the classification rules, introduce the new local path, keep a fallback to the upstream call until the local path is fully proven, then remove the dependency. (See [MCSE-DECISION-LOG.md #16](MCSE-DECISION-LOG.md#16--strangler-fig-v3v5-migration).)

> **Pushback: "Why absorb an upstream's responsibility — isn't that scope creep?"**
> "It's the opposite — it's removing a dependency that added a network hop and a failure mode without adding value. The upstream was classifying nodes from data MCSE already had to fetch anyway (inventory), so the call was pure overhead: one more thing that could time out on the hot path. Bringing the rules in-house means one fewer hop, one fewer breaker, and full control over the logic. I did it incrementally with a fallback to the old call so there was never a flag-day risk."

---

# Part B — Failure Modes & Resilience

## 6. — The error taxonomy

**The framing that sounds senior:** *"We don't return generic 500s. Every way sourcing can fail is a **typed reason** with a distinct root-cause path — so an operator reads a reason code and knows where to look, instead of grepping log soup."*

Four failure **classes** (I'll use plain-English names; internally they're typed codes):

| Class | Means | Root-cause category | First thing to check |
| --- | --- | --- | --- |
| **Reference-data-unavailable** | The offer/reference row is missing or the cache missed | Ingestion lag / cache staleness | Kafka consumer lag → cache snapshot version → Cassandra row |
| **Inventory-unavailable** | Inventory service returned zero (or timed out) | True out-of-stock **or** inventory-service outage | Is it one node (local) or all nodes (global outage / circuit open)? |
| **No-valid-fulfillment-option** | Every candidate node got filtered out | Eligibility / capacity / carrier constraint | Capacity flip? carrier-serves-zip? seller template? clearance pin? |
| **Date-calculation-failure** | Sourced fine, but couldn't compute the date | Transit-time cache miss / date-service error | Is the transit time present in cache? date-service healthy? |

**The triage skill:** for *inventory-unavailable*, the decisive question is **"one node or all nodes?"** — one node is a local OOS or a pool exhaustion; all nodes is an inventory-service outage or a globally-open circuit. That single branch changes the entire investigation.

> **Pushback: "Why typed reasons instead of just logging the failure?"**
> "Because at 700K rpm, 'just logging' means an operator drowns in log soup during an incident. A typed reason code turns triage into a lookup: this code always means ingestion staleness, that code always means a filtered candidate. It also lets me **alert on the rate of a specific failure class** — a spike in 'no-valid-fulfillment-option' in one market instantly points at a capacity flip or a bad config, not at inventory. Typing the failures is what makes the system operable by someone who isn't me at 2am."

---

## 7. — The 5 resilience layers

Defense in depth — each layer catches a failure the others can't:

```
Layer 1  Runtime feature flags   → kill any feature instantly, per-market, zero restart
Layer 2  Circuit breakers        → wrap every external call; failure rate crosses threshold → trip → fast-fail → fallback
Layer 3  Thread-pool bulkheads   → dedicated pool per dependency; a slow one can't starve the others
Layer 4  Fail-fast queue         → zero-capacity queue → overload rejects immediately → bounded latency
Layer 5  In-memory Hollow cache  → survive a Cassandra outage; 99%+ of reads served from local snapshot
```

**Circuit-breaker states** (know this cold — it's a classic follow-up):

```
CLOSED  (normal)   → requests pass through; failure counter increments on each failure
OPEN    (tripped)  → all requests instantly return fallback; downstream gets ZERO traffic (time to recover)
HALF-OPEN (probing)→ let ONE probe through; success → CLOSED, failure → OPEN again
```

The full "why five, not one" defense is in [MCSE-DECISION-LOG.md #18](MCSE-DECISION-LOG.md#18--resilience4j--5-resilience-layers).

> **Pushback: "What actually happens to a customer when the inventory service is fully down?"**
> "The breaker trips OPEN within its window, so we stop hammering the dead service and instead serve from the last-known-good inventory snapshot in the in-memory cache — the customer gets a **conservative but on-time** promise rather than an error. Meanwhile the breaker sits half-open, sending one probe periodically; the moment the service recovers, the probe succeeds and we close the breaker and resume live calls. The customer never sees a spinner-to-timeout; they see a slightly conservative date. Degraded, not down."

---

## 8. — Graceful degradation

Two principles, stated as slogans you can say in the room:

1. **"Stale data over no data."** A slightly stale in-memory snapshot is an acceptable answer; a hard failure is not. The only time we return reference-data-unavailable is when **both** the cache missed **and** the DB is down — every other path serves last-known-good.
2. **"Degraded but on-time beats correct but late."** On the read path, a fast conservative answer wins over a perfect answer that arrives after the customer's timeout. There's a dedicated fast-path that, when the full pipeline can't finish in budget, returns a bounded response rather than hanging.

```
🎨 Visual — the degradation hierarchy for a read

  live upstream call  ──fail──►  in-memory snapshot (last-known-good)  ──miss──►  fast conservative fallback
     (best answer)                  (slightly stale, still good)                    (degraded, still on-time)
                                                                                          │
                                                              only if EVERYTHING fails ──►  typed error

  KEY INVARIANT:
     Always return SOMETHING within the SLA window. Never hang. Never a bare 500.
```

> **Pushback: "'Degraded but on-time' — wouldn't a customer rather wait for the right answer?"**
> "On a product page or cart, no — a delivery date that shows up after the page has rendered (or timed out) is worthless; the customer has already scrolled or bounced. A conservative-but-instant date keeps them in the funnel, and 'conservative' means we err toward a date we're *more* likely to keep, so we don't over-promise. The place this flips is money operations — there I'd fail-fast with an **explicit error and retry**, not a silent conservative default, because a silent fallback on a payment is a correctness bug. Same instinct, domain-tuned."

---

## 9. — Bad config, the scariest failure mode

**The insight that impresses:** *"The most dangerous failures in my system aren't crashes — crashes are loud. It's a bad **config** value: every service healthy, every pod up, every circuit closed, every dashboard green — and the system quietly doing the wrong thing."*

```
🎨 Visual — why bad config is worse than an outage

  Infrastructure failure:              Bad config:
  ┌───────────────────────┐            ┌───────────────────────┐
  │ latency spikes ⚠️      │            │ latency: normal ✅     │
  │ error rate alerts ⚠️   │            │ error rate: 0 ✅       │
  │ circuit trips ⚠️       │            │ circuits: all closed ✅│
  │ pods restart ⚠️        │            │ pods: all healthy ✅   │
  │ → runbook fires        │            │ → NOTHING fires        │
  └───────────────────────┘            │ → wrong dates, lost $$ │
        LOUD, caught fast              └───────────────────────┘
                                         SILENT, caught by customers

  Example: a cutoff-time config typo (14:00 → 08:00) pushes every afternoon
  order's promise to next-day. Zero exceptions. Zero latency change. Lost sales.

  KEY INVARIANT:
     Infra-metric alerting CANNOT catch bad config — the infra is fine.
     Only BUSINESS-metric alerting can (promise-date distribution, transactability rate).
```

**The three mitigations (say all three):**

1. **Staged config rollout** — change 1% of traffic, watch **business** metrics 15–30 min, then 10% → 50% → 100%.
2. **Per-key rollback in seconds** — no restart, no deploy; a bad value reverts in ~30s.
3. **Alert on business metrics, not just system metrics** — a shifted promise-date histogram or a transactability drop in one market is the *only* reliable detector.

> **Pushback: "Give me a concrete example where infra alerting would've missed it."**
> "A cutoff-time value gets fat-fingered from 2pm to 8am. Now every order placed after 8am is promised next-day-plus-one instead of same-day. No exception is thrown — it's a valid config value. Latency doesn't move, no circuit trips, every pod is healthy. Every infra dashboard is green while we silently lose same-day conversions all afternoon. The only thing that catches it is a **business** alert: the promise-date distribution suddenly shifts later for that market. That incident class is exactly why I argued for business-metric alerting as a first-class part of the observability stack, not an afterthought."

---

## 10. — Back-pressure on both sides

The read side and write side apply **opposite** back-pressure strategies — and that contrast is a great answer to "how do you handle overload?"

```
🎨 Visual — opposite strategies, same goal (stay in control)

  READ side (latency-critical):            WRITE side (throughput-critical):
  overload → REJECT immediately            overload → SLOW DOWN, never drop
  ┌──────────────────────────┐             ┌──────────────────────────────┐
  │ zero-capacity queue       │             │ bounded processor queue fills │
  │ → task rejected in <5ms   │             │ → poller stops fetching       │
  │ → fallback fires          │             │ → Kafka holds records safely  │
  │ → bounded latency         │             │ → offset committed AFTER write│
  └──────────────────────────┘             └──────────────────────────────┘
   "fail fast — a late answer is useless"    "slow down — Kafka is the durable buffer"

  KEY INVARIANT:
     Read side optimizes for LATENCY (drop work to stay fast).
     Write side optimizes for DURABILITY (slow intake, never lose an event).
     Never commit a Kafka offset for a record you haven't durably written.
```

> **Pushback: "Why fail-fast on reads but never drop on writes — isn't that inconsistent?"**
> "It's consistent at the level that matters: both keep the system *in control* under overload — they just optimize for the thing each side can't compromise. On reads, the currency is **latency**; a queued request that resolves after the customer's timeout is wasted work, so I drop it and serve a fallback. On writes, the currency is **durability**; an offer update I drop is an item silently non-transactable, so I slow intake instead — the poller stops fetching, Kafka durably holds the backlog, and I catch up when pressure eases. Same principle — bounded, visible back-pressure — opposite tactic, chosen by what each side must never sacrifice."

---

## 11. — Failure severity ranking

What actually pages you, worst-first — a crisp answer to "what keeps you up about this system?":

| Rank | Failure | Impact | Recovery |
| --- | --- | --- | --- |
| 1 | **Both cache miss AND DB down** | Reference-data-unavailable for all reads — full outage | Wait for DB recovery; can't serve without reference data |
| 2 | **Bad config pushed broadly** | *Silent* wrong behavior — wrong dates, lost orders | Per-key rollback; maybe replay affected offers |
| 3 | **Inventory service full outage** | Inventory-unavailable everywhere | Degrade to last-known-good; breaker manages recovery |
| 4 | **Ingestion lag > 1 hour** | Cache serving stale data — new offers non-transactable | Add ingestion pods / tune processor pool; investigate lag |
| 5 | **Date-service outage** | Can source but not date | Fast-path response without a date; breaker recovers |
| 6 | **Cache snapshot fails to refresh** | Gradual staleness over hours | Restart cache generator; trigger manual batch |
| 7 | **Single pod cache stale** | Wrong signal on one pod | Self-heals on next TTL / delta |

**The pattern in the ranking:** the top two are the scariest for opposite reasons — #1 is *loud but rare* (both defenses fail at once), #2 is *quiet and more likely* (a human typo). Everything below is single-dependency degradation the resilience layers already absorb.

> **Pushback: "Your #1 is 'both cache and DB down' — how do you defend against that?"**
> "I can't make it impossible, but I make it *require two independent failures at once* — the in-memory cache serves 99%+ of reads even with the DB down, so a DB outage alone is survivable; and the DB is a masterless, multi-region, replicated store, so a total DB outage is itself rare. Reference-data-unavailable only happens in the **intersection** — a cache miss for a specific offer *and* the DB simultaneously unreachable. That's the residual risk I accept, and it's why cache hit-rate and snapshot freshness are load-bearing alerts, not nice-to-haves. Notably #2 — bad config — is *more likely* than #1, which is why I spend more design energy on config safety than on the DB-plus-cache doomsday."

---

## 🗺️ How to drill this file

- **For "explain something complex":** default to §1 (predictive delivery-time — the double-count insight is memorable) or §2 (multi-slot — the backward-compatible contract). Both have a non-obvious design decision, which is what "complex" really tests.
- **For "how do you handle failure":** lead with §6 (typed taxonomy) → §7/§8 (layers + degradation) → then the two that show *seniority*: §9 (bad config is scarier than crashes) and §10 (opposite back-pressure by domain).
- **Always end a failure answer with the meta-point:** the goal isn't "never fail," it's "fail visibly, degrade gracefully, and recover fast" — and the scariest failures are the *silent* ones.

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| Aug 28, 2026 | **File created.** Advanced features (predictive delivery-time, multi-slot, order-regrouping, clearance, node-classification) and failure modes (error taxonomy, 5 resilience layers, graceful degradation, bad-config, dual back-pressure, severity ranking) taught concept-first with diagrams + scripted pushbacks. Sourced from project-update knowledge layers 10/11/12; confidentiality-scrubbed to plain-English concepts. |
