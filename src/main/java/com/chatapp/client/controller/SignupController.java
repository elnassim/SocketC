package com.chatapp.client.controller;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;

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

    private static final String USERS_FILE_PATH = "/Users.json";
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    @FXML
    public void initialize() {
        Platform.runLater(() -> usernameField.requestFocus());
    }

    @FXML
    public void handleSignupButtonAction(ActionEvent event) {
        messageLabel.setText("");
        messageLabel.getStyleClass().clear();

        String username = usernameField.getText().trim();
        String email = emailField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        // Validate input
        if (username.isEmpty() || email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            showError("All fields are required.");
            return;
        }

        if (!EMAIL_PATTERN.matcher(email).matches()) {
            showError("Invalid email format.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            showError("Passwords do not match.");
            return;
        }

        if (password.length() < 6) {
            showError("Password must be at least 6 characters long.");
            return;
        }

        // Load existing users
        try {
            InputStream is = getClass().getResourceAsStream(USERS_FILE_PATH);
            if (is == null) {
                showError("Users file not found.");
                return;
            }

            BufferedReader reader = new BufferedReader(new InputStreamReader(is));
            StringBuilder jsonContent = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                jsonContent.append(line);
            }
            reader.close();

            JSONArray usersArray = new JSONArray(jsonContent.toString());

            // Check for duplicate username or email
            for (int i = 0; i < usersArray.length(); i++) {
                JSONObject user = usersArray.getJSONObject(i);
                if (user.getString("username").equals(username)) {
                    showError("Username already exists.");
                    return;
                }
                if (user.getString("email").equals(email)) {
                    showError("Email already exists.");
                    return;
                }
            }

            // Add new user
            JSONObject newUser = new JSONObject();
            newUser.put("username", username);
            newUser.put("email", email);
            newUser.put("password", password);
            usersArray.put(newUser);

            // Save updated users to file
            try (FileWriter writer = new FileWriter(getClass().getResource(USERS_FILE_PATH).getPath())) {
                writer.write(usersArray.toString());
                writer.flush();
            }

            showSuccess("Account created successfully! You can now log in.");
            resetForm();

        } catch (IOException e) {
            showError("Error reading or writing users file: " + e.getMessage());
        }
    }

    private void resetForm() {
        usernameField.clear();
        emailField.clear();
        passwordField.clear();
        confirmPasswordField.clear();
    }

    private void showError(String message) {
        messageLabel.setText(message);
        messageLabel.getStyleClass().add("error-message");
    }

    private void showSuccess(String message) {
        messageLabel.setText(message);
        messageLabel.getStyleClass().add("success-message");
    }

    @FXML
    public void handleBackToLoginButtonAction(ActionEvent event) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/chatapp/client/view/login-view.fxml"));
        Parent loginView = loader.load();

        Scene loginScene = new Scene(loginView);

        Stage stage = (Stage) backToLoginButton.getScene().getWindow();
        stage.setTitle("Chat Application - Login");
        stage.setScene(loginScene);
        stage.show();
    }
}