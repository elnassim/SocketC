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

import java.io.IOException;
import java.util.regex.Pattern;

import com.chatapp.common.model.User;

/**
 * Controller for the signup view
 */
public class SignupController {

    @FXML
    private TextField usernameField;

    @FXML
    private TextField emailField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private Button signupButton;

    @FXML
    private Button backToLoginButton;

    @FXML
    private Label messageLabel;

    // Email validation regex pattern
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    @FXML
    public void initialize() {
        // Set focus on username field when the form loads
        Platform.runLater(() -> usernameField.requestFocus());
    }

    @FXML
    public void handleSignupButtonAction(ActionEvent event) {
        // Clear previous messages
        messageLabel.setText("");
        messageLabel.getStyleClass().clear();
        messageLabel.getStyleClass().add("message-label");

        // Get form data
        String username = usernameField.getText().trim();
        String email = emailField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        // Validate form data
        if (username.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            showError("All fields are required");
            return;
        }

        // Validate email format
        if (!EMAIL_PATTERN.matcher(email).matches()) {
            showError("Please enter a valid email address");
            return;
        }

        // Check password match
        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match");
            return;
        }

        // Check password strength
        if (password.length() < 6) {
            showError("Password must be at least 6 characters long");
            return;
        }

        // Create user object
        User newUser = new User(username, password, email);

        // TODO: Send registration request to server
        // For now, show success message
        showSuccess("Account created successfully! You can now login.");

        // Reset form
        resetForm();
    }

    private void resetForm() {
        usernameField.clear();
        emailField.clear();
        passwordField.clear();
        confirmPasswordField.clear();
    }

    private void showError(String message) {
        messageLabel.setText(message);
        messageLabel.getStyleClass().clear();
        messageLabel.getStyleClass().add("error-message");
    }

    private void showSuccess(String message) {
        messageLabel.setText(message);
        messageLabel.getStyleClass().clear();
        messageLabel.getStyleClass().add("success-message");
    }

    @FXML
    public void handleBackToLoginButtonAction(ActionEvent event) throws IOException {
        // Load the login view
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/chatapp/client/view/login-view.fxml"));
        Parent loginView = loader.load();

        // Create new scene with the login view
        Scene loginScene = new Scene(loginView);

        // Get the current stage
        Stage stage = (Stage) backToLoginButton.getScene().getWindow();

        // Set the new scene
        stage.setTitle("Chat Application - Login");
        stage.setScene(loginScene);
        stage.show();
    }
}
