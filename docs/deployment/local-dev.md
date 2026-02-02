# Local Development

Goal: every contributor can run the system locally with consistent dependencies and configuration.

## Dependencies

- PostgreSQL (source of truth)
- Kafka (domain events)
- Redis (cache/locks only)

## Options

### Option A: Docker Compose (default)

- Use Compose to run PostgreSQL/Kafka/Redis locally.
- Keep configuration via environment variables with sensible defaults.

### Option B: Testcontainers (for tests)

- Use Testcontainers for integration tests and parity.
- Prefer Testcontainers for CI to avoid “it works on my machine” drift.

## Configuration rules

- All configuration must be environment-driven.
- Secrets are never committed; use env vars locally.
- Local config should mirror production structure (same keys, different values).
