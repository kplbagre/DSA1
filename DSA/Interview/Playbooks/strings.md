# Strings — Interview Playbook

> **Read this file when:** You have an interview in 1-2 days and need to connect string problems to the right pattern. Strings borrow heavily from arrays, hashing, and sliding window — this file shows you WHICH pattern to reach for.

---

## 🎯 Why You're Reading This

String problems are rarely "pure string" problems. They're usually an **array pattern wearing a string costume.** The challenge is recognizing the disguise. This file maps common string interview questions to the patterns you already know.

---

## 🔧 Essential Methods — Know These Cold

| Method | What it does | Used in |
| --- | --- | --- |
| `s.charAt(i)` | Get char at index — O(1) | All patterns |
| `s.toCharArray()` | Convert to `char[]` for in-place manipulation | Patterns 1, 3 |
| `s.substring(start, end)` | Extract substring `[start, end)` — O(n) creates new string | Pattern 4 |
| `s.length()` | String length | All patterns |
| `String.valueOf(charArray)` | Convert `char[]` back to String | Pattern 3 |
| `new StringBuilder()` | Mutable string builder | Pattern 4 |
| `sb.append(ch)` / `sb.reverse()` / `sb.toString()` | Build, reverse, extract | Patterns 3, 4 |
| `Arrays.sort(charArray)` | Sort characters (for canonical key) | Pattern 1 |
| `Character.isLetter(ch)` / `Character.toLowerCase(ch)` | Char classification and normalization | Pattern 2 |
| `map.computeIfAbsent(key, k -> new ArrayList<>())` | Get-or-create list for grouping (see fallback below) | Canonical (Group Anagrams) |
| `s.trim().split("\\s+")` | Strip leading/trailing spaces + split on one-or-more whitespace (see fallback below) | Pattern 3 (Reverse Words) |

> **Full reference:** `../Reference/string-operations-reference.md`

### 🔄 Lambda & Shorthand Explanations with Fallbacks

**`map.computeIfAbsent(key, k -> new ArrayList<>())` — Get-or-create for grouping**

```java
// What it does:
//   If key is ABSENT  → create new ArrayList, store it, AND return it
//   If key is PRESENT → just return the existing value
//
// k -> new ArrayList<>() is a lambda: given key k, produce a new list
// The "k" parameter is the key itself (we don't use it, but Java requires it)
// You can chain .add(s) because it returns the list
groups.computeIfAbsent(key, k -> new ArrayList<>()).add(s);

// 🔄 Fallback — if you forget computeIfAbsent(), this ALWAYS works:
if (!groups.containsKey(key)) {
    groups.put(key, new ArrayList<>());
}
groups.get(key).add(s);
```

**`s.trim().split("\\s+")` — Split string into words handling messy whitespace**

```java
// What it does (two steps chained):
//   trim()     → removes leading and trailing spaces
//                "  hello world  " → "hello world"
//   split("\\s+") → splits on ONE OR MORE whitespace characters
//                "hello   world" → ["hello", "world"]  (NOT ["hello", "", "", "world"])
//
// \\s  = regex for any whitespace character (space, tab, newline)
// +    = "one or more" — so consecutive spaces count as ONE split point
// Without the + : "hello   world".split("\\s") → ["hello", "", "", "world"] (empty strings!)
String[] words = s.trim().split("\\s+");

// 🔄 Fallback — if you forget the regex, split manually:
List<String> words = new ArrayList<>();
int i = 0;
while (i < s.length()) {
    // Skip whitespace
    while (i < s.length() && s.charAt(i) == ' ') {
        i++;
    }
    if (i >= s.length()) {
        break;
    }
    // Collect word characters
    int start = i;
    while (i < s.length() && s.charAt(i) != ' ') {
        i++;
    }
    words.add(s.substring(start, i));
}
```

---

## 🧠 The Mental Model — Strings Are Character Arrays

Every string problem falls into one of these families:

```
String problem
│
├── "Compare / match characters"
│   ├── Anagram / permutation check → Frequency Array (Pattern 1)
│   └── Palindrome check           → Two Pointers converging (Pattern 2)
│
├── "Find longest/shortest SUBSTRING" (contiguous)
│   └── This IS a sliding window problem → (see two-pointers file)
│
├── "Transform / build a string"
│   ├── Reverse words / characters  → Two Pointers or Stack (Pattern 3)
│   └── String building in a loop   → StringBuilder (Pattern 4)
│
├── "Parse / validate structure"
│   └── Parentheses / brackets      → Stack (see stacks-and-queues file)
│
└── "Find a SUBSEQUENCE" (not contiguous)
    └── Two-pointer walk on both strings (Pattern 5)
```

**The #1 string interview rule:** Never use `String +=` in a loop. Always `StringBuilder`.

---

## 🧭 Pattern 1: Frequency Array — `int[26]` ⭐

**What this solves:** Problems comparing character composition of strings — anagram checks, permutation detection, group-by-character-frequency. An `int[26]` maps each lowercase letter to an index, letting you build, compare, or slide a character frequency count in O(n) time.

**Recognition cues — reach for this when:**
- "Valid anagram"
- "Check if one string is a permutation of another"
- "Find all anagrams in a string" (+ sliding window)
- "Group anagrams" (+ HashMap with frequency as key)

**Brute force:** Sort both strings and compare the sorted versions. O(n log n) per string.

**Key insight:** Two strings are anagrams iff their frequency arrays are identical. Build the difference in one O(n) pass (increment for s, decrement for t), then check all-zeros in O(26) — far faster than sorting.

**Why `int[26]` instead of HashMap?** For lowercase English letters, `int[26]` is faster (no boxing/unboxing), uses less memory, and the comparison is `Arrays.equals(freq1, freq2)`.

**Steps in plain English:**

1. **Count** — build `int[26]` for each string (or increment/decrement from one).
2. **Compare** — `Arrays.equals(freq1, freq2)` for exact match, or check all zeros.

```java
public boolean isAnagram(String s, String t) {
    if (s.length() != t.length()) {
        return false;
    }

    // Step 1 — count: increment for s, decrement for t
    int[] freq = new int[26];
    for (int i = 0; i < s.length(); i++) {
        freq[s.charAt(i) - 'a']++;
        freq[t.charAt(i) - 'a']--;
    }

    // Step 2 — compare: all zeros means anagram
    for (int f : freq) {
        if (f != 0) {
            return false;
        }
    }
    return true;
}
```

**Variant — Find All Anagrams (LC 438):**

This is **Fixed Sliding Window + Frequency Array**. Window size = length of pattern. Slide and compare `int[26]` arrays.

```java
public List<Integer> findAnagrams(String s, String p) {
    List<Integer> result = new ArrayList<>();
    if (s.length() < p.length()) {
        return result;
    }

    int[] pFreq = new int[26];
    int[] wFreq = new int[26];
    for (char c : p.toCharArray()) {
        pFreq[c - 'a']++;
    }

    // Build first window
    for (int i = 0; i < p.length(); i++) {
        wFreq[s.charAt(i) - 'a']++;
    }
    if (Arrays.equals(pFreq, wFreq)) {
        result.add(0);
    }

    // Slide
    for (int right = p.length(); right < s.length(); right++) {
        wFreq[s.charAt(right) - 'a']++;
        wFreq[s.charAt(right - p.length()) - 'a']--;
        if (Arrays.equals(pFreq, wFreq)) {
            result.add(right - p.length() + 1);
        }
    }
    return result;
}
```

**Complexity (optimal):** O(n) time, O(1) space — `int[26]` is fixed size regardless of input length.

**🏷️ Problems:** LC 242 (Valid Anagram), LC 438 (Find All Anagrams), LC 567 (Permutation in String), LC 49 (Group Anagrams — key is sorted or freq).

---

## 🧭 Pattern 2: Palindrome Check — Two Pointers ⭐

**What this solves:** Problems verifying whether a string reads the same forwards and backwards, or finding the longest such substring. Two-pointer approach handles simple checks in O(1) space; expand-from-center handles finding the longest palindromic substring in O(n²) time.

**Recognition cues — reach for this when:**
- "Is this a palindrome?"
- "Valid palindrome" (ignoring non-alphanumeric)
- "Longest palindromic substring"

**Brute force:** Check every substring for the palindrome property — O(n²) substrings, each O(n) to verify. O(n³) total for the longest palindrome.

**Key insight:** Every palindrome is symmetric around a center. Expanding from each possible center (2n-1 options for odd + even lengths) covers all palindromes in O(n²) total — no substring re-checking, and each expansion immediately tells you the palindrome's length.

**For simple palindrome check:**

```java
public boolean isPalindrome(String s) {
    int left = 0;
    int right = s.length() - 1;
    while (left < right) {
        // Skip non-alphanumeric
        while (left < right && !Character.isLetterOrDigit(s.charAt(left))) {
            left++;
        }
        while (left < right && !Character.isLetterOrDigit(s.charAt(right))) {
            right--;
        }
        if (Character.toLowerCase(s.charAt(left)) != Character.toLowerCase(s.charAt(right))) {
            return false;
        }
        left++;
        right--;
    }
    return true;
}
```

**For Longest Palindromic Substring (LC 5) — Expand Around Center:**

**Recognition cue:** "Longest palindromic **substring**" → expand from each center (not DP for interviews — expand is easier to code and explain).

**Steps in plain English:**

1. **For each index** — treat it as the center of a potential palindrome.
2. **Expand** — move outward while characters match.
3. **Track the best** — update the longest found.
4. **Handle both odd and even length** — odd centers on a character, even centers between two characters.

```java
public String longestPalindrome(String s) {
    int start = 0;
    int maxLen = 1;

    for (int i = 0; i < s.length(); i++) {
        // Odd length — center is s[i]
        int len1 = expandFromCenter(s, i, i);
        // Even length — center is between s[i] and s[i+1]
        int len2 = expandFromCenter(s, i, i + 1);
        int len = Math.max(len1, len2);

        if (len > maxLen) {
            maxLen = len;
            start = i - (len - 1) / 2;
        }
    }
    return s.substring(start, start + maxLen);
}

private int expandFromCenter(String s, int left, int right) {
    while (left >= 0 && right < s.length() && s.charAt(left) == s.charAt(right)) {
        left--;
        right++;
    }
    // Length = right - left - 1 (both went one step too far)
    return right - left - 1;
}
```

**Complexity (optimal):** O(n) for simple check; O(n²) for longest palindromic substring. Both use O(1) space.

**🏷️ Problems:** LC 125 (Valid Palindrome), LC 5 (Longest Palindromic Substring), LC 647 (Palindromic Substrings — count all, same expand technique), LC 680 (Valid Palindrome II — allow one deletion).

---

## 🧭 Pattern 3: String Reversal — In-Place or Stack

**What this solves:** Problems requiring reversal of characters, words, or specific segments — either in-place on a `char[]` or by building a new result from reversed order. The two-pointer swap handles character-level reversal; `split` + iterate-in-reverse handles word-level reversal.

**Recognition cues — reach for this when:**
- "Reverse a string"
- "Reverse words in a string"
- "Reverse only certain characters"

**Brute force:** Build a new string or array by appending characters/words in reverse order. O(n) extra space.

**Key insight:** Two converging pointers swap characters in-place in a single pass using O(1) extra space. For word reversal: `trim().split("\\s+")` handles all whitespace edge cases, then iterate in reverse — or use the double-reversal trick (reverse all chars, then reverse each word).

**Reverse Characters — Two Pointers:**

```java
public void reverseString(char[] s) {
    int left = 0;
    int right = s.length - 1;
    while (left < right) {
        char temp = s[left];
        s[left] = s[right];
        s[right] = temp;
        left++;
        right--;
    }
}
```

**Reverse Words (LC 151):**

```java
public String reverseWords(String s) {
    // trim() strips leading/trailing spaces; split("\\s+") splits on 1+ whitespace (regex)
    // 🔄 Fallback: manually iterate with two while-loops (see Lambda section above)
    String[] words = s.trim().split("\\s+");
    StringBuilder sb = new StringBuilder();
    for (int i = words.length - 1; i >= 0; i--) {
        sb.append(words[i]);
        if (i > 0) {
            sb.append(" ");
        }
    }
    return sb.toString();
}
```

**Complexity (optimal):** O(n) time, O(1) extra space for in-place char swap; O(n) space if using split/StringBuilder.

**🏷️ Problems:** LC 344 (Reverse String), LC 151 (Reverse Words in a String), LC 541 (Reverse String II).

---

## 🧭 Pattern 4: StringBuilder for Construction ⭐

**What this solves:** Problems that build a result string incrementally — encoding schemes, compression, string transformation. The rule: any loop that appends to a string must use StringBuilder, not `String +=`.

**Recognition cues — reach for this when:**
- Building a result string character by character
- Any loop that appends to a string
- "Encode/decode" string problems

**Brute force:** Use `String +=` in a loop — each `+=` creates a new String and copies all previous characters. O(n²) total for n appends.

**Key insight:** StringBuilder writes to an existing internal buffer; each character is written exactly once, giving O(n) total regardless of how many `append()` calls there are.

**The performance rule:** `String +=` in a loop is O(n²) total because every `+=` creates a new String and copies all previous characters. `StringBuilder.append()` is amortized O(1).

```java
// ❌ O(n²) — copies entire string on every +=
String result = "";
for (char c : chars) {
    result += c;
}

// ✅ O(n) — StringBuilder
StringBuilder sb = new StringBuilder();
for (char c : chars) {
    sb.append(c);
}
String result = sb.toString();
```

**Encode and Decode Strings (LC 271):**

```java
// Encode: "abc" + "de" → "3#abc2#de"
public String encode(List<String> strs) {
    StringBuilder sb = new StringBuilder();
    for (String s : strs) {
        sb.append(s.length()).append('#').append(s);
    }
    return sb.toString();
}

// Decode: "3#abc2#de" → ["abc", "de"]
public List<String> decode(String s) {
    List<String> result = new ArrayList<>();
    int i = 0;
    while (i < s.length()) {
        int j = s.indexOf('#', i);
        int len = Integer.parseInt(s.substring(i, j));
        result.add(s.substring(j + 1, j + 1 + len));
        i = j + 1 + len;
    }
    return result;
}
```

**Complexity (optimal):** O(n) time, O(n) space — output string is at most as long as input.

**🏷️ Problems:** LC 271 (Encode and Decode Strings), LC 394 (Decode String — with stack), LC 443 (String Compression).

---

## 🧭 Pattern 5: Subsequence Check — Two-Pointer Walk

**What this solves:** Problems where characters of one string must appear in another in the same order, but not necessarily contiguously. "Is `s` a subsequence of `t`?" — the characters of `s` must appear in `t` from left to right with possibly other characters in between.

**Recognition cues — reach for this when:**
- "Is `s` a subsequence of `t`?"
- "Number of matching subsequences"
- Characters don't need to be contiguous — just in order

**Brute force:** Try all C(n, m) combinations of index positions in `t` to pick m positions that match `s`'s characters in order. Exponential time.

**Key insight:** Two pointers — advance the subsequence pointer only on a match, always advance the text pointer. The text pointer's job is to find the next matching character; skipping non-matches is free because order is preserved automatically.

**Steps in plain English:**

1. **Two pointers** — `i` on `s`, `j` on `t`.
2. **Walk `j`** — if `t[j] == s[i]`, advance both. Otherwise advance only `j`.
3. **If `i` reaches end of `s`** — it's a subsequence.

```java
public boolean isSubsequence(String s, String t) {
    int i = 0;
    int j = 0;
    while (i < s.length() && j < t.length()) {
        if (s.charAt(i) == t.charAt(j)) {
            i++;
        }
        j++;
    }
    return i == s.length();
}
```

**Complexity (optimal):** O(n) time, O(1) space — single pass through `t`.

**🏷️ Problems:** LC 392 (Is Subsequence), LC 792 (Number of Matching Subsequences).

---

## 🔬 Canonical Problem — LC 49: Group Anagrams

> **Problem:** Given an array of strings, group the anagrams together. Return the groups in any order.

### Step 1 — Read and identify triggers

"The problem says **group** strings that are **anagrams**. Anagrams have the same characters in different order. This triggers **Pattern 1 + Pattern 2 from arrays-and-hashing (Canonical Key)**: I need a key function where anagrams produce the same key."

### Step 2 — Choose the key strategy

Two options:
- **Sort each string** — anagrams sort to the same result. O(K log K) per string.
- **Frequency array** — `int[26]` converted to string. O(K) per string.

For an interview, sorting is simpler to code and explain. Mention the frequency optimization.

### Step 3 — Adapt and code

**Steps in plain English:**

1. **For each string** — compute its canonical key (sorted characters).
2. **Store** in `Map<String, List<String>>` — key → list of anagrams.
3. **Return** all values.

```java
public List<List<String>> groupAnagrams(String[] strs) {
    Map<String, List<String>> groups = new HashMap<>();

    for (String s : strs) {
        // Step 1 — compute canonical key
        char[] chars = s.toCharArray();
        Arrays.sort(chars);
        String key = new String(chars);

        // Step 2 — store in group
        // computeIfAbsent: if key absent → create new list & store it; if present → return existing
        groups.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        // 🔄 Fallback:
        // if (!groups.containsKey(key)) groups.put(key, new ArrayList<>());
        // groups.get(key).add(s);
    }

    // Step 3 — return groups
    return new ArrayList<>(groups.values());
}
```

### Step 4 — Verify with example

```
Input: ["eat", "tea", "tan", "ate", "nat", "bat"]

"eat" → sort → "aet" → map: {"aet": ["eat"]}
"tea" → sort → "aet" → map: {"aet": ["eat", "tea"]}
"tan" → sort → "ant" → map: {"aet": ["eat", "tea"], "ant": ["tan"]}
"ate" → sort → "aet" → map: {"aet": ["eat", "tea", "ate"], "ant": ["tan"]}
"nat" → sort → "ant" → map: {"aet": ["eat", "tea", "ate"], "ant": ["tan", "nat"]}
"bat" → sort → "abt" → map: {..., "abt": ["bat"]}

Output: [["eat","tea","ate"], ["tan","nat"], ["bat"]] ✅
```

### Complexity

- **Time:** O(N × K log K) — N strings, each sorted in O(K log K) where K = max string length
- **Space:** O(N × K) — storing all strings in the map

---

## ⚡ Problem Bank — Expanded

---

### LC 242: Valid Anagram

> **Problem:** Given two strings `s` and `t`, return true if `t` is an anagram of `s`. `"anagram","nagaram"` → true.

> **Brute force:** Sort both strings and compare. O(n log n) time.
> **Key insight:** Increment/decrement a single `int[26]` in one pass — anagrams cancel perfectly to all-zeros; no sorting needed.
> **Approach:** Single `int[26]` array. Increment for `s`, decrement for `t`. All zeros at end → anagram.

```java
int[] freq = new int[26];
// Increment for s, decrement for t — anagram means they cancel to all zeros
for (int i = 0; i < s.length(); i++) {
    freq[s.charAt(i) - 'a']++;
    freq[t.charAt(i) - 'a']--;
}
// Any non-zero means a character count mismatch
for (int f : freq) {
    if (f != 0) return false;
}
```

**Complexity (optimal):** O(n) time, O(1) space.

---

### LC 125: Valid Palindrome

> **Problem:** Given a string, determine if it's a palindrome considering only alphanumeric characters and ignoring case. `"A man, a plan, a canal: Panama"` → true.

> **Brute force:** Clean the string (remove non-alphanumeric, lowercase), reverse it, compare to original. O(n) time but O(n) extra space for the cleaned copy.
> **Key insight:** Two converging pointers skip non-alphanumeric characters in-place — no cleaned copy needed, O(1) extra space.
> **Approach:** Two pointers converging. Skip non-alphanumeric, compare lowercase.

```java
// Skip non-alphanumeric chars from both ends
while (left < right && !Character.isLetterOrDigit(s.charAt(left))) left++;
while (left < right && !Character.isLetterOrDigit(s.charAt(right))) right--;
// Case-insensitive comparison of the two valid chars
if (Character.toLowerCase(s.charAt(left)) != Character.toLowerCase(s.charAt(right))) return false;
```

**Complexity (optimal):** O(n) time, O(1) space.

---

### LC 5: Longest Palindromic Substring

> **Problem:** Return the longest palindromic substring. `"babad"` → `"bab"` or `"aba"`.

> **Brute force:** Check all n² substrings for palindrome property. O(n³) time.
> **Key insight:** Every palindrome has a center — expanding from each of the 2n-1 possible centers covers all palindromes in O(n²) total without re-checking any substring.
> **Approach:** Expand from center. Try each index as center for odd-length AND between indices for even-length palindromes.

```java
int len1 = expandFromCenter(s, i, i);       // odd
int len2 = expandFromCenter(s, i, i + 1);   // even
// expandFromCenter: while chars match, expand outward
```

**Complexity (optimal):** O(n²) time, O(1) space.

---

### LC 647: Palindromic Substrings

> **Problem:** Count the number of palindromic substrings. `"abc"` → 3 (each char), `"aaa"` → 6.

> **Brute force:** Check all n² substrings for palindrome property. O(n³) time.
> **Key insight:** Expand from each center — every successful expansion step counts as one palindrome. Same O(n²) technique as LC 5, but counting instead of tracking max length.
> **Approach:** Same expand-from-center as LC 5, but COUNT palindromes instead of tracking longest.

```java
// For each center, count how many times we can expand
count += expandAndCount(s, i, i) + expandAndCount(s, i, i + 1);
```

**Complexity (optimal):** O(n²) time, O(1) space.

---

### LC 3: Longest Substring Without Repeating Characters

> **Problem:** Find length of longest substring without repeating characters. `"abcabcbb"` → 3.

> **Brute force:** Check all O(n²) substrings for uniqueness. O(n³) or O(n²) with a set per substring.
> **Key insight:** Variable sliding window — when a duplicate enters, shrink the left boundary until the duplicate is removed. Each character enters and leaves the window at most once → O(n) total.
> **Approach:** Variable sliding window + HashSet. Shrink while duplicate exists.

```java
// Shrink from left until the duplicate is removed
while (window.contains(s.charAt(right))) window.remove(s.charAt(left++));
// Expand — safe to add now, no duplicates in window
window.add(s.charAt(right));
best = Math.max(best, right - left + 1);
```

**Complexity (optimal):** O(n) time, O(min(n, charset)) space.

---

### LC 438: Find All Anagrams in a String

> **Problem:** Given strings `s` and `p`, find all start indices in `s` where a substring is an anagram of `p`. `s="cbaebabacd", p="abc"` → `[0, 6]`.

> **Brute force:** For each window of size p.length(), sort the window and compare to sorted p. O(n · k log k) time.
> **Key insight:** Fixed window slides one step at a time — add the incoming char, remove the outgoing char, compare frequency arrays in O(26) = O(1). Total O(n).
> **Approach:** Fixed sliding window of size `p.length()` + `int[26]` frequency comparison at each slide.

```java
// Expand — add incoming char to window frequency
wFreq[s.charAt(right) - 'a']++;
// Shrink — remove the char that just fell out of the fixed-size window
wFreq[s.charAt(right - p.length()) - 'a']--;
// Frequencies match means this window is an anagram of p
if (Arrays.equals(pFreq, wFreq)) result.add(right - p.length() + 1);
```

**Complexity (optimal):** O(n) time, O(1) space.

---

### LC 20: Valid Parentheses

> **Problem:** Given string with `()[]{}`, determine if brackets are valid. `"()[]{}"` → true, `"(]"` → false.

> **Brute force:** For each close bracket, scan backward for its matching open bracket. O(n²) time.
> **Key insight:** Push the expected close bracket when you see an open — then the pop check is one equality comparison (`pop() != c`) and the stack naturally enforces nesting order.
> **Approach:** Stack. Push expected close bracket for each open. Pop and compare on close.

```java
// Push the EXPECTED closing bracket so the pop check is a simple ==
if (c == '(') stack.push(')');
// If stack is empty (no opener) or popped bracket doesn't match → invalid
else if (stack.isEmpty() || stack.pop() != c) return false;
```

**Complexity (optimal):** O(n) time, O(n) space.

---

### LC 271: Encode and Decode Strings

> **Problem:** Design an algorithm to encode a list of strings into a single string, and decode it back. Must handle any character including `#`, newlines, etc.

> **Brute force:** Use a delimiter that doesn't appear in strings — fragile, fails if the delimiter is present in a string.
> **Key insight:** Length-prefix encoding `"len#data"` is unambiguous regardless of content — the length field tells you exactly how many characters to read, making the `#` separator safe even if strings contain `#`.
> **Approach:** Length-prefix encoding. `"abc"` → `"3#abc"`. Decode by reading length, skipping `#`, extracting substring.

```java
// Encode: sb.append(s.length()).append('#').append(s);
// Decode: int len = parseInt(s.substring(i, j)); result.add(s.substring(j+1, j+1+len));
```

**Complexity (optimal):** O(n) time, O(n) space — n = total characters across all strings.

---

### LC 392: Is Subsequence

> **Problem:** Given strings `s` and `t`, return true if `s` is a subsequence of `t` (characters in order, not necessarily contiguous). `"ace","abcde"` → true.

> **Brute force:** Try all C(n, m) combinations of positions in `t` to match `s`'s characters. Exponential time.
> **Key insight:** Two pointers — advance the subsequence pointer only on a match, always advance the text pointer. A single left-to-right pass finds all characters in order without backtracking.
> **Approach:** Two-pointer walk. Pointer `i` on `s`, pointer `j` on `t`. Advance `j` always; advance `i` only when chars match.

```java
if (s.charAt(i) == t.charAt(j)) i++;
j++;
return i == s.length();
```

**Complexity (optimal):** O(n) time, O(1) space — single pass through `t`.

---

### LC 680: Valid Palindrome II

> **Problem:** Given a string, return true if it can be a palindrome after deleting **at most one** character. `"abca"` → true (delete 'b' or 'c').

> **Brute force:** Try deleting each of the n characters one by one, check if the resulting string is a palindrome. O(n²) time.
> **Key insight:** Two pointers on the original string — on the first mismatch, try skipping the left character OR the right character; only one deletion allowed, so check both subproblems directly.
> **Approach:** Two pointers. On first mismatch, try skipping LEFT or skipping RIGHT. If either resulting substring is palindrome → true.

```java
// On first mismatch, try skipping either the left or right char
if (s.charAt(l) != s.charAt(r)) {
    return isPalindrome(s, l + 1, r) || isPalindrome(s, l, r - 1);
}
```

**Complexity (optimal):** O(n) time, O(1) space.

---

### LC 567: Permutation in String

> **Problem:** Given `s1` and `s2`, return true if `s2` contains a permutation of `s1`. Example: `s1 = "ab", s2 = "eidbaooo"` → `true` (substring "ba" is a permutation of "ab").

> **Brute force:** For each window of size s1.length() in s2, sort the window and compare to sorted s1. O(n · k log k) time.
> **Key insight:** Fixed sliding window + frequency arrays — slide one character at a time; frequency comparison is O(26) = O(1) per step. Total O(n). Identical algorithm to LC 438, just returns boolean.
> **Approach:** Fixed sliding window of size `s1.length()`. Compare `int[26]` frequency arrays. Same as LC 438 but returns boolean. See `two-pointers-and-sliding-window.md`.

```java
int[] freq1 = new int[26], freq2 = new int[26];
// Build target frequency from s1
for (char c : s1.toCharArray()) freq1[c - 'a']++;
for (int i = 0; i < s2.length(); i++) {
    // Expand — add incoming char
    freq2[s2.charAt(i) - 'a']++;
    // Shrink — once window exceeds s1.length(), remove the leftmost char
    if (i >= s1.length()) freq2[s2.charAt(i - s1.length()) - 'a']--;
    // Matching frequencies means this window is a permutation of s1
    if (Arrays.equals(freq1, freq2)) return true;
}
```

**Complexity (optimal):** O(n) time, O(1) space.

---

### LC 49: Group Anagrams

> **Problem:** Group strings that are anagrams of each other. Example: `["eat","tea","tan","ate","nat","bat"]` → `[["bat"],["nat","tan"],["ate","eat","tea"]]`.

> **Brute force:** Compare every pair of strings to check anagram relationship, union-find to group them. O(n² · k) time.
> **Key insight:** Canonical key — sorted characters map all anagrams to the same string. HashMap groups them automatically; no pairwise comparison needed.
> **Approach:** Canonical key pattern. Sort each word's characters → anagrams produce the same sorted key. Use HashMap `<String, List<String>>`.

```java
Map<String, List<String>> map = new HashMap<>();
for (String s : strs) {
    // Sort characters — all anagrams produce the same sorted key
    char[] chars = s.toCharArray();
    Arrays.sort(chars);
    String key = String.valueOf(chars);
    // computeIfAbsent: if key absent → create new list; if present → return existing
    map.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
    // 🔄 Fallback:
    // if (!map.containsKey(key)) map.put(key, new ArrayList<>());
    // map.get(key).add(s);
}
return new ArrayList<>(map.values());
```

**Complexity (optimal):** O(n · k log k) time, O(n · k) space — n strings, each sorted in O(k log k).

---

### LC 344: Reverse String

> **Problem:** Reverse a character array in-place. Example: `['h','e','l','l','o']` → `['o','l','l','e','h']`.

> **Brute force:** Create a new array and fill it in reverse order. O(n) extra space.
> **Key insight:** Two converging pointers swap characters in-place — O(1) extra space, single pass.
> **Approach:** Two pointers converging. Swap `s[left]` and `s[right]`, then move inward. O(n) time, O(1) space.

```java
int lo = 0, hi = s.length - 1;
while (lo < hi) {
    // Swap characters at converging pointers
    char temp = s[lo];
    s[lo] = s[hi];
    s[hi] = temp;
    lo++;
    hi--;
}
```

**Complexity (optimal):** O(n) time, O(1) space.

---

### LC 151: Reverse Words in a String

> **Problem:** Reverse the order of words in a string. Multiple spaces between words, leading/trailing spaces. Example: `"  hello world  "` → `"world hello"`.

> **Brute force:** Manual character-by-character scan to collect words, reverse the list, rejoin. Verbose but O(n) time.
> **Key insight:** `trim().split("\\s+")` handles all whitespace edge cases (leading/trailing/multiple spaces) in one line; then iterate in reverse for word-order reversal. O(n) and clean.
> **Approach:** Split by whitespace, filter empties, reverse the list, join with single space. Or: reverse entire string, then reverse each word individually.

```java
// trim() strips edge spaces; split("\\s+") splits on 1+ whitespace (\\s = whitespace, + = one-or-more)
// 🔄 Fallback: manually iterate with two while-loops (see Lambda section at top of file)
String[] words = s.trim().split("\\s+");
StringBuilder sb = new StringBuilder();
// Walk words in reverse to reverse their order
for (int i = words.length - 1; i >= 0; i--) {
    sb.append(words[i]);
    if (i > 0) {
        sb.append(" ");
    }
}
return sb.toString();
```

**Complexity (optimal):** O(n) time, O(n) space.

---

### LC 541: Reverse String II

> **Problem:** Reverse the first `k` characters for every `2k` chunk of the string. Example: `s = "abcdefg", k = 2` → `"bacdfeg"`.

> **Brute force:** Build new char array by manually tracking which positions get reversed per chunk. O(n) space.
> **Key insight:** Walk in steps of 2k; at each chunk, apply two-pointer swap to just the first k chars (capping at array end). In-place on `toCharArray()`, O(1) extra space per chunk.
> **Approach:** Walk in steps of `2k`. For each chunk, reverse the first `k` characters (or remaining if fewer than `k`).

```java
char[] arr = s.toCharArray();
// Process every 2k chunk — reverse only the first k chars of each chunk
for (int i = 0; i < arr.length; i += 2 * k) {
    // hi caps at array end in case fewer than k chars remain
    int lo = i, hi = Math.min(i + k - 1, arr.length - 1);
    while (lo < hi) {
        char t = arr[lo];
        arr[lo++] = arr[hi];
        arr[hi--] = t;
    }
}
return new String(arr);
```

**Complexity (optimal):** O(n) time, O(n) space — `toCharArray()` creates a copy.

---

### LC 394: Decode String

> **Problem:** Decode encoded strings like `"3[a2[c]]"` → `"accaccacc"`. Number before brackets means repeat that many times.

> **Brute force:** Repeatedly find the innermost `[...]` pair, expand it, replace in string, repeat until no brackets. O(n · depth) time.
> **Key insight:** Two stacks (count + string-so-far) save and restore state at each nesting level — `[` saves current context and starts fresh, `]` restores outer context and applies the repetition.
> **Approach:** Use a stack. Push current string and count when hitting `[`. On `]`, pop and repeat. See `stacks-and-queues.md` for stack-based approach.

```java
// On '[': push currentString and currentNum to stacks, reset both
// On ']': pop and build: poppedString + currentString.repeat(poppedNum)
Deque<StringBuilder> strStack = new ArrayDeque<>();
Deque<Integer> numStack = new ArrayDeque<>();
```

**Complexity (optimal):** O(output length) time — proportional to the decoded string size.

---

### LC 443: String Compression

> **Problem:** Compress `['a','a','b','b','c','c','c']` → `['a','2','b','2','c','3']` in-place. Return new length. If count is 1, don't write the count.

> **Brute force:** Build compressed result in a separate array, then copy back. O(n) extra space.
> **Key insight:** Two-pointer in-place — `read` scans runs, `write` writes the compact form. The write pointer never overtakes the read pointer (compressed form ≤ original length), so in-place is always safe.
> **Approach:** Two pointers. `read` scans groups of same chars, `write` writes the char and count (if > 1). Convert count to chars digit by digit.

```java
int write = 0, read = 0;
while (read < chars.length) {
    char ch = chars[read];
    int count = 0;
    // Count the full run of identical characters
    while (read < chars.length && chars[read] == ch) {
        read++;
        count++;
    }
    // Write the character itself
    chars[write++] = ch;
    // Write the count digits only if count > 1 (single chars have no count)
    if (count > 1) {
        for (char c : String.valueOf(count).toCharArray()) {
            chars[write++] = c;
        }
    }
}
return write;
```

**Complexity (optimal):** O(n) time, O(1) space.

---

### LC 792: Number of Matching Subsequences

> **Problem:** Given a string `s` and an array of words, count how many words are subsequences of `s`. Example: `s = "abcde", words = ["a","bb","acd","ace"]` → `3`.

> **Brute force:** For each word, run the two-pointer subsequence check. O(n · m) total where n = length of s, m = total characters in all words.
> **Key insight:** Same two-pointer subsequence check per word (Pattern 5). Optimization: bucket words by their next expected character — walk `s` once and advance only matching buckets; avoids re-scanning `s` per word.
> **Approach:** For each word, use the subsequence two-pointer check (Pattern 5). Optimization: bucket words by their current character to avoid re-scanning `s` per word.

```java
// Basic approach: subsequence check per word
int sPtr = 0, wPtr = 0;
while (sPtr < s.length() && wPtr < word.length()) {
    // Match found — advance word pointer; sPtr always advances
    if (s.charAt(sPtr) == word.charAt(wPtr)) wPtr++;
    sPtr++;
}
// If wPtr reached the end, all chars of word were found in order
return wPtr == word.length();
```

**Complexity (optimal):** O(n + m) time with bucket optimization, O(m) space — n = length of s, m = total chars in all words.

---

## ⚠️ Interview Gotchas

### Edge cases interviewers will probe:

- **Empty string** — `""` is a palindrome, is a valid anagram of itself, has 0 substrings
- **Single character** — always a palindrome, length-1 substring
- **All same characters** — `"aaaa"` — every substring is a palindrome
- **Unicode / special characters** — "Should I handle uppercase?" → ask the interviewer

### Follow-up questions:

| After solving... | They'll ask... | Answer |
| --- | --- | --- |
| Valid Anagram | "What if strings contain Unicode?" | Use `HashMap<Character, Integer>` instead of `int[26]` |
| Group Anagrams | "Can you do O(N×K) instead of O(N×K log K)?" | Use `int[26]` frequency as key (avoid sorting) |
| Longest Palindrome | "Can you do O(n)?" | Manacher's algorithm — know the name, don't implement in interview |
| Valid Palindrome | "What about palindrome with one deletion?" | LC 680 — try skipping left OR right, check both |

### Java string traps:

- **`==` compares references, not content** — always use `.equals()` (or put literal first: `"hello".equals(s)`)
- **`substring()` end index is exclusive** — `"hello".substring(1, 3)` returns `"el"`, not `"hel"`
- **`charAt()` returns `char`, not `String`** — can't call `.equals()` on it; use `==` for char comparison
- **`char` arithmetic returns `int`** — `char d = c + 1;` won't compile; need `(char)(c + 1)`

---

## 🧩 Speed Drill — 7 Minutes

**Part 1 — Pattern Recognition (2 minutes)**

1. "Check if two strings are anagrams" → ___
2. "Find longest palindromic substring" → ___
3. "Group strings by anagram equivalence" → ___
4. "Is s a subsequence of t?" → ___
5. "Find all start indices of anagrams of p in s" → ___

**Answers:** 1. Frequency Array `int[26]`, 2. Expand from center, 3. Canonical Key + HashMap, 4. Two-pointer walk, 5. Fixed Sliding Window + Frequency Array

**Part 2 — Write the Template (3 minutes)**

From memory, write the `expandFromCenter` helper method. Input: string, left, right. Output: length of palindrome. Handle the while loop and the return.

**Part 3 — The Char Arithmetic Trap (2 minutes)**

Write, from memory:
1. How to convert `char c` to an index in `int[26]`: ___
2. How to convert index `i` back to a `char`: ___
3. How to check if a character is a lowercase letter: ___

**Answers:** 1. `c - 'a'`, 2. `(char)('a' + i)`, 3. `c >= 'a' && c <= 'z'` or `Character.isLowerCase(c)`

**Scoring:** All parts correct = ready. Missed Part 3 = review `DSA/Reference/string-operations-reference.md`.

---

## 🔗 Cross-References

| Topic | File |
| --- | --- |
| String operations reference (method syntax) | `DSA/Reference/string-operations-reference.md` |
| Sliding window patterns for substrings | `DSA/Interview/Playbooks/two-pointers-and-sliding-window.md` |
| Stack patterns for parentheses | `DSA/Interview/Playbooks/stacks-and-queues.md` |
| HashMap/HashSet syntax | `DSA/Reference/hashmap-section-updated.md` |
| Java coding traps (String ==, charAt, substring) | `DSA/Implementation/java-coding-traps.md` |

---

## 🔄 Changelog

| Date | Change |
| --- | --- |
| May 2026 | **File created.** Interview Playbook for Strings. 5 patterns: frequency array, palindrome check, reversal, StringBuilder, subsequence. Canonical walkthrough (LC 49 Group Anagrams), 10-problem bank, char arithmetic traps. |
| May 2026 | **Lambda/fallback pass.** Added `computeIfAbsent` to Essential Methods. Added 🔄 Lambda section. Inline comments + `🔄 Fallback` at both `computeIfAbsent` usage points (canonical walkthrough + LC 49 problem bank). |
| June 2026 | **Brute Force / Key Insight pass.** Added `**What this solves**`, `**Brute force**`, `**Key insight**`, `**Complexity (optimal)**` to all 5 pattern blocks. Added `> **Brute force**`, `> **Key insight**`, `**Complexity (optimal)**` to all 17 problem bank entries. |
