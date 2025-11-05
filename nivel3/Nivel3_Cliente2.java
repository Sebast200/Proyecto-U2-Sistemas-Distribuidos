// Nivel3_CasaMatriz.java
import java.io.*;
import java.net.*;
import java.util.Scanner;

public class Nivel3_Cliente2 {
    private static final String HOST_NIVEL2 = "127.0.0.1";
    private static final int PUERTO_NIVEL2 = 7000;

    public static void main(String[] args) {
        while (true) {
            try (Socket socket = new Socket(HOST_NIVEL2, PUERTO_NIVEL2);
                 PrintWriter salida = new PrintWriter(socket.getOutputStream(), true);
                 BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

                System.out.println("[CasaMatriz] Conectada a Nivel2 en " + HOST_NIVEL2 + ":" + PUERTO_NIVEL2);
                salida.println("[CasaMatriz] Activa");

                // hilo para enviar mensajes manuales al Nivel2
                Thread sender = new Thread(() -> {
                    try (Scanner scanner = new Scanner(System.in)) {
                        while (true) {
                            System.out.print("[CasaMatriz] Enviar mensaje a Nivel2 (salir para terminar): ");
                            String msg = scanner.nextLine();
                            if (msg.equalsIgnoreCase("salir")) {
                                salida.println("salir");
                                break;
                            }
                            salida.println("[CasaMatriz][Manual] " + msg);
                        }
                    }
                });
                sender.start();

                // leer mensajes desde el servidor (si los hubiera)
                String msg;
                while ((msg = entrada.readLine()) != null) {
                    System.out.println("[CasaMatriz] Mensaje desde Nivel2: " + msg);
                }

                // si la conexión se cierra, salimos del bloque y reintentamos
            } catch (IOException e) {
                System.err.println("[CasaMatriz] No se pudo conectar a Nivel2. Reintentando en 5s...");
                try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
            }
        }
    }
}
