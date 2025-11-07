// InterfazGrafica.java
// Panel de monitoreo minimal con 2 pestañas (Configuración de Red + Reportes)
// - Detección localhost: Surtidores(5000–5099), Distribuidores(6000–6099), Empresa(7000)
// - IP editable con reconexión automática
// - SIN botón "Desconectar todos"
// - Consola: muestra EXACTAMENTE lo recibido de cada nodo (sin etiquetas ni timestamps)
// - Reportes: Tabla (Nodo | Contenido) + exportar CSV
// Compilar: javac InterfazGrafica.java
// Ejecutar:  java InterfazGrafica

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

public class InterfazGrafica extends JFrame {

    // ======== Estado / Modelos ========
    // nodos: Nivel | ID | IP(editable) | Puerto | Estado
    private final DefaultTableModel nodosModel;
    private final Map<String, ConexionCliente> conexiones = new ConcurrentHashMap<>();

    private final DefaultListModel<String> estadoModel = new DefaultListModel<>();
    private final JTextArea consola = new JTextArea(12, 80);

    // Detección
    private final JButton detectBtn = new JButton("🔍 Detectar nodos activos (localhost)");
    private final JProgressBar progressScan = new JProgressBar(0, 1);

    // Reportes (todo lo recibido)
    private final DefaultTableModel reportesModel;

    private static final String EMPRESA_ID = "EMPRESA-7000";

    public InterfazGrafica() {
        super("Panel de Monitoreo – Empresa / Distribuidores / Surtidores (TCP)");

        nodosModel = new DefaultTableModel(new Object[]{"Nivel", "ID", "IP", "Puerto", "Estado"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c == 2; } // solo IP editable
            @Override public Class<?> getColumnClass(int col) {
                return switch (col) { case 3 -> Integer.class; default -> String.class; };
            }
        };

        reportesModel = new DefaultTableModel(new Object[]{"Nodo (ID)", "Contenido"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1150, 760));
        setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Configuración de Red", buildConfigPanel());
        tabs.addTab("Reportes", buildReportesPanel());
        setContentPane(tabs);

        // Refresco visual de estados
        new javax.swing.Timer(1000, e -> actualizarEstados()).start();

        // Reconexión automática al editar IP
        nodosModel.addTableModelListener(new TableModelListener() {
            @Override public void tableChanged(TableModelEvent e) {
                if (e.getType() == TableModelEvent.UPDATE && e.getColumn() == 2) {
                    int row = e.getFirstRow();
                    if (row >= 0) onIpEdited(row);
                }
            }
        });

        logInfo("GUI lista. Presiona “Detectar nodos activos (localhost)” para poblarla automáticamente.");
    }

    // ========================= Paneles =========================

    private JPanel buildConfigPanel() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        // Zona superior: acciones
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        progressScan.setStringPainted(true);
        progressScan.setPreferredSize(new Dimension(260, 20));
        top.add(detectBtn);
        top.add(progressScan);
        detectBtn.addActionListener(e -> detectarNodosLocalhost());

        JButton conectarTodos = new JButton("🔌 Conectar todos");
        conectarTodos.addActionListener(e -> conectarTodos());
        top.add(conectarTodos);
        // (sin botón "Desconectar todos")
        root.add(top, BorderLayout.NORTH);

        // Tabla de nodos
        JTable tabla = new JTable(nodosModel);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane sp = new JScrollPane(tabla);
        sp.setBorder(BorderFactory.createTitledBorder("Nodos detectados (IP editable)"));
        root.add(sp, BorderLayout.CENTER);

        // Lateral inferior: estado + consola
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        JList<String> estadoList = new JList<>(estadoModel);
        estadoList.setBorder(BorderFactory.createTitledBorder("Conexiones (estado)"));
        split.setTopComponent(new JScrollPane(estadoList));

        consola.setEditable(false);
        consola.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane consolaSP = new JScrollPane(consola);
        consolaSP.setBorder(BorderFactory.createTitledBorder("Consola (salida literal de cada nodo)"));
        split.setBottomComponent(consolaSP);
        split.setResizeWeight(0.4);

        root.add(split, BorderLayout.SOUTH);
        return root;
    }

    private JPanel buildReportesPanel() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        JTable tabla = new JTable(reportesModel);
        JScrollPane sp = new JScrollPane(tabla);
        sp.setBorder(BorderFactory.createTitledBorder("Reportes / Mensajes recibidos"));
        root.add(sp, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton exportar = new JButton("💾 Exportar CSV");
        exportar.addActionListener(e -> exportarTablaCSV(reportesModel, "reportes_export.csv"));
        bottom.add(exportar);
        root.add(bottom, BorderLayout.SOUTH);

        return root;
    }

    // ===================== Detección (localhost) =====================

    private void detectarNodosLocalhost() {
        detectBtn.setEnabled(false);
        progressScan.setIndeterminate(true);
        logInfo("[SCAN] Iniciando escaneo: Surtidores(5000–5099), Distribuidores(6000–6099), Empresa(7000)");

        SwingWorker<Void, String> worker = new SwingWorker<>() {
            @Override protected Void doInBackground() {
                escanearRango("127.0.0.1", 5000, 5099, "Surtidor");
                escanearRango("127.0.0.1", 6000, 6099, "Distribuidor");
                escanearRango("127.0.0.1", 7000, 7000, "Empresa");
                return null;
            }

            private void escanearRango(String ip, int start, int end, String tipo) {
                final String ipLocal = ip;
                final String tipoLocal = tipo;
                for (int port = start; port <= end; port++) {
                    final int portLocal = port; // final para lambda
                    if (isCancelled()) return;
                    if (existeNodoPorIpPuerto(ipLocal, portLocal)) continue;
                    try (Socket s = new Socket()) {
                        s.connect(new InetSocketAddress(ipLocal, portLocal), 200);
                        final String idLocal = generarIdPorTipo(tipoLocal, portLocal);
                        SwingUtilities.invokeLater(() -> agregarNodoDetectado(tipoLocal, idLocal, ipLocal, portLocal));
                        publish(String.format("[SCAN] Detectado %s en %s:%d", tipoLocal, ipLocal, portLocal));
                    } catch (IOException ignored) {
                        // puerto no abierto
                    }
                }
            }

            @Override protected void process(List<String> chunks) {
                for (String msg : chunks) logInfo(msg);
            }

            @Override protected void done() {
                progressScan.setIndeterminate(false);
                detectBtn.setEnabled(true);
                actualizarEstados();
                logInfo("[SCAN] Escaneo completado.");
            }
        };
        worker.execute();
    }

    private String generarIdPorTipo(String tipo, int port) {
        return switch (tipo) {
            case "Empresa" -> EMPRESA_ID;
            case "Distribuidor" -> "DIST-" + port;
            case "Surtidor" -> "SURT-" + port;
            default -> "NODO-" + port;
        };
    }

    private void agregarNodoDetectado(String nivel, String id, String ip, int port) {
        if (existeNodo(id) || existeNodoPorIpPuerto(ip, port)) return;
        nodosModel.addRow(new Object[]{nivel, id, ip, port, "Desconectado"});
    }

    // ====================== Conexiones ======================

    private void conectarTodos() {
        if (nodosModel.getRowCount() == 0) { logWarn("No hay nodos detectados."); return; }
        for (int i = 0; i < nodosModel.getRowCount(); i++) {
            String nivel = (String) nodosModel.getValueAt(i, 0);
            String id = (String) nodosModel.getValueAt(i, 1);
            String ip = String.valueOf(nodosModel.getValueAt(i, 2));
            int port = (int) nodosModel.getValueAt(i, 3);
            conectarNodo(nivel, id, ip, port);
        }
        actualizarEstados();
    }

    private void conectarNodo(String nivel, String id, String ip, int port) {
        ConexionCliente prev = conexiones.get(id);
        if (prev != null) {
            if (prev.isConnected() && prev.matches(ip, port)) {
                logInfo("Nodo " + id + " ya está conectado.");
                return;
            } else {
                prev.close();
            }
        }
        ConexionCliente c = new ConexionCliente(nivel, id, ip, port, this::onMensajeRecibido, this::onEstadoCambio);
        conexiones.put(id, c);
        c.start();
        logInfo("Conectando a " + id + " (" + nivel + ") " + ip + ":" + port + " ...");
    }

    private void onIpEdited(int row) {
        String nivel = (String) nodosModel.getValueAt(row, 0);
        String id = (String) nodosModel.getValueAt(row, 1);
        String ip = String.valueOf(nodosModel.getValueAt(row, 2));
        int port = (int) nodosModel.getValueAt(row, 3);

        logInfo("IP editada para " + id + ": ahora " + ip + ":" + port + ". Reintentando si estaba conectado.");
        ConexionCliente existing = conexiones.get(id);
        boolean wasConnected = existing != null && existing.isConnected();
        if (existing != null) existing.close();
        if (wasConnected) {
            conectarNodo(nivel, id, ip, port);
        }
    }

    private void actualizarEstados() {
        estadoModel.clear();
        for (int i = 0; i < nodosModel.getRowCount(); i++) {
            String nivel = (String) nodosModel.getValueAt(i, 0);
            String id = (String) nodosModel.getValueAt(i, 1);
            String ip = String.valueOf(nodosModel.getValueAt(i, 2));
            int port = (int) nodosModel.getValueAt(i, 3);
            boolean ok = conexiones.containsKey(id) && conexiones.get(id).isConnected();
            estadoModel.addElement(String.format("%s [%s] %s (%s:%d)",
                    ok ? "🟢" : "🔴", nivel, id, ip, port));
            nodosModel.setValueAt(ok ? "Conectado" : "Desconectado", i, 4);
        }
    }

    private void onEstadoCambio(String id) {
        SwingUtilities.invokeLater(this::actualizarEstados);
    }

    // ====================== Recepción / Consola / Reportes ======================

    private void onMensajeRecibido(String fromId, String payload) {
        // Consola: mostrar exactamente como llega (sin etiquetas, sin timestamps)
        logRaw(payload);
        // Tabla de reportes: guardar literal
        reportesModel.addRow(new Object[]{fromId, payload});
    }

    // ========================= Logging (sin timestamps/etiquetas) =========================

    private void logRaw(String s) {
        SwingUtilities.invokeLater(() -> {
            consola.append(s + "\n");
            consola.setCaretPosition(consola.getDocument().getLength());
        });
    }
    private void logInfo(String s) { logRaw("[INFO] " + s); }
    private void logWarn(String s) { logRaw("[WARN] " + s); }

    // ========================= Utilidades =========================

    private void exportarTablaCSV(DefaultTableModel model, String defaultName) {
        if (model.getRowCount() == 0) { logWarn("No hay datos para exportar."); return; }
        JFileChooser fc = new JFileChooser(".");
        fc.setSelectedFile(new File(defaultName));
        if (fc.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            File out = fc.getSelectedFile();
            try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(out), StandardCharsets.UTF_8))) {
                // encabezado
                int cols = model.getColumnCount();
                for (int c = 0; c < cols; c++) {
                    pw.print(model.getColumnName(c));
                    if (c < cols - 1) pw.print(",");
                }
                pw.println();
                // filas
                for (int r = 0; r < model.getRowCount(); r++) {
                    for (int c = 0; c < cols; c++) {
                        Object val = model.getValueAt(r, c);
                        String s = (val == null) ? "" : String.valueOf(val);
                        s = s.replace("\n", " ").replace("\r", " ").replace(",", " ");
                        pw.print(s);
                        if (c < cols - 1) pw.print(",");
                    }
                    pw.println();
                }
                logInfo("Exportado a: " + out.getAbsolutePath());
            } catch (Exception ex) {
                logWarn("Error exportando: " + ex.getMessage());
            }
        }
    }

    private boolean existeNodo(String id) {
        for (int i = 0; i < nodosModel.getRowCount(); i++) {
            if (id.equals(nodosModel.getValueAt(i, 1))) return true;
        }
        return false;
    }
    private boolean existeNodoPorIpPuerto(String ip, int port) {
        for (int i = 0; i < nodosModel.getRowCount(); i++) {
            String ipRow = String.valueOf(nodosModel.getValueAt(i, 2));
            int pRow = (int) nodosModel.getValueAt(i, 3);
            if (ip.equals(ipRow) && port == pRow) return true;
        }
        return false;
    }

    // ===================== Conexión TCP =====================

    static class ConexionCliente {
        private final String nivel, id;
        private volatile String ip;
        private volatile int port;

        private final Consumer2<String, String> onMessage;
        private final Consumer1<String> onState;

        private volatile boolean running = true;
        private volatile boolean connected = false;
        private Socket socket;
        private BufferedReader in;
        private PrintWriter out;

        public ConexionCliente(String nivel, String id, String ip, int port,
                               Consumer2<String, String> onMessage,
                               Consumer1<String> onState) {
            this.nivel = nivel; this.id = id; this.ip = ip; this.port = port;
            this.onMessage = onMessage; this.onState = onState;
        }

        public void start() { new Thread(this::loop, "Conn-" + id).start(); }

        private void loop() {
            while (running) {
                try {
                    socket = new Socket();
                    socket.connect(new InetSocketAddress(ip, port), 4000);
                    socket.setTcpNoDelay(true);
                    in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                    out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
                    connected = true;
                    if (onState != null) onState.accept(id);

                    String line;
                    while (running && (line = in.readLine()) != null) {
                        if (onMessage != null) onMessage.accept(id, line);
                    }
                } catch (IOException ex) {
                    connected = false;
                    if (onState != null) onState.accept(id);
                    try { Thread.sleep(1200); } catch (InterruptedException ignored) {}
                } finally {
                    closeQuiet();
                }
            }
        }

        public boolean matches(String ip, int port) { return Objects.equals(this.ip, ip) && this.port == port; }
        public boolean isConnected() { return connected; }

        // Esta GUI no envía mensajes; solo escucha.
        public boolean send(String msg) {
            try { if (out != null) { out.println(msg); return true; } }
            catch (Exception ignored) {}
            return false;
        }

        public void close() {
            running = false;
            connected = false;
            closeQuiet();
            if (onState != null) onState.accept(id);
        }

        private void closeQuiet() {
            try { if (out != null) out.flush(); } catch (Exception ignored) {}
            try { if (in != null) in.close(); } catch (Exception ignored) {}
            try { if (out != null) out.close(); } catch (Exception ignored) {}
            try { if (socket != null) socket.close(); } catch (Exception ignored) {}
        }
    }

    // ===================== Functional mini-interfaces =====================

    interface Consumer1<A> { void accept(A a); }
    interface Consumer2<A, B> { void accept(A a, B b); }

    // =============================== MAIN ===============================

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()); } catch (Exception ignored) {}
            new InterfazGrafica().setVisible(true);
        });
    }
}
