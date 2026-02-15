package com.solodev.fleet.modules.users.infrastructure.persistence

import com.solodev.fleet.modules.users.domain.model.TokenType
import com.solodev.fleet.modules.users.domain.model.UserId
import com.solodev.fleet.modules.users.domain.model.VerificationToken
import com.solodev.fleet.modules.users.domain.repository.VerificationTokenRepository
import com.solodev.fleet.shared.helpers.dbQuery
import java.time.Instant
import java.util.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll

class VerificationTokenRepositoryImpl : VerificationTokenRepository {

    override suspend fun save(token: VerificationToken): VerificationToken = dbQuery {
        VerificationTokensTable.insert {
            it[id] = token.id
            it[userId] = UUID.fromString(token.userId.value)
            it[VerificationTokensTable.token] = token.token
            it[type] = token.type.name
            it[expiresAt] = token.expiresAt
            it[createdAt] = token.createdAt
        }
        token
    }

    override suspend fun findByToken(token: String, type: TokenType): VerificationToken? = dbQuery {
        VerificationTokensTable.selectAll()
                .where {
                    (VerificationTokensTable.token eq token) and
                            (VerificationTokensTable.type eq type.name)
                }
                .map {
                    VerificationToken(
                            id = it[VerificationTokensTable.id].value,
                            userId = UserId(it[VerificationTokensTable.userId].value.toString()),
                            token = it[VerificationTokensTable.token],
                            type = TokenType.valueOf(it[VerificationTokensTable.type]),
                            expiresAt = it[VerificationTokensTable.expiresAt],
                            createdAt = it[VerificationTokensTable.createdAt]
                    )
                }
                .singleOrNull()
    }

    override suspend fun deleteByToken(token: String): Unit = dbQuery {
        VerificationTokensTable.deleteWhere { VerificationTokensTable.token eq token }
    }

    override suspend fun deleteExpired(): Unit = dbQuery {
        VerificationTokensTable.deleteWhere { expiresAt less Instant.now() }
    }
}
