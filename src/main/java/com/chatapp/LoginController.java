package com.chatapp;

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

public class LoginController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private Button loginButton;

    @FXML
    private Button signupButton;

    @FXML
    private Label messageLabel;

    @FXML
    public void handleLoginButtonAction(ActionEvent event) {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            messageLabel.setText("Please enter both username and password");
            return;
        }

        // Authenticate user
        if (UserRepository.authenticate(username, password)) {
            messageLabel.setText("Login successful!");
            messageLabel.getStyleClass().add("success-message");
            // Here you would connect to your chat server
        } else {
            messageLabel.setText("Invalid username or password");
            messageLabel.getStyleClass().add("error-message");
        }
    }

    @FXML
    public void handleSignupButtonAction(ActionEvent event) {
        try {
            // Load the signup view
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/signup.fxml"));
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
        }
    }
}