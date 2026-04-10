package com.smvdu.mess.controllers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.smvdu.mess.App;
import com.smvdu.mess.database.DatabaseConnection;
import com.smvdu.mess.models.User;
import com.smvdu.mess.utils.AdminSessionManager;
import com.smvdu.mess.utils.SessionManager;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginController {
    
    @FXML private TextField emailField;
    @FXML private PasswordField passwordField;
    @FXML private Hyperlink forgotPasswordLink;
    
    @FXML
    public void initialize() {
        DatabaseConnection.initialize();
    }
    
    @FXML
    private void handleLogin() {
        String email = emailField.getText().trim();
        String password = passwordField.getText();
        
        if (email.isEmpty() || password.isEmpty()) {
            showAlert("Error", "Please enter both email and password", Alert.AlertType.ERROR);
            return;
        }
        
        // Check if admin login
        if (isAdminEmail(email)) {
            loginAsAdmin(email, password);
        } else {
            loginAsCaretaker(email, password);
        }
    }
    
    private boolean isAdminEmail(String email) {
        return email.contains("vc.") || email.contains("dean.") || email.contains("registrar");
    }
    
    private void loginAsCaretaker(String email, String password) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(
                "SELECT u.*, h.name as hostel_name, h.mess_name " +
                "FROM users u " +
                "JOIN hostels h ON u.hostel_id = h.id " +
                "WHERE u.email = ? AND u.password = ?"
            );
            pstmt.setString(1, email);
            pstmt.setString(2, password);
            
            ResultSet rs = pstmt.executeQuery();
            
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
                showAlert("Login Failed", "Invalid email or password", Alert.AlertType.ERROR);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Login failed: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }
    
    private void loginAsAdmin(String email, String password) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(
                "SELECT * FROM admins WHERE email = ? AND password = ?"
            );
            pstmt.setString(1, email);
            pstmt.setString(2, password);
            
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                String adminName = rs.getString("name");
                String designation = rs.getString("designation");
                
                AdminSessionManager.setAdminInfo(adminName, designation);
                
                FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/admin_dashboard.fxml")
                );
                Parent root = loader.load();
                
                AdminDashboardController controller = loader.getController();
                controller.setAdminInfo(adminName, designation);
                
                App.getPrimaryStage().getScene().setRoot(root);
            } else {
                showAlert("Login Failed", "Invalid admin credentials", Alert.AlertType.ERROR);
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Admin login failed: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }
    
    // ✅ NEW: Handle Forgot Password
    @FXML
    private void handleForgotPassword() {
        try {
            App.setRoot("forgot_password");
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Failed to open forgot password page", Alert.AlertType.ERROR);
        }
    }
    
    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}