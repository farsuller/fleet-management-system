package com.solodev.fleet.modules.accounts.application.dto

import kotlinx.serialization.Serializable

@Serializable
data class AccountRequest(
        val accountCode: String,
        val accountName: String,
        val accountType: String,
        val parentAccountId: String? = null,
        val description: String? = null
)
