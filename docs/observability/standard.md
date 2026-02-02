# Observability Standard

Observability is required from day one.

## Logging

- Use **structured JSON logs**.
- Include these fields on every request log:
  - `requestId`
  - `method`, `path`, `status`
  - `durationMs`
  - `actorId` (when authenticated)
  - `service`, `module`
- Never log secrets or full tokens.

## Health endpoints

- Provide health endpoints suitable for Kubernetes probes:
  - liveness: process is alive
  - readiness: dependencies (DB/Kafka/Redis) are reachable if required for serving traffic

## Metrics (Micrometer)

Minimum metrics:

- HTTP:
  - request count, latency, error rate
- DB:
  - pool usage, query latency (where feasible)
- Kafka:
  - consumer lag, retries, DLQ count

## Tracing (OpenTelemetry)

- Propagate trace context across:
  - inbound HTTP
  - outbound HTTP
  - Kafka produce/consume (where supported)
- Add spans around:
  - request handling
  - DB transactions
  - external calls
  - Kafka handlers
