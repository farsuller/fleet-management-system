package com.solodev.fleet.modules.tracking.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class SensorPingBatchResponse(
    val accepted: Int,
    val rejected: Int,
)
