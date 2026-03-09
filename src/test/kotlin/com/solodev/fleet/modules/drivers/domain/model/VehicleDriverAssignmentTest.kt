package com.solodev.fleet.modules.drivers.domain.model

import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.*

class VehicleDriverAssignmentTest {

    @Test
    fun `assignment is active when releasedAt is null`() {
        val assignment = sampleAssignment(releasedAt = null)
        assertTrue(assignment.isActive)
    }

    @Test
    fun `assignment is inactive when releasedAt is set`() {
        val assignment = sampleAssignment(releasedAt = Instant.parse("2026-01-01T10:00:00Z"))
        assertFalse(assignment.isActive)
    }

    @Test
    fun `assignment stores vehicleId and driverId`() {
        val assignment = sampleAssignment()
        assertEquals("vehicle-001", assignment.vehicleId)
        assertEquals("driver-001", assignment.driverId)
    }

    @Test
    fun `assignment with notes stores the note text`() {
        val assignment = sampleAssignment(notes = "Regular city route")
        assertEquals("Regular city route", assignment.notes)
    }

    @Test
    fun `assignment without notes has null notes`() {
        val assignment = sampleAssignment(notes = null)
        assertNull(assignment.notes)
    }

    @Test
    fun `releasing active assignment sets releasedAt`() {
        val assignment = sampleAssignment(releasedAt = null)
        val released = assignment.copy(releasedAt = Instant.now())
        assertFalse(released.isActive)
        assertEquals(assignment.vehicleId, released.vehicleId)
        assertEquals(assignment.driverId, released.driverId)
    }

    private fun sampleAssignment(
        releasedAt: Instant? = null,
        notes: String? = null,
    ) = VehicleDriverAssignment(
        id         = "assign-001",
        vehicleId  = "vehicle-001",
        driverId   = "driver-001",
        assignedAt = Instant.parse("2026-01-01T08:00:00Z"),
        releasedAt = releasedAt,
        notes      = notes,
    )
}
