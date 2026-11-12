# RailLens Backend

> **Going live?** Follow [`DEPLOYMENT.md`](DEPLOYMENT.md): one step-by-step guide for the backend, website, Android app and RailOps, with a tracker of what's merged vs. live.

RailLens Backend is a Spring Boot REST API that powers the RailLens railway information system. Beyond core train/station/journey browsing, it exposes a full "Railway Intelligence" suite (train/station scoring, route comparison, network graph metrics, rankings, fun stats, achievements, dataset health), a hand-rolled JWT authentication system, and an admin panel — all designed as a stable REST contract for the Next.js web frontend today and for Android/iOS clients using the same API in the future.

## Tech Stack

* Java 26 (see `pom.xml` — `<java.version>`)
* Spring Boot 4.1.0 (Spring Framework 7)
* Spring Data JPA / Hibernate
* PostgreSQL
* Maven
* Caffeine (in-process caching, via `spring-boot-starter-cache` + `com.github.ben-manes.caffeine`)
* JWT authentication (`io.jsonwebtoken` / jjwt 0.13.0) on the Spring Security filter chain, deny-by-default; BCrypt passwords; refresh-token families with reuse detection (see "Authentication" below)
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
| `raillens.mfa.encryption-key` | Base64 of 32 random bytes (`openssl rand -base64 32`): encrypts admins' authenticator secrets at rest. Required for admin sign-in. Changing it invalidates every enrolled authenticator. | — |
| `raillens.admin.bootstrap-usernames` | Comma-separated existing accounts promoted to ADMIN at startup (never demotes). How the first admin is created. | — |
| `raillens.admin.legacy-key-enabled` | Whether the old shared `X-Admin-Key` is still accepted (logged as a warning). Set `false` once the web and mobile admin screens use admin accounts. | `true` |
| `raillens.admin.api-key` | LEGACY shared secret for the `X-Admin-Key` header, while `legacy-key-enabled=true`. Blank rejects the header rather than leaving routes open. | — |
| `raillens.jwt.admin-expiration-minutes` / `raillens.auth.mfa-session-hours` | Admin access-token lifetime / absolute admin session lifetime (refresh does not extend it) | `15` / `12` |
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

All endpoints are under `/api/v1/`. Every route under `/api/**` is subject to `RateLimitInterceptor`; routes under `/api/v1/admin/**` additionally require an `X-Admin-Key` header, as do **writes** (POST/PUT/PATCH/DELETE) to `/api/v1/trains`, `/api/v1/stations` and `/api/v1/schedules` (reads stay public), and routes under `/api/v1/auth/**` (except register/login/refresh/logout) require a valid `Authorization: Bearer <token>`.

### Trains — `TrainController`

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/v1/trains` | **Requires `X-Admin-Key`.** Body: `CreateTrainRequest` (validated). Returns `TrainSearchResponse`. |
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
| `POST` | `/api/v1/stations` | **Requires `X-Admin-Key`.** Body: `CreateStationRequest` (validated). Returns `StationSearchResponse`. |
| `GET` | `/api/v1/stations` | Paginated, same shape as `GET /api/v1/trains`. |
| `GET` | `/api/v1/stations/search?q=` | Free-text search. |
| `GET` | `/api/v1/stations/{stationCode}` | Station detail. Station code is uppercased before lookup. |
| `GET` | `/api/v1/stations/{stationCode}/intelligence` | "Station Intelligence" — network rank, connectivity score, origin/destination/transit %, average halt, average speed through the station, hourly departure/arrival histograms, importance score. |

### Schedules — `ScheduleController`

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/v1/schedules` | **Requires `X-Admin-Key`.** Body: `ScheduleRequest` (validated). Adds one stop to an existing train's route; evicts that train's and station's detail caches. |

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

RailLens uses the **Spring Security filter chain** (`config/SecurityConfig`) with **deny-by-default** authorization: every allowed route is listed explicitly and anything else is refused by `anyRequest().denyAll()`. Until Phase 1 (2026-11) auth was done with per-path MVC interceptors, which are allow-by-default: an endpoint was public unless someone remembered to add its path. That is how `POST /api/v1/trains`, `/stations` and `/schedules` ended up writable by anyone (fixed in Phase 0). Tokens and endpoints are unchanged for clients.

| Route | Who |
|---|---|
| `GET` trains, stations, journeys, network, search, stats; `/actuator/health`, `/actuator/info`; Swagger | everyone |
| `POST /api/v1/auth/register`, `login`, `refresh`, `logout`, `mfa/enrol`, `mfa/confirm` | everyone (the MFA calls carry a 5-minute enrolment token in the body) |
| other `/api/v1/auth/**` (`me`, `password`) | `Authorization: Bearer <access token>` |
| `/api/v1/admin/**`, and writes to trains/stations/schedules | admin access token (password + one-time code); legacy `X-Admin-Key` while enabled |
| anything else | nobody (401) |

- **Access tokens** are short-lived bearer JWTs (default 60 minutes, `raillens.jwt.expiration-minutes`), checked by `JwtAuthenticationFilter`. A bad token on a public read is ignored, as before.
- **Refresh tokens** (default 30 days, `raillens.jwt.refresh-expiration-days`) are stored hashed and are single-use. Each login starts a **token family**; `POST /api/v1/auth/refresh` locks the row, rotates it, and issues the successor in the same family. **Reuse detection:** presenting an already-rotated token (outside a 10 s grace window for two tabs refreshing at once, `raillens.auth.refresh-reuse-grace-seconds`) means a copy was stolen, so the whole family is revoked and the user must log in again. Logout revokes the family. Rotated tokens are kept until they expire as evidence for this check (the cleanup job deletes only expired and logged-out tokens).
- **Admin accounts with MFA** (Phase 2A, migration V7). `users.role` is `USER` or `ADMIN`. An admin signs in with their password **and** a 6-digit code from an authenticator app (RFC 6238 TOTP; Google/Microsoft Authenticator, Authy, 1Password…):
  1. First sign-in (no MFA yet): `POST /auth/login` returns `mfaEnrolmentRequired: true`, no session, and an `enrolmentToken` (5 minutes, usable only for the two calls below).
  2. `POST /auth/mfa/enrol {enrolmentToken}` returns `secret` and `otpauthUri` (show as a QR code).
  3. `POST /auth/mfa/confirm {enrolmentToken, otp}` turns MFA on and returns an admin session.
  4. Afterwards, `POST /auth/login {usernameOrEmail, password, otp}`. Without `otp` the answer is 401 with `"code": "MFA_REQUIRED"`; a wrong or reused code is 401 `INVALID_OTP` and counts toward lockout like a wrong password.

  Each code works once (`users.mfa_last_step`, claimed with a conditional UPDATE so two simultaneous logins can't both use it). Secrets are stored AES-256-GCM encrypted (`MfaSecretCipher`, bound to the user id). Admin access tokens last 15 minutes and carry `role=ADMIN` + `mfa=true`; only such a token gets `ROLE_ADMIN`. Refresh re-reads the role from the database (demotion takes effect on the next refresh, at most 15 minutes) and takes the MFA flag from the session, never the client; an admin session ends 12 hours after sign-in however often it refreshes. A user promoted while signed in must sign in again to get admin rights.
  - **First admin:** register the account normally, set `raillens.admin.bootstrap-usernames=<name>` and `raillens.mfa.encryption-key`, restart, then sign in to enrol. **Demote:** `UPDATE users SET role='USER' WHERE username='…'`. **Lost phone:** `UPDATE users SET mfa_enabled=false, mfa_secret=NULL WHERE username='…'`, then the admin enrols again at next sign-in. Run these only with the owner's approval.
- **RailOps login (single login, Phase 4B):** when `raillens.railops.jwks-url` and `raillens.railops.issuer` are set, RailOps access tokens are accepted too. They are RS256, checked against RailOps' published keys, issuer, audience `railops-api` and expiry; RailLens' own HS256 secret is never shared. A RailOps login reaches a RailLens account **only through `users.railops_user_id`** (set when accounts are moved to RailOps). A new email gets a new profile without a RailLens password. An email that already belongs to an unlinked RailLens account is **refused, never merged**, because neither app verifies email addresses. ROLE_ADMIN comes only from a RailOps nationwide admin (`ADMIN:GLOBAL:*`) who signed in with a one-time code. Tested in `RailOpsTokensTest`.
- **Moving accounts to RailOps (Phase 4C):** RailOps' identity service pulls `GET /api/v1/admin/railops/accounts` (accounts with a RailLens password and no link yet, including the bcrypt hash, sent with `Cache-Control: no-store`) and sends back `POST /api/v1/admin/railops/links`. A link is stored once, never re-pointed, and a RailOps id can't be shared by two accounts. Both endpoints need an admin who signed in with a one-time code: the legacy `X-Admin-Key` is refused there (403 `MFA_ADMIN_REQUIRED`), because the export contains password hashes. Tested in `RailOpsAccountMoveTest`.
- **Switch-over (Phase 4D):** a profile linked to a RailOps login has its password in RailOps. `PUT /api/v1/auth/password` and `DELETE /api/v1/auth/me` called with a RailOps token are forwarded to RailOps (`raillens.railops.api-url`, default the issuer) with that same token; RailOps checks the password and counts wrong guesses toward its lockout. On success RailLens clears its own copy of the password (change) or deletes the profile (close); if RailOps refuses, nothing changes locally (one transaction, local write flushed first). Called with a RailLens token instead, both answer 409 `MANAGED_BY_RAILOPS`. A profile with no local password can't sign in with RailLens' own login. Tested in `RailOpsAccountChangesTest`.
- **Legacy shared key:** `X-Admin-Key` (`AdminKeyAuthenticationFilter`, constant-time compare) still grants admin while `raillens.admin.legacy-key-enabled=true`, logging a warning per use, so the deployed web and mobile apps keep working during the switch. A wrong key is rejected with 401 even on public routes. With the flag `false`, the header is refused with "X-Admin-Key is no longer accepted - sign in with an admin account".
- **Unknown usernames** are checked against a dummy BCrypt hash, so login takes as long as for a real account and timing doesn't reveal which usernames exist.
- **Errors** keep the `{timestamp,status,error}` shape and messages the web and mobile apps display (`JsonAuthErrorHandler`). Errors a client must react to also carry a stable `code` (`MFA_REQUIRED`, `INVALID_OTP`, `INVALID_ENROLMENT_TOKEN`, `MFA_ALREADY_ENABLED`, `MFA_ENROLMENT_NOT_STARTED`, `MFA_NOT_CONFIGURED`); it is omitted elsewhere, so existing error bodies are unchanged.
- **CORS** is configured by the `UrlBasedCorsConfigurationSource` bean in `SecurityConfig` (Spring Security only applies CORS when that bean exists).
- **Security headers** come only from `SecurityHeadersFilter`; Spring Security's header defaults are disabled so public reads don't get `Cache-Control: no-cache`.
- **Rate limiting** — `RateLimitInterceptor` applies to all of `/api/**`, with a stricter `AuthRateLimitInterceptor` on `/api/v1/auth/**`. Buckets are keyed by the real client IP from `ClientIpResolver`: forwarding headers are believed only from trusted proxies, `X-Forwarded-For` is walked right to left (Render appends to it, so its leftmost entry is client-controlled), and on Render the Cloudflare-set `True-Client-IP` is used. Sign-ins from the website go through its own server (Phase 2B), which passes the visitor's IP in `X-RailLens-Client-IP`; that header counts only together with `X-RailLens-Proxy-Secret` matching `raillens.client-ip.proxy-secret`.
- **Tests that hold the line:** `EndpointAuthorizationTest` enumerates every registered write endpoint and fails the build if any accepts an anonymous caller; `SecurityChainTest` covers credentials, error messages, CORS and headers; `RefreshTokenFamilyTest` covers reuse detection, the row lock, cleanup and the MFA session cap on real PostgreSQL; `AdminMfaFlowTest` runs enrolment, code replay, refresh, demotion and the disabled legacy key end to end; `TotpTest` checks the RFC 6238 vectors.

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
- Cross-cutting: `GlobalExceptionHandler`, rate-limit interceptors (`RateLimitInterceptor`, `AuthRateLimitInterceptor`), `ClientIpResolver`, `RequestIdFilter`, `SecurityHeadersFilter`.
- Security (`security/`): `SecurityChainTest`, `EndpointAuthorizationTest`, `RefreshTokenFamilyTest`, `AdminMfaFlowTest`, `TotpTest`, `MfaSecretCipherTest`. The ones that boot the app need a local PostgreSQL.

## Known Gaps / Explicitly Out of Scope

- **No live train data.** RailLens works from a static, periodically-imported CSV dataset. Live running status, PNR status, seat availability, platform information, coach position, and delay information all require third-party/government real-time APIs that this project has deliberately chosen not to integrate — these were considered and explicitly removed from the product direction, not simply "not built yet."
- **Schema-integrity migration drafted but not applied.** `V2__enforce_schedule_integrity_constraints.sql` (see Database above) would close the schema review's `NOT NULL`/`UNIQUE` gaps on `train_schedule`, but is deliberately gated behind `spring.flyway.target=1` pending a live-database review of its pre-flight diagnostics.
- **The bulk CSV import runs as one large synchronous, unbatched transaction.** This is a known, documented risk for very large datasets and is intentionally left unchanged pending a database environment to validate a batching fix against — see the current backend architecture review for detail.

## License

This project is intended for learning and personal development.
