# 35 — Retry & Exponential Backoff Patterns

## 📖 What is Exponential Backoff?

**Full form:** Exponential Backoff — a retry strategy where failed requests are retried at progressively longer intervals (1s, 2s, 4s, 8s, 16s...) instead of immediately or at fixed intervals.

**Simple analogy:** Imagine you're trying to book a concert ticket during a flash sale. The website crashes. If you and 1 million other users all retry immediately, you create a "thundering herd" — everyone hammers the server at once, keeping it dead. Exponential backoff is like customers naturally spreading out: some retry after 1 second (and get through), some retry after 2 seconds, some after 4 seconds. By the time slow retriers hit the server, it's recovered. Backoff = breathing room for recovery.

**Core principle:** When a service is overloaded or temporarily down, immediate retries make it worse (more traffic on a struggling system). Exponential backoff staggeres retry attempts, giving the service time to recover. Jitter (randomness) ensures multiple clients don't all retry at the exact same interval.

**Why it matters in system design:** Without backoff, transient failures cascade into permanent outages. With backoff, a 5-second outage stays a 5-second outage. Without it, becomes a 5-minute one due to retry storms.

---

## 🎯 Why This Matters

- **Problem:** Services fail. If every client retries immediately, you turn a brief blip into a systemic collapse.
- **Interview signal:** "Your rate limiter is shedding requests. What's your retry strategy?" — this determines if your system thrashes or recovers.
- **Senior expectation:** You know not just that retries exist, but when to retry, how often, and what NOT to retry.

---

## 🧠 The Mental Model

Imagine a restaurant kitchen that goes down for 30 seconds (power blip). 

**Bad retry strategy (no backoff):**
- Every waiter immediately re-submits their order tickets. Kitchen gets 500 orders at once. Still offline. Waiters retry again. By the time kitchen restarts, there are 10,000 pending orders. Chaos.

**Good retry strategy (exponential backoff):**
- Waiter submits order, gets "kitchen down" response. Waiter waits 1 second, retries — still down. Waiter waits 2 seconds, retries — still down. Waiter waits 4 seconds, retries — kitchen just came back online, success. Other waiters are at 1s and 2s marks; they arrive at staggered times, not a flood.

**The key insight:** Exponential backoff + jitter = natural load spreading. Recovery looks exponential instead of chaotic.

---

## 🎨 Visual — System Topology & Retry Flow

```
FULL SYSTEM TOPOLOGY:
┌──────────┐          ┌──────────────┐          ┌──────────┐
│ Client A │─Request─▶│   Service    │◀─Response│ Client B │
│  (retry) │          │ (degraded)   │          │ (retry)  │
└──────────┘          └──────────────┘          └──────────┘
     │                       ▲                        │
     │ Retry after 1s        │ Overload              │ Retry after 2s
     │ (gets rejected)       │ (recovers slowly)    │ (gets through)
     └───────────────────────┘                      └────────────────┘

WITHOUT EXPONENTIAL BACKOFF (Thundering Herd):
Time │ Requests
0-1s │ ████████████ (retry flood, service still down)
1-2s │ ████████████ (retry flood, service still down)
2-3s │ ████████████ (retry flood, server overloaded by retries)
3-4s │ ████       (finally recovers? no, killed by incoming retries)

WITH EXPONENTIAL BACKOFF + JITTER (Graceful Recovery):
Time │ Requests
0-1s │ ██████████    (some clients retry at 1s mark)
1-2s │ ████          (fewer retry at 2s; service catching breath)
2-3s │ ██            (fewer still at 4s)
3-4s │ ███████       (service recovered! new traffic + stragglers)
4-5s │ ████████      (back to normal, no spike)

KEY INVARIANT:
   Exponential backoff spreads retries over time.
   Jitter prevents synchronized retry storms.
   Service gets recovery window instead of drowning in retries.
```

---

## ⚙️ How It Actually Works

**Steps:**

1. **First attempt:** Client sends request. Service responds (success) or fails with retryable error (503, timeout, connection refused).

2. **Identify retryable vs non-retryable errors:**
   - Retryable: 503 Service Unavailable, 429 Too Many Requests, timeout, connection refused, temporarily DNS resolution failure
   - Non-retryable: 400 Bad Request, 401 Unauthorized, 404 Not Found, 500 Internal Server Error (usually), invalid input

3. **Calculate backoff:** attempt_number starts at 0. Wait = min(max_wait, base_wait * (2 ^ attempt_number)) + random_jitter

4. **Add jitter:** random_jitter = random(0, max_jitter) prevents thundering herd when many clients use identical backoff formula

5. **Retry:** Re-send request after backoff delay. Increment attempt counter. If success, done. If retryable failure, go to step 3. If max retries reached, fail.

6. **Fallback:** Dead-letter the request (log it, queue for manual review) or return error to caller.

**Code example:**

```java
public class RetryableClient {
    private static final int MAX_RETRIES = 5;
    private static final int BASE_WAIT_MS = 100;
    private static final int MAX_WAIT_MS = 32000; // 32 seconds max
    private static final Random RANDOM = new Random();
    
    public String fetchWithRetry(String url) throws Exception {
        int attempt = 0;
        Exception lastException = null;
        
        while (attempt < MAX_RETRIES) {
            try {
                // Attempt the request
                HttpResponse response = httpClient.get(url);
                
                // Success
                if (response.getStatus() == 200) {
                    return response.getBody();
                }
                
                // Check if retryable
                if (isRetryableStatus(response.getStatus())) {
                    lastException = new Exception("Status: " + response.getStatus());
                } else {
                    // Non-retryable error — fail immediately
                    throw new Exception("Non-retryable status: " + response.getStatus());
                }
            } catch (IOException e) {
                // Network errors are retryable
                lastException = e;
            } catch (Exception e) {
                // Other errors are non-retryable
                throw e;
            }
            
            // Calculate backoff with jitter
            long backoffMs = calculateBackoff(attempt);
            System.out.println("Attempt " + attempt + " failed. Retrying in " + backoffMs + "ms");
            Thread.sleep(backoffMs);
            attempt++;
        }
        
        // Max retries exhausted
        throw new Exception("Failed after " + MAX_RETRIES + " retries", lastException);
    }
    
    private long calculateBackoff(int attempt) {
        // Exponential: 100ms, 200ms, 400ms, 800ms, 1600ms, 3200ms
        long exponentialWait = BASE_WAIT_MS * (1L << attempt); // 2^attempt
        long cappedWait = Math.min(MAX_WAIT_MS, exponentialWait);
        
        // Add random jitter: ±10% of capped wait
        int jitterMs = RANDOM.nextInt((int)(cappedWait / 10));
        return cappedWait + jitterMs;
    }
    
    private boolean isRetryableStatus(int status) {
        // 429 Too Many Requests, 503 Service Unavailable, 504 Gateway Timeout
        return status == 429 || status == 503 || status == 504;
    }
}
```

---

## 🏢 Real World — Where Companies Use This

- **Uber Driver App (retry on surge pricing service timeouts):** When surge pricing service is temporarily slow, driver app retries with exponential backoff. Without backoff, all drivers would hammer it simultaneously. With backoff, the service recovers naturally.

- **Stripe (API client retries on 503):** Stripe's official client libraries implement exponential backoff for transient failures. A merchant's payment retry uses this under the hood — if Stripe's payment processor is momentarily overloaded, backoff buys time for recovery instead of cascading failures.

- **Netflix (microservice-to-microservice retries):** Netflix's Hystrix (circuit breaker) integrates exponential backoff. When a service is slow, Hystrix backs off before retrying. Prevents retry storms that would amplify latency spikes.

- **Razorpay (webhook retry delivery):** When a merchant's webhook endpoint is slow, Razorpay retries with exponential backoff (1s, 2s, 4s, 8s...). Merchants see reliable delivery without overwhelming their servers.

- **AWS SDK (built-in exponential backoff):** AWS SDKs for Java, Python, Node.js include exponential backoff by default on transient errors. DynamoDB requests, S3 uploads — all benefit from standard backoff without manual configuration.

---

## 🧭 When to Use vs When NOT to Use

| Use this when | Do NOT use when |
|---|---|
| Calling a service that might be temporarily slow or down | Error is permanent/non-retryable (400, 401, 404) — immediate failure is better |
| Queuing retry logic (SQS, Kafka, dead-letter queues) | You're retrying inside a tight loop in client code without any delay (you'll blow through your retry budget) |
| Handling burst traffic or thundering herd scenarios | You're retrying deterministic failures (invalid input, authentication) — wastes time and resources |
| Building resilient microservice calls | Your service is already failing due to cascading overload from retries — add circuit breaker FIRST, then backoff |

**The common mistake:** Implementing retry logic without knowing which errors are retryable. You end up retrying 400 Bad Request (non-retryable) 5 times, wasting 30+ seconds, when the answer is "invalid input, fix it."

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Natural recovery from transient outages; prevents retry storms and cascading failures; distributed load spreading without explicit rate limiting |
| **You lose** | Increased latency for the initial caller (retry waits = slower response time); complexity in code (need to track attempt counts, handle partial success) |
| **Failure mode** | If max retries are too high or backoff is too slow, clients hang waiting for recovery. If backoff is too aggressive, you never retry fast enough. Tune for your SLA. |

---

## 🔬 Interview Q&As

### Q: "Why not just retry immediately? Why wait with exponential backoff?"

> Immediate retries amplify the problem. If service is down due to overload, immediate retries add MORE load, keeping it down longer. Exponential backoff gives the service breathing room. By the time you retry (4s later), the service has recovered. Without backoff, the 4 immediate retries might keep it dead for 30s.

### Q: "How do you choose the base wait and max wait times? 100ms vs 1s?"

> Base wait reflects your typical transient failure recovery time. 100ms for in-memory service failures (cache node reboot). 1-2s for database failover. Max wait prevents absurdly long waits — 32-60s is typical. Choose based on your SLA. If you can't tolerate waiting 30s for a retry, lower max_wait.

### Q: "What's the difference between exponential backoff and jitter? Why do we need both?"

> Exponential backoff (1s, 2s, 4s) staggeres retries over time. Jitter (random ±10% variation) prevents SYNCHRONIZED retries. Imagine 1 million clients all retrying at exactly 4 seconds — boom, thundering herd. Jitter desynchronizes them (4000ms, 4020ms, 3990ms...). Together, they spread load AND give recovery time.

### Q: "What errors should we retry? Should we retry 500 errors?"

> 500 Internal Server Error is typically non-retryable (programmer bug, not transient failure). Don't retry. Immediate failure tells the developer to fix the code. But 503 Service Unavailable IS retryable (temporary overload). Check the HTTP status code: retry 429, 503, 504, 408 (timeout), connection errors. Don't retry 4xx client errors.

### Q: "Our service has a 5-minute SLA. Should we retry for 5 minutes?"

> No. If initial request + 5 retries take 5 minutes, your user waited 5 minutes. Better to fail after 30s and let them know. Retry windows should be a small fraction of SLA, not the entire SLA. For 5-min SLA, target 30-60s total retry time. If it's still down after that, fail and escalate to team.

### Q: "What if the service is down for 10 minutes? Exponential backoff maxes out at 32s. We stop retrying."

> Correct. That's not a bug, that's design. If service is down 10 minutes, retrying won't help. Instead: (1) fail the request and return error to caller. (2) Log to dead-letter queue. (3) Human team gets paged. (4) Offline processing retries later (hours). Exponential backoff handles TRANSIENT failures (seconds). For sustained outages, you need human intervention.

### Q: "How does exponential backoff interact with idempotency keys?"

> Exponential backoff retries the same request (same idempotency key). Service sees the retry, recognizes the same key, returns cached response instead of re-executing. Key insight: backoff + idempotency = safe retries. Without idempotency, backoff can cause duplicate charges or duplicate messages. Always pair them.

---

## 🧾 TL;DR

> "We retry transient errors (503, timeout) with exponential backoff: 100ms, 200ms, 400ms... up to 32s. Jitter prevents thundering herd. Non-retryable errors (400, 401) fail immediately. Dead-letter queue handles permanent failures for async reprocessing."

---

## 🔗 Related Concepts

- **Idempotency (04):** Pair exponential backoff with idempotency keys to ensure safe retries
- **Circuit Breaker (20):** Prevent retries from hitting a broken service; detect failure early
- **Backpressure (10):** Backoff is a form of backpressure — reduce load when downstream is struggling
- **Message Queues (19):** Kafka/RabbitMQ include retry/backoff mechanisms natively for reliability

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **"Exponential Backoff And Jitter" — AWS Architecture Blog** (aws.amazon.com) | Deep dive on why jitter matters; includes comparison of different jitter strategies. CAP shows that exponential backoff is enough, but AWS shows why jitter is critical. | ~10 min |
| **"Retry Storms and Thundering Herds" — Martin Fowler** (martinfowler.com/articles) | System-level analysis of how retry storms cascade through microservices. Detailed examples from real outages. | ~12 min |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 25, 2026 | Initial creation. Added exponential backoff vs immediate retry comparison, jitter explanation, code example with Java HttpClient, retryable vs non-retryable error classification. Real-world examples (Uber, Stripe, Netflix, Razorpay, AWS). Seven Q&As covering backoff tuning, error classification, idempotency interaction, dead-letter queues. |
