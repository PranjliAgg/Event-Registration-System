package com.eventregistration.db;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Database initializer that ensures the schema is created on first run.
 */
public class DatabaseInitializer {

    /**
     * Initialize the database by executing the schema SQL.
     * This includes creating tables, triggers, and stored procedures.
     */
    public static void initialize() {
        System.out.println("[DB INIT] Starting database initialization...");
        Connection conn = DatabaseConnection.getInstance().getConnection();
        if (conn == null) {
            System.err.println("[DB INIT] Failed: No database connection");
            return;
        }
        System.out.println("[DB INIT] ✓ Got database connection");

        try {
            // Check if the database exists first
            if (!databaseExists(conn)) {
                System.out.println("[DB INIT] Database doesn't exist, creating...");
                createDatabase(conn);
            }

            // Switch to the database
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("USE event_registration_db");
            }
            System.out.println("[DB INIT] ✓ Using event_registration_db");

            // If tables already exist, still run schema migrations and sample data refresh
            if (tablesExist(conn)) {
                System.out.println("[DB INIT] Database schema already initialized. Running migrations and seed updates...");
            } else {
                System.out.println("[DB INIT] Initializing database schema...");
            }

            executeSchemaSQL(conn);
            System.out.println("[DB INIT] ✓ Database initialization complete!");

        } catch (SQLException e) {
            System.err.println("[DB INIT] Error during initialization: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static boolean databaseExists(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT 1 FROM information_schema.SCHEMATA WHERE SCHEMA_NAME = 'event_registration_db'");
            return stmt.getResultSet().next();
        } catch (SQLException e) {
            return false;
        }
    }

    private static void createDatabase(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE DATABASE event_registration_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            System.out.println("[DB INIT] ✓ Database created");
        }
    }

    private static boolean tablesExist(Connection conn) {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SELECT 1 FROM information_schema.TABLES WHERE TABLE_SCHEMA = 'event_registration_db' AND TABLE_NAME = 'USERS' LIMIT 1");
            return stmt.getResultSet().next();
        } catch (SQLException e) {
            return false;
        }
    }

    private static void executeSchemaSQL(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Create tables first
            createTables(stmt);
            // Then create stored procedures
            createStoredProcedures(conn);
            // Then insert sample data
            insertSampleData(stmt);
        }
    }

    private static void createTables(Statement stmt) throws SQLException {
        String[] tableSQL = {
            "CREATE TABLE IF NOT EXISTS EVENT_CATEGORY (category_id INT AUTO_INCREMENT PRIMARY KEY, category_name VARCHAR(100) NOT NULL UNIQUE)",
            "CREATE TABLE IF NOT EXISTS VENUE (venue_id INT AUTO_INCREMENT PRIMARY KEY, venue_name VARCHAR(150) NOT NULL, location VARCHAR(255) NOT NULL, capacity INT NOT NULL CHECK (capacity > 0))",
            "CREATE TABLE IF NOT EXISTS USERS (user_id INT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(100) NOT NULL, email VARCHAR(150) NOT NULL UNIQUE, phone VARCHAR(15), password VARCHAR(255) NOT NULL, role ENUM('admin','user') NOT NULL DEFAULT 'user')",
            "CREATE TABLE IF NOT EXISTS EVENTS (event_id INT AUTO_INCREMENT PRIMARY KEY, event_name VARCHAR(200) NOT NULL UNIQUE, event_date DATE NOT NULL, total_seats INT NOT NULL CHECK (total_seats > 0), available_seats INT NOT NULL, registration_deadline DATE NOT NULL, status ENUM('upcoming','ongoing','completed','cancelled') NOT NULL DEFAULT 'upcoming', description TEXT, price DECIMAL(10,2) DEFAULT 0, category_id INT NOT NULL, venue_id INT NOT NULL, CONSTRAINT fk_event_category FOREIGN KEY (category_id) REFERENCES EVENT_CATEGORY(category_id), CONSTRAINT fk_event_venue FOREIGN KEY (venue_id) REFERENCES VENUE(venue_id), CONSTRAINT chk_seats CHECK (available_seats <= total_seats AND available_seats >= 0), CONSTRAINT chk_deadline CHECK (registration_deadline <= event_date))",
            "CREATE TABLE IF NOT EXISTS REGISTRATIONS (reg_id INT AUTO_INCREMENT PRIMARY KEY, user_id INT NOT NULL, event_id INT NOT NULL, registration_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, status ENUM('pending','confirmed','cancelled','waitlisted') NOT NULL DEFAULT 'pending', CONSTRAINT fk_reg_user FOREIGN KEY (user_id) REFERENCES USERS(user_id), CONSTRAINT fk_reg_event FOREIGN KEY (event_id) REFERENCES EVENTS(event_id), CONSTRAINT uq_user_event UNIQUE (user_id, event_id))",
            "CREATE TABLE IF NOT EXISTS PAYMENTS (payment_id INT AUTO_INCREMENT PRIMARY KEY, user_id INT NOT NULL, event_id INT NOT NULL, amount DECIMAL(10,2) NOT NULL CHECK (amount >= 0), payment_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, status ENUM('pending','completed','failed','refunded') NOT NULL DEFAULT 'pending', payment_mode ENUM('cash','card','upi','netbanking','free') NOT NULL, CONSTRAINT fk_pay_user FOREIGN KEY (user_id) REFERENCES USERS(user_id), CONSTRAINT fk_pay_event FOREIGN KEY (event_id) REFERENCES EVENTS(event_id))",
            "CREATE TABLE IF NOT EXISTS EVENT_SCHEDULE (schedule_id INT AUTO_INCREMENT PRIMARY KEY, event_id INT NOT NULL, session_name VARCHAR(200) NOT NULL, start_time DATETIME NOT NULL, end_time DATETIME NOT NULL, CONSTRAINT fk_sched_event FOREIGN KEY (event_id) REFERENCES EVENTS(event_id), CONSTRAINT chk_time CHECK (end_time > start_time))",
            "CREATE TABLE IF NOT EXISTS AUDIT_LOG (log_id INT AUTO_INCREMENT PRIMARY KEY, action VARCHAR(100) NOT NULL, table_name VARCHAR(100) NOT NULL, record_id INT, details TEXT, changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP)"
        };

        for (String sql : tableSQL) {
            try {
                stmt.execute(sql);
            } catch (SQLException e) {
                System.out.println("[DB INIT] Info: " + e.getMessage());
            }
        }

        // Migrate existing schema: ensure price column exists in EVENTS
        try (ResultSet rs = stmt.executeQuery("SHOW COLUMNS FROM EVENTS LIKE 'price'")) {
            if (!rs.next()) {
                stmt.execute("ALTER TABLE EVENTS ADD COLUMN price DECIMAL(10,2) DEFAULT 0");
                System.out.println("[DB INIT] ✓ Added EVENTS.price column.");
            } else {
                System.out.println("[DB INIT] EVENTS.price already exists.");
            }
        } catch (SQLException e) {
            System.out.println("[DB INIT] Info: " + e.getMessage());
        }

        // Migrate existing schema: ensure event_name is unique, repairing duplicates with child references first
        boolean eventNameUnique = false;
        try (ResultSet rs = stmt.executeQuery("SHOW INDEX FROM EVENTS WHERE Key_name='uq_event_name'")) {
            eventNameUnique = rs.next();
        } catch (SQLException e) {
            System.out.println("[DB INIT] Info: " + e.getMessage());
        }

        if (!eventNameUnique) {
            try {
                repairDuplicateEvents(stmt.getConnection());
                try (Statement alterStmt = stmt.getConnection().createStatement()) {
                    alterStmt.execute("ALTER TABLE EVENTS ADD CONSTRAINT uq_event_name UNIQUE (event_name)");
                    System.out.println("[DB INIT] ✓ Added unique constraint on EVENTS.event_name");
                }
            } catch (SQLException x) {
                System.out.println("[DB INIT] Warning: could not apply unique constraint: " + x.getMessage());
            }
        } else {
            System.out.println("[DB INIT] EVENTS.event_name uniqueness already enforced.");
        }

        // Repair duplicates in category and venue tables where they may have been inserted repeatedly.
        repairDuplicateCategories(stmt.getConnection());
        repairDuplicateVenues(stmt.getConnection());
    }

    private static void repairDuplicateEvents(Connection conn) throws SQLException {
        System.out.println("[DB INIT] Repairing duplicate EVENTS by event_name");

        String findDuplicates = "SELECT event_name, MIN(event_id) AS keep_id, GROUP_CONCAT(event_id SEPARATOR ',') AS ids, COUNT(*) AS cnt " +
                                "FROM EVENTS GROUP BY event_name HAVING COUNT(*) > 1";

        class DuplicateGroup {
            final String eventName;
            final int keepId;
            final String dupIds;

            DuplicateGroup(String eventName, int keepId, String dupIds) {
                this.eventName = eventName;
                this.keepId = keepId;
                this.dupIds = dupIds;
            }
        }

        java.util.List<DuplicateGroup> duplicates = new java.util.ArrayList<>();

        try (Statement queryStmt = conn.createStatement(); ResultSet rs = queryStmt.executeQuery(findDuplicates)) {
            while (rs.next()) {
                String eventName = rs.getString("event_name");
                int keepId = rs.getInt("keep_id");
                String ids = rs.getString("ids");

                String[] allIds = ids.split(",");
                StringBuilder dupIdsBuilder = new StringBuilder();
                for (String id : allIds) {
                    int eventId = Integer.parseInt(id.trim());
                    if (eventId == keepId) continue;
                    if (dupIdsBuilder.length() > 0) dupIdsBuilder.append(",");
                    dupIdsBuilder.append(eventId);
                }

                if (dupIdsBuilder.length() > 0) {
                    duplicates.add(new DuplicateGroup(eventName, keepId, dupIdsBuilder.toString()));
                }
            }
        }

        for (DuplicateGroup group : duplicates) {
            try (Statement stmt = conn.createStatement()) {
                // Remove conflicting registrations/payments that would violate unique constraints
                stmt.executeUpdate("DELETE r FROM REGISTRATIONS r " +
                        "JOIN REGISTRATIONS r2 ON r.user_id = r2.user_id AND r2.event_id = " + group.keepId + " " +
                        "WHERE r.event_id IN (" + group.dupIds + ")");
                stmt.executeUpdate("DELETE p FROM PAYMENTS p " +
                        "JOIN PAYMENTS p2 ON p.user_id = p2.user_id AND p2.event_id = " + group.keepId + " " +
                        "WHERE p.event_id IN (" + group.dupIds + ")");

                stmt.executeUpdate("UPDATE REGISTRATIONS SET event_id = " + group.keepId + " WHERE event_id IN (" + group.dupIds + ")");
                stmt.executeUpdate("UPDATE PAYMENTS SET event_id = " + group.keepId + " WHERE event_id IN (" + group.dupIds + ")");
                stmt.executeUpdate("DELETE FROM EVENTS WHERE event_id IN (" + group.dupIds + ")");

                System.out.println("[DB INIT] ✓ Consolidated duplicates for event '" + group.eventName + "' to event_id=" + group.keepId);
            } catch (SQLException each) {
                System.out.println("[DB INIT] Warning: failed to consolidate duplicates for '" + group.eventName + "' -> " + each.getMessage());
            }
        }

        if (duplicates.isEmpty()) {
            System.out.println("[DB INIT] No duplicate events found to consolidate.");
        }
    }

    private static void repairDuplicateCategories(Connection conn) throws SQLException {
        System.out.println("[DB INIT] Repairing duplicate EVENT_CATEGORY by category_name");

        String findDuplicates = "SELECT category_name, MIN(category_id) AS keep_id, GROUP_CONCAT(category_id SEPARATOR ',') AS ids " +
                                "FROM EVENT_CATEGORY GROUP BY category_name HAVING COUNT(*) > 1";

        try (Statement queryStmt = conn.createStatement(); ResultSet rs = queryStmt.executeQuery(findDuplicates)) {
            while (rs.next()) {
                String categoryName = rs.getString("category_name");
                int keepId = rs.getInt("keep_id");
                String ids = rs.getString("ids");
                String[] allIds = ids.split(",");

                StringBuilder dupIdsBuilder = new StringBuilder();
                for (String id : allIds) {
                    int categoryId = Integer.parseInt(id.trim());
                    if (categoryId == keepId) continue;
                    if (dupIdsBuilder.length() > 0) dupIdsBuilder.append(",");
                    dupIdsBuilder.append(categoryId);
                }

                if (dupIdsBuilder.length() == 0) continue;
                String dupIds = dupIdsBuilder.toString();

                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("UPDATE EVENTS SET category_id = " + keepId + " WHERE category_id IN (" + dupIds + ")");
                    stmt.executeUpdate("DELETE FROM EVENT_CATEGORY WHERE category_id IN (" + dupIds + ")");
                    System.out.println("[DB INIT] ✓ Consolidated duplicate category '" + categoryName + "' to id=" + keepId);
                }
            }
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE EVENT_CATEGORY ADD UNIQUE INDEX uq_category_name (category_name)");
            System.out.println("[DB INIT] ✓ Added unique constraint on EVENT_CATEGORY.category_name");
        } catch (SQLException e) {
            System.out.println("[DB INIT] Info: " + e.getMessage());
        }
    }

    private static void repairDuplicateVenues(Connection conn) throws SQLException {
        System.out.println("[DB INIT] Repairing duplicate VENUE by venue_name");

        String findDuplicates = "SELECT venue_name, MIN(venue_id) AS keep_id, GROUP_CONCAT(venue_id SEPARATOR ',') AS ids " +
                                "FROM VENUE GROUP BY venue_name HAVING COUNT(*) > 1";

        try (Statement queryStmt = conn.createStatement(); ResultSet rs = queryStmt.executeQuery(findDuplicates)) {
            while (rs.next()) {
                String venueName = rs.getString("venue_name");
                int keepId = rs.getInt("keep_id");
                String ids = rs.getString("ids");
                String[] allIds = ids.split(",");

                StringBuilder dupIdsBuilder = new StringBuilder();
                for (String id : allIds) {
                    int venueId = Integer.parseInt(id.trim());
                    if (venueId == keepId) continue;
                    if (dupIdsBuilder.length() > 0) dupIdsBuilder.append(",");
                    dupIdsBuilder.append(venueId);
                }

                if (dupIdsBuilder.length() == 0) continue;
                String dupIds = dupIdsBuilder.toString();

                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("UPDATE EVENTS SET venue_id = " + keepId + " WHERE venue_id IN (" + dupIds + ")");
                    stmt.executeUpdate("DELETE FROM VENUE WHERE venue_id IN (" + dupIds + ")");
                    System.out.println("[DB INIT] ✓ Consolidated duplicate venue '" + venueName + "' to id=" + keepId);
                }
            }
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE VENUE ADD UNIQUE INDEX uq_venue_name (venue_name)");
            System.out.println("[DB INIT] ✓ Added unique constraint on VENUE.venue_name");
        } catch (SQLException e) {
            System.out.println("[DB INIT] Info: " + e.getMessage());
        }
    }

    private static void createStoredProcedures(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Ensure no old triggers conflict with our stored-procedure seat management
            stmt.execute("DROP TRIGGER IF EXISTS trg_decrement_seats");
            stmt.execute("DROP TRIGGER IF EXISTS trg_restore_seats_on_cancel");
            stmt.execute("DROP TRIGGER IF EXISTS trg_prevent_double_booking");
            stmt.execute("DROP TRIGGER IF EXISTS trg_auto_waitlist");
            stmt.execute("DROP TRIGGER IF EXISTS trg_deadline_check");

            stmt.execute("DROP PROCEDURE IF EXISTS sp_register_user");
            stmt.execute("DROP PROCEDURE IF EXISTS sp_cancel_registration");
            stmt.execute("DROP PROCEDURE IF EXISTS sp_user_registrations");
        }

        try (Statement stmt = conn.createStatement()) {
            stmt.setQueryTimeout(30);
            
            // Simple registration procedure - without complex transactions
            String spRegisterUser = "CREATE PROCEDURE sp_register_user(" +
                "IN p_user_id INT, " +
                "IN p_event_id INT, " +
                "IN p_amount DECIMAL(10,2), " +
                "IN p_pay_mode VARCHAR(20), " +
                "OUT p_result VARCHAR(200)) " +
                "BEGIN " +
                "  DECLARE v_seats INT; " +
                "  DECLARE v_deadline DATE; " +
                "  DECLARE v_already INT; " +
                "  SELECT COUNT(*) INTO v_already FROM REGISTRATIONS WHERE user_id = p_user_id AND event_id = p_event_id AND status IN ('confirmed','waitlisted'); " +
                "  IF v_already > 0 THEN " +
                "    SET p_result = 'ERROR: Already registered for this event.'; " +
                "  ELSE " +
                "    SELECT available_seats, registration_deadline INTO v_seats, v_deadline FROM EVENTS WHERE event_id = p_event_id; " +
                "    IF v_deadline < CURDATE() THEN " +
                "      SET p_result = 'ERROR: Registration deadline has passed.'; " +
                "    ELSEIF v_seats > 0 THEN " +
                "      INSERT INTO REGISTRATIONS(user_id, event_id, status) VALUES (p_user_id, p_event_id, 'confirmed'); " +
                "      INSERT INTO PAYMENTS(user_id, event_id, amount, status, payment_mode) VALUES (p_user_id, p_event_id, p_amount, 'completed', p_pay_mode); " +
                "      UPDATE EVENTS SET available_seats = available_seats - 1 WHERE event_id = p_event_id; " +
                "      SET p_result = 'SUCCESS: Registered successfully'; " +
                "    ELSE " +
                "      INSERT INTO REGISTRATIONS(user_id, event_id, status) VALUES (p_user_id, p_event_id, 'waitlisted'); " +
                "      INSERT INTO PAYMENTS(user_id, event_id, amount, status, payment_mode) VALUES (p_user_id, p_event_id, p_amount, 'pending', p_pay_mode); " +
                "      SET p_result = 'WAITLISTED: Added to waitlist and payment pending until confirmation.'; " +
                "    END IF; " +
                "  END IF; " +
                "END";
            
            stmt.execute(spRegisterUser);
            System.out.println("[DB INIT] ✓ Created sp_register_user");

            String spCancelRegistration = "CREATE PROCEDURE sp_cancel_registration(" +
                "IN p_reg_id INT, " +
                "OUT p_result VARCHAR(200)) " +
                "BEGIN " +
                "  DECLARE v_user_id INT; " +
                "  DECLARE v_event_id INT; " +
                "  DECLARE v_reg_status VARCHAR(50); " +
                "  DECLARE v_waitlist_id INT; " +
                "  DECLARE v_waitlist_user INT; " +
                "  SELECT user_id, event_id, status INTO v_user_id, v_event_id, v_reg_status FROM REGISTRATIONS WHERE reg_id = p_reg_id; " +
                "  IF v_reg_status = 'confirmed' THEN " +
                "    UPDATE EVENTS SET available_seats = available_seats + 1 WHERE event_id = v_event_id; " +
                "    SELECT reg_id INTO v_waitlist_id FROM REGISTRATIONS WHERE event_id = v_event_id AND status = 'waitlisted' ORDER BY registration_date ASC LIMIT 1; " +
                "    IF v_waitlist_id IS NOT NULL THEN " +
                "      SELECT user_id INTO v_waitlist_user FROM REGISTRATIONS WHERE reg_id = v_waitlist_id; " +
                "      UPDATE REGISTRATIONS SET status = 'confirmed' WHERE reg_id = v_waitlist_id; " +
                "      UPDATE PAYMENTS SET status = 'completed', payment_date = NOW() " +
                "      WHERE user_id = v_waitlist_user AND event_id = v_event_id AND status = 'pending'; " +
                "    END IF; " +
                "  END IF; " +
                "  UPDATE REGISTRATIONS SET status = 'cancelled' WHERE reg_id = p_reg_id; " +
                "  UPDATE PAYMENTS SET status = 'refunded' WHERE user_id = v_user_id AND event_id = v_event_id AND status = 'completed'; " +
                "  SET p_result = 'SUCCESS: Registration cancelled.'; " +
                "END";
            
            stmt.execute(spCancelRegistration);
            System.out.println("[DB INIT] ✓ Created sp_cancel_registration");

            String spUserRegistrations = "CREATE PROCEDURE sp_user_registrations(IN p_user_id INT) " +
                "BEGIN " +
                "  SELECT " +
                "    r.reg_id, e.event_name, e.event_date, r.registration_date, r.status AS reg_status, " +
                "    COALESCE(p.amount, 0) as amount, COALESCE(p.payment_mode, 'free') as payment_mode, " +
                "    COALESCE(p.status, 'pending') AS pay_status, v.venue_name, v.location " +
                "  FROM REGISTRATIONS r " +
                "  JOIN EVENTS e ON r.event_id = e.event_id " +
                "  JOIN VENUE v ON e.venue_id = v.venue_id " +
                "  LEFT JOIN PAYMENTS p ON p.user_id = r.user_id AND p.event_id = r.event_id " +
                "  WHERE r.user_id = p_user_id " +
                "  ORDER BY r.registration_date DESC; " +
                "END";
            
            stmt.execute(spUserRegistrations);
            System.out.println("[DB INIT] ✓ Created sp_user_registrations");
        }
    }

    private static void insertSampleData(Statement stmt) throws SQLException {
        // Delete old events first to refresh with new dates
        try {
            stmt.execute("DELETE FROM EVENTS WHERE event_date < '2026-04-01'");
            System.out.println("[DB INIT] Cleaned up old events");
        } catch (SQLException e) {
            System.out.println("[DB INIT] Info: " + e.getMessage());
        }

        String[] dataSQL = {
            "INSERT IGNORE INTO EVENT_CATEGORY (category_name) VALUES ('Technology'), ('Music'), ('Sports'), ('Arts & Culture'), ('Business'), ('Health & Wellness'), ('Education')",
            "INSERT IGNORE INTO VENUE (venue_name, location, capacity) VALUES ('Main Auditorium', 'Block A, Ground Floor', 500), ('Seminar Hall 1', 'Block B, First Floor', 100), ('Open Air Amphitheatre', 'College Ground', 1000), ('Conference Room 101', 'Admin Block', 50), ('Sports Complex', 'North Campus', 300)",
            "INSERT IGNORE INTO USERS (name, email, phone, password, role) VALUES ('Admin User', 'admin@college.edu', '9000000001', 'admin123', 'admin'), ('Alice Sharma', 'alice@student.edu', '9000000002', 'password123', 'user'), ('Bob Mehta', 'bob@student.edu', '9000000003', 'password123', 'user'), ('Carol Dsouza', 'carol@student.edu', '9000000004', 'password123', 'user'), ('David Kumar', 'david@student.edu', '9000000005', 'password123', 'user'), ('Eva Nair', 'eva@student.edu', '9000000006', 'password123', 'user')",
            "INSERT INTO EVENTS (event_name, event_date, total_seats, available_seats, registration_deadline, status, description, price, category_id, venue_id) VALUES ('Annual Tech Fest 2026', '2026-11-15', 500, 500, '2026-11-10', 'upcoming', 'Tech festival with workshops', 500, 1, 1), ('Classical Music Night', '2026-10-20', 200, 200, '2026-10-18', 'upcoming', 'Classical music evening', 1000, 2, 3), ('Inter-College Cricket', '2026-10-05', 300, 300, '2026-10-03', 'upcoming', 'Cricket tournament', 0, 3, 5), ('AI Workshop', '2026-09-28', 50, 50, '2026-09-25', 'upcoming', 'ML and AI workshop', 2000, 1, 2), ('Startup Summit', '2026-11-01', 100, 100, '2026-10-28', 'upcoming', 'Connect with entrepreneurs', 1500, 5, 4) ON DUPLICATE KEY UPDATE event_date=VALUES(event_date), registration_deadline=VALUES(registration_deadline), price=VALUES(price)"
        };

        for (String sql : dataSQL) {
            try {
                stmt.execute(sql);
            } catch (SQLException e) {
                System.out.println("[DB INIT] Info: " + e.getMessage());
            }
        }
    }
}
