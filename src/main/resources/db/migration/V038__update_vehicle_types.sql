-- V038: Update vehicle types for existing vehicles
-- Sets vehicle_type based on make/model for proper categorization

-- Update Trucks (based on known truck makes/models)
UPDATE vehicles SET vehicle_type = 'TRUCK'
WHERE (make = 'Volvo' AND model = 'B7R')
   OR (make = 'ISUZU' AND model = 'F-Series');

-- Update remaining vehicles to SEDAN as default (CAR type will be added in V039)
UPDATE vehicles SET vehicle_type = 'SEDAN'
WHERE vehicle_type = 'OTHER';

-- Note: BUS vehicles should be created directly via POST /v1/buses endpoint
-- which automatically sets vehicle_type = 'BUS'
