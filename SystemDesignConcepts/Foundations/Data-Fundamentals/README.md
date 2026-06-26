# Data Fundamentals

> **Core Question:** How do you design APIs, schemas, and data storage that scale and are maintainable?

**Concepts:** 7  
**Time:** ~7 hours  
**Difficulty:** ⭐⭐⭐

---

## 📖 Concepts

1. **11-api-design.md** (1h)
   - REST conventions, status codes, versioning
   - Idempotency at API layer
   - Interview: "Design an API for a document management system"

2. **12-data-modeling.md** (1.5h)
   - Relational schema design (normalization, 3NF)
   - Indexes, foreign keys, constraints
   - Interview: "Design schema for an e-commerce platform"

3. **07-cdc-outbox.md** (1h)
   - Capture data changes reliably
   - Solve dual-write problem
   - Interview: "How do you replicate changes to multiple services?"

4. **14-document-blob-storage.md** (1h)
   - Where to store unstructured data (images, videos, PDFs)
   - S3, GCS, Azure Blob
   - Interview: "Where does a user's profile picture live?"

5. **08-bloom-filter.md** (0.5h)
   - Fast approximate membership testing
   - O(1) space-efficient data structure
   - Interview: "Check if email is in spam list from 1B records"

6. **15-system-qualities.md** (1h)
   - How to measure system health (availability, latency, throughput)
   - SLI, SLO, SLA concepts
   - Interview: "What metrics matter for this system?"

7. **43-pagination-cursor-based.md** (1h) (NEW)
   - Offset vs cursor vs keyset pagination — when each breaks
   - Cursor encoding, composite key stability, Redis sorted sets for feeds
   - Interview: "Your feed pagination shows duplicate posts. Why?"

---

## 🎯 Study Order

**Recommended:** 11 → 12 → 07 → 14 → 08 → 15 → 43

**Why?**
- API design first (11) — how clients interact
- Schema design (12) — how data is stored
- CDC (07) — how changes propagate
- Blob storage (14) — where unstructured data goes
- Bloom filters (08) — optimization for existence checks
- System qualities (15) — how to measure it all

---

## 💡 Key Mental Models

| Concept | Mental Model |
|---------|--------------|
| API Design | Restaurant menu; contract between kitchen and customer |
| Data Modeling | Government office with filing cabinets |
| CDC/Outbox | Newspaper that records every event for subscribers |
| Blob Storage | Library card catalog + off-site warehouse |
| Bloom Filter | Spellchecker that says "definitely not in dictionary" |
| System Qualities | Measuring system health (pulse, blood pressure) |
| Pagination | Bookmark in a book — cursor remembers exact position, offset re-counts from the beginning |

---

## 🔬 Common Interview Questions

- "Design an API for a booking platform"
- "Design schema for a social network (users, posts, likes, follows)"
- "How do you sync user data to search index when profile changes?"
- "Design a system to store user-uploaded files"
- "Check if email is in spam list of 1B emails"
- "Define SLI/SLO for a payment system"

---

## 📚 Real Companies

- **Google**: API design (Google Cloud API); complex schema design; Bloom filters in BigTable
- **AWS**: Object storage (S3) best practices; API Gateway patterns
- **Stripe**: Immutable event log; CDC for analytics pipeline; SLO-driven design
- **Facebook**: Data modeling at scale; Bloom filters for spam detection; CDC for real-time features

---

## ✅ Checkpoint

After this folder, you should be able to:
- ✅ Design a well-versioned, maintainable API
- ✅ Model data in 3NF with appropriate indexes
- ✅ Explain how to reliably propagate changes
- ✅ Choose appropriate storage (relational vs blob)
- ✅ Define system quality metrics
