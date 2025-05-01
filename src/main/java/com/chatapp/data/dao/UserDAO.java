package com.chatapp.data.dao;

import java.sql.SQLException;
import java.util.List;

import com.chatapp.common.model.User;

public interface UserDAO {
    User findByEmail(String email) throws SQLException;
    boolean authenticate(String email, String password);
    boolean create(User user) throws SQLException;
    List<User> findAll();
    boolean update(User user);
    boolean delete(String email);
    
    // Méthode ajoutée pour mettre à jour le profil de l'utilisateur (username, password et profileImage)
    boolean updateUserProfile(User user) throws SQLException;
}
