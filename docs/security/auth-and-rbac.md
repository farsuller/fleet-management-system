# Authentication & RBAC

## Authentication

- Use **OAuth 2.0** for authentication.
- Use **JWT** for authorization in backend services.

### JWT principles

- Always verify signature and expiration.
- Keep claims minimal (no sensitive data).
- Prefer short-lived access tokens + refresh tokens (handled by the IdP).

## Authorization (RBAC)

Authorization is enforced at two layers:

- **API layer**: coarse-grained RBAC checks (does the caller have permission?)
- **Domain layer**: business-rule checks (is this action allowed in this state?)

### Permission naming

Use dotted, action-oriented permissions:

- `vehicles.read`
- `vehicles.update`
- `vehicles.decommission`
- `rentals.reserve`
- `rentals.activate`
- `maintenance.complete`
- `accounting.post`

### Claims (proposed)

Minimum required claims for services:

- `sub` (user id)
- `exp` (expiry)
- `iss` (issuer)
- `aud` (audience)
- `roles` (optional, if permissions are role-derived)
- `permissions` (preferred for direct RBAC checks)

Example (illustrative):

```json
{
  "sub": "user-uuid",
  "iss": "https://idp.example",
  "aud": "fleet-management",
  "exp": 1735689600,
  "permissions": ["rentals.reserve", "vehicles.read"]
}
```

## 401 vs 403 rules

- `401 UNAUTHORIZED`: missing/invalid/expired token.
- `403 FORBIDDEN`: valid token, but missing permission for the action.

## Audit fields

When writing data, capture actor info where applicable:

- `created_by`, `updated_by` (UUID)
- `requestId` in logs for traceability
