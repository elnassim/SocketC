package com.chatapp.server.service;

import java.io.*;
import java.net.URL;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.chatapp.common.model.User;
import com.chatapp.data.repository.UserRepository;

/**
 * Service class for user-related operations
 */
public class UserService {
    
    private final UserRepository userRepository;
    
    public UserService() {
        this.userRepository = new UserRepository();
    }
    
    /**
     * Authenticate a user with email and password
     * 
     * @param email    User email
     * @param password User password
     * @return true if authentication successful, false otherwise
     */
    public boolean authenticateUser(String email, String password) {
        // Find user by email and check password match
        User user = userRepository.findByEmail(email);
        return user != null && user.getPassword().equals(password);
    }
    
    /**
     * Authenticate a user with username and password
     * 
     * @param username Username
     * @param password Password
     * @return true if authentication successful, false otherwise
     */
    public boolean authenticateByUsername(String username, String password) {
        return userRepository.authenticate(username, password);
    }
    
    /**
     * Register a new user
     * 
     * @param username Username
     * @param password Password
     * @param email    Email address
     * @return true if registration successful, false otherwise
     */
    public boolean registerUser(String username, String password, String email) {
        return userRepository.registerUser(username, password, email);
    }
    
    /**
     * Get all users in the system
     * 
     * @return List of all users
     */
    public java.util.List<User> getAllUsers() {
        return userRepository.getAllUsers();
    }
    
    /**
     * Find a user by their email address
     * 
     * @param email Email to search for
     * @return User object if found, null otherwise
     */
    public User findUserByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    
    /**
     * Check if a user exists with the given email
     * 
     * @param email Email to check
     * @return true if user exists, false otherwise
     */
    public boolean userExistsByEmail(String email) {
        return findUserByEmail(email) != null;
    }
}