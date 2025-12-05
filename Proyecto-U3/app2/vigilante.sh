#!/bin/sh
echo "--- INICIANDO VIGILANTE ETERNO (CICLO INFINITO) ---"

CURRENT_MASTER="pg-master"
CURRENT_REPLICA="pg-replica"

# Espera inicial para que Docker arranque
sleep 5

while true; do
    # 1. Chequeo del Maestro
    STATUS=$(docker inspect -f '{{.State.Running}}' $CURRENT_MASTER 2>/dev/null)

    if [ "$STATUS" = "true" ]; then
        echo "✅ Maestro ($CURRENT_MASTER) OK"
    else
        echo "🚨 ALERTA: $CURRENT_MASTER HA CAIDO."
        
        # 2. Chequeo del Suplente antes de promover
        REP_STATUS=$(docker inspect -f '{{.State.Running}}' $CURRENT_REPLICA 2>/dev/null)
        
        if [ "$REP_STATUS" = "true" ]; then
            echo "⚡ Promoviendo a $CURRENT_REPLICA..."
            
            # 3. Promocion
            docker exec $CURRENT_REPLICA su-exec postgres pg_ctl promote -D /var/lib/postgresql/data
            
            echo "🏆 EXITO: $CURRENT_REPLICA es el nuevo Maestro."
            
            # 4. INTERCAMBIO DE ROLES (SWAP)
            TEMP=$CURRENT_MASTER
            CURRENT_MASTER=$CURRENT_REPLICA
            CURRENT_REPLICA=$TEMP
            
            echo "🔄 CAMBIO DE ROLES: Ahora vigilare a $CURRENT_MASTER"
            echo "⚠️  Recuerda levantar manualmante a $CURRENT_REPLICA para tener backup."
        else
            echo "💀 CRITICO: Ambos servidores estan muertos."
        fi
    fi
    sleep 2
done
