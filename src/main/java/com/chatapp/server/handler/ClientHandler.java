package com.chatapp.server.handler;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.chatapp.common.model.FileMessage;
import com.chatapp.common.model.Group;
import com.chatapp.common.model.Message;
import com.chatapp.data.dao.MessageDAO;
import com.chatapp.data.dao.impl.MessageDAOImpl;
import com.chatapp.server.service.FileService;
import com.chatapp.server.service.GroupService;
import com.chatapp.server.service.MessageService;
import com.chatapp.server.service.UserService;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private BufferedReader in;
    private PrintWriter out;
    private List<ClientHandler> clients;
    private String userEmail; // Authenticated user's email
    private UserService userService;
    private MessageService messageService;
    

    // Map to store groups (group name -> list of member emails)
    private static final GroupService groupService = new GroupService();
    private static final FileService fileService = new FileService();

    public ClientHandler(Socket socket, List<ClientHandler> clients) throws IOException {
        this.clientSocket = socket;
        this.clients = clients;
        this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        this.out = new PrintWriter(clientSocket.getOutputStream(), true);
        this.userService = new UserService();
        this.messageService = new MessageService();
    }

    
    @Override
    public void run() {
        try {
            String firstLine = in.readLine();
            System.out.println("Received: " + firstLine);

            JSONObject req = new JSONObject(firstLine);
            String reqType = req.optString("type", "LOGIN");

            if ("REGISTER".equals(reqType)) {
                handleRegistration(req);
                return;
            }

            if ("auth".equals(reqType)) {
                handleAuthentication(req);
            }

        } catch (IOException | JSONException e) {
            System.err.println("Error in ClientHandler: " + e.getMessage());
            sendErrorResponse("Internal server error: " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void handleAuthentication(JSONObject req) {
        try {
            String email = req.getString("email");
            String password = req.getString("password");
            String publicKey = req.optString("publicKey", null);
            this.userEmail = email;

            System.out.println("Authenticating user: " + email);
            
            if (userService.authenticateUser(email, password)) {
                System.out.println("Authentication successful for: " + email);
                
                JSONObject response = new JSONObject();
                response.put("type", "auth");
                response.put("success", true);
                response.put("email", email);
                if (publicKey != null) {
                    response.put("publicKeyReceived", true);
                }
                
                out.println(response.toString());
                System.out.println("Sent success response: " + response.toString());
                
                handleChat();
            } else {
                System.out.println("Authentication failed for: " + email);
                
                JSONObject response = new JSONObject();
                response.put("type", "auth");
                response.put("success", false);
                response.put("message", "Invalid email or password");
                
                out.println(response.toString());
                System.out.println("Sent failure response: " + response.toString());
            }
        } catch (Exception e) {
            System.err.println("Error during authentication: " + e.getMessage());
            sendErrorResponse("Authentication error: " + e.getMessage());
        }
    }

    private void handleRegistration(JSONObject req) {
        try {
            String username = req.getString("username");
            String email = req.getString("email");
            String password = req.getString("password");

            // Validate email format
            if (!email.matches("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")) {
                JSONObject response = new JSONObject();
                response.put("type", "register");
                response.put("success", false);
                response.put("message", "Invalid email format");
                out.println(response.toString());
                return;
            }

            // Validate username
            if (username.length() < 3) {
                JSONObject response = new JSONObject();
                response.put("type", "register");
                response.put("success", false);
                response.put("message", "Username must be at least 3 characters long");
                out.println(response.toString());
                return;
            }

            // Validate password
            if (password.length() < 6) {
                JSONObject response = new JSONObject();
                response.put("type", "register");
                response.put("success", false);
                response.put("message", "Password must be at least 6 characters long");
                out.println(response.toString());
                return;
            }

            System.out.println("Attempting to register user: " + email);
            boolean created = userService.registerUser(username, password, email);
            
            JSONObject response = new JSONObject();
            response.put("type", "register");
            response.put("success", created);
            if (!created) {
                response.put("message", "Registration failed - email might already be in use");
            } else {
                response.put("message", "Registration successful");
            }
            
            System.out.println("Registration result for " + email + ": " + (created ? "success" : "failed"));
            out.println(response.toString());
        } catch (Exception e) {
            System.err.println("Error during registration: " + e.getMessage());
            e.printStackTrace();
            sendErrorResponse("Registration error: " + e.getMessage());
        }
    }

    private void sendErrorResponse(String message) {
        try {
            JSONObject response = new JSONObject();
            response.put("type", "error");
            response.put("success", false);
            response.put("message", message);
            out.println(response.toString());
        } catch (JSONException e) {
            System.err.println("Error creating error response: " + e.getMessage());
        }
    }

    private void cleanup() {
        try {
            clients.remove(this);
            System.out.println("Client removed. Active clients: " + clients.size());
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
                System.out.println("Client socket closed");
            }
        } catch (IOException e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }

    private void handleChat() {
        sendOfflineMessages();
        try {
            String input;
            while ((input = in.readLine()) != null) {
                try {
                    JSONObject messageJson = new JSONObject(input);
                    String messageType = messageJson.getString("type");

                    switch (messageType) {
                        case "GET_HISTORY":
                            handleHistoryRequest(messageJson);
                            break;

                        case "GET_GROUP_HISTORY":
                            handleGroupHistoryRequest(messageJson);
                            break;

                        case "private":
                            handlePrivateMessage(messageJson);
                            break;

                        case "broadcast":
                            handleBroadcastMessage(messageJson);
                            break;

                        case "read_receipt":
                            handleReadReceipt(messageJson);
                            break;

                        case "create_group":
                            handleCreateGroup(messageJson);
                            break;
                        
                        case "get_groups":
                            handleGetGroups();
                            break;

                            case "file_upload":
                            handleFileUpload(messageJson);
                            break;
                            
                        case "file_download":
                            handleFileDownload(messageJson);
                            break;
                        case "group_file_upload":
                            handleGroupFileUpload(messageJson);
                            break;

                        case "disconnect":
                            System.out.println("Client " + userEmail + " disconnecting...");
                            return;

                        default:
                            System.out.println("Unknown message type: " + messageType);
                            break;
                    }
                } catch (JSONException e) {
                    System.out.println("Received non-JSON message: " + input);
                    broadcastMessage(userEmail + ": " + input);
                }
            }
        } catch (IOException e) {
            System.err.println("Client disconnected during chat: " + clientSocket.getInetAddress());
        }
    }

    private void handlePrivateMessage(JSONObject messageJson) throws JSONException {
        String recipient = messageJson.getString("to");
        String content = messageJson.getString("content");

        // Use GroupService instead of groupMap
        if (groupService.findGroupByName(recipient) != null) {
            handleGroupMessage(recipient, content);
        } else {
            handleDirectMessage(recipient, content);
        }
    }

    private void handleDirectMessage(String recipient, String content) throws JSONException {
        // The message is already encrypted by the client, we just need to forward it
        JSONObject routingMessage = messageService.createPrivateMessage(userEmail, recipient, content);
        String messageId = routingMessage.getString("id");

        ClientHandler recipientHandler = findClientByEmail(recipient);
        if (recipientHandler != null) {
            recipientHandler.sendMessage(routingMessage.toString());
            sendDeliveryReceipt(messageId, "delivered");
        } else {
            messageService.storeOfflineMessage(recipient, routingMessage);
            sendDeliveryReceipt(messageId, "pending");
        }
    }

    private void handleGroupMessage(String groupName, String content) throws JSONException {
        Group group = groupService.findGroupByName(groupName);
        
        if (group == null) {
            JSONObject error = new JSONObject();
            error.put("type", "error");
            error.put("content", "Group '" + groupName + "' not found");
            sendMessage(error.toString());
            return;
        }
        
        List<String> members = group.getMembersEmails();
        System.out.println("Handling group message to " + groupName + " with " + members.size() + " members");
        
        String messageId = "msg_" + System.currentTimeMillis() + "_" + Integer.toHexString((int) (Math.random() * 10000));
    
        // Create message object for database storage
        Message messageObj = new Message(userEmail, content, "group");
        messageObj.setId(messageId);
        messageObj.setConversationId("group_" + groupName);
        messageObj.setTimestamp(System.currentTimeMillis());
        
        // Use the direct save method instead of the regular one
        boolean saved = groupService.ultraDirectSaveGroupMessage(messageObj, groupName);
        System.out.println("DIRECT SAVE RESULT: " + (saved ? "SUCCESS" : "FAILED"));

        if (!saved) {
            System.out.println("Message failed to save - running diagnostic:");
            groupService.debugGroupMessageSaving(groupName);
        }
        
        // Create JSON message for delivery
        JSONObject routingMessage = new JSONObject();
        routingMessage.put("id", messageId);
        routingMessage.put("type", "private");
        routingMessage.put("isGroup", true);
        routingMessage.put("sender", userEmail);
        routingMessage.put("content", content);
        routingMessage.put("groupName", groupName);
        routingMessage.put("timestamp", System.currentTimeMillis());
        
        // Send to ALL members, INCLUDING sender (for consistency)
        for (String member : members) {
            ClientHandler memberHandler = findClientByEmail(member);
            if (memberHandler != null) {
                // Whether it's the sender or not, deliver the message
                System.out.println("Delivering group message to online member: " + member);
                memberHandler.sendMessage(routingMessage.toString());
            } else if (!member.equals(userEmail)) {
                // For offline users (except self), store as offline message
                System.out.println("Storing offline message for: " + member);
                messageService.storeOfflineMessage(member, routingMessage);
            }
        }
    }
    private void handleGroupHistoryRequest(JSONObject request) throws JSONException {
        String groupName = request.getString("groupName");
        System.out.println("History requested for group: " + groupName);
        
        try {
            // Get group messages from database - use the specialized group conversation ID format
            MessageDAO messageDAO = new MessageDAOImpl();
            String groupConversationId = "group_" + groupName;
            List<JSONObject> messages = messageDAO.getGroupMessages(groupConversationId);
            System.out.println("Found " + messages.size() + " text messages for group: " + groupName);
            
            // Get group files from database
            List<FileMessage> files = fileService.getFilesByConversation(groupConversationId);
            if (files != null && !files.isEmpty()) {
                System.out.println("Found " + files.size() + " files for group: " + groupName);
                
                for (FileMessage file : files) {
                    JSONObject fileJson = file.toJson();
                    fileJson.put("type", "file");
                    fileJson.put("groupName", groupName);
                    messages.add(fileJson);
                }
            }
            
            // Sort all messages by timestamp
            messages.sort((msg1, msg2) -> {
                long time1 = msg1.optLong("timestamp", 0);
                long time2 = msg2.optLong("timestamp", 0);
                return Long.compare(time1, time2);
            });
            
            // Prepare and send response
            JSONObject response = new JSONObject();
            response.put("type", "GROUP_HISTORY_RESPONSE");
            response.put("groupName", groupName);
            
            JSONArray messagesArray = new JSONArray();
            for (JSONObject message : messages) {
                messagesArray.put(message);
            }
            
            response.put("messages", messagesArray);
            sendMessage(response.toString());
            
        } catch (Exception e) {
            System.err.println("Error handling group history request: " + e.getMessage());
            e.printStackTrace();
            
            // Send empty response
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("type", "GROUP_HISTORY_RESPONSE");
            errorResponse.put("groupName", groupName);
            errorResponse.put("messages", new JSONArray());
            errorResponse.put("error", "Failed to retrieve history: " + e.getMessage());
            sendMessage(errorResponse.toString());
        }
    }

    // Add these methods to ClientHandler
private void handleFileUpload(JSONObject messageJson) throws JSONException {
    String recipient = messageJson.getString("to");
    String filename = messageJson.getString("filename");
    String mimeType = messageJson.getString("mimeType");
    String base64Data = messageJson.getString("data");
    
    // Decode base64 data
    byte[] fileData = java.util.Base64.getDecoder().decode(base64Data);
    
    // Save file
    FileMessage file = fileService.saveFile(userEmail, recipient, filename, fileData, mimeType);
    
    if (file != null) {
        // Notify recipient if online
        ClientHandler recipientHandler = findClientByEmail(recipient);
        JSONObject fileNotification = file.toJson();
        
        if (recipientHandler != null) {
            recipientHandler.sendMessage(fileNotification.toString());
            
            // Send delivery receipt to sender
            JSONObject receipt = new JSONObject();
            receipt.put("type", "file_receipt");
            receipt.put("fileId", file.getId());
            receipt.put("status", "delivered");
            sendMessage(receipt.toString());
            
            // Update file status in database
            fileService.updateFileStatus(file.getId(), true, false);
        } else {
            // Store notification for offline delivery
            JSONObject offlineMsg = new JSONObject();
            offlineMsg.put("type", "file");
            offlineMsg.put("id", file.getId());
            offlineMsg.put("sender", userEmail);
            offlineMsg.put("filename", filename);
            offlineMsg.put("mimeType", mimeType);
            offlineMsg.put("fileSize", fileData.length);
            messageService.storeOfflineMessage(recipient, offlineMsg);
            
            // Send pending receipt to sender
            JSONObject receipt = new JSONObject();
            receipt.put("type", "file_receipt");
            receipt.put("fileId", file.getId());
            receipt.put("status", "pending");
            sendMessage(receipt.toString());
        }
    } else {
        // Send error to sender
        JSONObject error = new JSONObject();
        error.put("type", "error");
        error.put("content", "Failed to save file: " + filename);
        sendMessage(error.toString());
    }
}
    


    
private void handleFileDownload(JSONObject messageJson) throws JSONException {
    String fileId = messageJson.getString("fileId");
    
    // Get file data
    byte[] fileData = fileService.getFileData(fileId);
    
    if (fileData != null) {
        // Base64 encode for transmission
        String base64Data = java.util.Base64.getEncoder().encodeToString(fileData);
        
        // Send file data to requester
        JSONObject fileResponse = new JSONObject();
        fileResponse.put("type", "file_data");
        fileResponse.put("fileId", fileId);
        fileResponse.put("data", base64Data);
        sendMessage(fileResponse.toString());
        
        // Mark file as viewed
        fileService.updateFileStatus(fileId, true, true);
    } else {
        // Send error
        JSONObject error = new JSONObject();
        error.put("type", "error");
        error.put("content", "File not found: " + fileId);
        sendMessage(error.toString());
    }
}
private void handleGroupFileUpload(JSONObject fileUpload) throws JSONException {
    String groupName = fileUpload.getString("groupName");
    String filename = fileUpload.getString("filename");
    String mimeType = fileUpload.getString("mimeType");
    String base64Data = fileUpload.getString("data");
    
    // Decode file data
    byte[] fileData = java.util.Base64.getDecoder().decode(base64Data);
    
    // Save file and metadata
    FileMessage fileMessage = fileService.saveGroupFile(userEmail, groupName, filename, fileData, mimeType);
    
    if (fileMessage != null) {
        Group group = groupService.findGroupByName(groupName);
        if (group != null) {
            // Create notification message for all group members
            JSONObject fileNotification = fileMessage.toJson();
            fileNotification.put("type", "file");
            fileNotification.put("sender", userEmail);
            fileNotification.put("groupName", groupName);
            
            // Notify all group members
            for (String member : group.getMembersEmails()) {
                if (!member.equals(userEmail)) { // Don't send to self
                    ClientHandler memberHandler = findClientByEmail(member);
                    if (memberHandler != null) {
                        memberHandler.sendMessage(fileNotification.toString());
                    } else {
                        // Store for offline delivery
                        messageService.storeOfflineMessage(member, fileNotification);
                    }
                }
            }
            
            // Confirm file was uploaded to sender
            JSONObject confirmation = new JSONObject();
            confirmation.put("type", "file_receipt");
            confirmation.put("fileId", fileMessage.getId());
            confirmation.put("status", "Delivered to server");
            sendMessage(confirmation.toString());
        }
    } else {
        // Send error message back to sender
        JSONObject error = new JSONObject();
        error.put("type", "error");
        error.put("content", "Failed to upload file: " + filename);
        sendMessage(error.toString());
    }
}

    private void handleBroadcastMessage(JSONObject messageJson) throws JSONException {
        String content = messageJson.getString("content");
        JSONObject broadcastMsg = messageService.createBroadcastMessage(userEmail, content);
        broadcastMessage(broadcastMsg.toString());
    }

    private void handleReadReceipt(JSONObject messageJson) throws JSONException {
        String messageId = messageJson.getString("messageId");
        String sender = messageJson.getString("sender");

        ClientHandler senderHandler = findClientByEmail(sender);
        if (senderHandler != null) {
            JSONObject readReceipt = new JSONObject();
            readReceipt.put("type", "read_receipt");
            readReceipt.put("messageId", messageId);
            readReceipt.put("reader", userEmail);
            readReceipt.put("timestamp", System.currentTimeMillis());
            senderHandler.sendMessage(readReceipt.toString());
        }
    }

    private void handleCreateGroup(JSONObject messageJson) throws JSONException {
        String groupName = messageJson.getString("groupName");
        JSONArray membersArray = messageJson.getJSONArray("members");
        List<String> groupMembers = new ArrayList<>();

        for (int i = 0; i < membersArray.length(); i++) {
            groupMembers.add(membersArray.getString(i));
        }
        
        System.out.println("Creating group " + groupName + " with " + groupMembers.size() + " members");
        
        // Add the sender if not already in members list
        if (!groupMembers.contains(userEmail)) {
            groupMembers.add(userEmail);
        }
        
        // Use GroupService instead of groupMap
        Group createdGroup = groupService.createGroup(groupName, groupMembers);
        
        if (createdGroup != null) {
            for (String member : groupMembers) {
                ClientHandler memberHandler = findClientByEmail(member);
                if (memberHandler != null) {
                    JSONObject groupCreatedMsg = new JSONObject();
                    groupCreatedMsg.put("type", "group_created");
                    groupCreatedMsg.put("groupName", groupName);
                    groupCreatedMsg.put("info", "You have been added to a new group");
                    
                    // Add members list to the notification
                    JSONArray membersJsonArray = new JSONArray();
                    for (String m : groupMembers) {
                        membersJsonArray.put(m);
                    }
                    groupCreatedMsg.put("members", membersJsonArray);
                    
                    memberHandler.sendMessage(groupCreatedMsg.toString());
                }
            }

            JSONObject ack = new JSONObject();
            ack.put("type", "system");
            ack.put("content", "Group '" + groupName + "' created successfully!");
            sendMessage(ack.toString());
        } else {
            JSONObject error = new JSONObject();
            error.put("type", "error");
            error.put("content", "Failed to create group '" + groupName + "'");
            sendMessage(error.toString());
        }
    }

    private void handleHistoryRequest(JSONObject request) throws JSONException {
        String otherUser = request.getString("otherUser");
        System.out.println("History requested between " + userEmail + " and " + otherUser);
        
        try {
            List<JSONObject> history = messageService.getMessageHistory(userEmail, otherUser);
            System.out.println("Found " + history.size() + " total messages in history");
            
            // Check if this is a group chat request
            boolean isGroup = !otherUser.contains("@");
            if (isGroup) {
                try {
                    // Add group files to history
                    List<FileMessage> groupFiles = fileService.getFilesByConversation("group_" + otherUser);
                    if (groupFiles != null) {
                        for (FileMessage file : groupFiles) {
                            JSONObject fileJson = file.toJson();
                            fileJson.put("type", "file");
                            fileJson.put("groupName", otherUser);
                            history.add(fileJson);
                        }
                        
                        // Re-sort by timestamp
                        history.sort((msg1, msg2) -> {
                            long time1 = msg1.optLong("timestamp", 0);
                            long time2 = msg2.optLong("timestamp", 0);
                            return Long.compare(time1, time2);
                        });
                    }
                } catch (Exception e) {
                    System.err.println("Error adding group files to history: " + e.getMessage());
                }
            }
            
            JSONObject response = new JSONObject();
            response.put("type", "HISTORY_RESPONSE");
            JSONArray messagesArray = new JSONArray();
            for (JSONObject message : history) {
                messagesArray.put(message);
            }
            response.put("messages", messagesArray);
            sendMessage(response.toString());
        } catch (Exception e) {
            System.err.println("Error handling history request: " + e.getMessage());
            e.printStackTrace();
            
            // Send empty response to avoid client waiting indefinitely
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("type", "HISTORY_RESPONSE");
            errorResponse.put("messages", new JSONArray());
            errorResponse.put("error", "Failed to retrieve history: " + e.getMessage());
            sendMessage(errorResponse.toString());
        }
    }

    private void sendOfflineMessages() {
        List<String> pendingMessages = messageService.getOfflineMessages(userEmail);
        for (String messageJson : pendingMessages) {
            sendMessage(messageJson);
        }
    }

    private void sendDeliveryReceipt(String messageId, String status) throws JSONException {
        JSONObject receipt = new JSONObject();
        receipt.put("type", "delivery_receipt");
        receipt.put("messageId", messageId);
        receipt.put("status", status);
        sendMessage(receipt.toString());
    }

    private ClientHandler findClientByEmail(String email) {
        for (ClientHandler client : clients) {
            if (email.equals(client.userEmail)) {
                return client;
            }
        }
        return null;
    }

    private void broadcastMessage(String message) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                client.sendMessage(message);
            }
        }
    }

    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    private void handleGetGroups() throws JSONException {
        List<Group> userGroups = groupService.getGroupsByUser(userEmail);
        
        JSONObject response = new JSONObject();
        response.put("type", "groups_list");
        
        JSONArray groupsArray = new JSONArray();
        for (Group group : userGroups) {
            JSONObject groupJson = new JSONObject();
            groupJson.put("name", group.getGroupName());
            
            JSONArray membersArray = new JSONArray();
            for (String member : group.getMembersEmails()) {
                membersArray.put(member);
            }
            groupJson.put("members", membersArray);
            groupsArray.put(groupJson);
        }
        
        response.put("groups", groupsArray);
        sendMessage(response.toString());
    }
}