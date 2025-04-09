package com.chatapp.data.dao.impl;

import com.chatapp.common.model.User;
import com.chatapp.data.dao.UserDAO;
import com.chatapp.data.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class UserDAOImpl implements UserDAO {

    @Override
    public User findByEmail(String email) {
        String query = "SELECT * FROM users WHERE email = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String username = rs.getString("username");
                String password = rs.getString("password");
                return new User(username, password, email);
            }
            return null;
        } catch (SQLException e) {
            System.err.println("Database error finding user: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public boolean authenticate(String email, String password) {
        String query = "SELECT password FROM users WHERE email = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {
                String storedPassword = rs.getString("password");
                return storedPassword.equals(password); // In a real app, use password hashing
            }
            return false;
        } catch (SQLException e) {
            System.err.println("Database error during authentication: " + e.getMessage());
            return false;
        }
    }

    @Override
    public boolean create(User user) {
        String query = "INSERT INTO users (email, username, password) VALUES (?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, user.getEmail());
            stmt.setString(2, user.getUsername());
            stmt.setString(3, user.getPassword());

            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Database error creating user: " + e.getMessage());
            return false;
        }
    }

    @Override
    public List<User> findAll() {
        String query = "SELECT * FROM users";
        List<User> users = new ArrayList<>();

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                String username = rs.getString("username");
                String password = rs.getString("password");
                String email = rs.getString("email");
                users.add(new User(username, password, email));
            }
            return users;
        } catch (SQLException e) {
            System.err.println("Database error retrieving all users: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @Override
    public boolean update(User user) {
        // First check if user exists
        if (findByEmail(user.getEmail()) == null) {
            System.err.println("Cannot update non-existent user: " + user.getEmail());
            return false;
        }

        String query = "UPDATE users SET username = ?, password = ? WHERE email = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getPassword());
            stmt.setString(3, user.getEmail());

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("User updated successfully: " + user.getEmail());
            }
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Database error updating user: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean delete(String email) {
        // First check if user exists
        if (findByEmail(email) == null) {
            System.err.println("Cannot delete non-existent user: " + email);
            return false;
        }

        // Start transaction to manage cascade deletion
        Connection conn = null;
        try {
            conn = DatabaseManager.getConnection();
            conn.setAutoCommit(false);

            // First delete from contacts table (both directions)
            String contactsQuery = "DELETE FROM contacts WHERE user_email = ? OR contact_email = ?";
            try (PreparedStatement stmt = conn.prepareStatement(contactsQuery)) {
                stmt.setString(1, email);
                stmt.setString(2, email);
                stmt.executeUpdate();
            }

            // Delete from conversation_participants
            String partQuery = "DELETE FROM conversation_participants WHERE user_email = ?";
            try (PreparedStatement stmt = conn.prepareStatement(partQuery)) {
                stmt.setString(1, email);
                stmt.executeUpdate();
            }

            // Find messages sent by user
            String findMsgQuery = "SELECT id FROM messages WHERE sender_email = ?";
            List<String> messageIds = new ArrayList<>();
            try (PreparedStatement stmt = conn.prepareStatement(findMsgQuery)) {
                stmt.setString(1, email);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    messageIds.add(rs.getString("id"));
                }
            }

            // Delete messages sent by user
            if (!messageIds.isEmpty()) {
                String deleteQuery = "DELETE FROM messages WHERE sender_email = ?";
                try (PreparedStatement stmt = conn.prepareStatement(deleteQuery)) {
                    stmt.setString(1, email);
                    stmt.executeUpdate();
                }
            }

            // Finally delete the user
            String userQuery = "DELETE FROM users WHERE email = ?";
            int rowsAffected;
            try (PreparedStatement stmt = conn.prepareStatement(userQuery)) {
                stmt.setString(1, email);
                rowsAffected = stmt.executeUpdate();
            }

            // Commit all changes
            conn.commit();

            if (rowsAffected > 0) {
                System.out.println("User deleted successfully: " + email);
            }
            return rowsAffected > 0;

        } catch (SQLException e) {
            // Rollback transaction on error
            if (conn != null) {
                try {
                    conn.rollback();
                } catch (SQLException ex) {
                    System.err.println("Error during transaction rollback: " + ex.getMessage());
                }
            }
            System.err.println("Database error deleting user: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    System.err.println("Error resetting connection state: " + e.getMessage());
                }
            }
        }
    }

    // --- Méthode ajoutée pour mettre à jour le profil (username, password, profile_image) ---
    @Override
    public boolean updateUserProfile(User user) {
        // First check if user exists
        if (findByEmail(user.getEmail()) == null) {
            System.err.println("Cannot update profile of non-existent user: " + user.getEmail());
            return false;
        }

        String query = "UPDATE users SET username = ?, password = ?, ProfilePhoto = ? WHERE email = ?";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {

            stmt.setString(1, user.getUsername());
            stmt.setString(2, user.getPassword());
            stmt.setString(3, user.getProfilePhoto());
            stmt.setString(4, user.getEmail());

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("User profile updated successfully: " + user.getEmail());
            }
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Error updating user profile: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}
