# 🚀 Deployment Guide - Fleet Management System

## Overview
This guide covers deploying the Fleet Management System to Render cloud platform using Docker containers.

---

## Prerequisites

### Local Development
- ✅ Java 17+ installed
- ✅ Docker installed
- ✅ Git installed
- ✅ Render account (free tier available)

### Repository Setup
- ✅ Code pushed to GitHub/GitLab
- ✅ All tests passing
- ✅ No hardcoded secrets

---

## 📦 Deployment Files

The following files are required for deployment:

### 1. **Dockerfile**
Multi-stage Docker build:
- **Stage 1**: Gradle build with JDK 21
- **Stage 2**: Runtime with JRE 21 Alpine (minimal footprint)
- **Features**: Health check, non-root user, dynamic PORT binding

### 2. **render.yaml**
Infrastructure-as-code for Render platform:
- Web service (Docker-based)
- PostgreSQL database (managed)
- Redis cache (managed)
- Environment variables auto-configured

### 3. **.dockerignore**
Excludes unnecessary files from Docker build context for faster builds.

### 4. **build.gradle.kts**
Includes Fat JAR task configuration:
```kotlin
ktor {
    fatJar {
        archiveFileName.set("fleet-management-all.jar")
    }
}
```

### 5. **application.yaml**
Uses environment variables instead of hardcoded values:
- `${PORT:8080}` - Server port
- `${DATABASE_URL}` - PostgreSQL connection
- `${REDIS_URL}` - Redis connection
- `${JWT_SECRET}` - JWT signing key

---

## 🧪 Local Testing

### Step 1: Build Fat JAR
```bash
./gradlew buildFatJar
```

**Expected Output**: `build/libs/fleet-management-all.jar`

### Step 2: Build Docker Image
```bash
docker build -t fleet-management:local .
```

**Expected**: Build completes without errors

### Step 3: Run Container Locally
```bash
docker run -p 8080:8080 \
  -e PORT=8080 \
  -e DATABASE_URL=jdbc:postgresql://host.docker.internal:5435/fleet_db \
  -e REDIS_URL=redis://host.docker.internal:6379 \
  -e JWT_SECRET=local-test-secret-min-64-chars-required-for-security \
  -e JWT_ISSUER=fleet-management-local \
  -e JWT_AUDIENCE=fleet-users-local \
  fleet-management:local
```

### Step 4: Verify Health Endpoint
```bash
curl http://localhost:8080/health
```

**Expected Response**:
```json
{
  "status": "UP"
}
```

### Step 5: Verify API Documentation
```bash
open http://localhost:8080/swagger
```

**Expected**: Swagger UI loads with all API endpoints

---

## 🌐 Deploying to Render

### Step 1: Push Code to GitHub
```bash
git add .
git commit -m "Add deployment configuration"
git push origin main
```

### Step 2: Create Render Account
1. Go to [render.com](https://render.com)
2. Sign up (free tier available)
3. Connect your GitHub account

### Step 3: Create New Web Service
1. Click **"New +"** → **"Blueprint"**
2. Connect your repository
3. Render auto-detects `render.yaml`
4. Click **"Apply"**

### Step 4: Monitor Deployment
Render will automatically:
1. ✅ Create PostgreSQL database
2. ✅ Create Redis instance
3. ✅ Build Docker image
4. ✅ Deploy web service
5. ✅ Run database migrations (Flyway)
6. ✅ Start application

**Build Time**: ~5-10 minutes (first deployment)

### Step 5: Verify Deployment
1. Check **"Logs"** tab for build progress
2. Wait for **"Live"** status
3. Click on the service URL
4. Verify health endpoint: `https://your-app.onrender.com/health`

---

## 🔐 Environment Variables

Render automatically configures these from `render.yaml`:

| Variable | Source | Description |
|----------|--------|-------------|
| `PORT` | Render | Injected by platform (10000) |
| `DATABASE_URL` | Managed DB | PostgreSQL connection string |
| `REDIS_URL` | Managed Redis | Redis connection string |
| `JWT_SECRET` | Auto-generated | Secure random value |
| `JWT_ISSUER` | render.yaml | `fleet-management-api` |
| `JWT_AUDIENCE` | render.yaml | `fleet-management-users` |
| `APP_ENV` | render.yaml | `production` |
| `DB_POOL_SIZE` | render.yaml | `10` |
| `LOG_LEVEL` | render.yaml | `INFO` |

### Adding Custom Environment Variables
1. Go to Render dashboard
2. Select your service
3. Click **"Environment"** tab
4. Add variables manually

---

## 📊 Post-Deployment Checklist

### Verify Application
- [ ] Health endpoint responds: `/health`
- [ ] Swagger UI accessible: `/swagger`
- [ ] Database migrations applied (check logs)
- [ ] Redis connection established (check logs)

### Test API Endpoints
- [ ] User registration: `POST /users/register`
- [ ] User login: `POST /users/login`
- [ ] Get vehicles: `GET /vehicles`
- [ ] Create rental: `POST /rentals`

### Monitor Performance
- [ ] Check application logs
- [ ] Monitor response times
- [ ] Verify database queries
- [ ] Check Redis cache hit rate

### Security
- [ ] Verify JWT authentication works
- [ ] Test RBAC (role-based access)
- [ ] Verify rate limiting active
- [ ] Check HTTPS enabled (Render provides free SSL)

---

## 🔧 Troubleshooting

### Build Fails
**Problem**: Docker build fails

**Solutions**:
1. Check Dockerfile syntax
2. Verify `buildFatJar` task exists in `build.gradle.kts`
3. Check build logs in Render dashboard

### Application Won't Start
**Problem**: Container starts but application crashes

**Solutions**:
1. Check environment variables are set
2. Verify `DATABASE_URL` format is correct
3. Check application logs for errors
4. Verify health endpoint path is `/health`

### Database Connection Fails
**Problem**: Can't connect to PostgreSQL

**Solutions**:
1. Verify `DATABASE_URL` is injected from managed database
2. Check database is in "Available" status
3. Verify Flyway migrations don't have errors
4. Check database logs

### Redis Connection Fails
**Problem**: Can't connect to Redis

**Solutions**:
1. Verify `REDIS_URL` is injected from managed Redis
2. Check Redis instance is running
3. Verify Redis plan has sufficient memory
4. Check application logs for connection errors

---

## 🔄 Updating Deployment

### Code Changes
```bash
git add .
git commit -m "Your changes"
git push origin main
```

Render will automatically:
1. Detect the push
2. Rebuild Docker image
3. Deploy new version
4. Run health checks
5. Switch traffic to new version

### Database Migrations
Flyway runs automatically on startup:
1. Add new migration: `src/main/resources/db/migration/V015__description.sql`
2. Push to GitHub
3. Render deploys and runs migration

---

## 📈 Scaling

### Vertical Scaling (More Resources)
1. Go to Render dashboard
2. Select your service
3. Change plan (Starter → Standard → Pro)

### Horizontal Scaling (More Instances)
1. Requires Standard plan or higher
2. Configure in Render dashboard
3. Render handles load balancing automatically

---

## 💰 Cost Estimate

### Free Tier
- ✅ Web service: Free (spins down after inactivity)
- ✅ PostgreSQL: Free (256MB RAM, 1GB storage)
- ✅ Redis: Free (25MB RAM)

**Total**: $0/month (with limitations)

### Starter Tier
- 💵 Web service: $7/month (always on)
- 💵 PostgreSQL: $7/month (256MB RAM, 1GB storage)
- 💵 Redis: $10/month (25MB RAM)

**Total**: $24/month

### Production Tier
- 💵 Web service: $25/month (1GB RAM, 0.5 CPU)
- 💵 PostgreSQL: $20/month (1GB RAM, 10GB storage)
- 💵 Redis: $25/month (100MB RAM)

**Total**: $70/month

---

## 📚 Additional Resources

- [Render Documentation](https://render.com/docs)
- [Ktor Deployment Guide](https://ktor.io/docs/deploy.html)
- [Docker Best Practices](https://docs.docker.com/develop/dev-best-practices/)
- [PostgreSQL on Render](https://render.com/docs/databases)
- [Redis on Render](https://render.com/docs/redis)

---

## 🆘 Support

### Issues
- Check application logs in Render dashboard
- Review this troubleshooting guide
- Check Phase 8 documentation: `docs/implementations/phase-8-deployment.md`

### Contact
- Render Support: [render.com/support](https://render.com/support)
- Project Documentation: `docs/`

---

**Last Updated**: 2026-02-17  
**Version**: 1.0.0  
**Status**: ✅ Production Ready
