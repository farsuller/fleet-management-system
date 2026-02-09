package com.solodev.fleet.shared.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.jwt.JWTPrincipal
import io.ktor.server.auth.principal
import io.ktor.server.plugins.origin
import io.ktor.server.plugins.ratelimit.*
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Configures the rate limiting strategy for the API. Uses a multi-tiered approach based on user
 * identity and endpoint sensitivity.
 */
fun Application.configureRateLimiting() {
    install(RateLimit) {
        // 1. Global Rate Limit: Acts as a final safety net for the entire server.
        register {
            rateLimiter(limit = 5, refillPeriod = 60.seconds)
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
