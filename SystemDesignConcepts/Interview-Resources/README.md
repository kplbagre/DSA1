# Interview Resources

> **Purpose:** Study guides, checklists, and decision trees to prepare for system design interviews.

---

## 📚 What's Inside

### Study Plans
**Choose your learning path:**

- **Study-Plans/** — How to progress through concepts efficiently
  - Complete-Path.md — Recommended order for all 37 concepts
  - By-Tier.md — Study by difficulty level
  - By-Problem-Type.md — Study by interview question type (payments, feeds, search, etc.)

### Quick References
**Use during practice interviews:**

- **Quick-References/** — Cheat sheets and decision trees
  - By-Concept.md — One-liner for every concept
  - Decision-Trees.md — "If problem asks X, use concept Y"
  - Common-Interview-Questions.md — 50+ real interview questions mapped to concepts
  - Follow-Up-Questions.md — How to probe deeper
  - Red-Flags.md — Mistakes that cost you the job

### Metadata
**Standards and resources:**

- **Metadata/AGENTS.md** — How concepts are organized
- **Metadata/notes-standards.md** — Quality standards every concept must meet
- **Metadata/resources.md** — External resources per concept
- **Metadata/GAP-CLOSURE-PLAN.md** — Missing concepts tracker
- **Metadata/TODO.md** — Work in progress

---

## 🎯 How to Use This Knowledge Base

### If you have 1 week
1. **Day 1:** Foundations/Concurrency-and-Consistency (01, 04, 06, 34)
2. **Day 2:** Foundations/Performance-and-Scale (02, 03, 05, 09)
3. **Day 3:** Core-Architecture/Resilience-and-Fault-Tolerance (20, 10, 23, 35)
4. **Day 4:** Core-Architecture/Service-Communication (17, 18, 19)
5. **Day 5:** Core-Architecture/Database-Core (06, 22) + Production-Grade/System-Design-Patterns (24, 31)
6. **Day 6-7:** Practice mock interviews using Quick-References

### If you have 2 weeks
1. **Week 1:** All Foundations (15 concepts, 15-20 hours)
2. **Week 2:** Core-Architecture (12 concepts, 22-27 hours)
3. **Day 15:** Production-Grade essentials (24, 25, 27) if time permits

### If you have 1 month
1. **Week 1:** Foundations (all 15 concepts, 15-20 hours)
2. **Week 2:** Core-Architecture (all 12 concepts, 22-27 hours)
3. **Week 3:** Production-Grade (all 11 concepts, 14-18 hours)
4. **Week 4:** Practice interviews, fill knowledge gaps, deep-dive into advanced variants

---

## 💡 Interview Preparation Checklist

**Before your interview:**

- [ ] Can you explain CAP theorem in 30 seconds?
- [ ] Can you draw a load balancer, cache, database on whiteboard?
- [ ] Can you design rate limiter from scratch?
- [ ] Can you answer "What if service X fails?" for your design?
- [ ] Can you justify every layer/component choice?
- [ ] Can you identify single points of failure?
- [ ] Can you explain trade-offs (consistency vs availability, latency vs cost)?

**During your interview:**

- [ ] Start with clarifying questions (scale, availability SLO, latency SLO)
- [ ] Draw the system (don't just talk)
- [ ] Identify bottlenecks before design
- [ ] Propose tradeoffs, don't claim perfection
- [ ] Admit when you don't know; propose how you'd figure it out

---

## 🗺️ Complete Concept Map

```
Foundations (17)
├── Concurrency-and-Consistency (4): 01, 04, 06, 41
├── Performance-and-Scale (4): 02, 03, 05, 09
└── Data-Fundamentals (7): 07, 08, 11, 12, 14, 15, 43

Core-Architecture (16)
├── Service-Communication (4): 17, 18, 19, 33
├── Resilience-and-Fault-Tolerance (8): 10, 20, 23, 29, 35, 36, 39, 44
├── Distributed-Systems (4): 34, 21, 37, 40
└── Database-Core (4): 06, 16, 22, 38

Production-Grade (12)
├── System-Design-Patterns (4): 24, 26, 31, 42
├── Observability (2): 25, 30
├── Auth-and-Security (3): 13, 27, 27-ref
└── Performance-Optimization (2): 28, 32

Advanced variants (bonus depth): 02-adv, 03-adv, 04-adv, 09-adv

Total: 44 core concepts + 4 advanced variants = 48 total files
Study time: 60-76 hours → SDE 3 Ready (~97% interview coverage)
```

---

## 📞 Support

Can't find something? Check:
1. Quick-References/By-Concept.md (concept one-liners)
2. Quick-References/Decision-Trees.md (problem → solution)
3. Metadata/resources.md (external references)
