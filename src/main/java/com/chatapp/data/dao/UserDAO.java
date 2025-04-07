package com.chatapp.data.dao;

import com.chatapp.common.model.User;
import java.util.List;

public interface UserDAO {
    User findByEmail(String email);
    boolean authenticate(String email, String password);
    boolean create(User user);
    List<User> findAll();
    boolean update(User user);
    boolean delete(String email);
}
