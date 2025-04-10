package com.chatapp.client.network;

public class UserStatus {
    private String email;
    private String status; // "online" ou "offline"

    public UserStatus(String email, String status) {
         this.email = email;
         this.status = status;
    }

    public String getEmail() {
         return email;
    }

    public String getStatus() {
         return status;
    }
}
