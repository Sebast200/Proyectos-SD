#!/bin/bash
set -e

# Configuración del Maestro
psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE TABLE citas (
        id SERIAL PRIMARY KEY,
        paciente VARCHAR(100) NOT NULL,
        fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
        descripcion TEXT
    );
    INSERT INTO citas (paciente, descripcion) VALUES ('Prueba Inicial', 'Paciente cero');

    CREATE USER replicator WITH REPLICATION ENCRYPTED PASSWORD 'replicapass';
    SELECT * FROM pg_create_physical_replication_slot('replication_slot');
EOSQL

echo "host replication all 0.0.0.0/0 md5" >> "$PGDATA/pg_hba.conf"
pg_ctl reload || true