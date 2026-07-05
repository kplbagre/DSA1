# Pattern Deep Dive: Feed & Fanout

> **Read this when:** You need to deeply understand how to build personalized feeds (timelines, activity streams, news feeds) and how to distribute content from one creator to many followers efficiently.
> **Pre-interview refresh:** Use `Reference/03-feed-and-fanout.md` (5 min).

---

## 🎯 What Problem Does This Pattern Solve?

You need to show each user a personalized feed — their timeline — assembled from content posted by the accounts they follow. The challenge has two faces:

**The write problem (fanout):** When Alice (100M followers) posts a tweet, how do you update 100M followers' feeds? Writing to 100M rows takes hours if done naively.

**The read problem (assembly):** When Bob opens the app, how do you assemble his feed from 500 accounts he follows — quickly, sorted, ranked — without scanning every post from every account he follows?

This is NOT the same as Scaling Reads (serving the same content fast) or Scaling Writes (ingesting high-volume writes). This pattern is about **personalized content distribution** — where each user's view is unique and assembled from many sources.

---

## 💡 Core Insight

There are two fundamentally different answers to "when do I do the work of assembling a user's feed?":

- **Fanout-on-write (push model):** Do the work at post creation time. When Alice posts, immediately push her post into all her followers' feed caches. Reading a feed is instant — it's already assembled.
- **Fanout-on-read (pull model):** Do the work at read time. When Bob opens the app, fetch recent posts from all accounts he follows and merge them on the fly. Writing is cheap; reading is expensive.

Neither works alone at scale. The production answer is a **hybrid** that routes based on follower count:
- Regular users → fanout-on-write (fast reads, manageable write amplification)
- Celebrities → fanout-on-read (avoid millions of writes per post)

> **KEY INSIGHT:** "Pre-compute what you can (regular users), pull what you must (celebrities). Merge at read time."

---

## 🗂️ The 3 Strategies

---

### Strategy 1 — Fanout-on-Write (Push Model)

🧠 **Mental model:** Carol (500 followers) posts a photo. In 200ms, her post_id lands in all 500 followers' Redis feed caches. When any of them open the app, their feed is already assembled — zero assembly latency at read time.

When a user posts, a fanout worker immediately writes the post reference into every follower's feed cache.

**When to use:**
- Poster has a manageable follower count (< 10K followers)
- Feed reads must be extremely fast (gaming social features, chat)
- Users are highly active (high read:write ratio — the pre-computed feed is actually read)

**When NOT to use:**
- Celebrities with millions of followers — write amplification becomes catastrophic
- Users who post rarely but have huge followlists — wasted precomputation for feeds never read
- Inactive users — precomputing feeds for users who log in once a month is waste

**How it works:**

**Steps in plain English:**
1. **Post** — Alice posts a tweet (1 write to Post DB).
2. **Read followers** — Fanout service reads Alice's follower list from Follower DB.
3. **Write to feeds** — For each follower, write post_id to that follower's feed ZSET in Redis.
4. **Feed structure** — Feed is a Sorted Set (ZSET) keyed by timestamp or score in Redis.
5. **Read** — When Bob opens his feed: fetch ZSET from Redis → batch-fetch post details by IDs → return.

```
                     ┌─────────────────┐
     Alice posts ───▶│  Post Service   │──▶ Postgres (posts table)
                     └────────┬────────┘
                              │ async
                     ┌────────▼────────┐
                     │ Fanout Service  │──▶ reads Alice's followers
                     │ (async worker)  │    from Follower DB
                     └────────┬────────┘
                              │ for each follower
                 ┌────────────┼────────────┐
                 │            │            │
          ┌──────▼──┐  ┌──────▼──┐  ┌─────▼──────┐
          │Bob's    │  │Carol's  │  │Dave's      │
          │Feed     │  │Feed     │  │Feed        │
          │(Redis   │  │(Redis   │  │(Redis      │
          │ list)   │  │ list)   │  │ list)      │
          └─────────┘  └─────────┘  └────────────┘

Write amplification: 1 post → N writes (N = follower count)
For 1K followers: 1 post = 1K Redis writes (fine)
For 10M followers: 1 post = 10M Redis writes (catastrophic)
```

**Feed data structure in Redis:**
```
Key: feed:{user_id}
Type: Sorted Set (ZSET)
Score: timestamp (or engagement score)
Value: post_id

ZADD feed:bob 1719820800 "post_id:789"
ZREVRANGE feed:bob 0 49  → returns 50 most recent post IDs
```

---

### Strategy 2 — Fanout-on-Read (Pull Model)

🧠 **Mental model:** RSS reader (Feedly) — when you open it, it fetches the latest from every feed you subscribe to and merges them live. No precomputation. Every open triggers fresh fetches. Cheap to write, expensive to read.

When a user opens their feed, the system fetches recent posts from all accounts they follow and merges them in real time.

**When to use:**
- High-follower accounts (celebrities) — no write amplification
- Users who follow few accounts (merging 10 feeds is cheap)
- Inactive users — no wasted precomputation

**When NOT to use:**
- Users follow thousands of accounts — merging thousands of feeds at read time is slow
- High read volume — every feed open triggers N DB queries (N = followings count)
- Feed needs complex ranking — ranking 10K posts at read time is expensive

**How it works:**

**Steps in plain English:**
1. **Open feed** — Bob opens his feed.
2. **Fetch following list** — System fetches Bob's "following" list (500 accounts).
3. **Fetch recent posts** — For each account, fetch their 10 most recent post_ids (500 Redis/DB lookups).
4. **Merge** — Merge 5,000 post_ids, sort by timestamp, take top 50.
5. **Hydrate** — Batch-fetch post details for top 50 post_ids.
6. **Return** — Deliver assembled feed to Bob.

> **Scale problem:** Step 3 = 500 lookups per feed open. At 10M simultaneous users: 5B lookups/sec — not feasible.

```
Bob opens feed ──▶ Feed Service
                        │
                        │ fetch following list: [Alice, Carol, Dave, ...]
                        │
             ┌──────────┼──────────┐
             │          │          │
      ┌──────▼──┐ ┌─────▼──┐ ┌────▼───┐
      │Alice's  │ │Carol's │ │Dave's  │  ... 500 accounts
      │recent   │ │recent  │ │recent  │
      │posts    │ │posts   │ │posts   │
      └──────┬──┘ └─────┬──┘ └────┬───┘
             │          │         │
             └──────────▼─────────┘
                    Merge + sort
                    Top 50 posts
                        │
                   Return to Bob
```

---

### Strategy 3 — Hybrid (Production Approach)

🧠 **Mental model:** Twitter — Carol's tweets (500 followers) are pre-fanned into follower caches. Elon's tweets (100M followers) are NOT fanned out — too expensive. Instead, at feed-read time, Twitter fetches Elon's recent posts and injects them inline. Carol = push. Elon = pull. Merge = your final feed.

Route based on follower count. Use fanout-on-write for regular users; fanout-on-read for celebrities. Merge at read time.

**The threshold:** Typically 10K–100K followers. Accounts above this are "celebrities" and flagged in the system.

**How it works:**

**Steps in plain English:**
1. **Celebrity post** — Alice (50M followers, flagged) posts → write to Post DB only. No fanout. Her posts are fetched at read time.
2. **Regular post** — Carol (500 followers) posts → write to Post DB + fanout service writes post_id to 500 follower Redis ZSETs.
3. **Feed read (Bob opens app):**
   - Fetch Bob's pre-built Redis ZSET (has Carol's post and all other non-celebrity posts).
   - Fetch Bob's celebrity following list: [Alice, Elon, ...].
   - For each celebrity, query their recent posts from Post DB.
   - Merge both sets, sort by score, paginate top 50.
   - Batch-fetch post details by IDs, return to Bob.

```
POST TIME:
Alice posts ──▶ Post DB only (no fanout — too many followers)
Carol posts ──▶ Post DB + fanout to 500 follower feeds in Redis

FEED READ TIME:
Bob's feed = Redis pre-built feed  +  merge  +  celebrity post lookups
             (Carol + others)                   (Alice + other celebs)

              ┌─────────────────────┐
              │  Feed Service       │
              └──┬──────────────────┘
                 │
      ┌──────────┼────────────────────┐
      │          │                    │
┌─────▼──────┐  │             ┌───────▼──────┐
│ Redis:     │  │             │ Post DB:     │
│ Bob's pre- │  │             │ Alice's last │
│ built feed │  │             │ 10 posts     │
│ (fanout-on │  │             │ (fanout-on-  │
│  write)    │  │             │  read)       │
└─────────────┘  │             └──────────────┘
                 │
                 ▼
            Merge + rank + paginate ──▶ return to Bob

KEY INVARIANT:
  Write amplification is bounded by capping fanout at follower threshold.
  Read latency is bounded by capping pull to celebrity accounts only (small N).
```

---

## 🧭 Decision Sequence

```
START: Need to build personalized feed

Step 1 ── Decide feed storage
          Use Redis Sorted Set per user (ZSET with timestamp as score).
          Stores post_ids, not full posts (post details fetched separately).

Step 2 ── Implement fanout-on-write for regular users
          Async fanout worker: on post → write post_id to N follower feed ZSETs.
          Set threshold: accounts with > T followers get celebrity treatment.
          Start with T = 10K; tune based on observed write amplification.

Step 3 ── Implement fanout-on-read for celebrities
          No fanout at post time. Store posts in Post DB.
          At feed read time: fetch celebrity followings, query their recent posts.

Step 4 ── Merge at read time
          Feed = (Redis pre-built feed) + (celebrity posts fetched live).
          Sort by timestamp or engagement score.
          Paginate with cursor (not offset — see pagination notes).

Step 5 ── Add feed ranking (if needed beyond chronological)
          Apply engagement score model at merge time.
          Cache ranked feed with short TTL (30–60s) to avoid re-ranking per request.
```

---

## 🎨 Visual — Full Hybrid Architecture

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

### Q: "How does Instagram's feed differ from Twitter's for fanout?"

> Instagram uses a more aggressive fanout-on-write (they historically precomputed feeds more eagerly) but also adopted a hybrid model for celebrity accounts. The key difference: Instagram's ranking model is heavier (relationship signals, interest graphs) and applied more aggressively at read time. Twitter's original architecture was purely fanout-on-write, then hit the celebrity problem at scale (the "Bieber problem") and adopted the hybrid. The celebrity threshold is a tunable parameter — Instagram uses a lower threshold because their content is richer (images) and re-fetching is more expensive.

---

### Q: "What's the difference between a feed and a notification? Both involve fanout."

> Different latency and delivery requirements. A feed is pull-based and eventually consistent — Bob opens the app and sees Alice's post from 5 minutes ago. Acceptable. A notification is push-based and time-sensitive — Bob gets a push notification within seconds of being mentioned. The fanout for notifications uses a different pipeline: post event → notification fanout service → push notification system (APNs, FCM). Feed fanout writes to Redis. Notification fanout writes to a push queue. They share the event source (post created) but diverge immediately in how they deliver.

---

## ⚠️ Anti-patterns

- **Storing full post content in feed caches.** Feed caches (Redis ZSETs) should store only post_ids as values, with timestamps as scores. Storing full post text, image URLs, and metadata bloats Redis memory enormously and makes cache invalidation (when a post is edited or deleted) require scanning every follower's feed. Store IDs; fetch details separately via batch lookup from Post DB or a post cache.

- **Ignoring inactive users in fanout.** Running fanout for users who haven't logged in for 6 months wastes Redis memory and write throughput. Add an "active" flag to user accounts; only fanout to followers who have been active in the last 30 days. Inactive users get fanout-on-read when they eventually return — they just see a slightly slower first feed load.

- **Using a single Redis key per user as a plain list.** A plain Redis List (`LPUSH`, `LRANGE`) doesn't support score-based sorting or efficient range queries. Use a Sorted Set (ZSET) with timestamp or engagement score as the sort key from day one. Migrating from List to ZSET at scale requires a full Redis migration.

---

## 🗺️ Problems Map

| Interview Problem | Why Feed & Fanout Applies | Key Decision |
|---|---|---|
| Design Twitter / X | Timeline = personalized feed from followings | Hybrid fanout (celebrity threshold) |
| Design Instagram | Photo feed from followings | Hybrid; heavier ranking at read time |
| Design Facebook News Feed | Social graph feed with engagement ranking | Hybrid + ML ranking layer |
| Design LinkedIn Feed | Professional activity feed | Hybrid; lower follower counts → fanout-on-write dominant |
| Design YouTube Subscriptions | Video feed from subscribed channels | Fanout-on-write (channels post rarely; subscriber counts high → hybrid) |
| Design TikTok For You Page | Algorithmic feed (not just followings) | Recommendation engine, not pure fanout |

---

## 🔗 Concept Notes — Go Here for "How It Works"

- **Push notifications fanout mechanics** → `../../Core-Architecture/Service-Communication/46-push-notifications-fanout.md`
- **Redis Sorted Sets and data structures** → `../../Foundations/Performance-and-Scale/03-caching.md`
- **Kafka fan-out** (how one event reaches multiple consumers) → `../../Core-Architecture/Service-Communication/19-message-queues-kafka-rabbitmq.md`
- **Cursor-based pagination** → `../../Foundations/Data-Fundamentals/43-pagination-cursor-based.md`

---

## 🔄 Changelog

| Date | Change |
|---|---|
| July 2026 | Note created. Novel pattern — no HI equivalent as standalone. Written from first principles. |
