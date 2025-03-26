package com.chatapp.server.handler;

import java.io.*;
import java.net.Socket;
import java.net.URL;
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
    private String userEmail; // Store authenticated user's email
    private UserService userService;
    private MessageService messageService;

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
            // Read login credentials from the client
            String credentials = in.readLine();
            System.out.println("Received credentials: " + credentials);

            try {
                JSONObject loginRequest = new JSONObject(credentials);
                String email = loginRequest.getString("email");
                String password = loginRequest.getString("password");
                this.userEmail = email; // Store email for later use

                System.out.println("Attempting to authenticate: " + email);

                // Authenticate the user
                if (userService.authenticateUser(email, password)) {
                    out.println("AUTH_SUCCESS"); // Send success response
                    System.out.println("User authenticated: " + email);

                    // Handle further communication (e.g., chat)
                    handleChat();
                } else {
                    out.println("AUTH_FAILED"); // Send failure response
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
            System.out.println("Client removed from active clients list. Active clients: " + clients.size());
            if (clientSocket != null && !clientSocket.isClosed()) {
                clientSocket.close();
                System.out.println("Client socket closed");
            }
        } catch (IOException e) {
            System.err.println("Error during cleanup: " + e.getMessage());
        }
    }

    private void handleChat() {
        // First, deliver any offline messages waiting for this user
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
                            
                            // Send delivery receipt
                            JSONObject deliveryReceipt = new JSONObject();
                            deliveryReceipt.put("type", "delivery_receipt");
                            deliveryReceipt.put("messageId", messageId);
                            deliveryReceipt.put("status", "delivered");
                            sendMessage(deliveryReceipt.toString());
                        } else {
                            // Store for offline delivery
                            messageService.storeOfflineMessage(recipient, routingMessage);
                            
                            // Send pending status
                            JSONObject pendingReceipt = new JSONObject();
                            pendingReceipt.put("type", "delivery_receipt");
                            pendingReceipt.put("messageId", messageId);
                            pendingReceipt.put("status", "pending");
                            sendMessage(pendingReceipt.toString());
                        }
                    } 
                    else if ("broadcast".equals(messageType)) {
                        String content = messageJson.getString("content");
                        
                        // Create enhanced broadcast message - this saves to history
                        JSONObject broadcastMsg = messageService.createBroadcastMessage(userEmail, content);
                        broadcastMessage(broadcastMsg.toString());
                    }
                    else if ("read_receipt".equals(messageType)) {
                        String messageId = messageJson.getString("messageId");
                        String sender = messageJson.getString("sender");
                        
                        // Find original sender and forward the read receipt
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
                    else if ("disconnect".equals(messageType)) {
                        // Handle proper disconnection
                        System.out.println("Client " + userEmail + " disconnecting...");
                        break;
                    }
                    
                } catch (JSONException e) {
                    // If not JSON, treat as legacy plain text message
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
            
            // Send a system notification about offline messages
            JSONObject notification = new JSONObject();
            notification.put("type", "system");
            notification.put("content", "You received " + pendingMessages.size() + 
                            " message(s) while you were offline");
            sendMessage(notification.toString());
        }
    }
    public String getUserEmail() {
        return userEmail;
    }

    // Find a client by email
    private ClientHandler findClientByEmail(String email) {
        for (ClientHandler client : clients) {
            if (email.equals(client.userEmail)) {
                return client;
            }
        }
        return null;
    }

    // Method to broadcast message to all clients
    private void broadcastMessage(String message) {
        synchronized (clients) {
            for (ClientHandler client : clients) {
                client.sendMessage(message);
            }
        }
    }

    // Send message to this client
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }
}
