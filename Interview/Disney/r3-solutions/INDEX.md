# Disney R3 — Onsite System Design Solutions

> **Read `solution-notes-standards.md` first.** It defines the 15-section format and Disney-specific quality bar for every file in this folder.

---

## Questions

| File | Question | Type | Status |
|---|---|---|---|
| `D1-leaderboard.md` | Design a Global Game Leaderboard | Type A — System Design | ✅ Complete |
| `D2-ad-budget-pacing.md` | Design an Ad Budget Pacing & Impression Counting System | Type A — System Design | ✅ Complete |
| `D3-rate-limiter.md` | Design an API Rate Limiter | Type A — System Design (+ Java LLD follow-up) | ✅ Complete |

---

## Disney R3 Context

- **Round:** Onsite Round 3 (R1 = Technical Screen, R2 = Coding/LLD, R3 = System Design)
- **Format:** 60-minute system design; 1–2 deep dives (lower depth bar than FAANG)
- **Most confirmed for Ad Platforms org:** Ad Budget Pacing (D2) and Rate Limiter (D3)
- **Most confirmed general:** Global Game Leaderboard (D1)
- **Key differentiator:** Guest-centric framing + Disney product context (ESPN live sports, Disney+ streaming, ad exchange)

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 21, 2026 | **Folder created.** Standards file + D1 Leaderboard added. |
| Jul 21, 2026 | **D2 and D3 added.** D2 (Ad Budget Pacing): two-layer AP/CP architecture, CPM unit bug fixed, smooth pacing formula, LongAdder + sharded counters, Kafka→Flink→Cassandra billing. D3 (Rate Limiter): token bucket + Redis Cluster + Lua INCR, four source-file bugs fixed (starvation, DECR free-pass, Retry-After=0, CROSSSLOT), LongAdder 300x Redis load reduction, Disney DSP tier design. |
