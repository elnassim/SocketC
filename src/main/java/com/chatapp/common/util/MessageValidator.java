package com.chatapp.common.util;

import org.json.JSONObject;
import org.json.JSONException;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/**
 * Validates messages and ensures data integrity
 */
public class MessageValidator {
    
    /**
     * Add checksum to a message
     * 
     * @param json The message JSON
     * @return JSON with added checksum
     */
    public static JSONObject addChecksum(JSONObject json) {
        String content = json.optString("content", "");
        String sender = json.optString("sender", "");
        long timestamp = json.optLong("timestamp", System.currentTimeMillis());
        
        String checksumInput = content + sender + timestamp;
        String checksum = calculateChecksum(checksumInput);
        
        json.put("checksum", checksum);
        return json;
    }
    
    /**
     * Verify message checksum
     * 
     * @param json The message to verify
     * @return true if checksum is valid
     */
    public static boolean verifyChecksum(JSONObject json) {
        try {
            if (!json.has("checksum")) return false;
            
            String originalChecksum = json.getString("checksum");
            String content = json.optString("content", "");
            String sender = json.optString("sender", "");
            long timestamp = json.optLong("timestamp", 0);
            
            String checksumInput = content + sender + timestamp;
            String calculatedChecksum = calculateChecksum(checksumInput);
            
            return calculatedChecksum.equals(originalChecksum);
        } catch (JSONException e) {
            return false;
        }
    }
    
    /**
     * Calculate SHA-256 checksum
     * 
     * @param input The input string
     * @return Hex string of the checksum
     */
    private static String calculateChecksum(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            
            return hexString.toString();
        } catch (Exception e) {
            return "";
        }
    }
}