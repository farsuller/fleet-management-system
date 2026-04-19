-- V037__create_bus_truck_tables.sql
-- Add type-specific child tables for buses and trucks.

CREATE TABLE IF NOT EXISTS buses (
    vehicle_id UUID PRIMARY KEY REFERENCES vehicles(id) ON DELETE CASCADE,
    route_number VARCHAR(20),
    door_count INTEGER NOT NULL DEFAULT 2,
    standing_capacity INTEGER,
    has_accessibility_ramp BOOLEAN NOT NULL DEFAULT FALSE,
    has_air_conditioning BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_bus_door_count_positive CHECK (door_count > 0),
    CONSTRAINT chk_bus_standing_capacity_non_negative CHECK (standing_capacity IS NULL OR standing_capacity >= 0)
);

CREATE TABLE IF NOT EXISTS trucks (
    vehicle_id UUID PRIMARY KEY REFERENCES vehicles(id) ON DELETE CASCADE,
    payload_capacity_tons NUMERIC(8,2),
    cargo_type VARCHAR(30),
    axle_count INTEGER NOT NULL DEFAULT 2,
    gross_vehicle_weight_kg INTEGER,
    has_trailer_hitch BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_truck_axle_count_positive CHECK (axle_count > 0),
    CONSTRAINT chk_truck_payload_non_negative CHECK (payload_capacity_tons IS NULL OR payload_capacity_tons >= 0),
    CONSTRAINT chk_truck_gvw_non_negative CHECK (gross_vehicle_weight_kg IS NULL OR gross_vehicle_weight_kg >= 0),
    CONSTRAINT chk_truck_cargo_type CHECK (
        cargo_type IS NULL OR cargo_type IN ('FLATBED', 'REFRIGERATED', 'TANKER', 'BOX', 'CURTAINSIDER')
    )
);

CREATE INDEX IF NOT EXISTS idx_buses_route_number ON buses(route_number);
CREATE INDEX IF NOT EXISTS idx_trucks_cargo_type ON trucks(cargo_type);

COMMENT ON TABLE buses IS 'Type-specific data for vehicles where vehicle_type=BUS';
COMMENT ON TABLE trucks IS 'Type-specific data for vehicles where vehicle_type=TRUCK';
