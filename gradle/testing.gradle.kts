import org.gradle.api.tasks.testing.Test

tasks.named<Test>("test") {
    useJUnitPlatform()
    outputs.upToDateWhen { false } // always rerun — never skip as UP-TO-DATE
    // Testcontainers on Windows with Docker Desktop.
    // docker.host is configured in src/test/resources/testcontainers.properties.
    // Ryuk disabled to prevent reaper container errors on Windows Docker Desktop.
    environment("TESTCONTAINERS_RYUK_DISABLED", "true")

    // Collect failed test class names so the summary can show them
    val failedClasses = mutableSetOf<String>()

    afterTest(
        KotlinClosure2({ desc: TestDescriptor, result: TestResult ->
            if (result.resultType == TestResult.ResultType.FAILURE) {
                val className = desc.className ?: desc.parent?.className ?: "Unknown"
                failedClasses += className.substringAfterLast('.')
                println("  FAILED: ${desc.className}#${desc.name}")
                result.exception?.let { println("         ${it.message}") }
            }
        }),
    )

    afterSuite(
        KotlinClosure2({ desc: TestDescriptor, result: TestResult ->
            if (desc.parent == null) {
                val pass = result.successfulTestCount
                val fail = result.failedTestCount
                val skip = result.skippedTestCount
                val total = result.testCount
                val outcome = if (result.resultType == TestResult.ResultType.SUCCESS) "PASSED" else "FAILED"
                if (failedClasses.isNotEmpty()) {
                    println("Failed classes: ${failedClasses.sorted().joinToString(", ")}")
                }
                println("Test Results: $outcome")
                println("Total: $total  |  Passed: $pass  |  Failed: $fail  |  Skipped: $skip")
            }
        }),
    )

    finalizedBy("jacocoTestReport")
}
