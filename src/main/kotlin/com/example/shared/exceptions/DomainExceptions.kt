package com.example.shared.exceptions

/**
 * Base exception for all domain-level errors.
 *
 * Domain exceptions represent business rule violations and are mapped to specific HTTP status codes
 * and error codes in the API layer.
 */
sealed class DomainException(message: String, val errorCode: String, cause: Throwable? = null) :
        Exception(message, cause)

/** Resource not found (404). */
class NotFoundException(message: String, errorCode: String = "NOT_FOUND") :
        DomainException(message, errorCode)

/** Validation error - well-formed request but invalid data (422). */
class ValidationException(
        message: String,
        val fieldErrors: List<Pair<String, String>> = emptyList(),
        errorCode: String = "VALIDATION_ERROR"
) : DomainException(message, errorCode)

/** Conflict - duplicate resource or invalid state transition (409). */
class ConflictException(message: String, errorCode: String = "CONFLICT") :
        DomainException(message, errorCode)

/** Unauthorized - missing or invalid authentication (401). */
class UnauthenticatedException(
        message: String = "Authentication required",
        errorCode: String = "UNAUTHENTICATED"
) : DomainException(message, errorCode)

/** Forbidden - valid authentication but insufficient permissions (403). */
class ForbiddenException(
        message: String = "Insufficient permissions",
        errorCode: String = "FORBIDDEN"
) : DomainException(message, errorCode)

/** Rate limit exceeded (429). */
class RateLimitException(
        message: String = "Rate limit exceeded",
        errorCode: String = "RATE_LIMITED"
) : DomainException(message, errorCode)

/** Business-specific: Rental overlap detected. */
class RentalOverlapException(
        message: String = "Vehicle is not available for the requested period",
        errorCode: String = "RENTAL_OVERLAP"
) : DomainException(message, errorCode)
