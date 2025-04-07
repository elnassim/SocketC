package com.chatapp.client.controller;

import javafx.fxml.FXML;
import javafx.scene.control.ListView;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextField;
import com.chatapp.client.network.ClientNetworkService;

import java.util.List;

public class CreateGroupController {
    @FXML
    private TextField groupNameField;

    @FXML
    private ListView<String> usersListView; // on va afficher les e-mails

    private ClientNetworkService networkService; // pour contacter le serveur

    // Cette méthode est appelée après le chargement du FXML
    @FXML
    public void initialize() {
        // Autoriser la sélection multiple
        usersListView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Charger la liste de tous les utilisateurs (ou contacts) depuis le serveur
        List<String> allUsers = networkService.getAllUserEmails();
        usersListView.getItems().addAll(allUsers);
    }

    @FXML
    private void onCreateGroup() {
        String groupName = groupNameField.getText();
        List<String> selectedMembers = usersListView.getSelectionModel().getSelectedItems();

        // Envoyer la requête de création de groupe au serveur
        networkService.createGroupOnServer(groupName, selectedMembers);

        // Fermer la fenêtre
        // ...
    }

    public void setNetworkService(ClientNetworkService networkService) {
        this.networkService = networkService;
    }
}
