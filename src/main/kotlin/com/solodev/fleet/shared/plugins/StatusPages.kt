package com.solodev.fleet.shared.plugins

import com.solodev.fleet.shared.exceptions.*
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.models.ErrorDetail
import com.solodev.fleet.shared.models.FieldError
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

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
                                ApiResponse<Nothing>(
                                        success = false,
                                        error =
                                                ErrorDetail(
                                                        code = cause.errorCode,
                                                        message = cause.message
                                                                        ?: "Resource not found"
                                                ),
                                        requestId = call.requestId
                                )
                        )
                }

                exception<ValidationException> { call, cause ->
                        call.respond(
                                HttpStatusCode.UnprocessableEntity,
                                ApiResponse<Nothing>(
                                        success = false,
                                        error =
                                                ErrorDetail(
                                                        code = cause.errorCode,
                                                        message = cause.message
                                                                        ?: "Validation failed",
                                                        details =
                                                                cause.fieldErrors.map {
                                                                        (field, reason) ->
                                                                        FieldError(field, reason)
                                                                }
                                                ),
                                        requestId = call.requestId
                                )
                        )
                }

                exception<ConflictException> { call, cause ->
                        call.respond(
                                HttpStatusCode.Conflict,
                                ApiResponse<Nothing>(
                                        success = false,
                                        error =
                                                ErrorDetail(
                                                        code = cause.errorCode,
                                                        message = cause.message
                                                                        ?: "Conflict detected"
                                                ),
                                        requestId = call.requestId
                                )
                        )
                }

                exception<UnauthenticatedException> { call, cause ->
                        call.respond(
                                HttpStatusCode.Unauthorized,
                                ApiResponse<Nothing>(
                                        success = false,
                                        error =
                                                ErrorDetail(
                                                        code = cause.errorCode,
                                                        message = cause.message
                                                                        ?: "Authentication required"
                                                ),
                                        requestId = call.requestId
                                )
                        )
                }

                exception<ForbiddenException> { call, cause ->
                        call.respond(
                                HttpStatusCode.Forbidden,
                                ApiResponse<Nothing>(
                                        success = false,
                                        error =
                                                ErrorDetail(
                                                        code = cause.errorCode,
                                                        message = cause.message
                                                                        ?: "Insufficient permissions"
                                                ),
                                        requestId = call.requestId
                                )
                        )
                }

                exception<RateLimitException> { call, cause ->
                        call.respond(
                                HttpStatusCode.TooManyRequests,
                                ApiResponse<Nothing>(
                                        success = false,
                                        error =
                                                ErrorDetail(
                                                        code = cause.errorCode,
                                                        message = cause.message
                                                                        ?: "Rate limit exceeded"
                                                ),
                                        requestId = call.requestId
                                )
                        )
                }

                // Catch-all for unexpected errors (500)
                exception<Throwable> { call, cause ->
                        // Log the full error for debugging but don't expose internals to client
                        call.application.log.error("Unhandled exception", cause)

                        call.respond(
                                HttpStatusCode.InternalServerError,
                                ApiResponse<Nothing>(
                                        success = false,
                                        error =
                                                ErrorDetail(
                                                        code = "INTERNAL_ERROR",
                                                        message = "An unexpected error occurred"
                                                ),
                                        requestId = call.requestId
                                )
                        )
                }
        }
}
