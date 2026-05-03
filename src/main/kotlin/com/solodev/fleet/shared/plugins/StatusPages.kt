package com.solodev.fleet.shared.plugins

import com.solodev.fleet.shared.exceptions.ConflictException
import com.solodev.fleet.shared.exceptions.ForbiddenException
import com.solodev.fleet.shared.exceptions.NotFoundException
import com.solodev.fleet.shared.exceptions.RateLimitException
import com.solodev.fleet.shared.exceptions.UnauthenticatedException
import com.solodev.fleet.shared.exceptions.ValidationException
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.models.ErrorDetail
import com.solodev.fleet.shared.models.FieldError
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import org.jetbrains.exposed.exceptions.ExposedSQLException
import org.postgresql.util.PSQLException

/** Inline helper — zero allocation overhead, reduces bytecode duplication. */
private inline fun errorResponse(
    code: String,
    message: String,
    requestId: String,
    details: List<FieldError>? = null,
) = ApiResponse<Nothing>(
    success = false,
    error = ErrorDetail(code = code, message = message, details = details),
    requestId = requestId,
)

/**
 * Configures global error handling.
 *
 * Maps domain exceptions to appropriate HTTP status codes and standardized error responses. All
 * errors follow the API response envelope format with proper error codes and request IDs.
 */
fun Application.configureStatusPages() {
    install(StatusPages) {
        // Domain-specific exceptions
        exception<NotFoundException> { call, cause ->
            call.respond(
                HttpStatusCode.NotFound,
                errorResponse(cause.errorCode, cause.message ?: "Resource not found", call.requestId),
            )
        }

        exception<ValidationException> { call, cause ->
            call.respond(
                HttpStatusCode.UnprocessableEntity,
                errorResponse(
                    cause.errorCode,
                    cause.message ?: "Validation failed",
                    call.requestId,
                    cause.fieldErrors.map { (field, reason) -> FieldError(field, reason) },
                ),
            )
        }

        exception<ConflictException> { call, cause ->
            call.respond(
                HttpStatusCode.Conflict,
                errorResponse(cause.errorCode, cause.message ?: "Conflict detected", call.requestId),
            )
        }

        exception<UnauthenticatedException> { call, cause ->
            call.respond(
                HttpStatusCode.Unauthorized,
                errorResponse(cause.errorCode, cause.message ?: "Authentication required", call.requestId),
            )
        }

        exception<ForbiddenException> { call, cause ->
            call.respond(
                HttpStatusCode.Forbidden,
                errorResponse(cause.errorCode, cause.message ?: "Insufficient permissions", call.requestId),
            )
        }

        status(HttpStatusCode.TooManyRequests) { call, _ ->
            val retryAfter = call.response.headers["Retry-After"]
            // Bridge the automatic 429 status to our Domain Exception
            throw RateLimitException("Too many requests. Please wait $retryAfter seconds.")
        }

        exception<RateLimitException> { call, cause ->
            call.respond(
                HttpStatusCode.TooManyRequests,
                errorResponse(cause.errorCode, cause.message ?: "Rate limit exceeded", call.requestId),
            )
        }

        // Serialization and Bad Request handling
        exception<BadRequestException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                errorResponse("BAD_REQUEST", cause.message ?: "Invalid request format", call.requestId),
            )
        }

        exception<kotlinx.serialization.SerializationException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                errorResponse("SERIALIZATION_ERROR", "Malformed JSON or type mismatch: ${cause.message}", call.requestId),
            )
        }

        exception<IllegalArgumentException> { call, cause ->
            call.respond(
                HttpStatusCode.BadRequest,
                errorResponse("BAD_REQUEST", cause.message ?: "Invalid argument", call.requestId),
            )
        }

        // Catch-all for unexpected errors (500)
        exception<Throwable> { call, cause ->
            // Log the full error for debugging but don't expose internals to client
            call.application.log.error("Unhandled exception", cause)

            call.respond(
                HttpStatusCode.InternalServerError,
                errorResponse("INTERNAL_ERROR", "An unexpected error occurred", call.requestId),
            )
        }

        status(HttpStatusCode.NotFound) { call, _ ->
            call.respond(
                HttpStatusCode.NotFound,
                errorResponse("NOT_FOUND", "The requested resource or endpoint was not found", call.requestId),
            )
        }
        exception<ExposedSQLException> { call, cause ->
            val psqlException = cause.cause as? PSQLException
            if (psqlException?.sqlState == "23505") {
                call.respond(
                    HttpStatusCode.Conflict,
                    errorResponse(
                        "CONFLICT",
                        "Resource already exists (duplicate key violation): ${
                            psqlException.message?.substringAfter("Detail: ") ?: psqlException.message
                        }",
                        call.requestId,
                    ),
                )
            } else {
                // Not a unique violation, fall back to default behavior (log and 500)
                call.application.log.error("Database error", cause)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    errorResponse("DATABASE_ERROR", "A database error occurred", call.requestId),
                )
            }
        }
    }
}
