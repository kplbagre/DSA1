# Performance and Scale

> **Core Question:** How do you serve 1000x more traffic without proportionally increasing servers/resources?

**Concepts:** 4  
**Time:** ~5 hours  
**Difficulty:** ⭐⭐⭐

---

## 📖 Concepts

1. **02-rate-limiting.md** + 02-rate-limiting_advanced.md (1.5h)
   - Control request flow (token bucket, sliding window, leaky bucket)
   - Protect backend from overload
   - Interview: "Design a rate limiter for a public API"

2. **03-caching.md** + 03-caching_advanced.md (1.5h)
   - Cache strategies (cache-aside, write-through)
   - Avoid cache stampede
   - Interview: "Your cache hit rate is 50%. What's going wrong?"

3. **05-consistent-hashing.md** (1h)
   - Distribute data fairly across servers
   - When one server fails, only 1/N keys move
   - Interview: "Design a distributed cache with 100 nodes"

4. **09-sharded-counters.md** + 09-sharded-counters_advanced.md (1h)
   - Distribute counter increments across shards
   - Avoid "thundering herd" on single counter
   - Interview: "Design a counter for 1M requests/sec"

---

## 🎯 Study Order

**Sequential:** 02 → 03 → 05 → 09

**Why?**
- Rate limiting is traffic control (02)
- Caching reduces load per server (03)
- Consistent hashing distributes load (05)
- Sharded counters apply distribution principle (09)

---

## 💡 Key Mental Models

| Concept | Mental Model |
|---------|--------------|
| Rate Limiting | Nightclub bouncer with a quota |
| Caching | Personal assistant with a notepad |
| Consistent Hashing | Circular dartboard; keys + servers both placed on ring |
| Sharded Counters | Counting people with multiple counters instead of one |

---

## 🔬 Common Interview Questions

- "Design a rate limiter for a public API with 10K RPS"
- "Your cache is being hit by concurrent misses on the same key. Why?"
- "We have 1000 servers. One fails. How many keys need to move?"
- "Design a system that counts impressions at 1M/sec"
- "How do you evict items from cache when memory is full?"

---

## 📚 Real Companies

- **Amazon**: Rate limiting per customer; distributed caching; consistent hashing for S3 sharding
- **Stripe**: Rate limiting per API key; Redis cache for fraud checks; consistent hashing for payment routing
- **Netflix**: Rate limiting per region; multi-layer caching; consistent hashing for content distribution
- **Uber**: Rate limiting per user; distributed counters for request IDs; consistent hashing for order sharding

---

## ✅ Checkpoint

After this folder, you should be able to:
- ✅ Design a rate limiter (multiple algorithms, trade-offs)
- ✅ Choose caching strategy and explain failure modes
- ✅ Explain consistent hashing and server failure recovery
- ✅ Design distributed counters for high throughput
