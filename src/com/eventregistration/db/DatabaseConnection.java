package com.eventregistration.db;

import java.sql.*;

/**
 * Singleton database connection manager.
 * Uses JDBC to connect to MySQL.
 */
public class DatabaseConnection {

    private static final String URL      = "jdbc:mysql://localhost:3306/?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String USER     = "root";
    private static final String PASSWORD = "pranjli*24";

    private static DatabaseConnection instance;
    private Connection connection;

    /** Private constructor */
    private DatabaseConnection() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            System.out.println("[DB] MySQL Driver loaded successfully");
            connection = DriverManager.getConnection(URL, USER, PASSWORD);
            System.out.println("[DB] ✓ Connected to MySQL");
        } catch (ClassNotFoundException e) {
            System.err.println("[DB] MySQL Driver not found: " + e.getMessage());
            e.printStackTrace();
        } catch (SQLException e) {
            System.err.println("[DB] Connection failed: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /** Returns the singleton instance */
    public static synchronized DatabaseConnection getInstance() {
        if (instance == null || instance.isConnectionClosed()) {
            instance = new DatabaseConnection();
        }
        return instance;
    }

    /** Returns the singleton connection */
    public Connection getConnection() {
        return connection;
    }

    /** Check if connection is closed */
    private boolean isConnectionClosed() {
        try {
            return connection == null || connection.isClosed();
        } catch (SQLException e) {
            return true;
        }
    }

    /** Close the connection gracefully */
    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("[DB] Connection closed.");
            }
        } catch (SQLException e) {
            System.err.println("[DB] Error closing connection: " + e.getMessage());
        }
    }

    /** Utility: safely close resources */
    public static void close(ResultSet rs, Statement st) {
        try { if (rs != null) rs.close(); } catch (SQLException ignored) {}
        try { if (st != null) st.close(); } catch (SQLException ignored) {}
    }

    /** Test the connection */
    public static void main(String[] args) {
        try {
            Connection con = DatabaseConnection.getInstance().getConnection();
            if (con != null) System.out.println("Connected successfully!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}