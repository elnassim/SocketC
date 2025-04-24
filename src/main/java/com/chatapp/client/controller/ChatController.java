package com.chatapp.client.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.geometry.Insets;
import javafx.geometry.Pos;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.Socket;
import java.net.URL;
import java.util.*;

import com.chatapp.common.model.User;
import com.chatapp.data.dao.ContactDAO;
import com.chatapp.data.dao.impl.ContactDAOImpl;
import com.chatapp.client.network.ClientNetworkService;

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

    /* ---------- Internal Fields ---------- */
    private String userEmail;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private boolean connected = false;
    private HashSet<String> contacts = new HashSet<>();
    // conversationMap keys: for one-to-one, use sender email; for groups, use group name
    private Map<String, List<MessageData>> conversationMap = new HashMap<>();
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
        this.userEmail = email;
        this.socket = socket;
        this.in = in;
        this.out = out;
        this.connected = true;
        
        // Initialize network service
        this.networkService = new ClientNetworkService();
        
        try {
            this.networkService.initRetryMechanism();
        } catch (Exception e) {
            addSystemMessage("Error initializing message retry system: " + e.getMessage());
        }
    
        loadContacts();
        loadGroups(); 
        refreshContactsList();
        addSystemMessage("Connected as " + userEmail);
        startMessageListener();
    }

    private void startMessageListener() {
        new Thread(() -> {
            try {
                String line;
                while (connected && (line = in.readLine()) != null) {
                    final String receivedMsg = line;
                    Platform.runLater(() -> handleIncomingMessage(receivedMsg));
                }
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
        JSONObject request = new JSONObject();
        request.put("type", "get_groups");
        out.println(request.toString());
        System.out.println("Requesting groups list from server");
    } catch (JSONException e) {
        e.printStackTrace();
        addSystemMessage("Error requesting groups: " + e.getMessage());
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
        
        // If this is a group, request conversation history differently
        if (!contactKey.contains("@")) {
            // This is a group
            requestGroupHistory(contactKey);
        } else {
            // This is an individual contact
            requestConversationHistory(contactKey);
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

    private Tab getOrCreateContactTab(String key) {
        if (contactTabs.containsKey(key)) {
            return contactTabs.get(key);
        }
        return createContactTab(key);
    }

    private Tab createContactTab(String key) {
        Tab newTab = new Tab(key);
        VBox conversationView = new VBox();
        conversationView.setSpacing(5);
    
        ScrollPane scrollPane = new ScrollPane();
        VBox messagesBox = new VBox(5);
        messagesBox.setPadding(new Insets(10));
        scrollPane.setContent(messagesBox);
        scrollPane.setFitToWidth(true);
        scrollPane.vvalueProperty().bind(messagesBox.heightProperty());
        scrollPane.getStyleClass().add("messages-scroll-pane");
        messagesBox.getStyleClass().add("messages-container");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        
        HBox inputArea = new HBox(5);
        inputArea.setSpacing(10);
        inputArea.setPadding(new Insets(10));
        inputArea.setAlignment(Pos.CENTER);
        inputArea.getStyleClass().add("message-input-container");
        
        TextField messageField = new TextField();
        messageField.setPromptText("Type message to " + key + "...");
        messageField.setPrefWidth(400);
        HBox.setHgrow(messageField, Priority.ALWAYS);
        
        Button sendButton = new Button("Send");
        sendButton.getStyleClass().add("primary-button");
        
        // Event handler for sending message
        sendButton.setOnAction(event -> {
            String msg = messageField.getText().trim();
            if (!msg.isEmpty()) {
                sendPrivateMessage(key, msg);
                messageField.clear();
                messageField.requestFocus();
            }
        });
        messageField.setOnAction(event -> {
            String msg = messageField.getText().trim();
            if (!msg.isEmpty()) {
                sendPrivateMessage(key, msg);
                messageField.clear();
                messageField.requestFocus();
            }
        });

        inputArea.getChildren().addAll(messageField, sendButton);
        conversationView.getChildren().addAll(scrollPane, inputArea);
        newTab.setContent(conversationView);
        newTab.setClosable(true);

        contactTabs.put(key, newTab);
        contactMessageContainers.put(key, messagesBox);
        
        newTab.setOnClosed(e -> {
            contactTabs.remove(key);
            contactMessageContainers.remove(key);
        });

        loadConversationHistory(key, messagesBox);
        return newTab;
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
            if ("groups_list".equals(type)) {
                handleGroupsListResponse(msgJson);
                return;
            }
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
                System.out.println("Received group_created notification for group: " + groupName);
                
                Platform.runLater(() -> {
                    if (!contacts.contains(groupName)) {
                        contacts.add(groupName);
                        saveContacts();
                        refreshContactsList();
                        addSystemMessage("You have been added to group: " + groupName);
                    }
                });
            }
        } catch (JSONException e) {
            addSystemMessage("Invalid message format: " + message);
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
                
                if (!messageJson.has("sender") || !messageJson.has("content") || 
                    !messageJson.has("type") || !messageJson.has("conversationId")) {
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
                                String content = messageJson.getString("content");
                                if (sender.equals(userEmail)) {
                                    addOutgoingMessageToContainer(container, content);
                                } else {
                                    addIncomingMessageToContainer(container, sender, content);
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
}
