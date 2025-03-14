package com.chatapp;

import java.io.*;
import java.net.Socket;
import java.net.ConnectException;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import org.json.JSONException;

public class Client {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 1234;
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private static HashSet<String> contacts = new HashSet<>();
    private static String userEmail;
    private static final String CONTACTS_FILE_PREFIX = "contacts_";
    
    public static void main(String[] args) {
        try (
            Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            BufferedReader consoleInput = new BufferedReader(new InputStreamReader(System.in))
        ) {
            System.out.println("Connected to server at " + SERVER_HOST + ":" + SERVER_PORT);

            // Get user credentials
            System.out.print("Enter email: ");
            userEmail = consoleInput.readLine();
            System.out.print("Enter password: ");
            String password = consoleInput.readLine();

            // Send authentication request
            JSONObject loginRequest = new JSONObject();
            loginRequest.put("email", userEmail);
            loginRequest.put("password", password);
            String loginJson = loginRequest.toString();
            System.out.println("Sending auth request: " + loginJson);
            out.println(loginJson);

            // Get authentication response
            String authResponse = in.readLine();
            System.out.println("Server response: " + authResponse);

            // If authenticated, start chat
            if ("AUTH_SUCCESS".equals(authResponse)) {
                System.out.println("Authentication successful!");
                
                // Load user contacts
                loadContacts();
                System.out.println("Your contacts: " + contacts);
                System.out.println("\nAvailable commands:");
                System.out.println("/add email@example.com - Add a contact");
                System.out.println("/list - List all contacts");
                System.out.println("@email@example.com message - Send private message");
                System.out.println("message - Send message to everyone");
                System.out.println("exit - Quit the application\n");
                
                // Start a separate thread to handle server messages
                Thread receiverThread = new Thread(() -> {
                    try {
                        String serverMsg;
                        while (running.get() && (serverMsg = in.readLine()) != null) {
                            // Try to parse as JSON first (server might send structured messages)
                            try {
                                JSONObject msgJson = new JSONObject(serverMsg);
                                if (msgJson.has("type") && msgJson.has("content")) {
                                    String type = msgJson.getString("type");
                                    String content = msgJson.getString("content");
                                    String sender = msgJson.optString("sender", "Server");
                                    
                                    if ("private".equals(type)) {
                                        System.out.println("[PRIVATE] " + sender + ": " + content);
                                    } else {
                                        System.out.println(sender + ": " + content);
                                    }
                                } else {
                                    System.out.println(serverMsg);
                                }
                            } catch (JSONException e) {
                                // Not JSON, print as is
                                System.out.println(serverMsg);
                            }
                        }
                    } catch (IOException e) {
                        if (running.get()) {
                            System.err.println("Connection to server lost: " + e.getMessage());
                        }
                    }
                });
                receiverThread.setDaemon(true);
                receiverThread.start();
                
                // Main thread handles user input
                String message;
                System.out.println("Type messages to send:");
                while ((message = consoleInput.readLine()) != null) {
                    if ("exit".equalsIgnoreCase(message)) {
                        break;
                    } else if (message.startsWith("/add")) {
                        if (message.split(" ").length > 1) {
                            String contact = message.split(" ", 2)[1];
                            if (!contacts.contains(contact)) {
                                contacts.add(contact);
                                saveContacts();
                                System.out.println(contact + " added to contacts.");
                            } else {
                                System.out.println(contact + " is already in your contacts.");
                            }
                        } else {
                            System.out.println("Usage: /add email@example.com");
                        }
                    } else if (message.startsWith("/list")) {
                        System.out.println("Your contacts: " + contacts);
                    } else if (message.startsWith("@")) {
                        int spaceIndex = message.indexOf(" ");
                        if (spaceIndex > 1) {
                            String recipient = message.substring(1, spaceIndex);
                            if (contacts.contains(recipient)) {
                                // Create JSON for private message
                                JSONObject privateMsg = new JSONObject();
                                privateMsg.put("type", "private");
                                privateMsg.put("to", recipient);
                                privateMsg.put("content", message.substring(spaceIndex + 1));
                                privateMsg.put("from", userEmail);  // Add sender information
                                out.println(privateMsg.toString());
                            } else {
                                System.out.println("User not in contacts. Add them first using /add email@example.com");
                            }
                        } else {
                            System.out.println("Usage: @email@example.com message");
                        }
                    } else {
                        // Regular broadcast message
                        JSONObject broadcastMsg = new JSONObject();
                        broadcastMsg.put("type", "broadcast");
                        broadcastMsg.put("content", message);
                        broadcastMsg.put("from", userEmail);  // Add sender information
                        out.println(broadcastMsg.toString());
                    }
                }
                
                // Signal threads to stop and clean up
                running.set(false);
                // Send a disconnect message
                JSONObject disconnectMsg = new JSONObject();
                disconnectMsg.put("type", "disconnect");
                out.println(disconnectMsg.toString());
                socket.close();
            } else {
                if (authResponse == null) {
                    System.out.println("Server closed connection without responding. The server might have crashed.");
                } else {
                    System.out.println("Authentication failed. Exiting...");
                }
            }
        } catch (ConnectException e) {
            System.err.println("Failed to connect to server. Is the server running?");
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void loadContacts() {
        String filename = CONTACTS_FILE_PREFIX + userEmail.replace("@", "_at_").replace(".", "_dot_") + ".txt";
        File file = new File(filename);
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        contacts.add(line.trim());
                    }
                }
                System.out.println("Contacts loaded successfully from " + filename);
            } catch (IOException e) {
                System.out.println("Error loading contacts: " + e.getMessage());
            }
        } else {
            System.out.println("No previous contacts found. Will create new file when contacts are added.");
        }
    }

    private static void saveContacts() {
        String filename = CONTACTS_FILE_PREFIX + userEmail.replace("@", "_at_").replace(".", "_dot_") + ".txt";
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            for (String contact : contacts) {
                writer.println(contact);
            }
            System.out.println("Contacts saved to " + filename);
        } catch (IOException e) {
            System.out.println("Error saving contacts: " + e.getMessage());
        }
    }
}