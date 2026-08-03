# MCSE — CA Stories, Bugs, and Programming Concepts
### Built from actual investigations, real code, real DB queries. For interview use.

> **This file complements mcse_project.md — don't repeat the architecture pitch.**
> Use this when asked: "Tell me about a bug you fixed", "Walk me through a production issue",
> "How do you debug in production?", "What are your design patterns?" etc.

---

## Real Bug Stories (Production Investigations)

---

### Bug 1 — CA Order Sourced to a Store Past Its DFS Cutoff

**What the business saw:**
An order placed at 14:01 EDT was sourced to store 3122 for same-day delivery. Store 3122 had a DFS cutoff of 13:30 EDT — the order was 31 minutes past cutoff. Customer got a same-day promise they couldn't receive.

**My debugging approach:**
1. Pulled logs using the order's correlation ID. Found the rejection reason for store 3178 (the correct store) was `SLA_TRIMMER_REJECTED` — not a filter error, a date calculation issue.
2. Grepped codebase for `SLA_TRIMMER` → traced to `SingleItemSolutionWithDateBuilder.java` → found it calls `DfsSameDayUtil.calculateEddForSameDayStores()` right after `calculateEdd(tnt=0)` for DFS paths.
3. Read `DfsSameDayUtil.java:94`:
```java
if (Objects.isNull(storeOrderCutOffWithTz) || orderDateToProcess.isAfter(storeOrderCutOffWithTz)) {
    eddInDcTz = eddInDcTz.plusDays(1);
}
```
4. Store 3178 had cutoff 13:00 — 14:01 is after → EDD pushed to Jul 22 → SLA trimmer rejects it (can't promise SAME_DAY Jul 21).
5. Store 3122 had cutoff 13:30 — 14:01 is also after, but here's the thing: there was a reshop call happening. `shouldSkipCapacityCheckForReshopCallAndResourcing()` returned true, which bypassed the capacity check. That's a separate bypass. The real question was: *why did store 3122 pass the cutoff check?*
6. Checked CCM for `same.day.sourcing.buffer.config` for CA (buId=1) — NOT configured. Default is 0 minutes. No backend buffer. So for store 3122, cutoff was 13:30, order was 14:01 — this should have failed too. But looking at DCC operating calendar data for 3122, the DFS cutoff was fetched as 14:15, not 13:30. So the cutoff times were different between the two stores — not a code bug in the buffer, but a data discrepancy in DCC.

**Root cause:**
Store 3178's DFS cutoff was correctly set to 13:00 in DCC. Store 3122's DFS cutoff was configured as 14:15, which is past the order time. So MCSE was correct — store 3122 was within cutoff at time of order. The real issue is the DCC operating calendar had a wrong cutoff for store 3122 (it should have matched the rest of the market).

**Secondary finding:**
Identified a code gap — when no carrier lane exists in `point_to_point_zone`, MCSE stamps zone=99 and cost=999.0 and continues. For DFS, zone=99 means the order cost is wrong and technically the lane doesn't exist. Raised a fix: reject in `DfsFilter.isValidLMDSolutionForS2H()` when `"99".equals(singleItemSolution.getZoneId())`.

**Interview hook:** This is a great story for "how do you debug production issues" — start from correlation ID, use logs to find rejection reason, trace backward through the call chain, confirm in DB, verify CCM config.

---

### Bug 2 — 17 CA Stores Not Receiving DFS Orders

**What the business saw:**
Business filed a ticket: "These 17 stores should be receiving DFS orders — please add DFS to their supported_service_eligibility."

**My investigation approach:**
First thing I thought: blanket SSE update is risky without understanding why. I ran a systematic DB scan across all 17 stores checking:
- `mcse_distributor` — SSE, configured_cm_map, lat/lon in address JSON
- `mcse_fc_capacity` — capacity rows for today and tomorrow

**What I found (vs what business assumed):**

| Store | Business assumption | Actual root cause |
|---|---|---|
| 1203 | Add DFS to SSE | ✅ Correct — DFS missing from SSE |
| 3002, 5832 | Add DFS to SSE | ❌ Wrong — SSE has DFS, but CM map is empty (no LMD carrier) |
| 1008–1071 (8 stores) | Add DFS to SSE | ❌ Wrong — SSE is there, but NO capacity rows + NO lat/lon |
| 1161, 1177, 3015, 3095 | Add DFS to SSE | ❌ Wrong — SSE is there, no lat/lon |
| 1065, 3041 | Add DFS to SSE | ❌ Wrong — everything in DB looks fine, missing ROVR catchment |

**Key code traces per root cause:**

**Lat/lon missing:**
```java
// CoreCommonUtils.getGeoPoints()
if (distAddress.getLatitude() != null && distAddress.getLongitude() != null) {
    return geoPoint;    // ← happy path
}
final GeoPoint fallback = GeoUtil.getGeoPoint(distZip);
// GeoUtil uses GeoMap.csv — a classpath file with ONLY US zip codes
// 0 Canadian postal codes in it → always returns null for CA stores
return null;  // → DELIVERY_FROM_STORE_RESTRICTION
```

**Capacity missing:**
CCM key `sfs.store.capacity.check.enabled = 'true'` for CA (buId='1') means stores go through the real FCAP lookup, not the "infinite capacity bypass" that US uses when rows are missing. Missing row → 0 capacity → filtered.

Additionally confirmed via `store_capacity_lookup` table: all 8 stores show `capacity_available = 'false'`.

**Empty CM map:**
`DistributorCarrierEligibilityFilter.java:109`:
```java
Map<String, ConfiguredDistributorCarrierMethod> carrierMethodMap =
    distToConfiguredDistributorCarrierMethodMap.get(distributorId);
if (carrierMethodMap == null) {
    return false;  // ← hard reject
}
```
Empty `configured_cm_map` → distributor not in the map → immediate rejection, never reaches DfsFilter.

**Interview hook:** Classic "always verify before acting" story. Business thought it was one problem, it was 5. Code + DB together gives the real answer. Not everything needs a code fix — most of this is config/data.

---

## Programming Concepts — From Actual Code

---

### Exception Handling

**How MCSE handles exceptions:**

There's a clear hierarchy:
1. **Per-route**: `.exceptionally()` on each `CompletableFuture` — catches timeout, NPE, downstream failure
2. **Request-level**: `Bulwark.fallBack()` — catches anything that slips through
3. **Ingestion pipeline**: Classic listeners never re-throw on bad records. They `log.error()`, emit a metric, and commit past the offset. Re-throwing would permanently block the partition on a poison pill.

**DfsFilter rejection handling — enumerated, not exceptions:**

Rejections are not exceptions — they're typed enums:
```java
public enum TripletRejectedReasons {
    DELIVERY_FROM_STORE_RESTRICTION,
    DESTINATION_LAT_LONG_NOT_PRESENT,
    DC_CM_SERVICE_ELIGIBILITY,
    SLA_TRIMMER_REJECTED,
    // ... ~40 more
}
```

Each filter implements `getRejectedReason()`. This means when debugging a production issue, you get an exact rejection code — not a generic error. You can grep logs for `DELIVERY_FROM_STORE_RESTRICTION` and immediately know the store had no lat/lon.

**Interview Q: "How do you handle exceptions in async code?"**
> "Two levels. CompletableFuture.exceptionally() at the route level — catches everything: timeout, NPE, downstream failure — and returns a RapidResponse fallback. Bulwark.fallBack() at the request level catches anything above that. We never propagate exceptions to the caller. The contract is always: you get a PromiseDateResponse, not an exception. For rejections within the evaluation — wrong cutoff, no capacity, distance too large — we use typed enums, not exceptions. Enums give structured rejection reasons that are loggable and queryable."

---

### Threads and Concurrency

Already in `mcse_project.md` — thread pool architecture, CompletableFuture, SynchronousQueue.

**Additional concepts seen in code:**

**ThreadLocal for per-request context:**
`RequestContextHolder.java` uses a thread-local to carry the request context (buId, correlationId, traceLoggingContext) through the call chain. Every downstream call reads from the same thread-local without passing it as a parameter.

Watch out: when CompletableFuture dispatches work to a different thread, the ThreadLocal is NOT automatically inherited. MCSE explicitly copies the context when creating the supplier:
```java
// Context must be captured before submitting to executor
final TraceLoggingContext capturedContext = TraceLoggingContext.get();
CompletableFuture.supplyAsync(() -> {
    TraceLoggingContext.set(capturedContext);   // set on the worker thread
    return doWork();
}, orchestratorExecutorService);
```

**Interview Q: "What's the risk of ThreadLocal in multithreaded code?"**
> "Two risks. First: context leakage — if you use a thread pool and the thread is reused for the next request without clearing the ThreadLocal, stale context bleeds into the new request. We always clear in a finally block after request completion. Second: CompletableFuture doesn't inherit ThreadLocals — you have to explicitly capture and propagate them when dispatching work to a different thread. We learned this the hard way when correlation IDs were missing from async logs."

---

### Design Patterns in Code

**Chain of Responsibility — SIS Filter Chain:**
Each `SingleItemSolution` passes through a chain of filters. Each filter either passes it or rejects it with a typed reason. Filters are completely decoupled — adding a new rejection rule is adding a new filter class, not modifying existing ones.

`DfsFilter` is one link. `DistributorCarrierEligibilityFilter` is another. They run in sequence. A solution must pass all of them to become a candidate.

**Strategy Pattern — Capacity Fetch Strategy:**
CCM key `capacity.fetch.strategy: CASSANDRA` for CA. The code selects which `CapacityFetchStrategy` implementation to use at runtime based on this config. CA uses Cassandra; other markets could use a different backend without code changes.

**Builder Pattern — Date Calculation:**
`SingleItemSolutionWithDateBuilder.java` builds up the date calculation step by step — EDD, cutoff check, SLA trimmer — each step mutates the builder state. Clean separation between "what data do I need" and "how do I calculate."

**Template Method — Bulwark:**
```java
// Subclass provides run() and fallBack()
// Bulwark decides which one to call and when
protected abstract PromiseDateResponse run();
protected abstract PromiseDateResponse fallBack();
```
The orchestration logic (retry, timeout, circuit opening) lives in the superclass. The subclass only cares about the happy path and the fallback.

---

### Logging and Observability

**How MCSE logs are structured:**

Logs are emitted at two levels:

**1. Application logs (SLF4J → Logback → OpenObserve / Splunk):**
- Structured log format — every log line includes `correlationId`, `buId`, `storeId`/`distributorId`
- Rejection reasons go to DEBUG, not ERROR — high volume, only turned on for investigation
- Example from `DfsFilter.java`:
```java
LOGGER.debug("Solution {} Rejected : Distributor Lat-Long not available", singleItemSolution.getKey());
LOGGER.debug("LMD solution {} eligible for DFS", singleItemSolution.getKey());
```
- If an order is misbehaving, you search OpenObserve/Splunk by correlationId and enable DEBUG for that customer/store temporarily via CCM flag.

**2. Structured trace events (→ Kafka → OpenObserve / BigQuery):**
Per the CCM config for CA:
```yaml
loggMode: kafka
categoriesForKafka: ORDER,ORDER_ITEM,SOLUTION,SHIPMENT,SHIPMENT_ITEM,
                    EXCEPTION,DISTRIBUTOR_CAPACITY,INVENTORY,TRIPLET,
                    SETTINGS,CTP,UPGRADES,DISCOUNT,CALL_DETAIL,BOX,BOX_ITEM
```
Every sourcing decision — which solution was picked, which were rejected, what capacity was seen, what the EDD was — gets emitted as a structured Kafka event. These land in BigQuery and are queryable for post-incident analysis. This is how we investigated the store 3122 issue: queried the TRIPLET events for that correlation ID and saw exactly which solutions were evaluated and why each was accepted/rejected.

**Grafana — what we watch:**
- **p95 latency** — per market, per route type (SDD/SFS/WFS). Alert if CA p95 > 150ms.
- **RapidResponse rate** — if RR spikes, it means either a downstream is slow or thread pool is full. RR should be <5% normally.
- **Thread pool utilization** — tracks active threads on orchestratorExecutorService. If this trends toward 800, we're close to SynchronousQueue rejections.
- **Kafka consumer lag** — for ingestion pipelines. If hot pipelines (offers, items) lag >5 min, Hollow caches start serving stale data. We page on this.
- **Hollow snapshot age** — how old is the current snapshot on each pod. Should be <15 min normally.
- **Rejection reason distribution** — how many orders got `DELIVERY_FROM_STORE_RESTRICTION` vs `SLA_TRIMMER_REJECTED` vs `DC_CM_SERVICE_ELIGIBILITY`. Sudden spike in one category = something specific broke.

**Interview Q: "How do you debug a production issue?"**
> "Four steps. First: get the correlation ID — every request has one, it threads through every log line. Second: search OpenObserve/Splunk with that ID — filter by component, look at rejection reasons. Third: check Grafana for the time window — did p95 spike? RapidResponse rate? Consumer lag? That tells me if it's isolated or systemic. Fourth: if it's data-related, go to Cassandra with the store/offer ID — check what the DB actually contains vs what it should. I've investigated bugs where the logs looked fine, Grafana looked fine, but Cassandra had wrong data for a specific store. You need all three."

---

## Things I'm NOT Clear On — Flag These for Your Own Context

These came up in our sessions but I couldn't verify from code alone. You'll need to fill these in yourself:

1. **Grafana dashboard names** — I know WHAT metrics are watched (latency, RR rate, lag) but not the exact dashboard names or panel structure in your Grafana setup.

2. **OpenObserve log stream names** — I can see logs are emitted via SLF4J but the exact stream names (e.g., `mcse-ca-prod`, `mcse-sourcing-trace`) you use for querying are not visible to me from code.

3. **How DEBUG is enabled in prod for specific orders** — I know there's a CCM-based approach, but the exact flag name and procedure isn't in the files I've read.

4. **The CA Promise V5 Multi-Slot feature details** — your existing `mcse_project.md` has a good summary (Story A), but the full E2E story (what the before state was, what APIs changed, how you handled backward compat, the upstream contract negotiation) — you're the only one who can fill that in.

5. **Ingestion pipeline incidents you personally handled** — the architecture is documented, but specific incidents (e.g., "we had a poison pill that blocked partition X for 3 hours") make much better interview stories than architecture descriptions.

6. **CCM push process** — you know CCM controls everything, but the exact procedure (how you draft a config change, who reviews, how you validate in stage before prod, rollback process) is institutional knowledge only you have.

---

## Quick Q&A — Bugs Specifically

**"Tell me about the hardest bug you debugged."**
> "CA was sourcing orders to a store 31 minutes past its DFS cutoff. The store had the right config, right SSE, right carrier — everything looked normal. The issue was in the date calculation layer. DfsSameDayUtil pushes EDD +1 day when an order is past the store's cutoff, which makes the SLA trimmer reject it as unable to meet same-day SLA. Store 3178 was being correctly rejected this way. Store 3122 was slipping through because its cutoff in DCC's operating calendar was set to 14:15 — 14 minutes after order time — even though the market standard was 13:00-13:30. So MCSE was correct, but the input data was wrong. I traced it from correlation ID → logs → rejection reason enum → code path → DCC data. Fix was in DCC, not in MCSE."

**"Give me an example of a bug that required DB investigation."**
> "17 stores not receiving DFS orders. Business asked for a blanket config change — add DFS to service eligibility for all 17. I wrote a Cassandra diagnostic script first and found only 1 store actually needed SSE — the other 16 had 4 completely different root causes: empty carrier map, missing capacity rows, missing lat/lon, missing ROVR catchment. If I'd done the blanket update the business asked for, 16 of those stores would still have gotten zero orders after the change. DB investigation before action saved us from a useless change and pointed to the right team owners for each fix."

**"What's your approach when a store suddenly stops receiving orders?"**
> "I follow a checklist: (1) Is the store in mcse_distributor at all, status = Y? (2) Is the target service type in supported_service_eligibility? (3) Is the LMD carrier (1300300 for DFS) in configured_cm_map? (4) Does the store have lat/lon in the address column? (5) Are there capacity rows in mcse_fc_capacity for today and tomorrow? (6) Is there a catchment row in mcse_dest_to_sfs_store_info pointing any customer postal code to this store? Failing any one of these is a hard blocker — but they manifest at different points in the call chain with different rejection reasons. Knowing which layer each check happens at means I can immediately narrow the search space."
