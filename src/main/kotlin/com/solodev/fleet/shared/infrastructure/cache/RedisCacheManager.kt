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
}
