package com.smvdu.mess.controllers;

import com.smvdu.mess.App;
import com.smvdu.mess.utils.EmailService;
import com.smvdu.mess.utils.OTPService;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class ForgotPasswordController {
    
    @FXML private TextField emailField;
    @FXML private Button sendOTPButton;
    @FXML private VBox otpSection;
    @FXML private TextField otpField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button resetPasswordButton;
    @FXML private Label statusLabel;
    
    private String currentEmail;
    private boolean isAdmin = false;
    
    @FXML
    public void initialize() {
        otpSection.setVisible(false);
        otpSection.setManaged(false);
    }
    
    @FXML
    private void handleSendOTP() {
        String email = emailField.getText().trim();
        
        if (email.isEmpty()) {
            showAlert("Error", "Please enter your email address", Alert.AlertType.ERROR);
            return;
        }
        
        // Validate email format
        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            showAlert("Error", "Please enter a valid email address", Alert.AlertType.ERROR);
            return;
        }
        
        sendOTPButton.setDisable(true);
        statusLabel.setText("Sending OTP...");
        
        // Check if admin or user based on email domain or pattern
        isAdmin = email.contains("vc.") || email.contains("dean.") || email.contains("registrar");
        
        // Generate OTP in background thread
        new Thread(() -> {
            String otp;
            
            if (isAdmin) {
                otp = OTPService.generateAndStoreOTPForAdmin(email);
            } else {
                otp = OTPService.generateAndStoreOTPForUser(email);
            }
            
            if (otp == null) {
                javafx.application.Platform.runLater(() -> {
                    statusLabel.setText("Email not found in system");
                    statusLabel.setStyle("-fx-text-fill: red;");
                    sendOTPButton.setDisable(false);
                    showAlert("Error", "Email address not found in the system", Alert.AlertType.ERROR);
                });
                return;
            }
            
            // Send email
            boolean emailSent = EmailService.sendOTPEmail(email, otp);
            
            javafx.application.Platform.runLater(() -> {
                if (emailSent) {
                    currentEmail = email;
                    statusLabel.setText("OTP sent to " + email);
                    statusLabel.setStyle("-fx-text-fill: green;");
                    
                    // Show OTP verification section
                    otpSection.setVisible(true);
                    otpSection.setManaged(true);
                    
                    emailField.setDisable(true);
                    
                    showAlert("Success", 
                             "OTP has been sent to your email.\n\nPlease check your inbox (and spam folder).\n\nOTP is valid for 5 minutes.", 
                             Alert.AlertType.INFORMATION);
                } else {
                    statusLabel.setText("Failed to send OTP. Check email configuration.");
                    statusLabel.setStyle("-fx-text-fill: red;");
                    sendOTPButton.setDisable(false);
                    
                    showAlert("Error", 
                             "Failed to send OTP email.\n\nPlease contact administrator or check your internet connection.", 
                             Alert.AlertType.ERROR);
                }
            });
        }).start();
    }
    
    @FXML
    private void handleResetPassword() {
        String otp = otpField.getText().trim();
        String newPassword = newPasswordField.getText();
        String confirmPassword = confirmPasswordField.getText();
        
        // Validation
        if (otp.isEmpty()) {
            showAlert("Error", "Please enter the OTP", Alert.AlertType.ERROR);
            return;
        }
        
        if (otp.length() != 6 || !otp.matches("\\d+")) {
            showAlert("Error", "OTP must be 6 digits", Alert.AlertType.ERROR);
            return;
        }
        
        if (newPassword.isEmpty()) {
            showAlert("Error", "Please enter a new password", Alert.AlertType.ERROR);
            return;
        }
        
        if (newPassword.length() < 6) {
            showAlert("Error", "Password must be at least 6 characters long", Alert.AlertType.ERROR);
            return;
        }
        
        if (!newPassword.equals(confirmPassword)) {
            showAlert("Error", "Passwords do not match", Alert.AlertType.ERROR);
            return;
        }
        
        resetPasswordButton.setDisable(true);
        statusLabel.setText("Resetting password...");
        
        // Verify OTP and reset password
        String result;
        if (isAdmin) {
            result = OTPService.verifyAndResetPasswordForAdmin(currentEmail, otp, newPassword);
        } else {
            result = OTPService.verifyAndResetPasswordForUser(currentEmail, otp, newPassword);
        }
        
        if ("SUCCESS".equals(result)) {
            statusLabel.setText("Password reset successful!");
            statusLabel.setStyle("-fx-text-fill: green;");
            
            showAlert("Success", 
                     "Your password has been reset successfully!\n\nYou can now login with your new password.", 
                     Alert.AlertType.INFORMATION);
            
            // Go back to login
            try {
                App.setRoot("login");
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            statusLabel.setText(result);
            statusLabel.setStyle("-fx-text-fill: red;");
            resetPasswordButton.setDisable(false);
            
            showAlert("Error", result, Alert.AlertType.ERROR);
        }
    }
    
    @FXML
    private void handleResendOTP() {
        otpField.clear();
        newPasswordField.clear();
        confirmPasswordField.clear();
        handleSendOTP();
    }
    
    @FXML
    private void goBack() {
        try {
            App.setRoot("login");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
}