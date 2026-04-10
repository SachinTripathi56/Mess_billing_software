package com.smvdu.mess.utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import com.smvdu.mess.database.DatabaseConnection;

public class SyncService {

    // ================= SYNC ALL =================
 public static void syncAll() {
    System.out.println("🔄 syncAll() called");  // ← add this

    if (!DatabaseConnection.isInternetAvailable()) {
        System.out.println("⚠️ No internet, skipping sync");
        return;
    }

    Connection remote = DatabaseConnection.getMySQLConnection();
    if (remote == null) {
        System.out.println("⚠️ MySQL not available, skipping sync");
        return;
    }

    System.out.println("🔄 Starting full sync...");
    syncMesses();
    syncHostels();
    syncUsers();
    syncAdmins();
    syncStudents();
    syncStudentAttendance();
    syncSettings();
    syncBillConfigurations();
    syncGeneratedBills();
    System.out.println("✅ Full sync completed");
}

    // ================= USERS =================
public static void syncUsers() {
    try {
        Connection local  = DatabaseConnection.getConnection();
        Connection remote = DatabaseConnection.getMySQLConnection();
        if (remote == null) return;

        // ✅ Disable FK checks during sync to avoid hostel_id constraint failure
        remote.createStatement().execute("SET FOREIGN_KEY_CHECKS = 0");

        PreparedStatement ps = local.prepareStatement(
            "SELECT * FROM users WHERE is_synced = 0"
        );
        ResultSet rs = ps.executeQuery();
        boolean anySynced = false;

        while (rs.next()) {
            try (PreparedStatement up = remote.prepareStatement("""
                INSERT INTO users
                    (email, password, name, hostel_id, role, otp, otp_expiry)
                VALUES (?,?,?,?,?,?,?)
                ON DUPLICATE KEY UPDATE
                    password=VALUES(password),
                    name=VALUES(name),
                    hostel_id=VALUES(hostel_id),
                    role=VALUES(role),
                    otp=VALUES(otp),
                    otp_expiry=VALUES(otp_expiry)
            """)) {
                up.setString(1, rs.getString("email"));
                up.setString(2, rs.getString("password"));
                up.setString(3, rs.getString("name"));
                up.setInt   (4, rs.getInt("hostel_id"));
                up.setString(5, rs.getString("role"));
                up.setString(6, rs.getString("otp"));
                up.setString(7, rs.getString("otp_expiry"));

                up.executeUpdate();
                anySynced = true;

            } catch (Exception e) {
                // ✅ Print exact error per row so we can see what's failing
                System.err.println("❌ Failed to sync user: "
                    + rs.getString("email") + " → " + e.getMessage());
            }

            try (PreparedStatement mark = local.prepareStatement(
                "UPDATE users SET is_synced = 1 WHERE email = ?"
            )) {
                mark.setString(1, rs.getString("email"));
                mark.executeUpdate();
            }
        }

        // ✅ Re-enable FK checks after sync
        remote.createStatement().execute("SET FOREIGN_KEY_CHECKS = 1");

        rs.close();
        ps.close();

        if (anySynced) System.out.println("✓ Users synced");

    } catch (Exception e) {
        System.err.println("Users sync error: " + e.getMessage());
        e.printStackTrace();
    }
}

    // ================= ADMINS =================
    public static void syncAdmins() {
        try {
            Connection local  = DatabaseConnection.getConnection();
            Connection remote = DatabaseConnection.getMySQLConnection();
            if (remote == null) return;

            PreparedStatement ps = local.prepareStatement(
                "SELECT * FROM admins WHERE is_synced = 0"
            );
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                try (PreparedStatement up = remote.prepareStatement("""
                    INSERT INTO admins 
                        (email, password, name, designation, otp, otp_expiry)
                    VALUES (?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                        password=VALUES(password),
                        name=VALUES(name),
                        designation=VALUES(designation),
                        otp=VALUES(otp),
                        otp_expiry=VALUES(otp_expiry)
                """)) {
                    up.setString(1, rs.getString("email"));
                    up.setString(2, rs.getString("password"));
                    up.setString(3, rs.getString("name"));
                    up.setString(4, rs.getString("designation"));
                    up.setString(5, rs.getString("otp"));
                    up.setString(6, rs.getString("otp_expiry"));
                    up.executeUpdate();
                }
                markSynced(local, "admins", "email", rs.getString("email"));
            }

            rs.close(); ps.close();
            System.out.println("✓ Admins synced");

        } catch (Exception e) {
            System.err.println("Admins sync error: " + e.getMessage());
        }
    }

    // ================= HOSTELS =================
    public static void syncHostels() {
        try {
            Connection local  = DatabaseConnection.getConnection();
            Connection remote = DatabaseConnection.getMySQLConnection();
            if (remote == null) return;

            PreparedStatement ps = local.prepareStatement(
                "SELECT * FROM hostels WHERE is_synced = 0"
            );
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                try (PreparedStatement up = remote.prepareStatement("""
                    INSERT INTO hostels (id, name, code, mess_name, mess_id)
                    VALUES (?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                        name=VALUES(name),
                        code=VALUES(code),
                        mess_name=VALUES(mess_name),
                        mess_id=VALUES(mess_id)
                """)) {
                    up.setInt   (1, rs.getInt("id"));
                    up.setString(2, rs.getString("name"));
                    up.setString(3, rs.getString("code"));
                    up.setString(4, rs.getString("mess_name"));
                    up.setObject(5, rs.getObject("mess_id"));
                    up.executeUpdate();
                }
                markSyncedById(local, "hostels", rs.getInt("id"));
            }

            rs.close(); ps.close();
            System.out.println("✓ Hostels synced");

        } catch (Exception e) {
            System.err.println("Hostels sync error: " + e.getMessage());
        }
    }

    public static void debugSync() {
    try {
        Connection local  = DatabaseConnection.getConnection();
        Connection remote = DatabaseConnection.getMySQLConnection();

        if (remote == null) {
            System.out.println("❌ DEBUG: MySQL connection is null");
            return;
        }

        System.out.println("✅ DEBUG: MySQL connected");

        // Check how many unsynced records exist locally
        String[] tables = {
            "users", "admins", "hostels", "messes",
            "students", "student_attendance",
            "settings", "bill_configurations", "generated_bills"
        };

        for (String table : tables) {
            try {
                ResultSet rs = local.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM " + table + " WHERE is_synced = 0"
                );
                rs.next();
                System.out.println("📊 " + table + " unsynced rows: " + rs.getInt(1));
            } catch (Exception e) {
                System.out.println("❌ Error checking " + table + ": " + e.getMessage());
            }
        }

        // Try inserting one test row directly into MySQL
        try {
            PreparedStatement test = remote.prepareStatement(
                "INSERT INTO messes (id, name, code) VALUES (99, 'Test Mess', 'TM') " +
                "ON DUPLICATE KEY UPDATE name=VALUES(name)"
            );
            test.executeUpdate();
            System.out.println("✅ DEBUG: Test insert to MySQL worked");
            // Clean up test row
            remote.createStatement().execute("DELETE FROM messes WHERE id = 99");
        } catch (Exception e) {
            System.out.println("❌ DEBUG: Test insert failed: " + e.getMessage());
            e.printStackTrace();
        }

    } catch (Exception e) {
        System.out.println("❌ DEBUG: " + e.getMessage());
        e.printStackTrace();
    }
}

    // ================= MESSES =================
    public static void syncMesses() {
        try {
            Connection local  = DatabaseConnection.getConnection();
            Connection remote = DatabaseConnection.getMySQLConnection();
            if (remote == null) return;

            PreparedStatement ps = local.prepareStatement(
                "SELECT * FROM messes WHERE is_synced = 0"
            );
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                try (PreparedStatement up = remote.prepareStatement("""
                    INSERT INTO messes (id, name, code)
                    VALUES (?,?,?)
                    ON DUPLICATE KEY UPDATE
                        name=VALUES(name),
                        code=VALUES(code)
                """)) {
                    up.setInt   (1, rs.getInt("id"));
                    up.setString(2, rs.getString("name"));
                    up.setString(3, rs.getString("code"));
                    up.executeUpdate();
                }
                markSyncedById(local, "messes", rs.getInt("id"));
            }

            rs.close(); ps.close();
            System.out.println("✓ Messes synced");

        } catch (Exception e) {
            System.err.println("Messes sync error: " + e.getMessage());
        }
    }

    // ================= STUDENTS =================
    public static void syncStudents() {
        try {
            Connection local  = DatabaseConnection.getConnection();
            Connection remote = DatabaseConnection.getMySQLConnection();
            if (remote == null) return;

            PreparedStatement ps = local.prepareStatement(
                "SELECT * FROM students WHERE is_synced = 0"
            );
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                try (PreparedStatement up = remote.prepareStatement("""
                    INSERT INTO students
                        (entry_number, name, hostel_id, room_number, phone, email, is_active)
                    VALUES (?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                        name=VALUES(name),
                        hostel_id=VALUES(hostel_id),
                        room_number=VALUES(room_number),
                        phone=VALUES(phone),
                        email=VALUES(email),
                        is_active=VALUES(is_active)
                """)) {
                    up.setString(1, rs.getString("entry_number"));
                    up.setString(2, rs.getString("name"));
                    up.setInt   (3, rs.getInt("hostel_id"));
                    up.setString(4, rs.getString("room_number"));
                    up.setString(5, rs.getString("phone"));
                    up.setString(6, rs.getString("email"));
                    up.setInt   (7, rs.getInt("is_active"));
                    up.executeUpdate();
                }
                markSynced(local, "students", "entry_number", rs.getString("entry_number"));
            }

            rs.close(); ps.close();
            System.out.println("✓ Students synced");

        } catch (Exception e) {
            System.err.println("Students sync error: " + e.getMessage());
        }
    }

    // ================= STUDENT ATTENDANCE =================
    public static void syncStudentAttendance() {
        try {
            Connection local  = DatabaseConnection.getConnection();
            Connection remote = DatabaseConnection.getMySQLConnection();
            if (remote == null) return;

            PreparedStatement ps = local.prepareStatement(
                "SELECT * FROM student_attendance WHERE is_synced = 0"
            );
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                try (PreparedStatement up = remote.prepareStatement("""
                    INSERT INTO student_attendance
                        (student_id, month, year, total_days, mess_days, absent_days, remarks)
                    VALUES (?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                        total_days=VALUES(total_days),
                        mess_days=VALUES(mess_days),
                        absent_days=VALUES(absent_days),
                        remarks=VALUES(remarks)
                """)) {
                    up.setInt   (1, rs.getInt("student_id"));
                    up.setInt   (2, rs.getInt("month"));
                    up.setInt   (3, rs.getInt("year"));
                    up.setInt   (4, rs.getInt("total_days"));
                    up.setInt   (5, rs.getInt("mess_days"));
                    up.setInt   (6, rs.getInt("absent_days"));
                    up.setString(7, rs.getString("remarks"));
                    up.executeUpdate();
                }
                markSyncedById(local, "student_attendance", rs.getInt("id"));
            }

            rs.close(); ps.close();
            System.out.println("✓ Student attendance synced");

        } catch (Exception e) {
            System.err.println("Attendance sync error: " + e.getMessage());
        }
    }

    // ================= SETTINGS =================
    public static void syncSettings() {
        try {
            Connection local  = DatabaseConnection.getConnection();
            Connection remote = DatabaseConnection.getMySQLConnection();
            if (remote == null) return;

            PreparedStatement ps = local.prepareStatement(
                "SELECT * FROM settings WHERE is_synced = 0"
            );
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                try (PreparedStatement up = remote.prepareStatement("""
                    INSERT INTO settings (`key`, value)
                    VALUES (?,?)
                    ON DUPLICATE KEY UPDATE
                        value=VALUES(value)
                """)) {
                    up.setString(1, rs.getString("key"));
                    up.setString(2, rs.getString("value"));
                    up.executeUpdate();
                }
                markSyncedById(local, "settings", rs.getInt("id"));
            }

            rs.close(); ps.close();
            System.out.println("✓ Settings synced");

        } catch (Exception e) {
            System.err.println("Settings sync error: " + e.getMessage());
        }
    }

    // ================= BILL CONFIGURATIONS =================
    public static void syncBillConfigurations() {
        try {
            Connection local  = DatabaseConnection.getConnection();
            Connection remote = DatabaseConnection.getMySQLConnection();
            if (remote == null) return;

            PreparedStatement ps = local.prepareStatement(
                "SELECT * FROM bill_configurations WHERE is_synced = 0"
            );
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                try (PreparedStatement up = remote.prepareStatement("""
                    INSERT INTO bill_configurations
                        (mess_id, month, year, start_date, end_date, operating_days, fine_amount)
                    VALUES (?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                        start_date=VALUES(start_date),
                        end_date=VALUES(end_date),
                        operating_days=VALUES(operating_days),
                        fine_amount=VALUES(fine_amount)
                """)) {
                    up.setInt   (1, rs.getInt("mess_id"));
                    up.setInt   (2, rs.getInt("month"));
                    up.setInt   (3, rs.getInt("year"));
                    up.setString(4, rs.getString("start_date"));
                    up.setString(5, rs.getString("end_date"));
                    up.setInt   (6, rs.getInt("operating_days"));
                    up.setDouble(7, rs.getDouble("fine_amount"));
                    up.executeUpdate();
                }
                markSyncedById(local, "bill_configurations", rs.getInt("id"));
            }

            rs.close(); ps.close();
            System.out.println("✓ Bill configurations synced");

        } catch (Exception e) {
            System.err.println("Bill config sync error: " + e.getMessage());
        }
    }

    // ================= GENERATED BILLS =================
    public static void syncGeneratedBills() {
        try {
            Connection local  = DatabaseConnection.getConnection();
            Connection remote = DatabaseConnection.getMySQLConnection();
            if (remote == null) return;

            PreparedStatement ps = local.prepareStatement(
                "SELECT * FROM generated_bills WHERE is_synced = 0"
            );
            ResultSet rs = ps.executeQuery();

            while (rs.next()) {
                try (PreparedStatement up = remote.prepareStatement("""
                    INSERT INTO generated_bills (
                        mess_id, mess_name, month, year, bill_period,
                        operating_days, total_students, total_student_days,
                        total_absent_days, total_mess_days, per_day_rate,
                        subtotal, gst_percent, gst_amount, fine_amount,
                        total_amount, generated_by, generated_at
                    ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE
                        total_amount=VALUES(total_amount),
                        generated_at=VALUES(generated_at)
                """)) {
                    up.setInt   (1,  rs.getInt("mess_id"));
                    up.setString(2,  rs.getString("mess_name"));
                    up.setInt   (3,  rs.getInt("month"));
                    up.setInt   (4,  rs.getInt("year"));
                    up.setString(5,  rs.getString("bill_period"));
                    up.setInt   (6,  rs.getInt("operating_days"));
                    up.setInt   (7,  rs.getInt("total_students"));
                    up.setInt   (8,  rs.getInt("total_student_days"));
                    up.setInt   (9,  rs.getInt("total_absent_days"));
                    up.setInt   (10, rs.getInt("total_mess_days"));
                    up.setDouble(11, rs.getDouble("per_day_rate"));
                    up.setDouble(12, rs.getDouble("subtotal"));
                    up.setDouble(13, rs.getDouble("gst_percent"));
                    up.setDouble(14, rs.getDouble("gst_amount"));
                    up.setDouble(15, rs.getDouble("fine_amount"));
                    up.setDouble(16, rs.getDouble("total_amount"));
                    up.setString(17, rs.getString("generated_by"));
                    up.setString(18, rs.getString("generated_at"));
                    up.executeUpdate();
                }
                markSyncedById(local, "generated_bills", rs.getInt("id"));
            }

            rs.close(); ps.close();
            System.out.println("✓ Generated bills synced");

        } catch (Exception e) {
            System.err.println("Generated bills sync error: " + e.getMessage());
        }
    }

    // ================= HELPERS =================

    // For tables with unique text column (email, entry_number)
    private static void markSynced(Connection local, String table,
                                    String col, String val) throws Exception {
        try (PreparedStatement ps = local.prepareStatement(
            "UPDATE " + table + " SET is_synced = 1 WHERE " + col + " = ?"
        )) {
            ps.setString(1, val);
            ps.executeUpdate();
        }
    }

    // For tables with integer id
    private static void markSyncedById(Connection local,
                                        String table, int id) throws Exception {
        try (PreparedStatement ps = local.prepareStatement(
            "UPDATE " + table + " SET is_synced = 1 WHERE id = ?"
        )) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    public static void closeConnections() {
        // MySQL connection is managed by DatabaseConnection
        // nothing to close here separately
        System.out.println("✅ SyncService shutdown");
    }
}