# MCSE — Interview Stories: Bugs, Investigations, Projects
> Sourced from actual production commits. All code refs are real.

---

## Bug Story 1 — Non-Deterministic Sourcing: Same Order, Different Results on Different Pods

**PR:** #15789 | Jul 2026

### What was happening
Sourcing results for the same order were different depending on which pod served the request. Same item, same customer zip, same time — two pods would return different delivery dates. This is the worst class of bug: not a crash, not an obvious error, just silent inconsistency. Reproducing it required routing the same request to multiple pods and comparing responses.

### Root cause
In `AsdFulfillmentPlanner.java`:

```java
// Before — non-deterministic
ZipSlaCaseData zipSlaCaseData = sourcingContext.getSlaBasedContexts().keySet().iterator().next();
```

`getSlaBasedContexts()` returns a `HashMap<ZipSlaCaseData, SlaBasedContext>`. `.keySet().iterator().next()` on a HashMap does NOT guarantee which key is returned first. HashMap does not maintain insertion order — iteration order depends on each key's `hashCode()` and the internal bucket layout. On different JVM instances, with different GC states, with different prior entries inserted, the same map can yield different iteration orders.

For a multi-destination order (e.g., split delivery), `slaBasedContexts` had more than one key. The wrong `ZipSlaCaseData` was picked on some pods, which meant the SLA context used for pool capacity calculation was different — producing different FCAP pool IDs, different capacity checks, different dates.

### The fix
```java
// After — deterministic
ZipSlaCaseData zipSlaCaseData = sourcingContext.getCustomerZipSlaCaseData();
```

`getCustomerZipSlaCaseData()` is explicitly set when the sourcing context is initialized from the request. It's always the actual customer's zip case — not an arbitrary map entry.

**Prevention / follow-through:** Fixing the instance wasn't enough — I searched the codebase for every `.keySet().iterator().next()` on a map that could hold more than one entry and removed the reliance on map-iteration order in each. That closed the whole *class* of non-determinism, not just this one call site.

### Why this is impressive
Non-determinism bugs are the hardest to find because they only manifest under specific conditions and aren't reproducible on demand. You can't add a test that catches this without understanding the bug first. The root cause is a Java language-level trap: treating `HashMap` iteration as if it were ordered. The right data structure for ordered iteration is `LinkedHashMap`. The fix doesn't change data structures — it simply removes the reliance on map iteration entirely.

**Concepts:** Java HashMap iteration order, non-determinism in distributed systems, contract between data structures and caller expectations.

---

## Bug Story 2 — Multi-Item Order: TNT Version Map Overwritten Per Item

**PR:** #15707 | Jul 2026

### What was happening
For orders with 2 or more ACS (Advanced Carrier Selection) eligible items, transit time calculations were wrong for all items except the last one. TNT versions (which determine carrier-level transit time tables) were being applied from the wrong carrier for earlier items.

### Root cause
In `MultiItemFulFillmentPlanner.java`, for each ACS-eligible item in the order, the code was building a `distributorMap` and then:

```java
// Before — overwrites on each iteration
sourcingContext.setDcCmTntVersionMap(distributorMap);
```

`setDcCmTntVersionMap` replaces the entire map. So for an order with items A and B:
- Process item A → `setDcCmTntVersionMap({distA: {cm1: v2}})`
- Process item B → `setDcCmTntVersionMap({distB: {cm3: v1}})` — item A's TNT data is gone

When the date calculator looks up TNT version for item A's distributor, it gets nothing from the map and falls back to default TNT — which may not be the carrier-negotiated version. Wrong TNT → wrong delivery date → customer gets an incorrect promise.

### The fix
```java
// After — accumulate across iterations
if (MapUtils.isEmpty(sourcingContext.getDcCmTntVersionMap())) {
    sourcingContext.setDcCmTntVersionMap(new HashMap<>());
}
sourcingContext.getDcCmTntVersionMap().putAll(distributorMap);
```

Initialize once if null, then `putAll` to merge each item's data. All items' TNT version info survives.

### Why this is impressive
This is a shared mutable state bug where the contract is implicit. `setDcCmTntVersionMap` looks harmless in isolation — it's just a setter. The bug only appears when you trace the entire multi-item processing loop and realize `sourcingContext` is shared across all iterations. The symptom (wrong delivery dates on specific items) requires knowing which items are ACS-eligible to reproduce. Added 125+ lines of new unit tests specifically covering single-item, multi-item, null-map, and accumulated-data scenarios.

**Concepts:** Shared mutable state across loop iterations, `putAll` vs `set` semantics, defensive null initialization.

---

## Bug Story 3 — Mexico Delivery Dates Off by 1 Hour: Daylight Saving Time Trap

**PR:** #15024 | Apr 2026

### What was happening
Mexico customers were getting delivery date promises that were off by exactly 1 hour during summer months. During winter (November–March), dates were correct. During summer (March–November), the same-day cutoff calculations were wrong, causing some orders to miss same-day eligibility or get next-day dates when same-day was available.

### Root cause
In `DateCalculatorHelper.java`:
```java
// Before
private static final String CST_TIMEZONE_ID = "America/Mexico_City";

public static DateTime convertToCSTTimeZone(DateTime dateTime) {
    return dateTime.withZone(DateTimeZone.forID(CST_TIMEZONE_ID));
}
```

`America/Mexico_City` is a valid IANA timezone that **automatically observes Daylight Saving Time** — CST in winter (UTC-6), CDT in summer (UTC-5). Mexico does observe DST, so in summer this shifts all cutoff calculations by 1 hour.

The problem: Mexico's **business logic** for delivery cutoffs is defined in terms of a fixed CST offset (UTC-6). Store operating hours, carrier pickup windows, and same-day cutoffs are all configured as if the timezone is always UTC-6. When the JVM applied CDT (UTC-5) in summer, the cutoff time in UTC was shifted 1 hour earlier than intended — stores that should have a 2:00 PM cutoff were effectively getting a 1:00 PM cutoff.

### The fix
```java
// After — fixed offset, no DST
private static final int CST_OFFSET_HOURS = -6;

public static DateTime convertToCSTTimeZone(DateTime dateTime) {
    return dateTime.withZone(DateTimeZone.forOffsetHours(CST_OFFSET_HOURS));
}
```

`DateTimeZone.forOffsetHours(-6)` is a fixed offset — it never adjusts for DST. Business hours stay business hours.

### Why this is impressive
DST bugs are notoriously silent — they only surface twice a year when clocks change, the symptom is "wrong by 1 hour" which sounds minor but breaks SLAs, and they pass all unit tests because tests typically use fixed dates. The right approach here is to know your domain: business cutoff times in MCSE are data — they're stored and configured in terms of a specific timezone. If your code shifts that timezone dynamically, you break the implicit contract between config data and the code consuming it. When you're dealing with multiple markets, the answer to "which timezone?" is almost always "the one the business operates in" — not "what does the IANA database say right now."

**Concepts:** IANA timezone vs fixed offset, DST-aware vs DST-fixed date arithmetic, timezone correctness in multi-market systems.

---

## Bug Story 4 — ASD/EDD Off by 1 Day: Multihop TNT Calculation Was Ignoring Hop Type

**PRs:** #14702 + #14714 | Mar 2026

Two bugs in the same code, both in `DateCalculatorUtil.java`, both about transit time calculation for multihop orders. Caught in the same sprint.

### Bug 4a — TNT-in-Minutes Ignored for Non-DIRECT Hops (#14702)

**Symptom:** Multi-hop inventory node orders were always using the fixed TNT (Transit Time in days) even when a more precise TNT-in-minutes value was available from the predictive TNT cache.

**Root cause:**
```java
// Before — hardcoded DIRECT
boolean isPlannedTNTApplicable = PredictiveTNTUtils.checkPlannedTNTApplicable(predictiveTNT, HopType.DIRECT);
```

`checkPlannedTNTApplicable` checks whether predictive TNT is usable for the given hop type. But it was always called with `HopType.DIRECT` regardless of the actual hop type. For an `INVENTORY_NODE` hop (first leg of a two-leg multihop), `isPlannedTNTApplicable` always returned false — so TNT-in-minutes was never applied. The order fell back to fixed TNT, which is in full days. A 6-hour transit was rounded up to 1 day.

**Fix:**
```java
// After — use actual hop type
boolean isPlannedTNTApplicable = PredictiveTNTUtils.checkPlannedTNTApplicable(predictiveTNT, hopType);
```

---

### Bug 4b — +1 Day Applied to Multihop When ESD == Ship Date (#14714)

**Symptom:** Multihop orders where ESD equalled the actual ship date were getting EDD pushed 1 day later than correct.

**Root cause:**
```java
// Before — +1 applied to ALL hops when esd == actualShipDate
eddInDcTz = esd.equals(actualShipDateInDcTz)
    ? multiHopTransitEddCalculator.calculateDeliveryDateWithTntInMinutes(
          hubTntBuffer + laneTntBuffer + 1, ...)
    : multiHopTransitEddCalculator.calculateDeliveryDateWithTntInMinutes(
          hubTntBuffer + laneTntBuffer, ...);
```

The +1 logic is correct for DIRECT hops: when the ESD is today, you need an extra day buffer because the item hasn't actually left yet. But for multihop, `hubTntBuffer` and `laneTntBuffer` already encode this delay — the hub staging time accounts for today's ship. Adding +1 on top double-counted the buffer.

**Fix:**
```java
// After — remove the condition entirely for multihop TNT path
eddInDcTz = multiHopTransitEddCalculator.calculateDeliveryDateWithTntInMinutes(
    hubTntBuffer + laneTntBuffer, ...);  // no ternary, no +1

// In the fixed-TNT path — guard on DIRECT only
eddInDcTz = esd.equals(actualShipDateInDcTz) && HopType.DIRECT.equals(hopType)
    ? fixedTransitEddCalculator.calculateDeliveryDate(plannedTnt + hubTntBuffer + laneTntBuffer + 1, ...)
    : fixedTransitEddCalculator.calculateDeliveryDate(plannedTnt + hubTntBuffer + laneTntBuffer, ...);
```

### Why this is impressive
Both bugs stem from the same root issue: code written assuming DIRECT hop, then reused for multihop without updating the hop-type guards. The `+1 day` error on multihop TNT-in-minutes is subtle — it only fires when same-day ship (ESD == actualShipDate), which is a specific subset of multihop orders. The fix required understanding what `hubTntBuffer`, `laneTntBuffer`, and `multihopTntMinutes` each represent semantically and why the +1 is valid for single-hop but already baked into the buffer for multihop.

**Prevention / follow-through:** Both bugs slipped through because every existing test used a single-hop (DIRECT) scenario. I added regression tests covering both DIRECT and INVENTORY_NODE hop paths so that blind spot can't recur, and flagged an EDD-accuracy-per-hop-type panel so a future mismatch surfaces as a monitoring signal rather than a business escalation.

**Concepts:** Multi-hop delivery architecture (DIRECT vs INVENTORY_NODE hop types), transit time buffer arithmetic, same-day ship vs next-day ship logic.

---

## Bug Story 5 — UnsupportedOperationException in Prod: Java 16 Changed What .toList() Returns

**PR:** #15322 | May 2026

### What was happening
Intermittent `UnsupportedOperationException` in `PreScatterRoverGenerator` when processing catchment store data. Only happened for specific sellers where downstream code tried to modify the catchment store list. Didn't fail in tests because unit tests used a different code path for constructing the list.

### Root cause
```java
// Before — Java 16+ .toList() returns unmodifiable List
.map(storeDto -> Integer.toString(storeDto.getStoreId()))
.toList()
```

In Java 16+, `Stream.toList()` returns an **unmodifiable** `List` — this is different from `Collectors.toList()` which returns a mutable `ArrayList`. The `sellerIdToCatchmentStores` map was built with these unmodifiable lists as values. Later code was trying to add or remove stores from these lists at runtime, which throws `UnsupportedOperationException`.

The migration from Java 11 to Java 17 introduced this — `Stream.toList()` didn't exist in Java 11, it was added in Java 16. Developers using it as a shorthand for `Collectors.toList()` don't realize the mutability contract changed.

### The fix
```java
// After — mutable ArrayList
.collect(Collectors.toList())
```

One character difference in the call. Completely different runtime behavior.

### Why this is impressive
This is a Java version upgrade trap. The code looks correct, compiles without warnings, and passes tests if the test never mutates the returned list. It only manifests in production paths where catchment stores are modified post-build. The lesson: when upgrading Java versions, every `.toList()` call needs review. More broadly: APIs that look similar can have different contracts — `Stream.toList()` vs `Collectors.toList()` vs `List.of()` vs `List.copyOf()` all produce lists with different mutability guarantees.

**Concepts:** Java version migration traps, unmodifiable vs immutable vs mutable list semantics, contract documentation.

---

## Bug Story 6 — WFS 3P Multihop Orders Released 8 Hours Too Early

**PR:** #15214 | May 2026

### What was happening
WFS 3P multihop orders were being batched and released for fulfillment 8 hours before the hub was ready to receive them. The hub staging buffer wasn't being respected. This led to items arriving at hub FCs before the transfer vehicle was scheduled, causing them to be rejected and re-routed.

### Root cause
In `BatchOptimizerHelper.calculateReleaseCutoff()`:
```java
// Before — no hop type check, WFS single-hop shortcut fires for all WFS
if (Objects.isNull(singleItemSolution.getPreOrderVirtualCallTime())
    && BooleanUtils.isTrue(offerIdToWFSFulfilledMap.get(offerId))
    && MapUtils.isEmpty(offerIdToMlmqMap)) {
    return orderDate.plusMinutes(2);  // release immediately for WFS
}
```

This early-return path was designed for WFS **single-hop** orders — items that ship directly from a WFS FC to the customer. Release cutoff = orderDate + 2 minutes means "release immediately, no batching delay needed."

But WFS **3P multihop** orders (`HopType.INVENTORY_NODE`) also matched this condition. They need to go through a hub transfer, which requires staging time. `defaultMultihopReleaseCutOff()` would calculate the right cutoff (orderCutOff minus hub buffer). By returning early with `orderDate + 2 minutes`, the hub staging window was completely skipped.

### The fix
```java
// After — only single-hop WFS gets the early release
if (Objects.isNull(singleItemSolution.getPreOrderVirtualCallTime())
    && HopType.DIRECT.equals(singleItemSolution.getHopTypeDefaultDirect())  // ← new guard
    && BooleanUtils.isTrue(offerIdToWFSFulfilledMap.get(offerId))
    && MapUtils.isEmpty(offerIdToMlmqMap)) {
    return orderDate.plusMinutes(2);
}
// Falls through to defaultMultihopReleaseCutOff() for INVENTORY_NODE hops
```

### Why this is impressive
The batching/release cutoff system is one of the most operationally critical parts of MCSE — getting it wrong causes real physical failures in fulfillment. The bug was invisible in code review because WFS multihop was added after the early-return shortcut was written, and nobody updated the condition. The fix is adding one predicate, but identifying the fix required understanding: what does release cutoff mean, how does the batch optimizer use it, what is a hub buffer, and why do DIRECT and INVENTORY_NODE hops need different release logic.

**Concepts:** Order batching lifecycle, hop type semantics (DIRECT vs INVENTORY_NODE), fulfillment cutoff vs release cutoff.

---

## Bug Story 7 — CA Performance Spike: Tracing Was Serializing a Giant Object Per Solution

**PR:** #12338 | Jun 2025 (k0b077v)

### What was happening
CA p95 latency shot up after a feature release that increased the number of box packs per shipment. Grafana showed the spike was in promise calls specifically, not sourcing calls. Thread pool utilization was fine, DB calls were fine — the time was disappearing somewhere inside `TraceEventPublisher.logSolution()`.

### Root cause
Added timing instrumentation around the trace logging path. Found that `sourcingContext.getBoxesToPackedItemBoxMap()` — a `Map<Box, List<PackedItemBox>>` — was being JSON-serialized for every solution evaluated, for every promise call. For CA orders with multiple shipments and multi-item packs, this map had hundreds of entries.

Multiply that by 50 store candidates evaluated per order, all on the hot promise call path: 50 serializations of a 400-entry map = significant CPU time before picking a winning solution.

The box data is purely for internal warehouse operations. It has zero value in sourcing traces — it was just never explicitly excluded.

### The fix
```java
// TraceEventPublisher.java
if (sourcingContext.getCallType().isPromiseCall()
        && ccmFactory.getBoolean(LiteCcmEnum.DISABLE_BOXWITHCOST_FROM_LOGGING, sourcingContext.getBusinessUnit())) {
    sourcingContext.setBoxesToPackedItemBoxMap(new HashMap<>());
}
```

New CCM key: `disable.boxWithCost.from.logging` — enabled for CA. Map is cleared in-place before `logSolution()` runs, so serialization skips all box data.

**Prevention / follow-through:** To make regressions visible instead of waiting for the next p95 spike, I added a CCM-controlled gauge on the map size — so if the object grows large again we get an early signal rather than discovering it through latency.

### Why this is impressive
The fix is 3 lines. Finding it took systematic profiling: Grafana narrowed it to promise calls, per-stage timing logs narrowed it to `logSolution()`, reading the serialization input narrowed it to the box map. The lesson: observability overhead compounds when evaluation counts scale up. You serialize a small object once — fine. You serialize a large object per candidate per call — the trace path becomes your bottleneck. The CCM flag pattern is important: zero-deploy rollback, per-market control, safe to experiment in prod.

**Concepts:** Serialization overhead at scale, per-call vs per-request cost amortization, CCM as operational safety valve.

---

---

## Project Story 1 — CA Promise V5 Onboarding: Multi-Speed Slot Architecture

**PR:** #12849 | Aug 2025 (k0b077v)

### What V5 is
Canada was on V3 — one delivery window per request, date-based SLAs. V5 is slot-based: SAME_DAY, ONE_DAY, TWO_DAY, TWO_HOUR (Express) delivery windows. Response returns all available slot tiers, customer picks one. Richer than V3 and incompatible with how slot data was stored and fetched.

### What I owned
The slot fetching pipeline. `StoreSlotFetchUtil.generateStoreSlotQueryKeys()` generated US-format Cassandra query keys — it didn't know about business units. CA uses different key formats, different speed constants, and different Cassandra schemas.

**Changes:**

1. Signature change: `generateStoreSlotQueryKeys(storeId, fulfillmentType)` → `generateStoreSlotQueryKeys(storeId, fulfillmentType, businessUnit)`. Updated all call sites and tests.

2. Added `TWO_HOUR_SPEED = "TWO_HOUR"` constant — Express delivery doesn't exist in US, so the constant didn't exist.

3. Updated `StoreSlotInfoMapper` to correctly map CA Cassandra slot entries to V5 `SlotInfo` objects — different field mapping than US.

4. Updated `SlotDataGenerator` and `SlotInfoGenerator` for CA-specific slot resolution — Express slots have different cutoff logic.

5. Updated `ScheduledDestinationGenerator` and `ScheduledFulfillmentDetailsResponseGenerator` for CA.

### The hard part
Getting the Cassandra key format right. US uses 5-digit zip + store ID + speed. CA uses FSA (3-char postal prefix) + store ID + speed + businessUnit discriminator. I had to read actual CA Cassandra rows in prod to verify the key format, then trace backward through `StoreSlotFetchUtil` to ensure the generated keys would match. All existing tests used PowerMock on the static method — after adding `businessUnit`, every mock expectation needed updating.

### Why this is an initiative story
This wasn't a bug fix. It was a coordinated feature: slot data access API change, multi-component updates, CA market launch. I was the owner of the data access layer for slots specifically — defined the new API signature (a contract change between components), validated it against prod data, and ensured the existing US path was untouched. Deployed with CA behind a CCM flag so US traffic was zero-risk throughout.

---

## Project Story 2 — Trace V2 Pipeline: Event-Driven Sourcing Observability

**PR:** #14945 | May 2026

### What V1 was, and why it wasn't enough
V1 tracing logged the entire sourcing session as a single large JSON document. Every solution evaluated, every rejection reason, all capacity data — one blob per request. Queryable only as a full document. Filtering by "show me all TRIPLET rejections for store 3122" required deserializing the whole document and scanning it.

### What V2 does
V2 is event-driven: each **category** emits independently to Kafka with its own schema:
- `ORDER` — request metadata
- `SOLUTION` — each evaluated solution with accept/reject and reason
- `SHIPMENT` — shipment-level data
- `TRIPLET` — per-triplet rejection reasons
- `MOF` — multi-objective function cost breakdown
- `SETTINGS` — CCM config snapshot at request time
- `EXCEPTION` — error events
- `DISTRIBUTOR_CAPACITY` — capacity data seen during evaluation

Events land in BigQuery/OpenObserve partitioned by category. Now you can write:
```sql
SELECT * FROM triplet_events WHERE store_id = '99993122' AND rejection_reason = 'SLA_TRIMMER_REJECTED'
```
without scanning irrelevant data.

### Architecture
`TraceLoggingContext` carries two new fields:
```java
private Boolean traceV2PipelineEnabled;  // master switch
private Boolean traceV2Eligible;         // per-request throttle (not all requests traced)
```

Each fulfillment planner (`AsdFulfillmentPlanner`, `B2BFulfillmentPlanner`, `SameDayFulfillmentPlanner`, etc.) was updated to call the V2 trace publisher alongside the V1 path — dual-write during migration. V1 kept running until V2 was validated.

Event mappers: one mapper class per category (`MofEventMapper`, `SettingsEventMapper`, `ExceptionEventMapper`, etc.). Each mapper extracts its relevant fields and builds a typed event DTO. No mapper knows about other categories — fully decoupled.

### Why this is an initiative story
~20 component files changed, new event mapper classes for each category, dual-write migration path, per-request throttle so only a sample of requests gets full tracing until prod stability is confirmed. The design principle — one event type per category, typed schemas, independent queryability — is the difference between a log file and an analytics table. This is what made the `CA-bulk-store-DFS-eligibility` investigation possible: querying TRIPLET events by store to find rejection reasons at scale.

---

## Project Story 3 — Supply and NodeSellable Propagation for CA Discovery

**PR:** #12933 | Aug 2025 (k0b077v)

### What this was
CA was running its discovery flow — discovery returns all possible delivery options before the customer commits. For CA, downstream merchandising systems needed two fields from Wakanda (inventory service): `supply` (warehouse stock) and `nodeSellable` (sellable quantity at that specific node). MCSE was fetching both from Wakanda but not propagating them to the promise response — they were being discarded in the mapper.

### What I built
End-to-end data flow: Wakanda response → `AvailabilityServiceV2Mapper` → `SourcingContext` → promise response.

The tricky part: Wakanda returns two node types with the same fields but different classes — `SaleableNodeV3` (regular nodes) and `SellablePreferredNode` (preferred/store nodes). Rather than duplicate the propagation logic, used a generic method:

```java
private <T> void updateDistributorSupplyAndNodeSellableMap(T node, String offerId, SourcingContext sourcingContext) {
    String distributorId;
    Double supply;
    Double nodeSellable;

    if (node instanceof SaleableNodeV3) {
        distributorId = ((SaleableNodeV3) node).getNodeId();
        supply = ((SaleableNodeV3) node).getSupply();
        nodeSellable = ((SaleableNodeV3) node).getNodeSellable();
    } else if (node instanceof SellablePreferredNode) {
        distributorId = ((SellablePreferredNode) node).getNodeId();
        supply = ((SellablePreferredNode) node).getSupply();
        nodeSellable = ((SellablePreferredNode) node).getNodeSellable();
    } else {
        throw new IllegalArgumentException("Unsupported node type");
    }
    // merge into sourcingContext distributor maps
}
```

Gated behind `isCADiscoveryRequest()` — US traffic untouched.

### Why this is an initiative story
Identified the gap (data fetched but thrown away), designed the data flow across three layers (Wakanda client → mapper → context → response), handled two different node type variants with one generic method, and defined the contract that downstream CA consumers depend on for merchandising decisions. Zero impact on US traffic.

---

## Quick Interview Q&A

**"What is the hardest bug you've debugged?"**
> "Non-deterministic sourcing results — same order, different delivery dates on different pods. The root cause was `HashMap.keySet().iterator().next()` which gives you an arbitrary key when there are multiple entries. Different JVM instances with different GC states returned different keys. I found it by routing the same request to specific pods and comparing trace events, then auditing every place in the code that picked from a map without an explicit key. One-line fix — use `getCustomerZipSlaCaseData()` which is set explicitly — but weeks to find."

**"Tell me about a performance issue you solved."**
> "CA p95 latency was spiking after a feature that increased box packs per shipment. Grafana said it was promise calls. I added per-stage timing logs and traced it to `logSolution()`. Reading the serialization input showed `boxesToPackedItemBoxMap` — hundreds of entries — being serialized for every solution candidate evaluated. 50 candidates per order × 400-entry map = the bottleneck. Added a CCM flag to clear the map before logging on promise calls. Zero-deploy rollback, CA-only control, p95 dropped immediately."

**"Tell me about a timezone bug."**
> "Mexico delivery dates were off by 1 hour in summer. We were using `DateTimeZone.forID('America/Mexico_City')` which correctly observes Mexican DST — UTC-6 in winter, UTC-5 in summer. But our store operating hours and carrier cutoffs are configured assuming a fixed UTC-6. So in summer the JVM was shifting all cutoffs 1 hour earlier than the business intended. Fix: use a fixed offset `forOffsetHours(-6)`. Lesson: there are two ways to represent a timezone — as an IANA zone (DST-aware) or as a fixed offset. When your config data is written in terms of a fixed offset, your code must use the same."

**"How do you think about multi-market codebases?"**
> "Two principles. First: gate new market behavior explicitly — `isCADiscoveryRequest()`, `BusinessUnit.WALMART_CA.equals(...)` — so US traffic is never affected by CA changes. Second: use CCM for all behavioral differences with market-scoped resolution (`buId=1` for CA, `buId=0` for US). That way any market-specific behavior can be turned off instantly without a deploy. The worst bugs in multi-market code are the ones that silently apply US-specific assumptions to CA or MX — timezone handling, unmodifiable list contracts, map iteration order — they all look identical but fail differently across markets."

**"Walk me through a design decision you made."**
> "In the supply/nodeSellable propagation for CA, Wakanda returns two different classes — `SaleableNodeV3` and `SellablePreferredNode` — that have the same fields but no common interface. I wrote a generic method with `instanceof` dispatch rather than duplicating the update logic. The key design choice: `throw new IllegalArgumentException('Unsupported node type')` for anything else, not a silent no-op. A no-op would hide future callers passing the wrong type. Failing loud is better than silently dropping data — especially in a data propagation method where the caller expects the context to be updated."
