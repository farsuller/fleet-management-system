package com.solodev.fleet.shared.utils

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.Date

class JwtService(
    private val secret: String,
    private val issuer: String,
    private val audience: String,
    private val expiresInMs: Long,
) {
    private val algorithm = Algorithm.HMAC256(secret)

    // Access token (short-lived, default expiresInMs)
    fun generateToken(
        id: String,
        email: String,
        roles: List<String>,
    ): String =
        JWT
            .create()
            .withSubject("Authentication")
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("id", id)
            .withClaim("email", email)
            .withClaim("type", "access")
            .withArrayClaim("roles", roles.toTypedArray())
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + expiresInMs))
            .sign(algorithm)

    // Refresh token (long-lived, 30 days)
    fun generateRefreshToken(
        id: String,
        email: String,
        roles: List<String>,
    ): String =
        JWT
            .create()
            .withSubject("Refresh")
            .withIssuer(issuer)
            .withAudience(audience)
            .withClaim("id", id)
            .withClaim("email", email)
            .withClaim("type", "refresh")
            .withArrayClaim("roles", roles.toTypedArray())
            .withIssuedAt(Date())
            .withExpiresAt(Date(System.currentTimeMillis() + 30L * 24 * 60 * 60 * 1000))
            .sign(algorithm)

    /**
     * Validates a JWT and returns the user ID claim, or null if invalid/expired.
     * Intended only for the refresh-token endpoint (unprotected route).
     */
    fun validateRefreshToken(token: String): Triple<String, String, List<String>>? =
        try {
            val verifier =
                JWT
                    .require(algorithm)
                    .withIssuer(issuer)
                    .withAudience(audience)
                    .withClaim("type", "refresh")
                    .build()
            val decoded = verifier.verify(token)
            val id = decoded.getClaim("id").asString()
            val email = decoded.getClaim("email").asString()
            val roles = decoded.getClaim("roles").asList(String::class.java) ?: emptyList()
            Triple(id, email, roles)
        } catch (e: Exception) {
            null
        }
}
