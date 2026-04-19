# Real Newsletter

An AI-powered news aggregation and delivery platform built with **Spring Boot 4**, **Spring AI 2 (OpenAI GPT-4)**, and **PostgreSQL (Supabase)**. It periodically fetches the latest articles from [Newsdata.io](https://newsdata.io) and the [NewsAPI](https://newsapi.org), enriches them with AI-generated summaries and tags, persists them, and delivers them to clients via a paginated REST API and a real-time Server-Sent Events (SSE) stream.

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
- [API Reference](#api-reference)
  - [GET /api/v1/articles](#get-apiv1articles)
  - [GET /api/v1/articles/stream](#get-apiv1articlesstream)
- [Real-Time SSE Streaming](#real-time-sse-streaming)
- [Database Schema](#database-schema)
- [CORS Configuration](#cors-configuration)
- [Running Tests](#running-tests)
- [Project Structure](#project-structure)
- [Release History](#release-history)

---

## Features

| Feature | Description |
|---|---|
| **Dual News Ingestion** | Scheduled jobs fetch the latest English/US news from both [Newsdata.io](https://newsdata.io) and [NewsAPI](https://newsapi.org) on configurable intervals. Duplicate articles (by URL) are automatically skipped. |
| **AI Enrichment** | Each new article is sent to OpenAI GPT-4 via Spring AI to generate a plain-text summary and a set of comma-separated tags. |
| **Rate Limiting** | Per-source rate limiting prevents API quota exhaustion across both news providers. |
| **Paginated REST API** | `GET /api/v1/articles` returns stored articles as a paginated JSON response, sorted by newest first. |
| **Real-Time SSE Stream** | `GET /api/v1/articles/stream` opens a persistent Server-Sent Events connection. Every new article saved triggers a `new-article` event pushed to all connected clients instantly. |
| **Profile-Based CORS** | Separate CORS policies for `development` (all origins allowed) and `production` (restricted to a configured frontend origin). |
| **Database Migrations** | Flyway manages schema evolution automatically on startup. |
| **Keep-Alive Scheduler** | A background job periodically pings the database to prevent idle connection drops on Supabase's connection pooler. |
| **Actuator** | Spring Boot Actuator endpoints available for health and metrics monitoring. |
| **OpenAPI / Swagger UI** | Auto-generated API docs available at `/swagger-ui.html`. |

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
│              │                          │                             │
│              └────────────┬─────────────┘                             │
│                           ▼                                           │
│                  IngestionScheduler                                    │
│              1. Fetch from news source                                 │
│              2. Deduplicate by URL                                     │
│              3. AI-enrich (summary + tags via GPT-4)                  │
│              4. Save to DB                                             │
│              5. Publish NewArticleEvent                                │
│                     │                  │                              │
│         ┌───────────▼──┐  ┌────────────▼──────────┐                  │
│         │  PostgreSQL   │  │  ArticleStreamService  │                  │
│         │  (Supabase)   │  │  (SSE broadcaster)     │                  │
│         └───────────────┘  └────────────┬───────────┘                  │
│                                         │ push event                  │
│                                ┌────────▼────────┐                    │
│                                │  SSE Clients    │                    │
│                                │  (browsers,curl │                    │
│                                │   etc.)         │                    │
│                                └─────────────────┘                    │
│                                                                       │
│  REST Clients ──► GET /api/v1/articles ──► ArticleController          │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 4.0.0 |
| AI Integration | Spring AI 2.0.0-M2 (OpenAI GPT-4) |
| HTTP Client | Spring `RestClient` (synchronous) |
| Persistence | Spring Data JPA + Hibernate |
| Database | PostgreSQL via Supabase (production), H2 (tests) |
| Migrations | Flyway |
| Real-Time | Server-Sent Events (`SseEmitter`) |
| Build | Maven 3.9+ |
| Containerisation | Docker / Docker Compose |
| Testing | JUnit 5, Mockito, MockMvc |
| Coverage | JaCoCo (≥ 87% line coverage) |
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
DB_URL=jdbc:postgresql://db.<project-ref>.supabase.co:6543/postgres?sslmode=require
DB_USER=postgres
DB_PASSWORD=your-supabase-password

NEWSDATA_API_URL=https://newsdata.io/api/1/news
NEWSDATA_API_KEY=pub_your-newsdata-key

NEWS_API_URL=https://newsapi.org/v2/everything
NEWS_API_KEY=your-newsapi-key

OPENAI_API_KEY=sk-...
```

### Full `application.yml` reference

| Property | Default | Description |
|---|---|---|
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

---

## Running Locally

### With Maven

```bash
# 1. Clone the repository
git clone https://github.com/ravidsun/real-newsletter.git
cd real-newsletter

# 2. Export environment variables (or create a .env file and source it)
export DB_URL="jdbc:postgresql://..."
export DB_USER="postgres"
export DB_PASSWORD="your-password"
export NEWSDATA_API_URL="https://newsdata.io/api/1/news"
export NEWSDATA_API_KEY="pub_your-newsdata-key"
export NEWS_API_URL="https://newsapi.org/v2/everything"
export NEWS_API_KEY="your-newsapi-key"
export OPENAI_API_KEY="sk-..."

# 3. Run the application
mvn spring-boot:run

# The app starts on http://localhost:8080
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

# The app is available at http://localhost:8080
```

The Docker build uses **layer caching** — Maven dependencies are downloaded in a dedicated layer keyed on `pom.xml` only, so source-only changes rebuild the JAR without re-downloading all dependencies.

To stop:

```bash
docker compose down
```

---

## API Reference

### `GET /api/v1/articles`

Returns a paginated list of stored articles, sorted by `createdAt` descending (newest first) by default.

**Query Parameters**

| Parameter | Type | Default | Description |
|---|---|---|---|
| `page` | integer | `0` | Zero-based page number |
| `size` | integer | `20` | Number of articles per page |
| `sort` | string | `createdAt,desc` | Sort field and direction (e.g. `title,asc`) |

**Example Request**

```bash
curl "http://localhost:8080/api/v1/articles?page=0&size=5&sort=createdAt,desc"
```

**Example Response**

```json
{
  "content": [
    {
      "id": "550e8400-e29b-41d4-a716-446655440000",
      "url": "https://example.com/article-1",
      "title": "Breaking: Major Tech Announcement",
      "content": "Full article content here...",
      "aiSummary": "A major technology company announced a groundbreaking product today...",
      "tags": "technology, innovation, ai",
      "createdAt": "2026-04-17T09:45:00Z"
    }
  ],
  "pageable": {
    "pageNumber": 0,
    "pageSize": 5,
    "sort": { "sorted": true, "unsorted": false }
  },
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

> **Note:** Spring Data 4 moved pagination metadata (`totalElements`, `totalPages`, `number`) under the nested `page` key in JSON responses.

---

### `GET /api/v1/articles/stream`

Opens a persistent **Server-Sent Events** (SSE) connection. The server pushes a `new-article` event to this connection every time the ingestion job saves a new article to the database.

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
data: {"id":"550e8400-...","url":"https://...","title":"...","aiSummary":"...","tags":"...","createdAt":"..."}
```

**Example — Browser (JavaScript)**

```js
const source = new EventSource('http://localhost:8080/api/v1/articles/stream');

source.addEventListener('new-article', (event) => {
  const article = JSON.parse(event.data);
  console.log('New article:', article.title);
  console.log('Tags:', article.tags);
});

source.onerror = () => {
  console.error('SSE connection error');
};
```

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
curl -N http://localhost:8080/api/v1/articles/stream
```

You will see output like:

```
id: 550e8400-e29b-41d4-a716-446655440000
event: new-article
data: {"id":"550e8400-...","title":"Example Article",...}
```

---

## Database Schema

Managed by **Flyway**. Migrations run automatically on application startup.

### `articles` table (`V1__init_schema.sql`)

| Column | Type | Constraints | Description |
|---|---|---|---|
| `id` | `UUID` | PK, default `uuid_generate_v4()` | Unique article identifier |
| `url` | `TEXT` | NOT NULL, UNIQUE | Source URL (used for deduplication) |
| `title` | `TEXT` | | Article headline |
| `content` | `TEXT` | | Full article body |
| `created_at` | `TIMESTAMPTZ` | default `now()` | When the article was ingested |
| `updated_at` | `TIMESTAMPTZ` | default `now()` | Last modification timestamp |

### AI fields (`V2__add_ai_fields.sql`)

| Column | Type | Description |
|---|---|---|
| `ai_summary` | `TEXT` | GPT-4 generated plain-text summary |
| `tags` | `TEXT` | Comma-separated tags generated by GPT-4 (e.g. `"ai, technology, innovation"`) |

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

Restricts access to `https://frontend.example.com` only. Update `CorsConfig.java` to match your actual frontend URL before deploying.

| Profile | Allowed Origins | Methods | Headers |
|---|---|---|---|
| `development` | `*` | GET, POST, PUT, DELETE, OPTIONS | `*` |
| `production` | `https://frontend.example.com` | GET, POST, PUT, DELETE, OPTIONS | Content-Type, Authorization, Accept |

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
| `ArticleControllerTest` | Paginated listing, sorting, SSE async start (integration) |
| `AiEnhancementServiceTest` | Summary extraction, tag parsing, null handling (unit) |
| `IngestionServiceTest` | Deduplication, new article persistence (integration) |
| `ExternalNewsClientTest` | Newsdata.io HTTP client response mapping (unit) |
| `ExternalNewsClientPaginatedTest` | Newsdata.io pagination logic (unit) |
| `NewsApiClientPaginatedTest` | NewsAPI pagination logic (unit) |
| `NewsApiIngestionSchedulerTest` | NewsAPI scheduler orchestration (unit) |
| `NewsDataIngestionSchedulerTest` | Newsdata.io scheduler orchestration (unit) |

Current line coverage: **≥ 87%**

---

## Project Structure

```
real-newsletter/
├── src/
│   ├── main/
│   │   ├── java/com/realnewsletter/
│   │   │   ├── RealNewsletterApplication.java        # Entry point
│   │   │   ├── config/
│   │   │   │   ├── CorsConfig.java                   # Profile-based CORS rules
│   │   │   │   ├── EnvironmentConfig.java            # Environment bean config
│   │   │   │   ├── FlywayConfig.java                 # Dev-profile clean+migrate runner
│   │   │   │   ├── NewsApiSchedulerProperties.java   # NewsAPI scheduler config props
│   │   │   │   ├── NewsDataSchedulerProperties.java  # Newsdata scheduler config props
│   │   │   │   ├── OpenApiConfig.java                # Swagger/OpenAPI configuration
│   │   │   │   ├── RateLimitConfig.java              # Rate limit bean setup
│   │   │   │   ├── RateLimitInterceptorImpl.java     # Rate limit interceptor
│   │   │   │   ├── RateLimitProperties.java          # Rate limit config props
│   │   │   │   └── WebClientConfig.java              # RestClient bean
│   │   │   ├── controller/
│   │   │   │   └── ArticleController.java            # REST + SSE endpoints
│   │   │   ├── dto/
│   │   │   │   └── ArticleDto.java                   # API response record
│   │   │   ├── model/
│   │   │   │   ├── Article.java                      # JPA entity
│   │   │   │   └── NewArticleEvent.java              # Spring application event
│   │   │   ├── repository/
│   │   │   │   └── ArticleRepository.java            # Spring Data JPA repository
│   │   │   ├── scheduler/
│   │   │   │   ├── IngestionScheduler.java           # Core ingestion orchestrator
│   │   │   │   ├── KeepAliveScheduler.java           # DB keep-alive ping
│   │   │   │   ├── NewsApiIngestionScheduler.java    # NewsAPI scheduled trigger
│   │   │   │   └── NewsDataIngestionScheduler.java   # Newsdata.io scheduled trigger
│   │   │   └── service/
│   │   │       ├── AiEnhancementService.java         # GPT-4 summary + tag generation
│   │   │       ├── ArticleStreamService.java         # SSE client registry + broadcaster
│   │   │       ├── ExternalNewsClient.java           # Newsdata.io HTTP client
│   │   │       └── NewsApiClient.java                # NewsAPI HTTP client
│   │   └── resources/
│   │       ├── application.yml                       # Main configuration
│   │       ├── application-dev.yml                   # Dev profile overrides
│   │       └── db/migration/
│   │           ├── V1__init_schema.sql               # Articles table
│   │           └── V2__add_ai_fields.sql             # AI summary + tags columns
│   └── test/
│       ├── java/com/realnewsletter/
│       │   ├── RealNewsletterApplicationTests.java
│       │   ├── controller/
│       │   │   └── ArticleControllerTest.java
│       │   ├── repository/
│       │   │   └── ArticleRepositoryTest.java
│       │   └── service/
│       │       ├── AiEnhancementServiceTest.java
│       │       ├── ExternalNewsClientPaginatedTest.java
│       │       ├── ExternalNewsClientTest.java
│       │       ├── IngestionServiceTest.java
│       │       ├── NewsApiClientPaginatedTest.java
│       │       ├── NewsApiIngestionSchedulerTest.java
│       │       └── NewsDataIngestionSchedulerTest.java
│       └── resources/
│           └── application-test.yml                  # H2 + Flyway-disabled config
├── Dockerfile
├── docker-compose.yml
├── pom.xml
└── README.md
```

---

## Release History

| Version | Date | Summary |
|---|---|---|
| [v1.8.0](https://github.com/ravidsun/real-newsletter/releases/tag/v1.8.0) | 2026-04-17 | Upgrade to Spring Boot 4.0.0 + Spring AI 2.0.0-M2; fix Docker layer caching and libgcc dependency |
| [v1.7.0](https://github.com/ravidsun/real-newsletter/releases/tag/v1.7.0) | — | Dual news source ingestion (Newsdata.io + NewsAPI) with rate limiting |
| [v1.5.0](https://github.com/ravidsun/real-newsletter/releases/tag/v1.5.0) | 2026-04-11 | REST API, SSE streaming, CORS profiles |
| v1.4.0 | — | AI enrichment pipeline (GPT-4 summaries + tags) |
| v1.3.0 | — | Scheduled news ingestion with deduplication |
| v1.2.0 | — | Flyway database migrations |
| v1.1.0 | — | Spring Data JPA persistence layer |
| v1.0.0 | — | Initial project scaffold |

---

## License

[MIT](LICENSE)
