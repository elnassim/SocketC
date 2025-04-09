package com.chatapp.client.controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import com.chatapp.common.model.User;
import com.chatapp.data.dao.UserDAO;
import com.chatapp.data.dao.impl.UserDAOImpl;

/**
 * Contrôleur pour la vue profile-view.fxml
 * Permet de modifier le username et le password dans la base de données.
 */
public class ProfileController {

    @FXML
    private Label emailLabel;          // Affiche l'email en lecture seule
    @FXML
    private TextField usernameField;   // Permet de saisir le nouveau username
    @FXML
    private PasswordField passwordField; // Permet de saisir le nouveau password
    @FXML
    private Label statusLabel;         // Affiche le résultat (succès/erreur)

    private User currentUser;
    private UserDAO userDAO = new UserDAOImpl();

    /**
     * Appelée par ChatController pour initialiser les données du profil à partir de l'email.
     */
    public void initData(String userEmail) {
        // Récupère l'utilisateur correspondant en base
        currentUser = userDAO.findByEmail(userEmail);
        
        if (currentUser != null) {
            emailLabel.setText(currentUser.getEmail());  // Affiche l'email actuel
            usernameField.setText(currentUser.getUsername());
            passwordField.setText(currentUser.getPassword());
        } else {
            statusLabel.setText("Utilisateur introuvable en base.");
        }
    }

    /**
     * Enregistre les modifications (username et password) en base de données.
     */
    @FXML
    private void handleSaveProfile() {
        if (currentUser == null) {
            statusLabel.setText("Aucun utilisateur chargé.");
            return;
        }

        // Récupère les nouvelles valeurs
        currentUser.setUsername(usernameField.getText());
        currentUser.setPassword(passwordField.getText());

        // Met à jour l'utilisateur en base
        boolean success = userDAO.update(currentUser);
        // Ou si vous utilisez une méthode dédiée : userDAO.updateUserProfile(currentUser);

        if (success) {
            statusLabel.setText("Profil mis à jour avec succès.");
        } else {
            statusLabel.setText("Erreur lors de la mise à jour du profil.");
        }
    }

    /**
     * Annule l'action et ferme la fenêtre de profil.
     */
    @FXML
    private void handleCancel() {
        Stage stage = (Stage) statusLabel.getScene().getWindow();
        stage.close();
    }
}
