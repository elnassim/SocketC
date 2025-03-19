package com.chatapp.client.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.geometry.Insets;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.Socket;
import java.util.*;

import com.chatapp.common.model.User;
import com.chatapp.client.network.ClientNetworkService;

/**
 * Contrôleur principal de l'application de chat.
 * - Les messages envoyés par l'utilisateur local s'affichent à droite.
 * - Les messages reçus (d'autres utilisateurs) s'affichent à gauche.
 */
public class ChatController {

    /* ---------- FXML Nodes ---------- */
    @FXML
    private ScrollPane messageScrollPane;
    @FXML
    private VBox messageContainer;
    @FXML
    private TextField messageInput;
    @FXML
    private Button sendButton;
    @FXML
    private ListView<String> contactsList;
    @FXML
    private TextField searchField;
    @FXML
    private Button addContactButton;
    @FXML
    private Button showContactsButton;
    @FXML
    private Button deleteContactButton;
    @FXML
    private Label contactNameLabel;
    @FXML
    private Button logoutButton;

    /* ---------- Champs internes ---------- */
    private String userEmail; // e-mail de l'utilisateur local
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean connected = false;

    /** Ensemble de contacts enregistrés localement */
    private HashSet<String> contacts = new HashSet<>();

    /**
     * conversationMap : pour chaque contact (String), liste de messages échangés.
     */
    private Map<String, List<MessageData>> conversationMap = new HashMap<>();

    /**
     * Clé (contact) de la conversation actuellement affichée.
     */
    private String currentConversationKey = null;

    private static final String CONTACTS_FILE_PREFIX = "contacts_";

    /**
     * Classe interne représentant un message (sender, contenu, etc.).
     */
    private static class MessageData {
        String sender; // e-mail de l'expéditeur
        String content; // texte du message
        boolean isPrivate; // message privé ?
        boolean isOutgoing; // true si message émis par userEmail

        MessageData(String sender, String content, boolean isPrivate, boolean isOutgoing) {
            this.sender = sender;
            this.content = content;
            this.isPrivate = isPrivate;
            this.isOutgoing = isOutgoing;
        }
    }

    /* ---------- Initialisation FXML ---------- */
    @FXML
    public void initialize() {
        // Envoyer le message quand on appuie sur ENTER
        messageInput.setOnAction(event -> sendMessage());

        // Auto-scroll : descendre en bas quand un nouveau message arrive
        messageContainer.heightProperty().addListener((obs, oldVal, newVal) -> {
            messageScrollPane.setVvalue(1.0);
        });

        // Sélection d'un contact => on charge sa conversation
        contactsList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                contactNameLabel.setText(newVal);
                if (currentConversationKey == null || !currentConversationKey.equals(newVal)) {
                    loadConversation(newVal);
                }
            }
        });
    }

    /**
     * Appelée après la connexion : on récupère le userEmail et le socket.
     */
    public void initChatSession(String email, Socket socket, BufferedReader in, PrintWriter out) {
        this.userEmail = email;
        this.socket = socket;
        this.in = in;
        this.out = out;
        this.connected = true;

        // Charger les contacts depuis le fichier local
        loadContacts();
        refreshContactsList();

        addSystemMessage("Connected as " + userEmail);
        addSystemMessage("Your contacts: " + contacts);
        addSystemMessage("Commands:\n"
                + "/list - List your contacts\n"
                + "@email@example.com message - Send a private message");

        // Lancement du thread d'écoute des messages
        startMessageListener();
    }

    /**
     * Thread écoutant les messages entrants depuis le serveur.
     */
    private void startMessageListener() {
        new Thread(() -> {
            try {
                String line;
                while (connected && (line = in.readLine()) != null) {
                    final String receivedMsg = line;
                    System.out.println("Received: " + receivedMsg);

                    try {
                        JSONObject msgJson = new JSONObject(receivedMsg);
                        if (msgJson.has("type") && msgJson.has("content")) {
                            String type = msgJson.getString("type");
                            String content = msgJson.getString("content");
                            // Le serveur doit envoyer "sender" : l'e-mail de l'expéditeur
                            String sender = msgJson.optString("sender", "Server");

                            // S'il s'agit de mes propres messages (sender == userEmail), je les ignore
                            // pour ne pas les afficher en double. (Dépend de votre logique serveur.)
                            if (sender.equals(userEmail)) {
                                continue;
                            }

                            // Si c'est un message privé
                            if ("private".equals(type)) {
                                // On stocke la conversation sous la clé "sender" (c'est la personne qui nous
                                // écrit)
                                storeMessage(sender, sender, content, true, false);
                            } else {
                                // Sinon, c'est un broadcast => on le stocke sous la clé "All"
                                storeMessage("All", sender, content, false, false);
                            }
                        } else {
                            // JSON incomplet => on l'affiche brut
                            Platform.runLater(() -> addMessage("", receivedMsg));
                        }
                    } catch (JSONException e) {
                        // Pas du JSON => affichage brut
                        Platform.runLater(() -> addMessage("", receivedMsg));
                    }
                }
            } catch (IOException e) {
                if (connected) {
                    Platform.runLater(() -> addSystemMessage("Connection lost: " + e.getMessage()));
                }
            }
        }).start();
    }

    /* ---------- Envoi de message ---------- */
    @FXML
    public void handleSendButtonAction(ActionEvent event) {
        sendMessage();
    }

    private void sendMessage() {
        String messageText = messageInput.getText().trim();
        if (messageText.isEmpty() || !connected)
            return;

        try {
            // Commande /list
            if (messageText.startsWith("/list")) {
                Platform.runLater(() -> addSystemMessage("Your contacts: " + contacts));
            }
            // Message privé => @destinataire
            else if (messageText.startsWith("@")) {
                int spaceIndex = messageText.indexOf(" ");
                if (spaceIndex > 1) {
                    String recipient = messageText.substring(1, spaceIndex);
                    String content = messageText.substring(spaceIndex + 1);

                    if (contacts.contains(recipient)) {
                        JSONObject privateMsg = new JSONObject();
                        privateMsg.put("type", "private");
                        privateMsg.put("to", recipient);
                        privateMsg.put("content", content);
                        // IMPORTANT : on envoie "sender" = userEmail
                        privateMsg.put("sender", userEmail);
                        out.println(privateMsg.toString());

                        // On stocke en local le message comme "sortant" (isOutgoing = true)
                        storeMessage(recipient, userEmail, content, true, true);
                    } else {
                        Platform.runLater(() -> addSystemMessage("User not in contacts. Add them first."));
                    }
                } else {
                    Platform.runLater(() -> addSystemMessage("Usage: @email@example.com message"));
                }
            }
            // Sinon, broadcast
            else {
                JSONObject broadcastMsg = new JSONObject();
                broadcastMsg.put("type", "broadcast");
                broadcastMsg.put("content", messageText);
                broadcastMsg.put("sender", userEmail);
                out.println(broadcastMsg.toString());

                storeMessage("All", userEmail, messageText, false, true);
            }
        } catch (Exception e) {
            Platform.runLater(() -> addSystemMessage("Error sending message: " + e.getMessage()));
        }

        messageInput.clear();
        messageInput.requestFocus();
    }

    /**
     * Stocke le message dans la map, puis l'affiche si c'est la conversation en
     * cours.
     */
    private void storeMessage(String convKey, String sender, String content, boolean isPrivate, boolean isOutgoing) {
        conversationMap.putIfAbsent(convKey, new ArrayList<>());
        conversationMap.get(convKey).add(new MessageData(sender, content, isPrivate, isOutgoing));

        // Si la conversation affichée correspond, on ajoute la bulle
        if (convKey.equals(currentConversationKey)) {
            Platform.runLater(() -> {
                if (isOutgoing) {
                    // message sortant => bulle à droite
                    if (isPrivate) {
                        addOutgoingPrivateMessage(convKey, content);
                    } else {
                        addOutgoingMessage(content);
                    }
                } else {
                    // message entrant => bulle à gauche
                    if (isPrivate) {
                        addPrivateMessage(sender, content);
                    } else {
                        addMessage(sender, content);
                    }
                }
            });
        }
    }

    /**
     * Charge et affiche la conversation pour convKey (un contact ou "All").
     */
    private void loadConversation(String convKey) {
        if (convKey.equals(currentConversationKey)) {
            return; // On est déjà dessus
        }
        currentConversationKey = convKey;
        messageContainer.getChildren().clear();

        List<MessageData> messages = conversationMap.get(convKey);
        if (messages != null) {
            for (MessageData md : messages) {
                if (md.isOutgoing) {
                    // message émis par moi => bulle à droite
                    if (md.isPrivate) {
                        addOutgoingPrivateMessage(convKey, md.content);
                    } else {
                        addOutgoingMessage(md.content);
                    }
                } else {
                    // message reçu => bulle à gauche
                    if (md.isPrivate) {
                        addPrivateMessage(md.sender, md.content);
                    } else {
                        addMessage(md.sender, md.content);
                    }
                }
            }
        }
    }

    /* ---------- Méthodes d'affichage (bulles) ---------- */

    /**
     * Affiche un message système (infos).
     */
    private void addSystemMessage(String text) {
        HBox box = new HBox();
        box.setAlignment(Pos.CENTER);
        Label label = new Label(text);
        label.getStyleClass().add("system-message");
        box.getChildren().add(label);
        messageContainer.getChildren().add(box);
        scrollToBottom();
    }

    /**
     * Message reçu "public" => bulle à gauche.
     */
    private void addMessage(String sender, String text) {
        HBox box = new HBox();
        box.setAlignment(Pos.CENTER_LEFT);

        // ex: "sara@gmail.com: Hello"
        Label label = new Label(sender.isEmpty() ? text : sender + ": " + text);
        label.getStyleClass().add("bubble-left");

        box.getChildren().add(label);
        messageContainer.getChildren().add(box);
        scrollToBottom();
    }

    /**
     * Message privé reçu => bulle à gauche (couleur différente).
     */
    private void addPrivateMessage(String sender, String text) {
        HBox box = new HBox();
        box.setAlignment(Pos.CENTER_LEFT);

        Label label = new Label("[PRIVATE] " + sender + ": " + text);
        label.getStyleClass().add("bubble-left-private");

        box.getChildren().add(label);
        messageContainer.getChildren().add(box);
        scrollToBottom();
    }

    /**
     * Message envoyé (public) => bulle à droite.
     */
    private void addOutgoingMessage(String text) {
        HBox box = new HBox();
        box.setAlignment(Pos.CENTER_RIGHT);

        Label label = new Label("You: " + text);
        label.getStyleClass().add("bubble-right");

        box.getChildren().add(label);
        messageContainer.getChildren().add(box);
        scrollToBottom();
    }

    /**
     * Message privé envoyé => bulle à droite (autre couleur).
     */
    private void addOutgoingPrivateMessage(String recipient, String text) {
        HBox box = new HBox();
        box.setAlignment(Pos.CENTER_RIGHT);

        Label label = new Label("[PRIVATE to " + recipient + "] You: " + text);
        label.getStyleClass().add("bubble-right-private");

        box.getChildren().add(label);
        messageContainer.getChildren().add(box);
        scrollToBottom();
    }

    private void scrollToBottom() {
        Platform.runLater(() -> {
            messageScrollPane.layout();
            messageScrollPane.setVvalue(1.0);
        });
    }

    /* ---------- Gestion des contacts ---------- */
    @FXML
    private void handleAddContactButton(ActionEvent event) {
        Stage addContactStage = new Stage();
        addContactStage.setTitle("Add New Contact");

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));

        Label instructionLabel = new Label("Enter the email address of the contact:");
        TextField contactField = new TextField();
        contactField.setPromptText("Email address");

        Button okButton = new Button("OK");
        okButton.setOnAction(e -> {
            String newContact = contactField.getText().trim();
            if (!newContact.isEmpty()) {
                addContact(newContact);
            }
            addContactStage.close();
        });

        vbox.getChildren().addAll(instructionLabel, contactField, okButton);
        Scene scene = new Scene(vbox);
        addContactStage.setScene(scene);
        addContactStage.show();
    }

    private void addContact(String contact) {
        if (!contacts.contains(contact)) {
            contacts.add(contact);
            saveContacts();
            refreshContactsList();
            addSystemMessage(contact + " added to contacts.");
        } else {
            addSystemMessage(contact + " is already in your contacts.");
        }
    }

    @FXML
    private void handleDeleteContactButton(ActionEvent event) {
        if (contacts.isEmpty()) {
            addSystemMessage("No contacts to delete.");
            return;
        }
        Stage deleteStage = new Stage();
        deleteStage.setTitle("Delete Contact");

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));

        Label instructionLabel = new Label("Select the contact to delete:");
        ComboBox<String> contactComboBox = new ComboBox<>();
        List<String> sortedContacts = new ArrayList<>(contacts);
        Collections.sort(sortedContacts);
        contactComboBox.getItems().addAll(sortedContacts);
        contactComboBox.setPromptText("Select contact");

        Button okButton = new Button("OK");
        okButton.setOnAction(e -> {
            String selectedContact = contactComboBox.getSelectionModel().getSelectedItem();
            if (selectedContact != null && !selectedContact.isEmpty()) {
                contacts.remove(selectedContact);
                conversationMap.remove(selectedContact);
                saveContacts();
                refreshContactsList();
                addSystemMessage(selectedContact + " deleted from contacts.");
            }
            deleteStage.close();
        });

        vbox.getChildren().addAll(instructionLabel, contactComboBox, okButton);
        Scene scene = new Scene(vbox);
        deleteStage.setScene(scene);
        deleteStage.show();
    }

    @FXML
    private void handleShowContactsButton(ActionEvent event) {
        Stage showContactsStage = new Stage();
        showContactsStage.setTitle("Your Contacts");

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(10));

        Label label = new Label("Your contacts:");
        ListView<String> listView = new ListView<>();

        List<String> sortedList = new ArrayList<>(contacts);
        Collections.sort(sortedList);
        listView.getItems().setAll(sortedList);

        vbox.getChildren().addAll(label, listView);
        Scene scene = new Scene(vbox, 300, 400);
        showContactsStage.setScene(scene);
        showContactsStage.show();
    }

    /* ---------- Persistance des contacts ---------- */
    private void loadContacts() {
        String filename = CONTACTS_FILE_PREFIX + userEmail.replace("@", "_at_").replace(".", "_dot_") + ".txt";
        File file = new File(filename);
        if (file.exists()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        contacts.add(line.trim());
                    }
                }
                addSystemMessage("Contacts loaded successfully.");
            } catch (IOException e) {
                addSystemMessage("Error loading contacts: " + e.getMessage());
            }
        } else {
            addSystemMessage("No previous contacts found.");
        }
    }

    private void saveContacts() {
        String filename = CONTACTS_FILE_PREFIX + userEmail.replace("@", "_at_").replace(".", "_dot_") + ".txt";
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            for (String contact : contacts) {
                writer.println(contact);
            }
        } catch (IOException e) {
            addSystemMessage("Error saving contacts: " + e.getMessage());
        }
    }

    private void refreshContactsList() {
        contactsList.getItems().setAll(contacts);
    }
        @FXML
    public void handleLogoutButtonAction(ActionEvent event) throws IOException {
        // Close the socket connection
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }

        // Load the login view
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/chatapp/client/view/login-view.fxml"));
        Parent loginView = loader.load();

        // Create new scene with the login view
        Scene loginScene = new Scene(loginView);

        // Get the current stage
        Stage stage = (Stage) logoutButton.getScene().getWindow();

        // Set the new scene
        stage.setTitle("Chat Application - Login");
        stage.setScene(loginScene);
        stage.show();
    }
}