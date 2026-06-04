-- Clean reset: Drop all migration tables and schema history
USE store_intelligence;

-- Disable foreign key checks temporarily
SET FOREIGN_KEY_CHECKS = 0;

-- Drop all tables to start fresh
DROP TABLE IF EXISTS flyway_schema_history;
DROP TABLE IF EXISTS pos_transactions;
DROP TABLE IF EXISTS store_events;
DROP TABLE IF EXISTS stores;
DROP TABLE IF EXISTS visitor_sessions;
DROP TABLE IF EXISTS session_zones;
DROP TABLE IF EXISTS anomalies;

-- Re-enable foreign key checks
SET FOREIGN_KEY_CHECKS = 1;

-- Verify database is clean
SHOW TABLES;

