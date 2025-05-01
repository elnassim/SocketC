package com.chatapp.client.controller;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.chatapp.client.network.ClientNetworkService;
import com.chatapp.data.dao.ContactDAO;
import com.chatapp.data.dao.impl.ContactDAOImpl;
import com.chatapp.security.SerpentCipher;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

/**
 * Main controller for the chat application with conversation tabs.
 */
public class ChatController {

    /* ---------- FXML Nodes ---------- */
    @FXML private ScrollPane messageScrollPane;
    @FXML private VBox messageContainer;
    @FXML private TextField messageInput;
    @FXML private Button sendButton;
    @FXML private ListView<String> contactsList;
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
                handleContactClick(newVal);
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

        contactsList.setCellFactory(lv -> new ListCell<String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    // Check if this is a contact (has @) or a group (no @)
                    if (item.contains("@")) {
                        setText(item);
                        setStyle("-fx-font-weight: normal;");
                    } else {
                        setText("👥 " + item); // Group icon
                        setStyle("-fx-font-weight: bold;");
                    }
                }
            }
        });
    }
    
    /**
     * Nouvelle méthode pour ouvrir la vue de gestion du profil.
     * Elle charge le fichier FXML, récupère le contrôleur, transmet l'email de l'utilisateur et affiche la fenêtre.
     */
    @FXML
    private void handleProfile(ActionEvent event) {
        try {
            // Debug print to check userEmail
            System.out.println("Current userEmail: " + userEmail);
            
            // Get the resource using different methods to debug
            String fxmlPath = "/com/chatapp/client/view/profile-view.fxml";
            URL resource = getClass().getResource(fxmlPath);
            if (resource == null) {
                // Try alternative loading methods
                resource = getClass().getClassLoader().getResource("com/chatapp/client/view/profile-view.fxml");
                if (resource == null) {
                    System.err.println("Could not find profile-view.fxml in any location");
                    addSystemMessage("Error: Could not find profile view file");
                    return;
                }
            }
            
            System.out.println("Found FXML at: " + resource.toExternalForm());
            
            FXMLLoader loader = new FXMLLoader(resource);
            Parent profileRoot = loader.load();
            
            // Debug print before getting controller
            System.out.println("FXML loaded successfully, getting controller...");
            
            ProfileController profileController = loader.getController();
            if (profileController == null) {
                System.err.println("Failed to get ProfileController instance");
                addSystemMessage("Error: Could not initialize profile controller");
                return;
            }
            
            System.out.println("Initializing profile data with email: " + userEmail);
            profileController.initData(userEmail);
            
            Scene profileScene = new Scene(profileRoot);
            Stage profileStage = new Stage();
            profileStage.setTitle("Profile Settings");
            profileStage.setScene(profileScene);
            profileStage.show();
            
        } catch (IOException e) {
            System.err.println("IOException while loading profile view:");
            e.printStackTrace();
            addSystemMessage("Error loading profile view: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error while loading profile view:");
            e.printStackTrace();
            addSystemMessage("Unexpected error: " + e.getMessage());
        }
    }

    /**
     * Initialise la session de chat et configure la connexion.
     */
    public void initChatSession(String email, Socket socket, BufferedReader in, PrintWriter out) {
        try {
            System.out.println("Initializing chat session for: " + email);
            
            if (email == null || socket == null || in == null || out == null) {
                throw new IllegalArgumentException("All parameters must be non-null");
            }
            
            this.userEmail = email;
            this.socket = socket;
            this.in = in;
            this.out = out;
            this.connected = true;
            
            // Initialize network service
            System.out.println("Initializing network service...");
            this.networkService = new ClientNetworkService();
            
            // Load contacts first (this has to work before loadGroups)
            System.out.println("Loading contacts...");
            loadContacts();
            
            // Add this explicit call to load groups after the socket is initialized
            System.out.println("Loading groups for user: " + email);
            loadGroups();
            
            // Refresh UI
            System.out.println("Refreshing contacts list...");
            Platform.runLater(() -> {
                try {
                    refreshContactsList();
                    addSystemMessage("Connected as " + userEmail);
                    
                    // Start message listener after UI is ready
                    System.out.println("Starting message listener...");
                    startMessageListener();
                    
                    // Make sure the General tab is selected and visible
                    if (conversationTabPane != null && !conversationTabPane.getTabs().isEmpty()) {
                        conversationTabPane.getSelectionModel().select(0);
                    } else {
                        System.err.println("Warning: conversationTabPane is null or empty");
                    }
                    
                } catch (Exception e) {
                    System.err.println("Error during UI refresh: " + e.getMessage());
                    e.printStackTrace();
                    addSystemMessage("Error initializing chat: " + e.getMessage());
                    throw new RuntimeException("Failed to initialize UI", e);
                }
            });
            
            System.out.println("Chat session initialization completed successfully");
            
        } catch (Exception e) {
            System.err.println("Error initializing chat session: " + e.getMessage());
            e.printStackTrace();
            Platform.runLater(() -> addSystemMessage("Error initializing chat session: " + e.getMessage()));
            throw new RuntimeException("Failed to initialize chat session", e);
        }
    }

    private void startMessageListener() {
        System.out.println("Creating message listener thread...");
        
        Thread listenerThread = new Thread(() -> {
            try {
                // Load groups again after a short delay to ensure connection is ready
                Thread.sleep(1000);
                
                System.out.println("Requesting initial groups list...");
                Platform.runLater(() -> {
                    try {
                        JSONObject request = new JSONObject();
                        request.put("type", "get_groups");
                        out.println(request.toString());
                        out.flush(); // Make sure to flush the output
                    } catch (JSONException e) {
                        System.err.println("Error requesting groups: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
                
                System.out.println("Starting message read loop...");
                String line;
                while (connected && (line = in.readLine()) != null) {
                    final String receivedMsg = line;
                    System.out.println("Received message: " + receivedMsg);
                    
                    // Handle message in a try-catch block to prevent listener from dying
                    try {
                        Platform.runLater(() -> handleIncomingMessage(receivedMsg));
                    } catch (Exception e) {
                        System.err.println("Error handling message: " + receivedMsg);
                        e.printStackTrace();
                        Platform.runLater(() -> addSystemMessage("Error processing message: " + e.getMessage()));
                    }
                }
            } catch (InterruptedException e) {
                System.err.println("Message listener interrupted: " + e.getMessage());
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                if (connected) {
                    System.err.println("Connection error in message listener: " + e.getMessage());
                    Platform.runLater(() -> addSystemMessage("Connection lost: " + e.getMessage()));
                }
            } catch (Exception e) {
                System.err.println("Unexpected error in message listener: " + e.getMessage());
                e.printStackTrace();
                Platform.runLater(() -> addSystemMessage("Error in message listener: " + e.getMessage()));
            }
        });
        
        listenerThread.setDaemon(true);
        listenerThread.start();
        System.out.println("Message listener thread started");
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
        
        // Always clear and request fresh history from database
        VBox container = contactMessageContainers.get(contactKey);
        if (container != null) {
            container.getChildren().clear();
            
            // Add loading indicator
            Label loadingLabel = new Label("Loading conversation history...");
            loadingLabel.getStyleClass().add("system-message");
            container.getChildren().add(loadingLabel);
            
            // Request fresh history from database
            if (!contactKey.contains("@")) {
                // Group chat
                requestGroupHistory(contactKey);
            } else {
                // Direct message
                requestConversationHistory(contactKey);
            }
        }
    }

    private void requestGroupHistory(String groupName) {
        try {
            JSONObject request = new JSONObject();
            request.put("type", "GET_GROUP_HISTORY");
            request.put("groupName", groupName);
            out.println(request.toString());
            System.out.println("Requesting history for group: " + groupName);
        } catch (JSONException e) {
            addSystemMessage("Error requesting group history: " + e.getMessage());
        }
    }
    private void handleGroupHistoryResponse(JSONObject response) {
        try {
            JSONArray messagesArray = response.getJSONArray("messages");
            String groupName = response.getString("groupName");
            System.out.println("Received history for group: " + groupName + " with " + messagesArray.length() + " messages");
            
            Platform.runLater(() -> {
                VBox container = contactMessageContainers.get(groupName);
                if (container != null) {
                    // Clear loading message
                    container.getChildren().removeIf(node -> 
                        node instanceof Label && 
                        ((Label)node).getText().equals("Loading conversation history..."));
                    
                    // Add messages to UI
                    for (int i = 0; i < messagesArray.length(); i++) {
                        try {
                            JSONObject messageJson = messagesArray.getJSONObject(i);
                            String sender = messageJson.getString("sender");
                            
                            // Check if this is a file message
                            if ("file".equals(messageJson.optString("type"))) {
                                // Handle file message
                                String fileId = messageJson.getString("id");
                                String filename;
                                
                                // Handle different field names for files
                                if (messageJson.has("originalFilename")) {
                                    filename = messageJson.getString("originalFilename");
                                } else {
                                    filename = messageJson.getString("filename");
                                }
                                
                                String mimeType = messageJson.getString("mimeType");
                                long fileSize = messageJson.getLong("fileSize");
                                boolean isOutgoing = sender.equals(userEmail);
                                
                                String sizeDisplay = formatFileSize(fileSize);
                                addFileMessageToContainer(container, sender, fileId, filename, mimeType, sizeDisplay, isOutgoing);
                            } else {
                                // Handle text message
                                String content = messageJson.getString("content");
                                
                                // Handle encrypted messages from history
                                if (messageJson.optBoolean("encrypted", false)) {
                                    try {
                                        byte[] encryptedBytes = java.util.Base64.getDecoder().decode(content);
                                        byte[] decryptedBytes = SerpentCipher.decrypt(encryptedBytes);
                                        content = new String(decryptedBytes);
                                    } catch (Exception e) {
                                        System.err.println("Error decrypting message: " + e.getMessage());
                                        content = "[Encrypted message - Decryption failed]";
                                    }
                                }
                                
                                if (sender.equals(userEmail)) {
                                    // Outgoing message
                                    addOutgoingMessageToContainer(container, content);
                                } else {
                                    // Incoming message
                                    addIncomingMessageToContainer(container, sender, content);
                                }
                            }
                        } catch (JSONException e) {
                            System.err.println("Error displaying group message: " + e.getMessage());
                        }
                    }
                    
                    // Scroll to bottom
                    ScrollPane scrollPane = (ScrollPane) container.getParent();
                    scrollPane.setVvalue(1.0);
                }
            });
        } catch (JSONException e) {
            System.err.println("Error processing group history: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private Tab getOrCreateContactTab(String key) {
        if (contactTabs.containsKey(key)) {
            return contactTabs.get(key);
        }
        return createContactTab(key);
    }

    // In ChatController.java, modify the createContactTab method
private Tab createContactTab(String contactKey) {
    boolean isGroup = !contactKey.contains("@");
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
            if (isGroup) {
                sendGroupMessage(contactKey, content);
            } else {
                sendPrivateMessage(contactKey, content);
            }
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
        if (isGroup) {
            sendGroupMessage(contactKey, content);
        } else {
            sendPrivateMessage(contactKey, content);
        }
        inputField.clear();
    }
});

    // Create file button - ONLY for contact tabs, not for General
    
    
    Button fileButton = new Button("📎");
    fileButton.setPrefWidth(40);
    fileButton.setPrefHeight(40);
    fileButton.getStyleClass().add("primary-button");
    fileButton.setOnAction(event -> {
        // Use different handler for group vs individual
        if (isGroup) {
            handleSendFileToGroup(contactKey);
        } else {
            handleSendFileToContact(contactKey);
        }
    });
    
    // Create input area with file button
    HBox inputArea = new HBox(10);
    inputArea.setAlignment(Pos.CENTER);
    inputArea.getStyleClass().add("message-input-container");
    inputArea.getChildren().addAll(inputField, fileButton, sendButton);
    
    // Add initial loading message
    // Display loading message first
Label loadingLabel = new Label("Loading conversation history...");
messagesContainer.getChildren().add(loadingLabel);

container.getChildren().addAll(scrollPane, inputArea);
tab.setContent(container);

// Now display locally stored messages if available
// List<MessageData> localMessages = conversationMap.getOrDefault(contactKey, new ArrayList<>());
// if (!localMessages.isEmpty()) {
//     // We have local messages, remove loading label
//     messagesContainer.getChildren().remove(loadingLabel);
    
//     // Add local messages to UI
//     for (MessageData msg : localMessages) {
//         if (msg.isOutgoing) {
//             addOutgoingMessageToContainer(messagesContainer, msg.content);
//         } else {
//             addIncomingMessageToContainer(messagesContainer, msg.sender, msg.content);
//         }
//     }
    
//     // Scroll to bottom
//     Platform.runLater(() -> {
//         scrollPane.setVvalue(1.0);
//     });
// }

// Then request history from server for any new messages
requestConversationHistory(contactKey);
    
    contactTabs.put(contactKey, tab);
    return tab;
}

private void handleSendFileToGroup(String groupName) {
    // Similar to handleSendFileToContact but calls sendGroupFile instead
    FileChooser fileChooser = new FileChooser();
    fileChooser.setTitle("Select File to Send to Group: " + groupName);
    
    // Set file filters
    FileChooser.ExtensionFilter allFilter = new FileChooser.ExtensionFilter("All Files", "*.*");
    FileChooser.ExtensionFilter imageFilter = new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif");
    FileChooser.ExtensionFilter docFilter = new FileChooser.ExtensionFilter("Documents", "*.pdf", "*.doc", "*.docx", "*.txt");
    
    fileChooser.getExtensionFilters().addAll(imageFilter, docFilter, allFilter);
    
    File selectedFile = fileChooser.showOpenDialog(conversationTabPane.getScene().getWindow());
    if (selectedFile == null) {
        return;
    }
    
    if (selectedFile.length() > 10 * 1024 * 1024) {
        addSystemMessage("File is too large. Maximum size is 10MB.");
        return;
    }
    
    sendGroupFile(groupName, selectedFile);
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

            // Encrypt the message content
            byte[] encryptedContent = SerpentCipher.encrypt(content.getBytes());
            
            JSONObject privateMsg = new JSONObject();
            privateMsg.put("type", "private");
            privateMsg.put("to", recipient);
            privateMsg.put("content", java.util.Base64.getEncoder().encodeToString(encryptedContent));
            privateMsg.put("sender", userEmail);
            privateMsg.put("id", messageId);
            privateMsg.put("encrypted", true); // Add flag to indicate encrypted content
            
            out.println(privateMsg.toString());
            
            storeMessage(recipient, userEmail, content, true, true);
            
            VBox container = contactMessageContainers.get(recipient);
            if (container != null) {
                addOutgoingMessageToContainer(container, content, messageId);
            }
        } catch (Exception e) {
            addSystemMessage("Error sending message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void sendGroupMessage(String groupName, String content) {
        try {
            String messageId = "msg_" + System.currentTimeMillis() + "_" +
                    Integer.toHexString((int) (Math.random() * 10000));
                    
            JSONObject groupMsg = new JSONObject();
            groupMsg.put("type", "private");  // Keep the type as private for server compatibility
            groupMsg.put("isGroup", true);     // Add this flag to identify it as a group message
            groupMsg.put("groupName", groupName);
            groupMsg.put("content", content);
            groupMsg.put("sender", userEmail);
            groupMsg.put("id", messageId);
            
            out.println(groupMsg.toString());
            
            storeMessage(groupName, userEmail, content, true, true);
            
            VBox container = contactMessageContainers.get(groupName);
            if (container != null) {
                addOutgoingMessageToContainer(container, content, messageId);
            }
        } catch (JSONException e) {
            addSystemMessage("Error sending group message: " + e.getMessage());
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

    // Modified storeMessage method
// filepath: c:\wamp64\www\SocketProject\src\main\java\com\chatapp\client\controller\ChatController.java
private void storeMessage(String key, String sender, String content, boolean isPrivate, boolean isOutgoing) {
    conversationMap.putIfAbsent(key, new ArrayList<>());
    conversationMap.get(key).add(new MessageData(sender, content, isPrivate, isOutgoing));
    // No call to saveConversationToDisk()
}
private void loadConversationsFromDisk() {
    // try {
    //     File conversationsDir = new File("data/conversations");
    //     if (!conversationsDir.exists()) {
    //         return;
    //     }
        
    //     System.out.println("Loading conversations from disk...");
        
    //     for (File file : conversationsDir.listFiles()) {
    //         if (file.isFile() && file.getName().endsWith(".json")) {
    //             String key = file.getName().replace("_at_", "@").replace("_dot_", ".");
    //             key = key.substring(0, key.length() - 5); // Remove .json
                
    //             try (FileReader reader = new FileReader(file)) {
    //                 StringBuilder content = new StringBuilder();
    //                 char[] buffer = new char[1024];
    //                 int bytesRead;
    //                 while ((bytesRead = reader.read(buffer)) != -1) {
    //                     content.append(buffer, 0, bytesRead);
    //                 }
                    
    //                 JSONArray messagesArray = new JSONArray(content.toString());
    //                 List<MessageData> messages = new ArrayList<>();
                    
    //                 for (int i = 0; i < messagesArray.length(); i++) {
    //                     JSONObject msgJson = messagesArray.getJSONObject(i);
    //                     String sender = msgJson.getString("sender");
    //                     String msgContent = msgJson.getString("content");
    //                     boolean isPrivate = msgJson.getBoolean("isPrivate");
    //                     boolean isOutgoing = msgJson.getBoolean("isOutgoing");
                        
    //                     messages.add(new MessageData(sender, msgContent, isPrivate, isOutgoing));
    //                 }
                    
    //                 conversationMap.put(key, messages);
    //                 System.out.println("Loaded " + messages.size() + " messages for " + key);
    //             }
    //         }
    //     }
    //     System.out.println("Finished loading conversations from disk");
    // } catch (Exception e) {
    //     System.err.println("Error loading conversations: " + e.getMessage());
    //     e.printStackTrace();
    // }
    conversationMap.clear();
}
private void loadConversationHistory(String contactEmail, VBox container) {
    Label loadingLabel = new Label("Loading conversation history...");
    loadingLabel.setStyle("-fx-text-fill: #757575; -fx-font-style: italic;");
    container.getChildren().add(loadingLabel);
    
    // Don't display local messages, just request from database
    requestConversationHistory(contactEmail);
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
            if (message == null || message.trim().isEmpty()) {
                System.err.println("Received empty message");
                return;
            }

            JSONObject msgJson = new JSONObject(message);
            String type = msgJson.getString("type");
            System.out.println("Processing message of type: " + type);

            switch (type) {
                case "private":
                    String sender = msgJson.getString("sender");
                    String content = msgJson.getString("content");
                    handlePrivateMessage(sender, content);
                    break;
                    
                case "broadcast":
                    sender = msgJson.getString("sender");
                    content = msgJson.getString("content");
                    handleBroadcastMessage(sender, content);
                    break;
                    
                case "history":
                    handleHistoryResponse(msgJson);
                    break;
                    
                case "group_history":
                    handleGroupHistoryResponse(msgJson);
                    break;
                    
                case "group_created":
                    handleGroupCreated(msgJson);
                    break;
                    
                case "error":
                    String errorMsg = msgJson.getString("message");
                    Platform.runLater(() -> addSystemMessage("Error: " + errorMsg));
                    break;
                    
                default:
                    System.err.println("Unknown message type: " + type);
                    break;
            }
        } catch (JSONException e) {
            System.err.println("Error parsing message: " + message);
            e.printStackTrace();
            Platform.runLater(() -> addSystemMessage("Error processing message: " + e.getMessage()));
        } catch (Exception e) {
            System.err.println("Unexpected error handling message: " + message);
            e.printStackTrace();
            Platform.runLater(() -> addSystemMessage("Error: " + e.getMessage()));
        }
    }

    private void handleGroupsListResponse(JSONObject response) {
        try {
            JSONArray groupsArray = response.getJSONArray("groups");
            Set<String> groupNames = new HashSet<>();
            
            for (int i = 0; i < groupsArray.length(); i++) {
                JSONObject groupJson = groupsArray.getJSONObject(i);
                String groupName = groupJson.getString("name");
                groupNames.add(groupName);
                
                // Store group members for later use if needed
                JSONArray membersArray = groupJson.getJSONArray("members");
                List<String> members = new ArrayList<>();
                for (int j = 0; j < membersArray.length(); j++) {
                    members.add(membersArray.getString(j));
                }
                
                System.out.println("Adding group to contacts list: " + groupName);
            }
            
            // Add groups to contacts and refresh the list
            Platform.runLater(() -> {
                contacts.addAll(groupNames);
                refreshContactsList();
                addSystemMessage("Groups loaded: " + groupNames.size());
            });
        } catch (JSONException e) {
            e.printStackTrace();
            addSystemMessage("Error processing groups list: " + e.getMessage());
        }
    }

    private void handleHistoryResponse(JSONObject response) {
        try {
            JSONArray messagesArray = response.getJSONArray("messages");
            System.out.println("Received history with " + messagesArray.length() + " messages");
            
            // Process each message
            for (int i = 0; i < messagesArray.length(); i++) {
                JSONObject messageJson = messagesArray.getJSONObject(i);
                
                // Determine message type
                String messageType = messageJson.optString("type", "text");
                boolean isFileMessage = "file".equals(messageType);
                
                // Get sender
                String sender = messageJson.getString("sender");
                
                // Determine which conversation this belongs to
                String containerKey;
                if (messageJson.has("groupName")) {
                    containerKey = messageJson.getString("groupName");
                } else {
                    // For direct messages, use the other user as key
                    String conversationId = messageJson.optString("conversationId", "");
                    if (sender.equals(userEmail)) {
                        // Outgoing message - recipient is container key
                        // Extract recipient from conversationId
                        if (conversationId.startsWith(userEmail + "_")) {
                            containerKey = conversationId.substring(userEmail.length() + 1);
                        } else if (conversationId.endsWith("_" + userEmail)) {
                            containerKey = conversationId.substring(0, conversationId.length() - userEmail.length() - 1);
                        } else {
                            containerKey = messageJson.optString("recipient", sender);
                        }
                    } else {
                        // Incoming message - sender is container key
                        containerKey = sender;
                    }
                }
                
                // Get or create message container
                VBox container = getOrCreateMessageContainer(containerKey);
                
                final String finalContainerKey = containerKey;
                final JSONObject finalMessageJson = messageJson;
                
                // Display message in UI
                Platform.runLater(() -> {
                    try {
                        if (isFileMessage) {
                            // Handle file message
                            String fileId = finalMessageJson.getString("id");
                            String filename = finalMessageJson.getString("originalFilename");
                            String mimeType = finalMessageJson.getString("mimeType");
                            long fileSize = finalMessageJson.getLong("fileSize");
                            boolean isOutgoing = sender.equals(userEmail);
                            
                            String sizeDisplay = formatFileSize(fileSize);
                            
                            System.out.println("Adding file to UI: " + filename + " (" + sizeDisplay + ")");
                            addFileMessageToContainer(container, sender, fileId, filename, mimeType, sizeDisplay, isOutgoing);
                        } else {
                            // Handle text message
                            String content = finalMessageJson.getString("content");
                            
                            // Handle encrypted messages from history
                            if (finalMessageJson.optBoolean("encrypted", false)) {
                                try {
                                    byte[] encryptedBytes = java.util.Base64.getDecoder().decode(content);
                                    byte[] decryptedBytes = SerpentCipher.decrypt(encryptedBytes);
                                    content = new String(decryptedBytes);
                                } catch (Exception e) {
                                    System.err.println("Error decrypting message: " + e.getMessage());
                                    content = "[Encrypted message - Decryption failed]";
                                }
                            }
                            
                            if (sender.equals(userEmail)) {
                                // Outgoing message
                                addOutgoingMessageToContainer(container, content);
                            } else {
                                // Incoming message
                                addIncomingMessageToContainer(container, sender, content);
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Error displaying message in history: " + e.getMessage());
                    }
                });
            }
            
            // Scroll to bottom after loading all messages
            Platform.runLater(() -> {
                for (VBox container : contactMessageContainers.values()) {
                    if (container.getParent() instanceof ScrollPane) {
                        ScrollPane scrollPane = (ScrollPane) container.getParent();
                        scrollPane.setVvalue(1.0);
                    }
                }
            });
        } catch (Exception e) {
            System.err.println("Error processing history response: " + e.getMessage());
            e.printStackTrace();
        }
    }
    // Helper method to get or create message container
private VBox getOrCreateMessageContainer(String contactKey) {
    if (!contactMessageContainers.containsKey(contactKey)) {
        Tab tab = createContactTab(contactKey);
        if (!contacts.contains(contactKey) && !contactKey.equals("All")) {
            contacts.add(contactKey);
            saveContacts();
            refreshContactsList();
        }
    }
    
    VBox container = contactMessageContainers.get(contactKey);
    container.getChildren().removeIf(node -> 
        node instanceof Label && 
        ((Label)node).getText().equals("Loading conversation history..."));
    
    return container;
}

// Helper method to display messages in container
private void displayMessagesInContainer(VBox container, List<JSONObject> messages) {
    for (JSONObject messageJson : messages) {
        try {
            String sender = messageJson.getString("sender");
            
            if (messageJson.has("content")) {
                // Text message
                String content = messageJson.getString("content");
                if (sender.equals(userEmail)) {
                    addOutgoingMessageToContainer(container, content);
                } else {
                    addIncomingMessageToContainer(container, sender, content);
                }
            } else if (messageJson.has("type") && messageJson.getString("type").equals("file")) {
                // File message
                String fileId = messageJson.getString("id");
                String filename = messageJson.getString("filename");
                String mimeType = messageJson.getString("mimeType");
                long fileSize = messageJson.getLong("fileSize");
                String sizeDisplay = formatFileSize(fileSize);
                
                addFileMessageToContainer(container, sender, fileId, filename, mimeType, sizeDisplay, 
                    sender.equals(userEmail));
            }
        } catch (JSONException e) {
            System.err.println("Error displaying message: " + e.getMessage());
        }
    }
    
    // Scroll to bottom
    ScrollPane scrollPane = (ScrollPane) container.getParent();
    scrollPane.setVvalue(1.0);
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

    private void refreshContactsList() {
        contactsList.getItems().setAll(contacts);
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
        contactsList.getItems().setAll(sortedContacts);
    }

    @FXML
    public void handleDeleteContactButton(ActionEvent event) {
        String selectedContact = contactsList.getSelectionModel().getSelectedItem();
        if (selectedContact != null) {
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

    private void handleGroupCreated(JSONObject groupJson) {
        try {
            String groupName = groupJson.getString("groupName");
            System.out.println("Group created: " + groupName);
            
            Platform.runLater(() -> {
                if (!contacts.contains(groupName)) {
                    contacts.add(groupName);
                    saveContacts(); // Save to local storage
                    refreshContactsList();
                    addSystemMessage("You've been added to group: " + groupName);
                }
            });
        } catch (JSONException e) {
            System.err.println("Error handling group creation: " + e.getMessage());
        }
    }
}
