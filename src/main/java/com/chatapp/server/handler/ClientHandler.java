package com.chatapp.server.handler;

import java.io.*;
import java.net.Socket;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.chatapp.server.service.UserService;
import com.chatapp.server.service.MessageService;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private BufferedReader in;
    private PrintWriter out;
    private List<ClientHandler> clients;
    private String userEmail; // authenticated user's email
    private UserService userService;
    private MessageService messageService;

    // *** GROUP FEATURE: Map to store groups (group name -> list of member emails)
    private static final java.util.Map<String, List<String>> groupMap = new java.util.concurrent.ConcurrentHashMap<>();

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
            String credentials = in.readLine();
            System.out.println("Received credentials: " + credentials);

            try {
                JSONObject loginRequest = new JSONObject(credentials);
                String email = loginRequest.getString("email");
                String password = loginRequest.getString("password");
                this.userEmail = email;
                System.out.println("Attempting to authenticate: " + email);

                if (userService.authenticateUser(email, password)) {
                    out.println("AUTH_SUCCESS");
                    System.out.println("User authenticated: " + email);
                    handleChat();
                } else {
                    out.println("AUTH_FAILED");
                    System.out.println("Authentication failed for: " + email);
                }
            } catch (JSONException e) {
                System.err.println("Error processing JSON: " + e.getMessage());
                e.printStackTrace();
                out.println("AUTH_ERROR: Invalid request format");
            }
        } catch (IOException e) {
            System.err.println("Client disconnected: " + clientSocket.getInetAddress());
        } finally {
            cleanup();
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
                    
                    // Handle history requests
                    if ("GET_HISTORY".equals(messageType)) {
                        handleHistoryRequest(messageJson);
                        continue; // Skip other processing for history requests
                    }
                    
                    // Handle private messages
                    if ("private".equals(messageType)) {
                        String recipient = messageJson.getString("to");
                        String content = messageJson.getString("content");
<<<<<<< HEAD
                        
                        // Use MessageService to create and save the message
                        // This will automatically save it to conversation history
                        JSONObject routingMessage = messageService.createPrivateMessage(
                            userEmail, recipient, content);
                        
                        // Get the message ID
                        String messageId = routingMessage.getString("id");
                        
                        // Find recipient in clients list
                        ClientHandler recipientHandler = findClientByEmail(recipient);
                        if (recipientHandler != null) {
                            recipientHandler.sendMessage(routingMessage.toString());
=======
                        // *** GROUP FEATURE: Check if recipient is a group
                        if (groupMap.containsKey(recipient)) {
                            List<String> members = groupMap.get(recipient);
                            String messageId = "msg_" + System.currentTimeMillis() + "_" + 
                                               Integer.toHexString((int)(Math.random() * 10000));
                            JSONObject routingMessage = new JSONObject();
                            routingMessage.put("id", messageId);
                            routingMessage.put("type", "private");
                            routingMessage.put("sender", userEmail);
                            routingMessage.put("content", content);
                            // Mark this message as a group message
                            routingMessage.put("isGroup", true);
                            routingMessage.put("groupName", recipient);
                            
                            for (String m : members) {
                                ClientHandler memberHandler = findClientByEmail(m);
                                if (memberHandler != null) {
                                    memberHandler.sendMessage(routingMessage.toString());
                                } else {
                                    messageService.storeOfflineMessage(m, routingMessage);
                                }
                            }
>>>>>>> soso
                            
                            JSONObject deliveryReceipt = new JSONObject();
                            deliveryReceipt.put("type", "delivery_receipt");
                            deliveryReceipt.put("messageId", messageId);
                            deliveryReceipt.put("status", "delivered");
                            sendMessage(deliveryReceipt.toString());
                        } else {
                            String conversationId = messageService.generateConversationId(userEmail, recipient);
                            String messageId = "msg_" + System.currentTimeMillis() + "_" + 
                                               Integer.toHexString((int)(Math.random() * 10000));
                            JSONObject routingMessage = new JSONObject();
                            routingMessage.put("id", messageId);
                            routingMessage.put("type", "private");
                            routingMessage.put("sender", userEmail);
                            routingMessage.put("content", content);
                            routingMessage.put("conversationId", conversationId);
                            routingMessage.put("timestamp", System.currentTimeMillis());
                            routingMessage.put("delivered", false);
                            routingMessage.put("read", false);
                            
                            ClientHandler recipientHandler = findClientByEmail(recipient);
                            if (recipientHandler != null) {
                                recipientHandler.sendMessage(routingMessage.toString());
                                
                                JSONObject deliveryReceipt = new JSONObject();
                                deliveryReceipt.put("type", "delivery_receipt");
                                deliveryReceipt.put("messageId", messageId);
                                deliveryReceipt.put("status", "delivered");
                                sendMessage(deliveryReceipt.toString());
                            } else {
                                messageService.storeOfflineMessage(recipient, routingMessage);
                                
                                JSONObject pendingReceipt = new JSONObject();
                                pendingReceipt.put("type", "delivery_receipt");
                                pendingReceipt.put("messageId", messageId);
                                pendingReceipt.put("status", "pending");
                                sendMessage(pendingReceipt.toString());
                            }
                        }
                    } 
                    else if ("broadcast".equals(messageType)) {
                        String content = messageJson.getString("content");
<<<<<<< HEAD
                        
                        // Create enhanced broadcast message - this saves to history
=======
>>>>>>> soso
                        JSONObject broadcastMsg = messageService.createBroadcastMessage(userEmail, content);
                        broadcastMessage(broadcastMsg.toString());
                    }
                    else if ("read_receipt".equals(messageType)) {
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
                    // *** GROUP FEATURE: Handle group creation request ***
                    else if ("create_group".equals(messageType)) {
                        String groupName = messageJson.getString("groupName");
                        JSONArray membersArray = messageJson.getJSONArray("members");
                        List<String> groupMembers = new java.util.ArrayList<>();
                        for (int i = 0; i < membersArray.length(); i++) {
                            groupMembers.add(membersArray.getString(i));
                        }
                        groupMap.put(groupName, groupMembers);
                        
                        for (String member : groupMembers) {
                            ClientHandler memberHandler = findClientByEmail(member);
                            if (memberHandler != null) {
                                JSONObject groupCreatedMsg = new JSONObject();
                                groupCreatedMsg.put("type", "group_created");
                                groupCreatedMsg.put("groupName", groupName);
                                groupCreatedMsg.put("info", "You have been added to a new group");
                                memberHandler.sendMessage(groupCreatedMsg.toString());
                            }
                        }
                        
                        JSONObject ack = new JSONObject();
                        ack.put("type", "system");
                        ack.put("content", "Group '" + groupName + "' created successfully!");
                        sendMessage(ack.toString());
                        
                        System.out.println("Group created: " + groupName + " with members: " + groupMembers);
                    }
                    else if ("disconnect".equals(messageType)) {
                        System.out.println("Client " + userEmail + " disconnecting...");
                        break;
                    }
                    
                } catch (JSONException e) {
                    System.out.println("Received non-JSON message: " + input);
                    final String formattedMessage = userEmail + ": " + input;
                    broadcastMessage(formattedMessage);
                }
            }
        } catch (IOException e) {
            System.err.println("Client disconnected during chat: " + clientSocket.getInetAddress());
        }
    }


    private void handleHistoryRequest(JSONObject request) {
        try {
            String otherUser = request.getString("otherUser");
            System.out.println("History request received from " + userEmail + " for conversation with " + otherUser);
            
            List<JSONObject> history = messageService.getMessageHistory(userEmail, otherUser);
            System.out.println("Found " + history.size() + " messages in history");
            
            // Create history response
            JSONObject response = new JSONObject();
            response.put("type", "HISTORY_RESPONSE");
            
            JSONArray messagesArray = new JSONArray();
            for (JSONObject message : history) {
                messagesArray.put(message);
            }
            
            response.put("messages", messagesArray);
            sendMessage(response.toString());
            System.out.println("Sent history response with " + messagesArray.length() + " messages to " + userEmail);
        } catch (JSONException e) {
            System.err.println("Error processing history request: " + e.getMessage());
            e.printStackTrace();
        }
    }
    private void sendOfflineMessages() {
        List<String> pendingMessages = messageService.getOfflineMessages(userEmail);
        if (!pendingMessages.isEmpty()) {
            System.out.println("Delivering " + pendingMessages.size() + " offline messages to " + userEmail);
            for (String messageJson : pendingMessages) {
                sendMessage(messageJson);
            }
            JSONObject notification = new JSONObject();
            notification.put("type", "system");
            notification.put("content", "You received " + pendingMessages.size() + " message(s) while you were offline");
            sendMessage(notification.toString());
        }
    }
    
    public String getUserEmail() {
        return userEmail;
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
}
