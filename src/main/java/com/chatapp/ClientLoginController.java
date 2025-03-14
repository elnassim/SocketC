package com.chatapp;

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

/**
 * Controller for the client login interface
 */
public class ClientLoginController {

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Button connectButton;

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
        String email = emailField.getText();
        String password = passwordField.getText();

        // Validate input
        if (email.isEmpty() || password.isEmpty()) {
            messageLabel.setText("Please enter both email and password");
            messageLabel.getStyleClass().clear();
            messageLabel.getStyleClass().add("error-message");
            return;
        }

        // Basic email validation
        if (!email.contains("@") || !email.contains(".")) {
            messageLabel.setText("Please enter a valid email");
            messageLabel.getStyleClass().clear();
            messageLabel.getStyleClass().add("error-message");
            return;
        }

        messageLabel.setText("Connecting to server...");
        messageLabel.getStyleClass().clear();
        messageLabel.getStyleClass().add("success-message");
        
        // Connect to server in a new thread
        new Thread(() -> {
            try {
                Socket socket = new Socket(SERVER_HOST, SERVER_PORT);
                PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                
                // Send JSON authentication credentials
                JSONObject loginRequest = new JSONObject();
                loginRequest.put("email", email);
                loginRequest.put("password", password);
                out.println(loginRequest.toString());
                
                // Get authentication response
                String response = in.readLine();
                
                if ("AUTH_SUCCESS".equals(response)) {
                    // Extract username from email (part before @)
                    String username = email.substring(0, email.indexOf("@"));
                    
                    // Update UI on JavaFX thread
                    Platform.runLater(() -> {
                        messageLabel.setText("Authentication successful!");
                        messageLabel.getStyleClass().clear();
                        messageLabel.getStyleClass().add("success-message");
                        
                        try {
                            launchChatUI(email, socket, in, out);
                        } catch (Exception e) {
                            messageLabel.setText("Error launching chat: " + e.getMessage());
                            messageLabel.getStyleClass().clear();
                            messageLabel.getStyleClass().add("error-message");
                        }
                    });
                } else {
                    // Show failure message on JavaFX thread
                    Platform.runLater(() -> {
                        messageLabel.setText("Authentication failed: " + response);
                        messageLabel.getStyleClass().clear();
                        messageLabel.getStyleClass().add("error-message");
                    });
                    socket.close();
                }
            } catch (Exception e) {
                Platform.runLater(() -> {
                    messageLabel.setText("Connection error: " + e.getMessage());
                    messageLabel.getStyleClass().clear();
                    messageLabel.getStyleClass().add("error-message");
                });
            }
        }).start();
    }
    
    private void launchChatUI(String email, Socket socket, BufferedReader in, PrintWriter out) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/chatapp/chatClient.fxml"));
        Parent chatView = loader.load();
        
        // Get controller and initialize it with connection details
        ChatClientController controller = loader.getController();
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