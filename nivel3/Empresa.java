import java.io.*;
import java.net.*;
import java.util.*;

public class Empresa {
    private final List<String> reportesRecibidos = Collections.synchronizedList(new ArrayList<>());
    private String nombreEmpresa;
    private Map<String, Double> preciosCombustibles;
    private List<DistribuidorConectado> distribuidores;
    private static final int PUERTO_DISTRIBUIDORES = 7000;
    private static final String ARCHIVO_PRECIOS = "/app/data/precios_empresa.txt";
    
    public Empresa(String nombre) {
        this.nombreEmpresa = nombre;
        this.preciosCombustibles = new HashMap<>();
        this.distribuidores = new ArrayList<>();
        inicializarPrecios();
        cargarPrecios();
    }
    
    private void inicializarPrecios() {
        // Precios por defecto de la empresa
        preciosCombustibles.put("93", 100.0);
        preciosCombustibles.put("95", 110.0);
        preciosCombustibles.put("97", 120.0);
        preciosCombustibles.put("Diesel", 95.0);
        preciosCombustibles.put("Kerosene", 85.0);
    }
    
    private void cargarPrecios() {
        File archivo = new File(ARCHIVO_PRECIOS);
        if (!archivo.exists()) {
            System.out.println("[INFO] No existe archivo de precios, usando valores por defecto");
            guardarPrecios();
            return;
        }
        
        try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                String[] partes = linea.split(",");
                if (partes.length == 2) {
                    String tipo = partes[0].trim();
                    double precio = Double.parseDouble(partes[1].trim());
                    preciosCombustibles.put(tipo, precio);
                }
            }
            System.out.println("[INFO] Precios corporativos cargados desde archivo");
            mostrarPrecios();
        } catch (IOException | NumberFormatException e) {
            System.err.println("[ERROR] Error al cargar precios: " + e.getMessage());
        }
    }
    
    private void guardarPrecios() {
        try {
            File archivo = new File(ARCHIVO_PRECIOS);
            archivo.getParentFile().mkdirs();
            
            try (BufferedWriter bw = new BufferedWriter(new FileWriter(archivo))) {
                for (Map.Entry<String, Double> entry : preciosCombustibles.entrySet()) {
                    bw.write(entry.getKey() + "," + entry.getValue());
                    bw.newLine();
                }
            }
            System.out.println("[INFO] Precios corporativos guardados en archivo");
        } catch (IOException e) {
            System.err.println("[ERROR] Error al guardar precios: " + e.getMessage());
        }
    }
    
    private void mostrarPrecios() {
        System.out.println("\n=== PRECIOS CORPORATIVOS ===");
        for (Map.Entry<String, Double> entry : preciosCombustibles.entrySet()) {
            System.out.printf("  %s: $%.2f%n", entry.getKey(), entry.getValue());
        }
        System.out.println("============================\n");
    }

    private void mostrarReportes() {
        System.out.println("\n=== REPORTES RECIBIDOS ===");
        synchronized (reportesRecibidos) {
            if (reportesRecibidos.isEmpty()) {
                System.out.println("No se han recibido reportes aún.");
            } else {
                for (String reporte : reportesRecibidos) {
                    System.out.println("  - " + reporte);
                }
            }
        }
        System.out.println("===========================\n");
    }

    public synchronized void agregarReporte(String reporte) {
        reportesRecibidos.add(reporte);
    }

        
    private void enviarPreciosADistribuidor(DistribuidorConectado distribuidor) {
        System.out.println("[SYNC] Enviando precios corporativos al distribuidor " + distribuidor.getId());
        for (Map.Entry<String, Double> entry : preciosCombustibles.entrySet()) {
            distribuidor.enviarComando("PRECIO " + entry.getKey() + " " + entry.getValue());
        }
    }
    
    private void enviarPreciosATodos() {
        if (distribuidores.isEmpty()) {
            System.out.println("[WARN] No hay distribuidores conectados");
            return;
        }
        
        System.out.println("[SYNC] Enviando precios a " + distribuidores.size() + " distribuidores...");
        for (DistribuidorConectado dist : distribuidores) {
            enviarPreciosADistribuidor(dist);
        }
        System.out.println("[SYNC] Precios enviados exitosamente");
    }
    
    public static void main(String[] args) {
        System.out.print("Ingrese nombre de la empresa (ej: Copec_Central): ");
        Scanner sc = new Scanner(System.in);
        String nombre = sc.nextLine();
        
        Empresa empresa = new Empresa(nombre);
        
        System.out.println("\n╔═════════════════════════════════════════╗");
        System.out.println("║  EMPRESA " + nombre + " - Nivel 3  ║");
        System.out.println("╚═════════════════════════════════════════╝\n");
        
        // Iniciar servidor para recibir distribuidores
        new Thread(() -> empresa.iniciarServidorDistribuidores()).start();
        
        // Menú de comandos
        empresa.menuPrincipal(sc);
    }
    
    private void iniciarServidorDistribuidores() {
        try (ServerSocket servidor = new ServerSocket(PUERTO_DISTRIBUIDORES)) {
            System.out.println("[SERVIDOR] Escuchando conexiones de distribuidores en puerto " + PUERTO_DISTRIBUIDORES);
            
            while (true) {
                Socket socket = servidor.accept();
                System.out.println("[CONEXIÓN] Nuevo distribuidor conectado desde: " + socket.getInetAddress());
                
                // Crear hilo para manejar este distribuidor
                new Thread(new ManejadorDistribuidor(socket, this)).start();
            }
        } catch (IOException e) {
            System.err.println("[ERROR] Error en servidor de distribuidores: " + e.getMessage());
        }
    }
    
    public synchronized void registrarDistribuidor(String id, DistribuidorConectado distribuidor) {
        distribuidores.add(distribuidor);
        System.out.println("[REGISTRO] Distribuidor " + id + " registrado. Total distribuidores: " + distribuidores.size());
    }
    
    public synchronized void desregistrarDistribuidor(DistribuidorConectado distribuidor) {
        distribuidores.remove(distribuidor);
        System.out.println("[DESCONEXIÓN] Distribuidor " + distribuidor.getId() + " desconectado. Total distribuidores: " + distribuidores.size());
    }
    
    private void menuPrincipal(Scanner sc) {
    while (true) {
        System.out.println("\n=== MENÚ DE LA EMPRESA ===");
        System.out.println("1. Listar distribuidores conectados");
        System.out.println("2. Ver precios corporativos actuales");
        System.out.println("3. Actualizar precio de un combustible");
        System.out.println("4. Actualizar todos los precios");
        System.out.println("5. Enviar precios a todos los distribuidores");
        System.out.println("6. Salir");
        System.out.println("7. Ver reportes recibidos"); 
        System.out.print("\nSeleccione opción: ");

        String opcion = sc.nextLine();

        switch (opcion) {
            case "1": listarDistribuidores(); break;
            case "2": mostrarPrecios(); break;
            case "3": actualizarPrecio(sc); break;
            case "4": actualizarTodosLosPrecios(sc); break;
            case "5": enviarPreciosATodos(); break;
            case "6":
                System.out.println("Saliendo...");
                System.exit(0);
                break;
            case "7": // 👇 nuevo caso
                mostrarReportes();
                break;
            default:
                System.out.println("Opción inválida");
        }
    }
}
    
    private void listarDistribuidores() {
        System.out.println("\n=== DISTRIBUIDORES CONECTADOS ===");
        if (distribuidores.isEmpty()) {
            System.out.println("No hay distribuidores conectados");
        } else {
            for (DistribuidorConectado dist : distribuidores) {
                System.out.println("  - " + dist.getId());
            }
        }
        System.out.println("Total: " + distribuidores.size() + " distribuidores");
    }
    
    private void actualizarPrecio(Scanner sc) {
        mostrarPrecios();
        
        System.out.print("Tipo de combustible (93, 95, 97, Diesel, Kerosene): ");
        String tipo = sc.nextLine();
        
        if (!preciosCombustibles.containsKey(tipo)) {
            System.out.println("Tipo de combustible no válido");
            return;
        }
        
        System.out.print("Nuevo precio corporativo: ");
        try {
            double precio = Double.parseDouble(sc.nextLine());
            
            // Actualizar precio en memoria
            preciosCombustibles.put(tipo, precio);
            
            // Guardar en archivo
            guardarPrecios();
            
            System.out.println("✓ Precio corporativo actualizado y guardado");
            System.out.print("¿Enviar cambio a todos los distribuidores? (s/n): ");
            String respuesta = sc.nextLine();
            
            if (respuesta.equalsIgnoreCase("s")) {
                for (DistribuidorConectado dist : distribuidores) {
                    dist.enviarComando("PRECIO " + tipo + " " + precio);
                }
                System.out.println("✓ Comando enviado a " + distribuidores.size() + " distribuidores");
            }
        } catch (NumberFormatException e) {
            System.out.println("Precio inválido");
        }
    }
    
    private void actualizarTodosLosPrecios(Scanner sc) {
        System.out.println("\n=== ACTUALIZACIÓN MASIVA DE PRECIOS ===");
        
        for (String tipo : new String[]{"93", "95", "97", "Diesel", "Kerosene"}) {
            System.out.print(tipo + " (actual: $" + preciosCombustibles.get(tipo) + "): ");
            String input = sc.nextLine();
            if (!input.trim().isEmpty()) {
                try {
                    double precio = Double.parseDouble(input);
                    preciosCombustibles.put(tipo, precio);
                } catch (NumberFormatException e) {
                    System.out.println("Precio inválido, manteniendo el anterior");
                }
            }
        }
        
        // Guardar en archivo
        guardarPrecios();
        
        System.out.println("\n✓ Precios corporativos actualizados");
        System.out.print("¿Enviar cambios a todos los distribuidores? (s/n): ");
        String respuesta = sc.nextLine();
        
        if (respuesta.equalsIgnoreCase("s")) {
            enviarPreciosATodos();
        }
    }
    
    // Clase interna para representar un distribuidor conectado
    static class DistribuidorConectado {
        private String id;
        private PrintWriter salida;
        
        public DistribuidorConectado(String id, PrintWriter salida) {
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
    
    // Manejador de conexiones de distribuidores
    static class ManejadorDistribuidor implements Runnable {
        private Socket socket;
        private Empresa empresa;
        
        public ManejadorDistribuidor(Socket socket, Empresa empresa) {
            this.socket = socket;
            this.empresa = empresa;
        }
        
        @Override
        public void run() {
            try (
                BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter salida = new PrintWriter(socket.getOutputStream(), true)
            ) {
                // Solicitar ID del distribuidor
                salida.println("IDENTIFICAR");
                String respuesta = entrada.readLine();
                
                if (respuesta == null || !respuesta.startsWith("ID:")) {
                    System.err.println("[ERROR] Distribuidor no se identificó correctamente");
                    return;
                }
                
                String idDistribuidor = respuesta.substring(3).trim();
                
                // Registrar distribuidor
                DistribuidorConectado distribuidor = new DistribuidorConectado(idDistribuidor, salida);
                empresa.registrarDistribuidor(idDistribuidor, distribuidor);
                
                salida.println("OK: Conectado a empresa " + empresa.nombreEmpresa);
                
                // Enviar precios actuales al distribuidor recién conectado
                empresa.enviarPreciosADistribuidor(distribuidor);
                
                // Escuchar mensajes del distribuidor
                String mensaje;
                while ((mensaje = entrada.readLine()) != null) {
                    if (mensaje.equalsIgnoreCase("SALIR")) {
                        break;
                    }
                    
                    System.out.println("[" + idDistribuidor + "] " + mensaje);
                    
                    // Responder si es necesario
                    if (mensaje.startsWith("REPORTE_AUTOMATICO")) {
                        String contenido = mensaje.substring(18).trim();

                        // 🔹 Formatear el contenido para que se vea legible
                        String contenidoFormateado = contenido.replace("|", "\n[] ");

                        String hora = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
                        String registro = String.format("[%s] (%s)\n[] %s", hora, idDistribuidor, contenidoFormateado);

                        empresa.agregarReporte(registro);

                        System.out.println("📝 [REPORTE] " + registro);
                        salida.println("ACK");
                    }
                }
                
                empresa.desregistrarDistribuidor(distribuidor);
                
            } catch (IOException e) {
                System.err.println("[ERROR] Error con distribuidor: " + e.getMessage());
            }
        }
    }
}
