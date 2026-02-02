package com.example.shared.models

import kotlinx.serialization.Serializable

/**
 * Standard API response envelope.
 *
 * All HTTP responses follow this consistent format to ensure predictable client handling.
 *
 * @param T The type of data being returned (null for errors)
 * @param success Whether the request succeeded
 * @param data The response payload (present on success)
 * @param error Error details (present on failure)
 * @param requestId Unique identifier for request tracing and support
 */
@Serializable
data class ApiResponse<T>(
        val success: Boolean,
        val data: T? = null,
        val error: ErrorDetail? = null,
        val requestId: String
)

/**
 * Error details included in failed API responses.
 *
 * @param code Stable, programmatic error code (e.g., "VALIDATION_ERROR", "NOT_FOUND")
 * @param message User-safe error message (no internal details)
 * @param details Optional field-level error details for validation failures
 */
@Serializable
data class ErrorDetail(
        val code: String,
        val message: String,
        val details: List<FieldError>? = null
)

/**
 * Field-level validation error.
 *
 * @param field The field name that failed validation
 * @param reason The validation failure reason
 */
@Serializable data class FieldError(val field: String, val reason: String)
