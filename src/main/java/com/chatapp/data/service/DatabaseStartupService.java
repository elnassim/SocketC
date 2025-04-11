package com.chatapp.data.service;

import com.chatapp.data.db.DatabaseUtils;
import com.chatapp.data.db.DatabaseManager;

/**
 * Service to handle database startup and initialization
 */
public class DatabaseStartupService {

    /**
     * Initialize the database system
     * 
     * @return true if initialization successful
     */
    public boolean initialize() {
        System.out.println("Starting database initialization...");

        // Test database connection
        if (!DatabaseUtils.testConnection()) {
            System.err.println("Database connection failed. Please check your configuration.");
            return false;
        }

        // Initialize schema if needed
        if (!DatabaseUtils.initializeSchema()) {
            System.err.println("Schema initialization failed.");
            return false;
        }

        System.out.println("Database initialized successfully");
        return true;
    }

    /**
     * Shutdown the database system
     */
    public void shutdown() {
        System.out.println("Shutting down database connection...");
        DatabaseManager.closePool();
    }

    /**
     * Main method for standalone initialization
     */
    public static void main(String[] args) {
        DatabaseStartupService service = new DatabaseStartupService();
        boolean success = service.initialize();
        System.out.println("Database initialization: " + (success ? "SUCCESS" : "FAILED"));

        // Only shut down if initialization succeeded
        if (success) {
            service.shutdown();
        }
    }
}
