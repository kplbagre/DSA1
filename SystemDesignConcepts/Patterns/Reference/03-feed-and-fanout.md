# Feed & Fanout — Quick Reference

> **Read this:** 30 min before an interview involving personalized feeds or activity streams.
> **Deep study:** `DeepDive/03-feed-and-fanout.md`

---

## 🎯 What kind of problem is this?

Use this pattern when: **building a personalized feed assembled from content posted by accounts a user follows** — each user's view is unique, content comes from many sources, and you need both fast reads and manageable write costs.

Trigger words: "design Twitter", "design Instagram feed", "timeline", "activity stream", "news feed", "fanout", "celebrity problem", "follower feed".

---

## 🧭 Decision Sequence

```
START: Need to build personalized feed

Step 1 → Feed storage
         Use Redis Sorted Set (ZSET) per user — post_ids as values, timestamps as scores.
         Never store full post content in the feed cache.

Step 2 → Fanout-on-write for regular users
         On post: async fanout worker writes post_id to each follower's Redis ZSET.
         Set celebrity threshold T (start at 10K followers). Tune based on write amplification.

Step 3 → Fanout-on-read for celebrities (followers > T)
         No fanout at post time. Post stored in Post DB only.
         At feed read time: fetch celebrity followings, query their recent posts live.

Step 4 → Merge at read time
         Feed = (Redis pre-built ZSET) + (celebrity post lookups).
         Sort by timestamp or engagement score. Paginate with cursor (not offset).

Step 5 → Add ranking (if needed)
         Apply engagement score at merge time.
         Cache ranked feed with 30–60s TTL to avoid re-ranking every request.
```

---

## ⚡ Strategies at a Glance

| Strategy | Use when | Don't use when |
|---|---|---|
| **Fanout-on-write (push)** | Regular users (< T followers), fast read required | Celebrities — 10M writes per post is catastrophic |
| **Fanout-on-read (pull)** | Celebrities (> T followers), inactive users | User follows 1000+ accounts — 10K DB queries per feed open |
| **Hybrid (production)** | Any real system at scale | Simple prototype |

**Key numbers to remember:**
- Celebrity threshold: typically 10K–100K followers (tunable)
- Fanout-on-write: 1 post = N Redis writes (N = follower count)
- Fanout-on-read: 1 feed open = N lookups (N = followings count)
- Justin Bieber problem: 100M followers × 1 write/sec = 100M Redis writes per post
- Redis ZSET read: O(log N) — feed is pre-sorted, reads are instant

---

## 🎨 Key Architecture Diagram

```
POST CREATION PATH:
                         ┌──────────────────────┐
    User posts ─────────▶│    Post Service       │──▶ Post DB (Postgres)
                         └──────────┬────────────┘
                                    │ async event → Kafka "post-created"
                         ┌──────────▼────────────┐
                         │   Fanout Service       │
                         │  (Kafka consumer)      │
                         └──────────┬────────────┘
                                    │
              ┌─────────────────────┼──────────────────┐
              │                     │                   │
      poster.followers < T?         │            poster.followers > T?
              │                     │                   │
    ┌─────────▼──────────┐          │       ┌───────────▼────────┐
    │ Fanout-on-write     │          │       │ Skip fanout        │
    │ Write post_id to   │          │       │ (celebrity: post   │
    │ each follower's    │          │       │  fetched at read)  │
    │ Redis ZSET         │          │       └────────────────────┘
    └────────────────────┘          │

FEED READ PATH:
                         ┌──────────────────────┐
    Bob requests feed ──▶│    Feed Service       │
                         └──────────┬────────────┘
                                    │
               ┌────────────────────┼──────────────────────┐
               │                                           │
    ┌──────────▼─────────┐                    ┌────────────▼────────┐
    │ Redis: Bob's ZSET   │                    │ Post DB: recent posts│
    │ (pre-built by      │                    │ from Bob's celebrity │
    │  fanout-on-write)  │                    │ followings           │
    └──────────┬─────────┘                    └────────────┬────────┘
               │                                           │
               └──────────────────┬────────────────────────┘
                                  │ merge + rank + paginate
                         ┌────────▼──────────┐
                         │ Batch-fetch post   │──▶ Post DB (fetch post details
                         │ details by IDs     │    for top 50 post_ids)
                         └────────┬──────────┘
                                  │
                         Return feed to Bob

KEY INVARIANT:
  Write amplification is bounded (regular users only, celebrity writes skipped).
  Read amplification is bounded (celebrity lookups only, not all followings).
  The merge step is always cheap: pre-built feed + small celebrity list.
```

---

## 🔬 Interview Q&A

### Q: "Why not just use fanout-on-write for everyone? It's simpler."

> Write amplification kills you at celebrity scale. If Justin Bieber (100M followers) posts, that's 100M Redis writes — a fanout job that takes hours, not milliseconds. Meanwhile, Bob opens his feed and sees the post 3 hours late. The celebrity problem is the fundamental reason pure fanout-on-write doesn't work. The hybrid exists specifically to handle this edge case that affects a tiny fraction of accounts but would dominate write infrastructure if not handled separately.

---

### Q: "Why not just use fanout-on-read for everyone? No write amplification at all."

> Read amplification at scale. If Bob follows 1000 accounts and opens his feed, you need to fetch 10 recent posts from each of 1000 accounts = 10,000 DB queries or Redis lookups per feed open. At 10M simultaneous active users, that's 100B queries/sec. Even with Redis, this is untenable. Additionally, merging and sorting 10,000 posts at read time adds hundreds of milliseconds of latency per request.

---

### Q: "How do you handle the feed for a new follower? They shouldn't get Alice's 3-year-old posts."

> Two approaches: (1) Back-fill on follow — when Bob follows Alice, write Alice's recent N posts (last 7 days, max 50) into Bob's Redis ZSET retroactively. Simple but can be slow for active accounts. (2) Pull recent posts at read time for the first N feed loads after a new follow — treat Alice as a "temporary celebrity" until the back-fill completes. Set a flag on Bob's account: "newly following Alice, pull her posts until caught up." This is how Twitter handles it.

---

### Q: "A celebrity has 100M followers. They post. When does the last follower see it in their feed?"

> With pure fanout-on-write: never fast enough — 100M Redis writes at 1M writes/sec takes 100 seconds minimum. That's why celebrities use fanout-on-read. With the hybrid: the celebrity's post is in Post DB immediately. Any follower who opens their feed within seconds sees it (pulled from Post DB at read time). Feed read latency for the celebrity's post: < 100ms (same as any single DB query). The 100M followers see it the moment they open their app — not batched over 100 seconds.

---

### Q: "How do you paginate a feed without showing duplicates or missing posts between pages?"

> Use cursor-based pagination, not offset-based. The cursor is the timestamp (or score) of the last post on the previous page. Next page: `ZREVRANGEBYSCORE feed:bob (last_seen_score -inf LIMIT 0 50`. This is stable — new posts added to the top of the feed don't shift existing posts' positions and cause page 2 to show duplicates of page 1. Offset-based pagination (`LIMIT 50 OFFSET 50`) breaks when new posts arrive between page 1 and page 2 loads.

---

### Q: "Your Redis cluster holding all feed caches goes down. What happens?"

> All pre-built feed data is gone — fanout-on-write data is lost (Redis is ephemeral without persistence). Fall back to fanout-on-read for all users: fetch recent posts from all followings live from Post DB. This is slower and more expensive but correct. Recovery plan: (1) Route all feed reads to Post DB (fanout-on-read for everyone, degraded mode). (2) Rebuild feed caches from Post DB as users actively request their feeds (lazy rebuild). (3) Don't pre-populate feeds for inactive users — let them rebuild naturally on next login. Feed caches can be lost safely; Post DB is the source of truth.

---

### Q: "How do you rank posts by engagement rather than chronologically?"

> Apply a scoring function at feed assembly time. Twitter's simplified formula: `score = recency_score × (likes × w1 + retweets × w2 + replies × w3)`. Weights (w1, w2, w3) are tuned by ML. Implementation: at merge time in Feed Service, re-score each post_id using its engagement metrics (fetched from a separate engagement store or cached on the post). Sort by score instead of timestamp. Cache the ranked feed in Redis with a short TTL (30–60s) — re-ranking every request is expensive; once per minute is sufficient.

---

### Q: "What's the difference between a feed and a notification? Both involve fanout."

> Different latency and delivery requirements. A feed is pull-based and eventually consistent — Bob opens the app and sees Alice's post from 5 minutes ago. Acceptable. A notification is push-based and time-sensitive — Bob gets a push notification within seconds of being mentioned. The fanout for notifications uses a different pipeline: post event → notification fanout service → push notification system (APNs, FCM). Feed fanout writes to Redis. Notification fanout writes to a push queue. They share the event source (post created) but diverge immediately in how they deliver.

---

## ⚠️ Anti-patterns (don't say these)

- **Storing full post content in feed caches** — store only post_ids; fetch details separately via batch lookup
- **Fanning out to inactive users** — skip fanout for users inactive > 30 days; they pull on return
- **Using a plain Redis List instead of ZSET** — plain List has no score-based sorting; migrate to ZSET at scale is painful

---

## 🧩 Common Interview Problems

| Problem | Strategy | Key decision |
|---|---|---|
| Design Twitter / X | Hybrid fanout | Celebrity threshold (~10K), ZSET per user |
| Design Instagram | Hybrid; heavier ranking at read time | Lower threshold than Twitter (richer content) |
| Design Facebook News Feed | Hybrid + ML ranking layer | Engagement score at merge time |
| Design LinkedIn Feed | Hybrid; lower follower counts → fanout-on-write dominant | Professional graph, less celebrity skew |
| Design YouTube Subscriptions | Fanout-on-write (channels post rarely; high sub counts → hybrid) | Sub counts vary wildly — threshold matters |

---

## 🔗 Full notes

`DeepDive/03-feed-and-fanout.md` — strategy deep dives, celebrity threshold tuning, full failure mode Q&A
