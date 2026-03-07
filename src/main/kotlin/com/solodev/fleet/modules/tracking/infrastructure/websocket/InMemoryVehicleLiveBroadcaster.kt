package com.solodev.fleet.modules.tracking.infrastructure.websocket

import com.solodev.fleet.modules.tracking.application.dto.VehicleStateDelta
import com.solodev.fleet.modules.tracking.application.ports.VehicleLiveBroadcaster
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/**
 * In-memory implementation of VehicleLiveBroadcaster for single-instance deployments.
 * For horizontal scaling, use RedisDeltaBroadcaster instead.
 */
class InMemoryVehicleLiveBroadcaster : VehicleLiveBroadcaster {
    private val broadcastFlow = MutableSharedFlow<VehicleStateDelta>(replay = 0)

    override suspend fun publish(delta: VehicleStateDelta) {
        broadcastFlow.emit(delta)
    }

    override fun stream(): Flow<VehicleStateDelta> {
        return broadcastFlow
    }
}