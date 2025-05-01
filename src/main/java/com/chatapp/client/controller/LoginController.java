package com.chatapp.client.controller;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URL;
import java.util.regex.Pattern;

import org.json.JSONException;

import com.chatapp.client.network.ClientNetworkService;

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
        // Met le focus sur le champ email lors du chargement du formulaire
        Platform.runLater(() -> emailField.requestFocus());
    }
    private static final Pattern EMAIL_REGEX =
    Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    @FXML
    public void handleConnectButtonAction(ActionEvent event) {
        String email = emailField.getText().trim();
        String password = passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            showError("Please enter both email and password");
            return;
        }
        if (!EMAIL_REGEX.matcher(email).matches()) {
            showError("Invalid email format");
            return;
        }

        showInfo("Connecting to server…");
        System.out.println("Starting login process for email: " + email);

        Thread connectionThread = new Thread(() -> {
            try {
                System.out.println("Connection thread started");
                ClientNetworkService networkService = ClientNetworkService.connect(email, password);
                System.out.println("Connected successfully~");

                // Get socket and streams from network service
                Socket socket = networkService.getSocket();
                BufferedReader in = networkService.getIn();
                PrintWriter out = networkService.getOut();

                // Authentication is already successful at this point, proceed to launch chat UI
                System.out.println("Authentication successful! Preparing to launch UI...");
                Platform.runLater(() -> {
                    try {
                        System.out.println("Inside Platform.runLater, launching chat UI...");
                        launchChatUI(email, socket, in, out);
                    } catch (IOException e) {
                        System.err.println("Error launching chat UI: " + e.getMessage());
                        e.printStackTrace();
                        showError("Error launching chat: " + e.getMessage());
                    } catch (Exception e) {
                        System.err.println("Unexpected error launching chat UI: " + e.getMessage());
                        e.printStackTrace();
                        showError("Unexpected error: " + e.getMessage());
                    }
                });
            } catch (IOException e) {
                System.err.println("Connection error: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> showError("Connection error: " + e.getMessage()));
            } catch (JSONException e) {
                System.err.println("Invalid server response: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> showError("Server error: Invalid response format"));
            } catch (Exception e) {
                System.err.println("Unexpected error: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> showError("Unexpected error: " + e.getMessage()));
            }
        });
        
        connectionThread.setDaemon(true);
        connectionThread.start();
    }
 
    @FXML
    public void handleSignupButtonAction(ActionEvent event) {
        try {
            // Charge la vue d'inscription
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/chatapp/client/view/signup-view.fxml"));
            Parent signupView = loader.load();

            // Crée une nouvelle scène avec la vue d'inscription
            Scene signupScene = new Scene(signupView);

            // Récupère la fenêtre actuelle et affecte la nouvelle scène
            Stage stage = (Stage) signupButton.getScene().getWindow();
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
   /* ---------- Helpers ---------- */
private void showInfo(String msg) {
    messageLabel.setText(msg);
    messageLabel.getStyleClass().setAll("message-label");
}

    /**
     * Transition vers la vue de chat après authentification réussie.
     *
     * @param email l'email de l'utilisateur
     * @param socket le socket connecté
     * @param in le flux d'entrée
     * @param out le flux de sortie
     * @throws IOException en cas d'erreur lors du chargement de la vue
     */
    private void launchChatUI(String email, Socket socket, BufferedReader in, PrintWriter out) throws IOException {
        System.out.println("Starting to launch chat UI...");
        
        try {
            // First verify the FXML file exists
            URL fxmlUrl = getClass().getResource("/com/chatapp/client/view/chat-view.fxml");
            if (fxmlUrl == null) {
                throw new IOException("Cannot find chat-view.fxml");
            }
            System.out.println("Found FXML at: " + fxmlUrl);
            
            // Create all UI components on the JavaFX Application Thread
            Platform.runLater(() -> {
                try {
                    FXMLLoader loader = new FXMLLoader(fxmlUrl);
                    Parent chatView = loader.load();
                    System.out.println("Chat view loaded");

                    ChatController controller = loader.getController();
                    if (controller == null) {
                        throw new IOException("Failed to get ChatController instance");
                    }
                    System.out.println("Chat controller obtained");

                    Scene chatScene = new Scene(chatView, 800, 600);
                    System.out.println("Chat scene created");

                    Stage stage = (Stage) connectButton.getScene().getWindow();
                    stage.setTitle("Chat Client - " + email);
                    stage.setScene(chatScene);
                    stage.setResizable(true);
                    
                    // Initialize chat session before showing the window
                    controller.initChatSession(email, socket, in, out);
                    System.out.println("Chat session initialized");
                    
                    stage.show();
                    System.out.println("Chat window shown successfully");
                    
                } catch (IOException e) {
                    System.err.println("Error loading chat UI: " + e.getMessage());
                    e.printStackTrace();
                    showError("Error loading chat interface: " + e.getMessage());
                } catch (RuntimeException e) {
                    System.err.println("Error initializing chat session: " + e.getMessage());
                    e.printStackTrace();
                    showError("Error initializing chat: " + e.getMessage());
                } catch (Exception e) {
                    System.err.println("Unexpected error: " + e.getMessage());
                    e.printStackTrace();
                    showError("Unexpected error: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            System.err.println("Error in launchChatUI: " + e.getMessage());
            e.printStackTrace();
            throw new IOException("Failed to launch chat UI: " + e.getMessage());
        }
    }

    private void showError(String message) {
        Platform.runLater(() -> {
            messageLabel.setText(message);
            messageLabel.getStyleClass().clear();
            messageLabel.getStyleClass().add("error-message");
        });
    }
}
