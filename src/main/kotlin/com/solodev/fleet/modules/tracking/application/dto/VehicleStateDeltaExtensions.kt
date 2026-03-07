package com.solodev.fleet.modules.tracking.application.dto

import java.util.UUID

/**
 * Extension methods for creating full and partial delta updates.
 */
fun VehicleStateDelta.Companion.full(state: VehicleRouteState): VehicleStateDelta {
    return VehicleStateDelta(
        vehicleId = UUID.fromString(state.vehicleId),
        progress = state.progress,
        bearing = state.heading,
        status = state.status,
        distanceFromRoute = state.distanceFromRoute,
        timestamp = state.timestamp
    )
}

fun VehicleStateDelta.Companion.diff(
    lastState: VehicleRouteState,
    newState: VehicleRouteState
): VehicleStateDelta {
    return VehicleStateDelta(
        vehicleId = UUID.fromString(newState.vehicleId),
        progress = if (lastState.progress != newState.progress) newState.progress else null,
        bearing = if (lastState.heading != newState.heading) newState.heading else null,
        status = if (lastState.status != newState.status) newState.status else null,
        distanceFromRoute = if (lastState.distanceFromRoute != newState.distanceFromRoute) newState.distanceFromRoute else null,
        timestamp = newState.timestamp
    )
}