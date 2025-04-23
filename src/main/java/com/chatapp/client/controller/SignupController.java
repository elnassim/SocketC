package com.chatapp.client.controller;

import com.chatapp.client.network.ClientNetworkService;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.regex.Pattern;

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
        String username = usernameField.getText().trim();
        String email    = emailField.getText().trim();
        String pass1    = passwordField.getText();
        String pass2    = confirmPasswordField.getText();

        // --- Validation rapide côté client ---
        if (username.isEmpty() || email.isEmpty() || pass1.isEmpty() || pass2.isEmpty()) {
            showError("All fields are required");
            return;
        }
        if (!EMAIL_REGEX.matcher(email).matches()) {
            showError("Invalid email format");
            return;
        }
        if (pass1.length() < 6) {              // ▼ longueur minimale
            showError("Password must be at least 6 characters");
            return;
        }
        if (!pass1.equals(pass2)) {
            showError("Passwords do not match");
            return;
        }
        if (pass1.length() < 6) { showError("Password must be ≥ 6 characters"); return; }


        // --- Appel réseau dans un thread séparé ---
        messageLabel.setText("Registering…");
        messageLabel.getStyleClass().setAll("success-message");

        Thread t = new Thread(() -> {
            try {
                ClientNetworkService net = new ClientNetworkService();
                boolean ok = net.register(username, email, pass1);   // <<< nouvelle méthode côté client

                Platform.runLater(() -> {
                    if (ok) {
                        showSuccess("Account created! You can now log in.");
                        clearFields();
                    } else {
                        showError("Registration failed – email already used?");
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> showError("Error: " + ex.getMessage()));
            }
        });
        t.setDaemon(true);
        t.start();
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

    private void showSuccess(String msg) {
        messageLabel.setText(msg);
        messageLabel.getStyleClass().setAll("success-message");
    }

    private void clearFields() {
        usernameField.clear();
        emailField.clear();
        passwordField.clear();
        confirmPasswordField.clear();
    }
}
