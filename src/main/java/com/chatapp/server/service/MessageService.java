package com.chatapp.server.service;

import org.json.JSONObject;
import com.chatapp.common.model.Message;

import java.util.*;
import java.io.*;

/**
 * Service class for message-related operations
 */
public class MessageService {

    private Map<String, List<String>> messageHistory = new HashMap<>();
    private Map<String, List<String>> offlineMessages = new HashMap<>();

    /**
     * Generate a unique conversation ID for two users
     * 
     * @param user1 First user's email
     * @param user2 Second user's email
     * @return A unique conversation ID
     */
    public String generateConversationId(String user1, String user2) {
        // Sort emails to ensure the same ID regardless of sender/receiver order
        if (user1.compareTo(user2) > 0) {
            String temp = user1;
            user1 = user2;
            user2 = temp;
        }
        return user1 + "_" + user2;
    }

    /**
     * Create a private message JSON object with routing information
     * 
     * @param sender    Sender's email
     * @param recipient Recipient's email
     * @param content   Message content
     * @return JSON object representing a private message
     */
    public JSONObject createPrivateMessage(String sender, String recipient, String content) {
        Message message = new Message(sender, content, "private");
        message.setConversationParticipants(sender, recipient);
        
        // Store in history
        storeMessage(sender, recipient, content);
        
        return message.toJson();
    }

    /**
     * Create a broadcast message JSON object
     * 
     * @param sender  Sender's email
     * @param content Message content
     * @return JSON object representing a broadcast message
     */
    public JSONObject createBroadcastMessage(String sender, String content) {
        Message message = new Message(sender, content, "broadcast");
        message.setConversationId("broadcast");
        
        // Store in history
        storeMessage(sender, "broadcast", content);
        
        return message.toJson();
    }

    /**
     * Store a message for offline delivery
     * 
     * @param recipient The recipient's email
     * @param message The JSON message to store
     */
    public void storeOfflineMessage(String recipient, JSONObject message) {
        // Initialize the offline messages map for this recipient if needed
        offlineMessages.computeIfAbsent(recipient, k -> new ArrayList<>());
        
        // Add the message to the recipient's queue
        offlineMessages.get(recipient).add(message.toString());
    }

    /**
     * Get and remove all offline messages for a user
     * 
     * @param userEmail The user's email
     * @return A list of stored messages
     */
    public List<String> getOfflineMessages(String userEmail) {
        List<String> messages = offlineMessages.getOrDefault(userEmail, new ArrayList<>());
        offlineMessages.remove(userEmail);
        return messages;
    }

    /**
     * Store a message in the history
     * 
     * @param sender    Sender's email
     * @param recipient Recipient's email or "broadcast"
     * @param content   Message content
     */
    private void storeMessage(String sender, String recipient, String content) {
        String conversationId = generateConversationId(sender, recipient);
        
        if (!messageHistory.containsKey(conversationId)) {
            messageHistory.put(conversationId, new ArrayList<>());
        }
        
        // Create a simple JSON representation for storage
        JSONObject storedMessage = new JSONObject();
        storedMessage.put("sender", sender);
        storedMessage.put("content", content);
        storedMessage.put("timestamp", System.currentTimeMillis());
        
        messageHistory.get(conversationId).add(storedMessage.toString());

        // If we want to maintain size limit
        List<String> messages = messageHistory.get(conversationId);
        if (messages.size() > 100) {
            messages.remove(0); // Remove oldest message if we have more than 100
        }
    }

    /**
     * Get message history by conversation ID
     * 
     * @param conversationId The conversation ID
     * @return List of messages in this conversation
     */
    public List<String> getMessageHistoryByConversation(String conversationId) {
        return messageHistory.getOrDefault(conversationId, new ArrayList<>());
    }
    
    /**
     * Get message history between users
     * 
     * @param user1 First user's email
     * @param user2 Second user's email
     * @return List of messages between the users
     */
    public List<String> getMessageHistory(String user1, String user2) {
        String conversationId = generateConversationId(user1, user2);
        return messageHistory.getOrDefault(conversationId, new ArrayList<>());
    }
}