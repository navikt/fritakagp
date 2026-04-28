#!/usr/bin/env bash
set -e

# NB:
#
# Dette er init-scriptet til docker-containeren som kjøres opp for testing
# og lokal kjøring. Ingenting av det som er her kjøres ute i miljøene (DEV/PROD)
# kun under bygg/test/ og lokal kjøring av applikasjonen.
#

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE USER fritakagp WITH PASSWORD 'fritakagp';
    CREATE DATABASE fritakagp_db;
    CREATE SCHEMA fritakagp;
    GRANT ALL PRIVILEGES ON DATABASE fritakagp_db TO fritakagp;
EOSQL

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "fritakagp_db" <<-EOSQL
    GRANT ALL ON SCHEMA public TO fritakagp;
EOSQL
