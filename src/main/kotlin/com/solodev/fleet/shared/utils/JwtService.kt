package com.solodev.fleet.shared.utils

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import java.util.*

class JwtService(
        private val secret: String,
        private val issuer: String,
        private val audience: String,
        private val expiresInMs: Long
) {
    private val algorithm = Algorithm.HMAC256(secret)

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
}
