package com.chatapp.server.service;

import java.io.*;
import java.util.*;
import java.nio.file.*;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import com.chatapp.common.model.Message;
import com.chatapp.data.dao.MessageDAO;
import com.chatapp.data.dao.impl.MessageDAOImpl;

/**
 * Service to handle conversation persistence and retrieval using Database Storage.
 */
public class ConversationService {
    
    // Note: Les méthodes relatives au stockage sur fichier (loadHistory, saveToFile) 
    // sont conservées ici pour référence, mais ne seront plus utilisées dans la nouvelle version.
    // La méthode generateConversationId reste inchangée.
    
    private final MessageDAO messageDAO;
    
    /**
     * Constructor that initializes the ConversationService.
     */
    public ConversationService() {
        this.messageDAO = new MessageDAOImpl();
        // Vous pouvez conserver un message de debug pour vérifier l'initialisation.
        System.out.println("DEBUG: Initializing ConversationService for DB storage");
    }
    
    /**
     * Save a message to conversation history directly in the database.
     * (Ancienne version basée sur les fichiers JSON remplacée par un appel direct au DAO.)
     */
    public boolean saveMessage(Message message) {
        return messageDAO.save(message);
    }
    
    /**
     * Load conversation history between two users from the database.
     * (Remplace l'ancienne lecture des fichiers JSON.)
     */
    public List<JSONObject> getConversationHistory(String user1, String user2) {
        String conversationId = generateConversationId(user1, user2);
        return messageDAO.getConversationHistory(conversationId);
    }
    
    /**
     * Generate a conversation ID from two user emails.
     * Cette méthode reste inchangée.
     */
    public String generateConversationId(String user1, String user2) {
        // Tri des emails pour garantir un ID constant quel que soit l'ordre.
        if (user1.compareTo(user2) > 0) {
            String temp = user1;
            user1 = user2;
            user2 = temp;
        }
        return user1 + "_" + user2;
    }
    
    // --- Anciennes méthodes pour le stockage en fichier (non utilisées dans la version DB) ---
    
    /**
     * Load conversation history from file. (Deprecated for DB storage)
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
            // En cas d'erreur, sauvegarder une copie de sauvegarde
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
     * Save conversation history to file. (Deprecated for DB storage)
     */
    private void saveToFile(String filename, List<JSONObject> messages) {
        try {
            JSONArray jsonArray = new JSONArray();
            for (JSONObject message : messages) {
                jsonArray.put(message);
            }
            
            System.out.println("DEBUG: Saving to file: " + filename);
            System.out.println("DEBUG: Content size: " + jsonArray.toString().length() + " bytes");
            
            // Création du répertoire parent si nécessaire
            File file = new File(filename);
            if (!file.getParentFile().exists()) {
                boolean created = file.getParentFile().mkdirs();
                System.out.println("DEBUG: Created parent directories: " + created);
            }
            
            Files.write(Paths.get(filename), jsonArray.toString().getBytes());
            
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
