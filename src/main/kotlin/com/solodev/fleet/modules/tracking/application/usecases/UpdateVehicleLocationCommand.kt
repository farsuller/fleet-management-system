package com.solodev.fleet.modules.tracking.application.usecases

import java.time.Instant

data class UpdateVehicleLocationCommand(
    val vehicleId:    String,
    val latitude:     Double,
    val longitude:    Double,
    val speed:        Double?  = null,
    val heading:      Double?  = null,
    val accuracy:     Double?  = null,
    val routeId:      String?  = null,
    val recordedAt:   Instant,
    // NEW
    val accelX:       Double?  = null,
    val accelY:       Double?  = null,
    val accelZ:       Double?  = null,
    val gyroX:        Double?  = null,
    val gyroY:        Double?  = null,
    val gyroZ:        Double?  = null,
    val batteryLevel: Int?     = null,
    val harshBrake:   Boolean  = false,
    val harshAccel:   Boolean  = false,
    val sharpTurn:    Boolean  = false,
)
