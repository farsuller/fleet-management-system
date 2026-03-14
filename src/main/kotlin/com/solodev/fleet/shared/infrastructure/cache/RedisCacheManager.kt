package com.solodev.fleet.shared.infrastructure.cache

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import redis.clients.jedis.JedisPool

class RedisCacheManager(@PublishedApi internal val jedisPool: JedisPool?) {
    @PublishedApi internal val json = Json { ignoreUnknownKeys = true }

    suspend inline fun <reified T> getOrSet(
            key: String,
            ttlSeconds: Long,
            fetcher: suspend () -> T?
    ): T? {
        val pool = jedisPool ?: return fetcher()
        return try {
            pool.resource.use { jedis ->
                val cached = jedis.get(key)
                if (cached != null) {
                    return json.decodeFromString<T>(cached)
                }

                val data = fetcher()
                if (data != null) {
                    jedis.setex(key, ttlSeconds, json.encodeToString(data))
                }
                data
            }
        } catch (e: Exception) {
            // Log error and fallback to fetcher
            fetcher()
        }
    }
    suspend inline fun <reified T> set(key: String, value: T, ttlSeconds: Long = 3600) {
        val pool = jedisPool ?: return
        try {
            pool.resource.use { jedis ->
                jedis.setex(key, ttlSeconds, json.encodeToString(value))
            }
        } catch (e: Exception) {
            // Log error
        }
    }

    suspend fun delete(key: String) {
        val pool = jedisPool ?: return
        try {
            pool.resource.use { jedis ->
                jedis.del(key)
            }
        } catch (e: Exception) {
            // Log error
        }
    }
}
