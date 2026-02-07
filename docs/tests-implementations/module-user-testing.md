# User Module - Test Implementation Guide

This document details the testing strategy and implementations for the User module, specifically focusing on authentication and profile management.

---

## 1. Testing (The Quality Shield)

### Use Case Unit Tests
`src/test/kotlin/com/solodev/fleet/modules/users/application/usecases/LoginUserUseCaseTest.kt`

```kotlin
class LoginUserUseCaseTest {
    private val repository = mockk<UserRepository>()
    private val useCase = LoginUserUseCase(repository)

    @Test
    fun `should login successfully with correct credentials`() = runBlocking {
        // Arrange
        val user = createSampleUser(email = "test@mail.com", hash = "hashed_secret123")
        coEvery { repository.findByEmail("test@mail.com") } returns user

        // Act
        val result = useCase.execute(LoginRequest("test@mail.com", "secret123"))

        // Assert
        assertEquals(user.id, result.id)
    }

    @Test
    fun `should fail login with wrong password`() = runBlocking {
        // Arrange
        val user = createSampleUser(hash = "hashed_correct")
        coEvery { repository.findByEmail(any()) } returns user

        // Act & Assert
        assertFailsWith<IllegalArgumentException> {
            useCase.execute(LoginRequest("test@mail.com", "wrong_pass"))
        }
    }
}
```

### Authentication Integration Tests
`src/test/kotlin/com/solodev/fleet/modules/users/infrastructure/http/UserRoutesTest.kt`

```kotlin
@Test
fun `POST login should return 200 with JWT`() = testApplication {
    configureTestDb()
    val response = client.post("/v1/users/login") {
        contentType(ContentType.Application.Json)
        setBody("""{ "email": "test@mail.com", "passwordRaw": "secret123" }""")
    }
    assertEquals(HttpStatusCode.OK, response.status)
    assertTrue(response.bodyAsText().contains("token"))
}
```
