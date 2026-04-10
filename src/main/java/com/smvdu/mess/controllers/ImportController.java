package com.smvdu.mess.controllers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.smvdu.mess.App;
import com.smvdu.mess.database.DatabaseConnection;
import com.smvdu.mess.utils.SessionManager;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.TextArea;
import javafx.stage.FileChooser;

public class ImportController {
    
    @FXML private Label fileLabel;
    @FXML private TextArea logArea;
    @FXML private ProgressBar progressBar;
    @FXML private Label hostelLabel;
    
    private File selectedFile;
    private int hostelId;
    
    @FXML
    public void initialize() {
        hostelId = SessionManager.getCurrentHostelId();
        hostelLabel.setText(SessionManager.getCurrentUser().getHostelName());
        progressBar.setProgress(0);
    }
    
    @FXML
    private void selectFile() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Excel File");
        fileChooser.getExtensionFilters().addAll(
            new FileChooser.ExtensionFilter("Excel Files", "*.xlsx", "*.xls"),
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        
        selectedFile = fileChooser.showOpenDialog(fileLabel.getScene().getWindow());
        
        if (selectedFile != null) {
            fileLabel.setText(selectedFile.getName());
            log("File selected: " + selectedFile.getName());
        }
    }
    
    @FXML
    private void importData() {
        if (selectedFile == null) {
            showAlert("Error", "Please select a file first", Alert.AlertType.ERROR);
            return;
        }
        
        try {
            if (selectedFile.getName().endsWith(".csv")) {
                importCSV();
            } else {
                importExcel();
            }
        } catch (Exception e) {
            e.printStackTrace();
            log("ERROR: " + e.getMessage());
            showAlert("Error", "Import failed: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }
    
    private void importExcel() throws Exception {
        try (FileInputStream fis = new FileInputStream(selectedFile);
             Workbook workbook = new XSSFWorkbook(fis)) {
            
            Sheet sheet = workbook.getSheetAt(0);
            
            int totalRows = sheet.getPhysicalNumberOfRows() - 1;
            int imported = 0;
            int updated = 0;
            int skipped = 0;
            
            Connection conn = DatabaseConnection.getConnection();
            
            PreparedStatement checkStmt = conn.prepareStatement(
                "SELECT id FROM students WHERE entry_number = ?"
            );
            
            // ✅ FIXED: Always set is_active = 1 for ALL students
            PreparedStatement insertStmt = conn.prepareStatement("""
                INSERT INTO students (entry_number, name, hostel_id, room_number, phone, email, is_active)
                VALUES (?, ?, ?, ?, ?, ?, 1)
            """);
            
            PreparedStatement updateStmt = conn.prepareStatement("""
                UPDATE students 
                SET name = ?, hostel_id = ?, room_number = ?, phone = ?, email = ?, is_active = 1
                WHERE entry_number = ?
            """);
            
            log("========================================");
            log("Starting import of " + totalRows + " records...");
            log("========================================\n");
            
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null) {
                    log("Row " + (i + 1) + ": Empty row, skipping");
                    skipped++;
                    continue;
                }
                
                try {
                    String entryNumber = getCellValue(row.getCell(0)).trim();
                    String name = getCellValue(row.getCell(1)).trim();
                    String roomNumber = getCellValue(row.getCell(2)).trim();
                    String phone = getCellValue(row.getCell(3)).trim();
                    String email = getCellValue(row.getCell(4)).trim();
                    
                    // Skip completely empty rows
                    if (entryNumber.isEmpty() && name.isEmpty()) {
                        log("Row " + (i + 1) + ": Empty row, skipping");
                        skipped++;
                        continue;
                    }
                    
                    // Validate entry number
                    if (entryNumber.isEmpty()) {
                        log("⚠ Row " + (i + 1) + ": SKIPPED - Missing entry number");
                        skipped++;
                        continue;
                    }
                    
                    // Validate name
                    if (name.isEmpty()) {
                        log("⚠ Row " + (i + 1) + ": SKIPPED - Missing name (Entry: " + entryNumber + ")");
                        skipped++;
                        continue;
                    }
                    
                    // Check if student exists
                    checkStmt.setString(1, entryNumber);
                    ResultSet rs = checkStmt.executeQuery();
                    
                    if (rs.next()) {
                        // UPDATE existing student (and set to active)
                        updateStmt.setString(1, name);
                        updateStmt.setInt(2, hostelId);
                        updateStmt.setString(3, roomNumber);
                        updateStmt.setString(4, phone);
                        updateStmt.setString(5, email);
                        updateStmt.setString(6, entryNumber);
                        updateStmt.executeUpdate();
                        
                        log("✓ UPDATED: " + entryNumber + " - " + name + " [ACTIVE]");
                        updated++;
                    } else {
                        // INSERT new student (as active)
                        insertStmt.setString(1, entryNumber);
                        insertStmt.setString(2, name);
                        insertStmt.setInt(3, hostelId);
                        insertStmt.setString(4, roomNumber);
                        insertStmt.setString(5, phone);
                        insertStmt.setString(6, email);
                        insertStmt.executeUpdate();
                        
                        log("✓ IMPORTED: " + entryNumber + " - " + name + " [ACTIVE]");
                        imported++;
                    }
                    
                    progressBar.setProgress((double) i / totalRows);
                    
                } catch (Exception e) {
                    log("✗ Row " + (i + 1) + " ERROR: " + e.getMessage());
                    skipped++;
                }
            }
            
            log("\n========================================");
            log("IMPORT COMPLETE!");
            log("========================================");
            log("✓ New students imported: " + imported);
            log("✓ Existing students updated: " + updated);
            log("✗ Rows skipped: " + skipped);
            log("========================================");
            log("Total students in database: " + (imported + updated));
            
            showAlert("Success", 
                     "Import Complete!\n\n" +
                     "✓ New Students: " + imported + "\n" +
                     "✓ Updated: " + updated + "\n" +
                     "✗ Skipped: " + skipped + "\n\n" +
                     "All students are now ACTIVE in the database.", 
                     Alert.AlertType.INFORMATION);
        }
    }
    
    private void importCSV() throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(selectedFile))) {
            String line;
            int lineNum = 0;
            int imported = 0;
            int updated = 0;
            int skipped = 0;
            
            Connection conn = DatabaseConnection.getConnection();
            
            PreparedStatement checkStmt = conn.prepareStatement(
                "SELECT id FROM students WHERE entry_number = ?"
            );
            
            PreparedStatement insertStmt = conn.prepareStatement("""
                INSERT INTO students (entry_number, name, hostel_id, room_number, phone, email, is_active)
                VALUES (?, ?, ?, ?, ?, ?, 1)
            """);
            
            PreparedStatement updateStmt = conn.prepareStatement("""
                UPDATE students 
                SET name = ?, hostel_id = ?, room_number = ?, phone = ?, email = ?, is_active = 1
                WHERE entry_number = ?
            """);
            
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (lineNum == 1) continue; // Skip header
                
                try {
                    String[] parts = line.split(",");
                    if (parts.length < 2) {
                        skipped++;
                        continue;
                    }
                    
                    String entryNumber = parts[0].trim();
                    String name = parts[1].trim();
                    
                    if (entryNumber.isEmpty() || name.isEmpty()) {
                        skipped++;
                        continue;
                    }
                    
                    checkStmt.setString(1, entryNumber);
                    ResultSet rs = checkStmt.executeQuery();
                    
                    if (rs.next()) {
                        updateStmt.setString(1, name);
                        updateStmt.setInt(2, hostelId);
                        updateStmt.setString(3, parts.length > 2 ? parts[2].trim() : "");
                        updateStmt.setString(4, parts.length > 3 ? parts[3].trim() : "");
                        updateStmt.setString(5, parts.length > 4 ? parts[4].trim() : "");
                        updateStmt.setString(6, entryNumber);
                        updateStmt.executeUpdate();
                        updated++;
                    } else {
                        insertStmt.setString(1, entryNumber);
                        insertStmt.setString(2, name);
                        insertStmt.setInt(3, hostelId);
                        insertStmt.setString(4, parts.length > 2 ? parts[2].trim() : "");
                        insertStmt.setString(5, parts.length > 3 ? parts[3].trim() : "");
                        insertStmt.setString(6, parts.length > 4 ? parts[4].trim() : "");
                        insertStmt.executeUpdate();
                        imported++;
                    }
                } catch (Exception e) {
                    log("Error on line " + lineNum + ": " + e.getMessage());
                    skipped++;
                }
            }
            
            log("\nImport complete!");
            log("New: " + imported + ", Updated: " + updated + ", Skipped: " + skipped);
            showAlert("Success", 
                     "Imported " + imported + " new, Updated " + updated + "!\nAll students are ACTIVE.", 
                     Alert.AlertType.INFORMATION);
        }
    }
    
    private String getCellValue(Cell cell) {
        if (cell == null) return "";
        
        try {
            return switch (cell.getCellType()) {
                case STRING -> cell.getStringCellValue().trim();
                case NUMERIC -> {
                    double numericValue = cell.getNumericCellValue();
                    // Check if it's a whole number
                    if (numericValue == (long) numericValue) {
                        yield String.valueOf((long) numericValue);
                    } else {
                        yield String.valueOf(numericValue);
                    }
                }
                case BLANK -> "";
                case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
                case FORMULA -> {
                    try {
                        yield String.valueOf((long) cell.getNumericCellValue());
                    } catch (Exception e) {
                        yield cell.getStringCellValue().trim();
                    }
                }
                default -> "";
            };
        } catch (Exception e) {
            return "";
        }
    }
    
    private void log(String message) {
        logArea.appendText(message + "\n");
        System.out.println(message); // Also print to console for debugging
    }
    
    private void showAlert(String title, String message, Alert.AlertType type) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
    
    @FXML
    private void downloadTemplate() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Template");
        fileChooser.setInitialFileName("students_template.csv");
        fileChooser.getExtensionFilters().add(
            new FileChooser.ExtensionFilter("CSV Files", "*.csv")
        );
        
        File file = fileChooser.showSaveDialog(fileLabel.getScene().getWindow());
        if (file != null) {
            try (PrintWriter writer = new PrintWriter(file)) {
                writer.println("entry_number,name,room_number,phone,email");
                writer.println("2021BCE001,John Doe,A-101,9876543210,john@example.com");
                writer.println("2021BCE002,Jane Smith,A-102,9876543211,jane@example.com");
                showAlert("Success", "Template saved!", Alert.AlertType.INFORMATION);
            } catch (Exception e) {
                showAlert("Error", "Failed to save template", Alert.AlertType.ERROR);
            }
        }
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