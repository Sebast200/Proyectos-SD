import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseManager {
    private static final String HOST = "localhost";
    private static final String PORT_WRITE = "5000"; // HAProxy Escritura
    private static final String PORT_READ = "5001";  // HAProxy Lectura
    private static final String DB_NAME = "hospital_db";
    private static final String USER = "admin";
    private static final String PASS = "adminpassword";

    static {
        try {
            Class.forName("org.postgresql.Driver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    // Usar para INSERT, UPDATE, DELETE
    public static Connection getWriteConnection() throws SQLException {
        String url = "jdbc:postgresql://" + HOST + ":" + PORT_WRITE + "/" + DB_NAME;
        return DriverManager.getConnection(url, USER, PASS);
    }

    // Usar para SELECT
    public static Connection getReadConnection() throws SQLException {
        String url = "jdbc:postgresql://" + HOST + ":" + PORT_READ + "/" + DB_NAME;
        return DriverManager.getConnection(url, USER, PASS);
    }
}