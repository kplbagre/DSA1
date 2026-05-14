# Strings — Java Reference & DSA Patterns

> Comprehensive reference for string operations in Java + the DSA patterns built on top of them. Pair this with `Set, HashSet, Map, HashMap — DSA Notes`.

---

## 🔹 Core: Why Strings Behave the Way They Do

Strings in Java are **immutable** — once created, they cannot be changed. Every operation like `s + "a"`, `s.replace(...)`, `s.toUpperCase()` returns a **new String**, the original is untouched.

> **Why it matters:** In a loop, `s += c` creates a new string every iteration → O(n²) total. Use **`StringBuilder`** for repeated concatenation.

```java
// ❌ O(n²) — each += creates new String
String result = "";
for (char c : arr) result += c;

// ✅ O(n)
StringBuilder sb = new StringBuilder();
for (char c : arr) sb.append(c);
String result = sb.toString();
```

---

## 🔹 String ↔ char[] Conversions

You convert to `char[]` when you need to:
- **Sort** characters (Strings can't be sorted directly — they're immutable)
- **Modify** characters in place (e.g., reverse, swap)
- **Iterate** efficiently with index access

```java
String s = "hello";

// String → char[]
char[] arr = s.toCharArray();        // ['h','e','l','l','o']

// char[] → String  (3 equivalent ways)
String back = new String(arr);
String back2 = String.valueOf(arr);
String back3 = String.copyValueOf(arr);

// String → char[] for a substring range
char[] partial = s.toCharArray();    // full
String sub = new String(arr, 1, 3);  // "ell" → from index 1, length 3
```

> **Rule of thumb:** Convert to `char[]` only when you need mutation or sorting. For read-only access, use `s.charAt(i)` directly.

---

## 🔹 Array → String — Use `Arrays.toString(arr)`, NOT `arr.toString()` ⚠️

This is one of the **most common Java traps** in DSA — especially when you use a frequency array as a HashMap key (Group Anagrams, etc.).

### Why arrays are special

Most classes (`String`, `Integer`, `ArrayList`, …) **override** `Object.toString()` to return a readable form. **Arrays don't** — they inherit the default, which prints `ClassName + @ + memoryHashCode`.

```java
int[] arr = new int[]{ 1, 0, 1, 0, 0 };

arr.toString();              // ❌ "[I@1540e19d"   (memory address, useless as key)
Arrays.toString(arr);        // ✅ "[1, 0, 1, 0, 0]"
```

### Why this matters in DSA

When using an array as a HashMap key, you usually convert it to a string. If you call `arr.toString()`:
- A new `int[]` object is created each iteration → different memory address each time
- Two arrays with **identical contents** produce **different keys** → grouping silently fails

```java
// ❌ WRONG — produces a different key for every string, no grouping happens
int[] count = new int[26];
// ... fill count ...
String key = count.toString();             // "[I@7a81197d"

// ✅ CORRECT
String key = Arrays.toString(count);       // "[1, 0, 1, ...]"
```

### Other array helpers you must use (same reason)

| Don't write | Write instead | Use case |
| --- | --- | --- |
| `arr.toString()` | `Arrays.toString(arr)` | Print / use as key |
| `arr.equals(other)` | `Arrays.equals(arr, other)` | Compare contents |
| `arr.hashCode()` | `Arrays.hashCode(arr)` | Hash by contents |
| `Arrays.toString(arr2D)` | `Arrays.deepToString(arr2D)` | 2D / nested arrays |
| Manual copy | `Arrays.copyOf(arr, len)` / `Arrays.copyOfRange(arr, from, to)` | Slice |
| `arr1 == arr2` | `Arrays.equals(arr1, arr2)` | Always — `==` checks reference, not contents |

> **Rule:** For *any* operation on a Java array (toString, equals, hashCode, copy), reach for the static helper in `java.util.Arrays`. Never call methods on the array directly.

### Quick mental check

> "Did I write `something.toString()` where `something` is an array?"
> If yes → switch to `Arrays.toString(something)` immediately.

---

## 🔹 Character Arithmetic — The `c - 'a'` Trick ⭐

Every `char` in Java is internally a number (its **ASCII / Unicode code point**). So you can do **arithmetic** on them.

```java
'a' = 97, 'b' = 98, ... 'z' = 122
'A' = 65, 'B' = 66, ... 'Z' = 90
'0' = 48, '1' = 49, ... '9' = 57
```

### Mapping a char to an array index

```java
char c = 'd';
int idx = c - 'a';       // d(100) - a(97) = 3 → 'd' is the 4th letter (0-indexed)

// Generalized:
// For lowercase a-z:  c - 'a'   →  range [0..25]
// For uppercase A-Z:  c - 'A'   →  range [0..25]
// For digits 0-9:     c - '0'   →  integer value
// For full ASCII:     (int) c   →  range [0..127]
```

### Why this is powerful

You can replace a `HashMap<Character, Integer>` with a tiny `int[26]` array → faster, no hashing overhead.

```java
int[] freq = new int[26];
for (char c : s.toCharArray()) freq[c - 'a']++;

// Get count of 'e'
int countE = freq['e' - 'a'];   // index 4
```

### Reverse direction: index → char

```java
int idx = 3;
char c = (char) ('a' + idx);    // 'd'
```

### Useful Character utility methods

```java
Character.isLetter(c);       Character.isDigit(c);
Character.isLetterOrDigit(c); Character.isWhitespace(c);
Character.isUpperCase(c);    Character.isLowerCase(c);
Character.toUpperCase(c);    Character.toLowerCase(c);
Character.getNumericValue(c); // '7' → 7
```

---

## 🔹 Frequency Counting — Three Levels

Pick the right container based on the **character set**:

| Char set | Use | Size | Why |
| --- | --- | --- | --- |
| Lowercase a-z only | `int[26]` | 26 | Smallest, fastest |
| All ASCII (a-z, A-Z, digits, symbols) | `int[128]` | 128 | Covers all standard ASCII |
| Extended ASCII / bytes | `int[256]` | 256 | If you might see > 127 |
| Unicode / emoji / non-English | `HashMap<Character, Integer>` | dynamic | Safe default |

```java
// Lowercase
int[] freq = new int[26];
for (char c : s.toCharArray()) freq[c - 'a']++;

// Any ASCII
int[] freq = new int[128];
for (char c : s.toCharArray()) freq[c]++;     // c auto-promoted to int

// Unicode-safe
Map<Character, Integer> freq = new HashMap<>();
for (char c : s.toCharArray()) freq.merge(c, 1, Integer::sum);
```

---

## 🔹 Sorting a String

Strings are immutable → you can't sort them in place. Convert to `char[]`, sort, rebuild.

```java
String s = "dcba";
char[] c = s.toCharArray();
Arrays.sort(c);
String sorted = new String(c);    // "abcd"

// As a one-liner you'll write often (e.g., Group Anagrams key):
String key = new String(s.chars().sorted()
              .collect(StringBuilder::new,
                       StringBuilder::appendCodePoint,
                       StringBuilder::append).toString());
// Most people just use the 3-line version above — clearer and faster.
```

---

## 🔹 StringBuilder — Your Mutable Friend

When you need to **build** or **modify** strings repeatedly, use `StringBuilder`.

```java
StringBuilder sb = new StringBuilder();        // empty
StringBuilder sb2 = new StringBuilder("init"); // pre-filled

// Common methods
sb.append("hi");            // append string / char / int / anything
sb.append('c');
sb.append(42);

sb.length();                // current length
sb.charAt(i);               // read char at index
sb.setCharAt(i, 'x');       // mutate in place
sb.deleteCharAt(i);         // remove char at index
sb.delete(start, end);      // remove range [start, end)
sb.insert(i, "abc");        // insert at index
sb.reverse();               // reverse in place
sb.toString();              // → final String

// Useful for trimming the last char (common in CSV-style building)
if (sb.length() > 0) sb.deleteCharAt(sb.length() - 1);
```

> **Time complexity:** All append/charAt/setCharAt are O(1) amortized.

---

## 🔹 Essential String Methods (Reference Table)

| Method | What it does | Example |
| --- | --- | --- |
| `s.length()` | Number of chars | `"abc".length()` → 3 |
| `s.charAt(i)` | Char at index | `"abc".charAt(1)` → 'b' |
| `s.isEmpty()` | length == 0 | `"".isEmpty()` → true |
| `s.equals(t)` | Content equal — **always use this, not ==** | |
| `s.equalsIgnoreCase(t)` | Case-insensitive equal | |
| `s.compareTo(t)` | Lexicographic compare; <0 / 0 / >0 | |
| `s.contains(t)` | Substring exists? | |
| `s.indexOf(c)` / `s.indexOf(t)` | First index, -1 if absent | |
| `s.lastIndexOf(c)` | Last index | |
| `s.startsWith(t)` / `s.endsWith(t)` | Prefix/suffix check | |
| `s.substring(i)` | From i to end | `"abcdef".substring(2)` → "cdef" |
| `s.substring(i, j)` | `[i, j)` half-open | `"abcdef".substring(1, 4)` → "bcd" |
| `s.replace(a, b)` | Replace all chars/strings | |
| `s.replaceAll(regex, repl)` | Regex replace | |
| `s.split(regex)` | Split into String[] | `"a,b,c".split(",")` → `["a","b","c"]` |
| `String.join(sep, parts)` | Join collection/array | `String.join("-", List.of("a","b"))` → "a-b" |
| `s.trim()` / `s.strip()` | Remove leading/trailing whitespace | `strip()` is Unicode-aware (preferred) |
| `s.toLowerCase()` / `s.toUpperCase()` | Case conversion | |
| `s.toCharArray()` | → char[] | |
| `String.valueOf(x)` | Any type → String | `String.valueOf(42)` → "42" |
| `Integer.parseInt(s)` | String → int | |

> ⚠️ `s.substring(i, j)` is **O(n)** in modern Java (copies the chars). Don't call it inside a loop unnecessarily.

---

## 🔹 String Equality — `==` vs `equals()`

```java
String a = "hello";
String b = "hello";
String c = new String("hello");

a == b;          // true — both point to same intern pool object
a == c;          // false — c is a new heap object
a.equals(c);     // true — content-based comparison ✅
```

> **Rule:** **Always use `.equals()`** for string comparison. Never `==`.

---

## ⚡ Common DSA Patterns

### **1. Frequency Array (int[26]) — Anagram / Counting Problems** ⭐

> Use when comparing two strings character by character. Fastest, no hashing, fixed memory.

```java
// Valid Anagram (s and t are anagrams?)
public boolean isAnagram(String s, String t) {
    if (s.length() != t.length()) return false;
    int[] count = new int[26];
    for (int i = 0; i < s.length(); i++) {
        count[s.charAt(i) - 'a']++;
        count[t.charAt(i) - 'a']--;
    }
    for (int n : count) if (n != 0) return false;
    return true;
}
```

---

### **2. Sorted String as Canonical Key — Grouping**

> Anagrams produce the same sorted form → use it as a HashMap key. (See **Hashable Key** pattern in HashMap notes.)

```java
char[] c = s.toCharArray();
Arrays.sort(c);
String key = new String(c);
```

---

### **3. Frequency Count as Canonical Key — Faster Grouping**

> Avoid the O(k log k) sort by encoding the frequency array itself.

```java
int[] count = new int[26];
for (char c : s.toCharArray()) count[c - 'a']++;
String key = Arrays.toString(count);   // "[1,0,1,0,...]"
```

---

### **4. Two Pointers — Palindrome / Reversal**

> Move two pointers from both ends toward the center.

```java
// Is palindrome?
public boolean isPalindrome(String s) {
    int l = 0, r = s.length() - 1;
    while (l < r) {
        if (s.charAt(l) != s.charAt(r)) return false;
        l++; r--;
    }
    return true;
}

// Reverse a string
public String reverse(String s) {
    char[] c = s.toCharArray();
    int l = 0, r = c.length - 1;
    while (l < r) {
        char tmp = c[l]; c[l] = c[r]; c[r] = tmp;
        l++; r--;
    }
    return new String(c);
}
```

---

### **5. Two Pointers with Filter — Valid Palindrome (Alphanumeric Only)**

> Skip non-alphanumeric chars; compare case-insensitively.

```java
public boolean isPalindrome(String s) {
    int l = 0, r = s.length() - 1;
    while (l < r) {
        while (l < r && !Character.isLetterOrDigit(s.charAt(l))) l++;
        while (l < r && !Character.isLetterOrDigit(s.charAt(r))) r--;
        if (Character.toLowerCase(s.charAt(l)) != Character.toLowerCase(s.charAt(r)))
            return false;
        l++; r--;
    }
    return true;
}
```

---

### **6. Sliding Window — Longest Substring Without Repeating Chars**

> Expand right, shrink left when constraint violated. Track chars with a Set or freq array.

```java
public int lengthOfLongestSubstring(String s) {
    Set<Character> window = new HashSet<>();
    int l = 0, best = 0;
    for (int r = 0; r < s.length(); r++) {
        while (window.contains(s.charAt(r))) {
            window.remove(s.charAt(l++));
        }
        window.add(s.charAt(r));
        best = Math.max(best, r - l + 1);
    }
    return best;
}
```

---

### **7. Sliding Window with Frequency Map — At Most K Distinct Chars**

> Maintain char → count in window; shrink when distinct count exceeds K.

```java
public int longestKDistinct(String s, int k) {
    Map<Character, Integer> freq = new HashMap<>();
    int l = 0, best = 0;
    for (int r = 0; r < s.length(); r++) {
        freq.merge(s.charAt(r), 1, Integer::sum);
        while (freq.size() > k) {
            char lc = s.charAt(l++);
            if (freq.merge(lc, -1, Integer::sum) == 0) freq.remove(lc);
        }
        best = Math.max(best, r - l + 1);
    }
    return best;
}
```

---

### **8. Sliding Window with Two Frequency Arrays — Find All Anagrams of P in S**

> Maintain a window of size = `p.length()`; compare `Arrays.equals(windowFreq, pFreq)` each step.

```java
public List<Integer> findAnagrams(String s, String p) {
    List<Integer> res = new ArrayList<>();
    if (s.length() < p.length()) return res;
    int[] pf = new int[26], sf = new int[26];
    for (char c : p.toCharArray()) pf[c - 'a']++;
    for (int i = 0; i < s.length(); i++) {
        sf[s.charAt(i) - 'a']++;
        if (i >= p.length()) sf[s.charAt(i - p.length()) - 'a']--;
        if (Arrays.equals(sf, pf)) res.add(i - p.length() + 1);
    }
    return res;
}
```

---

### **9. Expand Around Center — Longest Palindromic Substring**

> A palindrome is symmetric around its center. Try every char (odd) and every gap (even) as a center.

```java
public String longestPalindrome(String s) {
    int start = 0, maxLen = 0;
    for (int i = 0; i < s.length(); i++) {
        int len1 = expand(s, i, i);     // odd length
        int len2 = expand(s, i, i + 1); // even length
        int len = Math.max(len1, len2);
        if (len > maxLen) {
            maxLen = len;
            start = i - (len - 1) / 2;
        }
    }
    return s.substring(start, start + maxLen);
}
private int expand(String s, int l, int r) {
    while (l >= 0 && r < s.length() && s.charAt(l) == s.charAt(r)) { l--; r++; }
    return r - l - 1;
}
```

---

### **10. StringBuilder for Building / Reversing**

> When building output char by char or reversing.

```java
// Reverse words in a string: "  hello  world  " → "world hello"
public String reverseWords(String s) {
    String[] parts = s.trim().split("\\s+");
    StringBuilder sb = new StringBuilder();
    for (int i = parts.length - 1; i >= 0; i--) {
        sb.append(parts[i]);
        if (i > 0) sb.append(' ');
    }
    return sb.toString();
}
```

---

### **11. Encode / Decode with Delimiter — Serialization**

> Use `length + delimiter + content` so delimiters inside content don't break decoding.

```java
// Encode: ["abc", "de"] → "3#abc2#de"
public String encode(List<String> strs) {
    StringBuilder sb = new StringBuilder();
    for (String s : strs) sb.append(s.length()).append('#').append(s);
    return sb.toString();
}

// Decode
public List<String> decode(String s) {
    List<String> res = new ArrayList<>();
    int i = 0;
    while (i < s.length()) {
        int j = s.indexOf('#', i);
        int len = Integer.parseInt(s.substring(i, j));
        res.add(s.substring(j + 1, j + 1 + len));
        i = j + 1 + len;
    }
    return res;
}
```

---

### **12. String to Integer (`atoi`-style parsing)**

> Read sign, skip whitespace, accumulate digits, watch for overflow.

```java
public int myAtoi(String s) {
    int i = 0, n = s.length(), sign = 1, result = 0;
    while (i < n && s.charAt(i) == ' ') i++;
    if (i < n && (s.charAt(i) == '+' || s.charAt(i) == '-'))
        sign = s.charAt(i++) == '-' ? -1 : 1;
    while (i < n && Character.isDigit(s.charAt(i))) {
        int d = s.charAt(i++) - '0';
        if (result > (Integer.MAX_VALUE - d) / 10)
            return sign == 1 ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        result = result * 10 + d;
    }
    return sign * result;
}
```

---

## 🚀 Advanced Patterns (Brief)

### **Rolling Hash (Rabin-Karp idea) — Substring Search in O(n)**

> Treat substring as a number in base-26 (or 256). Slide window: subtract leading char, add trailing char.

```java
// Find if needle is in haystack using rolling hash (simplified)
int base = 26, mod = (int)1e9 + 7;
long needleHash = 0, windowHash = 0, power = 1;
int m = needle.length();
for (int i = 0; i < m; i++) {
    needleHash = (needleHash * base + needle.charAt(i)) % mod;
    windowHash = (windowHash * base + haystack.charAt(i)) % mod;
    if (i < m - 1) power = (power * base) % mod;
}
// Slide:
//   windowHash = ((windowHash - haystack.charAt(i-m) * power) * base + haystack.charAt(i)) % mod
```

---

### **Trie (Prefix Tree) — Prefix Search Problems**

> Tree where each node holds children for next chars. Use for autocomplete, word dictionary, longest common prefix at scale. *(See dedicated Trie notes.)*

```java
class TrieNode {
    TrieNode[] children = new TrieNode[26];
    boolean isEnd;
}
```

---

### **KMP / Z-Algorithm — Pattern Matching in O(n+m)**

> Avoid rechecking matched characters by pre-computing a failure/prefix array. *(Used in advanced string-matching problems; learn the idea first, code later.)*

---

### **Manacher's Algorithm — Longest Palindrome in O(n)**

> Optimization over "expand around center" using symmetry. *(Rare in interviews; expand-around-center is usually enough.)*

---

## 🔑 Quick Decision Cheat Sheet

| Need | Use |
| --- | --- |
| Iterate characters | `for (char c : s.toCharArray())` or `s.charAt(i)` |
| Frequency count (lowercase only) | `int[26]` with `c - 'a'` |
| Frequency count (Unicode/emoji) | `HashMap<Character, Integer>` |
| Sort characters | `char[] → Arrays.sort → new String` |
| Build string in loop | `StringBuilder` |
| Check anagram | Two `int[26]` and compare with `Arrays.equals` |
| Group anagrams | Sorted string OR `Arrays.toString(int[26])` as key |
| Substring search | `s.indexOf(t)` (OK for most), KMP/rolling hash for huge |
| Palindrome check | Two pointers |
| Longest palindrome | Expand around center |
| Window-based char problem | Sliding window + freq map / array |
| Compare strings | `.equals()` — never `==` |
| Convert digit char to int | `c - '0'` |

---

## ⚠️ Gotchas (Silent Bug Hall of Fame)

**`==` vs `equals()`** — always `equals()` for content comparison.

```java
String a = new String("hi");
String b = new String("hi");
a == b;        // false — different objects in memory
a.equals(b);   // true  — content comparison ✅
```

---

**`String` is immutable** — every modification creates a new object; use `StringBuilder` in loops.

```java
String s = "ab";
s.replace('a', 'x');   // returns "xb" but s is still "ab" ❌
s = s.replace('a', 'x'); // ✅ reassign to capture new String
```

---

**`s.substring(i, j)` is O(n)** — copies chars (since Java 7+). Avoid inside tight loops.

---

**`split()` takes a regex, not a literal.**

```java
"a.b.c".split(".");      // [] — empty! "." matches any char in regex ❌
"a.b.c".split("\\.");    // ["a", "b", "c"] — escaped = literal dot ✅
```

---

**`int[26]` only works for `[a-z]` OR `[A-Z]`** — not both at once. Use `int[128]` for mixed.

---

**`Character.getNumericValue('a')`** returns 10 — treats letters as base-36 digits.

```java
Character.getNumericValue('a');   // 10 ❌ (rarely what you want)
'a' - 'a';                        // 0  ✅ for letter index
'7' - '0';                        // 7  ✅ for digit value
```

---

**Integer overflow** in `atoi`/string-to-number — always check before multiplying.

```java
// Check BEFORE multiplying, not after
if (result > (Integer.MAX_VALUE - digit) / 10) {
    return Integer.MAX_VALUE; // would overflow
}
result = result * 10 + digit;
```

---

**`String.format` is slow** — fine for one-off, avoid in hot loops.

---

**`s.replace()` vs `s.replaceAll()`** — `replace` is literal, `replaceAll` is regex.

```java
"a.b.c".replace(".", "x");      // "axbxc" — literal ✅
"a.b.c".replaceAll(".", "x");   // "xxxxx" — regex! "." = ANY char ❌
"a.b.c".replaceAll("\\.", "x"); // "axbxc" — escaped ✅
```

---

**`s.matches(regex)` does FULL string match.** Returns false if pattern matches only a prefix/substring.

```java
"hello world".matches("hello");      // false ❌ — must match whole string
"hello world".matches("hello.*");    // true  ✅ — full match with wildcard
"hello world".contains("hello");     // true  ✅ — substring check
```

---

**`StringBuilder.equals()` compares references, not content.**

```java
StringBuilder a = new StringBuilder("hi");
StringBuilder b = new StringBuilder("hi");
a.equals(b);                       // false ❌ — reference compare
a.toString().equals(b.toString()); // true  ✅ — content compare
```

---

**char vs int comparison** — `'1' == 1` is `false`. The char `'1'` has int value 49.

```java
'1' == 1;            // false ❌
'1' == 49;           // true
'1' - '0' == 1;      // true  ✅ — convert digit char to int
Character.isDigit('1'); // true ✅ — type-check before arithmetic
```

---

**`arr.toString()` returns memory address for arrays** — always use `Arrays.toString(arr)`. (See dedicated section above.)

---

> Save this alongside `dsa-collections-notes.md`. Together they cover the bulk of string-related interview problems. 🚀
