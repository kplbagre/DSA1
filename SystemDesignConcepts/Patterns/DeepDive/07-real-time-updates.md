# Pattern Deep Dive: Real-time Updates

> **Read this when:** You need to understand how to push data from server to client as events happen — chat messages, live scores, stock prices, order tracking, collaborative editing — without the client polling.
> **Pre-interview refresh:** Use `Reference/07-real-time-updates.md` (5 min).

---

## 🎯 What Problem Does This Pattern Solve?

The client needs to see data change without refreshing the page. Classic HTTP is request-response: the client must ask, the server answers. But for real-time use cases, **the server needs to initiate the communication** — push a message to the client the moment something changes.

Symptoms:
- Users complain that they have to refresh to see new messages / bids / scores
- Your polling implementation is hammering the database every second per connected user
- Users miss time-sensitive updates (stock price, auction bid, ride ETA)
- Chat app shows old messages until manual refresh

The naive fix (polling every second) doesn't scale: 1M users × 1 request/sec = 1M requests/sec to your backend — just to say "nothing new yet." That's enormous load for zero informational value.

---

## 💡 Core Insight

**HTTP is pull. Real-time needs push.** The solution is to maintain a **persistent connection** between client and server so the server can send data whenever it has something new, without waiting for the client to ask.

Three technologies, one spectrum from simplest to most powerful:
- **Short/Long Polling** — still pull, but smarter about timing
- **Server-Sent Events (SSE)** — one-way push, HTTP-based
- **WebSockets** — full duplex, two-way persistent connection

The right choice depends on whether the client also sends data (if yes: WebSockets; if no: SSE or polling).

> **KEY INSIGHT:** "Don't make the client ask repeatedly. Hold the connection open and push when ready. Match the technology to the communication direction."

🧠 **Two-hop mental model:** Real-time delivery always has two distinct hops:
- **Hop 1:** Event source (Order Service, Chat Service) → Gateway (via **pub/sub** — a pattern where producers *publish* events to named *channels* and every server currently *subscribed* to that channel receives the event instantly, with no polling; Redis and Kafka are the two common implementations). Stateless fan-out.
- **Hop 2:** Gateway → client connection (persistent WebSocket or SSE stream). Stateful push.

These hops use different technologies and scale differently. Backend services remain stateless (they just publish). Gateway servers are stateful (they hold connections). Pub/sub is the bridge that decouples them — backend doesn't know which gateway holds which client.

---

## 🗂️ The 4 Strategies (Simple → Full Real-time)

---

### Strategy 1 — Short Polling

🧠 **Mental model:** Email client checking for new mail every 30 seconds. Simple, wastes requests when nothing changed, acceptable at low user count.

Client sends a request every N seconds. Server responds immediately (even if nothing changed).

**When to use:**
- Near-real-time is acceptable (5–30 second staleness OK)
- Very simple to implement (standard HTTP, no special infrastructure)
- Low number of connected clients (< 10K)
- Data changes infrequently (dashboard with 30s refresh)

**When NOT to use:**
- Large number of concurrent users (multiplies DB load linearly)
- Sub-second freshness required
- Cost is a concern (each poll is a full HTTP round-trip)

**How it works:**

**Steps in plain English:**
1. **Poll** — Client sends `GET /updates?since=T` on a fixed interval (e.g., every 5s).
2. **Query** — Server queries DB for anything newer than timestamp T.
3. **Respond** — Server returns data if found, or empty response if nothing changed.
4. **Wait** — Client waits N seconds, then repeats from step 1 with updated timestamp.

```
Client         Server          DB
  │──GET /updates?since=T──▶│            │
  │                          │──SELECT──▶│
  │                          │◀──results─│
  │◀──200 OK (data or empty)─│            │
  │   wait N seconds          │            │
  │──GET /updates?since=T'──▶│            │ (repeat forever)

Problem: If nothing changed, every poll is wasted work.
At 1M users polling every 1s = 1M DB queries/sec. Most return empty.
```

---

### Strategy 2 — Long Polling

🧠 **Mental model:** Old Facebook Chat (pre-2010). Held HTTP connections open; server responded when a message arrived. Fell back to regular polling when proxies timed out. Works everywhere, even behind old corporate firewalls.

Client sends a request. Server holds the connection open until data is available (or timeout), then responds. Client immediately reconnects.

**When to use:**
- Need lower latency than short polling but can't use WebSockets
- Data changes are relatively infrequent (not sub-second)
- Environments where WebSockets are blocked (some corporate firewalls, older proxies)

**When NOT to use:**
- Very high message frequency (server would respond immediately every time — degrades to short polling)
- Millions of concurrent connections (each holds an HTTP connection open — threads/memory expensive)

**How it works:**

**Steps in plain English:**
1. **Connect** — Client sends GET /updates.
2. **Hold** — Server holds the connection open (no response yet).
3. **Event arrives** — When data is ready, server writes response and closes connection.
4. **Reconnect** — Client processes response, immediately opens a new long-poll connection.
5. **Timeout** — If no event within N seconds (e.g., 30s), server sends empty response. Client reconnects.

```
Client         Server          Event Source
  │──GET /updates──────────▶│              │
  │   (server holds open)    │              │
  │                          │◀── event! ───│
  │◀─── 200 OK (event data) ─│              │
  │  (immediately reconnects) │              │
  │──GET /updates──────────▶│              │ (repeat)

Latency: event arrives at server → client sees it in < 100ms
Connection count: 1 connection per user (but held open = thread/memory cost)
```

---

### Strategy 3 — Server-Sent Events (SSE)

🧠 **Mental model:** Twitter's live retweet counter — you see the count increment in real-time without refreshing. Server streams count updates to your browser over a single HTTP connection. Client never sends anything back.

Server holds a persistent HTTP connection open and **streams** events to the client whenever they occur. Client can't send data back over the same connection.

**When to use:**
- Server-to-client push only (notifications, live feeds, order status, dashboards)
- Browser clients (SSE is natively supported in all modern browsers via `EventSource` API)
- HTTP/2 environments (SSE over HTTP/2 is highly efficient — multiplexed streams)
- Simpler than WebSockets when you don't need client-to-server messages

**When NOT to use:**
- Client also sends messages in real-time (chat, collaborative editing) — use WebSockets instead
- Non-browser clients (mobile apps handle WebSockets more naturally than SSE)
- Behind proxies that buffer responses (SSE requires streaming; some old proxies buffer until close)

**How it works:**

**Steps in plain English:**
1. **Connect** — Client opens `EventSource` connection to `/events` endpoint.
2. **Stream open** — Server sends HTTP 200 with `Content-Type: text/event-stream`. Connection stays open.
3. **Push events** — Server writes events in SSE format whenever they occur. Each event is flushed immediately.
4. **Client handles** — Browser `EventSource` fires an event callback for each received event.
5. **Reconnect** — If connection drops, browser automatically reconnects with `Last-Event-ID` header so server can replay missed events.

```
Client (Browser)             Server
  │──GET /events ──────────▶│
  │◀── 200 text/event-stream─│
  │                          │  (connection stays open)
  │◀── data: {"msg":"hello"} │  (server pushes when ready)
  │◀── data: {"price":142.5} │
  │◀── data: {"status":"out"}│
  │                          │
  │  (connection drops)       │
  │──GET /events ──────────▶│  (auto-reconnect with Last-Event-ID)
  │   Last-Event-ID: 42      │

SSE wire format:
  id: 42\n
  event: price-update\n
  data: {"symbol":"AAPL","price":142.5}\n
  \n
```

---

### Strategy 4 — WebSockets

🧠 **Mental model:** Slack desktop app — you send and receive messages on the same persistent connection. Your typing indicator goes up; others' messages come down. Both directions, same connection, no new HTTP requests.

Full-duplex (two-way) persistent TCP connection. Either side can send messages at any time. One connection per client, maintained for the session duration.

**When to use:**
- Two-way real-time communication (chat, collaborative editing, multiplayer gaming)
- Very high message frequency (financial trading, live auction bidding)
- Server needs to push AND client sends messages over the same connection
- Sub-100ms latency required

**When NOT to use:**
- Server-only push (use SSE — simpler, HTTP-native, works through more proxies)
- Infrequent updates (holding a WebSocket open for a dashboard that updates once a minute is overhead for nothing)
- Simple mobile push notifications (use APNs/FCM — purpose-built, battery-efficient)

**How it works:**

**Steps in plain English:**
1. **Handshake** — Client sends HTTP Upgrade request. Server responds 101 Switching Protocols. TCP connection is now a WebSocket.
2. **Bidirectional** — Either side sends frames at any time. No request-response; just message frames.
3. **Server push** — Server sends data whenever an event occurs (no client request needed).
4. **Client send** — Client sends messages (chat message, cursor position, bid) directly over the same connection.
5. **Close** — Either side sends a Close frame. Connection terminates gracefully.

```
                    HTTP Upgrade (WebSocket handshake)
Client ──────────────────────────────────────────────▶ Server
       ◀───────────────────── 101 Switching Protocols ──
       (TCP connection is now WebSocket — no more HTTP overhead)

       ◀─── {"type":"message","from":"Alice","text":"Hi"} ──  (server push)
       ──── {"type":"message","text":"Hey!"} ─────────────▶   (client send)
       ◀─── {"type":"typing","user":"Alice"} ────────────────  (server push)
       ──── {"type":"read","messageId":42} ─────────────────▶  (client send)

Both directions, any time, no polling, no new HTTP requests.
```

---

## 🧭 Decision Sequence

```
START: Client needs to see server-side changes without refreshing

Step 1 ── Does the client ALSO send data in real-time?
          Yes (chat, collaborative editing, gaming, live auction)
                → WebSockets. Two-way is the requirement.
          No (notifications, order status, live feed, dashboard)
                → Go to Step 2.

Step 2 ── Is the client a browser?
          Yes → Server-Sent Events (SSE). Native browser support, HTTP-native.
          No (mobile, desktop app) → WebSockets (more universal library support).

Step 3 ── Can you use SSE/WebSockets at all?
          Network/proxy blocks persistent connections?
                → Long Polling. Slower but works everywhere.
          Freshness requirement is > 30 seconds?
                → Short Polling. Simplest possible implementation.

Step 4 ── At what scale?
          < 10K concurrent connections → Any of the above on a single server.
          > 10K → Need connection management layer (see Architecture section).
          > 1M → Dedicated WebSocket gateway cluster + pub/sub backend.
```

---

## 🎨 Visual — Full Real-time Architecture at Scale

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

### Q: "Why does WebSocket load balancing need special treatment?"

> HTTP load balancers work at L7 (HTTP-aware) — they parse requests, route by path/header, and can terminate SSL. For WebSocket initial handshake (HTTP Upgrade), L7 LBs work fine. The problem is **sticky sessions**: after upgrade, all subsequent frames must go to the same backend server (it holds the connection). L7 LBs support sticky sessions via cookie or IP hash, but it adds config complexity. L4 LBs (TCP-level) are transparent — they forward the TCP stream without parsing; the connection naturally stays on the same backend. Production recommendation: AWS ALB (L7) handles WebSocket sticky sessions well for most scales. Switch to NLB (L4) if you have > 100K connections per LB instance.

---

### Q: "How do you route a message to a user when you don't know which gateway server holds their connection?"

> Pub/sub routing — the gateway server subscribes to a per-user channel on connect (`SUBSCRIBE user:123`). When any backend service has a message for user 123, it publishes to `user:123`. The gateway server holding that user's WebSocket is subscribed → pushes the message. On reconnect to a different gateway server, that server subscribes to the same channel. No routing table, no sticky sessions required. For billion-user scale, consistent hashing helps: hash(user_id) determines the preferred gateway cluster, reducing channel subscriptions per gateway and cross-cluster pub/sub traffic.

---

### Q: "You keep mentioning Redis Pub/Sub — what is it and how does it actually work?"

> Redis Pub/Sub is a lightweight, in-memory broadcast mechanism built into Redis — not a separate tool, just three commands:
>
> - `SUBSCRIBE user:123` — a gateway server runs this when a client connects; Redis registers it as a subscriber to that channel.
> - `PUBLISH user:123 '{"event":"order_shipped"}'` — a backend service runs this when state changes; Redis immediately forwards the payload to every server currently subscribed to that channel.
> - `PSUBSCRIBE room:*` — pattern subscribe; catches `room:42`, `room:general`, any channel matching the glob.
>
> **Under the hood:** Redis holds an in-memory map of `channel → [list of subscriber connections]`. `PUBLISH` is O(N) where N = number of subscribers on that channel. For a user with one WebSocket connection, N = 1. For a chat room with 500 connected users spread across 5 gateway servers, N = 5 (one subscription per gateway server, not per client). Delivery latency: sub-millisecond.
>
> **Fire-and-forget — the critical limitation:** Redis Pub/Sub has **no message persistence and no acknowledgment**. If a gateway server restarts during a `PUBLISH`, it misses the message. This is why the architecture has two layers: Redis Pub/Sub handles the "push right now" fast path; the DB-backed event log handles the "catch-up after reconnect" path (SSE `Last-Event-ID` or client sequence numbers).
>
> **Why Redis Pub/Sub here instead of Kafka?**
> - Redis latency: < 1ms (in-memory, synchronous delivery to subscriber list).
> - Kafka latency: 5–50ms (writes to disk partition, consumer polls on interval).
> - WebSocket gateway routing needs the first property. If a push is delayed 50ms it's a UX artifact; if it's missed entirely, the reconnect catches it up from the event log.
> - Use Kafka when: you need replay (reprocess events from 7 days ago), multiple independent consumer groups (analytics + main DB + search indexer all consuming the same event), or guaranteed delivery. Use Redis Pub/Sub when: you need sub-millisecond broadcast to currently-connected servers and durability is handled elsewhere.

---

### Q: "When would you choose WebRTC over WebSockets?"

> WebRTC is for peer-to-peer media (video/audio) where server-relay would be too expensive or slow. WebSockets: all data routes through your server (star topology, server pays bandwidth). WebRTC: client-to-client direct (server only helps with initial connection setup via ICE/STUN/TURN). Use WebRTC when: video/audio quality matters (routing HD video through a server is cost-prohibitive at scale), or when sub-50ms latency is required (real-time gaming, video calls). Use WebSockets when: data must pass through the server for auth/ordering/broadcast (auction bids, live scores), or you need reliable ordered delivery (WebRTC uses UDP by design, which is lossy and unordered for media).

---

## ⚠️ Anti-patterns

- **Polling at high frequency as a "simple" substitute for WebSockets.** Polling every 1 second for 100K users = 100K requests/sec — just to say "nothing changed" 99% of the time. The DB load alone is prohibitive. If you need < 5 second freshness for many concurrent users, SSE or WebSockets are non-negotiable. Polling is only "simple" at low scale; at any reasonable scale it's the most expensive approach per bit of information delivered.

- **Storing connection state in the application database.** WebSocket connection IDs, session tokens, and subscription lists should NOT live in Postgres. They're ephemeral — a connection lasts minutes or hours; a DB row lasts forever. Store connection state in Redis (fast, TTL-based expiry, pub/sub native). If the WS server crashes, Redis connection state auto-expires. Putting connection management in the DB creates a write-heavy hot table that degrades with scale.

- **Using WebSockets when SSE is sufficient.** WebSockets require sticky sessions or a pub/sub routing layer. SSE is plain HTTP — it works through standard load balancers, CDNs, and HTTP/2 multiplexing without configuration. If the client never sends data back over the real-time channel, adding WebSockets introduces operational complexity with no benefit. Ask: "Does the client send data over this connection?" If no — use SSE.

---

## 🗺️ Problems Map

| Interview Problem | Why Real-time Updates Applies | Strategy |
|---|---|---|
| Design Chat App | Messages must appear instantly without refresh | WebSockets (bidirectional) |
| Design Live Sports Score | Score updates pushed to viewers in real-time | SSE (server push only) |
| Design Stock Price Feed | Price ticks pushed to clients continuously | SSE or WebSockets (high frequency) |
| Design Uber Driver Tracking | Driver location updates every 3s to rider | WebSockets (client also sends location) |
| Design Notification System | Push alerts to users on events | SSE or WebSockets + APNs/FCM for mobile |
| Design Collaborative Doc Editor | Cursor positions, edits shared in real-time | WebSockets (fully bidirectional) |
| Design Live Auction | Bid updates pushed to all participants | WebSockets (client bids + server updates) |
| Design Order Tracking | Order status changes pushed to customer | SSE (server push only, infrequent) |
| Design Video Calls (Zoom/Meet) | Real-time video/audio between users | WebRTC (P2P media; server only coordinates) |

---

## 🔗 Concept Notes — Go Here for "How It Works"

- **WebSocket protocol internals** → `../../Production-Grade/System-Design-Patterns/26-websocket-real-time-communication.md`
- **Message queues for pub/sub fan-out** → `../../Core-Architecture/Service-Communication/19-message-queues-kafka-rabbitmq.md`
- **Push notification systems (APNs, FCM)** → `../../Core-Architecture/Service-Communication/46-push-notifications-fanout.md`

---

## 🔄 Changelog

| Date | Change |
|---|---|
| July 2026 | Note created. Batch 2 of 8 remaining patterns. |
| July 2026 | Added two-hop mental model to Core Insight. Added 🧠 mental model anchors per strategy. Added L4 vs L7 LB Q&A, pub/sub routing + consistent hashing Q&A, WebRTC Q&A. Added video call row to Problems Map. |
