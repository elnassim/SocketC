package com.chatapp.common.model;

/**
 * User model shared between client and server
 */
public class User {
    private String username;
    private String password;
    private String email;
    
    // Nouveaux attributs pour la gestion du profil (ici, non modifiables via l'interface de mise à jour)
    private String displayName;
    private String status;

    // No-args constructor
    public User() {
        this.email = "";
        this.username = "";
        this.password = "";
        this.displayName = "";
        this.status = "";
    }

    // Constructeur existant : initialisation des nouveaux champs avec des valeurs par défaut
    public User(String username, String password, String email) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.displayName = username; // Par défaut, le nom affiché est le username
        this.status = "";            // Par défaut, aucun statut
    }

    public User(String username, String password) {
        this(username, password, "");
    }
    
    // Constructeur complet (utilisé en interne, mais non persisté en DB)
    public User(String username, String password, String email, String displayName, String status) {
        this.username = username;
        this.password = password;
        this.email = email;
        this.displayName = displayName;
        this.status = status;
    }

    // Getters et setters existants
    public String getUsername() {
        return username;
    }
    public void setUsername(String username) {
        this.username = username;
    }
    public String getPassword() {
        return password;
    }
    public void setPassword(String password) {
        this.password = password;
    }
    public String getEmail() {
        return email;
    }
    public void setEmail(String email) {
        this.email = email;
    }
    
    // Nouveaux getters et setters pour le profil
    public String getDisplayName() {
        return displayName;
    }
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }
    public String getStatus() {
        return status;
    }
    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "User{username='" + username + "', email='" + email +
                "', displayName='" + displayName + "', status='" + status + "'}";
    }
}
