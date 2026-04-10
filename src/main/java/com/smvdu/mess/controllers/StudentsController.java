package com.smvdu.mess.controllers;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.smvdu.mess.App;
import com.smvdu.mess.database.DatabaseConnection;
import com.smvdu.mess.models.Student;
import com.smvdu.mess.utils.MessUtils;
import com.smvdu.mess.utils.SessionManager;
import com.smvdu.mess.utils.StudentReportPDFGenerator;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.stage.FileChooser;

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
    @FXML private ComboBox<String> batchFilterCombo;
    @FXML private Button printButton;

    private final ObservableList<Student> studentsList = FXCollections.observableArrayList();
    private final ObservableList<Student> allStudentsList = FXCollections.observableArrayList();
    private int hostelId;
    private int messId;

    @FXML
    public void initialize() {
        hostelId = SessionManager.getCurrentHostelId();
        messId = MessUtils.getMessIdForHostel(hostelId);

        hostelLabel.setText(
            SessionManager.getCurrentUser().getMessName() != null
                ? SessionManager.getCurrentUser().getMessName()
                : SessionManager.getCurrentUser().getHostelName()
        );

        setupTable();
        loadStudents();
        setupBatchFilter();

        searchField.textProperty().addListener((obs, o, n) -> filterStudents());
    }

    private void setupTable() {
        entryNumberCol.setCellValueFactory(new PropertyValueFactory<>("entryNumber"));
        nameCol.setCellValueFactory(new PropertyValueFactory<>("name"));
        roomCol.setCellValueFactory(new PropertyValueFactory<>("roomNumber"));
        messDaysCol.setCellValueFactory(new PropertyValueFactory<>("messDays"));
        absentDaysCol.setCellValueFactory(new PropertyValueFactory<>("absentDays"));

        // ✅ Action column with Edit Attendance, Edit Info, and Delete buttons
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final HBox buttonBox = new HBox(5);
            private final Button attendanceBtn = new Button("📅");
            private final Button editBtn = new Button("✏");
            private final Button deleteBtn = new Button("🗑");

            {
                attendanceBtn.setStyle("-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-cursor: hand; -fx-padding: 5 8; -fx-font-size: 12px;");
                editBtn.setStyle("-fx-background-color: #2196F3; -fx-text-fill: white; -fx-cursor: hand; -fx-padding: 5 8; -fx-font-size: 12px;");
                deleteBtn.setStyle("-fx-background-color: #f44336; -fx-text-fill: white; -fx-cursor: hand; -fx-padding: 5 8; -fx-font-size: 12px;");
                
                attendanceBtn.setTooltip(new javafx.scene.control.Tooltip("Edit Attendance"));
                editBtn.setTooltip(new javafx.scene.control.Tooltip("Edit Student Info"));
                deleteBtn.setTooltip(new javafx.scene.control.Tooltip("Delete Student"));
                
                attendanceBtn.setOnAction(e -> {
                    Student student = getTableView().getItems().get(getIndex());
                    showEditAttendanceDialog(student);
                });
                
                editBtn.setOnAction(e -> {
                    Student student = getTableView().getItems().get(getIndex());
                    showEditStudentDialog(student);
                });
                
                deleteBtn.setOnAction(e -> {
                    Student student = getTableView().getItems().get(getIndex());
                    deleteStudent(student);
                });
                
                buttonBox.getChildren().addAll(attendanceBtn, editBtn, deleteBtn);
                buttonBox.setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : buttonBox);
            }
        });

        studentsTable.setItems(studentsList);
    }

    private void loadStudents() {
        allStudentsList.clear();
        studentsList.clear();
        LocalDate now = LocalDate.now();
        
        int operatingDays = MessUtils.getOperatingDays(messId, now.getMonthValue(), now.getYear());

        try {
            List<Integer> hostelIds = MessUtils.getHostelIdsForMess(messId);
            String hostelIdsStr = MessUtils.hostelIdsToString(hostelIds);

            Connection conn = DatabaseConnection.getConnection();
            
            // ✅ Load ALL students with their attendance data
            String sql =
                "SELECT s.*, " +
                "COALESCE(sa.mess_days, ?) mess_days, " +
                "COALESCE(sa.absent_days, 0) absent_days " +
                "FROM students s " +
                "LEFT JOIN student_attendance sa ON s.id = sa.student_id " +
                "AND sa.month = ? AND sa.year = ? " +
                "WHERE s.hostel_id IN (" + hostelIdsStr + ") " +
                "ORDER BY s.entry_number";

            PreparedStatement ps = conn.prepareStatement(sql);
            ps.setInt(1, operatingDays);
            ps.setInt(2, now.getMonthValue());
            ps.setInt(3, now.getYear());

            ResultSet rs = ps.executeQuery();
            while (rs.next()) {
                Student st = new Student(
                    rs.getInt("id"),
                    rs.getString("entry_number"),
                    rs.getString("name"),
                    rs.getInt("hostel_id"),
                    rs.getString("room_number"),
                    rs.getString("phone"),
                    rs.getString("email"),
                    true // All students are active
                );
                st.setMessDays(rs.getInt("mess_days"));
                st.setAbsentDays(rs.getInt("absent_days"));
                allStudentsList.add(st);
            }

            studentsList.setAll(allStudentsList);
            totalLabel.setText("Total: " + studentsList.size() + " students");

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void setupBatchFilter() {
        Set<String> batches = new HashSet<>();
        batches.add("All Batches");

        for (Student student : allStudentsList) {
            String batch = extractBatch(student.getEntryNumber());
            if (batch != null) {
                batches.add(batch);
            }
        }

        List<String> sortedBatches = new ArrayList<>(batches);
        sortedBatches.sort((a, b) -> {
            if (a.equals("All Batches")) return -1;
            if (b.equals("All Batches")) return 1;
            return b.compareTo(a);
        });

        batchFilterCombo.setItems(FXCollections.observableArrayList(sortedBatches));
        batchFilterCombo.setValue("All Batches");
        
        batchFilterCombo.setOnAction(e -> filterStudents());
    }

    private String extractBatch(String entryNumber) {
        if (entryNumber == null || entryNumber.length() < 2) {
            return null;
        }

        try {
            String yearPrefix = entryNumber.substring(0, 2);
            int year = Integer.parseInt(yearPrefix);
            int fullYear = 2000 + year;
            return String.valueOf(fullYear);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void filterStudents() {
        String searchQuery = searchField.getText();
        String selectedBatch = batchFilterCombo.getValue();

        ObservableList<Student> filtered = allStudentsList.filtered(student -> {
            boolean batchMatch = true;
            if (selectedBatch != null && !selectedBatch.equals("All Batches")) {
                String studentBatch = extractBatch(student.getEntryNumber());
                batchMatch = selectedBatch.equals(studentBatch);
            }

            boolean searchMatch = true;
            if (searchQuery != null && !searchQuery.isEmpty()) {
                String lowerQuery = searchQuery.toLowerCase();
                searchMatch = student.getName().toLowerCase().contains(lowerQuery)
                           || student.getEntryNumber().toLowerCase().contains(lowerQuery);
            }

            return batchMatch && searchMatch;
        });

        studentsList.setAll(filtered);
        totalLabel.setText("Total: " + studentsList.size() + " students");
    }

    // ✅ ATTENDANCE DIALOG - Edit absent days (affects billing)
    private void showEditAttendanceDialog(Student student) {
        LocalDate now = LocalDate.now();
        int operatingDays = MessUtils.getOperatingDays(messId, now.getMonthValue(), now.getYear());

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Attendance");
        dialog.setHeaderText(student.getEntryNumber() + " - " + student.getName());

        Spinner<Integer> absentSpinner = new Spinner<>(0, operatingDays, student.getAbsentDays());
        Label messDaysLabel = new Label(String.valueOf(operatingDays - student.getAbsentDays()));

        absentSpinner.valueProperty().addListener((obs, o, n) ->
            messDaysLabel.setText(String.valueOf(operatingDays - n)));

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(20));
        grid.setVgap(10);
        grid.setHgap(10);

        grid.addRow(0, new Label("Operating Days:"), new Label(String.valueOf(operatingDays)));
        grid.addRow(1, new Label("Absent Days:"), absentSpinner);
        grid.addRow(2, new Label("Mess Days (Chargeable):"), messDaysLabel);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                updateAttendance(
                    student.getId(),
                    operatingDays - absentSpinner.getValue(),
                    absentSpinner.getValue(),
                    operatingDays
                );
                loadStudents();
                filterStudents();
            }
        });
    }

    // ✅ EDIT STUDENT INFO DIALOG
    private void showEditStudentDialog(Student student) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit Student Information");
        dialog.setHeaderText("Update Student Details");

        GridPane grid = new GridPane();
        grid.setPadding(new Insets(20));
        grid.setVgap(10);
        grid.setHgap(10);

        TextField entryField = new TextField(student.getEntryNumber());
        entryField.setDisable(true);
        
        TextField nameField = new TextField(student.getName());
        TextField roomField = new TextField(student.getRoomNumber());
        TextField phoneField = new TextField(student.getPhone());
        TextField emailField = new TextField(student.getEmail());

        grid.addRow(0, new Label("Entry Number:"), entryField);
        grid.addRow(1, new Label("Name:*"), nameField);
        grid.addRow(2, new Label("Room Number:"), roomField);
        grid.addRow(3, new Label("Phone:"), phoneField);
        grid.addRow(4, new Label("Email:"), emailField);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.showAndWait().ifPresent(bt -> {
            if (bt == ButtonType.OK) {
                String newName = nameField.getText().trim();
                if (newName.isEmpty()) {
                    showAlert("Error", "Name cannot be empty", Alert.AlertType.ERROR);
                    return;
                }
                
                updateStudentInfo(
                    student.getId(),
                    newName,
                    roomField.getText().trim(),
                    phoneField.getText().trim(),
                    emailField.getText().trim()
                );
                loadStudents();
                filterStudents();
            }
        });
    }

    // ✅ UPDATE ATTENDANCE (affects billing calculation)
    private void updateAttendance(int studentId, int messDays, int absentDays, int totalDays) {
        LocalDate now = LocalDate.now();

        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO student_attendance " +
                "(student_id, month, year, total_days, mess_days, absent_days) " +
                "VALUES (?,?,?,?,?,?) " +
                "ON CONFLICT(student_id, month, year) DO UPDATE SET " +
                "total_days = excluded.total_days, " +
                "mess_days = excluded.mess_days, " +
                "absent_days = excluded.absent_days, " +
                "updated_at = CURRENT_TIMESTAMP"
            );

            ps.setInt(1, studentId);
            ps.setInt(2, now.getMonthValue());
            ps.setInt(3, now.getYear());
            ps.setInt(4, totalDays);
            ps.setInt(5, messDays);
            ps.setInt(6, absentDays);

            ps.executeUpdate();
            showAlert("Success", "Attendance updated successfully!\nThis will be reflected in the bill.", Alert.AlertType.INFORMATION);

        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Error", "Failed to update attendance: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    // ✅ UPDATE STUDENT INFO (doesn't affect billing)
    private void updateStudentInfo(int studentId, String name, String room, String phone, String email) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "UPDATE students SET name = ?, room_number = ?, phone = ?, email = ? WHERE id = ?"
            );
            ps.setString(1, name);
            ps.setString(2, room);
            ps.setString(3, phone);
            ps.setString(4, email);
            ps.setInt(5, studentId);
            
            ps.executeUpdate();
            showAlert("Success", "Student information updated successfully", Alert.AlertType.INFORMATION);
            
        } catch (SQLException e) {
            e.printStackTrace();
            showAlert("Error", "Failed to update student: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    // ✅ DELETE STUDENT
    private void deleteStudent(Student student) {
        Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
        confirmAlert.setTitle("Delete Student");
        confirmAlert.setHeaderText("Delete " + student.getName() + "?");
        confirmAlert.setContentText("Entry Number: " + student.getEntryNumber() + "\n\nThis action cannot be undone.");

        Optional<ButtonType> result = confirmAlert.showAndWait();
        
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                Connection conn = DatabaseConnection.getConnection();
                
                PreparedStatement ps1 = conn.prepareStatement(
                    "DELETE FROM student_attendance WHERE student_id = ?"
                );
                ps1.setInt(1, student.getId());
                ps1.executeUpdate();
                
                PreparedStatement ps2 = conn.prepareStatement(
                    "DELETE FROM students WHERE id = ?"
                );
                ps2.setInt(1, student.getId());
                ps2.executeUpdate();
                
                showAlert("Success", "Student deleted successfully", Alert.AlertType.INFORMATION);
                loadStudents();
                filterStudents();
                
            } catch (SQLException e) {
                e.printStackTrace();
                showAlert("Error", "Failed to delete student: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    @FXML
    private void handlePrint() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Print Student Report");
        dialog.setHeaderText("Select report type");

        ButtonType allStudentsBtn = new ButtonType("All Students");
        ButtonType absentStudentsBtn = new ButtonType("Absent Students");
        ButtonType presentStudentsBtn = new ButtonType("Present Students");
        ButtonType cancelBtn = ButtonType.CANCEL;

        dialog.getDialogPane().getButtonTypes().addAll(
            allStudentsBtn, absentStudentsBtn, presentStudentsBtn, cancelBtn
        );

        dialog.setResultConverter(button -> {
            if (button == allStudentsBtn) return "ALL";
            if (button == absentStudentsBtn) return "ABSENT";
            if (button == presentStudentsBtn) return "PRESENT";
            return null;
        });

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(this::generatePDF);
    }

    private void generatePDF(String reportType) {
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Save Student Report");
            fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PDF Files", "*.pdf")
            );

            String fileName = "Students_" + reportType + "_" + 
                            LocalDate.now().toString().replace("-", "") + ".pdf";
            fileChooser.setInitialFileName(fileName);

            File file = fileChooser.showSaveDialog(studentsTable.getScene().getWindow());

            if (file != null) {
                List<Student> studentsToReport = new ArrayList<>();

                switch (reportType) {
                    case "ALL":
                        studentsToReport.addAll(studentsList);
                        break;
                    case "ABSENT":
                        for (Student s : studentsList) {
                            if (s.getAbsentDays() > 0) {
                                studentsToReport.add(s);
                            }
                        }
                        break;
                    case "PRESENT":
                        for (Student s : studentsList) {
                            if (s.getAbsentDays() == 0) {
                                studentsToReport.add(s);
                            }
                        }
                        break;
                }

                String messName = SessionManager.getCurrentUser().getMessName() != null
                    ? SessionManager.getCurrentUser().getMessName()
                    : SessionManager.getCurrentUser().getHostelName();

                LocalDate now = LocalDate.now();
                int operatingDays = MessUtils.getOperatingDays(messId, now.getMonthValue(), now.getYear());

                StudentReportPDFGenerator.generateStudentReport(
                    file.getAbsolutePath(),
                    "SHRI MATA VAISHNO DEVI UNIVERSITY",
                    messName,
                    reportType,
                    studentsToReport,
                    operatingDays,
                    now
                );

                showAlert("Success", "Report generated successfully!\nFile: " + file.getName(), Alert.AlertType.INFORMATION);
            }

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Failed to generate report: " + e.getMessage(), Alert.AlertType.ERROR);
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