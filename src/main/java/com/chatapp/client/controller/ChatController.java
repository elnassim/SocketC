package com.chatapp.client.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.*;

import com.chatapp.common.model.User;
import com.chatapp.data.dao.ContactDAO;
import com.chatapp.data.dao.impl.ContactDAOImpl;
import com.chatapp.client.network.ClientNetworkService;
import com.chatapp.client.network.UserStatusListener;
// Import du contrôleur de profil pour accéder à la méthode initData()
import com.chatapp.client.controller.ProfileController;

/**
 * Main controller for the chat application with conversation tabs.
 */
public class ChatController {

    /* ---------- FXML Nodes ---------- */
    @FXML private ScrollPane messageScrollPane;
    @FXML private VBox messageContainer;
    @FXML private TextField messageInput;
    @FXML private Button sendButton;
    @FXML private ListView<HBox> contactsList;
    @FXML private TextField searchField;
    @FXML private Button addContactButton;
    @FXML private Button showContactsButton;
    @FXML private Button deleteContactButton;
    @FXML private Label contactNameLabel;
    @FXML private TabPane conversationTabPane;
    @FXML private Button logoutButton;
    @FXML private Button createGroupButton;
    // Bouton Profil tel que défini dans chat-view.fxml
    @FXML private Button profileButton;
    @FXML private Button sendFileButton;

    /* ---------- Internal Fields ---------- */
    private String userEmail;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean connected = false;
    private HashSet<String> contacts = new HashSet<>();
    // conversationMap keys: for one-to-one, use sender email; for groups, use group name
    private Map<String, List<MessageData>> conversationMap = new HashMap<>();
    private Map<String, String> pendingDownloads = new HashMap<>();
    private ClientNetworkService networkService;
    private static final String CONTACTS_FILE_PREFIX = "contacts_";
    
    // Gestion des onglets
    private Map<String, Tab> contactTabs = new HashMap<>();
    private Map<String, VBox> contactMessageContainers = new HashMap<>();

    private static class MessageData {
        String sender;
        String content;
        boolean isPrivate;
        boolean isOutgoing;
        MessageData(String sender, String content, boolean isPrivate, boolean isOutgoing) {
            this.sender = sender;
            this.content = content;
            this.isPrivate = isPrivate;
            this.isOutgoing = isOutgoing;
        }
    }

    @FXML
    public void initialize() {
        messageInput.setOnAction(event -> sendMessage());
        sendFileButton.setOnAction(event -> handleSendFileAction());
        
        // When a contact is clicked, open its conversation tab
        contactsList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                Label contactNameLabel = (Label) newVal.getChildren().get(0); // Récupère le Label contenant l'email
                String email = contactNameLabel.getText();
                handleContactClick(email); // Passe l'email à la méthode
            }
        });

        conversationTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null) {
                newTab.setStyle("");
            }
        });
        Platform.runLater(this::loadGroups);
    
        // Handle window close event to cleanly close socket connection
        Platform.runLater(() -> {
            Stage stage = (Stage) messageInput.getScene().getWindow();
            stage.setOnCloseRequest(event -> {
                connected = false;
                try {
                    if (socket != null && !socket.isClosed()) {
                        socket.close();
                    }
                } catch (IOException e) {
                    System.err.println("Error closing socket: " + e.getMessage());
                }
            });
        });
    }
    
    /**
     * Nouvelle méthode pour ouvrir la vue de gestion du profil.
     * Elle charge le fichier FXML, récupère le contrôleur, transmet l'email de l'utilisateur et affiche la fenêtre.
     */
    @FXML
    private void handleProfile(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/chatapp/client/view/profile-view.fxml"));
            Parent profileRoot = loader.load();
            // Récupère le contrôleur associé et initialise les données du profil.
            ProfileController profileController = loader.getController();
            profileController.initData(userEmail);
            Scene profileScene = new Scene(profileRoot);
            Stage profileStage = new Stage();
            profileStage.setTitle("Gestion du profil");
            profileStage.setScene(profileScene);
            profileStage.show();
        } catch (IOException e) {
            e.printStackTrace();
            addSystemMessage("Erreur lors de l'ouverture de la vue de profil.");
        }
    }

    /**
     * Initialise la session de chat et configure la connexion.
     */
    public void initChatSession(String email, Socket socket, BufferedReader in, PrintWriter out) {
        this.userEmail = email;
        this.socket = socket;
        this.in = in;
        this.out = out;
        this.connected = true;
    
        // Start the UserStatusListener
        try {
            UserStatusListener listener = new UserStatusListener(status -> {
                Platform.runLater(() -> updateContactStatus(status.getEmail(), status.getStatus()));
            });
            new Thread(listener).start();
        } catch (IOException e) {
            addSystemMessage("Error starting UserStatusListener: " + e.getMessage());
        }
    
        // Load contacts first (this has to work before loadGroups)
        loadContacts();
        
        // Add this explicit call to load groups after the socket is initialized
        System.out.println("Loading groups for user: " + email);
        loadGroups();
        
        // Refresh UI
        refreshContactsList();
        addSystemMessage("Connected as " + userEmail);
        startMessageListener();
    }
   

    private void updateContactStatus(String email, String status) {
        contactsList.getItems().removeIf(item -> {
            Label label = (Label) item.getChildren().get(0);
            return label.getText().equals(email);
        });
    
        HBox contactItem = new HBox();
        contactItem.setSpacing(10);
    
        // Add contact name
        Label contactName = new Label(email);
        contactName.setStyle("-fx-font-weight: bold;");
    
        // Add status indicator
        Label statusLabel = new Label();
        if ("online".equals(status)) {
            statusLabel.setText("●");
            statusLabel.setStyle("-fx-text-fill: green; -fx-font-size: 12px;");
        } else {
            statusLabel.setText("●");
            statusLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 12px;");
        }
    
        contactItem.getChildren().addAll(contactName, statusLabel);
        contactsList.getItems().add(contactItem);
    }
    private void startMessageListener() {
        new Thread(() -> {
            try {
                // Load groups again after a short delay to ensure connection is ready
                Thread.sleep(1000);
                Platform.runLater(() -> {
                    try {
                        System.out.println("Requesting groups list after connection startup");
                        JSONObject request = new JSONObject();
                        request.put("type", "get_groups");
                        out.println(request.toString());
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                });
                
                String line;
                while (connected && (line = in.readLine()) != null) {
                    final String receivedMsg = line;
                    Platform.runLater(() -> handleIncomingMessage(receivedMsg));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                if (connected) {
                    Platform.runLater(() -> addSystemMessage("Connection lost: " + e.getMessage()));
                }
            }
        }).start();
    }
    
    private void requestConversationHistory(String contactEmail) {
        try {
            System.out.println("Requesting history for: " + contactEmail);
            JSONObject request = new JSONObject();
            request.put("type", "GET_HISTORY");
            request.put("otherUser", contactEmail);
            out.println(request.toString());
        } catch (JSONException e) {
            addSystemMessage("Error requesting chat history: " + e.getMessage());
        }
    }

    // For one-to-one messages (incoming)
    private void handlePrivateMessage(String sender, String content) {
        storeMessage(sender, sender, content, true, false);
        Platform.runLater(() -> {
            if (!contacts.contains(sender)) {
                contacts.add(sender);
                saveContacts();
                refreshContactsList();
            }
            if (!contactTabs.containsKey(sender)) {
                Tab tab = createContactTab(sender);
                if (!conversationTabPane.getTabs().contains(tab)) {
                    conversationTabPane.getTabs().add(tab);
                }
            }
            addIncomingMessageToContainer(contactMessageContainers.get(sender), sender, content);
            flashContactTab(sender);
        });
    }

    // For group messages, use the group name as the conversation key
    private void handleGroupMessage(String groupName, String sender, String content) {
        storeMessage(groupName, sender, content, true, false);
        Platform.runLater(() -> {
            if (!contactTabs.containsKey(groupName)) {
                Tab tab = createContactTab(groupName);
                if (!conversationTabPane.getTabs().contains(tab)) {
                    conversationTabPane.getTabs().add(tab);
                }
            }
            addIncomingMessageToContainer(contactMessageContainers.get(groupName), sender, content);
            flashContactTab(groupName);
        });
    }

    public void loadGroups() {
        try {
            System.out.println("Requesting groups from server...");
            JSONObject request = new JSONObject();
            request.put("type", "get_groups");
            out.println(request.toString()); 
        } catch (JSONException e) {
            System.err.println("Error requesting groups: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleSendFileAction() {
    // Check if a conversation is active
    if (conversationTabPane.getSelectionModel().isEmpty()) {
        addSystemMessage("Select a contact first");
        return;
    }
    
    String recipient = conversationTabPane.getSelectionModel().getSelectedItem().getText();
    
    // Create file chooser
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Select File to Send");
    
    // Set file filters
    FileChooser.ExtensionFilter allFilter = new FileChooser.ExtensionFilter("All Files", "*.*");
    FileChooser.ExtensionFilter imageFilter = new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif");
    FileChooser.ExtensionFilter docFilter = new FileChooser.ExtensionFilter("Documents", "*.pdf", "*.doc", "*.docx", "*.txt");
    
    fileChooser.getExtensionFilters().addAll(imageFilter, docFilter, allFilter);
    
    // Show dialog
    File selectedFile = fileChooser.showOpenDialog(sendFileButton.getScene().getWindow());
    if (selectedFile == null) {
        return;
    }
    
    // Check file size (limit to 10MB for example)
    if (selectedFile.length() > 10 * 1024 * 1024) {
        addSystemMessage("File is too large. Maximum size is 10MB.");
        return;
    }
    
    // Send file
    sendFile(recipient, selectedFile);
}

private void sendFile(String recipient, File file) {
    try {
        // Read file data
        byte[] fileData = Files.readAllBytes(file.toPath());
        
        // Determine mime type
        String mimeType = Files.probeContentType(file.toPath());
        if (mimeType == null) {
            mimeType = "application/octet-stream";
        }
        
        // Encode file data as Base64
        String base64Data = java.util.Base64.getEncoder().encodeToString(fileData);
        
        // Create file upload message
        JSONObject fileUpload = new JSONObject();
        fileUpload.put("type", "file_upload");
        fileUpload.put("to", recipient);
        fileUpload.put("sender", userEmail);
        fileUpload.put("filename", file.getName());
        fileUpload.put("mimeType", mimeType);
        fileUpload.put("data", base64Data);
        
        // Send message
        out.println(fileUpload.toString());
        
        // Add visual feedback
        VBox container = contactMessageContainers.get(recipient);
        if (container != null) {
            // Store the final mimeType in a final variable to use in the lambda
            final String finalMimeType = mimeType;
            String sizeDisplay = formatFileSize(file.length());
            // Generate temporary ID for the file until server assigns a real one
            String tempFileId = "temp_" + System.currentTimeMillis();
            Platform.runLater(() -> {
                addFileMessageToContainer(container, userEmail, tempFileId, file.getName(), 
                                         finalMimeType, sizeDisplay, true);
            });
        }
        
        // Add message to UI
        addSystemMessage("Sending file: " + file.getName() + " to " + recipient);
    } catch (IOException | JSONException e) {
        addSystemMessage("Error sending file: " + e.getMessage());
        e.printStackTrace();
    }
}

    private void handleBroadcastMessage(String sender, String content) {
        storeMessage("All", sender, content, false, false);
        addMessage(sender, content);
    }

    /* ---------- Tab Management ---------- */
    private void handleContactClick(String contactKey) {
        Tab tab = getOrCreateContactTab(contactKey);
        if (!conversationTabPane.getTabs().contains(tab)) {
            conversationTabPane.getTabs().add(tab);
        }
        conversationTabPane.getSelectionModel().select(tab);
    }

    private Tab getOrCreateContactTab(String key) {
        if (contactTabs.containsKey(key)) {
            return contactTabs.get(key);
        }
        return createContactTab(key);
    }

    // In ChatController.java, modify the createContactTab method
private Tab createContactTab(String contactKey) {
    Tab tab = new Tab(contactKey);
    
    VBox container = new VBox();
    container.setSpacing(10);
    
    ScrollPane scrollPane = new ScrollPane();
    scrollPane.setFitToWidth(true);
    scrollPane.getStyleClass().add("messages-scroll-pane");

    VBox messagesContainer = new VBox();
    messagesContainer.getStyleClass().add("messages-container");
    scrollPane.setContent(messagesContainer);
    
    contactMessageContainers.put(contactKey, messagesContainer);

    TextField inputField = new TextField();
    inputField.setPromptText("Type your message...");
    inputField.setPrefHeight(30);
    inputField.setOnAction(event -> {
        String content = inputField.getText().trim();
        if (!content.isEmpty()) {
            sendPrivateMessage(contactKey, content);
            inputField.clear();
        }
    });
    
    // Create send button
    Button sendButton = new Button("Send");
    sendButton.setPrefWidth(70);
    sendButton.getStyleClass().add("primary-button");
    sendButton.setOnAction(event -> {
        String content = inputField.getText().trim();
        if (!content.isEmpty()) {
            sendPrivateMessage(contactKey, content);
            inputField.clear();
        }
    });

    // Create file button - ONLY for contact tabs, not for General
    Button fileButton = new Button("📎");
    fileButton.setPrefWidth(40);
    fileButton.setPrefHeight(40);
    fileButton.getStyleClass().add("primary-button");
    fileButton.setOnAction(event -> {
        // Send file to this contact specifically
        handleSendFileToContact(contactKey);
    });
    
    // Create input area with file button
    HBox inputArea = new HBox(10);
    inputArea.setAlignment(Pos.CENTER);
    inputArea.getStyleClass().add("message-input-container");
    inputArea.getChildren().addAll(inputField, fileButton, sendButton);
    
    // Add initial loading message
    Label loadingLabel = new Label("Loading conversation history...");
    messagesContainer.getChildren().add(loadingLabel);
    
    container.getChildren().addAll(scrollPane, inputArea);
    tab.setContent(container);
    
    // Request history when tab is created
    requestConversationHistory(contactKey);
    
    contactTabs.put(contactKey, tab);
    return tab;
}

private void handleSendFileToContact(String contactEmail) {
    // Create file chooser
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Select File to Send to " + contactEmail);
    
    // Set file filters
    FileChooser.ExtensionFilter allFilter = new FileChooser.ExtensionFilter("All Files", "*.*");
    FileChooser.ExtensionFilter imageFilter = new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif");
    FileChooser.ExtensionFilter docFilter = new FileChooser.ExtensionFilter("Documents", "*.pdf", "*.doc", "*.docx", "*.txt");
    
    fileChooser.getExtensionFilters().addAll(imageFilter, docFilter, allFilter);
    
    // Show dialog
    File selectedFile = fileChooser.showOpenDialog(conversationTabPane.getScene().getWindow());
    if (selectedFile == null) {
        return;
    }
    
    // Check file size (limit to 10MB for example)
    if (selectedFile.length() > 10 * 1024 * 1024) {
        addSystemMessage("File is too large. Maximum size is 10MB.");
        return;
    }
    
    // Send file to the specific contact
    sendFile(contactEmail, selectedFile);
}

    private void loadConversationHistory(String contactEmail, VBox container) {
        List<MessageData> localHistory = conversationMap.getOrDefault(contactEmail, new ArrayList<>());
        Label loadingLabel = new Label("Loading conversation history...");
        loadingLabel.setStyle("-fx-text-fill: #757575; -fx-font-style: italic;");
        container.getChildren().add(loadingLabel);
        
        for (MessageData msg : localHistory) {
            if (msg.isOutgoing) {
                addOutgoingMessageToContainer(container, msg.content);
            } else {
                addIncomingMessageToContainer(container, msg.sender, msg.content);
            }
        }
        
        new Thread(() -> {
            try {
                Thread.sleep(500);
                Platform.runLater(() -> requestConversationHistory(contactEmail));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void flashContactTab(String key) {
        Tab tab = contactTabs.get(key);
        if (tab != null && !tab.isSelected()) {
            tab.setStyle("-fx-background-color: #FFD700;");
        }
    }

    /* ---------- Message Handling ---------- */
    private void sendMessage() {
        String messageText = messageInput.getText().trim();
        if (messageText.isEmpty()) return;
        if (messageText.startsWith("@")) {
            handlePrivateCommand(messageText);
        } else {
            sendBroadcastMessage(messageText);
        }
        messageInput.clear();
    }

    @FXML
    public void handleSendButtonAction(ActionEvent event) {
        sendMessage();
    }

    private void handlePrivateCommand(String messageText) {
        int spaceIndex = messageText.indexOf(" ");
        if (spaceIndex > 1) {
            String recipient = messageText.substring(1, spaceIndex);
            String content = messageText.substring(spaceIndex + 1);
            if (contacts.contains(recipient)) {
                sendPrivateMessage(recipient, content);
                Tab recipientTab = getOrCreateContactTab(recipient);
                if (!conversationTabPane.getTabs().contains(recipientTab)) {
                    conversationTabPane.getTabs().add(recipientTab);
                }
                conversationTabPane.getSelectionModel().select(recipientTab);
            }
        }
    }

    private void sendPrivateMessage(String recipient, String content) {
        try {
            String messageId = "msg_" + System.currentTimeMillis() + "_" +
                    Integer.toHexString((int) (Math.random() * 10000));

            JSONObject privateMsg = new JSONObject();
            privateMsg.put("type", "private");
            privateMsg.put("to", recipient);
            privateMsg.put("content", content);
            privateMsg.put("sender", userEmail);
            privateMsg.put("id", messageId);
            
            privateMsg = com.chatapp.common.util.MessageValidator.addChecksum(privateMsg);
            out.println(privateMsg.toString());
            
            storeMessage(recipient, userEmail, content, true, true);
            
            VBox container = contactMessageContainers.get(recipient);
            if (container != null) {
                addOutgoingMessageToContainer(container, content, messageId);
            }
        } catch (JSONException e) {
            addSystemMessage("Error sending message: " + e.getMessage());
        }
    }

    private void sendBroadcastMessage(String content) {
        try {
            JSONObject msg = new JSONObject();
            msg.put("type", "broadcast");
            msg.put("content", content);
            msg.put("sender", userEmail);
            out.println(msg.toString());
            
            storeMessage("All", userEmail, content, false, true);
            addOutgoingMessage(content);
        } catch (JSONException e) {
            addSystemMessage("Error sending message: " + e.getMessage());
        }
    }

    private void storeMessage(String key, String sender, String content, boolean isPrivate, boolean isOutgoing) {
        conversationMap.putIfAbsent(key, new ArrayList<>());
        conversationMap.get(key).add(new MessageData(sender, content, isPrivate, isOutgoing));
    }

    /* ---------- UI Components ---------- */
    private void addSystemMessage(String text) {
        HBox box = new HBox(new Label(text));
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("system-message");
        messageContainer.getChildren().add(box);
    }

    private void handleIncomingMessage(String message) {
        try {
            JSONObject msgJson = new JSONObject(message);
            String type = msgJson.getString("type");
            if ("delivery_receipt".equals(type)) {
                handleDeliveryReceipt(msgJson);
                return;
            }
            if ("read_receipt".equals(type)) {
                handleReadReceipt(msgJson);
                return;
            }
            if ("private".equals(type)) {
                String content = msgJson.getString("content");
                if (msgJson.optBoolean("isGroup", false)) {
                    String groupName = msgJson.getString("groupName");
                    handleGroupMessage(groupName, msgJson.optString("sender", "Server"), content);
                } else {
                    String sender = msgJson.optString("sender", "Server");
                    sendReadReceipt(msgJson.getString("id"), sender);
                    handlePrivateMessage(sender, content);
                }
            } else if ("broadcast".equals(type)) {
                String content = msgJson.getString("content");
                String sender = msgJson.optString("sender", "Server");
                handleBroadcastMessage(sender, content);
            } else if ("system".equals(type)) {
                String content = msgJson.getString("content");
                addSystemMessage(content);
            } else if ("HISTORY_RESPONSE".equals(type)) {
                handleHistoryResponse(msgJson);
            } else if ("group_created".equals(type)) {
                String groupName = msgJson.getString("groupName");
                System.out.println("Group created: " + groupName);
                
                Platform.runLater(() -> {
                    if (!contacts.contains(groupName)) {
                        contacts.add(groupName);
                        saveContacts(); // If you're using local storage
                        refreshContactsList();
                        addSystemMessage("You've been added to group: " + groupName);
                    }
                });
            }
            else if ("groups_list".equals(type)) {
                System.out.println("Received groups list from server");
                JSONArray groupsArray = msgJson.getJSONArray("groups");
                System.out.println("Found " + groupsArray.length() + " groups");
                
                Platform.runLater(() -> {
                    for (int i = 0; i < groupsArray.length(); i++) {
                        try {
                            JSONObject groupJson = groupsArray.getJSONObject(i);
                            String groupName = groupJson.getString("name");
                            System.out.println("Adding group to contacts: " + groupName);
                            
                            if (!contacts.contains(groupName)) {
                                contacts.add(groupName);
                            }
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                    }
                    
                    saveContacts(); // If you're saving contacts locally
                    refreshContactsList();
                });
            }

            else if ("file".equals(type)) {
                String sender = msgJson.getString("sender");
                String fileId = msgJson.getString("id");
                String filename = msgJson.getString("filename");
                String mimeType = msgJson.getString("mimeType");
                long fileSize = msgJson.getLong("fileSize");
                
                // Format file size for display
                String sizeDisplay = formatFileSize(fileSize);
                
                Platform.runLater(() -> {
                    // Add to UI
                    if (!sender.equals(userEmail)) {
                        // This is an incoming file
                        if (!contacts.contains(sender)) {
                            contacts.add(sender);
                            saveContacts();
                            refreshContactsList();
                        }
                        
                        // Create or get tab for this sender
                        Tab tab = getOrCreateContactTab(sender);
                        if (!conversationTabPane.getTabs().contains(tab)) {
                            conversationTabPane.getTabs().add(tab);
                        }
                        
                        // Add file message to conversation
                        VBox container = contactMessageContainers.get(sender);
                        if (container != null) {
                            addFileMessageToContainer(container, sender, fileId, filename, mimeType, sizeDisplay, false);
                            flashContactTab(sender);
                            
                            // Send read receipt if tab is selected
                            if (conversationTabPane.getSelectionModel().getSelectedItem() == tab) {
                                sendFileViewedReceipt(fileId, sender);
                            }
                        }
                    }
                });
            }
            else if ("file_data".equals(type)) {
                String fileId = msgJson.getString("fileId");
                String base64Data = msgJson.getString("data");
                
                // Save the file or display it
                handleFileData(fileId, base64Data);
            }
            else if ("file_receipt".equals(type)) {
                String fileId = msgJson.getString("fileId");
                String status = msgJson.getString("status");
                
                // Update file status in UI
                Platform.runLater(() -> {
                    updateFileStatus(fileId, status);
                });
            }
        } catch (JSONException e) {
            addSystemMessage("Invalid message format: " + message);
        }
    }

    private void handleHistoryResponse(JSONObject response) {
        try {
            if (!response.has("messages")) {
                System.err.println("Invalid history response: missing messages array");
                Platform.runLater(() -> addSystemMessage("Error: Could not load conversation history"));
                return;
            }
            
            JSONArray messagesArray = response.getJSONArray("messages");
            System.out.println("Received history with " + messagesArray.length() + " messages");
            
            if (messagesArray.length() == 0) {
                System.out.println("No history messages found");
                Platform.runLater(() -> {
                    for (VBox container : contactMessageContainers.values()) {
                        container.getChildren().removeIf(node -> 
                            node instanceof Label && 
                            ((Label)node).getText().equals("Loading conversation history..."));
                    }
                });
                return;
            }
    
            Map<String, List<JSONObject>> conversationMessages = new HashMap<>();
            
            for (int i = 0; i < messagesArray.length(); i++) {
                JSONObject messageJson = messagesArray.getJSONObject(i);
                
                if (!messageJson.has("sender") || !messageJson.has("type") || !messageJson.has("conversationId")) {
                    continue;
                }
                
                String sender = messageJson.getString("sender");
                String conversationId = messageJson.getString("conversationId");
                String partner;
                if (sender.equals(userEmail)) {
                    if (conversationId.startsWith(userEmail + "_")) {
                        partner = conversationId.substring((userEmail + "_").length());
                    } else if (conversationId.endsWith("_" + userEmail)) {
                        partner = conversationId.substring(0, conversationId.length() - (userEmail.length() + 1));
                    } else if (conversationId.equals("broadcast")) {
                        partner = "All";
                    } else {
                        String[] parts = conversationId.split("_");
                        partner = parts[0].equals(userEmail) ? parts[1] : parts[0];
                    }
                } else {
                    partner = sender;
                }
                
                conversationMessages.computeIfAbsent(partner, k -> new ArrayList<>()).add(messageJson);
            }
            
            for (Map.Entry<String, List<JSONObject>> entry : conversationMessages.entrySet()) {
                String partner = entry.getKey();
                List<JSONObject> messages = entry.getValue();
                final String contactKey = partner;
                
                Platform.runLater(() -> {
                    VBox container;
                    if (!contactMessageContainers.containsKey(contactKey)) {
                        Tab newTab = createContactTab(contactKey);
                        container = contactMessageContainers.get(contactKey);
                        if (!contacts.contains(contactKey) && !contactKey.equals("All")) {
                            contacts.add(contactKey);
                            saveContacts();
                            refreshContactsList();
                        }
                    } else {
                        container = contactMessageContainers.get(contactKey);
                        container.getChildren().removeIf(node -> 
                            node instanceof Label &&
                            ((Label)node).getText().equals("Loading conversation history..."));
                    }
                    
                    if (container != null) {
                        for (JSONObject messageJson : messages) {
                            try {
                                String sender = messageJson.getString("sender");
                                
                                // Handle regular text messages
                                if (messageJson.has("content")) {
                                    String content = messageJson.getString("content");
                                    if (sender.equals(userEmail)) {
                                        addOutgoingMessageToContainer(container, content);
                                    } else {
                                        addIncomingMessageToContainer(container, sender, content);
                                    }
                                }
                                // Handle file messages
                                else if (messageJson.getString("type").equals("file")) {
                                    String fileId = messageJson.getString("id");
                                    String filename = messageJson.getString("filename");
                                    String mimeType = messageJson.getString("mimeType");
                                    long fileSize = messageJson.getLong("fileSize");
                                    String sizeDisplay = formatFileSize(fileSize);
                                    
                                    if (sender.equals(userEmail)) {
                                        addFileMessageToContainer(container, sender, fileId, filename, mimeType, sizeDisplay, true);
                                    } else {
                                        addFileMessageToContainer(container, sender, fileId, filename, mimeType, sizeDisplay, false);
                                    }
                                }
                            } catch (JSONException e) {
                                System.err.println("Error displaying message: " + e.getMessage());
                            }
                        }
                    }
                });
            }
        } catch (JSONException e) {
            System.err.println("Error processing history: " + e.getMessage());
            e.printStackTrace();
            Platform.runLater(() -> addSystemMessage("Error processing chat history"));
        }
    }
    // Add to ChatController.java

                private void addFileMessageToContainer(VBox container, String sender, String fileId, 
                String filename, String mimeType, String sizeDisplay, boolean isOutgoing) {

                HBox messageBox = new HBox(10);
                messageBox.setAlignment(isOutgoing ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
                messageBox.setPadding(new Insets(5, 10, 5, 10));

                VBox fileBox = new VBox(5);
                fileBox.setStyle("-fx-background-color: " + (isOutgoing ? "#DCF8C6" : "#FFFFFF") + ";" +
                            "-fx-background-radius: 10;" +
                            "-fx-padding: 10;");

                Label nameLabel = new Label(filename);
                nameLabel.setStyle("-fx-font-weight: bold;");

                Label infoLabel = new Label(mimeType + " • " + sizeDisplay);
                infoLabel.setStyle("-fx-font-size: 10;");

                Button downloadButton = new Button("Download");
                downloadButton.setOnAction(e -> downloadFile(fileId, filename));

                fileBox.getChildren().addAll(nameLabel, infoLabel, downloadButton);

                if (isOutgoing) {
                Label statusLabel = new Label("Sending...");
                statusLabel.setId("file-status-" + fileId);
                statusLabel.setStyle("-fx-font-size: 10;");
                fileBox.getChildren().add(statusLabel);
                }

                messageBox.getChildren().add(fileBox);
                container.getChildren().add(messageBox);

                // Auto-scroll to bottom
                ScrollPane scrollPane = (ScrollPane) container.getParent();
                scrollPane.setVvalue(1.0);
                }

                private void downloadFile(String fileId, String filename) {
                    try {
                        // Store the filename for later use
                        pendingDownloads.put(fileId, filename);
                        
                        // Request file from server
                        JSONObject request = new JSONObject();
                        request.put("type", "file_download");
                        request.put("fileId", fileId);
                        out.println(request.toString());
                
                        addSystemMessage("Downloading file: " + filename);
                    } catch (JSONException e) {
                        addSystemMessage("Error requesting file: " + e.getMessage());
                    }
                }

                private void handleFileData(String fileId, String base64Data) {
                    try {
                        // Decode file data
                        byte[] fileData = java.util.Base64.getDecoder().decode(base64Data);
                        
                        // Create a "Downloads" directory if it doesn't exist
                        File downloadsDir = new File("Downloads");
                        if (!downloadsDir.exists()) {
                            downloadsDir.mkdirs();
                        }
                        
                        // Get the original filename from the server response or use the fileId
                        String filename = "downloaded_file_" + fileId;
                        if (pendingDownloads.containsKey(fileId)) {
                            filename = pendingDownloads.get(fileId);
                            pendingDownloads.remove(fileId);
                        }

                        File file = new File(downloadsDir, ensureUniqueFilename(downloadsDir, filename));
        
        // Save the file directly
        Files.write(file.toPath(), fileData);
        
        // Show success message with file location
        Platform.runLater(() -> {
            addSystemMessage("File downloaded successfully: " + file.getAbsolutePath());
            
            // Optional: Open the containing folder
            try {
                Runtime.getRuntime().exec("explorer.exe /select," + file.getAbsolutePath());
            } catch (IOException e) {
                // Silently ignore if we can't open the folder
            }
        });
        
    } catch (Exception e) {
        addSystemMessage("Error processing file data: " + e.getMessage());
        e.printStackTrace();
    }
}

private String ensureUniqueFilename(File directory, String filename) {
    File file = new File(directory, filename);
    if (!file.exists()) {
        return filename;
    }
    
    // If file exists, add a number to make it unique
    String name = filename;
    String extension = "";
    int dotIndex = filename.lastIndexOf('.');
    if (dotIndex > 0) {
        name = filename.substring(0, dotIndex);
        extension = filename.substring(dotIndex);
    }
    
    int counter = 1;
    while (true) {
        String newName = name + "_" + counter + extension;
        file = new File(directory, newName);
        if (!file.exists()) {
            return newName;
        }
        counter++;
    }
}

private void sendGroupFile(String groupName, File file) {
    try {
        // Read file data
        byte[] fileData = Files.readAllBytes(file.toPath());
        
        // Determine mime type
        String mimeType = Files.probeContentType(file.toPath());
        if (mimeType == null) {
            mimeType = "application/octet-stream";
        }
        
        // Encode file data as Base64
        String base64Data = java.util.Base64.getEncoder().encodeToString(fileData);
        
        // Create file upload message
        JSONObject fileUpload = new JSONObject();
        fileUpload.put("type", "group_file_upload");
        fileUpload.put("groupName", groupName);
        fileUpload.put("sender", userEmail);
        fileUpload.put("filename", file.getName());
        fileUpload.put("mimeType", mimeType);
        fileUpload.put("data", base64Data);
        
        // Send message
        out.println(fileUpload.toString());
        
        // Add message to UI
        addSystemMessage("Sending file: " + file.getName() + " to group: " + groupName);
    } catch (IOException | JSONException e) {
        addSystemMessage("Error sending file: " + e.getMessage());
        e.printStackTrace();
    }
}

            private void sendFileViewedReceipt(String fileId, String sender) {
                try {
                JSONObject receipt = new JSONObject();
                receipt.put("type", "file_viewed");
                receipt.put("fileId", fileId);
                receipt.put("sender", sender);
                out.println(receipt.toString());
                } catch (JSONException e) {
                System.err.println("Error sending file viewed receipt: " + e.getMessage());
                }
                }

            private void updateFileStatus(String fileId, String status) {
                Label statusLabel = (Label) conversationTabPane.getScene().lookup("#file-status-" + fileId);
                if (statusLabel != null) {
                statusLabel.setText(status);
                }
                }

                private String formatFileSize(long size) {
                final String[] units = new String[] { "B", "KB", "MB", "GB", "TB" };
                int unitIndex = 0;
                double sizeValue = size;

                while (sizeValue > 1024 && unitIndex < units.length - 1) {
                sizeValue /= 1024;
                unitIndex++;
                }

                return String.format("%.1f %s", sizeValue, units[unitIndex]);
                }

    private void handleDeliveryReceipt(JSONObject receipt) {
        try {
            String messageId = receipt.getString("messageId");
            String status = receipt.getString("status");
            Platform.runLater(() -> updateMessageStatus(messageId, status));
            if ("delivered".equals(status) && networkService != null) {
                networkService.processDeliveryReceipt(messageId);
            }
        } catch (JSONException e) {
            System.err.println("Error processing delivery receipt: " + e.getMessage());
        }
    }
    
    private void handleReadReceipt(JSONObject receipt) {
        try {
            String messageId = receipt.getString("messageId");
            Platform.runLater(() -> updateMessageStatus(messageId, "read"));
        } catch (JSONException e) {
            System.err.println("Error processing read receipt: " + e.getMessage());
        }
    }
    
    private void sendReadReceipt(String messageId, String sender) {
        try {
            JSONObject readReceipt = new JSONObject();
            readReceipt.put("type", "read_receipt");
            readReceipt.put("messageId", messageId);
            readReceipt.put("sender", sender);
            out.println(readReceipt.toString());
        } catch (JSONException e) {
            System.err.println("Error sending read receipt: " + e.getMessage());
        }
    }
    
    private void addOutgoingMessageToContainer(VBox container, String text, String messageId) {
        VBox messageBox = new VBox(3);
        messageBox.setAlignment(Pos.CENTER_RIGHT);
        
        Label messageLabel = new Label("You: " + text);
        messageLabel.getStyleClass().add("bubble-right");
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(300);
        
        Label statusLabel = new Label("Sending...");
        statusLabel.setId("status-" + messageId);
        statusLabel.getStyleClass().add("message-status");
        statusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #888888;");
        
        messageBox.getChildren().addAll(messageLabel, statusLabel);
        
        HBox wrapper = new HBox(messageBox);
        wrapper.setAlignment(Pos.CENTER_RIGHT);
        wrapper.setPadding(new Insets(5, 10, 5, 10));
        
        container.getChildren().add(wrapper);
    }
    
    private void addOutgoingMessageToContainer(VBox container, String text) {
        HBox box = new HBox(new Label("You: " + text));
        box.setAlignment(Pos.CENTER_RIGHT);
        box.getStyleClass().add("bubble-right");
        container.getChildren().add(box);
    }
    
    public void updateMessageStatus(String messageId, String status) {
        for (VBox container : contactMessageContainers.values()) {
            Label statusLabel = (Label) container.lookup("#status-" + messageId);
            if (statusLabel != null) {
                switch (status) {
                    case "delivered":
                        statusLabel.setText("✓ Delivered");
                        break;
                    case "read":
                        statusLabel.setText("✓✓ Read");
                        statusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #4fc3f7;");
                        break;
                    case "failed":
                        statusLabel.setText("❌ Failed");
                        statusLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #e57373;");
                        break;
                    case "pending":
                        statusLabel.setText("⏱ Pending");
                        break;
                    default:
                        statusLabel.setText(status);
                }
            }
        }
    }

    private void addIncomingMessageToContainer(VBox container, String sender, String text) {
        HBox box = new HBox(new Label(sender + ": " + text));
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("bubble-left");
        container.getChildren().add(box);
    }

    private void addMessage(String sender, String content) {
        HBox box = new HBox(new Label(sender + ": " + content));
        box.setAlignment(Pos.CENTER_LEFT);
        box.getStyleClass().add("bubble-left");
        messageContainer.getChildren().add(box);
    }

    private void addOutgoingMessage(String content) {
        HBox box = new HBox(new Label("You: " + content));
        box.setAlignment(Pos.CENTER_RIGHT);
        box.getStyleClass().add("bubble-right");
        messageContainer.getChildren().add(box);
    }

    /* ---------- Contacts Persistence ---------- */
   
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
    private HBox createContactItem(String email, String status) {
        HBox contactItem = new HBox();
        contactItem.setSpacing(10);
    
        Label contactName = new Label(email);
        contactName.setStyle("-fx-font-weight: bold;");
    
        Label statusLabel = new Label();
        if ("online".equals(status)) {
            statusLabel.setText("●");
            statusLabel.setStyle("-fx-text-fill: green; -fx-font-size: 12px;");
        } else {
            statusLabel.setText("●");
            statusLabel.setStyle("-fx-text-fill: gray; -fx-font-size: 12px;");
        }
    
        contactItem.getChildren().addAll(contactName, statusLabel);
        return contactItem;
    }

    private void refreshContactsList() {
        contactsList.getItems().clear();
        for (String email : contacts) {
            HBox contactItem = createContactItem(email, "offline"); // Par défaut, "offline"
            contactsList.getItems().add(contactItem);
        }
    }
    private final ContactDAO contactDAO = new ContactDAOImpl();

    @FXML
    private void loadContacts() {
        try {
            contacts.clear();
            contacts.addAll(contactDAO.getContacts(userEmail));
            loadGroups();
            refreshContactsList();
            
        } catch (Exception e) {
            addSystemMessage("Error loading contacts: " + e.getMessage());
        }
    }

    @FXML
    public void handleAddContactButton(ActionEvent event) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Contact");
        dialog.setHeaderText("Enter the email address of the contact to add:");
        dialog.setContentText("Email:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(email -> {
            if (email.contains("@") && !email.equals(userEmail)) {
                boolean success = contactDAO.addContact(userEmail, email);
                if (success) {
                    contacts.add(email);
                    refreshContactsList();
                    addSystemMessage("Contact added: " + email);
                } else {
                    addSystemMessage("Failed to add contact: " + email);
                }
            } else {
                addSystemMessage("Invalid email address.");
            }
        });
    }

    @FXML
public void handleShowContactsButton(ActionEvent event) {
    List<String> sortedContacts = new ArrayList<>(contacts);
    Collections.sort(sortedContacts);
    contactsList.getItems().clear();
    for (String email : sortedContacts) {
        HBox contactItem = createContactItem(email, "offline"); // Par défaut, "offline"
        contactsList.getItems().add(contactItem);
    }
}

@FXML
public void handleDeleteContactButton(ActionEvent event) {
    HBox selectedContactItem = contactsList.getSelectionModel().getSelectedItem();
    if (selectedContactItem != null) {
        Label contactNameLabel = (Label) selectedContactItem.getChildren().get(0);
        String selectedContact = contactNameLabel.getText();

        boolean success = contactDAO.removeContact(userEmail, selectedContact);
        if (success) {
            contacts.remove(selectedContact);
            refreshContactsList();
            addSystemMessage("Contact removed: " + selectedContact);
        } else {
            addSystemMessage("Failed to remove contact: " + selectedContact);
        }
    } else {
        addSystemMessage("No contact selected.");
    }
}
    
    /* ---------- Group Feature ---------- */
    @FXML
    public void handleCreateGroupButton(ActionEvent event) {
        showCreateGroupDialog();
    }
    
    private void showCreateGroupDialog() {
        Stage dialogStage = new Stage();
        dialogStage.setTitle("Create Group");

        VBox vbox = new VBox(10);
        vbox.setPadding(new Insets(15));

        Label groupNameLabel = new Label("Group Name:");
        TextField groupNameField = new TextField();
        groupNameField.setPromptText("Enter group name...");

        Label contactsLabel = new Label("Select Members:");
        ListView<String> membersListView = new ListView<>();
        membersListView.getItems().addAll(contacts);
        membersListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        Button okButton = new Button("OK");
        okButton.setOnAction(e -> {
            String groupName = groupNameField.getText().trim();
            List<String> selectedMembers = new ArrayList<>(membersListView.getSelectionModel().getSelectedItems());
            if (groupName.isEmpty()) {
                addSystemMessage("Group name cannot be empty!");
                return;
            }
            if (selectedMembers.isEmpty()) {
                addSystemMessage("No members selected!");
                return;
            }
            if (!selectedMembers.contains(userEmail)) {
                selectedMembers.add(userEmail);
            }
            createGroupOnServer(groupName, selectedMembers);
            dialogStage.close();
        });

        vbox.getChildren().addAll(groupNameLabel, groupNameField, contactsLabel, membersListView, okButton);

        Scene scene = new Scene(vbox, 300, 400);
        dialogStage.setScene(scene);
        dialogStage.show();
    }
    
    private void createGroupOnServer(String groupName, List<String> selectedMembers) {
        try {
            JSONObject groupRequest = new JSONObject();
            groupRequest.put("type", "create_group");
            groupRequest.put("groupName", groupName);
            JSONArray membersArray = new JSONArray();
            for (String m : selectedMembers) {
                membersArray.put(m);
            }
            groupRequest.put("members", membersArray);
            groupRequest.put("sender", userEmail);
            out.println(groupRequest.toString());
            addSystemMessage("Creating group on server: " + groupName);
        } catch (JSONException e) {
            addSystemMessage("Error creating group request: " + e.getMessage());
        }
    }
    
    /* ---------- Logout Feature ---------- */
    @FXML
    private void handleLogoutButtonAction(ActionEvent event) {
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
                System.out.println("Socket closed: " + socket.isClosed());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/com/chatapp/client/view/login-view.fxml"));
            Parent loginView = loader.load();
            Scene loginScene = new Scene(loginView);
            Stage stage = (Stage) logoutButton.getScene().getWindow();
            stage.setTitle("Chat Application - Login");
            stage.setScene(loginScene);
            stage.show();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /* ---------- Deprecated/Unsupported Methods ---------- */
    private void launchChatUI(String email, Socket socket, BufferedReader in, PrintWriter out) throws IOException {
        throw new UnsupportedOperationException("This method should not be called from ChatController");
    }
}
