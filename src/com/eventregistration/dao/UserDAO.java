package com.eventregistration.dao;

import com.eventregistration.db.DatabaseConnection;
import com.eventregistration.model.User;

import java.sql.*;

/**
 * Data Access Object for USERS table.
 */
public class UserDAO {

    private Connection conn() {
        return DatabaseConnection.getInstance().getConnection();
    }

    /**
     * Authenticate user by email + password hash.
     * In production, compare bcrypt hash; here we do plain equality for demo.
     */
    public User login(String email, String password) {
        String sql = "SELECT * FROM USERS WHERE email = ? AND password = ?";
        System.out.println("[USER DAO] Attempting login for: " + email);
        try {
            Connection connection = conn();
            if (connection == null) {
                System.err.println("[USER DAO] ERROR: Database connection is null");
                return null;
            }
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setString(1, email);
                ps.setString(2, password);   // replace with hash comparison in production
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        User u = new User();
                        u.setUserId(rs.getInt("user_id"));
                        u.setName(rs.getString("name"));
                        u.setEmail(rs.getString("email"));
                        u.setPhone(rs.getString("phone"));
                        u.setRole(rs.getString("role"));
                        System.out.println("[USER DAO] Login successful: " + u.getName() + " (" + u.getRole() + ")");
                        return u;
                    }
                }
            }
            System.out.println("[USER DAO] No user found with email: " + email);
        } catch (SQLException e) {
            System.err.println("[USER DAO] SQL Error: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /** Register a new user account. */
    public boolean registerUser(User user) {
        String sql = "INSERT INTO USERS(name,email,phone,password,role) VALUES(?,?,?,?,'user')";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, user.getName());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getPhone());
            ps.setString(4, user.getPassword());  // store hash in production
            return ps.executeUpdate() > 0;
        } catch (SQLIntegrityConstraintViolationException e) {
            return false;   // duplicate email
        } catch (SQLException e) {
            e.printStackTrace();
            return false;
        }
    }

    /** Check if email already exists. */
    public boolean emailExists(String email) {
        String sql = "SELECT COUNT(*) FROM USERS WHERE email = ?";
        try (PreparedStatement ps = conn().prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /** Get total user count (for admin dashboard). */
    public int getTotalUsers() {
        String sql = "SELECT COUNT(*) FROM USERS WHERE role='user'";
        try (PreparedStatement ps = conn().prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next()) return rs.getInt(1);
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return 0;
    }
}
