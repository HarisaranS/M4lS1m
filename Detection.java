// Detection.java
import java.net.*;
import java.io.*;
import javax.swing.*;

public class Detection {
    public static void main(String[] args) throws Exception {
        final int PORT = 8000;
        final String victimControlHost = "localhost";
        final int victimControlPort = PORT;

        ServerSocket ss = new ServerSocket(PORT);
        System.out.println("=== Detection === listening on port " + PORT);
        while (true) {
            Socket s = ss.accept();
            new Thread(() -> {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        System.out.println("[DETECT] " + line);

                        // Treat SIM-SHELL activity as HIGH severity
                        if (line.toUpperCase().contains("SIM-SHELL") || 
                            line.toUpperCase().contains("COMMAND:")) {
                            line = "[HIGH] Reverse shell activity detected → " + line;
                        }

                        // Popup for HIGH severity
                        if (line.toUpperCase().contains("HIGH")) {
                            final String detectedLine = line; // capture final copy
                            SwingUtilities.invokeLater(() -> {
                                int res = JOptionPane.showConfirmDialog(null,
                                        "HIGH severity detected:\n" + detectedLine + "\n\nBlock the attacker now?",
                                        "DETECTION ALERT", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                                if (res == JOptionPane.YES_OPTION) {
                                    try (Socket ctrl = new Socket(victimControlHost, victimControlPort);
                                         PrintWriter pw = new PrintWriter(ctrl.getOutputStream(), true)) {
                                        pw.println("BLOCK");
                                        System.out.println("[DETECT] Sent BLOCK command to victim control port.");
                                    } catch (Exception ex) {
                                        System.out.println("[DETECT] Failed to send BLOCK: " + ex.getMessage());
                                    }
                                } else {
                                    System.out.println("[DETECT] User chose to IGNORE.");
                                }
                            });
                        }
                    }
                } catch (IOException e) { }
            }).start();
        }
    }
}

