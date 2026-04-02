-- ============================================================
--  EVENT REGISTRATION SYSTEM — QUERIES REFERENCE
--  (Basic + Complex)
-- ============================================================

USE event_registration_db;

-- ============================================================
--  SECTION A: BASIC QUERIES
-- ============================================================

-- A1: List all upcoming events
SELECT event_id, event_name, event_date, available_seats, status
FROM EVENTS
WHERE status = 'upcoming'
ORDER BY event_date;

-- A2: Find all events in a specific category
SELECT e.event_name, e.event_date, c.category_name
FROM EVENTS e
JOIN EVENT_CATEGORY c ON e.category_id = c.category_id
WHERE c.category_name = 'Technology';

-- A3: Get all registrations for a specific user
SELECT r.reg_id, e.event_name, e.event_date, r.registration_date, r.status
FROM REGISTRATIONS r
JOIN EVENTS e ON r.event_id = e.event_id
WHERE r.user_id = 2;

-- A4: Check seat availability for an event
SELECT event_name, total_seats, available_seats,
       (total_seats - available_seats) AS booked_seats
FROM EVENTS
WHERE event_id = 1;

-- A5: Get payment details for a user
SELECT p.payment_id, e.event_name, p.amount, p.payment_date,
       p.status, p.payment_mode
FROM PAYMENTS p
JOIN EVENTS e ON p.event_id = e.event_id
WHERE p.user_id = 2;

-- A6: Get all sessions for a specific event
SELECT session_name, start_time, end_time,
       TIMEDIFF(end_time, start_time) AS duration
FROM EVENT_SCHEDULE
WHERE event_id = 1
ORDER BY start_time;

-- A7: List all users with their role
SELECT user_id, name, email, phone, role FROM USERS ORDER BY role, name;

-- A8: Find events at a specific venue
SELECT e.event_name, e.event_date, e.status
FROM EVENTS e
JOIN VENUE v ON e.venue_id = v.venue_id
WHERE v.venue_name = 'Main Auditorium';

-- A9: Count total registrations per event
SELECT e.event_name, COUNT(r.reg_id) AS total_registrations
FROM EVENTS e
LEFT JOIN REGISTRATIONS r ON e.event_id = r.event_id
GROUP BY e.event_id, e.event_name
ORDER BY total_registrations DESC;

-- A10: Get all pending payments
SELECT p.payment_id, u.name, e.event_name, p.amount, p.payment_date
FROM PAYMENTS p
JOIN USERS  u ON p.user_id  = u.user_id
JOIN EVENTS e ON p.event_id = e.event_id
WHERE p.status = 'pending';

-- A11: Search events by keyword
SELECT event_id, event_name, event_date, status
FROM EVENTS
WHERE event_name LIKE '%Tech%';

-- A12: List waitlisted users for an event
SELECT u.name, u.email, r.registration_date
FROM REGISTRATIONS r
JOIN USERS u ON r.user_id = u.user_id
WHERE r.event_id = 1 AND r.status = 'waitlisted'
ORDER BY r.registration_date;

-- ============================================================
--  SECTION B: COMPLEX QUERIES
-- ============================================================

-- B1: Revenue report per event (with fill rate)
SELECT
    e.event_name,
    e.event_date,
    c.category_name,
    e.total_seats,
    (e.total_seats - e.available_seats)                  AS seats_filled,
    ROUND((e.total_seats - e.available_seats) * 100.0
          / e.total_seats, 2)                            AS fill_rate_pct,
    COALESCE(SUM(p.amount), 0)                           AS total_revenue
FROM EVENTS e
JOIN EVENT_CATEGORY c ON e.category_id = c.category_id
LEFT JOIN PAYMENTS  p ON e.event_id = p.event_id AND p.status = 'completed'
GROUP BY e.event_id, e.event_name, e.event_date, c.category_name,
         e.total_seats, e.available_seats
ORDER BY total_revenue DESC;

-- B2: Top 3 most registered events using window function
SELECT event_name, total_registrations, reg_rank
FROM (
    SELECT
        e.event_name,
        COUNT(r.reg_id)                              AS total_registrations,
        DENSE_RANK() OVER (ORDER BY COUNT(r.reg_id) DESC) AS reg_rank
    FROM EVENTS e
    LEFT JOIN REGISTRATIONS r ON e.event_id = r.event_id
      AND r.status = 'confirmed'
    GROUP BY e.event_id, e.event_name
) ranked
WHERE reg_rank <= 3;

-- B3: Users who registered for more than one event
SELECT u.user_id, u.name, u.email, COUNT(r.event_id) AS event_count
FROM USERS u
JOIN REGISTRATIONS r ON u.user_id = r.user_id
WHERE r.status IN ('confirmed','waitlisted')
GROUP BY u.user_id, u.name, u.email
HAVING COUNT(r.event_id) > 1
ORDER BY event_count DESC;

-- B4: Events with overbooked venues (demand > capacity)
SELECT
    e.event_name,
    v.venue_name,
    v.capacity            AS venue_capacity,
    e.total_seats         AS event_seats,
    COUNT(r.reg_id)       AS actual_registrations
FROM EVENTS e
JOIN VENUE v ON e.venue_id = v.venue_id
LEFT JOIN REGISTRATIONS r ON e.event_id = r.event_id AND r.status = 'confirmed'
GROUP BY e.event_id, e.event_name, v.venue_name, v.capacity, e.total_seats
HAVING actual_registrations >= v.capacity;

-- B5: Category-wise registration summary with running total
SELECT
    c.category_name,
    COUNT(DISTINCT e.event_id)  AS events_held,
    COUNT(r.reg_id)             AS total_registrations,
    SUM(COUNT(r.reg_id)) OVER (
        ORDER BY COUNT(r.reg_id) DESC
        ROWS BETWEEN UNBOUNDED PRECEDING AND CURRENT ROW
    )                           AS running_total
FROM EVENT_CATEGORY c
LEFT JOIN EVENTS        e ON c.category_id = e.category_id
LEFT JOIN REGISTRATIONS r ON e.event_id    = r.event_id AND r.status = 'confirmed'
GROUP BY c.category_id, c.category_name
ORDER BY total_registrations DESC;

-- B6: Users who have never registered for any event
SELECT u.user_id, u.name, u.email
FROM USERS u
WHERE NOT EXISTS (
    SELECT 1 FROM REGISTRATIONS r WHERE r.user_id = u.user_id
)
AND u.role = 'user';

-- B7: Events with registration deadline within the next 7 days
SELECT
    e.event_name,
    e.event_date,
    e.registration_deadline,
    DATEDIFF(e.registration_deadline, CURDATE()) AS days_left,
    e.available_seats
FROM EVENTS e
WHERE e.registration_deadline BETWEEN CURDATE() AND DATE_ADD(CURDATE(), INTERVAL 7 DAY)
  AND e.status = 'upcoming'
ORDER BY e.registration_deadline;

-- B8: Most popular venue by confirmed registrations
SELECT
    v.venue_name,
    v.location,
    v.capacity,
    COUNT(r.reg_id)   AS total_confirmed,
    SUM(p.amount)     AS total_revenue
FROM VENUE v
JOIN EVENTS         e ON v.venue_id  = e.venue_id
LEFT JOIN REGISTRATIONS r ON e.event_id = r.event_id AND r.status = 'confirmed'
LEFT JOIN PAYMENTS      p ON e.event_id = p.event_id AND p.status = 'completed'
GROUP BY v.venue_id, v.venue_name, v.location, v.capacity
ORDER BY total_confirmed DESC
LIMIT 5;

-- B9: Per-user spending summary with rank
SELECT
    u.name,
    u.email,
    COUNT(p.payment_id)       AS total_payments,
    SUM(p.amount)             AS total_spent,
    RANK() OVER (ORDER BY SUM(p.amount) DESC) AS spend_rank
FROM USERS u
JOIN PAYMENTS p ON u.user_id = p.user_id AND p.status = 'completed'
GROUP BY u.user_id, u.name, u.email
ORDER BY total_spent DESC;

-- B10: Events where waitlist > confirmed count (high demand)
SELECT
    e.event_name,
    COUNT(CASE WHEN r.status = 'confirmed'  THEN 1 END) AS confirmed_count,
    COUNT(CASE WHEN r.status = 'waitlisted' THEN 1 END) AS waitlisted_count
FROM EVENTS e
JOIN REGISTRATIONS r ON e.event_id = r.event_id
GROUP BY e.event_id, e.event_name
HAVING waitlisted_count > confirmed_count;

-- B11: Monthly registration trends (using YEAR/MONTH)
SELECT
    YEAR(r.registration_date)  AS reg_year,
    MONTH(r.registration_date) AS reg_month,
    COUNT(r.reg_id)            AS registrations,
    SUM(p.amount)              AS revenue
FROM REGISTRATIONS r
LEFT JOIN PAYMENTS p ON r.user_id  = p.user_id
                     AND r.event_id = p.event_id
                     AND p.status   = 'completed'
WHERE r.status = 'confirmed'
GROUP BY reg_year, reg_month
ORDER BY reg_year DESC, reg_month DESC;

-- B12: Full event detail view (join across all tables)
SELECT
    e.event_id,
    e.event_name,
    e.event_date,
    e.status,
    c.category_name,
    v.venue_name,
    v.location,
    v.capacity            AS venue_capacity,
    e.total_seats,
    e.available_seats,
    e.registration_deadline,
    COUNT(DISTINCT r.reg_id)  AS registrations,
    COUNT(DISTINCT es.schedule_id) AS sessions
FROM EVENTS e
JOIN EVENT_CATEGORY c ON e.category_id  = c.category_id
JOIN VENUE          v ON e.venue_id     = v.venue_id
LEFT JOIN REGISTRATIONS  r  ON e.event_id = r.event_id  AND r.status  = 'confirmed'
LEFT JOIN EVENT_SCHEDULE es ON e.event_id = es.event_id
GROUP BY e.event_id, e.event_name, e.event_date, e.status,
         c.category_name, v.venue_name, v.location,
         v.capacity, e.total_seats, e.available_seats, e.registration_deadline;
