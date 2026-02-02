# Phase 3: Maintenance API Implementation Guide

**Implementation Date**: Pending  
**Verification**: Not yet started  
**Server Status**: ⏳ PENDING  
**Compliance**: 0%  
**Ready for Next Phase**: ❌ NO

This guide covers the implementation for the Maintenance domain.

## 1. Directory Structure

```text
src/main/kotlin/com/solodev/fleet/modules/
├── maintenance/
│   ├── application/
│   │   ├── dto/            <-- MaintenanceJobRequest, MaintenancePartDTO
│   │   └── usecases/       <-- ScheduleMaintenanceUseCase, StartJobUseCase
│   └── infrastructure/
│       └── http/           <-- MaintenanceRoutes.kt
```

## 2. Implementation Checklist

### A. DTOs
- [ ] `MaintenanceJobRequest.kt`: Fields for vehicleId, type, description, scheduled date.
- [ ] `MaintenanceJobResponse.kt`: Job status and cost breakdown.
- [ ] `MaintenancePartDTO.kt`: List of parts used.

### B. Use Cases
- [ ] `ScheduleJobUseCase.kt`: Create a job with status SCHEDULED.
- [ ] `StartJobUseCase.kt`: Transition to IN_PROGRESS.
- [ ] `CompleteJobUseCase.kt`: Transition to COMPLETED with cost updates.

### C. Routes
- [ ] `MaintenanceRoutes.kt`: 
  - `POST /v1/maintenance/jobs`
  - `GET /v1/maintenance/jobs/{id}`
  - `POST /v1/maintenance/jobs/{id}/start`
  - `POST /v1/maintenance/jobs/{id}/complete`

## 3. Code Samples

### MaintenanceJobResponse Mapper
```kotlin
fun fromDomain(j: MaintenanceJob) = MaintenanceJobResponse(
    id = j.id.value,
    jobNumber = j.jobNumber,
    vehicleId = j.vehicleId.value,
    status = j.status.name,
    jobType = j.jobType.name,
    totalCost = j.totalCostCents
)
```

---

## 3. API Endpoints & Sample Payloads

### **A. Schedule Maintenance Job**
- **Endpoint**: `POST /v1/maintenance/jobs`
- **Request Body**:
```json
{
  "vehicleId": "v-7b9c1d2e",
  "jobType": "ROUTINE",
  "description": "Oil change and tire rotation",
  "scheduledDate": "2024-03-01T09:00:00Z"
}
```
- **Response Body (200 OK)**:
```json
{
  "success": true,
  "data": {
    "id": "job-101",
    "jobNumber": "MNT-001",
    "vehicleId": "v-7b9c1d2e",
    "status": "SCHEDULED",
    "jobType": "ROUTINE",
    "totalCost": 0
  },
  "requestId": "req-222"
}
```

---

## 4. Wiring
In `Routing.kt`:
```kotlin
val maintenanceRepo = MaintenanceRepositoryImpl()
maintenanceRoutes(maintenanceRepo)
```
