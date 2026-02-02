# Event Catalog v1

This is the initial domain event set. Events are published via **outbox** and consumed with **idempotency** (at-least-once delivery).

## Conventions

- Event names are **past tense**.
- Each event has:
  - `eventId` (UUID)
  - `eventType` (string)
  - `occurredAt` (timestamp)
  - `aggregateType` + `aggregateId`
  - `payload` (versioned schema)
- Keys/partitioning:
  - Key by `aggregateId` to preserve per-aggregate ordering where needed.

## Events

### Fleet (Vehicles)

- `VehicleRegistered`
- `VehicleStateChanged`
- `VehicleOdometerUpdated`

### Rentals

- `RentalQuoted`
- `RentalReserved`
- `RentalActivated`
- `RentalCompleted`
- `RentalCancelled`

### Maintenance

- `MaintenanceScheduled`
- `MaintenanceStarted`
- `MaintenanceCompleted`
- `MaintenanceCancelled`

### Accounting

- `InvoiceIssued`
- `PaymentCaptured`
- `LedgerPosted`

## Versioning strategy

- Add `schemaVersion` in the payload and evolve compatibly.
- Breaking changes require new event type or a new schema version with compatibility strategy.

## Retry + DLQ policy (minimum)

- Retry transient failures with backoff (bounded attempts).
- Send poison messages to DLQ when:
  - schema is invalid/unparseable
  - the handler consistently fails due to bad data
- DLQ message must include:
  - original topic/partition/offset
  - error summary
  - original payload

## Replay runbook (minimum)

- Replays must be safe because consumers are idempotent.
- Steps:
  - identify time window / offsets
  - reprocess from offsets (or republish from DLQ)
  - monitor lag and error rate
