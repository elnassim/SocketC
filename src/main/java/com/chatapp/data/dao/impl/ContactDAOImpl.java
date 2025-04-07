package com.chatapp.data.dao.impl;

import com.chatapp.data.dao.ContactDAO;
import com.chatapp.data.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ContactDAOImpl implements ContactDAO {

    @Override
    public List<String> getContacts(String userEmail) {
        String query = "SELECT contact_email FROM contacts WHERE user_email = ?";
        List<String> contacts = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, userEmail);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                contacts.add(rs.getString("contact_email"));
            }
            
            return contacts;
        } catch (SQLException e) {
            System.err.println("Database error retrieving contacts: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @Override
    public boolean addContact(String userEmail, String contactEmail) {
        // Check if the contact user exists
        if (!userExists(contactEmail)) {
            System.err.println("Cannot add non-existent contact: " + contactEmail);
            return false;
        }
        
        String query = "INSERT INTO contacts (user_email, contact_email) VALUES (?, ?)";
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, userEmail);
            stmt.setString(2, contactEmail);
            
            try {
                int rowsAffected = stmt.executeUpdate();
                return rowsAffected > 0;
            } catch (SQLException e) {
                // Ignore duplicate contacts
                if (e.getErrorCode() == 1062) { // MySQL duplicate entry error
                    System.out.println("Contact already exists: " + contactEmail + " for user: " + userEmail);
                    return true;
                } else {
                    throw e;
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error adding contact: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean removeContact(String userEmail, String contactEmail) {
        String query = "DELETE FROM contacts WHERE user_email = ? AND contact_email = ?";
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, userEmail);
            stmt.setString(2, contactEmail);
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Database error removing contact: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    private boolean userExists(String email) {
        String query = "SELECT COUNT(*) FROM users WHERE email = ?";
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, email);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return rs.getInt(1) > 0;
            }
            return false;
        } catch (SQLException e) {
            System.err.println("Database error checking if user exists: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
}