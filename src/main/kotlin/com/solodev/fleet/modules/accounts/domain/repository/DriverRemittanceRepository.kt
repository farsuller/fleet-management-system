package com.solodev.fleet.modules.accounts.domain.repository

import com.solodev.fleet.modules.accounts.domain.model.DriverRemittance
import java.util.UUID

interface DriverRemittanceRepository {
    suspend fun save(remittance: DriverRemittance): DriverRemittance
    suspend fun findById(id: UUID): DriverRemittance?
    suspend fun findByDriverId(driverId: UUID): List<DriverRemittance>
    suspend fun findAll(): List<DriverRemittance>
}
