// Nivel1_Surtidor.java
import java.io.*;
import java.net.*;
import java.util.*;

public class Nivel1_Surtidor {
    public static void main(String[] args) {
        Scanner sc = new Scanner(System.in);

        // el surtidor elige a qué distribuidor conectarse
        System.out.print("Ingrese identificador del distribuidor destino: ");
        String distribuidor = sc.nextLine();

        // mapa con puertos asignados
        Map<String, Integer> puertosDistribuidores = new HashMap<>();
        puertosDistribuidores.put("Estacion_Norte", 5000);
        puertosDistribuidores.put("Estacion_Sur", 5001);
        puertosDistribuidores.put("Estacion_Centro", 5002);

        Integer puerto = puertosDistribuidores.get(distribuidor);
        if (puerto == null) {
            System.err.println("[Surtidor] Distribuidor no encontrado. Verifique el nombre.");
            return;
        }

        try (Socket socket = new Socket("127.0.0.1", puerto);
             PrintWriter salida = new PrintWriter(socket.getOutputStream(), true);
             BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {

            System.out.println("[Surtidor] Conectado al distribuidor " + distribuidor + " en puerto " + puerto);

            Scanner scanner = new Scanner(System.in);
            String mensaje;

            while (true) {
                System.out.print("[Surtidor] Escribir mensaje (salir para terminar): ");
                mensaje = scanner.nextLine();
                salida.println(mensaje);

                if (mensaje.equalsIgnoreCase("salir")) break;

                String respuesta = entrada.readLine();
                System.out.println("[Surtidor] Respuesta del distribuidor: " + respuesta);
            }

        } catch (IOException e) {
            System.err.println("[Surtidor] Error: " + e.getMessage());
        }
    }
}
