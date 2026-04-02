package com.eventregistration;

import com.eventregistration.db.DatabaseConnection;
import com.eventregistration.db.DatabaseInitializer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

/**
 * JavaFX Application entry point.
 */
public class MainApp extends Application {

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Login.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 900, 600);
        scene.getStylesheets().add(getClass().getResource("/css/style.css").toExternalForm());

        primaryStage.setTitle("Event Registration & Allocation System");
        primaryStage.setScene(scene);
        primaryStage.setResizable(true);
        primaryStage.show();
    }

    @Override
    public void stop() {
        // Close DB connection on app exit
        DatabaseConnection.getInstance().closeConnection();
        Platform.exit();
    }

    public static void main(String[] args) {
        // Initialize database schema on app startup
        DatabaseInitializer.initialize();
        launch(args);
    }
}
