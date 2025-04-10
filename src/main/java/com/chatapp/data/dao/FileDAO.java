package com.chatapp.data.dao;

import com.chatapp.common.model.FileMessage;
import java.util.List;

import org.json.JSONObject;

public interface FileDAO {
    boolean saveFileMetadata(FileMessage file);
    FileMessage getFileById(String fileId);
    List<FileMessage> getFilesByConversation(String conversationId);
    boolean updateFileStatus(String fileId, boolean delivered, boolean viewed);
    boolean deleteFile(String fileId);
    // Add to FileDAO.java
List<JSONObject> getFileHistoryForConversation(String conversationId);
}