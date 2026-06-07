# Resources — Curated Study List

> **Research date:** June 2026. Cross-referenced across 5 searches: YouTube recommendations, GitHub star counts, Reddit/Blind community picks, ByteByteGo vs HelloInterview comparison, and real interview experience posts from LeetCode Discuss.
>
> **Selection criteria:** Only resources that appeared in MULTIPLE sources or had 10K+ GitHub stars / strong community consensus. Not random blogs.

---

## 🎯 The Short Answer — Start Here

If you only have time for 3 things before an interview:

| Priority | Resource | What it gives you | Cost |
|---|---|---|---|
| 1 | **Arpit Bhayani (YouTube — Asli Engineering)** | Concept-level depth — caching, DB internals, consistent hashing, distributed locking. Most technically rigorous free content available. | FREE |
| 2 | **ashishps1/awesome-low-level-design** (GitHub) | 20+ LLD problems from easy to hard, OOP, design patterns, concurrency — interview-ready format. | FREE |
| 3 | **hellointerview.com — "System Design in a Hurry"** | Structured, interview-aligned system design guide built by FAANG hiring managers. Free section covers all core concepts. | FREE (partial) |

---

## 🔹 LLD Resources (Ranked)

### 1. ashishps1/awesome-low-level-design ⭐ PRIMARY
- **Link:** https://github.com/ashishps1/awesome-low-level-design
- **Stars:** 36.9K+ (community-validated)
- **What it covers:**
  - OOP fundamentals (encapsulation, inheritance, polymorphism, abstraction)
  - SOLID principles (with examples)
  - All major design patterns (Singleton, Factory, Strategy, Observer, Command, Builder, etc.)
  - 20+ interview problems (Easy → Hard): Parking Lot, Elevator, BookMyShow, Splitwise, Ride Sharing, Food Delivery
  - Concurrency handling in LLD
- **Why it's #1:** Appears in almost every "best LLD resources" list. Free alternative to paid courses. Java implementations included.
- **How to use:** Read the SOLID section first → pick 3-4 problems matching the frequency table → code them yourself

### 2. Concept and Coding with Shreyansh (YouTube)
- **Search:** "Concept and Coding Shreyansh LLD"
- **What it covers:** LLD walkthroughs with live coding, design pattern explanations, SOLID principles
- **Best for:** Seeing how an interviewer thinks through a problem in real time

### 3. sudoCode (YouTube)
- **Search:** "sudoCode LLD system design"
- **What it covers:** System design + LLD, clean explanations of design patterns with real use cases
- **Best for:** Quick concept reinforcement, 10-20 min videos

### 4. Tech Dummies Narendra L (YouTube)
- **Search:** "Tech Dummies Narendra L LLD"
- **Best for:** Both HLD + LLD balance if you want one channel for both

---

## 🔹 System Design Concepts Resources (Ranked)

### 1. Arpit Bhayani — Asli Engineering (YouTube) ⭐ PRIMARY
- **Link:** https://www.youtube.com/c/ArpitBhayani
- **Website:** https://arpitbhayani.me
- **What it covers:**
  - Database internals (how MySQL/Postgres actually work)
  - Caching (LRU, LFU, eviction, Redis internals)
  - Consistent hashing (with implementation)
  - Distributed locking, rate limiting
  - System Design Masterclass (paid, but free videos cover 80% of interview content)
- **Why it's #1:** 160K+ subscribers. Technically rigorous — explains WHY behind every concept, not just what. Engineers on Blind consistently recommend this for senior-level prep.
- **Best videos to start:** Search "Arpit Bhayani consistent hashing", "Arpit Bhayani rate limiting", "Arpit Bhayani distributed locking"

### 2. hellointerview.com — "System Design in a Hurry" ⭐ PRIMARY (FREE)
- **Link:** https://www.hellointerview.com/learn/system-design/in-a-hurry/introduction
- **What it covers:** Complete structured guide — caching, rate limiting, databases, messaging, sharding — interview-aligned
- **Why it's #2:** Built by FAANG hiring managers. Written specifically for interview performance, not academic depth. Free section is comprehensive.
- **Best for:** Rate Limiter, Caching strategy walkthroughs with trade-offs

### 3. System Design Primer (GitHub)
- **Link:** https://github.com/donnemartin/system-design-primer
- **Stars:** 270K+ (most starred system design repo on GitHub)
- **What it covers:** CAP theorem, scalability, latency, caching, load balancing, databases, sharding, consistent hashing, communication patterns
- **Why it's here:** The OG reference. Not interview-specific but covers fundamentals thoroughly.
- **How to use:** Reference only — don't read end-to-end. Look up specific concepts when needed.

### 4. ashishps1/awesome-system-design-resources (GitHub)
- **Link:** https://github.com/ashishps1/awesome-system-design-resources
- **Stars:** 36.9K+
- **What it covers:** Curated list of free articles, videos, and resources for every system design concept
- **Best for:** Finding the best article/video on any specific topic (consistent hashing → best article, Bloom filter → best video, etc.)

### 5. ByteByteGo (YouTube — FREE videos)
- **Link:** https://www.youtube.com/@ByteByteGo
- **What it covers:** Visual explainers for system design concepts — API gateway, CDN, caching, rate limiting, database indexing
- **Why use the free YouTube:** Their paid course is strong but the YouTube channel alone covers most interview topics with excellent visuals
- **Best for:** Quick 5-10 min visual explanations of any concept before going deeper with Arpit

---

## 🔹 Priority Study Order (Based on Interview Frequency)

From research — topics ranked by how often they appear in actual SDE2/SDE3 interviews:

### LLD Problems (do in this order)
| # | Problem | Pattern to learn | Difficulty |
|---|---|---|---|
| 1 | **Parking Lot** | Factory, Strategy | Easy — warm up |
| 2 | **BookMyShow / Movie Ticket** | Observer, State machine | Medium |
| 3 | **Splitwise** | Strategy, Observer | Medium |
| 4 | **Elevator System** | Command, Strategy | Medium |
| 5 | **LRU Cache** | Doubly LL + HashMap | Medium — technical |
| 6 | **Rate Limiter** | Strategy (token bucket vs sliding window) | Hard — technical |
| 7 | **Meeting Room Reservation** | Concurrency + State | Hard |
| 8 | **Ride Sharing (Uber-lite)** | Observer, Strategy, Factory | Hard |

**Concurrency is mandatory for ALL of the above.** After solving each, ask yourself: "How do I make this thread-safe?"

### System Design Concepts (do in this order)
| # | Concept | Resource to use |
|---|---|---|
| 1 | **Optimistic + Pessimistic Locking** | Arpit Bhayani |
| 2 | **Rate Limiting** (token bucket, sliding window) | hellointerview.com + Arpit |
| 3 | **Caching** (LRU, TTL, eviction, Redis) | hellointerview.com + ByteByteGo |
| 4 | **Idempotency** | Arpit Bhayani |
| 5 | **Consistent Hashing** | Arpit Bhayani |
| 6 | **Distributed Locking** (Redis SETNX, Redlock) | hellointerview.com |
| 7 | **CDC + Outbox Pattern** | Arpit Bhayani |
| 8 | **Kafka / Stream Processing basics** | ByteByteGo YouTube |
| 9 | **Bloom Filter** | ashishps1 resources |
| 10 | **Backpressure** | Arpit Bhayani |

---

## 🔹 What NOT to Use

| Resource | Why skip |
|---|---|
| Random Medium blogs | Quality varies wildly, often wrong trade-offs |
| GeeksforGeeks LLD pages | Surface-level, no depth on patterns |
| Grokking System Design (Educative) | Good but expensive; free alternatives cover the same content |
| InterviewBit LLD pages | Too generic, not enough depth for senior interviews |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. Research from 5 web searches cross-referenced. |
