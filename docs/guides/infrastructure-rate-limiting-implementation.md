# Infrastructure: API Rate Limiting & Abuse Protection Guide

**Status**: PROPOSED / PRODUCTION-READY  
**Version**: 1.1  
**Last Updated**: 2026-02-07  
**Objective**: Protect the Fleet Management API from DDoS attacks, brute-force attempts, and resource exhaustion, aligning with OWASP API4:2023 Resource Consumption.

---

## 1. Overview

Rate limiting is a critical security layer that controls the frequency of requests to the API. Following the principles in `@[skills/api-patterns/rate-limiting.md]`, we implement protection at multiple levels to ensure system availability and fairness.

### Goals:
- **DDoS Mitigation**: Prevent high-volume traffic from crashing the service.
- **Brute-Force Protection**: Limit attempts on sensitive endpoints (Login, Register).
- **Resource Fairness**: Ensure API resources are available to all users.
- **Cost Control**: Manage infrastructure load and related costs.

---

## 2. Strategy Selection

Based on the `@[skills/api-patterns/rate-limiting.md]`, we have selected the **Token Bucket** strategy for the Fleet Management System.

| Strategy | Why we use it |
|----------|---------------|
| **Token Bucket** | Allows for **traffic bursts** (e.g., loading a dashboard with many sub-requests) while maintaining a strict average rate over time. Ideal for modern RESTful APIs. |

---

## 3. Technology: Ktor Rate Limit Plugin

We use the official [Ktor Rate Limit](https://ktor.io/docs/rate-limit.html) plugin.

### Essential Dependencies:
```kotlin
implementation("io.ktor:ktor-server-rate-limit:$ktor_version")
```

---

## 4. Configuration & Registration

### **Why This Matters**:
We define multiple "Buckets" of rate limits. This tiered approach allows us to be generous with trusted, logged-in users while being extremely strict with anonymous traffic and sensitive endpoints like Login/Register.

### **RateLimiting.kt**
`src/main/kotlin/com/solodev/fleet/shared/plugins/RateLimiting.kt`

```kotlin
import io.ktor.server.application.*
import io.ktor.server.plugins.ratelimit.*
import io.ktor.server.response.*
import io.ktor.http.*
import kotlin.time.Duration.Companion.minutes

/**
 * Configures the rate limiting strategy for the API.
 * Uses a multi-tiered approach based on user identity and endpoint sensitivity.
 */
fun Application.configureRateLimiting() {
    install(RateLimit) {
        // 1. Global Rate Limit: Acts as a final safety net for the entire server.
        register(RateLimitName("global")) {
            rateLimiter(limit = 1000, refillPeriod = 1.minutes)
        }

        // 2. Public API: IP-based limit for guest users.
        // Prevents bulk scraping of public resources.
        register(RateLimitName("public_api")) {
            rateLimiter(limit = 100, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }

        // 3. Sensitive Endpoints: Extremely strict limits to block brute-force attacks.
        register(RateLimitName("auth_strict")) {
            rateLimiter(limit = 5, refillPeriod = 1.minutes)
            requestKey { call -> call.request.origin.remoteHost }
        }

        // 4. Authenticated API: User-based limiting.
        // We trust logged-in users more, so they get a higher quota.
        register(RateLimitName("authenticated_api")) {
            rateLimiter(limit = 500, refillPeriod = 1.minutes)
            requestKey { call -> 
                // Why: Grouping by ID prevents a user from bypassing limits by switching IPs
                call.principal<JWTPrincipal>()?.payload?.getClaim("id")?.asString() 
                    ?: call.request.origin.remoteHost
            }
        }
    }
}
```

---

## 5. Customizing the Response (RFC 6585)

### **Why This Matters**:
Providing rate limit headers (RFC 6585) allows legitimate clients to "back off" gracefully before they hit the hard reset. It improves the Developer Experience (DX) of your API consumers.

```kotlin
install(RateLimit) {
    register(RateLimitName("global")) {
        rateLimiter(limit = 1000, refillPeriod = 1.minutes)
        
        modifyResponse { call, state ->
            // Why: These headers inform the client how many requests they have left
            // and when their quota will be renewed.
            call.response.headers.append("X-RateLimit-Limit", state.limit.toString())
            call.response.headers.append("X-RateLimit-Remaining", state.remaining.toString())
            call.response.headers.append("X-RateLimit-Reset", state.resetIn.toString())
        }
    }
}
```

---

## 6. Bypass Protection

### **Why This Matters**:
Basic rate limiting is easy to circumvent. We implement these "Gotchas" to ensure the protection is meaningful in a production environment.

1.  **Header Trust**: **Gotcha:** If you use a load balancer, `remoteHost` might always be the balancer's IP. You MUST configure Ktor to trust `X-Forwarded-For` from known proxies.
2.  **Scope Variety**: By switching from IP to User ID as soon as a user logs in, we stop botnets that rotate through many residential IPs.
3.  **Endpoint Shadowing**: We apply limits to route groups to stop attackers from trying variations like `/v1/users` and `/v1/users/` to reset counts.

---

## 7. Scaling with Redis

### **Why This Matters**:
Memory-based limiting has a major flaw: in a horizontal cluster (multiple server instances), a user can hit Instance A, then Instance B, effectively doubling or tripling their quota. **Redis** provides a shared count that all server nodes respect.

---

## 8. Summary Table of Recommended Limits

| Usage Class | Scope | Limit | Refill | OWASP Focus |
|-------------|-------|-------|--------|-------------|
| **Anonymous** | IP | 60 | 1 min | API4 (Basic) |
| **Authenticated** | User ID | 300 | 1 min | API4 (Advanced) |
| **Authentication** | IP | 5 | 1 min | API2 (Brute-force) |
| **Search/Filter** | IP | 20 | 1 min | Heavy queries |
| **Health Check** | Global | ∞ | N/A | Excluded |

---

## 9. Security Testing Checklist

Based on `@[skills/api-patterns/security-testing.md]`:
- [ ] **Existence**: Verify every public endpoint has a limit.
- [ ] **Bypass**: Test if changing `Accept` headers or HTTP methods bypasses the limit.
- [ ] **IP-Rotation**: Verify that hitting the limit on one IP doesn't automatically block others (Proper Scope).
- [ ] **429 Format**: Verify the JSON error response matches the `ApiResponse` standard.
