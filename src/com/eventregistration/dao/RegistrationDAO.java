package com.eventregistration.dao;

import com.eventregistration.db.DatabaseConnection;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Data Access Object for REGISTRATIONS and PAYMENTS.
 * Calls stored procedures sp_register_user and sp_cancel_registration.
 */
public class RegistrationDAO {

    private Connection conn() {
        return DatabaseConnection.getInstance().getConnection();
    }

    /**
     * Register a user for an event using stored procedure sp_register_user.
     * Returns the result message (SUCCESS / WAITLISTED / ERROR).
     */
    public String registerUser(int userId, int eventId, double amount, String payMode) {
        System.out.println("[REGISTRATION] Attempting to register user " + userId + " for event " + eventId);
        Connection connection = conn();
        if (connection == null) {
            System.err.println("[REGISTRATION] ERROR: Database connection is null");
            return "ERROR: Database connection failed";
        }
        
        String sql = "{CALL sp_register_user(?, ?, ?, ?, ?)}";
        try (CallableStatement cs = connection.prepareCall(sql)) {
            cs.setInt(1, userId);
            cs.setInt(2, eventId);
            cs.setDouble(3, amount);
            cs.setString(4, payMode);
            cs.registerOutParameter(5, Types.VARCHAR);
            System.out.println("[REGISTRATION] Executing stored procedure...");
            cs.execute();
            String result = cs.getString(5);
            System.out.println("[REGISTRATION] Result: " + result);
            return result;
        } catch (SQLException e) {
            System.err.println("[REGISTRATION] SQL Error: " + e.getMessage());
            e.printStackTrace();
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Cancel a registration using stored procedure sp_cancel_registration.
     */
    public String cancelRegistration(int regId) {
        String sql = "{CALL sp_cancel_registration(?, ?)}";
        try (CallableStatement cs = conn().prepareCall(sql)) {
            cs.setInt(1, regId);
            cs.registerOutParameter(2, Types.VARCHAR);
            cs.execute();
            return cs.getString(2);
        } catch (SQLException e) {
            e.printStackTrace();
            return "ERROR: " + e.getMessage();
        }
    }

    /**
     * Get all registrations for a user — calls sp_user_registrations.
     * Returns rows as Object[] for TableView binding.
     * Columns: reg_id, event_name, event_date, registration_date,
     *          reg_status, amount, payment_mode, pay_status, venue_name, location
     */
    public List<Object[]> getUserRegistrations(int userId) {
        List<Object[]> rows = new ArrayList<>();
        String sql = "{CALL sp_user_registrations(?)}";
        Connection connection = conn();
        if (connection == null) {
            System.err.println("[REGISTRATION DAO] ERROR: Database connection is null");
            return rows;
        }
        
        try (CallableStatement cs = connection.prepareCall(sql)) {
            cs.setInt(1, userId);
            System.out.println("[REGISTRATION DAO] Executing sp_user_registrations for user " + userId);
            try (ResultSet rs = cs.executeQuery()) {
                while (rs.next()) {
                    rows.add(new Object[]{
                        rs.getInt("reg_id"),
                        rs.getString("event_name"),
                        rs.getDate("event_date"),
                        rs.getTimestamp("registration_date"),
                        rs.getString("reg_status"),
                        rs.getDouble("amount"),
                        rs.getString("payment_mode"),
                        rs.getString("pay_status"),
                        rs.getString("venue_name"),
                        rs.getString("location")
                    });
                }
                System.out.println("[REGISTRATION DAO] Found " + rows.size() + " registrations for user " + userId);
            }
        } catch (SQLException e) {
            System.err.println("[REGISTRATION DAO] Error fetching registrations: " + e.getMessage());
            e.printStackTrace();
        }
        return rows;
    }

    /**
     * Check if a user is already registered for an event.
     */
    public boolean isAlreadyRegistered(int userId, int eventId) {
        String sql = "SELECT COUNT(*) FROM REGISTRATIONS WHERE user_id=? AND event_id=?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setInt(1, userId); ps.setInt(2, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * Admin: get all registrations with full join.
     */
    public List<Object[]> getAllRegistrations() {
        List<Object[]> rows = new ArrayList<>();
        String sql = """
            SELECT r.reg_id, u.name, u.email,
                   e.event_name, r.registration_date, r.status,
                   COALESCE(p.amount,0) AS amount, COALESCE(p.payment_mode,'—') AS mode
            FROM REGISTRATIONS r
            JOIN USERS  u ON r.user_id  = u.user_id
            JOIN EVENTS e ON r.event_id = e.event_id
            LEFT JOIN PAYMENTS p ON p.user_id=r.user_id AND p.event_id=r.event_id
            ORDER BY r.registration_date DESC
            """;
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new Object[]{
                    rs.getInt("reg_id"),
                    rs.getString("name"),
                    rs.getString("email"),
                    rs.getString("event_name"),
                    rs.getTimestamp("registration_date"),
                    rs.getString("status"),
                    rs.getDouble("amount"),
                    rs.getString("mode")
                });
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return rows;
    }
}
