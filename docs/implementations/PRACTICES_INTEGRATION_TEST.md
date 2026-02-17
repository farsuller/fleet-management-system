Act as a **Senior Kotlin Backend Developer (Ktor)**. I need you to generate an **Integration Test** for the following Ktor components (Routing / Service / Repository).

Please strictly adhere to the following **Best Practices and Guidelines**:

---

### 1. Testcontainers Strategy (Crucial)

* Use **Testcontainers** (e.g., PostgreSQLContainer) instead of H2 for integration tests.
* Implement the **Singleton / Shared Container Pattern**: declare containers as `object` or `companion object` so they start only once per test suite.
* Containers must be started **before** the Ktor application boots.
* Database configuration must be injected via **environment variables or application.conf overrides** (not hard‑coded).

---

### 2. Test Configuration

* Use **Ktor Test Engine** (`testApplication {}`) to start the full application.
* Install the same plugins used in production (Routing, ContentNegotiation, Authentication if applicable).
* Use a real HTTP client (`client.get`, `client.post`) to hit actual routes.
* Serialization must use **kotlinx.serialization**.

---

### 3. Database & Migration Setup

* Run **Flyway migrations** automatically on startup before tests execute.
* Use **PostgreSQL Testcontainer** as the database.
* Use **HikariCP** for connection pooling.
* Exposed must be configured exactly as in production.

---

### 4. Data Management & Isolation

* Ensure test isolation:

  * Either clean database tables in `@BeforeEach` / `@AfterEach`
  * OR wrap each test in an Exposed transaction and manually rollback
* Never rely on test execution order.

---

### 5. Assertions

* Use **AssertJ** (`assertThat`) for all assertions.
* Verify:

  * HTTP status codes
  * Response body contents
  * Actual database side effects via repository queries

---

### 6. Structure & Naming

* Follow the **AAA Pattern** with comments:

  * `// Arrange`
  * `// Act`
  * `// Assert`
* Test method naming:

  * `should[ExpectedBehavior]_When[Scenario]`

---

### 7. External Service Mocking (Optional)

* Use **WireMock Testcontainer** for mocking external HTTP services.
* WireMock container must be shared across tests.
* Stub external calls **before** triggering the route under test.
* Verify calls using WireMock verification methods.

---

### 8. What This Test Represents

* This is a **true integration test**:

  * Real HTTP
  * Real database
  * Real serialization
  * Real migrations
* No mocks for repositories or services.
