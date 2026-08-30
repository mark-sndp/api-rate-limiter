-- Login to your local PostgreSQL shell using the admin account and run the following steps
CREATE USER rate_limiter WITH PASSWORD 'rate_limiter';
CREATE DATABASE rate_limiter;
-- Connect to the database and run the commands below
GRANT ALL PRIVILEGES ON DATABASE rate_limiter TO rate_limiter;
GRANT USAGE, CREATE ON SCHEMA public TO rate_limiter;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO rate_limiter;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO rate_limiter;
