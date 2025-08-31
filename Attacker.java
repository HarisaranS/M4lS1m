// Attacker.java
import java.net.*;
import java.io.*;
import java.util.*;

public class Attacker {
    private static final int VICTIM_PORT = 6000;
    private static final int VICTIM_CMD_PORT = 6100; // safe shell

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        while (true) {
            System.out.println("\nATTACKER MENU: 1=VIRUS 2=RANSOMWARE 3=KEYLOGGER 4=BOTNET 5=SIM-SHELL 0=EXIT");
            int c = sc.nextInt(); sc.nextLine();
            if (c == 0) break;
            switch (c) {
                case 1: sendPayload("VIRUS"); break;
                case 2: sendPayload("RANSOMWARE"); break;
                case 3: sendPayload("KEYLOGGER"); break;
                case 4: sendPayload("BOTNET"); break;
                case 5: openSimShell(); break;
                default: System.out.println("invalid");
            }
        }
    }

    private static void sendPayload(String type) {
        try (Socket s = new Socket("localhost", VICTIM_PORT);
             ObjectOutputStream oos = new ObjectOutputStream(s.getOutputStream())) {
            Payload p = new Payload(type, type.toLowerCase() + "_payload");
            oos.writeObject(p);
            System.out.println("[ATTACKER] sent " + p);
        } catch (IOException e) {
            System.out.println("[ATTACKER] failed to send payload: " + e.getMessage());
        }
    }

    private static void openSimShell() {
        System.out.println("[ATTACKER] connecting to simulated victim shell (allowed cmds: ls, read <file>, write <file> <text>, whoami, exit)");
        try (Socket s = new Socket("localhost", VICTIM_CMD_PORT);
             BufferedReader br = new BufferedReader(new InputStreamReader(s.getInputStream()));
             BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
             PrintWriter pw = new PrintWriter(s.getOutputStream(), true)) {

            // reader thread to print responses
            new Thread(() -> {
                try {
                    String l;
                    while ((l = br.readLine()) != null) {
                        System.out.println("[VICTIM] " + l);
                    }
                } catch (IOException ex) {}
            }).start();

            String cmd;
            while ((cmd = in.readLine()) != null) {
                pw.println(cmd);
                if ("exit".equalsIgnoreCase(cmd)) break;
            }
        } catch (IOException e) {
            System.out.println("[ATTACKER] shell error: " + e.getMessage());
        }
    }
}

