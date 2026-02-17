-- V001: Create Users and Authentication Schema
-- This migration creates tables for user management and authentication
-- Users table: Core user information
CREATE TABLE users (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone VARCHAR(20),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Roles table: Define available roles in the system
CREATE TABLE roles (
    id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    description TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- User roles junction table: Many-to-many relationship
CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    role_id UUID NOT NULL REFERENCES roles(id) ON DELETE CASCADE,
    assigned_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (user_id, role_id)
);

-- Staff profiles: Additional information for staff members
CREATE TABLE staff_profiles (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    employee_id VARCHAR(50) NOT NULL UNIQUE,
    department VARCHAR(100),
    position VARCHAR(100),
    hire_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Insert default roles
INSERT INTO roles (id, name, description) VALUES
    (gen_random_uuid(), 'ADMIN', 'System administrator with full access'),
    (gen_random_uuid(), 'FLEET_MANAGER', 'Manages fleet operations and vehicles'),
    (gen_random_uuid(), 'RENTAL_AGENT', 'Handles rental reservations and customer service'),
    (gen_random_uuid(), 'MAINTENANCE_STAFF', 'Manages vehicle maintenance and repairs'),
    (gen_random_uuid(), 'ACCOUNTANT', 'Manages financial transactions and reporting'),
    (gen_random_uuid(), 'CUSTOMER', 'Regular customer with rental privileges');

-- Indexes for performance
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_is_active ON users(is_active);
CREATE INDEX idx_user_roles_user_id ON user_roles(user_id);
CREATE INDEX idx_user_roles_role_id ON user_roles(role_id);
CREATE INDEX idx_staff_profiles_user_id ON staff_profiles(user_id);
CREATE INDEX idx_staff_profiles_employee_id ON staff_profiles(employee_id);

-- Updated_at trigger function
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ language 'plpgsql';

-- Apply trigger to users table
CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Apply trigger to staff_profiles table
CREATE TRIGGER update_staff_profiles_updated_at BEFORE UPDATE ON staff_profiles
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
