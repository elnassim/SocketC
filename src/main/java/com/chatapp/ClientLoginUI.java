package com.chatapp;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Simple UI for client login with email and password
 */
public class ClientLoginUI extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Fixed resource path to use Maven standard directory structure
        Parent root = FXMLLoader.load(getClass().getResource("/com/chatapp/clientLogin.fxml"));
        primaryStage.setTitle("Chat Client - Login");
        primaryStage.setScene(new Scene(root, 350, 250));
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}