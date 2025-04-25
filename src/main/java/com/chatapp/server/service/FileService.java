package com.chatapp.server.service;

import com.chatapp.common.model.FileMessage;
import com.chatapp.data.dao.FileDAO;
import com.chatapp.data.dao.impl.FileDAOImpl;
import org.json.JSONObject;

import java.io.*;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;

public class FileService {
    private static final String UPLOAD_DIR = "uploads";
    private final FileDAO fileDAO;
    
    public FileService() {
        this.fileDAO = new FileDAOImpl();
        // Create uploads directory if it doesn't exist
        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists()) {
            uploadDir.mkdirs();
        }
    }
    
    public FileMessage saveFile(String senderEmail, String recipientEmail, 
                              String filename, byte[] fileData, String mimeType) {
        try {
            // Generate a unique filename to prevent collisions
            String uniqueFilename = UUID.randomUUID().toString() + "_" + filename;
            String storedPath = UPLOAD_DIR + File.separator + uniqueFilename;
            
            // Write file to disk
            Path filePath = Paths.get(storedPath);
            Files.write(filePath, fileData);
            
            // Create file message
            FileMessage fileMessage = new FileMessage();
            fileMessage.setSenderEmail(senderEmail);
            
            // Generate conversation ID using the same method as for messages
            String conversationId = generateConversationId(senderEmail, recipientEmail);
            fileMessage.setConversationId(conversationId);
            
            fileMessage.setOriginalFilename(filename);
            fileMessage.setStoredPath(storedPath);
            fileMessage.setMimeType(mimeType);
            fileMessage.setFileSize(fileData.length);
            
            // Save metadata to database
            if (fileDAO.saveFileMetadata(fileMessage)) {
                return fileMessage;
            } else {
                // Cleanup file if database insertion fails
                Files.deleteIfExists(filePath);
                return null;
            }
            
        } catch (IOException e) {
            System.err.println("Error saving file: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    public byte[] getFileData(String fileId) {
        FileMessage file = fileDAO.getFileById(fileId);
        if (file == null) {
            return null;
        }
        
        try {
            return Files.readAllBytes(Paths.get(file.getStoredPath()));
        } catch (IOException e) {
            System.err.println("Error reading file: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    public List<FileMessage> getFilesByConversation(String conversationId) {
        return fileDAO.getFilesByConversation(conversationId);
    }
    
    public boolean updateFileStatus(String fileId, boolean delivered, boolean viewed) {
        return fileDAO.updateFileStatus(fileId, delivered, viewed);
    }
    
    public boolean deleteFile(String fileId) {
        FileMessage file = fileDAO.getFileById(fileId);
        if (file == null) {
            return false;
        }
        
        try {
            // Delete file from disk
            Files.deleteIfExists(Paths.get(file.getStoredPath()));
            
            // Delete metadata from database
            return fileDAO.deleteFile(fileId);
        } catch (IOException e) {
            System.err.println("Error deleting file: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }
    
    private String generateConversationId(String user1, String user2) {
        // Ensure deterministic ordering regardless of parameter order
        if (user1.compareTo(user2) <= 0) {
            return user1 + "_" + user2;
        } else {
            return user2 + "_" + user1;
        }
    }

    // Add to FileService.java

    public FileMessage saveGroupFile(String senderEmail, String groupName, String filename, byte[] fileData, String mimeType) {
        try {
            // Generate a unique filename
            String uniqueFilename = UUID.randomUUID().toString() + "_" + filename;
            String storedPath = UPLOAD_DIR + File.separator + uniqueFilename;
    
            // Write file to disk
            Path filePath = Paths.get(storedPath);
            Files.write(filePath, fileData);
    
            // Create file message
            FileMessage fileMessage = new FileMessage();
            fileMessage.setSenderEmail(senderEmail);
    
            // Use group convention for conversation ID
            fileMessage.setConversationId("group_" + groupName);
    
            fileMessage.setOriginalFilename(filename);
            fileMessage.setStoredPath(storedPath);
            fileMessage.setMimeType(mimeType);
            fileMessage.setFileSize(fileData.length);
            fileMessage.setTimestamp(System.currentTimeMillis());
    
            // Save metadata to database
            if (fileDAO.saveFileMetadata(fileMessage)) {
                return fileMessage;
            } else {
                // Cleanup file if database insertion fails
                Files.deleteIfExists(filePath);
                return null;
            }
        } catch (IOException e) {
            System.err.println("Error saving group file: " + e.getMessage());
            return null;
        }
    }
}