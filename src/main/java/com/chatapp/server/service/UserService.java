// filepath: src/main/java/com/chatapp/server/service/UserService.java
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
}