# GitHub Actions CI/CD Guide

## Overview
This project uses **GitHub Actions** for Continuous Integration (CI) and **Render** for Continuous Deployment (CD).

---

## 🔄 Complete CI/CD Flow

### 1. Developer Pushes Code
```bash
git push origin main
```

### 2. GitHub Actions Runs (CI)
Automatically runs on every push/PR:

#### ✅ **Job 1: Build and Test**
- Sets up PostgreSQL + Redis test databases
- Runs all unit and integration tests
- Builds Fat JAR
- Uploads test results and artifacts

#### ✅ **Job 2: Code Quality**
- Runs Kotlin linter (detekt)
- Checks code formatting (ktlint)

#### ✅ **Job 3: Security Scan**
- Scans for vulnerabilities (Trivy)
- Uploads results to GitHub Security tab

#### ✅ **Job 4: Build Docker Image**
- Builds Docker image (only on `main` branch)
- Tests image can run
- Caches layers for faster builds

#### ✅ **Job 5: Deployment Notification**
- Confirms all checks passed
- Notifies that Render will deploy

### 3. Render Deploys (CD)
If GitHub Actions passes:
- Render detects push to `main`
- Builds Docker image
- Runs database migrations
- Deploys new version

---

## 📊 CI/CD Pipeline Diagram

```
┌─────────────────┐
│  Developer      │
│  git push       │
└────────┬────────┘
         │
         ▼
┌─────────────────────────────────────────┐
│  GitHub Actions (CI)                    │
│  ┌─────────────────────────────────┐   │
│  │ 1. Build & Test                 │   │
│  │    - PostgreSQL + Redis         │   │
│  │    - Run tests                  │   │
│  │    - Build Fat JAR              │   │
│  └─────────────────────────────────┘   │
│  ┌─────────────────────────────────┐   │
│  │ 2. Code Quality                 │   │
│  │    - Linting                    │   │
│  │    - Formatting                 │   │
│  └─────────────────────────────────┘   │
│  ┌─────────────────────────────────┐   │
│  │ 3. Security Scan                │   │
│  │    - Vulnerability check        │   │
│  └─────────────────────────────────┘   │
│  ┌─────────────────────────────────┐   │
│  │ 4. Build Docker Image           │   │
│  │    - Test image                 │   │
│  └─────────────────────────────────┘   │
└────────────┬────────────────────────────┘
             │
             ▼ (if all pass)
┌─────────────────────────────────────────┐
│  Render (CD)                            │
│  ┌─────────────────────────────────┐   │
│  │ 1. Pull code from GitHub        │   │
│  │ 2. Build Docker image           │   │
│  │ 3. Run database migrations      │   │
│  │ 4. Deploy new version           │   │
│  │ 5. Health check                 │   │
│  │ 6. Switch traffic               │   │
│  └─────────────────────────────────┘   │
└─────────────────────────────────────────┘
```

---

## 🎯 What Happens on Each Event

### On Pull Request
```yaml
on:
  pull_request:
    branches: [ main ]
```

**GitHub Actions runs**:
- ✅ Build and test
- ✅ Code quality checks
- ✅ Security scan
- ❌ Does NOT build Docker image
- ❌ Does NOT deploy to Render

**Purpose**: Validate code before merging

### On Push to Main
```yaml
on:
  push:
    branches: [ main ]
```

**GitHub Actions runs**:
- ✅ Build and test
- ✅ Code quality checks
- ✅ Security scan
- ✅ Build Docker image
- ✅ Deployment notification

**Render then**:
- ✅ Auto-deploys to production

---

## 🔧 Configuration Files

### 1. GitHub Actions Workflow
**File**: `.github/workflows/ci-cd.yml`

**Triggers**:
- Push to `main` or `develop`
- Pull requests to `main`

**Services**:
- PostgreSQL 15 (for tests)
- Redis 7 (for tests)

### 2. Render Configuration
**File**: `render.yaml`

**Settings**:
```yaml
autoDeploy: true  # Auto-deploy on push to main
healthCheckPath: /health
```

---

## 📈 Viewing CI/CD Status

### GitHub Actions
1. Go to your repository on GitHub
2. Click **"Actions"** tab
3. See all workflow runs
4. Click on a run to see details

### Render Dashboard
1. Go to [render.com](https://render.com)
2. Select your service
3. Click **"Events"** tab
4. See deployment history

---

## ⚙️ Environment Variables

### GitHub Actions (for tests)
Set in workflow file:
```yaml
env:
  DATABASE_URL: jdbc:postgresql://localhost:5432/fleet_db_test
  REDIS_URL: redis://localhost:6379
  JWT_SECRET: test-secret-for-ci
```

### Render (for production)
Set in `render.yaml`:
```yaml
envVars:
  - key: DATABASE_URL
    fromDatabase: ...
  - key: JWT_SECRET
    generateValue: true
```

---

## 🚨 Handling Failures

### If GitHub Actions Fails
- ❌ Render does NOT deploy
- 📧 Email notification sent
- 🔍 Check "Actions" tab for error details
- 🔧 Fix code and push again

### If Render Deploy Fails
- ❌ Old version keeps running
- 📧 Email notification sent
- 🔍 Check Render dashboard logs
- 🔧 Fix code and push again

---

## 🎛️ Customizing CI/CD

### Disable Auto-Deploy
In `render.yaml`:
```yaml
autoDeploy: false  # Manual deploy only
```

### Run CI on Different Branches
In `.github/workflows/ci-cd.yml`:
```yaml
on:
  push:
    branches: [ main, develop, staging ]
```

### Add More Tests
In `.github/workflows/ci-cd.yml`:
```yaml
- name: Run integration tests
  run: ./gradlew integrationTest
```

---

## 💡 Best Practices

### 1. **Branch Protection**
Enable on GitHub:
- Require PR reviews
- Require status checks to pass
- Require branches to be up to date

### 2. **Separate Environments**
- `develop` branch → Staging environment
- `main` branch → Production environment

### 3. **Manual Approval for Production**
Set `autoDeploy: false` and deploy manually after review

### 4. **Monitor Deployments**
- Check GitHub Actions for CI status
- Check Render dashboard for deployment status
- Set up alerts for failures

---

## 📊 Summary

| Stage | Tool | What Happens | When |
|-------|------|--------------|------|
| **CI** | GitHub Actions | Tests, linting, security scan | Every push/PR |
| **Build** | GitHub Actions | Build Docker image | Push to `main` |
| **CD** | Render | Deploy to production | After CI passes |

**Your current setup**: 
- ✅ GitHub Actions validates code
- ✅ Render deploys if validation passes
- ✅ Zero downtime deployments
- ✅ Automatic database migrations

---

**Last Updated**: 2026-02-17  
**Status**: ✅ CI/CD Pipeline Active
