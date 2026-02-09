# Authentication & Authorization System (JWT)
**Status**: ACTIVE / SECURED
**Version**: 1.0.0
**Last Updated**: 2026-02-08

## 1. Executive Summary
This document details the complete simplified implementation of the JWT-based Authentication and Authorization system for the Fleet Management module. It combines the architectural design with the actual codebase status. The system enforces **stateless** authentication using signed JWTs, **secure password hashing** using BCrypt, and **Role-Based Access Control (RBAC)** via token claims.

---

## 2. Architecture & Flow

### 2.1 Authentication Flow
1.  **Client** sends credentials (`email`, `password`) to `POST /v1/users/login`.
2.  **Server** validates:
    *   **User Existence**: Checks database.
    *   **Email Verification**: Ensures `isVerified` is true.
    *   **Password**: Verifies against stored BCrypt hash.
3.  **Server** issues a **Signed JWT** containing:
    *   `sub`: "Authentication"
    *   `id`: User UUID
    *   `email`: User Email
    *   `roles`: User Roles (Array)
    *   `exp`: Expiration (Default 1 hour)
4.  **Client** receives `{ "success": true, "data": { "token": "...", "user": {...} } }`.
5.  **Client** includes `Authorization: Bearer <token>` in headers for all subsequent protected requests.

### 2.2 Registration & Verification Flow
1.  **Registration**: User created with `isVerified = false`.
2.  **Token Generation**: UUID token generated (24h expiry) and stored in `verification_tokens` table.
3.  **Delivery**: Token is logged to **Application Console** (Simulation mode).
4.  **Verification**: Client accesses `GET /v1/auth/verify?token=<UUID>`.
5.  **Activation**: User `isVerified` set to `true`.

---

## 3. Core Components Implementation

### 3.1 Security Utilities (`com.solodev.fleet.shared.utils`)
*   **`PasswordHasher`**:
    *   **Library**: `at.favre.lib:bcrypt` (v0.10.2).
    *   **Strength**: 12 Rounds.
    *   **Function**: Handles salt generation and verification automatically.
*   **`JwtService`**:
    *   **Library**: `com.auth0:java-jwt`.
    *   **Algorithm**: HMAC256.
    *   **Role**: Signs tokens and embeds claims (`id`, `email`, `roles`).

### 3.2 Ktor Security Plugin (`com.solodev.fleet.shared.plugins`)
*   **Configuration Name**: `auth-jwt`
*   **Logic**:
    *   Intercepts `Authorization: Bearer` header.
    *   Verifies Signature (HMAC256) using `jwt.secret`.
    *   Verifies Issuer and Audience.
    *   Validates `id` claim presence.
    *   Rejects `none` algorithm.

---

## 4. Configuration

### 4.1 Infrastructure Config (`application.yaml`)
```yaml
jwt:
    secret: "change-me-in-production..." # Use Env Var!
    issuer: "http://0.0.0.0:8080/"
    audience: "http://0.0.0.0:8080/"
    realm: "Fleet Management Access"
    expiresIn: 3600000 # 1 hour
```

### 4.2 Test Configuration
In `ApplicationTest` and `MigrationTest`, `MapApplicationConfig` is used to manually inject these properties to mock the environment, ensuring tests run in isolation with an H2 database.

---

## 5. API Usage & Protection

### 5.1 Endpoint Requirements
| Endpoint | Method | Requires Token? | Note |
| :--- | :--- | :--- | :--- |
| `/v1/users/register` | POST | **NO** | Public. |
| `/v1/users/login` | POST | **NO** | Public (Returns the token). |
| `/v1/auth/verify` | GET | **NO** | Uses **URL Token** (`?token=...`). |
| `/v1/users` | GET | **YES** | **Bearer Token** Required. |
| `/v1/users/{id}` | GET/PATCH/DEL | **YES** | **Bearer Token** Required. |
| `/v1/users/roles` | GET | **YES** | **Bearer Token** Required. |
| `/v1/users/{id}/roles` | POST | **YES** | **Bearer Token** Required. |

### 5.2 Implementation Guide: Enforcing Protection
To enforce security, wrap sensitive routes in the `authenticate("auth-jwt") { ... }` block.

**User Module (Mixed Public/Private)**
```kotlin
fun Route.userRoutes(...) {
    route("/v1/users") {
        // Public
        post("/register") { ... }
        post("/login") { ... }

        // Protected
        authenticate("auth-jwt") {
            get { ... } // List Users
            route("/{id}") { ... }
        }
    }
}
```

**Other Modules (Fully Protected Example)**
```kotlin
fun Route.vehicleRoutes(...) {
    authenticate("auth-jwt") {
        route("/v1/vehicles") {
            get { ... }
            post { ... }
        }
    }
}
```

---

## 6. Database & Models

### 6.1 Domain Models
*   **`User`**: Includes `passwordHash` (String), `isVerified` (Boolean), `isActive` (Boolean).
*   **`VerificationToken`**: Links to `User`, includes `expiresAt` (Instant).

### 6.2 Schema
*   **`users`**: Standard user table.
*   **`verification_tokens`**: Stores UUID tokens for email verification logic.
*   **Deviation**: Repository implementations (`*Impl`) currently reside in `domain.repository` (Pending move to `infrastructure.persistence`).

---

## 7. Security Checklist & Production Readiness
(Derived from OWASP API Security Best Practices)

1.  ✅ **Algorithm Enforced**: `HS256` only (no `none`).
2.  ✅ **Strong Hashing**: BCrypt with 12 rounds.
3.  ✅ **Stateless**: No session storage in DB (except refresh tokens, if added).
4.  ✅ **Route Protection**: Enforced on ALL modules (Users, Vehicles, Rentals, Customers, Maintenance).
5.  [ ] **HTTPS**: Must be enabled in production.
6.  [ ] **Secret Rotation**: Use Environment Variables for `jwt.secret`.
7.  [ ] **Email Service**: Replace Console Logging with real SMTP service (SendGrid/AWS SES).

---
