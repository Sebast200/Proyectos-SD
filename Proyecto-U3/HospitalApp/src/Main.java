import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new HospitalUI().setVisible(true);
        });
    }
}
// Ejecutar con java -cp "bin;lib/postgresql-42.7.3.jar" Main