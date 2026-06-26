# Bloom Filter

---

## 🎯 Why This Matters

A Bloom filter answers "have we seen this before?" in O(1) time and near-zero memory — but with a controlled chance of being wrong in one direction. It appears in senior interviews whenever you're designing a system that must avoid expensive lookups for keys that almost certainly don't exist: URL shorteners, duplicate payment detection, database read optimization, email spam filters. The key is that the interviewer isn't testing if you know what a Bloom filter is — they're testing if you know **exactly when to use it**, **what false positive means**, and **why there are no false negatives**.

---

## 🧠 The Mental Model

Imagine a nightclub with a **VIP deny list** — a list of people who are permanently banned. The bouncer has a giant poster board with 1,000 light switches, all starting in the OFF position.

When someone is **banned** (added to the filter), the bouncer picks 3 different switches based on that person's description (3 hash functions) and flips all 3 ON. The person's description is never written down — only the switch positions.

When a new person arrives, the bouncer checks their 3 switches:
- **Any switch is OFF?** → This person was definitely never banned. Let them in. ✅ (no false negatives — if they were banned, all 3 switches would be ON)
- **All 3 switches are ON?** → They were *probably* banned. Reject them and do a full ID check (expensive DB lookup). ❌ But wait — those 3 switches might have been turned ON by three *different* people who each flipped one switch. So the bouncer might wrongly reject a completely innocent person. This is a **false positive**.

The bouncer can handle 100,000 people's bans on a 1,000-switch board using almost no paper — just switches. The cost is occasionally rejecting an innocent person (false positive). But the bouncer **never lets in a banned person** — because a banned person always has all 3 of their switches ON.

**The key insight is:** A Bloom filter is a space-efficient "definitely not" machine. It cannot tell you "definitely yes" — only "definitely no" and "probably yes." False negatives are impossible by construction. False positives are tunable.

---

## 🎨 Visual — Insertion and lookup on a bit array

```
BIT ARRAY (size m=10 bits), K=3 hash functions
═══════════════════════════════════════════════

Initial state:
  Position: 0  1  2  3  4  5  6  7  8  9
  Bit:      0  0  0  0  0  0  0  0  0  0


INSERT "user:alice" — hash functions return positions 1, 4, 7:
  Position: 0  1  2  3  4  5  6  7  8  9
  Bit:      0  1  0  0  1  0  0  1  0  0
                ↑           ↑        ↑
             h1=1         h2=4    h3=7


INSERT "user:bob" — hash functions return positions 3, 4, 9:
  Position: 0  1  2  3  4  5  6  7  8  9
  Bit:      0  1  0  1  1  0  0  1  0  1
                      ↑  ↑           ↑
                   h1=3 h2=4(already set) h3=9


LOOKUP "user:alice" — check positions 1, 4, 7:
  All three bits are 1 → PROBABLY EXISTS ✅ (correct — she was inserted)


LOOKUP "user:carol" — hash functions return positions 3, 4, 7:
  Position 3 = 1 (set by bob)
  Position 4 = 1 (set by bob)
  Position 7 = 1 (set by alice)
  All three bits are 1 → PROBABLY EXISTS ❌ (FALSE POSITIVE — carol was never inserted!)
  → Trigger expensive DB lookup to confirm


LOOKUP "user:dave" — hash functions return positions 0, 2, 5:
  Position 0 = 0 → DEFINITELY NOT IN SET ✅
  (stop immediately — no DB lookup needed)


KEY INVARIANT:
   If ANY of the k checked bits is 0 → element definitely absent (no false negative).
   If ALL k bits are 1 → element probably present (false positive possible).
   A bit set to 1 can never be cleared → deletion is impossible in standard Bloom filter.
```

---

## ⚙️ How It Actually Works

### Bloom Filter Implementation in Java

**Steps in plain English:**

1. **Initialize** a bit array of size `m` bits (all zeros) and choose `k` independent hash functions.
2. **Insert** an element: compute all `k` hash values → set those `k` bit positions to 1.
3. **Query** an element: compute all `k` hash values → check those `k` positions. If any bit is 0 → definitely absent. If all bits are 1 → probably present.
4. **No deletion** in standard Bloom filter — once a bit is set to 1, it cannot be reset (it might have been set by another element).

```java
import java.util.BitSet;

public class BloomFilter {

    // Step 1 — bit array of size m
    private final BitSet bitArray;
    private final int size;
    // k hash functions — simulated using double hashing (Kirsch-Mitzenmacher optimization)
    private final int hashFunctionCount;

    public BloomFilter(int size, int hashFunctionCount) {
        this.size = size;
        this.bitArray = new BitSet(size);
        this.hashFunctionCount = hashFunctionCount;
    }

    // Step 2 — insert: set k bit positions to 1
    public void add(String element) {
        for (int i = 0; i < hashFunctionCount; i++) {
            int position = getHashPosition(element, i);
            bitArray.set(position);
        }
    }

    // Step 3 — query: check k bit positions
    public boolean mightContain(String element) {
        for (int i = 0; i < hashFunctionCount; i++) {
            int position = getHashPosition(element, i);
            if (!bitArray.get(position)) {
                // ANY bit is 0 → definitely not in the set — no false negatives
                return false;
            }
        }
        // ALL bits are 1 → probably in the set — false positive possible
        return true;
    }

    // Double hashing: simulates k independent hash functions with only 2 real hash computations
    // h_i(x) = hash1(x) + i * hash2(x)  (mod m)
    private int getHashPosition(String element, int hashIndex) {
        int hash1 = element.hashCode();
        // Second hash: use a different seed to get independent distribution
        int hash2 = murmurHash(element);
        // Math.abs to avoid negative positions; Math.floorMod handles edge cases
        return Math.abs((hash1 + hashIndex * hash2) % size);
    }

    // Simplified MurmurHash3-style finalizer — uniform distribution, fast
    private int murmurHash(String element) {
        int h = element.hashCode() ^ (element.hashCode() >>> 16);
        h *= 0x85ebca6b;
        h ^= (h >>> 13);
        h *= 0xc2b2ae35;
        h ^= (h >>> 16);
        return h;
    }
}
```

```java
// Usage: URL shortener — check before expensive DB lookup
public class UrlShortenerService {

    private final BloomFilter seenUrls;
    private final UrlRepository urlRepository;

    public UrlShortenerService(UrlRepository urlRepository) {
        // m=1_000_000 bits (~125 KB), k=7 hash functions → ~1% false positive rate for 100K URLs
        this.seenUrls = new BloomFilter(1_000_000, 7);
        this.urlRepository = urlRepository;
    }

    public String shortenUrl(String longUrl) {
        if (!seenUrls.mightContain(longUrl)) {
            // Bloom filter says definitely new → skip DB lookup, create directly
            return createNewShortUrl(longUrl);
        }
        // Bloom filter says probably seen → confirm with DB (handles false positives)
        String existing = urlRepository.findByLongUrl(longUrl);
        if (existing != null) {
            return existing;
        }
        // False positive: bloom said yes, DB said no → create new
        return createNewShortUrl(longUrl);
    }

    private String createNewShortUrl(String longUrl) {
        String shortCode = generateShortCode(longUrl);
        urlRepository.save(shortCode, longUrl);
        // Step 2 — add to bloom filter after confirming it's new
        seenUrls.add(longUrl);
        return shortCode;
    }

    private String generateShortCode(String longUrl) {
        // hash-based 6-character code
        return Integer.toHexString(Math.abs(longUrl.hashCode())).substring(0, 6);
    }
}
```

---

### What is a Bit Array, and why does it fit here?

A **bit array** (also called a bitset or bitmap) is an array where each element is a single bit (0 or 1) rather than a full byte or integer. Java's `BitSet` stores 64 bits per `long` under the hood — so a 1,000,000-bit array uses only ~125 KB.

**Why it fits:** A Bloom filter's entire job is to track whether a bit position has been "touched" — not to store values, not to store keys, just presence/absence. A bit array is the minimal data structure for this. A HashMap would use ~50 bytes per entry; a bit array uses ~10 bits per entry at a 1% false positive rate — a 40× space improvement.

**In an interview, if asked:** "A bit array stores presence information in the most space-efficient way possible — one bit per hash function result, no strings, no pointers, just 0s and 1s. A Bloom filter for 1 million elements fits in ~125 KB, while a HashSet would use ~50 MB — a 400× difference."

---

### Sizing the Filter — False Positive Rate Formula

In an interview, you don't need to derive this formula, but you should be able to quote the practical outcome:

| Target false positive rate | Bits per element (m/n) | Hash functions (k) |
|---|---|---|
| 10% | ~5 bits | ~3 |
| 1% | ~10 bits | ~7 |
| 0.1% | ~15 bits | ~10 |

**Rule of thumb to quote:** "At 10 bits per element and 7 hash functions, a Bloom filter gives roughly 1% false positive rate."

For a set of n=1,000,000 elements at 1% FP: m = 10 × 1,000,000 = 10,000,000 bits = **1.25 MB**. A HashSet of 1M string keys typically uses **50-100 MB** — Bloom filter is 40-80× smaller.

---

## 🏢 Real World — Where Companies Use This

- **Cassandra** (SSTable read optimization): Before reading an SSTable (sorted string table — an immutable on-disk data file), Cassandra checks a per-SSTable Bloom filter. If the filter says "definitely not here," the entire disk read is skipped. With 10 SSTables per key range, this avoids up to 9 unnecessary disk reads per query.
- **Google BigTable and HBase** (same SSTable pattern): Every HFile has a Bloom filter — a row key lookup checks the filter before deserializing the file.
- **Medium / URL shorteners** (duplicate URL detection): Before doing a full DB lookup on every submitted URL, a Bloom filter eliminates the obvious new URLs in O(1). Only "probably seen" URLs trigger a DB round-trip.
- **Redis** (Redisbloom module): Provides a native Bloom filter data structure — used by applications like rate limiting seen-user-IDs or deduplicating event streams.
- **Google Chrome** (safe browsing): Maintains a Bloom filter of malicious URLs locally. If the filter says "probably malicious," Chrome makes a full server request to confirm. This keeps the local filter small while catching nearly all known malicious URLs.
- **Akamai CDN** (one-hit-wonder caching): Akamai uses a Bloom filter to detect URLs that are requested only once and shouldn't be cached — saving cache space for truly popular content.

---

## 🧭 When to Use vs When NOT to Use

| Use this when | Do NOT use when |
|---|---|
| You want to skip expensive lookups for elements **definitely not** in a set | You need to **delete** elements (standard Bloom filter can't delete — use Counting Bloom Filter) |
| False positives are acceptable — a wrong "yes" triggers a cheap follow-up check | False positives are unacceptable — e.g., you can never tell a legitimate user they're banned |
| Memory is a constraint and the set has millions of elements | The set is small (< 10,000 elements) — a HashSet is simpler and exact |
| The use case is "pre-filter before expensive operation" (DB read, network call) | You need to retrieve the element, not just check existence — Bloom filters don't store values |
| Duplicate detection in streaming/event pipelines | You need exact count of how many times an element appeared — use HyperLogLog or a counter |

**The common mistake:** Using a Bloom filter without sizing it properly for the expected number of elements. Adding 10× more elements than `n` causes the false positive rate to skyrocket as the bit array fills up. Always estimate `n` upfront and size `m = n × 10` for 1% FP.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | O(1) lookup, O(1) insert, near-zero memory per element (~10 bits at 1% FP), zero false negatives — if the filter says "no," you're done |
| **You lose** | False positives (tunable but non-zero), cannot delete elements, cannot retrieve elements (it's not a key-value store), filter effectiveness degrades as it fills beyond its designed capacity |
| **Failure mode** | Filter is oversaturated — more elements were added than `n` allows for the target FP rate. Most bits are now 1, every lookup returns "probably present," and the pre-filter becomes useless (every query still hits the DB). Fix: periodically rebuild the filter, or use a scalable Bloom filter that adds layers |

---

## 🔬 Interview Q&As

### Q: "What is a Bloom filter and how does it work?"

> A Bloom filter is a probabilistic data structure that answers "have I seen this element before?" using a bit array and k hash functions. To insert: hash the element k times, set those k bit positions to 1. To query: hash k times, check those k positions — any 0 means definitely absent, all 1s means probably present. It never has false negatives (if all k bits are 1, the element could be present), but it can have false positives (another element's hashes happened to fill those same positions).

---

### Q: "Why are there no false negatives in a Bloom filter?"

> When you insert an element, you set exactly the k bit positions produced by that element's hash functions. Those bits are never cleared — the bit array is write-only. So if you query the same element later, those same k positions will still be 1. The only way to get a false negative would be if a bit got reset to 0, which never happens in a standard Bloom filter.

---

### Q: "What's a false positive in a Bloom filter? Give me a concrete example."

> A false positive is when the filter says "probably present" for an element that was never actually inserted. Concrete example: I insert "alice" (positions 1,4,7) and "bob" (positions 3,4,9). I query "carol" and her hash functions happen to return positions 3, 4, 7 — all three bits are 1 (3 was set by bob, 4 was set by both, 7 was set by alice). The filter says "probably present" but carol was never inserted. In a URL shortener, this means we do an unnecessary DB lookup for carol — not a correctness bug, just a wasted round-trip.

---

### Q: "Why can't you delete from a standard Bloom filter?"

> Because bits are shared between elements. If "alice" set bit position 4 and "bob" also set bit position 4, you can't clear position 4 to "delete" alice without also breaking bob's entry. The bit doesn't know which insertions touched it. For deletions, you'd use a **Counting Bloom Filter** — which stores an integer count per bit position instead of a single bit, allowing decrement on delete — at the cost of more memory (4-8 bytes per position instead of 1 bit).

---

### Q (Tier 2): "Cassandra uses a Bloom filter per SSTable. What happens to read performance when the Bloom filter says 'probably present' for a key that's actually been deleted (tombstoned)?"

> Cassandra uses tombstones — a deletion marker written as a new record rather than erasing the old one. The Bloom filter for the SSTable only knows about inserts, not deletions. So if a key was written (insert → SSTable + Bloom filter), then deleted (tombstone → newer SSTable), and then queried — the old SSTable's Bloom filter still says "probably present." Cassandra reads the old SSTable, finds the original value, reads the newer SSTable, finds the tombstone, and merges — returning "deleted." The Bloom filter didn't prevent the disk read, but this is the correct behaviour because the merge logic handles it. This is why Cassandra compaction (merging SSTables) is important: it eventually eliminates the old SSTable and its stale Bloom filter, restoring full Bloom filter effectiveness for that key range.

---

### Q (Tier 2): "How would you size a Bloom filter for a URL shortener that expects 100 million URLs with a 0.1% false positive rate?"

> Using the rule of thumb: 0.1% FP rate requires ~15 bits per element and ~10 hash functions. So: m = 15 × 100,000,000 = 1.5 billion bits = **187 MB**. That's the in-memory size of the bit array. A HashSet storing 100M URL strings would use roughly 100M × 50 bytes = **5 GB** — the Bloom filter is ~27× smaller. In an interview, walk through the formula: "FP rate ≈ (1 - e^(-kn/m))^k, but the practical table is: 1% → 10 bits/element, 0.1% → 15 bits/element. For 100M URLs at 0.1%, that's 187 MB — fits in a single Redis instance."

---

## 🧾 TL;DR — One Interviewer-Ready Line

> "A Bloom filter is a bit array + k hash functions that gives you O(1) 'definitely not present' or 'probably present' — zero false negatives by construction, tunable false positive rate around 1% at 10 bits per element — so I'd use it as a cheap pre-filter before any expensive DB lookup, like checking if a URL has been shortened before or if a Cassandra key exists in an SSTable."

---

## 🔗 Related Concepts

- **`05-consistent-hashing.md`** — consistent hash rings are often paired with Bloom filters: the ring routes to the right shard, the Bloom filter skips unnecessary disk reads within the shard
- **`03-caching.md`** — Bloom filter is a meta-cache: it prevents unnecessary reads from the actual cache or DB
- **`04-idempotency.md`** — Bloom filters can pre-filter duplicate event IDs before checking the idempotency table; reduces DB load on high-throughput event streams

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **"Bloom Filter"** — ByteByteGo (YouTube: "ByteByteGo bloom filter") | Animated bit array insertion/lookup — cements the visual mental model | ~8 min |
| **ashishps1/awesome-system-design-resources** (https://github.com/ashishps1/awesome-system-design-resources) | Best curated article with FP rate formula derivation and sizing tables | ~15 min read |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. Covers: bit array + k hash functions, insert/query mechanics, false positive vs false negative, sizing formula (10 bits/element for 1% FP), deletion impossibility (counting Bloom filter), real-world use (Cassandra, Chrome, URL shortener). 6 Q&As (4 Tier 1 + 2 Tier 2). |
