package com.solodev.fleet.modules.tracking.application.dto

import kotlinx.serialization.Serializable

/** Data Transfer Object representing a Route. */
@Serializable data class RouteDTO(val id: String, val name: String, val description: String?)
