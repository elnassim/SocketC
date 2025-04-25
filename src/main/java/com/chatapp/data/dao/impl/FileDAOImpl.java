package com.chatapp.data.dao.impl;

import com.chatapp.common.model.FileMessage;
import com.chatapp.data.dao.FileDAO;
import com.chatapp.data.db.DatabaseManager;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

public class FileDAOImpl implements FileDAO {

    @Override
    public boolean saveFileMetadata(FileMessage file) {
        String query = "INSERT INTO files (id, sender_email, conversation_id, original_filename, " +
                "stored_path, mime_type, file_size, timestamp, delivered, viewed) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, file.getId());
            stmt.setString(2, file.getSenderEmail());
            stmt.setString(3, file.getConversationId());
            stmt.setString(4, file.getOriginalFilename());
            stmt.setString(5, file.getStoredPath());
            stmt.setString(6, file.getMimeType());
            stmt.setLong(7, file.getFileSize());
            stmt.setLong(8, file.getTimestamp());
            stmt.setBoolean(9, file.isDelivered());
            stmt.setBoolean(10, file.isViewed());
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Database error saving file metadata: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public FileMessage getFileById(String fileId) {
        String query = "SELECT * FROM files WHERE id = ?";
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, fileId);
            ResultSet rs = stmt.executeQuery();
            
            if (rs.next()) {
                return mapResultSetToFileMessage(rs);
            }
            return null;
        } catch (SQLException e) {
            System.err.println("Database error retrieving file: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    @Override
public List<FileMessage> getFilesByConversation(String conversationId) {
    List<FileMessage> files = new ArrayList<>();
    String query = "SELECT * FROM files WHERE conversation_id = ? ORDER BY timestamp ASC";
    
    try (Connection conn = DatabaseManager.getConnection();
         PreparedStatement stmt = conn.prepareStatement(query)) {
        
        stmt.setString(1, conversationId);
        ResultSet rs = stmt.executeQuery();
        
        while (rs.next()) {
            FileMessage file = new FileMessage();
            file.setId(rs.getString("id"));
            file.setSenderEmail(rs.getString("sender_email"));
            file.setConversationId(rs.getString("conversation_id"));
            file.setOriginalFilename(rs.getString("original_filename"));
            file.setStoredPath(rs.getString("stored_path"));
            file.setMimeType(rs.getString("mime_type"));
            file.setFileSize(rs.getLong("file_size"));
            file.setTimestamp(rs.getLong("timestamp"));
            file.setDelivered(rs.getBoolean("delivered"));
            file.setViewed(rs.getBoolean("viewed"));
            
            files.add(file);
        }
        
        return files;
    } catch (SQLException e) {
        System.err.println("Database error getting files by conversation: " + e.getMessage());
        e.printStackTrace();
        return new ArrayList<>();
    }
}

    @Override
    public boolean updateFileStatus(String fileId, boolean delivered, boolean viewed) {
        String query = "UPDATE files SET delivered = ?, viewed = ? WHERE id = ?";
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setBoolean(1, delivered);
            stmt.setBoolean(2, viewed);
            stmt.setString(3, fileId);
            
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Database error updating file status: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
    public boolean deleteFile(String fileId) {
        String query = "DELETE FROM files WHERE id = ?";
        
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(query)) {
            
            stmt.setString(1, fileId);
            int rowsAffected = stmt.executeUpdate();
            return rowsAffected > 0;
        } catch (SQLException e) {
            System.err.println("Database error deleting file: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    @Override
public List<JSONObject> getFileHistoryForConversation(String conversationId) {
    String query = "SELECT * FROM files WHERE conversation_id = ? ORDER BY timestamp ASC";
    List<JSONObject> files = new ArrayList<>();
    
    try (Connection conn = DatabaseManager.getConnection();
         PreparedStatement stmt = conn.prepareStatement(query)) {
        
        stmt.setString(1, conversationId);
        ResultSet rs = stmt.executeQuery();
        
        while (rs.next()) {
            JSONObject fileJson = new JSONObject();
            fileJson.put("type", "file");
            fileJson.put("id", rs.getString("id"));
            fileJson.put("sender", rs.getString("sender_email"));
            fileJson.put("conversationId", rs.getString("conversation_id"));
            fileJson.put("filename", rs.getString("original_filename"));
            fileJson.put("mimeType", rs.getString("mime_type"));
            fileJson.put("fileSize", rs.getLong("file_size"));
            fileJson.put("timestamp", rs.getLong("timestamp"));
            fileJson.put("delivered", rs.getBoolean("delivered"));
            fileJson.put("viewed", rs.getBoolean("viewed"));
            
            files.add(fileJson);
        }
        
        return files;
    } catch (SQLException | JSONException e) {
        System.err.println("Error getting file history: " + e.getMessage());
        e.printStackTrace();
        return new ArrayList<>();
    }
}
    
    private FileMessage mapResultSetToFileMessage(ResultSet rs) throws SQLException {
        FileMessage file = new FileMessage();
        file.setId(rs.getString("id"));
        file.setSenderEmail(rs.getString("sender_email"));
        file.setConversationId(rs.getString("conversation_id"));
        file.setOriginalFilename(rs.getString("original_filename"));
        file.setStoredPath(rs.getString("stored_path"));
        file.setMimeType(rs.getString("mime_type"));
        file.setFileSize(rs.getLong("file_size"));
        file.setTimestamp(rs.getLong("timestamp"));
        file.setDelivered(rs.getBoolean("delivered"));
        file.setViewed(rs.getBoolean("viewed"));
        return file;
    }
}