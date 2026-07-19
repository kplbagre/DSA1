# Hybrid Data Structure Design — Deep Dive

> **What this covers:** The pattern of combining two data structures to serve two different query types efficiently. Centered on the In-Memory KV Store with TTL problem — confirmed in a real Docusign interview (Jul 2026).

---

## 📋 Section Index

| Section | Topic |
| --- | --- |
| [🎯 Goal](#goal) | What you can do after reading this |
| [🚦 Difficulty Tags](#difficulty-tags) | ✅ 🟡 🔴 ratings explained |
| [🌲 What Is the Dual-Index Pattern?](#what-is-dual-index) | HashMap + ordered structure together |
| [📖 Terminology](#terminology) | Primary index, secondary index, TTL, eviction |
| [🧠 Mental Model](#mental-model) | Two maps, one truth — how dual-index works |
| [🎨 Style Habits](#style-habits) | Sync discipline: always update both maps together |
| [🧭 The Pattern — Step by Step](#pattern-steps) | Phase 1/2/3 — how to design the data structure |
| [🔬 Worked Walkthrough](#walkthrough) | In-memory KV store with TTL — full implementation |
| [⏱️ Complexity Table](#complexity) | Get/put/evict — time and space |
| [⚠️ Gotchas](#gotchas) | Partial update, stale secondary index, eviction order |
| [🗺️ Practice Plan](#practice-plan) | LRU Cache → LFU Cache → TTL KV → Leaderboard |
| [🧾 TL;DR](#tldr) | One-page summary for revision day |
| [🔄 Changelog](#changelog) | Doc history |


---

<a id="goal"></a>
## 🎯 Why You're Reading This

Some problems cannot be solved optimally with a single data structure. They have **two types of queries** that pull in opposite directions:

| Query type | Best structure |
|---|---|
| Lookup by key (point query) | HashMap — O(1) |
| Range query ordered by some secondary attribute (time, count, priority) | TreeMap — O(log n) with `floorKey / tailMap / headMap` |

When a problem needs both, you reach for **two structures in parallel** — one per query type. This is the Hybrid Data Structure Design pattern.

The anchor problem here: **In-Memory KV Store with TTL** — exactly what Docusign asked about in production interview context (Jul 2026). It is also the gateway to Time-Based KV Store (LC 981), which is structurally identical.

After reading this once you will:
1. Recognize the dual-query signal in problem statements
2. Know why a single structure always fails for one of the two query types
3. Design the Entry class correctly (not the `Object[]` hack)
4. Code the full solution cleanly from scratch under interview pressure
5. Handle all follow-ups confidently

---

<a id="difficulty-tags"></a>
## 🚦 Difficulty Tagging — Read Before You Pick a Problem

| Tag | Meaning | Action |
|---|---|---|
| ✅ **Try Now** | Solvable with concepts in this doc | Attempt cold, time-box 25 min |
| 🟡 **Try After [Section]** | Needs a later section in this doc | Bookmark, return after named section |
| 🔴 **Reference Only** | Needs concepts beyond this doc | Read editorial, don't attempt cold |

---

<a id="what-is-dual-index"></a>
## 🌲 What Is the Dual-Index Pattern?

**The core idea:** When a problem requires O(1) lookup by key **and** O(log n) range queries by another dimension (time, value, frequency), no single data structure satisfies both requirements simultaneously.

The solution: maintain **two parallel data structures** — each optimized for one query type — and keep them **in sync** on every write (insert, update, delete).

**Simplest mental model:** Imagine a library that maintains two catalogs:
- **By title (alphabetical)** — find a specific book in O(1) hash lookup
- **By return-due-date (sorted)** — find all books due this week in O(log n)

When a book is added, both catalogs are updated. When a book is returned, both catalogs are updated. Neither catalog alone serves both query types well.

---

<a id="terminology"></a>
## 📖 Terminology Table

| Term | Plain-English meaning |
|---|---|
| **TTL** (time-to-live) | A duration after which a stored entry is automatically treated as expired and invisible to readers |
| **Expiry time** | The absolute timestamp at which a key expires = `System.currentTimeMillis() + ttl` |
| **Lazy expiry** | Expired entries are not proactively deleted; they are removed only when they are accessed. A background thread is NOT required. |
| **Point query** | Lookup by exact key: `get("docSign")` — HashMap is best |
| **Range query** | Lookup across a range of sorted keys: "all entries expiring after now" — TreeMap's `tailMap` is best |
| **Dual-index** | Two parallel data structures indexing the same data on two different keys (primary key + secondary attribute) |
| **`tailMap(k)`** | TreeMap operation: returns a view of all entries with key ≥ k — O(log n) to find the start point |
| **`floorKey(k)`** | TreeMap operation: returns the largest key ≤ k — O(log n) |
| **Entry / Record class** | An inner class grouping related fields (value + expiryTime) so they travel together and are type-safe |
| **Sentinel** | A pre-inserted boundary node in a linked list to eliminate null-pointer edge cases (used in LRU Cache, a related design problem) |

---

<a id="mental-model"></a>
## 🧠 Mental Model

### Why a single structure always fails

Think through what happens when you try to use only one structure:

**Option A — Only HashMap (key → Entry):**
- `get("key")` → O(1) ✅
- `countActive()` → must scan every entry and check `expiry > now` → O(n) ❌

**Option B — Only TreeMap keyed by expiry time (Long → Set\<String\>):**
- `countActive()` → `tailMap(now+1)` → O(log n) ✅
- `get("key")` → must scan all expiry buckets looking for the key → O(n) ❌

**Option C — Only TreeMap keyed by primary key (String → Entry):**
- `get("key")` → O(log n) — better than HashMap by a constant but still not O(1)
- `countActive()` → scan all entries, check expiry → O(n) ❌

None of these work. **Two structures, two indexes, one consistent state.**

### The sync contract

Every write operation (set, delete) must update BOTH structures:

```
set(key, value, ttl):
  1. Remove old expiry from TreeMap if key already exists
  2. Insert new Entry into HashMap
  3. Insert expiry → key into TreeMap

delete(key):
  1. Look up Entry in HashMap to get expiry time
  2. Remove key from that expiry bucket in TreeMap (clean empty bucket)
  3. Remove key from HashMap
```

If you update one and forget the other, the structures diverge → silent bugs that only show up in `countActive()`.

---

### 🎨 Visual — The Two-Structure Relationship

```
WRITE: set("docA", "PDF", 5000ms)  [now = 1000ms, expiry = 6000ms]
WRITE: set("docB", "SIG", 3000ms)  [now = 1000ms, expiry = 4000ms]
WRITE: set("docC", "ZIP", 5000ms)  [now = 1000ms, expiry = 6000ms]

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 STRUCTURE 1: HashMap<String, Entry>
 (Primary store — answers: what is the value/expiry for this key?)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  "docA" → Entry{ value="PDF", expiryTime=6000 }
  "docB" → Entry{ value="SIG", expiryTime=4000 }
  "docC" → Entry{ value="ZIP", expiryTime=6000 }

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 STRUCTURE 2: TreeMap<Long, Set<String>>
 (Expiry index — answers: which keys expire in what range?)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  4000ms → { "docB" }
  6000ms → { "docA", "docC" }

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
 QUERY: countActive() at now = 5000ms
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  expiryIndex.tailMap(5001ms)
     → returns { 6000ms → {"docA","docC"} }
     → sum = 2 entries  ← no scan of HashMap needed

 QUERY: get("docB") at now = 5000ms
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
  HashMap.get("docB") → Entry{ expiryTime=4000 }
  now(5000) >= expiryTime(4000) → EXPIRED
  lazy delete: remove from HashMap + remove from TreeMap bucket
  return null

KEY INVARIANT:
  HashMap answers "what is this key's value/expiry?" in O(1).
  TreeMap answers "how many keys are still alive?" in O(log n).
  Neither can answer the other's question efficiently.
```

---

<a id="style-habits"></a>
## 🎨 Style Habits

### 🌐 Universal (apply in every hybrid design problem)

1. **Always write an inner Entry/Record class** — never use `Object[]` or `Map.Entry<String, long[]>`. The inner class is type-safe, self-documenting, and easy to extend.

2. **The sync contract is sacred** — every write path touches BOTH structures. If you add a note in the code saying "update HashMap" but forget the TreeMap, you will forget the TreeMap under interview pressure.

3. **Handle the key-already-exists case in `set`** — if the key exists with a different TTL and you overwrite it, the OLD expiry must be removed from the TreeMap first or you'll have a ghost entry that inflates `countActive()`.

4. **Clean up empty TreeMap buckets** — after removing a key from a Set in the TreeMap, if the Set becomes empty, remove the bucket entirely. Otherwise memory leaks accumulate over time.

5. **Name `expiryTime`, not `expiryMs` or `ttl`** — the field is the absolute timestamp, not the duration. Naming it `expiryTime` makes `now >= expiryTime → expired` immediately readable.

### 🔧 Context-specific (KV Store with TTL)

6. **Use `System.currentTimeMillis()` as the clock source** — the problem typically says "wall clock". Centralise this in a single method `now()` so you can easily mock it in tests.

7. **Lazy vs eager expiry** — unless the interviewer asks for a background thread, default to lazy. Mention eager as a follow-up: "we could add a `ScheduledExecutorService` that calls `expiryIndex.headMap(now)` and bulk-deletes."

8. **`computeIfAbsent` over `if (!containsKey) put`** — it is atomic and idiomatic Java for building nested structures.

---

<a id="pattern-steps"></a>
## 🧭 The Pattern — Step by Step

### Design checklist when you see "two query types"

**Steps in plain English:**

1. **Identify the two query types.** One will be key-based (HashMap territory), the other will be range/order-based (TreeMap territory).
2. **Define the Entry class** — what fields must travel together per primary key?
3. **Choose the TreeMap key** — what attribute do you need to range-query on? (Expiry time, frequency, priority, etc.)
4. **Write the sync contract** — for every write operation, list which structures get updated.
5. **Handle the "already exists" case** — updating a key means removing the old secondary index entry before inserting the new one.
6. **Clean up empty secondary buckets** — after removing a key from a Set, if the Set is empty, remove the Set from the TreeMap.

---

<a id="walkthrough"></a>
## 🔬 Worked Walkthrough — In-Memory KV Store with TTL

**Problem context:** Design an in-memory key-value store for document signing sessions. Each entry (a signing URL) has a TTL — after which it expires. Support:
- `set(key, value, ttlMs)` — store with expiry
- `get(key)` — return value if alive, null if expired
- `delete(key)` — remove immediately
- `countActive()` — count non-expired keys efficiently

---

### Step 1 — The Entry Inner Class

Before writing any data structures, define how a single record is modeled.

**Why not `Object[]`?**

```java
// ❌ BAD — type-unsafe, unreadable
Object[] entry = store.get("key");
String value = (String) entry[0];   // hope this is index 0
long expiry  = (Long)   entry[1];   // hope this is index 1
```

One transposed index in a late-night interview session = wrong answer. The compiler will not save you.

```java
// ✅ GOOD — type-safe, self-documenting
private static class Entry {
    final String value;
    final long expiryTime;

    Entry(String value, long expiryTime) {
        this.value = value;
        this.expiryTime = expiryTime;
    }
}
```

Now: `entry.value` and `entry.expiryTime` — no ambiguity, no casts, no silent bugs.

> 🧩 **Drill — do this NOW before reading further:**
> On a blank notepad, write the Entry inner class from memory.
> It should have: two `final` fields, a constructor. No getters needed for interview code.
> Check: did you make the fields `final`? Did you write `private static class` not just `class`?

---

### Step 2 — Brute Force (single HashMap, O(n) countActive)

**Discussion:** Start here to show the interviewer you understand the naive approach before jumping to the optimal. The brute force is actually acceptable for small datasets — a Docusign session store with 100 active signers does not need the full optimization.

**Steps in plain English:**

1. Store each `key → Entry(value, expiryTime)` in one HashMap.
2. `get`: check `now >= entry.expiryTime` → expired → lazy delete → return null. Otherwise return value.
3. `delete`: remove from HashMap.
4. `countActive`: scan ALL entries, count those where `entry.expiryTime > now`. O(n).

```java
class KVStoreBrute {

    private static class Entry {
        final String value;
        final long expiryTime;

        Entry(String value, long expiryTime) {
            this.value = value;
            this.expiryTime = expiryTime;
        }
    }

    // Single map — key → (value + expiry) together, no split maps
    private final Map<String, Entry> store = new HashMap<>();

    public void set(String key, String value, long ttlMs) {
        long expiry = System.currentTimeMillis() + ttlMs;
        store.put(key, new Entry(value, expiry));
    }

    public String get(String key) {
        Entry entry = store.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() >= entry.expiryTime) {
            // Lazy expiry — remove on access
            store.remove(key);
            return null;
        }
        return entry.value;
    }

    public void delete(String key) {
        store.remove(key);
    }

    // O(n) — scans all entries every call
    public int countActive() {
        long now = System.currentTimeMillis();
        int count = 0;
        for (Entry entry : store.values()) {
            if (entry.expiryTime > now) {
                count++;
            }
        }
        return count;
    }
}
```

**Why this is slow for `countActive`:** Every call is O(n) regardless of result. On a platform like Docusign with millions of active signing sessions, a frequent "active sessions" dashboard call would scan millions of entries every time.

---

### Step 3 — Optimization Insight

**The question to ask yourself in the interview:** "What property allows `countActive` to avoid a full scan?"

An entry is active if `expiryTime > now`. We need all entries where the expiry timestamp is in the future. This is a **range query on a sorted attribute** — specifically, a `tailMap` query on expiry times.

If we keep a secondary index sorted by expiry time, `tailMap(now + 1)` gives us only the live entries in O(log n). We never touch expired entries.

**The cost of maintaining this index:** Every `set` and `delete` must also update the TreeMap — O(log n) per write. This is a worthwhile trade: writes happen once per session creation, but `countActive` could be called on every dashboard refresh.

---

### 🎨 Visual — Sync Contract for set() When Key Already Exists

```
Scenario: set("docA", "newPDF", 8000ms) when "docA" already exists
  Old entry: Entry{ value="PDF", expiryTime=6000ms }
  Now = 2000ms → newExpiry = 10000ms

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
BEFORE update:
  HashMap:   "docA" → Entry{ "PDF", 6000 }
  TreeMap:   6000 → { "docA", "docC" }

❌ WRONG — blindly overwrite HashMap, forget to fix TreeMap:
  HashMap:   "docA" → Entry{ "newPDF", 10000 }
  TreeMap:   6000  → { "docA", "docC" }   ← GHOST: "docA" at old expiry
             10000 → { "docA" }            ← "docA" counted twice!
  countActive() inflated ✗

✅ CORRECT — three-step sync:
  Step 1: read old entry → oldExpiry = 6000
  Step 2: remove "docA" from TreeMap bucket at 6000
          bucket = {"docA","docC"} → {"docC"}  (not empty, keep bucket)
  Step 3: put new Entry{"newPDF", 10000} in HashMap
  Step 4: add "docA" to TreeMap bucket at 10000

AFTER correct update:
  HashMap:   "docA" → Entry{ "newPDF", 10000 }
  TreeMap:   6000  → { "docC" }            ← "docA" cleanly removed
             10000 → { "docA" }

KEY INVARIANT:
  Every key appears in EXACTLY ONE expiry bucket in the TreeMap at all times.
  Updating a key = remove from old bucket + add to new bucket.
```

---

### Step 4 — Optimal Solution (HashMap + TreeMap, O(log n) countActive)

**Steps in plain English:**

1. **Entry inner class** — same as brute force.
2. **Primary store** — `HashMap<String, Entry>` for O(1) key lookup.
3. **Expiry index** — `TreeMap<Long, Set<String>>` for O(log n) `tailMap`.
4. **Helper** — `removeFromIndex(key, expiryTime)` encapsulates the "remove from bucket + clean empty bucket" logic. Reuse it in `set` and `delete` to avoid code duplication.
5. **set:** If key exists, call `removeFromIndex` with old expiry. Then insert into both structures.
6. **get:** Look up HashMap. If expired, call `delete` (lazy cleanup of both structures). Return null.
7. **delete:** `store.remove(key)` returns the entry. Use its `expiryTime` to call `removeFromIndex`.
8. **countActive:** `expiryIndex.tailMap(now + 1)` → iterate over the returned sub-map and sum set sizes.

```java
class KVStoreOptimal {

    // ── Inner Entry class ─────────────────────────────────────────────────
    private static class Entry {
        final String value;
        final long expiryTime;

        Entry(String value, long expiryTime) {
            this.value = value;
            this.expiryTime = expiryTime;
        }
    }

    // ── Two parallel structures ───────────────────────────────────────────

    // Structure 1: primary store — O(1) lookup by key
    private final Map<String, Entry> store = new HashMap<>();

    // Structure 2: expiry index — O(log n) range query by expiry time
    private final TreeMap<Long, Set<String>> expiryIndex = new TreeMap<>();

    // ── Helper — shared by set() and delete() ─────────────────────────────
    private void removeFromIndex(String key, long expiryTime) {
        Set<String> bucket = expiryIndex.get(expiryTime);
        if (bucket == null) {
            return;
        }
        bucket.remove(key);
        // Clean up empty buckets — prevent memory leak and wasteful tailMap iteration
        if (bucket.isEmpty()) {
            expiryIndex.remove(expiryTime);
        }
    }

    // ── Public API ────────────────────────────────────────────────────────

    public void set(String key, String value, long ttlMs) {
        long newExpiry = System.currentTimeMillis() + ttlMs;

        // If key already exists, remove it from its OLD expiry bucket first
        Entry existing = store.get(key);
        if (existing != null) {
            removeFromIndex(key, existing.expiryTime);
        }

        // Insert into primary store
        store.put(key, new Entry(value, newExpiry));

        // Insert into expiry index
        expiryIndex.computeIfAbsent(newExpiry, k -> new HashSet<>()).add(key);
    }

    public String get(String key) {
        Entry entry = store.get(key);
        if (entry == null) {
            return null;
        }
        if (System.currentTimeMillis() >= entry.expiryTime) {
            // Lazy delete — cleans BOTH structures via delete()
            delete(key);
            return null;
        }
        return entry.value;
    }

    public void delete(String key) {
        // store.remove returns the entry or null if absent
        Entry entry = store.remove(key);
        if (entry != null) {
            removeFromIndex(key, entry.expiryTime);
        }
    }

    // O(log n) to find the tail boundary + O(k) to sum live buckets
    public int countActive() {
        long now = System.currentTimeMillis();
        // tailMap(now + 1): all expiry timestamps strictly greater than now
        Map<Long, Set<String>> liveBuckets = expiryIndex.tailMap(now + 1);
        int count = 0;
        for (Set<String> bucket : liveBuckets.values()) {
            count += bucket.size();
        }
        return count;
    }
}
```

> 🧩 **Drill — do this NOW before reading further:**
> Close this doc. Write the `set()` method from memory.
> Checklist: (1) compute newExpiry, (2) check existing and call removeFromIndex, (3) store.put, (4) expiryIndex.computeIfAbsent.add.
> If you missed step 2, that is the most common interview mistake on this problem.

---

### Step 5 — Full Dry Run

```
All times in ms. Clock advances as shown.

t=0:    set("docA", "contract_v1", 5000)
          newExpiry = 5000
          existing = null (skip removeFromIndex)
          store:   { "docA" → Entry{"contract_v1", 5000} }
          index:   { 5000 → {"docA"} }

t=1000: set("docB", "nda_draft", 3000)
          newExpiry = 4000
          store:   { "docA"→{5000}, "docB"→{4000} }
          index:   { 4000 → {"docB"}, 5000 → {"docA"} }

t=2000: set("docA", "contract_v2", 4000)   ← UPDATE existing key
          existing = Entry{5000}
          removeFromIndex("docA", 5000):
            bucket {5000} → remove "docA" → empty → remove bucket
          newExpiry = 6000
          store:   { "docA"→{6000}, "docB"→{4000} }
          index:   { 4000 → {"docB"}, 6000 → {"docA"} }

t=4500: countActive()
          tailMap(4501) → { 6000 → {"docA"} }
          count = 1   ← "docB" expired at 4000 < 4500

t=4500: get("docB")
          entry.expiryTime = 4000, now = 4500 ≥ 4000 → EXPIRED
          delete("docB"):
            store.remove("docB") → Entry{4000}
            removeFromIndex("docB", 4000) → bucket empty → remove
          index now: { 6000 → {"docA"} }
          return null ✓

t=5000: get("docA")
          entry.expiryTime = 6000, now = 5000 < 6000 → ALIVE
          return "contract_v2" ✓
```

---

<a id="complexity"></a>
## ⏱️ Complexity Table

| Operation | Brute Force | Optimal |
|---|---|---|
| `set` (new key) | O(1) | O(log n) |
| `set` (update key) | O(1) | O(log n) |
| `get` | O(1) | O(log n) lazy |
| `delete` | O(1) | O(log n) |
| `countActive` | O(n) | O(log n + k) |
| Space | O(n) | O(n) |

k = number of distinct expiry buckets still alive (in practice much smaller than n)

---

<a id="gotchas"></a>
## ⚠️ Gotchas — Silent Bug Hall of Fame

### Bug 1 — Forgetting to Remove Old Expiry on Update

**Symptom:** `countActive()` returns inflated numbers over time. Subtle — only shows up when keys are updated, not on initial inserts.

```java
// ❌ WRONG — blindly overwrites HashMap but leaves stale TreeMap entry
store.put(key, new Entry(value, newExpiry));
expiryIndex.computeIfAbsent(newExpiry, k -> new HashSet<>()).add(key);
// "docA" now listed under BOTH old expiry AND new expiry in the TreeMap
```

```java
// ✅ FIX — always remove old expiry first
Entry existing = store.get(key);
if (existing != null) {
    removeFromIndex(key, existing.expiryTime);
}
store.put(key, new Entry(value, newExpiry));
expiryIndex.computeIfAbsent(newExpiry, k -> new HashSet<>()).add(key);
```

---

### Bug 2 — Not Cleaning Up Empty TreeMap Buckets

**Symptom:** Memory leak. After many deletions, the TreeMap retains empty `HashSet` objects. `tailMap` iteration visits them wastefully.

```java
// ❌ WRONG — leaves empty set in TreeMap
bucket.remove(key);
// Missing the cleanup below
```

```java
// ✅ FIX — always clean empty buckets
bucket.remove(key);
if (bucket.isEmpty()) {
    expiryIndex.remove(expiryTime);
}
```

---

### Bug 3 — Using `tailMap(now)` Instead of `tailMap(now + 1)`

**Symptom:** Keys expiring exactly at `now` are counted as active when they should be expired.

```java
// ❌ WRONG — includes entries where expiryTime == now (already dead)
expiryIndex.tailMap(now)
```

```java
// ✅ FIX — strictly greater than now
expiryIndex.tailMap(now + 1)
```

An entry with `expiryTime == now` has `now >= expiryTime` → it IS expired by the `get` check. `tailMap(now)` includes it. `tailMap(now + 1)` excludes it. Consistent.

---

### Bug 4 — Calling delete() on an Absent Key

**Symptom:** NullPointerException when `store.remove(key)` returns null.

```java
// ❌ WRONG — assumes entry always exists
Entry entry = store.remove(key);
removeFromIndex(key, entry.expiryTime);  // NPE if key was not in store
```

```java
// ✅ FIX — null check
Entry entry = store.remove(key);
if (entry != null) {
    removeFromIndex(key, entry.expiryTime);
}
```

---

### Bug 5 — Using Object[] Instead of Entry Class (Design Bug)

Not a runtime crash — a maintenance bomb. Object[] means casting everywhere and index-position coupling that breaks the moment a follow-up adds a third field.

```java
// ❌ WRONG — fragile, unreadable
Object[] entry = store.get(key);
long expiry = (Long) entry[1];  // what if follow-up adds lastAccessTime at [1]?
```

```java
// ✅ FIX — extend the class, compile-time safety guaranteed
private static class Entry {
    final String value;
    final long expiryTime;
    long lastAccessTime;  // easy to add, named, typed, compiler-enforced
}
```

---

> **Lesson learned the hard way (Jul 2026):** In the first draft of this note, `Object[]` was used for the primary store in both brute force and optimal. The `(Long)` cast on index 1 would have been a silent footgun in any follow-up asking to "add a third field." The Entry class costs 5 lines and pays back every time.

---

<a id="practice-plan"></a>
## 🗺️ Practice Plan

### Tier 1 — Directly apply this pattern

> 🧩 **Try these (build the muscle first):**
> - ✅ LC 981 Time Based Key-Value Store — same dual-structure pattern. No `countActive`, so simpler first attempt. HashMap + TreeMap with `floorKey`.
> - ✅ LC 380 Insert Delete GetRandom O(1) — HashMap + ArrayList for dual access patterns. Different secondary structure, same concept.
> - 🟡 In-Memory KV Store with TTL (this problem) — attempt after reading this doc fully, then code it from memory.

### Tier 2 — Extend the pattern

> 🧩 **Try after Tier 1:**
> - 🟡 LC 146 LRU Cache — HashMap + Doubly Linked List. Dual structure with O(1) for both get and eviction order.
> - 🟡 LC 1670 Design Front Middle Back Queue — dual Deque for position-based access.
> - 🔴 LC 460 LFU Cache — HashMap + TreeMap (frequency → list). More complex secondary key. Attempt only after LC 146 is comfortable.

### Tier 3 — Advanced / Reference Only

> 🧩 **Reference only (read editorial, don't attempt cold):**
> - 🔴 LC 295 Find Median from Data Stream — two heaps (max-heap + min-heap). Same "two structures, two access patterns" philosophy but the secondary structure is a heap.
> - 🔴 LC 352 Data Stream as Disjoint Intervals — TreeMap for merge-by-range queries. Complex merge logic on top of the dual-structure base.

---

<a id="tldr"></a>
## 🧾 TL;DR — One-Page Summary

**The signal:** A problem has two query types — one needs O(1) key lookup, the other needs O(log n) sorted range access.

**The solution:** Two parallel structures:
- `HashMap<String, Entry>` — answers key-based point queries in O(1)
- `TreeMap<Long, Set<String>>` — answers time/range queries in O(log n)

**The Entry class:** Always write an inner class. Never `Object[]`. Compile-time type safety, zero casting, easy to extend with a third field.

**The sync contract:** Every write (set, delete) touches BOTH structures. Missing either one creates ghost entries that silently corrupt `countActive`.

**The three things to get right:**

| Thing | What to do |
|---|---|
| `set` with existing key | Remove old expiry from TreeMap FIRST |
| `delete` | Null-check the returned entry before removing from index |
| `countActive` | Use `tailMap(now + 1)` not `tailMap(now)` |

**Follow-up answers at a glance:**

| Follow-up | Answer |
|---|---|
| Thread safety | `ConcurrentHashMap` + `ReentrantReadWriteLock` on TreeMap |
| Sliding TTL (refresh on get) | On live `get`, call `set(key, value, originalTtl)` again |
| Background eviction | `ScheduledExecutorService` calling `expiryIndex.headMap(now)` and bulk-deleting |
| Third field (e.g. last-access-time) | Add it to the Entry class — zero structural change |

---

<a id="changelog"></a>
## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 2026 | **File created.** In-Memory KV Store with TTL deep dive. Triggered by Docusign interview confirmation (friend's Q2, Jul 2026). Covers Entry class design, dual-structure rationale, sync contract, gotchas, and practice plan. Previous stub content replaced with full deep dive. |
