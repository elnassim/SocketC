package com.chatapp.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * Main application class for the chat client UI
 */
public class App extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        try {
            // Load the login view
            Parent root = FXMLLoader.load(getClass().getResource("/com/chatapp/client/view/login-view.fxml"));
            primaryStage.setTitle("Chat Client - Login");
            primaryStage.setScene(new Scene(root, 350, 250));
            primaryStage.setResizable(false);
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error loading login view: " + e.getMessage());
        }
    }
    
    /**
     * Main method to launch the application
     * 
     * @param args Command line arguments
     */
    public static void main(String[] args) {
        launch(args);
    }
}