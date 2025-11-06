import java.io.*;
import java.net.*;
import java.sql.*;
import java.util.*;

public class Distribuidor {
    private String nombreDistribuidor;
    private Map<String, SurtidorConectado> surtidores;
    private Map<String, Double> preciosCombustibles; // Precios centralizados
    private static final int PUERTO_SURTIDORES = 6000; // Puerto donde escucha el distribuidor
    private static final String DB_PATH = "/app/data/distribuidor.db";
    private Connection dbConnection;
    
    public Distribuidor(String nombre) {
        this.nombreDistribuidor = nombre;
        this.surtidores = new HashMap<>();
        this.preciosCombustibles = new HashMap<>();
        inicializarBaseDatos();
        inicializarPrecios();
        cargarPrecios();
    }
    
    private void inicializarBaseDatos() {
        try {
            // Crear directorio si no existe
            File dbFile = new File(DB_PATH);
            File parentDir = dbFile.getParentFile();
            if (!parentDir.exists()) {
                parentDir.mkdirs();
            }
            
            // Cargar el driver JDBC de SQLite explícitamente
            Class.forName("org.sqlite.JDBC");
            
            // Conectar a la base de datos (se crea si no existe)
            dbConnection = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
            
            // Crear tablas si no existen
            String createTransaccionesSQL = """
                CREATE TABLE IF NOT EXISTS transacciones (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    surtidor_id TEXT NOT NULL,
                    tipo_combustible TEXT NOT NULL,
                    litros_consumidos REAL NOT NULL,
                    cantidad_cargas INTEGER NOT NULL,
                    fecha_actualizacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(surtidor_id, tipo_combustible)
                )
            """;
            
            String createPreciosSQL = """
                CREATE TABLE IF NOT EXISTS precios_combustibles (
                    tipo_combustible TEXT PRIMARY KEY,
                    precio_actual REAL NOT NULL,
                    fecha_actualizacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP)
            """;
            
            Statement stmt = dbConnection.createStatement();
            stmt.execute(createTransaccionesSQL);
            stmt.execute(createPreciosSQL);
            
            // Insertar precios por defecto si la tabla está vacía
            String checkPreciosSQL = "SELECT COUNT(*) FROM precios_combustibles";
            var rs = stmt.executeQuery(checkPreciosSQL);
            if (rs.next() && rs.getInt(1) == 0) {
                String insertPreciosSQL = """
                    INSERT INTO precios_combustibles (tipo_combustible, precio_actual) VALUES
                    ('93', 100.0),
                    ('95', 100.0),
                    ('97', 100.0),
                    ('Diesel', 100.0),
                    ('Kerosene', 100.0)
                """;
                stmt.execute(insertPreciosSQL);
                System.out.println("[DB] ✓ Precios por defecto insertados");
            }
            rs.close();
            stmt.close();
            
            System.out.println("[DB] ✓ Base de datos inicializada: " + DB_PATH);
            
        } catch (ClassNotFoundException e) {
            System.err.println("[DB ERROR] Driver SQLite no encontrado: " + e.getMessage());
            System.err.println("[DB ERROR] Classpath: " + System.getProperty("java.class.path"));
            dbConnection = null;
        } catch (SQLException e) {
            System.err.println("[DB ERROR] Error al inicializar BD: " + e.getMessage());
            e.printStackTrace();
            dbConnection = null;
        } catch (Exception e) {
            System.err.println("[DB ERROR] Error inesperado: " + e.getMessage());
            e.printStackTrace();
            dbConnection = null;
        }
    }
    
    private void inicializarPrecios() {
        // Precios por defecto
        preciosCombustibles.put("93", 100.0);
        preciosCombustibles.put("95", 100.0);
        preciosCombustibles.put("97", 100.0);
        preciosCombustibles.put("Diesel", 100.0);
        preciosCombustibles.put("Kerosene", 100.0);
    }
    
    private void cargarPrecios() {
        // Cargar precios desde la base de datos
        if (dbConnection == null) {
            System.err.println("[ERROR] No hay conexión a BD, usando precios por defecto");
            return;
        }
        
        try {
            String sql = "SELECT tipo_combustible, precio_actual FROM precios_combustibles";
            Statement stmt = dbConnection.createStatement();
            ResultSet rs = stmt.executeQuery(sql);
            
            int count = 0;
            while (rs.next()) {
                String tipo = rs.getString("tipo_combustible");
                double precio = rs.getDouble("precio_actual");
                preciosCombustibles.put(tipo, precio);
                count++;
            }
            
            rs.close();
            stmt.close();
            
            System.out.println("[DB] ✓ " + count + " precios cargados desde base de datos");
            mostrarPrecios();
            
        } catch (SQLException e) {
            System.err.println("[ERROR] Error al cargar precios desde BD: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Guardar UN precio específico en la base de datos
    private void guardarPrecio(String tipoCombustible, double precio) {
        if (dbConnection == null) {
            System.err.println("[ERROR] No hay conexión a BD, no se puede guardar precio");
            return;
        }
        
        try {
            String sql = "UPDATE precios_combustibles SET precio_actual = ?, fecha_actualizacion = CURRENT_TIMESTAMP WHERE tipo_combustible = ?";
            PreparedStatement pstmt = dbConnection.prepareStatement(sql);
            
            pstmt.setDouble(1, precio);
            pstmt.setString(2, tipoCombustible);
            int rows = pstmt.executeUpdate();
            pstmt.close();
            
            if (rows > 0) {
                System.out.println("[DB] ✓ Precio de " + tipoCombustible + " actualizado en BD");
            } else {
                System.err.println("[DB] ⚠ No se encontró el combustible " + tipoCombustible);
            }
            
        } catch (SQLException e) {
            System.err.println("[ERROR] Error al guardar precio en BD: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    // Guardar TODOS los precios en la base de datos (usado solo para operaciones masivas/mantenimiento)
    @SuppressWarnings("unused")
    private void guardarPrecios() {
        if (dbConnection == null) {
            System.err.println("[ERROR] No hay conexión a BD, no se pueden guardar precios");
            return;
        }
        
        try {
            String sql = "UPDATE precios_combustibles SET precio_actual = ?, fecha_actualizacion = CURRENT_TIMESTAMP WHERE tipo_combustible = ?";
            PreparedStatement pstmt = dbConnection.prepareStatement(sql);
            
            int count = 0;
            for (Map.Entry<String, Double> entry : preciosCombustibles.entrySet()) {
                pstmt.setDouble(1, entry.getValue());
                pstmt.setString(2, entry.getKey());
                pstmt.executeUpdate();
                count++;
            }
            
            pstmt.close();
            System.out.println("[DB] ✓ " + count + " precios actualizados en base de datos");
            
        } catch (SQLException e) {
            System.err.println("[ERROR] Error al guardar precios en BD: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void mostrarPrecios() {
        System.out.println("\n=== PRECIOS ACTUALES ===");
        for (Map.Entry<String, Double> entry : preciosCombustibles.entrySet()) {
            System.out.printf("  %s: $%.2f%n", entry.getKey(), entry.getValue());
        }
        System.out.println("========================\n");
    }
    
    private void enviarPreciosASurtidor(SurtidorConectado surtidor) {
        System.out.println("[SYNC] Enviando precios al surtidor " + surtidor.getId());
        for (Map.Entry<String, Double> entry : preciosCombustibles.entrySet()) {
            surtidor.enviarComando("PRECIO " + entry.getKey() + " " + entry.getValue());
        }
    }
    
    // Métodos de gestión de base de datos
    
    public void registrarTransaccion(String surtidorId, String tipoCombustible, double litrosConsumidos) {
        if (dbConnection == null) {
            System.err.println("[ERROR] Base de datos no inicializada. No se puede registrar transacción.");
            return;
        }
        
        try {
            // Verificar si ya existe un registro para este surtidor y tipo de combustible
            String selectSQL = "SELECT cantidad_cargas, litros_consumidos FROM transacciones WHERE surtidor_id = ? AND tipo_combustible = ?";
            PreparedStatement selectStmt = dbConnection.prepareStatement(selectSQL);
            selectStmt.setString(1, surtidorId);
            selectStmt.setString(2, tipoCombustible);
            ResultSet rs = selectStmt.executeQuery();
            
            if (rs.next()) {
                // Actualizar registro existente
                int cantidadCargasActual = rs.getInt("cantidad_cargas");
                double litrosActuales = rs.getDouble("litros_consumidos");
                
                String updateSQL = "UPDATE transacciones SET litros_consumidos = ?, cantidad_cargas = ?, fecha_actualizacion = CURRENT_TIMESTAMP WHERE surtidor_id = ? AND tipo_combustible = ?";
                PreparedStatement updateStmt = dbConnection.prepareStatement(updateSQL);
                updateStmt.setDouble(1, litrosActuales + litrosConsumidos);
                updateStmt.setInt(2, cantidadCargasActual + 1);
                updateStmt.setString(3, surtidorId);
                updateStmt.setString(4, tipoCombustible);
                updateStmt.executeUpdate();
                updateStmt.close();
                
                System.out.println("[DB] Transacción actualizada: Surtidor " + surtidorId + ", " + tipoCombustible + ", +" + litrosConsumidos + "L");
            } else {
                // Insertar nuevo registro
                String insertSQL = "INSERT INTO transacciones (surtidor_id, tipo_combustible, litros_consumidos, cantidad_cargas) VALUES (?, ?, ?, ?)";
                PreparedStatement insertStmt = dbConnection.prepareStatement(insertSQL);
                insertStmt.setString(1, surtidorId);
                insertStmt.setString(2, tipoCombustible);
                insertStmt.setDouble(3, litrosConsumidos);
                insertStmt.setInt(4, 1);
                insertStmt.executeUpdate();
                insertStmt.close();
                
                System.out.println("[DB] Nueva transacción registrada: Surtidor " + surtidorId + ", " + tipoCombustible + ", " + litrosConsumidos + "L");
            }
            
            rs.close();
            selectStmt.close();
            
        } catch (SQLException e) {
            System.err.println("[ERROR] Error al registrar transacción: " + e.getMessage());
        }
    }
    
    public void mostrarTransacciones() {
        if (dbConnection == null) {
            System.err.println("\n[ERROR] Base de datos no disponible.");
            System.err.println("No se pueden mostrar transacciones sin conexión a la BD.\n");
            return;
        }
        
        try {
            String query = "SELECT surtidor_id, tipo_combustible, litros_consumidos, cantidad_cargas, fecha_actualizacion FROM transacciones ORDER BY surtidor_id, tipo_combustible";
            Statement stmt = dbConnection.createStatement();
            ResultSet rs = stmt.executeQuery(query);
            
            System.out.println("\n╔═══════════════════════════════════════════════════════════════════╗");
            System.out.println("║          REPORTE DE TRANSACCIONES - " + nombreDistribuidor + "          ║");
            System.out.println("╚═══════════════════════════════════════════════════════════════════╝");
            
            String surtidorActual = "";
            boolean hayDatos = false;
            
            while (rs.next()) {
                hayDatos = true;
                String surtidorId = rs.getString("surtidor_id");
                String tipoCombustible = rs.getString("tipo_combustible");
                double litros = rs.getDouble("litros_consumidos");
                int cargas = rs.getInt("cantidad_cargas");
                String fecha = rs.getString("fecha_actualizacion");
                
                if (!surtidorId.equals(surtidorActual)) {
                    if (!surtidorActual.isEmpty()) {
                        System.out.println("  " + "─".repeat(65));
                    }
                    System.out.println("\n  SURTIDOR: " + surtidorId);
                    surtidorActual = surtidorId;
                }
                
                System.out.printf("    %-12s | Litros: %10.2f | Cargas: %5d | Última: %s%n", 
                    tipoCombustible, litros, cargas, fecha);
            }
            
            if (!hayDatos) {
                System.out.println("\n  No hay transacciones registradas");
            }
            
            System.out.println("\n" + "═".repeat(70) + "\n");
            
            rs.close();
            stmt.close();
            
        } catch (SQLException e) {
            System.err.println("[ERROR] Error al mostrar transacciones: " + e.getMessage());
        }
    }
    
    public void mostrarResumenPorSurtidor(String surtidorId) {
        if (dbConnection == null) {
            System.err.println("\n[ERROR] Base de datos no disponible.");
            System.err.println("No se puede mostrar resumen sin conexión a la BD.\n");
            return;
        }
        
        try {
            String query = "SELECT tipo_combustible, litros_consumidos, cantidad_cargas FROM transacciones WHERE surtidor_id = ? ORDER BY tipo_combustible";
            PreparedStatement stmt = dbConnection.prepareStatement(query);
            stmt.setString(1, surtidorId);
            ResultSet rs = stmt.executeQuery();
            
            System.out.println("\n=== RESUMEN SURTIDOR " + surtidorId + " ===");
            
            double totalLitros = 0;
            int totalCargas = 0;
            boolean hayDatos = false;
            
            while (rs.next()) {
                hayDatos = true;
                String tipo = rs.getString("tipo_combustible");
                double litros = rs.getDouble("litros_consumidos");
                int cargas = rs.getInt("cantidad_cargas");
                
                System.out.printf("  %-12s: %10.2f litros, %5d cargas%n", tipo, litros, cargas);
                totalLitros += litros;
                totalCargas += cargas;
            }
            
            if (!hayDatos) {
                System.out.println("  No hay datos para este surtidor");
            } else {
                System.out.println("  " + "─".repeat(40));
                System.out.printf("  TOTAL       : %10.2f litros, %5d cargas%n", totalLitros, totalCargas);
            }
            
            System.out.println("================================\n");
            
            rs.close();
            stmt.close();
            
        } catch (SQLException e) {
            System.err.println("[ERROR] Error al mostrar resumen: " + e.getMessage());
        }
    }
    
    private void conectarAEmpresa(String host, int puerto) {
        new Thread(() -> {
            while (true) {
                try {
                    Socket socket = new Socket(host, puerto);
                    BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter salida = new PrintWriter(socket.getOutputStream(), true);
                    
                    System.out.println("[EMPRESA] Conectado a " + host + ":" + puerto);
                    
                    // Identificarse ante la empresa
                    String comando = entrada.readLine();
                    if ("IDENTIFICAR".equals(comando)) {
                        salida.println("ID:" + nombreDistribuidor);
                        String respuesta = entrada.readLine();
                        System.out.println("[EMPRESA] " + respuesta);
                    }
                    
                    // Escuchar comandos de la empresa
                    String mensaje;
                    while ((mensaje = entrada.readLine()) != null) {
                        if (mensaje.startsWith("PRECIO ")) {
                            String[] partes = mensaje.split(" ");
                            if (partes.length == 3) {
                                String tipo = partes[1];
                                try {
                                    double precio = Double.parseDouble(partes[2]);
                                    
                                    // Actualizar precio local y guardar en BD
                                    preciosCombustibles.put(tipo, precio);
                                    guardarPrecio(tipo, precio);  // Solo actualizar este precio
                                    
                                    System.out.println("[EMPRESA→PRECIOS] " + tipo + " actualizado a $" + precio);
                                    
                                    // Propagar a todos los surtidores conectados
                                    int surtidoresConectados = surtidores.size();
                                    for (Map.Entry<String, SurtidorConectado> entry : surtidores.entrySet()) {
                                        entry.getValue().enviarComando("PRECIO " + tipo + " " + precio);
                                    }
                                    
                                    if (surtidoresConectados > 0) {
                                        System.out.println("[SYNC] Precio propagado a " + surtidoresConectados + " surtidores conectados");
                                    } else {
                                        System.out.println("[SYNC] Precio guardado (sin surtidores conectados actualmente)");
                                    }
                                    
                                } catch (NumberFormatException e) {
                                    System.err.println("[ERROR] Precio inválido recibido de empresa");
                                }
                            }
                        }
                    }
                    
                    System.out.println("[EMPRESA] Conexión cerrada");
                    socket.close();
                    
                } catch (IOException e) {
                    System.err.println("[EMPRESA] Error de conexión: " + e.getMessage());
                }
                
                // Reintentar conexión cada 30 segundos
                try {
                    Thread.sleep(30000);
                } catch (InterruptedException ignored) {}
            }
        }).start();
    }
    
    public static void main(String[] args) {
        System.out.print("Ingrese identificador del distribuidor (ej: Distribuidor_Norte): ");
        Scanner sc = new Scanner(System.in);
        String nombre = sc.nextLine();
        
        Distribuidor distribuidor = new Distribuidor(nombre);
        
        System.out.println("\n╔═══════════════════════════════════════════════╗");
        System.out.println("║  DISTRIBUIDOR " + nombre + " - Sistema de Gestión  ║");
        System.out.println("╚═══════════════════════════════════════════════╝\n");
        
        // Iniciar servidor para recibir surtidores
        new Thread(() -> distribuidor.iniciarServidorSurtidores()).start();
        
        // Conectar automáticamente a la Empresa (Nivel 3)
        String empresaHost = System.getenv().getOrDefault("EMPRESA_HOST", "empresa");
        int empresaPuerto = Integer.parseInt(System.getenv().getOrDefault("EMPRESA_PORT", "7000"));
        System.out.println("\n[EMPRESA] Conectando a " + empresaHost + ":" + empresaPuerto + "...");
        distribuidor.conectarAEmpresa(empresaHost, empresaPuerto);
        
        // Menú de comandos
        distribuidor.menuPrincipal(sc);
    }
    
    private void iniciarServidorSurtidores() {
        try (ServerSocket servidor = new ServerSocket(PUERTO_SURTIDORES)) {
            System.out.println("[SERVIDOR] Escuchando conexiones de surtidores en puerto " + PUERTO_SURTIDORES);
            
            while (true) {
                Socket socket = servidor.accept();
                System.out.println("[CONEXIÓN] Nuevo surtidor conectado desde: " + socket.getInetAddress());
                
                // Crear hilo para manejar este surtidor
                new Thread(new ManejadorSurtidor(socket, this)).start();
            }
        } catch (IOException e) {
            System.err.println("[ERROR] Error en servidor de surtidores: " + e.getMessage());
        }
    }
    
    public synchronized void registrarSurtidor(String id, SurtidorConectado surtidor) {
        surtidores.put(id, surtidor);
        System.out.println("[REGISTRO] Surtidor " + id + " registrado. Total surtidores: " + surtidores.size());
    }
    
    public synchronized void desregistrarSurtidor(String id) {
        surtidores.remove(id);
        System.out.println("[DESCONEXIÓN] Surtidor " + id + " desconectado. Total surtidores: " + surtidores.size());
    }
    
    private void menuPrincipal(Scanner sc) {
        while (true) {
            System.out.println("\n=== MENÚ DEL DISTRIBUIDOR ===");
            System.out.println("1. Listar surtidores conectados");
            System.out.println("2. Actualizar precios en todos los surtidores");
            System.out.println("3. Consultar estado de un surtidor");
            System.out.println("4. Ver precios actuales");
            System.out.println("5. Gestionar estanque (REPONER/CONSULTAR/ESTADO)");
            System.out.println("6. Ver todas las transacciones");
            System.out.println("7. Ver transacciones de un surtidor");
            System.out.println("8. Salir");
            System.out.print("\nSeleccione opción: ");
            
            String opcion = sc.nextLine();
            
            switch (opcion) {
                case "1":
                    listarSurtidores();
                    break;
                case "2":
                    actualizarTodosLosPrecios(sc);
                    break;
                case "3":
                    consultarSurtidor(sc);
                    break;
                case "4":
                    mostrarPrecios();
                    break;
                case "5":
                    gestionarEstanque(sc);
                    break;
                case "6":
                    mostrarTransacciones();
                    break;
                case "7":
                    consultarTransaccionesSurtidor(sc);
                    break;
                case "8":
                    System.out.println("Saliendo...");
                    cerrarBaseDatos();
                    System.exit(0);
                    break;
                default:
                    System.out.println("Opción inválida");
            }
        }
    }
    
    private void consultarTransaccionesSurtidor(Scanner sc) {
        System.out.print("\nIngrese ID del surtidor: ");
        String id = sc.nextLine();
        mostrarResumenPorSurtidor(id);
    }
    
    private void cerrarBaseDatos() {
        try {
            if (dbConnection != null && !dbConnection.isClosed()) {
                dbConnection.close();
                System.out.println("[DB] Conexión cerrada");
            }
        } catch (SQLException e) {
            System.err.println("[ERROR] Error al cerrar base de datos: " + e.getMessage());
        }
    }
    
    private void listarSurtidores() {
        System.out.println("\n=== SURTIDORES CONECTADOS ===");
        if (surtidores.isEmpty()) {
            System.out.println("No hay surtidores conectados");
        } else {
            for (String id : surtidores.keySet()) {
                System.out.println("  - " + id);
            }
        }
        System.out.println("Total: " + surtidores.size() + " surtidores");
    }
    
    private void actualizarTodosLosPrecios(Scanner sc) {
        mostrarPrecios();
        
        System.out.print("Tipo de combustible (93, 95, 97, Diesel, Kerosene): ");
        String tipo = sc.nextLine();
        
        if (!preciosCombustibles.containsKey(tipo)) {
            System.out.println("Tipo de combustible no válido");
            return;
        }
        
        System.out.print("Nuevo precio: ");
        try {
            double precio = Double.parseDouble(sc.nextLine());
            
            // Actualizar precio en memoria y guardar en BD
            preciosCombustibles.put(tipo, precio);
            guardarPrecio(tipo, precio);  // Solo actualizar este precio
            
            // Enviar a todos los surtidores conectados
            int enviados = 0;
            for (Map.Entry<String, SurtidorConectado> entry : surtidores.entrySet()) {
                entry.getValue().enviarComando("PRECIO " + tipo + " " + precio);
                enviados++;
            }
            
            if (enviados > 0) {
                System.out.println("✓ Precio propagado a " + enviados + " surtidores conectados");
            } else {
                System.out.println("⚠ Sin surtidores conectados (recibirán el precio al conectarse)");
            }
        } catch (NumberFormatException e) {
            System.out.println("Precio inválido");
        }
    }
    
    private void consultarSurtidor(Scanner sc) {
        if (surtidores.isEmpty()) {
            System.out.println("No hay surtidores conectados");
            return;
        }
        
        listarSurtidores();
        System.out.print("\nIngrese ID del surtidor: ");
        String id = sc.nextLine();
        
        SurtidorConectado surtidor = surtidores.get(id);
        if (surtidor == null) {
            System.out.println("Surtidor no encontrado");
            return;
        }
        
        surtidor.enviarComando("ESTADO_SURTIDOR");
        System.out.println("Solicitud enviada al surtidor " + id);
    }
    
    private void gestionarEstanque(Scanner sc) {
        String estanqueHost = System.getenv().getOrDefault("ESTANQUE_HOST", "servidor");
        int estanquePuerto = 5000;
        
        System.out.println("\n=== GESTIÓN DE ESTANQUE ===");
        System.out.println("1. Reponer combustible");
        System.out.println("2. Consultar nivel de combustible");
        System.out.println("3. Ver estado completo del estanque");
        System.out.println("4. Volver al menú principal");
        System.out.print("\nSeleccione opción: ");
        
        String opcion = sc.nextLine();
        
        try (Socket socket = new Socket(estanqueHost, estanquePuerto);
             BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter salida = new PrintWriter(socket.getOutputStream(), true)) {
            
            // Leer y descartar mensaje de bienvenida (no mostrar al usuario)
            String bienvenida;
            while ((bienvenida = entrada.readLine()) != null) {
                if (bienvenida.contains("Tipos:") || bienvenida.contains("Kerosene")) break;
            }
            
            switch (opcion) {
                case "1": // Reponer
                    System.out.print("Tipo de combustible (93, 95, 97, Diesel, Kerosene): ");
                    String tipoReponer = sc.nextLine();
                    System.out.print("Cantidad de litros a reponer: ");
                    String litrosReponer = sc.nextLine();
                    
                    salida.println("REPONER " + tipoReponer + " " + litrosReponer);
                    String respuestaReponer = entrada.readLine();
                    System.out.println("[ESTANQUE] " + respuestaReponer);
                    break;
                    
                case "2": // Consultar
                    System.out.print("Tipo de combustible (93, 95, 97, Diesel, Kerosene): ");
                    String tipoConsultar = sc.nextLine();
                    
                    salida.println("CONSULTAR " + tipoConsultar);
                    String respuestaConsultar = entrada.readLine();
                    System.out.println("[ESTANQUE] " + respuestaConsultar);
                    break;
                    
                case "3": // Estado completo
                    salida.println("ESTADO");
                    System.out.println("\n[ESTANQUE] Estado actual:");
                    String lineaEstado;
                    while ((lineaEstado = entrada.readLine()) != null) {
                        System.out.println(lineaEstado);
                        if (lineaEstado.contains("====")) break;
                    }
                    break;
                    
                case "4": // Volver
                    System.out.println("Volviendo al menú principal...");
                    break;
                    
                default:
                    System.out.println("Opción inválida");
            }
            
            salida.println("SALIR");
            
        } catch (IOException e) {
            System.err.println("[ERROR] No se pudo conectar al estanque: " + e.getMessage());
        }
    }
    
    // Clase interna para representar un surtidor conectado
    static class SurtidorConectado {
        private String id;
        private PrintWriter salida;
        
        public SurtidorConectado(String id, PrintWriter salida) {
            this.id = id;
            this.salida = salida;
        }
        
        public void enviarComando(String comando) {
            salida.println(comando);
        }
        
        public String getId() {
            return id;
        }
    }
    
    // Manejador de conexiones de surtidores
    static class ManejadorSurtidor implements Runnable {
        private Socket socket;
        private Distribuidor distribuidor;
        
        public ManejadorSurtidor(Socket socket, Distribuidor distribuidor) {
            this.socket = socket;
            this.distribuidor = distribuidor;
        }
        
        @Override
        public void run() {
            try (
                BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter salida = new PrintWriter(socket.getOutputStream(), true)
            ) {
                // Solicitar ID del surtidor
                salida.println("IDENTIFICAR");
                String respuesta = entrada.readLine();
                
                if (respuesta == null || !respuesta.startsWith("ID:")) {
                    System.err.println("[ERROR] Surtidor no se identificó correctamente");
                    return;
                }
                
                String idSurtidor = respuesta.substring(3).trim();
                
                // Registrar surtidor
                SurtidorConectado surtidor = new SurtidorConectado(idSurtidor, salida);
                distribuidor.registrarSurtidor(idSurtidor, surtidor);
                
                salida.println("OK: Conectado al distribuidor " + distribuidor.nombreDistribuidor);
                
                // Enviar precios actuales al surtidor recién conectado
                distribuidor.enviarPreciosASurtidor(surtidor);
                
                // Escuchar mensajes del surtidor
                String mensaje;
                while ((mensaje = entrada.readLine()) != null) {
                    if (mensaje.equalsIgnoreCase("SALIR")) {
                        break;
                    }
                    
                    // Procesar comando TRANSACCION
                    if (mensaje.startsWith("TRANSACCION ")) {
                        String[] partes = mensaje.split(" ");
                        if (partes.length == 4) {
                            try {
                                String surtidorId = partes[1];
                                String tipoCombustible = partes[2];
                                double litros = Double.parseDouble(partes[3]);
                                
                                distribuidor.registrarTransaccion(surtidorId, tipoCombustible, litros);
                                salida.println("OK: Transacción registrada");
                            } catch (NumberFormatException e) {
                                salida.println("ERROR: Formato de transacción inválido");
                            }
                        } else {
                            salida.println("ERROR: Formato de transacción incorrecto");
                        }
                    } else if (mensaje.startsWith("REPORTE:")) {
                        salida.println("ACK");
                    } else if (mensaje.startsWith("ID:")) {
                        // Ignorar - ya se procesó en la identificación inicial
                    } else if (mensaje.startsWith("OK:") || mensaje.startsWith("ERROR:") || mensaje.equals("ACK")) {
                        // Ignorar respuestas informativas del surtidor
                    } else if (!mensaje.trim().isEmpty()) {
                        // Solo registrar mensajes no vacíos que no sean comandos conocidos
                        System.out.println("[" + idSurtidor + "] " + mensaje);
                    }
                }
                
                distribuidor.desregistrarSurtidor(idSurtidor);
                
            } catch (IOException e) {
                System.err.println("[ERROR] Error con surtidor: " + e.getMessage());
            }
        }
    }
}
