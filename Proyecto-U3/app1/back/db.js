const mariadb = require('mariadb');
require('dotenv').config();

const pool = mariadb.createPool({
  host: process.env.DB_HOST,
  port: process.env.DB_PORT || 3306,
  user: process.env.DB_USER,
  password: process.env.DB_PASSWORD,
  database: process.env.DB_NAME,
  connectionLimit: 5
});

async function initializeDB() {
  let conn;
  try {
    conn = await pool.getConnection();
    console.log('Connected to MariaDB');

    // Create tables if they don't exist
    await conn.query(`
      CREATE TABLE IF NOT EXISTS lista (
        id INT AUTO_INCREMENT PRIMARY KEY,
        name VARCHAR(255) NOT NULL
      )
    `);

    await conn.query(`
      CREATE TABLE IF NOT EXISTS item (
        id INT AUTO_INCREMENT PRIMARY KEY,
        description VARCHAR(255) NOT NULL,
        list_id INT,
        completed BOOLEAN DEFAULT FALSE,
        FOREIGN KEY (list_id) REFERENCES lista(id) ON DELETE CASCADE
      )
    `);

    console.log('Database tables initialized');
  } catch (err) {
    console.error('Error initializing database:', err);
  } finally {
    if (conn) conn.release();
  }
}

module.exports = { pool, initializeDB };
