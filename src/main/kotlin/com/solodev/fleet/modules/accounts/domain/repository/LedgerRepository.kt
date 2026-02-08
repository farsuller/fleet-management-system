package com.solodev.fleet.modules.accounts.domain.repository

import com.solodev.fleet.modules.accounts.domain.model.AccountId
import com.solodev.fleet.modules.accounts.domain.model.LedgerEntry
import java.time.Instant

interface LedgerRepository {
    suspend fun save(entry: LedgerEntry): LedgerEntry
    suspend fun calculateAccountBalance(accountId: AccountId, upToDate: Instant): Long
}