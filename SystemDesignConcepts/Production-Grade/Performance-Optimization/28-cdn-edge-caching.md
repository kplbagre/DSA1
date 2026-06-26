# CDN — Content Delivery Network & Edge Caching

> A CDN (Content Delivery Network) is a geographically distributed system of servers that cache content near end-users. When User A in India requests a video, CDN serves from an edge location in Mumbai (50ms latency) instead of fetching from origin in US (200ms+ latency). At SDE 3: you must know how CDN reduces latency, when to use it, and the mechanics of cache invalidation.

---

## 🎯 Why This Matters

Video platforms (YouTube, Netflix), e-commerce (Amazon), and social media (Facebook) serve billions of requests/day. Origin servers are far from users, causing latency. CDN solves this by caching content at edge locations globally. Without CDN, user in Tokyo waiting 500ms for image from US server. With CDN, user gets image from Tokyo edge in 50ms. CDN is non-negotiable at global scale. In interviews, candidates often think CDN is just "copy content everywhere" — you'll explain cache invalidation, TTL, and edge location selection.

---

## 🧠 The Mental Model

Imagine a bookstore chain. Without a CDN:
- Every customer orders books from the headquarters warehouse in California.
- Customer in Tokyo waits 30 days for shipping (slow).

With a CDN:
- Headquarters stocks popular books in local warehouses in Tokyo, London, São Paulo.
- Customer in Tokyo gets book in 1 day from local warehouse.
- Every day, a truck delivers new books to local warehouses (cache refreshed).
- Some books have an expiry date (TTL); if not sold, discard (cache invalidation).

**The key insight:** CDN trades **origin server bandwidth** for **geographic spread**. Content is cached close to users; updates propagate to edges on-demand (TTL) or explicitly (purge).

---

## 🎨 Visual — CDN Architecture

### Full System Topology — Where CDN Sits

```
┌──────────────────────────────────────────────────────────────┐
│ CLIENT (User in Tokyo)                                       │
│ Requests: GET /videos/song.mp4                               │
└──────────────────────────────────────────────────────────────┘
    ↓
┌──────────────────────────────────────────────────────────────┐
│ CDN EDGE LOCATION (Tokyo)                                    │
│ ┌──────────────────────────────────────────────────────┐    │
│ │ [Check cache: is song.mp4 stored here?]             │    │
│ │ ✅ Cache hit (>90% of requests) → return to client  │    │
│ │ ❌ Cache miss → fetch from origin                    │    │
│ └──────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────┘
    ↓ (only on cache miss)
┌──────────────────────────────────────────────────────────────┐
│ CDN ORIGIN SERVER (e.g., US)                                 │
│ (Your application server or S3 bucket)                       │
│ ┌──────────────────────────────────────────────────────┐    │
│ │ [Return video: song.mp4]                            │    │
│ │ Response headers:                                    │    │
│ │   Cache-Control: max-age=86400 (24 hours TTL)      │    │
│ │   ETag: "abc123def456" (for invalidation)          │    │
│ └──────────────────────────────────────────────────────┘    │
└──────────────────────────────────────────────────────────────┘
    ↓ (response flows back through edge)
┌──────────────────────────────────────────────────────────────┐
│ CDN EDGE LOCATION (Tokyo) — CACHES RESPONSE                 │
│ Now stores: song.mp4 (expires in 24 hours)                   │
│ Future requests from Japan hit cache (no origin query)       │
└──────────────────────────────────────────────────────────────┘

GLOBAL TOPOLOGY (Multiple regions):
┌────────────────────────────────────────────────────────────────┐
│         CDN EDGE LOCATIONS (geographically distributed)        │
│                                                                │
│ Tokyo          London        São Paulo         Sydney          │
│ [Cache]        [Cache]       [Cache]          [Cache]         │
│   ↓              ↓              ↓                ↓             │
│  Users in       Users in      Users in        Users in        │
│  Japan/East     Europe        Latin America   Oceania         │
│  Asia                                                         │
│                                                                │
│   All edges connect to ORIGIN SERVER (US)                    │
└────────────────────────────────────────────────────────────────┘

KEY INVARIANT:
   CDN sits between clients and origin.
   Each edge location maintains its own cache.
   Cache hits serve instantly; misses fetch from origin.
   TTL determines when edge discards cached content.
```

### Component Detail — Cache Hit/Miss & Invalidation

```
CACHE HIT vs MISS:

┌─────────────────────────────────────────────────────┐
│ Client request for /videos/song.mp4                │
└─────────────────────────────────────────────────────┘
    ↓
┌─────────────────────────────────────────────────────┐
│ Edge location checks:                               │
│ 1. Is file in cache?                               │
│ 2. Has cache expired (TTL)?                        │
└─────────────────────────────────────────────────────┘
    ↓
┌──────────────────────────────────────────────────────┐
│ ✅ CACHE HIT                                         │
│ File found + not expired                            │
│ Response: 200 OK (from cache, via X-Cache header)  │
│ Latency: <50ms (from edge)                         │
└──────────────────────────────────────────────────────┘

vs

┌──────────────────────────────────────────────────────┐
│ ❌ CACHE MISS                                        │
│ File not found OR expired                           │
│ Edge queries origin: GET /videos/song.mp4           │
│ Origin returns: 200 OK + Cache-Control header       │
│ Edge caches + responds to client                    │
│ Latency: 200-500ms (from origin + edge)            │
└──────────────────────────────────────────────────────┘


CACHE INVALIDATION STRATEGIES:

1. TTL (Time-To-Live) — Automatic expiry:
   ┌─────────────────────────────────────────┐
   │ Edge receives from origin:               │
   │ Cache-Control: max-age=86400             │
   │ (valid for 24 hours)                    │
   │ After 24h: cache entry deleted           │
   │ Next request: cache miss → fetch origin │
   └─────────────────────────────────────────┘
   Problem: If origin updates, users still see old version until TTL expires.
   Solution: Short TTL for frequently-updated content (1 hour).


2. EXPLICIT PURGE — On-demand invalidation:
   ┌─────────────────────────────────────────┐
   │ When you update content (new video):    │
   │ Call CDN API: PURGE /videos/song.mp4    │
   │ All edge locations delete from cache    │
   │ Next request: cache miss → fetch new    │
   │ Latency: ~1-2 min to propagate globally│
   └─────────────────────────────────────────┘
   Tradeoff: Immediate freshness vs API calls + cost.


3. ETAG / CONDITIONAL FETCH:
   ┌─────────────────────────────────────────┐
   │ Origin sends ETag with content:         │
   │ ETag: "abc123"                         │
   │ If content updates: new ETag: "xyz789" │
   │ Edge caches: content + ETag             │
   │ Client requests with If-None-Match      │
   │ If ETags differ: edge fetches new       │
   │ If ETags match: 304 Not Modified        │
   │ Bandwidth saved (no full content)       │
   └─────────────────────────────────────────┘


CACHE HIERARCHY (example: Netflix):
┌──────────────────────────────────┐
│ Client browser cache (1 day TTL) │
└──────────────────────────────────┘
    ↓
┌──────────────────────────────────┐
│ CDN Edge cache (3-7 days TTL)   │
│ (Cloudflare, Akamai, CloudFront)│
└──────────────────────────────────┘
    ↓
┌──────────────────────────────────┐
│ Origin server (S3, API server)   │
└──────────────────────────────────┘

Each layer reduces traffic to lower layers.
>90% of requests hit CDN edge (browsers cache locally).
<10% hit origin.
Origin bandwidth cost: ~90% reduction.

KEY INVARIANT:
   Cache hit rate = (hits) / (hits + misses)
   Good CDN: >90% hit rate for static content
   TTL tradeoff: short TTL = fresh + cache miss cost
   Purge is expensive: use sparingly, coordinate with origin updates
```

---

## ⚙️ How It Actually Works

**Steps in plain English:**

1. **Client requests content** via CDN URL (e.g., `https://cdn.example.com/videos/song.mp4`).
2. **CDN routing layer** directs request to nearest edge location (based on geography, latency).
3. **Edge checks cache** — is this file stored locally?
4. **If cache hit** — return content immediately from edge (50-100ms latency).
5. **If cache miss** — edge fetches from origin server (via private CDN network).
6. **Origin returns content** with Cache-Control headers (TTL, cache directives).
7. **Edge caches** the content locally (for future requests).
8. **Edge returns** content to client.
9. **Browser caches** content as well (additional layer).
10. **When content expires** (TTL) or is explicitly purged, edge discards from cache.

```java
// Origin Server (Serves content with cache headers)

@RestController
@RequestMapping("/videos")
public class VideoController {
    @Autowired
    private S3Service s3Service;

    // Step 1 — Client requests video
    @GetMapping("/{videoId}")
    public ResponseEntity<?> getVideo(@PathVariable String videoId) {
        // Step 6 — Fetch from S3 (or origin storage)
        Video video = s3Service.getVideo(videoId);

        // Step 6 — Set cache headers for CDN
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS)
                .noTransform()
                .mustRevalidate())
            // Cache-Control: max-age=604800 (7 days)
            // This tells CDN: cache for 7 days
            .header("CDN-Cache-Control", "public, max-age=604800")
            // This header is specific to CDN (separate from browser cache)
            .eTag(video.getETag())
            // ETag for invalidation/conditional requests
            .body(video.getContent());
    }

    // Alternative: purge endpoint (for admin)
    @PostMapping("/{videoId}/purge")
    @Secured("ROLE_ADMIN")
    public ResponseEntity<?> purgeCache(@PathVariable String videoId) {
        // Step 10 — Manually invalidate CDN cache
        cdnService.purgeCache("/videos/" + videoId);
        return ResponseEntity.ok("Cache purged");
    }
}

// CDN Integration (Cloudflare, AWS CloudFront, Akamai)

@Service
public class CdnService {
    private final CloudfrontClient cloudfrontClient;

    // Step 2-4 — Client requests through CDN
    // (This happens transparently; client doesn't know if edge or origin)

    // Step 10 — Explicit purge (invalidation)
    public void purgeCache(String path) {
        // Step 10 — Create invalidation request
        InvalidationBatch invalidationBatch = InvalidationBatch.builder()
            .paths(InvalidationBatchPathList.builder()
                .items(path)
                .quantity(1)
                .build())
            .callerReference(UUID.randomUUID().toString())
            .build();

        CreateInvalidationRequest invalidationRequest = CreateInvalidationRequest.builder()
            .distributionId(DISTRIBUTION_ID)
            .invalidationBatch(invalidationBatch)
            .build();

        // Step 10 — Trigger purge across all edge locations
        cloudfrontClient.createInvalidation(invalidationRequest);
        // Propagates to all edges within 1-2 minutes
    }
}

// CDN Edge Behavior (Conceptual — handled by CDN provider)

// Pseudocode: How edge caches content

class CDNEdge {
    private Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public ResponseEntity<?> serveContent(String path) {
        // Step 3 — Check cache
        CacheEntry entry = cache.get(path);

        if (entry != null && !entry.isExpired()) {
            // Step 4 — Cache hit
            return ResponseEntity.ok()
                .header("X-Cache", "HIT")
                .header("Age", entry.getAge())
                .body(entry.getContent());
        }

        // Step 5 — Cache miss: fetch from origin
        ResponseEntity<?> originResponse = fetchFromOrigin(path);

        // Step 6 — Parse cache headers
        String cacheControl = originResponse.getHeaders().get("Cache-Control").get(0);
        long maxAge = parseTTL(cacheControl);

        // Step 7 — Store in cache
        cache.put(path, new CacheEntry(
            originResponse.getBody(),
            System.currentTimeMillis(),
            maxAge
        ));

        // Step 8 — Return to client
        return ResponseEntity.ok()
            .header("X-Cache", "MISS")
            .body(originResponse.getBody());
    }

    class CacheEntry {
        byte[] content;
        long createdAt;
        long ttlSeconds;

        boolean isExpired() {
            long age = (System.currentTimeMillis() - createdAt) / 1000;
            return age > ttlSeconds;
        }

        long getAge() {
            return (System.currentTimeMillis() - createdAt) / 1000;
        }
    }
}

// Client-side (JavaScript) — additional caching layer

// Step 9 — Browser caches via HTTP headers
// No code needed; browsers respect Cache-Control automatically

// But you can control Service Worker caching:
self.addEventListener('fetch', event => {
    event.respondWith(
        caches.open('v1').then(cache => {
            return cache.match(event.request).then(response => {
                // Step 9 — Browser cache hit
                return response || fetch(event.request).then(response => {
                    // Step 9 — Add to browser cache
                    cache.put(event.request, response.clone());
                    return response;
                });
            });
        })
    );
});
```

### What is CloudFront / Cloudflare, and why does it fit here?

CloudFront is **AWS's CDN service** that caches content at edge locations globally. Cloudflare is an **independent CDN** with similar functionality. Both manage geographic distribution, cache invalidation, and DDoS protection. In an interview, if asked: *"CloudFront is AWS's CDN that caches your S3 content at 200+ edge locations worldwide. When a user in Tokyo requests a video, CloudFront serves from Tokyo edge (50ms). We use CloudFront because it integrates with AWS and handles cache invalidation via API."*

---

## 🏢 Real World — Where Companies Use This

- **Netflix (custom CDN + partnerships):** Netflix content cached at ISP edge locations and Cloudflare CDN. Most users get bitrate-adaptive video from edge (cached after first 10 requests). Reduces origin bandwidth cost by 95%.
- **Amazon (CloudFront for products):** Product images cached globally. When shopping, images load from nearby edge. Cache invalidation on inventory update — when item stock changes, image refreshed.
- **Shopify (Fastly CDN):** Store assets (JavaScript, CSS, images) cached at Fastly edges. When Shopify merchant updates store design, Shopify automatically purges affected files. Cache hit rate >95% for static assets.
- **GitHub (GitHub Pages + Cloudflare):** GitHub Pages content served via CDN. Blog posts, documentation cached globally. Invalidation on commit (push) — GitHub automatically purges old version.
- **Facebook / Meta (custom CDN):** Meta operates own CDN ("Network of CDN nodes"). Facebook images, videos cached at network edges (data centers worldwide). TTL varies: photos 30 days, videos 1 year. Purge on user delete.

---

## 🧭 When to Use vs When NOT to Use

| Use CDN when | Do NOT use when |
|---|---|
| Content is static or changes infrequently | Content changes frequently (every minute) |
| Users are globally distributed | Users are in one region (CDN adds latency) |
| Bandwidth cost is concern (images, videos) | Low-bandwidth API (text responses) |
| You have origin bandwidth limits | Origin server has unlimited bandwidth |
| You want DDoS protection / security | You need to hide origin IP (CDN exposes it if not configured) |

**The common mistake:** Caching dynamic content (personalized HTML) at CDN. CDN should cache static assets (JS, CSS, images, videos). Dynamic responses should be generated fresh at origin (or cached with very short TTL + cache-busting keys).

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Reduced latency (content served from nearby edge). Reduced origin bandwidth cost (>80% fewer requests reach origin). DDoS protection (CDN absorbs attacks). Automatic failover (if edge fails, other edges compensate). Global redundancy. |
| **You lose** | Cost (CDN providers charge per GB served, ~$0.02-0.10/GB). Complexity (cache invalidation is hard; stale content possible). Vendor lock-in (switching CDNs means updating DNS, cache rebuilds). Cache poisoning risk (if origin sends malicious content, CDN caches and spreads). |
| **Failure mode** | CDN edge fails → traffic reroutes to next-nearest edge or origin (graceful degradation). Cache is stale (TTL too long) → users see old content. Invalidation fails → purge doesn't propagate to all edges. Mitigation: short TTL for dynamic content, monitor cache hit rate, implement versioning (change URLs when content updates). |

---

## 🔬 Interview Q&As

### Q: "You have a video that's 1GB. After uploading, 1M users in US download it. Without CDN, origin serves 1TB of data (1M × 1GB = cost $20K). How does CDN help?"

> With CDN: first user downloads from origin (1GB served). CDN edge in US now caches. Next 999,999 users download from edge (no origin traffic). Net: origin serves 1GB; CDN edge serves 999GB. Cost: ~$1 (CDN bandwidth cheaper). 99.9% bandwidth reduction. Tradeoff: CDN costs money upfront, but saves origin costs. ⭐ **Tier 2 — Cost analysis**

### Q: "You update a video but don't purge CDN cache. Users still see old video for 24 hours (your TTL). How do you fix this?"

> Two approaches: (1) Purge immediately when uploading new video (API call to CDN). (2) Use versioning: instead of `/videos/song.mp4`, use `/videos/song-v2.mp4`. CDN has no cache for v2, so cache miss → fetches new version. Downside: old URLs still point to old cache. Better: use query params: `/videos/song.mp4?v=2`. ⭐ **Tier 2 — Cache invalidation**

### Q: "Your origin is in US. User in Tokyo requests video. Latency: 200ms from origin. With CDN edge in Tokyo: 50ms. But edge has cache miss (first request for that video). Total latency: 200ms (edge fetches from origin) + network propagation. Isn't that the same?"

> Yes, first request is slow. But subsequent requests hit cache. If Tokyo requests same video 100 times, 1 cache miss + 99 cache hits = average ~51ms (vs 200ms without CDN). CDN benefits scale with popularity (hot content cached longer). Cold content (unpopular videos) still fetches from origin but doesn't hurt because few people want it. ⭐ **Tier 2 — Performance**

### Q: "How do you ensure sensitive user data (personalized videos) isn't cached at CDN edges?"

> Don't use CDN for personalized content. Personalized videos should bypass CDN (fetch directly from origin). Set `Cache-Control: private` (browser caches, CDN doesn't) or `Cache-Control: no-cache, no-store` (no caching at all). For semi-personalized content, use `Vary` header: `Vary: Accept-Language` tells CDN to cache per language (separate cache for English vs Spanish versions). ⭐ **Tier 2 — Security**

### Q: "Your cache hit rate is 80% but you want 95%. How do you improve?"

> Check what's missing: (1) Increase TTL (longer cache retention). (2) Analyze cache misses — are unpopular files taking space? Implement LRU eviction. (3) Pre-warm cache — upload videos to edges before launch. (4) Check for cache busting headers — if origin sends `Cache-Control: no-cache` on static assets, change to `max-age=31536000`. ⭐ **Tier 2 — Optimization**

---

## 🧾 TL;DR

> "CDN caches content at geographically distributed edge locations. Users fetch from nearby edge (50-100ms) instead of distant origin (200-500ms). TTL determines cache expiry; purge invalidates manually. Trade-off: reduced latency/bandwidth cost vs cache staleness and CDN vendor costs."

---

## 🔗 Related Concepts

- **`24-api-gateway-pattern.md`** — API gateway routes to CDN for static content
- **`25-monitoring-observability-fundamentals.md`** — Monitor CDN cache hit rate, origin latency
- **`03-caching.md`** — CDN is a distributed cache layer (LRU, TTL, eviction)

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **ByteByteGo — "CDN Explained"** (YouTube) | Visual walkthrough of CDN architecture, cache invalidation strategies, geographic distribution | ~12 min |
| **Cloudflare Blog — How CDNs Work** | Real-world CDN mechanics, DDoS mitigation, cache optimization tips | ~20 min read |
| **AWS CloudFront Documentation** | CloudFront caching rules, cache behaviors, invalidation API | ~20 min read |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 25, 2026 | Created as Concept 28. Covered CDN as distributed edge caching layer, two-diagram topology (global edge distribution), cache hit/miss mechanics, TTL vs explicit invalidation, ETag revalidation, bandwidth and latency trade-offs. |
