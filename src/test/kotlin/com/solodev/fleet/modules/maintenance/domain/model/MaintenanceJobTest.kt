package com.solodev.fleet.modules.maintenance.domain.model

import com.solodev.fleet.modules.vehicles.domain.model.VehicleId
import java.time.Instant
import org.junit.jupiter.api.Test
import kotlin.test.*

class MaintenanceJobTest {

    // --- Invariants ---

    @Test
    fun `laborCost cannot be negative`() {
        assertFailsWith<IllegalArgumentException> {
            sampleJob(laborCost = -1000)
        }
    }

    @Test
    fun `partsCost cannot be negative`() {
        assertFailsWith<IllegalArgumentException> {
            sampleJob(partsCost = -500)
        }
    }

    @Test
    fun `totalCost is sum of labor and parts`() {
        val job = sampleJob(laborCost = 5000, partsCost = 2000)
        assertEquals(7000, job.totalCost)
    }

    @Test
    fun `MaintenanceJobId rejects blank value`() {
        assertFailsWith<IllegalArgumentException> {
            MaintenanceJobId("")
        }
    }

    // --- start() ---

    @Test
    fun `start transitions SCHEDULED to IN_PROGRESS`() {
        val job = sampleJob(status = MaintenanceStatus.SCHEDULED)
        val started = job.start()
        assertEquals(MaintenanceStatus.IN_PROGRESS, started.status)
        assertNotNull(started.startedAt)
    }

    @Test
    fun `start throws when job is not SCHEDULED`() {
        val job = sampleJob(status = MaintenanceStatus.IN_PROGRESS)
        assertFailsWith<IllegalArgumentException> {
            job.start()
        }
    }

    @Test
    fun `start throws for COMPLETED job`() {
        assertFailsWith<IllegalArgumentException> {
            sampleJob(status = MaintenanceStatus.COMPLETED).start()
        }
    }

    @Test
    fun `start throws for CANCELLED job`() {
        assertFailsWith<IllegalArgumentException> {
            sampleJob(status = MaintenanceStatus.CANCELLED).start()
        }
    }

    // --- complete() ---

    @Test
    fun `complete transitions IN_PROGRESS to COMPLETED with costs`() {
        val job = sampleJob(status = MaintenanceStatus.IN_PROGRESS)
        val completed = job.complete(labor = 5000, parts = 2000)
        assertEquals(MaintenanceStatus.COMPLETED, completed.status)
        assertEquals(5000, completed.laborCost)
        assertEquals(2000, completed.partsCost)
        assertNotNull(completed.completedAt)
    }

    @Test
    fun `complete throws when job is SCHEDULED (not yet started)`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            sampleJob(status = MaintenanceStatus.SCHEDULED).complete(labor = 1000, parts = 1000)
        }
        assertTrue(ex.message!!.contains("IN_PROGRESS", ignoreCase = true))
    }

    @Test
    fun `complete throws when job is CANCELLED`() {
        assertFailsWith<IllegalArgumentException> {
            sampleJob(status = MaintenanceStatus.CANCELLED).complete(labor = 1000, parts = 1000)
        }
    }

    // --- cancel() ---

    @Test
    fun `cancel transitions SCHEDULED to CANCELLED`() {
        val job = sampleJob(status = MaintenanceStatus.SCHEDULED)
        val cancelled = job.cancel()
        assertEquals(MaintenanceStatus.CANCELLED, cancelled.status)
    }

    @Test
    fun `cancel throws when job is IN_PROGRESS`() {
        assertFailsWith<IllegalArgumentException> {
            sampleJob(status = MaintenanceStatus.IN_PROGRESS).cancel()
        }
    }

    @Test
    fun `cancel throws for COMPLETED job`() {
        assertFailsWith<IllegalArgumentException> {
            sampleJob(status = MaintenanceStatus.COMPLETED).cancel()
        }
    }

    private fun sampleJob(
        status: MaintenanceStatus = MaintenanceStatus.SCHEDULED,
        laborCost: Int = 0,
        partsCost: Int = 0
    ) = MaintenanceJob(
        id = MaintenanceJobId("maint-001"),
        jobNumber = "MAINT-001",
        vehicleId = VehicleId("veh-001"),
        status = status,
        jobType = MaintenanceJobType.PREVENTIVE,
        description = "Oil change service",
        scheduledDate = Instant.now(),
        laborCost = laborCost,
        partsCost = partsCost,
        startedAt = if (status != MaintenanceStatus.SCHEDULED) Instant.now() else null,
        completedAt = if (status == MaintenanceStatus.COMPLETED) Instant.now() else null
    )
}
