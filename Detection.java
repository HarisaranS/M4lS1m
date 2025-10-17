import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class DetectionGUI {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new DetectionGUI().start());
    }

    private JFrame frame;
    private JTextArea ta;
    private final int PORT = 8000;
    private final String victimHost = "localhost";
    private final int victimControlPort = 6200;

    private void start() {
        frame = new JFrame("Detection (GUI)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(600, 400);
        ta = new JTextArea();
        ta.setEditable(false);
        frame.add(new JScrollPane(ta), BorderLayout.CENTER);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        new Thread(this::runServer).start();
    }

    private void append(String s) {
        SwingUtilities.invokeLater(() -> ta.append(s + "\n"));
        DBLogger.log("DetectionGUI", s, "INFO");  
    }

    private void runServer() {
        try (ServerSocket ss = new ServerSocket(PORT)) {
            append("=== Detection === listening on port " + PORT);
            while (true) {
                Socket s = ss.accept();
                new Thread(() -> handleConn(s)).start();
            }
        } catch (IOException e) {
            append("[DETECT] error: " + e.getMessage());
        }
    }

    private void handleConn(Socket s) {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null) {
                final String copy = line;
                append("[DETECT] " + copy);

                // mark reverse shell as HIGH
                String check = copy.toUpperCase();
                String report = copy;
                if (check.contains("SIM-SHELL") || check.contains("COMMAND:")) {
                    report = "[HIGH] Reverse shell activity detected -> " + copy;
                    append(report);
                }

                if (report.toUpperCase().contains("HIGH")) {
                    final String alertText = report;
                    SwingUtilities.invokeLater(() -> {
                        int res = JOptionPane.showConfirmDialog(null,
                                "HIGH severity detected:\n" + alertText + "\n\nBlock the attacker now?",
                                "DETECTION ALERT", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
                        if (res == JOptionPane.YES_OPTION) {
                            try (Socket ctrl = new Socket(victimHost, victimControlPort);
                                 PrintWriter pw = new PrintWriter(ctrl.getOutputStream(), true)) {
                                pw.println("BLOCK");
                                append("[DETECT] Sent BLOCK command to victim control port.");
                            } catch (Exception ex) {
                                append("[DETECT] Failed to send BLOCK: " + ex.getMessage());
                            }
                        } else {
                            append("[DETECT] User chose to IGNORE.");
                        }
                    });
                }
            }
        } catch (IOException e) {
            append("[DETECT] conn error: " + e.getMessage());
        }
    }
}
