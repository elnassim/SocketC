package com.chatapp.server.service;

import com.chatapp.common.model.Group;
import com.chatapp.common.model.Message;
import com.chatapp.data.dao.GroupDAO;
import com.chatapp.data.dao.MessageDAO;
import com.chatapp.data.dao.impl.GroupDAOImpl;
import com.chatapp.data.dao.impl.MessageDAOImpl;
import com.chatapp.data.db.DatabaseManager;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.List;

public class GroupService {
    private final GroupDAO groupDAO;
    
    public GroupService() {
        this.groupDAO = new GroupDAOImpl();
    }
    
public Group createGroup(String groupName, List<String> members) {
    System.out.println("GroupService: Creating group " + groupName + " with " + members.size() + " members");
    Group group = new Group(groupName, members);
    boolean success = groupDAO.createGroup(group);
    if (success) {
        System.out.println("GroupService: Group created successfully in the database");
        
        // *** ADD THIS CODE: Create the conversation record for the group ***
        String conversationId = "group_" + groupName;
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT INTO conversations (id, is_group) VALUES (?, ?)")) {
            stmt.setString(1, conversationId);
            stmt.setBoolean(2, true);
            stmt.executeUpdate();
            System.out.println("Created group conversation record: " + conversationId);
            
            // Add all members as participants
            String addParticipantQuery = "INSERT INTO conversation_participants (conversation_id, user_email) VALUES (?, ?)";
            try (PreparedStatement addStmt = conn.prepareStatement(addParticipantQuery)) {
                for (String member : members) {
                    addStmt.setString(1, conversationId);
                    addStmt.setString(2, member);
                    try {
                        addStmt.executeUpdate();
                    } catch (SQLException e) {
                        // Ignore duplicate key errors
                        if (!e.getMessage().contains("Duplicate entry")) {
                            throw e;
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error creating group conversation record: " + e.getMessage());
        }
        
        return group;
    }
    System.out.println("GroupService: Failed to create group in database");
    return null;
}

    /**
     * Ensures that a group conversation exists in the database
     */
    private void ensureGroupConversationExists(String groupName) {
        try {
            String conversationId = "group_" + groupName;
            
            try (Connection conn = DatabaseManager.getConnection()) {
                // Check if conversation exists
                String checkQuery = "SELECT COUNT(*) FROM conversations WHERE id = ?";
                try (PreparedStatement stmt = conn.prepareStatement(checkQuery)) {
                    stmt.setString(1, conversationId);
                    ResultSet rs = stmt.executeQuery();
                    
                    if (rs.next() && rs.getInt(1) == 0) {
                        // Create conversation entry if it doesn't exist
                        String insertQuery = "INSERT INTO conversations (id, is_group) VALUES (?, true)";
                        try (PreparedStatement insertStmt = conn.prepareStatement(insertQuery)) {
                            insertStmt.setString(1, conversationId);
                            insertStmt.executeUpdate();
                            System.out.println("Created group conversation in database: " + conversationId);
                        }
                        
                        // Add all group members as participants
                        Group group = findGroupByName(groupName);
                        if (group != null) {
                            String addParticipantQuery = 
                                "INSERT INTO conversation_participants (conversation_id, user_email) VALUES (?, ?)";
                            try (PreparedStatement addStmt = conn.prepareStatement(addParticipantQuery)) {
                                for (String member : group.getMembersEmails()) {
                                    addStmt.setString(1, conversationId);
                                    addStmt.setString(2, member);
                                    try {
                                        addStmt.executeUpdate();
                                        System.out.println("Added " + member + " to conversation " + conversationId);
                                    } catch (SQLException e) {
                                        // Ignore duplicate errors (code 1062)
                                        if (e.getErrorCode() != 1062) {
                                            throw e;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Database error ensuring group conversation exists: " + e.getMessage());
            e.printStackTrace();
        }
    }


 /**
 * Ultra-direct method to save group messages without any abstractions
 */
public boolean ultraDirectSaveGroupMessage(Message message, String groupName) {
    String conversationId = "group_" + groupName;
    message.setConversationId(conversationId);
    
    // Variables for our SQL values
    String messageId = message.getId();
    String sender = message.getSender();
    String content = message.getContent();
    long timestamp = System.currentTimeMillis();
    
    System.out.println("ULTRA DIRECT SAVE: " + messageId + " to " + conversationId);
    
    try {
        // Use a single statement with raw SQL
        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            
            // First, ensure the conversation exists
            String createConversationSql = String.format(
                "INSERT IGNORE INTO conversations (id, is_group) VALUES ('%s', TRUE)",
                conversationId.replace("'", "''"));
                
            stmt.executeUpdate(createConversationSql);
            System.out.println("Ensured conversation exists with raw SQL");
            
            // Now directly insert the message
            String insertMessageSql = String.format(
                "INSERT INTO messages (id, sender_email, conversation_id, content, type, status, timestamp, delivered, read_status) " +
                "VALUES ('%s', '%s', '%s', '%s', '%s', '%s', %d, %d, %d)",
                // Properly escape values
                messageId.replace("'", "''"),
                sender.replace("'", "''"), 
                conversationId.replace("'", "''"),
                content.replace("'", "''"),
                "group",
                "SENT",
                timestamp,
                0,  // FALSE for delivered
                0); // FALSE for read_status
                
            System.out.println("Executing SQL: " + insertMessageSql);
            
            int rows = stmt.executeUpdate(insertMessageSql);
            System.out.println("Ultra direct save result: " + (rows > 0 ? "SUCCESS" : "FAILED"));
            
            if (rows > 0) {
                // Add debug to verify message exists
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM messages WHERE id = '" + 
                    messageId.replace("'", "''") + "'");
                if (rs.next()) {
                    System.out.println("Message exists in DB: " + (rs.getInt(1) > 0));
                }
            }
            
            return rows > 0;
        }
    } catch (SQLException e) {
        System.err.println("ULTRA DIRECT SAVE ERROR: " + e.getMessage());
        System.err.println("SQL State: " + e.getSQLState());
        System.err.println("Error Code: " + e.getErrorCode());
        e.printStackTrace();
        return false;
    }
}


    /**
 * Super direct method to save group messages without any abstractions
 */
public boolean directSaveGroupMessage(Message message, String groupName) {
    String conversationId = "group_" + groupName;
    message.setConversationId(conversationId);
    
    System.out.println("SUPER DIRECT SAVE: Saving group message to database");
    System.out.println("ID: " + message.getId());
    System.out.println("Sender: " + message.getSender());
    System.out.println("Content: " + message.getContent());
    System.out.println("Conversation ID: " + conversationId);
    
    // First, force the conversation to exist
    try {
        // Create conversation directly
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement("INSERT IGNORE INTO conversations (id, is_group) VALUES (?, ?)")) {
            stmt.setString(1, conversationId);
            stmt.setBoolean(2, true);
            stmt.executeUpdate();
            System.out.println("Ensured conversation exists: " + conversationId);
        }
        
        // Now directly insert the message without any complex logic
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                "INSERT INTO messages (id, sender_email, conversation_id, content, type, status, timestamp, delivered, read_status) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            
            stmt.setString(1, message.getId());
            stmt.setString(2, message.getSender());
            stmt.setString(3, conversationId);
            stmt.setString(4, message.getContent());
            stmt.setString(5, "group");
            stmt.setString(6, "SENT");
            stmt.setLong(7, System.currentTimeMillis());
            stmt.setBoolean(8, false);
            stmt.setBoolean(9, false);
            
            int rows = stmt.executeUpdate();
            System.out.println("Direct message insert result: " + (rows > 0 ? "SUCCESS" : "FAILED"));
            return rows > 0;
        }
    } catch (SQLException e) {
        System.err.println("SUPER DIRECT SAVE ERROR: " + e.getMessage());
        System.err.println("SQL State: " + e.getSQLState());
        System.err.println("Error Code: " + e.getErrorCode());
        e.printStackTrace();

        if (e.getMessage().contains("Duplicate entry")) {
            System.err.println("DUPLICATE KEY DETECTED. Trying to find conflicting message:");
            try (Connection conn = DatabaseManager.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT * FROM messages WHERE id = ?")) {
                stmt.setString(1, message.getId());
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    System.err.println("Message with ID " + message.getId() + " already exists:");
                    System.err.println("  Content: " + rs.getString("content"));
                    System.err.println("  Sender: " + rs.getString("sender_email"));
                    System.err.println("  Conversation: " + rs.getString("conversation_id"));
                }
            } catch (SQLException ex) {
                System.err.println("Error checking duplicate message: " + ex.getMessage());
            }
        }
        return false;
    }
}

    /**
     * Saves a group message to the database
     */
    public void saveGroupMessage(Message message, String groupName) {
        try {
            // Ensure message has correct group conversation ID format
            String conversationId = "group_" + groupName;
            message.setConversationId(conversationId);
            
            System.out.println("DEBUG - Saving group message:");
            System.out.println("  ID: " + message.getId());
            System.out.println("  From: " + message.getSender());
            System.out.println("  Content: " + message.getContent());
            System.out.println("  Conversation ID: " + message.getConversationId());
            
            // Double check if conversation exists before proceeding
            if (!conversationExists(conversationId)) {
                System.out.println("  Conversation does not exist - creating it now");
                ensureGroupConversationExists(groupName);
            } else {
                System.out.println("  Conversation exists in database");
            }
            
            // Save the message using MessageDAO
            MessageDAO messageDAO = new MessageDAOImpl();
            boolean saved = messageDAO.save(message);
            if (saved) {
                System.out.println("Group message saved to database for group: " + groupName);
            } else {
                System.err.println("Failed to save group message for group: " + groupName);
            }
        } catch (Exception e) {
            System.err.println("Error saving group message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean conversationExists(String conversationId) {
        try (Connection conn = DatabaseManager.getConnection()) {
            String query = "SELECT COUNT(*) FROM conversations WHERE id = ?";
            try (PreparedStatement stmt = conn.prepareStatement(query)) {
                stmt.setString(1, conversationId);
                ResultSet rs = stmt.executeQuery();
                if (rs.next()) {
                    return rs.getInt(1) > 0;
                }
            }
        } catch (SQLException e) {
            System.err.println("Error checking if conversation exists: " + e.getMessage());
        }
        return false;
    }
    
    public Group findGroupByName(String groupName) {
        return groupDAO.findGroupByName(groupName);
    }
    
    public List<Group> getAllGroups() {
        return groupDAO.getAllGroups();
    }
    
    public List<Group> getGroupsByUser(String userEmail) {
        return groupDAO.getGroupsByUser(userEmail);
    }



    public void debugGroupMessageSaving(String groupName) {
        String conversationId = "group_" + groupName;
        
        try {
            Connection conn = DatabaseManager.getConnection();
            
            // 1. Check conversation record
            PreparedStatement checkConvStmt = conn.prepareStatement("SELECT * FROM conversations WHERE id = ?");
            checkConvStmt.setString(1, conversationId);
            ResultSet convRs = checkConvStmt.executeQuery();
            
            System.out.println("\n====== GROUP DEBUG ======");
            if (convRs.next()) {
                System.out.println("✓ Conversation exists: " + conversationId);
                System.out.println("  is_group = " + convRs.getBoolean("is_group"));
            } else {
                System.out.println("✗ Conversation DOES NOT exist: " + conversationId);
            }
            
            // 2. Check message count
            PreparedStatement checkMsgStmt = conn.prepareStatement("SELECT COUNT(*) FROM messages WHERE conversation_id = ?");
            checkMsgStmt.setString(1, conversationId);
            ResultSet msgRs = checkMsgStmt.executeQuery();
            
            if (msgRs.next()) {
                int count = msgRs.getInt(1);
                System.out.println(count > 0 ? 
                    "✓ " + count + " messages found for this group" :
                    "✗ No messages found for this group");
            }
            
            // 3. Test direct insert
            String testId = "test_" + System.currentTimeMillis();
            try {
                PreparedStatement insertStmt = conn.prepareStatement(
                    "INSERT INTO messages (id, sender_email, conversation_id, content, type, status, timestamp, delivered, read_status) " +
                    "VALUES (?, 'test@example.com', ?, 'TEST MESSAGE', 'group', 'SENT', ?, FALSE, FALSE)");
                insertStmt.setString(1, testId);
                insertStmt.setString(2, conversationId);
                insertStmt.setLong(3, System.currentTimeMillis());
                
                int result = insertStmt.executeUpdate();
                System.out.println(result > 0 ? 
                    "✓ Test insert SUCCEEDED" : 
                    "✗ Test insert FAILED (no error but no rows affected)");
                
                // Delete the test message
                PreparedStatement deleteStmt = conn.prepareStatement("DELETE FROM messages WHERE id = ?");
                deleteStmt.setString(1, testId);
                deleteStmt.executeUpdate();
                
            } catch (SQLException e) {
                System.out.println("✗ Test insert FAILED with error:");
                System.out.println("  Error: " + e.getMessage());
                System.out.println("  SQL State: " + e.getSQLState());
                System.out.println("  Error Code: " + e.getErrorCode());
            }
            
            System.out.println("=========================\n");
            conn.close();
            
        } catch (SQLException e) {
            System.err.println("Debug error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}