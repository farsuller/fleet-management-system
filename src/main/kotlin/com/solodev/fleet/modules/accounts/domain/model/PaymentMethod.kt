package com.solodev.fleet.modules.accounts.domain.model

import java.time.Instant
import java.util.*

data class PaymentMethod(
        val id: UUID = UUID.randomUUID(),
        val code: String,
        val displayName: String,
        val targetAccountCode: String,
        val isActive: Boolean = true,
        val description: String? = null,
        val createdAt: Instant = Instant.now(),
        val updatedAt: Instant = Instant.now()
) {
    init {
        require(code.isNotBlank()) { "Code cannot be blank" }
        require(displayName.isNotBlank()) { "Display name cannot be blank" }
        require(targetAccountCode.isNotBlank()) { "Target account code cannot be blank" }
    }
}
