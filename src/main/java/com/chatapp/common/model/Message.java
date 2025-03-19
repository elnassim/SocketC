package com.chatapp.common.model;

import java.time.Instant;
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
    private String type; // "private" or "broadcast"
    
    public Message(String sender, String content, String type) {
        this.id = generateId();
        this.sender = sender;
        this.content = content;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
        this.delivered = false;
        this.read = false;
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
    
    public JSONObject toJson() {
        JSONObject json = new JSONObject();
        json.put("id", id);
        json.put("conversationId", conversationId);
        json.put("sender", sender);
        json.put("content", content);
        json.put("timestamp", timestamp);
        json.put("delivered", delivered);
        json.put("read", read);
        json.put("type", type);
        return json;
    }
    
    public static Message fromJson(JSONObject json) {
        Message message = new Message(
            json.getString("sender"),
            json.getString("content"),
            json.getString("type")
        );
        message.id = json.getString("id");
        message.conversationId = json.getString("conversationId");
        message.timestamp = json.getLong("timestamp");
        message.delivered = json.getBoolean("delivered");
        message.read = json.getBoolean("read");
        return message;
    }
}