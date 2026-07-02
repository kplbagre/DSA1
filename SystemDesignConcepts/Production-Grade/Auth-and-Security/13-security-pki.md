# Security + PKI Fundamentals

> **Standard followed:** `SystemDesignConcepts/notes-standards.md`

---

## 🎯 Why This Matters

DocuSign's core product is legally binding digital signatures. If they ask D1 ("Design a Digital Signature System"), your answer lives or dies on whether you understand how signatures actually work under the hood — PKI, asymmetric cryptography, non-repudiation, audit trails. These are not abstract concepts; they map directly to DocuSign's architecture. Senior engineers at any company building secure APIs are expected to know why HTTPS works, how JWTs are signed, and what "the server can prove you sent this" means technically.

**Which round:** R2 System Design — D1 (Digital Signature System) and any secure API design question.
**Why senior engineers own this:** Anyone can say "use HTTPS." Senior engineers explain the key exchange underneath it, why we hash before signing, and how the certificate chain prevents impersonation.

---

## 📖 What is Security & PKI?

**Full form:** Public Key Infrastructure / Asymmetric Cryptography

**Simple analogy:** A medieval king seals important letters with his unique wax seal (private key). Anyone can verify the letter came from him by checking the seal matches his known coat of arms (public key). No one else has his signet ring — so only he could have sealed it (non-repudiation).

**Core principle:** PKI uses two keys (private and public):
- **Private key** (kept secret): signs documents and encrypts data. Proves your identity.
- **Public key** (shared openly): verifies signatures and decrypts data. Anyone can use it to confirm YOU sent the message.

A **Certificate Authority** (trusted third party) vouches for you: "I confirm this public key belongs to Alice." This chain of trust enables HTTPS, digital signatures, and secure APIs.

**Why it matters in system design:** Enables authentication (prove who you are), non-repudiation (you can't deny sending something), and integrity verification (detect tampering).

---

## 📖 Terminology Table

| Term | Plain-English Meaning | Example |
|---|---|---|
| PKI | Public Key Infrastructure — the system of keys, certificates, and Certificate Authorities that makes "trust a public key you've never seen" possible | HTTPS, DocuSign signatures, JWT RS256 signing all rely on PKI |
| Private Key | A secret cryptographic key kept only by its owner — used to sign documents and prove identity | RSA 2048-bit key stored in an HSM; never shared |
| Public Key | The freely shareable counterpart to the private key — used to verify signatures and encrypt messages to the key owner | Published in a TLS certificate or a JWKS endpoint |
| Certificate Authority | A trusted organization that signs certificates attesting "this public key belongs to this entity" | Let's Encrypt, DigiCert — their root public keys are pre-installed in browsers/OS |
| Digital Signature | The hash of a document encrypted with the signer's private key — proves both identity and document integrity | `SHA256(document)` encrypted with private key → attached to the PDF |
| Non-Repudiation | The property that a signer cannot later deny having signed — because only their private key could produce the valid signature | DocuSign's legal admissibility depends on non-repudiation |
| TLS Handshake | The protocol where client and server use asymmetric crypto to authenticate and exchange a symmetric session key, then switch to fast symmetric encryption | Browser connects to docusign.com → verifies certificate chain → establishes AES session |
| Hash Function (SHA-256) | A one-way function that converts any input to a fixed 32-byte digest — any change to input changes the digest completely | `SHA-256("hello") = 2cf24dba...` — used to hash documents before signing |
| X.509 Certificate | The standard format for public key certificates — contains the entity's public key, validity dates, issuer, and the CA's signature | docusign.com's TLS certificate is an X.509 cert signed by an Intermediate CA |

---

## 🎨 Visual — System Topology: PKI & Security in Architecture

```
INTERNET / UNTRUSTED NETWORK
    │
    │ Client request (user wants to sign document)
    │ or "Server, prove you're really PayPal"
    │
    ▼
┌────────────────────────────────────────────┐
│ Server with SSL/TLS Certificate            │
│                                            │
│ ┌──────────────────────────────────────┐  │
│ │ Public Key Certificate               │  │
│ │ (signed by Certificate Authority)    │  │
│ │                                      │  │
│ │ - Domain: example.com                │  │
│ │ - Public Key: RSA 2048 bit           │  │
│ │ - Valid until: Jan 2027              │  │
│ │ - Issuer: Let's Encrypt (CA)         │  │
│ │ - Signature: CA's digital signature  │  │
│ │   (proves this cert is authentic)    │  │
│ └──────────────────────────────────────┘  │
│          ▲                                 │
│          │ Verified via CA chain          │
│          │ (client trusts CA)             │
│                                           │
│ ┌──────────────────────────────────────┐  │
│ │ Private Key (KEPT SECURE)            │  │
│ │                                      │  │
│ │ - RSA 2048 bit key (never shared)    │  │
│ │ - Used only to:                      │  │
│ │   1. Sign the public certificate     │  │
│ │   2. Decrypt session keys (TLS)      │  │
│ │   3. Sign digital documents          │  │
│ └──────────────────────────────────────┘  │
│                                            │
└─────────────────┬──────────────────────────┘
                  │
    ┌─────────────┴──────────────┐
    │                            │
    ▼ (during HTTPS handshake)   ▼
Client verifies            Server uses
server's cert             private key
(via CA chain)            to prove identity
    │                            │
    └─────────────────────────────┘
            Secure channel
            established ✅

KEY INVARIANT:
   Private key signs & proves identity
   Public key verifies signature (anyone can do this)
   Certificate Authority chain anchors trust
   HTTPS = HTTP + TLS (which uses PKI)
```

---

## 🎨 Visual — Digital Signature: Create & Verify (Component Detail)

Think of it as a **medieval king's wax seal**.

A king sends a letter. Before sealing it, he presses his unique signet ring into hot wax. The ring has a distinctive pattern — his royal coat of arms. The king keeps the ring locked away (his **private key**). His coat of arms design is publicly known — stamped on official documents, carved above the palace gates (the **public key**).

When the letter arrives:
- Anyone can look at the seal and verify it matches the known coat of arms pattern → the letter is authentic (**signature verification**)
- The seal is intact, so the letter wasn't opened and re-sealed → the contents weren't tampered with (**integrity**)
- Only the king's ring could produce that exact seal → the king can't deny having sent it (**non-repudiation**)

**One problem with sealing the whole letter:** The king's ring is small — it can't make an impression on a 100-page document. So instead, a royal scribe reads the full letter and produces a short fixed-length fingerprint summary — say, "a letter to the Duke dated March 3rd about grain taxes." The king seals THAT fingerprint, not the full letter. This is **hashing** — turning an arbitrarily large document into a fixed-size digest before signing.

**Certificate Authorities — the trusted town herald:**
How does the Duke know the coat of arms on the seal is actually the king's, and not a forgery? He asks the town herald — a trusted third party whose own seal he already recognises. The herald's certificate says "I, the Herald, confirm this coat of arms belongs to King X." The Duke trusts the herald → trusts the king's seal. That chain of trust is the **Certificate Authority chain**.

**The key insight is:** Asymmetric cryptography solves the fundamental trust problem: how do two parties who have never met establish a secure, verifiable identity? Private key proves identity; public key lets anyone verify; a CA chain anchors the trust to a known root.

---

## 🎨 Visual — Digital Signature Flow

```
  HOW A DIGITAL SIGNATURE IS CREATED AND VERIFIED
  ─────────────────────────────────────────────────────────────────

  SIGNING (sender side):
  ┌─────────────────────────────────────────────────────────────┐
  │                      DOCUMENT                               │
  │  "I agree to the terms of this contract..."                 │
  └──────────────────────────────┬──────────────────────────────┘
                                 │
                         [ SHA-256 HASH ]
                                 │ (fixed 256-bit digest)
                                 ▼
                          DOCUMENT HASH
                    e.g., 3f4a9b2c1d...
                                 │
                     [ ENCRYPT WITH PRIVATE KEY ]
                                 │
                                 ▼
                         DIGITAL SIGNATURE
                    (encrypted hash — attached to doc)

  VERIFICATION (receiver side):
  ┌─────────────────────────────────────────────────────────────┐
  │  Received: DOCUMENT + DIGITAL SIGNATURE                     │
  └──────────┬──────────────────────────────┬───────────────────┘
             │                              │
     [ SHA-256 HASH ]         [ DECRYPT SIGNATURE with PUBLIC KEY ]
             │                              │
             ▼                              ▼
      Hash of received              Original hash
         document                  (what sender hashed)
             │                              │
             └──────────── COMPARE ─────────┘
                               │
                    Match? ✅ → Signature valid
                    No match? ❌ → Tampered or wrong key

  KEY INVARIANT:
     The private key encrypts (signs). The public key decrypts (verifies).
     This is opposite to regular encryption — that's what makes it a SIGNATURE.
     We hash first because: (1) private key operations are slow — hashing is fast,
     (2) the hash is fixed-size regardless of document length.


  CERTIFICATE AUTHORITY CHAIN
  ─────────────────────────────────────────────────────────────────

  Root CA (self-signed — trusted by OS/browser by default)
      │
      │ signs
      ▼
  Intermediate CA (e.g., "DigiCert TLS RSA SHA256 2020")
      │
      │ signs
      ▼
  End Entity Certificate (e.g., "docusign.com")
      │
      └── Contains: docusign.com's public key
                    Validity period
                    Issuer (Intermediate CA name)
                    Digital signature by Intermediate CA

  CHAIN VERIFICATION:
  Browser trusts Root CA → Root CA vouches for Intermediate CA
  → Intermediate CA vouches for docusign.com's certificate
  → Browser trusts docusign.com's public key

  KEY INVARIANT:
     Trust is transitive but anchored. You don't need to know every site's key —
     you just need to trust the root, and the chain does the rest.
     If ANY link in the chain is compromised, the whole chain fails.
```

---

## ⚙️ How It Actually Works

### Part 1 — Symmetric vs Asymmetric Encryption

**Symmetric encryption** — one key, same key encrypts and decrypts:
- Fast — AES-256 can encrypt gigabytes per second
- Problem: key distribution. If Alice and Bob want to communicate securely, how do they share the key without someone intercepting it?
- Use when: encrypting data at rest (database encryption, file encryption) where only one party needs the key

**Asymmetric encryption** — two mathematically linked keys (public + private):
- Public key: freely shared with the world. Encrypts messages to you, or verifies your signatures.
- Private key: kept secret. Decrypts messages to you, or creates your signatures.
- Slow — RSA operations are ~1000× slower than AES
- Solves key distribution: you share the public key openly; only the private key holder can decrypt/sign
- Use when: establishing trust between parties who haven't met, digital signatures, key exchange

**In practice (TLS/HTTPS):** Asymmetric crypto is used ONLY for the initial handshake — to securely exchange a symmetric key. Then AES (symmetric) takes over for the bulk data transfer. Best of both: security of asymmetric + speed of symmetric.

```java
// Java example — generating an RSA key pair (asymmetric)
KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
generator.initialize(2048);
KeyPair keyPair = generator.generateKeyPair();

PrivateKey privateKey = keyPair.getPrivate();  // keep secret — sign with this
PublicKey publicKey = keyPair.getPublic();     // share openly — verify with this
```

---

### Part 2 — Digital Signature Step by Step

**Steps:**
1. **Hash the document** using SHA-256 — produces a fixed 32-byte digest regardless of document size.
2. **Sign the hash** by encrypting it with the signer's private key — this produces the digital signature.
3. **Attach the signature** to the document and send both to the receiver.
4. **Receiver hashes** the received document with SHA-256 — produces a local hash.
5. **Receiver decrypts** the attached signature using the signer's PUBLIC key — recovers the original hash.
6. **Compare the two hashes** — if they match, the signature is valid and the document is untampered.

```java
// Step 1 + 2: sign a document
public byte[] signDocument(byte[] documentBytes, PrivateKey privateKey) throws Exception {
    // Step 1: hash is implicit in the signature algorithm (SHA256withRSA does hash + sign)
    Signature signer = Signature.getInstance("SHA256withRSA");
    signer.initSign(privateKey);
    signer.update(documentBytes);
    // Step 2: produces the digital signature (encrypted hash)
    return signer.sign();
}

// Steps 4-6: verify the signature
public boolean verifySignature(
        byte[] documentBytes,
        byte[] signatureBytes,
        PublicKey publicKey) throws Exception {
    Signature verifier = Signature.getInstance("SHA256withRSA");
    verifier.initVerify(publicKey);
    // Step 4: hash the received document
    verifier.update(documentBytes);
    // Steps 5-6: decrypt signature with public key, compare hashes
    return verifier.verify(signatureBytes);
}
```

### What is SHA-256, and why do we hash before signing?

**SHA-256** (Secure Hash Algorithm 256-bit) is a cryptographic hash function — it takes any input (a document, a string, a file) and produces a fixed 256-bit (32-byte) output called a digest. The same input always produces the same digest; any change to the input produces a completely different digest.

**Why hash before signing:**
1. **Speed:** Private key operations (RSA signing) are computationally expensive — ~1000× slower than hashing. A 10MB PDF would take seconds to sign directly. SHA-256 reduces it to 32 bytes first.
2. **Fixed size:** RSA can only operate on data up to its key size (2048 bits ≈ 256 bytes). A 10MB document won't fit. The 32-byte SHA-256 hash always fits.
3. **Integrity guarantee:** If even one bit of the document changes, the SHA-256 hash completely changes — ensuring tamper detection.

**In an interview, if asked:** "SHA-256 is a one-way hash function — it produces a fixed 32-byte fingerprint of any document. We hash before signing because RSA operations are slow and have size limits; hashing the document to 32 bytes first makes signing fast and practical. Any tampering with the document changes the hash, which breaks signature verification."

---

### What is PKI (Public Key Infrastructure)?

**PKI** is the system of roles, policies, and technologies that manages public key certificates — it's the infrastructure that makes "trust a public key you've never seen before" possible.

**In an interview, if asked:** "PKI is the trust system behind digital certificates. It answers: how do I know this public key actually belongs to DocuSign and not to an attacker? The answer is a chain of certificates from a trusted Root CA down to the website's certificate — each link signed by the one above it. My browser ships with ~150 trusted root CAs, and everything chains up to one of them."

---

### Part 3 — Certificate Authority (CA) Chain

A **Certificate Authority (CA)** is a trusted organisation that vouches for the identity of certificate holders by signing their certificates with the CA's own private key.

**The chain of trust:**
1. **Root CA** — self-signed certificate, trusted by default in all browsers and operating systems (e.g., DigiCert, Let's Encrypt, VeriSign). Their public keys are baked into browser/OS.
2. **Intermediate CA** — certificate signed by the Root CA. Used for day-to-day certificate issuance so the Root CA private key stays offline (air-gapped) for security.
3. **End Entity certificate** — certificate signed by Intermediate CA, issued to the actual website/service (e.g., `docusign.com`). Contains the site's public key + validity period.

**Why the chain matters:** You don't need to trust every website individually. You trust ~150 root CAs → they vouch for thousands of intermediate CAs → which vouch for millions of websites. The chain makes global trust scalable.

```java
// Verifying a certificate chain in Java (simplified)
public void verifyCertificateChain(X509Certificate[] chain) throws Exception {
    // chain[0] = end entity cert (docusign.com)
    // chain[1] = intermediate CA cert
    // chain[2] = root CA cert (or trusted anchor)

    for (int i = 0; i < chain.length - 1; i++) {
        // Each cert must be signed by the next cert's public key
        chain[i].verify(chain[i + 1].getPublicKey());
        // Each cert must be within its validity period
        chain[i].checkValidity();
    }
    // The last cert in the chain must be a trusted root
    // (In practice, Java's TrustManager handles this against its trust store)
}
```

---

### Part 4 — Non-Repudiation

**Non-repudiation** means the signer cannot later deny having signed. This is the legal backbone of DocuSign's entire product.

**How it's achieved technically:**
- The digital signature was created using the signer's private key
- Only the signer has (or should have) access to their private key
- The signature verification succeeds with their public key
- Therefore: if the signature is valid, the signer must have signed it

**Why this matters for DocuSign:** A contract signed via DocuSign is admissible in court because:
1. The document hash proves the content wasn't tampered with after signing
2. The digital signature proves only the signer's private key could have created it
3. The audit trail proves when and how the signing happened

**The edge case:** What if the signer claims their private key was stolen? This is why PKI includes **certificate revocation** — if a private key is compromised, the certificate is revoked (added to a Certificate Revocation List or checked via OCSP). After revocation, signatures made after that point are not trusted.

---

### Part 5 — Audit Trail Design

A legally valid audit trail for a document signing system must be:
1. **Append-only** — entries are never modified or deleted. Any alteration invalidates the trail.
2. **Timestamped** — every entry has a server-generated timestamp (plus a trusted timestamp from a TSA — Time Stamping Authority).
3. **Actors and actions** — who did what: `user_id`, `action`, `ip_address`, `document_hash`.
4. **Immutable document hash** — the SHA-256 hash of the document at each step is stored. If the document changes, the hash changes, and the audit trail becomes inconsistent.

```java
// Audit trail table — append-only, never UPDATE or DELETE
// CREATE TABLE audit_log (
//     id            BIGINT PRIMARY KEY AUTO_INCREMENT,
//     envelope_id   BIGINT NOT NULL,
//     actor_id      BIGINT NOT NULL,        -- who performed the action
//     action        VARCHAR(50) NOT NULL,   -- 'CREATED', 'VIEWED', 'SIGNED', 'COMPLETED'
//     document_hash VARCHAR(64),            -- SHA-256 of document at this moment
//     ip_address    VARCHAR(45),
//     user_agent    VARCHAR(255),
//     timestamp     TIMESTAMP NOT NULL DEFAULT NOW()
// );
// No UPDATE or DELETE permissions granted on this table.
// INDEX: (envelope_id, timestamp) for "show audit trail for envelope X"

@Entity
@Table(name = "audit_log")
public class AuditEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "envelope_id", nullable = false)
    private Long envelopeId;

    @Column(name = "actor_id", nullable = false)
    private Long actorId;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private AuditAction action;

    @Column(name = "document_hash")
    private String documentHash;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(nullable = false)
    private Instant timestamp;

    // No setters for timestamp — set once at creation, never updated
}
```

---

### Part 6 — Multi-Party Signing: Sequential vs Parallel

**Sequential signing:** Recipient A must sign before Recipient B can sign.
- Use when: the signing order has legal significance (manager approves, then employee acknowledges)
- Implementation: `recipients` table has an `order` column. Email is only sent to recipient N+1 after recipient N signs. Status: `WAITING_FOR_A` → `WAITING_FOR_B` → `COMPLETED`

**Parallel signing:** All recipients can sign independently in any order.
- Use when: order doesn't matter (two co-founders signing simultaneously)
- Implementation: all recipients get the email simultaneously. Status moves to `COMPLETED` only when all have signed.

```java
// Status machine for a multi-recipient envelope
public enum EnvelopeStatus {
    DRAFT,          // not yet sent
    IN_PROGRESS,    // at least one recipient has not signed
    COMPLETED,      // all recipients signed
    DECLINED,       // any recipient declined
    VOIDED          // sender cancelled
}

// Recipient table — tracks individual signing status
// CREATE TABLE recipients (
//     id           BIGINT PK,
//     envelope_id  BIGINT FK,
//     email        VARCHAR NOT NULL,
//     signing_order INT NOT NULL DEFAULT 1,  -- 1,2,3 for sequential; same number for parallel
//     status       ENUM('PENDING','SIGNED','DECLINED'),
//     signed_at    TIMESTAMP NULL,
//     signature    BLOB NULL              -- the actual digital signature bytes
// );

// After each signing event, check if envelope is complete
public void checkEnvelopeCompletion(Long envelopeId) {
    long unsigned = recipientRepo.countByEnvelopeIdAndStatus(envelopeId, RecipientStatus.PENDING);
    if (unsigned == 0) {
        envelopeRepo.updateStatus(envelopeId, EnvelopeStatus.COMPLETED);
        auditLog.append(envelopeId, "SYSTEM", AuditAction.COMPLETED, computeDocumentHash(envelopeId));
    }
}
```

---

## 🏢 Real World — Where Companies Use This

- **DocuSign** — Every signed envelope uses RSA-2048 asymmetric signatures. The `X-DocuSign-Signature-1` HTTP header in their webhook payloads contains an RSA signature so receiving servers can verify the webhook is genuinely from DocuSign. Audit trail stored per envelope with IP, timestamp, and document hash at each signing event.
- **GitHub** — GPG/SSH key-based commit signing. `git commit -S` signs the commit with your private key. `git log --show-signature` lets anyone verify the signature with your public key. Non-repudiation: the commit cannot be attributed to someone else if the signature is valid.
- **Indian Government (Aadhaar eSign)** — Digital signatures on tax filings and loan applications. The taxpayer's Aadhaar biometric authenticates their identity; the signing service then uses their registered private key to sign documents. Certificate chain runs from NIC (National Informatics Centre) Root CA.
- **Stripe** — Webhook signatures: `Stripe-Signature` header uses HMAC-SHA256 (symmetric — a shared secret rather than asymmetric). Different from PKI but same integrity principle: receiver recomputes the hash with the shared secret and compares.
- **SSL/TLS (every HTTPS connection)** — The certificate chain verification happens on every browser connection. Asymmetric crypto (RSA or ECDH) for key exchange; symmetric (AES-256) for bulk data. Certificate revocation checked via OCSP.
- **JWT (used in DocuSign's API auth)** — RS256 algorithm: server signs the JWT header+payload with its RSA private key. Client verifies with the server's public key (from a published JWKS endpoint). The `sub` claim identifies the user; the signature proves it wasn't forged.

---

## 🧭 When to Use vs When NOT to Use

| Use digital signatures when | Use simpler HMAC / shared secret when |
|---|---|
| Signer identity must be provable to a third party (legal contracts) | Only two parties — sender and receiver already share a secret (webhook verification) |
| Non-repudiation is required — signer cannot deny signing | Internal service-to-service calls where both sides are trusted |
| Multiple parties who don't know each other need to establish trust | The overhead of PKI is not justified by the use case |
| The document must remain verifiable years later | Short-lived tokens where expiry is the main security control |

**The common mistake:** Using symmetric encryption (AES) for digital signatures. Symmetric encryption requires both parties to have the same key — so the receiver could have created the signature too. That means no non-repudiation. Asymmetric signatures (RSA/ECDSA) are the only way to prove "only this specific key holder signed this."

---

## ⚠️ Trade-offs

| | |
|---|---|
| **You gain** | Legally admissible proof of who signed what and when. Tamper detection on documents. Trust between parties who have never met (via CA chain). |
| **You lose** | Complexity — PKI infrastructure, certificate lifecycle management, key rotation. Performance — asymmetric operations are slow; use hybrid (asymmetric for key exchange + symmetric for bulk) in practice. |
| **Failure mode** | Private key compromise. If an attacker gets the private key, they can forge signatures indefinitely until the certificate is revoked. Revocation (CRL/OCSP) is the defence — but there's always a window between compromise and revocation. |

---

## 🔬 Interview Q&As

### Tier 1 — Surface Questions

### Q: "How does a digital signature work?"
> The signer hashes the document with SHA-256 (produces a 32-byte digest), then encrypts that hash with their private key — the result is the digital signature. The signature is attached to the document. The receiver: (1) hashes the received document, (2) decrypts the signature using the signer's public key to get the original hash, (3) compares the two hashes. If they match — the document wasn't tampered with and only the private key holder could have signed it. We hash first because RSA is slow and has size limits; the 32-byte hash solves both.

### Q: "What is non-repudiation and why does DocuSign need it?"
> Non-repudiation means the signer cannot later deny having signed. Technically: the digital signature was created by encrypting the document hash with the signer's private key. If the signature verifies against their public key, only they could have signed it. For DocuSign, this is the legal foundation — a contract signed through their platform is court-admissible because the digital signature proves identity, the hash proves the document wasn't altered, and the audit trail proves when and how it happened.

### Q: "Walk me through designing a digital signature system for a contract platform."
> Three core components. First, identity: each signer has an asymmetric key pair — private key stored in a hardware security module (HSM) or encrypted vault, public key registered in the platform. Second, the signing flow: when a signer clicks "Sign," the platform: (1) computes SHA-256 of the document, (2) calls the signing service which uses the signer's private key to sign the hash, (3) stores the signature bytes against the document version in the DB, (4) appends an audit log entry with actor, timestamp, IP, and document hash. Third, verification: any party can re-hash the document and verify the signature against the registered public key — any discrepancy means tampering.

### Q: "What is the difference between symmetric and asymmetric encryption?"
> Symmetric uses one shared key for both encryption and decryption — fast (AES-256 can do GB/s) but requires secure key distribution. Asymmetric uses a key pair — public key encrypts (or verifies), private key decrypts (or signs) — solves key distribution since the public key can be shared openly, but is ~1000× slower than symmetric. In practice: HTTPS uses asymmetric for the initial handshake to exchange a session key, then symmetric for all data transfer. Digital signatures use asymmetric — you can't use symmetric because the receiver would also hold the key and could forge the signature.

---

### Tier 2 — Cross / Probe Questions

### Q: "How does a browser know to trust a certificate it has never seen before?"
> The browser ships with ~150 pre-installed trusted Root CA public keys. When it connects to docusign.com, it receives a certificate chain: docusign.com's certificate (signed by Intermediate CA) + Intermediate CA certificate (signed by Root CA). The browser verifies each signature using the issuer's public key: does the Intermediate CA's signature on docusign.com's cert verify against the Intermediate CA's public key? Does the Root CA's signature on the Intermediate cert verify against a Root CA in its trust store? If the whole chain verifies and the certificate is within its validity period, the browser trusts the connection. If any link fails — expired cert, revoked cert, unknown root — it shows an error.

### Q: "DocuSign has sequential signing — A must sign before B. How do you implement that technically?"
> The `recipients` table has a `signing_order` column (integer). On envelope creation, recipients are assigned order 1, 2, 3. The system only sends the signing request email to order-1 recipients. After order-1 signs: (1) verify their signature, (2) append audit log, (3) check if all order-1 recipients are done, (4) if yes — generate signing link and send email to order-2 recipients. Repeat until all orders complete, then mark envelope `COMPLETED`. The status machine: `WAITING_FOR_ORDER_1` → `WAITING_FOR_ORDER_2` → `COMPLETED`. The key edge case: if order-1 recipient declines, the whole envelope moves to `DECLINED` and no subsequent recipients are notified.

### Q: "The signer claims their private key was stolen after they signed. Is the signature still valid?"
> Technically the signature is cryptographically valid — it was produced by the private key, and the math checks out. But legally, if the signer proves the key was compromised before the signing event, they may successfully dispute it. This is why: (1) trusted timestamps from a TSA (Time Stamping Authority) are critical — they prove the document was signed at a specific time; (2) certificate revocation: if the key was reported stolen before the signing timestamp, the certificate's revocation timestamp (in the CRL or OCSP) would predate the signing — strong evidence for the signer's claim. Certificate revocation timestamp vs signing timestamp is how the dispute is settled.

---

## 🧾 TL;DR — One Interviewer-Ready Line

> *"A digital signature is the document's SHA-256 hash encrypted with the signer's private key — anyone can verify it with the public key, proving the document is untampered and only that key holder could have signed it. The CA chain handles trust distribution; the audit trail with document hash at each step provides non-repudiation for legal admissibility."*

---

## 🔗 Related Concepts

- **`04-idempotency.md`** — DocuSign webhook delivery uses digital signatures (`X-DocuSign-Signature-1` header) so the receiving server can verify authenticity. The idempotency key ensures duplicate webhook deliveries are safely ignored.
- **`11-api-design.md`** — JWT-based API authentication (`RS256` algorithm) uses asymmetric signing — the same private key / public key mechanics described here.
- **`07-cdc-outbox.md`** — Signing completion events can be reliably published to Kafka via the outbox pattern — ensuring the "envelope signed" notification reaches downstream services exactly once.

---

## 📚 Further Reading (Optional — after the note)

| Resource | What it adds | Time |
|---|---|---|
| **ByteByteGo — "How HTTPS works"** (YouTube) | Visual animation of TLS handshake, certificate chain verification, symmetric/asymmetric handoff. Search: "ByteByteGo HTTPS TLS explained" | ~8 min |
| **Arpit Bhayani — JWT Deep Dive** (YouTube) | RS256 signing in depth — same asymmetric mechanics as PKI, applied to JWTs. Search: "Arpit Bhayani JWT" | ~20 min |
| **hellointerview.com — Security** | Full interview walkthrough: auth, authorisation, encryption at rest vs in transit. URL: https://www.hellointerview.com/learn/system-design/core-concepts/security | ~15 min |

---

## 🔄 Changelog

| Date | Change |
|---|---|
| June 2026 | File created. DocuSign R2 prep — D1 question likely, DocuSign's own product is e-signatures. Covers symmetric/asymmetric encryption, digital signature flow, SHA-256, CA chain, non-repudiation, audit trail, multi-party signing. |
