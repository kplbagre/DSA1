# Real-time Updates — Quick Reference

> **Read this:** 30 min before an interview involving chat, live feeds, notifications, or collaborative editing.
> **Deep study:** `DeepDive/07-real-time-updates.md`

---

## 🎯 What kind of problem is this?

Use this pattern when: **the client needs to see server-side changes without refreshing** — classic HTTP request-response is insufficient.

Trigger words: "chat app", "live scores", "stock price feed", "order tracking", "typing indicator", "collaborative editing", "user has to refresh to see new messages", "real-time", "push notifications to browser", "driver location updates".

---

## 🧭 Decision Sequence

```
START: Client needs to see server-side changes without refreshing

Step 1 → Does the client ALSO send data in real-time?
         Yes (chat, collaborative editing, gaming, live auction)
               → WebSockets. Two-way is the requirement.
         No (notifications, order status, live feed, dashboard)
               → Go to Step 2.

Step 2 → Is the client a browser?
         Yes → Server-Sent Events (SSE). Native browser support, HTTP-native.
         No (mobile, desktop app) → WebSockets (more universal library support).

Step 3 → Can you use SSE/WebSockets at all?
         Network/proxy blocks persistent connections?
               → Long Polling. Slower but works everywhere.
         Freshness requirement is > 30 seconds?
               → Short Polling. Simplest possible implementation.

Step 4 → At what scale?
         < 10K concurrent connections → Any of the above on a single server.
         > 10K → Need connection management layer (pub/sub backend).
         > 1M → Dedicated WebSocket gateway cluster + pub/sub backend.
```

---

## ⚡ Strategies at a Glance

| Strategy | Use when | Don't use when |
|---|---|---|
| **Short Polling** | Staleness > 30s OK, very simple, low user count | Large concurrent users, sub-second freshness |
| **Long Polling** | Can't use WebSockets (firewall), infrequent messages | Very high message frequency, millions of users |
| **SSE (Server-Sent Events)** | Browser client, server-push only, HTTP/2 | Client also sends real-time data; non-browser clients |
| **WebSockets** | Two-way real-time, sub-100ms latency, high frequency | Server-push only (SSE is simpler); infrequent updates |
| **WebRTC** | P2P video/audio, sub-50ms latency, bandwidth cost matters | Data must pass through server for auth/ordering/broadcast |

**Key numbers to remember:**
- Short polling (1M users × 1 req/sec) = 1M req/sec just to say "nothing new" — avoid
- SSE: one-way, HTTP-native, auto-reconnect + `Last-Event-ID` built into browser
- WebSocket: one server holds ~50K–100K connections (memory bound)
- For 1M connections: ~10–20 gateway servers + pub/sub layer
- Pub/sub decouples business logic (stateless) from WebSocket servers (stateful)
- Redis Pub/Sub latency: < 1ms; Kafka fan-out: higher latency but durable

**Redis Pub/Sub in a nutshell (know this cold):**
- `SUBSCRIBE user:123` — gateway runs this on client connect; Redis registers it as a subscriber
- `PUBLISH user:123 '{"event":"x"}'` — backend runs this on state change; Redis instantly delivers to all subscribers on that channel
- **Fire-and-forget:** no persistence, no ACK, no replay — if subscriber is down, it misses the message
- **That's why two layers exist:** Redis = fast-path push (< 1ms); DB event log = catch-up after reconnect
- Choose Redis over Kafka here: both need < 1ms; Kafka is 5–50ms (disk + poll interval)

---

## 🎨 Key Architecture Diagram

```
                    Clients (millions)
              ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐
              │Client│ │Client│ │Client│ │Client│ ...
              └──┬───┘ └──┬───┘ └──┬───┘ └──┬───┘
                 │WebSocket or SSE  │        │
         ┌───────▼──────────────────▼────────▼───────────┐
         │         WebSocket Gateway Cluster               │
         │  (stateful: each server holds N connections)    │
         │  Server 1       Server 2       Server 3         │
         │  [conn pool]    [conn pool]    [conn pool]      │
         └───────────────────────┬────────────────────────┘
                                 │ subscribe / publish
         ┌───────────────────────▼────────────────────────┐
         │              Pub/Sub Layer                       │
         │         (Redis Pub/Sub or Kafka)                 │
         │  Channel: "user:123"  Channel: "room:general"   │
         └───────────────────────┬────────────────────────┘
                                 │ publishes events
         ┌───────────────────────▼────────────────────────┐
         │           Backend Services                       │
         │  Order Service  Chat Service  Price Service      │
         │  (publishes to channel when state changes)       │
         └────────────────────────────────────────────────┘

Flow:
1. Backend service changes state → publishes event to pub/sub channel.
2. All WS gateway servers subscribed to that channel receive the event.
3. Each gateway server pushes the event to connected clients who care about it.

KEY INVARIANT:
   WebSocket servers are stateful (they hold connections).
   Business logic is stateless (backend services).
   Pub/sub decouples them: backend doesn't know which WS server holds which client.
```

---

## 🔬 Interview Q&A

### Q: "What's the difference between WebSockets and SSE? When do you use each?"

> WebSockets: full-duplex TCP connection — both client and server can send at any time. Use when the client also generates real-time data (chat messages, cursor positions, bids, game moves). SSE: one-way, server-to-client only, over HTTP. Use when only the server pushes (notifications, live prices, order status updates). SSE is simpler: it's just HTTP, works through standard load balancers, auto-reconnects built into the browser, and supports `Last-Event-ID` for reliable delivery. WebSockets require sticky sessions or a pub/sub layer to route messages back to the right server. Default to SSE for push-only; upgrade to WebSockets when the client sends too.

---

### Q: "You have 1M concurrent WebSocket connections. How do you architect this?"

> WebSocket connections are stateful and long-lived — one server can hold ~50K–100K connections depending on memory. For 1M connections: (1) WebSocket gateway cluster (10–20 dedicated gateway servers, each holding 50–100K connections). (2) Pub/sub layer (Redis Pub/Sub or Kafka) connecting gateways to business logic. When Order Service has an update for user 123, it publishes to channel `user:123`. The gateway server holding user 123's connection is subscribed to that channel and pushes the event. Business logic never knows which gateway holds which connection. (3) Load balancer routes new WebSocket upgrades across gateway servers (sticky session not required — pub/sub handles message routing).

---

### Q: "A WebSocket server crashes. What happens to all connected clients?"

> All connections on that server are dropped. Clients detect the disconnect (WebSocket close event or ping timeout) and reconnect to any available gateway server. Reconnection time: typically 1–5 seconds with exponential backoff. The new server doesn't have the client's in-memory state. Mitigation: (1) Stateless business logic — the WS server is just a conduit; all state is in the DB or pub/sub layer. (2) Clients reconnect and re-subscribe to their channels; the new gateway server subscribes them to the same pub/sub channels. (3) For missed events during disconnect: client sends `Last-Event-ID` (SSE) or the server tracks per-user event sequence numbers so clients can request missed events on reconnect.

---

### Q: "How do you implement typing indicators in a chat app (e.g., 'Alice is typing...')"

> Typing indicators are ephemeral, high-frequency, and low-durability — exactly what WebSockets are built for. (1) When Alice types, client sends a WebSocket message: `{type: "typing", roomId: 42}`. (2) Server receives, publishes `{type: "typing", user: "Alice"}` to Pub/Sub channel `room:42`. (3) All WebSocket servers subscribed to `room:42` push the typing event to their connected clients. (4) No DB write — typing indicators are not persisted. (5) Client clears "Alice is typing" indicator if no new typing event arrives within 3 seconds (debounce). Key: this entire flow is < 50ms end-to-end.

---

### Q: "How do you handle a user who has the app open in 3 browser tabs?"

> Three WebSocket connections — potentially on three different gateway servers. When an event arrives for that user, publish to `channel:user:123`. All three gateway servers subscribed to that channel receive the event and push to each respective tab. The user sees the update in all three tabs simultaneously. No special handling needed — pub/sub fan-out handles it naturally. The only edge case: actions that mutate state (marking a notification as read) should be idempotent and reflected back to all tabs so they stay in sync.

---

### Q: "WebSockets vs HTTP/2 server push. Which do you use and why?"

> HTTP/2 server push was designed for preloading static assets (pushing CSS alongside HTML before the browser asks). It was deprecated for streaming real-time data — browsers removed support for it in 2022. It's not a WebSocket alternative. For real-time data streaming: use SSE over HTTP/2 (excellent — HTTP/2 multiplexes streams efficiently) or WebSockets over HTTP/1.1 or HTTP/2. HTTP/2 + SSE is a compelling alternative to WebSockets for push-only use cases because SSE is simpler and HTTP/2 gives you multiplexing.

---

### Q: "How do you ensure a client doesn't miss events if their connection drops for 10 seconds?"

> Two approaches: (1) SSE `Last-Event-ID` — server assigns a monotonic ID to each event. Client reconnects with `Last-Event-ID: 42` header. Server replays events with ID > 42. Requires server to buffer recent events (Redis list with TTL). (2) Client-side sequence numbers — client tracks last received sequence; on reconnect, sends `?since=42`. Server queries its event log for events after sequence 42. Key: the server must store events for at least as long as the expected reconnect window (e.g., 60 seconds). Events older than the buffer window are gone — client does a full state sync instead.

---

### Q: "Long polling vs WebSockets — when is long polling the right choice?"

> Long polling is right when: (1) You can't use WebSockets (corporate firewall, old proxy that doesn't support Upgrade header, legacy client). (2) Message frequency is low (< 1 message per minute) — holding a persistent WebSocket connection for hourly updates is overhead for no gain. (3) Simplicity is critical — long polling is regular HTTP; no special infrastructure needed. The cost of long polling: each reconnect is a full HTTP round-trip (TCP handshake + TLS handshake = ~100ms overhead). At high message frequency, this overhead accumulates. At low frequency, it's negligible.

---

### Q: "How does Slack implement real-time message delivery?"

> Slack uses WebSockets for the desktop/web client. When you open Slack, a WebSocket connection is established to Slack's gateway. When someone sends a message to a channel you're in: (1) Slack's messaging service persists the message to DB. (2) Publishes event to pub/sub for channel members. (3) Gateway server pushes the message over WebSocket to all online members. For mobile, Slack uses APNs/FCM for push notifications when the app is backgrounded (WebSocket is not viable in background on mobile). For large channels (thousands of members), Slack uses fan-out workers to push to each member's connection rather than a single publish.

---

## ⚠️ Anti-patterns (don't say these)

- **Polling every 1 second as "simple substitute"** — 100K users × 1 req/sec = 100K req/sec to say "nothing changed" 99% of the time; SSE/WebSocket at any real scale
- **Storing connection state in the application DB** — WebSocket connections are ephemeral; store in Redis (TTL-based expiry); putting in Postgres creates a write-heavy hot table
- **WebSockets when SSE is sufficient** — WebSockets need pub/sub routing; SSE is plain HTTP; if client never sends data, SSE is simpler with no operational overhead

---

## 🧩 Common Interview Problems

| Problem | Strategy | Key decision |
|---|---|---|
| Design Chat App | WebSockets | Bidirectional — client sends messages |
| Design Live Sports Score | SSE | Server push only, browser-native |
| Design Stock Price Feed | SSE or WebSockets | High frequency — prefer WebSockets if client also sends orders |
| Design Uber Driver Tracking | WebSockets | Client (driver) also sends location |
| Design Notification System | SSE + APNs/FCM for mobile | SSE for web; push notification for mobile background |
| Design Collaborative Doc Editor | WebSockets | Cursor positions, edits — fully bidirectional |
| Design Live Auction | WebSockets | Client bids + server pushes bid updates |
| Design Order Tracking | SSE | Server push only, infrequent |
| Design Video Calls (Zoom/Meet) | WebRTC | P2P media; server only coordinates handshake |

---

## 🔗 Full notes

`DeepDive/07-real-time-updates.md` — protocol internals, pub/sub architecture at scale, full failure mode Q&A
