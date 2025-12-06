const express = require("express");
const mysql = require("mysql2/promise");
const { Pool } = require('pg');
const app = express();

app.use(express.json());

// Retorna el hostname del master actual para conexión
async function getCurrentMaster() {
    const possibleMasters = ['mysql-master', 'mysql-replica1', 'mysql-replica2', 'mysql-replica3'];
    
    for (const host of possibleMasters) {
        try {
            const testPool = mysql.createPool({
                host: host,
                user: process.env.DB_USER || 'root',
                password: process.env.DB_PASS || 'rootpass',
                database: process.env.DB_NAME || 'biblioteca',
                connectionLimit: 1,
                connectTimeout: 2000
            });
            
            await testPool.query('SELECT 1');
            
            const [rows] = await testPool.query('SELECT @@read_only as ro');
            await testPool.end();
            
            if (rows[0].ro === 0) {
                console.log(`Master encontrado: ${host}`);
                return host;
            }
        } catch (e) {
            continue;
        }
    }
    
    console.log("No se encontró ningún master disponible, usando mysql-master por defecto");
    return 'mysql-master';
}

// Retorna información detallada del master para mostrar en UI (hostname con ID)
async function getMasterInfo() {
    const possibleMasters = ['mysql-master', 'mysql-replica1', 'mysql-replica2', 'mysql-replica3'];
    
    for (const host of possibleMasters) {
        try {
            const testPool = mysql.createPool({
                host: host,
                user: process.env.DB_USER || 'root',
                password: process.env.DB_PASS || 'rootpass',
                database: process.env.DB_NAME || 'biblioteca',
                connectionLimit: 1,
                connectTimeout: 2000
            });
            
            await testPool.query('SELECT 1');
            
            const [rows] = await testPool.query('SELECT @@read_only as ro, @@hostname as hostname');
            await testPool.end();
            
            if (rows[0].ro === 0) {
                // Retornar formato: hostname:puerto
                return `${rows[0].hostname}:3306`;
            }
        } catch (e) {
            continue;
        }
    }
    
    return 'Desconocido';
}

async function getWritePool() {
    const masterHost = await getCurrentMaster();
    return mysql.createPool({
        host: masterHost,
        user: process.env.DB_USER || 'root',
        password: process.env.DB_PASS || 'rootpass',
        database: process.env.DB_NAME || 'biblioteca',
        waitForConnections: true,
        connectionLimit: 10
    });
}

const poolRead = mysql.createPool({
    host: process.env.DB_HOST_READ || 'mysql-replica1',
    user: process.env.DB_USER || 'root',
    password: process.env.DB_PASS || 'rootpass',
    database: process.env.DB_NAME || 'biblioteca'
});

const poolHospital = new Pool({
    user: 'admin',
    host: 'haproxy',       
    database: 'hospital_db',
    password: 'adminpassword',
    port: 5001,            
});

// ==========================================
// RUTAS PROPIAS DE APP 3 (Biblioteca - Casa Matriz)
// ==========================================

app.get("/health", async (req, res) => {
    let masterActual = "Desconocido";
    
    try {
        // Usar getMasterInfo() para obtener el hostname con ID del contenedor
        masterActual = await getMasterInfo();
    } catch (e) {
        console.error("Error detectando master:", e.message);
        masterActual = "Desconocido";
    }

    res.json({ 
        status: "Middleware OK", 
        database: "MySQL Cluster",
        master_actual: masterActual
    });
});


// ==========================================
// RUTAS DE CONEXIÓN EXTERNA (DASHBOARD)
// ==========================================

app.get("/api/system-status", async (req, res) => {
    const status = {
        middleware: "down",
        app1: "down",
        hospital: "down"
    };

    try {
        await poolRead.query("SELECT 1");
        status.middleware = "up";
    } catch (e) {
        console.error("MySQL Middleware Error:", e.message);
    }

    try {
        const response = await fetch("http://app1-backend:3000/lists"); 
        if (response.ok) status.app1 = "up";
    } catch (e) {
        console.error("App1 Connection Error:", e.message);
    }

    try {
        await poolHospital.query('SELECT 1');
        status.hospital = "up";
    } catch (e) {
        console.error("Hospital Error:", e.message);
    }

    res.json(status);
});

app.get("/api/externo/app1/lists", async (req, res) => {
    try {
        const r = await fetch("http://app1-backend:3000/lists");
        if (!r.ok) throw new Error("Error en App1 Lists");
        const data = await r.json();
        res.json(data);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.get("/api/externo/app1/items", async (req, res) => {
    try {
        const listId = req.query.list_id;
        const url = listId 
            ? `http://app1-backend:3000/items?list_id=${listId}` 
            : `http://app1-backend:3000/items`;

        const r = await fetch(url);
        if (!r.ok) throw new Error("Error en App1 Items");
        const data = await r.json();
        res.json(data);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});


app.get("/api/externo/hospital/citas", async (req, res) => {
    try {
        const result = await poolHospital.query('SELECT * FROM citas ORDER BY id DESC');
        res.json(result.rows);
    } catch (err) {
        console.error("Error Postgres:", err);
        res.status(500).json({ error: "Error conectando al Hospital (Postgres)" });
    }
});

// ==========================================
// RUTAS DE SINCRONIZACIÓN
// ==========================================

app.post("/api/sync/app1", async (req, res) => {
    let poolWrite = null;
    try {
        poolWrite = await getWritePool();
        
        const listsRes = await fetch("http://app1-backend:3000/lists");
        const lists = await listsRes.json();

        const itemsRes = await fetch("http://app1-backend:3000/items");
        const items = await itemsRes.json();

        for (const list of lists) {
            await poolWrite.query(
                `INSERT INTO sync_shopping_lists (id, name) VALUES (?, ?) 
                 ON DUPLICATE KEY UPDATE name = VALUES(name), synced_at = CURRENT_TIMESTAMP`,
                [list.id, list.name]
            );
        }

        for (const item of items) {
            await poolWrite.query(
                `INSERT INTO sync_shopping_items (id, description, completed, list_id) VALUES (?, ?, ?, ?) 
                 ON DUPLICATE KEY UPDATE description = VALUES(description), completed = VALUES(completed), 
                 list_id = VALUES(list_id), synced_at = CURRENT_TIMESTAMP`,
                [item.id, item.description, item.completed || false, item.list_id]
            );
        }

        res.json({ status: "App1 sincronizado", lists: lists.length, items: items.length });
    } catch (err) {
        console.error("Error sync app1:", err);
        res.status(500).json({ error: "Error sincronizando App1: " + err.message });
    } finally {
        if (poolWrite) await poolWrite.end();
    }
});

app.post("/api/sync/hospital", async (req, res) => {
    let poolWrite = null;
    try {
        poolWrite = await getWritePool();
        
        const result = await poolHospital.query('SELECT * FROM citas');
        const citas = result.rows;

        for (const cita of citas) {
            await poolWrite.query(
                `INSERT INTO sync_hospital_citas (id, paciente, descripcion, fecha) VALUES (?, ?, ?, ?) 
                 ON DUPLICATE KEY UPDATE paciente = VALUES(paciente), descripcion = VALUES(descripcion), 
                 fecha = VALUES(fecha), synced_at = CURRENT_TIMESTAMP`,
                [cita.id, cita.paciente, cita.descripcion, cita.fecha]
            );
        }

        res.json({ status: "Hospital sincronizado", citas: citas.length });
    } catch (err) {
        console.error("Error sync hospital:", err);
        res.status(500).json({ error: "Error sincronizando Hospital: " + err.message });
    } finally {
        if (poolWrite) await poolWrite.end();
    }
});

// ==========================================
// RUTAS DE BASE DE DATOS LOCAL (CONSULTA)
// ==========================================

app.get("/api/local/lists", async (req, res) => {
    try {
        const [rows] = await poolRead.query("SELECT * FROM sync_shopping_lists ORDER BY id");
        res.json(rows);
    } catch (err) {
        console.error("Error local lists:", err);
        res.status(500).json({ error: err.message });
    }
});

app.get("/api/local/items", async (req, res) => {
    try {
        const [rows] = await poolRead.query("SELECT * FROM sync_shopping_items ORDER BY id");
        res.json(rows);
    } catch (err) {
        console.error("Error local items:", err);
        res.status(500).json({ error: err.message });
    }
});

app.get("/api/local/citas", async (req, res) => {
    try {
        const [rows] = await poolRead.query("SELECT * FROM sync_hospital_citas ORDER BY id DESC");
        res.json(rows);
    } catch (err) {
        console.error("Error local citas:", err);
        res.status(500).json({ error: err.message });
    }
});

app.listen(4000, () => {
    console.log("Middleware corriendo en http://localhost:4000");
});