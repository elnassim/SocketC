package com.chatapp.data.dao.impl;

import com.chatapp.common.model.Message;
import com.chatapp.data.dao.MessageDAO;
import com.chatapp.data.db.DatabaseManager;
import org.json.JSONException;
import org.json.JSONObject;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class MessageDAOImpl implements MessageDAO {

    @Override
    public boolean save(Message message) {
        // First ensure conversation exists
        ensureConversationExists(message.getConversationId());
        
        String query = "INSERT INTO messages (id, sender_email, conversation_id, content, type, status, " +
                "timestamp, delivered, read_status) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, message.getId());
            stmt.setString(2, message.getSender());
            stmt.setString(3, message.getConversationId());
            stmt.setString(4, message.getContent());
            stmt.setString(5, message.getType());
            stmt.setString(6, message.getStatus() != null ? message.getStatus().name() : "SENT");
            stmt.setLong(7, message.getTimestamp());
            stmt.setBoolean(8, message.isDelivered());
            stmt.setBoolean(9, message.isRead());
            
            int rowsAffected = stmt.executeUpdate();
            System.out.println("Saved message with ID: " + message.getId());
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Database error saving message: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<JSONObject> getConversationHistory(String user1, String user2) {
        // Generate conversation ID using the same algorithm as in ConversationService
        String conversationId = generateConversationId(user1, user2);
        
        String query = "SELECT * FROM messages WHERE conversation_id = ? ORDER BY timestamp ASC";
        List<JSONObject> messages = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, conversationId);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                JSONObject message = new JSONObject();
                message.put("id", rs.getString("id"));
                message.put("sender", rs.getString("sender_email"));
                message.put("conversationId", rs.getString("conversation_id"));
                message.put("content", rs.getString("content"));
                message.put("type", rs.getString("type"));
                message.put("status", rs.getString("status"));
                message.put("timestamp", rs.getLong("timestamp"));
                message.put("delivered", rs.getBoolean("delivered"));
                message.put("read", rs.getBoolean("read_status"));
                
                messages.add(message);
            }
            
            return messages;
        } catch (SQLException | JSONException e) {
            System.err.println("Database error retrieving conversation history: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @Override
    public boolean updateStatus(String messageId, Message.Status status) {
        String query = "UPDATE messages SET status = ?, delivered = ?, read_status = ? WHERE id = ?";
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, status.name());
            stmt.setBoolean(2, status == Message.Status.DELIVERED || status == Message.Status.READ);
            stmt.setBoolean(3, status == Message.Status.READ);
            stmt.setString(4, messageId);
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Database error updating message status: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public List<String> getOfflineMessages(String userEmail) {
        // Find all conversations where this user is a participant
        String query = "SELECT m.* FROM messages m " +
                "JOIN conversation_participants cp ON m.conversation_id = cp.conversation_id " +
                "WHERE cp.user_email = ? AND m.sender_email != ? AND (m.delivered = false OR m.read_status = false)";
        
        List<String> messages = new ArrayList<>();
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, userEmail);
            stmt.setString(2, userEmail);  // Exclude messages sent by this user
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                JSONObject message = new JSONObject();
                message.put("id", rs.getString("id"));
                message.put("sender", rs.getString("sender_email"));
                message.put("conversationId", rs.getString("conversation_id"));
                message.put("content", rs.getString("content"));
                message.put("type", rs.getString("type"));
                message.put("status", rs.getString("status"));
                message.put("timestamp", rs.getLong("timestamp"));
                message.put("delivered", rs.getBoolean("delivered"));
                message.put("read", rs.getBoolean("read_status"));
                
                messages.add(message.toString());
            }
            
            return messages;
        } catch (SQLException | JSONException e) {
            System.err.println("Database error retrieving offline messages: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    @Override
    public boolean deleteMessage(String messageId) {
        String query = "DELETE FROM messages WHERE id = ?";
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, messageId);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Database error deleting message: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    private boolean ensureConversationExists(String conversationId) {
        // Check if conversation exists
        String checkQuery = "SELECT COUNT(*) FROM conversations WHERE id = ?";
        String insertQuery = "INSERT INTO conversations (id, is_group) VALUES (?, false)";
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement checkStmt = conn.prepareStatement(checkQuery)) {
            
            checkStmt.setString(1, conversationId);
            ResultSet rs = checkStmt.executeQuery();
            
            if (rs.next() && rs.getInt(1) == 0) {
                // Conversation doesn't exist, create it
                try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery)) {
                    insertStmt.setString(1, conversationId);
                    insertStmt.executeUpdate();
                    
                    // Add conversation participants
                    String[] participants = conversationId.split("_");
                    if (participants.length >= 2) {
                        addConversationParticipant(conn, conversationId, participants[0]);
                        addConversationParticipant(conn, conversationId, participants[1]);
                    }
                    
                    System.out.println("Created new conversation: " + conversationId);
                }
            }
            return true;
        } catch (SQLException e) {
            System.err.println("Database error checking/creating conversation: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    private void addConversationParticipant(Connection conn, String conversationId, String userEmail) throws SQLException {
        String query = "INSERT INTO conversation_participants (conversation_id, user_email) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, conversationId);
            stmt.setString(2, userEmail);
            stmt.executeUpdate();
        }
    }
    
    private String generateConversationId(String user1, String user2) {
        // Ensure deterministic ordering regardless of parameter order
        if (user1.compareTo(user2) <= 0) {
            return user1 + "_" + user2;
        } else {
            return user2 + "_" + user1;
        }
    }
}