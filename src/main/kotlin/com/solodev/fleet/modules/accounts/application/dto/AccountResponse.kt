package com.solodev.fleet.modules.accounts.application.dto

import com.solodev.fleet.modules.accounts.domain.model.Account
import kotlinx.serialization.Serializable

@Serializable
data class AccountResponse(
        val id: String,
        val accountCode: String,
        val accountName: String,
        val accountType: String,
        val parentAccountId: String? = null,
        val isActive: Boolean,
        val description: String? = null,
        val balance: Long = 0
) {
        companion object {
                fun fromDomain(account: Account, balanceAmount: Long = 0) =
                        AccountResponse(
                                id = account.id.value,
                                accountCode = account.accountCode,
                                accountName = account.accountName,
                                accountType = account.accountType.name,
                                isActive = account.isActive,
                                description = account.description,
                                balance = balanceAmount
                        )
        }
}
