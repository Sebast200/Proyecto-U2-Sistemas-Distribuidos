import java.io.*;
import java.net.*;
import java.util.HashMap;
import java.util.Map;

public class Estanque {
    private Map<String, Double> niveles;

    public Estanque() {
        this.niveles = new HashMap<>();
        niveles.put("93", 1000.0);
        niveles.put("95", 1000.0);
        niveles.put("97", 1000.0);
        niveles.put("Diesel", 1000.0);
        niveles.put("Kerosene", 1000.0);
    }     

    public synchronized boolean extraer(String tipo, double litros) {
        if (niveles.containsKey(tipo) && niveles.get(tipo) >= litros) {
            niveles.put(tipo, niveles.get(tipo) - litros);
            return true;
        }
        return false;
    }

    public void guardarEstado(String rutaArchivo) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(rutaArchivo))) {
            for (Map.Entry<String, Double> entry : niveles.entrySet()) {
                writer.write(entry.getKey() + "," + entry.getValue());
                writer.newLine();
            }
        }
    }

    public void cargarEstado(String rutaArchivo) throws IOException {
        try (BufferedReader reader = new BufferedReader(new FileReader(rutaArchivo))) {
            String linea;
            while ((linea = reader.readLine()) != null) {
                String[] partes = linea.split(",");
                String tipo = partes[0];
                double cantidad = Double.parseDouble(partes[1]);
                niveles.put(tipo, cantidad);
            }
        }
    }

    public void mostrarEstado() {
        System.out.println("Estado del estanque:");
        for (Map.Entry<String, Double> entry : niveles.entrySet()) {
            System.out.println(entry.getKey() + ": " + entry.getValue() + " litros");
        }
    }

    public synchronized double getNivel(String tipo) {
        return niveles.getOrDefault(tipo, 0.0);
    }

    public synchronized boolean reponer(String tipo, double litros) {
        if (niveles.containsKey(tipo)) {
            niveles.put(tipo, niveles.get(tipo) + litros);
            return true;
        }
        return false;
    }

    private void guardarEstadoAutomatico() {
        try {
            guardarEstado("/app/data/estado_estanque.txt");
        } catch (IOException e) {
            System.err.println("[ERROR] No se pudo guardar el estado: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        Estanque estanque = new Estanque();
        int puerto = 5000;
        String archivoEstado = "/app/data/estado_estanque.txt";
        
        System.out.println("=== SERVIDOR ESTANQUE DE COMBUSTIBLE ===");
        
        // Intentar cargar estado previo
        File archivo = new File(archivoEstado);
        if (archivo.exists()) {
            try {
                estanque.cargarEstado(archivoEstado);
                System.out.println("[INFO] Estado cargado desde archivo");
            } catch (IOException e) {
                System.err.println("[ADVERTENCIA] No se pudo cargar el estado previo: " + e.getMessage());
                System.out.println("[INFO] Iniciando con valores por defecto");
            }
        } else {
            System.out.println("[INFO] No existe archivo de estado, iniciando con valores por defecto");
            try {
                // Crear directorio si no existe
                new File("/app/data").mkdirs();
                estanque.guardarEstado(archivoEstado);
            } catch (IOException e) {
                System.err.println("[ADVERTENCIA] No se pudo crear archivo de estado");
            }
        }
        
        System.out.println("\nEstado actual del estanque:");
        estanque.mostrarEstado();
        System.out.println("\nEscuchando en puerto " + puerto + "...\n");
        
        // Hilo para guardar estado periódicamente (cada 30 segundos)
        Thread guardadoAutomatico = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(30000); // 30 segundos
                    estanque.guardarEstadoAutomatico();
                    System.out.println("[AUTO-SAVE] Estado guardado automáticamente");
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        guardadoAutomatico.setDaemon(true);
        guardadoAutomatico.start();
        
        try (ServerSocket servidor = new ServerSocket(puerto)) {
            while (true) {
                Socket socket = servidor.accept();
                System.out.println("[CONEXIÓN] Cliente conectado: " + socket.getInetAddress());
                new Thread(new ManejadorCliente(socket, estanque)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            // Guardar estado al cerrar
            try {
                estanque.guardarEstado(archivoEstado);
                System.out.println("[SHUTDOWN] Estado guardado");
            } catch (IOException e) {
                System.err.println("[ERROR] No se pudo guardar el estado al cerrar");
            }
        }
    }

}

class ManejadorCliente implements Runnable {
    private Socket socket;
    private Estanque estanque;

    public ManejadorCliente(Socket socket, Estanque estanque) {
        this.socket = socket;
        this.estanque = estanque;
    }
    
    private void guardarEstado() {
        try {
            estanque.guardarEstado("/app/data/estado_estanque.txt");
        } catch (IOException e) {
            System.err.println("[ERROR] No se pudo guardar el estado: " + e.getMessage());
        }
    }

    @Override
    public void run() {
        try (
            BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter salida = new PrintWriter(socket.getOutputStream(), true)
        ) {
            salida.println("=== SISTEMA DE GESTIÓN DE ESTANQUE ===");
            salida.println("Comandos disponibles:");
            salida.println("  EXTRAER <tipo> <litros>  - Extraer combustible del estanque");
            salida.println("  REPONER <tipo> <litros>  - Reponer combustible al estanque");
            salida.println("  CONSULTAR <tipo>         - Ver nivel de un combustible");
            salida.println("  ESTADO                   - Ver estado completo del estanque");
            salida.println("  SALIR                    - Desconectar");
            salida.println("\nTipos: 93, 95, 97, Diesel, Kerosene");

            String mensaje;
            while ((mensaje = entrada.readLine()) != null) {
                if (mensaje.equalsIgnoreCase("salir")) {
                    salida.println("Desconectando...");
                    break;
                }

                String[] partes = mensaje.trim().split("\\s+");
                if (partes.length == 0) continue;

                String comando = partes[0].toUpperCase();

                switch (comando) {
                    case "EXTRAER":
                        if (partes.length != 3) {
                            salida.println("ERROR: Formato incorrecto. Usa: EXTRAER <tipo> <litros>");
                            break;
                        }
                        String tipoExtraer = partes[1];
                        try {
                            double litrosExtraer = Double.parseDouble(partes[2]);
                            if (litrosExtraer <= 0) {
                                salida.println("ERROR: La cantidad debe ser positiva");
                                break;
                            }
                            if (estanque.extraer(tipoExtraer, litrosExtraer)) {
                                salida.println("OK: Extraídos " + litrosExtraer + " litros de " + tipoExtraer + 
                                             ". Nivel actual: " + estanque.getNivel(tipoExtraer) + " litros");
                                System.out.println("[OPERACIÓN] Extraídos " + litrosExtraer + " L de " + tipoExtraer);
                                // Guardar estado después de la operación
                                guardarEstado();
                            } else {
                                salida.println("ERROR: No hay suficiente combustible o tipo inválido. Disponible: " + 
                                             estanque.getNivel(tipoExtraer) + " litros");
                            }
                        } catch (NumberFormatException e) {
                            salida.println("ERROR: Los litros deben ser un número válido");
                        }
                        break;

                    case "REPONER":
                        if (partes.length != 3) {
                            salida.println("ERROR: Formato incorrecto. Usa: REPONER <tipo> <litros>");
                            break;
                        }
                        String tipoReponer = partes[1];
                        try {
                            double litrosReponer = Double.parseDouble(partes[2]);
                            if (litrosReponer <= 0) {
                                salida.println("ERROR: La cantidad debe ser positiva");
                                break;
                            }
                            if (estanque.reponer(tipoReponer, litrosReponer)) {
                                salida.println("OK: Repuestos " + litrosReponer + " litros de " + tipoReponer + 
                                             ". Nivel actual: " + estanque.getNivel(tipoReponer) + " litros");
                                System.out.println("[OPERACIÓN] Repuestos " + litrosReponer + " L de " + tipoReponer);
                                // Guardar estado después de la operación
                                guardarEstado();
                            } else {
                                salida.println("ERROR: Tipo de combustible inválido");
                            }
                        } catch (NumberFormatException e) {
                            salida.println("ERROR: Los litros deben ser un número válido");
                        }
                        break;

                    case "CONSULTAR":
                        if (partes.length != 2) {
                            salida.println("ERROR: Formato incorrecto. Usa: CONSULTAR <tipo>");
                            break;
                        }
                        String tipoConsultar = partes[1];
                        double nivel = estanque.getNivel(tipoConsultar);
                        if (nivel > 0) {
                            salida.println("Nivel de " + tipoConsultar + ": " + nivel + " litros");
                        } else {
                            salida.println("Tipo de combustible no encontrado o nivel en 0");
                        }
                        break;

                    case "ESTADO":
                        salida.println("\n=== ESTADO DEL ESTANQUE ===");
                        salida.println("93:       " + String.format("%.2f", estanque.getNivel("93")) + " litros");
                        salida.println("95:       " + String.format("%.2f", estanque.getNivel("95")) + " litros");
                        salida.println("97:       " + String.format("%.2f", estanque.getNivel("97")) + " litros");
                        salida.println("Diesel:   " + String.format("%.2f", estanque.getNivel("Diesel")) + " litros");
                        salida.println("Kerosene: " + String.format("%.2f", estanque.getNivel("Kerosene")) + " litros");
                        salida.println("============================");
                        break;

                    default:
                        salida.println("ERROR: Comando no reconocido. Usa: EXTRAER, REPONER, CONSULTAR, ESTADO, SALIR");
                        break;
                }
            }

            System.out.println("[DESCONEXIÓN] Cliente desconectado: " + socket.getInetAddress());
            socket.close();

        } catch (IOException e) {
            System.err.println("[ERROR] Error con cliente: " + e.getMessage());
        }
    }
}