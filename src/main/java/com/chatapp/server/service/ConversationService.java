package com.chatapp.server.service;

import java.io.*;
import java.util.*;
import java.nio.file.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.chatapp.common.model.Message;

/**
 * Service to handle conversation persistence and retrieval
 */
public class ConversationService {
    private static final String CONVERSATIONS_DIR = "data/conversations/";
    private static final int HISTORY_LIMIT = 50;  // Maximum messages per conversation
    
    /**
     * Constructor that ensures the conversations directory exists
     */
    public ConversationService() {
        System.out.println("DEBUG: Initializing ConversationService");
        System.out.println("DEBUG: Working directory: " + new File(".").getAbsolutePath());
        
        // Ensure conversations directory exists
        try {
            File dir = new File(CONVERSATIONS_DIR);
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                System.out.println("DEBUG: Created conversations directory: " + created);
                System.out.println("DEBUG: Path: " + dir.getAbsolutePath());
            } else {
                System.out.println("DEBUG: Using existing conversations directory: " + dir.getAbsolutePath());
                System.out.println("DEBUG: Directory is writable: " + dir.canWrite());
            }
        } catch (Exception e) {
            System.err.println("Error creating conversations directory: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Save a message to conversation history
     */
    public void saveMessage(Message message) {
        if (message.getConversationId() == null) {
            System.err.println("Cannot save message without conversation ID");
            return;
        }
        
        String filename = CONVERSATIONS_DIR + message.getConversationId() + ".json";
        List<JSONObject> history = loadHistory(filename);
        
        
        history.add(message.toJson());
        
        // Keep history within size limit
        if (history.size() > HISTORY_LIMIT) {
            history = history.subList(history.size() - HISTORY_LIMIT, history.size());
        }
        
        saveToFile(filename, history);
    }
    
    /**
     * Load conversation history between users
     */
    public List<JSONObject> getConversationHistory(String user1, String user2) {
        String conversationId = generateConversationId(user1, user2);
        String filename = CONVERSATIONS_DIR + conversationId + ".json";
        return loadHistory(filename);
    }
    
    /**
     * Generate a conversation ID from two user emails
     */
    public String generateConversationId(String user1, String user2) {
        // Sort emails to ensure consistent ID regardless of order
        if (user1.compareTo(user2) > 0) {
            String temp = user1;
            user1 = user2;
            user2 = temp;
        }
        return user1 + "_" + user2;
    }
    
    /**
     * Load conversation history from file
     */
    private List<JSONObject> loadHistory(String filename) {
        File file = new File(filename);
        if (!file.exists()) {
            return new ArrayList<>();
        }
        
        try {
            String content = new String(Files.readAllBytes(Paths.get(filename)));
            if (content.trim().isEmpty()) {
                return new ArrayList<>();
            }
            
            JSONArray jsonArray = new JSONArray(content);
            List<JSONObject> messages = new ArrayList<>();
            
            for (int i = 0; i < jsonArray.length(); i++) {
                messages.add(jsonArray.getJSONObject(i));
            }
            
            return messages;
        } catch (IOException e) {
            System.err.println("Error loading conversation history: " + e.getMessage());
            return new ArrayList<>();
        } catch (JSONException e) {
            System.err.println("Error parsing conversation file: " + e.getMessage());
            // If there's a parsing error, backup the problematic file
            try {
                File backup = new File(filename + ".bak");
                Files.copy(Paths.get(filename), new FileOutputStream(backup));
                System.err.println("Backed up corrupted file to: " + backup.getPath());
            } catch (IOException ex) {
                System.err.println("Failed to backup corrupted file: " + ex.getMessage());
            }
            return new ArrayList<>();
        }
    }
    
    /**
     * Save conversation history to file
     */
    private void saveToFile(String filename, List<JSONObject> messages) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (JSONObject message : messages) {
                jsonArray.put(message);
            }
            
            System.out.println("DEBUG: Saving to file: " + filename);
            System.out.println("DEBUG: Content size: " + jsonArray.toString().length() + " bytes");
            
            // Create parent directories if they don't exist
            File file = new File(filename);
            if (!file.getParentFile().exists()) {
                boolean created = file.getParentFile().mkdirs();
                System.out.println("DEBUG: Created parent directories: " + created);
            }
            
            Files.write(Paths.get(filename), jsonArray.toString().getBytes());
            
            // Verify the file exists and has content
            File savedFile = new File(filename);
            if (savedFile.exists()) {
                System.out.println("DEBUG: File saved successfully, size: " + savedFile.length() + " bytes");
            } else {
                System.err.println("ERROR: Failed to save file - file does not exist after write operation");
            }
        } catch (IOException e) {
            System.err.println("Error saving conversation history: " + e.getMessage());
            e.printStackTrace();
        }
    }
}