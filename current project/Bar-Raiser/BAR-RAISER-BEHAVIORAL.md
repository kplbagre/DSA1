# Bar Raiser — Behavioral Round Prep
### Senior Software Engineer | Based on 2025–2026 interview research + real production stories

> **What the Bar Raiser checks behaviorally:**
> They are testing for Leadership Principles — ownership, judgment, customer focus, diving deep,
> disagreeing and committing, earning trust. They will follow up every answer with
> "what would you have done differently?", "what were the risks?", "how did you measure impact?"
>
> **Senior SWE calibration (not Principal):**
> Your stories should span a feature or component, not an org. Influence a team or one cross-team
> collaboration — not an entire org structure. Depth and judgment matter more than org-wide change.
>
> **Note:** Stories A–G are already in `15_BEHAVIORAL_ANSWERS_FULL.md`. Every story here is
> a different one, drawn from real bugs and projects in the platform.
>
> **Cross-references from that file (don't duplicate, just know them):**
> - **Mentoring/raising the bar** → use Story G from `15_BEHAVIORAL_ANSWERS_FULL.md`
> - **High-pressure prod incident (100% CPU)** → Story B
> - **Kafka ingestion deep-dive** → Story C
> - **CA multi-slot delivery end-to-end** → Story A
>
> **⚠️ VERIFY TAGS:** Sections marked `[VERIFY: ...]` contain specifics I invented.
> Fill them in with real numbers/outcomes BEFORE your interview. A fabricated stat
> collapses under one follow-up question ("what exactly was the distribution?").

---

## 📋 TABLE OF CONTENTS

**🏠 Category 1 — Ownership**
- [Q1 — Took ownership of something not strictly your responsibility](#q1-tell-me-about-a-time-you-took-ownership-of-something-that-wasnt-strictly-your-responsibility)
- [Q2 — Cross-team project you drove end-to-end](#q2-describe-a-project-you-drove-end-to-end-where-you-had-to-coordinate-across-multiple-teams)

**💬 Category 2 — Disagreement / Conflict**
- [Q3 — Disagreed with a teammate or tech lead](#q3-tell-me-about-a-time-you-disagreed-with-a-teammate-or-tech-lead-on-a-technical-approach)
- [Q4 — Pushed back on product or management](#q4-describe-a-time-you-had-to-push-back-on-a-request-from-product-or-management)

**❌ Category 3 — Failure and Learning**
- [Q5 — A technical mistake you made](#q5-tell-me-about-a-technical-mistake-you-made-what-happened-and-what-did-you-learn)
- [Q6 — Investigation that led to a wrong conclusion](#q6-describe-a-time-your-investigation-led-you-to-a-conclusion-that-turned-out-to-be-wrong)

**❓ Category 4 — Ambiguity**
- [Q7 — Decision without complete information](#q7-tell-me-about-a-time-you-had-to-make-a-decision-without-complete-information)
- [Q8 — Working when requirements are unclear or changing](#q8-how-do-you-work-when-requirements-are-unclear-or-changing)

**🤝 Category 5 — Influence Without Authority**
- [Q9 — Influenced a decision you didn't have authority to make](#q9-tell-me-about-a-time-you-influenced-a-decision-you-didnt-have-authority-to-make)
- [Q10 — Got buy-in from someone resistant to change](#q10-describe-a-time-you-had-to-get-buy-in-from-someone-who-was-resistant-to-a-change)

**👥 Category 6 — Customer Impact**
- [Q11 — Work that had direct impact on customers](#q11-tell-me-about-a-time-your-work-had-a-direct-impact-on-customers)
- [Q12 — Raised the quality bar on your team](#q12-tell-me-about-a-time-you-raised-the-quality-bar-on-your-team)

**🎯 Category 7 — Prioritization**
- [Q13 — Prioritized between multiple important things](#q13-tell-me-about-a-time-you-had-to-prioritize-between-multiple-important-things)
- [Q14 — When is technical debt worth fixing now vs. later?](#q14-how-do-you-decide-when-a-piece-of-technical-debt-is-worth-fixing-now-vs-later)

**📈 Category 8 — Data-Driven**
- [Q15 — Changed your approach based on data or feedback](#q15-tell-me-about-a-time-you-changed-your-approach-based-on-data-or-feedback)

**🔥 Category 9 — Situational Judgment / Pressure Scenarios**
- [S1 — Testing finds feature incomplete just before delivery](#s1--testing-finds-the-feature-is-incomplete-or-buggy-just-before-delivery)
- [S2 — Critical bug found just before big deployment](#s2--you-discover-a-critical-bug-in-your-own-code-just-before-a-big-deployment)
- [S3 — New requirement days before deployment](#s3--days-before-deployment-business-brings-a-significant-new-requirement)
- [S4 — Manager brings higher-priority task mid-implementation](#s4--manager-brings-a-higher-priority-task-while-youre-mid-implementation)
- [S5 — Asked to cut testing to hit a deadline](#s5--asked-to-cut-testing-or-skip-code-review-to-hit-a-deadline)
- [S6 — Mid-sprint, estimate was badly off](#s6--mid-sprint-you-realize-your-estimate-was-badly-off--task-is-2x-the-work)
- [S7 — Post-deploy metrics degraded — roll back or watch?](#s7--post-deploy-metrics-look-degraded-but-not-catastrophically--roll-back-or-watch)
- [S8 — Blocked by another team's late dependency](#s8--youre-blocked-dependency-from-another-team-is-late-your-work-cant-proceed)
- [S9 — Leading a project, teammate consistently falling behind](#s9--youre-leading-a-project-and-a-teammate-is-consistently-falling-behind)
- [S10 — Teammate's code quality repeatedly below bar](#s10--a-teammates-code-quality-is-repeatedly-below-bar--same-issues-in-every-pr)
- [S11 — On-call at 2am, production down, no idea why](#s11--youre-on-call-at-2am-production-is-down-you-have-no-idea-why)
- [S12 — Manager gives critical feedback you didn't expect](#s12--manager-gives-you-critical-feedback-you-didnt-expect)
- [S13 — Two stakeholders want conflicting things from your feature](#s13--two-stakeholders-want-conflicting-things-from-your-featureapi)
- [S14 — Spot a problem outside your domain — raise it or not?](#s14--you-spot-a-significant-problem-clearly-outside-your-domain--raise-it-or-not)
- [S15 — Asked to take on something much larger than before](#s15--youre-asked-to-take-on-something-significantly-larger-than-youve-done-before)

---

## HOW BAR RAISERS PROBE — Know This Before Reading Answers

After your first answer, they will ask:
- "What would you have done differently?"
- "How did you know it was working?"
- "What did the team think about this?"
- "What was the hardest part of that?"
- "What would have happened if you hadn't done that?"
- "Why did you make that call vs the alternative?"

Prepare a second layer for every story. Don't just have the story — have the reflection.

---

## CATEGORY 1 — Ownership and End-to-End Delivery

### Q1: Tell me about a time you took ownership of something that wasn't strictly your responsibility.

**Context / what they're listening for:** A senior engineer doesn't wait for someone to assign them work. They see a gap, claim it, drive it.

**STAR Answer:**

**Situation:**
During the Canada V5 slot delivery onboarding, we were integrating a new slot-fetching pipeline for two-hour delivery windows. My scope was the platform side — generating the right slot query keys and processing the slot response. But as I built the integration, I noticed the store slot data in Cassandra had no TTL set. If the slot publishing system stopped publishing slot data for a store, the platform would keep serving stale slots indefinitely — the data would never expire.

**Task:**
No one owned this gap. the slot publishing team owned slot publishing. The DBA team owned schema. I owned the platform's consumption logic. Nobody was watching TTL policy at the boundary.

**Action:**
I didn't file a ticket and wait. I traced the full data flow myself: from the slot publishing system → Kafka → our Hollow ingestion → Cassandra write → platform read. I identified that the ingest job that wrote Cassandra had no `WITH TTL` clause. I documented the risk (stale slots = wrong delivery windows promised to customers) and proposed a 48-hour TTL — long enough to survive a short publishing outage, short enough to auto-heal when data comes back.

I brought this to the next sync with the the slot publishing team and our infra team together. I had the root cause, the data flow diagram, the proposed fix, and the risk if we don't fix it. I didn't ask someone to investigate — I brought the answer and asked for sign-off.

**Result:**
TTL was added before the feature went to prod. We also added a Grafana alert for slot data age (Hollow snapshot age for the CA slot cache). The feature launched cleanly. [VERIFY: did the alert ever actually fire post-launch? Was there a publishing outage? Fill in the real outcome or remove this sentence.] Because TTL was set [VERIFY: actual TTL value you used], the platform continued serving the last valid slots without customer impact during any publishing gaps.

**What I'd do differently:**
I would have flagged this at design time, not during implementation. I caught it late. A data flow + data lifecycle review as a pre-implementation checklist would have caught this earlier.

---

### Q2: Describe a project you drove end-to-end where you had to coordinate across multiple teams.

**STAR Answer:**

**Situation:**
Trace V2 was a migration from a single-blob tracing approach (V1, one large JSON object per order) to an event-per-category system (V2, typed Kafka events — ORDER, SOLUTION, TRIPLET, MOF, etc.) that land in BigQuery and the observability platform. V1 was query-hostile — one JSON blob meant analysts had to grep through large payloads. V2 needed coordination: the BigQuery schema team needed to create event tables, the analytics team needed to update their dashboards, and the platform needed to emit both V1 and V2 simultaneously during a transition period.

**Task:**
My role was the platform side — design the V2 event schema, implement `TraceEventPublisher`, run dual-write, and give the analytics team stable events to build against. But V2 would be useless if analytics didn't actually migrate their dashboards.

**Action:**
I led a schema review meeting with the analytics team before writing a line of code. I walked them through the V2 event categories and asked what fields they needed in each event to replace their existing V1 queries. We iterated on the schema together — they proposed extra fields I hadn't planned to include [VERIFY: what fields did analytics actually ask for? Replace with real field names]. I added them because it would have been a breaking schema change later.

Then I wrote the typed event mappers (`SolutionEventMapper`, `TripletEventMapper`, etc.) and made dual-write opt-in via a CCM flag — `trace.v2.kafka.enabled`. We turned it on at 1% of traffic, had analytics verify BigQuery was filling correctly, then ramped to 100% over 3 weeks.

I maintained a shared doc tracking: which event categories were live, which dashboards had migrated, and which were still on V1. I checked in weekly.

**Result:**
[VERIFY: actual timeline — was it ~6 weeks? Longer? Shorter?] V2 went fully live and V1 was deprecated cleanly. Post-launch, the analytics team could write SQL: "show me all orders where TRIPLET_REJECTED with reason SLA_TRIMMER_REJECTED in the last 24 hours" — queries that used to take hours of log grep now ran in seconds. [VERIFY: any specific wins analytics got with V2 that you remember? Real example beats generic.]

**What I'd do differently:**
I should have created a test BigQuery table with synthetic events before starting dual-write. [VERIFY: did you actually have a schema mismatch issue during dual-write? What was the real pain point during V2 ramp-up? Fill with real detail or use a different "what I'd do differently."]

---

## CATEGORY 2 — Disagreement / Conflict

### Q3: Tell me about a time you disagreed with a teammate or tech lead on a technical approach.

**STAR Answer:**

**Situation:**
When we were fixing the multihop ASD/EDD calculation, there were two bugs in the same area: the `HopType.DIRECT` hardcoding in `checkPlannedTNTApplicable`, and a double-count of the +1 day buffer for multihop items. My fix addressed both in one PR. The tech lead on the review suggested we should only fix the first bug in this sprint and defer the second to avoid risk — "smaller change, safer rollout."

**Task:**
I disagreed. Both bugs were in the same calculation path. Fixing only one would leave customers with wrong EDDs for multihop items — just a different kind of wrong.

**Action:**
Rather than just push back in the comment, I wrote a document explaining the two bugs with concrete examples:
- Bug A: multihop item with HopType.DIRECT hardcoded → TNT check uses direct lane even though item routes through a distribution center → EDD undershoots by 1-2 days
- Bug B: +1 day buffer double-counted for multihop → EDD overshoots by 1 day

I showed that with only Bug A fixed, we'd overshoot by 1 day for multihop items. We'd actually be further from correct than before. The partial fix was worse than neither fix.

I also showed the scope of the change — the second fix was 4 lines of code removing a ternary:
```java
// Before: hardcoded
int days = HopType.DIRECT.equals(hopType) ? 1 : 0;
// After: correct
int days = 0;  // buffer already counted in multihop TNT
```

The risk was minimal. The benefit of shipping both together: one regression test covers both scenarios, one rollout, one monitoring window.

**Result:**
The tech lead agreed after seeing the examples. Both fixes shipped together in the same PR. Monitoring showed EDD accuracy for multihop items improved — the mixed signal of "sometimes right, sometimes off by 1 day" went away. No regression.

**What I'd do differently:**
I should have flagged the second bug explicitly in my first PR description. If I had explained "this PR fixes two coupled bugs in the same calculation" at the start, the conversation might have been shorter.

---

### Q4: Describe a time you had to push back on a request from product or management.

**STAR Answer:**

**Situation:**
We received a request from the Canada business team: "Please add DFS delivery eligibility to these 17 stores." They had a list, they had a target date, and they wanted one config change — add `DELIVERY_FROM_STORE` to each store's `supported_service_eligibility` in Cassandra.

**Task:**
This would directly affect what delivery options customers were shown. If we enabled DFS for a store that had no carrier, no lat/lon, or no slot capacity — we'd promise delivery we couldn't fulfill. I needed to either validate the request or push back with evidence.

**Action:**
Before responding to the ticket, I ran a full diagnostic on all 17 stores across 6 data dimensions: SSE, carrier map, lat/lon, capacity rows, slot catchment. I scripted the queries and ran them systematically.

What I found:
- Only 1 store actually needed SSE added — the rest already had it
- 8 stores had no capacity rows (orders would be accepted and immediately fail at fulfillment)
- 5 stores had no lat/lon (the platform can't route to a store without coordinates)
- 3 stores had empty carrier maps (no LMD carrier configured)
- 2 stores were missing ROVR catchment (no customer zone maps to this store)

I went back to the business team with a structured writeup: here are your 17 stores, here is the actual blocker for each, here is which team owns each fix (DCC for operating hours, the slot publishing team for catchment, infra for capacity). I explicitly said: if we make only the SSE change, 16 of these 17 stores will still receive zero DFS orders after the change.

**Result:**
The business team thanked me and escalated to the right owners. The SSE change was made for the 1 store that needed it. The other issues went to the correct teams with clear ownership. Three weeks later, 14 of the 17 stores were fully enabled after coordinated fixes across teams.

**What I'd do differently:**
I would have asked for a pre-flight diagnostic as a standard step for any store enablement request — not something I did ad hoc. I've since proposed a checklist that the business team can share when they raise a ticket, so we have context upfront.

---

## CATEGORY 3 — Failure and Learning

### Q5: Tell me about a technical mistake you made. What happened and what did you learn?

**STAR Answer:**

**Situation:**
While working on a CA-specific feature, I wrote a method that returned a stream:
```java
return items.stream()
    .filter(x -> x.isEligible())
    .toList();
```

This compiled cleanly. Tests passed. Code went to prod.

**Task:**
The bug surfaced two weeks later when a downstream component tried to do:
```java
List<Item> eligible = getEligibleItems();
eligible.sort(Comparator.comparing(Item::getPriority));
```

This threw `UnsupportedOperationException` in production.

**Action:**
The root cause: in Java 16+, `Stream.toList()` returns an **unmodifiable list**. The old way — `Collectors.toList()` — returns a `ArrayList`, which is mutable. I had switched to the concise `toList()` idiom without reading the semantics. It's not just a style difference — it's a behavior change.

I fixed it immediately: `.collect(Collectors.toList())`. Then I searched the entire codebase for `.toList()` calls on streams and found [VERIFY: actual count — was it ~8? More? Less?] more places where the returned list was later modified or passed to code that might modify it. I converted all of them.

I wrote a short internal note explaining the Java 16+ behavior change and asked to add a Checkstyle rule or comment to the code standard doc.

**Result:**
No further incidents. The Checkstyle rule wasn't added (too much noise for valid uses of `toList()`) but I added a comment to our Java upgrade notes: "If upgrading to Java 16+, audit all `.toList()` usages — check if the returned list is ever mutated downstream."

**What I learned:**
API conciseness and API semantics are different things. `toList()` looks like a drop-in replacement for `collect(Collectors.toList())` but it isn't. When adopting new language features, read the contract, not just the syntax. Especially for things that "should" be the same but weren't designed to be.

---

### Q6: Describe a time your investigation led you to a conclusion that turned out to be wrong.

**STAR Answer:**

**Situation:**
We had a bug where Mexico orders were getting delivery dates that were one hour late — customers expecting an EDD of March 15, 11 PM were getting March 16. Initially I assumed it was a timezone offset bug — some component was applying UTC-6 when Mexico City should be UTC-6, so maybe someone used UTC-5 somewhere.

**Task:**
I started looking at the wrong layer. I spent about 3 hours reviewing the carrier transit time calculation and the EDD assembly in `SingleItemSolutionWithDateBuilder.java`. I was confident the bug was there — transit time input was in UTC, conversion should apply `-6`.

**Action:**
But the transit time math was correct. When I added debug logging to print the input timezone, it showed `America/Mexico_City` — which is the right IANA timezone ID for Mexico City. Everything looked correct.

Then I re-read the code more carefully. The code was using:
```java
ZoneId.of("America/Mexico_City")
```
`America/Mexico_City` **observes DST** — it shifts from UTC-6 to UTC-5 during summer months (April–October). Mexico's federal law changed in 2023 to stop observing DST nationally, but the JVM timezone database `America/Mexico_City` still reflected the old DST rules unless the JVM had received the tzdata update.

The fix was **not** to fix the transit time code. It was to change the timezone resolution to use a **fixed offset**:
```java
ZoneOffset.ofHours(-6)  // always UTC-6, no DST
```

**Result:**
Delivery date calculations for Mexico normalized. The `America/Mexico_City` IANA ID would have self-corrected eventually when the JVM received the updated tzdata, but a fixed offset guarantees correctness regardless of JVM patch state.

**What I learned:**
I should have verified my hypothesis (timezone offset bug in transit calculation) with a diff test before spending hours in the wrong layer. If I had written: "expected timezone = UTC-6, actual = ?" in a test first, I'd have seen the IANA zone name was correct and pivoted faster. Confirm assumptions early, don't deep-dive the wrong thing.

---

## CATEGORY 4 — Ambiguity and Incomplete Information

### Q7: Tell me about a time you had to make a decision without complete information.

**STAR Answer:**

**Situation:**
During the CA V5 onboarding, we needed to determine the correct Cassandra key format for the CA slot cache. The the slot publishing team (who owned slot data publishing) was still finalizing their schema. We were 1.5 sprints from code freeze. If I waited for their final schema, I'd have no time to implement.

**Task:**
I needed to implement the CA slot query key generation — `generateStoreSlotQueryKeys(storeId, fulfillmentType, businessUnit)` — but the exact key format wasn't finalized. Two formats were being discussed by the slot publishing team: `<storeId>:<fulfillmentType>:<buId>` and `<storeId>-<buId>-<fulfillmentType>`.

**Action:**
Rather than block or guess, I separated the key format from the key usage. I implemented the slot lookup logic with an abstracted key builder:
```java
// Key format extracted behind a method — format can change without touching callers
private String buildSlotKey(String storeId, String fulfillmentType, String buId) {
    return storeId + ":" + fulfillmentType + ":" + buId;  // configurable
}
```

I also made the key delimiter a CCM config: `ca.slot.key.delimiter = ":"`. This meant if the slot publishing system changed their format, I could update in production with a config push, no deploy.

I sent the slot publishing team a test payload using my assumed format, asking them to confirm or correct it. While waiting, I completed all the surrounding logic — slot filtering, window mapping, TWO_HOUR_SPEED eligibility.

The slot publishing team confirmed the format 4 days later — it matched my assumption. But even if it hadn't, the CCM-configurable delimiter meant zero rework.

**Result:**
Code freeze was met. The CA slot lookup integrated cleanly. No rework on the key format. Post-launch, the CCM delimiter was used once — when the slot publishing team added a new fulfillment type and needed a slightly different key structure for it.

**What I'd do differently:**
I should have created a contract test between our service and the slot publishing system earlier — a simple round-trip: "here's a key we're sending, here's what we expect back." Contract tests would have formalized this instead of email-based confirmation.

---

### Q8: How do you work when requirements are unclear or changing?

**STAR Answer:**

**Situation:**
When I was working on propagating supply and nodeSellable from the inventory service into the CA sourcing response, the product requirement was vague: "make sure the CA promise shows inventory availability from the inventory service." The exact fields needed, where they'd be consumed, and what the downstream contract was — none of this was documented.

**Task:**
I needed to implement this without a clear spec. Getting it wrong would either: (a) add fields that nobody reads, wasting serialization, or (b) add the wrong fields and require breaking changes later.

**Action:**
I identified all the consumers of the CA promise response — there were 3 at the time. I read each consumer's code to understand what data they actually used from the response. For two of them, they used delivery date, carrier, and service type — no inventory fields at all. For the third, they were planning to use inventory data, but the product team hadn't written the consumer yet.

I scheduled a 30-minute call with the consumer team and asked them to walk me through what they'd need from inventory. They needed `supplyAvailable` (boolean) and `nodeSellerType`. I added exactly those two fields to `AvailabilityServiceV2Mapper.java`:
```java
// Propagate supply from the inventory service for CA V5
if (isCADiscoveryRequest()) {
    return propagateSupplyFromthe inventory service(sourcingContext, node);
}
```

I used the existing generic `<T>` pattern so both `SaleableNodeV3` and `SellablePreferredNode` types would work without duplication.

**Result:**
When the consumer team shipped their feature 3 weeks later, the fields they needed were already in the response. No rework. The contract was established by conversation before implementation, not discovered after.

**What I learned:**
Vague requirements don't have to be blocking — they can be made specific by talking to the consumers of the work. The spec lives in the minds of the people who will use what you build. Go find them.

---

## CATEGORY 5 — Influence Without Authority / Cross-Team Work

### Q9: Tell me about a time you influenced a decision you didn't have authority to make.

**STAR Answer:**

**Situation:**
After the Mexico DST bug — where delivery dates were off by one hour because `America/Mexico_City` observes DST — I realized the broader pattern: across the platform, we had [VERIFY: actual count of IANA ZoneId usages in date calculation code] places where timezone handling used IANA zone IDs for markets that either had changed their DST rules or where DST behavior was unreliable. [VERIFY: did you actually identify other services with the same pattern? Which ones? If not, remove the cross-team angle and keep it platform-internal.]

**Task:**
My scope was our platform. I had no authority over the other services. But if those services had the same timezone bug, they'd surface the same production incident — and they wouldn't know what to look for.

**Action:**
I wrote a short tech note: "DST risk in multi-market systems — what we learned and what to check." It covered:
- The root cause in the platform (IANA zone ID vs fixed offset)
- Which markets are most at risk (Mexico, any market that has changed DST policy recently)
- A simple code audit checklist: "grep for `ZoneId.of(` in your date calculation code — if you're using a named zone for a market that controls DST by law, not by geography, you may have this bug"
- The fix pattern: use `ZoneOffset.ofHours(N)` for fixed-offset markets

I shared this in the #mcse-engineering Slack channel and tagged the TLs of the two other services. No Jira ticket, no formal process — just "here's something we found, here's the check, here's the fix."

**Result:**
[VERIFY: did the other services actually respond? Did anyone find the same pattern? If the cross-team part didn't happen, simplify this story to be platform-internal: "I audited the codebase, found all ZoneId usages, and standardized to fixed offsets across all markets we owned. The tech note went out so other teams had the pattern." That's still a strong story without fabricated outcomes.]

**What I'd do differently:**
I should have proposed adding this to a shared engineering wiki page on "known Java gotchas for multi-market systems." The Slack message was useful in the moment but will get buried. Durable documentation is worth the extra 30 minutes.

---

### Q10: Describe a time you had to get buy-in from someone who was resistant to a change.

**STAR Answer:**

**Situation:**
When introducing the Trace V2 schema review with the analytics team, I encountered resistance. Their lead was concerned about migrating dashboard code — they had [VERIFY: actual number of dashboards/panels on V1] Grafana panels and BigQuery queries built on V1. "V1 works. Why break what's working?"

**Task:**
I needed their buy-in to define the V2 schema before I implemented it. Without their input, V2 would likely miss the fields they needed, and they'd have to either accept incomplete data or request a breaking schema change later.

**Action:**
I came to the conversation with a different framing. Instead of "V2 is better, you should migrate," I said: "I'm designing V2 right now. If you tell me what you need, I can build it in from day one — so migration is just updating a query, not rebuilding logic."

Then I showed them a concrete comparison:
- V1 query for "show rejected solutions for a given order": parse a large JSON blob with a `WHERE CONTAINS(blob, 'REJECTED')` — fragile, slow
- V2 query for the same thing: `SELECT * FROM triplet_events WHERE correlation_id = '...' AND status = 'REJECTED'` — typed, indexed, fast

Once they saw the query complexity reduction, the conversation shifted from "why change" to "what fields do I need." They asked for additional fields I hadn't planned [VERIFY: what did analytics actually ask for? Real field names here]. I added them because it would have been a breaking schema change to add them later.

**Result:**
[VERIFY: how long did their migration actually take? What was the real win analytics got from V2 that you remember? Replace with real outcome — e.g., "they were able to build the rejection heatmap they'd been asking for" or whatever actually happened.]

**What I'd do differently:**
I should have shown them the V2 query preview even earlier — in the initial proposal. The moment of buy-in happened when they saw the query. I could have led with that.

---

## CATEGORY 6 — Customer Impact and Standards

### Q11: Tell me about a time your work had a direct impact on customers.

**STAR Answer:**

**Situation:**
The ASD/EDD multihop bug was causing wrong delivery dates for items that routed through a distribution center on their way to a customer. These are multi-leg shipments: FC → distribution center → customer. For these items, the platform was using `HopType.DIRECT` (single-leg) in the TNT check, which meant the system used a shorter transit time than the actual route. Customers got delivery promises that were 1-2 days earlier than actually achievable.

**Task:**
These were not hypothetical customers. Wrong EDD meant the company was making delivery promises it couldn't keep. The downstream impact: missed delivery → customer dissatisfaction → potential returns, NPS impact.

**Action:**
Once I identified the root cause — `HopType.DIRECT` hardcoded in `checkPlannedTNTApplicable` — the fix was surgical:

Before:
```java
boolean isEligible = checkPlannedTNTApplicable(origin, destination, HopType.DIRECT, tnt);
```

After:
```java
boolean isEligible = checkPlannedTNTApplicable(origin, destination, hopType, tnt);  // use actual hopType
```

But I also fixed the second coupled bug — the double-counted +1 buffer for multihop. Both went in the same PR.

I wrote the PR with two test cases: (1) direct hop item, assert EDD unchanged; (2) multihop item, assert EDD is now +1 longer than before (correct). Both tests passing proved the fix was complete and didn't regress direct-hop items.

**Result:**
Multihop EDD accuracy improved measurably. Carrier performance alignment (how often we promise a date vs how often we deliver it) improved for the affected routes. The fix was behind a CCM flag for the first week, with gradual rollout — no surprises in production.

**What I'd do differently:**
I'd instrument this earlier. We should have had a Grafana panel specifically for EDD accuracy per hop type. If we had, the multihop accuracy gap would have surfaced as an alert, not as a business escalation.

---

### Q12: Tell me about a time you raised the quality bar on your team.

**STAR Answer:**

**Situation:**
After the HashMap non-determinism bug (`.keySet().iterator().next()` returning different keys on different pods), I realized we didn't have a good shared vocabulary for "this code has non-deterministic behavior." The PR had been reviewed — by two engineers including me — and the pattern wasn't caught. It looked innocuous.

**Task:**
My goal wasn't to blame the review. The goal was to make the team better at catching this class of bug.

**Action:**
I built a short, practical guide: "Concurrency and Determinism Code Review Checklist." It covered 5 patterns that look innocent but are actually risky:

1. `.keySet().iterator().next()` on a `HashMap` with multiple entries — non-deterministic iteration order
2. `Collections.sort()` on a shared list during request handling — race condition if list is shared
3. `Stream.toList()` when the caller might mutate the result
4. `ThreadLocal` in code that dispatches to a thread pool without capturing context
5. `CompletableFuture` without `.exceptionally()` — silently swallowed exceptions

For each one: what it looks like, why it's risky, what the safe alternative is.

I presented this at our next team review session — 20 minutes, not a formal meeting. I framed it as "here's what we've tripped on in the past 6 months, here's what to look for." I put it in our internal wiki.

**Result:**
[VERIFY: did anyone on the team actually use this checklist? Did it catch something in a real PR? If yes, fill in the real story — that's the best outcome. If not, the result can be simpler: "The guide became a reference artifact during code reviews. I started seeing people cite it in PR comments." Only claim what actually happened.]

**What I'd do differently:**
I should have turned the checklist into automated tests or static analysis rules where possible. Pattern #3 (`.toList()` mutation) can be caught with a Checkstyle rule. Manual checklists get forgotten faster than automated enforcement.

---

## CATEGORY 7 — Prioritization and Trade-offs

### Q13: Tell me about a time you had to prioritize between multiple important things.

**STAR Answer:**

**Situation:**
In the same sprint, I had: (1) the Mexico DST bug causing wrong EDDs in production, (2) the first half of the CA performance investigation (serialization overhead), and (3) a feature I was mid-sprint on — supply/nodeSellable propagation from the inventory service.

**Task:**
All three were important. The DST bug was in production causing wrong customer promises. The perf investigation was blocking a CA latency SLA. The feature had a sprint commitment.

**Action:**
I did a fast triage by customer impact:
1. Mexico DST: production incident, wrong delivery dates being promised right now. Fix first. It was also a 4-line fix — not a high investment.
2. CA performance investigation: not causing wrong data, causing high latency. Bad but not customer-promise-breaking. I got a rough timeline (how long has this been elevated?) from Grafana — it had been elevated for 3 days, wasn't a sudden spike. I could finish DST first.
3. Feature work: sprint commitment. Behind a CCM flag, not live yet, no external pressure.

I fixed the Mexico DST bug in half a day, pushed for deploy, flagged it in the daily standup. Then I continued the CA perf investigation — which took another 1.5 days of timing log analysis before I found the `boxesToPackedItemBoxMap` root cause. Feature work happened in parallel during review cycles.

**Result:**
All three completed in the same sprint. No sprint items were deferred. Mexico DST was in production within 24 hours of discovery.

**What I'd do differently:**
I would communicate this priority call to my tech lead at the start, not wait until standup. "I'm pivoting to DST today, here's why, I'll pick up CA perf tomorrow" — 2-minute message. I did it at standup, which was fine, but earlier visibility is better.

---

### Q14: How do you decide when a piece of technical debt is worth fixing now vs. later?

**Answer (non-STAR — conceptual question):**

I think about it along two axes: production risk and opportunity cost.

**Production risk:** Does this debt have a realistic failure mode in prod? The `HopType.DIRECT` hardcoding in the multihop path was high-production-risk — it was silently producing wrong results for a real use case. That's not deferred, that's fixed immediately. The Chain of Responsibility filter code that has grown messy over 18 months — it works correctly, it's just hard to extend. That's deferred.

**Opportunity cost:** What does it cost us to keep carrying this debt? If a messy abstraction slows down every new market onboarding by 2 weeks, the debt is expensive even if it's not failing. If a deprecated helper method just sits unused in the codebase — low cost, low urgency.

**My actual framework:**

- **Fix now:** Debt that could cause incorrect data (wrong EDD, wrong rejection, missed sourcing) or a production incident. Also debt that's in the hot path of a sprint I'm already doing — if I'm touching a file anyway, I clean it.
- **Fix next sprint:** Debt that slows velocity for future features, or debt that's becoming a recurring question in code review ("why is this here?").
- **Track:** Debt I've identified but can't act on now. I write a comment in the code and a ticket. Untracked debt doesn't exist as far as the team is concerned.
- **Accept:** Debt that's isolated, well-contained, and whose fix would require a large refactor with unclear benefit. The `isCADiscoveryRequest()` guards are technically debt — a true multi-market framework would be cleaner. But they work, they're explicit, and a refactor would touch 30+ files. Accept for now.

---

## CATEGORY 8 — Data-Driven Decisions / Changing Your Mind

### Q15: Tell me about a time you changed your approach based on data or feedback.

**STAR Answer:**

**Situation:**
For the CA serialization performance fix, my first instinct was to add a size limit — if `boxesToPackedItemBoxMap` exceeds N entries, truncate it before logging. This felt clean: cap the size, problem solved, no behavioral change.

**Task:**
Before implementing, I looked at the production data — what was the actual size distribution of this map in practice?

**Action:**
I added a temp log line for [VERIFY: how long did you capture? A day? A few hours?] of CA traffic to capture map size per request. The distribution was [VERIFY: what did the data actually show? The bimodal split (normal vs. large) is plausible — fill in the real numbers if you remember them, or keep it qualitative: "most requests had small maps, but the high-latency requests all had much larger maps"]. The high-size cases were exactly the ones hitting our latency SLA.

This changed my approach. A size limit of, say, 20 entries would truncate exactly the high-volume cases — the ones we're having trouble with. But truncating an observable (the map) that exists for debugging purposes would make those exact cases harder to investigate post-incident.

The right fix wasn't to truncate — it was to **not serialize this map at all during promise calls** (where the data was being logged redundantly) and only serialize it during the initial sourcing call. The data was logged at both points due to a code path that shared the same `TraceEventPublisher.logSolution()` method.

**Result:**
Fix was `clearBoxesToPackedItemBoxMap()` before the promise call's serialization path — behind a CCM flag for safe rollout. CPU dropped, latency normalized, no debugging information was lost because the map was still logged during sourcing.

**What I learned:**
My first instinct (size limit) would have worked, but it would have introduced a subtle debugging regression. The data-driven step (what does the distribution actually look like?) led to a better solution. Never skip the "look at actual production data" step for a performance fix.

---

## QUICK REFERENCE — Short Answers for Rapid-Fire Follow-Ups

**"What's the most complex system you've worked on?"**
> "The platform — 700K requests/min, sub-100ms p95, 16 in-memory caches hydrated by Kafka, multi-market (US/CA/MX), with a parallel CompletableFuture-based orchestration that has to never fail to return a response. Complexity isn't just the scale — it's that wrong output (wrong EDD) is worse than no output. We'd rather return a conservative fallback than return a confidently wrong delivery date."

**"Tell me about a time a production incident taught you something."**
> "The Mexico DST bug. I assumed IANA timezone IDs were static. They're not — they reflect current government DST policy, and that policy changed. The lesson: for anything where government action can change the answer (timezone, tax, holiday calendar), you need an explicit policy decision in code, not reliance on a library that might lag real-world changes. Fixed offsets for markets that explicitly don't observe DST."

**"What do you do when you're stuck?"**
> "I time-box the stuck state to 30 minutes. If I'm not making progress, I externalize the problem — write down what I know, what I've tried, what the remaining hypotheses are. Usually the act of writing it clarifies one thing to try next. If that doesn't work, I ask someone — but I ask with context: 'here's what I've tried, here's where I'm confused.' I don't ask until I've worked through my own understanding first."

**"How do you handle being wrong publicly?"**
> "Correct course, explain what I missed, move forward. During the Mexico DST investigation I spent 3 hours on the wrong layer before finding the real root cause. In the next standup I said 'I was looking at transit calculation — that was wrong, real issue is IANA zone ID behavior.' No drama. Being wrong isn't the problem; staying wrong is."

**"What does 'senior' mean to you?"**
> "Handling ambiguity without being blocked. A mid-level engineer needs a clear spec. A senior engineer figures out what the spec should be. Also: seeing the second-order effects. Not just 'does this code work?' but 'if this code is wrong in production, how will we know? Who else is affected? What's the rollback story?' Those questions have to become automatic."

---

## CATEGORY 9 — SITUATIONAL JUDGMENT / PRESSURE SCENARIOS

> These are "how would you handle" questions. Answer as you would in the actual interview — first person, conversational, show your reasoning. Don't recite a framework, just think out loud and let the structure emerge naturally.
>
> ⚠️ For **interpersonal scenarios** (S9, S10, S12): bar raisers will ask "give me a specific time." Make sure you have a real story ready, not a generic description. Where your real story fits Story G in `15_BEHAVIORAL_ANSWERS_FULL.md`, reference that. Where it doesn't, fill in from your own memory.

---

### THE COMMON SPINE (most situational questions share this)

Almost every pressure scenario comes back to the same instinct: **surface it early → name the trade-off explicitly → give your recommendation, let the stakeholder decide → commit fully once they do.**

What bar raisers are looking for:
- Do you communicate bad news fast, or do you hide until the deadline explodes?
- Do you give the person above you full information, or do you filter it?
- Do you treat the deadline as a constraint to optimize inside, or as a reason to cut corners?
- Do you own the outcome even when you didn't cause the problem?

---

### S1 — Testing finds the feature is incomplete or buggy, just before delivery

*LPs: Insist on Highest Standards, Deliver Results, Ownership*

The first question I ask is: how wrong is it? Because "missing a UI state" and "returning incorrect data to customers" are completely different situations. The severity drives everything.

If it's a correctness problem — something that would give customers a wrong answer — I don't ship it. I go to the PM right away with something concrete: here's exactly what's broken, here's my estimate to fix it, and here's what we can still release now. Usually there's a way to split it: ship the working parts, gate the broken path so it's unreachable in production. In the platform we do this with CCM flags — you can deploy code while keeping a feature off until you're confident it's right.

If it's more of a polish or edge-case thing — I document it, propose a follow-up story, and let the PM decide. I give them the information, not the decision.

What I don't do is ship something I know is wrong and let someone else discover it. That's a worse outcome for everyone.

*If they push back: "What if PM says 'ship it anyway'?"*
> I make the risk concrete for them. In the platform side: "this will cause customers to see wrong delivery dates — they'll get orders late, and that generates support volume and potentially order cancellations." That's my job — to make sure they're deciding with real information, not just the date pressure in their head. The call is theirs. But I put it on the table.

---

### S2 — You discover a critical bug in your own code, just before a big deployment

*LPs: Ownership, Have Backbone, Earn Trust*

Honestly, the first move is just — don't deploy. There's no question there if the issue is in the critical path. The thing people get wrong is thinking they can sneak in a fix and nobody needs to know. That almost always backfires.

The second thing is to understand what kind of problem it is. Can I fix this in a few hours and get another review pass? Does it need a data migration? Can it be gated off via CCM so we can still ship the binary without turning the feature on? That question matters because it changes what I tell people.

Then I communicate immediately — not "I found a bug and I fixed it," but "I found a bug and I'm blocking the release." The specifics: what it is, what it would do in prod, what my estimate is to fix it. Then I follow through.

*If they ask: "What if there's pressure to hit a business date?"*
> I separate "deploy date" from "feature-on date." We can get the code into production with the feature gated off via CCM — that satisfies the deploy window — and buy time to fix the bug before turning it on. Most timeline pressure comes from conflating those two things. Once people see they don't have to choose between "ship with the bug" and "miss the date," the conversation gets a lot easier.

*Real grounding:* The Mexico DST bug reinforced this for me. "It won't trigger in prod until March" is not the same as "it's safe." A latent bug with a future trigger is more dangerous than one that fails immediately, because it's much harder to catch in staging.

---

### S3 — Days before deployment, business brings a significant new requirement

*LPs: Have Backbone, Earn Trust, Customer Obsession*

My approach is to treat it as a trade-off negotiation, not a yes/no. The minute someone brings a late requirement, I want to give them a real estimate — not a gut feel, but an actual look at the work. "This is X days of implementation, Y days of testing." Then I lay out the options: we add this and push the date by X, or we ship without it and pick it up next sprint.

What I try to separate is what has to be in the binary versus what can be configuration. If the requirement is something that can be expressed as a config value, a flag, a feature toggle — I'll often just build it configurable and let them change it post-deploy without a code change. That sidesteps the whole deadline vs. requirement conflict.

If the requirement is critical — regulatory, legal, customer safety — then I'll protect the date by cutting lower-priority scope, not by cutting testing.

*Real grounding:* When CA V5 was being built, the Cassandra key format for Canada wasn't finalized while I was implementing. I made the delimiter CCM-configurable. That meant the format could be changed after deploy without touching code. Business could make the call whenever they were ready. That bought us time without creating risk.

---

### S4 — Manager brings a higher-priority task while you're mid-implementation

*LPs: Bias for Action, Earn Trust, Deliver Results*

The first thing I do is understand whether this is actually a fire or just a priority reorder. Those are different situations. If production is down or someone is completely blocked on me, I context-dump immediately — I write down exactly where I am, what's left, what the risk is of stopping here — and I switch. If it's a shift in priority but nothing is burning, I'll usually say: "I'm 2 hours from a clean stopping point — do you need me now or can I finish this out and hand it off properly?" That's usually fine.

What I never do is just silently drop what I'm doing and leave something half-done without documenting state. That's how work gets lost or causes problems later when someone picks it up without context.

*Real grounding:* I've seen this in practice — during the DST investigation, I had CA performance work in flight that had to go on hold because the Mexico DST issue had a real time pressure (a specific date when clocks would change). The triage was: customer impact from a wrong delivery date on a specific future date is not recoverable, CA perf is recoverable. Customer impact + time-sensitivity wins.

---

### S5 — Asked to cut testing or skip code review to hit a deadline

*LPs: Have Backbone, Insist on Highest Standards*

I push back, but not with "no" — with "here's what skipping testing actually costs." I make it concrete: if this goes wrong in prod, here's the customer impact, here's the remediation time, and that's a much bigger miss than a 2-day slip. Then I offer the real alternative: what can we cut from scope to make the date, while keeping test coverage intact?

I won't skip code review on high-traffic code paths. I have a real example of what happens when you do — the `.toList()` bug went to prod from my own code (Q5 in this file). One day of review would have caught it. The cost of that kind of prod incident is almost always higher than the cost of the review.

The line I've settled on: I'll agree to ship with less scope. I won't agree to ship with less confidence in what does ship.

*If they override me:* I put my concern in writing — a Jira comment, a Slack message — so that if the risk materializes, we can learn from it. Not to protect myself, but because that's how a team actually gets better. Then I execute on the decision with full commitment.

---

### S6 — Mid-sprint you realize your estimate was badly off — task is 2x the work

*LPs: Earn Trust, Ownership, Bias for Action*

I communicate it as soon as I know, not at the end of the sprint when it's too late for anyone to help. "I thought this was 3 days. It's looking like 6. The complexity I didn't see in the estimate was [specific thing]. Here's how I see our options: I can descope X to hit the sprint goal, take an extension with this heads-up now, or we parallelize if someone can pick up Y."

The key is bringing options, not just the problem. A manager who hears "I'm going to miss by 3 days" with no further information can't do anything with that. A manager who hears "I'm going to miss by 3 days, but here are three ways to handle it" can actually make a call.

*Real grounding:* Our platform has high coupling — anything that touches CCM validation, the Hollow cache structure, or existing API contracts tends to reveal more work than the estimate shows. I've learned to front-load a short integration check (even a couple of hours) before committing to a timeline on anything that touches external systems. Catching the underestimate on day 1 instead of day 8 changes everything.

What I never do is work nights trying to silently close the gap. That's the worst outcome — I'm exhausted, the work is rushed, and my manager had no chance to adjust anything.

---

### S7 — Post-deploy, metrics look degraded but not catastrophically — roll back or watch?

*LPs: Bias for Action, Ownership, Dive Deep*

The decision that matters most here happens before the deploy, not after it. Going in, I define what "roll back" looks like: if error rate crosses X, or p95 goes above Y, we roll back — no debate in the moment. If latency is up 15% but errors are flat, we watch for 20 minutes, and if it doesn't recover we roll back. Those thresholds need to be agreed on pre-deploy, because making this call under pressure with ambiguous signals is how people talk themselves into leaving a bad deploy up too long.

If something is genuinely unclear post-deploy, I lean toward rolling back. The cost of rolling back and re-deploying is almost always smaller than the cost of a prolonged incident. "Watch and see" is a reasonable strategy only if you've already defined what you're watching for and what triggers action.

*Real grounding:* At platform scale — 700K requests/minute — even a 1% error rate is 7,000 wrong delivery dates per minute going to customers. The per-minute cost of watching is real. That context pushed me to be very definitive about pre-deploy thresholds rather than relying on in-the-moment judgment.

---

### S8 — You're blocked: dependency from another team is late, your work can't proceed

*LPs: Ownership, Bias for Action, Earn Trust*

The first thing I ask is whether I can remove the dependency entirely — can I mock or stub it and keep moving? Can I redesign so I don't need it until later? In the platform, when the supply/nodeSellable spec from the inventory service was vague going into implementation, I went directly to the consumer teams to define the contract myself rather than waiting for a finalized spec to arrive. That's usually the most valuable move — don't wait for someone to hand you what you need, go figure out what it should be.

If I can't remove the dependency, I look at what I can resequence — what else in the sprint can I make progress on while this resolves?

If the dependency is genuinely late and it's blocking the critical path, I escalate to my manager with specifics: "Team X's deliverable is 5 days late, it blocks Y and Z, here's the sprint impact." Management decides the escalation path — I don't go directly to the other team's engineers unless there's an existing working relationship that makes that appropriate.

What I don't do is quietly let it slip to sprint end. If I know on day 2 that something's going sideways, my manager needs to know on day 2.

---

### S9 — You're leading a project and a teammate is consistently falling behind

*LPs: Ownership, Hire and Develop the Best, Earn Trust*

⚠️ **Bar raiser will ask: "Give me a specific example."** Use Story G from `15_BEHAVIORAL_ANSWERS_FULL.md` if it fits, or recall your own real instance before the interview.

The way I think about this: there are usually two different root causes, and the response is completely different depending on which one it is.

The first is a clarity problem — the person is behind because the task is bigger than they understood, or the spec is unclear, or they're blocked by something they haven't surfaced. That's a pretty easy fix once you know that's what's happening. A quick conversation usually surfaces it: "walk me through where you are — what's the next thing you're working on?" That question usually reveals the actual problem within 5 minutes.

The second is a capacity or skill problem — the work is genuinely beyond what they can do right now, or something personal is affecting them. That's a harder conversation, but it's still a conversation you have early, not after the project slips. I'd go to them privately: "I've noticed X is taking longer than we planned, I want to make sure I understand what's in the way." The goal is to understand, not to confront. Usually people are more aware of the delay than you think, and they're often waiting for someone to give them an opening to say it.

What I don't do is re-assign their work silently and hope they catch up on their own. That creates resentment and doesn't solve the problem.

If it's persistent despite those conversations, that's when I loop in my manager — not to complain, but to say "I've tried X, I've noticed Y, I think we need your help here."

---

### S10 — A teammate's code quality is repeatedly below bar — same issues in every PR

*LPs: Insist on Highest Standards, Earn Trust, Hire and Develop the Best*

I'd have this conversation directly with them, not just in code review comments. Comments in a PR are visible to the whole team and can feel like a public correction — which often makes people defensive rather than receptive.

What I've found works better is a 1-on-1 conversation outside the PR. "I've been seeing the same pattern in a few of your recent PRs — [specific pattern, not 'your code is bad']. I want to share how I think about this and see if it's useful." Then I show what I'd do differently and why. Not "this is wrong" but "here's the risk this creates, here's how I've avoided it."

If the same pattern keeps showing up after that conversation, I'd be more direct: "We talked about X a few weeks ago and I'm still seeing it in PRs. I want to understand if there's something I can explain differently, or if there's something bigger going on." At that point, if it still doesn't change, I'd involve my tech lead or manager — not to escalate punitively, but because at some level this is affecting the team's velocity and it's not something I can solve alone.

*Real grounding:* After the `.toList()` bug (Q5 in this file), I wrote up a short checklist on common Java mutation/immutability traps and shared it in our team channel. That was a way to raise the floor without singling anyone out.

---

### S11 — You're on-call at 2am, production is down, you have no idea why

*LPs: Ownership, Dive Deep, Bias for Action*

→ **Full story already in `15_BEHAVIORAL_ANSWERS_FULL.md` — Story B (100% CPU incident).** Use that. The core of it is: stay calm, start with observability (logs, metrics, dashboards), form a hypothesis before changing anything, don't guess-and-pray by randomly reverting recent deployments. The first action is to understand, not to fix.

Quick version: the first thing I look at is what changed recently — deployments, config changes, upstream traffic patterns. Then I look at where the system is spending its time — CPU, GC, thread pool saturation, I/O. I'm forming a hypothesis before I touch anything. Once I have a hypothesis I test it, and if confirmed I act. If not confirmed I go back to observability. I don't make random changes under pressure hoping something works.

---

### S12 — Manager gives you critical feedback you didn't expect

*LPs: Learn and Be Curious, Earn Trust*

⚠️ **Bar raiser will ask for a specific time.** Have your own real example ready.

My instinct in the moment is to listen fully before responding — not to defend, not to explain. Just to understand exactly what they're seeing. Something like: "Can you give me an example of what this looked like from your side?" That tells me whether it's a behavior they've observed or a perception gap, and those are different problems.

Once I understand it, I take a day to sit with it. Critical feedback often lands harder than it should in the moment, and my initial reaction isn't always the most useful one. If I still disagree with the assessment after thinking it through, I'll say so — but specifically, with my reasoning, not defensively. "I hear what you're describing, and I want to share how I saw the situation, because I think there might be a gap in what was visible to you versus what was happening." That's different from just pushing back.

If the feedback is valid — and most of the time there's at least something in it — I take one concrete action immediately, not a vague "I'll work on it." Something specific that the person can observe changing.

---

### S13 — Two stakeholders want conflicting things from your feature/API

*LPs: Are Right a Lot, Earn Trust, Customer Obsession*

I try to get them in the same room before I make any technical decision. If I implement one version and then surface the conflict, I've already burned time and potentially set an expectation with one side. Better to surface the conflict early and make it their problem to resolve with each other, with me facilitating — not my problem to resolve alone.

My role in that conversation is to make the technical trade-off explicit: "If we go with A, here's what B can't do. If we go with B, here's what A can't do. Is there a design that serves both?" Sometimes there is — often it's a generalization or a configuration parameter. Sometimes there isn't, and then the business stakeholders need to decide the priority.

*Real grounding:* On the CA V5 Cassandra key format — the format wasn't agreed on between teams, and rather than picking one I made it CCM-configurable. Both sides could adjust after the fact without a code change. That kind of flexibility is often the right answer when stakeholders disagree on something that's genuinely arbitrary.

---

### S14 — You spot a significant problem clearly outside your domain — raise it or not?

*LPs: Ownership, Dive Deep, Think Big*

Always raise it. The question is how, not whether.

I try to be specific about what I observed and what I think the risk is, without overstepping into telling someone how to do their job. "I noticed X in the [other service/code path]. It might be fine, but the risk I see is Y. Wanted to flag it — let me know if it's already known." That's it. I'm not demanding they fix it, I'm not filing a bug on their behalf. I'm putting the information in front of the person who owns it.

*Real grounding:* After fixing the Mexico DST bug, I sent a note to neighboring services — the ones I knew also had market-specific datetime handling — flagging the pattern I'd found and what to check for. Some of them found the same issue in their own code. That cost me 30 minutes. The alternative was that they hit the same bug in production independently, which is much worse for everyone.

If I see something and say nothing, that's on me. Ownership doesn't stop at your own files.

---

### S15 — You're asked to take on something significantly larger than you've done before

*LPs: Bias for Action, Learn and Be Curious, Earn Trust*

Honestly, my first reaction is to figure out what I don't know rather than pretending I have it all figured out. The worst thing I can do is agree without surfacing the uncertainty, go dark for two weeks, and then come back with something that missed the mark.

What I try to do early is identify the highest-risk unknowns — the things I've never done before that could go sideways — and tackle those first. Not the parts I know how to do. The known parts I can do at the end. I want to learn early whether the hard thing is actually hard, while there's still time to adjust.

I also ask for a checkpoint pretty early — "let me get something in front of you in a week so we can verify I'm going in the right direction before I'm deep into it." That's not weakness, that's how you reduce the risk of wasted work.

*Real grounding:* The CA V5 onboarding involved a completely new slot-fetching pipeline and a new Cassandra key structure I'd never built in this codebase. I front-loaded the parts with the most unknown surface area — the key generation logic and the integration with the new query structure — and got early eyes on those before finishing the full implementation.
