-- Ensures the spotprice user has full privileges on the database.
-- The database and user are already created by docker-compose env vars,
-- this script handles explicit privilege grants.
GRANT ALL PRIVILEGES ON DATABASE spotprice TO spotprice;
