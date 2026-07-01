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

> Publish to Kafka topic (fan-out exchange). Each WebSocket server subscribes. Each server gets message, loops through its connected clients, sends via WebSocket. This happens in parallel across all servers. Broadcast completes in ~100ms. Alternative: use Redis Pub-Sub (similar, but worse for massive scale). ⭐ **Tier 2 — Fan-out pattern**

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
