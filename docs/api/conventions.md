# API Conventions

These conventions apply across all modules/services.

## Versioning

- Use **URI versioning**: `/v1/...`
- Backward-compatible changes do not require a new version; breaking changes do.

## Resource naming

- Use **nouns**, not verbs.
- Use **plural** resources: `/vehicles`, `/rentals`
- Lowercase with hyphens: `/staff-profiles`
- Keep nesting shallow (max ~3 levels):
  - Good: `/vehicles/{vehicleId}/odometer-readings`
  - Avoid: `/fleet/vehicles/{id}/rentals/{rentalId}/payments/...`

## HTTP methods

- `GET` read (idempotent)
- `POST` create (non-idempotent unless an idempotency key is required)
- `PUT` replace (idempotent)
- `PATCH` partial update (treat as non-idempotent unless explicitly designed otherwise)
- `DELETE` remove (idempotent)

## Status codes

- `200` success (read/update)
- `201` created
- `204` success with no body
- `400` malformed request
- `401` unauthenticated
- `403` authenticated but unauthorized
- `404` not found
- `409` conflict (duplicate / invalid state transition)
- `422` validation error (well-formed request, invalid data)
- `429` rate limited
- `500` server error

## Pagination defaults

- Use **offset pagination** by default for small/medium datasets.
- Use **cursor/keyset pagination** for large datasets or frequently changing lists.
- Always document:
  - default limit
  - max limit
  - sort order

## Rate limiting

- Apply per actor (user/service).
- When rate limited:
  - return `429`
  - include:
    - `X-RateLimit-Limit`
    - `X-RateLimit-Remaining`
    - `X-RateLimit-Reset`
