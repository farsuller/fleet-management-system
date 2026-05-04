package com.solodev.fleet.shared.infrastructure.email

import kotlinx.serialization.Serializable

@Serializable
data class NuntlyEmailRequest(
    val from: String,
    val to: String,
    val subject: String,
    val html: String,
    val text: String? = null,
)

@Serializable
data class NuntlyEmailResponse(
    val data: NuntlyEmailData? = null,
    val error: NuntlyEmailError? = null,
)

@Serializable
data class NuntlyEmailData(
    val id: String,
)

@Serializable
data class NuntlyEmailError(
    val message: String? = null,
    val code: String? = null,
    val status: Int? = null,
    val title: String? = null,
)
