package com.chatapp.server.handler;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

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
    private String userEmail; // Authenticated user's email
    private UserService userService;
    private MessageService messageService;

    // Map to store groups (group name -> list of member emails)
    private static final Map<String, List<String>> groupMap = new ConcurrentHashMap<>();

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

                    switch (messageType) {
                        case "GET_HISTORY":
                            handleHistoryRequest(messageJson);
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

        if (groupMap.containsKey(recipient)) {
            handleGroupMessage(recipient, content);
        } else {
            handleDirectMessage(recipient, content);
        }
    }

    private void handleDirectMessage(String recipient, String content) throws JSONException {
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
        List<String> members = groupMap.get(groupName);
        String messageId = "msg_" + System.currentTimeMillis() + "_" + Integer.toHexString((int) (Math.random() * 10000));

        JSONObject routingMessage = new JSONObject();
        routingMessage.put("id", messageId);
        routingMessage.put("type", "group");
        routingMessage.put("sender", userEmail);
        routingMessage.put("content", content);
        routingMessage.put("groupName", groupName);

        for (String member : members) {
            ClientHandler memberHandler = findClientByEmail(member);
            if (memberHandler != null) {
                memberHandler.sendMessage(routingMessage.toString());
            } else {
                messageService.storeOfflineMessage(member, routingMessage);
            }
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
    }

    private void handleHistoryRequest(JSONObject request) throws JSONException {
        String otherUser = request.getString("otherUser");
        List<JSONObject> history = messageService.getMessageHistory(userEmail, otherUser);

        JSONObject response = new JSONObject();
        response.put("type", "HISTORY_RESPONSE");

        JSONArray messagesArray = new JSONArray();
        for (JSONObject message : history) {
            messagesArray.put(message);
        }

        response.put("messages", messagesArray);
        sendMessage(response.toString());
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
}