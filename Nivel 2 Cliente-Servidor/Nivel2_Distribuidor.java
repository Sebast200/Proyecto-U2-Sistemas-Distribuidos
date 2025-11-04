// Nivel2_Distribuidor.java
import java.io.*;
import java.net.*;
import java.util.*;

public class Nivel2_Distribuidor {
    private static final String HOST_NIVEL3 = "127.0.0.1";
    private static final int PUERTO_NIVEL3 = 6000;

    private static PrintWriter salidaNivel3;
    private static String nombreDistribuidor;
    private static int puertoServidor;  // cada distribuidor tiene su propio puerto

    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);
        System.out.print("Ingrese identificador del distribuidor (ej: Estacion_Norte): ");
        nombreDistribuidor = sc.nextLine();
        System.out.print("Ingrese puerto para este distribuidor (ej: 5000, 5001...): ");
        puertoServidor = sc.nextInt();

        new Thread(Nivel2_Distribuidor::iniciarClienteNivel3).start();
        new Thread(Nivel2_Distribuidor::iniciarServidorNivel1).start();
    }

    // Servidor (recibe surtidores)
    private static void iniciarServidorNivel1() {
        try (ServerSocket servidor = new ServerSocket(puertoServidor)) {
            System.out.println("[" + nombreDistribuidor + "] Servidor TCP escuchando en puerto " + puertoServidor);

            while (true) {
                Socket cliente = servidor.accept();
                System.out.println("[" + nombreDistribuidor + "] Surtidor conectado: " + cliente.getInetAddress());
                new Thread(new ManejadorSurtidor(cliente)).start();
            }

        } catch (IOException e) {
            System.err.println("[" + nombreDistribuidor + "] Error servidor: " + e.getMessage());
        }
    }

    // Cliente (conecta con Nivel 3)
    private static void iniciarClienteNivel3() {
        while (true) {
            try {
                Socket socket = new Socket(HOST_NIVEL3, PUERTO_NIVEL3);
                salidaNivel3 = new PrintWriter(socket.getOutputStream(), true);
                System.out.println("[" + nombreDistribuidor + "] Conectado al Nivel 3.");

                salidaNivel3.println("[" + nombreDistribuidor + "] Distribuidor activo en puerto " + puertoServidor);

                // hilo para enviar mensajes manuales al Nivel 3
                new Thread(() -> {
                    try (Scanner scanner = new Scanner(System.in)) {
                        while (true) {
                            System.out.print("[" + nombreDistribuidor + "] Enviar mensaje al Nivel 3: ");
                            String msg = scanner.nextLine();
                            if (msg.equalsIgnoreCase("salir")) break;
                            salidaNivel3.println("[" + nombreDistribuidor + "][Manual] " + msg);
                        }
                    }
                }).start();

                BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                String msg;
                while ((msg = entrada.readLine()) != null) {
                    System.out.println("[" + nombreDistribuidor + "] Mensaje desde Nivel 3: " + msg);
                }

            } catch (IOException e) {
                System.err.println("[" + nombreDistribuidor + "] No se pudo conectar al Nivel 3. Reintentando...");
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            }
        }
    }

    // Manejador de surtidores
    static class ManejadorSurtidor implements Runnable {
        private Socket socket;

        public ManejadorSurtidor(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (
                BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter salida = new PrintWriter(socket.getOutputStream(), true)
            ) {
                String mensaje;
                while ((mensaje = entrada.readLine()) != null) {
                    if (mensaje.equalsIgnoreCase("salir")) break;

                    System.out.println("[" + nombreDistribuidor + "] Recibido desde Surtidor: " + mensaje);
                    salida.println("ACK desde " + nombreDistribuidor);

                    // solo reenviar al nivel 3 si corresponde (ej. reportes)
                    if (salidaNivel3 != null && mensaje.startsWith("REPORTE:")) {
                        salidaNivel3.println("[" + nombreDistribuidor + "][Reporte] " + mensaje);
                    }
                }

                socket.close();
                System.out.println("[" + nombreDistribuidor + "] Surtidor desconectado.");

            } catch (IOException e) {
                System.err.println("[" + nombreDistribuidor + "] Error con surtidor: " + e.getMessage());
            }
        }
    }
}
