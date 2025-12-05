import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;

public class HospitalUI extends JFrame {
    private JTable table;
    private DefaultTableModel model;
    private JTextField txtPaciente, txtHora;

    public HospitalUI() {
        setTitle("Sistema Hospital (Orquestación Automática - Puerto 5000)");
        setSize(750, 500);
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // 1. Inicialización: Crear tabla si no existe
        // Ahora usamos la conexión genérica, el Vigilante dirige al Maestro
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

        // Carga inicial
        loadCitas();
    }

    // --- CREAR TABLA ---
    private void createTableIfNotExists() {
        // Usamos DatabaseManager.getConnection() para TODO
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            
            String sql = "CREATE TABLE IF NOT EXISTS citas (" +
                         "id SERIAL PRIMARY KEY, " +
                         "paciente VARCHAR(100) NOT NULL, " +
                         "fecha TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                         "descripcion TEXT)";
            stmt.executeUpdate(sql);
            System.out.println("Verificación de tabla completada.");
            
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error conectando al Cluster: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // --- AÑADIR (INSERT) ---
    private void addCita() {
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("INSERT INTO citas (paciente, descripcion) VALUES (?, ?)")) {
            
            pstmt.setString(1, txtPaciente.getText());
            pstmt.setString(2, txtHora.getText());
            pstmt.executeUpdate();
            
            txtPaciente.setText("");
            txtHora.setText("");
            
            // Recargamos inmediatamente
            loadCitas();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error al guardar (Si hubo failover, reintenta en 5s): " + e.getMessage());
        }
    }

    // --- LEER (SELECT) ---
    private void loadCitas() {
        model.setRowCount(0); // Limpiar tabla visual
        try (Connection conn = DatabaseManager.getConnection();
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
            System.out.println("Datos cargados desde el Maestro actual.");
        } catch (SQLException e) {
            System.err.println("Error leyendo datos: " + e.getMessage());
        }
    }

    // --- BORRAR (DELETE) ---
    private void deleteCita() {
        int row = table.getSelectedRow();
        if (row == -1) {
            JOptionPane.showMessageDialog(this, "Selecciona una fila primero");
            return;
        }
        int id = (int) model.getValueAt(row, 0);

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement("DELETE FROM citas WHERE id = ?")) {
            
            pstmt.setInt(1, id);
            pstmt.executeUpdate();
            
            loadCitas();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error al eliminar: " + e.getMessage());
        }
    }
}
