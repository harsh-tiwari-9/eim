# JIO eIM Platform — Phase 1 Developer Guide

> Everything you need to build, understand, and extend the platform.  
> Read this before writing a single line of code.

---

## Table of Contents

1. [What We Are Building](#1-what-we-are-building)
2. [System Architecture](#2-system-architecture)
3. [All Decisions and Why](#3-all-decisions-and-why)
4. [Understanding JWT](#4-understanding-jwt)
5. [Understanding BCrypt](#5-understanding-bcrypt)
6. [Understanding Spring Security](#6-understanding-spring-security)
7. [The Header Auth Pattern](#7-the-header-auth-pattern)
8. [Understanding BouncyCastle and X.509](#8-understanding-bouncycastle-and-x509)
9. [Database Schema — Complete Reference](#9-database-schema--complete-reference)
10. [Flyway — Why DB Migrations Matter](#10-flyway--why-db-migrations-matter)
11. [Auth Flow — Step by Step](#11-auth-flow--step-by-step)
12. [Inventory Registration Flow — Step by Step](#12-inventory-registration-flow--step-by-step)
13. [Service-by-Service Reference](#13-service-by-service-reference)
14. [Role Matrix](#14-role-matrix)
15. [API Reference](#15-api-reference)
16. [Running the Project](#16-running-the-project)
17. [What Comes Next — Sprint 2](#17-what-comes-next--sprint-2)
18. [Resources](#18-resources)

---

## 1. What We Are Building

JIO eIM Platform is a **GSMA SGP.32-compliant eSIM IoT Remote Manager** built entirely in-house. It manages the profile lifecycle (enable, disable, delete, download) of millions of IoT devices — smart meters, trackers, sensors — without physical SIM replacement.

**Phase 1 scope: three services**

```
api-gateway      :8080   The only entry point. Validates JWT, routes requests.
user-service     :8086   Login, JWT issuance, user management.
inventory-service :8087  Accept SGP.32 vendor payload, store device details.
```

**The vendor payload this phase handles:**

```json
{
  "eid":      "89044045910000000000000966075104",
  "ownerId":  "1.3.6.1.4.1.43565",
  "profiles": [{ "iccid": "89918740407079955539", "state": "ENABLED", "profileClass": "2" }],
  "autoEnable": "false",
  "autoDelete": "false",
  "ipaCapabilities": {
    "directRspServerCommunication":   true,
    "indirectRspServerCommunication": false
  },
  "euiccEumCerts": [{
    "euiccCertAsBase64": "MIICBzCC...",
    "eumCertAsBase64":   "MIICxjCC..."
  }]
}
```

What `ownerId: "1.3.6.1.4.1.43565"` means: this is an **OID** (Object Identifier) — a globally unique identifier used in X.509 certificates and enterprise systems. `43565` is Kigen's (ARM's eSIM division) IANA Private Enterprise Number. Every company that works with certificates and standards has one.

What `profileClass: "2"` means: SGP.32 defines three profile classes — `0=test`, `1=provisioning`, `2=operational`. Operational profiles are what go on production devices.

---

## 2. System Architecture

```
Browser (JIO Pulse)
        │
        ▼
┌─────────────────────────────────────────┐
│          API Gateway  :8080              │
│  • JWT validation                        │
│  • Header injection (X-User-Role etc.)   │
│  • Reverse proxy to downstream services  │
└──────────────┬──────────────────────────┘
               │
       ┌───────┴───────┐
       ▼               ▼
User Service      Inventory Service
   :8086              :8087
   login              store vendor payload
   JWT issuance       cert chain validation
   user CRUD          device registry
       │                    │
       └──────┬─────────────┘
              ▼
        PostgreSQL :5432
        ├── users schema
        │     users, auth_events
        └── inventory schema
              devices, device_profiles,
              ipa_capabilities, euicc_certs,
              ipa_config, euicc_info,
              device_tags, ingest_jobs,
              ingest_rows
```

**Why a shared PostgreSQL with separate schemas instead of separate databases?**

For Phase 1 with two developers on a single machine, a shared database is simpler to run and operate. The separate schemas (`users.*` and `inventory.*`) give us the logical separation we need. Each service owns its schema via Flyway — they never touch each other's tables.

When you scale to production, you can split schemas into separate databases without changing any application code because all your queries are already schema-qualified.

---

## 3. All Decisions and Why

### Auth: login → JWT (not API keys)

We decided against API keys because they require a database lookup on every single request. With millions of IoT devices polling constantly, that becomes a bottleneck.

JWT is **stateless** — the gateway validates the token's cryptographic signature without touching the database. The trade-off is that you can't immediately revoke a JWT (until it expires). For Phase 1 with short-expiry tokens this is acceptable.

### Symmetric HMAC-SHA256 (not asymmetric RSA)

Both user-service (signs) and api-gateway (verifies) share the same secret key. For an internal platform where all services are trusted, this is simpler than managing a public/private key pair. We upgrade to asymmetric in Sprint 2.

### MVC proxy gateway (not Spring Cloud Gateway reactive)

We use a Spring Boot MVC app with a `ProxyController` and `RestTemplate` rather than the fully reactive Spring Cloud Gateway. Why? Reactive programming (WebFlux, Mono, Flux) has a steep learning curve, and the proxy pattern gets us 90% of the value with 10% of the complexity. We switch to proper SCG when we need circuit breaking, retries, and path rewriting.

### Fine-grained auth: gateway (coarse) + service (fine)

The gateway asks: *"is this a valid token for a real user?"*

Each service asks: *"does this user's role allow THIS specific operation?"*

Using `@PreAuthorize` in controllers keeps role logic where it belongs — next to the business logic that needs it, not in a centralised routing table.

### Inventory: synchronous for now, Kafka later

The vendor JSON payload is stored synchronously in Phase 1. When we add bulk upload of 1 million records, we switch to async Kafka processing. This phased approach lets us ship a working API quickly.

### Certs stored in DB, not object storage

eUICC certificates are ~2KB each. For the current scale (thousands of devices), storing base64 in PostgreSQL `TEXT` columns is fine. At millions of devices, we move to object storage with the key in the DB.

---

## 4. Understanding JWT

**JWT = JSON Web Token**. It is a compact, self-contained token that carries claims (pieces of information) and is cryptographically signed.

### Structure

A JWT looks like: `xxxxx.yyyyy.zzzzz`

Three parts separated by dots:

```
HEADER.PAYLOAD.SIGNATURE

Header  (base64url):  {"alg":"HS256","typ":"JWT"}
Payload (base64url):  {"sub":"user-uuid","username":"harsh","role":"PLATFORM_ENGINEER","exp":1748000000}
Signature:            HMAC_SHA256(base64url(header) + "." + base64url(payload), secret)
```

**The header** says which algorithm is used.

**The payload** (called *claims*) is what you actually care about. Standard claims:
- `sub` — subject (who the token is about)
- `iat` — issued at (Unix timestamp)
- `exp` — expiration (Unix timestamp)

Custom claims in our token:
- `username`
- `email`
- `role`

**The signature** is what makes it tamper-proof. If anyone modifies the payload (e.g. changes `role` from `READ_ONLY` to `SUPER_ADMIN`), the signature won't match and the gateway rejects it.

### Why it's stateless

Traditional sessions require the server to maintain a lookup table: `session-id → user`. Every request hits the database. JWT flips this — the token IS the session. The server just verifies the signature mathematically. No database involved.

This is why the gateway can validate millions of tokens per second.

### How HMAC-SHA256 signing works

```
secret_key = "your-256-bit-secret-from-application.yml"

signature = HMAC_SHA256(
    input  = base64url(header) + "." + base64url(payload),
    key    = secret_key
)
```

Anyone with the secret key can verify a token. This is why the same `JWT_SECRET` environment variable is configured in both `user-service` (signs) and `api-gateway` (verifies).

The default dev secret in the project is:
```
SklPZUlNUGxhdGZvcm1EZXZTZWNyZXRLZXkyMDI2SEVS
```
This is base64-encoded. In production, replace with a cryptographically random 256-bit value and store in HashiCorp Vault.

### Expiry

Our tokens expire after 8 hours by default (`JWT_EXPIRY_HOURS=8`). After expiry the gateway rejects the token with `401 Token has expired — please login again`. The client must re-authenticate.

Short expiry limits the damage if a token is stolen — it becomes useless after 8 hours even if the attacker holds on to it.

### Using JJWT library

The project uses **JJWT 0.12.x**. The API changed significantly from 0.11.x — make sure you're using the 0.12 style:

```java
// SIGNING (user-service JwtService)
Jwts.builder()
    .subject(userId)
    .claim("role", role)
    .issuedAt(new Date())
    .expiration(expiry)
    .signWith(secretKey)     // 0.12: algorithm inferred from key type
    .compact();

// VERIFICATION (gateway JwtAuthFilter)
Jwts.parser()
    .verifyWith(secretKey)
    .build()
    .parseSignedClaims(token)
    .getPayload();           // returns Claims object
```

The secret key is created from the base64-encoded string:
```java
SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secretString));
```

---

## 5. Understanding BCrypt

**Never store passwords in plaintext.** Not even as MD5 or SHA-256 hashes. Here's why and what to use instead.

### Why MD5/SHA is not enough

MD5 and SHA produce a **deterministic** output — the same input always gives the same hash. This enables **rainbow table attacks**: attackers precompute hashes for millions of common passwords and then just look up your hash. The attack is instant.

### How BCrypt solves this

BCrypt has two properties that defeat rainbow tables:

**1. Salt** — BCrypt generates a random salt and embeds it in the hash. The same password produces a different hash each time:
```
"password123" → $2a$10$N9qo8uLOi...  (run 1)
"password123" → $2a$10$mK7ZI3pX...  (run 2)
```

**2. Cost factor (work factor)** — BCrypt deliberately wastes CPU cycles. With `strength=10`, each hash takes ~100ms. This is intentional. A developer logging in once doesn't notice. An attacker trying billions of guesses is brought to their knees.

```java
BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(10); // strength 10

// Hashing (done once when creating user)
String hash = bcrypt.encode("Admin@123");
// → "$2a$10$Xl0yhvzLIaJCDdKBS0Lld.ksK7c2Wss1tbMLr9yvjRF1a7d9VUGNi"

// Verification (done on every login)
boolean valid = bcrypt.matches("Admin@123", storedHash); // true
```

Notice: `matches()` extracts the salt from the stored hash automatically. You never store or compare salts manually.

The seed admin in `V1__users.sql` has password `Admin@123` (change this before any real deployment).

---

## 6. Understanding Spring Security

Spring Security is a filter chain that runs before your controllers. Each request passes through a series of filters before reaching your code.

### The Filter Chain

```
HTTP Request
    │
    ▼
[Filter 1: HeaderAuthFilter]      ← reads X-User-Role, populates SecurityContext
    │
    ▼
[Filter 2: UsernamePasswordAuthenticationFilter]  ← built-in, not used in our case
    │
    ▼
[Authorization check]              ← is this request allowed to proceed?
    │
    ▼
[Your Controller]
    │
    ▼
[@PreAuthorize check]              ← does this user's role allow THIS method?
```

### SecurityContextHolder

The `SecurityContextHolder` is a thread-local container that holds the current authentication. Think of it as a "who is calling me right now" holder.

Our `HeaderAuthFilter` sets it:
```java
// Inside HeaderAuthFilter.doFilterInternal()
var auth = new UsernamePasswordAuthenticationToken(
    userId,           // principal (who you are)
    null,             // credentials (not needed - already authenticated by gateway)
    List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ENGINEER"))  // authorities
);
SecurityContextHolder.getContext().setAuthentication(auth);
```

After this runs, any downstream code (including `@PreAuthorize`) can ask "who is the current user?" and get this authentication object.

### @PreAuthorize

`@PreAuthorize` is a method-level security annotation. Before your method body runs, Spring Security evaluates the expression and throws `AccessDeniedException` (→ 403) if it's false.

```java
@PostMapping
@PreAuthorize("hasAnyRole('SUPER_ADMIN','PLATFORM_ENGINEER','BSS_SYSTEM')")
public ResponseEntity<ApiResponse<InventoryResponse>> register(...) {
    // Only runs if the current user has one of these roles
}
```

**Why `ROLE_` prefix?**

Spring Security stores roles with a `ROLE_` prefix internally. When you call `new SimpleGrantedAuthority("ROLE_PLATFORM_ENGINEER")`, the `hasRole('PLATFORM_ENGINEER')` expression matches because `hasRole` automatically prepends `ROLE_`. The `hasAnyRole(...)` expression does the same. This is why our `HeaderAuthFilter` adds the prefix when building authorities.

### @EnableMethodSecurity

Without this annotation on your `SecurityConfig`, the `@PreAuthorize` annotations are **silently ignored**. Always include it:

```java
@Configuration
@EnableMethodSecurity         // ← this is what activates @PreAuthorize
public class SecurityConfig { ... }
```

---

## 7. The Header Auth Pattern

This is the key pattern that makes authentication work across microservices without every service needing to validate JWT.

```
┌───────────────────────────────────────────────────┐
│                   API Gateway                      │
│                                                    │
│  Receives: Authorization: Bearer eyJhbGciOi...    │
│                                                    │
│  1. Verifies HMAC signature                        │
│  2. Checks expiry                                  │
│  3. Extracts claims: userId, username, role        │
│  4. Injects headers:                               │
│       X-User-Id:    "uuid-of-the-user"             │
│       X-User-Role:  "PLATFORM_ENGINEER"            │
│       X-Username:   "harsh"                        │
│       X-User-Email: "harsh@jio.internal"           │
│  5. Strips the original Authorization header       │
│  6. Forwards to downstream service                 │
└───────────────────────────────────────────────────┘

┌───────────────────────────────────────────────────┐
│            Inventory Service                       │
│                                                    │
│  Receives:                                         │
│    X-User-Id:    "uuid-of-the-user"               │
│    X-User-Role:  "PLATFORM_ENGINEER"              │
│    (no Authorization header)                       │
│                                                    │
│  HeaderAuthFilter reads these headers              │
│  Sets SecurityContext with role                    │
│  @PreAuthorize("hasRole('PLATFORM_ENGINEER')")     │
│  Allows the request through                        │
└───────────────────────────────────────────────────┘
```

**Why services never see the raw JWT:**
- Services don't need the JWT library as a dependency
- Services can't forge tokens (they don't know the secret)
- Rotating the JWT secret only requires updating gateway + user-service
- Services trust the gateway completely — this is why they must be behind a network boundary and not publicly accessible

**What MutableHttpServletRequest does:**

The gateway uses a `HttpServletRequestWrapper` subclass to add headers to the request. The servlet specification doesn't allow modifying an incoming request's headers directly, so the wrapper pattern is used — a proxy object that intercepts `getHeader()` calls and returns the injected values:

```java
public class MutableHttpServletRequest extends HttpServletRequestWrapper {
    private final Map<String, String> extraHeaders;

    @Override
    public String getHeader(String name) {
        String injected = extraHeaders.get(name);
        return injected != null ? injected : super.getHeader(name);
    }
}
```

---

## 8. Understanding BouncyCastle and X.509

When a vendor provides devices to JIO, they include eUICC certificates. These certificates prove that:
- This eUICC chip was manufactured by a trusted manufacturer (Kigen, Infineon, STMicro)
- The chip identity (EID) is authentic
- The public key in the certificate genuinely belongs to this chip

### X.509 Certificates

An X.509 certificate is a structured binary document (DER encoding) containing:
- Subject: who this certificate belongs to
- Issuer: who signed this certificate
- Validity: notBefore, notAfter dates
- Public Key: the subject's public key
- Signature: the issuer's cryptographic signature over the above

In our vendor payload:
```
euiccCertAsBase64 → CERT.eUICC.ECDSA
                    Subject: O=Kigen, serialNumber=89044045910000000000000966075104
                    Issuer: CN=EUMConsumerDGDub
                    Contains: eUICC's ECDSA P-256 public key

eumCertAsBase64   → CERT.EUM.ECDSA (EUM = eUICC Manufacturer)
                    Subject: CN=EUMConsumerDGDub
                    Issuer: GSM Association - RSP2 Root CI1
                    Contains: Kigen's signing public key
```

### Certificate Chain Validation

The chain proves: **Kigen** (trusted manufacturer) vouches for **this specific eUICC chip**.

```
GSMA CI Root (GSMA's root of trust)
     │  signs
     ▼
CERT.EUM.ECDSA (Kigen's cert — trusted by GSMA)
     │  signs
     ▼
CERT.eUICC.ECDSA (this specific chip's cert — trusted by Kigen)
```

To validate: `euiccCert.verify(eumCert.getPublicKey())`

This confirms that the eUICC cert's signature was created by the private key corresponding to the EUM cert's public key — i.e., Kigen signed it.

Full chain validation (verifying the EUM cert against the GSMA CI root) is Sprint 2 — requires importing GSMA CI root certificates.

### Extracting the Public Key

Once you trust the certificate, you extract the public key:

```java
// The public key is inside the certificate
PublicKey pub = euiccCert.getPublicKey();

// Encode it as SubjectPublicKeyInfo DER bytes, convert to hex
byte[] encoded = pub.getEncoded();
String hex = toHex(encoded);
```

This hex string is stored in `inventory.euicc_certs.euicc_public_key_hex`. Later, when the device sends a `HandleNotification`, the ESipa service uses this public key to verify the device's ECDSA signature — proving the response genuinely came from this specific chip.

### Why BouncyCastle?

Java's built-in `java.security` can parse X.509 certificates, but BouncyCastle adds:
- Better support for ECDSA P-256 (the curve used in SGP.32)
- More certificate extensions parsed correctly
- ASN.1 encoding/decoding (needed for building eUICC packages later)

Always register the provider before using it:
```java
static {
    Security.addProvider(new BouncyCastleProvider());
}
```

---

## 9. Database Schema — Complete Reference

### Schema Separation

Two schemas live in one PostgreSQL database (`jio_eim`). Each service owns its schema via Flyway — they never cross-query.

```sql
users     schema  →  user-service owns, manages via Flyway
inventory schema  →  inventory-service owns, manages via Flyway
```

### users schema

#### `users.users`

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | `gen_random_uuid()` |
| `username` | VARCHAR(100) UNIQUE | Lowercase, alphanumeric + `. - _` |
| `email` | VARCHAR(200) UNIQUE | |
| `full_name` | VARCHAR(200) | |
| `password_hash` | VARCHAR(256) | BCrypt strength 10. Never store plaintext. |
| `role` | VARCHAR(30) | `SUPER_ADMIN` / `PLATFORM_ENGINEER` / `READ_ONLY` / `BSS_SYSTEM` |
| `status` | VARCHAR(20) | `ACTIVE` / `SUSPENDED` / `DELETED` |
| `created_at` | TIMESTAMP | Set on insert, never updated |
| `last_login` | TIMESTAMP | Updated on every successful login |

#### `users.auth_events`

Every login attempt (success or failure), logout, and role change is logged here. Never delete from this table — it's your audit trail.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | Auto-increment |
| `user_id` | UUID FK | References `users.users`. NULL if the username didn't exist. |
| `event_type` | VARCHAR(50) | `LOGIN_SUCCESS` / `LOGIN_FAILED` / `LOGOUT` / `ROLE_CHANGED` |
| `ip_address` | VARCHAR(45) | IPv4 or IPv6 |
| `user_agent` | VARCHAR(500) | Browser/client identifier |
| `details` | JSONB | Flexible extra data (e.g. failure reason) |
| `created_at` | TIMESTAMP | When the event occurred |

### inventory schema (V1 — populated from vendor payload)

#### `inventory.devices`

The anchor table. One row per EID. Every other inventory table is a satellite of this one.

| Column | Type | Notes |
|---|---|---|
| `eid` | VARCHAR(32) PK | 32 uppercase hex chars. The permanent identity of the eUICC chip. |
| `owner_id` | VARCHAR(100) | OID string. `1.3.6.1.4.1.43565` = Kigen. |
| `auto_enable` | BOOLEAN | From vendor payload `autoEnable`. Parsed from string "false"/"true". |
| `auto_delete` | BOOLEAN | From vendor payload `autoDelete`. |
| `status` | VARCHAR(20) | `REGISTERED` → `ACTIVE` → `SUSPENDED` / `DELETED` |
| `registered_at` | TIMESTAMP | First time this EID was ingested. Never changes. |
| `updated_at` | TIMESTAMP | Last update. Managed by `@PreUpdate`. |

#### `inventory.device_profiles`

One row per ICCID per device. A device typically has 2-5 profiles.

| Column | Type | Notes |
|---|---|---|
| `id` | BIGSERIAL PK | |
| `eid` | VARCHAR(32) FK | References `devices`. `ON DELETE CASCADE`. |
| `iccid` | VARCHAR(20) | Profile identifier. 19-20 digits. |
| `state` | VARCHAR(20) | `ENABLED` / `DISABLED` / `INSTALLED` / `DELETED` |
| `profile_class` | CHAR(1) | `0`=test, `1`=provisioning, `2`=operational |
| `mno_id` | VARCHAR(50) | Mobile Network Operator identifier |
| `is_fallback` | BOOLEAN | If true, this profile activates if all others fail |

`UNIQUE (eid, iccid)` — one device can't have the same profile twice.

When registering, existing profiles are deleted and re-inserted fresh (`deleteByEid` then insert). This keeps the data in sync with the latest vendor payload.

#### `inventory.ipa_capabilities`

One-to-one with `devices`. Records what download modes the device supports.

| Column | Type | Notes |
|---|---|---|
| `eid` | VARCHAR(32) PK FK | |
| `direct_rsp_server_communication` | BOOLEAN | `true` = supports Direct Download (ES9+). IPA talks to SM-DP+ directly. |
| `indirect_rsp_server_communication` | BOOLEAN | `true` = supports Indirect Download (ES9+'). eIM proxies the connection. |

Most modern devices support direct. Indirect is for constrained devices that can't maintain a TLS session to SM-DP+.

#### `inventory.euicc_certs`

The certificate chain. Critical for future `HandleNotification` ECDSA verification.

| Column | Type | Notes |
|---|---|---|
| `eid` | VARCHAR(32) PK FK | |
| `euicc_cert_base64` | TEXT | CERT.eUICC.ECDSA — device-specific X.509 certificate (PEM/DER base64) |
| `eum_cert_base64` | TEXT | CERT.EUM.ECDSA — manufacturer's certificate |
| `euicc_public_key_hex` | VARCHAR(300) | Extracted SubjectPublicKeyInfo DER bytes as hex. Used for ECDSA verify later. |
| `euicc_subject` | VARCHAR(300) | DN string from the eUICC cert, e.g. `O=Kigen, SERIALNUMBER=894...` |
| `eum_subject` | VARCHAR(300) | DN string from the EUM cert |
| `ci_reference` | VARCHAR(100) | GSMA CI root OID |
| `cert_valid_from` | TIMESTAMP | When the eUICC cert becomes valid |
| `cert_valid_to` | TIMESTAMP | When it expires. Alert before this date. |
| `chain_valid` | BOOLEAN | `true` if `euiccCert.verify(eumCert.getPublicKey())` passed |

### inventory schema (V2 — future use)

#### `inventory.ipa_config`

Operational config per device. Changes over time via eCO operations.

| Column | Key notes |
|---|---|
| `ipa_mode` | `IPAd` (software on device OS) or `IPAe` (embedded in eUICC chip) |
| `poll_interval_seconds` | Default 14400 (4 hours). Can be changed via eCO. |
| `configured_eim_url` | What eIM URL the device is pointing to. Set by eCO `updateEim`. |
| `last_poll_at` | When the device last contacted ESipa. |
| `last_poll_result` | `SUCCESS` / `EMPTY` / `FAILED`. |

#### `inventory.euicc_info`

Hardware specs from device firmware.

| Column | Notes |
|---|---|
| `euicc_form_factor` | `MFF2` (soldered), `2FF` (mini), `3FF` (micro), `4FF` (nano) |
| `profile_slots_available` | How many profiles can this chip hold |
| `sgp_version` | e.g. `SGP32_V1` |
| `euicc_category` | `M2M` or `Consumer` |

#### `inventory.device_tags`

Flexible key-value metadata. Eliminates schema changes when operators need new groupings.

```sql
-- Tag a device as belonging to a customer deployment
INSERT INTO inventory.device_tags (eid, tag_key, tag_value) 
VALUES ('8904...', 'customer', 'tata-motors');

-- Tag a device with its region
INSERT INTO inventory.device_tags (eid, tag_key, tag_value)
VALUES ('8904...', 'region', 'mumbai');
```

Later: `SELECT eid FROM inventory.device_tags WHERE tag_key='region' AND tag_value='mumbai'`

#### `inventory.ingest_jobs` + `inventory.ingest_rows`

For the 1M-record Kafka-based bulk upload (Sprint 2).

`ingest_jobs` — one row per uploaded file. Tracks overall progress.

`ingest_rows` — one row per device in the file. **Note: `ingest_rows.eid` is NOT a foreign key to `devices`** because failed rows may have invalid EIDs that never get inserted into `devices`.

---

## 10. Flyway — Why DB Migrations Matter

**Problem:** If you change your database schema directly, other developers won't know about it. Their local databases diverge from yours. Staging breaks.

**Solution:** Flyway is a migration tool that tracks which SQL scripts have been run and which haven't. Every schema change is a versioned SQL file. Flyway runs them in order on startup.

### File naming convention

```
V1__users.sql             ← runs first
V2__inventory_additional_tables.sql  ← runs second
V3__add_source_job_to_devices.sql    ← future change
```

Format: `V{number}__{description}.sql` — note the **double underscore**.

### Never modify a migration that has been run

Flyway stores a checksum of each file. If you modify `V1__users.sql` after it's been applied, the next startup fails with a checksum mismatch. Always create a new `V2__` file for changes.

### Schema configuration

Each service manages its own schema. In `application.yml`:

```yaml
spring:
  flyway:
    schemas: users              # Flyway only looks in this schema
    default-schema: users       # Default schema for migrations
    locations: classpath:db/migration
    baseline-on-migrate: true   # Creates baseline if DB has existing tables

  jpa:
    properties:
      hibernate:
        default_schema: users   # JPA queries without @Table(schema=...) go here
```

And in entity classes, always specify the schema explicitly:

```java
@Table(schema = "inventory", name = "devices")
public class InventoryDevice { ... }
```

---

## 11. Auth Flow — Step by Step

### Login

```
1. Client sends:
   POST /api/auth/login
   { "username": "harsh", "password": "Admin@123" }

2. API Gateway receives request.
   Path matches /api/auth/** → JwtAuthFilter SKIPS this path.
   ProxyController forwards to user-service:8086/api/auth/login

3. user-service AuthController calls AuthService.login()

4. AuthService:
   a. userRepo.findByUsername("harsh") → loads User from DB
   b. Checks status == ACTIVE
   c. bcrypt.matches("Admin@123", user.getPasswordHash()) → true
   d. Updates user.lastLogin to NOW()
   e. Logs auth_event: LOGIN_SUCCESS

5. JwtService.generate(user):
   Creates JWT payload:
   {
     "sub": "user-uuid",
     "username": "harsh",
     "email": "harsh@jio.internal",
     "role": "PLATFORM_ENGINEER",
     "iat": 1748000000,
     "exp": 1748028800   (iat + 8 hours)
   }
   Signs with HMAC-SHA256 using the shared secret.

6. Response returned:
   {
     "accessToken": "eyJhbGc...",
     "tokenType":   "Bearer",
     "expiresIn":   28800,
     "username":    "harsh",
     "role":        "PLATFORM_ENGINEER"
   }

7. Client stores the accessToken. Uses it for all subsequent requests.
```

### Authenticated Request

```
1. Client sends:
   GET /api/inventory/89044045910000000000000966075104
   Authorization: Bearer eyJhbGciOiJIUzI1...

2. API Gateway → JwtAuthFilter.doFilter()
   a. Path /api/inventory/** is not in PUBLIC list → auth required
   b. Extracts token: "eyJhbGciOiJIUzI1..."
   c. Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token)
   d. Checks expiry (embedded in payload)
   e. Extracts: userId, username, role = "PLATFORM_ENGINEER"

3. Gateway creates MutableHttpServletRequest with injected headers:
   X-User-Id:    "user-uuid"
   X-User-Role:  "PLATFORM_ENGINEER"
   X-Username:   "harsh"

4. ProxyController forwards to inventory-service:8087
   (WITHOUT the original Authorization header)

5. inventory-service → HeaderAuthFilter:
   Reads X-User-Id and X-User-Role
   Creates: UsernamePasswordAuthenticationToken("user-uuid", null, [ROLE_PLATFORM_ENGINEER])
   Sets it in SecurityContextHolder

6. InventoryController.get() is reached
   @PreAuthorize("hasAnyRole('SUPER_ADMIN','PLATFORM_ENGINEER','READ_ONLY','BSS_SYSTEM')")
   → Spring evaluates: does current auth have ROLE_PLATFORM_ENGINEER? → YES
   → Method body executes

7. Response returned to client.
```

---

## 12. Inventory Registration Flow — Step by Step

```
1. Client sends:
   POST /api/inventory
   Authorization: Bearer eyJhbGc...
   Content-Type: application/json
   { "eid": "89044045910000000000000966075104", "ownerId": "1.3.6.1.4.1.43565", ... }

2. Gateway validates JWT → injects X-User-Role: PLATFORM_ENGINEER
   → forwards to inventory-service:8087

3. InventoryController.register()
   @PreAuthorize("hasAnyRole('SUPER_ADMIN','PLATFORM_ENGINEER','BSS_SYSTEM')")
   → passes

4. InventoryRequest validation (@Valid):
   - @NotBlank on eid
   - @Pattern("[0-9A-F]{20,32}") on eid
   - @NotBlank on ownerId
   If any fail → 400 Bad Request returned immediately

5. InventoryService.register():

   STEP A — Upsert device
   deviceRepo.findById("8904...") → not found → create new
   device.setOwnerId("1.3.6.1.4.1.43565")
   device.setAutoEnable(false)   // parseBoolean("false")
   device.setAutoDelete(false)
   device.setStatus("REGISTERED")
   deviceRepo.save(device)
   → INSERT INTO inventory.devices ...

   STEP B — Replace profiles
   profileRepo.deleteByEid("8904...")   // clear existing
   For each profile in req.getProfiles():
     INSERT INTO inventory.device_profiles (eid, iccid, state, profile_class)
     VALUES ('8904...', '8991...', 'ENABLED', '2')

   STEP C — Upsert IPA capabilities
   capsRepo.findById("8904...") → not found → create new
   caps.setDirectRspServerCommunication(true)
   caps.setIndirectRspServerCommunication(false)
   capsRepo.save(caps)
   → INSERT INTO inventory.ipa_capabilities ...

   STEP D — Parse and validate certificates
   certDto = req.getEuiccEumCerts().get(0)  // take first pair
   
   CertificateService.validateAndExtract(certDto):
     a. Base64.decode(euiccCertAsBase64) → byte[]
     b. CertificateFactory.getInstance("X.509").generateCertificate(bytes) → X509Certificate
     c. Same for eumCert
     d. euiccCert.getSubjectX500Principal().getName() → "O=Kigen, SERIALNUMBER=8904..."
     e. euiccCert.verify(eumCert.getPublicKey()) → chain valid? true
     f. euiccCert.checkValidity() → not expired?
     g. euiccCert.getPublicKey().getEncoded() → hex string for future ECDSA verify
   
   certRepo.findById("8904...") → not found → create new
   cert.setEuiccCertBase64(...)
   cert.setEumCertBase64(...)
   cert.setEuiccPublicKeyHex("30593013...") // the extracted public key
   cert.setChainValid(true)
   certRepo.save(cert)

6. buildResponse() assembles:
   - device fields
   - profiles list
   - IPA capabilities
   - CertSummary with chainValid, subjects, expiry, public key

7. Response:
   HTTP 200
   {
     "success": true,
     "message": "Device registered — certificate chain valid",
     "data": {
       "eid": "89044045910000000000000966075104",
       "ownerId": "1.3.6.1.4.1.43565",
       "status": "REGISTERED",
       "autoEnable": "false",
       "autoDelete": "false",
       "profiles": [{ "iccid": "89918740407079955539", "state": "ENABLED", "profileClass": "2" }],
       "ipaCapabilities": { "directRspServerCommunication": true, ... },
       "certInfo": {
         "chainValid": true,
         "euiccSubject": "O=Kigen, SERIALNUMBER=...",
         "eumSubject": "CN=EUMConsumerDGDub",
         "euiccPublicKeyHex": "30593013...",
         "certValidFrom": "2025-10-22T13:56:09Z",
         "certValidTo": "2125-10-22T13:56:09Z"
       }
     }
   }
```

---

## 13. Service-by-Service Reference

### api-gateway

**What it does:** The only service exposed to the outside world. Every request passes through it.

**Key files:**
```
filter/JwtAuthFilter.java           ← validates JWT, injects X-User-* headers
filter/MutableHttpServletRequest.java ← allows adding headers to request
proxy/ProxyController.java          ← routes /api/auth/**, /api/users/** → user-service
                                       routes /api/inventory/** → inventory-service
config/SecurityConfig.java          ← disables CSRF, permits all (auth is in filter)
config/AppConfig.java               ← creates RestTemplate bean for proxying
```

**application.yml key settings:**
```yaml
jwt.secret: ${JWT_SECRET:SklPZUlNUGxhdGZvcm1EZXZTZWNyZXRLZXkyMDI2SEVS}
eim.services.user-url:      http://localhost:8086
eim.services.inventory-url: http://localhost:8087
```

**Public paths (no JWT required):**
- `/api/auth/` — login endpoint
- `/actuator/health`, `/actuator/info`

**How routing works:** `ProxyController` receives all `@RequestMapping` requests and calls `RestTemplate.exchange(targetUrl, method, entity, String.class)`. The `String.class` response type means it passes the raw JSON response bytes back to the caller untouched.

---

### user-service

**What it does:** Issues JWTs, manages users.

**Key files:**
```
controller/AuthController.java      ← POST /api/auth/login
controller/UserController.java      ← POST /api/users, GET /api/users, etc.
service/AuthService.java            ← bcrypt verify, JWT generate, auth_event log
service/JwtService.java             ← JJWT wrapper: generate(), validate()
service/UserService.java            ← CRUD for User entity
filter/HeaderAuthFilter.java        ← reads X-User-Role for @PreAuthorize
config/SecurityConfig.java          ← @EnableMethodSecurity
entity/User.java                    ← @Table(schema="users", name="users")
db/migration/V1__users.sql          ← schema + tables + admin seed
```

**Seed admin credentials:**
- Username: `admin`
- Password: `Admin@123`
- Role: `SUPER_ADMIN`

**Important:** The `BCryptPasswordEncoder` is instantiated with `new BCryptPasswordEncoder(10)` directly in the service classes (not as a Spring bean). This works but isn't ideal — if you add it as a `@Bean`, you can inject it everywhere.

---

### inventory-service

**What it does:** Accepts the vendor SGP.32 payload and stores it across 4 tables.

**Key files:**
```
controller/InventoryController.java ← @PreAuthorize + delegates to service
service/InventoryService.java       ← the 4-step register flow
service/CertificateService.java     ← BouncyCastle cert parsing + chain validation
filter/HeaderAuthFilter.java        ← same pattern as user-service
config/SecurityConfig.java          ← @EnableMethodSecurity

entity/InventoryDevice.java         ← maps to inventory.devices
entity/DeviceProfile.java           ← maps to inventory.device_profiles
entity/IpaCapabilities.java         ← maps to inventory.ipa_capabilities
entity/EuiccCert.java               ← maps to inventory.euicc_certs

repository/InventoryDeviceRepository.java   ← findById, search(), list
repository/DeviceProfileRepository.java     ← findByEid, deleteByEid
repository/IpaCapabilitiesRepository.java   ← findById
repository/EuiccCertRepository.java         ← findById

dto/InventoryRequest.java           ← exact shape of vendor JSON payload
dto/InventoryResponse.java          ← response with cert summary

db/migration/V1__inventory.sql      ← devices, device_profiles, ipa_capabilities, euicc_certs
db/migration/V2__inventory_additional_tables.sql ← ipa_config, euicc_info, device_tags, ingest_jobs, ingest_rows
```

**Why `deleteByEid` before inserting profiles:**

If a vendor re-registers a device (updated payload), we replace profiles wholesale. The `UNIQUE (eid, iccid)` constraint would cause a duplicate key error if we tried to insert existing profiles again. Deleting and re-inserting is atomic inside the `@Transactional` method.

**`autoEnable` is a String in the payload:**

The vendor sends `"autoEnable": "false"` as a string, not a boolean. The `parseBoolean()` method handles both: `"true"/"false"` strings AND actual booleans (in case the vendor fixes their API).

---

## 14. Role Matrix

| Endpoint | SUPER_ADMIN | PLATFORM_ENGINEER | READ_ONLY | BSS_SYSTEM |
|---|:---:|:---:|:---:|:---:|
| POST /api/auth/login | ✅ | ✅ | ✅ | ✅ |
| POST /api/users | ✅ | ❌ | ❌ | ❌ |
| GET /api/users | ✅ | ✅ | ❌ | ❌ |
| PATCH /api/users/{id}/role | ✅ | ❌ | ❌ | ❌ |
| POST /api/users/{id}/deactivate | ✅ | ❌ | ❌ | ❌ |
| POST /api/inventory | ✅ | ✅ | ❌ | ✅ |
| GET /api/inventory | ✅ | ✅ | ✅ | ✅ |
| GET /api/inventory/{eid} | ✅ | ✅ | ✅ | ✅ |
| DELETE /api/inventory/{eid} | ✅ | ✅ | ❌ | ❌ |
| POST /api/psmo/operations | ✅ | ❌ | ❌ | ❌ |
| GET /api/psmo/operations/{id} | ✅ | ✅ | ✅ | ✅ |
| GET /api/psmo/operations | ✅ | ✅ | ✅ | ✅ |

**Role descriptions:**
- `SUPER_ADMIN` — full access, manages users
- `PLATFORM_ENGINEER` — operates the platform, registers/manages devices
- `READ_ONLY` — can view, cannot modify
- `BSS_SYSTEM` — JIO's billing/OSS system, can register devices programmatically

---

## 15. API Reference

### Authentication

```
POST /api/auth/login
Content-Type: application/json

Body:
{
  "username": "admin",
  "password": "Admin@123"
}

Response 200:
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 28800,
    "username": "admin",
    "email": "admin@jio.internal",
    "role": "SUPER_ADMIN"
  }
}

Error 400: Invalid username or password (intentionally vague — don't leak which is wrong)
Error 400: Account is suspended
```

All subsequent requests require:
```
Authorization: Bearer {accessToken}
```

---

### User Management

```
POST /api/users               Create user           (SUPER_ADMIN)
GET  /api/users               List users            (SUPER_ADMIN, PLATFORM_ENGINEER)
GET  /api/users/{id}          Get user by UUID      (SUPER_ADMIN, PLATFORM_ENGINEER)
PATCH /api/users/{id}/role    Update role           (SUPER_ADMIN)
POST /api/users/{id}/deactivate  Deactivate user    (SUPER_ADMIN)

POST /api/users body:
{
  "username": "harsh",
  "email": "harsh@jio.internal",
  "fullName": "Harsh Tiwari",
  "password": "SecurePass@123",
  "role": "PLATFORM_ENGINEER"
}
```

---

### Inventory

```
POST   /api/inventory              Register device      (SUPER_ADMIN, PLATFORM_ENGINEER, BSS_SYSTEM)
GET    /api/inventory              List devices         (all roles)
GET    /api/inventory/{eid}        Get device details   (all roles)
DELETE /api/inventory/{eid}        Soft-delete device   (SUPER_ADMIN, PLATFORM_ENGINEER)

GET /api/inventory query params:
  ?ownerId=1.3.6.1.4.1.43565   Filter by manufacturer OID
  ?status=REGISTERED            Filter by status
  ?search=8904                  Prefix search on EID
  ?page=0&size=20               Pagination
```

**Register device body** — exact vendor payload shape:
```json
{
  "eid": "89044045910000000000000966075104",
  "ownerId": "1.3.6.1.4.1.43565",
  "profiles": [
    { "iccid": "89918740407079955539", "state": "ENABLED", "profileClass": "2" }
  ],
  "autoEnable": "false",
  "autoDelete": "false",
  "ipaCapabilities": {
    "directRspServerCommunication": true,
    "indirectRspServerCommunication": false
  },
  "euiccEumCerts": [
    {
      "euiccCertAsBase64": "MIICBzCC...",
      "eumCertAsBase64": "MIICxjCC..."
    }
  ]
}
```

---

### PSMO — Profile State Management Operations

```
POST /api/psmo/operations          Submit an operation      (SUPER_ADMIN)
GET  /api/psmo/operations/{id}      Get one operation        (SUPER_ADMIN, PLATFORM_ENGINEER, READ_ONLY, BSS_SYSTEM)
GET  /api/psmo/operations           List operation history   (SUPER_ADMIN, PLATFORM_ENGINEER, READ_ONLY, BSS_SYSTEM)

GET /api/psmo/operations query params (all optional):
  ?eid=8904...                  Filter by device EID
  ?type=ENABLE                  AUDIT | ENABLE | DISABLE | DELETE | DOWNLOAD
  ?status=EXECUTED              PENDING | SIGNED | SENT | EXECUTED | FAILED
  ?page=0&size=20               Pagination (size max 100); newest first
```

**Submit body** — `targetIccid` required for ENABLE/DISABLE/DELETE (decimal ICCID); omit for AUDIT:
```json
{
  "eid": "89044045910000000000000966075104",
  "type": "ENABLE",
  "targetIccid": "89918740407079955539"
}
```

Operations are asynchronous: submit returns `202` with `status: "PENDING"`; the eUICC executes it on
its next poll. Poll `GET /api/psmo/operations/{id}` (or watch the list) until `status` reaches
`EXECUTED`/`FAILED`. `GET /api/psmo/operations` returns a paged wrapper
(`content, page, size, totalElements, totalPages, first, last`) of the same operation objects.

---

## 16. Running the Project

### Prerequisites

```bash
# Java 21
java -version  # → openjdk 21...

# Maven
mvn -version

# Docker (for PostgreSQL + MinIO later)
docker -version
```

### Start the Database

```bash
docker-compose up -d  # starts PostgreSQL
```

### Build All Modules

```bash
cd jio-eim-phase1
mvn clean install -DskipTests
```

### Start Services (3 separate terminals)

```bash
# Terminal 1 — User Service (starts Flyway → creates users schema + tables + seeds admin)
cd jio-eim-phase1
mvn spring-boot:run -pl user-service

# Terminal 2 — Inventory Service (starts Flyway → creates inventory schema + tables)
mvn spring-boot:run -pl inventory-service

# Terminal 3 — API Gateway
mvn spring-boot:run -pl api-gateway
```

### Verify Everything is Up

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8086/actuator/health
curl http://localhost:8087/actuator/health
```

### First Test: Login

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}'
```

Save the `accessToken` from the response.

### Second Test: Register a Device

```bash
TOKEN="eyJhbGciOiJIUzI1NiJ9..."  # from login response

curl -X POST http://localhost:8080/api/inventory \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "eid": "89044045910000000000000966075104",
    "ownerId": "1.3.6.1.4.1.43565",
    "profiles": [{"iccid":"89918740407079955539","state":"ENABLED","profileClass":"2"}],
    "autoEnable": "false",
    "autoDelete": "false",
    "ipaCapabilities": {"directRspServerCommunication":true,"indirectRspServerCommunication":false},
    "euiccEumCerts": [{
      "euiccCertAsBase64": "MIICBzCCAaygAwIBAgIOBGPk1HCmDL58CwNVIuAwCgYIKoZIzj0EAwIwYjELMAkGA1UEBhMCVUsxEjAQBgNVBAcMCUNhbWJyaWRnZTEOMAwGA1UECgwFS2lnZW4xFDASBgNVBAsMC0VuZ2luZWVyaW5nMRkwFwYDVQQDDBBFVU1Db25zdW1lckRHRHViMCAXDTI1MTAyMjEzNTYwOVoYDzIxMjUxMDIyMTM1NjA5WjA7MQ4wDAYDVQQKDAVLaWdlbjEpMCcGA1UEBRMgODkwNDQwNDU5MTAwMDAwMDAwMDAwMDA5NjYwNzUxMDQwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAAT30k/ZX85tR2m/kAD2bXttHAFfZ1lHtvwQ6jOVqKkhEhUadppMpdCTCeLKb+JHI5OjYRoBvVR/2URsS6nyIzF+o2swaTAdBgNVHQ4EFgQUtp9hgtNG3ehRSpZZOWMY1RmbV40wHwYDVR0jBBgwFoAUGJoIU6/UlPaweZoLZ/D10MEp4/YwDgYDVR0PAQH/BAQDAgeAMBcGA1UdIAEB/wQNMAswCQYHZ4ESAQIBATAKBggqhkjOPQQDAgNJADBGAiEAj8yrxSu0J3d8jAbwLM989eruisdYe/uIRCCGmpEV/pYCIQCRQWtthSpJMof6UfH22pIl8oPLsC6k840Q+uyqm4Q6wQ==",
      "eumCertAsBase64": "MIICxjCCAm2gAwIBAgIQWs6PmNyGSUBKva1lfiBQkzAKBggqhkjOPQQDAjBEMRgwFgYDVQQKEw9HU00gQXNzb2NpYXRpb24xKDAmBgNVBAMTH0dTTSBBc3NvY2lhdGlvbiAtIFJTUDIgUm9vdCBDSTEwHhcNMjIxMDA1MDAwMDAwWhcNNDkxMjMxMjM1OTU5WjBiMQswCQYDVQQGEwJVSzESMBAGA1UEBwwJQ2FtYnJpZGdlMQ4wDAYDVQQKDAVLaWdlbjEUMBIGA1UECwwLRW5naW5lZXJpbmcxGTAXBgNVBAMMEEVVTUNvbnN1bWVyREdEdWIwWTATBgcqhkjOPQIBBggqhkjOPQMBBwNCAATG+X7qL3aK0XuoYpqwBOz3kP9DV/IIG+FcIxn3Lslm+aJgu7XHNRYGl3d7gptZyfZfvJo9eFQQ2Jhvwk74pDtbo4IBITCCAR0wEgYDVR0TAQH/BAgwBgEB/wIBADAXBgNVHSABAf8EDTALMAkGB2eBEgECAQIwTQYDVR0fBEYwRDBCoECgPoY8aHR0cDovL2dzbWEtY3JsLnN5bWF1dGguY29tL29mZmxpbmVjYS9nc21hLXJzcDItcm9vdC1jaTEuY3JsMA4GA1UdDwEB/wQEAwIBBjA3BgNVHR4BAf8ELTAroCkwJ6QlMCMxDjAMBgNVBAoTBUtpZ2VuMREwDwYDVQQFEwg4OTA0NDA0NTAWBgNVHREEDzANiAsrBgEEAYO7EwECAjAdBgNVHQ4EFgQUGJoIU6/UlPaweZoLZ/D10MEp4/YwHwYDVR0jBBgwFoAUgTcPUSXQsdQI1MOyMubSXnlb6/swCgYIKoZIzj0EAwIDRwAwRAIgEGYYqo+9pfQpkoH043EiBObp87CV8ZuOUu2mH35KTbECIDX4LQ40bi6DbH8YRDkqOS5D2976ROC/IhUl6JTlk4PR"
    }]
  }'
```

### Verify Storage

```bash
# Check directly in PostgreSQL
psql -h localhost -U eim -d jio_eim -c "SELECT eid, owner_id, status FROM inventory.devices;"
psql -h localhost -U eim -d jio_eim -c "SELECT eid, iccid, state FROM inventory.device_profiles;"
psql -h localhost -U eim -d jio_eim -c "SELECT eid, chain_valid, cert_valid_to FROM inventory.euicc_certs;"
```

---

## 17. What Comes Next — Sprint 2

### 1. Bulk Inventory Upload

Replace the synchronous `POST /api/inventory` with:
- `POST /api/inventory/upload` → stores JSON file in MinIO, creates job, returns 202 + jobId
- File reader uses **Jackson Streaming API** (not `objectMapper.readValue()`) to process records one at a time without loading the full 500MB file into memory
- Each record published to Kafka `inventory.ingest`, consumed and stored asynchronously
- `GET /api/inventory/jobs/{jobId}` → progress tracking via `ingest_jobs.processed_records` counter

### 2. JWT Refresh Tokens

Short-lived access tokens (1 hour) + long-lived refresh tokens (7 days). Client uses refresh token to get a new access token silently without re-entering credentials.

New table: `users.refresh_tokens`.

### 3. UTIMACO HSM Integration

Replace BouncyCastle PKCS12 keystore in Package Signing Service with UTIMACO HSM via PKCS#11. Private key never leaves hardware.

---

## 18. Resources

### JWT
- **jwt.io** — paste any token and decode it. Keep this tab open while developing: https://jwt.io
- **JJWT library** — the README quickstart covers everything in 50 lines: https://github.com/jwtk/jjwt#quickstart
- **Understanding JWT claims**: https://www.iana.org/assignments/jwt/jwt.xhtml

### Spring Security
- **@PreAuthorize and method security** — the shortest complete guide: https://www.baeldung.com/spring-security-method-security
- **Spring Security architecture** — how the filter chain actually works: https://docs.spring.io/spring-security/reference/servlet/architecture.html

### BCrypt
- **Spring Security password storage** — why and how: https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html
- **Why BCrypt** — the original 1999 paper is surprisingly readable: https://www.usenix.org/legacy/events/usenix99/provos/provos.pdf

### Spring Data JPA
- **Baeldung JPA + Hibernate**: https://www.baeldung.com/the-persistence-layer-with-spring-and-jpa
- **@Transactional explained** — what it actually does and why propagation matters: https://www.baeldung.com/transaction-configuration-with-jpa-and-spring

### Flyway
- **Getting started**: https://documentation.red-gate.com/fd/quickstart-how-flyway-works-184127223.html
- **Naming conventions**: https://documentation.red-gate.com/fd/migrations-184127470.html

### BouncyCastle + X.509
- **X.509 certificates explained**: https://www.ssl.com/faqs/what-is-an-x-509-certificate/
- **Java cert validation**: https://www.baeldung.com/java-certificate-authority-chain-validation
- **BouncyCastle developer guide**: https://www.bouncycastle.org/documentation.html

### SGP.32 Standard
- **Official spec** (free download after registration): https://www.gsma.com/solutions-and-impact/technologies/networks/esim-specification/
- Read sections **5** (ESipa interface), **6** (PSMO operations), **8** (package format), **9** (security) first

### Jackson Streaming (for the 1M record upload)
- **Why not readValue() at scale**: https://www.baeldung.com/jackson-streaming-api

### Kafka (for when you add bulk upload)
- **Confluent Kafka 101** — free video course, 2 hours, covers everything: https://developer.confluent.io/courses/apache-kafka/events/
- **Spring Kafka reference**: https://docs.spring.io/spring-kafka/docs/current/reference/html/

---

## Common Mistakes to Avoid

**Forget `@EnableMethodSecurity`** — `@PreAuthorize` silently does nothing without it.

**Forget the `ROLE_` prefix** — `SimpleGrantedAuthority("PLATFORM_ENGINEER")` won't match `hasRole('PLATFORM_ENGINEER')`. Always use `"ROLE_" + role`.

**Modify a Flyway migration that's already been applied** — Flyway checks checksums. Modify V1 after it ran → next startup fails. Always create V2.

**Store the JWT secret in code** — use `${JWT_SECRET}` environment variable and set it differently per environment.

**Use `objectMapper.readValue(file, List.class)` for a 2GB file** — loads the entire file into memory, kills the JVM. Use Jackson Streaming API when you implement bulk upload.

**Validate JWT in every service** — only the gateway validates. Services trust the gateway. Adding JWT validation inside inventory-service means every service needs the secret and the library.

**Return different errors for "wrong username" vs "wrong password"** — this leaks whether the username exists. Always return the same generic message.

---

*Phase 1 complete. Build it, break it, understand it.*
