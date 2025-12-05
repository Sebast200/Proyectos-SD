import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DatabaseManager {
    // Siempre al puerto 5000. El Vigilante decide quién está detrás.
    private static final String URL = "jdbc:postgresql://localhost:5000/hospital_db";
    private static final String USER = "admin";
    private static final String PASS = "adminpassword";

    static {
        try { Class.forName("org.postgresql.Driver"); } catch (Exception e) {}
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASS);
    }
}
