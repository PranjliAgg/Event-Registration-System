package com.eventregistration.controller;

import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class SceneManager {

    public static void loadScene(Stage stage, String resourcePath, String title, double width, double height) throws Exception {
        Parent root = FXMLLoader.load(SceneManager.class.getResource(resourcePath));
        loadScene(stage, root, title, width, height);
    }

    public static void loadScene(Stage stage, Parent root, String title, double width, double height) {
        Scene scene = new Scene(root, width, height);
        scene.getStylesheets().add(SceneManager.class.getResource("/css/style.css").toExternalForm());
        stage.setScene(scene);
        if (title != null && !title.isEmpty()) {
            stage.setTitle(title);
        }
    }
}
