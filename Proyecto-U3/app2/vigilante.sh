#!/bin/sh

# --- FUNCION: Esperar a que una DB responda ---
wait_for_db() {
    HOST=$1
    echo "⏳ Esperando a que $HOST este listo..."
    until pg_isready -h $HOST -U admin >/dev/null 2>&1; do
        sleep 1
        echo -n "."
    done
    echo " ✅ $HOST responde."
}

# --- FUNCION: Actualizar HAProxy ---
update_haproxy() {
    MASTER_HOST=$1
    echo "🔄 HAProxy -> Apuntando a $MASTER_HOST"
    cat <<EOF > /workdir/haproxy.cfg
global
    maxconn 100
defaults
    log global
    mode tcp
    retries 2
    timeout client 30m
    timeout connect 4s
    timeout server 30m
    timeout check 5s

listen database
    bind *:5000
    server master $MASTER_HOST:5432 check
EOF
    docker restart haproxy >/dev/null 2>&1
}

# --- FUNCION: Reconstruir Esclavo ---
rebuild_slave() {
    SLAVE=$1
    MASTER=$2
    echo "🛠️  Reparando $SLAVE (Clonando de $MASTER)..."
    
    docker stop $SLAVE >/dev/null 2>&1
    
    # Usamos un contenedor temporal para clonar de forma segura
    docker run --rm --network hospital-net \
        --volumes-from $SLAVE --volumes-from $MASTER \
        postgres:15-alpine \
        bash -c "
            echo '   -> Borrando datos viejos...'
            rm -rf /var/lib/postgresql/data/*
            chmod 0700 /var/lib/postgresql/data
            
            echo '   -> Esperando a que $MASTER permita clonar...'
            until pg_isready -h $MASTER -U admin >/dev/null 2>&1; do sleep 1; done
            
            echo '   -> Clonando...'
            export PGPASSWORD=adminpassword
            pg_basebackup -h $MASTER -U admin -D /var/lib/postgresql/data -R -X stream
        " >/dev/null 2>&1
        
    docker start $SLAVE >/dev/null 2>&1
    echo "✅ $SLAVE reparado y reiniciado."
}

# ==========================================
# INICIO DEL SCRIPT
# ==========================================
echo "--- 🛡️ VIGILANTE ROBUSTO INICIADO ---"
CURRENT_MASTER="pg-a"
CURRENT_SLAVE="pg-b"

# 1. Esperamos a que pg-a arranque totalmente antes de tocar nada
wait_for_db $CURRENT_MASTER

# 2. Creamos la tabla en el Maestro (pg-a)
echo "📄 Creando tabla 'citas' en $CURRENT_MASTER..."
docker exec $CURRENT_MASTER psql -U admin -d hospital_db -c "CREATE TABLE IF NOT EXISTS citas (id SERIAL PRIMARY KEY, paciente VARCHAR(100), fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP, descripcion TEXT);" >/dev/null 2>&1

# 3. Configuramos HAProxy inicial
update_haproxy $CURRENT_MASTER

# 4. Forzamos la reconstrucción de pg-b para asegurar que tenga la tabla
rebuild_slave "pg-b" "pg-a"

echo "✅ Cluster Sincronizado y Listo."

# ==========================================
# BUCLE DE VIGILANCIA
# ==========================================
while true; do
    sleep 3
    
    # Chequeo Maestro
    M_STATUS=$(docker inspect -f '{{.State.Running}}' $CURRENT_MASTER 2>/dev/null)
    
    if [ "$M_STATUS" = "true" ]; then
        # Chequeo Esclavo
        S_STATUS=$(docker inspect -f '{{.State.Running}}' $CURRENT_SLAVE 2>/dev/null)
        if [ "$S_STATUS" != "true" ]; then
            echo "⚠️  Replica $CURRENT_SLAVE caida. Reparando..."
            rebuild_slave $CURRENT_SLAVE $CURRENT_MASTER
        fi
    else
        echo "🚨 ALERTA: $CURRENT_MASTER ha muerto."
        
        S_STATUS=$(docker inspect -f '{{.State.Running}}' $CURRENT_SLAVE 2>/dev/null)
        
        if [ "$S_STATUS" = "true" ]; then
            echo "⚡ Promoviendo a $CURRENT_SLAVE..."
            docker exec $CURRENT_SLAVE pg_ctl promote -D /var/lib/postgresql/data >/dev/null 2>&1
            
            # Intercambio de roles
            OLD_MASTER=$CURRENT_MASTER
            CURRENT_MASTER=$CURRENT_SLAVE
            CURRENT_SLAVE=$OLD_MASTER
            
            update_haproxy $CURRENT_MASTER
            echo "👑 Nuevo Rey: $CURRENT_MASTER"
            
            rebuild_slave $CURRENT_SLAVE $CURRENT_MASTER
        else
            echo "💀 ERROR CRITICO: Todo muerto. Esperando resurrección..."
            # Logica de resurrección simple
            sleep 5
            if [ "$(docker inspect -f '{{.State.Running}}' pg-a 2>/dev/null)" = "true" ]; then
                 CURRENT_MASTER="pg-a"; CURRENT_SLAVE="pg-b"
            elif [ "$(docker inspect -f '{{.State.Running}}' pg-b 2>/dev/null)" = "true" ]; then
                 CURRENT_MASTER="pg-b"; CURRENT_SLAVE="pg-a"
            fi
            if [ "$(docker inspect -f '{{.State.Running}}' $CURRENT_MASTER 2>/dev/null)" = "true" ]; then
                docker exec $CURRENT_MASTER pg_ctl promote -D /var/lib/postgresql/data >/dev/null 2>&1
                update_haproxy $CURRENT_MASTER
                echo "🚀 Sistema revivido con $CURRENT_MASTER"
            fi
        fi
    fi
done
