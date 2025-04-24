package com.chatapp.common.model;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.UUID;

public class FileMessage {
    private String id;
    private String senderEmail;
    private String conversationId;
    private String originalFilename;
    private String storedPath;
    private String mimeType;
    private long fileSize;
    private long timestamp;
    private boolean delivered;
    private boolean viewed;
    
    public FileMessage() {
        this.id = UUID.randomUUID().toString();
        this.timestamp = System.currentTimeMillis();
        this.delivered = false;
        this.viewed = false;
    }
    
    public FileMessage(String senderEmail, String conversationId, String originalFilename, 
                        String storedPath, String mimeType, long fileSize) {
        this();
        this.senderEmail = senderEmail;
        this.conversationId = conversationId;
        this.originalFilename = originalFilename;
        this.storedPath = storedPath;
        this.mimeType = mimeType;
        this.fileSize = fileSize;
    }
    
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("type", "file");
            json.put("id", id);
            json.put("sender", senderEmail);
            json.put("conversationId", conversationId);
            json.put("filename", originalFilename);
            json.put("mimeType", mimeType);
            json.put("fileSize", fileSize);
            json.put("timestamp", timestamp);
            json.put("delivered", delivered);
            json.put("viewed", viewed);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }
    
    public static FileMessage fromJson(JSONObject json) throws JSONException {
        FileMessage file = new FileMessage();
        file.setId(json.getString("id"));
        file.setSenderEmail(json.getString("sender"));
        file.setConversationId(json.getString("conversationId"));
        file.setOriginalFilename(json.getString("filename"));
        file.setMimeType(json.getString("mimeType"));
        file.setFileSize(json.getLong("fileSize"));
        file.setTimestamp(json.getLong("timestamp"));
        file.setDelivered(json.optBoolean("delivered", false));
        file.setViewed(json.optBoolean("viewed", false));
        return file;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getSenderEmail() { return senderEmail; }
    public void setSenderEmail(String senderEmail) { this.senderEmail = senderEmail; }
    
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String originalFilename) { this.originalFilename = originalFilename; }
    
    public String getStoredPath() { return storedPath; }
    public void setStoredPath(String storedPath) { this.storedPath = storedPath; }
    
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    
    public long getFileSize() { return fileSize; }
    public void setFileSize(long fileSize) { this.fileSize = fileSize; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public boolean isDelivered() { return delivered; }
    public void setDelivered(boolean delivered) { this.delivered = delivered; }
    
    public boolean isViewed() { return viewed; }
    public void setViewed(boolean viewed) { this.viewed = viewed; }
}