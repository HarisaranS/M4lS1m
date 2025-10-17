import java.sql.*;

public class DBLogger {
    private static final String URL = "jdbc:mysql://localhost:3306/cyber_sim";
    private static final String USER = "<username>";
    private static final String PASS = "<Password>";

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (Exception e) {
            System.out.println("[DB] Driver load error: " + e.getMessage());
        }
    }

    public static void log(String source, String message, String severity) {
        String sql = "INSERT INTO logs (source, message, severity) VALUES (?, ?, ?)";
        try (Connection conn = DriverManager.getConnection(URL, USER, PASS);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, source);
            ps.setString(2, message);
            ps.setString(3, severity);
            ps.executeUpdate();
        } catch (SQLException e) {
            System.out.println("[DB] Error: " + e.getMessage());
        }
    }
}

