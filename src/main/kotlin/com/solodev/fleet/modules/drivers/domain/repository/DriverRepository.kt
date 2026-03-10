package com.solodev.fleet.modules.drivers.domain.repository

import com.solodev.fleet.modules.drivers.domain.model.Driver
import com.solodev.fleet.modules.drivers.domain.model.DriverId
import com.solodev.fleet.modules.drivers.domain.model.VehicleDriverAssignment

interface DriverRepository {
    suspend fun findById(id: DriverId): Driver?
    suspend fun findByEmail(email: String): Driver?
    suspend fun findByLicenseNumber(licenseNumber: String): Driver?
    suspend fun findAll(): List<Driver>
    suspend fun save(driver: Driver): Driver
    suspend fun deleteById(id: DriverId): Boolean

    // Vehicle assignment
    suspend fun assignToVehicle(driverId: DriverId, vehicleId: String, notes: String?): VehicleDriverAssignment
    suspend fun releaseFromVehicle(driverId: DriverId): VehicleDriverAssignment?
    suspend fun findActiveAssignmentByVehicle(vehicleId: String): VehicleDriverAssignment?
    suspend fun findActiveAssignmentByDriver(driverId: DriverId): VehicleDriverAssignment?
    suspend fun findAssignmentHistoryByVehicle(vehicleId: String): List<VehicleDriverAssignment>
    suspend fun findAssignmentHistoryByDriver(driverId: DriverId): List<VehicleDriverAssignment>
}
