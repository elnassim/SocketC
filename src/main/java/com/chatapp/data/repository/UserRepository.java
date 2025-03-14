package com.chatapp.data.repository;

import com.chatapp.common.model.User;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Repository for user data access
 */
public class UserRepository {
    private Map<String, User> users = new HashMap<>();
    private static final String USERS_FILE_PATH = "/Users.json";

    public UserRepository() {
        loadUsers();
    }

    /**
     * Load users from the JSON file
     */
    private void loadUsers() {
        try {
            InputStream is = getClass().getResourceAsStream(USERS_FILE_PATH);
            if (is == null) {
                System.err.println("Could not find Users.json in resources. Using default users.");
                // Add default users if file is not found
                addDefaultUsers();
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
            reader.close();

            JSONArray usersArray = new JSONArray(jsonContent.toString());
            for (int i = 0; i < usersArray.length(); i++) {
                JSONObject userJson = usersArray.getJSONObject(i);
                String email = userJson.getString("email");
                String password = userJson.getString("password");
                String username = email.substring(0, email.indexOf("@"));

                users.put(username, new User(username, password, email));
            }

        } catch (Exception e) {
            System.err.println("Error loading users: " + e.getMessage());
            e.printStackTrace();
            // Add default users if there's an error
            addDefaultUsers();
        }
    }

    /**
     * Add default users for testing
     */
    private void addDefaultUsers() {
        users.put("admin", new User("admin", "admin123", "admin@example.com"));
        users.put("user", new User("user", "user123", "user@example.com"));
    }

    /**
     * Authenticate a user
     * 
     * @param username Username
     * @param password Password
     * @return true if authentication successful
     */
    public boolean authenticate(String username, String password) {
        User user = users.get(username);
        return user != null && user.getPassword().equals(password);
    }

    /**
     * Register a new user
     * 
     * @param username Username
     * @param password Password
     * @param email    Email
     * @return true if registration successful
     */
    public boolean registerUser(String username, String password, String email) {
        if (users.containsKey(username)) {
            return false; // Username already exists
        }
        users.put(username, new User(username, password, email));
        return true;
    }

    /**
     * Get a list of all users
     * 
     * @return List of users
     */
    public List<User> getAllUsers() {
        return new ArrayList<>(users.values());
    }

    /**
     * Find a user by email
     * 
     * @param email Email to search for
     * @return User if found, null otherwise
     */
    public User findByEmail(String email) {
        for (User user : users.values()) {
            if (user.getEmail().equals(email)) {
                return user;
            }
        }
        return null;
    }
}
