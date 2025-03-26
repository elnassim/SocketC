package com.chatapp.server.service;

import org.json.JSONObject;
import com.chatapp.common.model.Message;

import java.util.*;
import java.io.*;

/**
 * Service class for message-related operations
 */
public class MessageService {

    private Map<String, List<String>> offlineMessages = new HashMap<>();
    private ConversationService conversationService;

    public MessageService() {
        this.conversationService = new ConversationService();
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
        
        // Store in conversation history
        conversationService.saveMessage(message);
        
        return message.toJson();
    }

    /**
     * Create a broadcast message JSON object
     */
    public JSONObject createBroadcastMessage(String sender, String content) {
        Message message = new Message(sender, content, "broadcast");
        message.setConversationId("broadcast");
        
        // Store in conversation history
        conversationService.saveMessage(message);
        
        return message.toJson();
    }

    /**
     * Store a message for offline delivery
     */
    public void storeOfflineMessage(String recipient, JSONObject message) {
        offlineMessages.computeIfAbsent(recipient, k -> new ArrayList<>());
        offlineMessages.get(recipient).add(message.toString());
        
        // Also save to conversation history
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
     * Get message history between users
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
}