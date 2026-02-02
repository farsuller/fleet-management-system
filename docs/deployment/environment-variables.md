# Environment Variables Documentation

This document describes all environment variables used by the Fleet Management System.

## Required Variables

### Database Configuration
- `DB_URL`: PostgreSQL connection string
  - Format: `jdbc:postgresql://host:port/database`
  - Default: `jdbc:postgresql://127.0.0.1:5435/fleet_db`
  
- `DB_USER`: Database username
  - Default: `fleet_user`
  
- `DB_PASSWORD`: Database password
  - **Required in production** (no default for security)
  - Default (local only): `secret_123`

- `DB_POOL_SIZE`: HikariCP connection pool size
  - Default: `10`

### Server Configuration
- `PORT`: HTTP server port
  - Default: `8080`

## Optional Variables (Future Phases)

### JWT Authentication (Phase 3+)
- `JWT_SECRET`: Secret key for JWT signing
- `JWT_ISSUER`: JWT issuer claim
- `JWT_AUDIENCE`: JWT audience claim

### Redis (Phase 4+)
- `REDIS_URL`: Redis connection string
  - Format: `redis://host:port`

### Kafka (Phase 4+)
- `KAFKA_BOOTSTRAP_SERVERS`: Kafka broker addresses
  - Format: `host1:port1,host2:port2`

## Local Development

1. Copy `.env.example` to `.env`:
   ```bash
   cp .env.example .env
   ```

2. Update values as needed for your local environment

3. The application will use defaults for most values if not set

## Production Deployment

- **Never commit `.env` files to version control**
- Set environment variables via:
  - Kubernetes Secrets
  - Docker environment variables
  - Cloud provider secret management (AWS Secrets Manager, etc.)
  
- Ensure `DB_PASSWORD` is always set from a secure source
