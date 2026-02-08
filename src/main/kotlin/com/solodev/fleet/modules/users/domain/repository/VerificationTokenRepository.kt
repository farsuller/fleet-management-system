package com.solodev.fleet.modules.users.domain.repository

import com.solodev.fleet.modules.users.domain.model.TokenType
import com.solodev.fleet.modules.users.domain.model.VerificationToken

interface VerificationTokenRepository {
    suspend fun save(token: VerificationToken): VerificationToken
    suspend fun findByToken(token: String, type: TokenType): VerificationToken?
    suspend fun deleteByToken(token: String)
    suspend fun deleteExpired()
}
