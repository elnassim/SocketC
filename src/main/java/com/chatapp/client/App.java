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
            // Load the signup view instead of the login view
            Parent root = FXMLLoader.load(getClass().getResource("/com/chatapp/client/view/signup-view.fxml"));
            primaryStage.setTitle("Chat Client - Sign Up");
            primaryStage.setScene(new Scene(root, 500, 600)); // Adjusted dimensions for the signup page
            primaryStage.setResizable(false);
            primaryStage.show();
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error loading signup view: " + e.getMessage());
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