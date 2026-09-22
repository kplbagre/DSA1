# ☕ HashMap Internals — Deep Dive

> After this note you can trace a `put("key", value)` call from the hash function through bucket selection, collision handling, treeification, and resize — and explain why a HashMap with a broken `hashCode()` degrades from O(1) to O(n).

---

## 🎯 The Problem This Solves

You have a million product records. A user searches by product ID. You need to find the matching record. With a list, you scan every entry — O(n). With a million records, that's a million comparisons per lookup. On a server handling 10,000 requests/second, that's 10 billion comparisons per second. The server melts.

What you need is a data structure that answers "give me the value for this key" in O(1) — constant time, regardless of whether there are 10 entries or 10 million. HashMap is Java's answer to that need. But the O(1) is not magic — it comes from a specific internal mechanism (hashing + bucketing) that has specific failure modes. Understanding the mechanism is what lets you use it safely and debug it when it breaks.

---

## 📖 Terminology

| Term | Definition |
|---|---|
| **Hash function** | A function that converts a key of any size into a fixed-size integer (the hash code). Same input always produces the same output. Different inputs *usually* produce different outputs — but not always. |
| **Hash code** | The integer returned by `key.hashCode()`. In Java, it's a 32-bit `int` (range: −2,147,483,648 to 2,147,483,647). |
| **Bucket** | A slot in HashMap's internal array. Each bucket holds zero or more entries. The hash code determines which bucket a key belongs to. |
| **Capacity** | The number of buckets in the internal array. Always a power of 2 in Java's HashMap (16, 32, 64, …). Default: 16. |
| **Load factor** | The threshold ratio of `size / capacity` that triggers a resize. Default: 0.75. When `size > capacity × loadFactor`, the array doubles and all entries are rehashed. |
| **Collision** | When two different keys produce hash codes that map to the same bucket. Collisions are expected and handled — they are not errors. |
| **Chaining** | The collision-resolution strategy HashMap uses: multiple entries in the same bucket are stored as a linked list (or a tree, after Java 8). |
| **Treeification** | The Java 8 optimization: when a single bucket's chain exceeds 8 nodes (and total capacity ≥ 64), the linked list is converted to a red-black tree. Lookup in that bucket goes from O(n) to O(log n). |
| **Rehash** | The process of recalculating every entry's bucket index when the internal array doubles in size. Every existing entry is re-placed — some stay in the same index, some move to `oldIndex + oldCapacity`. |
| **Node** | The internal object that holds one key-value pair in HashMap. Contains: `int hash`, `K key`, `V value`, `Node<K,V> next` (pointer to the next node in the chain). |
| **TreeNode** | The node type used after treeification. Extends `Node` but adds left/right/parent pointers and a red/black color flag — the structure of a red-black tree. |
| **Perturbation (spread function)** | HashMap does NOT use `hashCode()` directly as the bucket index. It XORs the hashCode's upper 16 bits into its lower 16 bits (`h ^ (h >>> 16)`) to spread entries more evenly across buckets. |

---

## 🧠 Mental Model

Think of HashMap as a **row of numbered mailboxes** (the bucket array). When you want to store a letter (key-value pair), you don't pick a random mailbox — you run the recipient's name through a formula (the hash function) that outputs a mailbox number. You put the letter in that mailbox. When someone asks for the letter, you run the same name through the same formula, get the same mailbox number, and pull the letter out. That's O(1) — you never search through all the mailboxes.

The complication: two different names can produce the same mailbox number (collision). When that happens, the mailbox holds a short chain of letters. To find the right one, you check each letter's name tag using `equals()`. As long as chains stay short (1–2 entries), this is still effectively O(1). If the hash function is terrible and everything lands in one mailbox, you're back to scanning a list — O(n). The entire performance contract of HashMap rests on the hash function distributing keys evenly across buckets.

> If you can say "HashMap is an array of buckets; the hash function picks the bucket; collisions chain within a bucket; Java 8 converts chains longer than 8 to red-black trees; the array doubles when 75% full" without notes, you have HashMap.

---

## 🎨 Visual — HashMap Internal Structure

```
  HashMap internal array (capacity = 8 buckets, shown as indices 0–7):

  Index │ Contents
  ──────┼──────────────────────────────────────────────────────────
    0   │ null
    1   │ [Node: "apple"→5] → [Node: "grape"→3] → null
    2   │ null
    3   │ [Node: "banana"→7] → null
    4   │ null
    5   │ [Node: "cherry"→2] → [Node: "date"→9] → ... 8 more
    6   │                                           → [TreeNode root]
    7   │ null                                          (treeified)

  HOW A KEY REACHES ITS BUCKET:

  key.hashCode()          →  raw 32-bit int (e.g., 2043891789)
       │
       ▼
  h ^ (h >>> 16)          →  perturbation: mix upper bits into lower bits
       │
       ▼
  hash & (capacity - 1)   →  bucket index (bitwise AND, not modulo)
                              capacity is always power of 2, so
                              (capacity - 1) is a bitmask of all 1s
                              Example: capacity=8 → mask=0b0111 → index 0–7

  WHY POWER OF 2:
  hash % capacity requires integer division (slow).
  hash & (capacity - 1) is a single CPU instruction (fast).
  They produce the same result ONLY when capacity is a power of 2.
  That's why HashMap enforces power-of-2 capacity — not for aesthetics,
  but because bitwise AND is faster than modulo.

KEY INVARIANT:
   The bucket index is deterministic: same key → same hashCode →
   same perturbation → same bucket index. This is what makes
   get() find the entry that put() stored — without searching.
```

---

## 🎨 Visual — Resize (Rehash) Process

```
  BEFORE RESIZE: capacity = 4, loadFactor = 0.75, threshold = 3
  Size is about to exceed 3 → resize triggered.

  Old array (capacity 4):                New array (capacity 8):
  ┌───────────────────────┐              ┌───────────────────────┐
  │ 0: [A hash=4] → null  │              │ 0: [A hash=4] → null  │
  │ 1: [B hash=5]→[C h=9] │              │ 1: [B hash=5] → null  │
  │ 2: null                │              │ 2: null                │
  │ 3: [D hash=7] → null  │              │ 3: [D hash=7] → null  │
  └───────────────────────┘              │ 4: null                │
                                         │ 5: [C hash=9] → null  │  ← C moved
                                         │ 6: null                │
                                         │ 7: null                │
                                         └───────────────────────┘

  HOW ENTRIES MOVE:
  Old mask: capacity 4 → mask 0b011 → A(hash=4): 4 & 0b011 = 0
  New mask: capacity 8 → mask 0b111 → A(hash=4): 4 & 0b111 = 4... wait.

  Actually: A stays at index 0 if the NEW bit (bit 2) is 0.
            C moves to oldIndex + oldCapacity if the NEW bit is 1.

  Java 8 optimized rehash: no need to recompute hash.
  Just check ONE extra bit — the bit that became relevant
  when capacity doubled. O(n) total work, but MUCH simpler
  than recomputing every hash.

KEY INVARIANT:
   After resize, every entry is in exactly one of two places:
   its old index, or old index + old capacity.
   This is a consequence of power-of-2 sizing.
```

---

## 🪜 Build-up

---

### Level 1 — The naive approach (and why it fails)

The most natural key-value store is a list of pairs:

```java
// Tier 1 — Demo: naive key-value store using ArrayList
List<Map.Entry<String, Integer>> store = new ArrayList<>();

// put
store.add(Map.entry("apple", 5));
store.add(Map.entry("banana", 7));
store.add(Map.entry("cherry", 2));
// ... 999,997 more entries

// get — must scan every entry
public Integer naiveGet(String key) {
    for (Map.Entry<String, Integer> entry : store) {
        if (entry.getKey().equals(key)) {
            return entry.getValue();
        }
    }
    return null;
}
// ⚠️ NOT thread-safe — ConcurrentModificationException if another thread modifies during iteration
```

This works. It's correct. And it's O(n) per lookup.

**The performance cliff:**

| Entries | Naive list lookup | HashMap lookup |
|---|---|---|
| 100 | ~50 comparisons avg | ~1 comparison |
| 10,000 | ~5,000 comparisons avg | ~1 comparison |
| 1,000,000 | ~500,000 comparisons avg | ~1 comparison |

At 10,000 requests/second with 1 million entries, the naive list does ~5 billion comparisons per second. HashMap does ~10,000. That's the difference between a server that responds in milliseconds and one that falls over.

The question is: how does HashMap achieve O(1)? The answer is the hash function — a way to compute the *exact location* of a key instead of searching for it.

---

### Level 2 — The real mechanism

#### 2.1 — The internal array

At its core, HashMap is a `Node<K,V>[] table` — an array. When you create `new HashMap<>()`, the array is NOT allocated yet (lazy initialization — it's created on the first `put()`). When it is created, the default capacity is **16 buckets**.

```java
// Simplified from java.util.HashMap source (JDK 21)
// This is what HashMap actually is — an array of linked-list heads
transient Node<K,V>[] table;
transient int size;           // number of key-value pairs currently stored
int threshold;                // = capacity × loadFactor — resize trigger
final float loadFactor;       // default 0.75

static class Node<K,V> implements Map.Entry<K,V> {
    final int hash;           // precomputed, perturbed hash
    final K key;
    V value;
    Node<K,V> next;           // pointer to next node in the chain (null if last)
}
// ⚠️ NOT thread-safe — concurrent put() can cause infinite loop (Java 7) or lost updates (Java 8+)
```

> **What the JVM is actually doing:** Each `Node` is a separate object on the heap — 32 bytes on a 64-bit JVM with compressed oops (12-byte header + 4 hash + 4 key ref + 4 value ref + 4 next ref + 4 padding). A HashMap with 1 million entries has at least 1 million Node objects on the heap, plus the array object itself. This is why HashMap has higher memory overhead than a flat array — the chaining structure requires per-entry object allocation. Each Node is independently GC-eligible when removed from the map.

#### 2.2 — The `put()` flow

When you call `map.put("apple", 5)`, here is what happens step by step:

**Steps in plain English:**

1. **Compute the hash** — call `"apple".hashCode()`, then perturb it by XORing upper and lower 16 bits.
2. **Find the bucket** — use `hash & (capacity - 1)` to get the array index.
3. **Check if the bucket is empty** — if yes, create a new `Node` and place it there. Done.
4. **If the bucket is occupied** — walk the chain. For each node, check: does `hash` match AND does `key.equals(existingKey)` return true?
5. **If a matching key is found** — overwrite the value. Return the old value.
6. **If no matching key is found** — append a new `Node` at the end of the chain.
7. **After insertion, check size** — if `size > threshold`, call `resize()` to double the array and rehash all entries.
8. **Check treeification** — if the chain in this bucket now has > 8 nodes AND capacity ≥ 64, convert the chain from a linked list to a red-black tree.

```java
// Tier 1 — Demo: tracing a put() call
Map<String, Integer> map = new HashMap<>();   // table = null (lazy init)

map.put("apple", 5);
// Step 1: "apple".hashCode() → 93029210
// Step 2: hash = 93029210 ^ (93029210 >>> 16) → perturbed hash
// Step 3: index = perturbedHash & (16 - 1) → some index 0–15
// Step 4: table[index] is null → create Node("apple", 5), place it
// Step 5: size = 1, threshold = 12 (16 × 0.75) → no resize needed

map.put("apple", 99);
// Same key → same hash → same bucket → walks chain → finds "apple" via equals()
// Overwrites value: 5 → 99. Returns old value 5.
// size stays 1 (no new entry added)
// ⚠️ NOT thread-safe — two threads calling put() simultaneously can lose one update
```

#### 2.3 — The `get()` flow

`get()` is the mirror of `put()`:

1. Compute hash (same perturbation).
2. Find bucket index (same `hash & (capacity - 1)`).
3. Walk the chain at that bucket.
4. For each node: if `hash == node.hash && (key == node.key || key.equals(node.key))`, return `node.value`.
5. If chain is exhausted without a match, return `null`.

**Critical detail:** HashMap checks `hash == node.hash` BEFORE calling `equals()`. This is an optimization — `int` comparison is a single CPU instruction, while `equals()` may involve comparing every character of two strings. If the hashes don't match, we skip `equals()` entirely. This is why hash distribution matters so much: good distribution means fewer `equals()` calls.

**Another critical detail:** `key == node.key` (identity check) is tested before `key.equals(node.key)` (equality check). If the key object IS the same reference, we skip the `equals()` call. This is a micro-optimization for the common case where the same key object is used for both put and get.

#### 2.4 — The perturbation (spread) function

Why doesn't HashMap just use `hashCode()` directly?

```java
// What HashMap actually computes (JDK 8+, unchanged in 21):
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
}
```

The bucket index is `hash & (capacity - 1)`. If capacity is 16, only the **lowest 4 bits** of the hash matter. A hashCode that varies only in its upper bits — perfectly legal — would put every key in the same bucket. The perturbation XORs the upper 16 bits into the lower 16 bits, ensuring that variation in any part of the hashCode affects the bucket index.

```
  hashCode:         1010 0011  1100 0101  0000 0000  0000 0111
  hashCode >>> 16:  0000 0000  0000 0000  1010 0011  1100 0101
  XOR result:       1010 0011  1100 0101  1010 0011  1100 0010
                                          ^^^^^^^^^^^^^^^^^^^^^^^^
                                          These lower bits now carry
                                          information from ALL 32 bits
```

#### 2.5 — Collision handling and treeification

When multiple keys map to the same bucket, they form a chain. In **Java 7**, this was always a linked list — O(n) traversal in the worst case.

**Java 8 changed this:** when a single bucket's chain exceeds **8 nodes** (the `TREEIFY_THRESHOLD`) AND the total table capacity is ≥ 64 (the `MIN_TREEIFY_CAPACITY`), the linked list is converted to a **red-black tree**. Worst-case lookup in that bucket goes from O(n) to O(log n).

```
  BEFORE Java 8 (linked list only):
  Bucket 5: [A] → [B] → [C] → [D] → [E] → [F] → [G] → [H] → [I]
  Looking up I requires 9 comparisons. O(n) in the chain length.

  AFTER Java 8 (treeified):
  Bucket 5:
              [E]
             /   \
           [C]   [G]
          / \    / \
        [B] [D] [F] [H]
        /             \
      [A]             [I]
  Looking up I requires 4 comparisons. O(log n) in the chain length.
```

**Why the capacity ≥ 64 guard?** If the table is small (e.g., capacity 16) and 9 keys collide in one bucket, the right fix is to resize (double the table), not to treeify. Treeification is a last resort for when resizing can't fix the collision problem — i.e., the keys genuinely have clashing hashes even in a large table.

**Untreeification:** when a resize causes a treeified bucket to shrink below **6 nodes** (the `UNTREEIFY_THRESHOLD`), the tree is converted back to a linked list. The gap between 8 (treeify) and 6 (untreeify) prevents thrashing between the two structures when the count hovers near the threshold.

> **What the JVM is actually doing:** Treeification replaces `Node` objects with `TreeNode` objects. `TreeNode` extends `Node` but adds `left`, `right`, `parent` pointers and a `boolean red` flag — roughly doubling the per-node memory from ~32 bytes to ~56 bytes. This is why treeification is a last resort, not a default: it doubles memory per entry in that bucket. The `TreeNode` objects are regular heap objects and become GC-eligible when the entry is removed or the tree is untreeified.

#### 2.6 — Resize (rehash)

When `size > threshold` (default threshold = `capacity × 0.75` = 12 for the initial capacity of 16), HashMap allocates a new array with double the capacity and rehashes every entry.

**The concrete numbers for default settings:**

| Resize # | Old capacity | New capacity | Triggered at size | New threshold |
|---|---|---|---|---|
| 1 | 16 | 32 | 13 | 24 |
| 2 | 32 | 64 | 25 | 48 |
| 3 | 64 | 128 | 49 | 96 |
| 4 | 128 | 256 | 97 | 192 |
| ... | ... | ... | ... | ... |
| 20 | 8,388,608 | 16,777,216 | ~6.3M | ~12.6M |

**Maximum capacity:** 2^30 = 1,073,741,824 buckets. After this, HashMap sets threshold to `Integer.MAX_VALUE` and never resizes again — collisions accumulate.

**Java 8 resize optimization:** In Java 7, rehash recomputed every entry's bucket index from scratch. Java 8 introduced a clever trick: since capacity always doubles (power of 2), each entry either stays at its old index or moves to `oldIndex + oldCapacity`. Which one depends on a single bit — the bit that was "above" the old mask and is now "inside" the new mask. This avoids recomputing hashes entirely.

> **What the JVM is actually doing:** Resize allocates a new `Node[]` on the heap (double the size), then walks every entry in the old array, re-linking each Node into the new array. The old array object becomes GC-eligible immediately after the resize completes. During resize, HashMap is in an inconsistent state — this is one reason concurrent access without synchronization can corrupt the data structure. In Java 7, concurrent resize could create a cycle in the linked list, causing `get()` to loop forever (infinite loop — not just wrong data, but a hang). Java 8 eliminated that specific bug by changing the resize algorithm, but concurrent access is still unsafe.

#### 2.7 — null key handling

HashMap allows **one null key** (unlike ConcurrentHashMap, which allows neither null keys nor null values). The null key always maps to bucket index 0 — the `hash()` function returns 0 for a null key.

```java
// From HashMap.hash():
static final int hash(Object key) {
    int h;
    return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
    //     ^^^^^^^^^^^^^^
    //     null key → hash 0 → bucket 0
}
```

This is a design decision: HashMap was designed for general-purpose single-threaded use where null is a legitimate value. ConcurrentHashMap was designed for concurrent use where null is ambiguous — does `get(key) == null` mean "key is absent" or "key is present with value null"? In a concurrent context, you can't distinguish these two cases without additional synchronization, so ConcurrentHashMap prohibits null to eliminate the ambiguity.

---

### Level 3 — The subtleties

#### 3.1 — Initial capacity and `tableSizeFor()`

If you pass a non-power-of-2 initial capacity, HashMap rounds UP to the nearest power of 2:

```java
// Tier 1 — Demo: capacity rounding
Map<String, Integer> map = new HashMap<>(10);
// You asked for 10. You get 16.
// tableSizeFor(10) → 16 (next power of 2 ≥ 10)

Map<String, Integer> map2 = new HashMap<>(17);
// You asked for 17. You get 32.
// ⚠️ NOT thread-safe
```

**The implementation** uses a clever bit-manipulation trick — spreading the highest set bit rightward, then adding 1:

```java
// From JDK 21 — HashMap.tableSizeFor(int cap)
static final int tableSizeFor(int cap) {
    int n = -1 >>> Integer.numberOfLeadingZeros(cap - 1);
    return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
}
```

**Practical implication:** if you know you'll store 1,000 entries, pass `new HashMap<>(1334)` (which is `1000 / 0.75`, rounded up). This gives capacity 2048 and prevents ANY resize during the 1,000 insertions. Passing `new HashMap<>(1000)` gives capacity 1024, threshold 768 — you'll resize at entry 769, which wastes time and memory mid-population.

The ideal formula: `initialCapacity = (int) (expectedSize / loadFactor) + 1`.

**Java 19+ shortcut:** `HashMap.newHashMap(expectedSize)` does this calculation for you.

#### 3.2 — Iteration order is NOT guaranteed

HashMap does not maintain insertion order, access order, or sorted order. The iteration order depends on the bucket index of each entry, which depends on the hash code, which depends on the key's `hashCode()` implementation. Adding or removing entries can change the iteration order of remaining entries (because resizing moves entries between buckets).

```java
// Tier 1 — Demo: iteration order is unpredictable
Map<String, Integer> map = new HashMap<>();
map.put("one", 1);
map.put("two", 2);
map.put("three", 3);
map.put("four", 4);

// Printing order might be: four, one, two, three
// NOT: one, two, three, four (that's insertion order — use LinkedHashMap)
// NOT: four, one, three, two (that's sorted — use TreeMap)
for (Map.Entry<String, Integer> e : map.entrySet()) {
    System.out.println(e.getKey());
}
// ⚠️ NOT thread-safe — ConcurrentModificationException if modified during iteration
```

**If you need insertion order:** `LinkedHashMap` — maintains a doubly-linked list through all entries, in insertion order. O(1) get/put (same as HashMap) but slightly higher memory per entry (two extra pointers: `before` and `after`).

**If you need sorted order:** `TreeMap` — red-black tree, O(log n) get/put, keys sorted by natural order or a `Comparator`.

#### 3.3 — `modCount` and fail-fast iterators

HashMap tracks a `modCount` (modification count) that increments on every structural change (put, remove, clear — but NOT on `put()` that only overwrites an existing value). When you create an iterator, it snapshots `modCount`. On every `next()` call, the iterator checks: did `modCount` change since the snapshot? If yes → `ConcurrentModificationException`.

```java
// Tier 1 — Demo: ConcurrentModificationException
Map<String, Integer> map = new HashMap<>();
map.put("a", 1);
map.put("b", 2);
map.put("c", 3);

// ❌ This throws ConcurrentModificationException
for (String key : map.keySet()) {
    if (key.equals("b")) {
        map.remove(key);   // structural modification during iteration
    }
}

// ✅ Use the iterator's own remove() method
Iterator<String> it = map.keySet().iterator();
while (it.hasNext()) {
    if (it.next().equals("b")) {
        it.remove();   // safe — iterator updates its own state
    }
}

// ✅ Or use removeIf (Java 8+)
map.keySet().removeIf(key -> key.equals("b"));
// ⚠️ NOT thread-safe — this fail-fast mechanism catches SINGLE-thread bugs;
//    it does NOT reliably detect multi-thread concurrent modification
```

**Important:** fail-fast is a *best-effort* detection mechanism, not a guarantee. It catches the most common case (single-thread modification during iteration) but can miss concurrent modifications from other threads. It is NOT a thread-safety mechanism — it's a bug-detection aid.

#### 3.4 — The `equals()` / `hashCode()` dependency

HashMap's correctness depends on this contract:

1. If `a.equals(b)` is true, then `a.hashCode() == b.hashCode()` MUST be true.
2. If `a.hashCode() != b.hashCode()`, then `a.equals(b)` MUST be false (contrapositive of rule 1).
3. If `a.hashCode() == b.hashCode()`, `a.equals(b)` CAN be true or false (hash collision).

If rule 1 is violated — two equal objects have different hash codes — `put()` stores the entry in bucket X, but `get()` looks in bucket Y. The entry is present but unfindable. This is a silent data loss bug — no exception, no error message, just a `null` return from `get()` for a key you definitely stored.

Full deep-dive on this contract is in `equals-hashcode-contract.md` (planned — Note #2 in the roadmap).

#### 3.5 — HashMap vs Hashtable vs ConcurrentHashMap

| Property | HashMap | Hashtable | ConcurrentHashMap |
|---|---|---|---|
| Thread-safe | ❌ No | ✅ Yes (full lock) | ✅ Yes (CAS + per-bin lock) |
| Null key | ✅ One null key allowed | ❌ No | ❌ No |
| Null value | ✅ Allowed | ❌ No | ❌ No |
| Performance (concurrent) | N/A (unsafe) | Poor (one global lock) | Good (fine-grained) |
| Iteration | Fail-fast | Fail-fast | Weakly consistent (no CME) |
| Java version | 1.2 | 1.0 (legacy) | 1.5 |

**Hashtable is legacy.** Never use it in new code. If you need thread safety, use `ConcurrentHashMap`. If you need a synchronized wrapper of HashMap (e.g., for tests), use `Collections.synchronizedMap(new HashMap<>())` — but know that it uses a single global lock, same as Hashtable. `ConcurrentHashMap` uses per-bin (per-bucket) locking with CAS operations in Java 8+ — far better concurrency.

---

## ⚠️ Common Misconceptions

| You might think... | But actually... |
|---|---|
| "HashMap uses `hashCode()` directly as the bucket index" | HashMap perturbs the hash: `h ^ (h >>> 16)`. It XORs upper bits into lower bits to improve distribution. Then uses `hash & (capacity - 1)` — bitwise AND, not modulo. |
| "Collisions mean the hash function is broken" | Collisions are mathematically inevitable (pigeonhole principle: 2^32 possible hashCodes, but far more possible keys). HashMap is *designed* to handle collisions — chains and trees exist for this purpose. A good hash function minimizes collisions; it can never eliminate them. |
| "HashMap capacity is the same as size" | Capacity = number of buckets (the array length). Size = number of key-value pairs stored. A HashMap with capacity 16 and size 5 has 16 buckets, 11 of which are empty. |
| "Resizing just adds more buckets" | Resizing doubles the array AND rehashes every existing entry. Every entry's bucket index may change because the mask width increased by 1 bit. This is O(n) work — proportional to the number of entries, not just the number of new buckets. |
| "HashMap is slow because of collisions" | With a good hash function and default load factor 0.75, the average chain length is < 1. Most buckets have 0 or 1 entries. Lookup is O(1) amortized. HashMap becomes slow only when the hash function is pathologically bad (many collisions in the same bucket) or when the load factor is set too high. |
| "Java 8 treeification means HashMap is always O(log n) worst case" | Treeification only triggers when a SINGLE bucket exceeds 8 entries AND capacity ≥ 64. If your hash function distributes well, no bucket ever reaches 8 entries. Treeification is a safety net, not a normal path. |

---

## 🐞 Production Footguns

---

> **Footgun: Mutable key corruption**
> **Cost:** Silent data corruption
>
> In a caching layer for a product catalog API, a developer used a mutable `Product` object as a HashMap key. After storing the entry, another part of the code modified the product's `name` field — which was used in `hashCode()`. The hash code changed, so `get()` looked in the wrong bucket. The cached product was present in the map but unfindable. The API returned null for cached products, causing unnecessary database hits and doubled latency under load.

```java
// ❌ The trap: mutable key
public class Product {
    private String name;
    private int price;

    // hashCode uses name
    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (!(o instanceof Product p)) { return false; }
        return Objects.equals(name, p.name);
    }

    // Setter exists — key is mutable
    public void setName(String name) {
        this.name = name;
    }
}

Map<Product, Double> priceCache = new HashMap<>();
Product p = new Product("Widget", 100);
priceCache.put(p, 9.99);

p.setName("Gadget");   // hashCode changes silently
priceCache.get(p);     // returns null — entry exists but is in the old bucket
priceCache.containsKey(p);   // false — same reason
// The entry is ORPHANED — it can never be found or removed by key
// It stays in memory until the map is cleared or GC'd

// ✅ The fix: immutable key (Java 16+ record)
public record ProductKey(String name) {}
// Records are immutable — fields are final, no setters
// hashCode() and equals() are auto-generated from all fields
// Safe to use as HashMap key

Map<ProductKey, Double> safeCache = new HashMap<>();
safeCache.put(new ProductKey("Widget"), 9.99);
// ProductKey has no setter — key can never change after creation
// ⚠️ NOT thread-safe — use ConcurrentHashMap if shared across threads
```

---

> **Footgun: Unbounded HashMap as memory leak**
> **Cost:** OOM (OutOfMemoryError)
>
> In a Kafka consumer service processing 700K messages/minute, each message's correlation ID was stored in a `HashMap<String, Instant>` for deduplication tracking. Entries were added on every message but never removed — no TTL, no eviction, no size cap. After 48 hours of continuous operation, the map held ~2 billion entries, consuming ~80 GB of heap. The JVM spent 90%+ time in GC (GC thrashing), latency spiked to 30+ seconds, and the service was killed by the orchestrator's health check.

```java
// ❌ The trap: unbounded map in a long-running service
public class DeduplicationService {
    // This map grows forever — no eviction
    private final Map<String, Instant> seen = new HashMap<>();

    public boolean isDuplicate(String correlationId) {
        if (seen.containsKey(correlationId)) {
            return true;
        }
        seen.put(correlationId, Instant.now());
        return false;
    }
    // After 48 hours at 700K msg/min → ~2 billion entries → OOM
}

// ✅ The fix: bounded map with eviction
// Option A: LinkedHashMap with removeEldestEntry (simplest)
public class BoundedDeduplicationService {
    private static final int MAX_SIZE = 1_000_000;

    private final Map<String, Instant> seen = new LinkedHashMap<>(
        MAX_SIZE * 4 / 3 + 1,  // initial capacity to avoid resizing
        0.75f,                  // load factor
        true                   // accessOrder = true (LRU eviction)
    ) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Instant> eldest) {
            return size() > MAX_SIZE;
        }
    };

    public boolean isDuplicate(String correlationId) {
        if (seen.containsKey(correlationId)) {
            return true;
        }
        seen.put(correlationId, Instant.now());
        return false;
    }
}
// ⚠️ NOT thread-safe — wrap in Collections.synchronizedMap() or use ConcurrentHashMap
//    with a separate eviction mechanism (Caffeine cache is the production choice)
```

---

> **Footgun: HashMap in Spring singleton without synchronization**
> **Cost:** Race condition / data corruption
>
> In a Spring Boot microservice, a `@Service` bean (singleton by default) used a plain `HashMap` to cache configuration values loaded at startup. Under load, two request-handling threads called `put()` simultaneously during a cache refresh. In Java 7, this caused an infinite loop in the linked list (the resize algorithm created a cycle). In Java 8+, the infinite loop was fixed, but concurrent `put()` can still lose entries silently — one thread's write is overwritten by the other's, with no exception.

```java
// ❌ The trap: HashMap in a Spring singleton
@Service
public class ConfigCacheService {
    // Singleton bean — shared by ALL request threads
    private final Map<String, String> configCache = new HashMap<>();

    public void refreshCache() {
        // Multiple threads can call this concurrently
        configCache.clear();
        configCache.put("timeout", "30");
        configCache.put("retries", "3");
        // If two threads hit this simultaneously:
        //   - Java 7: infinite loop in resize → thread hangs forever
        //   - Java 8+: lost update — one thread's put() silently overwrites
    }

    public String getConfig(String key) {
        return configCache.get(key);
    }
}

// ✅ The fix: ConcurrentHashMap
@Service
public class ConfigCacheService {
    private final Map<String, String> configCache = new ConcurrentHashMap<>();
    // ✅ Thread-safe — uses CAS + per-bin locking; reads are lock-free

    public void refreshCache() {
        // ConcurrentHashMap handles concurrent writes safely
        configCache.put("timeout", "30");
        configCache.put("retries", "3");
    }

    public String getConfig(String key) {
        return configCache.get(key);
    }
}
```

---

## 🔗 Concept Web

| Connects to | How |
|---|---|
| `equals-hashcode-contract.md` (planned — Note #2) | HashMap.get() calls `hashCode()` to find the bucket, then `equals()` to find the key within the bucket. If the equals/hashCode contract is broken, HashMap silently loses entries. This is the most critical dependency HashMap has. |
| `concurrent-collections.md` (planned — Phase 4, Note #18) | ConcurrentHashMap is the thread-safe evolution of HashMap. Its internal structure (CAS + per-bin synchronized) only makes sense after understanding HashMap's bucket array and chaining model. |
| `string-internals.md` (planned — Note #6) | `String.hashCode()` is the most common hashCode implementation used with HashMap. String caches its hashCode (computed once, stored in a field) — this is why String is an efficient HashMap key. |
| `java-pass-by-value-semantics.md` | HashMap values are object references. `map.get(key)` returns a reference to the same object — mutations propagate. Understanding pass-by-value explains why modifying a retrieved value changes the "stored" value. |
| `../Spring/DeepDive/02-spring-core.md` | Spring's `DefaultSingletonBeanRegistry` stores beans in a `ConcurrentHashMap`. Spring's `DefaultListableBeanFactory` uses multiple `HashMap`/`ConcurrentHashMap` instances for bean definitions, singleton caches, and dependency resolution. Understanding HashMap internals explains why Spring bean lookup is O(1). |
| `../../DSA/DeepDive/trees-fundamentals.md` | HashMap treeifies bucket chains with > 8 entries using a red-black tree — the same balanced BST type covered in the DSA trees fundamentals note. Understanding red-black trees explains why treeified bucket lookup is O(log n). |

---

## 🎙️ Interview Deep Questions

**Q1. Walk me through what happens internally when you call `map.put("hello", 42)` on a HashMap.**

> HashMap first computes `"hello".hashCode()`, then perturbs the result by XORing the upper 16 bits into the lower 16 bits — `h ^ (h >>> 16)`. This spreads hash bits so that keys whose hashCodes differ only in upper bits still land in different buckets. The bucket index is then `perturbedHash & (capacity - 1)` — a bitwise AND, not modulo, which is why capacity must be a power of 2. If the bucket is empty, a new `Node` object (holding hash, key, value, and a next pointer) is allocated on the heap and placed there. If the bucket is occupied, HashMap walks the chain comparing `hash == node.hash && key.equals(node.key)` — checking hash equality first as a fast filter before the more expensive `equals()` call. If a match is found, the value is overwritten and the old value is returned. If not, a new Node is appended. After insertion, if `size > capacity × loadFactor` (default 12 for initial capacity 16), the entire table doubles and every entry is rehashed.

**Q2. Why did Java 8 change HashMap's collision handling? What was wrong with Java 7's approach?**

> In Java 7, collisions were handled by a linked list only. If an attacker could control the keys being inserted (e.g., HTTP request parameters parsed into a HashMap), they could craft keys with identical hashCodes, forcing all entries into a single bucket. Lookup in that bucket was O(n), and with thousands of crafted keys, a single request could consume O(n²) CPU time — this was a real denial-of-service vulnerability (CVE-2011-4858, "HashDoS"). Java 8 mitigated this by converting chains longer than 8 nodes into red-black trees (when capacity ≥ 64), capping worst-case bucket lookup at O(log n) instead of O(n). The treeification threshold of 8 was chosen because under a good hash function with random distribution, the probability of 8+ entries in one bucket follows a Poisson distribution with λ=0.5 — it's astronomically unlikely (~0.00000006). So treeification is a safety net, not a normal code path.

**Q3. If I create `new HashMap<>(1000)` and insert exactly 1000 entries, how many resizes happen? How do I prevent them?**

> `new HashMap<>(1000)` rounds up to the nearest power of 2 via `tableSizeFor()`, giving capacity 1024. The resize threshold is `1024 × 0.75 = 768`. So at the 769th insertion, the table doubles to 2048 and all 768 entries are rehashed — that's one resize. After that, the new threshold is 1536, which is above 1000, so no further resizes occur. To prevent ALL resizes, you need initial capacity ≥ `1000 / 0.75` = 1334. Pass `new HashMap<>(1334)` — this rounds up to 2048, threshold 1536, and all 1000 insertions fit without a single resize. In Java 19+, `HashMap.newHashMap(1000)` does this calculation for you.

**Q4. Explain why using a mutable object as a HashMap key is dangerous. What exactly happens?**

> When you `put(key, value)`, HashMap computes `key.hashCode()`, perturbs it, and stores the entry in the corresponding bucket. The precomputed hash is stored inside the `Node` object. If you later mutate a field that `hashCode()` depends on, the key's hashCode changes — but the Node's stored hash and its bucket position do NOT update. When you call `get(key)`, HashMap computes the NEW hashCode (which points to a different bucket), walks that bucket's chain, and finds nothing. The entry is still physically present in the old bucket, but it's unreachable — it can never be found by `get()`, `containsKey()`, or `remove()`. It's an orphaned entry that leaks memory until the entire map is garbage-collected. This is a silent bug — no exception, no error message, just a `null` return for a key you definitely stored.

**Q5. What's the difference between `HashMap`, `Collections.synchronizedMap(new HashMap<>())`, and `ConcurrentHashMap`? When do you use each?**

> `HashMap` is not thread-safe at all — concurrent `put()` calls can lose entries silently (Java 8+) or cause infinite loops during resize (Java 7). Use it only when the map is owned by a single thread or is effectively immutable after construction. `Collections.synchronizedMap()` wraps every method in a `synchronized(mutex)` block — thread-safe but with a single global lock, so only one thread can read or write at a time. It's fine for low-contention scenarios but becomes a bottleneck under load. `ConcurrentHashMap` (Java 8+) uses CAS (compare-and-swap) for reads and `synchronized` on individual bin heads for writes — reads are lock-free, and writes only lock one bucket, not the entire map. It also prohibits null keys and null values to eliminate the ambiguity of `get() == null` (absent vs. null-valued) in concurrent contexts. Use `ConcurrentHashMap` for any shared mutable map in a Spring singleton or multi-threaded service.

**Q6. Why does HashMap require capacity to be a power of 2? What would break if it weren't?**

> The bucket index calculation is `hash & (capacity - 1)`. When capacity is a power of 2, `capacity - 1` is a bitmask of all 1s in the lower bits (e.g., capacity 16 → mask `0b1111`). Bitwise AND with this mask extracts exactly the right number of lower bits — equivalent to `hash % capacity` but executed as a single CPU instruction instead of integer division (which is 20-40x slower). If capacity weren't a power of 2, `hash & (capacity - 1)` would NOT be equivalent to `hash % capacity` — some bucket indices would never be used, causing uneven distribution and wasted memory. The resize optimization also depends on power-of-2: when capacity doubles, each entry either stays at its old index or moves to `oldIndex + oldCapacity`, determined by checking a single bit. With non-power-of-2 capacity, every entry's index would need to be fully recomputed.

---

## 🎙️ Say It in 60 Seconds

> **Part 1 — What (10s):** HashMap is an array of buckets. A hash function converts the key to a bucket index. Lookup is O(1) because you compute the location instead of searching for it.
>
> **Part 2 — How/Why (30s):** When you call `put()`, HashMap computes `key.hashCode()`, perturbs it by XORing the upper 16 bits into the lower 16, then masks it with `capacity - 1` to get the bucket index. Capacity is always a power of 2 so the mask is a bitwise AND — faster than modulo. Collisions are handled by chaining: a linked list at each bucket. Java 8 added treeification — when a chain exceeds 8 nodes and capacity is ≥ 64, the list converts to a red-black tree, taking worst-case lookup from O(n) to O(log n). When the entry count exceeds `capacity × 0.75`, the array doubles and every entry is rehashed — each entry either stays at its old index or moves to `oldIndex + oldCapacity`, determined by a single bit.
>
> **Part 3 — Gotcha (20s):** HashMap is NOT thread-safe. In a Spring singleton bean, concurrent `put()` calls can silently lose entries — no exception, no error, just missing data. Use `ConcurrentHashMap` for any shared mutable map. And never use a mutable object as a key — if the key's hashCode changes after insertion, the entry becomes orphaned and unfindable.

---

## 🧾 TL;DR

- HashMap = `Node<K,V>[]` array. Hash function → bucket index. O(1) average lookup.
- Perturbation: `h ^ (h >>> 16)` mixes upper bits into lower bits for better distribution.
- Bucket index: `hash & (capacity - 1)` — bitwise AND, not modulo. Requires power-of-2 capacity.
- Default: 16 buckets, load factor 0.75, resize at 12 entries, doubles to 32.
- Java 8: chains > 8 nodes (and capacity ≥ 64) → red-black tree. Untreeify at ≤ 6 nodes.
- Resize rehashes ALL entries. Each entry goes to old index or old index + old capacity.
- NOT thread-safe. Concurrent put: lost updates (Java 8+), infinite loop (Java 7). Use ConcurrentHashMap.
- Mutable keys = silent data corruption. Use immutable keys (String, records, Integer).

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note created as Note #1 (Phase 1) of the JavaBackend KB completion roadmap. Covers: internal array structure, put/get flow, perturbation function, collision chaining, Java 8 treeification (threshold 8, untreeify at 6, min capacity 64), resize mechanics (Java 8 single-bit optimization), null key handling, modCount fail-fast, capacity rounding via tableSizeFor, iteration order non-guarantee, HashMap vs Hashtable vs ConcurrentHashMap comparison. Three production footguns: mutable key corruption, unbounded map OOM, Spring singleton race condition. |
