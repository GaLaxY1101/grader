#!/bin/sh
# Runs once, on first start of an empty Postgres volume: creates Keycloak's database.
set -e
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE DATABASE keycloak OWNER "$POSTGRES_USER";
EOSQL
