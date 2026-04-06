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
            // Then create triggers
            createTriggers(conn);
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
            "CREATE TABLE IF NOT EXISTS EVENTS (" +
            "event_id INT AUTO_INCREMENT PRIMARY KEY, " +
            "event_name VARCHAR(200) NOT NULL UNIQUE, " +
            "event_date DATE NOT NULL, " +
            "total_seats INT NOT NULL CHECK (total_seats > 0), " +
            "available_seats INT NOT NULL, " +
            "registration_deadline DATE NOT NULL, " +
            "status ENUM('upcoming','ongoing','completed','cancelled') NOT NULL DEFAULT 'upcoming', " +
            "description TEXT, " +
            "price DECIMAL(8,2) DEFAULT 0.00, " +
            "category_id INT NOT NULL, " +
            "venue_id INT NOT NULL, " +
            "CONSTRAINT fk_event_category FOREIGN KEY (category_id) REFERENCES EVENT_CATEGORY(category_id), " +
            "CONSTRAINT fk_event_venue FOREIGN KEY (venue_id) REFERENCES VENUE(venue_id), " +
            "CONSTRAINT chk_seats CHECK (available_seats <= total_seats AND available_seats >= 0), " +
            "CONSTRAINT chk_deadline CHECK (registration_deadline <= event_date), " +
            "CONSTRAINT chk_price CHECK (price >= 0))",
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

    private static void createTriggers(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Drop existing triggers if they exist
            stmt.execute("DROP TRIGGER IF EXISTS trg_decrement_seats");
            stmt.execute("DROP TRIGGER IF EXISTS trg_restore_seats_on_cancel");
            stmt.execute("DROP TRIGGER IF EXISTS trg_prevent_double_booking");
            stmt.execute("DROP TRIGGER IF EXISTS trg_auto_waitlist");
            stmt.execute("DROP TRIGGER IF EXISTS trg_deadline_check");
            stmt.execute("DROP TRIGGER IF EXISTS trg_audit_registration");
            stmt.execute("DROP TRIGGER IF EXISTS trg_audit_payment");

            // Create triggers
            String[] triggerSQL = {
                "CREATE TRIGGER trg_decrement_seats AFTER INSERT ON REGISTRATIONS FOR EACH ROW BEGIN IF NEW.status = 'confirmed' THEN UPDATE EVENTS SET available_seats = available_seats - 1 WHERE event_id = NEW.event_id; END IF; END",
                "CREATE TRIGGER trg_restore_seats_on_cancel AFTER UPDATE ON REGISTRATIONS FOR EACH ROW BEGIN IF OLD.status = 'confirmed' AND NEW.status = 'cancelled' THEN UPDATE EVENTS SET available_seats = available_seats + 1 WHERE event_id = NEW.event_id; END IF; END",
                "CREATE TRIGGER trg_prevent_double_booking BEFORE INSERT ON REGISTRATIONS FOR EACH ROW BEGIN DECLARE existing_count INT; SELECT COUNT(*) INTO existing_count FROM REGISTRATIONS WHERE user_id = NEW.user_id AND event_id = NEW.event_id; IF existing_count > 0 THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'User is already registered for this event.'; END IF; END",
                "CREATE TRIGGER trg_auto_waitlist BEFORE INSERT ON REGISTRATIONS FOR EACH ROW BEGIN DECLARE seats INT; SELECT available_seats INTO seats FROM EVENTS WHERE event_id = NEW.event_id; IF seats <= 0 AND NEW.status = 'confirmed' THEN SET NEW.status = 'waitlisted'; END IF; END",
                "CREATE TRIGGER trg_deadline_check BEFORE INSERT ON REGISTRATIONS FOR EACH ROW BEGIN DECLARE deadline DATE; SELECT registration_deadline INTO deadline FROM EVENTS WHERE event_id = NEW.event_id; IF CURDATE() > deadline THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Registration deadline has passed for this event.'; END IF; END",
                "CREATE TRIGGER trg_audit_registration AFTER INSERT ON REGISTRATIONS FOR EACH ROW BEGIN INSERT INTO AUDIT_LOG(action, table_name, record_id, details) VALUES ('INSERT', 'REGISTRATIONS', NEW.reg_id, CONCAT('user_id=', NEW.user_id, ', event_id=', NEW.event_id, ', status=', NEW.status)); END",
                "CREATE TRIGGER trg_audit_payment AFTER UPDATE ON PAYMENTS FOR EACH ROW BEGIN IF OLD.status != NEW.status THEN INSERT INTO AUDIT_LOG(action, table_name, record_id, details) VALUES ('UPDATE', 'PAYMENTS', NEW.payment_id, CONCAT('status changed from ', OLD.status, ' to ', NEW.status, ' for user_id=', NEW.user_id)); END IF; END"
            };

            for (String sql : triggerSQL) {
                try {
                    stmt.execute(sql);
                    System.out.println("[DB INIT] ✓ Created trigger");
                } catch (SQLException e) {
                    System.out.println("[DB INIT] Info: " + e.getMessage());
                }
            }
        }
    }

    private static void createStoredProcedures(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            // Drop existing stored procedures if they exist
            stmt.execute("DROP PROCEDURE IF EXISTS sp_register_user");
            stmt.execute("DROP PROCEDURE IF EXISTS sp_cancel_registration");
            stmt.execute("DROP PROCEDURE IF EXISTS sp_event_report");
            stmt.execute("DROP PROCEDURE IF EXISTS sp_user_registrations");
            stmt.execute("DROP PROCEDURE IF EXISTS sp_search_events");

            // P1: Register a user for an event (with payment)
            String spRegisterUser = "CREATE PROCEDURE sp_register_user(" +
                "IN p_user_id INT, " +
                "IN p_event_id INT, " +
                "IN p_amount DECIMAL(10,2), " +
                "IN p_pay_mode VARCHAR(20), " +
                "OUT p_result VARCHAR(200)) " +
                "BEGIN " +
                "DECLARE v_seats INT; " +
                "DECLARE v_deadline DATE; " +
                "DECLARE v_reg_id INT; " +
                "SELECT available_seats, registration_deadline INTO v_seats, v_deadline FROM EVENTS WHERE event_id = p_event_id; " +
                "IF v_deadline < CURDATE() THEN " +
                "SET p_result = 'ERROR: Registration deadline has passed.'; " +
                "ELSEIF EXISTS(SELECT 1 FROM REGISTRATIONS WHERE user_id = p_user_id AND event_id = p_event_id LIMIT 1) THEN " +
                "SET p_result = 'ERROR: Already registered for this event.'; " +
                "ELSEIF v_seats > 0 THEN " +
                "INSERT INTO REGISTRATIONS(user_id, event_id, status) VALUES (p_user_id, p_event_id, 'confirmed'); " +
                "SET v_reg_id = LAST_INSERT_ID(); " +
                "INSERT INTO PAYMENTS(user_id, event_id, amount, status, payment_mode) VALUES (p_user_id, p_event_id, p_amount, 'completed', p_pay_mode); " +
                "SET p_result = CONCAT('SUCCESS: Registered with reg_id=', v_reg_id); " +
                "ELSE " +
                "INSERT INTO REGISTRATIONS(user_id, event_id, status) VALUES (p_user_id, p_event_id, 'waitlisted'); " +
                "INSERT INTO PAYMENTS(user_id, event_id, amount, status, payment_mode) VALUES (p_user_id, p_event_id, p_amount, 'pending', p_pay_mode); " +
                "SET v_reg_id = LAST_INSERT_ID(); " +
                "SET p_result = CONCAT('WAITLISTED: No seats available; added to waitlist with reg_id=', v_reg_id); " +
                "END IF; " +
                "END";

            stmt.execute(spRegisterUser);
            System.out.println("[DB INIT] ✓ Created sp_register_user");

            // P2: Cancel a registration and trigger refund
            String spCancelRegistration = "CREATE PROCEDURE sp_cancel_registration(" +
                "IN p_reg_id INT, " +
                "OUT p_result VARCHAR(200)) " +
                "BEGIN " +
                "DECLARE v_user_id INT; " +
                "DECLARE v_event_id INT; " +
                "DECLARE v_waitlist_id INT; " +
                "DECLARE v_waitlist_user INT; " +
                "SELECT user_id, event_id INTO v_user_id, v_event_id FROM REGISTRATIONS WHERE reg_id = p_reg_id; " +
                "UPDATE REGISTRATIONS SET status = 'cancelled' WHERE reg_id = p_reg_id; " +
                "UPDATE PAYMENTS SET status = 'refunded' WHERE user_id = v_user_id AND event_id = v_event_id AND status = 'completed'; " +
                "SELECT reg_id INTO v_waitlist_id FROM REGISTRATIONS WHERE event_id = v_event_id AND status = 'waitlisted' ORDER BY registration_date ASC LIMIT 1; " +
                "IF v_waitlist_id IS NOT NULL THEN " +
                "SELECT user_id INTO v_waitlist_user FROM REGISTRATIONS WHERE reg_id = v_waitlist_id; " +
                "UPDATE REGISTRATIONS SET status = 'confirmed' WHERE reg_id = v_waitlist_id; " +
                "UPDATE PAYMENTS SET status = 'completed', payment_date = NOW() WHERE user_id = v_waitlist_user AND event_id = v_event_id AND status = 'pending'; " +
                "UPDATE EVENTS SET available_seats = available_seats - 1 WHERE event_id = v_event_id; " +
                "END IF; " +
                "SET p_result = CONCAT('SUCCESS: Registration ', p_reg_id, ' cancelled and refund initiated.'); " +
                "END";

            stmt.execute(spCancelRegistration);
            System.out.println("[DB INIT] ✓ Created sp_cancel_registration");

            // P4: Get all registrations for a user
            String spUserRegistrations = "CREATE PROCEDURE sp_user_registrations(IN p_user_id INT) " +
                "BEGIN " +
                "SELECT r.reg_id, e.event_name, e.event_date, r.registration_date, r.status AS reg_status, " +
                "p.amount, p.payment_mode, p.status AS pay_status, v.venue_name, v.location " +
                "FROM REGISTRATIONS r " +
                "JOIN EVENTS e ON r.event_id = e.event_id " +
                "JOIN VENUE v ON e.venue_id = v.venue_id " +
                "LEFT JOIN PAYMENTS p ON p.user_id = r.user_id AND p.event_id = r.event_id " +
                "WHERE r.user_id = p_user_id " +
                "ORDER BY r.registration_date DESC; " +
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
            "INSERT IGNORE INTO EVENT_CATEGORY (category_name) VALUES " +
            "('Technology'), ('Music'), ('Sports'), ('Arts & Culture'), " +
            "('Business'), ('Health & Wellness'), ('Education'), ('Gaming'), " +
            "('Photography'), ('Dance')",

            "INSERT IGNORE INTO VENUE (venue_name, location, capacity) VALUES " +
            "('Main Auditorium', 'Block A, Ground Floor', 500), " +
            "('Seminar Hall 1', 'Block B, First Floor', 100), " +
            "('Open Air Amphitheatre','College Ground', 1000), " +
            "('Conference Room 101', 'Admin Block', 50), " +
            "('Sports Complex', 'North Campus', 300), " +
            "('Hall A', 'Block C', 150), " +
            "('Hall B', 'Block C', 200), " +
            "('Mini Auditorium', 'Block D', 120), " +
            "('Outdoor Stage', 'Central Lawn', 800)",

            "INSERT IGNORE INTO USERS (name, email, phone, password, role) VALUES " +
            "('Admin User', 'admin@college.edu', '9000000001', 'admin*123', 'admin'), " +
            "('Alice Sharma', 'alice@student.edu', '9000000002', 'alice*123', 'user'), " +
            "('Bob Mehta', 'bob@student.edu', '9000000003', 'bob*123', 'user'), " +
            "('Carol D''souza', 'carol@student.edu', '9000000004', 'carol*123', 'user'), " +
            "('David Kumar', 'david@student.edu', '9000000005', 'david*123', 'user'), " +
            "('Eva Nair', 'eva@student.edu', '9000000006', 'eva*123', 'user')",

            "INSERT INTO EVENTS (event_name, event_date, total_seats, available_seats, " +
            "registration_deadline, status, description, price, category_id, venue_id) VALUES " +
            "('Annual Tech Fest 2025', '2026-11-15', 500, 500, '2026-11-10', 'upcoming', " +
            "'Flagship technology festival with workshops, hackathon and talks.', 299.00, 1, 1), " +
            "('Classical Music Night', '2026-10-20', 200, 200, '2026-10-18', 'upcoming', " +
            "'An evening of Hindustani classical music performances.', 199.00, 2, 3), " +
            "('Inter-College Cricket', '2026-10-05', 300, 300, '2026-10-03', 'upcoming', " +
            "'Annual inter-college cricket tournament.', 150.00, 3, 5), " +
            "('AI Workshop', '2026-09-28', 50, 50, '2026-09-25', 'upcoming', " +
            "'Hands-on workshop on machine learning and AI tools.', 499.00, 1, 2), " +
            "('Startup Summit', '2026-11-01', 100, 100, '2026-10-28', 'upcoming', " +
            "'Connect with entrepreneurs, investors, and mentors.', 399.00, 5, 4), " +
            "('Yoga & Wellness Day', '2026-10-12', 80, 80, '2026-10-10', 'upcoming', " +
            "'Full-day wellness workshop including yoga, meditation, and nutrition talks.', 99.00, 6, 3), " +
            "('Gaming Championship', '2026-12-05', 150, 150, '2026-12-01', 'upcoming', " +
            "'Inter-college gaming competition', 250.00, 8, 6), " +
            "('Photography Contest', '2026-11-25', 100, 100, '2026-11-20', 'upcoming', " +
            "'Capture the best campus moments', 120.00, 9, 7), " +
            "('Dance Battle', '2026-10-30', 2, 2, '2026-10-28', 'upcoming', " +
            "'Street and freestyle dance competition', 180.00, 10, 8), " +
            "('Code Sprint', '2026-09-20', 80, 80, '2026-09-18', 'upcoming', " +
            "'Competitive coding challenge', 220.00, 1, 9)",

            "INSERT IGNORE INTO EVENT_SCHEDULE (event_id, session_name, start_time, end_time) VALUES " +
            "(1, 'Inauguration', '2026-11-15 09:00:00', '2026-11-15 10:00:00'), " +
            "(1, 'Hackathon Round 1', '2026-11-15 10:30:00', '2026-11-15 13:00:00'), " +
            "(1, 'Keynote Speech', '2026-11-15 14:00:00', '2026-11-15 15:00:00'), " +
            "(1, 'Award Ceremony', '2026-11-15 17:00:00', '2026-11-15 18:00:00'), " +
            "(2, 'Raga Bhairav', '2026-10-20 18:00:00', '2026-10-20 19:30:00'), " +
            "(2, 'Thumri Recital', '2026-10-20 20:00:00', '2026-10-20 21:30:00'), " +
            "(4, 'Python for ML', '2026-09-28 09:00:00', '2026-09-28 12:00:00'), " +
            "(4, 'Neural Networks Lab', '2026-09-28 13:00:00', '2026-09-28 17:00:00')"
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
