import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.*;
import java.net.Socket;

public class AttackerGUI {
    private final int VICTIM_PORT = 6000;
    private final int VICTIM_CMD_PORT = 6100;

    private JFrame frame;
    private JTextArea ta;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new AttackerGUI().buildAndShow());
    }

    private void buildAndShow() {
        frame = new JFrame("Attacker Console (GUI)");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(700, 480);
        frame.setLayout(new BorderLayout());

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton virus = new JButton("Send VIRUS");
        JButton ransomware = new JButton("Send RANSOMWARE");
        JButton keylogger = new JButton("Send KEYLOGGER");
        JButton botnet = new JButton("Send BOTNET");
        JButton simShell = new JButton("Open SIM-SHELL");

        top.add(virus); top.add(ransomware); top.add(keylogger); top.add(botnet); top.add(simShell);
        frame.add(top, BorderLayout.NORTH);

        ta = new JTextArea(); ta.setEditable(false);
        frame.add(new JScrollPane(ta), BorderLayout.CENTER);

        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JTextField custom = new JTextField(30);
        JButton sendCustom = new JButton("Send custom payload (type)");
        bottom.add(custom);
        bottom.add(sendCustom);
        frame.add(bottom, BorderLayout.SOUTH);

        virus.addActionListener(e -> sendPayload("VIRUS"));
        ransomware.addActionListener(e -> sendPayload("RANSOMWARE"));
        keylogger.addActionListener(e -> sendPayload("KEYLOGGER"));
        botnet.addActionListener(e -> sendPayload("BOTNET"));
        simShell.addActionListener(e -> openSimShellWindow());
        sendCustom.addActionListener(e -> {
            String t = custom.getText().trim();
            if (!t.isEmpty()) sendPayload(t);
        });

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }

    private void append(String s) {
        SwingUtilities.invokeLater(() -> ta.append(s + "\n"));
    }

    private void sendPayload(String type) {
        new Thread(() -> {
            try (Socket s = new Socket("localhost", VICTIM_PORT);
                 ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream())) {
                Payload p = new Payload(type, type.toLowerCase() + "_payload");
                oos.writeObject(p);
                append("[ATTACKER] sent " + p);
            } catch (IOException e) {
                append("[ATTACKER] failed to send payload: " + e.getMessage());
            }
        }).start();
    }

    private void openSimShellWindow() {
        JFrame f = new JFrame("SIM-SHELL (Attacker)");
        f.setSize(700, 420);
        f.setLayout(new BorderLayout());

        JTextArea out = new JTextArea();
        out.setEditable(false);
        JScrollPane sp = new JScrollPane(out);
        f.add(sp, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout());
        JTextField input = new JTextField();
        bottom.add(input, BorderLayout.CENTER);
        f.add(bottom, BorderLayout.SOUTH);

        f.setLocationRelativeTo(null);
        f.setVisible(true);

        
        final Socket[] sockHolder = new Socket[1];
        final BufferedReader[] brHolder = new BufferedReader[1];
        final PrintWriter[] pwHolder = new PrintWriter[1];

        Thread connThread = new Thread(() -> {
            try {
                sockHolder[0] = new Socket("localhost", VICTIM_CMD_PORT);
                brHolder[0] = new BufferedReader(new InputStreamReader(sockHolder[0].getInputStream()));
                pwHolder[0] = new PrintWriter(sockHolder[0].getOutputStream(), true);


                Thread reader = new Thread(() -> {
                    try {
                        String l;
                        while ((l = brHolder[0].readLine()) != null) {
                            final String line = l;
                            SwingUtilities.invokeLater(() -> out.append("[VICTIM] " + line + "\n"));
                        }
                    } catch (IOException ex) {
                        SwingUtilities.invokeLater(() -> out.append("[SIM-SHELL] connection closed\n"));
                    }
                });
                reader.start();


                input.addActionListener(ev -> {
                    String cmd = input.getText();
                    if (pwHolder[0] != null) {
                        pwHolder[0].println(cmd);
                    }
                    input.setText("");
                    if ("exit".equalsIgnoreCase(cmd)) {
                        try {
                            if (sockHolder[0] != null) sockHolder[0].close();
                        } catch (IOException ignored) {}
                        f.dispose();
                    }
                });

            } catch (IOException e) {
                SwingUtilities.invokeLater(() -> out.append("[ATTACKER] shell error: " + e.getMessage() + "\n"));
            }
        });
        connThread.start();

        f.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                try { if (sockHolder[0] != null) sockHolder[0].close(); } catch (IOException ignored) {}
            }
        });
    }
}
