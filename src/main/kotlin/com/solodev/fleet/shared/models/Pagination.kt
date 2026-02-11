package com.solodev.fleet.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class PaginatedResponse<T>(
    val items: List<T>,      // Core data
    val nextCursor: String?, // The ID of the last item to use for the NEXT request
    val limit: Int,          // Page size
    val total: Long? = null  // Optional grand total
)

data class PaginationParams(
    val limit: Int,
    val cursor: String?
)