# 🔒 Security & Configuration Guide

## Overview
This document explains what sensitive files must be excluded from version control and what files are needed to run the Fleet Management System locally.

---

## 🚨 CRITICAL: Files That MUST NOT Be Committed

### 1. **Environment Files with Secrets**
❌ **NEVER commit these files:**
- `.env` - Contains actual production/development secrets
- `.env.local` - Local overrides with real credentials
- `.env.production` - Production environment secrets
- `.env.staging` - Staging environment secrets
- `.env.development` - Development secrets (if different from `.env`)

✅ **SAFE to commit:**
- `.env.example` - Template with placeholder values (no real secrets)

### 2. **Secret Files & Certificates**
❌ **NEVER commit:**
- `*.key` - Private keys
- `*.pem` - SSL/TLS certificates
- `*.p12` - PKCS#12 keystores
- `*.jks` - Java keystores
- `*.keystore` - Any keystore files
- `secrets/` - Any directory containing secrets

### 3. **Database Files & Dumps**
❌ **NEVER commit:**
- `*.sql` - Database dumps (may contain sensitive data)
- `*.dump` - Database backups
- `*.db` - SQLite/H2 database files
- `*.h2.db` - H2 database files

### 4. **Configuration Files with Hardcoded Credentials**
❌ **NEVER commit:**
- `application-local.yaml` - Local config with real values
- `application-production.yaml` - Production config with secrets
- `config/local.yaml` - Any local configuration
- `docker-compose.override.yml` - May contain local secrets

---

## ✅ Files SAFE to Commit

These files should be committed to version control:

### Configuration Templates
- ✅ `.env.example` - Environment variable template
- ✅ `application.yaml` - Uses `${ENV_VAR}` placeholders
- ✅ `docker-compose.yml` - Infrastructure definition
- ✅ `render.yaml` - Deployment template

### Build & Project Files
- ✅ `build.gradle.kts` - Build configuration
- ✅ `gradle.properties` - Gradle settings (no secrets)
- ✅ `settings.gradle.kts` - Project settings
- ✅ `gradlew`, `gradlew.bat` - Gradle wrapper scripts

### Documentation
- ✅ `README.md` - Project documentation
- ✅ All files in `docs/` - Documentation

### Source Code
- ✅ All files in `src/` - Application source code

---

## 🏃 Running the Application Locally

### Prerequisites
1. **Java 17+** installed
2. **Docker** installed (for PostgreSQL + Redis)
3. **Git** installed

### Step-by-Step Setup

#### 1. Clone the Repository
```bash
git clone <repository-url>
cd fleet-management
```

#### 2. Create Environment File
```bash
# Copy the template
cp .env.example .env

# Edit .env and fill in actual values
# Required variables:
#   - DATABASE_URL (PostgreSQL connection)
#   - REDIS_URL (Redis connection)
#   - JWT_SECRET (generate with: openssl rand -base64 64)
#   - SMTP credentials (for email features)
```

#### 3. Start Infrastructure (PostgreSQL + Redis)
```bash
# Start PostgreSQL and Redis using Docker Compose
docker-compose up -d

# Verify services are running
docker-compose ps
```

#### 4. Run Database Migrations
```bash
# Migrations run automatically on application startup
# Or manually run: ./gradlew flywayMigrate
```

#### 5. Start the Application
```bash
# Using Gradle
./gradlew run

# Or build and run JAR
./gradlew build
java -jar build/libs/fleet-management.jar
```

#### 6. Verify Application is Running
```bash
# Health check
curl http://localhost:8080/health

# API documentation
open http://localhost:8080/swagger
```

---

## 🔐 Required Environment Variables

### Minimal Configuration (Required)
```bash
# Database
DATABASE_URL=jdbc:postgresql://localhost:5432/fleet_db?user=fleet_user&password=YOUR_PASSWORD

# Redis
REDIS_URL=redis://localhost:6379

# Security
JWT_SECRET=YOUR_LONG_RANDOM_STRING_HERE  # Generate with: openssl rand -base64 64
```

### Full Configuration (Recommended)
See `.env.example` for all available environment variables including:
- Email/SMTP configuration
- Performance tuning (connection pools, rate limits)
- Observability settings (logging, metrics)
- Optional integrations (payments, SMS, cloud storage)

---

## 🔍 Security Checklist

Before committing code, verify:

- [ ] No `.env` file in git staging area
- [ ] No hardcoded passwords or API keys in source code
- [ ] No database dumps or backups in repository
- [ ] No private keys or certificates committed
- [ ] `.gitignore` is up to date
- [ ] `.env.example` has placeholder values only
- [ ] All secrets use environment variables (e.g., `${JWT_SECRET}`)

---

## 🚀 Deployment Considerations

### Production Secrets Management
- **DO NOT** use `.env` files in production
- **USE** platform-specific secret management:
  - **Render**: Environment Variables in dashboard
  - **Kubernetes**: Secrets and ConfigMaps
  - **AWS**: Secrets Manager or Parameter Store
  - **Azure**: Key Vault
  - **GCP**: Secret Manager

### Environment Variable Injection
Production platforms should inject secrets via:
1. Platform dashboard (Render, Heroku)
2. CI/CD pipeline (GitHub Actions, GitLab CI)
3. Secret management service (Vault, AWS Secrets Manager)

### Rotating Secrets
Regularly rotate sensitive credentials:
- JWT_SECRET: Every 90 days
- Database passwords: Every 90 days
- API keys: As recommended by provider
- SSL/TLS certificates: Before expiration

---

## 📚 Additional Resources

- [Environment Variables Documentation](./docs/deployment/environment-variables.md)
- [Phase 8: Deployment Guide](./docs/implementations/phase-8-deployment.md)
- [Security Best Practices](./docs/security/best-practices.md)

---

## ⚠️ What to Do If Secrets Are Committed

If you accidentally commit secrets:

1. **Immediately rotate the compromised secrets**
2. **Remove from git history:**
   ```bash
   # Use BFG Repo-Cleaner or git-filter-repo
   git filter-repo --path .env --invert-paths
   ```
3. **Force push (if safe):**
   ```bash
   git push --force
   ```
4. **Notify your team**
5. **Update all environments with new secrets**

---

**Last Updated:** 2026-02-17  
**Maintained By:** Fleet Management Team
