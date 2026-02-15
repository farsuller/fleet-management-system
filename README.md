# Fleet Management System

A production-ready **Fleet Management System** built with Kotlin and Ktor, designed to manage vehicle rentals, customer profiles, and fleet operations.

[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.22-blue.svg)](https://kotlinlang.org/)
[![Ktor](https://img.shields.io/badge/Ktor-2.3.7-orange.svg)](https://ktor.io/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-blue.svg)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

---

## 📋 Table of Contents

- [Overview](#overview)
- [Features](#features)
- [System Architecture](#system-architecture)
- [Database Schema](#database-schema)
- [Technology Stack](#technology-stack)
- [Getting Started](#getting-started)
- [API Documentation](#api-documentation)
- [Project Structure](#project-structure)
- [Development](#development)
- [Testing](#testing)
- [Deployment](#deployment)

---

## 🎯 Overview

The Fleet Management System is a comprehensive solution for managing vehicle rental operations. It provides RESTful APIs for:

- **Customer Management** - Driver profiles and license validation
- **Vehicle Fleet Management** - Vehicle inventory and availability tracking
- **Rental Lifecycle** - Reservation, activation, completion, and cancellation
- **User Management** - Staff and customer authentication

### Key Capabilities

- ✅ **Real-time Availability** - Prevent double-booking with conflict detection
- ✅ **Driver Validation** - Automatic license expiry verification
- ✅ **Odometer Tracking** - Mileage recording for vehicle maintenance
- ✅ **State Management** - Clear rental lifecycle (RESERVED → ACTIVE → COMPLETED)
- ✅ **Multi-tenancy Ready** - Designed for scalability

---

## ✨ Features

### Customer Management
- Create and manage customer profiles
- Driver's license validation and expiry tracking
- Email and license uniqueness enforcement
- Optional user account linking for self-service portal

### Vehicle Management
- Complete vehicle inventory management
- Real-time availability status
- Odometer reading history
- Vehicle state tracking (AVAILABLE, RENTED, MAINTENANCE, RETIRED)

### Rental Operations
- Create reservations with conflict detection
- Activate rentals with odometer capture
- Complete rentals with final mileage
- Cancel reservations or active rentals
- Automatic cost calculation based on daily rates

### User & Authentication
- Role-based access control (RBAC)
- **Email Verification** - Account activation flow:
  - Register -> User created (`isVerified=false`), Token generated.
  - Login -> Fails ("Email not verified").
  - Verify Link -> User updated (`isVerified=true`).
  - Login -> Success (Token returned).
- Staff profiles with department tracking
- Multiple user roles (ADMIN, FLEET_MANAGER, RENTAL_AGENT, etc.)

---

## 🏗️ System Architecture

### Clean Architecture Layers

```
┌─────────────────────────────────────────────────────────────┐
│                    HTTP Layer (Ktor)                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │   Customer   │  │    Rental    │  │   Vehicle    │       │
│  │    Routes    │  │    Routes    │  │    Routes    │       │
│  └──────────────┘  └──────────────┘  └──────────────┘       │
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                    Application Layer                        │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │   Use Cases  │  │   Use Cases  │  │   Use Cases  │       │
│  │  (Customer)  │  │   (Rental)   │  │  (Vehicle)   │       │
│  └──────────────┘  └──────────────┘  └──────────────┘       │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              DTOs (Request/Response)                 │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                       Domain Layer                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐       │
│  │   Customer   │  │    Rental    │  │   Vehicle    │       │
│  │    Entity    │  │    Entity    │  │    Entity    │       │
│  └──────────────┘  └──────────────┘  └──────────────┘       │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │           Repository Interfaces (Ports)              │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────┐
│                   Infrastructure Layer                      │
│  ┌──────────────────────────────────────────────────────┐   │
│  │     Repository Implementations (Exposed ORM)         │   │
│  └──────────────────────────────────────────────────────┘   │
│                                                             │
│  ┌──────────────────────────────────────────────────────┐   │
│  │              Database (PostgreSQL)                   │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### Design Patterns

- **Clean Architecture** - Separation of concerns with clear boundaries
- **Repository Pattern** - Abstract data access layer
- **Use Case Pattern** - Encapsulate business logic
- **DTO Pattern** - Separate API contracts from domain models
- **Dependency Injection** - Manual DI for simplicity

---

## 🗄️ Database Schema

### Entity Relationship Diagram

**Current Implementation:** 20+ tables across 7 modules

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                          CORE ENTITIES                                        │
└──────────────────────────────────────────────────────────────────────────────┘

┌─────────────────┐                    ┌─────────────────┐
│     USERS       │                    │  VERIFICATION   │
│─────────────────│                    │     TOKENS      │
│ id (PK)         │                    │─────────────────│
│ email (UNIQUE)  │◄───────────────────│ user_id (FK)    │
│ password_hash   │                    │ token (UNIQUE)  │
│ first_name      │                    │ expires_at      │
│ last_name       │                    │ created_at      │
│ role            │                    └─────────────────┘
│ is_verified     │
│ is_active       │
└─────────────────┘
         │
         │ 1:1 (optional)
         ▼
┌─────────────────┐
│   CUSTOMERS     │
│─────────────────│
│ id (PK)         │◄─────────────────────┐
│ user_id (FK)    │                      │
│ email (UNIQUE)  │                      │
│ first_name      │                      │
│ last_name       │                      │
│ phone           │                      │
│ driver_license  │                      │
│ license_expiry  │                      │
│ is_active       │                      │
└─────────────────┘                      │
         │                               │
         │ 1:N                           │
         ▼                               │
┌─────────────────┐                      │
│    RENTALS      │                      │
│─────────────────│                      │
│ id (PK)         │                      │
│ rental_number   │                      │
│ customer_id (FK)├──────────────────────┘
│ vehicle_id (FK) ├──────────────────────┐
│ status          │                      │
│ start_date      │                      │
│ end_date        │                      │
│ daily_rate_cents│                      │
│ total_price_cents                      │
│ actual_start    │                      │
│ actual_end      │                      │
│ start_odo_km    │                      │
│ end_odo_km      │                      │
└─────────────────┘                      │
                                         │ N:1
                                         ▼
                                ┌─────────────────┐
                                │    VEHICLES     │
                                │─────────────────│
                                │ id (PK)         │
                                │ plate_number    │
                                │ make            │
                                │ model           │
                                │ year            │
                                │ state           │
                                │ daily_rate_cents│
                                │ mileage_km      │
                                │ version         │
                                └─────────────────┘
                                         │
                                         │ 1:N
                                         ▼
                                ┌─────────────────┐
                                │ ODOMETER_       │
                                │   READINGS      │
                                │─────────────────│
                                │ id (PK)         │
                                │ vehicle_id (FK) │
                                │ reading_km      │
                                │ recorded_at     │
                                │ recorded_by     │
                                └─────────────────┘

┌──────────────────────────────────────────────────────────────────────────────┐
│                        MAINTENANCE MODULE                                     │
└──────────────────────────────────────────────────────────────────────────────┘

┌─────────────────┐
│ MAINTENANCE_    │
│     JOBS        │
│─────────────────│
│ id (PK)         │
│ job_number      │
│ vehicle_id (FK) ├──────► VEHICLES
│ status          │
│ job_type        │
│ priority        │
│ scheduled_date  │
│ started_at      │
│ completed_at    │
│ odometer_km     │
│ labor_cost_cents│
│ parts_cost_cents│
│ assigned_to (FK)├──────► USERS
│ completed_by(FK)├──────► USERS
│ version         │
└─────────────────┘
         │
         │ 1:N
         ▼
┌─────────────────┐
│ MAINTENANCE_    │
│     PARTS       │
│─────────────────│
│ id (PK)         │
│ job_id (FK)     │
│ part_number     │
│ part_name       │
│ quantity        │
│ unit_cost_cents │
│ supplier        │
└─────────────────┘

┌─────────────────┐
│ MAINTENANCE_    │
│   SCHEDULES     │
│─────────────────│
│ id (PK)         │
│ vehicle_id (FK) ├──────► VEHICLES
│ schedule_type   │
│ interval_type   │
│ mileage_interval│
│ time_interval   │
│ last_service_date
│ next_service_date
│ is_active       │
└─────────────────┘

┌──────────────────────────────────────────────────────────────────────────────┐
│                      ACCOUNTING MODULE (Double-Entry)                         │
└──────────────────────────────────────────────────────────────────────────────┘

┌─────────────────┐
│    ACCOUNTS     │
│ (Chart of Accts)│
│─────────────────│
│ id (PK)         │◄────────────────────┐
│ account_code    │                     │
│ account_name    │                     │
│ account_type    │                     │
│ parent_acct(FK) ├─────────┐           │
│ is_active       │         │           │
└─────────────────┘         │           │
         ▲                  └───────────┘
         │
         │ N:1
         │
┌─────────────────┐         ┌─────────────────┐
│ LEDGER_ENTRY_   │         │ LEDGER_ENTRIES  │
│     LINES       │         │─────────────────│
│─────────────────│         │ id (PK)         │
│ id (PK)         │         │ entry_number    │
│ entry_id (FK)   ├────────►│ external_ref    │
│ account_id (FK) ├─────────┤ entry_date      │
│ debit_amt_cents │         │ description     │
│ credit_amt_cents│         │ created_by (FK) ├──► USERS
│ description     │         └─────────────────┘
└─────────────────┘

┌─────────────────┐
│    INVOICES     │
│─────────────────│
│ id (PK)         │◄────────────────────┐
│ invoice_number  │                     │
│ customer_id (FK)├──────► CUSTOMERS    │
│ rental_id (FK)  ├──────► RENTALS      │
│ status          │                     │
│ subtotal_cents  │                     │
│ tax_cents       │                     │
│ total_cents     │                     │
│ paid_cents      │                     │
│ balance_cents   │                     │
│ issue_date      │                     │
│ due_date        │                     │
│ paid_date       │                     │
└─────────────────┘                     │
         │                              │
         │ 1:N                          │
         ▼                              │
┌─────────────────┐                     │
│ INVOICE_LINE_   │                     │
│     ITEMS       │                     │
│─────────────────│                     │
│ id (PK)         │                     │
│ invoice_id (FK) │                     │
│ description     │                     │
│ quantity        │                     │
│ unit_price_cents│                     │
│ total_cents     │                     │
└─────────────────┘                     │
                                        │
┌─────────────────┐                     │
│    PAYMENTS     │                     │
│─────────────────│                     │
│ id (PK)         │                     │
│ payment_number  │                     │
│ customer_id (FK)├──────► CUSTOMERS    │
│ invoice_id (FK) ├─────────────────────┘
│ payment_method  │
│ amount_cents    │
│ status          │
│ payment_date    │
│ transaction_ref │
└─────────────────┘

┌─────────────────┐
│ PAYMENT_METHODS │
│─────────────────│
│ id (PK)         │
│ code (UNIQUE)   │
│ display_name    │
│ target_acct_code├──────► ACCOUNTS (account_code)
│ is_active       │
│ description     │
└─────────────────┘

┌──────────────────────────────────────────────────────────────────────────────┐
│                        INTEGRATION & SHARED                                   │
└──────────────────────────────────────────────────────────────────────────────┘

┌─────────────────┐
│ IDEMPOTENCY_    │
│      KEYS       │
│─────────────────│
│ id (PK)         │
│ idempotency_key │
│ response_status │
│ response_body   │
│ created_at      │
│ expires_at      │
└─────────────────┘
```

### Database Statistics

| Module | Tables | Key Features |
|--------|--------|--------------|
| **Users & Auth** | 2 | Users, Email verification tokens |
| **Customers** | 1 | Customer profiles with driver licenses |
| **Vehicles** | 2 | Fleet inventory, Odometer tracking |
| **Rentals** | 1 | Rental lifecycle management |
| **Maintenance** | 3 | Jobs, Parts, Schedules |
| **Accounting** | 7 | Double-entry ledger, Invoices, Payments |
| **Integration** | 1 | Idempotency keys for API safety |
| **Total** | **20** | Production-ready schema |

### Key Constraints

- **Double-Entry Validation**: Ledger entries must balance (debits = credits)
- **Rental Conflicts**: Prevents double-booking via date range checks
- **License Validation**: Driver license expiry must be future-dated
- **Odometer Integrity**: Readings must be non-decreasing
- **Financial Integrity**: All money stored as cents (integer) to avoid floating-point errors
- **Audit Trail**: All tables have `created_at`, `updated_at` timestamps
- **Optimistic Locking**: Version columns on critical tables (rentals, maintenance, vehicles)

---

## 🛠️ Technology Stack

### Backend Framework
- **[Kotlin](https://kotlinlang.org/)** 1.9.22 - Modern JVM language
- **[Ktor](https://ktor.io/)** 2.3.7 - Lightweight async web framework
- **[Exposed](https://github.com/JetBrains/Exposed)** - Kotlin SQL framework

### Database
- **[PostgreSQL](https://www.postgresql.org/)** 15+ - Production database
- **[H2](https://www.h2database.com/)** - In-memory database for testing
- **[Flyway](https://flywaydb.org/)** - Database migration tool
- **[HikariCP](https://github.com/brettwooldridge/HikariCP)** - Connection pooling

### Serialization & Validation
- **[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)** - JSON serialization
- **Kotlin `require()`** - Input validation

### Testing
- **[JUnit 5](https://junit.org/junit5/)** - Testing framework
- **[Kotlin Test](https://kotlinlang.org/api/latest/kotlin.test/)** - Kotlin testing utilities

### Code Quality
- **[Spotless](https://github.com/diffplug/spotless)** - Code formatting
- **[ktfmt](https://github.com/facebook/ktfmt)** - Kotlin formatter

### Build & Deployment
- **[Gradle](https://gradle.org/)** 8.5 - Build automation
- **[Docker](https://www.docker.com/)** - Containerization (optional)
- **[Docker Compose](https://docs.docker.com/compose/)** - Local development

---

## 🚀 Getting Started

### Prerequisites

- **JDK 17+** - [Download](https://adoptium.net/)
- **PostgreSQL 15+** - [Download](https://www.postgresql.org/download/)
- **Git** - [Download](https://git-scm.com/)

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/fleet-management.git
   cd fleet-management
   ```

2. **Set up the database**
   ```bash
   # Start PostgreSQL with Docker Compose
   docker-compose up -d
   
   # Or create database manually
   createdb fleet_management
   ```

3. **Configure environment**
   ```bash
   cp .env.example .env
   # Edit .env with your database credentials
   ```

4. **Run database migrations**
   ```bash
   ./gradlew flywayMigrate
   ```

5. **Build the project**
   ```bash
   ./gradlew build
   ```

6. **Run the application**
   ```bash
   ./gradlew run
   ```

The server will start at `http://localhost:8080`

### Quick Test

```bash
# Health check
curl http://localhost:8080/health

# Create a customer
curl -X POST http://localhost:8080/v1/customers \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@example.com",
    "firstName": "John",
    "lastName": "Doe",
    "phone": "+63-917-123-4567",
    "driversLicense": "N01-12-345678",
    "driverLicenseExpiry": "2028-12-31"
  }'
```

---

## 📚 API Documentation

### Base URL
```
http://localhost:8080
```

### Endpoints Overview

| Module | Endpoint | Method | Description |
|--------|----------|--------|-------------|
| **Health** | `/health` | GET | System health check |
| **Customers** | `/v1/customers` | GET | List all customers |
| | `/v1/customers` | POST | Create customer |
| | `/v1/customers/{id}` | GET | Get customer by ID |
| **Rentals** | `/v1/rentals` | GET | List all rentals |
| | `/v1/rentals` | POST | Create rental |
| | `/v1/rentals/{id}` | GET | Get rental by ID |
| | `/v1/rentals/{id}/activate` | POST | Activate rental |
| | `/v1/rentals/{id}/complete` | POST | Complete rental |
| | `/v1/rentals/{id}/cancel` | POST | Cancel rental |
| **Vehicles** | `/v1/vehicles` | GET | List all vehicles |
| | `/v1/vehicles` | POST | Create vehicle |
| | `/v1/vehicles/{id}` | GET | Get vehicle by ID |
| | `/v1/vehicles/{id}` | PUT | Update vehicle |
| | `/v1/vehicles/{id}` | DELETE | Delete vehicle |
| **Users** | `/v1/users` | GET | List all users |
| | `/v1/users` | POST | Create user |
| | `/v1/auth/verify` | GET | Verify Email |
| | `/v1/users/{id}` | GET | Get user by ID |

### Detailed Documentation

For complete API documentation with request/response examples, see:
- **[API Test Scenarios](docs/implementations/API-TEST-SCENARIOS.md)** - Complete test scenarios
- **[Customer Module](docs/implementations/module-customer-route-implementation.md)** - Customer API reference
- **[Rental Module](docs/implementations/module-rental-route-implementation.md)** - Rental API reference
- **[Vehicle Module](docs/implementations/module-vehicle-route-implementation.md)** - Vehicle API reference
- **[User Module](docs/implementations/module-user-route-implementation.md)** - User API reference

---

## 📁 Project Structure

```
fleet-management/
├── src/
│   ├── main/
│   │   ├── kotlin/com/solodev/fleet/
│   │   │   ├── Application.kt              # Main entry point
│   │   │   ├── Routing.kt                  # Route configuration
│   │   │   ├── modules/
│   │   │   │   ├── <module_name>/          # e.g., rentals, vehicles, users
│   │   │   │   │   ├── application/
│   │   │   │   │   │   ├── dto/            # Request/Response DTOs
│   │   │   │   │   │   └── usecases/       # Business logic (Use Cases)
│   │   │   │   │   ├── domain/
│   │   │   │   │   │   ├── model/          # Domain Entities & Value Objects
│   │   │   │   │   │   └── repository/     # Repository Interfaces (Ports)
│   │   │   │   │   └── infrastructure/
│   │   │   │   │       ├── http/           # HTTP Routes (Controllers)
│   │   │   │   │       └── persistence/    # Database Implementations
│   │   │   │   ├── infrastructure/         # Shared Infrastructure
│   │   │   │   │   └── persistence/        # Shared DB tables (e.g. Integration)
│   │   │   └── shared/
│   │   │       ├── models/                 # Shared models (ApiResponse)
│   │   │       └── plugins/                # Ktor plugins
│   │   └── resources/
│   │       ├── application.conf            # Ktor configuration
│   │       └── db/migration/               # Flyway migrations
│   │           ├── V001__create_users_schema.sql
│   │           ├── V002__create_vehicles_schema.sql
│   │           └── V003__create_rentals_schema.sql
│   └── test/
│       └── kotlin/com/solodev/fleet/
│           ├── ApplicationTest.kt
│           └── MigrationTest.kt
├── docs/
│   ├── implementations/                    # Implementation & guide docs
│   │   ├── README.md
│   │   ├── API-TEST-SCENARIOS.md
│   │   ├── module-customer-route-implementation.md
│   │   ├── module-rental-route-implementation.md
│   │   ├── module-vehicle-route-implementation.md
│   │   └── module-user-route-implementation.md
│   └── db/                                 # Database documentation
│       └── schema-design.md
├── build.gradle.kts                        # Gradle build configuration
├── docker-compose.yml                      # Docker services
├── .env.example                            # Environment variables template
└── README.md                               # This file
```

---

## 💻 Development

### Running Locally

```bash
# Run with auto-reload (development mode)
./gradlew run

# Run with specific environment
./gradlew run -Denv=development
```

### Database Migrations

```bash
# Run migrations
./gradlew flywayMigrate

# Rollback last migration
./gradlew flywayUndo

# Check migration status
./gradlew flywayInfo
```

### Linting

```bash
# Run all checks
./gradlew check

# Run tests only
./gradlew test
```

---

## 🧪 Testing

### Run All Tests

```bash
./gradlew test
```

### Run Specific Test

```bash
./gradlew test --tests "ApplicationTest"
```

### Test Coverage

```bash
./gradlew test jacocoTestReport
# Report available at: build/reports/jacoco/test/html/index.html
```

### Integration Testing

See [API-TEST-SCENARIOS.md](docs/implementations/API-TEST-SCENARIOS.md) for complete API test scenarios with cURL examples.

---

## 🚢 Deployment

### Docker Build

```bash
# Build Docker image
docker build -t fleet-management:latest .

# Run container
docker run -p 8080:8080 \
  -e DATABASE_URL=jdbc:postgresql://host:5432/fleet \
  -e DATABASE_USER=fleet_user \
  -e DATABASE_PASSWORD=secret \
  fleet-management:latest
```

### Production Checklist

- [ ] Set strong database credentials
- [ ] Configure HTTPS/TLS
- [ ] Enable CORS for allowed origins only
- [ ] Set up database backups
- [ ] Configure monitoring and logging
- [ ] Set up rate limiting
- [ ] Enable authentication/authorization
- [ ] Review and harden security settings

---

## 📖 Additional Documentation

- **[Implementation Standards](docs/implementations/IMPLEMENTATION-STANDARDS.md)** - Coding conventions and patterns
- **[Database Schema Design](docs/db/schema-design.md)** - Detailed schema documentation
- **[Running Locally Guide](docs/implementations/RUNNING_LOCALLY.md)** - Local development setup

---

## 🤝 Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## 👥 Authors

- **Your Name** - *Initial work*

---

## 🙏 Acknowledgments

- Built with [Ktor](https://ktor.io/)
- Database migrations with [Flyway](https://flywaydb.org/)
- ORM powered by [Exposed](https://github.com/JetBrains/Exposed)

---

**Happy Coding! 🚀**