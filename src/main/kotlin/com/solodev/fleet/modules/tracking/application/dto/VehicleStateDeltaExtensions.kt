package com.solodev.fleet.modules.tracking.application.dto

import java.util.UUID

/**
 * Extension methods for creating full and partial delta updates.
 */
fun VehicleStateDelta.Companion.full(state: VehicleRouteState): VehicleStateDelta {
    return VehicleStateDelta(
        vehicleId = UUID.fromString(state.vehicleId),
        routeProgress = state.progress,
        headingDeg = state.heading,
        latitude = state.latitude,
        longitude = state.longitude,
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
        routeProgress = if (lastState.progress != newState.progress) newState.progress else null,
        headingDeg = if (lastState.heading != newState.heading) newState.heading else null,
        latitude = if (lastState.latitude != newState.latitude) newState.latitude else null,
        longitude = if (lastState.longitude != newState.longitude) newState.longitude else null,
        status = if (lastState.status != newState.status) newState.status else null,
        distanceFromRoute = if (lastState.distanceFromRoute != newState.distanceFromRoute) newState.distanceFromRoute else null,
        timestamp = newState.timestamp
    )
}