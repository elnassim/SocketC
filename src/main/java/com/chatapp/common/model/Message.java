package com.chatapp.common.model;

import java.time.Instant;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Message model for the chat application
 */
public class Message {
    private String id;
    private String conversationId;
    private String sender;
    private String content;
    private long timestamp;
    private boolean delivered;
    private boolean read;
    private String type; // "private", "group", or "broadcast"
    private Status status;
    private String groupName; // Only used for group messages

    public enum Status {
        SENT, DELIVERED, READ
    }
    
    public Message(String sender, String content, String type) {
        this.id = generateId();
        this.sender = sender;
        this.content = content;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
        this.delivered = false;
        this.read = false;
        this.status = Status.SENT;
    }
    
    private String generateId() {
        return "msg_" + Instant.now().toEpochMilli() + "_" + 
               Integer.toHexString((int)(Math.random() * 10000));
    }
    
    // Set conversation ID based on participants
    public void setConversationParticipants(String user1, String user2) {
        // Sort emails to ensure the same conversation ID regardless of order
        if (user1.compareTo(user2) > 0) {
            String temp = user1;
            user1 = user2;
            user2 = temp;
        }
        this.conversationId = user1 + "_" + user2;
    }

    // Getters and setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getConversationId() { return conversationId; }
    public void setConversationId(String conversationId) { this.conversationId = conversationId; }
    
    public String getSender() { return sender; }
    public void setSender(String sender) { this.sender = sender; }
    
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
    
    public boolean isDelivered() { return delivered; }
    public void setDelivered(boolean delivered) { this.delivered = delivered; }
    
    public boolean isRead() { return read; }
    public void setRead(boolean read) { this.read = read; }
    
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    
    public String getGroupName() { return groupName; }
    public void setGroupName(String groupName) { this.groupName = groupName; }
    
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        try {
            json.put("id", id);
            json.put("conversationId", conversationId);
            json.put("sender", sender);
            json.put("content", content);
            json.put("timestamp", timestamp);
            json.put("delivered", delivered);
            json.put("read", read);
            json.put("type", type);
            json.put("status", status.name());
            if (groupName != null) {
                json.put("groupName", groupName);
                json.put("isGroup", true);
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return json;
    }
    
    public static Message fromJson(JSONObject json) {
        try {
            Message message = new Message(
                json.getString("sender"),
                json.getString("content"),
                json.getString("type")
            );
            
            // Set additional fields
            if (json.has("id")) {
                message.setId(json.getString("id"));
            }
            if (json.has("conversationId")) {
                message.setConversationId(json.getString("conversationId"));
            }
            if (json.has("timestamp")) {
                message.setTimestamp(json.getLong("timestamp"));
            }
            if (json.has("delivered")) {
                message.setDelivered(json.getBoolean("delivered"));
            }
            if (json.has("read")) {
                message.setRead(json.getBoolean("read"));
            }
            if (json.has("status") && json.getString("status") != null) {
                try {
                    message.setStatus(Status.valueOf(json.getString("status")));
                } catch (IllegalArgumentException e) {
                    message.setStatus(Status.SENT);
                }
            }
            if (json.has("groupName")) {
                message.setGroupName(json.getString("groupName"));
            }
            
            return message;
            
        } catch (JSONException e) {
            System.err.println("Error parsing message: " + e.getMessage());
            throw new RuntimeException("Error parsing Message from JSON", e);
        }
    }
}