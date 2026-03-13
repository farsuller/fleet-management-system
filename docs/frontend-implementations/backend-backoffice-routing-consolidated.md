# Backend and Backoffice Routing - Consolidated with Current Implementation

## Purpose
This document consolidates backend and backoffice route/tracking behavior using the current implemented APIs and frontend plans.

Primary sources:
- `src/main/kotlin/com/solodev/fleet/modules/tracking/infrastructure/http/TrackingRoutes.kt`
- `docs/implementations/phase-7-schematic-visualization-engine.md`
- `docs/sample-payloads/tracking-sample-payloads.md`
- `docs/frontend-implementations/web-schematic-visualization.md`
- `docs/frontend-implementations/kotlin-multiplatform-prompt.md`

## Current Backend Capability (Implemented)

### HTTP Endpoints
- `GET /v1/tracking/routes/active` (public)
- `POST /v1/tracking/vehicles/{id}/location` (auth-jwt, rate-limited, idempotency-aware)
- `GET /v1/tracking/vehicles/{vehicleId}/state` (auth-jwt)
- `GET /v1/tracking/fleet/status` (auth-jwt)
- `GET /v1/tracking/vehicles/{vehicleId}/history` (auth-jwt)
- `POST /v1/tracking/routes` (auth-jwt, GeoJSON -> stored route)

### WebSocket Endpoint
- `WS /v1/fleet/live` (inside auth-jwt in `TrackingRoutes.kt`)
- Delta-first broadcast model using `VehicleStateDelta`.

## Backend Processing Pipeline

```text
POST /v1/tracking/vehicles/{id}/location
  -> validates auth + rate limit + optional idempotency key
  -> use case processes SensorPing
  -> route snap/progress/status computation
  -> history persistence
  -> delta broadcast via RedisDeltaBroadcaster
  -> clients consume via WS /v1/fleet/live
```

## Backoffice Integration Contract

### Tracking Map Screen
Backoffice web should:
1. Load initial route set from `GET /v1/tracking/routes/active`.
2. Load fleet snapshot from `GET /v1/tracking/fleet/status`.
3. Connect to `WS /v1/fleet/live` for live deltas.
4. Merge deltas into `VehicleRouteState` using the existing `DeltaDecoder` pattern.

### Vehicle Detail Tracking Tab
Use:
- `GET /v1/tracking/vehicles/{vehicleId}/state` for latest point
- `GET /v1/tracking/vehicles/{vehicleId}/history` for timeline table

### Route Administration
Managers/Admin can create routes via:
- `POST /v1/tracking/routes` with GeoJSON string payload

## Consolidation Notes vs Generated OSRM Plan

1. Keep the generated plan's open-source map rendering approach for clients.
2. Treat backend-managed route entities as the canonical route source.
3. Replace non-existent endpoint assumptions (`/api/driver/route`) with implemented `/v1/tracking/*` APIs.
4. Standardize all frontend parsing on `ApiResponse<T>` envelope.
5. Keep one live transport channel: `WS /v1/fleet/live`.

## Operational Guidance

### Reliability
- Preserve idempotency key usage from clients for retried location updates.
- Respect 429 response handling client-side.
- Keep circuit-breaker fallback behavior visible in logs and dashboard alerts.

### Security
- Keep JWT required for all sensitive tracking endpoints and WS connection.
- Avoid query-param token leakage for browser clients unless strictly required.

### Performance
- Use delta updates for live sessions, not full snapshots for every update.
- Keep fleet snapshot endpoint for initial page load and WS reconnection recovery.

## Backoffice UI Data Mapping

### Fleet List Fields
From `/v1/tracking/fleet/status` map:
- `vehicleId`, `licensePlate`, `make`, `model`
- `status`, `speed`, `progress`, `distanceFromRoute`
- `latitude`, `longitude`, `heading`, `timestamp`

### Vehicle Status Color Guide
- `IN_TRANSIT`: green
- `IDLE`: yellow
- `OFF_ROUTE`: red
- `OFFLINE`: gray

## Backoffice Checklist (Ready to Implement)

### 1) Base Client Setup
- [ ] Base API URL configured per environment (dev/staging/prod).
- [ ] JSON parser supports `ApiResponse<T>` envelope consistently.
- [ ] Global timeout and cancellation strategy configured.
- [ ] Non-production HTTP logging enabled; disabled/redacted in production.

### 2) Auth and Headers
- [ ] Attach `Authorization: Bearer <jwt>` for protected HTTP routes.
- [ ] Ensure WebSocket auth is provided for `WS /v1/fleet/live`.
- [ ] Send `Accept: application/json` for all reads.
- [ ] Send `Content-Type: application/json` for POST route creation.

### 3) Initial Page Load (Tracking Map)
- [ ] Call `GET /v1/tracking/routes/active` and cache route geometry metadata.
- [ ] Call `GET /v1/tracking/fleet/status` to render initial marker set.
- [ ] Build in-memory map: `vehicleId -> VehicleRouteState`.
- [ ] Render empty-state and partial-failure states gracefully.

### 4) Live Updates via WebSocket
- [ ] Connect to `WS /v1/fleet/live` on map screen enter.
- [ ] Handle Ping/Pong keepalive.
- [ ] Merge incoming deltas into current state (`DeltaDecoder` pattern).
- [ ] Reconnect with capped exponential backoff.
- [ ] On reconnect, refresh from `GET /v1/tracking/fleet/status` to resync.

### 5) Vehicle Detail Tracking Tab
- [ ] Latest state from `GET /v1/tracking/vehicles/{vehicleId}/state`.
- [ ] Timeline from `GET /v1/tracking/vehicles/{vehicleId}/history?limit=&offset=`.
- [ ] Add pagination/infinite-scroll for history list.
- [ ] Show fallback messaging when no records exist.

### 6) Route Administration
- [ ] Route import form posts to `POST /v1/tracking/routes`.
- [ ] Validate GeoJSON before submission (must include LineString).
- [ ] On successful create, refresh `GET /v1/tracking/routes/active`.
- [ ] Show backend `requestId` for admin support diagnostics.

### 7) Error and Resilience Handling
- [ ] `401/403`: redirect to login or show unauthorized state.
- [ ] `429`: apply retry/backoff and communicate rate-limit state.
- [ ] `5xx`/network failures: keep last known fleet snapshot on screen.
- [ ] Track stale data age and show "last updated" timestamp badge.

### 8) DTO and Envelope Validation
- [ ] Validate envelope fields: `success`, `data`, `error`, `requestId`.
- [ ] Treat missing `data` on success as recoverable parse error.
- [ ] Tolerate additive fields in payloads for forward compatibility.
- [ ] Log parse failures with endpoint + requestId context.

### 9) Performance Checklist
- [ ] Batch UI state updates per animation frame where possible.
- [ ] Avoid full list rerenders for single-vehicle delta changes.
- [ ] Keep route geometry memoized after initial load.
- [ ] Cap history request page size to prevent UI jank.

### 10) Observability Checklist
- [ ] Record WebSocket connection lifecycle events.
- [ ] Record delta throughput and client-side drop/error counts.
- [ ] Emit frontend metrics for map render latency and frame rate.
- [ ] Correlate frontend errors with backend `requestId`.

### 11) Backoffice Ready Criteria
- [ ] Map renders initial fleet state from HTTP snapshot.
- [ ] Live deltas update markers without full refresh.
- [ ] Vehicle detail tab shows latest state + paginated history.
- [ ] Route admin can create and see new routes without manual reload.
- [ ] Auth failures and reconnect behavior are user-visible and recoverable.

## Directory-Level Consolidation Outcome
Use this file together with:
- `docs/frontend-implementations/android-routing-integration-consolidated.md`

These two files replace the old split between conceptual routing and implementation details by directly binding Android and backoffice behavior to the current backend code.

## References
- `src/main/kotlin/com/solodev/fleet/modules/tracking/infrastructure/http/TrackingRoutes.kt`
- `src/main/kotlin/com/solodev/fleet/modules/tracking/infrastructure/websocket/RedisDeltaBroadcaster.kt`
- `docs/implementations/phase-7-schematic-visualization-engine.md`
- `docs/frontend-implementations/web-schematic-visualization.md`
- `docs/frontend-implementations/kotlin-multiplatform-prompt.md`
- `docs/sample-payloads/tracking-sample-payloads.md`
