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
- [Implementation Roadmap](#implementation-roadmap)
- [System Architecture](#system-architecture)
- [Database Schema](#database-schema)
- [Typical Workflow](#typical-workflow)
- [Technology Stack](#technology-stack)
- [Getting Started](#getting-started)
- [API Documentation](#api-documentation)
- [Project Structure](#project-structure)
- [Development](#development)
- [Testing](#testing)
- [Deployment](#deployment)
- [Troubleshooting](#troubleshooting)

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
- ✅ **Double-Entry Ledger** - Synchronous financial postings with zero-sum integrity
- ✅ **Reconciliation** - Automated matching between operational and financial records
- ✅ **PHP Currency** - All monetary values handled as whole PHP units (Integer)
- ✅ **State Management** - Clear rental lifecycle (RESERVED → ACTIVE → COMPLETED)

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

### Accounting & Reporting (Phase 5)
- **Double-Entry Ledger** - Guaranteed balance between debits and credits
- **Automated Invoicing** - Synchronous invoice generation upon rental events
- **Payment Reconciliation** - Detecting "Silent Failures" in ledger postings
- **Financial Reports** - Balance Sheet and Revenue Reports with "Normal Balance" logic (sign-flipping)
- **Flexible Payments** - Support for multiple payment methods (Cash, Card, GCash, etc.)

---

## 🗺️ Implementation Roadmap

The system is developed in distinct phases, moving from architecture to complex features.

### ✅ **Completed Phases** (Production-Ready)

| Phase | Status | Document | Features Delivered | Completion |
|-------|--------|----------|-------------------|------------|
| **P0** | ✅ **Complete** | [Plan](./docs/implementations/phase-0-plan-requirements-dependencies-boundaries.md) | Requirements & Architecture Design | 100% |
| **P1** | ✅ **Complete** | [Architecture](./docs/implementations/phase-1-architecture-skeleton.md) | API Framework, Error Handling | 100% |
| **P2** | ✅ **Complete** | [Schema](./docs/implementations/phase-2-postgresql-schema-v1.md) | Database Schema (20+ tables) | 100% |
| **P3** | ✅ **Complete** | [API v1](./docs/implementations/phase-3-api-surface-v1.md) | **User Management**, **Customer Management**, **Vehicle Fleet**, **Rental Operations**, **Maintenance Tracking**, **Payment & Invoicing**, **Accounting Ledger** | 100% |
| **P4** | ✅ **Complete** | [Hardening](./docs/implementations/phase-4-hardening-v2-implementation.md) | Role-Based Access, Rate Limiting, Performance Caching | 100% |
| **P5** | ✅ **Complete** | [Accounting](./docs/implementations/phase-5-reporting-and-accounting-correctness.md) | **Chart of Accounts**, **Revenue Reports**, **Balance Sheet**, Reconciliation | 100% |
| **P6** | ✅ **Core Complete** | [Spatial](./docs/implementations/phase-6-postgis-spatial-extensions.md) | **PostGIS Integration**, **GPS Snapping**, **Route Matching**, **Distance Calculation** | ~85% — Geofencing deferred to P7 |
| **P7** | ✅ **Core Implemented** | [Visuals](./docs/implementations/phase-7-schematic-visualization-engine.md) | **Delta-Encoded WebSockets**, **Live Fleet Dashboard**, **Real-Time Positions**, **Movement Visualization** | ~80% — State/fleet endpoints still mocked; WS JWT pending |
| **P8** | ✅ **Complete** | [Deployment](./docs/implementations/phase-8-deployment.md) | **Production Hosting (Render)**, **Supabase DB**, **Dockerfile**, **render.yaml**, **CI/CD** | 100% |

### 📊 **Implementation Statistics**

#### **Database**
- ✅ **Migrations**: 18 applied (V001–V019, V016 intentionally skipped)
- ✅ **Tables**: 20+ across 8 modules (includes `location_history`, `routes`)
- ✅ **Constraints**: Double-booking prevention, Double-entry validation
- ✅ **Caching**: Redis (VehicleRepository, 5-min TTL)

#### **API Endpoints**
- ✅ **Modules**: 8 complete (Users, Customers, Vehicles, Rentals, Maintenance, Accounting, Integration, **Tracking**)
- ✅ **Authentication**: JWT with role-based claims
- ✅ **Authorization**: RBAC via `withRoles` plugin
- ✅ **Documentation**: OpenAPI + Swagger UI at `/swagger`

#### **Security & Hardening**
- ✅ **Rate Limiting**: Multi-tiered (IP + User-based)
- ✅ **Idempotency**: Header-based with DB persistence
- ✅ **SQL Injection**: Exposed Type-Safe DSL protection
- ✅ **Concurrency**: Optimistic + Pessimistic locking
- ✅ **Observability**: Structured JSON logs, Micrometer metrics, Request ID tracing

#### **Financial Integrity**
- ✅ **Double-Entry Ledger**: Synchronous transactional postings
- ✅ **Reconciliation**: Automated invoice/ledger matching
- ✅ **Reports**: Revenue & Balance Sheet generation
- ✅ **Chart of Accounts**: Full CRUD operations

### 🚀 **Deployment Status**
- ✅ **Core System**: Fully functional (Phases 1-8)
- ✅ **Business Logic**: All CRUD operations operational
- ✅ **Deployment**: Live on Render — `https://fleet-management-api-8tli.onrender.com`
- ✅ **Real-Time Tracking**: Core implemented (Phases 6-7) — WebSocket broadcasting active

---

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
│ daily_rate      │ (PHP)                │
│ total_price     │ (PHP)                │
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
│ labor_cost      │ (PHP)
│ parts_cost      │ (PHP)
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
│ unit_cost       │ (PHP)
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
│ debit_amount    │ (PHP)   │ description     │
│ credit_amount   │ (PHP)   │ created_by (FK) ├──► USERS
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
│ subtotal        │ (PHP)               │
│ tax             │ (PHP)               │
│ total           │ (PHP)               │
│ paid_amount     │ (PHP)               │
│ balance         │ (PHP)               │
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
│ unit_price      │ (PHP)               │
│ total           │ (PHP)               │
└─────────────────┘                     │
                                        │
┌─────────────────┐                     │
│    PAYMENTS     │                     │
│─────────────────│                     │
│ id (PK)         │                     │
│ payment_number  │                     │
│ customer_id (FK)├──────► CUSTOMERS    │
│ invoice_id (FK) ├─────────────────────┘
│ payment_method  │ (FK) ──────► PAYMENT_METHODS
│ amount          │ (PHP)
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
- **Financial Integrity**: All money stored as whole units (PHP) to avoid floating-point errors
- **Audit Trail**: All tables have `created_at`, `updated_at` timestamps
- **Optimistic Locking**: Version columns on critical tables (rentals, maintenance, vehicles)

---

---

## 🔄 Typical Workflow: Creating & Paying a Rental

```bash
1. Create Customer
   POST /v1/customers -> Returns customerId

2. Verify Vehicle Available
   GET /v1/vehicles/{vehicleId} -> Check status is "AVAILABLE"

3. Create Rental (Reservation)
   POST /v1/rentals -> Returns rentalId, status: "RESERVED"

4. Activate Rental (Pickup)
   POST /v1/rentals/{rentalId}/activate
   → Status: "ACTIVE", Vehicle: "RENTED", Ledger: Receivable + Revenue

5. Complete Rental (Return)
   POST /v1/rentals/{rentalId}/complete
   → Status: "COMPLETED", Vehicle: "AVAILABLE"

6. Issue Invoice
   POST /v1/reconciliation/invoices -> Standardizes reference formats

7. Capture Payment
   POST /v1/reconciliation/invoices/{id}/pay
   → Ledger: Cash + Clearance, Reconciliation: Balanced (0.00 mismatch)
```

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

Regardless of your operating system, you will need:
- **JDK 17 or 21** - [Adoptium (Recommended)](https://adoptium.net/)
- **Docker Desktop** - [Download](https://www.docker.com/products/docker-desktop/) (Required for local DB & Redis)
- **Git** - [Download](https://git-scm.com/)
- **Postman** (Optional) - For API testing

---

### 💻 OS-Specific Setup

#### 🪟 Windows Setup
1. **Enable WSL2**: Ensure WSL2 is enabled and Docker Desktop is configured to use the WSL2 backend.
2. **Clone the repository**:
   ```powershell
   git clone https://github.com/farsuller/fleet-management-system.git
   cd fleet-management-system
   ```
3. **Environment Setup**:
   ```powershell
   copy .env.example .env
   ```
4. **Start Infrastructure**:
   ```powershell
   docker-compose up -d
   ```
5. **Run Application**:
   ```powershell
   ./gradlew run
   ```

#### 🍎 macOS Setup
1. **Install Homebrew** (if not already installed): `/bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"`
2. **Clone the repository**:
   ```bash
   git clone https://github.com/farsuller/fleet-management-system.git
   cd fleet-management-system
   ```
3. **Environment Setup**:
   ```bash
   cp .env.example .env
   ```
4. **Start Infrastructure**:
   ```bash
   docker-compose up -d
   ```
5. **Run Application**:
   ```bash
   ./gradlew run
   ```

---

### 🔑 Configuration (Environment Variables)

The system supports two database targets:

1. **Local Development (Default)**: Uses the `.env` file pointing to `localhost:5435` (Docker).
2. **Cloud Development (Supabase)**: Uses `.env.supabase` for connecting to the managed Supabase instance.

**To switch to local dev:**
Ensure your `.env` has:
```env
DATABASE_URL=jdbc:postgresql://127.0.0.1:5435/fleet_db
DB_USER=fleet_user
DB_PASSWORD=secret_123
```

---

### 🛠️ Quick Test

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