-- V006: Create Integration Tables (Outbox and Inbox)
-- This migration creates tables for reliable event publishing and idempotent consumption

-- Outbox events table: Transactional outbox pattern for reliable event publishing
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    aggregate_type VARCHAR(100) NOT NULL, -- e.g., 'Vehicle', 'Rental', 'MaintenanceJob'
    aggregate_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL, -- e.g., 'VehicleRegistered', 'RentalActivated'
    payload JSONB NOT NULL,
    metadata JSONB, -- Additional context (user_id, correlation_id, etc.)
    
    -- Publishing tracking
    published_at TIMESTAMPTZ,
    published_to_topic VARCHAR(255),
    publish_attempts INTEGER NOT NULL DEFAULT 0,
    last_publish_attempt_at TIMESTAMPTZ,
    last_publish_error TEXT,
    
    -- Metadata
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Inbox processed messages table: Idempotent event consumption
CREATE TABLE inbox_processed_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    message_id VARCHAR(255) NOT NULL UNIQUE, -- Unique message identifier from Kafka
    consumer_group VARCHAR(100) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    partition_num INTEGER NOT NULL,
    offset_num BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processing_duration_ms INTEGER,
    
    -- Composite unique constraint to prevent duplicate processing
    CONSTRAINT unique_message_per_consumer UNIQUE (message_id, consumer_group)
);

-- Dead letter queue table: Failed messages for manual review
CREATE TABLE dlq_messages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    original_message_id VARCHAR(255),
    consumer_group VARCHAR(100) NOT NULL,
    topic VARCHAR(255) NOT NULL,
    partition_num INTEGER NOT NULL,
    offset_num BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    error_message TEXT NOT NULL,
    stack_trace TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    last_retry_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'RETRYING', 'RESOLVED', 'IGNORED')),
    resolved_at TIMESTAMPTZ,
    resolved_by_user_id UUID REFERENCES users(id),
    resolution_notes TEXT,
    failed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Idempotency keys table: Track idempotency keys for API requests
CREATE TABLE idempotency_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    request_path VARCHAR(500) NOT NULL,
    request_method VARCHAR(10) NOT NULL,
    request_body JSONB,
    response_status INTEGER,
    response_body JSONB,
    created_by_user_id UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    
    -- Automatically delete expired keys
    CONSTRAINT idempotency_key_not_expired CHECK (expires_at > CURRENT_TIMESTAMP)
);

-- Indexes for performance
CREATE INDEX idx_outbox_events_published_at ON outbox_events(published_at) WHERE published_at IS NULL;
CREATE INDEX idx_outbox_events_aggregate ON outbox_events(aggregate_type, aggregate_id);
CREATE INDEX idx_outbox_events_event_type ON outbox_events(event_type);
CREATE INDEX idx_outbox_events_occurred_at ON outbox_events(occurred_at DESC);

CREATE INDEX idx_inbox_messages_message_id ON inbox_processed_messages(message_id);
CREATE INDEX idx_inbox_messages_consumer_group ON inbox_processed_messages(consumer_group);
CREATE INDEX idx_inbox_messages_processed_at ON inbox_processed_messages(processed_at DESC);

CREATE INDEX idx_dlq_messages_status ON dlq_messages(status);
CREATE INDEX idx_dlq_messages_consumer_group ON dlq_messages(consumer_group);
CREATE INDEX idx_dlq_messages_failed_at ON dlq_messages(failed_at DESC);

CREATE INDEX idx_idempotency_keys_key ON idempotency_keys(idempotency_key);
CREATE INDEX idx_idempotency_keys_expires_at ON idempotency_keys(expires_at);

-- Function to clean up old outbox events
CREATE OR REPLACE FUNCTION cleanup_old_outbox_events()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    -- Delete published events older than 7 days
    DELETE FROM outbox_events
    WHERE published_at IS NOT NULL
      AND published_at < CURRENT_TIMESTAMP - INTERVAL '7 days';
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- Function to clean up old inbox messages
CREATE OR REPLACE FUNCTION cleanup_old_inbox_messages()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    -- Delete processed messages older than 30 days
    DELETE FROM inbox_processed_messages
    WHERE processed_at < CURRENT_TIMESTAMP - INTERVAL '30 days';
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- Function to clean up expired idempotency keys
CREATE OR REPLACE FUNCTION cleanup_expired_idempotency_keys()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    -- Delete expired idempotency keys
    DELETE FROM idempotency_keys
    WHERE expires_at < CURRENT_TIMESTAMP;
    
    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

-- Comments for documentation
COMMENT ON TABLE outbox_events IS 'Transactional outbox pattern for reliable event publishing to Kafka';
COMMENT ON TABLE inbox_processed_messages IS 'Tracks processed messages to ensure idempotent event consumption';
COMMENT ON TABLE dlq_messages IS 'Dead letter queue for failed message processing';
COMMENT ON TABLE idempotency_keys IS 'Tracks idempotency keys for API requests to prevent duplicate operations';

COMMENT ON COLUMN outbox_events.aggregate_id IS 'ID of the domain aggregate that produced this event';
COMMENT ON COLUMN outbox_events.payload IS 'JSON payload of the event';
COMMENT ON COLUMN inbox_processed_messages.message_id IS 'Unique message ID from Kafka headers or generated';
COMMENT ON COLUMN idempotency_keys.idempotency_key IS 'Client-provided idempotency key from Idempotency-Key header';
