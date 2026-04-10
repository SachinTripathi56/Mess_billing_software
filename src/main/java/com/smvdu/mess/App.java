package com.smvdu.mess;

import com.smvdu.mess.database.DatabaseConnection;
import com.smvdu.mess.utils.NetworkSessionManager;
import com.smvdu.mess.utils.SessionManager;
import com.smvdu.mess.utils.SyncService;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class App extends Application {

    private static Stage primaryStage;
    private static Thread syncThread;

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;

        // 1. Always bring up SQLite first (offline capable)
        DatabaseConnection.initialize();

        // 2. Background thread: wait for MySQL, then create sessions table,
        //    then run the periodic sync loop
        syncThread = new Thread(() -> {
            try {
                // Small delay for MySQL background connection to finish
                Thread.sleep(4000);

                // Ensure the sessions table exists (safe to call every startup)
                NetworkSessionManager.ensureSessionsTable();

                // Run an immediate sync cycle (will also retry pull if needed)
                System.out.println("🔄 Running initial sync...");
                SyncService.syncAll();

                // Then repeat every 30 seconds
                while (!Thread.currentThread().isInterrupted()) {
                    Thread.sleep(30_000);
                    SyncService.syncAll();
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, "sync-thread");
        syncThread.setDaemon(true);
        syncThread.start();

        // 3. Clean shutdown — release session lock + close DBs
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            SessionManager.logout();
            NetworkSessionManager.stopHeartbeat();
            SyncService.closeConnections();
            DatabaseConnection.closeAllConnections();
        }, "shutdown-hook"));

        // Also handle window X-button
        stage.setOnCloseRequest(e -> SessionManager.logout());

        // 4. Load login screen
        Parent root = FXMLLoader.load(getClass().getResource("/views/login.fxml"));
        Scene scene = new Scene(root, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/styles/styles.css").toExternalForm());

        stage.setTitle("SMVDU Mess Billing System");
        stage.setScene(scene);
        stage.setMinWidth(1000);
        stage.setMinHeight(700);
        stage.show();
    }

    public static void setRoot(String fxml) throws Exception {
        Parent root = FXMLLoader.load(App.class.getResource("/views/" + fxml + ".fxml"));
        primaryStage.getScene().setRoot(root);
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
