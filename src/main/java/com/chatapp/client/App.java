package com.chatapp.client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    @Override
    public void start(Stage primaryStage) {
        try {
            /* ---------- 1. Charge le FXML ---------- */
            Parent root = FXMLLoader.load(
                    getClass().getResource(
                            "/com/chatapp/client/view/login-view.fxml"));

            /* ---------- 2. Crée la scène SANS taille fixe ---------- */
            Scene scene = new Scene(root);

            /* ---------- 3. Ajoute la feuille de style globale ---------- */
            scene.getStylesheets().add(
                    getClass().getResource(
                            "/com/chatapp/client/styles.css").toExternalForm());

            /* ---------- 4. Configure le Stage ---------- */
            primaryStage.setTitle("Chat Client – Login");
            primaryStage.setScene(scene);
            primaryStage.sizeToScene();      // adapte la taille au contenu FXML
            primaryStage.setMinWidth(420);   // limites de confort
            primaryStage.setMinHeight(480);
            primaryStage.setResizable(true); // permet à l'utilisateur de l'agrandir
            primaryStage.show();

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error loading login view: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
