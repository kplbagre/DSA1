# JWT Token Storage — Where & How to Store Tokens Securely

> JWT tokens must be stored securely on the client. Wrong storage = token theft = account compromise. This quick reference covers all storage options, their trade-offs, and when to use each.

---

## 📖 What is JWT? (Full Form & Basics)

**JWT = JSON Web Token**

A JWT is a **signed, compact token** that contains user information and can be verified without checking a database every time.

### Simple Explanation

Imagine a theme park ticket:
- **Physical ticket:** Park staff must check a database every time to verify if ticket is real. Slow.
- **JWT ticket:** Ticket has a **hidden signature** that only park staff can verify (they know the secret to check). When guest shows ticket, staff instantly verifies it's real without checking database.

### What's Inside a JWT?

```
Structure: HEADER.PAYLOAD.SIGNATURE

Example:
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjMiLCJuYW1lIjoiSm9obiIsImFkbSI6dHJ1ZX0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c

PART 1: HEADER (base64 encoded)
{
  "alg": "HS256",    // Algorithm: HMAC with SHA-256
  "typ": "JWT"       // Type: JSON Web Token
}

PART 2: PAYLOAD (base64 encoded) — Contains the actual data
{
  "sub": "123",      // Subject (user ID)
  "name": "John",    // User's name
  "admin": true,     // Is admin?
  "exp": 1719345600  // Expiration time (timestamp)
}

PART 3: SIGNATURE (cryptographic hash)
HMAC-SHA256(
  base64(header) + "." + base64(payload),
  secret_key
)
// This signature proves the token is real (wasn't tampered with)
```

### Why JWT is Better Than Sessions

| | Sessions | JWT |
|---|----------|-----|
| **How it works** | Server stores session in memory/DB | Client stores token, server just verifies |
| **Server load** | Must query DB on EVERY request | Verify signature without DB lookup |
| **Scalability** | Doesn't scale (session stored centrally) | Scales infinitely (stateless) |
| **Mobile friendly** | Difficult (cookies not standard) | Easy (token in header) |

### JWT Lifecycle

```
1. LOGIN:
   Client: POST /login {username, password}
        ↓
   Server: Validates credentials
           Creates JWT with user_id, roles, exp
           Signs with SECRET_KEY
           Sends JWT to client
        ↓
   Client: Stores JWT (in memory, cookie, or secure storage)

2. API CALL:
   Client: GET /api/orders
           Header: Authorization: Bearer {JWT}
        ↓
   Server: Extracts JWT from Authorization header
           Verifies signature (using same SECRET_KEY)
           ✅ Valid? → Grant access
           ❌ Invalid/expired? → Reject (401)

3. EXPIRY:
   If JWT expired → use refresh token to get new JWT
   Refresh token is longer-lived (7 days)
   Access token is short-lived (15 minutes)
```

### Key Properties of JWT

✅ **Self-contained:** All info in token, no server lookup needed
✅ **Tamper-proof:** Signature prevents modification
✅ **Expiring:** Can expire after set time
✅ **Portable:** Works across domains and mobile apps
✅ **Stateless:** Server doesn't store session state

❌ **Readable:** Payload is base64 (not encrypted), so don't put secrets in it
❌ **Can't be revoked immediately:** Token valid until expiry (mitigation: short expiry + blacklist)

---

## 🎯 Quick Decision Tree

```
Are you building a web app (browser)?
├─ YES → Use httpOnly cookies (most secure)
│        Alternative: Memory + refresh token in cookie
│        DO NOT use localStorage (XSS vulnerability)
│
└─ NO, is it a mobile app (iOS/Android)?
   └─ YES → Use secure enclave (iOS Keychain / Android Keystore)
            Alternative: Memory + refresh token in secure storage
            
           Is it a desktop app (Electron, Desktop)?
           └─ YES → Use secure credential storage (OS keyring)
                    Alternative: Encrypted file storage
                    
           Is it a backend service (calling other APIs)?
           └─ YES → Store in memory during session
                    Or environment variables (for API keys / service creds)
                    DO NOT store in code / version control
```

---

## 🏪 Storage Options — Comparison Table

| Storage | Security | Theft Risk | Convenience | Best For | Avoid For |
|---------|----------|-----------|------------|----------|-----------|
| **httpOnly Cookie** | ⭐⭐⭐⭐⭐ (best) | XSS safe | Good (auto sent) | Web apps | Mobile, Desktop |
| **Memory (JS var)** | ⭐⭐⭐⭐ (good) | XSS + page refresh | Good | Web (with refresh token in cookie) | Persistence |
| **localStorage** | ⭐⭐ (bad) | XSS vulnerable | Easy | ❌ AVOID | All production apps |
| **sessionStorage** | ⭐⭐ (bad) | XSS vulnerable | Easy | ❌ AVOID | All production apps |
| **iOS Keychain** | ⭐⭐⭐⭐⭐ | OS-protected | Medium | Mobile (iOS) | Web, Android |
| **Android Keystore** | ⭐⭐⭐⭐⭐ | OS-protected | Medium | Mobile (Android) | Web, iOS |
| **OS Credential Store** | ⭐⭐⭐⭐⭐ | OS-protected | Medium | Desktop | Mobile, Web |
| **Environment Variable** | ⭐⭐⭐ | Dev machine risk | Easy | Backend service creds | Client apps |
| **Encrypted File** | ⭐⭐⭐⭐ | File access control | Medium | Desktop (config) | Frequent access |

---

## 📋 Detailed Scenarios

### **Web App (Browser) — BEST PRACTICE**

```
┌─────────────────────────────────────┐
│ LOGIN FLOW                          │
└─────────────────────────────────────┘

1. User submits credentials
   ↓
2. Server authenticates, creates JWT
   ↓
3. Server sends RESPONSE:
   ┌────────────────────────────────┐
   │ Set-Cookie: refresh_token=... │
   │   Path=/                      │
   │   HttpOnly (JS can't access)  │
   │   Secure (HTTPS only)         │
   │   SameSite=Strict             │
   │   Max-Age=7200 (2 hours)      │
   │                              │
   │ Body: {access_token: ...}    │
   │ (NOT in cookie, in response) │
   └────────────────────────────────┘
   ↓
4. Browser stores:
   ✅ refresh_token in httpOnly cookie (automatic, secure)
   ✅ access_token in memory (JS variable)
   ❌ NOT localStorage
   
5. Client makes API requests:
   ✅ Authorization: Bearer {access_token_from_memory}
      (sent in Authorization header, not cookie)


REFRESH TOKEN FLOW:
┌──────────────────────────────────────────┐
│ Access token expires (15 minutes)        │
│ Next API request fails: 401 Unauthorized │
└──────────────────────────────────────────┘
   ↓
Browser intercepts 401, makes silent request:
   POST /auth/refresh
   (refresh_token sent automatically in cookie)
   ↓
Server validates refresh_token (from secure cookie),
issues new access_token
   ↓
Client retries original request with new token


KEY SECURITY PROPERTIES:
✅ refresh_token: httpOnly cookie (can't be stolen by JS XSS)
✅ access_token: memory only (lost on page refresh, OK — use refresh)
✅ Shorter access_token lifetime (15 min), longer refresh (7 days)
✅ httpOnly prevents XSS theft
✅ Secure flag prevents HTTP transmission
✅ SameSite=Strict prevents CSRF attacks
```

**Code Example — Frontend:**

```javascript
// Login
const response = await fetch('/auth/login', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ username, password }),
  credentials: 'include' // IMPORTANT: include cookies
});

const { access_token } = await response.json();

// Store access_token in memory (NOT localStorage)
let accessToken = access_token; // JS variable, lost on refresh

// Make API call
const apiResponse = await fetch('/api/orders', {
  headers: {
    'Authorization': `Bearer ${accessToken}`
  },
  credentials: 'include' // Include cookies (refresh_token)
});

// On 401 (access token expired)
if (apiResponse.status === 401) {
  // Refresh token is already in cookie (httpOnly)
  // Call refresh endpoint
  const refreshResp = await fetch('/auth/refresh', {
    method: 'POST',
    credentials: 'include' // Send refresh_token cookie
  });
  
  const { access_token: newToken } = await refreshResp.json();
  accessToken = newToken; // Update memory variable
  
  // Retry original request
  return fetch('/api/orders', {
    headers: { 'Authorization': `Bearer ${accessToken}` },
    credentials: 'include'
  });
}
```

> **Why `credentials: 'include'` works — browser mechanics:**
> The browser **automatically** sends every HttpOnly cookie for the domain with each request — your JavaScript never reads the cookie value (`HttpOnly` blocks `document.cookie` access entirely). When JS calls `POST /auth/refresh` with `credentials: 'include'`, the browser attaches `Cookie: refresh_token=...` in the request header. The server reads it via `request.getCookies()`, which is the standard servlet API that parses the `Cookie` header. No JS code touches the cookie value at any point.

**Code Example — Backend:**

```java
// Login endpoint
@PostMapping("/auth/login")
public ResponseEntity<?> login(@RequestBody LoginRequest request) {
    User user = authenticate(request.getUsername(), request.getPassword());
    
    // Create tokens
    String accessToken = createAccessToken(user, 15 * 60); // 15 min
    String refreshToken = createRefreshToken(user, 7 * 24 * 60 * 60); // 7 days
    
    // Send refresh_token as httpOnly cookie
    ResponseCookie refreshCookie = ResponseCookie
        .from("refresh_token", refreshToken)
        .httpOnly(true)           // JS can't access
        .secure(true)             // HTTPS only
        .sameSite("Strict")        // CSRF protection
        .path("/")
        .maxAge(7 * 24 * 60 * 60) // 7 days
        .build();
    
    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
        .body(new LoginResponse(accessToken)); // Access token in body
}

// Refresh endpoint
@PostMapping("/auth/refresh")
public ResponseEntity<?> refresh(HttpServletRequest request) {
    // Extract refresh_token from cookie (automatic by servlet)
    Cookie[] cookies = request.getCookies();
    String refreshToken = null;
    
    for (Cookie cookie : cookies) {
        if ("refresh_token".equals(cookie.getName())) {
            refreshToken = cookie.getValue();
            break;
        }
    }
    
    if (refreshToken == null) {
        return ResponseEntity.status(401).body("No refresh token");
    }
    
    // Validate refresh_token
    if (!validateToken(refreshToken)) {
        return ResponseEntity.status(401).body("Refresh token expired");
    }
    
    // Issue new access_token
    User user = getUserFromToken(refreshToken);
    String newAccessToken = createAccessToken(user, 15 * 60);
    
    return ResponseEntity.ok(new RefreshResponse(newAccessToken));
}
```

---

### 🔐 Token Rotation + Reuse Detection (OAuth 2.1)

The refresh endpoint above reissues an access token but keeps the same refresh token — a weaker security model. **OAuth 2.1 mandates refresh token rotation**: every `/auth/refresh` call must issue a new refresh token and revoke the old one. The interview-signal upgrade is **reuse detection**: if a previously-rotated (revoked) refresh token is replayed, it means someone stole it. The correct response is to revoke **all sessions for that user immediately**.

```java
@PostMapping("/auth/refresh")
public ResponseEntity<?> refreshWithRotation(HttpServletRequest request) {
    String oldRefreshToken = extractCookieValue(request, "refresh_token");
    RefreshTokenRecord record = refreshTokenRepo.findByToken(oldRefreshToken);

    if (record == null) {
        return ResponseEntity.status(401).body("Invalid refresh token");
    }

    if (record.isRevoked()) {
        // REUSE DETECTED — old token replayed after rotation → assume theft
        refreshTokenRepo.revokeAllForUser(record.getUserId());
        return ResponseEntity.status(401).body("Refresh token reuse detected — all sessions revoked");
    }

    // Mark old token revoked BEFORE issuing new one (prevents concurrent-use race)
    refreshTokenRepo.markRevoked(record.getId());

    // Issue rotated tokens
    User user = userRepo.findById(record.getUserId());
    String newAccessToken = createAccessToken(user, 15 * 60);
    String newRefreshToken = createRefreshToken(user, 7 * 24 * 60 * 60);
    refreshTokenRepo.save(new RefreshTokenRecord(record.getUserId(), newRefreshToken));

    // Rotate cookie — new refresh token replaces old one in browser
    ResponseCookie rotatedCookie = ResponseCookie.from("refresh_token", newRefreshToken)
        .httpOnly(true)
        .secure(true)
        .sameSite("Strict")
        .path("/")
        .maxAge(7 * 24 * 60 * 60)
        .build();

    return ResponseEntity.ok()
        .header(HttpHeaders.SET_COOKIE, rotatedCookie.toString())
        .body(new RefreshResponse(newAccessToken));
}
```

**Reuse detection invariant:**

```
Token A issued → client stores A in httpOnly cookie
Client calls /auth/refresh with A → A marked revoked, B issued → client now has B
Attacker replays A → record.isRevoked() == true → revokeAllForUser() → all sessions killed
Both attacker and real user are logged out → real user re-authenticates → attacker locked out
```

---

### **Mobile App (iOS) — Keychain**

```
LOGIN FLOW:
┌────────────────────────┐
│ User enters credentials│
└────────────────────────┘
   ↓
POST /auth/login
   ↓
Server responds: {access_token, refresh_token}
   ↓
┌────────────────────────────────────┐
│ iOS App stores in Keychain:        │
│ ✅ access_token → Keychain         │
│ ✅ refresh_token → Keychain        │
│ (both encrypted by OS)             │
│ (can't be accessed by other apps)  │
└────────────────────────────────────┘
   ↓
Make API requests:
   GET /api/orders
   Header: Authorization: Bearer {access_token_from_keychain}
   
On expiry:
   POST /auth/refresh (refresh_token from keychain)
   → Get new access_token
   → Store in Keychain
```

**Swift Code:**

```swift
import Security

// Store tokens in Keychain
func storeToken(_ token: String, key: String) {
    let data = token.data(using: .utf8)!
    
    let query: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrAccount as String: key,
        kSecValueData as String: data
    ]
    
    // Delete old token
    SecItemDelete(query as CFDictionary)
    
    // Add new token
    SecItemAdd(query as CFDictionary, nil)
}

// Retrieve token from Keychain
func getToken(key: String) -> String? {
    let query: [String: Any] = [
        kSecClass as String: kSecClassGenericPassword,
        kSecAttrAccount as String: key,
        kSecReturnData as String: true
    ]
    
    var result: AnyObject?
    SecItemCopyMatching(query as CFDictionary, &result)
    
    if let data = result as? Data {
        return String(data: data, encoding: .utf8)
    }
    return nil
}

// Usage
storeToken(accessToken, key: "access_token")
storeToken(refreshToken, key: "refresh_token")

// Make API call
if let token = getToken(key: "access_token") {
    var request = URLRequest(url: url)
    request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
    // Make request
}
```

---

### **Mobile App (Android) — Keystore**

```kotlin
// Store in Android Keystore (hardware-backed if available)
fun storeToken(context: Context, token: String, key: String) {
    val encryptedSharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secret_shared_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    encryptedSharedPreferences.edit().putString(key, token).apply()
}

// Retrieve token
fun getToken(context: Context, key: String): String? {
    val encryptedSharedPreferences = EncryptedSharedPreferences.create(
        context,
        "secret_shared_prefs",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
    
    return encryptedSharedPreferences.getString(key, null)
}

// Usage
storeToken(context, accessToken, "access_token")
storeToken(context, refreshToken, "refresh_token")

val token = getToken(context, "access_token")
```

---

### **Desktop App (Electron) — Secure Storage**

```javascript
// Use electron-store with encryption
const Store = require('electron-store');

const store = new Store({
  encryptionKey: 'my-secret-key-from-secure-env'
});

// Store tokens
store.set('access_token', accessToken);
store.set('refresh_token', refreshToken);

// Retrieve
const token = store.get('access_token');

// Alternative: Use OS credential store
const keytar = require('keytar');

await keytar.setPassword('MyApp', 'access_token', accessToken);
await keytar.setPassword('MyApp', 'refresh_token', refreshToken);

const token = await keytar.getPassword('MyApp', 'access_token');
```

---

### **Backend Service (Node.js / Java)**

```javascript
// Node.js: Store in memory during service lifetime
let serviceAccessToken = null;

async function getServiceToken() {
    if (serviceAccessToken && isTokenValid(serviceAccessToken)) {
        return serviceAccessToken;
    }
    
    // Request new token from auth service
    const response = await fetch('https://auth-service/token', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            client_id: process.env.SERVICE_CLIENT_ID,      // From env var
            client_secret: process.env.SERVICE_CLIENT_SECRET // From env var
        })
    });
    
    const { access_token } = await response.json();
    serviceAccessToken = access_token;
    
    return access_token;
}

// Use in requests
const token = await getServiceToken();
const response = await fetch('https://api-service/data', {
    headers: { 'Authorization': `Bearer ${token}` }
});
```

```java
// Java: Store in memory (singleton)
@Component
public class ServiceTokenManager {
    private String serviceAccessToken;
    
    @Value("${service.client-id}")
    private String clientId;
    
    @Value("${service.client-secret}")
    private String clientSecret;
    
    // Store credentials in environment, not code
    // export SERVICE_CLIENT_ID=xyz
    // export SERVICE_CLIENT_SECRET=abc
    
    public synchronized String getServiceToken() throws Exception {
        if (isTokenValid(serviceAccessToken)) {
            return serviceAccessToken;
        }
        
        // Request new token
        String response = HttpClient.newHttpClient().send(
            HttpRequest.newBuilder()
                .uri(new URI("https://auth-service/token"))
                .POST(HttpRequest.BodyPublishers.ofString(
                    "{\"client_id\": \"" + clientId + "\", \"client_secret\": \"" + clientSecret + "\"}"
                ))
                .build(),
            HttpResponse.BodyHandlers.ofString()
        ).body();
        
        serviceAccessToken = parseToken(response);
        return serviceAccessToken;
    }
}
```

---

## 🏢 Real World — Where Companies Use This

**Stripe** (Payment Processing)
- Web dashboard: access token in memory, refresh token in httpOnly cookie
- Mobile app (iOS/Android): tokens in Keychain/Keystore
- Backend services: API keys in environment variables, never hardcoded
- Rotates tokens every 1 hour; refresh token extends session to 7 days
- XSS protection: strict Content Security Policy blocks localStorage

**Google / Google Workspace**
- Web: OAuth 2.0 access tokens in memory; refresh tokens in secure httpOnly cookies
- Mobile: Uses Google Play Services for token management (secure enclave)
- Desktop: Chrome browser stores tokens in OS credential manager (Windows Credential Manager, macOS Keychain)
- Continuously validates token signature; expired tokens trigger automatic refresh
- Short-lived (1 hour) access tokens prevent damage from token leaks

**Auth0** (Identity Platform)
- Web apps: access token in memory (ID token also available), refresh token in httpOnly cookie
- Mobile apps: SDK handles Keychain/Keystore storage automatically
- SPAs: Recommends Authorization Code + PKCE flow (no client secret in frontend)
- Provides built-in token refresh; developers don't manually manage refresh logic
- Token revocation: blacklist tokens in memory; long-lived tokens have separate revocation endpoint

**Razorpay** (Payments — India)
- Web: API key in httpOnly cookie during checkout; frontend tokens in memory
- Mobile apps: Tokens in encrypted SharedPreferences (Android) / Keychain (iOS)
- Backend services: API keys + API secrets stored in HashiCorp Vault (not env vars)
- Session timeout: 15-min access tokens; 24-hour refresh tokens for logged-in users
- Incident response: On security breach, immediately rotate API keys; existing tokens invalidated

**Slack**
- Web: OAuth access tokens in httpOnly cookies; refresh tokens in secure storage
- Mobile: Native Slack app stores tokens in OS secure enclave (cannot be accessed by other apps)
- Bot tokens: Stored server-side in encrypted database; never on client
- Token rotation: Rotating tokens supported; old tokens invalidated immediately upon rotation
- XSS hardening: Strict CSP, no eval(), no inline scripts prevent token theft

**Amazon Web Services (AWS)**
- Web console: Temporary credentials (access key + secret key) stored in browser memory only
- Mobile: AWS Amplify SDK handles secure token storage (Keychain/Keystore)
- Backend: IAM roles assign temporary credentials; no long-lived secrets in code
- Token lifetime: 15-min to 1-hour temporary credentials; MFA required for sensitive operations
- Credential refresh: Automatic refresh before expiry using STS (Security Token Service)

**Netflix**
- Web: Short-lived access tokens in memory; long-lived refresh tokens in httpOnly cookies
- Mobile: Native iOS/Android apps use platform-provided secure storage
- Backend microservices: Service-to-service tokens cached in memory; validated on every request
- Token encryption: Access tokens encrypted with AES-256 before transmission
- Revocation: Revoked tokens checked against real-time blacklist on API gateway

**GitLab / GitHub**
- Web: Personal access tokens stored in encrypted browser storage (not localStorage)
- Mobile: OAuth tokens in Keychain (iOS) / Keystore (Android)
- CLI tools: Tokens in `~/.netrc` (encrypted) or credential manager (Windows/macOS)
- Git operations: Use SSH keys (asymmetric) or OAuth tokens (symmetric)
- Token rotation: Support token rotation; old tokens immediately revoked

---

## ⚠️ Critical Security Rules

| ❌ DON'T | ✅ DO |
|---------|------|
| Store JWT in localStorage | Store in memory (web) or secure storage (mobile) |
| Store JWT in sessionStorage | Use httpOnly cookies for refresh token |
| Send JWT in URL parameters | Send access token in Authorization header |
| Hardcode tokens in code | Use environment variables for credentials |
| Store tokens in plain text files | Use OS-provided encryption (Keychain, Keystore) |
| Transmit token over HTTP | Always use HTTPS |
| Never expire tokens | Short-lived access (15 min), longer refresh (7 days) |
| Store sensitive data in JWT payload | JWT is readable; only use for public claims |

---

## 🎯 Interview Answer Template

**Q: "Where do you store JWT tokens?"**

> Depends on the client:
> 
> - **Web:** Refresh token in httpOnly cookie (XSS-safe), access token in memory (JS variable). On page refresh, access token is lost—use refresh token to get new one.
> - **Mobile:** Native secure storage (iOS Keychain, Android Keystore). Encrypted by OS, can't be accessed by other apps.
> - **Backend:** In memory during service lifetime. Credentials (client_id, client_secret) in environment variables, never in code.
> 
> Key principle: Never use localStorage (XSS vulnerable). Always use httpOnly cookies for long-lived tokens. Use short-lived access tokens (15 min) + longer-lived refresh tokens (7 days).

---

## 📌 File Locations (Where to Find This Info)

- **Related:** `27-auth-authz-fundamentals.md` (JWT creation, validation, signing)
- **Related:** `24-api-gateway-pattern.md` (how gateway validates tokens)
- **Security:** `13-security-pki.md` (RS256 asymmetric signing)

---

## 🔄 Quick Checklist Before Interview

- [ ] Access token: 15-min lifetime (short)
- [ ] Refresh token: 7-day lifetime (longer)
- [ ] Refresh token: httpOnly cookie (web apps)
- [ ] Access token: Authorization header (not cookie)
- [ ] Mobile: Use Keychain/Keystore (not SharedPreferences)
- [ ] Never localStorage (XSS vulnerable)
- [ ] Backend: Credentials in env vars, not code
- [ ] HTTPS required (Secure flag)
- [ ] SameSite=Strict for CSRF protection
- [ ] Refresh token rotation: every /auth/refresh issues a new refresh token, revokes the old
- [ ] Reuse detection: replayed rotated token → revoke ALL sessions for that user (OAuth 2.1)

---

## 🧾 TL;DR

> **Web:** refresh_token in httpOnly cookie, access_token in memory. **Mobile:** OS secure storage (Keychain/Keystore). **Backend:** memory + env vars. **Never:** localStorage or hardcoded credentials. **OAuth 2.1:** rotate refresh tokens on every use; replay of a rotated token = theft → revoke all sessions.

---

## 🔄 Changelog

| Date | Change |
|---|---|
| Jul 1, 2026 | Added browser auto-send mechanics callout before backend refresh code; added token rotation + reuse detection section (OAuth 2.1 `revokeAllForUser` pattern); updated checklist with rotation and reuse detection items. |
