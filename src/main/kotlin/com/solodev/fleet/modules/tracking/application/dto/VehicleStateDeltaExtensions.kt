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
        timestamp = state.timestamp,
        accelX = state.accelX,
        accelY = state.accelY,
        accelZ = state.accelZ,
        gyroX = state.gyroX,
        gyroY = state.gyroY,
        gyroZ = state.gyroZ,
        batteryLevel = state.batteryLevel,
        harshBrake = state.harshBrake,
        harshAccel = state.harshAccel,
        sharpTurn = state.sharpTurn
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
        timestamp = newState.timestamp,
        // Sensor fields
        accelX = if (lastState.accelX != newState.accelX) newState.accelX else null,
        accelY = if (lastState.accelY != newState.accelY) newState.accelY else null,
        accelZ = if (lastState.accelZ != newState.accelZ) newState.accelZ else null,
        gyroX = if (lastState.gyroX != newState.gyroX) newState.gyroX else null,
        gyroY = if (lastState.gyroY != newState.gyroY) newState.gyroY else null,
        gyroZ = if (lastState.gyroZ != newState.gyroZ) newState.gyroZ else null,
        batteryLevel = if (lastState.batteryLevel != newState.batteryLevel) newState.batteryLevel else null,
        harshBrake = if (lastState.harshBrake != newState.harshBrake) newState.harshBrake else null,
        harshAccel = if (lastState.harshAccel != newState.harshAccel) newState.harshAccel else null,
        sharpTurn = if (lastState.sharpTurn != newState.sharpTurn) newState.sharpTurn else null
    )
}