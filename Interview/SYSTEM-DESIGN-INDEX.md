# System Design — Solved Questions Index

> Quick-nav for all solved system design questions across Confluent, DocuSign, and Disney.
> Each row has a clickable link + keyword tags — use Ctrl+F / ⌘+F on the tag you're thinking about.

---

## 🔍 Keyword Legend (search these)

`bloom-filter` `cache` `cassandra` `cdn` `consistent-hashing` `cursor-pagination` `distributed` `dynamo` `elasticsearch` `email` `fan-out` `heartbeat` `inverted-index` `jwt` `kafka` `kv-store` `leaderboard` `messaging` `notification` `oauth` `pagination` `pki` `podcast` `rate-limiter` `read-heavy` `redis` `ring-buffer` `rss` `s3` `sharding` `smtp` `social-feed` `sorted-set` `sql-schema` `streaming` `time-series` `url-shortener` `video` `websocket` `write-heavy`

---

## 🟣 Confluent

> Round type: Type 1 = API + Data Model, Type 2 = Full System Design (HLD + scale stages)

| # | Question | Type | Tags | Link |
|---|---|---|---|---|
| 1 | **TempMail — Disposable Email Service** | HLD | `email` `smtp` `bloom-filter` `kafka` `inbox` `uuid` `read-heavy` | [tempmail-disposable-email.md](./Confluent/SystemDesign/tempmail-disposable-email.md) |
| 2 | **Aggregate News Feed** | HLD | `rss` `social-feed` `fan-out` `kafka` `redis` `deduplication` `ranking` `per-source-cache` | [aggregate-news-feed.md](./Confluent/SystemDesign/aggregate-news-feed.md) |
| 3 | **Distributed Key-Value Store** | HLD | `kv-store` `distributed` `consistent-hashing` `cassandra` `redis` `read-heavy` `bloom-filter` `sstable` | [distributed-kv-store.md](./Confluent/SystemDesign/distributed-kv-store.md) |
| 4 | **Health Check / wasAlive Monitoring** | HLD | `heartbeat` `monitoring` `ring-buffer` `sliding-window` `time-series` `kafka` `redis` | [health-check-monitoring.md](./Confluent/SystemDesign/health-check-monitoring.md) |
| 5 | **Feedly / Podcast Service API** | API + HLD | `podcast` `rss` `feed` `api-design` `sql-schema` `pagination` `fan-out` `per-source-cache` | [feedly-podcast-api-design.md](./Confluent/SystemDesign/feedly-podcast-api-design.md) |

---

## 🔵 DocuSign

> All are 60-min full-round solutions. Read `solution-notes-standards.md` before any file.

| # | Question | Tags | Link |
|---|---|---|---|
| A1 | **URL Shortener** | `url-shortener` `base62` `redirect` `hashing` `analytics` `read-heavy` | [A1-url-shortener.md](./DocuSign/r2-solutions/A1-url-shortener.md) |
| A2 | **Chat / Messenger (Facebook-scale)** | `messaging` `chat` `websocket` `real-time` `fan-out` `cassandra` `write-heavy` | [A2-chat-messenger.md](./DocuSign/r2-solutions/A2-chat-messenger.md) |
| A3 | **Video Distribution (Worldwide)** | `video` `cdn` `streaming` `s3` `transcoding` `read-heavy` | [A3-video-distribution.md](./DocuSign/r2-solutions/A3-video-distribution.md) |
| B1 | **Subscription Billing API** | `billing` `subscription` `payments` `idempotency` `sql-schema` `saas` | [B1-subscription-billing.md](./DocuSign/r2-solutions/B1-subscription-billing.md) |
| C1 | **Rate Limiter (Microservices API)** | `rate-limiter` `token-bucket` `sliding-window` `redis` `api-gateway` `distributed` | [C1-rate-limiter.md](./DocuSign/r2-solutions/C1-rate-limiter.md) |
| C2 | **Expense Report — Data Model** | `expense` `sql-schema` `data-model` `reporting` `pagination` | [C2-expense-report.md](./DocuSign/r2-solutions/C2-expense-report.md) |
| C3 | **Pagination API + Data Model** | `pagination` `cursor-pagination` `keyset` `api-design` `sql-schema` | [C3-pagination-api.md](./DocuSign/r2-solutions/C3-pagination-api.md) |
| CF1 | **Fitness Class Booking System (Cult.fit)** | `booking` `availability` `concurrency` `idempotency` `sql-schema` `redis` | [CF1-class-booking-system.md](./DocuSign/r2-solutions/CF1-class-booking-system.md) |
| D1 | **Digital Signature System** | `pki` `digital-signature` `certificate` `signing` `docusign` `audit-log` `s3` | [D1-digital-signature.md](./DocuSign/r2-solutions/D1-digital-signature.md) |
| D2 | **Document Storage & Retrieval** | `s3` `blob` `document-storage` `versioning` `metadata` `read-heavy` | [D2-document-storage.md](./DocuSign/r2-solutions/D2-document-storage.md) |
| D3 | **Real-Time Notification Service** | `notification` `push` `email` `sms` `fan-out` `kafka` `websocket` `write-heavy` | [D3-notification-service.md](./DocuSign/r2-solutions/D3-notification-service.md) |
| E1 | **Search System (Full-Text at Scale)** | `search` `elasticsearch` `inverted-index` `full-text` `ranking` `read-heavy` | [E1-search-system.md](./DocuSign/r2-solutions/E1-search-system.md) |
| E2 | **Authentication & Authorization System** | `auth` `jwt` `oauth` `sso` `session` `rbac` `rate-limiter` `distributed` | [E2-authentication-system.md](./DocuSign/r2-solutions/E2-authentication-system.md) |

---

## 🔴 Disney

> R3 = onsite solutions. HLD/LLD notes are companion concept files.

| # | Question | Type | Tags | Link |
|---|---|---|---|---|
| D1 | **Global Game Leaderboard** | HLD | `leaderboard` `redis` `sorted-set` `ranking` `sharding` `real-time` `write-heavy` | [D1-leaderboard.md](./Disney/r3-solutions/D1-leaderboard.md) |
| D2 | **Ad Budget Pacing & Impression Counting** | HLD | `ad-pacing` `impression` `budget` `redis` `counter` `streaming` `write-heavy` `time-series` | [D2-ad-budget-pacing.md](./Disney/r3-solutions/D2-ad-budget-pacing.md) |
| D3 | **API Rate Limiter** | HLD | `rate-limiter` `token-bucket` `sliding-window` `redis` `distributed` `api` | [D3-rate-limiter.md](./Disney/r3-solutions/D3-rate-limiter.md) |
| — | **HLD: Ad Impression Pacing (concept)** | Concept | `ad-pacing` `impression` `cdn` `streaming` `disney-ads` | [HLD-ad-impression-pacing.md](./Disney/HLD-ad-impression-pacing.md) |
| — | **LLD: Rate Limiter in Java** | LLD | `rate-limiter` `java` `concurrency` `token-bucket` `thread-safe` `reentrantlock` | [LLD-rate-limiter-java.md](./Disney/LLD-rate-limiter-java.md) |

---

## 🗺️ Cross-Question Patterns

> Same core pattern appearing across multiple questions — knowing one accelerates the others.

| Pattern | Questions |
|---|---|
| **Rate Limiter** | DocuSign C1, Disney D3 (HLD), Disney LLD-rate-limiter |
| **Fan-out / Feed** | Confluent Aggregate News Feed, Confluent Feedly, DocuSign D3 Notification |
| **Redis counter + window** | Disney D2 Ad Pacing, DocuSign C1 Rate Limiter, Disney D3 Rate Limiter |
| **Bloom filter** | Confluent TempMail, Confluent KV Store |
| **Pagination / Cursor** | DocuSign C3, Confluent Feedly |
| **Kafka as decoupler** | Confluent TempMail, Confluent News Feed, DocuSign D3 Notification, Disney D2 |
| **Consistent hashing** | Confluent KV Store, DocuSign A2 Chat |
| **S3 / blob storage** | DocuSign A3 Video, DocuSign D1 Digital Signature, DocuSign D2 Document Storage |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Aug 10, 2026 | **File created.** Indexed all solved system design questions across Confluent (5), DocuSign (13), and Disney (5 files). Added cross-question pattern table for faster warm-up. |
