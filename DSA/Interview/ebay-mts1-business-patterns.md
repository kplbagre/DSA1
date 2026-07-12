# eBay MTS1 — Business Implementation Patterns

> **Role:** MTS 1 (Member Technical Staff 1) — Backend
> **Round:** Onsite R1 / CodeSignal ICA (Incremental Coding Assessment — a multi-level format where each level adds requirements to the same class you built at the previous level; you cannot go back)
> **Companion files:**
> - DSA problems (Problems #2, #5, #8, #15): **`DSA/Interview/ebay-mts1-dsa-problems.md`**
> - AI round prep: **`DSA/Interview/ebay-mts1-ai-questions.md`**

---

## 🎯 What This File Is — And Why It Exists

eBay MTS1 has two categories of coding problems:

| Category | Characteristic | Example |
| --- | --- | --- |
| **DSA** | Algorithm is hard. OOP is boilerplate. | Binary Tree Subtree Counting, LRU Cache |
| **Business Implementation** | Algorithm is trivial (linear scan, HashMap). OOP *design* is the hard part. | WeightGrouping, Banking System, In-Memory DB |

Business implementation problems LOOK easy (no recursion, no complex traversal). But candidates fail them because they start coding before thinking. They write one giant if-else or one 200-line method. The interviewer then says "can you extend this to support a new rule type?" and the candidate has to rewrite everything.

This file is entirely about teaching you to THINK first — to derive the OOP design from the problem words before touching the keyboard.

---

## 🧠 The Universal Design-First Framework

Use this for every business implementation problem. Six steps, ~3 minutes, done before coding.

### Step 1 — Name the Entities

Read the problem. Circle every NOUN that represents a **thing** that holds state. Each becomes a class.

> "Implement a banking system that manages accounts. Each account has a balance. Scheduled payments can be made between accounts."

→ Entities: `Account`, `ScheduledPayment`, `BankingSystem`

### Step 2 — Name the Operations

Read the problem. Underline every VERB that represents something the system *does*. Each becomes a method.

> "...deposit, withdraw, transfer, schedule a payment, merge two accounts..."

→ Methods: `deposit()`, `withdraw()`, `transfer()`, `schedulePayment()`, `mergeAccounts()`

### Step 3 — Separate Config from Input

Ask: "What is set up ONCE at construction vs. what arrives PER CALL?"

- **Constructor:** initial rules, ranges, policies (things that don't change per request)
- **Method parameter:** runtime data — the account ID, amount, timestamp

This prevents "config creep" where you end up with 8-argument method calls.

### Step 4 — Apply the Extension Test

Before choosing your data structure, ask the hardest question:

> "What if they add ONE MORE [rule / account type / field / operation type] later? What code changes?"

- If the answer is **"just add data"** → your design is open-closed. ✅
- If the answer is **"add another if-else branch"** → make rules DATA, not CODE. ❌

This is the single most important discipline. The interviewer will ask this. Have the answer before they do.

### Step 5 — Choose Data Structures

Three questions to ask for each structure:

1. "What is the **dominant access pattern**?" (point lookup / ordered scan / range query / eviction)
2. "What is the **key type**?" (named string / timestamp integer / position index)
3. "Does **order matter**?" (no → HashMap, insertion order → LinkedHashMap, sorted → TreeMap)

Full decision table in **`§ ⚡ Data Structure Choice Cheat Sheet`** at the bottom.

### Step 6 — Draw It (2 minutes on the whiteboard)

Use this template every time:

```
EntityA                    EntityB
┌──────────────┐          ┌──────────────┐
│ field: type  │          │ field: type  │
│ ...          │          │ ...          │
└──────────────┘          └──────────────┘
       ▲                         ▲
       │ has-a (Map/List)         │ has-a (TreeMap)
       └────────── EngineClass ───┘
                ┌────────────────────┐
                │ operationA()       │
                │ operationB()       │
                │ [private helper()] │
                └────────────────────┘
```

Say out loud as you draw: *"I have an Account that holds balance and outflow. BankingSystem holds a Map of accounts keyed by id and a TreeMap of scheduled payments keyed by execution time."*

---

## 🎨 Visual — Live Derivation: Banking System (Words → Design)

This is the single most valuable page in this file. Read it once, fully.

**The prompt (paraphrased from CodeSignal ICA):**

> "Implement a banking system. Support create, deposit, withdraw, transfer. Track total outflow per account for a leaderboard query. Allow scheduling a future payment between two accounts. Allow merging two accounts."

**Applying the 6 steps:**

**Step 1 — Entities (nouns):**

```
"banking system" → BankingSystem  (the engine class)
"account"        → Account        (entity: holds balance + outflow)
"payment"        → ScheduledPayment (entity: source, target, amount, when)
```

**Step 2 — Operations (verbs):**

```
create, deposit, withdraw, transfer   → BankingSystem methods (Level 1–2)
"schedule a future payment"           → schedulePayment() method
"execute due payments before ops"     → private executeScheduled() helper
"merge two accounts"                  → mergeAccounts() method
```

**Step 3 — Config vs. Input:**

```
Constructor: (nothing — the system starts empty)
Method params: timestamp, accountId, amount, targetId, delay
```

Note the timestamp on EVERY operation. This is eBay's way of injecting controllable time.
All "future" logic depends on it.

**Step 4 — Extension Test:**

> "What if we add a savings account that earns interest?"

Without an `Account` class: you'd have to add a second map (`Map<String, Double> interestRate`)
and sprinkle interest logic across multiple methods. Each method must remember to check.

With an `Account` class: add an `accountType` field and `applyInterest()` method. One class,
one place. This is why Account deserves to be a class, not just a Long in a HashMap.

**Step 5 — Data Structures:**

```
Accounts lookup:
  Map<String, Account>   HASHMAP     ← O(1) by account ID, no ordering needed
  
Scheduled payments:
  TreeMap<Integer, List<ScheduledPayment>>
    ← keyed by execution timestamp
    ← headMap(T, inclusive=true) fetches all payments due at or before T in O(log n)
    ← HashMap would require O(n) scan to find due payments
    
Top spenders:
  Sort on demand (List + Comparator)
    ← maintaining an always-sorted structure is complex, premature optimization
    ← sort once per leaderboard query: O(n log n), n = number of accounts
```

**Step 6 — Draw It:**

```
Account                         ScheduledPayment
┌──────────────────────┐       ┌─────────────────────────┐
│ id: String           │       │ sourceId: String         │
│ balance: long        │       │ targetId: String         │
│ totalOutflow: long   │       │ amount: long             │
└──────────────────────┘       │ paymentId: String        │
          ▲                    └─────────────────────────┘
          │ has-a (HashMap)              ▲
          │                             │ has-a (TreeMap<ts, List>)
          └──────────── BankingSystem ──┘
                   ┌───────────────────────────────────────┐
                   │ accounts : Map<String, Account>       │
                   │ scheduled: TreeMap<Int, List<Pmt>>    │
                   │ paymentCounter: int                   │
                   │                                       │
                   │ + createAccount(ts, id)               │
                   │ + deposit(ts, id, amt)                │
                   │ + withdraw(ts, id, amt)               │
                   │ + transfer(ts, src, tgt, amt)         │
                   │ + getTopSpenders(ts, n)               │
                   │ + schedulePayment(ts, src, tgt, a, d) │
                   │ + mergeAccounts(ts, id1, id2)         │
                   │ - executeScheduled(ts)    [private]   │
                   └───────────────────────────────────────┘

KEY INVARIANT:
   Every public method calls executeScheduled(timestamp) FIRST.
   Time is monotonically increasing across calls.
   The TreeMap boundary (headMap ≤ T) gives you all due payments in one shot.
```

*Now* you start coding. Not before.

---

## 📖 Terminology

| Term | Plain-English meaning |
| --- | --- |
| **Rule Engine** | A class that holds a list of configurable rules (DATA) and applies them to input — instead of hardcoding the rules as if-else branches (CODE) |
| **Open-Closed Principle** | A design where you can add new behavior (new rule) without modifying existing code — just add a new data entry |
| **Tombstone** | A marker inserted at a timestamp to record a deletion, so time-travel queries can see the deletion as a historical event |
| **headMap(k, inclusive)** | TreeMap method: returns a live view of all entries whose key ≤ k. Clearing this view removes those entries from the original TreeMap. |
| **floorEntry(k)** | TreeMap method: returns the entry with the greatest key ≤ k. Used for time-travel: "what was the value at or before timestamp T?" |
| **Lazy TTL expiration** | Don't clean up expired entries proactively; check the expiry on every read and return null if expired. Simpler, correct for single-threaded problems. |
| **Config in constructor** | Placing the things that don't change per request (rules, policies, capacity limits) in the constructor so every method call doesn't re-derive them. |
| **Stateful system** | A class that accumulates state across many method calls — unlike a pure function that computes and returns. All business impl problems are stateful systems. |

---

## 🔹 Pattern 1: Rule Engine / OOP Classifier

### 🧠 Mental Model

> Decisions are DATA. The engine just iterates.

A rule engine holds a list of `Rule` objects built at construction time. Each call feeds input to the engine, which asks every rule "does this match you?" and collects results. The key discipline: adding a new rule = adding one entry to the list, not touching any if-else.

**When you see this pattern:** classification, grouping, discount tiers, category tagging, eligibility checks.

### 🎨 Visual — Class Diagram

```
WeightRange                     BucketResult
┌────────────────────────┐     ┌─────────────────────┐
│ name: String           │     │ name: String         │
│ low: int               │     │ frequency: int       │
│ high: int              │     │ min: int             │
│                        │     │ max: int             │
│ contains(w: int): bool │     └─────────────────────┘
└────────────────────────┘              ▲
          ▲ has-a (List)                │ produces
          │                            │
WeightClassifier
┌──────────────────────────────────────────────────────┐
│ ranges: List<WeightRange>                            │
│                                                      │
│ classify(items: List<Integer>): List<BucketResult>  │
└──────────────────────────────────────────────────────┘

KEY INVARIANT:
   ranges is built once in the constructor.
   classify() never modifies ranges — it only reads them.
   "Make rules DATA, not CODE."
```

### 🚀 Generic Template

**Steps in plain English:**

1. **Rule class** — one object per rule, holds its parameters, has a `matches(input)` method.
2. **Result class** — one object per group (output slot), accumulates stats.
3. **Engine constructor** — takes the list of rules, stores them. Nothing else.
4. **Engine method** — for each input item, scan rules to find the matching one, update that result.

```java
// ─── Rule / Config class ──────────────────────────────────────
class WeightRange {
    String name;
    int low;
    int high;

    WeightRange(String name, int low, int high) {
        this.name = name;
        this.low = low;
        this.high = high;
    }

    boolean contains(int weight) {
        return weight >= low && weight <= high;
    }
}

// ─── Result / Output class ────────────────────────────────────
class BucketResult {
    String name;
    int frequency;
    int min;
    int max;

    BucketResult(String name) {
        this.name = name;
        this.frequency = 0;
        this.min = Integer.MAX_VALUE;
        this.max = Integer.MIN_VALUE;
    }

    void add(int weight) {
        frequency++;
        min = Math.min(min, weight);
        max = Math.max(max, weight);
    }
}

// ─── Engine class ─────────────────────────────────────────────
class WeightClassifier {

    // Config lives here — built once, never modified after construction
    private final List<WeightRange> ranges;

    WeightClassifier(List<WeightRange> ranges) {
        this.ranges = ranges;
    }

    public List<BucketResult> classify(List<Integer> items) {
        // HASHMAP: O(1) lookup by name — insertion order doesn't matter here
        // → LinkedHashMap: if output must appear in the order ranges were declared
        // → TreeMap<String, BucketResult>: if output must be alphabetically sorted by name
        // → ConcurrentHashMap: ONLY if multiple threads call classify() concurrently (ask!)
        Map<String, BucketResult> resultsMap = new LinkedHashMap<>();
        for (WeightRange r : ranges) {
            resultsMap.put(r.name, new BucketResult(r.name));
        }

        for (int weight : items) {
            for (WeightRange range : ranges) {
                if (range.contains(weight)) {
                    resultsMap.get(range.name).add(weight);
                    // ranges are mutually exclusive — one item can only belong to one bucket
                    break;
                }
            }
        }

        return new ArrayList<>(resultsMap.values());
    }
}
```

**When to escalate from HashMap → TreeMap inside the engine:**

| Trigger | Structure |
| --- | --- |
| Many ranges, need O(log R) lookup | `TreeMap<Integer, WeightRange>` keyed by `low`, then `floorEntry(weight)` |
| Need to detect overlapping ranges | `TreeMap` + `floorEntry` + `ceilingEntry` to check both neighbours |
| Need nearest threshold (not range) | `TreeSet<Integer>` + `floor(weight)` and `ceiling(weight)` |

**Full problem + solution:** **`DSA/Interview/ebay-mts1-dsa-problems.md`** — Problem #5

**Extension triggers — same pattern appears in:**
- Discount engine (tier-based pricing by order value)
- Tax calculator (rate by income bracket)
- Shipping tier (rate by distance zone)
- Age-gate / eligibility checker (rule per user segment)

---

## 🔹 Pattern 2: Hierarchy Modeler (Stack Parsing)

### 🧠 Mental Model

> Nested structure = tree. Stack for building, recursion for traversing.

Any problem with open/close tokens (`<tag>...</tag>`, `dir/{files}`, `((...))`) is asking you to build a tree. You push nodes onto a stack when entering a level, pop when leaving. The top of the stack is always the current parent.

**When you see this pattern:** XML/HTML parsing, directory tree traversal, nested expression evaluation, JSON parsing.

### 🎨 Visual — Building the Tree with a Stack

```
Input: "<root><child1><leaf/></child1><child2/></root>"
Stack during parse:

After <root>    :  Stack [root]            current parent = root
After <child1>  :  Stack [root, child1]    current parent = child1
After <leaf/>   :  leaf added to child1's children, no push (self-closing)
After </child1> :  Stack [root]            current parent = root
After <child2/> :  child2 added to root's children
After </root>   :  Stack []               root is complete

KEY INVARIANT:
   Stack top = current parent.
   Open tag → create node, add to parent, push.
   Close tag → pop.
   Self-closing → create node, add to parent, NO push/pop.
```

### 🚀 Generic Template

**Steps in plain English:**

1. **Node class** — holds name and `List<Node> children`.
2. **Parse token by token** — use a Deque as a stack.
3. **On open token** — create a new node, add to stack-top's children, push.
4. **On close token** — pop the stack.
5. **Return the root** — the node left after parsing is complete.

```java
// Node class — same shape for any hierarchy problem
class TreeNode {
    String name;
    List<TreeNode> children;

    TreeNode(String name) {
        this.name = name;
        // ARRAYLIST: children accessed in order, no lookup by name needed
        // → HashMap<String, TreeNode>: if you need O(1) lookup of a child by name
        this.children = new ArrayList<>();
    }
}

// Generic parsing skeleton
public TreeNode parse(String input) {
    // ARRAYDEQUE as stack: push = addFirst, pop = removeFirst
    // → Stack<TreeNode>: also valid, but Deque is the modern Java idiom
    Deque<TreeNode> stack = new ArrayDeque<>();
    TreeNode root = null;

    // tokenize input — problem-specific
    for (String token : tokenize(input)) {
        if (isOpenTag(token)) {
            String name = extractName(token);
            TreeNode node = new TreeNode(name);
            if (!stack.isEmpty()) {
                stack.peek().children.add(node);
            }
            stack.push(node);
            if (root == null) {
                root = node;
            }
        } else if (isCloseTag(token)) {
            stack.pop();
        }
    }
    return root;
}

// Recursive traversal (DFS) — generic
public List<String> listAll(TreeNode node) {
    List<String> result = new ArrayList<>();
    if (node.children.isEmpty()) {
        result.add(node.name);  // leaf node (file in ls-r, element in HTML)
        return result;
    }
    for (TreeNode child : node.children) {
        result.addAll(listAll(child));
    }
    return result;
}
```

**Full problems + solutions:**
- HTML/XML Parser: **`DSA/Interview/ebay-mts1-dsa-problems.md`** — Problem #2
- `ls -r` implementation: **`DSA/Interview/ebay-mts1-dsa-problems.md`** — Problem #8

**Extension triggers — same pattern appears in:**
- DOM traversal / XPath evaluation
- JSON deserialization
- Nested configuration parsing (YAML/TOML tree → object)
- Abstract Syntax Tree (AST) evaluation

---

## 🔹 Pattern 3: API-Constrained Store (O(1) Everything)

### 🧠 Mental Model

> O(1) for both random access AND ordered eviction = two data structures in one class.

When the problem says "get and put must both be O(1)" AND involves some ordering (LRU order, insertion order, expiry), you cannot satisfy both with a single structure. The answer is always: HashMap for O(1) lookup + doubly-linked list for O(1) ordered access.

**When you see this pattern:** LRU cache, LFU cache, TTL cache with O(1) evict, sliding-window minimum.

### 🎨 Visual — LRU Internal Layout

```
HashMap                     Doubly-Linked List (MRU → LRU)
┌─────────────────────┐
│ key="A" → NodeA ───┼──┐   HEAD  ←→  [D]  ←→  [B]  ←→  [A]  ←→  TAIL
│ key="B" → NodeB ───┼──┼──────────────────────────────────►
│ key="D" → NodeD ───┼──┘   Most Recently Used         Least Recently Used
└─────────────────────┘
      O(1) lookup              O(1) move-to-front, O(1) evict-from-tail

KEY INVARIANT:
   Every get() promotes the node to MRU position (head).
   Every put() promotes or inserts at head.
   On capacity overflow, evict from tail.
   HashMap and DLL are always kept in sync — both update together or not at all.
```

**Full problem + solution:** **`DSA/Interview/ebay-mts1-dsa-problems.md`** — Problem #15 (LRU Cache)

**Map choice note:** `LinkedHashMap` with `accessOrder=true` gives you this structure for free. eBay onsite expects you to know the raw implementation, but know the shortcut exists.

```java
// Shortcut (3 lines, valid if interviewer allows it):
// LINKEDHASHMAP with removeEldestEntry override — built-in LRU
Map<Integer, Integer> cache = new LinkedHashMap<>(capacity, 0.75f, true) {
    protected boolean removeEldestEntry(Map.Entry<Integer, Integer> eldest) {
        return size() > capacity;
    }
};
```

**Extension triggers:** Disk cache, browser history (bounded), connection pool with least-recently-used eviction.

---

## 🔹 Pattern 4: Stateful Banking System (CodeSignal ICA)

> ⭐ **Full treatment — this is the hardest ICA problem type. Study every line.**

### 🧠 Mental Model

> State = accounts + their histories. Time = the timestamp on every operation. Scheduling = TreeMap with execution time as key.

The trap is thinking of this as a simple CRUD app. The complexity comes from:
- Operations at timestamp T must first execute all scheduled payments due at T
- Merged accounts must redirect all pending future payments

### 🧭 Design Steps — Walk the 6-Step Framework

**Entities:** `Account`, `ScheduledPayment`, `BankingSystem`

**Operations per level:**

```
Level 1: createAccount, deposit, withdraw, getBalance
Level 2: transfer, getTopSpenders
Level 3: schedulePayment  (+ private executeScheduled helper)
Level 4: mergeAccounts
```

**Extension test:** "What if we add a savings account with monthly interest?"
→ Add `accountType` field and `applyInterest()` to `Account`.
→ `BankingSystem.deposit()` doesn't change. ✅

**Data structures:**

```
accounts:  HashMap<String, Account>
  → O(1) lookup by account id
  → no ordering needed — we sort for getTopSpenders on demand

scheduled: TreeMap<Integer, List<ScheduledPayment>>
  → key = execution timestamp
  → headMap(T, true) fetches ALL due payments in O(log n) — not O(n)
  → clearing the headMap view removes entries from the TreeMap at once
```

### 🎨 Visual — executeScheduled Flow

```
                  Every public method
                        │
            ┌───────────▼───────────┐
            │  executeScheduled(T)  │
            └───────────┬───────────┘
                        │
            headMap(T, inclusive=true)
                        │
            ┌───────────▼───────────────────────────────┐
            │  for each due payment (in time order):    │
            │    if src.balance >= amount:              │
            │      src.balance -= amount                │
            │      src.totalOutflow += amount           │
            │      tgt.balance += amount                │
            │    else: skip (insufficient funds)        │
            └───────────────────────────────────────────┘
                        │
                 due.clear()  ← removes processed entries from the TreeMap
                        │
            ┌───────────▼───────────┐
            │  actual operation     │
            │  (deposit/withdraw/..)│
            └───────────────────────┘

KEY INVARIANT:
   executeScheduled runs BEFORE every public operation, never after.
   Payment execution at time T can change balances seen by the operation at time T.
   A scheduled payment at time T+delay has key = T+delay > T, so it is never processed
   by the headMap call at timestamp T — no accidental early execution.
```

### 🚀 Full Implementation

**Steps in plain English:**

1. **Account class** — holds id, balance, totalOutflow. No logic, just state.
2. **ScheduledPayment class** — holds sourceId, targetId, amount, paymentId. Also just state.
3. **BankingSystem constructor** — initialize empty HashMap for accounts, empty TreeMap for scheduled.
4. **Every public method** — call `executeScheduled(timestamp)` as first line.
5. **executeScheduled** — `headMap(timestamp, true)` → iterate all due payments → execute if sufficient funds → clear the view.
6. **mergeAccounts** — merge balances + outflows, remove account2, reroute account2's pending payments.

```java
class Account {
    String id;
    long balance;
    long totalOutflow;

    Account(String id) {
        this.id = id;
        this.balance = 0L;
        this.totalOutflow = 0L;
    }
}

class ScheduledPayment {
    String sourceId;
    String targetId;
    long amount;
    String paymentId;

    ScheduledPayment(String sourceId, String targetId, long amount, String paymentId) {
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.amount = amount;
        this.paymentId = paymentId;
    }
}

class BankingSystem {

    // HASHMAP: O(1) account lookup — no ordering needed, all queries by exact id
    // → LinkedHashMap: if iteration must match creation order (unlikely, avoid)
    // → TreeMap<String, Account>: never — account ids are arbitrary strings, no range queries
    // → ConcurrentHashMap: ONLY if interviewer says multi-threaded (ask before assuming)
    private final Map<String, Account> accounts = new HashMap<>();

    // TREEMAP: keyed by execution timestamp — enables headMap(T, true) to get all due payments
    // → HashMap: would require O(n) scan to find due payments — wrong choice here
    // → PriorityQueue: would work for finding the minimum, but headMap is cleaner for batches
    private final TreeMap<Integer, List<ScheduledPayment>> scheduled = new TreeMap<>();

    private int paymentCounter = 0;

    // ─── Level 1 ─────────────────────────────────────────────────────────────

    public boolean createAccount(int timestamp, String accountId) {
        if (accounts.containsKey(accountId)) {
            return false;
        }
        accounts.put(accountId, new Account(accountId));
        return true;
    }

    public Long deposit(int timestamp, String accountId, long amount) {
        // Step 1: always run scheduled payments first
        executeScheduled(timestamp);
        if (!accounts.containsKey(accountId)) {
            return null;
        }
        Account acc = accounts.get(accountId);
        acc.balance += amount;
        return acc.balance;
    }

    public Long withdraw(int timestamp, String accountId, long amount) {
        executeScheduled(timestamp);
        if (!accounts.containsKey(accountId)) {
            return null;
        }
        Account acc = accounts.get(accountId);
        if (acc.balance < amount) {
            // insufficient funds — return null per spec, never throw an exception
            return null;
        }
        acc.balance -= amount;
        acc.totalOutflow += amount;
        return acc.balance;
    }

    public Long getBalance(int timestamp, String accountId) {
        executeScheduled(timestamp);
        Account acc = accounts.get(accountId);
        return acc == null ? null : acc.balance;
    }

    // ─── Level 2 ─────────────────────────────────────────────────────────────

    public Long transfer(int timestamp, String sourceId, String targetId, long amount) {
        executeScheduled(timestamp);
        if (sourceId.equals(targetId)) {
            // self-transfer is not allowed — balance unchanged, return null
            return null;
        }
        if (!accounts.containsKey(sourceId) || !accounts.containsKey(targetId)) {
            return null;
        }
        Account src = accounts.get(sourceId);
        Account tgt = accounts.get(targetId);
        if (src.balance < amount) {
            return null;
        }
        src.balance -= amount;
        src.totalOutflow += amount;
        tgt.balance += amount;
        return src.balance;
    }

    public List<String> getTopSpenders(int timestamp, int n) {
        executeScheduled(timestamp);
        List<Account> all = new ArrayList<>(accounts.values());
        // Sort on demand: O(n log n) per call — better than maintaining a sorted structure
        // SORT: desc by totalOutflow, then asc by accountId as tie-breaker
        all.sort((a, b) -> {
            if (b.totalOutflow != a.totalOutflow) {
                return Long.compare(b.totalOutflow, a.totalOutflow);
            }
            return a.id.compareTo(b.id);
        });
        List<String> result = new ArrayList<>();
        for (int i = 0; i < Math.min(n, all.size()); i++) {
            Account acc = all.get(i);
            result.add(acc.id + "(" + acc.totalOutflow + ")");
        }
        return result;
    }

    // ─── Level 3 ─────────────────────────────────────────────────────────────

    public String schedulePayment(int timestamp, String sourceId, String targetId,
                                  long amount, int delay) {
        executeScheduled(timestamp);
        if (!accounts.containsKey(sourceId) || !accounts.containsKey(targetId)) {
            return null;
        }
        if (delay <= 0) {
            return null;
        }
        int executionTime = timestamp + delay;
        String paymentId = "payment" + (++paymentCounter);
        scheduled.computeIfAbsent(executionTime, k -> new ArrayList<>())
                 .add(new ScheduledPayment(sourceId, targetId, amount, paymentId));
        return paymentId;
    }

    // ─── Level 4 ─────────────────────────────────────────────────────────────

    public boolean mergeAccounts(int timestamp, String accountId1, String accountId2) {
        executeScheduled(timestamp);
        if (!accounts.containsKey(accountId1) || !accounts.containsKey(accountId2)) {
            return false;
        }
        if (accountId1.equals(accountId2)) {
            return false;
        }
        Account acc1 = accounts.get(accountId1);
        Account acc2 = accounts.get(accountId2);

        // Combine state: balance and outflow both transfer
        acc1.balance += acc2.balance;
        acc1.totalOutflow += acc2.totalOutflow;
        accounts.remove(accountId2);

        // Reroute pending scheduled payments that reference account2
        for (List<ScheduledPayment> payments : scheduled.values()) {
            for (ScheduledPayment p : payments) {
                if (p.sourceId.equals(accountId2)) {
                    p.sourceId = accountId1;
                }
                if (p.targetId.equals(accountId2)) {
                    p.targetId = accountId1;
                }
            }
        }
        return true;
    }

    // ─── Private helper ───────────────────────────────────────────────────────

    private void executeScheduled(int timestamp) {
        // headMap(timestamp, true): all entries with key <= timestamp
        // This is a LIVE VIEW — clearing it removes those entries from the original TreeMap
        NavigableMap<Integer, List<ScheduledPayment>> due = scheduled.headMap(timestamp, true);
        for (List<ScheduledPayment> payments : due.values()) {
            for (ScheduledPayment p : payments) {
                if (!accounts.containsKey(p.sourceId) || !accounts.containsKey(p.targetId)) {
                    // one account was deleted (e.g., removed by mergeAccounts) — skip silently
                    continue;
                }
                Account src = accounts.get(p.sourceId);
                Account tgt = accounts.get(p.targetId);
                if (src.balance >= p.amount) {
                    src.balance -= p.amount;
                    src.totalOutflow += p.amount;
                    tgt.balance += p.amount;
                }
                // insufficient funds at execution time → payment silently fails (no retry)
            }
        }
        due.clear();
    }
}
```

### 🔬 Worked Test Trace

```
createAccount(1, "alice")   → true     (alice: bal=0, out=0)
createAccount(2, "bob")     → true     (bob:   bal=0, out=0)
deposit(4, "alice", 500)    → 500      (alice: bal=500)
withdraw(5, "alice", 200)   → 300      (alice: bal=300, out=200)
withdraw(6, "alice", 400)   → null     (300 < 400 — insufficient)
deposit(9, "bob", 1000)     → 1000     (bob: bal=1000)
transfer(10, "bob", "alice", 400)
  → alice: 300+400=700, bob: 1000-400=600, bob.out=400
  → returns 600 (bob's remaining balance)

getTopSpenders(12, 2)
  → sort: bob.out=400 > alice.out=200
  → ["bob(400)", "alice(200)"]

deposit(14, "alice", 100)   → 800      (alice: bal=800)
schedulePayment(15, "alice", "carol", 300, 10)
  → carol created at ts=13, executionTime=15+10=25
  → stored in scheduled TreeMap at key=25
  
getBalance(20, "alice")     → 800      (headMap(20) doesn't include key=25 — not due)
getBalance(25, "alice")     → 500      (headMap(25) DOES include key=25 — executed first)
getBalance(25, "carol")     → 300      (received the scheduled payment)

deposit(31, "dave", 100)    → 100
mergeAccounts(32, "alice", "dave")
  → alice.bal=500+100=600, alice.out=200+0=200, dave deleted
  
getBalance(33, "alice")     → 600
getBalance(33, "dave")      → null
```

### ⚠️ Gotchas

```
1. executeScheduled BEFORE the operation, not after — the scheduled payment at T can
   change balances that the operation at T then reads.

2. headMap is a LIVE VIEW. Calling due.clear() removes those entries from the original
   TreeMap — that's exactly what you want. But iterating it while modifying the original
   would throw ConcurrentModificationException. Use due.values() safely here.

3. mergeAccounts must reroute pending payments BEFORE returning. If you forget this,
   a payment referencing the deleted account will silently fail at execution time.

4. Self-merge (accountId1.equals(accountId2)) must be rejected — otherwise you'd
   add the account's balance to itself and corrupt it.

5. getTopSpenders total outflow = withdrawals + transfers from that account.
   Receiving a transfer does NOT count. Scheduled payments that have already executed
   DO count (they updated totalOutflow during executeScheduled).
```

---

## 🔹 Pattern 5: In-Memory Database with TTL + Time-Travel (CodeSignal ICA)

> ⭐ **Full treatment — the inner TreeMap for time-travel is the key insight.**

### 🧠 Mental Model

> Storage = nested maps. Time = the timestamp key inside the innermost TreeMap. TTL = expiry metadata checked on every read. Time-travel = floorEntry(T) on the inner TreeMap.

The insight that unlocks this: you never overwrite a value. You INSERT a new entry at the current timestamp. `get(key, field, T)` asks "what was the most recent value set at or before T?" → `floorEntry(T)`.

### 🧭 Design Steps

**Entities:** `ValueEntry` (value + expiry), `InMemoryDatabase` (the engine)

**Operations per level:**

```
Level 1: set, get, delete, scan
Level 2: setWithTTL  (TTL = value expires at timestamp + ttl)
Level 3: scanByPrefix  (filter fields by name prefix)
Level 4: backup / restore at a given timestamp (getAtTimestamp — see note below)
```

> **Note on time-travel:** `get(timestamp, key, field)` already supports time-travel from Level 1
> because the inner TreeMap stores all historical values. `floorEntry(timestamp)` finds the value
> as of any past time. Level 4 typically asks you to add a `backup(timestamp)` operation that
> returns a snapshot, or `getAtTimestamp` across multiple fields — but the core mechanism
> (floorEntry on the inner TreeMap) is the same. Do not redesign the storage for Level 4.


**Extension test:** "What if we add indexes on field values for O(1) filter?"
→ Add `Map<String, Map<String, Set<String>>> invertedIndex` (field → value → Set<keys>).
→ Update `set()` and `delete()` to maintain the index.
→ The rest of the code doesn't change. ✅

**Data structures:**

```
store: HashMap<String, HashMap<String, TreeMap<Integer, ValueEntry>>>
  outer HashMap : key   → O(1) lookup by record key
  middle HashMap: field → O(1) lookup by field name
  inner TreeMap : ts    → O(log n) floorEntry(T) for time-travel

  WHY TreeMap here and not HashMap?
  → If HashMap: you'd have to scan all (timestamp, value) pairs to find "latest before T"
  → TreeMap.floorEntry(T) finds it in O(log n)

TTL: stored as expiryTimestamp in the ValueEntry itself
  → Lazy expiration: check expiry on every read, not proactively
  → No background cleanup thread needed (single-threaded problems never need it)
```

### 🎨 Visual — Inner TreeMap (Time-Travel)

```
store["user1"]["name"]  (a TreeMap<Integer, ValueEntry>)

timestamp → value
─────────────────────────────────────────────────
    1     → ValueEntry("Alice", expiry=∞)
   10     → ValueEntry("Alicia", expiry=∞)
   25     → ValueEntry(null, expiry=∞)   ← tombstone (deletion at ts=25)

Queries:
  get(5,  "user1", "name") → floorEntry(5)  = ts=1  → "Alice"   (∞ not expired)
  get(15, "user1", "name") → floorEntry(15) = ts=10 → "Alicia"
  get(30, "user1", "name") → floorEntry(30) = ts=25 → null (tombstone = deleted)
  get(0,  "user1", "name") → floorEntry(0)  = null  → no value existed at ts=0

KEY INVARIANT:
   set() and delete() APPEND entries — they never remove from the TreeMap.
   get() asks "what was the most recent entry at or before T?" via floorEntry(T).
   A null value in a ValueEntry = tombstone (the field was deleted at that timestamp).
```

### 🚀 Full Implementation

**Steps in plain English:**

1. **ValueEntry class** — holds value (null = tombstone) and expiryTimestamp.
2. **Three-level nested map** — outer HashMap by key, middle HashMap by field, inner TreeMap by timestamp.
3. **set()** — insert new ValueEntry at current timestamp. Clears TTL if a new permanent value is set after expiry.
4. **get()** — `floorEntry(timestamp)` on inner TreeMap, then null if tombstone or expired.
5. **delete()** — call `get()` to check existence, then insert a tombstone ValueEntry.
6. **scan()** — iterate all fields, call `get()` on each, collect non-null results sorted by field name (use a TreeMap for the sorted output).
7. **setWithTTL()** — same as `set()` but with `expiryTimestamp = timestamp + ttl`.

```java
class InMemoryDatabase {

    static class ValueEntry {
        // null value = tombstone — the field was deleted at this timestamp
        String value;
        // Integer.MAX_VALUE = no expiry (permanent set)
        int expiryTimestamp;

        ValueEntry(String value, int expiry) {
            this.value = value;
            this.expiryTimestamp = expiry;
        }
    }

    // Three-level structure:
    //   outer HASHMAP : key   → O(1) by record key (arbitrary string, no range queries)
    //   middle HASHMAP: field → O(1) by field name
    //   inner TREEMAP : ts    → O(log n) floorEntry for time-travel
    //
    // → ConcurrentHashMap for outer: ONLY if concurrent writes (ask interviewer)
    // → LinkedHashMap for middle: ONLY if scan must preserve field insertion order
    // → The inner TreeMap is NON-NEGOTIABLE for time-travel — no other structure gives floorEntry
    private final Map<String, Map<String, TreeMap<Integer, ValueEntry>>> store = new HashMap<>();

    // ─── Level 1: Basic CRUD ──────────────────────────────────────────────────

    public void set(int timestamp, String key, String field, String value) {
        store.computeIfAbsent(key, k -> new HashMap<>())
             .computeIfAbsent(field, f -> new TreeMap<>())
             .put(timestamp, new ValueEntry(value, Integer.MAX_VALUE));
    }

    public String get(int timestamp, String key, String field) {
        if (!store.containsKey(key)) {
            return null;
        }
        TreeMap<Integer, ValueEntry> history = store.get(key).get(field);
        if (history == null) {
            return null;
        }
        // Time-travel: find the value as it was at or before 'timestamp'
        Map.Entry<Integer, ValueEntry> entry = history.floorEntry(timestamp);
        if (entry == null) {
            // no value was set at or before this timestamp
            return null;
        }
        ValueEntry ve = entry.getValue();
        if (timestamp >= ve.expiryTimestamp) {
            // value has expired by this timestamp — lazy TTL check, return null
            return null;
        }
        // null value = tombstone — the field was explicitly deleted at set-time
        return ve.value;
    }

    public boolean delete(int timestamp, String key, String field) {
        if (get(timestamp, key, field) == null) {
            // field doesn't exist, is expired, or is already deleted — nothing to do
            return false;
        }
        // Insert a tombstone — null value means "deleted as of this timestamp"
        store.get(key)
             .get(field)
             .put(timestamp, new ValueEntry(null, Integer.MAX_VALUE));
        return true;
    }

    public List<String> scan(int timestamp, String key) {
        if (!store.containsKey(key)) {
            return new ArrayList<>();
        }
        // TREEMAP for sorted output — scan must return fields in alphabetical order
        // → HashMap + sort at end: also valid, but TreeMap makes the sort free
        TreeMap<String, String> sorted = new TreeMap<>();
        for (String fieldName : store.get(key).keySet()) {
            String val = get(timestamp, key, fieldName);
            if (val != null) {
                sorted.put(fieldName, val);
            }
        }
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            result.add(e.getKey() + "(" + e.getValue() + ")");
        }
        return result;
    }

    // ─── Level 2: TTL support ─────────────────────────────────────────────────

    public void setWithTTL(int timestamp, String key, String field, String value, int ttl) {
        int expiry = timestamp + ttl;
        store.computeIfAbsent(key, k -> new HashMap<>())
             .computeIfAbsent(field, f -> new TreeMap<>())
             .put(timestamp, new ValueEntry(value, expiry));
        // Expiry is stored IN the ValueEntry — no separate TTL map needed.
        // get() checks expiry every time: "if (timestamp >= ve.expiryTimestamp) return null"
    }

    // ─── Level 3: Prefix filter ───────────────────────────────────────────────

    public List<String> scanByPrefix(int timestamp, String key, String prefix) {
        if (!store.containsKey(key)) {
            return new ArrayList<>();
        }
        TreeMap<String, String> sorted = new TreeMap<>();
        for (String fieldName : store.get(key).keySet()) {
            if (!fieldName.startsWith(prefix)) {
                continue;
            }
            String val = get(timestamp, key, fieldName);
            if (val != null) {
                sorted.put(fieldName, val);
            }
        }
        List<String> result = new ArrayList<>();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            result.add(e.getKey() + "(" + e.getValue() + ")");
        }
        return result;
    }
}
```

### 🔬 Worked Test Trace

```
set(1, "user1", "name", "Alice")
set(2, "user1", "age",  "30")
set(3, "user2", "name", "Bob")

get(5, "user1", "name")          → "Alice"     (floorEntry(5) → ts=1, not expired)
get(5, "user1", "age")           → "30"
get(5, "nobody", "x")            → null        (key doesn't exist)

scan(5, "user1")                  → ["age(30)", "name(Alice)"]   (alphabetical)

setWithTTL(6, "user1", "session", "xyz", 5)   expiry = 6+5 = 11
get(8,  "user1", "session")       → "xyz"      (8 < 11 — not expired)
get(11, "user1", "session")       → null       (11 >= 11 — expired)

set(10, "user2", "name", "Bobby")   overwrite at ts=10
get(5,  "user2", "name")          → "Bob"      (floorEntry(5) → ts=3 entry)
get(15, "user2", "name")          → "Bobby"    (floorEntry(15) → ts=10 entry)

delete(20, "user1", "age")        → true       (tombstone inserted at ts=20)
get(25, "user1", "age")           → null       (floorEntry(25) → ts=20 tombstone)
get(3,  "user1", "age")           → "30"       (floorEntry(3) → ts=2, before tombstone)

scanByPrefix(35, "user3", "addr_") → ["addr_city(NYC)", "addr_zip(10001)"]
```

### ⚠️ Gotchas

```
1. The inner TreeMap stores ALL historical values — you never remove them.
   floorEntry(T) gives you "the most recent value at or before T."
   This is what makes time-travel free — it's baked into the data structure.

2. A null value in a ValueEntry is a TOMBSTONE (deletion marker), not the absence
   of a ValueEntry. Always check ve.value after floorEntry() — null value means deleted.

3. TTL expiry check: "if (timestamp >= expiryTimestamp) return null"
   If TTL=5 set at timestamp=6, expiry=11.
   At timestamp=11: 11 >= 11 → expired. At timestamp=10: 10 < 11 → valid.
   Off-by-one matters. Use >=, not >.

4. scan() and scanByPrefix() must go through get() for each field — not directly
   through the inner TreeMap. get() handles tombstones and TTL. Direct inner access
   bypasses both and returns stale/deleted values.

5. setWithTTL followed by a plain set() on the same field: the new set() inserts
   a new ValueEntry at the new timestamp with expiry=MAX_VALUE (permanent).
   floorEntry at any future T ≥ new timestamp will find the permanent entry first.
   The old TTL entry is buried in history — effectively cleared automatically.
```

---

## ⚡ Data Structure Choice Cheat Sheet

| You need | Use | Trigger phrase |
| --- | --- | --- |
| O(1) lookup by exact key | `HashMap` | "look up account by id", "check if X exists" |
| O(1) lookup + iteration in insertion order | `LinkedHashMap` | "return in the order added", built-in LRU trick |
| O(1) lookup + iteration in sorted key order | `TreeMap` | "iterate sorted by key", "print accounts alphabetically" |
| Range query (all keys ≤ T) | `TreeMap.headMap(T, true)` | "execute all payments due by time T" |
| Nearest key lookup (floor / ceiling) | `TreeMap.floorEntry(k)` | "most recent value at or before timestamp T" |
| Nearest value in a set | `TreeSet.floor(k)` / `.ceiling(k)` | "nearest threshold in a set of values" |
| Sorted set, no duplicates | `TreeSet` | "find nearest allowed weight", "overlap detection" |
| Concurrent reads + writes | `ConcurrentHashMap` | ← **only if interviewer explicitly says multi-threaded** |
| Sort on demand | `List` + `Collections.sort` or `Comparator` | "top N spenders", "leaderboard" — don't maintain sorted structure if only queried occasionally |
| O(1) get + O(1) evict by order | `HashMap` + doubly-linked list | LRU, LFU, bounded cache of any kind |

---

## ⚠️ Gotchas Hall of Fame

```
1. TREEMAP headMap RETURNS A LIVE VIEW.
   NavigableMap<K,V> due = treeMap.headMap(T, true);
   due.clear() removes those entries from treeMap. This is intentional and correct.
   But: never modify treeMap inside a headMap iteration — ConcurrentModificationException.

2. NULL AS TOMBSTONE vs NULL AS ABSENT.
   In InMemoryDatabase: ValueEntry.value == null means "deleted at this timestamp."
   A missing key in the outer map means "never existed."
   Always distinguish these — they have different semantics for time-travel.

3. SORTED OUTPUT IS FREE IF YOU USE TREEMAP FOR INTERMEDIATE.
   Instead of collecting into a List and calling sort(), collect into a TreeMap<String, String>.
   Iteration over a TreeMap is already in ascending key order. Saves one sort() call.

4. computeIfAbsent IS NOT THREAD-SAFE ON HashMap.
   Fine for single-threaded interview problems. If thread safety is needed, switch the
   outer map to ConcurrentHashMap and use putIfAbsent instead.

5. LONG vs INT FOR BALANCES.
   Always use long for monetary amounts and totals. A balance of 10B with 1000 accounts
   overflows int. eBay's test cases will include this edge case.

6. THE EXTENSION TEST IS THE REAL INTERVIEW.
   eBay interviewers routinely say "now what if we add X?" after you've coded.
   If you hardcoded rules as if-else, you rewrite. If you made them data, you don't.
   The interview grade is partly based on your answer to this question.
```

---

## 🧾 TL;DR — One-Line Core Per Pattern

| Pattern | One-line core |
| --- | --- |
| Rule Engine | Make rules DATA (a list of objects), not CODE (if-else branches) |
| Hierarchy Modeler | Stack for building tree (push on open, pop on close), DFS for traversal |
| API-Constrained Store | O(1) everywhere = HashMap + doubly-linked list together |
| Banking System | Every method executes scheduled payments first; TreeMap makes batch-due O(log n) |
| In-Memory DB | Never overwrite — append with timestamp; floorEntry gives time-travel for free |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| July 2026 | File created. Covers 5 business implementation patterns for eBay MTS1. Banking System and In-Memory Database implementations verified against test cases. |
