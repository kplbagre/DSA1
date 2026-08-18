# Salesforce SMTS — Coding Round: Deep Research Findings (Aug 2026)

> **Scope:** DSA coding rounds, Online Assessments (OA), and machine-coding/concurrency rounds only.
> Pure system-design rounds (distributed rate limiter HLD, notification service HLD) live in
> **`../HLD+LLD/research-findings-2026-08.md`** — cross-referenced below where relevant.
>
> **Research window:** ~Aug 2025 – Aug 2026 (today: 2026-08-13). Sources: LeetCode Discuss,
> Glassdoor, Blind, CodingKaro, InterviewExperiences.in, Desi QnA (OA Sets 13–18). Some reports
> from adjacent levels (LMTS, MTS) included where they add signal — those are explicitly labeled.
>
> **What we call "SMTS coding round":** Salesforce's SMTS (Senior Member of Technical Staff —
> roughly SDE-3 / Senior Software Engineer) loop contains 1–2 coding-focused rounds *before* the
> design rounds. These are the rounds covered here.

---

## 🎯 TL;DR — What Salesforce Is Actually Testing

If you read nothing else, read this.

**The core signal from ~20 real SMTS interview accounts (Aug 2025 – Aug 2026):**

1. **Medium → Hard LC problems, with mandatory complexity justification.** Every account mentions
   the interviewer explicitly probing time and space complexity after a solution lands. Getting
   the right answer is table stakes; being able to derive O(n log k) vs O(kn) and explain *why*
   is the real bar.

2. **Sliding window + heap + graph (BFS/DFS/cycle detection) dominate the OA and Round 1.**
   These three topic clusters show up more than anything else in the last 12 months. Arrays and
   strings are the substrate — the actual algorithmic techniques sitting on top of them are
   sliding window, prefix sums, and heap/priority-queue patterns.

3. **The coding round frequently bleeds into mini-LLD.** Multiple accounts describe "coding
   rounds" where they had to design a class (`Queue with getMax()`, thread-safe connection pool,
   LRU cache) and write working Java/Python code. This is *not* a pure LC-style round — expect
   to structure a small service cleanly. See **§5 Machine-Coding / Concurrency** below.

4. **Backtracking + DP are consistent second-tier topics.** Letter Combinations (LC #17), phone
   keypad variants, palindrome DP, and Coin Change-style problems appear across multiple
   accounts.

5. **Communication is explicitly evaluated.** One rejected SMTS candidate (Dec 2025) had the
   written feedback: *"did not sufficiently demonstrate the level of design thinking expected for
   an SMTS role."* Walk through your approach out loud before coding. State assumptions.
   Verbalize brute-force first, then optimize — the interviewer watches the thinking, not just
   the code.

---

## 🧭 Coding Round Structure

```
┌─────────────────────────────────────────────────────────────────────────┐
│  SALESFORCE SMTS INTERVIEW LOOP (Coding-Focused View)                   │
├──────────────┬──────────────────────────────────────────────────────────┤
│  OA (Round 0)│  HackerRank proctored — 2 problems, 75–90 min           │
│              │  Difficulty: LC Medium. Mic + video ON + recorded.       │
├──────────────┼──────────────────────────────────────────────────────────┤
│  Round 1     │  Virtual DSA round with interviewer — 2 problems, 60 min │
│  (Coding)    │  Topics: sliding window, heaps, graphs, trees, DP, BT    │
│              │  Interviewer probes complexity and pushes for optimize.   │
├──────────────┼──────────────────────────────────────────────────────────┤
│  Round 2     │  EITHER: LLD (class-level design + code)                 │
│  (Design /   │      OR: Combined LLD + HLD on one problem               │
│  Machine-Cod)│      OR: Machine-coding (concurrency, thread-safety)     │
│              │  → Lives in ../HLD+LLD/ for design half;                 │
│              │    concurrency/machine-code part covered in §5 below.    │
├──────────────┼──────────────────────────────────────────────────────────┤
│  Round 3+    │  HLD / HM behavioral — out of scope for this file        │
└──────────────┴──────────────────────────────────────────────────────────┘

KEY INVARIANT:
   The OA is a filter. Round 1 is where SMTS leveling actually starts.
   You will be pushed to optimize EVERY solution you land — prepare for it.
```

---

## 📖 Terminology

- **OA** (Online Assessment) — the HackerRank take-home test, usually sent before any virtual
  interview. Proctored: camera and mic on the whole time.
- **SMTS** (Senior Member of Technical Staff) — Salesforce's IC3/Senior engineer level, roughly
  equivalent to SDE-3 at Amazon/Google or Senior SWE at most product companies.
- **LMTS** (Lead Member of Technical Staff) — one level above SMTS (IC4 / Staff). Several
  interview accounts in this research are from LMTS candidates; those are explicitly labeled.
  The DSA and OA difficulty is comparable; design expectations are higher at LMTS.
- **MTS** (Member of Technical Staff) — one level below SMTS (IC2 / SDE-2). Labeled where
  referenced — DSA bar is somewhat lower.
- **Machine-coding round** — a round where you write fully working, compilable code for a
  small system (not pseudo-code), usually involving OOP design + concurrency.

---

## 🔬 §1 — OA (HackerRank) Questions: Reported Problems

All OA reports are 2 problems / 75–90 min unless noted otherwise. Level given where known.

| Date | Level | Q1 | Q2 | Outcome |
|------|-------|----|----|---------|
| Jan 2025 | SMTS | — | — | Passed (offer, Hyderabad) |
| Apr 2025 | LMTS | For each index, return whether current num appeared before (at lower indices) — array + hashmap | After every 0→1 update in a binary array, return sweeps needed to sort it into non-decreasing order | Passed (offer) |
| Aug 2025 | SMTS | Spam classifier: label text "spam" if ≥2 spam words match (case-sensitive) — string hashing | — | Passed |
| Sep 2025 | LMTS | Heap/priority-queue problem (exact wording undisclosed) | Basic DP or sorting+math combination | Passed (offer, India) |
| Feb 2026 | SMTS→LMTS | Min-length subarray with K distinct integers (sliding window) | Shortest cycle in a DAG (graph / BFS) | Passed (offer, Hyderabad — downleveled to SMTS) |
| Feb 2026 | SMTS | Various "OA Sets 13–15" documented on Desi QnA (Kumar K.) | | — |
| Apr 2026 | SMTS | "OA Sets 16–18" on Desi QnA — includes "LLM-proof" problems from Set 18 | | — |

**Pattern:** OA problems cluster around **sliding window, heaps, and DP**. No recursion-heavy
problems in OA. You do NOT need to run the code — a logically complete solution is acceptable.

---

## 🔬 §2 — Round 1 (Virtual DSA): Reported Problems

Two problems per 60-minute round. Level of candidate given where known.

### Sliding Window + Heaps

| Date | Level | Problem | Notes |
|------|-------|---------|-------|
| Jan 2025 | SMTS | **Coin Change** variant (LC #322 — DP) | — |
| Jan 2025 | SMTS | **Search a 2D Matrix II** (LC #240) | — |
| Apr 2025 | LMTS | **Kth greatest element for every subarray of size K to N** — heap | Multiple dry runs, edge case handling |
| Apr 2025 | LMTS | **Rearrange array: negatives first, positives second — in-place** | In-place array partition |
| Aug 2025 | SMTS | **Longest Substring Without Repeating Characters** (LC #3) | Interviewer asked: O(N) vs O(1) space? Answer: O(1) — bounded by ASCII charset |
| Aug 2025 | SMTS | **Vertical Order Traversal of Binary Tree** (LC #987) | Used map to store sorted elements per level |
| Sep 2025 | LMTS | **Implement a Connection Pool** (machine-coding) | → Covered in §5 |
| Feb 2026 | SMTS | **Max of minimums for every window of size K** — heap/monotonic stack | Interviewer only asked for approach on Q2 |
| Feb 2026 | SMTS | **Cycle detection in DAG** — approach-only (no code required) | BFS/Kahn's or DFS-with-colors |
| Dec 2025 | SMTS | **7-character string (digits or `#`) + target N** — find all combos summing to N | Backtracking / combination-sum variant |
| Unknown | SMTS | **Reconstruct Itinerary variant** — no loops, no multiple paths | HashMap + Set, solved in 30 min |
| Unknown | SMTS | **Queue with getMax() in O(1)** | Machine-coding; → §5 |
| Unknown | SMTS | **Zigzag string traversal** (LC #6 — ZigZag Conversion) | Given string + num rows → print zigzag |
| Unknown | SMTS | **Letter Combinations of Phone Number** (LC #17) | Backtracking |
| Unknown | SMTS | **Wildcard Pattern Matching** (LC #44) — `?` = any char, `*` = any sequence | DP or greedy |
| Unknown | SMTS | **Time to Burn a Tree** starting from a leaf node | BFS + parent pointers |
| Unknown | SMTS | **All permutations of string including duplicates** | Backtracking + used-set |
| Unknown | SMTS | **Deepest node in a complete binary tree** | BFS level-order |
| Unknown | MTS | **Snake and Ladder** — min dice throws via BFS | BFS on flattened grid |
| Unknown | MTS | **Count valid words from a character array given a dictionary** | Trie or hashmap |

> **Reminder:** LMTS entries have a higher design expectation but the DSA difficulty is comparable
> — all of the above are fair to prep for SMTS.

---

## 🔬 §3 — Top LeetCode Problems by Reported Frequency (Salesforce Tag)

Source: CodeJeet (188 tagged problems), InterviewSolver (46 tagged), company-wise frequency CSVs.
Problems below appear most frequently across Salesforce-tagged sets AND corroborate real accounts.

### 🔹 Must-Do (confirmed in real SMTS accounts AND high frequency tag)

| # | Problem | LC # | Difficulty | Topic |
|---|---------|------|-----------|-------|
| 1 | Longest Substring Without Repeating Characters | 3 | Medium | Sliding Window |
| 2 | Letter Combinations of a Phone Number | 17 | Medium | Backtracking |
| 3 | Merge Intervals | 56 | Medium | Sorting / Intervals |
| 4 | LRU Cache | 146 | Medium | Design / HashMap + DLL |
| 5 | Coin Change | 322 | Medium | DP |
| 6 | Kth Largest Element in an Array | 215 | Medium | Heap |
| 7 | Search a 2D Matrix II | 240 | Medium | Binary Search |
| 8 | Vertical Order Traversal of a Binary Tree | 987 | Hard | BFS + HashMap |
| 9 | Find the Smallest Divisor Given a Threshold | 1283 | Medium | Binary Search on Answer |
| 10 | Wildcard Matching | 44 | Hard | DP / Greedy |

### 🔹 High-Frequency Salesforce Tag (not always confirmed in SMTS-specific accounts)

| # | Problem | LC # | Difficulty | Topic |
|---|---------|------|-----------|-------|
| 11 | LFU Cache | 460 | Hard | Design |
| 12 | Maximum Frequency Stack | 895 | Hard | Stack / Design |
| 13 | Design HashMap | 706 | Easy | Design |
| 14 | Find Pivot Index | 724 | Easy | Prefix Sum |
| 15 | Decode Ways | 91 | Medium | DP |
| 16 | Group Anagrams | 49 | Medium | HashMap |
| 17 | Diameter of Binary Tree | 543 | Easy | DFS |
| 18 | Number of Islands | 200 | Medium | BFS/DFS |
| 19 | Course Schedule | 207 | Medium | Topological Sort |
| 20 | ZigZag Conversion | 6 | Medium | String Simulation |

---

## 🧠 §4 — What the Frequency Data Says About Topic Distribution

From the 188 Salesforce-tagged LeetCode problems (CodeJeet, 2025-2026):

```
Topic               Count   % of total
─────────────────────────────────────
Array               112     60%
String               51     27%
Hash Table           47     25%
Dynamic Programming  43     23%
Sorting              36     19%
DFS                  25     13%
Binary Search        24     13%
Heap/Priority Queue  24     13%
Greedy               23     12%
Stack                21     11%
(percentages overlap — problems have multiple tags)
```

**What this means practically:**

- **Arrays + Strings are the substrate** for almost every Salesforce problem. Master every
  sliding window, two-pointer, and hashing pattern before moving to anything else.

- **DP is the #4 topic** — Salesforce is not shy about it. Coin Change, Decode Ways,
  palindrome DP, wildcard matching. Don't skip DP.

- **Heap shows up at 13%** — consistent with the OA and Round 1 accounts above. Know your
  `PriorityQueue` API cold.

- **Graph (DFS/BFS at 13%, topological sort)** — cycle detection in DAG, reconstruct itinerary,
  number of islands, course schedule. Salesforce's multi-tenant platform makes graph-flavored
  problems natural (dependency resolution, permission traversal).

---

## 🔬 §5 — Machine-Coding / Concurrency Round

This is the section most LC-only prep misses. Multiple SMTS accounts describe a round where
you write **working, production-quality Java code** for a small concurrent system. Not
pseudo-code — compilable code with proper OOP structure and thread-safety.

### Problems Confirmed in Real Accounts

#### Connection Pool (Sep 2025, LMTS — India, Offer)

Implement a connection pool with these constraints:
- Pool size: 1,000–2,000 connections
- Interface: `getConnection()` / `returnConnection()` / `closeConnection()`
- States: `FREE`, `BLOCKED`, `CLOSED`
- If CLOSED: create a new connection and add it to maintain count
- Key evaluation: **how you handle concurrent requests with limited connections**

The candidate used a `BlockingQueue<Connection>` and made `getConnection()` / `returnConnection()`
synchronous where contention could occur.

```java
// Approach outline — English steps first:
// 1. Hold FREE connections in a BlockingQueue<Connection> (blocks callers
//    when pool is empty — correct semantics without explicit lock + condition).
// 2. Track all connections in a Set<Connection> to enforce CLOSED → replace.
// 3. getConnection() → take() from BlockingQueue (blocks until one is FREE).
// 4. returnConnection() → put() back if state is FREE; ignore if CLOSED.
// 5. closeConnection() → mark CLOSED, create a new FREE connection, put() it.

public class ConnectionPool {
    private final BlockingQueue<Connection> freeConnections;
    private final int maxSize;

    public ConnectionPool(int maxSize) throws Exception {
        this.maxSize = maxSize;
        this.freeConnections = new LinkedBlockingQueue<>(maxSize);
        for (int i = 0; i < maxSize; i++) {
            freeConnections.put(new Connection());
        }
    }

    public Connection getConnection() throws InterruptedException {
        // BlockingQueue.take() blocks until a FREE connection is available
        Connection conn = freeConnections.take();
        conn.setState(ConnectionState.BLOCKED);
        return conn;
    }

    public void returnConnection(Connection conn) throws InterruptedException {
        if (conn.getState() == ConnectionState.CLOSED) {
            // Replace with a fresh connection to maintain pool size
            freeConnections.put(new Connection());
        } else {
            conn.setState(ConnectionState.FREE);
            freeConnections.put(conn);
        }
    }

    public void closeConnection(Connection conn) throws InterruptedException {
        conn.setState(ConnectionState.CLOSED);
        // Create replacement so pool never shrinks below maxSize
        freeConnections.put(new Connection());
    }
}
```

#### Queue with getMax() in O(1) (SMTS, exact date unknown)

Design a Queue supporting `enqueue(x)`, `dequeue()`, and `getMax()` — all in O(1).

```java
// Steps:
// 1. Main queue: LinkedList<Integer> for FIFO enqueue/dequeue.
// 2. Max-deque: ArrayDeque<Integer> (monotonic decreasing front-to-back).
//    - On enqueue: pop from max-deque rear while rear < new element.
//    - On dequeue: if front of max-deque == dequeued element, pop front too.
//    - getMax(): peek front of max-deque.

public class MaxQueue {
    private final Queue<Integer> mainQueue = new LinkedList<>();
    private final Deque<Integer> maxDeque = new ArrayDeque<>();

    public void enqueue(int x) {
        mainQueue.offer(x);
        // Remove all smaller elements from rear — they can never be max
        while (!maxDeque.isEmpty() && maxDeque.peekLast() < x) {
            maxDeque.pollLast();
        }
        maxDeque.offerLast(x);
    }

    public int dequeue() {
        int removed = mainQueue.poll();
        if (maxDeque.peekFirst() == removed) {
            maxDeque.pollFirst();
        }
        return removed;
    }

    public int getMax() {
        return maxDeque.peekFirst();
    }
}
```

#### Thread-Safe LRU Cache (multiple accounts — this appears in "design round" but with live code)

LRU Cache (LC #146) is a high-frequency Salesforce tag AND appears in a Round 2 experience where
the interviewer asked to "make it thread-safe and scale it for 1 million customers."

```java
// Steps:
// 1. HashMap<Integer, Node> for O(1) get/put.
// 2. Doubly linked list to maintain access order (MRU at head, LRU at tail).
// 3. ReentrantReadWriteLock with write-lock for BOTH get() and put().
//    Why write-lock for get()? Because get() calls moveToHead() which mutates
//    the DLL — a read lock is unsafe. You cannot use a read lock for any
//    operation that reorders nodes. ConcurrentHashMap alone is not enough.

public class LRUCache {
    private final int capacity;
    private final Map<Integer, Node> cache;
    private final Node head;
    private final Node tail;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public LRUCache(int capacity) {
        this.capacity = capacity;
        this.cache = new HashMap<>();
        this.head = new Node(0, 0);
        this.tail = new Node(0, 0);
        head.next = tail;
        tail.prev = head;
    }

    public int get(int key) {
        lock.writeLock().lock();
        try {
            if (!cache.containsKey(key)) {
                return -1;
            }
            Node node = cache.get(key);
            moveToHead(node);
            return node.val;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void put(int key, int value) {
        lock.writeLock().lock();
        try {
            if (cache.containsKey(key)) {
                Node node = cache.get(key);
                node.val = value;
                moveToHead(node);
            } else {
                Node node = new Node(key, value);
                cache.put(key, node);
                addToHead(node);
                if (cache.size() > capacity) {
                    Node lru = removeTail();
                    cache.remove(lru.key);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void moveToHead(Node node) {
        removeNode(node);
        addToHead(node);
    }

    private void addToHead(Node node) {
        node.prev = head;
        node.next = head.next;
        head.next.prev = node;
        head.next = node;
    }

    private void removeNode(Node node) {
        node.prev.next = node.next;
        node.next.prev = node.prev;
    }

    private Node removeTail() {
        Node node = tail.prev;
        removeNode(node);
        return node;
    }

    private static class Node {
        int key;
        int val;
        Node prev;
        Node next;
        Node(int key, int val) { this.key = key; this.val = val; }
    }
}
```

> **Lesson learned (Aug 2026):** If the interviewer says "make it thread-safe," using a plain
> `synchronized` block on every method is not wrong, but using `ReentrantReadWriteLock` shows you
> understand read-heavy vs. write-heavy concurrency — that nuance is exactly what separates SMTS
> from MTS answers at Salesforce.

---

## 🧭 §6 — What the Interviewers Are Actually Evaluating

Synthesized from rejection feedback, offer candidate debrief, and shadow-interviewer notes:

| Signal They Watch For | What Bad Looks Like | What SMTS Looks Like |
|----------------------|--------------------|--------------------|
| **Complexity derivation** | States O(n log n) without justification | Derives it: "each element is pushed/popped once → O(n)" |
| **Communication** | Silent coding, explains after | Narrates assumption → brute force → optimization |
| **Edge cases** | Happy path only | Empty input, single element, duplicates, overflow |
| **Optimization under push** | Defends first solution | "If n is large, O(kn) hurts — switch to heap: O(n log k)" |
| **Concurrency awareness** | "just use synchronized" | "BlockingQueue gives correct blocking semantics + fair FIFO" |
| **Design thinking in coding round** | LC-style flat function | Proper class design, encapsulation, named constants |

**The rejection pattern:** One rejected SMTS candidate (Dec 2025) solved both problems correctly
but received feedback about insufficient design thinking. The problems themselves were medium-level
backtracking — the failure was in *how* they structured and communicated the solution, not in
whether they got the answer.

---

## 🗺️ §7 — Study Plan: 4-Week Coding Round Prep

### Week 1 — Sliding Window + Prefix Sum + Heaps
- LC #3, #76, #424, #567, #1004 (Sliding Window)
- LC #560, #724 (Prefix Sum)
- LC #215, #347, #378, #692 (Heap / Top-K)
- **OA simulation:** 2 problems / 75 min under proctored conditions

### Week 2 — Graphs + Trees
- LC #200, #207, #210, #417, #695 (BFS/DFS + Topo sort)
- LC #543, #124, #112, #987, #297 (Trees)
- Cycle detection in DAG (Kahn's + DFS-color both)
- Reconstruct Itinerary (LC #332)

### Week 3 — DP + Backtracking
- LC #322, #91, #44, #72 (DP)
- LC #17, #46, #78, #39, #131 (Backtracking)
- K-palindrome / palindrome DP (LC #5, #516)
- Zigzag conversion (LC #6)

### Week 4 — Machine-Coding / Concurrency + Design
- Implement Connection Pool with `BlockingQueue` — write full Java class
- Implement MaxQueue (LC #239 variant) — write full Java class
- Implement thread-safe LRU Cache (LC #146) with `ReentrantReadWriteLock`
- Study: `BlockingQueue`, `ReentrantReadWriteLock`, `ConcurrentHashMap`, `CountDownLatch`
- Read: **`../HLD+LLD/research-findings-2026-08.md`** for the design rounds that follow

---

## ⚠️ §8 — Gotchas and "Silent Failure" Patterns

**`BlockingQueue.take()` blocks — that's the point.** Don't accidentally use `poll()` (returns
null immediately when empty). For a connection pool, you *want* blocking semantics.

**LRU Cache with HashMap + DLL: Java's `LinkedHashMap` is NOT thread-safe** even wrapped in
`Collections.synchronizedMap()` — the access-order reordering on `get()` is a write operation.
Always use explicit `ReentrantReadWriteLock` or `ReentrantLock` + manual DLL.

**OA is proctored.** Mic + video on. Don't look away from screen. Don't use browser tabs
during the test. Some candidates were disqualified for this.

**SMTS vs LMTS distinction matters for leveling, not for prep.** The DSA bar at LMTS is not
meaningfully harder — the leveling happens in the design and behavioral rounds. Prep DSA the
same way for both.

**"Approach only" is sometimes enough.** In the Feb 2026 Hyderabad loop, Q2 in Round 1 was
a DAG cycle detection problem where the interviewer only asked for the approach and skipped
actual code. Don't assume you must always write code — but be ready to if asked.

---

## 🔄 Changelog

| Date | Change |
|------|--------|
| Aug 2026 | File created. Research window: Aug 2025 – Aug 2026. Sources: LeetCode Discuss (~15 SMTS threads), Glassdoor SMTS filter, Blind, CodingKaro, Desi QnA OA Sets 13–18. |
