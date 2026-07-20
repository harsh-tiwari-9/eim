# eIM Platform — UI API Integration Guide

All UI traffic goes through the **API Gateway** (`:8080`). The UI must never call the
`user`/`inventory`/`psmo` services directly. Device-facing ESipa endpoints
(`/gsma/rsp2/asn1`, `/esipa/*`) are **not** for the UI — the eUICC/IPA calls those.

- **Base URL:** `http://<host>:8080`
- **Content type:** `application/json` (except file upload — `multipart/form-data`)

## Auth model

1. `POST /api/auth/login` → returns `accessToken` (JWT).
2. Send it on **every** other request: `Authorization: Bearer <accessToken>`.
3. The gateway validates the token and injects identity/role downstream. A missing/expired/invalid
   token → `401`. Insufficient role → `403`.

Token also carries `role`; the UI can decode the JWT (or use the login response `role`) to
show/hide actions.

## Roles

| Role | Typical UI persona |
|------|--------------------|
| `SUPER_ADMIN` | full access incl. user mgmt, device delete, PSMO operations |
| `PLATFORM_ENGINEER` | read + inventory register/upload, read users/jobs/ops |
| `READ_ONLY` | read-only (lists, get-by-id) |
| `BSS_SYSTEM` | machine account — inventory register/upload, reads |

## Standard response envelope

Success responses wrap data in:

```json
{ "success": true, "message": "human readable", "data": { ... } }
```

`data` may be an object, a list, a paged object, or `null`.

## Errors — read this

- `401` (from gateway) has a fixed shape: `{ "success": false, "message": "Token has expired, please login again" }`.
- Validation (`400`), not-found (`404`), forbidden (`403`) come from the downstream services and use
  Spring's default error body (`timestamp`, `status`, `error`, `message`, `path`) — **not** the
  `success/message/data` envelope. **Treat any non-2xx generically** (check HTTP status, show
  `message`/`error`).
- ⚠️ Known limitation: the gateway proxies downstream 4xx/5xx and may surface some of them as `500`.
  Don't rely on the exact downstream status for non-2xx; branch on 2xx vs not, and on `401`/`403`.

---

# 1. Auth

### POST `/api/auth/login` — _public (no token)_
Request:
```json
{ "username": "admin", "password": "secret" }
```
Response `200`:
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "accessToken": "eyJhbGciOiJI...",
    "tokenType": "Bearer",
    "expiresIn": 3600,
    "username": "admin",
    "email": "admin@ril.com",
    "role": "SUPER_ADMIN"
  }
}
```
Store `accessToken`; attach as `Authorization: Bearer` on all subsequent calls.

---

# 2. Users  (base `/api/users`)

### POST `/api/users` — create user · `SUPER_ADMIN`
Request:
```json
{ "username": "jane", "email": "jane@ril.com", "fullName": "Jane Doe",
  "password": "P@ssw0rd", "role": "PLATFORM_ENGINEER" }
```
Response `200`: `data` = **UserResponse** (see shape below).

### GET `/api/users` — list users · `SUPER_ADMIN`, `PLATFORM_ENGINEER`
Response `200`: `data` = array of **UserResponse**.

### GET `/api/users/{id}` — get one · `SUPER_ADMIN`, `PLATFORM_ENGINEER`
`{id}` = user UUID. `data` = **UserResponse**.

### PATCH `/api/users/{id}/role` — change role · `SUPER_ADMIN`
Request: `{ "role": "READ_ONLY" }` → `data` = updated **UserResponse**.

### POST `/api/users/{id}/deactivate` — deactivate · `SUPER_ADMIN`
No body. `data` = **UserResponse** with `status` = `INACTIVE`.

**UserResponse shape:**
```json
{ "id": "uuid", "username": "jane", "email": "jane@ril.com", "fullName": "Jane Doe",
  "role": "PLATFORM_ENGINEER", "status": "ACTIVE",
  "createdAt": "2026-07-08T10:00:00Z", "lastLogin": "2026-07-08T11:30:00Z" }
```

---

# 3. Inventory  (base `/api/inventory`)

### POST `/api/inventory` — register one device · `SUPER_ADMIN`, `PLATFORM_ENGINEER`, `BSS_SYSTEM`
Request:
```json
{
  "eid": "89049032000000000000000000000001",
  "ownerId": "acme-corp",
  "profiles": [
    { "iccid": "8991000000000000001", "state": "enabled", "profileClass": "operational" }
  ],
  "autoEnable": "true",
  "autoDelete": "false",
  "ipaCapabilities": { "directRspServerCommunication": true, "indirectRspServerCommunication": false },
  "euiccEumCerts": [
    { "euiccCertAsBase64": "MIIB...", "eumCertAsBase64": "MIIC..." }
  ]
}
```
- `eid`: exactly 32 digits (required). `ownerId`: required. `euiccEumCerts`: at least one (required).
- Response `200`: `data` = **InventoryResponse**. `message` tells you if the cert chain validated.

### POST `/api/inventory/list` — search/paginate devices · `SUPER_ADMIN`, `PLATFORM_ENGINEER`, `READ_ONLY`, `BSS_SYSTEM`
Query params: `?page=0&size=20` (`size` 1–100). Body (all optional):
```json
{ "ownerId": "acme-corp", "status": "REGISTERED", "eid": "8904...0001", "search": "acme" }
```
Response `200`: `data` = **PagedResponse<InventoryResponse>**.
> Note: there is **no** `GET /api/inventory/{eid}` — to fetch a single device, call this with the
> `eid` filter.

### DELETE `/api/inventory/{eid}` — soft-delete device · `SUPER_ADMIN`
`{eid}` = 32 digits. Response `200`: `{ "success": true, "message": "Device deleted", "data": null }`.

### POST `/api/inventory/upload` — bulk upload (async) · `SUPER_ADMIN`, `PLATFORM_ENGINEER`, `BSS_SYSTEM`
`multipart/form-data`, field `file` (JSON file). Response `202 Accepted`: `data` = **IngestJobResponse**
(status `UPLOADED`). Poll the job endpoints for progress.

### GET `/api/inventory/jobs` — list ingest jobs · all read roles
Query: `?status=PROCESSING&uploadedBy=jane@ril.com&page=0&size=10`
(`status` ∈ `UPLOADED|PROCESSING|COMPLETED|FAILED`). `data` = **PagedResponse<IngestJobResponse>**.

### GET `/api/inventory/jobs/{jobId}` — job status · all read roles
`data` = **IngestJobResponse** (watch `processedRecords`/`totalRecords`/`failedRecords`).

### GET `/api/inventory/jobs/{jobId}/download` — download result file · `SUPER_ADMIN`, `PLATFORM_ENGINEER`, `BSS_SYSTEM`
Returns a **file stream** (not JSON) — the per-record ingest result. Handle as a download.

**InventoryResponse shape:**
```json
{
  "eid": "8904...0001", "ownerId": "acme-corp", "status": "REGISTERED",
  "autoEnable": "true", "autoDelete": "false",
  "profiles": [ { "iccid": "8991000000000000001", "state": "enabled", "profileClass": "operational" } ],
  "ipaCapabilities": { "directRspServerCommunication": true, "indirectRspServerCommunication": false },
  "certInfo": {
    "chainValid": true, "euiccSubject": "CN=...", "eumSubject": "CN=...",
    "euiccPublicKeyHex": "04ab...", "certValidFrom": "2025-01-01T00:00:00Z",
    "certValidTo": "2030-01-01T00:00:00Z"
  }
}
```

**About `profiles` freshness:** the `profiles[]` list reflects the eIM's stored view in
`inventory.device_profiles`. It is seeded at device registration and then **reconciled with the
on-card truth every time a successful `AUDIT` runs for that device** (a successful AUDIT fully
replaces the stored list with what the eUICC actually reports). So after registration the list may
be empty or stale; **run a PSMO `AUDIT` to refresh it**, after which this endpoint reflects the
card. `iccid` is the decimal ICCID; `state` is `enabled`/`disabled`.

**IngestJobResponse shape:**
```json
{ "jobId": 42, "status": "COMPLETED", "fileName": "batch1.json", "uploadedBy": "jane@ril.com",
  "inputFilePath": "...", "outputFilePath": "...", "remarks": null,
  "totalRecords": 1000, "processedRecords": 1000, "failedRecords": 3,
  "createdAt": "2026-07-08T10:00:00Z", "completedAt": "2026-07-08T10:02:00Z" }
```

**PagedResponse<T> shape:**
```json
{ "content": [ ... ], "page": 0, "size": 20, "totalElements": 137,
  "totalPages": 7, "first": true, "last": false }
```

---

# 4. PSMO — Profile State Management  (base `/api/psmo`)

> These are **asynchronous**. Submit returns `202` immediately with an operation in `PENDING`. The
> actual work happens when the device next polls the eIM. The UI must **poll** the get-operation
> endpoint to observe the outcome.

### POST `/api/psmo/operations` — submit an operation · `SUPER_ADMIN`
Request:
```json
{ "eid": "89049032000000000000000000000001", "type": "ENABLE", "targetIccid": "8991000000000000001" }
```
- `eid`: 20–32 hex chars (required).
- `type`: one of `AUDIT | ENABLE | DISABLE | DELETE | DOWNLOAD` (required).
  - `AUDIT` — list on-card profiles; no `targetIccid` needed.
  - `ENABLE` / `DISABLE` / `DELETE` — `targetIccid` **required**, must be the **decimal** ICCID
    (as shown in AUDIT's `iccid` field, **not** `iccidRaw`).
  - `DOWNLOAD` — provide `activationCode` (SGP.22 form `1$smdp.example.com$MATCHING-ID`; a leading
    `LPA:` is stripped). Omit it to tell the eUICC to use its default SM-DP+. Example body:
    `{ "eid": "...", "type": "DOWNLOAD", "activationCode": "1$smdp.example.com$ABC-123" }`.
- Response `202 Accepted`: `data` = **PsmoOperationResponse** with `status: "PENDING"`.

### GET `/api/psmo/operations/{id}` — get one operation's status/result · `SUPER_ADMIN`, `PLATFORM_ENGINEER`, `READ_ONLY`, `BSS_SYSTEM`
`{id}` = `operationId` from submit. `data` = **PsmoOperationResponse**. Poll until `status` is
`EXECUTED` or `FAILED`.

### GET `/api/psmo/operations` — paginated operation history (ops/logs page) · `SUPER_ADMIN`, `PLATFORM_ENGINEER`, `READ_ONLY`, `BSS_SYSTEM`
Query params (all optional): `eid`, `type` (`AUDIT|ENABLE|DISABLE|DELETE|DOWNLOAD`),
`status` (`PENDING|SIGNED|SENT|EXECUTED|FAILED`), `page` (default `0`), `size` (default `20`, max `100`).
Newest first. `data` = **PagedResponse<PsmoOperationResponse>** (same paged shape as inventory:
`content`, `page`, `size`, `totalElements`, `totalPages`, `first`, `last`). Each item is the same
**PsmoOperationResponse** as get-by-id, so the list can click through to a detail view.
Example: `GET /api/psmo/operations?status=FAILED&type=ENABLE&page=0&size=20`.

### POST `/api/psmo/operations/refresh` — refresh statuses of on-screen rows · `SUPER_ADMIN`, `PLATFORM_ENGINEER`, `READ_ONLY`, `BSS_SYSTEM`
For a polling UI: send the operation ids currently shown (typically the non-terminal ones) and get
their current state back in one call — instead of one `GET /operations/{id}` per row. Body:
`{ "operationIds": [101, 102, 103] }` (1–200 ids). `data` = array of **PsmoOperationResponse**,
ordered to match the requested ids; unknown ids are silently skipped. Poll this every few seconds
until the rows you care about are `EXECUTED`/`FAILED`.

### GET `/api/psmo/devices/{eid}/profiles` — on-card profile info (Profiles Information view) · `SUPER_ADMIN`, `PLATFORM_ENGINEER`, `READ_ONLY`, `BSS_SYSTEM`
Returns the profiles on the eUICC as of the device's **most recent successful AUDIT**. `data` =
```json
{
  "eid": "8904...5104",
  "auditedAt": "2026-07-20T09:59:00Z",
  "auditOperationId": 42,
  "profiles": [
    { "iccid": "89918740407079955539", "state": "disabled",
      "fallbackAttribute": false, "fallbackAllowed": false, "profileClass": "operational",
      "label": null, "profileName": "RJIO D2 NODA eIOT", "serviceProviderName": "Jio" }
  ]
}
```
Column mapping: `iccid`, `state` (enabled/disabled), `fallbackAttribute` (Fallback Attribute),
`fallbackAllowed` (Fallback Allowed), `profileClass` (test/provisioning/operational), `label`
(nickname), and under *Additional Information* → `profileName` + `serviceProviderName`.

⚠️ This reflects the **last AUDIT**, not live state. If the device was never audited, `profiles` is
`[]` and `auditedAt` is `null` — submit a PSMO `AUDIT`, poll it to `EXECUTED`, then re-fetch. Use
`auditedAt` to show an "as of" timestamp.

**Status lifecycle:** `PENDING` → `SIGNED` → `SENT` (device fetched it) → `EXECUTED` (success) |
`FAILED` (rejected/errored). `signedAt`/`sentAt`/`completedAt` timestamps mark each transition.

**DOWNLOAD is different** — it's a *trigger*, not a signed operation the eUICC runs locally. Its
terminal state from the eIM's view is **`SENT`** (the download trigger was delivered; the eUICC then
downloads directly from the SM-DP+ and reports the install to the SM-DP+, not to us). So for
DOWNLOAD, `SENT` means "trigger delivered, download in progress on the device" — **confirm the
profile actually installed by running an `AUDIT`** and checking the new ICCID appears. It does *not*
reach `EXECUTED`.

**PsmoOperationResponse shape:**
```json
{
  "operationId": 101, "eid": "8904...0001", "type": "ENABLE",
  "targetIccid": "8991000000000000001", "status": "EXECUTED", "requestedBy": "admin@ril.com",
  "resultPayload": "{...}",
  "createdAt": "...", "updatedAt": "...", "signedAt": "...", "sentAt": "...", "completedAt": "..."
}
```

**`resultPayload` is a JSON *string*** (parse it client-side). Examples of the parsed content:

- AUDIT success:
  ```json
  { "eimId": "id1", "counterValue": 7, "seqNumber": 9,
    "results": [ { "type": "listProfileInfo", "profiles": [
      { "iccid": "8991000000000000001", "iccidRaw": "981900000000000000f1",
        "state": "enabled", "serviceProviderName": "Jio", "profileName": "..." } ] } ] }
  ```
  → use `iccid` (decimal) as the `targetIccid` for a later enable/disable/delete.
- ENABLE/DISABLE/DELETE failure (`status: "FAILED"`):
  ```json
  { "results": [ { "type": "disable", "resultCode": 2, "result": "profileNotInEnabledState(2)" } ] }
  ```
  Result-code names the UI may surface: `ok(0)`, `iccidOrAidNotFound(1)`,
  `profileNotInDisabledState(2)` / `profileNotInEnabledState(2)`, `disallowedByPolicy(3)`,
  `catBusy(5)`, `rollbackNotAvailable(20)`, `returnFallbackProfile(21)`, `undefinedError(127)`.

---

## Gaps the UI team should know about (not yet available)

- **No single-device inventory GET** (`GET /api/inventory/{eid}` is disabled) — use `POST
  /api/inventory/list` with the `eid` filter.
