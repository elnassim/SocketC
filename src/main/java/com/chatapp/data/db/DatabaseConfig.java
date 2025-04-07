package com.chatapp.data.db;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Database configuration manager
 */
public class DatabaseConfig {
    private static final String CONFIG_FILE = "/db.properties";
    private static Properties properties;

    static {
        loadProperties();
    }

    /**
     * Load database properties from configuration file
     */
    private static void loadProperties() {
        properties = new Properties();

        try (InputStream input = DatabaseConfig.class.getResourceAsStream(CONFIG_FILE)) {
            if (input == null) {
                System.err.println("Database config file not found: " + CONFIG_FILE);
                return;
            }

            properties.load(input);
            System.out.println("Database configuration loaded successfully");
        } catch (IOException e) {
            System.err.println("Error loading database configuration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get database URL from properties
     * 
     * @return Database URL
     */
    public static String getJdbcUrl() {
        return properties.getProperty("jdbc.url");
    }

    /**
     * Get database username from properties
     * 
     * @return Database username
     */
    public static String getUsername() {
        return properties.getProperty("jdbc.user");
    }

    /**
     * Get database password from properties
     * 
     * @return Database password
     */
    public static String getPassword() {
        return properties.getProperty("jdbc.password");
    }

    /**
     * Get maximum pool size from properties
     * 
     * @return Maximum pool size or default value
     */
    public static int getMaxPoolSize() {
        String value = properties.getProperty("jdbc.pool.maxSize");
        return value != null ? Integer.parseInt(value) : 10;
    }

    /**
     * Get minimum idle connections from properties
     * 
     * @return Minimum idle connections or default value
     */
    public static int getMinIdle() {
        String value = properties.getProperty("jdbc.pool.minIdle");
        return value != null ? Integer.parseInt(value) : 5;
    }
}
