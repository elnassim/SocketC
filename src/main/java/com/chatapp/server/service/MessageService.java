package com.chatapp.server.service;

import org.json.JSONObject;
import java.util.*;
import java.io.*;

/**
 * Service class for message-related operations
 */
public class MessageService {

    private Map<String, List<String>> messageHistory = new HashMap<>();

    /**
     * Create a private message JSON object
     * 
     * @param sender    Sender's email
     * @param recipient Recipient's email
     * @param content   Message content
     * @return JSON object representing a private message
     */
    public JSONObject createPrivateMessage(String sender, String recipient, String content) {
        JSONObject message = new JSONObject();
        message.put("type", "private");
        message.put("sender", sender);
        message.put("to", recipient);
        message.put("content", content);
        message.put("timestamp", System.currentTimeMillis());

        // Store in history
        storeMessage(sender, recipient, content);

        return message;
    }

    /**
     * Create a broadcast message JSON object
     * 
     * @param sender  Sender's email
     * @param content Message content
     * @return JSON object representing a broadcast message
     */
    public JSONObject createBroadcastMessage(String sender, String content) {
        JSONObject message = new JSONObject();
        message.put("type", "broadcast");
        message.put("sender", sender);
        message.put("content", content);
        message.put("timestamp", System.currentTimeMillis());

        // Store in history
        storeMessage(sender, "broadcast", content);

        return message;
    }

    /**
     * Store a message in the history
     * 
     * @param sender    Sender's email
     * @param recipient Recipient's email or "broadcast"
     * @param content   Message content
     */
    private void storeMessage(String sender, String recipient, String content) {
        String key = sender + "-" + recipient;
        if (!messageHistory.containsKey(key)) {
            messageHistory.put(key, new ArrayList<>());
        }
        messageHistory.get(key).add(content);

        // If we want to maintain size limit
        List<String> messages = messageHistory.get(key);
        if (messages.size() > 100) {
            messages.remove(0); // Remove oldest message if we have more than 100
        }
    }

    /**
     * Get message history between users
     * 
     * @param user1 First user's email
     * @param user2 Second user's email
     * @return List of messages between the users
     */
    public List<String> getMessageHistory(String user1, String user2) {
        String key1 = user1 + "-" + user2;
        String key2 = user2 + "-" + user1;

        List<String> result = new ArrayList<>();
        if (messageHistory.containsKey(key1)) {
            result.addAll(messageHistory.get(key1));
        }
        if (messageHistory.containsKey(key2)) {
            result.addAll(messageHistory.get(key2));
        }

        return result;
    }
}
