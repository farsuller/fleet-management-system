# Infrastructure: Production-Grade JWT Authentication Guide

**Status**: PROPOSED / PRODUCTION-READY  
**Version**: 1.0  
**Last Updated**: 2026-02-07  
**Objective**: Replace mock tokens with a secured, verifiable, and industry-standard JWT implementation.

---

## 1. Overview

In a production environment, authentication must be stateless, secure, and verifiable. This guide details the implementation of **JWT (JSON Web Tokens)** using Ktor's auth plugin, following "Zero Trust" security principles.

### Key Security Features:
- **Asymmetric Signature**: Using `HS256` (HMAC with SHA-256) or `RS256`.
- **Password Hashing**: Utilizing **BCrypt** or **Argon2** instead of plaintext or mock hashing.
- **Stateless Verification**: The server verifies tokens without database lookups for every request.
- **Claim-Based RBAC**: Roles are embedded in the token for instant permission checks.

---

## 2. Configuration (`application.conf`)

Never hardcode secrets. Use environment variables defined in your configuration file.

```hocon
jwt {
    domain = "https://api.fleet-management.com"
    audience = "fleet-management-api"
    realm = "Fleet Management Access"
    issuer = "fleet-management-auth"
    # Read from environment variable
    secret = ${JWT_SECRET} 
    # Expiration: 24 hours in milliseconds
    expiresIn = 86400000 
}
```

---

## 3. Password Hashing Utility

Stop using mock `hashed_` prefixes. Use **BCrypt** for secure entropy.

### **PasswordHasher.kt**
`src/main/kotlin/com/solodev/fleet/shared/utils/PasswordHasher.kt`

```kotlin
import at.favre.lib.crypto.bcrypt.BCrypt

object PasswordHasher {
    private val hasher = BCrypt.withDefaults()
    private val verifier = BCrypt.verifyer()

    fun hash(password: String): String {
        return hasher.hashToString(12, password.toCharArray())
    }

    fun verify(password: String, hash: String): Boolean {
        return verifier.verify(password.toCharArray(), hash).verified
    }
}
```

---

## 4. Token Service Implementation

The `JwtService` is responsible for generating signed tokens with appropriate claims.

### **JwtService.kt**
`src/main/kotlin/com/solodev/fleet/shared/utils/JwtService.kt`

```kotlin
import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.solodev.fleet.modules.domain.models.User
import java.util.*

class JwtService(
    private val secret: String,
    private val issuer: String,
    private val audience: String,
    private val expiresInMs: Long
) {
    private val algorithm = Algorithm.HMAC256(secret)

    fun generateToken(user: User): String {
        return JWT.create()
            .withSubject("Authentication")
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("id", user.id.value)
            .withClaim("email", user.email)
            // Critical for RBAC: Include roles in the token
            .withArrayClaim("roles", user.roles.map { it.name }.toTypedArray())
            .withExpiresAt(Date(System.currentTimeMillis() + expiresInMs))
            .sign(algorithm)
    }
}
```

---

## 5. Ktor Security Configuration

Update `Security.kt` to perform strict verification.

### **Security.kt**
`src/main/kotlin/com/solodev/fleet/shared/plugins/Security.kt`

```kotlin
fun Application.configureSecurity(jwtService: JwtService) {
    val jwtSecret = environment.config.property("jwt.secret").getString()
    val jwtIssuer = environment.config.property("jwt.issuer").getString()
    val jwtAudience = environment.config.property("jwt.audience").getString()

    install(Authentication) {
        jwt("auth-jwt") {
            realm = environment.config.property("jwt.realm").getString()
            verifier(
                JWT.require(Algorithm.HMAC256(jwtSecret))
                    .withIssuer(jwtIssuer)
                    .withAudience(jwtAudience)
                    .build()
            )
            validate { credential ->
                // Ensure the 'id' claim is present and valid
                if (credential.payload.getClaim("id").asString().isNotEmpty()) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
}
```

---

## 6. Updated Login Use Case

Integrate the `PasswordHasher` and `JwtService` into your application logic.

### **LoginUserUseCase.kt**
```kotlin
class LoginUserUseCase(
    private val repository: UserRepository,
    private val jwtService: JwtService
) {
    suspend fun execute(request: LoginRequest): Pair<User, String> {
        val user = repository.findByEmail(request.email)
            ?: throw AuthenticationException("Invalid credentials")

        // Secure verification
        if (!PasswordHasher.verify(request.passwordRaw, user.passwordHash)) {
            throw AuthenticationException("Invalid credentials")
        }

        val token = jwtService.generateToken(user)
        return Pair(user, token)
    }
}
```

---

## 7. Protecting Routes

Use the `authenticate` wrapper to enforce JWT verification on specific endpoints.

### **UserRoutes.kt**
```kotlin
routing {
    route("/v1/users") {
        // Public routes
        post("/login") { ... }
        post("/register") { ... }

        // Protected routes
        authenticate("auth-jwt") {
            get("/me") {
                val principal = call.principal<JWTPrincipal>()
                val userId = principal?.payload?.getClaim("id")?.asString()
                // ... fetch and return profile
            }
            
            // RBAC Example: Only Admin can delete
            delete("/{id}") {
                val roles = call.principal<JWTPrincipal>()?.payload?.getClaim("roles")?.asList(String::class.java)
                if (roles?.contains("ADMIN") == false) {
                    call.respond(HttpStatusCode.Forbidden)
                    return@delete
                }
                // ... logic
            }
        }
    }
}
```

---

## 8. Production Check-List

1.  **Secret Rotation**: Use a key vault (AWS Secrets Manager, HashiCorp Vault) to manage `JWT_SECRET`.
2.  **HTTPS Only**: Never send tokens over HTTP. Ensure `Secure` flag on cookies if using them.
3.  **Short Expiration**: Set access tokens to expire quickly (15m - 1h) and use **Refresh Tokens** for long sessions.
4.  **Audience Validation**: Always verify the `aud` claim to prevent token misuse across different services.
5.  **Claim Minimization**: Do not put PII (Personally Identifiable Information) like full names or addresses in the token payload.

---

## 9. Testing with cURL

```bash
# 1. Login to get real token
curl -X POST http://localhost:8080/v1/users/login \
  -d '{"email":"admin@fleet.com", "passwordRaw":"secure_pass"}'
# → Returns {"token": "eyJhbGciOiJIUzI1..."}

# 2. Use token in subsequent requests
curl -X GET http://localhost:8080/v1/users/me \
  -H "Authorization: Bearer <your_token_here>"
```
