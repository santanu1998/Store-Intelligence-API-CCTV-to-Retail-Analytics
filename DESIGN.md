# DESIGN.md — Store Intelligence API

## System Architecture

```
CCTV Pipeline (Python/CV)
        │  POST /events/ingest
        ▼
┌───────────────────────────────────────────────────────┐
│              Store Intelligence API                   │
│  Spring Boot 3.5  ·  Java 21  ·  MySQL 8             │
│                                                       │
│  EventController ──► EventIngestionService            │
│       ├── Validate event schema                       │
│       ├── Idempotency check (event_id unique)         │
│       └── Auto-register store on first event          │
│                                                       │
│  StoreController ──► MetricsService                   │
│                  ──► FunnelService                    │
│                  ──► HeatmapService                   │
│                  ──► AnomalyService                   │
│                                                       │
│  HealthController ──► HealthService                   │
│       ├── DB connectivity probe                       │
│       └── Per-store stale-feed detection              │
└───────────────────┬───────────────────────────────────┘
                    │  JPA / Flyway
                    ▼
            ┌──────────────┐
            │    MySQL 8   │
            │  store_events│
            │ pos_transact.│
            │   stores     │
            └──────────────┘
```

## Data Flow

1. **Detection pipeline** (Python, out of scope for this Java service) processes CCTV
   clips and emits JSON events via `POST /events/ingest`.
2. **Ingestion layer** validates each event, skips known `event_id` values (idempotent),
   auto-registers new stores, and persists valid events.
3. **POS transactions** arrive separately via `POST /pos/ingest` or CSV upload.
4. **Analytics endpoints** compute metrics live from the `store_events` and
   `pos_transactions` tables — no caching, always fresh.
5. **Conversion correlation** links visitors to purchases by finding billing-zone activity
   in the 5-minute window before each POS transaction timestamp.

## Key Design Decisions

### 1. All-UTC Storage
All `DATETIME` columns store UTC values. The API never converts to a local timezone —
that responsibility belongs to the dashboard client. This avoids ambiguity during
daylight-saving transitions and simplifies cross-store comparison.

### 2. Idempotent Ingestion
`event_id` has a `UNIQUE` constraint in MySQL. Before inserting, the service checks
`existsByEventId()`. If already present, the event is counted as a `duplicate` (not an
error) and silently skipped. This makes the ingest endpoint safe to call twice with the
same payload (e.g., after a network retry).

### 3. Live Metrics (No Caching)
The challenge spec explicitly requires "Real-time — not cached from yesterday". All
`/metrics`, `/funnel`, `/heatmap`, and `/anomalies` endpoints execute live JPQL/SQL
queries on every call. For the challenge dataset this is fast enough. In production,
a read replica + materialized views would be the next step.

### 4. Conversion Correlation Algorithm
No `customer_id` exists in POS data. The spec defines correlation as:
> *A visitor in the billing zone in the 5-minute window before a POS transaction
> counts as converted.*

Implementation: for each POS transaction at time T, query `store_events` for
`BILLING_QUEUE_JOIN` or billing-zone `ZONE_ENTER` events in `[T-5min, T]`. The union
of all matched `visitor_id` values is the converted-visitor set.

### 5. Partial Success on Ingest
Invalid events in a batch do not fail the entire request. The response includes
`accepted`, `rejected`, `duplicates`, and per-event `errors`. This matches production
behaviour where a badly-formed event from one camera should not block valid events from
other cameras.

---

## AI-Assisted Decisions

### Decision 1 — Conversion Correlation Granularity
**Question asked to Claude:** "Should I correlate POS transactions to individual
visitor sessions or use a simpler time-window approach?"

**AI suggestion:** Use the simpler time-window approach (billing-zone activity in
N minutes before transaction) since there is no customer_id and session reconstruction
from trajectory data is unreliable across camera overlaps.

**My decision:** Agreed and implemented. Added a configurable `billing-window-minutes`
property (default 5) so the window can be tuned without a code change.

### Decision 2 — Event Schema Flattening vs JSON Column
**Question asked to Claude:** "Should `metadata` (queue_depth, sku_zone, session_seq)
be stored in a MySQL JSON column or as separate columns?"

**AI suggestion:** Separate columns for known fields — better indexability and
avoids JSON function calls in WHERE clauses.

**My decision:** Agreed. Flattened `queue_depth`, `sku_zone`, and `session_seq`
into top-level columns. This lets the anomaly detector query `queue_depth` directly
with a simple ORDER BY DESC LIMIT 1.

### Decision 3 — Anomaly Detection: In-Process vs Scheduled Job
**AI suggestion:** Use a `@Scheduled` background job to compute and cache anomalies
every 60 seconds to avoid per-request latency.

**My decision:** Disagreed — implemented as on-demand computation instead.
Rationale: (a) the challenge spec says anomaly endpoints must be "real-time",
(b) for 40 stores and a 20-minute clip dataset, per-request computation is fast,
(c) a scheduled job introduces state that can go stale, defeating the purpose.
In a production system serving thousands of concurrent requests, the scheduled
approach would be reconsidered.
