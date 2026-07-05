# Proximity Search — Quick Reference

> **Read this:** 30 min before an interview involving location-based features, ride-sharing, or local search.
> **Deep study:** `DeepDive/09-proximity-search.md`

---

## 🎯 What kind of problem is this?

Use this pattern when: **you need to find things near a location** — a standard DB B-tree index can't do 2D range queries efficiently.

Trigger words: "find nearby restaurants", "nearest available driver", "stores within 10km", "Uber / Lyft design", "Yelp design", "DoorDash design", "proximity search", "geospatial", "find places near me", "location-based search".

---

## 🧭 Decision Sequence

```
START: You need to find things near a location

Step 1 → Are the objects static or moving?
         Static (restaurants, stores, POIs) → Step 2
         Moving (drivers, couriers)         → Redis geospatial (Strategy 4)

Step 2 → Static objects: how large is the dataset?
         < 500K rows, low QPS → bounding box + Haversine (Strategy 1) is fine
         > 500K rows or high QPS → Step 3

Step 3 → What's your DB stack?
         PostgreSQL → PostGIS (Strategy 3) — most accurate, best for complex queries
         MongoDB    → 2dsphere index (same idea as PostGIS, different syntax)
         ElasticSearch → geo_distance query + geo_point field
         Redis / need fast cache → geohash in Redis (Strategy 2)

Step 4 → Do you need to combine proximity with other filters?
         "restaurants within 5km that are open now, rated > 4 stars, serve sushi"
         → PostGIS for the proximity part, indexed columns for the other filters
         → Or: geohash pre-filter → application-level filter

Step 5 → Do you need real-time updates of moving objects?
         High update rate (< 30s per object) → Redis GEOADD for current position
         Also persist to DB (Kafka → Cassandra) for history and analytics

Step 6 → Global scale (billions of items)?
         Shard by geographic region: city or country
         Each shard serves its region (Uber's city-based sharding)
         Global queries (cross-region) are rare and handled by a routing layer
```

---

## ⚡ Strategies at a Glance

| Strategy | Use when | Don't use when |
|---|---|---|
| **Bounding Box + Haversine** | Dataset < 500K, low QPS, quick prototype | High QPS, large dataset, need sub-10ms p99 |
| **Geohash (Redis GEOADD)** | 1M–50M locations, read-heavy, Redis in stack | Frequently moving objects; very non-uniform density |
| **PostGIS Spatial Index** | Postgres already in stack, complex queries, exact distance | Need Redis-speed (< 1ms); real-time moving objects |
| **Redis Geospatial (moving objects)** | Moving objects (drivers, couriers), < 5ms p99, ephemeral data | Stable locations; need location history |

**Key numbers to remember:**
- Geohash precision 6 ≈ 1.2km × 0.6km cells; precision 7 ≈ 153m cells
- Always search center cell + 8 neighbors — boundary points share no prefix with adjacent cells
- Redis GEOADD: O(log N), ~0.5ms for 100K drivers
- Redis single instance: 100K–500K ops/sec (handles 125K driver updates/sec comfortably)
- PostGIS ST_DWithin: O(log N) with GiST spatial index — O(N) without it
- Haversine computes great-circle distance — cheap on 50 candidates, O(N) death on 50M

---

## 🎨 Key Architecture Diagram

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

> Static dataset (~10M restaurants globally) with complex filters. Architecture: (1) Store restaurants in PostgreSQL with PostGIS `GEOGRAPHY(Point)` column and GiST spatial index. (2) Query: `SELECT * FROM restaurants WHERE ST_DWithin(location, $user_point, $radius) AND is_open = true AND rating >= 4.0 ORDER BY ST_Distance(location, $user_point) LIMIT 20`. (3) Cache popular city-level results in Redis (geohash prefix → list of restaurant IDs). A query for "restaurants in Mission District SF" hits cache first. Cache TTL: 5 minutes. (4) At Yelp scale, shard by city: each city has its own PostGIS replica. Queries are routed to the right shard by city name (not by lat/lng — users search within a known city). (5) ElasticSearch alternative: `geo_distance` filter on a `geo_point` field, combined with term filters for rating/cuisine.

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

## ⚠️ Anti-patterns (don't say these)

- **Running Haversine on the entire table without a spatial index** — O(N) per query on 10M rows at 1K QPS = 10B distance computations/sec; always spatial-index pre-filter first
- **Searching only the center geohash cell** — points at cell boundary return zero nearby results; always search center + all 8 neighbors (Redis GEORADIUS does this automatically)
- **Geohash for moving objects without TTL on stale entries** — offline drivers left in Redis get returned to riders; use ZREM on offline signal + background expiry for entries not updated in > 60s

---

## 🧩 Common Interview Problems

| Problem | Key Design Choice | Notes |
|---|---|---|
| Design Yelp / Local Search | PostGIS + filters, or geohash in Redis | Static dataset; compound filter (open + rating + cuisine) |
| Design Uber / Lyft | Redis GEOADD/GEORADIUS, city-sharded | Moving objects; 125K writes/sec at peak |
| Design DoorDash | Dual: PostGIS (restaurants) + Redis (dashers) | Two separate proximity problems |
| Design Google Maps "nearby" | S2 geohash + sharding by city | Billions of POIs; global precision |
| Design a Store Locator | Bounding box + Haversine (small); PostGIS (large) | Small dataset → simplest approach fine |
| Design Friend Location Sharing | Fuzzy geohash + privacy controls | Never expose exact coordinates |
| Design a Parking App | Redis geospatial + real-time availability | Spots change state frequently |

---

## 🔗 Full notes

`DeepDive/09-proximity-search.md` — geohash internals, PostGIS setup, S2 vs geohash, full failure mode Q&A
