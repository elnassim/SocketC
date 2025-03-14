package com.chatapp;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ClientHandler implements Runnable {
    private Socket clientSocket;
    private BufferedReader in;
    private PrintWriter out;
    private List<ClientHandler> clients;
    private String userEmail; // Store authenticated user's email

    public ClientHandler(Socket socket, List<ClientHandler> clients) throws IOException {
        this.clientSocket = socket;
        this.clients = clients;
        this.in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
        this.out = new PrintWriter(clientSocket.getOutputStream(), true);
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
                if (authenticateUser(email, password)) {
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

    private boolean authenticateUser(String email, String password) {
        // Authentication code remains unchanged
        try {
            System.out.println("Looking for Users.json file...");
            URL url = getClass().getResource("/Users.json");
            System.out.println("Resource URL: " + url);
            
            InputStream is = getClass().getResourceAsStream("/Users.json");
            if (is == null) {
                // Try lowercase as fallback
                is = getClass().getResourceAsStream("/users.json");
                if (is == null) {
                    System.err.println("Could not find Users.json in resources");
                    return false;
                }
            }
            
            // Read the JSON file
            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
            reader.close();

            // Parse the JSON file
            try {
                JSONArray users = new JSONArray(jsonContent.toString());
                System.out.println("Found " + users.length() + " users in JSON file");
                
                for (int i = 0; i < users.length(); i++) {
                    JSONObject user = users.getJSONObject(i);
                    if (user.getString("email").equals(email) && user.getString("password").equals(password)) {
                        System.out.println("Found matching user: " + email);
                        return true; // Authentication successful
                    }
                }
            } catch (JSONException e) {
                System.err.println("Error parsing Users.json: " + e.getMessage());
                e.printStackTrace();
                return false;
            }
        } catch (IOException e) {
            System.err.println("IO Error reading Users.json: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("No matching user found for: " + email);
        return false; // Authentication failed
    }

    private void handleChat() {
        try {
            String input;
            while ((input = in.readLine()) != null) {
                try {
                    JSONObject messageJson = new JSONObject(input);
                    String messageType = messageJson.getString("type");
                    
                    if ("private".equals(messageType)) {
                        String recipient = messageJson.getString("to");
                        String content = messageJson.getString("content");
                        
                        // Find recipient in clients list
                        ClientHandler recipientHandler = findClientByEmail(recipient);
                        if (recipientHandler != null) {
                            // Send private message
                            String formattedMessage = userEmail + " (private): " + content;
                            recipientHandler.sendMessage(formattedMessage);
                            // Also send confirmation to sender
                            sendMessage("Message sent to " + recipient);
                        } else {
                            sendMessage("User " + recipient + " is not online.");
                        }
                    } else if ("broadcast".equals(messageType)) {
                        String content = messageJson.getString("content");
                        String formattedMessage = userEmail + ": " + content;
                        broadcastMessage(formattedMessage);
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