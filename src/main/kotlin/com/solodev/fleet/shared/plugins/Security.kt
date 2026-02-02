package com.solodev.fleet.shared.plugins

import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*

/**
 * Configures application security.
 *
 * Implements JWT (JSON Web Token) authentication.
 * - Sets up the JWT authentication provider.
 * - Defines validation logic for identifying authenticated users.
 *
 * Note: This scaffolding uses a placeholder validation block. Real implementations must verify
 * signatures/audiences strictly.
 */
fun Application.configureSecurity() {
    install(Authentication) {
        jwt {
            // Placeholder: In a real app, strict verification of issuer/audience
            // and signature against a public key or secret is required.
            // For Phase 1 skeleton, we set up the structure.

            // val jwtAudience = environment.config.property("jwt.audience").getString()
            // val jwtDomain = environment.config.property("jwt.domain").getString()
            // realm = environment.config.property("jwt.realm").getString()

            validate { credential ->
                if (credential.payload.getClaim("id").asString() != "") {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
        }
    }
}
