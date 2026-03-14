# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Spring Boot 3.3 application that fetches hourly spot electricity prices from the aWATTar API, converts them to CZK using the Frankfurter API, persists them in PostgreSQL, and serves a Chart.js dashboard.

## Common Commands

```bash
# Start the development database (PostgreSQL + pgAdmin)
cd development/docker && docker compose up -d

# Run the application (dev profile: scheduler runs every minute, DEBUG logging)
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# Build
./mvnw package -DskipTests

# Run tests
./mvnw test

# Run a single test class
./mvnw test -Dtest=PriceQueryServiceTest

# Compile only (fast feedback)
./mvnw compile
```

**Development endpoints:**
- App: `http://localhost:8080`
- pgAdmin: `http://localhost:5050` (admin@local.dev / admin — server "SpotPrice Dev" is pre-configured, password: `spotprice`)
- Health: `http://localhost:8080/actuator/health`

## Architecture

### Data Flow

```
aWATTar API (hourly EUR/MWh)
    └─► PriceIngestionService (@Scheduled)
            ├─ SHA-256 hash of raw JSON → compare with latest SyncLog
            ├─ IF changed: fetch EUR/CZK from Frankfurter API
            └─ upsert SpotPrice records (keyed on hour-truncated Instant)
```

### Key Design Decisions

- **Hash-before-parse**: Raw HTTP response string is hashed *before* Jackson parsing. Never re-serialize for hashing — Jackson map ordering is not guaranteed.
- **`@Transactional` scope**: `persistPrices()` in `PriceIngestionService` saves SyncLog + all SpotPrice records atomically. If exchange rate fetch fails, the whole cycle is skipped (not partially committed).
- **Upsert**: `findByTimestamp()` → update existing or save new. The `spot_price.timestamp` column has a `UNIQUE` constraint as a DB-level guard.
- **Timestamps**: Always stored as UTC `Instant` (`TIMESTAMPTZ`). Day boundaries for today-stats use `ZoneId.of("Europe/Prague")`. aWATTar returns epoch **milliseconds** — use `Instant.ofEpochMilli()`, not `ofEpochSecond()`.
- **HTTP client**: `RestClient` (Spring 6.1) with explicit 10 s connect / 30 s read timeouts. Not `RestTemplate` (deprecated) or `WebClient` (reactive overhead unnecessary).
- **Schema**: Flyway owns the schema (`ddl-auto: validate`). Never use `ddl-auto: create` or `update`.

### Package Structure (`cz.jce.spotprice`)

| Package | Responsibility |
|---|---|
| `config` | `AppConfig` (RestClient bean), `SchedulingConfig` (@EnableScheduling) |
| `entity` | `SpotPrice`, `SyncLog` — JPA entities mapped to Flyway-managed tables |
| `repository` | Spring Data JPA repos; `SpotPriceRepository` has custom JPQL aggregation queries for min/max |
| `dto/awattar` | Deserialization of aWATTar API response |
| `dto/frankfurter` | Deserialization of Frankfurter exchange rate response |
| `dto/api` | Response records for the three REST endpoints |
| `service` | `ExchangeRateService`, `PriceIngestionService`, `PriceQueryService` |
| `controller` | `PriceController` (`/api/prices/*`), `GlobalExceptionHandler` |

### REST API

| Endpoint | Behaviour |
|---|---|
| `GET /api/prices/current` | Price for the current hour (hour-truncated `Instant.now()`) |
| `GET /api/prices/today-stats` | Min/max EUR+CZK for today (Prague timezone day boundary) |
| `GET /api/prices/history` | Last 24 h by default; accepts `?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD` |

Returns `404` with `{"error": "..."}` when no data exists yet (first run before scheduler fires).

### Profiles

| Profile | Scheduler cron | Log level |
|---|---|---|
| (default) | `0 0 * * * *` (top of hour) | INFO |
| `dev` | `0 * * * * *` (every minute) | DEBUG for `cz.jce.spotprice` and Spring web client |

### Database

Migrations in `src/main/resources/db/migration/`:
- `V1` — `spot_price` table with `UNIQUE` on `timestamp`
- `V2` — `sync_log` table

Docker Compose in `development/docker/` — uses `.env` for all credentials. pgAdmin server is auto-configured via `pgadmin/servers.json` mount.
