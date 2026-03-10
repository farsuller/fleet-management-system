package com.solodev.fleet.shared.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.solodev.fleet.shared.models.ApiResponse
import io.ktor.http.*
import io.ktor.http.auth.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.response.*
import io.ktor.server.routing.Route
import io.ktor.util.pipeline.*

/** Enumeration of all valid back-office staff roles in the system.
 *  CUSTOMER and DRIVER are separate domain entities with their own tables.
 */
enum class UserRole {
    ADMIN,           // Full system access
    FLEET_MANAGER,   // Manage vehicles and inventory
    CUSTOMER_SUPPORT, // Handle customer inquiries and basic support
    RENTAL_AGENT,    // Manage active rental lifecycles
}

/** Extension to extract and map roles from the JWT 'roles' claim into our UserRole enum. */
fun JWTPrincipal.getRoles(): List<UserRole> {
    return payload.getClaim("roles").asList(String::class.java)?.mapNotNull {
        runCatching { UserRole.valueOf(it.uppercase()) }.getOrNull()
    }
            ?: emptyList()
}

/** Plugin configuration for Authorization. */
class AuthorizationConfig {
    var requiredRoles: List<UserRole> = emptyList()
}

/**
 * Route-scoped authorization wrapper. Intercepts requests and verifies that the authenticated user
 * has at least one of the required roles.
 */
val Authorization =
        createRouteScopedPlugin(
                name = "Authorization",
                createConfiguration = ::AuthorizationConfig
        ) {
            val roles = pluginConfig.requiredRoles

            on(AuthenticationChecked) { call ->
                val principal = call.principal<JWTPrincipal>()
                val userRoles = principal?.getRoles() ?: emptyList()

                if (roles.isNotEmpty() &&
                                roles.none { it in userRoles } &&
                                UserRole.ADMIN !in userRoles
                ) {
                    call.respond(
                            HttpStatusCode.Forbidden,
                            ApiResponse.error(
                                    code = "FORBIDDEN",
                                    message = "You do not have the required permissions.",
                                    requestId = call.requestId
                            )
                    )
                }
            }
        }

/** Extension to apply authorization to a route. */
fun Route.withRoles(vararg roles: UserRole, build: Route.() -> Unit): Route {
    install(Authorization) { requiredRoles = roles.toList() }
    build()
    return this
}

fun Application.configureSecurity() {
    val jwtSecret = environment.config.property("jwt.secret").getString()
    val jwtIssuer = environment.config.property("jwt.issuer").getString()
    val jwtAudience = environment.config.property("jwt.audience").getString()
    val jwtRealm = environment.config.property("jwt.realm").getString()

    install(Authentication) {
        jwt("auth-jwt") {
            realm = jwtRealm
            // Also accept token via query parameter to support browser WebSocket connections,
            // which cannot send custom headers during the upgrade handshake.
            authHeader { call ->
                call.request.parseAuthorizationHeader()
                    ?: call.request.queryParameters["token"]?.let { token ->
                        HttpAuthHeader.Single("Bearer", token)
                    }
            }
            verifier(
                    JWT.require(Algorithm.HMAC256(jwtSecret))
                            .withIssuer(jwtIssuer)
                            .withAudience(jwtAudience)
                            .build()
            )
            validate { credential ->
                val id = credential.payload.getClaim("id").asString()
                if (!id.isNullOrEmpty()) {
                    JWTPrincipal(credential.payload)
                } else {
                    null
                }
            }
            challenge { defaultScheme, realm ->
                call.respond(
                        HttpStatusCode.Unauthorized,
                        ApiResponse.error(
                                code = "UNAUTHORIZED",
                                message = "Token is invalid or expired. Please log in again.",
                                requestId = call.requestId
                        )
                )
            }
        }
    }
}
