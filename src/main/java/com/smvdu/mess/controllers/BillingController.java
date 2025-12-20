package com.smvdu.mess.controllers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.Locale;

import com.smvdu.mess.App;
import com.smvdu.mess.database.DatabaseConnection;
import com.smvdu.mess.utils.SessionManager;

import javafx.fxml.FXML;
import javafx.print.PageLayout;
import javafx.print.PageOrientation;
import javafx.print.Paper;
import javafx.print.Printer;
import javafx.print.PrinterJob;
import javafx.scene.control.Alert;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.scene.transform.Scale;

public class BillingController {
    
    @FXML private ComboBox<String> monthCombo;
    @FXML private ComboBox<Integer> yearCombo;
    @FXML private Label hostelNameLabel;
    @FXML private Label totalStudentsLabel;
    @FXML private Label totalMessDaysLabel;
    @FXML private Label perDayRateLabel;
    @FXML private Label subtotalLabel;
    @FXML private Label gstPercentLabel;
    @FXML private Label gstAmountLabel;
    @FXML private Label totalAmountLabel;
    @FXML private VBox billPreview;
    @FXML private TextField rateField;
    @FXML private TextField gstField;
    
    private int hostelId;
    private double perDayRate = 120.0;
    private double gstPercent = 5.0;
    
    @FXML
    public void initialize() {
        hostelId = SessionManager.getCurrentHostelId();
        hostelNameLabel.setText(SessionManager.getCurrentUser().getHostelName());
        
        // Populate months
        for (Month month : Month.values()) {
            monthCombo.getItems().add(month.getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        }
        
        // Populate years
        int currentYear = LocalDate.now().getYear();
        for (int y = currentYear - 2; y <= currentYear + 1; y++) {
            yearCombo.getItems().add(y);
        }
        
        // Set current month/year
        monthCombo.setValue(LocalDate.now().getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH));
        yearCombo.setValue(currentYear);
        
        loadSettings();
        rateField.setText(String.valueOf(perDayRate));
        gstField.setText(String.valueOf(gstPercent));
        
        // Auto-generate on selection change
        monthCombo.setOnAction(e -> generateBill());
        yearCombo.setOnAction(e -> generateBill());
        
        generateBill();
    }
    
    private void loadSettings() {
        try {
            Connection conn = DatabaseConnection.getConnection();
            Statement stmt = conn.createStatement();
            
            ResultSet rs = stmt.executeQuery("SELECT value FROM settings WHERE key = 'per_day_rate'");
            if (rs.next()) perDayRate = Double.parseDouble(rs.getString("value"));
            
            rs = stmt.executeQuery("SELECT value FROM settings WHERE key = 'gst_percent'");
            if (rs.next()) gstPercent = Double.parseDouble(rs.getString("value"));
            
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
    
    @FXML
    private void generateBill() {
        try {
            perDayRate = Double.parseDouble(rateField.getText());
            gstPercent = Double.parseDouble(gstField.getText());
        } catch (NumberFormatException e) {
            showAlert("Error", "Please enter valid numbers for rate and GST", Alert.AlertType.ERROR);
            return;
        }
        
        int selectedMonth = monthCombo.getSelectionModel().getSelectedIndex() + 1;
        int selectedYear = yearCombo.getValue();
        int daysInMonth = YearMonth.of(selectedYear, selectedMonth).lengthOfMonth();
        
        try {
            Connection conn = DatabaseConnection.getConnection();
            
            // Get active students count
            PreparedStatement pstmt = conn.prepareStatement(
                "SELECT COUNT(*) FROM students WHERE hostel_id = ? AND is_active = 1"
            );
            pstmt.setInt(1, hostelId);
            ResultSet rs = pstmt.executeQuery();
            int totalStudents = rs.next() ? rs.getInt(1) : 0;
            
            // Get total mess days (considering attendance)
            pstmt = conn.prepareStatement("""
                SELECT COALESCE(SUM(
                    CASE WHEN sa.mess_days IS NOT NULL THEN sa.mess_days ELSE ? END
                ), 0) as total_mess_days
                FROM students s
                LEFT JOIN student_attendance sa ON s.id = sa.student_id 
                    AND sa.month = ? AND sa.year = ?
                WHERE s.hostel_id = ? AND s.is_active = 1
            """);
            pstmt.setInt(1, daysInMonth);
            pstmt.setInt(2, selectedMonth);
            pstmt.setInt(3, selectedYear);
            pstmt.setInt(4, hostelId);
            rs = pstmt.executeQuery();
            
            int totalMessDays = 0;
            if (rs.next()) {
                totalMessDays = rs.getInt("total_mess_days");
            }
            
            // If no records, assume all students ate all days
            if (totalMessDays == 0 && totalStudents > 0) {
                totalMessDays = totalStudents * daysInMonth;
            }
            
            // Calculate bill
            double subtotal = totalMessDays * perDayRate;
            double gstAmount = subtotal * (gstPercent / 100);
            double totalAmount = subtotal + gstAmount;
            
            // Update labels
            totalStudentsLabel.setText(String.valueOf(totalStudents));
            totalMessDaysLabel.setText(String.valueOf(totalMessDays));
            perDayRateLabel.setText(String.format("₹%.2f", perDayRate));
            subtotalLabel.setText(String.format("₹%.2f", subtotal));
            gstPercentLabel.setText(String.format("%.1f%%", gstPercent));
            gstAmountLabel.setText(String.format("₹%.2f", gstAmount));
            totalAmountLabel.setText(String.format("₹%.2f", totalAmount));
            
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Error", "Failed to generate bill", Alert.AlertType.ERROR);
        }
    }
    
    @FXML
    private void saveBill() {
        int selectedMonth = monthCombo.getSelectionModel().getSelectedIndex() + 1;
        int selectedYear = yearCombo.getValue();
        
        try {
            Connection conn = DatabaseConnection.getConnection();
            
            // Parse values from labels
            int totalStudents = Integer.parseInt(totalStudentsLabel.getText());
            int totalMessDays = Integer.parseInt(totalMessDaysLabel.getText());
            double subtotal = parseAmount(subtotalLabel.getText());
            double gstAmount = parseAmount(gstAmountLabel.getText());
            double totalAmount = parseAmount(totalAmountLabel.getText());
            
            PreparedStatement pstmt = conn.prepareStatement("""
                INSERT INTO bills (hostel_id, month, year, total_students, total_mess_days, 
                    per_day_rate, subtotal, gst_percent, gst_amount, total_amount, generated_by)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """);
            
            pstmt.setInt(1, hostelId);
            pstmt.setInt(2, selectedMonth);
            pstmt.setInt(3, selectedYear);
            pstmt.setInt(4, totalStudents);
            pstmt.setInt(5, totalMessDays);
            pstmt.setDouble(6, perDayRate);
            pstmt.setDouble(7, subtotal);
            pstmt.setDouble(8, gstPercent);
            pstmt.setDouble(9, gstAmount);
            pstmt.setDouble(10, totalAmount);
            pstmt.setInt(11, SessionManager.getCurrentUser().getId());
            
            pstmt.executeUpdate();
            
            showAlert("Success", "Bill saved successfully!", Alert.AlertType.INFORMATION);
            
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Error", "Failed to save bill", Alert.AlertType.ERROR);
        }
    }
    
    @FXML
    private void printBill() {
        PrinterJob job = PrinterJob.createPrinterJob();
        if (job != null && job.showPrintDialog(billPreview.getScene().getWindow())) {
            PageLayout pageLayout = job.getPrinter().createPageLayout(
                Paper.A4, PageOrientation.PORTRAIT, Printer.MarginType.DEFAULT
            );
            
            double scaleX = pageLayout.getPrintableWidth() / billPreview.getBoundsInParent().getWidth();
            double scaleY = pageLayout.getPrintableHeight() / billPreview.getBoundsInParent().getHeight();
            double scale = Math.min(scaleX, scaleY);
            
            billPreview.getTransforms().add(new Scale(scale, scale));
            
            boolean success = job.printPage(pageLayout, billPreview);
            if (success) {
                job.endJob();
                showAlert("Success", "Bill printed successfully!", Alert.AlertType.INFORMATION);
            }
            
            billPreview.getTransforms().clear();
        }
    }
    
    private double parseAmount(String text) {
        return Double.parseDouble(text.replace("₹", "").replace(",", "").trim());
    }
    
    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    @FXML
    private void goBack() {
        try {
            App.setRoot("dashboard");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
