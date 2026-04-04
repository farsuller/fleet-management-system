Act as a **Senior Kotlin Backend Developer (Ktor)**. I need you to generate an **Integration Test** for the following Ktor components (Routing / Service / Repository).

Please strictly adhere to the following **Best Practices and Guidelines**:

### Initial Setup
* Do not deviate from these practices, as they are crucial for ensuring the reliability and maintainability of our integration tests. Do not use wildcards or shortcuts that compromise the integrity of the tests. 
* Each point is essential for creating robust and effective integration tests that accurately reflect real-world scenarios and interactions within our Ktor application. 
* And do not use wildcard imports, as they can lead to namespace pollution and make it harder to identify which classes are being used in the test. 
* Always import only the specific classes needed for the test to maintain clarity and readability.

### 1. Testcontainers Strategy (Crucial)

* Use **Testcontainers** (e.g., PostgreSQLContainer) instead of H2 for integration tests.
* Implement the **Singleton / Shared Container Pattern**: declare containers as `object` or `companion object` so they start only once per test suite.
* Containers must be started **before** the Ktor application boots.
* Database configuration must be injected via **environment variables or application.conf overrides** (not hard‑coded).
* Do not use in-memory databases for integration tests; they do not reflect real-world behavior and can lead to false positives/negatives.
* Ensure that the same database configuration (URL, username, password) is used in both the application and the test setup to maintain consistency.
* Use **Flyway** for database migrations to ensure the schema is consistent with production, and run migrations automatically on container startup.
* Configure **HikariCP** for connection pooling in tests, just like in production, to catch any potential issues with connection management early on.
* Ensure that the Exposed configuration in tests matches production exactly, including transaction management and connection settings, to avoid discrepancies between test and production environments.
* Avoid starting/stopping containers for each test; instead, manage the lifecycle at the suite level to improve performance and reduce overhead.
* Use Testcontainers' built-in support for logging and debugging to troubleshoot any issues with the database during tests, and ensure that logs are easily accessible for analysis.
* Consider using Testcontainers' support for parallel test execution if your test suite is large, but ensure that your database setup can handle concurrent connections and transactions without conflicts.
* Regularly update Testcontainers and related dependencies to benefit from performance improvements, bug fixes, and new features that can enhance your testing strategy.

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
