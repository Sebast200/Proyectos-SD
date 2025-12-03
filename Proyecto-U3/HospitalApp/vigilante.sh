#!/bin/sh
echo "--- INICIANDO VIGILANTE AUTOMATICO ---"

# Esperamos un poco a que todo arranque
sleep 5

while true; do
    # 1. Preguntar a Docker si el maestro esta corriendo
    # En Windows/WSL, el socket responde igual que en Linux
    STATUS=$(docker inspect -f '{{.State.Running}}' pg-master 2>/dev/null)

    if [ "$STATUS" = "true" ]; then
        echo "✅ Maestro OK"
    else
        echo "🚨 ALERTA: Maestro CAIDO. Iniciando Failover..."
        
        # 2. Enviar orden de promocion a la replica
        docker exec pg-replica su-exec postgres pg_ctl promote -D /var/lib/postgresql/data
        
        if [ $? -eq 0 ]; then
            echo "🏆 EXITO: Replica promovida a Nuevo Maestro."
            echo "💤 Durmiendo para siempre (trabajo terminado)."
            # Mantenemos el contenedor vivo pero sin hacer nada
            tail -f /dev/null
        else
            echo "⚠️ Error al promover (o ya fue promovida)."
        fi
    fi
    sleep 2
done