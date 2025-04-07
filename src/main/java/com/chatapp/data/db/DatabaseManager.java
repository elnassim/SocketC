package com.chatapp.data.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class DatabaseManager {
    private static HikariDataSource dataSource;

    static {
        try {
            System.out.println("Initializing database connection pool...");

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(DatabaseConfig.getJdbcUrl());
            config.setUsername(DatabaseConfig.getUsername());
            config.setPassword(DatabaseConfig.getPassword());

            // Basic pool configuration
            config.setMaximumPoolSize(DatabaseConfig.getMaxPoolSize());
            config.setMinimumIdle(DatabaseConfig.getMinIdle());
            config.setIdleTimeout(300000); // 5 minutes

            // Connection testing
            config.setConnectionTestQuery("SELECT 1");

            // MySQL specific optimizations
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
            config.addDataSourceProperty("useServerPrepStmts", "true");

            try {
                dataSource = new HikariDataSource(config);
                System.out.println("Database connection pool initialized successfully");

                // Test connection
                try (Connection conn = dataSource.getConnection()) {
                    System.out.println("Successfully connected to database");
                }
            } catch (Exception e) {
                System.err.println("Error initializing connection pool: " + e.getMessage());
                e.printStackTrace();
            }
        } catch (Exception e) {
            System.err.println("Error loading database connection properties: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static Connection getConnection() throws SQLException {
        if (dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Database connection pool is not initialized or has been closed");
        }

        try {
            return dataSource.getConnection();
        } catch (SQLException e) {
            System.err.println("Error getting database connection: " + e.getMessage());
            throw e;
        }
    }

    public static void closePool() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            System.out.println("Database connection pool closed");
        }
    }
}