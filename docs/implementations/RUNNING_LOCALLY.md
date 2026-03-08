# Running the Fleet Management System Locally

This guide provides step-by-step instructions to run the Fleet Management System backend on your local machine.

## Prerequisites

- **Java JDK 21+**
- **Docker & Docker Compose** (for database and dependencies)
- **Git**

## 1. Start Support Services

The application requires PostgreSQL and Redis. We use Docker Compose to spin these up.

1. Open a terminal in the project root.
2. Run the following command:

   ```bash
   docker-compose up -d
   ```

3. Verify services are running:

   ```bash
   docker ps
   ```
   You should see `fleet_postgres` and `fleet_redis` containers.

## 2. Configuration

The application is configured via `src/main/resources/application.yaml`.
Default settings work out-of-the-box with the provided `docker-compose.yml`:
- **DB URL**: `jdbc:postgresql://127.0.0.1:5435/fleet_db`
- **DB User**: `fleet_user`
- **DB Password**: `secret_123`
- **Redis**: `redis://localhost:6379` (enabled by default)

> ⚠️ **Note**: The local PostgreSQL container is mapped to host port **5435** (not 5432) to avoid conflicts with any existing local PostgreSQL installation.

## 3. Run the Application

You can run the application directly using Gradle.

### Using Command Line

```bash
# Windows
./gradlew.bat run

# Linux/macOS
./gradlew run
```

### Application Startup
- Database migrations (Flyway) will run automatically on startup.
- The server will start on port **8080** (default).

## 4. Verify It Works

Once the application is running, you can test the health endpoint:

```bash
curl http://localhost:8080/health
```
**Expected Output**: `{"success":true,"data":{"status":"OK"},...}`

**Swagger UI (Interactive API Docs)**:
```
http://localhost:8080/swagger
```

**Prometheus Metrics**:
```
http://localhost:8080/metrics
```

Test a domain endpoint (requires auth, but you can check if it exists):
```bash
curl -v http://localhost:8080/v1/vehicles
```
**Expected Output**: `401 Unauthorized` (This confirms the server is up and routing works).

## 5. Run Tests

### Unit Tests (no Docker required)
```bash
# Windows
./gradlew.bat test

# Linux/macOS
./gradlew test
```
Unit tests use MockK and H2 in-memory — no database container needed.

After running, view the HTML report:
```
build/reports/tests/test/index.html
```

### Connect to Local Database (Optional)
Using Docker exec:
```bash
docker exec -it fleet_postgres psql -U fleet_user -d fleet_db
```
Or use any DB client (DBeaver, DataGrip):
- **Host**: `127.0.0.1` | **Port**: `5435` | **DB**: `fleet_db` | **User**: `fleet_user` | **Password**: `secret_123`

## 6. Troubleshooting

### Unit Tests (no Docker required)
```bash
# Windows
./gradlew.bat test

# Linux/macOS
./gradlew test
```
Unit tests use MockK and H2 in-memory — no database container needed.

After running, view the HTML report:
```
build/reports/tests/test/index.html
```

### Connect to Local Database (Optional)
Using Docker exec:
```bash
docker exec -it fleet_postgres psql -U fleet_user -d fleet_db
```
Or use any DB client (DBeaver, DataGrip):
- **Host**: `127.0.0.1` | **Port**: `5435` | **DB**: `fleet_db` | **User**: `fleet_user` | **Password**: `secret_123`

- **Port Conflicts**: Ensure ports `5435` (Postgres), `6379` (Redis), and `8080` are free.
- **Container Name Conflicts**: If you see an error like `The container name "/fleet_redis" is already in use`, it means old containers are still hanging around.
  - **Solution 1 (Recommended)**: Stop and remove all containers defined in compose:
    ```bash
    docker-compose down
    ```
  - **Solution 2 (Manual)**: Force remove the conflicting containers:
    ```bash
    docker rm -f fleet_postgres fleet_redis
    ```
- **Password Authentication Failed**: If you get `FATAL: password authentication failed`, your database volume might be stale with an old password.
  - **Solution**: Wipe the volumes and restart:
    ```bash
    docker-compose down -v
    docker-compose up -d
    ```
- **Database Connection**: If the app fails to start with a DB error, ensure Docker is running and `docker-compose up` was successful.
- **Clean Build**: If you encounter weird build errors, try:
  ```bash
  ./gradlew clean build
  ```
