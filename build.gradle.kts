import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

group = "com.example"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
    applicationDefaultJvmArgs = listOf("-Dio.ktor.development=true")
}

ktor {
    fatJar {
        archiveFileName.set("fleet-management-all.jar")
    }
}

tasks.jar {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from("src/main/resources") {
        include("**/*")
    }
}

repositories {
    mavenCentral()
    maven { url = uri("https://maven.pkg.jetbrains.space/public/p/ktor/eap") }
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_21)
        allWarningsAsErrors.set(false)
        freeCompilerArgs.set(listOf("-Xjvm-default=all"))
    }
}

dependencies {
    // --- Ktor Server Runtime ---
    implementation(libs.ktor.server.core)                 // Core Ktor framework
    implementation(libs.ktor.server.netty)                // Netty engine for high-performance HTTP
    implementation(libs.ktor.server.status.pages)         // Standardized error handling & status mapping
    
    // --- Security & Authentication ---
    implementation(libs.ktor.server.auth)                 // Authentication baseline
    implementation(libs.ktor.server.auth.jwt)             // JWT validation and claim handling
    implementation(libs.bcrypt)                           // Secure password hashing
    
    // --- API Features & Serialization ---
    implementation(libs.ktor.server.content.negotiation)  // Content negotiation (JSON, XML, etc.)
    implementation(libs.ktor.serialization.kotlinx.json)  // Kotlinx.serialization for JSON support
    implementation(libs.ktor.server.rate.limit)           // API protection against brute force/DOS
    implementation(libs.ktor.server.openapi)              // OpenAPI spec generation
    implementation(libs.ktor.server.swagger)              // Swagger UI for API testing
    
    // --- Observability & Configuration ---
    implementation(libs.ktor.server.call.logging)         // Request/Response logging
    implementation(libs.ktor.server.metrics.micrometer)   // Metrics collection framework
    implementation(libs.micrometer.registry.prometheus)   // Prometheus integration for metrics
    implementation(libs.ktor.server.config.yaml)          // YAML support for application.yaml
    implementation(libs.logback.classic)                  // Standard logging implementation
    implementation(libs.logstash.logback.encoder)
    
    // --- Database (Exposed ORM) ---
    implementation(libs.exposed.core)                      // Type-safe SQL DSL (prevents SQLi)
    implementation(libs.exposed.jdbc)                      // JDBC support for Exposed
    implementation(libs.exposed.java.time)                 // Java Time support for Exposed columns
    implementation(libs.exposed.json)                      // JSONB support for PostgreSQL
    implementation(libs.hikaricp)                          // High-performance connection pooling
    implementation(libs.postgresql)                        // PostgreSQL database driver

    implementation(libs.jedis)


    implementation(libs.resilience4j.circuitbreaker)       // Circuit breaker
    implementation(libs.resilience4j.kotlin)               // Resilience4j Kotlin extensions

    // --- Database Migrations ---
    implementation(libs.flyway.core)                       // Database version control
    implementation(libs.flyway.database.postgresql)        // Flyway support for PostgreSQL
    
    // --- Testing ---
    testImplementation(libs.ktor.server.test.host)         // In-memory Ktor server for integration tests
    testImplementation(libs.ktor.client.content.negotiation) // Ktor client for testing endpoints
    testImplementation(libs.kotlin.test.junit)             // Kotlin-style JUnit testing
    testImplementation(libs.h2)                            // In-memory DB for fast testing
}
