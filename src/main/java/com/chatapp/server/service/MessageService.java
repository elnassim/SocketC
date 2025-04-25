package com.chatapp.server.service;

import org.json.JSONObject;
import org.json.JSONException;
import com.chatapp.common.model.Message;
import com.chatapp.data.dao.MessageDAO;
import com.chatapp.data.dao.impl.MessageDAOImpl;
import com.chatapp.data.db.DatabaseManager;
import java.util.*;
import java.io.*;
import java.sql.*;

/**
 * Service class for message-related operations
 */
public class MessageService {

    private Map<String, List<String>> offlineMessages = new HashMap<>();
    private final MessageDAO messageDAO;
    private final ConversationService conversationService;

    public MessageService() {
        this.messageDAO = new MessageDAOImpl();
        this.conversationService = new ConversationService();
        // Add logging to verify initialization
        System.out.println("MessageService initialized with MessageDAO and ConversationService");
    }

    /**
     * Generate a unique conversation ID for two users
     */
    public String generateConversationId(String user1, String user2) {
        return conversationService.generateConversationId(user1, user2);
    }

    /**
     * Generate a unique group conversation ID for a group
     */
    public String generateGroupConversationId(int groupId) {
        return "group_" + groupId;
    }

    /**
     * Create a private message JSON object with routing information
     */
    public JSONObject createPrivateMessage(String sender, String recipient, String content) {
        Message message = new Message(sender, content, "private");
        message.setConversationParticipants(sender, recipient);
        messageDAO.save(message);
        return message.toJson();
    }

    /**
     * Create a group message JSON object
     */
    public JSONObject createGroupMessage(String sender, String groupName, String content) {
        try {
            // Get group ID
            String sql = "SELECT id FROM user_groups WHERE name = ?";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                
                pstmt.setString(1, groupName);
                ResultSet rs = pstmt.executeQuery();
                
                if (rs.next()) {
                    int groupId = rs.getInt("id");
                    String conversationId = generateGroupConversationId(groupId);
                    
                    // Create message
                    Message message = new Message(sender, content, "group");
                    message.setConversationId(conversationId);
                    messageDAO.save(message);
                    
                    // Ensure group conversation exists
                    ensureGroupConversationExists(groupId, conversationId);
                    
                    return message.toJson();
                }
            }
        } catch (SQLException e) {
            System.err.println("Error creating group message: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Create a broadcast message JSON object
     */
    public JSONObject createBroadcastMessage(String sender, String content) {
        Message message = new Message(sender, content, "broadcast");
        message.setConversationId("broadcast");
        messageDAO.save(message);
        return message.toJson();
    }

    /**
     * Store a message for offline delivery
     */
    public void storeOfflineMessage(String recipient, JSONObject message) {
        offlineMessages.computeIfAbsent(recipient, k -> new ArrayList<>());
        offlineMessages.get(recipient).add(message.toString());

        // Also save to conversation history via DB
        try {
            Message msgObj = Message.fromJson(message);
            conversationService.saveMessage(msgObj);
        } catch (Exception e) {
            System.err.println("Error saving offline message to history: " + e.getMessage());
        }
    }

    /**
     * Get and remove all offline messages for a user
     */
    public List<String> getOfflineMessages(String userEmail) {
        List<String> messages = offlineMessages.getOrDefault(userEmail, new ArrayList<>());
        offlineMessages.remove(userEmail);
        return messages;
    }
    
    /**
     * Get message history between users from the DB
     */
    public List<JSONObject> getMessageHistory(String user1, String user2) {
        return conversationService.getConversationHistory(user1, user2);
    }
    
    /**
     * Update message status (delivered/read)
     */
    public void updateMessageStatus(String messageId, String conversationId, Message.Status status) {
        // For now, just log the status change
        System.out.println("Message " + messageId + " status updated to " + status);
    }

    public void storeGroupMessage(String groupName, String sender, String content, String messageId) {
        try {
            // First get the group ID
            String getGroupIdSql = "SELECT id FROM user_groups WHERE name = ?";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(getGroupIdSql)) {
                
                pstmt.setString(1, groupName);
                ResultSet rs = pstmt.executeQuery();
                
                if (rs.next()) {
                    int groupId = rs.getInt("id");
                    String conversationId = generateGroupConversationId(groupId);
                    
                    // Create the message object
                    Message message = new Message(sender, content, "group");
                    message.setId(messageId);
                    message.setConversationId(conversationId);
                    message.setTimestamp(System.currentTimeMillis());
                    
                    // Save the message using the DAO
                    if (messageDAO.save(message)) {
                        // Ensure the group conversation exists
                        ensureGroupConversationExists(groupId, conversationId);
                        System.out.println("Stored group message: " + messageId + " in group: " + groupName);
                    } else {
                        System.err.println("Failed to store group message: " + messageId);
                    }
                } else {
                    System.err.println("Group not found: " + groupName);
                }
            }
        } catch (SQLException e) {
            System.err.println("Error storing group message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get message history for a group from the DB
     */
    public List<JSONObject> getGroupMessageHistory(String groupName) {
        try {
            // Get group ID
            String sql = "SELECT id FROM user_groups WHERE name = ?";
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement pstmt = conn.prepareStatement(sql)) {
                
                pstmt.setString(1, groupName);
                ResultSet rs = pstmt.executeQuery();
                
                if (rs.next()) {
                    int groupId = rs.getInt("id");
                    String conversationId = generateGroupConversationId(groupId);
                    
                    // Get messages for this conversation
                    List<JSONObject> messages = messageDAO.getConversationHistory(conversationId);
                    
                    // Add group information to each message
                    for (JSONObject message : messages) {
                        message.put("isGroup", true);
                        message.put("groupName", groupName);
                    }
                    
                    return messages;
                }
            }
        } catch (SQLException | JSONException e) {
            System.err.println("Error retrieving group message history: " + e.getMessage());
            e.printStackTrace();
        }
        return new ArrayList<>();
    }

    /**
     * Delete all messages for a specific group
     */
    public boolean deleteGroupMessages(String groupName) {
        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Get group ID
                String getGroupIdSql = "SELECT id FROM user_groups WHERE name = ?";
                int groupId;
                try (PreparedStatement pstmt = conn.prepareStatement(getGroupIdSql)) {
                    pstmt.setString(1, groupName);
                    ResultSet rs = pstmt.executeQuery();
                    if (!rs.next()) {
                        return false;
                    }
                    groupId = rs.getInt("id");
                }

                // Get conversation ID
                String conversationId = generateGroupConversationId(groupId);

                // Delete messages
                String deleteMessagesSql = "DELETE FROM messages WHERE conversation_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteMessagesSql)) {
                    pstmt.setString(1, conversationId);
                    pstmt.executeUpdate();
                }

                // Delete group conversation
                String deleteGroupConvSql = "DELETE FROM group_conversations WHERE group_id = ?";
                try (PreparedStatement pstmt = conn.prepareStatement(deleteGroupConvSql)) {
                    pstmt.setInt(1, groupId);
                    pstmt.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (SQLException e) {
                conn.rollback();
                System.err.println("Error deleting group messages: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        } catch (SQLException e) {
            System.err.println("Database connection error while deleting group messages: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    private void ensureGroupConversationExists(int groupId, String conversationId) {
        String sql = "INSERT IGNORE INTO group_conversations (group_id, conversation_id) VALUES (?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, groupId);
            pstmt.setString(2, conversationId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("Error ensuring group conversation exists: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
