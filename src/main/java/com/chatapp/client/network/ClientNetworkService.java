package com.chatapp.client.network;

import java.io.*;
import java.net.Socket;
import java.net.ConnectException;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicBoolean;
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
        try {
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
}