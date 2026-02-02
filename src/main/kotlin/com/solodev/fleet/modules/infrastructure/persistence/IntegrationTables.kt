package com.solodev.fleet.modules.infrastructure.persistence

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.timestamp

/** Exposed table definition for outbox events (transactional outbox pattern). */
object OutboxEventsTable : UUIDTable("outbox_events") {
    val aggregateType = varchar("aggregate_type", 100)
    val aggregateId = varchar("aggregate_id", 255)
    val eventType = varchar("event_type", 100)
    val payload = text("payload") // JSONB stored as text
    val metadata = text("metadata").nullable() // JSONB stored as text
    
    // Publishing tracking
    val publishedAt = timestamp("published_at").nullable()
    val publishedToTopic = varchar("published_to_topic", 255).nullable()
    val publishAttempts = integer("publish_attempts").default(0)
    val lastPublishAttemptAt = timestamp("last_publish_attempt_at").nullable()
    val lastPublishError = text("last_publish_error").nullable()
    
    // Metadata
    val occurredAt = timestamp("occurred_at")
    val createdAt = timestamp("created_at")
}

/** Exposed table definition for inbox processed messages (idempotent consumption). */
object InboxProcessedMessagesTable : UUIDTable("inbox_processed_messages") {
    val messageId = varchar("message_id", 255).uniqueIndex()
    val consumerGroup = varchar("consumer_group", 100)
    val topic = varchar("topic", 255)
    val partitionNum = integer("partition_num")
    val offsetNum = long("offset_num")
    val eventType = varchar("event_type", 100)
    val payload = text("payload") // JSONB stored as text
    val processedAt = timestamp("processed_at")
    val processingDurationMs = integer("processing_duration_ms").nullable()
}

/** Exposed table definition for dead letter queue messages. */
object DlqMessagesTable : UUIDTable("dlq_messages") {
    val originalMessageId = varchar("original_message_id", 255).nullable()
    val consumerGroup = varchar("consumer_group", 100)
    val topic = varchar("topic", 255)
    val partitionNum = integer("partition_num")
    val offsetNum = long("offset_num")
    val eventType = varchar("event_type", 100)
    val payload = text("payload") // JSONB stored as text
    val errorMessage = text("error_message")
    val stackTrace = text("stack_trace").nullable()
    val retryCount = integer("retry_count").default(0)
    val lastRetryAt = timestamp("last_retry_at").nullable()
    val status = varchar("status", 20).default("PENDING")
    val resolvedAt = timestamp("resolved_at").nullable()
    val resolvedByUserId = uuid("resolved_by_user_id").nullable()
    val resolutionNotes = text("resolution_notes").nullable()
    val failedAt = timestamp("failed_at")
    val createdAt = timestamp("created_at")
}

/** Exposed table definition for idempotency keys. */
object IdempotencyKeysTable : UUIDTable("idempotency_keys") {
    val idempotencyKey = varchar("idempotency_key", 255).uniqueIndex()
    val requestPath = varchar("request_path", 500)
    val requestMethod = varchar("request_method", 10)
    val requestBody = text("request_body").nullable() // JSONB stored as text
    val responseStatus = integer("response_status").nullable()
    val responseBody = text("response_body").nullable() // JSONB stored as text
    val createdByUserId = uuid("created_by_user_id").nullable()
    val createdAt = timestamp("created_at")
    val expiresAt = timestamp("expires_at")
}
