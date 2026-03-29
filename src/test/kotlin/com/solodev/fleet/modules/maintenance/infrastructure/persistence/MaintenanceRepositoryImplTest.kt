package com.solodev.fleet.modules.maintenance.infrastructure.persistence

import com.solodev.fleet.IntegrationTestBase
import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJobId
import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceJobType
import com.solodev.fleet.modules.maintenance.domain.model.MaintenancePriority
import com.solodev.fleet.modules.maintenance.domain.model.MaintenanceStatus
import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import com.solodev.fleet.modules.vehicles.infrastructure.persistence.VehiclesTable
import com.solodev.fleet.modules.maintenance.infrastructure.persistence.MaintenanceJobsTable
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking as krunBlocking

class MaintenanceRepositoryImplTest : IntegrationTestBase() {

    private val repository = MaintenanceRepositoryImpl()

    @BeforeEach
    fun setUp() {
        cleanDatabase()
        transaction {
            // Ensure tables are created (Flyway should handle this but SchemaUtils is safer for unit tests)
            SchemaUtils.create(VehiclesTable, MaintenanceJobsTable)
        }
    }

    @Test
    fun `findById should return maintenance job with enriched vehicle identity`() {
        val vehicleId = UUID.randomUUID()
        val jobId = UUID.randomUUID()
        val plate = "ABC-123"
        val make = "Toyota"
        val model = "Hilux"

        transaction {
            // 1. Seed Vehicle
            VehiclesTable.insert {
                it[id] = vehicleId
                it[plateNumber] = plate
                it[VehiclesTable.make] = make
                it[VehiclesTable.model] = model
                it[year] = 2022
                it[status] = "AVAILABLE"
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }

            // 2. Seed Maintenance Job
            MaintenanceJobsTable.insert {
                it[id] = jobId
                it[jobNumber] = "JOB-001"
                it[this.vehicleId] = vehicleId
                it[status] = MaintenanceStatus.SCHEDULED.name
                it[jobType] = MaintenanceJobType.PREVENTIVE.name
                it[description] = "Oil Change"
                it[priority] = MaintenancePriority.NORMAL.name
                it[scheduledDate] = Instant.now()
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }
        }

        // 3. Act
        val result = runBlocking {
            repository.findById(MaintenanceJobId(jobId.toString()))
        }

        // 4. Assert
        assertThat(result).isNotNull
        assertThat(result?.vehiclePlate).isEqualTo(plate)
        assertThat(result?.vehicleMake).isEqualTo(make)
        assertThat(result?.vehicleModel).isEqualTo(model)
        assertThat(result?.jobNumber).isEqualTo("JOB-001")
    }
}

// Helper for coroutines in tests
fun <T> runBlocking(block: suspend () -> T): T = krunBlocking { block() }
