-- V016: Seed Village Routes and Geofence
-- Seed a sample route using coordinates from the uploaded map.osm
-- This represents a path through the village (e.g., Eagle St to Peacock St area)
INSERT INTO routes (name, description, polyline) VALUES
('Village Main Loop', 'Core route through the main village streets',
 ST_GeomFromText('LINESTRING(121.1037114 14.7021343, 121.1036057 14.7024576, 121.1035961 14.7031675, 121.1043276 14.7048466)', 4326));

-- Seed the Main Depot Geofence (Polygon)
-- A rectangle covering a sample area
INSERT INTO geofences (name, type, boundary) VALUES
('Village Entry Depot', 'DEPOT',
 ST_GeomFromText('POLYGON((121.103 14.702, 121.104 14.702, 121.104 14.703, 121.103 14.703, 121.103 14.702))', 4326));
