package com.smvdu.mess.controllers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import com.smvdu.mess.App;
import com.smvdu.mess.database.DatabaseConnection;
import com.smvdu.mess.models.GeneratedBill;
import com.smvdu.mess.utils.MessUtils;
import com.smvdu.mess.utils.SessionManager;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public class PreviousBillsController {
    
    @FXML private Label hostelLabel;
    @FXML private FlowPane billsContainer;
    @FXML private ScrollPane scrollPane;
    
    private int messId;
    private String messName;
    
    @FXML
    public void initialize() {
        messId = MessUtils.getMessIdForHostel(SessionManager.getCurrentHostelId());
        messName = SessionManager.getCurrentUser().getMessName() != null
            ? SessionManager.getCurrentUser().getMessName()
            : SessionManager.getCurrentUser().getHostelName();
        
        hostelLabel.setText(messName);
        
        loadPreviousBills();
    }
    
    private void loadPreviousBills() {
        billsContainer.getChildren().clear();
        
        try {
            Connection conn = DatabaseConnection.getConnection();
            
            String query = "SELECT * FROM generated_bills " +
                          "WHERE mess_id = ? " +
                          "ORDER BY year DESC, month DESC";
            
            PreparedStatement pstmt = conn.prepareStatement(query);
            pstmt.setInt(1, messId);
            ResultSet rs = pstmt.executeQuery();
            
            int billCount = 0;
            while (rs.next()) {
                GeneratedBill bill = new GeneratedBill(
                    rs.getInt("id"),
                    rs.getInt("mess_id"),
                    rs.getString("mess_name"),
                    rs.getInt("month"),
                    rs.getInt("year"),
                    rs.getString("bill_period"),
                    rs.getInt("operating_days"),
                    rs.getInt("total_students"),
                    rs.getInt("total_student_days"),
                    rs.getInt("total_absent_days"),
                    rs.getInt("total_mess_days"),
                    rs.getDouble("per_day_rate"),
                    rs.getDouble("subtotal"),
                    rs.getDouble("gst_percent"),
                    rs.getDouble("gst_amount"),
                    rs.getDouble("fine_amount"),
                    rs.getDouble("total_amount"),
                    rs.getString("generated_by"),
                    LocalDateTime.parse(rs.getString("generated_at"), 
                                       DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                );
                
                VBox billCard = createBillCard(bill);
                billsContainer.getChildren().add(billCard);
                billCount++;
            }
            
            if (billCount == 0) {
                Label noDataLabel = new Label("No bills generated yet");
                noDataLabel.setStyle("-fx-font-size: 16px; -fx-text-fill: #999; -fx-padding: 50;");
                billsContainer.getChildren().add(noDataLabel);
            }
            
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Error", "Failed to load bills: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }
    
    private VBox createBillCard(GeneratedBill bill) {
        VBox card = new VBox(12);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPrefWidth(300);
        card.setMinHeight(200);
        card.setPadding(new Insets(20));
        card.setStyle("""
            -fx-background-color: white;
            -fx-background-radius: 10;
            -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 8, 0, 0, 2);
            -fx-border-color: #e0e0e0;
            -fx-border-width: 1;
            -fx-border-radius: 10;
        """);
        
        Label icon = new Label("📄");
        icon.setStyle("-fx-font-size: 40px;");
        
        Label monthLabel = new Label(bill.getMonthYearString());
        monthLabel.setFont(Font.font("System", FontWeight.BOLD, 18));
        monthLabel.setStyle("-fx-text-fill: #1e3a5f;");
        
        Label periodLabel = new Label(bill.getBillPeriod());
        periodLabel.setStyle("-fx-font-size: 12px; -fx-text-fill: #666;");
        
        Separator sep = new Separator();
        sep.setPrefWidth(250);
        
        Label amountLabel = new Label(String.format("₹%.2f", bill.getTotalAmount()));
        amountLabel.setFont(Font.font("System", FontWeight.BOLD, 20));
        amountLabel.setStyle("-fx-text-fill: #2e7d32;");
        
        Label generatedLabel = new Label("Generated by: " + bill.getGeneratedBy());
        generatedLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #999;");
        
        Label dateLabel = new Label(bill.getGeneratedAt().format(
            DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")));
        dateLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #999;");
        
        Button viewBtn = new Button("View Details");
        viewBtn.setStyle("""
            -fx-background-color: #2196F3;
            -fx-text-fill: white;
            -fx-font-weight: bold;
            -fx-padding: 8 20;
            -fx-background-radius: 5;
            -fx-cursor: hand;
        """);
        viewBtn.setOnAction(e -> showBillDetails(bill));
        
        Button deleteBtn = new Button("🗑");
        deleteBtn.setStyle("""
            -fx-background-color: #f44336;
            -fx-text-fill: white;
            -fx-font-weight: bold;
            -fx-padding: 8 12;
            -fx-background-radius: 5;
            -fx-cursor: hand;
        """);
        deleteBtn.setOnAction(e -> deleteBill(bill));
        
        javafx.scene.layout.HBox buttonBox = new javafx.scene.layout.HBox(10);
        buttonBox.setAlignment(Pos.CENTER);
        buttonBox.getChildren().addAll(viewBtn, deleteBtn);
        
        card.getChildren().addAll(icon, monthLabel, periodLabel, sep, 
                                  amountLabel, generatedLabel, dateLabel, buttonBox);
        
        card.setOnMouseEntered(e -> {
            card.setStyle("""
                -fx-background-color: #f8f9fa;
                -fx-background-radius: 10;
                -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.2), 12, 0, 0, 4);
                -fx-border-color: #2196F3;
                -fx-border-width: 2;
                -fx-border-radius: 10;
            """);
        });
        
        card.setOnMouseExited(e -> {
            card.setStyle("""
                -fx-background-color: white;
                -fx-background-radius: 10;
                -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.1), 8, 0, 0, 2);
                -fx-border-color: #e0e0e0;
                -fx-border-width: 1;
                -fx-border-radius: 10;
            """);
        });
        
        return card;
    }
    
    private void showBillDetails(GeneratedBill bill) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Bill Details");
        alert.setHeaderText(bill.getMessName() + " - " + bill.getMonthYearString());
        
        String details = String.format("""
            Bill Period: %s
            Operating Days: %d
            
            Total Students: %d
            Total Student-Days: %d
            Total Absent Days: %d
            Net Mess Days: %d
            
            Per Day Rate: ₹%.2f
            Subtotal: ₹%.2f
            GST (%.1f%%): ₹%.2f
            Fine Amount: ₹%.2f
            
            TOTAL AMOUNT: ₹%.2f
            
            Generated By: %s
            Generated On: %s
            """,
            bill.getBillPeriod(),
            bill.getOperatingDays(),
            bill.getTotalStudents(),
            bill.getTotalStudentDays(),
            bill.getTotalAbsentDays(),
            bill.getTotalMessDays(),
            bill.getPerDayRate(),
            bill.getSubtotal(),
            bill.getGstPercent(),
            bill.getGstAmount(),
            bill.getFineAmount(),
            bill.getTotalAmount(),
            bill.getGeneratedBy(),
            bill.getGeneratedAt().format(DateTimeFormatter.ofPattern("dd MMMM yyyy, hh:mm a"))
        );
        
        alert.setContentText(details);
        alert.getDialogPane().setPrefWidth(500);
        alert.showAndWait();
    }
    
    private void deleteBill(GeneratedBill bill) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Delete Bill");
        confirmAlert.setHeaderText("Delete " + bill.getMonthYearString() + " bill?");
        confirmAlert.setContentText("This action cannot be undone.");
        
        Optional<ButtonType> result = confirmAlert.showAndWait();
        
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                Connection conn = DatabaseConnection.getConnection();
                PreparedStatement pstmt = conn.prepareStatement(
                    "DELETE FROM generated_bills WHERE id = ?"
                );
                pstmt.setInt(1, bill.getId());
                pstmt.executeUpdate();
                
                showAlert("Success", "Bill deleted successfully", Alert.AlertType.INFORMATION);
                loadPreviousBills();
                
            } catch (SQLException e) {
                e.printStackTrace();
                showAlert("Error", "Failed to delete bill: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
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