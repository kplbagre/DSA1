# gRPC & Protocol Buffers — Efficient RPC Over HTTP/2

> gRPC is a framework for RPC (Remote Procedure Call) that uses Protocol Buffers (binary serialization) over HTTP/2. Compared to REST+JSON, gRPC is 7-10x faster (binary vs text), supports streaming (bidirectional), and multiplexes multiple RPCs on one connection. At SDE 3: you must know when to use gRPC (service-to-service) vs REST (client-to-service), and how HTTP/2 multiplexing works.

---

## 🎯 Why This Matters

Your microservice makes 100 RPC calls/sec to downstream services. Each REST call: 200 bytes overhead (HTTP headers), 500 bytes JSON response. Total: 100KB/sec waste. Switch to gRPC: same calls, 50 bytes overhead (HTTP/2 header compression), 100 bytes binary response. Total: 5KB/sec. 95% bandwidth reduction. At scale (Netflix, Google), this is millions of dollars saved. In interviews, candidates often confuse gRPC with REST; you'll explain the trade-offs.

---

## 📖 What is gRPC & Protocol Buffers? (Full Form & Basics)

**gRPC = gRPC Remote Procedure Call** (the "g" stands for Google)

**RPC (Remote Procedure Call)** = calling a function on a different server as if it were local.

**What it does:**
- Client calls: `getUser(123)` on a remote server
- Server executes the function
- Server sends back the result
- Client uses the result

**Traditional REST way:**
```
Client: GET /api/users/123 HTTP/1.1
        (text, 200+ bytes of headers)
Server: {"id": 123, "name": "John", ...}
        (JSON, 500 bytes of text)
```

**gRPC way:**
```
Client: getUser(123)
        (binary, 50 bytes, not human-readable but tiny)
Server: {id: 123, name: "John", ...}
        (binary, 100 bytes)
```

**Protocol Buffers** = a way to serialize data into binary (compact, not human-readable) instead of JSON (text, human-readable).

**Key difference:** 
- REST: Human-readable, slow, large
- gRPC: Machine-readable, fast, small (7-10x smaller)

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|------|----------------------|---------|
| **Protocol Buffers (Protobuf)** | binary serialization format; encodes data as compact bytes, not human-readable text | `{id: 1, name: "John"}` in JSON = 26 bytes; in Protobuf ≈ 8 bytes |
| **.proto file** | schema definition file; both client and server generate code from it at build time | `message User { int32 id = 1; string name = 2; }` |
| **HTTP/2 Multiplexing** | multiple RPCs share one TCP connection simultaneously; no per-call connection setup overhead | 100 concurrent gRPC calls over 1 connection vs 100 separate TCP sockets in REST |
| **gRPC Stub** | auto-generated client code from `.proto`; caller invokes it like a local method | `UserServiceStub stub = channel.newStub(); stub.getUser(request)` |
| **Unary RPC** | client sends one request, server sends one response — same semantics as HTTP REST | `GetUser(UserRequest) → UserResponse` |
| **Server Streaming** | client sends one request, server streams back multiple responses over time | `GetLiveUpdates(req) → stream of OrderEvent` |
| **Client Streaming** | client streams multiple messages, server sends one final response | `UploadFileChunks(stream of bytes) → UploadResponse` |
| **Bidirectional Streaming** | both client and server send a stream of messages simultaneously | live chat: both sides stream messages; neither waits for the other to finish |
| **Field Numbers** | integer tags in `.proto` identifying each field in binary encoding; enable safe schema evolution | old field removed → old clients see zero-value; new field added → old clients ignore it |
| **varint encoding** | integers encoded in 1–10 bytes based on magnitude; small numbers take fewer bytes | `1` encodes to 1 byte; `300` encodes to 2 bytes — vs JSON `"300"` = 5 chars |

---

## 🧠 The Mental Model

Imagine two people talking:

**REST+JSON (inefficient):**
- Person A speaks: "Hello, my name is John, I am 30 years old."
- Person B speaks: "Hello, my name is Alice, I am 28 years old."
- Inefficient: person has to say their name every time (HTTP headers repeat).

**gRPC+Protobuf (efficient):**
- Pre-agreed format: "Person {name: string, age: int}".
- Person A sends: binary encoding of {John, 30} (tiny message, 10 bytes).
- Person B sends: binary encoding of {Alice, 28} (also 10 bytes).
- Efficient: both know structure upfront; no repetition.

**The key insight:** Protocol Buffers encode **schema once, transmit data minimally**. HTTP/2 reuses connections; no per-call overhead.

---

## 🎨 Visual — gRPC in System Architecture

### Full System Topology — Where gRPC Sits

```
CLIENT APPLICATION
    ↓ (HTTP/2 multiplexed connection)
┌─────────────────────────────────────────────────────┐
│ gRPC CLIENT (generated from .proto file)            │
│ ┌───────────────────────────────────────────────┐  │
│ │ StubClient stub = channel.newStub()           │  │
│ │ Response resp1 = stub.getUser(request)        │  │
│ │ Response resp2 = stub.getOrder(request) // con
│ │ Response resp3 = stub.getProduct(request)     │  │
│ │                                               │  │
│ │ All 3 multiplexed on ONE HTTP/2 connection    │  │
│ │ (headers: user→stream1, order→stream2, etc.)  │  │
│ └───────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
    ↓ (HTTP/2, binary protocol buffers, multiplexed)
┌─────────────────────────────────────────────────────┐
│ gRPC SERVER (generated from .proto file)            │
│ ┌───────────────────────────────────────────────┐  │
│ │ Service Implementation:                       │  │
│ │ GetUser() { return user from DB }             │  │
│ │ GetOrder() { return order from cache }        │  │
│ │ GetProduct() { return product with variant }  │  │
│ │                                               │  │
│ │ Handles 3 concurrent streams (multiplexed)   │  │
│ │ Each stream has own context (no blocking)    │  │
│ └───────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────┘
    ↓
BACKEND SERVICES (Database, Cache, etc.)

KEY INVARIANT:
   One HTTP/2 connection carries multiple streams.
   Each stream is independent RPC call.
   No head-of-line blocking (unlike HTTP/1.1).
   Binary protocol (Protocol Buffers) is compact.
```

### Component Detail — HTTP/2 Multiplexing & Streaming

```
HTTP/1.1 (Sequential, slow):
┌───────────────────────────────────────────┐
│ Request #1: GET /users/123                │
│ Response: {id: 123, name: "John", ...}    │  Wait
│ (Headers + body = 500 bytes)              │  for
│ ┌───────────────────────────────────────┐ │  complete
│ │ Request #2: GET /orders/456            │ │
│ │ Response: {id: 456, items: [...], ...} │ │
│ │ (500 bytes)                            │ │
│ └───────────────────────────────────────┘ │
│ ┌───────────────────────────────────────┐ │
│ │ Request #3: GET /products/789          │ │
│ │ Response: {id: 789, price: 49.99, ...} │ │
│ │ (500 bytes)                            │ │
│ └───────────────────────────────────────┘ │
└───────────────────────────────────────────┘
Total: 3 requests sequential = T + T + T = 3T (if each takes 10ms = 30ms)


HTTP/2 (Multiplexed, fast):
┌───────────────────────────────────────────┐
│ Stream 1: Request #1 → Response (50 bytes)│
│ Stream 2: Request #2 → Response (50 bytes)│ All 3 concurrent
│ Stream 3: Request #3 → Response (50 bytes)│ (binary encoded, compact)
│ (Headers reused, not repeated per stream)│
└───────────────────────────────────────────┘
Total: 3 requests parallel = max(T, T, T) = 1T (10ms, not 30ms)
Latency: 3x faster
Bandwidth: 1/10 (binary vs text headers)


STREAMING TYPES:

1. UNARY (one request, one response):
   ┌─────────────────────────────────────┐
   │ Client: GetUser(id=123)             │
   │ Server: Response(user={...})        │
   │ Traditional RPC                     │
   └─────────────────────────────────────┘

2. SERVER STREAMING (one request, many responses):
   ┌─────────────────────────────────────┐
   │ Client: GetUserOrders(user_id=1)    │
   │ Server: Stream multiple orders      │
   │  → {order_id: 1, ...}               │
   │  → {order_id: 2, ...}               │
   │  → {order_id: 3, ...}               │
   │  → END_OF_STREAM                    │
   │ Use case: paginated results         │
   └─────────────────────────────────────┘

3. CLIENT STREAMING (many requests, one response):
   ┌─────────────────────────────────────┐
   │ Client: Stream multiple orders      │
   │  → {item: "laptop", qty: 1}         │
   │  → {item: "mouse", qty: 2}          │
   │  → {item: "keyboard", qty: 1}       │
   │  → END_OF_STREAM                    │
   │ Server: Response(total: 3 items)    │
   │ Use case: bulk upload               │
   └─────────────────────────────────────┘

4. BIDIRECTIONAL STREAMING (many requests, many responses):
   ┌──────────────────────────────────────┐
   │ Client streams price updates:        │
   │  → {product: "laptop", price: 999}  │
   │  → {product: "mouse", price: 25}    │
   │ Server streams confirmation:         │
   │  ← {product: "laptop", status: OK}  │
   │  ← {product: "mouse", status: OK}   │
   │ Use case: real-time bidirectional   │
   └──────────────────────────────────────┘


PROTOCOL BUFFER MESSAGE (Binary encoding):

.proto definition:
  message User {
    int32 id = 1;
    string name = 2;
    int32 age = 3;
  }

JSON encoding (REST):
  {"id": 123, "name": "John", "age": 30}
  → 35 bytes (text overhead)

Protobuf encoding (gRPC):
  Field 1 (id): varint 123 = 0x7B = 1 byte
  Field 2 (name): string "John" = 1 byte length + 4 bytes = 5 bytes
  Field 3 (age): varint 30 = 0x1E = 1 byte
  Total: ~7 bytes (80% smaller!)

  Binary representation (hexadecimal):
  08 7B 12 04 4A 6F 68 6E 18 1E
  ↑  ↑  ↑  ↑           ↑  ↑
  field id, field name, length, field age

KEY INVARIANT:
   HTTP/2 multiplexing: multiple streams on one connection.
   Protocol Buffers: compact binary encoding (10x smaller than JSON).
   Bidirectional streaming: both client and server can push anytime.
```

---

## ⚙️ How It Actually Works

**Steps in plain English:**

1. **Define API in .proto file** (service methods + message types).
2. **Generate client and server stubs** (protoc compiler creates Java/Python/Go code).
3. **Client creates gRPC channel** (HTTP/2 connection).
4. **Client makes RPC calls** (sends serialized messages).
5. **Multiple RPCs multiplexed** on single connection (different streams).
6. **Server receives and routes** to appropriate handler.
7. **Server sends response** (serialized Protocol Buffer message).
8. **Client receives and deserializes** response.
9. **Connection stays open** for future RPCs (reuse).

```java
// 1. Define API in .proto file

/*
syntax = "proto3";

package com.example.api;

service UserService {
  rpc GetUser(UserId) returns (User);
  rpc ListUserOrders(UserId) returns (stream Order);
  rpc CreateOrders(stream Order) returns (OrderSummary);
}

message UserId {
  int32 id = 1;
}

message User {
  int32 id = 1;
  string name = 2;
  string email = 3;
  int32 age = 4;
}

message Order {
  int32 id = 1;
  int32 user_id = 2;
  double amount = 3;
  string status = 4;
}

message OrderSummary {
  int32 total_orders = 1;
  double total_amount = 2;
}
*/

// 2. Protoc generates:
//   UserServiceGrpc.UserServiceStub (client)
//   UserServiceGrpc.UserServiceImplBase (server base class)
//   User, Order, UserId message classes

// 3. Server implementation

@GrpcService
public class UserServiceImpl extends UserServiceGrpc.UserServiceImplBase {
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OrderRepository orderRepository;

    // Unary RPC: one request, one response
    @Override
    public void getUser(UserId request, StreamObserver<User> responseObserver) {
        // Step 6 — Handle RPC
        User user = userRepository.findById(request.getId()).orElse(null);

        if (user == null) {
            // Step 7 — Send error
            responseObserver.onError(
                Status.NOT_FOUND.withDescription("User not found").asException()
            );
        } else {
            // Step 7 — Send response (serialized protobuf)
            responseObserver.onNext(user);
            responseObserver.onCompleted();
        }
    }

    // Server streaming: one request, many responses
    @Override
    public void listUserOrders(UserId request, StreamObserver<Order> responseObserver) {
        // Step 6 — Handle RPC
        List<Order> orders = orderRepository.findByUserId(request.getId());

        // Step 7 — Stream multiple responses
        for (Order order : orders) {
            responseObserver.onNext(order);  // Send one order
        }
        responseObserver.onCompleted();  // Signal end of stream
    }

    // Client streaming: many requests, one response
    @Override
    public StreamObserver<Order> createOrders(
            StreamObserver<OrderSummary> responseObserver) {
        return new StreamObserver<Order>() {
            int totalOrders = 0;
            double totalAmount = 0;

            @Override
            public void onNext(Order order) {
                // Step 6 — Receive one order
                orderRepository.save(order);
                totalOrders++;
                totalAmount += order.getAmount();
            }

            @Override
            public void onError(Throwable t) {
                responseObserver.onError(t);
            }

            @Override
            public void onCompleted() {
                // Step 7 — Send response after all orders received
                OrderSummary summary = OrderSummary.newBuilder()
                    .setTotalOrders(totalOrders)
                    .setTotalAmount(totalAmount)
                    .build();

                responseObserver.onNext(summary);
                responseObserver.onCompleted();
            }
        };
    }
}

// 4-5. Client implementation (step 3-9)

@Service
public class UserServiceClient {
    // Step 3 — Create gRPC channel (HTTP/2 connection)
    private final ManagedChannel channel;
    private final UserServiceGrpc.UserServiceStub stub;

    public UserServiceClient() {
        channel = ManagedChannelBuilder.forAddress("localhost", 50051)
            .usePlaintext()  // Not HTTPS for demo; use TLS in prod
            .build();

        stub = UserServiceGrpc.newStub(channel);
    }

    // Step 4-9 — Make RPC calls
    public void callUnaryRpc() {
        // Step 4 — Create request (protobuf message)
        UserId request = UserId.newBuilder()
            .setId(123)
            .build();

        // Step 5 — Send RPC (multiplexed on HTTP/2 connection)
        stub.getUser(request, new StreamObserver<User>() {
            @Override
            public void onNext(User user) {
                // Step 8 — Receive and deserialize response
                System.out.println("User: " + user.getName());
            }

            @Override
            public void onError(Throwable t) {
                System.err.println("Error: " + t);
            }

            @Override
            public void onCompleted() {
                System.out.println("RPC completed");
            }
        });
    }

    // Server streaming example
    public void callServerStreamingRpc() {
        UserId request = UserId.newBuilder().setId(123).build();

        // Step 5 — Request server to stream orders
        stub.listUserOrders(request, new StreamObserver<Order>() {
            @Override
            public void onNext(Order order) {
                // Step 8 — Receive each order (streamed)
                System.out.println("Order: " + order.getId());
            }

            @Override
            public void onError(Throwable t) {}

            @Override
            public void onCompleted() {
                System.out.println("All orders received");
            }
        });
    }

    // Client streaming example
    public void callClientStreamingRpc() {
        // Step 5 — Initiate streaming upload
        StreamObserver<Order> requestStream = stub.createOrders(
            new StreamObserver<OrderSummary>() {
                @Override
                public void onNext(OrderSummary summary) {
                    // Step 8 — Receive summary after all orders sent
                    System.out.println("Total: " + summary.getTotalOrders() + 
                        " orders, Amount: " + summary.getTotalAmount());
                }

                @Override
                public void onError(Throwable t) {}

                @Override
                public void onCompleted() {}
            }
        );

        // Step 4 — Send multiple orders (streaming)
        for (int i = 1; i <= 3; i++) {
            Order order = Order.newBuilder()
                .setId(i)
                .setUserId(123)
                .setAmount(50.0 * i)
                .setStatus("PENDING")
                .build();

            requestStream.onNext(order);  // Send one order
        }

        requestStream.onCompleted();  // Signal end of stream
    }

    public void shutdown() {
        channel.shutdown();
    }
}

// gRPC Configuration
@Configuration
public class GrpcConfig {
    @Bean
    public GrpcServerConfigurer grpcServerConfigurer() {
        return serverBuilder -> serverBuilder
            .maxInboundMessageSize(4 * 1024 * 1024); // 4MB max message
    }
}
```

### Proto3 Schema Evolution — What's Safe vs What Breaks

A heavily probed interview topic: "You need to add a field to your proto. What's safe?"

The key rule: **field numbers are the wire identity.** When protobuf serializes a message, field names are not sent — only field numbers. Changing a field number is a breaking change. The name is irrelevant at runtime.

| Change | Safe? | Why |
|---|---|---|
| Add a new field with a new field number | ✅ Safe | Old clients ignore unknown field numbers (proto3 default) |
| Remove an optional field | ✅ Safe (with caution) | Old clients that still send it — new server ignores; new clients — missing field gets zero/empty default |
| Rename a field (keep same field number) | ✅ Safe | Wire format uses number, not name |
| Change a field **number** | ❌ Breaking | Old clients encode with old number; new server reads wrong field |
| Change a field **type** incompatibly (e.g., `int32` → `string`) | ❌ Breaking | Old encoded bytes decoded as wrong type → garbage data |
| Reuse a deleted field's number | ❌ Breaking | Reserve deleted field numbers to prevent accidental reuse |

**Best practice — reserve deleted field numbers:**

```java
// proto3 — safe schema evolution example
// syntax = "proto3";

// message User {
//   int32 id = 1;
//   string name = 2;
//   // Field 3 was "email" but we deleted it
//   reserved 3;              // prevents reuse of field number 3
//   reserved "email";        // prevents reuse of field name "email"
//   int32 age = 4;           // new field — old clients ignore it
//   string phone_number = 5; // added later — fully backward compatible
// }
```

**Forward vs backward compatibility:**
- **Backward compatible:** New client can read old-format messages (new fields just get default values)
- **Forward compatible:** Old client can read new-format messages (unknown fields are silently ignored in proto3)

Proto3 is **both** by default — as long as you never reuse field numbers and only add/remove optional fields.

**Interview phrasing:** *"Proto3 is backward and forward compatible by default. Field numbers are the wire identity — as long as I never reuse a deleted field's number and only add new optional fields, old and new clients interoperate seamlessly. I use `reserved` on any deleted field number to prevent future developers from accidentally breaking the contract."*

### What is Protocol Buffers (Protobuf), and why does it fit here?

Protocol Buffers is **a method for serializing structured data** (from Google). Unlike JSON (text, verbose), Protobuf is binary and compact. You define schema in .proto file; compiler generates language-specific code (Java, Python, Go). In an interview, if asked: *"Protocol Buffers is a binary serialization format that's 10x more compact than JSON and faster to serialize/deserialize. Schema is defined once in .proto files; code is auto-generated. Type-safe and backward-compatible (can add fields without breaking old clients)."*

### What is HTTP/2, and why does it fit here?

HTTP/2 is the **successor to HTTP/1.1** with multiplexing (multiple concurrent streams on one connection), header compression, and server push. REST typically uses HTTP/1.1 (head-of-line blocking). gRPC uses HTTP/2 (multiplexed). In an interview, if asked: *"HTTP/2 multiplexing allows 100 concurrent RPC calls on one TCP connection without blocking each other. HTTP/1.1 requires separate connections per request (overhead). gRPC + HTTP/2 + Protocol Buffers = 7x faster than REST + JSON."*

---

## 🏢 Real World — Where Companies Use This

- **Google (gRPC internally):** All inter-service communication uses gRPC. Massively reduces bandwidth and latency at planetary scale.
- **Netflix (gRPC for microservices):** Uses gRPC for service-to-service communication (recommender → backend, backend → search). HTTP/REST only for client API.
- **Uber (Protocol Buffers internally):** Uber standardized on Protocol Buffers for all service communication. Binary messages reduce latency in ride-matching algorithms (critical for performance).
- **Slack (gRPC + streaming):** Uses gRPC for real-time messaging (server pushes to clients, clients update presence). Bidirectional streaming for live updates.
- **etcd (Kubernetes consensus):** etcd uses Protocol Buffers for Raft consensus messages (leader-replica replication). Compact serialization improves consensus latency.

---

## 🧭 When to Use vs When NOT to Use

| Use gRPC when | Use REST when |
|---|---|
| Service-to-service communication (internal) | Client-to-service API (browsers, mobile) |
| Performance is critical (latency, bandwidth) | Simplicity matters (easy debugging, cURL) |
| You need bidirectional streaming | Simple request-response pattern |
| Message size is large (images, data) | Humans need to read requests (JSON is readable) |
| Teams standardize on protobuf ecosystem | API is public (versioning is easier with REST) |

**The common mistake:** Using gRPC for public APIs. Public APIs should be REST (human-readable, simpler for clients). gRPC shines for internal service communication.

**When REST genuinely wins over gRPC:**

| Scenario | Why REST wins |
|---|---|
| **Public / partner APIs** | REST + JSON is universally accessible — any language, any tool, no proto schema needed. gRPC requires clients to have the .proto file and generated stubs. |
| **Browser-native clients** | Browsers cannot speak raw gRPC over HTTP/2 (gRPC-web is a workaround requiring a proxy; adds complexity). REST works natively in every browser via `fetch`. |
| **Debugging and inspection** | REST calls are inspectable with cURL, Postman, browser devtools. Binary gRPC frames are not human-readable — you need grpcurl or a dedicated tool. |
| **Simple request-response, infrequent calls** | gRPC setup overhead (proto compilation, channel management) is overkill for a service that receives 10 RPC calls/day. |
| **Firewall / proxy environments** | Some corporate proxies and legacy firewalls block HTTP/2 or don't understand gRPC content-type. REST over HTTP/1.1 has universal support. |

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | 7-10x faster (binary vs JSON, multiplexing vs sequential). Streaming (bidirectional). Type-safe (protobuf schema enforced). Backward compatible (new fields don't break old clients). Code generation (eliminate hand-written serialization). |
| **You lose** | Complexity (protobuf, code generation, HTTP/2 setup). Debugging difficulty (binary format, not human-readable). Browser incompatibility (gRPC-web workaround exists). Ecosystem maturity (REST tools are ubiquitous). Ecosystem diversity (Protobuf ties you to Google's ecosystem). |
| **Failure mode** | Breaking schema change (add required field) → old clients crash. Version mismatch (client and server protobuf versions differ) → deserialization errors. Mitigation: evolve schema carefully (add optional fields), versioning strategy, comprehensive testing. |

---

## 🔬 Interview Q&As

### Q: "You have 100 microservices making 1000 RPC calls/sec each (100K calls/sec total). Each REST+JSON call: 200 bytes overhead. Switch to gRPC: 50 bytes overhead. How much bandwidth saved?"

> REST: 100K × 200 = 20MB/sec overhead. gRPC: 100K × 50 = 5MB/sec overhead. Saved: 15MB/sec = 1.3TB/day. At cloud rates ($0.10/GB), you save $130/day ≈ $47K/year on bandwidth alone. Doesn't include latency savings (3x faster = more throughput per server). ⭐ **Tier 2 — Quantifying trade-offs**

### Q: "You need to add a new field to a proto message already in production. What's safe and what breaks?" ⭐

> The rule: **field numbers are the wire identity, not names.** Adding a new field with a new field number is always safe — old clients silently ignore unknown field numbers (proto3 default). Changing an existing field's number is a breaking change — old clients encode with the old number, new server reads the wrong slot. Renaming a field is safe (name isn't on the wire). Deleting a field is safe but mark the number as `reserved` to prevent future reuse (`reserved 3; reserved "email";`). Never reuse a deleted field number — if an old client still sends field 3, your new field at position 3 would misinterpret the bytes. ⭐ **Tier 1 — always probed when gRPC comes up**

### Q: "You define protobuf message with required field. Old client doesn't know about it. What happens?"

> Protobuf3 removed `required` keyword (too strict). All fields are optional. If field missing, default value used (0 for int, empty string for string). Old clients work, new clients get sensible defaults. If you truly need field validation, do it in application logic, not protobuf. ⭐ **Tier 2 — Versioning**

### Q: "Server streams 1M orders to client. Network drops halfway through. How do you resume?"

> gRPC streams are **full-duplex** (both sides can send independently over the same HTTP/2 stream), but they are **not resumable** — a broken connection tears down the stream and any un-received messages are lost; gRPC has no built-in resume/replay. Mitigation: (1) Paginate (server streams 1000 at a time, client requests next page if needed). (2) Checkpoint offsets (client remembers last received ID, requests resume from there). (3) Client-side buffering (receive into local queue, process asynchronously). ⭐ **Tier 2 — Streaming robustness**

### Q: "gRPC request takes 100ms (server processing: 90ms, network latency: 10ms). You make 100 concurrent gRPC calls. Total time?"

> max(100ms per call) = 100ms (concurrent calls on multiplexed connection). With REST+HTTP/1.1: would need separate connections, possibly 100 × 100ms = 10,000ms in worst case (or use connection pooling, but still slower due to non-multiplexing). ⭐ **Tier 2 — Concurrency**

### Q: "You put gRPC services behind a standard load balancer and one backend pod is getting all the traffic. Why?" ⭐

> The classic gRPC operational gotcha. gRPC holds a **single long-lived HTTP/2 connection** and multiplexes all calls over it. A connection-level (L4) load balancer picks a backend *once* at connection setup and pins every subsequent request there — so a client hammers one pod while others sit idle, and new pods added by autoscaling get no traffic. Fixes: (1) **L7 / request-level load balancing** (Envoy, Linkerd, a service mesh, or gRPC-aware NGINX) that balances individual streams, not connections; (2) **client-side load balancing** (the gRPC client resolves all backend addresses and round-robins itself); (3) **lookaside/xDS load balancing** for large fleets. Also periodically recycle connections (`MAX_CONNECTION_AGE`) so rebalancing can happen. This is one of the most-probed real-world gRPC issues.

### Q: "How do timeouts and errors propagate across a gRPC call chain?"

> gRPC uses **deadlines**, not per-hop timeouts: the client sets an absolute deadline, and it propagates through the metadata down the call chain — every downstream service sees the remaining time budget and aborts with `DEADLINE_EXCEEDED` if it's blown, preventing wasted work on a request the caller already gave up on. Errors use gRPC's own **status codes** (distinct from HTTP: `OK`, `NOT_FOUND`, `UNAVAILABLE`, `DEADLINE_EXCEEDED`, `RESOURCE_EXHAUSTED`, `FAILED_PRECONDITION`, …), returned in the trailing metadata. `UNAVAILABLE` is the retryable one; `FAILED_PRECONDITION` is not. ⭐ **Tier 2 — error model**

---

## 🧾 TL;DR

> "gRPC is RPC over HTTP/2 using Protocol Buffers. Binary encoding (10x smaller than JSON). Multiplexing (concurrent RPCs on one connection). Streaming (unary, server-stream, client-stream, bidirectional). 7x faster than REST. Use internally; REST for public APIs (browser-native, debuggable, no proto schema required). Schema evolution: field numbers are the wire identity — add new fields freely, never reuse deleted numbers."

---

## 🔗 Related Concepts

- **`24-api-gateway-pattern.md`** — Gateway translates gRPC (internal) ↔ REST (client-facing)
- **`25-monitoring-observability-fundamentals.md`** — gRPC tracing via OpenTelemetry
- **`26-websocket-real-time-communication.md`** — Alternative: WebSocket for bidirectional (but gRPC is better for performance)

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **gRPC Official Guide** | Service definitions, streaming types, code generation, best practices | ~25 min read |
| **Protocol Buffers Documentation** | Message syntax, field numbers, backward compatibility, code generation | ~20 min read |
| **ByteByteGo — "gRPC vs REST"** (YouTube) | Performance comparison, use cases, HTTP/2 multiplexing explanation | ~12 min |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 25, 2026 | Created as Concept 33. Covered gRPC as high-performance RPC over HTTP/2, Protocol Buffers binary serialization, four streaming types (unary, server-stream, client-stream, bidirectional), multiplexing benefits, backward compatibility, when to use gRPC vs REST. |
| July 1, 2026 | Added proto3 schema evolution table (safe vs breaking changes, reserved fields), "When REST wins" table, ⭐ schema evolution Q&A. Updated TL;DR. |
| Jul 19, 2026 | **Factual fix + gaps.** (1) Corrected "streams are half-duplex" — gRPC bidi streams are full-duplex (contradicted the rest of the file); the real point is streams aren't resumable. (2) Added the classic HTTP/2 single-connection load-balancing gotcha (why one backend gets all traffic; L7/client-side LB fix) and (3) a deadline-propagation + gRPC status-code Q&A. |
