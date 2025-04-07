package com.chatapp.data.db;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Utility class for database operations such as schema creation and testing
 */
public class DatabaseUtils {

    /**
     * Initialize the database schema if it doesn't exist
     * 
     * @return true if initialization successful
     */
    public static boolean initializeSchema() {
        // Path to schema file
        String schemaFilePath = "schema.sql";
        File schemaFile = new File(schemaFilePath);

        if (!schemaFile.exists()) {
            System.err.println("Schema file not found: " + schemaFile.getAbsolutePath());
            return false;
        }

        try (Connection conn = DatabaseManager.getConnection()) {
            // Check if tables already exist
            if (tablesExist(conn)) {
                System.out.println("Database schema already initialized");
                return true;
            }

            // Read schema file
            StringBuilder schema = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new FileReader(schemaFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    // Skip comments and empty lines
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("--") || line.startsWith("//")) {
                        continue;
                    }

                    schema.append(line).append("\n");

                    // Execute statements when semicolon found
                    if (line.endsWith(";")) {
                        String sql = schema.toString();

                        // Skip USE statement as we're already connected to the DB
                        if (!sql.toUpperCase().startsWith("USE ")) {
                            try (Statement stmt = conn.createStatement()) {
                                stmt.execute(sql);
                                System.out.println("Executed: " + sql.substring(0, Math.min(50, sql.length())) + "...");
                            }
                        }
                        schema.setLength(0); // Reset for next statement
                    }
                }
            }

            System.out.println("Database schema initialized successfully");
            return true;
        } catch (SQLException | IOException e) {
            System.err.println("Error initializing schema: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Check if the database tables already exist
     * 
     * @param conn Database connection
     * @return true if tables exist
     * @throws SQLException
     */
    private static boolean tablesExist(Connection conn) throws SQLException {
        DatabaseMetaData meta = conn.getMetaData();
        ResultSet tables = meta.getTables(null, null, "users", null);
        return tables.next(); // If users table exists, assume schema is initialized
    }

    /**
     * Test database connection
     * 
     * @return true if connection successful
     */
    public static boolean testConnection() {
        try (Connection conn = DatabaseManager.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                ResultSet rs = stmt.executeQuery("SELECT 1");
                if (rs.next()) {
                    System.out.println("Database connection test successful");
                    return true;
                }
            }
            return false;
        } catch (SQLException e) {
            System.err.println("Database connection test failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Main method for command-line database initialization
     */
    public static void main(String[] args) {
        if (testConnection()) {
            boolean result = initializeSchema();
            System.out.println("Schema initialization " + (result ? "successful" : "failed"));
        } else {
            System.err.println("Cannot initialize schema: connection test failed");
        }
    }
}
