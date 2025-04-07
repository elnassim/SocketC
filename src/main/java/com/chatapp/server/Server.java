package com.chatapp.server;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.chatapp.data.service.DatabaseStartupService;
import com.chatapp.server.handler.ClientHandler;
import com.chatapp.server.service.UserService;

public class Server {
    private static final int PORT = 1234;
    private static final List<ClientHandler> clients = Collections.synchronizedList(new ArrayList<>());
    private static UserService userService;
    private static DatabaseStartupService dbService;

    public static void main(String[] args) {
        // Initialize database
        dbService = new DatabaseStartupService();
        if (!dbService.initialize()) {
            System.err.println("Failed to initialize database. Exiting server.");
            System.exit(1);
        }

        userService = new UserService();

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server started on port " + PORT + ". Waiting for clients...");

            // Shutdown hook for clean database shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                System.out.println("Server shutting down...");
                if (dbService != null) {
                    dbService.shutdown();
                }
            }));

            // Continuously accept new clients
            while (true) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("New client connected: " + clientSocket.getInetAddress());

                    // Create a new thread for the client
                    ClientHandler clientHandler = new ClientHandler(clientSocket, clients);
                    clients.add(clientHandler);
                    new Thread(clientHandler).start();
                } catch (IOException e) {
                    System.err.println("Error handling client connection: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Final cleanup
            if (dbService != null) {
                dbService.shutdown();
            }
        }
    }

    // Broadcast a message to all clients
    public static void broadcast(String message, ClientHandler sender) {
        System.out.println("Broadcasting: " + message);
        synchronized (clients) {
            for (ClientHandler client : clients) {
                if (client != sender) { // Don't send back to sender
                    client.sendMessage(message);
                }
            }
        }
    }

    // Remove a client from the list
    public static void removeClient(ClientHandler client) {
        clients.remove(client);
        System.out.println("Client disconnected. Total clients: " + clients.size());
    }
}
