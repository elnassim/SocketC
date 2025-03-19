package com.chatapp.client.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

import org.json.JSONObject;
import java.io.*;
import java.net.Socket;

import com.chatapp.client.network.ClientNetworkService;


public class LoginController {

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Button connectButton;

    @FXML
    private Button signupButton;

    @FXML
    private Label messageLabel;

    private static final String SERVER_HOST = "localhost";
    private static final int SERVER_PORT = 1234;

    @FXML
    public void initialize() {
        // Set focus on email field when the form loads
        Platform.runLater(() -> emailField.requestFocus());
    }

    @FXML
public void handleConnectButtonAction(ActionEvent event) {
    String email = emailField.getText().trim();
    String password = passwordField.getText();

    // Validate input
    if (email.isEmpty() || password.isEmpty()) {
        showError("Please enter both email and password");
        return;
    }

    messageLabel.setText("Connecting to server...");
    messageLabel.getStyleClass().clear();
    messageLabel.getStyleClass().add("success-message");

    System.out.println("Attempting to connect with: " + email);
    
    // Connect to server in a new thread
    Thread connectionThread = new Thread(() -> {
        try {
            System.out.println("Connection thread started");
            ClientNetworkService networkService = new ClientNetworkService();
            System.out.println("Connecting to server...");
            Socket socket = networkService.connect(email, password);
            
            System.out.println("Socket connected: " + (socket != null && socket.isConnected()));
            
            if (socket != null) {
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                
                System.out.println("Waiting for server response...");
                // Wait for server response
                String response = in.readLine();
                System.out.println("Server response: " + response);
                
                if ("AUTH_SUCCESS".equals(response)) {
                    System.out.println("Authentication successful!");
                    Platform.runLater(() -> {
                        try {
                            launchChatUI(email, socket, in, out);
                        } catch (IOException e) {
                            System.out.println("Error launching chat UI: " + e.getMessage());
                            showError("Error launching chat: " + e.getMessage());
                        }
                    });
                } else {
                    System.out.println("Authentication failed: " + response);
                    Platform.runLater(() -> {
                        showError("Authentication failed. Please check your credentials.");
                    });
                }
            }
        } catch (IOException e) {
            System.out.println("Connection error: " + e.getMessage());
            e.printStackTrace();
            Platform.runLater(() -> {
                showError("Connection error: " + e.getMessage());
            });
        }
    });
    
    
    connectionThread.setDaemon(true); // Make it a daemon thread
    connectionThread.start();
    System.out.println("Connection thread started");
}
    private void showError(String message) {
        messageLabel.setText(message);
        messageLabel.getStyleClass().clear();
        messageLabel.getStyleClass().add("error-message");
    }

    @FXML
    public void handleSignupButtonAction(ActionEvent event) {
        try {
            // Load the signup view
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/chatapp/client/view/signup-view.fxml"));
            Parent signupView = loader.load();

            // Create new scene with the signup view
            Scene signupScene = new Scene(signupView);

            // Get the current stage
            Stage stage = (Stage) signupButton.getScene().getWindow();

            // Set the new scene
            stage.setTitle("Chat Application - Sign Up");
            stage.setScene(signupScene);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
            messageLabel.setText("Error loading signup page");
            messageLabel.getStyleClass().clear();
            messageLabel.getStyleClass().add("error-message");
        }
    }

    private void launchChatUI(String email, Socket socket, BufferedReader in, PrintWriter out) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/chatapp/client/view/chat-view.fxml"));
        Parent chatView = loader.load();

        // Get controller and initialize it with connection details
        ChatController controller = loader.getController();
        controller.initChatSession(email, socket, in, out);

        // Create new scene
        Scene chatScene = new Scene(chatView, 600, 400);

        // Get current stage and set new scene
        Platform.runLater(() -> {
            Stage stage = (Stage) connectButton.getScene().getWindow();
            stage.setTitle("Chat Client - " + email);
            stage.setScene(chatScene);
            stage.setResizable(true);
            stage.show();
        });
    }
}
