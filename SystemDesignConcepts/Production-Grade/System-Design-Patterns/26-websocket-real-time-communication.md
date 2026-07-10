# WebSocket — Real-Time Bidirectional Communication

> WebSocket is a protocol that upgrades an HTTP connection to a persistent, bidirectional channel. Unlike HTTP (request-response), WebSocket lets client and server both send messages anytime. At SDE 3: you must know when WebSocket is appropriate (chat, notifications, live updates), how it differs from polling/SSE, and how to scale it across multiple servers.

---

## 🎯 Why This Matters

You're building a real-time chat app. Option 1: client polls server every 100ms ("any new messages?"). At 1M users, that's 10M requests/sec — wasteful. Option 2: server pushes messages to client via WebSocket — client receives instantly, no polling overhead. WebSocket is the standard for real-time features (chat, notifications, live dashboards, collaborative editing). In interviews, candidates often ask "why not just use REST?" — you'll explain the bandwidth and latency trade-offs.

---

## 🧠 The Mental Model

Imagine a theater box office. Without WebSocket (polling):
- You call the box office every minute: "Any tickets for the 7pm show?"
- Box office answers: "No, call back later."
- You hang up and call back in a minute.
- Repeat 1000 times. Wasteful calls for no new info.

With WebSocket:
- You get a direct phone line to the box office.
- You stay on the line. Box office says "Tickets just became available!" immediately.
- You hang up only when you're done.
- One connection, instant updates.

**The key insight:** WebSocket trades "simple request-response" for "persistent connection with bidirectional messaging." Reduces latency and bandwidth but increases server-side connection management complexity.

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|------|----------------------|---------|
| **WebSocket Handshake** | initial HTTP request with `Upgrade: websocket` header; server agrees → connection promoted to persistent TCP socket | `GET /chat HTTP/1.1` + `Upgrade: websocket` → `101 Switching Protocols` |
| **Bidirectional** | both client and server can send messages at any time without waiting for a request | server pushes new chat message to client; client sends typing indicator to server |
| **Frame** | WebSocket data unit; much smaller overhead than HTTP (2 bytes vs 200+ bytes of HTTP headers) | text frame: `0x81 0x05 Hello` — 7 bytes total for "Hello" |
| **Long Polling** | client sends request, server holds it open until data is available, then responds; repeat | client: `GET /messages` → server holds 30s → new message → respond → client repeats |
| **SSE (Server-Sent Events)** | one-way server-to-client stream over HTTP; simpler than WebSocket; no client-to-server channel | live score updates, stock ticker — server pushes only, client never sends |
| **Sticky Session (WebSocket)** | load balancer must route all requests from a client to the same server node that holds their connection | client connected to WS Server #1 — all messages must route to #1, not #2 or #3 |
| **Redis Pub/Sub Fan-out** | all WebSocket servers subscribe to a Redis channel; message published once reaches all servers which relay to their clients | User A (on Server #1) sends message → Redis → Server #2 → User B (connected there) |
| **Heartbeat / Ping-Pong** | periodic ping from one side to confirm connection is alive; if no pong → close and clean up | server sends `PING` every 30s; client responds `PONG`; no response → evict connection |

---

## 🎨 Visual — WebSocket in System Architecture

### Full System Topology — Where WebSocket Sits

```
┌────────────────────────────────┐
│   CLIENT (Browser)             │
│   ┌──────────────────────────┐ │
│   │ WebSocket Connection     │ │
│   │ (persistent TCP socket)  │ │
│   │ ↔ bidirectional          │ │
│   └──────────────────────────┘ │
└────────────────────────────────┘
    ↕ (WebSocket frames, not HTTP)
┌────────────────────────────────────────────────────────────────┐
│ WebSocket Server #1                                            │
│ ┌──────────────────────────────────────────────────────────┐  │
│ │ [Connection Manager] [Message Handler] [Subscription]     │  │
│ │ Holds open connections from ~10K clients                  │  │
│ │ When client sends message → publish to Kafka topic        │  │
│ │ When message arrives from Kafka → send to all subscribers │  │
│ └──────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────┘
    ↕ (internal: subscribe to Kafka)
┌────────────────────────────────────────────────────────────────┐
│ MESSAGE BROKER (Kafka / Redis Pub-Sub)                         │
│ Topic: "chat:room:123"                                         │
│ All WebSocket servers subscribe                                │
└────────────────────────────────────────────────────────────────┘
    ↑
All WebSocket servers listen to this topic
WebSocket Server #2, #3, ... also connected
When any client sends message to room:123 → Kafka → all servers → broadcast to their clients

KEY INVARIANT:
   WebSocket connection is PERSISTENT (not request-response).
   One connection per client can handle MANY messages (bidirectional).
   To broadcast across multiple servers, use message broker (Kafka/Redis).
   Without broker, each server can only see messages from ITS OWN clients.
```

### Component Detail — WebSocket Protocol & Connection Lifecycle

```
CLIENT                                        SERVER
┌──────────────────────────────────────────────────────────┐
│ 1. HTTP UPGRADE HANDSHAKE (HTTP 101)                    │
│                                                          │
│ Client sends:                                           │
│ GET /chat HTTP/1.1                                      │
│ Upgrade: websocket                                      │
│ Connection: Upgrade                                     │
│ Sec-WebSocket-Key: abc123...                           │
│ Sec-WebSocket-Version: 13                              │
│                         ────────────────────────────► │
│                                                          │
│ Server responds:                                        │
│ HTTP/1.1 101 Switching Protocols                        │
│ Upgrade: websocket                                      │
│ Connection: Upgrade                                     │
│ Sec-WebSocket-Accept: xyz789...                        │
│                    ◄──────────────────────────────     │
│ (HTTP is now UPGRADED to WebSocket)                    │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│ 2. CONNECTION OPEN — Bidirectional Messaging             │
│                                                          │
│ Connection State: OPEN                                   │
│ Client: "Hello room" (WebSocket FRAME)                  │
│                         ────────────────────────────► │
│                         ◄───── "Hello back" (FRAME)     │
│ Server can send anytime, client can send anytime        │
│ No polling, no wait                                      │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│ 3. HEARTBEAT (Ping / Pong)                               │
│                                                          │
│ Server periodically (every 30s):                         │
│ Sends PING frame                                        │
│                         ────────────────────────────► │
│ Client responds:                                        │
│ Sends PONG frame                                        │
│                         ◄───────── PONG                  │
│ If no PONG after timeout, client is dead — close conn │
│ Keeps connection alive through proxies/firewalls        │
└──────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────┐
│ 4. CLOSE HANDSHAKE                                       │
│                                                          │
│ Client: Close code 1000 (normal closure)               │
│                         ────────────────────────────► │
│ Server: Close code 1000 (acknowledge)                   │
│                         ◄──────────────────────────     │
│ Connection closed, TCP socket released                  │
└──────────────────────────────────────────────────────────┘

CONNECTION STATE MACHINE:
┌──────────────┐
│ CONNECTING   │  HTTP upgrade in progress
└──────────────┘
      │
      │ upgrade successful
      ↓
┌──────────────┐
│    OPEN      │  Normal operation, bidirectional messaging
└──────────────┘
      │
      │ client/server initiates close
      ↓
┌──────────────┐
│   CLOSING    │  Close handshake in progress
└──────────────┘
      │
      │ close handshake complete
      ↓
┌──────────────┐
│    CLOSED    │  Connection terminated
└──────────────┘

WebSocket FRAME STRUCTURE (sent over TCP):
┌─────────────┬──────────────┬─────────────┬──────────────────┐
│ FIN (1 bit) │ Opcode (4b)  │ Mask (1b)   │ Payload Length   │
├─────────────┼──────────────┼─────────────┼──────────────────┤
│ 1           │ 0x1 (Text)   │ 1 (masked)  │ 126 (ext. length)│
├─────────────┼──────────────┼─────────────┼──────────────────┤
│ (extended   │              │ Masking Key │ Payload Data     │
│ payload     │              │ (4 bytes)   │ "Hello World"    │
│ length)     │              │             │                  │
└─────────────┴──────────────┴─────────────┴──────────────────┘

Opcodes:
  0x0 = continuation frame (multi-frame message)
  0x1 = text frame
  0x2 = binary frame
  0x8 = close frame
  0x9 = ping frame
  0xA = pong frame

KEY INVARIANT:
   WebSocket is NOT HTTP (no request-response).
   Is TCP-based, persistent, full-duplex.
   Frames are small (header ~2 bytes for text message).
   No overhead of HTTP headers for each message.
```

---

## ⚙️ How It Actually Works

**Steps in plain English:**

1. **Client initiates HTTP upgrade** — sends HTTP GET with `Upgrade: websocket` header.
2. **Server accepts upgrade** — sends HTTP 101 Switching Protocols.
3. **Connection becomes WebSocket** — HTTP is replaced; now using WebSocket protocol.
4. **Client and server exchange frames** — either can send anytime.
5. **Server maintains active connection** — holds socket open for this client.
6. **For broadcasts across servers** — server publishes to message broker; all servers subscribe and forward to their clients.
7. **Heartbeat mechanism** — server sends PING periodically; client responds with PONG.
8. **Graceful close** — either side initiates close; handshake happens; connection terminated.

```java
// WebSocket Server (Spring WebSocket or custom Netty implementation)

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Step 1-3 — Register WebSocket endpoint
        registry.addHandler(chatWebSocketHandler(), "/chat/{roomId}")
            .setAllowedOrigins("*");
    }

    @Bean
    public WebSocketHandler chatWebSocketHandler() {
        return new ChatWebSocketHandler();
    }
}

// Step 2-4 — WebSocket Handler manages connections and messages
@Component
public class ChatWebSocketHandler implements WebSocketHandler {
    private static final Logger logger = LoggerFactory.getLogger(ChatWebSocketHandler.class);
    
    // Step 5 — Maintain open connections by room
    private final Map<String, Set<WebSocketSession>> roomConnections = new ConcurrentHashMap<>();
    private final KafkaTemplate<String, ChatMessage> kafkaTemplate;

    // Step 1-3 — Handle new connection
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String roomId = extractRoomId(session);
        String userId = extractUserId(session);
        
        // Step 5 — Add session to room's connection set
        roomConnections.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet())
            .add(session);
        
        logger.info("User {} joined room {}", userId, roomId);
        
        // Notify room: "user joined"
        broadcastToRoom(roomId, ChatMessage.builder()
            .type("USER_JOINED")
            .userId(userId)
            .roomId(roomId)
            .build());
    }

    // Step 4 — Handle incoming message from client
    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
        String roomId = extractRoomId(session);
        String userId = extractUserId(session);
        String payload = message.getPayload().toString();
        
        // Step 6 — Publish to Kafka (for broadcast across servers)
        ChatMessage chatMsg = ChatMessage.builder()
            .type("MESSAGE")
            .userId(userId)
            .roomId(roomId)
            .content(payload)
            .timestamp(System.currentTimeMillis())
            .build();
        
        kafkaTemplate.send("chat:messages", roomId, chatMsg);
        
        logger.debug("Message from {} in room {}: {}", userId, roomId, payload);
    }

    // Step 4 — Handle connection close
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String roomId = extractRoomId(session);
        String userId = extractUserId(session);
        
        // Step 5 — Remove session from room
        roomConnections.computeIfPresent(roomId, (k, sessions) -> {
            sessions.remove(session);
            return sessions.isEmpty() ? null : sessions;
        });
        
        logger.info("User {} left room {}", userId, roomId);
        
        // Notify room: "user left"
        broadcastToRoom(roomId, ChatMessage.builder()
            .type("USER_LEFT")
            .userId(userId)
            .roomId(roomId)
            .build());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        logger.error("WebSocket error", exception);
        session.close();
    }

    // Step 5 — Broadcast to all clients in room
    private void broadcastToRoom(String roomId, ChatMessage message) {
        Set<WebSocketSession> sessions = roomConnections.get(roomId);
        if (sessions != null) {
            for (WebSocketSession session : sessions) {
                try {
                    // Step 4 — Server sends message to client via WebSocket
                    session.sendMessage(new TextMessage(message.toJson()));
                } catch (Exception e) {
                    logger.error("Failed to send message to session {}", session.getId(), e);
                }
            }
        }
    }

    private String extractRoomId(WebSocketSession session) {
        return (String) session.getAttributes().get("roomId");
    }

    private String extractUserId(WebSocketSession session) {
        return (String) session.getAttributes().get("userId");
    }
}

// Step 6 — Kafka listener (consume messages published to Kafka)
@Component
public class KafkaMessageConsumer {
    private final ChatWebSocketHandler wsHandler;

    @KafkaListener(topics = "chat:messages", groupId = "websocket-servers")
    public void consumeMessage(ChatMessage message) {
        // Step 6 — Message from Kafka (could be from different server)
        // Broadcast to all local clients in this room
        wsHandler.broadcastToRoom(message.getRoomId(), message);
    }
}

// Client side (JavaScript)
const socket = new WebSocket('ws://localhost:8080/chat/room123');

// Step 1-3 — Connection opened
socket.addEventListener('open', function(event) {
    console.log('Connected to WebSocket');
    // Step 4 — Client sends first message
    socket.send(JSON.stringify({
        type: 'MESSAGE',
        content: 'Hello room!'
    }));
});

// Step 4 — Receive message from server
socket.addEventListener('message', function(event) {
    const message = JSON.parse(event.data);
    console.log('Received:', message.content);
    updateChatUI(message);
});

// Step 7 — Heartbeat mechanism (browser closes idle connections after ~60s)
// Server proactively sends PING
// Browser automatically responds PONG

// Step 8 — Close connection
socket.addEventListener('close', function(event) {
    console.log('Disconnected from WebSocket');
});

socket.addEventListener('error', function(event) {
    console.error('WebSocket error:', event);
});
```

### What is the WebSocket upgrade handshake, and why does it fit here?

> **⚠️ Sticky Sessions — The Most Commonly Missed WebSocket Interview Topic**
>
> WebSocket connections are **stateful**: each open socket is bound to a specific server instance. This breaks standard round-robin load balancing. Without sticky sessions, a client's HTTP request after connect might route to a different server that has no open socket for that client → immediate connection error. Configure your L7 load balancer (AWS ALB) to use duration-based session affinity cookies so every request from Client A always hits Server A. Only after sticky sessions are configured should you layer Kafka on top for cross-server message fan-out.

The upgrade handshake is the **HTTP → WebSocket protocol switch**. Client sends HTTP GET with `Upgrade: websocket` header; server responds with HTTP 101 Switching Protocols. After this, the TCP connection is no longer HTTP — it becomes a bidirectional message channel. In an interview, if asked: *"WebSocket starts as HTTP (so it can traverse proxies that don't understand WebSocket), then upgrades to a persistent TCP connection using the HTTP 101 response code. This is why WebSocket works even in restrictive networks — it looks like HTTP initially."*

### What is Sec-WebSocket-Key, and why does it fit here?

Sec-WebSocket-Key is a **security header** that prevents WebSocket from being confused with HTTP Upgrade for other protocols. Server uses this key to calculate Sec-WebSocket-Accept (proof that server understands WebSocket). In an interview, if asked: *"Sec-WebSocket-Key is a base64-encoded random value sent by the client. Server hashes it with a magic string (RFC 6455) and sends back the hash. This prevents misconfigured proxies from speaking WebSocket when they shouldn't, and prevents cache poisoning."*

---

## 🧠 WebSocket at Scale: Sticky Sessions + Broker Pattern

WebSocket connections are **stateful** — each open socket is bound to a specific server instance. Standard round-robin load balancing breaks WebSocket. Here's the full solution:

### Step 1: Sticky sessions (prerequisite)

Without sticky sessions, HTTP upgrade requests and subsequent frames can route to different servers:

```
WITHOUT sticky sessions:
  Client A: WS upgrade → LB → Server 1 (socket opened)
  Client A: sends frame → LB round-robins → Server 2 (no socket!) → Error

WITH sticky sessions (ALB duration cookie):
  Client A: WS upgrade → LB → Server 1 (cookie: lb=server1)
  Client A: sends frame → LB reads cookie → Server 1 (socket found) ✅
  Client A: reconnects  → LB reads cookie → Server 1 ✅
```

**AWS ALB configuration:** Enable "Stickiness" with duration-based cookies in the target group settings. ALB sets `AWSALB` cookie; all future requests from that browser hit the same target.

**Nginx:** Use `ip_hash` directive in the upstream block to pin by client IP.

**Limitation:** Sticky sessions alone don't survive server crashes. If Server 1 dies, Client A reconnects and gets a new server — all in-memory subscription state is lost (which rooms they joined, etc.). Clients must re-subscribe on reconnect.

### Step 2: Message broker for cross-server fan-out

Even with sticky sessions, clients on Server 1 need to receive messages from clients on Server 2:

```
┌──────────────────────────────────────────────────────────────┐
│  Client A (Server 1)  ──sends message──►  Server 1           │
│                                           ↓ publishes         │
│                                        Kafka: "room:123"      │
│                                           ↓ all servers sub   │
│  Client B (Server 2)  ◄──forwards──   Server 2               │
└──────────────────────────────────────────────────────────────┘
```

Both sticky sessions AND a message broker are required. Neither alone is sufficient.

---

### Step 3: How does a server know WHICH pod holds the recipient's connection?

This is the question the room-broadcast pattern above dodges. In room-based fan-out, every server gets every message and self-filters ("do I have a client in this room?"). That works for group chat but wastes bandwidth at scale, and for **direct messages (DMs)** you need targeted delivery: "User A sends to User B — which specific pod is User B connected to?"

There are three approaches:

#### Approach 1 — Blind Broadcast / Room Fan-out (what the code above does)

All servers subscribe to a shared Kafka topic (e.g., `room:123`). Every message is delivered to **every server**. Each server checks its in-memory connection map — if it has a client in that room, it forwards; otherwise it discards.

**Critical Kafka detail — consumer groups determine who gets what:**

```
WRONG — all pods in ONE consumer group:
  Kafka load-balances → each partition goes to ONE pod
  Pod 1 gets it, Pod 2 and Pod 3 never see it → ❌ not a broadcast

CORRECT — each pod in its OWN consumer group:
  Every consumer group receives every message independently
  Pod 1 (group: ws-pod-1) gets it → has 3 clients → sends to them
  Pod 2 (group: ws-pod-2) gets it → has 0 clients → discards
  Pod 3 (group: ws-pod-3) gets it → has 1 client  → sends to them ✅
```

Each WebSocket pod must be registered as its own consumer group (`groupId = "ws-pod-" + instanceId`) — not a shared group.

- ✅ Simple — no routing logic; each pod self-selects
- ✅ Works for group chat and broadcast notifications
- ✅ Kafka provides durability (messages survive Redis restarts)
- ❌ All N pods receive every message even if N−2 have no clients in that room — wasted deserialization at scale
- **When to use:** Group channels, presence broadcasts, room-based chat

---

#### Approach 2 — User-Location Registry in Redis

On WebSocket connect, the pod writes its own identity into Redis:

```
User B connects to Pod 3:
  Redis SET  user:location:B  →  pod-3   (with TTL 90s, refreshed by heartbeat)
```

When User A wants to DM User B:
1. Pod 1 looks up `user:location:B` in Redis → gets `pod-3`
2. Pod 1 publishes message to a pod-specific inbox topic: `inbox:pod-3`
3. Pod 3 reads its inbox → delivers to User B's socket

```
User A (Pod 1) → Redis lookup → pod-3 → publish to "inbox:pod-3" → Pod 3 → User B
```

- ✅ Targeted — only Pod 3 receives the message, no wasted fan-out
- ✅ Scales cleanly — inbox topic per pod, not per user
- ❌ Extra Redis lookup per message (sub-millisecond, but adds latency)
- ❌ Stale entry risk: if User B crashes ungracefully, `user:location:B` points to a dead pod — heartbeat TTL prevents permanent staleness
- **When to use:** DMs, notifications to a specific user at high scale

---

#### Approach 3 — User-Specific Channel (most common in practice)

On WebSocket connect, the pod **subscribes** to a user-specific Redis Pub/Sub channel:

```
User B connects to Pod 3:
  Pod 3 subscribes to Redis channel:  user:B
```

When User A sends a DM to User B:
1. Pod 1 publishes to Redis channel `user:B`
2. Pod 3 (the only subscriber) receives it → delivers to User B's socket

```
User A (Pod 1) → PUBLISH user:B → Redis → Pod 3 (subscribed) → User B
```

No routing table, no lookup — the correct pod automatically receives because it subscribed when the connection was established.

```
User B disconnects:
  Pod 3 unsubscribes from  user:B
  If User B reconnects to Pod 7:
  Pod 7 subscribes to  user:B
```

**Scaling this approach — what actually limits it:**

The subscription count itself is not the bottleneck. 1M subscriptions ≈ ~200 MB of channel metadata — Redis handles that easily. The real limit is **PUBLISH throughput and how the cluster handles it**:

```
Redis < 7.0 with a cluster:
  PUBLISH user:B on node X → gossip protocol broadcasts to ALL nodes
  Adding nodes does NOT reduce per-node load — it increases it
  Redis Cluster < 7.0 does not help Pub/Sub scale horizontally ❌

Redis 7.0+ sharded Pub/Sub (SSUBSCRIBE / SPUBLISH):
  Channel hashes to a specific slot → only the slot-owning node handles it
  Adding nodes genuinely distributes the publish load ✅
  The subscribing pod must use a cluster-aware client (auto-routes by hash slot)
```

**Scale tiers:**

| Concurrent connections | What to use |
|---|---|
| < 500K | Single Redis node — standard PUBLISH / SUBSCRIBE |
| 500K – 50M | Redis Cluster 7.0+ — SSUBSCRIBE / SPUBLISH (sharded) |
| 50M+ | Consistent hashing to server clusters (WhatsApp/Erlang model) |

- ✅ No lookup — the pub/sub routing is implicit
- ✅ Clean lifecycle: subscribe on connect, unsubscribe on disconnect
- ✅ Used by Slack, Discord, most large chat systems
- ✅ Scales to 50M+ with Redis Cluster 7.0+ sharded Pub/Sub
- **When to use:** DMs and per-user targeted delivery at all scales up to ~50M concurrent

---

#### ⚠️ Why Kafka Is the Wrong Tool for Live DM Routing — and What It Is Right For

This is a common design trap that surfaces in interviews. The reason Kafka fails for live DM delivery is **structural, not a performance problem**:

```
The fundamental mismatch:

  Kafka assigns User B's partition → Pod X
    (decided by the partition coordinator based on partition count and group membership)

  Load balancer assigns User B's WebSocket connection → Pod Y
    (decided independently based on ALB routing at the moment B connected)

  Pod X receives User B's Kafka messages.
  Pod X has no WebSocket connection to User B → message dropped or requires another hop.
```

Making these two align requires a routing table lookup — at which point you have rebuilt Approach 2 (user-location registry) but with Kafka's operational complexity layered on top.

**The rebalance adds cost on top of the mismatch:** When any pod restarts or joins, Kafka triggers a consumer group rebalance. Modern Kafka (KIP-429 incremental cooperative rebalancing, KIP-345 static membership) reduces the pause duration, but rebalance still reshuffles partition assignments — while WebSocket connections stay in place. The mismatch deepens every time a pod bounces.

**Kafka's correct role in this architecture:**

| Role | Right tool | Wrong tool |
|---|---|---|
| Live delivery to a specific connected socket | ✅ Redis Pub/Sub (Approach 3) — subscription is tied to socket lifecycle | ❌ Kafka — partition assignment is independent of socket placement |
| Offline message storage (user not connected) | ✅ Kafka — log with configurable retention; user replays on reconnect | ❌ Redis Pub/Sub — fire-and-forget, no log |
| Group channel fan-out with durability | ✅ Kafka — Approach 1 with per-pod consumer groups | — |
| Broadcast to all users | ✅ Kafka — single topic, all pods subscribe | — |

**Interview phrasing if asked "why not Kafka for DMs?":**
*"Kafka partition assignment and WebSocket connection placement are made by two independent systems with no shared state. A message for User B would land on the pod that owns User B's partition — not necessarily the pod holding User B's socket. Redis Pub/Sub avoids this entirely: the subscription is created when the socket opens and destroyed when it closes, so they're always co-located. Kafka belongs in this system for durability — persisting messages for offline users who replay on reconnect — not for live delivery."*

---

#### Summary — which approach to reach for

| Scenario | Approach |
|---|---|
| Group chat / room broadcast | Approach 1 — Kafka (each pod in its OWN consumer group for fan-out) |
| DM at < 500K concurrent | Approach 3 — user-specific Redis channel (single Redis node) |
| DM at 500K – 50M concurrent | Approach 3 — Redis Cluster 7.0+ with SSUBSCRIBE / SPUBLISH |
| DM at 50M+ concurrent | Consistent hashing to server clusters (WhatsApp/Erlang model) |
| Notification to all users | Approach 1 — single Kafka topic, all pods subscribe |

**Interview answer if asked "how does Pod 1 know User B is on Pod 3?":**
*"In the room-broadcast pattern it doesn't need to — every pod subscribes to the room topic and self-filters. For direct messages I use a user-specific Redis channel: when User B connects, that pod subscribes to `user:B`. The sender publishes to `user:B` and only the correct pod receives it. No routing table, no lookup."*

**If the interviewer pushes "what about scale — 50 million connections?":**
*"Up to ~500K concurrent, a single Redis node handles it — subscription metadata is small (~200 MB). From 500K to ~50M, Redis 7.0+ sharded Pub/Sub: SSUBSCRIBE/SPUBLISH hash the channel to a slot, so publish load distributes across cluster nodes. Above 50M — WhatsApp territory — you switch to consistent hashing on the server cluster: a user's ID maps deterministically to a server group, and routing is done at the LB level without a message broker lookup."*

**If asked "why not use Kafka partitioned by userId for DMs?":**
*"Kafka assigns partitions to pods independently from how the load balancer placed WebSocket connections — two separate systems with no shared state. User B's Kafka partition lands on Pod X; User B's socket is on Pod Y. Redis Pub/Sub avoids this: the subscription is created when the socket opens and destroyed on disconnect, so they're always co-located. Kafka belongs in this system for durability — storing messages for offline users to replay on reconnect — not for live delivery routing."*

---

## 🧠 SSE vs WebSocket — Choosing the Right Protocol

A table interviewers love to probe. The decision axis is: does the **client** also need to send data?

| Dimension | WebSocket | Server-Sent Events (SSE) |
|---|---|---|
| **Direction** | Bidirectional (client AND server push) | Server → client only |
| **Protocol** | WebSocket protocol (WS/WSS, HTTP upgrade) | Plain HTTP/1.1 chunked response |
| **Load balancing** | ⚠️ Requires sticky sessions | ✅ Standard HTTP — any LB works |
| **Reconnection** | Manual (write your own reconnect logic) | ✅ Built-in (browser auto-reconnects) |
| **Proxy / firewall** | Some proxies block WS upgrade | ✅ Works through all HTTP proxies |
| **Overhead per message** | ~2 bytes framing (very low) | ~6 bytes `data: ` prefix (slightly higher) |
| **Use cases** | Chat, collaborative editing, gaming | Stock tickers, notifications, log tail, live feeds |

**Decision rule:**
- Client needs to **send** data too → WebSocket
- Only server pushes, client only receives → **SSE is simpler** (no sticky sessions, auto-reconnect, works through all proxies)

**Interview phrasing:** *"For our notification bell — server-to-client only — SSE is the better choice. No sticky sessions needed, browser handles reconnection automatically, and it works through all corporate proxies. WebSocket would add operational complexity (sticky sessions, reconnect logic) for a feature that's only one-directional. We use WebSocket only for chat where clients also send messages."*

---

## 🏢 Real World — Where Companies Use This

- **Slack (WebSocket for real-time chat):** Every message typed in Slack goes through WebSocket. User A types "hello" → server publishes to Kafka topic "channel:general" → all WebSocket servers with clients in #general channel receive and broadcast → all clients see instantly. Also used for presence (who's online?).
- **Google Docs (WebSocket for collaborative editing):** Every keystroke sends a delta operation (character inserted at position X) via WebSocket. All editors in the doc receive deltas → apply locally → stay in sync. Latency critical (<100ms). WebSocket provides instant bidirectional delivery.
- **DoorDash (real-time delivery tracking):** Customer tracks driver location. Driver's phone sends location updates via WebSocket every 5 seconds. Server broadcasts to customer's app via WebSocket. No polling overhead, updates arrive instantly.
- **Discord (WebSocket for voice/chat):** All real-time features (text chat, voice calls, presence) run over WebSocket. Connection multiplexes multiple streams (chat messages, voice packets, presence updates). If WebSocket disconnects, Discord falls back to polling (slower but works).
- **Stripe Webhooks + Twilio SMS:** Webhooks are "server pushes" similar to WebSocket — when payment completes, Stripe POSTs to your server. But webhook is one-direction (server → client). WebSocket is bidirectional (client and server both push anytime).

---

## 🧭 When to Use vs When NOT to Use

| Use WebSocket when | Do NOT use when |
|---|---|
| Real-time updates (chat, notifications, live dashboards) | Request-response pattern (just use REST) |
| Bidirectional messaging needed | You only need server-to-client push (use SSE instead) |
| Low latency is critical (<100ms) | Occasional messages, high delay tolerance (polling is fine) |
| Message frequency is high (many msgs/sec) | Browser compatibility with very old clients (IE8) |
| You want to avoid polling overhead | Your backend is stateless (WebSocket requires sticky sessions) |

**The common mistake:** Using WebSocket for everything. REST is simpler; WebSocket is for real-time. Don't use WebSocket for a static API.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Instant bidirectional messaging (no polling). Lower bandwidth (no HTTP overhead per message). Lower latency (don't wait for polling interval). Scalable for many concurrent connections (async I/O). Natural fit for real-time features. |
| **You lose** | Server-side complexity (must manage open connections, handle disconnects gracefully). Stateful connections (can't easily scale horizontally without sticky sessions or message broker). Browser compatibility edge cases (older proxies might not understand WebSocket upgrade). Memory overhead (holding thousands of open sockets requires buffer space). Firewall/proxy issues (some corporate networks block WebSocket). |
| **Failure mode** | Server crashes → all WebSocket clients disconnect, must reconnect. Network partition → clients think they're connected, send messages that never arrive (must implement timeout + reconnect logic). High connection count → memory exhaustion (each connection uses ~100KB buffer). Scaling across servers → messages from one client on Server A don't reach clients on Server B (must use message broker like Kafka). Mitigation: use circuit breaker pattern (close connections on overload), implement reconnection with exponential backoff, use Kafka/Redis for fan-out across servers. |

---

## 🔬 Interview Q&As

### Q: "How do you scale WebSocket across multiple servers? Client connects to Server A, but messages from Server B's clients never reach them."

> Each server can only send messages to ITS OWN connected clients. To broadcast across servers, use a message broker: (1) Client connects to Server A via WebSocket. (2) Another client on Server B sends message. (3) Server B publishes message to Kafka topic. (4) Server A subscribes to same Kafka topic, receives message. (5) Server A forwards to its connected clients. Without Kafka, Server A never sees the message. ⭐ **Tier 2 — Distributed systems**

### Q: "You have 1M concurrent WebSocket connections. What's the main bottleneck?"

> Memory. Each connection holds a TCP socket + buffers (~100KB). 1M × 100KB = 100GB. Single server can't hold it. Solution: (1) Run WebSocket servers behind a load balancer (sticky sessions — same client always hits same server). (2) Distribute clients across multiple servers. (3) Use async I/O (Spring WebFlux, Netty) instead of blocking threads (each thread = ~1MB stack space, 100 threads × 1MB = too much). ⭐ **Tier 2 — Scaling**

### Q: "Client sends message via WebSocket. Network drops. Client thinks it sent but server never received. Then network comes back. What happens?"

> Client doesn't know the message failed (WebSocket just closed without error notification at app level). Must implement timeout: if no response/ACK within 5s, assume message lost, retry. OR use message ID: client sends message with ID 123, server ACKs: "message 123 received". If no ACK within timeout, client retries. This requires idempotency: same message (same ID) sent twice should have same effect. ⭐ **Tier 2 — Reliability**

### Q: "Why does WebSocket require sticky sessions, and how do you configure them?" ⭐

> WebSocket connections are stateful — the open socket lives on a specific server instance. Without sticky sessions, a client's HTTP upgrade request may route to Server 1 (opens socket), but subsequent frames may round-robin to Server 2 (no socket for this client → error). Configure your ALB to use duration-based session affinity: ALB sets an `AWSALB` cookie, and all requests from that browser hit the same target for the cookie's duration. Sticky sessions are only the first half — you still need Kafka/Redis Pub-Sub so that messages from clients on Server 1 can reach clients on Server 2. Both are required. ⭐ **Tier 1 — Scaling prerequisite, almost always probed**

### Q: "How is WebSocket different from Server-Sent Events (SSE)? When do you choose SSE?" ⭐

> SSE is one-direction (server → client only, HTTP chunked response). WebSocket is bidirectional (both sides can push). Key trade-off: SSE works through all HTTP proxies and load balancers with zero config; WebSocket requires sticky sessions and some proxies block the upgrade. SSE has built-in browser reconnection; WebSocket reconnect must be hand-coded. Use SSE when only the server pushes (stock tickers, notification bell, live feed) — simpler, no sticky sessions. Use WebSocket when the client also sends data (chat, collaborative editing, gaming). Default to SSE unless you have a strong reason for bidirectionality. ⭐ **Tier 1 — Protocol comparison**

### Q: "You want to broadcast a message to 1M users at once. How?"

> Publish to Kafka topic (fan-out exchange). **Each WebSocket pod must be in its own consumer group** — if all pods share one consumer group, Kafka load-balances and only one pod gets the message. With per-pod consumer groups, every pod receives the message, loops through its connected clients, and sends via WebSocket in parallel. Broadcast completes in ~100ms. Alternative: Redis Pub/Sub on a single channel — every pod subscribes; works well up to ~500K concurrent and simpler operationally. For > 500K concurrent, Redis 7.0+ sharded Pub/Sub distributes the publish load across cluster nodes. Kafka is better here when you want message durability (replaying the broadcast for pods that were down). ⭐ **Tier 2 — Fan-out pattern**

### Q: "Client reconnects after network drop. It missed messages while offline. How do you handle this?"

> Two approaches: (1) **Message queue**: store messages in a queue per user (Redis list). When client reconnects, server sends recent messages (last 100 messages for past 5 min). Once client ACKs, remove from queue. (2) **Event replay**: store all messages in Kafka; when client reconnects, replay messages from `last_seen_offset` onwards. Second approach scales better (Kafka handles retention). ⭐ **Tier 2 — Reliability**

---

## 🧾 TL;DR

> "WebSocket is a persistent, bidirectional protocol (HTTP 101 upgrade). Scales with two things working together: sticky sessions (ALB cookie affinity — so reconnects hit the same server) + message broker (Kafka/Redis Pub-Sub — so messages cross server boundaries). Choose SSE instead when only the server pushes data — simpler, no sticky sessions, auto-reconnect built in."

---

## 🔗 Related Concepts

- **`19-message-queues-kafka-rabbitmq.md`** — Kafka used for fan-out messages across WebSocket servers
- **`25-monitoring-observability-fundamentals.md`** — Monitor WebSocket connection count, message latency, disconnection rate
- **`20-circuit-breaker-resilience.md`** — Close WebSocket connections under overload (bulkhead isolation)
- **`10-backpressure.md`** — Client sends messages faster than server can process; buffer fills up; close connection or drop messages

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **MDN Web Docs — WebSocket API** | Official WebSocket protocol spec, API reference, code examples | ~20 min read |
| **ByteByteGo — "WebSocket vs Polling vs Server-Sent Events"** (YouTube) | Visual comparison of three approaches, trade-off table, use case examples | ~10 min |
| **High Scalability Blog — WebSocket Scaling** | Scaling WebSocket to millions of connections, sticky sessions, message broker patterns | ~20 min read |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 25, 2026 | Created as Concept 26. Covered WebSocket protocol (HTTP upgrade, frames, heartbeat), bidirectional messaging, scaling via Kafka, Spring WebSocket code example, comparison with polling/SSE. |
| July 1, 2026 | Added sticky sessions section (ALB cookie affinity, why required, limitation on crash), SSE vs WebSocket comparison table, expanded SSE/sticky Q&As to ⭐ Tier 1. Updated TL;DR. |
| Jul 9, 2026 | Added **Step 3: How does a server know which pod holds the recipient's connection?** — gap identified during DocuSign prep reading. Three approaches: Approach 1 (blind broadcast / room fan-out), Approach 2 (user-location registry in Redis), Approach 3 (user-specific Redis channel — most common). Summary table + interview answer phrasing added. |
| Jul 10, 2026 | **Four correctness fixes** after deep-dive pushback: (1) Approach 1 — added critical Kafka consumer group clarification: each pod must be in its OWN consumer group for fan-out; same group = load-balanced, not broadcast. (2) Approach 3 — replaced vague "requires Redis Cluster" note with accurate Redis 7.0+ sharded Pub/Sub explanation (SSUBSCRIBE/SPUBLISH) and scale tiers (< 500K single node; 500K–50M Cluster 7+; 50M+ consistent hashing). (3) Added Kafka trade-off section explaining the fundamental mismatch — partition assignment ≠ socket placement — and Kafka's correct role (offline storage, not live routing). (4) Summary table and interview answers updated to reflect correct approach by scale tier. |
