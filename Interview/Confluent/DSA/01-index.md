# Confluent DSA Battle Pack — Master Index

> **How to use this file:** This is your single-page dashboard. Check off problems as you practice. Every problem links to its solution + the concept prereq it depends on.

---

## 📐 Format Standard

Every problem in this battle pack follows `_format.md`:
- 🎤 How It's Asked → Discussion → Brute Force (with complexity derivation) → Key Insight → **Steps in plain English** → Code with step-matching comments → Complexity derivation → Variants → Cross-Questions

---

## 📚 Files in This Pack

| File | Contents |
|---|---|
| [`_format.md`](./_format.md) | Format contract — read before writing any notes |
| [`dsa-research.md`](./dsa-research.md) | Interview research — process, question list, sources |
| [`00-concepts-before-problems.md`](./00-concepts-before-problems.md) | 9 prerequisite concepts — read before touching problems |
| [`02-design-coding.md`](./02-design-coding.md) | 8 "build a component" problems |
| [`03-algo-problems.md`](./03-algo-problems.md) | 8 pure algorithm problems |
| [`04-concurrency-problems.md`](./04-concurrency-problems.md) | 4 "make it thread-safe" problems |

---

## ✅ Master Problem Checklist

### ⭐ Tier 1 — High Frequency (3+ reports, do these first)

| # | Problem | File | LC # | Concept Prereq | Practiced? |
|---|---|---|---|---|---|
| 1 | KV Store with TTL + Windowed Average | [02 #1](./02-design-coding.md#1-kv-store-with-ttl--windowed-average) | ~981 | 00 §1 HashMap, §2 DLL+HashMap | [ ] |
| 2 | Thread-Safe LRU Cache with TTL | [02 #2](./02-design-coding.md#2-thread-safe-lru-cache-with-ttl) | 146 | 00 §2 DLL+HashMap, §9 Concurrency | [ ] |
| 3 | Valid Sudoku | [03 #1](./03-algo-problems.md#1-valid-sudoku) | 36 | 00 §1 HashMap | [ ] |
| 4 | Sudoku Solver | [03 #2](./03-algo-problems.md#2-sudoku-solver) | 37 | 00 §3 Backtracking | [ ] |
| 5 | Wildcard Matching | [03 #3](./03-algo-problems.md#3-wildcard-matching) | 44 | 00 §8 2D String DP | [ ] |
| 6 | Regular Expression Matching | [03 #4](./03-algo-problems.md#4-regular-expression-matching) | 10 | 00 §8 2D String DP | [ ] |
| 7 | Function Signature Matcher | [02 #6](./02-design-coding.md#6-function-signature-matcher-with-variadic-args) | Custom | 00 §1 HashMap | [ ] |
| 8 | Thread-Safe KV Store | [04 #1](./04-concurrency-problems.md#1-thread-safe-kv-store-with-ttl) | Custom | 00 §9 Concurrency | [ ] |
| 9 | Task Scheduler (Producer-Consumer) | [04 #3](./04-concurrency-problems.md#3-task-scheduler--producer-consumer) | Custom | 00 §9 Concurrency | [ ] |

### Tier 2 — Medium Frequency (2 reports)

| # | Problem | File | LC # | Concept Prereq | Practiced? |
|---|---|---|---|---|---|
| 10 | Inverted Index / Document Search | [02 #4](./02-design-coding.md#4-inverted-index--document-search) | Custom | 00 §1 HashMap | [ ] |
| 11 | Tail Command (last N lines) | [02 #5](./02-design-coding.md#5-tail-command--last-n-lines-of-huge-file) | Custom | 00 §7 File I/O | [ ] |
| 12 | Design HashMap | [02 #3](./02-design-coding.md#3-design-hashmap) | 706 | 00 §1 HashMap | [ ] |
| 13 | Restaurant Menu Optimizer | [02 #8](./02-design-coding.md#8-restaurant-menu-optimizer) | Custom | Bitmask enumeration | [ ] |
| 14 | Readers-Writers | [04 #4](./04-concurrency-problems.md#4-readers-writers-with-starvation-prevention) | Classic | 00 §9 Concurrency | [ ] |

### Tier 3 — Single Report (but still worth practicing)

| # | Problem | File | LC # | Concept Prereq | Practiced? |
|---|---|---|---|---|---|
| 15 | Number of Atoms | [03 #5](./03-algo-problems.md#5-number-of-atoms) | 726 | 00 §6 Stack Parsing | [ ] |
| 16 | Ways to Make a Fair Array | [03 #6](./03-algo-problems.md#6-ways-to-make-a-fair-array) | 1664 | 00 §4 Prefix Sum | [ ] |
| 17 | Merge Strings Alternately | [03 #7](./03-algo-problems.md#7-merge-strings-alternately) | 1768 | 00 §5 Two Pointers | [ ] |
| 18 | Identify the Largest Outlier | [03 #8](./03-algo-problems.md#8-identify-the-largest-outlier) | 3371 | 00 §1 HashMap | [ ] |
| 19 | Notebook Editor | [02 #7](./02-design-coding.md#7-notebook-editor) | Custom | Two-stack pattern | [ ] |
| 20 | Thread-Safe LRU (concurrency focus) | [04 #2](./04-concurrency-problems.md#2-thread-safe-lru-cache-with-ttl) | 146 | 00 §9 Concurrency | [ ] |

---

## 🗺️ Suggested Practice Order

**Day 1 — Foundations (warm up + high frequency):**
1. Merge Strings Alternately (#17) — easy warm-up, 5 min
2. Valid Sudoku (#3) — medium, builds to solver
3. Sudoku Solver (#4) — hard, backtracking
4. Design HashMap (#12) — medium, foundational

**Day 2 — Design-Coding (Confluent's signature style):**
5. KV Store with TTL (#1) — Tier 1, DLL + HashMap
6. Thread-Safe KV Store (#8) — make #1 concurrent
7. Inverted Index (#10) — clean design problem
8. Tail Command (#11) — unique, File I/O

**Day 3 — Hard Algorithms:**
9. Wildcard Matching (#5) — Tier 1, 2D DP
10. Regex Matching (#6) — variant of #5
11. Number of Atoms (#15) — stack parsing
12. Function Signature Matcher (#7) — custom Confluent favorite

**Day 4 — Concurrency + Remaining:**
13. Task Scheduler (#9) — producer-consumer
14. Readers-Writers (#14) — ReadWriteLock
15. LRU Cache with TTL (#2 + #20) — full stack: single-threaded → concurrent
16. Ways to Make Fair Array (#16) — prefix sum
17. Largest Outlier (#18) — math + hashmap
18. Restaurant Menu (#13) — bitmask enumeration
19. Notebook Editor (#19) — two-stack

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 2026 | Index created. 20 problems across 3 files. 4-day practice order. |
