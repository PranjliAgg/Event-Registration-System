import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

public class DatabaseReset {
    public static void main(String[] args) {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            Connection conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/", "root", "root"
            );
            
            Statement stmt = conn.createStatement();
            try {
                stmt.execute("DROP DATABASE IF EXISTS event_registration_db");
                System.out.println("✓ Database dropped successfully");
            } catch (Exception e) {
                System.out.println("Info: " + e.getMessage());
            }
            
            stmt.close();
            conn.close();
            System.out.println("Database will be recreated on next app launch.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
