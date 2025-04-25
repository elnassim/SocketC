package com.chatapp.util;

import com.chatapp.common.model.Message;
import com.chatapp.data.dao.MessageDAO;
import com.chatapp.data.dao.impl.MessageDAOImpl;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public class MigrationTool {
    
    private static final MessageDAO messageDAO = new MessageDAOImpl();
    
    public static void migrateConversationsToDatabase(String userEmail) {
        try {
            File conversationsDir = new File("data/conversations");
            if (!conversationsDir.exists()) {
                System.out.println("No conversations to migrate");
                return;
            }
            
            System.out.println("Starting migration of conversations to database...");
            
            for (File file : conversationsDir.listFiles()) {
                if (file.isFile() && file.getName().endsWith(".json")) {
                    String key = file.getName().replace("_at_", "@").replace("_dot_", ".");
                    key = key.substring(0, key.length() - 5); // Remove .json
                    
                    migrateConversation(key, file, userEmail);
                }
            }
            
            System.out.println("Migration complete");
        } catch (Exception e) {
            System.err.println("Error during migration: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void migrateConversation(String conversationKey, File file, String userEmail) {
        try {
            System.out.println("Migrating conversation: " + conversationKey);
            
            // Read the JSON file
            StringBuilder content = new StringBuilder();
            try (FileReader reader = new FileReader(file)) {
                char[] buffer = new char[1024];
                int bytesRead;
                while ((bytesRead = reader.read(buffer)) != -1) {
                    content.append(buffer, 0, bytesRead);
                }
            }
            
            JSONArray messagesArray = new JSONArray(content.toString());
            List<Message> messages = new ArrayList<>();
            
            for (int i = 0; i < messagesArray.length(); i++) {
                JSONObject msgJson = messagesArray.getJSONObject(i);
                String sender = msgJson.getString("sender");
                String msgContent = msgJson.getString("content");
                boolean isPrivate = msgJson.getBoolean("isPrivate");
                long timestamp = msgJson.optLong("timestamp", System.currentTimeMillis());
                
                Message message = new Message(sender, msgContent, isPrivate ? "private" : "broadcast");
                message.setTimestamp(timestamp);
                
                // Set conversation ID based on whether this is a group or individual conversation
                if (conversationKey.contains("@")) {
                    message.setConversationParticipants(userEmail, conversationKey);
                } else {
                    message.setConversationId(conversationKey);
                }
                
                messages.add(message);
            }
            
            // Save messages to database
            int savedCount = 0;
            for (Message message : messages) {
                if (messageDAO.save(message)) {
                    savedCount++;
                }
            }
            
            System.out.println("Migrated " + savedCount + " out of " + messages.size() + " messages for " + conversationKey);
            
        } catch (Exception e) {
            System.err.println("Error migrating conversation " + conversationKey + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public static void main(String[] args) {
        // Example usage
        if (args.length > 0) {
            migrateConversationsToDatabase(args[0]);
        } else {
            System.out.println("Please provide user email as argument");
        }
    }
}