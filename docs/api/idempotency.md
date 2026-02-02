# Idempotency

Idempotency prevents duplicate side effects when clients retry requests (timeouts, network errors).

## When required

Require an idempotency key for requests that create financial or booking side effects, e.g.:

- Reserve rental
- Capture payment
- Post ledger entry
- Issue invoice

## API contract

- Clients send: `Idempotency-Key: <uuid>`
- Server behavior:
  - Same key + same actor + same endpoint → returns the same outcome
  - Same key reused with a different payload → `409 CONFLICT`

## Storage

- Store idempotency records in PostgreSQL (source of truth).
- Keep enough data to re-return the prior response (or a stable reference).

## Error mapping

- Missing idempotency key when required → `400`
- Key conflict (payload mismatch) → `409`
