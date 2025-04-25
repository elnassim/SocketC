package com.chatapp.data.dao;

import com.chatapp.common.model.Message;
import org.json.JSONObject;
import java.util.List;

public interface MessageDAO {
    boolean save(Message message);
    List<JSONObject> getConversationHistory(String user1, String user2);
    boolean updateStatus(String messageId, Message.Status status);
    List<String> getOfflineMessages(String userEmail);
    List<JSONObject> getGroupMessages(String groupConversationId);
    boolean deleteMessage(String messageId);
}
