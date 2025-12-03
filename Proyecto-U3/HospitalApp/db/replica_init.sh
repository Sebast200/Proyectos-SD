#!/bin/bash
set -e

echo ">>> Esperando al Maestro..."
# Esperamos a que el maestro responda
until pg_isready -h pg-master -p 5432 -U admin; do
    echo "Esperando..."
    sleep 2
done

echo ">>> Maestro listo. Limpiando datos antiguos..."
# 1. Limpieza (Esto lo hace root)
rm -rf /var/lib/postgresql/data/*
chmod 0700 /var/lib/postgresql/data
# Aseguramos que la carpeta vacía sea del usuario postgres
chown postgres:postgres /var/lib/postgresql/data

echo ">>> Clonando base de datos..."
# 2. Clonación (Lo hacemos como usuario 'postgres' usando su-exec)
# Esto evita problemas de permisos en los archivos descargados
su-exec postgres bash -c "export PGPASSWORD=replicapass; pg_basebackup -h pg-master -p 5432 -U replicator -D /var/lib/postgresql/data -R -X stream -C -S replication_slot_replica" || echo "Aviso: Slot ya existe o backup completado."

echo ">>> Iniciando Réplica..."
# 3. ARRANQUE (La solución a tu error):
# Usamos 'su-exec' para ejecutar el proceso final como usuario 'postgres'
exec su-exec postgres postgres