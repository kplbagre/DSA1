# eBay MTS1 — LLD Battle Notes

> **File type:** Company-specific interview strategy note — eBay MTS 1 (Backend), Bangalore / Toronto.
> **Companion files:** Raw research → `../../DSA/Interview/ebay-mts1-research.md` | DSA solutions → `../../DSA/Interview/ebay-mts1-dsa-problems.md`
> **Confidence:** Format claims backed by 30+ sources across LC Discuss, Glassdoor, Blind, 1Point3Acres, CodingKaro (Jul 2026). See Research file §5 for the full source index.

---

## 🗺️ Table of Contents

1. [The Key Insight — Read This First](#-the-key-insight--read-this-first)
2. [Onsite Format (Confirmed)](#-onsite-format-confirmed)
3. [Problem 1 — HTML/XML Parser (OOP emphasis)](#-problem-1--htmlxml-parser--n-ary-tree)
4. [Problem 2 — Weighted Grouping (OOP emphasis)](#-problem-2--weighted-grouping-with-oop-design)
5. [Problem 3 — `ls -r` with JUnit (OOP emphasis)](#-problem-3--implement-ls--r-with-junit-tests)
6. [What to Have Ready — Pattern Map](#-what-to-have-ready--pattern-map)
7. [Spring Boot Round (Toronto Only — Low Confidence)](#-spring-boot-round--toronto-only)
8. [Cross-References](#-cross-references)

---

## 🎯 The Key Insight — Read This First

There is **no dedicated 1-hour LLD round** at eBay MTS1. Unlike companies that run a standalone "design a parking lot" interview, eBay folds OOP design expectations directly into the DSA round (R1).

| What you might expect | What actually happens |
|---|---|
| Dedicated 60-min LLD interview | No dedicated round — OOP is embedded inside R1 (DSA) |
| "Design Parking Lot from scratch" | OOP baked into a DSA problem: "parse this XML into a tree" |
| System Design round covers LLD | R2 is pure HLD — notification service, Dropbox, flash sale |

**eBay's actual bar:** You solve the DSA problem *and* your class design is production-quality. Multiple candidates who got the algorithm right but wrote procedural code without proper classes were explicitly dinged in reports. The OOP is not optional.

---

## 🧠 Onsite Format (Confirmed)

### 🎨 Visual — eBay MTS1 Interview Structure

```
┌─────────────────────────────────────────────────────────────────────┐
│  R1 — DSA  (60 min, CodeSignal, provided laptop)                    │
│                                                                     │
│  2 problems. P1 must submit before P2 unlocks.                      │
│  OOP class design expected here — even in "algorithm" problems.     │
│                                                                     │
│  ← THIS IS WHERE LLD SKILL IS TESTED AT EBAY MTS1 →               │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│  R2 — System Design / HLD  (45–60 min, virtual whiteboard)          │
│                                                                     │
│  Notification Service, Dropbox, Flash Sale, Ad Click Storage.       │
│  Pure architecture: component diagrams, trade-offs, failure modes.  │
│  No class-level design expected here.                               │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────────────┐
│  R3 — Director  (45 min, conversational)                            │
│                                                                     │
│  Walk through YOUR own past project in depth.                       │
│  Sometimes a trivial DSA warm-up (e.g., Count Primes — see §9).    │
│  Not an LLD or HLD round — a systems-thinking depth probe.         │
└─────────────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌──────────────────────────────────────────────┐
│  Behavioral + Hiring Manager (non-technical) │
└──────────────────────────────────────────────┘

KEY INVARIANT:
   There is no standalone LLD round. OOP discipline is tested in R1.
   A candidate who writes procedural code in R1 has no round to "make up" LLD skill.
```

> **⚠️ Toronto exception:** One report (CodingKaro, Apr 2025, single source — may be atypical) describes a live Spring Boot coding round at the Toronto office. BLR reports do not mention this. See [§7 Spring Boot Round](#-spring-boot-round--toronto-only). **Confirm your loop format with your recruiter before investing prep time there.**

---

## 🏗️ Problem 1 — HTML/XML Parser → N-ary Tree

**Tier:** ⭐ High confidence (2+ independent reports — Glassdoor eBay SWE 2025, Blind eBay thread Apr 2025)
**Full DSA solution (algorithm + code):** `../../DSA/Interview/ebay-mts1-dsa-problems.md` §2
**Pattern:** Stack-based parsing + N-ary Tree OOP (an N-ary tree is a tree where each node can have any number of children — not limited to 2 like a binary tree)

---

### What the interviewer is actually evaluating

Multiple reports say candidates who "solved it algorithmically" but wrote plain arrays instead of a proper class structure were explicitly called out. The interviewer asks for class design *before* any code is written.

| Interviewer says | They are testing |
|---|---|
| "Walk me through your class design before coding" | Can you identify `Node` as the core entity? Fields, access modifiers? |
| "What does your Node look like?" | Encapsulation (making `children` private and controlled via `addChild()`) |
| "What if the input is malformed?" | Exception hierarchy — custom exception over a generic throw |
| "Can you add/remove nodes after parsing?" | Extension points — `addChild()`, `removeChild()` methods on `Node` |

---

### 🎨 Visual — N-ary Tree OOP Class Design

```
Input: "<div><p>text</p><span/></div>"

After parsing → N-ary tree:

        Node("div")
       /           \
  Node("p")    Node("span")
  data="text"  (no children)

Class structure:

  Node
  ┌──────────────────────────────┐
  │ - tag: String                │
  │ - data: String               │
  │ - children: List<Node>  ←── private; callers can't mutate raw list
  │                              │
  │ + addChild(Node): void       │
  │ + getChildren(): List<Node>  │  ← returns unmodifiable view
  │ + getTag(): String           │
  │ + getData(): String          │
  │ + setData(String): void      │
  └──────────────────────────────┘

  HtmlParser                     MalformedXMLException
  ┌──────────────────┐           ┌──────────────────────────────┐
  │ + parseHTML(     │           │ extends RuntimeException     │
  │   String): Node  │           │ + MalformedXMLException(msg) │
  └──────────────────┘           └──────────────────────────────┘

KEY INVARIANT:
   Node controls its own children list — callers go through addChild().
   This is Encapsulation (hiding internal state, enforcing invariants).
   The algorithm (stack-based tokenizer) lives in HtmlParser — SRP.
```

---

### OOP Skeleton (the LLD focus — algorithm is in DSA file §2)

**Steps in plain English:**

1. `Node` holds tag, data, and a **private** children list — callers cannot reach in and corrupt it.
2. `addChild()` is the only mutation point. `getChildren()` returns an unmodifiable view.
3. `HtmlParser` owns only the parsing responsibility — SRP (Single Responsibility Principle).
4. `MalformedXMLException` is a custom exception so the caller can `catch` specifically — not generic `RuntimeException`.

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Node {

    private final String tag;
    private String data;
    // Encapsulation: children is private; external code cannot corrupt the list
    private final List<Node> children;

    public Node(String tag) {
        this.tag = tag;
        this.data = "";
        this.children = new ArrayList<>();
    }

    public void addChild(Node child) {
        this.children.add(child);
    }

    // Defensive copy — callers see the children but cannot modify the list
    public List<Node> getChildren() {
        return Collections.unmodifiableList(this.children);
    }

    public String getTag() {
        return this.tag;
    }

    public String getData() {
        return this.data;
    }

    public void setData(String data) {
        this.data = data;
    }
}

// Custom exception — lets the caller distinguish "malformed XML" from "NPE"
public class MalformedXMLException extends RuntimeException {

    public MalformedXMLException(String message) {
        super(message);
    }
}

// SRP: HtmlParser has exactly one responsibility — parse an XML string into a tree
public class HtmlParser {

    // Full stack-based algorithm → see DSA file §2
    // This skeleton shows the OOP boundary the interviewer expects
    public Node parseHTML(String xml) {
        if (xml == null || xml.isEmpty()) {
            throw new MalformedXMLException("Input is null or empty");
        }
        // tokenize → stack walk → return root
        // See ../../DSA/Interview/ebay-mts1-dsa-problems.md §2 for full implementation
        return null;
    }
}
```

**Interview drop-in:**
> *"`children` is private on `Node` — Encapsulation. The only way to add a child is through `addChild()`. If I returned the raw list, any caller could mutate it silently. `getChildren()` returns an unmodifiable view for the same reason. The parsing algorithm lives entirely in `HtmlParser` — Single Responsibility."*

---

## 🏗️ Problem 2 — Weighted Grouping with OOP Design

**Tier:** ⭐ High confidence (2+ independent reports — CodingKaro Apr 2025, 1Point3Acres Mar 2025)
**Full DSA solution:** `../../DSA/Interview/ebay-mts1-dsa-problems.md` §5
**Pattern:** Greedy first-fit decreasing (a heuristic bin-packing algorithm — sort items largest-to-smallest, then try to fit each into the first bucket that has room) + OOP class design with optional Strategy extension

---

### What the interviewer is actually evaluating

The greedy algorithm itself is not hard. The OOP design is the real test. Multiple reports state the interviewer said "walk me through your class design before you write any code" verbatim.

| Interviewer says | They are testing |
|---|---|
| "Walk me through your class design" | Three entities: `Item`, `Bucket`, `Grouper` — can you identify them? |
| "Why does `Bucket` have `addItem()` instead of a public list?" | Encapsulation — Bucket enforces its own capacity invariant |
| "What if we add a max item count per bucket?" | OCP (Open-Closed) — add a field + condition to `Bucket`, nothing else changes |
| "Can `Grouper` be swapped for a different algorithm?" | Strategy — extract `Grouper` to an interface, make current impl one strategy |

---

### 🎨 Visual — Domain Model (Class Structure)

```
Item                             Bucket
─────────────────────            ──────────────────────────────────────
- name: String    (final)        - capacity: int           (final)
- weight: int     (final)        - items: List<Item>       (private)
                                 - currentWeight: int      (private)
+ getName(): String              + addItem(Item): boolean  ← returns false if full
+ getWeight(): int               + getItems(): List<Item>
                                 + getCurrentWeight(): int

        Grouper
        ────────────────────────────────────────────────────
        - bucketCapacity: int
        + group(List<Item>): List<Bucket>
        │
        │ Algorithm: sort items descending by weight,
        │ then greedy first-fit: try each existing bucket;
        │ open a new bucket only if none fit.
        │
        └── Optional extension: extract to interface:
            interface Grouper { List<Bucket> group(items, capacity); }
            GreedyFirstFitGrouper implements Grouper

KEY INVARIANT:
   Bucket enforces its own capacity — addItem() returns false when over limit.
   The Grouper cannot violate Bucket's constraint even by accident.
   This is Encapsulation: the invariant lives with the data, not with the caller.
```

---

### OOP Skeleton

**Steps in plain English:**

1. `Item` is an immutable data holder — `final` fields, no setters.
2. `Bucket` is the capacity guard — `addItem()` checks before accepting.
3. `Grouper` runs the greedy algorithm — callers only call `group()`.
4. (Extension if asked) Extract `Grouper` to an interface → Strategy pattern.

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Item {

    private final String name;
    private final int weight;

    public Item(String name, int weight) {
        this.name = name;
        this.weight = weight;
    }

    public String getName() {
        return this.name;
    }

    public int getWeight() {
        return this.weight;
    }
}

public class Bucket {

    private final int capacity;
    // Encapsulation: items is private; callers use addItem(), not direct list access
    private final List<Item> items;
    private int currentWeight;

    public Bucket(int capacity) {
        this.capacity = capacity;
        this.items = new ArrayList<>();
        this.currentWeight = 0;
    }

    // Bucket enforces its own capacity invariant — callers cannot bypass this
    public boolean addItem(Item item) {
        if (this.currentWeight + item.getWeight() > this.capacity) {
            return false;
        }
        this.items.add(item);
        this.currentWeight += item.getWeight();
        return true;
    }

    public List<Item> getItems() {
        return Collections.unmodifiableList(this.items);
    }

    public int getCurrentWeight() {
        return this.currentWeight;
    }
}

public class Grouper {

    private final int bucketCapacity;

    public Grouper(int bucketCapacity) {
        this.bucketCapacity = bucketCapacity;
    }

    // Algorithm: greedy first-fit decreasing → full walkthrough in DSA file §5
    public List<Bucket> group(List<Item> items) {
        List<Item> sorted = new ArrayList<>(items);
        // Sort descending by weight — largest items placed first
        sorted.sort((a, b) -> b.getWeight() - a.getWeight());
        List<Bucket> buckets = new ArrayList<>();
        for (Item item : sorted) {
            boolean placed = false;
            for (Bucket bucket : buckets) {
                if (bucket.addItem(item)) {
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                Bucket newBucket = new Bucket(this.bucketCapacity);
                newBucket.addItem(item);
                buckets.add(newBucket);
            }
        }
        return buckets;
    }
}
```

**Extension — Strategy pattern (if interviewer asks "can we swap algorithms?"):**

**Steps in plain English:**

1. Extract `Grouper` to an interface with a single `group()` method.
2. The current implementation becomes `GreedyFirstFitGrouper implements Grouper`.
3. The caller holds a `Grouper` reference — swapping algorithms requires zero changes at the call site.

```java
// Step 1 — Grouper becomes the interface (Strategy contract)
public interface Grouper {

    List<Bucket> group(List<Item> items, int bucketCapacity);
}

// Step 2 — current implementation is now one strategy
public class GreedyFirstFitGrouper implements Grouper {

    @Override
    public List<Bucket> group(List<Item> items, int bucketCapacity) {
        // same algorithm as above
        List<Item> sorted = new ArrayList<>(items);
        sorted.sort((a, b) -> b.getWeight() - a.getWeight());
        List<Bucket> buckets = new ArrayList<>();
        for (Item item : sorted) {
            boolean placed = false;
            for (Bucket bucket : buckets) {
                if (bucket.addItem(item)) {
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                Bucket newBucket = new Bucket(bucketCapacity);
                newBucket.addItem(item);
                buckets.add(newBucket);
            }
        }
        return buckets;
    }
}
```

**Interview drop-in:**
> *"I extracted `Grouper` to an interface — Strategy pattern, Open-Closed. Adding a random-fit or best-fit algorithm is one new class; the client code holds `Grouper grouper = new GreedyFirstFitGrouper()` and never needs to change."*
>
> *"`Bucket.addItem()` returns false when the item doesn't fit instead of throwing — Encapsulation. The grouper reacts to the boolean; the capacity invariant lives with the data, not with the caller."*

---

## 🏗️ Problem 3 — Implement `ls -r` with JUnit Tests

**Tier:** 🔹 Lower confidence (CodingKaro eBay Toronto MTS1, Apr 2025 — 1 report)
**Full DSA solution:** Not yet in DSA file — this section covers the full OOP skeleton.
**Pattern:** Composite pattern (treating files and directories uniformly through the same type)

---

### What the interviewer is actually evaluating

This is the only reported problem that explicitly asked for JUnit unit tests in R1. The report states ~35 min for implementation, ~25 min for tests. Two skills are tested together: Composite-pattern OOP design AND test-case thinking.

| Interviewer says | They are testing |
|---|---|
| "Design the classes first" | Composite pattern: `FileSystemNode` handles both file and directory |
| "Implement `ls -r`" | Recursive DFS; accumulate paths during traversal |
| "Write JUnit tests for it" | Test-case design: edge cases (empty dir, single file, nested, mixed) |
| "What if someone adds a child to a file?" | Exception discipline — guard against invalid operations |

---

### 🎨 Visual — Composite Pattern for File System

```
Composite pattern: FileSystemNode represents BOTH files and directories.
Client calls list() on either — no instanceof check needed.

  root/  (isDirectory=true)
  ├── src/  (isDirectory=true)
  │   ├── Main.java  (isDirectory=false)
  │   └── Util.java  (isDirectory=false)
  └── README.md  (isDirectory=false)

FileSystemNode design:

  FileSystemNode
  ┌──────────────────────────────────────────┐
  │ - name: String                           │
  │ - isDirectory: boolean                   │
  │ - children: List<FileSystemNode>  ←── empty if file
  │                                          │
  │ + addChild(FileSystemNode): void  ←── throws if called on a file
  │ + list(String prefix): List<String>  ←── recursive; files return self, dirs recurse
  └──────────────────────────────────────────┘

list("") on root above produces:
  ["root/src/Main.java", "root/src/Util.java", "root/README.md"]

KEY INVARIANT:
   Both files and directories respond to list() — that's the Composite pattern.
   Files return themselves. Directories recurse into children.
   No caller ever asks "is this a file or a directory?" before calling list().
```

---

### OOP Skeleton

**Steps in plain English:**

1. `FileSystemNode` uses `isDirectory` to distinguish files from directories.
2. `addChild()` throws if called on a file node — a file cannot have children.
3. `list(prefix)` recurses: files append their path; directories delegate to children.
4. JUnit tests cover: empty directory, single file, nested directories, mixed content, invalid `addChild()`.

```java
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FileSystemNode {

    private final String name;
    private final boolean isDirectory;
    // Encapsulation: children is private; callers use addChild() only
    private final List<FileSystemNode> children;

    public FileSystemNode(String name, boolean isDirectory) {
        this.name = name;
        this.isDirectory = isDirectory;
        this.children = new ArrayList<>();
    }

    // Guard: a file node cannot have children — throw to enforce the invariant
    public void addChild(FileSystemNode child) {
        if (!this.isDirectory) {
            throw new IllegalStateException("Cannot add child to a file: " + this.name);
        }
        this.children.add(child);
    }

    public List<FileSystemNode> getChildren() {
        return Collections.unmodifiableList(this.children);
    }

    public String getName() {
        return this.name;
    }

    public boolean isDirectory() {
        return this.isDirectory;
    }

    // Composite: files return themselves; directories recurse into children
    public List<String> list(String prefix) {
        List<String> result = new ArrayList<>();
        String current = prefix.isEmpty() ? this.name : prefix + "/" + this.name;
        if (!this.isDirectory) {
            // Base case: this node is a file — its path is the result
            result.add(current);
            return result;
        }
        // Recursive case: this node is a directory — collect from all children
        for (FileSystemNode child : this.children) {
            result.addAll(child.list(current));
        }
        return result;
    }
}
```

### JUnit 5 Tests (what the interviewer explicitly asked for)

```java
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class FileSystemNodeTest {

    @Test
    void emptyDirectory_returnsEmptyList() {
        FileSystemNode root = new FileSystemNode("root", true);
        assertTrue(root.list("").isEmpty());
    }

    @Test
    void singleFile_returnsFileName() {
        FileSystemNode file = new FileSystemNode("readme.txt", false);
        assertEquals(List.of("readme.txt"), file.list(""));
    }

    @Test
    void nestedDirectories_returnsFullPath() {
        FileSystemNode root = new FileSystemNode("root", true);
        FileSystemNode docs = new FileSystemNode("docs", true);
        FileSystemNode readme = new FileSystemNode("readme.txt", false);
        docs.addChild(readme);
        root.addChild(docs);
        assertEquals(List.of("root/docs/readme.txt"), root.list(""));
    }

    @Test
    void mixedContent_returnsAllFilePaths() {
        FileSystemNode root = new FileSystemNode("root", true);
        FileSystemNode src = new FileSystemNode("src", true);
        FileSystemNode main = new FileSystemNode("Main.java", false);
        FileSystemNode util = new FileSystemNode("Util.java", false);
        src.addChild(main);
        src.addChild(util);
        root.addChild(src);
        List<String> result = root.list("");
        assertEquals(2, result.size());
        assertTrue(result.contains("root/src/Main.java"));
        assertTrue(result.contains("root/src/Util.java"));
    }

    @Test
    void addChildToFile_throwsIllegalStateException() {
        FileSystemNode file = new FileSystemNode("readme.txt", false);
        FileSystemNode child = new FileSystemNode("child.txt", false);
        assertThrows(IllegalStateException.class, () -> file.addChild(child));
    }
}
```

**Interview drop-in:**
> *"The Composite pattern here means `FileSystemNode` represents both files and directories. `list()` works on both — files return their path, directories recurse. The caller never does `instanceof FileNode` or `instanceof DirNode`. That's exactly what Composite gives you: treat individual and composite objects uniformly."*

---

## 📐 What to Have Ready — Pattern Map

| Problem | Algorithm | OOP Pattern | SOLID Principle |
|---|---|---|---|
| HTML/XML Parser | Stack-based O(n) | N-ary Tree + Encapsulation | SRP (`HtmlParser` vs `Node`), OCP (extension methods) |
| Weighted Grouping | Greedy first-fit decreasing | Strategy (if extended) | OCP (new grouper = new class), SRP (`Bucket` owns its constraint) |
| `ls -r` | DFS recursion | Composite | OCP (add a new node type without changing `list()`), SRP |

**LLD folder files that directly apply — cross-references (relative from `LLD/Interview/`):**

| What you need | Where it lives |
|---|---|
| Encapsulation, Composition vs Inheritance | `../Foundations/01-oop-concepts.md` |
| Composition vs aggregation, IS-A | `../Foundations/04-relationships.md` |
| SOLID principles (SRP, OCP, DIP) | `../Foundations/02-solid-principles.md` |
| Strategy pattern note | `../DesignPatterns/01-factory-strategy.md` |
| Composite pattern note (for `ls -r`) | `../DesignPatterns/07-composite.md` |
| KISS, DRY, SoC drop-in phrases | `../Foundations/03-design-principles.md` |
| 60-min execution playbook | `../InterviewPlaybook/execution-guide.md` |

> **✅ Composite pattern note now exists:** `../DesignPatterns/07-composite.md` covers the file-system / tree pattern the `ls -r` problem uses (leaf + container behind one interface). The skeleton in §3 above remains a good quick reference.

---

## ⚠️ Spring Boot Round — Toronto Only

**Source:** CodingKaro eBay Toronto MTS1 (Apr 2025) — single report, explicitly noted as potentially atypical.

> **Do NOT treat this as a standard eBay MTS1 round.** All BLR and San Jose reports follow R1 DSA → R2 HLD → R3 Director. No BLR report mentions a live Spring Boot coding session. Prepare this section only if your recruiter confirms a Toronto loop. Investing prep time here before confirming your office is a bad trade-off.

**What was reported (Toronto loop only):**

- 60 min live coding in IDE — NOT a whiteboard or class-design discussion.
- Build a product catalog REST API: `GET /products`, `POST /products`, `GET /products/{id}`.
- Entities: `Product`, `Category`, `Discount`.
- A discount rule engine was required — this is the Strategy pattern applied in a Spring Boot context.

**The discount rule engine (Strategy pattern):**

**Steps in plain English:**

1. `DiscountRule` is the Strategy interface — each implementation applies its rule to a price.
2. `PercentageDiscount` and `FixedDiscount` are two concrete strategies.
3. `Product` holds a nullable `DiscountRule` — null means no discount.

```java
// Step 1 — Strategy interface: each rule knows how to apply itself
public interface DiscountRule {

    double apply(double originalPrice);
}

// Step 2 — concrete strategy: percentage off
public class PercentageDiscount implements DiscountRule {

    private final double percentage;

    public PercentageDiscount(double percentage) {
        this.percentage = percentage;
    }

    @Override
    public double apply(double originalPrice) {
        return originalPrice * (1.0 - this.percentage / 100.0);
    }
}

// Step 2 — concrete strategy: fixed amount off (floor at 0)
public class FixedDiscount implements DiscountRule {

    private final double amount;

    public FixedDiscount(double amount) {
        this.amount = amount;
    }

    @Override
    public double apply(double originalPrice) {
        return Math.max(0.0, originalPrice - this.amount);
    }
}
```

```java
// Step 3 — Product holds a nullable DiscountRule (null = no discount)
// Java 17+ record for concise immutability
public record Product(
    long id,
    String name,
    String category,
    double price,
    DiscountRule discountRule
) {
    public double effectivePrice() {
        return discountRule == null ? price : discountRule.apply(price);
    }
}
```

**For the Spring Boot REST layer — what the interviewer evaluates:**

- `@RestController` → `@Service` → `@Repository` layering (SoC — each layer has one concern)
- `GET /products/{id}` returns `404 Not Found` when product is missing — not a silent null
- `POST /products` uses `@Valid` + `@NotNull` on the request DTO — input validation

```java
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import javax.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<Product> getAllProducts() {
        return productService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable long id) {
        return productService.findById(id)
                .map(ResponseEntity::ok)
                // 404 if not found — not a null or an exception without a response code
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Product> createProduct(@Valid @RequestBody ProductRequest request) {
        Product created = productService.create(request);
        return ResponseEntity.ok(created);
    }
}
```

**Interview drop-in (if this round occurs):**
> *"The controller delegates entirely to the service — Separation of Concerns. If business logic changes, the controller doesn't need to be touched. The 404 for missing products is explicit — a missing resource is a client error, not a server error."*

---

## 🔗 Cross-References

| What | Path (relative from `LLD/Interview/`) |
|---|---|
| Raw research — format, source index | `../../DSA/Interview/ebay-mts1-research.md` |
| Full DSA solutions — Problem §2 (Parser), §5 (Grouping) | `../../DSA/Interview/ebay-mts1-dsa-problems.md` |
| OOP pillars — Encapsulation, Composition vs Inheritance | `../Foundations/01-oop-concepts.md` |
| Relationships — composition vs aggregation, IS-A | `../Foundations/04-relationships.md` |
| SOLID principles — SRP, OCP, DIP | `../Foundations/02-solid-principles.md` |
| Strategy pattern | `../DesignPatterns/01-factory-strategy.md` |
| Composite pattern (`ls -r`) | `../DesignPatterns/07-composite.md` |
| Design principles — KISS, DRY, SoC | `../Foundations/03-design-principles.md` |
| 60-min execution playbook | `../InterviewPlaybook/execution-guide.md` |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 11, 2026 | File created. LLD/Interview folder created. Strategy note covering: no-dedicated-LLD-round format context, 3 OOP-heavy DSA problems (Parser, Grouping, ls -r), Spring Boot Toronto variant (low confidence). |
