# Pub-Sub System (EventBus)

## 🎯 Problem Statement

Design an in-memory Publish-Subscribe (pub-sub) system — also called an **EventBus** (a component that routes events from publishers to all subscribers interested in a given topic, without the publisher knowing who is listening) — where:
- Publishers send messages to named topics without knowing who is listening
- Multiple subscribers can independently receive messages from the same topic
- Subscribers can be added or removed at runtime without affecting publishers
- Both synchronous and asynchronous delivery modes are supported

**TrueFoundry context:** TrueFoundry fires internal events across services (model deployed, training completed, job failed). An in-memory EventBus is the LLD version of a Kafka topic — the same decoupling principle at the process level.

---

## 📖 Requirements

**Functional:**
- `subscribe(topic, subscriber)` — register a subscriber for a topic
- `unsubscribe(topic, subscriber)` — remove a subscriber
- `publish(topic, payload)` — synchronous delivery: all subscribers called inline
- `publishAsync(topic, payload)` — asynchronous delivery: each subscriber notified in its own thread pool task

**Non-functional:**
- Thread-safe — concurrent subscribe/unsubscribe/publish on the same topic must not corrupt subscriber lists or throw `ConcurrentModificationException` (a runtime exception thrown when a collection is modified while being iterated)
- Slow subscriber must not block other subscribers (with async mode)
- Subscriber exceptions must not crash the publisher

**Out of scope:**
- Message persistence / replay
- Message ordering guarantees across topics
- Message filtering / predicates — see "What Would You Do Differently?"

---

## 🏗️ Class Design

### 🎨 Visual — Class Relationships

```
                      ┌─────────────────────────────────────────┐
                      │               EventBus                  │
                      │                                         │
                      │  topics:                                │
                      │    ConcurrentHashMap<                   │
                      │      String,                            │
                      │      CopyOnWriteArrayList<Subscriber>>  │
                      │  asyncExecutor: ExecutorService         │
                      │                                         │
                      │  + subscribe(topic, subscriber)         │
                      │  + unsubscribe(topic, subscriber)       │
                      │  + publish(topic, payload)              │
                      │  + publishAsync(topic, payload)         │
                      │  + shutdown()                           │
                      └────────────┬────────────────────────────┘
                                   │ manages
              ┌────────────────────┼─────────────────────────┐
              ▼                    ▼                         ▼
       <<interface>>           Message                  EventBusStats (optional)
       Subscriber              - id: String             - publishCount: LongAdder
       + onMessage(msg)        - topic: String          - subscriberCount: int
                               - payload: Object
                               - timestamp: long
```

**KEY INVARIANT:**
```
   publisher knows only the topic name — never the subscriber identity.
   subscriber knows only the message type — never the publisher identity.
   EventBus is the only shared mutable state.
```

---

## 🔑 Key Interfaces

```java
// Subscriber.java — Observer pattern: receives messages for subscribed topics
@FunctionalInterface
public interface Subscriber {
    void onMessage(Message message);
}

// EventBus.java — the subject that manages topic → subscriber mapping
public interface EventBus {
    void subscribe(String topic, Subscriber subscriber);
    void unsubscribe(String topic, Subscriber subscriber);
    // Synchronous — publisher blocks until all subscribers have processed the message
    void publish(String topic, Object payload);
    // Asynchronous — publisher returns immediately; subscribers notified in thread pool
    void publishAsync(String topic, Object payload);
    void shutdown();
}
```

---

## 🧭 Design Decisions

| Decision | Choice | Why |
|---|---|---|
| Topic → subscriber mapping | `ConcurrentHashMap<String, CopyOnWriteArrayList<Subscriber>>` | Two levels: map for topic isolation, list for per-topic subscriber iteration |
| Subscriber list type | `CopyOnWriteArrayList` | Writes (subscribe/unsubscribe) are rare; reads (publish iteration) are frequent; iteration never throws CME |
| `computeIfAbsent` on subscribe | Atomic "create if absent" | Prevents two concurrent subscribes on a new topic from creating two separate lists |
| Sync vs async as separate methods | Two explicit methods | Makes caller intent explicit; sync is simpler, async is fire-and-forget |
| Object payload | Avoids generic complexity | `Subscriber<T>` with generics requires unchecked casts when stored as `Subscriber<?>`; Object + caller cast is cleaner in practice |

**Why `CopyOnWriteArrayList` specifically — not `Collections.synchronizedList()`?**
`Collections.synchronizedList()` requires explicit locking on the caller side when iterating (`synchronized(list) { for (...) }`). If you forget the lock, you get a `ConcurrentModificationException`. `CopyOnWriteArrayList` makes iteration safe by default — publish() never needs to hold a lock, which matters at high event rates.

---

## 🎨 Visual — Pub-Sub Topology and Dispatch Modes

```
         Publisher                          Subscribers

         publish("model.trained", event)
               │
               ▼
   ┌───────────────────────────────────────────────────┐
   │                    EventBus                       │
   │                                                   │
   │  topics:                                          │
   │  "model.trained" → [MetricsCollector, Notifier]  │
   │  "job.failed"    → [AlertingService]              │
   │  "data.loaded"   → [TrainingPipeline, Logger]     │
   └───────────────────────────────────────────────────┘
               │
     ┌─────────┴─────────┐
     │                   │
  SYNC mode           ASYNC mode
     │                   │
  Inline call         Submit each
  in publisher        subscriber as
  thread              separate task
     │                to thread pool
     ▼                   │
  MetricsCollector    MetricsCollector ──▶ Worker 1
  .onMessage()        Notifier         ──▶ Worker 2
  Notifier
  .onMessage()

SYNC: publisher blocks until ALL subscribers return.
ASYNC: publisher returns immediately; subscribers may lag.

KEY INVARIANT:
   subscriber exceptions are caught per-subscriber — one failing subscriber
   never prevents others from receiving the message.
```

**CopyOnWriteArrayList snapshot semantics:**

```
T=0  subscribe("model.trained", S4) called
     → creates new array copy: [S1, S2, S3, S4]

T=-1 publish("model.trained", e) already started iterating
     → sees snapshot [S1, S2, S3] from T=-2

     S4 was added AFTER publish took its snapshot — misses this message.
     Next publish will see [S1, S2, S3, S4].
```

This is correct behavior — subscribe-after-publish should not retroactively receive messages.

---

## 🖊️ Coding Skeleton

**Interview writing order:**

**Steps in plain English:**

1. **Define the message** — immutable envelope: topic, payload, metadata.
2. **Define the observer** — `Subscriber` functional interface.
3. **Wire the EventBus** — `ConcurrentHashMap` + `CopyOnWriteArrayList`, implement subscribe/unsubscribe.
4. **Implement sync publish** — iterate snapshot, call each subscriber defensively.
5. **Implement async publish** — submit each subscriber call as a separate pool task.

```java
// Step 1 — the message envelope
public class Message {
    private final String id;
    private final String topic;
    private final Object payload;
    private final long timestamp;

    public Message(String topic, Object payload) {
        this.id = UUID.randomUUID().toString();
        this.topic = topic;
        this.payload = payload;
        this.timestamp = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public String getTopic() { return topic; }
    public Object getPayload() { return payload; }
    public long getTimestamp() { return timestamp; }
}

// Step 2 — the Observer interface
@FunctionalInterface
public interface Subscriber {
    void onMessage(Message message);
}

// Steps 3–5 — the EventBus
public class InMemoryEventBus implements EventBus {

    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Subscriber>> topics =
        new ConcurrentHashMap<>();
    private final ExecutorService asyncExecutor;

    public InMemoryEventBus(int asyncThreads) {
        this.asyncExecutor = Executors.newFixedThreadPool(asyncThreads);
    }

    @Override
    public void subscribe(String topic, Subscriber subscriber) {
        // computeIfAbsent is atomic — safe when two threads subscribe on a new topic simultaneously
        topics.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>()).add(subscriber);
    }

    @Override
    public void unsubscribe(String topic, Subscriber subscriber) {
        CopyOnWriteArrayList<Subscriber> subs = topics.get(topic);
        if (subs != null) {
            subs.remove(subscriber);
        }
    }

    @Override
    public void publish(String topic, Object payload) {
        Message message = new Message(topic, payload);
        // getOrDefault returns empty list — no NPE if topic has no subscribers
        for (Subscriber sub : topics.getOrDefault(topic, new CopyOnWriteArrayList<>())) {
            // Defensive catch — one bad subscriber must not block others
            try {
                sub.onMessage(message);
            } catch (Exception e) {
                // Production: log the exception here
            }
        }
    }

    @Override
    public void publishAsync(String topic, Object payload) {
        Message message = new Message(topic, payload);
        for (Subscriber sub : topics.getOrDefault(topic, new CopyOnWriteArrayList<>())) {
            // Each subscriber runs in its own pool task — slow sub never blocks others
            asyncExecutor.submit(() -> {
                try {
                    sub.onMessage(message);
                } catch (Exception e) {
                    // Production: log the exception here
                }
            });
        }
    }

    @Override
    public void shutdown() {
        asyncExecutor.shutdown();
    }
}
```

---

## ⚡ Concurrency

**Shared mutable state:**

| Shared State | Location | Writers | Readers | Protection |
|---|---|---|---|---|
| `topics` map | `ConcurrentHashMap` | subscribe (computeIfAbsent) | publish (getOrDefault) | `ConcurrentHashMap` — no external sync needed |
| Subscriber list per topic | `CopyOnWriteArrayList` | subscribe (add), unsubscribe (remove) | publish iterates | `CopyOnWriteArrayList` — writes copy array; reads see stable snapshot |
| `asyncExecutor` | `ExecutorService` | publishAsync (submit), shutdown | — | `ExecutorService` is internally thread-safe |

**Race: concurrent subscribe + publish on the same topic:**

```
Thread A (subscribe):  topics.computeIfAbsent("model.trained", ...)
                            .add(S4)
                            → CopyOnWriteArrayList creates new array: [S1, S2, S3, S4]

Thread B (publish):    iterates snapshot: [S1, S2, S3]
                            → S4 misses this message (snapshot taken before add)
                            → NO ConcurrentModificationException

Next publish by Thread B: iterates [S1, S2, S3, S4] → S4 receives messages
```

**Race: concurrent subscribe + unsubscribe on the same topic:**

```
Thread A (unsubscribe):  subs.remove(S2)  → new array: [S1, S3]
Thread B (subscribe):    subs.add(S4)    → new array: [S1, S2, S4] OR [S1, S3, S4]
```

`CopyOnWriteArrayList` serializes the writes internally (synchronized on the list object). The final state is deterministic — one write happens before the other. The ordering of the two operations is non-deterministic (race), but neither corrupts data.

**Why sync publish can slow the publisher:**
- `publish()` calls `sub.onMessage()` inline in the publisher's thread
- If subscriber 1 takes 500ms, subscriber 2 and 3 wait — publisher is blocked for the sum of all subscriber latencies
- Fix: use `publishAsync()` — each subscriber runs in its own pool task

**Async publish and subscriber lag:**
- `publishAsync()` submits N tasks immediately and returns
- If the thread pool is saturated, tasks queue inside the `ExecutorService`
- Publisher does not know if delivery succeeded — fire-and-forget
- For delivery acknowledgment, add a `CompletableFuture` return type and collect all futures before returning

---

## 🔧 What Would You Do Differently?

1. **Message filtering by predicate** — Add `subscribe(topic, subscriber, Predicate<Message>)`. Store `FilteredSubscriber { subscriber, predicate }` in the list. `publish()` calls `predicate.test(message)` before invoking the subscriber.

2. **Ordered delivery per subscriber** — For each subscriber, maintain a dedicated single-threaded `ExecutorService`. Messages for that subscriber are always processed in order, regardless of how fast `publishAsync()` fires.

3. **Dead letter queue (DLQ)** — When a subscriber throws after N retries, route the message to a DLQ topic. Other services can subscribe to the DLQ for alerting or manual replay.

4. **Topic wildcards** — Subscribe to `"model.*"` to receive `model.trained`, `model.failed`, etc. Implement via a `matchesWildcard(pattern, topic)` check during publish. `ConcurrentHashMap` keyed on patterns, with regex compiled once per pattern.

5. **Backpressure on async** — Replace unbounded `ExecutorService` internal queue with a `BlockingQueue` of bounded size. When full, `publishAsync()` blocks (backpressure applied to publisher) or drops + increments a dropped-message counter.

---

## 🧩 Interview Q&As

**Q1: Why `CopyOnWriteArrayList` instead of `ArrayList + synchronized` block around every access?**

`ArrayList + synchronized` requires the lock on every read (publish iteration) AND every write (subscribe/unsubscribe). `publish()` could be called thousands of times per second. Under a synchronized block, every publish call acquires the lock — high contention if many publishers are active. `CopyOnWriteArrayList` makes iteration lock-free: publish never acquires a lock. The tradeoff is that `subscribe()` copies the array on every write. Since subscribes happen once at startup vs publishes happening constantly, `CopyOnWriteArrayList` is optimal for this access pattern.

**Q2: What's the difference between `publish()` (sync) and `publishAsync()` (async)? When would you pick each?**

`publish()` calls each subscriber inline — publisher waits for all subscribers to return. Use when you need delivery acknowledgment or when subscribers are fast (logging, in-memory state updates). `publishAsync()` submits each subscriber as a pool task and returns immediately — publisher doesn't wait. Use when subscribers may be slow (HTTP calls, DB writes) and publisher latency matters. Downside: delivery is eventually consistent; publisher can't tell if delivery failed.

**Q3: What happens if two threads call `subscribe("model.trained", s)` simultaneously on a brand-new topic?**

`computeIfAbsent` is atomic — it's one CAS on the `ConcurrentHashMap`. Only one thread creates the `CopyOnWriteArrayList`; the other gets the already-created list. Then `add()` on `CopyOnWriteArrayList` serializes the two inserts internally. Result: both subscribers are added correctly. No race, no lost subscriber.

**Q4: A subscriber is added after `publishAsync()` is called for a message. Does it receive that message?**

No. `publishAsync()` takes a snapshot of the subscriber list at call time (the CopyOnWriteArrayList snapshot). The newly added subscriber only receives future messages. This is correct event-bus semantics — "subscribe before publish" is a contract the caller must ensure.

**Q5: How would you add message replay for new subscribers?**

Store messages in a `ConcurrentLinkedDeque<Message>` per topic (bounded by maxHistory). On `subscribe()`, after adding the subscriber, iterate the stored messages and call `sub.onMessage()` for each. This is the "replay on subscribe" pattern — same idea as Kafka's `auto.offset.reset=earliest`.

---

## 🧾 TL;DR

`ConcurrentHashMap<topic, CopyOnWriteArrayList<Subscriber>>` is the whole data structure. `computeIfAbsent` on subscribe prevents race on new topics. `publish()` iterates the snapshot — concurrent subscribe/unsubscribe never causes CME. `publishAsync()` submits each subscriber as a pool task — slow sub never blocks others. One defensive try-catch per subscriber. Observer is the only genuine pattern here.

---

## 🗺️ Patterns Used

- **Observer** — `Subscriber.onMessage()` is the observer; `EventBus` is the subject; publishers and subscribers are fully decoupled by topic name. See `LLD/DesignPatterns/02-observer.md`

---

## 🖊️ Full Implementation

### Message.java

```java
import java.util.UUID;

public class Message {

    private final String id;
    private final String topic;
    private final Object payload;
    private final long timestamp;

    public Message(String topic, Object payload) {
        this.id = UUID.randomUUID().toString();
        this.topic = topic;
        this.payload = payload;
        this.timestamp = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public Object getPayload() {
        return payload;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "Message{id=" + id + ", topic=" + topic + ", payload=" + payload + "}";
    }
}
```

### Subscriber.java

```java
@FunctionalInterface
public interface Subscriber {
    // Observer — called by EventBus whenever a message is published to a subscribed topic
    void onMessage(Message message);
}
```

### EventBus.java

```java
public interface EventBus {
    void subscribe(String topic, Subscriber subscriber);
    void unsubscribe(String topic, Subscriber subscriber);
    // Synchronous — publisher blocks until all subscribers have processed the message
    void publish(String topic, Object payload);
    // Asynchronous — publisher returns immediately; subscribers notified in thread pool
    void publishAsync(String topic, Object payload);
    void shutdown();
}
```

### InMemoryEventBus.java

```java
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class InMemoryEventBus implements EventBus {

    // Two-level thread-safe structure:
    //   Level 1 — ConcurrentHashMap: concurrent subscribe/publish on DIFFERENT topics are lock-free
    //   Level 2 — CopyOnWriteArrayList: iteration during publish is lock-free; writes copy the array
    private final ConcurrentHashMap<String, CopyOnWriteArrayList<Subscriber>> topics =
        new ConcurrentHashMap<>();

    private final ExecutorService asyncExecutor;

    public InMemoryEventBus(int asyncThreads) {
        this.asyncExecutor = Executors.newFixedThreadPool(asyncThreads);
    }

    @Override
    public void subscribe(String topic, Subscriber subscriber) {
        // computeIfAbsent is atomic — safe when two threads subscribe on a brand-new topic simultaneously
        topics.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<>())
              .add(subscriber);
    }

    @Override
    public void unsubscribe(String topic, Subscriber subscriber) {
        CopyOnWriteArrayList<Subscriber> subs = topics.get(topic);
        if (subs != null) {
            subs.remove(subscriber);
        }
    }

    @Override
    public void publish(String topic, Object payload) {
        Message message = new Message(topic, payload);
        // getOrDefault — no NPE if topic has zero subscribers
        for (Subscriber sub : topics.getOrDefault(topic, new CopyOnWriteArrayList<>())) {
            // Defensive try-catch — one bad subscriber must not stop delivery to others
            try {
                sub.onMessage(message);
            } catch (Exception e) {
                // Production: log.warn("Subscriber threw on topic {}", topic, e);
            }
        }
    }

    @Override
    public void publishAsync(String topic, Object payload) {
        Message message = new Message(topic, payload);
        for (Subscriber sub : topics.getOrDefault(topic, new CopyOnWriteArrayList<>())) {
            // Each subscriber gets its own task — one slow subscriber never blocks others
            asyncExecutor.submit(() -> {
                try {
                    sub.onMessage(message);
                } catch (Exception e) {
                    // Production: log.warn("Async subscriber threw on topic {}", topic, e);
                }
            });
        }
    }

    @Override
    public void shutdown() {
        asyncExecutor.shutdown();
    }
}
```

### Usage Example

```java
public class EventBusExample {

    public static void main(String[] args) throws InterruptedException {
        EventBus bus = new InMemoryEventBus(4);

        // Subscribe with lambda — @FunctionalInterface enables this
        bus.subscribe("model.trained", msg ->
            log.info("[MetricsCollector] received: {}", msg.getPayload())
        );

        bus.subscribe("model.trained", msg ->
            log.info("[Notifier] sending email for: {}", msg.getPayload())
        );

        bus.subscribe("job.failed", msg ->
            log.info("[Alerting] job failed: {}", msg.getPayload())
        );

        // Publish synchronously — both subscribers called inline before publish returns
        bus.publish("model.trained", "gpt-ft-v2");

        // Publish asynchronously — publisher returns immediately
        bus.publishAsync("job.failed", "training-run-007");

        Thread.sleep(500);   // wait for async delivery to complete
        bus.shutdown();
    }
}
```

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 2026 | Note created — TrueFoundry LLD prep. One-file format. All Java inline. |
