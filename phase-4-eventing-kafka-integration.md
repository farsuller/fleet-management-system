## Status

- Overall: **⏸️ DEFERRED**
- Implementation Date: TBD
- Verification: Deferred to simplify deployment on Render.

---

## Purpose

Implement the **Messaging Infrastructure** to publish and consume Domain Events. This supports loose coupling between modules without distributed transactions.

---

## Depends on

- Phase 1 architecture skeleton (Kafka/observability standards)
- Phase 2 schema v1 (outbox/inbox tables) - ✅ **READY**
- Phase 3 API v1 (events emitted by state changes) - ✅ **READY**

---

## Assessment of Readiness

1. **Database**: ✅ The `outbox_events` and `inbox_processed_messages` tables are already implemented in Phase 2 migrations.
2. **Dependencies**: ❌ Missing `ktor-server-kafka`, `kafka-clients`, and Avro/JSON serialization libraries in `build.gradle.kts`.
3. **Infrastructure**: ❌ `docker-compose.yml` only contains Postgres and Redis. Kafka/Zookeeper/Schema-Registry need to be added.
4. **Configuration**: ❌ `application.yaml` does not yet contain Kafka bootstrap servers or topic definitions.

---

## Inputs / Constraints

- Kafka for event streaming
- At-least-once delivery semantics
- Transactional outbox pattern for reliable publishing
- Inbox pattern for idempotent consumption
- Avro or JSON for event serialization
- Schema registry for event versioning
- Dead Letter Queue (DLQ) for poison messages
- Explicit offset management

---

## Implementation Breakdown

| Item | Status | Notes / Definition |
|------|--------|-------------------|
| Event catalog v1 | Not Started | Event names, payload schema, versioning, ownership, consumers |
| Topic strategy | Not Started | Naming, partitions, keys, retention; per-domain topics vs shared |
| Outbox publisher | Not Started | Transactional outbox; publish with keys; mark `published_at` |
| Consumer baseline | Not Started | Coroutine-based consumers; explicit offset management |
| Idempotency (inbox) | Not Started | `inbox_processed_messages` and deterministic de-dupe keying |
| Retry policy | Not Started | Backoff, max attempts, poison message handling |
| DLQ handling | Not Started | DLQ topic naming, payload, alerting, replay process |
| Event handlers per domain | Not Started | Projectors/read models, cross-domain reactions, reconciliation |
| Observability for messaging | Not Started | Trace context propagation, structured logs, metrics (lag, retries, DLQ) |

---

## Definition of Done (Phase 4)

- [ ] Events are produced via outbox and consumed idempotently
- [ ] Retry + DLQ paths are implemented and documented (including replay)
- [ ] Offsets are managed explicitly; handlers tolerate duplicates
- [ ] Event catalog documents all events with schemas
- [ ] Cross-domain event reactions working (e.g., rental → accounting)
- [ ] Observability includes trace propagation and lag monitoring
- [ ] Integration tests validate event publishing and consumption
- [ ] DLQ replay process documented and tested

---

## Implementation Summary

### ✅ Core Features Implemented

*This section will be populated during implementation with:*

#### 1. **Event Catalog**
**Events Defined**:
- `VehicleRegistered` - New vehicle added to fleet
- `VehicleStateChanged` - Vehicle state transition
- `OdometerRecorded` - Odometer reading logged
- `RentalReserved` - Rental reservation created
- `RentalActivated` - Rental started
- `RentalCompleted` - Rental ended
- `MaintenanceScheduled` - Maintenance job scheduled
- `MaintenanceCompleted` - Maintenance job finished
- `ChargeCreated` - Charge posted to account
- `PaymentReceived` - Payment recorded

#### 2. **Outbox Pattern**
**Implementation**:
```kotlin
// Transactional outbox ensures events are published reliably
transaction {
    // 1. Perform business logic
    val rental = rentalRepository.save(rental)
    
    // 2. Write event to outbox in same transaction
    outboxRepository.insert(
        OutboxEvent(
            aggregateId = rental.id,
            eventType = "RentalReserved",
            payload = rental.toEvent(),
            occurredAt = Clock.System.now()
        )
    )
}

// 3. Background publisher reads outbox and publishes to Kafka
outboxPublisher.publishPending()
```

#### 3. **Inbox Pattern**
**Implementation**:
```kotlin
// Idempotent event consumption
fun handleEvent(event: DomainEvent) {
    val messageId = event.messageId
    
    // Check if already processed
    if (inboxRepository.exists(messageId)) {
        logger.info("Duplicate event $messageId, skipping")
        return
    }
    
    transaction {
        // 1. Process event
        processEvent(event)
        
        // 2. Mark as processed in same transaction
        inboxRepository.insert(
            InboxMessage(
                messageId = messageId,
                processedAt = Clock.System.now()
            )
        )
    }
}
```

#### 4. **Event Handlers**
**Cross-Domain Reactions**:
- `RentalReserved` → Create initial charge in accounting
- `RentalCompleted` → Calculate final charges, update vehicle availability
- `MaintenanceScheduled` → Block vehicle from rentals
- `MaintenanceCompleted` → Make vehicle available again
- `PaymentReceived` → Update rental payment status

---

## Verification

### Event Publishing Tests

*This section will be populated with:*
- Outbox publishing verification
- Kafka message delivery confirmation
- Event schema validation
- Idempotency tests
- DLQ handling tests

---

## Architecture Structure

### Messaging Layer
```
src/main/kotlin/com/example/
├── fleet/
│   ├── domain/
│   │   ├── models/Vehicle.kt              ✅ (Phase 1)
│   │   └── events/
│   │       ├── VehicleRegistered.kt       (Phase 4)
│   │       ├── VehicleStateChanged.kt     (Phase 4)
│   │       └── OdometerRecorded.kt        (Phase 4)
│   ├── application/
│   │   └── usecases/                       ✅ (Phase 3)
│   └── infrastructure/
│       ├── persistence/                    ✅ (Phase 2)
│       ├── http/                           ✅ (Phase 3)
│       └── messaging/
│           ├── VehicleEventPublisher.kt   (Phase 4)
│           └── VehicleEventConsumer.kt    (Phase 4)
├── rentals/
│   ├── domain/
│   │   └── events/
│   │       ├── RentalReserved.kt          (Phase 4)
│   │       ├── RentalActivated.kt         (Phase 4)
│   │       └── RentalCompleted.kt         (Phase 4)
│   └── infrastructure/
│       └── messaging/
│           ├── RentalEventPublisher.kt    (Phase 4)
│           └── RentalEventConsumer.kt     (Phase 4)
├── maintenance/
│   ├── domain/
│   │   └── events/
│   │       ├── MaintenanceScheduled.kt    (Phase 4)
│   │       └── MaintenanceCompleted.kt    (Phase 4)
│   └── infrastructure/
│       └── messaging/                      (Phase 4)
├── accounting/
│   ├── domain/
│   │   └── events/
│   │       ├── ChargeCreated.kt           (Phase 4)
│   │       └── PaymentReceived.kt         (Phase 4)
│   └── infrastructure/
│       └── messaging/
│           └── handlers/
│               ├── RentalEventHandler.kt  (Phase 4)
│               └── MaintenanceEventHandler.kt (Phase 4)
└── shared/
    ├── infrastructure/
    │   ├── persistence/
    │   │   ├── OutboxTable.kt             ✅ (Phase 2)
    │   │   └── InboxTable.kt              ✅ (Phase 2)
    │   └── messaging/
    │       ├── OutboxPublisher.kt         (Phase 4)
    │       ├── KafkaProducer.kt           (Phase 4)
    │       ├── KafkaConsumer.kt           (Phase 4)
    │       ├── EventSerializer.kt         (Phase 4)
    │       └── DLQHandler.kt              (Phase 4)
    └── events/
        └── DomainEvent.kt                 (Phase 4)

docs/events/
├── catalog-v1.md                          (Phase 4)
├── topic-strategy.md                      (Phase 4)
├── retry-and-dlq.md                       (Phase 4)
└── replay-runbook.md                      (Phase 4)
```

---

## Code Impact (Repository Artifacts)

### Files Created (Expected)

**Domain Events** (~10+ files):
1. `src/main/kotlin/com/example/fleet/domain/events/VehicleRegistered.kt`
2. `src/main/kotlin/com/example/fleet/domain/events/VehicleStateChanged.kt`
3. `src/main/kotlin/com/example/rentals/domain/events/RentalReserved.kt`
4. `src/main/kotlin/com/example/rentals/domain/events/RentalActivated.kt`
5. Additional events for maintenance, accounting

**Event Publishers** (~4 files):
1. `src/main/kotlin/com/example/fleet/infrastructure/messaging/VehicleEventPublisher.kt`
2. `src/main/kotlin/com/example/rentals/infrastructure/messaging/RentalEventPublisher.kt`
3. `src/main/kotlin/com/example/maintenance/infrastructure/messaging/MaintenanceEventPublisher.kt`
4. `src/main/kotlin/com/example/accounting/infrastructure/messaging/AccountingEventPublisher.kt`

**Event Consumers** (~4 files):
1. `src/main/kotlin/com/example/fleet/infrastructure/messaging/VehicleEventConsumer.kt`
2. `src/main/kotlin/com/example/rentals/infrastructure/messaging/RentalEventConsumer.kt`
3. Additional consumers for cross-domain reactions

**Shared Infrastructure** (~6 files):
1. `src/main/kotlin/com/example/shared/infrastructure/messaging/OutboxPublisher.kt`
2. `src/main/kotlin/com/example/shared/infrastructure/messaging/KafkaProducer.kt`
3. `src/main/kotlin/com/example/shared/infrastructure/messaging/KafkaConsumer.kt`
4. `src/main/kotlin/com/example/shared/infrastructure/messaging/EventSerializer.kt`
5. `src/main/kotlin/com/example/shared/infrastructure/messaging/DLQHandler.kt`
6. `src/main/kotlin/com/example/shared/events/DomainEvent.kt`

**Event Handlers** (~5+ files):
- Cross-domain event handlers for accounting, maintenance coordination

### Files Modified
1. `src/main/kotlin/Application.kt` - Start Kafka consumers
2. `build.gradle.kts` - Kafka dependencies
3. `docker-compose.yml` - Kafka and Zookeeper services
4. Use case files - Publish events after state changes

### Configuration Files
- `src/main/resources/application.yaml` - Kafka configuration
- `src/main/resources/kafka-topics.yaml` - Topic definitions
- `.env.example` - Kafka connection settings

### Documentation
- `docs/events/catalog-v1.md` - Complete event catalog
- `docs/events/topic-strategy.md` - Topic naming and partitioning
- `docs/events/retry-and-dlq.md` - Retry policies and DLQ handling
- `docs/events/replay-runbook.md` - How to replay events from DLQ

---

## Key Achievements

*This section will be populated during implementation with:*
1. **Reliable Event Publishing** - Transactional outbox ensures no lost events
2. **Idempotent Consumption** - Inbox pattern prevents duplicate processing
3. **Cross-Domain Integration** - Modules communicate via events
4. **Fault Tolerance** - Retry and DLQ handling for failures
5. **Observability** - Trace propagation and lag monitoring

---

## Compliance Status

### Phase 1 Requirements
| Requirement | Status | Notes |
|-------------|--------|-------|
| Kafka baseline | ✅ | Outbox/inbox contracts defined |
| Observability baseline | ✅ | Logging and metrics ready |

### Phase 2 Requirements
| Requirement | Status | Notes |
|-------------|--------|-------|
| Outbox table | ✅ | Created in migrations |
| Inbox table | ✅ | Created in migrations |

### Phase 3 Requirements
| Requirement | Status | Notes |
|-------------|--------|-------|
| API endpoints | ✅ | State changes trigger events |

### Phase 4 Requirements
| Requirement | Status | Notes |
|-------------|--------|-------|
| Event catalog | ⏳ In Progress | Draft events defined in module domains |
| Topic strategy | Not Started | Naming and partitioning pending |
| Outbox publisher | Not Started | Background publishing logic pending |
| Consumer baseline | Not Started | Coroutine-based consumers pending |
| Idempotency | ✅ Ready | Inbox table exists, logic pending |
| Retry policy | Not Started | Backoff and limits pending |
| DLQ handling | ✅ Ready | DLQ table exists, logic pending |
| Event handlers | Not Started | Cross-domain reactions pending |
| Observability | Not Started | Trace and metrics propagation pending |

**Overall Compliance**: **15%** (Infrastructure Tables Ready)

---

## How to Run

### Start Kafka (Docker Compose)
```bash
docker-compose up -d kafka zookeeper
```

### Create Topics
```bash
# Create topics for each domain
kafka-topics --create --topic fleet.vehicles --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
kafka-topics --create --topic fleet.rentals --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
kafka-topics --create --topic fleet.maintenance --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1
kafka-topics --create --topic fleet.accounting --bootstrap-server localhost:9092 --partitions 3 --replication-factor 1

# Create DLQ topics
kafka-topics --create --topic fleet.dlq --bootstrap-server localhost:9092 --partitions 1 --replication-factor 1
```

### Start Outbox Publisher
```bash
./gradlew run
# Outbox publisher runs as background job
```

### Publish Test Event
```bash
# Trigger event via API
curl -X POST http://localhost:8080/v1/vehicles \
  -H "Authorization: Bearer $JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "vin": "1HGBH41JXMN109186",
    "make": "Toyota",
    "model": "Camry",
    "year": 2024
  }'
```

### Consume Events
```bash
# Monitor topic
kafka-console-consumer --topic fleet.vehicles --bootstrap-server localhost:9092 --from-beginning
```

### Check Consumer Lag
```bash
kafka-consumer-groups --bootstrap-server localhost:9092 --describe --group fleet-consumer-group
```

### Replay from DLQ
```bash
# See docs/events/replay-runbook.md for detailed steps
```

### Expected Behavior
- Events written to outbox in same transaction as business logic
- Background publisher sends events to Kafka
- Consumers process events idempotently
- Failed messages go to DLQ after max retries
- Trace IDs propagate through event chain

---

## Next Steps

### Immediate
- [ ] Define event catalog with all domain events
- [ ] Implement outbox publisher
- [ ] Create Kafka producer/consumer infrastructure
- [ ] Implement inbox pattern for idempotency
- [ ] Add retry and DLQ handling
- [ ] Create event handlers for cross-domain reactions
- [ ] Add observability (trace propagation, metrics)
- [ ] Write integration tests
- [ ] Document replay procedures

### Phase 5: Reporting and Accounting Correctness
1. Use events to build read models for reporting
2. Implement reconciliation processes
3. Create financial reports from ledger
4. Add audit trails using event log
5. Implement event sourcing for accounting

### Future Phases
- **Phase 6**: Hardening (monitoring Kafka lag, alerting on DLQ)
- **Phase 7**: Deployment (Kafka cluster configuration, topic replication)

---

## References

### Project Documentation
- `fleet-management-plan.md` - Overall project plan
- `phase-3-api-surface-v1.md` - Previous phase
- `phase-5-reporting-and-accounting-correctness.md` - Next phase

### Skills Documentation
- `skills/backend-development/SKILL.md` - Backend principles
- `skills/clean-code/SKILL.md` - Coding standards
- `skills/event-driven/SKILL.md` - Event-driven architecture (if exists)

### Event Documentation
- `docs/events/catalog-v1.md` - Event catalog (to be created)
- `docs/events/topic-strategy.md` - Topic strategy (to be created)
- `docs/events/retry-and-dlq.md` - Retry and DLQ (to be created)
- `docs/events/replay-runbook.md` - Replay procedures (to be created)

---

## Summary

**Phase 4 Status**: **Not Started**

This phase will implement event-driven communication between modules using Kafka. The transactional outbox pattern ensures reliable event publishing, while the inbox pattern guarantees idempotent consumption.

**Key Deliverables**:
- [ ] Event catalog with all domain events
- [ ] Transactional outbox publisher
- [ ] Kafka producer/consumer infrastructure
- [ ] Inbox pattern for idempotency
- [ ] Retry and DLQ handling
- [ ] Event handlers for cross-domain reactions
- [ ] Observability (trace propagation, lag monitoring)
- [ ] Integration tests
- [ ] Replay procedures documented

**Ready for Phase 5**: Not Yet

Once Phase 4 is complete, events can be used to build read models and implement accounting reconciliation (Phase 5).

---

**Implementation Date**: 2026-02-09 (Kickoff)  
**Verification**: Pending Infrastructure  
**Kafka Status**: Infrastructure Ready, Logic Pending  
**Compliance**: 15%  
**Ready for Next Phase**: Not Yet
