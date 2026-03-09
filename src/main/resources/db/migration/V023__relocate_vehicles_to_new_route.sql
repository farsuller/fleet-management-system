-- V023: Relocate vehicles to new route and fix zero-coordinate location records
-- Updates the Village Main Loop polyline to the actual operating route coordinates,
-- corrects any location_history rows that have lat=0/lon=0, inserts fresh
-- current-position records so getLatestVehicleState() returns valid data,
-- and syncs vehicles.last_location for any vehicle still at (0,0).

-- ─────────────────────────────────────────────────────────────────────────────
-- 1. Replace route polyline with actual operating coordinates
--    GeoJSON order is [lon, lat]; WKT LINESTRING is also lon lat.
-- ─────────────────────────────────────────────────────────────────────────────
UPDATE routes
SET
    polyline   = ST_GeomFromText(
        'LINESTRING('
            '121.03202629988448 14.652974012832686,'
            '121.03385928838605 14.649792351767516,'
            '121.03574615623211 14.646767163494118,'
            '121.03790264789080 14.643376727663167,'
            '121.04016694074409 14.640247091489314,'
            '121.04232333273069 14.636752395922969,'
            '121.04458756285226 14.633309753106431,'
            '121.04685197286580 14.628562654148567,'
            '121.04906197922890 14.623607768773638'
        ')', 4326),
    updated_at = NOW()
WHERE name = 'Village Main Loop';

-- ─────────────────────────────────────────────────────────────────────────────
-- 2. Fix existing location_history rows that have lat=0 AND lon=0
--    Each vehicle is assigned a distinct point along the new route.
-- ─────────────────────────────────────────────────────────────────────────────
WITH vehicle_positions (vehicle_id, lat, lon, progress) AS (
    VALUES
        ('a1f2e3d4-c5b6-4a7f-8e9d-0c1b2a3f4e01', 14.652974012832686, 121.03202629988448, 0.00::double precision),
        ('b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d02', 14.649792351767516, 121.03385928838605, 0.13::double precision),
        ('c3d4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e03', 14.646767163494118, 121.03574615623211, 0.25::double precision),
        ('d4e5f6a7-b8c9-4d0e-1f2a-3b4c5d6e7f04', 14.640247091489314, 121.04016694074409, 0.50::double precision),
        ('e5f6a7b8-c9d0-4e1f-2a3b-4c5d6e7f8a05', 14.633309753106431, 121.04458756285226, 0.75::double precision),
        ('f6a7b8c9-d0e1-4f2a-3b4c-5d6e7f8a9b06', 14.623607768773638, 121.04906197922890, 0.92::double precision)
)
UPDATE location_history lh
SET
    latitude  = vp.lat,
    longitude = vp.lon,
    progress  = vp.progress
FROM vehicle_positions vp
WHERE lh.vehicle_id = vp.vehicle_id
  AND lh.latitude   = 0
  AND lh.longitude  = 0;

-- ─────────────────────────────────────────────────────────────────────────────
-- 3. Insert fresh current-position records for all 6 vehicles
--    Timestamp offset spreads vehicles naturally; NOW()-based rows will be the
--    latest returned by getLatestVehicleState() (queries ORDER BY timestamp DESC).
-- ─────────────────────────────────────────────────────────────────────────────
INSERT INTO location_history (
    id, vehicle_id, route_id, progress, segment_id,
    speed, heading, status, distance_from_route,
    latitude, longitude, timestamp
)
SELECT
    gen_random_uuid(),
    vp.vehicle_id,
    r.id::text,
    vp.progress,
    vp.seg_id,
    CASE WHEN vp.status = 'IDLE' THEN 0.0 ELSE 11.5 END,
    149.0,   -- heading SSE along the route
    vp.status,
    1.5,
    vp.lat,
    vp.lon,
    NOW() - (vp.offset_secs || ' seconds')::interval
FROM (
    VALUES
        ('a1f2e3d4-c5b6-4a7f-8e9d-0c1b2a3f4e01', 14.652974012832686, 121.03202629988448, 0.00::double precision, 'IN_TRANSIT', 'seg-001', 120),
        ('b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d02', 14.649792351767516, 121.03385928838605, 0.13::double precision, 'IN_TRANSIT', 'seg-002',  90),
        ('c3d4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e03', 14.646767163494118, 121.03574615623211, 0.25::double precision, 'IDLE',       'seg-003',  60),
        ('d4e5f6a7-b8c9-4d0e-1f2a-3b4c5d6e7f04', 14.640247091489314, 121.04016694074409, 0.50::double precision, 'IN_TRANSIT', 'seg-004',  45),
        ('e5f6a7b8-c9d0-4e1f-2a3b-4c5d6e7f8a05', 14.633309753106431, 121.04458756285226, 0.75::double precision, 'IN_TRANSIT', 'seg-005',  30),
        ('f6a7b8c9-d0e1-4f2a-3b4c-5d6e7f8a9b06', 14.623607768773638, 121.04906197922890, 0.92::double precision, 'IDLE',       'seg-006',  10)
) AS vp(vehicle_id, lat, lon, progress, status, seg_id, offset_secs)
CROSS JOIN (SELECT id FROM routes WHERE name = 'Village Main Loop' LIMIT 1) r;

-- ─────────────────────────────────────────────────────────────────────────────
-- 4. Sync vehicles.last_location for any vehicle still at NULL or (0, 0)
-- ─────────────────────────────────────────────────────────────────────────────
UPDATE vehicles v
SET
    last_location  = ST_SetSRID(ST_MakePoint(vp.lon, vp.lat), 4326),
    route_progress = vp.progress,
    bearing        = 149.0,
    updated_at     = NOW()
FROM (
    VALUES
        ('a1f2e3d4-c5b6-4a7f-8e9d-0c1b2a3f4e01'::uuid, 14.652974012832686, 121.03202629988448, 0.00::double precision),
        ('b2c3d4e5-f6a7-4b8c-9d0e-1f2a3b4c5d02'::uuid, 14.649792351767516, 121.03385928838605, 0.13::double precision),
        ('c3d4e5f6-a7b8-4c9d-0e1f-2a3b4c5d6e03'::uuid, 14.646767163494118, 121.03574615623211, 0.25::double precision),
        ('d4e5f6a7-b8c9-4d0e-1f2a-3b4c5d6e7f04'::uuid, 14.640247091489314, 121.04016694074409, 0.50::double precision),
        ('e5f6a7b8-c9d0-4e1f-2a3b-4c5d6e7f8a05'::uuid, 14.633309753106431, 121.04458756285226, 0.75::double precision),
        ('f6a7b8c9-d0e1-4f2a-3b4c-5d6e7f8a9b06'::uuid, 14.623607768773638, 121.04906197922890, 0.92::double precision)
) AS vp(vehicle_id, lat, lon, progress)
WHERE v.id = vp.vehicle_id
  AND (
      v.last_location IS NULL
      OR ST_Equals(v.last_location, ST_SetSRID(ST_MakePoint(0, 0), 4326))
  );
