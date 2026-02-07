# 📚 Documentation Guide Index

**Last Updated**: 2026-02-07

This directory contains comprehensive implementation guides for the Fleet Management System.

---

## 🚀 Quick Start

### For New Developers
1. Read **[RUNNING_LOCALLY.md](./RUNNING_LOCALLY.md)** to set up your environment
2. Review **[IMPLEMENTATION-STANDARDS.md](./IMPLEMENTATION-STANDARDS.md)** for coding standards
3. Start with **[API-TEST-SCENARIOS.md](./API-TEST-SCENARIOS.md)** to test the API

### For API Testing
1. **[API-TEST-SCENARIOS.md](./API-TEST-SCENARIOS.md)** - Complete test scenarios with cURL examples
2. **[module-customer-route-implementation.md](./module-customer-route-implementation.md)** - Customer API reference
3. **[module-rental-route-implementation.md](./module-rental-route-implementation.md)** - Rental API reference

---

## 📋 Module Implementation Guides

### Core Modules (Implemented)

| Module | Guide | Status | Description |
|--------|-------|--------|-------------|
| **Users** | [module-user-route-implementation.md](./module-user-route-implementation.md) | ✅ Complete | User authentication and management |
| **Vehicles** | [module-vehicle-route-implementation.md](./module-vehicle-route-implementation.md) | ✅ Complete | Vehicle fleet management |
| **Customers** | [module-customer-route-implementation.md](./module-customer-route-implementation.md) | ✅ Complete | Customer profile and driver management |
| **Rentals** | [module-rental-route-implementation.md](./module-rental-route-implementation.md) | ✅ Complete | Rental lifecycle management |

### Additional Modules (Planned)

| Module | Guide | Status | Description |
|--------|-------|--------|-------------|
| **Maintenance** | [module-maintenance-route-implementation.md](./module-maintenance-route-implementation.md) | 📝 Planned | Vehicle maintenance tracking |
| **Accounting** | [module-accounting-route-implementation.md](./module-accounting-route-implementation.md) | 📝 Planned | Financial transactions |

---

## 🛠️ Development Standards

- **[IMPLEMENTATION-STANDARDS.md](./IMPLEMENTATION-STANDARDS.md)**
  - Coding conventions
  - Architecture patterns
  - Error handling standards
  - API design principles

- **[MODULE-CONSISTENCY-AUDIT.md](./MODULE-CONSISTENCY-AUDIT.md)**
  - Cross-module consistency checks
  - Common patterns verification
  - Quality assurance checklist

- **[API-IMPLEMENTATION-SUMMARY.md](./API-IMPLEMENTATION-SUMMARY.md)**
  - High-level API overview
  - Endpoint summary table
  - Integration points

---

## 📊 API Endpoint Quick Reference

### Customer Management
```
POST   /v1/customers          - Create customer
GET    /v1/customers          - List all customers  
GET    /v1/customers/{id}     - Get customer by ID
```

### Rental Management
```
POST   /v1/rentals                    - Create rental
GET    /v1/rentals                    - List all rentals
GET    /v1/rentals/{id}               - Get rental by ID
POST   /v1/rentals/{id}/activate      - Activate rental
POST   /v1/rentals/{id}/complete      - Complete rental
POST   /v1/rentals/{id}/cancel        - Cancel rental
```

### Vehicle Management
```
POST   /v1/vehicles           - Create vehicle
GET    /v1/vehicles           - List all vehicles
GET    /v1/vehicles/{id}      - Get vehicle by ID
PUT    /v1/vehicles/{id}      - Update vehicle
DELETE /v1/vehicles/{id}      - Delete vehicle
```

### User Management
```
POST   /v1/users              - Create user
GET    /v1/users              - List all users
GET    /v1/users/{id}         - Get user by ID
PUT    /v1/users/{id}         - Update user
DELETE /v1/users/{id}         - Delete user
```

---

## 🔄 Typical Workflow: Creating a Rental

```
1. Create Customer
   POST /v1/customers
   {
     "email": "customer@example.com",
     "firstName": "John",
     "lastName": "Doe",
     "phone": "+63-917-123-4567",
     "driversLicense": "N01-12-345678",
     "driverLicenseExpiry": "2028-12-31"
   }
   → Returns customerId

2. Verify Vehicle Available
   GET /v1/vehicles/{vehicleId}
   → Check status is "AVAILABLE"

3. Create Rental
   POST /v1/rentals
   {
     "customerId": "<from-step-1>",
     "vehicleId": "<from-step-2>",
     "startDate": "2026-02-10T10:00:00Z",
     "endDate": "2026-02-15T10:00:00Z"
   }
   → Returns rentalId, status: "RESERVED"

4. Activate Rental (when customer picks up vehicle)
   POST /v1/rentals/{rentalId}/activate
   → Status changes to "ACTIVE"
   → Vehicle status changes to "RENTED"

5. Complete Rental (when customer returns vehicle)
   POST /v1/rentals/{rentalId}/complete
   {
     "finalMileage": 45680
   }
   → Status changes to "COMPLETED"
   → Vehicle status changes to "AVAILABLE"
```

---

## 📝 Recent Updates

### 2026-02-07: Customer Module Separated
- ✅ Created dedicated `module-customer-route-implementation.md`
- ✅ Separated customer concerns from rental module
- ✅ Cleaned up documentation structure
- ✅ Removed redundant ADDENDUM files

### Key Changes:
- **New Module**: Customer management now has its own dedicated guide
- **Cleaner Structure**: Rental guide focuses only on rental lifecycle
- **Better Organization**: Each module has a single source of truth

---

## 🆘 Troubleshooting

### "Customer not found" error when creating rental
**Solution**: Create the customer first using `POST /v1/customers`, then use the returned `id` in your rental request.

### "Driver's license is expired" error
**Solution**: Ensure `driverLicenseExpiry` is a future date in `YYYY-MM-DD` format.

### "Customer with email X already exists"
**Solution**: Either use a different email or retrieve the existing customer with `GET /v1/customers` and use that customer's ID.

---

## 📞 Support

For questions or issues:
1. Check the relevant module implementation guide
2. Review test scenarios in `API-TEST-SCENARIOS.md`
3. Verify your request matches the examples in the documentation
4. Check the error code in the response and consult the error handling section

---

**Happy Coding! 🚀**
