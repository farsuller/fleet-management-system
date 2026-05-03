import org.gradle.testing.jacoco.plugins.JacocoPluginExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.gradle.testing.jacoco.tasks.JacocoCoverageVerification

// JaCoCo Code Coverage Configuration
configure<JacocoPluginExtension> {
    toolVersion = project.extra["jacocoToolVersion"] as String
}

val jacocoCoverageExclusions =
    listOf(
        "**/*Table.class",
        "**/*Table$*.class",
        "**/*Tables.class",
        "**/*Tables$*.class",
        // DTOs — plain data carriers with no business logic
        "**/dto/*Request.class",
        "**/dto/*Response.class",
        "**/dto/*DTO.class",
        "**/dto/*Dtos.class",
        // Shared utilities, helpers, and base infrastructure
        "**/shared/**",
        // Repository implementations — infrastructure/persistence layer wired to the DB
        "**/persistence/*RepositoryImpl.class",
        "**/persistence/*RepositoryImpl$*.class",
    )

tasks.withType<JacocoReport>().configureEach {
    classDirectories.setFrom(
        files(
            classDirectories.files.map { directory ->
                fileTree(directory) {
                    exclude(jacocoCoverageExclusions)
                }
            },
        ),
    )
}

tasks.withType<JacocoCoverageVerification>().configureEach {
    classDirectories.setFrom(
        files(
            classDirectories.files.map { directory ->
                fileTree(directory) {
                    exclude(jacocoCoverageExclusions)
                }
            },
        ),
    )

    violationRules {
        rule {
            limit {
                // Keep local threshold aligned with CI coverage gate.
                minimum = "0.40".toBigDecimal()
            }
        }
    }
}

tasks.named<JacocoReport>("jacocoTestReport") {
    dependsOn("test")
    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    // Ensure the console summary table is printed even if the task is technically Up-To-Date.
    outputs.upToDateWhen { false }

    // Custom Console Reporter
    // Parses the resulting XML and prints a formatted summary table to the terminal,
    // providing immediate visibility into test coverage levels for each class.
    doLast {
        val xmlFile = reports.xml.outputLocation.get().asFile
        if (xmlFile.exists()) {
            logger.quiet("\n--- Code Coverage Summary ---")
            logger.quiet(String.format("%-70s | %-10s", "Class", "Coverage"))
            logger.quiet("-".repeat(85))

            try {
                val xml = xmlFile.readText()

                // Helper to extract missed and covered attributes
                fun extract(counterBlock: String, attr: String): Double {
                    val search = "$attr=\""
                    if (!counterBlock.contains(search)) return 0.0
                    return counterBlock.substringAfter(search).substringBefore("\"").toDoubleOrNull() ?: 0.0
                }

                // Patterns derived from jacocoCoverageExclusions, translated to simple class name matching
                val excludedPatterns = listOf(
                    Regex(".*Table\$"),
                    Regex(".*Table\\\$.*"),
                    Regex(".*Tables\$"),
                    Regex(".*Tables\\\$.*"),
                    // Also skip Kotlin-generated inner lambdas ($1, $2, etc.) and $Companion
                    Regex(".*\\\$\\d+.*"),
                    Regex(".*\\\$Companion.*"),
                    // DTOs — plain data carriers with no business logic
                    Regex(".*/dto/.*Request"),
                    Regex(".*/dto/.*Response"),
                    Regex(".*/dto/.*DTO"),
                    Regex(".*/dto/.*Dtos"),
                    Regex(".*/dto/.*Dto"),
                    // Shared package
                    Regex(".*/shared/.*"),
                    // Repository implementations — infrastructure/persistence layer
                    Regex(".*/persistence/.*RepositoryImpl"),
                    Regex(""".*/persistence/.*RepositoryImpl\$.*"""),
                )

                // Robust parsing: Split XML into class blocks and iterate
                xml.split("<class ").drop(1).forEach { classBlock ->
                    val fullName = classBlock.substringAfter("name=\"").substringBefore("\"")
                    val name = fullName.substringAfterLast("/")

                    // Skip excluded classes
                    if (excludedPatterns.any { it.matches(fullName) }) return@forEach

                    // Extract aggregate class-level counters (ignoring method-level counters if present)
                    val lastCounters = classBlock
                        .substringAfterLast("</method>")
                        .ifEmpty { classBlock.substringAfter(">") }
                        .substringBefore("</class>")

                    // Prioritize LINE coverage, fallback to INSTRUCTION
                    val lineBlock = lastCounters.substringAfter("type=\"LINE\"", "")
                    val instrBlock = lastCounters.substringAfter("type=\"INSTRUCTION\"", "")
                    val activeBlock = if (lineBlock.isNotEmpty()) lineBlock else instrBlock

                    if (activeBlock.isNotEmpty()) {
                        val missed = extract(activeBlock, "missed")
                        val covered = extract(activeBlock, "covered")
                        val total = missed + covered
                        val coverage = if (total > 0) (covered / total) * 100 else 0.0
                        logger.quiet(String.format("%-70s | %6.2f%%", name, coverage))
                    }
                }

                // Final Summary Logic: Extract the aggregate counters for the entire report
                val reportCounters = xml.substringAfterLast("</package>").substringBefore("</report>")
                val totalLine = reportCounters.substringAfter("type=\"LINE\"", "")
                val totalInstr = reportCounters.substringAfter("type=\"INSTRUCTION\"", "")
                val finalBlock = if (totalLine.isNotEmpty()) totalLine else totalInstr

                if (finalBlock.isNotEmpty()) {
                    val missed = extract(finalBlock, "missed")
                    val covered = extract(finalBlock, "covered")
                    val total = missed + covered
                    val ratio = if (total > 0) (covered / total) * 100 else 0.0

                    logger.quiet("-".repeat(85))
                    logger.quiet(String.format("%-70s | %6.2f%%", "OVERALL PROJECT COVERAGE", ratio))
                    logger.quiet(String.format("%-70s | %6s%%", "EXPECTED MINIMUM TARGET", "40.00"))

                    if (ratio < 40.0) {
                        logger.quiet("\n[WARNING] Coverage is below the required 40% threshold. Build verification may fail.")
                    } else {
                        logger.quiet("\n[SUCCESS] Quality gate passed: Coverage is within expected limits.")
                    }
                }
            } catch (e: Exception) {
                logger.quiet("Note: Detailed coverage overview truncated. Full report: ${reports.html.outputLocation.get()}")
            }
            logger.quiet("-".repeat(85))
        }
    }
}

// Custom task for running coverage locally
tasks.register("testCoverage") {
    group = "verification"
    description = "Runs all tests and generates a JaCoCo coverage report."
    dependsOn("test", "jacocoTestReport")
}

tasks.register("jacocoVerify") {
    group = "verification"
    description = "Runs tests, generates JaCoCo report, and verifies minimum coverage."
    dependsOn("test", "jacocoTestReport", "jacocoTestCoverageVerification")
}
