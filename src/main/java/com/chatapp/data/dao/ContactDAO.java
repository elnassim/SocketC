package com.chatapp.data.dao;

import java.util.List;

public interface ContactDAO {
    List<String> getContacts(String userEmail);
    boolean addContact(String userEmail, String contactEmail);
    boolean removeContact(String userEmail, String contactEmail);
}