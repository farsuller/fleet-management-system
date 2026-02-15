package com.solodev.fleet.shared.infrastructure.cache

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import redis.clients.jedis.Jedis

class RedisCacheManager(private val jedis: Jedis) {
    @PublishedApi internal val json = Json { ignoreUnknownKeys = true }

    @PublishedApi internal fun getJedis() = jedis

    suspend inline fun <reified T> getOrSet(
            key: String,
            ttlSeconds: Long,
            fetcher: suspend () -> T?
    ): T? {
        return try {
            val cached = getJedis()[key]
            if (cached != null) {
                return json.decodeFromString<T>(cached)
            }

            val data = fetcher()
            if (data != null) {
                getJedis().setex(key, ttlSeconds, json.encodeToString(data))
            }
            data
        } catch (e: Exception) {
            // Log error and fallback to fetcher
            fetcher()
        }
    }
}
