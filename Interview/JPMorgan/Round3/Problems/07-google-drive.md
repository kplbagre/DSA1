# Google Drive / File Storage (Dropbox Variant) — JPMC Round 3 (LLD + HLD)

> **JPMC context:** Round 3, two separate reports — one explicitly "Design Google Drive,"
> another "Design Dropbox." Same core problem. The interview has a distinctive format:
> the interviewer **hands you a skeleton diagram** (`Client → Load Balancer → File Service → S3`)
> and says *"improve this for scale."* That's a gift — the skeleton tells you exactly
> what they want you to find wrong and fix.
>
> **Why this problem is different from Document Upload:** Document Upload is a *one-shot
> async ingestion*: one user uploads, one worker validates, done. Google Drive is a
> *collaborative, durable, hierarchical file system*: files have versions, folders have
> children, permissions cascade, and multiple users can be synced to the same file
> simultaneously. The SDE-3 signals are **block-level deduplication**, **version append
> semantics**, and **CDN offload** — none of which appear in a naive blob-store design.

---

## Index

| § | Section |
|---|---------|
| §1 | 🎯 Problem Statement |
| §2 | ❓ Clarifying Questions |
| §3a | 🏗️ LLD — Construction Guide (7 Moves) |
| §3b | 🏗️ LLD — Complete Class Diagram |
| §4 | 🧭 Design Decisions |
| §5 | 🔌 Key Interfaces |
| §6 | ⚙️ Code — Three Methods |
| §7 | 🔁 Concurrency |
| §8 | 🧨 Java Depth Probes |
| §9 | 🌐 HLD — 3-Phase Construction Guide |
| §10 | 🏛️ HLD Decisions |
| §11 | 📡 API Design |
| §12 | 🛤️ Happy + Unhappy Paths |
| §13 | 🔧 Fault Tolerance |
| §14 | 🔬 Q&A — Tier-2 JPMC Probes |
| §15 | 🧾 TL;DR |
| §16 | 🔄 Changelog |

---

## §1 — 🎯 Problem Statement

Design a cloud file storage service (Google Drive / Dropbox). Users upload files, organize them into folders, share them with other users, and expect to see each other's changes reflected near-real-time on any connected device. Files may be large (up to several GB). The same file content uploaded by two different users should ideally be stored only once (deduplication). Every save creates a new version; prior versions are retrievable.

**The one-line framing to say out loud in the interview:**
> *"This is a hierarchical file system over a blob store. The three axes that make it
> hard are: (1) large files need chunked upload with block-level dedup, (2) every
> save must create an immutable version so nothing is ever lost, and (3) permission
> changes on a folder must propagate to all descendants without a full table scan.
> The skeleton gives me S3 — my job is to make it fast, durable, shared, and synced."*

---

## §2 — ❓ Clarifying Questions

**Scale**

1. How many users and DAU? How many file operations per user per day? (drives read/write throughput)
2. What is the maximum file size? (decides chunking strategy)
3. What is the total storage quota per user and system-wide? (decides whether S3 or a custom blob store is appropriate)

**Functional scope**

4. Is real-time collaborative editing in scope (like Google Docs), or is this conflict-flagging (like Dropbox — last write wins or manual merge)?
5. How many versions of a file do we retain? Indefinitely, or a cap (e.g., last 100 versions)?
6. Does sharing a folder automatically share its contents? (permission inheritance)
7. Is full-text search (search by file content) in scope, or just filename search?

**Consistency**

8. How long can a sync be stale? Can a second user's client show the old version for a few seconds, or must it be instant?
9. If two users edit the same file simultaneously (Dropbox case), what is the conflict policy — last write wins, or duplicate + flag?

**Performance**

10. What is the P99 download latency requirement? (Drives CDN vs direct S3 decision)
11. Are there "hot" files (shared with thousands of users, downloaded millions of times) that need different caching than personal files?

**Resumability**

12. If a large file upload is interrupted at 80%, must the user restart from byte 0, or can they resume? (drives chunked upload design)

---

## §3a — 🏗️ LLD — Construction Guide (7 Moves)

---

**Move 1 — List domain nouns — don't draw yet**

Read the statement, then do **two passes**: one for nouns that are literally in the problem, one for entities the constraints force you to invent.

**From the statement directly:** File, Folder, SharePermission, User

**Derived — say the reason out loud for each one:**
- *"The problem says 'every save creates a new version; prior versions are retrievable.' A version is a distinct thing I track separately from the file — if I stored content on File directly, prior versions would be lost on each save."* → **FileVersion**
- *"The problem says 'files may be large (up to several GB).' A single HTTP PUT for a 1GB file will timeout and cannot be resumed mid-upload. I need to split files into fixed-size pieces I can upload independently. Once I have separate pieces, I can also deduplicate them by content hash."* → **FileChunk**
- *"The problem says 'changes reflected near-real-time on any connected device.' That means I need to push change notifications to clients — an event object the sync client consumes."* → **SyncEvent**

> **Say:** "`FileChunk` is the one entity that comes entirely from a constraint, not the problem text. The moment I hear 'files may be several GB,' I know I need chunked upload — and once I have chunks, tracking them as separate entities gives me deduplication and resumability for free. That is worth saying explicitly in the interview."

**Your board at the end of Move 1:**

```
From statement:  File, Folder, SharePermission, User
Derived:         FileVersion (versioning req), FileChunk (large-file constraint),
                 SyncEvent (real-time sync req)
```

---

**Move 2 — Classify each noun: entity / enum / service**

`ResourceType` (FILE / FOLDER) and `ShareRole` (OWNER / EDITOR / VIEWER) are enums. `File`, `Folder`, `FileVersion`, `FileChunk`, `SharePermission` are entities. Now add **services** — these come from asking *"who does the work?"*

- *"Something manages file upload, download, and versioning — that is `FileService`."*
- *"Something manages folders, sharing, and search — that is `MetadataService`."* (I split these because blob operations and metadata operations scale very differently — I'll explain in Move 6.)
- *"Permission checking requires recursively walking the folder tree and caching results. That logic is too complex to inline in `FileService`. It deserves its own abstraction."* → **PermissionChecker** (interface)
- *"Sync push mechanism could be WebSocket today, SSE tomorrow — swappable."* → **SyncNotifier** (interface)

> **Say:** "`ShareRole` is an enum — adding COMMENTER later is one line. `PermissionChecker` is an interface because the recursive tree-walk plus Redis caching is logic I want to isolate and test independently from file operations."

**Your board at the end of Move 2:**

```
enum:    ResourceType (FILE · FOLDER),
         ShareRole (OWNER · EDITOR · VIEWER · COMMENTER)
entity:  File, Folder, FileVersion, FileChunk, SharePermission, SyncEvent
service: StorageClient (iface), SearchIndexer (iface),
         PermissionChecker (iface), SyncNotifier (iface),
         FileService, MetadataService
```

---

**Move 3 — Draw enums first. Explain the non-obvious design.**

There is no interesting state machine for files (alive or deleted). But there is a crucial invariant for versions.

**How I arrive at "append-only" — trace the reasoning:**
The problem says *"prior versions are retrievable."* If prior versions must be retrievable, they must never be deleted or mutated. If they are never mutated, I can enforce immutability by design: never allow an UPDATE on a `FileVersion` row, only INSERTs. That is what "append-only" means — it is not an architectural preference, it falls directly out of the retrieval requirement.

**Why this matters:** Once you say append-only, revert becomes trivially easy. The old version rows are already there — unchanged. Revert is one UPDATE on `File.currentVersionId`. No data movement, no re-computation, no cost beyond a single DB write.

> **Say:** "FileVersion is append-only — not by choice but because the problem says prior versions must be retrievable. If they're retrievable, they must exist. If they must always exist, I can never mutate them. Append-only is just what that requirement looks like in the schema. The payoff is that revert costs exactly one UPDATE on File."

**Your board at the end of Move 3:**

```
FileVersion is APPEND-ONLY:
  FileVersion rows are immutable once created.
  currentVersion pointer on File = the only mutable state.
  Revert = UPDATE File SET currentVersionId = <old> (no data movement).

ShareRole hierarchy:
  OWNER > EDITOR > VIEWER > COMMENTER
  Folder permission INHERITS to all children unless overridden.
```

---

**Move 4 — Draw entities smallest → largest. Name what each knows + can do.**

`FileChunk` (content atom) → `FileVersion` (ordered list of chunks) → `File` (current version pointer) → `Folder` (tree node). `SharePermission` is a cross-cutting entity.

> **Why is `FileChunk` a separate entity and not just a list of hashes stored on `FileVersion`?**
> "Because multiple `FileVersion` rows — even across different users — can reference the same chunk. If I make `FileChunk` its own row with a stable `s3Key`, then two versions that share a 256KB paragraph both just reference the same `chunkId`. No bytes are stored twice. If I embedded hashes directly on `FileVersion`, I would still need a second lookup to get the `s3Key` for download — the entity makes that relationship explicit and queryable. The entity is what makes dedup a DB join instead of application logic."

> **For `File.currentVersionId`:** "This is the only mutable pointer in the whole design. Everything else is either immutable (`FileVersion`, `FileChunk`) or append-only (`SharePermission`). I'll put a `@Version` optimistic lock on `File` because two concurrent saves both try to move this pointer — I want exactly one to win."

> **Say:** "The non-obvious field on `FileChunk` is `contentHash` — that is what enables block-level deduplication. Two different files that share the same 256KB paragraph share the same chunk row in storage. The chunk is the unit of dedup, upload, and resumability."

**Your board at the end of Move 4:**

```
FileChunk { chunkId, contentHash, s3Key, sizeBytes }
  ← one chunk may be referenced by many FileVersions

FileVersion { versionId, fileId, chunkIds: List<String>,
              totalSize, checksum, createdBy, createdAt }
  ← IMMUTABLE once written

File { fileId, name, mimeType, ownerId, parentFolderId,
       currentVersionId, createdAt, updatedAt }
  + newVersion(chunkIds) : FileVersion
  + updateVersion(versionId) : void    ← @Version optimistic lock

Folder { folderId, name, ownerId, parentFolderId, createdAt }

SharePermission { permId, resourceId, resourceType: ResourceType,
                  granteeId, role: ShareRole, grantedBy, grantedAt }
```

---

**Move 5 — Identify variable behavior. Extract interfaces.**

Upload destination is swappable (S3 in prod, in-memory in test) → `StorageClient`. Search implementation varies (Elasticsearch vs MySQL LIKE) → `SearchIndexer`. Permission evaluation is recursive and complex → `PermissionChecker`. Sync events can be pushed via WebSocket, SSE, or polling → `SyncNotifier`.

**Why does `PermissionChecker` deserve its own interface — not just a helper method in `FileService`?**
Two reasons: (1) the logic walks a tree (file → parent folder → grandparent → root), stops when it finds an explicit grant, and returns null if it reaches the root without finding one. That is non-trivial recursive logic that would clutter every file operation if inlined. (2) The result of this walk is a perfect cache candidate — the same (userId, resourceId) pair is checked repeatedly. Wrapping it in `PermissionChecker` lets me cache inside the implementation without the caller knowing. `FileService` asks "can this user read file X?" — it does not care whether the answer came from Redis or from a DB tree-walk.

> **Say:** "I extract `PermissionChecker` as an interface because permission logic is recursive: file inherits from folder, folder inherits from parent folder, up to the root. That logic does not belong inside `FileService` — it is a separate concern and a natural seam for caching."

**Your board at the end of Move 5:**

```
interface StorageClient   { String uploadChunk(hash, bytes);
                            byte[] downloadChunk(s3Key); }
interface SearchIndexer   { void index(File, FileVersion);
                            List<File> search(userId, query); }
interface PermissionChecker { ShareRole effectiveRole(userId, fileId); }
interface SyncNotifier    { void notifyChange(fileId, SyncEvent); }
```

---

**Move 6 — Add the orchestrating service last. Its constructor deps = your design.**

`FileService` (upload, download, version) and `MetadataService` (folder ops, sharing, search) are the two service seams.

**Why two services and not one?** The two have opposite characteristics:
- `FileService` handles large blobs — write-once, read-many, I/O bound. Issuing presigned URLs, tracking chunk uploads, writing version records.
- `MetadataService` handles small rows — frequent, CRUD-heavy, CPU-light. Folder creation, permission grants, search.

If these live in one service, the blob I/O and the metadata CRUD share the same thread pool. Under a burst of large uploads, the metadata API slows down. Separate services scale independently: `FileService` scales with upload volume, `MetadataService` scales with user count. The constructor of each also enforces the boundary: `FileService` does not have a `PermRepo` injected, so it physically cannot make a permission DB call by accident.

> **Say:** "I split at the data boundary — blobs vs rows. FileService is write-once, large, I/O bound. MetadataService is frequent small CRUD. Separate thread pools, separate scale-out dials. The constructors enforce the separation: FileService cannot touch permission storage because it is never injected with it."

**Your board at the end of Move 6:**

```
FileService (storage, metaRepo, chunkRepo, versionRepo, notifier)
  + initiateUpload(userId, fileId, chunks) : UploadPlan
  + completeUpload(fileId, versionMeta)  : FileVersion
  + downloadFile(userId, fileId)         : DownloadUrl
  + getVersion(userId, fileId, versionId): DownloadUrl

MetadataService (fileRepo, folderRepo, permRepo, indexer, checker)
  + createFolder(userId, parentId, name) : Folder
  + share(userId, resourceId, grantee, role) : SharePermission
  + search(userId, query)               : List<File>
  + effectiveRole(userId, resourceId)   : ShareRole
```

---

**Move 7 — Name the hot resource. One sentence tying all locks to it.**

The hot resource is `File.currentVersionId` — the pointer that determines what "latest" means. Two concurrent saves to the same file must not silently overwrite each other.

> **Say:** "The contended field is `File.currentVersionId`. I guard it with `@Version` optimistic locking — the second concurrent writer's UPDATE fails and gets a conflict exception; the caller can then decide: re-fetch and retry, or branch into a conflict version. That prevents silent data loss without holding a long lock."

**Your board at the end of Move 7:**

```
HOT RESOURCE: File.currentVersionId
  guard = @Version optimistic lock on File entity
  conflict = OptimisticLockException → caller decides: retry or fork version
  large shared file reads = no lock (blob is immutable; CDN handles concurrency)
```

---

### 75% Rule — What to Draw First If Time Is Short

```
Priority 1 — must reach (10 min):
  • FileChunk with contentHash (enables dedup — the SDE-3 insight)
  • FileVersion is append-only (enables revert, audit)
  • File with currentVersionId + @Version guard
  • SharePermission with role enum (OWNER/EDITOR/VIEWER)
  • PermissionChecker interface (recursive resolution)

Priority 2 — draw if time allows:
  • Folder hierarchy (parentFolderId self-join)
  • SyncNotifier interface (event push)
  • FileService vs MetadataService split

Priority 3 — verbally mention, never draw:
  • SearchIndexer internals, encryption-at-rest, quota enforcement
```

---

## §3b — 🏗️ LLD — Complete Class Diagram — What You're Building Toward

```
┌─────────────────────────────────────────┐
│ «enum» ResourceType   FILE · FOLDER      │
└─────────────────────────────────────────┘
┌─────────────────────────────────────────┐
│ «enum» ShareRole                         │
│   OWNER · EDITOR · VIEWER · COMMENTER    │
└─────────────────────────────────────────┘

┌───────────────────────────────────────────┐
│ FileChunk              «content atom»      │
├───────────────────────────────────────────┤
│ - chunkId: String                         │
│ - contentHash: String  ← dedup key        │
│ - s3Key: String                           │
│ - sizeBytes: long                         │
└────────────────────┬──────────────────────┘
                     │ referenced by many
                     ▼
┌───────────────────────────────────────────┐
│ FileVersion          «IMMUTABLE»           │
├───────────────────────────────────────────┤
│ - versionId: String                       │
│ - fileId: String                          │
│ - chunkIds: List<String>                  │
│ - totalSize: long                         │
│ - checksum: String    (sha256 of whole)   │
│ - createdBy: String                       │
│ - createdAt: Instant                      │
└────────────────────┬──────────────────────┘
                     │ 0..*
┌───────────────────────────────────────────┐
│ File                                       │
├───────────────────────────────────────────┤
│ - fileId: String                          │
│ - name: String                            │
│ - mimeType: String                        │
│ - ownerId: String                         │
│ - parentFolderId: String                  │
│ - currentVersionId: String  ← HOT field  │
│ - @Version: int             ← OCC guard  │
│ - createdAt, updatedAt: Instant           │
├───────────────────────────────────────────┤
│ + newVersion(chunkIds): FileVersion       │
│ + updateVersion(versionId): void          │
└────────────────────┬──────────────────────┘
                     │ lives in
                     ▼
┌───────────────────────────────────────────┐
│ Folder                                     │
├───────────────────────────────────────────┤
│ - folderId: String                        │
│ - name: String                            │
│ - ownerId: String                         │
│ - parentFolderId: String  (null = root)   │
│ - createdAt: Instant                      │
└───────────────────────────────────────────┘

┌───────────────────────────────────────────┐
│ SharePermission                            │
├───────────────────────────────────────────┤
│ - permId: String                          │
│ - resourceId: String                      │
│ - resourceType: ResourceType              │
│ - granteeId: String                       │
│ - role: ShareRole                         │
│ - grantedBy, grantedAt                    │
└───────────────────────────────────────────┘

┌────────────────────────────────────┐  ┌────────────────────────────────────┐
│ «interface» StorageClient          │  │ «interface» PermissionChecker       │
│ + uploadChunk(hash, bytes): String │  │ + effectiveRole(userId, resourceId) │
│ + downloadChunk(s3Key): byte[]     │  │     : ShareRole                     │
└────────────────────────────────────┘  └────────────────────────────────────┘
┌────────────────────────────────────┐  ┌────────────────────────────────────┐
│ «interface» SearchIndexer          │  │ «interface» SyncNotifier            │
│ + index(File, FileVersion): void   │  │ + notifyChange(fileId,             │
│ + search(userId, query): List<File>│  │      SyncEvent): void               │
└────────────────────────────────────┘  └────────────────────────────────────┘

┌────────────────────────────────────────────────────────────────┐
│ FileService   deps: StorageClient, ChunkRepo, VersionRepo,      │
│               FileRepo, SyncNotifier, PermChecker               │
│  + initiateUpload(userId, fileId, chunkHashes): UploadPlan      │
│  + completeUpload(fileId, chunkIds): FileVersion                │
│  + downloadFile(userId, fileId): DownloadUrl                    │
│  + getVersion(userId, fileId, versionId): DownloadUrl           │
└────────────────────────────────────────────────────────────────┘
┌────────────────────────────────────────────────────────────────┐
│ MetadataService   deps: FileRepo, FolderRepo, PermRepo,         │
│                   SearchIndexer, PermChecker                    │
│  + createFolder(userId, parentId, name): Folder                 │
│  + share(userId, resourceId, granteeId, role): SharePermission  │
│  + search(userId, query): List<File>                            │
└────────────────────────────────────────────────────────────────┘

KEY INVARIANT: FileVersion rows are never mutated after creation. The only
mutable pointer is File.currentVersionId, guarded by @Version OCC. Block-level
dedup means two versions sharing identical chunks reference the same FileChunk row.
```

---

## §4 — 🧭 LLD — Design Decisions

| Decision | Why this | What I rejected and why |
|---|---|---|
| **FileVersion is append-only** | Revert to any version = one UPDATE on File, no data movement; audit log is free | Mutable file content — one overwrite loses history permanently |
| **Chunk-level storage + `contentHash` dedup** | Identical 256KB blocks (common in docs) stored once regardless of how many files reference them; enables resumable upload (re-upload only the missing chunks) | Per-file dedup only — two near-identical 1GB files each fully stored; no resumability |
| **`@Version` optimistic lock on File.currentVersionId** | Two concurrent saves to the same file: the loser gets `OptimisticLockException` and can decide to retry or fork; no long lock held during the upload | Pessimistic lock — holds a lock for the duration of a potentially multi-minute upload |
| **Recursive permission resolution (file → parent → root)** | Folder sharing cascades to all contents automatically; no need to stamp every child row | Stamp every child row on share — fast reads but O(N) writes when sharing a large folder |
| **FileService vs MetadataService split** | Blob access is write-once / read-many on large payloads; metadata is frequent small-row CRUD — different scaling profiles, different DB choices | Monolith handling both — the blob-serving I/O drowns the metadata query throughput |
| **Block-level deduplicated chunks (256KB default)** | Sub-file granularity means a 10MB document with one paragraph changed re-uploads only the changed chunks | File-level dedup — any byte change = full re-upload; worse for large, frequently edited files |

---

## §5 — 🔌 LLD — Key Interfaces

| Interface | Contract |
|---|---|
| `StorageClient` | Stores/retrieves individual chunks by content-addressed key. Implementations: S3Adapter, InMemoryAdapter (test). |
| `PermissionChecker` | Resolves the effective role for a user on a resource, walking up the folder tree. Cached in Redis per (userId, resourceId) pair. |
| `SearchIndexer` | Indexes file metadata + text-extracted content; supports user-scoped keyword search. |
| `SyncNotifier` | Pushes sync events (file created / updated / deleted) to connected clients of the affected users. |

```java
public interface StorageClient {
    // Uploads chunk bytes; returns the s3Key for future retrieval.
    // Idempotent: uploading the same contentHash twice is safe.
    String uploadChunk(String contentHash, byte[] bytes);

    // Retrieves raw bytes for a previously uploaded chunk.
    byte[] downloadChunk(String s3Key);
}

public interface PermissionChecker {
    // Returns the effective role for userId on the given resourceId.
    // Walks up the folder tree until an explicit grant is found;
    // returns null if no grant exists (i.e., access denied).
    ShareRole effectiveRole(String userId, String resourceId);
}

public interface SyncNotifier {
    // Pushes a file change event to all clients subscribed to fileId.
    // Fire-and-forget; delivery is best-effort (clients also poll on reconnect).
    void notifyChange(String fileId, SyncEvent event);
}
```

---

## §6 — ⚙️ LLD — Code to Write

Three methods carry the non-obvious design weight: the **chunk-dedup upload plan**, the **recursive permission resolver**, and the **optimistic concurrent version update**.

---

### 1. The chunk-dedup upload plan — `initiateUpload` (the SDE-3 insight)

**Steps in plain English:**

1. **Receive the client's chunk manifest** — a list of (index, contentHash) pairs for each 256KB chunk.
2. **Look up each hash in the chunk table** — if a chunk with that hash already exists (in any user's file), we already have the bytes.
3. **Return two lists:** chunks we need (client must upload) and chunks we already have (client skips). This is the "rsync-style delta."
4. **This collapses dedup + resumability into one call** — a resumed upload sends only the missing chunks, not the whole file.

```java
public UploadPlan initiateUpload(String userId,
                                 String fileId,
                                 List<ChunkManifestEntry> manifest) {
    // Step 1 — manifest = [{index=0, hash="abc…"}, {index=1, hash="def…"}, …]
    List<String> toUpload = new ArrayList<>();
    List<String> alreadyHave = new ArrayList<>();

    for (ChunkManifestEntry entry : manifest) {
        // Step 2 — dedup check: does this hash exist in ANY user's storage?
        boolean exists = chunkRepo.existsByContentHash(entry.contentHash());
        if (exists) {
            // Step 3a — we already have these bytes; client skips them
            alreadyHave.add(entry.contentHash());
        } else {
            // Step 3b — client must upload these chunks
            toUpload.add(entry.contentHash());
        }
    }

    // Step 4 — return the delta plan: upload only what's missing
    // toUpload list = union of dedup savings + resume savings
    return UploadPlan.of(fileId, toUpload, alreadyHave);
}
```

---

### 2. Recursive permission resolution — `effectiveRole` (the non-obvious algo)

**Steps in plain English:**

1. **Check Redis cache** — most permission checks hit the same (userId, resourceId) pair repeatedly.
2. **Cache miss: check for an explicit grant on this resource.**
3. **No explicit grant: walk up to the parent** — files inherit from their folder, folders from their parent folder.
4. **Root (parentId == null) with no grant: return null** — access denied.
5. **Cache the resolved role** in Redis (short TTL, because grants change).

```java
public ShareRole effectiveRole(String userId, String resourceId) {
    // Step 1 — Redis cache: avoid recursive DB lookups on every read
    String cacheKey = "perm:" + userId + ":" + resourceId;
    String cached = redis.get(cacheKey);
    if (cached != null) {
        return "NONE".equals(cached) ? null : ShareRole.valueOf(cached);
    }

    // Step 2 — explicit grant on this resource
    Optional<SharePermission> direct =
        permRepo.findByGranteeAndResource(userId, resourceId);
    if (direct.isPresent()) {
        ShareRole role = direct.get().getRole();
        redis.setex(cacheKey, 60, role.name());
        return role;
    }

    // Step 3 — no explicit grant: resolve parent
    String parentId = resolveParentId(resourceId);
    if (parentId == null) {
        // Step 4 — reached root with no grant: access denied
        redis.setex(cacheKey, 60, "NONE");
        return null;
    }

    // Step 5 — recurse up the tree; cache the resolved result
    ShareRole inherited = effectiveRole(userId, parentId);
    String toCache = (inherited != null) ? inherited.name() : "NONE";
    redis.setex(cacheKey, 60, toCache);
    return inherited;
}
```

---

### 3. Optimistic concurrent version update — `updateVersion`

**Steps in plain English:**

1. **Call the ORM update** inside a transaction — JPA will append the `WHERE version = ?` clause automatically.
2. **If 0 rows updated**: another writer moved `currentVersionId` first — throw `ConflictException`.
3. **Caller decides** whether to retry (merge) or fork (create a conflict copy).

```java
// Entity carries @Version — JPA appends WHERE version = ? automatically
// on every UPDATE, protecting against concurrent pointer moves.
@Version
private int version;

// Called by FileService after all chunks are durably in S3
public void updateVersion(String newVersionId) {
    // Step 1 — ORM UPDATE includes WHERE version = <current> automatically
    this.currentVersionId = newVersionId;
    this.updatedAt = Instant.now();
    // Step 2 — if another writer already updated, JPA throws
    // OptimisticLockException → FileService catches it
}

// In FileService:
@Transactional
public FileVersion completeUpload(String fileId, List<String> chunkIds) {
    File file = fileRepo.findById(fileId);
    FileVersion version = file.newVersion(chunkIds);
    versionRepo.insert(version);
    try {
        file.updateVersion(version.getVersionId());
        fileRepo.save(file);  // OCC check happens here
    } catch (OptimisticLockException e) {
        // Step 3 — concurrent write: version still saved; caller gets conflict signal
        throw new ConflictException("Concurrent edit on file " + fileId
            + "; saved as version " + version.getVersionId()
            + " — resolve conflict or retry");
    }
    notifier.notifyChange(fileId, SyncEvent.updated(version));
    return version;
}
```

---

## §7 — 🔁 LLD — Concurrency

| Shared field | What breaks without a guard | Fix |
|---|---|---|
| `File.currentVersionId` | Two concurrent saves both update — the first writer's version is silently overwritten | `@Version` OCC on File entity — the second writer throws `OptimisticLockException` |
| `FileChunk` existence | Two uploaders check "does hash exist?" simultaneously, both see false, both upload — double storage | `INSERT IGNORE` or `ON CONFLICT DO NOTHING` on chunk insert; content-addressed key means idempotent |
| `SharePermission` for same (resourceId, granteeId) | Two concurrent share calls insert duplicates — ambiguous effective role | `UNIQUE(resource_id, grantee_id, resource_type)` DB constraint — second insert fails; UI shows current grant |
| Permission cache in Redis | Grant revoked; stale cached role allows access for up to TTL | Short TTL (60s acceptable for sharing changes); explicit cache invalidation on `revoke()` |

**Thread model note:** Download is entirely lock-free — `FileVersion.chunkIds` is immutable, S3 blobs are content-addressed and immutable, and `currentVersionId` is read without a lock (stale read is acceptable for downloads — the user gets the version that was current when they clicked).

---

## §8 — 🧨 Java Depth Probes

| What you say in design | What they immediately ask | Your answer |
|---|---|---|
| "block-level dedup via contentHash" | "What hash function? What happens on a hash collision?" | SHA-256 in practice (collision probability negligible for this scale). If paranoid: also check sizeBytes — a collision that passes both checks is effectively impossible. Content-addressed storage is the industry standard (Git objects, S3 Etag). |
| "`@Version` OCC on File" | "What does the client do when it gets a ConflictException?" | Dropbox behavior: save the incoming version as a `conflicted copy` — both versions survive. Google Docs: prevent the conflict entirely with operational transforms. For JPMC I'd pick Dropbox semantics (last-write-wins is acceptable for most file types; the conflict copy is the safety net). |
| "recursive permission walk up the tree" | "What if the folder tree is 20 levels deep?" | Cache aggressively. On a cache hit the recursion never happens. On a cache miss I limit the traversal to MAX_DEPTH (e.g., 20 levels) and throw if exceeded. In practice real folder trees are 3–5 levels deep. |
| "pre-signed S3 URL for download" | "How do you authorize a download via a pre-signed URL if the URL leaks?" | Short expiry (5 min default). For sensitive documents: require the user to present their JWT first and generate a one-time URL that invalidates after first use. |
| "CDN for read-heavy shared files" | "How does the CDN know when a file changes?" | Files are content-addressed by version — the URL includes the versionId. An old URL still resolves to the old version's bytes (immutable); the client learns about the new versionId via the sync event and fetches the new CDN URL. No cache invalidation needed. |

---

## §9 — 🌐 HLD — How to Build This Diagram in the Interview — 3 Phases

### Phase 1 — Numbers First (≈2 min)

```
DAU                         10M active users
Files modified/user/day     5 average       → 50M file ops/day
Active window               12 h (43,200 s)
Avg file operations/sec     50M / 43,200    ≈ 1,160/sec average
Peak (3× spike)             ≈ 3,500 file ops/sec
Avg file size               1 MB  (mix of docs, spreadsheets, images)
Upload byte throughput      3,500/sec × 1 MB = 3.5 GB/sec  → S3, never our servers
Download throughput         ~5× uploads (read-heavy) ≈ 17.5 GB/sec → CDN mandatory
Storage quota               10 GB/user × 10M users = 100 PB system-wide → S3 / object store
Metadata rows               ~100 files/user × 10M = 1B rows → MySQL + sharding OR Cassandra
Sync events                 5M changes/day → ~58 sync events/sec; ~1,000 active syncs at peak
Permission checks           every download/upload = ~7K checks/sec → Redis cache mandatory
```

**What the numbers force:**
- **3.5 GB/sec uploads** → **direct pre-signed S3 upload** (not through our servers).
- **17.5 GB/sec downloads** → **CDN** for hot/shared files; pre-signed S3 for personal cold files.
- **7K permission checks/sec** → **Redis permission cache** (DB can't absorb this).
- **1B metadata rows** → **MySQL with sharding by userId**, or Cassandra (wide-column).
- **58 sync events/sec** → **WebSocket/SSE push** is manageable; polling would drown the API.

---

### Phase 2 — Skeleton: What the Interviewer Gives You (≈2 min)

> **Note for the interview:** The interviewer will hand you this diagram and say "improve it."
> Spend 60 seconds finding all the breaking points before drawing anything new.

```
── Skeleton: Interviewer-Given Diagram ───────────────────────────

   ┌───────────────────────────┐
   │  Client                   │
   └──────────────┬────────────┘
                  │ HTTPS
   ┌──────────────▼────────────┐
   │  Load Balancer            │
   └──────────────┬────────────┘
                  │
   ┌──────────────▼────────────┐
   │  File Service             │
   │   - upload file bytes     │
   │   - download file bytes   │
   └──────┬──────────┬─────────┘
          │          │
          ▼          ▼
   ┌──────────┐  ┌────────────────────────┐
   │  S3      │  │  MySQL  (metadata)     │
   └──────────┘  └────────────────────────┘

BREAKING POINT:
  (a) Large files (1 GB) flow THROUGH the File Service → 17.5 GB/sec throughput
      bottleneck; service needs petabytes of RAM to buffer in-flight uploads.
  (b) No versioning → a single overwrite destroys previous content permanently.
  (c) No sharing model → every file is private to the uploader.
  (d) All downloads go through the File Service → same bandwidth bottleneck;
      a popular shared file brings it down.
  (e) No chunking → a dropped connection mid-upload requires full restart.
  (f) No sync mechanism → client B has no way to learn that client A changed a file.
  (g) No deduplication → two users uploading the same 10 GB file = 20 GB stored.
══════════════════════════════════════════════════════════════════
```

---

### Phase 3 — Upgrade It: One Fix per Pain Point (≈5 min)

```
PAIN POINT (a) + (d) → Pre-signed S3 upload URL (direct) + CDN for downloads
  Why this works: the File Service becomes a metadata orchestrator only. Bytes
  go Client → S3 directly on upload, and Client → CDN → S3 on download for hot
  files. File Service bandwidth drops to near zero.

PAIN POINT (b) → Append-only FileVersion rows; File.currentVersionId pointer
  Why this works: a save = INSERT FileVersion + UPDATE File. The old version row
  is never touched. Revert = flip currentVersionId. Zero data movement.

PAIN POINT (c) → SharePermission table with role enum + recursive resolution
  Why this works: one table covers file and folder grants. Folder grants cascade
  to children via the recursive walk. Cached in Redis per (userId, resourceId).

PAIN POINT (e) → Chunk-based upload (256KB chunks) + client-side dedup manifest
  Why this works: `initiateUpload` returns which chunks are already stored; client
  uploads only deltas. A dropped connection at 80% resumes from chunk N+1.

PAIN POINT (f) → Kafka (topic: file-events) + WebSocket/SSE push to sync clients
  Why this works: every file change publishes an event; a fan-out consumer notifies
  all clients subscribed to that file's shared users within seconds.

PAIN POINT (g) → Block-level dedup via FileChunk.contentHash
  Why this works: identical 256KB blocks are stored once; two near-identical files
  share the common chunks. Cross-user dedup is safe because chunks are immutable
  and content-addressed — a given hash always maps to the same bytes.
```

---

### ✅ Production Diagram — What You're Building Toward

```
── Production: All Upgrades Applied ──────────────────────────────

   ┌────────────────────────────────────────────┐
   │  Client A (uploader)   Client B (synced)    │
   └──────────┬──────────────────────┬───────────┘
              │                      │ WebSocket / SSE
              │ HTTPS                │ (sync events)
   ┌──────────▼──────────────────────▼───────────────────────────┐
   │  API Gateway  (JWT · rate-limit · routing)                   │
   └──────┬──────────────────────────────────────┬───────────────┘
          │ POST /upload · GET /download          │ WS /sync
          │ · GET /status · PATCH /share          │
   ┌──────▼────────────────────────┐  ┌───────────▼──────────────┐
   │  FileService                  │  │  SyncService              │
   │  initiateUpload → UploadPlan  │  │  WebSocket / SSE hub      │
   │  completeUpload → FileVersion │  │  push to subscribers      │
   │  downloadFile  → presign URL  │  └───────────┬──────────────┘
   └──────┬────────────────────────┘              │ consume
          │ presigned PUT / GET                   ▼
          ▼                          ┌─────────────────────────────┐
   ┌────────────────────────────┐    │  Kafka  (topic: file-events) │
   │  S3  (chunk blobs)         │    │  key = userId (shared space) │
   │  content-addressed by hash │    └──────────┬──────────────────┘
   └────────────────────────────┘               │ publish
          ▲ CDN (read hot files)                │
          │ (immutable URL per versionId)        │
   ┌──────┴────────────────────────────────────▼─────────────────┐
   │  MetadataService                                              │
   │  createFolder · share · search · effectiveRole               │
   └───┬──────────────────────────────────────────────────────────┘
       │                     │ permission checks
       ▼                     ▼
   ┌───────────────┐  ┌───────────────────────────────────────────┐
   │  Redis        │  │  MySQL (sharded by userId)                 │
   │  perm cache   │  │  file · folder · file_version ·           │
   │  perm:{u}:{r} │  │  file_chunk · share_permission            │
   │   TTL 60s     │  │  ← FileService · MetadataService          │
   └───────────────┘  └───────────────────────────────────────────┘
       ▲ also caches:
       │  session tokens (TTL 15 min)
   ┌───┴────────────────────────────────────────────────────────┐
   │  Search Service  (Elasticsearch)                            │
   │  index on: fileName, mimeType, text-extracted content       │
   │  query scoped to userId's accessible files                  │
   └────────────────────────────────────────────────────────────┘

KEY INVARIANT: file bytes never flow through the File Service — S3 handles
all upload/download bandwidth. Every save creates an immutable FileVersion;
File.currentVersionId is the only mutable pointer, protected by @Version OCC.
Identical chunks across any users share one storage row — dedup is free because
storage is content-addressed by hash.
══════════════════════════════════════════════════════════════════
```

---

## §10 — 🏛️ HLD — Decisions

| Component | Why chosen | What I rejected and why |
|---|---|---|
| **Pre-signed S3 for upload + CDN for download** | 17.5 GB/sec of bytes must skip our services | Proxy through File Service — bandwidth bottleneck, massive memory pressure on in-flight large files |
| **Append-only FileVersion + currentVersionId pointer** | Revert is a one-row update; audit is free; no data movement on save | Overwrite in place — one accident loses history; no revert possible |
| **Chunk-based upload (256KB blocks)** | Enables resumability (re-upload only missing chunks) and cross-user dedup | File-level upload — no resumability; full re-upload on any network drop |
| **Redis permission cache (TTL 60s)** | 7K permission checks/sec; DB cannot absorb this | Evaluate permissions from DB on every request — DB saturation under normal load |
| **MySQL sharded by userId** | File/folder ownership is naturally user-scoped; hot-user sharding is well-understood | Single MySQL — 1B rows at 3.5 GB/sec write throughput is impossible for one node |
| **Kafka + WebSocket for sync** | Sync events need fan-out to multiple connected clients; WebSocket provides push | Polling — 10M users polling every 5s = 2M polls/sec; dwarfs actual file activity |
| **Elasticsearch for search** | Full-text search across 100B+ rows with relevance ranking | MySQL LIKE — no full-text, no ranking, full-table scan at 1B rows |

---

## §11 — 📡 HLD — API Design

```
// Phase 1: initiate upload → get delta plan (which chunks to send)
POST /v1/files/{fileId}/upload
Authorization: Bearer <token>
Body: { chunkManifest: [{index: 0, hash: "abc…"}, {index: 1, hash: "def…"}] }

Response: 200 OK
          {
            fileId: "file_abc…",
            chunksRequired: ["abc…"],       ← upload these only
            chunksPresent:  ["def…"],       ← already stored; skip
            uploadUrls: { "abc…": "https://s3.../chunk/abc?X-Amz-Signature=…" }
          }

// Phase 2: client PUTs required chunks directly to S3 (bypass our servers)

// Phase 3: notify completion → creates new FileVersion
POST /v1/files/{fileId}/complete
Body: { chunkIds: ["abc…", "def…"], totalSize: 2097152, checksum: "sha256…" }
Response: 201 Created { versionId, fileId, createdAt }
```

```
GET /v1/files/{fileId}/download?version={versionId}
Authorization: Bearer <token>
Response: 302 Redirect to CDN URL (hot/shared) or pre-signed S3 URL (personal cold)
          // URL encodes versionId → CDN URL is immutable → no cache invalidation needed
```

```
GET /v1/files/{fileId}/versions
Response: [ { versionId, size, createdBy, createdAt }, … ]   // paginated

PATCH /v1/files/{fileId}/revert
Body: { targetVersionId: "ver_789…" }
Response: 200 OK { currentVersionId: "ver_789…" }
          // DB UPDATE only — no byte movement; old version rows are never deleted
```

```
POST /v1/resources/{resourceId}/share
Body: { granteeId: "user_xyz", role: "EDITOR" }
Response: 201 Created { permId, role, grantedAt }

GET /v1/files?q=quarterly+report
Response: [ { fileId, name, mimeType, path, currentVersionId }, … ]
          // scoped to files the caller can access
```

---

## §12 — 🛤️ Happy + Unhappy Paths

**Happy path — upload a new version of an existing file:**
1. Client sends chunk manifest (hashes) to `POST /files/{id}/upload` → `initiateUpload` returns delta plan.
2. Client PUTs only the missing chunks directly to S3 (pre-signed URLs).
3. Client calls `POST /files/{id}/complete` → `completeUpload` inserts `FileVersion`, updates `File.currentVersionId` with `@Version` OCC, publishes to Kafka.
4. Kafka fan-out → `SyncService` pushes WebSocket event to all clients subscribed to this file.
5. Client B's sync client receives event, fetches new `currentVersionId`, downloads delta chunks from CDN.

**Unhappy path — concurrent edit conflict:**
→ Client A and Client B both open a file at version V2, both edit, both call `completeUpload`
→ First writer (say Client A) wins the `@Version` OCC; their update succeeds, file is now V3
→ Second writer (Client B) catches `OptimisticLockException`
→ Server saves Client B's content as a new version anyway (V4), but flags it as a conflict copy
→ Client B is notified: "conflict — a newer version exists; please review"

**Unhappy path — upload interrupted at chunk 8 of 10:**
→ Client disconnects after uploading chunks 0–7
→ Client reconnects; calls `initiateUpload` again with the same manifest
→ `initiateUpload` finds chunks 0–7 already stored → returns `chunksRequired = [8, 9]`
→ Client uploads only the remaining two chunks → `completeUpload` proceeds normally

**Unhappy path — permission revoked mid-session:**
→ User A shares folder with User B; User B opens a file from that folder
→ User A revokes share → `SharePermission` row deleted, Redis cache invalidated (`DEL perm:B:*`)
→ User B's next request triggers a permission check → cache miss → DB walk finds no grant → 403
→ User B's WebSocket may briefly remain connected; next file event triggers re-auth

---

## §13 — 🔧 HLD — Fault Tolerance

| External call | What breaks | What you add |
|---|---|---|
| S3 (chunk upload) | Client PUT fails (S3 timeout / network drop) | Resumable upload: client retries `initiateUpload`; only missing chunks re-uploaded |
| S3 (download) | S3 unavailable → CDN still serves cached copies of hot files | CDN origin-shield: stale-while-revalidate; personal cold files surface a 503 |
| MySQL (metadata) | Primary down → writes fail | MySQL primary with read replica; writes fail-safe (no silent data loss); replica promotes via failover manager |
| Redis (permission cache) | Cache down → fall through to MySQL for permission checks | Fallback: every check hits MySQL (slower, correct). Deploy Redis Cluster to minimize single-node risk |
| Kafka (sync events) | Broker down → sync events not delivered | Clients also poll `/sync/delta?since={lastEventId}` on reconnect; DB is the source of truth for file state |
| ElasticSearch (search) | Indexer lag → search results stale | Acceptable: search shows results as of last index sync. Search failure falls back to MySQL filename LIKE match (degraded, not broken) |
| WebSocket (sync push) | Client disconnects → misses sync events | Client reconnects and fetches missed events via `GET /sync/events?since={lastSeenEventId}`; Kafka retention window covers the gap |

---

## §14 — 🔬 Q&A — Tier-2 JPMC Probes

### Q: "The skeleton has Client → LB → File Service → S3. What's wrong with it?"
> Seven things: (a) large file bytes flow through the service — bandwidth bottleneck; (b) no versioning — one overwrite = permanent loss; (c) no sharing model; (d) all downloads also go through the service — same bottleneck; (e) no chunking — any dropped connection requires full restart; (f) no sync — client B never knows client A changed a file; (g) no dedup — identical files stored N times. My upgrades address each one independently.

### Q (Tier-2): "Block-level dedup sounds clever. What's the actual storage savings?"
> For documents: very high. A 50-page Word file edited daily shares 95%+ of its chunks across versions. For random binary data (video, encrypted files): near zero — every chunk is unique. The 256KB chunk size is a practical sweet spot — small enough for good dedup on structured docs, large enough that the chunk table itself doesn't become the bottleneck (one chunk metadata row ≈ 200B; 1M chunks = 200MB in the chunk table).

### Q (Tier-2): "How does client B know when client A edits a shared file?"
> A Kafka event is published on every `completeUpload`. A fan-out consumer maps the fileId to all users with READ+ access (from the SharePermission table), then pushes a WebSocket/SSE message to each connected client. Client B's sync client receives the message, fetches the new `currentVersionId`, and downloads only the changed chunks via CDN. On reconnect after a gap: client polls `GET /sync/events?since={lastEventId}` to catch up.

### Q (Tier-2): "You said permission checks are cached in Redis with TTL 60s. If I revoke Bob's access, he can still read the file for up to a minute?"
> Correct — and that is a deliberate trade-off. On revoke I explicitly call `DEL perm:bob:*` in Redis (eager invalidation), so in practice the window is the replication lag between the revoke write and the cache delete, which is milliseconds. The TTL is a safety net for cache node failures. If the product requires zero-tolerance (e.g., GDPR "right to delete"), I lower the TTL and add a mandatory cache flush on every share change.

### Q: "Why append-only for FileVersion? What about storage costs?"
> The version table grows linearly with edits, but only stores metadata (chunkIds, checksums, timestamps — ≈1KB per version). The bytes are content-addressed in S3 and shared across versions — if only 2 of 40 chunks changed, 38 chunks are not duplicated. A 10-version file history adds 10 metadata rows (10KB) and at most 10 × (changed chunks × 256KB) of new bytes. A configurable retention policy (e.g., keep last 100 versions, archive older) bounds the storage cost.

### Q: "How would you implement 'restore to a previous version'?"
> One UPDATE: `File.currentVersionId = targetVersionId`, guarded by `@Version`. The `FileVersion` row for `targetVersionId` already exists with its chunk list. No bytes move. The FileService generates download URLs by reading `FileVersion.chunkIds` — pointing at the old chunks is identical to pointing at the new ones. Publish a sync event so all clients fetch the restored version.

---

## §15 — 🧾 TL;DR — 30-Second Pitch

> "The skeleton gives me S3 — my job is to add the seven things it is missing.
> **Bytes never touch my service:** clients upload chunks directly to S3 via pre-signed
> URLs; downloads go through CDN for hot/shared files. **Every save is a new immutable
> FileVersion** — the only mutable pointer is `File.currentVersionId`, guarded by `@Version`
> OCC (concurrent writes surface as conflict copies, not silent overwrites). **Block-level
> dedup** via `contentHash` on `FileChunk` means identical 256KB blocks across any user's
> files share one S3 object — dedup + resumability fall out of the same chunk manifest.
> **Permissions cascade recursively** from folder to children; resolved once and cached in
> Redis (60s TTL, eager invalidation on revoke). **Sync** is Kafka fan-out → WebSocket push
> to connected clients; client reconnect fetches missed events via a delta endpoint. The one
> question I want answered first: is real-time collaborative editing in scope, or is
> Dropbox-style last-write-wins + conflict copy acceptable? That decides whether I need
> operational transforms (very complex) or the OCC approach I just described (tractable)."

---

## §16 — 🔄 Changelog

| Date | Change |
|------|--------|
| Aug 17, 2026 | Note created. JPMC Round 3 "skeleton given — improve it" format. Full 16-section arc: block-level chunk dedup + append-only FileVersion + @Version OCC + recursive permission cache + Kafka sync fan-out + CDN for hot downloads. Numbers force direct S3 upload, CDN, Redis permission cache, MySQL sharding, Kafka+WebSocket push. Seven breaking points in the skeleton explicitly addressed. |
| Aug 17, 2026 | §3a revised: all 7 moves now include derivation reasoning — `FileChunk` traced to "files may be large (GB)" constraint, `SyncEvent` traced to real-time sync requirement, `FileVersion` append-only traced to "prior versions retrievable" requirement, `FileChunk` as separate entity justified vs embedding hashes on FileVersion, `PermissionChecker` interface justified by recursive complexity + cache seam, two-service split justified at blob-vs-row boundary. |
