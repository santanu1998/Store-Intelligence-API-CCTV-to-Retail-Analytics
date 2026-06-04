# Store Intelligence API — Purplle Tech Challenge

Real-time retail analytics API built on **Java 21 + Spring Boot 3.5 + MySQL 8**.  
Ingests CCTV detection events and POS transactions → delivers live store metrics,
conversion funnel, zone heatmap, and anomaly alerts.

---

## Quick Start (5 commands)

```bash
# 1. Clone
git clone <repo-url> && cd store-intelligence

# 2. Generate Gradle wrapper (only needed once)
gradle wrapper --gradle-version 8.13

# 3. Start MySQL + API with Docker Compose
docker compose up --build -d

# 4. Verify health (wait ~60 s for startup)
curl http://localhost:8080/health

# 5. Open Swagger UI
open http://localhost:8080/swagger-ui.html
```

> **Without Docker** — run MySQL locally then:
> ```bash
> ./gradlew bootRun
> ```

---

## Prerequisites

| Tool           | Version        |
|----------------|----------------|
| JDK            | 21             |
| Gradle         | 8.13           |
| MySQL          | 8.0+           |
| Docker         | 24+ (optional) |

---

## Environment Variables

| Variable                         | Default                                              | Description                       |
|----------------------------------|------------------------------------------------------|-----------------------------------|
| `SPRING_DATASOURCE_URL`          | `jdbc:mysql://localhost:3306/store_intelligence?...` | MySQL URL |
| `SPRING_DATASOURCE_USERNAME`     | `root`                                               | MySQL username                    |
| `SPRING_DATASOURCE_PASSWORD`     | `Santanu@2026`                                       | MySQL password                    |
| `APP_ANOMALY_QUEUE_SPIKE_THRESHOLD` | `5`                                                  | Queue depth for CRITICAL anomaly  |
| `APP_ANOMALY_STALE_FEED_MINUTES` | `10`                                                 | Minutes before STALE_FEED warning |
| `APP_CONVERSION_BILLING_WINDOW_MINUTES` | `5`                                                  | POS correlation window            |

All configurable in `src/main/resources/application.yml`.

---

## API Endpoints

| Method | Path                              | Description                              |
|--------|-----------------------------------|------------------------------------------|
| `POST` | `/events/ingest`                  | Ingest CCTV events (batch up to 500)     |
| `POST` | `/pos/ingest`                     | Ingest POS transactions (JSON)           |
| `POST` | `/pos/import-csv`                 | Import Brigade Bangalore CSV             |
| `GET`  | `/stores`                         | List all registered stores               |
| `GET`  | `/stores/{storeId}/metrics`       | Real-time conversion, dwell, queue       |
| `GET`  | `/stores/{storeId}/funnel`        | 4-stage conversion funnel                |
| `GET`  | `/stores/{storeId}/heatmap`       | Zone visit heatmap (0–100 score)         |
| `GET`  | `/stores/{storeId}/anomalies`     | Active anomalies with suggested actions  |
| `GET`  | `/health`                         | Service + per-store feed health          |

All endpoints accept optional `?start=` and `?end=` query params (ISO-8601 UTC)  
to query custom time windows. Default is today (UTC midnight → now).

Full interactive docs: **http://localhost:8080/swagger-ui.html**

---

## Run the Detection Pipeline and Feed Events

If you have the detection pipeline output (`.jsonl` event file):

```bash
# Feed detection events into the API
curl -X POST http://localhost:8080/events/ingest \
  -H "Content-Type: application/json" \
  -d '{"events": [<paste events array here>]}'

# Or import POS transactions from Brigade CSV
curl -X POST http://localhost:8080/pos/import-csv \
  -F "file=@Brigade_Bangalore_10_April_26.csv" \
  -F "storeId=ST1008"
```

---

## Database Schema

```
stores            — auto-registered per store_id
store_events      — all CCTV detection events (idempotent by event_id)
pos_transactions  — POS records for conversion correlation
```

Schema applied automatically via **Flyway** on startup (`V1__init_schema.sql`).

---

## Running Tests

```bash
./gradlew test
```

eeded). Coverage includes:
- `EventIngestionServiceTest` — batch ingest, deduplication, partial success
- `MetricsServiceTest` — conversion rate, zero-visitor edge cases
- `EventControllerTest` — MockMvc: HTTP status codes, validation, error responses

---

## Project Structure

```
src/main/java/com/purplle/storeintelligence/
├── config/         SecurityConfig, SwaggerConfig, AppProperties
├── constants/      AppConstants, ApiRoutes
├── controller/     EventController, StoreController, HealthController
├── dto/
│   ├── request/    IngestEventsRequest, StoreEventRequest, ...
│   └── response/   ApiResponse, StoreMetricsResponse, FunnelResponse, ...
├── entity/         Store, StoreEvent, PosTransaction
├── enums/          EventType, AnomalySeverity
├── exception/      GlobalExceptionHandler, AppException, ...
├── repository/     StoreEventRepository, PosTransactionRepository, ...
└── service/
    ├── impl/       EventIngestionServiceImpl, MetricsServiceImpl, ...
    └── (interfaces)
```

---

## Concurrency Handling

- Event ingestion is idempotent by `event_id` (UNIQUE constraint + pre-check).
- Store auto-registration uses `saveAndFlush` with `DataIntegrityViolationException` catch
  to handle concurrent first-event races safely.
- All metrics are computed live from the DB — no in-memory state that could go stale.
