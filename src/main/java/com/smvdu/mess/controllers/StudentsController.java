package com.smvdu.mess.controllers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

import com.smvdu.mess.App;
import com.smvdu.mess.database.DatabaseConnection;
import com.smvdu.mess.models.Student;
import com.smvdu.mess.utils.SessionManager;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;

public class StudentsController {
    
    @FXML private TableView<Student> studentsTable;
    @FXML private TableColumn<Student, String> entryNumberCol;
    @FXML private TableColumn<Student, String> nameCol;
    @FXML private TableColumn<Student, String> roomCol;
    @FXML private TableColumn<Student, Integer> messDaysCol;
    @FXML private TableColumn<Student, Integer> absentDaysCol;
    @FXML private TableColumn<Student, Void> actionCol;
    @FXML private TextField searchField;
    @FXML private Label hostelLabel;
    @FXML private Label totalLabel;
    
    private ObservableList<Student> studentsList = FXCollections.observableArrayList();
    private int hostelId;
    
    @FXML
    public void initialize() {
        hostelId = SessionManager.getCurrentHostelId();
        
        // Display mess name
        if (SessionManager.getCurrentUser().getMessName() != null) {
            hostelLabel.setText(SessionManager.getCurrentUser().getMessName());
        } else {
            hostelLabel.setText(SessionManager.getCurrentUser().getHostelName());
        }
        
        setupTable();
        loadStudents();
        
        searchField.textProperty().addListener((obs, old, newVal) -> filterStudents(newVal));
    }
    
    private void setupTable() {
        entryNumberCol.setCellValueFactory(new PropertyValueFactory<>("entryNumber"));
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        roomCol.setCellValueFactory(new PropertyValueFactory<>("roomNumber"));
        messDaysCol.setCellValueFactory(new PropertyValueFactory<>("messDays"));
        absentDaysCol.setCellValueFactory(new PropertyValueFactory<>("absentDays"));
        
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button editBtn = new Button("Edit");
            
            {
                editBtn.getStyleClass().add("edit-button");
                editBtn.setOnAction(e -> {
                    Student student = getTableView().getItems().get(getIndex());
                    showEditDialog(student);
                });
            }
            
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : editBtn);
            }
        });
        
        studentsTable.setItems(studentsList);
    }
    
    private void loadStudents() {
    studentsList.clear();
    LocalDate now = LocalDate.now();
    int daysInMonth = now.lengthOfMonth();
    
    try {
        Connection conn = DatabaseConnection.getConnection();
        
        // Get mess_id for current hostel
        PreparedStatement pstmt = conn.prepareStatement("SELECT mess_id FROM hostels WHERE id = ?");
        pstmt.setInt(1, hostelId);
        ResultSet rs = pstmt.executeQuery();
        int messId = rs.next() ? rs.getInt("mess_id") : hostelId;
        
        // Get all hostel IDs that share this mess
        pstmt = conn.prepareStatement("SELECT id FROM hostels WHERE mess_id = ?");
        pstmt.setInt(1, messId);
        rs = pstmt.executeQuery();
        
        StringBuilder hostelIds = new StringBuilder();
        while (rs.next()) {
            if (hostelIds.length() > 0) hostelIds.append(",");
            hostelIds.append(rs.getInt("id"));
        }
        
        // Load students from ALL hostels in this mess
        String studentQuery = "SELECT s.*, " +
                             "COALESCE(sa.mess_days, ?) as mess_days, " +
                             "COALESCE(sa.absent_days, 0) as absent_days " +
                             "FROM students s " +
                             "LEFT JOIN student_attendance sa ON s.id = sa.student_id " +
                             "AND sa.month = ? AND sa.year = ? " +
                             "WHERE s.hostel_id IN (" + hostelIds + ") AND s.is_active = 1 " +
                             "ORDER BY s.entry_number";
        
        pstmt = conn.prepareStatement(studentQuery);
        pstmt.setInt(1, daysInMonth);
        pstmt.setInt(2, now.getMonthValue());
        pstmt.setInt(3, now.getYear());
        
        rs = pstmt.executeQuery();
        
        while (rs.next()) {
            Student student = new Student(
                rs.getInt("id"),
                rs.getString("entry_number"),
                rs.getString("name"),
                rs.getInt("hostel_id"),
                rs.getString("room_number"),
                rs.getString("phone"),
                rs.getString("email"),
                rs.getInt("is_active") == 1
            );
            student.setMessDays(rs.getInt("mess_days"));
            student.setAbsentDays(rs.getInt("absent_days"));
            studentsList.add(student);
        }
        
        totalLabel.setText("Total: " + studentsList.size() + " students");
        
    } catch (SQLException e) {
        e.printStackTrace();
    }
}

    
    private void filterStudents(String searchText) {
        if (searchText == null || searchText.isEmpty()) {
            studentsTable.setItems(studentsList);
        } else {
            ObservableList<Student> filtered = studentsList.filtered(s ->
                s.getName().toLowerCase().contains(searchText.toLowerCase()) ||
                s.getEntryNumber().toLowerCase().contains(searchText.toLowerCase())
            );
            studentsTable.setItems(filtered);
        }
    }
    
    private void showEditDialog(Student student) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Mess Days - " + student.getName());
        dialog.setHeaderText("Entry: " + student.getEntryNumber());
        
        LocalDate now = LocalDate.now();
        int daysInMonth = now.lengthOfMonth();
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(20));
        
        Spinner<Integer> absentSpinner = new Spinner<>(0, daysInMonth, student.getAbsentDays());
        absentSpinner.setEditable(true);
        
        Label messDaysLabel = new Label(String.valueOf(daysInMonth - student.getAbsentDays()));
        
        absentSpinner.valueProperty().addListener((obs, old, newVal) -> {
            messDaysLabel.setText(String.valueOf(daysInMonth - newVal));
        });
        
        grid.add(new Label("Total Days in Month:"), 0, 0);
        grid.add(new Label(String.valueOf(daysInMonth)), 1, 0);
        grid.add(new Label("Absent Days:"), 0, 1);
        grid.add(absentSpinner, 1, 1);
        grid.add(new Label("Mess Days:"), 0, 2);
        grid.add(messDaysLabel, 1, 2);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        dialog.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                int absentDays = absentSpinner.getValue();
                int messDays = daysInMonth - absentDays;
                updateStudentAttendance(student.getId(), messDays, absentDays);
                loadStudents();
            }
        });
    }
    
    private void updateStudentAttendance(int studentId, int messDays, int absentDays) {
    LocalDate now = LocalDate.now();
    
    try {
        Connection conn = DatabaseConnection.getConnection();
        
        // First, check if record exists
        PreparedStatement checkStmt = conn.prepareStatement(
            "SELECT id FROM student_attendance WHERE student_id = ? AND month = ? AND year = ?"
        );
        checkStmt.setInt(1, studentId);
        checkStmt.setInt(2, now.getMonthValue());
        checkStmt.setInt(3, now.getYear());
        ResultSet rs = checkStmt.executeQuery();
        
        PreparedStatement pstmt;
        
        if (rs.next()) {
            // Record exists - UPDATE
            String updateQuery = "UPDATE student_attendance " +
                               "SET mess_days = ?, absent_days = ?, updated_at = CURRENT_TIMESTAMP " +
                               "WHERE student_id = ? AND month = ? AND year = ?";
            pstmt = conn.prepareStatement(updateQuery);
            pstmt.setInt(1, messDays);
            pstmt.setInt(2, absentDays);
            pstmt.setInt(3, studentId);
            pstmt.setInt(4, now.getMonthValue());
            pstmt.setInt(5, now.getYear());
        } else {
            // Record doesn't exist - INSERT
            String insertQuery = "INSERT INTO student_attendance " +
                               "(student_id, month, year, total_days, mess_days, absent_days) " +
                               "VALUES (?, ?, ?, ?, ?, ?)";
            pstmt = conn.prepareStatement(insertQuery);
            pstmt.setInt(1, studentId);
            pstmt.setInt(2, now.getMonthValue());
            pstmt.setInt(3, now.getYear());
            pstmt.setInt(4, now.lengthOfMonth());
            pstmt.setInt(5, messDays);
            pstmt.setInt(6, absentDays);
        }
        
        int rowsAffected = pstmt.executeUpdate();
        
        if (rowsAffected > 0) {
            showAlert("Success", "Attendance updated successfully!", Alert.AlertType.INFORMATION);
        } else {
            showAlert("Error", "No rows were updated", Alert.AlertType.ERROR);
        }
        
    } catch (SQLException e) {
        e.printStackTrace();
        showAlert("Error", "Failed to update attendance: " + e.getMessage(), Alert.AlertType.ERROR);
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
