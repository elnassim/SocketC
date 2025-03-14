package com.chatapp;

import java.util.HashMap;
import java.util.Map;

public class UserRepository {
    private static Map<String, User> users = new HashMap<>();

    static {
        // Add some default users for testing
        users.put("admin", new User("admin", "admin123", "admin@example.com"));
        users.put("user", new User("user", "user123", "user@example.com"));
    }

    public static boolean authenticate(String username, String password) {
        User user = users.get(username);
        return user != null && user.getPassword().equals(password);
    }

    public static boolean registerUser(String username, String password, String email) {
        if (users.containsKey(username)) {
            return false; // Username already exists
        }
        users.put(username, new User(username, password, email));
        return true;
    }
}