import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Surtidor {
    private String id;
    private boolean estado; //Para comprobar si se esta utilizando el surtidor y asi no permitir actualizar el precio
    private Map<String, Combustible> combustibles;
    private PrintWriter salidaDistribuidor; // Para enviar transacciones al distribuidor

    public Surtidor(String _id){
        this.id = _id;
        this.estado = false;
        this.combustibles = new HashMap<>();
        this.salidaDistribuidor = null;
    }
    
    public void setSalidaDistribuidor(PrintWriter salida) {
        this.salidaDistribuidor = salida;
    }

    //SETTER Y GETTERS

    public void setEstado(boolean _estado){
        this.estado = _estado;
    }

    public void inicializarCombustible() {
        combustibles.put("93", new Combustible("93", 0, 0, 100.0, 0.0));
        combustibles.put("95", new Combustible("95", 0, 0, 100.0, 0.0));
        combustibles.put("97", new Combustible("97", 0, 0, 100.0, 0.0));
        combustibles.put("Diesel", new Combustible("Diesel", 0, 0, 100.0, 0.0));
        combustibles.put("Kerosene", new Combustible("Kerosene", 0, 0, 100.0, 0.0));
    }

    public synchronized boolean registrarCarga(String tipo, double litros) {
        if (estado && combustibles.containsKey(tipo)) {
            System.out.println("Registrando carga de combustible | "+ "Tipo: " +tipo+" Cantidad: "+litros+" Litros");
            combustibles.get(tipo).registrarCarga(litros);
            
            // Enviar transacción al distribuidor si está conectado
            if (salidaDistribuidor != null) {
                salidaDistribuidor.println("TRANSACCION " + id + " " + tipo + " " + litros);
                System.out.println("[SYNC] Transacción enviada al distribuidor");
            }
            
            return true;
        }
        return false;
    }

    public synchronized boolean actualizarPrecio(String tipo, double nuevoPrecio) {
        if (estado && combustibles.containsKey(tipo)) {
            combustibles.get(tipo).actualizarPrecio(nuevoPrecio);
            return true;
        }
        return false;
    }

    public void guardarEstado(String rutaArchivo) throws IOException {
        List<String> nuevasLineas = new ArrayList<>();
        File archivo = new File(rutaArchivo);
        if (archivo.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(archivo))) {
                String linea;
                while ((linea = reader.readLine()) != null) {
                    // Solo mantener líneas de otros surtidores (no el actual)
                    String lineaId = linea.split(",")[0];
                    if (!lineaId.equals(id)) {
                        // Verificar que el ID sea numérico válido (1-4) antes de mantenerlo
                        try {
                            int idNum = Integer.parseInt(lineaId);
                            if (idNum >= 1 && idNum <= 4) {
                                nuevasLineas.add(linea);
                            }
                            // Si está fuera del rango 1-4, se descarta (limpieza automática)
                        } catch (NumberFormatException e) {
                            // IDs no numéricos (como S1, x1) se descartan (limpieza automática)
                        }
                    }
                }
            }
        }

        // Agregar las líneas del surtidor actual
        for (Combustible c : combustibles.values()) {
            nuevasLineas.add(id + "," + c.getTipo() + "," + c.getLitrosConsumidos() + "," + c.getPrecioActual());
        }

        // Escribir todo al archivo
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(rutaArchivo))) {
            for (String linea : nuevasLineas) {
                writer.write(linea);
                writer.newLine();
            }
        }
    }

    public void cargarEstado(String rutaArchivo) throws IOException {
        // Primero inicializar con valores por defecto
        inicializarCombustible();
        
        // Luego intentar sobrescribir con valores guardados
        File archivo = new File(rutaArchivo);
        if (!archivo.exists()) {
            return; // Si no existe el archivo, usar valores por defecto
        }
        
        try (BufferedReader reader = new BufferedReader(new FileReader(rutaArchivo))) {
            String linea;
            boolean encontrado = false;
            while ((linea = reader.readLine()) != null) {
                String[] partes = linea.split(",");
                if (partes.length < 4) continue;
                
                String surtidorId = partes[0];
                if (!surtidorId.equals(this.id)) continue;

                String tipo = partes[1];
                double litrosConsumidos = Double.parseDouble(partes[2]);
                double precio = Double.parseDouble(partes[3]);
                
                // Actualizar el combustible existente
                if (combustibles.containsKey(tipo)) {
                    // Calcular cargas realizadas (aproximación basada en litros)
                    int cargas = (int)(litrosConsumidos / 10); // Asumiendo cargas promedio de 10L
                    combustibles.put(tipo, new Combustible(tipo, litrosConsumidos, cargas, precio, 0.0));
                    encontrado = true;
                }
            }
            
            if (!encontrado) {
                System.out.println("[INFO] No se encontraron datos previos para surtidor " + this.id);
            }
        }
    }

    public synchronized boolean reponerCombustible(String tipo, double litros) {
        if (combustibles.containsKey(tipo)) {
            combustibles.get(tipo).reponer(litros);
            return true;
        }
        return false;
    }

    public void mostrarEstado() {
        System.out.println("Estado del surtidor " + id + ":");
        for (Combustible c : combustibles.values()) {
            System.out.println(c.getTipo() + " - Cargas: " + c.getCargasRealizadas() +", Litros entregados: " + 
            c.getLitrosConsumidos() + ", Precio: $" + c.getPrecioActual());
        }
    }
    
    public String getId() {
        return this.id;
    }
    
    // Método para conectarse al distribuidor
    public void conectarADistribuidor(String host, int puerto, String archivoEstado) {
        new Thread(() -> {
            while (true) {
                try (
                    Socket socket = new Socket(host, puerto);
                    BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                    PrintWriter salida = new PrintWriter(socket.getOutputStream(), true)
                ) {
                    System.out.println("[DISTRIBUIDOR] Conectado al distribuidor en " + host + ":" + puerto);
                    
                    // Guardar referencia al PrintWriter del distribuidor
                    this.setSalidaDistribuidor(salida);
                    
                    // Esperar solicitud de identificación
                    String mensaje = entrada.readLine();
                    if (mensaje != null && mensaje.equals("IDENTIFICAR")) {
                        salida.println("ID:" + this.id);
                    }
                    
                    // Esperar confirmación
                    String confirmacion = entrada.readLine();
                    if (confirmacion != null) {
                        System.out.println("[DISTRIBUIDOR] " + confirmacion);
                    }
                    
                    // Escuchar comandos del distribuidor
                    while ((mensaje = entrada.readLine()) != null) {
                        // Filtrar mensajes informativos (OK, ERROR, ACK) - no necesitan procesamiento
                        if (mensaje.startsWith("OK:") || mensaje.startsWith("ERROR:") || mensaje.equals("ACK")) {
                            // Solo mostrar si es relevante (no es respuesta a transacción)
                            if (!mensaje.contains("Transacción registrada")) {
                                System.out.println("[DISTRIBUIDOR] " + mensaje);
                            }
                            continue;
                        }
                        
                        String[] partes = mensaje.trim().split("\\s+");
                        
                        if (partes.length == 0) continue;
                        String comando = partes[0].toUpperCase();
                        
                        switch (comando) {
                            case "PRECIO":
                                if (partes.length == 3) {
                                    String tipo = partes[1];
                                    try {
                                        double precio = Double.parseDouble(partes[2]);
                                        this.setEstado(true);
                                        if (this.actualizarPrecio(tipo, precio)) {
                                            salida.println("OK: Precio de " + tipo + " actualizado a $" + precio);
                                            this.guardarEstado(archivoEstado);
                                            System.out.println("[DISTRIBUIDOR] ✓ Precio actualizado: " + tipo + " = $" + precio);
                                        } else {
                                            salida.println("ERROR: No se pudo actualizar el precio");
                                        }
                                        this.setEstado(false);
                                    } catch (NumberFormatException e) {
                                        salida.println("ERROR: Precio inválido");
                                    } catch (IOException e) {
                                        salida.println("ERROR: No se pudo guardar el estado");
                                    }
                                }
                                break;
                                
                            case "ESTADO_SURTIDOR":
                                StringBuilder estado = new StringBuilder();
                                estado.append("ESTADO:" + this.id + "|");
                                for (Combustible c : combustibles.values()) {
                                    estado.append(c.getTipo()).append(":")
                                          .append(c.getPrecioActual()).append(":")
                                          .append(c.getLitrosConsumidos()).append(":")
                                          .append(c.getCargasRealizadas()).append(";");
                                }
                                salida.println(estado.toString());
                                break;
                                
                            default:
                                // Ignorar mensajes desconocidos silenciosamente
                                // (pueden ser respuestas informativas del distribuidor)
                        }
                    }
                    
                    System.out.println("[DISTRIBUIDOR] Desconectado");
                    
                } catch (IOException e) {
                    System.err.println("[DISTRIBUIDOR] Error de conexión: " + e.getMessage());
                    System.out.println("[DISTRIBUIDOR] Reintentando en 10 segundos...");
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException ignored) {}
                }
            }
        }).start();
    }

    private static String asignarIdAutomatico(String rutaArchivo) {
        // Buscar IDs ya existentes en el archivo
        boolean[] idsUsados = new boolean[5]; // Índices 0-4, usaremos 1-4
        
        File archivo = new File(rutaArchivo);
        if (archivo.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(rutaArchivo))) {
                String linea;
                while ((linea = reader.readLine()) != null) {
                    String[] partes = linea.split(",");
                    if (partes.length > 0) {
                        String id = partes[0].trim();
                        // Verificar si es un ID numérico del 1-4
                        try {
                            int idNum = Integer.parseInt(id);
                            if (idNum >= 1 && idNum <= 4) {
                                idsUsados[idNum] = true;
                            }
                        } catch (NumberFormatException e) {
                            // Ignorar IDs no numéricos
                        }
                    }
                }
            } catch (IOException e) {
                System.err.println("[WARN] No se pudo leer archivo de estado: " + e.getMessage());
            }
        }
        
        // Encontrar el primer ID disponible del 1 al 4
        for (int i = 1; i <= 4; i++) {
            if (!idsUsados[i]) {
                return String.valueOf(i);
            }
        }
        
        // Si todos están ocupados, usar uno aleatorio del 1-4
        return String.valueOf((int)(Math.random() * 4) + 1);
    }

    public static void main(String[] args) {
        String servidorIP = args.length > 0 ? args[0] : "servidor";
        int puerto = 5000;
        String archivoEstado = "/app/data/estado_surtidor.txt";
        
        // Asignar ID automáticamente del 1 al 4
        String surtidorId = asignarIdAutomatico(archivoEstado);
        System.out.println("[INFO] ID asignado automáticamente: " + surtidorId);
        
        // Crear instancia del surtidor
        Surtidor surtidor = new Surtidor(surtidorId);
        
        // Intentar cargar estado previo (esto ya inicializa si no encuentra datos)
        try {
            surtidor.cargarEstado(archivoEstado);
            System.out.println("[INFO] Surtidor " + surtidorId + " listo");
        } catch (IOException e) {
            System.err.println("[ERROR] Error al cargar estado: " + e.getMessage());
            surtidor.inicializarCombustible();
        }
        
        // Guardar estado inicial si no existe el archivo
        File archivo = new File(archivoEstado);
        if (!archivo.exists()) {
            try {
                new File("/app/data").mkdirs();
                surtidor.guardarEstado(archivoEstado);
                System.out.println("[INFO] Estado inicial guardado");
            } catch (IOException e) {
                System.err.println("[ADVERTENCIA] No se pudo crear archivo de estado");
            }
        }
        
        System.out.println("\n╔══════════════════════════════════════════╗");
        System.out.println("║  TERMINAL SURTIDOR " + surtidorId + " - Sistema de Gestión ║");
        System.out.println("╚══════════════════════════════════════════╝");
        
        // Mostrar estado actual
        surtidor.mostrarEstado();
        
        // Conectar automáticamente al distribuidor
        String distribuidorHost = System.getenv().getOrDefault("DISTRIBUIDOR_HOST", "distribuidor");
        int distribuidorPuerto = Integer.parseInt(System.getenv().getOrDefault("DISTRIBUIDOR_PORT", "6000"));
        System.out.println("\n[DISTRIBUIDOR] Conectando a " + distribuidorHost + ":" + distribuidorPuerto + "...");
        surtidor.conectarADistribuidor(distribuidorHost, distribuidorPuerto, archivoEstado);

        try (
            Socket socket = new Socket(servidorIP, puerto);
            BufferedReader teclado = new BufferedReader(new InputStreamReader(System.in));
            PrintWriter salida = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()))
        ) {
            System.out.println("\nConectado al Estanque: " + servidorIP + ":" + puerto + "\n");

            // Leer y descartar mensajes iniciales del servidor (menú de bienvenida)
            String bienvenida;
            while ((bienvenida = entrada.readLine()) != null) {
                // Solo leer hasta encontrar el final del menú, pero no mostrarlo
                if (bienvenida.contains("Tipos:") || bienvenida.contains("Kerosene")) break;
            }

            System.out.println(">>> Comandos del Surtidor:");
            System.out.println("  EXTRAER <tipo> <litros>   - Extraer combustible del estanque");
            System.out.println("  CONSULTAR <tipo>          - Ver nivel disponible en estanque");
            System.out.println("  ESTADO                    - Ver estado del estanque");
            System.out.println("  CARGAR <tipo> <litros>    - Registrar venta de combustible");
            System.out.println("  PRECIO <tipo> <precio>    - Actualizar precio de combustible");
            System.out.println("  MISURTIDOR                - Ver estado de este surtidor");
            System.out.println("  SALIR                     - Desconectar\n");

            String mensaje;
            while (true) {
                System.out.print(surtidorId + "> ");
                mensaje = teclado.readLine();
                if (mensaje == null || mensaje.equalsIgnoreCase("salir")) {
                    salida.println("SALIR");
                    // Guardar estado antes de salir
                    try {
                        surtidor.guardarEstado(archivoEstado);
                        System.out.println("[INFO] Estado guardado correctamente");
                    } catch (IOException e) {
                        System.err.println("[ERROR] No se pudo guardar el estado: " + e.getMessage());
                    }
                    break;
                }
                
                String[] partes = mensaje.trim().split("\\s+");
                if (partes.length == 0) continue;
                
                String comando = partes[0].toUpperCase();
                
                // Comandos locales del surtidor
                if (comando.equals("CARGAR")) {
                    if (partes.length != 3) {
                        System.out.println("ERROR: Formato incorrecto. Usa: CARGAR <tipo> <litros>");
                        continue;
                    }
                    String tipo = partes[1];
                    try {
                        double litros = Double.parseDouble(partes[2]);
                        surtidor.setEstado(true);
                        if (surtidor.registrarCarga(tipo, litros)) {
                            System.out.println("OK: Registrada venta de " + litros + " L de " + tipo);
                            surtidor.guardarEstado(archivoEstado);
                        } else {
                            System.out.println("ERROR: No se pudo registrar la carga");
                        }
                        surtidor.setEstado(false);
                    } catch (NumberFormatException e) {
                        System.out.println("ERROR: Los litros deben ser un número");
                    } catch (IOException e) {
                        System.err.println("ERROR: No se pudo guardar el estado");
                    }
                    continue;
                }
                
                if (comando.equals("PRECIO")) {
                    if (partes.length != 3) {
                        System.out.println("ERROR: Formato incorrecto. Usa: PRECIO <tipo> <precio>");
                        continue;
                    }
                    String tipo = partes[1];
                    try {
                        double precio = Double.parseDouble(partes[2]);
                        surtidor.setEstado(true);
                        if (surtidor.actualizarPrecio(tipo, precio)) {
                            System.out.println("OK: Precio de " + tipo + " actualizado a $" + precio);
                            surtidor.guardarEstado(archivoEstado);
                        } else {
                            System.out.println("ERROR: No se pudo actualizar el precio");
                        }
                        surtidor.setEstado(false);
                    } catch (NumberFormatException e) {
                        System.out.println("ERROR: El precio debe ser un número");
                    } catch (IOException e) {
                        System.err.println("ERROR: No se pudo guardar el estado");
                    }
                    continue;
                }
                
                if (comando.equals("MISURTIDOR")) {
                    System.out.println("\n=== ESTADO DEL SURTIDOR " + surtidorId + " ===");
                    surtidor.mostrarEstado();
                    System.out.println("===============================\n");
                    continue;
                }
                
                // Comandos que se envían al servidor (estanque)
                salida.println(mensaje);
                
                // Leer respuesta (puede ser múltiples líneas para ESTADO)
                String respuesta = entrada.readLine();
                if (respuesta != null) {
                    System.out.println(respuesta);
                    
                    // Si la operación EXTRAER fue exitosa, registrar la carga en el surtidor
                    if (comando.equals("EXTRAER") && respuesta.startsWith("OK:")) {
                        try {
                            String tipo = partes[1];
                            double litros = Double.parseDouble(partes[2]);
                            surtidor.setEstado(true);
                            if (surtidor.registrarCarga(tipo, litros)) {
                                System.out.println("[SURTIDOR] Registrada venta de " + litros + " L de " + tipo);
                                surtidor.guardarEstado(archivoEstado);
                            }
                            surtidor.setEstado(false);
                        } catch (Exception e) {
                            System.err.println("[ERROR] No se pudo registrar la carga en el surtidor");
                        }
                    }
                    
                    // Si es comando ESTADO, leer todas las líneas hasta el final
                    if (mensaje.trim().toUpperCase().equals("ESTADO")) {
                        String linea;
                        while ((linea = entrada.readLine()) != null) {
                            System.out.println(linea);
                            if (linea.contains("====")) break;
                        }
                    }
                }
            }
            
            System.out.println("\n[INFO] Desconectado del estanque.");
            
        } catch (IOException e) {
            System.err.println("[ERROR] No se pudo conectar al estanque: " + e.getMessage());
        }
    }

}

class Combustible {
    private String tipo;
    private double ltConsumidos;
    private int cargasRealizadas;
    private double precioActual;
    private double cantidadDisponible;

    public Combustible(String _tipo, double _ltConsumidos, int _cargasRealizadas, double _precioActual, 
    double _cantidadDisponible) {
        this.tipo = _tipo;
        this.ltConsumidos = _ltConsumidos;
        this.cargasRealizadas = _cargasRealizadas;
        this.precioActual = _precioActual;
        this.cantidadDisponible = _cantidadDisponible;
    }

    //SETTERS Y GETTERS

    public String getTipo() {
        return this.tipo;
    }

    public double getCantidadDisponible() {
        return this.cantidadDisponible;
    }

    public double getPrecioActual() {
        return this.precioActual;
    }

    public int getCargasRealizadas() {
        return this.cargasRealizadas;
    }

    public double getLitrosConsumidos() {
        return this.ltConsumidos;
    }

    public synchronized void registrarCarga(double litros) {
        this.ltConsumidos += litros;
        this.cargasRealizadas++;
    }

    public synchronized void actualizarPrecio(double nuevoPrecio) {
        this.precioActual = nuevoPrecio;
    }

    public synchronized void reponer(double litros) {
        this.cantidadDisponible += litros;
    }

}
