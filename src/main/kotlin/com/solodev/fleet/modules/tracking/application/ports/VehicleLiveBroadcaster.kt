package com.solodev.fleet.modules.tracking.application.ports

import com.solodev.fleet.modules.tracking.application.dto.VehicleStateDelta
import kotlinx.coroutines.flow.Flow

/**
 * Application port for broadcasting real-time vehicle state deltas.
 * Allows swapping implementations (in-memory, Redis, etc.) without affecting domain logic.
 */
interface VehicleLiveBroadcaster {
    /**
     * Publishes a vehicle state delta to all connected subscribers.
     */
    suspend fun publish(delta: VehicleStateDelta)
    
    /**
     * Returns a Flow of all state deltas for WebSocket subscribers.
     */
    fun stream(): Flow<VehicleStateDelta>
}