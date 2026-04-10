package com.smvdu.mess.controllers;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.smvdu.mess.App;
import com.smvdu.mess.database.DatabaseConnection;
import com.smvdu.mess.models.User;
import com.smvdu.mess.utils.AdminSessionManager;
import com.smvdu.mess.utils.NetworkSessionManager;
import com.smvdu.mess.utils.NetworkSessionManager.SessionResult;
import com.smvdu.mess.utils.SessionManager;
import com.smvdu.mess.utils.SyncService;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public class LoginController {

    @FXML private TextField     emailField;
    @FXML private PasswordField passwordField;
    @FXML private Hyperlink     forgotPasswordLink;
    @FXML private Button        loginButton;        // add fx:id="loginButton" in FXML
    @FXML private Label         statusLabel;        // add fx:id="statusLabel"  in FXML

    // NOTE: Add these two nodes to login.fxml (see comment at the bottom of this file)

    @FXML
    public void initialize() {
        // DatabaseConnection.initialize() is already called from App.java — don't repeat it here
    }

    @FXML
    private void handleLogin() {
        String email    = emailField.getText().trim();
        String password = passwordField.getText();

        if (email.isEmpty() || password.isEmpty()) {
            showAlert("Error", "Please enter both email and password", Alert.AlertType.ERROR);
            return;
        }

        // ----------------------------------------------------------------
        // STEP 1 — Internet is MANDATORY for every login (first or not)
        // ----------------------------------------------------------------
        if (!DatabaseConnection.isInternetAvailable()) {
            showAlert("No Internet",
                "Internet connection is required to login.\n\n" +
                "Please connect to the internet and try again.\n\n" +
                "This ensures your data stays in sync with all other caretakers.",
                Alert.AlertType.ERROR);
            return;
        }

        // ----------------------------------------------------------------
        // Disable button and show status while we work in background
        // ----------------------------------------------------------------
        setUiBusy(true, "Connecting...");

        // Run everything in a background thread so UI doesn't freeze
        new Thread(() -> {

            // ------------------------------------------------------------
            // STEP 2 — Verify credentials against LOCAL SQLite
            //           (fast, works even if MySQL is slow)
            // ------------------------------------------------------------
            boolean credentialsOk = isAdminEmail(email)
                    ? checkAdminCredentials(email, password)
                    : checkCaretakerCredentials(email, password);

            if (!credentialsOk) {
                Platform.runLater(() -> {
                    setUiBusy(false, "");
                    showAlert("Login Failed", "Invalid email or password", Alert.AlertType.ERROR);
                });
                return;
            }

            // ------------------------------------------------------------
            // STEP 3 — Pull latest data from MySQL before entering app
            //           (always do this on login so data is fresh)
            // ------------------------------------------------------------
            Platform.runLater(() -> setUiBusy(true, "Fetching latest data..."));
            SyncService.pullFromMySQL();

            // ------------------------------------------------------------
            // STEP 4 — Session lock (caretakers only, admins skip)
            // ------------------------------------------------------------
            if (!isAdminEmail(email)) {
                Platform.runLater(() -> setUiBusy(true, "Checking session..."));

                SessionResult result = NetworkSessionManager.acquireSession(email);

                switch (result) {
                    case SESSION_ACTIVE_ELSEWHERE:
                        Platform.runLater(() -> {
                            setUiBusy(false, "");
                            showAlert("Session Active",
                                "This account is currently active on another device.\n\n" +
                                "Only one device can be logged in at a time.\n\n" +
                                "If the other device lost connection, please wait 2 minutes " +
                                "and try again.",
                                Alert.AlertType.WARNING);
                        });
                        return;

                    case MYSQL_UNAVAILABLE:
                        // MySQL went down between our internet check and session check
                        // Unlikely but handle gracefully — block login to be safe
                        Platform.runLater(() -> {
                            setUiBusy(false, "");
                            showAlert("Server Unavailable",
                                "Could not reach the database server.\n" +
                                "Please try again in a moment.",
                                Alert.AlertType.ERROR);
                        });
                        return;

                    case ERROR:
                        Platform.runLater(() -> {
                            setUiBusy(false, "");
                            showAlert("Error",
                                "An unexpected error occurred during login.\n" +
                                "Please try again.",
                                Alert.AlertType.ERROR);
                        });
                        return;

                    case SUCCESS:
                    default:
                        break; // proceed
                }
            }

            // ------------------------------------------------------------
            // STEP 5 — Navigate to dashboard
            // ------------------------------------------------------------
            Platform.runLater(() -> {
                setUiBusy(false, "");
                if (isAdminEmail(email)) {
                    proceedAsAdmin(email);
                } else {
                    proceedAsCaretaker(email, password);
                }
            });

        }, "login-thread").start();
    }

    // ====================================================================
    //  CREDENTIAL CHECKS  (against local SQLite)
    // ====================================================================
    private boolean checkCaretakerCredentials(String email, String password) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM users WHERE email = ? AND password = ?"
            );
            ps.setString(1, email);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();
            boolean ok = rs.next();
            rs.close(); ps.close();
            return ok;
        } catch (Exception e) {
            System.err.println("Credential check error: " + e.getMessage());
            return false;
        }
    }

    private boolean checkAdminCredentials(String email, String password) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM admins WHERE email = ? AND password = ?"
            );
            ps.setString(1, email);
            ps.setString(2, password);
            ResultSet rs = ps.executeQuery();
            boolean ok = rs.next();
            rs.close(); ps.close();
            return ok;
        } catch (Exception e) {
            System.err.println("Admin credential check error: " + e.getMessage());
            return false;
        }
    }

    // ====================================================================
    //  NAVIGATE TO DASHBOARDS
    // ====================================================================
    private void proceedAsCaretaker(String email, String password) {
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

                // Start heartbeat to keep session alive
                NetworkSessionManager.startHeartbeat(email);

                rs.close(); pstmt.close();
                App.setRoot("dashboard");
            } else {
                rs.close(); pstmt.close();
                showAlert("Error", "Failed to load user profile", Alert.AlertType.ERROR);
            }

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Login failed: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void proceedAsAdmin(String email) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement pstmt = conn.prepareStatement(
                "SELECT * FROM admins WHERE email = ?"
            );
            pstmt.setString(1, email);
            ResultSet rs = pstmt.executeQuery();

            if (rs.next()) {
                String adminName    = rs.getString("name");
                String designation  = rs.getString("designation");
                rs.close(); pstmt.close();

                AdminSessionManager.setAdminInfo(adminName, designation);

                FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/views/admin_dashboard.fxml")
                );
                Parent root = loader.load();
                AdminDashboardController controller = loader.getController();
                controller.setAdminInfo(adminName, designation);
                App.getPrimaryStage().getScene().setRoot(root);
            } else {
                rs.close(); pstmt.close();
                showAlert("Error", "Failed to load admin profile", Alert.AlertType.ERROR);
            }

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Admin login failed: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    // ====================================================================
    //  HELPERS
    // ====================================================================
    private boolean isAdminEmail(String email) {
        return email.contains("vc.") || email.contains("dean.") || email.contains("registrar");
    }

    /**
     * Disable/enable the login button and show a status message.
     * Call this on the JavaFX thread.
     */
    private void setUiBusy(boolean busy, String message) {
        if (loginButton != null)  loginButton.setDisable(busy);
        if (statusLabel != null) {
            statusLabel.setText(message);
            statusLabel.setVisible(!message.isEmpty());
        }
    }

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

/*
 * =========================================================================
 *  REQUIRED FXML CHANGES  — add these two nodes inside your <VBox spacing="10">
 *  in login.fxml, just above the Login <Button>:
 *
 *  <!-- Status label shown during login background work -->
 *  <Label fx:id="statusLabel"
 *         text=""
 *         visible="false"
 *         style="-fx-text-fill: #2196F3; -fx-font-size: 12px;"/>
 *
 *  And add fx:id to the login button:
 *  <Button fx:id="loginButton"
 *          text="Login"
 *          onAction="#handleLogin"
 *          maxWidth="Infinity"
 *          styleClass="login-btn"/>
 * =========================================================================
 */
