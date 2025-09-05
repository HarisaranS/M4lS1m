// C2Server.java 
import java.net.*;
import java.io.*;
import javax.swing.*;
import java.util.Timer;
import java.util.TimerTask;

public class C2Server {
    public static void main(String[] args) throws Exception {
        final int PORT = 7000;
        ServerSocket ss = new ServerSocket(PORT);
        System.out.println("=== C2 Server === listening on port " + PORT);

        while (true) {
            Socket s = ss.accept();
            System.out.println("[C2] Victim connected: " + s.getInetAddress());

            // --- Print SimShell connected only once ---
            System.out.println("[C2] Connected to SimShell");

            BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
            String line;
            while ((line = br.readLine()) != null) {
                System.out.println("[C2] " + line);

                String l = line.toLowerCase();
                if (l.contains("keylogger") || l.contains("botnet") || l.contains("ransomware")) {
                    final String capturedLine = line;
                    SwingUtilities.invokeLater(() -> {
                        JFrame f = new JFrame("C2 Update");
                        f.setSize(520, 200);
                        f.setAlwaysOnTop(true);
                        f.setLocationRelativeTo(null);
                        JTextArea ta = new JTextArea(capturedLine);
                        ta.setEditable(false);
                        f.add(new JScrollPane(ta));
                        f.setVisible(true);

                        new Timer().schedule(new TimerTask() {
                            public void run() { f.dispose(); }
                        }, 5000);
                    });
                }
            }
            br.close();
            s.close();
            System.out.println("[C2] Victim disconnected.");
        }
    }
}
