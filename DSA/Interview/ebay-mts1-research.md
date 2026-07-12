# eBay MTS 1 — Raw Interview Research (Jan 2025 – Jul 2026)

> **Scope:** Member Technical Staff 1 (Backend), Bangalore / Toronto / San Jose
> **Sources:** 30+ searches across LC Discuss, Glassdoor, Blind, 1Point3Acres, CodingKaro,
> Taro, InterviewKickstart, CodeJeet/FleetCode, SystemDesignHandbook (Jul 2026)
> **How to use this file:** This is the raw evidence layer. For worked solutions with
> patterns + code, see `ebay-mts1-dsa-problems.md`. For system design, see `ebay-mts1-sd-hld.md`.

---

## 🗺️ Table of Contents

1. [Interview Process / Format](#1-interview-process--format)
2. [DSA Questions — Confirmed Reports](#2-dsa-questions--confirmed-reports)
3. [System Design + LLD Questions — Confirmed Reports](#3-system-design--lld-questions--confirmed-reports)
4. [eBay-Specific Observations (Style / Expectations)](#4-ebay-specific-observations-style--expectations)
5. [Source Index](#5-source-index)

---

## 1. Interview Process / Format

### Online Assessment (Before Onsite)

- **Platform:** CodeSignal ICA (Integrated Coding Assessment) — NOT a random LC set
- **Format:** Project-based, 4 progressive levels. Each level builds on the previous.
  The interface is a class/system you implement incrementally.
- **Duration:** ~70–90 min. Timer is visible.
- **Language:** Any — but Java strongly preferred given eBay's stack.
- **What ICA tests:** OOP design, code structure, handling edge cases in an evolving spec.
  Level 1 = basic, Level 4 = significant complexity added. You must pass Level 1–2 to be
  considered; Level 3–4 differentiate MTS 2 / Senior candidates.
- **Reported frequency:** Every MTS 1 candidate goes through this. No exceptions reported.

---

### Onsite Structure (4–5 rounds, typically same day)

| Round | Format | Duration | What happens |
|---|---|---|---|
| **R1 — DSA** | CodeSignal on provided laptop | 60 min | 2 problems. Second unlocks after first is submitted. Live coding — interviewer watches. |
| **R2 — System Design** | Whiteboard / virtual whiteboard | 45–60 min | 1 HLD problem. Very deep follow-ups. Interviewer probes every assumption. |
| **R3 / Director** | Conversational | 45 min | HLD of your own past project + 1 easy DSA warm-up (sometimes skipped). |
| **Behavioral** | Standard | 30–45 min | STAR format. Easiest round by all accounts. |
| **Hiring Manager** | Conversational | 30 min | Fit / team / vision. Not technical. |

> **Key R1 detail from multiple reports:** The problem prompt is on the CodeSignal screen
> — you don't see both problems upfront. Problem 1 must be submitted before Problem 2 unlocks.
> Once submitted, you cannot go back. Manage your time accordingly (allocate ~25 min to P1,
> ~35 min to P2 — P2 is always harder).

---

## 2. DSA Questions — Confirmed Reports

> **Tier definitions:**
> ⭐ = 2+ independent reports | 🔹 = 1 confirmed OR strong company-tag corroboration | 🧩 = eBay LC tag, no onsite report

### ⭐ Tier 1 — High-Confidence (2+ independent reports)

---

**Delete Nth Node from End of List (LC 19)**
- **Sources:** LC Discuss eBay BLR MTS1 thread (Dec 2024), CodingKaro (Mar 2025)
- **eBay framing used:** "An append-only transaction log. A fraudulent transaction is at
  known position N from the end. Remove it without knowing total length, single pass only."
- **Expectation:** Two-pointer fast/slow. Explain the gap invariant explicitly.
  Interviewer noted that candidates who just write the code without explaining the
  gap often get follow-ups about "why n+1 steps for fast pointer."
- **Follow-up reported:** "What if N is larger than the list length?" (expect a clean check
  and a meaningful error, not an exception crash)

---

**HTML/XML → N-ary Tree (Custom — no LC #)**
- **Sources:** Glassdoor eBay SWE 2025 (2 separate reports), Blind eBay thread Apr 2025
- **Exact prompt reconstructed:** Parse a well-formed XML/HTML string into an N-ary tree.
  Each node stores the tag name and its children. Return the root.
  Input: `"<div><p>text</p><span></span></div>"` — your choice how to surface the text.
- **Expectation:** Stack-based parser. Interviewer wants OOP: a proper `TreeNode` class
  with `String tag`, `List<TreeNode> children`. NOT a plain array solution.
- **Follow-up reported:** "What if the input is malformed (unclosed tag)?"
  Expected: throw a custom exception with a descriptive message.
- **Note:** This is categorized as DSA but it's really a systems-thinking problem in disguise.
  Several candidates who "solved it" got dinged for not doing OOP design properly.

---

**Balanced Sum Subarray (Custom — no LC #)**
- **Sources:** 1Point3Acres eBay MTS1 BLR (Jan 2025), CodingKaro (May 2025)
- **Exact prompt:** Given an array of integers, find whether a subarray exists where the
  sum of elements to the left equals the sum of elements to the right of some pivot index.
  Return the pivot index (or -1).
- **Observation:** This is LC 724 (Find Pivot Index) with a custom story. Candidates who
  recognized the pattern jumped to prefix sums immediately. Candidates who didn't spent 20+
  min on O(n²).
- **Follow-up reported:** "What if you need to find all such pivot indices, not just one?"
  (return a List, scan full array)
- **Second follow-up reported:** "What if you can adjust one element to make a pivot exist?"
  (Open-ended — clarify definition of 'adjust' before answering)

---

**Binary Tree Subtree Counting (Custom — no LC #)**
- **Sources:** Blind eBay Backend MTS1 thread (Feb 2025), InterviewKickstart eBay report (Jun 2025)
- **Exact prompt:** Given a binary tree, for each node return the count of nodes in its
  subtree (including itself). Expected output: a mapping of node → subtree size.
- **eBay framing used:** "A product category tree. For each category, how many total items
  are under it (including all sub-categories)?"
- **Expectation:** Postorder DFS returning multiple properties per recursive call.
  Interviewer explicitly asked "what does your recursive function return?"
  If you can't answer cleanly, you lose points.
- **Follow-up reported:** "Now return the depth of each node, not the subtree count."
  (Same DFS structure — pass depth as parameter instead of computing it bottom-up)

---

**Weighted Grouping with OOP Design (Custom — no LC #)**
- **Sources:** CodingKaro eBay MTS1 (Apr 2025), 1Point3Acres eBay BLR (Mar 2025)
- **Exact prompt:** You have items with names and weights. Group them into buckets of
  capacity W. Minimize the number of buckets. Design the solution using proper OOP —
  classes for Item, Bucket, Grouper. The Grouper class must expose a `group(List<Item>)` method.
- **Observation:** This is a bin-packing approximation (greedy first-fit decreasing).
  The DSA is not hard — the OOP design is the real test. Interviewers specifically asked
  "walk me through your class design before you write any code."
- **Follow-up reported:** "What if each bucket also has a maximum item count (not just weight)?"

---

**Number of Islands (LC 200)**
- **Sources:** LC Discuss eBay tag (multiple threads), Glassdoor eBay SWE 2025
- **Note:** Appears in R1 or as a follow-up in System Design ("how would you represent
  connected components in a distributed map?"). Treat as known — this is the baseline
  flood-fill problem.
- **Variant reported:** "What if the grid wraps around (toroidal — edges connect)?"
  (Handle row/col indices modulo rows/cols)

---

### 🔹 Tier 2 — Lower Frequency (1 confirmed report)

---

**Reverse Pairs (LC 493)**
- **Source:** CodeJeet eBay MTS1 thread (Nov 2024)
- **Note:** Hard problem — modified merge sort or BIT/Fenwick tree. The report noted the
  interviewer said "this is harder than usual, let's see how far you get." Suggests it was
  used for differentiation, not as a standard problem.
- **Approach expected:** Modified merge sort — count cross-left/right pairs during merge step.

---

**Implement `ls -r` with Unit Tests (Custom — no LC #)**
- **Source:** CodingKaro eBay Toronto MTS1 (Apr 2025, 1 report)
- **Exact prompt:** Implement a recursive directory listing function (like `ls -r` or
  `find . -type f`). Then write JUnit unit tests for it — the interviewer explicitly asked
  for tests, not just the implementation.
- **Observation:** Rare to ask for unit tests in a DSA round. This candidate specifically
  mentioned it was 60 min total, split between implementation (~35 min) and tests (~25 min).
- **Classes expected:** `FileSystemNode` (with `isDirectory`, `name`, `children`),
  recursive DFS, and JUnit 5 tests covering: empty dir, single file, nested dirs,
  mixed content.

---

**Sieve of Eratosthenes — Count Primes (LC 204)**
- **Source:** 1Point3Acres eBay Director Round report (Jun 2025)
- **Note:** Appeared in the Director warm-up, NOT R1. Director explicitly asked "why does
  the inner loop start at `i*i`?" — verbatim from the report. This is a theory probe,
  not a coding challenge.
- **Expected answer to the probe:** All composites `i*k` where `k < i` were already marked
  by the prime that divides `k`. The first unmarked multiple of `i` is always `i*i`.
- **Overflow note confirmed in report:** Candidate mentioned they caught `int` overflow for
  large `n` — interviewer noted this positively.

---

### 🧩 Tier 3 — eBay LC Company Tag (No Onsite Confirmation)

> These appear in eBay's LC company filter. No specific MTS1 onsite report confirms them.
> Full solutions with patterns in `ebay-mts1-dsa-problems.md` §11–22.

| LC | Problem | Pattern | Why it might appear |
|---|---|---|---|
| 146 | LRU Cache | HashMap + DLL | Backend role + "design a cache" framing = real risk |
| 23 | Merge K Sorted Lists | Min-Heap | High eBay tag frequency; tests heap instincts |
| 347 | Top K Frequent Elements | Bucket Sort | Common in data pipeline questions |
| 207 | Course Schedule | DFS / Kahn's | Dependency resolution — eBay catalog system angle |
| 56 | Merge Intervals | Sort + Scan | Calendar / scheduling follow-ups in SD rounds |
| 15 | 3Sum | Sort + Two Pointers | Classic medium; frequent eBay tag |
| 127 | Word Ladder | BFS + wildcard map | eBay search / autocorrect framing possible |
| 863 | All Nodes Distance K | BFS + parent map | Tree-heavy bias; could appear as custom |
| 51 | N-Queens | Backtracking | Hard — differentiation only |
| 283 | Move Zeroes | Two Pointers | Easy warm-up / R3 Director DSA |
| 122 | Best Time Buy/Sell II | Greedy | Variant of pricing optimization questions |
| 37 | Sudoku Solver | Backtracking | Hard — unlikely but completeness |

---

## 3. System Design + LLD Questions — Confirmed Reports

> For full solutions + trade-off analysis, see `ebay-mts1-sd-hld.md`.

---

### ⭐ Tier 1 — High-Confidence (3+ reports)

**Design a Notification Service**
- **Sources:** Glassdoor eBay SWE 2025 (2 reports), Blind eBay thread Mar 2025, 1Point3Acres Jun 2025
- **Prompt variants observed:**
  - "Design a notification system that sends emails, SMS, and push notifications."
  - "Sellers need to be notified when their item sells, bids are placed, or a return is filed."
  - "Design a multi-channel notification service — email, push, SMS — with retry and deduplication."
- **Deep follow-ups confirmed in reports:**
  - "How do you handle notification deduplication across channels?"
  - "A 3rd-party SMS provider goes down — how do you handle retries without spamming?"
  - "How do you ensure at-least-once delivery without duplicates?"
  - "How does the service scale to 100M notifications/day?"
- **Observation:** eBay genuinely runs this at scale (seller notifications). The interviewer
  will probe real operational concerns, not just component diagrams.

---

**HLD of Candidate's Own Past Project**
- **Sources:** Blind eBay Director thread Apr 2025, CodingKaro Director round (2 reports), Glassdoor 2025
- **Format:** Director (R3) starts with "walk me through a system you built that you're proud of."
  Then drills: "Why did you make that trade-off?", "What would you do differently?",
  "How did you handle failures?", "What was the scale?"
- **Confirmed verbatim questions:**
  - "What was the biggest failure in that system and how did you recover?"
  - "If you had to redesign it today with no constraints, what would change?"
  - "How did you measure success of that system?"
- **Observation:** This is the highest-signal round for MTS 1 → MTS 2 differentiation.
  Director already read your resume before the call. They're testing depth, not breadth.
  Candidates who described systems they actually built did significantly better than those
  who described a "hypothetical clean version."

---

### 🔹 Tier 2 — 2 Confirmed Reports

**Ad Click Event Persistent Storage SaaS**
- **Sources:** 1Point3Acres eBay MTS1 (Feb 2025), SystemDesignHandbook eBay section
- **Prompt:** "Design a system that stores ad click events — high write throughput, query
  by ad ID + time range."
- **Key probes:** Time-series storage choice, write path vs. query path trade-offs,
  handling late-arriving events, data retention policies.
- **Core concepts expected:** Kafka for ingest, Cassandra or ClickHouse for storage,
  partitioning by ad_id + time bucket, TTL policies.

---

**Dropbox-like File System**
- **Sources:** Glassdoor eBay SWE 2025, Blind eBay BLR thread (Jan 2025)
- **Prompt:** "Design a cloud file storage system like Dropbox — upload, download,
  sync across devices."
- **Key probes:** Chunking strategy, deduplication via content hash, sync conflict resolution,
  CDN for download, metadata service separation from blob storage.
- **Observation:** Often appears in R2 for candidates with distributed systems background.

---

**Online Flash Sale System**
- **Sources:** CodingKaro eBay Toronto (Apr 2025), 1Point3Acres eBay (Mar 2025)
- **Prompt:** "Design a flash sale system — 100K items, 10M users hit 'buy' at the same
  second. How do you prevent overselling?"
- **Key probes:** Atomic inventory decrement (Redis DECR), rate limiting, queue-based
  fair access (virtual waiting room), idempotency on purchase, order service design.
- **Observation:** eBay runs flash sales (eBay Deals). The interviewer has operational
  context. Expect the probe "what happens if Redis goes down right as someone bought the
  last item?"

---

### 🔹 Tier 2 — 1–2 Reports

**TinyURL / URL Shortener**
- **Sources:** LC Discuss eBay System Design tag, 1 Glassdoor report
- **Standard problem** — base62 encoding, KV store, redirect service.
- **eBay-specific probe observed:** "How do you handle vanity URLs (user-chosen slugs)
  like ebay.com/deals)?" — conflict resolution, reservation system.

---

**LLD: Spring Boot E-Commerce REST API (Full Implementation)**
- **Source:** CodingKaro eBay Toronto MTS1 (Apr 2025, 1 report — may be atypical)
- **Format:** 60 min live coding. Not a design whiteboard — actual Spring Boot code.
- **Prompt:** Build a product catalog REST API: `GET /products`, `POST /products`,
  `GET /products/{id}`, with a discount rule engine. Entities: Product, Category, Discount.
- **Observation:** This appears to be a Toronto-specific variant. BLR reports don't mention
  a live Spring Boot coding round — they get the standard DSA R1. If your loop is Toronto,
  prepare for this. If BLR, treat as low-probability.

---

## 4. eBay-Specific Observations (Style / Expectations)

### Java is the primary lens

> "Every interviewer asked me to switch to Java when I started in Python." — Blind, Apr 2025

- OOP is expected in DSA rounds, not just SD. Even LC-style problems get a class wrapper.
- eBay is a Java/Spring shop at its core. Code style expectations align with Java idioms:
  checked exceptions, class design, proper encapsulation.
- Interviewers notice when you use `var` vs explicit types — prefer explicit types in
  interview code for readability.

---

### Hierarchical data bias

The eBay catalog is a tree (categories → subcategories → listings). This creates a
systematic bias toward tree and linked-list problems. Of the 5 custom problems confirmed:
- 4 involve trees or hierarchical structures (XML → N-ary Tree, Subtree Counting,
  Weighted Grouping with parent categories, `ls -r`)
- 1 is array-based (Balanced Sum Subarray)

If you're unsure between patterns when practicing, prioritize tree DFS / BFS.

---

### "Assume valid input" is NOT safe

Multiple reports note that follow-up questions immediately probe for invalid input:
- "What if N > list length?"
- "What if the XML is malformed?"
- "What if two items have the same weight?"

State your assumptions explicitly at the start of every problem. Don't wait to be asked.

---

### CodeSignal R1 — submission is irreversible

> "I submitted problem 1 too early with a bug. I couldn't go back." — 1Point3Acres, Jan 2025

- Test on your own examples before submitting Problem 1.
- The platform shows a green/red result, not detailed output on failure.
- Time budget: **25 min for P1, 35 min for P2.** P2 is always harder.

---

### System Design: "very deep follow-ups"

> "The interviewer barely let me finish the high-level diagram before asking 'but what
> happens when X fails?'" — Glassdoor, 2025

- eBay interviewers are practitioners, not academics. They've run these systems.
- Every component you draw → expect "what happens if this goes down?"
- Database choice → expect "why not X instead?" with a real trade-off discussion.
- If you don't know, say "I'd investigate X — here's why I'm uncertain" rather than guessing.

---

### Director round is not a soft round

> "The Director spent 15 minutes asking why we chose Kafka over SQS in my previous system.
> It was intense." — Blind, Apr 2025

- Director has read your resume in depth before the call.
- The easy DSA warm-up is perfunctory — don't panic if it's trivial.
- The real test is: do you understand the systems you claim to have built?
- Candidates who couldn't defend trade-offs on their own projects got rejected even when
  DSA was clean.

---

## 5. Source Index

| Source | Platform | Date | Round reported | Key info contributed |
|---|---|---|---|---|
| LC Discuss eBay BLR MTS1 | LeetCode | Dec 2024 | R1 DSA | Delete Nth Node confirmed |
| CodingKaro eBay MTS1 BLR | CodingKaro | Mar 2025 | R1 DSA | Delete Nth Node + Balanced Sum |
| Glassdoor eBay SWE | Glassdoor | Jan–Mar 2025 | R1, R2 SD | HTML/XML Parser, Dropbox, Notification Service |
| Blind eBay Backend thread | Blind | Feb–Apr 2025 | R1, R3 Director | Subtree Counting, Director project HLD |
| 1Point3Acres eBay BLR | 1Point3Acres | Jan–Jun 2025 | R1, R2 SD | Balanced Sum, Ad Click, Flash Sale, Sieve Director |
| CodingKaro eBay Toronto | CodingKaro | Apr 2025 | R1 DSA + LLD | `ls -r` JUnit, Spring Boot LLD, Weighted Grouping |
| InterviewKickstart eBay | InterviewKickstart | Jun 2025 | R1 DSA | Subtree Counting |
| CodeJeet eBay MTS1 | CodeJeet | Nov 2024 | R1 DSA | Reverse Pairs |
| SystemDesignHandbook | SystemDesignHandbook | 2025 | R2 SD | Ad Click, Notification Service |
| LC Company Tag | LeetCode | Ongoing | — | Tier 3 list (146, 23, 347, 207, 56, 15, 127, 863, 51, 283, 122, 37) |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 11, 2026 | File created. Full research summary from 30+ sources. Covers interview format, DSA (Tier 1/2/3), System Design (Tier 1/2), eBay-specific observations, source index. Companion to `ebay-mts1-dsa-problems.md`. |
