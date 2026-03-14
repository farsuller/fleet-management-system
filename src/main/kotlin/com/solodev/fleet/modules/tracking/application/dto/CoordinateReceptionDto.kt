package com.solodev.fleet.modules.tracking.application.dto

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
data class CoordinateReceptionRequest(val enabled: Boolean)

@Serializable
data class CoordinateReceptionStatus(
    val enabled:   Boolean,
    @Contextual val updatedAt: Instant,
    val updatedBy: String,
)
