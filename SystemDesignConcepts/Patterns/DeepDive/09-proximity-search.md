# Pattern Deep Dive: Proximity Search

> **Read this when:** You need to understand how to find things near a location — restaurants near me, drivers near a pickup point, stores within 10km — efficiently at scale.
> **Pre-interview refresh:** Use `Reference/09-proximity-search.md` (5 min).

---

## 🎯 What Problem Does This Pattern Solve?

A user is at latitude 37.77, longitude -122.41 (San Francisco). They want restaurants within 5km. Or Uber needs the 3 nearest available drivers to a pickup. Or Google Maps needs to show every pharmacy within 10 miles.

The challenge: "find everything within radius R of point P" is a **2D range query**. Standard DB indexes (B-trees) work in 1D — they can efficiently answer "give me all rows where `price BETWEEN 10 AND 50`," but not "give me all rows where the (lat, lng) point is within 5km of (37.77, -122.41)." A naive approach scans every row and computes distance — O(N) per query against millions of locations.

Classic proximity search scenarios:
- **Ride-sharing:** Nearest available drivers to a pickup location. Needs < 100ms, updated every 4 seconds as drivers move.
- **Food delivery:** Restaurants within 5km that deliver to your address. Needs filtering (cuisine, open now) on top of proximity.
- **Local search:** Yelp, Google Maps, Foursquare — find businesses near a point, sorted by distance.
- **Store locator:** "Find the nearest Walmart within 30 miles."
- **Social:** "Friends near me." Privacy-sensitive, requires fuzzy location (imprecise on purpose).

---

## 💡 Core Insight

**Reduce the 2D problem to a 1D problem.** Any technique that lets you convert a (lat, lng) pair into a string or integer that preserves geographic proximity allows you to use a standard B-tree index for a fast initial filter, then apply exact distance math on the small candidate set that remains.

> **KEY INSIGHT:** "Proximity search is not about computing distances — it's about shrinking the candidate set. Use a spatial encoding (geohash, S2) or spatial index (PostGIS R-tree) to eliminate 99.9% of rows before doing any distance math. Distance math is cheap on 50 rows; it's O(N) death on 50 million."

---

## 🗂️ The 4 Strategies

---

### Strategy 1 — Bounding Box + Haversine Filter

Add `lat FLOAT` and `lng FLOAT` columns to your location table. Index both. Query a square bounding box first, then filter by exact circle using the Haversine formula (the formula that computes great-circle distance between two lat/lng points on Earth's surface).

**When to use:**
- Dataset < 500K locations
- Low query volume (< 100 QPS)
- Quick prototype / internal tool
- No PostGIS available and geohash feels like overkill

**When NOT to use:**
- High query volume or large dataset (compound index on float columns still produces many false positives)
- Queries spanning large radiuses (bounding box grows huge, many false positives)
- You need sub-10ms p99 at scale

**Steps in plain English:**
1. **Index** — Create a compound index on `(lat, lng)`. Separately, ensure `lat` and `lng` have individual indexes (DB can use index intersection).
2. **Bounding box query** — Compute the bounding box: `lat_min = lat - (radius_km / 111)`, `lat_max = lat + (radius_km / 111)`, `lng_min = lng - (radius_km / (111 × cos(lat)))`, `lng_max = lng + (radius_km / (111 × cos(lat)))`. Query `WHERE lat BETWEEN lat_min AND lat_max AND lng BETWEEN lng_min AND lng_max`.
3. **Haversine filter** — In application code (or a DB function), compute exact great-circle distance for each candidate row and discard those outside radius R. This filters the corners of the bounding box.
4. **Sort and return** — Sort surviving rows by distance, return top K.

```
               Grid view of the bounding box approach:

         ┌──────────────────────────────┐
         │   ✗     ✗     ✗     ✗        │  ← bounding box rows
         │      ✓────────────╮          │     returned by WHERE clause
         │   ✗  │  ✓   ✓   ✓│  ✗       │
         │      │   ✓ (P)✓  │          │  P = query point
         │   ✗  │  ✓   ✓   ✓│  ✗       │  ✓ = inside circle (kept)
         │      ╰────────────╯          │  ✗ = inside box, outside circle
         │   ✗     ✗     ✗     ✗        │     (Haversine discards these)
         └──────────────────────────────┘

Bounding box = fast index scan (low cost).
Haversine = O(|box results|) math (cheap when box is small).
Weakness: at poles or large radii, |box results| grows large.
```

---

### Strategy 2 — Geohash (Standard Cache-Friendly Approach)

Encode (lat, lng) into a short alphanumeric string called a **geohash** (a compact string prefix that encodes geographic cells, where strings sharing a prefix belong to the same geographic cell). Nearby locations share the same prefix. Index the geohash string. Query by prefix.

**When to use:**
- Moderate dataset (1M–50M locations)
- Need to cache location data in Redis (Redis has native `GEOADD`/`GEORADIUS` using geohash internally)
- Read-heavy workload with stable locations (restaurants, stores)
- Need simple sharding by geohash prefix (all locations in cell X go to shard X)

**When NOT to use:**
- Frequently moving locations (drivers, users) — geohash changes as location changes, cache invalidation is noisy
- Very non-uniform density (geohash cells are fixed size — a cell covering NYC has millions of restaurants, a cell covering rural Montana has zero)
- Queries requiring very precise distance (geohash proximity is approximate — use PostGIS for exact)

**Steps in plain English:**
1. **Encode** — When storing a location, compute its geohash: `encode(lat, lng, precision)`. Precision 6 = ~1.2km cells, precision 7 = ~153m cells. Choose precision based on your typical query radius.
2. **Store** — Store the geohash string alongside (lat, lng). Index the geohash column (or use Redis GEOADD which handles this for you).
3. **Query** — For a radius query: (a) Compute the geohash of the query point. (b) Get all 8 neighboring cell hashes (to avoid boundary misses). (c) `WHERE geohash LIKE 'prefix%'` for each of the 9 cells (1 center + 8 neighbors). (d) Run Haversine filter on candidates to get exact distances.
4. **Cache in Redis** — Use `GEOADD locations:restaurants lng lat "restaurant:abc"` to store. Use `GEORADIUS locations:restaurants -122.41 37.77 5 km WITHCOORD WITHDIST COUNT 20 ASC` to query. Redis does the geohash + neighbor + Haversine internally.

```
Geohash precision 6 ≈ 1.2km × 0.6km per cell

     ┌──────┬──────┬──────┐
     │ 9q8yp│ 9q8yr│ 9q8yx│
     ├──────┼──────┼──────┤
     │ 9q8yn│ 9q8yy│ 9q8yz│  ← center cell (query point P is here)
     ├──────┼──────┼──────┤
     │ 9q8yj│ 9q8ym│ 9q8yq│
     └──────┴──────┴──────┘

Query: "restaurants near 9q8yy"
→ Search center cell (9q8yy) + all 8 neighbors
→ Combine candidates → Haversine filter → sort by distance

BOUNDARY EDGE CASE:
If P is at the edge of cell 9q8yy, nearby points in 9q8yr
share zero prefix with P despite being 50m away.
Fix: always search all 8 neighbors, not just the center cell.

KEY INVARIANT:
   Points in the same geohash cell are guaranteed to be within ~1.4× the cell size.
   Searching center + 8 neighbors guarantees no nearby point is missed
   (up to ~2× the query radius, depending on cell size vs radius ratio).
```

---

### Strategy 3 — PostGIS Spatial Index (DB-Native, Production Standard)

Use the PostGIS (a PostgreSQL extension that adds support for geographic objects and spatial queries via an R-tree index — a type of tree that groups nearby shapes together, making "find things near point P" a fast tree traversal instead of a full table scan) extension. Store locations as `GEOMETRY` or `GEOGRAPHY` types. Query with `ST_DWithin`. The R-tree index prunes the search space from millions to dozens in microseconds.

**When to use:**
- PostgreSQL already in your stack
- Complex spatial queries beyond simple radius (polygon containment, route-based search, overlapping regions)
- Exact distance required (PostGIS uses true geodesic distance, not approximations)
- Dataset up to ~100M points (PostGIS scales well with proper indexing and partitioning)

**When NOT to use:**
- Need Redis-level latency (< 1ms) — PostGIS adds DB round-trip
- Real-time moving objects (drivers) — DB writes per location update at scale creates write pressure
- Already using a non-Postgres DB (MongoDB has 2dsphere indexes, ElasticSearch has geo_distance — use those instead)

**Steps in plain English:**
1. **Enable extension** — `CREATE EXTENSION postgis;` in your PostgreSQL database.
2. **Add column** — `ALTER TABLE places ADD COLUMN location GEOGRAPHY(Point, 4326);` (4326 = WGS84, the standard GPS coordinate system).
3. **Populate** — `UPDATE places SET location = ST_MakePoint(lng, lat)::GEOGRAPHY;`
4. **Create spatial index** — `CREATE INDEX places_location_gist ON places USING GIST(location);` GiST (Generalized Search Tree — PostgreSQL's index access method for non-B-tree data structures like geometric shapes and text) builds an R-tree internally for spatial columns.
5. **Query** — `SELECT *, ST_Distance(location, ref_point) AS dist_meters FROM places WHERE ST_DWithin(location, ref_point, 5000) ORDER BY dist_meters LIMIT 20;` where `ref_point = ST_MakePoint(-122.41, 37.77)::GEOGRAPHY`.

```
R-tree index structure (conceptual):

Root
├── Region A (covers west coast USA)
│   ├── Region A1 (covers San Francisco Bay Area)
│   │   ├── Region A1a (covers Mission District)
│   │   │   ├── Restaurant 1 (37.758, -122.411)
│   │   │   ├── Restaurant 2 (37.761, -122.413)
│   │   │   └── ...
│   │   └── Region A1b (covers SoMa)
│   └── Region A2 (covers Oakland)
├── Region B (covers NYC)
└── ...

ST_DWithin(location, query_point, 5000m):
  → Prune regions that can't intersect the 5km circle
  → Descend only into intersecting regions
  → Check actual distance only for leaf points in those regions

Cost: O(log N) to find candidates, O(|candidates|) to distance-filter.
```

---

### Strategy 4 — Geohash Grid in Redis (Real-Time Moving Objects)

For objects that move frequently (ride-share drivers, delivery couriers), the static-index approaches break down — every location update must modify the index. Redis geospatial commands (`GEOADD`, `GEORADIUS`) handle this gracefully: a single `GEOADD` atomically updates a driver's position, and `GEORADIUS` queries the updated positions in microseconds.

**When to use:**
- Objects move frequently (every 4–30 seconds): drivers, couriers, delivery bikes
- Need < 5ms p99 for location queries (matching engines)
- Location data is ephemeral (if a driver goes offline, their entry is deleted — no DB cleanup needed)
- High write volume (10K drivers × 1 update/4s = 2,500 writes/sec)

**When NOT to use:**
- Stable locations (restaurants, stores) — Redis geospatial is overkill, just use PostGIS or geohash in DB
- You need full history of location changes (Redis only stores current position — write location history to DB/Kafka separately)
- Data must survive Redis restart without DB persistence configured

**Steps in plain English:**
1. **Driver reports position** — Driver app sends GPS update every 4 seconds to a location service.
2. **Update Redis** — `GEOADD drivers:available -122.41 37.77 "driver:abc123"`. This upserts the driver's position into the sorted set. O(log N).
3. **Driver goes offline** — `ZREM drivers:available "driver:abc123"`. Instantly removed from the searchable set.
4. **Match query** — When rider requests pickup: `GEORADIUSBYMEMBER drivers:available <pickup_lng> <pickup_lat> 10 km WITHCOORD WITHDIST COUNT 5 ASC`. Returns the 5 nearest available drivers with their coordinates and distances.
5. **Driver accepted** — Remove driver from `drivers:available` set immediately (prevent double-assignment). Add to `drivers:busy` set.

```
         Redis Geo Sorted Set: "drivers:available"

         Member           Score (geohash int)   Decoded position
         ─────────────    ─────────────────────  ──────────────────
         driver:abc123    3476000183...          (37.77, -122.41)
         driver:xyz456    3475999821...          (37.76, -122.40)
         driver:pqr789    3476000512...          (37.78, -122.42)

         GEORADIUS pickup_point 3 km ASC
            → decode each geohash → compute distance → filter → sort
            → returns [driver:abc123 (0.4km), driver:xyz456 (1.2km)]

         Driver location update (every 4 seconds):
            GEOADD drivers:available -122.415 37.772 "driver:abc123"
            → old entry overwritten atomically
            → O(log N), ~0.5ms even for 100K drivers

KEY INVARIANT:
   GEOADD is an upsert. Each driver has exactly one position at any moment.
   GEORADIUS always reflects the latest known position of every driver.
   No stale index entries — driver going offline = ZREM, instant consistency.
```

---

## 🧭 Decision Sequence

```
START: You need to find things near a location

Step 1 ── Are the objects static or moving?
          Static (restaurants, stores, POIs) → Step 2
          Moving (drivers, couriers)         → Redis geospatial (Strategy 4)

Step 2 ── Static objects: how large is the dataset?
          < 500K rows, low QPS → bounding box + Haversine (Strategy 1) is fine
          > 500K rows or high QPS → Step 3

Step 3 ── What's your DB stack?
          PostgreSQL → PostGIS (Strategy 3) — most accurate, best for complex queries
          MongoDB    → 2dsphere index (same idea as PostGIS, different syntax)
          ElasticSearch → geo_distance query + geo_point field
          Redis / need fast cache → geohash in Redis (Strategy 2)

Step 4 ── Do you need to combine proximity with other filters?
          "restaurants within 5km that are open now, rated > 4 stars, serve sushi"
          → PostGIS for the proximity part, indexed columns for the other filters
          → Or: geohash pre-filter → application-level filter

Step 5 ── Do you need real-time updates of moving objects?
          High update rate (< 30s per object) → Redis GEOADD for current position
          Also persist to DB (Kafka → Cassandra) for history and analytics

Step 6 ── Global scale (billions of items)?
          Shard by geographic region: city or country
          Each shard serves its region (Uber's city-based sharding)
          Global queries (cross-region) are rare and handled by a routing layer
```

---

## 🎨 Visual — Full Proximity Search Architecture (Ride-Share Example)

```
   Driver App (every 4s)                                   Rider App
        │                                                       │
        │──POST /location                                       │──POST /match-driver
        │  {lat:37.77, lng:-122.41}                            │  {lat:37.76, lng:-122.40}
        │                                                       │
        ▼                                                       ▼
  Location Service                                      Matching Service
  ┌──────────────────┐                                ┌─────────────────────┐
  │ GEOADD           │                                │ GEORADIUS           │
  │ drivers:active   │                                │ drivers:active      │
  │ lng lat driver_id│                                │ pickup_point        │
  └────────┬─────────┘                                │ 5 km COUNT 5 ASC   │
           │                                          └──────────┬──────────┘
           ▼                                                     │
   ┌───────────────────────────────────────────────┐            │
   │              Redis Geo Sorted Set              │◀───────────┘
   │        "drivers:active"                        │
   │  ┌─────────────────────────────────────────┐  │
   │  │  driver:abc  ●  (37.77, -122.41)        │  │
   │  │  driver:xyz  ●  (37.76, -122.40)        │  │
   │  │  driver:pqr  ●  (37.78, -122.42)        │  │
   │  │  driver:mnop ●  (37.74, -122.44)        │  │
   │  └─────────────────────────────────────────┘  │
   └───────────────────────────────────────────────┘
           │
           │ Kafka (location history, analytics)
           ▼
   ┌─────────────────────┐
   │   Cassandra         │
   │   driver_location   │
   │   (time-series log) │
   └─────────────────────┘

   Matching result:
     [driver:abc (0.4km), driver:xyz (1.1km)]
     → Send ride offer to driver:abc
     → ZREM driver:abc from drivers:active
     → ZADD driver:abc to drivers:busy

KEY INVARIANT:
   Redis holds current position only — one entry per driver, always fresh.
   Cassandra holds full location history — for analytics, ETAs, ML training.
   Matching queries always go to Redis (< 1ms). History queries go to Cassandra.
   A driver going offline triggers ZREM — no stale entries pollute search results.
```

---

## 🔬 Interview Q&A

### Q: "Design a 'find nearby restaurants' feature for Yelp."

> Static dataset (~10M restaurants globally) with complex filters. Architecture: (1) Store restaurants in PostgreSQL with PostGIS `GEOGRAPHY(Point)` column and GiST spatial index. (2) Query: `SELECT * FROM restaurants WHERE ST_DWithin(location, $user_point, $radius) AND is_open = true AND rating >= 4.0 ORDER BY ST_Distance(location, $user_point) LIMIT 20`. (3) Cache popular city-level results in Redis (geohash prefix → list of restaurant IDs). A query for "restaurants in Mission District SF" hits cache first. Cache TTL: 5 minutes. (4) At Yelp scale, shard by city: each city has its own PostGIS replica. Queries are routed to the right shard by city name (not by lat/lng — users search within a known city). (5) ElasticSearch alternative: `geo_distance` filter on a `geo_point` field, combined with term filters for rating/cuisine. ElasticSearch scales reads extremely well and handles the compound filter natively.

---

### Q: "How does geohash handle the boundary problem? (two points in adjacent cells that share no prefix)"

> Two points 10 meters apart but on opposite sides of a geohash cell boundary will have completely different geohash strings at the precise cell level. For example: `9q8yy` and `9q8yz` are adjacent cells — a point at (37.770, -122.410) might be in `9q8yy` and a point at (37.770, -122.409) might be in `9q8yz`. They share the prefix `9q8y` at precision 4 but not at precision 6. The fix is always search the center cell AND all 8 neighboring cells. This guarantees that any point within the query radius is found, regardless of which side of a cell boundary it falls on. Redis GEORADIUS handles this automatically — it's built into the implementation.

---

### Q: "Uber has 500K active drivers at peak. How do you handle 500K location updates every 4 seconds?"

> 500K drivers × 1 update/4s = 125K writes/sec. Plan: (1) Each GEOADD to Redis is O(log N) ≈ sub-millisecond. Redis can handle 100K–500K ops/sec on a single instance. 125K writes/sec is well within Redis's capacity. (2) Shard by city: `drivers:active:san_francisco`, `drivers:active:new_york`. Each city is a separate Redis key (sorted set). Location updates are routed to the relevant city shard. This distributes load and contains the query scope — a NYC rider only queries the NYC set. (3) Separately, write location updates to Kafka (async). Consumer groups write to Cassandra for history, ML training, and ETA calculations. (4) Matching service queries Redis (`GEORADIUS`) per pickup request — not the history DB.

---

### Q: "What's the difference between geohash and Google's S2 library?"

> Both solve the same problem (encode 2D geography into a searchable 1D form) but differ in geometry and use cases. Geohash: rectangular cells projected on a flat (Mercator) grid. Simple string prefix. Precision level directly maps to cell size. Works well for most applications. Redis uses geohash. Limitation: cells near the poles are distorted (Mercator projection makes polar cells misleadingly large). S2 (Google's Spherical Geometry library — treats Earth as a sphere divided into hierarchical cells): cells on a cube projected onto a sphere. Cell shapes are more uniform globally. Better for large-area queries and polar regions. Used by Google Maps, Foursquare, and systems needing precise global coverage. For an interview: geohash is sufficient for "find nearby restaurants" at city scale. S2 becomes relevant when you say "this system needs to work globally with consistent precision."

---

### Q: "How do you handle a 'search by driving distance' vs 'search by straight-line distance'?"

> These are fundamentally different queries. Straight-line distance (as the crow flies): use geohash, PostGIS `ST_Distance`, or `GEORADIUS` — all compute Haversine distance. Fast, O(log N) with a spatial index. Driving distance (actual route): requires a routing engine (Google Maps API, OSRM, Valhalla). You cannot precompute driving distances for all possible origin-destination pairs. Practical approach: (1) Pre-filter by straight-line distance (get candidates within 2× the target driving distance — a straight-line filter with padding). (2) For the top K candidates, call the routing engine to get actual driving distances. (3) Re-rank and filter by driving distance. The routing call is expensive (~50ms per candidate) — do it for the top 20 candidates only, not all 10,000 in the radius.

---

### Q: "Your proximity search is slow at peak hours. How do you diagnose and fix it?"

> Diagnosis: (1) Check if the spatial index is being used: run `EXPLAIN ANALYZE` on the query. If you see `Seq Scan` instead of `Index Scan using places_location_gist`, the GiST index isn't being used. (2) Check query radius: a 50km radius on a dense urban dataset returns hundreds of thousands of candidates — even with a spatial index, filtering and sorting that many rows is slow. Fix options: (a) Reduce radius or add more filters to reduce candidates. (b) Partition the table by region — each partition covers one city, PostGIS queries a single partition. (c) Add a Redis geohash cache for hot queries (top 100 queried locations cached for 5 minutes). (d) Read replicas: spatial queries are read-heavy, route them to replicas. (e) Pre-compute: if you have fixed query points (store locator with fixed stores), pre-compute nearest stores for popular zip codes and cache.

---

### Q: "How do you make 'find nearby' respect privacy? (users don't want exact location exposed)"

> Fuzzy location disclosure. (1) Never expose exact coordinates for user-location features. Instead: snap to a geohash cell of appropriate precision. Precision 5 (≈ 4.9km cells) is suitable for "friends nearby" — it tells you someone is roughly in your neighborhood, not their exact address. (2) On the backend: store exact location privately, return geohash center (not exact coordinates) in API responses. (3) User control: "share my location" toggle. When off, remove from the geospatial index entirely. (4) Temporal fuzzing: if a user hasn't moved in 10 minutes, consider them "stationary" and don't update their location in the index — prevents someone tracking another user's movement patterns by watching position updates. (5) For sensitive contexts (healthcare, dating apps): only reveal "within 5 miles" (boolean), not the actual distance.

---

### Q: "Design the location system for a food delivery app (DoorDash / Uber Eats)."

> Two separate proximity problems: (1) **Finding restaurants near customer**: static dataset, PostGIS or geohash in Redis. Pre-indexed. Cache popular neighborhoods. Query: restaurants within 10km + filters (open now, estimated delivery time). (2) **Finding available dashers near a pickup**: real-time moving objects. Redis GEOADD per dasher location update (every 10 seconds). GEORADIUS for nearest available dashers. Set TTL on dasher entries: if no update in 60 seconds, auto-expire (dasher went offline). (3) **ETA calculation**: Haversine distance + average speed heuristic for initial ETA. Refine with actual routing via routing engine once dasher is assigned. (4) **Dasher state machine**: `dashers:available:{city}` → `dashers:on_pickup:{city}` → `dashers:delivering:{city}`. Each state is a separate Redis sorted set. Dasher atomically moves between sets (MULTI/EXEC) on state transitions.

---

## ⚠️ Anti-patterns

- **Running Haversine on the entire table without a spatial index.** `SELECT *, haversine(lat, lng, $lat, $lng) AS dist FROM locations WHERE haversine(...) < 5` — this computes distance for every row in the table. O(N) per query. With 10M locations and 1,000 QPS, that's 10 billion distance computations per second. Always pre-filter with a spatial index (geohash prefix, PostGIS `ST_DWithin`, R-tree) to get a small candidate set, then apply exact distance math only to the ~50-200 candidates. The index is the whole point.

- **Searching only the center geohash cell, not its 8 neighbors.** A point sitting on the border of a geohash cell will appear to have zero nearby results — all the nearby points are in adjacent cells with a completely different prefix. This is the classic geohash bug: "find nearby restaurants" returns nothing because the user is at cell boundary. Always query the center cell + all 8 surrounding cells. Nine queries, not one. Redis GEORADIUS handles this automatically, which is one reason to prefer it over manual geohash queries.

- **Using geohash for real-time moving objects without TTL on stale entries.** A driver who goes offline without explicitly calling ZREM leaves a stale entry in the Redis geospatial set. When a rider queries for nearby drivers, they receive a driver ID that is offline — the match attempt fails, the rider waits, and the system retries. Fix: (1) Explicit ZREM on offline signal. (2) TTL-based expiry: use a secondary set with expiry timestamps, or run a background job to purge entries not updated in > 60 seconds. (3) Heartbeat: treat location updates as heartbeats. No update in 60 seconds = driver is offline, remove from available set.

---

## 🗺️ Problems Map

| Interview Problem | Why Proximity Search Applies | Key Design Choice |
|---|---|---|
| Design Yelp / Local Search | Find restaurants near a point | PostGIS + filters, or geohash in Redis |
| Design Uber / Lyft | Nearest available driver | Redis GEOADD/GEORADIUS, city-sharded |
| Design DoorDash | Both restaurants + dashers near me | Dual problem: PostGIS (restaurants) + Redis (dashers) |
| Design Google Maps "nearby" | Billions of POIs worldwide | S2 geohash + sharding by city |
| Design a Store Locator | Nearest store within 30 miles | Bounding box + Haversine (small dataset), PostGIS (large) |
| Design Friend Location Sharing | See friends on a map | Fuzzy geohash + privacy controls |
| Design a Parking App | Available spots near me | Redis geospatial + real-time availability updates |

---

## 🔗 Concept Notes — Go Here for "How It Works"

- **Redis data structures** (sorted sets, GEOADD internals, TTL, ZADD/ZREM) → `../../Foundations/Data-Fundamentals/redis-deep-dive.md`
- **PostgreSQL indexing** (GiST index, B-tree vs R-tree, EXPLAIN ANALYZE) → `../../Foundations/Data-Fundamentals/postgresql-indexing.md`
- **Cassandra** (time-series location history, wide-row design) → `../../Foundations/Data-Fundamentals/cassandra-deep-dive.md`
- **Scaling reads** (caching geohash results, read replicas for spatial queries) → `01-scaling-reads.md`
- **Real-time location updates to client** (pushing driver position to rider app) → `07-real-time-updates.md`

---

## 🔄 Changelog

| Date | Change |
|---|---|
| July 2026 | Note created. Final pattern in the batch of 9 DeepDive notes. |
