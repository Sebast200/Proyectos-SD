const express = require('express');
const cors = require('cors');
const { pool, initializeDB } = require('./db');
require('dotenv').config();

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());

// Initialize Database
initializeDB();

// --- Lists Routes ---

// Get all lists
app.get('/lists', async (req, res) => {
    let conn;
    try {
        conn = await pool.getConnection();
        const rows = await conn.query('SELECT * FROM lista');
        res.json(rows);
    } catch (err) {
        res.status(500).json({ error: err.message });
    } finally {
        if (conn) conn.release();
    }
});

// Create a list
app.post('/lists', async (req, res) => {
    const { name } = req.body;
    let conn;
    try {
        conn = await pool.getConnection();
        const result = await conn.query('INSERT INTO lista (name) VALUES (?)', [name]);
        res.status(201).json({ id: Number(result.insertId), name });
    } catch (err) {
        res.status(500).json({ error: err.message });
    } finally {
        if (conn) conn.release();
    }
});

// Update a list
app.put('/lists/:id', async (req, res) => {
    const { id } = req.params;
    const { name } = req.body;
    let conn;
    try {
        conn = await pool.getConnection();
        await conn.query('UPDATE lista SET name = ? WHERE id = ?', [name, id]);
        res.json({ message: 'List updated' });
    } catch (err) {
        res.status(500).json({ error: err.message });
    } finally {
        if (conn) conn.release();
    }
});

// Delete a list
app.delete('/lists/:id', async (req, res) => {
    const { id } = req.params;
    let conn;
    try {
        conn = await pool.getConnection();
        await conn.query('DELETE FROM lista WHERE id = ?', [id]);
        res.json({ message: 'List deleted' });
    } catch (err) {
        res.status(500).json({ error: err.message });
    } finally {
        if (conn) conn.release();
    }
});

// --- Items Routes ---

// Get all items (optionally filter by list_id)
app.get('/items', async (req, res) => {
    const { list_id } = req.query;
    let conn;
    try {
        conn = await pool.getConnection();
        let query = 'SELECT * FROM item';
        let params = [];
        if (list_id) {
            query += ' WHERE list_id = ?';
            params.push(list_id);
        }
        const rows = await conn.query(query, params);
        res.json(rows);
    } catch (err) {
        res.status(500).json({ error: err.message });
    } finally {
        if (conn) conn.release();
    }
});

// Create an item
app.post('/items', async (req, res) => {
    const { description, list_id } = req.body;
    let conn;
    try {
        conn = await pool.getConnection();
        const result = await conn.query(
            'INSERT INTO item (description, list_id) VALUES (?, ?)',
            [description, list_id]
        );
        res.status(201).json({ id: Number(result.insertId), description, list_id, completed: false });
    } catch (err) {
        res.status(500).json({ error: err.message });
    } finally {
        if (conn) conn.release();
    }
});

// Update an item (e.g. toggle completed)
app.put('/items/:id', async (req, res) => {
    const { id } = req.params;
    const { description, completed } = req.body;
    let conn;
    try {
        conn = await pool.getConnection();
        // Dynamic update query
        let updates = [];
        let params = [];
        if (description !== undefined) {
            updates.push('description = ?');
            params.push(description);
        }
        if (completed !== undefined) {
            updates.push('completed = ?');
            params.push(completed);
        }

        if (updates.length > 0) {
            params.push(id);
            await conn.query(`UPDATE item SET ${updates.join(', ')} WHERE id = ?`, params);
            res.json({ message: 'Item updated' });
        } else {
            res.status(400).json({ message: 'No fields to update' });
        }
    } catch (err) {
        res.status(500).json({ error: err.message });
    } finally {
        if (conn) conn.release();
    }
});

// Delete an item
app.delete('/items/:id', async (req, res) => {
    const { id } = req.params;
    let conn;
    try {
        conn = await pool.getConnection();
        await conn.query('DELETE FROM item WHERE id = ?', [id]);
        res.json({ message: 'Item deleted' });
    } catch (err) {
        res.status(500).json({ error: err.message });
    } finally {
        if (conn) conn.release();
    }
});

app.listen(PORT, () => {
    console.log(`Server running on port ${PORT}`);
});
