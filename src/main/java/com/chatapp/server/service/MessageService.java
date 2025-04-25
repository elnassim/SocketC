package com.chatapp.server.service;

import org.json.JSONObject;

import com.chatapp.common.model.FileMessage;
import com.chatapp.common.model.Message;
import java.util.*;
import java.io.*;

/**
 * Service class for message-related operations
 */
public class MessageService {

    private Map<String, List<String>> offlineMessages = new HashMap<>();
    private ConversationService conversationService;
    private final FileService fileService;

    public MessageService() {
        this.conversationService = new ConversationService();
        this.fileService = new FileService();
        // Add logging to verify initialization
        System.out.println("MessageService initialized with ConversationService");
    }

    /**
     * Generate a unique conversation ID for two users
     */
    public String generateConversationId(String user1, String user2) {
        return conversationService.generateConversationId(user1, user2);
    }

    /**
     * Create a private message JSON object with routing information
     */
    public JSONObject createPrivateMessage(String sender, String recipient, String content) {
        Message message = new Message(sender, content, "private");
        message.setConversationParticipants(sender, recipient);

        // Store in conversation history via DB
        conversationService.saveMessage(message);

        return message.toJson();
    }

    /**
     * Create a broadcast message JSON object
     */
    public JSONObject createBroadcastMessage(String sender, String content) {
        Message message = new Message(sender, content, "broadcast");
        message.setConversationId("broadcast");

        // Store in conversation history via DB
        conversationService.saveMessage(message);

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
    /**
 * Get message history between users from the DB
 */
public List<JSONObject> getMessageHistory(String user1, String user2) {
    try {
        // Get text messages from conversation
        List<JSONObject> messages = conversationService.getConversationHistory(user1, user2);
        System.out.println("Retrieved " + messages.size() + " text messages from DB");
        
        // Get files from the same conversation
        String conversationId = conversationService.generateConversationId(user1, user2);
        System.out.println("Looking for files with conversation ID: " + conversationId);
        
        try {
            List<FileMessage> files = fileService.getFilesByConversation(conversationId);
            if (files != null && !files.isEmpty()) {
                System.out.println("Found " + files.size() + " files for conversation");
                
                // Convert files to JSONObjects and add to messages list
                for (FileMessage file : files) {
                    try {
                        JSONObject fileJson = file.toJson();
                        // Add required fields for client processing
                        fileJson.put("type", "file");
                        fileJson.put("sender", file.getSenderEmail());
                        fileJson.put("conversationId", file.getConversationId());
                        fileJson.put("timestamp", file.getTimestamp());
                        
                        // Add these fields for compatibility with message display
                        if (!fileJson.has("originalFilename")) {
                            fileJson.put("originalFilename", file.getOriginalFilename());
                        }
                        if (!fileJson.has("mimeType")) {
                            fileJson.put("mimeType", file.getMimeType());
                        }
                        if (!fileJson.has("fileSize")) {
                            fileJson.put("fileSize", file.getFileSize());
                        }
                        
                        // Add to message list
                        messages.add(fileJson);
                        System.out.println("Added file to history: " + file.getOriginalFilename());
                    } catch (Exception e) {
                        System.err.println("Error converting file to JSON: " + e.getMessage());
                    }
                }
            } else {
                System.out.println("No files found for conversation: " + conversationId);
            }
        } catch (Exception e) {
            System.err.println("Error retrieving files for conversation: " + e.getMessage());
            e.printStackTrace();
        }
        
        // Sort messages by timestamp to maintain chronological order
        messages.sort((msg1, msg2) -> {
            long time1 = msg1.optLong("timestamp", 0);
            long time2 = msg2.optLong("timestamp", 0);
            return Long.compare(time1, time2);
        });
        
        System.out.println("Returning " + messages.size() + " total history items (messages + files)");
        return messages;
    } catch (Exception e) {
        System.err.println("Error in getMessageHistory: " + e.getMessage());
        e.printStackTrace();
        return new ArrayList<>();
    }
}
    
    /**
     * Update message status (delivered/read)
     */
    public void updateMessageStatus(String messageId, String conversationId, Message.Status status) {
        // For now, just log the status change
        System.out.println("Message " + messageId + " status updated to " + status);
    }
}
