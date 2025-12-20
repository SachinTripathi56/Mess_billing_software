package com.smvdu.mess.controllers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.TextStyle;
import java.util.Locale;

import com.smvdu.mess.App;
import com.smvdu.mess.database.DatabaseConnection;
import com.smvdu.mess.models.User;
import com.smvdu.mess.utils.SessionManager;

import javafx.fxml.FXML;
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
        hostelLabel.setText(currentUser.getHostelName());
        
        loadDashboardStats();
    }
    
    private void loadDashboardStats() {
        try {
            Connection conn = DatabaseConnection.getConnection();
            int hostelId = currentUser.getHostelId();
            LocalDate now = LocalDate.now();
            int currentMonth = now.getMonthValue();
            int currentYear = now.getYear();
            
            // Total students
            PreparedStatement pstmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM students WHERE hostel_id = ?"
            );
            pstmt.setInt(1, hostelId);
            ResultSet rs = pstmt.executeQuery();
            int totalStudents = rs.next() ? rs.getInt(1) : 0;
            totalStudentsLabel.setText(String.valueOf(totalStudents));
            
            // Active students
            pstmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM students WHERE hostel_id = ? AND is_active = 1"
            );
            pstmt.setInt(1, hostelId);
            rs = pstmt.executeQuery();
            int activeStudents = rs.next() ? rs.getInt(1) : 0;
            activeStudentsLabel.setText(String.valueOf(activeStudents));
            
            // Current month
            String monthName = Month.of(currentMonth).getDisplayName(TextStyle.FULL, Locale.ENGLISH);
            currentMonthLabel.setText(monthName + " " + currentYear);
            
            // Total mess days for current month
            int daysInMonth = now.lengthOfMonth();
            pstmt = conn.prepareStatement("""
                SELECT COALESCE(SUM(sa.mess_days), ?) as total_mess_days
                FROM students s
                LEFT JOIN student_attendance sa ON s.id = sa.student_id 
                    AND sa.month = ? AND sa.year = ?
                WHERE s.hostel_id = ? AND s.is_active = 1
            """);
            pstmt.setInt(1, activeStudents * daysInMonth);
            pstmt.setInt(2, currentMonth);
            pstmt.setInt(3, currentYear);
            pstmt.setInt(4, hostelId);
            rs = pstmt.executeQuery();
            int totalMessDays = rs.next() ? rs.getInt("total_mess_days") : 0;
            
            // If no attendance records, assume all active students eat all days
            if (totalMessDays == 0) {
                totalMessDays = activeStudents * daysInMonth;
            }
            totalMessDaysLabel.setText(String.valueOf(totalMessDays));
            
            // Get per day rate
            pstmt = conn.prepareStatement("SELECT value FROM settings WHERE key = 'per_day_rate'");
            rs = pstmt.executeQuery();
            double perDayRate = rs.next() ? Double.parseDouble(rs.getString("value")) : 120.0;
            
            // Calculate estimated bill
            double subtotal = totalMessDays * perDayRate;
            double gst = subtotal * 0.05;
            double total = subtotal + gst;
            estimatedBillLabel.setText(String.format("₹%.2f", total));
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
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
