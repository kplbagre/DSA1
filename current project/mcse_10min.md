# MCSE — 10-Minute Interview Version
### When they say "Tell me about your project" — use this file only.

---

## Open With This (memorise word for word)

> "I work on my company's promise and sourcing engine. For every item in a customer's cart, it decides which warehouse ships it and what delivery date the customer sees. 700,000 requests per minute, sub-100ms p95 latency."

Then say: *"Let me draw it for you."*

---

## The Diagram — Draw This in Order

Draw left-to-right, top-to-bottom. Talk while you draw. Don't rush.

```
                    CUSTOMERS
                  (Search / Cart / Checkout)
                          │
                          ▼
                  [ Unified Promise ]
                    (API Gateway)
                          │
                          ▼
 ┌────────────────────────────────────────────────────┐
 │              MCSE  (~30 Maven modules)             │
 │                                                    │
 │  ┌──────────────────────────────────────────┐      │◄─── [ 16 In-Memory Caches ]
 │  │  Pre-Scatter  (parallel data fetch)      │      │     Netflix Hollow framework
 │  │  • Inventory  (Wakanda)                  │      │     Sub-microsecond reads
 │  │  • Eligibility (LIMO)                    │      │
 │  │  • Capacity slots (FCAP)                 │      │     TNT Cache
 │  │  • Distributor data                      │      │     FlipsCache
 │  │  • Offer templates (3P sellers)          │      │     PoolConfigCache
 │  └──────────────────────────────────────────┘      │     OfferDataCache ...
 │                        │                           │
 │                        ▼                           │
 │  ┌──────────────────────────────────────────┐      │
 │  │  Orchestrator  (fan-out)                 │      │
 │  │  50–100 CompletableFuture evaluations    │      │
 │  │  Item × Warehouse × Shipping Method      │      │
 │  │  Each has its own timeout + fallback     │      │
 │  └──────────────────────────────────────────┘      │
 │                        │                           │
 │                        ▼                           │
 │  ┌──────────────────────────────────────────┐      │
 │  │  Gather / MOF                            │      │
 │  │  Reduce all candidates → best answer     │      │
 │  │  (earliest date + lowest cost)           │      │
 │  └──────────────────────────────────────────┘      │
 └────────────────────────────────────────────────────┘
                          │
                          ▼
              "Arrives by Thursday, $0 shipping"
              returned to customer in <100ms


  WRITE PATH (completely separate — never touches hot path):

  18 Domain Teams
        │  Kafka events
        ▼
  [ Ingestion Service ]
        │  writes
        ▼
  [ Cassandra ]
        │  Spark reads every ~10 min
        ▼
  [ cache-generator → new Hollow snapshot ]
        │  pods download + atomic pointer swap
        ▼
  [ All MCSE pods updated ]
```

---

## What to Say While Drawing (30-second script per box)

**While drawing "Unified Promise":**
> "All upstream callers — Search, Item Page, Cart, Checkout — route through a gateway called Unified Promise. MCSE is one of the backends it calls."

**While drawing "Pre-Scatter":**
> "First thing MCSE does is fetch all input data in parallel — inventory from Wakanda, eligibility from LIMO, capacity slots from FCAP. All parallel, before we do any evaluation."

**While drawing "Orchestrator":**
> "Then we fan out. For a single item we might check 50 to 100 combinations — warehouse in Dallas, warehouse in Atlanta, ship-from-store nearby — all evaluated simultaneously using CompletableFuture. Each evaluation has its own timeout so one slow warehouse can't block the rest."

**While drawing "16 Caches":**
> "All the reference data — transit times, warehouse configs, capacity states — lives in 16 in-memory caches using Netflix's Hollow framework. That's why we can do 50 lookups inside 100ms. Sub-microsecond reads, no network hop."

**While drawing "Gather":**
> "Gather reduces all those candidates to the single best answer — earliest date, lowest cost. That's what the customer sees."

**While drawing the write path:**
> "The write side is completely separate. 18 domain teams publish events on Kafka. We consume them, write to Cassandra, and a Spark job rebuilds the cache snapshot every ~10 minutes. Pods atomically swap to the new snapshot. The hot request path never blocks on writes."

---

## The 5 Numbers to Drop Naturally

| Number | Say it like this |
|---|---|
| **700K RPM** | "…700,000 requests per minute at peak" |
| **sub-100ms p95** | "…with a p95 latency under 100 milliseconds" |
| **~30 Maven modules** | "…it's a modular monolith, about 30 modules in one deployable" |
| **16 Hollow caches** | "…16 in-memory snapshots pre-loaded at startup" |
| **18 domain teams** | "…18 upstream teams feed data through Kafka into our ingestion layer" |

---

## The 3 Follow-Up Questions They Will Ask (and your 2-sentence answers)

**"Why not microservices?"**
> "Each request fans out to 50–100 internal evaluations. If those were separate services, every request would make 50+ network calls — at a 100ms budget, the math breaks. Modular monolith keeps all evaluations in-process."

**"Why not Redis instead of Hollow caches?"**
> "Redis is a network call — even at 1ms, 50 lookups per request at 700K RPM means tens of millions of Redis GETs per minute on the hot path. Hollow is memory-mapped inside the process — sub-microsecond, no network. We only use Redis-equivalent (MeghaCache) for data that needs cross-pod consistency."

**"How does a data change get reflected?"**
> "Domain team publishes to Kafka → our ingestion writes to Cassandra → Spark reads it and builds a new Hollow snapshot → all pods atomically swap. End-to-end, a few minutes for most data. For live capacity signals, we have an incremental Kafka path that updates in seconds."

---

## If They Ask to Go Deeper → Open MCSE_PROJECT.md

The full file has:
- Real class names and thread pool numbers from source code
- CompletableFuture taught from scratch + deep-dive Q&A
- Bulwark pattern, RapidResponse, Hystrix on Wakanda
- Cache refresh deep dive, ingestion pipeline internals
- Failure scenarios, cross-questions, terminology cheat sheet
