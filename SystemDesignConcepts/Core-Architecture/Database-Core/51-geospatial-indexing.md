# 51. Geospatial Indexing

---

## 📖 What is Geospatial Indexing?

**Full form:** Geospatial Index (Geographic Spatial Index)

**Simple analogy:** Imagine dividing a country map into a grid of postal codes — the first 3 digits tell you the state, the first 5 tell you the city district, the full 7 tell you a street block. "Find every pizza place near me" becomes "find all rows that share my first 6-digit postal code" — a fast string prefix scan, not a search through every address in the country.

**Core principle:** Geospatial indexing encodes 2D coordinates (latitude, longitude) into a data structure that supports fast *proximity queries* ("who is within 5 km of me?") without scanning every row. The key trick is converting 2D space into 1D strings (geohash), a hierarchical tree (quad tree), or a hexagonal grid (H3) — so ordinary indexes or in-memory structures can eliminate 99% of candidates before the exact distance calculation runs.

**Why it matters in system design:** Any location-based product — Uber (driver matching), Swiggy (restaurant discovery), Tinder (nearby profiles), Airbnb (map search) — needs to answer "what's nearby" in under 100 ms against millions of coordinates. A naïve `SELECT * WHERE haversine(lat, lng, user_lat, user_lng) <= 5` does a full table scan, computing expensive trigonometry on every row — it will kill the database at scale.

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|---|---|---|
| Proximity Query | A database/index query that finds all records within a given distance of a point | "Find restaurants within 5 km of (19.08, 72.89)" |
| Haversine Distance | The exact great-circle distance between two lat/lng points on Earth's surface | Distance between Mumbai (19.08, 72.89) and Pune (18.52, 73.85) ≈ 149 km |
| Geohash | A base-32 alphanumeric string that encodes (lat, lng) where shared prefix ≈ same geographic area | `te7u2qx` encodes a city-block-level area in Mumbai |
| Geohash Precision | Number of characters in the geohash — more characters = smaller area | Precision 7 ≈ 153 m × 153 m; Precision 5 ≈ 4.9 km × 4.9 km |
| Quad Tree | An in-memory tree where each node splits its 2D region into 4 quadrants; adapts depth to point density | Dense city center subdivides many times; sparse suburb stays shallow |
| H3 | Uber's hexagonal hierarchical grid system; all 6 neighbours of each hexagon are equidistant from its centre | Resolution 9 hex ≈ 174 m across; used for surge pricing zones |
| Redis GEO | A Redis sorted set that stores locations as geohash-encoded scores; supports GEOADD and GEOSEARCH | `GEOADD drivers:locations 72.89 19.08 driver-42` |
| Neighbor Cell Search | Querying the center cell plus its 8 surrounding cells (geohash) or 6 surrounding hexagons (H3) to avoid boundary misses | A restaurant 15 m away but in an adjacent cell is only found if you query 9 cells |

---

## 🎯 Why This Matters

- **Problem it solves:** Proximity queries against large coordinate datasets are too slow for a full scan. Spatial indexing prunes the candidate set to ~100 rows before any distance math runs.
- **Where it shows up:** Every "design Uber / Swiggy / Tinder / Airbnb" interview asks how you find nearby entities at scale. This is the foundational data-layer question for location-based systems.
- **Why senior engineers must know this:** Getting this wrong is catastrophic — the difference between a 500 ms full scan and a 5 ms indexed lookup at 100K requests/second is the product living or dying.

---

## 🧠 The Mental Model

Think of a librarian organising books by **Dewey Decimal Classification** — a number like `598.294` where `5` means Natural Sciences, `59` means Zoology, `598` means Birds, `598.2` means Songbirds, and each extra decimal narrows the shelf further. If you want "all bird books," you walk straight to shelf `598` and ignore everything else. You don't check every book in the library.

Geohashing works exactly this way for maps. The world is carved into a grid. Each cell gets a short alphanumeric code. The first character divides the world into 32 giant zones; each extra character splits the current zone into 32 smaller sub-zones. A restaurant in Mumbai might get geohash `te7u2qx`. Every other restaurant within ~150 m shares the prefix `te7u2q`. Finding them is a plain indexed string-prefix query on the database — not a trig calculation.

**What goes wrong without this:** A naïve SQL query computing `haversine(lat, lng, user_lat, user_lng) < 5` for every row forces the database to evaluate a costly function on 10 million rows. No B-tree index can help a function applied to every row. The query takes seconds, not milliseconds.

**How spatial indexing fixes it:** By storing the geohash string in a standard B-tree indexed column, "find restaurants within 5 km" becomes `WHERE geohash IN ('te7u2q0', 'te7u2q1', ..., 9 cells)` — the index eliminates 99.99% of rows in microseconds, then exact distance checking runs on the ~100 survivors.

**The key insight is:** Encoding 2D location as a 1D string where shared prefix = same geographic area lets you piggyback ordinary B-tree indexes for spatial proximity — the hard geometric search becomes a cheap prefix lookup.

---

## 🎨 Visual — System Topology + Geohash Precision Levels

### Full System Topology

```
CLIENT TIER
  User app: lat=19.08, lng=72.89
     │
     │ GET /restaurants?lat=19.08&lng=72.89&radius=5km
     ▼
┌─────────────────────────────────────────┐
│            API GATEWAY / LB             │
└─────────────────┬───────────────────────┘
                  │
                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                    GEO-SEARCH SERVICE                           │
│                                                                 │
│  1. encode(lat, lng) → geohash "te7u2qx"  (precision=7)        │
│  2. neighbours("te7u2qx") + self  → 9-cell search list         │
│  3. SELECT * FROM restaurants WHERE geohash IN (9 cells)        │
│     → ~100 candidates from B-tree index                        │
│  4. haversine filter on candidates → drop outside radius        │
│  5. sort by distance → return top 20                           │
└────────────┬──────────────────────────┬────────────────────────┘
             │                          │
             ▼                          ▼
┌────────────────────┐    ┌───────────────────────────────────┐
│    Redis GEO       │    │    PostgreSQL + PostGIS            │
│  (driver/rider     │    │  (restaurant table with            │
│   real-time        │    │   geohash VARCHAR(12), indexed)    │
│   positions)       │    │                                   │
│  GEOADD / GEOSEARCH│    │  CREATE INDEX ON restaurants       │
└────────────────────┘    │  (geohash);                       │
                          └───────────────────────────────────┘

Write path (drivers): driver app → GEOADD key lng lat driver_id → Redis sorted set
Read path (riders):   GEOSEARCH key BYRADIUS 5 km → driver_id list → metadata lookup
```

### Component Detail — Geohash Precision Levels + Boundary Problem

```
GEOHASH PRECISION LEVELS (more characters = smaller area):

Precision 1  →  ≈ 5,000 km × 5,000 km  (continent)
Precision 3  →  ≈  156 km ×  156 km  (country region)
Precision 5  →  ≈   4.9 km ×  4.9 km  (city district)
Precision 7  →  ≈   153 m ×   153 m  (city block) ← restaurant matching
Precision 9  →  ≈   2.4 m ×   2.4 m  (building level)

Nested subdivision:
┌──────────────────────────────────────────┐
│  "t"  (precision-1: ~1/32 of world)      │
│  ┌──────────────────────────────────┐    │
│  │  "te" (precision-2: 1/1024 world)│    │
│  │  ┌────────────────────────┐      │    │
│  │  │  "te7"  (p-3: ~78km)   │      │    │
│  │  │  ┌──────────────┐      │      │    │
│  │  │  │ "te7u" (p-4) │      │      │    │
│  │  │  │ "te7u2" (p-5)│      │      │    │
│  │  │  │ "te7u2q" (p-6: ≈153m)      │    │
│  │  │  │ "te7u2qx" (p-7: ≈20m) ◀──restaurant here
│  │  │  └──────────────┘      │      │    │
│  │  └────────────────────────┘      │    │
│  └──────────────────────────────────┘    │
└──────────────────────────────────────────┘

BOUNDARY PROBLEM — the critical gotcha:

                    ┌────────────┬────────────┐
                    │ "te7u2q"   │ "te7u2r"   │
                    │            │            │
  point A ●─────────────────────●  point B   │
  (19.0800, 72.8900)│  15m apart │(19.0801, 72.8901)
                    │ "te7u2qx"  │ "te7u2rh"  │
                    └────────────┴────────────┘
          A and B are 15 m apart but have DIFFERENT prefixes!

FIX: always search 9 cells (center + 8 Moore neighbors):
   ┌────┬────┬────┐
   │NW  │ N  │NE  │
   ├────┼────┼────┤
   │ W  │CTR │ E  │  ← search all 9 cells in one IN() query
   ├────┼────┼────┤
   │SW  │ S  │SE  │
   └────┴────┴────┘

KEY INVARIANT:
   Shared prefix = approximately nearby.
   Proximity is NOT guaranteed across cell boundaries.
   Always query 9 cells (geohash) or 7 cells (H3) to eliminate false negatives.
```

---

## ⚙️ How It Actually Works

### Strategy A — Geohash + Database Column

#### What is Geohash, and why does it fit here?

**Geohash** is a geocoding scheme (invented by Gustavo Niemeyer, 2008) that encodes a (latitude, longitude) pair into a short base-32 alphanumeric string using interleaved bit encoding. The latitude bits and longitude bits alternate: even-position bits encode longitude, odd-position bits encode latitude. Each additional character narrows the area by ~32×.

**In an interview, if asked:** "Geohash converts 2D coordinates into a 1D string where shared prefix ≈ same geographic area — which means a standard B-tree index on a geohash column can serve as a fast spatial index. The one catch is the boundary problem at cell edges: two points 1 m apart can have completely different prefixes, so I always query 9 cells (current + 8 neighbors) to avoid false negatives."

#### What is geohash-java, and why does it fit here?

**geohash-java** (`com.github.davidmoten:geohash-java`) is a Java library implementing geohash encode/decode, neighbour computation, and bounding-box lookups. Neighbour computation at prefix boundaries is non-trivial — the bit-interleaving means you can't just increment the string — so using the library instead of hand-coding prevents silent bugs.

**In an interview, if asked:** "I'd use a battle-tested library for neighbour computation — it's a subtle algorithm and getting it wrong silently means missing restaurants at cell edges."

**Steps:**

1. **Encode on write** — when a restaurant registers, compute a 7-char geohash and store it as an indexed column alongside lat and lng.
2. **Encode the user's location** at the same precision (7 chars).
3. **Expand to 9 cells** — compute the center cell plus all 8 Moore neighbours to guarantee boundary safety.
4. **Indexed prefix query** — `WHERE geohash IN (9 cell strings)` hits the B-tree and retrieves ~100–200 candidates.
5. **Haversine filter** — compute exact great-circle distance on the candidate set and discard everything outside the radius.

```java
import com.github.davidmoten.geo.GeoHash;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class GeohashService {

    // Precision 7 = ≈ 153 m per cell — right for restaurant/driver matching
    private static final int PRECISION = 7;

    // Step 1 & 2: Encode a (lat, lng) point to its geohash cell string
    public String encode(double lat, double lng) {
        return GeoHash.encodeHash(lat, lng, PRECISION);
    }

    // Step 3: Compute center cell + 8 Moore neighbours = 9 cells total
    public List<String> getSearchCells(double lat, double lng) {
        String centerHash = encode(lat, lng);
        Set<String> neighbors = GeoHash.neighbours(centerHash);
        List<String> allCells = new ArrayList<>(neighbors);
        // Add the center cell itself — neighbours() returns only the 8 surrounding cells
        allCells.add(centerHash);
        return allCells;
    }

    // Steps 4-5: Query DB for candidates, then exact-distance filter
    public List<Restaurant> findNearby(double lat, double lng, double radiusKm,
                                       RestaurantRepository repo) {
        List<String> searchCells = getSearchCells(lat, lng);
        // Step 4: index-backed query — B-tree eliminates irrelevant rows
        List<Restaurant> candidates = repo.findByGeohashIn(searchCells);
        List<Restaurant> results = new ArrayList<>();
        // Step 5: exact haversine filter on the small candidate set
        for (Restaurant r : candidates) {
            if (haversineKm(lat, lng, r.getLat(), r.getLng()) <= radiusKm) {
                results.add(r);
            }
        }
        return results;
    }

    // Haversine formula — exact great-circle distance in kilometres
    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
```

---

### Strategy B — Quad Tree (In-Memory)

#### What is a Quad Tree, and why does it fit here?

**Quad Tree** is a tree data structure where each internal node has exactly four children — the four geographic quadrants (NE, NW, SE, SW) of its rectangular bounding box. It recursively splits a 2D region until each leaf node holds at most MAX_POINTS. Depth varies with point density: dense city centre nodes split many times; sparse rural nodes stay shallow.

**In an interview, if asked:** "A quad tree adapts depth to data density — where drivers are dense, it subdivides further; where they're sparse, it stays shallow. This is better than geohash for highly non-uniform distributions like urban driver density vs rural, and it's the structure Google Maps uses internally for certain spatial operations. The trade-off is that it lives in-memory and requires a rebuild when the point set changes heavily."

**Steps:**

1. **Initialise** with a root node covering the world's bounding box (lat: −90 → +90, lng: −180 → +180).
2. **Insert** each point: if the leaf has capacity, add the point; if at capacity, subdivide into 4 children and redistribute.
3. **Subdivide** by splitting the bounding box at its midpoint into 4 quadrants, creating child nodes.
4. **Search** by recursively visiting only nodes whose bounding box overlaps the query circle; prune everything else.
5. **Haversine filter** at the leaf level to return only exact in-radius points.

```java
import java.util.ArrayList;
import java.util.List;

public class QuadTree {

    // Maximum points in a leaf node before splitting
    private static final int MAX_POINTS = 4;

    private final double minLat;
    private final double maxLat;
    private final double minLng;
    private final double maxLng;
    // Non-null only when leaf node; null when internal (subdivided) node
    private List<GeoPoint> points;
    // [0]=NE, [1]=NW, [2]=SE, [3]=SW; null when leaf
    private QuadTree[] children;

    public QuadTree(double minLat, double maxLat, double minLng, double maxLng) {
        this.minLat = minLat;
        this.maxLat = maxLat;
        this.minLng = minLng;
        this.maxLng = maxLng;
        this.points = new ArrayList<>();
    }

    // Steps 2-3: Insert a point, subdividing if capacity is reached
    public void insert(GeoPoint point) {
        if (!contains(point.lat, point.lng)) {
            return;
        }
        if (children == null && points.size() < MAX_POINTS) {
            // Step 2: leaf has room — add directly
            points.add(point);
            return;
        }
        if (children == null) {
            // Step 3: capacity exceeded — create 4 child quadrants
            subdivide();
        }
        for (QuadTree child : children) {
            child.insert(point);
        }
    }

    // Step 3: Split bounding box at midpoint into 4 children
    private void subdivide() {
        double midLat = (minLat + maxLat) / 2.0;
        double midLng = (minLng + maxLng) / 2.0;
        children = new QuadTree[4];
        children[0] = new QuadTree(midLat, maxLat, midLng, maxLng); // NE
        children[1] = new QuadTree(midLat, maxLat, minLng, midLng); // NW
        children[2] = new QuadTree(minLat, midLat, midLng, maxLng); // SE
        children[3] = new QuadTree(minLat, midLat, minLng, midLng); // SW
        // Redistribute existing points into child quadrants
        for (GeoPoint p : points) {
            for (QuadTree child : children) {
                child.insert(p);
            }
        }
        // This node is now internal — clear leaf list
        points = null;
    }

    // Steps 4-5: Find all points within radiusKm of (centerLat, centerLng)
    public List<GeoPoint> search(double centerLat, double centerLng, double radiusKm) {
        List<GeoPoint> result = new ArrayList<>();
        // Step 4: prune — skip nodes whose bbox doesn't overlap the query circle
        if (!overlapsCircle(centerLat, centerLng, radiusKm)) {
            return result;
        }
        if (children == null) {
            // Step 5: leaf — exact haversine check on each stored point
            for (GeoPoint p : points) {
                if (haversineKm(centerLat, centerLng, p.lat, p.lng) <= radiusKm) {
                    result.add(p);
                }
            }
            return result;
        }
        // Internal node — recurse into all children (pruning handles efficiency)
        for (QuadTree child : children) {
            result.addAll(child.search(centerLat, centerLng, radiusKm));
        }
        return result;
    }

    // True if this bounding box contains the point
    private boolean contains(double lat, double lng) {
        return lat >= minLat && lat <= maxLat && lng >= minLng && lng <= maxLng;
    }

    // True if the query circle overlaps this bounding box
    // (find the nearest bbox point to the circle centre, check distance)
    private boolean overlapsCircle(double lat, double lng, double radiusKm) {
        double nearestLat = Math.max(minLat, Math.min(lat, maxLat));
        double nearestLng = Math.max(minLng, Math.min(lng, maxLng));
        return haversineKm(lat, lng, nearestLat, nearestLng) <= radiusKm;
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
```

---

### Strategy C — H3 Hexagonal Grid (Uber's Approach)

#### What is H3, and why does it fit here?

**H3** is Uber's open-source hierarchical hexagonal geospatial indexing system (open-sourced 2018). It divides the Earth into hexagonal cells at 16 resolution levels (0 = continent-size cells, 15 = ~1 m² cells). Each cell is identified by a compact 64-bit integer.

**Why hexagons instead of squares?** All 6 neighbours of a hexagon are *equidistant* from its centre. With squares, 4 edge-neighbours are at distance d but 4 corner-neighbours are at d × √2 — so a "search all neighbours" query over-samples diagonally and under-samples along edges. Hexagons eliminate this distortion, making each k-ring of radius 1 (7 cells) a much tighter approximation of a circular search area.

**In an interview, if asked:** "H3 uses hexagons because every neighbour is equidistant — a k-ring of 1 (the centre hex plus its 6 neighbours = 7 cells) maps cleanly to a circular search area with no diagonal over-sampling. This is why Uber uses it for surge pricing zones and ETA computation: each hexagon cell covers a geographically meaningful, shape-consistent area."

#### What is h3-java, and why does it fit here?

**h3-java** (`com.uber.h3core:h3-java`) is Uber's official Java binding for the H3 library. It provides `latLngToCell`, `gridDisk`, `gridRing`, and `cellToLatLng` operations backed by the C H3 core for performance.

**In an interview, if asked:** "I'd use `h3-java` directly — it's Uber's production library, zero-dependency for the core, and exposes the exact k-ring and resolution API I need."

**Steps:**

1. **Encode on write** — when a driver comes online, compute their H3 cell at resolution 9 (≈ 174 m per hex) and store it as an indexed `BIGINT` column.
2. **Encode the rider's position** at resolution 9.
3. **k-ring search cells** — call `gridDisk(centerCell, 1)` to get 7 cells (centre + 6 hexagonal neighbours).
4. **DB query** `WHERE h3_cell IN (7 cells)` — retrieves candidates using the B-tree index on the `BIGINT` column.
5. **Haversine filter** on the ~50–300 candidates to return only in-radius drivers.

```java
import com.uber.h3core.H3Core;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class H3GeoService {

    // Resolution 9: each hexagon ≈ 174 m across — city-block level
    private static final int RESOLUTION = 9;

    private final H3Core h3;

    public H3GeoService() throws IOException {
        this.h3 = H3Core.newInstance();
    }

    // Step 1 & 2: Encode (lat, lng) to the H3 cell index (64-bit integer)
    public long encode(double lat, double lng) {
        return h3.latLngToCell(lat, lng, RESOLUTION);
    }

    // Step 3: k-ring of radius 1 = centre + 6 neighbours = 7 cells total
    public List<Long> getSearchCells(double lat, double lng) {
        long centerCell = encode(lat, lng);
        // gridDisk(cell, 1) returns centre + 6 neighbours = 7 cells
        return new ArrayList<>(h3.gridDisk(centerCell, 1));
    }

    // Steps 4-5: DB query with B-tree on h3_cell, then haversine filter
    public List<Driver> findNearbyDrivers(double lat, double lng, double radiusKm,
                                          DriverRepository repo) {
        List<Long> searchCells = getSearchCells(lat, lng);
        // Step 4: B-tree index on h3_cell BIGINT eliminates irrelevant drivers
        List<Driver> candidates = repo.findByH3CellIn(searchCells);
        List<Driver> results = new ArrayList<>();
        // Step 5: exact haversine filter on the small candidate set
        for (Driver d : candidates) {
            if (haversineKm(lat, lng, d.getLat(), d.getLng()) <= radiusKm) {
                results.add(d);
            }
        }
        return results;
    }

    // Compute H3 cell for a surge-pricing zone — useful for aggregating
    // driver density per cell to set dynamic pricing multipliers
    public long getSurgePricingCell(double lat, double lng, int zoneResolution) {
        // Lower resolution = larger cells = broader surge zone
        return h3.latLngToCell(lat, lng, zoneResolution);
    }

    private double haversineKm(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return 6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
```

---

### Strategy D — Redis GEO Commands

#### What are Redis GEO commands, and why do they fit here?

**Redis GEO** stores locations as members of a sorted set (a `ZSET`), where each member's score is a 52-bit geohash-like encoding of its (longitude, latitude) pair. Commands `GEOADD`, `GEODIST`, and `GEOSEARCH` provide O(N + log M) proximity queries in memory, with no separate index or DB row needed.

**In an interview, if asked:** "Redis GEO is the fastest option for real-time location tracking because it's entirely in memory. Drivers update their position every 4 seconds; GEOADD is O(log N) per update, and GEOSEARCH returns the 10 nearest drivers within 5 km in under 1 ms. The trade-off: you can only store a member name (string ID) per location — all driver metadata (name, rating, car type) lives in a separate DB and you join after the GEOSEARCH."

**Steps:**

1. **GEOADD on each driver update** — the driver app sends its location every 4 seconds; the service calls `GEOADD drivers:locations <lng> <lat> <driver_id>` (O(log N)).
2. **GEOSEARCH on rider request** — call `GEOSEARCH drivers:locations FROMLONLAT <riderLng> <riderLat> BYRADIUS 5 km ASC COUNT 10` to get the 10 closest available driver IDs.
3. **Metadata join** — take the returned driver IDs and query Postgres (or a Redis hash) for name, rating, vehicle type.

```java
import io.lettuce.core.GeoArgs;
import io.lettuce.core.GeoSearch;
import io.lettuce.core.api.sync.RedisCommands;
import java.util.List;

public class RedisGeoService {

    private final RedisCommands<String, String> redis;
    // All driver positions in one sorted-set key
    private static final String DRIVERS_GEO_KEY = "drivers:locations";

    public RedisGeoService(RedisCommands<String, String> redis) {
        this.redis = redis;
    }

    // Step 1: Driver app sends location ping every 4 s — GEOADD is O(log N)
    // IMPORTANT: Redis GEO takes (longitude, latitude) order — not (lat, lng)
    public void updateDriverLocation(String driverId, double lat, double lng) {
        redis.geoadd(DRIVERS_GEO_KEY, lng, lat, driverId);
    }

    // Step 2: Find the 10 nearest available drivers within radiusKm of the rider
    public List<String> findNearbyDrivers(double riderLat, double riderLng, double radiusKm) {
        return redis.geosearch(
            DRIVERS_GEO_KEY,
            GeoSearch.fromCoordinates(riderLng, riderLat),
            GeoSearch.byRadius(radiusKm, GeoArgs.Unit.km),
            GeoArgs.Builder.count(10).asc()
        );
    }

    // Step 3 helper: get exact driver distance for display in the rider app
    public Double getDistanceKm(String driverIdA, String driverIdB) {
        // GEODIST is exact haversine — O(1) lookup from stored coordinates
        return redis.geodist(DRIVERS_GEO_KEY, driverIdA, driverIdB, GeoArgs.Unit.km);
    }

    // Remove driver when they go offline — keeps the sorted set clean
    public void removeDriver(String driverId) {
        redis.zrem(DRIVERS_GEO_KEY, driverId);
    }
}
```

---

## 🏢 Real World — Where Companies Use This

- **Uber** (H3 for surge pricing and ETA): Uber open-sourced H3 because surge zones and ETAs need consistent, equidistant cells. Each hexagon at resolution 7 (~5 km²) holds one pricing zone. GEOSEARCH in Redis tracks real-time driver positions; H3 aggregates them for supply/demand imbalance calculation.
- **DoorDash** (geohash for nearby dashers): Each Dasher position is stored as a 7-char geohash in Redis. When an order arrives, the dispatcher queries 9 geohash cells to find dashers within ~300 m and assigns to the closest one within 50 ms.
- **Swiggy / Zomato** (geohash + Postgres for restaurant discovery): Restaurant records carry an indexed `geohash VARCHAR(9)` column. "Find restaurants within 3 km" queries 9 geohash cells, retrieves ~200 candidates, applies the haversine filter, then overlays business rules (open now, delivery slot available, surge capacity).
- **Tinder** (geohash for nearby profiles): Profile storage uses a 6-char geohash (≈ 1.2 km cells) indexed column. "Cards near you" queries 9 surrounding cells, then filters by age/preference on the ~500 candidates — so the expensive preference computation only runs on a small set.
- **Airbnb** (PostGIS R-tree for map search): Airbnb's bounding-box queries ("show all listings visible on this map viewport") use PostGIS with a GiST index backed by an R-tree. PostGIS's `ST_DWithin` prunes to listings within the viewport — the R-tree eliminates non-overlapping MBRs (minimum bounding rectangles) in O(log n).

---

## 🧭 When to Use vs When NOT to Use

| Use this when | Do NOT use when |
|---|---|
| You need sub-100 ms proximity queries at scale (millions of rows) | Data volume is small (< 50K rows) — `haversine` with a lat/lng index is fine |
| You have a "find nearby X within R km" requirement | Queries are polygon-shaped or bounding-box only — use PostGIS ST_Within instead |
| Driver/rider real-time location updates (Redis GEO) | Location data is static and queried rarely — PostGIS without special tuning works |
| Storing driver density per zone for surge pricing (H3) | You need exact polygon containment ("is this point inside a delivery zone?") — use PostGIS |
| Non-uniform point density (city dense, rural sparse) → quad tree | You need range queries on other dimensions too (time + location) — use a composite index strategy |

**The common mistake:** Using geohash prefix 5 (≈ 5 km cells) for a 500 m radius search. You get far too few cells to return meaningful results AND you miss boundary cases. Match precision to your typical search radius: precision 7 (≈ 153 m) for sub-1 km searches, precision 5 (≈ 5 km) for city-level searches.

**Note on R-tree:** PostGIS uses an R-tree (via GiST index) under the hood for `ST_DWithin` and `ST_Within` queries. R-trees store minimum bounding rectangles hierarchically — good for polygon containment and bounding-box queries, but geohash/H3 beat them for circular proximity queries at scale because they avoid the MBR over-approximation cost.

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Sub-100 ms proximity queries against millions of coordinates; prune 99%+ of candidates before any distance math runs; reuse standard B-tree indexes without special DB extensions |
| **You lose** | Extra column to maintain (geohash or H3 cell must stay in sync with lat/lng); boundary problem requires searching 9 cells (geohash) or 7 cells (H3) instead of 1; precision must be tuned to search radius (wrong precision = wrong candidates) |
| **Failure mode** | Choosing precision too coarse — searching precision-4 cells (≈ 40 km) for a 1 km radius returns thousands of candidates, negating the index benefit and slowing the haversine filter pass. Always calibrate precision so each cell is ~2–5× smaller than your search radius. |

---

## 🔬 Interview Q&As

### Q: "How does geohashing speed up a 'find nearby restaurants' query?"

> Geohash encodes (lat, lng) as a short string where shared prefix ≈ same geographic area. By storing the geohash as an indexed VARCHAR column, a "nearby" query becomes a `WHERE geohash IN (9 cells)` prefix scan — the B-tree retrieves ~100–200 candidates in microseconds. Then exact haversine distance runs only on that small set, not on all 10 million restaurants.

---

### Q: "When would you use a quad tree instead of geohash?"

> Quad tree when your data has highly non-uniform spatial density — like driver locations that are dense in city centres and sparse in suburbs. The quad tree adapts its depth per region, so dense areas get finer subdivision automatically. Geohash uses a fixed-precision grid that either over-indexes sparse areas or under-indexes dense ones. Geohash wins when you need persistence (stored in a DB column); quad tree wins for in-memory structures that are rebuilt as data changes.

---

### Q: "Two restaurants are 15 m apart but have completely different geohash prefixes. What happens to your 'find nearby' query, and how do you fix it?"

> This is the boundary problem — two points straddling a cell edge get different prefix strings even if they're centimetres apart. A query for only the user's current cell misses the restaurant in the adjacent cell entirely. The fix is to always query 9 cells: the user's current cell plus its 8 Moore neighbours (cardinal and diagonal). Neighbour computation is non-trivial at the bit-interleaving level, so I'd use the `geohash-java` library rather than hand-rolling it. With 9 cells searched, no in-radius point can be missed due to boundary placement.

---

### Q: "Why does Uber use hexagons (H3) instead of squares for its geo grid?"

> With a square grid, 4 edge-neighbours are at distance d from the centre but 4 corner-neighbours are at d × √2. When you search all 8 neighbours, you're over-sampling diagonally. Hexagons fix this: all 6 neighbours are equidistant from the centre, so a k-ring of radius 1 (7 cells: centre + 6 neighbours) approximates a circular search area with no directional bias. For surge pricing and ETA estimation — where the input is "drivers within R km" — hexagons give a more geometrically consistent zone shape, which matters for fairness and accurate supply/demand calculation.

---

### Q: "How would you design a 'find nearby drivers' feature for a ride-hailing app?"

> Two-layer approach. **Real-time layer (Redis GEO):** driver app pings location every 4 seconds; backend calls `GEOADD drivers:locations <lng> <lat> <driver_id>`. On rider request, `GEOSEARCH BYRADIUS 5 km ASC COUNT 10` returns the 10 nearest driver IDs in under 1 ms. **Persistent layer (PostgreSQL with H3):** each driver row carries an indexed `h3_cell BIGINT` column (resolution 9). This supports analytics, fallback queries, and surge-zone aggregation. I'd also store last-seen timestamp and expire Redis keys for drivers who haven't pinged in > 30 seconds — avoids showing offline drivers.

---

### Q: "Your geospatial service works fine normally. At 7 PM in Mumbai, 400,000 drivers are all active in the same metro area. What breaks?"

> Two problems. **Hot-key problem in Redis:** all 400K drivers map to a small set of GEO key segments — a single Redis node holding `drivers:locations` becomes a write bottleneck (400K GEOADD calls/minute). Fix: shard the key by city or H3 parent cell (`drivers:mumbai:locations`) so updates distribute across Redis nodes. **Candidate explosion problem in DB:** a precision-7 geohash query for 9 cells in a dense area returns 5,000 candidates instead of 200. Fix: after the 9-cell fetch, apply an in-memory distance sort and take the top 20 without running haversine on all 5,000 — or reduce search radius dynamically when local supply is high.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> "Geospatial indexing converts 2D lat/lng into a 1D string or tree structure so proximity queries hit an ordinary B-tree index instead of scanning every row — the key trade-off is calibrating cell precision to match your search radius, and always querying 9 (geohash) or 7 (H3) cells to avoid missing points at cell boundaries."

---

## 🔗 Related Concepts

- **`Core-Architecture/Database-Core/50-database-indexing.md`** — B-tree index internals; the geohash column is just another B-tree index on a VARCHAR.
- **`Core-Architecture/Database-Core/45-hot-partition-problem.md`** — hot-key in Redis GEO; all drivers in one city = one hot sorted-set key.
- **`Core-Architecture/Database-Core/38-sharding-strategy.md`** — sharding Redis GEO keys by city/region to distribute write load.
- **`Foundations/Data-Fundamentals/12-data-modeling.md`** — adding a geohash column alongside lat/lng; composite index strategy.
- **`Core-Architecture/Resilience-and-Fault-Tolerance/45-hot-partition-problem.md`** — relevant when surge traffic hits a single GEO key.

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **Uber Engineering — H3: Uber's Hexagonal Hierarchical Spatial Index** (eng.uber.com) | Primary source — why Uber chose hexagons, resolution selection for surge/ETA, real production data on cell count and resolution trade-offs | ~15 min read |
| **Redis GEO documentation** — redis.io/docs/latest/commands/geosearch | Exact API contract for GEOSEARCH (BYRADIUS vs BYBOX, ASC/DESC, COUNT, WITHCOORD) — fills in production edge cases not in this note | ~10 min read |
| **hellointerview.com — Proximity Service** | Full system design walkthrough of "find nearby drivers" — how to combine Redis GEO + geohash DB, failure modes at scale | ~20 min read |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 1, 2026 | Note created. Covers geohash (9-cell boundary fix), quad tree (adaptive density), H3 hexagonal grid (equidistant neighbours), and Redis GEO commands — all four with complete Java implementations. Tier 2 Q&As: boundary problem, hexagon-vs-square, Mumbai rush-hour hot-key. |
