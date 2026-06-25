# Syntax Quick Card — Methods You'll Blank On

## 🎯 Purpose

You know WHAT to use. You forgot HOW to write it. This file is creation + top 5 methods per DS. No explanations, just syntax.

---

## 📚 Linear Structures

### Array

```java
// Creation
int[] arr = new int[5];
int[] arr = {1, 2, 3};
int[] arr = new int[]{1, 2, 3};

// Top methods
arr.length;                         // size (field, not method!)
int val = arr[2];                   // direct access
arr[2] = 99;                        // direct assignment
// Arrays utility
Arrays.sort(arr);                   // sort in-place
Arrays.fill(arr, 0);                // fill all with value
Arrays.copyOf(arr, newLength);      // copy, resize if needed
Arrays.equals(arr1, arr2);          // compare two arrays
Arrays.binarySearch(arr, target);   // search in sorted array, returns index or -(insertion_point+1)
```

### ArrayList

```java
// Creation
ArrayList<Integer> list = new ArrayList<>();
ArrayList<Integer> list = new ArrayList<>(Arrays.asList(1, 2, 3));

// Top methods
list.add(5);                        // append at end, O(1) amortized
list.add(1, 99);                    // insert at index 1, O(n) at middle
list.get(2);                        // access by index
list.remove(1);                     // remove at index, O(n)
list.size();                        // length
list.set(2, 100);                   // update value at index
list.contains(5);                   // linear search, O(n)
list.clear();                       // remove all
list.isEmpty();                     // size == 0?
Collections.sort(list);             // sort in-place
```

### LinkedList

```java
// Creation
LinkedList<Integer> list = new LinkedList<>();

// Top methods (as list)
list.add(5);                        // append at end
list.add(1, 99);                    // insert at index, O(n) to reach
list.get(1);                        // access, O(n) to reach
list.remove(0);                     // remove, O(n) to reach
list.size();
// Better for queue/stack — use ArrayDeque instead
```

---

## 🔄 Stack & Queue

### ArrayDeque as Stack

```java
// Creation
Deque<Integer> stack = new ArrayDeque<>();

// Top methods
stack.push(5);                      // add to top, O(1)
stack.pop();                        // remove from top, O(1), throws if empty
stack.peek();                       // view top without removing, O(1), returns null if empty
stack.isEmpty();
stack.size();
```

### ArrayDeque as Queue

```java
// Creation
Queue<Integer> queue = new ArrayDeque<>();

// Top methods
queue.offer(5);                     // enqueue (add to tail), O(1)
queue.poll();                       // dequeue (remove from head), O(1), returns null if empty
queue.peek();                       // view head, O(1), returns null if empty
queue.isEmpty();
queue.size();
// Alternative names (legacy, same effect):
// queue.add(5) instead of offer — throws if capacity issue (won't happen with ArrayDeque)
// queue.remove() instead of poll — throws if empty
```

### PriorityQueue (Min-Heap by Default)

```java
// Creation (min-heap)
PriorityQueue<Integer> pq = new PriorityQueue<>();

// Creation (max-heap)
PriorityQueue<Integer> pq = new PriorityQueue<>(Collections.reverseOrder());

// Creation (custom comparator)
PriorityQueue<int[]> pq = new PriorityQueue<>((a, b) -> a[1] - b[1]);  // min by second element
PriorityQueue<Integer> pq = new PriorityQueue<>((a, b) -> b - a);      // max-heap (reverse natural)

// Top methods
pq.offer(5);                        // add, O(log n)
pq.poll();                          // remove min (or max if reversed), O(log n), returns null if empty
pq.peek();                          // view min, O(1), returns null if empty
pq.size();
pq.isEmpty();
// DON'T do iteration — heap not sorted, iteration is O(n) and random order
```

---

## 🗺️ Maps

### HashMap

```java
// Creation
Map<String, Integer> map = new HashMap<>();
Map<String, Integer> map = new HashMap<>(new HashMap<String, Integer>(){{
    put("a", 1);
    put("b", 2);
}});  // double brace initialization (verbose, avoid)

// Top methods
map.put("alice", 25);               // insert/update, O(1) avg
map.get("alice");                   // retrieve, O(1) avg, returns null if absent
map.getOrDefault("bob", 0);         // get with fallback if absent
map.containsKey("alice");           // O(1) avg
map.remove("alice");                // O(1) avg, returns value
map.size();
map.isEmpty();
map.keySet();                       // Set of all keys
map.values();                       // Collection of all values
map.entrySet();                     // Set of Map.Entry, best for iteration
// Iteration
for (Map.Entry<String, Integer> e : map.entrySet()) {
    String key = e.getKey();
    Integer val = e.getValue();
}
// Frequency map (common pattern)
map.merge(key, 1, Integer::sum);    // if key absent, insert (key, 1); else add 1 to value
map.putIfAbsent(key, 0);            // insert only if absent, then return value
map.computeIfAbsent(key, k -> new ArrayList<>());  // if absent, compute value and insert
```

### LinkedHashMap

```java
// Creation (insertion order)
Map<String, Integer> map = new LinkedHashMap<>();

// Creation (access order, LRU cache style)
Map<String, Integer> map = new LinkedHashMap<String, Integer>(16, 0.75f, true) {
    protected boolean removeEldestEntry(Map.Entry eldest) {
        return size() > CAPACITY;  // auto-evict oldest if size exceeds CAPACITY
    }
};

// Top methods (same as HashMap)
map.put("a", 1);
map.get("a");
map.entrySet();  // iteration = insertion order
```

### TreeMap

```java
// Creation (natural order)
Map<String, Integer> map = new TreeMap<>();

// Creation (custom order)
Map<String, Integer> map = new TreeMap<>(Comparator.reverseOrder());

// Top methods
map.put("alice", 25);               // insert, O(log n)
map.get("alice");                   // O(log n)
map.containsKey("alice");
map.remove("alice");                // O(log n)
map.size();
// Range queries
map.subMap("alice", "bob");         // keys in [alice, bob), Map object
map.headMap("charlie");             // keys < charlie
map.tailMap("alice");               // keys >= alice
map.floorKey("bob");                // largest key <= bob, null if none
map.ceilingKey("bob");              // smallest key >= bob, null if none
map.firstKey();                     // smallest key, throws if empty
map.lastKey();                      // largest key, throws if empty
map.firstEntry();                   // smallest key-value pair
map.lastEntry();                    // largest key-value pair
map.pollFirstEntry();               // remove and return smallest
map.pollLastEntry();                // remove and return largest
// Iteration (sorted by key)
for (Map.Entry<String, Integer> e : map.entrySet()) {
    // keys in sorted order
}
```

---

## 🎯 Sets

### HashSet

```java
// Creation
Set<Integer> set = new HashSet<>();
Set<Integer> set = new HashSet<>(Arrays.asList(1, 2, 3));

// Top methods
set.add(5);                         // O(1) avg, returns true if added, false if already present
set.remove(5);                      // O(1) avg, returns true if was present
set.contains(5);                    // O(1) avg
set.size();
set.isEmpty();
set.clear();
// Iteration (no order)
for (Integer num : set) { }
```

### LinkedHashSet

```java
// Creation (insertion order)
Set<Integer> set = new LinkedHashSet<>();

// Top methods (same as HashSet)
set.add(5);
set.remove(5);
set.contains(5);
// Iteration (insertion order)
for (Integer num : set) { }
```

### TreeSet

```java
// Creation (natural order)
Set<Integer> set = new TreeSet<>();

// Creation (custom order)
Set<Integer> set = new TreeSet<>(Comparator.reverseOrder());

// Top methods
set.add(5);                         // O(log n), returns true/false
set.remove(5);                      // O(log n), returns true/false
set.contains(5);                    // O(log n)
set.size();
// Range queries
set.subSet(10, 20);                 // elements in [10, 20)
set.headSet(20);                    // elements < 20
set.tailSet(10);                    // elements >= 10
set.floor(15);                      // largest element <= 15
set.ceiling(15);                    // smallest element >= 15
set.first();                        // smallest, throws if empty
set.last();                         // largest, throws if empty
set.pollFirst();                    // remove and return smallest
set.pollLast();                     // remove and return largest
// Iteration (sorted order)
for (Integer num : set) { }
```

---

## 📝 Strings & Chars

### String

```java
// Creation
String s = "hello";
String s = new String("hello");
String s = String.valueOf(123);
String s = "" + 123;

// Top methods
s.length();                         // not .size()!
s.charAt(2);                        // char at index
s.substring(1, 4);                  // [1, 4), "ell"
s.substring(2);                     // [2, end), "llo"
s.indexOf('l');                     // first occurrence, -1 if not found
s.lastIndexOf('l');                 // last occurrence
s.contains("ll");                   // substring check
s.startsWith("he");
s.endsWith("lo");
s.toLowerCase();
s.toUpperCase();
s.split(",");                       // String[] split by delimiter
s.trim();                           // remove leading/trailing whitespace
s.replace('o', '0');                // replace all chars
s.replace("ll", "");                // replace substring
s.toCharArray();                    // String → char[]
s.compareTo(t);                     // lexicographic compare, <0 if this<t, >0 if this>t, 0 if equal
s.equals(t);                        // ==? (not equals)
s.equalsIgnoreCase(t);
// Convert
Integer.parseInt("123");            // String → int, throws if invalid
Integer.valueOf("123");             // String → Integer object
String.valueOf(123);                // int → String
Character.isDigit('5');
Character.isLetter('a');
Character.isAlphabetic('a');
Character.isUpperCase('A');
Character.isLowerCase('a');
Character.toUpperCase('a');
Character.toLowerCase('A');
int digit = c - '0';                // char digit → int value
int index = c - 'a';                // char a-z → 0-25
int index = c - 'A';                // char A-Z → 0-25
```

### StringBuilder

```java
// Creation
StringBuilder sb = new StringBuilder();
StringBuilder sb = new StringBuilder("hello");

// Top methods
sb.append("world");                 // add string at end, O(1) amortized
sb.append(123);                     // add int, converts to string
sb.insert(2, "XX");                 // insert at index, O(n)
sb.delete(1, 3);                    // remove [1, 3)
sb.deleteCharAt(2);                 // remove at index
sb.reverse();                       // reverse in-place, modifies sb itself (does NOT return new sb)
sb.charAt(2);                       // access char
sb.length();                        // length
sb.toString();                      // StringBuilder → String
sb.setCharAt(2, 'X');               // update char at index
```

### char Array

```java
// Creation and conversion
String s = "hello";
char[] arr = s.toCharArray();       // String → char[]
String s2 = new String(arr);        // char[] → String

// Top methods
arr[2] = 'X';                       // modify in-place
int len = arr.length;
// Reverse in-place
int left = 0, right = arr.length - 1;
while (left < right) {
    char temp = arr[left];
    arr[left] = arr[right];
    arr[right] = temp;
    left++;
    right--;
}
```

---

## 🔧 Useful Utilities

### Collections

```java
Collections.sort(list);             // sort ArrayList
Collections.sort(list, Comparator.reverseOrder());
Collections.reverse(list);          // reverse ArrayList
Collections.min(list);              // minimum element
Collections.max(list);              // maximum element
Collections.frequency(list, value); // count occurrences
Collections.swap(list, i, j);       // swap elements
Collections.unmodifiableList(list); // read-only view
```

### Arrays Utility

```java
Arrays.sort(arr);                   // sort array
Arrays.sort(arr, Comparator.reverseOrder());  // Note: use Integer[] not int[]
Arrays.fill(arr, value);            // fill all with value
Arrays.copyOf(arr, newLength);      // copy, pad or truncate
Arrays.copyOfRange(arr, from, to);  // copy subarray [from, to)
Arrays.equals(arr1, arr2);          // compare
Arrays.binarySearch(arr, target);   // on sorted array
Arrays.asList(1, 2, 3);             // create List from varargs or array
```

---

## 🧠 Method Naming Patterns

**Queue operations:**
- `add()` vs `offer()` — both enqueue, offer safer (no exception on capacity)
- `remove()` vs `poll()` — both dequeue, poll safer (returns null vs exception)
- `element()` vs `peek()` — both view head, peek safer (returns null vs exception)

**Stack operations (ArrayDeque):**
- `push()` = `addFirst()` — add to top
- `pop()` = `removeFirst()` — remove from top
- `peek()` = `getFirst()` — view top

**Map methods:**
- `put(k, v)` — insert/update
- `get(k)` — retrieve, null if absent
- `getOrDefault(k, defaultValue)` — safely get with fallback
- `merge(k, v, remappingFunction)` — combine old value with new
- `putIfAbsent(k, v)` — insert only if absent, returns final value
- `computeIfAbsent(k, f)` — if absent, compute value using function f, insert, return

**Set methods:**
- `add(e)` — returns true if added, false if already present
- `contains(e)` — membership check
- `remove(e)` — returns true if was present, false if absent

---

## 🔹 Common Gotchas — Syntax

**Integer vs int:**
```java
int[] arr = new int[5];             // primitive int array, initialized to 0
Integer[] arr = new Integer[5];     // Integer array, initialized to null
ArrayList<Integer> list = ...;      // never ArrayList<int>, must be Integer
```

**Array vs .length vs .size():**
```java
int[] arr = new int[5];
arr.length;                         // field, not method!

ArrayList<Integer> list = ...;
list.size();                        // method, not .length

String s = "hello";
s.length();                         // method
```

**String.substring()**:
```java
String s = "hello";
s.substring(1, 4);                  // [1, 4) = "ell" (end exclusive!)
s.substring(2);                     // [2, end] = "llo"
```

**String immutability:**
```java
String s = "hello";
s.toUpperCase();                    // returns new String, s still "hello"
String s2 = s.toUpperCase();        // s2 = "HELLO"
```

**StringBuilder.reverse():**
```java
StringBuilder sb = new StringBuilder("hello");
sb.reverse();                       // modifies sb in-place, sb now "olleh"
// Does NOT return a new object like String.reverse() (which doesn't exist)
```

**PriorityQueue iteration:**
```java
PriorityQueue<Integer> pq = new PriorityQueue<>();
pq.add(5); pq.add(2); pq.add(8);
for (Integer x : pq) { }            // NOT in [2, 5, 8] order!
// To get sorted: poll() one by one
```

---

## 🧾 Quick Copy-Paste Templates

**Frequency map:**
```java
Map<Character, Integer> freq = new HashMap<>();
for (char c : s.toCharArray()) {
    freq.merge(c, 1, Integer::sum);
}
```

**Two-pointer array:**
```java
int left = 0, right = arr.length - 1;
while (left < right) {
    // process arr[left] and arr[right]
    left++;
    right--;
}
```

**Tree traversal (BFS):**
```java
Queue<TreeNode> q = new LinkedList<>();
q.offer(root);
while (!q.isEmpty()) {
    TreeNode node = q.poll();
    // process node
    if (node.left != null) q.offer(node.left);
    if (node.right != null) q.offer(node.right);
}
```

**Monotonic stack (next greater element):**
```java
Stack<Integer> stack = new Stack<>();
int[] result = new int[nums.length];
for (int i = nums.length - 1; i >= 0; i--) {
    while (!stack.isEmpty() && stack.peek() <= nums[i]) {
        stack.pop();
    }
    result[i] = stack.isEmpty() ? -1 : stack.peek();
    stack.push(nums[i]);
}
```

---

## 🎯 Final Checklist Before Interview

- [ ] ArrayList: add, get, size, remove, clear
- [ ] HashMap: put, get, getOrDefault, entrySet iteration, merge
- [ ] TreeMap: floorKey, ceilingKey, subMap
- [ ] PriorityQueue: creation (min/max), offer, poll, peek
- [ ] ArrayDeque as Stack: push, pop, peek
- [ ] ArrayDeque as Queue: offer, poll, peek
- [ ] String: length(), charAt, substring, indexOf, toCharArray, split
- [ ] StringBuilder: append, reverse, toString
- [ ] TreeSet/HashSet: add, remove, contains
- [ ] char to int: c - '0' and c - 'a'
- [ ] Collections.sort, Arrays.sort
