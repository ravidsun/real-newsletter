# Real Newsletter

An AI-powered news aggregation and delivery platform built with **Spring Boot 4**, **Spring AI 2 (OpenAI GPT-4)**, **Spring Security (JWT)**, and **PostgreSQL (Supabase)**. It periodically fetches the latest articles from [Newsdata.io](https://newsdata.io) and the [NewsAPI](https://newsapi.org), enriches them with AI-generated summaries and tags, persists them, and delivers them to clients via a paginated REST API and a real-time Server-Sent Events (SSE) stream — all behind a hardened security layer.

---

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Prerequisites](#prerequisites)
- [Configuration](#configuration)
- [Running Locally](#running-locally)
  - [With Maven](#with-maven)
  - [With Docker Compose](#with-docker-compose)
- [Deployment to VPS](#deployment-to-vps)
  - [Architecture](#architecture-1)
  - [Port Configuration](#port-configuration)
  - [GitHub Actions Workflows](#github-actions-workflows)
  - [Required Secrets & Variables](#required-github-secrets)
  - [VPS Firewall](#vps-firewall-configuration)
  - [Accessing Deployed Apps](#accessing-the-deployed-applications)
  - [Database Setup](#database-setup-on-vps)
  - [VPS Setup](#one-time-vps-setup)
  - [Verifying Deployments](#verifying-deployments)
- [Authentication](#authentication)
  - [POST /api/auth/login](#post-apiauthlogin)
  - [POST /api/auth/refresh](#post-apiauthrefresh)
  - [POST /api/auth/logout](#post-apiauthlogout)
- [API Reference](#api-reference)
  - [GET /api/v1/articles](#get-apiv1articles)
  - [GET /api/v1/articles/archived](#get-apiv1articlesarchived)
  - [POST /api/v1/articles](#post-apiv1articles)
  - [PUT /api/v1/articles/{id}](#put-apiv1articlesid)
  - [DELETE /api/v1/articles/{id}](#delete-apiv1articlesid)
  - [GET /api/v1/articles/stream](#get-apiv1articlesstream)
  - [POST /api/v1/ingestion](#post-apiv1ingestion)
  - [GET /api/v1/search](#get-apiv1search)
- [Real-Time SSE Streaming](#real-time-sse-streaming)
- [Article Lifecycle](#article-lifecycle)
- [Security Model](#security-model)
- [Database Schema](#database-schema)
- [CORS Configuration](#cors-configuration)
- [Running Tests](#running-tests)
- [Project Structure](#project-structure)
- [Branching Strategy](#branching-strategy)
- [Release History](#release-history)

---

## Features

| Feature | Description |
|---|---|
| **Dual News Ingestion** | Scheduled jobs fetch the latest English/US news from both [Newsdata.io](https://newsdata.io) and [NewsAPI](https://newsapi.org) on configurable intervals. Duplicate articles (by URL) are automatically skipped. |
| **Manual Ingestion** | `POST /api/v1/ingestion?source=newsdata\|newsapi\|all` triggers an immediate fetch and returns `IngestionResult` stats (`fetched`, `saved`, `skipped`, `errors`). |
| **AI Enrichment** | Each new article is sent to OpenAI GPT-4 via Spring AI to generate a plain-text summary and a set of comma-separated tags. |
| **Keyword Search** | `GET /api/v1/search?query=...` performs full-text keyword search across title and content, with optional `category` and `dateRange` filters. Rate-limited to prevent abuse. |
| **Article Lifecycle** | Articles progress through `DRAFT → PUBLISHED → DISABLED / ARCHIVED` states. Admins can disable or delete articles via REST. Articles older than 7 days are archived automatically. |
| **Admin Portal API** | State-changing endpoints (`POST`, `PUT`, `DELETE` on `/api/v1/articles`) require the `ADMIN` role and are protected by `@PreAuthorize`. |
| **JWT Authentication** | Short-lived access tokens (15 min) issued on login. Long-lived refresh tokens stored in `HttpOnly; Secure; SameSite=Strict` cookies with automatic token rotation. |
| **Rate Limiting (Bucket4j)** | `/api/auth/**` and `/api/v1/search` are protected by a token-bucket rate limiter; excess requests receive `HTTP 429`. |
| **XSS Sanitization** | All rich-text HTML fields (`title`, `description`, `content`) are sanitized through OWASP Java HTML Sanitizer before persistence. |
| **RBAC** | `@PreAuthorize("hasRole('ADMIN')")` guards all state-changing article endpoints. Non-admin users receive `HTTP 403`. |
| **Paginated REST API** | `GET /api/v1/articles` returns stored PUBLISHED articles as a paginated JSON response, sorted newest-first, with optional `country`, `language`, and `category` filters. |
| **Archived Feed** | `GET /api/v1/articles/archived` returns articles that have been moved to `ARCHIVED` status (older than 7 days), supporting the same filters as the main feed. |
| **Real-Time SSE Stream** | `GET /api/v1/articles/stream` opens a persistent Server-Sent Events connection. Every new article saved triggers a `new-article` event pushed to all connected clients instantly. |
| **Profile-Based CORS** | Separate CORS policies for `development` (all origins allowed) and `production` (restricted to a configured frontend origin). |
| **Profile-Driven Config** | `application-{profile}.yml` files own all environment-specific settings (Flyway, connection pool, rate limits, logging). No hardcoded defaults in `@Value` annotations. |
| **Database Migrations** | Flyway manages schema evolution automatically on startup with per-profile settings. |
| **Daily Archiving Scheduler** | Runs at midnight every day; automatically transitions PUBLISHED articles older than 7 days to ARCHIVED status. |
| **Keep-Alive Scheduler** | A background job periodically pings the database to prevent idle connection drops on Supabase's connection pooler. |
| **Actuator & OpenAPI** | Spring Boot Actuator endpoints for health and metrics; auto-generated Swagger UI at `/swagger-ui.html`. |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│              Schedulers (configurable intervals)                      │
│                                                                       │
│   ┌──────────────────────┐   ┌──────────────────────┐               │
│   │ NewsApiIngestion      │   │ NewsDataIngestion     │               │
│   │ Scheduler            │   │ Scheduler             │               │
│   └──────────┬───────────┘   └──────────┬────────────┘               │
│              └────────────┬─────────────┘                             │
│                           ▼                                           │
│                  IngestionScheduler                                   │
│              1. Fetch from news source                                │
│              2. Deduplicate by URL                                    │
│              3. AI-enrich (summary + tags via GPT-4)                 │
│              4. Sanitize HTML fields (OWASP Sanitizer)               │
│              5. Save to DB (status = PUBLISHED)                       │
│              6. Publish NewArticleEvent                               │
│                     │                  │                              │
│         ┌───────────▼──┐  ┌────────────▼──────────┐                  │
│         │  PostgreSQL   │  │  ArticleStreamService  │                  │
│         │  (Supabase)   │  │  (SSE broadcaster)     │                  │
│         └───────────────┘  └────────────┬───────────┘                  │
│                                         │ push event                  │
│                                ┌────────▼────────┐                    │
│                                │  SSE Clients    │                    │
│                                └─────────────────┘                    │
│                                                                       │
│  ArticleArchivingScheduler ──► bulk PUBLISHED→ARCHIVED (daily)       │
│                                                                       │
│  REST Clients                                                         │
│    Public:  GET /api/v1/articles ──────────► ArticleController       │
│    Public:  GET /api/v1/articles/archived ─► ArticleController       │
│    Public:  GET /api/v1/search ────────────► SearchController        │
│    Admin:   POST/PUT/DELETE /api/v1/articles ► ArticleController     │
│    Auth:    POST /api/auth/** ─────────────► AuthController          │
│                                                                       │
│  Spring Security / JwtAuthenticationFilter (all requests)            │
│  Bucket4j Rate Limiter (/api/auth/**, /api/v1/search)                │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0.0 |
| Security | Spring Security 6 + JJWT (JWT), Bucket4j (rate limiting) |
| AI Integration | Spring AI 2.0.0-M2 (OpenAI GPT-4) |
| HTTP Client | Spring `RestClient` (synchronous) |
| Persistence | Spring Data JPA + Hibernate |
| Database | PostgreSQL via Supabase (production), H2 (tests) |
| Migrations | Flyway |
| Real-Time | Server-Sent Events (`SseEmitter`) |
| Input Sanitization | OWASP Java HTML Sanitizer |
| Build | Maven 3.9+ |
| Containerisation | Docker / Docker Compose |
| Testing | JUnit 5, Mockito, MockMvc |
| Coverage | JaCoCo (≥ 71% line coverage) |
| API Docs | SpringDoc OpenAPI 3 |

---

## Prerequisites

- **Java 21+**
- **Maven 3.9+**
- **Docker & Docker Compose** (optional, for containerised run)
- A **[Supabase](https://supabase.com)** project (free tier works) — provides the PostgreSQL database
- A **[Newsdata.io](https://newsdata.io)** API key — news feed (free tier: 200 credits/day)
- A **[NewsAPI](https://newsapi.org)** API key — news feed (free tier available)
- An **[OpenAI](https://platform.openai.com)** API key (for GPT-4 AI enrichment)

---

## Configuration

All secrets are supplied through environment variables. Copy the template below into a `.env` file at the project root (never commit this file):

```bash
# .env — fill in your values before running

# Application
SERVER_PORT=8081

# Database
DB_URL=jdbc:postgresql://db.<project-ref>.supabase.co:6543/postgres?sslmode=require
DB_USER=postgres
DB_PASSWORD=your-supabase-password

# News APIs
NEWSDATA_API_URL=https://newsdata.io/api/1/news
NEWSDATA_API_KEY=pub_your-newsdata-key

NEWS_API_URL=https://newsapi.org/v2/everything
NEWS_API_KEY=your-newsapi-key

# AI Enrichment
OPENAI_API_KEY=sk-...

# JWT — change these in production!
JWT_SECRET=your-256-bit-base64-encoded-secret
JWT_ACCESS_TOKEN_TTL_MINUTES=15
JWT_REFRESH_TOKEN_TTL_DAYS=7

# CORS — production frontend origin
CORS_ALLOWED_ORIGIN=https://frontend.example.com
```

### Full `application.yml` reference

| Property | Default | Description |
|---|---|---|
| `server.port` | `8081` | HTTP server port (override with `$SERVER_PORT`; dev uses 8081, prod uses 9000 on VPS) |
| `spring.datasource.url` | *(required)* `$DB_URL` | PostgreSQL JDBC connection string |
| `spring.datasource.username` | *(required)* `$DB_USER` | Database username |
| `spring.datasource.password` | *(required)* `$DB_PASSWORD` | Database password |
| `spring.ai.openai.api-key` | `$OPENAI_API_KEY` | OpenAI API key for GPT-4 enrichment |
| `spring.ai.openai.chat.options.model` | `gpt-4` | OpenAI model name |
| `external.news.api.url` | `$NEWSDATA_API_URL` | Newsdata.io articles endpoint |
| `external.news.api.key` | `$NEWSDATA_API_KEY` | Newsdata.io API key |
| `news.api.url` | `$NEWS_API_URL` | NewsAPI endpoint |
| `news.api.key` | `$NEWS_API_KEY` | NewsAPI key |
| `ingestion.interval.ms` | `600000` (10 min) | How often the ingestion jobs run (milliseconds) |
| `jwt.secret` | `$JWT_SECRET` | HS256 secret key (base64-encoded, ≥ 32 bytes) |
| `jwt.access-token-ttl-minutes` | `15` | Access token lifetime in minutes |
| `jwt.refresh-token-ttl-days` | `7` | Refresh token lifetime in days |
| `jwt.refresh-cookie-name` | `refresh_token` | Name of the HttpOnly refresh-token cookie |
| `bucket4j-rate-limit.enabled` | `true` | Enable/disable Bucket4j rate limiting |
| `bucket4j-rate-limit.capacity` | `20` | Token bucket burst capacity |
| `bucket4j-rate-limit.refill-tokens` | `20` | Tokens refilled per window |
| `bucket4j-rate-limit.refill-period-seconds` | `60` | Refill window in seconds |

---

## Running Locally

### With Maven

```bash
# 1. Clone the repository
git clone https://github.com/ravidsun/real-newsletter.git
cd real-newsletter
```bash
# 2. Export environment variables (or create a .env file and source it)
export DB_URL="jdbc:postgresql://..."
export DB_USER="postgres"
export DB_PASSWORD="your-password"
export NEWSDATA_API_URL="https://newsdata.io/api/1/news"
export NEWSDATA_API_KEY="pub_your-newsdata-key"
export NEWS_API_URL="https://newsapi.org/v2/everything"
export NEWS_API_KEY="your-newsapi-key"
export OPENAI_API_KEY="sk-..."
export JWT_SECRET="your-base64-secret"

# 3. Run the application
mvn spring-boot:run

# The app starts on http://localhost:8081 (configurable via SERVER_PORT env var)
```

To run on a different port locally:

```bash
export SERVER_PORT=3000
mvn spring-boot:run
# App runs on http://localhost:3000
```

To run with a specific Spring profile (e.g. `development` for permissive CORS):

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=development
```

### With Docker Compose

```bash
# 1. Create your .env file (see Configuration section above)
cp .env.example .env   # edit with your real values

# 2. Build and start
docker compose up --build

# The app is available at http://localhost:8081
```

The Docker build uses **layer caching** — Maven dependencies are downloaded in a dedicated layer keyed on `pom.xml` only, so source-only changes rebuild the JAR without re-downloading all dependencies.

To run with a different port locally:

```bash
export SERVER_PORT=3000
export DB_URL="..." DB_PASSWORD="..." 
docker compose up
# http://localhost:3000
```

To stop:

```bash
docker compose down
```

---

## Authentication

The API uses **JWT-based authentication** with short-lived access tokens and HttpOnly refresh token cookies.

### Token Flow

```
Client                                        Server
  │                                              │
  │  POST /api/auth/login {username, password}   │
  ├─────────────────────────────────────────────►│
  │                                              │  Verify credentials
  │  200 { accessToken, tokenType, expiresIn }   │  Issue 15-min access token
  │◄─────────────────────────────────────────────┤  Set HttpOnly refresh cookie
  │                                              │
  │  GET /api/v1/articles                        │
  │  Authorization: Bearer <accessToken>         │
  ├─────────────────────────────────────────────►│
  │  200 { articles... }                         │
  │◄─────────────────────────────────────────────┤
  │                                              │
  │  POST /api/auth/refresh (cookie sent auto)   │  Rotate refresh token
  ├─────────────────────────────────────────────►│  Issue new access token
  │  200 { accessToken, tokenType, expiresIn }   │  Set new HttpOnly cookie
  │◄─────────────────────────────────────────────┤
```

All access tokens are **Bearer tokens** included in the `Authorization` header. Refresh tokens travel exclusively in `HttpOnly; Secure; SameSite=Strict` cookies, making them invisible to JavaScript and immune to XSS theft.

---

### `POST /api/auth/login`

Authenticates a user and returns a short-lived access token + sets a refresh token cookie.

**Request Body**

```json
{ "username": "admin", "password": "secret" }
```

**Success Response (200)**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

The response also sets a `Set-Cookie` header:

```
Set-Cookie: refresh_token=<opaque-token>; Path=/api/auth; Max-Age=604800; HttpOnly; Secure; SameSite=Strict
```

**Error Responses**

| Status | Meaning |
|--------|---------|
| `401` | Invalid credentials |
| `429` | Rate limit exceeded |

---

### `POST /api/auth/refresh`

Exchanges a valid refresh token (from cookie) for a new access token. The old refresh token is **rotated** — it is invalidated and a new one is issued.

**Headers / Cookies**

The refresh token cookie is sent automatically by the browser. No request body required.

**Success Response (200)**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

**Error Responses**

| Status | Meaning |
|--------|---------|
| `401` | Missing, expired, or already-rotated refresh token |
| `429` | Rate limit exceeded |

---

### `POST /api/auth/logout`

Revokes the refresh token and clears the cookie.

**Success Response**

`204 No Content`

---

## API Reference

### `GET /api/v1/articles`

Returns a paginated list of **PUBLISHED** articles, sorted by `createdAt` descending (newest first) by default. DISABLED and ARCHIVED articles are excluded from this feed.

**Query Parameters**

| Parameter | Type | Default | Description |
|---|---|---|---|
| `page` | integer | `0` | Zero-based page number |
| `size` | integer | `20` | Number of articles per page |
| `sort` | string | `createdAt,desc` | Sort field and direction (e.g. `title,asc`) |
| `country` | string | — | Filter by ISO country code (e.g. `us`, `gb`) |
| `language` | string | — | Filter by ISO language code (e.g. `en`, `fr`) |
| `category` | string | — | Filter by category (e.g. `technology`, `sports`) |

**Example Request**

```bash
curl "http://localhost:8081/api/v1/articles?page=0&size=5&category=technology"
```

**Example Response**

```json
{
  "content": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "link": "https://example.com/article-1",
      "title": "Breaking: Major Tech Announcement",
      "content": "Full article content here...",
      "aiSummary": "A major technology company announced a groundbreaking product today...",
      "category": "technology",
      "country": "us",
      "language": "english",
      "status": "PUBLISHED",
      "pubDate": "2026-05-14T09:45:00Z",
      "createdAt": "2026-05-14T09:45:00Z"
    }
  ],
  "page": {
    "totalElements": 42,
    "totalPages": 9,
    "number": 0,
    "size": 5
  },
  "last": false,
  "first": true,
  "numberOfElements": 5
}
```

> **Note:** Spring Data 4 moved pagination metadata under the nested `page` key.

---

### `GET /api/v1/articles/archived`

Returns a paginated list of **ARCHIVED** articles (articles automatically archived after 7 days). Supports the same optional filters as the main feed.

**Query Parameters**

Same as `GET /api/v1/articles` — supports `page`, `size`, `sort`, `country`, `language`, `category`.

**Example Request**

```bash
curl "http://localhost:8080/api/v1/articles/archived?page=0&size=10"
```

---

### `POST /api/v1/articles`

Creates a new article. **Requires `ADMIN` role** (`Authorization: Bearer <adminToken>`).

Rich-text fields (`title`, `description`, `content`) are sanitized by OWASP Java HTML Sanitizer before persistence to prevent stored XSS.

**Request Body**

```json
{
  "link": "https://example.com/article",
  "title": "Article Title",
  "description": "Short description",
  "content": "Full article content...",
  "creator": "Author Name"
}
```

**Success Response**

`201 Created` with the saved `ArticleDto`.

**Error Responses**

| Status | Meaning |
|--------|---------|
| `400` | Validation failure (missing required fields) |
| `401` | Missing or invalid access token |
| `403` | Authenticated but not ADMIN |

---

### `PUT /api/v1/articles/{id}`

Updates the lifecycle status of an article. **Requires `ADMIN` role**.

**Path Parameters**

| Parameter | Type | Description |
|---|---|---|
| `id` | UUID | Article identifier |

**Request Body**

```json
{ "status": "DISABLED" }
```

Valid values: `DRAFT`, `PUBLISHED`, `DISABLED`, `ARCHIVED`

**Success Response**

`200 OK` with the updated `ArticleDto`.

**Error Responses**

| Status | Meaning |
|--------|---------|
| `401` | Missing or invalid access token |
| `403` | Authenticated but not ADMIN |
| `404` | Article not found |

---

### `DELETE /api/v1/articles/{id}`

Permanently deletes an article from the database. **Requires `ADMIN` role**.

**Success Response**

`204 No Content`

**Error Responses**

| Status | Meaning |
|--------|---------|
| `401` | Missing or invalid access token |
| `403` | Authenticated but not ADMIN |
| `404` | Article not found |

---

### `GET /api/v1/articles/stream`

Opens a persistent **Server-Sent Events** (SSE) connection. The server pushes a `new-article` event to this connection every time the ingestion job saves a new article.

**Response Headers**

```
Content-Type: text/event-stream
Cache-Control: no-cache
Connection: keep-alive
```

**Event Format**

```
id: 550e8400-e29b-41d4-a716-446655440000
event: new-article
data: {"id":"550e8400-...","link":"https://...","title":"...","aiSummary":"...","createdAt":"..."}
```

**Example — Browser (JavaScript)**

```js
const source = new EventSource('http://localhost:8081/api/v1/articles/stream');

source.addEventListener('new-article', (event) => {
  const article = JSON.parse(event.data);
  console.log('New article:', article.title);
});

source.onerror = () => console.error('SSE connection error');
```

---

### `POST /api/v1/ingestion`

Triggers an immediate news ingestion cycle and waits for it to complete before returning the result.

**Query Parameters**

| Parameter | Type | Default | Description |
|---|---|---|---|
| `source` | string | `all` | Which source to ingest: `newsdata`, `newsapi`, or `all` |

**Example Request**

```bash
curl -X POST "http://localhost:8081/api/v1/ingestion?source=all"
```

**Example Response**

```json
{
  "status": "completed",
  "source": "all",
  "triggeredAt": "2026-05-14T14:00:00Z",
  "newsdata": { "fetched": 10, "saved": 8, "skipped": 2, "errors": 0 },
  "newsapi":   { "fetched": 15, "saved": 12, "skipped": 3, "errors": 0 }
}
```

---

### `GET /api/v1/search`

Searches **PUBLISHED** articles by keyword across `title` and `content`, with optional `category` and `dateRange` filters. This endpoint is rate-limited — excess requests receive `HTTP 429`.

**Query Parameters**

| Parameter | Type | Required | Description |
|---|---|---|---|
| `query` | string | ✅ | Keyword to search (case-insensitive, matched against title and content) |
| `category` | string | — | Filter by category (e.g. `technology`) |
| `dateRange` | string | — | Date window: `last7days`, `last30days`, `last90days` |
| `page` | integer | — | Zero-based page number (default `0`) |
| `size` | integer | — | Results per page (default `20`) |
| `sort` | string | — | Sort field and direction (default `createdAt,desc`) |

**Example Requests**

```bash
# Simple keyword search
curl "http://localhost:8081/api/v1/search?query=artificial+intelligence"

# Search with category and date filter
curl "http://localhost:8081/api/v1/search?query=climate&category=science&dateRange=last30days"
```

**Example Response**

```json
{
  "content": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "link": "https://example.com/ai-article",
      "title": "Advances in Artificial Intelligence",
      "aiSummary": "Researchers have unveiled new breakthroughs in AI...",
      "category": "technology",
      "status": "PUBLISHED",
      "pubDate": "2026-05-10T08:00:00Z",
      "createdAt": "2026-05-10T08:00:00Z"
    }
  ],
  "page": {
    "totalElements": 7,
    "totalPages": 1,
    "number": 0,
    "size": 20
  }
}
```

**Error Responses**

| Status | Meaning |
|--------|---------|
| `400` | `query` parameter is missing or blank |
| `429` | Rate limit exceeded |

---

## Real-Time SSE Streaming

### How It Works

1. A client opens a connection to `GET /api/v1/articles/stream`.
2. The server registers an `SseEmitter` (infinite timeout) and keeps the HTTP connection open.
3. When a scheduled ingestion job saves a new article, it publishes a `NewArticleEvent` via Spring's `ApplicationEventPublisher`.
4. `ArticleStreamService` listens for the event and calls `emitter.send(...)` on every registered emitter.
5. Disconnected clients (detected via `IOException`) are automatically removed from the list.

### Smoke Test with `curl`

```bash
# Keep the connection open; new articles will stream in as they are ingested
curl -N http://localhost:8081/api/v1/articles/stream
```

You will see output like:

```
id: 550e8400-e29b-41d4-a716-446655440000
event: new-article
data: {"id":"550e8400-...","title":"Example Article",...}
```

---

## Article Lifecycle

Articles follow a well-defined lifecycle managed by admins and automated schedulers:

```
           Ingestion
               │
               ▼
           PUBLISHED  ◄──── Admin re-enables
               │
        ┌──────┴──────┐
        │             │
        ▼             ▼
    DISABLED      ARCHIVED
   (admin)      (auto, 7 days)
        │
        ▼
    DELETE (permanent)
```

| Status | Visible in `/api/v1/articles` | Visible in `/archived` | How it gets there |
|--------|:----:|:----:|---|
| `PUBLISHED` | ✅ | ❌ | Default on ingestion or admin action |
| `DISABLED` | ❌ | ❌ | Admin sets via `PUT /{id}` |
| `ARCHIVED` | ❌ | ✅ | `ArticleArchivingScheduler` runs daily at midnight |
| `DRAFT` | ❌ | ❌ | Admin creates via `POST /api/v1/articles` |

The **`ArticleArchivingScheduler`** runs every day at midnight (server timezone) and bulk-transitions all `PUBLISHED` articles whose `pubDate` is older than **7 days** to `ARCHIVED`.

---

## Security Model

The platform implements multiple layers of security aligned with the OWASP Top 10:

| Threat | Mitigation |
|--------|-----------|
| Brute-force / credential stuffing | Bucket4j rate limiting on `/api/auth/**` (HTTP 429) |
| Scraping / DDoS on search | Bucket4j rate limiting on `/api/v1/search` (HTTP 429) |
| XSS via stored content | OWASP Java HTML Sanitizer on all rich-text article fields |
| Privilege escalation | `@PreAuthorize("hasRole('ADMIN')")` on state-changing endpoints |
| Token theft via XSS | Refresh tokens in `HttpOnly; Secure; SameSite=Strict` cookies |
| Replay attacks | Refresh token rotation — each `/refresh` call invalidates the previous token |
| Cross-origin requests | Strict CORS — only configured frontend origins allowed in production |

### Secured Endpoints Summary

| Endpoint | Auth Required | Role Required |
|----------|:---:|:---:|
| `GET /api/v1/articles` | ❌ | — |
| `GET /api/v1/articles/archived` | ❌ | — |
| `GET /api/v1/articles/stream` | ❌ | — |
| `GET /api/v1/search` | ❌ | — (rate-limited) |
| `POST /api/v1/ingestion` | ❌ | — |
| `POST /api/v1/articles` | ✅ Bearer | `ADMIN` |
| `PUT /api/v1/articles/{id}` | ✅ Bearer | `ADMIN` |
| `DELETE /api/v1/articles/{id}` | ✅ Bearer | `ADMIN` |
| `POST /api/auth/login` | ❌ | — (rate-limited) |
| `POST /api/auth/refresh` | ❌ (cookie) | — (rate-limited) |
| `POST /api/auth/logout` | ❌ (cookie) | — |

---

## Database Schema

Managed by **Flyway**. Migrations run automatically on application startup.

### Core `articles` table

| Column | Type | Description |
|---|---|---|
| `id` | `UUID` PK | Unique article identifier |
| `link` | `TEXT` UNIQUE | Source URL (used for deduplication) |
| `title` | `TEXT` | Article headline |
| `content` | `TEXT` | Full article body |
| `description` | `TEXT` | Short article description |
| `creator` | `TEXT` | Author / byline |
| `language` | `TEXT` | ISO language code |
| `country` | `TEXT` | ISO country code |
| `category` | `TEXT` | News category |
| `pub_date` | `TIMESTAMPTZ` | Original publication date (used for archiving) |
| `status` | `VARCHAR(20)` | Article lifecycle status (`PUBLISHED`, `DISABLED`, `ARCHIVED`, `DRAFT`) |
| `ai_summary` | `TEXT` | GPT-4 generated summary |
| `ai_tag` | `TEXT` | GPT-4 generated tags |
| `source_name` | `TEXT` | Source publication name |
| `image_url` | `TEXT` | Article thumbnail URL |
| `created_at` | `TIMESTAMPTZ` | When the article was ingested |
| `updated_at` | `TIMESTAMPTZ` | Last modification timestamp (auto-updated by trigger) |

### Migration history

| Migration | Description |
|---|---|
| `V1__init_schema.sql` | Initial `articles` table with `url`, `title`, `content`, `created_at` |
| `V2__add_ai_fields.sql` | Added `ai_summary`, `tags` columns |
| `V3__extend_articles_schema.sql` | Renamed `url` → `link`; added full Newsdata.io field set |
| `V4__add_source_type.sql` | Added `source_type` discriminator column |
| `V5__add_article_sequence.sql` | Added sequence for article ordering |
| `V6__add_title_hash.sql` | Added `title_hash` column for deduplication |
| `V7__add_updated_at_trigger.sql` | Trigger to auto-update `updated_at` on row change |
| `V8__add_article_status.sql` | Added `status VARCHAR(20) NOT NULL DEFAULT 'PUBLISHED'` |

---

## CORS Configuration

CORS is configured via **Spring profiles**. Activate the appropriate profile at startup.

### `development` profile — all origins allowed

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=development
# or
java -jar app.jar --spring.profiles.active=development
```

Allows all origins (`*`), all standard methods, all headers, with credentials and a 1-hour max-age cache.

### `production` profile — restricted origin

```bash
java -jar app.jar --spring.profiles.active=production
```

Restricts access to your configured `CORS_ALLOWED_ORIGIN` only. Set this environment variable to your actual frontend domain before deploying.

| Profile | Allowed Origins | Methods | Headers |
|---|---|---|---|
| `development` | `*` | GET, POST, PUT, DELETE, OPTIONS | `*` |
| `production` | `$CORS_ALLOWED_ORIGIN` | GET, POST, PUT, DELETE, OPTIONS | Content-Type, Authorization, Accept |

---

## Running Tests

```bash
# Run all tests and generate JaCoCo coverage report
mvn test

# Run a specific test class
mvn test -Dtest=ArticleControllerTest

# View the HTML coverage report
# Open: target/site/jacoco/index.html
```

Tests use an **H2 in-memory database** (profile `test`) — no Supabase connection required. Flyway is disabled in test mode; Hibernate creates the schema automatically from JPA entity metadata. Spring AI OpenAI autoconfiguration is excluded in the test profile to avoid external API dependencies.

### Test Coverage

| Test Class | What Is Tested |
|---|---|
| `RealNewsletterApplicationTests` | Spring context loads without errors |
| `ArticleControllerTest` | Paginated listing, filters, SSE async start (integration) |
| `AdminArticleControllerTest` | Admin create, disable, delete — RBAC enforcement (integration) |
| `SearchControllerTest` | Keyword search, category + date-range filters, rate limit (integration) |
| `SecurityIntegrationTest` | Auth filter, token validation, 401/403 responses (integration) |
| `AuthControllerIntegrationTest` | Login, refresh, logout, token rotation (integration) |
| `JwtServiceTest` | Token generation, parsing, expiry (unit) |
| `RefreshTokenStoreTest` | Token creation, validation, rotation, expiry (unit) |
| `ArticleArchivingSchedulerTest` | Archiving logic, cutoff date, idempotency (unit) |
| `ArticleArchivingAcceptanceTest` | End-to-end archive flow — scheduler + feed exclusion (integration) |
| `AiEnhancementServiceTest` | Summary extraction, tag parsing, null handling (unit) |
| `IngestionServiceTest` | Deduplication, new article persistence (integration) |
| `ExternalNewsClientTest` | Newsdata.io HTTP client response mapping (unit) |
| `ExternalNewsClientPaginatedTest` | Newsdata.io pagination logic (unit) |
| `NewsApiClientPaginatedTest` | NewsAPI pagination logic (unit) |
| `NewsApiIngestionSchedulerTest` | NewsAPI scheduler orchestration (unit) |
| `NewsDataIngestionSchedulerTest` | Newsdata.io scheduler orchestration (unit) |
| `NewsApiSchedulerPropertiesTest` | NewsAPI scheduler config binding (unit) |
| `NewsDataSchedulerPropertiesTest` | Newsdata.io scheduler config binding (unit) |
| `ArticleRepositoryTest` | JPA queries, search, bulk status update (integration) |

Current line coverage: **≥ 71%**

---

The application deploys to a **Hostinger VPS** via **GitHub Actions** with automated Docker image builds and server updates.

### Architecture

```
GitHub (main / develop branches)
        │
        ├─► develop push ──► build image ──► push to GHCR ──► SSH to dev VPS ──► docker compose deploy
        │                                        (:develop tag)
        │
        └─► main push ──────► build image ──► push to GHCR ──► SSH to prod VPS ──► docker compose deploy
                                 (:latest tag)
```

### Port Configuration

| Environment | Host Port | Container Port | `SERVER_PORT` | Spring Profile |
|---|---|---|---|---|
| **Dev** | `8081` | `8081` | `8081` | `dev` |
| **Prod** | `9000` | `9000` | `9000` | `prd` |

Both dev and prod run on the **same VPS** simultaneously without port conflicts.

### GitHub Actions Workflows

#### 1. `.github/workflows/deploy.yml` — Deploy to Dev VPS

**Trigger:** `push` to `develop` branch

```yaml
on:
  push:
    branches: [ develop ]
```

**Steps:**
1. Validate all required GitHub secrets/variables are present
2. Checkout source code
3. Log in to GHCR
4. Build Docker image and push as `:develop` tag
5. Test SSH connectivity (warning-only, doesn't fail the pipeline)
6. SSH into dev VPS and execute:
   - Fetch `docker-compose.yml` from the `develop` branch
   - Log in to GHCR
   - Pull the latest `:develop` image
   - Tear down old containers
   - Start fresh with `docker compose up -d`
   - Print app logs if startup fails

#### 2. `.github/workflows/deploy-prod.yml` — Deploy to Prod VPS

**Trigger:** `push` to `main` branch

Same steps as dev, but:
- Pushes image as `:latest` tag
- Fetches `docker-compose.prod.yml` from `main` branch
- Deploys to prod VPS at port `9000`
- Spring profile set to `prd`

### Required GitHub Secrets

Go to **Settings → Secrets and variables → Actions → New repository secret** and add:

| Secret Name | Value | Used In |
|---|---|---|
| `GHCR_TOKEN` | GitHub Personal Access Token (classic) with `read:packages` + `write:packages` scopes | Both workflows (push image to GHCR) |
| `DEV_VPS_HOST` | Dev VPS IP address or hostname | dev workflow |
| `DEV_VPS_USER` | SSH username for dev VPS (e.g. `ravidsun`) | dev workflow |
| `DEV_VPS_SSH_KEY` | Private SSH key (multiline, Unix line endings) | dev workflow |
| `DB_PASSWORD_DEV` | PostgreSQL password for dev database | dev workflow (passed to app) |
| `PROD_VPS_HOST` | Prod VPS IP address or hostname | prod workflow |
| `PROD_VPS_USER` | SSH username for prod VPS | prod workflow |
| `PROD_VPS_SSH_KEY` | Private SSH key for prod VPS | prod workflow |
| `DB_PASSWORD` | PostgreSQL password for prod database | prod workflow (passed to app) |

### Required GitHub Variables

Go to **Settings → Secrets and variables → Actions → Variables** and add:

| Variable Name | Value | Used In |
|---|---|---|
| `DEV_DB_URL` | JDBC connection string (e.g. `jdbc:postgresql://72.60.201.76:5432/realnews-dev?sslmode=disable`) | dev workflow (passed to app) |
| `PROD_DB_URL` | JDBC connection string for prod database | prod workflow (passed to app) |
| `DEV_APP_HOST` | Hostname for Traefik routing (e.g. `dev-api.yourdomain.com` or leave empty for IP access) | dev workflow |
| `PROD_APP_HOST` | Hostname for Traefik routing (e.g. `api.yourdomain.com`) | prod workflow |

### VPS Firewall Configuration

Open the required ports in **Hostinger control panel** → **VPS → Firewall**:

| Protocol | Port | Source | Purpose |
|---|---|---|---|
| TCP | 22 | `0.0.0.0/0` | SSH access for deployments |
| TCP | 8081 | `0.0.0.0/0` | Dev API access |
| TCP | 9000 | `0.0.0.0/0` | Prod API access |

### Accessing the Deployed Applications

**Dev:**
```bash
curl http://72.60.201.76:8081/actuator/health
curl http://72.60.201.76:8081/api/v1/articles
curl http://72.60.201.76:8081/swagger-ui.html
```

**Prod:**
```bash
curl http://72.60.201.76:9000/actuator/health
curl http://72.60.201.76:9000/api/v1/articles
curl http://72.60.201.76:9000/swagger-ui.html
```

### Database Setup on VPS

Before the first deployment, set up the PostgreSQL database:

```bash
# Connect to PostgreSQL on the VPS
psql -h 127.0.0.1 -U postgres -d realnews-dev
```

Then create the app user and grant permissions:

```sql
-- Create app user
CREATE USER realnewsuser WITH PASSWORD 'your_db_password';
GRANT ALL PRIVILEGES ON DATABASE "realnews-dev" TO realnewsuser;
GRANT ALL ON SCHEMA public TO realnewsuser;

-- Grant on all existing tables (including flyway_schema_history)
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO realnewsuser;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO realnewsuser;

-- Make realnewsuser owner of flyway_schema_history
ALTER TABLE public.flyway_schema_history OWNER TO realnewsuser;

-- Default privileges for future objects
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO realnewsuser;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO realnewsuser;

\q
```

### One-Time VPS Setup

1. **Create the app directory:**
   ```bash
   mkdir -p /opt/real-newsletter
   chown -R ravidsun:ravidsun /opt/real-newsletter
   ```

2. **Allow ravidsun to run Docker without sudo:**
   ```bash
   usermod -aG docker ravidsun
   ```

3. **Generate SSH key for ravidsun (if not already done):**
   ```bash
   sudo -u ravidsun ssh-keygen -t ed25519 -C "github-actions-deploy" -f /home/ravidsun/.ssh/id_ed25519 -N ""
   cat /home/ravidsun/.ssh/id_ed25519.pub >> /home/ravidsun/.ssh/authorized_keys
   ```

   Copy the private key (`cat /home/ravidsun/.ssh/id_ed25519`) and add it as the `DEV_VPS_SSH_KEY` GitHub secret.

### Verifying Deployments

After pushing to `develop` or `main`, check the GitHub Actions run:

1. Go to **Actions** in your GitHub repository
2. Click the workflow run (deploy-prod or deploy)
3. Check logs for any failures

If deployment succeeds, access the app:

```bash
# Check container is running
ssh ravidsun@72.60.201.76 "docker ps | grep real-newsletter"

# Check app startup logs
ssh ravidsun@72.60.201.76 "docker logs real-newsletter-app-1 --tail 50"

# Test HTTP response
curl http://72.60.201.76:8081/actuator/health  # dev
curl http://72.60.201.76:9000/actuator/health  # prod
```

---


## Project Structure

```
real-newsletter/
├── src/
│   ├── main/
│   │   ├── java/com/realnewsletter/
│   │   │   ├── RealNewsletterApplication.java        # Entry point
│   │   │   ├── auth/
│   │   │   │   ├── AuthController.java               # /api/auth/login, /refresh, /logout
│   │   │   │   ├── JwtAuthenticationFilter.java      # Bearer token extraction + validation
│   │   │   │   ├── JwtProperties.java                # JWT config props (TTL, secret, cookie name)
│   │   │   │   ├── JwtService.java                   # Token generation & parsing (JJWT)
│   │   │   │   ├── LoginRequest.java                 # Login DTO (username, password)
│   │   │   │   ├── LoginResponse.java                # Login DTO (accessToken, tokenType, expiresIn)
│   │   │   │   └── RefreshTokenStore.java            # In-memory refresh token store (rotation)
│   │   │   ├── config/
│   │   │   │   ├── Bucket4jRateLimitInterceptor.java # Rate limiter (auth + search endpoints)
│   │   │   │   ├── Bucket4jRateLimitProperties.java  # Rate limiter config (capacity, refill, TTL)
│   │   │   │   ├── CorsConfig.java                   # Profile-based CORS rules
│   │   │   │   ├── EnvironmentConfig.java            # Environment bean config
│   │   │   │   ├── FlywayConfig.java                 # Dev-profile clean+migrate runner
│   │   │   │   ├── NewsApiSchedulerProperties.java   # NewsAPI scheduler config props
│   │   │   │   ├── NewsDataSchedulerProperties.java  # Newsdata scheduler config props
│   │   │   │   ├── OpenApiConfig.java                # Swagger/OpenAPI configuration
│   │   │   │   ├── RateLimitConfig.java              # Rate limit bean setup
│   │   │   │   ├── RateLimitInterceptorImpl.java     # Legacy rate limit interceptor
│   │   │   │   ├── RateLimitProperties.java          # Legacy rate limit config props
│   │   │   │   ├── SecurityConfig.java               # Spring Security filter chain + RBAC setup
│   │   │   │   └── WebClientConfig.java              # RestClient bean
│   │   │   ├── controller/
│   │   │   │   ├── ArticleController.java            # REST + SSE + admin article endpoints
│   │   │   │   ├── IngestionController.java          # Manual ingestion trigger
│   │   │   │   └── SearchController.java             # GET /api/v1/search
│   │   │   ├── dto/
│   │   │   │   ├── ArticleCreateRequest.java         # Admin create article request body
│   │   │   │   ├── ArticleDto.java                   # API response record
│   │   │   │   └── ArticleStatusUpdateRequest.java   # Admin status update request body
│   │   │   ├── model/
│   │   │   │   ├── Article.java                      # JPA entity (base)
│   │   │   │   ├── ArticleStatus.java                # Lifecycle enum (DRAFT, PUBLISHED, DISABLED, ARCHIVED)
│   │   │   │   ├── NewArticleEvent.java              # Spring application event
│   │   │   │   ├── NewsApiArticle.java               # NewsAPI-specific article model
│   │   │   │   └── NewsdataArticle.java              # Newsdata.io-specific article model
│   │   │   ├── repository/
│   │   │   │   ├── ArticleRepository.java            # Spring Data JPA repository
│   │   │   │   └── ArticleSpecification.java         # JPA Specifications (filters, search queries)
│   │   │   ├── scheduler/
│   │   │   │   ├── ArticleArchivingScheduler.java    # Daily cron: PUBLISHED→ARCHIVED after 7 days
│   │   │   │   ├── IngestionResult.java              # Shared record: fetched/saved/skipped/errors
│   │   │   │   ├── KeepAliveScheduler.java           # DB keep-alive ping
│   │   │   │   ├── NewsApiIngestionScheduler.java    # NewsAPI scheduled trigger
│   │   │   │   └── NewsDataIngestionScheduler.java   # Newsdata.io scheduled trigger
│   │   │   └── service/
│   │   │       ├── AiEnhancementService.java         # GPT-4 summary + tag generation
│   │   │       ├── ArticleStreamService.java         # SSE client registry + broadcaster
│   │   │       ├── ExternalNewsClient.java           # Newsdata.io HTTP client
│   │   │       ├── HtmlSanitizerService.java         # OWASP HTML sanitization
│   │   │       ├── NewsApiClient.java                # NewsAPI HTTP client
│   │   │       └── SearchService.java                # Keyword search with category / date filters
│   │   └── resources/
│   │       ├── application.yml                       # Invariant defaults
│   │       ├── application-local.yml                 # Local dev overrides
│   │       ├── application-dev.yml                   # Dev environment overrides
│   │       ├── application-prd.yml                   # Production overrides
│   │       └── db/migration/
│   │           ├── V1__init_schema.sql               # Articles table
│   │           ├── V2__add_ai_fields.sql             # AI summary + tags columns
│   │           ├── V3__extend_articles_schema.sql    # Full Newsdata.io field set
│   │           ├── V4__add_source_type.sql           # Source type discriminator
│   │           ├── V5__add_article_sequence.sql      # Article ordering sequence
│   │           ├── V6__add_title_hash.sql            # Title deduplication hash
│   │           ├── V7__add_updated_at_trigger.sql    # Auto-update trigger
│   │           └── V8__add_article_status.sql        # Lifecycle status column
│   └── test/
│       ├── java/com/realnewsletter/
│       │   ├── RealNewsletterApplicationTests.java
│       │   ├── auth/
│       │   │   ├── AuthControllerIntegrationTest.java
│       │   │   ├── JwtServiceTest.java
│       │   │   └── RefreshTokenStoreTest.java
│       │   ├── config/
│       │   │   ├── NewsApiSchedulerPropertiesTest.java
│       │   │   └── NewsDataSchedulerPropertiesTest.java
│       │   ├── controller/
│       │   │   ├── AdminArticleControllerTest.java
│       │   │   ├── ArticleControllerTest.java
│       │   │   ├── SearchControllerTest.java
│       │   │   └── SecurityIntegrationTest.java
│       │   ├── repository/
│       │   │   └── ArticleRepositoryTest.java
│       │   └── service/
│       │       ├── AiEnhancementServiceTest.java
│       │       ├── ArticleArchivingAcceptanceTest.java
│       │       ├── ArticleArchivingSchedulerTest.java
│       │       ├── ExternalNewsClientPaginatedTest.java
│       │       ├── ExternalNewsClientTest.java
│       │       ├── IngestionServiceTest.java
│       │       ├── NewsApiClientPaginatedTest.java
│       │       ├── NewsApiIngestionSchedulerTest.java
│       │       └── NewsDataIngestionSchedulerTest.java
│       └── resources/
│           └── application-test.yml                  # H2 + Flyway-disabled + security config
├── Dockerfile
├── docker-compose.yml
├── pom.xml
└── README.md
```

---

## Branching Strategy

This project uses a **`develop`-first** workflow to keep production (`main`) stable:

| Branch | Purpose | How changes land |
|--------|---------|-----------------|
| `feature/*` | Individual feature / bug-fix work | Branched from `develop`; merged into `develop` via PR after agent-pipeline review |
| `develop` | Integration / staging branch | All automated agent pipeline work targets here |
| `main` | Production branch | **Manual promotion only** — a human must merge `develop` → `main` before a production release |

### How the Agent Pipeline Fits In

```
GitHub Issue
     │
     ▼
[Pipeline Agent]  Planner → Coder → Reviewer → Test* → DevOps
                                       │
                   feature/* ──────────► develop  (automated)
                                                         │
                                       develop ─────────► main  (manual gate)
```

- Feature branches are created from `develop` and merged back into `develop` on review approval.
- The **DevOps agent** builds and releases from `develop` — it never touches `main`.
- Promoting `develop` → `main` is a deliberate, human-controlled step. Run it when you are ready to go to production:

```bash
git checkout main
git merge --no-ff develop -m "chore: promote develop → main for vX.Y.Z release"
git push origin main
```

---

## Release History

| Version | Date | Summary |
|---|---|---|
| [v2.5.0](https://github.com/ravidsun/real-newsletter/releases/tag/v2.5.0) | 2026-05-14 | AI-Powered Article Search — `GET /api/v1/search` with keyword, category, and date-range filters; rate-limited |
| [v2.4.0](https://github.com/ravidsun/real-newsletter/releases/tag/v2.4.0) | 2026-05-11 | Automated Article Archiving — daily scheduler archives PUBLISHED articles older than 7 days; `GET /api/v1/articles/archived` endpoint |
| [v2.3.0](https://github.com/ravidsun/real-newsletter/releases/tag/v2.3.0) | 2026-05-11 | Admin Portal Article Lifecycle — `ArticleStatus` enum (DRAFT/PUBLISHED/DISABLED/ARCHIVED); `PUT /{id}`, `DELETE /{id}` admin endpoints; public feed excludes DISABLED articles |
| [v2.2.0](https://github.com/ravidsun/real-newsletter/releases/tag/v2.2.0) | 2026-05-10 | JWT Authentication — short-lived access tokens (15 min), HttpOnly refresh token cookies, token rotation, `/api/auth/login`, `/refresh`, `/logout` |
| [v2.1.0](https://github.com/ravidsun/real-newsletter/releases/tag/v2.1.0) | 2026-05-08 | Security Hardening — Bucket4j rate limiting, strict CORS, OWASP HTML sanitization, `@PreAuthorize` RBAC on all state-changing endpoints |
| [v2.0.0](https://github.com/ravidsun/real-newsletter/releases/tag/v2.0.0) | 2026-05-03 | Profile-driven Flyway; manual ingestion endpoint with `IngestionResult` stats; JaCoCo 0.8.13 for Java 25 support |
| [v1.8.0](https://github.com/ravidsun/real-newsletter/releases/tag/v1.8.0) | 2026-04-17 | Upgrade to Spring Boot 4.0.0 + Spring AI 2.0.0-M2; Docker layer caching fix |
| [v1.7.0](https://github.com/ravidsun/real-newsletter/releases/tag/v1.7.0) | 2026-04-17 | Dual news source ingestion (Newsdata.io + NewsAPI) with rate limiting |
| [v1.5.0](https://github.com/ravidsun/real-newsletter/releases/tag/v1.5.0) | 2026-04-11 | REST API, SSE streaming, CORS profiles |
| v1.4.0 | 2026-04-11 | AI enrichment pipeline (GPT-4 summaries + tags) |
| v1.3.0 | — | Scheduled news ingestion with deduplication |
| v1.2.0 | — | Flyway database migrations |
| v1.1.0 | — | Spring Data JPA persistence layer |
| v1.0.0 | — | Initial project scaffold |

---

## License

[MIT](LICENSE)
