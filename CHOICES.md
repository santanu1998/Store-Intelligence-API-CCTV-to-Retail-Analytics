# CHOICES.md — Three Key Engineering Decisions

---

## Decision 1 — Technology Stack: Java + Spring Boot vs Python/FastAPI

### Options Considered
| Option | Pros | Cons |
|--------|------|------|
| Python + FastAPI | Native ML ecosystem, shorter code, recommended by spec | Async complexity, weaker JPA/ORM story |
| **Java + Spring Boot** | Strong JPA, transaction management, production tooling | More verbose, slightly higher startup time |
| Go + Gin | Fast, low memory | No JPA, manual SQL, unfamiliar to most reviewers |

### What AI Suggested
Claude suggested Python/FastAPI citing the spec's hint and the simpler async event
ingestion model.

### What I Chose and Why
**Java 21 + Spring Boot 3.5.** The challenge evaluates production readiness and
engineering thinking, not language loyalty. Spring Boot gives me:
- `@Transactional` + JPA for safe concurrent writes out of the box
- Flyway for schema versioning with zero configuration
- Spring Validation for declarative request validation
- Springdoc for automatic OpenAPI documentation
- Actuator for health endpoints

Java 21 with virtual threads (Project Loom, stable since 21) means concurrency at
Python-like simplicity with JVM reliability. The detection pipeline runs in Python;
the intelligence API is independently deployable and uses the stack I can best reason
about in a follow-up interview.

---

## Decision 2 — Database: MySQL vs PostgreSQL vs SQLite

### Options Considered
| Option | Pros | Cons |
|--------|------|------|
| SQLite | Zero setup, file-based | No concurrent writes at scale, no proper DATETIME tz support |
| PostgreSQL | Better JSON, JSONB, window functions | Heavier setup, slightly more complex Docker config |
| **MySQL 8** | Familiar, excellent Spring Boot integration, POS CSV data already in MySQL-compatible format | Less advanced analytics functions than Postgres |

### What AI Suggested
Claude suggested PostgreSQL for its `JSONB` type (useful for storing event metadata) and
superior window functions for funnel analysis.

### What I Chose and Why
**MySQL 8.** The uploaded Brigade Bangalore CSV and challenge dataset come from a retail
system that commonly uses MySQL. Flyway's MySQL dialect support is mature. The challenge
does not require window functions — all funnel logic is implemented in Java by issuing
individual `COUNT(DISTINCT)` queries per stage, which is readable and debuggable.

For a production system handling millions of events/day, PostgreSQL's `JSONB` and window
functions would be compelling. For this challenge, MySQL reduces operational complexity
without sacrificing correctness.

---

## Decision 3 — Conversion Rate: Query-Time Correlation vs Pre-computed Sessions

### Options Considered

**Option A — Pre-computed visitor sessions table:**
A background job materialises `visitor_sessions` rows from events, then joins against
POS transactions. Query time is O(1). But: sessions can be stale, re-entry handling is
complex, and maintaining a second source of truth introduces bugs.

**Option B (chosen) — Query-time POS correlation:**
On every `/metrics` request, load POS transactions for the window, then for each
transaction query billing-zone visitors in the 5-minute window before it. Union the
visitor IDs. Conversion rate = `|union| / unique_entries`.

### What AI Suggested
Claude initially suggested Option A (pre-computed sessions) as more scalable.

### What I Chose and Why
**Option B — Query-time correlation.** The spec says metrics must be "real-time — not
cached from yesterday." Pre-computed sessions cannot satisfy this without a job running
at sub-second frequency, which defeats the purpose.

For the challenge clip (20 minutes per camera), the number of POS transactions is in
the hundreds. Each correlation query is a simple indexed lookup. Total computation per
`/metrics` call is well under 100 ms on any modern MySQL instance.

**Trade-off acknowledged:** At 40 live stores each with thousands of transactions/hour,
this approach would need a materialised view or a streaming aggregation layer
(e.g. Kafka + Flink). This is documented in DESIGN.md as the explicit next step.

The chosen approach keeps the code simple, the output correct, and the reasoning easy to
defend in the follow-up interview.
