package com.smvdu.mess;

import com.smvdu.mess.database.DatabaseConnection;
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

    DatabaseConnection.initialize();
   
    syncThread = new Thread(() -> {
        try {
            // ✅ Small delay just for MySQL background thread to finish connecting
            Thread.sleep(4000);

            // ✅ Run immediately on startup, don't wait 30 seconds
            System.out.println("🔄 Running initial sync...");
            SyncService.syncAll();

            // ✅ Then repeat every 30 seconds
            while (!Thread.currentThread().isInterrupted()) {
                Thread.sleep(30000);
                SyncService.syncAll();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            e.printStackTrace();
        }
    });
    syncThread.setDaemon(true);
    syncThread.start();

    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        SyncService.closeConnections();
        DatabaseConnection.closeAllConnections();
    }));

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