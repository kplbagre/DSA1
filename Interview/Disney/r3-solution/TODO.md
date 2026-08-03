# Disney R3 — Ad Frequency Cap System (TODO)

## Problem Statement

Design a system to maintain a configurable frequency cap on ads shown to users:
- Do NOT show a given ad to a user more than **N times** (N is configurable per ad)
- Scale: **100M users**, **100K ads**

---

## TODOs (notes to write)

- [ ] Clarifying questions and scope decisions
- [ ] Scale math — storage and write throughput estimates
- [ ] Core data model — what are we actually storing?
- [ ] Frequency check + increment — the hot path (read-modify-write atomicity)
- [ ] Storage choice — why Redis over SQL/Cassandra for the counter store
- [ ] Redis data structure choice — why HASH or bitmap vs plain key per (user, ad)
- [ ] TTL strategy — per-impression window (last 24h? lifetime? per-campaign?)
- [ ] Serving path — where does the cap check live (ad server, DSP, CDN edge)?
- [ ] Configuration store — how is N stored and updated per ad
- [ ] Thundering herd / hot key problem — 100M users × 100K ads fanout
- [ ] Durability tradeoff — Redis is in-memory; what happens on restart/eviction?
- [ ] Fallback behavior — what do we serve if the counter store is unavailable?
- [ ] Over-counting risk — distributed ad servers incrementing concurrently
- [ ] Under-counting risk — losing counts on Redis crash → showing ad too many times
- [ ] Exact vs approximate cap — Redis vs probabilistic approach (HyperLogLog / Bloom)
- [ ] Cross-device problem — same user on mobile + desktop = separate counters?
- [ ] Observability — how do we know the cap is working? What do we alert on?
