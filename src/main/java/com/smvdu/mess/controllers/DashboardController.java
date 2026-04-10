package com.smvdu.mess.controllers;

import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;

import com.smvdu.mess.App;
import com.smvdu.mess.models.User;
import com.smvdu.mess.utils.MessUtils;
import com.smvdu.mess.utils.SessionManager;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class DashboardController {
    
    @FXML private Label welcomeLabel;
    @FXML private Label hostelLabel;
    @FXML private Label totalStudentsLabel;
    @FXML private Label activeStudentsLabel;
    @FXML private Label currentMonthLabel;
    @FXML private Label totalMessDaysLabel;
    @FXML private Label estimatedBillLabel;
    @FXML private VBox statsContainer;
    
    private User currentUser;
    private int messId;
    
    @FXML
    public void initialize() {
        currentUser = SessionManager.getCurrentUser();
        
        if (currentUser == null) {
            try {
                App.setRoot("login");
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }
        
        welcomeLabel.setText("Welcome, " + currentUser.getName());
        
        if (currentUser.getMessName() != null) {
            hostelLabel.setText(currentUser.getMessName());
        } else {
            hostelLabel.setText(currentUser.getHostelName());
        }
        
        messId = MessUtils.getMessIdForHostel(currentUser.getHostelId());
        
        loadDashboardStats();
    }
    
 private void loadDashboardStats() {
    try {
        LocalDate now = LocalDate.now();
        int currentMonth = now.getMonthValue();
        int currentYear = now.getYear();
        
        int operatingDays = MessUtils.getOperatingDays(messId, currentMonth, currentYear);
        
        List<Integer> hostelIds = MessUtils.getHostelIdsForMess(messId);
        
        if (hostelIds.isEmpty()) {
            showAlert("Error", "No hostels found for this mess", Alert.AlertType.ERROR);
            return;
        }
        
        int totalStudents = MessUtils.getTotalStudentCount(hostelIds);
        int activeStudents = MessUtils.getActiveStudentCount(hostelIds);
        
        int totalAbsentDays = MessUtils.getTotalAbsentDays(hostelIds, currentMonth, currentYear);
        
        int totalPossibleDays = activeStudents * operatingDays;
        int netMessDays = totalPossibleDays - totalAbsentDays;
        if (netMessDays < 0) netMessDays = 0;
        
        double perDayRate = MessUtils.getSetting("per_day_rate", 120.0);
        double gstPercent = MessUtils.getSetting("gst_percent", 5.0);
        
        double fineAmount = MessUtils.getFineAmount(messId, currentMonth, currentYear);
        
        double subtotal = netMessDays * perDayRate;
        double gst = subtotal * (gstPercent / 100);
        
        // ✅ CHANGED: Subtract fine instead of add
        double total = subtotal + gst - fineAmount;
        
        totalStudentsLabel.setText(String.valueOf(totalStudents));
        activeStudentsLabel.setText(String.valueOf(activeStudents));
        
        String monthName = Month.of(currentMonth).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
        currentMonthLabel.setText(monthName + " " + currentYear);
        
        totalMessDaysLabel.setText(String.valueOf(operatingDays));
        estimatedBillLabel.setText(String.format("₹%.2f", total));
        
    } catch (Exception e) {
        e.printStackTrace();
        showAlert("Error", "Failed to load dashboard statistics: " + e.getMessage(), Alert.AlertType.ERROR);
    }
}
    
    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    @FXML
    private void openBilling() {
        try {
            App.setRoot("billing");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @FXML
    private void openStudents() {
        try {
            App.setRoot("students");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @FXML
    private void openImport() {
        try {
            App.setRoot("import");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    // ✅ NEW: Open Previous Bills
    @FXML
    private void openPreviousBills() {
        try {
            App.setRoot("previous_bills");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @FXML
    private void handleLogout() {
        SessionManager.logout();
        try {
            App.setRoot("login");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}