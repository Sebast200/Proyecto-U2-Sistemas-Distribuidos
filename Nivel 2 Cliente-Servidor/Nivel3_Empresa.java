// Nivel3_Empresa.java
import java.io.*;
import java.net.*;
import java.util.concurrent.CopyOnWriteArrayList;

public class Nivel3_Empresa {
    private static final int PUERTO = 6000;
    private static CopyOnWriteArrayList<Socket> distribuidoresConectados = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        try (ServerSocket servidor = new ServerSocket(PUERTO)) {
            System.out.println("[Empresa] Servidor TCP escuchando en puerto " + PUERTO);

            while (true) {
                Socket distribuidor = servidor.accept();
                distribuidoresConectados.add(distribuidor);
                System.out.println("[Empresa] Nuevo distribuidor conectado: " + distribuidor.getInetAddress());
                new Thread(new ManejadorDistribuidor(distribuidor)).start();
            }

        } catch (IOException e) {
            System.err.println("[Empresa] Error servidor: " + e.getMessage());
        }
    }

    static class ManejadorDistribuidor implements Runnable {
        private Socket socket;

        public ManejadorDistribuidor(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try (BufferedReader entrada = new BufferedReader(
                    new InputStreamReader(socket.getInputStream()))) {
                String mensaje;
                while ((mensaje = entrada.readLine()) != null) {
                    // El Nivel 3 solo imprime mensajes directos del Nivel 2
                    System.out.println("[Empresa] Recibido desde distribuidor: " + mensaje);
                }
            } catch (IOException e) {
                System.err.println("[Empresa] Error con distribuidor: " + e.getMessage());
            } finally {
                distribuidoresConectados.remove(socket);
                System.out.println("[Empresa] Distribuidor desconectado: " + socket.getInetAddress());
            }
        }
    }
}
