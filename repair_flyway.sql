-- Repair Flyway Migration History
-- This script clears the schema history so Flyway can re-run migrations

USE store_intelligence;

-- Drop the Flyway schema history table to reset migrations
DROP TABLE IF EXISTS flyway_schema_history;

-- Verify all user tables are still there
SHOW TABLES;

