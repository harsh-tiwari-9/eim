# JIO eIM Platform — Phase 1

GSMA SGP.32-compliant eSIM IoT Remote Manager (Phase 1).

## Services

| Service | Port | Description |
|---------|------|-------------|
| api-gateway | 8080 | JWT validation, header injection, reverse proxy |
| user-service | 8086 | Login, JWT issuance, user management |
| inventory-service | 8087 | SGP.32 vendor payload, device registry |

## Prerequisites

- Java 21
- Maven 3.9+
- Docker (PostgreSQL)

## Quick start

```bash
# Start PostgreSQL
docker compose up -d

# Build all modules
mvn clean install -DskipTests

# Terminal 1 — user-service
mvn spring-boot:run -pl user-service

# Terminal 2 — inventory-service
mvn spring-boot:run -pl inventory-service

# Terminal 3 — api-gateway
mvn spring-boot:run -pl api-gateway
```

## Smoke test

```bash
# Login (via gateway)
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123"}'

# Use accessToken from response for inventory calls
```

Default admin: `admin` / `Admin@123` (change before production).

See [jio-eim-phase1-dev-guide.md](jio-eim-phase1-dev-guide.md) for architecture and API details.
