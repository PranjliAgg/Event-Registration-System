-- ============================================================
--  EVENT REGISTRATION & ALLOCATION SYSTEM
--  Database Schema — MySQL
-- ============================================================

CREATE DATABASE IF NOT EXISTS event_registration_db;
USE event_registration_db;

-- ============================================================
--  TABLE DEFINITIONS
-- ============================================================

CREATE TABLE IF NOT EXISTS EVENT_CATEGORY (
    category_id   INT AUTO_INCREMENT PRIMARY KEY,
    category_name VARCHAR(100) NOT NULL UNIQUE
);

CREATE TABLE IF NOT EXISTS VENUE (
    venue_id   INT AUTO_INCREMENT PRIMARY KEY,
    venue_name VARCHAR(150) NOT NULL,
    location   VARCHAR(255) NOT NULL,
    capacity   INT NOT NULL CHECK (capacity > 0)
);

CREATE TABLE IF NOT EXISTS USERS (
    user_id  INT AUTO_INCREMENT PRIMARY KEY,
    name     VARCHAR(100) NOT NULL,
    email    VARCHAR(150) NOT NULL UNIQUE,
    phone    VARCHAR(15),
    password VARCHAR(255) NOT NULL,
    role     ENUM('admin','user') NOT NULL DEFAULT 'user'
);

CREATE TABLE IF NOT EXISTS EVENTS (
    event_id              INT AUTO_INCREMENT PRIMARY KEY,
    event_name            VARCHAR(200) NOT NULL,
    event_date            DATE NOT NULL,
    total_seats           INT NOT NULL CHECK (total_seats > 0),
    available_seats       INT NOT NULL,
    registration_deadline DATE NOT NULL,
    status                ENUM('upcoming','ongoing','completed','cancelled') NOT NULL DEFAULT 'upcoming',
    description           TEXT,
    category_id           INT NOT NULL,
    venue_id              INT NOT NULL,
    CONSTRAINT fk_event_category FOREIGN KEY (category_id) REFERENCES EVENT_CATEGORY(category_id),
    CONSTRAINT fk_event_venue    FOREIGN KEY (venue_id)    REFERENCES VENUE(venue_id),
    CONSTRAINT chk_seats         CHECK (available_seats <= total_seats AND available_seats >= 0),
    CONSTRAINT chk_deadline      CHECK (registration_deadline <= event_date)
);

CREATE TABLE IF NOT EXISTS REGISTRATIONS (
    reg_id            INT AUTO_INCREMENT PRIMARY KEY,
    user_id           INT NOT NULL,
    event_id          INT NOT NULL,
    registration_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status            ENUM('pending','confirmed','cancelled','waitlisted') NOT NULL DEFAULT 'pending',
    CONSTRAINT fk_reg_user  FOREIGN KEY (user_id)  REFERENCES USERS(user_id),
    CONSTRAINT fk_reg_event FOREIGN KEY (event_id) REFERENCES EVENTS(event_id),
    CONSTRAINT uq_user_event UNIQUE (user_id, event_id)
);

CREATE TABLE IF NOT EXISTS PAYMENTS (
    payment_id   INT AUTO_INCREMENT PRIMARY KEY,
    user_id      INT NOT NULL,
    event_id     INT NOT NULL,
    amount       DECIMAL(10,2) NOT NULL CHECK (amount >= 0),
    payment_date DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    status       ENUM('pending','completed','failed','refunded') NOT NULL DEFAULT 'pending',
    payment_mode ENUM('cash','card','upi','netbanking','free') NOT NULL,
    CONSTRAINT fk_pay_user  FOREIGN KEY (user_id)  REFERENCES USERS(user_id),
    CONSTRAINT fk_pay_event FOREIGN KEY (event_id) REFERENCES EVENTS(event_id)
);

CREATE TABLE IF NOT EXISTS EVENT_SCHEDULE (
    schedule_id  INT AUTO_INCREMENT PRIMARY KEY,
    event_id     INT NOT NULL,
    session_name VARCHAR(200) NOT NULL,
    start_time   DATETIME NOT NULL,
    end_time     DATETIME NOT NULL,
    CONSTRAINT fk_sched_event FOREIGN KEY (event_id) REFERENCES EVENTS(event_id),
    CONSTRAINT chk_time       CHECK (end_time > start_time)
);

-- ============================================================
--  AUDIT / LOG TABLE  (used by triggers)
-- ============================================================

CREATE TABLE IF NOT EXISTS AUDIT_LOG (
    log_id     INT AUTO_INCREMENT PRIMARY KEY,
    action     VARCHAR(100) NOT NULL,
    table_name VARCHAR(100) NOT NULL,
    record_id  INT,
    details    TEXT,
    changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================
--  TRIGGERS
-- ============================================================

DELIMITER $$

-- T1: Decrement available_seats when a new confirmed registration is inserted
CREATE TRIGGER trg_decrement_seats
AFTER INSERT ON REGISTRATIONS
FOR EACH ROW
BEGIN
    IF NEW.status = 'confirmed' THEN
        UPDATE EVENTS
        SET available_seats = available_seats - 1
        WHERE event_id = NEW.event_id;
    END IF;
END$$

-- T2: Restore available_seats when a registration is cancelled
CREATE TRIGGER trg_restore_seats_on_cancel
AFTER UPDATE ON REGISTRATIONS
FOR EACH ROW
BEGIN
    IF OLD.status = 'confirmed' AND NEW.status = 'cancelled' THEN
        UPDATE EVENTS
        SET available_seats = available_seats + 1
        WHERE event_id = NEW.event_id;
    END IF;

    -- Confirm first waitlisted user when a seat opens
    IF OLD.status = 'confirmed' AND NEW.status = 'cancelled' THEN
        UPDATE REGISTRATIONS
        SET status = 'confirmed'
        WHERE event_id = NEW.event_id
          AND status = 'waitlisted'
        ORDER BY registration_date ASC
        LIMIT 1;
    END IF;
END$$

-- T3: Prevent double-booking (extra guard beyond UNIQUE constraint)
CREATE TRIGGER trg_prevent_double_booking
BEFORE INSERT ON REGISTRATIONS
FOR EACH ROW
BEGIN
    DECLARE existing_count INT;
    SELECT COUNT(*) INTO existing_count
    FROM REGISTRATIONS
    WHERE user_id = NEW.user_id AND event_id = NEW.event_id;

    IF existing_count > 0 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'User is already registered for this event.';
    END IF;
END$$

-- T4: Auto-waitlist when no seats are available
CREATE TRIGGER trg_auto_waitlist
BEFORE INSERT ON REGISTRATIONS
FOR EACH ROW
BEGIN
    DECLARE seats INT;
    SELECT available_seats INTO seats FROM EVENTS WHERE event_id = NEW.event_id;
    IF seats <= 0 AND NEW.status = 'confirmed' THEN
        SET NEW.status = 'waitlisted';
    END IF;
END$$

-- T5: Block registration after deadline
CREATE TRIGGER trg_deadline_check
BEFORE INSERT ON REGISTRATIONS
FOR EACH ROW
BEGIN
    DECLARE deadline DATE;
    SELECT registration_deadline INTO deadline FROM EVENTS WHERE event_id = NEW.event_id;
    IF CURDATE() > deadline THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Registration deadline has passed for this event.';
    END IF;
END$$

-- T6: Audit log for every new registration
CREATE TRIGGER trg_audit_registration
AFTER INSERT ON REGISTRATIONS
FOR EACH ROW
BEGIN
    INSERT INTO AUDIT_LOG(action, table_name, record_id, details)
    VALUES ('INSERT', 'REGISTRATIONS', NEW.reg_id,
            CONCAT('user_id=', NEW.user_id, ', event_id=', NEW.event_id, ', status=', NEW.status));
END$$

-- T7: Audit log for payment completion
CREATE TRIGGER trg_audit_payment
AFTER UPDATE ON PAYMENTS
FOR EACH ROW
BEGIN
    IF OLD.status != NEW.status THEN
        INSERT INTO AUDIT_LOG(action, table_name, record_id, details)
        VALUES ('UPDATE', 'PAYMENTS', NEW.payment_id,
                CONCAT('status changed from ', OLD.status, ' to ', NEW.status,
                       ' for user_id=', NEW.user_id));
    END IF;
END$$

DELIMITER ;

-- ============================================================
--  STORED PROCEDURES
-- ============================================================

DELIMITER $$

-- P1: Register a user for an event (with payment)
CREATE PROCEDURE sp_register_user(
    IN  p_user_id    INT,
    IN  p_event_id   INT,
    IN  p_amount     DECIMAL(10,2),
    IN  p_pay_mode   VARCHAR(20),
    OUT p_result     VARCHAR(200)
)
BEGIN
    DECLARE v_seats    INT;
    DECLARE v_deadline DATE;
    DECLARE v_reg_id   INT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SET p_result = 'ERROR: Transaction failed.';
    END;

    START TRANSACTION;

    SELECT available_seats, registration_deadline
    INTO v_seats, v_deadline
    FROM EVENTS WHERE event_id = p_event_id FOR UPDATE;

    IF v_deadline < CURDATE() THEN
        SET p_result = 'ERROR: Registration deadline has passed.';
        ROLLBACK;
    ELSEIF v_seats > 0 THEN
        INSERT INTO REGISTRATIONS(user_id, event_id, status)
        VALUES (p_user_id, p_event_id, 'confirmed');
        SET v_reg_id = LAST_INSERT_ID();

        INSERT INTO PAYMENTS(user_id, event_id, amount, status, payment_mode)
        VALUES (p_user_id, p_event_id, p_amount, 'completed', p_pay_mode);

        COMMIT;
        SET p_result = CONCAT('SUCCESS: Registered with reg_id=', v_reg_id);
    ELSE
        INSERT INTO REGISTRATIONS(user_id, event_id, status)
        VALUES (p_user_id, p_event_id, 'waitlisted');

        COMMIT;
        SET p_result = 'WAITLISTED: No seats available; added to waitlist.';
    END IF;
END$$

-- P2: Cancel a registration and trigger refund
CREATE PROCEDURE sp_cancel_registration(
    IN  p_reg_id INT,
    OUT p_result VARCHAR(200)
)
BEGIN
    DECLARE v_user_id  INT;
    DECLARE v_event_id INT;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        SET p_result = 'ERROR: Cancellation failed.';
    END;

    START TRANSACTION;

    SELECT user_id, event_id INTO v_user_id, v_event_id
    FROM REGISTRATIONS WHERE reg_id = p_reg_id FOR UPDATE;

    UPDATE REGISTRATIONS SET status = 'cancelled' WHERE reg_id = p_reg_id;

    UPDATE PAYMENTS
    SET status = 'refunded'
    WHERE user_id = v_user_id AND event_id = v_event_id AND status = 'completed';

    COMMIT;
    SET p_result = CONCAT('SUCCESS: Registration ', p_reg_id, ' cancelled and refund initiated.');
END$$

-- P3: Get full event report
CREATE PROCEDURE sp_event_report(IN p_event_id INT)
BEGIN
    SELECT
        e.event_id,
        e.event_name,
        e.event_date,
        e.total_seats,
        e.available_seats,
        (e.total_seats - e.available_seats) AS seats_filled,
        e.status,
        v.venue_name,
        v.location,
        c.category_name,
        COUNT(DISTINCT r.reg_id)   AS total_registrations,
        SUM(p.amount)              AS total_revenue,
        COUNT(CASE WHEN r.status='waitlisted' THEN 1 END) AS waitlist_count
    FROM EVENTS e
    JOIN VENUE          v ON e.venue_id    = v.venue_id
    JOIN EVENT_CATEGORY c ON e.category_id = c.category_id
    LEFT JOIN REGISTRATIONS r ON e.event_id = r.event_id
    LEFT JOIN PAYMENTS      p ON e.event_id = p.event_id AND p.status = 'completed'
    WHERE e.event_id = p_event_id
    GROUP BY e.event_id, e.event_name, e.event_date, e.total_seats,
             e.available_seats, e.status, v.venue_name, v.location, c.category_name;
END$$

-- P4: Get all registrations for a user
CREATE PROCEDURE sp_user_registrations(IN p_user_id INT)
BEGIN
    SELECT
        r.reg_id,
        e.event_name,
        e.event_date,
        r.registration_date,
        r.status          AS reg_status,
        p.amount,
        p.payment_mode,
        p.status          AS pay_status,
        v.venue_name,
        v.location
    FROM REGISTRATIONS r
    JOIN EVENTS  e ON r.event_id = e.event_id
    JOIN VENUE   v ON e.venue_id = v.venue_id
    LEFT JOIN PAYMENTS p ON p.user_id = r.user_id AND p.event_id = r.event_id
    WHERE r.user_id = p_user_id
    ORDER BY r.registration_date DESC;
END$$

-- P5: Search events with filters
CREATE PROCEDURE sp_search_events(
    IN p_keyword    VARCHAR(200),
    IN p_category   INT,
    IN p_date_from  DATE,
    IN p_date_to    DATE
)
BEGIN
    SELECT
        e.event_id,
        e.event_name,
        e.event_date,
        e.available_seats,
        e.registration_deadline,
        e.status,
        c.category_name,
        v.venue_name,
        v.location
    FROM EVENTS e
    JOIN EVENT_CATEGORY c ON e.category_id = c.category_id
    JOIN VENUE          v ON e.venue_id    = v.venue_id
    WHERE
        (p_keyword   IS NULL OR e.event_name LIKE CONCAT('%', p_keyword, '%'))
        AND (p_category  IS NULL OR e.category_id = p_category)
        AND (p_date_from IS NULL OR e.event_date >= p_date_from)
        AND (p_date_to   IS NULL OR e.event_date <= p_date_to)
        AND e.status NOT IN ('cancelled','completed')
    ORDER BY e.event_date ASC;
END$$

DELIMITER ;

-- ============================================================
--  SAMPLE DATA
-- ============================================================

INSERT INTO EVENT_CATEGORY (category_name) VALUES
('Technology'), ('Music'), ('Sports'), ('Arts & Culture'),
('Business'), ('Health & Wellness'), ('Education'), ('Gaming'),
('Photography'), ('Dance');

INSERT INTO VENUE (venue_name, location, capacity) VALUES
('Main Auditorium',      'Block A, Ground Floor', 500),
('Seminar Hall 1',       'Block B, First Floor',  100),
('Open Air Amphitheatre','College Ground',         1000),
('Conference Room 101',  'Admin Block',             50),
('Sports Complex',       'North Campus',           300),
('Hall A', 'Block C', 150),
('Hall B', 'Block C', 200),
('Mini Auditorium', 'Block D', 120),
('Outdoor Stage', 'Central Lawn', 800);

INSERT INTO USERS (name, email, phone, password, role) VALUES
('Admin User',    'admin@college.edu',   '9000000001', 'admin*123',   'admin'),
('Alice Sharma',  'alice@student.edu',   '9000000002', 'alice*123',   'user'),
('Bob Mehta',     'bob@student.edu',     '9000000003', 'bob*123',     'user'),
('Carol D''souza','carol@student.edu',   '9000000004', 'carol*123',   'user'),
('David Kumar',   'david@student.edu',   '9000000005', 'david*123',   'user'),
('Eva Nair',      'eva@student.edu',     '9000000006', 'eva*123',     'user');

INSERT INTO EVENTS
(event_name, event_date, total_seats, available_seats,
 registration_deadline, status, description, category_id, venue_id) VALUES
('Annual Tech Fest 2025', '2026-11-15', 500, 500, '2026-11-10', 'upcoming',
 'Flagship technology festival with workshops, hackathon and talks.', 1, 1),
('Classical Music Night', '2026-10-20', 200, 200, '2026-10-18', 'upcoming',
 'An evening of Hindustani classical music performances.', 2, 3),
('Inter-College Cricket', '2026-10-05', 300, 300, '2026-10-03', 'upcoming',
 'Annual inter-college cricket tournament.', 3, 5),
('AI Workshop', '2026-09-28', 50, 50, '2026-09-25', 'upcoming',
 'Hands-on workshop on machine learning and AI tools.', 1, 2),
('Startup Summit', '2026-11-01', 100, 100, '2026-10-28', 'upcoming',
 'Connect with entrepreneurs, investors, and mentors.', 5, 4),
('Yoga & Wellness Day', '2026-10-12', 80, 80, '2026-10-10', 'upcoming',
 'Full-day wellness workshop including yoga, meditation, and nutrition talks.', 6, 3),
('Gaming Championship', '2026-12-05', 150, 150, '2026-12-01', 'upcoming',
 'Inter-college gaming competition', 8, 6),
('Photography Contest', '2026-11-25', 100, 100, '2026-11-20', 'upcoming',
 'Capture the best campus moments', 9, 7),
('Dance Battle', '2026-10-30', 200, 200, '2026-10-28', 'upcoming',
 'Street and freestyle dance competition', 10, 8),
('Code Sprint', '2026-09-20', 80, 80, '2026-09-18', 'upcoming',
 'Competitive coding challenge', 1, 9);

INSERT INTO EVENT_SCHEDULE (event_id, session_name, start_time, end_time) VALUES
(1, 'Inauguration',         '2026-11-15 09:00:00', '2026-11-15 10:00:00'),
(1, 'Hackathon Round 1',    '2026-11-15 10:30:00', '2026-11-15 13:00:00'),
(1, 'Keynote Speech',       '2026-11-15 14:00:00', '2026-11-15 15:00:00'),
(1, 'Award Ceremony',       '2026-11-15 17:00:00', '2026-11-15 18:00:00'),
(2, 'Raga Bhairav',         '2026-10-20 18:00:00', '2026-10-20 19:30:00'),
(2, 'Thumri Recital',       '2026-10-20 20:00:00', '2026-10-20 21:30:00'),
(4, 'Python for ML',        '2026-09-28 09:00:00', '2026-09-28 12:00:00'),
(4, 'Neural Networks Lab',  '2026-09-28 13:00:00', '2026-09-28 17:00:00');
