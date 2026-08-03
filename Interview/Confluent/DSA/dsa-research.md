# Confluent — DSA Interview Research (2024–2026)

> **Data quality:** ~20 distinct interview experience reports with specific question details + InterviewSolver (40 tagged problems) + Glassdoor (377 questions / 353 reviews). Enough to identify clear patterns.
>
> **IBM acquisition note:** IBM announced $11B acquisition of Confluent in Dec 2025, expected to close mid-2026. Interview process has NOT changed post-announcement. Some 2026 reports mention India hiring freeze.

---

## 📋 Interview Process / Structure

### Standard SSE (Senior Software Engineer) Loop

| Stage | Duration | Format | Focus |
|---|---|---|---|
| **Recruiter Screen** | 30–60 min | Phone/Video | Background, motivation, fit |
| **Technical Phone Screen** | 60 min | CoderPad or HackerRank | DSA + LLD hybrid, concurrency follow-ups |
| **Virtual Onsite (4–5 rounds)** | 60 min each | CoderPad | Coding, System Design, LLD, Concurrency |
| **Engineering Values Round** | 60 min | Video | Behavioral — Confluent values-aligned |
| **Team Matching** | 30–60 min | Video | Fit with specific team |

### SSE Onsite Breakdown (4–5 rounds)

| Round | Content |
|---|---|
| **Coding Round 1** | DSA — 2-part: easy-medium then medium-hard escalation |
| **Coding Round 2** | Concurrency / Multithreading — dedicated round |
| **System Design / HLD** | URL shortener, Feedly, Kafka-as-a-Service, bit.ly |
| **Low-Level Design** | SOLID, design patterns, class design with thread safety |
| **Engineering Values / HM** | Handling ambiguity, mentoring, pushing back, operational impact |

### Key Details

- **Platform:** CoderPad (onsite), HackerRank (OA/phone screen)
- **OA Format** (when used): 3 questions in 75 minutes on HackerRank, or 2 Easy-Medium questions
- **Timeline:** 22–29 days average, up to 2 months
- **NDA:** Candidates reportedly sign an NDA
- **Difficulty rating:** 2.9–3.2 / 5 on Glassdoor

---

## ⭐ Specific DSA Questions — By Frequency

### Tier 1: HIGH FREQUENCY (3+ independent reports)

| # | Problem | LC # | Difficulty | Category | Reports |
|---|---|---|---|---|---|
| 1 | **Key-Value Store with Expiration / Windowed Average** | ~LC 981 (Time Based KV Store) | Medium | HashMap, DLL, Design | 6+ |
| 2 | **Validate Sudoku / Solve Sudoku** | LC 36 + LC 37 | Medium + Hard | Backtracking, Recursion | 5+ |
| 3 | **Wildcard / Regex Matching** | LC 44 / LC 10 | Hard | String, DP, Backtracking | 4+ |
| 4 | **Function Signature Matching with Variadic Args** | Custom | Medium-Hard | HashMap, Type System | 4+ |
| 5 | **Thread-Safe LRU Cache with TTL** | LC 146 variant | Medium | Design, Concurrency, DLL + HashMap | 4+ |
| 6 | **Task Scheduler (Multithreading)** | Custom | Medium-Hard | Concurrency, PQ, Producer-Consumer | 3+ |

### Tier 2: MEDIUM FREQUENCY (2 reports)

| # | Problem | LC # | Difficulty | Category |
|---|---|---|---|---|
| 7 | **Word/Phrase Search in Documents (Inverted Index)** | Custom | Medium | HashMap, String, Search |
| 8 | **Tail command — read last N lines of huge file** | Custom | Medium | File I/O, Memory Optimization |
| 9 | **HashMap Design** | LC 706 variant | Medium | Design, Hash |
| 10 | **Restaurant Menu / Best Price with Value Meals** | Custom | Medium | Combinatorics, Greedy/DP |

### Tier 3: SINGLE REPORT (mentioned once but specific)

| # | Problem | LC # | Difficulty | Category |
|---|---|---|---|---|
| 11 | Number of Atoms | LC 726 | Hard | Stack, Recursion, Parsing |
| 12 | Ways to Make a Fair Array | LC 1664 | Medium | Prefix Sum, Array |
| 13 | Merge Strings Alternately | LC 1768 | Easy | Two Pointers, String |
| 14 | Identify the Largest Outlier in an Array | LC 3371 | Medium | Array, HashSet, Math |
| 15 | Notebook Editor Class (AddText, etc.) | Custom | Medium | Design, DLL |
| 16 | Merge Sort (explain + code) | N/A | Easy | Sorting |
| 17 | Fibonacci Series | N/A | Easy | Recursion/DP |
| 18 | Windowed Map with Regex Pattern Match | Custom | Medium-Hard | String, Design |

---

## 📊 Topic Frequency Breakdown

| Rank | Topic | Frequency | Notes |
|---|---|---|---|
| 1 | **Concurrency / Multithreading** | ⭐ Very High | Distinctive Confluent focus. Dedicated round. Thread-safe designs, producer-consumer, locks, semaphores, CAS |
| 2 | **Hash Maps / KV Stores** | ⭐ Very High | Windowed KV store, LRU Cache, HashMap design — appears in nearly every loop |
| 3 | **Backtracking / Recursion** | High | Sudoku solver, regex matching, Number of Atoms |
| 4 | **String Matching / Parsing** | High | Wildcard matching, regex, function signature parsing |
| 5 | **Design-flavored coding** | High | LRU Cache, KV Store, Inverted Index — not pure algo, more "build a component" |
| 6 | **Arrays / Prefix Sums** | Medium | Fair Array, Merge Strings, Outlier detection |
| 7 | **File I/O / Memory** | Medium | Tail command, reading huge files with minimal memory |
| 8 | **Linked Lists (DLL)** | Medium | LRU Cache internals, notebook editor |
| 9 | **Trees / Graphs** | Low | Not prominently reported |
| 10 | **Dynamic Programming** | Low | Wildcard matching has DP solution, but pure DP rarely reported |

---

## 📊 Difficulty Distribution

**From InterviewSolver (40 Confluent-tagged problems):**

| Difficulty | Count | Percentage |
|---|---|---|
| Easy | 7 | 18% |
| Medium | 30 | 75% |
| Hard | 3 | 8% |

**From candidate reports:**

| Round | Difficulty |
|---|---|
| Phone screen | Easy → Medium (escalates) |
| Onsite Coding Round 1 | Medium (Part 1) → Medium-Hard (Part 2) |
| Onsite Coding Round 2 | Medium-Hard (concurrency adds complexity) |

> **Key insight:** Raw algorithmic difficulty is moderate (mostly LC Medium), but concurrency follow-ups and code quality expectations raise the effective difficulty significantly.

---

## 🧠 Confluent-Specific Interview Patterns

### Pattern 1 — "Old School" Practical Problems, Not Pure LeetCode

Confluent leans toward **"build a real component"** problems rather than tricky algorithmic puzzles:
- "Implement a key-value store with expiration"
- "Implement `tail -n` for a huge file"
- "Build an inverted index for document search"
- "Make your LRU Cache thread-safe"

These test design thinking + coding simultaneously, not just algorithm knowledge.

### Pattern 2 — 2-Part Escalation in Coding Rounds

Almost every DSA round is structured as:
1. **Part 1:** Simpler version (Easy-Medium) — get working code
2. **Part 2:** Add complexity — make it thread-safe, add TTL, handle edge cases, optimize for huge files

### Pattern 3 — Concurrency Is THE Differentiator

Unlike most companies where concurrency is a follow-up, Confluent has a **dedicated concurrency round**. Expect:
- Make any data structure thread-safe (LRU Cache, KV Store, task scheduler)
- Producer-consumer patterns
- Readers-writers problem
- Fine-grained vs coarse-grained locking discussion
- CAS, `ReentrantLock`, `ReentrantReadWriteLock`, `Semaphore`

### Pattern 4 — Code Quality Is Non-Negotiable

> One candidate got a no-hire despite a correct solution because they had duplicate code.

Clean code, no duplication, proper API design, good naming — all matter more here than at most companies.

### Pattern 5 — API Design Under Scrutiny

In design rounds, interviewers care deeply about:
- REST verbs (GET/POST/PUT/DELETE — correct usage)
- HTTP response codes
- Request/response headers
- Mistakes are heavily penalized

---

## ✅ Must-Do LeetCode Problems

| Priority | LC # | Problem | Why |
|---|---|---|---|
| ⭐ | LC 146 | LRU Cache | Tier 1 — then make thread-safe + add TTL |
| ⭐ | LC 981 | Time Based Key-Value Store | Tier 1 — windowed KV store pattern |
| ⭐ | LC 36 | Valid Sudoku | Tier 1 — backtracking warmup |
| ⭐ | LC 37 | Sudoku Solver | Tier 1 — full backtracking |
| ⭐ | LC 44 | Wildcard Matching | Tier 1 — "everyone I know was asked this" |
| ⭐ | LC 10 | Regular Expression Matching | Tier 1 — variant of LC 44 |
| High | LC 706 | Design HashMap | Tier 2 — hash design fundamentals |
| High | LC 726 | Number of Atoms | Tier 3 — parsing + recursion |
| Medium | LC 1664 | Ways to Make a Fair Array | Tier 3 — prefix sum |
| Medium | LC 1768 | Merge Strings Alternately | Tier 3 — easy warmup |
| Medium | LC 3371 | Identify the Largest Outlier | Tier 3 — math + hash |

---

## ✅ Must-Practice Custom Problems

These are NOT on LeetCode but repeatedly reported:

| Problem | What It Tests |
|---|---|
| KV Store with expiration + `get_average()` over window | HashMap + DLL + time-based eviction |
| Function signature matcher with variadic args | HashMap + type matching + edge cases |
| Inverted index / document search | HashMap of word → list of (docId, positions) |
| `tail -n` for a huge file (can't fit in memory) | File I/O, `RandomAccessFile`, seek from end |
| Task scheduler with producer-consumer threading | `BlockingQueue`, `ExecutorService`, `Semaphore` |
| Restaurant menu optimizer with value meals | Combinatorics/greedy — subset optimization |

---

## ✅ Must-Know Concurrency (Dedicated Round)

| Concept | Specific Prep |
|---|---|
| Thread-safe LRU Cache | Fine-grained locking (per-node) vs coarse-grained (global lock) |
| Producer-Consumer | `BlockingQueue`, `wait()/notify()`, `Condition` variables |
| Readers-Writers | `ReentrantReadWriteLock` — when reads >> writes |
| CAS operations | `AtomicReference.compareAndSet()` for lock-free state transitions |
| `ReentrantLock` | `tryLock(timeout)`, fair mode, `lockInterruptibly()` |
| `Semaphore` | Resource pool limiting (connection pools, charging stations) |
| Thread pools | `ExecutorService`, `newFixedThreadPool`, `submit()`, `shutdown()` |

> **Cross-reference:** All of these are covered in `LLD/concurrency-deep-dive.md` (Patterns 1–8).

---

## 🗺️ Prep Strategy for Kapil

**What plays to your strengths:**
- LLD and concurrency are heavily tested — you've already written Job Scheduler, Pub-Sub, LRU Cache, Rate Limiter notes with full concurrency sections
- "Build a component" style matches your LLD note format exactly

**Gaps to close:**
1. **LC 44 / LC 10** (Wildcard/Regex matching) — Hard string DP, high frequency at Confluent, not in your DSA playbooks yet
2. **LC 37** (Sudoku Solver) — backtracking, not in your playbooks yet
3. **Custom KV Store with TTL** — variant of your LRU Cache note, but needs expiration + `get_average()` extension
4. **File I/O problems** (tail command) — not covered in DSA or LLD notes

---

## 📚 Sources

- LeetCode Discuss: 12+ Confluent experience posts (2024–2026)
- Glassdoor: 353 reviews, 377 interview questions
- TeamBlind: 7+ Confluent-specific threads
- 1Point3Acres: 4 Confluent interview threads (2025–2026)
- InterviewSolver: 40 Confluent-tagged LC problems
- GeeksforGeeks, Medium, CodingKaro, EngineBogie, Exponent, InterviewQuery, TechPrep, Scoutify: additional data points

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 2026 | Research compiled. 35+ sources across LeetCode, Glassdoor, Blind, 1P3A, GFG. 18 specific problems identified across 3 tiers. |
