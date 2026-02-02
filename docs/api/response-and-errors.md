# Response & Error Format

Pick one format and keep it consistent across all APIs.

## Response envelope (chosen)

All responses return an envelope:

```json
{
  "success": true,
  "data": {},
  "requestId": "req_..."
}
```

## Error envelope (chosen)

```json
{
  "success": false,
  "error": {
    "code": "RENTAL_OVERLAP",
    "message": "Vehicle is not available for the requested period.",
    "details": [
      { "field": "startAt", "reason": "overlaps_existing_rental" }
    ]
  },
  "requestId": "req_..."
}
```

### Rules

- `code` is stable and programmatic.
- `message` is safe for end users (no internals).
- `details` is optional and may include field-level errors.
- Always include `requestId` for support.

## Common error codes (initial set)

- `VALIDATION_ERROR` (422)
- `UNAUTHENTICATED` (401)
- `FORBIDDEN` (403)
- `NOT_FOUND` (404)
- `CONFLICT` (409)
- `RENTAL_OVERLAP` (409)
- `RATE_LIMITED` (429)
- `INTERNAL_ERROR` (500)

## Mapping guide (summary)

- Invalid JSON / missing required structure → `400` + `VALIDATION_ERROR` (or a distinct `BAD_REQUEST`)
- Field validation failures → `422` + `VALIDATION_ERROR` with `details`
- Missing/invalid JWT → `401` + `UNAUTHENTICATED`
- Missing permission/role → `403` + `FORBIDDEN`
- Duplicate resource / invalid state transition → `409` + specific code (e.g., `RENTAL_OVERLAP`)
- Rate limit exceeded → `429` + `RATE_LIMITED`

## Success responses

- Prefer returning the created resource on `201`.
- Use `204` only when the client does not need a body.
