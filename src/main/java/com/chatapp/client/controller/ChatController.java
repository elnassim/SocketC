package com.chatapp.client.controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.geometry.Insets;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

import java.io.*;
import java.net.Socket;
import java.util.*;

import com.chatapp.common.model.User;
import com.chatapp.client.network.ClientNetworkService;

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
    @FXML private Button createGroupButton; // Button for group creation

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
    
    // Tab management
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
            if (newVal != null) handleContactClick(newVal);
        });

        conversationTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            if (newTab != null) {
                newTab.setStyle("");
            }
        });
    
        // Add a window close handler
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
                if (connected) Platform.runLater(() -> 
                    addSystemMessage("Connection lost: " + e.getMessage()));
            }
        }).start();
    }

    private void addOutgoingMessageToContainer(VBox container, String text) {
        HBox box = new HBox();
        box.setAlignment(Pos.CENTER_RIGHT);
        box.setPadding(new Insets(5, 10, 5, 10));
        
        Label label = new Label("You: " + text);
        label.getStyleClass().add("bubble-right");
        label.setWrapText(true);
        label.setMaxWidth(300);
        
        box.getChildren().add(label);
        container.getChildren().add(box);
    }

    // For one-to-one messages
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
        VBox.setVgrow(scrollPane, javafx.scene.layout.Priority.ALWAYS);
        
        HBox inputArea = new HBox(5);
        inputArea.setSpacing(10);
        inputArea.setPadding(new Insets(10));
        inputArea.setAlignment(Pos.CENTER);
        inputArea.getStyleClass().add("message-input-container");
        
        TextField messageField = new TextField();
        messageField.setPromptText("Type message to " + key + "...");
        messageField.setPrefWidth(400);
        HBox.setHgrow(messageField, javafx.scene.layout.Priority.ALWAYS);
        
        Button sendButton = new Button("Send");
        sendButton.getStyleClass().add("primary-button");
        
        EventHandler<ActionEvent> sendHandler = event -> {
            String msg = messageField.getText().trim();
            if (!msg.isEmpty()) {
                // For group tabs, the key is the group name.
                sendPrivateMessage(key, msg);
                messageField.clear();
                messageField.requestFocus();
            }
        };

        sendButton.setOnAction(sendHandler);
        messageField.setOnAction(sendHandler);

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

    private void loadConversationHistory(String key, VBox container) {
        List<MessageData> history = conversationMap.getOrDefault(key, new ArrayList<>());
        for (MessageData msg : history) {
            if (msg.isOutgoing) {
                addOutgoingMessageToContainer(container, msg.content);
            } else {
                addIncomingMessageToContainer(container, msg.sender, msg.content);
            }
        }
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

    // This method is used for both one-to-one and group messages.
    // When sending to a group, 'recipient' is the group name.
    private void sendPrivateMessage(String recipient, String content) {
        try {
            String messageId = "msg_" + System.currentTimeMillis() + "_" + 
                               Integer.toHexString((int)(Math.random() * 10000));
                
            JSONObject msgObj = new JSONObject();
            msgObj.put("type", "private");
            msgObj.put("to", recipient);
            msgObj.put("content", content);
            msgObj.put("sender", userEmail);
            msgObj.put("id", messageId);
            // No checksum changes here
            out.println(msgObj.toString());
            
            // Store message under the conversation key (recipient or group name)
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
                // Check if this is a group message by looking for "isGroup" flag
                if (msgJson.optBoolean("isGroup", false)) {
                    String groupName = msgJson.getString("groupName");
                    // For group messages, use groupName as the conversation key.
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
            }
            // Handle group_created notification
            else if ("group_created".equals(type)) {
                String groupName = msgJson.getString("groupName");
                contacts.add(groupName);
                saveContacts();
                refreshContactsList();
                addSystemMessage("You have been added to group: " + groupName);
            }
        } catch (JSONException e) {
            addSystemMessage("Invalid message format: " + message);
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
    public void handleAddContactButton(ActionEvent event) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add Contact");
        dialog.setHeaderText("Enter the email address of the contact to add:");
        dialog.setContentText("Email:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(email -> {
            if (email.contains("@") && !email.equals(userEmail)) {
                contacts.add(email);
                saveContacts();
                refreshContactsList();
                addSystemMessage("Contact added: " + email);
            } else {
                addSystemMessage("Invalid email address");
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
            contacts.remove(selectedContact);
            Tab tab = contactTabs.remove(selectedContact);
            if (tab != null) {
                conversationTabPane.getTabs().remove(tab);
            }
            contactMessageContainers.remove(selectedContact);
            saveContacts();
            refreshContactsList();
            addSystemMessage("Contact removed: " + selectedContact);
        } else {
            addSystemMessage("No contact selected");
        }
    }
    
    // *** GROUP FEATURE: Handler for "Create Group" button ***
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
            // Automatically add the creator if not selected
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
    
    private void launchChatUI(String email, Socket socket, BufferedReader in, PrintWriter out) throws IOException {
        throw new UnsupportedOperationException("This method should not be called from ChatController");
    }
}
