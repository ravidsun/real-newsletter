-- PostgreSQL Script to grant permissions to realnewsuser
-- Run this as the postgres superuser to fix the "permission denied for schema public" error
-- Command: psql -U postgres -d realnews -f grant-permissions.sql

-- Grant all privileges on the public schema to realnewsuser
GRANT ALL PRIVILEGES ON SCHEMA public TO realnewsuser;

-- Grant privileges on all existing tables in public schema
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO realnewsuser;

-- Grant privileges on all sequences (for auto-increment/serial columns)
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO realnewsuser;

-- Set default privileges for future tables created in public schema
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON TABLES TO realnewsuser;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON SEQUENCES TO realnewsuser;

-- Set default privileges for future functions
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT ALL ON FUNCTIONS TO realnewsuser;

-- Also grant permissions on the schema itself to the postgres role's owned objects
ALTER DEFAULT PRIVILEGES FOR USER postgres IN SCHEMA public GRANT ALL ON TABLES TO realnewsuser;
ALTER DEFAULT PRIVILEGES FOR USER postgres IN SCHEMA public GRANT ALL ON SEQUENCES TO realnewsuser;

