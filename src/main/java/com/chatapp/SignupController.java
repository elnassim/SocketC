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

public class SignupController {

    @FXML
    private TextField usernameField;

    @FXML
    private PasswordField passwordField;

    @FXML
    private PasswordField confirmPasswordField;

    @FXML
    private TextField emailField;

    @FXML
    private Button signupButton;

    @FXML
    private Button backToLoginButton;

    @FXML
    private Label messageLabel;

    @FXML
    public void handleSignupButtonAction(ActionEvent event) {
        // Existing code...
    }

    @FXML
    public void handleBackToLoginButtonAction(ActionEvent event) throws IOException {
        backToLogin();
    }
    
    private void backToLogin() throws IOException {
        // Load the login view
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/login.fxml"));
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
