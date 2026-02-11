package com.solodev.fleet.shared.plugins

import com.solodev.fleet.shared.models.PaginationParams
import io.ktor.server.application.*

/**
 * Helper to extract ?limit=X&cursor=Y from the URL.
 */
fun ApplicationCall.paginationParams(defaultLimit: Int = 20, maxLimit: Int = 100): PaginationParams {
    val limit = request.queryParameters["limit"]?.toIntOrNull()?.coerceIn(1, maxLimit) ?: defaultLimit
    val cursor = request.queryParameters["cursor"]
    return PaginationParams(limit, cursor)
}