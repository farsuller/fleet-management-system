package com.solodev.fleet.shared.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LocationTest {

    @Test
    fun `should create location with valid coordinates`() {
        val location = Location(14.5995, 121.0244)
        assertEquals(14.5995, location.latitude)
        assertEquals(121.0244, location.longitude)
    }

    @Test
    fun `should fail if latitude is out of range`() {
        assertFailsWith<IllegalArgumentException> { Location(91.0, 121.0) }
        assertFailsWith<IllegalArgumentException> { Location(-91.0, 121.0) }
    }

    @Test
    fun `should fail if longitude is out of range`() {
        assertFailsWith<IllegalArgumentException> { Location(14.0, 181.0) }
        assertFailsWith<IllegalArgumentException> { Location(14.0, -181.0) }
    }

    @Test
    fun `should format to string correctly`() {
        val location = Location(14.5, 121.5)
        assertEquals("14.5,121.5", location.toString())
    }

    @Test
    fun `should parse from string correctly`() {
        val location = Location.fromString("14.5,121.5")
        assertEquals(14.5, location.latitude)
        assertEquals(121.5, location.longitude)
    }
}
