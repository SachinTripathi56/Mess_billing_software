package com.smvdu.mess.controllers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.smvdu.mess.App;
import com.smvdu.mess.database.DatabaseConnection;
import com.smvdu.mess.models.User;
import com.smvdu.mess.utils.SessionManager;

import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class LoginController {

    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label errorLabel;
    @FXML
    private Button loginButton;
    @FXML
    private VBox loginBox;

    @FXML
    public void initialize() {
        errorLabel.setVisible(false);

        // Enter key to login
        passwordField.setOnAction(e -> handleLogin());
    }

@FXML
private void handleLogin() {
    String email = emailField.getText().trim();
    String password = passwordField.getText();
    
    if (email.isEmpty() || password.isEmpty()) {
        showError("Please enter both email and password");
        return;
    }
    
    try {
        Connection conn = DatabaseConnection.getConnection();
        
        // Check if admin FIRST
        PreparedStatement pstmt = conn.prepareStatement(
            "SELECT id, email, name, designation FROM admins WHERE email = ? AND password = ?"
        );
        pstmt.setString(1, email);
        pstmt.setString(2, password);
        ResultSet rs = pstmt.executeQuery();
        
  if (rs.next()) {
    // Admin login detected
    String adminName = rs.getString("name");
    String designation = rs.getString("designation");
    
    System.out.println("Admin login: " + adminName + " (" + designation + ")");
    
    // Save admin session
    com.smvdu.mess.utils.AdminSessionManager.setAdminInfo(adminName, designation);
    
    javafx.fxml.FXMLLoader loader = new javafx.fxml.FXMLLoader(
        getClass().getResource("/views/admin_dashboard.fxml")
    );
    
    javafx.scene.Parent root = loader.load();
    AdminDashboardController controller = loader.getController();
    controller.setAdminInfo(adminName, designation);
    
    App.getPrimaryStage().getScene().setRoot(root);
    return;
}

        
        // Check if caretaker
        String query = """
            SELECT u.id, u.email, u.name, u.hostel_id, h.name as hostel_name, h.mess_name
            FROM users u 
            JOIN hostels h ON u.hostel_id = h.id 
            WHERE u.email = ? AND u.password = ?
        """;
        
        pstmt = conn.prepareStatement(query);
        pstmt.setString(1, email);
        pstmt.setString(2, password);
        
        rs = pstmt.executeQuery();
        
        if (rs.next()) {
            User user = new User(
                rs.getInt("id"),
                rs.getString("email"),
                rs.getString("name"),
                rs.getInt("hostel_id"),
                rs.getString("hostel_name"),
                rs.getString("mess_name")
            );
            
            SessionManager.setCurrentUser(user);
            App.setRoot("dashboard");
        } else {
            showError("Invalid email or password");
        }
        
    } catch (Exception e) {
        e.printStackTrace();
        showError("Login failed: " + e.getMessage());
    }
}



    private void showError(String message) {
        errorLabel.setText(message);
        errorLabel.setVisible(true);
    }
}
