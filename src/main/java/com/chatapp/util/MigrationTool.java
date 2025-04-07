package com.chatapp.util;

import com.chatapp.common.model.Message;
import com.chatapp.common.model.User;
import com.chatapp.data.dao.ContactDAO;
import com.chatapp.data.dao.MessageDAO;
import com.chatapp.data.dao.UserDAO;
import com.chatapp.data.dao.impl.ContactDAOImpl;
import com.chatapp.data.dao.impl.MessageDAOImpl;
import com.chatapp.data.dao.impl.UserDAOImpl;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class MigrationTool {

    private static final String CONVERSATIONS_DIR = "data/conversations/";
    private static final String USERS_FILE_PATH = "src/main/resources/data/Users.json";
    private static final String CONTACTS_FILE_PREFIX = "contacts_";
    
    private final UserDAO userDAO;
    private final MessageDAO messageDAO;
    private final ContactDAO contactDAO;
    
    public MigrationTool() {
        this.userDAO = new UserDAOImpl();
        this.messageDAO = new MessageDAOImpl();
        this.contactDAO = new ContactDAOImpl();
    }
    
    public void migrateAll() {
        migrateUsers();
        migrateContacts();
        migrateConversations();
        System.out.println("Migration completed successfully");
    }
    
    private void migrateUsers() {
        try {
            String content = new String(Files.readAllBytes(Paths.get(USERS_FILE_PATH)));
            JSONArray usersArray = new JSONArray(content);
            
            for (int i = 0; i < usersArray.length(); i++) {
                JSONObject userJson = usersArray.getJSONObject(i);
                String email = userJson.getString("email");
                String password = userJson.getString("password");
                String username = email.substring(0, email.indexOf("@"));
                
                User user = new User(username, password, email);
                userDAO.create(user);
            }
            
            System.out.println("Users migration completed");
        } catch (IOException | JSONException e) {
            System.err.println("Error migrating users: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void migrateContacts() {
        // Find all contact files
        File dir = new File(".");
        File[] contactFiles = dir.listFiles((d, name) -> name.startsWith(CONTACTS_FILE_PREFIX));
        
        if (contactFiles == null || contactFiles.length == 0) {
            System.out.println("No contact files found");
            return;
        }
        
        for (File file : contactFiles) {
            try {
                // Extract user email from filename
                String filename = file.getName();
                String emailPart = filename.substring(CONTACTS_FILE_PREFIX.length());
                String userEmail = emailPart.replace("_at_", "@").replace("_dot_", ".");
                
                // Read contacts
                List<String> contacts = new ArrayList<>();
                try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.trim().isEmpty()) {
                            contacts.add(line.trim());
                        }
                    }
                }
                
                // Add contacts to database
                for (String contactEmail : contacts) {
                    contactDAO.addContact(userEmail, contactEmail);
                }
                
                System.out.println("Migrated contacts for " + userEmail);
            } catch (IOException e) {
                System.err.println("Error migrating contacts from " + file.getName() + ": " + e.getMessage());
            }
        }
        
        System.out.println("Contacts migration completed");
    }
    
    private void migrateConversations() {
        File dir = new File(CONVERSATIONS_DIR);
        File[] conversationFiles = dir.listFiles((d, name) -> name.endsWith(".json"));
        
        if (conversationFiles == null || conversationFiles.length == 0) {
            System.out.println("No conversation files found");
            return;
        }
        
        for (File file : conversationFiles) {
            try {
                // Get conversation ID from filename
                String conversationId = file.getName().replace(".json", "");
                
                // Read messages
                String content = new String(Files.readAllBytes(file.toPath()));
                JSONArray messagesArray = new JSONArray(content);
                
                for (int i = 0; i < messagesArray.length(); i++) {
                    JSONObject messageJson = messagesArray.getJSONObject(i);
                    Message message = Message.fromJson(messageJson);
                    messageDAO.save(message);
                }
                
                System.out.println("Migrated conversation: " + conversationId);
            } catch (IOException | JSONException e) {
                System.err.println("Error migrating conversation " + file.getName() + ": " + e.getMessage());
            }
        }
        
        System.out.println("Conversations migration completed");
    }
    
    public static void main(String[] args) {
        MigrationTool tool = new MigrationTool();
        tool.migrateAll();
    }
}