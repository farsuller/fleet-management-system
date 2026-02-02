-- V003: Create Rentals Schema
-- This migration creates tables for rental management with double-booking prevention

-- Customers table: Customer information
CREATE TABLE customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID UNIQUE REFERENCES users(id) ON DELETE SET NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(20) NOT NULL,
    driver_license_number VARCHAR(50) NOT NULL UNIQUE,
    driver_license_expiry DATE NOT NULL,
    address TEXT,
    city VARCHAR(100),
    state VARCHAR(100),
    postal_code VARCHAR(20),
    country VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Rentals table: Core rental information
CREATE TABLE rentals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rental_number VARCHAR(50) NOT NULL UNIQUE,
    customer_id UUID NOT NULL REFERENCES customers(id),
    vehicle_id UUID NOT NULL REFERENCES vehicles(id),
    status VARCHAR(20) NOT NULL CHECK (status IN ('RESERVED', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    
    -- Rental period
    start_date TIMESTAMPTZ NOT NULL,
    end_date TIMESTAMPTZ NOT NULL,
    actual_start_date TIMESTAMPTZ,
    actual_end_date TIMESTAMPTZ,
    
    -- Pricing
    daily_rate_cents INTEGER NOT NULL CHECK (daily_rate_cents >= 0),
    total_amount_cents INTEGER NOT NULL CHECK (total_amount_cents >= 0),
    currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',
    
    -- Odometer
    start_odometer_km INTEGER CHECK (start_odometer_km >= 0),
    end_odometer_km INTEGER CHECK (end_odometer_km >= 0),
    
    -- Locations
    pickup_location VARCHAR(255),
    dropoff_location VARCHAR(255),
    
    -- Metadata
    notes TEXT,
    created_by_user_id UUID REFERENCES users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0,
    
    -- Constraints
    CONSTRAINT rental_dates_valid CHECK (end_date > start_date),
    CONSTRAINT actual_dates_valid CHECK (actual_end_date IS NULL OR actual_end_date >= actual_start_date),
    CONSTRAINT odometer_valid CHECK (end_odometer_km IS NULL OR end_odometer_km >= start_odometer_km)
);

-- Double-booking prevention using exclusion constraint
-- This ensures no overlapping rentals for the same vehicle when status is RESERVED or ACTIVE
CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE rental_periods (
    rental_id UUID PRIMARY KEY REFERENCES rentals(id) ON DELETE CASCADE,
    vehicle_id UUID NOT NULL REFERENCES vehicles(id),
    period TSTZRANGE NOT NULL,
    status VARCHAR(20) NOT NULL,
    
    -- Exclusion constraint: prevent overlapping periods for same vehicle in RESERVED or ACTIVE status
    EXCLUDE USING GIST (
        vehicle_id WITH =,
        period WITH &&
    ) WHERE (status IN ('RESERVED', 'ACTIVE'))
);

-- Rental charges table: Additional charges beyond base rental
CREATE TABLE rental_charges (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rental_id UUID NOT NULL REFERENCES rentals(id) ON DELETE CASCADE,
    charge_type VARCHAR(50) NOT NULL CHECK (charge_type IN ('FUEL', 'DAMAGE', 'LATE_FEE', 'CLEANING', 'TOLL', 'OTHER')),
    description TEXT NOT NULL,
    amount_cents INTEGER NOT NULL CHECK (amount_cents >= 0),
    currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',
    charged_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    charged_by_user_id UUID REFERENCES users(id)
);

-- Rental payments table: Track payments for rentals
CREATE TABLE rental_payments (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    rental_id UUID NOT NULL REFERENCES rentals(id) ON DELETE CASCADE,
    payment_method VARCHAR(50) NOT NULL CHECK (payment_method IN ('CREDIT_CARD', 'DEBIT_CARD', 'CASH', 'BANK_TRANSFER')),
    amount_cents INTEGER NOT NULL CHECK (amount_cents > 0),
    currency_code VARCHAR(3) NOT NULL DEFAULT 'USD',
    transaction_reference VARCHAR(255),
    status VARCHAR(20) NOT NULL CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REFUNDED')),
    paid_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for performance
CREATE INDEX idx_rentals_customer_id ON rentals(customer_id);
CREATE INDEX idx_rentals_vehicle_id ON rentals(vehicle_id);
CREATE INDEX idx_rentals_status ON rentals(status);
CREATE INDEX idx_rentals_start_date ON rentals(start_date);
CREATE INDEX idx_rentals_end_date ON rentals(end_date);
CREATE INDEX idx_rentals_rental_number ON rentals(rental_number);
CREATE INDEX idx_rental_periods_vehicle_id ON rental_periods(vehicle_id);
CREATE INDEX idx_rental_charges_rental_id ON rental_charges(rental_id);
CREATE INDEX idx_rental_payments_rental_id ON rental_payments(rental_id);
CREATE INDEX idx_customers_email ON customers(email);
CREATE INDEX idx_customers_driver_license ON customers(driver_license_number);

-- Triggers for updated_at
CREATE TRIGGER update_customers_updated_at BEFORE UPDATE ON customers
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_rentals_updated_at BEFORE UPDATE ON rentals
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_rental_payments_updated_at BEFORE UPDATE ON rental_payments
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Trigger for version increment
CREATE TRIGGER increment_rentals_version BEFORE UPDATE ON rentals
    FOR EACH ROW EXECUTE FUNCTION increment_version();

-- Function to sync rental_periods when rental is created/updated
CREATE OR REPLACE FUNCTION sync_rental_period()
RETURNS TRIGGER AS $$
BEGIN
    -- Delete existing period if status changed to COMPLETED or CANCELLED
    IF NEW.status IN ('COMPLETED', 'CANCELLED') THEN
        DELETE FROM rental_periods WHERE rental_id = NEW.id;
    -- Insert or update period for RESERVED or ACTIVE rentals
    ELSIF NEW.status IN ('RESERVED', 'ACTIVE') THEN
        INSERT INTO rental_periods (rental_id, vehicle_id, period, status)
        VALUES (NEW.id, NEW.vehicle_id, tstzrange(NEW.start_date, NEW.end_date, '[)'), NEW.status)
        ON CONFLICT (rental_id) DO UPDATE
        SET vehicle_id = NEW.vehicle_id,
            period = tstzrange(NEW.start_date, NEW.end_date, '[)'),
            status = NEW.status;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER sync_rental_period_trigger
    AFTER INSERT OR UPDATE ON rentals
    FOR EACH ROW
    EXECUTE FUNCTION sync_rental_period();
