Act as a **Senior Kotlin Backend Developer (Ktor)**. I need you to generate a **Unit Test**.

Please strictly adhere to the following **Best Practices and Guidelines**:

### Initial Setup
* Do not deviate from these practices, as they are crucial for ensuring the reliability and maintainability of our integration tests. Do not use wildcards or shortcuts that compromise the integrity of the tests.
* Each point is essential for creating robust and effective integration tests that accurately reflect real-world scenarios and interactions within our Ktor application.
* And do not use wildcard imports, as they can lead to namespace pollution and make it harder to identify which classes are being used in the test.
* Always import only the specific classes needed for the test to maintain clarity and readability.


### 1. Frameworks

* Use **JUnit 5** (Jupiter)
* Use **Mockito** or **MockK** (prefer MockK for Kotlin)
* Use **kotlin.test** where appropriate

---

### 2. Isolation (Crucial)

* Do **NOT** use `testApplication {}` or start Ktor.
* Do **NOT** use Testcontainers, H2, PostgreSQL, or any database.
* Do **NOT** load application.conf or environment configs.
* Tests must be **pure JVM unit tests**.

---

### 3. Mocks

* Use `@Mock` / `@MockK` for dependencies.
* Use `@InjectMocks` / `@InjectMockKs` for the class under test.
* Mock only **direct dependencies** (repositories, external clients).

---

### 4. Argument Matching & Verification (Strict)

* **NO generic matchers** (`any()`, `anyString()`, etc.) unless absolutely unavoidable.
* Always use **exact values** in stubs and verifications.
* If an object is created internally:

  * Use **ArgumentCaptor**
  * Assert captured fields using AssertJ

---

### 5. Assertions

* Use **AssertJ** (`assertThat`) exclusively. Do NOT use JUnit's `assertEquals`.
* Utilize fluent assertions (e.g., `.hasSize()`, `.contains()`, `.isEqualTo()`).
* For exceptions, use `assertThatThrownBy { ... }`.

---

### 6. Structure

* Follow the **AAA Pattern** (Arrange, Act, Assert).
* Always add comments:

  * `// Arrange`
  * `// Act`
  * `// Assert`

---

### 7. Naming Convention

* Method names must follow:

  * `should[ExpectedBehavior]_When[Condition]`
* Example:

  * `shouldReturnUser_WhenIdIsValid`

---

### 8. Scenarios

* Always cover:

  * Happy Path (success)
  * Edge Cases (exceptions / errors)

---

### 9. What This Test Represents

* This is a **true unit test**:

  * No framework bootstrapping
  * No IO
  * No database
  * Fast and deterministic
