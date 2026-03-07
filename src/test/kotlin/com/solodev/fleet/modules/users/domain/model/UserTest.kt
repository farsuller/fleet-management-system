package com.solodev.fleet.modules.users.domain.model

import org.junit.jupiter.api.Test
import kotlin.test.*

class UserTest {

    @Test
    fun `UserId rejects blank value`() {
        assertFailsWith<IllegalArgumentException> {
            UserId("")
        }
    }

    @Test
    fun `UserId accepts valid UUID string`() {
        val id = UserId("a8098c1a-f86e-11da-bd1a-00112444be1e")
        assertEquals("a8098c1a-f86e-11da-bd1a-00112444be1e", id.value)
    }

    @Test
    fun `User fullName concatenates first and last name`() {
        val user = sampleUser()
        assertEquals("Juan dela Cruz", user.fullName)
    }

    @Test
    fun `unverified user is marked isVerified false`() {
        val user = sampleUser(isVerified = false)
        assertFalse(user.isVerified)
    }

    private fun sampleUser(
        isVerified: Boolean = true
    ) = User(
        id = UserId("a8098c1a-f86e-11da-bd1a-00112444be1e"),
        email = "juan@fleet.ph",
        firstName = "Juan",
        lastName = "dela Cruz",
        passwordHash = "hashed_password",
        phone = "+63912345678",
        isVerified = isVerified,
        roles = emptyList()
    )
}
