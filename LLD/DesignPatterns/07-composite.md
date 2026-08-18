# Composite Pattern

> **Standard followed:** `../notes-standards.md` · **Index:** `../README.md`
>
> **Why this note exists:** the "file system" family of problems (`mkdir`, `ls -r`, `du`,
> `find`) and any tree-shaped domain (org charts, UI component trees, nested menus, bill-of-
> materials) all reduce to Composite. It was the one structural pattern missing from
> `DesignPatterns/` — previously buried inside `../Interview/ebay-mts1-lld.md`.

---

## 🎯 What Problem Does This Pattern Solve?

You have a **tree**: some nodes are *leaves* (a file) and some are *containers* (a directory) that hold more nodes. The naive code is riddled with `if (node is directory) … else …` branches, and every operation (`size()`, `print()`, `search()`) re-implements the same recursion by hand.

**Composite lets the client treat a single leaf and a whole subtree through the exact same interface** — so `directory.size()` and `file.size()` are called identically, and the recursion lives *inside* the structure, not in the caller.

---

## 🧠 Mental Model

A **folder on your computer.** When you ask a folder for its total size, you don't care that inside it there are files *and* more folders — you just get one number. The folder figures out its own total by asking each child for *its* size, and a sub-folder does the same, all the way down. You (the client) said one thing — `getSize()` — to the top and the whole tree cooperated. **Uniform treatment of "one thing" and "a group of things" is the whole pattern.**

---

## 🔌 The Interface Contract

The key move: **the container and the leaf implement the *same* interface.** That interface is what the client depends on.

```java
// The common contract — a leaf and a container both ARE a FileSystemNode.
public interface FileSystemNode {

    String getName();

    // Every node can report its size. A file returns its own; a directory sums its children.
    long getSize();

    // Every node can render itself. Depth drives indentation for a tree view.
    void print(String indent);
}
```

> **Design note:** whether `add(child)` / `remove(child)` belong on the shared interface or
> only on the container is the classic Composite trade-off — see §6.

---

## ⚙️ Implementation

**Steps in plain English:**

1. **Define the common interface** (`FileSystemNode`) with the operations every node supports — `getSize()`, `print()`.
2. **Write the leaf** (`File`) — it has no children, so its operations return its own value directly (base case of the recursion).
3. **Write the composite** (`Directory`) — it holds a `List<FileSystemNode>` and implements each operation by **delegating to its children and combining the results** (recursive case).
4. **Client calls the interface** — it never checks "is this a file or a directory?"; the tree handles its own recursion.

```java
// Step 2 — the LEAF: no children; it is the base case
public class File implements FileSystemNode {

    private final String name;
    private final long sizeBytes;

    public File(String name, long sizeBytes) {
        this.name = name;
        this.sizeBytes = sizeBytes;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getSize() {
        // base case — a file just knows its own size
        return sizeBytes;
    }

    @Override
    public void print(String indent) {
        // a file prints itself with its size
        System.out.println(indent + "- " + name + " (" + sizeBytes + "B)");
    }
}
```

```java
// Step 3 — the COMPOSITE: holds children; it is the recursive case
public class Directory implements FileSystemNode {

    private final String name;
    private final List<FileSystemNode> children = new ArrayList<>();

    public Directory(String name) {
        this.name = name;
    }

    // container-only operation — building the tree
    public void add(FileSystemNode node) {
        children.add(node);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public long getSize() {
        long total = 0;
        // recursive case — ask each child (file OR sub-directory) for ITS size
        for (FileSystemNode child : children) {
            total += child.getSize();
        }
        return total;
    }

    @Override
    public void print(String indent) {
        System.out.println(indent + "+ " + name + "/");
        // recurse into every child with deeper indentation
        for (FileSystemNode child : children) {
            child.print(indent + "  ");
        }
    }
}
```

```java
// Step 4 — the CLIENT: no type checks, no manual recursion
Directory root = new Directory("root");
Directory src = new Directory("src");
src.add(new File("Main.java", 1200));
src.add(new File("Util.java", 800));
root.add(src);
root.add(new File("README.md", 300));

// Same call on the whole tree as on a single file — that's the point.
long total = root.getSize();   // 2300 — recursion happened inside the tree
root.print("");
```

---

## 🏢 Real World Usage

- **Java AWT / Swing** — `Component` is the leaf, `Container` is the composite. A `JPanel` holds `Component`s (buttons *and* nested panels); calling `paint()` on the top window recursively paints the whole UI tree.
- **The DOM (browsers)** — every HTML element is a node; a `<div>` contains text nodes *and* more elements. `element.textContent` walks the subtree uniformly.
- **File systems (`du`, `ls -r`, `find`)** — the canonical example above; the OS treats a file and a directory as the same "inode" abstraction.
- **Org charts / approval chains** — an `Employee` and a `Manager` (who has reports) both answer `getHeadcount()`; a manager sums its reports recursively.

---

## 🧭 When to Use vs When NOT to Use

| Use Composite when… | Do NOT use it when… |
|---|---|
| Your data is a **tree / part-whole hierarchy** | The data is flat (a list or map) — Composite is pure overhead |
| Clients should treat **one item and a group identically** | Leaves and containers genuinely need *different* client-facing APIs |
| Operations are naturally **recursive** (size, print, search, cost) | There is only ever one operation and no nesting — a plain loop is simpler |
| The hierarchy depth is **arbitrary / unknown** | Depth is fixed and shallow (e.g., always exactly 2 levels) |

---

## 🧩 LLD Problems That Use This Pattern

- **File System (`mkdir`, `ls -r`, `du`)** — `File` (leaf) and `Directory` (composite) share a `FileSystemNode` interface; `getSize()` and `ls -r` recurse through the tree with zero type checks in the caller.
- **`ls -r` (eBay MTS1)** — recursive listing is exactly `Directory.print()` delegating into children; see `../Interview/ebay-mts1-lld.md` for the full worked problem.
- **Menu / navigation systems** — a `MenuItem` (leaf) and a `SubMenu` (composite) both implement `render()`, so a menu of arbitrary nesting renders through one call.
- **Organisation hierarchy** — `getTotalSalary()` on a `Manager` sums leaf employees plus sub-managers recursively.

---

## 🔬 Interview Q&As

**Q: Composite vs just using a recursive function over a tree?**
> A raw recursive function forces the *caller* to branch on node type (`if file … else directory …`) in every operation. Composite pushes that branch *into the type system* — the leaf and container implement the same interface, so the caller writes one call and never type-checks. Adding a new operation means adding a method to the interface, not editing every call site.

**Q: Where do `add(child)` and `remove(child)` go — the shared interface or only the container?**
> Two schools. **Transparent** Composite puts `add`/`remove` on the shared interface so *everything* is treated uniformly — but then `File.add()` has to throw `UnsupportedOperationException` (an LSP smell). **Safe** Composite puts them only on `Directory`, so a leaf never exposes a method it can't honour — but the client must sometimes downcast to add children. I default to **safe** (child-management only on the container) because it respects Interface Segregation; I'd only go transparent if the client genuinely must add children without knowing the concrete type.

**Q: How does Composite relate to the other patterns?**
> It's structural, and it composes well with others: **Iterator** to traverse the tree, **Visitor** to add new operations without touching the node classes, and **Decorator** (which also has a recursive "wraps one child" shape — but Decorator wraps *exactly one* component to add behaviour, while Composite holds *many* children to represent a hierarchy).

**Q: What's the risk of Composite?**
> Over-generalising a hierarchy that will always be shallow and fixed — you pay for arbitrary nesting you never use. And transparent Composite's `UnsupportedOperationException` stubs violate LSP if you're not careful.

---

## 🧾 TL;DR

> *"Composite lets a client treat a single leaf and a whole subtree through one interface —
> the leaf is the base case, the container recurses into its children, and the caller never
> type-checks. It's the pattern behind every file system, UI tree, and org chart."*

**Related:** part-whole is a HAS-A composition relationship — see `../Foundations/04-relationships.md`. The uniform-interface idea rests on Liskov + Interface Segregation — see `../Foundations/02-solid-principles.md`.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 2026 | **Created** during the LLD restructure to fill the long-flagged Composite gap (previously the pattern existed only inside `../Interview/ebay-mts1-lld.md`). Canonical example: file system (`File` leaf + `Directory` composite). Covers transparent-vs-safe child management, real-world usages, and relations to Iterator/Visitor/Decorator. |
