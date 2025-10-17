**MemGuard** is an educational Java project that simulates a small Command & Control (C2) ecosystem with attacker, victim, and detection GUIs. It's intended for learning and demonstration only — not for real-world malicious activity.

## 1. Project Overview
MemGuard demonstrates common C2 concepts using Java Swing GUIs and Java sockets. The simulation includes:

- **AttackerPanel** — attacker control UI to send simulated commands.
- **VictimPanel** — victim client that executes simulations (file deletion, encryption, keylogging, popups, reverse shell simulation).
- **C2ServerPanel** — central server that accepts victim connections and relays attacker commands.
- **DetectionPanel** — simulated IDS/alerts UI.
- **ShellPanel / KeyloggingPanel** — interactive shell and keystroke capture display.
- **DBLogger** — writes logs to a MySQL database (optional).

All payloads are simulated for demonstration (e.g., encryption may be simple reversible transformation). Inspect the code before running and **never** run destructive routines on real systems.

---

## 2. Prerequisites
- Java JDK 17 or later (javac/java on PATH).
- Optional: MySQL or MariaDB if you want DB logging.
- Optional: MySQL JDBC driver (e.g. `mysql-connector-java-x.x.x.jar`) if using DBLogger.
- A safe test environment (VM, container, snapshot) — strongly recommended.

---

## 3. Build / Compile
From the project root where the `.java` files live:

```bash
# If you have a JDBC jar, set MYSQL_JAR to its path (optional)
# Example: MYSQL_JAR=usr/bin/mysql-connector-java-8.0.33.jar

# Compile all .java files (simple one-liner):
javac -cp ".:${MYSQL_JAR:-}" *.java
```

---

## 4. Run 
Open separate four terminals :

```bash
# 1) Start C2 server 
java -cp ".:${MYSQL_JAR:-}" C2Server
(or)
java -cp .:/usr/bin/java/mysql-connector-java-x.x.xx.jar C2Server

# 2) Start Detection GUI
java -cp ".:${MYSQL_JAR:-}" Detection
(or)
java -cp .:/usr/bin/java/mysql-connector-java-x.x.xx.jar Detection

# 3) Start Victim clients
java -cp ".:${MYSQL_JAR:-}" Victim
(or)
java -cp .:/usr/bin/java/mysql-connector-java-x.x.xx.jar Victim

# 4) Start Attacker GUI
java -cp ".:${MYSQL_JAR:-}" Attacker
(or)
java -cp .:/usr/bin/java/mysql-connector-java-x.x.xx.jar Attacker
```

## 5. DB (optional) — setup & configuration
If you plan to enable DB logging, create a dedicated DB and user with minimal privileges. Example MySQL commands:

```sql
CREATE DATABASE memguard;
CREATE USER 'memguard_user'@'localhost' IDENTIFIED BY 'StrongPasswordHere';
GRANT INSERT, SELECT, UPDATE ON memguard.* TO 'memguard_user'@'localhost';
FLUSH PRIVILEGES;
```

Edit `DBLogger.java` (or a config file if present) to set the JDBC URL, username and password, for example:

```java
String url = "jdbc:mysql://localhost:3306/memguard?serverTimezone=UTC";
String user = "memguard_user";
String pass = "StrongPasswordHere";
```

Then ensure the JDBC jar is on the classpath when compiling and running (see `MYSQL_JAR` usage above).

---

## . Default ports & configuration
- The repository often hardcodes a port constant in `C2Server.java` and `Victim.java`. Common example ports used in tutorials are **7000** or **8000**. **Always** open the Java files and verify the actual `PORT` constant used in your copy of the repo.

If you want to change the port, edit both the server and client source files (or better: add a configuration file or accept command-line args).

Example (if `C2Server` uses `PORT=7000`):
```java
private final int PORT = 7000;
```

---

## 8. Troubleshooting
- **No suitable driver found**: you must include the MySQL JDBC jar in the classpath when running a program that uses DBLogger. Example: `java -cp ".:./lib/mysql-connector-java-8.0.xx.jar" Victim`.
- **Port already in use**: change the port constant in `C2Server.java` and `Victim.java` to an unused port.
- **File permissions**: file-delete or encryption simulations may require the victim process to have read/write permissions. Run the victim with appropriate privileges inside a test folder.
- **GUI not showing / headless**: make sure your environment has X11/Wayland available (or run with a virtual display) if running the Swing GUIs remotely.
- **ClassNotFoundException / NoClassDefFoundError**: ensure compiled `.class` files are present and your `-cp` includes the project root.

---


## 10. Contributing
If you want to improve the project, consider:
- Adding a configuration file (properties or YAML) for ports, DB credentials, and file paths.
- Replacing hardcoded values with CLI args or config.
- Improving encryption simulation to show reversible secure operations (e.g., AES with a generated key and key-storage demonstration) — but _do not_ implement real ransomware behavior that could be abused.
- Adding unit tests for serialization and network message handling.

Create a fork, make changes, and open a PR with a clear description and test steps.

---

## 11. License
If the repository does not include a license, explicitly add one to clarify permitted uses. For educational/demo projects, common choices are MIT or Apache 2.0. Example `LICENSE` file content can be added at repo root.

---
