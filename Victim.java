import javax.swing.*;
import java.awt.*;
import java.awt.event.*; 
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;
import java.util.stream.*;

public class VictimGUI {
    private JFrame frame;
    private JTextArea log;
    private DefaultListModel<String> fileListModel;
    private boolean blocked = false;
    private final Path SANDBOX = Paths.get("safe_sandbox");
    private final int CMD_PORT = 6100;
    private final int CONTROL_PORT = 6200;
    private final int C2_PORT = 7000;
    private final int DETECT_PORT = 8000;
    private final String id = "VICTIM-1";

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new VictimGUI().start());
    }

    private void start() {
        frame = new JFrame("Victim Sandbox");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 500);
        frame.setLayout(new BorderLayout());

        log = new JTextArea();
        log.setEditable(false);
        fileListModel = new DefaultListModel<>();
        JList<String> fileList = new JList<>(fileListModel);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT,
                new JScrollPane(fileList), new JScrollPane(log));
        split.setDividerLocation(250);
        frame.add(split, BorderLayout.CENTER);

        JPanel top = new JPanel();
        JButton bRefresh = new JButton("Refresh Files");
        top.add(bRefresh);
        frame.add(top, BorderLayout.NORTH);

        bRefresh.addActionListener(e -> refreshFiles());

        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        
        ensureSamples();
        

        new Thread(this::cmdServer).start();
        new Thread(this::controlListener).start();
        new Thread(this::payloadListener).start();
    }

    private void log(String msg) {
        SwingUtilities.invokeLater(() -> log.append(msg + "\n"));
    }



    private void refreshFiles() {
    SwingUtilities.invokeLater(() -> {
        try {
            if (!Files.exists(SANDBOX)) Files.createDirectories(SANDBOX);

            Path infected = SANDBOX.resolve("infected");
            if (Files.exists(infected)) {
                try (Stream<Path> s = Files.list(infected)) {
                    s.filter(Files::isRegularFile).forEach(f -> {
                        try {
                            Files.deleteIfExists(SANDBOX.resolve(f.getFileName())); 
                            Files.move(f, SANDBOX.resolve(f.getFileName()), StandardCopyOption.REPLACE_EXISTING);
                        } catch (IOException e) {
                            log("[ERROR] restoring infected file: " + e.getMessage());
                        }
                    });
                }
            }

            for (int i = 1; i <= 4; i++) {
                Path p = SANDBOX.resolve("sample_" + i + ".txt");
                Files.writeString(p, "SAMPLE " + i + " -- confidential (SIM)", StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            }

            fileListModel.clear();
            try (Stream<Path> s = Files.list(SANDBOX)) {
                s.filter(Files::isRegularFile).forEach(p -> fileListModel.addElement(p.getFileName().toString()));
            }

            log("[VICTIM] Sandbox refreshed. All files restored to safe state.");

        } catch (Exception e) {
            fileListModel.addElement("[error: " + e.getMessage() + "]");
        }
    });
}


    private void sendC2(String msg) {
        sendLine(C2_PORT, "[" + id + "] " + msg);
        DBLogger.log("Victim", msg, "INFO");
    }

    private void sendDetect(String msg, String sev) {
        sendLine(DETECT_PORT, "[" + id + "] " + msg + " | " + sev);
        DBLogger.log("Detection", msg, sev);
    }

    private void sendLine(int port, String msg) {
        try (Socket s = new Socket("localhost", port);
             PrintWriter pw = new PrintWriter(s.getOutputStream(), true)) {
            pw.println(msg);
        } catch (Exception ignored) {}
    }

    private void controlListener() {
        try (ServerSocket ss = new ServerSocket(CONTROL_PORT)) {
            log("[VICTIM] Listening for control on port " + CONTROL_PORT);
            while (true) {
                Socket s = ss.accept();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                    String line = br.readLine();
                    if ("BLOCK".equalsIgnoreCase(line)) {
                        blocked = true;
                        log("[BLOCKED] Received BLOCK command!");
                        sendDetect("Victim blocked by Detection", "HIGH");
                    }
                }
            }
        } catch (Exception e) {
            log("[ERROR] Control listener: " + e.getMessage());
        }
    }

    private void runVirus() {
        sendC2("Payload=VIRUS received");
        sendDetect("VIRUS simulation started", "HIGH");
        List<String> files = listSandboxFiles();
        for (String f : files) {
            if (blocked) { log("[VIRUS] stopped"); sendC2("VIRUS stopped"); return; }
            moveToInfected(f);
            sendC2("VIRUS moved: " + f);
            sendDetect("VIRUS moved " + f, "HIGH");
            log("[VIRUS]" + f + " → infected");
            sleep(500);
        }
       
    }

    private void runRansomware() {
        sendC2("Payload=RANSOMWARE received");
        sendDetect("RANSOMWARE simulation started", "HIGH");
        List<String> files = listSandboxFiles();
        String lastHash = "N/A";
        for (String f : files) {
            if (blocked) { log("[RANSOMWARE] stopped"); sendC2("RANSOMWARE stopped"); return; }
            try {
                lastHash = xorEncryptFileAndHash(f);
                sendC2("RANSOMWARE encrypted " + f + " sha256=" + lastHash);
                sendDetect("RANSOMWARE encrypted " + f, "HIGH");
                log("[RANSOMWARE] Encrypted " + f);
                sleep(600);
            } catch (Exception e) { log("[ERROR] " + e.getMessage()); }
        }
        
        showRansomNote(lastHash);
    }

    private void runKeylogger() {
        sendC2("Payload=KEYLOGGER started");
        sendDetect("KEYLOGGER started", "MEDIUM");
        log("[KEYLOGGER] GUI input opened (type here). Use ENDKL to stop line mode, ENDKL_LINE to stop per-char mode.");

        SwingUtilities.invokeLater(() -> {
            final JDialog d = new JDialog(frame, "Keylogger Input (GUI)", false);
            d.setSize(700, 380);
            d.setLayout(new BorderLayout());

            JTextArea taInput = new JTextArea();
            taInput.setLineWrap(true);
            taInput.setWrapStyleWord(true);
            JScrollPane sp = new JScrollPane(taInput);
            d.add(sp, BorderLayout.CENTER);

            JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
            JButton btnSendLine = new JButton("Send Line");
            JToggleButton btnPerChar = new JToggleButton("Per-char: OFF");
            JButton btnClose = new JButton("Close");
            bottom.add(btnSendLine);
            bottom.add(btnPerChar);
            bottom.add(btnClose);
            d.add(bottom, BorderLayout.SOUTH);

            btnPerChar.addActionListener(ev -> {
                if (btnPerChar.isSelected()) {
                    btnPerChar.setText("Per-char: ON");
                } else {
                    btnPerChar.setText("Per-char: OFF");
                }
            });

            taInput.addKeyListener(new KeyAdapter() {
                @Override
                public void keyTyped(KeyEvent e) {
                    if (!btnPerChar.isSelected()) return;
                    char c = e.getKeyChar();
                    new Thread(() -> {
                        sendC2("KEYLOGGER char: [" + printableChar(c) + "]");
                        sendDetect("KEYLOGGER char captured", "MEDIUM");
                    }).start();
                    if (blocked) sendC2("KEYLOGGER blocked");
                }
            });

            btnSendLine.addActionListener(ev -> {
                final String text = taInput.getText();
                if (text == null || text.isEmpty()) return;
                new Thread(() -> {
                    for (char c : text.toCharArray()) {
                        sendC2("KEYLOGGER char: [" + printableChar(c) + "]");
                        sendDetect("KEYLOGGER char captured", "MEDIUM");
                        if (blocked) { sendC2("KEYLOGGER blocked"); break; }
                        try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                    }
                    String trimmed = text.trim();
                    if ("ENDKL".equalsIgnoreCase(trimmed)) {
                        sendC2("KEYLOGGER: stopped line mode");
                        SwingUtilities.invokeLater(d::dispose);
                    } else if ("ENDKL_LINE".equalsIgnoreCase(trimmed)) {
                        sendC2("KEYLOGGER: stopped per-char mode");
                        SwingUtilities.invokeLater(d::dispose);
                    }
                }).start();
            });

            btnClose.addActionListener(ev -> {
                sendC2("KEYLOGGER: GUI closed by user");
                d.dispose();
            });

            d.setLocationRelativeTo(frame);
            d.setVisible(true);
        });
    }

    private String printableChar(char c) {
        if (c == '\n' || c == '\r') return "\\n";
        if (Character.isISOControl(c)) return String.format("0x%02x", (int)c);
        return Character.toString(c);
    }

    private void runBotnet() {
        sendC2("Payload=BOTNET received");
        sendDetect("BOTNET simulation started", "HIGH");
        new Thread(() -> {
            for (int i = 1; i <= 10; i++) {
                if (blocked) { sendC2("BOTNET stopped"); return; }
                int idx = i;
                SwingUtilities.invokeLater(() -> {
                    JFrame f = new JFrame("Botnet Task");
                    f.setSize(300, 100);
                    f.setAlwaysOnTop(true);
                    JLabel l = new JLabel("Botnet task executed #" + idx, SwingConstants.CENTER);
                    f.add(l);
                    f.setVisible(true);
                    new java.util.Timer().schedule(new java.util.TimerTask() {
                        public void run() { f.dispose(); }
                    }, 900);
                });
                sendC2("BOTNET task executed #" + i);
                sendDetect("BOTNET task #" + i, "HIGH");
                log("[BOTNET] task #" + i + " executed");
                sleep(1500);
            }
            sendC2("BOTNET finished");
        }).start();
    }

    private List<String> listSandboxFiles() {
        try (Stream<Path> s = Files.list(SANDBOX)) {
            return s.filter(Files::isRegularFile).map(p -> p.getFileName().toString()).toList();
        } catch (IOException e) { return List.of(); }
    }

    private void ensureSamples() {
        try {
            if (!Files.exists(SANDBOX)) Files.createDirectories(SANDBOX);
            for (int i = 1; i <= 4; i++) {
                Path p = SANDBOX.resolve("sample_" + i + ".txt");
                if (!Files.exists(p))
                    Files.writeString(p, "SAMPLE " + i + " -- confidential (SIM)");
            }
        } catch (IOException e) { log("[ERROR] ensureSamples: " + e.getMessage()); }
    }

    private boolean moveToInfected(String fname) {
        try {
            Path f = SANDBOX.resolve(fname);
            Path inf = SANDBOX.resolve("infected");
            if (!Files.exists(inf)) Files.createDirectories(inf);
            Files.move(f, inf.resolve(fname), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) { return false; }
    }

    private String xorEncryptFileAndHash(String fname) throws Exception {
        Path p = SANDBOX.resolve(fname);
        byte[] data = Files.readAllBytes(p);
        for (int i = 0; i < data.length; i++) data[i] ^= 0x5A;
        Files.write(p, data);
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private void showRansomNote(String hash) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame();
            f.setUndecorated(true);
            f.setExtendedState(JFrame.MAXIMIZED_BOTH);
            f.setBackground(Color.BLACK);
            JLabel l = new JLabel("<html><center><div style='color:red;font-size:40px;'>YOUR FILES ARE ENCRYPTED (SIM)</div>"
                    + "<div style='color:white;font-size:20px;'>SHA-256: " + hash + "</div>"
                    + "<div style='color:yellow;font-size:18px;'>Pay ransom to 1FakeBTCAddr</div></center></html>",
                    SwingConstants.CENTER);
            f.add(l);
            f.setVisible(true);
        });
    }

    private void sleep(int ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }

    private void cmdServer() {
        try (ServerSocket ss = new ServerSocket(CMD_PORT)) {
            log("[VICTIM] Command server on port " + CMD_PORT);
            while (true) {
                Socket s = ss.accept();
                new Thread(() -> {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
                         PrintWriter pw = new PrintWriter(s.getOutputStream(), true)) {
                        pw.println("SIM-SHELL connected. Allowed: ls, read, write,copy,delete,hash ,mkdir, simulate, refresh, block, listdir ,exit");
                        String line;
                        while ((line = br.readLine()) != null) {
                            String resp = handleCmd(line.trim());
                            pw.println(resp);
                            if ("bye".equals(resp)) break; 
                        }
                    } catch (Exception e) {
                        log("[VICTIM] cmd conn error: " + e.getMessage());
                        StringWriter sw = new StringWriter();
                        e.printStackTrace(new PrintWriter(sw));
                        log(sw.toString());
                    } finally {
                        try { s.close(); } catch (IOException ignored) {}
                        log("[VICTIM] Sim-shell connection closed (server side).");
                    }
                }).start();
            }
        } catch (Exception e) {
            log("[ERROR] cmdServer: " + e.getMessage());
        }
    }


   private String handleCmd(String cmdLine) {
    try {
        String[] parts = cmdLine.split(" ", 3);
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "ls":
                return String.join(", ", listSandboxFiles());
            case "read":
                if (parts.length < 2) return "Missing filename";
                return Files.readString(SANDBOX.resolve(parts[1]));
            case "write":
                if (parts.length < 3) return "Usage: write <file> <text>";
                Files.writeString(SANDBOX.resolve(parts[1]), parts[2], StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                return "Written to " + parts[1];
            case "simulate":
                if (parts.length < 2) return "Missing payload type";
                switch (parts[1].toLowerCase()) {
                    case "virus" -> new Thread(this::runVirus).start();
                    case "ransomware" -> new Thread(this::runRansomware).start();
                    case "keylogger" -> new Thread(this::runKeylogger).start();
                    case "botnet" -> new Thread(this::runBotnet).start();
                    default -> { return "Unknown payload"; }
                }
                return "Simulating " + parts[1];
            case "exit":
                return "bye";
            case "delete":
                if (parts.length < 2) return "Missing filename";
                Path del = SANDBOX.resolve(parts[1]);
                if (Files.exists(del)) { Files.delete(del); return "Deleted " + parts[1]; }
                else return "File not found";
            case "copy":
                if (parts.length < 3) return "Usage: copy <src> <dest>";
                Path src = SANDBOX.resolve(parts[1]);
                Path dest = SANDBOX.resolve(parts[2]);
                if (Files.exists(src)) { Files.copy(src, dest, StandardCopyOption.REPLACE_EXISTING); return "Copied to " + parts[2]; }
                else return "Source file not found";
            case "hash":
                if (parts.length < 2) return "Missing filename";
                Path f = SANDBOX.resolve(parts[1]);
                if (!Files.exists(f)) return "File not found";
                MessageDigest md = MessageDigest.getInstance("SHA-256");
                byte[] h = md.digest(Files.readAllBytes(f));
                StringBuilder sb = new StringBuilder();
                for (byte b : h) sb.append(String.format("%02x", b));
                return sb.toString();
            case "mkdir":
                if (parts.length < 2) return "Missing directory name";
                Path dir = SANDBOX.resolve(parts[1]);
                if (!Files.exists(dir)) Files.createDirectories(dir);
                return "Directory created: " + parts[1];
            case "refresh":
                refreshFiles();
                return "Sandbox refreshed";
            case "block":
                blocked = true;
                return "Victim blocked!";
            case "listdir":
                if (parts.length < 2) return "Missing directory name";
                Path ldir = SANDBOX.resolve(parts[1]);
                if (!Files.exists(ldir) || !Files.isDirectory(ldir)) return "Directory not found";
                try (Stream<Path> s = Files.list(ldir)) {
                    return s.map(pth -> pth.getFileName().toString()).reduce((a,b) -> a + ", " + b).orElse("Empty");
                }
            default:
                return "Unknown command";
        }
    } catch (Exception e) { 
        return "error: " + e.getMessage(); 
    }
}


    private final int PAYLOAD_PORT = 6000;

    private void payloadListener() {
        try (ServerSocket ss = new ServerSocket(PAYLOAD_PORT)) {
            log("[VICTIM] Payload listener on port " + PAYLOAD_PORT);
            while (true) {
                Socket s = ss.accept();
                new Thread(() -> {
                    try (ObjectInputStream ois = new ObjectInputStream(s.getInputStream())) {
                        Object o = ois.readObject();
                        if (o instanceof Payload p) {
                            log("[VICTIM] received payload: " + p);
                            switch (p.type.toUpperCase()) {
                                case "VIRUS" -> new Thread(this::runVirus).start();
                                case "RANSOMWARE" -> new Thread(this::runRansomware).start();
                                case "KEYLOGGER" -> new Thread(this::runKeylogger).start();
                                case "BOTNET" -> new Thread(this::runBotnet).start();
                                default -> log("[VICTIM] unknown payload type: " + p.type);
                            }
                        } else {
                            log("[VICTIM] unknown object received");
                        }
                    } catch (Exception e) {
                        log("[VICTIM] payload error: " + e.getMessage());
                    }
                }).start();
            }
        } catch (Exception e) {
            log("[VICTIM] payloadListener error: " + e.getMessage());
        }
    }

}

