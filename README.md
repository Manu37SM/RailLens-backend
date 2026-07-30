# RailLens Backend

RailLens Backend is a Spring Boot REST API that powers the RailLens railway information system. Beyond core train/station/journey browsing, it exposes a full "Railway Intelligence" suite (train/station scoring, route comparison, network graph metrics, rankings, fun stats, achievements, dataset health), a hand-rolled JWT authentication system, and an admin panel — all designed as a stable REST contract for the Next.js web frontend today and for Android/iOS clients using the same API in the future.

## Tech Stack

* Java 26 (see `pom.xml` — `<java.version>`)
* Spring Boot 4.1.0 (Spring Framework 7)
* Spring Data JPA / Hibernate
* PostgreSQL
* Maven
* Caffeine (in-process caching, via `spring-boot-starter-cache` + `com.github.ben-manes.caffeine`)
* Hand-rolled JWT authentication (`io.jsonwebtoken` / jjwt 0.13.0) with `spring-security-crypto` used only for BCrypt password hashing — **not** the full Spring Security filter chain (see "Authentication" below for why)
* springdoc-openapi (Swagger UI / OpenAPI spec generation)
* opencsv / commons-csv (CSV import)
* Lombok

## Project Structure

```
src/main/java/com/labs/train/train_db
├── controller        REST endpoints
├── service            business logic
│   └── network         railway network graph (RailwayNetworkService and friends)
├── repository         Spring Data JPA repositories
├── entity              JPA entities
├── model               request/response DTOs, projections
├── exception           custom exceptions + GlobalExceptionHandler
├── config               CORS, interceptors, cache, security headers, OpenAPI, etc.
└── common               shared utilities (fuzzy matching, constants)
```

## Getting Started

### Prerequisites

* Java 26+
* PostgreSQL
* Maven (or use the bundled `./mvnw` / `mvnw.cmd` wrapper)

### Database

Create a PostgreSQL database (the default in `application.properties` is named `traindb`), then copy `src/main/resources/application.properties.example` to `application.properties` (gitignored) and fill in your own values.

```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/traindb
spring.datasource.username=your_username
spring.datasource.password=your_password
```

Schema is managed by **Flyway** (`src/main/resources/db/migration/`), not Hibernate's `ddl-auto`:

- `spring.jpa.hibernate.ddl-auto=validate` — Hibernate checks the entities match the schema at startup; it does not create or alter tables.
- `V1__baseline_schema.sql` reproduces the schema exactly as it was already deployed (built up over time by the old `ddl-auto=update` behavior). `spring.flyway.baseline-on-migrate=true` + `spring.flyway.baseline-version=1` let Flyway adopt an already-existing, already-populated database (every dev/prod database as of this writing) by marking V1 as already applied rather than trying to re-run it. A genuinely fresh/empty database (new clone, CI) has Flyway actually execute V1 to build the schema.
- `V2__enforce_schedule_integrity_constraints.sql` drafts the fix for the database schema review's two P0 findings (`NOT NULL` on `train_schedule.train_id`/`station_id`/`sequence_no`, plus a `UNIQUE (train_id, sequence_no)` constraint). **It is not applied automatically** — `spring.flyway.target=1` in `application.properties`/`.example` pins Flyway to the V1 baseline only, so V2 sits on disk, reviewable, but inert. Its own header comment lists the diagnostic queries to run against a real database first, and explains why: applying a `NOT NULL`/`UNIQUE` constraint against data that might violate it needs a human decision, not an automatic migration. Remove/raise `spring.flyway.target` only after doing that review.
- New migrations go in the same directory following Flyway's `V<number>__description.sql` naming convention.

### Environment Variables / Application Properties

All of the following are read from `application.properties` (see `application.properties.example` for the annotated template):

| Property | Purpose | Default |
|---|---|---|
| `spring.datasource.url` / `username` / `password` | PostgreSQL connection | — (required) |
| `spring.datasource.hikari.*` | Connection pool bounds (max size, idle timeout, etc.) | conservative, indie-scale defaults |
| `raillens.admin.api-key` | Shared secret required in the `X-Admin-Key` header for every `/api/v1/admin/**` request. Left blank rejects all admin requests rather than leaving the routes open. | — (required) |
| `raillens.cors.allowed-origins` | Comma-separated list of origins allowed to call `/api/**` from a browser | `http://localhost:3000` |
| `raillens.jwt.secret` | HMAC signing secret for auth JWTs — the app refuses to start if unset or under 32 bytes. Generate with `openssl rand -base64 32`. | — (required) |
| `raillens.jwt.expiration-minutes` | Access token lifetime | `60` |
| `raillens.jwt.refresh-expiration-days` | Refresh token lifetime (rotated on use, so effectively single-use per issuance) | `30` |
| `server.compression.*` | Gzip compression for JSON/HTML/CSS/JS responses | enabled, 1KB threshold |
| `management.endpoints.web.exposure.include` | Actuator endpoints exposed (`health`, `info`) | `health,info` |

### Run the Application

```bash
mvn spring-boot:run
```

or

```bash
./mvnw spring-boot:run
```

The API starts on `http://localhost:8080`. With `springdoc-openapi` on the classpath, Swagger UI is available at `/swagger-ui.html` and the raw OpenAPI spec at `/v3/api-docs`. A basic liveness/readiness check is at `GET /actuator/health` (unauthenticated, deliberately outside `/api/**` so it isn't behind rate limiting or the admin key).

## API Endpoints

All endpoints are under `/api/v1/`. Every route under `/api/**` is subject to `RateLimitInterceptor`; routes under `/api/v1/admin/**` additionally require an `X-Admin-Key` header, and routes under `/api/v1/auth/**` (except register/login/refresh/logout) require a valid `Authorization: Bearer <token>`.

### Trains — `TrainController`

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/v1/trains` | Body: `CreateTrainRequest` (validated). Returns `TrainSearchResponse`. |
| `GET` | `/api/v1/trains` | Paginated (`?page=&size=&sort=`, default size 20). Returns `Page<TrainSearchResponse>`. |
| `GET` | `/api/v1/trains/search?q=` | Free-text search, `@NotBlank @Size(max=100)`. |
| `GET` | `/api/v1/trains/{trainNumber}` | Full route/schedule detail. |
| `GET` | `/api/v1/trains/{trainNumber}/intelligence` | "Train Intelligence" — route complexity, uniqueness, expressness, night/day travel split, longest non-stop segment, average halt duration, journey efficiency, `isCircularRoute`, possibly-skipped stations. Backed by the shared network graph, so this is a heavier call than plain train details. |
| `GET` | `/api/v1/trains/{trainNumber}/compare/{otherTrainNumber}` | "Route Analytics" — shared stations, similarity %, longest common contiguous segment, divergence/convergence points, reverse-route detection. |

Example:

```bash
curl -X POST http://localhost:8080/api/v1/trains \
  -H "Content-Type: application/json" \
  -d '{"trainNumber":"12345","trainName":"Example Express"}'

curl http://localhost:8080/api/v1/trains/12345/intelligence
```

### Stations — `StationController`

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/v1/stations` | Body: `CreateStationRequest` (validated). Returns `StationSearchResponse`. |
| `GET` | `/api/v1/stations` | Paginated, same shape as `GET /api/v1/trains`. |
| `GET` | `/api/v1/stations/search?q=` | Free-text search. |
| `GET` | `/api/v1/stations/{stationCode}` | Station detail. Station code is uppercased before lookup. |
| `GET` | `/api/v1/stations/{stationCode}/intelligence` | "Station Intelligence" — network rank, connectivity score, origin/destination/transit %, average halt, average speed through the station, hourly departure/arrival histograms, importance score. |

### Schedules — `ScheduleController`

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/v1/schedules` | Body: `ScheduleRequest` (validated). Adds one stop to an existing train's route; evicts that train's and station's detail caches. |

### Journeys — `JourneyController`

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/v1/journeys?from=&to=` | All trains running from one station to another, fastest first. Each result now includes "Journey Analysis" for that leg: moving minutes, halted minutes, number of halts, longest halt, average moving speed, night/day travel percentage. |

```bash
curl "http://localhost:8080/api/v1/journeys?from=LTT&to=PPTA"
```

### Railway Network — `NetworkController`

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/v1/network/stats` | Whole-network graph metrics — betweenness/closeness centrality (Brandes' algorithm), connected components, network diameter. Public, unauthenticated, heavier computation backed by `NETWORK_CACHE`. |

### Statistics — `StatsController`

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/v1/stats` | Aggregate dataset statistics (totals, longest/shortest route, busiest station). |
| `GET` | `/api/v1/stats/rankings` | Most/fewest halts, longest/shortest halt, most popular origin stations, most connected stations. |
| `GET` | `/api/v1/stats/fun-facts` | Longest/shortest station name, most common word in station names, alphabet coverage, train with the most unique stations, palindrome station codes. |
| `GET` | `/api/v1/stats/achievements` | Top-100 longest/fastest trains, mega routes (>3000km), super-express rankings, rare routes, hidden gems. |

### Smart Search — `SmartSearchController`

| Method | Path | Notes |
|---|---|---|
| `GET` | `/api/v1/search/smart?q=` | Structured, natural-language-ish queries against a fixed grammar (e.g. "trains that stop at both X and Y", "trains longer than N hours", "trains with more than N halts"). An unrecognized query returns `200` with `recognized: false` rather than a 4xx — a free-text field failing to parse is an expected outcome, not a client error. |

### Authentication — `AuthController`

| Method | Path | Auth required? | Notes |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | No | Body: `RegisterRequest` (validated username/email/password rules). |
| `POST` | `/api/v1/auth/login` | No | Body: `LoginRequest`. |
| `POST` | `/api/v1/auth/refresh` | No (uses refresh token) | Rotates the refresh token — the old one is revoked. |
| `POST` | `/api/v1/auth/logout` | No (uses refresh token) | Revokes the given refresh token. |
| `GET` | `/api/v1/auth/me` | Yes | Current user profile. |
| `PUT` | `/api/v1/auth/password` | Yes | Change password. |
| `DELETE` | `/api/v1/auth/me` | Yes | Permanently deletes the account; requires the current password in the body even though the request is already token-authenticated. |

### Admin — `AdminController` / `RailwayDataImportController`

All under `/api/v1/admin/**`, requiring the `X-Admin-Key` header.

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/v1/admin/import` | Runs the CSV bulk import synchronously (blocks until complete) and returns `ImportResult` (row counts, success flag). Destructive per-train: existing schedule rows for any train encountered in the file are deleted and replaced. |
| `GET` | `/api/v1/admin/stats` | Operational stats to verify an import (distinct from the public `/api/v1/stats`). |
| `GET` | `/api/v1/admin/health` | "Dataset Health" diagnostics — duplicate schedule rows, missing timings, distance inconsistencies, impossible speeds, halt anomalies, orphan stations, invalid routes. |
| `POST` | `/api/v1/admin/cache/clear` | Manually evicts every named cache. |

## Authentication

RailLens uses a hand-rolled JWT scheme rather than Spring Security's filter-chain framework — a deliberate choice (see `pom.xml`'s dependency comments) to avoid two competing security paradigms alongside the project's existing interceptor pattern (`AdminApiKeyInterceptor`, `RateLimitInterceptor`). `spring-security-crypto` is used standalone, only for its industry-reviewed BCrypt implementation.

- **Access tokens** are short-lived bearer JWTs (default 60 minutes, `raillens.jwt.expiration-minutes`), issued on register/login and validated per-request by `JwtAuthInterceptor` against `/api/v1/auth/**` (except register/login/refresh/logout, which don't require one).
- **Refresh tokens** are longer-lived (default 30 days, `raillens.jwt.refresh-expiration-days`), stored server-side via `RefreshTokenService`/`RefreshTokenRepository`, and single-use: exchanging one at `POST /api/v1/auth/refresh` revokes it and issues a new one.
- **Admin routes** (`/api/v1/admin/**`) use a separate mechanism — a static shared secret in the `X-Admin-Key` header (`AdminApiKeyInterceptor`), not a user JWT.
- **Rate limiting** — `RateLimitInterceptor` applies to all of `/api/**` (so admin routes can't be hammered around it), with a stricter `AuthRateLimitInterceptor` layered specifically on `/api/v1/auth/**` to slow down credential-stuffing/registration abuse.
- Trains/stations/schedules write endpoints (`POST /api/v1/trains`, etc.) remain unauthenticated pending a separate decision — this is a known, deliberate gap, not an oversight.

## Caching Strategy

In-process caching via Caffeine (`CacheConfig`), chosen over Redis specifically to avoid adding new infrastructure for a single-instance, cost-conscious deployment. Each named cache is capped at 5,000 entries with a 15-minute `expireAfterWrite` TTL as a safety net; the primary invalidation strategy is explicit eviction on writes (e.g. `ScheduleService` evicts the affected train/station on a new stop, cache-changing imports evict on completion).

| Cache | Backs | Key shape |
|---|---|---|
| `trainDetails` | `TrainService#getTrainDetails` | per train number |
| `stationDetails` | `StationService#getStation` | per station code |
| `stats` | `StatsService#getStats` | single entry |
| `searchIndex` | Fuzzy-search fallback + `TrainSummaryIndex` (Smart Search) | small number of whole-table candidate lists |
| `railwayNetwork` | `RailwayNetworkService#buildSnapshot` | single entry (most expensive computation in the app) |
| `rankings` | `RankingsService#getRankings` | single entry |
| `funStats` | `FunStatsService#getFunStats` | single entry |
| `achievements` | `AchievementsService#getAchievements` | single entry |

`POST /api/v1/admin/cache/clear` evicts all eight caches manually.

## Testing

```bash
mvn test
```

Tests use JUnit 5, Mockito, and AssertJ, and are organized to mirror `src/main/java`. Coverage includes:

- Core services: `TrainService`, `StationService`, `JourneyService`, `ScheduleService`, `RailwayDataImportService`, `AdminService`, `AuthService`, `RefreshTokenService`, `JwtService`.
- Railway Intelligence services: `TrainIntelligenceService`, `StationIntelligenceService`, `RouteAnalyticsService`, `RailwayNetworkService` (under `service/network/`), `RankingsService`, `FunStatsService`, `AchievementsService`, `DatasetHealthService`, `SmartSearchQueryParser`, `SmartSearchService`.
- Shared utilities: `JourneyDayCalculator`, `FuzzyMatch`.
- Cross-cutting: `GlobalExceptionHandler`, interceptors (`RateLimitInterceptor`, `AuthRateLimitInterceptor`, `AdminApiKeyInterceptor`, `JwtAuthInterceptor`), `RequestIdFilter`.

## Known Gaps / Explicitly Out of Scope

- **No live train data.** RailLens works from a static, periodically-imported CSV dataset. Live running status, PNR status, seat availability, platform information, coach position, and delay information all require third-party/government real-time APIs that this project has deliberately chosen not to integrate — these were considered and explicitly removed from the product direction, not simply "not built yet."
- **Schema-integrity migration drafted but not applied.** `V2__enforce_schedule_integrity_constraints.sql` (see Database above) would close the schema review's `NOT NULL`/`UNIQUE` gaps on `train_schedule`, but is deliberately gated behind `spring.flyway.target=1` pending a live-database review of its pre-flight diagnostics.
- **Trains/stations/schedules write endpoints are unauthenticated.** Only `/api/v1/admin/**` (API key) and `/api/v1/auth/**` (JWT) currently enforce authentication.
- **The bulk CSV import runs as one large synchronous, unbatched transaction.** This is a known, documented risk for very large datasets and is intentionally left unchanged pending a database environment to validate a batching fix against — see the current backend architecture review for detail.

## License

This project is intended for learning and personal development.
