# LLD — Subdomain Standards

> **Read the universal `AGENTS.md` at the repo root first.** This file only adds LLD-specific rules on top of those.

---

## 🎯 What This Folder Contains

Low-Level Design (LLD) knowledge for senior backend interviews. Two types of content:

| Type | Location | Format |
|---|---|---|
| **Design Pattern notes** | `DesignPatterns/` | Markdown only — conceptual, mental model, when to use, code snippet |
| **Problem walkthroughs** | `Problems/<problem-name>/` | Markdown (approach) + `.java` files (runnable implementation) |

This is **general knowledge** — not company-specific. Company-specific battle files live in `Interview/<Company>/`.

---

## 📁 Folder Structure

```
LLD/
├── AGENTS.md                        ← this file
├── TODO.md                          ← topic list + study order
├── notes-standards.md               ← format rules for both note types
├── interview-execution-guide.md     ← 60-minute interview execution playbook
│
├── DesignPatterns/
│   ├── 01-factory-strategy.md
│   ├── 02-observer.md
│   ├── 03-command.md
│   ├── 04-builder.md
│   └── 05-state.md
│
└── Problems/
    ├── parking-lot/
    │   ├── parking-lot.md           ← approach, patterns, concurrency, interview tips
    │   ├── ParkingLot.java
    │   ├── ParkingSpot.java
    │   └── ...
    ├── bookmyshow/
    └── ...
```

---

## 🎨 LLD-Specific Emoji Additions

In addition to the universal palette, these are approved for LLD notes:

| Emoji | Use for |
|---|---|
| `🏗️` | Class diagram / structure section |
| `🔌` | Interface definition section |
| `🖊️` | Coding skeleton / interview order section |
| `🔁` | Concurrency / thread-safety section |
| `📐` | Design decisions / trade-offs section |

---

## 📏 LLD-Specific Code Rules

On top of universal AGENTS.md code rules:

1. **Interfaces before classes** — every pattern note shows the interface first, implementation second
2. **`@Override` always** — never omit it on implemented/overridden methods
3. **Access modifiers explicit** — never package-private by accident; always `public`, `private`, or `protected`
4. **Thread-safety annotations** — if a class is thread-safe, say so in a comment: `// thread-safe: uses synchronized`
5. **No `System.out.println` in production code** — use a `Logger` or omit output entirely
6. **Java 17+** — prefer records for DTOs, sealed classes for closed hierarchies where appropriate

---

## ✅ LLD Quality Checklist

Run before finalizing any LLD note, in addition to the universal checklist:

**For Design Pattern notes:**
- [ ] Mental model uses a concrete everyday analogy
- [ ] Shows the INTERFACE definition before any implementation
- [ ] Includes a "when NOT to use this pattern" row in the trade-offs
- [ ] Java code compiles mentally (no syntax gaps)
- [ ] At least 2 real-world company examples

**For Problem notes (markdown part):**
- [ ] Requirements section lists functional + non-functional requirements
- [ ] Design decisions explained (why this class, why this pattern)
- [ ] Concurrency section present — which fields are shared, which lock strategy
- [ ] "What would you do differently?" answer present (from AGENTS.md)
- [ ] Cross-references the patterns used (link to DesignPatterns/ file)

**For Problem notes (Java files):**
- [ ] All files compile if copied into a blank Java project
- [ ] Interfaces defined before implementations
- [ ] `@Override` on all overridden methods
- [ ] No hardcoded magic numbers — use constants or enums

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | LLD folder created. Standards defined. Study order: interleaved (pattern → problem). |
