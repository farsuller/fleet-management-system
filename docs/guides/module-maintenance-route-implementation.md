# Phase 3: Maintenance API Implementation Guide

**Original Implementation**: Pending  
**Enhanced Implementation**: Pending (Awaiting Skills Application)  
**Verification**: Not yet started  
**Server Status**: ⏳ PENDING  
**Compliance**: 0%  
**Standards**: Will follow IMPLEMENTATION-STANDARDS.md  
**Skills to Apply**: Backend Development, Clean Code, API Patterns, Lint & Validate

This guide details the implementation for the Maintenance module, covering job scheduling and tracking.

---

## 1. Directory Structure

```text
src/main/kotlin/com/solodev/fleet/modules/maintenance/
├── application/
│   ├── dto/
│   │   └── MaintenanceDTO.kt
│   └── usecases/
│       ├── ScheduleMaintenanceUseCase.kt
│       ├── StartMaintenanceUseCase.kt
│       └── CompleteMaintenanceUseCase.kt
└── infrastructure/
    └── http/
        └── MaintenanceRoutes.kt
```

---

## 2. Domain Model

### Maintenance.kt
`src/main/kotlin/com/solodev/fleet/modules/domain/models/Maintenance.kt`

```kotlin
package com.solodev.fleet.modules.domain.models

import java.time.Instant

/** Maintenance job status. */
enum class MaintenanceStatus {
    SCHEDULED, IN_PROGRESS, COMPLETED, CANCELLED
}

/**
 * Maintenance job domain entity.
 */
data class MaintenanceJob(
    val id: MaintenanceJobId,
    val jobNumber: String,
    val vehicleId: VehicleId,
    val status: MaintenanceStatus,
    val jobType: MaintenanceJobType,
    val description: String,
    val scheduledDate: Instant,
    val laborCostCents: Int = 0,
    val partsCostCents: Int = 0,
    val currencyCode: String = "PHP"
) {
    val totalCostCents: Int get() = laborCostCents + partsCostCents
}
```

---

## 3. Data Transfer Objects (DTOs)

### **MaintenanceDTO.kt**
`src/main/kotlin/com/solodev/fleet/modules/maintenance/application/dto/MaintenanceDTO.kt`
```kotlin
package com.solodev.fleet.modules.maintenance.application.dto

import com.solodev.fleet.modules.domain.models.MaintenanceJob
import kotlinx.serialization.Serializable

@Serializable
data class MaintenanceRequest(
    val vehicleId: String,
    val jobType: String, // ROUTINE, REPAIR, etc.
    val description: String,
    val priority: String, // NORMAL, HIGH, etc.
    val scheduledDate: String // ISO-8601
)

@Serializable
data class MaintenanceResponse(
    val id: String,
    val jobNumber: String,
    val vehicleId: String,
    val status: String,
    val jobType: String,
    val description: String,
    val scheduledDate: String,
    val totalCostCents: Int? = null
) {
    companion object {
        fun fromDomain(j: MaintenanceJob) = MaintenanceResponse(
            id = j.id.value,
            jobNumber = j.jobNumber,
            vehicleId = j.vehicleId.value,
            status = j.status.name,
            jobType = j.jobType.name,
            description = j.description,
            scheduledDate = j.scheduledDate.toString(),
            totalCostCents = j.totalCostCents
        )
    }
}
```

---

## 4. Repository Implementation

### MaintenanceRepositoryImpl.kt
`src/main/kotlin/com/solodev/fleet/modules/infrastructure/persistence/MaintenanceRepositoryImpl.kt`

```kotlin
class MaintenanceRepositoryImpl : MaintenanceRepository {
    override suspend fun saveJob(job: MaintenanceJob): MaintenanceJob = dbQuery {
        val exists = MaintenanceJobsTable.select { MaintenanceJobsTable.id eq UUID.fromString(job.id.value) }.count() > 0
        if (exists) {
            MaintenanceJobsTable.update({ MaintenanceJobsTable.id eq UUID.fromString(job.id.value) }) {
                it[status] = job.status.name
                it[updatedAt] = Instant.now()
            }
        } else {
            MaintenanceJobsTable.insert {
                it[id] = UUID.fromString(job.id.value)
                it[jobNumber] = job.jobNumber
                it[vehicleId] = UUID.fromString(job.vehicleId.value)
                it[status] = job.status.name
                it[createdAt] = Instant.now()
                it[updatedAt] = Instant.now()
            }
        }
        job
    }
}
```

---

## 5. Application Use Cases

### **ScheduleMaintenanceUseCase.kt**
```kotlin
package com.solodev.fleet.modules.maintenance.application.usecases

import com.solodev.fleet.modules.domain.models.*
import com.solodev.fleet.modules.domain.ports.MaintenanceRepository
import com.solodev.fleet.modules.maintenance.application.dto.MaintenanceRequest
import java.time.Instant
import java.util.*

class ScheduleMaintenanceUseCase(private val repository: MaintenanceRepository) {
    suspend fun execute(request: MaintenanceRequest): MaintenanceJob {
        val job = MaintenanceJob(
            id = MaintenanceJobId(UUID.randomUUID().toString()),
            jobNumber = "JOB-${System.currentTimeMillis()}",
            vehicleId = VehicleId(request.vehicleId),
            status = MaintenanceStatus.SCHEDULED,
            jobType = MaintenanceJobType.valueOf(request.jobType),
            description = request.description,
            priority = MaintenancePriority.valueOf(request.priority),
            scheduledDate = Instant.parse(request.scheduledDate)
        )
        return repository.saveJob(job)
    }
}
```

---

## 6. Ktor Routes

### **MaintenanceRoutes.kt**
```kotlin
package com.solodev.fleet.modules.maintenance.infrastructure.http

import com.solodev.fleet.modules.domain.ports.MaintenanceRepository
import com.solodev.fleet.modules.maintenance.application.dto.*
import com.solodev.fleet.modules.maintenance.application.usecases.*
import com.solodev.fleet.shared.models.ApiResponse
import com.solodev.fleet.shared.plugins.requestId
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.maintenanceRoutes(repository: MaintenanceRepository) {
    val scheduleUseCase = ScheduleMaintenanceUseCase(repository)

    route("/v1/maintenance") {
        post {
            val request = call.receive<MaintenanceRequest>()
            val job = scheduleUseCase.execute(request)
            call.respond(ApiResponse.success(MaintenanceResponse.fromDomain(job), call.requestId))
        }

        get("/vehicle/{vehicleId}") {
            val vehicleId = call.parameters["vehicleId"] ?: return@get
            val jobs = repository.findByVehicleId(com.solodev.fleet.modules.domain.models.VehicleId(vehicleId))
            call.respond(ApiResponse.success(jobs.map { MaintenanceResponse.fromDomain(it) }, call.requestId))
        }
    }
}
```

---

## 7. Testing

### Integration Tests
```kotlin
@Test fun `should schedule and retrieve maintenance job`() = runBlocking {
    val repo = MaintenanceRepositoryImpl()
    val job = createSampleJob()
    repo.saveJob(job)
    
    val found = repo.findById(job.id)
    assertNotNull(found)
    assertEquals(job.jobNumber, found.jobNumber)
}
```

---

## 8. API Endpoints & Sample Payloads

### **A. Schedule Maintenance**
- **Endpoint**: `POST /v1/maintenance`
- **Request Body**:
```json
{
  "vehicleId": "46b6a07c-...",
  "jobType": "ROUTINE",
  "description": "Annual oil change and tire rotation",
  "priority": "NORMAL",
  "scheduledDate": "2024-07-01T09:00:00Z"
}
```

### **B. List Jobs for Vehicle**
- **Endpoint**: `GET /v1/maintenance/vehicle/46b6a07c-...`
- **Response**:
```json
{
  "success": true,
  "data": [
    {
      "id": "job_123...",
      "jobNumber": "JOB-1717...",
      "status": "SCHEDULED",
      "description": "Annual oil change..."
    }
  ],
  "requestId": "..."
}
```

---

## 9. Wiring
In `Routing.kt`:
```kotlin
val maintenanceRepo = MaintenanceRepositoryImpl() 
routing {
    maintenanceRoutes(maintenanceRepo)
}
```
