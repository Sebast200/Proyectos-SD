import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public class HospitalUI extends JFrame {
    private JTable table;
    private DefaultTableModel model;
    private JTextField txtPaciente, txtHora;

    public HospitalUI() {
        setTitle("Sistema Hospital (HAProxy: Write:5000 / Read:5001)");
        setSize(750, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 1. Inicialización: Crear tabla (Operación de Escritura)
        createTableIfNotExists();

        // 2. Panel Superior (Inputs)
        JPanel panelInput = new JPanel(new FlowLayout());
        txtPaciente = new JTextField(15);
        txtHora = new JTextField(15);
        
        JButton btnAdd = new JButton("Añadir Cita");
        JButton btnDelete = new JButton("Eliminar");
        JButton btnRefresh = new JButton("Refrescar");

        panelInput.add(new JLabel("Paciente:"));
        panelInput.add(txtPaciente);
        panelInput.add(new JLabel("Descripción:"));
        panelInput.add(txtHora);
        panelInput.add(btnAdd);
        panelInput.add(btnDelete);
        panelInput.add(btnRefresh);

        add(panelInput, BorderLayout.NORTH);

        // 3. Tabla de Datos
        model = new DefaultTableModel(new String[]{"ID", "Paciente", "Descripción", "Fecha"}, 0);
        table = new JTable(model);
        add(new JScrollPane(table), BorderLayout.CENTER);

        // 4. Eventos
        btnAdd.addActionListener(e -> addCita());
        btnRefresh.addActionListener(e -> loadCitas());
        btnDelete.addActionListener(e -> deleteCita());

        // Carga inicial (Lectura)
        loadCitas();
    }

    // --- MÉTODO DE ESCRITURA (Puerto 5000) ---
    private void createTableIfNotExists() {
        // Usamos getWriteConnection porque CREATE TABLE cambia la base de datos
        try (Connection conn = DatabaseManager.getWriteConnection();
             Statement stmt = conn.createStatement()) {
            
            String sql = "CREATE TABLE IF NOT EXISTS citas (" +
                         "id SERIAL PRIMARY KEY, " +
                         "paciente VARCHAR(100) NOT NULL, " +
                         "fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                         "descripcion TEXT)";
            stmt.executeUpdate(sql);
            System.out.println("Tabla verificada en el Maestro.");
            
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error conectando al Maestro (HAProxy 5000): " + e.getMessage());
            e.printStackTrace();
        }
    }

    // --- MÉTODO DE ESCRITURA (Puerto 5000) ---
    private void addCita() {
        try (Connection conn = DatabaseManager.getWriteConnection();
             PreparedStatement pstmt = conn.prepareStatement("INSERT INTO citas (paciente, descripcion) VALUES (?, ?)")) {
            
            pstmt.setString(1, txtPaciente.getText());
            pstmt.setString(2, txtHora.getText());
            pstmt.executeUpdate();
            
            txtPaciente.setText("");
            txtHora.setText("");
            
            // TRUCO: Esperamos 500ms para dar tiempo a que el dato viaje del Maestro a la Réplica
            Thread.sleep(500);
            
            // Recargamos (Lectura)
            loadCitas();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error al guardar: " + e.getMessage());
        }
    }

    // --- MÉTODO DE LECTURA (Puerto 5001) ---
    private void loadCitas() {
        model.setRowCount(0); // Limpiar tabla visual
        // Usamos getReadConnection para balancear carga
        try (Connection conn = DatabaseManager.getReadConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM citas ORDER BY id DESC")) {
            
            while (rs.next()) {
                model.addRow(new Object[]{
                    rs.getInt("id"), 
                    rs.getString("paciente"), 
                    rs.getString("descripcion"),
                    rs.getTimestamp("fecha")
                });
            }
            System.out.println("Datos cargados (Round-Robin).");
        } catch (SQLException e) {
            System.err.println("Error leyendo datos: " + e.getMessage());
        }
    }

    // --- MÉTODO DE ESCRITURA (Puerto 5000) ---
    private void deleteCita() {
        int row = table.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Selecciona una fila primero");
            return;
        }
        int id = (int) model.getValueAt(row, 0);

        try (Connection conn = DatabaseManager.getWriteConnection();
             PreparedStatement pstmt = conn.prepareStatement("DELETE FROM citas WHERE id = ?")) {
            
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
            
            Thread.sleep(500); // Espera de replicación
            loadCitas();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error al eliminar: " + e.getMessage());
        }
    }
}