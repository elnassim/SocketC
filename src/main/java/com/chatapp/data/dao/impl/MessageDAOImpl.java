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
import java.util.Comparator;
import java.util.List;

public class MessageDAOImpl implements MessageDAO {

    @Override
    public boolean save(Message message) {
        System.out.println("Attempting to save message: " + message.getId());
        System.out.println("  Type: " + message.getType());
        System.out.println("  Conversation ID: " + message.getConversationId());
        
        // First ensure conversation exists
        boolean conversationCreated = ensureConversationExists(message.getConversationId());
        if (!conversationCreated) {
            System.err.println("Failed to create conversation - cannot save message");
            return false;
        }
        
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
            System.out.println("Message save result: " + (rowsAffected > 0 ? "SUCCESS" : "FAILED"));
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Database error saving message: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    // In MessageDAOImpl.java, update getConversationHistory method:

    @Override
    public List<JSONObject> getConversationHistory(String user1, String user2) {
        // Generate conversation ID the same way as when saving messages
        String conversationId = generateConversationId(user1, user2);
        System.out.println("Fetching history for conversation ID: " + conversationId);
        
        List<JSONObject> messages = new ArrayList<>();
        
        // Query to get all messages in this conversation
        String query = "SELECT * FROM messages WHERE conversation_id = ? ORDER BY timestamp ASC";
        
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
                message.put("timestamp", rs.getLong("timestamp"));
                message.put("delivered", rs.getBoolean("delivered"));
                message.put("read", rs.getBoolean("read_status"));
                
                messages.add(message);
            }
            
            System.out.println("Found " + messages.size() + " messages in conversation");
            return messages;
        } catch (Exception e) {
            System.err.println("Error retrieving conversation history: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }
    
    private String generateConversationId(String user1, String user2) {
        // Sort emails to ensure the same ID regardless of order
        if (user1.compareTo(user2) > 0) {
            String temp = user1;
            user1 = user2;
            user2 = temp;
        }
        return user1 + "_" + user2;
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
public List<JSONObject> getGroupMessages(String groupConversationId) {
    // Ensure the conversation ID has the correct prefix
    if (!groupConversationId.startsWith("group_")) {
        groupConversationId = "group_" + groupConversationId;
    }
    
    System.out.println("Fetching messages for group conversation: " + groupConversationId);
    
    // Initialize the messages list before using it
    List<JSONObject> messages = new ArrayList<>();
    
    // Query to get all messages for this group
    String query = "SELECT * FROM messages WHERE conversation_id = ? ORDER BY timestamp ASC";
    
    try (Connection conn = DatabaseManager.getConnection();
         PreparedStatement stmt = conn.prepareStatement(query)) {
        
        stmt.setString(1, groupConversationId);
        ResultSet rs = stmt.executeQuery();
        
        while (rs.next()) {
            JSONObject message = new JSONObject();
            message.put("id", rs.getString("id"));
            message.put("sender", rs.getString("sender_email"));
            message.put("conversationId", rs.getString("conversation_id"));
            message.put("content", rs.getString("content"));
            message.put("type", rs.getString("type"));
            message.put("timestamp", rs.getLong("timestamp"));
            message.put("delivered", rs.getBoolean("delivered"));
            message.put("read", rs.getBoolean("read_status"));
            message.put("isGroup", true);  // Add this flag to identify it as a group message
            message.put("groupName", groupConversationId.substring(6));  // Remove 'group_' prefix
            
            messages.add(message);
        }
        
        System.out.println("Found " + messages.size() + " messages for group with ID: " + groupConversationId);
        return messages;
    } catch (Exception e) {
        System.err.println("Error retrieving group messages: " + e.getMessage());
        e.printStackTrace();
        return new ArrayList<>();
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
        boolean isGroup = conversationId.startsWith("group_");
        String groupName = isGroup ? conversationId.substring(6) : null;
        
        try (Connection conn = DatabaseManager.getConnection()) {
            // Check if conversation exists
            String checkQuery = "SELECT COUNT(*) FROM conversations WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(checkQuery)) {
                stmt.setString(1, conversationId);
                ResultSet rs = stmt.executeQuery();
                
                if (rs.next() && rs.getInt(1) == 0) {
                    System.out.println("Creating new conversation: " + conversationId);
                    // Create conversation if it doesn't exist
                    String insertQuery = "INSERT INTO conversations (id, is_group) VALUES (?, ?)";
                    try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery)) {
                        insertStmt.setString(1, conversationId);
                        insertStmt.setBoolean(2, isGroup);
                        insertStmt.executeUpdate();
                    }
                    
                    if (isGroup) {
                        System.out.println("Adding group members as participants for: " + groupName);
                        addGroupMembersAsParticipants(conn, conversationId, groupName);
                    }
                }
            }
            return true; // Conversation now exists
        } catch (SQLException e) {
            System.err.println("Database error ensuring conversation exists: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
   

    // Add this method in MessageDAOImpl
private void addGroupMembersAsParticipants(Connection conn, String conversationId, String groupName) {
    try {
        // First, get all group members
        String queryMembers = "SELECT user_email FROM group_members JOIN user_groups ON group_members.group_id = user_groups.id WHERE user_groups.name = ?";
        List<String> members = new ArrayList<>();
        
        try (PreparedStatement stmt = conn.prepareStatement(queryMembers)) {
            stmt.setString(1, groupName);
            ResultSet rs = stmt.executeQuery();
            
            while (rs.next()) {
                members.add(rs.getString("user_email"));
            }
        }
        
        System.out.println("Adding " + members.size() + " members as participants to conversation: " + conversationId);
        
        // Now add each member as a participant
        String addParticipantQuery = "INSERT INTO conversation_participants (conversation_id, user_email) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(addParticipantQuery)) {
            for (String member : members) {
                stmt.setString(1, conversationId);
                stmt.setString(2, member);
                try {
                    stmt.executeUpdate();
                    System.out.println("Added " + member + " to conversation " + conversationId);
                } catch (SQLException e) {
                    if (e.getMessage().contains("Duplicate entry")) {
                        System.out.println("User " + member + " already a participant in conversation " + conversationId);
                    } else {
                        throw e; // Rethrow if it's a different error
                    }
                }
            }
        }
    } catch (SQLException e) {
        System.err.println("Error adding group members as participants: " + e.getMessage());
        e.printStackTrace();
    }
}
    private void addConversationParticipant(Connection conn, String conversationId, String userEmail) throws SQLException {
        // First check if participant already exists
        String checkQuery = "SELECT COUNT(*) FROM conversation_participants WHERE conversation_id = ? AND user_email = ?";
        try (PreparedStatement stmt = conn.prepareStatement(checkQuery)) {
            stmt.setString(1, conversationId);
            stmt.setString(2, userEmail);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next() && rs.getInt(1) > 0) {
                // Participant already exists
                return;
            }
        }
        
        // Add participant if they don't exist
        String query = "INSERT INTO conversation_participants (conversation_id, user_email) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(query)) {
            stmt.setString(1, conversationId);
            stmt.setString(2, userEmail);
            stmt.executeUpdate();
            System.out.println("Added user " + userEmail + " as participant in conversation " + conversationId);
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) { // MySQL duplicate entry error
                // Ignore duplicate entry errors
                System.out.println("User " + userEmail + " already a participant in conversation " + conversationId);
            } else {
                throw e; // Rethrow if it's a different error
            }
        }
    }
    
    
}