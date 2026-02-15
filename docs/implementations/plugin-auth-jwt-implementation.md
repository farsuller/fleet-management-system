# Authentication & Authorization System (JWT)
**Status**: ACTIVE / SECURED
**Version**: 1.1.0
**Last Updated**: 2026-02-15

## 1. Executive Summary
This document details the actual implementation of the JWT-based Authentication and Authorization system for the Fleet Management system. The system enforces **stateless** authentication using signed JWTs and **Role-Based Access Control (RBAC)** via a custom Ktor plugin and token claims.

---

## 2. Architecture & Flow

### 2.1 Authentication Flow
1.  **Client** sends credentials to `POST /v1/users/login`.
2.  **Server** validates credentials and issues a **Signed JWT** containing:
    *   `sub`: "Authentication"
    *   `id`: User UUID
    *   `email`: User Email
    *   `roles`: User Roles (Array of strings matching `UserRole` enum)
3.  **Client** includes `Authorization: Bearer <token>` in headers for protected requests.

---

## 3. Core Components Implementation

### 3.1 JwtService ([JwtService.kt](file:///e:/Antigravity%20Projects/fleet-management/src/main/kotlin/com/solodev/fleet/shared/utils/JwtService.kt))
The `JwtService` handles the creation of tokens using `com.auth0:java-jwt`.

```kotlin
fun generateToken(id: String, email: String, roles: List<String>): String {
    return JWT.create()
            .withSubject("Authentication")
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("id", id)
            .withClaim("email", email)
            .withArrayClaim("roles", roles.toTypedArray())
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + expiresInMs))
            .sign(algorithm)
}
```

### 3.2 Role System ([Security.kt](file:///e:/Antigravity%20Projects/fleet-management/src/main/kotlin/com/solodev/fleet/shared/plugins/Security.kt))
We define a concrete set of roles using the `UserRole` enum:

```kotlin
enum class UserRole {
    ADMIN,             // Full system access (Bypasses role checks)
    FLEET_MANAGER,     // Manage vehicles and inventory
    CUSTOMER_SUPPORT,  // View customers and handle basic issues
    RENTAL_AGENT,      // Manage active rental lifecycles
    CUSTOMER           // Basic self-service access
}
```

### 3.3 Authorization Plugin
A custom route-scoped plugin `Authorization` intercepts requests after authentication to verify roles.

*   **Principal Extension**: `JWTPrincipal.getRoles()` maps token claims to `UserRole`.
*   **Admin Bypass**: If a user has the `ADMIN` role, they are granted access regardless of the specific roles required for a route.

---

## 4. Configuration (`application.yaml`)

```yaml
jwt:
    secret: ${JWT_SECRET:-"change-me-in-production..."}
    issuer: "http://0.0.0.0:8080/"
    audience: "http://0.0.0.0:8080/"
    realm: "Fleet Management Access"
    expiresIn: 3600000 # 1 hour
```

---

## 5. API Usage & Protection

### 5.1 Protecting Routes
To protect a route, use the `authenticate("auth-jwt")` block. To restrict by role, use the `withRoles` extension.

```kotlin
fun Route.vehicleRoutes() {
    authenticate("auth-jwt") {
        route("/v1/vehicles") {
            // Accessible by any authenticated user
            get { ... }

            // Restrict specific actions by role
            withRoles(UserRole.ADMIN, UserRole.FLEET_MANAGER) {
                post { ... }
                patch("/{id}") { ... }
                delete("/{id}") { ... }
            }
        }
    }
}
```

### 5.2 Implementation Details of `withRoles`
```kotlin
fun Route.withRoles(vararg roles: UserRole, build: Route.() -> Unit): Route {
    install(Authorization) { requiredRoles = roles.toList() }
    build()
    return this
}
```

---

## 6. Security Checklist

1.  ✅ **Algorithm Enforced**: HMAC256 only.
2.  ✅ **RBAC Implementation**: Enforced via `Authorization` plugin.
3.  ✅ **Admin Override**: Built into the security logic.
4.  ✅ **Stateless**: All identity information is carried in the JWT.
5.  [ ] **Production Secrets**: Ensure `JWT_SECRET` is set via environment variables.
6.  [ ] **HTTPS**: Mandatory for token transit security.

