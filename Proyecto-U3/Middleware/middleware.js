const express = require("express");
const mysql = require("mysql2/promise");
const { Pool } = require('pg');
const app = express();

app.use(express.json());

const poolWrite = mysql.createPool({
    host: process.env.DB_HOST_WRITE || 'mysql-master',
    user: process.env.DB_USER || 'root',
    password: process.env.DB_PASS || 'rootpass',
    database: process.env.DB_NAME || 'biblioteca'
});

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
// RUTAS PROPIAS DE APP 3 (Biblioteca)
// ==========================================

app.get("/api/products", async (req, res) => {
    try {
        const [rows] = await poolRead.query("SELECT * FROM products");
        res.json(rows);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.get("/api/users", async (req, res) => {
    try {
        const [rows] = await poolRead.query("SELECT * FROM users");
        res.json(rows);
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.post("/api/orders", async (req, res) => {
    const { userId, productId } = req.body;
    try {
        const [result] = await poolWrite.query(
            "INSERT INTO orders (user_id, product_id) VALUES (?, ?)", 
            [userId, productId]
        );
        res.json({ 
            status: "Orden creada",
            orderId: result.insertId,
            details: { userId, productId }
        });
    } catch (err) {
        res.status(500).json({ error: err.message });
    }
});

app.get("/health", (req, res) => {
    res.json({ status: "Middleware OK", database: "MySQL Cluster" });
});


// ==========================================
// RUTAS DE CONEXIÓN EXTERNA (DASHBOARD)
// ==========================================

// --- Endpoint de Estado General del Sistema ---
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

// --- Rutas para App 1 (Compras) ---

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

// --- Rutas para App 2 (Hospital) ---

app.get("/api/externo/hospital/citas", async (req, res) => {
    try {
        const result = await poolHospital.query('SELECT * FROM citas ORDER BY id DESC');
        res.json(result.rows);
    } catch (err) {
        console.error("Error Postgres:", err);
        res.status(500).json({ error: "Error conectando al Hospital (Postgres)" });
    }
});

app.listen(4000, () => {
    console.log("Middleware corriendo en http://localhost:4000");
});