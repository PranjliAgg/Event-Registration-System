package com.eventregistration.dao;

import com.eventregistration.db.DatabaseConnection;
import com.eventregistration.model.Event;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for EVENTS table.
 * Demonstrates Basic Queries, Complex Queries, and Stored Procedure calls.
 */
public class EventDAO {

    private Connection conn() {
        return DatabaseConnection.getInstance().getConnection();
    }

    // ── Basic: Get all upcoming events ───────────────────────────
    public List<Event> getAllUpcomingEvents() {
        List<Event> events = new ArrayList<>();
        Connection connection = conn();
        if (connection == null) {
            System.err.println("[EVENT DAO] ERROR: Database connection is null");
            return events;
        }
        
        String sql = """
            SELECT e.event_id, e.event_name, e.event_date,
                   e.total_seats, e.available_seats,
                   e.registration_deadline, e.status, e.description,
                   e.price, c.category_name, v.venue_name, v.location
            FROM EVENTS e
            JOIN EVENT_CATEGORY c ON e.category_id = c.category_id
            JOIN VENUE          v ON e.venue_id    = v.venue_id
            WHERE e.status IN ('upcoming', 'completed')
            ORDER BY e.event_date
            """;
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                events.add(mapRow(rs));
            }
            System.out.println("[EVENT DAO] Successfully loaded " + events.size() + " upcoming events");
        } catch (SQLException e) {
            System.err.println("[EVENT DAO] Error loading events: " + e.getMessage());
            e.printStackTrace();
        }
        return events;
    }

    // ── Basic: Get event by ID ────────────────────────────────────
    public Event getEventById(int eventId) {
        String sql = """
            SELECT e.event_id, e.event_name, e.event_date,
                   e.total_seats, e.available_seats,
                   e.registration_deadline, e.status, e.description,
                   e.price, c.category_name, v.venue_name, v.location
            FROM EVENTS e
            JOIN EVENT_CATEGORY c ON e.category_id = c.category_id
            JOIN VENUE          v ON e.venue_id    = v.venue_id
            WHERE e.event_id = ?
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return mapRow(rs);
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    // ── Helpers for filter dropdowns ─────────────────────────────
    public List<String> getAllCategories() {
        List<String> categories = new ArrayList<>();
        String sql = "SELECT DISTINCT category_name FROM EVENT_CATEGORY ORDER BY category_name";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                categories.add(rs.getString("category_name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return categories;
    }

    public List<String> getAllVenues() {
        List<String> venues = new ArrayList<>();
        String sql = "SELECT DISTINCT venue_name FROM VENUE ORDER BY venue_name";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                venues.add(rs.getString("venue_name"));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return venues;
    }

    public int getCategoryIdByName(String categoryName) {
        String sql = "SELECT category_id FROM EVENT_CATEGORY WHERE category_name = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, categoryName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("category_id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 1;
    }

    public int getVenueIdByName(String venueName) {
        String sql = "SELECT venue_id FROM VENUE WHERE venue_name = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, venueName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt("venue_id");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 1;
    }

    // ── Complex: Search and filter events by keyword/category/venue ──
    public List<Event> searchEvents(String keyword, String categoryName, String venueName) {
        List<Event> events = new ArrayList<>();
        String sql = """
            SELECT e.event_id, e.event_name, e.event_date,
                   e.total_seats, e.available_seats,
                   e.registration_deadline, e.status, e.description,
                   e.price, c.category_name, v.venue_name, v.location
            FROM EVENTS e
            JOIN EVENT_CATEGORY c ON e.category_id = c.category_id
            JOIN VENUE          v ON e.venue_id    = v.venue_id
            WHERE e.status IN ('upcoming','completed')
              AND (? IS NULL OR e.event_name LIKE ? OR e.description LIKE ?)
              AND (? IS NULL OR c.category_name = ?)
              AND (? IS NULL OR v.venue_name = ?)
            ORDER BY e.event_date
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            String kw = (keyword == null || keyword.isBlank()) ? null : "%" + keyword + "%";
            ps.setString(1, kw); ps.setString(2, kw); ps.setString(3, kw);
            ps.setString(4, categoryName); ps.setString(5, categoryName);
            ps.setString(6, venueName); ps.setString(7, venueName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) events.add(mapRow(rs));
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return events;
    }

    // ── Admin: Add new event ──────────────────────────────────────
    public boolean addEvent(Event event) {
        String sql = """
            INSERT INTO EVENTS
              (event_name, event_date, total_seats, available_seats,
               registration_deadline, status, description, price, category_id, venue_id)
            VALUES (?,?,?,?,?,?,?,?,?,?)
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, event.getEventName());
            ps.setDate(2, Date.valueOf(event.getEventDate()));
            ps.setInt(3, event.getTotalSeats());
            ps.setInt(4, event.getTotalSeats());       // initially all seats available
            ps.setDate(5, Date.valueOf(event.getRegistrationDeadline()));
            ps.setString(6, "upcoming");
            ps.setString(7, event.getDescription());
            ps.setDouble(8, event.getPrice());
            ps.setInt(9, event.getCategoryId());
            ps.setInt(10, event.getVenueId());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // ── Admin: Update event status ────────────────────────────────
    public boolean updateEventStatus(int eventId, String status) {
        String sql = "UPDATE EVENTS SET status = ? WHERE event_id = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setInt(2, eventId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    // ── Complex: Revenue report (used in admin dashboard) ─────────
    public List<Object[]> getRevenueReport() {
        List<Object[]> rows = new ArrayList<>();
        String sql = """
            SELECT e.event_name, c.category_name,
                   e.total_seats,
                   (e.total_seats - e.available_seats) AS seats_filled,
                   ROUND((e.total_seats - e.available_seats)*100.0/e.total_seats,1) AS fill_pct,
                   COALESCE(SUM(p.amount),0) AS revenue
            FROM EVENTS e
            JOIN EVENT_CATEGORY c ON e.category_id = c.category_id
            LEFT JOIN PAYMENTS p ON e.event_id = p.event_id AND p.status='completed'
            GROUP BY e.event_id, e.event_name, c.category_name,
                     e.total_seats, e.available_seats
            ORDER BY revenue DESC
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new Object[]{
                    rs.getString("event_name"),
                    rs.getString("category_name"),
                    rs.getInt("seats_filled") + "/" + rs.getInt("total_seats"),
                    rs.getDouble("fill_pct") + "%",
                    "₹" + rs.getDouble("revenue")
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return rows;
    }

    // ── Stored Procedure: Full event report ───────────────────────
    public void callEventReport(int eventId) {
        String sql = "{CALL sp_event_report(?)}";
        try (CallableStatement cs = conn().prepareCall(sql)) {
            cs.setInt(1, eventId);
            try (ResultSet rs = cs.executeQuery()) {
                while (rs.next()) {
                    System.out.println("Event     : " + rs.getString("event_name"));
                    System.out.println("Revenue   : ₹" + rs.getDouble("total_revenue"));
                    System.out.println("Waitlist  : " + rs.getInt("waitlist_count"));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    // ── Helper: map ResultSet row → Event ────────────────────────
    private Event mapRow(ResultSet rs) throws SQLException {
        Event e = new Event();
        e.setEventId(rs.getInt("event_id"));
        e.setEventName(rs.getString("event_name"));
        e.setEventDate(rs.getDate("event_date").toLocalDate());
        e.setTotalSeats(rs.getInt("total_seats"));
        e.setAvailableSeats(rs.getInt("available_seats"));
        e.setRegistrationDeadline(rs.getDate("registration_deadline").toLocalDate());
        e.setStatus(rs.getString("status"));
        e.setDescription(rs.getString("description"));
        double price = rs.getDouble("price");
        e.setPrice(price != 0 ? price : 0);
        e.setCategoryName(rs.getString("category_name"));
        e.setVenueName(rs.getString("venue_name"));
        e.setVenueLocation(rs.getString("location"));
        return e;
    }
}
