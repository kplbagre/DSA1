# ☕ String Internals — Deep Dive

> After this note you can explain why String is immutable (3 reasons), what the string pool is, why `==` works for string literals but fails for `new String()`, and when `intern()` helps vs hurts.

---

## 🎯 The Problem This Solves

Strings are the most used objects in any Java application — log messages, JSON keys, database queries, HTTP headers, map keys. If strings were mutable, any code holding a reference to a string could silently change its value — corrupting HashMap keys, security checks, and cached data. And if every identical string literal created a new object, a server processing 10,000 requests/second would allocate millions of identical `"Content-Type"` strings — wasting heap and triggering GC.

Java's String design — immutable + pooled — solves both problems. But the design has non-obvious consequences that every senior Java developer hits in production.

---

## 📖 Terminology

| Term | Definition |
|---|---|
| **Immutable** | An object whose state cannot change after construction. String's internal `byte[]` and coder fields are `final`. No method on String modifies the object — methods like `toUpperCase()` return a NEW String. |
| **String pool (intern pool)** | A special area in the heap (since Java 7) where the JVM caches unique string literals. When you write `"hello"` in source code, the JVM checks the pool first — if `"hello"` already exists, it reuses that instance. |
| **String literal** | A string defined directly in source code between double quotes: `"hello"`. Automatically placed in the string pool by the JVM. |
| **`intern()`** | A method that adds a string to the pool (if not already present) and returns the pooled instance. After `intern()`, `==` comparison works because both references point to the same pooled object. |
| **Compact Strings (Java 9)** | An optimization where strings containing only Latin-1 characters use a `byte[]` with 1 byte per character (LATIN1 encoding) instead of 2 bytes per character (UTF-16). Reduces memory by ~50% for ASCII-heavy applications. |

---

## 🧠 Mental Model

Think of String as a **sealed envelope** — once sealed, nobody can change what's inside. You can create a new envelope with different contents (`toUpperCase()` returns a new String), but the original envelope is untouched. The JVM keeps a **post office** (string pool) of sealed envelopes for common strings. When you write `"hello"` in code, the JVM checks the post office — if `"hello"` already exists, you get the existing envelope (same reference). If not, it creates one and files it. This means all `"hello"` literals across your entire application point to the SAME object — saving memory and enabling `==` comparison for literals.

> If you can say "String is immutable (byte array is final, no mutating methods); literals go in the pool (shared across the app); `new String()` bypasses the pool; `==` compares references, `equals()` compares content" without notes, you have String.

---

## 🎨 Visual — String Pool and Heap

```
  HEAP MEMORY:
  ┌─────────────────────────────────────────────────────┐
  │                                                     │
  │  String Pool (inside the heap since Java 7):        │
  │  ┌───────────────────────────────────────────────┐  │
  │  │  "hello" ──── 0xAA01 (one instance)           │  │
  │  │  "world" ──── 0xBB02 (one instance)           │  │
  │  │  "hello" ──── (NOT duplicated — reuses 0xAA01)│  │
  │  └───────────────────────────────────────────────┘  │
  │                                                     │
  │  Regular heap objects:                              │
  │  ┌───────────────────────────────────────────────┐  │
  │  │  new String("hello") ──── 0xCC03              │  │
  │  │  (separate object, NOT in the pool)           │  │
  │  │  (contains same chars as 0xAA01 but ≠ ref)    │  │
  │  └───────────────────────────────────────────────┘  │
  └─────────────────────────────────────────────────────┘

  String a = "hello";            → a points to 0xAA01 (pool)
  String b = "hello";            → b points to 0xAA01 (same object!)
  String c = new String("hello"); → c points to 0xCC03 (different object)

  a == b        → true  (same reference — both from pool)
  a == c        → false (different references — pool vs heap)
  a.equals(c)   → true  (same content — "hello".equals("hello"))

KEY INVARIANT:
   Literals → pool → shared reference → == works.
   new String() → heap → separate object → == fails.
   ALWAYS use .equals() for string comparison. Period.
```

---

## 🪜 Build-up

---

### Level 1 — The naive approach (and why it fails)

A developer uses `==` to compare strings, assuming it checks content:

```java
// Tier 1 — Demo: == vs equals() trap
String a = "hello";
String b = "hello";
String c = new String("hello");

System.out.println(a == b);       // true  ← misleading! Works by accident (pool)
System.out.println(a == c);       // false ← the real behavior
System.out.println(a.equals(c));  // true  ← correct comparison

// The trap: developer sees a == b is true, assumes == checks content.
// Later, c comes from user input / network / concatenation → == fails silently.
// No exception — just a false return from a condition check.
```

This is the #1 Java beginner bug that survives into senior code — especially when it works in tests (literals) but fails in production (runtime-constructed strings).

---

### Level 2 — The real mechanism

#### 2.1 — Why String is immutable: 3 reasons

**Reason 1 — Security:**

```java
// String is used in security-sensitive contexts:
// file paths, database URLs, class names, network hosts.
// If strings were mutable:
String path = "/safe/directory/file.txt";
validatePath(path);   // passes security check
// ... attacker mutates path ...
openFile(path);       // now opens "/etc/shadow" — security breach

// Immutability guarantees: the string you validated is the string you use.
```

**Reason 2 — HashMap key safety:**

```java
// String is the most common HashMap key type.
// HashMap stores the key's hashCode at insertion time.
// If the key's content could change after insertion:
//   - hashCode changes → entry is in the wrong bucket → unfindable
// Immutability guarantees: hashCode never changes → HashMap never breaks.
// String caches its hashCode (computed once, stored in a field) — safe because immutable.
```

**Reason 3 — Thread safety for free:**

```java
// Immutable objects are inherently thread-safe — no synchronization needed.
// Any thread can read a String without locking.
// String references can be shared across threads freely.
// This is why String is safe as a Spring singleton field, a Kafka message key,
// a cache key, a log message — without any synchronization.
// ✅ Thread-safe — immutable after construction
```

#### 2.2 — String's internal representation

```java
// Pre-Java 9 (char[] internal):
public final class String {
    private final char[] value;   // UTF-16: 2 bytes per char
    private int hash;             // cached hashCode — 0 = not computed
}

// Java 9+ (compact strings — byte[] internal):
public final class String {
    private final byte[] value;   // LATIN1 (1 byte/char) or UTF-16 (2 bytes/char)
    private final byte coder;     // 0 = LATIN1, 1 = UTF-16
    private int hash;
    private boolean hashIsZero;   // handles the edge case where hash is legitimately 0
}
```

> **What the JVM is actually doing:** Java 9's Compact Strings check each string's content at creation time. If all characters fit in Latin-1 (0x00–0xFF — covers ASCII, Western European languages), the string uses 1 byte per character. Only strings with characters beyond Latin-1 (Chinese, Japanese, emoji, etc.) use the 2-byte-per-char UTF-16 encoding. For typical enterprise applications (English text, JSON, SQL), this cuts String memory usage by ~50%. The optimization is transparent — `String.charAt()`, `length()`, and all methods work identically regardless of the internal encoding.

#### 2.3 — The String pool mechanism

```java
// LITERALS are automatically pooled:
String a = "hello";   // JVM checks pool → not found → creates, adds to pool, returns
String b = "hello";   // JVM checks pool → found → returns same reference
// a == b is true — same object

// COMPILE-TIME CONSTANTS are pooled:
String c = "hel" + "lo";   // compiler optimizes to "hello" at compile time → pooled
// a == c is true

// RUNTIME CONCATENATION is NOT pooled:
String prefix = "hel";
String d = prefix + "lo";   // runtime concatenation → new String on heap → NOT pooled
// a == d is false

// intern() manually adds to pool:
String e = d.intern();   // adds "hello" to pool (already there) → returns pool reference
// a == e is true
```

**String pool location history:**

| Java version | Pool location | Implication |
|---|---|---|
| Java 6 and earlier | PermGen (fixed size) | Pool overflow → `OutOfMemoryError: PermGen space` |
| Java 7+ | Main heap | Pool grows/shrinks with regular GC. No fixed limit. Pooled strings are GC-eligible when unreferenced. |

#### 2.4 — String concatenation evolution

```java
// Pre-Java 5: + operator uses StringBuffer (synchronized — unnecessary overhead)
String result = "hello" + " " + "world";
// Compiler generates: new StringBuffer().append("hello").append(" ").append("world").toString()

// Java 5–8: + operator uses StringBuilder (not synchronized — faster)
// Compiler generates: new StringBuilder().append("hello").append(" ").append("world").toString()

// Java 9+: + operator uses invokedynamic + StringConcatFactory
// The JVM generates an optimized concatenation strategy at runtime.
// Can pre-size the buffer, avoid intermediate copies, and even use
// platform-specific optimizations. Faster than manual StringBuilder in most cases.

// PRACTICAL RULE:
// ✅ For simple concatenation: use + operator (JVM optimizes it since Java 9)
String msg = "User " + name + " logged in at " + time;

// ✅ For loops: use StringBuilder (+ in a loop creates N StringBuilders)
StringBuilder sb = new StringBuilder();
for (String item : items) {
    sb.append(item).append(", ");
}
String result = sb.toString();
// ⚠️ NOT thread-safe — use StringBuffer if shared across threads (rare)

// ✅ For joining: use String.join() or Collectors.joining()
String csv = String.join(", ", items);
String csv2 = items.stream().collect(Collectors.joining(", "));
```

#### 2.5 — hashCode caching

```java
// String caches its hashCode — computed once, stored in 'hash' field:
public int hashCode() {
    int h = hash;
    if (h == 0 && !hashIsZero) {
        h = isLatin1() ? StringLatin1.hashCode(value)
                       : StringUTF16.hashCode(value);
        if (h == 0) {
            hashIsZero = true;
        } else {
            hash = h;
        }
    }
    return h;
}
// First call: walks every byte, computes hash, caches it.
// Subsequent calls: returns cached value in O(1).
// This is safe ONLY because String is immutable — the hash can never change.
// This is why String is the ideal HashMap key — O(1) hashCode after first call.
```

---

### Level 3 — The subtleties

#### 3.1 — `equals()` implementation detail

```java
// String.equals() (simplified from JDK 21):
public boolean equals(Object anObject) {
    if (this == anObject) {          // identity check first — O(1) fast path
        return true;
    }
    if (anObject instanceof String aString) {
        if (coder() == aString.coder()) {
            return isLatin1()
                ? StringLatin1.equals(value, aString.value)    // byte-by-byte comparison
                : StringUTF16.equals(value, aString.value);
        }
    }
    return false;
}
// Key optimization: coder check first — if one is LATIN1 and the other is UTF-16,
// they COULD still be equal (a UTF-16 string with only Latin-1 chars).
// But the fast path catches the common case where both have the same encoding.
```

#### 3.2 — When `intern()` helps vs hurts

```java
// ✅ intern() HELPS when:
// - You have many duplicate strings from external input (parsing CSV, XML, JSON)
// - Memory savings outweigh the CPU cost of intern()
// Example: parsing 10M rows with a "status" column that has 5 distinct values
// Without intern: 10M String objects for "status" → ~200 MB
// With intern:    5 String objects → ~200 bytes
for (Row row : rows) {
    row.setStatus(row.getStatus().intern());
}

// ❌ intern() HURTS when:
// - Strings are mostly unique (UUIDs, timestamps) — pool grows without dedup benefit
// - High-contention path — intern() uses a native hash table with synchronization
// - Pre-Java-7 — the pool was in PermGen with fixed size → overflow risk
```

#### 3.3 — `StringBuilder` vs `StringBuffer`

| | StringBuilder | StringBuffer |
|---|---|---|
| Thread-safe | ❌ No | ✅ Yes (every method is `synchronized`) |
| Performance | Faster (no lock overhead) | Slower (acquires lock on every append) |
| When to use | 99% of cases — single-thread string building | Only when building a string across multiple threads (rare) |

**In practice:** `StringBuffer` is a legacy class. Use `StringBuilder` unless you genuinely need multiple threads appending to the same builder — which is a design smell in itself.

---

## ⚠️ Common Misconceptions

| You might think... | But actually... |
|---|---|
| "`==` works for String comparison" | It works for literals (because they're pooled to the same reference) but fails for runtime-constructed strings (`new String()`, input from network/file, concatenation with variables). ALWAYS use `.equals()`. |
| "`new String(\"hello\")` is the same as `\"hello\"`" | `"hello"` reuses the pooled instance. `new String("hello")` creates a NEW object on the heap AND ensures `"hello"` is in the pool (two objects total). The `new` keyword ALWAYS creates a new object — it never returns a pooled one. |
| "String concatenation with `+` in a loop is fine since Java 9" | Java 9's `invokedynamic` optimization applies to a SINGLE concatenation expression. In a loop, each iteration creates a new `StringConcatFactory` call. A loop with 10,000 iterations still creates intermediate strings. Use `StringBuilder` for loops. |
| "Strings are stored in a special memory area outside the heap" | Since Java 7, the string pool is part of the regular heap. Pooled strings are regular objects — they're GC-eligible when unreferenced. Before Java 7, the pool was in PermGen (a fixed-size area outside the main heap). |
| "`intern()` always improves performance" | `intern()` adds CPU overhead (hash lookup + synchronization in native code). It helps only when you have many duplicate strings. For unique strings (UUIDs, timestamps), it wastes CPU and grows the pool without benefit. |

---

## 🐞 Production Footguns

---

> **Footgun: `==` comparison from external input**
> **Cost:** Silent logic bug
>
> In an authorization service, role checking used `==` instead of `.equals()`. It worked in tests (roles were string literals defined in test fixtures) but failed in production (roles were loaded from a database as new String objects). Users with the `"ADMIN"` role were denied access — `userRole == "ADMIN"` returned false because `userRole` was a database-constructed String, not a pooled literal.

```java
// ❌ The trap: == for string comparison
public boolean isAdmin(String userRole) {
    return userRole == "ADMIN";   // works in tests (literals), fails in production (DB strings)
}

// ✅ The fix: always use .equals()
public boolean isAdmin(String userRole) {
    return "ADMIN".equals(userRole);   // null-safe — "ADMIN" is never null
    // Put the literal FIRST: "ADMIN".equals(userRole)
    // NOT: userRole.equals("ADMIN") — throws NullPointerException if userRole is null
}
```

---

> **Footgun: StringBuilder in loop creates garbage**
> **Cost:** Performance cliff + GC pressure
>
> In a report generation service producing CSV files for 1M rows, each row was built using string concatenation with `+` inside the loop. Each iteration created intermediate String objects — 1M iterations × ~3 concatenations = ~3M temporary String objects, triggering major GC pauses every few seconds. Switching to a single `StringBuilder` outside the loop eliminated the GC pressure entirely.

```java
// ❌ The trap: + concatenation in a loop
String csv = "";
for (Row row : rows) {   // 1M rows
    csv += row.getName() + "," + row.getValue() + "\n";
    // Each += creates a new String → old one becomes garbage
    // O(n²) — each concatenation copies the entire accumulated string
}

// ✅ The fix: StringBuilder outside the loop
StringBuilder sb = new StringBuilder(rows.size() * 50);   // pre-size estimate
for (Row row : rows) {
    sb.append(row.getName()).append(',').append(row.getValue()).append('\n');
}
String csv = sb.toString();
// O(n) — each append copies only the new content
// Pre-sizing avoids internal array resizing
// ⚠️ NOT thread-safe — fine for single-thread report generation
```

---

## 🔗 Concept Web

| Connects to | How |
|---|---|
| `hashmap-internals.md` | String is the most common HashMap key. String's immutability guarantees hashCode stability (computed once, cached). HashMap's `hash()` function calls `String.hashCode()`, which walks the `byte[]` on first call and returns the cached value thereafter — O(1) amortized. |
| `equals-hashcode-contract.md` | String has a well-implemented `equals()` (char-by-char comparison) and `hashCode()` (31-multiplier polynomial). Understanding String's implementation is the concrete example that makes the abstract contract tangible. |
| `java-pass-by-value-semantics.md` | String's immutability makes it behave like a value type in practice — passing a String to a method can't corrupt the caller's data. This is why String is safe as a method parameter without defensive copying. |
| `java-version-evolution.md` | Java 9: compact strings (char[] → byte[]). Java 9+: invokedynamic string concatenation. Java 15: text blocks. Each evolution addressed a specific String pain point. |
| `generics-type-erasure.md` | `String` cannot be used as a type parameter for primitives (`List<String>` works, `List<char>` doesn't). String's internal `byte[]` is a consequence of erasure — you can't parameterize arrays with generic types, so String uses `byte[]` directly. |

---

## 🎙️ Interview Deep Questions

**Q1. Why is String immutable in Java? What would break if it were mutable?**

> String is immutable for three reasons. First, security: strings are used in security-critical contexts (file paths, database URLs, class names). If a string could be mutated after a security check, the check becomes meaningless. Second, HashMap safety: String is the most common HashMap key. HashMap caches the key's hashCode at insertion — if the string's content changed, its hashCode would change, but the cached hash wouldn't update, making the entry unfindable (orphaned in the wrong bucket). Third, thread safety: immutable objects are inherently thread-safe. String references can be shared across threads without synchronization — critical in a multi-threaded Spring container where the same string is read by hundreds of request threads simultaneously.

**Q2. What is the String pool? Where does it live? What's the difference between Java 6 and Java 7+?**

> The string pool is a cache of unique string instances maintained by the JVM. All string literals in source code (`"hello"`) are automatically added to the pool. When you write `"hello"` in two different classes, both references point to the same object in the pool — saving memory. In Java 6 and earlier, the pool lived in PermGen — a fixed-size memory region separate from the main heap. If you interned too many strings, you got `OutOfMemoryError: PermGen space`. In Java 7+, the pool moved to the regular heap. Pooled strings are now regular heap objects — they grow and shrink with normal GC, and they're GC-eligible when no longer referenced. This eliminated the PermGen overflow problem.

**Q3. What are Compact Strings (Java 9) and how do they save memory?**

> Before Java 9, String used `char[]` internally — 2 bytes per character (UTF-16), even for ASCII characters that fit in 1 byte. Java 9 switched to `byte[]` with a `coder` flag: if all characters in the string are Latin-1 (0x00–0xFF), the string uses 1 byte per character (LATIN1 encoding). Only strings with characters beyond Latin-1 use the 2-byte UTF-16 encoding. For typical enterprise applications (English text, JSON, SQL, logs), roughly 95% of strings are Latin-1 — cutting String memory by ~50%. The optimization is fully transparent: all String methods check the coder flag and dispatch to the appropriate implementation. Performance is equal or better — smaller arrays mean better CPU cache utilization.

**Q4. When should you use `intern()` and when should you avoid it?**

> Use `intern()` when you're processing large volumes of data with many duplicate strings — like parsing a CSV with 10 million rows where a "status" column has only 5 distinct values. Without interning, you'd have 10 million String objects holding the same 5 values. With interning, you'd have 5 String objects, and all 10 million references point to those 5. Avoid `intern()` for unique strings (UUIDs, timestamps, user IDs) — it adds CPU overhead (native hash table lookup with synchronization) without deduplication benefit, and grows the pool. Also avoid it on high-contention paths — the native intern table has a global lock. In modern Java, `Caffeine` or a `ConcurrentHashMap`-based deduplicator is often better than `intern()`.

**Q5. Why does `"hello" + "world"` create a pooled string but `name + "world"` doesn't?**

> The compiler evaluates constant expressions at compile time. `"hello" + "world"` is a compile-time constant — both operands are literals. The compiler folds it into `"helloworld"` and stores that literal in the constant pool of the classfile, which is automatically interned by the JVM at class loading time. But `name + "world"` has a variable (`name`), so the compiler can't evaluate it at compile time. It generates runtime concatenation code (StringBuilder in Java 5–8, invokedynamic in Java 9+), which creates a NEW String on the heap, not in the pool. That's why `"hello" + "world" == "helloworld"` is true (both are the same pooled literal), but `(name + "world") == "helloworld"` is false (left side is a new heap object).

---

## 🎙️ Say It in 60 Seconds

> **Part 1 — What (10s):** String is immutable — `final class`, `final byte[]`, no mutating methods. String literals are pooled by the JVM — same literal across the app = same object. `==` compares references, `equals()` compares content.
>
> **Part 2 — How/Why (30s):** Immutability serves three purposes: security (validated strings can't be changed after the check), HashMap safety (hashCode is cached and never changes — pooled strings are ideal HashMap keys), and thread safety (no synchronization needed). Java 9 added Compact Strings — Latin-1 characters use 1 byte instead of 2, cutting memory ~50%. The pool moved from PermGen (fixed size, overflow risk) to the main heap in Java 7 — pooled strings are now GC-eligible like regular objects.
>
> **Part 3 — Gotcha (20s):** The top trap: `==` works for literals (pooled) but fails for runtime strings (user input, database results, concatenation with variables). Put the literal first in `.equals()` — `"ADMIN".equals(role)` is null-safe; `role.equals("ADMIN")` throws NPE if role is null. And never use `+` concatenation inside a loop — each iteration creates a new String, making it O(n²). Use `StringBuilder`.

---

## 🧾 TL;DR

- String is `final class` with `final byte[]` — immutable, no setters, no mutation.
- Immutability enables: security, HashMap key safety (cached hashCode), and free thread safety.
- Literals are pooled → same literal = same object → `==` works for literals ONLY.
- `new String("hello")` creates a SEPARATE heap object → `==` fails. Always use `.equals()`.
- Java 9 Compact Strings: Latin-1 chars = 1 byte/char (not 2). ~50% memory reduction.
- String pool moved from PermGen (Java ≤6) to heap (Java 7+). Pooled strings are GC-eligible.
- `intern()` helps for many duplicates. Hurts for unique strings (CPU + pool bloat).
- Use `StringBuilder` for loops. `+` in loops is O(n²).

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Sep 2026 | Note created as Note #6 (Phase 1) of the JavaBackend KB completion roadmap. Covers: 3 reasons for immutability (security, HashMap, thread safety), internal representation (char[] pre-9, byte[] + coder post-9), string pool mechanism + history (PermGen→heap), == vs equals() with pool explanation, intern() trade-offs, hashCode caching, compact strings (Java 9), string concatenation evolution (StringBuffer → StringBuilder → invokedynamic), StringBuilder vs StringBuffer. Two production footguns: == for authorization check, string concatenation in loop. |
