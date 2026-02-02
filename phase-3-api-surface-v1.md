# Phase 3 — API surface v1

## Status

- Overall: **Not Started**

## Purpose

Deliver the **Presentation/Web Adapter** layer (HTTP APIs) for each domain. These adapters invoke Application Use Cases and return consistent REST responses.

## Depends on

- Phase 1 architecture skeleton (routing/auth/error/observability baselines)
- Phase 2 schema v1 (persistence ready for real endpoints)

## API decisions (apply consistently)

- **Style**: REST (resource-based, nouns, plural, shallow nesting)
- **Versioning**: URI versioning (`/v1/...`) unless internal-only use proves otherwise
- **Response format**: single consistent envelope across services
- **Errors**: stable error codes + user-safe messages; include `requestId`; never leak internals
- **Auth**: OAuth2 + JWT; RBAC via claims; 401 vs 403 per policy
- **Rate limiting**: define limits per actor (user/service) and return 429 when exceeded

## Implementation breakdown (with statuses)

| Item | Status | Notes / definition |
|------|--------|-------------------|
| Define endpoint naming + versioning rules | Not Started | `/v1/vehicles`, `/v1/rentals`, no verbs in paths |
| Define response envelope | Not Started | Choose and document `{ success, data, error, requestId }` (or alternative) |
| Define error model | Not Started | Error codes, field errors for 422, mapping rules for 400/401/403/404/409/422/429/500 |
| Define idempotency behavior | Not Started | Idempotency keys for POST where duplicates are dangerous (reserve, capture payment, post ledger) |
| Vehicles endpoints | Not Started | Register/update, state transitions, odometer updates |
| Rentals endpoints | Not Started | Quote, reserve, activate, complete/cancel, availability |
| Maintenance endpoints | Not Started | Schedule/start/complete, parts used, costs |
| Users/Staff endpoints | Not Started | Role/permission management (if not external IdP), staff profiles |
| Accounting endpoints | Not Started | Charges/payments, invoice issuance, ledger posting/read views |
| Pagination strategy | Not Started | Offset vs cursor per resource; document selection and defaults |
| OpenAPI documentation | Not Started | Endpoint schemas + examples + auth + error format + rate limiting |
| Security testing plan | Not Started | Auth/authz tests (OWASP API Top 10 focus) |

## Definition of Done (Phase 3)

- Each domain has a minimal v1 API with consistent conventions and auth.
- Responses and errors are consistent across modules/services.
- OpenAPI docs exist with examples and error formats.

## Code impact (expected repo artifacts)

- **Code expected in this phase** (routes/controllers + request/response DTOs).
- Suggested artifacts:
  - `src/main/kotlin/.../routes/`
  - `src/main/kotlin/.../http/dto/`
  - `src/main/resources/openapi.*` (or generated OpenAPI)
  - `docs/api/` (conventions, error codes, idempotency, rate limits)

## References
- `fleet-management-plan.md`
- `skills/backend-development/SKILL.md`
- `skills/clean-code/SKILL.md`
- `skills/api-patterns/rest.md`
- `skills/api-patterns/response.md`
- `skills/api-patterns/versioning.md`
- `skills/api-patterns/auth.md`
- `skills/api-patterns/rate-limiting.md`
- `skills/api-patterns/documentation.md`
