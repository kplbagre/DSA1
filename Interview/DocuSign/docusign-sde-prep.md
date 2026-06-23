# DocuSign — Software Engineer, Commerce Backend
## Battle-Ready Prep File

> **Role:** Software Engineer, Commerce Backend
> **Rounds:** R1 (DSA — 60 min) → R2 (System Design — 60 min) → R3 (Hiring Manager)
> **Platform:** HackerRank — no autocomplete, no AI tools. Practice there.
> **Difficulty:** Medium LeetCode. DocuSign explicitly says medium-level in their own guide.
> **Full prep detail:** `Interview/DocuSign/DOCUSIGN_PREP.md`

---

## ⚡ 4-Step Interview Ritual — Memorize This

```
STEP 1 (2 min) — CLARIFY BEFORE CODING
  "Can values be negative? Can input be empty? Any size constraints?"
  Never touch the keyboard until you've asked at least 2 questions.

STEP 2 (3 min) — THINK ALOUD, TWO APPROACHES
  State brute force + its complexity first. Then state optimal.
  "Naive O(n²) would be... I can optimize to O(n log n) by..."

STEP 3 (35 min) — CODE CLEANLY
  Meaningful variable names. No i/j/k unless truly obvious.
  Handle empty input, single element, duplicate edge cases explicitly.

STEP 4 (10 min) — TEST AND VERIFY
  Walk through 2 test cases: one normal, one edge.
  Find and fix bugs YOURSELF before the interviewer sees them — this is a plus.
```

---

## R1 — 12 Target Problems (medium only)

For each: the pattern, where your notes live, the key insight you MUST state aloud.

---

### 1. LC 49 — Group Anagrams ⭐ | HashMap

**Pattern:** Canonical key (sorted string) → HashMap of lists

**Notes:** `DSA/Interview/Playbooks/arrays-and-hashing.md` Pattern 2 + `DSA/Patterns/group-anagrams-problem.md` (full deep dive)

**Key insight to state aloud:** *"Sort each word's characters — anagrams share the same sorted form. Use that as the HashMap key."*

**Complexity:** O(n × k log k) time where k = max word length, O(n×k) space

---

### 2. LC 3 — Longest Substring Without Repeating ⭐ | Sliding Window

**Pattern:** Sliding window with HashSet (shrink from left when duplicate found)

**Notes:** `DSA/Interview/Playbooks/two-pointers-and-sliding-window.md` Pattern 3

**Key insight to state aloud:** *"Expand right. When a duplicate enters the window, shrink from left until it's gone. Track max window size."*

**Complexity:** O(n) time, O(min(n, charset)) space

---

### 3. LC 200 — Number of Islands ⭐ | BFS/DFS

**Pattern:** Grid BFS/DFS — sink visited land cells to '0'

**Notes:** `DSA/Interview/Playbooks/graphs.md` — canonical walkthrough (full code)

**Key insight to state aloud:** *"Each BFS/DFS call from an unvisited '1' explores one island. Count how many times you trigger it."*

**Complexity:** O(m×n) time and space

---

### 4. LC 56 — Merge Intervals ⭐ | Intervals

**Pattern:** Sort by start, then scan — merge if current overlaps last

**Notes:** `DSA/Interview/Playbooks/intervals.md` Pattern 1

**Key insight to state aloud:** *"Sort by start time. For each interval: if it overlaps the last in result (current.start <= last.end), extend last.end. Otherwise add new."*

**Complexity:** O(n log n) time (sort dominates), O(n) space

---

### 5. LC 146 — LRU Cache ⭐⭐ | Design (HashMap + DoublyLinkedList)

**Pattern:** HashMap for O(1) lookup + DoublyLinkedList to maintain recency order

**Notes:** `DSA/DeepDive/hybrid-design-problems.md` Problem 1 (260 lines, full deep dive)
Quick ref: `DSA/Interview/QuickRef/hybrid-problems-cheatsheet.md`

**Key insight to state aloud:** *"HashMap gives O(1) get/put by key. But we also need to evict the Least Recently Used — we need to know which element is oldest. A DoublyLinkedList lets us move an accessed node to the front in O(1) and evict from the tail in O(1). HashMap stores key → node so we can jump directly to the node in the list."*

**Do NOT say:** "I'll use LinkedHashMap" — unless they say it's fine. They want to see you know the underlying mechanism.

**LinkedHashMap shortcut (if allowed):**
```java
new LinkedHashMap<>(capacity, 0.75f, true) {
    protected boolean removeEldestEntry(Map.Entry eldest) {
        return size() > capacity;
    }
};
```
`true` = accessOrder mode (moves accessed entry to end). Override `removeEldestEntry` for eviction.

**Complexity:** O(1) get, O(1) put, O(capacity) space

---

### 6. LC 981 — Time Based Key-Value Store | Binary Search + HashMap

**Pattern:** HashMap + binary search on sorted timestamps (or HashMap + TreeMap with `floorKey()`)

**Notes:** `DSA/Interview/Playbooks/binary-search.md` — newly added problem bank entry

**Key insight to state aloud:** *"Timestamps arrive in increasing order so each key's list is always sorted. `get()` needs the largest timestamp ≤ target — that's bisect-right - 1. TreeMap.floorKey() does this in one call."*

**Preferred implementation (TreeMap):**
```java
Map<String, TreeMap<Integer, String>> map = new HashMap<>();
// set: map.computeIfAbsent(key, k -> new TreeMap<>()).put(timestamp, value);
// get: Integer floor = map.get(key).floorKey(timestamp);
//      return floor == null ? "" : map.get(key).get(floor);
```

**Complexity:** O(1) set, O(log n) get, O(n) space

---

### 7. LC 155 — Min Stack | Stack Design

**Pattern:** Two parallel stacks — main stack + min-tracker stack

**Notes:** `DSA/Interview/Playbooks/stacks-and-queues.md` Pattern 5

**Key insight to state aloud:** *"For each push, also push onto the min stack: `Math.min(x, minStack.peek())`. Min is always `minStack.peek()` — no scan needed."*

**Complexity:** O(1) all operations, O(n) space

---

### 8. LC 238 — Product of Array Except Self | Array (Prefix Product)

**Pattern:** Two-pass prefix/suffix product — left pass then right pass

**Notes:** `DSA/Interview/Playbooks/arrays-and-hashing.md` problem bank

**Key insight to state aloud:** *"No division allowed. For each index, we need product of everything LEFT × product of everything RIGHT. Left pass builds prefix products in result[]. Right pass multiplies running suffix product into result[]."*

**Complexity:** O(n) time, O(1) extra space (output array doesn't count)

---

### 9. LC 79 — Word Search | DFS + Backtracking

**Pattern:** DFS with in-place marking (board[r][c] = '#' to mark visited, restore after)

**Notes:** `DSA/Interview/Playbooks/backtracking.md`

**Key insight to state aloud:** *"Try DFS from every cell. Mark visited by temporarily replacing the character (board[r][c] = '#'). Restore after backtrack. This avoids a separate visited array."*

**Complexity:** O(m × n × 4^L) time where L = word length, O(L) space (recursion stack)

---

### 10. LC 347 — Top K Frequent Elements | Heap

**Pattern:** Frequency map + min-heap of size k (or bucket sort)

**Notes:** `DSA/Interview/Playbooks/heaps.md`

**Key insight to state aloud:** *"Count frequencies with HashMap. Maintain a min-heap of size k — if heap size > k, remove the minimum. At the end, heap contains the k most frequent."*

**Follow-up:** "O(n) solution?" → Bucket sort: bucket[freq] = list of elements with that freq. Iterate from freq=n down to 1.

**Complexity:** O(n log k) heap approach, O(n) bucket sort

---

### 11. LC 994 — Rotting Oranges | Multi-Source BFS

**Pattern:** BFS from all rotten oranges simultaneously (multi-source BFS)

**Notes:** `DSA/Interview/Playbooks/graphs.md` + `DSA/Patterns/rotting-oranges-problem.md` (full deep dive)

**Key insight to state aloud:** *"All rotten oranges spread simultaneously — add ALL of them to the queue before starting BFS. Each BFS level = 1 minute. Count levels. Check if any fresh oranges remain."*

**Complexity:** O(m×n) time and space

---

### 12. LC 207 — Course Schedule | Topological Sort

**Pattern:** Kahn's algorithm (BFS-based topo sort) — detect cycle via indegrees

**Notes:** `DSA/Interview/Playbooks/graphs.md` Pattern 2

**Key insight to state aloud:** *"Build adjacency list + indegree count. Start BFS with all nodes of indegree 0. Each poll: decrement neighbors' indegrees. If indegree hits 0, enqueue. If processed count < n, cycle exists."*

**Complexity:** O(V + E) time and space

---

## R1 — Coverage Check at a Glance

| Problem | Status | Notes location |
|---|---|---|
| LC 49 Group Anagrams | ✅ Full deep dive | `Patterns/group-anagrams-problem.md` |
| LC 3 Longest Substring | ✅ Covered | `Playbooks/two-pointers-and-sliding-window.md` |
| LC 200 Number of Islands | ✅ Canonical walkthrough | `Playbooks/graphs.md` |
| LC 56 Merge Intervals | ✅ Pattern 1 | `Playbooks/intervals.md` |
| LC 146 LRU Cache | ✅ 260 lines deep dive | `DeepDive/hybrid-design-problems.md` |
| LC 981 Time Based KV Store | ✅ Added June 2026 | `Playbooks/binary-search.md` |
| LC 155 Min Stack | ✅ Pattern 5 | `Playbooks/stacks-and-queues.md` |
| LC 238 Product Except Self | ✅ Problem bank | `Playbooks/arrays-and-hashing.md` |
| LC 79 Word Search | ✅ Covered | `Playbooks/backtracking.md` |
| LC 347 Top K Frequent | ✅ Covered | `Playbooks/heaps.md` |
| LC 994 Rotting Oranges | ✅ Full deep dive | `Patterns/rotting-oranges-problem.md` |
| LC 207 Course Schedule | ✅ Pattern 2 | `Playbooks/graphs.md` |

**All 12 target problems covered.**

---

## R2 — System Design Topics

Full designs (key decisions, trade-offs, code snippets) are in:
`Interview/DocuSign/DOCUSIGN_PREP.md`

| Topic | Type | Section in DOCUSIGN_PREP.md |
|---|---|---|
| URL Shortener | Infrastructure | "Infrastructure Design 1" |
| Facebook Chat / Messaging | Infrastructure | "Infrastructure Design 2" |
| Video CDN Distribution | Infrastructure | "Infrastructure Design 3" |
| Subscription + Billing API | Product Architecture | "Product Architecture 1" ← most likely for this role |
| Feed API | Product Architecture | "Product Architecture 2" |
| Rate Limiter | Commerce-specific | "Rate Limiter" |

**⚠️ Confirm with recruiter before R2:** Is it Infrastructure Design or Product Architecture variant? If unsure, prep the Subscription Billing API (most likely for Commerce Backend role).

### R2 Opening Template (use for every question):
```
1. Clarify requirements (2–3 min)
   → Scale? Latency SLA? Read-heavy or write-heavy? Consistency requirements?

2. Back-of-envelope estimate (1–2 min)
   → QPS, storage, bandwidth

3. High-level boxes first (5 min)
   → client → API → services → storage

4. Deep dive on hardest component (20 min)
   → This is where senior signal is. Lead the conversation.

5. Name the trade-offs (5 min)
   → "The trade-off here is X. We accepted it because Y."
```

---

## Your MCSE Bridge (use in both R1 and R2)

**30-second pitch:**
> "I own the promise and sourcing engine at Walmart — decides which warehouse ships an order and what delivery date the customer sees. 700K requests per minute, sub-100ms p95. The hard problems: concurrency at fan-out scale (50–100 parallel evaluations per request via CompletableFuture), eventually-consistent reference data (16 Hollow caches), multi-market config isolation."

**Bridge to Commerce Backend:**
> "The most directly relevant piece: inventory reservation pattern. When MCSE selects a fulfilment node, it optimistically reserves capacity — if reservation fails due to concurrent modification, retry with next-best warehouse. Same problem as concurrent subscription seat purchases or concurrent invoice generation."

---

## Pre-R1 Checklist

- [ ] HackerRank account set up, screen sharing tested
- [ ] All 12 problems practiced on HackerRank (not LeetCode UI)
- [ ] Can state time/space complexity for each without hesitation
- [ ] Practiced vocalizing thought process on 3 problems out loud
- [ ] LRU Cache — can write DoublyLinkedList + HashMap from memory in 20 min
- [ ] LC 981 — `TreeMap.floorKey()` syntax memorized
- [ ] LC 238 — two-pass prefix/suffix product explained without division
- [ ] Camera on. No virtual background. Screen sharing tested before call.
- [ ] **No AI tools** during interview — no ChatGPT, no Cluely. Explicitly prohibited.

## Pre-R2 Checklist

- [ ] Confirmed variant with recruiter (Infrastructure or Product Architecture)
- [ ] Subscription billing API — state machine, idempotency, proration, Kafka fanout
- [ ] Rate limiter — token bucket vs sliding window log comparison ready
- [ ] URL shortener — 301 vs 302 trade-off, Base62, Redis TTL
- [ ] MCSE 30-second pitch smooth
- [ ] SOLID principles — can name all 5 and apply to a design on the fly

---

## 5-Day Execution Plan

| Day | DSA (1.5h) | System Design (1.5h) | Other (1h) |
|---|---|---|---|
| Day 1 (today) | LC 49, LC 3, LC 200 on HackerRank | URL Shortener | MCSE pitch out loud × 3 |
| Day 2 | LC 56, LC 146 (LRU), LC 981 | Messaging / Chat | 1 STAR story for behavioral |
| Day 3 | LC 155, LC 238, LC 79 | Subscription Billing API | Glassdoor review pass |
| Day 4 | LC 347, LC 994, LC 207 | Rate Limiter + Feed API | HackerRank screen share test |
| Day 5 | Mock: 2 random problems, 50 min timer | Full R2 run-through out loud | Final checklist |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | **File created.** DocuSign SDE Commerce Backend prep. R1: 12 problems mapped to knowledge base + key insights. R2: 6 system design topics cross-referenced to DOCUSIGN_PREP.md. All 12 R1 problems confirmed covered (LRU + LC 981 were gap-filled same day). |
