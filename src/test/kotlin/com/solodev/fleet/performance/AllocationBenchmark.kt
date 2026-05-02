package com.solodev.fleet.performance

import org.junit.jupiter.api.Test
import kotlin.system.measureNanoTime

/**
 * A micro-benchmark to demonstrate Phase 1 RAM optimizations.
 * Run this to see the nanosecond-level gains and theoretical allocation savings.
 */
class AllocationBenchmark {
    private val iterations = 1_000_000

    @Test
    fun runBenchmarks() {
        println("\n=== Phase 1 Optimization Benchmark ===")
        println("Iterations: $iterations\n")

        benchmarkRegex()
        println()
        benchmarkRepositoryPattern()
        println("======================================\n")
    }

    /**
     * Demonstrates the impact of hoisting Regex patterns.
     */
    private fun benchmarkRegex() {
        val email = "test.user@example.com"

        // Hoisted Regex (Optimized)
        val hoistedRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        println("1. Regex Validation (Hoisted vs. Local)")

        val timeOld =
            measureNanoTime {
                repeat(iterations) {
                    val localRegex = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
                    localRegex.matches(email)
                }
            }
        println("   - Local Regex (Old):   ${timeOld / 1_000_000}ms (Compiles Pattern EVERY call)")

        val timeNew =
            measureNanoTime {
                repeat(iterations) {
                    hoistedRegex.matches(email)
                }
            }
        println("   - Hoisted Regex (New): ${timeNew / 1_000_000}ms (Zero re-compilation)")
        println("   Gain: ${"%.1f".format(timeOld.toDouble() / timeNew)}x faster")
    }

    /**
     * Demonstrates the impact of avoiding intermediate list allocations in repositories.
     */
    private fun benchmarkRepositoryPattern() {
        val dummyData = List(1) { "ResultRowMock" }

        println("2. Repository Lookup (.map{}.singleOrNull vs. .singleOrNull()?.toX)")

        val timeOld =
            measureNanoTime {
                repeat(iterations) {
                    // Simulates .map { it.toX() }.singleOrNull()
                    val list = ArrayList<String>(dummyData.size)
                    for (item in dummyData) {
                        list.add("Mapped_$item")
                    }
                    list.firstOrNull()
                }
            }
        println("   - Map Pattern (Old):   ${timeOld / 1_000_000}ms (Allocates ArrayList EVERY call)")

        val timeNew =
            measureNanoTime {
                repeat(iterations) {
                    // Simulates .singleOrNull()?.toX()
                    val row = dummyData.firstOrNull()
                    row?.let { "Mapped_$it" }
                }
            }
        println("   - Nullable Pattern (New): ${timeNew / 1_000_000}ms (Zero ArrayList allocation)")
        println("   Gain: ${"%.1f".format(timeOld.toDouble() / timeNew)}x faster")
    }
}
