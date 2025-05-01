package com.chatapp.client.network;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.chatapp.security.MessageEncryption;

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
    private static ClientNetworkService instance;
    private MessageEncryption encryption;
    private Map<String, String> userPublicKeys; // Store other users' public keys

    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    

    public ClientNetworkService() {
        userPublicKeys = new ConcurrentHashMap<>();
        try {
            encryption = new MessageEncryption();
        } catch (Exception e) {
            System.err.println("Failed to initialize encryption: " + e.getMessage());
        }
    }

    /**
     * Connect to the server and authenticate
     * 
     * @param email    User email
     * @param password User password
     * @return Socket connection if successful, null otherwise
     * @throws IOException If a connection error occurs
     */
    public static ClientNetworkService connect(String email, String password) throws IOException {
        ClientNetworkService client = getInstance();
        boolean connected = client.connectToServer(email, password);
        if (!connected) {
            throw new IOException("Authentication failed");
        }
        return client;
    }

    private boolean connectToServer(String email, String password) throws IOException {
        userEmail = email;
        try {
            System.out.println("Attempting to connect to server at " + SERVER_HOST + ":" + SERVER_PORT);
            socket = new Socket(SERVER_HOST, SERVER_PORT);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            // Send authentication request with public key
            JSONObject authRequest = new JSONObject();
            authRequest.put("type", "auth");
            authRequest.put("email", email);
            authRequest.put("password", password);
            authRequest.put("publicKey", encryption.getPublicKeyString());
            
            System.out.println("Sending authentication request...");
            out.println(authRequest.toString());
            
            String response = in.readLine();
            System.out.println("Received server response: " + response);
            
            if (response == null) {
                throw new IOException("No response from server");
            }
            
            JSONObject jsonResponse = new JSONObject(response);
            
            if (jsonResponse.getBoolean("success")) {
                System.out.println("Authentication successful--");
                // Initialize retry mechanism for failed messages
                initRetryMechanism();
                System.out.println("Authentication successful--2");
                return true;
            } else {
                String errorMessage = jsonResponse.optString("message", "Authentication failed");
                System.err.println("Authentication failed: " + errorMessage);
                throw new IOException(errorMessage);
            }
        } catch (JSONException e) {
            System.err.println("Invalid server response format: " + e.getMessage());
            throw new IOException("Server returned invalid response: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Connection error: " + e.getMessage());
            throw new IOException("Failed to connect: " + e.getMessage());
        }
    }

    public static ClientNetworkService getInstance() {
        if (instance == null) {
            instance = new ClientNetworkService();
        }
        return instance;
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
    public boolean register(String username, String email, String password) throws IOException {
        Socket regSocket = null;
        try {
            System.out.println("Attempting to register user: " + email);
            regSocket = new Socket(SERVER_HOST, SERVER_PORT);
            PrintWriter regOut = new PrintWriter(regSocket.getOutputStream(), true);
            BufferedReader regIn = new BufferedReader(new InputStreamReader(regSocket.getInputStream()));

            // Create registration request
            JSONObject req = new JSONObject();
            req.put("type", "REGISTER");  // Changed to uppercase to match server expectation
            req.put("username", username);
            req.put("email", email);
            req.put("password", password);
            
            System.out.println("Sending registration request...");
            regOut.println(req.toString());
            
            // Read and parse response
            String response = regIn.readLine();
            System.out.println("Received registration response: " + response);
            
            if (response == null) {
                throw new IOException("No response received from server");
            }
            
            JSONObject jsonResponse = new JSONObject(response);
            boolean success = jsonResponse.getBoolean("success");
            
            if (!success) {
                String errorMessage = jsonResponse.optString("message", "Registration failed");
                System.err.println("Registration failed: " + errorMessage);
                throw new IOException(errorMessage);
            }
            
            return success;
            
        } catch (JSONException e) {
            System.err.println("Invalid server response format: " + e.getMessage());
            throw new IOException("Server returned invalid response: " + e.getMessage());
        } catch (IOException e) {
            System.err.println("Registration error: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            System.err.println("Unexpected error during registration: " + e.getMessage());
            throw new IOException("Registration failed: " + e.getMessage());
        } finally {
            if (regSocket != null && !regSocket.isClosed()) {
                try {
                    regSocket.close();
                } catch (IOException e) {
                    System.err.println("Error closing registration socket: " + e.getMessage());
                }
            }
        }
    }
    

    /**
     * Send a message to the server
     * 
     * @param message The message to send
     */
    public void sendMessage(String message) {
        try {
            JSONObject jsonMessage = new JSONObject(message);
            if (jsonMessage.getString("type").equals("private")) {
                String recipient = jsonMessage.getString("to");
                String content = jsonMessage.getString("content");
                
                String recipientPublicKey = userPublicKeys.get(recipient);
                if (recipientPublicKey != null) {
                    encryption.setOtherPartyPublicKey(recipientPublicKey);
                    String encryptedContent = encryption.encryptMessage(content);
                    jsonMessage.put("content", encryptedContent);
                    jsonMessage.put("encrypted", true);
                }
            }
            out.println(jsonMessage.toString());
        } catch (Exception e) {
            System.err.println("Error sending encrypted message: " + e.getMessage());
        }
    }
    public boolean updateProfile(String displayName, String photoURL, String status) {
        try {
            // Construction de la chaîne de requête avec le séparateur '|'
            String request = "UPDATE_PROFILE|" + displayName + "|" + photoURL + "|" + status;
            // Envoi de la requête au serveur
            out.println(request);
            out.flush();

            // Lecture de la réponse du serveur (par exemple, "OK" ou "KO")
            String response = in.readLine();
            return "OK".equals(response);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
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
    
    // Add method to store other user's public key
    public void storeUserPublicKey(String userEmail, String publicKey) {
        userPublicKeys.put(userEmail, publicKey);
        try {
            encryption.setOtherPartyPublicKey(publicKey);
        } catch (Exception e) {
            System.err.println("Failed to store public key for " + userEmail + ": " + e.getMessage());
        }
    }

    // Add method to decrypt received message
    public String decryptMessage(String encryptedContent) {
        try {
            return encryption.decryptMessage(encryptedContent);
        } catch (Exception e) {
            System.err.println("Failed to decrypt message: " + e.getMessage());
            return "[Encrypted Message - Decryption Failed]";
        }
    }
    
    /**
     * Main method for command line client usage
     */
    public static void main(String[] args) {
        try (BufferedReader consoleInput = new BufferedReader(new InputStreamReader(System.in))) {
            System.out.println("Simple Chat Client");

            // Get user credentials
            System.out.print("Enter email: ");
            String email = consoleInput.readLine();
            System.out.print("Enter password: ");
            String password = consoleInput.readLine();

            // Connect to server
            ClientNetworkService client = ClientNetworkService.connect(email, password);

            // Get authentication response
            String authResponse = client.getIn().readLine();
            System.out.println("Server response: " + authResponse);

            if ("AUTH_SUCCESS".equals(authResponse)) {
                System.out.println("Authentication successful!!!!");
            } else {
                System.out.println("Authentication failed. Exiting...");
            }
        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
        }
    }

    public Socket getSocket() {
        return socket;
    }

    public BufferedReader getIn() {
        return in;
    }

    public PrintWriter getOut() {
        return out;
    }
}
