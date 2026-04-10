package com.smvdu.mess.utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.smvdu.mess.database.DatabaseConnection;

public class OTPService {
    
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * Generate and store OTP for user (caretaker)
     */
    public static String generateAndStoreOTPForUser(String email) {
        Connection conn = null;
        PreparedStatement checkStmt = null;
        PreparedStatement updateStmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            // ✅ Added debugging
            System.out.println("Checking if user exists: " + email);
            
            // Check if user exists
            checkStmt = conn.prepareStatement(
                "SELECT id, name FROM users WHERE email = ?"
            );
            checkStmt.setString(1, email);
            rs = checkStmt.executeQuery();
            
            if (!rs.next()) {
                System.out.println("✗ User not found in database: " + email);
                return null;
            }
            
            System.out.println("✓ User found: " + rs.getString("name"));
            
            // Generate OTP
            String otp = EmailService.generateOTP();
            
            // Set expiry time (5 minutes from now)
            LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(5);
            String expiryString = expiryTime.format(DATE_FORMAT);
            
            System.out.println("Generated OTP: " + otp + " (Expires: " + expiryString + ")");
            
            // ✅ Close ResultSet before UPDATE
            rs.close();
            
            // Store OTP in database
            updateStmt = conn.prepareStatement(
                "UPDATE users SET otp = ?, otp_expiry = ? WHERE email = ?"
            );
            updateStmt.setString(1, otp);
            updateStmt.setString(2, expiryString);
            updateStmt.setString(3, email);
            
            int rowsUpdated = updateStmt.executeUpdate();
            
            if (rowsUpdated > 0) {
                System.out.println("✓ OTP stored successfully for: " + email);
                return otp;
            } else {
                System.out.println("✗ Failed to store OTP for: " + email);
                return null;
            }
            
        } catch (Exception e) {
            System.err.println("✗ Error in generateAndStoreOTPForUser:");
            e.printStackTrace();
            return null;
        } finally {
            // ✅ Properly close resources
            try {
                if (rs != null) rs.close();
                if (checkStmt != null) checkStmt.close();
                if (updateStmt != null) updateStmt.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Generate and store OTP for admin
     */
    public static String generateAndStoreOTPForAdmin(String email) {
        Connection conn = null;
        PreparedStatement checkStmt = null;
        PreparedStatement updateStmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            System.out.println("Checking if admin exists: " + email);
            
            checkStmt = conn.prepareStatement(
                "SELECT id, name FROM admins WHERE email = ?"
            );
            checkStmt.setString(1, email);
            rs = checkStmt.executeQuery();
            
            if (!rs.next()) {
                System.out.println("✗ Admin not found: " + email);
                return null;
            }
            
            System.out.println("✓ Admin found: " + rs.getString("name"));
            
            String otp = EmailService.generateOTP();
            LocalDateTime expiryTime = LocalDateTime.now().plusMinutes(5);
            String expiryString = expiryTime.format(DATE_FORMAT);
            
            System.out.println("Generated OTP: " + otp + " (Expires: " + expiryString + ")");
            
            rs.close();
            
            updateStmt = conn.prepareStatement(
                "UPDATE admins SET otp = ?, otp_expiry = ? WHERE email = ?"
            );
            updateStmt.setString(1, otp);
            updateStmt.setString(2, expiryString);
            updateStmt.setString(3, email);
            
            int rowsUpdated = updateStmt.executeUpdate();
            
            if (rowsUpdated > 0) {
                System.out.println("✓ OTP stored successfully for admin: " + email);
                return otp;
            } else {
                System.out.println("✗ Failed to store OTP for admin: " + email);
                return null;
            }
            
        } catch (Exception e) {
            System.err.println("✗ Error in generateAndStoreOTPForAdmin:");
            e.printStackTrace();
            return null;
        } finally {
            try {
                if (rs != null) rs.close();
                if (checkStmt != null) checkStmt.close();
                if (updateStmt != null) updateStmt.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Verify OTP for user and reset password
     */
    public static String verifyAndResetPasswordForUser(String email, String otp, String newPassword) {
        Connection conn = null;
        PreparedStatement stmt = null;
        PreparedStatement updateStmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            stmt = conn.prepareStatement(
                "SELECT otp, otp_expiry FROM users WHERE email = ?"
            );
            stmt.setString(1, email);
            rs = stmt.executeQuery();
            
            if (!rs.next()) {
                return "Email not found";
            }
            
            String storedOTP = rs.getString("otp");
            String expiryString = rs.getString("otp_expiry");
            
            if (storedOTP == null || storedOTP.isEmpty()) {
                return "No OTP found. Please request a new one.";
            }
            
            if (!storedOTP.equals(otp)) {
                return "Invalid OTP";
            }
            
            LocalDateTime expiryTime = LocalDateTime.parse(expiryString, DATE_FORMAT);
            if (LocalDateTime.now().isAfter(expiryTime)) {
                clearOTPForUser(email);
                return "OTP expired. Please request a new one.";
            }
            
            rs.close();
            
            updateStmt = conn.prepareStatement(
                "UPDATE users SET password = ?, otp = NULL, otp_expiry = NULL WHERE email = ?"
            );
            updateStmt.setString(1, newPassword);
            updateStmt.setString(2, email);
            updateStmt.executeUpdate();
            
            System.out.println("✓ Password reset successful for: " + email);
            return "SUCCESS";
            
        } catch (Exception e) {
            e.printStackTrace();
            return "Error resetting password: " + e.getMessage();
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (updateStmt != null) updateStmt.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Verify OTP for admin and reset password
     */
    public static String verifyAndResetPasswordForAdmin(String email, String otp, String newPassword) {
        Connection conn = null;
        PreparedStatement stmt = null;
        PreparedStatement updateStmt = null;
        ResultSet rs = null;
        
        try {
            conn = DatabaseConnection.getConnection();
            
            stmt = conn.prepareStatement(
                "SELECT otp, otp_expiry FROM admins WHERE email = ?"
            );
            stmt.setString(1, email);
            rs = stmt.executeQuery();
            
            if (!rs.next()) {
                return "Email not found";
            }
            
            String storedOTP = rs.getString("otp");
            String expiryString = rs.getString("otp_expiry");
            
            if (storedOTP == null || storedOTP.isEmpty()) {
                return "No OTP found. Please request a new one.";
            }
            
            if (!storedOTP.equals(otp)) {
                return "Invalid OTP";
            }
            
            LocalDateTime expiryTime = LocalDateTime.parse(expiryString, DATE_FORMAT);
            if (LocalDateTime.now().isAfter(expiryTime)) {
                clearOTPForAdmin(email);
                return "OTP expired. Please request a new one.";
            }
            
            rs.close();
            
            updateStmt = conn.prepareStatement(
                "UPDATE admins SET password = ?, otp = NULL, otp_expiry = NULL WHERE email = ?"
            );
            updateStmt.setString(1, newPassword);
            updateStmt.setString(2, email);
            updateStmt.executeUpdate();
            
            System.out.println("✓ Password reset successful for admin: " + email);
            return "SUCCESS";
            
        } catch (Exception e) {
            e.printStackTrace();
            return "Error resetting password: " + e.getMessage();
        } finally {
            try {
                if (rs != null) rs.close();
                if (stmt != null) stmt.close();
                if (updateStmt != null) updateStmt.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
    
    public static void clearOTPForUser(String email) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "UPDATE users SET otp = NULL, otp_expiry = NULL WHERE email = ?"
            );
            stmt.setString(1, email);
            stmt.executeUpdate();
            stmt.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public static void clearOTPForAdmin(String email) {
        try {
            Connection conn = DatabaseConnection.getConnection();
            PreparedStatement stmt = conn.prepareStatement(
                "UPDATE admins SET otp = NULL, otp_expiry = NULL WHERE email = ?"
            );
            stmt.setString(1, email);
            stmt.executeUpdate();
            stmt.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}