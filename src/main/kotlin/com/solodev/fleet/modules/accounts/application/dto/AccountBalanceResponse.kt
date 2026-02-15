package com.solodev.fleet.modules.accounts.application.dto

import kotlinx.serialization.Serializable

@Serializable data class AccountBalanceResponse(val account: String, val balance: Long)
