package com.solodev.fleet.shared.infrastructure.persistence

import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.json.jsonb
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

class IdempotencyRepositoryImpl {
    /** Map to the 'idempotency_keys' table created in V006 migration */
    object IdempotencyKeys : Table("idempotency_keys") {
        val id = uuid("id")
        val idempotencyKey = varchar("idempotency_key", 255).uniqueIndex()
        val requestPath = varchar("request_path", 500)
        val requestMethod = varchar("request_method", 10)
        val responseStatus = integer("response_status").nullable()
        val responseBody = jsonb<String>("response_body", Json { ignoreUnknownKeys = true }).nullable()
        val expiresAt = timestamp("expires_at")
        override val primaryKey = PrimaryKey(id)
    }

    /** Lookup an existing key to see if this request was already processed */
    fun find(key: String) = transaction {
        IdempotencyKeys.select { IdempotencyKeys.idempotencyKey eq key }
            .map {
                StoredResponse(
                    it[IdempotencyKeys.responseStatus],
                    it[IdempotencyKeys.responseBody]
                )
            }.singleOrNull()
    }

    /** Claim a key to prevent other concurrent requests with the same key */
    fun create(key: String, path: String, method: String, ttlMinutes: Long = 60) = transaction {
        IdempotencyKeys.insert {
            it[id] = UUID.randomUUID()
            it[idempotencyKey] = key
            it[requestPath] = path
            it[requestMethod] = method
            it[expiresAt] = Instant.now().plusSeconds(ttlMinutes * 60)
        }
    }

    /** Store the final successful or failed response for future retries */
    fun updateResponse(key: String, status: Int, body: String) = transaction {
        IdempotencyKeys.update({ IdempotencyKeys.idempotencyKey eq key }) {
            it[responseStatus] = status
            it[responseBody] = body
        }
    }
}

data class StoredResponse(val status: Int?, val body: String?)