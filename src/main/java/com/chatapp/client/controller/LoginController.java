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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

import com.chatapp.client.network.ClientNetworkService;
import com.chatapp.client.network.UserStatusBroadcaster;

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

    @FXML
    public void handleConnectButtonAction(ActionEvent event) {
        String email = emailField.getText().trim();
        String password = passwordField.getText();

        // Validation des champs
        if (email.isEmpty() || password.isEmpty()) {
            showError("Please enter both email and password");
            return;
        }

        messageLabel.setText("Connecting to server...");
        messageLabel.getStyleClass().clear();
        messageLabel.getStyleClass().add("success-message");

        System.out.println("Attempting to connect with: " + email);

        // Connexion au serveur dans un thread séparé
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
                        Platform.runLater(() -> showError("Authentication failed. Please check your credentials."));
                    }
                }
            } catch (IOException e) {
                System.out.println("Connection error: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> showError("Connection error: " + e.getMessage()));
            }
        });
        
        connectionThread.setDaemon(true); // Thread daemon pour ne pas bloquer la fermeture de l'application
        connectionThread.start();
        System.out.println("Connection thread started");
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
    FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/chatapp/client/view/chat-view.fxml"));
    Parent chatView = loader.load();

    ChatController controller = loader.getController();
    controller.initChatSession(email, socket, in, out);

    // Start broadcasting "online" status
    UserStatusBroadcaster broadcaster = new UserStatusBroadcaster(email);
new Thread(broadcaster).start();

    Scene chatScene = new Scene(chatView, 600, 400);
    Platform.runLater(() -> {
        Stage stage = (Stage) connectButton.getScene().getWindow();
        stage.setTitle("Chat Client - " + email);
        stage.setScene(chatScene);
        stage.setResizable(true);
        stage.show();
    });
}

    private void showError(String message) {
        messageLabel.setText(message);
        messageLabel.getStyleClass().clear();
        messageLabel.getStyleClass().add("error-message");
    }
}
