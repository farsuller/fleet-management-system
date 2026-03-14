package com.solodev.fleet.modules.tracking.application.usecases

import com.solodev.fleet.modules.tracking.application.dto.CoordinateReceptionStatus
import com.solodev.fleet.shared.infrastructure.cache.RedisCacheManager
import java.time.Instant

/**
 * Service to manage the global toggle for coordinate reception.
 * Uses Redis to ensure the state is synchronized across all backend nodes.
 */
open class CoordinateReceptionService(
    private val redisCache: RedisCacheManager
) {
    companion object {
        private const val KEY_ENABLED = "fleet:tracking:coordinate_reception_enabled"
        private const val KEY_UPDATED_AT = "fleet:tracking:coordinate_reception_updated_at"
        private const val KEY_UPDATED_BY = "fleet:tracking:coordinate_reception_updated_by"
    }

    /**
     * Checks if coordinate reception is globally enabled.
     * Defaults to true if not set.
     */
    open suspend fun isReceptionEnabled(): Boolean {
        return redisCache.getOrSet(KEY_ENABLED, 0) { true } ?: true
    }

    /**
     * Sets the global coordinate reception status with audit trailing.
     */
    open suspend fun setReceptionEnabled(enabled: Boolean, updatedBy: String): CoordinateReceptionStatus {
        val now = Instant.now()
        redisCache.set(KEY_ENABLED, enabled, 0)
        redisCache.set(KEY_UPDATED_AT, now.toString(), 0)
        redisCache.set(KEY_UPDATED_BY, updatedBy, 0)
        return CoordinateReceptionStatus(enabled, now, updatedBy)
    }

    /**
     * Retrieves the current status with audit information.
     */
    open suspend fun getStatus(): CoordinateReceptionStatus {
        val enabled = isReceptionEnabled()
        val updatedAtStr = redisCache.getOrSet(KEY_UPDATED_AT, 0) { Instant.EPOCH.toString() } ?: Instant.EPOCH.toString()
        val updatedBy = redisCache.getOrSet(KEY_UPDATED_BY, 0) { "system" } ?: "system"
        
        return CoordinateReceptionStatus(
            enabled = enabled,
            updatedAt = Instant.parse(updatedAtStr),
            updatedBy = updatedBy
        )
    }
}
