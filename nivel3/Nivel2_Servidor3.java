import java.io.*;
import java.net.*;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;

class Nivel2_Servidor3 {
    private static final int PUERTO = 7000;
    private static CopyOnWriteArrayList<Socket> clientes = new CopyOnWriteArrayList<>();
    private static CopyOnWriteArrayList<PrintWriter> escritores = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        try (ServerSocket servidor = new ServerSocket(PUERTO)) {
            System.out.println("[Nivel2_Cliente3] Servidor escuchando en puerto " + PUERTO);

            // hilo para mandar mensajes manuales a todos los clientes conectados
            new Thread(() -> {
                try (Scanner sc = new Scanner(System.in)) {
                    while (true) {
                        System.out.print("[Nivel2_Cliente3] Mensaje para clientes (salir para terminar): ");
                        String msg = sc.nextLine();
                        if (msg.equalsIgnoreCase("salir")) break;
                        for (PrintWriter pw : escritores) {
                            pw.println("[Nivel2_Cliente3][Broadcast] " + msg);
                        }
                    }
                }
            }).start();

            while (true) {
                Socket cliente = servidor.accept();
                clientes.add(cliente);
                PrintWriter pw = new PrintWriter(cliente.getOutputStream(), true);
                escritores.add(pw);
                System.out.println("[Nivel2_Cliente3] Nueva conexión desde: " + cliente.getInetAddress());
                new Thread(new ManejadorCliente(cliente, pw)).start();
            }

        } catch (IOException e) {
            System.err.println("[Nivel2_Cliente3] Error servidor: " + e.getMessage());
        }
    }

    static class ManejadorCliente implements Runnable {
        private Socket socket;
        private PrintWriter salida;

        public ManejadorCliente(Socket socket, PrintWriter salida) {
            this.socket = socket;
            this.salida = salida;
        }

        @Override
        public void run() {
            try (BufferedReader entrada = new BufferedReader(new InputStreamReader(socket.getInputStream()))) {
                String mensaje;
                while ((mensaje = entrada.readLine()) != null) {
                    if (mensaje.equalsIgnoreCase("salir")) break;
                    System.out.println("[Nivel2_Cliente3] Recibido desde CasaMatriz: " + mensaje);
                    // opcional: responder
                    // salida.println("ACK desde Nivel2_Cliente3");
                }
            } catch (IOException e) {
                System.err.println("[Nivel2_Cliente3] Error con cliente: " + e.getMessage());
            } finally {
                escritores.remove(salida);
                clientes.remove(socket);
                System.out.println("[Nivel2_Cliente3] Cliente desconectado: " + socket.getInetAddress());
                try { socket.close(); } catch (IOException ignored) {}
            }
        }
    }
}