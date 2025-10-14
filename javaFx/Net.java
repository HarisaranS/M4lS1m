//WebSocket Connections 

import java.io.PrintWriter;
import java.net.Socket;

public class Net {
    public static void sendLine(String host, int port, String line) {
        try (Socket s = new Socket(host, port);
             PrintWriter pw = new PrintWriter(s.getOutputStream(), true)) {
            pw.println(line);
        } catch (Exception e) {
            System.out.println("[NET] failed to send to " + host + ":" + port + " : " + e.getMessage());
        }
    }
}
