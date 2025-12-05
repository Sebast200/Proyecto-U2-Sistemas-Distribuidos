# Proyecto U3 - Sistemas Distribuidos

Este proyecto implementa tres aplicaciones distribuidas que demuestran diferentes arquitecturas de bases de datos, balanceo de carga y alta disponibilidad usando Docker Compose.

## 📋 Descripción General

El proyecto está compuesto por tres aplicaciones independientes que comparten una red común:

1. **App 1 - Shopping List**: Sistema de listas de compras con MariaDB Galera Cluster
2. **App 2 - Hospital**: Sistema hospitalario con PostgreSQL y replicación activa/pasiva
3. **App 3 - Biblioteca**: Sistema de biblioteca con MySQL y replicación maestro-esclavo

## 🏗️ Arquitectura del Proyecto

```
Proyecto-U3/
├── app1/               # Shopping List (Galera Cluster)
│   ├── back/          # Backend Node.js + Express
│   ├── front/         # Frontend React + TypeScript + Vite
│   ├── galera.cnf     # Configuración Galera Cluster
│   ├── proxysql.cnf   # Configuración ProxySQL
│   ├── nginx-backend.conf
│   └── nginx-frontend.conf
├── app2/              # Hospital (PostgreSQL)
│   ├── db/           # Scripts de inicialización
│   ├── haproxy.cfg   # Configuración HAProxy
│   └── vigilante.sh  # Script de monitoreo
├── app3/              # Biblioteca (MySQL)
│   ├── aplicacion/   # GUI Tkinter
│   └── database/     # Scripts y configuración MySQL
├── Middleware/        # Middleware Node.js para App 3
└── docker-compose.yaml
```

## 📦 Aplicación 1: Shopping List

### Tecnologías
- **Frontend**: React 19 + TypeScript + Vite
- **Backend**: Node.js + Express + Sequelize
- **Base de Datos**: MariaDB 11 con Galera Cluster (3 nodos)
- **Proxy**: ProxySQL para balanceo de carga
- **Load Balancer**: Nginx (frontend y backend)

### Características
- Cluster de base de datos multi-maestro con Galera
- Escalabilidad horizontal con réplicas de frontend y backend
- Balanceo de carga con Nginx
- Alta disponibilidad con ProxySQL

### Puertos
- `3001`: Nginx Frontend
- `3002`: Nginx Backend
- `6033`: ProxySQL (MySQL)
- `6032`: ProxySQL Admin

### Iniciar App 1
```bash
# Iniciar el cluster Galera en orden
docker compose up -d galera-node-1 && sleep 10 && \
docker compose up -d galera-node-2 galera-node-3 proxysql && sleep 10 && \
docker compose up -d app1-backend app1-frontend nginx-backend nginx-frontend

# Escalar las réplicas
docker compose up -d --scale app1-backend=2 --scale app1-frontend=2
```

### Acceso
- **Aplicación Web**: http://localhost:3001
- **API Backend**: http://localhost:3002
- **ProxySQL Admin**: `mysql -h127.0.0.1 -P6032 -uadmin -padmin`

## 🏥 Aplicación 2: Hospital

### Tecnologías
- **Base de Datos**: PostgreSQL 15 (maestro-réplica)
- **Load Balancer**: HAProxy
- **Monitoreo**: Script Vigilante con Docker CLI

### Características
- Replicación streaming de PostgreSQL
- Separación de lecturas y escrituras
- Monitoreo automático de salud de réplicas
- Failover manual con script vigilante

### Puertos
- `5000`: HAProxy - Escrituras (Master)
- `5001`: HAProxy - Lecturas (Replica)

### Configuración de Replicación
- **Master**: Configurado con WAL level=replica
- **Replica**: Hot standby mode activo
- **HAProxy**: Balance round-robin para lecturas

### Iniciar App 2
```bash
docker compose up -d pg-master pg-replica haproxy vigilante
```
Luego para abrir la aplicación
```bash
javac -encoding UTF-8 -cp "src;lib/postgresql-42.7.3.jar" -d bin src/*.java 
java -cp "bin;lib/postgresql-42.7.3.jar" Main
```
### Conexión a la Base de Datos
```bash
# Escrituras (Master)
psql -h localhost -p 5000 -U admin -d hospital_db

# Lecturas (Replica)
psql -h localhost -p 5001 -U admin -d hospital_db
```

## 📚 Aplicación 3: Biblioteca

### Tecnologías
- **Base de Datos**: MySQL 5.7 con replicación GTID
- **Orchestrator**: Gestión automática de topología
- **Middleware**: Node.js + Express + MySQL2
- **GUI**: Python Tkinter

### Características
- MySQL master con 3 réplicas read-only
- Orchestrator para gestión de replicación
- Middleware para separación de lecturas/escrituras
- Interfaz gráfica de escritorio

### Puertos
- `3000`: Orchestrator Web UI
- `3307`: MySQL Master
- `4000`: Middleware API

### Configuración
- **Server IDs**: Master(1), Replica1(2), Replica2(3), Replica3(4)
- **GTID Mode**: Habilitado en todos los nodos
- **Binlog Format**: ROW

### Iniciar App 3
```bash
docker compose up -d mysql-master mysql-replica1 mysql-replica2 mysql-replica3 \
                     orchestrator middleware app3-gui
```

### Acceso
- **Orchestrator UI**: http://localhost:3000
- **Middleware API**: http://localhost:4000
- **GUI Tkinter**: Se ejecuta en el contenedor (requiere X11)

### Verificar Replicación
```bash
# Conectar al master
docker exec -it mysql-master mysql -uroot -prootpass

# Verificar estado de réplicas
SHOW SLAVE HOSTS;
```

## 🚀 Inicio Rápido

### Prerrequisitos
- Docker Engine 20.10+
- Docker Compose 2.0+
- Mínimo 8GB RAM
- Puertos: 3000-3002, 4000, 5000-5001, 6032-6033, 3307

### Instalación Completa
```bash
# Clonar el repositorio
cd Proyecto-U3

# Iniciar todas las aplicaciones
# App 1
docker compose up -d galera-node-1 && sleep 10 && \
docker compose up -d galera-node-2 galera-node-3 proxysql && sleep 10 && \
docker compose up -d app1-backend app1-frontend nginx-backend nginx-frontend && \
docker compose up -d --scale app1-backend=2 --scale app1-frontend=2

# App 2
docker compose up -d pg-master pg-replica haproxy vigilante

# App 3
docker compose up -d mysql-master && sleep 15 && \
docker compose up -d mysql-replica1 mysql-replica2 mysql-replica3 && sleep 10 && \
docker compose up -d orchestrator middleware app3-gui
```

### Verificar Estado
```bash
# Ver todos los contenedores
docker compose ps

# Ver logs
docker compose logs -f [servicio]
```

## 🔧 Comandos Útiles

### Gestión de Servicios
```bash
# Detener todo
docker compose down

# Detener y eliminar volúmenes
docker compose down -v

# Reiniciar un servicio específico
docker compose restart [servicio]

# Ver logs en tiempo real
docker logs -f [servicio]
```

### Escalar Servicios
```bash
# App 1: Escalar backend y frontend
docker compose up -d --scale app1-backend=3 --scale app1-frontend=3
```

### Base de Datos

#### Galera Cluster (App 1)
```bash
# Conectar a ProxySQL
mysql -h127.0.0.1 -P6033 -uroot -p123456 -Dproyecto3

# Ver estado del cluster
docker exec -it galera-node-1 mysql -uroot -p123456 \
  -e "SHOW STATUS LIKE 'wsrep_cluster_size';"
```

#### PostgreSQL (App 2)
```bash
# Ver estado de replicación
docker exec -it pg-master psql -U admin -d hospital_db \
  -c "SELECT * FROM pg_stat_replication;"
```

#### MySQL (App 3)
```bash
# Conectar al master
docker exec -it mysql-master mysql -uroot -prootpass -Dbiblioteca

# Ver estado de replicación
docker exec -it mysql-replica1 mysql -uroot -prootpass \
  -e "SHOW SLAVE STATUS\G"
```

## 🌐 Infraestructura de Red

Todas las aplicaciones comparten una red bridge llamada `main-network` que permite la comunicación entre todos los servicios.

### Resolución de Nombres
Los servicios se comunican usando los nombres definidos en docker-compose:
- `galera-node-1`, `galera-node-2`, `galera-node-3`
- `proxysql`
- `pg-master`, `pg-replica`
- `mysql-master`, `mysql-replica1`, `mysql-replica2`, `mysql-replica3`
- `haproxy`, `orchestrator`, `middleware`

## 💾 Volúmenes Persistentes

El proyecto utiliza volúmenes Docker para persistir datos:
- `galera_data_1`, `galera_data_2`, `galera_data_3`: Datos de Galera Cluster
- `orchestrator-data`: Datos de Orchestrator

## 🔍 Monitoreo y Administración

### App 1 - ProxySQL
- Admin Interface: `mysql -h127.0.0.1 -P6032 -uadmin -padmin`
- Ver estadísticas:
  ```sql
  SELECT * FROM stats_mysql_connection_pool;
  SELECT * FROM stats_mysql_query_digest;
  ```

### App 2 - Vigilante
El script `vigilante.sh` monitorea automáticamente la salud de la réplica de PostgreSQL y puede ser extendido para failover automático.

### App 3 - Orchestrator
- Web UI: http://localhost:3000
- Visualiza la topología de replicación
- Permite failover manual y relocación de réplicas



