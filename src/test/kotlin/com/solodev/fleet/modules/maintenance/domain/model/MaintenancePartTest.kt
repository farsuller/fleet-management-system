package com.solodev.fleet.modules.maintenance.domain.model

import java.util.UUID
import org.junit.jupiter.api.Test
import kotlin.test.*

class MaintenancePartTest {

    private val jobId = MaintenanceJobId("job-001")

    @Test
    fun `partNumber cannot be blank`() {
        assertFailsWith<IllegalArgumentException> {
            MaintenancePart(
                id = UUID.randomUUID(),
                jobId = jobId,
                partNumber = "",
                partName = "Oil Filter",
                quantity = 1,
                unitCost = 50000
            )
        }
    }

    @Test
    fun `partName cannot be blank`() {
        assertFailsWith<IllegalArgumentException> {
            MaintenancePart(
                id = UUID.randomUUID(),
                jobId = jobId,
                partNumber = "PRT-001",
                partName = "",
                quantity = 1,
                unitCost = 50000
            )
        }
    }

    @Test
    fun `quantity must be greater than 0`() {
        assertFailsWith<IllegalArgumentException> {
            MaintenancePart(
                id = UUID.randomUUID(),
                jobId = jobId,
                partNumber = "PRT-001",
                partName = "Oil Filter",
                quantity = 0,
                unitCost = 50000
            )
        }
    }

    @Test
    fun `unitCost cannot be negative`() {
        assertFailsWith<IllegalArgumentException> {
            MaintenancePart(
                id = UUID.randomUUID(),
                jobId = jobId,
                partNumber = "PRT-001",
                partName = "Oil Filter",
                quantity = 1,
                unitCost = -100
            )
        }
    }

    @Test
    fun `totalCost is quantity times unitCost`() {
        val part = MaintenancePart(
            id = UUID.randomUUID(),
            jobId = jobId,
            partNumber = "PRT-001",
            partName = "Oil Filter",
            quantity = 3,
            unitCost = 50000
        )
        assertEquals(150000, part.totalCost)
    }
}
