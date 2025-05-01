package com.chatapp.server.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.Base64;

import com.chatapp.common.model.User;
import com.chatapp.data.dao.UserDAO;
import com.chatapp.data.dao.impl.UserDAOImpl;

public class UserService {
    private final UserDAO userDAO;
    
    public UserService() {
        this.userDAO = new UserDAOImpl();
    }
    
    public boolean authenticateUser(String email, String password) {
        try {
            User user = userDAO.findByEmail(email);
            if (user == null) {
                return false;
            }
            String hashedPassword = hashPassword(password);
            return user.getPassword().equals(hashedPassword);
        } catch (SQLException e) {
            System.err.println("Database error during authentication: " + e.getMessage());
            return false;
        }
    }
    
    public boolean registerUser(String username, String password, String email) {
        try {
            // Check if user already exists
            if (userDAO.findByEmail(email) != null) {
                System.out.println("User with email " + email + " already exists");
                return false;
            }
            
            // Hash the password before storing
            String hashedPassword = hashPassword(password);
            User newUser = new User(username, hashedPassword, email);
            
            boolean success = userDAO.create(newUser);
            if (success) {
                System.out.println("Successfully registered user: " + email);
            } else {
                System.err.println("Failed to register user: " + email);
            }
            return success;
        } catch (SQLException e) {
            System.err.println("Database error during registration: " + e.getMessage());
            return false;
        }
    }
    
    public User findUserByEmail(String email) {
        try {
            return userDAO.findByEmail(email);
        } catch (SQLException e) {
            System.err.println("Database error finding user: " + e.getMessage());
            return null;
        }
    }
    
    public boolean userExistsByEmail(String email) {
        return findUserByEmail(email) != null;
    }
    
    /**
     * Met à jour le profil utilisateur en base.
     * Ici, nous supposons que pour la mise à jour de profil, on souhaite actualiser
     * le username (peut servir de displayName) et le profile_image.
     * Pour simplifier, nous utilisons ici newUsername et newPassword (ce dernier pouvant rester inchangé)
     * ainsi que la nouvelle URL de photo.
     */
    public boolean updateUserProfile(String email, String newUsername, String newPassword) {
        try {
            User user = findUserByEmail(email);
            if (user == null) {
                return false;
            }
            user.setUsername(newUsername);
            if (newPassword != null && !newPassword.isEmpty()) {
                user.setPassword(hashPassword(newPassword));
            }
            return userDAO.updateUserProfile(user);
        } catch (SQLException e) {
            System.err.println("Database error updating profile: " + e.getMessage());
            return false;
        }
    }
    
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            System.err.println("Error hashing password: " + e.getMessage());
            return password; // Fallback to plain password if hashing fails
        }
    }
}
