-- V020: Seed Fleet Vehicles and Initial Tracking State
-- Inserts 6 tricycle vehicles with fixed UUIDs and places each one at a
-- distinct point along the Village Main Loop (seeded in V018).
-- The route_id references the known route UUID used throughout the tracking module.
-- location_history.vehicle_id / route_id are VARCHAR (no FK) so no dependency on routes row existing.

-- ─────────────────────────────────────────────
-- 1. Seed Vehicles  (ON CONFLICT DO NOTHING = idempotent)
-- ─────────────────────────────────────────────
INSERT INTO vehicles (id, plate_number, make, model, year, status, passenger_capacity,
                      current_odometer_km, color, fuel_type, transmission, daily_rate, currency_code)
VALUES
    ('a1f2e3d4-c5b6-4a7f-8e9d-0c1b2a3f4e01', 'ABC-1234', 'Honda',    'TRX 150',      2020, 'AVAILABLE', 3, 12500, 'Blue',   'GASOLINE', 'MANUAL', 500, 'PHP'),
    ('b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d02', 'DEF-5678', 'Yamaha',   'Mio i125',     2021, 'AVAILABLE', 3,  8200, 'Red',    'GASOLINE', 'MANUAL', 500, 'PHP'),
    ('c3d4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e03', 'GHI-9012', 'Kawasaki', 'Barako II',    2019, 'AVAILABLE', 3, 21000, 'Yellow', 'GASOLINE', 'MANUAL', 500, 'PHP'),
    ('d4e5f6a7-b8c9-4d0e-1f2a-3b4c5d6e7f04', 'JKL-3456', 'Honda',    'TRX 125',      2022, 'AVAILABLE', 3,  4600, 'White',  'GASOLINE', 'MANUAL', 450, 'PHP'),
    ('e5f6a7b8-c9d0-4e1f-2a3b-4c5d6e7f8a05', 'MNO-7890', 'Suzuki',   'Raider R150',  2021, 'AVAILABLE', 3, 15300, 'Green',  'GASOLINE', 'MANUAL', 500, 'PHP'),
    ('f6a7b8c9-d0e1-4f2a-3b4c-5d6e7f8a9b06', 'PQR-2345', 'Yamaha',   'Sniper 150',   2020, 'AVAILABLE', 3,  9700, 'Orange', 'GASOLINE', 'MANUAL', 450, 'PHP')
ON CONFLICT (plate_number) DO NOTHING;

-- ─────────────────────────────────────────────
-- 2. Seed Initial Location History
--    Route: Village Main Loop  68a1a7f1-76dd-4ec9-ad63-fefc22acf428
--    LINESTRING: (121.1037114 14.7021343) → (121.1036057 14.7024576)
--                → (121.1035961 14.7031675) → (121.1043276 14.7048466)
--    Vehicles are spread evenly along the route at different progress values.
-- ─────────────────────────────────────────────
INSERT INTO location_history (id, vehicle_id, route_id, progress, segment_id,
                               speed, heading, status, distance_from_route,
                               latitude, longitude, timestamp)
VALUES
    -- Vehicle 1 · progress 0.08 · near route start · IN_TRANSIT
    (gen_random_uuid(),
     'a1f2e3d4-c5b6-4a7f-8e9d-0c1b2a3f4e01',
     '68a1a7f1-76dd-4ec9-ad63-fefc22acf428',
     0.08, 'seg-001', 11.1, 10.0, 'IN_TRANSIT', 2.5,
     14.70220, 121.10371, NOW() - INTERVAL '1 minute'),

    -- Vehicle 2 · progress 0.25 · quarter way · IN_TRANSIT
    (gen_random_uuid(),
     'b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d02',
     '68a1a7f1-76dd-4ec9-ad63-fefc22acf428',
     0.25, 'seg-002', 13.9, 20.0, 'IN_TRANSIT', 4.1,
     14.70246, 121.10361, NOW() - INTERVAL '45 seconds'),

    -- Vehicle 3 · progress 0.42 · near midpoint · IDLE (stopped at pick-up)
    (gen_random_uuid(),
     'c3d4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e03',
     '68a1a7f1-76dd-4ec9-ad63-fefc22acf428',
     0.42, 'seg-003', 0.0, 180.0, 'IDLE', 1.8,
     14.70283, 121.10360, NOW() - INTERVAL '2 minutes'),

    -- Vehicle 4 · progress 0.58 · past midpoint · IN_TRANSIT
    (gen_random_uuid(),
     'd4e5f6a7-b8c9-4d0e-1f2a-3b4c5d6e7f04',
     '68a1a7f1-76dd-4ec9-ad63-fefc22acf428',
     0.58, 'seg-004', 10.3, 25.0, 'IN_TRANSIT', 3.3,
     14.70317, 121.10360, NOW() - INTERVAL '30 seconds'),

    -- Vehicle 5 · progress 0.75 · three-quarters · IN_TRANSIT
    (gen_random_uuid(),
     'e5f6a7b8-c9d0-4e1f-2a3b-4c5d6e7f8a05',
     '68a1a7f1-76dd-4ec9-ad63-fefc22acf428',
     0.75, 'seg-005', 12.5, 30.0, 'IN_TRANSIT', 5.2,
     14.70380, 121.10385, NOW() - INTERVAL '1 minute 20 seconds'),

    -- Vehicle 6 · progress 0.92 · near route end · IDLE (waiting at depot)
    (gen_random_uuid(),
     'f6a7b8c9-d0e1-4f2a-3b4c-5d6e7f8a9b06',
     '68a1a7f1-76dd-4ec9-ad63-fefc22acf428',
     0.92, 'seg-006', 0.0, 5.0, 'IDLE', 2.0,
     14.70460, 121.10420, NOW() - INTERVAL '3 minutes');
