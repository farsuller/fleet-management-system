package com.solodev.fleet.shared.models

import kotlinx.serialization.Serializable

@Serializable
data class PaginatedResponse<T>(
    val items: List<T>,
    val nextCursor: String?,
    val limit: Int,
    val total: Long? = null,
)

data class PaginationParams(
    val limit: Int,
    val cursor: String?,
    val filters: Map<String, String> = emptyMap(),
)
