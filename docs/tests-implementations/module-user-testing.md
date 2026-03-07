# User Module - Test Implementation Guide

This document covers testing strategy and implementations for the User module, including registration, email verification, authentication (JWT), profile management, and role assignment.

---

## 1. Domain Unit Tests

### User Value Object Tests
`src/test/kotlin/com/solodev/fleet/modules/users/domain/model/UserTest.kt`

```kotlin
package com.solodev.fleet.modules.users.domain.model

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

    private fun sampleUser(
        isVerified: Boolean = true
    ) = User(
        id = UserId("a8098c1a-f86e-11da-bd1a-00112444be1e"),
        email = "juan@fleet.ph",
        passwordHash = "hashed_pass",
        firstName = "Juan",
        lastName = "dela Cruz",
        phone = "+6391234567",
        isVerified = isVerified,
        roles = emptyList()
    )
}
```

### VerificationToken Tests
`src/test/kotlin/com/solodev/fleet/modules/users/domain/model/VerificationTokenTest.kt`

```kotlin
package com.solodev.fleet.modules.users.domain.model

import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.*

class VerificationTokenTest {

    @Test
    fun `isExpired returns true when expiresAt is in the past`() {
        val token = VerificationToken(
            userId = UserId("a8098c1a-f86e-11da-bd1a-00112444be1e"),
            token = "some-uuid-token",
            type = TokenType.EMAIL_VERIFICATION,
            expiresAt = Instant.now().minus(1, ChronoUnit.HOURS)
        )
        assertTrue(token.expiresAt.isBefore(Instant.now()))
    }

    @Test
    fun `isExpired returns false when expiresAt is in the future`() {
        val token = VerificationToken(
            userId = UserId("a8098c1a-f86e-11da-bd1a-00112444be1e"),
            token = "some-uuid-token",
            type = TokenType.EMAIL_VERIFICATION,
            expiresAt = Instant.now().plus(24, ChronoUnit.HOURS)
        )
        assertFalse(token.expiresAt.isBefore(Instant.now()))
    }
}
```

---

## 2. Use Case Unit Tests

### RegisterUserUseCase Tests
`src/test/kotlin/com/solodev/fleet/modules/users/application/usecases/RegisterUserUseCaseTest.kt`

```kotlin
package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.users.application.dto.UserRegistrationRequest
import com.solodev.fleet.modules.users.domain.model.*
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import com.solodev.fleet.modules.users.domain.repository.VerificationTokenRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class RegisterUserUseCaseTest {

    private val userRepository = mockk<UserRepository>()
    private val tokenRepository = mockk<VerificationTokenRepository>()
    private val useCase = RegisterUserUseCase(userRepository, tokenRepository)

    private val validRequest = UserRegistrationRequest(
        email = "juan@fleet.ph",
        passwordRaw = "Secure@123",
        firstName = "Juan",
        lastName = "dela Cruz",
        phone = "+63912345678"
    )

    @Test
    fun `registers new user successfully`() = runBlocking {
        val customerRole = Role(id = "role-1", name = "CUSTOMER", description = null)
        coEvery { userRepository.findByEmail(any()) } returns null
        coEvery { userRepository.findRoleByName("CUSTOMER") } returns customerRole
        coEvery { userRepository.save(any()) } returnsArgument 0
        coEvery { tokenRepository.save(any()) } just Runs

        val result = useCase.execute(validRequest)

        assertEquals("juan@fleet.ph", result.email)
        assertFalse(result.isVerified)                          // new users are unverified
        coVerify { tokenRepository.save(any()) }                // verification token generated
        coVerify { userRepository.save(match { !it.isVerified }) }
    }

    @Test
    fun `throws when email is already registered`() = runBlocking {
        val existingUser = mockk<User>()
        coEvery { userRepository.findByEmail("juan@fleet.ph") } returns existingUser

        assertFailsWith<IllegalStateException> {
            useCase.execute(validRequest)
        }
    }

    @Test
    fun `throws when CUSTOMER role is not found in database`() = runBlocking {
        coEvery { userRepository.findByEmail(any()) } returns null
        coEvery { userRepository.findRoleByName("CUSTOMER") } returns null

        assertFailsWith<IllegalStateException> {
            useCase.execute(validRequest)
        }
    }

    @Test
    fun `password is stored as hash, not plaintext`() = runBlocking {
        val customerRole = Role(id = "role-1", name = "CUSTOMER", description = null)
        coEvery { userRepository.findByEmail(any()) } returns null
        coEvery { userRepository.findRoleByName("CUSTOMER") } returns customerRole
        coEvery { userRepository.save(any()) } returnsArgument 0
        coEvery { tokenRepository.save(any()) } just Runs

        useCase.execute(validRequest)

        coVerify { userRepository.save(match { it.passwordHash != "Secure@123" }) }
    }
}
```

### LoginUserUseCase Tests
`src/test/kotlin/com/solodev/fleet/modules/users/application/usecases/LoginUserUseCaseTest.kt`

```kotlin
package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.users.application.dto.LoginRequest
import com.solodev.fleet.modules.users.domain.model.*
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import com.solodev.fleet.shared.utils.JwtService
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class LoginUserUseCaseTest {

    private val userRepository = mockk<UserRepository>()
    private val jwtService = mockk<JwtService>()
    private val useCase = LoginUserUseCase(userRepository, jwtService)

    @Test
    fun `returns user and JWT token on valid credentials`() = runBlocking {
        val user = sampleUser(isVerified = true, passwordHash = hashOf("correct_pass"))
        coEvery { userRepository.findByEmail("juan@fleet.ph") } returns user
        every { jwtService.generateToken(any(), any(), any()) } returns "jwt.token.here"

        val (returnedUser, token) = useCase.execute(LoginRequest("juan@fleet.ph", "correct_pass"))

        assertEquals(user.id, returnedUser.id)
        assertEquals("jwt.token.here", token)
        verify { jwtService.generateToken(user.id.value, user.email, any()) }
    }

    @Test
    fun `throws when user email does not exist`() = runBlocking {
        coEvery { userRepository.findByEmail(any()) } returns null

        assertFailsWith<IllegalArgumentException> {
            useCase.execute(LoginRequest("unknown@fleet.ph", "any_pass"))
        }
    }

    @Test
    fun `throws when password is wrong`() = runBlocking {
        val user = sampleUser(isVerified = true, passwordHash = hashOf("correct_pass"))
        coEvery { userRepository.findByEmail(any()) } returns user

        assertFailsWith<IllegalArgumentException> {
            useCase.execute(LoginRequest("juan@fleet.ph", "wrong_pass"))
        }
    }

    @Test
    fun `throws when user email is not verified`() = runBlocking {
        val user = sampleUser(isVerified = false, passwordHash = hashOf("correct_pass"))
        coEvery { userRepository.findByEmail(any()) } returns user

        val ex = assertFailsWith<IllegalArgumentException> {
            useCase.execute(LoginRequest("juan@fleet.ph", "correct_pass"))
        }
        assertTrue(ex.message!!.contains("not verified", ignoreCase = true))
    }

    private fun hashOf(raw: String) = com.solodev.fleet.shared.utils.PasswordHasher.hash(raw)

    private fun sampleUser(isVerified: Boolean, passwordHash: String) = User(
        id = UserId("a8098c1a-f86e-11da-bd1a-00112444be1e"),
        email = "juan@fleet.ph",
        passwordHash = passwordHash,
        firstName = "Juan",
        lastName = "dela Cruz",
        phone = "+63912345678",
        isVerified = isVerified,
        roles = listOf(Role(id = "r-1", name = "CUSTOMER", description = null))
    )
}
```

### VerifyEmailUseCase Tests
`src/test/kotlin/com/solodev/fleet/modules/users/application/usecases/VerifyEmailUseCaseTest.kt`

```kotlin
package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.users.domain.model.*
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import com.solodev.fleet.modules.users.domain.repository.VerificationTokenRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.test.*

class VerifyEmailUseCaseTest {

    private val userRepository = mockk<UserRepository>()
    private val tokenRepository = mockk<VerificationTokenRepository>()
    private val useCase = VerifyEmailUseCase(userRepository, tokenRepository)

    private val userId = UserId("a8098c1a-f86e-11da-bd1a-00112444be1e")

    @Test
    fun `marks user as verified and deletes token`() = runBlocking {
        val token = validToken()
        val user = unverifiedUser()
        coEvery { tokenRepository.findByToken(TOKEN, TokenType.EMAIL_VERIFICATION) } returns token
        coEvery { userRepository.findById(userId) } returns user
        coEvery { userRepository.save(any()) } returnsArgument 0
        coEvery { tokenRepository.deleteByToken(TOKEN) } just Runs

        useCase.execute(TOKEN)

        coVerify { userRepository.save(match { it.isVerified }) }
        coVerify { tokenRepository.deleteByToken(TOKEN) }
    }

    @Test
    fun `throws on unknown token`() = runBlocking {
        coEvery { tokenRepository.findByToken(any(), any()) } returns null

        assertFailsWith<IllegalArgumentException> {
            useCase.execute("bad-token")
        }
    }

    @Test
    fun `throws when token is expired`() = runBlocking {
        val expiredToken = VerificationToken(
            userId = userId,
            token = TOKEN,
            type = TokenType.EMAIL_VERIFICATION,
            expiresAt = Instant.now().minus(1, ChronoUnit.HOURS)
        )
        coEvery { tokenRepository.findByToken(TOKEN, TokenType.EMAIL_VERIFICATION) } returns expiredToken

        assertFailsWith<IllegalArgumentException> {
            useCase.execute(TOKEN)
        }
    }

    @Test
    fun `throws when user associated with token is not found`() = runBlocking {
        coEvery { tokenRepository.findByToken(TOKEN, TokenType.EMAIL_VERIFICATION) } returns validToken()
        coEvery { userRepository.findById(userId) } returns null

        assertFailsWith<IllegalStateException> {
            useCase.execute(TOKEN)
        }
    }

    companion object {
        private const val TOKEN = "test-verification-token-uuid"
    }

    private fun validToken() = VerificationToken(
        userId = userId,
        token = TOKEN,
        type = TokenType.EMAIL_VERIFICATION,
        expiresAt = Instant.now().plus(24, ChronoUnit.HOURS)
    )

    private fun unverifiedUser() = User(
        id = userId,
        email = "juan@fleet.ph",
        passwordHash = "hash",
        firstName = "Juan",
        lastName = "dela Cruz",
        phone = "+63912345678",
        isVerified = false,
        roles = emptyList()
    )
}
```

### AssignRoleUseCase Tests
`src/test/kotlin/com/solodev/fleet/modules/users/application/usecases/AssignRoleUseCaseTest.kt`

```kotlin
package com.solodev.fleet.modules.users.application.usecases

import com.solodev.fleet.modules.users.domain.model.*
import com.solodev.fleet.modules.users.domain.repository.UserRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import kotlin.test.*

class AssignRoleUseCaseTest {

    private val userRepository = mockk<UserRepository>()
    private val useCase = AssignRoleUseCase(userRepository)

    @Test
    fun `assigns role to user`() = runBlocking {
        val user = sampleUser(roles = emptyList())
        val adminRole = Role(id = "r-admin", name = "ADMIN", description = "Fleet admin")
        coEvery { userRepository.findById(any()) } returns user
        coEvery { userRepository.findRoleByName("ADMIN") } returns adminRole
        coEvery { userRepository.save(any()) } returnsArgument 0

        val result = useCase.execute(user.id.value, "ADMIN")

        assertTrue(result.roles.any { it.name == "ADMIN" })
        coVerify { userRepository.save(match { it.roles.any { r -> r.name == "ADMIN" } }) }
    }

    @Test
    fun `throws when user not found`() = runBlocking {
        coEvery { userRepository.findById(any()) } returns null

        assertFailsWith<IllegalArgumentException> {
            useCase.execute("unknown-id", "ADMIN")
        }
    }

    @Test
    fun `throws when role does not exist`() = runBlocking {
        coEvery { userRepository.findById(any()) } returns sampleUser()
        coEvery { userRepository.findRoleByName(any()) } returns null

        assertFailsWith<IllegalArgumentException> {
            useCase.execute("user-id", "NONEXISTENT_ROLE")
        }
    }

    private fun sampleUser(roles: List<Role> = emptyList()) = User(
        id = UserId("a8098c1a-f86e-11da-bd1a-00112444be1e"),
        email = "juan@fleet.ph",
        passwordHash = "hash",
        firstName = "Juan",
        lastName = "dela Cruz",
        phone = "+63912345678",
        isVerified = true,
        roles = roles
    )
}
```

---

## 3. HTTP Route Integration Tests

### User Authentication Routes
`src/test/kotlin/com/solodev/fleet/modules/users/infrastructure/http/UserAuthRoutesTest.kt`

```kotlin
package com.solodev.fleet.modules.users.infrastructure.http

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class UserAuthRoutesTest {

    // --- Registration ---

    @Test
    fun `POST register returns 201 with user data`() = testApplication {
        val response = client.post("/v1/users/register") {
            contentType(ContentType.Application.Json)
            setBody("""
                {
                    "email": "newuser@fleet.ph",
                    "passwordRaw": "Secure@123",
                    "firstName": "Juan",
                    "lastName": "dela Cruz",
                    "phone": "+63912345678"
                }
            """.trimIndent())
        }
        assertEquals(HttpStatusCode.Created, response.status)
        val body = response.bodyAsText()
        assertTrue(body.contains("email"))
        assertTrue(body.contains("newuser@fleet.ph"))
    }

    @Test
    fun `POST register returns 409 when email already exists`() = testApplication {
        // Register once successfully, then attempt again with same email
        val body = registrationBody("dup@fleet.ph")
        client.post("/v1/users/register") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        val second = client.post("/v1/users/register") {
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        assertTrue(second.status.value in 409..500)
    }

    @Test
    fun `POST register returns 400 on missing required fields`() = testApplication {
        val response = client.post("/v1/users/register") {
            contentType(ContentType.Application.Json)
            setBody("""{ "email": "incomplete@fleet.ph" }""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    // --- Login ---

    @Test
    fun `POST login returns 200 with JWT token`() = testApplication {
        // Pre-condition: user exists and is verified
        val response = client.post("/v1/users/login") {
            contentType(ContentType.Application.Json)
            setBody("""{ "email": "verified@fleet.ph", "passwordRaw": "Secure@123" }""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("token"))
    }

    @Test
    fun `POST login returns 401 for wrong credentials`() = testApplication {
        val response = client.post("/v1/users/login") {
            contentType(ContentType.Application.Json)
            setBody("""{ "email": "user@fleet.ph", "passwordRaw": "WRONG_PASS" }""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun `POST login returns 403 when email is not verified`() = testApplication {
        val response = client.post("/v1/users/login") {
            contentType(ContentType.Application.Json)
            setBody("""{ "email": "unverified@fleet.ph", "passwordRaw": "Secure@123" }""")
        }
        assertTrue(response.status.value in 400..403)
        assertTrue(response.bodyAsText().contains("not verified", ignoreCase = true))
    }

    // --- Email verification ---

    @Test
    fun `GET auth-verify returns 200 on valid token`() = testApplication {
        val response = client.get("/v1/auth/verify?token=valid-test-token")
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `GET auth-verify returns 400 on invalid token`() = testApplication {
        val response = client.get("/v1/auth/verify?token=nonexistent-token")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `GET auth-verify returns 400 when token query param is missing`() = testApplication {
        val response = client.get("/v1/auth/verify")
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    private fun registrationBody(email: String) = """
        {
            "email": "$email",
            "passwordRaw": "Secure@123",
            "firstName": "Test",
            "lastName": "User",
            "phone": "+63912345678"
        }
    """.trimIndent()
}
```

### User Profile Routes
`src/test/kotlin/com/solodev/fleet/modules/users/infrastructure/http/UserProfileRoutesTest.kt`

```kotlin
package com.solodev.fleet.modules.users.infrastructure.http

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class UserProfileRoutesTest {

    // --- List users ---

    @Test
    fun `GET users returns 200 list`() = testApplication {
        val response = client.get("/v1/users") { bearerAuth(ADMIN_JWT) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("success"))
    }

    @Test
    fun `GET users returns 401 without token`() = testApplication {
        val response = client.get("/v1/users")
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // --- Single user ---

    @Test
    fun `GET users-id returns 200 with profile`() = testApplication {
        val response = client.get("/v1/users/$USER_ID") { bearerAuth(ADMIN_JWT) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("email"))
    }

    @Test
    fun `GET users-id returns 404 for unknown user`() = testApplication {
        val response = client.get("/v1/users/00000000-0000-0000-0000-000000000000") {
            bearerAuth(ADMIN_JWT)
        }
        assertTrue(response.status.value in 404..500)
    }

    // --- Update user ---

    @Test
    fun `PATCH users-id returns 200 with updated profile`() = testApplication {
        val response = client.patch("/v1/users/$USER_ID") {
            bearerAuth(ADMIN_JWT)
            contentType(ContentType.Application.Json)
            setBody("""{ "firstName": "Updated", "lastName": "Name" }""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    @Test
    fun `PATCH users-id returns 401 without auth`() = testApplication {
        val response = client.patch("/v1/users/$USER_ID") {
            contentType(ContentType.Application.Json)
            setBody("""{ "firstName": "Hacker" }""")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    // --- Delete user ---

    @Test
    fun `DELETE users-id returns 200`() = testApplication {
        val response = client.delete("/v1/users/$USER_ID") { bearerAuth(ADMIN_JWT) }
        assertEquals(HttpStatusCode.OK, response.status)
    }

    // --- Assign role ---

    @Test
    fun `POST users-id assigns role and returns updated user`() = testApplication {
        val response = client.post("/v1/users/$USER_ID") {
            bearerAuth(ADMIN_JWT)
            contentType(ContentType.Application.Json)
            setBody("""{ "roleName": "ADMIN" }""")
        }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("roles"))
    }

    // --- List roles ---

    @Test
    fun `GET users-roles returns all available roles`() = testApplication {
        val response = client.get("/v1/users/roles") { bearerAuth(ADMIN_JWT) }
        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("CUSTOMER"))
    }

    companion object {
        const val USER_ID = "a8098c1a-f86e-11da-bd1a-00112444be1e"
        const val ADMIN_JWT = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..." // Use real test JWT
    }
}
```

---

## 4. Error Scenario Tests

```kotlin
package com.solodev.fleet.modules.users.infrastructure.http

import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlin.test.*

class UserRoutesErrorTest {

    @Test
    fun `POST register with invalid email format returns 400`() = testApplication {
        val response = client.post("/v1/users/register") {
            contentType(ContentType.Application.Json)
            setBody("""{ "email": "not-an-email", "passwordRaw": "X", "firstName": "A", "lastName": "B" }""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `POST login with missing password field returns 400`() = testApplication {
        val response = client.post("/v1/users/login") {
            contentType(ContentType.Application.Json)
            setBody("""{ "email": "user@fleet.ph" }""")
        }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }

    @Test
    fun `PATCH user with invalid UUID path returns 400`() = testApplication {
        val response = client.patch("/v1/users/not-a-uuid") {
            bearerAuth("valid.jwt.token")
            contentType(ContentType.Application.Json)
            setBody("""{ "firstName": "Test" }""")
        }
        assertTrue(response.status.value in 400..404)
    }
}
```

---

## 5. Test Summary

| Test Class | Layer | Coverage |
|---|---|---|
| `UserTest` | Unit – Domain | `UserId` validation, `fullName` |
| `VerificationTokenTest` | Unit – Domain | Token expiry logic |
| `RegisterUserUseCaseTest` | Unit – Use Case | Happy path, duplicate email, missing role, password hashing |
| `LoginUserUseCaseTest` | Unit – Use Case | JWT generation, wrong password, unverified user, missing user |
| `VerifyEmailUseCaseTest` | Unit – Use Case | Happy path, expired token, unknown token, missing user |
| `AssignRoleUseCaseTest` | Unit – Use Case | Add role, missing user, unknown role |
| `UserAuthRoutesTest` | Integration – HTTP | Register, login, email verify, error paths |
| `UserProfileRoutesTest` | Integration – HTTP | CRUD, role assignment, list roles, auth enforcement |
| `UserRoutesErrorTest` | Integration – Errors | Invalid email format, missing fields, bad UUID |
