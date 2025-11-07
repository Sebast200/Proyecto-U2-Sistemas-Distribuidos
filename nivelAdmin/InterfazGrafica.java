// InterfazGrafica.java
// GUI final: detección localhost (5000–5099, 6000–6099, 7000),
// IP editable con reconexión, Mensajes separados (Reportes y Precios),
// Reportes y Historial en memoria con exportación CSV.
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
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class InterfazGrafica extends JFrame {

    // ======== Modelos y estado ========
    // nodos: Nivel | ID | IP (editable) | Puerto | Estado
    private final DefaultTableModel nodosModel;
    private final Map<String, ConexionCliente> conexiones = new ConcurrentHashMap<>();

    // Listado lateral de estado
    private final DefaultListModel<String> estadoModel = new DefaultListModel<>();
    private final JTextArea consola = new JTextArea(12, 80);

    // Detección
    private final JButton detectBtn = new JButton("🔍 Detectar nodos activos (localhost)");
    private final JProgressBar progressScan = new JProgressBar(0, 1);

    // Pestaña "Mensajes"
    private final JComboBox<String> cmbDistForReport = new JComboBox<>();
    private final JTextField txtReporte = new JTextField(36);
    private final JButton btnEnviarReporte = new JButton("📤 Enviar reporte a Empresa");

    private final JComboBox<String> cmbDistForPrice = new JComboBox<>();
    private final JTextField txtPrecio = new JTextField(36);
    private final JButton btnEnviarPrecio = new JButton("📦 Enviar precio a Distribuidor");

    // Pestaña "Reportes"
    private final DefaultTableModel reportesModel;

    // Pestaña "Historial"
    private final DefaultTableModel historialModel;

    // Identificadores por convención
    private static final String EMPRESA_ID = "EMPRESA-7000";

    public InterfazGrafica() {
        super("Panel Central – Empresa / Distribuidores / Surtidores (TCP)");

        // ===== Tabla de nodos =====
        nodosModel = new DefaultTableModel(new Object[]{"Nivel", "ID", "IP", "Puerto", "Estado"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return c == 2; } // solo IP editable
            @Override public Class<?> getColumnClass(int col) {
                return switch (col) { case 3 -> Integer.class; default -> String.class; };
            }
        };

        // Tabla de reportes (recibidos)
        reportesModel = new DefaultTableModel(new Object[]{"Fecha", "Nodo (Distribuidor)", "Tipo", "Contenido"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        // Tabla de historial (en memoria)
        historialModel = new DefaultTableModel(new Object[]{"Fecha", "Emisor", "Receptor", "Tipo", "Contenido"}, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1180, 780));
        setLocationRelativeTo(null);

        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("Configuración de Red", buildConfigPanel());
        tabs.addTab("Mensajes", buildMensajesPanel());
        tabs.addTab("Reportes", buildReportesPanel());
        tabs.addTab("Historial", buildHistorialPanel());
        setContentPane(tabs);

        // Refresco visual de estados
        new javax.swing.Timer(1000, e -> actualizarEstados()).start();

        // Listener para cambios de IP en tabla (reconectar)
        nodosModel.addTableModelListener(new TableModelListener() {
            @Override public void tableChanged(TableModelEvent e) {
                if (e.getType() == TableModelEvent.UPDATE && e.getColumn() == 2) { // IP editada
                    int row = e.getFirstRow();
                    if (row >= 0) onIpEdited(row);
                }
            }
        });

        // Inicial
        log("GUI lista. Presiona “Detectar nodos activos (localhost)” para poblar automáticamente.");
        actualizarCombosDistribuidores();
        actualizarHabilitacionesMensajes();
    }

    // ========================= Paneles =========================

    private JPanel buildConfigPanel() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        // Tabla de nodos
        JTable tabla = new JTable(nodosModel);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JScrollPane sp = new JScrollPane(tabla);
        sp.setBorder(BorderFactory.createTitledBorder("Nodos detectados (IP editable)"));
        root.add(sp, BorderLayout.CENTER);

        // Zona superior: acciones
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        progressScan.setStringPainted(true);
        progressScan.setPreferredSize(new Dimension(260, 20));
        top.add(detectBtn);
        top.add(progressScan);
        detectBtn.addActionListener(e -> detectarNodosLocalhost());
        JButton conectarTodos = new JButton("🔌 Conectar todos");
        conectarTodos.addActionListener(e -> conectarTodos());
        JButton desconectarTodos = new JButton("⛔ Desconectar todos");
        desconectarTodos.addActionListener(e -> desconectarTodos());
        top.add(conectarTodos);
        top.add(desconectarTodos);
        root.add(top, BorderLayout.NORTH);

        // Lateral: estado y consola
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT);
        JList<String> estadoList = new JList<>(estadoModel);
        estadoList.setBorder(BorderFactory.createTitledBorder("Conexiones (estado)"));
        split.setTopComponent(new JScrollPane(estadoList));

        consola.setEditable(false);
        consola.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane consolaSP = new JScrollPane(consola);
        consolaSP.setBorder(BorderFactory.createTitledBorder("Consola"));
        split.setBottomComponent(consolaSP);
        split.setResizeWeight(0.4);
        root.add(split, BorderLayout.SOUTH);

        return root;
    }

    private JPanel buildMensajesPanel() {
        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        // Sección A: Reportes (Distribuidor -> Empresa)
        JPanel panelA = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panelA.setBorder(BorderFactory.createTitledBorder("Reportes (Distribuidor → Empresa)"));
        panelA.add(new JLabel("Distribuidor:"));
        cmbDistForReport.setPreferredSize(new Dimension(260, 26));
        panelA.add(cmbDistForReport);
        panelA.add(new JLabel("Reporte:"));
        txtReporte.setPreferredSize(new Dimension(460, 26));
        panelA.add(txtReporte);
        btnEnviarReporte.addActionListener(e -> enviarReporteDistribuidorAEmpresa());
        panelA.add(btnEnviarReporte);

        // Sección B: Precios (Empresa -> Distribuidor)
        JPanel panelB = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panelB.setBorder(BorderFactory.createTitledBorder("Precios (Empresa → Distribuidor)"));
        panelB.add(new JLabel("Distribuidor:"));
        cmbDistForPrice.setPreferredSize(new Dimension(260, 26));
        panelB.add(cmbDistForPrice);
        panelB.add(new JLabel("Precio/Actualización:"));
        txtPrecio.setPreferredSize(new Dimension(460, 26));
        panelB.add(txtPrecio);
        btnEnviarPrecio.addActionListener(e -> enviarPrecioEmpresaADistribuidor());
        panelB.add(btnEnviarPrecio);

        root.add(panelA);
        root.add(Box.createVerticalStrut(12));
        root.add(panelB);

        actualizarHabilitacionesMensajes();
        return root;
    }

    private JPanel buildReportesPanel() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        JTable tabla = new JTable(reportesModel);
        JScrollPane sp = new JScrollPane(tabla);
        sp.setBorder(BorderFactory.createTitledBorder("Reportes recibidos (Distribuidores → Empresa)"));
        root.add(sp, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton exportar = new JButton("💾 Exportar reportes (CSV)");
        exportar.addActionListener(e -> exportarTablaCSV(reportesModel, "reportes_export.csv"));
        bottom.add(exportar);
        root.add(bottom, BorderLayout.SOUTH);

        return root;
    }

    private JPanel buildHistorialPanel() {
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(new EmptyBorder(12, 12, 12, 12));

        JTable tabla = new JTable(historialModel);
        JScrollPane sp = new JScrollPane(tabla);
        sp.setBorder(BorderFactory.createTitledBorder("Historial de mensajes (en memoria)"));
        root.add(sp, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton exportar = new JButton("💾 Exportar historial (CSV)");
        exportar.addActionListener(e -> exportarTablaCSV(historialModel, "historial_export.csv"));
        bottom.add(exportar);
        root.add(bottom, BorderLayout.SOUTH);

        return root;
    }

    // ===================== Detección (localhost) =====================

    private void detectarNodosLocalhost() {
        detectBtn.setEnabled(false);
        progressScan.setIndeterminate(true);
        log("[SCAN] Iniciando escaneo en localhost: Surtidores(5000–5099), Distribuidores(6000–6099), Empresa(7000)");

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
                    final int portLocal = port; // requerido para la lambda
                    if (isCancelled()) return;
                    if (existeNodoPorIpPuerto(ipLocal, portLocal)) continue;
                    try (Socket s = new Socket()) {
                        s.connect(new InetSocketAddress(ipLocal, portLocal), 200);
                        final String idLocal = generarIdPorTipo(tipoLocal, portLocal);
                        SwingUtilities.invokeLater(() -> agregarNodoDetectado(tipoLocal, idLocal, ipLocal, portLocal));
                        publish(String.format("[SCAN] Detectado %s en %s:%d", tipoLocal, ipLocal, portLocal));
                    } catch (IOException ignored) {
                        // no abierto
                    }
                }
            }

            @Override protected void process(List<String> chunks) {
                for (String msg : chunks) log(msg);
            }

            @Override protected void done() {
                progressScan.setIndeterminate(false);
                detectBtn.setEnabled(true);
                actualizarCombosDistribuidores();
                actualizarHabilitacionesMensajes();
                log("[SCAN] Escaneo completado.");
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
        actualizarCombosDistribuidores();
        actualizarHabilitacionesMensajes();
    }

    // ====================== Conexiones ======================

    private void conectarTodos() {
        if (nodosModel.getRowCount() == 0) { warn("No hay nodos detectados."); return; }
        for (int i = 0; i < nodosModel.getRowCount(); i++) {
            String nivel = (String) nodosModel.getValueAt(i, 0);
            String id = (String) nodosModel.getValueAt(i, 1);
            String ip = String.valueOf(nodosModel.getValueAt(i, 2));
            int port = (int) nodosModel.getValueAt(i, 3);
            conectarNodo(nivel, id, ip, port);
        }
        actualizarEstados();
        actualizarHabilitacionesMensajes();
    }

    private void conectarNodo(String nivel, String id, String ip, int port) {
        ConexionCliente prev = conexiones.get(id);
        if (prev != null) {
            if (prev.isConnected() && prev.matches(ip, port)) {
                log("Nodo " + id + " ya está conectado.");
                return;
            } else {
                prev.close();
            }
        }
        ConexionCliente c = new ConexionCliente(nivel, id, ip, port, this::onMensajeRecibido, this::onEstadoCambio);
        conexiones.put(id, c);
        c.start();
        log("Conectando a " + id + " (" + nivel + ") " + ip + ":" + port + " ...");
    }

    private void desconectarTodos() {
        conexiones.values().forEach(ConexionCliente::close);
        conexiones.clear();
        actualizarEstados();
        actualizarHabilitacionesMensajes();
        log("Todas las conexiones cerradas.");
    }

    private void onIpEdited(int row) {
        String nivel = (String) nodosModel.getValueAt(row, 0);
        String id = (String) nodosModel.getValueAt(row, 1);
        String ip = String.valueOf(nodosModel.getValueAt(row, 2));
        int port = (int) nodosModel.getValueAt(row, 3);

        log("IP editada para " + id + ": ahora " + ip + ":" + port + ". Reintentando conexión si aplica.");
        // Si estaba conectado, reconectar con la nueva IP
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
        SwingUtilities.invokeLater(() -> {
            actualizarEstados();
            actualizarHabilitacionesMensajes();
        });
    }

    // ====================== Mensajería ======================

    private void enviarReporteDistribuidorAEmpresa() {
        String distId = (String) cmbDistForReport.getSelectedItem();
        if (distId == null) { warn("Selecciona un distribuidor."); return; }
        ConexionCliente distConn = conexiones.get(distId);
        ConexionCliente empConn = conexiones.get(EMPRESA_ID);
        if (distConn == null || !distConn.isConnected()) { warn("El distribuidor no está conectado."); return; }
        if (empConn == null || !empConn.isConnected()) { warn("La empresa no está conectada."); return; }

        String msg = txtReporte.getText().trim();
        if (msg.isEmpty()) { warn("Escribe el reporte/comentario."); return; }
        // Enviamos desde el distribuidor a la empresa: en esta arquitectura,
        // la GUI abre sockets a ambos, pero el envío lo haremos "dirigido" al socket del distribuidor
        // suponiendo que éste redirige/actúa como emisor lógico hacia empresa.
        // Si deseas que la GUI envíe directamente al socket de empresa etiquetando distribuidor, cambia aquí.
        boolean ok = distConn.send("REPORT:" + msg);
        if (ok) {
            logTx(distId + " → " + EMPRESA_ID, "REPORT:" + msg);
            pushHistorial("TX", distId, EMPRESA_ID, "Reporte", msg);
        } else {
            error("No se pudo enviar el reporte desde " + distId);
        }
    }

    private void enviarPrecioEmpresaADistribuidor() {
        String distId = (String) cmbDistForPrice.getSelectedItem();
        if (distId == null) { warn("Selecciona un distribuidor."); return; }
        ConexionCliente distConn = conexiones.get(distId);
        ConexionCliente empConn = conexiones.get(EMPRESA_ID);
        if (empConn == null || !empConn.isConnected()) { warn("La empresa no está conectada."); return; }
        if (distConn == null || !distConn.isConnected()) { warn("El distribuidor no está conectado."); return; }

        String msg = txtPrecio.getText().trim();
        if (msg.isEmpty()) { warn("Escribe el precio/actualización."); return; }

        boolean ok = empConn.send("SET_PRICE:" + msg + "|TO:" + distId);
        if (ok) {
            logTx(EMPRESA_ID + " → " + distId, "SET_PRICE:" + msg);
            pushHistorial("TX", EMPRESA_ID, distId, "Precio", msg);
        } else {
            error("No se pudo enviar el precio desde Empresa a " + distId);
        }
    }

    private void onMensajeRecibido(String fromId, String payload) {
        logRx(fromId, payload);

        // Historial
        // No sabemos el receptor real si viene por socket "pull", así que lo marcamos desconocido o inferido.
        // Si viene un REPORT: desde un distribuidor, lo registramos hacia EMPRESA_ID.
        if (payload.startsWith("REPORT:")) {
            String contenido = payload.substring("REPORT:".length());
            pushHistorial("RX", fromId, EMPRESA_ID, "Reporte", contenido);
            // Reportes tab
            reportesModel.addRow(new Object[]{ts(), fromId, "Reporte", contenido});
        } else if (payload.startsWith("SET_PRICE")) {
            // Si recibimos confirmaciones u otros mensajes
            pushHistorial("RX", fromId, "-", "SET_PRICE", payload);
        } else {
            pushHistorial("RX", fromId, "-", "Mensaje", payload);
        }
    }

    private void actualizarCombosDistribuidores() {
        List<String> dists = new ArrayList<>();
        for (int i = 0; i < nodosModel.getRowCount(); i++) {
            String nivel = (String) nodosModel.getValueAt(i, 0);
            String id = (String) nodosModel.getValueAt(i, 1);
            if ("Distribuidor".equalsIgnoreCase(nivel)) dists.add(id);
        }
        Collections.sort(dists);
        cmbDistForReport.removeAllItems();
        cmbDistForPrice.removeAllItems();
        for (String id : dists) {
            cmbDistForReport.addItem(id);
            cmbDistForPrice.addItem(id);
        }
    }

    private void actualizarHabilitacionesMensajes() {
        boolean empresaConectada = conexiones.containsKey(EMPRESA_ID) && conexiones.get(EMPRESA_ID).isConnected();

        // Distribuidores conectados
        boolean existeDistConectado = false;
        Set<String> distConectados = new HashSet<>();
        for (Map.Entry<String, ConexionCliente> e : conexiones.entrySet()) {
            String id = e.getKey();
            if (id.startsWith("DIST-") && e.getValue().isConnected()) {
                existeDistConectado = true;
                distConectados.add(id);
            }
        }

        btnEnviarReporte.setEnabled(empresaConectada && existeDistConectado && cmbDistForReport.getItemCount() > 0);
        btnEnviarPrecio.setEnabled(empresaConectada && existeDistConectado && cmbDistForPrice.getItemCount() > 0);
    }

    // ====================== Reportes/Historial helpers ======================

    private void exportarTablaCSV(DefaultTableModel model, String defaultName) {
        if (model.getRowCount() == 0) { warn("No hay datos para exportar."); return; }
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
                        pw.print(val == null ? "" : String.valueOf(val).replace(",", " "));
                        if (c < cols - 1) pw.print(",");
                    }
                    pw.println();
                }
                info("Exportado a: " + out.getAbsolutePath());
            } catch (Exception ex) {
                error("Error exportando: " + ex.getMessage());
            }
        }
    }

    private void pushHistorial(String dir, String emisor, String receptor, String tipo, String contenido) {
        // dir: TX/RX (solo informativo), aquí guardamos fecha/emisor/receptor/tipo/contenido
        historialModel.addRow(new Object[]{ts(), emisor, receptor, tipo, contenido});
    }

    // ========================= Utilidades =========================

    private void log(String s) {
        SwingUtilities.invokeLater(() -> {
            consola.append(ts() + " " + s + "\n");
            consola.setCaretPosition(consola.getDocument().getLength());
        });
    }
    private void logRx(String from, String msg) { log("[RX][" + from + "] " + msg); }
    private void logTx(String to, String msg) { log("[TX][" + to + "] " + msg); }

    private void error(String s) {
        log("ERROR: " + s);
        JOptionPane.showMessageDialog(this, s, "Error", JOptionPane.ERROR_MESSAGE);
    }
    private void warn(String s) {
        log("WARN: " + s);
        JOptionPane.showMessageDialog(this, s, "Atención", JOptionPane.WARNING_MESSAGE);
    }
    private void info(String s) {
        log("INFO: " + s);
        JOptionPane.showMessageDialog(this, s, "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    private String ts() { return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()); }

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

        public boolean send(String msg) {
            try {
                if (out != null) { out.println(msg); return true; }
            } catch (Exception ignored) {}
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
