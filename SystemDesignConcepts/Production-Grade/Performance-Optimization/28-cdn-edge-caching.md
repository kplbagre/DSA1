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

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|------|----------------------|---------|
| **CDN (Content Delivery Network)** | A globally distributed network of servers that cache content close to end-users. Requests are routed to the nearest edge location, cutting latency from 200ms+ (origin far away) to 50ms (edge nearby). | Cloudflare, AWS CloudFront, Akamai. A video hosted in the US is served to Tokyo users from a Tokyo edge server. |
| **Edge Location / PoP (Point of Presence)** | A single CDN server or small cluster in a specific city or region. PoP stands for Point of Presence — the physical location where the CDN has hardware. Users are routed to their nearest PoP automatically. | Cloudflare has 200+ PoPs worldwide. A user in Mumbai is routed to Cloudflare's Mumbai PoP, not to the US origin. |
| **Pull CDN** | The CDN edge starts empty. The **first user** to request a file triggers a cache miss → edge fetches from your origin → caches it for future users. Zero setup required. Cold on first hit per edge, then fast. | First Tokyo user gets `song.mp4` in 200ms (origin round-trip). Every user after gets it in 50ms from Tokyo edge cache. |
| **Push CDN** | You proactively **upload content to all edges before any user requests it** — no cold cache miss is possible. Every user gets a cache hit from request #1. Requires you to manage content lifecycle manually (when does pushed content expire?). | Before a movie premiere, push `avengers.mp4` to all 200 PoPs. When the film drops, every user globally gets a cache hit immediately — no origin storm. |
| **Cache Hit** | The requested file is found in the edge's local cache and has not yet expired (TTL is still valid). Served instantly from edge without contacting origin. | Edge has `song.mp4` (expires in 24h). User requests it 2h in: cache hit, 50ms response. |
| **Cache Miss** | The file is NOT in the edge cache — either never fetched or the TTL expired. Edge must fetch from origin. Slower: full origin round-trip. | Edge has no `song.mp4`. Fetches from US origin (200ms), caches it, returns to user. Next request is a hit. |
| **TTL (Time-To-Live)** | How long a cached file stays valid at the edge before being discarded. Set via `Cache-Control: max-age=86400` (24 hours). After TTL expires, the next request is a cache miss. | Static JS: `max-age=31536000` (1 year — file never changes). API response: `max-age=60` (refresh every minute). |
| **Cache Invalidation (Purge)** | Explicitly deleting a cached file from all edge locations **before its TTL expires** — forcing the next request to fetch fresh content from origin. Propagates to all PoPs in 1–2 minutes. | You update `logo.png`. Call CDN API: `PURGE /images/logo.png`. All edges delete it. Next user triggers a miss and fetches the new logo. |
| **Anycast** | A routing technique where the **same IP address is simultaneously announced from multiple geographic locations** via BGP. Internet routers automatically send each packet to the nearest announcing location. No DNS TTL lag on failover — BGP re-routes in seconds when an PoP goes down. | Cloudflare's IP `1.1.1.1` is served from 200+ PoPs. A Tokyo user reaches Tokyo; a London user reaches London — same IP, different physical servers. |
| **Signed URL** | A time-limited CDN URL with an HMAC signature embedded in the query params. The CDN edge validates the signature and expiry timestamp before serving the file — no round-trip to origin for authorization. Used for private paid content. | Netflix generates a signed URL for `avengers.mp4` valid for 6 hours. If a user shares the URL, it expires. The video stays cached at the edge; only the access token times out. |

---

## 🧠 Push vs Pull CDN

CDNs operate in two fundamentally different modes that determine *how* content gets to the edge.

### Pull CDN (default — CloudFront, Cloudflare, Akamai)

**How it works:** Edge starts empty. First user to request a file triggers a cache miss → edge fetches from origin → caches it. Every subsequent user gets a cache hit.

```
First request (Tokyo edge empty):
  User (Tokyo) → CDN edge (Tokyo) → MISS → Origin (US) → content
  CDN edge now caches the content for TTL duration.

Subsequent requests:
  User (Tokyo) → CDN edge (Tokyo) → HIT → user (no origin involved)
```

**Best for:** Long-tail content (millions of unique files — not all popular), or when you don't know which edges need which content.

**Downside:** First user to hit each edge gets full origin latency (cold cache). Flash releases (a movie premiere) will have many simultaneous cache misses.

### Push CDN

**How it works:** You proactively upload content to all edges *before* any user requests it. No cache miss possible — every user gets a HIT from the first request.

```
Before launch (your action):
  You → CDN API: PUSH /movies/avengers.mp4 to ALL edges
  CDN propagates file to Tokyo, London, São Paulo, Sydney edges.

At launch (user request):
  User (Tokyo) → CDN edge (Tokyo) → HIT (always) → user
```

**Best for:** Predictable popular content — movie releases, flash sale assets, software downloads where the first request wave must be fast.

**Downside:** You pay to store content on every edge (even edges no one ever requests from). Must manage lifecycle — when does the pushed content expire?

**Rule of thumb:** Start with pull CDN (zero config). Switch to push CDN for content you know will spike globally on a known date (Super Bowl, Diwali sale, game launch).

---

## 🧠 How Users Get to the Nearest Edge (Anycast & DNS Geo-Routing)

A frequently missed question: *"When I type cdn.example.com, how does my request go to the Tokyo edge and not the London edge?"*

Two mechanisms:

### 1. DNS Geo-Routing (AWS Route53, Cloudflare)

```
User in Tokyo resolves cdn.example.com:
  DNS query → Authoritative DNS server
  DNS checks: where is the user's IP coming from? (→ Japan)
  DNS returns: 203.0.113.20 (Tokyo edge IP)

User in London resolves cdn.example.com:
  DNS query → Authoritative DNS server
  DNS checks: where is the user's IP coming from? (→ UK)
  DNS returns: 198.51.100.40 (London edge IP)

Different users → different IPs → different physical servers
```

**Limitation:** DNS TTL caches the IP. If Tokyo edge fails, users in Japan may still be routed there for several minutes until DNS TTL expires.

### 2. Anycast IP (Cloudflare, Fastly)

```
Both Tokyo edge and London edge advertise the SAME IP: 104.16.0.1

User in Tokyo sends packet to 104.16.0.1:
  BGP routing: multiple data centers claim this IP
  Internet routers pick the shortest path → Tokyo data center

User in London sends packet to 104.16.0.1:
  BGP routing: shortest path → London data center

Same IP, physically different servers — routing by network topology
```

**Advantage:** Instant failover. If Tokyo goes down, BGP re-routes all Tokyo traffic to next-nearest PoP (Point of Presence) within seconds — no DNS TTL wait.

**Interview phrasing:** *"CDN providers use anycast: the same IP is advertised from hundreds of data centers. BGP routing naturally sends your packet to the nearest one — like how water flows to the lowest point. This is why Cloudflare can claim sub-20ms latency for most users globally."*

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

### Cache-Control Directives — The Complete Table

`Cache-Control` is the HTTP header that tells both browsers and CDN edges how to cache a response. Getting this wrong is how you accidentally cache private data or serve stale content for a week.

| Directive | Who it applies to | Effect |
|---|---|---|
| `max-age=3600` | Browser + CDN | Cache for 3600 seconds (1 hour). Most common. |
| `s-maxage=86400` | CDN only (shared caches) | Overrides `max-age` for CDN edges only. Browser still uses `max-age`. Use to give CDN a longer TTL than browser. |
| `public` | Browser + CDN | Explicitly allow caching by shared caches (CDN). Default for most CDNs. |
| `private` | Browser only | Browser may cache; CDN and shared caches MUST NOT cache. Use for user-personalized responses. |
| `no-cache` | Browser + CDN | Cache the content, but **must revalidate with origin before serving**. Not "don't cache" — it's "always check freshness." |
| `no-store` | Browser + CDN | **Do not cache anywhere.** Every request goes to origin. Use for sensitive data (banking, auth tokens). |
| `must-revalidate` | Browser + CDN | Once the cached copy is stale (past `max-age`), it MUST be revalidated. Never serve a stale copy even if origin is down. |
| `stale-while-revalidate=60` | Browser + some CDNs | Serve the stale copy immediately (fast), while fetching a fresh copy in the background for next requests. Accepts 60s of staleness. |
| `immutable` | Browser | Content at this URL will NEVER change. Browser skips revalidation entirely within `max-age`. Use only with versioned URLs (`/app.v2.js`). |

**⚠️ Critical interview gotcha:** `no-cache` ≠ `no-store`.
- `no-cache` = "cache it, but always ask origin if it's still fresh" (uses ETag/Last-Modified for cheap validation)
- `no-store` = "never cache, period" (every request is a full round-trip to origin)

**Practical pattern for static assets with versioned filenames** (e.g., `main.a1b2c3.js`):
```java
// Fingerprinted asset — content never changes at this URL
// Browser and CDN cache forever; deploy new URL for new content
response.setHeader("Cache-Control", "public, max-age=31536000, immutable");
```

**Practical pattern for API responses:**
```java
// Shared data: cache at CDN for 1 min, browser for 30s
response.setHeader("Cache-Control", "public, s-maxage=60, max-age=30, stale-while-revalidate=10");

// Private user data: browser only, always revalidate
response.setHeader("Cache-Control", "private, no-cache");

// Sensitive auth response: never cache
response.setHeader("Cache-Control", "no-store");
```

---

### Signed URLs — Private Content at CDN Scale

For paid or private content (Netflix movies, premium downloads), you can't make CDN URLs public — anyone with the URL would be able to download. **Signed URLs** solve this: a time-limited, HMAC-signed URL that the CDN edge validates before serving content. No separate auth round-trip needed at the edge.

**How it works:**

```
1. User clicks "Play" on a paid Netflix movie
         │
         ▼
2. Netflix backend (authenticated request):
   - Verifies user has a valid subscription
   - Generates a signed URL valid for 6 hours:
     https://cdn.netflix.com/movies/avengers.mp4
       ?Expires=1719859200
       &Signature=abc123hmacSignedWithPrivateKey
       &Key-Pair-Id=APKAJDKJSK12345
         │
         ▼
3. Signed URL returned to player
         │
         ▼
4. Player fetches video from CDN edge using signed URL
         │
         ▼
5. CDN edge validates:
   - Is Expires timestamp in the future? ✅ / ❌
   - Is Signature valid (HMAC matches)? ✅ / ❌
   If both pass → serve content directly from edge cache
   If either fails → 403 Forbidden (no origin round-trip)
```

**Result:** If someone shares the URL, it expires after 6 hours. No permanent piracy window. The private video stays in the CDN cache — it's the *access token* that expires, not the content.

```java
// CloudFront signed URL generation (Java)
public String generateSignedUrl(String videoPath, int expiryHours) {
    Date expiry = Date.from(
        Instant.now().plus(expiryHours, ChronoUnit.HOURS)
    );

    // Step 2 — build signed URL config
    SignedUrlConfig signedUrlConfig = SignedUrlConfig.builder()
        .url("https://cdn.netflix.com/" + videoPath)
        .expiration(expiry)
        .privateKeyFile(new File("/secrets/cloudfront-private.key"))
        .keyPairId("CLOUDFRONT_KEY_PAIR_ID")
        .build();

    // Step 2 — HMAC sign and return URL
    return CloudFrontUrlSigner.getSignedURLWithCannedPolicy(signedUrlConfig);
}
```

**Interview phrasing:** *"For private paid content we use CloudFront signed URLs. The backend generates a URL with a 6-hour expiry and an HMAC signature using our CloudFront key pair. The edge validates the signature before serving — zero extra auth round-trips, so latency is the same as public CDN content. The video itself stays cached at the edge; only the access token expires."*

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

### Q: "What's the difference between push and pull CDN? When do you use each?" ⭐

> Pull CDN starts empty — edge fetches from origin on the first request (cache miss), then caches for subsequent users. Zero setup, zero storage cost for cold content. Pull CDN is default for most use cases (CloudFront, Cloudflare). Push CDN: you proactively upload content to all edges before any user requests it — no cold cache miss possible. Use push when you have predictable, globally popular content on a known date: movie premiere, flash sale, game launch. If a billion users hit your CDN 30 seconds after launch, pull means 30 million simultaneous cache misses hammering origin; push means every edge already has the file. ⭐ **Tier 1 — always asked for CDN design**

### Q: "How does a CDN know to route my request to the nearest edge?" ⭐

> Two mechanisms: (1) **DNS geo-routing** — your DNS server returns a different IP based on where the query originates (Route53, Cloudflare). User in Tokyo gets Tokyo edge IP; user in London gets London edge IP. Limitation: DNS TTL means failover takes minutes. (2) **Anycast** — same IP is advertised from every edge PoP (Point of Presence) via BGP. Internet routers naturally route packets to the nearest advertiser. If Tokyo goes down, BGP re-routes to the next-nearest PoP in seconds. Cloudflare uses anycast — that's how 1.1.1.1 works from anywhere in the world. For interviews: default answer is "DNS geo-routing via Route53 or Cloudflare," then mention anycast as the more sophisticated mechanism. ⭐ **Tier 2 — probed in depth questions**

### Q: "Explain Cache-Control: no-cache vs no-store. When do you use each?"

> `no-cache` means "you CAN cache this, but check with origin before every serve." The cached copy is held in reserve; if origin says ETag matches, you serve the cached copy (304 Not Modified — saves bandwidth). Use for semi-dynamic content where freshness matters but bandwidth optimization is valuable. `no-store` means "never cache anywhere, ever." Every request is a full round-trip. Use for auth tokens, session IDs, banking responses — anything that must never be retrievable from any cache. The common mistake: using `no-store` for everything "to be safe" — this kills CDN hit rate and hammers origin. Use `private, no-cache` for user-specific content; reserve `no-store` for actual secrets. ⭐ **Tier 2 — common interview trap**

### Q: "How do you serve private paid video content via CDN without making it publicly accessible?"

> Use signed URLs. When a user pays and clicks "Play": backend verifies subscription, then generates a signed URL with (a) an expiry timestamp (e.g., 6 hours) and (b) an HMAC signature using your CDN private key. User's player fetches video from CDN edge using this signed URL. Edge validates the signature and expiry — no round-trip to origin for auth. If someone shares the URL, it expires after 6 hours. The content stays cached at the edge; only the access token expires. CloudFront calls these "signed URLs" (canned or custom policy). This is Netflix's model for paid content. ⭐ **Tier 2 — security + CDN design**

### Q: "Your cache hit rate is 80% but you want 95%. How do you improve?"

> Check what's missing: (1) Increase TTL (longer cache retention). (2) Analyze cache misses — are unpopular files taking space? Implement LRU eviction. (3) Pre-warm cache — upload videos to edges before launch. (4) Check for cache busting headers — if origin sends `Cache-Control: no-cache` on static assets, change to `max-age=31536000`. ⭐ **Tier 2 — Optimization**

---

## 🧾 TL;DR

> "CDN caches content at geographically distributed edge locations — users fetch from nearby edge (50ms) instead of distant origin (200ms+). Pull CDN fetches from origin on first miss; push CDN pre-positions content before launch. Users reach the nearest edge via DNS geo-routing or anycast BGP. Sensitive paid content uses signed URLs (HMAC-signed, time-limited). Cache-Control directives control who caches what: `s-maxage` for CDN-only TTL, `private` for user data, `no-store` for secrets, `stale-while-revalidate` for background refresh."

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
| July 1, 2026 | Added push vs pull CDN section, Anycast/DNS geo-routing explanation, full Cache-Control directives table, signed URLs section with CloudFront Java example, and 4 new interview Q&As. Updated TL;DR. Added Terminology Table (CDN, edge location/PoP, pull CDN, push CDN, cache hit/miss, TTL, cache invalidation/purge, anycast, signed URL) — PoP and anycast used in diagrams before being defined. |
