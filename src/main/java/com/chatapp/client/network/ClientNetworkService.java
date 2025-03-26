package com.chatapp.client.network;

import java.io.*;
import java.net.Socket;
import java.net.ConnectException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

/**
 * Handles network communication between the client and server
 */
public class ClientNetworkService {
    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 1234;
    private static final AtomicBoolean running = new AtomicBoolean(true);
    private static HashSet<String> contacts = new HashSet<>();
    private static String userEmail;
    private static final String CONTACTS_FILE_PREFIX = "contacts_";
    private MessageCache messageCache;
    private ScheduledExecutorService retryService;
    private boolean connected = false;
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long RETRY_INTERVAL_MS = 5000; // 5 seconds

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    /**
     * Connect to the server and authenticate
     * 
     * @param email    User email
     * @param password User password
     * @return Socket connection if successful, null otherwise
     * @throws IOException If a connection error occurs
     */
    public Socket connect(String email, String password) throws IOException {
        try {
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            userEmail = email;

            // Send authentication request
            JSONObject loginRequest = new JSONObject();
            loginRequest.put("email", email);
            loginRequest.put("password", password);
            String loginJson = loginRequest.toString();
            out.println(loginJson);

            return socket;
        } catch (ConnectException e) {
            throw new IOException("Failed to connect to server. Is the server running?");
        }
    }
    
    public void initRetryMechanism() {
        if (userEmail == null) {
            throw new IllegalStateException("User email not set. Connect first.");
        }
        
        messageCache = new MessageCache(userEmail);
        retryService = Executors.newSingleThreadScheduledExecutor();
        connected = true;
        
        // Schedule retry task
        retryService.scheduleAtFixedRate(() -> {
            if (connected && socket != null && !socket.isClosed()) {
                List<JSONObject> pendingMessages = messageCache.getPendingMessages();
                for (JSONObject message : pendingMessages) {
                    // Check if retry count exceeded
                    int retryCount = message.optInt("retry_count", 0);
                    if (retryCount < MAX_RETRY_ATTEMPTS) {
                        message.put("retry_count", retryCount + 1);
                        sendMessage(message.toString());
                    } else {
                        // Max retries reached, notify UI of failure
                        JSONObject failureNotification = new JSONObject();
                        failureNotification.put("type", "send_failure");
                        failureNotification.put("originalMessage", message);
                        // This will be handled by the message listener in ChatController
                        sendMessage(failureNotification.toString());
                        messageCache.removeDeliveredMessage(message.getString("id"));
                    }
                }
            }
        }, RETRY_INTERVAL_MS, RETRY_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * Send a message to the server
     * 
     * @param message The message to send
     */
    public void sendMessage(String message) {
        if (out != null) {
            out.println(message);
        }
    }

    /**
     * Close the connection to the server
     */
    public void disconnect() {
        connected = false;
        try {
            if (retryService != null) {
                retryService.shutdown();
            }
            
            if (socket != null && !socket.isClosed()) {
                JSONObject disconnectMsg = new JSONObject();
                disconnectMsg.put("type", "disconnect");
                out.println(disconnectMsg.toString());
                socket.close();
            }
        } catch (Exception e) {
            System.err.println("Error while disconnecting: " + e.getMessage());
        }
    }

    public void sendMessageWithRetry(JSONObject message) {
        // Add ID if not present
        if (!message.has("id")) {
            message.put("id", "msg_" + System.currentTimeMillis() + "_" + 
                      Integer.toHexString((int)(Math.random() * 10000)));
        }
        
        // Add message to cache
        messageCache.cacheMessage(message);
        
        // Send message
        sendMessage(message.toString());
    }
    
    // Add this to process delivery receipts
    public void processDeliveryReceipt(String messageId) {
        messageCache.removeDeliveredMessage(messageId);
    }
    
    // *** GROUP FEATURE ***
    public void createGroupOnServer(String groupName, List<String> members) {
        try {
            JSONObject groupRequest = new JSONObject();
            groupRequest.put("type", "create_group");
            groupRequest.put("groupName", groupName);
            JSONArray membersArray = new JSONArray();
            for (String m : members) {
                membersArray.put(m);
            }
            groupRequest.put("members", membersArray);
            groupRequest.put("sender", userEmail);
            sendMessage(groupRequest.toString());
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }
    
    // NEW: Method to retrieve all user emails (for group creation)
    public List<String> getAllUserEmails() {
        return new ArrayList<>(contacts);
    }

    // Helper methods for contacts management
    public static void loadContacts() {
        try (BufferedReader reader = new BufferedReader(new FileReader(CONTACTS_FILE_PREFIX + userEmail + ".txt"))) {
            String contact;
            while ((contact = reader.readLine()) != null) {
                contacts.add(contact);
            }
        } catch (IOException e) {
            System.err.println("Error loading contacts: " + e.getMessage());
        }
    }

    public static void saveContacts() {
        try (PrintWriter writer = new PrintWriter(new FileWriter(CONTACTS_FILE_PREFIX + userEmail + ".txt"))) {
            for (String contact : contacts) {
                writer.println(contact);
            }
        } catch (IOException e) {
            System.err.println("Error saving contacts: " + e.getMessage());
        }
    }
    
    /**
     * Main method for command line client usage
     */
    public static void main(String[] args) {
        ClientNetworkService client = new ClientNetworkService();

        try (BufferedReader consoleInput = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.println("Simple Chat Client");

            // Get user credentials
            System.out.print("Enter email: ");
            String email = consoleInput.readLine();
            System.out.print("Enter password: ");
            String password = consoleInput.readLine();

            // Connect to server
            Socket socket = client.connect(email, password);
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Get authentication response
            String authResponse = in.readLine();
            System.out.println("Server response: " + authResponse);

            if ("AUTH_SUCCESS".equals(authResponse)) {
                System.out.println("Authentication successful!");

                // Rest of client logic...
                // ...
            } else {
                System.out.println("Authentication failed. Exiting...");
            }
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        } finally {
            client.disconnect();
        }
    }
}
