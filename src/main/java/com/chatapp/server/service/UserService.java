package com.chatapp.server.service;

import com.chatapp.common.model.User;
import com.chatapp.data.dao.UserDAO;
import com.chatapp.data.dao.impl.UserDAOImpl;

public class UserService {
    private final UserDAO userDAO;
    
    public UserService() {
        this.userDAO = new UserDAOImpl();
    }
    
    public boolean authenticateUser(String email, String password) {
        return userDAO.authenticate(email, password);
    }
    
    public boolean registerUser(String username, String password, String email) {
        User newUser = new User(username, password, email);
        return userDAO.create(newUser);
    }
    
    public User findUserByEmail(String email) {
        return userDAO.findByEmail(email);
    }
    
    public boolean userExistsByEmail(String email) {
        return findUserByEmail(email) != null;
    }
    
    /**
     * Met à jour le profil utilisateur en base.
     * Ici, nous supposons que pour la mise à jour de profil, on souhaite actualiser
     * le username (peut servir de displayName) et le profile_image.
     * Pour simplifier, nous utilisons ici newUsername et newPassword (ce dernier pouvant rester inchangé)
     * ainsi que la nouvelle URL de photo.
     */
    public boolean updateUserProfile(String email, String newUsername, String newPassword, String profilePhoto) {
        User user = findUserByEmail(email);
        if (user == null) {
            return false;
        }
        user.setUsername(newUsername);    // On considère displayName = username, pour cet exemple
        user.setPassword(newPassword);      // Vous pouvez modifier selon vos besoins
        user.setProfilePhoto(profilePhoto); // Affecte la nouvelle photo de profil
        return userDAO.updateUserProfile(user);
    }
}
