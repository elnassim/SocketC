package com.chatapp.client.controller;

import java.io.IOException;
import java.util.regex.Pattern;

import com.chatapp.client.network.ClientNetworkService;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class SignupController {

    @FXML private TextField usernameField;
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button registerButton;
    @FXML private Button backButton;
    @FXML private Label messageLabel;

    private static final Pattern EMAIL_REGEX =
    Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    /* ---------- Actions ---------- */

    @FXML
    private void handleRegister(ActionEvent event) {
        // Clear previous error messages
        messageLabel.setText("");
        
        // Get input values
        String username = usernameField.getText().trim();
        String email = emailField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        
        // Validate input
        if (username.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            showError("All fields are required");
            return;
        }
        
        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match");
            return;
        }
        
        // Email format validation
        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            showError("Invalid email format");
            return;
        }
        
        // Password strength validation
        if (password.length() < 6) {
            showError("Password must be at least 6 characters long");
            return;
        }
        
        try {
            ClientNetworkService networkService = new ClientNetworkService();
            boolean registered = networkService.register(username, email, password);
            
            if (registered) {
                showInfo("Registration successful! Please log in.");
                // Load login view
                FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/chatapp/client/view/login-view.fxml"));
                Parent loginView = loader.load();
                Scene loginScene = new Scene(loginView);
                Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
                stage.setScene(loginScene);
                stage.show();
            }
        } catch (IOException e) {
            // Show the specific error message from the server
            showError(e.getMessage());
            System.err.println("Registration failed: " + e.getMessage());
        } catch (Exception e) {
            showError("An unexpected error occurred. Please try again.");
            System.err.println("Unexpected error during registration: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @FXML
    private void handleBackToLogin(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/chatapp/client/view/login-view.fxml"));
            Parent loginView = loader.load();
            Stage stage = (Stage) backButton.getScene().getWindow();
            stage.setScene(new Scene(loginView));
            stage.setTitle("Chat Application – Login");
        } catch (IOException e) {
            showError("Cannot load login page: " + e.getMessage());
        }
    }

    /* ---------- Helpers ---------- */

    private void showError(String msg) {
        messageLabel.setText(msg);
        messageLabel.getStyleClass().setAll("error-message");
    }

    private void showInfo(String msg) {
        messageLabel.setText(msg);
        messageLabel.getStyleClass().setAll("info-message");
    }

    private void clearFields() {
        usernameField.clear();
        emailField.clear();
        passwordField.clear();
        confirmPasswordField.clear();
    }
}
