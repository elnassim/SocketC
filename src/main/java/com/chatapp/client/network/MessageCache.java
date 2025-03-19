package com.chatapp.client.network;

import org.json.JSONObject;
import java.util.*;
import java.io.*;

/**
 * Cache for storing messages locally
 */
public class MessageCache {
    private static final String CACHE_DIR = "message_cache";
    private Map<String, List<JSONObject>> pendingMessages = new HashMap<>();
    private String userEmail;
    
    public MessageCache(String userEmail) {
        this.userEmail = userEmail;
        File dir = new File(CACHE_DIR);
        if (!dir.exists()) {
            dir.mkdir();
        }
        loadPendingMessages();
    }
    
    /**
     * Cache a message that hasn't been confirmed as delivered
     * 
     * @param message The message to cache
     */
    public void cacheMessage(JSONObject message) {
        String recipient = message.getString("to");
        pendingMessages.computeIfAbsent(recipient, k -> new ArrayList<>());
        pendingMessages.get(recipient).add(message);
        savePendingMessages();
    }
    
    /**
     * Remove a message from cache once delivered
     * 
     * @param messageId The ID of the delivered message
     */
    public void removeDeliveredMessage(String messageId) {
        for (List<JSONObject> messages : pendingMessages.values()) {
            messages.removeIf(msg -> msg.getString("id").equals(messageId));
        }
        savePendingMessages();
    }
    
    /**
     * Get all pending messages for retry
     * 
     * @return List of all pending messages
     */
    public List<JSONObject> getPendingMessages() {
        List<JSONObject> allPending = new ArrayList<>();
        for (List<JSONObject> messages : pendingMessages.values()) {
            allPending.addAll(messages);
        }
        return allPending;
    }
    
    /**
     * Save pending messages to disk
     */
    private void savePendingMessages() {
        try {
            // Convert to string representation for serialization
            Map<String, List<String>> serializable = new HashMap<>();
            for (Map.Entry<String, List<JSONObject>> entry : pendingMessages.entrySet()) {
                List<String> jsonStrings = new ArrayList<>();
                for (JSONObject json : entry.getValue()) {
                    jsonStrings.add(json.toString());
                }
                serializable.put(entry.getKey(), jsonStrings);
            }
            
            File cacheFile = new File(CACHE_DIR + "/" + userEmail.replace("@", "_at_").replace(".", "_dot_") + ".cache");
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(cacheFile))) {
                oos.writeObject(serializable);
            }
        } catch (IOException e) {
            System.err.println("Error saving message cache: " + e.getMessage());
        }
    }
    
    /**
     * Load pending messages from disk
     */
    @SuppressWarnings("unchecked")
    private void loadPendingMessages() {
        File cacheFile = new File(CACHE_DIR + "/" + userEmail.replace("@", "_at_").replace(".", "_dot_") + ".cache");
        if (cacheFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(cacheFile))) {
                Map<String, List<String>> serialized = (Map<String, List<String>>) ois.readObject();
                
                // Convert back to JSONObjects
                for (Map.Entry<String, List<String>> entry : serialized.entrySet()) {
                    List<JSONObject> jsonObjects = new ArrayList<>();
                    for (String jsonStr : entry.getValue()) {
                        jsonObjects.add(new JSONObject(jsonStr));
                    }
                    pendingMessages.put(entry.getKey(), jsonObjects);
                }
            } catch (IOException | ClassNotFoundException | org.json.JSONException e) {
                System.err.println("Error loading message cache: " + e.getMessage());
            }
        }
    }
}