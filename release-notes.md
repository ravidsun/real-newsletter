# Release Notes

## v1.5.0 — 2026-04-11

### Features
- **REST API**: `GET /api/v1/articles` — paginated article listing with sorting support (`?page`, `?size`, `?sort=createdAt,desc`)
- **SSE Streaming**: `GET /api/v1/articles/stream` — Server-Sent Events endpoint; each new article ingested by the scheduler is pushed in real time to all connected clients as a `new-article` event
- **CORS Configuration**: Profile-based CORS — `development` profile allows all origins; `production` profile restricts to `https://frontend.example.com` with credentials and `maxAge(3600)`
- **ArticleDto**: Extended to include `id`, `aiSummary`, `tags`, and `createdAt` fields for richer API responses and SSE payloads

### Internal
- Added `NewArticleEvent` Spring application event published by `IngestionService` after each article save
- Added `ArticleStreamService` to listen for `NewArticleEvent` and broadcast to SSE clients (with automatic cleanup of disconnected clients)
- Integration tests added for all `ArticleController` endpoints

### Issues Closed
- #6 — Impl: REST API & Real-Time Delivery (SSE)

