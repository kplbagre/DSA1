# Resources — LLD Study List

> **Research date:** June 2026. Selected from community-validated sources (GitHub stars, Reddit/Blind recommendations, direct interview feedback).
>
> **Rule (from AGENTS.md):** Before writing the `📚 Further Reading` section of any LLD note, check this file for already-vetted resources. Do NOT invent recommendations.

---

## 🎯 The Short Answer — Start Here

If you only have time for 2 things before an LLD interview:

| Priority | Resource | What it gives you | Cost |
|---|---|---|---|
| 1 | **ashishps1/awesome-low-level-design** (GitHub) | 20+ problems, Java implementations, SOLID examples — the most referenced free LLD resource | FREE |
| 2 | **hellointerview.com — "Learn Low Level Design"** | Structured delivery framework, design principles, OOP concepts, patterns — interview-aligned format | FREE (partial) |

---

## 🔹 Primary Resources (Use for every problem)

### 1. ashishps1/awesome-low-level-design ⭐ PRIMARY
- **Link:** https://github.com/ashishps1/awesome-low-level-design
- **Stars:** 36.9K+ (community-validated)
- **What it covers:**
  - SOLID principles with working Java examples
  - All major design patterns (Factory, Strategy, Observer, Builder, Command, State, Decorator, etc.)
  - 20+ worked problems: Parking Lot, BookMyShow, Splitwise, Elevator, LRU Cache, Rate Limiter, Vending Machine, Logger, Meeting Room, Ride Sharing, Stock Exchange, Library Management
  - Concurrency handling in each problem
- **Why it's #1:** Appears in nearly every "best LLD prep" thread. Java implementations are clean, pattern usage is explicit, and problems match exactly what gets asked.
- **How to use:** For each problem, read ashishps1's solution AFTER writing your own first attempt. Compare class hierarchy, interface choices, and concurrency approach.

---

### 2. hellointerview.com — "Learn Low Level Design" ⭐ PRIMARY
- **Link:** https://www.hellointerview.com/learn/low-level-design/in-a-hurry/introduction
- **What it covers:**
  - **Delivery Framework** — their step-by-step approach for the interview (requirements → entities → class design → code → concurrency)
  - **Design Principles** — SOLID, DRY, YAGNI with LLD application context
  - **OOP Concepts** — encapsulation, inheritance, polymorphism, abstraction — how they show up in interviews specifically
  - **Design Patterns** — Gang of Four patterns with interview-relevant framing
- **Why use it:** Built by FAANG hiring managers. Their delivery framework aligns with what interviewers actually evaluate. Especially strong on how to articulate *why* you made a design decision.
- **Best for:** The delivery framework section — read once, internalize the step sequence.

---

## 🔹 Supplementary Resources (Use for depth on specific topics)

### 3. Concept and Coding with Shreyansh (YouTube)
- **Search:** "Concept and Coding Shreyansh LLD"
- **What it covers:** Live LLD walkthroughs — Shreyansh designs and codes from scratch, narrating as he goes. See how an expert thinks in real time.
- **Best for:** Watching the design phase — how to go from requirements to class diagram verbally. Helps calibrate the "talk while you think" skill.
- **When to use:** Watch ONE walkthrough of a problem you've already attempted. See what you missed or what you over-engineered.

### 4. sudoCode (YouTube)
- **Search:** "sudoCode LLD" or "sudoCode design patterns"
- **What it covers:** Design pattern explanations (10-20 min each), LLD problem walkthroughs
- **Best for:** Quick pattern reinforcement. If you're shaky on Command or Builder, a 15-min sudoCode video before the interview is useful.

### 5. Tech Dummies Narendra L (YouTube)
- **Search:** "Tech Dummies Narendra LLD"
- **Best for:** Both HLD + LLD balance — useful if you want one channel covering both domains.

---

## 🔹 Resources by Design Pattern

| Pattern | Best resource | Our note |
|---|---|---|
| Factory + Strategy | ashishps1 (Parking Lot, Rate Limiter) | `DesignPatterns/01-factory-strategy.md` ✅ |
| Observer | ashishps1 (BookMyShow, Logger) + Shreyansh | `DesignPatterns/02-observer.md` ✅ |
| Command | sudoCode "Command Pattern" video + ashishps1 (Elevator) | `DesignPatterns/03-command.md` ✅ |
| Builder | ashishps1 examples + hellointerview.com patterns section | `DesignPatterns/04-builder.md` ✅ |
| State | ashishps1 (Vending Machine) + sudoCode | `DesignPatterns/05-state.md` ✅ |
| Singleton | hellointerview.com + Effective Java Item 3 | `DesignPatterns/06-singleton.md` ✅ |
| Decorator | hellointerview.com + ashishps1 | (Phase 2 — Logger system) |

---

## 🔹 Resources by Problem

| Problem | Primary reference | Notes |
|---|---|---|
| Parking Lot | ashishps1 | Clean Factory + Strategy implementation; concurrency section included |
| BookMyShow / Movie Ticket | ashishps1 | Observer + State; seat locking concurrency is the key teaching |
| LRU Cache | ashishps1 | Technical LLD — doubly linked list + HashMap; no design pattern, pure data structure skill |
| Rate Limiter (LLD) | ashishps1 | Strategy pattern IS the core; TokenBucket vs SlidingWindow implementations |
| Splitwise | ashishps1 | Strategy for splitting; group debt simplification algorithm |
| Elevator System | ashishps1 + Shreyansh | Command + Strategy for scheduling algorithm |
| Vending Machine | ashishps1 | State pattern canonical example |
| Logger System | ashishps1 | Factory + Observer; chain of responsibility variant |
| Meeting Room Reservation | ashishps1 | Concurrency-heavy; booking conflict detection is the interview crux |
| Ride Sharing (Cab Booking) | ashishps1 + Shreyansh | Observer + Strategy; most complex LLD problem |
| Stock Exchange / Order Book | ashishps1 | Strategy for matching algorithm; Observer for trade events |

---

## 🔹 What NOT to Use

| Resource | Why skip |
|---|---|
| Random Medium blogs on "LLD interview" | Inconsistent quality; often pseudo-code without working Java |
| GeeksforGeeks LLD pages | Surface-level; no pattern naming, no concurrency |
| Grokking OOD (Educative) | Paid course; ashishps1 covers the same problems for free with better Java |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. Curated from 5 searches: GitHub stars, Reddit/Blind recs, Kapil's InMobi interview gaps, hellointerview.com review, ashishps1 audit. Two primaries locked in. |
| June 2026 | Pattern table updated — added Command (03), Builder (04), Singleton (06) as Done with note links. Decorator marked Phase 2. |
